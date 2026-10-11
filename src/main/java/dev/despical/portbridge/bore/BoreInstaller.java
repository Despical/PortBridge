/*
 * PortBridge - Managed TCP tunnels for Minecraft.
 * Copyright (C) 2026  Berke Akçen
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package dev.despical.portbridge.bore;

import dev.despical.portbridge.config.BridgeSettings;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Resolves a configured Bore executable or installs a verified managed release.
 * <p>
 * Managed downloads use a pinned release and platform-specific SHA-256 digests.
 * Archive extraction validates entry names and types, limits archive and expanded
 * sizes, and installs only the expected executable. Cache receipts retain both
 * archive and executable digests so modified binaries are downloaded again.
 * <p>
 * Resolution is serialized per installer and may perform filesystem and network
 * work. Call it outside the server thread. Explicit executable paths are checked
 * for usability and bypass managed downloads and digest verification.
 *
 * @author Despical
 * <p>
 * Created at 10.10.2026
 */
public final class BoreInstaller {

    public static final String VERSION = "0.6.0";
    private static final long ARCHIVE_LIMIT = 32L * 1024 * 1024;
    private static final long EXTRACT_LIMIT = 64L * 1024 * 1024;

    // Digests published by GitHub's official ekzhang/bore v0.6.0 release API.
    private static final Map<String, String> DIGESTS = Map.of(
        "x86_64-pc-windows-msvc.zip", "01709c64fe2787cdc9a21d7030b0f08ad72dff0c36b7ecb72f4f667a55a34b4f",
        "i686-pc-windows-msvc.zip", "23a249d09987f14e114ea126dc19ed00654acf35f8169cbd0f575c8f656ea7c5",
        "x86_64-unknown-linux-musl.tar.gz", "e484d1e3acba77169b773f31a5bfb34192d4b660f44a094a658a2522cd2270f7",
        "aarch64-unknown-linux-musl.tar.gz", "ffc4515f3617420b243758cf36ed6a63208d7dba76b2ec3e90d1f476a9742951",
        "x86_64-apple-darwin.tar.gz", "206db723a382bbc18d2893fc8472868e0b5f41de35975269aab7891ecc8659cc",
        "aarch64-apple-darwin.tar.gz", "65f43a67b90874700538bdb6064c5e92276e64dfba24f5cd72ef24a035eec3bc"
    );

    private final Path dataDirectory;
    private final Logger logger;

    public BoreInstaller(Path dataDirectory, Logger logger) {
        this.dataDirectory = dataDirectory.toAbsolutePath().normalize();
        this.logger = logger;
    }

    public synchronized Path resolve(BridgeSettings.Binary settings) throws IOException, InterruptedException {
        if (!settings.executable().isBlank()) {
            Path configured = Path.of(settings.executable());
            Path executable = (configured.isAbsolute() ? configured : dataDirectory.resolve(configured)).normalize();
            requireExecutable(executable);
            return executable;
        }

        Asset asset = platform(System.getProperty("os.name"), System.getProperty("os.arch"));
        Path directory = dataDirectory.resolve("bin").resolve("bore-" + VERSION + "-" + asset.target());
        Path executable = directory.resolve(asset.executable());
        Path receipt = directory.resolve("verified.sha256");

        if (validCache(executable, receipt, asset.digest())) {
            requireExecutable(executable);
            return executable;
        }

        if (!settings.autoDownload()) {
            throw new IOException("No verified Bore binary is available. Enable bore.auto-download or configure bore.executable.");
        }

        Files.createDirectories(directory);

        Path archive = Files.createTempFile(directory, "download-", ".tmp");
        Path extracted = Files.createTempFile(directory, "executable-", ".tmp");
        Path receiptTemp = Files.createTempFile(directory, "verified-", ".tmp");

        try {
            logger.info("Downloading verified Bore v" + VERSION + " for " + asset.target() + "...");
            download(asset, archive, settings.downloadTimeoutSeconds());

            if (!digest(archive).equals(asset.digest())) {
                throw new IOException("Bore archive SHA-256 verification failed.");
            }

            extract(archive, extracted, asset.executable(), asset.target().endsWith(".zip"));
            makeExecutable(extracted);

            Files.writeString(receiptTemp, asset.digest() + "\n" + digest(extracted) + "\n");
            move(extracted, executable);
            move(receiptTemp, receipt);

            requireExecutable(executable);
            return executable;
        } finally {
            Files.deleteIfExists(archive);
            Files.deleteIfExists(extracted);
            Files.deleteIfExists(receiptTemp);
        }
    }

    static Asset platform(String osName, String architecture) throws IOException {
        String os = osName.toLowerCase(Locale.ROOT);
        String arch = architecture.toLowerCase(Locale.ROOT);

        boolean x64 = arch.equals("amd64") || arch.equals("x86_64");
        boolean arm64 = arch.equals("aarch64") || arch.equals("arm64");

        String target;
        if (os.contains("windows")) {
            if (x64) {
                target = "x86_64-pc-windows-msvc.zip";
            } else if (arch.equals("x86") || arch.matches("i[3-6]86")) {
                target = "i686-pc-windows-msvc.zip";
            } else {
                throw unsupported(osName, architecture);
            }
        } else if (os.contains("linux")) {
            if (!x64 && !arm64) {
                throw unsupported(osName, architecture);
            }

            target = (x64 ? "x86_64" : "aarch64") + "-unknown-linux-musl.tar.gz";
        } else if (os.contains("mac") || os.contains("darwin")) {
            if (!x64 && !arm64) {
                throw unsupported(osName, architecture);
            }

            target = (x64 ? "x86_64" : "aarch64") + "-apple-darwin.tar.gz";
        } else {
            throw unsupported(osName, architecture);
        }

        return new Asset(target, DIGESTS.get(target), target.endsWith(".zip") ? "bore.exe" : "bore");
    }

    private static IOException unsupported(String os, String arch) {
        return new IOException("Automatic Bore download is unavailable for " + os + " / " + arch + ". Configure bore.executable.");
    }

    static boolean validCache(Path executable, Path receipt, String archiveDigest) throws IOException {
        if (!Files.isRegularFile(executable) || Files.isSymbolicLink(executable) || !Files.isRegularFile(receipt)
            || Files.isSymbolicLink(receipt) || Files.size(receipt) > 256 || Files.size(executable) > EXTRACT_LIMIT
        ) {
            return false;
        }

        String[] hashes = Files.readString(receipt).strip().split("\\R");
        return hashes.length == 2 && hashes[0].equals(archiveDigest) && hashes[1].equals(digest(executable));
    }

    private static void requireExecutable(Path executable) throws IOException {
        if (!Files.isRegularFile(executable) || !Files.isReadable(executable) || !Files.isExecutable(executable)) {
            throw new IOException("Bore executable is missing or cannot be executed: " + executable);
        }
    }

    private static void download(Asset asset, Path destination, int timeoutSeconds) throws IOException, InterruptedException {
        int timeout = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, timeoutSeconds) * 1000);
        long deadline = System.nanoTime() + Duration.ofMillis(timeout).toNanos();

        String filename = "bore-v" + VERSION + "-" + asset.target();
        URI uri = URI.create("https://github.com/ekzhang/bore/releases/download/v" + VERSION + "/" + filename);

        for (int redirects = 0; redirects < 6; redirects++) {
            if (Thread.interrupted()) {
                throw new InterruptedException("Bore download was cancelled.");
            }

            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setConnectTimeout(remainingMillis(deadline));
            connection.setReadTimeout(remainingMillis(deadline));
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "PortBridge/1.0");

            try {
                int status = connection.getResponseCode();
                if (status >= 300 && status < 400) {
                    String location = connection.getHeaderField("Location");

                    if (location == null) {
                        throw new IOException("Bore download redirect has no destination.");
                    }

                    uri = uri.resolve(location);
                    if (!"https".equals(uri.getScheme())) {
                        throw new IOException("Bore download requires HTTPS.");
                    }

                    continue;
                }

                if (status != 200) {
                    throw new IOException("Bore download returned HTTP " + status + ".");
                }

                if (connection.getContentLengthLong() > ARCHIVE_LIMIT) {
                    throw new IOException("Bore archive exceeds the size limit.");
                }

                try (InputStream input = connection.getInputStream(); OutputStream output = Files.newOutputStream(destination)) {
                    byte[] buffer = new byte[16 * 1024];
                    long total = 0;

                    int read;
                    while (true) {
                        if (Thread.interrupted()) {
                            throw new InterruptedException("Bore download was cancelled.");
                        }

                        connection.setReadTimeout(remainingMillis(deadline));
                        read = input.read(buffer);

                        if (read == -1) {
                            break;
                        }

                        total += read;

                        if (total > ARCHIVE_LIMIT) {
                            throw new IOException("Bore archive exceeds the size limit.");
                        }

                        output.write(buffer, 0, read);
                    }
                }
                return;
            } finally {
                connection.disconnect();
            }
        }

        throw new IOException("Bore download exceeded the redirect limit.");
    }

    private static int remainingMillis(long deadline) throws IOException {
        long remaining = deadline - System.nanoTime();

        if (remaining <= 0) {
            throw new IOException("Bore download timed out.");
        }

        return Math.clamp(remaining / 1_000_000, 1, Integer.MAX_VALUE);
    }

    static void extract(Path archive, Path destination, String executableName, boolean zip) throws IOException, InterruptedException {
        boolean found = false;

        long total = 0;
        int entries = 0;

        try (InputStream raw = Files.newInputStream(archive);
             InputStream input = zip ? new ZipInputStream(raw) : new TarArchiveInputStream(new GZIPInputStream(raw))
        ) {
            while (true) {
                String name;
                boolean directory;

                if (zip) {
                    ZipEntry entry = ((ZipInputStream) input).getNextEntry();

                    if (entry == null) {
                        break;
                    }

                    name = entry.getName();
                    directory = entry.isDirectory();
                } else {
                    TarArchiveEntry entry = ((TarArchiveInputStream) input).getNextEntry();

                    if (entry == null) {
                        break;
                    }

                    if (!entry.isFile() && !entry.isDirectory()) {
                        throw new IOException("Bore archive contains an unsupported entry.");
                    }

                    name = entry.getName();
                    directory = entry.isDirectory();
                }

                if (++entries > 256) {
                    throw new IOException("Bore archive contains too many entries.");
                }

                validateName(name);
                if (directory) {
                    continue;
                }

                boolean executable = name.equals(executableName) || name.endsWith("/" + executableName);
                if (executable && found) {
                    throw new IOException("Bore archive contains duplicate executables.");
                }

                try (OutputStream output = executable ? Files.newOutputStream(destination) : OutputStream.nullOutputStream()) {
                    byte[] buffer = new byte[16 * 1024];
                    int read;

                    while ((read = input.read(buffer)) != -1) {
                        if (Thread.interrupted()) {
                            throw new InterruptedException("Bore extraction was cancelled.");
                        }

                        total += read;

                        if (total > EXTRACT_LIMIT) {
                            throw new IOException("Bore archive exceeds the extraction size limit.");
                        }

                        output.write(buffer, 0, read);
                    }
                }

                if (executable) {
                    found = true;
                }
            }
        }

        if (!found || Files.size(destination) == 0) {
            throw new IOException("Bore archive does not contain a usable executable.");
        }
    }

    private static void validateName(String name) throws IOException {
        if (name.isBlank() || name.startsWith("/") || name.contains("\\") || name.contains(":") || name.indexOf('\0') >= 0) {
            throw new IOException("Bore archive contains an unsafe entry name.");
        }

        for (String part : name.split("/")) {
            if (part.equals("..")) {
                throw new IOException("Bore archive contains a traversal entry.");
            }
        }
    }

    static String digest(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[16 * 1024];
                int read;

                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }

            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static void makeExecutable(Path file) throws IOException {
        if (file.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(file, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
        }
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Identifies a pinned platform archive, its expected digest and executable name.
     *
     * @author Despical
     * <p>
     * Created at 10.10.2026
     */
    record Asset(String target, String digest, String executable) {
    }
}

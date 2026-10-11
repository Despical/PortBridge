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
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies platform selection, managed binary receipts and archive extraction.
 * <p>
 * Local archive fixtures exercise executable selection, traversal rejection,
 * duplicate entries and symbolic-link handling without downloading a release.
 *
 * @author Despical
 * <p>
 * Created at 10.10.2026
 */
class BoreInstallerTest {

    @TempDir
    Path directory;

    @Test
    void selectsNativePlatformsAndRejectsUnsupportedArchitecture() throws IOException {
        assertEquals("bore.exe", BoreInstaller.platform("Windows 11", "amd64").executable());
        assertEquals("i686-pc-windows-msvc.zip", BoreInstaller.platform("Windows 10", "x86").target());
        assertEquals("aarch64-unknown-linux-musl.tar.gz", BoreInstaller.platform("Linux", "aarch64").target());
        assertEquals("aarch64-apple-darwin.tar.gz", BoreInstaller.platform("Mac OS X", "arm64").target());
        assertThrows(IOException.class, () -> BoreInstaller.platform("Windows", "aarch64"));
    }

    @Test
    void detectsModifiedCachedExecutables() throws IOException {
        Path executable = directory.resolve("bore.exe");
        Path receipt = directory.resolve("verified.sha256");

        Files.writeString(executable, "verified executable");
        Files.writeString(receipt, "archive\n" + BoreInstaller.digest(executable) + "\n");

        assertTrue(BoreInstaller.validCache(executable, receipt, "archive"));

        Files.writeString(executable, "modified executable");
        assertFalse(BoreInstaller.validCache(executable, receipt, "archive"));
    }

    @Test
    void reportsMissingBinaryWhenDownloadIsDisabled() {
        BoreInstaller installer = new BoreInstaller(directory, Logger.getAnonymousLogger());

        IOException exception = assertThrows(IOException.class, () -> installer.resolve(new BridgeSettings.Binary(false, "", 20)));
        assertTrue(exception.getMessage().contains("bore.auto-download"));
    }

    @Test
    void extractsOnlyExecutableAndRejectsTraversalEntries() throws Exception {
        Path archive = directory.resolve("release.zip");
        Path output = directory.resolve("binary.tmp");
        zip(archive, "release/bore.exe", "executable");

        BoreInstaller.extract(archive, output, "bore.exe", true);
        assertEquals("executable", Files.readString(output));

        zip(archive, "../bore.exe", "unsafe");
        assertThrows(IOException.class, () -> BoreInstaller.extract(archive, output, "bore.exe", true));
        assertFalse(Files.exists(directory.getParent().resolve("bore.exe")));
    }

    @Test
    void rejectsArchivesWithoutBinaryOrWithDuplicateBinaries() throws Exception {
        Path archive = directory.resolve("release.zip");
        Path output = directory.resolve("binary.tmp");
        Files.createFile(output);

        zip(archive, "README", "not a binary");
        assertThrows(IOException.class, () -> BoreInstaller.extract(archive, output, "bore.exe", true));

        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (String name : new String[] {"a/bore.exe", "b/bore.exe"}) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write(1);
                zip.closeEntry();
            }
        }

        assertThrows(IOException.class, () -> BoreInstaller.extract(archive, output, "bore.exe", true));
    }

    @Test
    void extractsTarAndRejectsSymlinkExecutable() throws Exception {
        Path archive = directory.resolve("release.tar.gz");
        Path output = directory.resolve("binary.tmp");

        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(new GZIPOutputStream(Files.newOutputStream(archive)))) {
            TarArchiveEntry entry = new TarArchiveEntry("bore");
            entry.setSize(3);

            tar.putArchiveEntry(entry);
            tar.write(new byte[] {1, 2, 3});
            tar.closeArchiveEntry();
        }

        BoreInstaller.extract(archive, output, "bore", false);
        assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(output));

        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(new GZIPOutputStream(Files.newOutputStream(archive)))) {
            TarArchiveEntry entry = new TarArchiveEntry("bore", (byte) '2');
            entry.setLinkName("/bin/sh");

            tar.putArchiveEntry(entry);
            tar.closeArchiveEntry();
        }

        assertThrows(IOException.class, () -> BoreInstaller.extract(archive, output, "bore", false));
    }

    private static void zip(Path file, String name, String content) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }
}

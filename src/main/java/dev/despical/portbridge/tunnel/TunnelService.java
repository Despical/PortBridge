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

package dev.despical.portbridge.tunnel;

import dev.despical.portbridge.bore.BoreInstaller;
import dev.despical.portbridge.bore.BoreOutput;
import dev.despical.portbridge.bore.MinecraftStatusProbe;
import dev.despical.portbridge.config.BridgeSettings;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Owns one native Bore client and serializes its asynchronous lifecycle.
 * <p>
 * A single scheduled worker prepares the executable, checks the local Minecraft
 * listener, launches the process and probes the public route. Virtual threads drain
 * native output without blocking Bukkit. Immutable snapshots expose connection
 * state, addresses, retry timing and errors to commands and notifications.
 * <p>
 * Generation tokens prevent stale callbacks from reviving a stopped or replaced
 * session. Reload reconnects a desired tunnel while preserving an explicit stop.
 * Closing cancels pending work and terminates the owned process. State listeners
 * run outside the server thread and must marshal Bukkit access back to it.
 *
 * @author Despical
 * <p>
 * Created at 10.10.2026
 */
public final class TunnelService implements AutoCloseable {

    private final BoreInstaller installer;
    private final Logger logger;
    private final Consumer<TunnelSnapshot> listener;
    private final BiFunction<String, Map<String, ?>, String> messages;
    private final ScheduledExecutorService worker;
    private final ExecutorService outputReader = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicLong generation = new AtomicLong();
    private final ArrayDeque<String> recentOutput = new ArrayDeque<>();
    private final Thread shutdownHook;

    private volatile BridgeSettings settings;
    private volatile TunnelSnapshot snapshot;
    private volatile Process process;
    private volatile boolean desired;
    private volatile boolean closed;
    private ScheduledFuture<?> pendingRetry;
    private ScheduledFuture<?> connectionDeadline;
    private ScheduledFuture<?> healthCheck;
    private int failures;
    private int attempts;
    private boolean usingRandomPort;
    private Path executable;

    public TunnelService(BoreInstaller installer, BridgeSettings settings, Logger logger,
        Consumer<TunnelSnapshot> listener, BiFunction<String, Map<String, ?>, String> messages) {
        this.installer = installer;
        this.settings = settings;
        this.logger = logger;
        this.listener = listener;
        this.messages = messages;
        this.snapshot = stoppedSnapshot();
        this.worker = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "PortBridge-tunnel");
            thread.setDaemon(true);
            return thread;
        });
        this.shutdownHook = new Thread(this::destroyProcess, "PortBridge-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    public TunnelSnapshot snapshot() {
        return snapshot;
    }

    public synchronized boolean start() {
        if (closed || desired) {
            return false;
        }

        desired = true;
        long token = generation.incrementAndGet();
        submit(() -> begin(token));
        return true;
    }

    public synchronized boolean stop() {
        if (closed || !desired && snapshot.state() == TunnelState.STOPPED) {
            return false;
        }

        desired = false;
        long token = generation.incrementAndGet();
        submit(() -> {
            if (generation.get() == token) {
                cleanup();
                publish(stoppedSnapshot());
            }
        });
        return true;
    }

    public synchronized void restart() {
        if (closed) {
            return;
        }

        desired = true;
        long token = generation.incrementAndGet();
        submit(() -> begin(token));
    }

    /** Reload preserves an explicit stop; auto-start applies only to server startup. */
    public synchronized void reconfigure(BridgeSettings replacement) {
        settings = Objects.requireNonNull(replacement);

        if (desired) {
            restart();
        } else if (snapshot.state() == TunnelState.STOPPED) {
            snapshot = stoppedSnapshot();
        }
    }

    private void begin(long token) {
        if (!current(token)) {
            return;
        }

        cleanup();
        attempts = 0;
        usingRandomPort = false;
        connect(token);
    }

    private void connect(long token) {
        if (!current(token)) {
            return;
        }

        cleanup();
        BridgeSettings config = settings;
        String localAddress = address(config.local().host(), config.local().port());
        update(TunnelState.PREPARING, "", localAddress, null, null, -1, text("detail-preparing"), snapshot.lastError());

        try {
            executable = installer.resolve(config.binary());

            if (!current(token)) {
                return;
            }

            try {
                MinecraftStatusProbe.ping(config.local().host(), config.local().port(), config.health().timeoutSeconds() * 1000);
            } catch (IOException error) {
                throw new IOException(text("error-local-listener", "address", localAddress, "reason", message(error)), error);
            }

            if (!current(token)) {
                return;
            }

            launch(token, config, localAddress);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        } catch (Exception error) {
            fail(token, text("error-start", "reason", message(error)));
        }
    }

    private void launch(long token, BridgeSettings config, String localAddress) throws IOException {
        var arguments = new ArrayList<String>();
        arguments.add(executable.toString());
        arguments.add("local");
        arguments.add(Integer.toString(config.local().port()));
        arguments.add("--local-host");
        arguments.add(config.local().host());
        arguments.add("--to");
        arguments.add(config.relay().host());
        arguments.add("--port");
        arguments.add(Integer.toString(usingRandomPort ? 0 : config.relay().port()));

        ProcessBuilder builder = new ProcessBuilder(arguments).redirectErrorStream(true);
        builder.environment().put("RUST_LOG", "info");
        builder.environment().put("NO_COLOR", "1");
        builder.environment().remove("BORE_SECRET");

        if (!config.relay().secret().isBlank()) {
            builder.environment().put("BORE_SECRET", config.relay().secret());
        }

        recentOutput.clear();
        failures = 0;
        update(TunnelState.CONNECTING, "", localAddress, null, null, -1, text("detail-connecting", "host", config.relay().host()), snapshot.lastError());
        Process child = builder.start();
        process = child;

        if (!current(token)) {
            child.destroyForcibly();
            return;
        }

        connectionDeadline = worker.schedule(() -> {
            if (current(token) && process == child && !snapshot.hasAddress()) {
                fail(token, text("error-port-timeout"));
            }
        }, 20, TimeUnit.SECONDS);

        outputReader.submit(() -> readOutput(token, child));
    }

    private void readOutput(long token, Process child) {
        try (var reader = new BufferedReader(new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8))) {
            String line;

            while ((line = reader.readLine()) != null) {
                String clean = redact(BoreOutput.clean(line));
                submit(() -> onOutput(token, child, clean));
            }

            int exitCode = child.waitFor();
            submit(() -> onExit(token, child, exitCode));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        } catch (IOException error) {
            submit(() -> {
                if (current(token) && process == child) {
                    fail(token, text("error-output", "reason", message(error)));
                }
            });
        }
    }

    private void onOutput(long token, Process child, String line) {
        if (!current(token) || process != child || line.isBlank()) {
            return;
        }

        if (recentOutput.size() == 8) {
            recentOutput.removeFirst();
        }

        recentOutput.addLast(line.length() > 400 ? line.substring(0, 400) : line);

        if (settings.debug()) {
            logger.info("[Bore] " + line);
        }

        var port = BoreOutput.remotePort(line);

        if (port.isPresent() && !snapshot.hasAddress()) {
            allocated(token, child, port.getAsInt());
        }
    }

    private void allocated(long token, Process child, int port) {
        cancel(connectionDeadline);
        String host = settings.relay().publicHost().isBlank() ? settings.relay().host() : settings.relay().publicHost();
        update(TunnelState.CONNECTING, address(host, port), snapshot.localAddress(), Instant.now(), null, -1,
            text("detail-allocated"), snapshot.lastError());

        if (!settings.health().enabled()) {
            online(-1, text("detail-unverified"));
            return;
        }

        probe(token, child, host, port);

        if (current(token) && process == child) {
            healthCheck = worker.scheduleWithFixedDelay(() -> probe(token, child, host, port),
                settings.health().intervalSeconds(), settings.health().intervalSeconds(), TimeUnit.SECONDS);
        }
    }

    private void probe(long token, Process child, String host, int port) {
        if (!current(token) || process != child) {
            return;
        }

        try {
            long latency = MinecraftStatusProbe.ping(host, port, settings.health().timeoutSeconds() * 1000);

            if (!current(token) || process != child) {
                return;
            }

            failures = 0;
            online(latency, text("detail-online"));
        } catch (IOException error) {
            if (!current(token) || process != child) {
                return;
            }

            failures++;
            String reason = text("error-public-probe", "reason", message(error));

            if (failures >= settings.health().failureThreshold()) {
                fail(token, reason);
            } else {
                update(TunnelState.DEGRADED, snapshot.address(), snapshot.localAddress(), snapshot.connectedAt(), null, -1,
                    "Public check failed (" + failures + "/" + settings.health().failureThreshold() + "). Retrying the check.", reason);
            }
        }
    }

    private void online(long latency, String detail) {
        attempts = 0;
        update(TunnelState.ONLINE, snapshot.address(), snapshot.localAddress(), snapshot.connectedAt(), null, latency, detail, "");
    }

    private void onExit(long token, Process child, int exitCode) {
        if (!current(token) || process != child) {
            return;
        }

        String output = String.join(" | ", recentOutput);

        if (!usingRandomPort && settings.relay().port() != 0 && settings.relay().fallbackToRandom()
            && output.toLowerCase(Locale.ROOT).contains("port already in use")) {
            usingRandomPort = true;
            logger.warning("Requested relay port is in use; requesting an available port instead.");
            connect(token);
            return;
        }

        String reason = text(output.isBlank() ? "error-process-exit" : "error-process-output", "code", exitCode, "output", output);
        fail(token, reason);
    }

    private synchronized void fail(long token, String reason) {
        if (!current(token)) {
            return;
        }

        cleanup();
        attempts = Math.min(attempts, Integer.MAX_VALUE - 1) + 1;
        reason = redact(reason);

        if (!RetryPolicy.canRetry(settings.reconnect(), attempts)) {
            desired = false;
            update(TunnelState.FAILED, "", snapshot.localAddress(), null, null, -1, text("detail-failed"), reason);
            return;
        }

        int delay = RetryPolicy.delaySeconds(settings.reconnect(), attempts);
        Instant retryAt = Instant.now().plusSeconds(delay);
        update(TunnelState.RECONNECTING, "", snapshot.localAddress(), null, retryAt, -1, text("detail-reconnecting", "seconds", delay), reason);
        pendingRetry = worker.schedule(() -> connect(token), delay, TimeUnit.SECONDS);
    }

    private void update(TunnelState state, String address, String localAddress, Instant connectedAt, Instant retryAt,
        long latency, String detail, String lastError) {
        publish(new TunnelSnapshot(state, address, localAddress, connectedAt, attempts, retryAt, latency, detail, lastError));
    }

    private void publish(TunnelSnapshot replacement) {
        TunnelSnapshot previous = snapshot;
        snapshot = replacement;

        if (previous.state() != replacement.state() || !previous.address().equals(replacement.address())) {
            logger.info(replacement.state() + " - " + replacement.detail());

            if (!replacement.lastError().isBlank()) {
                logger.warning(replacement.lastError());
            }

            if (replacement.hasAddress() && !replacement.address().equals(previous.address())) {
                logger.info("Join address: " + replacement.address());
            }

            try {
                listener.accept(replacement);
            } catch (RuntimeException error) {
                logger.warning("Could not deliver tunnel notification: " + message(error));
            }
        }
    }

    private boolean current(long token) {
        return !closed && desired && generation.get() == token;
    }

    private void submit(Runnable action) {
        try {
            worker.execute(action);
        } catch (RejectedExecutionException ignored) {
            // Shutdown has already invalidated every pending generation.
        }
    }

    private void cleanup() {
        cancel(pendingRetry);
        cancel(connectionDeadline);
        cancel(healthCheck);
        destroyProcess();
    }

    private void destroyProcess() {
        Process child = process;
        process = null;

        if (child != null && child.isAlive()) {
            child.destroyForcibly();
        }
    }

    private static void cancel(ScheduledFuture<?> task) {
        if (task != null) {
            task.cancel(false);
        }
    }

    private String text(String key, Object... placeholders) {
        var values = new HashMap<String, Object>();
        for (int index = 0; index < placeholders.length; index += 2) {
            values.put((String) placeholders[index], placeholders[index + 1]);
        }
        return messages.apply(key, values);
    }

    private TunnelSnapshot stoppedSnapshot() {
        return new TunnelSnapshot(TunnelState.STOPPED, "", "", null, 0, null, -1, text("detail-stopped"), "");
    }

    private String redact(String text) {
        String secret = settings.relay().secret();
        return secret.isBlank() ? text : text.replace(secret, "[redacted]");
    }

    private static String message(Exception error) {
        return Objects.toString(error.getMessage(), error.getClass().getSimpleName());
    }

    private static String address(String host, int port) {
        return (host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host) + ":" + port;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }

        closed = true;
        desired = false;
        generation.incrementAndGet();
        destroyProcess();
        worker.shutdownNow();
        outputReader.shutdownNow();
        snapshot = stoppedSnapshot();

        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
            // The JVM is already shutting down; the hook remains a final cleanup guard.
        }
    }
}

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

package dev.despical.portbridge.config;

/**
 * Captures validated bridge configuration for asynchronous tunnel operations.
 * <p>
 * Nested records group startup, local listener, relay, executable, retry and health
 * settings. The loader resolves automatic listener detection before constructing
 * this value, allowing workers to read settings without accessing Bukkit state.
 * <p>
 * These records store values without performing validation themselves. Construct
 * operational settings through SettingsLoader so ranges and timing relationships
 * are checked before a tunnel uses them.
 *
 * @author Despical
 * <p>
 * Created at 10.10.2026
 */
public record BridgeSettings(Startup startup, Local local, Relay relay, Binary binary, Reconnect reconnect,
    Health health, boolean notifyOnJoin, boolean debug) {

    /**
     * Stores whether and when automatic tunnel startup should be scheduled.
     *
     * @author Despical
     * <p>
     * Created at 10.10.2026
     */
    public record Startup(boolean autoStart, int delaySeconds) {
    }

    /**
     * Stores the resolved Minecraft listener and automatic-detection preference.
     * <p>
     * The tunnel uses host and port directly; automatic detection is performed by the loader.
     *
     * @author Despical
     * <p>
     * Created at 10.10.2026
     */
    public record Local(boolean autoDetect, String host, int port) {
    }

    /**
     * Stores relay endpoints, public-port selection and the optional shared secret.
     * <p>
     * The secret is sensitive configuration and must be removed from shared diagnostics.
     *
     * @author Despical
     * <p>
     * Created at 10.10.2026
     */
    public record Relay(String host, String publicHost, int port, boolean fallbackToRandom, String secret) {
    }

    /**
     * Stores managed-download policy, an optional executable path and download timeout.
     *
     * @author Despical
     * <p>
     * Created at 10.10.2026
     */
    public record Binary(boolean autoDownload, String executable, int downloadTimeoutSeconds) {
    }

    /**
     * Stores retry enablement, capped delay bounds and the optional attempt limit.
     *
     * @author Despical
     * <p>
     * Created at 10.10.2026
     */
    public record Reconnect(boolean enabled, int initialDelaySeconds, int maxDelaySeconds, int maxAttempts) {
    }

    /**
     * Stores public-probe enablement, timing and the consecutive-failure threshold.
     *
     * @author Despical
     * <p>
     * Created at 10.10.2026
     */
    public record Health(boolean enabled, int intervalSeconds, int timeoutSeconds, int failureThreshold) {
    }
}

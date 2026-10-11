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

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Validates configuration before publishing an immutable bridge settings value.
 * <p>
 * Loading reads config.yml and resolves the local listener from the running server
 * when automatic detection is enabled. Missing keys use documented defaults; invalid
 * types, hostnames, port ranges and incompatible timing values reject the entire load.
 * <p>
 * Read server properties on the server thread before submitting tunnel work.
 * Successful loading does not mutate the running tunnel; the plugin applies the
 * replacement only after its message configuration has also been validated.
 *
 * @author Despical
 * <p>
 * Created at 10.10.2026
 */
public final class SettingsLoader {

    private final JavaPlugin plugin;

    public SettingsLoader(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public BridgeSettings load() {
        plugin.saveDefaultConfig();
        var config = new YamlConfiguration();

        try {
            config.load(new File(plugin.getDataFolder(), "config.yml"));
        } catch (IOException | InvalidConfigurationException exception) {
            throw new IllegalArgumentException("Could not read config.yml: " + exception.getMessage(), exception);
        }

        return parse(config, plugin.getServer().getIp(), plugin.getServer().getPort());
    }

    static BridgeSettings parse(ConfigurationSection config, String serverHost, int serverPort) {
        for (String group : new String[] {"startup", "local", "relay", "bore", "reconnect", "health", "notifications"}) {
            if (config.contains(group) && !config.isConfigurationSection(group)) {
                throw invalid(group, "a configuration section");
            }
        }

        boolean autoDetect = bool(config, "local.auto-detect", true);
        String localHost = host(string(config, "local.host", "127.0.0.1"), "local.host", false);
        int localPort = integer(config, "local.port", 25565, 1, 65535);

        if (autoDetect) {
            localHost = serverHost == null || serverHost.isBlank() || serverHost.equals("0.0.0.0")
                || serverHost.equals("::") || serverHost.equals("0:0:0:0:0:0:0:0") ? "127.0.0.1"
                : host(serverHost, "server-ip", false);
            localPort = serverPort;

            if (localPort < 1 || localPort > 65535) {
                throw invalid("server-port", "an integer between 1 and 65535");
            }
        }

        int initialDelay = integer(config, "reconnect.initial-delay-seconds", 5, 1, 3600);
        int maxDelay = integer(config, "reconnect.max-delay-seconds", 60, 1, 3600);
        int interval = integer(config, "health.interval-seconds", 30, 5, 3600);
        int timeout = integer(config, "health.timeout-seconds", 5, 1, 30);

        if (maxDelay < initialDelay) {
            throw new IllegalArgumentException("reconnect.max-delay-seconds must be at least reconnect.initial-delay-seconds.");
        }

        if (timeout > interval) {
            throw new IllegalArgumentException("health.timeout-seconds must not exceed health.interval-seconds.");
        }

        String secret = string(config, "relay.secret", "");
        String executable = string(config, "bore.executable", "");

        if (secret.length() > 4096 || secret.chars().anyMatch(Character::isISOControl)) {
            throw invalid("relay.secret", "a string without control characters, up to 4096 characters");
        }

        if (executable.chars().anyMatch(Character::isISOControl)) {
            throw invalid("bore.executable", "a file path without control characters");
        }

        return new BridgeSettings(
            new BridgeSettings.Startup(bool(config, "startup.auto-start", true), integer(config, "startup.delay-seconds", 3, 0, 3600)),
            new BridgeSettings.Local(autoDetect, localHost, localPort),
            new BridgeSettings.Relay(host(string(config, "relay.host", "bore.pub"), "relay.host", false),
                host(string(config, "relay.public-host", ""), "relay.public-host", true),
                integer(config, "relay.port", 0, 0, 65535), bool(config, "relay.fallback-to-random", true), secret),
            new BridgeSettings.Binary(bool(config, "bore.auto-download", true), executable,
                integer(config, "bore.download-timeout-seconds", 60, 5, 600)),
            new BridgeSettings.Reconnect(bool(config, "reconnect.enabled", true), initialDelay, maxDelay,
                integer(config, "reconnect.max-attempts", 0, 0, 1000)),
            new BridgeSettings.Health(bool(config, "health.enabled", true), interval, timeout,
                integer(config, "health.failure-threshold", 3, 1, 100)),
            bool(config, "notifications.on-admin-join", true), bool(config, "debug", false)
        );
    }

    private static boolean bool(ConfigurationSection config, String path, boolean fallback) {
        Object value = config.get(path);
        if (value == null) {
            return fallback;
        }

        if (value instanceof Boolean result) {
            return result;
        }

        throw invalid(path, "true or false");
    }

    private static int integer(ConfigurationSection config, String path, int fallback, int min, int max) {
        Object value = config.get(path);
        if (value == null) {
            return fallback;
        }

        if (!(value instanceof Integer) && !(value instanceof Long)) {
            throw invalid(path, "an integer between " + min + " and " + max);
        }

        long result = ((Number) value).longValue();
        if (result < min || result > max) {
            throw invalid(path, "an integer between " + min + " and " + max);
        }

        return (int) result;
    }

    private static String string(ConfigurationSection config, String path, String fallback) {
        Object value = config.get(path);
        if (value == null) {
            return fallback;
        }

        if (value instanceof String result) {
            return result;
        }

        throw invalid(path, "a string (quote numeric-looking values)");
    }

    private static String host(String value, String path, boolean optional) {
        String result = value.trim();
        if (optional && result.isEmpty()) {
            return result;
        }

        if (result.length() > 253 || result.isEmpty()) {
            throw invalid(path, "a hostname or IP address without a port");
        }

        if (result.contains(":")) {
            try {
                if (!result.matches("[0-9a-fA-F:]+") || InetAddress.getByName(result).getAddress().length != 16) {
                    throw invalid(path, "a hostname or IP address without a port");
                }
            } catch (UnknownHostException ignored) {
                throw invalid(path, "a hostname or IP address without a port");
            }
        } else {
            for (String label : result.split("\\.", -1)) {
                if (label.length() > 63 || !label.matches("[a-zA-Z0-9](?:[a-zA-Z0-9-]*[a-zA-Z0-9])?")) {
                    throw invalid(path, "a hostname or IP address without a port");
                }
            }
        }

        return result;
    }

    private static IllegalArgumentException invalid(String path, String expected) {
        return new IllegalArgumentException(path + " must be " + expected + ".");
    }
}

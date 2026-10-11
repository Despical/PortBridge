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

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies automatic listener detection and strict configuration validation.
 * <p>
 * Tests cover manual targets, wildcard bindings, value types, hostnames, TCP ports,
 * retry timing, probe timing and control characters in sensitive strings.
 *
 * @author Despical
 * <p>
 * Created at 10.10.2026
 */
class SettingsLoaderTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "0.0.0.0", "::", "0:0:0:0:0:0:0:0"})
    void detectsPaperPortAndConvertsWildcardBindings(String binding) {
        BridgeSettings settings = SettingsLoader.parse(new YamlConfiguration(), binding, 25567);
        assertEquals("127.0.0.1", settings.local().host());
        assertEquals(25567, settings.local().port());
        assertTrue(settings.startup().autoStart());
        assertEquals(0, settings.relay().port());
    }

    @Test
    void keepsSpecificPaperBindingAndIgnoresManualTarget() {
        var config = new YamlConfiguration();
        config.set("local.host", "other.example");
        config.set("local.port", 25570);
        BridgeSettings settings = SettingsLoader.parse(config, "192.168.1.5", 25566);
        assertEquals("192.168.1.5", settings.local().host());
        assertEquals(25566, settings.local().port());
    }

    @Test
    void allowsManualTargetAndExplicitPublicAddress() {
        var config = new YamlConfiguration();
        config.set("local.auto-detect", false);
        config.set("local.host", "::1");
        config.set("local.port", 25568);
        config.set("relay.public-host", "play.example.com");
        config.set("relay.port", 42424);
        config.set("startup.auto-start", false);
        BridgeSettings settings = SettingsLoader.parse(config, "", 25565);
        assertFalse(settings.local().autoDetect());
        assertFalse(settings.startup().autoStart());
        assertEquals("::1", settings.local().host());
        assertEquals(25568, settings.local().port());
        assertEquals("play.example.com", settings.relay().publicHost());
        assertEquals(42424, settings.relay().port());
    }

    @ParameterizedTest
    @ValueSource(strings = {"local.port", "relay.port", "reconnect.initial-delay-seconds", "health.timeout-seconds"})
    void rejectsQuotedOrFractionalIntegerValues(String path) {
        var config = new YamlConfiguration();
        config.set(path, "25565");
        assertThrows(IllegalArgumentException.class, () -> SettingsLoader.parse(config, "", 25565));
        config.set(path, 2.5);
        assertThrows(IllegalArgumentException.class, () -> SettingsLoader.parse(config, "", 25565));
    }

    @Test
    void rejectsOverflowAndInvalidPortsEvenWhenAutoDetecting() {
        var config = new YamlConfiguration();
        config.set("local.port", 0);
        assertThrows(IllegalArgumentException.class, () -> SettingsLoader.parse(config, "", 25565));
        config.set("local.port", 25565);
        config.set("relay.port", Long.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, () -> SettingsLoader.parse(config, "", 25565));
    }

    @Test
    void rejectsIncorrectBooleanAndSectionTypes() {
        var config = new YamlConfiguration();
        config.set("debug", "false");
        assertThrows(IllegalArgumentException.class, () -> SettingsLoader.parse(config, "", 25565));
        config.set("debug", false);
        config.set("relay", "bore.pub");
        assertThrows(IllegalArgumentException.class, () -> SettingsLoader.parse(config, "", 25565));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://bore.pub", "bore.pub:25565", "-invalid.example", "invalid..example", "has space", ""})
    void rejectsNonHostRelayValues(String host) {
        var config = new YamlConfiguration();
        config.set("relay.host", host);
        assertThrows(IllegalArgumentException.class, () -> SettingsLoader.parse(config, "", 25565));
    }

    @Test
    void rejectsInconsistentRetryAndHealthSettings() {
        var config = new YamlConfiguration();
        config.set("reconnect.initial-delay-seconds", 60);
        config.set("reconnect.max-delay-seconds", 5);
        assertThrows(IllegalArgumentException.class, () -> SettingsLoader.parse(config, "", 25565));
        config.set("reconnect.max-delay-seconds", 60);
        config.set("health.interval-seconds", 5);
        config.set("health.timeout-seconds", 6);
        assertThrows(IllegalArgumentException.class, () -> SettingsLoader.parse(config, "", 25565));
    }

    @Test
    void rejectsControlCharactersInSecretAndExecutablePath() {
        var config = new YamlConfiguration();
        config.set("relay.secret", "first\nsecond");
        assertThrows(IllegalArgumentException.class, () -> SettingsLoader.parse(config, "", 25565));
        config.set("relay.secret", " secret with spaces ");
        config.set("bore.executable", "C:\\bore.exe\nargument");
        assertThrows(IllegalArgumentException.class, () -> SettingsLoader.parse(config, "", 25565));
        config.set("bore.executable", "C:\\Program Files\\bore.exe");
        assertEquals(" secret with spaces ", SettingsLoader.parse(config, "", 25565).relay().secret());
    }
}

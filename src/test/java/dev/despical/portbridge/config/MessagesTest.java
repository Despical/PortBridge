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

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies configurable replies, fallback values and literal placeholder expansion.
 *
 * @author Despical
 * <p>
 * Created at 11.10.2026
 */
class MessagesTest {

    @Test
    void missingNewKeysFallBackWithoutReplacingExistingOverrides() throws Exception {
        var configured = new YamlConfiguration();
        configured.set("command-started", "Custom start reply.");
        configured.set("acf.acf-core.permission_denied", "Custom denial.");
        var messages = Messages.parse(defaults(), configured);

        assertEquals("Custom start reply.", messages.message("command-started"));
        assertEquals("Custom denial.", messages.message("acf.acf-core.permission_denied"));
        assertEquals("Retrying in 12 seconds.", messages.format("detail-reconnecting", Map.of("seconds", 12)));
        assertEquals(0x70C8C3, messages.color("accent").value());
    }

    @Test
    void substitutionsRemainLiteralAndDoNotExpandRecursively() {
        String diagnostic = "$1 \\ <red> {seconds}";
        assertEquals("Failure: " + diagnostic + "; 3", Messages.interpolate("Failure: {reason}; {seconds}",
            Map.of("reason", diagnostic, "seconds", 3)));
        assertEquals("Keep {unknown}", Messages.interpolate("Keep {unknown}", Map.of()));
    }

    @Test
    void validatesFrameworkRepliesAndThemeSectionsBeforePublishing() throws Exception {
        var defaults = defaults();
        var invalid = new YamlConfiguration();
        invalid.set("acf.acf-core.permission_denied", "Two\nlines");
        assertThrows(IllegalArgumentException.class, () -> Messages.parse(defaults, invalid));
        invalid.set("acf.acf-core.permission_denied", "Denied.");
        invalid.set("theme.accent", "cyan");
        assertThrows(IllegalArgumentException.class, () -> Messages.parse(defaults, invalid));
        invalid.set("theme", "wrong section type");
        assertThrows(IllegalArgumentException.class, () -> Messages.parse(defaults, invalid));
    }

    @Test
    void rejectsBlankOrNonStringCommandReplies() throws Exception {
        var defaults = defaults();
        for (Object value : new Object[] {"", "  ", 42, true}) {
            var invalid = new YamlConfiguration();
            invalid.set("command-reloaded", value);
            assertThrows(IllegalArgumentException.class, () -> Messages.parse(defaults, invalid));
        }
    }

    @Test
    void publishedViewsRetainValuesAndRejectMutation() throws Exception {
        var configured = new YamlConfiguration();
        configured.set("command-started", "Before reload.");
        var original = Messages.parse(defaults(), configured);
        configured.set("command-started", "After reload.");
        var replacement = Messages.parse(defaults(), configured);

        assertEquals("Before reload.", original.message("command-started"));
        assertEquals("After reload.", replacement.message("command-started"));
        assertThrows(UnsupportedOperationException.class, () -> original.messages().put("command-started", "mutated"));
        assertTrue(original.messages().containsKey("error-process-output"));
    }

    private static YamlConfiguration defaults() throws Exception {
        var defaults = new YamlConfiguration();
        try (var reader = new InputStreamReader(Objects.requireNonNull(
            MessagesTest.class.getResourceAsStream("/messages.yml")), StandardCharsets.UTF_8)) {
            defaults.load(reader);
        }
        return defaults;
    }
}

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

import net.kyori.adventure.text.format.TextColor;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Loads and validates configurable command, tunnel and framework messages.
 * <p>
 * Reload publishes one immutable appearance only after every bundled key and theme
 * color is validated. Missing keys retain bundled defaults. Workers can read the
 * snapshot without accessing Bukkit configuration.
 * <p>
 * Placeholder substitution is literal and runs once, so diagnostic values cannot
 * introduce formatting markup or recursively replace another placeholder.
 *
 * @author Despical
 * <p>
 * Created at 11.10.2026
 */
public final class Messages {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z][a-z0-9-]*)}");
    private final JavaPlugin plugin;
    private volatile Appearance appearance;

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");

        if (!file.isFile()) {
            plugin.saveResource("messages.yml", false);
        }

        var defaults = new YamlConfiguration();
        var loaded = new YamlConfiguration();

        try (var input = new InputStreamReader(Objects.requireNonNull(plugin.getResource("messages.yml")), StandardCharsets.UTF_8)) {
            defaults.load(input);
            loaded.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            throw new IllegalArgumentException("Could not read messages.yml: " + exception.getMessage(), exception);
        }

        appearance = parse(defaults, loaded);
    }

    static Appearance parse(ConfigurationSection defaults, ConfigurationSection loaded) {
        var values = new HashMap<String, String>();
        var colors = new HashMap<String, TextColor>();

        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key)) {
                if (loaded.contains(key) && !loaded.isConfigurationSection(key)) {
                    throw new IllegalArgumentException("messages.yml: " + key + " must be a configuration section.");
                }

                continue;
            }

            Object configured = loaded.contains(key) ? loaded.get(key) : defaults.get(key);
            if (!(configured instanceof String value) || value.isBlank() || value.codePoints().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("messages.yml: " + key + " must be a non-empty, single-line string.");
            }

            if (key.startsWith("theme.")) {
                TextColor color = value.matches("#[0-9a-fA-F]{6}") ? TextColor.fromHexString(value) : null;
                if (color == null) {
                    throw new IllegalArgumentException("messages.yml: " + key + " must be a hex color such as #70C8C3.");
                }

                colors.put(key.substring("theme.".length()), color);
            } else {
                values.put(key, value);
            }
        }

        return new Appearance(Map.copyOf(values), Map.copyOf(colors));
    }

    public Appearance view() {
        return appearance;
    }

    public String format(String key, Map<String, ?> replacements) {
        return appearance.format(key, replacements);
    }

    static String interpolate(String template, Map<String, ?> replacements) {
        var matcher = PLACEHOLDER.matcher(template);
        return matcher.replaceAll(match -> java.util.regex.Matcher.quoteReplacement(
            replacements.containsKey(match.group(1)) ? String.valueOf(replacements.get(match.group(1))) : match.group()));
    }

    /**
     * Retains validated literal messages and theme colors for one render snapshot.
     *
     * @author Despical
     * <p>
     * Created at 11.10.2026
     */
    public record Appearance(Map<String, String> messages, Map<String, TextColor> colors) {

        public String message(String key) {
            return Objects.requireNonNull(messages.get(key), "Missing message: " + key);
        }

        public TextColor color(String key) {
            return Objects.requireNonNull(colors.get(key), "Missing theme color: " + key);
        }

        public String format(String key, Map<String, ?> replacements) {
            return interpolate(message(key), replacements);
        }
    }
}

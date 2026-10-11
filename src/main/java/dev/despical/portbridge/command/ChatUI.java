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

package dev.despical.portbridge.command;

import dev.despical.portbridge.config.BridgeSettings;
import dev.despical.portbridge.config.Messages;
import dev.despical.portbridge.config.Messages.Appearance;
import dev.despical.portbridge.tunnel.TunnelSnapshot;
import dev.despical.portbridge.tunnel.TunnelState;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.map.MinecraftFont;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renders Adventure chat panels with configured text and permission-aware actions.
 * <p>
 * Status, help and connection panels share typography, theme colors, separators
 * and hover text. Addresses use clipboard actions, while lifecycle buttons suggest
 * or run commands only when the sender has the matching permission. Message values
 * are rendered literally rather than parsed as formatting markup.
 * <p>
 * Reload validates messages and hex colors before publishing an immutable appearance
 * snapshot. Rendering captures one appearance value per panel. Bukkit sender access
 * and configuration reloads belong on the server thread.
 *
 * @author Despical
 * <p>
 * Created at 10.10.2026
 */
public final class ChatUI {

    private static final String ASCII_ADVANCES = "42466662444626266666666666225656766666666466666666666666666464663666665662653666666646666664247";

    private final Messages messages;

    public ChatUI(JavaPlugin plugin) {
        this(new Messages(plugin));
    }

    public ChatUI(Messages messages) {
        this.messages = messages;
    }

    public void noticeKey(CommandSender sender, String key) {
        notice(sender, messages.format(key, Map.of()));
    }

    public void errorKey(CommandSender sender, String key, Map<String, ?> replacements) {
        error(sender, messages.format(key, replacements));
    }

    public void reload() {
        messages.reload();
    }

    public void status(CommandSender sender, TunnelSnapshot snapshot, BridgeSettings settings) {
        Appearance view = messages.view();
        header(sender, view, "status-title", "status-subtitle");
        row(sender, view, "label-state", text(view, "state-" + snapshot.state().name().toLowerCase(Locale.ROOT), stateColor(view, snapshot.state())));

        if (snapshot.hasAddress() && sender.hasPermission("portbridge.address")) {
            row(sender, view, "label-address", connection(view, snapshot.address()));
        }

        String local = snapshot.localAddress().isBlank() ? endpoint(settings.local().host(), settings.local().port()) : snapshot.localAddress();
        row(sender, view, "label-local", Component.text(local, view.color("text")));
        row(sender, view, "label-relay", Component.text(settings.relay().host(), view.color("text")));
        row(sender, view, "label-health", text(view, settings.health().enabled() ? "health-enabled" : "health-disabled",
            view.color(settings.health().enabled() ? "muted" : "warning")));

        if (snapshot.connectedAt() != null) {
            row(sender, view, "label-uptime", Component.text(duration(Duration.between(snapshot.connectedAt(), Instant.now()).getSeconds(), view), view.color("text")));
        }

        if (snapshot.latencyMillis() >= 0) {
            row(sender, view, "label-latency", Component.text(view.format("latency-value", Map.of("millis", snapshot.latencyMillis())), view.color("text")));
        }

        if (snapshot.nextRetryAt() != null) {
            long seconds = Math.max(0, Duration.between(Instant.now(), snapshot.nextRetryAt()).getSeconds());
            row(sender, view, "label-retry", Component.text(view.format("retry-value", Map.of("seconds", seconds, "attempt", snapshot.reconnectAttempt())), view.color("warning")));
        }

        if (!snapshot.detail().isBlank()) {
            detailRow(sender, view, "label-detail", snapshot.detail(), "muted");
        }

        if (!snapshot.lastError().isBlank()) {
            detailRow(sender, view, "label-error", snapshot.lastError(), "error");
        }

        actions(sender, view, snapshot, true);
        footer(sender, view);
    }

    public void help(CommandSender sender) {
        Appearance view = messages.view();
        header(sender, view, "help-title", "help-subtitle");
        command(sender, view, "status", "portbridge.status");
        command(sender, view, "address", "portbridge.address");
        command(sender, view, "start", "portbridge.manage");
        command(sender, view, "stop", "portbridge.manage");
        command(sender, view, "restart", "portbridge.manage");
        command(sender, view, "reload", "portbridge.reload");
        sender.sendMessage(Component.empty());
        sender.sendMessage(text(view, "help-hint", view.color("muted")));
        footer(sender, view);
    }

    public void address(CommandSender sender, TunnelSnapshot snapshot) {
        if (!snapshot.hasAddress()) {
            notice(sender, messages.view().message("no-address"));
            return;
        }

        addressPanel(sender, snapshot, "address-title", "address-subtitle");
    }

    public void connected(CommandSender sender, TunnelSnapshot snapshot) {
        if (snapshot.hasAddress()) {
            String title = snapshot.state() != TunnelState.ONLINE ? "address-title"
                : snapshot.latencyMillis() < 0 ? "relay-title" : "connected-title";
            addressPanel(sender, snapshot, title, "address-subtitle");
        }
    }

    public void notice(CommandSender sender, String message) {
        Appearance view = messages.view();
        sender.sendMessage(prefix(view).append(Component.text(message, view.color("text"))));
    }

    public void error(CommandSender sender, String reason) {
        Appearance view = messages.view();
        if (!(sender instanceof Player)) {
            sender.sendMessage(prefix(view).append(Component.text(reason, view.color("error"))));
            return;
        }

        List<String> lines = wrap(shorten(reason), 240);
        for (int index = 0; index < lines.size(); index++) {
            sender.sendMessage((index == 0 ? prefix(view) : Component.text("  "))
                .append(Component.text(lines.get(index), view.color("error"))));
        }
    }

    private void addressPanel(CommandSender sender, TunnelSnapshot snapshot, String title, String subtitle) {
        if (!sender.hasPermission("portbridge.address")) {
            return;
        }

        Appearance view = messages.view();
        header(sender, view, title, subtitle);
        sender.sendMessage(center(sender, connection(view, snapshot.address()).decorate(TextDecoration.BOLD)));
        sender.sendMessage(Component.empty());
        sender.sendMessage(center(sender, text(view, "address-hint", view.color("muted"))));

        if (snapshot.state() != TunnelState.ONLINE) {
            sender.sendMessage(center(sender, text(view, "state-" + snapshot.state().name().toLowerCase(Locale.ROOT), stateColor(view, snapshot.state()))));
        } else if (snapshot.latencyMillis() < 0) {
            sender.sendMessage(center(sender, text(view, "unverified-hint", view.color("warning"))));
        }

        actions(sender, view, snapshot, false);
        footer(sender, view);
    }

    private void actions(CommandSender sender, Appearance view, TunnelSnapshot snapshot, boolean controls) {
        var buttons = new ArrayList<Component>();

        if (snapshot.hasAddress() && sender.hasPermission("portbridge.address")) {
            Component copy = text(view, "button-copy", view.color("accent")).decorate(TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(text(view, "hover-copy", view.color("text"))));
            buttons.add(sender instanceof Player ? copy.clickEvent(ClickEvent.copyToClipboard(snapshot.address())) : copy);
        }

        if (controls && sender.hasPermission("portbridge.manage")) {
            if (snapshot.state() == TunnelState.STOPPED || snapshot.state() == TunnelState.FAILED) {
                buttons.add(button(sender, view, "start", "accent"));
            } else {
                buttons.add(button(sender, view, "stop", "warning"));
                buttons.add(button(sender, view, "restart", "secondary"));
            }
        }

        if (sender.hasPermission("portbridge.status")) {
            buttons.add(button(sender, view, "status", "secondary"));
        }

        if (buttons.isEmpty()) {
            return;
        }

        sender.sendMessage(Component.empty());
        sender.sendMessage(center(sender, Component.join(net.kyori.adventure.text.JoinConfiguration.separator(Component.text("   ")), buttons)));
    }

    private Component button(CommandSender sender, Appearance view, String command, String color) {
        Component result = text(view, "button-" + command, view.color(color)).decorate(TextDecoration.BOLD)
            .hoverEvent(HoverEvent.showText(text(view, "hover-" + command, view.color("text"))));
        return sender instanceof Player ? result.clickEvent(ClickEvent.runCommand("/portbridge " + command))
            : result.append(Component.text(view.format("console-command-suffix", Map.of("command", "/pb " + command)), view.color("muted")).decoration(TextDecoration.BOLD, false));
    }

    private void command(CommandSender sender, Appearance view, String command, String permission) {
        if (!sender.hasPermission(permission)) {
            return;
        }

        Component name = Component.text(view.format("help-command-label", Map.of("command", "/pb " + command)), view.color("accent"));
        if (sender instanceof Player) {
            name = name.clickEvent(ClickEvent.suggestCommand("/portbridge " + command));
        }

        sender.sendMessage(name.append(Component.text("  " + view.message("help-" + command), view.color("text"))));
    }

    private void header(CommandSender sender, Appearance view, String title, String subtitle) {
        sender.sendMessage(center(sender, text(view, "separator", view.color("separator"))));
        sender.sendMessage(Component.empty());
        sender.sendMessage(center(sender, text(view, title, view.color("accent")).decorate(TextDecoration.BOLD)));
        sender.sendMessage(center(sender, text(view, subtitle, view.color("muted"))));
        sender.sendMessage(Component.empty());
    }

    private void footer(CommandSender sender, Appearance view) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(center(sender, text(view, "separator", view.color("separator"))));
    }

    private void row(CommandSender sender, Appearance view, String label, Component value) {
        sender.sendMessage(Component.text("  " + view.message(label) + "  ", view.color("label")).append(value));
    }

    private void detailRow(CommandSender sender, Appearance view, String label, String value, String color) {
        if (!(sender instanceof Player)) {
            row(sender, view, label, Component.text(value, view.color(color)));
            return;
        }

        String prefix = "  " + view.message(label) + "  ";
        List<String> lines = wrap(shorten(value), Math.max(120, 304 - textWidth(prefix, false)));

        for (int index = 0; index < lines.size(); index++) {
            if (index == 0) {
                row(sender, view, label, Component.text(lines.get(index), view.color(color)));
            } else {
                sender.sendMessage(Component.text(" ".repeat(Math.max(2, textWidth(prefix, false) / 4)))
                    .append(Component.text(lines.get(index), view.color(color))));
            }
        }
    }

    private static String shorten(String value) {
        String clean = value.replaceAll("\\s+", " ").trim();
        return clean.length() > 240 ? clean.substring(0, 237) + "..." : clean;
    }

    private Component prefix(Appearance view) {
        return Component.empty().append(text(view, "prefix", view.color("accent")).decorate(TextDecoration.BOLD)).append(Component.text(" "));
    }

    private Component connection(Appearance view, String address) {
        return Component.text(address, view.color("accent")).clickEvent(ClickEvent.copyToClipboard(address))
            .hoverEvent(HoverEvent.showText(text(view, "hover-copy", view.color("text"))));
    }

    private Component text(Appearance view, String key, TextColor color) {
        return Component.text(view.message(key), color);
    }

    private TextColor stateColor(Appearance view, TunnelState state) {
        return view.color(switch (state) {
            case ONLINE -> "accent";
            case FAILED -> "error";
            case DEGRADED, RECONNECTING -> "warning";
            case PREPARING, CONNECTING -> "secondary";
            case STOPPED -> "muted";
        });
    }

    private static Component center(CommandSender sender, Component component) {
        if (!(sender instanceof Player)) {
            return component;
        }
        int padding = Math.max(0, (320 - pixelWidth(component, false)) / 8);
        return Component.text(" ".repeat(padding)).append(component);
    }

    static int pixelWidth(Component component, boolean inheritedBold) {
        TextDecoration.State style = component.decoration(TextDecoration.BOLD);
        boolean bold = style == TextDecoration.State.NOT_SET ? inheritedBold : style == TextDecoration.State.TRUE;
        int width = component instanceof TextComponent text ? textWidth(text.content(), bold) : 0;

        for (Component child : component.children()) {
            width += pixelWidth(child, bold);
        }

        return width;
    }

    static List<String> wrap(String text, int width) {
        var lines = new ArrayList<String>();
        StringBuilder line = new StringBuilder();

        for (String word : text.split("\\s+")) {
            if (!line.isEmpty() && textWidth(line + " " + word, false) > width) {
                lines.add(line.toString());
                line.setLength(0);
            }

            if (!line.isEmpty()) {
                line.append(' ');
            }

            for (char character : word.toCharArray()) {
                if (!line.isEmpty() && textWidth(line.toString() + character, false) > width) {
                    lines.add(line.toString());
                    line.setLength(0);
                }

                line.append(character);
            }
        }

        if (!line.isEmpty()) {
            lines.add(line.toString());
        }

        return lines;
    }

    private static int textWidth(String text, boolean bold) {
        int width = 0;

        for (char character : text.toCharArray()) {
            int advance;

            if (character >= 32 && character <= 126) {
                advance = ASCII_ADVANCES.charAt(character - 32) - '0';
            } else if (character == '•') {
                advance = 3;
            } else if (character == '·') {
                advance = 2;
            } else {
                var glyph = MinecraftFont.Font.getChar(character);
                advance = (glyph == null ? 5 : glyph.getWidth()) + 1;
            }

            width += advance + (bold && character != ' ' ? 1 : 0);
        }

        return width;
    }

    private static String endpoint(String host, int port) {
        return (host.contains(":") ? "[" + host + "]" : host) + ":" + port;
    }

    private static String duration(long seconds, Appearance view) {
        long safe = Math.max(0, seconds);
        if (safe >= 3600) {
            return view.format("uptime-hours", Map.of("hours", safe / 3600, "minutes", safe % 3600 / 60));
        }
        if (safe >= 60) {
            return view.format("uptime-minutes", Map.of("minutes", safe / 60, "seconds", safe % 60));
        }
        return view.format("uptime-seconds", Map.of("seconds", safe));
    }

}

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

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.HelpCommand;
import co.aikar.commands.annotation.Subcommand;

import dev.despical.portbridge.PortBridgePlugin;

import org.bukkit.command.CommandSender;

import java.util.Map;

/**
 * Receives ACF command invocations for tunnel status and lifecycle management.
 * <p>
 * The portbridge, port and pb aliases share the same command handlers. Subcommands
 * declare their individual permissions and delegate to the plugin and tunnel owner,
 * so manual requests follow the same lifecycle as automatic startup.
 * <p>
 * Status, help and address requests render configured chat panels on the server
 * thread. Reload failures are shown to the sender while the previous settings
 * remain active.
 *
 * @author Despical
 * <p>
 * Created at 10.10.2026
 */
@CommandAlias("portbridge|port|pb")
public final class BridgeCommands extends BaseCommand {

    private final PortBridgePlugin plugin;

    public BridgeCommands(PortBridgePlugin plugin) {
        this.plugin = plugin;
    }

    @Default
    @Subcommand("status")
    @CommandPermission("portbridge.status")
    public void status(CommandSender sender) {
        plugin.chat().status(sender, plugin.snapshot(), plugin.settings());
    }

    @HelpCommand
    @Subcommand("help")
    @CommandPermission("portbridge.status")
    public void help(CommandSender sender) {
        plugin.chat().help(sender);
    }

    @Subcommand("address|ip")
    @CommandPermission("portbridge.address")
    public void address(CommandSender sender) {
        plugin.chat().address(sender, plugin.snapshot());
    }

    @Subcommand("start")
    @CommandPermission("portbridge.manage")
    public void start(CommandSender sender) {
        if (plugin.startTunnel()) {
            plugin.chat().noticeKey(sender, "command-started");
        } else {
            plugin.chat().noticeKey(sender, "command-already-running");
        }
    }

    @Subcommand("stop")
    @CommandPermission("portbridge.manage")
    public void stop(CommandSender sender) {
        plugin.chat().noticeKey(sender, plugin.stopTunnel() ? "command-stopped" : "command-already-stopped");
    }

    @Subcommand("restart")
    @CommandPermission("portbridge.manage")
    public void restart(CommandSender sender) {
        plugin.restartTunnel();
        plugin.chat().noticeKey(sender, "command-restarted");
    }

    @Subcommand("reload")
    @CommandPermission("portbridge.reload")
    public void reload(CommandSender sender) {
        try {
            plugin.reloadBridge();
            plugin.chat().noticeKey(sender, "command-reloaded");
        } catch (Exception error) {
            plugin.chat().errorKey(sender, "command-reload-failed", Map.of("reason", String.valueOf(error.getMessage())));
        }
    }
}

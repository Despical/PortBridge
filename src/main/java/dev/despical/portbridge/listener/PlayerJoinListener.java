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

package dev.despical.portbridge.listener;

import dev.despical.portbridge.PortBridgePlugin;
import dev.despical.portbridge.tunnel.TunnelState;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Shows the current connection address to authorized joining players.
 * <p>
 * The delayed server-thread callback rechecks the notification preference, player
 * presence, address permission and tunnel state before sending the chat panel.
 *
 * @author Despical
 * <p>
 * Created at 11.10.2026
 */
public final class PlayerJoinListener implements Listener {

    private final PortBridgePlugin plugin;

    public PlayerJoinListener(PortBridgePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.settings().notifyOnJoin() || !event.getPlayer().hasPermission("portbridge.address")) {
            return;
        }

        var player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!plugin.isEnabled() || !plugin.settings().notifyOnJoin() || !player.isOnline()
                || !player.hasPermission("portbridge.address")) {
                return;
            }

            var snapshot = plugin.snapshot();
            if (snapshot.state() == TunnelState.ONLINE) {
                plugin.chat().connected(player, snapshot);
            }
        }, 30L);
    }
}

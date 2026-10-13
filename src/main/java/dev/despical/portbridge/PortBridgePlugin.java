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

package dev.despical.portbridge;

import co.aikar.commands.PaperCommandManager;
import co.aikar.locales.MessageKey;

import dev.despical.portbridge.bore.BoreInstaller;
import dev.despical.portbridge.command.BridgeCommands;
import dev.despical.portbridge.command.ChatUI;
import dev.despical.portbridge.config.BridgeSettings;
import dev.despical.portbridge.config.Messages;
import dev.despical.portbridge.config.SettingsLoader;
import dev.despical.portbridge.listener.PlayerJoinListener;
import dev.despical.portbridge.tunnel.TunnelService;
import dev.despical.portbridge.tunnel.TunnelSnapshot;
import dev.despical.portbridge.tunnel.TunnelState;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bstats.bukkit.Metrics;

import java.util.Locale;
import java.util.Objects;

/**
 * Coordinates the lifecycle of PortBridge and its shared tunnel services.
 * <p>
 * Startup installs missing configuration, validates settings and messages, and
 * registers commands and joining-player notifications. Automatic tunnel startup
 * is scheduled on the server thread; downloads, processes and network probes are
 * owned by the asynchronous tunnel service. A startup failure disables the plugin.
 * <p>
 * Reload validates replacement settings and messages before applying them, so an
 * invalid file leaves the previous configuration active. Shutdown cancels pending
 * startup, unregisters commands and closes the managed Bore client. Service accessors
 * and Bukkit notifications are intended for enabled-plugin use on the server thread.
 *
 * @author Despical
 * <p>
 * Created at 10.10.2026
 */
public final class PortBridgePlugin extends JavaPlugin {

    private SettingsLoader settingsLoader;
    private BridgeSettings settings;
    private ChatUI chat;
    private Messages messages;
    private TunnelService tunnel;
    private PaperCommandManager commands;
    private BukkitTask startupTask;
    private Metrics metrics;

    @Override
    public void onEnable() {
        try {
            loadConfiguration();
            initializeTunnel();
            registerCommands();
            registerListeners();
            initializeMetrics();

            if (settings.startup().autoStart()) {
                scheduleStartup();
                getLogger().info("Automatic tunnel startup scheduled in " + settings.startup().delaySeconds() + " seconds.");
            } else {
                getLogger().info("Automatic startup is disabled. Use /port start when ready.");
            }
        } catch (Exception error) {
            getLogger().severe("Could not enable PortBridge: " + error.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void loadConfiguration() {
        saveDefaultConfig();

        settingsLoader = new SettingsLoader(this);
        settings = settingsLoader.load();
        messages = new Messages(this);
        chat = new ChatUI(messages);
    }

    private void initializeTunnel() {
        tunnel = new TunnelService(new BoreInstaller(getDataFolder().toPath(), getLogger()), settings,
            getLogger(), this::notifyState, messages::format);
    }

    private void registerCommands() {
        commands = new PaperCommandManager(this);
        commands.getLocales().setDefaultLocale(Locale.ENGLISH);
        configureCommandMessages();
        commands.registerCommand(new BridgeCommands(this));
    }

    private void configureCommandMessages() {
        messages.view().messages().forEach((key, value) -> {
            if (key.startsWith("acf.")) {
                commands.getLocales().addMessage(Locale.ENGLISH, MessageKey.of(key.substring(4)), value);
            }
        });
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
    }

    private void scheduleStartup() {
        startupTask = getServer().getScheduler().runTaskLater(this, () -> {
            startupTask = null;
            tunnel.start();
        }, Math.max(1L, settings.startup().delaySeconds() * 20L));
    }

    private void initializeMetrics() {
        try {
            metrics = new Metrics(this, 34669);
        } catch (Exception metricsError) {
            getLogger().warning("bStats could not start: " + metricsError.getMessage());
        }
    }

    public BridgeSettings settings() {
        return settings;
    }

    public ChatUI chat() {
        return chat;
    }

    public TunnelSnapshot snapshot() {
        return tunnel.snapshot();
    }

    public boolean startTunnel() {
        cancelStartup();
        return tunnel.start();
    }

    public boolean stopTunnel() {
        boolean scheduled = cancelStartup();
        return tunnel.stop() || scheduled;
    }

    public void restartTunnel() {
        cancelStartup();
        tunnel.restart();
    }

    public void reloadBridge() {
        BridgeSettings replacement = settingsLoader.load();
        messages.reload();

        configureCommandMessages();
        settings = replacement;
        tunnel.reconfigure(replacement);

        if (startupTask != null) {
            cancelStartup();

            if (replacement.startup().autoStart()) {
                scheduleStartup();
            }
        }
    }

    private void notifyState(TunnelSnapshot snapshot) {
        if (!isEnabled()) {
            return;
        }

        getServer().getScheduler().runTask(this, () -> {
            if (!isEnabled()) {
                return;
            }

            TunnelSnapshot current = snapshot();

            if (current.state() != snapshot.state() || !current.address().equals(snapshot.address())
                || !Objects.equals(current.connectedAt(), snapshot.connectedAt())
            ) {
                return;
            }

            if (snapshot.state() == TunnelState.ONLINE) {
                chat.connected(getServer().getConsoleSender(), snapshot);

                for (var player : getServer().getOnlinePlayers()) {
                    if (player.hasPermission("portbridge.address")) {
                        chat.connected(player, snapshot);
                    }
                }
            } else if (snapshot.state() == TunnelState.FAILED) {
                chat.error(getServer().getConsoleSender(), snapshot.lastError());
            }
        });
    }

    private boolean cancelStartup() {
        if (startupTask == null) {
            return false;
        }

        startupTask.cancel();
        startupTask = null;
        return true;
    }

    @Override
    public void onDisable() {
        cancelStartup();

        if (metrics != null) {
            metrics.shutdown();
            metrics = null;
        }

        if (commands != null) {
            commands.unregisterCommands();
        }

        if (tunnel != null) {
            tunnel.close();

            getLogger().info("Tunnel closed. PortBridge's Bore client has been stopped.");
        }
    }
}

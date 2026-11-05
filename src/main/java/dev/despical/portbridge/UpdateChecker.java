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

import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Asks SpigotMC for the latest published resource version without blocking the server thread.
 * <p>
 * The request runs asynchronously and failures are logged only at a fine level, so an
 * unreachable API never affects startup. A newer version is reported to the callback on
 * the server thread; equal, older or unparsable versions are ignored.
 *
 * @author Despical
 * <p>
 * Created at 01.11.2026
 */
final class UpdateChecker {

    private static final String API = "https://api.spigotmc.org/legacy/update.php?resource=";

    private static final Pattern NON_VERSION = Pattern.compile("[^0-9.]");

    private UpdateChecker() {
    }

    /**
     * Calls {@code onNewer} on the server thread when the published version is newer than the running one.
     *
     * @param plugin  the plugin whose version is compared
     * @param resource the SpigotMC resource identifier
     * @param onNewer receives the latest published version
     */
    static void check(JavaPlugin plugin, int resource, Consumer<String> onNewer) {
        String current = plugin.getPluginMeta().getVersion();
        HttpRequest request = HttpRequest.newBuilder(URI.create(API + resource))
            .timeout(Duration.ofSeconds(10))
            .header("User-Agent", "PortBridge/" + current)
            .build();

        HttpClient client = HttpClient.newHttpClient();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenAccept(response -> {
                String latest = response.body().trim();

                if (response.statusCode() == 200 && isNewer(latest, current) && plugin.isEnabled()) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> onNewer.accept(latest));
                }
            })
            .exceptionally(error -> {
                plugin.getLogger().log(Level.FINE, "Update check failed", error);
                return null;
            })
            .whenComplete((ignored, error) -> client.close());
    }

    /** Compares dotted numeric versions; anything unparsable counts as not newer. */
    static boolean isNewer(String candidate, String current) {
        String[] left = NON_VERSION.matcher(candidate).replaceAll("").split("\\.");
        String[] right = NON_VERSION.matcher(current).replaceAll("").split("\\.");

        for (int index = 0; index < Math.max(left.length, right.length); index++) {
            int a = index < left.length ? parse(left[index]) : 0;
            int b = index < right.length ? parse(right[index]) : 0;

            if (a != b) {
                return a > b;
            }
        }

        return false;
    }

    private static int parse(String part) {
        try {
            return part.isEmpty() ? 0 : Integer.parseInt(part);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }
}

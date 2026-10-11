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

package dev.despical.portbridge.tunnel;

import dev.despical.portbridge.config.BridgeSettings;

/**
 * Calculates bounded exponential reconnect delays and retry eligibility.
 * <p>
 * Delays grow from the configured initial value to the maximum without integer
 * overflow. A maximum attempt count of zero permits indefinite retries; disabling
 * reconnection rejects every attempt. The tunnel owner schedules accepted retries.
 *
 * @author Despical
 * <p>
 * Created at 10.10.2026
 */
public final class RetryPolicy {

    private RetryPolicy() {
    }

    public static int delaySeconds(BridgeSettings.Reconnect settings, int attempt) {
        long delay = settings.initialDelaySeconds();

        for (int index = 1; index < attempt && delay < settings.maxDelaySeconds(); index++) {
            delay = Math.min(delay * 2, settings.maxDelaySeconds());
        }

        return (int) Math.min(delay, settings.maxDelaySeconds());
    }

    public static boolean canRetry(BridgeSettings.Reconnect settings, int attempt) {
        return settings.enabled() && (settings.maxAttempts() == 0 || attempt <= settings.maxAttempts());
    }
}

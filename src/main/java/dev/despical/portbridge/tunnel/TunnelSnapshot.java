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

import java.time.Instant;

/**
 * Provides an immutable view of the current tunnel for commands and notifications.
 * <p>
 * The worker publishes snapshots after lifecycle and route-check updates. Readers
 * can retain a snapshot across threads without accessing the native process.
 * Connection and retry timestamps may be absent; negative latency means no public
 * ping measurement is available. An allocated address alone does not verify a route.
 *
 * @author Despical
 * <p>
 * Created at 10.10.2026
 */
public record TunnelSnapshot(TunnelState state, String address, String localAddress, Instant connectedAt,
    int reconnectAttempt, Instant nextRetryAt, long latencyMillis, String detail, String lastError) {

    public static TunnelSnapshot stopped() {
        return new TunnelSnapshot(TunnelState.STOPPED, "", "", null, 0, null, -1, "Tunnel is stopped.", "");
    }

    public boolean hasAddress() {
        return !address.isBlank();
    }
}

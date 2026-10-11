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

/**
 * Describes preparation, connection, route health and terminal tunnel states.
 * <p>
 * ONLINE means the public Minecraft probe succeeded when route checks are enabled.
 * With checks disabled it represents a relay connection without route verification.
 * DEGRADED retains a failed route while its failure threshold is evaluated;
 * RECONNECTING waits for an accepted retry and FAILED has no scheduled retry.
 *
 * @author Despical
 * <p>
 * Created at 10.10.2026
 */
public enum TunnelState {
    STOPPED, PREPARING, CONNECTING, ONLINE, DEGRADED, RECONNECTING, FAILED
}

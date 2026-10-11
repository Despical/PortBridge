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

import dev.despical.portbridge.config.BridgeSettings;
import dev.despical.portbridge.tunnel.RetryPolicy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies capped reconnect delays, unlimited retries and explicit retry limits.
 *
 * @author Despical
 * <p>
 * Created at 10.10.2026
 */
class RetryPolicyTest {

    @Test
    void repeatedFailuresBackOffWithoutOverflow() {
        var settings = new BridgeSettings.Reconnect(true, 5, 60, 0);
        assertEquals(5, RetryPolicy.delaySeconds(settings, 1));
        assertEquals(10, RetryPolicy.delaySeconds(settings, 2));
        assertEquals(60, RetryPolicy.delaySeconds(settings, 50));
        assertEquals(60, RetryPolicy.delaySeconds(settings, Integer.MAX_VALUE));
        assertTrue(RetryPolicy.canRetry(settings, Integer.MAX_VALUE));
    }

    @Test
    void finiteRetriesAndExplicitDisableAreHonored() {
        var settings = new BridgeSettings.Reconnect(true, 5, 60, 3);
        assertTrue(RetryPolicy.canRetry(settings, 3));
        assertFalse(RetryPolicy.canRetry(settings, 4));
        assertFalse(RetryPolicy.canRetry(new BridgeSettings.Reconnect(false, 5, 60, 0), 1));
    }
}

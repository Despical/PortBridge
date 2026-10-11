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

package dev.despical.portbridge.bore;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies Bore port parsing, invalid-port rejection and control-sequence cleanup.
 *
 * @author Despical
 * <p>
 * Created at 10.10.2026
 */
class BoreOutputTest {

    @Test
    void recognizesRealBoreConnectionMessages() {
        assertEquals(42817, BoreOutput.remotePort("2026-10-10T00:00:00Z INFO bore::client: listening at bore.pub:42817").orElseThrow());
        assertEquals(42817, BoreOutput.remotePort("\u001B[32m INFO\u001B[0m bore::client: connected to server \u001B[3mremote_port\u001B[0m=42817").orElseThrow());
        assertEquals(1234, BoreOutput.remotePort("listening at [::1]:1234").orElseThrow());
        assertEquals(1234, BoreOutput.remotePort("listening at ::1:1234").orElseThrow());
    }

    @Test
    void rejectsUnrelatedLogsAndInvalidPorts() {
        for (String line : new String[] {"could not connect to bore.pub:7835", "new connection remote_port=1234",
            "listening at bore.pub:0", "listening at bore.pub:65536", "listening at bore.pub:1234 error", "remote_port=2345"}
        ) {
            assertTrue(BoreOutput.remotePort(line).isEmpty(), line);
        }
    }

    @Test
    void stripsAnsiAndControlSequences() {
        assertEquals("INFO active", BoreOutput.clean("\u001B]0;title\u0007\u001B[32mINFO\u001B[0m active\r\n"));
    }
}

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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies dotted version comparison used by the update check.
 * <p>
 * Tests cover newer, equal, older, differently sized and unparsable versions.
 *
 * @author Despical
 * <p>
 * Created at 01.11.2026
 */
class UpdateCheckerTest {

    @Test
    void detectsNewerVersions() {
        assertTrue(UpdateChecker.isNewer("1.1.0", "1.0.0"));
        assertTrue(UpdateChecker.isNewer("1.0.1", "1.0"));
        assertTrue(UpdateChecker.isNewer("2.0", "1.9.9"));
        assertTrue(UpdateChecker.isNewer("1.10.0", "1.9.0"));
    }

    @Test
    void ignoresEqualOlderAndUnparsableVersions() {
        assertFalse(UpdateChecker.isNewer("1.0.0", "1.0.0"));
        assertFalse(UpdateChecker.isNewer("1.0", "1.0.0"));
        assertFalse(UpdateChecker.isNewer("1.0.0", "1.1.0"));
        assertFalse(UpdateChecker.isNewer("", "1.0.0"));
        assertFalse(UpdateChecker.isNewer("Resource not found", "1.0.0"));
    }
}

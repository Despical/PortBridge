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

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies inherited text decoration, pixel widths and bounded chat-line wrapping.
 *
 * @author Despical
 * <p>
 * Created at 10.10.2026
 */
class ChatUITest {

    @Test
    void includesBoldInheritedByChildrenInPixelWidth() {
        Component label = Component.text("A").decorate(TextDecoration.BOLD).append(Component.text("A"));
        assertEquals(14, ChatUI.pixelWidth(label, false));
    }

    @Test
    void respectsExplicitUnboldChildWithoutAddingBoldWidthToSpaces() {
        Component label = Component.text("A ").decorate(TextDecoration.BOLD)
            .append(Component.text("A").decoration(TextDecoration.BOLD, false));
        assertEquals(17, ChatUI.pixelWidth(label, false));
    }

    @Test
    void measuresMixedBoldButtonsAndCountsEveryChildOnce() {
        Component line = Component.empty().append(Component.text("A").decorate(TextDecoration.BOLD))
            .append(Component.text(" ")).append(Component.text("A"));
        assertEquals(17, ChatUI.pixelWidth(line, false));
        assertEquals(0, ChatUI.pixelWidth(Component.empty(), true));
    }

    @Test
    void wrapsWordsAndLongUnbrokenTokensWithinThePixelBudget() {
        String value = "Relay connection failed while preparing " + "A".repeat(100);
        var lines = ChatUI.wrap(value, 180);

        assertTrue(lines.size() > 1);
        assertTrue(lines.stream().allMatch(line -> ChatUI.pixelWidth(Component.text(line), false) <= 180));
        assertEquals(value.replace(" ", ""), String.join("", lines).replace(" ", ""));
    }
}

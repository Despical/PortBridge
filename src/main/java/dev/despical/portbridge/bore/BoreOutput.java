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

import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognizes public-port announcements from the pinned Bore client.
 * <p>
 * ANSI sequences and control characters are removed before checking supported
 * listening and connection messages. Unrelated output and ports outside the TCP
 * range produce an empty result. Parsing performs no network or process work.
 *
 * @author Despical
 * <p>
 * Created at 10.10.2026
 */
public final class BoreOutput {

    private static final Pattern ANSI = Pattern.compile("\\u001B(?:\\[[0-?]*[ -/]*[@-~]|\\][^\\u0007\\u001B]*(?:\\u0007|\\u001B\\\\))");
    private static final Pattern LISTENING = Pattern.compile("(?:^|\\s)listening at (?:\\[[a-zA-Z0-9:._%-]+]|[a-zA-Z0-9:._%-]+):(\\d{1,5})\\s*$");
    private static final Pattern CONNECTED = Pattern.compile("(?:^|\\s)connected to server\\s+remote_port=(\\d{1,5})\\s*$");

    private BoreOutput() {
    }

    public static String clean(String line) {
        return ANSI.matcher(line).replaceAll("").replaceAll("[\\p{Cntrl}&&[^\\t]]", "").strip();
    }

    public static OptionalInt remotePort(String line) {
        String cleaned = clean(line);
        Matcher matcher = LISTENING.matcher(cleaned);

        if (!matcher.find()) {
            matcher = CONNECTED.matcher(cleaned);

            if (!matcher.find()) {
                return OptionalInt.empty();
            }
        }

        int port = Integer.parseInt(matcher.group(1));
        return port > 0 && port <= 65535 ? OptionalInt.of(port) : OptionalInt.empty();
    }
}

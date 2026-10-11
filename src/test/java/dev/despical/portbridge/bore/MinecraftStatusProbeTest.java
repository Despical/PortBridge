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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies Minecraft status validation and ping exchanges against local sockets.
 * <p>
 * Fixtures cover handshake contents, matching pong tokens, malformed packets,
 * closed connections and bounded reads without contacting a public relay.
 *
 * @author Despical
 * <p>
 * Created at 10.10.2026
 */
class MinecraftStatusProbeTest {

    private static final String STATUS = "{\"version\":{\"name\":\"Paper\",\"protocol\":-1},\"players\":{\"max\":20,\"online\":0},\"description\":{\"text\":\"Hello\"}}";

    @Test
    void validatesStatusAndRejectsMalformedOrUnrelatedJson() throws IOException {
        MinecraftStatusProbe.validateStatus(STATUS);

        for (String json : new String[] {"{}", "[]", STATUS + "junk", STATUS.replace("\"Paper\"", "\"Bad\\x\""),
            STATUS.replace("\"online\":0", "\"online\":00"), STATUS.replace("\"protocol\":-1", "\"protocol\":true")}) {

            assertThrows(IOException.class, () -> MinecraftStatusProbe.validateStatus(json), json);
        }
    }

    @Test
    void performsHandshakeAndMatchesPingToken() throws Exception {
        try (ServerSocket listener = new ServerSocket(0); var executor = Executors.newSingleThreadExecutor()) {
            Future<?> server = executor.submit(() -> {

                try (Socket socket = listener.accept()) {
                    socket.setSoTimeout(3000);

                    DataInputStream input = new DataInputStream(socket.getInputStream());
                    DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                    DataInputStream handshake = packet(input);

                    assertEquals(0, varInt(handshake));
                    assertEquals(-1, varInt(handshake));

                    int hostLength = varInt(handshake);

                    assertEquals("127.0.0.1", new String(handshake.readNBytes(hostLength), StandardCharsets.UTF_8));
                    assertEquals(listener.getLocalPort(), handshake.readUnsignedShort());
                    assertEquals(1, varInt(handshake));
                    assertEquals(0, packet(input).readByte());

                    byte[] json = STATUS.getBytes(StandardCharsets.UTF_8);
                    ByteArrayOutputStream body = new ByteArrayOutputStream();

                    DataOutputStream status = new DataOutputStream(body);
                    writeVarInt(status, 0);
                    writeVarInt(status, json.length);
                    status.write(json);

                    writeVarInt(output, body.size());

                    output.write(body.toByteArray());
                    output.flush();

                    DataInputStream ping = packet(input);
                    assertEquals(1, ping.readByte());

                    long token = ping.readLong();
                    output.write(new byte[] {9, 1});
                    output.writeLong(token);
                    output.flush();
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }
            });

            assertTrue(MinecraftStatusProbe.ping("127.0.0.1", listener.getLocalPort(), 3000) >= 0);
            server.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void rejectsOversizedPacketAndClosedConnection() throws Exception {
        for (boolean oversized : new boolean[] {true, false}) {
            try (ServerSocket listener = new ServerSocket(0); var executor = Executors.newSingleThreadExecutor()) {
                Future<?> server = executor.submit(() -> {

                    try (Socket socket = listener.accept()) {
                        socket.setSoTimeout(2000);

                        DataInputStream input = new DataInputStream(socket.getInputStream());
                        packet(input);
                        packet(input);

                        if (oversized) {
                            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                            writeVarInt(output, 2 * 1024 * 1024);

                            output.flush();
                        }
                    } catch (IOException exception) {
                        throw new RuntimeException(exception);
                    }
                });

                assertThrows(IOException.class, () -> MinecraftStatusProbe.ping("127.0.0.1", listener.getLocalPort(), 2000));
                server.get(3, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void boundsStatusReadDuration() throws Exception {
        try (ServerSocket listener = new ServerSocket(0); var executor = Executors.newSingleThreadExecutor()) {
            Future<?> server = executor.submit(() -> {

                try (Socket socket = listener.accept()) {
                    socket.setSoTimeout(2000);

                    try {
                        while (socket.getInputStream().read() != -1) {
                            // Consume requests without answering, until the probe times out.
                        }
                    } catch (IOException ignored) {
                    }
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }
            });

            long start = System.nanoTime();

            assertThrows(IOException.class, () -> MinecraftStatusProbe.ping("127.0.0.1", listener.getLocalPort(), 150));
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 2000);

            server.get(3, TimeUnit.SECONDS);
        }
    }

    private static DataInputStream packet(DataInputStream input) throws IOException {
        return new DataInputStream(new ByteArrayInputStream(input.readNBytes(varInt(input))));
    }

    private static int varInt(DataInputStream input) throws IOException {
        int value = 0;

        for (int shift = 0; shift < 35; shift += 7) {
            int next = input.readUnsignedByte();
            value |= (next & 0x7f) << shift;

            if ((next & 0x80) == 0) {
                return value;
            }
        }

        throw new IOException("Bad VarInt");
    }

    private static void writeVarInt(DataOutputStream output, int value) throws IOException {
        while ((value & ~0x7f) != 0) {
            output.writeByte((value & 0x7f) | 0x80);
            value >>>= 7;
        }

        output.writeByte(value);
    }
}

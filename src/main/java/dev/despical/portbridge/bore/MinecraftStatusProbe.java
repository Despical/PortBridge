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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Checks a Minecraft Java listener using the status handshake and ping exchange.
 * <p>
 * The probe validates packet lengths, UTF-8 status JSON and the matching pong token
 * before reporting round-trip latency. Packet size limits and a shared socket-read
 * deadline bound malformed or slow replies; no player login is performed.
 * <p>
 * Calls perform blocking network work and belong outside the server thread.
 * Protocol, connection and timeout failures are reported as I/O exceptions.
 *
 * @author Despical
 * <p>
 * Created at 10.10.2026
 */
public final class MinecraftStatusProbe {

    private static final int PACKET_LIMIT = 1024 * 1024;

    private MinecraftStatusProbe() {
    }

    public static long ping(String host, int port, int timeoutMillis) throws IOException {
        if (host.isBlank() || host.length() > 255 || port < 1 || port > 65535 || timeoutMillis < 1) {
            throw new IOException("Invalid Minecraft status probe destination or timeout.");
        }

        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMillis);
            socket.setTcpNoDelay(true);

            InputStream input = new DeadlineInput(socket, deadline);
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());

            ByteArrayOutputStream handshake = new ByteArrayOutputStream();
            DataOutputStream payload = new DataOutputStream(handshake);
            writeVarInt(payload, 0);
            writeVarInt(payload, -1);

            byte[] hostname = host.getBytes(StandardCharsets.UTF_8);
            writeVarInt(payload, hostname.length);

            payload.write(hostname);
            payload.writeShort(port);

            writeVarInt(payload, 1);
            writePacket(output, handshake.toByteArray());
            writePacket(output, new byte[] {0});

            output.flush();

            DataInputStream status = readPacket(input);
            if (readVarInt(status) != 0) {
                throw new IOException("Unexpected Minecraft status packet.");
            }

            int length = readVarInt(status);
            if (length <= 0 || length > status.available()) {
                throw new IOException("Invalid Minecraft status JSON length.");
            }

            byte[] json = status.readNBytes(length);
            if (status.available() != 0) {
                throw new IOException("Trailing data in Minecraft status packet.");
            }

            validateStatus(decode(json));

            long token = System.nanoTime();
            ByteArrayOutputStream ping = new ByteArrayOutputStream(9);
            DataOutputStream pingPayload = new DataOutputStream(ping);
            pingPayload.writeByte(1);
            pingPayload.writeLong(token);

            writePacket(output, ping.toByteArray());
            output.flush();

            DataInputStream pong = readPacket(input);
            if (readVarInt(pong) != 1 || pong.readLong() != token || pong.available() != 0) {
                throw new IOException("Invalid Minecraft ping reply.");
            }

            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - token);
        }
    }

    private static String decode(byte[] json) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(json)).toString();
        } catch (CharacterCodingException exception) {
            throw new IOException("Minecraft status JSON is not UTF-8.", exception);
        }
    }

    static void validateStatus(String json) throws IOException {
        try {
            JsonElement parsed = JsonParser.parseString(json);

            if (!parsed.isJsonObject()) {
                throw new IOException("Endpoint did not return a Minecraft Java status response.");
            }

            JsonObject root = parsed.getAsJsonObject();
            JsonElement version = root.get("version");
            JsonElement players = root.get("players");
            JsonElement description = root.get("description");
            if (version == null || !version.isJsonObject() || players == null || !players.isJsonObject()
                || !hasPrimitive(version.getAsJsonObject(), "name", false)
                || !hasPrimitive(version.getAsJsonObject(), "protocol", true)
                || !hasPrimitive(players.getAsJsonObject(), "max", true)
                || !hasPrimitive(players.getAsJsonObject(), "online", true) || description == null
                || !(description.isJsonObject() || description.isJsonArray()
                || description.isJsonPrimitive() && description.getAsJsonPrimitive().isString())
            ) {
                throw new IOException("Endpoint did not return a Minecraft Java status response.");
            }
        } catch (JsonParseException exception) {
            throw new IOException("Malformed Minecraft status JSON.", exception);
        }
    }

    private static boolean hasPrimitive(JsonObject object, String key, boolean number) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive()
            && (number ? value.getAsJsonPrimitive().isNumber() : value.getAsJsonPrimitive().isString());
    }

    private static DataInputStream readPacket(InputStream input) throws IOException {
        int length = readVarInt(input);
        if (length <= 0 || length > PACKET_LIMIT) {
            throw new IOException("Minecraft status packet exceeds the allowed size.");
        }

        byte[] packet = input.readNBytes(length);
        if (packet.length != length) {
            throw new EOFException("Minecraft status connection closed early.");
        }

        return new DataInputStream(new ByteArrayInputStream(packet));
    }

    private static int readVarInt(InputStream input) throws IOException {
        int value = 0;

        for (int position = 0; position < 5; position++) {
            int next = input.read();

            if (next == -1) {
                throw new EOFException("Minecraft status connection closed early.");
            }

            if (position == 4 && (next & 0xf0) != 0) {
                throw new IOException("Invalid Minecraft VarInt.");
            }

            value |= (next & 0x7f) << (position * 7);
            if ((next & 0x80) == 0) {
                return value;
            }
        }

        throw new IOException("Invalid Minecraft VarInt.");
    }

    private static void writeVarInt(DataOutputStream output, int value) throws IOException {
        while ((value & ~0x7f) != 0) {
            output.writeByte((value & 0x7f) | 0x80);
            value >>>= 7;
        }

        output.writeByte(value);
    }

    private static void writePacket(DataOutputStream output, byte[] payload) throws IOException {
        writeVarInt(output, payload.length);
        output.write(payload);
    }

    /**
     * Applies the remaining probe deadline before each blocking socket read.
     * <p>
     * Sharing a deadline across reads prevents partial replies from renewing the timeout.
     *
     * @author Despical
     * <p>
     * Created at 10.10.2026
     */
    private static final class DeadlineInput extends InputStream {

        private final Socket socket;
        private final InputStream input;
        private final long deadline;

        private DeadlineInput(Socket socket, long deadline) throws IOException {
            this.socket = socket;
            this.input = socket.getInputStream();
            this.deadline = deadline;
        }

        private void timeout() throws IOException {
            long remaining = deadline - System.nanoTime();

            if (remaining <= 0) {
                throw new IOException("Minecraft status probe timed out.");
            }

            socket.setSoTimeout((int) Math.clamp(remaining / 1_000_000, 1, Integer.MAX_VALUE));
        }

        @Override
        public int read() throws IOException {
            timeout();
            return input.read();
        }

        @Override
        public int read(byte @NotNull [] buffer, int offset, int length) throws IOException {
            timeout();
            return input.read(buffer, offset, length);
        }
    }
}

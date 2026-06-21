package com.horizonkk.chuwudai.modbag;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

final class ModBagByteBuf {
    private static final int MAX_VARINT_BYTES = 5;
    private final ByteArrayOutputStream output;
    private final byte[] input;
    private int readerIndex;

    private ModBagByteBuf() {
        this.output = new ByteArrayOutputStream();
        this.input = null;
    }

    private ModBagByteBuf(byte[] input) {
        this.output = null;
        this.input = input == null ? new byte[0] : input;
    }

    static ModBagByteBuf writer() {
        return new ModBagByteBuf();
    }

    static ModBagByteBuf reader(byte[] input) {
        return new ModBagByteBuf(input);
    }

    byte[] toByteArray() {
        return output.toByteArray();
    }

    void writeBoolean(boolean value) {
        output.write(value ? 1 : 0);
    }

    boolean readBoolean() {
        return readUnsignedByte() != 0;
    }

    void writeVarInt(int value) {
        while ((value & -128) != 0) {
            output.write(value & 127 | 128);
            value >>>= 7;
        }
        output.write(value);
    }

    int readVarInt() {
        int value = 0;
        int position = 0;
        int currentByte;
        do {
            currentByte = readUnsignedByte();
            value |= (currentByte & 127) << position;
            position += 7;
            if (position >= MAX_VARINT_BYTES * 7) {
                throw new IllegalArgumentException("VarInt is too large");
            }
        } while ((currentByte & 128) == 128);
        return value;
    }

    void writeString(String value) {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        writeVarInt(bytes.length);
        output.write(bytes, 0, bytes.length);
    }

    String readString(int maxLength) {
        int length = readVarInt();
        if (length < 0 || length > maxLength * 4 || readerIndex + length > input.length) {
            throw new IllegalArgumentException("Invalid string length: " + length);
        }
        String value = new String(input, readerIndex, length, StandardCharsets.UTF_8);
        readerIndex += length;
        if (value.length() > maxLength) {
            throw new IllegalArgumentException("String is too long");
        }
        return value;
    }

    private int readUnsignedByte() {
        if (input == null || readerIndex >= input.length) {
            throw new IllegalArgumentException("Unexpected end of packet");
        }
        return input[readerIndex++] & 255;
    }
}

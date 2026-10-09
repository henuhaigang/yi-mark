package com.yimark.watermark;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.zip.CRC32;

/**
 * Invisible watermark payload framing: magic, length, payload, CRC32.
 * The framed bytes are Reed-Solomon encoded before being spread over image blocks.
 */
public final class Frame {

    public static final int MAGIC = 0x53445733; // "SDW3"
    public static final int HEADER_BYTES = 4 + 4;
    public static final int TRAILER_BYTES = 4;

    private Frame() {}

    public static byte[] wrap(byte[] payload) {
        CRC32 crc = new CRC32();
        crc.update(payload);
        return ByteBuffer.allocate(HEADER_BYTES + payload.length + TRAILER_BYTES)
                .putInt(MAGIC)
                .putInt(payload.length)
                .put(payload)
                .putInt((int) crc.getValue())
                .array();
    }

    /** Returns the payload, or throws if the frame is damaged or truncated. */
    public static byte[] unwrap(byte[] framed) throws ReedSolomon.UncorrectableException {
        if (framed.length < HEADER_BYTES + TRAILER_BYTES) {
            throw new ReedSolomon.UncorrectableException("frame too short");
        }
        ByteBuffer buffer = ByteBuffer.wrap(framed);
        int magic = buffer.getInt();
        if (magic != MAGIC) {
            throw new ReedSolomon.UncorrectableException("bad magic: " + Integer.toHexString(magic));
        }
        int length = buffer.getInt();
        if (length < 0 || length > framed.length - HEADER_BYTES - TRAILER_BYTES) {
            throw new ReedSolomon.UncorrectableException("bad length: " + length);
        }
        byte[] payload = new byte[length];
        buffer.get(payload);
        int expected = buffer.getInt();
        CRC32 crc = new CRC32();
        crc.update(payload);
        if ((int) crc.getValue() != expected) {
            throw new ReedSolomon.UncorrectableException("CRC32 mismatch");
        }
        return payload;
    }

    public static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] p : parts) {
            total += p.length;
        }
        byte[] out = new byte[total];
        int offset = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, offset, p.length);
            offset += p.length;
        }
        return out;
    }

    public static byte[] slice(byte[] data, int from, int to) {
        return Arrays.copyOfRange(data, from, Math.min(to, data.length));
    }
}

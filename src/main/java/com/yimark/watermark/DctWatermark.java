package com.yimark.watermark;

import com.yimark.image.ImageOps;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Robust invisible watermark.
 *
 * <p>Payload layout: a fixed 16 byte sync preamble, then the caller payload,
 * then the frame header (magic, length, CRC32). The whole frame is Reed-Solomon
 * encoded (interleaved) and spread over the image: every 8x8 block carries one
 * bit, chosen by a hash of the block's original coordinates, so the mapping
 * survives cropping and resizing without knowing the original geometry.
 *
 * <p>Extraction recovers the bit for every block, then searches for the block
 * offset that best explains the sync preamble (translation sync), and - if the
 * agreement is still poor - resamples the image over a set of scales, rotations
 * and sub-block shifts. Each logical bit is decided by majority vote over all
 * blocks that hash to it, which is what gives the redundancy.
 */
public final class DctWatermark {

    public static final double STRENGTH = 18.0;
    public static final int SYNC_BYTES = 16;
    public static final int SYNC_BITS = SYNC_BYTES * 8;
    public static final int MAX_CODEWORDS = 1;
    public static final int SEARCH_RADIUS_BLOCKS = 32;
    public static final double SYNC_THRESHOLD = 0.9;

    private static final byte[] SYNC = syncPattern();
    private static final double[][] BASIS_PQ = DctTransform.basis(
            DctTransform.COEFFICIENT_P, DctTransform.COEFFICIENT_Q);
    private static final double[][] BASIS_QP = DctTransform.basis(
            DctTransform.COEFFICIENT_Q, DctTransform.COEFFICIENT_P);
    private static final int[] SCALE_CANDIDATES = {
            5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 22, 25, 28, 33, 40, 50};
    private static final int[] ANGLE_CANDIDATES_TENTHS = {
            -40, -30, -20, -15, -12, -10, -7, -5, 5, 7, 10, 12, 15, 20, 30, 40};

    private DctWatermark() {}

    private static byte[] syncPattern() {
        CRC32 crc = new CRC32();
        crc.update("SecureDoc-watermark-sync".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] value = new byte[SYNC_BYTES];
        long v = crc.getValue();
        for (int i = 0; i < SYNC_BYTES; i++) {
            v = v * 0x100000001B3L ^ (i + 1);
            value[i] = (byte) (v >>> 56);
        }
        return value;
    }

    // ---------------------------------------------------------------- embed

    public static BufferedImage embed(BufferedImage source, byte[] payload) {
        int width = source.getWidth();
        int height = source.getHeight();
        int blocks = (width / 8) * (height / 8);
        byte[] body = new byte[SYNC_BYTES + 4 + payload.length];
        System.arraycopy(SYNC, 0, body, 0, SYNC_BYTES);
        body[SYNC_BYTES] = (byte) (payload.length >>> 24);
        body[SYNC_BYTES + 1] = (byte) (payload.length >>> 16);
        body[SYNC_BYTES + 2] = (byte) (payload.length >>> 8);
        body[SYNC_BYTES + 3] = (byte) payload.length;
        System.arraycopy(payload, 0, body, SYNC_BYTES + 4, payload.length);
        byte[] stream = ReedSolomon.encodeInterleaved(body, ReedSolomon.ECC_BYTES);
        boolean[] bits = toBits(stream);
        if (blocks < bits.length) {
            throw new IllegalArgumentException(
                    "image too small for this payload: " + blocks + " blocks < " + bits.length + " bits");
        }
        BufferedImage out = ImageOps.copy(source);
        for (int blockY = 0; blockY < height / 8; blockY++) {
            for (int blockX = 0; blockX < width / 8; blockX++) {
                int index = bitIndex(blockX, blockY, bits.length);
                embedBit(out, blockX * 8, blockY * 8, bits[index]);
            }
        }
        return out;
    }

    private static void embedBit(BufferedImage image, int x, int y, boolean one) {
        double[][] samples = DctTransform.luminanceSamples(image, x, y);
        double pq = DctTransform.coefficient(samples, DctTransform.COEFFICIENT_P, DctTransform.COEFFICIENT_Q);
        double qp = DctTransform.coefficient(samples, DctTransform.COEFFICIENT_Q, DctTransform.COEFFICIENT_P);
        if (one) {
            if (pq < qp + STRENGTH) {
                apply(image, x, y, qp + STRENGTH - pq, BASIS_PQ);
            }
        } else {
            if (qp < pq + STRENGTH) {
                apply(image, x, y, pq + STRENGTH - qp, BASIS_QP);
            }
        }
    }

    private static void apply(BufferedImage image, int x, int y, double delta, double[][] basis) {
        for (int j = 0; j < 8; j++) {
            for (int i = 0; i < 8; i++) {
                int rgb = image.getRGB(x + i, y + j);
                int r = rgb >> 16 & 0xFF;
                int g = rgb >> 8 & 0xFF;
                int b = rgb & 0xFF;
                int shift = (int) Math.round(delta * basis[j][i]);
                image.setRGB(x + i, y + j, 0xFF000000
                        | (clamp(r + shift) << 16)
                        | (clamp(g + shift) << 8)
                        | clamp(b + shift));
            }
        }
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    // ------------------------------------------------------------- extract

    public static Extraction extract(BufferedImage image) {
        Attempt best = search(image, "identity");
        Extraction identity = decodeIfPromising(best);
        if (identity != null) {
            return identity;
        }
        Attempt winner = best;

        for (int shiftY = 0; shiftY < 8; shiftY++) {
            for (int shiftX = 0; shiftX < 8; shiftX++) {
                if (shiftX == 0 && shiftY == 0) {
                    continue;
                }
                BufferedImage shifted = ImageOps.shift(image, shiftX, shiftY);
                Attempt attempt = search(shifted, "shift +" + shiftX + "," + shiftY);
                winner = better(winner, attempt);
                Extraction decoded = decodeIfPromising(attempt);
                if (decoded != null) {
                    return decoded;
                }
            }
        }

        for (int tenths : SCALE_CANDIDATES) {
            double factor = tenths / 10.0;
            BufferedImage scaled = ImageOps.scale(image, factor);
            Attempt attempt = search(scaled, "scale x" + factor);
            winner = better(winner, attempt);
            Extraction decoded = decodeIfPromising(attempt);
            if (decoded != null) {
                return decoded;
            }
        }

        for (int tenths : ANGLE_CANDIDATES_TENTHS) {
            double degrees = tenths / 10.0;
            BufferedImage rotated = ImageOps.rotate(image, degrees);
            Attempt attempt = search(rotated, "rotate " + degrees + " deg");
            winner = better(winner, attempt);
            Extraction decoded = decodeIfPromising(attempt);
            if (decoded != null) {
                return decoded;
            }
        }

        if (winner == null) {
            return Extraction.failure(0, 0, "identity", "image too small to hold a watermark");
        }
        return winner.failure();
    }

    /**
     * Attempts to decode a candidate that clears the sync threshold. A high
     * sync score alone is not enough: on a resampled image several alignments
     * clear the threshold, and only the correct one survives error correction,
     * so the search must keep going until a decode actually succeeds.
     */
    private static Extraction decodeIfPromising(Attempt attempt) {
        if (attempt == null || attempt.score < SYNC_THRESHOLD) {
            return null;
        }
        Extraction result = attempt.decode();
        return result.recovered ? result : null;
    }

    /** Runs the translation search on one rendering of the image. */
    private static Attempt search(BufferedImage image, String transform) {
        int width = image.getWidth();
        int height = image.getHeight();
        int blockWidth = width / 8;
        int blockHeight = height / 8;
        if (blockWidth <= 0 || blockHeight <= 0) {
            return null;
        }
        boolean[][] sign = new boolean[blockHeight][blockWidth];
        for (int blockY = 0; blockY < blockHeight; blockY++) {
            for (int blockX = 0; blockX < blockWidth; blockX++) {
                double[][] samples = DctTransform.luminanceSamples(image, blockX * 8, blockY * 8);
                sign[blockY][blockX] =
                        DctTransform.coefficient(samples, DctTransform.COEFFICIENT_P, DctTransform.COEFFICIENT_Q)
                                > DctTransform.coefficient(samples, DctTransform.COEFFICIENT_Q, DctTransform.COEFFICIENT_P);
            }
        }

        double bestScore = -1;
        int bestCodewords = 1;
        int bestOffsetX = 0;
        int bestOffsetY = 0;
        for (int codewords = 1; codewords <= MAX_CODEWORDS; codewords++) {
            int bits = codewords * ReedSolomon.CODEWORD_BYTES * 8;
            List<int[]> syncBlocks = syncBlockCoordinates(bits, blockWidth + SEARCH_RADIUS_BLOCKS,
                    blockHeight + SEARCH_RADIUS_BLOCKS);
            for (int offsetY = -SEARCH_RADIUS_BLOCKS; offsetY <= SEARCH_RADIUS_BLOCKS; offsetY++) {
                for (int offsetX = -SEARCH_RADIUS_BLOCKS; offsetX <= SEARCH_RADIUS_BLOCKS; offsetX++) {
                    double score = syncScore(sign, blockWidth, blockHeight,
                            offsetX, offsetY, bits, syncBlocks);
                    if (score > bestScore) {
                        bestScore = score;
                        bestCodewords = codewords;
                        bestOffsetX = offsetX;
                        bestOffsetY = offsetY;
                    }
                }
            }
        }
        return new Attempt(image, sign, blockWidth, blockHeight,
                bestScore, bestCodewords, bestOffsetX, bestOffsetY, transform);
    }

    /** Original block coordinates whose hashed bit position falls inside the sync preamble. */
    private static List<int[]> syncBlockCoordinates(int bits, int width, int height) {
        List<int[]> blocks = new ArrayList<>();
        for (int blockY = 0; blockY < height; blockY++) {
            for (int blockX = 0; blockX < width; blockX++) {
                if (bitIndex(blockX, blockY, bits) < SYNC_BITS) {
                    blocks.add(new int[]{blockX, blockY});
                }
            }
        }
        return blocks;
    }

    private static double syncScore(boolean[][] sign, int blockWidth, int blockHeight,
                                     int offsetX, int offsetY, int bits, List<int[]> syncBlocks) {
        int[] votes = new int[SYNC_BITS];
        for (int[] block : syncBlocks) {
            int observedX = block[0] - offsetX;
            int observedY = block[1] - offsetY;
            if (observedX < 0 || observedY < 0 || observedX >= blockWidth || observedY >= blockHeight) {
                continue;
            }
            int index = bitIndex(block[0], block[1], bits);
            votes[index] += sign[observedY][observedX] ? 1 : -1;
        }
        int match = 0;
        for (int i = 0; i < SYNC_BITS; i++) {
            boolean recovered = votes[i] > 0;
            boolean expected = ((SYNC[i / 8] >> (7 - i % 8)) & 1) != 0;
            if (recovered == expected) {
                match++;
            }
        }
        return (double) match / SYNC_BITS;
    }

    private static Attempt better(Attempt a, Attempt b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return b.score > a.score ? b : a;
    }

    // ------------------------------------------------------------ decoding

    private static final class Attempt {
        private final BufferedImage image;
        private final boolean[][] sign;
        private final int blockWidth;
        private final int blockHeight;
        private final double score;
        private final int codewords;
        private final int offsetX;
        private final int offsetY;
        private final String transform;

        Attempt(BufferedImage image, boolean[][] sign, int blockWidth, int blockHeight,
                double score, int codewords, int offsetX, int offsetY, String transform) {
            this.image = image;
            this.sign = sign;
            this.blockWidth = blockWidth;
            this.blockHeight = blockHeight;
            this.score = score;
            this.codewords = codewords;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.transform = transform;
        }

        int bits() {
            return codewords * ReedSolomon.CODEWORD_BYTES * 8;
        }

        Extraction decode() {
            int bits = bits();
            int[] votes = new int[bits];
            int blocksUsed = 0;
            for (int blockY = 0; blockY < blockHeight; blockY++) {
                for (int blockX = 0; blockX < blockWidth; blockX++) {
                    int originalX = blockX + offsetX;
                    int originalY = blockY + offsetY;
                    if (originalX < 0 || originalY < 0) {
                        continue;
                    }
                    votes[bitIndex(originalX, originalY, bits)] += sign[blockY][blockX] ? 1 : -1;
                    blocksUsed++;
                }
            }
            boolean[] recovered = new boolean[bits];
            for (int i = 0; i < bits; i++) {
                recovered[i] = votes[i] > 0;
            }
            try {
                byte[] body = ReedSolomon.decodeInterleaved(toBytes(recovered), ReedSolomon.ECC_BYTES);
                if (body.length < SYNC_BYTES + 4 || !Arrays.equals(Arrays.copyOf(body, SYNC_BYTES), SYNC)) {
                    return Extraction.failure(score, codewords, transform,
                            "sync preamble mismatch after error correction");
                }
                int length = ((body[SYNC_BYTES] & 0xFF) << 24)
                        | ((body[SYNC_BYTES + 1] & 0xFF) << 16)
                        | ((body[SYNC_BYTES + 2] & 0xFF) << 8)
                        | (body[SYNC_BYTES + 3] & 0xFF);
                if (length < 0 || length > body.length - SYNC_BYTES - 4) {
                    return Extraction.failure(score, codewords, transform,
                            "declared payload length out of range: " + length);
                }
                byte[] payload = Arrays.copyOfRange(body, SYNC_BYTES + 4, SYNC_BYTES + 4 + length);
                return Extraction.success(payload, score, codewords,
                        blocksUsed / (double) bits, transform);
            } catch (ReedSolomon.UncorrectableException e) {
                return Extraction.failure(score, codewords, transform,
                        "error correction failed: " + e.getMessage());
            }
        }

        Extraction failure() {
            return Extraction.failure(score, codewords, transform,
                    "no transform recovered the sync preamble");
        }
    }

    /** Maps a block of the original image to the logical bit it carries. */
    public static int bitIndex(int blockX, int blockY, int modulus) {
        int h = blockX * 0x9E3779B1 + blockY * 0x85EBCA77;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 13;
        h *= 0x297A2D39;
        h ^= h >>> 16;
        return (h & 0x7FFFFFFF) % modulus;
    }

    static boolean[] toBits(byte[] data) {
        boolean[] bits = new boolean[data.length * 8];
        for (int i = 0; i < data.length; i++) {
            for (int bit = 0; bit < 8; bit++) {
                bits[i * 8 + bit] = ((data[i] >> (7 - bit)) & 1) != 0;
            }
        }
        return bits;
    }

    static byte[] toBytes(boolean[] bits) {
        byte[] out = new byte[(bits.length + 7) / 8];
        for (int i = 0; i < bits.length; i++) {
            if (bits[i]) {
                out[i / 8] |= 1 << (7 - i % 8);
            }
        }
        return out;
    }
}

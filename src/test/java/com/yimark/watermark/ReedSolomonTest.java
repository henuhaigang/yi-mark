package com.yimark.watermark;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReedSolomonTest {

    private static final int NSYM = ReedSolomon.ECC_BYTES;

    @Test
    @DisplayName("clean codeword decodes to the original data")
    void cleanRoundTrip() throws Exception {
        byte[] data = randomBytes(ReedSolomon.DATA_BYTES, 7);
        byte[] codeword = ReedSolomon.encode(data, NSYM);
        assertEquals(ReedSolomon.CODEWORD_BYTES, codeword.length);
        byte[] decoded = ReedSolomon.decode(codeword, NSYM);
        assertArrayEquals(data, java.util.Arrays.copyOf(decoded, data.length));
    }

    @Test
    @DisplayName("all payload lengths up to the codeword capacity round-trip")
    void allLengths() throws Exception {
        for (int len = 1; len <= ReedSolomon.DATA_BYTES; len++) {
            byte[] data = randomBytes(len, len);
            byte[] codeword = ReedSolomon.encode(data, NSYM);
            byte[] decoded = ReedSolomon.decode(codeword, NSYM);
            assertArrayEquals(data, java.util.Arrays.copyOf(decoded, len), "length " + len);
        }
    }

    @Test
    @DisplayName("corrects every error count up to nsym/2 at every position")
    void correctsUpToHalfNsym() throws Exception {
        Random random = new Random(42);
        for (int errors = 1; errors <= NSYM / 2; errors++) {
            for (int trial = 0; trial < 20; trial++) {
                byte[] data = randomBytes(ReedSolomon.DATA_BYTES, trial);
                byte[] codeword = ReedSolomon.encode(data, NSYM);
                int[] positions = distinctPositions(random, codeword.length, errors);
                for (int p : positions) {
                    codeword[p] ^= (byte) (1 + random.nextInt(255));
                }
                byte[] decoded = ReedSolomon.decode(codeword, NSYM);
                assertArrayEquals(data, java.util.Arrays.copyOf(decoded, data.length),
                        "errors=" + errors + " at " + java.util.Arrays.toString(positions));
            }
        }
    }

    @Test
    @DisplayName("corrects a burst of up to 16 consecutive bytes")
    void correctsBurst() throws Exception {
        Random random = new Random(9);
        byte[] data = randomBytes(ReedSolomon.DATA_BYTES, 3);
        byte[] codeword = ReedSolomon.encode(data, NSYM);
        int start = 20;
        for (int i = 0; i < 16; i++) {
            codeword[start + i] ^= (byte) (0x5A + i);
        }
        byte[] decoded = ReedSolomon.decode(codeword, NSYM);
        assertArrayEquals(data, java.util.Arrays.copyOf(decoded, data.length));
    }

    @Test
    @DisplayName("rejects damage beyond the correction capability instead of returning garbage")
    void rejectsUncorrectable() {
        Random random = new Random(11);
        int detected = 0;
        for (int trial = 0; trial < 40; trial++) {
            byte[] data = randomBytes(ReedSolomon.DATA_BYTES, trial);
            byte[] codeword = ReedSolomon.encode(data, NSYM);
            int[] positions = distinctPositions(random, codeword.length, 40);
            for (int p : positions) {
                codeword[p] ^= (byte) (1 + random.nextInt(255));
            }
            try {
                byte[] decoded = ReedSolomon.decode(codeword, NSYM);
                assertTrue(false, "decoder silently accepted heavy damage");
            } catch (ReedSolomon.UncorrectableException expected) {
                detected++;
            } catch (ArrayIndexOutOfBoundsException bad) {
                throw new AssertionError("decoder crashed instead of reporting failure", bad);
            }
        }
        assertEquals(40, detected);
    }

    @Test
    @DisplayName("wrap/unwrap preserves the payload and detects corruption")
    void frameRoundTrip() throws Exception {
        byte[] payload = "SD3|payload|with|fields".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] framed = Frame.wrap(payload);
        assertArrayEquals(payload, Frame.unwrap(framed));

        framed[framed.length - 1] ^= 0x01;
        assertThrows(ReedSolomon.UncorrectableException.class, () -> Frame.unwrap(framed));
    }

    private static byte[] randomBytes(int length, long seed) {
        Random random = new Random(seed);
        byte[] data = new byte[length];
        random.nextBytes(data);
        return data;
    }

    private static int[] distinctPositions(Random random, int bound, int count) {
        int[] positions = new int[count];
        for (int i = 0; i < count; i++) {
            int p;
            boolean clash;
            do {
                p = random.nextInt(bound);
                clash = false;
                for (int j = 0; j < i; j++) {
                    if (positions[j] == p) {
                        clash = true;
                        break;
                    }
                }
            } while (clash);
            positions[i] = p;
        }
        return positions;
    }
}

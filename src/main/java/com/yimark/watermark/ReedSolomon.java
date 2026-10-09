package com.yimark.watermark;

import java.io.ByteArrayOutputStream;

/**
 * Systematic Reed-Solomon over GF(2^8), primitive polynomial 0x11D, generator 2,
 * first consecutive root alpha^0. Corrects up to nsym/2 byte errors per codeword.
 */
public final class ReedSolomon {

    public static final int DATA_BYTES = 223;
    public static final int ECC_BYTES = 32;
    public static final int CODEWORD_BYTES = DATA_BYTES + ECC_BYTES;

    private static final int PRIMITIVE = 0x11D;
    private static final int[] EXP = new int[512];
    private static final int[] LOG = new int[256];

    static {
        int x = 1;
        for (int i = 0; i < 255; i++) {
            EXP[i] = x;
            LOG[x] = i;
            x <<= 1;
            if ((x & 0x100) != 0) {
                x ^= PRIMITIVE;
            }
        }
        for (int i = 255; i < EXP.length; i++) {
            EXP[i] = EXP[i - 255];
        }
    }

    /** Raised when the damage exceeds the correction capability of the codeword. */
    public static final class UncorrectableException extends Exception {
        public UncorrectableException(String message) {
            super(message);
        }
    }

    private ReedSolomon() {}

    /**
     * Splits the data into chunks of at most DATA_BYTES, encodes each with
     * its own parity, and interleaves the codewords byte-wise so a burst of
     * damage is spread across all of them instead of destroying one.
     */
    public static byte[] encodeInterleaved(byte[] data, int nsym) {
        if (data.length > DATA_BYTES * 1000) {
            throw new IllegalArgumentException("payload too large");
        }
        int codewords = (data.length + DATA_BYTES - 1) / DATA_BYTES;
        byte[][] encoded = new byte[codewords][];
        for (int i = 0; i < codewords; i++) {
            byte[] chunk = new byte[DATA_BYTES];
            int offset = i * DATA_BYTES;
            int length = Math.min(DATA_BYTES, data.length - offset);
            System.arraycopy(data, offset, chunk, 0, length);
            encoded[i] = encode(chunk, nsym);
        }
        byte[] out = new byte[codewords * CODEWORD_BYTES];
        for (int position = 0; position < CODEWORD_BYTES; position++) {
            for (int word = 0; word < codewords; word++) {
                out[position * codewords + word] = encoded[word][position];
            }
        }
        return out;
    }

    /** Inverse of {@link #encodeInterleaved}. */
    public static byte[] decodeInterleaved(byte[] interleaved, int nsym)
            throws UncorrectableException {
        if (interleaved.length % CODEWORD_BYTES != 0) {
            throw new UncorrectableException("interleaved length is not a multiple of the codeword size");
        }
        int codewords = interleaved.length / CODEWORD_BYTES;
        byte[][] corrected = new byte[codewords][];
        for (int word = 0; word < codewords; word++) {
            byte[] codeword = new byte[CODEWORD_BYTES];
            for (int position = 0; position < CODEWORD_BYTES; position++) {
                codeword[position] = interleaved[position * codewords + word];
            }
            corrected[word] = decode(codeword, nsym);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int word = 0; word < codewords; word++) {
            out.write(corrected[word], 0, DATA_BYTES);
        }
        return out.toByteArray();
    }

    private static int mul(int a, int b) {
        if (a == 0 || b == 0) {
            return 0;
        }
        return EXP[LOG[a] + LOG[b]];
    }

    private static int div(int a, int b) {
        if (b == 0) {
            throw new ArithmeticException("GF division by zero");
        }
        if (a == 0) {
            return 0;
        }
        return EXP[(LOG[a] - LOG[b] + 255) % 255];
    }

    private static int inverse(int a) {
        return EXP[255 - LOG[a]];
    }

    /** Polynomials are stored highest degree first, matching the classic RS formulation. */
    private static int[] polyMul(int[] a, int[] b) {
        int[] r = new int[a.length + b.length - 1];
        for (int i = 0; i < a.length; i++) {
            if (a[i] == 0) {
                continue;
            }
            for (int j = 0; j < b.length; j++) {
                r[i + j] ^= mul(a[i], b[j]);
            }
        }
        return r;
    }

    private static int[] polyAdd(int[] a, int[] b) {
        int[] r = new int[Math.max(a.length, b.length)];
        for (int i = 0; i < a.length; i++) {
            r[i + r.length - a.length] = a[i];
        }
        for (int i = 0; i < b.length; i++) {
            r[i + r.length - b.length] ^= b[i];
        }
        return r;
    }

    private static int[] polyScale(int[] p, int x) {
        int[] r = new int[p.length];
        for (int i = 0; i < p.length; i++) {
            r[i] = mul(p[i], x);
        }
        return r;
    }

    private static int polyEval(int[] p, int x) {
        int y = 0;
        for (int coefficient : p) {
            y = mul(y, x) ^ coefficient;
        }
        return y;
    }

    private static int[] generator(int nsym) {
        int[] g = {1};
        for (int i = 0; i < nsym; i++) {
            g = polyMul(g, new int[]{1, EXP[i]});
        }
        return g;
    }

    public static byte[] encode(byte[] data, int nsym) {
        if (data.length + nsym > 255) {
            throw new IllegalArgumentException("codeword too long");
        }
        int[] gen = generator(nsym);
        int[] out = new int[data.length + nsym];
        for (int i = 0; i < data.length; i++) {
            out[i] = data[i] & 0xFF;
        }
        for (int i = 0; i < data.length; i++) {
            int coef = out[i];
            if (coef == 0) {
                continue;
            }
            for (int j = 1; j < gen.length; j++) {
                out[i + j] ^= mul(gen[j], coef);
            }
        }
        for (int i = 0; i < data.length; i++) {
            out[i] = data[i] & 0xFF;
        }
        return toBytes(out);
    }

    public static byte[] decode(byte[] message, int nsym) throws UncorrectableException {
        int[] m = toInts(message);
        int[] synd = syndromes(m, nsym);
        if (isZero(synd)) {
            return message;
        }
        int[] locator = findErrorLocator(synd, nsym);
        int[] positions = findErrorPositions(locator, message.length);
        if (positions == null) {
            throw new UncorrectableException("no valid error positions found");
        }
        m = correctErrata(m, synd, positions);
        if (!isZero(syndromes(m, nsym))) {
            throw new UncorrectableException("correction did not clear syndromes");
        }
        return toBytes(m);
    }

    /** S_j = C(alpha^j) for j = 0..nsym-1. */
    private static int[] syndromes(int[] m, int nsym) {
        int[] synd = new int[nsym];
        for (int i = 0; i < nsym; i++) {
            synd[i] = polyEval(m, EXP[i]);
        }
        return synd;
    }

    private static boolean isZero(int[] a) {
        for (int v : a) {
            if (v != 0) {
                return false;
            }
        }
        return true;
    }

    /** Berlekamp-Massey. Returns Lambda(x) = prod (1 - X_i x), highest degree coefficient first. */
    private static int[] findErrorLocator(int[] synd, int nsym) throws UncorrectableException {
        int[] locator = {1};
        int[] previous = {1};
        for (int i = 0; i < nsym; i++) {
            int delta = synd[i];
            for (int j = 1; j < locator.length; j++) {
                delta ^= mul(locator[locator.length - 1 - j], synd[i - j]);
            }
            int[] grown = new int[previous.length + 1];
            System.arraycopy(previous, 0, grown, 0, previous.length);
            previous = grown;
            if (delta != 0) {
                if (grown.length > locator.length) {
                    int[] scaled = polyScale(grown, delta);
                    previous = polyScale(locator, inverse(delta));
                    locator = scaled;
                }
                locator = polyAdd(locator, polyScale(previous, delta));
            }
        }
        while (locator.length > 0 && locator[0] == 0) {
            int[] trimmed = new int[locator.length - 1];
            System.arraycopy(locator, 1, trimmed, 0, trimmed.length);
            locator = trimmed;
        }
        if (locator.length == 0) {
            throw new UncorrectableException("degenerate error locator");
        }
        int errors = locator.length - 1;
        if (errors * 2 > nsym) {
            throw new UncorrectableException("too many errors to correct: " + errors);
        }
        return locator;
    }

    /**
     * Chien search. The error at array index p has locator value X = alpha^(n-1-p) and
     * Lambda(alpha^-i) vanishes for i = n-1-p, so position p = n-1-i.
     */
    private static int[] findErrorPositions(int[] locator, int messageLength) {
        int errors = locator.length - 1;
        int[] positions = new int[errors];
        int count = 0;
        for (int i = 0; i < messageLength && count < errors; i++) {
            if (polyEval(locator, EXP[(255 - (i % 255)) % 255]) == 0) {
                positions[count++] = messageLength - 1 - i;
            }
        }
        return count == errors ? positions : null;
    }

    /**
     * Forney algorithm. With S(x) = sum_j S_j x^j and Lambda(x) = prod (1 - X_i x),
     * the magnitude of the error at position p is X * Omega(X^-1) / prod_{j!=i} (1 - X_j X^-1).
     */
    private static int[] correctErrata(int[] m, int[] synd, int[] positions) throws UncorrectableException {
        int length = m.length;
        int[] x = new int[positions.length];
        for (int i = 0; i < positions.length; i++) {
            x[i] = EXP[(length - 1 - positions[i]) % 255];
        }

        int[] locator = {1};
        for (int xi : x) {
            locator = polyMul(locator, new int[]{xi, 1});
        }

        int[] sPolynomial = new int[synd.length];
        for (int j = 0; j < synd.length; j++) {
            sPolynomial[synd.length - 1 - j] = synd[j];
        }
        int[] product = polyMul(sPolynomial, locator);
        int keep = Math.min(synd.length, product.length);
        int[] omega = new int[synd.length];
        System.arraycopy(product, product.length - keep, omega, synd.length - keep, keep);

        int[] out = m.clone();
        for (int i = 0; i < positions.length; i++) {
            int xInverse = inverse(x[i]);
            int denominator = 1;
            for (int j = 0; j < x.length; j++) {
                if (j != i) {
                    denominator = mul(denominator, 1 ^ mul(x[j], xInverse));
                }
            }
            if (denominator == 0) {
                throw new UncorrectableException("singular errata denominator");
            }
            int magnitude = polyEval(omega, xInverse);
            magnitude = mul(magnitude, inverse(denominator));
            out[positions[i]] ^= magnitude;
        }
        return out;
    }

    private static int[] toInts(byte[] a) {
        int[] r = new int[a.length];
        for (int i = 0; i < a.length; i++) {
            r[i] = a[i] & 0xFF;
        }
        return r;
    }

    private static byte[] toBytes(int[] a) {
        byte[] r = new byte[a.length];
        for (int i = 0; i < a.length; i++) {
            r[i] = (byte) a[i];
        }
        return r;
    }
}

package com.yimark.watermark;

import java.awt.image.BufferedImage;

/**
 * 8x8 DCT helpers limited to the two mid-frequency coefficients the watermark
 * uses, plus the matching basis matrices. Computing only (p,q) and (q,p)
 * instead of the full transform keeps both embedding and extraction fast, and
 * because the inverse DCT is linear, nudging a single coefficient and adding
 * its basis scaled by the delta is exactly equivalent to a full IDCT round trip.
 */
public final class DctTransform {

    public static final int SIZE = 8;
    public static final int COEFFICIENT_P = 2;
    public static final int COEFFICIENT_Q = 3;

    private static final double[] COS = new double[8 * 8];

    static {
        for (int k = 0; k < 8; k++) {
            for (int n = 0; n < 8; n++) {
                COS[k * 8 + n] = StrictMath.cos((2 * n + 1) * k * StrictMath.PI / 16);
            }
        }
    }

    private DctTransform() {}

    private static double alpha(int index) {
        return index == 0 ? 1 / Math.sqrt(2) : 1;
    }

    /** F(u,v) = 1/4 * alpha(u) * alpha(v) * sum of sample * cos * cos. */
    public static double coefficient(double[][] samples, int u, int v) {
        double sum = 0;
        for (int y = 0; y < SIZE; y++) {
            double rowSum = 0;
            for (int x = 0; x < SIZE; x++) {
                rowSum += samples[y][x] * COS[u * 8 + x];
            }
            sum += rowSum * COS[v * 8 + y];
        }
        return 0.25 * alpha(u) * alpha(v) * sum;
    }

    /** The spatial pattern contributed by a unit change of F(u,v). */
    public static double[][] basis(int u, int v) {
        double[][] b = new double[SIZE][SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                b[y][x] = 0.25 * alpha(u) * alpha(v) * COS[u * 8 + x] * COS[v * 8 + y];
            }
        }
        return b;
    }

    public static double[][] luminanceSamples(BufferedImage image, int blockX, int blockY) {
        double[][] samples = new double[SIZE][SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int rgb = image.getRGB(blockX + x, blockY + y);
                int r = rgb >> 16 & 0xFF;
                int g = rgb >> 8 & 0xFF;
                int b = rgb & 0xFF;
                samples[y][x] = 0.299 * r + 0.587 * g + 0.114 * b - 128;
            }
        }
        return samples;
    }

    /** Avoids a java.awt dependency in the extraction path used by tests. */
    public interface BufferedImageLike {
        int getRGB(int x, int y);
    }
}

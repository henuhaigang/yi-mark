package com.yimark.image;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** Plain image resampling helpers used by the watermark sync search. */
public final class ImageOps {

    private ImageOps() {}

    public static BufferedImage copy(BufferedImage source) {
        BufferedImage out = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = out.createGraphics();
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return out;
    }

    /** Drops the leading shiftX/shiftY pixels, re-aligning an 8 pixel block grid. */
    public static BufferedImage shift(BufferedImage source, int shiftX, int shiftY) {
        int width = Math.max(8, source.getWidth() - shiftX);
        int height = Math.max(8, source.getHeight() - shiftY);
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = out.createGraphics();
        graphics.drawImage(source, -shiftX, -shiftY, null);
        graphics.dispose();
        return out;
    }

    public static BufferedImage scale(BufferedImage source, double factor) {
        int width = Math.max(8, (int) Math.round(source.getWidth() * factor));
        int height = Math.max(8, (int) Math.round(source.getHeight() * factor));
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = out.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        return out;
    }

    public static BufferedImage rotate(BufferedImage source, double degrees) {
        BufferedImage out = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = out.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.translate(source.getWidth() / 2.0, source.getHeight() / 2.0);
        graphics.rotate(Math.toRadians(degrees));
        graphics.drawImage(source, -source.getWidth() / 2, -source.getHeight() / 2, null);
        graphics.dispose();
        return out;
    }

    /**
     * Applies a mosaic (pixelation) effect to a rectangular region of the image.
     *
     * @param source     the source image (modified in place)
     * @param x          left coordinate of the region
     * @param y          top coordinate of the region
     * @param width      width of the region
     * @param height     height of the region
     * @param blockSize  size of each mosaic block in pixels (e.g., 10, 20, 30)
     * @return the modified image (same instance as source)
     */
    public static BufferedImage applyMosaic(BufferedImage source, int x, int y, int width, int height, int blockSize) {
        if (blockSize <= 1) return source;
        int imgW = source.getWidth();
        int imgH = source.getHeight();
        // Clamp region to image bounds
        x = Math.max(0, Math.min(x, imgW - 1));
        y = Math.max(0, Math.min(y, imgH - 1));
        width = Math.min(width, imgW - x);
        height = Math.min(height, imgH - y);
        if (width <= 0 || height <= 0) return source;

        // Create a copy of the region to sample from
        BufferedImage region = source.getSubimage(x, y, width, height);
        int regionW = region.getWidth();
        int regionH = region.getHeight();

        Graphics2D g = source.createGraphics();
        // Draw mosaic blocks
        for (int by = 0; by < regionH; by += blockSize) {
            for (int bx = 0; bx < regionW; bx += blockSize) {
                int bw = Math.min(blockSize, regionW - bx);
                int bh = Math.min(blockSize, regionH - by);
                // Sample the center pixel of the block
                int sampleX = bx + bw / 2;
                int sampleY = by + bh / 2;
                int rgb = region.getRGB(sampleX, sampleY);
                // Fill the block
                g.setColor(new java.awt.Color(rgb));
                g.fillRect(x + bx, y + by, bw, bh);
            }
        }
        g.dispose();
        return source;
    }

    /**
     * Applies mosaic to multiple rectangular regions.
     *
     * @param source   the source image (modified in place)
     * @param regions  array of [x, y, width, height] for each region
     * @param blockSize mosaic block size in pixels
     * @return the modified image
     */
    public static BufferedImage applyMosaicRegions(BufferedImage source, int[][] regions, int blockSize) {
        for (int[] r : regions) {
            if (r.length >= 4) {
                applyMosaic(source, r[0], r[1], r[2], r[3], blockSize);
            }
        }
        return source;
    }

    private ImageOps() {}
}

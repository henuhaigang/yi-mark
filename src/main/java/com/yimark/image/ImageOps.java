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
}

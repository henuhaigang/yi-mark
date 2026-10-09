package com.yimark.image;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/** Corner markers that give a verifier a visual anchor for the protected region. */
public final class GeometryMarkers {

    public static void draw(BufferedImage image) {
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(255, 255, 255, 90));
        int size = Math.max(8, Math.min(image.getWidth(), image.getHeight()) / 60);
        graphics.fillRect(size, size, size, size);
        graphics.fillRect(image.getWidth() - 2 * size, size, size, size);
        graphics.fillRect(size, image.getHeight() - 2 * size, size, size);
        graphics.fillRect(image.getWidth() - 2 * size, image.getHeight() - 2 * size, size, size);
        graphics.dispose();
    }

    private GeometryMarkers() {}
}

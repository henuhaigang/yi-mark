package com.yimark.watermark;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DctWatermarkTest {

    private static final int WIDTH = 1200;
    private static final int HEIGHT = 800;

    @TempDir
    File tempDir;

    private static BufferedImage photo(int width, int height, long seed) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(seed);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double v = 128 + 70 * Math.sin(x / 37.0) * Math.cos(y / 53.0)
                        + 25 * Math.sin((x + y) / 11.0);
                int noise = random.nextInt(21) - 10;
                int value = (int) Math.max(0, Math.min(255, v + noise));
                image.setRGB(x, y, value << 16 | value << 8 | value);
            }
        }
        return image;
    }

    private static byte[] payload() {
        return ("SD3|0013aa3e-cca5-49c0-be66-c1f3ff0dbcf9|"
                + "a".repeat(64) + "|" + "b".repeat(64))
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("embed then extract recovers the payload from an untouched image")
    void cleanRoundTrip() {
        BufferedImage source = photo(WIDTH, HEIGHT, 1);
        BufferedImage embedded = DctWatermark.embed(source, payload());
        Extraction extraction = DctWatermark.extract(embedded);
        assertTrue(extraction.recovered, () -> "expected recovery, got: " + extraction);
        assertArrayEquals(payload(), extraction.payload());
        assertTrue(extraction.syncScore() > 0.99, "sync score " + extraction.syncScore());
    }

    @Test
    @DisplayName("extraction reports failure on an image that was never watermarked")
    void rejectsUnwatermarkedImage() {
        BufferedImage plain = photo(WIDTH, HEIGHT, 2);
        Extraction extraction = DctWatermark.extract(plain);
        assertFalse(extraction.recovered);
    }

    @Test
    @DisplayName("payload survives a JPEG q90 re-encode")
    void survivesJpeg() throws Exception {
        BufferedImage embedded = DctWatermark.embed(photo(WIDTH, HEIGHT, 3), payload());
        File file = new File(tempDir, "q90.jpg");
        javax.imageio.ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        javax.imageio.stream.ImageOutputStream ios = ImageIO.createImageOutputStream(file);
        writer.setOutput(ios);
        javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(0.90f);
        writer.write(null, new javax.imageio.IIOImage(embedded, null, null), param);
        ios.close();
        writer.dispose();

        Extraction extraction = DctWatermark.extract(ImageIO.read(file));
        assertTrue(extraction.recovered, () -> "expected recovery after JPEG, got: " + extraction);
        assertArrayEquals(payload(), extraction.payload());
    }

    @Test
    @DisplayName("payload survives a 50% downscale")
    void survivesDownscale() {
        BufferedImage embedded = DctWatermark.embed(photo(WIDTH, HEIGHT, 4), payload());
        BufferedImage scaled = new BufferedImage(WIDTH / 2, HEIGHT / 2, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(embedded, 0, 0, WIDTH / 2, HEIGHT / 2, null);
        graphics.dispose();

        Extraction extraction = DctWatermark.extract(scaled);
        assertTrue(extraction.recovered, () -> "expected recovery after downscale, got: " + extraction);
        assertArrayEquals(payload(), extraction.payload());
    }

    @Test
    @DisplayName("payload survives a 1.5 degree rotation")
    void survivesRotation() {
        BufferedImage embedded = DctWatermark.embed(photo(WIDTH, HEIGHT, 5), payload());
        BufferedImage rotated = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = rotated.createGraphics();
        graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.translate(WIDTH / 2.0, HEIGHT / 2.0);
        graphics.rotate(Math.toRadians(1.5));
        graphics.drawImage(embedded, -WIDTH / 2, -HEIGHT / 2, null);
        graphics.dispose();

        Extraction extraction = DctWatermark.extract(rotated);
        assertTrue(extraction.recovered, () -> "expected recovery after rotation, got: " + extraction);
        assertArrayEquals(payload(), extraction.payload());
    }

    @Test
    @DisplayName("payload survives a crop that is not aligned to the 8 pixel grid")
    void survivesUnalignedCrop() {
        BufferedImage embedded = DctWatermark.embed(photo(WIDTH, HEIGHT, 6), payload());
        BufferedImage cropped = embedded.getSubimage(137, 91, WIDTH - 200, HEIGHT - 150);
        Extraction extraction = DctWatermark.extract(cropped);
        assertTrue(extraction.recovered, () -> "expected recovery after crop, got: " + extraction);
        assertArrayEquals(payload(), extraction.payload());
    }

    @Test
    @DisplayName("a black rectangle over part of the image does not destroy the payload")
    void survivesLocalDamage() {
        BufferedImage embedded = DctWatermark.embed(photo(WIDTH, HEIGHT, 7), payload());
        java.awt.Graphics2D graphics = embedded.createGraphics();
        graphics.setColor(java.awt.Color.BLACK);
        graphics.fillRect(400, 300, 300, 200);
        graphics.dispose();

        Extraction extraction = DctWatermark.extract(embedded);
        assertTrue(extraction.recovered, () -> "expected recovery after local damage, got: " + extraction);
        assertArrayEquals(payload(), extraction.payload());
    }

    @Test
    @DisplayName("embedding is deterministic for the same payload and image")
    void deterministic() {
        BufferedImage source = photo(400, 512, 8);
        byte[] small = "SD3|test".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        BufferedImage first = DctWatermark.embed(source, small);
        BufferedImage second = DctWatermark.embed(source, small);
        for (int y = 0; y < 512; y++) {
            for (int x = 0; x < 400; x++) {
                assertEquals(first.getRGB(x, y), second.getRGB(x, y));
            }
        }
    }

    @Test
    @DisplayName("a too small image is rejected instead of silently losing bits")
    void rejectsSmallImage() {
        BufferedImage tiny = photo(64, 64, 9);
        assertNotNull(tiny);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> DctWatermark.embed(tiny, payload()));
    }
}

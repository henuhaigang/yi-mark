package com.yimark.image;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/** Tiled diagonal visible watermark carrying the issuer, purpose and document identity. */
public final class VisibleWatermark {

    /**
     * @param fontSize explicit font size; the caller computes it from the base size and
     *                 the user's size multiplier so preview and final render look identical
     * @param degrees  diagonal slant angle, negative tilts the text up to the right
     */
    public static BufferedImage apply(BufferedImage source, String issuer, String purpose,
                                      String identity, double opacity, int fontSize, double degrees) {
        int width = source.getWidth();
        int height = source.getHeight();
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = out.createGraphics();
        graphics.drawImage(source, 0, 0, null);
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) opacity));
        graphics.setColor(Color.WHITE);
        Font font = new Font("SansSerif", Font.BOLD, fontSize);
        graphics.setFont(font);
        graphics.rotate(Math.toRadians(degrees), width / 2.0, height / 2.0);

        // 只拼接非空片段，避免多余分隔符
        StringBuilder sb = new StringBuilder();
        if (issuer != null && !issuer.isEmpty()) sb.append(issuer);
        if (purpose != null && !purpose.isEmpty()) {
            if (!sb.isEmpty()) sb.append(" · ");
            sb.append(purpose);
        }
        if (identity != null && !identity.isEmpty()) {
            if (!sb.isEmpty()) sb.append(" · ");
            sb.append(identity);
        }
        String text = sb.toString();

        // 用 FontMetrics 测量真实文本宽度，避免长文本重叠
        java.awt.font.FontRenderContext frc = graphics.getFontRenderContext();
        int textWidth = (int) Math.ceil(font.getStringBounds(text, frc).getWidth());
        int rowStep = Math.max(24, (int) Math.round(fontSize * 2.4));
        int colStep = Math.max(textWidth + 24, (int) Math.round(fontSize * 9.0)); // 文本宽 + 24px 内边距

        for (int y = -height; y < height * 2; y += rowStep) {
            // 奇数行错位半个列间距，形成交错布局
            boolean oddRow = ((y + height) / rowStep) % 2 != 0;
            int xStart = oddRow ? -width + colStep / 2 : -width;
            for (int x = xStart; x < width * 2; x += colStep) {
                graphics.drawString(text, x, y);
            }
        }
        graphics.dispose();
        return out;
    }

    /** Font size used by the GUI at a given image width, before the user's multiplier. */
    public static int baseFontSize(int imageWidth) {
        return Math.max(18, imageWidth / 28);
    }

    private VisibleWatermark() {}
}

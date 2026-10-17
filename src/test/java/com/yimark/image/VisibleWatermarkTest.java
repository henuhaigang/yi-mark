package com.yimark.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VisibleWatermarkTest {

    @Test
    @DisplayName("line spacing continuously scales the vertical row step")
    void rowStepScalesWithLineSpacing() {
        int fontSize = 40;
        int tight = VisibleWatermark.rowStepFor(fontSize, 1.0);
        int defaultStep = VisibleWatermark.rowStepFor(fontSize, VisibleWatermark.DEFAULT_LINE_SPACING_RATIO);
        int loose = VisibleWatermark.rowStepFor(fontSize, 8.0);

        assertEquals(fontSize, tight);
        assertEquals(96, defaultStep);
        assertEquals(320, loose);
        assertTrue(tight < defaultStep);
        assertTrue(defaultStep < loose);
    }

    @Test
    @DisplayName("invalid line spacing falls back to the default spacing")
    void invalidLineSpacingFallsBackToDefault() {
        int fontSize = 40;
        assertEquals(
                VisibleWatermark.rowStepFor(fontSize, VisibleWatermark.DEFAULT_LINE_SPACING_RATIO),
                VisibleWatermark.rowStepFor(fontSize, Double.NaN)
        );
        assertEquals(
                VisibleWatermark.rowStepFor(fontSize, VisibleWatermark.DEFAULT_LINE_SPACING_RATIO),
                VisibleWatermark.rowStepFor(fontSize, Double.POSITIVE_INFINITY)
        );
    }
}

package io.github.minifaizeelang.grouplives.client;

import net.minecraft.client.gui.Gui;

/**
 * Shared visual language of the mod UI: yellow-on-black, drop shadows,
 * diagonal hazard stripes and a radiation trefoil. All drawing is plain
 * drawRect calls so it needs no textures.
 */
public final class UiTheme {

    /** Fallout-terminal green palette. (Constant names reference the old amber theme.) */
    public static final int YELLOW = 0xFF9CC97A;
    public static final int YELLOW_BRIGHT = 0xFFC9E5A8;
    public static final int YELLOW_DIM = 0xFF5F7A46;
    public static final int PANEL_BG = 0xF20C110C;
    public static final int PANEL_EDGE = 0xFF3E523E;
    public static final int TEXT = 0xFFE2EFDC;
    public static final int TEXT_DIM = 0xFF8FA88F;
    public static final int TEXT_FADED = 0xFF5E725E;
    public static final int RED = 0xFFD0483C;
    public static final int GREEN = 0xFF7CC24A;
    public static final int BLUE = 0xFF5B8FFB;
    public static final int HP_GREEN = 0xFF55FF55;
    public static final int HP_ORANGE = 0xFFFFAA00;
    public static final int HP_RED = 0xFFFF5555;

    private UiTheme() {
    }

    /** Two stacked translucent rects offset down-right: a cheap drop shadow. */
    public static void shadow(int x, int y, int w, int h) {
        Gui.drawRect(x + 3, y + 3, x + w + 3, y + h + 3, 0x33000000);
        Gui.drawRect(x + 2, y + 2, x + w + 2, y + h + 2, 0x44000000);
    }

    /** Yellow/black diagonal hazard stripes (industrial warning tape). */
    public static void hazardStripes(int x, int y, int w, int h) {
        Gui.drawRect(x, y, x + w, y + h, 0xFF0A0810);
        for (int j = 0; j < h; j++) {
            int s = ((-(j)) % 8 + 8) % 8;
            for (int k = s; k < w; k += 8) {
                int end = Math.min(k + 4, w);
                Gui.drawRect(x + k, y + j, x + end, y + j + 1, YELLOW);
            }
        }
    }

    /** Small pixel radiation trefoil (yellow blades, dark core). */
    public static void trefoil(int x, int y) {
        trefoil(x, y, YELLOW);
    }

    /** Radiation trefoil in an arbitrary color (used for dim watermarks). */
    public static void trefoil(int x, int y, int color) {
        Gui.drawRect(x + 3, y, x + 5, y + 2, color);
        Gui.drawRect(x, y + 4, x + 3, y + 6, color);
        Gui.drawRect(x + 4, y + 4, x + 7, y + 6, color);
        Gui.drawRect(x + 2, y + 2, x + 5, y + 5, color);
        Gui.drawRect(x + 3, y + 3, x + 4, y + 4, 0xFF0A0810);
    }

    /** Subtle CRT scanlines over a region (Fallout terminal feel). */
    public static void scanlines(int x, int y, int w, int h) {
        for (int j = y; j < y + h; j += 3) {
            Gui.drawRect(x, j, x + w, j + 1, 0x06FFFFFF);
        }
    }

    /** Dark themed backdrop for text fields (draw before drawTextBox). */
    public static void field(int x, int y, int w, int h) {
        Gui.drawRect(x - 1, y - 1, x + w + 1, y + h + 1, PANEL_EDGE);
        Gui.drawRect(x, y, x + w, y + h, 0xFF0A0810);
    }

    /** Yellow L-shaped corner brackets over the panel corners. */
    public static void cornerBrackets(int x, int y, int w, int h) {
        Gui.drawRect(x, y, x + 6, y + 1, YELLOW);
        Gui.drawRect(x, y, x + 1, y + 6, YELLOW);
        Gui.drawRect(x + w - 6, y, x + w, y + 1, YELLOW);
        Gui.drawRect(x + w - 1, y, x + w, y + 6, YELLOW);
        Gui.drawRect(x, y + h - 1, x + 6, y + h, YELLOW);
        Gui.drawRect(x, y + h - 6, x + 1, y + h, YELLOW);
        Gui.drawRect(x + w - 6, y + h - 1, x + w, y + h, YELLOW);
        Gui.drawRect(x + w - 1, y + h - 6, x + w, y + h, YELLOW);
    }

    /**
     * Standard panel: drop shadow, near-black fill, dark border, colored
     * accent bar on the left and yellow corner brackets.
     */
    public static void panel(int x, int y, int w, int h, int accent) {
        shadow(x, y, w, h);
        Gui.drawRect(x, y, x + w, y + h, PANEL_BG);
        Gui.drawRect(x, y, x + w, y + 1, PANEL_EDGE);
        Gui.drawRect(x, y + h - 1, x + w, y + h, PANEL_EDGE);
        Gui.drawRect(x, y, x + 1, y + h, PANEL_EDGE);
        Gui.drawRect(x + w - 1, y, x + w, y + h, PANEL_EDGE);
        Gui.drawRect(x + 3, y + 3, x + 5, y + h - 3, accent);
        cornerBrackets(x, y, w, h);
    }

    /** Panel with a hazard-stripe strip along the top edge. */
    public static void panelHazard(int x, int y, int w, int h, int accent) {
        panel(x, y, w, h, accent);
        hazardStripes(x + 1, y + 1, w - 2, 3);
    }
}

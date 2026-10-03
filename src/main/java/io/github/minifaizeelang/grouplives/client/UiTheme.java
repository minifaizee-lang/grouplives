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

    /** Small pixel radiation trefoil (accent blades, dark core). */
    public static void trefoil(int x, int y) {
        pixels(TREFOIL_PIXELS, x, y, 1, YELLOW, 0xFF0A0F0A);
    }

    /** Radiation trefoil in an arbitrary color (used for dim watermarks). */
    public static void trefoil(int x, int y, int color) {
        pixels(TREFOIL_PIXELS, x, y, 1, color, 0xFF0A0810);
    }

    /** Radiation trefoil glyph, 7x7 cells ('X' = color, 'D' = dark core). */
    public static final String[] TREFOIL_PIXELS = {
            "...XX..",
            "...XX..",
            "..XXXX.",
            "..XDDX.",
            "..XDDX.",
            "XXXXXXX",
            "XXX.XXX",
    };

    /** Lightning bolt glyph, 7x7 cells. */
    public static final String[] BOLT_PIXELS = {
            "...XX..",
            "..XX...",
            ".XX....",
            "XXXXXX.",
            "...XX..",
            "..XX...",
            ".XX....",
    };

    /** Clipboard checklist glyph, 7x7 cells. */
    public static final String[] LIST_PIXELS = {
            ".XXXXX.",
            "X.....X",
            "X.XXX.X",
            "X.....X",
            "X.XXX.X",
            "X.....X",
            ".XXXXX.",
    };

    /** Renders a bitmap glyph: 'X' = color, 'D' = darkColor, '.' = skip. */
    public static void pixels(String[] rows, int x, int y, int scale, int color, int darkColor) {
        for (int j = 0; j < rows.length; j++) {
            for (int i = 0; i < rows[j].length(); i++) {
                char c = rows[j].charAt(i);
                if (c == 'X') {
                    Gui.drawRect(x + i * scale, y + j * scale, x + (i + 1) * scale, y + (j + 1) * scale, color);
                } else if (c == 'D') {
                    Gui.drawRect(x + i * scale, y + j * scale, x + (i + 1) * scale, y + (j + 1) * scale, darkColor);
                }
            }
        }
    }

    /** Subtle CRT scanlines over a region (Fallout terminal feel). */
    public static void scanlines(int x, int y, int w, int h) {
        for (int j = y; j < y + h; j += 3) {
            Gui.drawRect(x, j, x + w, j + 1, 0x06FFFFFF);
        }
    }

    /** Subtle white grid (сетка) for filling empty panel space. */
    public static void grid(int x, int y, int w, int h) {
        for (int i = 0; i <= w; i += 10) {
            Gui.drawRect(x + i, y, x + i + 1, y + h, 0x14FFFFFF);
        }
        for (int j = 0; j <= h; j += 10) {
            Gui.drawRect(x, y + j, x + w, y + j + 1, 0x14FFFFFF);
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

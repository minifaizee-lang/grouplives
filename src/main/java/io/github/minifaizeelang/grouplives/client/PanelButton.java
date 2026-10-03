package io.github.minifaizeelang.grouplives.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

/**
 * Wide settings panel styled in the mod's yellow/black theme: dark fill with
 * a drop shadow, thin border with yellow corner brackets, colored accent bar
 * on the left edge, title in the accent color, gray description, current
 * value in the top-right corner.
 */
public class PanelButton extends GuiButton {

    private final int accentColor;
    private int currentAccent;
    public String description = "";
    public String value = "";

    public PanelButton(int buttonId, int x, int y, int widthIn, int heightIn,
                       String title, int accentColor) {
        super(buttonId, x, y, widthIn, heightIn, title);
        this.accentColor = accentColor;
        this.currentAccent = accentColor;
    }

    /** Allows live recolor of the accent bar (e.g. border toggle state). */
    public void setAccent(int accent) {
        this.currentAccent = accent;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) {
            return;
        }
        this.hovered = this.enabled
                && mouseX >= this.x && mouseX < this.x + this.width
                && mouseY >= this.y && mouseY < this.y + this.height;

        int fill = this.hovered ? 0xFF241D0C : 0xFF12101A;
        int edge = this.hovered ? UiTheme.YELLOW : UiTheme.PANEL_EDGE;
        UiTheme.shadow(this.x, this.y, this.width, this.height);
        drawRect(this.x, this.y, this.x + this.width, this.y + this.height, fill);
        drawRect(this.x, this.y, this.x + this.width, this.y + 1, edge);
        drawRect(this.x, this.y + this.height - 1, this.x + this.width, this.y + this.height, edge);
        drawRect(this.x, this.y, this.x + 1, this.y + this.height, edge);
        drawRect(this.x + this.width - 1, this.y, this.x + this.width, this.y + this.height, edge);
        drawRect(this.x + 3, this.y + 3, this.x + 5, this.y + this.height - 3, this.currentAccent);
        UiTheme.cornerBrackets(this.x, this.y, this.width, this.height);

        mc.fontRenderer.drawStringWithShadow(this.displayString, this.x + 11, this.y + 5, this.currentAccent);
        if (!this.description.isEmpty()) {
            mc.fontRenderer.drawStringWithShadow(this.description, this.x + 13, this.y + this.height - 14, UiTheme.TEXT_DIM);
        }
        if (!this.value.isEmpty()) {
            int valueWidth = mc.fontRenderer.getStringWidth(this.value);
            mc.fontRenderer.drawStringWithShadow(this.value,
                    this.x + this.width - 11 - valueWidth, this.y + 5, UiTheme.TEXT);
        }
    }
}

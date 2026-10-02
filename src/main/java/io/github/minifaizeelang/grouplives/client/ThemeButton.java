package io.github.minifaizeelang.grouplives.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

/**
 * The standard mod button: dark fill with a drop shadow, yellow border that
 * brightens on hover, a small accent notch on the left and centered text.
 * Replaces vanilla gray buttons so every control matches the lobby theme.
 */
public class ThemeButton extends GuiButton {

    public ThemeButton(int buttonId, int x, int y, int widthIn, int heightIn, String label) {
        super(buttonId, x, y, widthIn, heightIn, label);
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) {
            return;
        }
        this.hovered = this.enabled
                && mouseX >= this.x && mouseX < this.x + this.width
                && mouseY >= this.y && mouseY < this.y + this.height;

        int fill = this.hovered ? 0xFF2A2410 : 0xFF12101A;
        int edge = this.hovered ? UiTheme.YELLOW : UiTheme.PANEL_EDGE;
        int notch = this.hovered ? UiTheme.YELLOW : UiTheme.YELLOW_DIM;
        int textColor = !this.enabled ? UiTheme.TEXT_FADED
                : this.hovered ? UiTheme.YELLOW_BRIGHT : UiTheme.TEXT;

        UiTheme.shadow(this.x, this.y, this.width, this.height);
        drawRect(this.x, this.y, this.x + this.width, this.y + this.height, fill);
        drawRect(this.x, this.y, this.x + this.width, this.y + 1, edge);
        drawRect(this.x, this.y + this.height - 1, this.x + this.width, this.y + this.height, edge);
        drawRect(this.x, this.y, this.x + 1, this.y + this.height, edge);
        drawRect(this.x + this.width - 1, this.y, this.x + this.width, this.y + this.height, edge);
        drawRect(this.x + 3, this.y + 2, this.x + 4, this.y + this.height - 2, notch);

        drawCenteredString(mc.fontRenderer, this.displayString,
                this.x + this.width / 2, this.y + (this.height - 8) / 2, textColor);
    }
}

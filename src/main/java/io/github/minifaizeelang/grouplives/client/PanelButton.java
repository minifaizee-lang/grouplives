package io.github.minifaizeelang.grouplives.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

/**
 * Wide settings panel styled after the reference image: dark fill, thin border,
 * colored accent bar on the left edge, title in the accent color, gray
 * description, current value in the top-right corner.
 */
public class PanelButton extends GuiButton {

    private final int accentColor;
    public String description = "";
    public String value = "";

    public PanelButton(int buttonId, int x, int y, int widthIn, int heightIn,
                       String title, int accentColor) {
        super(buttonId, x, y, widthIn, heightIn, title);
        this.accentColor = accentColor;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) {
            return;
        }
        this.hovered = this.enabled
                && mouseX >= this.x && mouseX < this.x + this.width
                && mouseY >= this.y && mouseY < this.y + this.height;

        int fill = this.hovered ? 0xFF28222F : 0xFF17121C;
        int edge = 0xFF3B3344;
        drawRect(this.x, this.y, this.x + this.width, this.y + this.height, fill);
        drawRect(this.x, this.y, this.x + this.width, this.y + 1, edge);
        drawRect(this.x, this.y + this.height - 1, this.x + this.width, this.y + this.height, edge);
        drawRect(this.x, this.y, this.x + 1, this.y + this.height, edge);
        drawRect(this.x + this.width - 1, this.y, this.x + this.width, this.y + this.height, edge);
        drawRect(this.x + 3, this.y + 3, this.x + 7, this.y + this.height - 3, this.accentColor);

        mc.fontRenderer.drawStringWithShadow(this.displayString, this.x + 13, this.y + 5, this.accentColor);
        if (!this.description.isEmpty()) {
            mc.fontRenderer.drawStringWithShadow(this.description, this.x + 13, this.y + this.height - 14, 0xFF9A8FA8);
        }
        if (!this.value.isEmpty()) {
            int valueWidth = mc.fontRenderer.getStringWidth(this.value);
            mc.fontRenderer.drawStringWithShadow(this.value,
                    this.x + this.width - 11 - valueWidth, this.y + 5, 0xFFF5F2F7);
        }
    }
}

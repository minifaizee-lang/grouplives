package io.github.minifaizeelang.grouplives.client;

import net.minecraft.client.gui.GuiScreen;

import java.io.IOException;

/**
 * Quick-tasks overlay: the same always-visible panel but clickable, opened
 * by the Tasks key without opening the chat. Transparent (the world stays
 * visible) and does not pause the game. Only the assignee's own tasks react
 * to clicks.
 */
public class GuiTasksOverlay extends GuiScreen {

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // No background - the world stays fully visible behind the panel.
        TaskHud.drawPanel(this.mc, true);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0) {
            TaskHud.handlePanelClick(this.mc, mouseX, mouseY);
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1 || keyCode == ClientEvents.TASKS_KEY.getKeyCode()) {
            this.mc.displayGuiScreen(null);
        } else {
            super.keyTyped(typedChar, keyCode);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}

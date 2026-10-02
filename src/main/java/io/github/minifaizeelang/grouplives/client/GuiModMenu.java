package io.github.minifaizeelang.grouplives.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.io.IOException;

/**
 * Main mod menu (Fallout-terminal style): one entry point that gathers the
 * lobby, the team task constructor and the teammate teleport menu.
 */
public class GuiModMenu extends GuiScreen {

    private static final int ID_LOBBY = 100;
    private static final int ID_TASKS = 101;
    private static final int ID_TP = 102;
    private static final int ID_BACK = 0;

    private final GuiScreen parentScreen;

    public GuiModMenu(GuiScreen parentScreen) {
        this.parentScreen = parentScreen;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        int x = this.width / 2 - 130;
        this.buttonList.add(new ThemeButton(ID_LOBBY, x, 76, 260, 24, "ЛОББИ ИВЕНТА"));
        this.buttonList.add(new ThemeButton(ID_TASKS, x, 106, 260, 24, "ЗАДАЧИ КОМАНДЫ"));
        this.buttonList.add(new ThemeButton(ID_TP, x, 136, 260, 24, "ТЕЛЕПОРТ К ТОВАРИЩУ"));
        this.buttonList.add(new ThemeButton(ID_BACK, x, 178, 260, 18, "НАЗАД"));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawGradientRect(0, 0, this.width, this.height, 0xFF231826, 0xFF05040A);
        UiTheme.scanlines(0, 0, this.width, this.height);

        int px = this.width / 2 - 150;
        UiTheme.panelHazard(px, 34, 300, 178, UiTheme.YELLOW);
        UiTheme.trefoil(px + 12, 46);

        int cx = this.width / 2;
        drawCenteredString(this.fontRenderer, "КОМАНДНОЕ ВЫЖИВАНИЕ", cx, 44, UiTheme.YELLOW);
        drawCenteredString(this.fontRenderer, "ГЛАВНОЕ МЕНЮ", cx, 56, UiTheme.TEXT_DIM);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case ID_LOBBY:
                ClientEvents.openLobbyScreen(this);
                return;
            case ID_TASKS:
                this.mc.displayGuiScreen(new GuiTeamTasks(this));
                return;
            case ID_TP:
                this.mc.displayGuiScreen(new GuiTeamTeleport(this));
                return;
            case ID_BACK:
                this.mc.displayGuiScreen(this.parentScreen);
                return;
            default:
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) {
            this.mc.displayGuiScreen(this.parentScreen);
        } else {
            super.keyTyped(typedChar, keyCode);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }
}

package io.github.minifaizeelang.grouplives.client;

import io.github.minifaizeelang.grouplives.ModConfig;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.io.IOException;

/**
 * Event settings screen styled after the reference image: dark background,
 * glitch-style title, wide panels with colored accent bars. The values are
 * applied by sending /event commands to the server (which re-checks
 * permissions), so nothing here trusts the client.
 */
public class GuiEventSettings extends GuiScreen {

    private static final int ID_BORDER_PANEL = 10;
    private static final int ID_SPACING_PANEL = 11;
    private static final int ID_START_PANEL = 12;
    private static final int ID_BACK = 0;

    /** Session-persistent values shown in the GUI (initialized from config defaults). */
    public static int borderSize = ModConfig.eventBorderSize;
    public static int teamSpacing = ModConfig.teamSpacing;

    private static final long CONFIRM_WINDOW_MS = 5000;

    private final GuiScreen parentScreen;
    private PanelButton borderPanel;
    private PanelButton spacingPanel;
    private PanelButton startPanel;
    private long startArmedUntil;

    public GuiEventSettings(GuiScreen parentScreen) {
        this.parentScreen = parentScreen;
    }

    @Override
    public void initGui() {
        int panelWidth = Math.min(this.width - 40, 400);
        int x0 = this.width / 2 - panelWidth / 2;
        int y = 52;

        this.buttonList.add(this.borderPanel = new PanelButton(ID_BORDER_PANEL, x0, y, panelWidth, 38,
                "ГРАНИЦА МИРА", 0xFFE8B33C));
        this.borderPanel.description = "Клик — применить сейчас";
        y += 38;
        addSteppers(x0, panelWidth, y, new int[]{-1000, -100, 100, 1000}, 20);
        y += 18;

        this.buttonList.add(this.spacingPanel = new PanelButton(ID_SPACING_PANEL, x0, y, panelWidth, 38,
                "ДИСТАНЦИЯ МЕЖДУ КОМАНДАМИ", 0xFF5B8FFB));
        this.spacingPanel.description = "Минимальное расстояние между командами";
        y += 38;
        addSteppers(x0, panelWidth, y, new int[]{-500, -100, 100, 500}, 24);
        y += 18;

        this.buttonList.add(this.startPanel = new PanelButton(ID_START_PANEL, x0, y, panelWidth, 38,
                "СТАРТ ИВЕНТА", 0xFFD0483C));
        y += 44;

        this.buttonList.add(new GuiButton(ID_BACK, this.width / 2 - 100, Math.max(y, this.height - 30), 200, 20, "Назад"));
        refreshPanels();
    }

    private void addSteppers(int x0, int panelWidth, int y, int[] deltas, int firstId) {
        int buttonWidth = 44;
        int gap = 4;
        int total = deltas.length * buttonWidth + (deltas.length - 1) * gap;
        int x = x0 + panelWidth - total;
        for (int i = 0; i < deltas.length; i++) {
            int delta = deltas[i];
            String label = delta > 0 ? "+" + delta : String.valueOf(delta);
            this.buttonList.add(new GuiButton(firstId + i, x, y, buttonWidth, 16, label));
            x += buttonWidth + gap;
        }
    }

    private void refreshPanels() {
        this.borderPanel.value = borderSize + " x " + borderSize;
        this.spacingPanel.value = teamSpacing + " блоков";
        boolean armed = System.currentTimeMillis() < this.startArmedUntil;
        this.startPanel.description = armed
                ? "Нажмите ещё раз для подтверждения"
                : "Распределить команды по карте";
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawGradientRect(0, 0, this.width, this.height, 0xFF231826, 0xFF05040A);
        String title = "НАСТРОЙКИ ИВЕНТА";
        int cx = this.width / 2;
        drawCenteredString(this.fontRenderer, title, cx - 1, 16, 0xFFFF4D4D);
        drawCenteredString(this.fontRenderer, title, cx + 1, 16, 0xFF4DFFFF);
        drawCenteredString(this.fontRenderer, title, cx, 16, 0xFFF5F2F7);
        drawCenteredString(this.fontRenderer, "границы мира и распределение команд", cx, 28, 0xFF8A7F96);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case ID_BACK:
                this.mc.displayGuiScreen(this.parentScreen);
                return;
            case ID_BORDER_PANEL:
                sendCommand("/event border " + borderSize);
                return;
            case ID_SPACING_PANEL:
                return; // value-only panel, adjusted with the stepper buttons
            case ID_START_PANEL: {
                if (System.currentTimeMillis() < this.startArmedUntil) {
                    this.startArmedUntil = 0;
                    sendCommand("/event start " + borderSize + " " + teamSpacing);
                    this.mc.displayGuiScreen(null);
                } else {
                    this.startArmedUntil = System.currentTimeMillis() + CONFIRM_WINDOW_MS;
                }
                refreshPanels();
                return;
            }
            default:
                break;
        }
        // Steppers: ids 20-23 border, 24-27 spacing.
        if (button.id >= 20 && button.id <= 23) {
            borderSize = clamp(borderSize + stepperDelta(button.id, 20), 1000, 600000);
            refreshPanels();
        } else if (button.id >= 24 && button.id <= 27) {
            teamSpacing = clamp(teamSpacing + stepperDelta(button.id, 24), 0, 100000);
            refreshPanels();
        }
    }

    private static int stepperDelta(int id, int firstId) {
        switch (id - firstId) {
            case 0:
                return -1000;
            case 1:
                return -100;
            case 2:
                return 100;
            default:
                return 1000;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void sendCommand(String command) {
        if (this.mc.player != null) {
            this.mc.player.sendChatMessage(command);
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

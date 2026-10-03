package io.github.minifaizeelang.grouplives.client;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Main mod menu (Fallout-terminal style): stacked section cards like the
 * reference - icon box, small header, title, description, arrow button.
 * Click a card (or its arrow) to open the section.
 */
public class GuiModMenu extends GuiScreen {

    private static class Card {
        final int id;
        final String header;
        final String title;
        final String desc;

        Card(int id, String header, String title, String desc) {
            this.id = id;
            this.header = header;
            this.title = title;
            this.desc = desc;
        }
    }

    private static final int ID_LOBBY = 100;
    private static final int ID_TP = 101;
    private static final int ID_TASKS = 102;

    private final Card[] cards = {
            new Card(ID_LOBBY, "• ЛОББИ ИВЕНТА", "Лобби", "Соберите команды и настройте мир."),
            new Card(ID_TP, "• 3 УЧАСТНИКА В СЕТИ", "Телепорты", "Нажмите на сокомандника, чтобы переместиться."),
            new Card(ID_TASKS, "• ЗАДАНИЯ", "Задания", "Создавайте цели и отмечайте выполнение."),
    };

    private final GuiScreen parentScreen;
    private final List<int[]> cardRects = new ArrayList<int[]>();
    private int hoveredCard = -1;

    public GuiModMenu(GuiScreen parentScreen) {
        this.parentScreen = parentScreen;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        this.cardRects.clear();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawGradientRect(0, 0, this.width, this.height, 0xFF0A0F0A, 0xFF040704);
        UiTheme.scanlines(0, 0, this.width, this.height);

        // Outer thin frame with a dash at the top center (reference style)
        int fx = this.width / 2 - 178;
        int fw = 356;
        int fy = 24;
        int fh = this.height - 24 - fy;
        Gui.drawRect(fx, fy, fx + fw, fy + 1, UiTheme.PANEL_EDGE);
        Gui.drawRect(fx, fy + fh - 1, fx + fw, fy + fh, UiTheme.PANEL_EDGE);
        Gui.drawRect(fx, fy, fx + 1, fy + fh, UiTheme.PANEL_EDGE);
        Gui.drawRect(fx + fw - 1, fy, fx + fw, fy + fh, UiTheme.PANEL_EDGE);
        Gui.drawRect(this.width / 2 - 12, fy, this.width / 2 + 12, fy + 1, UiTheme.YELLOW_DIM);
        Gui.drawRect(this.width / 2 - 12, fy + 1, this.width / 2 - 8, fy + 2, UiTheme.YELLOW_DIM);
        Gui.drawRect(this.width / 2 + 8, fy + 1, this.width / 2 + 12, fy + 2, UiTheme.YELLOW_DIM);

        int cx = this.width / 2;
        drawCenteredString(this.fontRenderer, "КОМАНДНОЕ ВЫЖИВАНИЕ", cx, 34, UiTheme.YELLOW_BRIGHT);
        drawCenteredString(this.fontRenderer, "государственный терминал · выберите раздел", cx, 46, UiTheme.TEXT_FADED);

        hoveredCard = -1;
        cardRects.clear();
        int cardX = cx - 150;
        int cardW = 300;
        int cardH = 52;
        int y = 78;
        for (int i = 0; i < cards.length; i++) {
            Card card = cards[i];
            boolean hovered = mouseX >= cardX && mouseX < cardX + cardW
                    && mouseY >= y && mouseY < y + cardH;
            int edge = hovered ? UiTheme.YELLOW_BRIGHT : 0xFF3E523E;
            int fill = hovered ? 0xFF12190F : 0xFF0D110D;
            Gui.drawRect(cardX, y, cardX + cardW, y + cardH, fill);
            Gui.drawRect(cardX, y, cardX + cardW, y + 1, edge);
            Gui.drawRect(cardX, y + cardH - 1, cardX + cardW, y + cardH, edge);
            Gui.drawRect(cardX, y, cardX + 1, y + cardH, edge);
            Gui.drawRect(cardX + cardW - 1, y, cardX + cardW, y + cardH, edge);

            // Icon box
            int ib = y + (cardH - 26) / 2;
            Gui.drawRect(cardX + 10, ib, cardX + 34, ib + 26, 0xFF0A0F0A);
            Gui.drawRect(cardX + 10, ib, cardX + 11, ib + 26, edge);
            Gui.drawRect(cardX + 33, ib, cardX + 34, ib + 26, edge);
            drawGlyph(card.id, cardX + 16, ib + 6);

            this.fontRenderer.drawStringWithShadow(card.header, cardX + 44, y + 6, UiTheme.TEXT_FADED);
            this.fontRenderer.drawStringWithShadow(card.title, cardX + 44, y + 16, hovered ? UiTheme.YELLOW_BRIGHT : UiTheme.TEXT);
            this.fontRenderer.drawStringWithShadow(card.desc, cardX + 44, y + 27, UiTheme.TEXT_DIM);
            String num = i == 0 ? "01" : i == 1 ? "02" : "03";
            this.fontRenderer.drawStringWithShadow(num, cardX + cardW - 18 - this.fontRenderer.getStringWidth(num), y + 6, UiTheme.TEXT_FADED);

            // Arrow box
            Gui.drawRect(cardX + cardW - 26, y + (cardH - 16) / 2, cardX + cardW - 10, y + (cardH - 16) / 2 + 16, 0xFF0A0F0A);
            Gui.drawRect(cardX + cardW - 26, y + (cardH - 16) / 2, cardX + cardW - 25, y + (cardH - 16) / 2 + 16, hovered ? UiTheme.YELLOW : edge);
            Gui.drawRect(cardX + cardW - 11, y + (cardH - 16) / 2, cardX + cardW - 10, y + (cardH - 16) / 2 + 16, hovered ? UiTheme.YELLOW : edge);
            Gui.drawRect(cardX + cardW - 25, y + cardH / 2 - 1, cardX + cardW - 13, y + cardH / 2, hovered ? UiTheme.YELLOW : UiTheme.TEXT_DIM);
            Gui.drawRect(cardX + cardW - 15, y + cardH / 2 - 3, cardX + cardW - 13, y + cardH / 2 + 1, hovered ? UiTheme.YELLOW : UiTheme.TEXT_DIM);
            Gui.drawRect(cardX + cardW - 17, y + cardH / 2 - 5, cardX + cardW - 15, y + cardH / 2 - 3, hovered ? UiTheme.YELLOW : UiTheme.TEXT_DIM);
            Gui.drawRect(cardX + cardW - 25, y + cardH / 2 - 1, cardX + cardW - 13, y + cardH / 2 + 1, hovered ? UiTheme.YELLOW : UiTheme.TEXT_DIM);

            cardRects.add(new int[]{cardX, y, cardW, cardH, card.id});
            if (hovered) {
                hoveredCard = i;
            }
            y += cardH + 14;
        }

        // Footer hints and signal bars
        this.fontRenderer.drawStringWithShadow("[ ЛКМ ] ВЫБОР   [ ESC ] НАЗАД", fx + 12, fy + fh - 12, UiTheme.TEXT_FADED);
        int barsX = fx + fw - 30;
        for (int b = 0; b < 5; b++) {
            int bh = 2 + b * 2;
            Gui.drawRect(barsX + b * 4, fy + fh - 12 - bh + 8, barsX + b * 4 + 2, fy + fh - 12 + 8, b < 4 ? UiTheme.YELLOW_DIM : 0xFF2E3A2E);
        }
    }

    /** Glyphs for the card icon boxes: bitmap patterns at 3x scale (21px). */
    private void drawGlyph(int id, int x, int y) {
        if (id == ID_LOBBY) {
            UiTheme.pixels(UiTheme.TREFOIL_PIXELS, x, y, 3, UiTheme.YELLOW, UiTheme.PANEL_BG);
        } else if (id == ID_TP) {
            UiTheme.pixels(UiTheme.BOLT_PIXELS, x, y, 3, UiTheme.YELLOW, UiTheme.PANEL_BG);
        } else {
            UiTheme.pixels(UiTheme.LIST_PIXELS, x, y, 3, UiTheme.YELLOW, UiTheme.PANEL_BG);
        }
    }


    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0) {
            for (int[] rect : cardRects) {
                if (mouseX >= rect[0] && mouseX <= rect[0] + rect[2]
                        && mouseY >= rect[1] && mouseY <= rect[1] + rect[3]) {
                    openSection(rect[4]);
                    return;
                }
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void openSection(int id) {
        if (id == ID_LOBBY) {
            ClientEvents.openLobbyScreen(this);
        } else if (id == ID_TP) {
            this.mc.displayGuiScreen(new GuiTeamTeleport(this));
        } else if (id == ID_TASKS) {
            this.mc.displayGuiScreen(new GuiTeamTasks(this));
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

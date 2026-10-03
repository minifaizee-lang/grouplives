package io.github.minifaizeelang.grouplives.client;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.text.TextFormatting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Team teleport menu: lists online group members with avatars, their HP and
 * a themed request row; clicking a member sends them a /tpa request.
 */
public class GuiTeamTeleport extends GuiScreen {

    private static final int ROW_BASE = 400;
    private static final int ID_BACK = 0;

    private final GuiScreen parentScreen;
    private final List<String> mates = new ArrayList<String>();
    private String hint;
    private long hintUntil;

    public GuiTeamTeleport(GuiScreen parentScreen) {
        this.parentScreen = parentScreen;
    }

    private static int colorOf(ScorePlayerTeam team) {
        TextFormatting format = team.getColor();
        int index = format != null ? format.getColorIndex() : -1;
        int[] rgb = {
                0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
                0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF,
        };
        return 0xFF000000 | (index >= 0 && index < rgb.length ? rgb[index] : 0xFFFFFF);
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        this.mates.clear();
        this.buttonList.add(new ThemeButton(ID_BACK, this.width / 2 - 60, this.height - 26, 120, 16, "Назад"));

        if (this.mc.player == null || this.mc.world == null || this.mc.getConnection() == null) {
            return;
        }
        String self = this.mc.player.getName();
        Scoreboard sb = this.mc.world.getScoreboard();
        ScorePlayerTeam myTeam = sb.getPlayersTeam(self);
        if (myTeam == null) {
            return;
        }
        List<String> members = new ArrayList<String>(myTeam.getMembershipCollection());
        members.sort(String::compareTo);
        int y = 72;
        for (String member : members) {
            if (member.equals(self)) {
                continue;
            }
            if (this.mc.getConnection().getPlayerInfo(member) == null) {
                continue; // offline
            }
            if (y + 26 > this.height - 40) {
                break;
            }
            this.mates.add(member);
            PanelButton row = new PanelButton(ROW_BASE + this.mates.size() - 1,
                    this.width / 2 - 140, y, 280, 28, member, colorOf(myTeam));
            this.buttonList.add(row);
            y += 32;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        Gui.drawRect(0, 0, this.width, this.height, 0xFF000000);
        UiTheme.scanlines(0, 0, this.width, this.height);

        int px = this.width / 2 - 150;
        int pw = 300;
        int ph = this.height - 30 - 36;
        UiTheme.panelHazard(px, 30, pw, ph, UiTheme.YELLOW);
        UiTheme.trefoil(px + 12, 42);

        String title = "КОМАНДА";
        int cx = this.width / 2;
        drawCenteredString(this.fontRenderer, title, cx, 40, UiTheme.YELLOW);
        drawCenteredString(this.fontRenderer, "телепорт к сокомандникам", cx, 52, UiTheme.TEXT_DIM);

        if (mates.isEmpty()) {
            drawCenteredString(this.fontRenderer, noTeamReason(), cx, this.height / 2, UiTheme.TEXT_DIM);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);

        // Avatars and HP bars on top of the rows.
        Scoreboard sb = this.mc.world != null ? this.mc.world.getScoreboard() : null;
        ScorePlayerTeam myTeam = this.mc.player != null && sb != null
                ? sb.getPlayersTeam(this.mc.player.getName()) : null;
        for (int i = 0; i < mates.size(); i++) {
            int rowY = 72 + i * 32;
            NetworkPlayerInfo info = this.mc.getConnection() != null
                    ? this.mc.getConnection().getPlayerInfo(mates.get(i)) : null;
            if (info != null) {
                this.mc.getTextureManager().bindTexture(info.getLocationSkin());
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                Gui.drawScaledCustomSizeModalRect(px + 14, rowY + 4, 8, 8, 8, 8, 20, 20, 64, 64);
                GlStateManager.enableBlend();
                Gui.drawScaledCustomSizeModalRect(px + 14, rowY + 4, 40, 8, 8, 8, 20, 20, 64, 64);
                GlStateManager.disableBlend();
            }
            EntityPlayer entity = TaskHud.findPlayer(this.mc, mates.get(i));
            if (entity != null) {
                float max = entity.getMaxHealth();
                float frac = max > 0 ? Math.min(1.0F, Math.max(0.0F, entity.getHealth() / max)) : 0.0F;
                int bx = px + pw - 90;
                Gui.drawRect(bx, rowY + 12, bx + 70, rowY + 16, 0xFF3B3344);
                int fill = (int) (70 * frac);
                if (fill > 0) {
                    Gui.drawRect(bx, rowY + 12, bx + fill, rowY + 16, TaskHud.hpColor(frac));
                }
                String pct = (int) (frac * 100) + "%";
                this.fontRenderer.drawStringWithShadow(pct, bx + 74, rowY + 9, UiTheme.TEXT_DIM);
            }
            if (myTeam != null) {
                String tag = "[" + myTeam.getName() + "]";
                this.fontRenderer.drawStringWithShadow(tag, px + pw - 12 - this.fontRenderer.getStringWidth(tag),
                        rowY + 3, colorOf(myTeam));
            }
        }

        if (hint != null && System.currentTimeMillis() < hintUntil) {
            drawCenteredString(this.fontRenderer, hint, cx, this.height - 44, UiTheme.GREEN);
        }
    }

    private String noTeamReason() {
        if (this.mc.player == null || this.mc.world == null) {
            return "Загрузка...";
        }
        Scoreboard sb = this.mc.world.getScoreboard();
        if (sb.getPlayersTeam(this.mc.player.getName()) == null) {
            return "Вы не состоите в команде - выберите её в лобби (L).";
        }
        return "Сокомандники сейчас не в сети.";
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id >= ROW_BASE && button.id - ROW_BASE < mates.size()) {
            String name = mates.get(button.id - ROW_BASE);
            if (this.mc.player != null) {
                this.mc.player.sendChatMessage("/tpa " + name);
            }
            hint = "Запрос отправлен: " + name;
            hintUntil = System.currentTimeMillis() + 3000;
            return;
        }
        if (button.id == ID_BACK) {
            this.mc.displayGuiScreen(this.parentScreen);
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

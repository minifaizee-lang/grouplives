package io.github.minifaizeelang.grouplives.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.text.TextFormatting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Team teleport menu: lists online group members with avatars; clicking a
 * member sends them a /tpa request - no typing required.
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

    @Override
    public void initGui() {
        this.buttonList.clear();
        this.mates.clear();
        this.buttonList.add(new GuiButton(ID_BACK, this.width / 2 - 60, this.height - 24, 120, 16, "Назад"));

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
        int y = 44;
        for (String member : members) {
            if (member.equals(self)) {
                continue;
            }
            if (this.mc.getConnection().getPlayerInfo(member) == null) {
                continue; // offline
            }
            if (y + 20 > this.height - 30) {
                break;
            }
            this.mates.add(member);
            PanelButton row = new PanelButton(ROW_BASE + this.mates.size() - 1, this.width / 2 - 110, y, 220, 20,
                    member, colorOf(myTeam));
            row.description = "нажмите - запрос телепорта (/tpa)";
            this.buttonList.add(row);
            y += 24;
        }
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
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawGradientRect(0, 0, this.width, this.height, 0xFF231826, 0xFF05040A);
        String title = "КОМАНДА";
        int cx = this.width / 2;
        drawCenteredString(this.fontRenderer, title, cx - 1, 14, 0xFFFF4D4D);
        drawCenteredString(this.fontRenderer, title, cx + 1, 14, 0xFF4DFFFF);
        drawCenteredString(this.fontRenderer, title, cx, 14, 0xFFF5F2F7);
        drawCenteredString(this.fontRenderer, "телепорт к сокомандникам", cx, 26, 0xFF8A7F96);

        if (mates.isEmpty()) {
            String message = noTeamReason();
            drawCenteredString(this.fontRenderer, message, cx, this.height / 2 - 10, 0xFF9A8FA8);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);

        // Avatars on top of the rows.
        for (int i = 0; i < mates.size(); i++) {
            NetworkPlayerInfo info = this.mc.getConnection() != null
                    ? this.mc.getConnection().getPlayerInfo(mates.get(i)) : null;
            if (info != null) {
                int y = 44 + i * 24;
                this.mc.getTextureManager().bindTexture(info.getLocationSkin());
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                net.minecraft.client.gui.Gui.drawScaledCustomSizeModalRect(this.width / 2 - 107, y + 3, 8, 8, 8, 8, 14, 14, 64, 64);
                GlStateManager.enableBlend();
                net.minecraft.client.gui.Gui.drawScaledCustomSizeModalRect(this.width / 2 - 107, y + 3, 40, 8, 8, 8, 14, 14, 64, 64);
                GlStateManager.disableBlend();
            }
        }

        if (hint != null && System.currentTimeMillis() < hintUntil) {
            drawCenteredString(this.fontRenderer, hint, cx, this.height - 40, 0xFF7CC24A);
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

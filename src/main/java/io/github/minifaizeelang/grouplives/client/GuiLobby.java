package io.github.minifaizeelang.grouplives.client;

import io.github.minifaizeelang.grouplives.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.text.TextFormatting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lobby screen: live list of players (avatar + nickname + team tag) on the
 * left, clickable team rows on the right, join/leave/create-team controls,
 * and - for the host only - event settings (border, spacing, start).
 *
 * Everything is rendered from vanilla-synced data (tab list + scoreboard) and
 * applied by sending the regular /group and /event commands, so the server
 * keeps full permission control.
 */
public class GuiLobby extends GuiScreen {

    private static final int ID_BACK = 0;
    private static final int ID_LEAVE = 1;
    private static final int ID_CREATE = 2;
    private static final int ID_CREATE_CONFIRM = 3;
    private static final int ID_CREATE_CANCEL = 4;
    private static final int ID_COLOR_CYCLE = 5;
    private static final int ID_BORDER_PANEL = 10;
    private static final int ID_SPACING_PANEL = 11;
    private static final int ID_START_PANEL = 12;
    private static final int ID_BORDER_STEP = 20; // 20..21 = -1000/+1000
    private static final int ID_SPACING_STEP = 24; // 24..25 = -100/+100
    private static final int TEAM_ROW_BASE_ID = 100;

    /** (friendly color name for the command, Russian label) pairs offered at team creation. */
    private static final String[][] COLORS = {
            {"red", "красный"}, {"gold", "золотой"}, {"yellow", "жёлтый"},
            {"green", "зелёный"}, {"aqua", "голубой"}, {"blue", "синий"},
            {"light_purple", "розовый"}, {"dark_purple", "фиолетовый"},
            {"dark_red", "тёмно-красный"}, {"dark_aqua", "бирюзовый"},
            {"dark_blue", "тёмно-синий"}, {"gray", "серый"},
    };

    /** Values shared with the host settings row; persist for the session. */
    public static int borderSize = ModConfig.eventBorderSize;
    public static int teamSpacing = ModConfig.teamSpacing;

    private static final long CONFIRM_WINDOW_MS = 5000;

    private final GuiScreen parentScreen;
    private final Map<Integer, String> teamByButton = new HashMap<Integer, String>();

    private GuiTextField nameField;
    private boolean createMode;
    private int colorIndex;
    private long startArmedUntil;
    private String lastStateKey = "";

    public GuiLobby(GuiScreen parentScreen) {
        this.parentScreen = parentScreen;
    }

    private boolean isHost() {
        return this.mc != null && this.mc.isSingleplayer();
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    @Override
    public void initGui() {
        this.buttonList.clear();
        this.teamByButton.clear();
        this.nameField = null;

        int half = this.width / 2;
        int bottomY = this.height - 26;

        // Bottom controls: leave / create (create hidden for the host while typing a new name).
        if (!createMode) {
            this.buttonList.add(new GuiButton(ID_LEAVE, half - 152, bottomY, 146, 20, "Покинуть команду"));
            if (isHost()) {
                this.buttonList.add(new GuiButton(ID_CREATE, half + 6, bottomY, 146, 20, "Создать команду"));
            }
            this.buttonList.add(new GuiButton(ID_BACK, half - 50, bottomY + 24, 100, 16, "Назад"));
        } else {
            this.nameField = new GuiTextField(0, this.fontRenderer, half - 150, bottomY, 120, 20);
            this.nameField.setMaxStringLength(16);
            this.nameField.setFocused(true);
            this.buttonList.add(new GuiButton(ID_COLOR_CYCLE, half - 24, bottomY, 110, 20, "Цвет: " + COLORS[colorIndex][1]));
            this.buttonList.add(new GuiButton(ID_CREATE_CONFIRM, half + 90, bottomY, 60, 20, "Создать"));
            this.buttonList.add(new GuiButton(ID_CREATE_CANCEL, half - 152, bottomY + 24, 302, 16, "Отмена"));
        }

        // Host event settings row (above the bottom controls).
        if (isHost() && !createMode) {
            int y = bottomY - 48;
            drawHostRowLater(y);
        }

        buildTeamRows();
    }

    private void drawHostRowLater(int y) {
        int half = this.width / 2;
        // Border: label via panel, steps via small buttons.
        PanelButton border = new PanelButton(ID_BORDER_PANEL, half - 200, y, 120, 20, "ГРАНИЦА", 0xFFE8B33C);
        border.value = String.valueOf(borderSize);
        this.buttonList.add(border);
        this.buttonList.add(new GuiButton(ID_BORDER_STEP, half - 74, y + 2, 34, 16, "-1к"));
        this.buttonList.add(new GuiButton(ID_BORDER_STEP + 1, half - 36, y + 2, 34, 16, "+1к"));

        PanelButton spacing = new PanelButton(ID_SPACING_PANEL, half + 4, y, 120, 20, "ДИСТАНЦИЯ", 0xFF5B8FFB);
        spacing.value = String.valueOf(teamSpacing);
        this.buttonList.add(spacing);
        this.buttonList.add(new GuiButton(ID_SPACING_STEP, half + 130, y + 2, 34, 16, "-100"));
        this.buttonList.add(new GuiButton(ID_SPACING_STEP + 1, half + 168, y + 2, 34, 16, "+100"));

        PanelButton start = new PanelButton(ID_START_PANEL, half + 210, y, 90, 20, "СТАРТ", 0xFFD0483C);
        this.buttonList.add(start);
        refreshStartDescription();
    }

    private void buildTeamRows() {
        if (this.mc.world == null) {
            return;
        }
        Scoreboard sb = this.mc.world.getScoreboard();
        List<ScorePlayerTeam> teams = new ArrayList<ScorePlayerTeam>(sb.getTeams());
        teams.sort(Comparator.comparing(ScorePlayerTeam::getName));

        int x = this.width / 2 + 16;
        int w = this.width / 2 - 32;
        int y = 44;
        int maxY = this.height - (isHost() || createMode ? 84 : 56);
        for (ScorePlayerTeam team : teams) {
            if (y + 34 > maxY) {
                break;
            }
            PanelButton row = new PanelButton(TEAM_ROW_BASE_ID + this.teamByButton.size(),
                    x, y, w, 34, team.getName(), colorOf(team));
            List<String> members = new ArrayList<String>(team.getMembershipCollection());
            members.sort(String::compareTo);
            row.value = members.size() + " чел.";
            row.description = truncate(String.join(", ", members), w - 26);
            this.teamByButton.put(row.id, team.getName());
            this.buttonList.add(row);
            y += 38;
        }
    }

    private String truncate(String text, int maxWidth) {
        if (this.fontRenderer.getStringWidth(text) <= maxWidth) {
            return text;
        }
        while (text.length() > 1 && this.fontRenderer.getStringWidth(text + "...") > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "...";
    }

    /** RGB values for TextFormatting.getColorIndex() order (black..white), vanilla chat colors. */
    private static final int[] FORMAT_RGB = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF,
    };

    private static int colorOf(ScorePlayerTeam team) {
        TextFormatting format = team.getColor();
        int index = format != null ? format.getColorIndex() : -1;
        int rgb = index >= 0 && index < FORMAT_RGB.length ? FORMAT_RGB[index] : 0xFFFFFF;
        return 0xFF000000 | rgb;
    }

    private void refreshStartDescription() {
        for (GuiButton button : this.buttonList) {
            if (button instanceof PanelButton && button.id == ID_START_PANEL) {
                ((PanelButton) button).description = System.currentTimeMillis() < this.startArmedUntil
                        ? "ещё раз!"
                        : "";
            }
        }
    }

    // ------------------------------------------------------------------
    // Live refresh
    // ------------------------------------------------------------------

    @Override
    public void updateScreen() {
        if (createMode || this.mc.world == null || this.mc.getConnection() == null) {
            return;
        }
        Scoreboard sb = this.mc.world.getScoreboard();
        StringBuilder key = new StringBuilder();
        for (ScorePlayerTeam team : sb.getTeams()) {
            key.append(team.getName()).append('|')
                    .append(team.getColor()).append('|')
                    .append(team.getMembershipCollection().size()).append(';');
        }
        key.append('#').append(this.mc.getConnection().getPlayerInfoMap().size());
        if (!key.toString().equals(this.lastStateKey)) {
            this.lastStateKey = key.toString();
            initGui();
        }
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawGradientRect(0, 0, this.width, this.height, 0xFF231826, 0xFF05040A);

        String title = "ЛОББИ";
        int cx = this.width / 2;
        drawCenteredString(this.fontRenderer, title, cx - 1, 12, 0xFFFF4D4D);
        drawCenteredString(this.fontRenderer, title, cx + 1, 12, 0xFF4DFFFF);
        drawCenteredString(this.fontRenderer, title, cx, 12, 0xFFF5F2F7);
        drawCenteredString(this.fontRenderer, isHost() ? "хост" : "выбор команды", cx, 24, 0xFF8A7F96);

        drawPlayers();
        this.fontRenderer.drawStringWithShadow("Команды:", this.width / 2 + 16, 32, 0xFF9A8FA8);

        super.drawScreen(mouseX, mouseY, partialTicks);

        if (createMode && this.nameField != null) {
            this.nameField.drawTextBox();
            this.fontRenderer.drawStringWithShadow("Название команды:", this.width / 2 - 150, bottomY() - 12, 0xFF9A8FA8);
        }
    }

    private int bottomY() {
        return this.height - 26;
    }

    private void drawPlayers() {
        if (this.mc.getConnection() == null) {
            return;
        }
        this.fontRenderer.drawStringWithShadow("Игроки:", 24, 32, 0xFF9A8FA8);

        List<NetworkPlayerInfo> infos = new ArrayList<NetworkPlayerInfo>(this.mc.getConnection().getPlayerInfoMap());
        infos.sort(Comparator.comparing(i -> i.getGameProfile().getName()));

        Scoreboard sb = this.mc.world != null ? this.mc.world.getScoreboard() : null;
        int leftW = this.width / 2 - 44;
        int rowH = 22;
        int y = 44;
        int maxRows = Math.max(3, (this.height - 130) / rowH);
        int shown = 0;
        for (NetworkPlayerInfo info : infos) {
            if (shown >= maxRows) {
                break;
            }
            String name = info.getGameProfile().getName();
            drawRect(20, y - 2, 20 + leftW, y + rowH - 4, 0x9014101A);
            drawAvatar(24, y, info, 16);
            String display = name;
            if (this.mc.player != null && name.equals(this.mc.player.getName())) {
                display = name + " (вы)";
            }
            this.fontRenderer.drawStringWithShadow(display, 46, y + 4, 0xFFF5F2F7);
            if (sb != null) {
                ScorePlayerTeam team = sb.getPlayersTeam(name);
                if (team != null) {
                    String tag = "[" + truncate(team.getName(), leftW / 2) + "]";
                    this.fontRenderer.drawStringWithShadow(tag,
                            20 + leftW - 6 - this.fontRenderer.getStringWidth(tag), y + 4, colorOf(team));
                }
            }
            y += rowH;
            shown++;
        }
        if (infos.size() > shown) {
            this.fontRenderer.drawStringWithShadow("и ещё " + (infos.size() - shown) + "...", 24, y, 0xFF8A7F96);
        }
    }

    private void drawAvatar(int x, int y, NetworkPlayerInfo info, int size) {
        this.mc.getTextureManager().bindTexture(info.getLocationSkin());
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        Gui.drawScaledCustomSizeModalRect(x, y, 8, 8, 8, 8, size, size, 64, 64);
        GlStateManager.enableBlend();
        Gui.drawScaledCustomSizeModalRect(x, y, 40, 8, 8, 8, size, size, 64, 64);
        GlStateManager.disableBlend();
    }

    // ------------------------------------------------------------------
    // Interaction
    // ------------------------------------------------------------------

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id >= TEAM_ROW_BASE_ID && this.teamByButton.containsKey(button.id)) {
            sendCommand("/group join " + this.teamByButton.get(button.id));
            return;
        }
        switch (button.id) {
            case ID_BACK:
                this.mc.displayGuiScreen(this.parentScreen);
                return;
            case ID_LEAVE:
                sendCommand("/group leave");
                return;
            case ID_CREATE:
                this.createMode = true;
                initGui();
                return;
            case ID_CREATE_CANCEL:
                this.createMode = false;
                initGui();
                return;
            case ID_COLOR_CYCLE:
                this.colorIndex = (this.colorIndex + 1) % COLORS.length;
                initGui();
                return;
            case ID_CREATE_CONFIRM: {
                String name = this.nameField != null ? this.nameField.getText().trim() : "";
                if (!name.isEmpty()) {
                    sendCommand("/group create " + name + " " + COLORS[colorIndex][0]);
                    this.createMode = false;
                }
                initGui();
                return;
            }
            case ID_BORDER_STEP:
                borderSize = clamp(borderSize - 1000, 1000, 600000);
                initGui();
                return;
            case ID_BORDER_STEP + 1:
                borderSize = clamp(borderSize + 1000, 1000, 600000);
                initGui();
                return;
            case ID_SPACING_STEP:
                teamSpacing = clamp(teamSpacing - 100, 0, 100000);
                initGui();
                return;
            case ID_SPACING_STEP + 1:
                teamSpacing = clamp(teamSpacing + 100, 0, 100000);
                initGui();
                return;
            case ID_BORDER_PANEL:
                sendCommand("/event border " + borderSize);
                return;
            case ID_START_PANEL:
                if (System.currentTimeMillis() < this.startArmedUntil) {
                    this.startArmedUntil = 0;
                    sendCommand("/event start " + borderSize + " " + teamSpacing);
                    this.mc.displayGuiScreen(null);
                } else {
                    this.startArmedUntil = System.currentTimeMillis() + CONFIRM_WINDOW_MS;
                }
                refreshStartDescription();
                return;
            default:
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
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (createMode && this.nameField != null) {
            this.nameField.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (createMode && this.nameField != null && this.nameField.isFocused()) {
            if (keyCode == 1) { // Esc leaves the field first
                this.nameField.setFocused(false);
                return;
            }
            if (keyCode == 28 && !this.nameField.getText().trim().isEmpty()) { // Enter creates
                actionPerformedById(ID_CREATE_CONFIRM);
                return;
            }
            this.nameField.textboxKeyTyped(typedChar, keyCode);
            return;
        }
        if (keyCode == 1) {
            if (createMode) {
                this.createMode = false;
                initGui();
            } else {
                this.mc.displayGuiScreen(this.parentScreen);
            }
        } else {
            super.keyTyped(typedChar, keyCode);
        }
    }

    private void actionPerformedById(int id) {
        for (GuiButton button : this.buttonList) {
            if (button.id == id && button.enabled) {
                try {
                    actionPerformed(button);
                } catch (IOException ignored) {
                }
                return;
            }
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }
}

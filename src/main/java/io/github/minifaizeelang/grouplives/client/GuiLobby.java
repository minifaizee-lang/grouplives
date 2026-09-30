package io.github.minifaizeelang.grouplives.client;

import io.github.minifaizeelang.grouplives.ModConfig;
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
 * World Lobby: pre-game screen styled after the reference image. Banner with
 * the lobby status, team cards (avatars, members, join buttons), a waiting
 * room, and - for the host - world settings (border toggle/size, spacing
 * slider, START). Everything acts through regular /group and /event commands.
 */
public class GuiLobby extends GuiScreen {

    private static final int ID_BACK = 0;
    private static final int ID_BORDER_TOGGLE = 30;
    private static final int ID_BORDER_MINUS = 31;
    private static final int ID_BORDER_PLUS = 32;
    private static final int ID_BORDER_PANEL = 10;
    private static final int ID_START_PANEL = 12;
    private static final int ID_CREATE = 2;
    private static final int ID_CREATE_CONFIRM = 3;
    private static final int ID_CREATE_CANCEL = 4;
    private static final int ID_COLOR_CYCLE = 5;
    private static final int TEAM_JOIN_BASE = 200;
    private static final int WAITING_BASE = 300;

    private static final String[][] COLORS = {
            {"red", "красный"}, {"gold", "золотой"}, {"yellow", "жёлтый"},
            {"green", "зелёный"}, {"aqua", "голубой"}, {"blue", "синий"},
            {"light_purple", "розовый"}, {"dark_purple", "фиолетовый"},
            {"dark_red", "тёмно-красный"}, {"dark_aqua", "бирюзовый"},
            {"dark_blue", "тёмно-синий"}, {"gray", "серый"},
    };

    private static final int[] FORMAT_RGB = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF,
    };

    private static final long CONFIRM_WINDOW_MS = 5000;
    private static final int SPACING_MIN = 100;
    private static final int SPACING_MAX = 2000;

    /** Host's spacing value; lives for the session, applied by /event start. */
    public static int teamSpacing = ModConfig.teamSpacing;
    /** Host's border size; lives for the session, applied by /event start or the size panel. */
    public static int borderSize = ModConfig.eventBorderSize;

    private final GuiScreen parentScreen;
    private final List<ScorePlayerTeam> visibleTeams = new ArrayList<ScorePlayerTeam>();
    private final Map<Integer, ScorePlayerTeam> joinTargets = new HashMap<Integer, ScorePlayerTeam>();
    private final Map<Integer, String> waitingByButton = new HashMap<Integer, String>();

    private GuiTextField nameField;
    private boolean createMode;
    private int colorIndex;
    private long startArmedUntil;
    private String lastStateKey = "";
    private String selectedWaiting;
    private boolean sliderDragging;

    public GuiLobby(GuiScreen parentScreen) {
        this.parentScreen = parentScreen;
    }

    private boolean isHost() {
        return this.mc != null && this.mc.isSingleplayer();
    }

    private int leftX() {
        return 10;
    }

    private int leftW() {
        return this.width / 2 - 18;
    }

    private int rightX() {
        return this.width / 2 + 8;
    }

    private int rightW() {
        return this.width - this.width / 2 - 8 - 8;
    }

    private int cardsY() {
        return 64;
    }

    private int cardH() {
        return 54;
    }

    private int waitingY() {
        return this.height - 62;
    }

    private int panelY() {
        return 64;
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    @Override
    public void initGui() {
        this.buttonList.clear();
        this.joinTargets.clear();
        this.waitingByButton.clear();
        this.visibleTeams.clear();
        this.nameField = null;

        if (createMode) {
            this.nameField = new GuiTextField(0, this.fontRenderer, 10, this.height - 46, 120, 18);
            this.nameField.setMaxStringLength(16);
            this.nameField.setFocused(true);
            this.buttonList.add(new GuiButton(ID_COLOR_CYCLE, 134, this.height - 46, 110, 18, "Цвет: " + COLORS[colorIndex][1]));
            this.buttonList.add(new GuiButton(ID_CREATE_CONFIRM, 248, this.height - 46, 60, 18, "Создать"));
            this.buttonList.add(new GuiButton(ID_CREATE_CANCEL, 10, this.height - 24, 200, 16, "Отмена"));
            this.buttonList.add(new GuiButton(ID_BACK, this.width - 70, this.height - 24, 60, 16, "Назад"));
            return;
        }

        Scoreboard sb = this.mc.world != null ? this.mc.world.getScoreboard() : null;
        List<ScorePlayerTeam> teams = new ArrayList<ScorePlayerTeam>();
        if (sb != null) {
            teams.addAll(sb.getTeams());
            teams.sort(Comparator.comparing(ScorePlayerTeam::getName));
        }
        int i = 0;
        for (ScorePlayerTeam team : teams) {
            int by = cardsY() + i * (cardH() + 5);
            if (by + cardH() > waitingY() - 4) {
                break;
            }
            visibleTeams.add(team);
            boolean mine = isMyTeam(team);
            String label = mine ? "✓ ВАША КОМАНДА"
                    : (selectedWaiting != null && isHost() ? "+ " + selectedWaiting : "ВСТУПИТЬ →");
            PanelButton join = new PanelButton(TEAM_JOIN_BASE + i, leftX() + 4, by + cardH() - 18,
                    leftW() - 8, 14, label, colorOf(team));
            joinTargets.put(join.id, team);
            this.buttonList.add(join);
            i++;
        }

        if (isHost()) {
            List<String> waiting = ungroupedNames();
            int wx = leftX() + 4;
            int count = Math.min(3, waiting.size());
            int w = count > 0 ? Math.min(120, (leftW() - 8) / count - 4) : 0;
            for (int j = 0; j < count; j++) {
                GuiButton b = new GuiButton(WAITING_BASE + j, wx, waitingY() + 12, w, 12, waiting.get(j));
                waitingByButton.put(b.id, waiting.get(j));
                this.buttonList.add(b);
                wx += w + 4;
            }

            int px = rightX();
            int pw = rightW();
            PanelButton toggle = new PanelButton(ID_BORDER_TOGGLE, px + pw - 58, panelY() + 14, 54, 14,
                    ClientState.borderEnabled ? "ВКЛ" : "ВЫКЛ",
                    ClientState.borderEnabled ? 0xFF7CC24A : 0xFFD0483C);
            this.buttonList.add(toggle);
            PanelButton sizePanel = new PanelButton(ID_BORDER_PANEL, px + 4, panelY() + 32, pw - 8, 16,
                    "РАЗМЕР", 0xFFE8B33C);
            sizePanel.value = borderSize + " бл.";
            this.buttonList.add(sizePanel);
            this.buttonList.add(new GuiButton(ID_BORDER_MINUS, px + 4, panelY() + 50, 34, 14, "-1к"));
            this.buttonList.add(new GuiButton(ID_BORDER_PLUS, px + 42, panelY() + 50, 34, 14, "+1к"));
            PanelButton start = new PanelButton(ID_START_PANEL, px + 4, panelY() + 96, pw - 8, 22,
                    "СТАРТ ИВЕНТА", 0xFFE8B33C);
            start.description = System.currentTimeMillis() < this.startArmedUntil ? "ещё раз!" : "";
            this.buttonList.add(start);
        }

        this.buttonList.add(new GuiButton(ID_BACK, this.width - 70, this.height - 22, 60, 16, "Назад"));
    }

    private boolean isMyTeam(ScorePlayerTeam team) {
        return this.mc.player != null && team.getMembershipCollection().contains(this.mc.player.getName());
    }

    private List<String> ungroupedNames() {
        List<String> out = new ArrayList<String>();
        if (this.mc.getConnection() == null) {
            return out;
        }
        Scoreboard sb = this.mc.world != null ? this.mc.world.getScoreboard() : null;
        if (sb == null) {
            return out;
        }
        List<NetworkPlayerInfo> infos = new ArrayList<NetworkPlayerInfo>(this.mc.getConnection().getPlayerInfoMap());
        infos.sort(Comparator.comparing(info -> info.getGameProfile().getName()));
        for (NetworkPlayerInfo info : infos) {
            String name = info.getGameProfile().getName();
            if (sb.getPlayersTeam(name) == null) {
                out.add(name);
            }
        }
        return out;
    }

    private static int colorOf(ScorePlayerTeam team) {
        TextFormatting format = team.getColor();
        int index = format != null ? format.getColorIndex() : -1;
        int rgb = index >= 0 && index < FORMAT_RGB.length ? FORMAT_RGB[index] : 0xFFFFFF;
        return 0xFF000000 | rgb;
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
        key.append('#').append(this.mc.getConnection().getPlayerInfoMap().size())
                .append('#').append(selectedWaiting)
                .append('#').append(ClientState.eventStarted)
                .append('#').append(ClientState.borderEnabled);
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
        int cx = this.width / 2;

        // Banner
        card(8, 6, this.width - 16, 34, 0xFFE8B33C);
        goldIcon(18, 12, 4);
        this.fontRenderer.drawStringWithShadow("ПРЕДСТОЯЩЕЕ СОБЫТИЕ", 48, 11, 0xFFE8B33C);
        this.fontRenderer.drawStringWithShadow("КОМАНДНОЕ ВЫЖИВАНИЕ", 48, 20, 0xFFF5F2F7);
        this.fontRenderer.drawStringWithShadow("Приватный мир · Выживание · Команды", 48, 30, 0xFF9A8FA8);
        int sbx = this.width - 8 - 124;
        drawRect(sbx, 11, sbx + 114, 33, 0xFF0E0B12);
        drawRect(sbx, 11, sbx + 1, 33, 0xFF3B3344);
        drawRect(sbx + 113, 11, sbx + 114, 33, 0xFF3B3344);
        boolean started = ClientState.eventStarted;
        drawRect(sbx + 6, 17, sbx + 10, 21, started ? 0xFFD0483C : 0xFFE8B33C);
        this.fontRenderer.drawStringWithShadow(started ? "ИВЕНТ ИДЁТ" : "ЛОББИ ОТКРЫТ", sbx + 14, 14, 0xFFF5F2F7);
        this.fontRenderer.drawStringWithShadow(started ? "граница активна" : "ожидание хоста", sbx + 14, 23, 0xFF8A7F96);

        // Section headers
        this.fontRenderer.drawStringWithShadow("01 / ИГРОКИ", leftX(), 45, 0xFFE8B33C);
        this.fontRenderer.drawStringWithShadow("ВЫБЕРИТЕ КОМАНДУ", leftX(), 53, 0xFFF5F2F7);
        List<String> waiting = ungroupedNames();
        int totalOnline = this.mc.getConnection() != null ? this.mc.getConnection().getPlayerInfoMap().size() : 0;
        String counter = (totalOnline - waiting.size()) + "/" + totalOnline + " В КОМАНДАХ";
        this.fontRenderer.drawStringWithShadow(counter, leftX() + leftW() - this.fontRenderer.getStringWidth(counter), 53, 0xFF8A7F96);

        // Team cards
        for (int i = 0; i < visibleTeams.size(); i++) {
            ScorePlayerTeam team = visibleTeams.get(i);
            int by = cardsY() + i * (cardH() + 5);
            int accent = colorOf(team);
            card(leftX(), by, leftW(), cardH(), accent);
            this.fontRenderer.drawStringWithShadow(team.getName().toUpperCase(), leftX() + 10, by + 3, accent);
            String count = team.getMembershipCollection().size() + " чел.";
            this.fontRenderer.drawStringWithShadow(count, leftX() + leftW() - 8 - this.fontRenderer.getStringWidth(count), by + 3, 0xFF8A7F96);

            List<String> members = new ArrayList<String>(team.getMembershipCollection());
            members.sort(String::compareTo);
            String line = String.join(", ", members);
            this.fontRenderer.drawStringWithShadow(truncate(line.isEmpty() ? "Пока пусто - вступите первым!" : line,
                    leftW() - 20), leftX() + 10, by + 15, line.isEmpty() ? 0xFF6E6480 : 0xFF9A8FA8);

            // Avatar strip + empty slots
            int ax = leftX() + 10;
            int heads = 0;
            for (String member : members) {
                if (heads >= 5) {
                    break;
                }
                NetworkPlayerInfo info = this.mc.getConnection() != null
                        ? this.mc.getConnection().getPlayerInfo(member) : null;
                if (info != null) {
                    drawAvatar(ax, by + 26, info, 9);
                    ax += 11;
                    heads++;
                }
            }
            int slots = Math.max(0, Math.min(4 - heads, 3));
            for (int s = 0; s < slots; s++) {
                drawDashedSlot(ax + s * 11, by + 26, 9);
            }
        }

        // Waiting room
        card(leftX(), waitingY(), leftW(), 26, 0xFF8A7F96);
        this.fontRenderer.drawStringWithShadow("ОЖИДАЮТ КОМАНДЫ", leftX() + 10, waitingY() + 3, 0xFFF5F2F7);
        if (waiting.isEmpty()) {
            this.fontRenderer.drawStringWithShadow("все игроки распределены", leftX() + 10, waitingY() + 14, 0xFF6E6480);
        } else if (!isHost()) {
            this.fontRenderer.drawStringWithShadow(truncate(String.join(", ", waiting), leftW() - 20),
                    leftX() + 10, waitingY() + 14, 0xFF9A8FA8);
        }

        // Right column
        if (isHost()) {
            card(rightX(), panelY(), rightW(), 118, 0xFFD0483C);
            this.fontRenderer.drawStringWithShadow("НАСТРОЙКИ МИРА", rightX() + 10, panelY() + 3, 0xFFE8B33C);
            this.fontRenderer.drawStringWithShadow("ГРАНИЦА МИРА", rightX() + 10, panelY() + 17, 0xFF9A8FA8);
            this.fontRenderer.drawStringWithShadow("ДИСТАНЦИЯ", rightX() + 10, panelY() + 68, 0xFF9A8FA8);
            String spacingText = teamSpacing + " бл.";
            this.fontRenderer.drawStringWithShadow(spacingText,
                    rightX() + rightW() - 8 - this.fontRenderer.getStringWidth(spacingText), panelY() + 68, 0xFFF5F2F7);
            drawSlider(rightX() + 10, panelY() + 82, rightW() - 20);
            this.fontRenderer.drawStringWithShadow("СТАРТ применит границу и разбросает команды",
                    rightX() + 10, panelY() + 106, 0xFF6E6480);
        } else {
            card(rightX(), panelY(), rightW(), 118, 0xFF5B8FFB);
            this.fontRenderer.drawStringWithShadow("НАСТРОЙКИ МИРА", rightX() + 10, panelY() + 3, 0xFF5B8FFB);
            this.fontRenderer.drawStringWithShadow("Доступно только хосту мира.", rightX() + 10, panelY() + 17, 0xFF9A8FA8);
            this.fontRenderer.drawStringWithShadow("Выберите команду слева и ждите старта.",
                    rightX() + 10, panelY() + 27, 0xFF9A8FA8);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);

        // Avatars drawn on top of the card backgrounds.
        for (int i = 0; i < visibleTeams.size(); i++) {
            ScorePlayerTeam team = visibleTeams.get(i);
            int by = cardsY() + i * (cardH() + 5);
            int ax = leftX() + 10;
            int heads = 0;
            for (String member : team.getMembershipCollection()) {
                if (heads >= 5) {
                    break;
                }
                NetworkPlayerInfo info = this.mc.getConnection() != null
                        ? this.mc.getConnection().getPlayerInfo(member) : null;
                if (info != null) {
                    drawAvatar(ax, by + 26, info, 9);
                    ax += 11;
                    heads++;
                }
            }
        }

        if (createMode && this.nameField != null) {
            this.nameField.drawTextBox();
            this.fontRenderer.drawStringWithShadow("Название команды:", 10, this.height - 56, 0xFF9A8FA8);
        }
    }

    private void card(int x, int y, int w, int h, int accent) {
        drawRect(x, y, x + w, y + h, 0xFF17121C);
        drawRect(x, y, x + w, y + 1, 0xFF3B3344);
        drawRect(x, y + h - 1, x + w, y + h, 0xFF3B3344);
        drawRect(x, y, x + 1, y + h, 0xFF3B3344);
        drawRect(x + w - 1, y, x + w, y + h, 0xFF3B3344);
        drawRect(x + 3, y + 3, x + 6, y + h - 3, accent);
    }

    private void goldIcon(int x, int y, int s) {
        drawRect(x + s, y, x + 2 * s, y + s, 0xFFE8B33C);
        drawRect(x, y + s, x + 3 * s, y + 2 * s, 0xFFE8B33C);
        drawRect(x + s, y + 2 * s, x + 2 * s, y + 3 * s, 0xFFE8B33C);
        drawRect(x + s, y + s, x + 2 * s, y + 2 * s, 0xFFF7D27C);
    }

    private void drawDashedSlot(int x, int y, int size) {
        drawRect(x, y, x + size, y + 1, 0xFF3B3344);
        drawRect(x, y + size - 1, x + size, y + size, 0xFF3B3344);
        drawRect(x, y, x + 1, y + size, 0xFF3B3344);
        drawRect(x + size - 1, y, x + size, y + size, 0xFF3B3344);
        drawRect(x + size / 2 - 1, y + size / 2 - 1, x + size / 2 + 1, y + size / 2 + 1, 0xFF3B3344);
    }

    private void drawSlider(int x, int y, int w) {
        double fraction = (teamSpacing - SPACING_MIN) / (double) (SPACING_MAX - SPACING_MIN);
        fraction = Math.max(0.0, Math.min(1.0, fraction));
        int knobX = x + (int) (fraction * (w - 4)) + 2;
        drawRect(x, y, x + w, y + 3, 0xFF3B3344);
        drawRect(x, y, knobX, y + 3, 0xFFE8B33C);
        drawRect(knobX - 2, y - 3, knobX + 2, y + 7, 0xFFF5F2F7);
    }

    private boolean inSlider(int mouseX, int mouseY) {
        int x = rightX() + 10;
        int y = panelY() + 82;
        return isHost() && mouseX >= x - 3 && mouseX <= x + rightW() - 20 + 3 && mouseY >= y - 5 && mouseY <= y + 10;
    }

    private void updateSpacing(int mouseX) {
        int x = rightX() + 10;
        int w = rightW() - 20;
        double fraction = (mouseX - x) / (double) w;
        fraction = Math.max(0.0, Math.min(1.0, fraction));
        teamSpacing = SPACING_MIN + (int) Math.round(fraction * ((SPACING_MAX - SPACING_MIN) / 50.0)) * 50;
    }

    private void drawAvatar(int x, int y, NetworkPlayerInfo info, int size) {
        this.mc.getTextureManager().bindTexture(info.getLocationSkin());
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        Gui.drawScaledCustomSizeModalRect(x, y, 8, 8, 8, 8, size, size, 64, 64);
        GlStateManager.enableBlend();
        Gui.drawScaledCustomSizeModalRect(x, y, 40, 8, 8, 8, size, size, 64, 64);
        GlStateManager.disableBlend();
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

    // ------------------------------------------------------------------
    // Interaction
    // ------------------------------------------------------------------

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id >= TEAM_JOIN_BASE && joinTargets.containsKey(button.id)) {
            ScorePlayerTeam team = joinTargets.get(button.id);
            if (selectedWaiting != null && isHost()) {
                sendCommand("/group add " + selectedWaiting + " " + team.getName());
                selectedWaiting = null;
                initGui();
            } else {
                sendCommand("/group join " + team.getName());
            }
            return;
        }
        if (button.id >= WAITING_BASE && waitingByButton.containsKey(button.id)) {
            String name = waitingByButton.get(button.id);
            selectedWaiting = name.equals(selectedWaiting) ? null : name;
            initGui();
            return;
        }
        switch (button.id) {
            case ID_BACK:
                this.mc.displayGuiScreen(this.parentScreen);
                return;
            case ID_BORDER_TOGGLE:
                sendCommand("/event border " + (ClientState.borderEnabled ? "off" : "on"));
                return;
            case ID_BORDER_PANEL:
                sendCommand("/event border " + borderSize);
                return;
            case ID_BORDER_MINUS:
                borderSize = Math.max(1000, borderSize - 1000);
                initGui();
                return;
            case ID_BORDER_PLUS:
                borderSize = Math.min(600000, borderSize + 1000);
                initGui();
                return;
            case ID_START_PANEL:
                if (System.currentTimeMillis() < this.startArmedUntil) {
                    this.startArmedUntil = 0;
                    sendCommand("/event start " + borderSize + " " + teamSpacing);
                    this.mc.displayGuiScreen(null);
                } else {
                    this.startArmedUntil = System.currentTimeMillis() + CONFIRM_WINDOW_MS;
                }
                initGui();
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
            default:
        }
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
        if (!createMode && mouseButton == 0 && inSlider(mouseX, mouseY)) {
            this.sliderDragging = true;
            updateSpacing(mouseX);
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (this.sliderDragging && clickedMouseButton == 0) {
            updateSpacing(mouseX);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        this.sliderDragging = false;
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (createMode && this.nameField != null && this.nameField.isFocused()) {
            if (keyCode == 1) {
                this.nameField.setFocused(false);
                return;
            }
            if (keyCode == 28 && !this.nameField.getText().trim().isEmpty()) {
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

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
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * World Lobby: pre-game screen styled after the reference image. Banner with
 * the lobby status, team cards (member names, join buttons), a waiting room,
 * and - for the host - world settings (border toggle, border-size slider,
 * spacing slider, START). Everything acts through regular /group and /event
 * commands.
 */
public class GuiLobby extends GuiScreen {

    private static final int ID_BACK = 0;
    private static final int ID_BORDER_TOGGLE = 30;
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

    private static final int BORDER_MIN = 1000;
    private static final int BORDER_MAX = 30000;
    private static final int BORDER_STEP = 500;
    private static final int SPACING_MIN = 100;
    private static final int SPACING_MAX = 2000;
    private static final int SPACING_STEP = 50;

    /** Host's values; live for the session and applied by /event start or the size panel. */
    public static int teamSpacing = ModConfig.teamSpacing;
    public static int borderSize = ModConfig.eventBorderSize;

    private final GuiScreen parentScreen;
    private final List<ScorePlayerTeam> visibleTeams = new ArrayList<ScorePlayerTeam>();
    private final Map<Integer, ScorePlayerTeam> joinTargets = new HashMap<Integer, ScorePlayerTeam>();
    private final Map<Integer, String> waitingByButton = new HashMap<Integer, String>();

    private GuiTextField nameField;
    private String draftName = "";
    private boolean createPanelVisible;
    private int createPanelY;
    private int colorIndex;
    private String lastStateKey = "";
    private String selectedWaiting;
    private int activeSlider;
    private PanelButton sizePanel;

    /** RGB color for a TextFormatting-friendly name (e.g. "light_purple"). */
    private static int colorOfFriendly(String friendly) {
        try {
            TextFormatting format = TextFormatting.valueOf(friendly.toUpperCase());
            int index = format.getColorIndex();
            return 0xFF000000 | (index >= 0 && index < FORMAT_RGB.length ? FORMAT_RGB[index] : 0xFFFFFF);
        } catch (IllegalArgumentException e) {
            return 0xFFFFFFFF;
        }
    }

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
        return 48;
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
        this.sizePanel = null;
        this.activeSlider = 0;
        this.createPanelVisible = false;

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
            PanelButton join = new PanelButton(TEAM_JOIN_BASE + i, leftX() + 4, by + cardH() - 17,
                    leftW() - 8, 13, label, colorOf(team));
            joinTargets.put(join.id, team);
            this.buttonList.add(join);
            i++;
        }

        if (isHost()) {
            // Create-team panel fills the gap between the cards and the waiting room
            int createTop = cardsY() + visibleTeams.size() * (cardH() + 5) + 4;
            int createBottom = waitingY() - 6;
            this.createPanelVisible = createBottom - createTop >= 76;
            if (this.createPanelVisible) {
                this.createPanelY = createTop;
                UiTheme.panel(leftX(), createTop, leftW(), createBottom - createTop, UiTheme.YELLOW);
                this.nameField = new GuiTextField(0, this.fontRenderer, leftX() + 12, createTop + 22, leftW() - 24, 14);
                this.nameField.setMaxStringLength(16);
                this.nameField.setText(draftName);
                this.nameField.setEnableBackgroundDrawing(false);
                this.buttonList.add(new ThemeButton(ID_COLOR_CYCLE, leftX() + 12, createTop + 44, 92, 16,
                        "Цвет: " + COLORS[colorIndex][1]));
                this.buttonList.add(new ThemeButton(ID_CREATE_CONFIRM, leftX() + 112, createTop + 44, 76, 16, "Создать"));
            }

            List<String> waiting = ungroupedNames();
            int wx = leftX() + 4;
            int count = Math.min(3, waiting.size());
            int w = count > 0 ? Math.min(120, (leftW() - 8) / count - 4) : 0;
            for (int j = 0; j < count; j++) {
                ThemeButton b = new ThemeButton(WAITING_BASE + j, wx, waitingY() + 12, w, 12, waiting.get(j));
                waitingByButton.put(b.id, waiting.get(j));
                this.buttonList.add(b);
                wx += w + 4;
            }

            int px = rightX();
            int pw = rightW();
            PanelButton toggle = new PanelButton(ID_BORDER_TOGGLE, px + pw - 58, panelY() + 13, 54, 14,
                    ClientState.borderEnabled ? "ВКЛ" : "ВЫКЛ",
                    ClientState.borderEnabled ? 0xFF7CC24A : 0xFFD0483C);
            this.buttonList.add(toggle);
            PanelButton start = new PanelButton(ID_START_PANEL, px + 4, panelY() + 100, pw - 8, 20,
                    "СТАРТ ИВЕНТА", UiTheme.YELLOW);
            this.buttonList.add(start);
        }

        this.buttonList.add(new ThemeButton(ID_BACK, this.width - 70, this.height - 22, 60, 16, "Назад"));
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
        if (this.mc.world == null || this.mc.getConnection() == null) {
            return;
        }
        if (this.nameField != null) {
            draftName = this.nameField.getText();
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
        UiTheme.scanlines(0, 0, this.width, this.height);

        // Banner
        UiTheme.panelHazard(8, 6, this.width - 16, 34, UiTheme.YELLOW);
        UiTheme.trefoil(18, 12);
        this.fontRenderer.drawStringWithShadow("ПРЕДСТОЯЩЕЕ СОБЫТИЕ", 48, 11, UiTheme.YELLOW);
        this.fontRenderer.drawStringWithShadow("КОМАНДНОЕ ВЫЖИВАНИЕ", 48, 20, UiTheme.TEXT);
        this.fontRenderer.drawStringWithShadow("Приватный мир · Выживание · Команды", 48, 30, UiTheme.TEXT_DIM);
        int sbx = this.width - 8 - 124;
        Gui.drawRect(sbx, 11, sbx + 114, 33, 0xFF0E0B12);
        Gui.drawRect(sbx, 11, sbx + 1, 33, 0xFF3B3344);
        Gui.drawRect(sbx + 113, 11, sbx + 114, 33, 0xFF3B3344);
        boolean started = ClientState.eventStarted;
        drawRect(sbx + 6, 17, sbx + 10, 21, started ? UiTheme.RED : UiTheme.YELLOW);
        this.fontRenderer.drawStringWithShadow(started ? "ИВЕНТ ИДЁТ" : "ЛОББИ ОТКРЫТ", sbx + 14, 14, UiTheme.TEXT);
        this.fontRenderer.drawStringWithShadow(started ? "граница активна" : "ожидание хоста", sbx + 14, 23, UiTheme.TEXT_DIM);

        // Section headers
        this.fontRenderer.drawStringWithShadow("01 / ИГРОКИ", leftX(), 45, 0xFFE8B33C);
        this.fontRenderer.drawStringWithShadow("ВЫБЕРИТЕ КОМАНДУ", leftX(), 53, 0xFFF5F2F7);
        List<String> waiting = ungroupedNames();
        int totalOnline = this.mc.getConnection() != null ? this.mc.getConnection().getPlayerInfoMap().size() : 0;
        String counter = (totalOnline - waiting.size()) + "/" + totalOnline + " В КОМАНДАХ";
        this.fontRenderer.drawStringWithShadow(counter, leftX() + leftW() - this.fontRenderer.getStringWidth(counter), 53, 0xFF8A7F96);

        // Team cards (members as text - no player limit implied)
        for (int i = 0; i < visibleTeams.size(); i++) {
            ScorePlayerTeam team = visibleTeams.get(i);
            int by = cardsY() + i * (cardH() + 5);
            int accent = colorOf(team);
            UiTheme.panel(leftX(), by, leftW(), cardH(), accent);
            this.fontRenderer.drawStringWithShadow(team.getName().toUpperCase(), leftX() + 10, by + 3, accent);
            String count = team.getMembershipCollection().size() + " чел.";
            this.fontRenderer.drawStringWithShadow(count, leftX() + leftW() - 8 - this.fontRenderer.getStringWidth(count), by + 3, UiTheme.TEXT_DIM);

            List<String> members = new ArrayList<String>(team.getMembershipCollection());
            members.sort(String::compareTo);
            String line = String.join(", ", members);
            this.fontRenderer.drawStringWithShadow(truncate(line.isEmpty() ? "Пока пусто - вступите первым!" : line,
                    leftW() - 20), leftX() + 10, by + 16, line.isEmpty() ? UiTheme.TEXT_FADED : UiTheme.TEXT_DIM);
        }

        // Create-team panel header and live color preview (host)
        if (isHost() && createPanelVisible) {
            this.fontRenderer.drawStringWithShadow("НОВАЯ КОМАНДА", leftX() + 12, createPanelY + 5, UiTheme.YELLOW);
            UiTheme.trefoil(leftX() + leftW() - 18, createPanelY + 6, 0xFF2E2508);
            String draft = this.nameField != null ? this.nameField.getText().trim() : "";
            if (draft.isEmpty()) {
                this.fontRenderer.drawStringWithShadow("Превью: введите название команды...",
                        leftX() + 12, createPanelY + 64, UiTheme.TEXT_FADED);
            } else {
                TextFormatting format;
                try {
                    format = TextFormatting.valueOf(COLORS[colorIndex][0].toUpperCase());
                } catch (IllegalArgumentException e) {
                    format = TextFormatting.WHITE;
                }
                String nick = this.mc.player != null ? this.mc.player.getName() : "";
                String coloredTag = format.toString()
                        + String.format(ModConfig.groupPrefixFormat, draft)
                        + TextFormatting.RESET;
                this.fontRenderer.drawStringWithShadow("Превью: ", leftX() + 12, createPanelY + 64, UiTheme.TEXT_DIM);
                this.fontRenderer.drawStringWithShadow(coloredTag + nick,
                        leftX() + 12 + this.fontRenderer.getStringWidth("Превью: "), createPanelY + 64, UiTheme.TEXT);
            }
        }

        // Waiting room
        UiTheme.panel(leftX(), waitingY(), leftW(), 26, 0xFF8A7F96);
        this.fontRenderer.drawStringWithShadow("ОЖИДАЮТ КОМАНДЫ", leftX() + 10, waitingY() + 3, UiTheme.TEXT);
        if (waiting.isEmpty()) {
            this.fontRenderer.drawStringWithShadow("все игроки распределены", leftX() + 10, waitingY() + 14, UiTheme.TEXT_FADED);
        } else if (!isHost()) {
            this.fontRenderer.drawStringWithShadow(truncate(String.join(", ", waiting), leftW() - 20),
                    leftX() + 10, waitingY() + 14, UiTheme.TEXT_DIM);
        }

        // Right column
        if (isHost()) {
            UiTheme.panel(rightX(), panelY(), rightW(), 126, UiTheme.YELLOW);
            this.fontRenderer.drawStringWithShadow("НАСТРОЙКИ МИРА", rightX() + 10, panelY() + 3, UiTheme.YELLOW);
            this.fontRenderer.drawStringWithShadow("ГРАНИЦА МИРА", rightX() + 10, panelY() + 16, UiTheme.TEXT_DIM);
            // РАЗМЕР МИРА row - same style as ДИСТАНЦИЯ, own row so the value is never hidden
            int sizeRowY = panelY() + 30;
            Gui.drawRect(rightX() + 4, sizeRowY, rightX() + rightW() - 4, sizeRowY + 14, 0xFF12101A);
            Gui.drawRect(rightX() + 4, sizeRowY, rightX() + 5, sizeRowY + 14, UiTheme.YELLOW);
            this.fontRenderer.drawStringWithShadow("РАЗМЕР МИРА", rightX() + 11, sizeRowY + 3, UiTheme.YELLOW);
            String sizeText = ClientState.borderSize + " бл.";
            this.fontRenderer.drawStringWithShadow(sizeText,
                    rightX() + rightW() - 12 - this.fontRenderer.getStringWidth(sizeText), sizeRowY + 3, UiTheme.TEXT);
            drawSlider(1);
            // ДИСТАНЦИЯ row - same style as the РАЗМЕР row
            int rowY = panelY() + 62;
            Gui.drawRect(rightX() + 4, rowY, rightX() + rightW() - 4, rowY + 16, 0xFF12101A);
            Gui.drawRect(rightX() + 4, rowY, rightX() + 5, rowY + 16, UiTheme.YELLOW);
            this.fontRenderer.drawStringWithShadow("ДИСТАНЦИЯ", rightX() + 11, rowY + 4, UiTheme.YELLOW);
            String spacingText = teamSpacing + " бл.";
            this.fontRenderer.drawStringWithShadow(spacingText,
                    rightX() + rightW() - 12 - this.fontRenderer.getStringWidth(spacingText), rowY + 4, UiTheme.TEXT);
            drawSlider(2);
        } else {
            UiTheme.panel(rightX(), panelY(), rightW(), 126, UiTheme.BLUE);
            this.fontRenderer.drawStringWithShadow("НАСТРОЙКИ МИРА", rightX() + 10, panelY() + 3, UiTheme.BLUE);
            this.fontRenderer.drawStringWithShadow("Доступно только хосту мира.", rightX() + 10, panelY() + 17, UiTheme.TEXT_DIM);
            this.fontRenderer.drawStringWithShadow("Выберите команду слева и ждите старта.",
                    rightX() + 10, panelY() + 27, UiTheme.TEXT_DIM);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);

        if (isHost() && createPanelVisible && this.nameField != null) {
            this.nameField.drawTextBox();
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

    // ------------------------------------------------------------------
    // Sliders (border size and team distance)
    // ------------------------------------------------------------------

    private int sliderX() {
        return rightX() + 10;
    }

    private int sliderW() {
        return rightW() - 20;
    }

    private int sliderY(int which) {
        return which == 1 ? panelY() + 48 : panelY() + 80;
    }

    private boolean inSlider(int which, int mouseX, int mouseY) {
        int y = sliderY(which);
        return isHost() && mouseX >= sliderX() - 3 && mouseX <= sliderX() + sliderW() + 3
                && mouseY >= y - 5 && mouseY <= y + 10;
    }

    private void updateSlider(int which, int mouseX) {
        double fraction = (mouseX - sliderX()) / (double) sliderW();
        fraction = Math.max(0.0, Math.min(1.0, fraction));
        if (which == 1) {
            borderSize = BORDER_MIN + (int) Math.round(fraction * ((BORDER_MAX - BORDER_MIN) / (double) BORDER_STEP)) * BORDER_STEP;
            if (this.sizePanel != null) {
                this.sizePanel.value = borderSize + " бл.";
            }
        } else {
            teamSpacing = SPACING_MIN + (int) Math.round(fraction * ((SPACING_MAX - SPACING_MIN) / (double) SPACING_STEP)) * SPACING_STEP;
        }
    }

    private void drawSlider(int which) {
        int min = which == 1 ? BORDER_MIN : SPACING_MIN;
        int max = which == 1 ? BORDER_MAX : SPACING_MAX;
        int value = which == 1 ? borderSize : teamSpacing;
        int x = sliderX();
        int y = sliderY(which);
        int w = sliderW();
        double fraction = Math.max(0.0, Math.min(1.0, (value - min) / (double) (max - min)));
        int knobX = x + (int) (fraction * (w - 4)) + 2;
        drawRect(x, y, x + w, y + 3, 0xFF3B3344);
        drawRect(x, y, knobX, y + 3, 0xFFE8B33C);
        drawRect(knobX - 2, y - 3, knobX + 2, y + 7, 0xFFF5F2F7);
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
            case ID_START_PANEL:
                sendCommand("/event start " + borderSize + " " + teamSpacing);
                this.mc.displayGuiScreen(null);
                return;
            case ID_COLOR_CYCLE:
                colorIndex = (colorIndex + 1) % COLORS.length;
                button.displayString = "Цвет: " + COLORS[colorIndex][1];
                return;
            case ID_CREATE_CONFIRM: {
                String name = this.nameField != null ? this.nameField.getText().trim() : "";
                if (!name.isEmpty()) {
                    sendCommand("/group create " + name + " " + COLORS[colorIndex][0]);
                    draftName = "";
                    this.nameField.setText("");
                }
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
        if (this.nameField != null) {
            this.nameField.mouseClicked(mouseX, mouseY, mouseButton);
        }
        if (mouseButton == 0) {
            if (inSlider(1, mouseX, mouseY)) {
                this.activeSlider = 1;
                updateSlider(1, mouseX);
            } else if (inSlider(2, mouseX, mouseY)) {
                this.activeSlider = 2;
                updateSlider(2, mouseX);
            }
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (this.activeSlider != 0 && clickedMouseButton == 0) {
            updateSlider(this.activeSlider, mouseX);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        this.activeSlider = 0;
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (this.nameField != null && this.nameField.isFocused()) {
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
            this.mc.displayGuiScreen(this.parentScreen);
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

package io.github.minifaizeelang.grouplives.client;

import io.github.minifaizeelang.grouplives.GroupLivesMod;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.text.TextFormatting;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Team tasks screen: the whole team board (click a task to mark it, X
 * removes your own) plus a task constructor - pick the assignee, the action
 * (mine/craft), the item from a searchable icon grid, and the amount (typed
 * or via steppers/chips).
 */
public class GuiTeamTasks extends GuiScreen {

    private static final int ID_BACK = 0;
    private static final int ID_MEMBER_CYCLE = 40;
    private static final int ID_ACTION_CYCLE = 41;
    private static final int ID_SCROLL_UP = 42;
    private static final int ID_SCROLL_DOWN = 43;
    private static final int ID_AMOUNT_MINUS = 44;
    private static final int ID_AMOUNT_PLUS = 45;
    private static final int ID_ADD = 12;
    private static final int ID_CHIP_BASE = 50; // 50..54 = 1/8/16/32/64
    private static final int[] CHIPS = {1, 8, 16, 32, 64};

    private static final int[] FORMAT_RGB = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF,
    };

    private final GuiScreen parentScreen;

    private List<String> members = new ArrayList<String>();
    private int memberIndex;
    private int actionType; // 0 = mine, 1 = craft
    private GuiTextField searchField;
    private GuiTextField amountField;
    private String searchQuery = "";
    private List<ItemStack> filtered = new ArrayList<ItemStack>();
    private int gridScroll;
    private String selectedItemId;
    private int hoveredCell = -1;

    private int panelY;
    private int panelH;
    private int gridY;
    private int gridRows;

    private static class BoardRow {
        final int x, y, w, h, index;
        final String player;
        final boolean mine;

        BoardRow(int x, int y, int w, int h, String player, int index, boolean mine) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.player = player;
            this.index = index;
            this.mine = mine;
        }
    }

    private final List<BoardRow> boardRows = new ArrayList<BoardRow>();

    private static List<ItemStack> itemCache;

    public GuiTeamTasks(GuiScreen parentScreen) {
        this.parentScreen = parentScreen;
    }

    private int boardX() {
        return 10;
    }

    private int boardW() {
        return this.width / 2 - 18;
    }

    private int panelX() {
        return this.width / 2 + 8;
    }

    private int panelW() {
        return this.width - this.width / 2 - 8 - 8;
    }

    private int gridX() {
        return panelX() + 4;
    }

    private int gridCols() {
        return Math.max(1, (panelW() - 8) / 19);
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        this.boardRows.clear();

        // Panel stretches over the full right column; the grid takes all the space left.
        this.panelY = 28;
        this.panelH = this.height - 30 - this.panelY;
        this.gridY = this.panelY + 70;
        int gridBottom = this.panelY + this.panelH - 64;
        this.gridRows = Math.max(2, (gridBottom - this.gridY) / 19);

        this.members = onlineTeamMembers();
        if (this.memberIndex >= this.members.size()) {
            this.memberIndex = 0;
        }

        if (hasTeam()) {
            this.searchField = new GuiTextField(0, this.fontRenderer, panelX() + 4, panelY + 52, panelW() - 34, 14);
            this.searchField.setMaxStringLength(32);
            this.searchField.setEnableBackgroundDrawing(false);
            this.amountField = new GuiTextField(1, this.fontRenderer, panelX() + 4, panelY + panelH - 46, 40, 14);
            this.amountField.setMaxStringLength(5);
            this.amountField.setText(String.valueOf(1));
            this.amountField.setValidator(s -> s.isEmpty() || s.matches("\\d{1,5}"));
            this.amountField.setEnableBackgroundDrawing(false);

            this.buttonList.add(makeCycleButton(ID_MEMBER_CYCLE, panelY + 16, "Кому: " + this.members.get(this.memberIndex), 0xFF5B8FFB));
            this.buttonList.add(makeCycleButton(ID_ACTION_CYCLE, panelY + 34, "Действие: " + (actionType == 0 ? "ДОБЫТЬ" : "СКРАФТИТЬ"), 0xFF7CC24A));
            this.buttonList.add(new ThemeButton(ID_SCROLL_UP, panelX() + panelW() - 26, panelY + 52, 22, 7, "^"));
            this.buttonList.add(new ThemeButton(ID_SCROLL_DOWN, panelX() + panelW() - 26, panelY + 59, 22, 7, "v"));
            this.buttonList.add(new ThemeButton(ID_AMOUNT_MINUS, panelX() + panelW() - 64, panelY + panelH - 46, 14, 14, "-"));
            this.buttonList.add(new ThemeButton(ID_AMOUNT_PLUS, panelX() + panelW() - 48, panelY + panelH - 46, 14, 14, "+"));
            for (int i = 0; i < CHIPS.length; i++) {
                this.buttonList.add(new ThemeButton(ID_CHIP_BASE + i,
                        panelX() + panelW() - 230 + i * 30, panelY + panelH - 46, 26, 14, String.valueOf(CHIPS[i])));
            }
            PanelButton add = new PanelButton(ID_ADD, panelX() + 4, panelY + panelH - 24, panelW() - 8, 18,
                    "ДОБАВИТЬ ЗАДАЧУ", 0xFFE8B33C);
            this.buttonList.add(add);
        }
        this.buttonList.add(new ThemeButton(ID_BACK, this.width - 70, this.height - 22, 60, 16, "Назад"));
        refilter();
    }

    /** Cycle buttons must never be vanilla (their texture breaks past 200px) - use our own drawing. */
    private PanelButton makeCycleButton(int id, int y, String label, int accent) {
        String label2 = truncate(label, panelW() - 30);
        return new PanelButton(id, panelX() + 4, y, panelW() - 8, 16, label2, accent);
    }

    private boolean hasTeam() {
        return this.mc.player != null && this.mc.world != null
                && this.mc.world.getScoreboard().getPlayersTeam(this.mc.player.getName()) != null;
    }

    private List<String> onlineTeamMembers() {
        List<String> out = new ArrayList<String>();
        if (this.mc.player == null || this.mc.world == null || this.mc.getConnection() == null) {
            return out;
        }
        Scoreboard sb = this.mc.world.getScoreboard();
        ScorePlayerTeam myTeam = sb.getPlayersTeam(this.mc.player.getName());
        if (myTeam == null) {
            out.add(this.mc.player.getName());
            return out;
        }
        List<String> members = new ArrayList<String>(myTeam.getMembershipCollection());
        members.sort(String::compareTo);
        for (String member : members) {
            if (this.mc.getConnection().getPlayerInfo(member) != null) {
                out.add(member);
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

    private static synchronized List<ItemStack> allItems() {
        if (itemCache == null) {
            List<ItemStack> list = new ArrayList<ItemStack>();
            for (Item item : Item.REGISTRY) {
                if (item == null) {
                    continue;
                }
                ItemStack stack = new ItemStack(item);
                if (stack.isEmpty()) {
                    continue;
                }
                list.add(stack);
            }
            list.sort(Comparator.comparing(stack -> stack.getDisplayName().toLowerCase()));
            itemCache = list;
        }
        return itemCache;
    }

    private void refilter() {
        String query = this.searchField != null ? this.searchField.getText().toLowerCase().trim() : "";
        filtered.clear();
        for (ItemStack stack : allItems()) {
            if (query.isEmpty()
                    || stack.getDisplayName().toLowerCase().contains(query)
                    || stack.getItem().getRegistryName().toString().contains(query)) {
                filtered.add(stack);
            }
        }
        gridScroll = Math.min(gridScroll, maxScroll());
    }

    private int maxScroll() {
        return Math.max(0, (filtered.size() + gridCols() - 1) / gridCols() - gridRows);
    }

    private int amount() {
        try {
            return Math.max(1, Math.min(99999, Integer.parseInt(this.amountField.getText().trim())));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private void setAmount(int value) {
        this.amountField.setText(String.valueOf(Math.max(1, Math.min(99999, value))));
    }

    @Override
    public void updateScreen() {
        if (this.searchField != null) {
            this.searchField.updateCursorCounter();
        }
        if (this.amountField != null) {
            this.amountField.updateCursorCounter();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawGradientRect(0, 0, this.width, this.height, 0xFF231826, 0xFF05040A);
        UiTheme.scanlines(0, 0, this.width, this.height);
        String title = "ЗАДАЧИ КОМАНДЫ";
        int cx = this.width / 2;
        drawCenteredString(this.fontRenderer, title, cx, 8, UiTheme.YELLOW);

        if (!hasTeam()) {
            drawCenteredString(this.fontRenderer, "Вы не состоите в команде - выберите её в лобби (L).",
                    cx, this.height / 2 - 10, 0xFF9A8FA8);
            super.drawScreen(mouseX, mouseY, partialTicks);
            return;
        }

        drawBoard();
        drawConstructor(mouseX, mouseY);

        super.drawScreen(mouseX, mouseY, partialTicks);

        if (this.searchField != null) {
            this.searchField.drawTextBox();
        }
        if (this.amountField != null) {
            this.amountField.drawTextBox();
        }
    }

    private void drawBoard() {
        int x = boardX();
        int w = boardW();
        int top = panelY;
        int bottom = this.height - 30;
        UiTheme.panel(x, top, w, bottom - top, UiTheme.YELLOW);
        UiTheme.trefoil(x + w - 34, bottom - 40, 0xFF2E2508);

        this.fontRenderer.drawStringWithShadow("ДОСКА ЗАДАЧ", x + 12, top + 5, UiTheme.TEXT);

        boardRows.clear();
        Map<String, List<ClientState.TaskEntry>> byPlayer =
                new LinkedHashMap<String, List<ClientState.TaskEntry>>();
        for (ClientState.TaskEntry entry : ClientState.teamTasks) {
            List<ClientState.TaskEntry> list = byPlayer.get(entry.player);
            if (list == null) {
                list = new ArrayList<ClientState.TaskEntry>();
                byPlayer.put(entry.player, list);
            }
            list.add(entry);
        }

        Scoreboard sb = this.mc.world != null ? this.mc.world.getScoreboard() : null;
        ScorePlayerTeam myTeam = this.mc.player != null && sb != null
                ? sb.getPlayersTeam(this.mc.player.getName()) : null;
        int accent = myTeam != null ? colorOf(myTeam) : 0xFF5B8FFB;

        int ry = top + 28;
        outer:
        for (Map.Entry<String, List<ClientState.TaskEntry>> entry : byPlayer.entrySet()) {
            if (ry + 16 > bottom) {
                break;
            }
            List<ClientState.TaskEntry> tasks = entry.getValue();
            NetworkPlayerInfo info = mc.getConnection() != null
                    ? mc.getConnection().getPlayerInfo(entry.getKey()) : null;
            if (info != null) {
                mc.getTextureManager().bindTexture(info.getLocationSkin());
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                Gui.drawScaledCustomSizeModalRect(x + 8, ry, 8, 8, 8, 8, 11, 11, 64, 64);
                GlStateManager.enableBlend();
                Gui.drawScaledCustomSizeModalRect(x + 8, ry, 40, 8, 8, 8, 11, 11, 64, 64);
                GlStateManager.disableBlend();
            }
            int done = 0;
            for (ClientState.TaskEntry task : tasks) {
                if (task.done) {
                    done++;
                }
            }
            this.fontRenderer.drawStringWithShadow(entry.getKey(), x + 23, ry + 2, 0xFFF5F2F7);
            String counter = done + "/" + tasks.size();
            this.fontRenderer.drawStringWithShadow(counter,
                    x + w - 8 - this.fontRenderer.getStringWidth(counter), ry + 2, 0xFFE8B33C);
            ry += 15;

            for (ClientState.TaskEntry task : tasks) {
                boolean mine = this.mc.player != null && task.player.equals(this.mc.player.getName());
                int rowH = task.type >= 0 ? 18 : 11;
                if (ry + rowH > bottom - 2) {
                    this.fontRenderer.drawStringWithShadow("...", x + 14, ry, 0xFF6E6480);
                    break outer;
                }
                Gui.drawRect(x + 12, ry + 1, x + 18, ry + 7, task.done ? 0xFF7CC24A : 0xFF3B3344);
                if (!task.done) {
                    Gui.drawRect(x + 12, ry + 1, x + 18, ry + 2, accent);
                    Gui.drawRect(x + 12, ry + 6, x + 18, ry + 7, accent);
                    Gui.drawRect(x + 12, ry + 1, x + 13, ry + 7, accent);
                    Gui.drawRect(x + 17, ry + 1, x + 18, ry + 7, accent);
                }
                String text = TaskHud.taskText(task);
                this.fontRenderer.drawStringWithShadow(truncate(text, w - 60), x + 22, ry + (rowH == 18 ? 5 : 1),
                        task.done ? 0xFF6E6480 : 0xFFF5F2F7);
                if (mine) {
                    this.fontRenderer.drawStringWithShadow("X",
                            x + w - 16, ry + (rowH == 18 ? 5 : 1), 0xFFD0483C);
                }
                boardRows.add(new BoardRow(x + 8, ry, w - 16, rowH, task.player, task.index, mine));
                ry += rowH;
            }
            ry += 3;
        }
    }

    private void drawConstructor(int mouseX, int mouseY) {
        int px = panelX();
        int pw = panelW();
        int py = panelY;
        int ph = this.height - 30 - py;
        UiTheme.panel(px, py, pw, ph, UiTheme.YELLOW);

        this.fontRenderer.drawStringWithShadow("НОВАЯ ЗАДАЧА", px + 12, py + 5, UiTheme.YELLOW);

        // Themed backdrops for the text fields (drawn beneath their text)
        UiTheme.field(px + 4, panelY + 52, panelW() - 34, 14);
        UiTheme.field(px + 4, panelY + panelH - 46, 40, 14);

        // Item grid
        int gx = gridX();
        int gy = gridY;
        int cols = gridCols();
        Gui.drawRect(gx - 2, gy - 2, gx + cols * 19 + 1, gy + gridRows * 19 - 1, 0xFF0E0B12);
        String selectedName = "";
        hoveredCell = -1;
        for (int row = 0; row < gridRows; row++) {
            for (int col = 0; col < cols; col++) {
                int i = (gridScroll + row) * cols + col;
                if (i >= filtered.size()) {
                    continue;
                }
                ItemStack stack = filtered.get(i);
                int cellX = gx + col * 19;
                int cellY = gy + row * 19;
                boolean isSelected = stack.getItem().getRegistryName().toString().equals(selectedItemId);
                boolean isHovered = mouseX >= cellX && mouseX < cellX + 17 && mouseY >= cellY && mouseY < cellY + 17;
                if (isSelected) {
                    Gui.drawRect(cellX - 1, cellY - 1, cellX + 17, cellY + 17, 0xFFF5F2F7);
                } else if (isHovered) {
                    Gui.drawRect(cellX - 1, cellY - 1, cellX + 17, cellY + 17, 0xFF5B8FFB);
                    hoveredCell = i;
                }
                mc.getRenderItem().renderItemAndEffectIntoGUI(stack, cellX, cellY);
                mc.getRenderItem().renderItemOverlayIntoGUI(mc.fontRenderer, stack, cellX, cellY, null);
                if (isSelected) {
                    selectedName = stack.getDisplayName();
                }
            }
        }
        if (selectedName.isEmpty() && selectedItemId != null) {
            selectedName = selectedItemId;
        }
        this.fontRenderer.drawStringWithShadow(
                selectedName.isEmpty() ? "выберите предмет из сетки" : truncate(selectedName, pw - 24),
                px + 12, py + ph - 60, selectedName.isEmpty() ? UiTheme.TEXT_FADED : UiTheme.TEXT);

        this.fontRenderer.drawStringWithShadow("Кол-во:", px + 52, py + ph - 42, UiTheme.TEXT_DIM);
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
        switch (button.id) {
            case ID_BACK:
                this.mc.displayGuiScreen(this.parentScreen);
                return;
            case ID_MEMBER_CYCLE:
                if (!members.isEmpty()) {
                    memberIndex = (memberIndex + 1) % members.size();
                    button.displayString = truncate("Кому: " + members.get(memberIndex), panelW() - 30);
                }
                return;
            case ID_ACTION_CYCLE:
                actionType = (actionType + 1) % 2;
                button.displayString = "Действие: " + (actionType == 0 ? "ДОБЫТЬ" : "СКРАФТИТЬ");
                return;
            case ID_SCROLL_UP:
                gridScroll = Math.max(0, gridScroll - 1);
                return;
            case ID_SCROLL_DOWN:
                gridScroll = Math.min(maxScroll(), gridScroll + 1);
                return;
            case ID_AMOUNT_MINUS:
                setAmount(amount() - 1);
                return;
            case ID_AMOUNT_PLUS:
                setAmount(amount() + 1);
                return;
            case ID_ADD: {
                if (members.isEmpty() || selectedItemId == null) {
                    return;
                }
                sendCommand("/task additem " + members.get(memberIndex) + " "
                        + (actionType == 0 ? "mine" : "craft") + " " + selectedItemId + " " + amount());
                return;
            }
            default:
        }
        if (button.id >= ID_CHIP_BASE && button.id < ID_CHIP_BASE + CHIPS.length) {
            setAmount(CHIPS[button.id - ID_CHIP_BASE]);
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
        if (this.searchField != null) {
            this.searchField.mouseClicked(mouseX, mouseY, mouseButton);
            refilter();
        }
        if (this.amountField != null) {
            this.amountField.mouseClicked(mouseX, mouseY, mouseButton);
        }

        // Item grid cells
        if (mouseButton == 0) {
            int gx = gridX();
            int gy = gridY;
            int cols = gridCols();
            int col = (mouseX - gx) / 19;
            int row = (mouseY - gy) / 19;
            if (col >= 0 && col < cols && row >= 0 && row < gridRows
                    && mouseX >= gx && mouseY >= gy) {
                int i = (gridScroll + row) * cols + col;
                if (i >= 0 && i < filtered.size()) {
                    selectedItemId = filtered.get(i).getItem().getRegistryName().toString();
                }
            }
        }

        // Board rows: own rows toggle (whole row) and X zone removes; other players' rows are read-only
        if (mouseButton == 0 && mc.player != null) {
            for (BoardRow row : boardRows) {
                if (mouseX >= row.x && mouseX <= row.x + row.w && mouseY >= row.y && mouseY <= row.y + row.h) {
                    if (!row.mine) {
                        return;
                    }
                    if (mouseX > row.x + row.w - 14) {
                        sendCommand("/task remove " + row.player + " " + row.index);
                    } else {
                        sendCommand("/task toggle " + row.player + " " + row.index);
                    }
                    return;
                }
            }
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getDWheel();
        if (wheel != 0) {
            int mx = Mouse.getX() * this.width / this.mc.displayWidth;
            int my = this.height - Mouse.getY() * this.height / this.mc.displayHeight - 1;
            int gy = gridY;
            if (mx >= panelX() && mx <= panelX() + panelW() && my >= gy - 4 && my <= gy + gridRows * 19 + 4) {
                int step = wheel > 0 ? -1 : 1;
                gridScroll = Math.max(0, Math.min(maxScroll(), gridScroll + step));
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (this.amountField != null && this.amountField.isFocused()) {
            if (keyCode == 1) {
                this.amountField.setFocused(false);
                return;
            }
            this.amountField.textboxKeyTyped(typedChar, keyCode);
            return;
        }
        if (this.searchField != null && this.searchField.isFocused()) {
            if (keyCode == 1) {
                this.searchField.setFocused(false);
                return;
            }
            this.searchField.textboxKeyTyped(typedChar, keyCode);
            refilter();
            return;
        }
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

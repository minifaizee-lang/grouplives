package io.github.minifaizeelang.grouplives.client;

import io.github.minifaizeelang.grouplives.GroupManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.text.TextFormatting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Team roster screen (Fallout-terminal style): create a team with a live
 * color preview, invite players, kick members, leave or delete the team.
 * Joining happens only through invitations (clickable chat message).
 */
public class GuiTeamRoster extends GuiScreen {

    private static final int ID_BACK = 0;
    private static final int ID_COLOR_CYCLE = 40;
    private static final int ID_CREATE = 41;
    private static final int ID_INVITE_CYCLE = 42;
    private static final int ID_INVITE = 43;
    private static final int ID_LEAVE = 44;
    private static final int ID_DELETE = 45;
    private static final int ID_KICK_BASE = 200;

    private static final int[] FORMAT_RGB = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF,
    };

    private static final String[][] COLORS = {
            {"red", "красный"}, {"gold", "золотой"}, {"yellow", "жёлтый"},
            {"green", "зелёный"}, {"aqua", "голубой"}, {"blue", "синий"},
            {"light_purple", "розовый"}, {"dark_purple", "фиолетовый"},
            {"dark_red", "тёмно-красный"}, {"dark_aqua", "бирюзовый"},
            {"dark_blue", "тёмно-синий"}, {"gray", "серый"},
    };

    private final GuiScreen parentScreen;

    private GuiTextField nameField;
    private String draftName = "";
    private int colorIndex;
    private List<String> inviteTargets = new ArrayList<String>();
    private int inviteIndex;
    private boolean hasTeam;
    private ScorePlayerTeam myTeam;

    private final List<Object[]> kickRects = new ArrayList<Object[]>(); // {x, y, w, h, memberName}
    private String hint;
    private long hintUntil;
    private String lastStateKey = "";

    public GuiTeamRoster(GuiScreen parentScreen) {
        this.parentScreen = parentScreen;
    }

    /**
     * Ownership is validated server-side; the client shows the controls to
     * everyone and the server rejects non-owners with a clear message.
     */
    private boolean isOwner() {
        return true;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        this.kickRects.clear();

        this.myTeam = null;
        this.hasTeam = false;
        if (this.mc.player != null && this.mc.world != null) {
            Scoreboard sb = this.mc.world.getScoreboard();
            this.myTeam = sb.getPlayersTeam(this.mc.player.getName());
            this.hasTeam = this.myTeam != null;
        }

        if (this.hasTeam) {
            // Invite card: cycle teamless online players + invite button
            this.inviteTargets = teamlessOnlinePlayers();
            if (this.inviteIndex >= this.inviteTargets.size()) {
                this.inviteIndex = 0;
            }
            this.buttonList.add(new ThemeButton(ID_INVITE_CYCLE, 24, this.height - 118, 200, 16,
                    "Пригласить: " + currentInviteTarget()));
            this.buttonList.add(new ThemeButton(ID_INVITE, 24, this.height - 100, 200, 16, "ПРИГЛАСИТЬ"));
            this.buttonList.add(new ThemeButton(ID_LEAVE, 24, this.height - 56, 150, 16, "Покинуть команду"));
            if (isOwner()) {
                this.buttonList.add(new ThemeButton(ID_DELETE, 24, this.height - 38, 150, 16, "Распустить команду"));
            }
        } else {
            this.nameField = new GuiTextField(0, this.fontRenderer, 24, this.height - 132, 150, 14);
            this.nameField.setMaxStringLength(16);
            this.nameField.setText(draftName);
            this.nameField.setEnableBackgroundDrawing(false);
            this.buttonList.add(new ThemeButton(ID_COLOR_CYCLE, 190, this.height - 133, 104, 16, "Цвет: " + COLORS[colorIndex][1]));
            this.buttonList.add(new ThemeButton(ID_CREATE, 300, this.height - 133, 84, 16, "СОЗДАТЬ"));
        }
        this.buttonList.add(new ThemeButton(ID_BACK, this.width - 70, this.height - 22, 60, 16, "Назад"));
    }

    private String currentInviteTarget() {
        if (inviteTargets.isEmpty()) {
            return "нет свободных игроков";
        }
        return inviteTargets.get(inviteIndex);
    }

    private List<String> teamlessOnlinePlayers() {
        List<String> out = new ArrayList<String>();
        if (this.mc.world == null) {
            return out;
        }
        Scoreboard sb = this.mc.world.getScoreboard();
        for (Object o : this.mc.world.playerEntities) {
            EntityPlayer player = (EntityPlayer) o;
            if (sb.getPlayersTeam(player.getName()) == null) {
                out.add(player.getName());
            }
        }
        Collections.sort(out);
        return out;
    }

    private static int colorOfFriendly(String friendly) {
        try {
            TextFormatting format = TextFormatting.valueOf(friendly.toUpperCase());
            int index = format.getColorIndex();
            return 0xFF000000 | (index >= 0 && index < FORMAT_RGB.length ? FORMAT_RGB[index] : 0xFFFFFF);
        } catch (IllegalArgumentException e) {
            return 0xFFFFFFFF;
        }
    }

    @Override
    public void updateScreen() {
        if (this.nameField != null) {
            draftName = this.nameField.getText();
        }
        if (this.mc.world == null || this.mc.player == null) {
            return;
        }
        // Rebuild when the team layout changes (members, teams, teamless players)
        Scoreboard sb = this.mc.world.getScoreboard();
        StringBuilder key = new StringBuilder();
        ScorePlayerTeam myTeamNow = sb.getPlayersTeam(this.mc.player.getName());
        if (myTeamNow != null) {
            key.append(myTeamNow.getName()).append('|');
            List<String> members = new ArrayList<String>(myTeamNow.getMembershipCollection());
            Collections.sort(members);
            for (String member : members) {
                key.append(member).append(',');
            }
        }
        key.append('#').append(teamlessOnlinePlayers().size());
        if (!key.toString().equals(lastStateKey)) {
            lastStateKey = key.toString();
            initGui();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        Gui.drawRect(0, 0, this.width, this.height, 0xFF000000);
        UiTheme.scanlines(0, 0, this.width, this.height);

        int cx = this.width / 2;
        drawCenteredString(this.fontRenderer, "СОСТАВ КОМАНДЫ", cx, 14, UiTheme.YELLOW);
        drawCenteredString(this.fontRenderer, "государственный терминал · управление отрядом", cx, 26, UiTheme.TEXT_FADED);

        UiTheme.panel(10, 40, this.width - 20, this.height - 72, UiTheme.YELLOW);

        if (hasTeam) {
            drawTeamCard();
        } else {
            drawCreateForm();
        }

        super.drawScreen(mouseX, mouseY, partialTicks);

        if (!hasTeam && this.nameField != null) {
            this.nameField.drawTextBox();
        }
        if (hint != null && System.currentTimeMillis() < hintUntil) {
            drawCenteredString(this.fontRenderer, hint, cx, this.height - 32, UiTheme.GREEN);
        }
    }

    private void drawCreateForm() {
        this.fontRenderer.drawStringWithShadow("НОВАЯ КОМАНДА", 24, 48, UiTheme.YELLOW);
        UiTheme.trefoil(this.width - 46, 48, 0xFF2E2508);
        this.fontRenderer.drawStringWithShadow("Название команды:", 24, this.height - 142, UiTheme.TEXT_DIM);

        // Live preview: colored tag + nickname
        String draft = this.nameField != null ? this.nameField.getText().trim() : "";
        if (draft.isEmpty()) {
            this.fontRenderer.drawStringWithShadow("Превью: введите название команды...",
                    24, this.height - 96, UiTheme.TEXT_FADED);
        } else {
            TextFormatting format;
            try {
                format = TextFormatting.valueOf(COLORS[colorIndex][0].toUpperCase());
            } catch (IllegalArgumentException e) {
                format = TextFormatting.WHITE;
            }
            String nick = this.mc.player != null ? this.mc.player.getName() : "";
            String coloredTag = format.toString()
                    + String.format(GroupManager.getPrefixFormat(), draft) + TextFormatting.RESET;
            this.fontRenderer.drawStringWithShadow("Превью: ", 24, this.height - 96, UiTheme.TEXT_DIM);
            this.fontRenderer.drawStringWithShadow(coloredTag + nick,
                    24 + this.fontRenderer.getStringWidth("Превью: "), this.height - 96, UiTheme.TEXT);
        }
    }

    private void drawTeamCard() {
        int x = 24;
        int y = 48;
        TextFormatting color = myTeam.getColor() == null ? TextFormatting.WHITE : myTeam.getColor();
        this.fontRenderer.drawStringWithShadow("КОМАНДА:", x, y, UiTheme.TEXT_DIM);
        this.fontRenderer.drawStringWithShadow(myTeam.getName().toUpperCase(), x + 76, y, colorOf(myTeam));
        this.fontRenderer.drawStringWithShadow("Нажмите X у игрока, чтобы исключить (владелец).",
                x, y + 14, UiTheme.TEXT_FADED);

        kickRects.clear();
        List<String> members = new ArrayList<String>(myTeam.getMembershipCollection());
        Collections.sort(members);
        int ry = y + 30;
        for (String member : members) {
            boolean self = member.equals(this.mc.player.getName());
            this.fontRenderer.drawStringWithShadow(member + (self ? " (вы)" : ""), x + 12, ry, UiTheme.TEXT);
            if (!self) {
                this.fontRenderer.drawStringWithShadow("X", this.width - 62, ry, UiTheme.RED);
                kickRects.add(new Object[]{this.width - 66, ry - 2, 16, 12, member});
            }
            ry += 13;
        }
    }

    private static int colorOf(ScorePlayerTeam team) {
        TextFormatting format = team.getColor();
        int index = format.getColorIndex();
        return 0xFF000000 | (index >= 0 && index < FORMAT_RGB.length ? FORMAT_RGB[index] : 0xFFFFFF);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (this.nameField != null) {
            this.nameField.mouseClicked(mouseX, mouseY, mouseButton);
        }
        if (mouseButton == 0 && isOwner()) {
            for (Object[] rect : kickRects) {
                int rx = (Integer) rect[0];
                int ryy = (Integer) rect[1];
                int rw = (Integer) rect[2];
                int rh = (Integer) rect[3];
                String member = (String) rect[4];
                if (mouseX >= rx && mouseX <= rx + rw && mouseY >= ryy && mouseY <= ryy + rh) {
                    sendCommand("/group kick " + member);
                    return;
                }
            }
        }
    }

    private void sendCommand(String command) {
        if (this.mc.player != null) {
            this.mc.player.sendChatMessage(command);
        }
    }

    public void showHint(String text) {
        hint = text;
        hintUntil = System.currentTimeMillis() + 3000;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (this.nameField != null && this.nameField.isFocused()) {
            if (keyCode == 1) {
                this.nameField.setFocused(false);
                return;
            }
            if (keyCode == 28 && !this.nameField.getText().trim().isEmpty()) {
                actionPerformedById(ID_CREATE);
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
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case ID_BACK:
                this.mc.displayGuiScreen(this.parentScreen);
                return;
            case ID_COLOR_CYCLE:
                colorIndex = (colorIndex + 1) % COLORS.length;
                button.displayString = "Цвет: " + COLORS[colorIndex][1];
                return;
            case ID_CREATE: {
                String name = this.nameField != null ? this.nameField.getText().trim() : "";
                if (!name.isEmpty()) {
                    sendCommand("/group create " + name + " " + COLORS[colorIndex][0]);
                    this.nameField.setText("");
                    draftName = "";
                }
                return;
            }
            case ID_INVITE_CYCLE:
                if (!inviteTargets.isEmpty()) {
                    inviteIndex = (inviteIndex + 1) % inviteTargets.size();
                    button.displayString = "Пригласить: " + currentInviteTarget();
                }
                return;
            case ID_INVITE:
                if (!inviteTargets.isEmpty()) {
                    sendCommand("/group invite " + inviteTargets.get(inviteIndex));
                    showHint("Приглашение отправлено: " + inviteTargets.get(inviteIndex));
                }
                return;
            case ID_LEAVE:
                sendCommand("/group leave");
                return;
            case ID_DELETE:
                sendCommand("/group delete");
                return;
            default:
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }
}

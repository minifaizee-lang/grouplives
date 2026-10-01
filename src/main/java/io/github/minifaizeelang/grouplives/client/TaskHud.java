package io.github.minifaizeelang.grouplives.client;

import io.github.minifaizeelang.grouplives.GroupLivesMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The chat-side team panel: while the chat is open, draws "ВАША КОМАНДА"
 * (team members with avatars, per-player task counters and their tasks with
 * checkboxes) in the top-left corner. Clicking a task row toggles it via
 * /task toggle. The server only ever sends this board to team members.
 */
@Mod.EventBusSubscriber(value = Side.CLIENT, modid = GroupLivesMod.MODID)
public final class TaskHud {

    private static class Row {
        final int x, y, w, h, index;
        final String player;

        Row(int x, int y, int w, int h, String player, int index) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.player = player;
            this.index = index;
        }
    }

    /** Clickable task rows, recomputed on every draw (client thread only). */
    private static final List<Row> rows = new ArrayList<Row>();

    private TaskHud() {
    }

    @SubscribeEvent
    public static void onDrawScreen(GuiScreenEvent.DrawScreenEvent.Post event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!(event.getGui() instanceof GuiChat) || mc.player == null || mc.world == null) {
            return;
        }
        if (ClientState.teamTasks.isEmpty()) {
            return;
        }
        rows.clear();

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

        int x = 8;
        int w = 180;
        int y = 8;
        int headerH = 24;
        int maxH = mc.currentScreen.height - 140;
        int contentH = 0;
        for (List<ClientState.TaskEntry> tasks : byPlayer.values()) {
            contentH += 15 + tasks.size() * 10 + 3;
        }
        int panelH = Math.min(headerH + contentH + 8, maxH);

        Gui.drawRect(x, y, x + w, y + panelH, 0xE617121C);
        Gui.drawRect(x, y, x + w, y + 1, 0xFF3B3344);
        Gui.drawRect(x, y + panelH - 1, x + w, y + panelH, 0xFF3B3344);
        Gui.drawRect(x, y, x + 1, y + panelH, 0xFF3B3344);
        Gui.drawRect(x + w - 1, y, x + w, y + panelH, 0xFF3B3344);
        Gui.drawRect(x + 3, y + 3, x + 6, y + panelH - 3, 0xFFE8B33C);

        mc.fontRenderer.drawStringWithShadow("ВАША КОМАНДА", x + 12, y + 5, 0xFFF5F2F7);
        mc.fontRenderer.drawStringWithShadow("нажмите на задачу, чтобы отметить", x + 12, y + 14, 0xFF6E6480);

        int ry = y + headerH;
        int bottom = y + panelH - 4;
        outer:
        for (Map.Entry<String, List<ClientState.TaskEntry>> entry : byPlayer.entrySet()) {
            if (ry + 15 > bottom) {
                break;
            }
            List<ClientState.TaskEntry> tasks = entry.getValue();
            NetworkPlayerInfo info = mc.getConnection() != null
                    ? mc.getConnection().getPlayerInfo(entry.getKey()) : null;
            if (info != null) {
                mc.getTextureManager().bindTexture(info.getLocationSkin());
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                Gui.drawScaledCustomSizeModalRect(x + 7, ry, 8, 8, 8, 8, 11, 11, 64, 64);
                GlStateManager.enableBlend();
                Gui.drawScaledCustomSizeModalRect(x + 7, ry, 40, 8, 8, 8, 11, 11, 64, 64);
                GlStateManager.disableBlend();
            }
            int done = 0;
            for (ClientState.TaskEntry task : tasks) {
                if (task.done) {
                    done++;
                }
            }
            mc.fontRenderer.drawStringWithShadow(entry.getKey(), x + 22, ry + 2, 0xFFF5F2F7);
            String counter = done + "/" + tasks.size();
            mc.fontRenderer.drawStringWithShadow(counter,
                    x + w - 8 - mc.fontRenderer.getStringWidth(counter), ry + 2, 0xFFE8B33C);
            ry += 14;

            for (ClientState.TaskEntry task : tasks) {
                if (ry + 10 > bottom) {
                    mc.fontRenderer.drawStringWithShadow("...", x + 14, ry, 0xFF6E6480);
                    break outer;
                }
                if (task.done) {
                    Gui.drawRect(x + 12, ry + 1, x + 18, ry + 7, 0xFF7CC24A);
                } else {
                    Gui.drawRect(x + 12, ry + 1, x + 18, ry + 2, 0xFFE8B33C);
                    Gui.drawRect(x + 12, ry + 6, x + 18, ry + 7, 0xFFE8B33C);
                    Gui.drawRect(x + 12, ry + 1, x + 13, ry + 7, 0xFFE8B33C);
                    Gui.drawRect(x + 17, ry + 1, x + 18, ry + 7, 0xFFE8B33C);
                }
                String text = (task.done ? "+ " : "") + task.text;
                mc.fontRenderer.drawStringWithShadow(truncate(mc, text, w - 36), x + 22, ry,
                        task.done ? 0xFF6E6480 : 0xFFF5F2F7);
                rows.add(new Row(x + 8, ry - 1, w - 16, 11, task.player, task.index));
                ry += 10;
            }
            ry += 3;
        }
    }

    @SubscribeEvent
    public static void onMousePre(GuiScreenEvent.MouseInputEvent.Pre event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!(mc.currentScreen instanceof GuiChat) || rows.isEmpty()) {
            return;
        }
        if (Mouse.getEventButton() != 0 || mc.player == null) {
            return;
        }
        int mx = Mouse.getEventX() * mc.currentScreen.width / mc.displayWidth;
        int my = mc.currentScreen.height - Mouse.getEventY() * mc.currentScreen.height / mc.displayHeight - 1;
        for (Row row : rows) {
            if (mx >= row.x && mx <= row.x + row.w && my >= row.y && my <= row.y + row.h) {
                mc.player.sendChatMessage("/task toggle " + row.player + " " + row.index);
                event.setCanceled(true);
                return;
            }
        }
    }

    private static String truncate(Minecraft mc, String text, int maxWidth) {
        if (mc.fontRenderer.getStringWidth(text) <= maxWidth) {
            return text;
        }
        while (text.length() > 1 && mc.fontRenderer.getStringWidth(text + "...") > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "...";
    }
}

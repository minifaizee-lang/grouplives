package io.github.minifaizeelang.grouplives.client;

import io.github.minifaizeelang.grouplives.GroupLivesMod;
import io.github.minifaizeelang.grouplives.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The team task panel. Always visible on the HUD (below the minimap) and
 * interactive in two places: over the chat and inside the quick-tasks
 * overlay screen (Tasks key). Only the assignee marks their own task.
 * The server never sends the board to non-team players.
 */
@Mod.EventBusSubscriber(value = Side.CLIENT, modid = GroupLivesMod.MODID)
public final class TaskHud {

    public static class Row {
        final int x, y, w, h, index;
        final String player;
        final boolean mine;

        Row(int x, int y, int w, int h, String player, int index, boolean mine) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.player = player;
            this.index = index;
            this.mine = mine;
        }
    }

    /** Clickable task rows, recomputed on every draw (client thread only). */
    private static final List<Row> rows = new ArrayList<Row>();

    /** Localized display-name cache for structured task items. */
    private static final Map<String, String> DISPLAY_CACHE = new HashMap<String, String>();

    private TaskHud() {
    }

    /** Human-readable text for a task: structured ("добыть X x3") or legacy text. */
    public static String taskText(ClientState.TaskEntry task) {
        if (task.type < 0) {
            return task.text == null ? "" : task.text;
        }
        String name = DISPLAY_CACHE.get(task.itemId);
        if (name == null) {
            net.minecraft.item.Item item = net.minecraft.item.Item.getByNameOrId(task.itemId);
            name = item == null ? task.itemId : new net.minecraft.item.ItemStack(item).getDisplayName();
            DISPLAY_CACHE.put(task.itemId, name);
        }
        return (task.type == 0 ? "добыть " : "скрафтить ") + name + " x" + task.amount;
    }

    // ------------------------------------------------------------------
    // Rendering: HUD (always) + chat (interactive)
    // ------------------------------------------------------------------

    /** Always-on HUD copy; hidden while any screen is open (those draw their own). */
    @SubscribeEvent
    public static void onRenderHud(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null || mc.player == null || mc.world == null) {
            return;
        }
        rows.clear();
        drawPanel(mc, true);
    }

    /** Interactive copy over the chat. */
    @SubscribeEvent
    public static void onDrawScreen(GuiScreenEvent.DrawScreenEvent.Post event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!(event.getGui() instanceof GuiChat) || mc.player == null || mc.world == null) {
            return;
        }
        rows.clear();
        drawPanel(mc, true);
    }

    /**
     * Draws the panel at (8, taskPanelTopOffset) and, when interactive,
     * records click regions for own tasks. Shared by HUD, chat and overlay.
     */
    public static void drawPanel(Minecraft mc, boolean interactive) {
        if (ClientState.teamTasks.isEmpty()) {
            rows.clear();
            return;
        }
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
        int y = ModConfig.taskPanelTopOffset;
        ScaledResolution sr = new ScaledResolution(mc);
        int maxH = Math.max(60, sr.getScaledHeight() - y - 60);
        int contentH = 0;
        for (List<ClientState.TaskEntry> tasks : byPlayer.values()) {
            contentH += 15 + tasks.size() * 10 + 3;
        }
        int panelH = Math.min(24 + contentH + 8, maxH);

        Gui.drawRect(x, y, x + w, y + panelH, 0xD017121C);
        Gui.drawRect(x, y, x + w, y + 1, 0xFF3B3344);
        Gui.drawRect(x, y + panelH - 1, x + w, y + panelH, 0xFF3B3344);
        Gui.drawRect(x, y, x + 1, y + panelH, 0xFF3B3344);
        Gui.drawRect(x + w - 1, y, x + w, y + panelH, 0xFF3B3344);
        Gui.drawRect(x + 3, y + 3, x + 6, y + panelH - 3, 0xFFE8B33C);

        mc.fontRenderer.drawStringWithShadow("ВАША КОМАНДА", x + 12, y + 5, 0xFFF5F2F7);
        mc.fontRenderer.drawStringWithShadow("нажмите на задачу, чтобы отметить", x + 12, y + 14, 0xFF6E6480);

        int ry = y + 24;
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

            boolean minePlayer = mc.player != null && entry.getKey().equals(mc.player.getName());
            for (ClientState.TaskEntry task : tasks) {
                if (ry + 10 > bottom) {
                    mc.fontRenderer.drawStringWithShadow("...", x + 14, ry, 0xFF6E6480);
                    break outer;
                }
                boolean mine = interactive && minePlayer;
                if (task.done) {
                    Gui.drawRect(x + 12, ry + 1, x + 18, ry + 7, 0xFF7CC24A);
                } else {
                    Gui.drawRect(x + 12, ry + 1, x + 18, ry + 2, 0xFFE8B33C);
                    Gui.drawRect(x + 12, ry + 6, x + 18, ry + 7, 0xFFE8B33C);
                    Gui.drawRect(x + 12, ry + 1, x + 13, ry + 7, 0xFFE8B33C);
                    Gui.drawRect(x + 17, ry + 1, x + 18, ry + 7, 0xFFE8B33C);
                }
                String text = (task.done ? "+ " : "") + taskText(task);
                mc.fontRenderer.drawStringWithShadow(truncate(mc, text, w - 36), x + 22, ry,
                        task.done ? 0xFF6E6480 : 0xFFF5F2F7);
                if (interactive) {
                    rows.add(new Row(x + 8, ry - 1, w - 16, 11, task.player, task.index, mine));
                }
                ry += 10;
            }
            ry += 3;
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

    // ------------------------------------------------------------------
    // Clicks (only the assignee can mark their own task)
    // ------------------------------------------------------------------

    /** Marks a clicked own task; returns true when a row was hit. */
    public static boolean handlePanelClick(Minecraft mc, int mouseX, int mouseY) {
        for (Row row : rows) {
            if (!row.mine) {
                continue;
            }
            if (mouseX >= row.x && mouseX <= row.x + row.w && mouseY >= row.y && mouseY <= row.y + row.h) {
                if (mc.player != null) {
                    mc.player.sendChatMessage("/task toggle " + row.player + " " + row.index);
                }
                return true;
            }
        }
        return false;
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
        if (handlePanelClick(mc, mx, my)) {
            event.setCanceled(true);
        }
    }
}

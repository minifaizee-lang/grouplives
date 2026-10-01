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
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.input.Mouse;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The team task panel. Always visible on the HUD; interactive while the chat
 * is open - own tasks are marked by clicking, and the panel itself can be
 * dragged anywhere (position persists in config/grouplives_client.cfg).
 * Only the assignee marks their own task; the server never sends the board
 * to non-team players.
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

    /** Clickable/draggable state, recomputed on every draw (client thread only). */
    private static final List<Row> rows = new ArrayList<Row>();

    /** Localized display-name cache for structured task items. */
    private static final Map<String, String> DISPLAY_CACHE = new HashMap<String, String>();

    private static final int PANEL_W = 260;

    private static int panelX;
    private static int panelY;
    private static int panelH;
    private static boolean posLoaded;
    private static boolean dragging;
    private static int dragOffX;
    private static int dragOffY;

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
    // Panel position (client-side config, changed by dragging in the chat)
    // ------------------------------------------------------------------

    private static void loadPos() {
        if (posLoaded) {
            return;
        }
        posLoaded = true;
        panelX = 8;
        panelY = ModConfig.taskPanelTopOffset;
        try {
            Configuration cfg = new Configuration(
                    new File(Loader.instance().getConfigDir(), "grouplives_client.cfg"));
            panelX = cfg.get("taskPanel", "x", panelX).getInt();
            panelY = cfg.get("taskPanel", "y", panelY).getInt();
        } catch (RuntimeException ignored) {
        }
    }

    private static void savePos() {
        try {
            Configuration cfg = new Configuration(
                    new File(Loader.instance().getConfigDir(), "grouplives_client.cfg"));
            cfg.get("taskPanel", "x", panelX).set(panelX);
            cfg.get("taskPanel", "y", panelY).set(panelY);
            cfg.save();
        } catch (RuntimeException ignored) {
        }
    }

    // ------------------------------------------------------------------
    // Rendering: HUD (always) + chat (interactive, draggable)
    // ------------------------------------------------------------------

    /** Always-on HUD copy; hidden while any screen is open (chat draws its own). */
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
        drawPanel(mc, false);
    }

    /** Interactive copy over the chat: tasks clickable, panel draggable. */
    @SubscribeEvent
    public static void onDrawScreen(GuiScreenEvent.DrawScreenEvent.Post event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!(event.getGui() instanceof GuiChat) || mc.player == null || mc.world == null) {
            return;
        }
        rows.clear();
        drawPanel(mc, true);
    }

    private static void drawPanel(Minecraft mc, boolean interactive) {
        if (ClientState.teamTasks.isEmpty()) {
            rows.clear();
            return;
        }
        loadPos();
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

        ScaledResolution sr = new ScaledResolution(mc);
        panelX = Math.min(panelX, Math.max(0, sr.getScaledWidth() - 80));
        panelY = Math.min(panelY, Math.max(0, sr.getScaledHeight() - 60));
        int maxH = Math.max(60, sr.getScaledHeight() - panelY - 60);
        int contentH = 0;
        for (List<ClientState.TaskEntry> tasks : byPlayer.values()) {
            contentH += 15 + tasks.size() * 10 + 3;
        }
        panelH = Math.min(24 + contentH + 8, maxH);

        Gui.drawRect(panelX, panelY, panelX + PANEL_W, panelY + panelH, 0xD017121C);
        Gui.drawRect(panelX, panelY, panelX + PANEL_W, panelY + 1, 0xFF3B3344);
        Gui.drawRect(panelX, panelY + panelH - 1, panelX + PANEL_W, panelY + panelH, 0xFF3B3344);
        Gui.drawRect(panelX, panelY, panelX + 1, panelY + panelH, 0xFF3B3344);
        Gui.drawRect(panelX + PANEL_W - 1, panelY, panelX + PANEL_W, panelY + panelH, 0xFF3B3344);
        Gui.drawRect(panelX + 3, panelY + 3, panelX + 6, panelY + panelH - 3, 0xFFE8B33C);

        mc.fontRenderer.drawStringWithShadow("ВАША КОМАНДА", panelX + 12, panelY + 5, 0xFFF5F2F7);
        mc.fontRenderer.drawStringWithShadow("задача - клик | окно - тащить", panelX + 12, panelY + 14, 0xFF6E6480);

        int ry = panelY + 24;
        int bottom = panelY + panelH - 4;
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
                Gui.drawScaledCustomSizeModalRect(panelX + 7, ry, 8, 8, 8, 8, 11, 11, 64, 64);
                GlStateManager.enableBlend();
                Gui.drawScaledCustomSizeModalRect(panelX + 7, ry, 40, 8, 8, 8, 11, 11, 64, 64);
                GlStateManager.disableBlend();
            }
            int done = 0;
            for (ClientState.TaskEntry task : tasks) {
                if (task.done) {
                    done++;
                }
            }
            mc.fontRenderer.drawStringWithShadow(entry.getKey(), panelX + 22, ry + 2, 0xFFF5F2F7);
            String counter = done + "/" + tasks.size();
            mc.fontRenderer.drawStringWithShadow(counter,
                    panelX + PANEL_W - 8 - mc.fontRenderer.getStringWidth(counter), ry + 2, 0xFFE8B33C);
            ry += 14;

            boolean minePlayer = mc.player != null && entry.getKey().equals(mc.player.getName());
            for (ClientState.TaskEntry task : tasks) {
                if (ry + 10 > bottom) {
                    mc.fontRenderer.drawStringWithShadow("...", panelX + 14, ry, 0xFF6E6480);
                    break outer;
                }
                boolean mine = interactive && minePlayer;
                if (task.done) {
                    Gui.drawRect(panelX + 12, ry + 1, panelX + 18, ry + 7, 0xFF7CC24A);
                } else {
                    Gui.drawRect(panelX + 12, ry + 1, panelX + 18, ry + 2, 0xFFE8B33C);
                    Gui.drawRect(panelX + 12, ry + 6, panelX + 18, ry + 7, 0xFFE8B33C);
                    Gui.drawRect(panelX + 12, ry + 1, panelX + 13, ry + 7, 0xFFE8B33C);
                    Gui.drawRect(panelX + 17, ry + 1, panelX + 18, ry + 7, 0xFFE8B33C);
                }
                String text = (task.done ? "+ " : "") + taskText(task);
                mc.fontRenderer.drawStringWithShadow(truncate(mc, text, PANEL_W - 36), panelX + 22, ry,
                        task.done ? 0xFF6E6480 : 0xFFF5F2F7);
                if (interactive) {
                    rows.add(new Row(panelX + 8, ry - 1, PANEL_W - 16, 11, task.player, task.index, mine));
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
    // Chat interaction: click own task to mark, drag anywhere else to move
    // ------------------------------------------------------------------

    /** Marks a clicked own task; returns true when one was hit. */
    private static boolean hitOwnTask(Minecraft mc, int mouseX, int mouseY) {
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

    private static boolean inPanel(int mouseX, int mouseY) {
        return mouseX >= panelX && mouseX <= panelX + PANEL_W
                && mouseY >= panelY && mouseY <= panelY + panelH;
    }

    @SubscribeEvent
    public static void onMousePre(GuiScreenEvent.MouseInputEvent.Pre event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!(mc.currentScreen instanceof GuiChat) || mc.player == null || rows.isEmpty() && !dragging) {
            return;
        }
        int button = Mouse.getEventButton();
        boolean pressed = Mouse.getEventButtonState();
        int mx = Mouse.getEventX() * mc.currentScreen.width / mc.displayWidth;
        int my = mc.currentScreen.height - Mouse.getEventY() * mc.currentScreen.height / mc.displayHeight - 1;

        if (pressed && button == 0 && inPanel(mx, my)) {
            if (hitOwnTask(mc, mx, my)) {
                event.setCanceled(true);
                return;
            }
            // grab the panel anywhere else
            dragging = true;
            dragOffX = mx - panelX;
            dragOffY = my - panelY;
            event.setCanceled(true);
            return;
        }
        if (!pressed && button == 0 && dragging) {
            dragging = false;
            savePos();
            return;
        }
        if (dragging && button == -1) { // pure mouse-move event while holding
            ScaledResolution sr = new ScaledResolution(mc);
            panelX = Math.max(0, Math.min(mx - dragOffX, sr.getScaledWidth() - 60));
            panelY = Math.max(0, Math.min(my - dragOffY, sr.getScaledHeight() - 20));
            event.setCanceled(true);
        }
    }
}

package io.github.minifaizeelang.grouplives.client;

import io.github.minifaizeelang.grouplives.GroupLivesMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.input.Keyboard;

/**
 * Client entry points: the lobby button + team teleport button in the Esc
 * menu, the L keybinding, and the auto-open behavior - the lobby launches by
 * itself when the world loads, for the host and for every joining player.
 * After the event has started the lobby is host-only.
 */
@Mod.EventBusSubscriber(value = Side.CLIENT, modid = GroupLivesMod.MODID)
public final class ClientEvents {

    private static final int MENU_BUTTON_ID = 7321;

    public static final KeyBinding LOBBY_KEY = new KeyBinding("Лобби ивента", Keyboard.KEY_L, "Groups & Lives");

    private static int openDelayTicks = -1;

    private ClientEvents() {
    }

    /** Called from the mod's init on the client side only. */
    public static void register() {
        ClientRegistry.registerKeyBinding(LOBBY_KEY);
    }

    private static boolean canOpenLobby(Minecraft mc, boolean notify) {
        if (!ClientState.eventStarted || mc.isSingleplayer()) {
            return true; // pre-event, or the host (who may reopen the lobby any time)
        }
        if (notify && mc.player != null) {
            mc.player.sendMessage(new TextComponentString(TextFormatting.GOLD + "[G&L] "
                    + TextFormatting.RED + "Лобби закрыто - ивент уже начался. "
                    + TextFormatting.YELLOW + "Телепорт к сокомандникам: Esc → «Телепорт к товарищу»."));
        }
        return false;
    }

    /**
     * Auto-opens the lobby after the server's login packet marked it pending
     * (pre-event only). Driven by the packet flag rather than world presence
     * so respawns and dimension changes do not re-trigger it.
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        boolean present = mc.player != null && mc.world != null;
        if (!present) {
            openDelayTicks = -1;
            return;
        }
        if (ClientState.autoOpenPending && openDelayTicks < 0) {
            openDelayTicks = 40; // let the world finish loading first
        }
        if (openDelayTicks > 0) {
            openDelayTicks--;
            if (openDelayTicks == 0) {
                ClientState.autoOpenPending = false;
                if (mc.currentScreen == null && canOpenLobby(mc, false)) {
                    mc.displayGuiScreen(new GuiLobby(null));
                }
            }
        }
    }

    /** Opens the lobby from the mod menu (with the host-only gate after start). */
    public static void openLobbyScreen(GuiScreen parent) {
        Minecraft mc = Minecraft.getMinecraft();
        if (canOpenLobby(mc, true)) {
            mc.displayGuiScreen(new GuiLobby(parent));
        }
    }

    @SubscribeEvent
    public static void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (event.getGui() instanceof GuiIngameMenu) {
            event.getButtonList().add(new GuiButton(MENU_BUTTON_ID,
                    event.getGui().width / 2 - 100, event.getGui().height / 4 + 150, 200, 16, "Командное выживание"));
        }
    }

    @SubscribeEvent
    public static void onActionPerformed(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (event.getButton().id == MENU_BUTTON_ID) {
            event.setCanceled(true);
            Minecraft mc = Minecraft.getMinecraft();
            mc.displayGuiScreen(new GuiModMenu(event.getGui()));
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (LOBBY_KEY.isPressed() && mc.currentScreen == null && mc.player != null && canOpenLobby(mc, true)) {
            mc.displayGuiScreen(new GuiLobby(null));
        }
    }
}

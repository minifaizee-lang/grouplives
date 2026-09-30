package io.github.minifaizeelang.grouplives.client;

import io.github.minifaizeelang.grouplives.GroupLivesMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
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

    private static final int LOBBY_BUTTON_ID = 7321;
    private static final int TP_BUTTON_ID = 7323;

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

    @SubscribeEvent
    public static void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (event.getGui() instanceof GuiIngameMenu) {
            event.getButtonList().add(new GuiButton(LOBBY_BUTTON_ID,
                    event.getGui().width / 2 - 100, event.getGui().height / 4 + 158, 200, 20, "Лобби ивента"));
            event.getButtonList().add(new GuiButton(TP_BUTTON_ID,
                    event.getGui().width / 2 - 100, event.getGui().height / 4 + 180, 200, 20, "Телепорт к товарищу"));
        }
    }

    @SubscribeEvent
    public static void onActionPerformed(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.getButton().id == LOBBY_BUTTON_ID) {
            event.setCanceled(true);
            if (canOpenLobby(mc, true)) {
                mc.displayGuiScreen(new GuiLobby(event.getGui()));
            }
        } else if (event.getButton().id == TP_BUTTON_ID) {
            event.setCanceled(true);
            mc.displayGuiScreen(new GuiTeamTeleport(event.getGui()));
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

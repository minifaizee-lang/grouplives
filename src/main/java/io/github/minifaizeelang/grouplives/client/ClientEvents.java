package io.github.minifaizeelang.grouplives.client;

import io.github.minifaizeelang.grouplives.GroupLivesMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.input.Keyboard;

/**
 * Client entry points: the "Лобби ивента" button in the Esc menu and the L
 * keybinding, both opening the lobby screen. Client-only; none of these
 * classes load on a dedicated server.
 */
@Mod.EventBusSubscriber(value = Side.CLIENT, modid = GroupLivesMod.MODID)
public final class ClientEvents {

    private static final int LOBBY_BUTTON_ID = 7321;

    public static final KeyBinding LOBBY_KEY = new KeyBinding("Лобби ивента", Keyboard.KEY_L, "Groups & Lives");

    private ClientEvents() {
    }

    /** Called from the mod's init on the client side only. */
    public static void register() {
        ClientRegistry.registerKeyBinding(LOBBY_KEY);
    }

    @SubscribeEvent
    public static void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (event.getGui() instanceof GuiIngameMenu) {
            event.getButtonList().add(new GuiButton(LOBBY_BUTTON_ID,
                    event.getGui().width / 2 - 100, event.getGui().height / 4 + 168, 200, 20, "Лобби ивента"));
        }
    }

    @SubscribeEvent
    public static void onActionPerformed(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (event.getButton().id == LOBBY_BUTTON_ID) {
            Minecraft.getMinecraft().displayGuiScreen(new GuiLobby(event.getGui()));
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (LOBBY_KEY.isPressed() && mc.currentScreen == null && mc.player != null) {
            mc.displayGuiScreen(new GuiLobby(null));
        }
    }
}

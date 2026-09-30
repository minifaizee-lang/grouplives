package io.github.minifaizeelang.grouplives.client;

import io.github.minifaizeelang.grouplives.GroupLivesMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Adds the "Настройки ивента" button to the Esc menu. Client-only; none of
 * these classes load on a dedicated server.
 */
@Mod.EventBusSubscriber(value = Side.CLIENT, modid = GroupLivesMod.MODID)
public final class ClientEvents {

    private static final int EVENT_SETTINGS_BUTTON_ID = 7321;

    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (event.getGui() instanceof GuiIngameMenu) {
            event.getButtonList().add(new GuiButton(EVENT_SETTINGS_BUTTON_ID,
                    event.getGui().width / 2 - 100, event.getGui().height / 4 + 168, 200, 20, "Настройки ивента"));
        }
    }

    @SubscribeEvent
    public static void onActionPerformed(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (event.getButton().id == EVENT_SETTINGS_BUTTON_ID) {
            Minecraft.getMinecraft().displayGuiScreen(new GuiEventSettings(event.getGui()));
            event.setCanceled(true);
        }
    }
}

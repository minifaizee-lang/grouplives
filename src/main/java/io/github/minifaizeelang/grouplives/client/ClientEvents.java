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
 * Client entry points: two buttons in the Esc menu - the team roster
 * (create/invite/manage) and the teammate teleport menu.
 */
@Mod.EventBusSubscriber(value = Side.CLIENT, modid = GroupLivesMod.MODID)
public final class ClientEvents {

    private static final int TEAM_BUTTON_ID = 7321;
    private static final int TP_BUTTON_ID = 7323;

    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (event.getGui() instanceof GuiIngameMenu) {
            event.getButtonList().add(new GuiButton(TEAM_BUTTON_ID,
                    event.getGui().width / 2 - 100, event.getGui().height / 4 + 150, 200, 16, "Состав команды"));
            event.getButtonList().add(new GuiButton(TP_BUTTON_ID,
                    event.getGui().width / 2 - 100, event.getGui().height / 4 + 168, 200, 16, "Телепорт к товарищу"));
        }
    }

    @SubscribeEvent
    public static void onActionPerformed(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (event.getButton().id == TEAM_BUTTON_ID) {
            event.setCanceled(true);
            Minecraft.getMinecraft().displayGuiScreen(new GuiTeamRoster(event.getGui()));
        } else if (event.getButton().id == TP_BUTTON_ID) {
            event.setCanceled(true);
            Minecraft.getMinecraft().displayGuiScreen(new GuiTeamTeleport(event.getGui()));
        }
    }
}

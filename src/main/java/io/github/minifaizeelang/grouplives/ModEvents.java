package io.github.minifaizeelang.grouplives;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

@Mod.EventBusSubscriber(modid = GroupLivesMod.MODID)
public final class ModEvents {

    private ModEvents() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP) || event.player.world.isRemote) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        LivesManager.onLogin(player.mcServer, player);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().world.isRemote || !(event.getEntity() instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.getEntity();
        MinecraftServer server = player.mcServer;
        int remaining = LivesManager.onDeath(server, player);
        if (remaining <= 0) {
            Msg.broadcast(server, TextFormatting.DARK_RED,
                    player.getName() + " ran out of lives and has been eliminated!");
        } else {
            Msg.send(player, TextFormatting.YELLOW, "You died. Lives remaining: " + remaining);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.player instanceof EntityPlayerMP) || event.player.world.isRemote) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        LivesManager.onRespawn(player.mcServer, player);
    }
}

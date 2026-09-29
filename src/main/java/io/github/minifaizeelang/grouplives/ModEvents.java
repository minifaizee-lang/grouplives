package io.github.minifaizeelang.grouplives;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.ServerChatEvent;
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
        GroupManager.sendAllTabNamesTo(player.mcServer, player);
        GroupManager.updateTabName(player.mcServer, player);
    }

    /**
     * Rebuilds player chat as "[Tag] Nickname: message" with the group color -
     * vanilla 1.12.2 chat does not render team prefixes on its own.
     */
    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        EntityPlayerMP player = event.getPlayer();
        if (player.world.isRemote) {
            return;
        }
        ScorePlayerTeam team = GroupManager.scoreboard(player.mcServer).getPlayersTeam(player.getName());
        if (team == null) {
            return;
        }
        TextFormatting color = team.getColor() == null ? TextFormatting.WHITE : team.getColor();
        TextComponentString tag = new TextComponentString(String.format(ModConfig.groupPrefixFormat, team.getName()));
        tag.getStyle().setColor(color);
        TextComponentString name = new TextComponentString(player.getName());
        name.getStyle().setColor(color);
        TextComponentString line = new TextComponentString("");
        line.appendSibling(tag);
        line.appendSibling(name);
        line.appendSibling(new TextComponentString(": " + event.getMessage()));
        event.setComponent(line);
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

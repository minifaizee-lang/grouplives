package io.github.minifaizeelang.grouplives;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketChat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

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
     * Rebuilds player chat per audience: teammates see "[Tag] <Nickname> msg"
     * (colored tag), everyone else sees "<???> msg" - nicknames are
     * team-private. Players without a group keep the plain vanilla format.
     */
    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        EntityPlayerMP player = event.getPlayer();
        if (player.world.isRemote) {
            return;
        }
        ScorePlayerTeam team = GroupManager.scoreboard(player.mcServer).getPlayersTeam(player.getName());

        TextComponentString teamLine;
        TextComponentString outsiderLine;
        if (team == null) {
            String plain = "<" + player.getName() + "> " + event.getMessage();
            teamLine = new TextComponentString(plain);
            outsiderLine = teamLine;
        } else {
            TextFormatting color = team.getColor() == null ? TextFormatting.WHITE : team.getColor();
            TextComponentString tag = new TextComponentString(String.format(ModConfig.groupPrefixFormat, team.getName()));
            tag.getStyle().setColor(color);
            teamLine = new TextComponentString("");
            teamLine.appendSibling(tag);
            teamLine.appendSibling(new TextComponentString("<" + player.getName() + "> " + event.getMessage()));
            outsiderLine = new TextComponentString(TextFormatting.GRAY + "???"
                    + TextFormatting.RESET + ": " + event.getMessage());
        }

        event.setCanceled(true);
        for (EntityPlayerMP receiver : player.mcServer.getPlayerList().getPlayers()) {
            ITextComponent line = GroupManager.areTeammates(player.mcServer, receiver.getName(), player.getName())
                    ? teamLine : outsiderLine;
            receiver.connection.sendPacket(new SPacketChat(line));
        }
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

    /** Drives the /tpa countdown and enforces the event border. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
            if (server != null) {
                TeleportManager.tick(server);
            }
        }
    }

    /** Taking damage cancels a pending /tpa teleport. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity().world.isRemote || !(event.getEntity() instanceof EntityPlayerMP)) {
            return;
        }
        TeleportManager.cancelIfWarmingUp((EntityPlayerMP) event.getEntity(), "you took damage");
    }

    /** Drops requests and countdowns when someone leaves. */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.player instanceof EntityPlayerMP) || event.player.world.isRemote) {
            return;
        }
        TeleportManager.onLogout((EntityPlayerMP) event.player);
    }
}

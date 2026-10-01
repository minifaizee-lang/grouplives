package io.github.minifaizeelang.grouplives.network;

import io.github.minifaizeelang.grouplives.EventManager;
import io.github.minifaizeelang.grouplives.GroupLivesMod;
import io.github.minifaizeelang.grouplives.ModConfig;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * The mod's single network channel. Only one packet (server -> client event
 * state) is needed; GUI actions travel as regular chat commands.
 */
public final class NetworkHandler {

    public static SimpleNetworkWrapper CHANNEL;

    private NetworkHandler() {
    }

    public static void init() {
        CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(GroupLivesMod.MODID);
        CHANNEL.registerMessage(PacketEventState.class, PacketEventState.class, 0, Side.CLIENT);
        CHANNEL.registerMessage(PacketTeamTasks.class, PacketTeamTasks.class, 1, Side.CLIENT);
    }

    public static void sendEventStateToAll(MinecraftServer server) {
        EventManager.EventStateData data = EventManager.data(server);
        CHANNEL.sendToAll(new PacketEventState(data.started, data.borderEnabled,
                data.borderSize, ModConfig.teamSpacing));
    }

    public static void sendEventStateTo(EntityPlayerMP player) {
        EventManager.EventStateData data = EventManager.data(player.mcServer);
        CHANNEL.sendTo(new PacketEventState(data.started, data.borderEnabled,
                data.borderSize, ModConfig.teamSpacing), player);
    }
}

package io.github.minifaizeelang.grouplives.network;

import io.github.minifaizeelang.grouplives.client.ClientState;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Server -> client sync of the event state (started, border). The handler
 * runs on the client thread and feeds ClientState, which the lobby GUI reads.
 */
public class PacketEventState implements IMessage, IMessageHandler<PacketEventState, IMessage> {

    public boolean started;
    public boolean borderEnabled;
    public int borderSize;
    public int teamSpacing;

    public PacketEventState() {
    }

    public PacketEventState(boolean started, boolean borderEnabled, int borderSize, int teamSpacing) {
        this.started = started;
        this.borderEnabled = borderEnabled;
        this.borderSize = borderSize;
        this.teamSpacing = teamSpacing;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(started);
        buf.writeBoolean(borderEnabled);
        buf.writeInt(borderSize);
        buf.writeInt(teamSpacing);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        started = buf.readBoolean();
        borderEnabled = buf.readBoolean();
        borderSize = buf.readInt();
        teamSpacing = buf.readInt();
    }

    @Override
    public IMessage onMessage(PacketEventState message, MessageContext ctx) {
        if (ctx.side == Side.CLIENT) {
            // Static volatile writes: safe from the network thread, visible to the client thread.
            ClientState.apply(message.started, message.borderEnabled, message.borderSize, message.teamSpacing);
        }
        return null;
    }
}

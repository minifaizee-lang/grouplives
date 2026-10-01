package io.github.minifaizeelang.grouplives.network;

import io.github.minifaizeelang.grouplives.client.ClientState;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> client sync of the whole team task board (only sent to members
 * of that team). A task is either structured (type + item id + amount) or
 * legacy free text (type < 0).
 */
public class PacketTeamTasks implements IMessage, IMessageHandler<PacketTeamTasks, IMessage> {

    public static class Entry {
        public final String player;
        public final int index;
        public final int type;
        public final String itemId;
        public final int amount;
        public final String text;
        public final boolean done;

        public Entry(String player, int index, int type, String itemId, int amount, String text, boolean done) {
            this.player = player;
            this.index = index;
            this.type = type;
            this.itemId = itemId;
            this.amount = amount;
            this.text = text;
            this.done = done;
        }
    }

    public final List<Entry> entries = new ArrayList<Entry>();

    public PacketTeamTasks() {
    }

    public PacketTeamTasks(List<Entry> entries) {
        this.entries.addAll(entries);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeVarInt(buf, entries.size(), 1);
        for (Entry entry : entries) {
            ByteBufUtils.writeUTF8String(buf, entry.player);
            ByteBufUtils.writeVarInt(buf, entry.index, 1);
            buf.writeBoolean(entry.done);
            buf.writeByte(entry.type);
            if (entry.type >= 0) {
                ByteBufUtils.writeUTF8String(buf, entry.itemId);
                ByteBufUtils.writeVarInt(buf, entry.amount, 2);
            } else {
                ByteBufUtils.writeUTF8String(buf, entry.text == null ? "" : entry.text);
            }
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        entries.clear();
        int count = ByteBufUtils.readVarInt(buf, 3);
        for (int i = 0; i < count; i++) {
            String player = ByteBufUtils.readUTF8String(buf);
            int index = ByteBufUtils.readVarInt(buf, 3);
            boolean done = buf.readBoolean();
            int type = buf.readByte();
            if (type >= 0) {
                String itemId = ByteBufUtils.readUTF8String(buf);
                int amount = ByteBufUtils.readVarInt(buf, 2);
                entries.add(new Entry(player, index, type, itemId, amount, null, done));
            } else {
                String text = ByteBufUtils.readUTF8String(buf);
                entries.add(new Entry(player, index, type, null, 0, text, done));
            }
        }
    }

    @Override
    public IMessage onMessage(PacketTeamTasks message, MessageContext ctx) {
        if (ctx.side == Side.CLIENT) {
            List<ClientState.TaskEntry> board = new ArrayList<ClientState.TaskEntry>();
            for (Entry entry : message.entries) {
                board.add(new ClientState.TaskEntry(entry.player, entry.index, entry.type,
                        entry.itemId, entry.amount, entry.text, entry.done));
            }
            ClientState.teamTasks = board;
        }
        return null;
    }
}

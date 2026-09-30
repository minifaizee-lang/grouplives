package io.github.minifaizeelang.grouplives;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketTitle;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /tpa request bookkeeping and the countdown before a teleport happens.
 * Everything is tick-based (20 ticks = 1 second) and server-side only.
 * Teleports are only allowed between players of the same group.
 */
public final class TeleportManager {

    /** Player being teleported -> destination and countdown state. */
    private static class Warmup {
        final UUID targetId;
        final int endTick;
        int lastAnnouncedSecond = -1;

        Warmup(UUID targetId, int endTick) {
            this.targetId = targetId;
            this.endTick = endTick;
        }
    }

    /** Teleport target -> (requesting player -> tick the request expires at). */
    private static final Map<UUID, Map<UUID, Long>> REQUESTS = new HashMap<UUID, Map<UUID, Long>>();
    private static final Map<UUID, Warmup> WARMUPS = new HashMap<UUID, Warmup>();

    private TeleportManager() {
    }

    // ------------------------------------------------------------------
    // /tpa
    // ------------------------------------------------------------------

    public static void request(MinecraftServer server, EntityPlayerMP from, EntityPlayerMP to) {
        purgeExpired(server);
        if (WARMUPS.containsKey(from.getUniqueID())) {
            Msg.send(from, TextFormatting.RED, "You are already teleporting somewhere.");
            return;
        }
        long expiresAt = server.getTickCounter() + 20L * Math.max(1, ModConfig.requestTimeoutSeconds);
        Map<UUID, Long> pending = REQUESTS.get(to.getUniqueID());
        if (pending == null) {
            pending = new HashMap<UUID, Long>();
            REQUESTS.put(to.getUniqueID(), pending);
        }
        pending.put(from.getUniqueID(), expiresAt);
        Msg.send(from, TextFormatting.GREEN, "Request sent to " + to.getName() + " - they have "
                + ModConfig.requestTimeoutSeconds + " seconds to accept.");
        Msg.send(to, TextFormatting.GOLD, from.getName() + " wants to teleport to you.");
        Msg.send(to, "Type /tpaccept to allow or /tpdeny to refuse.");
    }

    // ------------------------------------------------------------------
    // /tpaccept and /tpdeny
    // ------------------------------------------------------------------

    public static void accept(MinecraftServer server, EntityPlayerMP acceptor, String[] args) {
        UUID requesterId = pickRequester(server, acceptor, args);
        if (requesterId == null) {
            return;
        }
        removeRequest(acceptor.getUniqueID(), requesterId);
        EntityPlayerMP requester = server.getPlayerList().getPlayerByUUID(requesterId);
        if (requester == null) {
            Msg.send(acceptor, TextFormatting.RED, "That player is offline now.");
            return;
        }
        if (!GroupManager.areTeammates(server, acceptor.getName(), requester.getName())) {
            Msg.send(acceptor, TextFormatting.RED, "You are no longer in the same group as " + requester.getName() + ".");
            Msg.send(requester, TextFormatting.RED, acceptor.getName() + " accepted, but you are no longer teammates.");
            return;
        }
        if (WARMUPS.containsKey(requesterId)) {
            Msg.send(acceptor, TextFormatting.RED, requester.getName() + " is already teleporting.");
            return;
        }
        int endTick = server.getTickCounter() + 20 * Math.max(0, ModConfig.teleportWarmupSeconds);
        WARMUPS.put(requesterId, new Warmup(acceptor.getUniqueID(), endTick));
        Msg.send(acceptor, TextFormatting.GREEN, "Accepted - " + requester.getName() + " will arrive in "
                + ModConfig.teleportWarmupSeconds + " seconds.");
        Msg.send(requester, TextFormatting.GOLD, "Teleporting to " + acceptor.getName() + " in "
                + ModConfig.teleportWarmupSeconds + " seconds - do not take damage!");
    }

    public static void deny(MinecraftServer server, EntityPlayerMP acceptor, String[] args) {
        UUID requesterId = pickRequester(server, acceptor, args);
        if (requesterId == null) {
            return;
        }
        removeRequest(acceptor.getUniqueID(), requesterId);
        Msg.send(acceptor, TextFormatting.YELLOW, "Request denied.");
        EntityPlayerMP requester = server.getPlayerList().getPlayerByUUID(requesterId);
        if (requester != null) {
            Msg.send(requester, TextFormatting.RED, acceptor.getName() + " denied your teleport request.");
        }
    }

    /** Resolves which pending request the player means: the only one, or the named one. */
    private static UUID pickRequester(MinecraftServer server, EntityPlayerMP acceptor, String[] args) {
        purgeExpired(server);
        Map<UUID, Long> pending = REQUESTS.get(acceptor.getUniqueID());
        if (pending == null || pending.isEmpty()) {
            Msg.send(acceptor, TextFormatting.RED, "You have no teleport requests.");
            return null;
        }
        if (args.length >= 1) {
            EntityPlayerMP requester = server.getPlayerList().getPlayerByUsername(args[0]);
            if (requester == null || !pending.containsKey(requester.getUniqueID())) {
                Msg.send(acceptor, TextFormatting.RED, "No pending request from " + args[0] + ".");
                return null;
            }
            return requester.getUniqueID();
        }
        if (pending.size() == 1) {
            return pending.keySet().iterator().next();
        }
        Msg.send(acceptor, TextFormatting.RED, "You have several requests - use /tpaccept <player>:");
        for (UUID requesterId : pending.keySet()) {
            EntityPlayerMP requester = server.getPlayerList().getPlayerByUUID(requesterId);
            if (requester != null) {
                Msg.send(acceptor, " - " + requester.getName());
            }
        }
        return null;
    }

    private static void removeRequest(UUID acceptorId, UUID requesterId) {
        Map<UUID, Long> pending = REQUESTS.get(acceptorId);
        if (pending != null) {
            pending.remove(requesterId);
            if (pending.isEmpty()) {
                REQUESTS.remove(acceptorId);
            }
        }
    }

    private static void purgeExpired(MinecraftServer server) {
        int now = server.getTickCounter();
        Iterator<Map.Entry<UUID, Map<UUID, Long>>> it = REQUESTS.entrySet().iterator();
        while (it.hasNext()) {
            Map<UUID, Long> pending = it.next().getValue();
            Iterator<Long> expiries = pending.values().iterator();
            while (expiries.hasNext()) {
                if (expiries.next() <= now) {
                    expiries.remove();
                }
            }
            if (pending.isEmpty()) {
                it.remove();
            }
        }
    }

    /** Online players that currently have a pending request to this player (for tab completion). */
    public static List<String> pendingRequesterNames(MinecraftServer server, EntityPlayerMP acceptor) {
        purgeExpired(server);
        List<String> out = new ArrayList<String>();
        Map<UUID, Long> pending = REQUESTS.get(acceptor.getUniqueID());
        if (pending != null) {
            for (UUID requesterId : pending.keySet()) {
                EntityPlayerMP requester = server.getPlayerList().getPlayerByUUID(requesterId);
                if (requester != null) {
                    out.add(requester.getName());
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Countdown and cancellation
    // ------------------------------------------------------------------

    /** Called once per server tick from ModEvents. */
    public static void tick(MinecraftServer server) {
        if (WARMUPS.isEmpty()) {
            return;
        }
        int now = server.getTickCounter();
        Iterator<Map.Entry<UUID, Warmup>> it = WARMUPS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Warmup> entry = it.next();
            EntityPlayerMP teleporting = server.getPlayerList().getPlayerByUUID(entry.getKey());
            if (teleporting == null) {
                it.remove();
                continue;
            }
            Warmup warmup = entry.getValue();
            if (now >= warmup.endTick) {
                it.remove();
                finishTeleport(server, teleporting, warmup);
            } else {
                int remaining = (int) Math.ceil((warmup.endTick - now) / 20.0);
                if (remaining != warmup.lastAnnouncedSecond) {
                    warmup.lastAnnouncedSecond = remaining;
                    teleporting.connection.sendPacket(new SPacketTitle(SPacketTitle.Type.ACTIONBAR,
                            new TextComponentString(TextFormatting.GOLD + "Teleporting in " + remaining + "...")));
                }
            }
        }
    }

    private static void finishTeleport(MinecraftServer server, EntityPlayerMP teleporting, Warmup warmup) {
        EntityPlayerMP target = server.getPlayerList().getPlayerByUUID(warmup.targetId);
        if (target == null) {
            Msg.send(teleporting, TextFormatting.RED, "Teleport cancelled - the player went offline.");
            return;
        }
        if (!GroupManager.areTeammates(server, teleporting.getName(), target.getName())) {
            Msg.send(teleporting, TextFormatting.RED, "Teleport cancelled - you are no longer in the same group.");
            return;
        }
        if (teleporting.dimension != target.dimension) {
            server.getPlayerList().transferPlayerToDimension(teleporting, target.dimension,
                    server.getWorld(target.dimension).getDefaultTeleporter());
        }
        teleporting.connection.setPlayerLocation(target.posX, target.posY, target.posZ,
                target.rotationYaw, target.rotationPitch);
        Msg.send(teleporting, TextFormatting.GREEN, "Teleported to " + target.getName() + ".");
    }

    /** Cancels the countdown if this player is the one teleporting (e.g. they took damage). */
    public static void cancelIfWarmingUp(EntityPlayerMP player, String reason) {
        if (WARMUPS.remove(player.getUniqueID()) != null) {
            Msg.send(player, TextFormatting.RED, "Teleport cancelled - " + reason + ".");
        }
    }

    /** Cleans up requests and countdowns when someone leaves. */
    public static void onLogout(EntityPlayerMP player) {
        WARMUPS.remove(player.getUniqueID());
        REQUESTS.remove(player.getUniqueID());
        for (Map<UUID, Long> pending : REQUESTS.values()) {
            pending.remove(player.getUniqueID());
        }
    }
}

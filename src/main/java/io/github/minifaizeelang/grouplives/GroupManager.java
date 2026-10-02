package io.github.minifaizeelang.grouplives;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketPlayerListItem;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Groups are stored as vanilla scoreboard teams. The prefix appears above
 * heads (nametag) and in the tab list fallback; the tab list itself is driven
 * by custom display-name packets so tag and color are not limited by the
 * team-prefix packet size.
 */
public final class GroupManager {

    /**
     * The team prefix travels in a packet field capped at 16 UTF-8 bytes
     * (every section-sign code costs two bytes). Anything longer makes the
     * packet write throw and the team silently stays unstyled client-side.
     */
    private static final int PREFIX_BYTE_LIMIT = 16;

    private static final Pattern VALID_NAME = Pattern.compile("^[\\p{L}\\p{N}_.|+\\-/]{1,16}$");

    private GroupManager() {
    }

    public static boolean isValidName(String name) {
        return VALID_NAME.matcher(name).matches();
    }

    public static Scoreboard scoreboard(MinecraftServer server) {
        return server.getEntityWorld().getScoreboard();
    }

    /** @return the new team, or null if a group with that name already exists. */
    public static ScorePlayerTeam create(MinecraftServer server, String name, TextFormatting color) {
        Scoreboard sb = scoreboard(server);
        if (sb.getTeam(name) != null) {
            return null;
        }
        ScorePlayerTeam team = sb.createTeam(name);
        applyStyle(server, team, name, color);
        return team;
    }

    /**
     * Order matters: setColor does not broadcast anything on its own, but
     * setPrefix broadcasts the whole team state - so the color has to be set
     * first to reach clients in the same packet. Nametags are hidden from
     * other teams: nicknames are team-private.
     */
    public static void applyStyle(MinecraftServer server, ScorePlayerTeam team, String name, TextFormatting color) {
        TextFormatting actual = color == null ? TextFormatting.WHITE : color;
        team.setColor(actual);
        team.setPrefix(fitPrefix(actual, name));
        team.setNameTagVisibility(Team.EnumVisible.HIDE_FOR_OTHER_TEAMS);
        updateTabNames(server, team);
    }

    /**
     * Builds a self-contained prefix (color + tag + reset) that always fits
     * the 16-byte packet limit; longer group names shrink step by step: full
     * format, bare [tag], bare tag, color only. Because the prefix ends with
     * a reset, the nickname after it stays white and the tag shows up colored
     * everywhere vanilla uses the display name (nametag, achievements, death
     * messages).
     */
    private static String fitPrefix(TextFormatting color, String tag) {
        String code = color.toString();
        String reset = TextFormatting.RESET.toString();
        String[] candidates = new String[]{
                code + String.format(ModConfig.groupPrefixFormat, tag) + reset,
                code + "[" + tag + "]" + reset,
                code + tag + reset,
        };
        for (String candidate : candidates) {
            if (candidate.getBytes(StandardCharsets.UTF_8).length <= PREFIX_BYTE_LIMIT) {
                return candidate;
            }
        }
        return code;
    }

    /** @return true if the group existed and was removed. */
    public static boolean delete(MinecraftServer server, String name) {
        Scoreboard sb = scoreboard(server);
        ScorePlayerTeam team = sb.getTeam(name);
        if (team == null) {
            return false;
        }
        List<String> members = new ArrayList<String>(team.getMembershipCollection());
        sb.removeTeam(team);
        for (String member : members) {
            updateTabName(server, member);
        }
        return true;
    }

    /**
     * Puts a player into a group (removes them from their previous group,
     * vanilla behaviour). @return false if the group does not exist.
     */
    public static boolean join(MinecraftServer server, String playerName, String groupName) {
        boolean joined = scoreboard(server).addPlayerToTeam(playerName, groupName);
        if (joined) {
            updateTabName(server, playerName);
        }
        return joined;
    }

    /** @return true if the player was in a group and has left it. */
    public static boolean leave(MinecraftServer server, String playerName) {
        Scoreboard sb = scoreboard(server);
        ScorePlayerTeam team = sb.getPlayersTeam(playerName);
        if (team == null) {
            return false;
        }
        sb.removePlayerFromTeam(playerName, team);
        updateTabName(server, playerName);
        return true;
    }

    public static List<String> groupNames(MinecraftServer server) {
        List<String> out = new ArrayList<String>();
        for (ScorePlayerTeam team : scoreboard(server).getTeams()) {
            out.add(team.getName());
        }
        return out;
    }

    /** True if both players are online members of the same group. */
    public static boolean areTeammates(MinecraftServer server, String playerA, String playerB) {
        Scoreboard sb = scoreboard(server);
        ScorePlayerTeam teamA = sb.getPlayersTeam(playerA);
        ScorePlayerTeam teamB = sb.getPlayersTeam(playerB);
        return teamA != null && teamA.isSameTeam(teamB);
    }

    /** Re-applies the current prefix format to every existing group (called at server start; heals data from older versions). */
    public static void reapplyAllStyles(MinecraftServer server) {
        for (ScorePlayerTeam team : scoreboard(server).getTeams()) {
            applyStyle(server, team, team.getName(), team.getColor());
        }
    }

    // ------------------------------------------------------------------
    // Tab list display names
    //
    // The "action" and "players" fields on SPacketPlayerListItem are private,
    // and access transformers shipped in the mod jar are not applied by every
    // 1.12.2 runtime, so they are resolved by reflection under both names
    // they can carry: "action"/"players" in a development environment and
    // "field_179770_a"/"field_179769_b" in a production one.
    // ------------------------------------------------------------------

    private static final Field TAB_PACKET_ACTION = findPacketField("action", "field_179770_a");
    private static final Field TAB_PACKET_ENTRIES = findPacketField("players", "field_179769_b");
    private static boolean tabPacketWarned = false;

    private static Field findPacketField(String devName, String runtimeName) {
        for (String name : new String[]{devName, runtimeName}) {
            try {
                Field field = SPacketPlayerListItem.class.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    /**
     * Tab-list name, per viewer: teammates see "[TAG] Nickname", everyone
     * else sees "???" - nicknames are team-private. Players without a group
     * show their plain name.
     */
    public static ITextComponent buildTabName(MinecraftServer server, EntityPlayerMP viewer, EntityPlayerMP target) {
        Scoreboard sb = scoreboard(server);
        ScorePlayerTeam viewerTeam = sb.getPlayersTeam(viewer.getName());
        ScorePlayerTeam targetTeam = sb.getPlayersTeam(target.getName());
        TextComponentString line = new TextComponentString("");
        if (targetTeam == null) {
            line.appendSibling(new TextComponentString(target.getName()));
            return line;
        }
        TextFormatting color = targetTeam.getColor() == null ? TextFormatting.WHITE : targetTeam.getColor();
        if (viewerTeam != null && viewerTeam.isSameTeam(targetTeam)) {
            TextComponentString tag = new TextComponentString(String.format(ModConfig.groupPrefixFormat, targetTeam.getName()));
            tag.getStyle().setColor(color);
            line.appendSibling(tag);
            line.appendSibling(new TextComponentString(target.getName()));
        } else {
            line.appendSibling(new TextComponentString(TextFormatting.GRAY + "???"));
        }
        return line;
    }

    /** Tab entries are visible only to teammates, teamless players and the player themselves. */
    private static boolean tabVisibleTo(MinecraftServer server, EntityPlayerMP receiver, EntityPlayerMP target) {
        if (receiver == target) {
            return true;
        }
        Scoreboard sb = scoreboard(server);
        ScorePlayerTeam receiverTeam = sb.getPlayersTeam(receiver.getName());
        ScorePlayerTeam targetTeam = sb.getPlayersTeam(target.getName());
        return targetTeam == null || (receiverTeam != null && receiverTeam.isSameTeam(targetTeam));
    }

    /**
     * Pushes one player's tab entry per receiver: teammates get the tagged
     * nickname, everyone else gets the entry REMOVED from their tab list
     * entirely - nicknames of other teams are not shown at all.
     */
    public static void updateTabName(MinecraftServer server, EntityPlayerMP target) {
        for (EntityPlayerMP receiver : server.getPlayerList().getPlayers()) {
            SPacketPlayerListItem packet = buildTabPacket(server, receiver, target);
            if (packet != null) {
                receiver.connection.sendPacket(packet);
            }
        }
    }

    /** Pushes the whole (per-receiver filtered) tab list to one player (used right after login). */
    public static void sendAllTabNamesTo(MinecraftServer server, EntityPlayerMP recipient) {
        if (TAB_PACKET_ACTION == null || TAB_PACKET_ENTRIES == null) {
            warnOnce();
            return;
        }
        try {
            SPacketPlayerListItem show = new SPacketPlayerListItem();
            TAB_PACKET_ACTION.set(show, SPacketPlayerListItem.Action.UPDATE_DISPLAY_NAME);
            @SuppressWarnings("unchecked")
            List<SPacketPlayerListItem.AddPlayerData> showEntries =
                    (List<SPacketPlayerListItem.AddPlayerData>) TAB_PACKET_ENTRIES.get(show);
            SPacketPlayerListItem hide = new SPacketPlayerListItem();
            TAB_PACKET_ACTION.set(hide, SPacketPlayerListItem.Action.REMOVE_PLAYER);
            @SuppressWarnings("unchecked")
            List<SPacketPlayerListItem.AddPlayerData> hideEntries =
                    (List<SPacketPlayerListItem.AddPlayerData>) TAB_PACKET_ENTRIES.get(hide);

            for (EntityPlayerMP online : server.getPlayerList().getPlayers()) {
                if (tabVisibleTo(server, recipient, online)) {
                    showEntries.add(show.new AddPlayerData(
                            online.getGameProfile(),
                            online.ping,
                            online.interactionManager.getGameType(),
                            buildTabName(server, recipient, online)));
                } else {
                    hideEntries.add(hide.new AddPlayerData(online.getGameProfile(), 0, null, null));
                }
            }
            recipient.connection.sendPacket(show);
            if (!hideEntries.isEmpty()) {
                recipient.connection.sendPacket(hide);
            }
        } catch (Throwable t) {
            GroupLivesMod.log().warn("Failed to send tab list display names", t);
        }
    }

    /** Re-sends every online player's filtered tab list (after team changes). */
    public static void resyncAllTabNames(MinecraftServer server) {
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            sendAllTabNamesTo(server, player);
        }
    }

    private static SPacketPlayerListItem buildTabPacket(MinecraftServer server, EntityPlayerMP receiver, EntityPlayerMP target) {
        if (TAB_PACKET_ACTION == null || TAB_PACKET_ENTRIES == null) {
            warnOnce();
            return null;
        }
        try {
            SPacketPlayerListItem packet = new SPacketPlayerListItem();
            @SuppressWarnings("unchecked")
            List<SPacketPlayerListItem.AddPlayerData> entries =
                    (List<SPacketPlayerListItem.AddPlayerData>) TAB_PACKET_ENTRIES.get(packet);
            if (tabVisibleTo(server, receiver, target)) {
                TAB_PACKET_ACTION.set(packet, SPacketPlayerListItem.Action.UPDATE_DISPLAY_NAME);
                entries.add(packet.new AddPlayerData(
                        target.getGameProfile(),
                        target.ping,
                        target.interactionManager.getGameType(),
                        buildTabName(server, receiver, target)));
            } else {
                TAB_PACKET_ACTION.set(packet, SPacketPlayerListItem.Action.REMOVE_PLAYER);
                entries.add(packet.new AddPlayerData(target.getGameProfile(), 0, null, null));
            }
            return packet;
        } catch (Throwable t) {
            GroupLivesMod.log().warn("Failed to build tab list display name packet", t);
            return null;
        }
    }

    private static void warnOnce() {
        if (!tabPacketWarned) {
            tabPacketWarned = true;
            GroupLivesMod.log().warn("Cannot customize tab list names: SPacketPlayerListItem fields unavailable. "
                    + "Falling back to plain team prefixes.");
        }
    }

    /** Pushes one player's custom tab entry, looked up by name (offline players are skipped). */
    public static void updateTabName(MinecraftServer server, String playerName) {
        EntityPlayerMP player = server.getPlayerList().getPlayerByUsername(playerName);
        if (player != null) {
            updateTabName(server, player);
        }
    }

    private static void updateTabNames(MinecraftServer server, ScorePlayerTeam team) {
        for (String member : team.getMembershipCollection()) {
            updateTabName(server, member);
        }
    }
}

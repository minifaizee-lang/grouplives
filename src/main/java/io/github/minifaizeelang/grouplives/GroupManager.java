package io.github.minifaizeelang.grouplives;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketPlayerListItem;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

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

    private static final Pattern VALID_NAME = Pattern.compile("^[A-Za-z0-9_-]{1,16}$");

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
     * first to reach clients in the same packet.
     */
    public static void applyStyle(MinecraftServer server, ScorePlayerTeam team, String name, TextFormatting color) {
        TextFormatting actual = color == null ? TextFormatting.WHITE : color;
        team.setColor(actual);
        team.setPrefix(fitPrefix(actual, name));
        team.setSuffix(TextFormatting.RESET.toString());
        updateTabNames(server, team);
    }

    /**
     * Builds a prefix that always fits the packet limit. The trailing reset
     * code is deliberately omitted so the color "leaks" onto the nickname -
     * that is how vanilla renders team-colored names in the tab list and
     * nametags. The reset is placed in the suffix instead.
     */
    private static String fitPrefix(TextFormatting color, String tag) {
        String code = color.toString();
        String[] candidates = new String[]{
                code + String.format(ModConfig.groupPrefixFormat, tag),
                code + "[" + tag + "]",
                code + tag,
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

    // ------------------------------------------------------------------
    // Tab list display names
    // ------------------------------------------------------------------

    /** Group tag followed by the nickname, both in the team color. */
    public static ITextComponent buildTabName(MinecraftServer server, EntityPlayerMP player) {
        ScorePlayerTeam team = scoreboard(server).getPlayersTeam(player.getName());
        TextComponentString line = new TextComponentString("");
        if (team != null) {
            TextFormatting color = team.getColor() == null ? TextFormatting.WHITE : team.getColor();
            TextComponentString tag = new TextComponentString(String.format(ModConfig.groupPrefixFormat, team.getName()));
            tag.getStyle().setColor(color);
            line.appendSibling(tag);
            TextComponentString name = new TextComponentString(player.getName());
            name.getStyle().setColor(color);
            line.appendSibling(name);
        } else {
            line.appendSibling(new TextComponentString(player.getName()));
        }
        return line;
    }

    /** Pushes one player's custom tab entry to everyone online. */
    public static void updateTabName(MinecraftServer server, String playerName) {
        EntityPlayerMP player = server.getPlayerList().getPlayerByUsername(playerName);
        if (player != null) {
            updateTabName(server, player);
        }
    }

    public static void updateTabName(MinecraftServer server, EntityPlayerMP player) {
        SPacketPlayerListItem packet = new SPacketPlayerListItem();
        packet.action = SPacketPlayerListItem.Action.UPDATE_DISPLAY_NAME;
        packet.players.add(packet.new AddPlayerData(
                player.getGameProfile(),
                player.ping,
                player.interactionManager.getGameType(),
                buildTabName(server, player)));
        server.getPlayerList().sendPacketToAllPlayers(packet);
    }

    /** Pushes every online player's custom tab entry to one player (used right after login). */
    public static void sendAllTabNamesTo(MinecraftServer server, EntityPlayerMP recipient) {
        SPacketPlayerListItem packet = new SPacketPlayerListItem();
        packet.action = SPacketPlayerListItem.Action.UPDATE_DISPLAY_NAME;
        for (EntityPlayerMP online : server.getPlayerList().getPlayers()) {
            packet.players.add(packet.new AddPlayerData(
                    online.getGameProfile(),
                    online.ping,
                    online.interactionManager.getGameType(),
                    buildTabName(server, online)));
        }
        recipient.connection.sendPacket(packet);
    }

    private static void updateTabNames(MinecraftServer server, ScorePlayerTeam team) {
        for (String member : team.getMembershipCollection()) {
            updateTabName(server, member);
        }
    }
}

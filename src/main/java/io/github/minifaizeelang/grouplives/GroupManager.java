package io.github.minifaizeelang.grouplives;

import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextFormatting;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Groups are stored as vanilla scoreboard teams. That is what makes the
 * prefix appear in the tab list, above heads and in chat without any
 * client-side code.
 */
public final class GroupManager {

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
        applyStyle(team, name, color);
        return team;
    }

    public static void applyStyle(ScorePlayerTeam team, String name, TextFormatting color) {
        TextFormatting actual = color == null ? TextFormatting.WHITE : color;
        String prefix = actual.toString() + String.format(ModConfig.groupPrefixFormat, name) + TextFormatting.RESET;
        team.setPrefix(prefix);
        team.setColor(actual);
    }

    /** @return true if the group existed and was removed. */
    public static boolean delete(MinecraftServer server, String name) {
        Scoreboard sb = scoreboard(server);
        ScorePlayerTeam team = sb.getTeam(name);
        if (team == null) {
            return false;
        }
        sb.removeTeam(team);
        return true;
    }

    /**
     * Puts a player into a group (removes them from their previous group,
     * vanilla behaviour). @return false if the group does not exist.
     */
    public static boolean join(MinecraftServer server, String playerName, String groupName) {
        return scoreboard(server).addPlayerToTeam(playerName, groupName);
    }

    /** @return true if the player was in a group and has left it. */
    public static boolean leave(MinecraftServer server, String playerName) {
        Scoreboard sb = scoreboard(server);
        ScorePlayerTeam team = sb.getPlayersTeam(playerName);
        if (team == null) {
            return false;
        }
        sb.removePlayerFromTeam(playerName, team);
        return true;
    }

    public static List<String> groupNames(MinecraftServer server) {
        List<String> out = new ArrayList<String>();
        for (ScorePlayerTeam team : scoreboard(server).getTeams()) {
            out.add(team.getName());
        }
        return out;
    }
}

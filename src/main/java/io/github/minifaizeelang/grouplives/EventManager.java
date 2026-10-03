package io.github.minifaizeelang.grouplives;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.WorldServer;
import net.minecraft.world.border.WorldBorder;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Event start: scatters all teams across the world within the VANILLA world
 * border (set by the admin with /worldborder) and gives each player a respawn
 * point at their drop location. No custom border code - the border is entirely
 * vanilla /worldborder territory.
 */
public final class EventManager {

    /** Ring offsets so teleported teammates stand next to each other, not inside each other. */
    private static final int[][] MEMBER_OFFSETS = {
            {0, 0}, {3, 0}, {-3, 0}, {0, 3}, {0, -3},
            {3, 3}, {-3, 3}, {3, -3}, {-3, -3},
            {6, 0}, {-6, 0}, {0, 6}, {0, -6},
            {6, 6}, {-6, 6}, {6, -6}, {-6, -6},
    };

    /** Scatter area cap when the vanilla border is at its huge default size. */
    private static final double MAX_HALF = 10000;

    private EventManager() {
    }

    /** Teams (groups) that have at least one online member. */
    public static List<ScorePlayerTeam> activeTeams(MinecraftServer server) {
        List<ScorePlayerTeam> teams = new ArrayList<ScorePlayerTeam>();
        for (ScorePlayerTeam team : GroupManager.scoreboard(server).getTeams()) {
            for (String member : team.getMembershipCollection()) {
                if (server.getPlayerList().getPlayerByUsername(member) != null) {
                    teams.add(team);
                    break;
                }
            }
        }
        return teams;
    }

    /** Scatter half-extent: the vanilla border, capped for huge default borders. */
    private static double halfExtent(WorldBorder border) {
        return Math.min(border.getSize() / 2.0, MAX_HALF);
    }

    public static void startEvent(MinecraftServer server, ICommandSender feedbackTo, int spacing) {
        WorldServer world = server.getWorld(0);
        WorldBorder border = world.getWorldBorder();
        double centerX = border.getCenterX();
        double centerZ = border.getCenterZ();
        double half = halfExtent(border);

        List<ScorePlayerTeam> teams = activeTeams(server);
        if (teams.isEmpty()) {
            Msg.send(feedbackTo, TextFormatting.RED, "No groups with online players found - nothing to scatter.");
            return;
        }

        int margin = Math.max(100, (int) (half / 20));
        double minX = centerX - half + margin;
        double maxX = centerX + half - margin;
        double minZ = centerZ - half + margin;
        double maxZ = centerZ + half - margin;
        Random rand = new Random();

        // Pick one point per team, respecting the minimum spacing between teams.
        double[][] points = new double[teams.size()][];
        for (int i = 0; i < teams.size(); i++) {
            double[] picked = null;
            for (int attempt = 0; attempt < 400 && picked == null; attempt++) {
                double x = minX + rand.nextDouble() * (maxX - minX);
                double z = minZ + rand.nextDouble() * (maxZ - minZ);
                if (spacing > 0) {
                    boolean tooClose = false;
                    for (int j = 0; j < i; j++) {
                        double dx = points[j][0] - x;
                        double dz = points[j][1] - z;
                        if (dx * dx + dz * dz < (double) spacing * spacing) {
                            tooClose = true;
                            break;
                        }
                    }
                    if (tooClose) {
                        continue;
                    }
                }
                picked = new double[]{x, z};
            }
            if (picked == null) {
                // Too many teams for this spacing on this border - give up on spacing.
                picked = new double[]{minX + rand.nextDouble() * (maxX - minX), minZ + rand.nextDouble() * (maxZ - minZ)};
            }
            points[i] = picked;
        }

        Msg.broadcast(server, TextFormatting.GOLD, "Ивент начался! Команды разбросаны по миру. Команд: " + teams.size() + ".");

        StringBuilder report = new StringBuilder();
        for (int i = 0; i < teams.size(); i++) {
            ScorePlayerTeam team = teams.get(i);
            double x = points[i][0];
            double z = points[i][1];

            // Ground check: shift east if the spot is ocean/lava so teams do not spawn in water.
            BlockPos top = world.getTopSolidOrLiquidBlock(new BlockPos(x, 1, z));
            int tries = 0;
            while (tries < 8 && world.getBlockState(top).getMaterial().isLiquid()) {
                x += 512;
                if (x > maxX) {
                    x = minX + (x - maxX);
                }
                top = world.getTopSolidOrLiquidBlock(new BlockPos(x, 1, z));
                tries++;
            }
            int y = top.getY() + 1;

            List<EntityPlayerMP> members = new ArrayList<EntityPlayerMP>();
            for (String member : team.getMembershipCollection()) {
                EntityPlayerMP player = server.getPlayerList().getPlayerByUsername(member);
                if (player != null) {
                    members.add(player);
                }
            }
            for (int m = 0; m < members.size(); m++) {
                EntityPlayerMP player = members.get(m);
                int[] offset = MEMBER_OFFSETS[m % MEMBER_OFFSETS.length];
                if (player.dimension != 0) {
                    server.getPlayerList().transferPlayerToDimension(player, 0, world.getDefaultTeleporter());
                }
                player.connection.setPlayerLocation(x + offset[0], y, z + offset[1], rand.nextFloat() * 360.0F, 0.0F);
                // Respawn point follows the drop location, so death returns the player to the team base
                player.setSpawnPoint(new BlockPos(x + offset[0], y, z + offset[1]), true);
            }

            report.append(team.getName()).append(" -> ").append((int) x).append(", ").append((int) z)
                    .append(" (").append(members.size()).append(" players); ");
        }
        Msg.send(feedbackTo, TextFormatting.GREEN, "Teams scattered: " + report);
    }
}

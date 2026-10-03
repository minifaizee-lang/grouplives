package io.github.minifaizeelang.grouplives;

import io.github.minifaizeelang.grouplives.network.NetworkHandler;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketTitle;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.WorldServer;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.storage.WorldSavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Event management: a mod-owned square border (not the vanilla world border),
 * team scattering on start, and the started/lobby-phase state that is synced
 * to clients. The border is enforced server-side every tick by clamping
 * players back inside. All of it runs in the overworld.
 */
public final class EventManager {

    /** Ring offsets so teleported teammates stand next to each other, not inside each other. */
    private static final int[][] MEMBER_OFFSETS = {
            {0, 0}, {3, 0}, {-3, 0}, {0, 3}, {0, -3},
            {3, 3}, {-3, 3}, {3, -3}, {-3, -3},
            {6, 0}, {-6, 0}, {0, 6}, {0, -6},
            {6, 6}, {-6, 6}, {6, -6}, {-6, -6},
    };

    /** Anything below this size is a leftover border from older mod versions and gets cleared. */
    private static final int VANILLA_BORDER_CLEARED_SIZE = 59999968;

    private EventManager() {
    }

    public static class EventStateData extends WorldSavedData {

        public static final String DATA_NAME = "grouplives_event";

        public boolean borderEnabled;
        public boolean started;
        public int borderSize = ModConfig.eventBorderSize;
        public double centerX;
        public double centerZ;

        public EventStateData(String name) {
            super(name);
        }

        public EventStateData() {
            super(DATA_NAME);
            centerX = 0.5;
            centerZ = 0.5;
        }

        public double minX() {
            return centerX - borderSize / 2.0;
        }

        public double maxX() {
            return centerX + borderSize / 2.0;
        }

        public double minZ() {
            return centerZ - borderSize / 2.0;
        }

        public double maxZ() {
            return centerZ + borderSize / 2.0;
        }

        @Override
        public void readFromNBT(NBTTagCompound nbt) {
            borderEnabled = nbt.getBoolean("BorderEnabled");
            started = nbt.getBoolean("Started");
            borderSize = nbt.hasKey("Size") ? nbt.getInteger("Size") : ModConfig.eventBorderSize;
            centerX = nbt.getDouble("CenterX");
            centerZ = nbt.getDouble("CenterZ");
            if (!nbt.hasKey("CV")) {
                // Migration: versions <= 0.4.0 centered the border on the world spawn;
                // the border is measured from the zero coordinates now.
                centerX = 0.5;
                centerZ = 0.5;
                markDirty();
            }
        }

        @Override
        public NBTTagCompound writeToNBT(NBTTagCompound compound) {
            compound.setBoolean("BorderEnabled", borderEnabled);
            compound.setBoolean("Started", started);
            compound.setInteger("Size", borderSize);
            compound.setDouble("CenterX", centerX);
            compound.setDouble("CenterZ", centerZ);
            compound.setInteger("CV", 2);
            return compound;
        }
    }

    public static EventStateData data(MinecraftServer server) {
        EventStateData data = (EventStateData) server.getEntityWorld().getMapStorage()
                .getOrLoadData(EventStateData.class, EventStateData.DATA_NAME);
        if (data == null) {
            data = new EventStateData();
            server.getEntityWorld().getMapStorage().setData(EventStateData.DATA_NAME, data);
        }
        return data;
    }

    /** Older versions (<= 0.2.x) used the vanilla world border; undo any leftover limit. */
    private static void clearVanillaBorder(MinecraftServer server) {
        WorldBorder vanilla = server.getWorld(0).getWorldBorder();
        if (vanilla.getSize() < 59000000) {
            vanilla.setSize(VANILLA_BORDER_CLEARED_SIZE);
        }
    }

    /**
     * Called once at server start: wipes any leftover vanilla world border so
     * a border disabled in the mod can never be shadowed by an invisible
     * vanilla one from older versions or manual /worldborder use.
     */
    public static void onServerStart(MinecraftServer server) {
        clearVanillaBorder(server);
    }

    public static void setBorder(MinecraftServer server, int size, boolean enabled) {
        EventStateData data = data(server);
        data.borderSize = size;
        data.borderEnabled = enabled;
        data.markDirty();
        clearVanillaBorder(server);
        NetworkHandler.sendEventStateToAll(server);
    }

    public static void toggleBorder(MinecraftServer server, boolean enabled) {
        EventStateData data = data(server);
        data.borderEnabled = enabled;
        data.markDirty();
        NetworkHandler.sendEventStateToAll(server);
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

    public static void startEvent(MinecraftServer server, ICommandSender feedbackTo, int borderSize, int spacing) {
        EventStateData data = data(server);
        data.borderSize = borderSize;
        data.borderEnabled = true;
        clearVanillaBorder(server);

        WorldServer world = server.getWorld(0);
        List<ScorePlayerTeam> teams = activeTeams(server);
        if (teams.isEmpty()) {
            Msg.send(feedbackTo, TextFormatting.RED, "No groups with online players found - nothing to scatter.");
            return;
        }

        int margin = Math.max(100, borderSize / 20);
        double minX = data.minX() + margin;
        double maxX = data.maxX() - margin;
        double minZ = data.minZ() + margin;
        double maxZ = data.maxZ() - margin;
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

        data.started = true;
        data.markDirty();

        Msg.broadcast(server, TextFormatting.GOLD, "Ивент начался! Граница мира: "
                + borderSize + " x " + borderSize + ". Команд разбросано: " + teams.size() + ".");

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
        NetworkHandler.sendEventStateToAll(server);
    }

    /** Enforces the mod-owned border; called once per server tick. */
    public static void tick(MinecraftServer server) {
        EventStateData data = data(server);
        if (!data.borderEnabled) {
            return;
        }
        double minX = data.minX() + 1;
        double maxX = data.maxX() - 1;
        double minZ = data.minZ() + 1;
        double maxZ = data.maxZ() - 1;
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            if (player.dimension != 0) {
                continue;
            }
            double clampedX = clamp(player.posX, minX, maxX);
            double clampedZ = clamp(player.posZ, minZ, maxZ);
            if (clampedX != player.posX || clampedZ != player.posZ) {
                player.connection.setPlayerLocation(clampedX, player.posY, clampedZ,
                        player.rotationYaw, player.rotationPitch);
                player.connection.sendPacket(new SPacketTitle(SPacketTitle.Type.ACTIONBAR,
                        new TextComponentString(TextFormatting.RED + "Вы достигли границы мира!")));
            }
        }
    }

    private static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }
}

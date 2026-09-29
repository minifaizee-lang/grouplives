package io.github.minifaizeelang.grouplives;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.scoreboard.IScoreCriteria;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks each player's remaining lives in the world save (survives restarts)
 * and mirrors the numbers into a "lives" scoreboard objective so they show
 * up in the tab list.
 */
public final class LivesManager {

    public static final String OBJECTIVE_NAME = "lives";

    private LivesManager() {
    }

    public static class LivesData extends WorldSavedData {

        public static final String DATA_NAME = "grouplives_lives";

        private final Map<UUID, Integer> lives = new HashMap<UUID, Integer>();

        public LivesData(String name) {
            super(name);
        }

        public LivesData() {
            super(DATA_NAME);
        }

        public boolean has(UUID id) {
            return lives.containsKey(id);
        }

        /** Players without an entry are treated as having a fresh set of lives. */
        public int get(UUID id) {
            Integer value = lives.get(id);
            return value == null ? ModConfig.maxLives : value;
        }

        public void set(UUID id, int value) {
            lives.put(id, Math.max(0, value));
            markDirty();
        }

        public void remove(UUID id) {
            lives.remove(id);
            markDirty();
        }

        public Map<UUID, Integer> all() {
            return lives;
        }

        @Override
        public void readFromNBT(NBTTagCompound nbt) {
            lives.clear();
            for (String key : nbt.getKeySet()) {
                try {
                    lives.put(UUID.fromString(key), nbt.getInteger(key));
                } catch (IllegalArgumentException ignored) {
                    GroupLivesMod.log().warn("Skipping corrupt lives entry: " + key);
                }
            }
        }

        @Override
        public NBTTagCompound writeToNBT(NBTTagCompound compound) {
            for (Map.Entry<UUID, Integer> entry : lives.entrySet()) {
                compound.setInteger(entry.getKey().toString(), entry.getValue());
            }
            return compound;
        }
    }

    public static LivesData data(MinecraftServer server) {
        World overworld = server.getEntityWorld();
        LivesData data = (LivesData) overworld.getMapStorage().getOrLoadData(LivesData.class, LivesData.DATA_NAME);
        if (data == null) {
            data = new LivesData();
            overworld.getMapStorage().setData(LivesData.DATA_NAME, data);
        }
        return data;
    }

    // ------------------------------------------------------------------
    // Hooks called from ModEvents
    // ------------------------------------------------------------------

    public static void onLogin(MinecraftServer server, EntityPlayerMP player) {
        LivesData data = data(server);
        UUID id = player.getUniqueID();
        if (!data.has(id)) {
            data.set(id, ModConfig.maxLives);
        }
        refreshTab(server);
        if (data.get(id) <= 0 && "ban".equalsIgnoreCase(ModConfig.eliminationMode)) {
            player.connection.disconnect(Msg.text(TextFormatting.DARK_RED,
                    "You have no lives left. Ask an operator to revive you with /lives revive."));
        }
    }

    /** @return how many lives the player has left after this death. */
    public static int onDeath(MinecraftServer server, EntityPlayerMP player) {
        LivesData data = data(server);
        int remaining = Math.max(0, data.get(player.getUniqueID()) - 1);
        data.set(player.getUniqueID(), remaining);
        refreshTab(server);
        return remaining;
    }

    public static void onRespawn(MinecraftServer server, EntityPlayerMP player) {
        if (data(server).get(player.getUniqueID()) <= 0
                && "spectator".equalsIgnoreCase(ModConfig.eliminationMode)) {
            player.setGameType(GameType.SPECTATOR);
            Msg.send(player, TextFormatting.RED,
                    "You have no lives left - you are now a spectator.");
        }
    }

    // ------------------------------------------------------------------
    // Operations called from /lives
    // ------------------------------------------------------------------

    public static void setLives(MinecraftServer server, EntityPlayerMP player, int value) {
        data(server).set(player.getUniqueID(), value);
        if (value <= 0 && "spectator".equalsIgnoreCase(ModConfig.eliminationMode)
                && player.interactionManager.getGameType() != GameType.SPECTATOR
                && !player.capabilities.isCreativeMode) {
            player.setGameType(GameType.SPECTATOR);
        }
        refreshTab(server);
    }

    public static void revive(MinecraftServer server, EntityPlayerMP player) {
        data(server).set(player.getUniqueID(), ModConfig.maxLives);
        if (player.interactionManager.getGameType() == GameType.SPECTATOR) {
            player.setGameType(GameType.SURVIVAL);
        }
        refreshTab(server);
    }

    /** Creates/updates the "lives" objective shown next to names in the tab list. */
    public static void refreshTab(MinecraftServer server) {
        Scoreboard sb = server.getEntityWorld().getScoreboard();
        ScoreObjective objective = sb.getObjective(OBJECTIVE_NAME);
        if (ModConfig.showLivesInTab) {
            if (objective == null) {
                objective = sb.addScoreObjective(OBJECTIVE_NAME, IScoreCriteria.DUMMY);
            }
            sb.setObjectiveInDisplaySlot(0, objective);
            LivesData data = data(server);
            for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
                Score score = sb.getOrCreateScore(player.getName(), objective);
                score.setScorePoints(data.get(player.getUniqueID()));
            }
        } else if (objective != null) {
            sb.setObjectiveInDisplaySlot(0, null);
            sb.removeObjective(objective);
        }
    }
}

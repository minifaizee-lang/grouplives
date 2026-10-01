package io.github.minifaizeelang.grouplives;

import io.github.minifaizeelang.grouplives.network.NetworkHandler;
import io.github.minifaizeelang.grouplives.network.PacketTeamTasks;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.storage.WorldSavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Team task board: every player can have free-text tasks, marked done by
 * clicking them in the chat-side panel. The board is stored in the world
 * save and synced only to the player's team members.
 */
public final class TasksManager {

    public static class Task {
        public String text;
        public boolean done;

        public Task(String text) {
            this.text = text;
        }
    }

    public static class TasksData extends WorldSavedData {

        public static final String DATA_NAME = "grouplives_tasks";

        /** Keyed by lowercase player name - team membership is name-based too. */
        private final Map<String, List<Task>> tasks = new HashMap<String, List<Task>>();

        public TasksData(String name) {
            super(name);
        }

        public TasksData() {
            super(DATA_NAME);
        }

        public List<Task> get(String playerName) {
            List<Task> list = tasks.get(playerName.toLowerCase());
            return list == null ? new ArrayList<Task>() : list;
        }

        private List<Task> mutable(String playerName) {
            String key = playerName.toLowerCase();
            List<Task> list = tasks.get(key);
            if (list == null) {
                list = new ArrayList<Task>();
                tasks.put(key, list);
            }
            return list;
        }

        @Override
        public void readFromNBT(NBTTagCompound nbt) {
            tasks.clear();
            NBTTagList players = nbt.getTagList("Players", 10);
            for (int i = 0; i < players.tagCount(); i++) {
                NBTTagCompound pc = players.getCompoundTagAt(i);
                List<Task> list = new ArrayList<Task>();
                NBTTagList taskTags = pc.getTagList("Tasks", 10);
                for (int j = 0; j < taskTags.tagCount(); j++) {
                    NBTTagCompound tc = taskTags.getCompoundTagAt(j);
                    Task task = new Task(tc.getString("Text"));
                    task.done = tc.getBoolean("Done");
                    list.add(task);
                }
                tasks.put(pc.getString("Name").toLowerCase(), list);
            }
        }

        @Override
        public NBTTagCompound writeToNBT(NBTTagCompound compound) {
            NBTTagList players = new NBTTagList();
            for (Map.Entry<String, List<Task>> entry : tasks.entrySet()) {
                NBTTagCompound pc = new NBTTagCompound();
                pc.setString("Name", entry.getKey());
                NBTTagList taskTags = new NBTTagList();
                for (Task task : entry.getValue()) {
                    NBTTagCompound tc = new NBTTagCompound();
                    tc.setString("Text", task.text);
                    tc.setBoolean("Done", task.done);
                    taskTags.appendTag(tc);
                }
                pc.setTag("Tasks", taskTags);
                players.appendTag(pc);
            }
            compound.setTag("Players", players);
            return compound;
        }
    }

    public static TasksData data(MinecraftServer server) {
        TasksData data = (TasksData) server.getEntityWorld().getMapStorage()
                .getOrLoadData(TasksData.class, TasksData.DATA_NAME);
        if (data == null) {
            data = new TasksData();
            server.getEntityWorld().getMapStorage().setData(TasksData.DATA_NAME, data);
        }
        return data;
    }

    // ------------------------------------------------------------------
    // Mutations (each one saves and re-syncs the team board)
    // ------------------------------------------------------------------

    public static void add(MinecraftServer server, String playerName, String text) {
        data(server).mutable(playerName).add(new Task(text));
        syncPlayer(server, playerName);
    }

    public static boolean toggle(MinecraftServer server, String playerName, int index) {
        List<Task> list = data(server).get(playerName);
        if (index < 0 || index >= list.size()) {
            return false;
        }
        Task task = list.get(index);
        task.done = !task.done;
        syncPlayer(server, playerName);
        return true;
    }

    public static boolean remove(MinecraftServer server, String playerName, int index) {
        List<Task> list = data(server).get(playerName);
        if (index < 0 || index >= list.size()) {
            return false;
        }
        list.remove(index);
        syncPlayer(server, playerName);
        return true;
    }

    public static void clear(MinecraftServer server, String playerName) {
        data(server).tasks.remove(playerName.toLowerCase());
        syncPlayer(server, playerName);
    }

    // ------------------------------------------------------------------
    // Sync
    // ------------------------------------------------------------------

    /** The player's team (or just the player when teamless). */
    public static List<String> teamMembers(MinecraftServer server, String playerName) {
        ScorePlayerTeam team = GroupManager.scoreboard(server).getPlayersTeam(playerName);
        if (team == null) {
            return Collections.singletonList(playerName);
        }
        return new ArrayList<String>(team.getMembershipCollection());
    }

    public static PacketTeamTasks buildBoard(MinecraftServer server, List<String> members) {
        List<PacketTeamTasks.Entry> entries = new ArrayList<PacketTeamTasks.Entry>();
        for (String member : members) {
            List<Task> list = data(server).get(member);
            for (int i = 0; i < list.size(); i++) {
                entries.add(new PacketTeamTasks.Entry(member, i, list.get(i).text, list.get(i).done));
            }
        }
        return new PacketTeamTasks(entries);
    }

    /** Sends the board of playerName's team to every online member of that team. */
    public static void syncPlayer(MinecraftServer server, String playerName) {
        List<String> members = teamMembers(server, playerName);
        sendTo(server, buildBoard(server, members), members);
    }

    /** Sends the board of exactly this set of members to its online members. */
    public static void syncMembers(MinecraftServer server, List<String> members) {
        if (members.isEmpty()) {
            return;
        }
        sendTo(server, buildBoard(server, members), members);
    }

    private static void sendTo(MinecraftServer server, PacketTeamTasks packet, List<String> audience) {
        for (String name : audience) {
            EntityPlayerMP player = server.getPlayerList().getPlayerByUsername(name);
            if (player != null) {
                NetworkHandler.CHANNEL.sendTo(packet, player);
            }
        }
    }
}

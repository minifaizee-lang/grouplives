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
 * Team task board: structured tasks (mine/craft an item in an amount) plus
 * legacy free-text tasks, stored per player in the world save and synced
 * only to the player's team members.
 */
public final class TasksManager {

    public static final int MAX_TASKS_PER_PLAYER = 32;

    /** Task types: 0 = mine/obtain, 1 = craft, -1 = legacy free text. */
    public static final int TYPE_MINE = 0;
    public static final int TYPE_CRAFT = 1;
    public static final int TYPE_TEXT = -1;

    public static class Task {
        public int type = TYPE_TEXT;
        public String itemId;
        public int amount = 1;
        public String text;
        public boolean done;

        public static Task structured(int type, String itemId, int amount) {
            Task task = new Task();
            task.type = type;
            task.itemId = itemId;
            task.amount = amount;
            return task;
        }

        public static Task legacy(String text) {
            Task task = new Task();
            task.text = text;
            return task;
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
                    Task task;
                    if (tc.hasKey("Item")) {
                        task = Task.structured(tc.getInteger("Type"), tc.getString("Item"), tc.getInteger("Amount"));
                    } else {
                        task = Task.legacy(tc.getString("Text"));
                    }
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
                    if (task.type >= 0) {
                        tc.setInteger("Type", task.type);
                        tc.setString("Item", task.itemId);
                        tc.setInteger("Amount", task.amount);
                    } else {
                        tc.setString("Text", task.text);
                    }
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

    public static boolean add(MinecraftServer server, String playerName, String text) {
        List<Task> list = data(server).mutable(playerName);
        if (list.size() >= MAX_TASKS_PER_PLAYER) {
            return false;
        }
        list.add(Task.legacy(text));
        syncPlayer(server, playerName);
        return true;
    }

    public static boolean addStructured(MinecraftServer server, String playerName, int type, String itemId, int amount) {
        List<Task> list = data(server).mutable(playerName);
        if (list.size() >= MAX_TASKS_PER_PLAYER) {
            return false;
        }
        list.add(Task.structured(type, itemId, amount));
        syncPlayer(server, playerName);
        return true;
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
                Task task = list.get(i);
                entries.add(new PacketTeamTasks.Entry(member, i, task.type, task.itemId,
                        task.amount, task.text, task.done));
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

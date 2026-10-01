package io.github.minifaizeelang.grouplives.client;

import io.github.minifaizeelang.grouplives.ModConfig;

import java.util.Collections;
import java.util.List;

/**
 * Client mirror of the event state, kept fresh by PacketEventState. The
 * fields are volatile because the network handler thread writes them while
 * the client thread reads them. Deliberately free of any client-only
 * imports so the packet class can reference it on a dedicated server too.
 */
public final class ClientState {

    public static volatile boolean eventStarted;
    public static volatile boolean borderEnabled;
    public static volatile int borderSize = ModConfig.eventBorderSize;
    public static volatile int teamSpacing = ModConfig.teamSpacing;

    /** Set when the login packet says the event has not started yet - the lobby auto-opens then. */
    public static volatile boolean autoOpenPending;

    /** The player's team task board, replaced wholesale by PacketTeamTasks. */
    public static volatile List<TaskEntry> teamTasks = Collections.emptyList();

    public static class TaskEntry {
        public final String player;
        public final int index;
        public final String text;
        public final boolean done;

        public TaskEntry(String player, int index, String text, boolean done) {
            this.player = player;
            this.index = index;
            this.text = text;
            this.done = done;
        }
    }

    private ClientState() {
    }

    public static void apply(boolean started, boolean border, int size, int spacing) {
        eventStarted = started;
        borderEnabled = border;
        borderSize = size;
        teamSpacing = spacing;
        autoOpenPending = !started;
    }
}

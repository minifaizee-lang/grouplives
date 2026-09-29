package io.github.minifaizeelang.grouplives;

import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

/**
 * All user-facing chat output goes through here, so restyling the mod later
 * means editing this one class.
 */
public final class Msg {

    /** Prefix in front of every mod message - restyle here. */
    public static final String CHAT_PREFIX = TextFormatting.GOLD + "[G&L] " + TextFormatting.RESET;

    private Msg() {
    }

    public static TextComponentString text(String message) {
        return new TextComponentString(CHAT_PREFIX + message);
    }

    public static TextComponentString text(TextFormatting color, String message) {
        return new TextComponentString(CHAT_PREFIX + color + message + TextFormatting.RESET);
    }

    public static void send(ICommandSender to, String message) {
        to.sendMessage(text(message));
    }

    public static void send(ICommandSender to, TextFormatting color, String message) {
        to.sendMessage(text(color, message));
    }

    public static void broadcast(MinecraftServer server, TextFormatting color, String message) {
        server.getPlayerList().sendMessage(text(color, message));
    }
}

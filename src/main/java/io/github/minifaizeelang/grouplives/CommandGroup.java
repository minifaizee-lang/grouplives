package io.github.minifaizeelang.grouplives;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CommandGroup extends CommandBase {

    @Override
    public String getName() {
        return "group";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/group <create|delete|join|leave|add|remove|list|info|color>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    private boolean isOp(ICommandSender sender) {
        return sender.canUseCommand(2, getName());
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 1) {
            throw new WrongUsageException(getUsage(sender));
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "create": {
                if (!isOp(sender) && !ModConfig.playersCanCreateGroups) {
                    throw new CommandException("Only operators can create groups.");
                }
                if (args.length < 2) {
                    throw new WrongUsageException("/group create <name> [color]");
                }
                String name = args[1];
                if (!GroupManager.isValidName(name)) {
                    throw new CommandException("Invalid group name: use 1-16 characters (letters, digits, . _ | + - /)");
                }
                TextFormatting color = TextFormatting.WHITE;
                if (args.length >= 3) {
                    color = parseColor(args[2]);
                }
                if (GroupManager.create(server, name, color) == null) {
                    throw new CommandException("A group named " + name + " already exists.");
                }
                Msg.send(sender, TextFormatting.GREEN, "Group created: " + name);
                return;
            }
            case "delete": {
                if (!isOp(sender)) {
                    throw new CommandException("Only operators can delete groups.");
                }
                if (args.length < 2) {
                    throw new WrongUsageException("/group delete <name>");
                }
                if (!GroupManager.delete(server, args[1])) {
                    throw new CommandException("No group named " + args[1] + " exists.");
                }
                Msg.send(sender, TextFormatting.YELLOW, "Group deleted: " + args[1]);
                return;
            }
            case "join": {
                if (!isOp(sender) && !ModConfig.playersCanJoinLeaveFreely) {
                    throw new CommandException("Only operators can put players into groups.");
                }
                if (args.length < 2) {
                    throw new WrongUsageException("/group join <name>");
                }
                if (!GroupManager.join(server, sender.getName(), args[1])) {
                    throw new CommandException("Could not join " + args[1] + " - does it exist?");
                }
                Msg.send(sender, TextFormatting.GREEN, "Joined group: " + args[1]);
                return;
            }
            case "leave": {
                if (!isOp(sender) && !ModConfig.playersCanJoinLeaveFreely) {
                    throw new CommandException("Only operators can remove players from groups.");
                }
                if (!GroupManager.leave(server, sender.getName())) {
                    throw new CommandException("You are not in a group.");
                }
                Msg.send(sender, TextFormatting.YELLOW, "You left your group.");
                return;
            }
            case "add": {
                if (!isOp(sender)) {
                    throw new CommandException("Only operators can add players to groups.");
                }
                if (args.length < 3) {
                    throw new WrongUsageException("/group add <player> <group>");
                }
                if (!GroupManager.join(server, args[1], args[2])) {
                    throw new CommandException("Could not add " + args[1] + " to " + args[2]);
                }
                Msg.send(sender, TextFormatting.GREEN, "Added " + args[1] + " to " + args[2]);
                return;
            }
            case "remove": {
                if (!isOp(sender)) {
                    throw new CommandException("Only operators can remove players from groups.");
                }
                if (args.length < 2) {
                    throw new WrongUsageException("/group remove <player>");
                }
                if (!GroupManager.leave(server, args[1])) {
                    throw new CommandException(args[1] + " is not in a group.");
                }
                Msg.send(sender, TextFormatting.YELLOW, "Removed " + args[1] + " from their group.");
                return;
            }
            case "list": {
                List<String> names = GroupManager.groupNames(server);
                if (names.isEmpty()) {
                    Msg.send(sender, "No groups exist yet. An operator can create one with /group create.");
                    return;
                }
                Msg.send(sender, TextFormatting.GOLD, "Groups (" + names.size() + "):");
                for (String name : names) {
                    ScorePlayerTeam team = GroupManager.scoreboard(server).getTeam(name);
                    Msg.send(sender, " - " + name + " (" + team.getMembershipCollection().size() + " members)");
                }
                return;
            }
            case "info": {
                if (args.length < 2) {
                    throw new WrongUsageException("/group info <name>");
                }
                ScorePlayerTeam team = GroupManager.scoreboard(server).getTeam(args[1]);
                if (team == null) {
                    throw new CommandException("No group named " + args[1] + " exists.");
                }
                Msg.send(sender, TextFormatting.GOLD, "Group " + team.getName() + ":");
                for (String member : team.getMembershipCollection()) {
                    Msg.send(sender, " - " + member);
                }
                return;
            }
            case "color": {
                if (!isOp(sender)) {
                    throw new CommandException("Only operators can change group colors.");
                }
                if (args.length < 3) {
                    throw new WrongUsageException("/group color <name> <color>");
                }
                ScorePlayerTeam team = GroupManager.scoreboard(server).getTeam(args[1]);
                if (team == null) {
                    throw new CommandException("No group named " + args[1] + " exists.");
                }
                TextFormatting color = parseColor(args[2]);
                GroupManager.applyStyle(server, team, team.getName(), color);
                Msg.send(sender, TextFormatting.GREEN, "Color of " + team.getName() + " updated.");
                return;
            }
            default:
                throw new WrongUsageException(getUsage(sender));
        }
    }

    private static TextFormatting parseColor(String name) throws CommandException {
        for (TextFormatting formatting : TextFormatting.values()) {
            if (formatting.isColor() && formatting.getFriendlyName().equalsIgnoreCase(name)) {
                return formatting;
            }
        }
        throw new CommandException("Unknown color: " + name + " (e.g. red, gold, aqua, dark_purple)");
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args,
                    "create", "delete", "join", "leave", "add", "remove", "list", "info", "color");
        }
        String first = args[0].toLowerCase();
        if (args.length == 2) {
            switch (first) {
                case "delete":
                case "join":
                case "info":
                case "color":
                    return getListOfStringsMatchingLastWord(args, GroupManager.groupNames(server));
                case "add":
                case "remove":
                    return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
                default:
                    return Collections.emptyList();
            }
        }
        if (args.length == 3 && ("add".equals(first) || "color".equals(first))) {
            if ("add".equals(first)) {
                return getListOfStringsMatchingLastWord(args, GroupManager.groupNames(server));
            }
            List<String> colors = new ArrayList<String>();
            for (TextFormatting formatting : TextFormatting.values()) {
                if (formatting.isColor()) {
                    colors.add(formatting.getFriendlyName());
                }
            }
            return getListOfStringsMatchingLastWord(args, colors);
        }
        return Collections.emptyList();
    }
}

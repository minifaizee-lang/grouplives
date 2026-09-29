package io.github.minifaizeelang.grouplives;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CommandLives extends CommandBase {

    @Override
    public String getName() {
        return "lives";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/lives [get <player>|list|set <player> <n>|give <player> <n>|take <player> <n>|revive <player>]";
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
        String sub = args.length == 0 ? "get" : args[0].toLowerCase();
        switch (sub) {
            case "get": {
                if (args.length >= 2) {
                    EntityPlayerMP target = getPlayer(server, sender, args[1]);
                    Msg.send(sender, target.getName() + " has "
                            + LivesManager.data(server).get(target.getUniqueID()) + " lives.");
                } else {
                    EntityPlayerMP self = getCommandSenderAsPlayer(sender);
                    Msg.send(sender, "You have "
                            + LivesManager.data(server).get(self.getUniqueID()) + " lives.");
                }
                return;
            }
            case "list": {
                Map<UUID, Integer> all = LivesManager.data(server).all();
                if (all.isEmpty()) {
                    Msg.send(sender, "No lives tracked yet.");
                    return;
                }
                Msg.send(sender, TextFormatting.GOLD, "Lives (" + all.size() + " players):");
                for (Map.Entry<UUID, Integer> entry : all.entrySet()) {
                    EntityPlayerMP online = server.getPlayerList().getPlayerByUUID(entry.getKey());
                    String name = online != null ? online.getName() : entry.getKey().toString();
                    Msg.send(sender, " - " + name + ": " + entry.getValue()
                            + (entry.getValue() <= 0 ? " (eliminated)" : ""));
                }
                return;
            }
            case "set": {
                if (!isOp(sender)) {
                    throw new CommandException("Only operators can change lives.");
                }
                if (args.length < 3) {
                    throw new WrongUsageException("/lives set <player> <amount>");
                }
                EntityPlayerMP target = getPlayer(server, sender, args[1]);
                int amount = parseInt(args[2], 0);
                LivesManager.setLives(server, target, amount);
                Msg.send(sender, TextFormatting.GREEN,
                        target.getName() + " now has " + amount + " lives.");
                return;
            }
            case "give": {
                if (!isOp(sender)) {
                    throw new CommandException("Only operators can change lives.");
                }
                if (args.length < 3) {
                    throw new WrongUsageException("/lives give <player> <amount>");
                }
                EntityPlayerMP target = getPlayer(server, sender, args[1]);
                int amount = parseInt(args[2], 0);
                int newValue = LivesManager.data(server).get(target.getUniqueID()) + amount;
                LivesManager.setLives(server, target, newValue);
                Msg.send(sender, TextFormatting.GREEN,
                        target.getName() + " now has " + Math.max(0, newValue) + " lives.");
                return;
            }
            case "take": {
                if (!isOp(sender)) {
                    throw new CommandException("Only operators can change lives.");
                }
                if (args.length < 3) {
                    throw new WrongUsageException("/lives take <player> <amount>");
                }
                EntityPlayerMP target = getPlayer(server, sender, args[1]);
                int amount = parseInt(args[2], 0);
                int newValue = LivesManager.data(server).get(target.getUniqueID()) - amount;
                LivesManager.setLives(server, target, newValue);
                Msg.send(sender, TextFormatting.GREEN,
                        target.getName() + " now has " + Math.max(0, newValue) + " lives.");
                return;
            }
            case "revive": {
                if (!isOp(sender)) {
                    throw new CommandException("Only operators can revive players.");
                }
                if (args.length < 2) {
                    throw new WrongUsageException("/lives revive <player>");
                }
                EntityPlayerMP target = getPlayer(server, sender, args[1]);
                LivesManager.revive(server, target);
                Msg.broadcast(server, TextFormatting.GREEN,
                        target.getName() + " was revived with " + ModConfig.maxLives + " lives!");
                return;
            }
            default:
                throw new WrongUsageException(getUsage(sender));
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args,
                    "get", "list", "set", "give", "take", "revive");
        }
        if (args.length == 2 && !"list".equals(args[0].toLowerCase()) && !"get".equals(args[0].toLowerCase())) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        if (args.length == 2 && "get".equals(args[0].toLowerCase())) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        return Collections.emptyList();
    }
}

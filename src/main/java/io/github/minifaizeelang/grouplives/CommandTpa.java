package io.github.minifaizeelang.grouplives;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

import java.util.Collections;
import java.util.List;

public class CommandTpa extends CommandBase {

    @Override
    public String getName() {
        return "tpa";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/tpa <player>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        EntityPlayerMP self = getCommandSenderAsPlayer(sender);
        if (args.length < 1) {
            throw new WrongUsageException(getUsage(sender));
        }
        EntityPlayerMP target = getPlayer(server, sender, args[0]);
        if (target == self) {
            throw new CommandException("You cannot teleport to yourself.");
        }
        if (!GroupManager.areTeammates(server, self.getName(), target.getName())) {
            throw new CommandException("You can only teleport to players in your own group.");
        }
        TeleportManager.request(server, self, target);
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        return Collections.emptyList();
    }
}

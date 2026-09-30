package io.github.minifaizeelang.grouplives;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

import java.util.Collections;
import java.util.List;

public class CommandTpdeny extends CommandBase {

    @Override
    public String getName() {
        return "tpdeny";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/tpdeny [player]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        EntityPlayerMP self = getCommandSenderAsPlayer(sender);
        TeleportManager.deny(server, self, args);
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
        if (args.length == 1 && sender instanceof EntityPlayerMP) {
            return getListOfStringsMatchingLastWord(args,
                    TeleportManager.pendingRequesterNames(server, (EntityPlayerMP) sender));
        }
        return Collections.emptyList();
    }
}

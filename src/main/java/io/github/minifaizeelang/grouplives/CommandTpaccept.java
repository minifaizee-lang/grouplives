package io.github.minifaizeelang.grouplives;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

import java.util.Collections;
import java.util.List;

public class CommandTpaccept extends CommandBase {

    @Override
    public String getName() {
        return "tpaccept";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/tpaccept [player]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        EntityPlayerMP self = getCommandSenderAsPlayer(sender);
        TeleportManager.accept(server, self, args);
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

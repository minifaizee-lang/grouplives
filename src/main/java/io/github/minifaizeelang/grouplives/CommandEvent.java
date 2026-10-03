package io.github.minifaizeelang.grouplives;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.WorldServer;
import net.minecraft.world.border.WorldBorder;

/**
 * Event control: scatters teams across the world (within the VANILLA world
 * border set by the admin via /worldborder) with respawn points at the drop
 * locations. The border itself is not managed by this mod at all.
 */
public class CommandEvent extends CommandBase {

    @Override
    public String getName() {
        return "event";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/event <start [spacing]|info>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 1) {
            throw new WrongUsageException(getUsage(sender));
        }
        switch (args[0].toLowerCase()) {
            case "start": {
                int spacing = args.length >= 2 ? parseInt(args[1], 0, 100000) : ModConfig.teamSpacing;
                EventManager.startEvent(server, sender, spacing);
                return;
            }
            case "info": {
                WorldServer world = server.getWorld(0);
                WorldBorder border = world.getWorldBorder();
                Msg.send(sender, TextFormatting.GOLD, "Event status:");
                Msg.send(sender, " - Граница мира (ванильная /worldborder): "
                        + (int) border.getSize() + " x " + (int) border.getSize()
                        + " (центр " + (int) border.getCenterX() + ", " + (int) border.getCenterZ() + ")");
                Msg.send(sender, " - Команд с игроками онлайн: " + EventManager.activeTeams(server).size());
                Msg.send(sender, " - Дистанция между командами: " + ModConfig.teamSpacing);
                return;
            }
            default:
                throw new WrongUsageException(getUsage(sender));
        }
    }
}

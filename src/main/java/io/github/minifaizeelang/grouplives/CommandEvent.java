package io.github.minifaizeelang.grouplives;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.WorldServer;

public class CommandEvent extends CommandBase {

    @Override
    public String getName() {
        return "event";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/event <border <size>|start [size] [spacing]|info>";
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
            case "border": {
                if (args.length < 2) {
                    throw new WrongUsageException("/event border <size>");
                }
                int size = parseInt(args[1], 100, 600000);
                EventManager.setBorder(server, size);
                Msg.send(sender, TextFormatting.GREEN,
                        "World border set to " + size + " x " + size + ", centered on world spawn.");
                return;
            }
            case "start": {
                int size = args.length >= 2 ? parseInt(args[1], 100, 600000) : ModConfig.eventBorderSize;
                int spacing = args.length >= 3 ? parseInt(args[2], 0, 100000) : ModConfig.teamSpacing;
                EventManager.startEvent(server, sender, size, spacing);
                return;
            }
            case "info": {
                WorldServer world = server.getWorld(0);
                int size = world.getWorldBorder().getSize();
                Msg.send(sender, TextFormatting.GOLD, "Event status:");
                Msg.send(sender, " - World border: " + size + " x " + size
                        + " (center " + (int) world.getWorldBorder().getCenterX()
                        + ", " + (int) world.getWorldBorder().getCenterZ() + ")");
                Msg.send(sender, " - Teams with online players: " + EventManager.activeTeams(server).size());
                Msg.send(sender, " - Team spacing on start: " + ModConfig.teamSpacing + " blocks");
                return;
            }
            default:
                throw new WrongUsageException(getUsage(sender));
        }
    }
}

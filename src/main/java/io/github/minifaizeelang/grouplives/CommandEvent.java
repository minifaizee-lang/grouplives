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
        return "/event <border <size|on|off>|start [size] [spacing]|info>";
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
                    throw new WrongUsageException("/event border <size|on|off>");
                }
                if ("on".equalsIgnoreCase(args[1]) || "off".equalsIgnoreCase(args[1])) {
                    boolean enabled = "on".equalsIgnoreCase(args[1]);
                    EventManager.toggleBorder(server, enabled);
                    Msg.send(sender, TextFormatting.GREEN, enabled
                            ? "Граница мира включена." : "Граница мира выключена.");
                    return;
                }
                int size = parseInt(args[1], 100, 600000);
                EventManager.setBorder(server, size, true);
                Msg.send(sender, TextFormatting.GREEN,
                        "Граница мира установлена: " + size + " x " + size + ", центр - точка спавна.");
                return;
            }
            case "start": {
                int size = args.length >= 2 ? parseInt(args[1], 100, 600000) : ModConfig.eventBorderSize;
                int spacing = args.length >= 3 ? parseInt(args[2], 0, 100000) : ModConfig.teamSpacing;
                EventManager.startEvent(server, sender, size, spacing);
                return;
            }
            case "info": {
                EventManager.EventStateData data = EventManager.data(server);
                Msg.send(sender, TextFormatting.GOLD, "Event status:");
                Msg.send(sender, " - Граница: " + (data.borderEnabled ? "включена" : "выключена")
                        + ", размер " + data.borderSize + " x " + data.borderSize
                        + " (центр " + (int) data.centerX + ", " + (int) data.centerZ + ")");
                Msg.send(sender, " - Ивент: " + (data.started ? "идёт" : "лобби (подготовка)"));
                Msg.send(sender, " - Команд с игроками онлайн: " + EventManager.activeTeams(server).size());
                Msg.send(sender, " - Дистанция между командами: " + ModConfig.teamSpacing);
                return;
            }
            default:
                throw new WrongUsageException(getUsage(sender));
        }
    }
}

package io.github.minifaizeelang.grouplives;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * /task - free-text tasks per player, shown in the chat-side team panel.
 * Any teammate may mark (toggle) anyone's task; removing other players'
 * tasks requires operator rights.
 */
public class CommandTasks extends CommandBase {

    @Override
    public String getName() {
        return "task";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/task <add <text>|additem <player> <mine|craft> <item> <n>|addfor <player> <text>|toggle <player> <n>|remove <player> <n>|clear|list>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    private boolean isOp(ICommandSender sender) {
        return sender.canUseCommand(2, getName());
    }

    private static String joinText(String[] args, int from) {
        return String.join(" ", Arrays.copyOfRange(args, from, args.length)).trim();
    }

    private boolean canTouch(MinecraftServer server, ICommandSender sender, String targetName) {
        return sender.getName().equalsIgnoreCase(targetName)
                || isOp(sender)
                || GroupManager.areTeammates(server, sender.getName(), targetName);
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        String sub = args.length == 0 ? "list" : args[0].toLowerCase();
        switch (sub) {
            case "add": {
                EntityPlayerMP self = getCommandSenderAsPlayer(sender);
                String text = joinText(args, 1);
                if (text.isEmpty()) {
                    throw new WrongUsageException("/task add <text>");
                }
                if (!TasksManager.add(server, self.getName(), text)) {
                    throw new CommandException("Слишком много задач (максимум 32).");
                }
                Msg.send(sender, TextFormatting.GREEN, "Задача добавлена.");
                return;
            }
            case "additem": {
                if (args.length < 5) {
                    throw new WrongUsageException("/task additem <player> <mine|craft> <item> <amount>");
                }
                EntityPlayerMP target = getPlayer(server, sender, args[1]);
                if (!canTouch(server, sender, target.getName())) {
                    throw new CommandException("Задачи можно ставить только членам своей команды.");
                }
                int type;
                if ("mine".equalsIgnoreCase(args[2])) {
                    type = TasksManager.TYPE_MINE;
                } else if ("craft".equalsIgnoreCase(args[2])) {
                    type = TasksManager.TYPE_CRAFT;
                } else {
                    throw new CommandException("Тип должен быть mine или craft.");
                }
                if (Item.getByNameOrId(args[3]) == null) {
                    throw new CommandException("Неизвестный предмет: " + args[3]);
                }
                int amount = parseInt(args[4], 1);
                if (!TasksManager.addStructured(server, target.getName(), type, args[3], amount)) {
                    throw new CommandException("Слишком много задач (максимум 32).");
                }
                if (sender != target) {
                    Msg.send(sender, TextFormatting.GREEN, "Задача добавлена для " + target.getName() + ".");
                    Msg.send(target, TextFormatting.GOLD, sender.getName() + " добавил вам задачу.");
                }
                return;
            }
            case "addfor": {
                if (!isOp(sender)) {
                    throw new CommandException("Только операторы могут назначать задачи другим.");
                }
                if (args.length < 3) {
                    throw new WrongUsageException("/task addfor <player> <text>");
                }
                EntityPlayerMP target = getPlayer(server, sender, args[1]);
                String text = joinText(args, 2);
                TasksManager.add(server, target.getName(), text);
                Msg.send(sender, TextFormatting.GREEN, "Задача добавлена для " + target.getName() + ".");
                Msg.send(target, TextFormatting.GOLD, sender.getName() + " добавил вам задачу: " + text);
                return;
            }
            case "toggle": {
                if (args.length < 3) {
                    throw new WrongUsageException("/task toggle <player> <n>");
                }
                EntityPlayerMP target = getPlayer(server, sender, args[1]);
                if (!canTouch(server, sender, target.getName())) {
                    throw new CommandException("Отмечать можно только задачи своей команды.");
                }
                int index = parseInt(args[2], 0);
                if (!TasksManager.toggle(server, target.getName(), index)) {
                    throw new CommandException("Нет задачи с таким номером.");
                }
                return;
            }
            case "remove": {
                if (args.length < 3) {
                    throw new WrongUsageException("/task remove <player> <n>");
                }
                EntityPlayerMP target = getPlayer(server, sender, args[1]);
                if (!sender.getName().equalsIgnoreCase(target.getName()) && !isOp(sender)) {
                    throw new CommandException("Чужие задачи может удалять только оператор или сам игрок.");
                }
                int index = parseInt(args[2], 0);
                if (!TasksManager.remove(server, target.getName(), index)) {
                    throw new CommandException("Нет задачи с таким номером.");
                }
                Msg.send(sender, TextFormatting.YELLOW, "Задача удалена.");
                return;
            }
            case "clear": {
                EntityPlayerMP self = getCommandSenderAsPlayer(sender);
                TasksManager.clear(server, self.getName());
                Msg.send(sender, TextFormatting.YELLOW, "Список задач очищен.");
                return;
            }
            case "list": {
                EntityPlayerMP self = getCommandSenderAsPlayer(sender);
                List<TasksManager.Task> tasks = TasksManager.data(server).get(self.getName());
                if (tasks.isEmpty()) {
                    Msg.send(sender, "Задач нет. Добавьте: /task add <текст>");
                    return;
                }
                Msg.send(sender, TextFormatting.GOLD, "Ваши задачи:");
                for (int i = 0; i < tasks.size(); i++) {
                    TasksManager.Task task = tasks.get(i);
                    Msg.send(sender, " " + (i + 1) + ". [" + (task.done ? "x" : " ") + "] " + task.text);
                }
                return;
            }
            default:
                throw new WrongUsageException(getUsage(sender));
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "add", "additem", "addfor", "toggle", "remove", "clear", "list");
        }
        String first = args[0].toLowerCase();
        if (args.length == 2 && ("toggle".equals(first) || "remove".equals(first) || "addfor".equals(first) || "additem".equals(first))) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        if (args.length == 3 && "additem".equals(first)) {
            return getListOfStringsMatchingLastWord(args, "mine", "craft");
        }
        return Collections.emptyList();
    }
}

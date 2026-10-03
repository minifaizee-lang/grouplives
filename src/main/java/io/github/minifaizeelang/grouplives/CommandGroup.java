package io.github.minifaizeelang.grouplives;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Team management: any player can create a team, members invite others,
 * the owner kicks members, recolors and deletes the team. Joining happens
 * only through invitations (clickable chat message).
 */
public class CommandGroup extends CommandBase {

    private final String name;

    public CommandGroup() {
        this("group");
    }

    public CommandGroup(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/" + this.name + " <create|invite|accept|decline|kick|leave|delete|color|list|info|add|remove>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    private boolean isOp(ICommandSender sender) {
        return sender.canUseCommand(2, getName());
    }

    private ScorePlayerTeam teamOf(MinecraftServer server, ICommandSender sender) throws CommandException {
        ScorePlayerTeam team = GroupManager.scoreboard(server).getPlayersTeam(sender.getName());
        if (team == null) {
            throw new CommandException("Вы не состоите в команде.");
        }
        return team;
    }

    private void requireOwner(MinecraftServer server, ScorePlayerTeam team, ICommandSender sender) throws CommandException {
        if (!GroupManager.isOwner(server, team.getName(), sender.getName()) && !isOp(sender)) {
            throw new CommandException("Это может сделать только владелец команды (" +
                    GroupManager.getOwner(server, team.getName()) + ").");
        }
    }

    private void resyncTab(MinecraftServer server) {
        GroupManager.resyncAllTabNames(server);
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 1) {
            throw new WrongUsageException(getUsage(sender));
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "create": {
                if (args.length < 2) {
                    throw new WrongUsageException("/" + getName() + " create <имя> [цвет]");
                }
                String teamName = args[1];
                if (!GroupManager.isValidName(teamName)) {
                    throw new CommandException("Недопустимое имя: 1-16 символов (буквы, цифры, . _ | + - /)");
                }
                if (GroupManager.scoreboard(server).getPlayersTeam(sender.getName()) != null) {
                    throw new CommandException("Вы уже состоите в команде.");
                }
                TextFormatting color = TextFormatting.WHITE;
                if (args.length >= 3) {
                    color = parseColor(args[2]);
                }
                if (!GroupManager.createTeam(server, teamName, color, sender.getName())) {
                    throw new CommandException("Команда с таким именем уже существует.");
                }
                resyncTab(server);
                Msg.send(sender, TextFormatting.GREEN, "Команда создана. Приглашайте: /" + getName() + " invite <ник>");
                return;
            }
            case "invite": {
                if (args.length < 2) {
                    throw new WrongUsageException("/" + getName() + " invite <ник>");
                }
                ScorePlayerTeam team = teamOf(server, sender);
                EntityPlayerMP target = getPlayer(server, sender, args[1]);
                if (target.getName().equalsIgnoreCase(sender.getName())) {
                    throw new CommandException("Себя пригласить нельзя.");
                }
                if (GroupManager.scoreboard(server).getPlayersTeam(target.getName()) != null) {
                    throw new CommandException("Игрок уже состоит в команде.");
                }
                if (!GroupManager.invite(server, (EntityPlayerMP) sender, target, team)) {
                    throw new CommandException("У игрока уже есть непринятое приглашение.");
                }
                Msg.send(sender, TextFormatting.GREEN, "Приглашение отправлено " + target.getName() + ".");

                TextFormatting color = team.getColor() == null ? TextFormatting.WHITE : team.getColor();
                TextComponentString invite = new TextComponentString(
                        TextFormatting.YELLOW + "[G&L] " + TextFormatting.RESET
                                + sender.getName() + " приглашает вас в команду ");
                TextComponentString teamName = new TextComponentString(team.getName());
                teamName.getStyle().setColor(color);
                teamName.getStyle().setUnderlined(true);
                invite.appendSibling(teamName);

                TextComponentString accept = new TextComponentString(" [ПРИНЯТЬ]");
                accept.getStyle().setColor(TextFormatting.GREEN);
                accept.getStyle().setUnderlined(true);
                accept.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + getName() + " accept"));

                TextComponentString decline = new TextComponentString(" [ОТКЛОНИТЬ]");
                decline.getStyle().setColor(TextFormatting.RED);
                decline.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + getName() + " decline"));

                invite.appendSibling(accept);
                invite.appendSibling(decline);
                target.sendMessage(invite);
                return;
            }
            case "accept": {
                EntityPlayerMP self = getCommandSenderAsPlayer(sender);
                String teamName = GroupManager.acceptInvite(server, self);
                if (teamName == null) {
                    throw new CommandException("Приглашение не найдено или устарело.");
                }
                resyncTab(server);
                Msg.send(self, TextFormatting.GREEN, "Вы вступили в команду " + teamName + ".");
                Msg.broadcast(server, TextFormatting.GREEN, self.getName() + " присоединился к команде " + teamName + ".");
                return;
            }
            case "decline": {
                EntityPlayerMP self = getCommandSenderAsPlayer(sender);
                if (GroupManager.declineInvite(self)) {
                    Msg.send(self, TextFormatting.YELLOW, "Приглашение отклонено.");
                } else {
                    Msg.send(self, "Приглашение не найдено.");
                }
                return;
            }
            case "kick": {
                if (args.length < 2) {
                    throw new WrongUsageException("/" + getName() + " kick <ник>");
                }
                ScorePlayerTeam team = teamOf(server, sender);
                requireOwner(server, team, sender);
                EntityPlayerMP target = getPlayer(server, sender, args[1]);
                if (target.getName().equalsIgnoreCase(sender.getName())) {
                    throw new CommandException("Себя нельзя исключить - используйте leave.");
                }
                if (!team.getMembershipCollection().contains(target.getName())) {
                    throw new CommandException("Игрок не состоит в вашей команде.");
                }
                GroupManager.kickMember(server, team, target);
                resyncTab(server);
                Msg.broadcast(server, TextFormatting.YELLOW,
                        target.getName() + " исключён из команды " + team.getName() + ".");
                return;
            }
            case "leave": {
                EntityPlayerMP self = getCommandSenderAsPlayer(sender);
                String teamName = teamOf(server, sender).getName();
                List<String> remaining = GroupManager.leaveTeam(server, self.getName());
                resyncTab(server);
                Msg.send(self, TextFormatting.YELLOW, "Вы покинули команду " + teamName + ".");
                if (!remaining.isEmpty()) {
                    Msg.broadcast(server, TextFormatting.YELLOW, self.getName() + " покинул команду " + teamName + ".");
                }
                return;
            }
            case "delete": {
                EntityPlayerMP self = getCommandSenderAsPlayer(sender);
                ScorePlayerTeam team = teamOf(server, sender);
                requireOwner(server, team, sender);
                List<String> members = GroupManager.deleteTeam(server, team.getName());
                resyncTab(server);
                Msg.broadcast(server, TextFormatting.YELLOW,
                        "Команда " + team.getName() + " распущена владельцем.");
                for (String member : members) {
                    EntityPlayerMP online = server.getPlayerList().getPlayerByUsername(member);
                    if (online != null) {
                        Msg.send(online, TextFormatting.YELLOW, "Ваша команда была распущена.");
                    }
                }
                return;
            }
            case "color": {
                if (args.length < 2) {
                    throw new WrongUsageException("/" + getName() + " color <цвет>");
                }
                ScorePlayerTeam team = teamOf(server, sender);
                requireOwner(server, team, sender);
                TextFormatting color = parseColor(args[1]);
                GroupManager.applyStyle(server, team, team.getName(), color);
                GroupManager.resyncAllTabNames(server);
                Msg.send(sender, TextFormatting.GREEN, "Цвет команды обновлен.");
                return;
            }
            case "list": {
                List<ScorePlayerTeam> teams = new ArrayList<ScorePlayerTeam>(GroupManager.scoreboard(server).getTeams());
                if (teams.isEmpty()) {
                    Msg.send(sender, "Команд нет. Создайте: /" + getName() + " create <имя> [цвет]");
                    return;
                }
                Msg.send(sender, TextFormatting.GOLD, "Команды:");
                for (ScorePlayerTeam team : teams) {
                    String owner = GroupManager.getOwner(server, team.getName());
                    Msg.send(sender, " - " + team.getName() + " (" + team.getMembershipCollection().size()
                            + " чел.)" + (owner != null && !owner.isEmpty() ? " · владелец: " + owner : ""));
                }
                return;
            }
            case "info": {
                ScorePlayerTeam team;
                if (args.length >= 2) {
                    team = GroupManager.scoreboard(server).getTeam(args[1]);
                } else {
                    team = GroupManager.scoreboard(server).getPlayersTeam(sender.getName());
                }
                if (team == null) {
                    throw new CommandException("Команда не найдена.");
                }
                String owner = GroupManager.getOwner(server, team.getName());
                Msg.send(sender, TextFormatting.GOLD, "Команда " + team.getName() + ":");
                Msg.send(sender, "Владелец: " + (owner == null ? "неизвестно" : owner));
                for (String member : team.getMembershipCollection()) {
                    Msg.send(sender, " - " + member);
                }
                return;
            }
            case "add": {
                if (!isOp(sender)) {
                    throw new CommandException("Только оператор.");
                }
                if (args.length < 3) {
                    throw new WrongUsageException("/" + getName() + " add <ник> <команда>");
                }
                EntityPlayerMP target = getPlayer(server, sender, args[1]);
                ScorePlayerTeam team = GroupManager.scoreboard(server).getTeam(args[2]);
                if (team == null) {
                    throw new CommandException("Команда не найдена: " + args[2]);
                }
                GroupManager.scoreboard(server).addPlayerToTeam(target.getName(), team.getName());
                resyncTab(server);
                Msg.send(sender, TextFormatting.GREEN, target.getName() + " добавлен в " + team.getName() + ".");
                return;
            }
            case "remove": {
                if (!isOp(sender)) {
                    throw new CommandException("Только оператор.");
                }
                if (args.length < 2) {
                    throw new WrongUsageException("/" + getName() + " remove <ник>");
                }
                EntityPlayerMP target = getPlayer(server, sender, args[1]);
                GroupManager.leaveTeam(server, target.getName());
                resyncTab(server);
                Msg.send(sender, TextFormatting.GREEN, target.getName() + " удален из команды.");
                return;
            }
            default:
                throw new WrongUsageException(getUsage(sender));
        }
    }

    private TextFormatting parseColor(String name) throws CommandException {
        for (TextFormatting format : TextFormatting.values()) {
            if (format.isColor() && format.getFriendlyName().equalsIgnoreCase(name)) {
                return format;
            }
        }
        throw new CommandException("Неизвестный цвет: " + name + " (например red, gold, aqua)");
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args,
                    "create", "invite", "accept", "decline", "kick", "leave", "delete", "color", "list", "info", "add", "remove");
        }
        String first = args[0].toLowerCase();
        if (args.length == 2 && ("invite".equals(first) || "kick".equals(first) || "add".equals(first) || "remove".equals(first))) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        if (args.length == 2 && ("color".equals(first) || "create".equals(first))) {
            List<String> colors = new ArrayList<String>();
            for (TextFormatting format : TextFormatting.values()) {
                if (format.isColor()) {
                    colors.add(format.getFriendlyName());
                }
            }
            return getListOfStringsMatchingLastWord(args, colors);
        }
        if (args.length == 2 && "info".equals(first)) {
            List<String> names = new ArrayList<String>();
            for (ScorePlayerTeam team : GroupManager.scoreboard(server).getTeams()) {
                names.add(team.getName());
            }
            return getListOfStringsMatchingLastWord(args, names);
        }
        return Collections.emptyList();
    }
}

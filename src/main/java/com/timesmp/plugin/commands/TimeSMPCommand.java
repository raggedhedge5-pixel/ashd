package com.timesmp.plugin.commands;

import com.timesmp.plugin.combat.CombatConfig;
import com.timesmp.plugin.data.DataStore;
import com.timesmp.plugin.data.GameState;
import com.timesmp.plugin.data.PlayerData;
import com.timesmp.plugin.quest.QuestManager;
import com.timesmp.plugin.rank.RankManager;
import com.timesmp.plugin.util.TabCompleteUtil;
import com.timesmp.plugin.util.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.Plugin;

import java.util.List;

/** /timesmp admin subcommands - reload config, start/stop/pause/restart the global timer, adjust balances, force a quest reroll. */
public class TimeSMPCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "start", "pause", "stop", "restart", "reload", "save", "settime", "addtime", "removetime", "reroll");
    private static final List<String> TIME_EDIT_SUBCOMMANDS = List.of("settime", "addtime", "removetime");
    private static final List<String> EXAMPLE_HOURS = List.of("0.5", "1", "2", "5", "10", "-2", "-5");

    private final Plugin plugin;
    private final DataStore dataStore;
    private final QuestManager questManager;
    private final RankManager rankManager;
    private final CombatConfig combatConfig;

    public TimeSMPCommand(Plugin plugin, DataStore dataStore, QuestManager questManager, RankManager rankManager, CombatConfig combatConfig) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        this.questManager = questManager;
        this.rankManager = rankManager;
        this.combatConfig = combatConfig;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("/timesmp <start|pause|stop|restart|reload|save|settime|addtime|removetime|reroll> ...", NamedTextColor.YELLOW));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start":
                dataStore.setState(GameState.RUNNING);
                dataStore.save();
                Bukkit.broadcast(Component.text("The timer has started! Time is now counting.", NamedTextColor.GREEN));
                return true;

            case "pause":
                dataStore.setState(GameState.PAUSED);
                dataStore.save();
                Bukkit.broadcast(Component.text("The timer has been paused.", NamedTextColor.YELLOW));
                return true;

            case "stop":
                dataStore.setState(GameState.STOPPED);
                dataStore.save();
                Bukkit.broadcast(Component.text("The timer has been stopped.", NamedTextColor.RED));
                return true;

            case "restart":
                dataStore.resetAllTimeAndQuests();
                dataStore.setState(GameState.RUNNING);
                dataStore.save();
                rankManager.refresh();
                Bukkit.broadcast(Component.text("Everyone's time has been reset - the timer has restarted!", NamedTextColor.GOLD));
                return true;

            case "reload":
                plugin.reloadConfig();
                dataStore.setStartingTimeSeconds(plugin.getConfig().getLong("starting-time-seconds", 0));
                combatConfig.reload();
                sender.sendMessage(Component.text("Config reloaded (config.yml + combatlog.yml).", NamedTextColor.GREEN));
                return true;

            case "save":
                dataStore.save();
                sender.sendMessage(Component.text("Player data saved.", NamedTextColor.GREEN));
                return true;

            case "settime":
            case "addtime":
            case "removetime":
                return handleTimeEdit(sender, args);

            case "reroll":
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /timesmp reroll <player>", NamedTextColor.RED));
                    return true;
                }
                PlayerData rerollData = lookup(sender, args[1]);
                if (rerollData == null) return true;
                questManager.assignRandomQuest(rerollData);
                sender.sendMessage(Component.text("Rerolled quest for " + rerollData.getLastKnownName() + ": " + rerollData.getActiveQuest().describe(), NamedTextColor.GREEN));
                return true;

            default:
                sender.sendMessage(Component.text("Unknown subcommand.", NamedTextColor.RED));
                return true;
        }
    }

    private boolean handleTimeEdit(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /timesmp " + args[0] + " <player> <hours>", NamedTextColor.RED));
            return true;
        }
        PlayerData data = lookup(sender, args[1]);
        if (data == null) return true;

        double hours;
        try {
            hours = Double.parseDouble(args[2]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(Component.text("'" + args[2] + "' isn't a number.", NamedTextColor.RED));
            return true;
        }
        long seconds = TimeUtil.hoursToSeconds(hours);

        // Deliberately NOT clamped to the -4h floor - this is the admin override tool,
        // meant to work regardless of that limit (e.g. to fully zero out a cheater).
        switch (args[0].toLowerCase()) {
            case "settime" -> data.setTimeSeconds(seconds);
            case "addtime" -> data.addTimeSeconds(seconds);
            case "removetime" -> data.addTimeSeconds(-seconds);
        }

        rankManager.refresh();
        sender.sendMessage(Component.text(data.getLastKnownName() + "'s time is now " + TimeUtil.format(data.getTimeSeconds()), NamedTextColor.GREEN));
        return true;
    }

    private PlayerData lookup(CommandSender sender, String name) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline.getUniqueId() == null || (!offline.hasPlayedBefore() && !offline.isOnline())) {
            sender.sendMessage(Component.text("No known player named '" + name + "'.", NamedTextColor.RED));
            return null;
        }
        return dataStore.getOrCreate(offline.getUniqueId(), offline.getName() != null ? offline.getName() : name);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompleteUtil.filterStartingWith(SUBCOMMANDS, args[0]);
        }
        String sub = args[0].toLowerCase();
        if (args.length == 2 && (TIME_EDIT_SUBCOMMANDS.contains(sub) || sub.equals("reroll"))) {
            return TabCompleteUtil.filterStartingWith(TabCompleteUtil.onlinePlayerNames(), args[1]);
        }
        if (args.length == 3 && TIME_EDIT_SUBCOMMANDS.contains(sub)) {
            return TabCompleteUtil.filterStartingWith(EXAMPLE_HOURS, args[2]);
        }
        return List.of();
    }
}

package com.timesmp.plugin.commands;

import com.timesmp.plugin.data.DataStore;
import com.timesmp.plugin.data.GameState;
import com.timesmp.plugin.data.PlayerData;
import com.timesmp.plugin.quest.Quest;
import com.timesmp.plugin.rank.RankManager;
import com.timesmp.plugin.scoreboard.ScoreboardManager;
import com.timesmp.plugin.util.TabCompleteUtil;
import com.timesmp.plugin.util.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/** Handles the player-facing commands: /playtime, /quest, /top, /timeboard. */
public class PlayerCommands implements CommandExecutor, TabCompleter {

    private final DataStore dataStore;
    private final RankManager rankManager;
    private final ScoreboardManager scoreboardManager;

    public PlayerCommands(DataStore dataStore, RankManager rankManager, ScoreboardManager scoreboardManager) {
        this.dataStore = dataStore;
        this.rankManager = rankManager;
        this.scoreboardManager = scoreboardManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (label.toLowerCase()) {
            case "playtime":
                return handlePlaytime(sender, args);
            case "quest":
                return handleQuest(sender);
            case "top":
                return handleTop(sender);
            case "timeboard":
                return handleTimeboard(sender);
            default:
                return false;
        }
    }

    private boolean handlePlaytime(CommandSender sender, String[] args) {
        PlayerData data;
        String targetName;
        if (args.length > 0) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(args[0]);
            data = dataStore.get(offline.getUniqueId());
            targetName = offline.getName() != null ? offline.getName() : args[0];
            if (data == null) {
                sender.sendMessage(Component.text("No time data for '" + targetName + "' yet.", NamedTextColor.RED));
                return true;
            }
        } else {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Console must specify a player: /playtime <player>", NamedTextColor.RED));
                return true;
            }
            data = dataStore.getOrCreate(player.getUniqueId(), player.getName());
            targetName = player.getName();
        }

        sender.sendMessage(Component.text(targetName + "'s time: ", NamedTextColor.GRAY)
                .append(TimeUtil.formatColored(data.getTimeSeconds()))
                .append(Component.text("  (Rank " + (data.getCurrentRank() > 0 ? "#" + data.getCurrentRank() : "unranked") + ")", NamedTextColor.GOLD)));
        return true;
    }

    private boolean handleQuest(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can check their quest.", NamedTextColor.RED));
            return true;
        }
        if (dataStore.getState() != GameState.RUNNING) {
            sender.sendMessage(Component.text(dataStore.getState() == GameState.PAUSED
                    ? "The timer is paused - no quests right now." : "The timer hasn't started yet - no quests right now.", NamedTextColor.DARK_GRAY));
            return true;
        }
        PlayerData data = dataStore.getOrCreate(player.getUniqueId(), player.getName());
        Quest quest = data.getActiveQuest();
        if (quest == null) {
            long now = System.currentTimeMillis() / 1000L;
            long wait = data.getNextQuestAvailableAtEpochSeconds() - now;
            if (wait > 0) {
                sender.sendMessage(Component.text("No active quest - next one in " + TimeUtil.formatDuration(wait) + ".", NamedTextColor.GRAY));
            } else {
                sender.sendMessage(Component.text("No active quest - you'll get one shortly.", NamedTextColor.GRAY));
            }
            return true;
        }
        sender.sendMessage(Component.text(""));
        sender.sendMessage(Component.text("Current quest: ", NamedTextColor.AQUA, TextDecoration.BOLD)
                .append(Component.text(quest.describe(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Progress: ", NamedTextColor.GRAY)
                .append(Component.text(quest.describeProgress(), NamedTextColor.GREEN)));
        sender.sendMessage(Component.text("Reward: ", NamedTextColor.GRAY)
                .append(Component.text("+" + String.format("%.1f", quest.getRewardSeconds() / 3600.0) + " hours", NamedTextColor.GOLD)));
        return true;
    }

    private boolean handleTop(CommandSender sender) {
        List<PlayerData> sorted = rankManager.getSortedCache();
        sender.sendMessage(Component.text(""));
        sender.sendMessage(Component.text("=== Top Time Leaderboard ===", NamedTextColor.GOLD, TextDecoration.BOLD));
        int limit = Math.min(10, sorted.size());
        for (int i = 0; i < limit; i++) {
            PlayerData data = sorted.get(i);
            sender.sendMessage(Component.text("#" + (i + 1) + " ", NamedTextColor.YELLOW)
                    .append(Component.text(data.getLastKnownName(), NamedTextColor.WHITE))
                    .append(Component.text(" - ", NamedTextColor.GRAY))
                    .append(TimeUtil.formatColored(data.getTimeSeconds())));
        }
        if (sorted.isEmpty()) {
            sender.sendMessage(Component.text("Nobody's on the board yet.", NamedTextColor.GRAY));
        }
        return true;
    }

    private boolean handleTimeboard(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can toggle the scoreboard.", NamedTextColor.RED));
            return true;
        }
        PlayerData data = dataStore.getOrCreate(player.getUniqueId(), player.getName());
        if (data.isScoreboardEnabled()) {
            scoreboardManager.disable(player);
            sender.sendMessage(Component.text("Sidebar scoreboard disabled.", NamedTextColor.YELLOW));
        } else {
            scoreboardManager.enable(player);
            sender.sendMessage(Component.text("Sidebar scoreboard enabled.", NamedTextColor.GREEN));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (alias.equalsIgnoreCase("playtime") && args.length == 1) {
            return TabCompleteUtil.filterStartingWith(TabCompleteUtil.onlinePlayerNames(), args[0]);
        }
        return List.of();
    }
}

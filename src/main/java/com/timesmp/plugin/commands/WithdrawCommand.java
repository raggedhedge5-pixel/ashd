package com.timesmp.plugin.commands;

import com.timesmp.plugin.data.DataStore;
import com.timesmp.plugin.data.PlayerData;
import com.timesmp.plugin.inventory.HourToken;
import com.timesmp.plugin.rank.RankManager;
import com.timesmp.plugin.util.TabCompleteUtil;
import com.timesmp.plugin.util.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;

/** /withdraw <hours> - turns whole hours of banked time into tradeable 1-hour clock items. */
public class WithdrawCommand implements CommandExecutor, TabCompleter {

    private static final List<String> EXAMPLE_HOURS = List.of("1", "2", "5", "10");

    private final Plugin plugin;
    private final DataStore dataStore;
    private final RankManager rankManager;
    private final HourToken hourToken;

    public WithdrawCommand(Plugin plugin, DataStore dataStore, RankManager rankManager, HourToken hourToken) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        this.rankManager = rankManager;
        this.hourToken = hourToken;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can withdraw.", NamedTextColor.RED));
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(Component.text("Usage: /withdraw <hours>", NamedTextColor.RED));
            return true;
        }

        int hours;
        try {
            hours = Integer.parseInt(args[0]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(Component.text("'" + args[0] + "' isn't a whole number of hours.", NamedTextColor.RED));
            return true;
        }
        if (hours <= 0) {
            sender.sendMessage(Component.text("Enter a positive number of hours.", NamedTextColor.RED));
            return true;
        }

        PlayerData data = dataStore.getOrCreate(player.getUniqueId(), player.getName());
        FileConfiguration config = plugin.getConfig();
        long floorSeconds = TimeUtil.hoursToSeconds(config.getDouble("min-time-hours", -4));
        long requestedSeconds = TimeUtil.hoursToSeconds(hours);
        long balanceAfter = data.getTimeSeconds() - requestedSeconds;

        if (balanceAfter < floorSeconds) {
            long maxWithdrawableHours = (data.getTimeSeconds() - floorSeconds) / 3600L;
            sender.sendMessage(Component.text("You can't withdraw that much - you'd drop below the " +
                    TimeUtil.formatDuration(floorSeconds) + " floor. Max right now: " + Math.max(0, maxWithdrawableHours) + "h.", NamedTextColor.RED));
            return true;
        }

        data.addTimeSeconds(-requestedSeconds);
        rankManager.refresh();

        var leftover = player.getInventory().addItem(hourToken.create(hours));
        for (var notFitted : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), notFitted);
        }

        sender.sendMessage(Component.text("Withdrew " + hours + "h into clock items. New balance: ", NamedTextColor.GREEN)
                .append(TimeUtil.formatColored(data.getTimeSeconds())));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompleteUtil.filterStartingWith(EXAMPLE_HOURS, args[0]);
        }
        return List.of();
    }
}

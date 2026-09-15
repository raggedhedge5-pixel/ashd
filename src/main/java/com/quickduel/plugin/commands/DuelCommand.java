package com.quickduel.plugin.commands;

import com.quickduel.plugin.duel.DuelManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class DuelCommand implements CommandExecutor, TabCompleter {

    private final DuelManager duelManager;

    public DuelCommand(DuelManager duelManager) {
        this.duelManager = duelManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can duel.", NamedTextColor.RED));
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(Component.text("Usage: /duel <player|accept|decline>", NamedTextColor.RED));
            return true;
        }

        String arg = args[0];
        if (arg.equalsIgnoreCase("accept")) {
            duelManager.openConfirmation(player);
            return true;
        }
        if (arg.equalsIgnoreCase("decline")) {
            duelManager.decline(player);
            return true;
        }

        Player target = Bukkit.getPlayerExact(arg);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(Component.text("'" + arg + "' isn't online.", NamedTextColor.RED));
            return true;
        }
        duelManager.challenge(player, target);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();

        List<String> options = new ArrayList<>();
        options.add("accept");
        options.add("decline");
        for (Player player : Bukkit.getOnlinePlayers()) {
            options.add(player.getName());
        }

        String typedLower = args[0].toLowerCase();
        List<String> matches = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase().startsWith(typedLower)) {
                matches.add(option);
            }
        }
        return matches;
    }
}

package com.timesmp.plugin.commands;

import com.timesmp.plugin.inventory.ExtraInventoryManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ExtraInventoryCommand implements CommandExecutor {

    private final ExtraInventoryManager extraInventoryManager;

    public ExtraInventoryCommand(ExtraInventoryManager extraInventoryManager) {
        this.extraInventoryManager = extraInventoryManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can open the extra inventory.", NamedTextColor.RED));
            return true;
        }
        extraInventoryManager.open(player);
        return true;
    }
}

package com.timesmp.plugin.combat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * While tagged, blocks every command except /msg - no /tpa, /spawn, /home, /warp,
 * etc. to dodge a fight. Players with timesmp.admin bypass this (so you don't
 * accidentally lock yourself out of admin commands while testing combat).
 */
public class CombatCommandBlockListener implements Listener {

    private final CombatManager combatManager;
    private final CombatConfig config;

    public CombatCommandBlockListener(CombatManager combatManager, CombatConfig config) {
        this.combatManager = combatManager;
        this.config = config;
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("timesmp.admin")) return;
        if (config.isWorldDisabled(player.getWorld())) return;
        if (!combatManager.isInCombat(player.getUniqueId())) return;

        String message = event.getMessage();
        int spaceIndex = message.indexOf(' ');
        String label = (spaceIndex == -1 ? message : message.substring(0, spaceIndex)).substring(1).toLowerCase();

        if (label.equals("msg")) return;

        event.setCancelled(true);
        player.sendMessage(Component.text("You can't use commands while in combat! (except /msg)", NamedTextColor.RED));
    }
}

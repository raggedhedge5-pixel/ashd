package com.timesmp.plugin.combat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Bounces a combat-tagged player back out if they try to walk into one of the
 * WorldGuard regions listed under worldguard.blocked-regions in combatlog.yml.
 * Does nothing at all if WorldGuard isn't installed, or the list is empty.
 */
public class CombatRegionListener implements Listener {

    private final CombatManager combatManager;
    private final CombatConfig config;
    private final WorldGuardHook worldGuardHook;

    public CombatRegionListener(CombatManager combatManager, CombatConfig config, WorldGuardHook worldGuardHook) {
        this.combatManager = combatManager;
        this.config = config;
        this.worldGuardHook = worldGuardHook;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!worldGuardHook.isAvailable()) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        // Only care about actual block-to-block movement, not just looking around.
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        if (!combatManager.isInCombat(player.getUniqueId())) return;
        if (config.isWorldDisabled(player.getWorld())) return;

        List<String> blockedRegions = config.blockedRegions();
        if (blockedRegions.isEmpty()) return;

        boolean wasOutside = !worldGuardHook.isInAnyRegion(from, blockedRegions);
        boolean enteringNow = worldGuardHook.isInAnyRegion(to, blockedRegions);

        if (wasOutside && enteringNow) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("You can't enter there while in combat!", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_SLIME_JUMP, 1f, 0.7f);

            // A little push back the way they came, so it actually feels like a bounce
            // rather than just an invisible wall.
            Vector pushBack = from.toVector().subtract(to.toVector()).normalize().multiply(0.6);
            pushBack.setY(0.2);
            player.setVelocity(pushBack);
        }
    }
}

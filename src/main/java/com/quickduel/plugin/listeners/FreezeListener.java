package com.quickduel.plugin.listeners;

import com.quickduel.plugin.duel.FreezeManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/** Locks frozen players in place (snaps back on move) and blocks them from dealing or taking damage, during a duel countdown. */
public class FreezeListener implements Listener {

    private final FreezeManager freezeManager;

    public FreezeListener(FreezeManager freezeManager) {
        this.freezeManager = freezeManager;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!freezeManager.isFrozen(player.getUniqueId())) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) {
            return; // just looking around, not actually moving - fine
        }

        Location freezeLoc = freezeManager.getFreezeLocation(player.getUniqueId());
        if (freezeLoc != null) {
            event.setTo(freezeLoc);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim && freezeManager.isFrozen(victim.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (event.getDamager() instanceof Player attacker && freezeManager.isFrozen(attacker.getUniqueId())) {
            event.setCancelled(true);
        }
    }
}

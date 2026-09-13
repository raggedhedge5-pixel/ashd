package com.timesmp.plugin.afk;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Feeds AfkManager only on genuine click events:
 *   - PlayerAnimationEvent: fires on the arm-swing animation, i.e. a left click
 *     (whether it lands on a block, an entity, or thin air).
 *   - PlayerInteractEvent: covers right-clicks (and left-clicks on blocks too,
 *     which overlaps with the animation event above - harmless, just means
 *     activity gets recorded twice for the same click).
 * Deliberately nothing here for movement, chat, or commands - see AfkManager.
 */
public class AfkListener implements Listener {

    private final AfkManager afkManager;

    public AfkListener(AfkManager afkManager) {
        this.afkManager = afkManager;
    }

    @EventHandler
    public void onSwing(PlayerAnimationEvent event) {
        afkManager.recordActivity(event.getPlayer());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        afkManager.recordActivity(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        afkManager.recordActivity(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        afkManager.forget(event.getPlayer().getUniqueId());
    }
}

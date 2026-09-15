package com.quickduel.plugin.duel;

import net.kyori.adventure.title.Title;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;

/**
 * Runs the whole accept sequence as a 1-tick-per-second state machine:
 *   5,4,3,2,1 ("don't move") while frozen at the CURRENT location
 *   -> teleport both players to the pre-rolled destination (same location whose
 *      biome was already shown in the confirmation GUI, not a fresh roll)
 *   -> 3,2,1,GO while frozen at the NEW location
 *   -> unfreeze, duel begins
 * Both players stay frozen (can't move, can't deal or take damage - see
 * FreezeListener) for the entire sequence, including the moment of teleport.
 */
public class DuelCountdownTask extends BukkitRunnable {

    private final FreezeManager freezeManager;
    private final Player p1;
    private final Player p2;
    private final Location destination;
    private final double separation;

    private int tick = 0;

    public DuelCountdownTask(Plugin plugin, FreezeManager freezeManager, Player p1, Player p2, Location destination, double separation) {
        this.freezeManager = freezeManager;
        this.p1 = p1;
        this.p2 = p2;
        this.destination = destination;
        this.separation = separation;
    }

    public void start(Plugin plugin) {
        freezeManager.freeze(p1);
        freezeManager.freeze(p2);
        runTaskTimer(plugin, 0L, 20L);
    }

    @Override
    public void run() {
        if (!p1.isOnline() || !p2.isOnline()) {
            cleanupAndCancel();
            return;
        }

        switch (tick) {
            case 0 -> sendCountdown("5", "&7Don't move!");
            case 1 -> sendCountdown("4", "&7Don't move!");
            case 2 -> sendCountdown("3", "&7Don't move!");
            case 3 -> sendCountdown("2", "&7Don't move!");
            case 4 -> sendCountdown("1", "&7Don't move!");
            case 5 -> {
                Location loc1 = destination.clone();
                Location loc2 = destination.clone().add(separation, 0, 0);

                p1.teleport(loc1);
                p2.teleport(loc2);
                p1.lookAt(loc2);
                p2.lookAt(loc1);

                // Keep them frozen at the NEW spot - otherwise the move-lock would
                // try to snap them back to before the teleport.
                freezeManager.updateFreezeLocation(p1, loc1);
                freezeManager.updateFreezeLocation(p2, loc2);

                sendCountdown("3", null);
            }
            case 6 -> sendCountdown("2", null);
            case 7 -> sendCountdown("1", null);
            case 8 -> {
                sendTitle(p1, Component.text("GO!", NamedTextColor.GREEN));
                sendTitle(p2, Component.text("GO!", NamedTextColor.GREEN));
                cleanupAndCancel();
                return;
            }
        }

        tick++;
    }

    private void sendCountdown(String number, String subtitleLegacy) {
        Component title = Component.text(number, NamedTextColor.YELLOW);
        Component subtitle = subtitleLegacy == null ? Component.empty()
                : Component.text(subtitleLegacy.replace("&7", ""), NamedTextColor.GRAY);
        Title t = Title.title(title, subtitle, Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ZERO));
        p1.showTitle(t);
        p2.showTitle(t);
    }

    private void sendTitle(Player player, Component title) {
        player.showTitle(Title.title(title, Component.empty(), Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofMillis(300))));
    }

    private void cleanupAndCancel() {
        freezeManager.unfreeze(p1);
        freezeManager.unfreeze(p2);
        this.cancel();
    }
}

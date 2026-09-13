package com.timesmp.plugin.afk;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks the last time each player did something that counts as "not AFK" - which,
 * by design, is ONLY a physical left- or right-click (see AfkListener). Movement,
 * chat, and commands deliberately do NOT reset this timer, so an auto-walk/auto-jump
 * macro won't stop the kick from happening - only real clicks do.
 */
public class AfkManager {

    private final Plugin plugin;
    private final Map<UUID, Long> lastClickMillis = new HashMap<>();

    public AfkManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void recordActivity(Player player) {
        lastClickMillis.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public void forget(UUID uuid) {
        lastClickMillis.remove(uuid);
    }

    /** Call once a second for every online player. */
    public void tick() {
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("afk-kick.enabled", true)) return;

        long timeoutMs = config.getLong("afk-kick.timeout-minutes", 5) * 60_000L;
        long warningMs = config.getLong("afk-kick.warning-seconds", 30) * 1000L;
        long now = System.currentTimeMillis();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("timesmp.afk.bypass")) continue;

            long lastClick = lastClickMillis.computeIfAbsent(player.getUniqueId(), id -> now);
            long idleMs = now - lastClick;

            if (idleMs >= timeoutMs) {
                lastClickMillis.remove(player.getUniqueId());
                player.kick(Component.text("Kicked for being AFK (no clicks for " +
                        config.getLong("afk-kick.timeout-minutes", 5) + " minutes)", NamedTextColor.RED));
                continue;
            }

            long remainingMs = timeoutMs - idleMs;
            if (remainingMs <= warningMs) {
                long remainingSeconds = Math.max(1, remainingMs / 1000L);
                player.sendActionBar(Component.text("AFK kick in " + remainingSeconds + "s - click to cancel!", NamedTextColor.YELLOW));
            }
        }
    }
}

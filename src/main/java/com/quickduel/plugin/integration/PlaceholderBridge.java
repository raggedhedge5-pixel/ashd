package com.quickduel.plugin.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.logging.Level;

/**
 * Reads TimeSMP's rank via PlaceholderAPI, if both are installed - QuickDuel has
 * no direct dependency on TimeSMP, it just asks PlaceholderAPI to resolve
 * %timesmp_rank_tag% for a player, the same way any server-side placeholder
 * would be read. Defensive: only touches PlaceholderAPI's class if the plugin is
 * actually present, and fails open (returns null) if anything goes wrong or the
 * placeholder isn't registered (e.g. TimeSMP isn't installed).
 */
public final class PlaceholderBridge {

    private PlaceholderBridge() {}

    public static boolean isAvailable() {
        return Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    /** The player's TimeSMP rank tag (e.g. "#7"), or null if unavailable for any reason. */
    public static String getRankTag(Player player) {
        if (!isAvailable()) return null;
        try {
            String resolved = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, "%timesmp_rank_tag%");
            if (resolved == null || resolved.equals("%timesmp_rank_tag%")) return null; // unresolved = not registered
            return resolved;
        } catch (Throwable ex) {
            Bukkit.getLogger().log(Level.WARNING, "[QuickDuel] Rank placeholder lookup failed - skipping rank display", ex);
            return null;
        }
    }
}

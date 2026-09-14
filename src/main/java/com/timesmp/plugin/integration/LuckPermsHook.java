package com.timesmp.plugin.integration;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.logging.Level;

/**
 * Thin wrapper around LuckPerms' API, used to pull a player's suffix so TimeSMP
 * can append it after its own rank tag in the tab-list and nametags - needed now
 * that a tab-list-formatting plugin (like TAB) isn't present to translate
 * LuckPerms' data into those two systems via placeholders itself. Without either
 * TAB or this hook, LuckPerms metadata never reaches tab-list/nametags on its own -
 * LuckPerms is a permissions plugin, it doesn't render anything by itself.
 *
 * Defensive the same way as WorldGuardHook: only touches LuckPerms classes if the
 * plugin is actually installed, and fails open (returns an empty Component) if
 * anything goes wrong, rather than ever breaking tab-list/nametag rendering.
 *
 * CAVEAT: same as WorldGuard/PlaceholderAPI before it - I could not compile-test
 * this against a real LuckPerms jar (no network access to their repo from this
 * sandbox). If the build fails on this file, or the suffix never shows despite
 * LuckPerms being installed and a suffix being set, tell me and I'll adjust the
 * exact calls. It also assumes the suffix is stored with '&' colour codes (the
 * common convention) - if yours use raw '§' codes instead, tell me and I'll
 * switch the parser.
 */
public class LuckPermsHook {

    private final boolean available;

    public LuckPermsHook() {
        this.available = Bukkit.getPluginManager().getPlugin("LuckPerms") != null;
    }

    public boolean isAvailable() {
        return available;
    }

    /** The player's effective LuckPerms suffix as a Component (colours preserved), or empty if none/unavailable. */
    public Component getSuffix(Player player) {
        if (!available) return Component.empty();
        try {
            net.luckperms.api.LuckPerms luckPerms = net.luckperms.api.LuckPermsProvider.get();
            net.luckperms.api.model.user.User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user == null) return Component.empty();

            String suffix = user.getCachedData().getMetaData().getSuffix();
            if (suffix == null || suffix.isEmpty()) return Component.empty();

            return LegacyComponentSerializer.legacyAmpersand().deserialize(suffix);
        } catch (Throwable ex) {
            Bukkit.getLogger().log(Level.WARNING, "[TimeSMP] LuckPerms suffix lookup failed - failing open (no suffix shown)", ex);
            return Component.empty();
        }
    }
}

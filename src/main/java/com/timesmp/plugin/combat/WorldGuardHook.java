package com.timesmp.plugin.combat;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Set;
import java.util.logging.Level;

/**
 * Thin wrapper around WorldGuard's region API, used only for the "bounce out of
 * these regions while combat-tagged" feature. Deliberately defensive:
 *   - Never touches any WorldGuard class unless the WorldGuard plugin is actually
 *     present (checked first), so servers without WorldGuard never even attempt
 *     to load those classes (no NoClassDefFoundError).
 *   - Wraps every real call in a try/catch that fails OPEN (returns "not blocked")
 *     rather than breaking normal movement if anything about the integration is off.
 *
 * IMPORTANT CAVEAT: I was not able to compile-test this against a real WorldGuard
 * jar - my sandbox has no network access to EngineHub's Maven repository, the same
 * limitation that applies to Paper's own API (see the top-level README). The method
 * names/API shape below match WorldGuard 7's public API as documented, but if the
 * build fails specifically in this file, or regions never trigger a bounce even
 * though WorldGuard is installed and the region ID is right, paste me the error and
 * I'll adjust the exact calls.
 */
public class WorldGuardHook {

    private final boolean available;

    public WorldGuardHook() {
        this.available = Bukkit.getPluginManager().getPlugin("WorldGuard") != null;
    }

    public boolean isAvailable() {
        return available;
    }

    /** True if the location falls inside any of the given WorldGuard region IDs. */
    public boolean isInAnyRegion(Location location, List<String> regionIds) {
        if (!available || regionIds == null || regionIds.isEmpty() || location.getWorld() == null) {
            return false;
        }
        try {
            com.sk89q.worldedit.world.World weWorld = com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(location.getWorld());
            com.sk89q.worldguard.protection.managers.RegionManager regionManager =
                    com.sk89q.worldguard.WorldGuard.getInstance().getPlatform().getRegionContainer().get(weWorld);
            if (regionManager == null) return false;

            com.sk89q.worldedit.math.BlockVector3 point = com.sk89q.worldedit.bukkit.BukkitAdapter.asBlockVector(location);
            Set<com.sk89q.worldguard.protection.regions.ProtectedRegion> regions =
                    regionManager.getApplicableRegions(point).getRegions();

            for (com.sk89q.worldguard.protection.regions.ProtectedRegion region : regions) {
                if (regionIds.contains(region.getId())) {
                    return true;
                }
            }
        } catch (Throwable ex) {
            Bukkit.getLogger().log(Level.WARNING, "[TimeSMP] WorldGuard region check failed - failing open (not blocking movement)", ex);
        }
        return false;
    }
}

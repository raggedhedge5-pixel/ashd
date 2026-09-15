package com.quickduel.plugin.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Picks a random point within a radius of world spawn and finds a reasonably
 * safe Y to stand on (avoiding landing in lava or the void). This forces chunk
 * generation for whatever spot gets picked (Bukkit does this synchronously under
 * the hood via getHighestBlockYAt), so an occasional /duel causing a brief pause
 * while a fresh chunk generates is expected and fine - this isn't meant to be
 * called rapidly/repeatedly.
 */
public final class SafeLocationFinder {

    private SafeLocationFinder() {}

    /** Uniformly random point within radius of the world's spawn, retried up to maxAttempts times if unsafe. */
    public static Location findSafeSpot(World world, double radius, int maxAttempts) {
        Location center = world.getSpawnLocation();

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
            // sqrt() keeps the distribution uniform over the AREA of the circle, not biased toward the center.
            double distance = Math.sqrt(ThreadLocalRandom.current().nextDouble()) * radius;

            double x = center.getX() + distance * Math.cos(angle);
            double z = center.getZ() + distance * Math.sin(angle);

            int blockX = (int) Math.floor(x);
            int blockZ = (int) Math.floor(z);
            int highestY = world.getHighestBlockYAt(blockX, blockZ);

            if (highestY <= world.getMinHeight() + 1) {
                continue; // basically the void - try again
            }

            Material groundType = world.getBlockAt(blockX, highestY, blockZ).getType();
            if (groundType == Material.LAVA || isWater(groundType)) {
                continue; // land only - never water, never lava
            }

            return new Location(world, blockX + 0.5, highestY + 1, blockZ + 0.5);
        }

        // Gave up finding a safe random spot - spawn is always a reasonable fallback.
        return center;
    }

    private static boolean isWater(Material material) {
        return material == Material.WATER || material == Material.KELP || material == Material.KELP_PLANT
                || material == Material.SEAGRASS || material == Material.TALL_SEAGRASS;
    }
}

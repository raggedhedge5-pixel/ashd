package com.quickduel.plugin.duel;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Tracks which players are currently frozen (duel countdown in progress) and where to snap them back to if they try to move. */
public class FreezeManager {

    private final Map<UUID, Location> frozen = new HashMap<>();

    public void freeze(Player player) {
        frozen.put(player.getUniqueId(), player.getLocation());
    }

    public void updateFreezeLocation(Player player, Location location) {
        if (frozen.containsKey(player.getUniqueId())) {
            frozen.put(player.getUniqueId(), location);
        }
    }

    public void unfreeze(Player player) {
        frozen.remove(player.getUniqueId());
    }

    public boolean isFrozen(UUID uuid) {
        return frozen.containsKey(uuid);
    }

    public Location getFreezeLocation(UUID uuid) {
        return frozen.get(uuid);
    }
}

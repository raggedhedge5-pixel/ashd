package com.timesmp.plugin.util;

import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

/** Checks the disabled-worlds config list - worlds where TimeSMP should act as if it isn't installed. */
public class WorldFilter {

    private final Plugin plugin;

    public WorldFilter(Plugin plugin) {
        this.plugin = plugin;
    }

    public boolean isDisabled(World world) {
        if (world == null) return false;
        FileConfiguration config = plugin.getConfig();
        return config.getStringList("disabled-worlds").contains(world.getName());
    }
}

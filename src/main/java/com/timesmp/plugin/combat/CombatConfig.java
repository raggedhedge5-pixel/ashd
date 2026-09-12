package com.timesmp.plugin.combat;

import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * Wraps combatlog.yml - a separate config file from the main config.yml, per
 * request, so the combat-log feature is self-contained and easy to find/edit.
 */
public class CombatConfig {

    private final Plugin plugin;
    private final File file;
    private FileConfiguration config;

    public CombatConfig(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "combatlog.yml");
    }

    public void load() {
        if (!file.exists()) {
            plugin.saveResource("combatlog.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);

        // Fill in any keys missing from an older copy of the file with the bundled defaults.
        try (InputStream defStream = plugin.getResource("combatlog.yml")) {
            if (defStream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8));
                config.setDefaults(defaults);
            }
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to load combatlog.yml defaults", ex);
        }
    }

    public void reload() {
        load();
    }

    public int durationSeconds() {
        return config.getInt("combat-duration-seconds", 30);
    }

    public boolean showActionbar() {
        return config.getBoolean("show-actionbar", true);
    }

    public int pearlCooldownSeconds() {
        return config.getInt("pearl-cooldown-seconds", 30);
    }

    public int maceCooldownSeconds() {
        return config.getInt("mace-cooldown-seconds", 30);
    }

    public boolean blockElytra() {
        return config.getBoolean("block-elytra", true);
    }

    public boolean punishCombatLog() {
        return config.getBoolean("punish-combat-log", true);
    }

    public boolean isWorldDisabled(World world) {
        if (world == null) return false;
        return config.getStringList("disabled-worlds").contains(world.getName());
    }
}

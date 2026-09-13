package com.timesmp.plugin.data;

import com.timesmp.plugin.quest.Quest;
import com.timesmp.plugin.quest.QuestType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * In-memory cache of all known players' data, mirrored to a single
 * playerdata.yml file. Loaded once on startup, saved on an interval and on
 * shutdown/player-quit.
 */
public class DataStore {

    private final Plugin plugin;
    private final File file;
    private final Map<UUID, PlayerData> cache = new HashMap<>();
    private long startingTimeSeconds = 0;
    private GameState state = GameState.STOPPED;

    public DataStore(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "playerdata.yml");
    }

    public void setStartingTimeSeconds(long seconds) {
        this.startingTimeSeconds = seconds;
    }

    public GameState getState() {
        return state;
    }

    public void setState(GameState state) {
        this.state = state;
    }

    /** Resets every known player's time and quest state back to fresh, keeping everything else (inventory, prefs). */
    public void resetAllTimeAndQuests() {
        for (PlayerData data : cache.values()) {
            data.setTimeSeconds(startingTimeSeconds);
            data.setActiveQuest(null);
            data.setNextQuestAvailableAtEpochSeconds(0);
        }
    }

    public PlayerData getOrCreate(UUID uuid, String name) {
        PlayerData data = cache.get(uuid);
        if (data == null) {
            data = new PlayerData(uuid, name, startingTimeSeconds);
            cache.put(uuid, data);
        } else {
            data.setLastKnownName(name);
        }
        return data;
    }

    public PlayerData get(UUID uuid) {
        return cache.get(uuid);
    }

    public Map<UUID, PlayerData> all() {
        return cache;
    }

    public void load() {
        cache.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        try {
            state = GameState.valueOf(yaml.getString("state", GameState.STOPPED.name()));
        } catch (IllegalArgumentException ex) {
            state = GameState.STOPPED;
        }
        ConfigurationSection playersSection = yaml.getConfigurationSection("players");
        if (playersSection == null) return;

        for (String key : playersSection.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                ConfigurationSection s = playersSection.getConfigurationSection(key);
                if (s == null) continue;

                String name = s.getString("name", "Unknown");
                long time = s.getLong("time", startingTimeSeconds);
                PlayerData data = new PlayerData(uuid, name, time);
                data.setScoreboardEnabled(s.getBoolean("scoreboard-enabled", true));
                data.setNextQuestAvailableAtEpochSeconds(s.getLong("next-quest-available", 0));
                data.setKills(s.getInt("kills", 0));
                data.setDeaths(s.getInt("deaths", 0));

                ConfigurationSection q = s.getConfigurationSection("quest");
                if (q != null) {
                    QuestType type = QuestType.valueOf(q.getString("type"));
                    double target = q.getDouble("target");
                    long reward = q.getLong("reward-seconds");
                    long assignedAt = q.getLong("assigned-at");
                    Quest quest = new Quest(type, target, reward, assignedAt);
                    quest.setProgress(q.getDouble("progress", 0));
                    List<String> kills = q.getStringList("unique-kills");
                    for (String k : kills) {
                        try {
                            quest.getUniqueKills().add(UUID.fromString(k));
                        } catch (IllegalArgumentException ignored) {}
                    }
                    data.setActiveQuest(quest);
                }

                List<?> rawItems = s.getList("extra-inventory");
                if (rawItems != null) {
                    List<ItemStack> items = new ArrayList<>();
                    for (Object o : rawItems) {
                        if (o instanceof ItemStack itemStack) {
                            items.add(itemStack);
                        }
                    }
                    data.setExtraInventoryItems(items);
                }

                cache.put(uuid, data);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to load player data entry '" + key + "'", ex);
            }
        }
        plugin.getLogger().info("Loaded time data for " + cache.size() + " players.");
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("state", state.name());
        ConfigurationSection playersSection = yaml.createSection("players");

        for (Map.Entry<UUID, PlayerData> entry : cache.entrySet()) {
            PlayerData data = entry.getValue();
            ConfigurationSection s = playersSection.createSection(entry.getKey().toString());
            s.set("name", data.getLastKnownName());
            s.set("time", data.getTimeSeconds());
            s.set("scoreboard-enabled", data.isScoreboardEnabled());
            s.set("next-quest-available", data.getNextQuestAvailableAtEpochSeconds());
            s.set("kills", data.getKills());
            s.set("deaths", data.getDeaths());

            Quest quest = data.getActiveQuest();
            if (quest != null) {
                ConfigurationSection q = s.createSection("quest");
                q.set("type", quest.getType().name());
                q.set("target", quest.getTarget());
                q.set("reward-seconds", quest.getRewardSeconds());
                q.set("assigned-at", quest.getAssignedAtEpochSeconds());
                q.set("progress", quest.getProgress());
                List<String> kills = new ArrayList<>();
                for (UUID u : quest.getUniqueKills()) kills.add(u.toString());
                q.set("unique-kills", kills);
            }

            s.set("extra-inventory", data.getExtraInventoryItems());
        }

        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save playerdata.yml", ex);
        }
    }
}

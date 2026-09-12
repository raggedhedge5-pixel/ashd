package com.timesmp.plugin.tasks;

import com.timesmp.plugin.afk.AfkManager;
import com.timesmp.plugin.combat.CombatManager;
import com.timesmp.plugin.data.DataStore;
import com.timesmp.plugin.data.GameState;
import com.timesmp.plugin.data.PlayerData;
import com.timesmp.plugin.quest.QuestManager;
import com.timesmp.plugin.rank.RankManager;
import com.timesmp.plugin.scoreboard.ScoreboardManager;
import com.timesmp.plugin.util.WorldFilter;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * The core heartbeat: every second, ticks every online player's banked time up by
 * one second and advances quest progress - skipping anyone currently on the death
 * screen (Player#isDead()) or standing in a disabled world. On its own configurable
 * interval it also recalculates the leaderboard/hearts/tab and refreshes sidebar
 * scoreboards, and periodically autosaves. The AFK check runs every second too, but
 * deliberately OUTSIDE the RUNNING gate - AFK kicking isn't part of the time economy,
 * it should work regardless of whether an admin has started the timer.
 */
public class TimerTask extends BukkitRunnable {

    private final Plugin plugin;
    private final DataStore dataStore;
    private final QuestManager questManager;
    private final RankManager rankManager;
    private final ScoreboardManager scoreboardManager;
    private final WorldFilter worldFilter;
    private final AfkManager afkManager;
    private final CombatManager combatManager;
    private final FileConfiguration config;

    private long secondsElapsed = 0;

    public TimerTask(Plugin plugin, DataStore dataStore, QuestManager questManager,
                      RankManager rankManager, ScoreboardManager scoreboardManager, WorldFilter worldFilter,
                      AfkManager afkManager, CombatManager combatManager) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        this.questManager = questManager;
        this.rankManager = rankManager;
        this.scoreboardManager = scoreboardManager;
        this.worldFilter = worldFilter;
        this.afkManager = afkManager;
        this.combatManager = combatManager;
        this.config = plugin.getConfig();
    }

    @Override
    public void run() {
        secondsElapsed++;

        if (dataStore.getState() == GameState.RUNNING) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.isDead() || worldFilter.isDisabled(player.getWorld())) continue;
                PlayerData data = dataStore.getOrCreate(player.getUniqueId(), player.getName());
                data.addTimeSeconds(1);
                questManager.tickOnlinePlayer(data);
            }
        }

        afkManager.tick();
        combatManager.tick();

        long refreshEvery = Math.max(1, config.getLong("rank-refresh-seconds", 5));
        if (secondsElapsed % refreshEvery == 0) {
            rankManager.refresh();
            scoreboardManager.refreshAll();
        }

        long autosaveEvery = Math.max(60, config.getLong("autosave-minutes", 5) * 60);
        if (secondsElapsed % autosaveEvery == 0) {
            dataStore.save();
        }
    }
}

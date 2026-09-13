package com.timesmp.plugin.listeners;

import com.timesmp.plugin.combat.PvpKillResolver;
import com.timesmp.plugin.data.DataStore;
import com.timesmp.plugin.data.GameState;
import com.timesmp.plugin.data.PlayerData;
import com.timesmp.plugin.display.DeathAnimation;
import com.timesmp.plugin.display.PopupEffect;
import com.timesmp.plugin.inventory.ExtraInventoryManager;
import com.timesmp.plugin.quest.QuestManager;
import com.timesmp.plugin.rank.RankManager;
import com.timesmp.plugin.util.TimeUtil;
import com.timesmp.plugin.util.WorldFilter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.Plugin;

/**
 * Handles a normal PvP death: the actual time-steal math lives in PvpKillResolver
 * now (shared with CombatLogListener, so a combat-logged kill applies identical
 * rules). This class is just the death-event glue: extra-inventory drop, the
 * tombstone animation, resetting the no-death-streak quest, and triggering the
 * resolver + popup once the tombstone lands.
 *
 * Sequencing: the tombstone animation plays first, and only once it's actually
 * landed does the time-transfer popup appear (they used to fire simultaneously,
 * which looked like a garbled overlap in-game). Only ONE popup shows - the
 * killer's gain - not a separate loss popup for the victim; the victim still gets
 * a plain chat message about their loss (sent from inside PvpKillResolver).
 */
public class CombatListener implements Listener {

    private final Plugin plugin;
    private final DataStore dataStore;
    private final QuestManager questManager;
    private final RankManager rankManager;
    private final ExtraInventoryManager extraInventoryManager;
    private final WorldFilter worldFilter;
    private final PvpKillResolver pvpKillResolver;
    private final FileConfiguration config;

    public CombatListener(Plugin plugin, DataStore dataStore, QuestManager questManager, RankManager rankManager,
                           ExtraInventoryManager extraInventoryManager, WorldFilter worldFilter, PvpKillResolver pvpKillResolver) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        this.questManager = questManager;
        this.rankManager = rankManager;
        this.extraInventoryManager = extraInventoryManager;
        this.worldFilter = worldFilter;
        this.pvpKillResolver = pvpKillResolver;
        this.config = plugin.getConfig();
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        boolean disabledWorld = worldFilter.isDisabled(victim.getWorld());

        if (!disabledWorld) {
            extraInventoryManager.dropOnDeath(victim);
        }

        if (dataStore.getState() != GameState.RUNNING || disabledWorld) {
            return;
        }

        PlayerData victimData = dataStore.getOrCreate(victim.getUniqueId(), victim.getName());
        questManager.onPlayerDied(victimData);

        Player killer = victim.getKiller();
        boolean isPvpKill = killer != null && !killer.getUniqueId().equals(victim.getUniqueId());

        long[] stealHolder = {0};
        if (isPvpKill) {
            PlayerData killerData = dataStore.getOrCreate(killer.getUniqueId(), killer.getName());
            stealHolder[0] = pvpKillResolver.resolveWithData(killer, killerData, victimData, victim);
        } else {
            victimData.incrementDeaths();
        }

        rankManager.refresh();

        boolean animate = config.getBoolean("death-animation.enabled", true);
        Runnable onLanded = () -> {
            if (stealHolder[0] > 0) {
                PopupEffect.show(plugin, victim.getLocation().add(0, 0.6, 0), "+" + TimeUtil.formatDuration(stealHolder[0]), true);
            }
        };

        if (animate) {
            int duration = config.getInt("death-animation.duration-seconds", 60);
            DeathAnimation.show(plugin, victim, duration, onLanded);
        } else {
            onLanded.run();
        }
    }
}

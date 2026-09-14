package com.timesmp.plugin.listeners;

import com.timesmp.plugin.data.DataStore;
import com.timesmp.plugin.data.GameState;
import com.timesmp.plugin.data.PlayerData;
import com.timesmp.plugin.quest.QuestManager;
import com.timesmp.plugin.util.WorldFilter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

/**
 * Tracks damage dealt for the DAMAGE_DEALT quest type. Counts damage dealt to
 * ANY living entity (not just other players) - the spec just says "hearts of
 * damage" without restricting to PvP, so this is treated as general combat damage.
 */
public class DamageListener implements Listener {

    private final DataStore dataStore;
    private final QuestManager questManager;
    private final WorldFilter worldFilter;

    public DamageListener(DataStore dataStore, QuestManager questManager, WorldFilter worldFilter) {
        this.dataStore = dataStore;
        this.questManager = questManager;
        this.worldFilter = worldFilter;
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (dataStore.getState() != GameState.RUNNING) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;
        if (worldFilter.isDisabled(event.getEntity().getWorld())) return;

        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null) return;
        // Don't let players farm the quest by hitting themselves.
        if (attacker.getUniqueId().equals(event.getEntity().getUniqueId())) return;

        PlayerData attackerData = dataStore.getOrCreate(attacker.getUniqueId(), attacker.getName());
        questManager.onDamageDealt(attackerData, event.getFinalDamage());
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) {
                return player;
            }
        }
        return null;
    }
}

package com.timesmp.plugin.combat;

import com.timesmp.plugin.data.DataStore;
import com.timesmp.plugin.data.PlayerData;
import com.timesmp.plugin.display.DeathAnimation;
import com.timesmp.plugin.display.PopupEffect;
import com.timesmp.plugin.inventory.ExtraInventoryManager;
import com.timesmp.plugin.quest.QuestManager;
import com.timesmp.plugin.rank.RankManager;
import com.timesmp.plugin.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * If a player disconnects while still combat-tagged, this treats it as if they
 * died right then: their real inventory AND their TimeSMP extra-inventory drop on
 * the ground, and whoever tagged them most recently still gets the normal
 * kill-steal (via the same PvpKillResolver a real PvP death uses) - applied
 * immediately on the disconnect, not deferred until they next join.
 *
 * This deliberately does NOT rely on a real PlayerDeathEvent (forcing one via
 * setHealth(0) mid-quit is unreliable) - it simulates the consequences directly.
 * Item-drop punishment happens regardless of the main timer's state; the actual
 * time-steal only happens if it's RUNNING (same rule PvpKillResolver already
 * enforces for a normal kill).
 */
public class CombatLogListener implements Listener {

    private final Plugin plugin;
    private final DataStore dataStore;
    private final RankManager rankManager;
    private final ExtraInventoryManager extraInventoryManager;
    private final CombatManager combatManager;
    private final CombatConfig combatConfig;
    private final PvpKillResolver pvpKillResolver;
    private final QuestManager questManager;
    private final FileConfiguration config;

    public CombatLogListener(Plugin plugin, DataStore dataStore, RankManager rankManager,
                              ExtraInventoryManager extraInventoryManager, CombatManager combatManager,
                              CombatConfig combatConfig, PvpKillResolver pvpKillResolver, QuestManager questManager) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        this.rankManager = rankManager;
        this.extraInventoryManager = extraInventoryManager;
        this.combatManager = combatManager;
        this.combatConfig = combatConfig;
        this.pvpKillResolver = pvpKillResolver;
        this.questManager = questManager;
        this.config = plugin.getConfig();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player victim = event.getPlayer();
        UUID victimId = victim.getUniqueId();

        if (!combatManager.isInCombat(victimId)) return;
        boolean disabledWorld = combatConfig.isWorldDisabled(victim.getWorld());
        combatManager.clear(victimId);
        if (disabledWorld || !combatConfig.punishCombatLog()) {
            return;
        }

        Location loc = victim.getLocation();
        World world = loc.getWorld();

        dropRealInventory(victim, world, loc);
        extraInventoryManager.dropOnDeath(victim);

        PlayerData victimData = dataStore.getOrCreate(victimId, victim.getName());
        questManager.onPlayerDied(victimData);

        UUID attackerId = combatManager.getLastAttacker(victimId);
        Player attacker = attackerId != null ? Bukkit.getPlayer(attackerId) : null;

        long[] stealHolder = {0};
        if (attacker != null && !attacker.getUniqueId().equals(victimId)) {
            PlayerData killerData = dataStore.getOrCreate(attacker.getUniqueId(), attacker.getName());
            stealHolder[0] = pvpKillResolver.resolveWithData(attacker, killerData, victimData, victim);
            attacker.sendMessage(net.kyori.adventure.text.Component.text(
                    victim.getName() + " combat logged - you still get credit for the kill!", net.kyori.adventure.text.format.NamedTextColor.GOLD));
        } else {
            victimData.incrementDeaths();
        }

        rankManager.refresh();

        boolean animate = config.getBoolean("death-animation.enabled", true);
        long steal = stealHolder[0];
        Runnable onLanded = () -> {
            if (steal > 0) {
                PopupEffect.show(plugin, loc.clone().add(0, 0.6, 0), "+" + TimeUtil.formatDuration(steal), true);
            }
        };
        if (animate && world != null) {
            int duration = config.getInt("death-animation.duration-seconds", 60);
            DeathAnimation.show(plugin, victim, duration, onLanded);
        } else {
            onLanded.run();
        }
    }

    private void dropRealInventory(Player player, World world, Location loc) {
        if (world == null) return;
        PlayerInventory inv = player.getInventory();

        for (ItemStack item : inv.getContents()) {
            dropIfPresent(world, loc, item);
        }
        for (ItemStack item : inv.getArmorContents()) {
            dropIfPresent(world, loc, item);
        }
        dropIfPresent(world, loc, inv.getItemInOffHand());

        inv.clear();
        inv.setArmorContents(new ItemStack[4]);
        inv.setItemInOffHand(null);
    }

    private void dropIfPresent(World world, Location loc, ItemStack item) {
        if (item != null && item.getType() != Material.AIR) {
            world.dropItemNaturally(loc, item);
        }
    }
}

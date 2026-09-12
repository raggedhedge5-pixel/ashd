package com.timesmp.plugin.combat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

/**
 * Tags both participants of a PvP hit, and enforces the ONLY hard block: Elytra
 * gliding. Pearls and mace both just get a cooldown (applied in
 * CombatManager.tagPair() via vanilla's real per-material item-cooldown system) -
 * nothing here cancels a mace swing anymore. Note that a mace's cooldown is
 * therefore purely cosmetic in practice: vanilla's item-cooldown system only gates
 * right-click "use" actions, not melee swings, so it won't actually stop someone
 * from attacking with a mace - it'll just show the cooldown swirl on the item.
 *
 * onPvpDamage runs at MONITOR priority with ignoreCancelled=true specifically so it
 * only tags combat AFTER protection plugins (WorldGuard, etc.) have had their say -
 * if WorldGuard (or anything else) already cancelled the damage because PVP is
 * disabled there, this handler is skipped entirely and no tag happens.
 */
public class CombatTagListener implements Listener {

    private final CombatManager combatManager;
    private final CombatConfig config;

    public CombatTagListener(CombatManager combatManager, CombatConfig config) {
        this.combatManager = combatManager;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPvpDamage(EntityDamageByEntityEvent event) {
        if (config.isWorldDisabled(event.getEntity().getWorld())) return;
        if (!(event.getEntity() instanceof Player victim)) return;
        if (event.getFinalDamage() <= 0) return;

        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) return;

        combatManager.tagPair(attacker, victim);
    }

    @EventHandler
    public void onElytraToggle(EntityToggleGlideEvent event) {
        if (!config.blockElytra()) return;
        if (!event.isGliding()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (config.isWorldDisabled(player.getWorld())) return;

        if (combatManager.isInCombat(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("You can't glide while in combat!", NamedTextColor.RED));
        }
    }

    /** Catches the right-click that would throw a pearl, from EITHER hand - getItem() reflects whichever hand triggered this event. */
    @EventHandler
    public void onPearlInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.ENDER_PEARL) return;

        Player player = event.getPlayer();
        if (config.isWorldDisabled(player.getWorld())) return;

        if (combatManager.isInCombat(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("Ender pearls are on cooldown while in combat!", NamedTextColor.RED));
        }
    }

    /** Second safety net directly on the thrown projectile itself, in case anything ever bypasses the interact event above. */
    @EventHandler
    public void onPearlLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof EnderPearl pearl)) return;
        if (!(pearl.getShooter() instanceof Player player)) return;
        if (config.isWorldDisabled(player.getWorld())) return;

        if (combatManager.isInCombat(player.getUniqueId())) {
            event.setCancelled(true);
        }
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

package com.timesmp.plugin.combat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

/**
 * Tags both participants of a PvP hit, and enforces the in-combat restrictions:
 *   - mace: any mace hit landed WHILE ALREADY tagged is cancelled outright (see
 *     combatlog.yml for why this isn't done via item-cooldown like pearls).
 *   - elytra: cancels EntityToggleGlideEvent while tagged, so you can't fly away either.
 *   - pearls: CombatManager.tagPair() already applies vanilla's real item-cooldown
 *     for ENDER_PEARL, which is keyed by material rather than by hand, so it should
 *     already cover a pearl sitting in the offhand too. This class ALSO explicitly
 *     cancels the interact and the actual throw for belt-and-suspenders reliability,
 *     regardless of which hand the pearl was used from - see onPearlInteract/onPearlLaunch.
 */
public class CombatTagListener implements Listener {

    private final CombatManager combatManager;
    private final CombatConfig config;

    public CombatTagListener(CombatManager combatManager, CombatConfig config) {
        this.combatManager = combatManager;
        this.config = config;
    }

    @EventHandler
    public void onPvpDamage(EntityDamageByEntityEvent event) {
        if (config.isWorldDisabled(event.getEntity().getWorld())) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) return;

        // Block mace hits from someone who is ALREADY tagged (their very first hit,
        // which starts the tag, still lands - see combatlog.yml comments).
        if (combatManager.isInCombat(attacker.getUniqueId())) {
            ItemStack weapon = attacker.getInventory().getItemInMainHand();
            if (weapon.getType() == Material.MACE) {
                event.setCancelled(true);
                attacker.sendActionBar(Component.text("Mace is disabled while you're in combat!", NamedTextColor.RED));
                // Being attacked (even a blocked swing) still counts as engaging - keep the tag alive.
                combatManager.tagPair(attacker, victim);
                return;
            }
        }

        if (!event.isCancelled() && event.getFinalDamage() > 0) {
            combatManager.tagPair(attacker, victim);
        }
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

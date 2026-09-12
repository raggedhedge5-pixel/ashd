package com.timesmp.plugin.combat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Tracks who's currently combat-tagged, until when, and who tagged them last. */
public class CombatManager {

    private final CombatConfig config;

    private final Map<UUID, Long> combatEndsAtMillis = new HashMap<>();
    private final Map<UUID, UUID> lastAttacker = new HashMap<>();

    public CombatManager(CombatConfig config) {
        this.config = config;
    }

    /** Tags both participants of a PvP hit, refreshing their timers back to full and recording each other as last attacker. */
    public void tagPair(Player attacker, Player victim) {
        long endsAt = System.currentTimeMillis() + config.durationSeconds() * 1000L;
        combatEndsAtMillis.put(attacker.getUniqueId(), endsAt);
        combatEndsAtMillis.put(victim.getUniqueId(), endsAt);
        lastAttacker.put(victim.getUniqueId(), attacker.getUniqueId());
        lastAttacker.put(attacker.getUniqueId(), victim.getUniqueId());

        int cooldownTicks = config.pearlCooldownSeconds() * 20;
        attacker.setCooldown(Material.ENDER_PEARL, cooldownTicks);
        victim.setCooldown(Material.ENDER_PEARL, cooldownTicks);
        int maceCooldownTicks = config.maceCooldownSeconds() * 20;
        attacker.setCooldown(Material.MACE, maceCooldownTicks);
        victim.setCooldown(Material.MACE, maceCooldownTicks);
    }

    public boolean isInCombat(UUID uuid) {
        Long endsAt = combatEndsAtMillis.get(uuid);
        return endsAt != null && System.currentTimeMillis() < endsAt;
    }

    public long getRemainingSeconds(UUID uuid) {
        Long endsAt = combatEndsAtMillis.get(uuid);
        if (endsAt == null) return 0;
        return Math.max(0, (endsAt - System.currentTimeMillis()) / 1000L);
    }

    public UUID getLastAttacker(UUID uuid) {
        return lastAttacker.get(uuid);
    }

    public void clear(UUID uuid) {
        combatEndsAtMillis.remove(uuid);
        lastAttacker.remove(uuid);
    }

    /** Call once a second - sends the actionbar countdown and expires tags that have run out. */
    public void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            if (!combatEndsAtMillis.containsKey(uuid)) continue;

            if (!isInCombat(uuid)) {
                combatEndsAtMillis.remove(uuid);
                lastAttacker.remove(uuid);
                player.sendActionBar(Component.text("You are no longer in combat.", NamedTextColor.GRAY));
                continue;
            }

            if (config.showActionbar()) {
                long remaining = getRemainingSeconds(uuid);
                player.sendActionBar(Component.text("⚔ Combat: " + remaining + "s", NamedTextColor.RED));
            }
        }
    }
}

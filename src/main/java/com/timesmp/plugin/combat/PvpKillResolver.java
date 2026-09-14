package com.timesmp.plugin.combat;

import com.timesmp.plugin.data.DataStore;
import com.timesmp.plugin.data.GameState;
import com.timesmp.plugin.data.PlayerData;
import com.timesmp.plugin.quest.QuestManager;
import com.timesmp.plugin.util.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * The actual "kill steals time from victim, gives it to killer" logic - shared
 * between a normal PvP death (CombatListener) and a combat-log punishment
 * (CombatLogListener), so both paths apply the exact same rules and floor.
 */
public class PvpKillResolver {

    private final DataStore dataStore;
    private final QuestManager questManager;
    private final FileConfiguration config;

    public PvpKillResolver(Plugin plugin, DataStore dataStore, QuestManager questManager) {
        this.dataStore = dataStore;
        this.questManager = questManager;
        this.config = plugin.getConfig();
    }

    /** Applies the steal (only if the timer is RUNNING) and increments kill/death counters.
     * A combat-log victim may already be offline by the time this runs, hence victimOrNull. */
    public long resolveWithData(Player killer, PlayerData killerData, PlayerData victimData, Player victimOrNull) {
        if (dataStore.getState() != GameState.RUNNING) return 0;

        killerData.incrementKills();
        victimData.incrementDeaths();

        long floorSeconds = TimeUtil.hoursToSeconds(config.getDouble("min-time-hours", -4));
        long desiredSteal = TimeUtil.hoursToSeconds(config.getDouble("kill-reward-hours", 2));
        long maxStealable = victimData.getTimeSeconds() - floorSeconds;
        long actualSteal = Math.max(0, Math.min(desiredSteal, maxStealable));

        victimData.addTimeSeconds(-actualSteal);
        killerData.addTimeSeconds(actualSteal);
        questManager.onPlayerKilledPlayer(killerData, victimData.getUuid());

        if (actualSteal > 0) {
            killer.sendMessage(Component.text("You stole " + TimeUtil.formatDuration(actualSteal) + " from " + victimData.getLastKnownName() + "!", NamedTextColor.GREEN));
            if (victimOrNull != null) {
                victimOrNull.sendMessage(Component.text("You lost " + TimeUtil.formatDuration(actualSteal) + " of time.", NamedTextColor.RED));
            }
        } else {
            killer.sendMessage(Component.text(victimData.getLastKnownName() + " was already at the time floor - nothing to steal.", NamedTextColor.GRAY));
        }

        return actualSteal;
    }
}

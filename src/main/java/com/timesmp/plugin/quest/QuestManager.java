package com.timesmp.plugin.quest;

import com.timesmp.plugin.data.DataStore;
import com.timesmp.plugin.data.PlayerData;
import com.timesmp.plugin.util.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Owns quest assignment, progress tracking and reward payout.
 *
 * Design notes / assumptions (spec was ambiguous on a couple of points):
 *  - Only the DAILY_PLAYTIME quest has a hard deadline (24h by default, configurable):
 *    it explicitly says "within 24 hours". If it isn't finished in time it is rerolled
 *    (not treated as a punishing failure - just a fresh quest, no cooldown wait).
 *  - NO_DEATH_STREAK progress resets to 0 on death but the quest itself is NOT rerolled -
 *    you just have to string together the required time without dying, whenever that ends up being.
 *  - Reward hours scale linearly between quests.min-reward-hours and quests.max-reward-hours
 *    based on where the *rolled* target falls within its configured min/max range - a quest
 *    that rolled a harder target pays out closer to the max reward.
 */
public class QuestManager {

    private final Plugin plugin;
    private final DataStore dataStore;
    private final FileConfiguration config;

    public QuestManager(Plugin plugin, DataStore dataStore) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        this.config = plugin.getConfig();
    }

    // ---------------------------------------------------------------- assignment

    public void tryAssignIfEligible(PlayerData data) {
        long now = nowEpochSeconds();
        if (data.isEligibleForNewQuest(now)) {
            assignRandomQuest(data);
        } else if (data.getActiveQuest() != null) {
            checkDailyPlaytimeExpiry(data, now);
        }
    }

    private void checkDailyPlaytimeExpiry(PlayerData data, long now) {
        Quest quest = data.getActiveQuest();
        if (quest == null || quest.getType() != QuestType.DAILY_PLAYTIME) return;
        double deadlineHours = config.getDouble("quests.daily-playtime-hours.deadline-hours", 24);
        long deadlineSeconds = TimeUtil.hoursToSeconds(deadlineHours);
        if (now - quest.getAssignedAtEpochSeconds() > deadlineSeconds) {
            // Expired without completion - reroll fresh, no cooldown penalty.
            assignRandomQuest(data);
            Player p = Bukkit.getPlayer(data.getUuid());
            if (p != null && p.isOnline()) {
                p.sendMessage(Component.text("Your daily playtime quest expired - here's a new one:", NamedTextColor.YELLOW));
                sendQuestAssignedMessage(p, data.getActiveQuest());
            }
        }
    }

    public void assignRandomQuest(PlayerData data) {
        QuestType[] types = QuestType.values();
        QuestType type = types[ThreadLocalRandom.current().nextInt(types.length)];

        double target;
        double min;
        double max;
        switch (type) {
            case KILL_COUNT:
                min = config.getInt("quests.kill-count.min", 3);
                max = config.getInt("quests.kill-count.max", 8);
                target = randomInRange(min, max);
                target = Math.round(target); // whole players
                break;
            case DAILY_PLAYTIME:
                min = TimeUtil.hoursToSeconds(config.getDouble("quests.daily-playtime-hours.min", 1));
                max = TimeUtil.hoursToSeconds(config.getDouble("quests.daily-playtime-hours.max", 3));
                target = randomInRange(min, max);
                break;
            case NO_DEATH_STREAK:
                min = TimeUtil.hoursToSeconds(config.getDouble("quests.no-death-streak-hours.min", 1));
                max = TimeUtil.hoursToSeconds(config.getDouble("quests.no-death-streak-hours.max", 3));
                target = randomInRange(min, max);
                break;
            case DAMAGE_DEALT:
            default:
                min = config.getInt("quests.damage-dealt-hearts.min", 20);
                max = config.getInt("quests.damage-dealt-hearts.max", 40);
                target = randomInRange(min, max);
                target = Math.round(target);
                break;
        }

        double t = max > min ? (target - min) / (max - min) : 0.0;
        double minReward = config.getDouble("quests.min-reward-hours", 5);
        double maxReward = config.getDouble("quests.max-reward-hours", 8);
        double rewardHours = minReward + t * (maxReward - minReward);
        long rewardSeconds = TimeUtil.hoursToSeconds(rewardHours);

        Quest quest = new Quest(type, target, rewardSeconds, nowEpochSeconds());
        data.setActiveQuest(quest);
    }

    private double randomInRange(double min, double max) {
        if (max <= min) return min;
        return min + ThreadLocalRandom.current().nextDouble() * (max - min);
    }

    public void sendQuestAssignedMessage(Player player, Quest quest) {
        player.sendMessage(Component.text("")
                .append(Component.text("New quest: ", NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.text(quest.describe(), NamedTextColor.WHITE))
                .append(Component.text("  (+" + String.format("%.1f", quest.getRewardSeconds() / 3600.0) + "h reward)", NamedTextColor.GREEN)));
    }

    // ---------------------------------------------------------------- progress hooks

    /** Call once per second per online player. */
    public void tickOnlinePlayer(PlayerData data) {
        tryAssignIfEligible(data);
        Quest quest = data.getActiveQuest();
        if (quest == null || quest.isCompleted()) return;

        if (quest.getType() == QuestType.DAILY_PLAYTIME || quest.getType() == QuestType.NO_DEATH_STREAK) {
            quest.setProgress(quest.getProgress() + 1);
            checkCompletion(data, quest);
        }
    }

    public void onPlayerKilledPlayer(PlayerData killerData, UUID victimUuid) {
        Quest quest = killerData.getActiveQuest();
        if (quest == null || quest.isCompleted() || quest.getType() != QuestType.KILL_COUNT) return;
        quest.getUniqueKills().add(victimUuid);
        checkCompletion(killerData, quest);
    }

    public void onPlayerDied(PlayerData data) {
        Quest quest = data.getActiveQuest();
        if (quest != null && quest.getType() == QuestType.NO_DEATH_STREAK && !quest.isCompleted()) {
            quest.setProgress(0);
            Player player = Bukkit.getPlayer(data.getUuid());
            if (player != null && player.isOnline()) {
                player.sendMessage(Component.text("Your no-death streak was reset to 0.", NamedTextColor.RED));
            }
        }
    }

    public void onDamageDealt(PlayerData attackerData, double rawHpDealt) {
        Quest quest = attackerData.getActiveQuest();
        if (quest == null || quest.isCompleted() || quest.getType() != QuestType.DAMAGE_DEALT) return;
        double hearts = rawHpDealt / 2.0;
        quest.setProgress(quest.getProgress() + hearts);
        checkCompletion(attackerData, quest);
    }

    private void checkCompletion(PlayerData data, Quest quest) {
        if (!quest.isTargetMet()) return;
        quest.markCompleted();

        data.addTimeSeconds(quest.getRewardSeconds());
        double cooldownHours = config.getDouble("quests.cooldown-hours", 12);
        data.setNextQuestAvailableAtEpochSeconds(nowEpochSeconds() + TimeUtil.hoursToSeconds(cooldownHours));
        data.setActiveQuest(null);

        Player player = Bukkit.getPlayer(data.getUuid());
        if (player != null && player.isOnline()) {
            player.sendMessage(Component.text(""));
            player.sendMessage(Component.text("✔ Quest complete! ", NamedTextColor.GREEN, TextDecoration.BOLD)
                    .append(Component.text("+" + String.format("%.1f", quest.getRewardSeconds() / 3600.0) + " hours", NamedTextColor.GOLD)));
            player.sendMessage(Component.text(""));
            player.showTitle(Title.title(
                    Component.text("Quest Complete!", NamedTextColor.GREEN, TextDecoration.BOLD),
                    Component.text("+" + String.format("%.1f", quest.getRewardSeconds() / 3600.0) + " hours banked", NamedTextColor.GOLD),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(500))
            ));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
    }

    private long nowEpochSeconds() {
        return System.currentTimeMillis() / 1000L;
    }
}

package com.timesmp.plugin.quest;

import com.timesmp.plugin.util.TimeUtil;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A single active (or completed) quest instance belonging to one player.
 * "target" and "progress" are both stored in double form for a uniform
 * comparison, but represent different units depending on {@link #type}:
 *   KILL_COUNT       -> whole players (progress tracked via {@link #uniqueKills})
 *   DAILY_PLAYTIME   -> seconds
 *   NO_DEATH_STREAK  -> seconds
 *   DAMAGE_DEALT     -> hearts (2 HP per heart)
 */
public class Quest {

    private final QuestType type;
    private final double target;
    private final long rewardSeconds;
    private final long assignedAtEpochSeconds;

    private double progress = 0.0;
    private boolean completed = false;

    // Only used by KILL_COUNT so we count *different* players, not raw kills.
    private final Set<UUID> uniqueKills = new HashSet<>();

    public Quest(QuestType type, double target, long rewardSeconds, long assignedAtEpochSeconds) {
        this.type = type;
        this.target = target;
        this.rewardSeconds = rewardSeconds;
        this.assignedAtEpochSeconds = assignedAtEpochSeconds;
    }

    public QuestType getType() {
        return type;
    }

    public double getTarget() {
        return target;
    }

    public long getRewardSeconds() {
        return rewardSeconds;
    }

    public long getAssignedAtEpochSeconds() {
        return assignedAtEpochSeconds;
    }

    public double getProgress() {
        return progress;
    }

    public void setProgress(double progress) {
        this.progress = progress;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void markCompleted() {
        this.completed = true;
    }

    public Set<UUID> getUniqueKills() {
        return uniqueKills;
    }

    public boolean isTargetMet() {
        if (type == QuestType.KILL_COUNT) {
            return uniqueKills.size() >= target;
        }
        return progress >= target;
    }

    public double getCurrentProgressValue() {
        if (type == QuestType.KILL_COUNT) {
            return uniqueKills.size();
        }
        return progress;
    }

    /** Human readable description, e.g. "Kill 5 different players" or "Play for 02:30:00 without dying". */
    public String describe() {
        switch (type) {
            case KILL_COUNT:
                return String.format(type.getDescriptionTemplate(), (int) target);
            case DAILY_PLAYTIME:
                return String.format(type.getDescriptionTemplate(), TimeUtil.formatDuration((long) target));
            case NO_DEATH_STREAK:
                return String.format(type.getDescriptionTemplate(), TimeUtil.formatDuration((long) target));
            case DAMAGE_DEALT:
                return String.format(type.getDescriptionTemplate(), (int) target);
            default:
                return "Unknown quest";
        }
    }

    /** Progress string, e.g. "3/5 kills" or "01:12:03 / 02:30:00". */
    public String describeProgress() {
        switch (type) {
            case KILL_COUNT:
                return uniqueKills.size() + "/" + (int) target + " players";
            case DAILY_PLAYTIME:
            case NO_DEATH_STREAK:
                return TimeUtil.formatDuration((long) progress) + " / " + TimeUtil.formatDuration((long) target);
            case DAMAGE_DEALT:
                return String.format("%.1f/%.0f hearts", progress, target);
            default:
                return "";
        }
    }
}

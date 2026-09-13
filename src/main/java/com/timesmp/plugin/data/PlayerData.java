package com.timesmp.plugin.data;

import com.timesmp.plugin.quest.Quest;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * All persisted state for one player. One instance per player, kept in memory
 * and mirrored to disk by {@link DataStore}.
 */
public class PlayerData {

    private final UUID uuid;
    private String lastKnownName;

    private long timeSeconds;
    private int currentRank = -1; // -1 = not yet ranked / outside leaderboard

    private Quest activeQuest;
    // Epoch seconds at which the player becomes eligible for a new quest after completing one.
    // 0 means "eligible right now" (e.g. brand new player).
    private long nextQuestAvailableAtEpochSeconds = 0;

    private boolean scoreboardEnabled = true;

    // Contents of the rank-based extra inventory perk. Only ever populated with
    // "real" items (locked-slot placeholders are never stored here).
    private List<ItemStack> extraInventoryItems = new ArrayList<>();

    private int kills = 0;
    private int deaths = 0;

    public PlayerData(UUID uuid, String lastKnownName, long startingTimeSeconds) {
        this.uuid = uuid;
        this.lastKnownName = lastKnownName;
        this.timeSeconds = startingTimeSeconds;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getLastKnownName() {
        return lastKnownName;
    }

    public void setLastKnownName(String lastKnownName) {
        this.lastKnownName = lastKnownName;
    }

    public long getTimeSeconds() {
        return timeSeconds;
    }

    public void setTimeSeconds(long timeSeconds) {
        this.timeSeconds = timeSeconds;
    }

    public void addTimeSeconds(long delta) {
        this.timeSeconds += delta;
    }

    public boolean isInDebt() {
        return timeSeconds < 0;
    }

    public int getCurrentRank() {
        return currentRank;
    }

    public void setCurrentRank(int currentRank) {
        this.currentRank = currentRank;
    }

    public Quest getActiveQuest() {
        return activeQuest;
    }

    public void setActiveQuest(Quest activeQuest) {
        this.activeQuest = activeQuest;
    }

    public long getNextQuestAvailableAtEpochSeconds() {
        return nextQuestAvailableAtEpochSeconds;
    }

    public void setNextQuestAvailableAtEpochSeconds(long nextQuestAvailableAtEpochSeconds) {
        this.nextQuestAvailableAtEpochSeconds = nextQuestAvailableAtEpochSeconds;
    }

    public boolean isEligibleForNewQuest(long nowEpochSeconds) {
        return activeQuest == null && nowEpochSeconds >= nextQuestAvailableAtEpochSeconds;
    }

    public boolean isScoreboardEnabled() {
        return scoreboardEnabled;
    }

    public void setScoreboardEnabled(boolean scoreboardEnabled) {
        this.scoreboardEnabled = scoreboardEnabled;
    }

    public List<ItemStack> getExtraInventoryItems() {
        return extraInventoryItems;
    }

    public void setExtraInventoryItems(List<ItemStack> extraInventoryItems) {
        this.extraInventoryItems = extraInventoryItems;
    }

    public int getKills() {
        return kills;
    }

    public void incrementKills() {
        this.kills++;
    }

    public void setKills(int kills) {
        this.kills = kills;
    }

    public int getDeaths() {
        return deaths;
    }

    public void incrementDeaths() {
        this.deaths++;
    }

    public void setDeaths(int deaths) {
        this.deaths = deaths;
    }
}

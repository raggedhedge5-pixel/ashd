package com.timesmp.plugin.quest;

/**
 * The four quest archetypes described in the spec. Each has its own unit for
 * "progress"/"target" (see comments) and its own randomised target range.
 */
public enum QuestType {
    /** target/progress unit = number of *unique* players killed. */
    KILL_COUNT("Kill %d different players", "kills"),
    /** target/progress unit = seconds played within the current 24h quest window. */
    DAILY_PLAYTIME("Play for %s in one day", "playtime"),
    /** target/progress unit = seconds survived without dying since the quest started. */
    NO_DEATH_STREAK("Play %s without dying", "no-death streak"),
    /** target/progress unit = hearts (1 heart = 2 HP) of damage dealt. */
    DAMAGE_DEALT("Deal %d hearts of damage", "damage dealt");

    private final String descriptionTemplate;
    private final String shortLabel;

    QuestType(String descriptionTemplate, String shortLabel) {
        this.descriptionTemplate = descriptionTemplate;
        this.shortLabel = shortLabel;
    }

    public String getDescriptionTemplate() {
        return descriptionTemplate;
    }

    public String getShortLabel() {
        return shortLabel;
    }
}

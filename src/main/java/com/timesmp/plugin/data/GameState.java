package com.timesmp.plugin.data;

/**
 * Global on/off switch for the whole plugin's time-counting behaviour.
 *   STOPPED - default on first install. Nothing ticks, no kill/death time
 *             changes, no quests get assigned. Lets an admin set the server
 *             up without falling behind everyone who joins during setup.
 *   RUNNING - normal operation, exactly as before this feature existed.
 *   PAUSED  - same practical effect as STOPPED (nothing ticks), but kept as
 *             a separate state so /timesmp pause and /timesmp stop read
 *             correctly as two different admin intents (temporary break vs
 *             ending the event) even though today they behave identically.
 */
public enum GameState {
    STOPPED,
    RUNNING,
    PAUSED
}

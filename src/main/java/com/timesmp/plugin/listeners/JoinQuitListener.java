package com.timesmp.plugin.listeners;

import com.timesmp.plugin.data.DataStore;
import com.timesmp.plugin.data.GameState;
import com.timesmp.plugin.data.PlayerData;
import com.timesmp.plugin.guide.GuideBook;
import com.timesmp.plugin.quest.QuestManager;
import com.timesmp.plugin.rank.RankManager;
import com.timesmp.plugin.scoreboard.ScoreboardManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class JoinQuitListener implements Listener {

    private final DataStore dataStore;
    private final QuestManager questManager;
    private final RankManager rankManager;
    private final ScoreboardManager scoreboardManager;
    private final GuideBook guideBook;

    public JoinQuitListener(DataStore dataStore, QuestManager questManager, RankManager rankManager, ScoreboardManager scoreboardManager, GuideBook guideBook) {
        this.dataStore = dataStore;
        this.questManager = questManager;
        this.rankManager = rankManager;
        this.scoreboardManager = scoreboardManager;
        this.guideBook = guideBook;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        PlayerData data = dataStore.getOrCreate(player.getUniqueId(), player.getName());

        // No quests get handed out until an admin actually starts the timer.
        if (dataStore.getState() == GameState.RUNNING) {
            questManager.tryAssignIfEligible(data);
        }
        rankManager.refresh();
        guideBook.giveIfMissing(player);
        // Always set up their personal scoreboard (needed for nametag rank-tags
        // regardless of their own sidebar preference) - the sidebar objective itself
        // stays conditional on data.isScoreboardEnabled() inside refreshOne().
        scoreboardManager.refreshOne(player);

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("Welcome back! Your banked time: ", NamedTextColor.GRAY)
                .append(com.timesmp.plugin.util.TimeUtil.formatColored(data.getTimeSeconds())));
        if (data.getActiveQuest() != null) {
            questManager.sendQuestAssignedMessage(player, data.getActiveQuest());
        }

        switch (dataStore.getState()) {
            case STOPPED -> player.sendMessage(Component.text("The timer hasn't started yet.", NamedTextColor.DARK_GRAY));
            case PAUSED -> player.sendMessage(Component.text("The timer is currently paused.", NamedTextColor.DARK_GRAY));
            case RUNNING -> {}
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        dataStore.save();
        scoreboardManager.removeBoard(event.getPlayer().getUniqueId());
        // Refresh shortly after so remaining online players' ranks/hearts update
        // now that this player has dropped out of the "online" view if relevant.
        rankManager.refresh();
    }
}

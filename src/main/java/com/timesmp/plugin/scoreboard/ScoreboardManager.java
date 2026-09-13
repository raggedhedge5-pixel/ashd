package com.timesmp.plugin.scoreboard;

import com.timesmp.plugin.data.DataStore;
import com.timesmp.plugin.data.GameState;
import com.timesmp.plugin.data.PlayerData;
import com.timesmp.plugin.quest.Quest;
import com.timesmp.plugin.rank.RankManager;
import com.timesmp.plugin.util.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owns one persistent, personal Scoreboard object per player for their whole
 * session. That single object carries two independent things:
 *   1. The rank-tag/coloured NAMETAG teams (one team per online player) - always
 *      present regardless of the sidebar toggle, so turning the sidebar off doesn't
 *      also blind that viewer to everyone else's rank-coloured nametags.
 *   2. The optional sidebar objective (rank/time/kills/deaths/quest) - added or
 *      removed based on each player's own /timeboard toggle.
 * Splitting it this way (instead of swapping between a personal board and the
 * server's main board) is what lets nametags work independently of the sidebar.
 */
public class ScoreboardManager {

    private final Plugin plugin;
    private final DataStore dataStore;
    private final RankManager rankManager;

    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    public ScoreboardManager(Plugin plugin, DataStore dataStore, RankManager rankManager) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        this.rankManager = rankManager;
    }

    private Scoreboard boardFor(Player player) {
        return boards.computeIfAbsent(player.getUniqueId(), id -> {
            Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
            player.setScoreboard(board);
            return board;
        });
    }

    /** Call on join and on toggling the sidebar on. */
    public void enable(Player player) {
        PlayerData data = dataStore.getOrCreate(player.getUniqueId(), player.getName());
        data.setScoreboardEnabled(true);
        refreshOne(player);
    }

    /** Call on toggling the sidebar off. Nametag teams stay - only the sidebar objective is removed. */
    public void disable(Player player) {
        PlayerData data = dataStore.getOrCreate(player.getUniqueId(), player.getName());
        data.setScoreboardEnabled(false);
        Scoreboard board = boardFor(player);
        Objective existing = board.getObjective("timesmp");
        if (existing != null) existing.unregister();
    }

    /** Called every rank-refresh cycle for every online player. */
    public void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshOne(player);
        }
    }

    public void removeBoard(UUID playerId) {
        boards.remove(playerId);
    }

    public void refreshOne(Player player) {
        Scoreboard board = boardFor(player);
        if (!rankManager.shouldSkipOwnTabHandling()) {
            rebuildNametagTeams(board);
        }

        PlayerData data = dataStore.getOrCreate(player.getUniqueId(), player.getName());
        if (data.isScoreboardEnabled()) {
            rebuildSidebar(board, data);
        }
    }

    private void rebuildNametagTeams(Scoreboard board) {
        for (Team team : new ArrayList<>(board.getTeams())) {
            team.unregister();
        }
        int i = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            PlayerData data = dataStore.getOrCreate(online.getUniqueId(), online.getName());
            TextColor color = rankManager.rankColor(data.getCurrentRank());
            String rankStr = data.getCurrentRank() > 0 ? "#" + data.getCurrentRank() : "#?";

            Team team = board.registerNewTeam("r" + (i++));
            team.addEntry(online.getName());
            team.prefix(Component.text("[" + rankStr + "] ", color));
            if (color instanceof NamedTextColor named) {
                team.color(named);
            }
        }
    }

    private void rebuildSidebar(Scoreboard board, PlayerData data) {
        Objective existing = board.getObjective("timesmp");
        if (existing != null) existing.unregister();

        Objective objective = board.registerNewObjective("timesmp", Criteria.DUMMY, Component.text(""));
        objective.displayName(Component.text("CLANKER", NamedTextColor.RED)
                .append(Component.text(" SMP", TextColor.color(0xFFA500))));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        List<Component> lines = buildLines(data);
        int score = lines.size();
        for (int i = 0; i < lines.size(); i++) {
            String entry = org.bukkit.ChatColor.COLOR_CHAR + Integer.toHexString(i) + org.bukkit.ChatColor.RESET;
            Team team = board.registerNewTeam("line" + i);
            team.addEntry(entry);
            team.prefix(lines.get(i));
            objective.getScore(entry).setScore(score--);
        }
    }

    private List<Component> buildLines(PlayerData data) {
        List<Component> lines = new ArrayList<>();

        if (dataStore.getState() != GameState.RUNNING) {
            NamedTextColor statusColor = dataStore.getState() == GameState.PAUSED ? NamedTextColor.YELLOW : NamedTextColor.RED;
            lines.add(Component.text(dataStore.getState().name(), statusColor));
        }

        int rank = data.getCurrentRank();
        lines.add(Component.text("Rank: ", NamedTextColor.GRAY)
                .append(Component.text(rank > 0 ? "#" + rank : "unranked", rankManager.rankColor(rank))));
        lines.add(Component.text("Time: ", NamedTextColor.GRAY)
                .append(TimeUtil.formatColored(data.getTimeSeconds())));
        lines.add(Component.text("Kills: ", NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(data.getKills()), NamedTextColor.GREEN)));
        lines.add(Component.text("Deaths: ", NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(data.getDeaths()), NamedTextColor.RED)));

        Quest quest = data.getActiveQuest();
        lines.add(Component.text("Quest: ", NamedTextColor.AQUA));
        if (quest != null) {
            lines.add(Component.text(shorten(quest.describe()), NamedTextColor.WHITE));
            lines.add(Component.text(quest.describeProgress(), NamedTextColor.GREEN));
        } else {
            lines.add(Component.text("next one soon...", NamedTextColor.DARK_GRAY));
        }

        lines.add(Component.text(" "));
        lines.add(Component.text(data.getLastKnownName(), NamedTextColor.GOLD));

        return lines;
    }

    private String shorten(String s) {
        return s.length() > 30 ? s.substring(0, 27) + "..." : s;
    }
}

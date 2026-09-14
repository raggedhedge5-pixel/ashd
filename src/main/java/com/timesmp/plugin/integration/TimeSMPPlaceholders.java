package com.timesmp.plugin.integration;

import com.timesmp.plugin.data.DataStore;
import com.timesmp.plugin.data.PlayerData;
import com.timesmp.plugin.rank.RankManager;
import com.timesmp.plugin.util.TimeUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

/**
 * Exposes TimeSMP's data as PlaceholderAPI placeholders (%timesmp_...%). This
 * exists specifically for servers running a dedicated tab-list/nametag plugin
 * (like TAB) - those plugins take over tab-list and nametag rendering via their
 * own packet system and simply overwrite anything TimeSMP sets directly through
 * Bukkit's tab-list/Scoreboard-Team APIs on their next refresh. Point TAB's own
 * nametag/tab-list format config at these placeholders instead of fighting it -
 * see the README for exact placeholder names and an example TAB format string.
 *
 * Only ever constructed/registered if the PlaceholderAPI plugin is actually
 * installed (checked in TimeSMP.onEnable before this class is ever referenced),
 * so servers without it never attempt to load this class at all.
 */
public class TimeSMPPlaceholders extends PlaceholderExpansion {

    private final DataStore dataStore;
    private final RankManager rankManager;

    public TimeSMPPlaceholders(DataStore dataStore, RankManager rankManager) {
        this.dataStore = dataStore;
        this.rankManager = rankManager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "timesmp";
    }

    @Override
    public @NotNull String getAuthor() {
        return "TimeSMP";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null || player.getUniqueId() == null) return "";
        PlayerData data = dataStore.get(player.getUniqueId());
        if (data == null) return "";

        int rank = data.getCurrentRank();

        switch (params.toLowerCase()) {
            case "rank":
                return rank > 0 ? String.valueOf(rank) : "?";
            case "rank_tag":
                return rank > 0 ? "#" + rank : "#?";
            case "rank_color":
                return legacyColorCode(rankManager.rankColor(rank));
            case "time":
                return TimeUtil.format(data.getTimeSeconds());
            case "hearts":
                int hearts = data.isInDebt() ? 8 : rankManager.getHeartsForRank(rank <= 0 ? Integer.MAX_VALUE : rank);
                return String.valueOf(hearts);
            case "kills":
                return String.valueOf(data.getKills());
            case "deaths":
                return String.valueOf(data.getDeaths());
            default:
                return null;
        }
    }

    /** Maps our rank tiers to a legacy "&"-free color code most tab-list plugins (including TAB) understand directly. */
    private String legacyColorCode(TextColor color) {
        if (color == NamedTextColor.RED) return "§c";
        if (color == NamedTextColor.YELLOW) return "§e";
        return "§6"; // the "orange" tier (#2-40) - no legacy equivalent, approximated as gold.
    }
}

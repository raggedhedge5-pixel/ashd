package com.timesmp.plugin.rank;

import com.timesmp.plugin.data.DataStore;
import com.timesmp.plugin.data.PlayerData;
import com.timesmp.plugin.integration.LuckPermsHook;
import com.timesmp.plugin.util.WorldFilter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Recalculates the global leaderboard (ranked purely by banked time, highest first)
 * and applies the consequences: bonus/penalty hearts, tab list rank tag + ordering,
 * and keeps a cached sorted list for commands like /top.
 */
public class RankManager {

    private static final TextColor ORANGE = TextColor.color(0xFFA500);

    private final Plugin plugin;
    private final DataStore dataStore;
    private final FileConfiguration config;
    private final WorldFilter worldFilter;
    private final LuckPermsHook luckPermsHook;

    private List<PlayerData> sortedCache = new ArrayList<>();

    public RankManager(Plugin plugin, DataStore dataStore, WorldFilter worldFilter, LuckPermsHook luckPermsHook) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        this.config = plugin.getConfig();
        this.worldFilter = worldFilter;
        this.luckPermsHook = luckPermsHook;
    }

    /** Recomputes ranks for ALL known players (online or not) and applies live effects to online ones. */
    public void refresh() {
        List<PlayerData> all = new ArrayList<>(dataStore.all().values());
        all.sort(Comparator.comparingLong(PlayerData::getTimeSeconds).reversed());

        for (int i = 0; i < all.size(); i++) {
            all.get(i).setCurrentRank(i + 1);
        }
        this.sortedCache = all;

        for (PlayerData data : all) {
            Player player = Bukkit.getPlayer(data.getUuid());
            if (player != null && player.isOnline()) {
                applyHearts(player, data);
                applyTabName(player, data);
            }
        }
    }

    public List<PlayerData> getSortedCache() {
        return sortedCache;
    }

    public int getHeartsForRank(int rank) {
        int rank1 = config.getInt("hearts.rank-1", 20);
        int rank2to5 = config.getInt("hearts.rank-2-to-5", 19);
        int rank6 = config.getInt("hearts.rank-6", 19);
        int rank40 = config.getInt("hearts.rank-40", 11);
        int everyoneElse = config.getInt("hearts.everyone-else", 10);
        int cutoff = config.getInt("hearts.top-rank-cutoff", 40);

        if (rank == 1) return rank1;
        if (rank >= 2 && rank <= 5) return rank2to5;
        if (rank >= 6 && rank <= cutoff) {
            double span = Math.max(1, cutoff - 6);
            double t = (rank - 6) / span;
            double hearts = rank6 - t * (rank6 - rank40);
            return (int) Math.round(hearts);
        }
        return everyoneElse;
    }

    public void applyHearts(Player player, PlayerData data) {
        int hearts;
        if (worldFilter.isDisabled(player.getWorld())) {
            hearts = 10;
        } else if (data.isInDebt()) {
            hearts = config.getInt("hearts.negative-balance-hearts", 8);
        } else {
            hearts = getHeartsForRank(data.getCurrentRank() <= 0 ? Integer.MAX_VALUE : data.getCurrentRank());
        }
        double newMax = hearts * 2.0;

        var attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;
        double oldMax = attr.getBaseValue();
        if (Math.abs(oldMax - newMax) < 0.01) return;

        boolean healOnIncrease = config.getBoolean("heal-on-increase", true);
        attr.setBaseValue(newMax);

        if (newMax < player.getHealth()) {
            player.setHealth(Math.max(1.0, newMax));
        } else if (newMax > oldMax && healOnIncrease) {
            player.setHealth(newMax);
        }
    }

    public void applyTabName(Player player, PlayerData data) {
        if (shouldSkipOwnTabHandling()) {
            // A dedicated tab-list plugin (TAB) is installed and force-own-tab-and-nametags is
            // false - TAB takes over tab-list/nametag rendering via its own packet system and
            // will just overwrite anything set here on its next refresh, so there's no point
            // fighting it by default. Use the PlaceholderAPI expansion (timesmp_rank,
            // timesmp_rank_color, etc - see README) in TAB's own config instead, or set
            // force-own-tab-and-nametags: true in config.yml to make TimeSMP handle it directly.
            return;
        }
        String rankStr = data.getCurrentRank() > 0 ? "#" + data.getCurrentRank() : "#?";
        Component listName = Component.text("[" + rankStr + "] ", rankColor(data.getCurrentRank()))
                .append(Component.text(player.getName(), NamedTextColor.WHITE));
        Component suffix = luckPermsHook.getSuffix(player);
        if (!suffix.equals(Component.empty())) {
            listName = listName.append(Component.text(" ")).append(suffix);
        }
        player.playerListName(listName);
        if (data.getCurrentRank() > 0) {
            // Paper's tab-list order: LOWER order value sorts LATER in the list (confirmed
            // backwards from the intuitive reading during testing), so rank #1 needs the
            // highest order value to land at the top - hence the inversion here.
            player.setPlayerListOrder(Integer.MAX_VALUE - data.getCurrentRank());
        }
    }

    public boolean isTabPluginPresent() {
        return Bukkit.getPluginManager().getPlugin("TAB") != null;
    }

    public boolean shouldSkipOwnTabHandling() {
        return isTabPluginPresent() && !config.getBoolean("force-own-tab-and-nametags", false);
    }

    public Component chatPrefix(PlayerData data) {
        String rankStr = data.getCurrentRank() > 0 ? "#" + data.getCurrentRank() : "#?";
        return Component.text("[" + rankStr + "] ", rankColor(data.getCurrentRank()));
    }

    public int getExtraInventorySlotsForRank(int rank) {
        int cutoff = config.getInt("extra-inventory.top-rank-cutoff", 20);
        if (rank < 1 || rank > cutoff) return 0;
        int rank1Slots = config.getInt("extra-inventory.rank-1-slots", 27);
        int cutoffSlots = config.getInt("extra-inventory.cutoff-rank-slots", 5);
        if (cutoff <= 1) return rank1Slots;
        double t = (rank - 1.0) / (cutoff - 1.0);
        double slots = rank1Slots - t * (rank1Slots - cutoffSlots);
        return (int) Math.round(slots);
    }

    /** #1 = red, #2-40 = orange, everyone else = yellow. */
    public TextColor rankColor(int rank) {
        if (rank == 1) return NamedTextColor.RED;
        if (rank >= 2 && rank <= 40) return ORANGE;
        return NamedTextColor.YELLOW;
    }
}

package com.timesmp.plugin.inventory;

import com.timesmp.plugin.data.DataStore;
import com.timesmp.plugin.data.PlayerData;
import com.timesmp.plugin.rank.RankManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Rank-gated bonus inventory (top N players only, size scaled by rank). Internally
 * it's always the same fixed-size chest GUI (rounded up to a multiple of 9 from the
 * rank-1 slot count) - slots the player hasn't unlocked are filled with a locked
 * placeholder rather than the GUI literally shrinking, since Bukkit chest inventories
 * can't be arbitrary non-multiple-of-9 sizes.
 *
 * Design note: a player's "allowed slot count" is captured once, when they open the
 * GUI, and stays fixed for that viewing session even if their rank changes underneath
 * them (e.g. a live tick recalculates ranks while they're browsing). This avoids a
 * whole class of desync bugs from resizing a GUI a player currently has open. Their
 * new allowance simply applies next time they open it.
 */
public class ExtraInventoryManager {

    private final Plugin plugin;
    private final DataStore dataStore;
    private final RankManager rankManager;
    private final FileConfiguration config;

    public ExtraInventoryManager(Plugin plugin, DataStore dataStore, RankManager rankManager) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        this.rankManager = rankManager;
        this.config = plugin.getConfig();
    }

    private int guiSize() {
        int maxSlots = config.getInt("extra-inventory.rank-1-slots", 27);
        int size = ((maxSlots + 8) / 9) * 9; // round up to nearest multiple of 9
        return Math.max(9, Math.min(54, size));
    }

    public boolean open(Player player) {
        PlayerData data = dataStore.getOrCreate(player.getUniqueId(), player.getName());
        int rank = data.getCurrentRank();
        int allowed = rankManager.getExtraInventorySlotsForRank(rank);

        if (allowed <= 0) {
            int cutoff = config.getInt("extra-inventory.top-rank-cutoff", 20);
            player.sendMessage(Component.text("You need to be in the top " + cutoff + " to use the extra inventory.", NamedTextColor.RED));
            return false;
        }

        int size = guiSize();
        ExtraInventoryHolder holder = new ExtraInventoryHolder(player.getUniqueId(), allowed);
        Inventory inv = Bukkit.createInventory(holder, size,
                Component.text("Extra Inventory ", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD)
                        .append(Component.text("(#" + rank + ")", NamedTextColor.GRAY)));
        holder.setInventory(inv);

        List<ItemStack> stored = data.getExtraInventoryItems();
        for (int i = 0; i < size; i++) {
            if (i < allowed) {
                inv.setItem(i, i < stored.size() ? stored.get(i) : null);
            } else {
                inv.setItem(i, lockedPlaceholder());
            }
        }

        player.openInventory(inv);
        return true;
    }

    /** Call on inventory close - reads back only the unlocked slots and persists them. */
    public void saveFromInventory(Player player, Inventory inv, ExtraInventoryHolder holder) {
        PlayerData data = dataStore.getOrCreate(player.getUniqueId(), player.getName());
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < holder.getAllowedSlots(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                items.add(item.clone());
            }
        }
        data.setExtraInventoryItems(items);
    }

    /** Call on death - drops everything currently stored and empties it. */
    public void dropOnDeath(Player player) {
        // If they somehow died with the GUI open, force it closed first so any
        // last-second edits get saved before we read/clear the stored contents.
        InventoryHolder openHolder = player.getOpenInventory().getTopInventory().getHolder();
        if (openHolder instanceof ExtraInventoryHolder h) {
            saveFromInventory(player, player.getOpenInventory().getTopInventory(), h);
            player.closeInventory();
        }

        PlayerData data = dataStore.getOrCreate(player.getUniqueId(), player.getName());
        List<ItemStack> items = data.getExtraInventoryItems();
        if (items.isEmpty()) return;

        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world != null) {
            for (ItemStack item : items) {
                if (item != null && item.getType() != Material.AIR) {
                    world.dropItemNaturally(loc, item);
                }
            }
        }
        data.setExtraInventoryItems(new ArrayList<>());
    }

    private ItemStack lockedPlaceholder() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Locked", NamedTextColor.RED, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Climb the leaderboard to", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("unlock more slots!", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }
}

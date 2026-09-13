package com.timesmp.plugin.guide;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the "how this server works" guide book and hands it out on join, but
 * only if the player doesn't already have one - checked via a hidden persistent
 * data tag (not by name/lore, so a renamed or lore-edited copy still counts).
 */
public class GuideBook {

    private final Plugin plugin;
    private final NamespacedKey tagKey;

    public GuideBook(Plugin plugin) {
        this.plugin = plugin;
        this.tagKey = new NamespacedKey(plugin, "guide_book");
    }

    public ItemStack build() {
        FileConfiguration config = plugin.getConfig();
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();

        meta.title(Component.text(config.getString("guide-book.title", "The Clanker Plugin!")));
        meta.author(Component.text(config.getString("guide-book.author", "Server")));

        List<String> pageStrings = config.getStringList("guide-book.pages");
        List<Component> pages = new ArrayList<>();
        for (String page : pageStrings) {
            pages.add(Component.text(page));
        }
        meta.pages(pages);

        meta.getPersistentDataContainer().set(tagKey, PersistentDataType.BOOLEAN, true);
        book.setItemMeta(meta);
        return book;
    }

    public boolean alreadyHasBook(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() != Material.WRITTEN_BOOK) continue;
            BookMeta meta = (BookMeta) item.getItemMeta();
            if (meta != null && Boolean.TRUE.equals(meta.getPersistentDataContainer().get(tagKey, PersistentDataType.BOOLEAN))) {
                return true;
            }
        }
        return false;
    }

    /** Gives the book if the player doesn't already have one; drops it at their feet if their inventory is full. */
    public void giveIfMissing(Player player) {
        if (alreadyHasBook(player)) return;
        ItemStack book = build();
        var leftover = player.getInventory().addItem(book);
        for (ItemStack notFitted : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), notFitted);
        }
    }
}

package com.timesmp.plugin.inventory;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * A physical, tradeable "1 hour" clock. Withdrawn from a player's banked time via
 * /withdraw, and redeemed by right-clicking it (see HourTokenListener), which adds
 * exactly 1 hour to whoever clicks it and consumes one from the stack.
 */
public class HourToken {

    private final NamespacedKey tagKey;

    public HourToken(Plugin plugin) {
        this.tagKey = new NamespacedKey(plugin, "hour_token");
    }

    public NamespacedKey getTagKey() {
        return tagKey;
    }

    public ItemStack create(int amount) {
        ItemStack item = new ItemStack(Material.CLOCK, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("1 HOUR", NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Right-click to claim", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("+1 hour of banked time!", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(tagKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isHourToken(ItemStack item) {
        if (item == null || item.getType() != Material.CLOCK || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return Boolean.TRUE.equals(meta.getPersistentDataContainer().get(tagKey, PersistentDataType.BOOLEAN));
    }
}

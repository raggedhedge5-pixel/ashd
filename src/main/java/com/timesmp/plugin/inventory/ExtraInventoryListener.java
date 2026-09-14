package com.timesmp.plugin.inventory;

import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Locks the placeholder slots of the extra-inventory GUI against being touched,
 * and persists real contents back to storage whenever it's closed.
 */
public class ExtraInventoryListener implements Listener {

    private final ExtraInventoryManager manager;

    public ExtraInventoryListener(ExtraInventoryManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ExtraInventoryHolder holder)) return;

        int topSize = event.getView().getTopInventory().getSize();
        int rawSlot = event.getRawSlot();
        boolean touchesLockedSlot = rawSlot >= 0 && rawSlot < topSize && rawSlot >= holder.getAllowedSlots();
        boolean touchesPlaceholderItem = event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.GRAY_STAINED_GLASS_PANE;

        if (touchesLockedSlot || touchesPlaceholderItem) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof ExtraInventoryHolder holder)) return;

        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize && rawSlot >= holder.getAllowedSlots()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof ExtraInventoryHolder holder)) return;
        HumanEntity entity = event.getPlayer();
        if (entity instanceof Player player) {
            manager.saveFromInventory(player, event.getInventory(), holder);
        }
    }
}

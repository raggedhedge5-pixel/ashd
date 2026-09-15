package com.quickduel.plugin.listeners;

import com.quickduel.plugin.duel.DuelGuiHolder;
import com.quickduel.plugin.duel.DuelManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

public class DuelGuiListener implements Listener {

    private final DuelManager duelManager;

    public DuelGuiListener(DuelManager duelManager) {
        this.duelManager = duelManager;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof DuelGuiHolder holder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();

        if (slot == 4) {
            holder.markResolved();
            player.closeInventory();
            duelManager.confirmAccept(player, holder);
        } else if (slot == 5) {
            holder.markResolved();
            player.closeInventory();
            duelManager.cancelConfirmation(player);
        }
        // any other slot: just cancelled above, no action
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof DuelGuiHolder holder)) return;
        if (holder.isResolved()) return; // already handled by an explicit Accept/Cancel click

        if (event.getPlayer() instanceof Player player) {
            duelManager.cancelConfirmation(player);
        }
    }
}

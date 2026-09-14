package com.timesmp.plugin.inventory;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Marker holder so we can reliably recognise our custom GUI in click/close/drag
 * events (via event.getInventory().getHolder() instanceof ExtraInventoryHolder),
 * and so we know, at the moment this particular session was opened, how many of
 * its slots were actually unlocked for that player.
 */
public class ExtraInventoryHolder implements InventoryHolder {

    private final UUID ownerUuid;
    private final int allowedSlots;
    private Inventory inventory;

    public ExtraInventoryHolder(UUID ownerUuid, int allowedSlots) {
        this.ownerUuid = ownerUuid;
        this.allowedSlots = allowedSlots;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public int getAllowedSlots() {
        return allowedSlots;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

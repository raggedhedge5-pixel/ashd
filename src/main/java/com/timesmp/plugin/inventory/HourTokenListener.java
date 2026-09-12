package com.timesmp.plugin.inventory;

import com.timesmp.plugin.data.DataStore;
import com.timesmp.plugin.data.PlayerData;
import com.timesmp.plugin.rank.RankManager;
import com.timesmp.plugin.util.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class HourTokenListener implements Listener {

    private final DataStore dataStore;
    private final RankManager rankManager;
    private final HourToken hourToken;

    public HourTokenListener(DataStore dataStore, RankManager rankManager, HourToken hourToken) {
        this.dataStore = dataStore;
        this.rankManager = rankManager;
        this.hourToken = hourToken;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (!hourToken.isHourToken(item)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        PlayerData data = dataStore.getOrCreate(player.getUniqueId(), player.getName());
        data.addTimeSeconds(TimeUtil.hoursToSeconds(1));
        rankManager.refresh();

        if (item.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            item.setAmount(item.getAmount() - 1);
        }

        player.sendMessage(Component.text("Claimed +1 hour! New balance: ", NamedTextColor.GOLD)
                .append(TimeUtil.formatColored(data.getTimeSeconds())));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.4f);
    }
}

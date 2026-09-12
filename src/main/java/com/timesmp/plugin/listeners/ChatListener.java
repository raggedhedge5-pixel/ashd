package com.timesmp.plugin.listeners;

import com.timesmp.plugin.data.DataStore;
import com.timesmp.plugin.data.PlayerData;
import com.timesmp.plugin.rank.RankManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Prepends the player's live rank tag to their chat messages, e.g. "[#23] Mi14s: hello".
 */
public class ChatListener implements Listener {

    private final DataStore dataStore;
    private final RankManager rankManager;

    public ChatListener(DataStore dataStore, RankManager rankManager) {
        this.dataStore = dataStore;
        this.rankManager = rankManager;
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        var player = event.getPlayer();
        PlayerData data = dataStore.getOrCreate(player.getUniqueId(), player.getName());
        Component prefix = rankManager.chatPrefix(data);

        event.renderer((source, sourceDisplayName, message, viewer) ->
                Component.text()
                        .append(prefix)
                        .append(Component.text(source.getName() + ": ", NamedTextColor.WHITE))
                        .append(message)
                        .build());
    }
}

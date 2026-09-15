package com.quickduel.plugin;

import com.quickduel.plugin.commands.DuelCommand;
import com.quickduel.plugin.duel.DuelManager;
import com.quickduel.plugin.duel.FreezeManager;
import com.quickduel.plugin.listeners.DuelGuiListener;
import com.quickduel.plugin.listeners.FreezeListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class QuickDuel extends JavaPlugin implements Listener {

    private DuelManager duelManager;
    private FreezeManager freezeManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        freezeManager = new FreezeManager();
        duelManager = new DuelManager(this, freezeManager);

        DuelCommand duelCommand = new DuelCommand(duelManager);
        var cmd = getCommand("duel");
        if (cmd != null) {
            cmd.setExecutor(duelCommand);
            cmd.setTabCompleter(duelCommand);
        }

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new FreezeListener(freezeManager), this);
        getServer().getPluginManager().registerEvents(new DuelGuiListener(duelManager), this);

        getLogger().info("QuickDuel enabled.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Drop any pending request TO this player - no point letting them accept/decline
        // something after logging out and back in with no context. (If they disconnect
        // mid-countdown, DuelCountdownTask notices within a second and cleans that up too.)
        duelManager.forget(event.getPlayer().getUniqueId());
    }
}

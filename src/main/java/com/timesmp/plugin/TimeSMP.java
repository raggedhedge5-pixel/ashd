package com.timesmp.plugin;

import com.timesmp.plugin.commands.ExtraInventoryCommand;
import com.timesmp.plugin.commands.PlayerCommands;
import com.timesmp.plugin.commands.TimeSMPCommand;
import com.timesmp.plugin.commands.WithdrawCommand;
import com.timesmp.plugin.afk.AfkListener;
import com.timesmp.plugin.afk.AfkManager;
import com.timesmp.plugin.combat.CombatConfig;
import com.timesmp.plugin.combat.CombatLogListener;
import com.timesmp.plugin.combat.CombatManager;
import com.timesmp.plugin.combat.CombatTagListener;
import com.timesmp.plugin.combat.PvpKillResolver;
import com.timesmp.plugin.data.DataStore;
import com.timesmp.plugin.guide.GuideBook;
import com.timesmp.plugin.inventory.ExtraInventoryListener;
import com.timesmp.plugin.inventory.ExtraInventoryManager;
import com.timesmp.plugin.inventory.HourToken;
import com.timesmp.plugin.inventory.HourTokenListener;
import com.timesmp.plugin.listeners.ChatListener;
import com.timesmp.plugin.listeners.CombatListener;
import com.timesmp.plugin.listeners.DamageListener;
import com.timesmp.plugin.listeners.JoinQuitListener;
import com.timesmp.plugin.quest.QuestManager;
import com.timesmp.plugin.rank.RankManager;
import com.timesmp.plugin.scoreboard.ScoreboardManager;
import com.timesmp.plugin.tasks.TimerTask;
import com.timesmp.plugin.util.WorldFilter;
import org.bukkit.plugin.java.JavaPlugin;

public class TimeSMP extends JavaPlugin {

    private DataStore dataStore;
    private QuestManager questManager;
    private RankManager rankManager;
    private ScoreboardManager scoreboardManager;
    private ExtraInventoryManager extraInventoryManager;
    private GuideBook guideBook;
    private HourToken hourToken;
    private WorldFilter worldFilter;
    private AfkManager afkManager;
    private CombatConfig combatConfig;
    private CombatManager combatManager;
    private PvpKillResolver pvpKillResolver;
    private TimerTask timerTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        dataStore = new DataStore(this);
        dataStore.setStartingTimeSeconds(getConfig().getLong("starting-time-seconds", 0));
        dataStore.load();

        worldFilter = new WorldFilter(this);
        questManager = new QuestManager(this, dataStore);
        rankManager = new RankManager(this, dataStore, worldFilter);
        scoreboardManager = new ScoreboardManager(this, dataStore, rankManager);
        extraInventoryManager = new ExtraInventoryManager(this, dataStore, rankManager);
        guideBook = new GuideBook(this);
        hourToken = new HourToken(this);
        afkManager = new AfkManager(this);

        combatConfig = new CombatConfig(this);
        combatConfig.load();
        combatManager = new CombatManager(combatConfig);
        pvpKillResolver = new PvpKillResolver(this, dataStore, questManager);

        getServer().getPluginManager().registerEvents(
                new CombatListener(this, dataStore, questManager, rankManager, extraInventoryManager, worldFilter, pvpKillResolver), this);
        getServer().getPluginManager().registerEvents(
                new DamageListener(dataStore, questManager, worldFilter), this);
        getServer().getPluginManager().registerEvents(
                new JoinQuitListener(dataStore, questManager, rankManager, scoreboardManager, guideBook), this);
        getServer().getPluginManager().registerEvents(
                new ChatListener(dataStore, rankManager), this);
        getServer().getPluginManager().registerEvents(
                new ExtraInventoryListener(extraInventoryManager), this);
        getServer().getPluginManager().registerEvents(
                new HourTokenListener(dataStore, rankManager, hourToken), this);
        getServer().getPluginManager().registerEvents(
                new AfkListener(afkManager), this);
        getServer().getPluginManager().registerEvents(
                new CombatTagListener(combatManager, combatConfig), this);
        getServer().getPluginManager().registerEvents(
                new CombatLogListener(this, dataStore, rankManager, extraInventoryManager, combatManager, combatConfig, pvpKillResolver, questManager), this);

        TimeSMPCommand adminCommand = new TimeSMPCommand(this, dataStore, questManager, rankManager, combatConfig);
        var adminExec = getCommand("timesmp");
        if (adminExec != null) adminExec.setExecutor(adminCommand);

        PlayerCommands playerCommands = new PlayerCommands(dataStore, rankManager, scoreboardManager);
        for (String label : new String[] {"playtime", "quest", "top", "timeboard"}) {
            var cmd = getCommand(label);
            if (cmd != null) cmd.setExecutor(playerCommands);
        }

        ExtraInventoryCommand extraInventoryCommand = new ExtraInventoryCommand(extraInventoryManager);
        var extraInvCmd = getCommand("extrainventory");
        if (extraInvCmd != null) extraInvCmd.setExecutor(extraInventoryCommand);

        WithdrawCommand withdrawCommand = new WithdrawCommand(this, dataStore, rankManager, hourToken);
        var withdrawCmd = getCommand("withdraw");
        if (withdrawCmd != null) withdrawCmd.setExecutor(withdrawCommand);

        timerTask = new TimerTask(this, dataStore, questManager, rankManager, scoreboardManager, worldFilter, afkManager, combatManager);
        timerTask.runTaskTimer(this, 20L, 20L); // every 1 second

        rankManager.refresh();

        getLogger().info("TimeSMP enabled.");
    }

    @Override
    public void onDisable() {
        if (timerTask != null) {
            timerTask.cancel();
        }
        if (dataStore != null) {
            dataStore.save();
        }
        getLogger().info("TimeSMP disabled, data saved.");
    }

    public DataStore getDataStore() {
        return dataStore;
    }

    public QuestManager getQuestManager() {
        return questManager;
    }

    public RankManager getRankManager() {
        return rankManager;
    }

    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    public ExtraInventoryManager getExtraInventoryManager() {
        return extraInventoryManager;
    }

    public CombatConfig getCombatConfig() {
        return combatConfig;
    }

    public CombatManager getCombatManager() {
        return combatManager;
    }
}

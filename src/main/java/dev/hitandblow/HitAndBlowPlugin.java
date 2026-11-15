package dev.hitandblow;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** プラグイン本体 */
public final class HitAndBlowPlugin extends JavaPlugin {
    private GameManager games;
    private Bank bank;
    private Ledger ledger;
    private int turnSeconds;
    private int recruitSeconds;
    private long maxBet;
    private boolean accepting;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        bank = new Bank(this);
        ledger = new Ledger(this);
        ledger.warnLeftovers();
        games = new GameManager(this);
        HbCommand cmd = new HbCommand(this);
        PluginCommand hb = getCommand("hb");
        if (hb != null) {
            hb.setExecutor(cmd);
            hb.setTabCompleter(cmd);
        }
        getServer().getPluginManager().registerEvents(new GameListener(this), this);
    }

    @Override
    public void onDisable() {
        if (games != null) games.stopAll("§7サーバー（プラグイン）停止のため終了します。");
    }

    void loadSettings() {
        reloadConfig();
        turnSeconds = Math.max(1, Math.min(64, getConfig().getInt("turn-seconds", 30)));
        recruitSeconds = Math.max(1, getConfig().getInt("recruit-seconds", 60));
        maxBet = Math.max(0, getConfig().getLong("max-bet", 100000));
        accepting = getConfig().getBoolean("enabled", true);
    }

    /** /hb maxbet：変更して config.yml に保存する */
    void setMaxBet(long value) {
        maxBet = value;
        getConfig().set("max-bet", value);
        saveConfig();
    }

    /** /hb on・/hb off：変更して config.yml に保存する */
    void setAccepting(boolean value) {
        accepting = value;
        getConfig().set("enabled", value);
        saveConfig();
    }

    public GameManager games() {
        return games;
    }

    Bank bank() {
        return bank;
    }

    Ledger ledger() {
        return ledger;
    }

    public int turnSeconds() {
        return turnSeconds;
    }

    public int recruitSeconds() {
        return recruitSeconds;
    }

    long maxBet() {
        return maxBet;
    }

    boolean accepting() {
        return accepting;
    }
}

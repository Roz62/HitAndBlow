package dev.hitandblow;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Vault 経由のお金の出し入れ。Vault に触るのはこのクラスだけ。
 * 経済プラグイン（EssentialsX など）は後から有効になることがあるので、使うたびに取りに行く。
 */
final class Bank {
    private final HitAndBlowPlugin plugin;

    Bank(HitAndBlowPlugin plugin) {
        this.plugin = plugin;
    }

    static String yen(long amount) {
        return String.format("%,d円", amount);
    }

    /** 経済プラグインが使えるか */
    boolean available() {
        return Bukkit.getPluginManager().isPluginEnabled("Vault") && economy() != null;
    }

    boolean has(OfflinePlayer p, long amount) {
        Economy eco = economy();
        return eco != null && eco.has(p, amount);
    }

    boolean withdraw(OfflinePlayer p, long amount) {
        Economy eco = economy();
        return eco != null && eco.withdrawPlayer(p, amount).transactionSuccess();
    }

    /** 入金。失敗したらログに残して false（返金・配当の取りこぼしを後で追えるように） */
    boolean deposit(OfflinePlayer p, long amount) {
        Economy eco = economy();
        if (eco != null && eco.depositPlayer(p, amount).transactionSuccess()) return true;
        plugin.getLogger().severe("入金に失敗しました: " + p.getName() + " (" + p.getUniqueId() + ") に " + yen(amount));
        return false;
    }

    private static Economy economy() {
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        return rsp == null ? null : rsp.getProvider();
    }
}

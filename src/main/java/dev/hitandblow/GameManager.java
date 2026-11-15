package dev.hitandblow;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 募集と進行中ゲームの管理。1人が関われるのは募集1つ or ゲーム1つまで。 */
public final class GameManager {

    /** 募集。target が null なら全体向け、それ以外は招待相手だけが参加できる。bet は1人あたりの賭け金 */
    record Recruit(UUID host, String hostName, UUID target, String targetName, long bet, long expiresAt, BukkitTask expiry) {}

    private final HitAndBlowPlugin plugin;
    private final Map<UUID, Recruit> recruits = new LinkedHashMap<>();   // key: 募集者
    private final Map<UUID, Game> games = new HashMap<>();               // key: 参加者（2人分）

    GameManager(HitAndBlowPlugin plugin) {
        this.plugin = plugin;
    }

    // ── 状態の問い合わせ ──

    public Game gameOf(UUID player) {
        return games.get(player);
    }

    public boolean isBusy(UUID player) {
        return games.containsKey(player) || recruits.containsKey(player);
    }

    /** viewer が参加できる募集の一覧（全体向け＋自分宛ての招待） */
    public List<Recruit> joinableFor(UUID viewer) {
        List<Recruit> list = new ArrayList<>();
        for (Recruit r : recruits.values()) {
            if (r.host().equals(viewer)) continue;
            if (r.target() == null || r.target().equals(viewer)) list.add(r);
        }
        return list;
    }

    static String betLabel(long bet) {
        return bet > 0 ? "§6賭け金 " + Bank.yen(bet) : "§7賭け金なし";
    }

    // ── 募集 ──

    /** 募集メッセージを出し直す残り秒数（出した直後の1回と合わせて計3回） */
    private static final int[] REMIND_AT = {40, 20};

    /** @param target null なら全体向け */
    public void recruit(Player host, Player target, long bet) {
        UUID id = host.getUniqueId();
        int seconds = plugin.recruitSeconds();
        long expiresAt = System.currentTimeMillis() + seconds * 1000L;
        int[] left = {seconds};
        // 1秒ごとに数え、残り40秒・20秒で募集メッセージを出し直し、0秒で締め切る
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Recruit r = recruits.get(id);
            if (r == null) return;
            left[0]--;
            if (left[0] <= 0) { expire(id); return; }
            for (int at : REMIND_AT) if (left[0] == at) announce(r, at);
        }, 20L, 20L);
        Recruit r = new Recruit(id, host.getName(), target == null ? null : target.getUniqueId(),
                target == null ? null : target.getName(), bet, expiresAt, task);
        recruits.put(id, r);
        announce(r, seconds);
    }

    /** 募集メッセージ。募集者本人にも同じ形で出す（「募集を出しました」は出さない） */
    private void announce(Recruit r, int secondsLeft) {
        Component join = joinButton(r.hostName());
        String info = " §f(" + betLabel(r.bet()) + "§f) §7（残り" + secondsLeft + "秒） ";
        if (r.target() == null) {
            Component msg = Items.text(Msg.PREFIX + "§e" + r.hostName() + " §fが§bヒット＆ブロー§fの対戦相手を募集中！" + info).append(join);
            for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(msg);
        } else {
            Player target = Bukkit.getPlayer(r.target());
            if (target != null) {
                target.sendMessage(Items.text(Msg.PREFIX + "§e" + r.hostName() + " §fから§bヒット＆ブロー§fの招待が届きました！" + info).append(join));
            }
            Player host = Bukkit.getPlayer(r.host());
            if (host != null) {
                host.sendMessage(Items.text(Msg.PREFIX + "§e" + r.hostName() + " §fが §e" + r.targetName() + " §fを§bヒット＆ブロー§fに招待中！" + info).append(join));
            }
        }
    }

    /** 募集の残り秒数（/hb list 用） */
    static long secondsLeft(Recruit r) {
        return Math.max(0, (r.expiresAt() - System.currentTimeMillis() + 999) / 1000);
    }

    /** [ここをクリックで参加] ボタン */
    static Component joinButton(String hostName) {
        return Items.text("§a§l[ここをクリックで参加]")
                .clickEvent(ClickEvent.runCommand("/hb join " + hostName))
                .hoverEvent(HoverEvent.showText(Items.text("§7/hb join " + hostName)));
    }

    private void expire(UUID host) {
        Recruit r = recruits.remove(host);
        if (r == null) return;
        r.expiry().cancel();
        Player p = Bukkit.getPlayer(host);
        if (p != null) p.sendMessage(Items.text(Msg.PREFIX + "§7募集の期限が切れました。"));
    }

    /** 募集を取り消す（ログアウト時・ゲーム開始時） */
    public void cancelRecruit(UUID host) {
        Recruit r = recruits.remove(host);
        if (r != null) r.expiry().cancel();
    }

    /** 参加処理。エラー時はメッセージを返し、成功時は null */
    public String join(Player joiner, String hostName) {
        if (!plugin.accepting()) return "§c現在ヒット＆ブローは停止中です。";
        Player host = Bukkit.getPlayerExact(hostName);
        if (host == null) return "§cそのプレイヤーはオンラインではありません。";
        if (host.equals(joiner)) return recruits.containsKey(host.getUniqueId())
                ? "§cすでに参加中です（相手が参加するのを待っています）。"
                : "§c自分の募集には参加できません。";
        Recruit r = recruits.get(host.getUniqueId());
        if (r == null) return "§c" + host.getName() + " は募集していません（期限切れの可能性があります）。";
        if (r.target() != null && !r.target().equals(joiner.getUniqueId())) return "§cその募集は招待された人しか参加できません。";
        if (isBusy(joiner.getUniqueId())) return "§cあなたは既に募集中かゲーム中です。";

        // 賭け金：2人から先に預かる（勝者に2人分、勝敗なしなら返金）
        long bet = r.bet();
        if (bet > 0) {
            Bank bank = plugin.bank();
            if (!bank.available()) return "§c経済プラグインが見つからないため、賭け金ありのゲームは開始できません。";
            if (!bank.has(joiner, bet)) return "§c所持金が足りません（" + betLabel(bet) + "§c）。";
            if (!bank.has(host, bet)) {
                cancelRecruit(host.getUniqueId());
                host.sendMessage(Items.text(Msg.PREFIX + "§c所持金が賭け金に足りないため、募集を取り消しました。"));
                return "§c" + host.getName() + " の所持金が足りないため、開始できませんでした。";
            }
            if (!bank.withdraw(host, bet)) return "§c賭け金の引き落としに失敗しました。";
            if (!bank.withdraw(joiner, bet)) {
                bank.deposit(host, bet);
                return "§c賭け金の引き落としに失敗しました。";
            }
        }

        cancelRecruit(host.getUniqueId());
        Game game = new Game(plugin, host, joiner, bet);
        games.put(host.getUniqueId(), game);
        games.put(joiner.getUniqueId(), game);
        game.start();
        return null;
    }

    // ── ゲーム ──

    /** Game.finish から呼ばれる */
    void remove(Game game) {
        for (UUID id : game.players()) games.remove(id, game);
    }

    /** 全ゲームを勝敗なしで終了（賭け金は返金）し、募集もすべて取り消す。/hb off とプラグイン停止時 */
    void stopAll(String reason) {
        for (Game g : new ArrayList<>(games.values())) g.finish(-1, reason);
        for (Recruit r : recruits.values()) {
            r.expiry().cancel();
            Player p = Bukkit.getPlayer(r.host());
            if (p != null) p.sendMessage(Items.text(Msg.PREFIX + "§7募集は取り消されました。"));
        }
        recruits.clear();
    }

    int gameCount() {
        return new java.util.HashSet<>(games.values()).size();
    }
}

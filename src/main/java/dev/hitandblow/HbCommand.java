package dev.hitandblow;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /hb（別名 /hitblow） */
public final class HbCommand implements TabExecutor {
    private static final String ADMIN = "hitandblow.admin";
    private final HitAndBlowPlugin plugin;

    HbCommand(HitAndBlowPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "start" -> start(sender, args);
            case "invite" -> invite(sender, args);
            case "join" -> join(sender, args);
            case "open" -> open(sender);
            case "list" -> list(sender);
            case "end" -> end(sender, args);
            case "reload" -> reload(sender);
            case "maxbet" -> maxBet(sender, args);
            case "on" -> setAccepting(sender, true);
            case "off" -> setAccepting(sender, false);
            default -> help(sender);
        }
        return true;
    }

    // ── 全員 ──

    /** /hb start [金額] */
    private void start(CommandSender sender, String[] args) {
        Player p = requirePlayer(sender);
        if (p == null || !canRecruit(p)) return;
        Long bet = parseBet(p, args.length >= 2 ? args[1] : null);
        if (bet == null) return;
        plugin.games().recruit(p, null, bet);
    }

    /** /hb invite <player> [金額] */
    private void invite(CommandSender sender, String[] args) {
        Player p = requirePlayer(sender);
        if (p == null) return;
        if (args.length < 2) { send(sender, "§c使い方: /hb invite <プレイヤー> [金額]"); return; }
        if (!canRecruit(p)) return;
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { send(sender, "§cそのプレイヤーはオンラインではありません。"); return; }
        if (target.equals(p)) { send(sender, "§c自分は招待できません。"); return; }
        if (plugin.games().gameOf(target.getUniqueId()) != null) { send(sender, "§c" + target.getName() + " はゲーム中です。"); return; }
        Long bet = parseBet(p, args.length >= 3 ? args[2] : null);
        if (bet == null) return;
        plugin.games().recruit(p, target, bet);
    }

    private void join(CommandSender sender, String[] args) {
        Player p = requirePlayer(sender);
        if (p == null) return;
        if (args.length < 2) { send(sender, "§c使い方: /hb join <プレイヤー>"); return; }
        String error = plugin.games().join(p, args[1]);
        if (error != null) send(sender, error);
    }

    private void open(CommandSender sender) {
        Player p = requirePlayer(sender);
        if (p == null) return;
        Game game = plugin.games().gameOf(p.getUniqueId());
        if (game == null) { send(sender, "§c参加中のゲームはありません。"); return; }
        p.openInventory(game.view(game.indexOf(p.getUniqueId())).getInventory());
    }

    private void list(CommandSender sender) {
        if (!(sender instanceof Player p)) { send(sender, "§cプレイヤーのみ実行できます。"); return; }
        List<GameManager.Recruit> list = plugin.games().joinableFor(p.getUniqueId());
        if (list.isEmpty()) { send(sender, "§7参加できる募集はありません。"); return; }
        send(sender, "§f参加できる募集（" + list.size() + "件）:");
        for (GameManager.Recruit r : list) {
            String kind = r.target() == null ? "§7全体" : "§d招待";
            Component line = Items.text(" §e" + r.hostName() + " " + kind + " " + GameManager.betLabel(r.bet()) + " §7（残り" + GameManager.secondsLeft(r) + "秒） ")
                    .append(GameManager.joinButton(r.hostName()));
            p.sendMessage(line);
        }
    }

    // ── OP ──

    private void end(CommandSender sender, String[] args) {
        if (!isAdmin(sender)) return;
        if (args.length < 2) { send(sender, "§c使い方: /hb end <プレイヤー>"); return; }
        Player target = Bukkit.getPlayerExact(args[1]);
        Game game = target == null ? null : plugin.games().gameOf(target.getUniqueId());
        if (game == null) { send(sender, "§cそのプレイヤーはゲーム中ではありません。"); return; }
        game.finish(-1, "§c管理者によって強制終了されました。");
        send(sender, "§f" + game.name(0) + " vs " + game.name(1) + " のゲームを強制終了しました。");
    }

    private void reload(CommandSender sender) {
        if (!isAdmin(sender)) return;
        plugin.loadSettings();
        send(sender, "§aconfig.yml を再読み込みしました（手番 " + plugin.turnSeconds() + "秒 / 募集 " + plugin.recruitSeconds()
                + "秒 / 最高賭け金 " + Bank.yen(plugin.maxBet()) + " / " + (plugin.accepting() ? "受付中" : "停止中") + "）。");
    }

    /** /hb maxbet [金額]：省略すると今の値を表示 */
    private void maxBet(CommandSender sender, String[] args) {
        if (!isAdmin(sender)) return;
        if (args.length < 2) {
            send(sender, "§f現在の最高賭け金: §6" + Bank.yen(plugin.maxBet()) + " §7（変更: /hb maxbet <金額>）");
            return;
        }
        long value;
        try {
            value = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            send(sender, "§c金額は0以上の整数で指定してください。");
            return;
        }
        if (value < 0) { send(sender, "§c金額は0以上の整数で指定してください。"); return; }
        plugin.setMaxBet(value);
        send(sender, "§a最高賭け金を §6" + Bank.yen(value) + " §aに変更しました（出ている募集・進行中のゲームはそのまま）。");
    }

    /** /hb on・/hb off */
    private void setAccepting(CommandSender sender, boolean on) {
        if (!isAdmin(sender)) return;
        if (plugin.accepting() == on) {
            send(sender, "§7既に" + (on ? "受付中" : "停止中") + "です。");
            return;
        }
        plugin.setAccepting(on);
        if (on) {
            send(sender, "§aヒット＆ブローの受付を再開しました。");
        } else {
            int count = plugin.games().gameCount();
            plugin.games().stopAll("§c管理者によってヒット＆ブローが停止されたため、終了します。");
            send(sender, "§cヒット＆ブローを停止しました（終了したゲーム " + count + "件。新しい募集はできません）。");
        }
    }

    private void help(CommandSender sender) {
        send(sender, "§fコマンド一覧:");
        sender.sendMessage(Items.text(" §e/hb start [金額] §7- 全体向けに募集する（金額を省略すると0円）"));
        sender.sendMessage(Items.text(" §e/hb invite <player> [金額] §7- 特定の人向けに募集する"));
        sender.sendMessage(Items.text(" §e/hb join <player> §7- プレイヤーの募集に参加する"));
        sender.sendMessage(Items.text(" §e/hb open §7- 参加中のゲーム画面を開く"));
        sender.sendMessage(Items.text(" §e/hb list §7- 募集中のプレイヤーを表示する"));
        sender.sendMessage(Items.text(" §e/hb help §7- コマンド一覧を表示する"));
        sender.sendMessage(Items.text(" §7賭け金は2人から預かり、勝った人が2人分を受け取ります（0〜" + Bank.yen(plugin.maxBet()) + "）。"));
        if (sender.hasPermission(ADMIN)) {
            sender.sendMessage(Items.text(" §c/hb end <player> §7- そのプレイヤーのゲームを強制終了する（賭け金は返金）"));
            sender.sendMessage(Items.text(" §c/hb maxbet <金額> §7- 最高賭け金を変更する"));
            sender.sendMessage(Items.text(" §c/hb on §7- ゲームを開始できるようにする"));
            sender.sendMessage(Items.text(" §c/hb off §7- ゲームを停止する（進行中のゲームは終了し、新しい募集もできなくなる）"));
            sender.sendMessage(Items.text(" §c/hb reload §7- config.yml を再読み込みする"));
        }
    }

    // ── 補助 ──

    /**
     * 賭け金を読む。省略なら0。範囲外・所持金不足・経済プラグインなしならメッセージを出して null
     */
    private Long parseBet(Player p, String arg) {
        if (arg == null) return 0L;
        long bet;
        try {
            bet = Long.parseLong(arg);
        } catch (NumberFormatException e) {
            send(p, "§c金額は整数で指定してください（0〜" + Bank.yen(plugin.maxBet()) + "）。");
            return null;
        }
        if (bet < 0 || bet > plugin.maxBet()) {
            send(p, "§c金額は 0〜" + Bank.yen(plugin.maxBet()) + " で指定してください。");
            return null;
        }
        if (bet > 0) {
            if (!plugin.bank().available()) { send(p, "§c経済プラグインが見つからないため、賭け金は指定できません。"); return null; }
            if (!plugin.bank().has(p, bet)) { send(p, "§c所持金が足りません（賭け金 " + Bank.yen(bet) + "）。"); return null; }
        }
        return bet;
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player p) return p;
        send(sender, "§cプレイヤーのみ実行できます。");
        return null;
    }

    private boolean isAdmin(CommandSender sender) {
        if (sender.hasPermission(ADMIN)) return true;
        send(sender, "§c権限がありません。");
        return false;
    }

    /** 募集を出せるか（受付中・ゲーム中でない・募集中でない） */
    private boolean canRecruit(Player p) {
        if (!plugin.accepting()) { send(p, "§c現在ヒット＆ブローは停止中です。"); return false; }
        if (plugin.games().gameOf(p.getUniqueId()) != null) { send(p, "§cゲーム中です。"); return false; }
        if (plugin.games().isBusy(p.getUniqueId())) { send(p, "§c既に募集中です（期限が切れるまで待ってください）。"); return false; }
        return true;
    }

    private static void send(CommandSender sender, String legacy) {
        sender.sendMessage(Items.text(Msg.PREFIX + legacy));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        String sub = args.length >= 1 ? args[0].toLowerCase(Locale.ROOT) : "";
        boolean admin = sender.hasPermission(ADMIN);
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("start", "invite", "join", "open", "list", "help"));
            if (admin) subs.addAll(List.of("end", "maxbet", "on", "off", "reload"));
            out.addAll(subs);
        } else if (args.length == 2) {
            if (sub.equals("join") && sender instanceof Player p) {
                for (GameManager.Recruit r : plugin.games().joinableFor(p.getUniqueId())) out.add(r.hostName());
            } else if (sub.equals("invite") || (sub.equals("end") && admin)) {
                for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            } else if (sub.equals("start")) {
                out.add("0");
            } else if (sub.equals("maxbet") && admin) {
                out.add(String.valueOf(plugin.maxBet()));
            }
        } else if (args.length == 3 && sub.equals("invite")) {
            out.add("0");
        }
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}

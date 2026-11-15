package dev.hitandblow;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 1試合分の状態と進行。
 *
 * GUI（ラージチェスト 9列×6行、スロット = 行*9 + 列、0始まり）
 * <pre>
 * 列→  0     1     2     3     4   | 5   6     7     8
 * 行0 [今回][1前][2前][3前][4前]  | ▦  頭①   頭②   時計
 * 行1 [今回][ 〃][ 〃][ 〃][ 〃]  | ▦  ★/✕  ★/✕  賞金
 * 行2 [今回][ 〃][ 〃][ 〃][ 〃]  | ▦  ▦    ▦    ▦
 * 行3 [今回][ 〃][ 〃][ 〃][ 〃]  | ▦  赤    青    黄
 * 行4  ▦    ▦    ▦    ▦    ▦     | ▦  緑    白    黒
 * 行5 [  ] [紙] [紙] [紙] [紙]    | ▦  ▦   クリア  決定
 * </pre>
 */
public final class Game {
    // ── スロット番号 ──
    static final int SLOT_HEAD_1 = 6, SLOT_HEAD_2 = 7, SLOT_CLOCK = 8;
    static final int SLOT_TURN_1 = 15, SLOT_TURN_2 = 16, SLOT_PRIZE = 17;
    static final int SLOT_CLEAR = 52, SLOT_SUBMIT = 53;
    static final int[] SEPARATORS = {5, 14, 23, 32, 41, 50, 24, 25, 26, 36, 37, 38, 39, 40, 51};
    static final Map<Integer, HbColor> PANEL = Map.of(
            33, HbColor.RED, 34, HbColor.BLUE, 35, HbColor.YELLOW,
            42, HbColor.GREEN, 43, HbColor.WHITE, 44, HbColor.BLACK);
    static final int HISTORY_COLUMNS = 4;   // 左上に表示する過去の回答の数
    static final int CODE_LENGTH = 4;

    private final HitAndBlowPlugin plugin;
    private final UUID[] players = new UUID[2];
    private final String[] names = new String[2];
    private final GameView[] views = new GameView[2];
    private final HbColor[] secret = new HbColor[CODE_LENGTH];
    private final List<HbColor> input = new ArrayList<>();
    private final List<Guess> history = new ArrayList<>();   // 古い順
    private int turn;          // 手番のプレイヤー番号（0 or 1）
    private int remaining;     // 手番の残り秒数
    private boolean finished;
    private BukkitTask timer;
    // ── 判定演出（決定してから次の手番までの間）──
    static final int SLOT_PEG_FIRST = 36;        // 行4の列0〜3に結果のピンを並べる
    static final long PEG_FIRST_DELAY = 8L;      // 決定 → 1つ目のピン
    static final long PEG_INTERVAL = 6L;         // ピン同士の間隔（0.3秒）
    static final long AFTER_REVEAL_DELAY = 40L;  // 全部出てから相手の手番まで（2秒）
    static final long WIN_DELAY = 10L;           // 4ヒットのとき、全部出てから終了まで
    private Guess pending;     // 判定演出中の回答（null なら演出中ではない）
    private int pegsShown;
    private BukkitTask revealTask;

    private final long bet;    // 1人あたりの賭け金（開始時に2人から預かり済み）
    private java.nio.file.Path ledgerFile;   // 未精算の記録（賭け金ありのときだけ）

    Game(HitAndBlowPlugin plugin, Player host, Player guest, long bet) {
        this.plugin = plugin;
        this.bet = bet;
        players[0] = host.getUniqueId();
        players[1] = guest.getUniqueId();
        names[0] = host.getName();
        names[1] = guest.getName();
        views[0] = new GameView(this, 0);
        views[1] = new GameView(this, 1);
        HbColor[] all = HbColor.values();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < CODE_LENGTH; i++) secret[i] = all[rnd.nextInt(all.length)];
        turn = rnd.nextInt(2);
    }

    // ===================================================================
    //  進行
    // ===================================================================

    void start() {
        if (bet > 0) ledgerFile = plugin.ledger().open(names, players, bet);
        sendBoth(Msg.PREFIX + "§e" + names[0] + " §fvs §e" + names[1] + " §fのヒット＆ブローを開始！");
        sendBoth(Msg.PREFIX + "§f先手：§e" + names[turn]);
        playBoth(Sound.ENTITY_PLAYER_LEVELUP, 1f);
        for (int i = 0; i < 2; i++) {
            Player p = player(i);
            if (p != null) p.openInventory(views[i].getInventory());
        }
        timer = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        beginTurn();
    }

    private void beginTurn() {
        remaining = plugin.turnSeconds();
        input.clear();
        renderAll();
        Player p = player(turn);
        if (p != null) {
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            p.sendMessage(Items.text(Msg.PREFIX + "§aあなたの手番です。"));
        }
    }

    /** 1秒ごとに呼ばれる */
    private void tick() {
        if (finished || pending != null) return;   // 判定演出中は時間を止める
        remaining--;
        if (remaining <= 0) {
            sendBoth(Msg.PREFIX + "§e" + names[turn] + " §7の時間切れ。手番をスキップします。");
            playBoth(Sound.BLOCK_NOTE_BLOCK_BASS, 1f);
            turn = 1 - turn;
            beginTurn();
            return;
        }
        for (GameView v : views) renderClock(v);
        Player p = player(turn);
        if (p != null) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, remaining <= 10 ? 2f : 1f);
        }
    }

    /** GUI がクリックされた（イベントは呼び出し側でキャンセル済み） */
    void handleClick(Player p, GameView view, int slot) {
        if (finished) return;
        boolean panelSlot = PANEL.containsKey(slot) || slot == SLOT_CLEAR || slot == SLOT_SUBMIT;
        if (!panelSlot) return;
        if (view.viewer != turn || pending != null) {
            deny(p);
            return;
        }
        HbColor color = PANEL.get(slot);
        if (color != null) {
            if (input.size() >= CODE_LENGTH) { deny(p); return; }
            input.add(color);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1f);
            for (GameView v : views) renderInput(v);
        } else if (slot == SLOT_CLEAR) {
            if (input.isEmpty()) { deny(p); return; }
            input.clear();
            p.playSound(p.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_OFF, 1f, 1f);
            for (GameView v : views) renderInput(v);
        } else {
            if (input.size() < CODE_LENGTH) { deny(p); return; }
            submit();
        }
    }

    private void submit() {
        HbColor[] guess = input.toArray(new HbColor[0]);
        int[] r = Judge.judge(secret, guess);
        pending = new Guess(history.size() + 1, names[turn], guess, r[0], r[1]);
        pegsShown = 0;
        input.clear();
        renderAll();   // 操作板を隠し、列0に決定した回答を出す
        playBoth(Sound.BLOCK_NOTE_BLOCK_PLING, 1f);
        revealTask = Bukkit.getScheduler().runTaskTimer(plugin, this::revealStep, PEG_FIRST_DELAY, PEG_INTERVAL);
    }

    /** 判定演出：ピンを1つずつ出す（ヒット→ブロー→なし の順） */
    private void revealStep() {
        if (finished) { revealTask.cancel(); return; }
        int i = pegsShown++;
        if (i < pending.hits()) {
            playBoth(Sound.BLOCK_NOTE_BLOCK_BELL, 1.2f + 0.2f * i);
        } else if (i < pending.hits() + pending.blows()) {
            playBoth(Sound.BLOCK_NOTE_BLOCK_CHIME, 1f + 0.2f * i);
        } else {
            playBoth(Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1f);
        }
        for (GameView v : views) renderPegs(v);
        if (pegsShown < CODE_LENGTH) return;

        revealTask.cancel();
        Guess g = pending;
        sendBoth(Msg.PREFIX + "§f" + g.playerName() + ": " + HbColor.join(g.colors())
                + " §f→ §e" + g.hits() + "ヒット §b" + g.blows() + "ブロー");
        boolean win = g.hits() == CODE_LENGTH;
        Bukkit.getScheduler().runTaskLater(plugin, () -> endReveal(win), win ? WIN_DELAY : AFTER_REVEAL_DELAY);
    }

    private void endReveal(boolean win) {
        if (finished) return;
        history.add(pending);
        pending = null;
        if (win) {
            finish(turn, null);
            return;
        }
        turn = 1 - turn;
        beginTurn();
    }

    /**
     * 試合終了。
     * @param winner 勝者の番号。-1 なら勝敗なし
     * @param reason 追加メッセージ（null 可）
     */
    void finish(int winner, String reason) {
        if (finished) return;
        finished = true;
        if (timer != null) timer.cancel();
        if (revealTask != null) revealTask.cancel();
        pending = null;   // 演出の途中で終わったら、その回答は盤面に残さない
        plugin.games().remove(this);

        if (reason != null) sendBoth(Msg.PREFIX + reason);
        sendBoth(Msg.PREFIX + "§f答え: " + HbColor.join(secret));
        if (winner >= 0) {
            sendBoth(Msg.PREFIX + "§6§l" + names[winner] + " の勝ち！ §7（" + history.size() + "手）");
            Player w = player(winner);
            if (w != null) w.playSound(w.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            Player l = player(1 - winner);
            if (l != null) l.playSound(l.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.4f, 1f);
        } else {
            sendBoth(Msg.PREFIX + "§7勝敗なしで終了しました。");
        }
        // 賭け金の精算（オフラインの相手にも入金できる）
        if (bet > 0) {
            boolean ok = true;
            if (winner >= 0) {
                if (!plugin.bank().deposit(Bukkit.getOfflinePlayer(players[winner]), bet * 2)) {
                    ok = false;
                    plugin.ledger().markFailed(ledgerFile, "配当 " + names[winner] + " (" + players[winner] + ") に " + Bank.yen(bet * 2));
                }
                Player w = player(winner);   // 勝った人にだけ知らせる
                if (w != null) w.sendMessage(Items.text(Msg.PREFIX + "§6" + Bank.yen(bet * 2) + " §f獲得しました。"));
            } else {
                for (int i = 0; i < 2; i++) {
                    if (!plugin.bank().deposit(Bukkit.getOfflinePlayer(players[i]), bet)) {
                        ok = false;
                        plugin.ledger().markFailed(ledgerFile, "返金 " + names[i] + " (" + players[i] + ") に " + Bank.yen(bet));
                    }
                }
                sendBoth(Msg.PREFIX + "§7賭け金 " + Bank.yen(bet) + " を2人に返金しました。");
            }
            if (ok) plugin.ledger().close(ledgerFile);   // 精算完了 → 記録を消す（失敗したら残す）
        }
        renderAll();
        // 最終盤面を3秒見せてから閉じる（プラグイン停止中はタスクを登録できないので即閉じる）
        if (plugin.isEnabled()) Bukkit.getScheduler().runTaskLater(plugin, this::closeViews, 60L);
        else closeViews();
    }

    private void closeViews() {
        for (int i = 0; i < 2; i++) {
            Player p = player(i);
            if (p != null && p.getOpenInventory().getTopInventory().getHolder() == views[i]) {
                p.closeInventory();
            }
        }
    }

    // ===================================================================
    //  画面描画
    // ===================================================================

    private void renderAll() {
        for (GameView v : views) render(v);
    }

    private void render(GameView v) {
        Inventory inv = v.getInventory();
        inv.clear();
        for (int s : SEPARATORS) inv.setItem(s, Items.pane());
        renderInput(v);
        renderPegs(v);
        renderHistory(inv);
        // 右上: 頭と手番表示
        for (int i = 0; i < 2; i++) {
            inv.setItem(i == 0 ? SLOT_HEAD_1 : SLOT_HEAD_2, i == v.viewer
                    ? Items.head(Bukkit.getOfflinePlayer(players[i]), "§f§l" + names[i], "§a(あなた)")
                    : Items.head(Bukkit.getOfflinePlayer(players[i]), "§f§l" + names[i]));
            boolean myTurn = !finished && turn == i;
            inv.setItem(i == 0 ? SLOT_TURN_1 : SLOT_TURN_2, myTurn
                    ? Items.of(Material.NETHER_STAR, "§a§l手番", "§7" + names[i] + " が回答中")
                    : Items.of(Material.BARRIER, "§7待機中"));
        }
        renderClock(v);
        inv.setItem(SLOT_PRIZE, Items.of(Material.GOLD_BLOCK, "§6賞金：" + Bank.yen(bet * 2)));
        // 右下: 操作板（自分の手番のときだけ）
        boolean showPanel = !finished && pending == null && v.viewer == turn;
        for (Map.Entry<Integer, HbColor> e : PANEL.entrySet()) {
            HbColor c = e.getValue();
            inv.setItem(e.getKey(), showPanel ? Items.of(c.concrete, c.colored(), "§7クリックで選択") : Items.pane());
        }
        inv.setItem(SLOT_CLEAR, showPanel ? Items.of(Material.BARRIER, "§c選択をクリア") : Items.pane());
        inv.setItem(SLOT_SUBMIT, showPanel ? Items.of(Material.END_CRYSTAL, "§d§l回答を決定", "§74つ選ぶと決定できます") : Items.pane());
    }

    /** 左上の列0：入力中の回答（終了後は答え） */
    private void renderInput(GameView v) {
        Inventory inv = v.getInventory();
        for (int row = 0; row < CODE_LENGTH; row++) {
            int slot = row * 9;
            if (finished) {
                HbColor c = secret[row];
                inv.setItem(slot, Items.of(c.concrete, "§6答え " + (row + 1) + "番目: " + c.colored()));
            } else if (pending != null) {
                HbColor c = pending.colors()[row];
                inv.setItem(slot, Items.of(c.concrete, (row + 1) + "番目: " + c.colored(), "§7" + pending.playerName() + " の回答（判定中）"));
            } else if (row < input.size()) {
                HbColor c = input.get(row);
                inv.setItem(slot, Items.of(c.concrete, (row + 1) + "番目: " + c.colored(), "§7" + names[turn] + " が入力中"));
            } else {
                inv.setItem(slot, null);
            }
        }
    }

    /** 行4の列0〜3：判定演出のピン（黄＝ヒット、水色＝ブロー、白＝なし）。演出中以外は仕切り */
    private void renderPegs(GameView v) {
        Inventory inv = v.getInventory();
        for (int i = 0; i < CODE_LENGTH; i++) {
            int slot = SLOT_PEG_FIRST + i;
            if (pending == null || i >= pegsShown) {
                inv.setItem(slot, Items.pane());
            } else if (i < pending.hits()) {
                inv.setItem(slot, Items.of(Material.YELLOW_STAINED_GLASS_PANE, "§e§lヒット"));
            } else if (i < pending.hits() + pending.blows()) {
                inv.setItem(slot, Items.of(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "§b§lブロー"));
            } else {
                inv.setItem(slot, Items.of(Material.WHITE_STAINED_GLASS_PANE, "§fなし"));
            }
        }
    }

    /** 左上の列1〜4（ガラス）と左下の紙：新しい回答ほど左 */
    private void renderHistory(Inventory inv) {
        for (int k = 0; k < HISTORY_COLUMNS && k < history.size(); k++) {
            Guess g = history.get(history.size() - 1 - k);
            int col = 1 + k;
            for (int row = 0; row < CODE_LENGTH; row++) {
                HbColor c = g.colors()[row];
                inv.setItem(row * 9 + col, Items.of(c.glass, (row + 1) + "番目: " + c.colored(),
                        "§7" + g.number() + "手目: " + g.playerName()));
            }
            inv.setItem(45 + col, Items.of(Material.PAPER, "§e" + g.hits() + "ヒット §b" + g.blows() + "ブロー",
                    "§7" + g.number() + "手目: " + g.playerName(), HbColor.join(g.colors())));
        }
    }

    /** 右上の時計：名前と個数で残り秒数 */
    private void renderClock(GameView v) {
        if (finished) {
            v.getInventory().setItem(SLOT_CLOCK, Items.of(Material.CLOCK, "§7終了"));
            return;
        }
        if (pending != null) {
            v.getInventory().setItem(SLOT_CLOCK, Items.of(Material.CLOCK, remaining, "§7判定中…"));
            return;
        }
        String sec =(remaining <= 10 ? "§c" : "§e") + "残り" + remaining + "秒";
        String name = v.viewer == turn ? sec : "§7相手の手番 " + sec;
        v.getInventory().setItem(SLOT_CLOCK, Items.of(Material.CLOCK, remaining, name));
    }

    // ===================================================================
    //  ユーティリティ
    // ===================================================================

    UUID[] players() {
        return players;
    }

    int indexOf(UUID id) {
        return players[0].equals(id) ? 0 : players[1].equals(id) ? 1 : -1;
    }

    String name(int i) {
        return names[i];
    }

    GameView view(int i) {
        return views[i];
    }

    private Player player(int i) {
        return Bukkit.getPlayer(players[i]);
    }

    private void deny(Player p) {
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
    }

    private void sendBoth(String legacy) {
        for (int i = 0; i < 2; i++) {
            Player p = player(i);
            if (p != null) p.sendMessage(Items.text(legacy));
        }
    }

    private void playBoth(Sound sound, float pitch) {
        for (int i = 0; i < 2; i++) {
            Player p = player(i);
            if (p != null) p.playSound(p.getLocation(), sound, 1f, pitch);
        }
    }
}

package dev.hitandblow;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** プレイヤー1人分のゲーム画面（ラージチェスト）。holder でゲームと視点を識別する。 */
public final class GameView implements InventoryHolder {
    public final Game game;
    /** この画面を見ているプレイヤーの番号（0 or 1） */
    public final int viewer;
    private final Inventory inventory;
    /** true なら履歴画面を表示中（同じインベントリの中身を入れ替えて表示する） */
    boolean history;
    /** 履歴画面で、新しい側から何件ずらして表示しているか */
    int historyOffset;

    GameView(Game game, int viewer) {
        this.game = game;
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, 54, Items.text("§8ヒット＆ブロー"));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

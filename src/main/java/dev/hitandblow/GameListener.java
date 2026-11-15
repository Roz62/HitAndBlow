package dev.hitandblow;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** GUI 操作とログアウトの処理 */
public final class GameListener implements Listener {
    private final HitAndBlowPlugin plugin;

    GameListener(HitAndBlowPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof GameView view)) return;
        e.setCancelled(true);   // GUI を開いている間はアイテムを一切動かせない
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (!e.isLeftClick() && !e.isRightClick()) return;
        view.game.handleClick(p, view, e.getRawSlot());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof GameView) e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        plugin.games().cancelRecruit(p.getUniqueId());
        Game game = plugin.games().gameOf(p.getUniqueId());
        if (game != null) {
            int idx = game.indexOf(p.getUniqueId());
            game.finish(1 - idx, "§e" + p.getName() + " §7がログアウトしたため、放棄負けになりました。");
        }
    }
}

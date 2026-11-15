package dev.hitandblow;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/** GUI 用アイテムの生成ヘルパー。名前・説明文は § の色コードで書ける。 */
public final class Items {
    private Items() {}

    /** § 色コード付き文字列 → Component（斜体なし） */
    public static Component text(String legacy) {
        return LegacyComponentSerializer.legacySection().deserialize(legacy)
                .decoration(TextDecoration.ITALIC, false);
    }

    public static ItemStack of(Material material, int amount, String name, String... lore) {
        ItemStack item = new ItemStack(material, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        apply(meta, name, lore);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack of(Material material, String name, String... lore) {
        return of(material, 1, name, lore);
    }

    public static ItemStack head(OfflinePlayer owner, String name, String... lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(owner);
        apply(meta, name, lore);
        item.setItemMeta(meta);
        return item;
    }

    /** 仕切り（黒色のガラス板・名前なし） */
    public static ItemStack pane() {
        return of(Material.BLACK_STAINED_GLASS_PANE, " ");
    }

    private static void apply(ItemMeta meta, String name, String... lore) {
        meta.displayName(text(name));
        if (lore.length > 0) {
            List<Component> lines = new ArrayList<>();
            for (String l : lore) lines.add(text(l));
            meta.lore(lines);
        }
    }
}

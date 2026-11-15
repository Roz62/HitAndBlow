package dev.hitandblow;

import org.bukkit.Material;

/** ゲームで使う6色。黄緑は「緑」、灰は「黒」として扱う。 */
public enum HbColor {
    RED("赤", "§c", Material.RED_CONCRETE, Material.RED_STAINED_GLASS),
    BLUE("青", "§9", Material.BLUE_CONCRETE, Material.BLUE_STAINED_GLASS),
    YELLOW("黄", "§e", Material.YELLOW_CONCRETE, Material.YELLOW_STAINED_GLASS),
    GREEN("緑", "§a", Material.LIME_CONCRETE, Material.LIME_STAINED_GLASS),
    WHITE("白", "§f", Material.WHITE_CONCRETE, Material.WHITE_STAINED_GLASS),
    BLACK("黒", "§8", Material.GRAY_CONCRETE, Material.GRAY_STAINED_GLASS);

    public final String label;
    public final String chat;
    public final Material concrete;
    public final Material glass;

    HbColor(String label, String chat, Material concrete, Material glass) {
        this.label = label;
        this.chat = chat;
        this.concrete = concrete;
        this.glass = glass;
    }

    /** チャット用の色付き表記（例: §c赤） */
    public String colored() {
        return chat + label;
    }

    /** 色の並びをチャット用に連結（例: §c赤 §9青 …） */
    public static String join(HbColor[] colors) {
        StringBuilder sb = new StringBuilder();
        for (HbColor c : colors) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(c.colored());
        }
        return sb.toString();
    }
}

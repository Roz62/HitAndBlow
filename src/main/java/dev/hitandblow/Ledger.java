package dev.hitandblow;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.logging.Level;

/**
 * 未精算の記録（補填用の証跡）。
 * 賭け金ありの試合が始まったら plugins/HitAndBlow/unsettled/ に1試合1ファイルで書き、
 * 精算（配当・返金）が終わったら消す。
 * → クラッシュなどで正常に終わらなかった試合のファイルだけが残る。
 */
final class Ledger {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter TEXT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final HitAndBlowPlugin plugin;

    Ledger(HitAndBlowPlugin plugin) {
        this.plugin = plugin;
    }

    private File dir() {
        return new File(plugin.getDataFolder(), "unsettled");
    }

    /** 起動時：残っている記録があればコンソールに警告する */
    void warnLeftovers() {
        File[] files = dir().listFiles((d, n) -> n.endsWith(".txt"));
        if (files == null || files.length == 0) return;
        plugin.getLogger().warning("正常に精算されなかった試合の記録が " + files.length + " 件あります（補填の対象）: " + dir().getPath());
        for (File f : files) plugin.getLogger().warning("  - " + f.getName());
    }

    /** 試合開始（賭け金を預かった直後）。書けなかったら null */
    Path open(String[] names, UUID[] ids, long bet) {
        LocalDateTime now = LocalDateTime.now();
        Path file = dir().toPath().resolve(now.format(FILE_TIME) + "_" + names[0] + "_vs_" + names[1] + ".txt");
        String text = String.join(System.lineSeparator(),
                "# ヒット＆ブロー 未精算の記録",
                "# このファイルが残っている = 賭け金を預かったまま正常に精算されずに終わった試合（補填の対象）",
                "# 補填が済んだら、このファイルは削除してよい",
                "開始: " + now.format(TEXT_TIME),
                "賭け金: " + Bank.yen(bet) + "（1人あたり。2人から預かり済み）",
                "プレイヤー1: " + names[0] + " (" + ids[0] + ")",
                "プレイヤー2: " + names[1] + " (" + ids[1] + ")",
                "");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text, StandardCharsets.UTF_8);
            return file;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "未精算の記録を書けませんでした: " + text, e);
            return null;
        }
    }

    /** 精算完了：記録を消す */
    void close(Path file) {
        if (file == null) return;
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "未精算の記録を削除できませんでした: " + file, e);
        }
    }

    /** 精算の一部に失敗：消さずに、失敗した内容を追記して残す */
    void markFailed(Path file, String line) {
        if (file == null) return;
        try {
            Files.writeString(file, "入金失敗: " + LocalDateTime.now().format(TEXT_TIME) + " " + line + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "未精算の記録に追記できませんでした: " + file, e);
        }
    }
}

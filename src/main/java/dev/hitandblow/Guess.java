package dev.hitandblow;

/** 1回分の回答と結果。number は通し番号（1手目, 2手目…）。 */
public record Guess(int number, String playerName, HbColor[] colors, int hits, int blows) {}

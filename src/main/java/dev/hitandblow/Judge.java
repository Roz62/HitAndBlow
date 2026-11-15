package dev.hitandblow;

/** ヒット・ブローの判定（色の重複あり対応）。 */
public final class Judge {
    private Judge() {}

    /** @return {ヒット数, ブロー数} */
    public static int[] judge(HbColor[] secret, HbColor[] guess) {
        int hits = 0;
        int[] secretCount = new int[HbColor.values().length];
        int[] guessCount = new int[HbColor.values().length];
        for (int i = 0; i < secret.length; i++) {
            if (secret[i] == guess[i]) hits++;
            secretCount[secret[i].ordinal()]++;
            guessCount[guess[i].ordinal()]++;
        }
        // 色ごとに「少ないほうの個数」が一致数。そこからヒットを引いた残りがブロー
        int common = 0;
        for (int c = 0; c < secretCount.length; c++) {
            common += Math.min(secretCount[c], guessCount[c]);
        }
        return new int[]{hits, common - hits};
    }
}

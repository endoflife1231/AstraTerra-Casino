package dev.astraterra.casino;

import java.util.*;

final class PokerHand {
    private PokerHand() {}

    static long best(List<Integer> cards) {
        if (cards.size() < 5) return 0;
        long best = 0;
        int n = cards.size();
        for (int a = 0; a < n - 4; a++)
            for (int b = a + 1; b < n - 3; b++)
                for (int c = b + 1; c < n - 2; c++)
                    for (int d = c + 1; d < n - 1; d++)
                        for (int e = d + 1; e < n; e++)
                            best = Math.max(best, score(cards.get(a), cards.get(b), cards.get(c), cards.get(d), cards.get(e)));
        return best;
    }

    static String name(long score) {
        return switch ((int) (score >>> 28)) {
            case 8 -> "стрит-флеш";
            case 7 -> "каре";
            case 6 -> "фулл-хаус";
            case 5 -> "флеш";
            case 4 -> "стрит";
            case 3 -> "сет";
            case 2 -> "две пары";
            case 1 -> "пара";
            default -> "старшая карта";
        };
    }

    private static long score(int... cards) {
        int[] ranks = new int[5];
        int[] counts = new int[15];
        int suit = Cards.pokerSuit(cards[0]);
        boolean flush = true;
        for (int i = 0; i < 5; i++) {
            ranks[i] = Cards.pokerRank(cards[i]);
            counts[ranks[i]]++;
            if (Cards.pokerSuit(cards[i]) != suit) flush = false;
        }
        Arrays.sort(ranks);
        int straightHigh = straightHigh(counts);
        if (flush && straightHigh > 0) return pack(8, straightHigh);
        int four = 0, three = 0;
        List<Integer> pairs = new ArrayList<>(), singles = new ArrayList<>();
        for (int r = 14; r >= 2; r--) {
            if (counts[r] == 4) four = r;
            else if (counts[r] == 3) three = Math.max(three, r);
            else if (counts[r] == 2) pairs.add(r);
            else if (counts[r] == 1) singles.add(r);
        }
        if (four > 0) return pack(7, four, singles.get(0));
        if (three > 0 && !pairs.isEmpty()) return pack(6, three, pairs.get(0));
        if (flush) return pack(5, descending(ranks));
        if (straightHigh > 0) return pack(4, straightHigh);
        if (three > 0) return pack(3, three, singles.get(0), singles.get(1));
        if (pairs.size() >= 2) return pack(2, pairs.get(0), pairs.get(1), singles.get(0));
        if (pairs.size() == 1) return pack(1, pairs.get(0), singles.get(0), singles.get(1), singles.get(2));
        return pack(0, descending(ranks));
    }

    private static int straightHigh(int[] counts) {
        for (int high = 14; high >= 5; high--) {
            boolean ok = true;
            for (int r = high; r > high - 5; r--) if (counts[r] == 0) { ok = false; break; }
            if (ok) return high;
        }
        return counts[14] > 0 && counts[2] > 0 && counts[3] > 0 && counts[4] > 0 && counts[5] > 0 ? 5 : 0;
    }

    private static int[] descending(int[] ranks) {
        int[] out = ranks.clone();
        for (int i = 0; i < out.length / 2; i++) { int t = out[i]; out[i] = out[out.length - 1 - i]; out[out.length - 1 - i] = t; }
        return out;
    }

    private static long pack(int category, int... values) {
        long result = ((long) category) << 28;
        int shift = 24;
        for (int value : values) { result |= ((long) value & 15L) << shift; shift -= 4; if (shift < 0) break; }
        return result;
    }
}

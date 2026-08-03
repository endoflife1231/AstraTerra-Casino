package dev.astraterra.casino;

import java.util.*;

final class CasinoStats {
    private static final Map<String, Object> IDS = new HashMap<>();
    private CasinoStats() {}

    static void registerAll() {
        String[] names = {
            "rounds", "wins", "losses", "wagered_silver", "deposit_silver", "withdraw_silver",
            "blackjack_games", "blackjack_wins", "blackjack_naturals", "blackjack_21",
            "roulette_games", "roulette_wins", "roulette_zero_wins",
            "dice_games", "dice_wins", "dice_seven_wins",
            "wheel_games", "wheel_wins", "wheel_jackpots",
            "streak3", "streak5", "streak10", "profit_silver",
            "poker_games", "poker_wins", "poker_rooms_created", "poker_hands", "poker_hands_won",
            "poker_tournaments_won", "poker_checks", "poker_calls", "poker_raises", "poker_folds",
            "poker_allins", "poker_showdown_wins", "poker_no_showdown_wins", "poker_sidepot_wins",
            "poker_turnover_silver", "poker_largest_pot_silver",
            "durak_games", "durak_wins", "durak_losses"
        };
        try {
            Object fmt = Reflect.staticField("net.minecraft.class_3446", "field_16975");
            for (String n : names) {
                Object id = Reflect.invokeStatic("net.minecraft.class_3468", "method_15021", "astraterra-casino:" + n, fmt);
                IDS.put(n, id);
            }
            System.out.println("[AstraTerra Casino] Registered " + IDS.size() + " quest statistics");
        } catch (Throwable e) {
            throw new IllegalStateException("Could not register casino statistics", e);
        }
    }

    static void add(Object player, String name, int amount) {
        if (amount <= 0) return;
        Object id = IDS.get(name);
        if (id == null) return;
        try {
            Reflect.invoke(player, "method_7339", id, amount);
        } catch (Throwable e) {
            System.err.println("[AstraTerra Casino] Stat failed: " + name);
            e.printStackTrace();
        }
    }

    static void addEach(Iterable<? extends Object> holders, String name, int amount) {
        for (Object holder : holders) {
            try {
                Object player = Reflect.field(holder, "player");
                add(player, name, amount);
            } catch (Throwable ignored) {
                // Only used with internal seat objects; ignore malformed holders.
            }
        }
    }

    static void result(Object p, boolean win, long bet, long profit) {
        add(p, "rounds", 1);
        add(p, win ? "wins" : "losses", 1);
        add(p, "wagered_silver", (int) Math.min(Integer.MAX_VALUE, Math.max(1, bet / 100)));
        if (profit >= 100) add(p, "profit_silver", (int) Math.min(Integer.MAX_VALUE, profit / 100));
        long streak;
        if (win) streak = CasinoData.add(p, "win_streak", 1);
        else { CasinoData.set(p, "win_streak", 0); streak = 0; }
        if (streak >= 3 && !CasinoData.flag(p, "streak3")) { CasinoData.flag(p, "streak3", true); add(p, "streak3", 1); }
        if (streak >= 5 && !CasinoData.flag(p, "streak5")) { CasinoData.flag(p, "streak5", true); add(p, "streak5", 1); }
        if (streak >= 10 && !CasinoData.flag(p, "streak10")) { CasinoData.flag(p, "streak10", true); add(p, "streak10", 1); }
    }
}

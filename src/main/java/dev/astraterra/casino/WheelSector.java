package dev.astraterra.casino;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

final class WheelSector {
    enum Rarity { COMMON, UNCOMMON, RARE, EPIC, JACKPOT }

    static final List<WheelSector> SECTORS = List.of(
        new WheelSector("empty_north", "Пустой сектор", 0, 1, 170, Rarity.COMMON, "wheel_loss"),
        new WheelSector("return", "Возврат ставки", 1, 1, 145, Rarity.COMMON, "wheel_stop"),
        new WheelSector("half", "Половина ставки", 1, 2, 150, Rarity.COMMON, "wheel_loss"),
        new WheelSector("double", "Двойная выплата", 2, 1, 95, Rarity.UNCOMMON, "wheel_win"),
        new WheelSector("empty_east", "Пустой сектор", 0, 1, 170, Rarity.COMMON, "wheel_loss"),
        new WheelSector("one_half", "Полуторная выплата", 3, 2, 115, Rarity.UNCOMMON, "wheel_win"),
        new WheelSector("triple", "Тройная выплата", 3, 1, 48, Rarity.RARE, "wheel_win"),
        new WheelSector("return_south", "Возврат ставки", 1, 1, 145, Rarity.COMMON, "wheel_stop"),
        new WheelSector("empty_west", "Пустой сектор", 0, 1, 170, Rarity.COMMON, "wheel_loss"),
        new WheelSector("five", "Экспедиционный приз ×5", 5, 1, 18, Rarity.EPIC, "wheel_win"),
        new WheelSector("double_west", "Двойная выплата", 2, 1, 95, Rarity.UNCOMMON, "wheel_win"),
        new WheelSector("jackpot", "ДЖЕКПОТ ×10", 10, 1, 4, Rarity.JACKPOT, "wheel_jackpot")
    );

    final String id;
    final String name;
    final int numerator;
    final int denominator;
    final int weight;
    final Rarity rarity;
    final String sound;

    WheelSector(String id, String name, int numerator, int denominator, int weight, Rarity rarity, String sound) {
        this.id = id;
        this.name = name;
        this.numerator = numerator;
        this.denominator = denominator;
        this.weight = weight;
        this.rarity = rarity;
        this.sound = sound;
    }

    static int chooseIndex() {
        int total = 0;
        for (WheelSector sector : SECTORS) total = Math.addExact(total, sector.weight);
        int roll = ThreadLocalRandom.current().nextInt(total);
        for (int i = 0; i < SECTORS.size(); i++) {
            roll -= SECTORS.get(i).weight;
            if (roll < 0) return i;
        }
        return 0;
    }

    long payout(long bet, long maxAmount) {
        if (bet <= 0 || numerator <= 0) return 0;
        long multiplied;
        try { multiplied = Math.multiplyExact(bet, numerator); }
        catch (ArithmeticException overflow) { multiplied = Long.MAX_VALUE; }
        return Math.min(maxAmount, multiplied / denominator);
    }

    String multiplierText() {
        if (denominator == 1) return "×" + numerator;
        if (numerator == 1 && denominator == 2) return "×0.5";
        if (numerator == 3 && denominator == 2) return "×1.5";
        return "×" + numerator + "/" + denominator;
    }
}

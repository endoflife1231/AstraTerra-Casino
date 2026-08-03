package dev.astraterra.casino;

enum CurrencyUnit {
    BRONZE(0, 1L, "бронза", "б"),
    SILVER(1, 100L, "серебро", "с"),
    GOLD(2, 10_000L, "золото", "з");

    final int id;
    final long multiplier;
    final String label;
    final String shortLabel;

    CurrencyUnit(int id, long multiplier, String label, String shortLabel) {
        this.id = id;
        this.multiplier = multiplier;
        this.label = label;
        this.shortLabel = shortLabel;
    }

    static CurrencyUnit byId(int id) {
        for (CurrencyUnit unit : values()) if (unit.id == id) return unit;
        return SILVER;
    }

    CurrencyUnit next() {
        return values()[(ordinal() + 1) % values().length];
    }

    long toBase(long amount) {
        if (amount <= 0) return 0;
        if (amount > Long.MAX_VALUE / multiplier) return Long.MAX_VALUE;
        return amount * multiplier;
    }
}

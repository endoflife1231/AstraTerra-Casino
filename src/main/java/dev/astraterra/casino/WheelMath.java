package dev.astraterra.casino;

final class WheelMath {
    private WheelMath() {}

    static int targetAngleMilli(int startAngleMilli, int sectorIndex, int rotations, int offsetMilli) {
        int normalizedStart = Math.floorMod(startAngleMilli, 360_000);
        int targetRemainder = Math.floorMod(-sectorIndex * 30_000 + offsetMilli, 360_000);
        int delta = Math.floorMod(targetRemainder - normalizedStart, 360_000);
        return startAngleMilli + Math.max(1, rotations) * 360_000 + delta;
    }

    static double easeOutQuint(double p) {
        p = Math.max(0.0, Math.min(1.0, p));
        double inv = 1.0 - p;
        return 1.0 - inv * inv * inv * inv * inv;
    }

    static double angle(int startAngleMilli, int targetAngleMilli, long elapsedMs, long durationMs) {
        if (durationMs <= 0) return targetAngleMilli / 1000.0;
        double p = Math.max(0.0, Math.min(1.0, elapsedMs / (double) durationMs));
        return startAngleMilli / 1000.0 + (targetAngleMilli - startAngleMilli) / 1000.0 * easeOutQuint(p);
    }
}

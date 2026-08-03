package dev.astraterra.casino;

final class WheelSpin {
    Object player;
    final String playerId;
    final String spinId;
    final int sectorIndex;
    final WheelSector sector;
    final long bet;
    final long payout;
    final long startedAtMs;
    final long durationMs;
    final int fullRotations;
    final int startAngleMilli;
    final int targetAngleMilli;
    boolean finishSent;

    WheelSpin(Object player, String playerId, String spinId, int sectorIndex, WheelSector sector,
              long bet, long payout, long startedAtMs, long durationMs, int fullRotations,
              int startAngleMilli, int targetAngleMilli) {
        this.player = player;
        this.playerId = playerId;
        this.spinId = spinId;
        this.sectorIndex = sectorIndex;
        this.sector = sector;
        this.bet = bet;
        this.payout = payout;
        this.startedAtMs = startedAtMs;
        this.durationMs = durationMs;
        this.fullRotations = fullRotations;
        this.startAngleMilli = startAngleMilli;
        this.targetAngleMilli = targetAngleMilli;
    }

    long elapsed(long now) { return Math.max(0, Math.min(durationMs, now - startedAtMs)); }
    boolean finished(long now) { return elapsed(now) >= durationMs; }
}

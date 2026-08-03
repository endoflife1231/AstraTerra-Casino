package dev.astraterra.casino;

final class WheelClientOptions {
    enum ParticleMode { OFF, LOW, NORMAL }

    private WheelClientOptions() {}

    static boolean animationEnabled() {
        return !Boolean.getBoolean("astraterra.casino.wheel.disableAnimation");
    }

    static boolean reducedAnimation() {
        return Boolean.getBoolean("astraterra.casino.wheel.reducedAnimation");
    }

    static boolean soundsEnabled() {
        return !Boolean.getBoolean("astraterra.casino.wheel.disableSounds");
    }

    static ParticleMode particles() {
        String value = System.getProperty("astraterra.casino.wheel.particles", "NORMAL");
        try { return ParticleMode.valueOf(value.trim().toUpperCase()); }
        catch (IllegalArgumentException ignored) { return ParticleMode.NORMAL; }
    }
}

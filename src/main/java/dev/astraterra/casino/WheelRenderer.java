package dev.astraterra.casino;

import net.minecraft.class_2960;
import net.minecraft.class_327;
import net.minecraft.class_332;
import net.minecraft.class_4587;
import org.joml.Quaternionf;

import java.util.Locale;

final class WheelRenderer {
    private static final class_2960 WHEEL = new class_2960(AstraTerraCasino.MOD_ID, "textures/gui/wheel.png");
    private static final class_2960 POINTER = new class_2960(AstraTerraCasino.MOD_ID, "textures/gui/pointer.png");
    private static final class_2960 HUB = new class_2960(AstraTerraCasino.MOD_ID, "textures/gui/hub.png");
    private static final class_2960 GLOW = new class_2960(AstraTerraCasino.MOD_ID, "textures/gui/sector_glow.png");
    private static final int SECTOR_COUNT = 12;
    private static final double SECTOR_DEGREES = 360.0 / SECTOR_COUNT;

    private String observedSpinId = "";
    private long lastTickMs;
    private int lastBoundary = Integer.MIN_VALUE;
    private boolean stopPlayed;
    private boolean resultPlayed;

    void reset() {
        observedSpinId = "";
        lastTickMs = 0;
        lastBoundary = Integer.MIN_VALUE;
        stopPlayed = false;
        resultPlayed = false;
    }

    void render(class_332 context, class_327 textRenderer, int x1, int x2, int y1, int y2, CasinoViewState state) {
        int width = Math.max(1, x2 - x1);
        int height = Math.max(1, y2 - y1);
        boolean compact = width < 500 || height < 245;
        int legendW = compact ? 0 : Math.min(225, Math.max(170, width / 3));
        int wheelAreaW = width - legendW - (legendW > 0 ? 12 : 0);
        int diameter = ResponsiveCasinoLayout.clamp(Math.min(wheelAreaW - 12, height - 16), compact ? 145 : 180, compact ? 245 : 340);
        diameter = Math.min(diameter, Math.min(wheelAreaW - 4, height - 4));
        int wheelX = x1 + Math.max(2, (wheelAreaW - diameter) / 2);
        int wheelY = y1 + Math.max(2, (height - diameter) / 2);
        int centerX = wheelX + diameter / 2;
        int centerY = wheelY + diameter / 2;

        long now = System.currentTimeMillis();
        double angle = animatedAngle(state, now);
        observeSounds(state, now, angle);

        // Shadow and stable frame.
        context.method_25294(wheelX + 4, wheelY + 6, wheelX + diameter + 6, wheelY + diameter + 8, 0x75000000);
        class_4587 matrices = context.method_51448();
        matrices.method_22903();
        matrices.method_22904(centerX, centerY, 0.0);
        matrices.method_22907(new Quaternionf().rotateZ((float) Math.toRadians(angle)));
        matrices.method_22904(-centerX, -centerY, 0.0);
        context.method_25293(WHEEL, wheelX, wheelY, diameter, diameter, 0, 0, 512, 512, 512, 512);
        matrices.method_22909();

        int hub = Math.max(34, diameter / 4);
        context.method_25293(HUB, centerX - hub / 2, centerY - hub / 2, hub, hub, 0, 0, 128, 128, 128, 128);
        int pointerW = Math.max(34, diameter / 5);
        int pointerH = pointerW;
        context.method_25293(POINTER, centerX - pointerW / 2, wheelY - pointerH / 3, pointerW, pointerH, 0, 0, 96, 96, 96, 96);

        if (isFinished(state)) {
            context.method_25293(GLOW, wheelX, wheelY, diameter, diameter, 0, 0, 512, 512, 512, 512);
            if (WheelClientOptions.particles() != WheelClientOptions.ParticleMode.OFF)
                renderParticles(context, centerX, centerY, diameter, state.wheelSectorIndex, now, WheelClientOptions.particles());
        }

        if (legendW > 0) {
            int lx = x2 - legendW;
            context.method_25294(lx, y1, x2, y2, 0xA8131823);
            border(context, lx, y1, x2, y2, 0xFF4D6078);
            draw(context, textRenderer, "§d§lКОЛЕСО ЭКСПЕДИЦИИ", lx + 11, y1 + 11, 0xFFFFFF);
            draw(context, textRenderer, statusLine(state), lx + 11, y1 + 31, 0xFFFFFF);
            int yy = y1 + 54;
            draw(context, textRenderer, "§7Ставка", lx + 11, yy, 0xAAAAAA);
            draw(context, textRenderer, "§f" + CasinoEngine.money(state.bet), lx + 11, yy + 13, 0xFFFFFF);
            yy += 37;
            draw(context, textRenderer, "§7Сектор", lx + 11, yy, 0xAAAAAA);
            draw(context, textRenderer, "§f" + displaySector(state), lx + 11, yy + 13, 0xFFFFFF);
            yy += 37;
            draw(context, textRenderer, "§7Множитель", lx + 11, yy, 0xAAAAAA);
            draw(context, textRenderer, "§f" + (isFinished(state) ? multiplierText(state) : "—"), lx + 11, yy + 13, 0xFFFFFF);
            yy += 37;
            draw(context, textRenderer, "§7Выплата", lx + 11, yy, 0xAAAAAA);
            draw(context, textRenderer, isFinished(state) ? "§f" + CasinoEngine.money(state.wheelPayout) : "§8скрыта до остановки", lx + 11, yy + 13, 0xFFFFFF);
            if (isFinished(state) && state.wheelPayout != state.bet && state.bet > 0) {
                long delta = state.wheelPayout - state.bet;
                draw(context, textRenderer, (delta >= 0 ? "§aПрибыль +" : "§cУбыток −") + CasinoEngine.money(Math.abs(delta)), lx + 11, Math.min(y2 - 18, yy + 34), 0xFFFFFF);
            }
        } else {
            drawCentered(context, textRenderer, statusLine(state), centerX, Math.min(y2 - 12, wheelY + diameter + 3), 0xFFFFFF);
        }
    }

    private void observeSounds(CasinoViewState state, long now, double angle) {
        if (!WheelClientOptions.soundsEnabled()) return;
        if (state.wheelSpinId == null || state.wheelSpinId.isBlank()) return;
        if (!state.wheelSpinId.equals(observedSpinId)) {
            observedSpinId = state.wheelSpinId;
            lastBoundary = (int) Math.floor(angle / SECTOR_DEGREES);
            lastTickMs = 0;
            stopPlayed = false;
            resultPlayed = false;
        }
        if (isSpinning(state)) {
            int boundary = (int) Math.floor(angle / SECTOR_DEGREES);
            if (boundary != lastBoundary && now - lastTickMs >= 58L) {
                double progress = progress(state, now);
                float pitch = (float) (0.86 + (1.0 - progress) * 0.42);
                WheelSoundController.play("wheel_tick", pitch, 0.30f);
                lastBoundary = boundary;
                lastTickMs = now;
            }
            if (progress(state, now) >= 0.965 && !stopPlayed) {
                WheelSoundController.play("wheel_stop", 0.92f, 0.35f);
                stopPlayed = true;
            }
        } else if (isFinished(state) && !resultPlayed) {
            String sound = switch (state.wheelRarity == null ? "" : state.wheelRarity.toUpperCase(Locale.ROOT)) {
                case "JACKPOT" -> "wheel_jackpot";
                case "EPIC", "RARE", "UNCOMMON" -> "wheel_win";
                default -> state.wheelPayout > 0 ? "wheel_stop" : "wheel_loss";
            };
            WheelSoundController.play(sound, 1.0f, "JACKPOT".equals(state.wheelRarity) ? 0.58f : 0.44f);
            resultPlayed = true;
        }
    }

    static double animatedAngle(CasinoViewState state, long now) {
        if (!WheelClientOptions.animationEnabled() || state.wheelDurationMs <= 0) return state.wheelTargetAngleMilli / 1000.0;
        double p = progress(state, now);
        double eased = WheelMath.easeOutQuint(p);
        double start = state.wheelStartAngleMilli / 1000.0;
        double target = state.wheelTargetAngleMilli / 1000.0;
        return start + (target - start) * eased;
    }

    static double progress(CasinoViewState state, long now) {
        if (!WheelClientOptions.animationEnabled() || state.wheelDurationMs <= 0) return 1.0;
        long duration = WheelClientOptions.reducedAnimation() ? Math.min(800L, state.wheelDurationMs) : state.wheelDurationMs;
        long baseElapsed = WheelClientOptions.reducedAnimation()
            ? Math.min(duration, Math.round(state.wheelElapsedMs * (duration / (double) state.wheelDurationMs)))
            : state.wheelElapsedMs;
        long localDelta = state.wheelSnapshotReceivedAtMs <= 0 ? 0 : Math.max(0, now - state.wheelSnapshotReceivedAtMs);
        long elapsed = Math.min(duration, Math.max(0, baseElapsed + localDelta));
        return Math.max(0.0, Math.min(1.0, elapsed / (double) duration));
    }

    static double easeOutQuint(double p) { return WheelMath.easeOutQuint(p); }

    static int targetAngleMilli(int startAngleMilli, int sectorIndex, int rotations, int offsetMilli) {
        return WheelMath.targetAngleMilli(startAngleMilli, sectorIndex, rotations, offsetMilli);
    }

    private static boolean isSpinning(CasinoViewState state) { return "SPINNING".equals(state.wheelState); }
    private static boolean isFinished(CasinoViewState state) { return "FINISHED".equals(state.wheelState); }

    private static String statusLine(CasinoViewState state) {
        if (isSpinning(state)) return "§bКолесо вращается… §f" + Math.round(progress(state, System.currentTimeMillis()) * 100) + "%";
        if (isFinished(state)) return state.wheelPayout > state.bet ? "§aРезультат зафиксирован" : state.wheelPayout == state.bet ? "§eСтавка возвращена" : "§cЭкспедиция без награды";
        return "§7Выберите ставку и запустите колесо";
    }

    private static String displaySector(CasinoViewState state) {
        return state.wheelSectorName == null || state.wheelSectorName.isBlank() ? "скрыт до остановки" : state.wheelSectorName;
    }

    private static String multiplierText(CasinoViewState state) {
        if (state.wheelMultiplierDenominator <= 0) return "—";
        if (state.wheelMultiplierDenominator == 1) return "×" + state.wheelMultiplierNumerator;
        if (state.wheelMultiplierNumerator == 1 && state.wheelMultiplierDenominator == 2) return "×0.5";
        if (state.wheelMultiplierNumerator == 3 && state.wheelMultiplierDenominator == 2) return "×1.5";
        return "×" + state.wheelMultiplierNumerator + "/" + state.wheelMultiplierDenominator;
    }

    private static void renderParticles(class_332 context, int cx, int cy, int diameter, int seed, long now,
                                        WheelClientOptions.ParticleMode mode) {
        double phase = (now % 1400L) / 1400.0;
        int count = mode == WheelClientOptions.ParticleMode.LOW ? 10 : 24;
        for (int i = 0; i < count; i++) {
            double angle = (i * Math.PI * 2.0 / count) + phase * Math.PI * 2.0;
            double radius = diameter * (0.40 + ((i * 37 + seed * 13) % 24) / 100.0);
            int x = cx + (int) Math.round(Math.cos(angle) * radius);
            int y = cy + (int) Math.round(Math.sin(angle) * radius);
            int size = 1 + (i % 3);
            int alpha = 0x70 + (i % 4) * 0x20;
            int color = (alpha << 24) | (i % 2 == 0 ? 0xFFD66B : 0xE7A8FF);
            context.method_25294(x, y, x + size, y + size, color);
        }
    }

    private static void draw(class_332 c, class_327 renderer, String text, int x, int y, int color) {
        c.method_27535(renderer, TextBridge.of(text), x, y, color);
    }

    private static void drawCentered(class_332 c, class_327 renderer, String text, int x, int y, int color) {
        c.method_27534(renderer, TextBridge.of(text), x, y, color);
    }

    private static void border(class_332 c, int x1, int y1, int x2, int y2, int color) {
        c.method_25294(x1, y1, x2, y1 + 1, color);
        c.method_25294(x1, y2 - 1, x2, y2, color);
        c.method_25294(x1, y1, x1 + 1, y2, color);
        c.method_25294(x2 - 1, y1, x2, y2, color);
    }
}

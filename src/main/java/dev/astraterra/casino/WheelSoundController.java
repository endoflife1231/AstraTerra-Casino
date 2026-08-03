package dev.astraterra.casino;

import net.minecraft.class_1109;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_3414;

final class WheelSoundController {
    private WheelSoundController() {}

    static void play(String path, float pitch, float volume) {
        try {
            class_2960 id = new class_2960(AstraTerraCasino.MOD_ID, path);
            class_3414 event = class_3414.method_47908(id);
            class_1109 instance = class_1109.method_4757(event, pitch, volume);
            class_310.method_1551().method_1483().method_4873(instance);
        } catch (Throwable ignored) {
            // Audio is optional; rendering and payouts must continue if a client sound backend rejects a resource.
        }
    }
}

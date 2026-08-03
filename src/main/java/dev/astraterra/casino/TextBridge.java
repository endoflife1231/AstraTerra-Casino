package dev.astraterra.casino;

import net.minecraft.class_2561;

/** Runtime-safe creation of Minecraft text without baking a mapped return descriptor into bytecode. */
final class TextBridge {
    private TextBridge() {}

    static class_2561 of(String value) {
        try {
            return (class_2561) Reflect.invokeStatic("net.minecraft.class_2561", "method_30163", value);
        } catch (ReflectiveOperationException first) {
            try {
                return (class_2561) Reflect.invokeStatic("net.minecraft.class_2561", "method_43470", value);
            } catch (ReflectiveOperationException second) {
                second.addSuppressed(first);
                throw new IllegalStateException("Could not create Minecraft text", second);
            }
        }
    }
}

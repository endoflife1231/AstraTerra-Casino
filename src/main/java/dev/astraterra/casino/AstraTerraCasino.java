package dev.astraterra.casino;

import net.fabricmc.api.ModInitializer;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.Executor;

public final class AstraTerraCasino implements ModInitializer {
    public static final String MOD_ID = "astraterra-casino";
    private static final List<String> LEGACY_ACTIONS = List.of(
        "status", "deposit_1", "deposit_10", "deposit_all", "withdraw_1", "withdraw_10", "withdraw_all",
        "bj_start_1", "bj_start_5", "bj_hit", "bj_stand", "bj_double",
        "roulette_red", "roulette_black", "roulette_even", "roulette_odd", "roulette_zero", "roulette_dozen1", "roulette_dozen2", "roulette_dozen3",
        "dice_low", "dice_seven", "dice_high", "wheel_1", "wheel_5"
    );

    @Override public void onInitialize() {
        CasinoStats.registerAll();
        RewardCommand.register();
        registerRequestChannel();
        registerServerEvents();
        for (String action : LEGACY_ACTIONS) registerLegacy(action);
        System.out.println("[AstraTerra Casino] 0.9.5 initialized: animated server-authoritative Expedition Wheel");
    }

    private static void registerRequestChannel() {
        try {
            Class<?> type = Reflect.cls("net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking$PlayChannelHandler");
            Object handler = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) return objectMethod(proxy, method, args, "CasinoRequestChannel");
                if (args == null || args.length < 2) return null;
                Object server = args[0], player = args[1], buffer = findBuffer(args);
                CasinoRequest request = CasinoRequest.read(buffer);
                Runnable task = () -> CasinoEngine.action(player, request);
                if (server instanceof Executor executor) executor.execute(task); else Reflect.invoke(server, "execute", task);
                return null;
            });
            Reflect.invokeStatic("net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking", "registerGlobalReceiver", id("request"), handler);
        } catch (Throwable e) {
            throw new IllegalStateException("Could not register casino request channel", e);
        }
    }


    private static void registerServerEvents() {
        try {
            Class<?> type = Reflect.cls("net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents$EndTick");
            Object listener = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) return objectMethod(proxy, method, args, "CasinoServerTick");
                MultiplayerRooms.tick();
                CasinoEngine.tick();
                return null;
            });
            Object event = Reflect.staticField("net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents", "END_SERVER_TICK");
            Reflect.invoke(event, "register", listener);
        } catch (Throwable e) {
            System.err.println("[AstraTerra Casino] Turn timers unavailable: " + e);
        }

        registerConnectionEvent("JOIN", "Join", true);
        registerConnectionEvent("DISCONNECT", "Disconnect", false);
    }

    private static void registerConnectionEvent(String field, String nestedType, boolean joined) {
        try {
            String base = "net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents";
            Class<?> type = Reflect.cls(base + "$" + nestedType);
            Object listener = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) return objectMethod(proxy, method, args, "CasinoConnection:" + field);
                if (args != null && args.length > 0) {
                    Object player = Reflect.field(args[0], "field_14140");
                    if (joined) {
                        MultiplayerRooms.connected(player);
                        CasinoEngine.connected(player);
                    } else {
                        MultiplayerRooms.disconnected(player);
                        CasinoEngine.disconnected(player);
                    }
                }
                return null;
            });
            Object event = Reflect.staticField(base, field);
            Reflect.invoke(event, "register", listener);
        } catch (Throwable e) {
            System.err.println("[AstraTerra Casino] Connection event " + field + " unavailable: " + e);
        }
    }

    private static Object findBuffer(Object[] args) throws ReflectiveOperationException {
        Class<?> packet = Reflect.cls("net.minecraft.class_2540");
        for (Object arg : args) if (arg != null && packet.isInstance(arg)) return arg;
        throw new NoSuchElementException("PacketByteBuf argument not found");
    }

    private static void registerLegacy(String action) {
        try {
            Object channel = id(action);
            Class<?> type = Reflect.cls("net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking$PlayChannelHandler");
            Object handler = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) return objectMethod(proxy, method, args, "LegacyCasino:" + action);
                if (args == null || args.length < 2) return null;
                Object server = args[0], player = args[1];
                Runnable task = () -> CasinoEngine.action(player, CasinoEngine.legacyRequest(action));
                if (server instanceof Executor executor) executor.execute(task); else Reflect.invoke(server, "execute", task);
                return null;
            });
            Reflect.invokeStatic("net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking", "registerGlobalReceiver", channel, handler);
        } catch (Throwable e) {
            throw new IllegalStateException("Could not register legacy channel " + action, e);
        }
    }

    private static Object objectMethod(Object proxy, Method method, Object[] args, String name) {
        return switch (method.getName()) {
            case "toString" -> name;
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == (args == null ? null : args[0]);
            default -> null;
        };
    }

    public static Object id(String path) throws ReflectiveOperationException {
        return Reflect.construct("net.minecraft.class_2960", MOD_ID, path);
    }
}

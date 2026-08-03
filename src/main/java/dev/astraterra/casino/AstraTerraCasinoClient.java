package dev.astraterra.casino;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.class_310;
import java.lang.reflect.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

public final class AstraTerraCasinoClient implements ClientModInitializer {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static volatile CasinoViewState VIEW = new CasinoViewState();
    private static final ArrayDeque<String> HISTORY = new ArrayDeque<>();
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static volatile long LAST_SENT;
    private static volatile long LAST_ACKED;

    @Override public void onInitializeClient() {
        installInventoryButton();
        installStateReceiver();
    }

    private static void installInventoryButton() {
        try {
            Object event = Reflect.staticField("net.fabricmc.fabric.api.client.screen.v1.ScreenEvents", "AFTER_INIT");
            Class<?> type = Reflect.cls("net.fabricmc.fabric.api.client.screen.v1.ScreenEvents$AfterInit");
            Object callback = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) return null;
                if (args != null && args.length >= 4) addButton(args[1], ((Number) args[2]).intValue(), ((Number) args[3]).intValue());
                return null;
            });
            Reflect.invoke(event, "register", callback);
        } catch (Throwable e) {
            throw new IllegalStateException("Could not install inventory casino button", e);
        }
    }

    private static void installStateReceiver() {
        try {
            Class<?> type = Reflect.cls("net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking$PlayChannelHandler");
            Object handler = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) return null;
                if (args == null || args.length < 3) return null;
                Object client = args[0];
                CasinoViewState state = CasinoPacket.read(args[2]);
                Runnable apply = () -> applyState(state);
                if (client instanceof Executor executor) executor.execute(apply); else Reflect.invoke(client, "execute", apply);
                return null;
            });
            Reflect.invokeStatic("net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking", "registerGlobalReceiver", AstraTerraCasino.id("state"), handler);
        } catch (Throwable e) {
            throw new IllegalStateException("Could not install casino state receiver", e);
        }
    }

    private static void applyState(CasinoViewState state) {
        CasinoViewState previous = VIEW;
        boolean sameRoom = !state.roomCode.isBlank() && state.roomCode.equals(previous.roomCode) && state.roomType.equals(previous.roomType);
        if (sameRoom && state.revision < previous.revision) return;
        state.wheelSnapshotReceivedAtMs = System.currentTimeMillis();
        VIEW = state.copy();
        LAST_ACKED = Math.max(LAST_ACKED, state.ackSequence);
        if (state.event != null && !state.event.isBlank()) {
            synchronized (HISTORY) {
                HISTORY.addFirst("§8[" + LocalTime.now().format(TIME) + "] §r" + state.event);
                while (HISTORY.size() > 40) HISTORY.removeLast();
            }
        }
        CasinoScreen.refreshActive();
    }

    static CasinoViewState view() { return VIEW.copy(); }
    static boolean actionPending() { return LAST_SENT > LAST_ACKED; }

    static List<String> history() {
        synchronized (HISTORY) { return new ArrayList<>(HISTORY); }
    }

    @SuppressWarnings("unchecked") private static void addButton(Object screen, int w, int h) {
        try {
            if (!Reflect.cls("net.minecraft.class_490").isInstance(screen)) return;
            Object text = TextBridge.of("§6AstraTerra Club");
            Class<?> press = Reflect.cls("net.minecraft.class_4185$class_4241");
            Object action = Proxy.newProxyInstance(press.getClassLoader(), new Class<?>[]{press}, (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) return null;
                class_310.method_1551().method_1507(new CasinoScreen());
                send("status", 0, CurrencyUnit.SILVER.id, "", "");
                return null;
            });
            Object builder = Reflect.invokeStatic("net.minecraft.class_4185", "method_46430", text, action);
            Reflect.invoke(builder, "method_46434", w / 2 - 60, Math.max(4, h / 2 - 126), 120, 20);
            Object button = Reflect.invoke(builder, "method_46431");
            Object list = Reflect.invokeStatic("net.fabricmc.fabric.api.client.screen.v1.Screens", "getButtons", screen);
            ((List<Object>) list).add(button);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    static void send(String action, long amount, int unitId, String text, String extra) {
        try {
            long sequence = SEQUENCE.incrementAndGet();
            CasinoViewState view = VIEW;
            Object buffer = Reflect.invokeStatic("net.fabricmc.fabric.api.networking.v1.PacketByteBufs", "create");
            CasinoRequest.write(buffer, new CasinoRequest(action, amount, unitId, text, extra, view.revision, sequence));
            LAST_SENT = sequence;
            Reflect.invokeStatic("net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking", "send", AstraTerraCasino.id("request"), buffer);
        } catch (Throwable e) {
            System.err.println("[AstraTerra Casino] Send failed: " + action);
            e.printStackTrace();
        }
    }

    static void send(String action) { send(action, 0, CurrencyUnit.SILVER.id, "", ""); }
}

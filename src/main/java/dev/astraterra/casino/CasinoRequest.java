package dev.astraterra.casino;

import java.nio.charset.StandardCharsets;

final class CasinoRequest {
    private static final int PROTOCOL = 5;
    private static final int MAX_STRING_BYTES = 256;

    final String action;
    final long amount;
    final int unitId;
    final String text;
    final String extra;
    final long revision;
    final long sequence;

    CasinoRequest(String action, long amount, int unitId, String text, String extra) {
        this(action, amount, unitId, text, extra, 0, 0);
    }

    CasinoRequest(String action, long amount, int unitId, String text, String extra, long revision, long sequence) {
        this.action = action == null ? "" : action;
        this.amount = Math.max(0, amount);
        this.unitId = unitId;
        this.text = text == null ? "" : text;
        this.extra = extra == null ? "" : extra;
        this.revision = Math.max(0, revision);
        this.sequence = Math.max(0, sequence);
    }

    long baseAmount() {
        return CurrencyUnit.byId(unitId).toBase(amount);
    }

    static void write(Object buffer, CasinoRequest request) throws ReflectiveOperationException {
        Reflect.invoke(buffer, "writeInt", PROTOCOL);
        writeString(buffer, request.action);
        Reflect.invoke(buffer, "writeLong", request.amount);
        Reflect.invoke(buffer, "writeInt", request.unitId);
        writeString(buffer, request.text);
        writeString(buffer, request.extra);
        Reflect.invoke(buffer, "writeLong", request.revision);
        Reflect.invoke(buffer, "writeLong", request.sequence);
    }

    static CasinoRequest read(Object buffer) throws ReflectiveOperationException {
        int protocol = ((Number) Reflect.invoke(buffer, "readInt")).intValue();
        if (protocol != PROTOCOL) throw new IllegalStateException("Unsupported casino request protocol: " + protocol);
        String action = readString(buffer);
        long amount = ((Number) Reflect.invoke(buffer, "readLong")).longValue();
        int unit = ((Number) Reflect.invoke(buffer, "readInt")).intValue();
        String text = readString(buffer);
        String extra = readString(buffer);
        long revision = ((Number) Reflect.invoke(buffer, "readLong")).longValue();
        long sequence = ((Number) Reflect.invoke(buffer, "readLong")).longValue();
        return new CasinoRequest(action, amount, unit, text, extra, revision, sequence);
    }

    private static void writeString(Object buffer, String value) throws ReflectiveOperationException {
        byte[] data = value.getBytes(StandardCharsets.UTF_8);
        if (data.length > MAX_STRING_BYTES) {
            byte[] truncated = new byte[MAX_STRING_BYTES];
            System.arraycopy(data, 0, truncated, 0, truncated.length);
            data = truncated;
        }
        Reflect.invoke(buffer, "writeInt", data.length);
        Reflect.invoke(buffer, "writeBytes", data);
    }

    private static String readString(Object buffer) throws ReflectiveOperationException {
        int length = ((Number) Reflect.invoke(buffer, "readInt")).intValue();
        if (length < 0 || length > MAX_STRING_BYTES) throw new IllegalArgumentException("Invalid request string length: " + length);
        byte[] data = new byte[length];
        Reflect.invoke(buffer, "readBytes", data);
        return new String(data, StandardCharsets.UTF_8);
    }
}

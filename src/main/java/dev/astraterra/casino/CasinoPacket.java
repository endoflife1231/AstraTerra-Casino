package dev.astraterra.casino;

import java.nio.charset.StandardCharsets;

final class CasinoPacket {
    private static final int MAX_STRING_BYTES = 32_768;
    private CasinoPacket() {}

    static void send(Object player, CasinoViewState state) throws ReflectiveOperationException {
        Object buffer = Reflect.invokeStatic("net.fabricmc.fabric.api.networking.v1.PacketByteBufs", "create");
        write(buffer, state);
        Reflect.invokeStatic("net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking", "send", player, AstraTerraCasino.id("state"), buffer);
    }

    static void write(Object buffer, CasinoViewState state) throws ReflectiveOperationException {
        Reflect.invoke(buffer, "writeInt", CasinoViewState.PROTOCOL);
        Reflect.invoke(buffer, "writeLong", state.wallet);
        Reflect.invoke(buffer, "writeLong", state.numismatic);
        Reflect.invoke(buffer, "writeLong", state.bet);
        writeString(buffer, state.game); writeString(buffer, state.phase); writeString(buffer, state.playerCards);
        writeString(buffer, state.dealerCards); writeString(buffer, state.result); writeString(buffer, state.event);
        Reflect.invoke(buffer, "writeInt", state.playerValue); Reflect.invoke(buffer, "writeInt", state.dealerValue);
        Reflect.invoke(buffer, "writeBoolean", state.blackjackActive);

        writeString(buffer, state.wheelSpinId); writeString(buffer, state.wheelState);
        writeString(buffer, state.wheelSectorId); writeString(buffer, state.wheelSectorName); writeString(buffer, state.wheelRarity);
        Reflect.invoke(buffer, "writeInt", state.wheelSectorIndex); Reflect.invoke(buffer, "writeInt", state.wheelRotations);
        Reflect.invoke(buffer, "writeInt", state.wheelStartAngleMilli); Reflect.invoke(buffer, "writeInt", state.wheelTargetAngleMilli);
        Reflect.invoke(buffer, "writeInt", state.wheelMultiplierNumerator); Reflect.invoke(buffer, "writeInt", state.wheelMultiplierDenominator);
        Reflect.invoke(buffer, "writeLong", state.wheelDurationMs); Reflect.invoke(buffer, "writeLong", state.wheelElapsedMs);
        Reflect.invoke(buffer, "writeLong", state.wheelPayout);
        writeString(buffer, state.roomType); writeString(buffer, state.roomCode); writeString(buffer, state.roomPlayers);
        writeString(buffer, state.roomStatus); writeString(buffer, state.handCards); writeString(buffer, state.boardCards);
        writeString(buffer, state.tableCards); writeString(buffer, state.trumpCard); writeString(buffer, state.currentTurn);
        writeString(buffer, state.role);
        Reflect.invoke(buffer, "writeInt", state.deckCount);
        Reflect.invoke(buffer, "writeBoolean", state.durakTaking);
        Reflect.invoke(buffer, "writeLong", state.stack); Reflect.invoke(buffer, "writeLong", state.pot);
        Reflect.invoke(buffer, "writeLong", state.toCall); Reflect.invoke(buffer, "writeLong", state.minRaise);
        Reflect.invoke(buffer, "writeLong", state.maxRaise); Reflect.invoke(buffer, "writeLong", state.currentBet);
        Reflect.invoke(buffer, "writeLong", state.streetContribution); Reflect.invoke(buffer, "writeLong", state.handContribution);
        Reflect.invoke(buffer, "writeLong", state.roomStake); Reflect.invoke(buffer, "writeLong", state.revision);
        Reflect.invoke(buffer, "writeLong", state.handId); Reflect.invoke(buffer, "writeLong", state.ackSequence);
        Reflect.invoke(buffer, "writeLong", state.turnDeadlineEpochMs);
        Reflect.invoke(buffer, "writeInt", state.players.size());
        for (CasinoPlayerView player : state.players) writePlayer(buffer, player);
        writeString(buffer, state.allowedActions); writeString(buffer, state.sidePots); writeString(buffer, state.dealerName);
        writeString(buffer, state.smallBlindName); writeString(buffer, state.bigBlindName); writeString(buffer, state.lastAction);
        writeString(buffer, state.errorCode);
        Reflect.invoke(buffer, "writeBoolean", state.roomHost); Reflect.invoke(buffer, "writeBoolean", state.roomReady);
        Reflect.invoke(buffer, "writeBoolean", state.multiplayerActive); Reflect.invoke(buffer, "writeBoolean", state.canAct);
    }

    static CasinoViewState read(Object buffer) throws ReflectiveOperationException {
        int protocol = intValue(buffer);
        if (protocol != CasinoViewState.PROTOCOL) throw new IllegalStateException("Unsupported casino protocol: " + protocol);
        CasinoViewState state = new CasinoViewState();
        state.wallet = longValue(buffer); state.numismatic = longValue(buffer); state.bet = longValue(buffer);
        state.game = readString(buffer); state.phase = readString(buffer); state.playerCards = readString(buffer);
        state.dealerCards = readString(buffer); state.result = readString(buffer); state.event = readString(buffer);
        state.playerValue = intValue(buffer); state.dealerValue = intValue(buffer); state.blackjackActive = boolValue(buffer);

        state.wheelSpinId = readString(buffer); state.wheelState = readString(buffer);
        state.wheelSectorId = readString(buffer); state.wheelSectorName = readString(buffer); state.wheelRarity = readString(buffer);
        state.wheelSectorIndex = intValue(buffer); state.wheelRotations = intValue(buffer);
        state.wheelStartAngleMilli = intValue(buffer); state.wheelTargetAngleMilli = intValue(buffer);
        state.wheelMultiplierNumerator = intValue(buffer); state.wheelMultiplierDenominator = intValue(buffer);
        state.wheelDurationMs = longValue(buffer); state.wheelElapsedMs = longValue(buffer); state.wheelPayout = longValue(buffer);
        state.roomType = readString(buffer); state.roomCode = readString(buffer); state.roomPlayers = readString(buffer);
        state.roomStatus = readString(buffer); state.handCards = readString(buffer); state.boardCards = readString(buffer);
        state.tableCards = readString(buffer); state.trumpCard = readString(buffer); state.currentTurn = readString(buffer);
        state.role = readString(buffer);
        state.deckCount = intValue(buffer);
        state.durakTaking = boolValue(buffer);
        state.stack = longValue(buffer); state.pot = longValue(buffer); state.toCall = longValue(buffer);
        state.minRaise = longValue(buffer); state.maxRaise = longValue(buffer); state.currentBet = longValue(buffer);
        state.streetContribution = longValue(buffer); state.handContribution = longValue(buffer);
        state.roomStake = longValue(buffer); state.revision = longValue(buffer); state.handId = longValue(buffer); state.ackSequence = longValue(buffer);
        state.turnDeadlineEpochMs = longValue(buffer);
        int playerCount = intValue(buffer);
        if (playerCount < 0 || playerCount > 6) throw new IllegalArgumentException("Invalid casino player count: " + playerCount);
        for (int i = 0; i < playerCount; i++) state.players.add(readPlayer(buffer));
        state.allowedActions = readString(buffer); state.sidePots = readString(buffer); state.dealerName = readString(buffer);
        state.smallBlindName = readString(buffer); state.bigBlindName = readString(buffer); state.lastAction = readString(buffer);
        state.errorCode = readString(buffer);
        state.roomHost = boolValue(buffer); state.roomReady = boolValue(buffer);
        state.multiplayerActive = boolValue(buffer); state.canAct = boolValue(buffer);
        return state;
    }


    private static void writePlayer(Object buffer, CasinoPlayerView player) throws ReflectiveOperationException {
        writeString(buffer, player.id); writeString(buffer, player.name); writeString(buffer, player.status); writeString(buffer, player.cards);
        Reflect.invoke(buffer, "writeLong", player.stack);
        Reflect.invoke(buffer, "writeLong", player.streetContribution);
        Reflect.invoke(buffer, "writeLong", player.handContribution);
        Reflect.invoke(buffer, "writeBoolean", player.local);
        Reflect.invoke(buffer, "writeBoolean", player.host);
        Reflect.invoke(buffer, "writeBoolean", player.ready);
        Reflect.invoke(buffer, "writeBoolean", player.connected);
        Reflect.invoke(buffer, "writeBoolean", player.activeTurn);
        Reflect.invoke(buffer, "writeBoolean", player.dealer);
        Reflect.invoke(buffer, "writeBoolean", player.smallBlind);
        Reflect.invoke(buffer, "writeBoolean", player.bigBlind);
        Reflect.invoke(buffer, "writeBoolean", player.folded);
        Reflect.invoke(buffer, "writeBoolean", player.allIn);
        Reflect.invoke(buffer, "writeBoolean", player.winner);
    }

    private static CasinoPlayerView readPlayer(Object buffer) throws ReflectiveOperationException {
        CasinoPlayerView player = new CasinoPlayerView();
        player.id = readString(buffer); player.name = readString(buffer); player.status = readString(buffer); player.cards = readString(buffer);
        player.stack = longValue(buffer); player.streetContribution = longValue(buffer); player.handContribution = longValue(buffer);
        player.local = boolValue(buffer); player.host = boolValue(buffer); player.ready = boolValue(buffer); player.connected = boolValue(buffer);
        player.activeTurn = boolValue(buffer); player.dealer = boolValue(buffer); player.smallBlind = boolValue(buffer); player.bigBlind = boolValue(buffer);
        player.folded = boolValue(buffer); player.allIn = boolValue(buffer); player.winner = boolValue(buffer);
        return player;
    }

    private static long longValue(Object buffer) throws ReflectiveOperationException { return ((Number) Reflect.invoke(buffer, "readLong")).longValue(); }
    private static int intValue(Object buffer) throws ReflectiveOperationException { return ((Number) Reflect.invoke(buffer, "readInt")).intValue(); }
    private static boolean boolValue(Object buffer) throws ReflectiveOperationException { return (Boolean) Reflect.invoke(buffer, "readBoolean"); }

    private static void writeString(Object buffer, String value) throws ReflectiveOperationException {
        byte[] data = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
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
        if (length < 0 || length > MAX_STRING_BYTES) throw new IllegalArgumentException("Invalid casino packet string length: " + length);
        byte[] data = new byte[length]; Reflect.invoke(buffer, "readBytes", data);
        return new String(data, StandardCharsets.UTF_8);
    }
}

package dev.astraterra.casino;

import java.util.ArrayList;
import java.util.List;

final class CasinoViewState {
    static final int PROTOCOL = 6;

    long wallet;
    long numismatic;
    long bet;
    String game = "Лобби";
    String phase = "Готово к игре";
    String playerCards = "";
    String dealerCards = "";
    String result = "Выберите игру и ставку.";
    String event = "";
    int playerValue = -1;
    int dealerValue = -1;
    boolean blackjackActive;

    String wheelSpinId = "";
    String wheelState = "IDLE";
    String wheelSectorId = "";
    String wheelSectorName = "";
    String wheelRarity = "";
    int wheelSectorIndex = -1;
    int wheelRotations;
    int wheelStartAngleMilli;
    int wheelTargetAngleMilli;
    int wheelMultiplierNumerator;
    int wheelMultiplierDenominator = 1;
    long wheelDurationMs;
    long wheelElapsedMs;
    long wheelPayout;
    long wheelSnapshotReceivedAtMs;

    String roomType = "";
    String roomCode = "";
    String roomPlayers = "";
    String roomStatus = "";
    String handCards = "";
    String boardCards = "";
    String tableCards = "";
    String trumpCard = "";
    String currentTurn = "";
    String role = "";
    int deckCount;
    boolean durakTaking;
    long stack;
    long pot;
    long toCall;
    long minRaise;
    long maxRaise;
    long currentBet;
    long streetContribution;
    long handContribution;
    long roomStake;
    long revision;
    long handId;
    long ackSequence;
    long turnDeadlineEpochMs;
    final List<CasinoPlayerView> players = new ArrayList<>();
    String allowedActions = "";
    String sidePots = "";
    String dealerName = "";
    String smallBlindName = "";
    String bigBlindName = "";
    String lastAction = "";
    String errorCode = "";
    boolean roomHost;
    boolean roomReady;
    boolean multiplayerActive;
    boolean canAct;

    boolean allows(String action) {
        if (action == null || action.isBlank() || allowedActions == null || allowedActions.isBlank()) return false;
        for (String value : allowedActions.split(",")) if (value.equals(action)) return true;
        return false;
    }

    CasinoViewState copy() {
        CasinoViewState c = new CasinoViewState();
        c.wallet = wallet; c.numismatic = numismatic; c.bet = bet;
        c.game = game; c.phase = phase; c.playerCards = playerCards; c.dealerCards = dealerCards;
        c.result = result; c.event = event; c.playerValue = playerValue; c.dealerValue = dealerValue;
        c.blackjackActive = blackjackActive;

        c.wheelSpinId = wheelSpinId; c.wheelState = wheelState; c.wheelSectorId = wheelSectorId;
        c.wheelSectorName = wheelSectorName; c.wheelRarity = wheelRarity; c.wheelSectorIndex = wheelSectorIndex;
        c.wheelRotations = wheelRotations; c.wheelStartAngleMilli = wheelStartAngleMilli; c.wheelTargetAngleMilli = wheelTargetAngleMilli;
        c.wheelMultiplierNumerator = wheelMultiplierNumerator; c.wheelMultiplierDenominator = wheelMultiplierDenominator;
        c.wheelDurationMs = wheelDurationMs; c.wheelElapsedMs = wheelElapsedMs; c.wheelPayout = wheelPayout;
        c.wheelSnapshotReceivedAtMs = wheelSnapshotReceivedAtMs;
        c.roomType = roomType; c.roomCode = roomCode; c.roomPlayers = roomPlayers; c.roomStatus = roomStatus;
        c.handCards = handCards; c.boardCards = boardCards; c.tableCards = tableCards; c.trumpCard = trumpCard;
        c.currentTurn = currentTurn; c.role = role; c.deckCount = deckCount; c.durakTaking = durakTaking; c.stack = stack; c.pot = pot; c.toCall = toCall;
        c.minRaise = minRaise; c.maxRaise = maxRaise; c.currentBet = currentBet;
        c.streetContribution = streetContribution; c.handContribution = handContribution;
        c.roomStake = roomStake; c.revision = revision; c.handId = handId; c.ackSequence = ackSequence;
        c.turnDeadlineEpochMs = turnDeadlineEpochMs;
        for (CasinoPlayerView player : players) c.players.add(player.copy());
        c.allowedActions = allowedActions; c.sidePots = sidePots; c.dealerName = dealerName;
        c.smallBlindName = smallBlindName; c.bigBlindName = bigBlindName; c.lastAction = lastAction;
        c.errorCode = errorCode;
        c.roomHost = roomHost; c.roomReady = roomReady; c.multiplayerActive = multiplayerActive; c.canAct = canAct;
        return c;
    }
}

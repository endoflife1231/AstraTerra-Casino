package dev.astraterra.casino;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

final class MultiplayerRooms {
    private static final Map<String, PokerRoom> POKER = new HashMap<>();
    private static final Map<String, DurakRoom> DURAK = new HashMap<>();
    private static final Map<String, RoomRef> MEMBERSHIP = new HashMap<>();
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private MultiplayerRooms() {}

    static synchronized boolean handles(String action) {
        return action.startsWith("poker_") || action.startsWith("durak_");
    }

    static synchronized void action(Object player, CasinoRequest request) {
        if (request.action.startsWith("poker_")) pokerAction(player, request);
        else if (request.action.startsWith("durak_")) durakAction(player, request);
    }

    static synchronized boolean isMember(Object player) { return MEMBERSHIP.containsKey(CasinoData.id(player)); }

    static synchronized CasinoViewState view(Object player) {
        RoomRef ref = MEMBERSHIP.get(CasinoData.id(player));
        if (ref == null) return null;
        if (ref.type.equals("poker")) {
            PokerRoom room = POKER.get(ref.code);
            return room == null ? null : room.view(player);
        }
        DurakRoom room = DURAK.get(ref.code);
        return room == null ? null : room.view(player);
    }

    static synchronized void tick() {
        long now = System.currentTimeMillis();
        for (PokerRoom room : new ArrayList<>(POKER.values())) {
            room.tick(now);
            room.syncIfDue(now);
        }
        for (DurakRoom room : new ArrayList<>(DURAK.values())) room.syncIfDue(now);
    }

    static synchronized void connected(Object player) {
        String id = CasinoData.id(player);
        RoomRef ref = MEMBERSHIP.get(id);
        if (ref == null) return;
        Room<?> room = ref.type.equals("poker") ? POKER.get(ref.code) : DURAK.get(ref.code);
        if (room == null) return;
        Seat seat = room.seatById(id);
        if (seat == null) return;
        boolean reconnected = !seat.connected;
        seat.player = player;
        seat.connected = true;
        if (reconnected) room.broadcast("§a" + seat.name + " переподключился к комнате.");
    }

    static synchronized void disconnected(Object player) {
        String id = CasinoData.id(player);
        RoomRef ref = MEMBERSHIP.get(id);
        if (ref == null) return;
        Room<?> room = ref.type.equals("poker") ? POKER.get(ref.code) : DURAK.get(ref.code);
        if (room == null) return;
        Seat seat = room.seatById(id);
        if (seat == null) return;
        seat.connected = false;
        if (room instanceof PokerRoom poker) poker.onDisconnect(seat);
        room.broadcast("§e" + seat.name + " отключился. Место сохранено для переподключения.");
    }

    private static void pokerAction(Object player, CasinoRequest request) {
        switch (request.action) {
            case "poker_create" -> createPoker(player, request);
            case "poker_join" -> joinPoker(player, request);
            default -> {
                PokerRoom room = pokerRoom(player);
                if (room == null) { CasinoEngine.errorState(player, "Вы не состоите в покерной комнате."); return; }
                room.action(player, request);
            }
        }
    }

    private static void durakAction(Object player, CasinoRequest request) {
        switch (request.action) {
            case "durak_create" -> createDurak(player, request);
            case "durak_join" -> joinDurak(player, request);
            default -> {
                DurakRoom room = durakRoom(player);
                if (room == null) { CasinoEngine.errorState(player, "Вы не состоите в комнате дурака."); return; }
                room.action(player, request);
            }
        }
    }

    private static void createPoker(Object player, CasinoRequest request) {
        if (!canCreate(player)) return;
        long buyIn = request.baseAmount();
        if (buyIn <= 0) { CasinoEngine.errorState(player, "Укажите бай-ин больше нуля."); return; }
        String code = code();
        PokerRoom room = new PokerRoom(code, buyIn, player);
        room.seats.get(0).lastSequence = request.sequence;
        POKER.put(code, room);
        MEMBERSHIP.put(CasinoData.id(player), new RoomRef("poker", code));
        CasinoStats.add(player, "poker_rooms_created", 1);
        room.broadcast("§aСоздана покерная комната §f" + code + "§a. Передайте код друзьям.");
    }

    private static void joinPoker(Object player, CasinoRequest request) {
        String code = normalizeCode(request.text);
        PokerRoom room = POKER.get(code);
        if (room == null) { CasinoEngine.errorState(player, "Покерная комната с кодом " + code + " не найдена."); return; }
        if (!canCreate(player)) return;
        PokerSeat seat = room.join(player);
        if (seat == null) return;
        seat.lastSequence = request.sequence;
        MEMBERSHIP.put(CasinoData.id(player), new RoomRef("poker", code));
        room.broadcast("§a" + playerName(player) + " вошёл в покерную комнату.");
    }

    private static void createDurak(Object player, CasinoRequest request) {
        if (!canCreate(player)) return;
        long stake = Math.max(0, request.baseAmount());
        String code = code();
        DurakRoom room = new DurakRoom(code, stake, player);
        room.seats.get(0).lastSequence = request.sequence;
        DURAK.put(code, room);
        MEMBERSHIP.put(CasinoData.id(player), new RoomRef("durak", code));
        room.broadcast("§aСоздана комната дурака §f" + code + "§a. Передайте код друзьям.");
    }

    private static void joinDurak(Object player, CasinoRequest request) {
        String code = normalizeCode(request.text);
        DurakRoom room = DURAK.get(code);
        if (room == null) { CasinoEngine.errorState(player, "Комната дурака с кодом " + code + " не найдена."); return; }
        if (!canCreate(player)) return;
        DurakSeat seat = room.join(player);
        if (seat == null) return;
        seat.lastSequence = request.sequence;
        MEMBERSHIP.put(CasinoData.id(player), new RoomRef("durak", code));
        room.broadcast("§a" + playerName(player) + " вошёл в комнату дурака.");
    }

    private static boolean canCreate(Object player) {
        String id = CasinoData.id(player);
        RoomRef ref = MEMBERSHIP.get(id);
        if (ref == null) return true;
        Room<?> room = ref.type.equals("poker") ? POKER.get(ref.code) : DURAK.get(ref.code);
        if (room != null && room.started()) {
            CasinoEngine.errorState(player, "Сначала завершите активную сетевую партию.");
            return false;
        }
        if (room != null) room.detachForSwitch(player);
        else MEMBERSHIP.remove(id);
        return true;
    }

    private static PokerRoom pokerRoom(Object player) {
        RoomRef ref = MEMBERSHIP.get(CasinoData.id(player));
        return ref != null && ref.type.equals("poker") ? POKER.get(ref.code) : null;
    }

    private static DurakRoom durakRoom(Object player) {
        RoomRef ref = MEMBERSHIP.get(CasinoData.id(player));
        return ref != null && ref.type.equals("durak") ? DURAK.get(ref.code) : null;
    }

    private static String code() {
        String code;
        do {
            StringBuilder b = new StringBuilder(5);
            for (int i = 0; i < 5; i++) b.append(CODE_CHARS.charAt(ThreadLocalRandom.current().nextInt(CODE_CHARS.length())));
            code = b.toString();
        } while (POKER.containsKey(code) || DURAK.containsKey(code));
        return code;
    }

    private static String normalizeCode(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    static String playerName(Object player) {
        try {
            Object profile = Reflect.invoke(player, "method_7334");
            return String.valueOf(Reflect.invoke(profile, "getName"));
        } catch (Throwable ignored) {
            return "Игрок";
        }
    }

    private record RoomRef(String type, String code) {}

    private abstract static class Room<S extends Seat> {
        final String code;
        final long stake;
        final List<S> seats = new ArrayList<>();
        String hostId;
        String lastEvent = "";
        long revision = 1;
        long lastSyncEpochMs;
        boolean suppressNetwork;

        Room(String code, long stake, Object host) {
            this.code = code;
            this.stake = stake;
            this.hostId = CasinoData.id(host);
        }

        abstract String type();
        abstract int maxPlayers();
        abstract boolean started();
        abstract CasinoViewState view(Object player);
        abstract void action(Object player, CasinoRequest request);
        abstract S newSeat(Object player);

        S join(Object player) {
            if (started()) { CasinoEngine.errorState(player, "Партия уже началась."); return null; }
            String id = CasinoData.id(player);
            S existing = seatById(id);
            if (existing != null) {
                existing.player = player;
                existing.connected = true;
                return existing;
            }
            if (seats.size() >= maxPlayers()) { CasinoEngine.errorState(player, "Комната заполнена."); return null; }
            S seat = newSeat(player);
            seats.add(seat);
            return seat;
        }

        S seat(Object player) {
            S seat = seatById(CasinoData.id(player));
            if (seat != null) {
                seat.player = player;
                seat.connected = true;
            }
            return seat;
        }

        S seatById(String id) { for (S s : seats) if (s.id.equals(id)) return s; return null; }
        boolean isHost(Object player) { return hostId.equals(CasinoData.id(player)); }

        boolean beginRequest(Object player, CasinoRequest request, boolean requireFreshRevision) {
            S seat = seat(player);
            if (seat == null) { CasinoEngine.errorState(player, "Игрок не найден в комнате."); return false; }
            if (request.sequence > 0 && request.sequence <= seat.lastSequence) {
                sendSnapshot(player, "", "duplicate_request");
                return false;
            }
            seat.lastSequence = Math.max(seat.lastSequence, request.sequence);
            if (requireFreshRevision && request.revision != revision) {
                reject(player, "stale_state", "Состояние стола изменилось. Интерфейс синхронизирован повторно.");
                return false;
            }
            return true;
        }

        void toggleReady(Object player) {
            S seat = seat(player);
            if (seat == null || started()) return;
            seat.ready = !seat.ready;
            broadcast("§e" + seat.name + (seat.ready ? " готов." : " больше не готов."));
        }

        void detachForSwitch(Object player) {
            String id = CasinoData.id(player);
            S seat = seatById(id);
            if (seat == null) { MEMBERSHIP.remove(id); return; }
            seats.remove(seat);
            MEMBERSHIP.remove(id);
            if (seats.isEmpty()) {
                if (type().equals("poker")) POKER.remove(code); else DURAK.remove(code);
                return;
            }
            if (hostId.equals(id)) hostId = seats.get(0).id;
            broadcast("§e" + seat.name + " перешёл в другую игровую комнату.");
        }

        void leaveLobby(Object player) {
            if (started()) { reject(player, "game_active", "Во время партии выход недоступен: сначала завершите игру."); return; }
            String id = CasinoData.id(player);
            S seat = seatById(id);
            if (seat == null) return;
            seats.remove(seat);
            MEMBERSHIP.remove(id);
            CasinoViewState state = CasinoEngine.baseState(player);
            state.game = type().equals("poker") ? "Покер" : "Дурак";
            state.result = "§eВы вышли из комнаты.";
            state.ackSequence = seat.lastSequence;
            CasinoEngine.sendState(player, state);
            if (seats.isEmpty()) {
                if (type().equals("poker")) POKER.remove(code); else DURAK.remove(code);
                return;
            }
            if (hostId.equals(id)) hostId = seats.get(0).id;
            broadcast("§e" + seat.name + " вышел из комнаты.");
        }

        boolean allReady() {
            if (seats.size() < 2) return false;
            for (S s : seats) if (!s.ready) return false;
            return true;
        }

        boolean collectStakes() {
            for (S s : seats) if (CasinoEngine.wallet(s.player) < stake) {
                broadcast("§cУ " + s.name + " недостаточно средств. Нужно: §f" + CasinoEngine.money(stake));
                return false;
            }
            List<S> paid = new ArrayList<>();
            for (S s : seats) {
                if (!CasinoData.beginEscrow(s.player, stake)) {
                    for (S p : paid) CasinoData.refundEscrow(p.player);
                    broadcast("§cНе удалось безопасно собрать ставки. Списания отменены.");
                    return false;
                }
                paid.add(s);
            }
            return true;
        }

        String playersText() {
            StringBuilder b = new StringBuilder();
            for (S s : seats) {
                if (b.length() > 0) b.append('|');
                b.append(s.id.equals(hostId) ? "★ " : "").append(s.name);
                if (!started()) b.append(s.ready ? " §a[готов]" : " §7[не готов]");
                b.append(extraPlayerStatus(s));
            }
            return b.toString();
        }

        String extraPlayerStatus(S seat) { return ""; }

        void reject(Object player, String code, String message) {
            sendSnapshot(player, "§c" + message, code);
        }

        void sendSnapshot(Object player, String message, String errorCode) {
            if (suppressNetwork) return;
            CasinoViewState state = view(player);
            if (message != null && !message.isBlank()) state.result = message;
            state.errorCode = errorCode == null ? "" : errorCode;
            S seat = seat(player);
            if (seat != null) state.ackSequence = seat.lastSequence;
            CasinoEngine.sendState(player, state);
        }

        void broadcast(String event) {
            revision++;
            lastEvent = event == null ? "" : event;
            if (suppressNetwork) return;
            for (S s : new ArrayList<>(seats)) {
                try {
                    CasinoViewState state = view(s.player);
                    state.event = lastEvent;
                    state.revision = revision;
                    state.ackSequence = s.lastSequence;
                    CasinoEngine.sendState(s.player, state);
                } catch (Throwable e) {
                    System.err.println("[AstraTerra Casino] Could not update room member " + s.name);
                    e.printStackTrace();
                }
            }
        }

        void syncIfDue(long now) {
            if (suppressNetwork || now - lastSyncEpochMs < 1_000L) return;
            lastSyncEpochMs = now;
            for (S s : new ArrayList<>(seats)) {
                if (!s.connected || s.player == null) continue;
                try {
                    CasinoViewState state = view(s.player);
                    state.event = "";
                    state.revision = revision;
                    state.ackSequence = s.lastSequence;
                    CasinoEngine.sendState(s.player, state);
                } catch (Throwable e) {
                    System.err.println("[AstraTerra Casino] Periodic room sync failed for " + s.name);
                    e.printStackTrace();
                }
            }
        }
    }

    private abstract static class Seat {
        final String id;
        final String name;
        Object player;
        boolean ready;
        boolean connected = true;
        long lastSequence;

        Seat(Object player) {
            this.id = CasinoData.id(player);
            this.name = playerName(player);
            this.player = player;
        }
    }

    static final class PokerSeat extends Seat {
        long stack;
        long streetBet;
        long handContribution;
        boolean folded;
        boolean allIn;
        boolean needsAction;
        boolean raiseLocked;
        String revealedCards = "";
        final List<Integer> hole = new ArrayList<>(2);
        PokerSeat(Object player) { super(player); }
    }

    static final class PokerRoom extends Room<PokerSeat> {
        PokerPhase phase = PokerPhase.WAITING_FOR_PLAYERS;
        boolean tournamentStarted;
        int dealerIndex = -1;
        int smallBlindIndex = -1;
        int bigBlindIndex = -1;
        int currentIndex = -1;
        long pot;
        long currentBet;
        long lastFullRaise;
        long smallBlind;
        long bigBlind;
        long totalBank;
        long handId;
        List<Integer> deck = new ArrayList<>();
        final List<Integer> board = new ArrayList<>(5);
        String handResult = "Ожидание игроков.";
        String lastAction = "";
        String sidePotSummary = "";
        long turnDeadlineEpochMs;
        static final long TURN_TIME_MS = 45_000L;
        static final long DISCONNECT_GRACE_MS = 10_000L;

        PokerRoom(String code, long buyIn, Object host) {
            super(code, buyIn, host);
            seats.add(new PokerSeat(host));
            smallBlind = Math.max(1, buyIn / 50L);
            bigBlind = Math.max(2, smallBlind * 2L);
        }

        @Override String type() { return "poker"; }
        @Override int maxPlayers() { return 6; }
        @Override boolean started() { return tournamentStarted; }
        @Override PokerSeat newSeat(Object player) { return new PokerSeat(player); }

        @Override void action(Object player, CasinoRequest request) {
            if (!beginRequest(player, request, true)) return;
            switch (request.action) {
                case "poker_ready" -> toggleReady(player);
                case "poker_leave" -> leaveLobby(player);
                case "poker_start" -> startTournament(player);
                case "poker_fold" -> act(player, PokerAction.FOLD, 0);
                case "poker_check" -> act(player, PokerAction.CHECK, 0);
                case "poker_call" -> act(player, PokerAction.CALL, 0);
                case "poker_bet", "poker_raise_to" -> act(player, currentBet == 0 ? PokerAction.BET : PokerAction.RAISE_TO, request.baseAmount());
                case "poker_allin" -> act(player, PokerAction.ALL_IN, 0);
                case "poker_next" -> nextHand(player);
                case "poker_sync" -> sendSnapshot(player, "", "");
                default -> reject(player, "unknown_action", "Неизвестное действие покера.");
            }
        }

        private void startTournament(Object player) {
            if (!isHost(player)) { reject(player, "host_only", "Начать турнир может только создатель комнаты."); return; }
            if (tournamentStarted) { reject(player, "already_started", "Турнир уже идёт."); return; }
            if (!allReady()) { reject(player, "not_ready", "Для старта нужно 2–6 готовых игроков."); return; }
            if (!collectStakes()) return;
            tournamentStarted = true;
            phase = PokerPhase.STARTING_HAND;
            totalBank = safeMultiply(stake, seats.size());
            for (PokerSeat s : seats) {
                s.stack = stake;
                s.ready = false;
                CasinoStats.add(s.player, "poker_games", 1);
            }
            startHand();
        }

        private void nextHand(Object player) {
            if (!isHost(player)) { reject(player, "host_only", "Следующую раздачу запускает создатель комнаты."); return; }
            if (!tournamentStarted || phase != PokerPhase.BETWEEN_HANDS) {
                reject(player, "hand_not_ready", "Следующая раздача пока недоступна.");
                return;
            }
            startHand();
        }

        void startHand() {
            List<Integer> active = activeIndices();
            if (active.size() <= 1) { finishTournament(); return; }
            handId++;
            phase = PokerPhase.STARTING_HAND;
            deck = Cards.pokerDeck();
            board.clear();
            pot = 0;
            currentBet = 0;
            lastFullRaise = bigBlind;
            sidePotSummary = "";
            for (PokerSeat s : seats) {
                s.hole.clear();
                s.streetBet = 0;
                s.handContribution = 0;
                s.folded = s.stack <= 0;
                s.allIn = s.stack <= 0;
                s.needsAction = false;
                s.raiseLocked = false;
                s.revealedCards = "";
                if (s.stack > 0) CasinoStats.add(s.player, "poker_hands", 1);
            }

            dealerIndex = nextWithStack(dealerIndex);
            if (active.size() == 2) {
                smallBlindIndex = dealerIndex;
                bigBlindIndex = nextWithStack(dealerIndex);
            } else {
                smallBlindIndex = nextWithStack(dealerIndex);
                bigBlindIndex = nextWithStack(smallBlindIndex);
            }

            int dealStart = active.size() == 2 ? dealerIndex : smallBlindIndex;
            for (int round = 0; round < 2; round++) {
                for (int idx : orderedFrom(dealStart)) seats.get(idx).hole.add(draw());
            }

            postBlind(seats.get(smallBlindIndex), smallBlind);
            postBlind(seats.get(bigBlindIndex), bigBlind);
            currentBet = Math.max(seats.get(smallBlindIndex).streetBet, seats.get(bigBlindIndex).streetBet);
            for (PokerSeat s : seats) if (!s.folded && !s.allIn) {
                s.needsAction = true;
                s.raiseLocked = false;
            }

            phase = PokerPhase.PREFLOP;
            currentIndex = active.size() == 2 ? dealerIndex : nextPending(bigBlindIndex);
            armTurnTimer();
            handResult = "§eИдёт торговля до флопа.";
            lastAction = "Новая раздача";
            if (actionableCount() <= 1 || currentIndex < 0) runToShowdown();
            else broadcast("§6Новая раздача. Блайнды: §f" + CasinoEngine.money(smallBlind) + " / " + CasinoEngine.money(bigBlind));
        }

        private void postBlind(PokerSeat seat, long value) {
            long paid = Math.min(value, seat.stack);
            contribute(seat, paid);
            if (seat.stack == 0) seat.allIn = true;
        }

        void act(Object player, PokerAction action, long target) {
            if (!phase.betting()) { reject(player, "no_betting", "Сейчас нет активной торговли."); return; }
            PokerSeat seat = seat(player);
            int index = seat == null ? -1 : seats.indexOf(seat);
            if (seat == null || index != currentIndex) { reject(player, "not_your_turn", "Сейчас ход другого игрока."); return; }
            if (seat.folded || seat.allIn || !seat.needsAction) { reject(player, "cannot_act", "Вы сейчас не можете выполнить это действие."); return; }

            long toCall = Math.max(0, currentBet - seat.streetBet);
            switch (action) {
                case FOLD -> {
                    seat.folded = true;
                    seat.needsAction = false;
                    lastAction = seat.name + " — пас";
                    handResult = "§c" + seat.name + " сбросил карты.";
                    CasinoStats.add(seat.player, "poker_folds", 1);
                }
                case CHECK -> {
                    if (toCall != 0) { reject(player, "check_requires_call", "Чек невозможен: нужно уравнять " + CasinoEngine.money(toCall) + "."); return; }
                    seat.needsAction = false;
                    lastAction = seat.name + " — чек";
                    handResult = "§a" + seat.name + " сказал чек.";
                    CasinoStats.add(seat.player, "poker_checks", 1);
                }
                case CALL -> {
                    if (toCall <= 0) { reject(player, "nothing_to_call", "Уравнивать нечего: доступен чек."); return; }
                    long paid = Math.min(toCall, seat.stack);
                    contribute(seat, paid);
                    seat.needsAction = false;
                    if (seat.stack == 0) seat.allIn = true;
                    lastAction = seat.name + " — колл " + CasinoEngine.money(paid);
                    handResult = "§a" + seat.name + " уравнял " + CasinoEngine.money(paid) + (seat.allIn ? " и пошёл all-in." : ".");
                    CasinoStats.add(seat.player, "poker_calls", 1);
                    if (seat.allIn) CasinoStats.add(seat.player, "poker_allins", 1);
                }
                case BET, RAISE_TO -> {
                    if (seat.raiseLocked) { reject(player, "raise_not_reopened", "Повышение не переоткрыто после неполного all-in; доступны только колл или пас."); return; }
                    if (!raiseTo(seat, target, false)) return;
                    CasinoStats.add(seat.player, "poker_raises", 1);
                }
                case ALL_IN -> {
                    long allInTarget = safeAdd(seat.streetBet, seat.stack);
                    if (seat.raiseLocked && allInTarget > currentBet) {
                        reject(player, "raise_not_reopened", "Повышение не переоткрыто; all-in доступен только как колл.");
                        return;
                    }
                    if (!raiseTo(seat, allInTarget, true)) return;
                    CasinoStats.add(seat.player, "poker_allins", 1);
                }
            }
            advanceBetting();
        }

        private boolean raiseTo(PokerSeat seat, long requestedTarget, boolean allInCommand) {
            long maximum = safeAdd(seat.streetBet, seat.stack);
            if (maximum <= seat.streetBet) { reject(seat.player, "no_chips", "У вас нет фишек для ставки."); return false; }
            long target = allInCommand ? maximum : requestedTarget;
            if (target <= seat.streetBet) { reject(seat.player, "invalid_target", "Сумма должна быть больше вашего текущего вклада."); return false; }
            if (target > maximum) { reject(seat.player, "insufficient_stack", "Максимальная ставка до: " + CasinoEngine.money(maximum)); return false; }

            long oldCurrent = currentBet;
            long minimumTarget = minimumRaiseTarget();
            boolean raises = target > oldCurrent;
            boolean fullRaise = raises && target >= minimumTarget;
            boolean shortAllIn = raises && target == maximum && !fullRaise;

            if (raises && !fullRaise && !shortAllIn) {
                reject(seat.player, "raise_too_small", "Минимальная ставка до: " + CasinoEngine.money(minimumTarget));
                return false;
            }
            if (!raises && !allInCommand) {
                reject(seat.player, "use_call", "Для уравнивания используйте кнопку «Колл».");
                return false;
            }

            long paid = target - seat.streetBet;
            contribute(seat, paid);
            seat.needsAction = false;
            if (seat.stack == 0) seat.allIn = true;

            if (target > oldCurrent) {
                long raiseSize = target - oldCurrent;
                currentBet = target;
                if (fullRaise) {
                    lastFullRaise = oldCurrent == 0 ? target : raiseSize;
                    for (PokerSeat other : seats) if (other != seat && eligible(other)) {
                        other.needsAction = true;
                        other.raiseLocked = false;
                    }
                } else {
                    for (PokerSeat other : seats) if (other != seat && eligible(other) && other.streetBet < currentBet) {
                        if (!other.needsAction) other.raiseLocked = true;
                        other.needsAction = true;
                    }
                }
            }

            String verb = oldCurrent == 0 ? "поставил до" : target > oldCurrent ? "повысил до" : target < oldCurrent ? "пошёл all-in на" : "уравнял до";
            lastAction = seat.name + " — " + verb + " " + CasinoEngine.money(target) + (seat.allIn ? " (all-in)" : "");
            handResult = "§d" + seat.name + " " + verb + " " + CasinoEngine.money(target) + (seat.allIn ? " и пошёл all-in." : ".");
            return true;
        }

        private void contribute(PokerSeat seat, long amount) {
            if (amount <= 0) return;
            long paid = Math.min(amount, seat.stack);
            seat.stack -= paid;
            seat.streetBet = safeAdd(seat.streetBet, paid);
            seat.handContribution = safeAdd(seat.handContribution, paid);
            pot = safeAdd(pot, paid);
        }

        private void armTurnTimer() {
            turnDeadlineEpochMs = currentIndex >= 0 && phase.betting() ? System.currentTimeMillis() + TURN_TIME_MS : 0L;
        }

        private void onDisconnect(Seat disconnectedSeat) {
            if (!(disconnectedSeat instanceof PokerSeat seat)) return;
            if (phase.betting() && seats.indexOf(seat) == currentIndex) {
                turnDeadlineEpochMs = Math.min(turnDeadlineEpochMs == 0 ? Long.MAX_VALUE : turnDeadlineEpochMs,
                    System.currentTimeMillis() + DISCONNECT_GRACE_MS);
            }
        }

        void tick(long now) {
            if (!phase.betting() || currentIndex < 0 || currentIndex >= seats.size() || turnDeadlineEpochMs <= 0 || now < turnDeadlineEpochMs) return;
            PokerSeat seat = seats.get(currentIndex);
            long toCall = Math.max(0, currentBet - seat.streetBet);
            String reason = seat.connected ? "время хода истекло" : "игрок отключён";
            if (toCall == 0) {
                lastAction = seat.name + " — авто-чек (" + reason + ")";
                act(seat.player, PokerAction.CHECK, 0);
            } else {
                lastAction = seat.name + " — авто-пас (" + reason + ")";
                act(seat.player, PokerAction.FOLD, 0);
            }
        }

        private void advanceBetting() {
            List<PokerSeat> alive = notFolded();
            if (alive.size() == 1) { awardUncontested(alive.get(0)); return; }
            if (roundComplete()) { nextStreet(); return; }
            currentIndex = nextPending(currentIndex);
            armTurnTimer();
            if (currentIndex < 0) runToShowdown(); else broadcast(handResult);
        }

        private boolean roundComplete() {
            for (PokerSeat s : seats) if (eligible(s) && (s.needsAction || s.streetBet != currentBet)) return false;
            return true;
        }

        private void nextStreet() {
            for (PokerSeat s : seats) {
                s.streetBet = 0;
                s.needsAction = eligible(s);
                s.raiseLocked = false;
            }
            currentBet = 0;
            lastFullRaise = bigBlind;
            switch (phase) {
                case PREFLOP -> { board.add(draw()); board.add(draw()); board.add(draw()); phase = PokerPhase.FLOP; }
                case FLOP -> { board.add(draw()); phase = PokerPhase.TURN; }
                case TURN -> { board.add(draw()); phase = PokerPhase.RIVER; }
                case RIVER -> { showdown(); return; }
                default -> { return; }
            }
            currentIndex = nextPending(dealerIndex);
            armTurnTimer();
            lastAction = "Открыта улица: " + phase.label;
            if (actionableCount() <= 1 || currentIndex < 0) runToShowdown();
            else broadcast("§6Открыта улица: §f" + phase.label + ".");
        }

        private void runToShowdown() {
            while (board.size() < 5) board.add(draw());
            showdown();
        }

        private void showdown() {
            phase = PokerPhase.SHOWDOWN;
            Map<PokerSeat, Long> scores = new HashMap<>();
            for (PokerSeat s : seats) if (!s.folded) {
                s.revealedCards = Cards.csvPoker(s.hole);
                List<Integer> seven = new ArrayList<>(board);
                seven.addAll(s.hole);
                scores.put(s, PokerHand.best(seven));
            }

            long originalPot = pot;
            List<PotTier> tiers = buildPotTiers();
            LinkedHashSet<PokerSeat> allWinners = new LinkedHashSet<>();
            LinkedHashSet<PokerSeat> sidePotWinners = new LinkedHashSet<>();
            List<String> summaries = new ArrayList<>();
            for (int tierIndex = 0; tierIndex < tiers.size(); tierIndex++) {
                PotTier tier = tiers.get(tierIndex);
                long best = Long.MIN_VALUE;
                List<PokerSeat> winners = new ArrayList<>();
                for (PokerSeat s : tier.eligible) {
                    long score = scores.getOrDefault(s, Long.MIN_VALUE);
                    if (score > best) { best = score; winners.clear(); winners.add(s); }
                    else if (score == best) winners.add(s);
                }
                if (winners.isEmpty()) {
                    List<PokerSeat> contributors = tier.contributors;
                    if (!contributors.isEmpty()) {
                        long share = tier.amount / contributors.size();
                        long remainder = tier.amount % contributors.size();
                        contributors = clockwiseAfterDealer(new ArrayList<>(contributors));
                        for (int i = 0; i < contributors.size(); i++) contributors.get(i).stack = safeAdd(contributors.get(i).stack, share + (i < remainder ? 1 : 0));
                    }
                    continue;
                }
                winners = clockwiseAfterDealer(winners);
                long share = tier.amount / winners.size();
                long remainder = tier.amount % winners.size();
                for (int i = 0; i < winners.size(); i++) winners.get(i).stack = safeAdd(winners.get(i).stack, share + (i < remainder ? 1 : 0));
                if (tierIndex > 0) sidePotWinners.addAll(winners);
                allWinners.addAll(winners);
                String label = tierIndex == 0 ? "Основной" : "Побочный " + tierIndex;
                summaries.add(label + ": " + CasinoEngine.money(tier.amount) + " → " + joinNames(winners));
            }
            sidePotSummary = String.join(" | ", summaries);
            String names = joinNames(new ArrayList<>(allWinners));
            long topScore = Long.MIN_VALUE;
            for (PokerSeat winner : allWinners) topScore = Math.max(topScore, scores.getOrDefault(winner, Long.MIN_VALUE));
            handResult = "§6Вскрытие: §f" + names + " §6— " + PokerHand.name(topScore) + ", банк " + CasinoEngine.money(originalPot) + ".";
            lastAction = "Вскрытие и распределение банка";
            for (PokerSeat winner : allWinners) {
                CasinoStats.add(winner.player, "poker_showdown_wins", 1);
                CasinoStats.add(winner.player, "poker_hands_won", 1);
            }
            for (PokerSeat winner : sidePotWinners) CasinoStats.add(winner.player, "poker_sidepot_wins", 1);
            recordHandStatistics(allWinners, originalPot);
            pot = 0;
            afterHand();
        }

        List<PotTier> buildPotTiers() {
            TreeSet<Long> levels = new TreeSet<>();
            for (PokerSeat s : seats) if (s.handContribution > 0) levels.add(s.handContribution);
            List<PotTier> result = new ArrayList<>();
            long previous = 0;
            for (long level : levels) {
                List<PokerSeat> contributors = new ArrayList<>();
                List<PokerSeat> eligible = new ArrayList<>();
                for (PokerSeat s : seats) {
                    if (s.handContribution >= level) contributors.add(s);
                    if (!s.folded && s.handContribution >= level) eligible.add(s);
                }
                long amount = safeMultiply(level - previous, contributors.size());
                previous = level;
                if (amount > 0) result.add(new PotTier(amount, eligible, contributors));
            }
            return result;
        }

        private String previewSidePots() {
            List<PotTier> tiers = buildPotTiers();
            if (tiers.size() <= 1) return "";
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < tiers.size(); i++) {
                if (b.length() > 0) b.append(" | ");
                b.append(i == 0 ? "Основной" : "Побочный " + i).append(": ").append(CasinoEngine.money(tiers.get(i).amount));
            }
            return b.toString();
        }

        private void awardUncontested(PokerSeat winner) {
            winner.stack = safeAdd(winner.stack, pot);
            handResult = "§a" + winner.name + " забирает банк " + CasinoEngine.money(pot) + " без вскрытия.";
            lastAction = winner.name + " выиграл без вскрытия";
            CasinoStats.add(winner.player, "poker_no_showdown_wins", 1);
            CasinoStats.add(winner.player, "poker_hands_won", 1);
            recordHandStatistics(List.of(winner), pot);
            pot = 0;
            afterHand();
        }

        private void recordHandStatistics(Collection<PokerSeat> winners, long handPot) {
            if (suppressNetwork) return;
            for (PokerSeat seat : seats) {
                if (seat.handContribution > 0) {
                    int silver = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, seat.handContribution / 100L));
                    CasinoStats.add(seat.player, "poker_turnover_silver", silver);
                }
            }
            int potSilver = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, handPot / 100L));
            for (PokerSeat winner : winners) {
                long previous = CasinoData.get(winner.player, "poker_largest_pot_silver", 0);
                if (potSilver > previous && CasinoData.set(winner.player, "poker_largest_pot_silver", potSilver)) {
                    CasinoStats.add(winner.player, "poker_largest_pot_silver", (int) Math.min(Integer.MAX_VALUE, potSilver - previous));
                }
            }
        }

        private void afterHand() {
            if (activeIndices().size() <= 1) finishTournament();
            else {
                phase = PokerPhase.BETWEEN_HANDS;
                currentIndex = -1;
                broadcast(handResult + " §7Создатель комнаты может запустить следующую раздачу.");
            }
        }

        private void finishTournament() {
            PokerSeat winner = null;
            for (PokerSeat s : seats) if (s.stack > 0) { winner = s; break; }
            if (winner == null && !seats.isEmpty()) winner = seats.get(0);
            long payout = 0;
            for (PokerSeat s : seats) payout = safeAdd(payout, s.stack);
            if (payout <= 0) payout = totalBank;
            Map<String, Long> payouts = new HashMap<>();
            if (winner != null) payouts.put(winner.id, payout);
            if (!CasinoData.settleEscrows(seats.stream().map(s -> s.player).toList(), payouts)) {
                broadcast("§cНе удалось зафиксировать выплаты турнира. Эскроу сохранено для восстановления.");
                return;
            }
            if (winner != null) {
                CasinoStats.add(winner.player, "poker_wins", 1);
                CasinoStats.add(winner.player, "poker_tournaments_won", 1);
            }
            for (PokerSeat s : seats) CasinoStats.result(s.player, s == winner, stake, s == winner ? payout - stake : -stake);
            phase = PokerPhase.TOURNAMENT_FINISHED;
            tournamentStarted = false;
            handResult = winner == null ? "§eТурнир завершён." : "§6Победитель турнира: §f" + winner.name + "§6. Выплата: §f" + CasinoEngine.money(payout);
            lastAction = "Турнир завершён";
            for (PokerSeat s : seats) { s.ready = false; s.stack = 0; s.needsAction = false; }
            broadcast(handResult);
        }

        private long minimumRaiseTarget() {
            return currentBet == 0 ? bigBlind : safeAdd(currentBet, Math.max(1, lastFullRaise));
        }

        private int draw() { return deck.remove(deck.size() - 1); }
        private boolean eligible(PokerSeat s) { return !s.folded && !s.allIn && s.stack > 0; }
        private int actionableCount() { int c = 0; for (PokerSeat s : seats) if (eligible(s)) c++; return c; }
        private List<Integer> activeIndices() { List<Integer> r = new ArrayList<>(); for (int i = 0; i < seats.size(); i++) if (seats.get(i).stack > 0) r.add(i); return r; }
        private int nextWithStack(int from) { for (int n = 1; n <= seats.size(); n++) { int i = (from + n + seats.size()) % seats.size(); if (seats.get(i).stack > 0) return i; } return -1; }
        private List<Integer> orderedFrom(int start) { List<Integer> r = new ArrayList<>(); if (start < 0) return r; int i = start; for (int n = 0; n < seats.size(); n++) { if (seats.get(i).stack > 0) r.add(i); i = (i + 1) % seats.size(); } return r; }
        private int nextPending(int from) { for (int n = 1; n <= seats.size(); n++) { int i = (from + n + seats.size()) % seats.size(); PokerSeat s = seats.get(i); if (eligible(s) && s.needsAction) return i; } return -1; }
        private List<PokerSeat> notFolded() { List<PokerSeat> r = new ArrayList<>(); for (PokerSeat s : seats) if (!s.folded) r.add(s); return r; }
        private String joinNames(List<PokerSeat> list) { StringBuilder b = new StringBuilder(); for (PokerSeat s : list) { if (b.length() > 0) b.append(", "); b.append(s.name); } return b.toString(); }
        private List<PokerSeat> clockwiseAfterDealer(List<PokerSeat> winners) {
            winners.sort(Comparator.comparingInt(s -> (seats.indexOf(s) - dealerIndex - 1 + seats.size() * 2) % seats.size()));
            return winners;
        }

        String allowedActions(PokerSeat me) {
            if (me == null || !phase.betting() || seats.indexOf(me) != currentIndex || !eligible(me) || !me.needsAction) return "";
            List<String> actions = new ArrayList<>();
            actions.add(PokerAction.FOLD.id);
            long call = Math.max(0, currentBet - me.streetBet);
            if (call == 0) actions.add(PokerAction.CHECK.id); else actions.add(PokerAction.CALL.id);
            long max = safeAdd(me.streetBet, me.stack);
            if (me.stack > 0 && (!me.raiseLocked || max <= currentBet)) actions.add(PokerAction.ALL_IN.id);
            if (!me.raiseLocked && max >= minimumRaiseTarget()) actions.add(currentBet == 0 ? PokerAction.BET.id : PokerAction.RAISE_TO.id);
            return String.join(",", actions);
        }

        @Override String extraPlayerStatus(PokerSeat s) {
            if (!tournamentStarted) return "";
            String status = s.folded ? " §c[пас]" : s.allIn ? " §d[all-in]" : seats.indexOf(s) == currentIndex ? " §e[ход]" : "";
            String marker = seats.indexOf(s) == dealerIndex ? " §6[D]" : seats.indexOf(s) == smallBlindIndex ? " §e[SB]" : seats.indexOf(s) == bigBlindIndex ? " §c[BB]" : "";
            return " §7— стек " + CasinoEngine.money(s.stack) + ", улица " + CasinoEngine.money(s.streetBet) + marker + status;
        }

        @Override CasinoViewState view(Object player) {
            PokerSeat me = seat(player);
            CasinoViewState state = CasinoEngine.baseState(player);
            state.game = "Покер с друзьями";
            state.phase = phase.label;
            state.roomType = "poker";
            state.roomCode = code;
            state.roomPlayers = playersText();
            state.roomStatus = handResult;
            state.roomStake = stake;
            state.roomHost = isHost(player);
            state.roomReady = me != null && me.ready;
            state.multiplayerActive = tournamentStarted;
            state.handCards = me == null ? "" : Cards.csvPoker(me.hole);
            state.boardCards = Cards.csvPoker(board);
            state.stack = me == null ? 0 : me.stack;
            state.pot = pot;
            state.currentBet = currentBet;
            state.toCall = me == null ? 0 : Math.max(0, currentBet - me.streetBet);
            state.minRaise = minimumRaiseTarget();
            state.maxRaise = me == null ? 0 : safeAdd(me.streetBet, me.stack);
            state.streetContribution = me == null ? 0 : me.streetBet;
            state.handContribution = me == null ? 0 : me.handContribution;
            state.currentTurn = currentIndex >= 0 ? seats.get(currentIndex).name : "—";
            state.role = me == null ? "Наблюдатель" : seats.indexOf(me) == currentIndex ? "Ваш ход" : me.folded ? "Пас" : me.allIn ? "All-in" : "Ожидание";
            state.allowedActions = allowedActions(me);
            state.canAct = !state.allowedActions.isBlank();
            state.sidePots = sidePotSummary.isBlank() ? previewSidePots() : sidePotSummary;
            state.dealerName = dealerIndex >= 0 ? seats.get(dealerIndex).name : "—";
            state.smallBlindName = smallBlindIndex >= 0 ? seats.get(smallBlindIndex).name : "—";
            state.bigBlindName = bigBlindIndex >= 0 ? seats.get(bigBlindIndex).name : "—";
            state.lastAction = lastAction;
            state.result = handResult;
            state.bet = stake;
            state.revision = revision;
            state.handId = handId;
            state.ackSequence = me == null ? 0 : me.lastSequence;
            state.turnDeadlineEpochMs = turnDeadlineEpochMs;
            for (int i = 0; i < seats.size(); i++) {
                PokerSeat seat = seats.get(i);
                CasinoPlayerView playerView = new CasinoPlayerView();
                playerView.id = seat.id;
                playerView.name = seat.name;
                playerView.stack = seat.stack;
                playerView.streetContribution = seat.streetBet;
                playerView.handContribution = seat.handContribution;
                playerView.local = seat == me;
                playerView.host = seat.id.equals(hostId);
                playerView.ready = seat.ready;
                playerView.connected = seat.connected;
                playerView.activeTurn = i == currentIndex;
                playerView.dealer = i == dealerIndex;
                playerView.smallBlind = i == smallBlindIndex;
                playerView.bigBlind = i == bigBlindIndex;
                playerView.folded = seat.folded;
                playerView.allIn = seat.allIn;
                playerView.cards = seat == me ? Cards.csvPoker(seat.hole) : seat.revealedCards;
                playerView.status = !seat.connected ? "отключён" : seat.folded ? "пас" : seat.allIn ? "all-in" : i == currentIndex ? "ходит" : !tournamentStarted ? (seat.ready ? "готов" : "не готов") : "ожидает";
                state.players.add(playerView);
            }
            return state;
        }

        record PotTier(long amount, List<PokerSeat> eligible, List<PokerSeat> contributors) {}
    }

    static final class DurakSeat extends Seat {
        final List<Integer> hand = new ArrayList<>();
        boolean out;
        DurakSeat(Object player) { super(player); }
    }

    private static final class AttackPair {
        final int attack;
        Integer defense;
        AttackPair(int attack) { this.attack = attack; }
    }

    static final class DurakRoom extends Room<DurakSeat> {
        boolean gameStarted;
        String stage = "Лобби";
        List<Integer> deck = new ArrayList<>();
        int trumpCard = -1;
        int trumpSuit = -1;
        int attacker = -1;
        int defender = -1;
        int maxAttack = 6;
        final List<AttackPair> table = new ArrayList<>();
        String gameResult = "Ожидание игроков.";
        boolean takingCards;
        long totalPot;

        DurakRoom(String code, long stake, Object host) {
            super(code, stake, host);
            seats.add(new DurakSeat(host));
        }

        @Override String type() { return "durak"; }
        @Override int maxPlayers() { return 6; }
        @Override boolean started() { return gameStarted; }
        @Override DurakSeat newSeat(Object player) { return new DurakSeat(player); }

        @Override void action(Object player, CasinoRequest request) {
            if (!beginRequest(player, request, true)) return;
            switch (request.action) {
                case "durak_ready" -> toggleReady(player);
                case "durak_leave" -> leaveLobby(player);
                case "durak_start" -> startGame(player);
                case "durak_card" -> playCard(player, request.extra);
                case "durak_throw_rank" -> throwRank(player, request.extra);
                case "durak_take" -> take(player);
                case "durak_finish_take" -> finishTake(player);
                case "durak_beat" -> beat(player);
                case "durak_sync" -> sendSnapshot(player, "", "");
                default -> reject(player, "unknown_action", "Неизвестное действие дурака.");
            }
        }

        private void startGame(Object player) {
            if (!isHost(player)) { reject(player, "host_only", "Начать игру может только создатель комнаты."); return; }
            if (gameStarted) { reject(player, "already_started", "Игра уже началась."); return; }
            if (!allReady()) { reject(player, "not_ready", "Для старта нужно 2–6 готовых игроков."); return; }
            if (stake > 0 && !collectStakes()) return;
            totalPot = safeMultiply(stake, seats.size());
            gameStarted = true; stage = "Игра идёт"; takingCards = false; deck = Cards.durakDeck(); table.clear();
            trumpCard = deck.get(0); trumpSuit = Cards.durakSuit(trumpCard);
            for (DurakSeat s : seats) { s.hand.clear(); s.out = false; s.ready = false; CasinoStats.add(s.player, "durak_games", 1); }
            attacker = 0;
            dealFrom(attacker);
            attacker = lowestTrumpHolder();
            defender = nextInGame(attacker);
            beginRound();
            broadcast("§6Игра началась. Козырь: §f" + Cards.durakCard(trumpCard));
        }

        private void playCard(Object player, String code) {
            if (!gameStarted) { reject(player, "not_started", "Игра ещё не началась."); return; }
            DurakSeat seat = seat(player);
            if (seat == null) return;
            int index = seats.indexOf(seat);
            int card = Cards.parseDurakCard(code, seat.hand);
            if (card < 0) { reject(player, "card_missing", "Карта не найдена в вашей руке."); return; }
            if (index == defender) defendCard(seat, card);
            else if (!seat.out) attackCard(seat, card);
            else reject(player, "already_out", "Вы уже вышли из этой партии.");
        }

        private void attackCard(DurakSeat seat, int card) {
            if (table.isEmpty() && seats.indexOf(seat) != attacker) { reject(seat.player, "attacker_only", "Первую карту кладёт основной атакующий."); return; }
            if (!takingCards && !allDefended() && !table.isEmpty()) { reject(seat.player, "defense_pending", "Сначала дождитесь защиты текущей карты."); return; }
            if (table.size() >= maxAttack) { reject(seat.player, "attack_limit", "Больше карт в этом раунде подкинуть нельзя."); return; }
            if (!table.isEmpty() && !rankOnTable(Cards.durakRank(card))) { reject(seat.player, "wrong_rank", "Подкидывать можно только достоинство, уже лежащее на столе."); return; }
            seat.hand.remove(Integer.valueOf(card));
            table.add(new AttackPair(card));
            gameResult = "§e" + seat.name + " атакует картой " + Cards.durakCard(card) + ".";
            if (takingCards && table.size() >= maxAttack) finishTakeInternal(); else broadcast(gameResult);
        }

        private void throwRank(Object player, String token) {
            if (!gameStarted) { reject(player, "not_started", "Игра ещё не началась."); return; }
            DurakSeat seat = seat(player);
            if (seat == null || seat.out) { reject(player, "cannot_throw", "Вы не можете подкидывать карты."); return; }
            if (seats.indexOf(seat) == defender) { reject(player, "defender_cannot_throw", "Защищающийся не может подкидывать карты."); return; }
            if (table.isEmpty()) { reject(player, "empty_table", "Сначала положите первую карту обычным нажатием."); return; }
            if (!takingCards && !allDefended()) { reject(player, "defense_pending", "Сначала дождитесь защиты текущей карты."); return; }
            int rank = Cards.parseDurakRank(token);
            if (rank < 0 || !rankOnTable(rank)) { reject(player, "wrong_rank", "Такого достоинства сейчас нет на столе."); return; }
            int capacity = maxAttack - table.size();
            if (capacity <= 0) { reject(player, "attack_limit", "Лимит карт этого раунда уже достигнут."); return; }
            List<Integer> matches = new ArrayList<>();
            for (int card : seat.hand) if (Cards.durakRank(card) == rank && matches.size() < capacity) matches.add(card);
            if (matches.isEmpty()) { reject(player, "rank_missing", "В руке больше нет карт этого достоинства."); return; }
            for (int card : matches) { seat.hand.remove(Integer.valueOf(card)); table.add(new AttackPair(card)); }
            gameResult = "§d" + seat.name + " докинул сразу " + matches.size() + " карт достоинства " + token + ".";
            if (takingCards && table.size() >= maxAttack) finishTakeInternal(); else broadcast(gameResult);
        }

        private void defendCard(DurakSeat seat, int card) {
            AttackPair target = firstUndefended();
            if (target == null) { reject(seat.player, "nothing_to_defend", "На столе нет непобитой карты."); return; }
            if (!Cards.durakBeats(card, target.attack, trumpSuit)) { reject(seat.player, "card_does_not_beat", "Эта карта не бьёт " + Cards.durakCard(target.attack) + "."); return; }
            seat.hand.remove(Integer.valueOf(card));
            target.defense = card;
            gameResult = "§a" + seat.name + " отбился картой " + Cards.durakCard(card) + ".";
            broadcast(gameResult);
        }

        private void take(Object player) {
            DurakSeat me = seat(player);
            if (!gameStarted || me == null || seats.indexOf(me) != defender) { reject(player, "defender_only", "Взять карты может только защищающийся."); return; }
            if (table.isEmpty()) { reject(player, "empty_table", "На столе нет карт."); return; }
            if (takingCards) { finishTakeInternal(); return; }
            takingCards = true;
            stage = "Добрасывание";
            gameResult = "§c" + me.name + " объявил, что забирает карты. Остальные могут докинуть подходящие достоинства.";
            if (table.size() >= maxAttack) finishTakeInternal(); else broadcast(gameResult);
        }

        private void finishTake(Object player) {
            DurakSeat me = seat(player);
            if (!gameStarted || !takingCards || me == null || seats.indexOf(me) != attacker) {
                reject(player, "attacker_only", "Закончить доброс может основной атакующий.");
                return;
            }
            finishTakeInternal();
        }

        private void finishTakeInternal() {
            if (!takingCards || defender < 0 || defender >= seats.size()) return;
            DurakSeat d = seats.get(defender);
            int collected = 0;
            for (AttackPair pair : table) {
                d.hand.add(pair.attack); collected++;
                if (pair.defense != null) { d.hand.add(pair.defense); collected++; }
            }
            table.clear();
            int oldDefender = defender;
            dealFrom(attacker);
            attacker = nextInGame(oldDefender);
            defender = nextInGame(attacker);
            beginRound();
            gameResult = "§c" + d.name + " забрал " + collected + " карт. Атакует " + seats.get(attacker).name + ".";
            checkFinishOrBroadcast();
        }

        private void beat(Object player) {
            DurakSeat me = seat(player);
            if (takingCards) { reject(player, "taking_cards", "Защищающийся уже забирает карты. Завершите доброс."); return; }
            if (!gameStarted || me == null || seats.indexOf(me) != attacker) { reject(player, "attacker_only", "Завершить отбой может только атакующий."); return; }
            if (table.isEmpty() || !allDefended()) { reject(player, "not_all_defended", "Не все карты побиты."); return; }
            table.clear();
            int oldDefender = defender;
            dealFrom(attacker);
            attacker = oldDefender;
            if (seats.get(attacker).hand.isEmpty() && deck.isEmpty()) seats.get(attacker).out = true;
            attacker = ensureInGame(attacker);
            defender = nextInGame(attacker);
            beginRound();
            gameResult = "§aОтбой. Атакует " + seats.get(attacker).name + ".";
            checkFinishOrBroadcast();
        }

        private void beginRound() {
            takingCards = false;
            stage = "Игра идёт";
            if (attacker >= 0 && defender >= 0) maxAttack = Math.min(6, Math.max(1, seats.get(defender).hand.size()));
        }
        private void checkFinishOrBroadcast() { markOutPlayers(); if (deck.isEmpty() && remainingWithCards() <= 1) finishGame(); else broadcast(gameResult); }

        private void finishGame() {
            DurakSeat loser = null;
            for (DurakSeat s : seats) if (!s.hand.isEmpty()) { loser = s; break; }
            List<DurakSeat> winners = new ArrayList<>();
            for (DurakSeat s : seats) if (s != loser) winners.add(s);
            long share = winners.isEmpty() ? 0 : totalPot / winners.size();
            long rem = winners.isEmpty() ? 0 : totalPot % winners.size();
            Map<String, Long> payouts = new HashMap<>();
            for (int i = 0; i < winners.size(); i++) payouts.put(winners.get(i).id, stake > 0 ? share + (i == 0 ? rem : 0) : 0L);
            if (stake > 0 && !CasinoData.settleEscrows(seats.stream().map(s -> s.player).toList(), payouts)) { broadcast("§cНе удалось зафиксировать выплаты. Эскроу сохранено для восстановления."); return; }
            for (int i = 0; i < winners.size(); i++) {
                DurakSeat winner = winners.get(i);
                CasinoStats.add(winner.player, "durak_wins", 1);
                CasinoStats.result(winner.player, true, stake, stake > 0 ? share + (i == 0 ? rem : 0) - stake : 0);
            }
            if (loser != null) { CasinoStats.add(loser.player, "durak_losses", 1); CasinoStats.result(loser.player, false, stake, -stake); }
            gameStarted = false; stage = "Игра завершена";
            gameResult = loser == null ? "§eИгра завершилась без дурака." : "§6Дурак: §f" + loser.name + (stake > 0 ? "§6. Банк разделён между остальными." : "§6.");
            for (DurakSeat s : seats) s.ready = false;
            broadcast(gameResult);
        }

        private void dealFrom(int start) {
            if (deck.isEmpty()) return;
            int index = start;
            for (int n = 0; n < seats.size(); n++) {
                DurakSeat s = seats.get(index);
                while (s.hand.size() < 6 && !deck.isEmpty()) s.hand.add(deck.remove(deck.size() - 1));
                sortHand(s.hand);
                index = (index + 1) % seats.size();
            }
        }

        private void sortHand(List<Integer> hand) { hand.sort(Comparator.comparingInt(Cards::durakSuit).thenComparingInt(Cards::durakRank)); }
        private int lowestTrumpHolder() { int holder = 0, rank = 99; for (int i = 0; i < seats.size(); i++) for (int c : seats.get(i).hand) if (Cards.durakSuit(c) == trumpSuit && Cards.durakRank(c) < rank) { rank = Cards.durakRank(c); holder = i; } return holder; }
        private boolean rankOnTable(int rank) { for (AttackPair p : table) if (Cards.durakRank(p.attack) == rank || p.defense != null && Cards.durakRank(p.defense) == rank) return true; return false; }
        private AttackPair firstUndefended() { for (AttackPair p : table) if (p.defense == null) return p; return null; }
        private boolean allDefended() { return firstUndefended() == null; }
        private int nextInGame(int from) { for (int n = 1; n <= seats.size(); n++) { int i = (from + n + seats.size()) % seats.size(); if (!seats.get(i).out) return i; } return -1; }
        private int ensureInGame(int index) { return index >= 0 && !seats.get(index).out ? index : nextInGame(index); }
        private void markOutPlayers() { if (!deck.isEmpty()) return; for (DurakSeat s : seats) if (s.hand.isEmpty()) s.out = true; }
        private int remainingWithCards() { int count = 0; for (DurakSeat s : seats) if (!s.hand.isEmpty()) count++; return count; }

        private String tableText() { StringBuilder b = new StringBuilder(); for (AttackPair pair : table) { if (b.length() > 0) b.append('|'); b.append(Cards.durakCard(pair.attack)).append('>').append(pair.defense == null ? "?" : Cards.durakCard(pair.defense)); } return b.toString(); }

        @Override String extraPlayerStatus(DurakSeat s) { return gameStarted ? " §7— карт: " + s.hand.size() + (s.out ? " §a[вышел]" : "") : ""; }

        @Override CasinoViewState view(Object player) {
            DurakSeat me = seat(player);
            CasinoViewState state = CasinoEngine.baseState(player);
            state.game = "Дурак с друзьями";
            state.phase = stage;
            state.roomType = "durak";
            state.roomCode = code;
            state.roomPlayers = playersText();
            state.roomStatus = gameResult;
            state.roomStake = stake;
            state.roomHost = isHost(player);
            state.roomReady = me != null && me.ready;
            state.multiplayerActive = gameStarted;
            state.handCards = me == null ? "" : Cards.csvDurak(me.hand);
            state.tableCards = tableText();
            state.trumpCard = trumpCard < 0 ? "" : Cards.durakCard(trumpCard);
            state.deckCount = deck.size();
            state.durakTaking = takingCards;
            state.currentTurn = attacker >= 0 && defender >= 0
                ? takingCards
                    ? seats.get(defender).name + " забирает; " + seats.get(attacker).name + " завершает доброс"
                    : seats.get(attacker).name + " атакует; " + seats.get(defender).name + " защищается"
                : "—";
            int meIndex = me == null ? -1 : seats.indexOf(me);
            state.role = meIndex == attacker ? "Атакующий" : meIndex == defender ? "Защищающийся" : me != null && !me.out ? "Подкидывающий" : "Ожидание";
            state.canAct = gameStarted && me != null && !me.out;
            if (state.canAct) {
                List<String> actions = new ArrayList<>();
                if (takingCards) {
                    if (meIndex == defender) actions.add("take");
                    else {
                        actions.add("card");
                        actions.add("throw_rank");
                        if (meIndex == attacker) actions.add("finish_take");
                    }
                } else {
                    actions.add("card");
                    if (meIndex != defender && !table.isEmpty()) actions.add("throw_rank");
                    if (meIndex == defender) actions.add("take");
                    if (meIndex == attacker) actions.add("beat");
                }
                state.allowedActions = String.join(",", actions);
            }
            state.pot = totalPot;
            state.result = gameResult;
            state.bet = stake;
            state.revision = revision;
            state.ackSequence = me == null ? 0 : me.lastSequence;
            for (int i = 0; i < seats.size(); i++) {
                DurakSeat seat = seats.get(i);
                CasinoPlayerView playerView = new CasinoPlayerView();
                playerView.id = seat.id;
                playerView.name = seat.name;
                playerView.local = seat == me;
                playerView.host = seat.id.equals(hostId);
                playerView.ready = seat.ready;
                playerView.connected = seat.connected;
                playerView.activeTurn = i == attacker || i == defender;
                playerView.status = !seat.connected ? "отключён" : seat.out ? "вышел" : i == attacker ? "атакует" : i == defender ? "защищается" : !gameStarted ? (seat.ready ? "готов" : "не готов") : "подкидывает";
                playerView.cards = seat == me ? Cards.csvDurak(seat.hand) : "";
                playerView.handContribution = seat.hand.size();
                state.players.add(playerView);
            }
            return state;
        }
    }

    private static long safeAdd(long a, long b) {
        if (b > 0 && a > Long.MAX_VALUE - b) return Long.MAX_VALUE;
        if (b < 0 && a < Long.MIN_VALUE - b) return Long.MIN_VALUE;
        return a + b;
    }

    private static long safeMultiply(long a, long b) {
        if (a == 0 || b == 0) return 0;
        if (a > Long.MAX_VALUE / b) return Long.MAX_VALUE;
        return a * b;
    }
}

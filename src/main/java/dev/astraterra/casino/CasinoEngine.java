package dev.astraterra.casino;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

final class CasinoEngine {
    private static final Map<String, Blackjack> BLACKJACK = new HashMap<>();
    private static final Map<String, WheelSpin> WHEELS = new HashMap<>();
    private static final Map<String, Long> WHEEL_SEQUENCE = new HashMap<>();
    private static final Map<String, Long> LAST = new HashMap<>();
    private static final long COOLDOWN = 120L;
    private static final long MAX_AMOUNT = 9_000_000_000_000_000L;
    private static final ThreadLocal<Long> REQUEST_SEQUENCE = ThreadLocal.withInitial(() -> 0L);

    private CasinoEngine() {}

    static synchronized void connected(Object player) {
        String id = CasinoData.id(player);
        WHEEL_SEQUENCE.remove(id);
        WheelSpin spin = WHEELS.get(id);
        if (spin != null) spin.player = player;
    }

    static void disconnected(Object player) {
        // The authoritative spin remains recoverable through currentState on reconnect.
    }

    static CasinoRequest legacyRequest(String action) {
        if (action.equals("status")) return new CasinoRequest("status", 0, 1, "", "");
        if (action.equals("deposit_all") || action.equals("withdraw_all")) return new CasinoRequest(action, 0, 1, "", "");
        if (action.startsWith("deposit_")) return new CasinoRequest("deposit", parse(action.substring(8), 1), 1, "", "");
        if (action.startsWith("withdraw_")) return new CasinoRequest("withdraw", parse(action.substring(9), 1), 1, "", "");
        if (action.startsWith("bj_start_")) return new CasinoRequest("bj_start", parse(action.substring(9), 1), 1, "", "");
        if (action.startsWith("wheel_")) return new CasinoRequest("wheel_spin", parse(action.substring(6), 1), 1, "", "");
        if (action.startsWith("roulette_")) return new CasinoRequest(action, 1, 1, "", "");
        if (action.startsWith("dice_")) return new CasinoRequest(action, 1, 1, "", "");
        return new CasinoRequest(action, 0, 1, "", "");
    }

    static void action(Object player, CasinoRequest request) {
        REQUEST_SEQUENCE.set(request.sequence);
        try {
            String action = request.action;
            if (wheelBusy(player) && isGameAction(action) && !action.equals("wheel_spin")) {
                errorState(player, "Дождитесь завершения вращения Колеса экспедиции.");
                return;
            }
            if (MultiplayerRooms.handles(action)) { MultiplayerRooms.action(player, request); return; }
            if (!cooldown(player, action)) {
                errorState(player, "Действие отправлено слишком быстро. Повторите попытку.");
                return;
            }
            long amount = checkedAmount(request.baseAmount());
            switch (action) {
                case "status" -> status(player);
                case "deposit" -> deposit(player, amount);
                case "deposit_all" -> depositAll(player);
                case "withdraw" -> withdraw(player, amount);
                case "withdraw_all" -> withdrawAll(player);
                case "bj_start" -> startBlackjack(player, amount);
                case "bj_hit" -> hit(player);
                case "bj_stand" -> stand(player);
                case "bj_double" -> doubleDown(player);
                case "roulette_red", "roulette_black", "roulette_even", "roulette_odd", "roulette_zero",
                     "roulette_dozen1", "roulette_dozen2", "roulette_dozen3" -> roulette(player, action.substring(9), amount);
                case "dice_low", "dice_seven", "dice_high" -> dice(player, action.substring(5), amount);
                case "wheel_spin" -> wheel(player, amount);
                default -> errorState(player, "Неизвестное действие казино: " + action);
            }
        } catch (Throwable e) {
            System.err.println("[AstraTerra Casino] Action failed: " + request.action);
            e.printStackTrace();
            errorState(player, "Ошибка казино. Подробности записаны в latest.log");
        } finally {
            REQUEST_SEQUENCE.remove();
        }
    }

    private static boolean cooldown(Object player, String action) {
        if (action.equals("status")) return true;
        String id = CasinoData.id(player);
        long now = System.currentTimeMillis();
        synchronized (LAST) {
            long previous = LAST.getOrDefault(id, 0L);
            if (now - previous < COOLDOWN) return false;
            LAST.put(id, now);
        }
        return true;
    }

    private static long checkedAmount(long value) {
        if (value <= 0) return 0;
        return Math.min(value, MAX_AMOUNT);
    }

    private static void status(Object player) {
        CasinoViewState state = currentState(player);
        state.result = "§aСостояние клуба синхронизировано.";
        sendState(player, state);
    }

    private static void depositAll(Object player) throws Exception { deposit(player, CurrencyBridge.balance(player)); }
    private static void withdrawAll(Object player) { withdraw(player, wallet(player)); }

    private static void deposit(Object player, long value) throws Exception {
        if (value <= 0) { errorState(player, "Введите положительную сумму пополнения."); return; }
        if (!CurrencyBridge.take(player, value)) { errorState(player, "Недостаточно монет Numismatic для пополнения."); return; }
        if (!CasinoData.change(player, "wallet", value)) {
            CurrencyBridge.give(player, value);
            errorState(player, "Не удалось сохранить депозит; монеты возвращены.");
            return;
        }
        CasinoStats.add(player, "deposit_silver", statSilver(value));
        CasinoViewState state = currentState(player);
        state.result = "§aПополнение выполнено: §f" + money(value);
        state.event = "§aВнесено в клуб: §f" + money(value);
        sendState(player, state);
    }

    private static void withdraw(Object player, long value) {
        if (value <= 0) { errorState(player, "Введите положительную сумму вывода."); return; }
        if (wallet(player) < value) { errorState(player, "Недостаточно средств на клубном балансе."); return; }
        if (!CasinoData.change(player, "wallet", -value)) { errorState(player, "Не удалось сохранить вывод. Баланс не изменён."); return; }
        try {
            CurrencyBridge.give(player, value);
        } catch (Exception e) {
            CasinoData.change(player, "wallet", value);
            throw new IllegalStateException(e);
        }
        CasinoStats.add(player, "withdraw_silver", statSilver(value));
        CasinoViewState state = currentState(player);
        state.result = "§bВывод выполнен: §f" + money(value);
        state.event = "§bВыведено из клуба: §f" + money(value);
        sendState(player, state);
    }

    static long wallet(Object player) { return CasinoData.get(player, "wallet", 0); }

    static boolean debitWallet(Object player, long value, boolean notify) {
        if (value <= 0) return true;
        if (wallet(player) < value) {
            if (notify) errorState(player, "На клубном балансе недостаточно средств.");
            return false;
        }
        if (!CasinoData.change(player, "wallet", -value)) {
            if (notify) errorState(player, "Не удалось зафиксировать списание.");
            return false;
        }
        return true;
    }

    static boolean creditWallet(Object player, long value, boolean notify) {
        if (value <= 0) return true;
        if (!CasinoData.change(player, "wallet", value)) {
            if (notify) errorState(player, "Не удалось сохранить выплату.");
            return false;
        }
        return true;
    }

    private static synchronized void startBlackjack(Object player, long bet) {
        if (!validBet(player, bet)) return;
        String id = CasinoData.id(player);
        if (BLACKJACK.containsKey(id)) { errorState(player, "Партия уже идёт: выберите игровое действие."); return; }
        if (!debitWallet(player, bet, true)) return;
        Blackjack blackjack = new Blackjack(bet);
        BLACKJACK.put(id, blackjack);
        CasinoStats.add(player, "blackjack_games", 1);
        if (blackjack.playerValue() == 21) {
            CasinoStats.add(player, "blackjack_naturals", 1);
            finishBlackjack(player, blackjack, true);
        } else sendState(player, blackjackState(player, blackjack, true, "§6Партия началась. Выберите действие.", "§6Новая партия в блэкджек: §f" + money(bet)));
    }

    private static synchronized void hit(Object player) {
        Blackjack blackjack = BLACKJACK.get(CasinoData.id(player));
        if (blackjack == null) { errorState(player, "Сначала начните партию в блэкджек."); return; }
        blackjack.player.add(blackjack.draw());
        int value = blackjack.playerValue();
        if (value > 21) finishBlackjack(player, blackjack, false);
        else if (value == 21) { CasinoStats.add(player, "blackjack_21", 1); stand(player); }
        else sendState(player, blackjackState(player, blackjack, true, "§eВы взяли карту. Текущая сумма: §f" + value, "§eВзята дополнительная карта."));
    }

    private static synchronized void stand(Object player) {
        Blackjack blackjack = BLACKJACK.get(CasinoData.id(player));
        if (blackjack == null) { errorState(player, "Нет активной партии в блэкджек."); return; }
        while (blackjack.dealerShouldHit()) blackjack.dealer.add(blackjack.draw());
        finishBlackjack(player, blackjack, true);
    }

    private static synchronized void doubleDown(Object player) {
        Blackjack blackjack = BLACKJACK.get(CasinoData.id(player));
        if (blackjack == null) { errorState(player, "Нет активной партии в блэкджек."); return; }
        if (blackjack.player.size() != 2) { errorState(player, "Удвоение доступно только первым действием."); return; }
        if (!debitWallet(player, blackjack.bet, true)) return;
        blackjack.bet *= 2;
        blackjack.player.add(blackjack.draw());
        if (blackjack.playerValue() > 21) finishBlackjack(player, blackjack, false); else stand(player);
    }

    private static void finishBlackjack(Object player, Blackjack blackjack, boolean dealerPlay) {
        BLACKJACK.remove(CasinoData.id(player));
        int playerValue = blackjack.playerValue();
        if (dealerPlay) while (blackjack.dealerShouldHit()) blackjack.dealer.add(blackjack.draw());
        int dealerValue = blackjack.dealerValue();
        boolean natural = playerValue == 21 && blackjack.player.size() == 2;
        long payout = 0;
        String result;
        boolean win = false, loss = false;
        if (playerValue > 21) { result = "§cПеребор — партия проиграна."; loss = true; }
        else if (dealerValue > 21 || playerValue > dealerValue) { win = true; payout = natural ? blackjack.bet * 5 / 2 : blackjack.bet * 2; result = natural ? "§6НАТУРАЛЬНЫЙ БЛЭКДЖЕК! Выплата 3:2." : "§aПобеда в блэкджек."; }
        else if (playerValue == dealerValue) { payout = blackjack.bet; result = "§eНичья — ставка возвращена."; }
        else { result = "§cДилер победил."; loss = true; }
        if (payout > 0) creditWallet(player, payout, true);
        if (win) { CasinoStats.add(player, "blackjack_wins", 1); CasinoStats.result(player, true, blackjack.bet, payout - blackjack.bet); }
        else if (loss) CasinoStats.result(player, false, blackjack.bet, -blackjack.bet);
        else neutralRound(player, blackjack.bet);
        CasinoViewState state = blackjackState(player, blackjack, false, result, "§6Блэкджек завершён: §f" + strip(result) + " §7(выплата " + money(payout) + ")");
        state.result = result + " §7Выплата: §f" + money(payout);
        sendState(player, state);
    }

    private static void roulette(Object player, String type, long bet) {
        if (!validBet(player, bet) || !debitWallet(player, bet, true)) return;
        CasinoStats.add(player, "roulette_games", 1);
        int number = ThreadLocalRandom.current().nextInt(37);
        boolean red = isRed(number), win = false;
        int multiplier = 0;
        switch (type) {
            case "red" -> { win = number > 0 && red; multiplier = 2; }
            case "black" -> { win = number > 0 && !red; multiplier = 2; }
            case "even" -> { win = number > 0 && number % 2 == 0; multiplier = 2; }
            case "odd" -> { win = number % 2 == 1; multiplier = 2; }
            case "zero" -> { win = number == 0; multiplier = 36; }
            case "dozen1" -> { win = number >= 1 && number <= 12; multiplier = 3; }
            case "dozen2" -> { win = number >= 13 && number <= 24; multiplier = 3; }
            case "dozen3" -> { win = number >= 25 && number <= 36; multiplier = 3; }
        }
        long payout = win ? safeMultiply(bet, multiplier) : 0;
        if (win) { creditWallet(player, payout, true); CasinoStats.add(player, "roulette_wins", 1); if (number == 0) CasinoStats.add(player, "roulette_zero_wins", 1); }
        CasinoStats.result(player, win, bet, win ? payout - bet : -bet);
        String color = number == 0 ? "§aZERO" : red ? "§cКРАСНОЕ" : "§8ЧЁРНОЕ";
        CasinoViewState state = baseState(player);
        state.game = "Рулетка"; state.phase = "Выпало число §f" + number; state.bet = bet;
        state.playerCards = "Число " + number; state.dealerCards = strip(color);
        state.result = win ? "§aВыигрыш: §f" + money(payout) : "§cСтавка проиграна.";
        state.event = "§6Рулетка: §f" + number + " " + color + " §7— " + (win ? "§aвыигрыш " + money(payout) : "§cпроигрыш");
        sendState(player, state);
    }

    private static void dice(Object player, String type, long bet) {
        if (!validBet(player, bet) || !debitWallet(player, bet, true)) return;
        CasinoStats.add(player, "dice_games", 1);
        int first = ThreadLocalRandom.current().nextInt(1, 7), second = ThreadLocalRandom.current().nextInt(1, 7), sum = first + second;
        boolean win = type.equals("low") && sum < 7 || type.equals("high") && sum > 7 || type.equals("seven") && sum == 7;
        int multiplier = type.equals("seven") ? 5 : 2;
        long payout = win ? safeMultiply(bet, multiplier) : 0;
        if (win) { creditWallet(player, payout, true); CasinoStats.add(player, "dice_wins", 1); if (sum == 7) CasinoStats.add(player, "dice_seven_wins", 1); }
        CasinoStats.result(player, win, bet, win ? payout - bet : -bet);
        CasinoViewState state = baseState(player);
        state.game = "Кости"; state.phase = "Бросок завершён"; state.bet = bet;
        state.playerCards = Integer.toString(first); state.dealerCards = Integer.toString(second); state.playerValue = sum;
        state.result = win ? "§aСумма " + sum + ". Выигрыш: §f" + money(payout) : "§cСумма " + sum + ". Ставка проиграна.";
        state.event = "§3Кости: §f" + first + " + " + second + " = " + sum + " §7— " + (win ? "§aвыигрыш " + money(payout) : "§cпроигрыш");
        sendState(player, state);
    }

    private static synchronized void wheel(Object player, long bet) {
        if (!validBet(player, bet)) return;
        String id = CasinoData.id(player);
        long now = System.currentTimeMillis();
        if (BLACKJACK.containsKey(id) || MultiplayerRooms.isMember(player)) {
            errorState(player, "Сначала завершите текущую карточную игру или покиньте комнату.");
            return;
        }
        long sequence = REQUEST_SEQUENCE.get();
        if (sequence > 0 && sequence <= WHEEL_SEQUENCE.getOrDefault(id, 0L)) {
            WheelSpin replay = WHEELS.get(id);
            if (replay != null) sendState(player, wheelState(player, replay, now, false));
            else errorState(player, "Повторный запрос вращения отклонён.");
            return;
        }
        WheelSpin previous = WHEELS.get(id);
        if (previous != null && !previous.finished(now)) {
            errorState(player, "Колесо уже вращается. Дождитесь результата.");
            return;
        }
        if (!debitWallet(player, bet, true)) return;

        int sectorIndex = WheelSector.chooseIndex();
        WheelSector sector = WheelSector.SECTORS.get(sectorIndex);
        long payout = sector.payout(bet, MAX_AMOUNT);
        if (payout > 0 && !creditWallet(player, payout, true)) {
            creditWallet(player, bet, false);
            errorState(player, "Не удалось зафиксировать выплату колеса; ставка возвращена.");
            return;
        }

        CasinoStats.add(player, "wheel_games", 1);
        if (payout > 0) CasinoStats.add(player, "wheel_wins", 1);
        if (sector.rarity == WheelSector.Rarity.JACKPOT) CasinoStats.add(player, "wheel_jackpots", 1);
        if (payout == bet) neutralRound(player, bet);
        else CasinoStats.result(player, payout > bet, bet, payout - bet);

        int rotations = ThreadLocalRandom.current().nextInt(5, 9);
        long duration = ThreadLocalRandom.current().nextLong(4_700L, 6_301L);
        int startAngle = previous == null ? ThreadLocalRandom.current().nextInt(0, 360_000)
            : Math.floorMod(previous.targetAngleMilli, 360_000);
        int visualOffset = ThreadLocalRandom.current().nextInt(-8_000, 8_001);
        int targetAngle = WheelMath.targetAngleMilli(startAngle, sectorIndex, rotations, visualOffset);
        String spinId = UUID.randomUUID().toString();
        WheelSpin spin = new WheelSpin(player, id, spinId, sectorIndex, sector, bet, payout, now, duration,
            rotations, startAngle, targetAngle);
        WHEELS.put(id, spin);
        if (sequence > 0) WHEEL_SEQUENCE.put(id, sequence);

        CasinoViewState state = wheelState(player, spin, now, false);
        state.event = "§5Колесо запущено: ставка §f" + money(bet);
        sendState(player, state);
    }

    static synchronized void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, WheelSpin>> iterator = WHEELS.entrySet().iterator();
        while (iterator.hasNext()) {
            WheelSpin spin = iterator.next().getValue();
            if (spin.finished(now) && !spin.finishSent) {
                spin.finishSent = true;
                CasinoViewState state = wheelState(spin.player, spin, now, true);
                state.event = "§5Колесо: §f" + spin.sector.name + " §7— выплата §f" + money(spin.payout);
                sendState(spin.player, state);
            }
            if (now - spin.startedAtMs > 3_600_000L) iterator.remove();
        }
    }

    private static CasinoViewState wheelState(Object player, WheelSpin spin, long now, boolean includeFinalEvent) {
        CasinoViewState state = baseState(player);
        boolean finished = spin.finished(now);
        state.game = "Колесо экспедиции";
        state.phase = finished ? "Результат экспедиции" : "Колесо вращается";
        state.bet = spin.bet;
        state.wheelSpinId = spin.spinId;
        state.wheelState = finished ? "FINISHED" : "SPINNING";
        state.wheelSectorIndex = spin.sectorIndex;
        state.wheelRotations = spin.fullRotations;
        state.wheelStartAngleMilli = spin.startAngleMilli;
        state.wheelTargetAngleMilli = spin.targetAngleMilli;
        state.wheelDurationMs = spin.durationMs;
        state.wheelElapsedMs = spin.elapsed(now);
        state.wheelPayout = spin.payout;
        state.wheelMultiplierNumerator = spin.sector.numerator;
        state.wheelMultiplierDenominator = spin.sector.denominator;
        if (finished) {
            state.wheelSectorId = spin.sector.id;
            state.wheelSectorName = spin.sector.name;
            state.wheelRarity = spin.sector.rarity.name();
            state.playerCards = spin.sector.multiplierText();
            state.result = spin.payout > spin.bet
                ? "§a" + spin.sector.name + ". Выплата: §f" + money(spin.payout)
                : spin.payout == spin.bet
                    ? "§e" + spin.sector.name + ". Ставка возвращена."
                    : spin.payout > 0
                        ? "§e" + spin.sector.name + ". Выплата: §f" + money(spin.payout)
                        : "§c" + spin.sector.name + ". Выплаты нет.";
        } else {
            state.playerCards = "?";
            state.result = "§bКолесо вращается. Результат уже зафиксирован сервером.";
        }
        return state;
    }

    private static synchronized boolean wheelBusy(Object player) {
        WheelSpin spin = WHEELS.get(CasinoData.id(player));
        return spin != null && !spin.finished(System.currentTimeMillis());
    }

    private static boolean isGameAction(String action) {
        return action.startsWith("bj_") || action.startsWith("roulette_") || action.startsWith("dice_")
            || action.startsWith("wheel_") || action.startsWith("poker_") || action.startsWith("durak_");
    }

    private static boolean validBet(Object player, long bet) {
        if (bet <= 0) { errorState(player, "Введите положительный размер ставки."); return false; }
        if (bet > MAX_AMOUNT) { errorState(player, "Ставка слишком велика."); return false; }
        return true;
    }

    private static void neutralRound(Object player, long bet) {
        CasinoStats.add(player, "rounds", 1);
        CasinoStats.add(player, "wagered_silver", statSilver(bet));
        CasinoData.set(player, "win_streak", 0);
    }

    static synchronized CasinoViewState currentState(Object player) {
        CasinoViewState room = MultiplayerRooms.view(player);
        if (room != null) return room;
        String id = CasinoData.id(player);
        Blackjack blackjack = BLACKJACK.get(id);
        if (blackjack != null) return blackjackState(player, blackjack, true, "§eРаунд продолжается.", "");
        WheelSpin spin = WHEELS.get(id);
        if (spin != null) {
            spin.player = player;
            return wheelState(player, spin, System.currentTimeMillis(), false);
        }
        return baseState(player);
    }

    static CasinoViewState baseState(Object player) {
        if(!MultiplayerRooms.isMember(player)&&CasinoData.escrow(player)>0)CasinoData.refundEscrow(player);
        CasinoViewState state = new CasinoViewState();
        state.wallet = wallet(player);
        try { state.numismatic = CurrencyBridge.balance(player); } catch (Throwable ignored) { state.numismatic = 0; }
        return state;
    }

    private static CasinoViewState blackjackState(Object player, Blackjack blackjack, boolean hidden, String result, String event) {
        CasinoViewState state = baseState(player);
        state.game = "Блэкджек"; state.phase = hidden ? "Раунд идёт" : "Раунд завершён"; state.bet = blackjack.bet;
        state.playerCards = blackjackCsv(blackjack.player); state.dealerCards = hidden ? blackjackCard(blackjack.dealer.get(0)) + ",?" : blackjackCsv(blackjack.dealer);
        state.playerValue = blackjack.playerValue(); state.dealerValue = hidden ? -1 : blackjack.dealerValue(); state.blackjackActive = hidden;
        state.result = result; state.event = event;
        return state;
    }

    static void errorState(Object player, String text) {
        try {
            CasinoViewState state = currentState(player);
            state.result = "§c" + text; state.event = "§c" + text;
            sendState(player, state);
        } catch (Throwable packetError) { fallbackMessage(player, "§c" + text); }
    }

    static void sendState(Object player, CasinoViewState state) {
        try {
            if (state.ackSequence == 0) state.ackSequence = REQUEST_SEQUENCE.get();
            state.wallet = wallet(player);
            try { state.numismatic = CurrencyBridge.balance(player); } catch (Throwable ignored) {}
            CasinoPacket.send(player, state);
        } catch (Throwable e) {
            System.err.println("[AstraTerra Casino] Could not send UI state");
            e.printStackTrace();
            fallbackMessage(player, state.result);
        }
    }

    private static void fallbackMessage(Object player, String text) {
        try { Reflect.invoke(player, "method_7353", TextBridge.of(text), true); } catch (Throwable ignored) {}
    }

    static void msg(Object player, String text) { fallbackMessage(player, text); }

    static String money(long value) {
        long gold = value / 10_000, silver = value % 10_000 / 100, bronze = value % 100;
        return gold + "з " + silver + "с " + bronze + "б";
    }

    private static int statSilver(long value) { return (int) Math.min(Integer.MAX_VALUE, Math.max(1, value / 100)); }
    private static long safeMultiply(long value, int multiplier) { return value > MAX_AMOUNT / multiplier ? MAX_AMOUNT : value * multiplier; }
    private static long parse(String value, long fallback) { try { return Long.parseLong(value); } catch (Exception ignored) { return fallback; } }
    private static String formatMultiplier(double value) { return value == (long) value ? Long.toString((long) value) : Double.toString(value); }
    private static String strip(String text) { return text == null ? "" : text.replaceAll("§.", ""); }
    private static boolean isRed(int number) { return switch (number) { case 1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36 -> true; default -> false; }; }
    private static String blackjackCsv(List<Integer> cards) { StringBuilder out = new StringBuilder(); for (int card : cards) { if (out.length() > 0) out.append(','); out.append(blackjackCard(card)); } return out.toString(); }
    private static String blackjackCard(int card) { return card == 1 ? "A" : card == 11 ? "J" : card == 12 ? "Q" : card == 13 ? "K" : Integer.toString(card); }

    private static final class Blackjack {
        final List<Integer> deck = new ArrayList<>(312), player = new ArrayList<>(), dealer = new ArrayList<>();
        long bet;
        Blackjack(long bet) {
            this.bet = bet;
            for (int deckNo = 0; deckNo < 24; deckNo++) for (int card = 1; card <= 13; card++) deck.add(card);
            Collections.shuffle(deck);
            player.add(draw()); dealer.add(draw()); player.add(draw()); dealer.add(draw());
        }
        int draw() { return deck.remove(deck.size() - 1); }
        int playerValue() { return value(player); }
        int dealerValue() { return value(dealer); }
        boolean dealerShouldHit() { return value(dealer) < 17; }
        static int value(List<Integer> hand) { int value = 0, aces = 0; for (int card : hand) { if (card == 1) { value++; aces++; } else value += Math.min(card, 10); } while (aces-- > 0 && value + 10 <= 21) value += 10; return value; }
    }
}

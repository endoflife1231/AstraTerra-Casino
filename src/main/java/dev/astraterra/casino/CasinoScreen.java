package dev.astraterra.casino;

import net.minecraft.class_332;
import net.minecraft.class_342;
import net.minecraft.class_437;
import net.minecraft.class_4185;

import java.util.*;

public final class CasinoScreen extends class_437 {
    private static volatile CasinoScreen ACTIVE;
    private final WheelRenderer wheelRenderer = new WheelRenderer();

    private int selectedTab;
    private CurrencyUnit transferUnit = CurrencyUnit.SILVER;
    private CurrencyUnit betUnit = CurrencyUnit.SILVER;
    private String transferText = "1";
    private String betText = "1";
    private String roomCodeText = "";
    private class_342 transferField;
    private class_342 betField;
    private class_342 roomCodeField;
    private int durakPage;
    private boolean durakBatchMode;
    private int historyPage;
    private ResponsiveCasinoLayout.Page compactPage = ResponsiveCasinoLayout.Page.TABLE;
    private ResponsiveCasinoLayout.Page mediumSidePage = ResponsiveCasinoLayout.Page.WALLET;
    private String localHint = "";
    private String lastWidgetKey = "";
    private static final boolean DEBUG_LAYOUT = Boolean.getBoolean("astraterra.casino.debugLayout");

    public CasinoScreen() {
        super(TextBridge.of("AstraTerra Club"));
        selectedTab = tabFromState(AstraTerraCasinoClient.view());
        ACTIVE = this;
    }

    static void refreshActive() {
        CasinoScreen screen = ACTIVE;
        if (screen != null) screen.refreshFromServer();
    }

    void refreshFromServer() {
        captureFields();
        CasinoViewState state = AstraTerraCasinoClient.view();
        if (state.roomType.equals("poker")) selectedTab = 4;
        else if (state.roomType.equals("durak")) selectedTab = 5;
        if (!state.roomCode.isBlank()) {
            roomCodeText = state.roomCode;
            if (roomCodeField != null && !state.roomCode.equals(roomCodeField.method_1882())) {
                roomCodeField.method_1852(state.roomCode);
                roomCodeField.method_1888(false);
            }
        }
        String nextKey = widgetKey(state);
        if (!nextKey.equals(lastWidgetKey)) method_41843();
    }

    @Override protected void method_25426() {
        method_37067();
        ResponsiveCasinoLayout.Layout layout = layout();
        CasinoViewState state = AstraTerraCasinoClient.view();
        lastWidgetKey = widgetKey(state);
        buildNavigation(layout);
        if (layout.showWallet()) buildWalletControls(layout.wallet());
        if (layout.showTable()) buildTableControls(layout.table(), layout.mode(), state);
        if (layout.showJournal()) buildHistoryControls(layout.journal());
        buildFooter(layout.footer(), layout.mode());
    }

    private void buildNavigation(ResponsiveCasinoLayout.Layout layout) {
        if (layout.mode() == ResponsiveCasinoLayout.Mode.LARGE) return;
        ResponsiveCasinoLayout.Box n = layout.navigation();
        if (layout.mode() == ResponsiveCasinoLayout.Mode.COMPACT) {
            int gap = 4;
            int w = (n.w() - gap * 2) / 3;
            pageButton("Стол", ResponsiveCasinoLayout.Page.TABLE, compactPage, n.x(), n.y(), w, n.h(), true);
            pageButton("Кошелёк", ResponsiveCasinoLayout.Page.WALLET, compactPage, n.x() + w + gap, n.y(), w, n.h(), true);
            pageButton("Журнал", ResponsiveCasinoLayout.Page.JOURNAL, compactPage, n.x() + (w + gap) * 2, n.y(), w, n.h(), true);
        } else {
            int w = Math.min(170, n.w() / 3);
            addButton((mediumSidePage == ResponsiveCasinoLayout.Page.WALLET ? "§e" : "§7") + "Боковая панель: кошелёк", () -> {
                captureFields(); mediumSidePage = ResponsiveCasinoLayout.Page.WALLET; method_41843();
            }, n.right() - w * 2 - 4, n.y(), w, n.h(), true);
            addButton((mediumSidePage == ResponsiveCasinoLayout.Page.JOURNAL ? "§b" : "§7") + "Журнал", () -> {
                captureFields(); mediumSidePage = ResponsiveCasinoLayout.Page.JOURNAL; method_41843();
            }, n.right() - w, n.y(), w, n.h(), true);
        }
    }

    private void pageButton(String label, ResponsiveCasinoLayout.Page page, ResponsiveCasinoLayout.Page current,
                            int x, int y, int w, int h, boolean enabled) {
        addButton((page == current ? "§6§l" : "§7") + label, () -> {
            captureFields(); compactPage = page; method_41843();
        }, x, y, w, h, enabled);
    }

    private void buildFooter(ResponsiveCasinoLayout.Box footer, ResponsiveCasinoLayout.Mode mode) {
        int closeW = mode == ResponsiveCasinoLayout.Mode.COMPACT ? Math.min(150, footer.w()) : Math.min(210, footer.w() / 3);
        addButton("§cЗакрыть", this::method_25419, footer.x(), footer.y() + 3, closeW, 20, true);
    }

    private void buildWalletControls(ResponsiveCasinoLayout.Box box) {
        int x = box.x() + 12;
        int w = box.w() - 24;
        int y = box.y() + 128;
        int unitW = Math.min(94, Math.max(72, w / 3));
        transferField = numericField(x, y, Math.max(70, w - unitW - 5), transferText, "Сумма");
        addButton("§e" + transferUnit.label, () -> {
            captureFields(); transferUnit = transferUnit.next(); method_41843();
        }, x + w - unitW, y, unitW, 20, true);

        int gap = 5;
        int half = (w - gap) / 2;
        addButton("§aВнести", () -> sendAmount("deposit", transferField, transferUnit), x, y + 27, half, 20, true);
        addButton("§bВывести", () -> sendAmount("withdraw", transferField, transferUnit), x + half + gap, y + 27, half, 20, true);
        addButton("§aВнести всё", () -> AstraTerraCasinoClient.send("deposit_all"), x, y + 52, half, 20, true);
        addButton("§bВывести всё", () -> AstraTerraCasinoClient.send("withdraw_all"), x + half + gap, y + 52, half, 20, true);
        addButton("§eОбновить", () -> AstraTerraCasinoClient.send("status"), x, y + 77, w, 20, true);
    }

    private void buildTableControls(ResponsiveCasinoLayout.Box box, ResponsiveCasinoLayout.Mode mode, CasinoViewState state) {
        TableGeometry g = tableGeometry(box, mode, selectedTab >= 4);
        buildTabs(g, box.w());
        buildBetControls(g, selectedTab >= 4, state);
        buildGameControls(g, state, mode);
    }

    private void buildTabs(TableGeometry g, int panelWidth) {
        String[] names = {"Блэкджек", "Рулетка", "Кости", "Колесо", "Покер", "Дурак"};
        int gap = 3;
        int columns = tabColumns(panelWidth);
        int buttonW = (g.innerW - gap * (columns - 1)) / columns;
        for (int i = 0; i < names.length; i++) {
            int row = i / columns, col = i % columns, tab = i;
            addButton((selectedTab == i ? "§6§l" : "§7") + names[i], () -> {
                captureFields(); selectedTab = tab; localHint = ""; method_41843();
            }, g.x + col * (buttonW + gap), g.tabsY + row * 23, buttonW, 20, true);
        }
    }

    private void buildBetControls(TableGeometry g, boolean networkGame, CasinoViewState state) {
        int unitW = Math.min(105, Math.max(76, g.innerW / 4));
        int fieldW = Math.max(80, g.innerW - unitW - 5);
        betField = numericField(g.x, g.betY, fieldW, betText, networkGame ? "Ставка / бай-ин / raise-to" : "Размер ставки");
        addButton("§e" + betUnit.label, () -> {
            captureFields(); betUnit = betUnit.next(); method_41843();
        }, g.x + fieldW + 5, g.betY, unitW, 20, true);
        if (networkGame) {
            boolean inThisRoom = !state.roomCode.isBlank()
                && ((selectedTab == 4 && state.roomType.equals("poker")) || (selectedTab == 5 && state.roomType.equals("durak")));
            String value = inThisRoom ? state.roomCode : roomCodeText;
            roomCodeField = textField(g.x, g.codeY, g.innerW, value, inThisRoom ? "Код текущей комнаты" : "Введите код комнаты");
            roomCodeField.method_1888(!inThisRoom);
            if (inThisRoom) roomCodeText = state.roomCode;
        }
    }

    private void buildGameControls(TableGeometry g, CasinoViewState state, ResponsiveCasinoLayout.Mode mode) {
        int x = g.x, y = g.actionY, w = g.innerW;
        switch (selectedTab) {
            case 0 -> buildBlackjackControls(x, y, w);
            case 1 -> buildRouletteControls(x, y, w, mode);
            case 2 -> buildDiceControls(x, y, w);
            case 3 -> {
                boolean spinning = "SPINNING".equals(state.wheelState);
                String label = spinning ? "§bКолесо вращается…" : state.wheelSpinId.isBlank() ? "§dКрутить колесо" : "§dКрутить снова";
                addButton(label, () -> sendBet("wheel_spin", ""), x, y, w, 22, !spinning && !AstraTerraCasinoClient.actionPending());
            }
            case 4 -> buildPokerControls(x, y, w, state, mode);
            case 5 -> buildDurakControls(x, y, w, state, mode);
            default -> { }
        }
    }

    private void buildBlackjackControls(int x, int y, int w) {
        int gap = 4;
        addButton("§6Новая партия", () -> sendBet("bj_start", ""), x, y, w, 21, true);
        int bw = (w - gap * 2) / 3;
        addButton("§fЕщё карту", () -> AstraTerraCasinoClient.send("bj_hit"), x, y + 25, bw, 20, true);
        addButton("§fХватит", () -> AstraTerraCasinoClient.send("bj_stand"), x + bw + gap, y + 25, bw, 20, true);
        addButton("§eУдвоить", () -> AstraTerraCasinoClient.send("bj_double"), x + (bw + gap) * 2, y + 25, bw, 20, true);
    }

    private void buildRouletteControls(int x, int y, int w, ResponsiveCasinoLayout.Mode mode) {
        String[] labels = {"§cКрасное", "§8Чёрное", "§fЧётное", "§fНечётное", "§aZERO", "§d1–12", "§d13–24", "§d25–36"};
        String[] actions = {"roulette_red", "roulette_black", "roulette_even", "roulette_odd", "roulette_zero", "roulette_dozen1", "roulette_dozen2", "roulette_dozen3"};
        int columns = mode == ResponsiveCasinoLayout.Mode.COMPACT && w < 520 ? 2 : 4;
        int gap = 3, bw = (w - gap * (columns - 1)) / columns;
        for (int i = 0; i < labels.length; i++) {
            int row = i / columns, col = i % columns, idx = i;
            addButton(labels[i], () -> sendBet(actions[idx], ""), x + col * (bw + gap), y + row * 23, bw, 20, true);
        }
    }

    private void buildDiceControls(int x, int y, int w) {
        int gap = 4, bw = (w - gap * 2) / 3;
        addButton("§3Меньше 7", () -> sendBet("dice_low", ""), x, y, bw, 22, true);
        addButton("§bРовно 7", () -> sendBet("dice_seven", ""), x + bw + gap, y, bw, 22, true);
        addButton("§3Больше 7", () -> sendBet("dice_high", ""), x + (bw + gap) * 2, y, bw, 22, true);
    }

    private void buildPokerControls(int x, int y, int w, CasinoViewState state, ResponsiveCasinoLayout.Mode mode) {
        if (!state.roomType.equals("poker")) {
            int half = (w - 5) / 2;
            addButton("§aСоздать комнату", () -> sendBet("poker_create", ""), x, y, half, 22, true);
            addButton("§bВойти по коду", () -> sendRoom("poker_join"), x + half + 5, y, half, 22, true);
            return;
        }
        if (!state.multiplayerActive) {
            int third = (w - 8) / 3;
            addButton(state.roomReady ? "§eНе готов" : "§aГотов", () -> AstraTerraCasinoClient.send("poker_ready"), x, y, third, 22, true);
            addButton("§6Начать", () -> AstraTerraCasinoClient.send("poker_start"), x + third + 4, y, third, 22, state.roomHost);
            addButton("§cВыйти", () -> AstraTerraCasinoClient.send("poker_leave"), x + (third + 4) * 2, y, third, 22, true);
            return;
        }
        if (state.phase.equals(PokerPhase.BETWEEN_HANDS.label)) {
            addButton(state.roomHost ? "§6Следующая раздача" : "§7Ожидание создателя", () -> AstraTerraCasinoClient.send("poker_next"), x, y, w, 22, state.roomHost);
            return;
        }
        if (!isPokerBettingPhase(state.phase)) {
            addButton("§7Ожидание завершения раздачи", () -> {}, x, y, w, 22, false);
            return;
        }

        boolean pending = AstraTerraCasinoClient.actionPending();
        int gap = 4;
        int topW = (w - gap * 2) / 3;
        boolean canFold = state.allows(PokerAction.FOLD.id) && !pending;
        boolean canCheck = state.allows(PokerAction.CHECK.id) && !pending;
        boolean canCall = state.allows(PokerAction.CALL.id) && !pending;
        boolean canAllIn = state.allows(PokerAction.ALL_IN.id) && !pending;
        addButton("§cПас", () -> AstraTerraCasinoClient.send("poker_fold"), x, y, topW, 22, canFold);
        String middle = canCheck || state.toCall == 0 ? "§aЧек" : "§aКолл " + CasinoEngine.money(state.toCall);
        addButton(middle, () -> AstraTerraCasinoClient.send(canCheck ? "poker_check" : "poker_call"), x + topW + gap, y, topW, 22, canCheck || canCall);
        addButton("§dAll-in", () -> AstraTerraCasinoClient.send("poker_allin"), x + (topW + gap) * 2, y, topW, 22, canAllIn);

        boolean canBet = state.allows(PokerAction.BET.id) && !pending;
        boolean canRaise = state.allows(PokerAction.RAISE_TO.id) && !pending;
        String label = state.currentBet == 0 ? "§6Поставить до введённой суммы" : "§6Повысить до введённой суммы";
        addButton(label, () -> sendBet(state.currentBet == 0 ? "poker_bet" : "poker_raise_to", ""), x, y + 26, w, 22, canBet || canRaise);
        localHint = pokerActionHint(state, pending);
    }

    private void buildDurakControls(int x, int y, int w, CasinoViewState state, ResponsiveCasinoLayout.Mode mode) {
        if (!state.roomType.equals("durak")) {
            int half = (w - 5) / 2;
            addButton("§aСоздать комнату", () -> sendBet("durak_create", ""), x, y, half, 22, true);
            addButton("§bВойти по коду", () -> sendRoom("durak_join"), x + half + 5, y, half, 22, true);
            return;
        }
        if (!state.multiplayerActive) {
            int third = (w - 8) / 3;
            addButton(state.roomReady ? "§eНе готов" : "§aГотов", () -> AstraTerraCasinoClient.send("durak_ready"), x, y, third, 22, true);
            addButton("§6Начать", () -> AstraTerraCasinoClient.send("durak_start"), x + third + 4, y, third, 22, state.roomHost);
            addButton("§cВыйти", () -> AstraTerraCasinoClient.send("durak_leave"), x + (third + 4) * 2, y, third, 22, true);
            return;
        }

        addDurakCardButtons(x, y, w, state);
        int gap = 4;
        int third = (w - gap * 2) / 3;
        int controlsY = y + 25;
        String takeLabel = state.durakTaking ? "§cЗабрать со стола" : "§cВзять карты";
        addButton(takeLabel, () -> AstraTerraCasinoClient.send("durak_take"), x, controlsY, third, 20, state.allows("take"));

        String finishLabel = state.durakTaking ? "§aЗакончить доброс" : "§aБито";
        String finishAction = state.durakTaking ? "durak_finish_take" : "durak_beat";
        boolean finishEnabled = state.durakTaking ? state.allows("finish_take") : state.allows("beat");
        addButton(finishLabel, () -> AstraTerraCasinoClient.send(finishAction), x + third + gap, controlsY, third, 20, finishEnabled);

        boolean batchAllowed = state.allows("throw_rank");
        String batchLabel = durakBatchMode ? "§dВсе одинаковые" : "§7По одной карте";
        addButton(batchLabel, () -> {
            durakBatchMode = !durakBatchMode;
            localHint = durakBatchMode ? "§dРежим доброса: все карты выбранного достоинства." : "§7Режим доброса: по одной карте.";
            method_41843();
        }, x + (third + gap) * 2, controlsY, third, 20, batchAllowed);
    }

    private void addDurakCardButtons(int x, int y, int w, CasinoViewState state) {
        String csv = state.handCards;
        if (csv == null || csv.isBlank()) return;
        String[] cards = csv.split(",");
        int pageSize = Math.max(4, Math.min(8, w / 58));
        int pages = Math.max(1, (cards.length + pageSize - 1) / pageSize);
        durakPage = Math.max(0, Math.min(durakPage, pages - 1));
        int start = durakPage * pageSize;
        int end = Math.min(cards.length, start + pageSize);

        int leftNav = pages > 1 ? 52 : 0;
        int rightNav = pages > 1 ? 31 : 0;
        int cardsX = x + leftNav;
        int cardsW = Math.max(120, w - leftNav - rightNav);
        int count = Math.max(1, end - start);
        int gap = 3;
        int bw = Math.max(38, (cardsW - gap * (count - 1)) / count);

        if (pages > 1) {
            addButton("§7◀ " + (durakPage + 1) + "/" + pages, () -> {
                captureFields(); durakPage = Math.max(0, durakPage - 1); method_41843();
            }, x, y, 48, 20, durakPage > 0);
            addButton("§7▶", () -> {
                captureFields(); durakPage = Math.min(pages - 1, durakPage + 1); method_41843();
            }, x + w - 28, y, 28, 20, durakPage < pages - 1);
        }

        for (int i = start; i < end; i++) {
            int local = i - start;
            String card = cards[i].trim();
            String rank = Cards.durakRankToken(card);
            boolean canPlay = state.allows("card");
            addButton(card, () -> {
                if (durakBatchMode && state.allows("throw_rank")) {
                    AstraTerraCasinoClient.send("durak_throw_rank", 0, betUnit.id, "", rank);
                } else {
                    AstraTerraCasinoClient.send("durak_card", 0, betUnit.id, "", card);
                }
            }, cardsX + local * (bw + gap), y, bw, 20, canPlay);
        }
    }

    private void buildHistoryControls(ResponsiveCasinoLayout.Box box) {
        List<String> history = AstraTerraCasinoClient.history();
        int linesPerPage = Math.max(3, (box.h() - 70) / 36);
        int pages = Math.max(1, (history.size() + linesPerPage - 1) / linesPerPage);
        historyPage = Math.max(0, Math.min(historyPage, pages - 1));
        int y = box.bottom() - 28;
        int half = (box.w() - 29) / 2;
        addButton("§7◀", () -> { historyPage = Math.max(0, historyPage - 1); method_41843(); }, box.x() + 10, y, half, 20, historyPage > 0);
        addButton("§7▶", () -> { historyPage = Math.min(pages - 1, historyPage + 1); method_41843(); }, box.x() + 19 + half, y, half, 20, historyPage < pages - 1);
    }

    private class_342 numericField(int x, int y, int w, String value, String placeholder) {
        class_342 field = textField(x, y, w, value, placeholder);
        field.method_1880(18);
        field.method_1890(text -> text != null && text.matches("\\d{0,18}"));
        return field;
    }

    private class_342 textField(int x, int y, int w, String value, String placeholder) {
        class_342 field = new class_342(field_22793, x, y, Math.max(50, w), 20, TextBridge.of(placeholder));
        field.method_1852(value == null ? "" : value);
        field.method_47404(TextBridge.of("§8" + placeholder));
        method_37063(field);
        return field;
    }

    private void sendAmount(String action, class_342 field, CurrencyUnit unit) {
        long amount = parseAmount(field);
        if (amount <= 0) { localHint = "§cВведите положительную сумму."; return; }
        AstraTerraCasinoClient.send(action, amount, unit.id, "", "");
    }

    private void sendBet(String action, String extra) {
        long amount = parseAmount(betField);
        if (amount <= 0 && !action.equals("durak_create")) { localHint = "§cВведите положительную сумму ставки."; return; }
        AstraTerraCasinoClient.send(action, amount, betUnit.id, roomCodeField == null ? "" : roomCodeField.method_1882(), extra);
    }

    private void sendRoom(String action) {
        String code = roomCodeField == null ? roomCodeText : roomCodeField.method_1882();
        if (code == null || code.isBlank()) { localHint = "§cВведите код комнаты."; return; }
        AstraTerraCasinoClient.send(action, parseAmount(betField), betUnit.id, code, "");
    }

    private long parseAmount(class_342 field) {
        try { return field == null || field.method_1882().isBlank() ? 0 : Long.parseLong(field.method_1882()); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private void captureFields() {
        if (transferField != null) transferText = transferField.method_1882();
        if (betField != null) betText = betField.method_1882();
        if (roomCodeField != null) roomCodeText = roomCodeField.method_1882();
    }

    private void addButton(String label, Runnable action, int x, int y, int w, int h, boolean enabled) {
        String visible = enabled ? label : "§8" + Cards.strip(label);
        class_4185 button = class_4185.method_46430(TextBridge.of(visible), ignored -> {
            if (enabled) action.run();
        }).method_46434(x, y, Math.max(34, w), h).method_46431();
        method_37063(button);
    }

    @Override public void method_25393() {
        if (transferField != null) transferField.method_1865();
        if (betField != null) betField.method_1865();
        if (roomCodeField != null) roomCodeField.method_1865();
    }

    @Override public void method_25394(class_332 context, int mouseX, int mouseY, float delta) {
        method_25420(context);
        ResponsiveCasinoLayout.Layout layout = layout();
        CasinoViewState state = AstraTerraCasinoClient.view();
        ResponsiveCasinoLayout.Box frame = layout.frame();
        context.method_25294(frame.x(), frame.y(), frame.right(), frame.bottom(), 0xF00A0D15);
        context.method_25294(layout.header().x(), layout.header().y(), layout.header().right(), layout.header().bottom(), 0xFF171C2A);
        border(context, frame.x(), frame.y(), frame.right(), frame.bottom(), 0xFF7B6332);
        context.method_27534(field_22793, TextBridge.of("§6§lASTRATERRA CLUB"), frame.x() + frame.w() / 2, frame.y() + 10, 0xFFFFFF);
        String subtitle = layout.mode() == ResponsiveCasinoLayout.Mode.COMPACT ? "§7Адаптивный компактный режим" : "§7Баланс, ставки и игровые комнаты синхронизируются сервером";
        context.method_27534(field_22793, TextBridge.of(subtitle), frame.x() + frame.w() / 2, frame.y() + 27, 0xBBBBBB);

        if (layout.showWallet()) drawWalletPanel(context, layout.wallet(), state);
        if (layout.showTable()) drawTablePanel(context, layout.table(), layout.mode(), state);
        if (layout.showJournal()) drawHistoryPanel(context, layout.journal());
        drawFooter(context, layout.footer(), state);
        if (DEBUG_LAYOUT) drawLayoutDebug(context, layout);
        super.method_25394(context, mouseX, mouseY, delta);
    }

    private void drawWalletPanel(class_332 context, ResponsiveCasinoLayout.Box box, CasinoViewState state) {
        panel(context, box, "§eКЛУБНЫЙ КОШЕЛЁК");
        int x = box.x() + 12, y = box.y() + 31;
        draw(context, "§7Баланс клуба", x, y, 0xB8B8B8);
        draw(context, "§f" + CasinoEngine.money(state.wallet), x, y + 15, 0xFFFFFF);
        draw(context, "§7Баланс Numismatic", x, y + 42, 0xB8B8B8);
        draw(context, "§f" + CasinoEngine.money(state.numismatic), x, y + 57, 0xFFFFFF);
        draw(context, "§eСумма перевода", x, y + 84, 0xFFD86B);
        long preview = transferUnit.toBase(parseTextAmount(transferText));
        draw(context, "§8Итого: " + CasinoEngine.money(preview), x, y + 108, 0x888888);
        int infoY = Math.max(y + 210, box.bottom() - 67);
        for (String line : wrap("§8Номинал выбирается отдельно. Все переводы повторно проверяются сервером.", Math.max(20, (box.w() - 24) / 6))) {
            if (infoY > box.bottom() - 12) break;
            draw(context, line, x, infoY, 0x777777); infoY += 13;
        }
    }

    private void drawTablePanel(class_332 context, ResponsiveCasinoLayout.Box box, ResponsiveCasinoLayout.Mode mode, CasinoViewState state) {
        panel(context, box, "§6ИГРОВОЙ СТОЛ");
        TableGeometry g = tableGeometry(box, mode, selectedTab >= 4);
        int summaryY = g.summaryY;
        draw(context, "§7" + tabName(selectedTab) + "  §8•  §f" + state.phase, g.x, summaryY, 0xFFFFFF);
        if (mode != ResponsiveCasinoLayout.Mode.COMPACT) {
            draw(context, "§7Принятая ставка: §f" + CasinoEngine.money(state.bet), g.x, summaryY + 14, 0xFFFFFF);
        }
        draw(context, selectedTab >= 4 ? "§eСтавка / бай-ин / raise-to" : "§eРазмер ставки", g.x, g.betY - 12, 0xFFD86B);
        if (selectedTab >= 4) draw(context, "§eКод комнаты", g.x, g.codeY - 12, 0xFFD86B);

        context.method_25294(g.x, g.contentTop, g.x + g.innerW, g.contentBottom, 0xB0080B11);
        border(context, g.x, g.contentTop, g.x + g.innerW, g.contentBottom, 0xFF353D4D);
        switch (selectedTab) {
            case 0 -> drawBlackjack(context, g.x + 8, g.x + g.innerW - 8, g.contentTop + 8, g.contentBottom - 8, state);
            case 1 -> drawRoulette(context, g.x + 8, g.x + g.innerW - 8, g.contentTop + 10, state);
            case 2 -> drawDice(context, g.x + 8, g.x + g.innerW - 8, g.contentTop + 10, state);
            case 3 -> drawWheel(context, g.x + 8, g.x + g.innerW - 8, g.contentTop + 5, g.contentBottom - 5, state);
            case 4 -> drawPoker(context, g.x + 8, g.x + g.innerW - 8, g.contentTop + 7, g.contentBottom - 7, state, mode);
            case 5 -> drawDurak(context, g.x + 8, g.x + g.innerW - 8, g.contentTop + 7, g.contentBottom - 7, state, mode);
            default -> { }
        }
        String result = !localHint.isBlank() ? localHint : state.result;
        drawOutcomeBanner(context, g.x, g.resultY, g.innerW, g.actionY - g.resultY - 4, state, result, mode);
    }

    private void drawOutcomeBanner(class_332 c, int x, int y, int w, int h, CasinoViewState state,
                                   String result, ResponsiveCasinoLayout.Mode mode) {
        RoundOutcome outcome = classifyOutcome(state, result);
        int bg;
        int edge;
        String title;
        switch (outcome) {
            case WIN -> { bg = 0xD01B5E35; edge = 0xFF62E58C; title = "§a§lПОБЕДА"; }
            case LOSS -> { bg = 0xD0662028; edge = 0xFFFF6673; title = "§c§lПОРАЖЕНИЕ"; }
            case DRAW -> { bg = 0xD063541B; edge = 0xFFFFD95B; title = "§e§lНИЧЬЯ / ВОЗВРАТ"; }
            case ACTIVE -> { bg = 0xD0184258; edge = 0xFF55C7F2; title = "§b§lРАУНД ИДЁТ"; }
            case ERROR -> { bg = 0xD04B1720; edge = 0xFFFF4C5C; title = "§c§lОШИБКА"; }
            default -> { bg = 0xD0222935; edge = 0xFF596579; title = "§7§lСТАТУС"; }
        }
        int height = Math.max(25, h);
        c.method_25294(x, y, x + w, y + height, bg);
        border(c, x, y, x + w, y + height, edge);
        draw(c, title, x + 9, y + 7, 0xFFFFFF);
        int titleOffset = mode == ResponsiveCasinoLayout.Mode.COMPACT ? 88 : 132;
        int chars = Math.max(18, (w - titleOffset - 10) / 6);
        String detail = truncate(result == null || result.isBlank() ? "Ожидание действия" : result, chars);
        draw(c, "§f" + Cards.strip(detail), x + titleOffset, y + 7, 0xFFFFFF);
        if (mode != ResponsiveCasinoLayout.Mode.COMPACT && height >= 39) {
            String extra = outcomeExtra(state, outcome);
            if (!extra.isBlank()) draw(c, extra, x + 9, y + 23, 0xD8D8D8);
        }
    }

    private RoundOutcome classifyOutcome(CasinoViewState state, String text) {
        String plain = Cards.strip(text == null ? "" : text).toLowerCase(Locale.ROOT);
        if (!localHint.isBlank() || !state.errorCode.isBlank()
            || plain.contains("ошибка") || plain.contains("недостаточно") || plain.contains("слишком быстро")
            || plain.contains("не удалось") || plain.contains("неизвестное действие")) return RoundOutcome.ERROR;
        if (state.blackjackActive || plain.contains("раунд продолжается") || plain.contains("партия началась")
            || plain.contains("вы взяли карту") || "SPINNING".equals(state.wheelState)) return RoundOutcome.ACTIVE;
        if ("FINISHED".equals(state.wheelState) && state.game.contains("Колесо")) {
            if (state.wheelPayout > state.bet) return RoundOutcome.WIN;
            if (state.wheelPayout == state.bet) return RoundOutcome.DRAW;
            return RoundOutcome.LOSS;
        }

        String localName = "";
        for (CasinoPlayerView player : state.players) if (player.local) { localName = player.name == null ? "" : player.name.toLowerCase(Locale.ROOT); break; }
        if (state.roomType.equals("durak") && plain.startsWith("дурак:")) {
            return !localName.isBlank() && plain.contains(localName) ? RoundOutcome.LOSS : RoundOutcome.WIN;
        }
        if (state.roomType.equals("poker") && (plain.contains("победитель турнира") || plain.contains("забирает банк") || plain.contains("вскрытие:"))) {
            return !localName.isBlank() && plain.contains(localName) ? RoundOutcome.WIN : RoundOutcome.LOSS;
        }

        if (plain.contains("ничья") || plain.contains("ставка возвращена") || plain.contains("возврат ставки")
            || plain.contains("без дурака")) return RoundOutcome.DRAW;
        if (plain.contains("натуральный блэкджек") || plain.contains("победа") || plain.contains("выигрыш")
            || plain.contains("джекпот") || plain.contains("выплата:") && !plain.contains("выплаты нет")) return RoundOutcome.WIN;
        if (plain.contains("проиграна") || plain.contains("дилер победил") || plain.contains("перебор")
            || plain.contains("проигрыш") || plain.contains("выплаты нет") || plain.contains("пустой сектор")) return RoundOutcome.LOSS;
        return RoundOutcome.INFO;
    }

    private String outcomeExtra(CasinoViewState state, RoundOutcome outcome) {
        if (state.bet <= 0) return "";
        return switch (outcome) {
            case WIN -> "§7Ставка: §f" + CasinoEngine.money(state.bet) + " §8• §aВыплата зафиксирована сервером";
            case LOSS -> {
                long loss = "FINISHED".equals(state.wheelState) ? Math.max(0, state.bet - state.wheelPayout) : state.bet;
                yield "§7Чистый убыток: §c−" + CasinoEngine.money(loss);
            }
            case DRAW -> "§7Ставка возвращена: §e" + CasinoEngine.money(state.bet);
            case ACTIVE -> "§7Текущая ставка: §f" + CasinoEngine.money(state.bet);
            default -> "";
        };
    }

    private void drawFooter(class_332 c, ResponsiveCasinoLayout.Box footer, CasinoViewState state) {
        String sync = AstraTerraCasinoClient.actionPending() ? "§eОжидание ответа сервера…" : state.errorCode.isBlank() ? "§8Состояние синхронизировано" : "§c" + state.errorCode;
        draw(c, sync, footer.x() + Math.min(220, footer.w() / 3), footer.y() + 8, 0xAAAAAA);
    }

    private void drawBlackjack(class_332 c, int x1, int x2, int y1, int y2, CasinoViewState s) {
        int h = y2 - y1;
        int cardH = ResponsiveCasinoLayout.clamp((h - 44) / 2, 34, 58);
        int cardW = Math.max(26, cardH * 3 / 4);
        draw(c, "§cДИЛЕР" + (s.dealerValue >= 0 ? " §7— §f" + s.dealerValue : " §7— скрытая карта"), x1, y1, 0xFFFFFF);
        drawCards(c, s.dealerCards, x1, y1 + 15, x2 - x1, cardW, cardH);
        int py = y1 + 22 + cardH;
        draw(c, "§aИГРОК" + (s.playerValue >= 0 ? " §7— §f" + s.playerValue : ""), x1, py, 0xFFFFFF);
        drawCards(c, s.playerCards, x1, py + 15, x2 - x1, cardW, cardH);
    }

    private void drawDice(class_332 c, int x1, int x2, int y, CasinoViewState s) {
        int center = (x1 + x2) / 2;
        contextTitle(c, "§3БРОСОК КОСТЕЙ", center, y);
        drawTile(c, s.playerCards.isBlank() ? "—" : s.playerCards, center - 66, y + 22, 52, 52, 0xFFF1F1F1, 0xFF111111);
        drawTile(c, s.dealerCards.isBlank() ? "—" : s.dealerCards, center + 14, y + 22, 52, 52, 0xFFF1F1F1, 0xFF111111);
        if (s.playerValue >= 0) contextTitle(c, "§fСумма: §b" + s.playerValue, center, y + 82);
    }

    private void drawRoulette(class_332 c, int x1, int x2, int y, CasinoViewState s) {
        int center = (x1 + x2) / 2;
        contextTitle(c, "§6ЕВРОПЕЙСКАЯ РУЛЕТКА", center, y);
        String number = s.playerCards.replace("Число ", "");
        int fill = "0".equals(number) ? 0xFF167A3A : s.dealerCards.contains("КРАСНОЕ") ? 0xFF9B2424 : 0xFF171717;
        drawTile(c, number.isBlank() ? "—" : number, center - 35, y + 22, 70, 70, fill, 0xFFFFFFFF);
        contextTitle(c, "§f" + s.dealerCards, center, y + 100);
    }

    private void drawWheel(class_332 c, int x1, int x2, int y1, int y2, CasinoViewState s) {
        wheelRenderer.render(c, field_22793, x1, x2, y1, y2, s);
    }

    private void drawPoker(class_332 c, int x1, int x2, int y1, int y2, CasinoViewState s, ResponsiveCasinoLayout.Mode mode) {
        if (!s.roomType.equals("poker")) {
            drawIdleRoom(c, x1, x2, y1, y2, "§6ПОКЕР С ДРУЗЬЯМИ");
            return;
        }
        if (!s.multiplayerActive) {
            drawNetworkLobby(c, x1, x2, y1, y2, s, "§6ПОКЕРНОЕ ЛОББИ", 6);
            return;
        }

        int width = x2 - x1, height = y2 - y1;
        boolean compact = mode == ResponsiveCasinoLayout.Mode.COMPACT || width < 560 || height < 260;
        draw(c, "§6Комната §f" + s.roomCode + " §8• §7Бай-ин §f" + CasinoEngine.money(s.roomStake), x1, y1, 0xFFFFFF);
        draw(c, "§7Банк §f" + CasinoEngine.money(s.pot) + " §8• §7Ставка улицы §f" + CasinoEngine.money(s.currentBet)
            + " §8• §7Колл §f" + CasinoEngine.money(s.toCall), x1, y1 + 14, 0xFFFFFF);
        String timer = pokerTimer(s.turnDeadlineEpochMs);
        draw(c, "§7Ход §f" + s.currentTurn + (timer.isBlank() ? "" : " §8• §e" + timer) + " §8• §7" + s.role, x1, y1 + 28, 0xFFFFFF);

        if (compact) {
            int contentY = y1 + 47;
            if (width >= 430) {
                int handPanelW = ResponsiveCasinoLayout.clamp(width / 3, 150, 205);
                int boardW = width - handPanelW - 10;
                draw(c, "§eОбщие карты", x1, contentY, 0xFFD86B);
                int boardCardH = ResponsiveCasinoLayout.clamp((height - 100) / 2, 32, 48);
                int boardCardW = Math.max(24, boardCardH * 3 / 4);
                drawCards(c, s.boardCards, x1, contentY + 14, boardW, boardCardW, boardCardH);
                int panelX = x2 - handPanelW;
                c.method_25294(panelX, contentY - 4, x2, y2 - 3, 0xB0131924);
                border(c, panelX, contentY - 4, x2, y2 - 3, 0xFF4D6078);
                draw(c, "§aВАШИ КАРТЫ", panelX + 10, contentY + 3, 0x8CFF9B);
                int ownW = ResponsiveCasinoLayout.clamp((handPanelW - 28) / 2, 38, 58);
                int ownH = ownW * 4 / 3;
                drawCards(c, s.handCards, panelX + 10, contentY + 18, handPanelW - 20, ownW, ownH);
                draw(c, "§7Стек §f" + CasinoEngine.money(s.stack), panelX + 10, contentY + 25 + ownH, 0xFFFFFF);
                draw(c, "§7Вклад §f" + CasinoEngine.money(s.handContribution), panelX + 10, contentY + 39 + ownH, 0xFFFFFF);
                draw(c, "§7Мин. raise-to §f" + CasinoEngine.money(s.minRaise), panelX + 10, contentY + 53 + ownH, 0xFFFFFF);
            } else {
                int cardH = ResponsiveCasinoLayout.clamp((height - 118) / 2, 30, 44);
                int cardW = Math.max(23, cardH * 3 / 4);
                draw(c, "§eОбщие карты", x1, contentY, 0xFFD86B);
                drawCards(c, s.boardCards, x1, contentY + 13, width, cardW, cardH);
                int handY = contentY + cardH + 22;
                draw(c, "§aВаши карты §8• §7стек §f" + CasinoEngine.money(s.stack), x1, handY, 0xFFFFFF);
                drawCards(c, s.handCards, x1, handY + 13, width, Math.max(30, cardW), Math.max(40, cardH));
            }
            if (!s.lastAction.isBlank()) draw(c, "§8" + truncate(s.lastAction, Math.max(24, width / 6)), x1, y2 - 10, 0xAAAAAA);
            return;
        }

        int infoTop = y1 + 46;
        int handPanelW = ResponsiveCasinoLayout.clamp(width / 4, 180, 240);
        int tableX = x1 + Math.max(58, width / 12);
        int tableRight = x2 - handPanelW - 12;
        int tableY = infoTop + 13;
        int tableBottom = y2 - 10;
        c.method_25294(tableX, tableY, tableRight, tableBottom, 0xD0184A35);
        border(c, tableX, tableY, tableRight, tableBottom, 0xFF8B6C2D);
        int centerX = (tableX + tableRight) / 2;
        draw(c, "§6" + s.phase + " §8• §7D §f" + s.dealerName + " §8• §7SB §f" + s.smallBlindName + " §8• §7BB §f" + s.bigBlindName,
            tableX + 9, tableY + 7, 0xFFFFFF);

        int cardH = ResponsiveCasinoLayout.clamp((tableBottom - tableY) / 4, 32, 52);
        int cardW = Math.max(24, cardH * 3 / 4);
        String[] board = splitCards(s.boardCards);
        int boardTotal = board.length == 0 ? cardW * 5 + 16 : board.length * cardW + Math.max(0, board.length - 1) * 4;
        int boardX = centerX - boardTotal / 2;
        draw(c, "§eОБЩИЕ КАРТЫ", centerX - 40, tableY + 26, 0xFFD86B);
        if (board.length == 0) {
            for (int i = 0; i < 5; i++) drawTile(c, "", boardX + i * (cardW + 4), tableY + 40, cardW, cardH, 0xFF263C34, 0xFFAAAAAA);
        } else {
            drawCards(c, s.boardCards, boardX, tableY + 40, boardTotal + 2, cardW, cardH);
        }

        draw(c, "§6БАНК §f" + CasinoEngine.money(s.pot), centerX - 42, tableY + 47 + cardH, 0xFFFFFF);
        if (!s.sidePots.isBlank()) draw(c, "§d" + truncate(s.sidePots, Math.max(30, (tableRight - tableX) / 7)), tableX + 8, tableY + 63 + cardH, 0xE5B3FF);

        CasinoPlayerView local = null;
        List<CasinoPlayerView> opponents = new ArrayList<>();
        for (CasinoPlayerView player : s.players) {
            if (player.local) local = player; else opponents.add(player);
        }
        int tableWidth = tableRight - tableX;
        int seatW = ResponsiveCasinoLayout.clamp(tableWidth / 4, 92, 145);
        int seatH = 42;
        int[][] positions = pokerOpponentPositions(opponents.size(), tableX, tableRight, tableY, tableBottom, seatW, seatH);
        for (int i = 0; i < opponents.size() && i < positions.length; i++) drawPokerSeat(c, opponents.get(i), positions[i][0], positions[i][1], seatW, seatH);

        if (local == null) {
            local = new CasinoPlayerView(); local.name = "Вы"; local.stack = s.stack; local.local = true; local.cards = s.handCards;
        }
        int localW = Math.min(190, Math.max(125, tableWidth / 3));
        int localX = centerX - localW / 2;
        int localY = tableBottom - 49;
        drawPokerSeat(c, local, localX, localY, localW, 40);

        int handX = tableRight + 8;
        int handRight = x2;
        c.method_25294(handX, tableY, handRight, tableBottom, 0xD0121823);
        border(c, handX, tableY, handRight, tableBottom, 0xFF4D6078);
        draw(c, "§a§lВАШИ КАРТЫ", handX + 12, tableY + 10, 0x8CFF9B);
        String ownCards = local.cards == null || local.cards.isBlank() ? s.handCards : local.cards;
        int ownCardW = ResponsiveCasinoLayout.clamp((handPanelW - 38) / 2, 48, 68);
        int ownCardH = ownCardW * 4 / 3;
        drawCards(c, ownCards, handX + 12, tableY + 28, handPanelW - 24, ownCardW, ownCardH);
        int detailsY = tableY + 38 + ownCardH;
        draw(c, "§7Стек: §f" + CasinoEngine.money(s.stack), handX + 12, detailsY, 0xFFFFFF);
        draw(c, "§7Роль: §f" + s.role, handX + 12, detailsY + 14, 0xFFFFFF);
        draw(c, "§7Колл: §f" + CasinoEngine.money(s.toCall), handX + 12, detailsY + 28, 0xFFFFFF);
        draw(c, "§7Raise-to min: §f" + CasinoEngine.money(s.minRaise), handX + 12, detailsY + 42, 0xFFFFFF);
        draw(c, "§7Вклад: §f" + CasinoEngine.money(s.handContribution), handX + 12, detailsY + 56, 0xFFFFFF);
        int msgY = detailsY + 76;
        for (String line : wrap(s.lastAction.isBlank() ? s.roomStatus : s.lastAction, Math.max(18, (handPanelW - 24) / 6))) {
            if (msgY > tableBottom - 12) break;
            draw(c, "§8" + line, handX + 12, msgY, 0xAAAAAA);
            msgY += 12;
        }

        draw(c, "§7Вклад: улица §f" + CasinoEngine.money(s.streetContribution) + " §8• §7раздача §f" + CasinoEngine.money(s.handContribution),
            tableX + 8, tableBottom - 14, 0xCCCCCC);
        if (!s.lastAction.isBlank()) draw(c, "§8Последнее: §f" + truncate(s.lastAction, Math.max(24, tableWidth / 7)), tableX + 8, tableBottom - 27, 0xAAAAAA);
    }

    private void drawNetworkLobby(class_332 c, int x1, int x2, int y1, int y2, CasinoViewState s,
                                  String title, int maxPlayers) {
        int width = x2 - x1;
        contextTitle(c, title, (x1 + x2) / 2, y1 + 4);
        contextTitle(c, "§6КОД КОМНАТЫ: §f§l" + s.roomCode, (x1 + x2) / 2, y1 + 23);
        int count = s.players.isEmpty() ? countRoomPlayers(s.roomPlayers) : s.players.size();
        contextTitle(c, "§7Игроки: §f" + count + "/" + maxPlayers + " §8• §7Ставка: §f" + CasinoEngine.money(s.roomStake), (x1 + x2) / 2, y1 + 41);

        int listTop = y1 + 63;
        int listBottom = y2 - 45;
        c.method_25294(x1, listTop, x2, listBottom, 0xA0121721);
        border(c, x1, listTop, x2, listBottom, 0xFF3D485A);
        draw(c, "§bУЧАСТНИКИ", x1 + 10, listTop + 8, 0xFFFFFF);
        int py = listTop + 27;
        if (!s.players.isEmpty()) {
            int columns = width >= 620 ? 2 : 1;
            int colWidth = (width - 24) / columns;
            for (int i = 0; i < s.players.size(); i++) {
                CasinoPlayerView player = s.players.get(i);
                int col = i % columns;
                int row = i / columns;
                int px = x1 + 11 + col * colWidth;
                int lineY = py + row * 21;
                if (lineY > listBottom - 17) break;
                String icon = player.host ? "§6★ " : "§7• ";
                String ready = player.ready ? " §a[готов]" : " §7[не готов]";
                String connected = player.connected ? "" : " §c[отключён]";
                draw(c, icon + "§f" + truncate(player.name, Math.max(8, colWidth / 8)) + ready + connected, px, lineY, 0xFFFFFF);
            }
        } else {
            drawPlayerList(c, s.roomPlayers, x1 + 11, py, listBottom - 10, Math.max(22, width / 7));
        }

        String role = s.roomHost ? "§6Вы — создатель комнаты" : "§7Участник комнаты";
        String ready = s.roomReady ? "§aГотов" : "§eНе готов";
        draw(c, role + " §8• " + ready, x1 + 10, y2 - 34, 0xFFFFFF);
        String status = s.roomStatus == null || s.roomStatus.isBlank() ? "Ожидание участников." : s.roomStatus;
        draw(c, truncate(status, Math.max(28, width / 7)), x1 + 10, y2 - 19, 0xFFFFFF);
    }

    private int countRoomPlayers(String players) {
        if (players == null || players.isBlank()) return 0;
        return players.split("\\|").length;
    }

    private int[][] pokerOpponentPositions(int count, int x1, int x2, int tableY, int tableBottom, int seatW, int seatH) {
        if (count <= 0) return new int[0][0];
        int left = x1 + 3;
        int right = x2 - seatW - 3;
        int center = (x1 + x2 - seatW) / 2;
        int top = tableY + 3;
        int middle = (tableY + tableBottom - seatH) / 2;
        int[][] slots = {{center, top}, {left, top}, {right, top}, {left, middle}, {right, middle}};
        int[][] layouts = switch (count) {
            case 1 -> new int[][]{slots[0]};
            case 2 -> new int[][]{slots[1], slots[2]};
            case 3 -> new int[][]{slots[1], slots[0], slots[2]};
            case 4 -> new int[][]{slots[1], slots[2], slots[3], slots[4]};
            default -> slots;
        };
        return layouts;
    }

    private void drawPokerSeat(class_332 c, CasinoPlayerView player, int x, int y, int w, int h) {
        int bg = player.activeTurn ? 0xE05B4A18 : player.folded ? 0xC02A2A2A : !player.connected ? 0xC04A2020 : player.local ? 0xD0203A52 : 0xD0161C27;
        int borderColor = player.activeTurn ? 0xFFFFC94A : player.local ? 0xFF5CC8FF : 0xFF4B5568;
        c.method_25294(x, y, x + w, y + h, bg);
        border(c, x, y, x + w, y + h, borderColor);
        String markers = (player.dealer ? " §6[D]" : "") + (player.smallBlind ? " §e[SB]" : "") + (player.bigBlind ? " §c[BB]" : "");
        draw(c, (player.local ? "§a" : "§f") + truncate(player.name, Math.max(8, w / 8)) + markers, x + 5, y + 5, 0xFFFFFF);
        draw(c, "§7" + CasinoEngine.money(player.stack) + " §8• " + player.status, x + 5, y + 18, 0xCCCCCC);
        if (player.cards != null && !player.cards.isBlank() && !player.local) draw(c, "§8" + truncate(player.cards, Math.max(8, w / 7)), x + 5, y + 30, 0xAAAAAA);
    }

    private String pokerTimer(long deadline) {
        if (deadline <= 0) return "";
        long seconds = Math.max(0, (deadline - System.currentTimeMillis() + 999) / 1000);
        return seconds + "с";
    }

    private void drawDurak(class_332 c, int x1, int x2, int y1, int y2, CasinoViewState s, ResponsiveCasinoLayout.Mode mode) {
        if (!s.roomType.equals("durak")) {
            drawIdleRoom(c, x1, x2, y1, y2, "§6ДУРАК С ДРУЗЬЯМИ");
            return;
        }
        if (!s.multiplayerActive) {
            drawNetworkLobby(c, x1, x2, y1, y2, s, "§6ЛОББИ ДУРАКА", 6);
            return;
        }
        draw(c, "§6Комната §f" + s.roomCode + " §8• §7Взнос §f" + CasinoEngine.money(s.roomStake), x1, y1, 0xFFFFFF);
        draw(c, "§7Козырь §f" + (s.trumpCard.isBlank() ? "—" : s.trumpCard) + " §8• §7В колоде §f" + s.deckCount, x1, y1 + 14, 0xFFFFFF);
        draw(c, "§7Роль §f" + s.role + (s.durakTaking ? " §8• §cЗащищающийся забирает карты" : ""), x1, y1 + 28, 0xFFFFFF);
        draw(c, "§7Ход §f" + s.currentTurn, x1, y1 + 42, 0xFFFFFF);
        int tableY = y1 + 62;
        draw(c, "§eКарты на столе", x1, tableY, 0xFFD86B);
        drawDurakTable(c, s.tableCards, x1, tableY + 14, x2 - x1);
        if (mode != ResponsiveCasinoLayout.Mode.COMPACT) {
            int listX = Math.max(x1 + 250, x2 - 225);
            draw(c, "§bИгроки", listX, tableY, 0xFFFFFF);
            if (!s.players.isEmpty()) {
                int py = tableY + 15;
                for (CasinoPlayerView player : s.players) {
                    if (py > y2 - 12) break;
                    String ready = player.ready ? " §a[готов]" : "";
                    String connected = player.connected ? "" : " §c[отключён]";
                    draw(c, (player.host ? "§6★ " : "§7• ") + "§f" + truncate(player.name, 19) + ready + connected + " §8(" + player.handContribution + ")", listX, py, 0xFFFFFF);
                    py += 14;
                }
            } else {
                drawPlayerList(c, s.roomPlayers, listX, tableY + 15, y2 - 8, 30);
            }
        }
    }

    private void drawIdleRoom(class_332 c, int x1, int x2, int y1, int y2, String title) {
        int centerX = (x1 + x2) / 2;
        int centerY = (y1 + y2) / 2;
        contextTitle(c, title, centerX, centerY - 12);
        contextTitle(c, "§8Нет активной комнаты", centerX, centerY + 9);
    }

    private void drawDurakTable(class_332 c, String table, int x, int y, int width) {
        if (table == null || table.isBlank()) { draw(c, "§8Стол пуст", x, y + 16, 0x777777); return; }
        String[] pairs = table.split("\\|");
        int pairW = 78, gap = 4, max = Math.max(1, width / (pairW + gap));
        for (int i = 0; i < pairs.length && i < max; i++) {
            String[] cards = pairs[i].split(">", 2);
            drawTile(c, cards[0], x + i * (pairW + gap), y, 36, 49, 0xFFF3EFE4, 0xFF111111);
            drawTile(c, cards.length > 1 ? cards[1] : "?", x + i * (pairW + gap) + 30, y + 10, 36, 49, 0xFFE8E2D3, 0xFF111111);
        }
    }

    private void drawPlayerList(class_332 c, String players, int x, int y, int maxY, int maxChars) {
        if (players == null || players.isBlank()) { draw(c, "§8Нет игроков", x, y, 0x777777); return; }
        for (String player : players.split("\\|")) {
            for (String line : wrap(player, maxChars)) {
                if (y > maxY) return;
                draw(c, line, x, y, 0xFFFFFF); y += 12;
            }
            y += 2;
        }
    }

    private void drawHistoryPanel(class_332 context, ResponsiveCasinoLayout.Box box) {
        panel(context, box, "§bЖУРНАЛ КЛУБА");
        List<String> history = AstraTerraCasinoClient.history();
        if (history.isEmpty()) {
            draw(context, "§8Здесь появятся ставки,", box.x() + 11, box.y() + 32, 0x777777);
            draw(context, "§8ходы, выигрыши и выплаты.", box.x() + 11, box.y() + 46, 0x777777);
            return;
        }
        int linesPerPage = Math.max(3, (box.h() - 70) / 36);
        int start = historyPage * linesPerPage;
        int end = Math.min(history.size(), start + linesPerPage);
        int y = box.y() + 31, maxChars = Math.max(20, (box.w() - 22) / 6), maxY = box.bottom() - 34;
        for (int i = start; i < end; i++) {
            for (String line : wrap(history.get(i), maxChars)) {
                if (y > maxY) break;
                draw(context, line, box.x() + 10, y, 0xFFFFFF); y += 12;
            }
            y += 4;
        }
    }

    private String[] splitCards(String csv) {
        if (csv == null || csv.isBlank()) return new String[0];
        return Arrays.stream(csv.split(",")).map(String::trim).filter(value -> !value.isBlank()).toArray(String[]::new);
    }

    private void drawCards(class_332 c, String csv, int x, int y, int availableWidth, int cardW, int cardH) {
        if (csv == null || csv.isBlank()) { draw(c, "§8Карты пока не розданы", x, y + 15, 0x777777); return; }
        String[] cards = csv.split(",");
        int gap = Math.max(2, Math.min(5, cardW / 8));
        int max = Math.max(1, availableWidth / (cardW + gap));
        for (int i = 0; i < cards.length && i < max; i++) {
            String rank = cards[i].trim(); int bx = x + i * (cardW + gap);
            int bg = "?".equals(Cards.strip(rank)) ? 0xFF3B284F : 0xFFF3EFE4;
            int fg = "?".equals(Cards.strip(rank)) ? 0xFFE6B8FF : 0xFF1B1B1B;
            drawTile(c, rank, bx, y, cardW, cardH, bg, fg);
        }
        if (cards.length > max) draw(c, "§7+" + (cards.length - max), x + max * (cardW + gap), y + cardH / 2, 0xFFFFFF);
    }

    private void drawTile(class_332 c, String text, int x, int y, int w, int h, int bg, int fg) {
        c.method_25294(x, y, x + w, y + h, bg);
        border(c, x, y, x + w, y + h, 0xFF9A8A65);
        c.method_27534(field_22793, TextBridge.of(text), x + w / 2, y + h / 2 - 4, fg);
    }

    private void panel(class_332 c, ResponsiveCasinoLayout.Box box, String title) {
        c.method_25294(box.x(), box.y(), box.right(), box.bottom(), 0xC0111520);
        c.method_25294(box.x(), box.y(), box.right(), box.y() + 25, 0xEE202635);
        border(c, box.x(), box.y(), box.right(), box.bottom(), 0xFF414A5D);
        c.method_27534(field_22793, TextBridge.of(title), box.x() + box.w() / 2, box.y() + 8, 0xFFFFFF);
    }

    private void border(class_332 c, int x1, int y1, int x2, int y2, int color) {
        c.method_25294(x1, y1, x2, y1 + 1, color); c.method_25294(x1, y2 - 1, x2, y2, color);
        c.method_25294(x1, y1, x1 + 1, y2, color); c.method_25294(x2 - 1, y1, x2, y2, color);
    }

    private void draw(class_332 c, String text, int x, int y, int color) {
        // Minecraft 1.20.1 intermediary signature returns int (rendered width), not void.
        // Compile this source against real Yarn/intermediary mappings or a stub declaring the int return type.
        c.method_27535(field_22793, TextBridge.of(text), x, y, color);
    }
    private void contextTitle(class_332 c, String text, int x, int y) { c.method_27534(field_22793, TextBridge.of(text), x, y, 0xFFFFFF); }

    private ResponsiveCasinoLayout.Layout layout() {
        return ResponsiveCasinoLayout.calculate(field_22789, field_22790, compactPage, mediumSidePage);
    }

    private TableGeometry tableGeometry(ResponsiveCasinoLayout.Box box, ResponsiveCasinoLayout.Mode mode, boolean networkGame) {
        int pad = mode == ResponsiveCasinoLayout.Mode.COMPACT ? 8 : 12;
        int x = box.x() + pad;
        int innerW = box.w() - pad * 2;
        int columns = tabColumns(box.w());
        int rows = (6 + columns - 1) / columns;
        int tabsY = box.y() + 29;
        int summaryY = tabsY + rows * 23 + 3;
        int summaryH = mode == ResponsiveCasinoLayout.Mode.COMPACT ? 17 : 33;
        int betY = summaryY + summaryH + 13;
        int codeY = betY + 31;
        int contentTop = networkGame ? codeY + 27 : betY + 27;
        int actionH;
        if (selectedTab == 1) actionH = mode == ResponsiveCasinoLayout.Mode.COMPACT && innerW < 520 ? 96 : 48;
        else if (selectedTab == 4) actionH = 50;
        else if (selectedTab == 5) actionH = 50;
        else actionH = 48;
        int actionY = box.bottom() - actionH - 10;
        int resultH = mode == ResponsiveCasinoLayout.Mode.COMPACT ? 34 : 49;
        int resultY = actionY - resultH;
        int contentBottom = Math.max(contentTop + 52, resultY - 5);
        return new TableGeometry(x, innerW, tabsY, summaryY, betY, codeY, contentTop, contentBottom, resultY, actionY);
    }

    private int tabColumns(int panelWidth) {
        if (panelWidth >= 720) return 6;
        if (panelWidth >= 420) return 3;
        return 2;
    }

    private static boolean isPokerBettingPhase(String phase) {
        return PokerPhase.PREFLOP.label.equals(phase) || PokerPhase.FLOP.label.equals(phase) || PokerPhase.TURN.label.equals(phase) || PokerPhase.RIVER.label.equals(phase);
    }

    private static String pokerActionHint(CasinoViewState state, boolean pending) {
        if (pending) return "§eДействие отправлено. Ожидается подтверждение сервера.";
        if (!state.canAct) return "§8Сейчас ход игрока: §f" + state.currentTurn;
        if (state.toCall > 0) return "§7Нужно уравнять §f" + CasinoEngine.money(state.toCall) + "§7. Минимальный raise-to: §f" + CasinoEngine.money(state.minRaise);
        return "§7Доступен чек. Минимальная ставка до: §f" + CasinoEngine.money(state.minRaise);
    }

    private long parseTextAmount(String text) {
        try { return text == null || text.isBlank() ? 0 : Long.parseLong(text); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private static List<String> wrap(String text, int maxChars) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isBlank()) { result.add(""); return result; }
        String color = ""; StringBuilder line = new StringBuilder(), plain = new StringBuilder();
        for (String word : text.split(" ")) {
            String visible = word.replaceAll("§.", "");
            if (plain.length() > 0 && plain.length() + 1 + visible.length() > maxChars) {
                result.add(line.toString()); line = new StringBuilder(color); plain = new StringBuilder();
            }
            if (plain.length() > 0) { line.append(' '); plain.append(' '); }
            line.append(word); plain.append(visible);
            int idx = word.lastIndexOf('§'); if (idx >= 0 && idx + 1 < word.length()) color = "§" + word.charAt(idx + 1);
        }
        if (line.length() > 0) result.add(line.toString());
        return result;
    }

    private static String truncate(String text, int max) {
        String plain = Cards.strip(text);
        if (plain.length() <= max) return text;
        return plain.substring(0, Math.max(1, max - 1)) + "…";
    }

    private static int tabFromState(CasinoViewState s) {
        if (s.roomType.equals("poker")) return 4;
        if (s.roomType.equals("durak")) return 5;
        if (s.game.contains("Рулетка")) return 1;
        if (s.game.contains("Кости")) return 2;
        if (s.game.contains("Колесо")) return 3;
        return 0;
    }

    private static String tabName(int tab) {
        return switch (tab) {
            case 0 -> "Блэкджек";
            case 1 -> "Рулетка";
            case 2 -> "Кости";
            case 3 -> "Колесо";
            case 4 -> "Покер с друзьями";
            case 5 -> "Дурак с друзьями";
            default -> "Клуб";
        };
    }

    private void drawLayoutDebug(class_332 c, ResponsiveCasinoLayout.Layout l) {
        border(c, l.header().x(), l.header().y(), l.header().right(), l.header().bottom(), 0xFFFF00FF);
        if (l.navigation().w() > 0) border(c, l.navigation().x(), l.navigation().y(), l.navigation().right(), l.navigation().bottom(), 0xFFFFFF00);
        if (l.showWallet()) border(c, l.wallet().x(), l.wallet().y(), l.wallet().right(), l.wallet().bottom(), 0xFF00FF00);
        if (l.showTable()) border(c, l.table().x(), l.table().y(), l.table().right(), l.table().bottom(), 0xFFFF8800);
        if (l.showJournal()) border(c, l.journal().x(), l.journal().y(), l.journal().right(), l.journal().bottom(), 0xFF00FFFF);
        border(c, l.footer().x(), l.footer().y(), l.footer().right(), l.footer().bottom(), 0xFFFF0000);
    }

    private String widgetKey(CasinoViewState state) {
        return selectedTab + "|" + state.roomType + "|" + state.roomCode + "|" + state.phase + "|" + state.allowedActions + "|"
            + state.roomHost + "|" + state.roomReady + "|" + state.multiplayerActive + "|" + state.toCall + "|"
            + state.currentBet + "|" + state.roomPlayers + "|" + state.deckCount + "|" + state.durakTaking + "|" + state.revision
            + "|" + state.wheelSpinId + "|" + state.wheelState + "|" + state.wheelSectorIndex + "|" + state.wheelElapsedMs
            + "|" + compactPage + "|" + mediumSidePage + "|" + field_22789 + "x" + field_22790;
    }

    @Override public void method_25419() {
        wheelRenderer.reset();
        if (ACTIVE == this) ACTIVE = null;
        super.method_25419();
    }

    @Override public boolean method_25421() { return false; }

    private enum RoundOutcome { WIN, LOSS, DRAW, ACTIVE, ERROR, INFO }

    private record TableGeometry(int x, int innerW, int tabsY, int summaryY, int betY, int codeY,
                                 int contentTop, int contentBottom, int resultY, int actionY) {}
}

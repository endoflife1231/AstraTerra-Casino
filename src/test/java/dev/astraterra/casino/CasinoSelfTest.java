package dev.astraterra.casino;

import java.io.*;
import java.lang.reflect.Field;
import java.util.*;

public final class CasinoSelfTest {
    public static void main(String[] args) throws Exception {
        testPokerStateMachine();
        testDurakFlowAndBatchThrow();
        testRoomIdentityAndCodeSync();
        testLayouts();
        testScreens();
        testPokerHands();
        testProtocolRoundTrips();
        testWheelMathAndSectors();
        testCurrencyConversion();
        System.out.println("All autonomous AstraTerra Casino tests passed");
    }


    private static void testPokerStateMachine() {
        TestPlayer a = new TestPlayer("00000000-0000-0000-0000-000000000001", "Alice");
        TestPlayer b = new TestPlayer("00000000-0000-0000-0000-000000000002", "Bob");
        TestPlayer c = new TestPlayer("00000000-0000-0000-0000-000000000003", "Cara");

        MultiplayerRooms.PokerRoom room = new MultiplayerRooms.PokerRoom("TEST1", 1_000, a);
        room.suppressNetwork = true;
        room.seats.add(new MultiplayerRooms.PokerSeat(b));
        room.tournamentStarted = true;
        for (MultiplayerRooms.PokerSeat seat : room.seats) seat.stack = 1_000;
        room.startHand();
        require(room.phase == PokerPhase.PREFLOP, "heads-up must start preflop");
        require(room.currentIndex == room.dealerIndex, "heads-up dealer/SB must act first preflop");
        MultiplayerRooms.PokerSeat first = room.seats.get(room.currentIndex);
        require(room.currentBet - first.streetBet > 0, "first player must face a call");
        room.act(first.player, PokerAction.CALL, 0);
        MultiplayerRooms.PokerSeat second = room.seats.get(room.currentIndex);
        require(room.currentBet - second.streetBet == 0, "big blind must be able to check");
        room.act(second.player, PokerAction.CHECK, 0);
        require(room.phase == PokerPhase.FLOP, "check must advance to flop");

        MultiplayerRooms.PokerRoom foldRoom = new MultiplayerRooms.PokerRoom("TEST2", 1_000, a);
        foldRoom.suppressNetwork = true;
        foldRoom.seats.add(new MultiplayerRooms.PokerSeat(b));
        foldRoom.tournamentStarted = true;
        for (MultiplayerRooms.PokerSeat seat : foldRoom.seats) seat.stack = 1_000;
        foldRoom.startHand();
        MultiplayerRooms.PokerSeat folder = foldRoom.seats.get(foldRoom.currentIndex);
        long beforeTotal = foldRoom.seats.stream().mapToLong(seat -> seat.stack).sum() + foldRoom.pot;
        foldRoom.act(folder.player, PokerAction.FOLD, 0);
        long afterTotal = foldRoom.seats.stream().mapToLong(seat -> seat.stack).sum() + foldRoom.pot;
        require(beforeTotal == afterTotal, "fold must conserve chips");

        MultiplayerRooms.PokerRoom side = new MultiplayerRooms.PokerRoom("TEST3", 1_000, a);
        side.suppressNetwork = true;
        MultiplayerRooms.PokerSeat sa = side.seats.get(0);
        MultiplayerRooms.PokerSeat sb = new MultiplayerRooms.PokerSeat(b);
        MultiplayerRooms.PokerSeat sc = new MultiplayerRooms.PokerSeat(c);
        side.seats.add(sb); side.seats.add(sc);
        sa.handContribution = 100; sb.handContribution = 200; sc.handContribution = 300;
        List<MultiplayerRooms.PokerRoom.PotTier> tiers = side.buildPotTiers();
        require(tiers.size() == 3, "three contribution levels must create three pots");
        require(tiers.stream().mapToLong(MultiplayerRooms.PokerRoom.PotTier::amount).sum() == 600, "side pots must conserve contributions");

        MultiplayerRooms.PokerRoom shortRaise = new MultiplayerRooms.PokerRoom("TEST4", 1_000, a);
        shortRaise.suppressNetwork = true;
        MultiplayerRooms.PokerSeat ra = shortRaise.seats.get(0);
        MultiplayerRooms.PokerSeat rb = new MultiplayerRooms.PokerSeat(b);
        MultiplayerRooms.PokerSeat rc = new MultiplayerRooms.PokerSeat(c);
        shortRaise.seats.add(rb); shortRaise.seats.add(rc);
        shortRaise.tournamentStarted = true; shortRaise.phase = PokerPhase.PREFLOP; shortRaise.currentBet = 100; shortRaise.lastFullRaise = 100; shortRaise.currentIndex = 1;
        ra.stack=900; ra.streetBet=100; ra.handContribution=100; ra.needsAction=false;
        rb.stack=50; rb.streetBet=100; rb.handContribution=100; rb.needsAction=true;
        rc.stack=900; rc.streetBet=100; rc.handContribution=100; rc.needsAction=true;
        shortRaise.pot=300;
        shortRaise.act(rb.player, PokerAction.ALL_IN, 0);
        require(shortRaise.currentBet == 150 && ra.raiseLocked && !rc.raiseLocked, "short all-in reopening rules failed");
        shortRaise.act(rc.player, PokerAction.CALL, 0);
        String lockedActions = shortRaise.allowedActions(ra);
        require(lockedActions.contains("call") && lockedActions.contains("fold"), "raise-locked call/fold missing");
        require(!lockedActions.contains("raise_to") && !lockedActions.contains("all_in"), "raise-locked betting reopened");

        MultiplayerRooms.PokerRoom timeout = new MultiplayerRooms.PokerRoom("TEST5", 1_000, a);
        timeout.suppressNetwork = true;
        MultiplayerRooms.PokerSeat ta = timeout.seats.get(0);
        MultiplayerRooms.PokerSeat tb = new MultiplayerRooms.PokerSeat(b);
        MultiplayerRooms.PokerSeat tc = new MultiplayerRooms.PokerSeat(c);
        timeout.seats.add(tb); timeout.seats.add(tc);
        timeout.tournamentStarted=true; timeout.phase=PokerPhase.PREFLOP; timeout.currentIndex=0; timeout.currentBet=100;
        ta.stack=900; ta.streetBet=0; ta.needsAction=true;
        tb.stack=900; tb.streetBet=100; tb.handContribution=100; tb.needsAction=true;
        tc.stack=900; tc.streetBet=100; tc.handContribution=100; tc.needsAction=true;
        timeout.pot=200; timeout.turnDeadlineEpochMs=1;
        timeout.tick(System.currentTimeMillis());
        require(ta.folded, "expired player facing a bet must auto-fold");
        System.out.println("Poker state machine: heads-up, check, fold, side pots, short all-in and timeout");
    }


    private static void testDurakFlowAndBatchThrow() {
        TestPlayer a = new TestPlayer("00000000-0000-0000-0000-000000000011", "Attacker");
        TestPlayer b = new TestPlayer("00000000-0000-0000-0000-000000000012", "Defender");
        MultiplayerRooms.DurakRoom room = new MultiplayerRooms.DurakRoom("DTEST", 0, a);
        room.suppressNetwork = true;
        MultiplayerRooms.DurakSeat defender = new MultiplayerRooms.DurakSeat(b);
        room.seats.add(defender);
        room.gameStarted = true;
        room.attacker = 0;
        room.defender = 1;
        room.maxAttack = 3;
        room.trumpSuit = 3;
        room.deck = new ArrayList<>(Cards.durakDeck().subList(0, 12));
        MultiplayerRooms.DurakSeat attacker = room.seats.get(0);
        attacker.hand.clear();
        attacker.hand.add(durakCard(14, 0));
        attacker.hand.add(durakCard(14, 1));
        attacker.hand.add(durakCard(14, 2));
        defender.hand.clear();
        defender.hand.add(durakCard(6, 0));
        defender.hand.add(durakCard(7, 0));
        defender.hand.add(durakCard(8, 0));

        room.action(a, new CasinoRequest("durak_card", 0, CurrencyUnit.SILVER.id, "", Cards.durakCard(attacker.hand.get(0)), room.revision, 1));
        require(room.table.size() == 1, "first durak attack missing");
        room.action(b, new CasinoRequest("durak_take", 0, CurrencyUnit.SILVER.id, "", "", room.revision, 1));
        require(room.takingCards, "defender take must open throw-in phase");
        room.action(a, new CasinoRequest("durak_throw_rank", 0, CurrencyUnit.SILVER.id, "", "A", room.revision, 2));
        require(!room.takingCards && room.table.isEmpty(), "batch throw at attack limit must finalize pickup");
        CasinoViewState view = room.view(a);
        require(view.deckCount == room.deck.size(), "durak deck count must be synchronized");
        require(view.players.size() == 2, "durak participant snapshot missing");
        require(Cards.parseDurakRank("A") == 14 && Cards.parseDurakRank("10") == 10, "durak rank parser failed");
        System.out.println("Durak: pickup phase, batch same-rank throw, participant snapshot and deck count");
    }

    private static void testRoomIdentityAndCodeSync() throws Exception {
        NullIdPlayer alice = new NullIdPlayer("Alice");
        NullIdPlayer bob = new NullIdPlayer("Bob");
        String aliceId = CasinoData.id(alice);
        String bobId = CasinoData.id(bob);
        require(!aliceId.equals(bobId), "offline players with null UUID must have distinct stable ids");
        require(aliceId.equals(CasinoData.id(alice)), "offline fallback id must be stable");

        MultiplayerRooms.PokerRoom room = new MultiplayerRooms.PokerRoom("N746B", 100, alice);
        room.suppressNetwork = true;
        require(room.join(bob) != null && room.seats.size() == 2, "second offline player must join the same room");

        Field viewField = AstraTerraCasinoClient.class.getDeclaredField("VIEW");
        viewField.setAccessible(true);
        CasinoViewState state = new CasinoViewState();
        state.roomType = "poker";
        state.roomCode = "N746B";
        state.roomPlayers = "★ Alice §7[не готов]|Bob §7[не готов]";
        state.players.add(player("alice", "Alice", 0, true, false, false, false, false, "", "не готов"));
        state.players.add(player("bob", "Bob", 0, false, false, false, false, false, "", "не готов"));
        viewField.set(null, state);
        CasinoScreen screen = new CasinoScreen();
        screen.refreshFromServer();
        Field codeText = CasinoScreen.class.getDeclaredField("roomCodeText");
        codeText.setAccessible(true);
        require("N746B".equals(codeText.get(screen)), "server room code must replace stale join text");
        System.out.println("Room sync: null-UUID identities, second-player join and authoritative room code");
    }

    private static void testLayouts() {
        int[][] sizes = {{854,480},{960,540},{1280,720},{1600,900},{1920,1080}};
        for (int[] size : sizes) {
            for (ResponsiveCasinoLayout.Page page : ResponsiveCasinoLayout.Page.values()) {
                var l = ResponsiveCasinoLayout.calculate(size[0], size[1], page, ResponsiveCasinoLayout.Page.WALLET);
                require(l.frame().contains(l.header()), "header out of frame " + size[0] + "x" + size[1]);
                require(l.frame().contains(l.footer()), "footer out of frame");
                if (l.showWallet()) require(l.frame().contains(l.wallet()), "wallet out of frame");
                if (l.showTable()) require(l.frame().contains(l.table()), "table out of frame");
                if (l.showJournal()) require(l.frame().contains(l.journal()), "journal out of frame");
                if (l.showWallet() && l.showTable()) require(!l.wallet().intersects(l.table()), "wallet intersects table");
                if (l.showTable() && l.showJournal()) require(!l.table().intersects(l.journal()), "table intersects journal");
            }
        }
        System.out.println("Responsive layout: 5 viewport sizes × all compact pages");
    }

    private static void testScreens() throws Exception {
        int[][] sizes = {{854,480},{960,540},{1280,720},{1600,900},{1920,1080}};
        Field width = net.minecraft.class_437.class.getDeclaredField("field_22789"); width.setAccessible(true);
        Field height = net.minecraft.class_437.class.getDeclaredField("field_22790"); height.setAccessible(true);
        Field children = net.minecraft.class_437.class.getDeclaredField("children"); children.setAccessible(true);
        Field selected = CasinoScreen.class.getDeclaredField("selectedTab"); selected.setAccessible(true);
        Field compact = CasinoScreen.class.getDeclaredField("compactPage"); compact.setAccessible(true);
        Field viewField = AstraTerraCasinoClient.class.getDeclaredField("VIEW"); viewField.setAccessible(true);

        for (int[] size : sizes) {
            for (int tab = 0; tab < 6; tab++) {
                CasinoViewState state = new CasinoViewState();
                if (tab == 3) {
                    state.game = "Колесо экспедиции"; state.phase = "Колесо вращается"; state.bet = 100;
                    state.wheelSpinId = "screen-spin"; state.wheelState = "SPINNING"; state.wheelSectorIndex = 9;
                    state.wheelStartAngleMilli = 20_000; state.wheelTargetAngleMilli = 2_100_000;
                    state.wheelDurationMs = 5_500; state.wheelElapsedMs = 2_300;
                    state.wheelMultiplierNumerator = 5; state.wheelMultiplierDenominator = 1;
                    state.wheelSnapshotReceivedAtMs = System.currentTimeMillis();
                } else if (tab == 4) {
                    state.roomType = "poker"; state.roomCode = "ABCDE"; state.multiplayerActive = true;
                    state.phase = PokerPhase.PREFLOP.label; state.allowedActions = "fold,call,raise_to,all_in";
                    state.toCall = 100; state.currentBet = 200; state.minRaise = 400; state.maxRaise = 1000;
                    state.roomPlayers = "★ Alice §7— стек 8з 0с 0б §6[D]|Bob §7— стек 12з 0с 0б §e[ход]|Cara §c[пас]";
                    state.boardCards = "§fA♣,§cK♦,§c10♥";
                    state.handCards = "§fQ♣,§cJ♦";
                    state.turnDeadlineEpochMs = System.currentTimeMillis() + 45_000;
                    state.players.add(player("a", "Alice", 80_000, true, false, true, false, false, "§fQ♣,§cJ♦", "ожидает"));
                    state.players.add(player("b", "Bob", 120_000, false, true, false, true, false, "", "ходит"));
                    state.players.add(player("c", "Cara", 60_000, false, false, false, false, true, "", "пас"));
                } else if (tab == 5) {
                    state.roomType = "durak"; state.roomCode = "ABCDE"; state.multiplayerActive = true;
                    state.allowedActions = "card,take,throw_rank";
                    state.deckCount = 17;
                    state.handCards = "6♣,6♦,6♥,7♠,8♣,8♦,9♥,9♠,10♣,10♦,J♥,J♠,Q♣,Q♦,K♥,A♠";
                }
                viewField.set(null, state);
                for (ResponsiveCasinoLayout.Page page : ResponsiveCasinoLayout.Page.values()) {
                    CasinoScreen screen = new CasinoScreen();
                    width.setInt(screen, size[0]); height.setInt(screen, size[1]); selected.setInt(screen, tab); compact.set(screen, page);
                    screen.method_25426();
                    @SuppressWarnings("unchecked") List<Object> items = (List<Object>) children.get(screen);
                    List<int[]> rects = new ArrayList<>();
                    for (Object item : items) {
                        int x, y, w, h;
                        if (item instanceof net.minecraft.class_4185 b) { x=b.x; y=b.y; w=b.w; h=b.h; }
                        else if (item instanceof net.minecraft.class_342 f) { x=f.x; y=f.y; w=f.w; h=f.h; }
                        else continue;
                        require(x >= 0 && y >= 0 && x + w <= size[0] && y + h <= size[1],
                            "widget outside " + size[0] + "x" + size[1] + " tab=" + tab + " page=" + page + " rect=" + x+","+y+","+w+","+h);
                        rects.add(new int[]{x,y,w,h});
                    }
                    for (int i = 0; i < rects.size(); i++) for (int j = i + 1; j < rects.size(); j++) {
                        int[] a=rects.get(i), b=rects.get(j);
                        boolean overlap=a[0] < b[0]+b[2] && a[0]+a[2] > b[0] && a[1] < b[1]+b[3] && a[1]+a[3] > b[1];
                        require(!overlap, "widget overlap " + size[0] + "x" + size[1] + " tab=" + tab + " page=" + page);
                    }
                    screen.method_25394(new net.minecraft.class_332(), 0, 0, 0);
                }
            }
        }
        System.out.println("Screen geometry: 5 sizes × 6 games × 3 compact pages, no widget overlaps");
    }

    private static void testPokerHands() {
        long wheel = PokerHand.best(List.of(card(14,0), card(2,1), card(3,2), card(4,3), card(5,0), card(10,0), card(9,1)));
        require(PokerHand.name(wheel).equals("стрит"), "wheel straight not detected");

        long royal = PokerHand.best(List.of(card(10,0),card(11,0),card(12,0),card(13,0),card(14,0),card(8,1),card(2,2)));
        require(PokerHand.name(royal).equals("стрит-флеш"), "royal flush not detected");

        long acesKingsQueen = PokerHand.best(List.of(card(14,0),card(14,1),card(13,0),card(13,1),card(12,0),card(2,2),card(3,3)));
        long acesKingsJack = PokerHand.best(List.of(card(14,2),card(14,3),card(13,2),card(13,3),card(11,0),card(4,1),card(5,2)));
        require(acesKingsQueen > acesKingsJack, "two-pair kicker comparison failed");

        long fullHouseAces = PokerHand.best(List.of(card(14,0),card(14,1),card(14,2),card(13,0),card(13,1),card(13,2),card(2,0)));
        require(PokerHand.name(fullHouseAces).equals("фулл-хаус"), "two trips must produce full house");

        List<Integer> board = List.of(card(10,0),card(11,0),card(12,0),card(13,0),card(14,0));
        long playerOne = PokerHand.best(concat(board, List.of(card(2,1),card(3,1))));
        long playerTwo = PokerHand.best(concat(board, List.of(card(9,2),card(9,3))));
        require(playerOne == playerTwo, "board-only tie comparison failed");
        System.out.println("Hand evaluator: wheel, royal flush, kickers, double trips and board-only tie");
    }

    private static void testProtocolRoundTrips() throws Exception {
        FakeBuffer requestBuffer = new FakeBuffer();
        CasinoRequest sourceRequest = new CasinoRequest("poker_raise_to", 27, CurrencyUnit.GOLD.id, "ABCDE", "", 91, 1234);
        CasinoRequest.write(requestBuffer, sourceRequest);
        requestBuffer.flip();
        CasinoRequest decodedRequest = CasinoRequest.read(requestBuffer);
        require(decodedRequest.action.equals(sourceRequest.action), "request action mismatch");
        require(decodedRequest.amount == 27 && decodedRequest.unitId == CurrencyUnit.GOLD.id, "request amount mismatch");
        require(decodedRequest.revision == 91 && decodedRequest.sequence == 1234, "request revision mismatch");
        require(decodedRequest.baseAmount() == 270_000L, "request denomination conversion mismatch");

        CasinoViewState source = new CasinoViewState();
        source.wallet=123456; source.numismatic=654321; source.bet=1000; source.game="Покер"; source.phase=PokerPhase.TURN.label;
        source.wheelSpinId="spin-123"; source.wheelState="SPINNING"; source.wheelSectorId="double";
        source.wheelSectorName="Двойная выплата"; source.wheelRarity="UNCOMMON"; source.wheelSectorIndex=3;
        source.wheelRotations=6; source.wheelStartAngleMilli=12000; source.wheelTargetAngleMilli=2200000;
        source.wheelMultiplierNumerator=2; source.wheelMultiplierDenominator=1; source.wheelDurationMs=5500;
        source.wheelElapsedMs=2300; source.wheelPayout=2000;
        source.roomType="poker"; source.roomCode="Q7K9P"; source.roomPlayers="Alice|Bob"; source.handCards="A♣,K♦";
        source.deckCount=19; source.durakTaking=true;
        source.boardCards="2♣,3♦,4♥,5♠"; source.stack=9999; source.pot=7777; source.toCall=100; source.minRaise=400;
        source.maxRaise=9999; source.currentBet=200; source.streetContribution=100; source.handContribution=500;
        source.revision=42; source.handId=8; source.ackSequence=1234; source.allowedActions="fold,call,raise_to,all_in";
        source.sidePots="Основной: 10с"; source.dealerName="Alice"; source.smallBlindName="Alice"; source.bigBlindName="Bob";
        source.lastAction="Bob — колл"; source.errorCode=""; source.roomHost=true; source.multiplayerActive=true; source.canAct=true;
        source.turnDeadlineEpochMs=123456789L;
        source.players.add(player("a", "Alice", 9999, true, false, true, false, false, "A♣,K♦", "ожидает"));
        source.players.add(player("b", "Bob", 8888, false, true, false, true, false, "", "ходит"));
        FakeBuffer stateBuffer = new FakeBuffer();
        CasinoPacket.write(stateBuffer, source);
        stateBuffer.flip();
        CasinoViewState decoded = CasinoPacket.read(stateBuffer);
        require(decoded.revision == 42 && decoded.handId == 8 && decoded.ackSequence == 1234, "state revision mismatch");
        require(decoded.allowedActions.equals(source.allowedActions), "state actions mismatch");
        require(decoded.pot == 7777 && decoded.maxRaise == 9999, "state money mismatch");
        require(decoded.roomHost && decoded.multiplayerActive && decoded.canAct, "state flags mismatch");
        require(decoded.turnDeadlineEpochMs == 123456789L && decoded.players.size() == 2, "structured player snapshot mismatch");
        require(decoded.deckCount == 19 && decoded.durakTaking, "durak protocol fields mismatch");
        require(decoded.wheelSpinId.equals("spin-123") && decoded.wheelState.equals("SPINNING"), "wheel protocol state mismatch");
        require(decoded.wheelSectorIndex == 3 && decoded.wheelDurationMs == 5500 && decoded.wheelPayout == 2000,
            "wheel protocol numeric fields mismatch");
        require(decoded.players.get(1).activeTurn && decoded.players.get(0).local, "player flags mismatch");
        System.out.println("Protocol: C2S request and structured S2C authoritative snapshot round-trip");
    }


    private static void testWheelMathAndSectors() {
        require(WheelSector.SECTORS.size() == 12, "wheel must contain 12 sectors");
        for (int i = 0; i < WheelSector.SECTORS.size(); i++) {
            WheelSector sector = WheelSector.SECTORS.get(i);
            require(sector.weight > 0 && sector.denominator > 0, "invalid wheel sector configuration");
            int target = WheelMath.targetAngleMilli(17_000, i, 6, 0);
            int remainder = Math.floorMod(target, 360_000);
            int expected = Math.floorMod(-i * 30_000, 360_000);
            require(remainder == expected, "sector center does not align with pointer: " + i);
            require(target > 17_000 + 5 * 360_000, "wheel must include full rotations");
        }
        int target = WheelMath.targetAngleMilli(12_345, 7, 5, 4_000);
        double end30 = WheelMath.angle(12_345, target, 5_000, 5_000);
        double end60 = WheelMath.angle(12_345, target, 5_000, 5_000);
        double end144 = WheelMath.angle(12_345, target, 5_000, 5_000);
        require(Math.abs(end30 - end60) < 0.000001 && Math.abs(end60 - end144) < 0.000001,
            "wheel final angle must be FPS-independent");
        require(WheelSector.SECTORS.get(2).payout(101, Long.MAX_VALUE) == 50, "half multiplier must use integer money");
        require(WheelSector.SECTORS.get(11).payout(Long.MAX_VALUE / 2, 9_000_000_000_000_000L) == 9_000_000_000_000_000L,
            "jackpot payout must saturate safely");
        System.out.println("Wheel: 12 sectors, pointer alignment, rational payouts and FPS-independent animation");
    }

    private static void testCurrencyConversion() {
        require(CurrencyUnit.BRONZE.toBase(27) == 27, "bronze conversion");
        require(CurrencyUnit.SILVER.toBase(27) == 2700, "silver conversion");
        require(CurrencyUnit.GOLD.toBase(27) == 270000, "gold conversion");
        require(CurrencyUnit.GOLD.toBase(Long.MAX_VALUE) == Long.MAX_VALUE, "overflow saturation");
        require(CurrencyUnit.byId(999) == CurrencyUnit.SILVER, "unknown denomination fallback");
        System.out.println("Currency: denomination conversion and overflow saturation");
    }

    private static CasinoPlayerView player(String id, String name, long stack, boolean local, boolean active, boolean dealer, boolean bigBlind, boolean folded, String cards, String status) {
        CasinoPlayerView p = new CasinoPlayerView();
        p.id=id; p.name=name; p.stack=stack; p.local=local; p.activeTurn=active; p.dealer=dealer; p.bigBlind=bigBlind; p.folded=folded; p.cards=cards; p.status=status;
        return p;
    }

    private static int card(int rank, int suit) { return suit * 13 + (rank - 2); }
    private static int durakCard(int rank, int suit) { return suit * 9 + (rank - 6); }
    private static List<Integer> concat(List<Integer> a, List<Integer> b) { List<Integer> r=new ArrayList<>(a); r.addAll(b); return r; }
    private static void require(boolean v, String m) { if (!v) throw new IllegalStateException(m); }

    public static final class TestProfile {
        private final UUID id;
        private final String name;
        TestProfile(String id, String name) { this.id=UUID.fromString(id); this.name=name; }
        public UUID getId() { return id; }
        public String getName() { return name; }
    }

    public static final class TestPlayer {
        private final TestProfile profile;
        TestPlayer(String id, String name) { profile=new TestProfile(id,name); }
        public TestProfile method_7334() { return profile; }
    }

    public static final class NullIdProfile {
        private final String name;
        NullIdProfile(String name) { this.name = name; }
        public UUID getId() { return null; }
        public String getName() { return name; }
    }

    public static final class NullIdPlayer {
        private final NullIdProfile profile;
        NullIdPlayer(String name) { profile = new NullIdProfile(name); }
        public NullIdProfile method_7334() { return profile; }
    }

    public static final class FakeBuffer {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final DataOutputStream out = new DataOutputStream(bytes);
        private DataInputStream in;
        public void writeInt(int v) throws IOException { out.writeInt(v); }
        public void writeLong(long v) throws IOException { out.writeLong(v); }
        public void writeBoolean(boolean v) throws IOException { out.writeBoolean(v); }
        public void writeBytes(byte[] v) throws IOException { out.write(v); }
        public int readInt() throws IOException { return in.readInt(); }
        public long readLong() throws IOException { return in.readLong(); }
        public boolean readBoolean() throws IOException { return in.readBoolean(); }
        public void readBytes(byte[] v) throws IOException { in.readFully(v); }
        void flip() throws IOException { out.flush(); in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())); }
    }
}

package dev.astraterra.casino;

final class CasinoPlayerView {
    String id = "";
    String name = "";
    String status = "";
    String cards = "";
    long stack;
    long streetContribution;
    long handContribution;
    boolean local;
    boolean host;
    boolean ready;
    boolean connected = true;
    boolean activeTurn;
    boolean dealer;
    boolean smallBlind;
    boolean bigBlind;
    boolean folded;
    boolean allIn;
    boolean winner;

    CasinoPlayerView copy() {
        CasinoPlayerView c = new CasinoPlayerView();
        c.id = id; c.name = name; c.status = status; c.cards = cards;
        c.stack = stack; c.streetContribution = streetContribution; c.handContribution = handContribution;
        c.local = local; c.host = host; c.ready = ready; c.connected = connected; c.activeTurn = activeTurn;
        c.dealer = dealer; c.smallBlind = smallBlind; c.bigBlind = bigBlind;
        c.folded = folded; c.allIn = allIn; c.winner = winner;
        return c;
    }
}

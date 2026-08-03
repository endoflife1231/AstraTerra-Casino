package dev.astraterra.casino;

enum PokerAction {
    CHECK("check"),
    CALL("call"),
    BET("bet"),
    RAISE_TO("raise_to"),
    FOLD("fold"),
    ALL_IN("all_in");

    final String id;

    PokerAction(String id) {
        this.id = id;
    }
}

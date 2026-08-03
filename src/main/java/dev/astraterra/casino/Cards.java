package dev.astraterra.casino;

import java.util.*;

final class Cards {
    private static final String[] SUITS = {"♣", "♦", "♥", "♠"};
    private Cards() {}

    static List<Integer> pokerDeck() {
        List<Integer> deck = new ArrayList<>(52);
        for (int i = 0; i < 52; i++) deck.add(i);
        Collections.shuffle(deck);
        return deck;
    }

    static int pokerRank(int card) { return card % 13 + 2; }
    static int pokerSuit(int card) { return card / 13; }

    static String pokerCard(int card) {
        int rank = pokerRank(card);
        String r = switch (rank) { case 14 -> "A"; case 13 -> "K"; case 12 -> "Q"; case 11 -> "J"; case 10 -> "10"; default -> Integer.toString(rank); };
        String color = pokerSuit(card) == 1 || pokerSuit(card) == 2 ? "§c" : "§f";
        return color + r + SUITS[pokerSuit(card)];
    }

    static List<Integer> durakDeck() {
        List<Integer> deck = new ArrayList<>(36);
        for (int suit = 0; suit < 4; suit++) for (int rank = 6; rank <= 14; rank++) deck.add(suit * 9 + (rank - 6));
        Collections.shuffle(deck);
        return deck;
    }

    static int durakRank(int card) { return card % 9 + 6; }
    static int durakSuit(int card) { return card / 9; }

    static String durakCard(int card) {
        int rank = durakRank(card);
        String r = switch (rank) { case 14 -> "A"; case 13 -> "K"; case 12 -> "Q"; case 11 -> "J"; case 10 -> "10"; default -> Integer.toString(rank); };
        String color = durakSuit(card) == 1 || durakSuit(card) == 2 ? "§c" : "§f";
        return color + r + SUITS[durakSuit(card)];
    }

    static String csvPoker(Collection<Integer> cards) { return csv(cards, true); }
    static String csvDurak(Collection<Integer> cards) { return csv(cards, false); }

    private static String csv(Collection<Integer> cards, boolean poker) {
        StringBuilder out = new StringBuilder();
        for (int card : cards) {
            if (out.length() > 0) out.append(',');
            out.append(poker ? pokerCard(card) : durakCard(card));
        }
        return out.toString();
    }

    static boolean durakBeats(int defense, int attack, int trumpSuit) {
        int ds = durakSuit(defense), as = durakSuit(attack);
        return (ds == as && durakRank(defense) > durakRank(attack)) || (ds == trumpSuit && as != trumpSuit);
    }


    static String durakRankToken(String display) {
        String normalized = strip(display).trim();
        if (normalized.length() < 2) return normalized;
        return normalized.substring(0, normalized.length() - 1);
    }

    static int parseDurakRank(String token) {
        String value = strip(token).trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "A" -> 14;
            case "K" -> 13;
            case "Q" -> 12;
            case "J" -> 11;
            case "10" -> 10;
            case "9" -> 9;
            case "8" -> 8;
            case "7" -> 7;
            case "6" -> 6;
            default -> -1;
        };
    }

    static int parseDurakCard(String code, Collection<Integer> hand) {
        String normalized = strip(code);
        for (int card : hand) if (strip(durakCard(card)).equals(normalized)) return card;
        return -1;
    }

    static String strip(String value) { return value == null ? "" : value.replaceAll("§.", ""); }
}

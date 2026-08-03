package dev.astraterra.casino;

enum PokerPhase {
    WAITING_FOR_PLAYERS("Лобби"),
    READY_CHECK("Проверка готовности"),
    STARTING_HAND("Подготовка раздачи"),
    PREFLOP("Префлоп"),
    FLOP("Флоп"),
    TURN("Тёрн"),
    RIVER("Ривер"),
    SHOWDOWN("Вскрытие"),
    PAYOUT("Выплата"),
    BETWEEN_HANDS("Между раздачами"),
    TOURNAMENT_FINISHED("Турнир завершён"),
    CANCELLED("Отменено");

    final String label;

    PokerPhase(String label) {
        this.label = label;
    }

    boolean betting() {
        return this == PREFLOP || this == FLOP || this == TURN || this == RIVER;
    }
}

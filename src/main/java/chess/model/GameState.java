package main.java.chess.model;

public enum GameState {
    ONGOING,
    CHECK,
    CHECKMATE,
    STALEMATE,
    DRAW_75_MOVE,
    DRAW_FIVEFOLD_REPETITION
}
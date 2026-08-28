package main.java.chess.model;

public record Square(int file, int rank) {

    public Square {
        if (file < 0 || file > 7 || rank < 0 || rank > 7) {
            throw new IllegalArgumentException("Square must be on the chessboard.");
        }
    }

    public static Square fromAlgebraic(String notation) {
        if (notation == null || notation.length() != 2) {
            throw new IllegalArgumentException("Invalid square: " + notation);
        }

        int file = notation.charAt(0) - 'a';
        int rank = notation.charAt(1) - '1';

        return new Square(file, rank);
    }

    public String toAlgebraic() {
        char fileChar = (char) ('a' + file);
        char rankChar = (char) ('1' + rank);

        return "" + fileChar + rankChar;
    }
}
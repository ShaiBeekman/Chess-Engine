package main.java.chess.model;

public class Board {

    private final Piece[][] squares;

    public Board() {
        squares = new Piece[8][8];
    }

    public Board(Board other) {
        squares = new Piece[8][8];

        for (int rank = 0; rank < 8; rank++) {
            for (int file = 0; file < 8; file++) {
                squares[rank][file] = other.squares[rank][file];
            }
        }
    }

    public Piece getPiece(Square square) {
        return squares[square.rank()][square.file()];
    }

    public void setPiece(Square square, Piece piece) {
        squares[square.rank()][square.file()] = piece;
    }

    public void removePiece(Square square) {
        squares[square.rank()][square.file()] = null;
    }

    public boolean isEmpty(Square square) {
        return getPiece(square) == null;
    }
}
package main.java.chess.model;

public record Move(
        Square from,
        Square to,
        PieceType promotion
) {

    public Move(Square from, Square to) {
        this(from, to, null);
    }

    public boolean isPromotion() {
        return promotion != null;
    }
}
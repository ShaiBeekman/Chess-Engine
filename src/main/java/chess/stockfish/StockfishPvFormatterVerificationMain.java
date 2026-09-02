package main.java.chess.stockfish;

import main.java.chess.model.Board;
import main.java.chess.model.Color;
import main.java.chess.model.Piece;
import main.java.chess.model.PieceType;
import main.java.chess.model.Position;
import main.java.chess.model.Square;

import java.util.List;

public final class StockfishPvFormatterVerificationMain {

    private StockfishPvFormatterVerificationMain() {
    }

    public static void main(String[] args) {
        Board board = new Board();
        PieceType[] back = {
                PieceType.ROOK, PieceType.KNIGHT, PieceType.BISHOP, PieceType.QUEEN,
                PieceType.KING, PieceType.BISHOP, PieceType.KNIGHT, PieceType.ROOK
        };
        for (int file = 0; file < 8; file++) {
            board.setPiece(new Square(file, 0), new Piece(back[file], Color.WHITE));
            board.setPiece(new Square(file, 1), new Piece(PieceType.PAWN, Color.WHITE));
            board.setPiece(new Square(file, 6), new Piece(PieceType.PAWN, Color.BLACK));
            board.setPiece(new Square(file, 7), new Piece(back[file], Color.BLACK));
        }

        Position start = new Position(board, Color.WHITE);
        List<String> san = StockfishPvFormatter.format(
                start,
                List.of("e2e4", "c7c5", "b1c3", "a7a6", "g1f3", "e7e6")
        );

        System.out.println("M67C-STOCKFISH-PRESENTATION-V1");
        System.out.println("========================================");
        System.out.println("SAN PV: " + String.join(" ", san));

        List<String> expected = List.of("e4", "c5", "Nc3", "a6", "Nf3", "e6");
        if (!san.equals(expected)) {
            throw new IllegalStateException("SAN conversion mismatch: " + san);
        }

        System.out.println("SAN conversion: PASS");
        System.out.println("Auto-discovery candidate: src/main/java/chess/stockfish/stockfish.exe");
        System.out.println();
        System.out.println("M67C STOCKFISH PRESENTATION PASSED");
    }
}

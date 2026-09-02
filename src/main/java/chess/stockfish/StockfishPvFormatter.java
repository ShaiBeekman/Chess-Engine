package main.java.chess.stockfish;

import main.java.chess.model.Move;
import main.java.chess.model.PieceType;
import main.java.chess.model.Position;
import main.java.chess.model.Square;
import main.java.chess.rules.MoveGenerator;
import main.java.chess.util.SanMoveFormatter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Converts a Stockfish UCI principal variation to this project's SAN notation. */
public final class StockfishPvFormatter {

    private StockfishPvFormatter() {
    }

    public static List<String> format(
            Position root,
            List<String> uciMoves
    ) {
        if (root == null || uciMoves == null || uciMoves.isEmpty()) {
            return Collections.emptyList();
        }

        MoveGenerator moveGenerator = new MoveGenerator();
        SanMoveFormatter sanFormatter = new SanMoveFormatter();
        List<String> result = new ArrayList<>();
        Position position = root;

        for (String uci : uciMoves) {
            Move move = findLegalMove(position, uci, moveGenerator);
            if (move == null) {
                // Never display a partially mistranslated line. Preserve the
                // remaining UCI token visibly if Stockfish ever gives us a
                // move our local rules layer cannot match.
                result.add(uci);
                break;
            }

            result.add(sanFormatter.format(position, move));
            position = position.makeMove(move);
        }

        return Collections.unmodifiableList(result);
    }

    private static Move findLegalMove(
            Position position,
            String uci,
            MoveGenerator moveGenerator
    ) {
        if (uci == null || uci.length() < 4) {
            return null;
        }

        Square from = square(uci.substring(0, 2));
        Square to = square(uci.substring(2, 4));
        PieceType promotion = uci.length() >= 5
                ? promotionType(uci.charAt(4))
                : null;

        for (Move move : moveGenerator.generateLegalMoves(position)) {
            if (!move.from().equals(from) || !move.to().equals(to)) {
                continue;
            }
            if (move.promotion() == promotion) {
                return move;
            }
        }

        return null;
    }

    private static Square square(String algebraic) {
        int file = algebraic.charAt(0) - 'a';
        int rank = algebraic.charAt(1) - '1';
        if (file < 0 || file > 7 || rank < 0 || rank > 7) {
            throw new IllegalArgumentException("Invalid UCI square: " + algebraic);
        }
        return new Square(file, rank);
    }

    private static PieceType promotionType(char value) {
        return switch (Character.toLowerCase(value)) {
            case 'q' -> PieceType.QUEEN;
            case 'r' -> PieceType.ROOK;
            case 'b' -> PieceType.BISHOP;
            case 'n' -> PieceType.KNIGHT;
            default -> null;
        };
    }
}

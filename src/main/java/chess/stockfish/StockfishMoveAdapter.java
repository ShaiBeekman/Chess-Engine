package main.java.chess.stockfish;

import main.java.chess.model.Move;
import main.java.chess.model.PieceType;
import main.java.chess.model.Position;
import main.java.chess.model.Square;
import main.java.chess.rules.MoveGenerator;
import main.java.chess.util.SanMoveFormatter;

import java.util.List;

/**
 * Bridges Stockfish UCI moves into the project's native Move model.
 */
public final class StockfishMoveAdapter {

    private static final MoveGenerator MOVE_GENERATOR =
            new MoveGenerator();

    private static final SanMoveFormatter SAN_FORMATTER =
            new SanMoveFormatter();

    private StockfishMoveAdapter() {
    }

    /**
     * Finds the legal project Move corresponding to a Stockfish UCI move.
     *
     * Examples:
     * e2e4
     * g1f3
     * e7e8q
     */
    public static Move findLegalMove(
            Position position,
            String uci
    ) {

        if (position == null
                || uci == null
                || uci.length() < 4) {

            return null;
        }

        String normalized =
                uci.trim().toLowerCase();

        if (normalized.length() < 4) {
            return null;
        }

        Square from =
                parseSquare(
                        normalized.substring(
                                0,
                                2
                        )
                );

        Square to =
                parseSquare(
                        normalized.substring(
                                2,
                                4
                        )
                );

        if (from == null
                || to == null) {

            return null;
        }

        PieceType promotion =
                normalized.length() >= 5
                        ? promotionType(
                        normalized.charAt(4)
                )
                        : null;

        List<Move> legalMoves =
                MOVE_GENERATOR.generateLegalMoves(
                        position
                );

        for (Move move : legalMoves) {

            if (!move.from().equals(from)) {
                continue;
            }

            if (!move.to().equals(to)) {
                continue;
            }

            if (move.promotion() != promotion) {
                continue;
            }

            return move;
        }

        return null;
    }

    /**
     * Converts a Stockfish UCI move into SAN.
     *
     * If the UCI move cannot be matched to one of our legal moves,
     * the raw UCI move is returned so the UI still has something
     * useful to display.
     */
    public static String san(
            Position position,
            String uci
    ) {

        Move move =
                findLegalMove(
                        position,
                        uci
                );

        if (move == null) {

            return uci == null
                    ? ""
                    : uci;
        }

        return SAN_FORMATTER.format(
                position,
                move
        );
    }

    /**
     * Applies a Stockfish UCI move using the project's own Position
     * implementation.
     */
    public static Position resultingPosition(
            Position position,
            String uci
    ) {

        Move move =
                findLegalMove(
                        position,
                        uci
                );

        if (move == null) {
            return null;
        }

        return position.makeMove(
                move
        );
    }

    private static Square parseSquare(
            String algebraic
    ) {

        if (algebraic == null
                || algebraic.length() != 2) {

            return null;
        }

        int file =
                Character.toLowerCase(
                        algebraic.charAt(0)
                ) - 'a';

        int rank =
                algebraic.charAt(1) - '1';

        if (file < 0
                || file > 7
                || rank < 0
                || rank > 7) {

            return null;
        }

        return new Square(
                file,
                rank
        );
    }

    private static PieceType promotionType(
            char value
    ) {

        return switch (
                Character.toLowerCase(value)
                ) {

            case 'q' ->
                    PieceType.QUEEN;

            case 'r' ->
                    PieceType.ROOK;

            case 'b' ->
                    PieceType.BISHOP;

            case 'n' ->
                    PieceType.KNIGHT;

            default ->
                    null;
        };
    }
}
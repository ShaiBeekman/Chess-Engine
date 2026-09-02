package main.java.chess.stockfish;

import main.java.chess.model.Color;

import java.util.Locale;

/**
 * Converts the raw UCI root score returned by Stockfish into the same
 * fixed perspective used by ChessEngine's evaluation display:
 *
 *     positive = White is better
 *     negative = Black is better
 *
 * Stockfish's root score is relative to the side to move in the analyzed
 * position. Therefore a Black-to-move root must have its sign reversed
 * before it can be compared directly with a White-positive evaluation.
 */
public final class StockfishScorePerspective {

    private StockfishScorePerspective() {
    }


    public static Integer toWhiteCentipawns(
            Color sideToMove,
            Integer sideToMoveCentipawns
    ) {

        if (sideToMoveCentipawns == null) {
            return null;
        }

        requireSideToMove(sideToMove);

        return sideToMove == Color.WHITE
                ? sideToMoveCentipawns
                : -sideToMoveCentipawns;
    }


    public static Integer toWhiteMateScore(
            Color sideToMove,
            Integer sideToMoveMateIn
    ) {

        if (sideToMoveMateIn == null) {
            return null;
        }

        requireSideToMove(sideToMove);

        return sideToMove == Color.WHITE
                ? sideToMoveMateIn
                : -sideToMoveMateIn;
    }


    public static String formatWhitePerspective(
            Color sideToMove,
            StockfishClient.Analysis analysis
    ) {

        if (analysis == null) {
            return "—";
        }

        Integer whiteMate =
                toWhiteMateScore(
                        sideToMove,
                        analysis.mateIn()
                );

        if (whiteMate != null) {
            if (whiteMate > 0) {
                return "White mate in " + Math.abs(whiteMate);
            }

            if (whiteMate < 0) {
                return "Black mate in " + Math.abs(whiteMate);
            }

            return "Mate";
        }

        Integer whiteCentipawns =
                toWhiteCentipawns(
                        sideToMove,
                        analysis.centipawns()
                );

        if (whiteCentipawns != null) {
            double pawns =
                    Math.abs(whiteCentipawns) / 100.0;

            if (whiteCentipawns > 0) {
                return String.format(
                        Locale.ROOT,
                        "White advantage %.2f",
                        pawns
                );
            }

            if (whiteCentipawns < 0) {
                return String.format(
                        Locale.ROOT,
                        "Black advantage %.2f",
                        pawns
                );
            }

            return "Equal";
        }

        return "—";
    }


    private static void requireSideToMove(
            Color sideToMove
    ) {

        if (sideToMove == null) {
            throw new IllegalArgumentException(
                    "Side to move cannot be null."
            );
        }
    }
}

package main.java.chess.endgame;


/**
 * Allocation-free KQRK legality and attack helpers.
 *
 * This layer operates only on primitive square indexes (0..63) and packed
 * KQRK states. It is intentionally independent from Board, Position, Piece,
 * Square, MoveGenerator, and collections.
 *
 * Material convention:
 *
 *     strong side owns queen + rook
 *     weak side owns only its king
 *
 * The strong color is supplied to methods that need to know which physical
 * king belongs with the queen and rook.
 */
public final class KqrkPrimitiveRules {

    private KqrkPrimitiveRules() {
    }


    // =========================================================
    // STRUCTURAL LEGALITY
    // =========================================================

    /**
     * Exact structural legality for an encoded KQRK state.
     *
     * A state is rejected when:
     *
     *     - two pieces occupy the same square
     *     - kings are adjacent
     *     - the side that moved previously left its own king in check
     *
     * The side to move is allowed to be in check.
     */
    public static boolean isStructurallyLegal(
            int state,
            boolean strongIsWhite
    ) {

        if (!KqrkPrimitiveState.hasDistinctSquares(
                state
        )) {

            return false;
        }


        if (KqrkPrimitiveState.kingsAdjacent(
                state
        )) {

            return false;
        }


        boolean blackToMove =
                KqrkPrimitiveState.blackToMove(
                        state
                );


        /*
         * Previous mover is the opposite of side to move.
         */
        boolean previousMoverIsWhite =
                blackToMove;


        return !isKingInCheck(
                state,
                previousMoverIsWhite,
                strongIsWhite
        );
    }


    // =========================================================
    // CHECK / ATTACK DETECTION
    // =========================================================

    public static boolean sideToMoveIsInCheck(
            int state,
            boolean strongIsWhite
    ) {

        boolean sideIsWhite =
                !KqrkPrimitiveState.blackToMove(
                        state
                );


        return isKingInCheck(
                state,
                sideIsWhite,
                strongIsWhite
        );
    }


    public static boolean isKingInCheck(
            int state,
            boolean kingIsWhite,
            boolean strongIsWhite
    ) {

        int whiteKing =
                KqrkPrimitiveState.whiteKing(
                        state
                );

        int blackKing =
                KqrkPrimitiveState.blackKing(
                        state
                );

        int queen =
                KqrkPrimitiveState.queen(
                        state
                );

        int rook =
                KqrkPrimitiveState.rook(
                        state
                );


        int king =
                kingIsWhite
                        ? whiteKing
                        : blackKing;


        int enemyKing =
                kingIsWhite
                        ? blackKing
                        : whiteKing;


        if (kingsAdjacent(
                king,
                enemyKing
        )) {

            return true;
        }


        /*
         * Queen and rook attack only for the strong side.
         */
        if (kingIsWhite
                == strongIsWhite) {

            return false;
        }


        if (rookAttacks(
                rook,
                king,
                whiteKing,
                blackKing,
                queen
        )) {

            return true;
        }


        return queenAttacks(
                queen,
                king,
                whiteKing,
                blackKing,
                rook
        );
    }


    public static boolean queenAttacks(
            int from,
            int target,
            int blockerA,
            int blockerB,
            int blockerC
    ) {

        int fromFile =
                from & 7;

        int fromRank =
                from >>> 3;

        int targetFile =
                target & 7;

        int targetRank =
                target >>> 3;


        int df =
                targetFile
                        - fromFile;

        int dr =
                targetRank
                        - fromRank;


        boolean straight =
                df == 0
                        || dr == 0;

        boolean diagonal =
                Math.abs(
                        df
                )
                        == Math.abs(
                        dr
                );


        if (!straight
                && !diagonal) {

            return false;
        }


        return rayClear(
                from,
                target,
                Integer.compare(
                        df,
                        0
                ),
                Integer.compare(
                        dr,
                        0
                ),
                blockerA,
                blockerB,
                blockerC
        );
    }


    public static boolean rookAttacks(
            int from,
            int target,
            int blockerA,
            int blockerB,
            int blockerC
    ) {

        int fromFile =
                from & 7;

        int fromRank =
                from >>> 3;

        int targetFile =
                target & 7;

        int targetRank =
                target >>> 3;


        int df =
                targetFile
                        - fromFile;

        int dr =
                targetRank
                        - fromRank;


        if (df != 0
                && dr != 0) {

            return false;
        }


        return rayClear(
                from,
                target,
                Integer.compare(
                        df,
                        0
                ),
                Integer.compare(
                        dr,
                        0
                ),
                blockerA,
                blockerB,
                blockerC
        );
    }


    private static boolean rayClear(
            int from,
            int target,
            int stepFile,
            int stepRank,
            int blockerA,
            int blockerB,
            int blockerC
    ) {

        int file =
                (from & 7)
                        + stepFile;

        int rank =
                (from >>> 3)
                        + stepRank;


        int targetFile =
                target & 7;

        int targetRank =
                target >>> 3;


        while (file != targetFile
                || rank != targetRank) {

            int square =
                    rank * 8
                            + file;


            if (square == blockerA
                    || square == blockerB
                    || square == blockerC) {

                return false;
            }


            file +=
                    stepFile;

            rank +=
                    stepRank;
        }


        return true;
    }


    private static boolean kingsAdjacent(
            int first,
            int second
    ) {

        int firstFile =
                first & 7;

        int firstRank =
                first >>> 3;

        int secondFile =
                second & 7;

        int secondRank =
                second >>> 3;


        return Math.max(
                Math.abs(
                        firstFile
                                - secondFile
                ),
                Math.abs(
                        firstRank
                                - secondRank
                )
        ) <= 1;
    }
}

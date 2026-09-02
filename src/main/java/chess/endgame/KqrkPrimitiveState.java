package main.java.chess.endgame;

/**
 * Primitive KQRK state helpers used by future tablebase generation.
 *
 * State layout is identical to FourPieceTablebase:
 *
 *     white king, black king, queen, rook, side to move
 *
 * All square values are 0..63.  No chess-model objects are allocated.
 */
public final class KqrkPrimitiveState {

    public static final int STATE_COUNT =
            64 * 64 * 64 * 64 * 2;


    private KqrkPrimitiveState() {
    }


    public static int encode(
            int whiteKing,
            int blackKing,
            int queen,
            int rook,
            boolean blackToMove
    ) {

        int state =
                whiteKing;


        state =
                state * 64
                        + blackKing;

        state =
                state * 64
                        + queen;

        state =
                state * 64
                        + rook;

        state =
                state * 2
                        + (blackToMove
                        ? 1
                        : 0);


        return state;
    }


    public static int whiteKing(
            int state
    ) {

        return (state >>> 19)
                & 63;
    }


    public static int blackKing(
            int state
    ) {

        return (state >>> 13)
                & 63;
    }


    public static int queen(
            int state
    ) {

        return (state >>> 7)
                & 63;
    }


    public static int rook(
            int state
    ) {

        return (state >>> 1)
                & 63;
    }


    public static boolean blackToMove(
            int state
    ) {

        return (state & 1)
                != 0;
    }


    public static boolean hasDistinctSquares(
            int state
    ) {

        int wk =
                whiteKing(
                        state
                );

        int bk =
                blackKing(
                        state
                );

        int q =
                queen(
                        state
                );

        int r =
                rook(
                        state
                );


        return wk != bk
                && wk != q
                && wk != r
                && bk != q
                && bk != r
                && q != r;
    }


    public static boolean kingsAdjacent(
            int state
    ) {

        return kingsAdjacent(
                whiteKing(
                        state
                ),
                blackKing(
                        state
                )
        );
    }


    public static boolean kingsAdjacent(
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


    public static boolean basicStructuralCandidate(
            int state
    ) {

        return hasDistinctSquares(
                state
        )
                &&
                !kingsAdjacent(
                        state
                );
    }
}

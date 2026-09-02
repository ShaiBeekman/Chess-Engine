package main.java.chess.endgame;

/**
 * Milestone 62 compact exact index for SPLIT KP-KP.
 *
 * Ordinary states retain the existing 33,554,432 FourPieceGenericPrimitiveState
 * IDs. Only geometries that can actually carry a relevant en-passant right get
 * an additional overlay ID.
 *
 * EP overlay cardinality:
 *     64 * 64 king placements
 *   * 2 side-to-move orientations
 *   * 14 directed adjacent pawn-file pairs
 *   = 114,688 raw overlay IDs.
 *
 * Total exact array domain: 33,669,120 states.
 */
public final class FourPieceTierTwoKpkpStateIndex {

    public static final int BASE_STATE_COUNT =
            FourPieceGenericPrimitiveState.STATE_COUNT;

    public static final int EP_PAIR_COUNT =
            14;

    public static final int EP_STATE_COUNT =
            64 * 64 * 2 * EP_PAIR_COUNT;

    public static final int STATE_COUNT =
            BASE_STATE_COUNT + EP_STATE_COUNT;

    private FourPieceTierTwoKpkpStateIndex() {
    }

    public static int of(
            FourPieceTierTwoKpkpPrimitiveState.State state
    ) {

        if (state == null) {
            throw new IllegalArgumentException(
                    "KP-KP state cannot be null."
            );
        }

        return ofBase(
                state.baseState(),
                state.enPassantAvailable()
        );
    }

    public static int ofBase(
            int baseState,
            boolean enPassantAvailable
    ) {

        requireBase(baseState);

        if (!enPassantAvailable) {
            return baseState;
        }

        if (!FourPieceTierTwoKpkpPrimitiveState
                .canCarryEnPassant(baseState)) {
            throw new IllegalArgumentException(
                    "Base state cannot carry a KP-KP EP overlay."
            );
        }

        int wk =
                FourPieceGenericPrimitiveState.whiteKing(baseState);

        int bk =
                FourPieceGenericPrimitiveState.blackKing(baseState);

        int whitePawn =
                FourPieceGenericPrimitiveState.firstExtra(baseState);

        int blackPawn =
                FourPieceGenericPrimitiveState.secondExtra(baseState);

        boolean blackToMove =
                FourPieceGenericPrimitiveState.blackToMove(baseState);

        int pair =
                pairCode(
                        whitePawn & 7,
                        blackPawn & 7
                );

        int overlay =
                (((wk * 64 + bk) * 2
                        + (blackToMove ? 1 : 0))
                        * EP_PAIR_COUNT)
                        + pair;

        return BASE_STATE_COUNT + overlay;
    }

    public static int baseState(
            int stateId
    ) {

        requireStateId(stateId);

        if (stateId < BASE_STATE_COUNT) {
            return stateId;
        }

        int overlay =
                stateId - BASE_STATE_COUNT;

        int pair =
                overlay % EP_PAIR_COUNT;

        overlay /=
                EP_PAIR_COUNT;

        boolean blackToMove =
                (overlay & 1) != 0;

        overlay >>>=
                1;

        int bk =
                overlay & 63;

        int wk =
                overlay >>> 6;

        int whiteFile;
        int blackFile;

        if ((pair & 1) == 0) {
            whiteFile =
                    pair >>> 1;
            blackFile =
                    whiteFile + 1;
        } else {
            whiteFile =
                    (pair + 1) >>> 1;
            blackFile =
                    whiteFile - 1;
        }

        int pawnRank =
                blackToMove
                        ? 3
                        : 4;

        int whitePawn =
                pawnRank * 8 + whiteFile;

        int blackPawn =
                pawnRank * 8 + blackFile;

        return FourPieceGenericPrimitiveState.encode(
                wk,
                bk,
                whitePawn,
                blackPawn,
                blackToMove
        );
    }

    public static boolean enPassantAvailable(
            int stateId
    ) {

        requireStateId(stateId);

        return stateId >= BASE_STATE_COUNT;
    }

    public static FourPieceTierTwoKpkpPrimitiveState.State state(
            int stateId
    ) {

        return FourPieceTierTwoKpkpPrimitiveState.of(
                baseState(stateId),
                enPassantAvailable(stateId)
        );
    }

    public static int whiteKing(
            int stateId
    ) {

        return FourPieceGenericPrimitiveState.whiteKing(
                baseState(stateId)
        );
    }

    public static int blackKing(
            int stateId
    ) {

        return FourPieceGenericPrimitiveState.blackKing(
                baseState(stateId)
        );
    }

    public static int whitePawn(
            int stateId
    ) {

        return FourPieceGenericPrimitiveState.firstExtra(
                baseState(stateId)
        );
    }

    public static int blackPawn(
            int stateId
    ) {

        return FourPieceGenericPrimitiveState.secondExtra(
                baseState(stateId)
        );
    }

    public static boolean blackToMove(
            int stateId
    ) {

        return FourPieceGenericPrimitiveState.blackToMove(
                baseState(stateId)
        );
    }

    public static int enPassantTargetSquare(
            int stateId
    ) {

        if (!enPassantAvailable(stateId)) {
            return -1;
        }

        if (blackToMove(stateId)) {
            return whitePawn(stateId) - 8;
        }

        return blackPawn(stateId) + 8;
    }

    public static int colorReverse(
            int stateId
    ) {

        int reversedBase =
                FourPieceGenericPrimitiveState.encode(
                        flip(blackKing(stateId)),
                        flip(whiteKing(stateId)),
                        flip(blackPawn(stateId)),
                        flip(whitePawn(stateId)),
                        !blackToMove(stateId)
                );

        if (!enPassantAvailable(stateId)) {
            return reversedBase;
        }

        /*
         * Full-domain validation color-reverses raw EP overlay IDs, including
         * INVALID impossible-history slots. Preserve the raw overlay mapping
         * without routing through ofBase(...), which correctly rejects those
         * histories for actual states.
         */
        return rawEpId(
                reversedBase
        );
    }

    private static int rawEpId(
            int baseState
    ) {

        int wk =
                FourPieceGenericPrimitiveState.whiteKing(baseState);

        int bk =
                FourPieceGenericPrimitiveState.blackKing(baseState);

        int whitePawn =
                FourPieceGenericPrimitiveState.firstExtra(baseState);

        int blackPawn =
                FourPieceGenericPrimitiveState.secondExtra(baseState);

        boolean blackToMove =
                FourPieceGenericPrimitiveState.blackToMove(baseState);

        int pair =
                pairCode(
                        whitePawn & 7,
                        blackPawn & 7
                );

        int overlay =
                (((wk * 64 + bk) * 2
                        + (blackToMove ? 1 : 0))
                        * EP_PAIR_COUNT)
                        + pair;

        return BASE_STATE_COUNT + overlay;
    }


    private static int pairCode(
            int whiteFile,
            int blackFile
    ) {

        if (blackFile == whiteFile + 1) {
            return whiteFile * 2;
        }

        if (blackFile == whiteFile - 1) {
            return whiteFile * 2 - 1;
        }

        throw new IllegalArgumentException(
                "KP-KP EP pawns are not on adjacent files."
        );
    }

    private static int flip(
            int square
    ) {

        return (7 - (square >>> 3)) * 8
                + (square & 7);
    }

    private static void requireBase(
            int baseState
    ) {

        if (baseState < 0
                || baseState >= BASE_STATE_COUNT) {
            throw new IllegalArgumentException(
                    "KP-KP base state out of range: "
                            + baseState
            );
        }
    }

    private static void requireStateId(
            int stateId
    ) {

        if (stateId < 0
                || stateId >= STATE_COUNT) {
            throw new IllegalArgumentException(
                    "KP-KP exact state ID out of range: "
                            + stateId
            );
        }
    }
}

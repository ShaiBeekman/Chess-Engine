package main.java.chess.endgame;

/**
 * Milestone 60.
 *
 * Exact primitive state for the final four-piece family: KP-KP.
 *
 * The ordinary board component deliberately reuses
 * FourPieceGenericPrimitiveState:
 *
 *     white king, black king, WHITE pawn, BLACK pawn, side to move
 *
 * Unlike every earlier four-piece family, KP-KP also has a history-sensitive
 * state: a currently available en-passant capture.
 *
 * We do NOT double the entire 33,554,432-state dense board domain. Instead:
 *
 *     dense state  = ordinary FourPieceGenericPrimitiveState board state
 *     EP overlay   = the same board state plus one boolean
 *
 * The overlay is valid only when the board geometry and side to move make an
 * en-passant capture possible. With exactly one pawn per side, the target
 * square is then uniquely determined by the board:
 *
 * WHITE to move:
 *     white pawn rank 5, black pawn rank 5, adjacent files
 *     black pawn just moved rank 7 -> rank 5
 *     EP target is black-pawn square + 8
 *
 * BLACK to move:
 *     black pawn rank 4, white pawn rank 4, adjacent files
 *     white pawn just moved rank 2 -> rank 4
 *     EP target is white-pawn square - 8
 *
 * firstExtra is always the WHITE pawn and secondExtra is always the BLACK
 * pawn. They are not interchangeable.
 */
public final class FourPieceTierTwoKpkpPrimitiveState {

    public static final int BASE_STATE_COUNT =
            FourPieceGenericPrimitiveState.STATE_COUNT;

    private FourPieceTierTwoKpkpPrimitiveState() {
    }

    public static State of(
            int baseState,
            boolean enPassantAvailable
    ) {

        if (baseState < 0
                || baseState >= BASE_STATE_COUNT) {

            throw new IllegalArgumentException(
                    "KP-KP base state out of range: "
                            + baseState
            );
        }

        if (enPassantAvailable
                && !canCarryEnPassant(
                baseState
        )) {

            throw new IllegalArgumentException(
                    "KP-KP state cannot carry en-passant availability."
            );
        }

        return new State(
                baseState,
                enPassantAvailable
        );
    }

    public static State encode(
            int whiteKing,
            int blackKing,
            int whitePawn,
            int blackPawn,
            boolean blackToMove,
            boolean enPassantAvailable
    ) {

        int baseState =
                FourPieceGenericPrimitiveState.encode(
                        whiteKing,
                        blackKing,
                        whitePawn,
                        blackPawn,
                        blackToMove
                );

        return of(
                baseState,
                enPassantAvailable
        );
    }

    public static int whiteKing(
            State state
    ) {

        return FourPieceGenericPrimitiveState.whiteKing(
                requireState(
                        state
                ).baseState()
        );
    }

    public static int blackKing(
            State state
    ) {

        return FourPieceGenericPrimitiveState.blackKing(
                requireState(
                        state
                ).baseState()
        );
    }

    public static int whitePawn(
            State state
    ) {

        return FourPieceGenericPrimitiveState.firstExtra(
                requireState(
                        state
                ).baseState()
        );
    }

    public static int blackPawn(
            State state
    ) {

        return FourPieceGenericPrimitiveState.secondExtra(
                requireState(
                        state
                ).baseState()
        );
    }

    public static boolean blackToMove(
            State state
    ) {

        return FourPieceGenericPrimitiveState.blackToMove(
                requireState(
                        state
                ).baseState()
        );
    }

    public static boolean canCarryEnPassant(
            int baseState
    ) {

        int whiteKing =
                FourPieceGenericPrimitiveState.whiteKing(
                        baseState
                );

        int blackKing =
                FourPieceGenericPrimitiveState.blackKing(
                        baseState
                );

        int whitePawn =
                FourPieceGenericPrimitiveState.firstExtra(
                        baseState
                );

        int blackPawn =
                FourPieceGenericPrimitiveState.secondExtra(
                        baseState
                );

        boolean blackToMove =
                FourPieceGenericPrimitiveState.blackToMove(
                        baseState
                );

        int whiteFile =
                whitePawn & 7;

        int whiteRank =
                whitePawn >>> 3;

        int blackFile =
                blackPawn & 7;

        int blackRank =
                blackPawn >>> 3;

        if (Math.abs(
                whiteFile - blackFile
        ) != 1) {

            return false;
        }

        /*
         * EP history is stronger than board geometry alone.
         *
         * The pawn that just double-pushed must have had both its origin square
         * and its crossed square free of either king immediately before the
         * move. With only kings and the two pawns on the board, those are the
         * only additional blockers that can make the alleged double push
         * historically impossible.
         */
        int origin;
        int crossed;

        if (!blackToMove) {

            // WHITE to move after BLACK's rank-7 -> rank-5 double push.
            if (whiteRank != 4
                    || blackRank != 4) {

                return false;
            }

            origin =
                    6 * 8 + blackFile;

            crossed =
                    5 * 8 + blackFile;

        } else {

            // BLACK to move after WHITE's rank-2 -> rank-4 double push.
            if (whiteRank != 3
                    || blackRank != 3) {

                return false;
            }

            origin =
                    1 * 8 + whiteFile;

            crossed =
                    2 * 8 + whiteFile;
        }

        return whiteKing != origin
                && whiteKing != crossed
                && blackKing != origin
                && blackKing != crossed;
    }


    public static int enPassantTargetSquare(
            State state
    ) {

        State actual =
                requireState(
                        state
                );

        if (!actual.enPassantAvailable()) {

            return -1;
        }

        if (!canCarryEnPassant(
                actual.baseState()
        )) {

            throw new IllegalStateException(
                    "Invalid KP-KP en-passant overlay."
            );
        }

        if (blackToMove(
                actual
        )) {

            // WHITE just double-pushed. BLACK captures downward.
            return whitePawn(
                    actual
            ) - 8;
        }

        // BLACK just double-pushed. WHITE captures upward.
        return blackPawn(
                actual
        ) + 8;
    }

    public static State withoutEnPassant(
            State state
    ) {

        return of(
                requireState(
                        state
                ).baseState(),
                false
        );
    }

    private static State requireState(
            State state
    ) {

        if (state == null) {

            throw new IllegalArgumentException(
                    "KP-KP primitive state cannot be null."
            );
        }

        return state;
    }

    public record State(
            int baseState,
            boolean enPassantAvailable
    ) {

        public State {

            if (baseState < 0
                    || baseState >= BASE_STATE_COUNT) {

                throw new IllegalArgumentException(
                        "KP-KP base state out of range: "
                                + baseState
                );
            }

            if (enPassantAvailable
                    && !FourPieceTierTwoKpkpPrimitiveState
                    .canCarryEnPassant(
                            baseState
                    )) {

                throw new IllegalArgumentException(
                        "KP-KP state cannot carry en-passant availability."
                );
            }
        }
    }
}

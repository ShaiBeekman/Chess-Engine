package main.java.chess.endgame;


/**
 * Generic primitive state encoding for one four-piece material class.
 *
 * Every four-piece state contains:
 *
 *     white king
 *     black king
 *     first extra piece
 *     second extra piece
 *     side to move
 *
 * Material type and piece ownership are intentionally NOT encoded here.
 * They belong to the FourPieceMaterialClass being solved.
 *
 * Layout:
 *
 *     bit 0       side to move
 *                 0 = white
 *                 1 = black
 *
 *     bits 1..6   second extra square
 *     bits 7..12  first extra square
 *     bits 13..18 black king square
 *     bits 19..24 white king square
 *
 * This is deliberately compatible with the square ordering already used
 * by the specialized KQRK primitive representation.
 */
public final class FourPieceGenericPrimitiveState {

    public static final int STATE_COUNT =
            64 * 64 * 64 * 64 * 2;


    private FourPieceGenericPrimitiveState() {

    }


    public static int encode(
            int whiteKing,
            int blackKing,
            int firstExtra,
            int secondExtra,
            boolean blackToMove
    ) {

        requireSquare(
                whiteKing
        );

        requireSquare(
                blackKing
        );

        requireSquare(
                firstExtra
        );

        requireSquare(
                secondExtra
        );


        int packed =
                whiteKing;

        packed =
                (packed << 6)
                        | blackKing;

        packed =
                (packed << 6)
                        | firstExtra;

        packed =
                (packed << 6)
                        | secondExtra;


        return (packed << 1)
                | (blackToMove
                ? 1
                : 0);
    }


    public static int whiteKing(
            int state
    ) {

        requireState(
                state
        );


        return (state >>> 19)
                & 63;
    }


    public static int blackKing(
            int state
    ) {

        requireState(
                state
        );


        return (state >>> 13)
                & 63;
    }


    public static int firstExtra(
            int state
    ) {

        requireState(
                state
        );


        return (state >>> 7)
                & 63;
    }


    public static int secondExtra(
            int state
    ) {

        requireState(
                state
        );


        return (state >>> 1)
                & 63;
    }


    public static boolean blackToMove(
            int state
    ) {

        requireState(
                state
        );


        return (state & 1)
                != 0;
    }


    /**
     * Same-side identical pieces are physically indistinguishable.
     *
     * For:
     *
     *     KQQK
     *     KRRK
     *     KBBK
     *     KNNK
     *
     * and eventually KPPK, we choose:
     *
     *     firstExtra < secondExtra
     *
     * as the canonical primitive representation.
     *
     * Split material never uses this reduction because ownership
     * distinguishes the two pieces.
     */
    public static boolean isCanonical(
            int state,
            FourPieceMaterialClass material
    ) {

        if (!hasInterchangeableExtras(
                material
        )) {

            return true;
        }


        return firstExtra(
                state
        )
                < secondExtra(
                state
        );
    }


    public static int canonicalize(
            int state,
            FourPieceMaterialClass material
    ) {

        if (!hasInterchangeableExtras(
                material
        )) {

            return state;
        }


        int first =
                firstExtra(
                        state
                );

        int second =
                secondExtra(
                        state
                );


        if (first <= second) {

            return state;
        }


        return encode(
                whiteKing(
                        state
                ),
                blackKing(
                        state
                ),
                second,
                first,
                blackToMove(
                        state
                )
        );
    }


    private static boolean hasInterchangeableExtras(
            FourPieceMaterialClass material
    ) {

        if (material == null) {

            throw new IllegalArgumentException(
                    "Material cannot be null."
            );
        }


        return material.distribution()
                == FourPieceMaterialClass.Distribution.SAME_SIDE
                && material.firstType()
                == material.secondType();
    }


    private static void requireSquare(
            int square
    ) {

        if (square < 0
                || square >= 64) {

            throw new IllegalArgumentException(
                    "Square must be between 0 and 63: "
                            + square
            );
        }
    }


    private static void requireState(
            int state
    ) {

        if (state < 0
                || state >= STATE_COUNT) {

            throw new IllegalArgumentException(
                    "State must be between 0 and "
                            + (STATE_COUNT - 1)
                            + ": "
                            + state
            );
        }
    }
}
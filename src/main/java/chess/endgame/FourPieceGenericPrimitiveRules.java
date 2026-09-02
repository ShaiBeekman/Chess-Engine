package main.java.chess.endgame;

import main.java.chess.model.PieceType;


/**
 * Allocation-free structural legality and attack rules for generic
 * pawnless four-piece material.
 *
 * Supported material:
 *
 *     Tier 0 only
 *
 *     Q, R, B, N
 *
 * Pawns are deliberately excluded from this milestone because they add:
 *
 *     directionality
 *     promotion
 *     promotion material transitions
 *
 * SAME_SIDE ownership:
 *
 *     sameSideOwnerIsWhite == true
 *         both extras belong to White
 *
 *     sameSideOwnerIsWhite == false
 *         both extras belong to Black
 *
 * SPLIT ownership:
 *
 *     first extra belongs to White
 *     second extra belongs to Black
 *
 * That fixed split orientation gives the canonical material class a
 * concrete board interpretation without duplicating color-inverted
 * tablebases.
 */
public final class FourPieceGenericPrimitiveRules {

    private FourPieceGenericPrimitiveRules() {

    }


    public static boolean isStructurallyLegal(
            int state,
            FourPieceMaterialClass material,
            boolean sameSideOwnerIsWhite
    ) {

        requireTierZero(
                material
        );


        int whiteKing =
                FourPieceGenericPrimitiveState.whiteKing(
                        state
                );

        int blackKing =
                FourPieceGenericPrimitiveState.blackKing(
                        state
                );

        int first =
                FourPieceGenericPrimitiveState.firstExtra(
                        state
                );

        int second =
                FourPieceGenericPrimitiveState.secondExtra(
                        state
                );


        if (!allDistinct(
                whiteKing,
                blackKing,
                first,
                second
        )) {

            return false;
        }


        if (kingsAdjacent(
                whiteKing,
                blackKing
        )) {

            return false;
        }


        /*
         * Same-side identical pieces have two primitive encodings for the
         * same chess position.
         *
         * Only the canonical ordering is admitted into the generic state
         * space.
         */
        if (!FourPieceGenericPrimitiveState.isCanonical(
                state,
                material
        )) {

            return false;
        }


        boolean blackToMove =
                FourPieceGenericPrimitiveState.blackToMove(
                        state
                );


        /*
         * A legal chess position cannot have the side that just moved
         * currently in check.
         *
         * If Black is to move, White just moved.
         * Therefore White's king must not be attacked by Black.
         *
         * If White is to move, Black just moved.
         * Therefore Black's king must not be attacked by White.
         */
        if (blackToMove) {

            return !squareAttackedBy(
                    whiteKing,
                    false,
                    whiteKing,
                    blackKing,
                    first,
                    second,
                    material,
                    sameSideOwnerIsWhite
            );
        }


        return !squareAttackedBy(
                blackKing,
                true,
                whiteKing,
                blackKing,
                first,
                second,
                material,
                sameSideOwnerIsWhite
        );
    }


    public static boolean sideToMoveIsInCheck(
            int state,
            FourPieceMaterialClass material,
            boolean sameSideOwnerIsWhite
    ) {

        requireTierZero(
                material
        );


        int whiteKing =
                FourPieceGenericPrimitiveState.whiteKing(
                        state
                );

        int blackKing =
                FourPieceGenericPrimitiveState.blackKing(
                        state
                );

        int first =
                FourPieceGenericPrimitiveState.firstExtra(
                        state
                );

        int second =
                FourPieceGenericPrimitiveState.secondExtra(
                        state
                );


        boolean blackToMove =
                FourPieceGenericPrimitiveState.blackToMove(
                        state
                );


        if (blackToMove) {

            return squareAttackedBy(
                    blackKing,
                    true,
                    whiteKing,
                    blackKing,
                    first,
                    second,
                    material,
                    sameSideOwnerIsWhite
            );
        }


        return squareAttackedBy(
                whiteKing,
                false,
                whiteKing,
                blackKing,
                first,
                second,
                material,
                sameSideOwnerIsWhite
        );
    }


    /**
     * Tests whether one color attacks a square in the current four-piece
     * position.
     */
    public static boolean squareAttackedBy(
            int target,
            boolean attackingWhite,
            int whiteKing,
            int blackKing,
            int firstExtra,
            int secondExtra,
            FourPieceMaterialClass material,
            boolean sameSideOwnerIsWhite
    ) {

        requireTierZero(
                material
        );


        int attackingKing =
                attackingWhite
                        ? whiteKing
                        : blackKing;


        if (kingsAdjacent(
                attackingKing,
                target
        )) {

            return true;
        }


        boolean firstIsWhite =
                firstExtraIsWhite(
                        material,
                        sameSideOwnerIsWhite
                );

        boolean secondIsWhite =
                secondExtraIsWhite(
                        material,
                        sameSideOwnerIsWhite
                );


        if (firstIsWhite
                == attackingWhite
                && pieceAttacks(
                material.firstType(),
                firstExtra,
                target,
                whiteKing,
                blackKing,
                firstExtra,
                secondExtra
        )) {

            return true;
        }


        return secondIsWhite
                == attackingWhite
                && pieceAttacks(
                material.secondType(),
                secondExtra,
                target,
                whiteKing,
                blackKing,
                firstExtra,
                secondExtra
        );
    }


    /**
     * Generic Tier-0 non-king attack test.
     *
     * The four occupied squares are supplied so slider rays can stop at
     * blockers without constructing a Board or Piece collection.
     */
    public static boolean pieceAttacks(
            PieceType type,
            int from,
            int target,
            int whiteKing,
            int blackKing,
            int firstExtra,
            int secondExtra
    ) {

        return switch (type) {

            case QUEEN ->
                    queenAttacks(
                            from,
                            target,
                            whiteKing,
                            blackKing,
                            firstExtra,
                            secondExtra
                    );

            case ROOK ->
                    rookAttacks(
                            from,
                            target,
                            whiteKing,
                            blackKing,
                            firstExtra,
                            secondExtra
                    );

            case BISHOP ->
                    bishopAttacks(
                            from,
                            target,
                            whiteKing,
                            blackKing,
                            firstExtra,
                            secondExtra
                    );

            case KNIGHT ->
                    knightAttacks(
                            from,
                            target
                    );

            case PAWN ->
                    throw new IllegalArgumentException(
                            "Pawn attacks are not part of Tier-0 generic rules."
                    );

            case KING ->
                    throw new IllegalArgumentException(
                            "KING is not a valid four-piece extra-piece type."
                    );
        };
    }


    public static boolean queenAttacks(
            int from,
            int target,
            int whiteKing,
            int blackKing,
            int firstExtra,
            int secondExtra
    ) {

        return rookAttacks(
                from,
                target,
                whiteKing,
                blackKing,
                firstExtra,
                secondExtra
        )
                ||
                bishopAttacks(
                        from,
                        target,
                        whiteKing,
                        blackKing,
                        firstExtra,
                        secondExtra
                );
    }


    public static boolean rookAttacks(
            int from,
            int target,
            int whiteKing,
            int blackKing,
            int firstExtra,
            int secondExtra
    ) {

        int fromFile =
                from & 7;

        int fromRank =
                from >>> 3;

        int targetFile =
                target & 7;

        int targetRank =
                target >>> 3;


        int df;
        int dr;


        if (fromFile == targetFile) {

            df = 0;

            dr =
                    Integer.compare(
                            targetRank,
                            fromRank
                    );

        } else if (fromRank == targetRank) {

            df =
                    Integer.compare(
                            targetFile,
                            fromFile
                    );

            dr = 0;

        } else {

            return false;
        }


        return rayClear(
                from,
                target,
                df,
                dr,
                whiteKing,
                blackKing,
                firstExtra,
                secondExtra
        );
    }


    public static boolean bishopAttacks(
            int from,
            int target,
            int whiteKing,
            int blackKing,
            int firstExtra,
            int secondExtra
    ) {

        int fromFile =
                from & 7;

        int fromRank =
                from >>> 3;

        int targetFile =
                target & 7;

        int targetRank =
                target >>> 3;


        int fileDistance =
                targetFile
                        - fromFile;

        int rankDistance =
                targetRank
                        - fromRank;


        if (Math.abs(
                fileDistance
        )
                != Math.abs(
                rankDistance
        )) {

            return false;
        }


        if (fileDistance == 0) {

            return false;
        }


        int df =
                Integer.compare(
                        targetFile,
                        fromFile
                );

        int dr =
                Integer.compare(
                        targetRank,
                        fromRank
                );


        return rayClear(
                from,
                target,
                df,
                dr,
                whiteKing,
                blackKing,
                firstExtra,
                secondExtra
        );
    }


    public static boolean knightAttacks(
            int from,
            int target
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
                Math.abs(
                        fromFile
                                - targetFile
                );

        int dr =
                Math.abs(
                        fromRank
                                - targetRank
                );


        return (df == 1
                && dr == 2)
                ||
                (df == 2
                        && dr == 1);
    }


    public static boolean firstExtraIsWhite(
            FourPieceMaterialClass material,
            boolean sameSideOwnerIsWhite
    ) {

        if (material.distribution()
                == FourPieceMaterialClass.Distribution.SAME_SIDE) {

            return sameSideOwnerIsWhite;
        }


        /*
         * Canonical SPLIT orientation:
         *
         * firstType belongs to White.
         */
        return true;
    }


    public static boolean secondExtraIsWhite(
            FourPieceMaterialClass material,
            boolean sameSideOwnerIsWhite
    ) {

        if (material.distribution()
                == FourPieceMaterialClass.Distribution.SAME_SIDE) {

            return sameSideOwnerIsWhite;
        }


        /*
         * Canonical SPLIT orientation:
         *
         * secondType belongs to Black.
         */
        return false;
    }


    private static boolean rayClear(
            int from,
            int target,
            int df,
            int dr,
            int whiteKing,
            int blackKing,
            int firstExtra,
            int secondExtra
    ) {

        int file =
                (from & 7)
                        + df;

        int rank =
                (from >>> 3)
                        + dr;


        while (inside(
                file,
                rank
        )) {

            int square =
                    rank * 8
                            + file;


            if (square == target) {

                return true;
            }


            if (square == whiteKing
                    || square == blackKing
                    || square == firstExtra
                    || square == secondExtra) {

                return false;
            }


            file +=
                    df;

            rank +=
                    dr;
        }


        return false;
    }


    private static boolean allDistinct(
            int first,
            int second,
            int third,
            int fourth
    ) {

        return first != second
                && first != third
                && first != fourth
                && second != third
                && second != fourth
                && third != fourth;
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


    private static boolean inside(
            int file,
            int rank
    ) {

        return file >= 0
                && file < 8
                && rank >= 0
                && rank < 8;
    }


    private static void requireTierZero(
            FourPieceMaterialClass material
    ) {

        if (material == null) {

            throw new IllegalArgumentException(
                    "Material cannot be null."
            );
        }


        if (material.buildTier()
                != 0) {

            throw new IllegalArgumentException(
                    "Generic primitive rules milestone currently supports only Tier-0 pawnless material: "
                            + material.displayName()
            );
        }
    }
}
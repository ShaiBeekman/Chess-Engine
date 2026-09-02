package main.java.chess.endgame;

import main.java.chess.model.PieceType;

import java.util.Arrays;


/**
 * Milestone 43.
 *
 * Generic SAME-CLASS predecessor generator for all eight canonical Tier-1
 * one-pawn four-piece material families.
 *
 * Boundary transitions are intentionally excluded. Retrograde uses only
 * parents that remain in the same four-piece material class; captures and
 * promotions are exact dependency seeds handled by the builder.
 *
 * Strategy:
 *
 *   1. Determine which color moved immediately before {@code child}.
 *   2. Reverse the geometry of every piece that color owns in this material.
 *   3. Construct a candidate primitive parent.
 *   4. Require primitive structural legality.
 *   5. Forward-verify with the proven M42 generic Tier-1 move generator.
 *
 * Forward verification is deliberately retained. It makes this reverse
 * generator exact without duplicating all move-legality edge cases.
 */
public final class FourPieceTierOnePrimitivePredecessorGenerator {

    private static final int[] KING_DF = {
            -1, -1, -1, 0, 0, 1, 1, 1
    };

    private static final int[] KING_DR = {
            -1, 0, 1, -1, 1, -1, 0, 1
    };

    private static final int[] KNIGHT_DF = {
            -2, -2, -1, -1, 1, 1, 2, 2
    };

    private static final int[] KNIGHT_DR = {
            -1, 1, -2, 2, -2, 2, -1, 1
    };

    private static final int[][] SLIDER_DIRECTIONS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };


    private FourPieceTierOnePrimitivePredecessorGenerator() {
    }


    public static final class Buffer {

        private int[] states;

        private int size;


        public Buffer() {

            this(
                    64
            );
        }


        public Buffer(
                int capacity
        ) {

            states =
                    new int[
                            Math.max(
                                    16,
                                    capacity
                            )
                            ];
        }


        public void clear() {

            size =
                    0;
        }


        public int size() {

            return size;
        }


        public int state(
                int index
        ) {

            return states[index];
        }


        private void addUnique(
                int state
        ) {

            for (int i = 0;
                 i < size;
                 i++) {

                if (states[i]
                        == state) {

                    return;
                }
            }


            if (size
                    == states.length) {

                states =
                        Arrays.copyOf(
                                states,
                                states.length * 2
                        );
            }


            states[size++] =
                    state;
        }
    }


    public static int generatePredecessors(
            int child,
            FourPieceMaterialClass material,
            boolean sameSideOwnerIsWhite,
            Buffer output,
            FourPieceTierOnePrimitiveMoveGenerator.Buffer forwardBuffer
    ) {

        if (!FourPieceTierOnePrimitiveMoveGenerator.supports(
                material
        )) {

            throw new IllegalArgumentException(
                    "Expected a canonical Tier-1 one-pawn material class."
            );
        }


        if (output == null) {

            throw new IllegalArgumentException(
                    "Predecessor output buffer cannot be null."
            );
        }


        if (forwardBuffer == null) {

            throw new IllegalArgumentException(
                    "Forward-validation buffer cannot be null."
            );
        }


        output.clear();


        if (!FourPieceTierOnePrimitiveMoveGenerator
                .isStructurallyLegal(
                        child,
                        material,
                        sameSideOwnerIsWhite
                )) {

            return 0;
        }


        int wk =
                FourPieceGenericPrimitiveState.whiteKing(
                        child
                );

        int bk =
                FourPieceGenericPrimitiveState.blackKing(
                        child
                );

        int first =
                FourPieceGenericPrimitiveState.firstExtra(
                        child
                );

        int pawn =
                FourPieceGenericPrimitiveState.secondExtra(
                        child
                );

        boolean childBlackToMove =
                FourPieceGenericPrimitiveState.blackToMove(
                        child
                );


        /*
         * If child is BLACK to move, WHITE made the previous move.
         * If child is WHITE to move, BLACK made the previous move.
         */
        boolean previousMoverWhite =
                childBlackToMove;

        boolean parentBlackToMove =
                !childBlackToMove;

        boolean firstOwnerWhite =
                firstOwnerIsWhite(
                        material,
                        sameSideOwnerIsWhite
                );

        boolean pawnOwnerWhite =
                pawnOwnerIsWhite(
                        material,
                        sameSideOwnerIsWhite
                );


        int previousMoverKing =
                previousMoverWhite
                        ? wk
                        : bk;


        reverseKing(
                child,
                wk,
                bk,
                first,
                pawn,
                previousMoverKing,
                parentBlackToMove,
                material,
                sameSideOwnerIsWhite,
                output,
                forwardBuffer
        );


        if (previousMoverWhite
                == firstOwnerWhite) {

            reverseFirstExtra(
                    child,
                    wk,
                    bk,
                    first,
                    pawn,
                    parentBlackToMove,
                    material,
                    sameSideOwnerIsWhite,
                    output,
                    forwardBuffer
            );
        }


        if (previousMoverWhite
                == pawnOwnerWhite) {

            reversePawn(
                    child,
                    wk,
                    bk,
                    first,
                    pawn,
                    pawnOwnerWhite,
                    parentBlackToMove,
                    material,
                    sameSideOwnerIsWhite,
                    output,
                    forwardBuffer
            );
        }


        return output.size();
    }


    public static int generatePredecessors(
            int child,
            FourPieceMaterialClass material,
            boolean sameSideOwnerIsWhite,
            Buffer output
    ) {

        return generatePredecessors(
                child,
                material,
                sameSideOwnerIsWhite,
                output,
                new FourPieceTierOnePrimitiveMoveGenerator.Buffer(
                        64
                )
        );
    }


    private static void reverseKing(
            int child,
            int wk,
            int bk,
            int first,
            int pawn,
            int currentKingSquare,
            boolean parentBlackToMove,
            FourPieceMaterialClass material,
            boolean sameSideOwnerIsWhite,
            Buffer output,
            FourPieceTierOnePrimitiveMoveGenerator.Buffer forwardBuffer
    ) {

        int file =
                currentKingSquare & 7;

        int rank =
                currentKingSquare >>> 3;


        for (int i = 0;
             i < KING_DF.length;
             i++) {

            int previousFile =
                    file + KING_DF[i];

            int previousRank =
                    rank + KING_DR[i];


            if (!inside(
                    previousFile,
                    previousRank
            )) {

                continue;
            }


            int previousSquare =
                    previousRank * 8
                            + previousFile;


            /*
             * Same-class predecessors cannot be captures. All four pieces
             * must exist in both parent and child.
             */
            if (occupied(
                    previousSquare,
                    wk,
                    bk,
                    first,
                    pawn
            )) {

                continue;
            }


            int parentWk =
                    wk;

            int parentBk =
                    bk;


            if (currentKingSquare
                    == wk) {

                parentWk =
                        previousSquare;

            } else if (currentKingSquare
                    == bk) {

                parentBk =
                        previousSquare;

            } else {

                throw new IllegalStateException(
                        "Reverse Tier-1 king source is not a king."
                );
            }


            validateCandidate(
                    child,
                    parentWk,
                    parentBk,
                    first,
                    pawn,
                    parentBlackToMove,
                    material,
                    sameSideOwnerIsWhite,
                    output,
                    forwardBuffer
            );
        }
    }


    private static void reverseFirstExtra(
            int child,
            int wk,
            int bk,
            int first,
            int pawn,
            boolean parentBlackToMove,
            FourPieceMaterialClass material,
            boolean sameSideOwnerIsWhite,
            Buffer output,
            FourPieceTierOnePrimitiveMoveGenerator.Buffer forwardBuffer
    ) {

        switch (material.firstType()) {

            case QUEEN ->
                    reverseSlider(
                            child,
                            wk,
                            bk,
                            first,
                            pawn,
                            parentBlackToMove,
                            material,
                            sameSideOwnerIsWhite,
                            true,
                            true,
                            output,
                            forwardBuffer
                    );

            case ROOK ->
                    reverseSlider(
                            child,
                            wk,
                            bk,
                            first,
                            pawn,
                            parentBlackToMove,
                            material,
                            sameSideOwnerIsWhite,
                            true,
                            false,
                            output,
                            forwardBuffer
                    );

            case BISHOP ->
                    reverseSlider(
                            child,
                            wk,
                            bk,
                            first,
                            pawn,
                            parentBlackToMove,
                            material,
                            sameSideOwnerIsWhite,
                            false,
                            true,
                            output,
                            forwardBuffer
                    );

            case KNIGHT ->
                    reverseKnight(
                            child,
                            wk,
                            bk,
                            first,
                            pawn,
                            parentBlackToMove,
                            material,
                            sameSideOwnerIsWhite,
                            output,
                            forwardBuffer
                    );

            default ->
                    throw new IllegalStateException(
                            "Unsupported Tier-1 first extra: "
                                    + material.firstType()
                    );
        }
    }


    private static void reverseSlider(
            int child,
            int wk,
            int bk,
            int first,
            int pawn,
            boolean parentBlackToMove,
            FourPieceMaterialClass material,
            boolean sameSideOwnerIsWhite,
            boolean orthogonal,
            boolean diagonal,
            Buffer output,
            FourPieceTierOnePrimitiveMoveGenerator.Buffer forwardBuffer
    ) {

        int file =
                first & 7;

        int rank =
                first >>> 3;


        for (int[] direction :
                SLIDER_DIRECTIONS) {

            boolean isDiagonal =
                    direction[0] != 0
                            && direction[1] != 0;


            if (isDiagonal
                    && !diagonal) {

                continue;
            }


            if (!isDiagonal
                    && !orthogonal) {

                continue;
            }


            int previousFile =
                    file + direction[0];

            int previousRank =
                    rank + direction[1];


            while (inside(
                    previousFile,
                    previousRank
            )) {

                int previousSquare =
                        previousRank * 8
                                + previousFile;


                if (previousSquare == wk
                        || previousSquare == bk
                        || previousSquare == pawn) {

                    break;
                }


                validateCandidate(
                        child,
                        wk,
                        bk,
                        previousSquare,
                        pawn,
                        parentBlackToMove,
                        material,
                        sameSideOwnerIsWhite,
                        output,
                        forwardBuffer
                );


                previousFile +=
                        direction[0];

                previousRank +=
                        direction[1];
            }
        }
    }


    private static void reverseKnight(
            int child,
            int wk,
            int bk,
            int first,
            int pawn,
            boolean parentBlackToMove,
            FourPieceMaterialClass material,
            boolean sameSideOwnerIsWhite,
            Buffer output,
            FourPieceTierOnePrimitiveMoveGenerator.Buffer forwardBuffer
    ) {

        int file =
                first & 7;

        int rank =
                first >>> 3;


        for (int i = 0;
             i < KNIGHT_DF.length;
             i++) {

            int previousFile =
                    file + KNIGHT_DF[i];

            int previousRank =
                    rank + KNIGHT_DR[i];


            if (!inside(
                    previousFile,
                    previousRank
            )) {

                continue;
            }


            int previousSquare =
                    previousRank * 8
                            + previousFile;


            if (occupied(
                    previousSquare,
                    wk,
                    bk,
                    first,
                    pawn
            )) {

                continue;
            }


            validateCandidate(
                    child,
                    wk,
                    bk,
                    previousSquare,
                    pawn,
                    parentBlackToMove,
                    material,
                    sameSideOwnerIsWhite,
                    output,
                    forwardBuffer
            );
        }
    }


    private static void reversePawn(
            int child,
            int wk,
            int bk,
            int first,
            int pawn,
            boolean pawnOwnerWhite,
            boolean parentBlackToMove,
            FourPieceMaterialClass material,
            boolean sameSideOwnerIsWhite,
            Buffer output,
            FourPieceTierOnePrimitiveMoveGenerator.Buffer forwardBuffer
    ) {

        int file =
                pawn & 7;

        int rank =
                pawn >>> 3;

        int direction =
                pawnOwnerWhite
                        ? 1
                        : -1;


        /*
         * Reverse a one-square non-promotion pawn push.
         */
        int onePreviousRank =
                rank - direction;


        if (onePreviousRank > 0
                && onePreviousRank < 7) {

            int previousPawn =
                    onePreviousRank * 8
                            + file;


            if (!occupied(
                    previousPawn,
                    wk,
                    bk,
                    first,
                    pawn
            )) {

                validateCandidate(
                        child,
                        wk,
                        bk,
                        first,
                        previousPawn,
                        parentBlackToMove,
                        material,
                        sameSideOwnerIsWhite,
                        output,
                        forwardBuffer
                );
            }
        }


        /*
         * Reverse a starting-rank double push.
         *
         * white: rank 2 -> rank 4
         * black: rank 7 -> rank 5
         */
        int doubleDestinationRank =
                pawnOwnerWhite
                        ? 3
                        : 4;

        int startRank =
                pawnOwnerWhite
                        ? 1
                        : 6;


        if (rank
                == doubleDestinationRank) {

            int previousPawn =
                    startRank * 8
                            + file;

            int intermediate =
                    (startRank + direction) * 8
                            + file;


            if (!occupied(
                    previousPawn,
                    wk,
                    bk,
                    first,
                    pawn
            )
                    && !occupied(
                    intermediate,
                    wk,
                    bk,
                    first,
                    pawn
            )) {

                validateCandidate(
                        child,
                        wk,
                        bk,
                        first,
                        previousPawn,
                        parentBlackToMove,
                        material,
                        sameSideOwnerIsWhite,
                        output,
                        forwardBuffer
                );
            }
        }


        /*
         * No reverse diagonal pawn capture belongs to the SAME CLASS.
         *
         * There is exactly one pawn total. In SPLIT material a pawn capture
         * removes firstExtra and therefore enters a three-piece boundary.
         * In SAME_SIDE material firstExtra is friendly and cannot be captured.
         *
         * Promotion predecessors are likewise boundaries, not same-class
         * parents.
         */
    }


    private static void validateCandidate(
            int child,
            int wk,
            int bk,
            int first,
            int pawn,
            boolean parentBlackToMove,
            FourPieceMaterialClass material,
            boolean sameSideOwnerIsWhite,
            Buffer output,
            FourPieceTierOnePrimitiveMoveGenerator.Buffer forwardBuffer
    ) {

        if (!distinct(
                wk,
                bk,
                first,
                pawn
        )) {

            return;
        }


        int parent =
                FourPieceGenericPrimitiveState.encode(
                        wk,
                        bk,
                        first,
                        pawn,
                        parentBlackToMove
                );


        if (!FourPieceTierOnePrimitiveMoveGenerator
                .isStructurallyLegal(
                        parent,
                        material,
                        sameSideOwnerIsWhite
                )) {

            return;
        }


        int count =
                FourPieceTierOnePrimitiveMoveGenerator
                        .generateLegalSuccessors(
                                parent,
                                material,
                                sameSideOwnerIsWhite,
                                forwardBuffer
                        );


        for (int i = 0;
             i < count;
             i++) {

            if (forwardBuffer.boundaryType(
                    i
            ) != FourPieceTierOnePrimitiveMoveGenerator
                    .BOUNDARY_NONE) {

                continue;
            }


            if (forwardBuffer.state(
                    i
            ) == child) {

                output.addUnique(
                        parent
                );

                return;
            }
        }
    }


    private static boolean firstOwnerIsWhite(
            FourPieceMaterialClass material,
            boolean sameSideOwnerIsWhite
    ) {

        return material.distribution()
                == FourPieceMaterialClass.Distribution.SAME_SIDE
                ? sameSideOwnerIsWhite
                : true;
    }


    private static boolean pawnOwnerIsWhite(
            FourPieceMaterialClass material,
            boolean sameSideOwnerIsWhite
    ) {

        return material.distribution()
                == FourPieceMaterialClass.Distribution.SAME_SIDE
                ? sameSideOwnerIsWhite
                : false;
    }


    private static boolean occupied(
            int square,
            int wk,
            int bk,
            int first,
            int pawn
    ) {

        return square == wk
                || square == bk
                || square == first
                || square == pawn;
    }


    private static boolean distinct(
            int a,
            int b,
            int c,
            int d
    ) {

        return a != b
                && a != c
                && a != d
                && b != c
                && b != d
                && c != d;
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
}

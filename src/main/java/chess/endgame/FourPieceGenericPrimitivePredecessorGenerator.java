package main.java.chess.endgame;

import main.java.chess.model.PieceType;

import java.util.Arrays;


/**
 * Allocation-free reverse generator for generic Tier-0 four-piece
 * in-class edges.
 *
 * Every in-class four-piece -> four-piece edge is non-capturing.
 *
 * Therefore a predecessor can be reconstructed by:
 *
 *     1. identifying the previous mover
 *     2. moving one of that side's current pieces backward
 *     3. constructing the candidate parent
 *     4. validating that the already-verified forward generator
 *        actually produces the child
 *
 * The forward generator remains the final source of truth.
 *
 * This deliberately mirrors the proven KQRK predecessor architecture,
 * but supports:
 *
 *     QUEEN
 *     ROOK
 *     BISHOP
 *     KNIGHT
 *
 * and both:
 *
 *     SAME_SIDE
 *     SPLIT
 *
 * Tier-0 only. Pawns are handled later.
 */
public final class FourPieceGenericPrimitivePredecessorGenerator {

    private static final int[] KING_DF =
            {-1, -1, -1, 0, 0, 1, 1, 1};

    private static final int[] KING_DR =
            {-1, 0, 1, -1, 1, -1, 0, 1};


    private static final int[] KNIGHT_DF =
            {-2, -2, -1, -1, 1, 1, 2, 2};

    private static final int[] KNIGHT_DR =
            {-1, 1, -2, 2, -2, 2, -1, 1};


    private static final int[][] ROOK_DIRS = {
            {1, 0},
            {-1, 0},
            {0, 1},
            {0, -1}
    };


    private static final int[][] BISHOP_DIRS = {
            {1, 1},
            {1, -1},
            {-1, 1},
            {-1, -1}
    };


    private static final int[][] QUEEN_DIRS = {
            {1, 0},
            {-1, 0},
            {0, 1},
            {0, -1},
            {1, 1},
            {1, -1},
            {-1, 1},
            {-1, -1}
    };


    /*
     * Candidate verification buffer.
     *
     * Predecessor generation itself remains allocation-free.
     */
    private static final FourPieceGenericPrimitiveMoveGenerator.Buffer
            VERIFY =
            new FourPieceGenericPrimitiveMoveGenerator.Buffer(
                    64
            );


    private FourPieceGenericPrimitivePredecessorGenerator() {

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

            if (index < 0
                    || index >= size) {

                throw new IndexOutOfBoundsException(
                        "Predecessor index: "
                                + index
                                + ", size: "
                                + size
                );
            }


            return states[index];
        }


        /**
         * Canonicalization of identical same-side pieces can cause two
         * geometric reverse constructions to represent the same parent.
         *
         * Predecessor sets must contain each parent exactly once.
         */
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


    public static int generateLegalPredecessors(
            int child,
            FourPieceMaterialClass material,
            boolean sameSideOwnerIsWhite,
            Buffer output
    ) {

        if (output == null) {

            throw new IllegalArgumentException(
                    "Output buffer cannot be null."
            );
        }


        requireTierZero(
                material
        );


        output.clear();


        if (!FourPieceGenericPrimitiveRules.isStructurallyLegal(
                child,
                material,
                sameSideOwnerIsWhite
        )) {

            return 0;
        }


        int whiteKing =
                FourPieceGenericPrimitiveState.whiteKing(
                        child
                );

        int blackKing =
                FourPieceGenericPrimitiveState.blackKing(
                        child
                );

        int first =
                FourPieceGenericPrimitiveState.firstExtra(
                        child
                );

        int second =
                FourPieceGenericPrimitiveState.secondExtra(
                        child
                );


        boolean childBlackToMove =
                FourPieceGenericPrimitiveState.blackToMove(
                        child
                );


        /*
         * If Black is now to move, White made the previous move.
         *
         * If White is now to move, Black made the previous move.
         */
        boolean previousMoverIsWhite =
                childBlackToMove;


        /*
         * Every side always has a king.
         */
        reverseKing(
                child,
                whiteKing,
                blackKing,
                first,
                second,
                previousMoverIsWhite,
                childBlackToMove,
                material,
                sameSideOwnerIsWhite,
                output
        );


        boolean firstWhite =
                FourPieceGenericPrimitiveRules.firstExtraIsWhite(
                        material,
                        sameSideOwnerIsWhite
                );


        if (firstWhite
                == previousMoverIsWhite) {

            reverseExtraPiece(
                    child,
                    0,
                    whiteKing,
                    blackKing,
                    first,
                    second,
                    childBlackToMove,
                    material,
                    sameSideOwnerIsWhite,
                    output
            );
        }


        boolean secondWhite =
                FourPieceGenericPrimitiveRules.secondExtraIsWhite(
                        material,
                        sameSideOwnerIsWhite
                );


        if (secondWhite
                == previousMoverIsWhite) {

            reverseExtraPiece(
                    child,
                    1,
                    whiteKing,
                    blackKing,
                    first,
                    second,
                    childBlackToMove,
                    material,
                    sameSideOwnerIsWhite,
                    output
            );
        }


        return output.size();
    }


    private static void reverseKing(
            int child,
            int whiteKing,
            int blackKing,
            int first,
            int second,
            boolean previousMoverIsWhite,
            boolean childBlackToMove,
            FourPieceMaterialClass material,
            boolean sameSideOwnerIsWhite,
            Buffer output
    ) {

        int destination =
                previousMoverIsWhite
                        ? whiteKing
                        : blackKing;


        int destinationFile =
                destination & 7;

        int destinationRank =
                destination >>> 3;


        for (int i = 0;
             i < 8;
             i++) {

            int file =
                    destinationFile
                            + KING_DF[i];

            int rank =
                    destinationRank
                            + KING_DR[i];


            if (!inside(
                    file,
                    rank
            )) {

                continue;
            }


            int origin =
                    rank * 8
                            + file;


            if (origin == whiteKing
                    || origin == blackKing
                    || origin == first
                    || origin == second) {

                continue;
            }


            int predecessorWhiteKing =
                    previousMoverIsWhite
                            ? origin
                            : whiteKing;

            int predecessorBlackKing =
                    previousMoverIsWhite
                            ? blackKing
                            : origin;


            tryCandidate(
                    child,
                    predecessorWhiteKing,
                    predecessorBlackKing,
                    first,
                    second,
                    !childBlackToMove,
                    material,
                    sameSideOwnerIsWhite,
                    output
            );
        }
    }


    private static void reverseExtraPiece(
            int child,
            int extraIndex,
            int whiteKing,
            int blackKing,
            int first,
            int second,
            boolean childBlackToMove,
            FourPieceMaterialClass material,
            boolean sameSideOwnerIsWhite,
            Buffer output
    ) {

        PieceType type =
                extraIndex == 0
                        ? material.firstType()
                        : material.secondType();


        switch (type) {

            case QUEEN ->
                    reverseSlider(
                            child,
                            extraIndex,
                            QUEEN_DIRS,
                            whiteKing,
                            blackKing,
                            first,
                            second,
                            childBlackToMove,
                            material,
                            sameSideOwnerIsWhite,
                            output
                    );

            case ROOK ->
                    reverseSlider(
                            child,
                            extraIndex,
                            ROOK_DIRS,
                            whiteKing,
                            blackKing,
                            first,
                            second,
                            childBlackToMove,
                            material,
                            sameSideOwnerIsWhite,
                            output
                    );

            case BISHOP ->
                    reverseSlider(
                            child,
                            extraIndex,
                            BISHOP_DIRS,
                            whiteKing,
                            blackKing,
                            first,
                            second,
                            childBlackToMove,
                            material,
                            sameSideOwnerIsWhite,
                            output
                    );

            case KNIGHT ->
                    reverseKnight(
                            child,
                            extraIndex,
                            whiteKing,
                            blackKing,
                            first,
                            second,
                            childBlackToMove,
                            material,
                            sameSideOwnerIsWhite,
                            output
                    );

            case PAWN ->
                    throw new IllegalArgumentException(
                            "Pawn predecessor generation is not part of Tier 0."
                    );

            case KING ->
                    throw new IllegalArgumentException(
                            "KING cannot be an extra four-piece material type."
                    );
        }
    }


    private static void reverseSlider(
            int child,
            int extraIndex,
            int[][] directions,
            int whiteKing,
            int blackKing,
            int first,
            int second,
            boolean childBlackToMove,
            FourPieceMaterialClass material,
            boolean sameSideOwnerIsWhite,
            Buffer output
    ) {

        int destination =
                extraIndex == 0
                        ? first
                        : second;


        int destinationFile =
                destination & 7;

        int destinationRank =
                destination >>> 3;


        for (int[] direction :
                directions) {

            int file =
                    destinationFile
                            + direction[0];

            int rank =
                    destinationRank
                            + direction[1];


            while (inside(
                    file,
                    rank
            )) {

                int origin =
                        rank * 8
                                + file;


                /*
                 * These are pieces that existed in both parent and child.
                 *
                 * The destination piece itself is deliberately ignored
                 * because we are moving it backward.
                 */
                if (origin == whiteKing
                        || origin == blackKing
                        || (extraIndex == 0
                        && origin == second)
                        || (extraIndex == 1
                        && origin == first)) {

                    break;
                }


                int predecessorFirst =
                        extraIndex == 0
                                ? origin
                                : first;

                int predecessorSecond =
                        extraIndex == 1
                                ? origin
                                : second;


                tryCandidate(
                        child,
                        whiteKing,
                        blackKing,
                        predecessorFirst,
                        predecessorSecond,
                        !childBlackToMove,
                        material,
                        sameSideOwnerIsWhite,
                        output
                );


                file +=
                        direction[0];

                rank +=
                        direction[1];
            }
        }
    }


    private static void reverseKnight(
            int child,
            int extraIndex,
            int whiteKing,
            int blackKing,
            int first,
            int second,
            boolean childBlackToMove,
            FourPieceMaterialClass material,
            boolean sameSideOwnerIsWhite,
            Buffer output
    ) {

        int destination =
                extraIndex == 0
                        ? first
                        : second;


        int destinationFile =
                destination & 7;

        int destinationRank =
                destination >>> 3;


        for (int i = 0;
             i < KNIGHT_DF.length;
             i++) {

            int file =
                    destinationFile
                            + KNIGHT_DF[i];

            int rank =
                    destinationRank
                            + KNIGHT_DR[i];


            if (!inside(
                    file,
                    rank
            )) {

                continue;
            }


            int origin =
                    rank * 8
                            + file;


            if (origin == whiteKing
                    || origin == blackKing
                    || (extraIndex == 0
                    && origin == second)
                    || (extraIndex == 1
                    && origin == first)) {

                continue;
            }


            int predecessorFirst =
                    extraIndex == 0
                            ? origin
                            : first;

            int predecessorSecond =
                    extraIndex == 1
                            ? origin
                            : second;


            tryCandidate(
                    child,
                    whiteKing,
                    blackKing,
                    predecessorFirst,
                    predecessorSecond,
                    !childBlackToMove,
                    material,
                    sameSideOwnerIsWhite,
                    output
            );
        }
    }


    /**
     * Construct a possible predecessor, canonicalize identical pieces,
     * verify its structural legality, and finally require its forward
     * successor set to actually contain the requested child.
     */
    private static void tryCandidate(
            int child,
            int whiteKing,
            int blackKing,
            int first,
            int second,
            boolean predecessorBlackToMove,
            FourPieceMaterialClass material,
            boolean sameSideOwnerIsWhite,
            Buffer output
    ) {

        int predecessor =
                FourPieceGenericPrimitiveState.encode(
                        whiteKing,
                        blackKing,
                        first,
                        second,
                        predecessorBlackToMove
                );


        predecessor =
                FourPieceGenericPrimitiveState.canonicalize(
                        predecessor,
                        material
                );


        if (!FourPieceGenericPrimitiveRules.isStructurallyLegal(
                predecessor,
                material,
                sameSideOwnerIsWhite
        )) {

            return;
        }


        int successorCount =
                FourPieceGenericPrimitiveMoveGenerator.generateLegalSuccessors(
                        predecessor,
                        material,
                        sameSideOwnerIsWhite,
                        VERIFY
                );


        for (int i = 0;
             i < successorCount;
             i++) {

            if (VERIFY.isBoundary(
                    i
            )) {

                continue;
            }


            if (VERIFY.state(
                    i
            )
                    == child) {

                output.addUnique(
                        predecessor
                );

                return;
            }
        }
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
                    "Generic primitive predecessor generation currently supports only Tier-0 pawnless material: "
                            + material.displayName()
            );
        }
    }
}
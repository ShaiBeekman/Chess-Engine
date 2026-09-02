package main.java.chess.endgame;

import main.java.chess.model.PieceType;

import java.util.Arrays;


/**
 * Allocation-free legal successor generator for generic Tier-0
 * four-piece material.
 *
 * Supported extra-piece types:
 *
 *     QUEEN
 *     ROOK
 *     BISHOP
 *     KNIGHT
 *
 * Pawns are deliberately deferred to the later pawn tiers.
 *
 *
 * STATE OWNERSHIP
 * ===============
 *
 * SAME_SIDE:
 *
 *     both extra pieces belong to sameSideOwnerIsWhite
 *
 * SPLIT:
 *
 *     first extra  belongs to White
 *     second extra belongs to Black
 *
 *
 * SUCCESSORS
 * ==========
 *
 * A successor either remains inside the same four-piece material class,
 * or a capture reduces the position to an exact three-piece boundary.
 *
 * For in-class successors:
 *
 *     state(index)
 *
 * contains the encoded generic four-piece state.
 *
 * For boundary successors:
 *
 *     state(index) == -1
 *
 * and the buffer stores the complete resulting three-piece primitive
 * position:
 *
 *     white king square
 *     black king square
 *     surviving non-king type
 *     surviving non-king square
 *     surviving non-king color
 *     side to move
 *
 * Keeping the full boundary position will be important when the generic
 * retrograde solver is connected to the already-solved three-piece
 * tablebases.
 */
public final class FourPieceGenericPrimitiveMoveGenerator {

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


    private FourPieceGenericPrimitiveMoveGenerator() {

    }


    /**
     * Reusable allocation-free successor buffer.
     */
    public static final class Buffer {

        private int[] states;

        private boolean[] boundaries;

        private int[] boundaryWhiteKings;
        private int[] boundaryBlackKings;

        private PieceType[] survivingTypes;
        private int[] survivingSquares;
        private boolean[] survivingWhites;

        private boolean[] boundaryBlackToMove;

        private int size;


        public Buffer() {

            this(
                    64
            );
        }


        public Buffer(
                int capacity
        ) {

            int initial =
                    Math.max(
                            16,
                            capacity
                    );


            states =
                    new int[initial];

            boundaries =
                    new boolean[initial];

            boundaryWhiteKings =
                    new int[initial];

            boundaryBlackKings =
                    new int[initial];

            survivingTypes =
                    new PieceType[initial];

            survivingSquares =
                    new int[initial];

            survivingWhites =
                    new boolean[initial];

            boundaryBlackToMove =
                    new boolean[initial];
        }


        public void clear() {

            size =
                    0;
        }


        public int size() {

            return size;
        }


        public boolean isBoundary(
                int index
        ) {

            requireIndex(
                    index
            );


            return boundaries[index];
        }


        public int state(
                int index
        ) {

            requireIndex(
                    index
            );


            return states[index];
        }


        public int boundaryWhiteKing(
                int index
        ) {

            requireBoundaryIndex(
                    index
            );


            return boundaryWhiteKings[index];
        }


        public int boundaryBlackKing(
                int index
        ) {

            requireBoundaryIndex(
                    index
            );


            return boundaryBlackKings[index];
        }


        public PieceType survivingPieceType(
                int index
        ) {

            requireBoundaryIndex(
                    index
            );


            return survivingTypes[index];
        }


        public int survivingPieceSquare(
                int index
        ) {

            requireBoundaryIndex(
                    index
            );


            return survivingSquares[index];
        }


        public boolean survivingPieceIsWhite(
                int index
        ) {

            requireBoundaryIndex(
                    index
            );


            return survivingWhites[index];
        }


        public boolean boundaryBlackToMove(
                int index
        ) {

            requireBoundaryIndex(
                    index
            );


            return boundaryBlackToMove[index];
        }


        private void addInClass(
                int state
        ) {

            ensureCapacity();


            states[size] =
                    state;

            boundaries[size] =
                    false;

            boundaryWhiteKings[size] =
                    -1;

            boundaryBlackKings[size] =
                    -1;

            survivingTypes[size] =
                    null;

            survivingSquares[size] =
                    -1;

            survivingWhites[size] =
                    false;

            boundaryBlackToMove[size] =
                    false;


            size++;
        }


        private void addBoundary(
                int whiteKing,
                int blackKing,
                PieceType survivingType,
                int survivingSquare,
                boolean survivingWhite,
                boolean blackToMove
        ) {

            ensureCapacity();


            states[size] =
                    -1;

            boundaries[size] =
                    true;

            boundaryWhiteKings[size] =
                    whiteKing;

            boundaryBlackKings[size] =
                    blackKing;

            survivingTypes[size] =
                    survivingType;

            survivingSquares[size] =
                    survivingSquare;

            survivingWhites[size] =
                    survivingWhite;

            boundaryBlackToMove[size] =
                    blackToMove;


            size++;
        }


        private void ensureCapacity() {

            if (size
                    < states.length) {

                return;
            }


            int next =
                    states.length * 2;


            states =
                    Arrays.copyOf(
                            states,
                            next
                    );

            boundaries =
                    Arrays.copyOf(
                            boundaries,
                            next
                    );

            boundaryWhiteKings =
                    Arrays.copyOf(
                            boundaryWhiteKings,
                            next
                    );

            boundaryBlackKings =
                    Arrays.copyOf(
                            boundaryBlackKings,
                            next
                    );

            survivingTypes =
                    Arrays.copyOf(
                            survivingTypes,
                            next
                    );

            survivingSquares =
                    Arrays.copyOf(
                            survivingSquares,
                            next
                    );

            survivingWhites =
                    Arrays.copyOf(
                            survivingWhites,
                            next
                    );

            boundaryBlackToMove =
                    Arrays.copyOf(
                            boundaryBlackToMove,
                            next
                    );
        }


        private void requireIndex(
                int index
        ) {

            if (index < 0
                    || index >= size) {

                throw new IndexOutOfBoundsException(
                        "Successor index: "
                                + index
                                + ", size: "
                                + size
                );
            }
        }


        private void requireBoundaryIndex(
                int index
        ) {

            requireIndex(
                    index
            );


            if (!boundaries[index]) {

                throw new IllegalStateException(
                        "Successor "
                                + index
                                + " is not a three-piece boundary."
                );
            }
        }
    }


    public static int generateLegalSuccessors(
            int state,
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
                state,
                material,
                sameSideOwnerIsWhite
        )) {

            return 0;
        }


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


        boolean movingWhite =
                !blackToMove;


        generateKing(
                whiteKing,
                blackKing,
                first,
                second,
                material,
                sameSideOwnerIsWhite,
                movingWhite,
                blackToMove,
                output
        );


        if (FourPieceGenericPrimitiveRules.firstExtraIsWhite(
                material,
                sameSideOwnerIsWhite
        )
                == movingWhite) {

            generateExtraPiece(
                    0,
                    whiteKing,
                    blackKing,
                    first,
                    second,
                    material,
                    sameSideOwnerIsWhite,
                    movingWhite,
                    blackToMove,
                    output
            );
        }


        if (FourPieceGenericPrimitiveRules.secondExtraIsWhite(
                material,
                sameSideOwnerIsWhite
        )
                == movingWhite) {

            generateExtraPiece(
                    1,
                    whiteKing,
                    blackKing,
                    first,
                    second,
                    material,
                    sameSideOwnerIsWhite,
                    movingWhite,
                    blackToMove,
                    output
            );
        }


        return output.size();
    }


    private static void generateKing(
            int whiteKing,
            int blackKing,
            int first,
            int second,
            FourPieceMaterialClass material,
            boolean sameSideOwnerIsWhite,
            boolean movingWhite,
            boolean blackToMove,
            Buffer output
    ) {

        int from =
                movingWhite
                        ? whiteKing
                        : blackKing;


        int file =
                from & 7;

        int rank =
                from >>> 3;


        boolean firstWhite =
                FourPieceGenericPrimitiveRules.firstExtraIsWhite(
                        material,
                        sameSideOwnerIsWhite
                );

        boolean secondWhite =
                FourPieceGenericPrimitiveRules.secondExtraIsWhite(
                        material,
                        sameSideOwnerIsWhite
                );


        for (int i = 0;
             i < 8;
             i++) {

            int nextFile =
                    file
                            + KING_DF[i];

            int nextRank =
                    rank
                            + KING_DR[i];


            if (!inside(
                    nextFile,
                    nextRank
            )) {

                continue;
            }


            int to =
                    nextRank * 8
                            + nextFile;


            int enemyKing =
                    movingWhite
                            ? blackKing
                            : whiteKing;


            if (to == enemyKing
                    || adjacent(
                    to,
                    enemyKing
            )) {

                continue;
            }


            /*
             * Own extra pieces occupy the destination.
             */
            if (to == first
                    && firstWhite
                    == movingWhite) {

                continue;
            }


            if (to == second
                    && secondWhite
                    == movingWhite) {

                continue;
            }


            boolean capturesFirst =
                    to == first
                            && firstWhite
                            != movingWhite;

            boolean capturesSecond =
                    to == second
                            && secondWhite
                            != movingWhite;


            int nextWhiteKing =
                    movingWhite
                            ? to
                            : whiteKing;

            int nextBlackKing =
                    movingWhite
                            ? blackKing
                            : to;


            if (capturesFirst) {

                addBoundaryAfterCapture(
                        nextWhiteKing,
                        nextBlackKing,
                        material.secondType(),
                        second,
                        secondWhite,
                        !blackToMove,
                        output
                );

                continue;
            }


            if (capturesSecond) {

                addBoundaryAfterCapture(
                        nextWhiteKing,
                        nextBlackKing,
                        material.firstType(),
                        first,
                        firstWhite,
                        !blackToMove,
                        output
                );

                continue;
            }


            addInClassIfLegal(
                    nextWhiteKing,
                    nextBlackKing,
                    first,
                    second,
                    !blackToMove,
                    material,
                    sameSideOwnerIsWhite,
                    output
            );
        }
    }


    private static void generateExtraPiece(
            int extraIndex,
            int whiteKing,
            int blackKing,
            int first,
            int second,
            FourPieceMaterialClass material,
            boolean sameSideOwnerIsWhite,
            boolean movingWhite,
            boolean blackToMove,
            Buffer output
    ) {

        PieceType type =
                extraIndex == 0
                        ? material.firstType()
                        : material.secondType();


        switch (type) {

            case QUEEN ->
                    generateSlider(
                            extraIndex,
                            QUEEN_DIRS,
                            whiteKing,
                            blackKing,
                            first,
                            second,
                            material,
                            sameSideOwnerIsWhite,
                            movingWhite,
                            blackToMove,
                            output
                    );

            case ROOK ->
                    generateSlider(
                            extraIndex,
                            ROOK_DIRS,
                            whiteKing,
                            blackKing,
                            first,
                            second,
                            material,
                            sameSideOwnerIsWhite,
                            movingWhite,
                            blackToMove,
                            output
                    );

            case BISHOP ->
                    generateSlider(
                            extraIndex,
                            BISHOP_DIRS,
                            whiteKing,
                            blackKing,
                            first,
                            second,
                            material,
                            sameSideOwnerIsWhite,
                            movingWhite,
                            blackToMove,
                            output
                    );

            case KNIGHT ->
                    generateKnight(
                            extraIndex,
                            whiteKing,
                            blackKing,
                            first,
                            second,
                            material,
                            sameSideOwnerIsWhite,
                            movingWhite,
                            blackToMove,
                            output
                    );

            case PAWN ->
                    throw new IllegalArgumentException(
                            "Pawn move generation is not part of Tier 0."
                    );

            case KING ->
                    throw new IllegalArgumentException(
                            "KING cannot be an extra four-piece material type."
                    );
        }
    }


    private static void generateSlider(
            int extraIndex,
            int[][] directions,
            int whiteKing,
            int blackKing,
            int first,
            int second,
            FourPieceMaterialClass material,
            boolean sameSideOwnerIsWhite,
            boolean movingWhite,
            boolean blackToMove,
            Buffer output
    ) {

        int from =
                extraIndex == 0
                        ? first
                        : second;


        int file =
                from & 7;

        int rank =
                from >>> 3;


        for (int[] direction :
                directions) {

            int nextFile =
                    file
                            + direction[0];

            int nextRank =
                    rank
                            + direction[1];


            while (inside(
                    nextFile,
                    nextRank
            )) {

                int to =
                        nextRank * 8
                                + nextFile;


                if (to == whiteKing
                        || to == blackKing) {

                    break;
                }


                int otherIndex =
                        extraIndex == 0
                                ? 1
                                : 0;

                int otherSquare =
                        otherIndex == 0
                                ? first
                                : second;


                if (to == otherSquare) {

                    boolean otherWhite =
                            extraIsWhite(
                                    otherIndex,
                                    material,
                                    sameSideOwnerIsWhite
                            );


                    if (otherWhite
                            != movingWhite) {

                        addExtraCaptureBoundary(
                                extraIndex,
                                to,
                                whiteKing,
                                blackKing,
                                material,
                                movingWhite,
                                !blackToMove,
                                output
                        );
                    }


                    break;
                }


                int nextFirst =
                        extraIndex == 0
                                ? to
                                : first;

                int nextSecond =
                        extraIndex == 1
                                ? to
                                : second;


                addInClassIfLegal(
                        whiteKing,
                        blackKing,
                        nextFirst,
                        nextSecond,
                        !blackToMove,
                        material,
                        sameSideOwnerIsWhite,
                        output
                );


                nextFile +=
                        direction[0];

                nextRank +=
                        direction[1];
            }
        }
    }


    private static void generateKnight(
            int extraIndex,
            int whiteKing,
            int blackKing,
            int first,
            int second,
            FourPieceMaterialClass material,
            boolean sameSideOwnerIsWhite,
            boolean movingWhite,
            boolean blackToMove,
            Buffer output
    ) {

        int from =
                extraIndex == 0
                        ? first
                        : second;


        int file =
                from & 7;

        int rank =
                from >>> 3;


        for (int i = 0;
             i < KNIGHT_DF.length;
             i++) {

            int nextFile =
                    file
                            + KNIGHT_DF[i];

            int nextRank =
                    rank
                            + KNIGHT_DR[i];


            if (!inside(
                    nextFile,
                    nextRank
            )) {

                continue;
            }


            int to =
                    nextRank * 8
                            + nextFile;


            if (to == whiteKing
                    || to == blackKing) {

                continue;
            }


            int otherIndex =
                    extraIndex == 0
                            ? 1
                            : 0;

            int otherSquare =
                    otherIndex == 0
                            ? first
                            : second;


            if (to == otherSquare) {

                boolean otherWhite =
                        extraIsWhite(
                                otherIndex,
                                material,
                                sameSideOwnerIsWhite
                        );


                if (otherWhite
                        != movingWhite) {

                    addExtraCaptureBoundary(
                            extraIndex,
                            to,
                            whiteKing,
                            blackKing,
                            material,
                            movingWhite,
                            !blackToMove,
                            output
                    );
                }


                continue;
            }


            int nextFirst =
                    extraIndex == 0
                            ? to
                            : first;

            int nextSecond =
                    extraIndex == 1
                            ? to
                            : second;


            addInClassIfLegal(
                    whiteKing,
                    blackKing,
                    nextFirst,
                    nextSecond,
                    !blackToMove,
                    material,
                    sameSideOwnerIsWhite,
                    output
            );
        }
    }


    /**
     * One extra piece captures the opposing extra piece.
     *
     * The moving piece itself is therefore the surviving three-piece
     * non-king piece and now occupies the capture square.
     */
    private static void addExtraCaptureBoundary(
            int movingExtraIndex,
            int destination,
            int whiteKing,
            int blackKing,
            FourPieceMaterialClass material,
            boolean movingWhite,
            boolean nextBlackToMove,
            Buffer output
    ) {

        PieceType survivingType =
                movingExtraIndex == 0
                        ? material.firstType()
                        : material.secondType();


        addBoundaryAfterCapture(
                whiteKing,
                blackKing,
                survivingType,
                destination,
                movingWhite,
                nextBlackToMove,
                output
        );
    }


    private static void addBoundaryAfterCapture(
            int whiteKing,
            int blackKing,
            PieceType survivingType,
            int survivingSquare,
            boolean survivingWhite,
            boolean nextBlackToMove,
            Buffer output
    ) {

        if (!threePieceStructurallyLegal(
                whiteKing,
                blackKing,
                survivingType,
                survivingSquare,
                survivingWhite,
                nextBlackToMove
        )) {

            return;
        }


        output.addBoundary(
                whiteKing,
                blackKing,
                survivingType,
                survivingSquare,
                survivingWhite,
                nextBlackToMove
        );
    }


    /**
     * Structural legality test for the resulting three-piece boundary.
     *
     * Exactly the same chess invariant used by the four-piece primitive
     * rules applies:
     *
     *     the side that just moved may not have left its own king in check.
     */
    private static boolean threePieceStructurallyLegal(
            int whiteKing,
            int blackKing,
            PieceType survivingType,
            int survivingSquare,
            boolean survivingWhite,
            boolean blackToMove
    ) {

        if (whiteKing == blackKing
                || whiteKing == survivingSquare
                || blackKing == survivingSquare) {

            return false;
        }


        if (adjacent(
                whiteKing,
                blackKing
        )) {

            return false;
        }


        boolean justMovedWhite =
                blackToMove;


        int justMovedKing =
                justMovedWhite
                        ? whiteKing
                        : blackKing;

        boolean attackingWhite =
                !justMovedWhite;

        int attackingKing =
                attackingWhite
                        ? whiteKing
                        : blackKing;


        if (adjacent(
                attackingKing,
                justMovedKing
        )) {

            return false;
        }


        if (survivingWhite
                != attackingWhite) {

            return true;
        }


        return !FourPieceGenericPrimitiveRules.pieceAttacks(
                survivingType,
                survivingSquare,
                justMovedKing,
                whiteKing,
                blackKing,
                survivingSquare,
                -1
        );
    }


    private static void addInClassIfLegal(
            int whiteKing,
            int blackKing,
            int first,
            int second,
            boolean blackToMove,
            FourPieceMaterialClass material,
            boolean sameSideOwnerIsWhite,
            Buffer output
    ) {

        int state =
                FourPieceGenericPrimitiveState.encode(
                        whiteKing,
                        blackKing,
                        first,
                        second,
                        blackToMove
                );


        /*
         * Same-side identical pieces may need their two slots exchanged
         * after one of them moves.
         */
        state =
                FourPieceGenericPrimitiveState.canonicalize(
                        state,
                        material
                );


        if (!FourPieceGenericPrimitiveRules.isStructurallyLegal(
                state,
                material,
                sameSideOwnerIsWhite
        )) {

            return;
        }


        output.addInClass(
                state
        );
    }


    private static boolean extraIsWhite(
            int index,
            FourPieceMaterialClass material,
            boolean sameSideOwnerIsWhite
    ) {

        return index == 0
                ? FourPieceGenericPrimitiveRules.firstExtraIsWhite(
                material,
                sameSideOwnerIsWhite
        )
                : FourPieceGenericPrimitiveRules.secondExtraIsWhite(
                material,
                sameSideOwnerIsWhite
        );
    }


    private static boolean adjacent(
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
                    "Generic primitive move generation currently supports only Tier-0 pawnless material: "
                            + material.displayName()
            );
        }
    }
}
package main.java.chess.endgame;

import java.util.Arrays;

/**
 * Milestone 35.
 *
 * Primitive SAME-CLASS predecessor generator for KQPK.
 *
 * Boundary transitions are intentionally excluded:
 *
 *     KQPK -> KQK / KPK
 *     KQPK -> Tier-0 promotion
 *
 * Those are dependency seeds for retrograde, not in-class predecessors.
 *
 * Strategy:
 *   1. Reverse the geometry of the piece that could have moved last.
 *   2. Construct each candidate KQPK parent.
 *   3. Require primitive structural legality.
 *   4. Confirm the candidate by running the already-verified M33 forward
 *      primitive generator and checking that it reaches the requested child.
 *
 * This gives us an exact predecessor implementation while reusing the proven
 * forward legality semantics. A later optimization can inline more of the
 * validation if profiling shows predecessor generation is the bottleneck.
 */
public final class FourPieceTierOneKqpkPrimitivePredecessorGenerator {

    private static final int[] KING_DF =
            {-1, -1, -1, 0, 0, 1, 1, 1};

    private static final int[] KING_DR =
            {-1, 0, 1, -1, 1, -1, 0, 1};

    private static final int[][] QUEEN_DIRS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };


    private FourPieceTierOneKqpkPrimitivePredecessorGenerator() {
    }


    public static final class Buffer {

        private int[] states;
        private int size;


        public Buffer() {
            this(64);
        }


        public Buffer(int capacity) {

            states =
                    new int[Math.max(
                            16,
                            capacity
                    )];
        }


        public void clear() {
            size = 0;
        }


        public int size() {
            return size;
        }


        public int state(int index) {
            return states[index];
        }


        private void addUnique(
                int state
        ) {

            for (int i = 0;
                 i < size;
                 i++) {

                if (states[i] == state) {
                    return;
                }
            }


            if (size == states.length) {

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


    /**
     * Generate all legal SAME-CLASS KQPK parents that have {@code child} as
     * one of their legal M33 forward successors.
     */
    public static int generatePredecessors(
            int child,
            boolean strongIsWhite,
            Buffer output,
            FourPieceTierOneKqpkPrimitiveMoveGenerator.Buffer forwardBuffer
    ) {

        if (output == null) {

            throw new IllegalArgumentException(
                    "Predecessor output buffer cannot be null."
            );
        }


        if (forwardBuffer == null) {

            throw new IllegalArgumentException(
                    "Forward validation buffer cannot be null."
            );
        }


        output.clear();


        if (!FourPieceTierOneKqpkPrimitiveMoveGenerator
                .isStructurallyLegal(
                        child,
                        strongIsWhite
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

        int queen =
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
         * The parent has the opposite side to move.
         */
        boolean parentBlackToMove =
                !childBlackToMove;

        boolean previousMoverIsWhite =
                childBlackToMove;

        boolean previousMoverIsStrong =
                previousMoverIsWhite
                        == strongIsWhite;


        if (previousMoverIsStrong) {

            int strongKing =
                    strongIsWhite
                            ? wk
                            : bk;


            reverseKing(
                    child,
                    wk,
                    bk,
                    queen,
                    pawn,
                    strongKing,
                    strongIsWhite,
                    parentBlackToMove,
                    output,
                    forwardBuffer
            );


            reverseQueen(
                    child,
                    wk,
                    bk,
                    queen,
                    pawn,
                    strongIsWhite,
                    parentBlackToMove,
                    output,
                    forwardBuffer
            );


            reversePawn(
                    child,
                    wk,
                    bk,
                    queen,
                    pawn,
                    strongIsWhite,
                    parentBlackToMove,
                    output,
                    forwardBuffer
            );

        } else {

            int weakKing =
                    strongIsWhite
                            ? bk
                            : wk;


            reverseKing(
                    child,
                    wk,
                    bk,
                    queen,
                    pawn,
                    weakKing,
                    strongIsWhite,
                    parentBlackToMove,
                    output,
                    forwardBuffer
            );
        }


        return output.size();
    }


    public static int generatePredecessors(
            int child,
            boolean strongIsWhite,
            Buffer output
    ) {

        return generatePredecessors(
                child,
                strongIsWhite,
                output,
                new FourPieceTierOneKqpkPrimitiveMoveGenerator.Buffer(
                        64
                )
        );
    }


    private static void reverseKing(
            int child,
            int wk,
            int bk,
            int queen,
            int pawn,
            int currentKingSquare,
            boolean strongIsWhite,
            boolean parentBlackToMove,
            Buffer output,
            FourPieceTierOneKqpkPrimitiveMoveGenerator.Buffer forwardBuffer
    ) {

        int file =
                currentKingSquare & 7;

        int rank =
                currentKingSquare >>> 3;


        for (int i = 0;
             i < 8;
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


            if (occupied(
                    previousSquare,
                    wk,
                    bk,
                    queen,
                    pawn
            )) {

                continue;
            }


            int parentWk =
                    wk;

            int parentBk =
                    bk;


            if (currentKingSquare == wk) {

                parentWk =
                        previousSquare;

            } else if (currentKingSquare == bk) {

                parentBk =
                        previousSquare;

            } else {

                throw new IllegalStateException(
                        "Reverse KQPK king source is not a king."
                );
            }


            validateCandidate(
                    child,
                    parentWk,
                    parentBk,
                    queen,
                    pawn,
                    parentBlackToMove,
                    strongIsWhite,
                    output,
                    forwardBuffer
            );
        }
    }


    private static void reverseQueen(
            int child,
            int wk,
            int bk,
            int queen,
            int pawn,
            boolean strongIsWhite,
            boolean parentBlackToMove,
            Buffer output,
            FourPieceTierOneKqpkPrimitiveMoveGenerator.Buffer forwardBuffer
    ) {

        int file =
                queen & 7;

        int rank =
                queen >>> 3;


        for (int[] direction :
                QUEEN_DIRS) {

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
                        strongIsWhite,
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


    private static void reversePawn(
            int child,
            int wk,
            int bk,
            int queen,
            int pawn,
            boolean strongIsWhite,
            boolean parentBlackToMove,
            Buffer output,
            FourPieceTierOneKqpkPrimitiveMoveGenerator.Buffer forwardBuffer
    ) {

        int file =
                pawn & 7;

        int rank =
                pawn >>> 3;

        int direction =
                strongIsWhite
                        ? 1
                        : -1;


        /*
         * Reverse a normal one-square push.
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
                    queen,
                    pawn
            )) {

                validateCandidate(
                        child,
                        wk,
                        bk,
                        queen,
                        previousPawn,
                        parentBlackToMove,
                        strongIsWhite,
                        output,
                        forwardBuffer
                );
            }
        }


        /*
         * Reverse a legal starting-rank double push:
         *
         * white: rank 2 -> rank 4
         * black: rank 7 -> rank 5
         */
        int doubleDestinationRank =
                strongIsWhite
                        ? 3
                        : 4;

        int startRank =
                strongIsWhite
                        ? 1
                        : 6;


        if (rank == doubleDestinationRank) {

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
                    queen,
                    pawn
            )
                    && !occupied(
                    intermediate,
                    wk,
                    bk,
                    queen,
                    pawn
            )) {

                validateCandidate(
                        child,
                        wk,
                        bk,
                        queen,
                        previousPawn,
                        parentBlackToMove,
                        strongIsWhite,
                        output,
                        forwardBuffer
                );
            }
        }


        /*
         * There is no reverse pawn capture inside same-side KQPK because the
         * strong pawn has no weak non-king material to capture.
         *
         * Promotion predecessors are also deliberately excluded: promotion
         * children are Tier-0 boundary states, not KQPK children.
         */
    }


    private static void validateCandidate(
            int child,
            int wk,
            int bk,
            int queen,
            int pawn,
            boolean parentBlackToMove,
            boolean strongIsWhite,
            Buffer output,
            FourPieceTierOneKqpkPrimitiveMoveGenerator.Buffer forwardBuffer
    ) {

        if (!distinct(
                wk,
                bk,
                queen,
                pawn
        )) {

            return;
        }


        int parent =
                FourPieceGenericPrimitiveState.encode(
                        wk,
                        bk,
                        queen,
                        pawn,
                        parentBlackToMove
                );


        if (!FourPieceTierOneKqpkPrimitiveMoveGenerator
                .isStructurallyLegal(
                        parent,
                        strongIsWhite
                )) {

            return;
        }


        int count =
                FourPieceTierOneKqpkPrimitiveMoveGenerator
                        .generateLegalSuccessors(
                                parent,
                                strongIsWhite,
                                forwardBuffer
                        );


        for (int i = 0;
             i < count;
             i++) {

            if (forwardBuffer.boundaryType(
                    i
            ) != FourPieceTierOneKqpkPrimitiveMoveGenerator
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


    private static boolean occupied(
            int square,
            int wk,
            int bk,
            int queen,
            int pawn
    ) {

        return square == wk
                || square == bk
                || square == queen
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

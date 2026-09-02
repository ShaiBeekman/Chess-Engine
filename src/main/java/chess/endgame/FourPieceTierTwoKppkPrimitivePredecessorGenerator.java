package main.java.chess.endgame;

import java.util.Arrays;

/**
 * Milestone 57.
 *
 * SAME-CLASS predecessor generator for canonical SAME_SIDE KPPK.
 *
 * Boundary transitions are intentionally excluded:
 *
 *     - king capture of a pawn -> KPK
 *     - pawn promotion -> Tier-1 KQPK/KRPK/KBPK/KNPK
 *
 * Retrograde propagation only needs parents that remain KPPK.
 *
 * Strategy:
 *
 *   1. Determine which color moved immediately before {@code child}.
 *   2. Reverse that color's king move.
 *   3. If that color owns the pawns, reverse either pawn's single/double push.
 *   4. Canonicalize the two indistinguishable pawn slots.
 *   5. Require exact primitive structural legality.
 *   6. Forward-verify the candidate with the M56 successor generator.
 *
 * Forward verification deliberately remains the final authority. It keeps
 * reverse generation exact without duplicating every forward-legality rule.
 */
public final class FourPieceTierTwoKppkPrimitivePredecessorGenerator {

    private static final int[] KING_DF = {
            -1, -1, -1, 0, 0, 1, 1, 1
    };

    private static final int[] KING_DR = {
            -1, 0, 1, -1, 1, -1, 0, 1
    };


    private FourPieceTierTwoKppkPrimitivePredecessorGenerator() {
    }


    public static final class Buffer {

        private int[] states;

        private int size;


        public Buffer() {

            this(
                    32
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
            boolean pawnOwnerIsWhite,
            Buffer output,
            FourPieceTierTwoKppkPrimitiveMoveGenerator.Buffer forwardBuffer
    ) {

        if (!FourPieceTierTwoKppkPrimitiveMoveGenerator.supports(
                material
        )) {

            throw new IllegalArgumentException(
                    "Expected canonical SAME_SIDE KPPK material."
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

        if (!FourPieceTierTwoKppkPrimitiveMoveGenerator
                .isStructurallyLegal(
                        child,
                        material,
                        pawnOwnerIsWhite
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

        int firstPawn =
                FourPieceGenericPrimitiveState.firstExtra(
                        child
                );

        int secondPawn =
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

        int previousMoverKing =
                previousMoverWhite
                        ? wk
                        : bk;

        reverseKing(
                child,
                wk,
                bk,
                firstPawn,
                secondPawn,
                previousMoverKing,
                parentBlackToMove,
                material,
                pawnOwnerIsWhite,
                output,
                forwardBuffer
        );

        if (previousMoverWhite
                == pawnOwnerIsWhite) {

            /*
             * The pawn slots are canonicalized and therefore have no stable
             * physical identity. Either current slot may be the pawn that
             * moved immediately before the child.
             */
            reversePawn(
                    child,
                    wk,
                    bk,
                    firstPawn,
                    secondPawn,
                    firstPawn,
                    secondPawn,
                    parentBlackToMove,
                    material,
                    pawnOwnerIsWhite,
                    output,
                    forwardBuffer
            );

            reversePawn(
                    child,
                    wk,
                    bk,
                    firstPawn,
                    secondPawn,
                    secondPawn,
                    firstPawn,
                    parentBlackToMove,
                    material,
                    pawnOwnerIsWhite,
                    output,
                    forwardBuffer
            );
        }

        return output.size();
    }


    public static int generatePredecessors(
            int child,
            FourPieceMaterialClass material,
            boolean pawnOwnerIsWhite,
            Buffer output
    ) {

        return generatePredecessors(
                child,
                material,
                pawnOwnerIsWhite,
                output,
                new FourPieceTierTwoKppkPrimitiveMoveGenerator.Buffer(
                        32
                )
        );
    }


    private static void reverseKing(
            int child,
            int wk,
            int bk,
            int firstPawn,
            int secondPawn,
            int currentKingSquare,
            boolean parentBlackToMove,
            FourPieceMaterialClass material,
            boolean pawnOwnerIsWhite,
            Buffer output,
            FourPieceTierTwoKppkPrimitiveMoveGenerator.Buffer forwardBuffer
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
             * Same-class predecessors cannot reverse a capture. All four
             * pieces must exist in both parent and child.
             */
            if (occupied(
                    previousSquare,
                    wk,
                    bk,
                    firstPawn,
                    secondPawn
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
                        "Reverse KPPK king source is not a king."
                );
            }

            validateCandidate(
                    child,
                    parentWk,
                    parentBk,
                    firstPawn,
                    secondPawn,
                    parentBlackToMove,
                    material,
                    pawnOwnerIsWhite,
                    output,
                    forwardBuffer
            );
        }
    }


    private static void reversePawn(
            int child,
            int wk,
            int bk,
            int firstPawn,
            int secondPawn,
            int currentPawn,
            int otherPawn,
            boolean parentBlackToMove,
            FourPieceMaterialClass material,
            boolean pawnOwnerIsWhite,
            Buffer output,
            FourPieceTierTwoKppkPrimitiveMoveGenerator.Buffer forwardBuffer
    ) {

        int file =
                currentPawn & 7;

        int rank =
                currentPawn >>> 3;

        int direction =
                pawnOwnerIsWhite
                        ? 1
                        : -1;

        /*
         * Reverse an ordinary one-square non-promotion push.
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
                    firstPawn,
                    secondPawn
            )) {

                validatePawnCandidate(
                        child,
                        wk,
                        bk,
                        currentPawn,
                        otherPawn,
                        previousPawn,
                        parentBlackToMove,
                        material,
                        pawnOwnerIsWhite,
                        output,
                        forwardBuffer
                );
            }
        }

        /*
         * Reverse a legal starting-rank double push:
         *
         *     WHITE: rank 2 -> rank 4
         *     BLACK: rank 7 -> rank 5
         */
        int doubleDestinationRank =
                pawnOwnerIsWhite
                        ? 3
                        : 4;

        int startRank =
                pawnOwnerIsWhite
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
                    firstPawn,
                    secondPawn
            )
                    && !occupied(
                    intermediate,
                    wk,
                    bk,
                    firstPawn,
                    secondPawn
            )) {

                validatePawnCandidate(
                        child,
                        wk,
                        bk,
                        currentPawn,
                        otherPawn,
                        previousPawn,
                        parentBlackToMove,
                        material,
                        pawnOwnerIsWhite,
                        output,
                        forwardBuffer
                );
            }
        }

        /*
         * No reverse pawn capture exists in SAME_SIDE KPPK.
         * No reverse promotion exists in the same class either.
         */
    }


    private static void validatePawnCandidate(
            int child,
            int wk,
            int bk,
            int currentPawn,
            int otherPawn,
            int previousPawn,
            boolean parentBlackToMove,
            FourPieceMaterialClass material,
            boolean pawnOwnerIsWhite,
            Buffer output,
            FourPieceTierTwoKppkPrimitiveMoveGenerator.Buffer forwardBuffer
    ) {

        int parentFirst;
        int parentSecond;

        if (currentPawn
                == FourPieceGenericPrimitiveState.firstExtra(
                child
        )) {

            parentFirst =
                    previousPawn;

            parentSecond =
                    otherPawn;

        } else {

            parentFirst =
                    otherPawn;

            parentSecond =
                    previousPawn;
        }

        validateCandidate(
                child,
                wk,
                bk,
                parentFirst,
                parentSecond,
                parentBlackToMove,
                material,
                pawnOwnerIsWhite,
                output,
                forwardBuffer
        );
    }


    private static void validateCandidate(
            int child,
            int wk,
            int bk,
            int firstPawn,
            int secondPawn,
            boolean parentBlackToMove,
            FourPieceMaterialClass material,
            boolean pawnOwnerIsWhite,
            Buffer output,
            FourPieceTierTwoKppkPrimitiveMoveGenerator.Buffer forwardBuffer
    ) {

        if (!distinct(
                wk,
                bk,
                firstPawn,
                secondPawn
        )) {

            return;
        }

        int rawParent =
                FourPieceGenericPrimitiveState.encode(
                        wk,
                        bk,
                        firstPawn,
                        secondPawn,
                        parentBlackToMove
                );

        int parent =
                FourPieceGenericPrimitiveState.canonicalize(
                        rawParent,
                        material
                );

        if (!FourPieceTierTwoKppkPrimitiveMoveGenerator
                .isStructurallyLegal(
                        parent,
                        material,
                        pawnOwnerIsWhite
                )) {

            return;
        }

        int count =
                FourPieceTierTwoKppkPrimitiveMoveGenerator
                        .generateLegalSuccessors(
                                parent,
                                material,
                                pawnOwnerIsWhite,
                                forwardBuffer
                        );

        for (int i = 0;
             i < count;
             i++) {

            if (forwardBuffer.boundaryType(
                    i
            ) != FourPieceTierTwoKppkPrimitiveMoveGenerator
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
            int firstPawn,
            int secondPawn
    ) {

        return square == wk
                || square == bk
                || square == firstPawn
                || square == secondPawn;
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

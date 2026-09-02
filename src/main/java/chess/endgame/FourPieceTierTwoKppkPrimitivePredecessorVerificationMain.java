package main.java.chess.endgame;

import main.java.chess.model.PieceType;

import java.util.HashSet;
import java.util.Set;

/**
 * Milestone 57 directed verification for the KPPK same-class predecessor
 * generator.
 */
public final class FourPieceTierTwoKppkPrimitivePredecessorVerificationMain {

    private static final FourPieceMaterialClass KPPK =
            FourPieceMaterialClass.sameSide(
                    PieceType.PAWN,
                    PieceType.PAWN
            );


    private FourPieceTierTwoKppkPrimitivePredecessorVerificationMain() {
    }


    public static void main(
            String[] args
    ) {

        System.out.println(
                "KPPK Tier-2 predecessor directed gate"
        );
        System.out.println(
                "====================================="
        );

        verifyWhiteSinglePush();
        verifyWhiteDoublePush();
        verifyBlackSinglePush();
        verifyBlackDoublePush();
        verifyKingMove();
        verifyCanonicalSlotCrossing();
        verifyNoDuplicates();
        verifyBoundaryParentsExcluded();

        System.out.println();
        System.out.println(
                "KPPK TIER-2 PREDECESSOR DIRECTED GATE PASSED"
        );
        System.out.println(
                "NEXT: KPPK RANDOM COMPLETENESS / SOUNDNESS GATE"
        );
    }


    private static void verifyWhiteSinglePush() {

        int parent =
                state(
                        "a1",
                        "h8",
                        "c2",
                        "f4",
                        false
                );

        int child =
                requireChild(
                        parent,
                        true,
                        "c2",
                        "c3"
                );

        requirePredecessor(
                child,
                true,
                parent,
                "WHITE single push c2-c3"
        );
    }


    private static void verifyWhiteDoublePush() {

        int parent =
                state(
                        "a1",
                        "h8",
                        "c2",
                        "f4",
                        false
                );

        int child =
                requireChild(
                        parent,
                        true,
                        "c2",
                        "c4"
                );

        requirePredecessor(
                child,
                true,
                parent,
                "WHITE double push c2-c4"
        );
    }


    private static void verifyBlackSinglePush() {

        int parent =
                state(
                        "a1",
                        "h8",
                        "c5",
                        "f7",
                        true
                );

        int child =
                requireChild(
                        parent,
                        false,
                        "f7",
                        "f6"
                );

        requirePredecessor(
                child,
                false,
                parent,
                "BLACK single push f7-f6"
        );
    }


    private static void verifyBlackDoublePush() {

        int parent =
                state(
                        "a1",
                        "h8",
                        "c5",
                        "f7",
                        true
                );

        int child =
                requireChild(
                        parent,
                        false,
                        "f7",
                        "f5"
                );

        requirePredecessor(
                child,
                false,
                parent,
                "BLACK double push f7-f5"
        );
    }


    private static void verifyKingMove() {

        int parent =
                state(
                        "a1",
                        "h8",
                        "c3",
                        "f4",
                        false
                );

        int child =
                requireChild(
                        parent,
                        true,
                        "a1",
                        "b1"
                );

        requirePredecessor(
                child,
                true,
                parent,
                "WHITE king a1-b1"
        );
    }


    private static void verifyCanonicalSlotCrossing() {

        /*
         * c3 and c4 are ordered c3 < c4. Moving c3-c5 makes the moved pawn's
         * square greater than c4, so the canonical slots swap in the child.
         * The predecessor generator must still recover the original parent.
         */
        int parent =
                state(
                        "a1",
                        "h8",
                        "c3",
                        "c4",
                        false
                );

        /*
         * Use adjacent files: b3 -> b4 while c3 remains.
         * b4 sorts after c3, so canonical pawn slots cross in the child.
         */
        parent =
                state(
                        "a1",
                        "h8",
                        "b3",
                        "c3",
                        false
                );

        int child =
                requireChild(
                        parent,
                        true,
                        "b3",
                        "b4"
                );

        requirePredecessor(
                child,
                true,
                parent,
                "canonical pawn-slot crossing b3-b4"
        );
    }


    private static void verifyNoDuplicates() {

        int child =
                state(
                        "b1",
                        "h8",
                        "b4",
                        "c3",
                        true
                );

        FourPieceTierTwoKppkPrimitivePredecessorGenerator.Buffer predecessors =
                predecessors(
                        child,
                        true
                );

        Set<Integer> unique =
                new HashSet<>();

        for (int i = 0;
             i < predecessors.size();
             i++) {

            require(
                    unique.add(
                            predecessors.state(
                                    i
                            )
                    ),
                    "Duplicate predecessor returned."
            );
        }

        System.out.println();
        System.out.println(
                "Duplicate suppression: PASSED"
        );
    }


    private static void verifyBoundaryParentsExcluded() {

        /*
         * Promotion boundaries and king captures do not have same-class KPPK
         * children, so a predecessor call on an ordinary legal KPPK child must
         * return only parents whose forward edge is BOUNDARY_NONE.
         */
        int child =
                state(
                        "a1",
                        "h8",
                        "c6",
                        "f5",
                        true
                );

        FourPieceTierTwoKppkPrimitivePredecessorGenerator.Buffer predecessors =
                predecessors(
                        child,
                        true
                );

        FourPieceTierTwoKppkPrimitiveMoveGenerator.Buffer forward =
                new FourPieceTierTwoKppkPrimitiveMoveGenerator.Buffer(
                        32
                );

        for (int i = 0;
             i < predecessors.size();
             i++) {

            int parent =
                    predecessors.state(
                            i
                    );

            int count =
                    FourPieceTierTwoKppkPrimitiveMoveGenerator
                            .generateLegalSuccessors(
                                    parent,
                                    KPPK,
                                    true,
                                    forward
                            );

            boolean found =
                    false;

            for (int j = 0;
                 j < count;
                 j++) {

                if (forward.boundaryType(
                        j
                ) == FourPieceTierTwoKppkPrimitiveMoveGenerator.BOUNDARY_NONE
                        && forward.state(
                        j
                ) == child) {

                    found =
                            true;

                    break;
                }
            }

            require(
                    found,
                    "Predecessor lacks a same-class forward edge."
            );
        }

        System.out.println();
        System.out.println(
                "Boundary exclusion / forward verification: PASSED"
        );
    }


    private static int requireChild(
            int parent,
            boolean pawnOwnerIsWhite,
            String from,
            String to
    ) {

        FourPieceTierTwoKppkPrimitiveMoveGenerator.Buffer moves =
                new FourPieceTierTwoKppkPrimitiveMoveGenerator.Buffer(
                        32
                );

        int count =
                FourPieceTierTwoKppkPrimitiveMoveGenerator
                        .generateLegalSuccessors(
                                parent,
                                KPPK,
                                pawnOwnerIsWhite,
                                moves
                        );

        int fromSquare =
                sq(
                        from
                );

        int toSquare =
                sq(
                        to
                );

        for (int i = 0;
             i < count;
             i++) {

            if (moves.boundaryType(
                    i
            ) == FourPieceTierTwoKppkPrimitiveMoveGenerator.BOUNDARY_NONE
                    && moves.fromSquare(
                    i
            ) == fromSquare
                    && moves.toSquare(
                    i
            ) == toSquare) {

                return moves.state(
                        i
                );
            }
        }

        throw new IllegalStateException(
                "Missing expected same-class child "
                        + from
                        + "-"
                        + to
                        + "."
        );
    }


    private static void requirePredecessor(
            int child,
            boolean pawnOwnerIsWhite,
            int expectedParent,
            String label
    ) {

        FourPieceTierTwoKppkPrimitivePredecessorGenerator.Buffer predecessors =
                predecessors(
                        child,
                        pawnOwnerIsWhite
                );

        for (int i = 0;
             i < predecessors.size();
             i++) {

            if (predecessors.state(
                    i
            ) == expectedParent) {

                System.out.println();
                System.out.println(
                        label
                                + ": PASSED"
                );

                return;
            }
        }

        throw new IllegalStateException(
                "Missing expected predecessor for "
                        + label
                        + "."
        );
    }


    private static FourPieceTierTwoKppkPrimitivePredecessorGenerator.Buffer predecessors(
            int child,
            boolean pawnOwnerIsWhite
    ) {

        FourPieceTierTwoKppkPrimitivePredecessorGenerator.Buffer output =
                new FourPieceTierTwoKppkPrimitivePredecessorGenerator.Buffer(
                        32
                );

        FourPieceTierTwoKppkPrimitiveMoveGenerator.Buffer forward =
                new FourPieceTierTwoKppkPrimitiveMoveGenerator.Buffer(
                        32
                );

        FourPieceTierTwoKppkPrimitivePredecessorGenerator
                .generatePredecessors(
                        child,
                        KPPK,
                        pawnOwnerIsWhite,
                        output,
                        forward
                );

        return output;
    }


    private static int state(
            String wk,
            String bk,
            String firstPawn,
            String secondPawn,
            boolean blackToMove
    ) {

        int raw =
                FourPieceGenericPrimitiveState.encode(
                        sq(
                                wk
                        ),
                        sq(
                                bk
                        ),
                        sq(
                                firstPawn
                        ),
                        sq(
                                secondPawn
                        ),
                        blackToMove
                );

        return FourPieceGenericPrimitiveState.canonicalize(
                raw,
                KPPK
        );
    }


    private static int sq(
            String square
    ) {

        int file =
                square.charAt(
                        0
                ) - 'a';

        int rank =
                square.charAt(
                        1
                ) - '1';

        return rank * 8
                + file;
    }


    private static void require(
            boolean condition,
            String message
    ) {

        if (!condition) {

            throw new IllegalStateException(
                    message
            );
        }
    }
}

package main.java.chess.endgame;

import main.java.chess.model.PieceType;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Milestone 57 randomized completeness + soundness gate for canonical KPPK
 * same-class predecessor generation.
 *
 * For each pawn-owner orientation this performs two independent checks:
 *
 *   A. Parent -> child completeness:
 *      every sampled same-class forward edge must be recovered when the
 *      predecessor generator is asked for that child.
 *
 *   B. Child -> parent soundness:
 *      every returned predecessor must forward-generate the child through a
 *      BOUNDARY_NONE edge, and predecessor output must contain no duplicates.
 */
public final class FourPieceTierTwoKppkPrimitivePredecessorCompletenessMain {

    private static final FourPieceMaterialClass KPPK =
            FourPieceMaterialClass.sameSide(
                    PieceType.PAWN,
                    PieceType.PAWN
            );

    private static final int DEFAULT_PARENT_SAMPLES =
            100_000;

    private static final int DEFAULT_CHILD_SAMPLES =
            100_000;


    private FourPieceTierTwoKppkPrimitivePredecessorCompletenessMain() {
    }


    public static void main(
            String[] args
    ) {

        if (args.length > 2) {

            throw new IllegalArgumentException(
                    "Usage: FourPieceTierTwoKppkPrimitivePredecessorCompletenessMain "
                            + "[parent-samples] [child-samples]"
            );
        }

        int parentSamples =
                args.length >= 1
                        ? Integer.parseInt(
                        args[0]
                )
                        : DEFAULT_PARENT_SAMPLES;

        int childSamples =
                args.length >= 2
                        ? Integer.parseInt(
                        args[1]
                )
                        : DEFAULT_CHILD_SAMPLES;

        if (parentSamples < 1
                || childSamples < 1) {

            throw new IllegalArgumentException(
                    "Sample counts must be positive."
            );
        }

        System.out.println(
                "KPPK Tier-2 predecessor completeness / soundness gate"
        );
        System.out.println(
                "===================================================="
        );
        System.out.println(
                "Parent samples per orientation: "
                        + parentSamples
        );
        System.out.println(
                "Child samples per orientation: "
                        + childSamples
        );

        verifyOrientation(
                true,
                parentSamples,
                childSamples,
                0x4B50504B5701L
        );

        verifyOrientation(
                false,
                parentSamples,
                childSamples,
                0x4B50504B5702L
        );

        System.out.println();
        System.out.println(
                "KPPK TIER-2 PREDECESSOR COMPLETENESS / SOUNDNESS GATE PASSED"
        );
        System.out.println(
                "NEXT: KPPK DEPENDENCY-AWARE EXACT RETROGRADE BUILD / VALIDATION"
        );
    }


    private static void verifyOrientation(
            boolean pawnOwnerIsWhite,
            int parentSamples,
            int childSamples,
            long seed
    ) {

        Random random =
                new Random(
                        seed
                );

        FourPieceTierTwoKppkPrimitiveMoveGenerator.Buffer successors =
                new FourPieceTierTwoKppkPrimitiveMoveGenerator.Buffer(
                        32
                );

        FourPieceTierTwoKppkPrimitiveMoveGenerator.Buffer validationForward =
                new FourPieceTierTwoKppkPrimitiveMoveGenerator.Buffer(
                        32
                );

        FourPieceTierTwoKppkPrimitivePredecessorGenerator.Buffer predecessors =
                new FourPieceTierTwoKppkPrimitivePredecessorGenerator.Buffer(
                        32
                );

        long parentAttempts =
                0;

        int legalParents =
                0;

        long sameClassEdges =
                0;

        long boundaryEdges =
                0;

        long recoveredEdges =
                0;

        while (legalParents
                < parentSamples) {

            parentAttempts++;

            int parent =
                    randomState(
                            random
                    );

            if (!FourPieceTierTwoKppkPrimitiveMoveGenerator
                    .isStructurallyLegal(
                            parent,
                            KPPK,
                            pawnOwnerIsWhite
                    )) {

                continue;
            }

            int successorCount =
                    FourPieceTierTwoKppkPrimitiveMoveGenerator
                            .generateLegalSuccessors(
                                    parent,
                                    KPPK,
                                    pawnOwnerIsWhite,
                                    successors
                            );

            for (int i = 0;
                 i < successorCount;
                 i++) {

                if (successors.boundaryType(
                        i
                ) != FourPieceTierTwoKppkPrimitiveMoveGenerator
                        .BOUNDARY_NONE) {

                    boundaryEdges++;

                    continue;
                }

                sameClassEdges++;

                int child =
                        successors.state(
                                i
                        );

                int predecessorCount =
                        FourPieceTierTwoKppkPrimitivePredecessorGenerator
                                .generatePredecessors(
                                        child,
                                        KPPK,
                                        pawnOwnerIsWhite,
                                        predecessors,
                                        validationForward
                                );

                if (!contains(
                        predecessors,
                        predecessorCount,
                        parent
                )) {

                    throw new IllegalStateException(
                            "KPPK predecessor generator missed legal same-class edge."
                                    + "\nowner="
                                    + (pawnOwnerIsWhite
                                    ? "WHITE"
                                    : "BLACK")
                                    + "\nparent="
                                    + describe(
                                    parent
                            )
                                    + "\nchild="
                                    + describe(
                                    child
                            )
                    );
                }

                recoveredEdges++;
            }

            legalParents++;
        }

        int legalChildren =
                0;

        long predecessorsChecked =
                0;

        long duplicatePredecessors =
                0;

        while (legalChildren
                < childSamples) {

            int child =
                    randomState(
                            random
                    );

            if (!FourPieceTierTwoKppkPrimitiveMoveGenerator
                    .isStructurallyLegal(
                            child,
                            KPPK,
                            pawnOwnerIsWhite
                    )) {

                continue;
            }

            int predecessorCount =
                    FourPieceTierTwoKppkPrimitivePredecessorGenerator
                            .generatePredecessors(
                                    child,
                                    KPPK,
                                    pawnOwnerIsWhite,
                                    predecessors,
                                    validationForward
                            );

            Set<Integer> unique =
                    new HashSet<>(
                            Math.max(
                                    16,
                                    predecessorCount * 2
                            )
                    );

            for (int i = 0;
                 i < predecessorCount;
                 i++) {

                int parent =
                        predecessors.state(
                                i
                        );

                if (!unique.add(
                        parent
                )) {

                    duplicatePredecessors++;
                }

                int forwardCount =
                        FourPieceTierTwoKppkPrimitiveMoveGenerator
                                .generateLegalSuccessors(
                                        parent,
                                        KPPK,
                                        pawnOwnerIsWhite,
                                        validationForward
                                );

                if (!containsSameClassChild(
                        validationForward,
                        forwardCount,
                        child
                )) {

                    throw new IllegalStateException(
                            "KPPK predecessor generator returned unsound parent."
                                    + "\nowner="
                                    + (pawnOwnerIsWhite
                                    ? "WHITE"
                                    : "BLACK")
                                    + "\nparent="
                                    + describe(
                                    parent
                            )
                                    + "\nchild="
                                    + describe(
                                    child
                            )
                    );
                }

                predecessorsChecked++;
            }

            legalChildren++;
        }

        if (duplicatePredecessors
                != 0) {

            throw new IllegalStateException(
                    "Duplicate KPPK predecessors observed: "
                            + duplicatePredecessors
            );
        }

        if (sameClassEdges
                != recoveredEdges) {

            throw new IllegalStateException(
                    "KPPK completeness accounting mismatch."
            );
        }

        System.out.println();
        System.out.println(
                "KPPK — pawn owner "
                        + (pawnOwnerIsWhite
                        ? "WHITE"
                        : "BLACK")
        );
        System.out.println(
                "--------------------------------"
        );
        System.out.println(
                "  legal parents checked: "
                        + legalParents
        );
        System.out.println(
                "  parent attempts: "
                        + parentAttempts
        );
        System.out.println(
                "  same-class edges checked: "
                        + sameClassEdges
        );
        System.out.println(
                "  boundary edges skipped: "
                        + boundaryEdges
        );
        System.out.println(
                "  recovered same-class edges: "
                        + recoveredEdges
        );
        System.out.println(
                "  legal children checked: "
                        + legalChildren
        );
        System.out.println(
                "  predecessors soundness-checked: "
                        + predecessorsChecked
        );
        System.out.println(
                "  duplicate predecessors: "
                        + duplicatePredecessors
        );
        System.out.println(
                "  completeness + soundness: PASSED"
        );
    }


    private static int randomState(
            Random random
    ) {

        while (true) {

            int wk =
                    random.nextInt(
                            64
                    );

            int bk =
                    random.nextInt(
                            64
                    );

            int firstRank =
                    1 + random.nextInt(
                            6
                    );

            int firstPawn =
                    firstRank * 8
                            + random.nextInt(
                            8
                    );

            int secondRank =
                    1 + random.nextInt(
                            6
                    );

            int secondPawn =
                    secondRank * 8
                            + random.nextInt(
                            8
                    );

            if (!distinct(
                    wk,
                    bk,
                    firstPawn,
                    secondPawn
            )) {

                continue;
            }

            int raw =
                    FourPieceGenericPrimitiveState.encode(
                            wk,
                            bk,
                            firstPawn,
                            secondPawn,
                            random.nextBoolean()
                    );

            return FourPieceGenericPrimitiveState.canonicalize(
                    raw,
                    KPPK
            );
        }
    }


    private static boolean contains(
            FourPieceTierTwoKppkPrimitivePredecessorGenerator.Buffer buffer,
            int count,
            int state
    ) {

        for (int i = 0;
             i < count;
             i++) {

            if (buffer.state(
                    i
            ) == state) {

                return true;
            }
        }

        return false;
    }


    private static boolean containsSameClassChild(
            FourPieceTierTwoKppkPrimitiveMoveGenerator.Buffer buffer,
            int count,
            int child
    ) {

        for (int i = 0;
             i < count;
             i++) {

            if (buffer.boundaryType(
                    i
            ) == FourPieceTierTwoKppkPrimitiveMoveGenerator.BOUNDARY_NONE
                    && buffer.state(
                    i
            ) == child) {

                return true;
            }
        }

        return false;
    }


    private static String describe(
            int state
    ) {

        return "WK="
                + algebraic(
                FourPieceGenericPrimitiveState.whiteKing(
                        state
                )
        )
                + " BK="
                + algebraic(
                FourPieceGenericPrimitiveState.blackKing(
                        state
                )
        )
                + " P1="
                + algebraic(
                FourPieceGenericPrimitiveState.firstExtra(
                        state
                )
        )
                + " P2="
                + algebraic(
                FourPieceGenericPrimitiveState.secondExtra(
                        state
                )
        )
                + " stm="
                + (FourPieceGenericPrimitiveState.blackToMove(
                state
        )
                ? "BLACK"
                : "WHITE")
                + " ["
                + state
                + "]";
    }


    private static String algebraic(
            int square
    ) {

        return ""
                + (char) ('a' + (square & 7))
                + ((square >>> 3) + 1);
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
}

package main.java.chess.endgame;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;


/**
 * Bidirectional completeness cross-check for primitive KQRK reverse edges.
 *
 * This test is stronger than the earlier predecessor soundness test.
 *
 * For each sampled legal parent:
 *   1. generate every primitive legal in-class child
 *   2. record the forward edge parent -> child
 *
 * Then, for each encountered child:
 *   3. generate the child's complete primitive predecessor set
 *   4. require that the original parent is present
 *
 * It also independently compares the reverse generator against a brute-force
 * local inverse candidate enumeration for sampled child states.
 *
 * The result therefore checks both directions:
 *
 *   forward edge => reverse generator contains parent
 *   reverse candidate => forward generator contains child
 */
public final class KqrkPrimitivePredecessorCompletenessMain {

    private static final int DEFAULT_PARENTS = 100_000;
    private static final int DEFAULT_CHILDREN = 25_000;

    private static final int[] KING_DF =
            {-1, -1, -1, 0, 0, 1, 1, 1};

    private static final int[] KING_DR =
            {-1, 0, 1, -1, 1, -1, 0, 1};

    private static final int[][] ROOK_DIRS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };

    private static final int[][] QUEEN_DIRS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };


    public static void main(String[] args) {

        int parents =
                args.length > 0
                        ? Integer.parseInt(args[0])
                        : DEFAULT_PARENTS;

        int children =
                args.length > 1
                        ? Integer.parseInt(args[1])
                        : DEFAULT_CHILDREN;

        verify(
                parents,
                children,
                true,
                0x4B51524301L
        );

        verify(
                parents,
                children,
                false,
                0x4B51524302L
        );
    }


    private static void verify(
            int parentSamples,
            int childSamples,
            boolean strongIsWhite,
            long seed
    ) {
        Random random = new Random(seed);

        KqrkPrimitiveMoveGenerator.Buffer successors =
                new KqrkPrimitiveMoveGenerator.Buffer(64);

        KqrkPrimitivePredecessorGenerator.Buffer predecessors =
                new KqrkPrimitivePredecessorGenerator.Buffer(64);

        int checkedParents = 0;
        long checkedForwardEdges = 0;
        int missingReverseEdges = 0;

        // -----------------------------------------------------
        // A. Every sampled forward edge must be reversible.
        // -----------------------------------------------------

        while (checkedParents < parentSamples) {

            int parent =
                    random.nextInt(
                            KqrkPrimitiveState.STATE_COUNT
                    );

            if (!KqrkPrimitiveRules.isStructurallyLegal(
                    parent,
                    strongIsWhite
            )) {
                continue;
            }

            int successorCount =
                    KqrkPrimitiveMoveGenerator
                            .generateLegalSuccessors(
                                    parent,
                                    strongIsWhite,
                                    successors
                            );

            for (int i = 0;
                 i < successorCount;
                 i++) {

                if (successors.boundaryType(i)
                        != KqrkPrimitiveMoveGenerator.BOUNDARY_NONE) {
                    continue;
                }

                int child = successors.state(i);

                int predecessorCount =
                        KqrkPrimitivePredecessorGenerator
                                .generateLegalPredecessors(
                                        child,
                                        strongIsWhite,
                                        predecessors
                                );

                boolean found = false;

                for (int j = 0;
                     j < predecessorCount;
                     j++) {
                    if (predecessors.state(j) == parent) {
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    missingReverseEdges++;

                    if (missingReverseEdges <= 10) {
                        System.out.println();
                        System.out.println(
                                "MISSING REVERSE EDGE"
                        );
                        System.out.println(
                                "Strong: "
                                        + (strongIsWhite
                                        ? "WHITE"
                                        : "BLACK")
                        );
                        System.out.println(
                                "Parent: " + parent
                        );
                        System.out.println(
                                "Child: " + child
                        );
                    }
                }

                checkedForwardEdges++;
            }

            checkedParents++;

            if (checkedParents % 10_000 == 0) {
                System.out.println(
                        "  forward/reverse checked parents "
                                + checkedParents
                                + " / "
                                + parentSamples
                );
            }
        }

        // -----------------------------------------------------
        // B. Exact predecessor-set equality against an
        //    independent local inverse enumeration.
        // -----------------------------------------------------

        int checkedChildren = 0;
        int predecessorSetMismatches = 0;
        long comparedPredecessors = 0;

        while (checkedChildren < childSamples) {

            int child =
                    random.nextInt(
                            KqrkPrimitiveState.STATE_COUNT
                    );

            if (!KqrkPrimitiveRules.isStructurallyLegal(
                    child,
                    strongIsWhite
            )) {
                continue;
            }

            int generatedCount =
                    KqrkPrimitivePredecessorGenerator
                            .generateLegalPredecessors(
                                    child,
                                    strongIsWhite,
                                    predecessors
                            );

            Set<Integer> generated =
                    new HashSet<>(
                            Math.max(
                                    16,
                                    generatedCount * 2
                            )
                    );

            for (int i = 0;
                 i < generatedCount;
                 i++) {
                generated.add(
                        predecessors.state(i)
                );
            }

            Set<Integer> reference =
                    bruteForceLocalPredecessors(
                            child,
                            strongIsWhite
                    );

            comparedPredecessors +=
                    Math.max(
                            generated.size(),
                            reference.size()
                    );

            if (!generated.equals(reference)) {
                predecessorSetMismatches++;

                if (predecessorSetMismatches <= 10) {
                    System.out.println();
                    System.out.println(
                            "PREDECESSOR SET MISMATCH"
                    );
                    System.out.println(
                            "Strong: "
                                    + (strongIsWhite
                                    ? "WHITE"
                                    : "BLACK")
                    );
                    System.out.println(
                            "Child: " + child
                    );
                    System.out.println(
                            "Generated: "
                                    + generated.size()
                    );
                    System.out.println(
                            "Reference: "
                                    + reference.size()
                    );

                    Set<Integer> missing =
                            new HashSet<>(reference);
                    missing.removeAll(generated);

                    Set<Integer> extra =
                            new HashSet<>(generated);
                    extra.removeAll(reference);

                    System.out.println(
                            "Missing: "
                                    + limited(missing)
                    );
                    System.out.println(
                            "Extra: "
                                    + limited(extra)
                    );
                }
            }

            checkedChildren++;

            if (checkedChildren % 5_000 == 0) {
                System.out.println(
                        "  exact predecessor sets "
                                + checkedChildren
                                + " / "
                                + childSamples
                );
            }
        }

        System.out.println();
        System.out.println(
                "KQRK predecessor completeness — strong "
                        + (strongIsWhite
                        ? "WHITE"
                        : "BLACK")
        );
        System.out.println(
                "Legal parents checked: "
                        + checkedParents
        );
        System.out.println(
                "Forward in-class edges checked: "
                        + checkedForwardEdges
        );
        System.out.println(
                "Missing reverse edges: "
                        + missingReverseEdges
        );
        System.out.println(
                "Legal child predecessor sets checked: "
                        + checkedChildren
        );
        System.out.println(
                "Compared predecessor entries: "
                        + comparedPredecessors
        );
        System.out.println(
                "Predecessor-set mismatches: "
                        + predecessorSetMismatches
        );

        if (missingReverseEdges != 0
                || predecessorSetMismatches != 0) {
            throw new IllegalStateException(
                    "KQRK predecessor completeness verification failed."
            );
        }

        System.out.println(
                "KQRK PREDECESSOR COMPLETENESS PASSED"
        );
    }


    /**
     * Independent local inverse enumerator.
     *
     * It does NOT call KqrkPrimitivePredecessorGenerator.
     * It enumerates all geometrically possible previous origins for the
     * previous mover, constructs each candidate parent, and then uses the
     * already-verified forward generator solely to determine whether that
     * candidate really has child as a legal successor.
     */
    private static Set<Integer> bruteForceLocalPredecessors(
            int child,
            boolean strongIsWhite
    ) {
        Set<Integer> result =
                new HashSet<>();

        int wk = KqrkPrimitiveState.whiteKing(child);
        int bk = KqrkPrimitiveState.blackKing(child);
        int q = KqrkPrimitiveState.queen(child);
        int r = KqrkPrimitiveState.rook(child);

        boolean childBlackToMove =
                KqrkPrimitiveState.blackToMove(child);

        boolean previousMoverIsWhite =
                childBlackToMove;

        boolean previousMoverStrong =
                previousMoverIsWhite
                        == strongIsWhite;

        if (previousMoverStrong) {
            int king =
                    strongIsWhite ? wk : bk;

            enumerateKingOrigins(
                    result,
                    child,
                    wk, bk, q, r,
                    king,
                    true,
                    strongIsWhite,
                    childBlackToMove
            );

            enumerateSliderOrigins(
                    result,
                    child,
                    wk, bk, q, r,
                    q,
                    true,
                    strongIsWhite,
                    childBlackToMove
            );

            enumerateSliderOrigins(
                    result,
                    child,
                    wk, bk, q, r,
                    r,
                    false,
                    strongIsWhite,
                    childBlackToMove
            );
        } else {
            int king =
                    strongIsWhite ? bk : wk;

            enumerateKingOrigins(
                    result,
                    child,
                    wk, bk, q, r,
                    king,
                    false,
                    strongIsWhite,
                    childBlackToMove
            );
        }

        return result;
    }


    private static void enumerateKingOrigins(
            Set<Integer> result,
            int child,
            int wk,
            int bk,
            int q,
            int r,
            int destination,
            boolean strongKing,
            boolean strongIsWhite,
            boolean childBlackToMove
    ) {
        int file0 = destination & 7;
        int rank0 = destination >>> 3;

        for (int i = 0; i < 8; i++) {
            int file = file0 + KING_DF[i];
            int rank = rank0 + KING_DR[i];

            if (!inside(file, rank)) {
                continue;
            }

            int origin = rank * 8 + file;

            if (origin == wk
                    || origin == bk
                    || origin == q
                    || origin == r) {
                continue;
            }

            int pwk = wk;
            int pbk = bk;

            if (strongKing) {
                if (strongIsWhite) {
                    pwk = origin;
                } else {
                    pbk = origin;
                }
            } else {
                if (strongIsWhite) {
                    pbk = origin;
                } else {
                    pwk = origin;
                }
            }

            addCandidateIfForwardEdge(
                    result,
                    child,
                    pwk, pbk, q, r,
                    !childBlackToMove,
                    strongIsWhite
            );
        }
    }


    private static void enumerateSliderOrigins(
            Set<Integer> result,
            int child,
            int wk,
            int bk,
            int q,
            int r,
            int destination,
            boolean queen,
            boolean strongIsWhite,
            boolean childBlackToMove
    ) {
        int[][] dirs =
                queen ? QUEEN_DIRS : ROOK_DIRS;

        int file0 = destination & 7;
        int rank0 = destination >>> 3;

        for (int[] dir : dirs) {
            int file = file0 + dir[0];
            int rank = rank0 + dir[1];

            while (inside(file, rank)) {
                int origin = rank * 8 + file;

                if (origin == wk
                        || origin == bk
                        || origin == q
                        || origin == r) {
                    break;
                }

                int pq =
                        queen ? origin : q;

                int pr =
                        queen ? r : origin;

                addCandidateIfForwardEdge(
                        result,
                        child,
                        wk, bk, pq, pr,
                        !childBlackToMove,
                        strongIsWhite
                );

                file += dir[0];
                rank += dir[1];
            }
        }
    }


    private static void addCandidateIfForwardEdge(
            Set<Integer> result,
            int child,
            int wk,
            int bk,
            int q,
            int r,
            boolean parentBlackToMove,
            boolean strongIsWhite
    ) {
        int parent =
                KqrkPrimitiveState.encode(
                        wk,
                        bk,
                        q,
                        r,
                        parentBlackToMove
                );

        if (!KqrkPrimitiveRules.isStructurallyLegal(
                parent,
                strongIsWhite
        )) {
            return;
        }

        KqrkPrimitiveMoveGenerator.Buffer forward =
                new KqrkPrimitiveMoveGenerator.Buffer(64);

        int count =
                KqrkPrimitiveMoveGenerator
                        .generateLegalSuccessors(
                                parent,
                                strongIsWhite,
                                forward
                        );

        for (int i = 0; i < count; i++) {
            if (forward.boundaryType(i)
                    == KqrkPrimitiveMoveGenerator.BOUNDARY_NONE
                    && forward.state(i) == child) {
                result.add(parent);
                return;
            }
        }
    }


    private static String limited(
            Set<Integer> values
    ) {
        if (values.isEmpty()) {
            return "[]";
        }

        int[] data =
                new int[
                        Math.min(
                                10,
                                values.size()
                        )
                        ];

        int index = 0;

        for (int value : values) {
            if (index == data.length) {
                break;
            }

            data[index++] = value;
        }

        return Arrays.toString(data)
                + (values.size() > 10
                ? " ..."
                : "");
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

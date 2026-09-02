package main.java.chess.endgame;

import java.util.Random;

/**
 * Milestone 36.
 *
 * Large-sample completeness + performance gate for the primitive KQPK
 * predecessor generator.
 *
 * This deliberately tests a much larger edge population than M35 and times
 * reverse generation separately.
 *
 * Correctness pass:
 *   - sample legal KQPK parents;
 *   - generate every M33 same-class successor;
 *   - require M35 to recover the parent from every child.
 *
 * Performance pass:
 *   - sample legal KQPK child states;
 *   - generate all M35 predecessors;
 *   - consume every returned state in a checksum.
 *
 * Boundary successors are counted but excluded from predecessor completeness
 * because they leave KQPK and become dependency seeds in retrograde.
 *
 * Optional arguments:
 *
 *   arg 0 = legal parent samples per orientation
 *   arg 1 = legal child samples per orientation
 *
 * Defaults:
 *
 *   250,000 legal parents
 *   250,000 legal children
 */
public final class FourPieceTierOneKqpkPrimitivePredecessorCompletenessMain {

    private static final int DEFAULT_PARENT_SAMPLES =
            250_000;

    private static final int DEFAULT_CHILD_SAMPLES =
            250_000;


    private FourPieceTierOneKqpkPrimitivePredecessorCompletenessMain() {
    }


    public static void main(
            String[] args
    ) {

        int parentSamples =
                args.length > 0
                        ? Integer.parseInt(args[0])
                        : DEFAULT_PARENT_SAMPLES;

        int childSamples =
                args.length > 1
                        ? Integer.parseInt(args[1])
                        : DEFAULT_CHILD_SAMPLES;


        if (parentSamples < 1
                || childSamples < 1) {

            throw new IllegalArgumentException(
                    "Sample counts must be positive."
            );
        }


        runOrientation(
                true,
                parentSamples,
                childSamples,
                0x4B51504B3601L
        );

        runOrientation(
                false,
                parentSamples,
                childSamples,
                0x4B51504B3602L
        );


        System.out.println();

        System.out.println(
                "KQPK LARGE-SAMPLE PREDECESSOR GATE PASSED"
        );

        System.out.println(
                "NEXT: DEPENDENCY-AWARE KQPK RETROGRADE BUILDER"
        );
    }


    private static void runOrientation(
            boolean strongIsWhite,
            int parentSamples,
            int childSamples,
            long seed
    ) {

        Random random =
                new Random(seed);

        FourPieceTierOneKqpkPrimitiveMoveGenerator.Buffer successors =
                new FourPieceTierOneKqpkPrimitiveMoveGenerator.Buffer(64);

        FourPieceTierOneKqpkPrimitiveMoveGenerator.Buffer forwardValidation =
                new FourPieceTierOneKqpkPrimitiveMoveGenerator.Buffer(64);

        FourPieceTierOneKqpkPrimitivePredecessorGenerator.Buffer predecessors =
                new FourPieceTierOneKqpkPrimitivePredecessorGenerator.Buffer(64);


        long correctnessStarted =
                System.nanoTime();

        int legalParents =
                0;

        long parentAttempts =
                0;

        long sameClassEdges =
                0;

        long boundaryEdges =
                0;

        long recoveredEdges =
                0;


        while (legalParents < parentSamples) {

            parentAttempts++;


            int parent =
                    randomState(random);


            if (!FourPieceTierOneKqpkPrimitiveMoveGenerator
                    .isStructurallyLegal(
                            parent,
                            strongIsWhite
                    )) {

                continue;
            }


            int successorCount =
                    FourPieceTierOneKqpkPrimitiveMoveGenerator
                            .generateLegalSuccessors(
                                    parent,
                                    strongIsWhite,
                                    successors
                            );


            for (int i = 0;
                 i < successorCount;
                 i++) {

                if (successors.boundaryType(i)
                        != FourPieceTierOneKqpkPrimitiveMoveGenerator
                        .BOUNDARY_NONE) {

                    boundaryEdges++;

                    continue;
                }


                sameClassEdges++;


                int child =
                        successors.state(i);


                int predecessorCount =
                        FourPieceTierOneKqpkPrimitivePredecessorGenerator
                                .generatePredecessors(
                                        child,
                                        strongIsWhite,
                                        predecessors,
                                        forwardValidation
                                );


                if (!contains(
                        predecessors,
                        predecessorCount,
                        parent
                )) {

                    System.out.println();

                    System.out.println(
                            "LARGE-SAMPLE PREDECESSOR MISS"
                    );

                    System.out.println(
                            "  strong: "
                                    + colorName(strongIsWhite)
                    );

                    System.out.println(
                            "  parent: "
                                    + describe(parent)
                    );

                    System.out.println(
                            "  child:  "
                                    + describe(child)
                    );

                    System.out.println(
                            "  predecessor count: "
                                    + predecessorCount
                    );


                    throw new IllegalStateException(
                            "M36 found a missing KQPK predecessor."
                    );
                }


                recoveredEdges++;
            }


            legalParents++;
        }


        long correctnessElapsed =
                System.nanoTime()
                        - correctnessStarted;


        /*
         * Warm the predecessor path before timing it independently.
         */
        int warmupTarget =
                Math.min(
                        25_000,
                        childSamples
                );

        int warmed =
                0;

        long warmupChecksum =
                0;


        while (warmed < warmupTarget) {

            int child =
                    randomState(random);


            if (!FourPieceTierOneKqpkPrimitiveMoveGenerator
                    .isStructurallyLegal(
                            child,
                            strongIsWhite
                    )) {

                continue;
            }


            int count =
                    FourPieceTierOneKqpkPrimitivePredecessorGenerator
                            .generatePredecessors(
                                    child,
                                    strongIsWhite,
                                    predecessors,
                                    forwardValidation
                            );


            for (int i = 0;
                 i < count;
                 i++) {

                warmupChecksum +=
                        predecessors.state(i);
            }


            warmed++;
        }


        long performanceStarted =
                System.nanoTime();

        int legalChildren =
                0;

        long childAttempts =
                0;

        long returnedPredecessors =
                0;

        long checksum =
                0;


        while (legalChildren < childSamples) {

            childAttempts++;


            int child =
                    randomState(random);


            if (!FourPieceTierOneKqpkPrimitiveMoveGenerator
                    .isStructurallyLegal(
                            child,
                            strongIsWhite
                    )) {

                continue;
            }


            int count =
                    FourPieceTierOneKqpkPrimitivePredecessorGenerator
                            .generatePredecessors(
                                    child,
                                    strongIsWhite,
                                    predecessors,
                                    forwardValidation
                            );


            returnedPredecessors +=
                    count;


            for (int i = 0;
                 i < count;
                 i++) {

                checksum +=
                        predecessors.state(i) * 31L
                                + child * 17L
                                + i;
            }


            legalChildren++;
        }


        long performanceElapsed =
                System.nanoTime()
                        - performanceStarted;


        double correctnessSeconds =
                correctnessElapsed
                        / 1_000_000_000.0;

        double performanceSeconds =
                performanceElapsed
                        / 1_000_000_000.0;

        double childrenPerSecond =
                legalChildren
                        / performanceSeconds;

        double predecessorsPerSecond =
                returnedPredecessors
                        / performanceSeconds;

        double averagePredecessors =
                legalChildren == 0
                        ? 0.0
                        : (double) returnedPredecessors
                        / legalChildren;


        System.out.println();

        System.out.println(
                "KQPK predecessor large-sample gate — strong "
                        + colorName(strongIsWhite)
        );

        System.out.println(
                "===================================================="
        );

        System.out.println(
                "Completeness legal parents: "
                        + legalParents
        );

        System.out.println(
                "Completeness parent attempts: "
                        + parentAttempts
        );

        System.out.println(
                "Same-class edges checked: "
                        + sameClassEdges
        );

        System.out.println(
                "Recovered same-class edges: "
                        + recoveredEdges
        );

        System.out.println(
                "Boundary edges observed/excluded: "
                        + boundaryEdges
        );

        System.out.println(
                "Missing predecessors: 0"
        );

        System.out.printf(
                "Completeness elapsed: %.3f sec%n",
                correctnessSeconds
        );

        System.out.println();

        System.out.println(
                "Performance legal children: "
                        + legalChildren
        );

        System.out.println(
                "Performance child attempts: "
                        + childAttempts
        );

        System.out.println(
                "Returned predecessors: "
                        + returnedPredecessors
        );

        System.out.printf(
                "Average predecessors/child: %.3f%n",
                averagePredecessors
        );

        System.out.printf(
                "Performance elapsed: %.3f sec%n",
                performanceSeconds
        );

        System.out.printf(
                "Child throughput: %,.0f children/sec%n",
                childrenPerSecond
        );

        System.out.printf(
                "Predecessor throughput: %,.0f edges/sec%n",
                predecessorsPerSecond
        );

        System.out.println(
                "Checksum: "
                        + checksum
        );

        System.out.println(
                "Warmup checksum: "
                        + warmupChecksum
        );

        System.out.println(
                "PASSED"
        );
    }


    private static int randomState(
            Random random
    ) {

        while (true) {

            int wk =
                    random.nextInt(64);

            int bk =
                    random.nextInt(64);

            int queen =
                    random.nextInt(64);

            /*
             * KQPK in-class pawn squares are ranks 2 through 7 for White's
             * coordinate system: primitive rank indices 1..6.
             */
            int pawn =
                    (1 + random.nextInt(6)) * 8
                            + random.nextInt(8);


            if (!distinct(
                    wk,
                    bk,
                    queen,
                    pawn
            )) {

                continue;
            }


            return FourPieceGenericPrimitiveState.encode(
                    wk,
                    bk,
                    queen,
                    pawn,
                    random.nextBoolean()
            );
        }
    }


    private static boolean contains(
            FourPieceTierOneKqpkPrimitivePredecessorGenerator.Buffer buffer,
            int count,
            int target
    ) {

        for (int i = 0;
             i < count;
             i++) {

            if (buffer.state(i)
                    == target) {

                return true;
            }
        }


        return false;
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


    private static String colorName(
            boolean strongIsWhite
    ) {

        return strongIsWhite
                ? "WHITE"
                : "BLACK";
    }


    private static String describe(
            int state
    ) {

        return "WK="
                + algebraic(
                FourPieceGenericPrimitiveState.whiteKing(state)
        )
                + " BK="
                + algebraic(
                FourPieceGenericPrimitiveState.blackKing(state)
        )
                + " Q="
                + algebraic(
                FourPieceGenericPrimitiveState.firstExtra(state)
        )
                + " P="
                + algebraic(
                FourPieceGenericPrimitiveState.secondExtra(state)
        )
                + " stm="
                + (FourPieceGenericPrimitiveState.blackToMove(state)
                ? "BLACK"
                : "WHITE")
                + " ["
                + state
                + "]";
    }


    private static String algebraic(
            int square
    ) {

        int file =
                square & 7;

        int rank =
                square >>> 3;


        return String.valueOf(
                (char) ('a' + file)
        )
                + (rank + 1);
    }
}

package main.java.chess.endgame;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Milestone 35 verification.
 *
 * Completeness is tested in both directions:
 *
 *   forward -> predecessor
 *       Every sampled SAME-CLASS M33 edge parent -> child must be recovered
 *       by the M35 predecessor generator for that child.
 *
 *   predecessor -> forward
 *       Every predecessor returned for a sampled child must generate that
 *       child as an M33 SAME-CLASS successor.
 *
 * Boundary edges are excluded by design because they leave KQPK.
 */
public final class FourPieceTierOneKqpkPrimitivePredecessorVerificationMain {

    private static final int DEFAULT_PARENT_SAMPLES =
            50_000;

    private static final int DEFAULT_CHILD_SAMPLES =
            50_000;


    private FourPieceTierOneKqpkPrimitivePredecessorVerificationMain() {
    }


    public static void main(
            String[] args
    ) {

        int parentSamples =
                args.length > 0
                        ? Integer.parseInt(
                        args[0]
                )
                        : DEFAULT_PARENT_SAMPLES;

        int childSamples =
                args.length > 1
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


        verifyOrientation(
                true,
                parentSamples,
                childSamples,
                0x4B51504B3501L
        );

        verifyOrientation(
                false,
                parentSamples,
                childSamples,
                0x4B51504B3502L
        );


        System.out.println();

        System.out.println(
                "KQPK PRIMITIVE PREDECESSOR VERIFICATION PASSED"
        );

        System.out.println(
                "NEXT: LARGE-SAMPLE PREDECESSOR COMPLETENESS / PERFORMANCE GATE"
        );
    }


    private static void verifyOrientation(
            boolean strongIsWhite,
            int parentSamples,
            int childSamples,
            long seed
    ) {

        Random random =
                new Random(
                        seed
                );

        FourPieceTierOneKqpkPrimitiveMoveGenerator.Buffer forward =
                new FourPieceTierOneKqpkPrimitiveMoveGenerator.Buffer(
                        64
                );

        FourPieceTierOneKqpkPrimitiveMoveGenerator.Buffer validationForward =
                new FourPieceTierOneKqpkPrimitiveMoveGenerator.Buffer(
                        64
                );

        FourPieceTierOneKqpkPrimitivePredecessorGenerator.Buffer predecessors =
                new FourPieceTierOneKqpkPrimitivePredecessorGenerator.Buffer(
                        64
                );


        long attempts =
                0;

        long sameClassEdgesChecked =
                0;

        long boundaryEdgesObserved =
                0;

        int legalParentsChecked =
                0;


        /*
         * ---------------------------------------------------------
         * FORWARD -> PREDECESSOR COMPLETENESS
         * ---------------------------------------------------------
         */
        while (legalParentsChecked
                < parentSamples) {

            attempts++;


            int parent =
                    randomInClassState(
                            random
                    );


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
                                    forward
                            );


            for (int i = 0;
                 i < successorCount;
                 i++) {

                if (forward.boundaryType(
                        i
                ) != FourPieceTierOneKqpkPrimitiveMoveGenerator
                        .BOUNDARY_NONE) {

                    boundaryEdgesObserved++;

                    continue;
                }


                int child =
                        forward.state(
                                i
                        );


                int predecessorCount =
                        FourPieceTierOneKqpkPrimitivePredecessorGenerator
                                .generatePredecessors(
                                        child,
                                        strongIsWhite,
                                        predecessors,
                                        validationForward
                                );


                if (!contains(
                        predecessors,
                        predecessorCount,
                        parent
                )) {

                    printMissing(
                            parent,
                            child,
                            strongIsWhite,
                            predecessors,
                            predecessorCount
                    );

                    throw new IllegalStateException(
                            "KQPK predecessor generator missed a legal same-class edge."
                    );
                }


                sameClassEdgesChecked++;
            }


            legalParentsChecked++;
        }


        /*
         * ---------------------------------------------------------
         * PREDECESSOR -> FORWARD SOUNDNESS
         * ---------------------------------------------------------
         */
        int legalChildrenChecked =
                0;

        long predecessorsChecked =
                0;

        long duplicatePredecessors =
                0;


        while (legalChildrenChecked
                < childSamples) {

            int child =
                    randomInClassState(
                            random
                    );


            if (!FourPieceTierOneKqpkPrimitiveMoveGenerator
                    .isStructurallyLegal(
                            child,
                            strongIsWhite
                    )) {

                continue;
            }


            int predecessorCount =
                    FourPieceTierOneKqpkPrimitivePredecessorGenerator
                            .generatePredecessors(
                                    child,
                                    strongIsWhite,
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

                    throw new IllegalStateException(
                            "Duplicate KQPK predecessor: "
                                    + parent
                    );
                }


                int successorCount =
                        FourPieceTierOneKqpkPrimitiveMoveGenerator
                                .generateLegalSuccessors(
                                        parent,
                                        strongIsWhite,
                                        forward
                                );


                if (!containsSameClassChild(
                        forward,
                        successorCount,
                        child
                )) {

                    printUnsound(
                            parent,
                            child,
                            strongIsWhite
                    );

                    throw new IllegalStateException(
                            "KQPK predecessor generator returned an unsound parent."
                    );
                }


                predecessorsChecked++;
            }


            legalChildrenChecked++;
        }


        System.out.println();

        System.out.println(
                "KQPK predecessor verification — strong "
                        + (strongIsWhite
                        ? "WHITE"
                        : "BLACK")
        );

        System.out.println(
                "  Legal parents checked: "
                        + legalParentsChecked
        );

        System.out.println(
                "  Parent attempts: "
                        + attempts
        );

        System.out.println(
                "  Same-class forward edges checked: "
                        + sameClassEdgesChecked
        );

        System.out.println(
                "  Boundary edges observed/excluded: "
                        + boundaryEdgesObserved
        );

        System.out.println(
                "  Legal children checked: "
                        + legalChildrenChecked
        );

        System.out.println(
                "  Returned predecessors rechecked: "
                        + predecessorsChecked
        );

        System.out.println(
                "  Missing predecessors: 0"
        );

        System.out.println(
                "  Unsound predecessors: 0"
        );

        System.out.println(
                "  Duplicate predecessors: "
                        + duplicatePredecessors
        );

        System.out.println(
                "  PASSED"
        );
    }


    private static int randomInClassState(
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

            int queen =
                    random.nextInt(
                            64
                    );

            int pawnRank =
                    1 + random.nextInt(
                            6
                    );

            int pawn =
                    pawnRank * 8
                            + random.nextInt(
                            8
                    );


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
            FourPieceTierOneKqpkPrimitiveMoveGenerator.Buffer buffer,
            int count,
            int child
    ) {

        for (int i = 0;
             i < count;
             i++) {

            if (buffer.boundaryType(
                    i
            ) == FourPieceTierOneKqpkPrimitiveMoveGenerator
                    .BOUNDARY_NONE
                    && buffer.state(
                    i
            ) == child) {

                return true;
            }
        }


        return false;
    }


    private static void printMissing(
            int parent,
            int child,
            boolean strongIsWhite,
            FourPieceTierOneKqpkPrimitivePredecessorGenerator.Buffer predecessors,
            int predecessorCount
    ) {

        System.out.println();

        System.out.println(
                "MISSING KQPK PREDECESSOR"
        );

        System.out.println(
                "  strong: "
                        + (strongIsWhite
                        ? "WHITE"
                        : "BLACK")
        );

        System.out.println(
                "  parent: "
                        + describe(
                        parent
                )
        );

        System.out.println(
                "  child:  "
                        + describe(
                        child
                )
        );

        System.out.println(
                "  generated predecessor count: "
                        + predecessorCount
        );


        for (int i = 0;
             i < predecessorCount
                     && i < 20;
             i++) {

            System.out.println(
                    "    "
                            + describe(
                            predecessors.state(
                                    i
                            )
                    )
            );
        }
    }


    private static void printUnsound(
            int parent,
            int child,
            boolean strongIsWhite
    ) {

        System.out.println();

        System.out.println(
                "UNSOUND KQPK PREDECESSOR"
        );

        System.out.println(
                "  strong: "
                        + (strongIsWhite
                        ? "WHITE"
                        : "BLACK")
        );

        System.out.println(
                "  parent: "
                        + describe(
                        parent
                )
        );

        System.out.println(
                "  child:  "
                        + describe(
                        child
                )
        );
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
                + " Q="
                + algebraic(
                FourPieceGenericPrimitiveState.firstExtra(
                        state
                )
        )
                + " P="
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

        int file =
                square & 7;

        int rank =
                square >>> 3;


        return String.valueOf(
                (char) ('a' + file)
        )
                + (rank + 1);
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

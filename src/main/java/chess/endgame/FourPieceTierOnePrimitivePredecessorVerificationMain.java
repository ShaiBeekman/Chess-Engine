package main.java.chess.endgame;

import main.java.chess.model.PieceType;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;


/**
 * Milestone 43 verification.
 *
 * For every canonical Tier-1 family:
 *
 *   1. FORWARD -> PREDECESSOR COMPLETENESS
 *      Sample legal parents, enumerate every M42 same-class child, and require
 *      the generic predecessor generator to recover the original parent.
 *
 *   2. PREDECESSOR -> FORWARD SOUNDNESS
 *      Sample legal children, enumerate every predecessor, and require each
 *      predecessor to forward-generate that exact child as a same-class edge.
 *
 *   3. DUPLICATE FREEDOM
 *      Every predecessor list must contain unique states.
 *
 * KQPK also receives a direct regression check against the already-proven M35
 * predecessor generator.
 */
public final class FourPieceTierOnePrimitivePredecessorVerificationMain {

    private static final int PARENT_SAMPLES =
            20_000;

    private static final int CHILD_SAMPLES =
            20_000;


    private FourPieceTierOnePrimitivePredecessorVerificationMain() {
    }


    public static void main(
            String[] args
    ) {

        System.out.println(
                "Generic Tier-1 primitive predecessor verification"
        );

        System.out.println(
                "==============================================="
        );


        PieceType[] nonPawns = {
                PieceType.QUEEN,
                PieceType.ROOK,
                PieceType.BISHOP,
                PieceType.KNIGHT
        };


        long seed =
                0x4D34334CL;


        for (PieceType type :
                nonPawns) {

            FourPieceMaterialClass sameSide =
                    FourPieceMaterialClass.sameSide(
                            type,
                            PieceType.PAWN
                    );


            verifyMaterial(
                    sameSide,
                    true,
                    PARENT_SAMPLES,
                    CHILD_SAMPLES,
                    seed++
            );


            verifyMaterial(
                    sameSide,
                    false,
                    PARENT_SAMPLES,
                    CHILD_SAMPLES,
                    seed++
            );


            FourPieceMaterialClass split =
                    FourPieceMaterialClass.split(
                            type,
                            PieceType.PAWN
                    );


            verifyMaterial(
                    split,
                    true,
                    PARENT_SAMPLES,
                    CHILD_SAMPLES,
                    seed++
            );
        }


        verifyKqpkRegression(
                true,
                20_000,
                seed++
        );


        verifyKqpkRegression(
                false,
                20_000,
                seed
        );


        System.out.println();

        System.out.println(
                "GENERIC TIER-1 PREDECESSOR GATE PASSED"
        );

        System.out.println(
                "NEXT: GENERIC DEPENDENCY-AWARE TIER-1 RETROGRADE BUILDER"
        );
    }


    private static void verifyMaterial(
            FourPieceMaterialClass material,
            boolean sameSideOwnerIsWhite,
            int parentSamples,
            int childSamples,
            long seed
    ) {

        Random random =
                new Random(
                        seed
                );

        FourPieceTierOnePrimitiveMoveGenerator.Buffer successors =
                new FourPieceTierOnePrimitiveMoveGenerator.Buffer(
                        64
                );

        FourPieceTierOnePrimitiveMoveGenerator.Buffer validationForward =
                new FourPieceTierOnePrimitiveMoveGenerator.Buffer(
                        64
                );

        FourPieceTierOnePrimitivePredecessorGenerator.Buffer predecessors =
                new FourPieceTierOnePrimitivePredecessorGenerator.Buffer(
                        64
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


            if (!FourPieceTierOnePrimitiveMoveGenerator
                    .isStructurallyLegal(
                            parent,
                            material,
                            sameSideOwnerIsWhite
                    )) {

                continue;
            }


            int successorCount =
                    FourPieceTierOnePrimitiveMoveGenerator
                            .generateLegalSuccessors(
                                    parent,
                                    material,
                                    sameSideOwnerIsWhite,
                                    successors
                            );


            for (int i = 0;
                 i < successorCount;
                 i++) {

                if (successors.boundaryType(
                        i
                ) != FourPieceTierOnePrimitiveMoveGenerator
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
                        FourPieceTierOnePrimitivePredecessorGenerator
                                .generatePredecessors(
                                        child,
                                        material,
                                        sameSideOwnerIsWhite,
                                        predecessors,
                                        validationForward
                                );


                if (!contains(
                        predecessors,
                        predecessorCount,
                        parent
                )) {

                    printMissing(
                            material,
                            sameSideOwnerIsWhite,
                            parent,
                            child,
                            predecessors,
                            predecessorCount
                    );


                    throw new IllegalStateException(
                            "Generic Tier-1 predecessor generator missed "
                                    + "a legal same-class edge."
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


            if (!FourPieceTierOnePrimitiveMoveGenerator
                    .isStructurallyLegal(
                            child,
                            material,
                            sameSideOwnerIsWhite
                    )) {

                continue;
            }


            int predecessorCount =
                    FourPieceTierOnePrimitivePredecessorGenerator
                            .generatePredecessors(
                                    child,
                                    material,
                                    sameSideOwnerIsWhite,
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
                        FourPieceTierOnePrimitiveMoveGenerator
                                .generateLegalSuccessors(
                                        parent,
                                        material,
                                        sameSideOwnerIsWhite,
                                        validationForward
                                );


                if (!containsSameClassChild(
                        validationForward,
                        forwardCount,
                        child
                )) {

                    printUnsound(
                            material,
                            sameSideOwnerIsWhite,
                            parent,
                            child
                    );


                    throw new IllegalStateException(
                            "Generic Tier-1 predecessor generator returned "
                                    + "an unsound parent."
                    );
                }


                predecessorsChecked++;
            }


            legalChildren++;
        }


        if (duplicatePredecessors
                != 0) {

            throw new IllegalStateException(
                    "Duplicate generic Tier-1 predecessors observed: "
                            + duplicatePredecessors
            );
        }


        System.out.println();

        System.out.println(
                material.displayName()
                        + " — "
                        + orientationName(
                        material,
                        sameSideOwnerIsWhite
                )
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


    private static void verifyKqpkRegression(
            boolean strongIsWhite,
            int childSamples,
            long seed
    ) {

        FourPieceMaterialClass material =
                FourPieceMaterialClass.sameSide(
                        PieceType.QUEEN,
                        PieceType.PAWN
                );

        Random random =
                new Random(
                        seed
                );

        FourPieceTierOnePrimitiveMoveGenerator.Buffer genericForward =
                new FourPieceTierOnePrimitiveMoveGenerator.Buffer(
                        64
                );

        FourPieceTierOnePrimitivePredecessorGenerator.Buffer generic =
                new FourPieceTierOnePrimitivePredecessorGenerator.Buffer(
                        64
                );

        FourPieceTierOneKqpkPrimitiveMoveGenerator.Buffer oldForward =
                new FourPieceTierOneKqpkPrimitiveMoveGenerator.Buffer(
                        64
                );

        FourPieceTierOneKqpkPrimitivePredecessorGenerator.Buffer old =
                new FourPieceTierOneKqpkPrimitivePredecessorGenerator.Buffer(
                        64
                );


        int checked =
                0;


        while (checked
                < childSamples) {

            int child =
                    randomState(
                            random
                    );


            boolean oldLegal =
                    FourPieceTierOneKqpkPrimitiveMoveGenerator
                            .isStructurallyLegal(
                                    child,
                                    strongIsWhite
                            );

            boolean newLegal =
                    FourPieceTierOnePrimitiveMoveGenerator
                            .isStructurallyLegal(
                                    child,
                                    material,
                                    strongIsWhite
                            );


            if (oldLegal
                    != newLegal) {

                throw new IllegalStateException(
                        "KQPK predecessor regression: legality mismatch."
                );
            }


            if (!oldLegal) {

                continue;
            }


            int oldCount =
                    FourPieceTierOneKqpkPrimitivePredecessorGenerator
                            .generatePredecessors(
                                    child,
                                    strongIsWhite,
                                    old,
                                    oldForward
                            );

            int newCount =
                    FourPieceTierOnePrimitivePredecessorGenerator
                            .generatePredecessors(
                                    child,
                                    material,
                                    strongIsWhite,
                                    generic,
                                    genericForward
                            );


            Set<Integer> oldStates =
                    states(
                            old,
                            oldCount
                    );

            Set<Integer> newStates =
                    states(
                            generic,
                            newCount
                    );


            if (!oldStates.equals(
                    newStates
            )) {

                throw new IllegalStateException(
                        "Generic predecessor set differs from proven M35 "
                                + "for KQPK state "
                                + child
                                + ".\nold="
                                + oldStates
                                + "\nnew="
                                + newStates
                );
            }


            checked++;
        }


        System.out.println();

        System.out.println(
                "KQPK M35 predecessor regression — strong "
                        + (strongIsWhite
                        ? "WHITE"
                        : "BLACK")
        );

        System.out.println(
                "  legal children checked: "
                        + checked
        );

        System.out.println(
                "  exact predecessor-set equivalence: PASSED"
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

            int first =
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
                    first,
                    pawn
            )) {

                continue;
            }


            return FourPieceGenericPrimitiveState.encode(
                    wk,
                    bk,
                    first,
                    pawn,
                    random.nextBoolean()
            );
        }
    }


    private static boolean contains(
            FourPieceTierOnePrimitivePredecessorGenerator.Buffer buffer,
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
            FourPieceTierOnePrimitiveMoveGenerator.Buffer buffer,
            int count,
            int child
    ) {

        for (int i = 0;
             i < count;
             i++) {

            if (buffer.boundaryType(
                    i
            ) == FourPieceTierOnePrimitiveMoveGenerator
                    .BOUNDARY_NONE
                    && buffer.state(
                    i
            ) == child) {

                return true;
            }
        }


        return false;
    }


    private static Set<Integer> states(
            FourPieceTierOnePrimitivePredecessorGenerator.Buffer buffer,
            int count
    ) {

        Set<Integer> result =
                new HashSet<>(
                        Math.max(
                                16,
                                count * 2
                        )
                );


        for (int i = 0;
             i < count;
             i++) {

            result.add(
                    buffer.state(
                            i
                    )
            );
        }


        return result;
    }


    private static Set<Integer> states(
            FourPieceTierOneKqpkPrimitivePredecessorGenerator.Buffer buffer,
            int count
    ) {

        Set<Integer> result =
                new HashSet<>(
                        Math.max(
                                16,
                                count * 2
                        )
                );


        for (int i = 0;
             i < count;
             i++) {

            result.add(
                    buffer.state(
                            i
                    )
            );
        }


        return result;
    }


    private static String orientationName(
            FourPieceMaterialClass material,
            boolean sameSideOwnerIsWhite
    ) {

        if (material.distribution()
                == FourPieceMaterialClass.Distribution.SPLIT) {

            return "canonical split (first WHITE / pawn BLACK)";
        }


        return sameSideOwnerIsWhite
                ? "same-side owner WHITE"
                : "same-side owner BLACK";
    }


    private static void printMissing(
            FourPieceMaterialClass material,
            boolean sameSideOwnerIsWhite,
            int parent,
            int child,
            FourPieceTierOnePrimitivePredecessorGenerator.Buffer predecessors,
            int count
    ) {

        System.out.println();

        System.out.println(
                "MISSING GENERIC TIER-1 PREDECESSOR"
        );

        System.out.println(
                "  material: "
                        + material.displayName()
        );

        System.out.println(
                "  orientation: "
                        + orientationName(
                        material,
                        sameSideOwnerIsWhite
                )
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
                        + count
        );


        for (int i = 0;
             i < count
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
            FourPieceMaterialClass material,
            boolean sameSideOwnerIsWhite,
            int parent,
            int child
    ) {

        System.out.println();

        System.out.println(
                "UNSOUND GENERIC TIER-1 PREDECESSOR"
        );

        System.out.println(
                "  material: "
                        + material.displayName()
        );

        System.out.println(
                "  orientation: "
                        + orientationName(
                        material,
                        sameSideOwnerIsWhite
                )
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
                + " X="
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
                : "WHITE");
    }


    private static String algebraic(
            int square
    ) {

        char file =
                (char) (
                        'a'
                                + (square & 7)
                );

        char rank =
                (char) (
                        '1'
                                + (square >>> 3)
                );


        return ""
                + file
                + rank;
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

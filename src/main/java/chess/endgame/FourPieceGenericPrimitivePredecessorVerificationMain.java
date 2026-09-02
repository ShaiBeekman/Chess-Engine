package main.java.chess.endgame;

import main.java.chess.model.Color;
import main.java.chess.model.PieceType;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;


/**
 * Milestone 16 correctness gate for generic Tier-0 primitive
 * predecessor generation.
 *
 * Tests:
 *
 * 1. Exact KQRK predecessor-set equivalence against the proven
 *    specialized KqrkPrimitivePredecessorGenerator.
 *
 * 2. Forward -> reverse completeness for representative generic
 *    Tier-0 material classes.
 *
 * 3. Reverse -> forward validity for every generated predecessor.
 *
 * 4. No duplicate predecessor ids.
 */
public final class FourPieceGenericPrimitivePredecessorVerificationMain {

    private static final int DEFAULT_KQRK_CHILDREN =
            25_000;

    private static final int DEFAULT_GENERIC_PARENTS =
            10_000;


    private FourPieceGenericPrimitivePredecessorVerificationMain() {

    }


    public static void main(
            String[] args
    ) {

        int kqrkChildren =
                args.length > 0
                        ? Integer.parseInt(
                        args[0]
                )
                        : DEFAULT_KQRK_CHILDREN;


        int genericParents =
                args.length > 1
                        ? Integer.parseInt(
                        args[1]
                )
                        : DEFAULT_GENERIC_PARENTS;


        if (kqrkChildren < 1
                || genericParents < 1) {

            throw new IllegalArgumentException(
                    "Verification sample sizes must be positive."
            );
        }


        verifyKqrkExactSets(
                kqrkChildren,
                Color.WHITE,
                0x47454E5052454457L
        );


        verifyKqrkExactSets(
                kqrkChildren,
                Color.BLACK,
                0x47454E5052454442L
        );


        verifyGenericMaterial(
                FourPieceMaterialClass.sameSide(
                        PieceType.QUEEN,
                        PieceType.BISHOP
                ),
                true,
                genericParents,
                0x51425001L
        );


        verifyGenericMaterial(
                FourPieceMaterialClass.sameSide(
                        PieceType.QUEEN,
                        PieceType.KNIGHT
                ),
                true,
                genericParents,
                0x514E5002L
        );


        verifyGenericMaterial(
                FourPieceMaterialClass.sameSide(
                        PieceType.QUEEN,
                        PieceType.QUEEN
                ),
                true,
                genericParents,
                0x51515003L
        );


        verifyGenericMaterial(
                FourPieceMaterialClass.split(
                        PieceType.QUEEN,
                        PieceType.ROOK
                ),
                true,
                genericParents,
                0x51525004L
        );


        verifyGenericMaterial(
                FourPieceMaterialClass.split(
                        PieceType.BISHOP,
                        PieceType.KNIGHT
                ),
                true,
                genericParents,
                0x424E5005L
        );


        System.out.println();

        System.out.println(
                "GENERIC TIER-0 PRIMITIVE PREDECESSOR VERIFICATION PASSED"
        );
    }


    private static void verifyKqrkExactSets(
            int children,
            Color strongColor,
            long seed
    ) {

        boolean strongIsWhite =
                strongColor
                        == Color.WHITE;


        FourPieceMaterialClass material =
                FourPieceMaterialClass.sameSide(
                        PieceType.QUEEN,
                        PieceType.ROOK
                );


        KqrkPrimitivePredecessorGenerator.Buffer specialized =
                new KqrkPrimitivePredecessorGenerator.Buffer(
                        64
                );


        FourPieceGenericPrimitivePredecessorGenerator.Buffer generic =
                new FourPieceGenericPrimitivePredecessorGenerator.Buffer(
                        64
                );


        Random random =
                new Random(
                        seed
                );


        int checked =
                0;

        int mismatches =
                0;

        long specializedEdges =
                0;

        long genericEdges =
                0;


        while (checked
                < children) {

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


            int specializedCount =
                    KqrkPrimitivePredecessorGenerator
                            .generateLegalPredecessors(
                                    child,
                                    strongIsWhite,
                                    specialized
                            );


            int genericCount =
                    FourPieceGenericPrimitivePredecessorGenerator
                            .generateLegalPredecessors(
                                    child,
                                    material,
                                    strongIsWhite,
                                    generic
                            );


            specializedEdges +=
                    specializedCount;

            genericEdges +=
                    genericCount;


            Set<Integer> expected =
                    new HashSet<>(
                            Math.max(
                                    16,
                                    specializedCount * 2
                            )
                    );


            for (int i = 0;
                 i < specializedCount;
                 i++) {

                expected.add(
                        specialized.state(
                                i
                        )
                );
            }


            Set<Integer> actual =
                    new HashSet<>(
                            Math.max(
                                    16,
                                    genericCount * 2
                            )
                    );


            for (int i = 0;
                 i < genericCount;
                 i++) {

                actual.add(
                        generic.state(
                                i
                        )
                );
            }


            if (!expected.equals(
                    actual
            )) {

                mismatches++;


                if (mismatches
                        <= 10) {

                    Set<Integer> missing =
                            new HashSet<>(
                                    expected
                            );

                    missing.removeAll(
                            actual
                    );


                    Set<Integer> extra =
                            new HashSet<>(
                                    actual
                            );

                    extra.removeAll(
                            expected
                    );


                    System.out.println();

                    System.out.println(
                            "GENERIC KQRK PREDECESSOR MISMATCH"
                    );

                    System.out.println(
                            "Strong color: "
                                    + strongColor
                    );

                    System.out.println(
                            "Child: "
                                    + child
                    );

                    System.out.println(
                            "Specialized count: "
                                    + specializedCount
                    );

                    System.out.println(
                            "Generic count: "
                                    + genericCount
                    );

                    System.out.println(
                            "Missing: "
                                    + limited(
                                    missing
                            )
                    );

                    System.out.println(
                            "Extra: "
                                    + limited(
                                    extra
                            )
                    );
                }
            }


            checked++;
        }


        System.out.println();

        System.out.println(
                "Generic KQRK predecessor equivalence — strong "
                        + strongColor
        );

        System.out.println(
                "Legal children checked: "
                        + checked
        );

        System.out.println(
                "Specialized predecessor edges: "
                        + specializedEdges
        );

        System.out.println(
                "Generic predecessor edges: "
                        + genericEdges
        );

        System.out.println(
                "Predecessor-set mismatches: "
                        + mismatches
        );


        if (mismatches
                != 0) {

            throw new IllegalStateException(
                    "Generic KQRK predecessor sets differ from the proven specialized generator."
            );
        }


        System.out.println(
                "GENERIC KQRK PREDECESSORS MATCH"
        );
    }


    private static void verifyGenericMaterial(
            FourPieceMaterialClass material,
            boolean sameSideOwnerIsWhite,
            int parents,
            long seed
    ) {

        FourPieceGenericPrimitiveMoveGenerator.Buffer successors =
                new FourPieceGenericPrimitiveMoveGenerator.Buffer(
                        64
                );


        FourPieceGenericPrimitiveMoveGenerator.Buffer verifyForward =
                new FourPieceGenericPrimitiveMoveGenerator.Buffer(
                        64
                );


        FourPieceGenericPrimitivePredecessorGenerator.Buffer predecessors =
                new FourPieceGenericPrimitivePredecessorGenerator.Buffer(
                        64
                );


        Random random =
                new Random(
                        seed
                );


        int checkedParents =
                0;

        long checkedForwardEdges =
                0;

        long checkedReverseEdges =
                0;

        int missingReverseEdges =
                0;

        int invalidReverseEdges =
                0;

        int duplicatePredecessors =
                0;


        while (checkedParents
                < parents) {

            int parent =
                    random.nextInt(
                            FourPieceGenericPrimitiveState.STATE_COUNT
                    );


            if (!FourPieceGenericPrimitiveRules.isStructurallyLegal(
                    parent,
                    material,
                    sameSideOwnerIsWhite
            )) {

                continue;
            }


            int successorCount =
                    FourPieceGenericPrimitiveMoveGenerator
                            .generateLegalSuccessors(
                                    parent,
                                    material,
                                    sameSideOwnerIsWhite,
                                    successors
                            );


            for (int i = 0;
                 i < successorCount;
                 i++) {

                if (successors.isBoundary(
                        i
                )) {

                    continue;
                }


                int child =
                        successors.state(
                                i
                        );


                int predecessorCount =
                        FourPieceGenericPrimitivePredecessorGenerator
                                .generateLegalPredecessors(
                                        child,
                                        material,
                                        sameSideOwnerIsWhite,
                                        predecessors
                                );


                boolean found =
                        false;


                Set<Integer> unique =
                        new HashSet<>(
                                Math.max(
                                        16,
                                        predecessorCount * 2
                                )
                        );


                for (int j = 0;
                     j < predecessorCount;
                     j++) {

                    int candidate =
                            predecessors.state(
                                    j
                            );


                    if (!unique.add(
                            candidate
                    )) {

                        duplicatePredecessors++;
                    }


                    if (candidate
                            == parent) {

                        found =
                                true;
                    }


                    /*
                     * Reverse -> forward correctness.
                     */
                    int verifyCount =
                            FourPieceGenericPrimitiveMoveGenerator
                                    .generateLegalSuccessors(
                                            candidate,
                                            material,
                                            sameSideOwnerIsWhite,
                                            verifyForward
                                    );


                    boolean regeneratesChild =
                            false;


                    for (int k = 0;
                         k < verifyCount;
                         k++) {

                        if (!verifyForward.isBoundary(
                                k
                        )
                                && verifyForward.state(
                                k
                        )
                                == child) {

                            regeneratesChild =
                                    true;

                            break;
                        }
                    }


                    if (!regeneratesChild) {

                        invalidReverseEdges++;


                        if (invalidReverseEdges
                                <= 10) {

                            System.out.println(
                                    "Invalid reverse edge for "
                                            + material.displayName()
                                            + ": "
                                            + candidate
                                            + " -> "
                                            + child
                            );
                        }
                    }


                    checkedReverseEdges++;
                }


                if (!found) {

                    missingReverseEdges++;


                    if (missingReverseEdges
                            <= 10) {

                        System.out.println(
                                "Missing reverse edge for "
                                        + material.displayName()
                                        + ": "
                                        + parent
                                        + " -> "
                                        + child
                        );
                    }
                }


                checkedForwardEdges++;
            }


            checkedParents++;
        }


        System.out.println();

        System.out.println(
                "Generic predecessor sanity — "
                        + material.displayName()
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
                "Reverse edges checked: "
                        + checkedReverseEdges
        );

        System.out.println(
                "Missing reverse edges: "
                        + missingReverseEdges
        );

        System.out.println(
                "Invalid reverse edges: "
                        + invalidReverseEdges
        );

        System.out.println(
                "Duplicate predecessors: "
                        + duplicatePredecessors
        );


        if (missingReverseEdges
                != 0
                || invalidReverseEdges
                != 0
                || duplicatePredecessors
                != 0) {

            throw new IllegalStateException(
                    "Generic predecessor verification failed for "
                            + material.displayName()
            );
        }


        System.out.println(
                "GENERIC PREDECESSOR SANITY PASSED"
        );
    }


    private static String limited(
            Set<Integer> values
    ) {

        StringBuilder builder =
                new StringBuilder(
                        "["
                );


        int count =
                0;


        for (int value :
                values) {

            if (count > 0) {

                builder.append(
                        ", "
                );
            }


            builder.append(
                    value
            );


            count++;


            if (count
                    == 10) {

                if (values.size()
                        > count) {

                    builder.append(
                            ", ..."
                    );
                }


                break;
            }
        }


        builder.append(
                ']'
        );


        return builder.toString();
    }
}
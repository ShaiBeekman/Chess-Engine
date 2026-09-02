package main.java.chess.endgame;

import main.java.chess.model.Color;
import main.java.chess.model.PieceType;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;


/**
 * Milestone 14 correctness gate.
 *
 * The first and most important test compares the generic Tier-0 successor
 * generator directly against the already-verified specialized KQRK
 * primitive successor generator.
 *
 * Comparison is by successor MULTISET rather than simple set so duplicate
 * generation bugs cannot hide.
 *
 * We verify both:
 *
 *     strong side = White
 *     strong side = Black
 *
 * After KQRK equivalence is established, several additional Tier-0 material
 * classes receive generic structural sanity testing:
 *
 *     KQBK
 *     KQNK
 *     KQQK
 *     KQ vs KR
 *     KB vs KN
 *
 * Every generated in-class successor must itself satisfy generic structural
 * legality, and every boundary must contain a valid three-piece material
 * description.
 */
public final class FourPieceGenericPrimitiveMoveVerificationMain {

    private static final int DEFAULT_KQRK_POSITIONS =
            25_000;

    private static final int DEFAULT_GENERIC_POSITIONS =
            5_000;


    private FourPieceGenericPrimitiveMoveVerificationMain() {

    }


    public static void main(
            String[] args
    ) {

        int kqrkPositions =
                args.length > 0
                        ? Integer.parseInt(
                        args[0]
                )
                        : DEFAULT_KQRK_POSITIONS;


        int genericPositions =
                args.length > 1
                        ? Integer.parseInt(
                        args[1]
                )
                        : DEFAULT_GENERIC_POSITIONS;


        if (kqrkPositions < 1
                || genericPositions < 1) {

            throw new IllegalArgumentException(
                    "Verification sample sizes must be positive."
            );
        }


        verifyKqrkEquivalence(
                kqrkPositions,
                Color.WHITE,
                0x47454E4B515257L
        );


        verifyKqrkEquivalence(
                kqrkPositions,
                Color.BLACK,
                0x47454E4B515242L
        );


        verifyGenericMaterial(
                FourPieceMaterialClass.sameSide(
                        PieceType.QUEEN,
                        PieceType.BISHOP
                ),
                true,
                genericPositions,
                0x4B51424BL
        );


        verifyGenericMaterial(
                FourPieceMaterialClass.sameSide(
                        PieceType.QUEEN,
                        PieceType.KNIGHT
                ),
                true,
                genericPositions,
                0x4B514E4BL
        );


        verifyGenericMaterial(
                FourPieceMaterialClass.sameSide(
                        PieceType.QUEEN,
                        PieceType.QUEEN
                ),
                true,
                genericPositions,
                0x4B51514BL
        );


        verifyGenericMaterial(
                FourPieceMaterialClass.split(
                        PieceType.QUEEN,
                        PieceType.ROOK
                ),
                true,
                genericPositions,
                0x515652L
        );


        verifyGenericMaterial(
                FourPieceMaterialClass.split(
                        PieceType.BISHOP,
                        PieceType.KNIGHT
                ),
                true,
                genericPositions,
                0x42564EL
        );


        System.out.println();

        System.out.println(
                "GENERIC TIER-0 PRIMITIVE MOVE VERIFICATION PASSED"
        );
    }


    private static void verifyKqrkEquivalence(
            int positions,
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


        KqrkPrimitiveMoveGenerator.Buffer specialized =
                new KqrkPrimitiveMoveGenerator.Buffer(
                        64
                );


        FourPieceGenericPrimitiveMoveGenerator.Buffer generic =
                new FourPieceGenericPrimitiveMoveGenerator.Buffer(
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

        long attempts =
                0;


        while (checked
                < positions) {

            attempts++;


            int state =
                    random.nextInt(
                            KqrkPrimitiveState.STATE_COUNT
                    );


            if (!KqrkPrimitiveRules.isStructurallyLegal(
                    state,
                    strongIsWhite
            )) {

                continue;
            }


            /*
             * Milestone 13 already proved generic KQRK structural legality
             * is equivalent. Check it here again because the move test
             * depends on that invariant.
             */
            if (!FourPieceGenericPrimitiveRules.isStructurallyLegal(
                    state,
                    material,
                    strongIsWhite
            )) {

                throw new IllegalStateException(
                        "State accepted by specialized KQRK rules was rejected by generic rules: "
                                + state
                );
            }


            int specializedCount =
                    KqrkPrimitiveMoveGenerator.generateLegalSuccessors(
                            state,
                            strongIsWhite,
                            specialized
                    );


            int genericCount =
                    FourPieceGenericPrimitiveMoveGenerator.generateLegalSuccessors(
                            state,
                            material,
                            strongIsWhite,
                            generic
                    );


            Map<String, Integer> expected =
                    classifySpecialized(
                            specialized,
                            specializedCount
                    );


            Map<String, Integer> actual =
                    classifyGenericKqrk(
                            generic,
                            genericCount,
                            strongIsWhite
                    );


            if (!expected.equals(
                    actual
            )) {

                mismatches++;


                if (mismatches
                        <= 10) {

                    System.out.println();

                    System.out.println(
                            "Generic KQRK move mismatch"
                    );

                    System.out.println(
                            "State: "
                                    + state
                    );

                    System.out.println(
                            "Strong color: "
                                    + strongColor
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
                            "Specialized only: "
                                    + difference(
                                    expected,
                                    actual
                            )
                    );

                    System.out.println(
                            "Generic only: "
                                    + difference(
                                    actual,
                                    expected
                            )
                    );
                }
            }


            checked++;
        }


        System.out.println();

        System.out.println(
                "Generic KQRK move verification — strong "
                        + strongColor
        );

        System.out.println(
                "Legal positions checked: "
                        + checked
        );

        System.out.println(
                "Random raw attempts: "
                        + attempts
        );

        System.out.println(
                "Mismatches: "
                        + mismatches
        );


        if (mismatches
                != 0) {

            throw new IllegalStateException(
                    "Generic KQRK successor generation differs from the proven specialized generator."
            );
        }


        System.out.println(
                "GENERIC KQRK MOVE GENERATION MATCHES"
        );
    }


    private static Map<String, Integer> classifySpecialized(
            KqrkPrimitiveMoveGenerator.Buffer buffer,
            int count
    ) {

        Map<String, Integer> result =
                new HashMap<>();


        for (int i = 0;
             i < count;
             i++) {

            int type =
                    buffer.boundaryType(
                            i
                    );


            String key;


            if (type
                    == KqrkPrimitiveMoveGenerator.BOUNDARY_NONE) {

                key =
                        "S:"
                                + buffer.state(
                                i
                        );

            } else if (type
                    == KqrkPrimitiveMoveGenerator.BOUNDARY_KQK) {

                key =
                        "KQK:"
                                + buffer.survivingPieceSquare(
                                i
                        );

            } else if (type
                    == KqrkPrimitiveMoveGenerator.BOUNDARY_KRK) {

                key =
                        "KRK:"
                                + buffer.survivingPieceSquare(
                                i
                        );

            } else if (type
                    == KqrkPrimitiveMoveGenerator.BOUNDARY_KK) {

                key =
                        "KK";

            } else {

                key =
                        "UNSUPPORTED";
            }


            result.merge(
                    key,
                    1,
                    Integer::sum
            );
        }


        return result;
    }


    private static Map<String, Integer> classifyGenericKqrk(
            FourPieceGenericPrimitiveMoveGenerator.Buffer buffer,
            int count,
            boolean strongIsWhite
    ) {

        Map<String, Integer> result =
                new HashMap<>();


        for (int i = 0;
             i < count;
             i++) {

            String key;


            if (!buffer.isBoundary(
                    i
            )) {

                key =
                        "S:"
                                + buffer.state(
                                i
                        );

            } else {

                PieceType type =
                        buffer.survivingPieceType(
                                i
                        );


                /*
                 * In KQRK, the surviving non-king piece must still belong
                 * to the strong side.
                 */
                if (buffer.survivingPieceIsWhite(
                        i
                )
                        != strongIsWhite) {

                    key =
                            "WRONG_OWNER";

                } else if (type
                        == PieceType.QUEEN) {

                    key =
                            "KQK:"
                                    + buffer.survivingPieceSquare(
                                    i
                            );

                } else if (type
                        == PieceType.ROOK) {

                    key =
                            "KRK:"
                                    + buffer.survivingPieceSquare(
                                    i
                            );

                } else {

                    key =
                            "UNSUPPORTED";
                }
            }


            result.merge(
                    key,
                    1,
                    Integer::sum
            );
        }


        return result;
    }


    private static void verifyGenericMaterial(
            FourPieceMaterialClass material,
            boolean sameSideOwnerIsWhite,
            int positions,
            long seed
    ) {

        FourPieceGenericPrimitiveMoveGenerator.Buffer buffer =
                new FourPieceGenericPrimitiveMoveGenerator.Buffer(
                        64
                );


        Random random =
                new Random(
                        seed
                );


        int checked =
                0;

        long attempts =
                0;

        long successors =
                0;

        long inClass =
                0;

        long boundaries =
                0;


        while (checked
                < positions) {

            attempts++;


            int state =
                    random.nextInt(
                            FourPieceGenericPrimitiveState.STATE_COUNT
                    );


            if (!FourPieceGenericPrimitiveRules.isStructurallyLegal(
                    state,
                    material,
                    sameSideOwnerIsWhite
            )) {

                continue;
            }


            int count =
                    FourPieceGenericPrimitiveMoveGenerator.generateLegalSuccessors(
                            state,
                            material,
                            sameSideOwnerIsWhite,
                            buffer
                    );


            successors +=
                    count;


            for (int i = 0;
                 i < count;
                 i++) {

                if (!buffer.isBoundary(
                        i
                )) {

                    inClass++;


                    int child =
                            buffer.state(
                                    i
                            );


                    if (!FourPieceGenericPrimitiveRules.isStructurallyLegal(
                            child,
                            material,
                            sameSideOwnerIsWhite
                    )) {

                        throw new IllegalStateException(
                                "Generator produced illegal in-class child for "
                                        + material.displayName()
                                        + ": "
                                        + child
                        );
                    }


                    if (!FourPieceGenericPrimitiveState.isCanonical(
                            child,
                            material
                    )) {

                        throw new IllegalStateException(
                                "Generator produced noncanonical child for "
                                        + material.displayName()
                                        + ": "
                                        + child
                        );
                    }

                } else {

                    boundaries++;


                    PieceType survivingType =
                            buffer.survivingPieceType(
                                    i
                            );


                    if (survivingType
                            == null
                            || survivingType
                            == PieceType.KING
                            || survivingType
                            == PieceType.PAWN) {

                        throw new IllegalStateException(
                                "Invalid Tier-0 boundary material from "
                                        + material.displayName()
                        );
                    }


                    int whiteKing =
                            buffer.boundaryWhiteKing(
                                    i
                            );

                    int blackKing =
                            buffer.boundaryBlackKing(
                                    i
                            );

                    int survivingSquare =
                            buffer.survivingPieceSquare(
                                    i
                            );


                    if (whiteKing == blackKing
                            || whiteKing == survivingSquare
                            || blackKing == survivingSquare) {

                        throw new IllegalStateException(
                                "Boundary contains overlapping pieces for "
                                        + material.displayName()
                        );
                    }


                    if (adjacent(
                            whiteKing,
                            blackKing
                    )) {

                        throw new IllegalStateException(
                                "Boundary contains adjacent kings for "
                                        + material.displayName()
                        );
                    }
                }
            }


            checked++;
        }


        System.out.println();

        System.out.println(
                "Generic material sanity — "
                        + material.displayName()
        );

        System.out.println(
                "Legal positions checked: "
                        + checked
        );

        System.out.println(
                "Random raw attempts: "
                        + attempts
        );

        System.out.println(
                "Generated successors: "
                        + successors
        );

        System.out.println(
                "In-class successors: "
                        + inClass
        );

        System.out.println(
                "Three-piece boundaries: "
                        + boundaries
        );

        System.out.println(
                "GENERIC MATERIAL SANITY PASSED"
        );
    }


    private static Map<String, Integer> difference(
            Map<String, Integer> first,
            Map<String, Integer> second
    ) {

        Map<String, Integer> result =
                new HashMap<>();


        for (Map.Entry<String, Integer> entry :
                first.entrySet()) {

            int other =
                    second.getOrDefault(
                            entry.getKey(),
                            0
                    );


            int extra =
                    entry.getValue()
                            - other;


            if (extra > 0) {

                result.put(
                        entry.getKey(),
                        extra
                );
            }
        }


        return result;
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
}
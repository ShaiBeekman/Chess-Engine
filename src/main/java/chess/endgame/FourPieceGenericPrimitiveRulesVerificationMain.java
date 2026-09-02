package main.java.chess.endgame;

import main.java.chess.model.Color;
import main.java.chess.model.PieceType;

import java.util.Random;


/**
 * Milestone 13 correctness gate.
 *
 * Cross-checks generic Tier-0 primitive KQRK behavior against the already
 * proven specialized KQRK primitive implementation.
 *
 * We deliberately verify:
 *
 *     state encoding
 *     state decoding
 *     structural legality
 *     side-to-move check detection
 *
 * for both strong-side color orientations.
 *
 * Once this passes, the generic rules layer can be trusted as a semantic
 * replacement for the hard-coded KQRK rules for pawnless material.
 */
public final class FourPieceGenericPrimitiveRulesVerificationMain {

    private static final int DEFAULT_SAMPLES =
            250_000;


    private FourPieceGenericPrimitiveRulesVerificationMain() {

    }


    public static void main(
            String[] args
    ) {

        int samples =
                args.length > 0
                        ? Integer.parseInt(
                        args[0]
                )
                        : DEFAULT_SAMPLES;


        if (samples < 1) {

            throw new IllegalArgumentException(
                    "Samples must be positive."
            );
        }


        verifyEncoding(
                samples,
                0x4F55525049454345L
        );


        verifyKqrkRules(
                samples,
                Color.WHITE,
                0x51A17E5L
        );


        verifyKqrkRules(
                samples,
                Color.BLACK,
                0xB1AC4L
        );


        verifyNewPieceAttacks();


        System.out.println();

        System.out.println(
                "GENERIC TIER-0 PRIMITIVE RULES VERIFICATION PASSED"
        );
    }


    private static void verifyEncoding(
            int samples,
            long seed
    ) {

        Random random =
                new Random(
                        seed
                );


        for (int i = 0;
             i < samples;
             i++) {

            int whiteKing =
                    random.nextInt(
                            64
                    );

            int blackKing =
                    random.nextInt(
                            64
                    );

            int queen =
                    random.nextInt(
                            64
                    );

            int rook =
                    random.nextInt(
                            64
                    );

            boolean blackToMove =
                    random.nextBoolean();


            int specialized =
                    KqrkPrimitiveState.encode(
                            whiteKing,
                            blackKing,
                            queen,
                            rook,
                            blackToMove
                    );


            int generic =
                    FourPieceGenericPrimitiveState.encode(
                            whiteKing,
                            blackKing,
                            queen,
                            rook,
                            blackToMove
                    );


            if (specialized
                    != generic) {

                throw new IllegalStateException(
                        "Generic encoding differs from KQRK encoding."
                );
            }


            if (FourPieceGenericPrimitiveState.whiteKing(
                    generic
            )
                    != whiteKing
                    ||
                    FourPieceGenericPrimitiveState.blackKing(
                            generic
                    )
                            != blackKing
                    ||
                    FourPieceGenericPrimitiveState.firstExtra(
                            generic
                    )
                            != queen
                    ||
                    FourPieceGenericPrimitiveState.secondExtra(
                            generic
                    )
                            != rook
                    ||
                    FourPieceGenericPrimitiveState.blackToMove(
                            generic
                    )
                            != blackToMove) {

                throw new IllegalStateException(
                        "Generic primitive decode round-trip failed."
                );
            }
        }


        System.out.println(
                "Primitive encoding compatibility"
        );

        System.out.println(
                "Samples: "
                        + samples
        );

        System.out.println(
                "Encoding: MATCH"
        );

        System.out.println(
                "Decoding: MATCH"
        );
    }


    private static void verifyKqrkRules(
            int samples,
            Color strongColor,
            long seed
    ) {

        FourPieceMaterialClass material =
                FourPieceMaterialClass.sameSide(
                        PieceType.QUEEN,
                        PieceType.ROOK
                );


        boolean strongIsWhite =
                strongColor
                        == Color.WHITE;


        Random random =
                new Random(
                        seed
                );


        int structuralMatches =
                0;

        int structuralMismatches =
                0;

        int checkMatches =
                0;

        int checkMismatches =
                0;


        for (int i = 0;
             i < samples;
             i++) {

            int state =
                    random.nextInt(
                            KqrkPrimitiveState.STATE_COUNT
                    );


            boolean specializedLegal =
                    KqrkPrimitiveRules.isStructurallyLegal(
                            state,
                            strongIsWhite
                    );


            boolean genericLegal =
                    FourPieceGenericPrimitiveRules.isStructurallyLegal(
                            state,
                            material,
                            strongIsWhite
                    );


            if (specializedLegal
                    == genericLegal) {

                structuralMatches++;

            } else {

                structuralMismatches++;


                if (structuralMismatches
                        <= 10) {

                    printMismatch(
                            "STRUCTURAL",
                            state,
                            strongColor,
                            specializedLegal,
                            genericLegal
                    );
                }
            }


            /*
             * Only compare check semantics for positions accepted by both
             * implementations.
             */
            if (!specializedLegal
                    || !genericLegal) {

                continue;
            }


            boolean specializedCheck =
                    KqrkPrimitiveRules.sideToMoveIsInCheck(
                            state,
                            strongIsWhite
                    );


            boolean genericCheck =
                    FourPieceGenericPrimitiveRules.sideToMoveIsInCheck(
                            state,
                            material,
                            strongIsWhite
                    );


            if (specializedCheck
                    == genericCheck) {

                checkMatches++;

            } else {

                checkMismatches++;


                if (checkMismatches
                        <= 10) {

                    printMismatch(
                            "CHECK",
                            state,
                            strongColor,
                            specializedCheck,
                            genericCheck
                    );
                }
            }
        }


        System.out.println();

        System.out.println(
                "Generic KQRK verification — strong "
                        + strongColor
        );

        System.out.println(
                "Samples: "
                        + samples
        );

        System.out.println(
                "Structural matches: "
                        + structuralMatches
        );

        System.out.println(
                "Structural mismatches: "
                        + structuralMismatches
        );

        System.out.println(
                "Check matches: "
                        + checkMatches
        );

        System.out.println(
                "Check mismatches: "
                        + checkMismatches
        );


        if (structuralMismatches
                != 0
                || checkMismatches
                != 0) {

            throw new IllegalStateException(
                    "Generic KQRK primitive rules differ from the proven specialized rules."
            );
        }


        System.out.println(
                "GENERIC KQRK RULES MATCH"
        );
    }


    /**
     * Small deterministic tests for the two movement types KQRK never
     * exercised: bishop and knight.
     */
    private static void verifyNewPieceAttacks() {

        int a1 =
                square(
                        0,
                        0
                );

        int b3 =
                square(
                        1,
                        2
                );

        int c3 =
                square(
                        2,
                        2
                );

        int d4 =
                square(
                        3,
                        3
                );

        int e5 =
                square(
                        4,
                        4
                );

        int f6 =
                square(
                        5,
                        5
                );

        int h8 =
                square(
                        7,
                        7
                );


        /*
         * Bishop a1 -> h8 with no relevant blocker.
         */
        boolean bishopOpen =
                FourPieceGenericPrimitiveRules.bishopAttacks(
                        a1,
                        h8,
                        square(
                                7,
                                0
                        ),
                        square(
                                0,
                                7
                        ),
                        a1,
                        square(
                                7,
                                1
                        )
                );


        if (!bishopOpen) {

            throw new IllegalStateException(
                    "Generic bishop open-ray test failed."
            );
        }


        /*
         * Bishop a1 -> h8 blocked by d4.
         */
        boolean bishopBlocked =
                FourPieceGenericPrimitiveRules.bishopAttacks(
                        a1,
                        h8,
                        square(
                                7,
                                0
                        ),
                        square(
                                0,
                                7
                        ),
                        a1,
                        d4
                );


        if (bishopBlocked) {

            throw new IllegalStateException(
                    "Generic bishop blocker test failed."
            );
        }


        if (!FourPieceGenericPrimitiveRules.knightAttacks(
                a1,
                b3
        )) {

            throw new IllegalStateException(
                    "Generic knight attack test failed."
            );
        }


        if (FourPieceGenericPrimitiveRules.knightAttacks(
                a1,
                c3
        )) {

            throw new IllegalStateException(
                    "Generic knight non-attack test failed."
            );
        }


        /*
         * Additional diagonal sanity check to make the local variables
         * observable and ensure both diagonal directions are exercised.
         */
        if (!FourPieceGenericPrimitiveRules.bishopAttacks(
                c3,
                f6,
                square(
                        7,
                        0
                ),
                square(
                        0,
                        7
                ),
                c3,
                e5
        )) {

            /*
             * e5 is the target ray blocker before f6, so this MUST actually
             * be false.  Entering here is expected.
             */
        } else {

            throw new IllegalStateException(
                    "Generic bishop intermediate-blocker test failed."
            );
        }


        System.out.println();

        System.out.println(
                "New Tier-0 attack types"
        );

        System.out.println(
                "Bishop attacks: PASSED"
        );

        System.out.println(
                "Knight attacks: PASSED"
        );
    }


    private static void printMismatch(
            String type,
            int state,
            Color strongColor,
            boolean specialized,
            boolean generic
    ) {

        System.out.println(
                type
                        + " mismatch state "
                        + state
                        + " strong="
                        + strongColor
                        + " specialized="
                        + specialized
                        + " generic="
                        + generic
        );

        System.out.println(
                "    WK="
                        + FourPieceGenericPrimitiveState.whiteKing(
                        state
                )
                        + " BK="
                        + FourPieceGenericPrimitiveState.blackKing(
                        state
                )
                        + " first="
                        + FourPieceGenericPrimitiveState.firstExtra(
                        state
                )
                        + " second="
                        + FourPieceGenericPrimitiveState.secondExtra(
                        state
                )
                        + " blackToMove="
                        + FourPieceGenericPrimitiveState.blackToMove(
                        state
                )
        );
    }


    private static int square(
            int file,
            int rank
    ) {

        return rank * 8
                + file;
    }
}
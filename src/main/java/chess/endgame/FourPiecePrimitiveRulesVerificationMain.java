package main.java.chess.endgame;

import main.java.chess.model.Color;
import main.java.chess.model.Position;

import java.util.Random;


/**
 * Cross-checks primitive KQRK structural legality against the existing
 * object-based FourPieceTablebase encoder on a randomized sample.
 *
 * This is a correctness test, not a performance benchmark.
 */
public final class FourPiecePrimitiveRulesVerificationMain {

    private static final int DEFAULT_SAMPLES =
            100_000;


    public static void main(
            String[] args
    ) {

        int samples =
                args.length > 0
                        ? Integer.parseInt(
                        args[0]
                )
                        : DEFAULT_SAMPLES;


        verify(
                samples,
                Color.WHITE,
                0x51A17E5L
        );


        verify(
                samples,
                Color.BLACK,
                0xB1AC4L
        );
    }


    private static void verify(
            int samples,
            Color strongColor,
            long seed
    ) {

        FourPieceTablebase tablebase =
                new FourPieceTablebase(
                        FourPieceMaterial.kqrk(
                                strongColor
                        )
                );


        Random random =
                new Random(
                        seed
                );


        int matched =
                0;

        int mismatches =
                0;


        for (int i = 0;
             i < samples;
             i++) {

            int state =
                    random.nextInt(
                            KqrkPrimitiveState.STATE_COUNT
                    );


            boolean primitive =
                    KqrkPrimitiveRules.isStructurallyLegal(
                            state,
                            strongColor == Color.WHITE
                    );


            Position objectPosition =
                    tablebase.positionForState(
                            state
                    );


            boolean objectBased =
                    tablebase.encodePosition(
                            objectPosition
                    ) >= 0;


            if (primitive
                    == objectBased) {

                matched++;

            } else {

                mismatches++;


                if (mismatches <= 10) {

                    System.out.println(
                            "Mismatch state "
                                    + state
                                    + ": primitive="
                                    + primitive
                                    + ", object="
                                    + objectBased
                    );
                }
            }
        }


        System.out.println();
        System.out.println(
                "Verification strong "
                        + strongColor
        );

        System.out.println(
                "Samples: "
                        + samples
        );

        System.out.println(
                "Matched: "
                        + matched
        );

        System.out.println(
                "Mismatches: "
                        + mismatches
        );


        if (mismatches != 0) {

            throw new IllegalStateException(
                    "Primitive KQRK legality did not match the existing tablebase encoder."
            );
        }


        System.out.println(
                "PRIMITIVE LEGALITY VERIFICATION PASSED"
        );
    }
}

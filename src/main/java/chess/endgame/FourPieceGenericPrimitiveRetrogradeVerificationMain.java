package main.java.chess.endgame;

import main.java.chess.model.Color;
import main.java.chess.model.PieceType;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;


/**
 * Milestone 17 full-solver correctness gate.
 *
 * Builds KQRK with:
 *
 *     1. the new generic Tier-0 primitive retrograde builder
 *     2. the already-proven specialized KQRK primitive builder
 *
 * and compares:
 *
 *     legal-state count
 *     WIN count
 *     LOSS count
 *     DRAW count
 *     complete WDL + DTM SHA-256 digest
 *
 * The two full result arrays are deliberately not retained at the same
 * time. This keeps the memory footprint substantially lower.
 *
 * Optional argument:
 *
 *     white
 *     black
 *
 * Default:
 *
 *     white
 */
public final class FourPieceGenericPrimitiveRetrogradeVerificationMain {

    private FourPieceGenericPrimitiveRetrogradeVerificationMain() {

    }


    public static void main(
            String[] args
    ) {

        Color strongColor =
                args.length > 0
                        && args[0].equalsIgnoreCase(
                        "black"
                )
                        ? Color.BLACK
                        : Color.WHITE;


        boolean strongIsWhite =
                strongColor
                        == Color.WHITE;


        FourPieceMaterialClass material =
                FourPieceMaterialClass.sameSide(
                        PieceType.QUEEN,
                        PieceType.ROOK
                );


        System.out.println(
                "Generic/full KQRK retrograde verification"
        );

        System.out.println(
                "========================================"
        );

        System.out.println(
                "Strong color: "
                        + strongColor
        );


        // =====================================================
        // GENERIC BUILD
        // =====================================================

        FourPieceGenericPrimitiveRetrogradeBuilder
                genericBuilder =
                new FourPieceGenericPrimitiveRetrogradeBuilder(
                        material,
                        strongIsWhite
                );


        FourPieceGenericPrimitiveRetrogradeBuilder.Result
                generic =
                genericBuilder.build();


        Summary genericSummary =
                summarizeGeneric(
                        generic
                );


        System.out.println();

        System.out.println(
                "Generic complete-table digest: "
                        + genericSummary.digest()
        );


        /*
         * Release the enormous result arrays before starting the reference
         * build. The Summary retains only scalar counts and the digest.
         */
        generic =
                null;

        genericBuilder =
                null;


        System.gc();


        // =====================================================
        // SPECIALIZED REFERENCE BUILD
        // =====================================================

        System.out.println();

        System.out.println(
                "Building specialized KQRK reference..."
        );


        KqrkPrimitiveRetrogradeBuilder
                specializedBuilder =
                new KqrkPrimitiveRetrogradeBuilder(
                        strongColor
                );


        KqrkPrimitiveRetrogradeBuilder.Result
                specialized =
                specializedBuilder.build();


        Summary specializedSummary =
                summarizeSpecialized(
                        specialized
                );


        System.out.println();

        System.out.println(
                "Specialized complete-table digest: "
                        + specializedSummary.digest()
        );


        // =====================================================
        // EXACT COMPARISON
        // =====================================================

        System.out.println();

        System.out.println(
                "Complete KQRK comparison"
        );

        System.out.println(
                "Generic legal states: "
                        + genericSummary.legalStates()
        );

        System.out.println(
                "Specialized legal states: "
                        + specializedSummary.legalStates()
        );

        System.out.println(
                "Generic WIN: "
                        + genericSummary.wins()
        );

        System.out.println(
                "Specialized WIN: "
                        + specializedSummary.wins()
        );

        System.out.println(
                "Generic LOSS: "
                        + genericSummary.losses()
        );

        System.out.println(
                "Specialized LOSS: "
                        + specializedSummary.losses()
        );

        System.out.println(
                "Generic DRAW: "
                        + genericSummary.draws()
        );

        System.out.println(
                "Specialized DRAW: "
                        + specializedSummary.draws()
        );

        System.out.println(
                "Full WDL/DTM digest match: "
                        + genericSummary.digest()
                        .equals(
                                specializedSummary.digest()
                        )
        );


        if (genericSummary.legalStates()
                != specializedSummary.legalStates()
                ||
                genericSummary.wins()
                        != specializedSummary.wins()
                ||
                genericSummary.losses()
                        != specializedSummary.losses()
                ||
                genericSummary.draws()
                        != specializedSummary.draws()
                ||
                !genericSummary.digest()
                        .equals(
                                specializedSummary.digest()
                        )) {

            throw new IllegalStateException(
                    "Generic KQRK retrograde does not exactly match the specialized KQRK solution."
            );
        }


        System.out.println();

        System.out.println(
                "GENERIC KQRK COMPLETE WDL/DTM MATCH PASSED"
        );

        System.out.println(
                "GENERIC TIER-0 RETROGRADE ARCHITECTURE VERIFIED"
        );
    }


    private static Summary summarizeGeneric(
            FourPieceGenericPrimitiveRetrogradeBuilder.Result result
    ) {

        return new Summary(
                result.legalStates(),
                result.wins(),
                result.losses(),
                result.draws(),
                digest(
                        result.outcome(),
                        result.distance()
                )
        );
    }


    private static Summary summarizeSpecialized(
            KqrkPrimitiveRetrogradeBuilder.Result result
    ) {

        return new Summary(
                result.legalStates(),
                result.wins(),
                result.losses(),
                result.draws(),
                digest(
                        result.outcome(),
                        result.distance()
                )
        );
    }


    /**
     * Hash every outcome byte and every DTM short.
     *
     * Shorts are encoded deterministically as two bytes in big-endian
     * order so the comparison does not depend on native machine endianness.
     */
    private static String digest(
            byte[] outcome,
            short[] distance
    ) {

        if (outcome == null
                || distance == null
                || outcome.length
                != distance.length) {

            throw new IllegalArgumentException(
                    "Outcome/distance arrays are invalid."
            );
        }


        final MessageDigest digest;


        try {

            digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

        } catch (NoSuchAlgorithmException exception) {

            throw new IllegalStateException(
                    "SHA-256 is unavailable.",
                    exception
            );
        }


        /*
         * Feed the complete outcome array directly.
         */
        digest.update(
                outcome
        );


        /*
         * Stream the short[] through a modest reusable byte buffer rather
         * than allocating a second ~67 MB byte representation.
         */
        byte[] buffer =
                new byte[
                        16 * 1024
                        ];


        int bufferIndex =
                0;


        for (short value :
                distance) {

            buffer[bufferIndex++] =
                    (byte) (
                            (value >>> 8)
                                    & 0xFF
                    );


            buffer[bufferIndex++] =
                    (byte) (
                            value
                                    & 0xFF
                    );


            if (bufferIndex
                    == buffer.length) {

                digest.update(
                        buffer
                );

                bufferIndex =
                        0;
            }
        }


        if (bufferIndex > 0) {

            digest.update(
                    buffer,
                    0,
                    bufferIndex
            );
        }


        return HexFormat.of()
                .formatHex(
                        digest.digest()
                );
    }


    private record Summary(
            long legalStates,
            long wins,
            long losses,
            long draws,
            String digest
    ) {

    }
}
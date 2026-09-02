package main.java.chess.endgame;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;


/**
 * Milestone 37 build/verification entry point.
 *
 * Default:
 *
 *     solve canonical strong-WHITE KQPK
 *
 * Optional:
 *
 *     "black" -> solve the color-reversed strong-BLACK orientation
 *
 * M37 intentionally keeps the solved tablebase in memory only.  Persistence
 * and runtime routing follow after the exact solve itself has been validated.
 */
public final class FourPieceTierOneKqpkRetrogradeBuildMain {

    private FourPieceTierOneKqpkRetrogradeBuildMain() {
    }


    public static void main(
            String[] args
    ) throws Exception {

        boolean strongIsWhite =
                parseStrongColor(
                        args
                );


        System.out.println(
                "Dependency-aware KQPK retrograde build"
        );

        System.out.println(
                "======================================"
        );

        System.out.println(
                "Strong side: "
                        + (strongIsWhite
                        ? "WHITE"
                        : "BLACK")
        );

        System.out.println();


        FourPieceTierOneKqpkRetrogradeBuilder builder =
                new FourPieceTierOneKqpkRetrogradeBuilder(
                        strongIsWhite
                );


        FourPieceTierOneKqpkRetrogradeBuilder.Result result =
                builder.build();


        verifyResult(
                result
        );


        System.out.println();

        System.out.println(
                "KQPK exact result"
        );

        System.out.println(
                "================="
        );

        System.out.println(
                "Material: "
                        + result.material()
                        .displayName()
        );

        System.out.println(
                "Strong side: "
                        + (result.strongIsWhite()
                        ? "WHITE"
                        : "BLACK")
        );

        System.out.println(
                "Legal states: "
                        + result.legalStates()
        );

        System.out.println(
                "WIN: "
                        + result.wins()
        );

        System.out.println(
                "LOSS: "
                        + result.losses()
        );

        System.out.println(
                "DRAW: "
                        + result.draws()
        );

        System.out.println(
                "Maximum DTM: "
                        + result.maximumDistance()
        );

        System.out.printf(
                "Build time: %.3f sec%n",
                result.buildMillis()
                        / 1000.0
        );

        System.out.println();

        System.out.println(
                "Graph/dependency accounting"
        );

        System.out.println(
                "  Same-class edges scanned: "
                        + result.sameClassEdges()
        );

        System.out.println(
                "  Three-piece boundary edges: "
                        + result.threePieceEdges()
        );

        System.out.println(
                "  Promotion boundary edges: "
                        + result.promotionEdges()
        );

        System.out.println(
                "  External WIN children: "
                        + result.externalWinChildren()
        );

        System.out.println(
                "  External LOSS children: "
                        + result.externalLossChildren()
        );

        System.out.println(
                "  External DRAW children: "
                        + result.externalDrawChildren()
        );

        System.out.println(
                "  Predecessor edges processed: "
                        + result.predecessorEdgesProcessed()
        );

        System.out.println();

        System.out.println(
                "Digest: "
                        + digest(
                        result.outcome(),
                        result.distance()
                )
        );

        System.out.println();

        System.out.println(
                "DEPENDENCY-AWARE KQPK RETROGRADE BUILD PASSED"
        );

        System.out.println(
                "NEXT: KQPK EXACT VALIDATION / COLOR-SYMMETRY GATE"
        );
    }


    private static void verifyResult(
            FourPieceTierOneKqpkRetrogradeBuilder.Result result
    ) {

        if (result.material()
                .buildTier() != 1) {

            throw new IllegalStateException(
                    "KQPK result is not Tier 1."
            );
        }


        if (result.legalStates()
                <= 0) {

            throw new IllegalStateException(
                    "KQPK solve produced no legal states."
            );
        }


        if (result.wins()
                <= 0) {

            throw new IllegalStateException(
                    "KQPK solve produced no WIN states."
            );
        }


        if (result.losses()
                <= 0) {

            throw new IllegalStateException(
                    "KQPK solve produced no LOSS states."
            );
        }


        if (result.wins()
                + result.losses()
                + result.draws()
                != result.legalStates()) {

            throw new IllegalStateException(
                    "KQPK WDL counts do not balance."
            );
        }


        /*
         * Exercise the existing generic tablebase container constructor.
         * Persistence is deliberately deferred, but M37 must prove that the
         * solved arrays already fit the generic four-piece runtime shape.
         */
        FourPieceGenericTablebase tablebase =
                result.toTablebase();


        if (tablebase.legalStates()
                != result.legalStates()
                || tablebase.wins()
                != result.wins()
                || tablebase.losses()
                != result.losses()
                || tablebase.draws()
                != result.draws()
                || tablebase.maximumDistance()
                != result.maximumDistance()) {

            throw new IllegalStateException(
                    "KQPK generic tablebase container does not preserve result metadata."
            );
        }
    }


    private static boolean parseStrongColor(
            String[] args
    ) {

        if (args.length == 0) {

            return true;
        }


        if (args.length != 1) {

            throw new IllegalArgumentException(
                    "Usage: FourPieceTierOneKqpkRetrogradeBuildMain [white|black]"
            );
        }


        if ("white".equalsIgnoreCase(
                args[0]
        )) {

            return true;
        }


        if ("black".equalsIgnoreCase(
                args[0]
        )) {

            return false;
        }


        throw new IllegalArgumentException(
                "Strong side must be 'white' or 'black'."
        );
    }


    private static String digest(
            byte[] outcome,
            short[] distance
    ) throws NoSuchAlgorithmException {

        MessageDigest digest =
                MessageDigest.getInstance(
                        "SHA-256"
                );


        digest.update(
                outcome
        );


        byte[] pair =
                new byte[2];


        for (short value :
                distance) {

            pair[0] =
                    (byte) (value >>> 8);

            pair[1] =
                    (byte) value;

            digest.update(
                    pair
            );
        }


        return HexFormat.of()
                .formatHex(
                        digest.digest()
                );
    }
}

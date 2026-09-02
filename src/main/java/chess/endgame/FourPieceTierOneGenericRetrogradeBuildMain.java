package main.java.chess.endgame;

import main.java.chess.model.PieceType;


/**
 * Milestone 44 build gate.
 *
 * Phase A:
 *   Rebuild KQPK through the NEW generic Tier-1 retrograde builder and require
 *   the exact M37 content digest. This proves the generic builder preserves
 *   the already-validated KQPK solution.
 *
 * Phase B:
 *   Solve KRPK in canonical strong-WHITE orientation. This is the first new
 *   Tier-1 family produced by the generalized architecture.
 *
 * KRPK is not persisted by this milestone. Exact color-symmetry/Bellman
 * validation comes next, before we treat the new asset as production-ready.
 */
public final class FourPieceTierOneGenericRetrogradeBuildMain {

    private static final String EXPECTED_KQPK_DIGEST =
            "7eaacc1e82deaf4e5a4b1ef8c0b9abd6304094f3f7c78a18060ae7769b8fbffb";


    private FourPieceTierOneGenericRetrogradeBuildMain() {
    }


    public static void main(
            String[] args
    ) throws Exception {

        System.out.println(
                "Generic Tier-1 dependency-aware retrograde gate"
        );

        System.out.println(
                "=============================================="
        );


        verifyKqpkRegression();


        /*
         * Encourage release of the ~100 MiB KQPK result arrays before the
         * first genuinely new Tier-1 solve begins.
         */
        System.gc();


        buildKrpk();


        System.out.println();

        System.out.println(
                "GENERIC TIER-1 RETROGRADE GATE PASSED"
        );

        System.out.println(
                "NEXT: KRPK EXACT COLOR-SYMMETRY / BELLMAN VALIDATION"
        );
    }


    private static void verifyKqpkRegression()
            throws Exception {

        System.out.println();

        System.out.println(
                "Phase A — generic KQPK regression"
        );

        System.out.println(
                "---------------------------------"
        );


        FourPieceMaterialClass material =
                FourPieceMaterialClass.sameSide(
                        PieceType.QUEEN,
                        PieceType.PAWN
                );


        FourPieceTierOneRetrogradeBuilder.Result result =
                new FourPieceTierOneRetrogradeBuilder(
                        material,
                        true
                ).build();


        FourPieceGenericTablebase tablebase =
                result.toTablebase();


        String digest =
                FourPieceGenericTablebaseCodec.contentDigest(
                        tablebase
                );


        printResult(
                result,
                digest
        );


        if (!EXPECTED_KQPK_DIGEST.equals(
                digest
        )) {

            throw new IllegalStateException(
                    "Generic KQPK regression digest mismatch.\nExpected: "
                            + EXPECTED_KQPK_DIGEST
                            + "\nActual:   "
                            + digest
            );
        }


        System.out.println(
                "  exact M37 digest match: PASSED"
        );
    }


    private static void buildKrpk()
            throws Exception {

        System.out.println();

        System.out.println(
                "Phase B — first new generic Tier-1 solve: KRPK"
        );

        System.out.println(
                "---------------------------------------------"
        );


        FourPieceMaterialClass material =
                FourPieceMaterialClass.sameSide(
                        PieceType.ROOK,
                        PieceType.PAWN
                );


        FourPieceTierOneRetrogradeBuilder.Result result =
                new FourPieceTierOneRetrogradeBuilder(
                        material,
                        true
                ).build();


        String digest =
                FourPieceGenericTablebaseCodec.contentDigest(
                        result.toTablebase()
                );


        printResult(
                result,
                digest
        );


        if (result.legalStates()
                <= 0) {

            throw new IllegalStateException(
                    "KRPK solve produced no legal states."
            );
        }


        if (result.wins()
                + result.losses()
                + result.draws()
                != result.legalStates()) {

            throw new IllegalStateException(
                    "KRPK result counts do not balance."
            );
        }


        System.out.println(
                "  internally balanced exact fixpoint: PASSED"
        );

        System.out.println(
                "  NOTE: KRPK is solved but not yet promoted to a persisted "
                        + "runtime asset until the M45 independent validation gate."
        );
    }


    private static void printResult(
            FourPieceTierOneRetrogradeBuilder.Result result,
            String digest
    ) {

        System.out.println();

        System.out.println(
                "Result — "
                        + result.material().displayName()
        );

        System.out.printf(
                "  legal: %,d%n",
                result.legalStates()
        );

        System.out.printf(
                "  WIN:   %,d%n",
                result.wins()
        );

        System.out.printf(
                "  LOSS:  %,d%n",
                result.losses()
        );

        System.out.printf(
                "  DRAW:  %,d%n",
                result.draws()
        );

        System.out.println(
                "  max DTM: "
                        + result.maximumDistance()
        );

        System.out.printf(
                "  build: %.3f sec%n",
                result.buildMillis()
                        / 1000.0
        );

        System.out.printf(
                "  same-class edges: %,d%n",
                result.sameClassEdges()
        );

        System.out.printf(
                "  three-piece edges: %,d%n",
                result.threePieceEdges()
        );

        System.out.printf(
                "  promotion edges: %,d%n",
                result.promotionEdges()
        );

        System.out.printf(
                "  predecessor edges processed: %,d%n",
                result.predecessorEdgesProcessed()
        );

        System.out.println(
                "  digest: "
                        + digest
        );
    }
}

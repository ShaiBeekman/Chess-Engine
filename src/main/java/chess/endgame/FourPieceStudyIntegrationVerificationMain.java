package main.java.chess.endgame;

import main.java.chess.model.PieceType;

import java.util.Random;


/**
 * M63D no-rebuild integration gate.
 *
 * Verifies:
 *   1. the study selector exposes all 30 canonical four-piece families;
 *   2. SAME_SIDE and SPLIT are both first-class selection paths;
 *   3. an actual KQ-KR 2-v-2 root is generated and recognized as an exact WIN
 *      by the unified runtime facade.
 */
public final class FourPieceStudyIntegrationVerificationMain {

    private static final int SELECTION_SAMPLES =
            20_000;

    private FourPieceStudyIntegrationVerificationMain() {
    }


    public static void main(
            String[] args
    ) {

        FourPieceStudyPositionGenerator generator =
                new FourPieceStudyPositionGenerator();

        Random selectionRandom =
                new Random(
                        0x4D363344L
                );


        System.out.println(
                "M63D four-piece Endgame Study integration gate"
        );
        System.out.println(
                "==============================================="
        );
        System.out.println(
                "Generator BUILD_ID: "
                        + FourPieceStudyPositionGenerator.BUILD_ID
        );
        System.out.println(
                "Exact facade BUILD_ID: "
                        + ExactEndgameTablebase.BUILD_ID
        );


        if (generator.catalog().size()
                != 30) {

            throw new IllegalStateException(
                    "Expected the complete 30-family four-piece catalog."
            );
        }


        int sameSide =
                0;

        int split =
                0;


        for (int i = 0;
             i < SELECTION_SAMPLES;
             i++) {

            FourPieceMaterialClass material =
                    generator.randomMaterial(
                            selectionRandom
                    );


            if (material.distribution()
                    == FourPieceMaterialClass.Distribution.SAME_SIDE) {

                sameSide++;

            } else {

                split++;
            }
        }


        System.out.println();
        System.out.println(
                "Material-selection coverage"
        );
        System.out.println(
                "---------------------------"
        );
        System.out.println(
                "Canonical families: "
                        + generator.catalog().size()
        );
        System.out.println(
                "Selection samples: "
                        + SELECTION_SAMPLES
        );
        System.out.println(
                "SAME_SIDE selections: "
                        + sameSide
        );
        System.out.println(
                "SPLIT selections: "
                        + split
        );


        if (sameSide == 0
                || split == 0) {

            throw new IllegalStateException(
                    "Study selector failed to expose both SAME_SIDE and SPLIT."
            );
        }


        System.out.println(
                "SAME_SIDE / SPLIT selection: PASSED"
        );


        FourPieceMaterialClass splitFixture =
                FourPieceMaterialClass.split(
                        PieceType.QUEEN,
                        PieceType.ROOK
                );


        ExactEndgameTablebase tablebase =
                ExactEndgameTablebase.tierZeroCatalog();


        System.out.println();
        System.out.println(
                "Live 2-v-2 exact root"
        );
        System.out.println(
                "---------------------"
        );
        System.out.println(
                "Material: "
                        + splitFixture.assetStem()
        );


        FourPieceStudyPositionGenerator.StudyRoot root =
                generator.generateWinningStudy(
                        splitFixture,
                        tablebase,
                        new Random(
                                0x4B512D4B52L
                        )
                );


        ExactEndgameTablebase.Probe probe =
                tablebase.probe(
                        root.position()
                );


        System.out.println(
                "Generated after attempts: "
                        + root.attempts()
        );
        System.out.println(
                "Outcome: "
                        + probe.outcome()
        );
        System.out.println(
                "DTM: "
                        + probe.mateDistance()
        );


        if (!root.split()) {

            throw new IllegalStateException(
                    "Generated fixture is not SPLIT."
            );
        }


        if (probe.outcome()
                != ExactEndgameTablebase.Outcome.WIN) {

            throw new IllegalStateException(
                    "Unified runtime did not recognize generated KQ-KR as WIN."
            );
        }


        if (tablebase.bestMoves(
                root.position()
        ).isEmpty()) {

            throw new IllegalStateException(
                    "Generated KQ-KR study root is terminal."
            );
        }


        System.out.println(
                "Unified runtime 2-v-2 probe: PASSED"
        );

        System.out.println();
        System.out.println(
                "M63D FOUR-PIECE ENDGAME STUDY INTEGRATION PASSED"
        );
    }
}

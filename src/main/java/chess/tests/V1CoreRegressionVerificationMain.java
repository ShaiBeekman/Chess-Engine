package main.java.chess.tests;

import main.java.chess.model.FenCodec;
import main.java.chess.model.Position;


/**
 * M87 headless wrapper around the existing v1.0 correctness harnesses.
 *
 * This class intentionally does NOT launch ChessWindow. The normal application
 * Main runs these same harnesses and then opens Swing; a release orchestrator
 * needs a deterministic process that exits when the tests finish.
 *
 * No production chess behavior is changed here.
 */
public final class V1CoreRegressionVerificationMain {

    public static final String BUILD_ID =
            "M87-HEADLESS-CORE-REGRESSION-V1";

    private static final String START_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";


    private V1CoreRegressionVerificationMain() {
    }


    public static void main(
            String[] args
    ) {

        if (args.length != 0) {
            throw new IllegalArgumentException(
                    "Usage: V1CoreRegressionVerificationMain"
            );
        }


        System.out.println(
                "M87 headless core v1.0 regression gate"
        );

        System.out.println(
                "======================================"
        );

        System.out.println(
                "BUILD_ID: "
                        + BUILD_ID
        );


        Position startingPosition =
                FenCodec.parse(
                        START_FEN
                );


        /*
         * These are the existing v1.0 correctness harnesses used by Main.
         * Any failed assertion throws and gives this process a nonzero exit.
         */
        TerminalStateTestHarness.run(
                startingPosition
        );

        SpecialRuleTestHarness.run(
                startingPosition
        );

        GraphInvariantTestHarness.run(
                startingPosition
        );

        IncrementalPropagationTestHarness.run(
                startingPosition
        );

        TacticalSolvingTestHarness.run(
                startingPosition
        );

        ExplorationFairnessTestHarness.run(
                startingPosition
        );

        FenCodecTestHarness.run();


        System.out.println();

        System.out.println(
                "======================================"
        );

        System.out.println(
                "M87 HEADLESS CORE REGRESSION GATE PASSED"
        );

        System.out.println(
                "======================================"
        );
    }
}

package main.java.chess.engine;

import main.java.chess.model.FenCodec;
import main.java.chess.model.Move;
import main.java.chess.model.Position;
import main.java.chess.model.Square;
import main.java.chess.rules.MoveGenerator;

import java.util.List;


/**
 * M70 non-Swing regression gate for the live Dovetail telemetry bridge.
 *
 * Verifies that ChessEngine exposes the M69 hybrid scheduler's live state
 * without changing the search itself.
 */
public final class DovetailTelemetryVerificationMain {

    private static final String START_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private static final int FIRST_WORK =
            2_000;

    private static final int FOCUSED_WORK =
            1_000;

    private static final MoveGenerator MOVE_GENERATOR =
            new MoveGenerator();


    private DovetailTelemetryVerificationMain() {
    }


    public static void main(
            String[] args
    ) {

        System.out.println(
                "Dovetail live-telemetry verification gate"
        );

        System.out.println(
                "========================================"
        );

        System.out.println(
                "Engine telemetry BUILD_ID: "
                        + ChessEngine.TELEMETRY_BUILD_ID
        );


        verifyHybridTelemetry();
        verifyFocusFairness();
        verifyCanonicalTranspositionTelemetry();


        System.out.println();
        System.out.println(
                "========================================"
        );

        System.out.println(
                "DOVETAIL LIVE TELEMETRY GATE PASSED"
        );

        System.out.println(
                "========================================"
        );
    }


    private static void verifyHybridTelemetry() {

        System.out.println();
        System.out.println(
                "Hybrid live counters"
        );

        System.out.println(
                "--------------------"
        );


        Position start =
                FenCodec.parse(
                        START_FEN
                );


        ChessEngine engine =
                new ChessEngine(
                        1
                );


        engine.analyze(
                start
        );


        ChessEngine.SearchTelemetry before =
                engine.getSearchTelemetry();


        engine.advanceExplorationWork(
                FIRST_WORK
        );


        ChessEngine.SearchTelemetry after =
                engine.getSearchTelemetry();


        require(
                after.graphNodes()
                        > before.graphNodes(),
                "Graph did not grow during hybrid exploration."
        );

        require(
                after.workUnits()
                        == FIRST_WORK,
                "Engine work-unit telemetry mismatch."
        );

        require(
                after.walkerSteps()
                        > 0L,
                "Walker lane reported zero work."
        );

        require(
                after.coverageSteps()
                        > 0L,
                "Coverage lane reported zero work."
        );

        require(
                after.walkerSteps()
                        + after.coverageSteps()
                        == after.workUnits(),
                "Lane counters do not conserve total work."
        );

        require(
                Math.abs(
                        after.walkerSteps()
                                - after.coverageSteps()
                ) <= 1L,
                "Hybrid lane fairness drifted by more than one step."
        );

        require(
                after.walkers()
                        > 1,
                "Diagonal scheduler did not create multiple walkers."
        );

        require(
                after.activeWalkers()
                        > 0,
                "No active walkers remain."
        );

        require(
                after.activeWalkers()
                        <= after.walkers(),
                "Active-walker count exceeds total walkers."
        );

        require(
                after.maximumWalkerDepth()
                        > 0,
                "Maximum walker depth did not grow."
        );

        require(
                after.globalQueueSize()
                        >= 0,
                "Global queue telemetry is invalid."
        );

        require(
                after.focusQueueSize()
                        >= 0,
                "Focus queue telemetry is invalid."
        );


        printTelemetry(
                after
        );

        System.out.println(
                "  lane accounting / depth / walkers: PASSED"
        );
    }


    private static void verifyFocusFairness() {

        System.out.println();
        System.out.println(
                "Selected-line focus fairness"
        );

        System.out.println(
                "----------------------------"
        );


        Position start =
                FenCodec.parse(
                        START_FEN
                );


        ChessEngine engine =
                new ChessEngine(
                        1
                );


        engine.analyze(
                start
        );

        engine.advanceExplorationWork(
                500
        );

        engine.setExplorationFocus(
                start
        );


        ChessEngine.SearchTelemetry before =
                engine.getSearchTelemetry();


        engine.advanceExplorationWork(
                FOCUSED_WORK
        );


        ChessEngine.SearchTelemetry after =
                engine.getSearchTelemetry();


        long walkerDelta =
                after.walkerSteps()
                        - before.walkerSteps();

        long coverageDelta =
                after.coverageSteps()
                        - before.coverageSteps();


        require(
                walkerDelta > 0L,
                "Selected-line focus starved line walkers."
        );

        require(
                coverageDelta > 0L,
                "Selected-line focus starved node coverage."
        );

        require(
                walkerDelta + coverageDelta
                        == FOCUSED_WORK,
                "Focused work was not fully represented by the two lanes."
        );


        System.out.println(
                "  focused walker steps:   "
                        + String.format(
                        "%,d",
                        walkerDelta
                )
        );

        System.out.println(
                "  focused coverage steps: "
                        + String.format(
                        "%,d",
                        coverageDelta
                )
        );

        System.out.println(
                "  focus bias without starvation: PASSED"
        );
    }


    private static void verifyCanonicalTranspositionTelemetry() {

        System.out.println();
        System.out.println(
                "Canonical transposition telemetry"
        );

        System.out.println(
                "---------------------------------"
        );


        Position start =
                FenCodec.parse(
                        START_FEN
                );


        ChessEngine engine =
                new ChessEngine(
                        0
                );


        engine.analyze(
                start
        );


        commitPath(
                engine,
                start,
                List.of(
                        "g1f3",
                        "g8f6",
                        "b1c3",
                        "b8c6"
                )
        );


        commitPath(
                engine,
                start,
                List.of(
                        "b1c3",
                        "b8c6",
                        "g1f3",
                        "g8f6"
                )
        );


        ChessEngine.SearchTelemetry telemetry =
                engine.getSearchTelemetry();


        require(
                telemetry.transpositionNodes()
                        >= 1,
                "Known commuting move paths did not register a transposition node."
        );

        require(
                telemetry.transpositionLinks()
                        >= 1L,
                "Known commuting move paths did not register an extra parent link."
        );


        System.out.println(
                "  transposition nodes: "
                        + String.format(
                        "%,d",
                        telemetry.transpositionNodes()
                )
        );

        System.out.println(
                "  extra parent links:  "
                        + String.format(
                        "%,d",
                        telemetry.transpositionLinks()
                )
        );

        System.out.println(
                "  canonical graph merge accounting: PASSED"
        );
    }


    private static void commitPath(
            ChessEngine engine,
            Position start,
            List<String> uciMoves
    ) {

        Position parent =
                start;


        for (String uci :
                uciMoves) {

            Move move =
                    findMove(
                            parent,
                            uci
                    );


            if (move == null) {

                throw new IllegalStateException(
                        "Could not find legal move "
                                + uci
                );
            }


            Position child =
                    parent.makeMove(
                            move
                    );


            require(
                    engine.ensureManualContinuation(
                            parent,
                            child
                    ),
                    "Could not commit manual graph continuation "
                            + uci
            );


            parent =
                    child;
        }
    }


    private static Move findMove(
            Position position,
            String uci
    ) {

        if (uci == null
                || uci.length() != 4) {

            throw new IllegalArgumentException(
                    "Verification UCI move must contain four characters."
            );
        }


        Square from =
                square(
                        uci.substring(
                                0,
                                2
                        )
                );

        Square to =
                square(
                        uci.substring(
                                2,
                                4
                        )
                );


        for (Move move :
                MOVE_GENERATOR.generateLegalMoves(
                        position
                )) {

            if (move.from().equals(
                    from
            )
                    && move.to().equals(
                    to
            )
                    && move.promotion() == null) {

                return move;
            }
        }


        return null;
    }


    private static Square square(
            String algebraic
    ) {

        return new Square(
                algebraic.charAt(0) - 'a',
                algebraic.charAt(1) - '1'
        );
    }


    private static void printTelemetry(
            ChessEngine.SearchTelemetry telemetry
    ) {

        System.out.println(
                "  positions:       "
                        + String.format(
                        "%,d",
                        telemetry.graphNodes()
                )
        );

        System.out.println(
                "  work:            "
                        + String.format(
                        "%,d",
                        telemetry.workUnits()
                )
        );

        System.out.println(
                "  line walkers:    "
                        + String.format(
                        "%,d",
                        telemetry.walkerSteps()
                )
        );

        System.out.println(
                "  node coverage:   "
                        + String.format(
                        "%,d",
                        telemetry.coverageSteps()
                )
        );

        System.out.println(
                "  walkers:         "
                        + telemetry.activeWalkers()
                        + "/"
                        + telemetry.walkers()
        );

        System.out.println(
                "  deepest line:    "
                        + telemetry.maximumWalkerDepth()
        );

        System.out.println(
                "  path revisits:   "
                        + String.format(
                        "%,d",
                        telemetry.walkerPathRevisits()
                )
        );

        System.out.println(
                "  transpositions:  "
                        + String.format(
                        "%,d",
                        telemetry.transpositionLinks()
                )
        );

        System.out.println(
                "  global queue:    "
                        + String.format(
                        "%,d",
                        telemetry.globalQueueSize()
                )
        );

        System.out.println(
                "  focus queue:     "
                        + String.format(
                        "%,d",
                        telemetry.focusQueueSize()
                )
        );
    }


    private static void require(
            boolean condition,
            String message
    ) {

        if (!condition) {

            throw new IllegalStateException(
                    message
            );
        }
    }
}

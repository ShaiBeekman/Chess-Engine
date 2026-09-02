package main.java.chess.engine;

import main.java.chess.model.FenCodec;
import main.java.chess.model.Position;
import main.java.chess.rules.MoveGenerator;
import main.java.chess.search.ExplorationScheduler;
import main.java.chess.search.PositionGraph;
import main.java.chess.search.PositionNode;

import java.util.List;


/**
 * M71 regression gate.
 *
 * Proves that Dovetail and Hybrid are genuinely different search modes:
 *
 * DOVETAIL = diagonal persistent line walkers only
 * HYBRID   = those same walkers + fair node/edge coverage
 */
public final class EngineModeSeparationVerificationMain {

    private static final String START_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private static final int WORK_UNITS =
            2_000;


    private EngineModeSeparationVerificationMain() {
    }


    public static void main(
            String[] args
    ) {

        int workUnits =
                args.length > 0
                        && "quick".equalsIgnoreCase(args[0])
                        ? 600
                        : WORK_UNITS;


        System.out.println(
                "Dovetail / Hybrid engine-mode separation gate"
        );

        System.out.println(
                "============================================="
        );

        System.out.println(
                "Scheduler mode BUILD_ID: "
                        + ExplorationScheduler.MODE_BUILD_ID
        );

        System.out.println(
                "Engine mode BUILD_ID:    "
                        + ChessEngine.MODE_BUILD_ID
        );

        System.out.println(
                "Work units per mode:     "
                        + workUnits
        );

        System.out.println();


        verifySharedDiagonalDefinition();

        verifyPureDovetail(
                workUnits
        );

        verifyHybrid(
                workUnits
        );

        verifyChessEngineRouting(
                workUnits / 2
        );


        System.out.println();

        System.out.println(
                "============================================="
        );

        System.out.println(
                "DOVETAIL / HYBRID MODE SEPARATION PASSED"
        );

        System.out.println(
                "============================================="
        );
    }


    // =========================================================
    // Shared Dovetail definition
    // =========================================================

    private static void verifySharedDiagonalDefinition() {

        System.out.println(
                "Shared diagonal walker definition"
        );

        System.out.println(
                "---------------------------------"
        );


        List<String> expected =
                List.of(
                        "A1",
                        "B1",
                        "A2",
                        "C1",
                        "B2",
                        "A3",
                        "D1",
                        "C2",
                        "B3",
                        "A4"
                );


        List<String> actual =
                ExplorationScheduler
                        .previewWalkerScheduleLabels(
                                expected.size()
                        );


        require(
                expected.equals(
                        actual
                ),
                "Diagonal schedule changed: "
                        + actual
        );


        System.out.println(
                "  "
                        + String.join(
                        ", ",
                        actual
                )
        );

        System.out.println(
                "  both modes share the same Dovetail walkers: PASSED"
        );

        System.out.println();
    }


    // =========================================================
    // Pure Dovetail
    // =========================================================

    private static void verifyPureDovetail(
            int workUnits
    ) {

        System.out.println(
                "Pure Dovetail"
        );

        System.out.println(
                "-------------"
        );


        Fixture fixture =
                createFixture(
                        ExplorationScheduler.Mode.DOVETAIL
                );


        ExplorationScheduler scheduler =
                fixture.scheduler();


        int completed =
                scheduler.advance(
                        workUnits
                );


        require(
                completed > 0,
                "Pure Dovetail performed no work."
        );


        require(
                scheduler.getMode()
                        == ExplorationScheduler.Mode.DOVETAIL,
                "Scheduler did not retain DOVETAIL mode."
        );


        require(
                scheduler.getWalkerSteps()
                        == completed,
                "Every pure-Dovetail work unit must be a walker step."
        );


        require(
                scheduler.getCoverageSteps()
                        == 0L,
                "Pure Dovetail unexpectedly performed node coverage."
        );


        require(
                scheduler.getGlobalQueueSize()
                        == 0,
                "Pure Dovetail should not maintain the Hybrid coverage queue."
        );


        require(
                scheduler.getFocusQueueSize()
                        == 0,
                "Pure Dovetail should not maintain the Hybrid focus queue."
        );


        require(
                scheduler.getWalkerCount()
                        > 1,
                "Pure Dovetail did not create multiple walkers."
        );


        require(
                scheduler.getMaximumWalkerDepth()
                        > 0,
                "Pure Dovetail did not establish line depth."
        );


        /*
         * Selecting a line must not bias pure Dovetail.
         *
         * Its defining schedule remains:
         *
         * A1, B1, A2, C1, B2, A3, ...
         */
        scheduler.setFocus(
                fixture.root()
        );


        require(
                scheduler.getFocusNode()
                        == null,
                "Pure Dovetail must not alter its diagonal schedule "
                        + "for selected-line focus."
        );


        System.out.printf(
                "  completed work: %,d%n",
                completed
        );

        System.out.printf(
                "  walker steps:   %,d%n",
                scheduler.getWalkerSteps()
        );

        System.out.printf(
                "  coverage steps: %,d%n",
                scheduler.getCoverageSteps()
        );

        System.out.printf(
                "  walkers:        %,d%n",
                scheduler.getWalkerCount()
        );

        System.out.printf(
                "  maximum depth:  %,d%n",
                scheduler.getMaximumWalkerDepth()
        );

        System.out.printf(
                "  graph nodes:    %,d%n",
                fixture.graph().size()
        );

        System.out.println(
                "  diagonal walkers only: PASSED"
        );

        System.out.println();
    }


    // =========================================================
    // Hybrid
    // =========================================================

    private static void verifyHybrid(
            int workUnits
    ) {

        System.out.println(
                "Hybrid"
        );

        System.out.println(
                "------"
        );


        Fixture fixture =
                createFixture(
                        ExplorationScheduler.Mode.HYBRID
                );


        ExplorationScheduler scheduler =
                fixture.scheduler();


        int completed =
                scheduler.advance(
                        workUnits
                );


        long walkerSteps =
                scheduler.getWalkerSteps();

        long coverageSteps =
                scheduler.getCoverageSteps();


        require(
                completed > 0,
                "Hybrid performed no work."
        );


        require(
                scheduler.getMode()
                        == ExplorationScheduler.Mode.HYBRID,
                "Scheduler did not retain HYBRID mode."
        );


        require(
                walkerSteps > 0L,
                "Hybrid walker lane performed no work."
        );


        require(
                coverageSteps > 0L,
                "Hybrid coverage lane performed no work."
        );


        require(
                walkerSteps
                        + coverageSteps
                        == completed,
                "Hybrid lane accounting does not equal completed work."
        );


        require(
                Math.abs(
                        walkerSteps
                                - coverageSteps
                ) <= 1L,
                "Hybrid lanes are no longer approximately 50/50."
        );


        require(
                scheduler.getGlobalQueueSize()
                        > 0,
                "Hybrid coverage queue is unexpectedly empty."
        );


        /*
         * Hybrid may bias the coverage lane toward a selected subtree.
         *
         * The Dovetail walker lane itself continues independently.
         */
        scheduler.setFocus(
                fixture.root()
        );


        require(
                scheduler.getFocusNode()
                        == fixture.root(),
                "Hybrid selected-line focus was not retained."
        );


        System.out.printf(
                "  completed work: %,d%n",
                completed
        );

        System.out.printf(
                "  walker steps:   %,d%n",
                walkerSteps
        );

        System.out.printf(
                "  coverage steps: %,d%n",
                coverageSteps
        );

        System.out.printf(
                "  walkers:        %,d%n",
                scheduler.getWalkerCount()
        );

        System.out.printf(
                "  maximum depth:  %,d%n",
                scheduler.getMaximumWalkerDepth()
        );

        System.out.printf(
                "  graph nodes:    %,d%n",
                fixture.graph().size()
        );

        System.out.println(
                "  Dovetail + node coverage: PASSED"
        );

        System.out.println();
    }


    // =========================================================
    // ChessEngine routing
    // =========================================================

    private static void verifyChessEngineRouting(
            int workUnits
    ) {

        System.out.println(
                "ChessEngine mode routing"
        );

        System.out.println(
                "------------------------"
        );


        Position start =
                FenCodec.parse(
                        START_FEN
                );


        ChessEngine engine =
                new ChessEngine(
                        1
                );


        // =====================================================
        // Dovetail through ChessEngine
        // =====================================================

        engine.setSearchMode(
                ChessEngine.SearchMode.DOVETAIL
        );


        engine.analyze(
                start
        );


        engine.advanceExplorationWork(
                workUnits
        );


        ExplorationScheduler dovetailScheduler =
                engine.getExplorationScheduler();


        require(
                engine.getSearchMode()
                        == ChessEngine.SearchMode.DOVETAIL,
                "ChessEngine did not retain DOVETAIL mode."
        );


        require(
                dovetailScheduler != null,
                "ChessEngine DOVETAIL created no scheduler."
        );


        require(
                dovetailScheduler.getMode()
                        == ExplorationScheduler.Mode.DOVETAIL,
                "ChessEngine routed DOVETAIL "
                        + "to the wrong scheduler mode."
        );


        require(
                dovetailScheduler.getWalkerSteps()
                        > 0L,
                "ChessEngine DOVETAIL produced no walker work."
        );


        require(
                dovetailScheduler.getCoverageSteps()
                        == 0L,
                "ChessEngine DOVETAIL leaked Hybrid coverage work."
        );


        long dovetailWalkerSteps =
                dovetailScheduler.getWalkerSteps();

        long dovetailCoverageSteps =
                dovetailScheduler.getCoverageSteps();


        // =====================================================
        // Hybrid through ChessEngine
        // =====================================================

        engine.setSearchMode(
                ChessEngine.SearchMode.HYBRID
        );


        engine.analyze(
                start
        );


        engine.advanceExplorationWork(
                workUnits
        );


        ExplorationScheduler hybridScheduler =
                engine.getExplorationScheduler();


        require(
                engine.getSearchMode()
                        == ChessEngine.SearchMode.HYBRID,
                "ChessEngine did not retain HYBRID mode."
        );


        require(
                hybridScheduler != null,
                "ChessEngine HYBRID created no scheduler."
        );


        require(
                hybridScheduler.getMode()
                        == ExplorationScheduler.Mode.HYBRID,
                "ChessEngine routed HYBRID "
                        + "to the wrong scheduler mode."
        );


        require(
                hybridScheduler.getWalkerSteps()
                        > 0L,
                "ChessEngine HYBRID produced no walker work."
        );


        require(
                hybridScheduler.getCoverageSteps()
                        > 0L,
                "ChessEngine HYBRID produced no coverage work."
        );


        System.out.printf(
                "  Dovetail: walkers %,d / coverage %,d%n",
                dovetailWalkerSteps,
                dovetailCoverageSteps
        );


        System.out.printf(
                "  Hybrid:   walkers %,d / coverage %,d%n",
                hybridScheduler.getWalkerSteps(),
                hybridScheduler.getCoverageSteps()
        );


        System.out.println(
                "  independent scheduler policy routing: PASSED"
        );

        System.out.println();
    }


    // =========================================================
    // Fixture
    // =========================================================

    private static Fixture createFixture(
            ExplorationScheduler.Mode mode
    ) {

        Position start =
                FenCodec.parse(
                        START_FEN
                );


        PositionGraph graph =
                new PositionGraph();


        MoveGenerator moveGenerator =
                new MoveGenerator();


        PositionNode root =
                graph.getOrCreateNode(
                        start
                );


        ExplorationScheduler scheduler =
                new ExplorationScheduler(
                        graph,
                        moveGenerator,
                        root,
                        mode
                );


        return new Fixture(
                graph,
                root,
                scheduler
        );
    }


    // =========================================================
    // Assertion
    // =========================================================

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


    // =========================================================
    // Fixture record
    // =========================================================

    private record Fixture(
            PositionGraph graph,
            PositionNode root,
            ExplorationScheduler scheduler
    ) {
    }
}
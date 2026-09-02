package main.java.chess.search;

import main.java.chess.model.FenCodec;
import main.java.chess.model.Move;
import main.java.chess.model.Position;
import main.java.chess.rules.MoveGenerator;

import java.util.List;

/**
 * M69 non-Swing verification gate for the hybrid Dovetail search core.
 */
public final class DovetailSearchVerificationMain {

    private static final String START_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private static final String CHECKMATE_FEN =
            "7k/6Q1/6K1/8/8/8/8/8 b - - 0 1";

    private DovetailSearchVerificationMain() {
    }

    public static void main(String[] args) {
        int workUnits =
                args.length > 0 && "quick".equalsIgnoreCase(args[0])
                        ? 600
                        : 2_000;

        System.out.println("Dovetail hybrid-search verification gate");
        System.out.println("========================================");
        System.out.println("Scheduler BUILD_ID: " + ExplorationScheduler.BUILD_ID);
        System.out.println("Work units: " + workUnits);
        System.out.println();

        verifyDiagonalSchedule();
        verifyWalkerNamingAndDepthCap();
        verifyHybridSearch(workUnits);
        verifyFocusDoesNotStarveGlobal(workUnits / 2);
        verifyTerminalRootStopsCleanly();

        System.out.println();
        System.out.println("========================================");
        System.out.println("DOVETAIL HYBRID SEARCH GATE PASSED");
        System.out.println("========================================");
    }

    private static void verifyDiagonalSchedule() {
        System.out.println("Countable diagonal walker schedule");
        System.out.println("----------------------------------");

        List<Integer> actual = ExplorationScheduler.previewWalkerSchedule(10);
        List<Integer> expected = List.of(0, 1, 0, 2, 1, 0, 3, 2, 1, 0);

        require(actual.equals(expected),
                "Diagonal walker index schedule mismatch: " + actual);

        List<String> labels = ExplorationScheduler.previewWalkerScheduleLabels(10);
        List<String> expectedLabels =
                List.of("A1", "B1", "A2", "C1", "B2", "A3", "D1", "C2", "B3", "A4");

        require(labels.equals(expectedLabels),
                "Diagonal walker label schedule mismatch: " + labels);

        System.out.println("  " + String.join(", ", labels));
        System.out.println("  exact diagonal enumeration: PASSED");
        System.out.println();
    }

    private static void verifyWalkerNamingAndDepthCap() {
        System.out.println("Walker identity / line bound");
        System.out.println("----------------------------");

        require("A".equals(DovetailWalker.labelFor(0)), "Walker 0 must be A.");
        require("Z".equals(DovetailWalker.labelFor(25)), "Walker 25 must be Z.");
        require("AA".equals(DovetailWalker.labelFor(26)), "Walker 26 must be AA.");
        require("AZ".equals(DovetailWalker.labelFor(51)), "Walker 51 must be AZ.");
        require(DovetailWalker.MAX_HALF_MOVES == 10_000,
                "Walker half-move cap must remain 10,000.");

        System.out.println("  labels A..Z, AA...: PASSED");
        System.out.println("  maximum line length: 10,000 half-moves");
        System.out.println();
    }

    private static void verifyHybridSearch(int workUnits) {
        System.out.println("Hybrid depth + coverage search");
        System.out.println("------------------------------");

        Fixture fixture = newFixture();

        long walkerBefore = fixture.scheduler().getWalkerSteps();
        long coverageBefore = fixture.scheduler().getCoverageSteps();

        int completed = fixture.scheduler().advance(workUnits);

        long walkerDelta = fixture.scheduler().getWalkerSteps() - walkerBefore;
        long coverageDelta = fixture.scheduler().getCoverageSteps() - coverageBefore;

        require(completed > 0, "Hybrid scheduler performed no work.");
        require(completed == walkerDelta + coverageDelta,
                "Work accounting mismatch.");
        require(walkerDelta > 0, "Line-walker lane made no progress.");
        require(coverageDelta > 0, "Node-coverage lane made no progress.");
        require(fixture.scheduler().getWalkerCount() >= 2,
                "Diagonal scheduler did not create multiple walkers.");
        require(fixture.scheduler().getMaximumWalkerDepth() >= 4,
                "Walkers did not establish meaningful line depth.");
        require(fixture.graph().size() > 1,
                "Shared graph did not grow.");

        int rootWalkerVisits = 0;
        int rootDovetailVisits = 0;
        for (Move move : fixture.root().getOrCacheLegalMoves(fixture.moveGenerator())) {
            rootWalkerVisits += fixture.root().getWalkerEdgeVisitCount(move);
            rootDovetailVisits += fixture.root().getDovetailEdgeVisitCount(move);
        }

        require(rootWalkerVisits > 0,
                "Root contains no walker-edge accounting.");
        require(rootDovetailVisits > 0,
                "Root contains no coverage/dovetail accounting.");

        ExplorationScheduler.DovetailTelemetry telemetry = fixture.scheduler().telemetry();

        System.out.printf("  completed work: %,d%n", completed);
        System.out.printf("  walker steps:   %,d%n", telemetry.walkerSteps());
        System.out.printf("  coverage steps: %,d%n", telemetry.coverageSteps());
        System.out.printf("  walkers:        %,d (%d active)%n",
                telemetry.walkers(), telemetry.activeWalkers());
        System.out.printf("  maximum depth:  %,d%n", telemetry.maximumWalkerDepth());
        System.out.printf("  graph nodes:    %,d%n", telemetry.graphNodes());
        System.out.printf("  path revisits:  %,d%n", telemetry.walkerPathRevisits());
        System.out.println("  shared canonical graph / dual lanes: PASSED");
        System.out.println();
    }

    private static void verifyFocusDoesNotStarveGlobal(int workUnits) {
        System.out.println("Selected-line focus fairness");
        System.out.println("----------------------------");

        Fixture fixture = newFixture();
        fixture.scheduler().advance(Math.max(100, workUnits / 2));

        require(!fixture.root().getOutgoingEdges().isEmpty(),
                "Could not create a focus continuation.");

        PositionNode focus = fixture.root().getOutgoingEdges().get(0).getTarget();
        fixture.scheduler().setFocus(focus);

        long walkerBefore = fixture.scheduler().getWalkerSteps();
        long coverageBefore = fixture.scheduler().getCoverageSteps();

        int completed = fixture.scheduler().advance(Math.max(100, workUnits));

        long walkerDelta = fixture.scheduler().getWalkerSteps() - walkerBefore;
        long coverageDelta = fixture.scheduler().getCoverageSteps() - coverageBefore;

        require(completed > 0, "Focused scheduler performed no work.");
        require(walkerDelta > 0,
                "Focus starved the global line-walker lane.");
        require(coverageDelta > 0,
                "Focus starved the fair coverage lane.");
        require(fixture.scheduler().getFocusNode() == focus,
                "Focus node was not preserved.");

        System.out.printf("  post-focus walker steps:   %,d%n", walkerDelta);
        System.out.printf("  post-focus coverage steps: %,d%n", coverageDelta);
        System.out.println("  focus bias without global starvation: PASSED");
        System.out.println();
    }

    private static void verifyTerminalRootStopsCleanly() {
        System.out.println("Terminal-root handling");
        System.out.println("----------------------");

        Position terminal = FenCodec.parse(CHECKMATE_FEN);
        PositionGraph graph = new PositionGraph();
        MoveGenerator moveGenerator = new MoveGenerator();
        PositionNode root = graph.getOrCreateNode(terminal);
        ExplorationScheduler scheduler =
                new ExplorationScheduler(graph, moveGenerator, root);

        int completed = scheduler.advance(100);

        require(completed == 0,
                "Terminal root should not produce exploration work.");
        require(!scheduler.hasGlobalWork(),
                "Terminal root should report no remaining global work.");

        System.out.println("  terminal root produces zero work: PASSED");
    }

    private static Fixture newFixture() {
        Position start = FenCodec.parse(START_FEN);
        PositionGraph graph = new PositionGraph();
        MoveGenerator moveGenerator = new MoveGenerator();
        PositionNode root = graph.getOrCreateNode(start);
        ExplorationScheduler scheduler =
                new ExplorationScheduler(graph, moveGenerator, root);

        return new Fixture(graph, moveGenerator, root, scheduler);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private record Fixture(
            PositionGraph graph,
            MoveGenerator moveGenerator,
            PositionNode root,
            ExplorationScheduler scheduler
    ) {
    }
}

package main.java.chess.tests;

import main.java.chess.model.Position;
import main.java.chess.rules.MoveGenerator;
import main.java.chess.search.ExplorationScheduler;
import main.java.chess.search.PositionGraph;
import main.java.chess.search.PositionNode;
import main.java.chess.search.SearchEdge;

import java.util.ArrayList;
import java.util.List;


/**
 * Version 1.0 correctness suite — Phase 6.
 *
 * Verifies persistent exploration-scheduler behavior:
 *
 * 1. scheduling state persists across advance() calls
 * 2. global round-robin work reaches every queued root branch
 * 3. focus gives extra work to the selected subtree
 * 4. focus never stops global exploration
 */
public final class ExplorationFairnessTestHarness {

    private final MoveGenerator moveGenerator;

    private int passed;
    private int failed;


    public ExplorationFairnessTestHarness() {

        this.moveGenerator =
                new MoveGenerator();

        this.passed =
                0;

        this.failed =
                0;
    }


    // =========================================================
    // PUBLIC ENTRY POINT
    // =========================================================

    public static void run(
            Position initialPosition
    ) {

        if (initialPosition == null) {

            throw new IllegalArgumentException(
                    "Initial position cannot be null."
            );
        }


        ExplorationFairnessTestHarness harness =
                new ExplorationFairnessTestHarness();


        harness.runAll(
                initialPosition
        );
    }


    private void runAll(
            Position initialPosition
    ) {

        System.out.println();

        System.out.println(
                "========================================"
        );

        System.out.println(
                "Chess Engine v1.0 Correctness Tests"
        );

        System.out.println(
                "Phase 6 — Exploration Fairness"
        );

        System.out.println(
                "========================================"
        );

        System.out.println();


        testPersistentScheduling(
                initialPosition
        );

        testGlobalRoundRobinFairness(
                initialPosition
        );

        testFocusBias(
                initialPosition
        );

        testFocusDoesNotStarveGlobalWork(
                initialPosition
        );


        System.out.println();

        System.out.println(
                "========================================"
        );

        System.out.printf(
                "Tests passed: %d%n",
                passed
        );

        System.out.printf(
                "Tests failed: %d%n",
                failed
        );

        System.out.println(
                "========================================"
        );

        System.out.println();


        if (failed > 0) {

            throw new IllegalStateException(
                    "Phase 6 correctness suite failed: "
                            + failed
                            + " test(s) failed."
            );
        }
    }


    // =========================================================
    // TEST 1 — PERSISTENT SCHEDULING STATE
    // =========================================================

    private void testPersistentScheduling(
            Position initialPosition
    ) {

        System.out.println(
                "--- Persistent scheduling state ---"
        );


        SchedulerFixture fixture =
                createRootBranchFixture(
                        initialPosition
                );


        ExplorationScheduler scheduler =
                fixture.scheduler;


        int graphSizeBefore =
                fixture.graph.size();


        int firstWork =
                scheduler.advance(
                        5
                );


        int graphSizeAfterFirst =
                fixture.graph.size();


        int secondWork =
                scheduler.advance(
                        5
                );


        int graphSizeAfterSecond =
                fixture.graph.size();


        expectEquals(
                "First scheduler call completes requested work",
                5,
                firstWork
        );


        expectEquals(
                "Second scheduler call completes requested work",
                5,
                secondWork
        );


        expectTrue(
                "First scheduler call grows persistent graph",
                graphSizeAfterFirst
                        > graphSizeBefore
        );


        expectTrue(
                "Second scheduler call continues growing same graph",
                graphSizeAfterSecond
                        > graphSizeAfterFirst
        );


        expectTrue(
                "Scheduler still reports global work",
                scheduler.hasGlobalWork()
        );


        System.out.println(
                "Persistent scheduler graph nodes: "
                        + graphSizeBefore
                        + " -> "
                        + graphSizeAfterFirst
                        + " -> "
                        + graphSizeAfterSecond
        );


        System.out.println();
    }


    // =========================================================
    // TEST 2 — GLOBAL ROUND-ROBIN FAIRNESS
    // =========================================================

    private void testGlobalRoundRobinFairness(
            Position initialPosition
    ) {

        System.out.println(
                "--- Global round-robin fairness ---"
        );


        SchedulerFixture fixture =
                createRootBranchFixture(
                        initialPosition
                );


        List<PositionNode> rootChildren =
                fixture.rootChildren;


        expectTrue(
                "Starting position exposes multiple root branches",
                rootChildren.size() > 1
        );


        /*
         * The scheduler's seed queue contains every already-generated
         * expandable child of the fully expanded root.
         *
         * With no focus active, one work unit per initial child is
         * enough for every queued root branch to receive a turn before
         * any requeued node can lap it.
         */
        int requestedWork =
                rootChildren.size();


        int workDone =
                fixture.scheduler.advance(
                        requestedWork
                );


        expectEquals(
                "Global scheduler completes one work unit per root branch",
                requestedWork,
                workDone
        );


        int touched =
                countExpanded(
                        rootChildren
                );


        expectEquals(
                "Every queued root branch receives expansion work",
                rootChildren.size(),
                touched
        );


        int minimumExpansionCount =
                minimumExpansionCount(
                        rootChildren
                );


        expectTrue(
                "No initial root branch is starved",
                minimumExpansionCount >= 1
        );


        System.out.println(
                "Root branches reached: "
                        + touched
                        + " / "
                        + rootChildren.size()
        );


        System.out.println();
    }


    // =========================================================
    // TEST 3 — FOCUS BIAS
    // =========================================================

    private void testFocusBias(
            Position initialPosition
    ) {

        System.out.println(
                "--- Selected-subtree focus bias ---"
        );


        SchedulerFixture fixture =
                createRootBranchFixture(
                        initialPosition
                );


        List<PositionNode> rootChildren =
                fixture.rootChildren;


        PositionNode focus =
                rootChildren.get(
                        0
                );


        fixture.scheduler.setFocus(
                focus
        );


        expectSame(
                "Scheduler stores selected focus node",
                focus,
                fixture.scheduler.getFocusNode()
        );


        int workDone =
                fixture.scheduler.advance(
                        80
                );


        expectEquals(
                "Focused scheduler completes requested work",
                80,
                workDone
        );


        int focusExpansions =
                focus.getExpansionCount();


        int maxNonFocusExpansions =
                0;


        for (int index = 1;
             index < rootChildren.size();
             index++) {

            maxNonFocusExpansions =
                    Math.max(
                            maxNonFocusExpansions,
                            rootChildren.get(index)
                                    .getExpansionCount()
                    );
        }


        expectTrue(
                "Selected root branch receives expansion work",
                focusExpansions > 0
        );


        expectTrue(
                "Focus receives extra work beyond ordinary root branches",
                focusExpansions
                        > maxNonFocusExpansions
        );


        expectTrue(
                "Focus queue remains a distinct scheduler mechanism",
                fixture.scheduler.getFocusNode()
                        == focus
        );


        System.out.println(
                "Focus expansions: "
                        + focusExpansions
                        + ", max non-focus root expansions: "
                        + maxNonFocusExpansions
        );


        System.out.println();
    }


    // =========================================================
    // TEST 4 — FOCUS DOES NOT STARVE GLOBAL WORK
    // =========================================================

    private void testFocusDoesNotStarveGlobalWork(
            Position initialPosition
    ) {

        System.out.println(
                "--- Focus does not starve global work ---"
        );


        SchedulerFixture fixture =
                createRootBranchFixture(
                        initialPosition
                );


        List<PositionNode> rootChildren =
                fixture.rootChildren;


        PositionNode focus =
                rootChildren.get(
                        0
                );


        fixture.scheduler.setFocus(
                focus
        );


        /*
         * 3 of every 4 scheduler turns are global by design. Run long
         * enough that every root child present in the initial global
         * queue must have had an opportunity to advance.
         */
        int workDone =
                fixture.scheduler.advance(
                        120
                );


        expectEquals(
                "Focused search still completes substantial global work",
                120,
                workDone
        );


        int touchedNonFocus =
                0;


        int nonFocusCount =
                rootChildren.size() - 1;


        for (int index = 1;
             index < rootChildren.size();
             index++) {

            if (rootChildren.get(index)
                    .getExpansionCount()
                    > 0) {

                touchedNonFocus++;
            }
        }


        expectEquals(
                "Every non-focus root branch still receives work",
                nonFocusCount,
                touchedNonFocus
        );


        expectTrue(
                "Global queue still has reachable work while focus is active",
                fixture.scheduler.hasGlobalWork()
        );


        fixture.scheduler.clearFocus();


        expectTrue(
                "Focus can be cleared without destroying global work",
                fixture.scheduler.getFocusNode()
                        == null
                        &&
                        fixture.scheduler.hasGlobalWork()
        );


        int afterClear =
                fixture.scheduler.advance(
                        8
                );


        expectEquals(
                "Global exploration continues after focus is cleared",
                8,
                afterClear
        );


        System.out.println(
                "Non-focus root branches reached under focus: "
                        + touchedNonFocus
                        + " / "
                        + nonFocusCount
        );


        System.out.println();
    }


    // =========================================================
    // FIXTURE
    // =========================================================

    private SchedulerFixture createRootBranchFixture(
            Position initialPosition
    ) {

        PositionGraph graph =
                new PositionGraph();


        PositionNode root =
                graph.getOrCreateNode(
                        initialPosition
                );


        /*
         * Fully generate only the root's immediate legal moves.
         * This leaves each ordinary root child expandable, giving the
         * scheduler a deterministic starting set of independent lines.
         */
        while (root.hasUnexploredEdges(
                moveGenerator
        )) {

            graph.expandOneEdge(
                    root,
                    moveGenerator
            );
        }


        List<PositionNode> rootChildren =
                new ArrayList<>();


        for (SearchEdge edge :
                root.getOutgoingEdges()) {

            PositionNode child =
                    edge.getTarget();


            if (!rootChildren.contains(
                    child
            )) {

                rootChildren.add(
                        child
                );
            }
        }


        if (rootChildren.isEmpty()) {

            throw new IllegalStateException(
                    "Scheduler fairness fixture has no root children."
            );
        }


        ExplorationScheduler scheduler =
                new ExplorationScheduler(
                        graph,
                        moveGenerator,
                        root
                );


        return new SchedulerFixture(
                graph,
                root,
                rootChildren,
                scheduler
        );
    }


    private record SchedulerFixture(
            PositionGraph graph,
            PositionNode root,
            List<PositionNode> rootChildren,
            ExplorationScheduler scheduler
    ) {
    }


    // =========================================================
    // METRIC HELPERS
    // =========================================================

    private int countExpanded(
            List<PositionNode> nodes
    ) {

        int count =
                0;


        for (PositionNode node :
                nodes) {

            if (node.getExpansionCount()
                    > 0) {

                count++;
            }
        }


        return count;
    }


    private int minimumExpansionCount(
            List<PositionNode> nodes
    ) {

        int minimum =
                Integer.MAX_VALUE;


        for (PositionNode node :
                nodes) {

            minimum =
                    Math.min(
                            minimum,
                            node.getExpansionCount()
                    );
        }


        if (minimum
                == Integer.MAX_VALUE) {

            return 0;
        }


        return minimum;
    }


    // =========================================================
    // ASSERTION HELPERS
    // =========================================================

    private void expectSame(
            String name,
            Object expected,
            Object actual
    ) {

        if (expected == actual) {

            pass(
                    name
            );

        } else {

            fail(
                    name,
                    "Expected identical object references."
            );
        }
    }


    private void expectTrue(
            String name,
            boolean condition
    ) {

        if (condition) {

            pass(
                    name
            );

        } else {

            fail(
                    name,
                    "Expected true."
            );
        }
    }


    private void expectEquals(
            String name,
            int expected,
            int actual
    ) {

        if (expected == actual) {

            pass(
                    name
            );

        } else {

            fail(
                    name,
                    "Expected "
                            + expected
                            + " but got "
                            + actual
                            + "."
            );
        }
    }


    private void pass(
            String name
    ) {

        passed++;

        System.out.println(
                "[PASS] "
                        + name
        );
    }


    private void fail(
            String name,
            String detail
    ) {

        failed++;

        System.out.println(
                "[FAIL] "
                        + name
                        + " — "
                        + detail
        );
    }
}

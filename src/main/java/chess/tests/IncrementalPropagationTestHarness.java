package main.java.chess.tests;

import main.java.chess.model.Move;
import main.java.chess.model.Position;
import main.java.chess.model.PositionKey;
import main.java.chess.model.Square;
import main.java.chess.rules.MoveGenerator;
import main.java.chess.search.PositionGraph;
import main.java.chess.search.PositionNode;
import main.java.chess.search.SearchOutcome;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Version 1.0 correctness suite — Phase 4.
 *
 * Verifies that incremental dirty-queue propagation agrees with the
 * retained full/reference propagation implementations.
 */
public final class IncrementalPropagationTestHarness {

    private final MoveGenerator moveGenerator;

    private int passed;
    private int failed;


    public IncrementalPropagationTestHarness() {

        this.moveGenerator =
                new MoveGenerator();

        this.passed =
                0;

        this.failed =
                0;
    }


    public static void run(
            Position initialPosition
    ) {

        if (initialPosition == null) {

            throw new IllegalArgumentException(
                    "Initial position cannot be null."
            );
        }


        IncrementalPropagationTestHarness harness =
                new IncrementalPropagationTestHarness();


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
                "Phase 4 — Incremental Propagation"
        );

        System.out.println(
                "========================================"
        );

        System.out.println();


        testIncrementalNumericalPropagation(
                initialPosition
        );

        testIncrementalMatePropagation(
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
                    "Phase 4 correctness suite failed: "
                            + failed
                            + " test(s) failed."
            );
        }
    }


    // =========================================================
    // TEST 1 — NUMERICAL SEARCH VALUES
    // =========================================================

    private void testIncrementalNumericalPropagation(
            Position initialPosition
    ) {

        System.out.println(
                "--- Incremental numerical search values ---"
        );


        PositionGraph graph =
                new PositionGraph();


        PositionNode root =
                graph.getOrCreateNode(
                        initialPosition
                );


        /*
         * Build a deterministic nontrivial graph.
         *
         * Each round expands every node that existed at the start of
         * that round by one edge. This produces branching without
         * relying on ExplorationScheduler behavior.
         */
        for (int round = 0;
             round < 6;
             round++) {

            List<PositionNode> wave =
                    List.copyOf(
                            graph.getNodes()
                    );


            for (PositionNode node :
                    wave) {

                graph.expandOneEdge(
                        node,
                        moveGenerator
                );
            }
        }


        int incrementalChanges =
                graph.propagateDirtySearchValues();


        Map<PositionKey, Integer> incrementalValues =
                snapshotSearchValues(
                        graph
                );


        /*
         * If incremental propagation reached the same fixed state as
         * the synchronous oracle, the first full reference pass must
         * make no changes.
         */
        int oracleChanges =
                graph.backupAllNodesSynchronously();


        expectTrue(
                "Incremental numerical propagation performed work",
                incrementalChanges > 0
        );


        expectEquals(
                "Full numerical oracle finds no mismatch after incremental propagation",
                0,
                oracleChanges
        );


        expectTrue(
                "Numerical dirty queue is fully drained",
                !graph.hasSearchValueDirtyNodes()
        );


        expectTrue(
                "All numerical values remain identical after oracle pass",
                searchValuesMatchSnapshot(
                        graph,
                        incrementalValues
                )
        );


        expectTrue(
                "Numerical test graph contains multiple canonical nodes",
                graph.size() > 1
        );


        expectTrue(
                "Root has generated continuations",
                !root.getOutgoingEdges()
                        .isEmpty()
        );


        System.out.println(
                "Numerical test graph nodes: "
                        + graph.size()
        );


        System.out.println();
    }


    // =========================================================
    // TEST 2 — MATE / OUTCOME PROPAGATION
    // =========================================================

    private void testIncrementalMatePropagation(
            Position initialPosition
    ) {

        System.out.println(
                "--- Incremental mate/outcome propagation ---"
        );


        /*
         * Fool's Mate:
         *
         * 1. f3 e5
         * 2. g4 Qh4#
         *
         * We commit the exact line into a fresh graph. The final node
         * is seeded as BLACK_WIN, mate distance 0. Incremental outcome
         * propagation must carry that proof backward through every
         * parent in the line.
         */
        Position p0 =
                initialPosition;

        Position p1 =
                play(p0, "f2", "f3");

        Position p2 =
                play(p1, "e7", "e5");

        Position p3 =
                play(p2, "g2", "g4");

        Position p4 =
                play(p3, "d8", "h4");


        PositionGraph graph =
                new PositionGraph();


        PositionNode n0 =
                graph.getOrCreateNode(
                        p0
                );


        commit(graph, p0, p1);
        commit(graph, p1, p2);
        commit(graph, p2, p3);
        commit(graph, p3, p4);


        PositionNode mate =
                graph.getOrCreateNode(
                        p4
                );


        expectEquals(
                "Terminal mate node is seeded BLACK_WIN",
                SearchOutcome.BLACK_WIN,
                mate.getSearchOutcome()
        );


        expectEquals(
                "Terminal mate node has mate distance 0",
                0,
                mate.getMateDistance()
        );


        int incrementalChanges =
                graph.propagateDirtyOutcomes();


        Map<PositionKey, OutcomeSnapshot> incrementalOutcomes =
                snapshotOutcomes(
                        graph
                );


        /*
         * The retained full implementation is our reference oracle.
         * One pass after incremental convergence must change nothing.
         */
        int oracleChanges =
                graph.propagateMateOutcomesOnePass();


        expectTrue(
                "Incremental outcome propagation performed work",
                incrementalChanges > 0
        );


        expectEquals(
                "Full mate oracle finds no mismatch after incremental propagation",
                0,
                oracleChanges
        );


        expectTrue(
                "Outcome dirty queue is fully drained",
                !graph.hasOutcomeDirtyNodes()
        );


        expectTrue(
                "All outcomes and mate distances remain identical after oracle pass",
                outcomesMatchSnapshot(
                        graph,
                        incrementalOutcomes
                )
        );


        /*
         * On this deliberately single-line graph:
         *
         * p3: Black to move, Qh4# available -> BLACK_WIN in 1
         * p2: White has only one generated edge and is not fully
         *     expanded -> cannot yet be proven lost.
         *
         * Therefore propagation should stop exactly where proof rules
         * say it must stop.
         */
        PositionNode n3 =
                graph.getOrCreateNode(
                        p3
                );


        PositionNode n2 =
                graph.getOrCreateNode(
                        p2
                );


        expectEquals(
                "Parent of mate is proven BLACK_WIN",
                SearchOutcome.BLACK_WIN,
                n3.getSearchOutcome()
        );


        expectEquals(
                "Parent of mate has distance 1",
                1,
                n3.getMateDistance()
        );


        expectEquals(
                "Unexpanded opponent node remains UNKNOWN",
                SearchOutcome.UNKNOWN,
                n2.getSearchOutcome()
        );


        expectEquals(
                "Root remains UNKNOWN without a complete forced proof",
                SearchOutcome.UNKNOWN,
                n0.getSearchOutcome()
        );


        System.out.println(
                "Outcome test graph nodes: "
                        + graph.size()
        );


        System.out.println();
    }


    // =========================================================
    // SNAPSHOT HELPERS
    // =========================================================

    private Map<PositionKey, Integer> snapshotSearchValues(
            PositionGraph graph
    ) {

        Map<PositionKey, Integer> snapshot =
                new HashMap<>();


        for (PositionNode node :
                graph.getNodes()) {

            snapshot.put(
                    node.getKey(),
                    node.getSearchValue()
            );
        }


        return snapshot;
    }


    private boolean searchValuesMatchSnapshot(
            PositionGraph graph,
            Map<PositionKey, Integer> snapshot
    ) {

        if (snapshot.size()
                != graph.size()) {

            return false;
        }


        for (PositionNode node :
                graph.getNodes()) {

            Integer expected =
                    snapshot.get(
                            node.getKey()
                    );


            if (expected == null
                    ||
                    expected
                            != node.getSearchValue()) {

                return false;
            }
        }


        return true;
    }


    private Map<PositionKey, OutcomeSnapshot> snapshotOutcomes(
            PositionGraph graph
    ) {

        Map<PositionKey, OutcomeSnapshot> snapshot =
                new HashMap<>();


        for (PositionNode node :
                graph.getNodes()) {

            snapshot.put(
                    node.getKey(),
                    new OutcomeSnapshot(
                            node.getSearchOutcome(),
                            node.getMateDistance()
                    )
            );
        }


        return snapshot;
    }


    private boolean outcomesMatchSnapshot(
            PositionGraph graph,
            Map<PositionKey, OutcomeSnapshot> snapshot
    ) {

        if (snapshot.size()
                != graph.size()) {

            return false;
        }


        for (PositionNode node :
                graph.getNodes()) {

            OutcomeSnapshot expected =
                    snapshot.get(
                            node.getKey()
                    );


            if (expected == null
                    ||
                    expected.outcome
                            != node.getSearchOutcome()
                    ||
                    expected.mateDistance
                            != node.getMateDistance()) {

                return false;
            }
        }


        return true;
    }


    private record OutcomeSnapshot(
            SearchOutcome outcome,
            int mateDistance
    ) {
    }


    // =========================================================
    // GRAPH / MOVE HELPERS
    // =========================================================

    private void commit(
            PositionGraph graph,
            Position parent,
            Position child
    ) {

        boolean committed =
                graph.ensureManualContinuation(
                        parent,
                        child
                );


        if (!committed) {

            throw new IllegalStateException(
                    "Failed to commit legal test continuation."
            );
        }
    }


    private Position play(
            Position position,
            String from,
            String to
    ) {

        Move move =
                findMove(
                        position,
                        from,
                        to
                );


        if (move == null) {

            throw new IllegalStateException(
                    "Required test move is not legal: "
                            + from
                            + " -> "
                            + to
                            + System.lineSeparator()
                            + "Side to move: "
                            + position.getSideToMove()
                            + System.lineSeparator()
                            + "Legal moves: "
                            + moveGenerator.generateLegalMoves(
                            position
                    )
            );
        }


        return position.makeMove(
                move
        );
    }


    private Move findMove(
            Position position,
            String from,
            String to
    ) {

        Square fromSquare =
                square(
                        from
                );


        Square toSquare =
                square(
                        to
                );


        for (Move move :
                moveGenerator.generateLegalMoves(
                        position
                )) {

            if (move.from().equals(
                    fromSquare
            )
                    &&
                    move.to().equals(
                            toSquare
                    )
                    &&
                    move.promotion()
                            == null) {

                return move;
            }
        }


        return null;
    }


    private Square square(
            String coordinate
    ) {

        int file =
                Character.toLowerCase(
                        coordinate.charAt(
                                0
                        )
                )
                        - 'a';


        int rank =
                coordinate.charAt(
                        1
                )
                        - '1';


        return new Square(
                file,
                rank
        );
    }


    // =========================================================
    // ASSERTION HELPERS
    // =========================================================

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
            Object expected,
            Object actual
    ) {

        boolean equal =
                expected == null
                        ? actual == null
                        : expected.equals(
                        actual
                );


        if (equal) {

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

package main.java.chess.tests;

import main.java.chess.model.Move;
import main.java.chess.model.Position;
import main.java.chess.model.PositionKey;
import main.java.chess.model.Square;
import main.java.chess.rules.MoveGenerator;
import main.java.chess.search.PositionGraph;
import main.java.chess.search.PositionNode;

import java.util.List;


/**
 * Version 1.0 correctness suite — Phase 3.
 *
 * Verifies the persistent canonical PositionGraph invariants:
 *
 * 1. canonical node identity
 * 2. transposition merging
 * 3. outgoing-edge deduplication
 * 4. incoming-parent tracking
 * 5. cycle-safe statistics invalidation
 */
public final class GraphInvariantTestHarness {

    private final MoveGenerator moveGenerator;

    private int passed;
    private int failed;


    public GraphInvariantTestHarness() {

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


        GraphInvariantTestHarness harness =
                new GraphInvariantTestHarness();


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
                "Phase 3 — Graph Invariants"
        );

        System.out.println(
                "========================================"
        );

        System.out.println();


        testCanonicalNodeIdentity(
                initialPosition
        );

        testTranspositionMerging(
                initialPosition
        );

        testEdgeDeduplication(
                initialPosition
        );

        testIncomingParentTracking(
                initialPosition
        );

        testCycleSafeStatisticsInvalidation(
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
                    "Phase 3 correctness suite failed: "
                            + failed
                            + " test(s) failed."
            );
        }
    }


    // =========================================================
    // TEST 1 — CANONICAL NODE IDENTITY
    // =========================================================

    private void testCanonicalNodeIdentity(
            Position initialPosition
    ) {

        System.out.println(
                "--- Canonical node identity ---"
        );


        PositionGraph graph =
                new PositionGraph();


        PositionNode first =
                graph.getOrCreateNode(
                        initialPosition
                );


        PositionNode second =
                graph.getOrCreateNode(
                        initialPosition
                );


        expectSame(
                "Repeated lookup returns the same PositionNode object",
                first,
                second
        );


        expectEquals(
                "Repeated canonical lookup creates only one graph node",
                1,
                graph.size()
        );


        expectEquals(
                "Node key equals its Position key",
                initialPosition.createPositionKey(),
                first.getKey()
        );


        System.out.println();
    }


    // =========================================================
    // TEST 2 — TRANSPOSITION MERGING
    // =========================================================

    private void testTranspositionMerging(
            Position initialPosition
    ) {

        System.out.println(
                "--- Transposition merging ---"
        );


        /*
         * Two legal move orders reach the same current chess state:
         *
         * Line A:
         *   Nf3 Nf6 Nc3 Nc6
         *
         * Line B:
         *   Nc3 Nc6 Nf3 Nf6
         *
         * Board, side to move, castling rights, en-passant state,
         * and reversible-move count agree at the end.
         */
        Position lineA =
                initialPosition;

        lineA = play(lineA, "g1", "f3");
        lineA = play(lineA, "g8", "f6");
        lineA = play(lineA, "b1", "c3");
        lineA = play(lineA, "b8", "c6");


        Position lineB =
                initialPosition;

        lineB = play(lineB, "b1", "c3");
        lineB = play(lineB, "b8", "c6");
        lineB = play(lineB, "g1", "f3");
        lineB = play(lineB, "g8", "f6");


        PositionKey keyA =
                lineA.createPositionKey();


        PositionKey keyB =
                lineB.createPositionKey();


        expectEquals(
                "Equivalent transposition lines produce equal PositionKeys",
                keyA,
                keyB
        );


        PositionGraph graph =
                new PositionGraph();


        PositionNode nodeA =
                graph.getOrCreateNode(
                        lineA
                );


        PositionNode nodeB =
                graph.getOrCreateNode(
                        lineB
                );


        expectSame(
                "Equivalent transposition lines merge to one canonical node",
                nodeA,
                nodeB
        );


        expectEquals(
                "Merged transposition occupies one graph node",
                1,
                graph.size()
        );


        System.out.println();
    }


    // =========================================================
    // TEST 3 — EDGE DEDUPLICATION
    // =========================================================

    private void testEdgeDeduplication(
            Position initialPosition
    ) {

        System.out.println(
                "--- Edge deduplication ---"
        );


        PositionGraph graph =
                new PositionGraph();


        PositionNode root =
                graph.getOrCreateNode(
                        initialPosition
                );


        Position e4 =
                play(
                        initialPosition,
                        "e2",
                        "e4"
                );


        boolean firstCommit =
                graph.ensureManualContinuation(
                        initialPosition,
                        e4
                );


        boolean secondCommit =
                graph.ensureManualContinuation(
                        initialPosition,
                        e4
                );


        expectTrue(
                "First legal graph edge commit succeeds",
                firstCommit
        );


        expectTrue(
                "Repeated legal graph edge commit is accepted",
                secondCommit
        );


        expectEquals(
                "Repeated commit stores exactly one outgoing edge",
                1,
                root.getOutgoingEdges()
                        .size()
        );


        PositionNode child =
                graph.getOrCreateNode(
                        e4
                );


        expectEquals(
                "Repeated commit stores parent exactly once",
                1,
                child.getIncomingNodeCount()
        );


        expectTrue(
                "Child incoming-parent set contains root",
                child.getIncomingNodes()
                        .contains(
                                root
                        )
        );


        System.out.println();
    }


    // =========================================================
    // TEST 4 — MULTIPLE INCOMING PARENTS / TRANSPOSITION
    // =========================================================

    private void testIncomingParentTracking(
            Position initialPosition
    ) {

        System.out.println(
                "--- Incoming-parent tracking ---"
        );


        /*
         * Build both transposition paths inside the same PositionGraph.
         *
         * The final canonical node should have two distinct immediate
         * parents:
         *
         *   ... Nc3 -> Nc6
         *   ... Nf3 -> Nf6
         */
        PositionGraph graph =
                new PositionGraph();


        graph.getOrCreateNode(
                initialPosition
        );


        // -------------------------
        // Line A
        // -------------------------

        Position a0 =
                initialPosition;

        Position a1 =
                play(a0, "g1", "f3");

        Position a2 =
                play(a1, "g8", "f6");

        Position a3 =
                play(a2, "b1", "c3");

        Position a4 =
                play(a3, "b8", "c6");


        commit(
                graph,
                a0,
                a1
        );

        commit(
                graph,
                a1,
                a2
        );

        commit(
                graph,
                a2,
                a3
        );

        commit(
                graph,
                a3,
                a4
        );


        // -------------------------
        // Line B
        // -------------------------

        Position b0 =
                initialPosition;

        Position b1 =
                play(b0, "b1", "c3");

        Position b2 =
                play(b1, "b8", "c6");

        Position b3 =
                play(b2, "g1", "f3");

        Position b4 =
                play(b3, "g8", "f6");


        commit(
                graph,
                b0,
                b1
        );

        commit(
                graph,
                b1,
                b2
        );

        commit(
                graph,
                b2,
                b3
        );

        commit(
                graph,
                b3,
                b4
        );


        PositionNode finalA =
                graph.getOrCreateNode(
                        a4
                );


        PositionNode finalB =
                graph.getOrCreateNode(
                        b4
                );


        expectSame(
                "Both paths terminate at the same canonical node",
                finalA,
                finalB
        );


        expectEquals(
                "Transposition node records two distinct incoming parents",
                2,
                finalA.getIncomingNodeCount()
        );


        PositionNode parentA =
                graph.getOrCreateNode(
                        a3
                );


        PositionNode parentB =
                graph.getOrCreateNode(
                        b3
                );


        expectTrue(
                "Transposition node contains line A parent",
                finalA.getIncomingNodes()
                        .contains(
                                parentA
                        )
        );


        expectTrue(
                "Transposition node contains line B parent",
                finalA.getIncomingNodes()
                        .contains(
                                parentB
                        )
        );


        expectTrue(
                "The two incoming parents are distinct canonical nodes",
                parentA != parentB
        );


        System.out.println();
    }


    // =========================================================
    // TEST 5 — CYCLE-SAFE STATISTICS INVALIDATION
    // =========================================================

    private void testCycleSafeStatisticsInvalidation(
            Position initialPosition
    ) {

        System.out.println(
                "--- Cycle-safe statistics invalidation ---"
        );


        PositionGraph graph =
                new PositionGraph();


        PositionNode root =
                graph.getOrCreateNode(
                        initialPosition
                );


        Position childPosition =
                play(
                        initialPosition,
                        "e2",
                        "e4"
                );


        PositionNode child =
                graph.getOrCreateNode(
                        childPosition
                );


        /*
         * Give both nodes valid cached statistics so we can verify that
         * one invalidation wave reaches each exactly once.
         */
        root.cacheSubtreeStatistics(
                1,
                0,
                0
        );


        child.cacheSubtreeStatistics(
                1,
                0,
                0
        );


        /*
         * Create one real legal edge root -> child. This also places
         * root into PositionGraph's statistics-dirty queue.
         */
        boolean committed =
                graph.ensureManualContinuation(
                        initialPosition,
                        childPosition
                );


        expectTrue(
                "Legal edge used to seed invalidation is committed",
                committed
        );


        /*
         * Add the reverse dependency directly to produce a tiny
         * synthetic graph cycle:
         *
         *     root -> child
         *      ^       |
         *      |_______|
         *
         * This is deliberately a graph-infrastructure test, not a claim
         * that e4 can legally move back to the initial chess position.
         */
        Move syntheticReverseMove =
                new Move(
                        new Square(
                                4,
                                3
                        ),
                        new Square(
                                4,
                                1
                        )
                );


        child.addOutgoingEdge(
                syntheticReverseMove,
                root
        );


        root.addIncomingNode(
                child
        );


        int invalidated =
                graph.propagateStatisticsDirtiness();


        expectEquals(
                "Cycle invalidation processes two cached nodes exactly once",
                2,
                invalidated
        );


        expectTrue(
                "Root statistics cache is invalidated",
                !root.hasValidSubtreeStatistics()
        );


        expectTrue(
                "Child statistics cache is invalidated through cycle",
                !child.hasValidSubtreeStatistics()
        );


        expectTrue(
                "Statistics dirty queue drains despite cycle",
                !graph.hasStatisticsDirtyNodes()
        );


        System.out.println();
    }


    // =========================================================
    // GRAPH HELPERS
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


    // =========================================================
    // LEGAL MOVE HELPERS
    // =========================================================

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


        List<Move> legalMoves =
                moveGenerator.generateLegalMoves(
                        position
                );


        for (Move move :
                legalMoves) {

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

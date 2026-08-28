package main.java.chess.tests;

import main.java.chess.model.Board;
import main.java.chess.model.Color;
import main.java.chess.model.GameState;
import main.java.chess.model.Move;
import main.java.chess.model.Piece;
import main.java.chess.model.PieceType;
import main.java.chess.model.Position;
import main.java.chess.model.Square;
import main.java.chess.rules.GameStateEvaluator;
import main.java.chess.rules.MoveGenerator;
import main.java.chess.search.PositionGraph;
import main.java.chess.search.PositionNode;
import main.java.chess.search.SearchEdge;
import main.java.chess.search.SearchOutcome;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * Version 1.0 correctness suite — Phase 5.
 *
 * Verifies tactical solving behavior:
 *
 * 1. actual mate-in-1 discovery
 * 2. actual mate-in-2 discovery in a legal KQK position
 * 3. shortest winning mate-distance selection
 * 4. longest forced-loss delay selection
 */
public final class TacticalSolvingTestHarness {

    private final MoveGenerator moveGenerator;
    private final GameStateEvaluator gameStateEvaluator;

    private int passed;
    private int failed;


    public TacticalSolvingTestHarness() {

        this.moveGenerator =
                new MoveGenerator();

        this.gameStateEvaluator =
                new GameStateEvaluator();

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


        TacticalSolvingTestHarness harness =
                new TacticalSolvingTestHarness();


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
                "Phase 5 — Tactical Solving"
        );

        System.out.println(
                "========================================"
        );

        System.out.println();


        testActualMateInOne(
                initialPosition
        );

        testActualMateInTwo();

        testShortestWinningMateSelection(
                initialPosition
        );

        testLongestForcedLossDelay(
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
                    "Phase 5 correctness suite failed: "
                            + failed
                            + " test(s) failed."
            );
        }
    }


    // =========================================================
    // TEST 1 — ACTUAL MATE IN 1
    // =========================================================

    private void testActualMateInOne(
            Position initialPosition
    ) {

        System.out.println(
                "--- Actual mate in 1 ---"
        );


        /*
         * Fool's Mate precursor:
         *
         * 1. f3 e5
         * 2. g4
         *
         * Black to move has Qh4#.
         */
        Position position =
                initialPosition;

        position = play(position, "f2", "f3");
        position = play(position, "e7", "e5");
        position = play(position, "g2", "g4");


        PositionGraph graph =
                new PositionGraph();


        PositionNode root =
                graph.getOrCreateNode(
                        position
                );


        fullyExpandNode(
                graph,
                root
        );


        graph.propagateDirtyOutcomes();


        expectEquals(
                "Mate-in-1 root is proven BLACK_WIN",
                SearchOutcome.BLACK_WIN,
                root.getSearchOutcome()
        );


        expectEquals(
                "Mate-in-1 root has mate distance 1 ply",
                1,
                root.getMateDistance()
        );


        PositionNode mateChild =
                findWinningChildWithDistance(
                        root,
                        SearchOutcome.BLACK_WIN,
                        0
                );


        expectTrue(
                "Mate-in-1 root contains a terminal winning child",
                mateChild != null
        );


        if (mateChild != null) {

            expectEquals(
                    "Winning child is CHECKMATE",
                    GameState.CHECKMATE,
                    gameStateEvaluator.evaluate(
                            mateChild.getPosition()
                    )
            );
        }


        System.out.println();
    }


    // =========================================================
    // TEST 2 — ACTUAL MATE IN 2
    // =========================================================

    private void testActualMateInTwo() {

        System.out.println(
                "--- Actual mate in 2 ---"
        );


        /*
         * Rather than hard-code a dubious composed position, the
         * harness independently scans legal KQK positions and uses a
         * small recursive tree oracle to locate one that is exactly
         * mate in 2 (3 plies).
         *
         * The PositionGraph solver is then tested against that
         * independently established result.
         */
        Position mateInTwo =
                findLegalKqkMateInTwo();


        expectTrue(
                "Independent KQK oracle finds a legal exact mate-in-2 position",
                mateInTwo != null
        );


        if (mateInTwo == null) {

            System.out.println();

            return;
        }


        TacticalResult oracle =
                solveTacticalTree(
                        mateInTwo,
                        3
                );


        expectEquals(
                "Independent oracle says WHITE wins",
                Color.WHITE,
                oracle.winner
        );


        expectEquals(
                "Independent oracle gives exact distance 3 plies",
                3,
                oracle.distance
        );


        PositionGraph graph =
                new PositionGraph();


        PositionNode root =
                graph.getOrCreateNode(
                        mateInTwo
                );


        fullyExpandToDepth(
                graph,
                root,
                3
        );


        graph.propagateDirtyOutcomes();


        expectEquals(
                "PositionGraph proves mate-in-2 as WHITE_WIN",
                SearchOutcome.WHITE_WIN,
                root.getSearchOutcome()
        );


        expectEquals(
                "PositionGraph mate-in-2 distance is 3 plies",
                3,
                root.getMateDistance()
        );


        expectTrue(
                "Mate-in-2 root has at least one winning continuation",
                findWinningChildWithDistance(
                        root,
                        SearchOutcome.WHITE_WIN,
                        2
                )
                        != null
        );


        System.out.println(
                "Mate-in-2 position: "
                        + describePosition(
                        mateInTwo
                )
        );


        System.out.println(
                "Mate-in-2 graph nodes: "
                        + graph.size()
        );


        System.out.println();
    }


    // =========================================================
    // TEST 3 — SHORTEST WINNING MATE
    // =========================================================

    private void testShortestWinningMateSelection(
            Position initialPosition
    ) {

        System.out.println(
                "--- Shortest winning mate selection ---"
        );


        PositionGraph graph =
                new PositionGraph();


        PositionNode root =
                graph.getOrCreateNode(
                        initialPosition
                );


        fullyExpandNode(
                graph,
                root
        );


        List<SearchEdge> edges =
                root.getOutgoingEdges();


        expectTrue(
                "Selection-policy root has at least two legal children",
                edges.size() >= 2
        );


        if (edges.size() < 2) {

            System.out.println();

            return;
        }


        PositionNode shortWin =
                edges.get(0)
                        .getTarget();


        PositionNode longWin =
                edges.get(1)
                        .getTarget();


        shortWin.setSearchOutcome(
                SearchOutcome.WHITE_WIN
        );

        shortWin.setMateDistance(
                2
        );


        longWin.setSearchOutcome(
                SearchOutcome.WHITE_WIN
        );

        longWin.setMateDistance(
                6
        );


        /*
         * Expansion already marked root outcome-dirty.
         */
        graph.propagateDirtyOutcomes();


        expectEquals(
                "Winning side chooses WHITE_WIN",
                SearchOutcome.WHITE_WIN,
                root.getSearchOutcome()
        );


        expectEquals(
                "Winning side chooses shortest mate distance",
                3,
                root.getMateDistance()
        );


        System.out.println();
    }


    // =========================================================
    // TEST 4 — LONGEST FORCED-LOSS DELAY
    // =========================================================

    private void testLongestForcedLossDelay(
            Position initialPosition
    ) {

        System.out.println(
                "--- Longest forced-loss delay ---"
        );


        PositionGraph graph =
                new PositionGraph();


        PositionNode root =
                graph.getOrCreateNode(
                        initialPosition
                );


        fullyExpandNode(
                graph,
                root
        );


        List<SearchEdge> edges =
                root.getOutgoingEdges();


        expectTrue(
                "Forced-loss root is fully expanded",
                root.isFullyExpanded(
                        moveGenerator
                )
        );


        expectTrue(
                "Forced-loss root has multiple legal continuations",
                edges.size() >= 2
        );


        if (edges.isEmpty()) {

            System.out.println();

            return;
        }


        int longestChildDistance =
                0;


        for (int index = 0;
             index < edges.size();
             index++) {

            PositionNode child =
                    edges.get(index)
                            .getTarget();


            int distance =
                    1
                            + (
                            index % 5
                    );


            child.setSearchOutcome(
                    SearchOutcome.BLACK_WIN
            );

            child.setMateDistance(
                    distance
            );


            longestChildDistance =
                    Math.max(
                            longestChildDistance,
                            distance
                    );
        }


        /*
         * White is to move at the standard initial position. If every
         * legal child is a proven BLACK_WIN, White is forced to lose
         * and must choose the line that delays mate the longest.
         */
        graph.propagateDirtyOutcomes();


        expectEquals(
                "Fully expanded losing root is proven BLACK_WIN",
                SearchOutcome.BLACK_WIN,
                root.getSearchOutcome()
        );


        expectEquals(
                "Losing side chooses longest available mate delay",
                longestChildDistance + 1,
                root.getMateDistance()
        );


        System.out.println();
    }


    // =========================================================
    // INDEPENDENT SMALL TREE ORACLE
    // =========================================================

    private TacticalResult solveTacticalTree(
            Position position,
            int remainingPlies
    ) {

        GameState state =
                gameStateEvaluator.evaluate(
                        position
                );


        if (state
                == GameState.CHECKMATE) {

            Color winner =
                    opposite(
                            position.getSideToMove()
                    );


            return new TacticalResult(
                    winner,
                    0
            );
        }


        if (state
                == GameState.STALEMATE
                ||
                state
                        == GameState.DRAW_75_MOVE
                ||
                state
                        == GameState.DRAW_FIVEFOLD_REPETITION) {

            return TacticalResult.UNKNOWN;
        }


        if (remainingPlies <= 0) {

            return TacticalResult.UNKNOWN;
        }


        List<Move> legalMoves =
                moveGenerator.generateLegalMoves(
                        position
                );


        if (legalMoves.isEmpty()) {

            return TacticalResult.UNKNOWN;
        }


        Color sideToMove =
                position.getSideToMove();


        Color opponent =
                opposite(
                        sideToMove
                );


        int shortestWin =
                Integer.MAX_VALUE;


        boolean everyChildOpponentWin =
                true;


        int longestLoss =
                Integer.MIN_VALUE;


        for (Move move :
                legalMoves) {

            Position child =
                    position.makeMove(
                            move
                    );


            TacticalResult childResult =
                    solveTacticalTree(
                            child,
                            remainingPlies - 1
                    );


            if (childResult.winner
                    == sideToMove) {

                shortestWin =
                        Math.min(
                                shortestWin,
                                childResult.distance + 1
                        );
            }


            if (childResult.winner
                    != opponent) {

                everyChildOpponentWin =
                        false;

            } else {

                longestLoss =
                        Math.max(
                                longestLoss,
                                childResult.distance + 1
                        );
            }
        }


        if (shortestWin
                != Integer.MAX_VALUE) {

            return new TacticalResult(
                    sideToMove,
                    shortestWin
            );
        }


        if (everyChildOpponentWin
                &&
                longestLoss
                        != Integer.MIN_VALUE) {

            return new TacticalResult(
                    opponent,
                    longestLoss
            );
        }


        return TacticalResult.UNKNOWN;
    }


    private record TacticalResult(
            Color winner,
            int distance
    ) {

        private static final TacticalResult UNKNOWN =
                new TacticalResult(
                        null,
                        -1
                );
    }


    // =========================================================
    // KQK MATE-IN-2 POSITION DISCOVERY
    // =========================================================

    private Position findLegalKqkMateInTwo() {

        /*
         * Keep the black king near a corner so exact mates within
         * three plies are common and the scan remains tiny.
         */
        Square[] blackKingSquares = {
                square("a8"),
                square("h8"),
                square("a1"),
                square("h1")
        };


        for (Square blackKing :
                blackKingSquares) {

            for (int whiteKingFile = 0;
                 whiteKingFile < 8;
                 whiteKingFile++) {

                for (int whiteKingRank = 0;
                     whiteKingRank < 8;
                     whiteKingRank++) {

                    Square whiteKing =
                            new Square(
                                    whiteKingFile,
                                    whiteKingRank
                            );


                    if (whiteKing.equals(
                            blackKing
                    )
                            ||
                            kingsAdjacent(
                                    whiteKing,
                                    blackKing
                            )) {

                        continue;
                    }


                    for (int queenFile = 0;
                         queenFile < 8;
                         queenFile++) {

                        for (int queenRank = 0;
                             queenRank < 8;
                             queenRank++) {

                            Square queen =
                                    new Square(
                                            queenFile,
                                            queenRank
                                    );


                            if (queen.equals(
                                    blackKing
                            )
                                    ||
                                    queen.equals(
                                            whiteKing
                                    )) {

                                continue;
                            }


                            /*
                             * With White to move, Black may not already
                             * be in check in a legal position.
                             */
                            if (queenAttacks(
                                    queen,
                                    blackKing,
                                    whiteKing
                            )) {

                                continue;
                            }


                            Position candidate =
                                    createKqkPosition(
                                            whiteKing,
                                            queen,
                                            blackKing
                                    );


                            TacticalResult result =
                                    solveTacticalTree(
                                            candidate,
                                            3
                                    );


                            if (result.winner
                                    == Color.WHITE
                                    &&
                                    result.distance
                                            == 3) {

                                return candidate;
                            }
                        }
                    }
                }
            }
        }


        return null;
    }


    private Position createKqkPosition(
            Square whiteKing,
            Square whiteQueen,
            Square blackKing
    ) {

        Board board =
                new Board();


        board.setPiece(
                whiteKing,
                new Piece(
                        PieceType.KING,
                        Color.WHITE
                )
        );


        board.setPiece(
                whiteQueen,
                new Piece(
                        PieceType.QUEEN,
                        Color.WHITE
                )
        );


        board.setPiece(
                blackKing,
                new Piece(
                        PieceType.KING,
                        Color.BLACK
                )
        );


        return new Position(
                board,
                Color.WHITE
        );
    }


    private boolean kingsAdjacent(
            Square first,
            Square second
    ) {

        return Math.abs(
                first.file()
                        - second.file()
        ) <= 1
                &&
                Math.abs(
                        first.rank()
                                - second.rank()
                ) <= 1;
    }


    private boolean queenAttacks(
            Square queen,
            Square target,
            Square blocker
    ) {

        int fileDelta =
                target.file()
                        - queen.file();


        int rankDelta =
                target.rank()
                        - queen.rank();


        boolean aligned =
                fileDelta == 0
                        ||
                        rankDelta == 0
                        ||
                        Math.abs(
                                fileDelta
                        )
                                == Math.abs(
                                rankDelta
                        );


        if (!aligned) {

            return false;
        }


        int fileStep =
                Integer.compare(
                        fileDelta,
                        0
                );


        int rankStep =
                Integer.compare(
                        rankDelta,
                        0
                );


        int file =
                queen.file()
                        + fileStep;


        int rank =
                queen.rank()
                        + rankStep;


        while (file != target.file()
                ||
                rank != target.rank()) {

            if (blocker.file()
                    == file
                    &&
                    blocker.rank()
                            == rank) {

                return false;
            }


            file += fileStep;
            rank += rankStep;
        }


        return true;
    }


    // =========================================================
    // GRAPH EXPANSION HELPERS
    // =========================================================

    private void fullyExpandNode(
            PositionGraph graph,
            PositionNode node
    ) {

        while (node.hasUnexploredEdges(
                moveGenerator
        )) {

            graph.expandOneEdge(
                    node,
                    moveGenerator
            );
        }
    }


    private void fullyExpandToDepth(
            PositionGraph graph,
            PositionNode root,
            int depth
    ) {

        List<PositionNode> frontier =
                new ArrayList<>();


        frontier.add(
                root
        );


        for (int currentDepth = 0;
             currentDepth < depth;
             currentDepth++) {

            List<PositionNode> nextFrontier =
                    new ArrayList<>();


            Set<PositionNode> seenNext =
                    new HashSet<>();


            for (PositionNode node :
                    frontier) {

                fullyExpandNode(
                        graph,
                        node
                );


                for (SearchEdge edge :
                        node.getOutgoingEdges()) {

                    PositionNode child =
                            edge.getTarget();


                    if (seenNext.add(
                            child
                    )) {

                        nextFrontier.add(
                                child
                        );
                    }
                }
            }


            frontier =
                    nextFrontier;


            if (frontier.isEmpty()) {

                break;
            }
        }
    }


    private PositionNode findWinningChildWithDistance(
            PositionNode node,
            SearchOutcome outcome,
            int distance
    ) {

        for (SearchEdge edge :
                node.getOutgoingEdges()) {

            PositionNode child =
                    edge.getTarget();


            if (child.getSearchOutcome()
                    == outcome
                    &&
                    child.getMateDistance()
                            == distance) {

                return child;
            }
        }


        return null;
    }


    // =========================================================
    // POSITION DESCRIPTION
    // =========================================================

    private String describePosition(
            Position position
    ) {

        List<String> pieces =
                new ArrayList<>();


        for (int rank = 0;
             rank < 8;
             rank++) {

            for (int file = 0;
                 file < 8;
                 file++) {

                Square square =
                        new Square(
                                file,
                                rank
                        );


                Piece piece =
                        position
                                .getBoard()
                                .getPiece(
                                        square
                                );


                if (piece == null) {

                    continue;
                }


                pieces.add(
                        piece.color()
                                + " "
                                + piece.type()
                                + "@"
                                + squareName(
                                square
                        )
                );
            }
        }


        return String.join(
                ", ",
                pieces
        );
    }


    private String squareName(
            Square square
    ) {

        return ""
                + (
                char
                ) (
                'a'
                        + square.file()
        )
                + (
                square.rank() + 1
        );
    }


    // =========================================================
    // MOVE HELPERS
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


    private Color opposite(
            Color color
    ) {

        return color
                == Color.WHITE
                ? Color.BLACK
                : Color.WHITE;
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

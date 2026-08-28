package main.java.chess.tests;

import main.java.chess.model.GameState;
import main.java.chess.model.Move;
import main.java.chess.model.Position;
import main.java.chess.model.Square;
import main.java.chess.rules.GameStateEvaluator;
import main.java.chess.rules.MoveGenerator;

import java.util.List;


/**
 * Version 1.0 correctness suite — Phase 1.
 *
 * Verifies the engine's four fundamental non-draw terminal/state categories:
 *
 * 1. ordinary ongoing position
 * 2. check, but not mate
 * 3. checkmate
 * 4. stalemate
 *
 * The harness intentionally constructs every test position by playing legal
 * moves from the ordinary starting position supplied by Main.  That means the
 * tests exercise the real MoveGenerator + Position.makeMove(...) path instead
 * of relying on a separate FEN/parser/test-position implementation.
 */
public final class TerminalStateTestHarness {

    private final MoveGenerator moveGenerator;
    private final GameStateEvaluator gameStateEvaluator;

    private int passed;
    private int failed;


    public TerminalStateTestHarness() {

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


        TerminalStateTestHarness harness =
                new TerminalStateTestHarness();


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
                "Phase 1 — Terminal Position States"
        );

        System.out.println(
                "========================================"
        );

        System.out.println();


        testOngoing(
                initialPosition
        );

        testCheck(
                initialPosition
        );

        testCheckmate(
                initialPosition
        );

        testStalemate(
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
                    "Phase 1 correctness suite failed: "
                            + failed
                            + " test(s) failed."
            );
        }
    }


    // =========================================================
    // TEST 1 — ORDINARY ONGOING POSITION
    // =========================================================

    private void testOngoing(
            Position initialPosition
    ) {

        System.out.println(
                "--- Ongoing position ---"
        );


        GameState state =
                gameStateEvaluator.evaluate(
                        initialPosition
                );


        List<Move> legalMoves =
                moveGenerator.generateLegalMoves(
                        initialPosition
                );


        expectEquals(
                "Initial position is ONGOING",
                GameState.ONGOING,
                state
        );


        expectTrue(
                "Initial position has legal moves",
                !legalMoves.isEmpty()
        );


        System.out.println();
    }


    // =========================================================
    // TEST 2 — CHECK, BUT NOT CHECKMATE
    // =========================================================

    private void testCheck(
            Position initialPosition
    ) {

        System.out.println(
                "--- Check, but not mate ---"
        );


        /*
         * 1. e4 f6
         * 2. Qh5+
         *
         * Moving the f7 pawn clears the h5-e8 diagonal.
         * Black is in check but still has legal replies.
         */
        Position position =
                play(
                        initialPosition,
                        "e2",
                        "e4"
                );


        position =
                play(
                        position,
                        "f7",
                        "f6"
                );


        position =
                play(
                        position,
                        "d1",
                        "h5"
                );


        GameState state =
                gameStateEvaluator.evaluate(
                        position
                );


        List<Move> legalMoves =
                moveGenerator.generateLegalMoves(
                        position
                );


        expectEquals(
                "Check position is CHECK",
                GameState.CHECK,
                state
        );


        expectTrue(
                "Checked side still has legal moves",
                !legalMoves.isEmpty()
        );


        System.out.println();
    }


    // =========================================================
    // TEST 3 — CHECKMATE
    // =========================================================

    private void testCheckmate(
            Position initialPosition
    ) {

        System.out.println(
                "--- Checkmate ---"
        );


        /*
         * Fool's Mate:
         *
         * 1. f3 e5
         * 2. g4 Qh4#
         */
        Position position =
                play(
                        initialPosition,
                        "f2",
                        "f3"
                );


        position =
                play(
                        position,
                        "e7",
                        "e5"
                );


        position =
                play(
                        position,
                        "g2",
                        "g4"
                );


        position =
                play(
                        position,
                        "d8",
                        "h4"
                );


        GameState state =
                gameStateEvaluator.evaluate(
                        position
                );


        List<Move> legalMoves =
                moveGenerator.generateLegalMoves(
                        position
                );


        expectEquals(
                "Fool's Mate is CHECKMATE",
                GameState.CHECKMATE,
                state
        );


        expectEquals(
                "Checkmate has zero legal moves",
                0,
                legalMoves.size()
        );


        System.out.println();
    }


    // =========================================================
    // TEST 4 — STALEMATE
    // =========================================================

    private void testStalemate(
            Position initialPosition
    ) {

        System.out.println(
                "--- Stalemate ---"
        );


        /*
         * Sam Loyd's classic shortest stalemate construction
         * from the ordinary starting position:
         *
         * 1. e3   a5
         * 2. Qh5  Ra6
         * 3. Qxa5 h5
         * 4. Qxc7 Rah6
         * 5. h4   f6
         * 6. Qxd7+ Kf7
         * 7. Qxb7 Qd3
         * 8. Qxb8 Qh7
         * 9. Qxc8 Kg6
         * 10. Qe6
         *
         * Black has no legal move and is not in check.
         */
        Position position =
                initialPosition;


        position = play(position, "e2", "e3");
        position = play(position, "a7", "a5");

        position = play(position, "d1", "h5");
        position = play(position, "a8", "a6");

        position = play(position, "h5", "a5");
        position = play(position, "h7", "h5");

        position = play(position, "a5", "c7");
        position = play(position, "a6", "h6");

        position = play(position, "h2", "h4");
        position = play(position, "f7", "f6");

        position = play(position, "c7", "d7");
        position = play(position, "e8", "f7");

        position = play(position, "d7", "b7");
        position = play(position, "d8", "d3");

        position = play(position, "b7", "b8");
        position = play(position, "d3", "h7");

        position = play(position, "b8", "c8");
        position = play(position, "f7", "g6");

        position = play(position, "c8", "e6");


        GameState state =
                gameStateEvaluator.evaluate(
                        position
                );


        List<Move> legalMoves =
                moveGenerator.generateLegalMoves(
                        position
                );


        expectEquals(
                "Loyd position is STALEMATE",
                GameState.STALEMATE,
                state
        );


        expectEquals(
                "Stalemate has zero legal moves",
                0,
                legalMoves.size()
        );


        System.out.println();
    }


    // =========================================================
    // LEGAL MOVE SEQUENCE HELPER
    // =========================================================

    private Position play(
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
                    )) {

                return position.makeMove(
                        move
                );
            }
        }


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
                        + legalMoves
        );
    }


    // =========================================================
    // ALGEBRAIC SQUARE HELPER
    // =========================================================

    private Square square(
            String coordinate
    ) {

        if (coordinate == null
                ||
                coordinate.length() != 2) {

            throw new IllegalArgumentException(
                    "Invalid square coordinate: "
                            + coordinate
            );
        }


        char fileCharacter =
                Character.toLowerCase(
                        coordinate.charAt(
                                0
                        )
                );


        char rankCharacter =
                coordinate.charAt(
                        1
                );


        int file =
                fileCharacter
                        - 'a';


        int rank =
                rankCharacter
                        - '1';


        if (file < 0
                ||
                file > 7
                ||
                rank < 0
                ||
                rank > 7) {

            throw new IllegalArgumentException(
                    "Invalid square coordinate: "
                            + coordinate
            );
        }


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

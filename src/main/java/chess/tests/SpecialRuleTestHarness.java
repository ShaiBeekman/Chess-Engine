package main.java.chess.tests;

import main.java.chess.model.Board;
import main.java.chess.model.Color;
import main.java.chess.model.GameState;
import main.java.chess.model.Move;
import main.java.chess.model.Piece;
import main.java.chess.model.PieceType;
import main.java.chess.model.Position;
import main.java.chess.model.PositionKey;
import main.java.chess.model.Square;
import main.java.chess.rules.GameStateEvaluator;
import main.java.chess.rules.MoveGenerator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Version 1.0 correctness suite — Phase 2.
 *
 * Verifies special chess rules and history-sensitive draw rules:
 *
 * 1. kingside castling
 * 2. en passant
 * 3. promotion
 * 4. fivefold repetition
 * 5. seventy-five-move rule
 */
public final class SpecialRuleTestHarness {

    private static final int SEVENTY_FIVE_MOVE_HALF_MOVES =
            150;

    /*
     * A generous but finite safety bound for the test's path search.
     *
     * This is test infrastructure only; it is not part of the chess
     * engine's search.
     */
    private static final long REVERSIBLE_PATH_SEARCH_LIMIT =
            2_000_000L;


    private final MoveGenerator moveGenerator;
    private final GameStateEvaluator gameStateEvaluator;

    private int passed;
    private int failed;

    private long reversibleSearchNodes;


    public SpecialRuleTestHarness() {

        this.moveGenerator =
                new MoveGenerator();

        this.gameStateEvaluator =
                new GameStateEvaluator();

        this.passed =
                0;

        this.failed =
                0;

        this.reversibleSearchNodes =
                0L;
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


        SpecialRuleTestHarness harness =
                new SpecialRuleTestHarness();


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
                "Phase 2 — Special Rules"
        );

        System.out.println(
                "========================================"
        );

        System.out.println();


        testKingsideCastling(
                initialPosition
        );

        testEnPassant(
                initialPosition
        );

        testPromotion();

        testFivefoldRepetition(
                initialPosition
        );

        testSeventyFiveMoveRule();


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
                    "Phase 2 correctness suite failed: "
                            + failed
                            + " test(s) failed."
            );
        }
    }


    // =========================================================
    // TEST 1 — KING-SIDE CASTLING
    // =========================================================

    private void testKingsideCastling(
            Position initialPosition
    ) {

        System.out.println(
                "--- Kingside castling ---"
        );


        /*
         * Clear f1 and g1:
         *
         * 1. Nf3 Nf6
         * 2. g3  g6
         * 3. Bg2 Bg7
         */
        Position position =
                initialPosition;

        position = play(position, "g1", "f3");
        position = play(position, "g8", "f6");

        position = play(position, "g2", "g3");
        position = play(position, "g7", "g6");

        position = play(position, "f1", "g2");
        position = play(position, "f8", "g7");


        Move castle =
                findMove(
                        position,
                        "e1",
                        "g1",
                        null
                );


        expectTrue(
                "White kingside castling move is generated",
                castle != null
        );


        if (castle != null) {

            position =
                    position.makeMove(
                            castle
                    );


            Piece king =
                    position
                            .getBoard()
                            .getPiece(
                                    square(
                                            "g1"
                                    )
                            );


            Piece rook =
                    position
                            .getBoard()
                            .getPiece(
                                    square(
                                            "f1"
                                    )
                            );


            expectPiece(
                    "Castling places white king on g1",
                    king,
                    PieceType.KING,
                    Color.WHITE
            );


            expectPiece(
                    "Castling places white rook on f1",
                    rook,
                    PieceType.ROOK,
                    Color.WHITE
            );


            expectTrue(
                    "Castling clears e1",
                    position
                            .getBoard()
                            .getPiece(
                                    square(
                                            "e1"
                                    )
                            )
                            == null
            );


            expectTrue(
                    "Castling clears h1",
                    position
                            .getBoard()
                            .getPiece(
                                    square(
                                            "h1"
                                    )
                            )
                            == null
            );
        }


        System.out.println();
    }


    // =========================================================
    // TEST 2 — EN PASSANT
    // =========================================================

    private void testEnPassant(
            Position initialPosition
    ) {

        System.out.println(
                "--- En passant ---"
        );


        /*
         * 1. e4 a6
         * 2. e5 d5
         * 3. exd6 e.p.
         */
        Position position =
                initialPosition;

        position = play(position, "e2", "e4");
        position = play(position, "a7", "a6");

        position = play(position, "e4", "e5");
        position = play(position, "d7", "d5");


        Move enPassant =
                findMove(
                        position,
                        "e5",
                        "d6",
                        null
                );


        expectTrue(
                "En-passant capture e5xd6 is generated",
                enPassant != null
        );


        if (enPassant != null) {

            position =
                    position.makeMove(
                            enPassant
                    );


            Piece whitePawn =
                    position
                            .getBoard()
                            .getPiece(
                                    square(
                                            "d6"
                                    )
                            );


            expectPiece(
                    "En passant places white pawn on d6",
                    whitePawn,
                    PieceType.PAWN,
                    Color.WHITE
            );


            expectTrue(
                    "En passant removes black pawn from d5",
                    position
                            .getBoard()
                            .getPiece(
                                    square(
                                            "d5"
                                    )
                            )
                            == null
            );


            expectTrue(
                    "En passant clears e5",
                    position
                            .getBoard()
                            .getPiece(
                                    square(
                                            "e5"
                                    )
                            )
                            == null
            );
        }


        System.out.println();
    }


    // =========================================================
    // TEST 3 — PROMOTION
    // =========================================================

    private void testPromotion() {

        System.out.println(
                "--- Promotion ---"
        );


        Board board =
                new Board();


        board.setPiece(
                square(
                        "h1"
                ),
                new Piece(
                        PieceType.KING,
                        Color.WHITE
                )
        );


        board.setPiece(
                square(
                        "h8"
                ),
                new Piece(
                        PieceType.KING,
                        Color.BLACK
                )
        );


        board.setPiece(
                square(
                        "a7"
                ),
                new Piece(
                        PieceType.PAWN,
                        Color.WHITE
                )
        );


        Position position =
                new Position(
                        board,
                        Color.WHITE
                );


        List<Move> legalMoves =
                moveGenerator.generateLegalMoves(
                        position
                );


        int promotionCount =
                0;


        boolean queenPromotion =
                false;

        boolean rookPromotion =
                false;

        boolean bishopPromotion =
                false;

        boolean knightPromotion =
                false;


        Move queenMove =
                null;


        for (Move move :
                legalMoves) {

            if (!move.from().equals(
                    square(
                            "a7"
                    )
            )
                    ||
                    !move.to().equals(
                            square(
                                    "a8"
                            )
                    )
                    ||
                    move.promotion() == null) {

                continue;
            }


            promotionCount++;


            switch (move.promotion()) {

                case QUEEN -> {
                    queenPromotion = true;
                    queenMove = move;
                }

                case ROOK ->
                        rookPromotion = true;

                case BISHOP ->
                        bishopPromotion = true;

                case KNIGHT ->
                        knightPromotion = true;

                default -> {
                    // King/pawn promotions are never legal.
                }
            }
        }


        expectEquals(
                "Exactly four a8 promotion choices are generated",
                4,
                promotionCount
        );


        expectTrue(
                "Queen promotion is generated",
                queenPromotion
        );


        expectTrue(
                "Rook promotion is generated",
                rookPromotion
        );


        expectTrue(
                "Bishop promotion is generated",
                bishopPromotion
        );


        expectTrue(
                "Knight promotion is generated",
                knightPromotion
        );


        if (queenMove != null) {

            Position promoted =
                    position.makeMove(
                            queenMove
                    );


            Piece promotedPiece =
                    promoted
                            .getBoard()
                            .getPiece(
                                    square(
                                            "a8"
                                    )
                            );


            expectPiece(
                    "Queen promotion creates a white queen on a8",
                    promotedPiece,
                    PieceType.QUEEN,
                    Color.WHITE
            );


            expectTrue(
                    "Promotion clears a7",
                    promoted
                            .getBoard()
                            .getPiece(
                                    square(
                                            "a7"
                                    )
                            )
                            == null
            );

        } else {

            fail(
                    "Queen promotion can be executed",
                    "No queen-promotion move was generated."
            );
        }


        System.out.println();
    }


    // =========================================================
    // TEST 4 — FIVEFOLD REPETITION
    // =========================================================

    private void testFivefoldRepetition(
            Position initialPosition
    ) {

        System.out.println(
                "--- Fivefold repetition ---"
        );


        Position position =
                initialPosition;


        for (int cycle = 0;
             cycle < 4;
             cycle++) {

            position = play(position, "g1", "f3");
            position = play(position, "g8", "f6");

            position = play(position, "f3", "g1");
            position = play(position, "f6", "g8");
        }


        GameState state =
                gameStateEvaluator.evaluate(
                        position
                );


        expectEquals(
                "Fifth occurrence is DRAW_FIVEFOLD_REPETITION",
                GameState.DRAW_FIVEFOLD_REPETITION,
                state
        );


        System.out.println();
    }


    // =========================================================
    // TEST 5 — 75-MOVE RULE
    // =========================================================

    private void testSeventyFiveMoveRule() {

        System.out.println(
                "--- 75-move rule ---"
        );


        /*
         * Use a deliberately sparse legal position.
         *
         * No pawns are present, so every legal non-capturing move is
         * reversible for purposes of the 75-move clock.
         *
         * White: King a1, Knight b1, Knight g1
         * Black: King h8, Knight b8, Knight g8
         *
         * The harness performs a depth-first backtracking search for
         * 150 legal non-capturing half-moves while refusing any fifth
         * occurrence.  This avoids the flaw in the previous greedy
         * test, which could choose itself into a dead end.
         */
        Position start =
                createReversibleClockTestPosition();


        Map<PositionKey, Integer> occurrenceCounts =
                new HashMap<>();


        recordOccurrence(
                occurrenceCounts,
                start
        );


        List<Move> path =
                new ArrayList<>();


        reversibleSearchNodes =
                0L;


        Position result =
                searchReversiblePath(
                        start,
                        0,
                        occurrenceCounts,
                        path
                );


        expectTrue(
                "150 reversible half-moves can be constructed",
                result != null
        );


        if (result == null) {

            System.out.println(
                    "75-move test search nodes: "
                            + reversibleSearchNodes
            );

            System.out.println();

            return;
        }


        expectEquals(
                "Constructed exactly 150 reversible half-moves",
                SEVENTY_FIVE_MOVE_HALF_MOVES,
                path.size()
        );


        GameState state =
                gameStateEvaluator.evaluate(
                        result
                );


        expectEquals(
                "150 reversible half-moves trigger DRAW_75_MOVE",
                GameState.DRAW_75_MOVE,
                state
        );


        System.out.println(
                "75-move test search nodes: "
                        + reversibleSearchNodes
        );


        System.out.println();
    }


    private Position createReversibleClockTestPosition() {

        Board board =
                new Board();


        board.setPiece(
                square(
                        "a1"
                ),
                new Piece(
                        PieceType.KING,
                        Color.WHITE
                )
        );


        board.setPiece(
                square(
                        "b1"
                ),
                new Piece(
                        PieceType.KNIGHT,
                        Color.WHITE
                )
        );


        board.setPiece(
                square(
                        "g1"
                ),
                new Piece(
                        PieceType.KNIGHT,
                        Color.WHITE
                )
        );


        board.setPiece(
                square(
                        "h8"
                ),
                new Piece(
                        PieceType.KING,
                        Color.BLACK
                )
        );


        board.setPiece(
                square(
                        "b8"
                ),
                new Piece(
                        PieceType.KNIGHT,
                        Color.BLACK
                )
        );


        board.setPiece(
                square(
                        "g8"
                ),
                new Piece(
                        PieceType.KNIGHT,
                        Color.BLACK
                )
        );


        return new Position(
                board,
                Color.WHITE
        );
    }


    private Position searchReversiblePath(
            Position position,
            int depth,
            Map<PositionKey, Integer> occurrenceCounts,
            List<Move> path
    ) {

        reversibleSearchNodes++;


        if (reversibleSearchNodes
                > REVERSIBLE_PATH_SEARCH_LIMIT) {

            return null;
        }


        if (depth
                == SEVENTY_FIVE_MOVE_HALF_MOVES) {

            GameState state =
                    gameStateEvaluator.evaluate(
                            position
                    );


            if (state
                    == GameState.DRAW_75_MOVE) {

                return position;
            }


            return null;
        }


        List<Move> candidates =
                getSafeReversibleMoves(
                        position,
                        occurrenceCounts,
                        depth
                );


        /*
         * Prefer moves whose resulting PositionKey has been seen the
         * fewest times. This gives the DFS a strong anti-repetition
         * bias while retaining full backtracking correctness.
         */
        candidates.sort(
                (first, second) -> {

                    Position firstPosition =
                            position.makeMove(
                                    first
                            );

                    Position secondPosition =
                            position.makeMove(
                                    second
                            );


                    int firstCount =
                            occurrenceCounts.getOrDefault(
                                    firstPosition.createPositionKey(),
                                    0
                            );


                    int secondCount =
                            occurrenceCounts.getOrDefault(
                                    secondPosition.createPositionKey(),
                                    0
                            );


                    return Integer.compare(
                            firstCount,
                            secondCount
                    );
                }
        );


        for (Move move :
                candidates) {

            Position next =
                    position.makeMove(
                            move
                    );


            PositionKey key =
                    next.createPositionKey();


            occurrenceCounts.put(
                    key,
                    occurrenceCounts.getOrDefault(
                            key,
                            0
                    ) + 1
            );


            path.add(
                    move
            );


            Position result =
                    searchReversiblePath(
                            next,
                            depth + 1,
                            occurrenceCounts,
                            path
                    );


            if (result != null) {

                return result;
            }


            path.remove(
                    path.size() - 1
            );


            int count =
                    occurrenceCounts.get(
                            key
                    ) - 1;


            if (count == 0) {

                occurrenceCounts.remove(
                        key
                );

            } else {

                occurrenceCounts.put(
                        key,
                        count
                );
            }


            if (reversibleSearchNodes
                    > REVERSIBLE_PATH_SEARCH_LIMIT) {

                return null;
            }
        }


        return null;
    }


    private List<Move> getSafeReversibleMoves(
            Position position,
            Map<PositionKey, Integer> occurrenceCounts,
            int depth
    ) {

        List<Move> legalMoves =
                moveGenerator.generateLegalMoves(
                        position
                );


        List<Move> result =
                new ArrayList<>();


        for (Move move :
                legalMoves) {

            Piece movingPiece =
                    position
                            .getBoard()
                            .getPiece(
                                    move.from()
                            );


            if (movingPiece == null
                    ||
                    movingPiece.type()
                            == PieceType.PAWN) {

                continue;
            }


            /*
             * Captures reset the clock, so exclude them.
             */
            if (position
                    .getBoard()
                    .getPiece(
                            move.to()
                    )
                    != null) {

                continue;
            }


            Position candidate =
                    position.makeMove(
                            move
                    );


            PositionKey key =
                    candidate.createPositionKey();


            int previousOccurrences =
                    occurrenceCounts.getOrDefault(
                            key,
                            0
                    );


            /*
             * Refuse a fifth occurrence so fivefold repetition cannot
             * terminate the test before the 75-move threshold.
             */
            if (previousOccurrences >= 4) {

                continue;
            }


            GameState state =
                    gameStateEvaluator.evaluate(
                            candidate
                    );


            if (state
                    == GameState.CHECKMATE
                    ||
                    state
                            == GameState.STALEMATE
                    ||
                    state
                            == GameState.DRAW_FIVEFOLD_REPETITION) {

                continue;
            }


            /*
             * The 75-move draw may appear only on the move that reaches
             * 150 reversible half-moves.
             */
            if (state
                    == GameState.DRAW_75_MOVE
                    &&
                    depth + 1
                            < SEVENTY_FIVE_MOVE_HALF_MOVES) {

                continue;
            }


            result.add(
                    move
            );
        }


        return result;
    }


    private void recordOccurrence(
            Map<PositionKey, Integer> occurrenceCounts,
            Position position
    ) {

        PositionKey key =
                position.createPositionKey();


        occurrenceCounts.put(
                key,
                occurrenceCounts.getOrDefault(
                        key,
                        0
                ) + 1
        );
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
                        to,
                        null
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
            String to,
            PieceType promotion
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

            if (!move.from().equals(
                    fromSquare
            )
                    ||
                    !move.to().equals(
                            toSquare
                    )) {

                continue;
            }


            if (promotion == null) {

                if (move.promotion() == null) {

                    return move;
                }

            } else if (move.promotion()
                    == promotion) {

                return move;
            }
        }


        return null;
    }


    // =========================================================
    // SQUARE HELPER
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

    private void expectPiece(
            String name,
            Piece actual,
            PieceType expectedType,
            Color expectedColor
    ) {

        if (actual != null
                &&
                actual.type()
                        == expectedType
                &&
                actual.color()
                        == expectedColor) {

            pass(
                    name
            );

        } else {

            fail(
                    name,
                    "Expected "
                            + expectedColor
                            + " "
                            + expectedType
                            + " but got "
                            + actual
                            + "."
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

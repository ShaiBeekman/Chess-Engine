package main.java.chess.evaluation;

import main.java.chess.model.Board;
import main.java.chess.model.Color;
import main.java.chess.model.Piece;
import main.java.chess.model.PieceType;
import main.java.chess.model.Position;
import main.java.chess.model.Square;

import java.util.HashMap;


/**
 * Milestone 22 verification.
 *
 * Proves that PositionEvaluator automatically uses the persisted exact
 * Tier-0 tablebase before heuristic evaluation.
 */
public final class PositionEvaluatorTablebaseVerificationMain {

    private PositionEvaluatorTablebaseVerificationMain() {

    }


    public static void main(
            String[] args
    ) {

        PositionEvaluator evaluator =
                new PositionEvaluator();


        System.out.println(
                "PositionEvaluator exact Tier-0 verification"
        );

        System.out.println(
                "==========================================="
        );


        /*
         * This is the same deterministic KQBK position previously
         * verified through FourPieceGenericTablebaseService.
         *
         * Exact result:
         *
         *     White to move
         *     WIN
         *     DTM 7
         */
        Position whiteWinningKqbk =
                position(
                        Color.WHITE,

                        piece(
                                "a1",
                                PieceType.KING,
                                Color.WHITE
                        ),

                        piece(
                                "g8",
                                PieceType.KING,
                                Color.BLACK
                        ),

                        piece(
                                "d4",
                                PieceType.QUEEN,
                                Color.WHITE
                        ),

                        piece(
                                "e4",
                                PieceType.BISHOP,
                                Color.WHITE
                        )
                );


        int winningScore =
                evaluator.evaluate(
                        whiteWinningKqbk
                );


        int expectedWinningScore =
                PositionEvaluator.TABLEBASE_MATE_SCORE
                        - 7;


        System.out.println(
                "KQBK White-to-move exact WIN"
        );

        System.out.println(
                "  Evaluation: "
                        + winningScore
        );

        System.out.println(
                "  Expected:   "
                        + expectedWinningScore
        );


        require(
                winningScore
                        == expectedWinningScore,
                "White exact WIN was not used by PositionEvaluator."
        );


        /*
         * Complete color reversal.
         *
         * The side to move is now Black and Black has the same forced
         * win. Since PositionEvaluator is White-centric, the numerical
         * score must be the exact negative.
         */
        Position blackWinningKqbk =
                position(
                        Color.BLACK,

                        piece(
                                "a1",
                                PieceType.KING,
                                Color.BLACK
                        ),

                        piece(
                                "g8",
                                PieceType.KING,
                                Color.WHITE
                        ),

                        piece(
                                "d4",
                                PieceType.QUEEN,
                                Color.BLACK
                        ),

                        piece(
                                "e4",
                                PieceType.BISHOP,
                                Color.BLACK
                        )
                );


        int blackWinningScore =
                evaluator.evaluate(
                        blackWinningKqbk
                );


        System.out.println();

        System.out.println(
                "KQBK Black-to-move exact WIN"
        );

        System.out.println(
                "  Evaluation: "
                        + blackWinningScore
        );

        System.out.println(
                "  Expected:   "
                        + (-expectedWinningScore)
        );


        require(
                blackWinningScore
                        == -expectedWinningScore,
                "Black exact WIN was not converted to White-centric score."
        );


        /*
         * KNNK deterministic draw from Milestone 21.
         *
         * Heuristic material evaluation would normally strongly favor
         * White here.
         *
         * The exact tablebase must override that and return exactly 0.
         */
        Position knnkDraw =
                position(
                        Color.WHITE,

                        piece(
                                "a1",
                                PieceType.KING,
                                Color.WHITE
                        ),

                        piece(
                                "h8",
                                PieceType.KING,
                                Color.BLACK
                        ),

                        piece(
                                "c3",
                                PieceType.KNIGHT,
                                Color.WHITE
                        ),

                        piece(
                                "f5",
                                PieceType.KNIGHT,
                                Color.WHITE
                        )
                );


        int drawScore =
                evaluator.evaluate(
                        knnkDraw
                );


        System.out.println();

        System.out.println(
                "KNNK exact DRAW"
        );

        System.out.println(
                "  Evaluation: "
                        + drawScore
        );

        System.out.println(
                "  Expected:   0"
        );


        require(
                drawScore == 0,
                "Exact KNNK draw did not override heuristic material advantage."
        );


        /*
         * Verify that unsupported ordinary positions still reach the
         * heuristic evaluator rather than receiving a tablebase score.
         *
         * This position includes a pawn, so Tier 0 cannot support it.
         */
        Position unsupported =
                position(
                        Color.WHITE,

                        piece(
                                "a1",
                                PieceType.KING,
                                Color.WHITE
                        ),

                        piece(
                                "h8",
                                PieceType.KING,
                                Color.BLACK
                        ),

                        piece(
                                "d4",
                                PieceType.QUEEN,
                                Color.WHITE
                        ),

                        piece(
                                "e4",
                                PieceType.PAWN,
                                Color.WHITE
                        )
                );


        int fallbackScore =
                evaluator.evaluate(
                        unsupported
                );


        System.out.println();

        System.out.println(
                "Unsupported Tier-1 material"
        );

        System.out.println(
                "  Heuristic evaluation: "
                        + fallbackScore
        );


        require(
                Math.abs(
                        fallbackScore
                )
                        < PositionEvaluator.TABLEBASE_MATE_SCORE,
                "Unsupported position incorrectly received an exact mate score."
        );


        System.out.println();

        System.out.println(
                "POSITION EVALUATOR EXACT TIER-0 INTEGRATION PASSED"
        );

        System.out.println(
                "NORMAL ENGINE EVALUATION NOW USES FOUR-PIECE TABLEBASES"
        );
    }


    private static Position position(
            Color sideToMove,
            PlacedPiece... pieces
    ) {

        Board board =
                new Board();


        for (PlacedPiece placed :
                pieces) {

            board.setPiece(
                    placed.square(),
                    placed.piece()
            );
        }


        return new Position(
                board,
                sideToMove,
                false,
                false,
                false,
                false,
                null,
                0,
                1,
                new HashMap<>()
        );
    }


    private static PlacedPiece piece(
            String square,
            PieceType type,
            Color color
    ) {

        return new PlacedPiece(
                Square.fromAlgebraic(
                        square
                ),
                new Piece(
                        type,
                        color
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


    private record PlacedPiece(
            Square square,
            Piece piece
    ) {

    }
}
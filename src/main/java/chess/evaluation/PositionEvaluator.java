package main.java.chess.evaluation;

import main.java.chess.model.Board;
import main.java.chess.model.Color;
import main.java.chess.model.Piece;
import main.java.chess.model.PieceType;
import main.java.chess.model.Position;
import main.java.chess.model.Square;
import main.java.chess.rules.MoveGenerator;

public class PositionEvaluator {

    /*
     * Positional weights are deliberately
     * much smaller than material values.
     */
    private static final int MOBILITY_WEIGHT = 2;

    private static final int DEVELOPMENT_BONUS = 15;

    private static final int CENTER_OCCUPATION_BONUS = 15;


    // =========================
    // Main evaluation
    // =========================

    public int evaluate(
            Position position
    ) {

        int material =
                evaluateMaterial(
                        position
                );

        int mobility =
                evaluateMobility(
                        position
                );

        int development =
                evaluateDevelopment(
                        position
                );

        int center =
                evaluateCenterOccupation(
                        position
                );

        return material
                + mobility
                + development
                + center;
    }


    // =========================
    // Material
    // =========================

    private int evaluateMaterial(
            Position position
    ) {

        Board board =
                position.getBoard();

        int score = 0;

        for (int file = 0;
             file < 8;
             file++) {

            for (int rank = 0;
                 rank < 8;
                 rank++) {

                Square square =
                        new Square(
                                file,
                                rank
                        );

                Piece piece =
                        board.getPiece(
                                square
                        );

                if (piece == null) {
                    continue;
                }

                int value =
                        getPieceValue(
                                piece.type()
                        );

                if (piece.color()
                        == Color.WHITE) {

                    score += value;

                } else {

                    score -= value;
                }
            }
        }

        return score;
    }


    private int getPieceValue(
            PieceType type
    ) {

        return switch (type) {

            case PAWN ->
                    100;

            case KNIGHT ->
                    320;

            case BISHOP ->
                    330;

            case ROOK ->
                    500;

            case QUEEN ->
                    900;

            /*
             * The king does not receive a
             * material value.
             *
             * Checkmate will eventually be
             * handled separately and outrank
             * every numerical evaluation.
             */
            case KING ->
                    0;
        };
    }


    // =========================
    // Mobility
    // =========================

    private int evaluateMobility(
            Position position
    ) {

        MoveGenerator generator =
                new MoveGenerator();

        /*
         * Current side's legal mobility.
         */
        int currentMobility =
                generator
                        .generateLegalMoves(
                                position
                        )
                        .size();

        /*
         * Create an equivalent position
         * with the opposite side to move
         * so we can estimate the opponent's
         * mobility as well.
         */
        Color oppositeColor;

        if (position.getSideToMove()
                == Color.WHITE) {

            oppositeColor =
                    Color.BLACK;

        } else {

            oppositeColor =
                    Color.WHITE;
        }

        Position oppositePosition =
                new Position(
                        position.getBoard(),
                        oppositeColor
                );

        int oppositeMobility =
                generator
                        .generateLegalMoves(
                                oppositePosition
                        )
                        .size();

        int whiteMobility;
        int blackMobility;

        if (position.getSideToMove()
                == Color.WHITE) {

            whiteMobility =
                    currentMobility;

            blackMobility =
                    oppositeMobility;

        } else {

            blackMobility =
                    currentMobility;

            whiteMobility =
                    oppositeMobility;
        }

        return (
                whiteMobility
                        - blackMobility
        ) * MOBILITY_WEIGHT;
    }


    // =========================
    // Development
    // =========================

    private int evaluateDevelopment(
            Position position
    ) {

        Board board =
                position.getBoard();

        int score = 0;


        /*
         * White knights.
         */
        score += developmentScore(
                board,
                "b1",
                PieceType.KNIGHT,
                Color.WHITE
        );

        score += developmentScore(
                board,
                "g1",
                PieceType.KNIGHT,
                Color.WHITE
        );


        /*
         * White bishops.
         */
        score += developmentScore(
                board,
                "c1",
                PieceType.BISHOP,
                Color.WHITE
        );

        score += developmentScore(
                board,
                "f1",
                PieceType.BISHOP,
                Color.WHITE
        );


        /*
         * Black knights.
         */
        score -= developmentScore(
                board,
                "b8",
                PieceType.KNIGHT,
                Color.BLACK
        );

        score -= developmentScore(
                board,
                "g8",
                PieceType.KNIGHT,
                Color.BLACK
        );


        /*
         * Black bishops.
         */
        score -= developmentScore(
                board,
                "c8",
                PieceType.BISHOP,
                Color.BLACK
        );

        score -= developmentScore(
                board,
                "f8",
                PieceType.BISHOP,
                Color.BLACK
        );

        return score;
    }


    private int developmentScore(
            Board board,
            String startingSquare,
            PieceType type,
            Color color
    ) {

        Square square =
                Square.fromAlgebraic(
                        startingSquare
                );

        Piece piece =
                board.getPiece(
                        square
                );

        /*
         * If the original minor piece is no
         * longer sitting on its starting
         * square, regard it as developed.
         *
         * This is intentionally simple for
         * our first positional evaluator.
         */
        if (piece == null) {

            return DEVELOPMENT_BONUS;
        }

        if (piece.type() != type
                || piece.color() != color) {

            return DEVELOPMENT_BONUS;
        }

        return 0;
    }


    // =========================
    // Center occupation
    // =========================

    private int evaluateCenterOccupation(
            Position position
    ) {

        Board board =
                position.getBoard();

        int score = 0;

        String[] centerSquares = {
                "d4",
                "e4",
                "d5",
                "e5"
        };

        for (String squareName :
                centerSquares) {

            Square square =
                    Square.fromAlgebraic(
                            squareName
                    );

            Piece piece =
                    board.getPiece(
                            square
                    );

            if (piece == null) {
                continue;
            }

            if (piece.color()
                    == Color.WHITE) {

                score +=
                        CENTER_OCCUPATION_BONUS;

            } else {

                score -=
                        CENTER_OCCUPATION_BONUS;
            }
        }

        return score;
    }
}
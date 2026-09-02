package main.java.chess.evaluation;

import main.java.chess.endgame.ExactEndgameTablebase;
import main.java.chess.model.Board;
import main.java.chess.model.Color;
import main.java.chess.model.Piece;
import main.java.chess.model.PieceType;
import main.java.chess.model.Position;
import main.java.chess.model.Square;
import main.java.chess.rules.MoveGenerator;


public class PositionEvaluator {

    /*
     * Exact tablebase scores must dominate every normal positional or
     * material evaluation.
     */
    public static final int TABLEBASE_MATE_SCORE =
            1_000_000;


    /*
     * Positional weights are deliberately
     * much smaller than material values.
     */
    private static final int MOBILITY_WEIGHT =
            2;

    private static final int DEVELOPMENT_BONUS =
            15;

    private static final int CENTER_OCCUPATION_BONUS =
            15;


    private final ExactEndgameTablebase
            exactEndgameTablebase;


    /**
     * Normal engine constructor.
     *
     * This does NOT load all twenty four-piece assets.
     *
     * The underlying service loads one material table lazily the first
     * time that particular material family is encountered.
     */
    public PositionEvaluator() {

        this(
                ExactEndgameTablebase.tierZeroCatalog()
        );
    }


    /**
     * Dependency-injection constructor.
     *
     * Useful for testing and for future engine configurations.
     */
    public PositionEvaluator(
            ExactEndgameTablebase exactEndgameTablebase
    ) {

        this.exactEndgameTablebase =
                exactEndgameTablebase;
    }


    // =========================
    // Main evaluation
    // =========================

    public int evaluate(
            Position position
    ) {

        /*
         * Exact endgame knowledge has priority over heuristic
         * evaluation.
         */
        Integer exactScore =
                evaluateExactEndgame(
                        position
                );


        if (exactScore != null) {

            return exactScore;
        }


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
    // Exact endgame
    // =========================

    /**
     * Return an exact White-centric score when tablebase information is
     * available.
     *
     * null means:
     *
     *     no exact tablebase supports this position
     *
     * and normal heuristic evaluation should continue.
     *
     *
     * Tablebase WDL is from the side-to-move perspective.
     *
     * PositionEvaluator scores are from White's perspective:
     *
     *     positive = good for White
     *     negative = good for Black
     */
    private Integer evaluateExactEndgame(
            Position position
    ) {

        if (exactEndgameTablebase == null) {

            return null;
        }


        ExactEndgameTablebase.Probe probe =
                exactEndgameTablebase.probe(
                        position
                );


        if (probe.outcome()
                == ExactEndgameTablebase.Outcome.UNSUPPORTED) {

            return null;
        }


        if (probe.outcome()
                == ExactEndgameTablebase.Outcome.DRAW) {

            return 0;
        }


        int distance =
                Math.max(
                        0,
                        probe.mateDistance()
                );


        /*
         * Smaller DTM is preferable when winning.
         *
         * When losing, larger DTM is preferable because it delays the
         * forced mate.
         */
        int sideToMoveScore =
                switch (probe.outcome()) {

                    case WIN ->
                            TABLEBASE_MATE_SCORE
                                    - distance;

                    case LOSS ->
                            -TABLEBASE_MATE_SCORE
                                    + distance;

                    case DRAW ->
                            0;

                    case UNSUPPORTED ->
                            throw new IllegalStateException(
                                    "UNSUPPORTED tablebase outcome reached exact scoring."
                            );
                };


        /*
         * Convert side-to-move perspective into the evaluator's
         * White-centric perspective.
         */
        if (position.getSideToMove()
                == Color.WHITE) {

            return sideToMoveScore;
        }


        return -sideToMoveScore;
    }


    // =========================
    // Material
    // =========================

    private int evaluateMaterial(
            Position position
    ) {

        Board board =
                position.getBoard();

        int score =
                0;


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

                    score +=
                            value;

                } else {

                    score -=
                            value;
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


        int currentMobility =
                generator
                        .generateLegalMoves(
                                position
                        )
                        .size();


        Color oppositeColor =
                position.getSideToMove()
                        .opposite();


        /*
         * Preserve the position's rule metadata rather than using the
         * two-argument Position constructor, because that constructor
         * assumes all four castling rights are available.
         *
         * Repetition history does not affect this temporary mobility
         * estimate.
         */
        Position oppositePosition =
                new Position(
                        position.getBoard(),
                        oppositeColor,
                        position.canWhiteCastleKingSide(),
                        position.canWhiteCastleQueenSide(),
                        position.canBlackCastleKingSide(),
                        position.canBlackCastleQueenSide(),
                        position.getEnPassantTarget(),
                        position.getHalfmoveClock(),
                        position.getFullmoveNumber(),
                        java.util.Map.of()
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

        int score =
                0;


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

        int score =
                0;


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
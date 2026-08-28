package main.java.chess.util;

import main.java.chess.model.Move;
import main.java.chess.model.PieceType;
import main.java.chess.model.Position;
import main.java.chess.model.Square;
import main.java.chess.rules.GameStateEvaluator;
import main.java.chess.model.GameState;

public class MoveFormatter {

    private final GameStateEvaluator gameStateEvaluator;

    public MoveFormatter() {

        this.gameStateEvaluator =
                new GameStateEvaluator();
    }

    public String format(
            Position positionBeforeMove,
            Move move
    ) {

        StringBuilder builder =
                new StringBuilder();

        builder.append(
                squareToString(
                        move.from()
                )
        );

        builder.append("-");

        builder.append(
                squareToString(
                        move.to()
                )
        );

        /*
         * Promotion.
         */
        if (move.promotion() != null) {

            builder.append("=");

            builder.append(
                    promotionLetter(
                            move.promotion()
                    )
            );
        }

        /*
         * Determine whether the resulting
         * position is check or checkmate.
         */
        Position positionAfterMove =
                positionBeforeMove.makeMove(
                        move
                );

        GameState gameState =
                gameStateEvaluator.evaluate(
                        positionAfterMove
                );

        if (gameState == GameState.CHECKMATE) {

            builder.append("#");

        } else if (gameState == GameState.CHECK) {

            builder.append("+");
        }

        return builder.toString();
    }

    private String squareToString(
            Square square
    ) {

        char file =
                (char) (
                        'a'
                                + square.file()
                );

        int rank =
                square.rank() + 1;

        return ""
                + file
                + rank;
    }

    private String promotionLetter(
            PieceType pieceType
    ) {

        return switch (pieceType) {

            case QUEEN -> "Q";

            case ROOK -> "R";

            case BISHOP -> "B";

            case KNIGHT -> "N";

            default -> "";
        };
    }
}
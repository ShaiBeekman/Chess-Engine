package main.java.chess.rules;

import main.java.chess.model.Board;
import main.java.chess.model.Color;
import main.java.chess.model.GameState;
import main.java.chess.model.Move;
import main.java.chess.model.Piece;
import main.java.chess.model.PieceType;
import main.java.chess.model.Position;
import main.java.chess.model.Square;

import java.util.List;

public class GameStateEvaluator {

    private final AttackDetector attackDetector =
            new AttackDetector();

    private final MoveGenerator moveGenerator =
            new MoveGenerator();

    public GameState evaluate(Position position) {

        Board board =
                position.getBoard();

        Color sideToMove =
                position.getSideToMove();

        Square kingSquare =
                findKing(
                        board,
                        sideToMove
                );

        if (kingSquare == null) {
            throw new IllegalStateException(
                    "King not found."
            );
        }

        boolean inCheck =
                attackDetector.isSquareAttacked(
                        board,
                        kingSquare,
                        sideToMove.opposite()
                );

        List<Move> legalMoves =
                moveGenerator.generateLegalMoves(
                        position
                );

        boolean hasLegalMoves =
                !legalMoves.isEmpty();

        // Checkmate must be checked first.
        if (inCheck && !hasLegalMoves) {
            return GameState.CHECKMATE;
        }

        // No legal moves without check = stalemate.
        if (!inCheck && !hasLegalMoves) {
            return GameState.STALEMATE;
        }

        // Five occurrences of the same position
        // produce an automatic draw.
        if (position.getCurrentRepetitionCount() >= 5) {
            return GameState.DRAW_FIVEFOLD_REPETITION;
        }

        // 150 halfmoves = 75 moves by each player
        // without a pawn move or capture.
        if (position.getHalfmoveClock() >= 150) {
            return GameState.DRAW_75_MOVE;
        }

        // The king is attacked, but legal responses exist.
        if (inCheck) {
            return GameState.CHECK;
        }

        return GameState.ONGOING;
    }

    private Square findKing(
            Board board,
            Color color
    ) {

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
                        board.getPiece(
                                square
                        );

                if (piece == null) {
                    continue;
                }

                if (piece.type() == PieceType.KING
                        && piece.color() == color) {

                    return square;
                }
            }
        }

        return null;
    }
}
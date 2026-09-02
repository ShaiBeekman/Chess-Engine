package main.java.chess.endgame;

import main.java.chess.model.Board;
import main.java.chess.model.Color;
import main.java.chess.model.Move;
import main.java.chess.model.Piece;
import main.java.chess.model.PieceType;
import main.java.chess.model.Position;
import main.java.chess.model.PositionKey;
import main.java.chess.model.Square;
import main.java.chess.rules.MoveGenerator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Small M59 diagnostic. Does not rebuild any tablebase.
 *
 * Recreates the exact KPPK fixture that failed runtime move classification
 * and prints every legal child together with the unified exact probe result.
 */
public final class FourPieceTierTwoKppkRuntimeChildDiagnosticMain {

    private static final int FIXTURE_STATE = 17426;

    private FourPieceTierTwoKppkRuntimeChildDiagnosticMain() {
    }

    public static void main(String[] args) {
        if (args.length != 0) {
            throw new IllegalArgumentException(
                    "Usage: FourPieceTierTwoKppkRuntimeChildDiagnosticMain");
        }

        Position parent = canonicalPosition(FIXTURE_STATE);
        ExactEndgameTablebase exact = ExactEndgameTablebase.tierZeroCatalog();
        MoveGenerator generator = new MoveGenerator();

        System.out.println("KPPK runtime child diagnostic");
        System.out.println("=============================");
        System.out.println("Fixture state: " + FIXTURE_STATE);

        ExactEndgameTablebase.Probe parentProbe = exact.probe(parent);
        System.out.println("Parent: " + parentProbe.outcome()
                + " DTM " + parentProbe.mateDistance());
        System.out.println("Parent EP target: " + parent.getEnPassantTarget());

        List<Move> moves = generator.generateLegalMoves(parent);
        System.out.println("Legal moves: " + moves.size());
        System.out.println();

        int unsupported = 0;

        for (int i = 0; i < moves.size(); i++) {
            Move move = moves.get(i);
            Position child = parent.makeMove(move);
            ExactEndgameTablebase.Probe probe = exact.probe(child);

            if (probe.outcome() == ExactEndgameTablebase.Outcome.UNSUPPORTED) {
                unsupported++;
            }

            System.out.println("[" + i + "] " + move);
            System.out.println("    child: " + probe.outcome()
                    + " DTM " + probe.mateDistance());
            System.out.println("    side: " + child.getSideToMove());
            System.out.println("    EP target: " + child.getEnPassantTarget());
            System.out.println("    material: " + material(child));
        }

        System.out.println();
        System.out.println("Unsupported children: " + unsupported);

        if (unsupported == 0) {
            System.out.println("All direct child probes are exact.");
            System.out.println("If analyzeMoves() is still empty, inspect its child-routing path next.");
        } else {
            System.out.println("DIAGNOSTIC FOUND THE ROUTING GAP.");
        }
    }

    private static String material(Position position) {
        StringBuilder builder = new StringBuilder();

        for (int rank = 0; rank < 8; rank++) {
            for (int file = 0; file < 8; file++) {
                Square square = new Square(file, rank);
                Piece piece = position.getBoard().getPiece(square);
                if (piece == null) continue;

                if (builder.length() > 0) builder.append(", ");

                builder.append(piece.color())
                        .append(' ')
                        .append(piece.type())
                        .append('@')
                        .append((char) ('a' + file))
                        .append(rank + 1);
            }
        }

        return builder.toString();
    }

    private static Position canonicalPosition(int state) {
        Board board = new Board();

        board.setPiece(
                square(FourPieceGenericPrimitiveState.whiteKing(state)),
                new Piece(PieceType.KING, Color.WHITE));

        board.setPiece(
                square(FourPieceGenericPrimitiveState.blackKing(state)),
                new Piece(PieceType.KING, Color.BLACK));

        board.setPiece(
                square(FourPieceGenericPrimitiveState.firstExtra(state)),
                new Piece(PieceType.PAWN, Color.WHITE));

        board.setPiece(
                square(FourPieceGenericPrimitiveState.secondExtra(state)),
                new Piece(PieceType.PAWN, Color.WHITE));

        return position(
                board,
                FourPieceGenericPrimitiveState.blackToMove(state)
                        ? Color.BLACK
                        : Color.WHITE);
    }

    private static Position position(Board board, Color sideToMove) {
        Position temporary =
                new Position(
                        board,
                        sideToMove,
                        false,
                        false,
                        false,
                        false,
                        null,
                        0,
                        1,
                        new HashMap<>());

        Map<PositionKey, Integer> repetitions = new HashMap<>();
        repetitions.put(temporary.createPositionKey(), 1);

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
                repetitions);
    }

    private static Square square(int primitive) {
        return new Square(primitive & 7, primitive >>> 3);
    }
}

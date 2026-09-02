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
 * M59 compile/source sanity check.
 *
 * This deliberately references FourPieceTierTwoKppkTablebaseService.BUILD_ID.
 * If the project is still using the old service source, this class will not
 * compile. If it compiles and runs, we know the corrected service is actually
 * in the project.
 */
public final class FourPieceTierTwoKppkRuntimeSourceCheckMain {

    private static final int FIXTURE_STATE = 17426;

    private FourPieceTierTwoKppkRuntimeSourceCheckMain() {
    }

    public static void main(String[] args) throws Exception {
        System.out.println("KPPK runtime source check");
        System.out.println("=========================");
        System.out.println("Service BUILD_ID: "
                + FourPieceTierTwoKppkTablebaseService.BUILD_ID);

        FourPieceTierTwoKppkTablebaseService service =
                new FourPieceTierTwoKppkTablebaseService();

        Position parent = canonicalPosition(FIXTURE_STATE);
        List<Move> moves = new MoveGenerator().generateLegalMoves(parent);

        int epChildren = 0;
        int epSupported = 0;

        for (Move move : moves) {
            Position child = parent.makeMove(move);

            if (child.getEnPassantTarget() == null) {
                continue;
            }

            epChildren++;

            boolean supports = service.supports(child);
            boolean probes = service.probe(child).isPresent();

            if (supports && probes) {
                epSupported++;
            }

            System.out.println(move);
            System.out.println("  EP target: " + child.getEnPassantTarget());
            System.out.println("  supports: " + supports);
            System.out.println("  probe present: " + probes);
        }

        System.out.println();
        System.out.println("EP children: " + epChildren);
        System.out.println("EP children supported: " + epSupported);

        if (!"M59-KPPK-EP-IGNORED-V2".equals(
                FourPieceTierTwoKppkTablebaseService.BUILD_ID)) {
            throw new IllegalStateException("Unexpected KPPK service BUILD_ID.");
        }

        if (epChildren != 2 || epSupported != 2) {
            throw new IllegalStateException(
                    "Corrected KPPK service is compiled, but EP-bearing KPPK children are still rejected.");
        }

        System.out.println("KPPK SOURCE / COMPILE CHECK PASSED");
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

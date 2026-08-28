package main.java.chess.rules;

import main.java.chess.model.Board;
import main.java.chess.model.Color;
import main.java.chess.model.Move;
import main.java.chess.model.Piece;
import main.java.chess.model.PieceType;
import main.java.chess.model.Position;
import main.java.chess.model.Square;

import java.util.ArrayList;
import java.util.List;

public class MoveGenerator {

    private final AttackDetector attackDetector =
            new AttackDetector();

    public List<Move> generateLegalMoves(
            Position position
    ) {

        List<Move> candidateMoves =
                new ArrayList<>();

        Board board = position.getBoard();
        Color sideToMove =
                position.getSideToMove();

        for (int rank = 0; rank < 8; rank++) {
            for (int file = 0; file < 8; file++) {

                Square from =
                        new Square(file, rank);

                Piece piece =
                        board.getPiece(from);

                if (piece == null) {
                    continue;
                }

                if (piece.color() != sideToMove) {
                    continue;
                }

                if (piece.type() == PieceType.KING) {

                    generateKingMoves(
                            board,
                            from,
                            piece.color(),
                            candidateMoves
                    );

                    generateKingSideCastling(
                            position,
                            from,
                            piece.color(),
                            candidateMoves
                    );

                    generateQueenSideCastling(
                            position,
                            from,
                            piece.color(),
                            candidateMoves
                    );
                }

                if (piece.type() == PieceType.ROOK) {

                    generateRookMoves(
                            board,
                            from,
                            piece.color(),
                            candidateMoves
                    );
                }

                if (piece.type() == PieceType.BISHOP) {

                    generateBishopMoves(
                            board,
                            from,
                            piece.color(),
                            candidateMoves
                    );
                }

                if (piece.type() == PieceType.QUEEN) {

                    generateQueenMoves(
                            board,
                            from,
                            piece.color(),
                            candidateMoves
                    );
                }

                if (piece.type() == PieceType.KNIGHT) {

                    generateKnightMoves(
                            board,
                            from,
                            piece.color(),
                            candidateMoves
                    );
                }

                if (piece.type() == PieceType.PAWN) {

                    generatePawnMoves(
                            position,
                            from,
                            piece.color(),
                            candidateMoves
                    );
                }
            }
        }

        List<Move> legalMoves =
                new ArrayList<>();

        for (Move move : candidateMoves) {

            Board testBoard =
                    createTestBoard(
                            position,
                            move
                    );

            Square kingSquare =
                    findKing(
                            testBoard,
                            sideToMove
                    );

            if (kingSquare == null) {
                continue;
            }

            if (!attackDetector.isSquareAttacked(
                    testBoard,
                    kingSquare,
                    sideToMove.opposite()
            )) {

                legalMoves.add(move);
            }
        }

        return legalMoves;
    }

    private void generateKingMoves(
            Board board,
            Square from,
            Color color,
            List<Move> candidateMoves
    ) {

        for (int rankOffset = -1;
             rankOffset <= 1;
             rankOffset++) {

            for (int fileOffset = -1;
                 fileOffset <= 1;
                 fileOffset++) {

                if (rankOffset == 0
                        && fileOffset == 0) {
                    continue;
                }

                int newFile =
                        from.file() + fileOffset;

                int newRank =
                        from.rank() + rankOffset;

                if (!isOnBoard(
                        newFile,
                        newRank
                )) {
                    continue;
                }

                Square to =
                        new Square(
                                newFile,
                                newRank
                        );

                Piece targetPiece =
                        board.getPiece(to);

                if (targetPiece != null
                        && targetPiece.color() == color) {
                    continue;
                }

                candidateMoves.add(
                        new Move(from, to)
                );
            }
        }
    }

    private void generateKingSideCastling(
            Position position,
            Square from,
            Color color,
            List<Move> candidateMoves
    ) {

        Board board =
                position.getBoard();

        Square kingStart;
        Square rookStart;
        Square throughSquare;
        Square destination;

        boolean hasCastlingRight;

        if (color == Color.WHITE) {

            kingStart =
                    Square.fromAlgebraic("e1");

            rookStart =
                    Square.fromAlgebraic("h1");

            throughSquare =
                    Square.fromAlgebraic("f1");

            destination =
                    Square.fromAlgebraic("g1");

            hasCastlingRight =
                    position.canWhiteCastleKingSide();

        } else {

            kingStart =
                    Square.fromAlgebraic("e8");

            rookStart =
                    Square.fromAlgebraic("h8");

            throughSquare =
                    Square.fromAlgebraic("f8");

            destination =
                    Square.fromAlgebraic("g8");

            hasCastlingRight =
                    position.canBlackCastleKingSide();
        }

        if (!from.equals(kingStart)) {
            return;
        }

        if (!hasCastlingRight) {
            return;
        }

        Piece rook =
                board.getPiece(rookStart);

        if (rook == null
                || rook.type() != PieceType.ROOK
                || rook.color() != color) {
            return;
        }

        if (!board.isEmpty(throughSquare)
                || !board.isEmpty(destination)) {
            return;
        }

        if (attackDetector.isSquareAttacked(
                board,
                kingStart,
                color.opposite()
        )) {
            return;
        }

        Board throughBoard =
                new Board(board);

        Piece king =
                throughBoard.getPiece(kingStart);

        throughBoard.removePiece(kingStart);
        throughBoard.setPiece(
                throughSquare,
                king
        );

        if (attackDetector.isSquareAttacked(
                throughBoard,
                throughSquare,
                color.opposite()
        )) {
            return;
        }

        candidateMoves.add(
                new Move(
                        kingStart,
                        destination
                )
        );
    }

    private void generateQueenSideCastling(
            Position position,
            Square from,
            Color color,
            List<Move> candidateMoves
    ) {

        Board board =
                position.getBoard();

        Square kingStart;
        Square rookStart;
        Square throughSquare;
        Square destination;
        Square extraEmptySquare;

        boolean hasCastlingRight;

        if (color == Color.WHITE) {

            kingStart =
                    Square.fromAlgebraic("e1");

            rookStart =
                    Square.fromAlgebraic("a1");

            throughSquare =
                    Square.fromAlgebraic("d1");

            destination =
                    Square.fromAlgebraic("c1");

            extraEmptySquare =
                    Square.fromAlgebraic("b1");

            hasCastlingRight =
                    position.canWhiteCastleQueenSide();

        } else {

            kingStart =
                    Square.fromAlgebraic("e8");

            rookStart =
                    Square.fromAlgebraic("a8");

            throughSquare =
                    Square.fromAlgebraic("d8");

            destination =
                    Square.fromAlgebraic("c8");

            extraEmptySquare =
                    Square.fromAlgebraic("b8");

            hasCastlingRight =
                    position.canBlackCastleQueenSide();
        }

        if (!from.equals(kingStart)) {
            return;
        }

        if (!hasCastlingRight) {
            return;
        }

        Piece rook =
                board.getPiece(rookStart);

        if (rook == null
                || rook.type() != PieceType.ROOK
                || rook.color() != color) {
            return;
        }

        if (!board.isEmpty(throughSquare)
                || !board.isEmpty(destination)
                || !board.isEmpty(extraEmptySquare)) {
            return;
        }

        if (attackDetector.isSquareAttacked(
                board,
                kingStart,
                color.opposite()
        )) {
            return;
        }

        Board throughBoard =
                new Board(board);

        Piece king =
                throughBoard.getPiece(kingStart);

        throughBoard.removePiece(kingStart);

        throughBoard.setPiece(
                throughSquare,
                king
        );

        if (attackDetector.isSquareAttacked(
                throughBoard,
                throughSquare,
                color.opposite()
        )) {
            return;
        }

        candidateMoves.add(
                new Move(
                        kingStart,
                        destination
                )
        );
    }

    private void generateRookMoves(
            Board board,
            Square from,
            Color color,
            List<Move> candidateMoves
    ) {

        int[][] directions = {
                {0, 1},
                {0, -1},
                {1, 0},
                {-1, 0}
        };

        generateSlidingMoves(
                board,
                from,
                color,
                candidateMoves,
                directions
        );
    }

    private void generateBishopMoves(
            Board board,
            Square from,
            Color color,
            List<Move> candidateMoves
    ) {

        int[][] directions = {
                {1, 1},
                {-1, 1},
                {1, -1},
                {-1, -1}
        };

        generateSlidingMoves(
                board,
                from,
                color,
                candidateMoves,
                directions
        );
    }

    private void generateQueenMoves(
            Board board,
            Square from,
            Color color,
            List<Move> candidateMoves
    ) {

        int[][] directions = {
                {0, 1},
                {0, -1},
                {1, 0},
                {-1, 0},
                {1, 1},
                {-1, 1},
                {1, -1},
                {-1, -1}
        };

        generateSlidingMoves(
                board,
                from,
                color,
                candidateMoves,
                directions
        );
    }

    private void generateSlidingMoves(
            Board board,
            Square from,
            Color color,
            List<Move> candidateMoves,
            int[][] directions
    ) {

        for (int[] direction : directions) {

            int fileOffset =
                    direction[0];

            int rankOffset =
                    direction[1];

            int newFile =
                    from.file() + fileOffset;

            int newRank =
                    from.rank() + rankOffset;

            while (isOnBoard(
                    newFile,
                    newRank
            )) {

                Square to =
                        new Square(
                                newFile,
                                newRank
                        );

                Piece targetPiece =
                        board.getPiece(to);

                if (targetPiece == null) {

                    candidateMoves.add(
                            new Move(from, to)
                    );

                } else {

                    if (targetPiece.color() != color) {

                        candidateMoves.add(
                                new Move(from, to)
                        );
                    }

                    break;
                }

                newFile += fileOffset;
                newRank += rankOffset;
            }
        }
    }

    private void generateKnightMoves(
            Board board,
            Square from,
            Color color,
            List<Move> candidateMoves
    ) {

        int[][] offsets = {
                {1, 2},
                {2, 1},
                {2, -1},
                {1, -2},
                {-1, -2},
                {-2, -1},
                {-2, 1},
                {-1, 2}
        };

        for (int[] offset : offsets) {

            int newFile =
                    from.file() + offset[0];

            int newRank =
                    from.rank() + offset[1];

            if (!isOnBoard(
                    newFile,
                    newRank
            )) {
                continue;
            }

            Square to =
                    new Square(
                            newFile,
                            newRank
                    );

            Piece targetPiece =
                    board.getPiece(to);

            if (targetPiece != null
                    && targetPiece.color() == color) {
                continue;
            }

            candidateMoves.add(
                    new Move(from, to)
            );
        }
    }

    private void generatePawnMoves(
            Position position,
            Square from,
            Color color,
            List<Move> candidateMoves
    ) {

        Board board =
                position.getBoard();

        int direction;

        if (color == Color.WHITE) {
            direction = 1;
        } else {
            direction = -1;
        }

        int newRank =
                from.rank() + direction;

        // Forward one square
        if (isOnBoard(
                from.file(),
                newRank
        )) {

            Square forward =
                    new Square(
                            from.file(),
                            newRank
                    );

            if (board.isEmpty(forward)) {

                addPawnMove(
                        from,
                        forward,
                        color,
                        candidateMoves
                );
            }
        }

        // Initial double move
        int startingRank;

        if (color == Color.WHITE) {
            startingRank = 1;
        } else {
            startingRank = 6;
        }

        if (from.rank() == startingRank) {

            int oneStepRank =
                    from.rank() + direction;

            int twoStepRank =
                    from.rank()
                            + (2 * direction);

            Square oneStep =
                    new Square(
                            from.file(),
                            oneStepRank
                    );

            Square twoStep =
                    new Square(
                            from.file(),
                            twoStepRank
                    );

            if (board.isEmpty(oneStep)
                    && board.isEmpty(twoStep)) {

                candidateMoves.add(
                        new Move(
                                from,
                                twoStep
                        )
                );
            }
        }

        // Normal diagonal captures
        int[] fileOffsets = {-1, 1};

        for (int fileOffset : fileOffsets) {

            int captureFile =
                    from.file() + fileOffset;

            int captureRank =
                    from.rank() + direction;

            if (!isOnBoard(
                    captureFile,
                    captureRank
            )) {
                continue;
            }

            Square captureSquare =
                    new Square(
                            captureFile,
                            captureRank
                    );

            Piece targetPiece =
                    board.getPiece(captureSquare);

            if (targetPiece != null
                    && targetPiece.color() != color) {

                addPawnMove(
                        from,
                        captureSquare,
                        color,
                        candidateMoves
                );
            }
        }

        // En passant
        Square enPassantTarget =
                position.getEnPassantTarget();

        if (enPassantTarget != null) {

            int rankDifference =
                    enPassantTarget.rank()
                            - from.rank();

            int fileDifference =
                    Math.abs(
                            enPassantTarget.file()
                                    - from.file()
                    );

            if (rankDifference == direction
                    && fileDifference == 1) {

                candidateMoves.add(
                        new Move(
                                from,
                                enPassantTarget
                        )
                );
            }
        }
    }

    private void addPawnMove(
            Square from,
            Square to,
            Color color,
            List<Move> candidateMoves
    ) {

        int promotionRank;

        if (color == Color.WHITE) {
            promotionRank = 7;
        } else {
            promotionRank = 0;
        }

        if (to.rank() == promotionRank) {

            candidateMoves.add(
                    new Move(
                            from,
                            to,
                            PieceType.QUEEN
                    )
            );

            candidateMoves.add(
                    new Move(
                            from,
                            to,
                            PieceType.ROOK
                    )
            );

            candidateMoves.add(
                    new Move(
                            from,
                            to,
                            PieceType.BISHOP
                    )
            );

            candidateMoves.add(
                    new Move(
                            from,
                            to,
                            PieceType.KNIGHT
                    )
            );

        } else {

            candidateMoves.add(
                    new Move(from, to)
            );
        }
    }

    private Board createTestBoard(
            Position position,
            Move move
    ) {

        Board originalBoard =
                position.getBoard();

        Board testBoard =
                new Board(originalBoard);

        Piece movingPiece =
                testBoard.getPiece(move.from());

        boolean isEnPassant =
                movingPiece.type() == PieceType.PAWN
                        && position.getEnPassantTarget() != null
                        && move.to().equals(
                        position.getEnPassantTarget()
                )
                        && testBoard.isEmpty(move.to());

        testBoard.removePiece(move.from());

        if (move.isPromotion()) {

            testBoard.setPiece(
                    move.to(),
                    new Piece(
                            move.promotion(),
                            movingPiece.color()
                    )
            );

        } else {

            testBoard.setPiece(
                    move.to(),
                    movingPiece
            );
        }

        // Remove pawn captured by en passant.
        if (isEnPassant) {

            Square capturedPawnSquare =
                    new Square(
                            move.to().file(),
                            move.from().rank()
                    );

            testBoard.removePiece(
                    capturedPawnSquare
            );
        }

        // Castling
        if (movingPiece.type() == PieceType.KING
                && Math.abs(
                move.to().file()
                        - move.from().file()
        ) == 2) {

            int rank =
                    move.from().rank();

            // Kingside
            if (move.to().file() == 6) {

                Square rookFrom =
                        new Square(7, rank);

                Square rookTo =
                        new Square(5, rank);

                Piece rook =
                        testBoard.getPiece(rookFrom);

                testBoard.removePiece(rookFrom);
                testBoard.setPiece(rookTo, rook);

            }

            // Queenside
            else if (move.to().file() == 2) {

                Square rookFrom =
                        new Square(0, rank);

                Square rookTo =
                        new Square(3, rank);

                Piece rook =
                        testBoard.getPiece(rookFrom);

                testBoard.removePiece(rookFrom);
                testBoard.setPiece(rookTo, rook);
            }
        }

        return testBoard;
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
                        new Square(file, rank);

                Piece piece =
                        board.getPiece(square);

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

    private boolean isOnBoard(
            int file,
            int rank
    ) {

        return file >= 0
                && file < 8
                && rank >= 0
                && rank < 8;
    }
}
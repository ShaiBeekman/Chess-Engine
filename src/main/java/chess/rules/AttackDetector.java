package main.java.chess.rules;

import main.java.chess.model.Board;
import main.java.chess.model.Color;
import main.java.chess.model.Piece;
import main.java.chess.model.PieceType;
import main.java.chess.model.Square;

public class AttackDetector {

    public boolean isSquareAttacked(
            Board board,
            Square square,
            Color attackingColor
    ) {

        if (isAttackedByKing(board, square, attackingColor)) {
            return true;
        }

        if (isAttackedByRook(board, square, attackingColor)) {
            return true;
        }

        if (isAttackedByBishop(board, square, attackingColor)) {
            return true;
        }

        if (isAttackedByQueen(board, square, attackingColor)) {
            return true;
        }

        if (isAttackedByKnight(board, square, attackingColor)) {
            return true;
        }

        if (isAttackedByPawn(board, square, attackingColor)) {
            return true;
        }

        return false;
    }

    private boolean isAttackedByKing(
            Board board,
            Square square,
            Color attackingColor
    ) {

        for (int rankOffset = -1; rankOffset <= 1; rankOffset++) {
            for (int fileOffset = -1; fileOffset <= 1; fileOffset++) {

                if (rankOffset == 0 && fileOffset == 0) {
                    continue;
                }

                int checkFile = square.file() + fileOffset;
                int checkRank = square.rank() + rankOffset;

                if (!isOnBoard(checkFile, checkRank)) {
                    continue;
                }

                Square nearbySquare = new Square(checkFile, checkRank);
                Piece nearbyPiece = board.getPiece(nearbySquare);

                if (nearbyPiece == null) {
                    continue;
                }

                if (nearbyPiece.type() == PieceType.KING
                        && nearbyPiece.color() == attackingColor) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isAttackedByRook(
            Board board,
            Square square,
            Color attackingColor
    ) {

        int[][] directions = {
                {0, 1},
                {0, -1},
                {1, 0},
                {-1, 0}
        };

        for (int[] direction : directions) {

            int checkFile = square.file() + direction[0];
            int checkRank = square.rank() + direction[1];

            while (isOnBoard(checkFile, checkRank)) {

                Square checkSquare = new Square(checkFile, checkRank);
                Piece piece = board.getPiece(checkSquare);

                if (piece != null) {

                    if (piece.color() == attackingColor
                            && piece.type() == PieceType.ROOK) {
                        return true;
                    }

                    break;
                }

                checkFile += direction[0];
                checkRank += direction[1];
            }
        }

        return false;
    }

    private boolean isAttackedByBishop(
            Board board,
            Square square,
            Color attackingColor
    ) {

        int[][] directions = {
                {1, 1},
                {-1, 1},
                {1, -1},
                {-1, -1}
        };

        for (int[] direction : directions) {

            int checkFile = square.file() + direction[0];
            int checkRank = square.rank() + direction[1];

            while (isOnBoard(checkFile, checkRank)) {

                Square checkSquare = new Square(checkFile, checkRank);
                Piece piece = board.getPiece(checkSquare);

                if (piece != null) {

                    if (piece.color() == attackingColor
                            && piece.type() == PieceType.BISHOP) {
                        return true;
                    }

                    break;
                }

                checkFile += direction[0];
                checkRank += direction[1];
            }
        }

        return false;
    }

    private boolean isAttackedByQueen(
            Board board,
            Square square,
            Color attackingColor
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

        for (int[] direction : directions) {

            int checkFile = square.file() + direction[0];
            int checkRank = square.rank() + direction[1];

            while (isOnBoard(checkFile, checkRank)) {

                Square checkSquare = new Square(checkFile, checkRank);
                Piece piece = board.getPiece(checkSquare);

                if (piece != null) {

                    if (piece.color() == attackingColor
                            && piece.type() == PieceType.QUEEN) {
                        return true;
                    }

                    break;
                }

                checkFile += direction[0];
                checkRank += direction[1];
            }
        }

        return false;
    }

    private boolean isAttackedByKnight(
            Board board,
            Square square,
            Color attackingColor
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

            int checkFile = square.file() + offset[0];
            int checkRank = square.rank() + offset[1];

            if (!isOnBoard(checkFile, checkRank)) {
                continue;
            }

            Square checkSquare = new Square(checkFile, checkRank);
            Piece piece = board.getPiece(checkSquare);

            if (piece != null
                    && piece.color() == attackingColor
                    && piece.type() == PieceType.KNIGHT) {
                return true;
            }
        }

        return false;
    }

    private boolean isAttackedByPawn(
            Board board,
            Square square,
            Color attackingColor
    ) {

        int pawnDirection;

        if (attackingColor == Color.WHITE) {
            pawnDirection = 1;
        } else {
            pawnDirection = -1;
        }

        int pawnRank = square.rank() - pawnDirection;

        int[] fileOffsets = {-1, 1};

        for (int fileOffset : fileOffsets) {

            int pawnFile = square.file() + fileOffset;

            if (!isOnBoard(pawnFile, pawnRank)) {
                continue;
            }

            Square pawnSquare = new Square(pawnFile, pawnRank);
            Piece piece = board.getPiece(pawnSquare);

            if (piece != null
                    && piece.color() == attackingColor
                    && piece.type() == PieceType.PAWN) {
                return true;
            }
        }

        return false;
    }

    private boolean isOnBoard(int file, int rank) {
        return file >= 0 && file < 8
                && rank >= 0 && rank < 8;
    }
}
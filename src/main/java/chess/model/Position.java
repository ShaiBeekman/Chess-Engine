package main.java.chess.model;

import java.util.HashMap;
import java.util.Map;

public class Position {

    private final Board board;
    private final Color sideToMove;

    private final boolean whiteKingSideCastle;
    private final boolean whiteQueenSideCastle;
    private final boolean blackKingSideCastle;
    private final boolean blackQueenSideCastle;

    private final Square enPassantTarget;

    private final int halfmoveClock;
    private final int fullmoveNumber;

    private final Map<PositionKey, Integer> repetitionCounts;

    public Position(
            Board board,
            Color sideToMove,
            boolean whiteKingSideCastle,
            boolean whiteQueenSideCastle,
            boolean blackKingSideCastle,
            boolean blackQueenSideCastle,
            Square enPassantTarget,
            int halfmoveClock,
            Map<PositionKey, Integer> repetitionCounts
    ) {
        this(
                board,
                sideToMove,
                whiteKingSideCastle,
                whiteQueenSideCastle,
                blackKingSideCastle,
                blackQueenSideCastle,
                enPassantTarget,
                halfmoveClock,
                1,
                repetitionCounts
        );
    }


    public Position(
            Board board,
            Color sideToMove,
            boolean whiteKingSideCastle,
            boolean whiteQueenSideCastle,
            boolean blackKingSideCastle,
            boolean blackQueenSideCastle,
            Square enPassantTarget,
            int halfmoveClock,
            int fullmoveNumber,
            Map<PositionKey, Integer> repetitionCounts
    ) {
        if (fullmoveNumber < 1) {
            throw new IllegalArgumentException(
                    "Fullmove number must be at least 1."
            );
        }

        this.board = board;
        this.sideToMove = sideToMove;

        this.whiteKingSideCastle = whiteKingSideCastle;
        this.whiteQueenSideCastle = whiteQueenSideCastle;
        this.blackKingSideCastle = blackKingSideCastle;
        this.blackQueenSideCastle = blackQueenSideCastle;

        this.enPassantTarget = enPassantTarget;

        this.halfmoveClock = halfmoveClock;
        this.fullmoveNumber = fullmoveNumber;

        this.repetitionCounts =
                new HashMap<>(repetitionCounts);
    }

    public Position(
            Board board,
            Color sideToMove
    ) {
        this.board = board;
        this.sideToMove = sideToMove;

        this.whiteKingSideCastle = true;
        this.whiteQueenSideCastle = true;
        this.blackKingSideCastle = true;
        this.blackQueenSideCastle = true;

        this.enPassantTarget = null;

        this.halfmoveClock = 0;
        this.fullmoveNumber = 1;

        this.repetitionCounts =
                new HashMap<>();

        PositionKey startingKey =
                createPositionKey();

        this.repetitionCounts.put(
                startingKey,
                1
        );
    }

    public Board getBoard() {
        return board;
    }

    public Color getSideToMove() {
        return sideToMove;
    }

    public boolean canWhiteCastleKingSide() {
        return whiteKingSideCastle;
    }

    public boolean canWhiteCastleQueenSide() {
        return whiteQueenSideCastle;
    }

    public boolean canBlackCastleKingSide() {
        return blackKingSideCastle;
    }

    public boolean canBlackCastleQueenSide() {
        return blackQueenSideCastle;
    }

    public Square getEnPassantTarget() {
        return enPassantTarget;
    }

    public int getHalfmoveClock() {
        return halfmoveClock;
    }


    public int getFullmoveNumber() {
        return fullmoveNumber;
    }

    public int getCurrentRepetitionCount() {

        PositionKey currentKey =
                createPositionKey();

        return repetitionCounts.getOrDefault(
                currentKey,
                0
        );
    }

    public PositionKey createPositionKey() {

        StringBuilder key =
                new StringBuilder();

        for (int rank = 0;
             rank < 8;
             rank++) {

            for (int file = 0;
                 file < 8;
                 file++) {

                Piece piece =
                        board.getPiece(
                                new Square(
                                        file,
                                        rank
                                )
                        );

                if (piece == null) {

                    key.append(".");

                    continue;
                }

                if (piece.color() == Color.WHITE) {

                    key.append("W");

                } else {

                    key.append("B");
                }

                switch (piece.type()) {

                    case KING ->
                            key.append("K");

                    case QUEEN ->
                            key.append("Q");

                    case ROOK ->
                            key.append("R");

                    case BISHOP ->
                            key.append("B");

                    case KNIGHT ->
                            key.append("N");

                    case PAWN ->
                            key.append("P");
                }
            }
        }

        key.append("|");
        key.append(sideToMove);

        key.append("|");

        key.append(
                whiteKingSideCastle
        );

        key.append(
                whiteQueenSideCastle
        );

        key.append(
                blackKingSideCastle
        );

        key.append(
                blackQueenSideCastle
        );

        key.append("|");

        if (enPassantTarget == null) {

            key.append("-");

        } else {

            key.append(
                    enPassantTarget.toAlgebraic()
            );
        }

        return new PositionKey(
                key.toString()
        );
    }

    public Position makeMove(
            Move move
    ) {

        Board newBoard =
                new Board(board);

        Piece movingPiece =
                newBoard.getPiece(
                        move.from()
                );

        if (movingPiece == null) {

            throw new IllegalArgumentException(
                    "No piece on "
                            + move.from()
                            .toAlgebraic()
            );
        }

        Piece capturedPiece =
                newBoard.getPiece(
                        move.to()
                );

        boolean isEnPassant =
                movingPiece.type()
                        == PieceType.PAWN

                        && enPassantTarget != null

                        && move.to().equals(
                        enPassantTarget
                )

                        && newBoard.isEmpty(
                        move.to()
                );

        newBoard.removePiece(
                move.from()
        );

        if (move.isPromotion()) {

            Piece promotedPiece =
                    new Piece(
                            move.promotion(),
                            movingPiece.color()
                    );

            newBoard.setPiece(
                    move.to(),
                    promotedPiece
            );

        } else {

            newBoard.setPiece(
                    move.to(),
                    movingPiece
            );
        }

        if (isEnPassant) {

            Square capturedPawnSquare =
                    new Square(
                            move.to().file(),
                            move.from().rank()
                    );

            newBoard.removePiece(
                    capturedPawnSquare
            );
        }

        /*
         * Castling
         */
        if (movingPiece.type()
                == PieceType.KING

                && Math.abs(
                move.to().file()
                        - move.from().file()
        ) == 2) {

            int rank =
                    move.from().rank();

            /*
             * Kingside
             */
            if (move.to().file() == 6) {

                Square rookFrom =
                        new Square(
                                7,
                                rank
                        );

                Square rookTo =
                        new Square(
                                5,
                                rank
                        );

                Piece rook =
                        newBoard.getPiece(
                                rookFrom
                        );

                newBoard.removePiece(
                        rookFrom
                );

                newBoard.setPiece(
                        rookTo,
                        rook
                );
            }

            /*
             * Queenside
             */
            else if (move.to().file() == 2) {

                Square rookFrom =
                        new Square(
                                0,
                                rank
                        );

                Square rookTo =
                        new Square(
                                3,
                                rank
                        );

                Piece rook =
                        newBoard.getPiece(
                                rookFrom
                        );

                newBoard.removePiece(
                        rookFrom
                );

                newBoard.setPiece(
                        rookTo,
                        rook
                );
            }
        }

        boolean newWhiteKingSideCastle =
                whiteKingSideCastle;

        boolean newWhiteQueenSideCastle =
                whiteQueenSideCastle;

        boolean newBlackKingSideCastle =
                blackKingSideCastle;

        boolean newBlackQueenSideCastle =
                blackQueenSideCastle;

        /*
         * King move destroys both castling rights.
         */
        if (movingPiece.type()
                == PieceType.KING) {

            if (movingPiece.color()
                    == Color.WHITE) {

                newWhiteKingSideCastle =
                        false;

                newWhiteQueenSideCastle =
                        false;

            } else {

                newBlackKingSideCastle =
                        false;

                newBlackQueenSideCastle =
                        false;
            }
        }

        /*
         * Moving original rook
         */
        if (movingPiece.type()
                == PieceType.ROOK) {

            if (move.from().equals(
                    Square.fromAlgebraic(
                            "h1"
                    )
            )) {

                newWhiteKingSideCastle =
                        false;
            }

            if (move.from().equals(
                    Square.fromAlgebraic(
                            "a1"
                    )
            )) {

                newWhiteQueenSideCastle =
                        false;
            }

            if (move.from().equals(
                    Square.fromAlgebraic(
                            "h8"
                    )
            )) {

                newBlackKingSideCastle =
                        false;
            }

            if (move.from().equals(
                    Square.fromAlgebraic(
                            "a8"
                    )
            )) {

                newBlackQueenSideCastle =
                        false;
            }
        }

        /*
         * Capturing an original rook
         */
        if (capturedPiece != null
                && capturedPiece.type()
                == PieceType.ROOK) {

            if (move.to().equals(
                    Square.fromAlgebraic(
                            "h1"
                    )
            )) {

                newWhiteKingSideCastle =
                        false;
            }

            if (move.to().equals(
                    Square.fromAlgebraic(
                            "a1"
                    )
            )) {

                newWhiteQueenSideCastle =
                        false;
            }

            if (move.to().equals(
                    Square.fromAlgebraic(
                            "h8"
                    )
            )) {

                newBlackKingSideCastle =
                        false;
            }

            if (move.to().equals(
                    Square.fromAlgebraic(
                            "a8"
                    )
            )) {

                newBlackQueenSideCastle =
                        false;
            }
        }

        /*
         * En passant target
         */
        Square newEnPassantTarget =
                null;

        if (movingPiece.type()
                == PieceType.PAWN

                && Math.abs(
                move.to().rank()
                        - move.from().rank()
        ) == 2) {

            int passedRank =
                    (
                            move.from().rank()
                                    + move.to().rank()
                    ) / 2;

            newEnPassantTarget =
                    new Square(
                            move.from().file(),
                            passedRank
                    );
        }

        /*
         * Halfmove clock
         */
        int newHalfmoveClock;

        if (movingPiece.type()
                == PieceType.PAWN

                || capturedPiece != null

                || isEnPassant) {

            newHalfmoveClock = 0;

        } else {

            newHalfmoveClock =
                    halfmoveClock + 1;
        }

        /*
         * FEN fullmove number increments immediately after
         * Black makes a move.
         */
        int newFullmoveNumber =
                fullmoveNumber
                        + (
                        sideToMove == Color.BLACK
                                ? 1
                                : 0
                );


        /*
         * Copy this line's repetition history.
         */
        Map<PositionKey, Integer>
                newRepetitionCounts =
                new HashMap<>(
                        repetitionCounts
                );

        /*
         * First create the resulting position.
         *
         * We temporarily use the copied map before
         * incrementing the resulting position's key.
         */
        Position newPosition =
                new Position(
                        newBoard,
                        sideToMove.opposite(),
                        newWhiteKingSideCastle,
                        newWhiteQueenSideCastle,
                        newBlackKingSideCastle,
                        newBlackQueenSideCastle,
                        newEnPassantTarget,
                        newHalfmoveClock,
                        newFullmoveNumber,
                        newRepetitionCounts
                );

        PositionKey newKey =
                newPosition.createPositionKey();

        newRepetitionCounts.put(
                newKey,
                newRepetitionCounts.getOrDefault(
                        newKey,
                        0
                ) + 1
        );

        /*
         * Return the same resulting position again,
         * now with the updated repetition history.
         */
        return new Position(
                newBoard,
                sideToMove.opposite(),
                newWhiteKingSideCastle,
                newWhiteQueenSideCastle,
                newBlackKingSideCastle,
                newBlackQueenSideCastle,
                newEnPassantTarget,
                newHalfmoveClock,
                newFullmoveNumber,
                newRepetitionCounts
        );
    }
}

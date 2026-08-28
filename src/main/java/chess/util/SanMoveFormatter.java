package main.java.chess.util;

import main.java.chess.model.Board;
import main.java.chess.model.GameState;
import main.java.chess.model.Move;
import main.java.chess.model.Piece;
import main.java.chess.model.PieceType;
import main.java.chess.model.Position;
import main.java.chess.model.Square;

import main.java.chess.rules.GameStateEvaluator;
import main.java.chess.rules.MoveGenerator;

import java.util.List;

public class SanMoveFormatter {

    private final GameStateEvaluator gameStateEvaluator;
    private final MoveGenerator moveGenerator;


    public SanMoveFormatter() {

        this.gameStateEvaluator =
                new GameStateEvaluator();

        this.moveGenerator =
                new MoveGenerator();
    }


    // =========================
    // Main SAN formatter
    // =========================

    public String format(
            Position positionBeforeMove,
            Move move
    ) {

        Board board =
                positionBeforeMove.getBoard();

        Piece movingPiece =
                board.getPiece(
                        move.from()
                );

        if (movingPiece == null) {

            throw new IllegalStateException(
                    "No piece found on move origin: "
                            + move.from()
            );
        }


        Piece capturedPiece =
                board.getPiece(
                        move.to()
                );

        boolean capture =
                capturedPiece != null;


        // =========================
        // Castling
        // =========================

        if (movingPiece.type()
                == PieceType.KING) {

            int fileDifference =
                    move.to().file()
                            - move.from().file();

            /*
             * King moves two files
             * toward the rook when castling.
             */
            if (fileDifference == 2) {

                return addCheckSuffix(
                        positionBeforeMove,
                        move,
                        "O-O"
                );
            }

            if (fileDifference == -2) {

                return addCheckSuffix(
                        positionBeforeMove,
                        move,
                        "O-O-O"
                );
            }
        }


        StringBuilder san =
                new StringBuilder();


        // =========================
        // Piece letter
        // =========================

        /*
         * Pawns have no leading
         * piece letter in SAN.
         */
        if (movingPiece.type()
                != PieceType.PAWN) {

            san.append(
                    pieceLetter(
                            movingPiece.type()
                    )
            );
        }


        // =========================
        // Piece disambiguation
        // =========================

        /*
         * Example:
         *
         * Nbd2
         * Nfd2
         *
         * when two knights can legally
         * move to the same square.
         */
        if (movingPiece.type()
                != PieceType.PAWN
                && movingPiece.type()
                != PieceType.KING) {

            appendDisambiguation(
                    san,
                    positionBeforeMove,
                    move,
                    movingPiece
            );
        }


        // =========================
        // Pawn capture origin file
        // =========================

        /*
         * Example:
         *
         * exd5
         *
         * rather than just xd5.
         */
        if (movingPiece.type()
                == PieceType.PAWN
                && capture) {

            san.append(
                    fileCharacter(
                            move.from().file()
                    )
            );
        }


        // =========================
        // Capture marker
        // =========================

        if (capture) {

            san.append("x");
        }


        // =========================
        // Destination
        // =========================

        san.append(
                squareToString(
                        move.to()
                )
        );


        // =========================
        // Promotion
        // =========================

        if (move.promotion() != null) {

            san.append("=");

            san.append(
                    pieceLetter(
                            move.promotion()
                    )
            );
        }


        // =========================
        // Check / checkmate
        // =========================

        return addCheckSuffix(
                positionBeforeMove,
                move,
                san.toString()
        );
    }


    // =========================
    // SAN disambiguation
    // =========================

    private void appendDisambiguation(
            StringBuilder san,
            Position position,
            Move move,
            Piece movingPiece
    ) {

        List<Move> legalMoves =
                moveGenerator
                        .generateLegalMoves(
                                position
                        );

        boolean competingPieceExists =
                false;

        boolean sameFileExists =
                false;

        boolean sameRankExists =
                false;


        for (Move candidate :
                legalMoves) {

            /*
             * Ignore the move we're
             * currently formatting.
             */
            if (candidate.equals(
                    move
            )) {

                continue;
            }


            /*
             * Only competing moves to
             * the same destination matter.
             */
            if (!candidate.to().equals(
                    move.to()
            )) {

                continue;
            }


            Piece candidatePiece =
                    position
                            .getBoard()
                            .getPiece(
                                    candidate.from()
                            );

            if (candidatePiece == null) {
                continue;
            }


            /*
             * Must be the same piece type.
             */
            if (candidatePiece.type()
                    != movingPiece.type()) {

                continue;
            }


            /*
             * Must belong to the same side.
             */
            if (candidatePiece.color()
                    != movingPiece.color()) {

                continue;
            }


            competingPieceExists =
                    true;


            /*
             * Another candidate originates
             * from the same file.
             */
            if (candidate.from().file()
                    == move.from().file()) {

                sameFileExists =
                        true;
            }


            /*
             * Another candidate originates
             * from the same rank.
             */
            if (candidate.from().rank()
                    == move.from().rank()) {

                sameRankExists =
                        true;
            }
        }


        /*
         * No ambiguity.
         */
        if (!competingPieceExists) {
            return;
        }


        /*
         * Prefer file disambiguation
         * whenever file alone is enough.
         *
         * Example:
         *
         * Nbd2
         */
        if (!sameFileExists) {

            san.append(
                    fileCharacter(
                            move.from().file()
                    )
            );

            return;
        }


        /*
         * If file isn't sufficient,
         * try rank.
         *
         * Example:
         *
         * R1e2
         */
        if (!sameRankExists) {

            san.append(
                    move.from().rank() + 1
            );

            return;
        }


        /*
         * Extremely rare case:
         * both file and rank are required.
         */
        san.append(
                fileCharacter(
                        move.from().file()
                )
        );

        san.append(
                move.from().rank() + 1
        );
    }


    // =========================
    // Check suffix
    // =========================

    private String addCheckSuffix(
            Position positionBeforeMove,
            Move move,
            String san
    ) {

        Position positionAfterMove =
                positionBeforeMove.makeMove(
                        move
                );

        GameState state =
                gameStateEvaluator.evaluate(
                        positionAfterMove
                );


        if (state
                == GameState.CHECKMATE) {

            return san + "#";
        }


        if (state
                == GameState.CHECK) {

            return san + "+";
        }


        return san;
    }


    // =========================
    // Piece letters
    // =========================

    private String pieceLetter(
            PieceType pieceType
    ) {

        return switch (pieceType) {

            case KING ->
                    "K";

            case QUEEN ->
                    "Q";

            case ROOK ->
                    "R";

            case BISHOP ->
                    "B";

            case KNIGHT ->
                    "N";

            case PAWN ->
                    "";
        };
    }


    // =========================
    // Square formatting
    // =========================

    private char fileCharacter(
            int file
    ) {

        return (char) (
                'a' + file
        );
    }


    private String squareToString(
            Square square
    ) {

        char file =
                fileCharacter(
                        square.file()
                );

        int rank =
                square.rank() + 1;

        return ""
                + file
                + rank;
    }
}
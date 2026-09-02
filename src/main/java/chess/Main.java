package main.java.chess;

import main.java.chess.gui.ChessWindow;

import main.java.chess.model.Board;
import main.java.chess.model.Color;
import main.java.chess.model.Piece;
import main.java.chess.model.PieceType;
import main.java.chess.model.Position;
import main.java.chess.model.Square;

import javax.swing.SwingUtilities;

public class Main {

    public static void main(
            String[] args
    ) {

        Position startingPosition =
                createStartingPosition();


        /*
         * Production startup is intentionally lightweight.
         *
         * The full v1.0 correctness/regression suite lives behind the
         * dedicated release verification entry points, including:
         *
         *     main.java.chess.release.V1ReleaseRegressionMain
         *
         * Normal application startup should only construct the initial
         * position and launch the Swing GUI.
         */
        SwingUtilities.invokeLater(
                () -> {

                    ChessWindow window =
                            new ChessWindow(
                                    startingPosition
                            );

                    window.setVisible(
                            true
                    );
                }
        );
    }


    // =========================================================
    // Standard chess starting position
    // =========================================================

    private static Position createStartingPosition() {

        Board board =
                new Board();


        // =========================
        // White back rank
        // =========================

        board.setPiece(
                new Square(
                        0,
                        0
                ),
                new Piece(
                        PieceType.ROOK,
                        Color.WHITE
                )
        );


        board.setPiece(
                new Square(
                        1,
                        0
                ),
                new Piece(
                        PieceType.KNIGHT,
                        Color.WHITE
                )
        );


        board.setPiece(
                new Square(
                        2,
                        0
                ),
                new Piece(
                        PieceType.BISHOP,
                        Color.WHITE
                )
        );


        board.setPiece(
                new Square(
                        3,
                        0
                ),
                new Piece(
                        PieceType.QUEEN,
                        Color.WHITE
                )
        );


        board.setPiece(
                new Square(
                        4,
                        0
                ),
                new Piece(
                        PieceType.KING,
                        Color.WHITE
                )
        );


        board.setPiece(
                new Square(
                        5,
                        0
                ),
                new Piece(
                        PieceType.BISHOP,
                        Color.WHITE
                )
        );


        board.setPiece(
                new Square(
                        6,
                        0
                ),
                new Piece(
                        PieceType.KNIGHT,
                        Color.WHITE
                )
        );


        board.setPiece(
                new Square(
                        7,
                        0
                ),
                new Piece(
                        PieceType.ROOK,
                        Color.WHITE
                )
        );


        // =========================
        // White pawns
        // =========================

        for (int file = 0;
             file < 8;
             file++) {

            board.setPiece(
                    new Square(
                            file,
                            1
                    ),
                    new Piece(
                            PieceType.PAWN,
                            Color.WHITE
                    )
            );
        }


        // =========================
        // Black back rank
        // =========================

        board.setPiece(
                new Square(
                        0,
                        7
                ),
                new Piece(
                        PieceType.ROOK,
                        Color.BLACK
                )
        );


        board.setPiece(
                new Square(
                        1,
                        7
                ),
                new Piece(
                        PieceType.KNIGHT,
                        Color.BLACK
                )
        );


        board.setPiece(
                new Square(
                        2,
                        7
                ),
                new Piece(
                        PieceType.BISHOP,
                        Color.BLACK
                )
        );


        board.setPiece(
                new Square(
                        3,
                        7
                ),
                new Piece(
                        PieceType.QUEEN,
                        Color.BLACK
                )
        );


        board.setPiece(
                new Square(
                        4,
                        7
                ),
                new Piece(
                        PieceType.KING,
                        Color.BLACK
                )
        );


        board.setPiece(
                new Square(
                        5,
                        7
                ),
                new Piece(
                        PieceType.BISHOP,
                        Color.BLACK
                )
        );


        board.setPiece(
                new Square(
                        6,
                        7
                ),
                new Piece(
                        PieceType.KNIGHT,
                        Color.BLACK
                )
        );


        board.setPiece(
                new Square(
                        7,
                        7
                ),
                new Piece(
                        PieceType.ROOK,
                        Color.BLACK
                )
        );


        // =========================
        // Black pawns
        // =========================

        for (int file = 0;
             file < 8;
             file++) {

            board.setPiece(
                    new Square(
                            file,
                            6
                    ),
                    new Piece(
                            PieceType.PAWN,
                            Color.BLACK
                    )
            );
        }


        // =========================
        // White to move
        // =========================

        return new Position(
                board,
                Color.WHITE
        );
    }
}

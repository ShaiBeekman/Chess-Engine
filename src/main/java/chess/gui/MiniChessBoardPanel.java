package main.java.chess.gui;

import main.java.chess.model.Board;
import main.java.chess.model.Piece;
import main.java.chess.model.PieceType;
import main.java.chess.model.Position;
import main.java.chess.model.Square;

import javax.swing.*;
import java.awt.*;

public class MiniChessBoardPanel extends JPanel {

    private static final int BOARD_SIZE =
            136;

    private static final Color LIGHT_SQUARE =
            new Color(
                    238,
                    238,
                    210
            );

    private static final Color DARK_SQUARE =
            new Color(
                    118,
                    150,
                    86
            );

    private static final Color BORDER_COLOR =
            new Color(
                    55,
                    60,
                    68
            );


    private Position position;


    public MiniChessBoardPanel(
            Position position
    ) {

        this.position =
                position;


        setPreferredSize(
                new Dimension(
                        BOARD_SIZE,
                        BOARD_SIZE
                )
        );


        setMinimumSize(
                new Dimension(
                        BOARD_SIZE,
                        BOARD_SIZE
                )
        );


        setMaximumSize(
                new Dimension(
                        BOARD_SIZE,
                        BOARD_SIZE
                )
        );


        setOpaque(
                false
        );


        setToolTipText(
                "Preview position"
        );
    }


    public void setPosition(
            Position position
    ) {

        this.position =
                position;

        repaint();
    }


    public Position getPosition() {

        return position;
    }


    @Override
    protected void paintComponent(
            Graphics graphics
    ) {

        super.paintComponent(
                graphics
        );


        if (position == null) {
            return;
        }


        Graphics2D g2 =
                (Graphics2D) graphics.create();


        try {

            g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );


            int size =
                    Math.min(
                            getWidth(),
                            getHeight()
                    );


            int squareSize =
                    size / 8;


            int boardPixels =
                    squareSize * 8;


            int offsetX =
                    (getWidth() - boardPixels) / 2;


            int offsetY =
                    (getHeight() - boardPixels) / 2;


            Board board =
                    position.getBoard();


            // =================================================
            // Squares
            // =================================================

            for (int row = 0;
                 row < 8;
                 row++) {

                for (int column = 0;
                     column < 8;
                     column++) {

                    boolean light =
                            (row + column) % 2 == 0;


                    g2.setColor(
                            light
                                    ? LIGHT_SQUARE
                                    : DARK_SQUARE
                    );


                    int x =
                            offsetX
                                    + column
                                    * squareSize;


                    int y =
                            offsetY
                                    + row
                                    * squareSize;


                    g2.fillRect(
                            x,
                            y,
                            squareSize,
                            squareSize
                    );


                    /*
                     * Screen row 0 = rank 8.
                     */
                    int rank =
                            7 - row;


                    Square square =
                            new Square(
                                    column,
                                    rank
                            );


                    Piece piece =
                            board.getPiece(
                                    square
                            );


                    if (piece != null) {

                        drawPiece(
                                g2,
                                piece,
                                x,
                                y,
                                squareSize
                        );
                    }
                }
            }


            g2.setColor(
                    BORDER_COLOR
            );


            g2.drawRect(
                    offsetX,
                    offsetY,
                    boardPixels - 1,
                    boardPixels - 1
            );


        } finally {

            g2.dispose();
        }
    }


    private void drawPiece(
            Graphics2D g2,
            Piece piece,
            int x,
            int y,
            int squareSize
    ) {

        String symbol =
                getPieceSymbol(
                        piece
                );


        int fontSize =
                Math.max(
                        12,
                        (int) (
                                squareSize
                                        * 0.92
                        )
                );


        Font font =
                new Font(
                        Font.SERIF,
                        Font.PLAIN,
                        fontSize
                );


        g2.setFont(
                font
        );


        FontMetrics metrics =
                g2.getFontMetrics();


        int textWidth =
                metrics.stringWidth(
                        symbol
                );


        int textHeight =
                metrics.getAscent()
                        - metrics.getDescent();


        int drawX =
                x
                        + (
                        squareSize
                                - textWidth
                ) / 2;


        int drawY =
                y
                        + (
                        squareSize
                                + textHeight
                ) / 2;


        /*
         * Unicode chess glyphs are already
         * different symbols for White/Black,
         * so use one foreground color.
         */
        g2.setColor(
                Color.BLACK
        );


        g2.drawString(
                symbol,
                drawX,
                drawY
        );
    }


    private String getPieceSymbol(
            Piece piece
    ) {

        boolean white =
                piece.color()
                        == main.java.chess.model.Color.WHITE;


        PieceType type =
                piece.type();


        return switch (type) {

            case KING ->
                    white
                            ? "\u2654"
                            : "\u265A";

            case QUEEN ->
                    white
                            ? "\u2655"
                            : "\u265B";

            case ROOK ->
                    white
                            ? "\u2656"
                            : "\u265C";

            case BISHOP ->
                    white
                            ? "\u2657"
                            : "\u265D";

            case KNIGHT ->
                    white
                            ? "\u2658"
                            : "\u265E";

            case PAWN ->
                    white
                            ? "\u2659"
                            : "\u265F";
        };
    }
}
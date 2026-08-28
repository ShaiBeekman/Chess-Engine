package main.java.chess.gui;

import main.java.chess.search.SearchOutcome;

import javax.swing.*;
import java.awt.*;

public class EvaluationBar extends JPanel {

    private static final int BAR_WIDTH = 42;

    /*
     * Ordinary evaluations are visually compressed
     * into this range.
     *
     * +/- 10 pawns is effectively the visual limit.
     */
    private static final double MAX_DISPLAY_EVALUATION =
            10.0;


    private int evaluation;

    private SearchOutcome outcome;

    private int mateDistance;


    public EvaluationBar() {

        evaluation =
                0;

        outcome =
                SearchOutcome.UNKNOWN;

        mateDistance =
                -1;


        setPreferredSize(
                new Dimension(
                        BAR_WIDTH,
                        640
                )
        );


        setMinimumSize(
                new Dimension(
                        BAR_WIDTH,
                        200
                )
        );


        setOpaque(
                true
        );
    }


    // =========================================================
    // Set displayed analysis
    // =========================================================

    public void setAnalysis(
            int evaluation,
            SearchOutcome outcome,
            int mateDistance
    ) {

        this.evaluation =
                evaluation;

        this.outcome =
                outcome == null
                        ? SearchOutcome.UNKNOWN
                        : outcome;

        this.mateDistance =
                mateDistance;


        repaint();
    }


    public void setEvaluation(
            int evaluation
    ) {

        setAnalysis(
                evaluation,
                SearchOutcome.UNKNOWN,
                -1
        );
    }


    // =========================================================
    // Drawing
    // =========================================================

    @Override
    protected void paintComponent(
            Graphics graphics
    ) {

        super.paintComponent(
                graphics
        );


        Graphics2D g2 =
                (Graphics2D) graphics.create();


        try {

            g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );


            int width =
                    getWidth();


            int height =
                    getHeight();


            if (width <= 0
                    || height <= 0) {

                return;
            }


            // =================================================
            // Draw state
            // =================================================

            if (outcome == SearchOutcome.DRAW) {

                drawDrawState(
                        g2,
                        width,
                        height
                );

            } else if (outcome == SearchOutcome.WHITE_WIN) {

                drawMateState(
                        g2,
                        width,
                        height,
                        true
                );

            } else if (outcome == SearchOutcome.BLACK_WIN) {

                drawMateState(
                        g2,
                        width,
                        height,
                        false
                );

            } else {

                drawEvaluationState(
                        g2,
                        width,
                        height
                );
            }


            // =================================================
            // Outer border
            // =================================================

            g2.setColor(
                    new java.awt.Color(
                            70,
                            74,
                            80
                    )
            );


            g2.drawRect(
                    0,
                    0,
                    width - 1,
                    height - 1
            );


        } finally {

            g2.dispose();
        }
    }


    // =========================================================
    // Ordinary evaluation
    // =========================================================

    private void drawEvaluationState(
            Graphics2D g2,
            int width,
            int height
    ) {

        double pawns =
                evaluation / 100.0;


        /*
         * Compress large evaluations so that
         * +2 does not already consume nearly
         * the entire bar.
         *
         * tanh gives us a smooth asymptotic
         * approach toward either side.
         */
        double normalized =
                Math.tanh(
                        pawns
                                / MAX_DISPLAY_EVALUATION
                                * 2.5
                );


        /*
         * normalized:
         *
         * -1 = completely Black
         *  0 = equal
         * +1 = completely White
         */
        double whiteFraction =
                0.5
                        + normalized
                        * 0.5;


        whiteFraction =
                Math.max(
                        0.0,
                        Math.min(
                                1.0,
                                whiteFraction
                        )
                );


        int whiteHeight =
                (int) Math.round(
                        height
                                * whiteFraction
                );


        int blackHeight =
                height
                        - whiteHeight;


        // Black occupies top.
        g2.setColor(
                new java.awt.Color(
                        35,
                        38,
                        42
                )
        );


        g2.fillRect(
                0,
                0,
                width,
                blackHeight
        );


        // White occupies bottom.
        g2.setColor(
                new java.awt.Color(
                        238,
                        238,
                        232
                )
        );


        g2.fillRect(
                0,
                blackHeight,
                width,
                whiteHeight
        );


        String text =
                formatEvaluation(
                        evaluation
                );


        /*
         * Put the evaluation on the side
         * currently favored.
         */
        if (evaluation >= 0) {

            drawBottomLabel(
                    g2,
                    text,
                    width,
                    height,
                    java.awt.Color.BLACK
            );

        } else {

            drawTopLabel(
                    g2,
                    text,
                    width,
                    java.awt.Color.WHITE
            );
        }
    }


    // =========================================================
    // Proven mate
    // =========================================================

    private void drawMateState(
            Graphics2D g2,
            int width,
            int height,
            boolean whiteWins
    ) {

        if (whiteWins) {

            g2.setColor(
                    new java.awt.Color(
                            238,
                            238,
                            232
                    )
            );

        } else {

            g2.setColor(
                    new java.awt.Color(
                            35,
                            38,
                            42
                    )
            );
        }


        g2.fillRect(
                0,
                0,
                width,
                height
        );


        String mateText =
                formatMate();


        if (whiteWins) {

            drawBottomLabel(
                    g2,
                    mateText,
                    width,
                    height,
                    java.awt.Color.BLACK
            );

        } else {

            drawTopLabel(
                    g2,
                    mateText,
                    width,
                    java.awt.Color.WHITE
            );
        }
    }


    // =========================================================
    // Draw
    // =========================================================

    private void drawDrawState(
            Graphics2D g2,
            int width,
            int height
    ) {

        int midpoint =
                height / 2;


        g2.setColor(
                new java.awt.Color(
                        35,
                        38,
                        42
                )
        );


        g2.fillRect(
                0,
                0,
                width,
                midpoint
        );


        g2.setColor(
                new java.awt.Color(
                        238,
                        238,
                        232
                )
        );


        g2.fillRect(
                0,
                midpoint,
                width,
                height - midpoint
        );


        /*
         * Small center marker.
         */
        g2.setColor(
                new java.awt.Color(
                        110,
                        115,
                        122
                )
        );


        g2.drawLine(
                0,
                midpoint,
                width,
                midpoint
        );


        drawCenteredLabel(
                g2,
                "DRAW",
                width,
                height
        );
    }


    // =========================================================
    // Labels
    // =========================================================

    private void drawTopLabel(
            Graphics2D g2,
            String text,
            int width,
            java.awt.Color color
    ) {

        g2.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.BOLD,
                        11
                )
        );


        g2.setColor(
                color
        );


        FontMetrics metrics =
                g2.getFontMetrics();


        int x =
                (
                        width
                                - metrics.stringWidth(
                                text
                        )
                ) / 2;


        g2.drawString(
                text,
                x,
                metrics.getAscent()
                        + 7
        );
    }


    private void drawBottomLabel(
            Graphics2D g2,
            String text,
            int width,
            int height,
            java.awt.Color color
    ) {

        g2.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.BOLD,
                        11
                )
        );


        g2.setColor(
                color
        );


        FontMetrics metrics =
                g2.getFontMetrics();


        int x =
                (
                        width
                                - metrics.stringWidth(
                                text
                        )
                ) / 2;


        int y =
                height
                        - 7;


        g2.drawString(
                text,
                x,
                y
        );
    }


    private void drawCenteredLabel(
            Graphics2D g2,
            String text,
            int width,
            int height
    ) {

        g2.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.BOLD,
                        9
                )
        );


        FontMetrics metrics =
                g2.getFontMetrics();


        int textWidth =
                metrics.stringWidth(
                        text
                );


        int textHeight =
                metrics.getHeight();


        int paddingX =
                3;


        int paddingY =
                2;


        int x =
                (
                        width
                                - textWidth
                ) / 2;


        int baseline =
                (
                        height
                                - textHeight
                ) / 2
                        + metrics.getAscent();


        g2.setColor(
                new java.awt.Color(
                        90,
                        94,
                        100
                )
        );


        g2.fillRoundRect(
                x - paddingX,
                baseline
                        - metrics.getAscent()
                        - paddingY,
                textWidth
                        + paddingX * 2,
                textHeight
                        + paddingY * 2,
                6,
                6
        );


        g2.setColor(
                java.awt.Color.WHITE
        );


        g2.drawString(
                text,
                x,
                baseline
        );
    }


    // =========================================================
    // Formatting
    // =========================================================

    private String formatEvaluation(
            int centipawns
    ) {

        double pawns =
                centipawns / 100.0;


        if (pawns > 0) {

            return String.format(
                    "+%.2f",
                    pawns
            );
        }


        return String.format(
                "%.2f",
                pawns
        );
    }


    private String formatMate() {

        if (mateDistance < 0) {

            return "M";
        }


        if (mateDistance == 0) {

            return "M0";
        }


        int moves =
                (
                        mateDistance
                                + 1
                ) / 2;


        return "M"
                + moves;
    }
}
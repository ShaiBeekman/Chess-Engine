package main.java.chess.gui;

import main.java.chess.model.Color;
import main.java.chess.model.Piece;
import main.java.chess.model.PieceType;
import main.java.chess.model.Square;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.JWindow;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.RenderingHints;
import java.awt.Window;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;


/**
 * Position-setup controls and draggable piece palette.
 *
 * M68C6E
 *
 * - preserves old compact setup-button appearance
 * - uses solid chess glyphs for both colors
 * - White pieces render solid white
 * - Black pieces render solid black
 * - light mode uses normal light button backgrounds
 * - White pieces receive a subtle dark outline in light mode
 * - palette dragging shows only the piece, never a square
 */
public class PiecePalettePanel extends JPanel {

    private final ChessBoardPanel boardPanel;

    private Runnable analyzeListener;
    private Runnable cancelListener;

    private java.awt.Color backgroundColor;
    private java.awt.Color foregroundColor;
    private java.awt.Color secondaryForegroundColor;
    private java.awt.Color borderColor;
    private java.awt.Color buttonColor;

    private boolean darkTheme;

    private JWindow dragGhost;
    private SolidPieceLabel dragGhostLabel;


    public PiecePalettePanel(
            ChessBoardPanel boardPanel
    ) {

        if (boardPanel == null) {

            throw new IllegalArgumentException(
                    "Board panel cannot be null."
            );
        }

        this.boardPanel =
                boardPanel;

        this.darkTheme =
                true;

        this.backgroundColor =
                new java.awt.Color(
                        19,
                        27,
                        35
                );

        this.foregroundColor =
                new java.awt.Color(
                        242,
                        244,
                        247
                );

        this.secondaryForegroundColor =
                new java.awt.Color(
                        150,
                        165,
                        180
                );

        this.borderColor =
                new java.awt.Color(
                        42,
                        53,
                        64
                );

        this.buttonColor =
                new java.awt.Color(
                        26,
                        35,
                        44
                );

        this.dragGhost =
                null;

        this.dragGhostLabel =
                null;


        setLayout(
                new BoxLayout(
                        this,
                        BoxLayout.Y_AXIS
                )
        );


        rebuildBorder();


        add(
                createTopRow()
        );


        add(
                Box.createVerticalStrut(
                        6
                )
        );


        add(
                createPieceRow()
        );


        applyTheme(
                true
        );


        /*
         * ChessWindow's board column is 672 logical pixels wide:
         *
         *     20px left inset + 640px board + 12px right inset.
         *
         * M73F keeps this palette in a transparent workspace-bottom host so
         * its width no longer pushes the analysis panel away. Matching that
         * board-column width also makes the footer visually terminate with the
         * board instead of extending awkwardly into the analysis area.
         */
        Dimension natural =
                getPreferredSize();

        setPreferredSize(
                new Dimension(
                        672,
                        natural.height
                )
        );

        setMaximumSize(
                new Dimension(
                        672,
                        natural.height
                )
        );
    }


    // =========================================================
    // Public listeners
    // =========================================================

    public void setAnalyzeListener(
            Runnable listener
    ) {

        this.analyzeListener =
                listener;
    }


    public void setCancelListener(
            Runnable listener
    ) {

        this.cancelListener =
                listener;
    }


    // =========================================================
    // Theme
    // =========================================================

    public void applyTheme(
            boolean dark
    ) {

        darkTheme =
                dark;


        backgroundColor =
                dark
                        ? new java.awt.Color(
                        19,
                        27,
                        35
                )
                        : new java.awt.Color(
                        250,
                        251,
                        253
                );


        foregroundColor =
                dark
                        ? new java.awt.Color(
                        242,
                        244,
                        247
                )
                        : new java.awt.Color(
                        31,
                        35,
                        41
                );


        secondaryForegroundColor =
                dark
                        ? new java.awt.Color(
                        146,
                        163,
                        180
                )
                        : new java.awt.Color(
                        100,
                        110,
                        122
                );


        borderColor =
                dark
                        ? new java.awt.Color(
                        42,
                        53,
                        64
                )
                        : new java.awt.Color(
                        210,
                        216,
                        224
                );


        buttonColor =
                dark
                        ? new java.awt.Color(
                        26,
                        35,
                        44
                )
                        : new java.awt.Color(
                        244,
                        246,
                        249
                );


        setBackground(
                backgroundColor
        );


        rebuildBorder();


        updateChildTheme(
                this
        );


        revalidate();
        repaint();
    }


    private void rebuildBorder() {

        setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(
                                1,
                                0,
                                0,
                                0,
                                borderColor
                        ),
                        BorderFactory.createEmptyBorder(
                                8,
                                10,
                                8,
                                10
                        )
                )
        );
    }


    // =========================================================
    // Top controls
    // =========================================================

    private JPanel createTopRow() {

        JPanel row =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.LEFT,
                                8,
                                0
                        )
                );


        row.setOpaque(
                false
        );


        JLabel label =
                new JLabel(
                        "SETUP POSITION"
                );


        label.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.BOLD,
                        11
                )
        );


        label.putClientProperty(
                "setupLabel",
                Boolean.TRUE
        );


        row.add(
                label
        );


        row.add(
                Box.createHorizontalStrut(
                        8
                )
        );


        JToggleButton white =
                createSideButton(
                        "White to move",
                        Color.WHITE
                );


        JToggleButton black =
                createSideButton(
                        "Black to move",
                        Color.BLACK
                );


        ButtonGroup group =
                new ButtonGroup();


        group.add(
                white
        );


        group.add(
                black
        );


        if (boardPanel.getSetupSideToMove()
                == Color.BLACK) {

            black.setSelected(
                    true
            );

        } else {

            white.setSelected(
                    true
            );
        }


        row.add(
                white
        );


        row.add(
                black
        );


        JButton clear =
                createActionButton(
                        "Clear"
                );


        clear.addActionListener(
                event ->
                        boardPanel.clearSetupBoard()
        );


        row.add(
                clear
        );


        JButton cancel =
                createActionButton(
                        "Cancel"
                );


        cancel.addActionListener(
                event -> {

                    hideDragGhost();

                    if (cancelListener != null) {

                        cancelListener.run();
                    }
                }
        );


        row.add(
                cancel
        );


        JButton analyze =
                createActionButton(
                        "Analyze Position"
                );


        analyze.addActionListener(
                event -> {

                    hideDragGhost();

                    if (analyzeListener != null) {

                        analyzeListener.run();
                    }
                }
        );


        row.add(
                analyze
        );


        return row;
    }


    // =========================================================
    // Piece row
    // =========================================================

    private JPanel createPieceRow() {

        JPanel row =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.LEFT,
                                2,
                                0
                        )
                );


        row.setOpaque(
                false
        );


        JLabel instruction =
                new JLabel(
                        "Drag a piece onto the board:"
                );


        instruction.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.PLAIN,
                        11
                )
        );


        instruction.putClientProperty(
                "setupLabel",
                Boolean.TRUE
        );


        row.add(
                instruction
        );


        for (Color color :
                new Color[]{
                        Color.WHITE,
                        Color.BLACK
                }) {


            JLabel colorLabel =
                    new JLabel(
                            color == Color.WHITE
                                    ? "WHITE"
                                    : "BLACK"
                    );


            colorLabel.setFont(
                    new Font(
                            Font.SANS_SERIF,
                            Font.BOLD,
                            10
                    )
            );


            colorLabel.putClientProperty(
                    "setupSecondaryLabel",
                    Boolean.TRUE
            );


            row.add(
                    Box.createHorizontalStrut(
                            5
                    )
            );


            row.add(
                    colorLabel
            );


            row.add(
                    Box.createHorizontalStrut(
                            3
                    )
            );


            for (PieceType type :
                    new PieceType[]{
                            PieceType.KING,
                            PieceType.QUEEN,
                            PieceType.ROOK,
                            PieceType.BISHOP,
                            PieceType.KNIGHT,
                            PieceType.PAWN
                    }) {


                Piece piece =
                        new Piece(
                                type,
                                color
                        );


                row.add(
                        createPieceDragButton(
                                piece
                        )
                );
            }


            row.add(
                    Box.createHorizontalStrut(
                            8
                    )
            );
        }


        return row;
    }


    // =========================================================
    // Ordinary setup controls
    // =========================================================

    private JToggleButton createSideButton(
            String text,
            Color side
    ) {

        JToggleButton button =
                new JToggleButton(
                        text
                );


        /*
         * Keep White/Black as a real ButtonGroup selection so setup-side
         * state remains correct, but do not let the platform Look & Feel
         * paint the selected toggle with its own bright highlight color.
         *
         * The selected fill always follows this palette's normal buttonColor,
         * so White to move / Black to move keep the same compact appearance
         * as Clear / Cancel / Analyze Position in both themes.
         */
        button.setUI(
                new javax.swing.plaf.basic.BasicToggleButtonUI() {

                    @Override
                    protected void paintButtonPressed(
                            java.awt.Graphics graphics,
                            javax.swing.AbstractButton abstractButton
                    ) {

                        graphics.setColor(
                                abstractButton.getBackground()
                        );

                        graphics.fillRect(
                                0,
                                0,
                                abstractButton.getWidth(),
                                abstractButton.getHeight()
                        );
                    }
                }
        );


        styleControl(
                button
        );


        button.addActionListener(
                event ->
                        boardPanel.setSetupSideToMove(
                                side
                        )
        );


        return button;
    }


    private JButton createActionButton(
            String text
    ) {

        JButton button =
                new JButton(
                        text
                );


        styleControl(
                button
        );


        return button;
    }


    // =========================================================
    // Piece buttons
    // =========================================================

    private JButton createPieceDragButton(
            Piece piece
    ) {

        SolidPieceButton button =
                new SolidPieceButton(
                        getSolidPieceSymbol(
                                piece.type()
                        ),
                        piece.color()
                );


        /*
         * M73G:
         *
         * Keep the original one-line palette layout, but make only the
         * draggable piece buttons slightly more compact so the complete WHITE
         * + BLACK piece set fits beneath the board instead of hanging into the
         * analysis column.
         */
        button.setFont(
                new Font(
                        Font.SERIF,
                        Font.PLAIN,
                        22
                )
        );


        Dimension size =
                new Dimension(
                        29,
                        29
                );


        button.setPreferredSize(
                size
        );


        button.setMinimumSize(
                size
        );


        button.setMaximumSize(
                size
        );


        button.setFocusable(
                false
        );


        button.setFocusPainted(
                false
        );


        button.setHorizontalAlignment(
                SwingConstants.CENTER
        );


        button.setVerticalAlignment(
                SwingConstants.CENTER
        );


        button.setCursor(
                Cursor.getPredefinedCursor(
                        Cursor.HAND_CURSOR
                )
        );


        button.setToolTipText(
                (piece.color() == Color.WHITE
                        ? "White "
                        : "Black ")
                        + piece.type()
                        .name()
                        .toLowerCase()
        );


        button.putClientProperty(
                "setupControl",
                Boolean.TRUE
        );


        button.putClientProperty(
                "palettePiece",
                Boolean.TRUE
        );


        button.putClientProperty(
                "pieceColor",
                piece.color()
        );


        MouseAdapter drag =
                new MouseAdapter() {

                    private boolean pressed;


                    @Override
                    public void mousePressed(
                            MouseEvent event
                    ) {

                        if (!SwingUtilities.isLeftMouseButton(
                                event
                        )) {

                            return;
                        }


                        pressed =
                                true;


                        showDragGhost(
                                piece
                        );


                        updateDragGhostLocation();
                    }


                    @Override
                    public void mouseDragged(
                            MouseEvent event
                    ) {

                        if (!pressed) {
                            return;
                        }


                        updateDragGhostLocation();
                    }


                    @Override
                    public void mouseReleased(
                            MouseEvent event
                    ) {

                        if (!pressed) {
                            return;
                        }


                        pressed =
                                false;


                        Point boardPoint =
                                SwingUtilities.convertPoint(
                                        button,
                                        event.getPoint(),
                                        boardPanel
                                );


                        Square square =
                                boardPanel.getSquareAtPoint(
                                        boardPoint
                                );


                        if (square != null) {

                            boardPanel.placeSetupPiece(
                                    piece,
                                    square
                            );
                        }


                        hideDragGhost();
                    }
                };


        button.addMouseListener(
                drag
        );


        button.addMouseMotionListener(
                drag
        );


        return button;
    }


    // =========================================================
    // Solid glyphs
    // =========================================================

    private String getSolidPieceSymbol(
            PieceType type
    ) {

        return switch (type) {

            case KING ->
                    "\u265A";

            case QUEEN ->
                    "\u265B";

            case ROOK ->
                    "\u265C";

            case BISHOP ->
                    "\u265D";

            case KNIGHT ->
                    "\u265E";

            case PAWN ->
                    "\u265F";
        };
    }


    // =========================================================
    // Drag ghost
    // =========================================================

    private void showDragGhost(
            Piece piece
    ) {

        hideDragGhost();


        Window owner =
                SwingUtilities.getWindowAncestor(
                        this
                );


        if (owner == null) {

            dragGhost =
                    new JWindow();

        } else {

            dragGhost =
                    new JWindow(
                            owner
                    );
        }


        dragGhost.setBackground(
                new java.awt.Color(
                        0,
                        0,
                        0,
                        0
                )
        );


        dragGhostLabel =
                new SolidPieceLabel(
                        getSolidPieceSymbol(
                                piece.type()
                        ),
                        piece.color()
                );


        dragGhostLabel.setFont(
                new Font(
                        Font.SERIF,
                        Font.PLAIN,
                        58
                )
        );


        dragGhostLabel.setHorizontalAlignment(
                SwingConstants.CENTER
        );


        dragGhostLabel.setVerticalAlignment(
                SwingConstants.CENTER
        );


        dragGhostLabel.setOpaque(
                false
        );


        dragGhostLabel.setBorder(
                null
        );


        dragGhostLabel.setDarkTheme(
                darkTheme
        );


        dragGhost.getContentPane()
                .setBackground(
                        new java.awt.Color(
                                0,
                                0,
                                0,
                                0
                        )
                );


        dragGhost.getContentPane()
                .add(
                        dragGhostLabel
                );


        dragGhost.setSize(
                64,
                64
        );


        dragGhost.setFocusableWindowState(
                false
        );


        dragGhost.setAlwaysOnTop(
                true
        );


        dragGhost.setVisible(
                true
        );
    }


    private void updateDragGhostLocation() {

        if (dragGhost == null) {
            return;
        }


        PointerInfo pointer =
                MouseInfo.getPointerInfo();


        if (pointer == null) {
            return;
        }


        Point mouse =
                pointer.getLocation();


        dragGhost.setLocation(
                mouse.x - 32,
                mouse.y - 32
        );
    }


    private void hideDragGhost() {

        if (dragGhost == null) {
            return;
        }


        dragGhost.setVisible(
                false
        );


        dragGhost.dispose();


        dragGhost =
                null;


        dragGhostLabel =
                null;
    }


    // =========================================================
    // Setup control styling
    // =========================================================

    private void styleControl(
            javax.swing.AbstractButton button
    ) {

        button.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.BOLD,
                        11
                )
        );


        button.setFocusPainted(
                false
        );


        button.setFocusable(
                false
        );


        button.setCursor(
                Cursor.getPredefinedCursor(
                        Cursor.HAND_CURSOR
                )
        );


        button.putClientProperty(
                "setupControl",
                Boolean.TRUE
        );
    }


    // =========================================================
    // Recursive theme
    // =========================================================

    private void updateChildTheme(
            java.awt.Container container
    ) {

        for (java.awt.Component component :
                container.getComponents()) {


            if (component instanceof JLabel label
                    && Boolean.TRUE.equals(
                    label.getClientProperty(
                            "setupLabel"
                    )
            )) {

                label.setForeground(
                        foregroundColor
                );
            }


            if (component instanceof JLabel label
                    && Boolean.TRUE.equals(
                    label.getClientProperty(
                            "setupSecondaryLabel"
                    )
            )) {

                label.setForeground(
                        secondaryForegroundColor
                );
            }


            if (component
                    instanceof javax.swing.AbstractButton button
                    && Boolean.TRUE.equals(
                    button.getClientProperty(
                            "setupControl"
                    )
            )) {


                /*
                 * M68C6E:
                 *
                 * Every setup button now uses the SAME theme background.
                 *
                 * No special dark square behind White pieces in light mode.
                 */
                button.setBackground(
                        buttonColor
                );


                button.setBorder(
                        BorderFactory.createLineBorder(
                                borderColor,
                                1,
                                true
                        )
                );


                button.setOpaque(
                        true
                );


                button.setContentAreaFilled(
                        true
                );


                boolean palettePiece =
                        Boolean.TRUE.equals(
                                button.getClientProperty(
                                        "palettePiece"
                                )
                        );


                if (palettePiece
                        && button instanceof SolidPieceButton pieceButton) {


                    pieceButton.setDarkTheme(
                            darkTheme
                    );


                } else {


                    button.setForeground(
                            foregroundColor
                    );
                }
            }


            if (component
                    instanceof java.awt.Container child) {

                updateChildTheme(
                        child
                );
            }
        }
    }


    // =========================================================
    // Custom solid piece button
    // =========================================================

    /**
     * Paints a solid chess glyph without changing the normal JButton
     * background.
     *
     * In light mode White pieces receive a tiny dark outline so a genuinely
     * white glyph remains visible on the normal light setup-button surface.
     */
    private static final class SolidPieceButton
            extends JButton {

        private final String symbol;
        private final Color pieceColor;

        private boolean darkTheme;


        private SolidPieceButton(
                String symbol,
                Color pieceColor
        ) {

            super("");

            this.symbol =
                    symbol;

            this.pieceColor =
                    pieceColor;

            this.darkTheme =
                    true;
        }


        private void setDarkTheme(
                boolean darkTheme
        ) {

            this.darkTheme =
                    darkTheme;

            repaint();
        }


        @Override
        protected void paintComponent(
                Graphics graphics
        ) {

            /*
             * Let JButton paint its normal themed background first.
             */
            super.paintComponent(
                    graphics
            );


            Graphics2D g2 =
                    (Graphics2D) graphics.create();


            try {

                g2.setRenderingHint(
                        RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON
                );


                g2.setFont(
                        getFont()
                );


                java.awt.FontMetrics metrics =
                        g2.getFontMetrics();


                int textWidth =
                        metrics.stringWidth(
                                symbol
                        );


                int x =
                        (getWidth() - textWidth)
                                / 2;


                int y =
                        (getHeight()
                                - metrics.getHeight())
                                / 2
                                + metrics.getAscent();


                if (pieceColor == Color.WHITE) {


                    /*
                     * In light mode, draw a very small dark outline.
                     *
                     * This gives the solid white piece definition without
                     * putting it inside a dark rectangular tile.
                     */
                    if (!darkTheme) {

                        g2.setColor(
                                new java.awt.Color(
                                        70,
                                        76,
                                        84
                                )
                        );


                        g2.drawString(
                                symbol,
                                x - 1,
                                y
                        );


                        g2.drawString(
                                symbol,
                                x + 1,
                                y
                        );


                        g2.drawString(
                                symbol,
                                x,
                                y - 1
                        );


                        g2.drawString(
                                symbol,
                                x,
                                y + 1
                        );
                    }


                    g2.setColor(
                            java.awt.Color.WHITE
                    );


                    g2.drawString(
                            symbol,
                            x,
                            y
                    );


                } else {


                    g2.setColor(
                            java.awt.Color.BLACK
                    );


                    g2.drawString(
                            symbol,
                            x,
                            y
                    );
                }


            } finally {

                g2.dispose();
            }
        }
    }


    // =========================================================
    // Custom drag label
    // =========================================================

    private static final class SolidPieceLabel
            extends JLabel {

        private final String symbol;
        private final Color pieceColor;

        private boolean darkTheme;


        private SolidPieceLabel(
                String symbol,
                Color pieceColor
        ) {

            super("");

            this.symbol =
                    symbol;

            this.pieceColor =
                    pieceColor;

            this.darkTheme =
                    true;
        }


        private void setDarkTheme(
                boolean darkTheme
        ) {

            this.darkTheme =
                    darkTheme;

            repaint();
        }


        @Override
        protected void paintComponent(
                Graphics graphics
        ) {

            Graphics2D g2 =
                    (Graphics2D) graphics.create();


            try {

                g2.setRenderingHint(
                        RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON
                );


                g2.setFont(
                        getFont()
                );


                java.awt.FontMetrics metrics =
                        g2.getFontMetrics();


                int textWidth =
                        metrics.stringWidth(
                                symbol
                        );


                int x =
                        (getWidth() - textWidth)
                                / 2;


                int y =
                        (getHeight()
                                - metrics.getHeight())
                                / 2
                                + metrics.getAscent();


                if (pieceColor == Color.WHITE) {


                    /*
                     * Same light-mode outline used by the palette button.
                     */
                    if (!darkTheme) {

                        g2.setColor(
                                new java.awt.Color(
                                        70,
                                        76,
                                        84
                                )
                        );


                        g2.drawString(
                                symbol,
                                x - 1,
                                y
                        );


                        g2.drawString(
                                symbol,
                                x + 1,
                                y
                        );


                        g2.drawString(
                                symbol,
                                x,
                                y - 1
                        );


                        g2.drawString(
                                symbol,
                                x,
                                y + 1
                        );
                    }


                    g2.setColor(
                            java.awt.Color.WHITE
                    );


                    g2.drawString(
                            symbol,
                            x,
                            y
                    );


                } else {


                    g2.setColor(
                            java.awt.Color.BLACK
                    );


                    g2.drawString(
                            symbol,
                            x,
                            y
                    );
                }


            } finally {

                g2.dispose();
            }
        }
    }
}
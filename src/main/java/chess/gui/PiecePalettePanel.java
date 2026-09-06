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

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicButtonUI;
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
import java.awt.Toolkit;

import java.awt.event.AWTEventListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;


/**
 * Position-setup controls and draggable chess-piece tiles.
 * The floating drag ghost remains a piece-only window.
 */
public class PiecePalettePanel extends JPanel {

    private final ChessBoardPanel boardPanel;

    private Runnable analyzeListener;
    private Runnable cancelListener;
    private JToggleButton whiteSideButton;
    private JToggleButton blackSideButton;

    private java.awt.Color backgroundColor;
    private java.awt.Color foregroundColor;
    private java.awt.Color secondaryForegroundColor;
    private java.awt.Color borderColor;
    private java.awt.Color buttonColor;

    private boolean darkTheme;

    private JWindow dragGhost;
    private SolidPieceLabel dragGhostLabel;
    private boolean paletteDragActive;
    private final AWTEventListener globalMouseReleaseListener;
    private boolean globalMouseReleaseListenerInstalled;


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


        this.paletteDragActive =
                false;


        this.globalMouseReleaseListener =
                event -> {

                    if (!(event instanceof MouseEvent mouseEvent)
                            || mouseEvent.getID() != MouseEvent.MOUSE_RELEASED) {
                        return;
                    }


                    /*
                     * Let the button's own mouseReleased handler run first. If
                     * an OS-level interruption (for example Print Screen) caused
                     * that release to miss the button, this deferred fallback
                     * still tears down the floating JWindow ghost.
                     */
                    SwingUtilities.invokeLater(
                            () -> {

                                if (paletteDragActive) {
                                    cancelActiveDrag();
                                }
                            }
                    );
                };


        this.globalMouseReleaseListenerInstalled =
                false;


        setLayout(new BorderLayout(0, 8));
        rebuildBorder();
        add(createPieceRow(), BorderLayout.CENTER);
        add(createTopRow(), BorderLayout.NORTH);

        applyTheme(
                true
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
    // Drag lifecycle
    // =========================================================

    /**
     * Cancel any in-progress palette drag and dispose the floating drag ghost.
     * Safe to call repeatedly from Reset, Setup/Endgame transitions, and Home.
     */
    public void cancelActiveDrag() {

        paletteDragActive =
                false;


        hideDragGhost();
    }


    @Override
    public void setVisible(
            boolean visible
    ) {

        if (!visible) {
            cancelActiveDrag();
        }


        super.setVisible(
                visible
        );
    }


    @Override
    public void addNotify() {

        super.addNotify();


        if (!globalMouseReleaseListenerInstalled) {

            Toolkit.getDefaultToolkit()
                    .addAWTEventListener(
                            globalMouseReleaseListener,
                            AWTEvent.MOUSE_EVENT_MASK
                    );

            globalMouseReleaseListenerInstalled =
                    true;
        }
    }


    @Override
    public void removeNotify() {

        cancelActiveDrag();


        if (globalMouseReleaseListenerInstalled) {

            Toolkit.getDefaultToolkit()
                    .removeAWTEventListener(
                            globalMouseReleaseListener
                    );

            globalMouseReleaseListenerInstalled =
                    false;
        }


        super.removeNotify();
    }


    // =========================================================
    // Theme
    // =========================================================

    public void applyTheme(
            boolean dark
    ) {

        darkTheme =
                dark;


        backgroundColor = dark ? new java.awt.Color(14, 23, 30) : new java.awt.Color(242, 244, 247);
        foregroundColor = dark ? new java.awt.Color(240, 243, 247) : new java.awt.Color(31, 35, 41);
        secondaryForegroundColor = dark ? new java.awt.Color(177, 199, 219) : new java.awt.Color(100, 107, 117);
        borderColor = dark ? new java.awt.Color(32, 51, 64) : new java.awt.Color(215, 220, 227);
        buttonColor = dark ? new java.awt.Color(22, 30, 38) : new java.awt.Color(248, 250, 252);

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
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(borderColor),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
    }

    private JPanel createTopRow() {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        JPanel title = new JPanel(new GridLayout(2, 1, 0, 2));
        title.setOpaque(false);
        JLabel heading = new JLabel("PIECE PALETTE");
        heading.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        heading.putClientProperty("setupSecondaryLabel", Boolean.TRUE);
        JLabel help = new JLabel("Drag pieces to the board");
        help.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        help.putClientProperty("setupSecondaryLabel", Boolean.TRUE);
        title.add(heading);
        title.add(help);
        row.add(title, BorderLayout.CENTER);
        JButton clear = createActionButton("Clear Board");
        clear.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        clear.setIcon(new SetupIcon(SetupIcon.Kind.TRASH, 24));
        clear.setIconTextGap(6);
        clear.putClientProperty("quietAction", Boolean.TRUE);
        clear.addActionListener(event -> {
            cancelActiveDrag();
            boardPanel.clearSetupBoard();
        });
        row.add(clear, BorderLayout.EAST);
        return row;
    }

    // =========================================================
    // Piece row
    // =========================================================

    private JPanel createPieceRow() {
        JPanel rows = new JPanel(new GridLayout(2, 1, 0, 6));
        rows.setOpaque(false);

        for (Color color : new Color[]{Color.WHITE, Color.BLACK}) {
            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setOpaque(false);
            JLabel colorLabel = new JLabel(color == Color.WHITE ? "WHITE" : "BLACK");
            colorLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            colorLabel.putClientProperty("setupSecondaryLabel", Boolean.TRUE);
            row.add(colorLabel, BorderLayout.WEST);
            JPanel pieces = new JPanel(new GridLayout(1, 6, 6, 0));
            pieces.setOpaque(false);
            for (PieceType type : new PieceType[]{PieceType.PAWN, PieceType.KNIGHT, PieceType.BISHOP,
                    PieceType.ROOK, PieceType.QUEEN, PieceType.KING}) {
                pieces.add(createPieceDragButton(new Piece(type, color)));
            }
            row.add(pieces, BorderLayout.CENTER);
            rows.add(row);
        }
        rows.setToolTipText("Drag a piece onto the board.");
        return rows;
    }

    /** Synchronize presentation after edits/history without changing board state. */
    void refreshSideToMove() {
        if (whiteSideButton == null || blackSideButton == null) return;
        whiteSideButton.setSelected(boardPanel.getSetupSideToMove() != Color.BLACK);
        blackSideButton.setSelected(boardPanel.getSetupSideToMove() == Color.BLACK);
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


        button.setFont(
                new Font(
                        Font.SERIF,
                        Font.PLAIN,
                        34
                )
        );


        Dimension size =
                new Dimension(
                        46,
                        42
                );


        button.setPreferredSize(
                size
        );


        button.setMinimumSize(new Dimension(0, 0));


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


        button.getAccessibleContext().setAccessibleName(button.getToolTipText());
        button.setRolloverEnabled(true);

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

                    @Override
                    public void mousePressed(
                            MouseEvent event
                    ) {

                        if (!SwingUtilities.isLeftMouseButton(
                                event
                        )) {

                            return;
                        }


                        paletteDragActive =
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

                        if (!paletteDragActive) {
                            return;
                        }


                        updateDragGhostLocation();
                    }


                    @Override
                    public void mouseReleased(
                            MouseEvent event
                    ) {

                        if (!paletteDragActive) {
                            return;
                        }


                        paletteDragActive =
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


                        cancelActiveDrag();
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

    private void styleControl(AbstractButton button) {
        button.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        button.setFocusPainted(false);
        button.setFocusable(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.putClientProperty("setupControl", Boolean.TRUE);
        button.putClientProperty("primaryAction", "Analyze Position".equals(button.getText()));
        button.setRolloverEnabled(true);
        button.setUI(new BasicButtonUI() {
            @Override
            public void paint(Graphics graphics, JComponent component) {
                AbstractButton control = (AbstractButton) component;
                boolean primary = Boolean.TRUE.equals(control.getClientProperty("primaryAction"));
                boolean selected = control.isSelected();
                java.awt.Color accent = darkTheme
                        ? new java.awt.Color(63, 151, 255) : new java.awt.Color(64, 100, 145);
                java.awt.Color fill = primary
                        ? (darkTheme ? new java.awt.Color(26, 47, 69) : new java.awt.Color(64, 100, 145))
                        : selected
                        ? (darkTheme ? new java.awt.Color(20, 38, 54) : new java.awt.Color(238, 244, 252))
                        : buttonColor;
                if (control.getModel().isPressed() && control.getModel().isArmed()) {
                    fill = fill.darker();
                } else if (control.getModel().isRollover()) {
                    fill = primary ? fill.brighter()
                            : (darkTheme ? new java.awt.Color(29, 40, 51) : new java.awt.Color(232, 238, 245));
                }
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(fill);
                    if (!Boolean.TRUE.equals(control.getClientProperty("quietAction")) || control.getModel().isRollover())
                        g.fillRect(0, 0, control.getWidth(), control.getHeight());
                    g.setColor(selected ? accent : primary ? fill.brighter() : borderColor);
                    if (!Boolean.TRUE.equals(control.getClientProperty("quietAction")) || control.hasFocus())
                        g.drawRect(0, 0, control.getWidth() - 1, control.getHeight() - 1);
                } finally {
                    g.dispose();
                }
                super.paint(graphics, component);
            }

            @Override
            protected void paintButtonPressed(Graphics graphics, AbstractButton control) {
                // The pressed surface is painted above.
            }
        });
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


                button.setBackground(buttonColor);
                button.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
                button.setOpaque(false);
                button.setContentAreaFilled(false);

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
                    button.setForeground(Boolean.TRUE.equals(button.getClientProperty("primaryAction"))
                            ? java.awt.Color.WHITE : foregroundColor);
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

    /** Tactile tiles use solid surfaces; only the glyph is used by the drag ghost. */
    private static final class SolidPieceButton extends JButton {
        private final String symbol;
        private final Color pieceColor;
        private boolean darkTheme;

        private SolidPieceButton(String symbol, Color pieceColor) {
            super("");
            this.symbol = symbol;
            this.pieceColor = pieceColor;
            this.darkTheme = true;
        }

        private void setDarkTheme(boolean darkTheme) {
            this.darkTheme = darkTheme;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                boolean pressed = getModel().isPressed() && getModel().isArmed();
                boolean hovered = getModel().isRollover();
                int top = pressed ? 1 : 0;
                java.awt.Color edge = darkTheme ? new java.awt.Color(48, 62, 76) : new java.awt.Color(196, 205, 216);
                java.awt.Color fill = darkTheme ? new java.awt.Color(26, 36, 46) : new java.awt.Color(249, 250, 252);
                if (hovered) {
                    fill = darkTheme ? new java.awt.Color(33, 49, 65) : new java.awt.Color(234, 242, 250);
                    edge = darkTheme ? new java.awt.Color(63, 151, 255) : new java.awt.Color(64, 100, 145);
                }
                if (pressed) fill = fill.darker();
                g.setColor(fill);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(edge);
                g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);

                g.setFont(getFont().deriveFont(Math.min(getFont().getSize2D(),
                        Math.max(12f, Math.min(getWidth(), getHeight()) - 6f))));
                java.awt.FontMetrics metrics = g.getFontMetrics();
                int x = (getWidth() - metrics.stringWidth(symbol)) / 2;
                int y = (getHeight() - 2 - metrics.getHeight()) / 2 + metrics.getAscent() + top;
                // Outline both colors so black pieces remain legible on dark tiles.
                g.setColor(pieceColor == Color.WHITE
                        ? new java.awt.Color(75, 85, 97)
                        : (darkTheme ? new java.awt.Color(163, 176, 190) : new java.awt.Color(113, 125, 140)));
                g.drawString(symbol, x - 1, y);
                g.drawString(symbol, x + 1, y);
                g.drawString(symbol, x, y - 1);
                g.drawString(symbol, x, y + 1);
                g.setColor(pieceColor == Color.WHITE ? java.awt.Color.WHITE : java.awt.Color.BLACK);
                g.drawString(symbol, x, y);
            } finally {
                g.dispose();
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

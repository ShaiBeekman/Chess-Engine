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
import javax.swing.JWindow;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class PiecePalettePanel extends JPanel {

    private final ChessBoardPanel boardPanel;

    private Runnable analyzeListener;
    private Runnable cancelListener;

    private java.awt.Color backgroundColor;
    private java.awt.Color foregroundColor;
    private java.awt.Color borderColor;
    private java.awt.Color buttonColor;

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

        this.backgroundColor =
                new java.awt.Color(19, 27, 35);

        this.foregroundColor =
                new java.awt.Color(242, 244, 247);

        this.borderColor =
                new java.awt.Color(42, 53, 64);

        this.buttonColor =
                new java.awt.Color(26, 35, 44);

        setLayout(
                new BoxLayout(
                        this,
                        BoxLayout.Y_AXIS
                )
        );

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
    }


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


    public void applyTheme(
            boolean dark
    ) {

        backgroundColor =
                dark
                        ? new java.awt.Color(19, 27, 35)
                        : new java.awt.Color(250, 251, 253);

        foregroundColor =
                dark
                        ? new java.awt.Color(242, 244, 247)
                        : new java.awt.Color(31, 35, 41);

        borderColor =
                dark
                        ? new java.awt.Color(42, 53, 64)
                        : new java.awt.Color(210, 216, 224);

        buttonColor =
                dark
                        ? new java.awt.Color(26, 35, 44)
                        : new java.awt.Color(244, 246, 249);

        setBackground(
                backgroundColor
        );

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

        updateChildTheme(
                this
        );

        repaint();
    }


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


    private JPanel createPieceRow() {

        JPanel row =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.LEFT,
                                5,
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
                            6
                    )
            );
        }

        return row;
    }


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


    private JButton createPieceDragButton(
            Piece piece
    ) {

        JButton button =
                new JButton(
                        ChessBoardPanel.getPieceSymbol(
                                piece
                        )
                );

        button.setFont(
                new Font(
                        Font.SERIF,
                        Font.PLAIN,
                        26
                )
        );

        button.setPreferredSize(
                new Dimension(
                        42,
                        42
                )
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

        MouseAdapter drag =
                new MouseAdapter() {

                    private JWindow dragGhost;


                    @Override
                    public void mousePressed(
                            MouseEvent event
                    ) {

                        Window owner =
                                SwingUtilities.getWindowAncestor(
                                        button
                                );


                        dragGhost =
                                new JWindow(
                                        owner
                                );


                        JLabel ghostPiece =
                                new JLabel(
                                        ChessBoardPanel.getPieceSymbol(
                                                piece
                                        )
                                );


                        ghostPiece.setFont(
                                new Font(
                                        Font.SERIF,
                                        Font.PLAIN,
                                        58
                                )
                        );


                        ghostPiece.setForeground(
                                java.awt.Color.BLACK
                        );


                        ghostPiece.setOpaque(
                                false
                        );


                        dragGhost.setBackground(
                                new java.awt.Color(
                                        0,
                                        0,
                                        0,
                                        0
                                )
                        );


                        dragGhost.add(
                                ghostPiece
                        );


                        dragGhost.pack();


                        moveGhost(
                                event
                        );


                        dragGhost.setVisible(
                                true
                        );
                    }


                    @Override
                    public void mouseDragged(
                            MouseEvent event
                    ) {

                        moveGhost(
                                event
                        );
                    }


                    @Override
                    public void mouseReleased(
                            MouseEvent event
                    ) {

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


                        disposeGhost();
                    }


                    private void moveGhost(
                            MouseEvent event
                    ) {

                        if (dragGhost == null) {
                            return;
                        }


                        Point screen =
                                event.getLocationOnScreen();


                        dragGhost.setLocation(
                                screen.x
                                        - dragGhost.getWidth() / 2,
                                screen.y
                                        - dragGhost.getHeight() / 2
                        );
                    }


                    private void disposeGhost() {

                        if (dragGhost == null) {
                            return;
                        }


                        dragGhost.setVisible(
                                false
                        );


                        dragGhost.dispose();


                        dragGhost =
                                null;
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

            if (component instanceof javax.swing.AbstractButton button
                    && Boolean.TRUE.equals(
                    button.getClientProperty(
                            "setupControl"
                    )
            )) {

                button.setForeground(
                        foregroundColor
                );

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
            }

            if (component instanceof java.awt.Container child) {

                updateChildTheme(
                        child
                );
            }
        }
    }
}

package main.java.chess.gui;

import main.java.chess.model.Board;
import main.java.chess.model.Color;
import main.java.chess.model.Piece;
import main.java.chess.model.PieceType;
import main.java.chess.model.Position;
import main.java.chess.model.Square;
import main.java.chess.rules.AttackDetector;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.util.EnumMap;
import java.util.Map;

public class SetupPanel extends JPanel {

    private final JLabel sideToMoveValue;
    private final JLabel whiteMaterialValue;
    private final JLabel blackMaterialValue;
    private final JLabel validationValue;
    private final JTextArea fenArea;
    private final JButton analyzeButton;
    private final AttackDetector attackDetector;

    private Runnable analyzeListener;

    private java.awt.Color backgroundColor;
    private java.awt.Color primaryColor;
    private java.awt.Color secondaryColor;
    private java.awt.Color borderColor;
    private java.awt.Color controlColor;

    public SetupPanel() {

        attackDetector =
                new AttackDetector();

        setLayout(
                new BorderLayout()
        );

        setPreferredSize(
                new Dimension(
                        390,
                        640
                )
        );

        JPanel content =
                new JPanel();

        content.setOpaque(
                false
        );

        content.setLayout(
                new BoxLayout(
                        content,
                        BoxLayout.Y_AXIS
                )
        );

        content.setBorder(
                BorderFactory.createEmptyBorder(
                        20,
                        20,
                        20,
                        20
                )
        );

        JLabel title =
                new JLabel(
                        "SETUP POSITION"
                );

        title.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.BOLD,
                        13
                )
        );

        title.putClientProperty(
                "primary",
                Boolean.TRUE
        );

        JLabel subtitle =
                new JLabel(
                        "Build a position on the board."
                );

        subtitle.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.PLAIN,
                        12
                )
        );

        subtitle.putClientProperty(
                "secondary",
                Boolean.TRUE
        );

        content.add(
                title
        );

        content.add(
                Box.createVerticalStrut(
                        4
                )
        );

        content.add(
                subtitle
        );

        content.add(
                Box.createVerticalStrut(
                        24
                )
        );

        content.add(
                sectionLabel(
                        "SIDE TO MOVE"
                )
        );

        content.add(
                Box.createVerticalStrut(
                        6
                )
        );

        sideToMoveValue =
                valueLabel();

        content.add(
                sideToMoveValue
        );

        content.add(
                Box.createVerticalStrut(
                        22
                )
        );

        content.add(
                sectionLabel(
                        "MATERIAL"
                )
        );

        content.add(
                Box.createVerticalStrut(
                        8
                )
        );

        whiteMaterialValue =
                valueLabel();

        blackMaterialValue =
                valueLabel();

        content.add(
                whiteMaterialValue
        );

        content.add(
                Box.createVerticalStrut(
                        5
                )
        );

        content.add(
                blackMaterialValue
        );

        content.add(
                Box.createVerticalStrut(
                        22
                )
        );

        content.add(
                sectionLabel(
                        "POSITION STATUS"
                )
        );

        content.add(
                Box.createVerticalStrut(
                        8
                )
        );

        validationValue =
                valueLabel();

        content.add(
                validationValue
        );

        content.add(
                Box.createVerticalStrut(
                        22
                )
        );

        content.add(
                sectionLabel(
                        "FEN"
                )
        );

        content.add(
                Box.createVerticalStrut(
                        8
                )
        );

        fenArea =
                new JTextArea(
                        3,
                        24
                );

        fenArea.setEditable(
                false
        );

        fenArea.setLineWrap(
                true
        );

        fenArea.setWrapStyleWord(
                true
        );

        fenArea.setFont(
                new Font(
                        Font.MONOSPACED,
                        Font.PLAIN,
                        11
                )
        );

        fenArea.setBorder(
                BorderFactory.createEmptyBorder(
                        8,
                        8,
                        8,
                        8
                )
        );

        JScrollPane fenScroll =
                new JScrollPane(
                        fenArea
                );

        fenScroll.setMaximumSize(
                new Dimension(
                        Integer.MAX_VALUE,
                        82
                )
        );

        fenScroll.setPreferredSize(
                new Dimension(
                        340,
                        82
                )
        );

        content.add(
                fenScroll
        );

        content.add(
                Box.createVerticalGlue()
        );

        analyzeButton =
                new JButton(
                        "Analyze Position"
                );

        analyzeButton.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.BOLD,
                        12
                )
        );

        analyzeButton.setFocusPainted(
                false
        );

        analyzeButton.setCursor(
                Cursor.getPredefinedCursor(
                        Cursor.HAND_CURSOR
                )
        );

        analyzeButton.setMaximumSize(
                new Dimension(
                        Integer.MAX_VALUE,
                        42
                )
        );

        analyzeButton.setPreferredSize(
                new Dimension(
                        340,
                        42
                )
        );

        analyzeButton.addActionListener(
                event -> {

                    if (analyzeListener != null) {
                        analyzeListener.run();
                    }
                }
        );

        content.add(
                analyzeButton
        );

        add(
                content,
                BorderLayout.CENTER
        );

        applyTheme(
                true
        );
    }


    public void setAnalyzeListener(
            Runnable listener
    ) {
        analyzeListener =
                listener;
    }


    public void updateFromBoard(
            Board board,
            Color sideToMove
    ) {

        if (board == null) {
            return;
        }

        sideToMoveValue.setText(
                sideToMove == Color.BLACK
                        ? "Black"
                        : "White"
        );

        whiteMaterialValue.setText(
                "White: "
                        + materialString(
                        board,
                        Color.WHITE
                )
        );

        blackMaterialValue.setText(
                "Black: "
                        + materialString(
                        board,
                        Color.BLACK
                )
        );

        String validation =
                validationText(
                        board,
                        sideToMove
                );

        validationValue.setText(
                validation
        );

        boolean valid =
                validation.startsWith(
                        "Ready"
                );

        analyzeButton.setEnabled(
                valid
        );

        fenArea.setText(
                buildSetupFen(
                        board,
                        sideToMove
                )
        );
    }


    public void applyTheme(
            boolean dark
    ) {

        backgroundColor =
                dark
                        ? new java.awt.Color(19, 27, 35)
                        : new java.awt.Color(250, 251, 253);

        primaryColor =
                dark
                        ? new java.awt.Color(242, 244, 247)
                        : new java.awt.Color(31, 35, 41);

        secondaryColor =
                dark
                        ? new java.awt.Color(164, 173, 184)
                        : new java.awt.Color(100, 107, 117);

        borderColor =
                dark
                        ? new java.awt.Color(42, 53, 64)
                        : new java.awt.Color(210, 216, 224);

        controlColor =
                dark
                        ? new java.awt.Color(26, 35, 44)
                        : new java.awt.Color(244, 246, 249);

        setBackground(
                backgroundColor
        );

        setBorder(
                BorderFactory.createLineBorder(
                        borderColor,
                        1,
                        true
                )
        );

        applyThemeRecursively(
                this
        );

        fenArea.setForeground(
                primaryColor
        );

        fenArea.setBackground(
                controlColor
        );

        analyzeButton.setForeground(
                primaryColor
        );

        analyzeButton.setBackground(
                controlColor
        );

        analyzeButton.setBorder(
                BorderFactory.createLineBorder(
                        borderColor,
                        1,
                        true
                )
        );

        analyzeButton.setOpaque(
                true
        );

        analyzeButton.setContentAreaFilled(
                true
        );

        repaint();
    }


    private JLabel sectionLabel(
            String text
    ) {

        JLabel label =
                new JLabel(
                        text
                );

        label.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.BOLD,
                        10
                )
        );

        label.putClientProperty(
                "secondary",
                Boolean.TRUE
        );

        return label;
    }


    private JLabel valueLabel() {

        JLabel label =
                new JLabel();

        label.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.PLAIN,
                        12
                )
        );

        label.putClientProperty(
                "primary",
                Boolean.TRUE
        );

        return label;
    }


    private String materialString(
            Board board,
            Color color
    ) {

        Map<PieceType, Integer> counts =
                new EnumMap<>(
                        PieceType.class
                );

        for (PieceType type :
                PieceType.values()) {

            counts.put(
                    type,
                    0
            );
        }

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

                if (piece == null
                        || piece.color()
                        != color) {
                    continue;
                }

                counts.put(
                        piece.type(),
                        counts.get(
                                piece.type()
                        ) + 1
                );
            }
        }

        StringBuilder result =
                new StringBuilder();

        appendMaterial(
                result,
                "K",
                counts.get(
                        PieceType.KING
                )
        );

        appendMaterial(
                result,
                "Q",
                counts.get(
                        PieceType.QUEEN
                )
        );

        appendMaterial(
                result,
                "R",
                counts.get(
                        PieceType.ROOK
                )
        );

        appendMaterial(
                result,
                "B",
                counts.get(
                        PieceType.BISHOP
                )
        );

        appendMaterial(
                result,
                "N",
                counts.get(
                        PieceType.KNIGHT
                )
        );

        appendMaterial(
                result,
                "P",
                counts.get(
                        PieceType.PAWN
                )
        );

        if (result.length() == 0) {
            return "—";
        }

        return result.toString();
    }


    private void appendMaterial(
            StringBuilder builder,
            String symbol,
            int count
    ) {

        if (count <= 0) {
            return;
        }

        if (builder.length() > 0) {
            builder.append(
                    "  "
            );
        }

        builder.append(
                symbol
        );

        if (count > 1) {

            builder.append(
                    "×"
            );

            builder.append(
                    count
            );
        }
    }


    private String validationText(
            Board board,
            Color sideToMove
    ) {

        int whiteKings =
                countPiece(
                        board,
                        Color.WHITE,
                        PieceType.KING
                );

        int blackKings =
                countPiece(
                        board,
                        Color.BLACK,
                        PieceType.KING
                );

        if (whiteKings == 0) {
            return "White king missing";
        }

        if (whiteKings > 1) {
            return "More than one white king";
        }

        if (blackKings == 0) {
            return "Black king missing";
        }

        if (blackKings > 1) {
            return "More than one black king";
        }

        Square whiteKing =
                findKing(
                        board,
                        Color.WHITE
                );

        Square blackKing =
                findKing(
                        board,
                        Color.BLACK
                );

        if (whiteKing != null
                && blackKing != null
                && Math.abs(
                whiteKing.file()
                        - blackKing.file()
        ) <= 1
                && Math.abs(
                whiteKing.rank()
                        - blackKing.rank()
        ) <= 1) {

            return "Kings cannot be adjacent";
        }

        for (int file = 0;
             file < 8;
             file++) {

            Piece firstRank =
                    board.getPiece(
                            new Square(
                                    file,
                                    0
                            )
                    );

            Piece eighthRank =
                    board.getPiece(
                            new Square(
                                    file,
                                    7
                            )
                    );

            if ((firstRank != null
                    && firstRank.type()
                    == PieceType.PAWN)
                    ||
                    (eighthRank != null
                            && eighthRank.type()
                            == PieceType.PAWN)) {

                return "Pawn cannot be on rank 1 or 8";
            }
        }

        Color actualSideToMove =
                sideToMove == null
                        ? Color.WHITE
                        : sideToMove;

        Color previousMover =
                actualSideToMove.opposite();

        Square previousKing =
                findKing(
                        board,
                        previousMover
                );

        if (previousKing != null
                && attackDetector.isSquareAttacked(
                board,
                previousKing,
                actualSideToMove
        )) {

            return (previousMover == Color.WHITE
                    ? "White"
                    : "Black")
                    + " king cannot already be in check when "
                    + (actualSideToMove == Color.WHITE
                    ? "White"
                    : "Black")
                    + " is to move";
        }

        return "Ready to analyze ✓";
    }


    private int countPiece(
            Board board,
            Color color,
            PieceType type
    ) {

        int count = 0;

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

                if (piece != null
                        && piece.color()
                        == color
                        && piece.type()
                        == type) {

                    count++;
                }
            }
        }

        return count;
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
                        new Square(
                                file,
                                rank
                        );

                Piece piece =
                        board.getPiece(
                                square
                        );

                if (piece != null
                        && piece.color()
                        == color
                        && piece.type()
                        == PieceType.KING) {

                    return square;
                }
            }
        }

        return null;
    }


    private String buildSetupFen(
            Board board,
            Color sideToMove
    ) {

        StringBuilder fen =
                new StringBuilder();

        for (int rank = 7;
             rank >= 0;
             rank--) {

            int empty =
                    0;

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

                    empty++;
                    continue;
                }

                if (empty > 0) {

                    fen.append(
                            empty
                    );

                    empty =
                            0;
                }

                fen.append(
                        fenPiece(
                                piece
                        )
                );
            }

            if (empty > 0) {

                fen.append(
                        empty
                );
            }

            if (rank > 0) {

                fen.append(
                        '/'
                );
            }
        }

        fen.append(
                sideToMove == Color.BLACK
                        ? " b - - 0 1"
                        : " w - - 0 1"
        );

        return fen.toString();
    }


    private char fenPiece(
            Piece piece
    ) {

        char symbol =
                switch (piece.type()) {
                    case KING -> 'k';
                    case QUEEN -> 'q';
                    case ROOK -> 'r';
                    case BISHOP -> 'b';
                    case KNIGHT -> 'n';
                    case PAWN -> 'p';
                };

        if (piece.color()
                == Color.WHITE) {

            return Character.toUpperCase(
                    symbol
            );
        }

        return symbol;
    }


    private void applyThemeRecursively(
            java.awt.Container container
    ) {

        for (java.awt.Component component :
                container.getComponents()) {

            if (component instanceof JLabel label) {

                if (Boolean.TRUE.equals(
                        label.getClientProperty(
                                "secondary"
                        )
                )) {

                    label.setForeground(
                            secondaryColor
                    );

                } else {

                    label.setForeground(
                            primaryColor
                    );
                }
            }

            if (component instanceof java.awt.Container child) {

                applyThemeRecursively(
                        child
                );
            }
        }
    }
}

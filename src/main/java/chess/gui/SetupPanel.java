package main.java.chess.gui;

import main.java.chess.model.Board;
import main.java.chess.model.Color;
import main.java.chess.model.Piece;
import main.java.chess.model.PieceType;
import main.java.chess.model.Square;
import main.java.chess.rules.AttackDetector;

import javax.swing.*;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.font.TextAttribute;
import java.util.Map;
import java.util.function.Consumer;

/** Position construction dashboard; rules and FEN remain owned by the existing validator. */
public class SetupPanel extends JPanel {
    private final JToggleButton whiteSideButton;
    private final JToggleButton blackSideButton;
    private final JLabel whitePieceCount = label("0 pieces", 17, false, true);
    private final JLabel blackPieceCount = label("0 pieces", 17, false, true);
    private final JLabel whiteMaterialTotal = label("0 material", 17, false, true);
    private final JLabel blackMaterialTotal = label("0 material", 17, false, true);
    private final JLabel statusLabel = sectionLabel("POSITION");
    private final JLabel statusDetail = label("Awaiting position", 16, false, true);
    private final JLabel[] checkLabels = new JLabel[4];
    private final int[] checkStates = {-1, -1, -1, -1};
    private final JTextArea validationValue;
    private final JTextField fenArea;
    private final JButton analyzeButton;
    private final AttackDetector attackDetector = new AttackDetector();
    private Runnable analyzeListener;
    private Runnable clearListener;
    private Runnable cancelListener;
    private Consumer<Color> sideToMoveListener;
    private boolean hasPosition;
    private boolean positionValid;
    private boolean darkTheme;
    private java.awt.Color backgroundColor, primaryColor, secondaryColor, borderColor;
    private java.awt.Color controlColor, cardColor, accentColor, warningColor, readyColor, statusReadyColor;

    public SetupPanel() {
        setLayout(new BorderLayout());
        DashboardContent content = new DashboardContent();

        JPanel heading = transparentPanel(new BorderLayout(12, 0));
        JPanel title = transparentPanel(new GridLayout(2, 1, 0, 2));
        title.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        title.add(label("POSITION SETUP", 32, true, false));
        title.add(label("Construct a position and choose the side to move.", 17, false, true));
        heading.add(title, BorderLayout.CENTER);
        JPanel status = card(new GridLayout(2, 1, 0, 0));
        status.putClientProperty("cardInset", 12);
        statusLabel.setIconTextGap(14);
        statusDetail.setBorder(BorderFactory.createEmptyBorder(0, 32, 0, 0));
        status.add(statusLabel);
        status.add(statusDetail);
        JPanel statusHost = transparentPanel(new GridBagLayout());
        statusHost.add(status);
        heading.add(statusHost, BorderLayout.EAST);
        content.add(heading);

        JPanel dashboard = transparentPanel(new GridBagLayout());
        GridBagConstraints columns = new GridBagConstraints();
        columns.fill = GridBagConstraints.BOTH;
        columns.weighty = 1;
        columns.weightx = 0.55;
        JPanel construction = new JPanel(new GridBagLayout());
        GridBagConstraints constructionRow = new GridBagConstraints();
        constructionRow.gridx = 0;
        constructionRow.fill = GridBagConstraints.BOTH;
        constructionRow.weightx = 1;
        constructionRow.weighty = 0.42;
        construction.setOpaque(false);
        JPanel sideCard = card(new BorderLayout(0, 14));
        sideCard.add(sectionLabel("SIDE TO MOVE"), BorderLayout.NORTH);
        JPanel segments = transparentPanel(new GridLayout(1, 2, 8, 0));
        whiteSideButton = sideButton("White", Color.WHITE);
        blackSideButton = sideButton("Black", Color.BLACK);
        ButtonGroup sideGroup = new ButtonGroup();
        sideGroup.add(whiteSideButton);
        sideGroup.add(blackSideButton);
        whiteSideButton.setSelected(true);
        segments.add(whiteSideButton);
        segments.add(blackSideButton);
        sideCard.add(segments, BorderLayout.CENTER);
        construction.add(sideCard, constructionRow);
        JPanel material = card(new BorderLayout(0, 12));
        material.add(sectionLabel("MATERIAL SUMMARY"), BorderLayout.NORTH);
        JPanel cells = transparentPanel(new GridLayout(1, 2, 8, 0));
        cells.add(materialCell("WHITE", whitePieceCount, whiteMaterialTotal));
        cells.add(materialCell("BLACK", blackPieceCount, blackMaterialTotal));
        material.add(cells, BorderLayout.CENTER);
        constructionRow.gridy = 1;
        constructionRow.weighty = 0.58;
        constructionRow.insets = new Insets(12, 0, 0, 0);
        construction.add(material, constructionRow);
        dashboard.add(construction, columns);

        JPanel state = card(new BorderLayout(0, 16));
        JLabel stateHeading = sectionLabel("POSITION STATE");
        stateHeading.putClientProperty("divider", Boolean.TRUE);
        state.add(stateHeading, BorderLayout.NORTH);
        JPanel checks = transparentPanel(new GridLayout(4, 1, 0, 6));
        String[] names = {"Both kings present", "Kings separated", "Pawn placement valid", "Position legal"};
        for (int i = 0; i < names.length; i++) {
            checkLabels[i] = label(names[i], 17, false, true);
            checkLabels[i].setIconTextGap(16);
            checks.add(checkLabels[i]);
        }
        JPanel stateBody = transparentPanel(new BorderLayout(0, 12));
        stateBody.add(checks, BorderLayout.NORTH);
        validationValue = new JTextArea(2, 1);
        validationValue.setEditable(false);
        validationValue.setFocusable(false);
        validationValue.setLineWrap(true);
        validationValue.setWrapStyleWord(true);
        validationValue.setOpaque(false);
        validationValue.setFont(uiFont(14, false));
        validationValue.getAccessibleContext().setAccessibleName("Position validation details");
        stateBody.add(validationValue, BorderLayout.CENTER);
        state.add(stateBody, BorderLayout.CENTER);
        columns.gridx = 1;
        columns.weightx = 0.45;
        columns.insets = new Insets(0, 18, 0, 0);
        dashboard.add(state, columns);
        content.add(dashboard);

        JPanel fenCard = card(new BorderLayout(0, 14));
        fenCard.add(sectionLabel("FEN PREVIEW"), BorderLayout.NORTH);
        JPanel fenStrip = transparentPanel(new BorderLayout(8, 0));
        fenArea = new JTextField();
        fenArea.setEditable(false);
        fenArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 16));
        fenArea.getAccessibleContext().setAccessibleName("Live FEN preview");
        fenStrip.add(fenArea, BorderLayout.CENTER);
        JButton copy = actionButton("", false);
        copy.setIcon(new SetupIcon(SetupIcon.Kind.COPY, 24));
        copy.setToolTipText("Copy FEN");
        copy.getAccessibleContext().setAccessibleName("Copy FEN");
        copy.addActionListener(event -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(fenArea.getText()), null);
            copy.setToolTipText("FEN copied");
        });
        fenStrip.add(copy, BorderLayout.EAST);
        fenCard.add(fenStrip, BorderLayout.CENTER);
        content.add(fenCard);

        JPanel actionsCard = card(new BorderLayout(0, 12));
        actionsCard.add(sectionLabel("ACTIONS"), BorderLayout.NORTH);
        JPanel actions = transparentPanel(new GridBagLayout());
        GridBagConstraints action = new GridBagConstraints();
        action.fill = GridBagConstraints.BOTH;
        action.weighty = 1;
        action.weightx = 0.24;
        JButton clear = actionButton("Clear", false);
        clear.setIcon(new SetupIcon(SetupIcon.Kind.TRASH, 24));
        clear.addActionListener(event -> { if (clearListener != null) clearListener.run(); });
        actions.add(clear, action);
        JButton cancel = actionButton("Cancel", false);
        cancel.setIcon(new SetupIcon(SetupIcon.Kind.CLOSE, 24));
        cancel.addActionListener(event -> { if (cancelListener != null) cancelListener.run(); });
        action.gridx = 1;
        action.weightx = 0.27;
        action.insets = new Insets(0, 12, 0, 0);
        actions.add(cancel, action);
        analyzeButton = actionButton("ANALYZE POSITION", true);
        analyzeButton.setIcon(new SetupIcon(SetupIcon.Kind.PLAY, 24));
        analyzeButton.setEnabled(false);
        analyzeButton.addActionListener(event -> { if (analyzeListener != null) analyzeListener.run(); });
        action.gridx = 2;
        action.weightx = 0.49;
        actions.add(analyzeButton, action);
        actionsCard.add(actions, BorderLayout.CENTER);
        content.add(actionsCard);

        JPanel help = card(new BorderLayout(18, 0));
        JLabel info = new JLabel(new SetupIcon(SetupIcon.Kind.INFO, 48));
        info.setVerticalAlignment(SwingConstants.TOP);
        info.putClientProperty("accent", Boolean.TRUE);
        help.add(info, BorderLayout.WEST);
        JPanel helpText = transparentPanel(new BorderLayout(0, 10));
        JLabel helpTitle = sectionLabel("POSITION SETUP");
        helpTitle.putClientProperty("accent", Boolean.TRUE);
        helpText.add(helpTitle, BorderLayout.NORTH);
        JTextArea instructions = new JTextArea("Drag pieces from the palette to build a position. Choose the side to move and click \"Analyze Position\" to evaluate with the engine.");
        instructions.setRows(2);
        instructions.setColumns(1);
        instructions.setFont(uiFont(16, false));
        instructions.setEditable(false);
        instructions.setFocusable(false);
        instructions.setLineWrap(true);
        instructions.setWrapStyleWord(true);
        instructions.setOpaque(false);
        helpText.add(instructions, BorderLayout.CENTER);
        help.add(helpText, BorderLayout.CENTER);
        content.add(help);

        content.configureRows();
        add(content, BorderLayout.CENTER);
        applyTheme(true);
    }

    public void setAnalyzeListener(Runnable listener) { analyzeListener = listener; }
    public void setClearListener(Runnable listener) { clearListener = listener; }
    public void setCancelListener(Runnable listener) { cancelListener = listener; }
    public void setSideToMoveListener(Consumer<Color> listener) { sideToMoveListener = listener; }

    public void updateFromBoard(Board board, Color sideToMove) {
        if (board == null) return;
        whiteSideButton.setSelected(sideToMove != Color.BLACK);
        blackSideButton.setSelected(sideToMove == Color.BLACK);
        updateMaterial(board, Color.WHITE, whitePieceCount, whiteMaterialTotal);
        updateMaterial(board, Color.BLACK, blackPieceCount, blackMaterialTotal);

        String validation = validationText(board, sideToMove);
        validationValue.setText(validation);
        validationValue.setVisible(!validation.startsWith("Ready"));
        revalidate();
        boolean valid = validation.startsWith("Ready");
        analyzeButton.setEnabled(valid);
        hasPosition = true;
        positionValid = valid;
        updateCheckStates(validation, valid);
        updateStatusTheme();
        fenArea.setText(buildSetupFen(board, sideToMove));
        fenArea.setCaretPosition(0);
        fenArea.setToolTipText(fenArea.getText());
    }

    /** Display-only summary: piece count includes kings; material excludes kings. */
    private void updateMaterial(Board board, Color color, JLabel countLabel, JLabel totalLabel) {
        int count = 0;
        int total = 0;
        for (int rank = 0; rank < 8; rank++) {
            for (int file = 0; file < 8; file++) {
                Piece piece = board.getPiece(new Square(file, rank));
                if (piece == null || piece.color() != color) continue;
                count++;
                total += switch (piece.type()) {
                    case PAWN -> 1;
                    case KNIGHT, BISHOP -> 3;
                    case ROOK -> 5;
                    case QUEEN -> 9;
                    case KING -> 0;
                };
            }
        }
        countLabel.setText(count + " pieces");
        totalLabel.setText(total + " material");
    }

    /** Reflect the existing validator's short-circuit order; do not run new rules. */
    private void updateCheckStates(String validation, boolean valid) {
        int failure = valid ? 4 : switch (validation) {
            case "White king missing", "Black king missing",
                    "More than one white king", "More than one black king" -> 0;
            case "Kings cannot be adjacent" -> 1;
            case "Pawn cannot be on rank 1 or 8" -> 2;
            default -> 3;
        };
        for (int i = 0; i < checkStates.length; i++) {
            checkStates[i] = i < failure ? 1 : i == failure ? 0 : -1;
            String detail = checkStates[i] == 1 ? "Passed"
                    : checkStates[i] == 0 ? validation : "Pending earlier checks";
            checkLabels[i].setToolTipText(detail);
            checkLabels[i].getAccessibleContext().setAccessibleDescription(detail);
        }
    }

    private void updateStatusTheme() {
        statusLabel.setText(!hasPosition ? "POSITION" : positionValid ? "READY" : "NEEDS EDIT");
        statusDetail.setText(!hasPosition ? "Awaiting position" : positionValid ? "Valid position" : "Review position");
        statusLabel.setForeground(!hasPosition ? secondaryColor : positionValid ? statusReadyColor : warningColor);
        statusLabel.setIcon(new StateIndicator(!hasPosition ? -1 : positionValid ? 1 : 0, true));
        validationValue.setForeground(positionValid ? secondaryColor : warningColor);
        for (int i = 0; i < checkStates.length; i++) {
            checkLabels[i].setIcon(new StateIndicator(checkStates[i]));
            checkLabels[i].setForeground(secondaryColor);
        }
    }

    public void applyTheme(boolean dark) {
        darkTheme = dark;
        backgroundColor = dark ? new java.awt.Color(12, 20, 26) : new java.awt.Color(242, 245, 248);
        cardColor = dark ? new java.awt.Color(14, 23, 30) : java.awt.Color.WHITE;
        primaryColor = dark ? new java.awt.Color(241, 245, 250) : new java.awt.Color(31, 43, 55);
        secondaryColor = dark ? new java.awt.Color(177, 199, 219) : new java.awt.Color(77, 99, 118);
        borderColor = dark ? new java.awt.Color(32, 51, 64) : new java.awt.Color(202, 213, 224);
        controlColor = dark ? new java.awt.Color(13, 23, 30) : new java.awt.Color(248, 250, 252);
        accentColor = dark ? new java.awt.Color(0, 189, 244) : new java.awt.Color(0, 117, 172);
        warningColor = dark ? new java.awt.Color(226, 174, 104) : new java.awt.Color(151, 90, 25);
        readyColor = dark ? new java.awt.Color(61, 173, 92) : new java.awt.Color(42, 143, 76);
        statusReadyColor = dark ? new java.awt.Color(36, 205, 178) : new java.awt.Color(21, 127, 100);
        setBackground(backgroundColor);
        setBorder(BorderFactory.createLineBorder(borderColor));
        applyThemeRecursively(this);
        fenArea.setForeground(primaryColor);
        fenArea.setBackground(backgroundColor);
        fenArea.setCaretColor(primaryColor);
        fenArea.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(borderColor),
                BorderFactory.createEmptyBorder(6, 18, 6, 12)));
        fenArea.setSelectionColor(dark ? new java.awt.Color(35, 71, 108) : new java.awt.Color(209, 227, 246));
        fenArea.setSelectedTextColor(primaryColor);
        updateStatusTheme();
        for (Component component : getComponents()) {
            if (component instanceof DashboardContent content) content.lastInset = -1;
        }
        revalidate();
        repaint();
    }

    private JPanel materialCell(String name, JLabel count, JLabel total) {
        JPanel cell = card(new BorderLayout(0, 12));
        cell.putClientProperty("cardInset", 12);
        cell.add(sectionLabel(name), BorderLayout.NORTH);
        JPanel values = transparentPanel(new GridLayout(2, 1, 0, 2));
        values.add(count);
        values.add(total);
        JLabel icon = new JLabel(new SetupIcon(SetupIcon.Kind.MATERIAL, 34));
        icon.putClientProperty("mutedIcon", "BLACK".equals(name));
        icon.putClientProperty("materialIcon", Boolean.TRUE);
        JPanel detail = transparentPanel(new BorderLayout(16, 0));
        detail.add(icon, BorderLayout.WEST);
        JPanel valueHost = transparentPanel(new GridBagLayout());
        GridBagConstraints valueConstraints = new GridBagConstraints();
        valueConstraints.weightx = 1;
        valueConstraints.fill = GridBagConstraints.HORIZONTAL;
        valueHost.add(values, valueConstraints);
        detail.add(valueHost, BorderLayout.CENTER);
        cell.add(detail, BorderLayout.CENTER);
        count.setToolTipText("Number of pieces, including the king.");
        total.setToolTipText("Material points: pawn 1, knight/bishop 3, rook 5, queen 9; king excluded.");
        return cell;
    }

    private JToggleButton sideButton(String text, Color side) {
        JToggleButton button = new JToggleButton(text);
        styleButton(button, false);
        button.addActionListener(event -> { if (sideToMoveListener != null) sideToMoveListener.accept(side); });
        return button;
    }

    private JButton actionButton(String text, boolean primary) {
        JButton button = new JButton(text);
        styleButton(button, primary);
        return button;
    }

    private void styleButton(AbstractButton button, boolean primary) {
        button.setFont(uiFont(primary ? 21 : 20, true));
        button.setIconTextGap(16);
        button.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setRolloverEnabled(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.putClientProperty("primaryAction", primary);
        button.setUI(new BasicButtonUI() {
            @Override public void paint(Graphics graphics, JComponent component) {
                AbstractButton control = (AbstractButton) component;
                boolean selected = control.isSelected();
                java.awt.Color fill = primary && control.isEnabled() ? new java.awt.Color(17, 96, 199)
                        : selected ? (darkTheme ? new java.awt.Color(9, 41, 68) : new java.awt.Color(223, 239, 250)) : controlColor;
                if (control.isEnabled() && control.getModel().isPressed() && control.getModel().isArmed()) fill = fill.darker();
                else if (control.isEnabled() && control.getModel().isRollover()) fill = fill.brighter();
                graphics.setColor(fill);
                graphics.fillRect(0, 0, control.getWidth(), control.getHeight());
                graphics.setColor(selected || control.hasFocus() || (primary && control.isEnabled()) ? accentColor : borderColor.brighter());
                graphics.drawRect(0, 0, control.getWidth() - 1, control.getHeight() - 1);
                control.setForeground(primary && control.isEnabled() ? java.awt.Color.WHITE : selected ? primaryColor : secondaryColor);
                ((Graphics2D) graphics).setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                super.paint(graphics, component);
            }
            @Override protected void paintButtonPressed(Graphics graphics, AbstractButton control) { }
            @Override protected void paintText(Graphics graphics, AbstractButton control, Rectangle bounds, String text) {
                graphics.setColor(primary && control.isEnabled() ? java.awt.Color.WHITE : control.getForeground());
                graphics.drawString(text, bounds.x, bounds.y + graphics.getFontMetrics().getAscent());
            }
        });
    }

    private JPanel card(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.putClientProperty("setupCard", Boolean.TRUE);
        return panel;
    }
    private static JPanel transparentPanel(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }
    private static Font uiFont(int size, boolean bold) {
        return new Font("Segoe UI", bold ? Font.BOLD : Font.PLAIN, size);
    }
    private static JLabel label(String text, int size, boolean bold, boolean secondary) {
        JLabel label = new JLabel(text);
        Font font = uiFont(size, bold);
        if (bold) font = font.deriveFont(Map.of(TextAttribute.TRACKING, 0.055f));
        label.setFont(font);
        label.putClientProperty("secondary", secondary);
        return label;
    }
    private static JLabel sectionLabel(String text) { return label(text, 17, false, true); }

    private final class StateIndicator implements Icon {
        private final int state;
        private final boolean dot;
        private StateIndicator(int state) { this(state, false); }
        private StateIndicator(int state, boolean dot) { this.state = state; this.dot = dot; }
        public int getIconWidth() { return dot ? 18 : 28; }
        public int getIconHeight() { return dot ? 18 : 28; }
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.translate(x, y);
            int size = getIconWidth();
            g.setColor(state > 0 ? readyColor : state == 0 ? warningColor : secondaryColor);
            if (state >= 0) g.fillOval(0, 0, size, size);
            else g.drawOval(1, 1, size - 2, size - 2);
            if (!dot) {
                g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(state >= 0 ? java.awt.Color.WHITE : secondaryColor);
                if (state > 0) {
                    g.drawLine(7, 14, 12, 19);
                    g.drawLine(12, 19, 21, 9);
                } else if (state == 0) {
                    g.drawLine(14, 7, 14, 15);
                    g.fillOval(13, 19, 3, 3);
                } else g.drawLine(9, 14, 19, 14);
            }
            g.dispose();
        }
    }

    /** Natural control sizes reserve the actions; extra height expands the existing cards. */
    private final class DashboardContent extends JPanel {
        private float lastScale = -1;
        private int lastInset = -1;

        private DashboardContent() { super(new GridBagLayout()); setOpaque(false); }

        private void configureRows() {
            GridBagLayout layout = (GridBagLayout) getLayout();
            double[] weights = {0, 1, 0.15, 0.15, 0.25};
            for (int i = 0; i < getComponentCount(); i++) {
                GridBagConstraints row = new GridBagConstraints();
                row.gridx = 0;
                row.gridy = i;
                row.weightx = 1;
                row.weighty = weights[i];
                row.fill = GridBagConstraints.BOTH;
                row.insets = new Insets(i == 0 ? 0 : 12, 0, 0, 0);
                layout.setConstraints(getComponent(i), row);
            }
        }

        @Override public Dimension getPreferredSize() {
            Dimension natural = super.getPreferredSize();
            return new Dimension(800, natural.height);
        }

        @Override public Dimension getMinimumSize() { return new Dimension(0, 0); }

        @Override public void doLayout() {
            float widthScale = Math.min(1f, Math.max(0.73f, getWidth() / 800f));
            float heightScale = Math.min(1f, Math.max(0.8f, getHeight() / 740f));
            float scale = Math.min(widthScale, heightScale);
            int inset = Math.max(6, Math.min(16, (getHeight() - 480) / 25));
            if (scale != lastScale || inset != lastInset) {
                lastScale = scale;
                lastInset = inset;
                adaptSpacing(this, scale, inset);
                setBorder(BorderFactory.createEmptyBorder(inset, inset, inset, inset));
            }
            super.doLayout();
        }

        private void adaptSpacing(Container parent, float scale, int inset) {
            if (parent.getLayout() instanceof BorderLayout layout) {
                if (parent instanceof JComponent c && c.getClientProperty("baseVgap") == null)
                    c.putClientProperty("baseVgap", layout.getVgap());
                int base = (Integer) ((JComponent) parent).getClientProperty("baseVgap");
                layout.setVgap(Math.min(base, inset));
            }
            if (parent.getLayout() instanceof GridBagLayout layout) {
                for (Component child : parent.getComponents()) {
                    GridBagConstraints constraints = layout.getConstraints(child);
                    Insets gaps = constraints.insets;
                    if (gaps.top > 0) gaps.top = inset;
                    if (gaps.left > 0) gaps.left = inset;
                    layout.setConstraints(child, constraints);
                }
            }
            for (Component component : parent.getComponents()) {
                if (component instanceof JComponent c) {
                    if (c instanceof JLabel || c instanceof AbstractButton || c instanceof JTextArea || c instanceof JTextField) {
                        if (c.getClientProperty("baseFont") == null) c.putClientProperty("baseFont", c.getFont());
                        Font base = (Font) c.getClientProperty("baseFont");
                        c.setFont(base.deriveFont(base.getSize2D() * scale));
                    }
                    if (Boolean.TRUE.equals(c.getClientProperty("setupCard"))) {
                        int padding = Math.min(inset, c.getClientProperty("cardInset") instanceof Integer value ? value : 16);
                        c.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(borderColor),
                                BorderFactory.createEmptyBorder(padding, padding, padding, padding)));
                    }
                    if (Boolean.TRUE.equals(c.getClientProperty("divider"))) {
                        c.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, borderColor.brighter()),
                                BorderFactory.createEmptyBorder(0, 0, inset / 2, 0)));
                    }
                    if (c instanceof JLabel label && label.getIcon() != null) label.setIconTextGap(Math.round(12 * scale));
                    if (c instanceof AbstractButton button) {
                        button.setIconTextGap(Math.round(12 * scale));
                        button.setBorder(BorderFactory.createEmptyBorder(Math.min(8, inset), Math.round(12 * scale),
                                Math.min(8, inset), Math.round(12 * scale)));
                        if (Boolean.TRUE.equals(button.getClientProperty("primaryAction"))) {
                            button.setFont(button.getFont().deriveFont(21 * (scale < 0.85f ? 0.62f : scale)));
                        }
                    }
                    if (Boolean.TRUE.equals(c.getClientProperty("materialIcon"))) c.setVisible(scale > 0.88f);
                }
                if (component instanceof Container child) adaptSpacing(child, scale, inset);
            }
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


    private void applyThemeRecursively(Container container) {
        for (Component component : container.getComponents()) {
            if (component instanceof JPanel panel && Boolean.TRUE.equals(panel.getClientProperty("setupCard"))) {
                int inset = panel.getClientProperty("cardInset") instanceof Integer value ? value : 16;
                panel.setBackground(cardColor);
                panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(borderColor),
                        BorderFactory.createEmptyBorder(inset, inset, inset, inset)));
            }
            if (component instanceof JLabel label) {
                label.setForeground(Boolean.TRUE.equals(label.getClientProperty("accent")) ? accentColor
                        : Boolean.TRUE.equals(label.getClientProperty("mutedIcon")) ? secondaryColor.darker()
                        : Boolean.TRUE.equals(label.getClientProperty("secondary")) ? secondaryColor : primaryColor);
                if (Boolean.TRUE.equals(label.getClientProperty("divider"))) {
                    label.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, borderColor.brighter()),
                            BorderFactory.createEmptyBorder(0, 0, 12, 0)));
                }
            }
            if (component instanceof JTextArea area) area.setForeground(secondaryColor);
            if (component instanceof Container child) applyThemeRecursively(child);
        }
    }
}

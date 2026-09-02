package main.java.chess.gui;

import main.java.chess.model.Position;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Stockfish-backed counterpart to AnalysisPanel.
 *
 * The presentation intentionally mirrors Dovetail's analysis panel:
 * same header hierarchy, breadcrumb, suggested-line treatment,
 * selected-line cascade, mini-board cards, indentation, and theme.
 */
public final class StockfishCandidatePanel extends JPanel {

    public record Candidate(
            Position position,
            String san,
            int evaluation,
            int depth,
            List<String> pv
    ) {
        public Candidate {
            pv = pv == null
                    ? List.of()
                    : List.copyOf(pv);
        }
    }

    private static java.awt.Color BACKGROUND =
            new java.awt.Color(242, 244, 247);
    private static java.awt.Color CARD_BACKGROUND =
            new java.awt.Color(255, 255, 255);
    private static java.awt.Color CARD_HOVER =
            new java.awt.Color(248, 250, 252);
    private static java.awt.Color CARD_SELECTED =
            new java.awt.Color(238, 244, 252);
    private static java.awt.Color BORDER_COLOR =
            new java.awt.Color(215, 220, 227);
    private static java.awt.Color PRIMARY_TEXT =
            new java.awt.Color(31, 35, 41);
    private static java.awt.Color SECONDARY_TEXT =
            new java.awt.Color(100, 107, 117);
    private static java.awt.Color ACCENT =
            new java.awt.Color(64, 100, 145);

    private static final java.awt.Color LIGHT_BACKGROUND =
            new java.awt.Color(242, 244, 247);
    private static final java.awt.Color LIGHT_CARD_BACKGROUND =
            new java.awt.Color(255, 255, 255);
    private static final java.awt.Color LIGHT_CARD_HOVER =
            new java.awt.Color(248, 250, 252);
    private static final java.awt.Color LIGHT_CARD_SELECTED =
            new java.awt.Color(238, 244, 252);
    private static final java.awt.Color LIGHT_BORDER_COLOR =
            new java.awt.Color(215, 220, 227);
    private static final java.awt.Color LIGHT_PRIMARY_TEXT =
            new java.awt.Color(31, 35, 41);
    private static final java.awt.Color LIGHT_SECONDARY_TEXT =
            new java.awt.Color(100, 107, 117);
    private static final java.awt.Color LIGHT_ACCENT =
            new java.awt.Color(64, 100, 145);

    private static final java.awt.Color DARK_BACKGROUND =
            new java.awt.Color(15, 21, 27);
    private static final java.awt.Color DARK_CARD_BACKGROUND =
            new java.awt.Color(18, 25, 32);
    private static final java.awt.Color DARK_CARD_HOVER =
            new java.awt.Color(22, 30, 38);
    private static final java.awt.Color DARK_CARD_SELECTED =
            new java.awt.Color(20, 38, 54);
    private static final java.awt.Color DARK_BORDER_COLOR =
            new java.awt.Color(38, 48, 58);
    private static final java.awt.Color DARK_PRIMARY_TEXT =
            new java.awt.Color(240, 243, 247);
    private static final java.awt.Color DARK_SECONDARY_TEXT =
            new java.awt.Color(158, 170, 182);
    private static final java.awt.Color DARK_ACCENT =
            new java.awt.Color(63, 151, 255);

    private static final int BRANCH_INDENT = 24;

    private final JLabel titleLabel;
    private final JLabel statusLabel;
    private final JLabel evaluationLabel;
    private final JLabel breadcrumbLabel;
    private final JButton backButton;

    private final JPanel principalVariationPanel;
    private final JPanel principalVariationMovesPanel;

    private final JPanel cardsPanel;
    private final JScrollPane scrollPane;

    private final List<PathEntry> path;

    /*
     * Keyboard snapshot for Stockfish selected-line browsing.
     *
     * This mirrors AnalysisPanel's preview navigation without committing any
     * move to gameHistory. The floor depth is the already-committed prefix;
     * Left/Down never move behind it, while Right/Up can restore the deepest
     * Stockfish line the user explicitly selected.
     */
    private final List<PathEntry> keyboardPathSnapshot;
    private int keyboardPathFloorDepth;

    private Consumer<Candidate> selectionListener;
    private Runnable collapseListener;
    private Runnable backListener;

    private boolean blackPerspective;
    private boolean darkTheme;

    private Position rootPosition;
    private Position currentPosition;
    private int currentEvaluation;
    private int currentDepth;
    private List<Candidate> currentCandidates;
    private boolean analyzing;

    public StockfishCandidatePanel() {
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(510, 690));
        setBackground(BACKGROUND);

        path = new ArrayList<>();

        keyboardPathSnapshot =
                new ArrayList<>();

        keyboardPathFloorDepth =
                0;

        currentCandidates = List.of();
        currentDepth = 0;
        analyzing = false;

        JPanel northPanel = new JPanel();
        northPanel.setOpaque(false);
        northPanel.setLayout(new BoxLayout(northPanel, BoxLayout.Y_AXIS));
        northPanel.setBorder(BorderFactory.createEmptyBorder(12, 18, 8, 18));

        JPanel header = new JPanel(new BorderLayout(14, 0));
        header.setOpaque(false);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);

        backButton = createBackButton();
        header.add(backButton, BorderLayout.WEST);

        JPanel headingText = new JPanel();
        headingText.setOpaque(false);
        headingText.setLayout(new BoxLayout(headingText, BoxLayout.Y_AXIS));

        titleLabel = new JLabel("Current Position");
        titleLabel.setForeground(PRIMARY_TEXT);
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 21));

        statusLabel = new JLabel("Stockfish ready");
        statusLabel.setForeground(SECONDARY_TEXT);
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));

        evaluationLabel = new JLabel("Evaluation  —");
        evaluationLabel.setForeground(PRIMARY_TEXT);
        evaluationLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));

        breadcrumbLabel = new JLabel("Start");
        breadcrumbLabel.setForeground(ACCENT);
        breadcrumbLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));

        headingText.add(titleLabel);
        headingText.add(Box.createVerticalStrut(4));
        headingText.add(statusLabel);
        headingText.add(Box.createVerticalStrut(2));
        headingText.add(evaluationLabel);
        headingText.add(Box.createVerticalStrut(7));
        headingText.add(breadcrumbLabel);

        header.add(headingText, BorderLayout.CENTER);
        northPanel.add(header);
        northPanel.add(Box.createVerticalStrut(8));

        principalVariationMovesPanel =
                new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        principalVariationMovesPanel.setOpaque(false);

        principalVariationPanel = createPrincipalVariationPanel();
        northPanel.add(principalVariationPanel);
        northPanel.add(Box.createVerticalStrut(8));

        add(northPanel, BorderLayout.NORTH);

        cardsPanel = new JPanel();
        cardsPanel.setOpaque(false);
        cardsPanel.setLayout(new BoxLayout(cardsPanel, BoxLayout.Y_AXIS));
        cardsPanel.setBorder(BorderFactory.createEmptyBorder(0, 18, 18, 18));

        scrollPane = new JScrollPane(cardsPanel);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setHorizontalScrollBarPolicy(
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        );
        add(scrollPane, BorderLayout.CENTER);

        blackPerspective = false;
        darkTheme = false;
        updateBackButton();
        setDarkTheme(true);
    }

    public void setBlackPerspective(boolean value) {
        blackPerspective = value;
        rebuildVerticalDisplay();
    }

    public void setSelectionListener(Consumer<Candidate> listener) {
        selectionListener = listener;
    }

    public void setCollapseListener(Runnable listener) {
        collapseListener = listener;
    }

    public void setBackListener(Runnable listener) {
        backListener = listener;
    }

    public void setAnalyzing(Position position, int depth) {
        if (position == null) {
            return;
        }

        if (rootPosition == null) {
            rootPosition = position;
        }

        currentPosition = position;
        currentDepth = depth;
        currentCandidates = List.of();
        analyzing = true;
        titleLabel.setText(path.isEmpty() ? "Current Position" : "Analysis Variation");
        statusLabel.setText("Stockfish analyzing • depth " + depth + "…");

        if (path.isEmpty()) {
            evaluationLabel.setText("Evaluation  —");
        } else {
            PathEntry endpoint = path.get(path.size() - 1);
            evaluationLabel.setText(
                    "Evaluation  " + formatAbsolute(endpoint.evaluation)
            );
        }
        updateBreadcrumb();
        showCalculatingLine();

        if (path.isEmpty()) {
            showLoadingCards();
        } else {
            rebuildVerticalDisplay();
        }

        updateBackButton();
    }

    public void setUnavailable(String message) {
        analyzing = false;
        statusLabel.setText(
                message == null || message.isBlank()
                        ? "Stockfish unavailable."
                        : message
        );
        principalVariationMovesPanel.removeAll();
        cardsPanel.removeAll();
        refreshCards();
    }

    public void setCandidates(
            Position parent,
            int parentEvaluation,
            int depth,
            List<Candidate> candidates
    ) {

        setProgressiveCandidates(
                parent,
                parentEvaluation,
                depth,
                "depth " + depth,
                false,
                candidates
        );
    }


    /**
     * Progressive Stockfish update.
     *
     * Candidate cards remain visible while later passes refine the strongest
     * moves. Each Candidate carries its own depth, so a final list can
     * legitimately contain (for example) #1 at depth 20, #2/#3 at depth 18,
     * #4-#8 at depth 14, and the remaining moves at depth 10.
     */
    public void setProgressiveCandidates(
            Position parent,
            int parentEvaluation,
            int displayDepth,
            String phaseLabel,
            boolean refining,
            List<Candidate> candidates
    ) {

        if (parent == null) {
            return;
        }


        if (rootPosition == null) {
            rootPosition = parent;
        }


        currentPosition =
                parent;

        currentEvaluation =
                parentEvaluation;

        currentDepth =
                Math.max(
                        0,
                        displayDepth
                );

        analyzing =
                refining;

        currentCandidates =
                candidates == null
                        ? List.of()
                        : List.copyOf(
                        candidates
                );


        if (!path.isEmpty()) {

            PathEntry endpoint =
                    path.get(
                            path.size() - 1
                    );

            if (samePosition(
                    endpoint.position,
                    parent
            )) {

                endpoint.evaluation =
                        parentEvaluation;
            }
        }


        titleLabel.setText(
                path.isEmpty()
                        ? "Current Position"
                        : "Analysis Variation"
        );


        String phase =
                phaseLabel == null
                        || phaseLabel.isBlank()
                        ? "progressive analysis"
                        : phaseLabel;


        statusLabel.setText(
                "Stockfish • "
                        + phase
                        + " • "
                        + currentCandidates.size()
                        + " candidates"
                        + (refining
                        ? " • refining…"
                        : "")
        );


        evaluationLabel.setText(
                "Evaluation  "
                        + formatAbsolute(
                        parentEvaluation
                )
        );


        updateBreadcrumb();
        populateSuggestedLine(
                currentCandidates
        );
        rebuildVerticalDisplay();
        updateBackButton();
    }


    /**
     * Called when the user physically plays a move while Stockfish mode is active.
     * The move becomes part of the same visible selected-line stack rather than
     * erasing that stack.
     */
    public void commitManualPosition(
            Position parent,
            Position child,
            String san
    ) {
        if (parent == null || child == null) {
            return;
        }

        if (rootPosition == null) {
            rootPosition = parent;
        }

        if (path.isEmpty() && !samePosition(rootPosition, parent)) {
            rootPosition = parent;
        }

        if (!path.isEmpty()
                && !samePosition(path.get(path.size() - 1).position, parent)) {
            // A real move was made from a different actual root. Start a clean
            // line from that actual position rather than creating a false path.
            path.clear();
            rootPosition = parent;
        }

        path.add(
                new PathEntry(
                        child,
                        san == null || san.isBlank() ? "move" : san,
                        currentEvaluation,
                        currentEvaluation
                )
        );

        clearKeyboardPathNavigation();

        currentPosition = child;
        currentCandidates = List.of();
        analyzing = true;
        titleLabel.setText("Analysis Variation");
        updateBreadcrumb();
        rebuildVerticalDisplay();
        updateBackButton();
    }

    public boolean canGoBack() {
        return !path.isEmpty();
    }

    public Position goBack(Position fallbackRoot) {
        if (path.isEmpty()) {
            return effectiveRoot(fallbackRoot);
        }

        path.remove(path.size() - 1);
        Position target = path.isEmpty()
                ? effectiveRoot(fallbackRoot)
                : path.get(path.size() - 1).position;

        currentPosition = target;
        titleLabel.setText(path.isEmpty() ? "Current Position" : "Analysis Variation");
        updateBreadcrumb();
        updateBackButton();
        return target;
    }

    public void clearPath() {
        path.clear();
        clearKeyboardPathNavigation();
        rootPosition = currentPosition;
        titleLabel.setText("Current Position");
        updateBreadcrumb();
        updateBackButton();
    }

    public void clearPath(Position root) {
        path.clear();
        clearKeyboardPathNavigation();
        rootPosition = root;
        currentPosition = root;
        titleLabel.setText("Current Position");
        updateBreadcrumb();
        updateBackButton();
    }

    public Position getCurrentPosition(Position fallbackRoot) {
        return path.isEmpty()
                ? effectiveRoot(fallbackRoot)
                : path.get(path.size() - 1).position;
    }

    public Position getRootPosition(Position fallbackRoot) {
        return effectiveRoot(fallbackRoot);
    }

    public List<Position> getSelectedPathPositions() {
        List<Position> result = new ArrayList<>();
        for (PathEntry entry : path) {
            result.add(entry.position);
        }
        return Collections.unmodifiableList(result);
    }


    // =========================================================
    // Arrow-key selected-line navigation
    // =========================================================

    public void armKeyboardPathNavigation(
            int floorDepth
    ) {

        keyboardPathSnapshot.clear();
        keyboardPathSnapshot.addAll(
                path
        );

        keyboardPathFloorDepth =
                Math.max(
                        0,
                        Math.min(
                                floorDepth,
                                keyboardPathSnapshot.size()
                        )
                );
    }


    public void clearKeyboardPathNavigation() {

        keyboardPathSnapshot.clear();
        keyboardPathFloorDepth =
                0;
    }


    public boolean hasKeyboardPathNavigation() {

        return !keyboardPathSnapshot.isEmpty();
    }


    public Position keyboardPathPrevious(
            Position fallbackRoot
    ) {

        if (path.size()
                <= keyboardPathFloorDepth) {

            return null;
        }

        path.remove(
                path.size() - 1
        );

        return finishKeyboardPathNavigation(
                fallbackRoot
        );
    }


    public Position keyboardPathNext(
            Position fallbackRoot
    ) {

        if (keyboardPathSnapshot.isEmpty()
                ||
                path.size()
                        >= keyboardPathSnapshot.size()) {

            return null;
        }

        path.add(
                keyboardPathSnapshot.get(
                        path.size()
                )
        );

        return finishKeyboardPathNavigation(
                fallbackRoot
        );
    }


    /**
     * Down = reset the preview suffix to the committed-prefix floor.
     */
    public Position keyboardPathReset(
            Position fallbackRoot
    ) {

        if (path.size()
                <= keyboardPathFloorDepth) {

            return null;
        }

        while (path.size()
                > keyboardPathFloorDepth) {

            path.remove(
                    path.size() - 1
            );
        }

        return finishKeyboardPathNavigation(
                fallbackRoot
        );
    }


    /**
     * Up = re-setup / restore the deepest Stockfish line the user selected.
     */
    public Position keyboardPathRestore(
            Position fallbackRoot
    ) {

        if (keyboardPathSnapshot.isEmpty()
                ||
                path.size()
                        >= keyboardPathSnapshot.size()) {

            return null;
        }

        while (path.size()
                < keyboardPathSnapshot.size()) {

            path.add(
                    keyboardPathSnapshot.get(
                            path.size()
                    )
            );
        }

        return finishKeyboardPathNavigation(
                fallbackRoot
        );
    }


    /**
     * Synchronize the visible Stockfish path with a committed history
     * position after manual undo/reset. This does not perform a search; the
     * caller requests fresh/cached Stockfish candidates for the returned
     * endpoint immediately afterward.
     */
    public void rewindToCommittedPosition(
            Position target,
            Position fallbackRoot
    ) {

        if (target == null) {
            return;
        }


        Position effectiveRoot =
                effectiveRoot(
                        fallbackRoot
                );


        if (effectiveRoot != null
                &&
                samePosition(
                        target,
                        effectiveRoot
                )) {

            path.clear();

        } else {

            int matchIndex =
                    -1;

            for (int index = path.size() - 1;
                 index >= 0;
                 index--) {

                if (samePosition(
                        path.get(index).position,
                        target
                )) {

                    matchIndex =
                            index;

                    break;
                }
            }


            if (matchIndex >= 0) {

                while (path.size()
                        > matchIndex + 1) {

                    path.remove(
                            path.size() - 1
                    );
                }

            } else {

                /*
                 * Defensive fallback for an externally supplied/manual root
                 * not represented in the current Stockfish path.
                 */
                path.clear();

                rootPosition =
                        target;
            }
        }


        currentPosition =
                target;

        currentCandidates =
                List.of();

        analyzing =
                true;

        clearKeyboardPathNavigation();

        titleLabel.setText(
                path.isEmpty()
                        ? "Current Position"
                        : "Analysis Variation"
        );

        updateBreadcrumb();
        rebuildVerticalDisplay();
        updateBackButton();
    }


    private Position finishKeyboardPathNavigation(
            Position fallbackRoot
    ) {

        Position target =
                path.isEmpty()
                        ? effectiveRoot(
                        fallbackRoot
                )
                        : path.get(
                        path.size() - 1
                ).position;


        currentPosition =
                target;

        currentCandidates =
                List.of();

        analyzing =
                true;

        titleLabel.setText(
                path.isEmpty()
                        ? "Current Position"
                        : "Analysis Variation"
        );

        updateBreadcrumb();
        rebuildVerticalDisplay();
        updateBackButton();

        return target;
    }


    public void setDarkTheme(boolean dark) {
        darkTheme = dark;

        BACKGROUND = dark ? DARK_BACKGROUND : LIGHT_BACKGROUND;
        CARD_BACKGROUND = dark ? DARK_CARD_BACKGROUND : LIGHT_CARD_BACKGROUND;
        CARD_HOVER = dark ? DARK_CARD_HOVER : LIGHT_CARD_HOVER;
        CARD_SELECTED = dark ? DARK_CARD_SELECTED : LIGHT_CARD_SELECTED;
        BORDER_COLOR = dark ? DARK_BORDER_COLOR : LIGHT_BORDER_COLOR;
        PRIMARY_TEXT = dark ? DARK_PRIMARY_TEXT : LIGHT_PRIMARY_TEXT;
        SECONDARY_TEXT = dark ? DARK_SECONDARY_TEXT : LIGHT_SECONDARY_TEXT;
        ACCENT = dark ? DARK_ACCENT : LIGHT_ACCENT;

        setBackground(BACKGROUND);
        titleLabel.setForeground(PRIMARY_TEXT);
        statusLabel.setForeground(SECONDARY_TEXT);
        evaluationLabel.setForeground(PRIMARY_TEXT);
        breadcrumbLabel.setForeground(ACCENT);
        styleBackButton();

        rebuildVerticalDisplay();
        repaint();
    }

    private JButton createBackButton() {
        JButton button = new JButton("Back");
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        button.setFocusable(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addActionListener(event -> {
            if (backListener != null) {
                backListener.run();
            }
        });
        return button;
    }

    private JPanel createPrincipalVariationPanel() {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setOpaque(false);
        outer.setAlignmentX(Component.LEFT_ALIGNMENT);
        outer.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1, true));

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        JLabel heading = new JLabel("SUGGESTED LINE");
        heading.setForeground(SECONDARY_TEXT);
        heading.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);

        principalVariationMovesPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        content.add(heading);
        content.add(Box.createVerticalStrut(7));
        content.add(principalVariationMovesPanel);
        outer.add(content, BorderLayout.CENTER);
        return outer;
    }

    private void populateSuggestedLine(List<Candidate> candidates) {
        principalVariationMovesPanel.removeAll();

        if (candidates == null || candidates.isEmpty()) {
            JLabel empty = new JLabel("No line available");
            empty.setForeground(SECONDARY_TEXT);
            principalVariationMovesPanel.add(empty);
        } else {
            List<String> pv = candidates.get(0).pv();
            if (pv == null || pv.isEmpty()) {
                JLabel empty = new JLabel("No line available");
                empty.setForeground(SECONDARY_TEXT);
                principalVariationMovesPanel.add(empty);
            } else {
                int limit = Math.min(8, pv.size());
                for (int index = 0; index < limit; index++) {
                    JLabel move = new JLabel(pv.get(index));
                    move.setForeground(ACCENT);
                    move.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));
                    principalVariationMovesPanel.add(move);

                    if (index < limit - 1) {
                        JLabel divider = new JLabel("  ");
                        principalVariationMovesPanel.add(divider);
                    }
                }
            }
        }

        principalVariationMovesPanel.revalidate();
        principalVariationMovesPanel.repaint();
    }

    private void showCalculatingLine() {
        principalVariationMovesPanel.removeAll();
        JLabel calculating = new JLabel("Calculating line…");
        calculating.setForeground(SECONDARY_TEXT);
        principalVariationMovesPanel.add(calculating);
        principalVariationMovesPanel.revalidate();
        principalVariationMovesPanel.repaint();
    }

    private void showLoadingCards() {
        cardsPanel.removeAll();
        JLabel loading = new JLabel("Analyzing candidate moves…");
        loading.setForeground(SECONDARY_TEXT);
        loading.setBorder(BorderFactory.createEmptyBorder(16, 8, 8, 8));
        cardsPanel.add(loading);
        refreshCards();
    }

    private void rebuildVerticalDisplay() {
        if (cardsPanel == null) {
            return;
        }

        cardsPanel.removeAll();
        principalVariationPanel.setBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1, true)
        );

        if (path.isEmpty()) {
            addSectionHeading("STOCKFISH CANDIDATE MOVES", 0);
            int rank = 1;
            for (Candidate candidate : currentCandidates) {
                addCard(
                        createCandidateCard(candidate, rank, currentEvaluation, rank == 1),
                        0
                );
                rank++;
            }
            if (currentCandidates.isEmpty() && analyzing) {
                JPanel loadingWrapper =
                        new JPanel(
                                new BorderLayout()
                        );
                loadingWrapper.setOpaque(false);
                loadingWrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
                loadingWrapper.setMaximumSize(
                        new Dimension(
                                Integer.MAX_VALUE,
                                38
                        )
                );
                loadingWrapper.setBorder(
                        BorderFactory.createEmptyBorder(
                                8,
                                2,
                                8,
                                0
                        )
                );

                JLabel loading =
                        new JLabel(
                                "Analyzing candidate moves…"
                        );
                loading.setForeground(SECONDARY_TEXT);

                loadingWrapper.add(
                        loading,
                        BorderLayout.WEST
                );

                cardsPanel.add(
                        loadingWrapper
                );
            }

            cardsPanel.add(Box.createVerticalGlue());
            refreshCards();
            return;
        }

        addSectionHeading("SELECTED LINE", 0);

        for (int depth = 0; depth < path.size(); depth++) {
            PathEntry entry = path.get(depth);
            int parentEvaluation = depth == 0
                    ? entry.parentEvaluation
                    : path.get(depth - 1).evaluation;

            AnalysisCard selectedCard = new AnalysisCard(
                    entry.position,
                    createSelectedMoveText(depth, entry.san),
                    entry.evaluation,
                    parentEvaluation,
                    currentDepth,
                    depth == 0,
                    true
            );

            final int collapseDepth = depth;
            selectedCard.setClickAction(() -> collapseFrom(collapseDepth));

            int indent = depth == 0 ? 0 : BRANCH_INDENT;
            addCard(selectedCard, indent);

            if (depth < path.size() - 1) {
                addConnector(BRANCH_INDENT);
            }
        }

        if (!currentCandidates.isEmpty()) {
            addConnector(BRANCH_INDENT);
            addSectionHeading(
                    "CONTINUATIONS AFTER " + path.get(path.size() - 1).san,
                    BRANCH_INDENT
            );

            int rank = 1;
            for (Candidate candidate : currentCandidates) {
                addCard(
                        createCandidateCard(candidate, rank, currentEvaluation, rank == 1),
                        BRANCH_INDENT
                );
                rank++;
            }

        } else if (analyzing) {

            /*
             * Keep the loading state in the same hierarchy as the completed
             * state.  This prevents the selected card from appearing detached
             * from the continuation area while Stockfish is calculating.
             */
            addConnector(BRANCH_INDENT);

            addSectionHeading(
                    "CONTINUATIONS AFTER " + path.get(path.size() - 1).san,
                    BRANCH_INDENT
            );

            JPanel loadingWrapper =
                    new JPanel(
                            new BorderLayout()
                    );

            loadingWrapper.setOpaque(false);
            loadingWrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
            loadingWrapper.setMaximumSize(
                    new Dimension(
                            Integer.MAX_VALUE,
                            34
                    )
            );
            loadingWrapper.setBorder(
                    BorderFactory.createEmptyBorder(
                            2,
                            BRANCH_INDENT + 2,
                            8,
                            0
                    )
            );

            JLabel loading =
                    new JLabel(
                            "Analyzing candidate moves…"
                    );

            loading.setForeground(SECONDARY_TEXT);
            loading.setFont(
                    new Font(
                            Font.SANS_SERIF,
                            Font.PLAIN,
                            12
                    )
            );

            loadingWrapper.add(
                    loading,
                    BorderLayout.WEST
            );

            cardsPanel.add(
                    loadingWrapper
            );
        }

        cardsPanel.add(
                Box.createVerticalGlue()
        );

        refreshCards();
    }

    private AnalysisCard createCandidateCard(
            Candidate candidate,
            int rank,
            int parentEvaluation,
            boolean best
    ) {
        AnalysisCard card = new AnalysisCard(
                candidate.position(),
                "#" + rank + "  " + candidate.san(),
                candidate.evaluation(),
                parentEvaluation,
                candidate.depth(),
                best,
                false
        );

        card.setClickAction(() -> {
            path.add(
                    new PathEntry(
                            candidate.position(),
                            candidate.san(),
                            candidate.evaluation(),
                            parentEvaluation
                    )
            );
            currentPosition = candidate.position();
            currentCandidates = List.of();
            titleLabel.setText("Analysis Variation");
            updateBreadcrumb();
            rebuildVerticalDisplay();
            updateBackButton();

            if (selectionListener != null) {
                selectionListener.accept(candidate);
            }
        });

        return card;
    }

    private void collapseFrom(int depth) {
        if (depth < 0 || depth >= path.size()) {
            return;
        }

        while (path.size() > depth) {
            path.remove(path.size() - 1);
        }

        currentPosition = path.isEmpty()
                ? rootPosition
                : path.get(path.size() - 1).position;

        titleLabel.setText(path.isEmpty() ? "Current Position" : "Analysis Variation");
        updateBreadcrumb();
        updateBackButton();

        if (collapseListener != null) {
            collapseListener.run();
        }
    }

    private void addSectionHeading(String text, int indent) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.setMaximumSize(
                new Dimension(
                        Integer.MAX_VALUE,
                        30
                )
        );
        wrapper.setBorder(BorderFactory.createEmptyBorder(4, indent + 2, 8, 0));

        JLabel label = new JLabel(text);
        label.setForeground(SECONDARY_TEXT);
        label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));

        wrapper.add(label, BorderLayout.WEST);
        cardsPanel.add(wrapper);
    }

    private void addConnector(int indent) {
        JPanel connector = new JPanel(
                new FlowLayout(FlowLayout.LEFT, indent + 14, 0)
        );
        connector.setOpaque(false);
        connector.setAlignmentX(Component.LEFT_ALIGNMENT);
        connector.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        JLabel arrow = new JLabel("↓");
        arrow.setForeground(ACCENT);
        arrow.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        connector.add(arrow);
        cardsPanel.add(connector);
    }

    private void addCard(AnalysisCard card, int indent) {
        if (indent <= 0) {
            cardsPanel.add(card);
        } else {
            JPanel wrapper = new JPanel(new BorderLayout());
            wrapper.setOpaque(false);
            wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
            wrapper.setBorder(BorderFactory.createEmptyBorder(0, indent, 0, 0));
            wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 198));
            wrapper.add(card, BorderLayout.CENTER);
            cardsPanel.add(wrapper);
        }

        cardsPanel.add(Box.createVerticalStrut(8));
    }

    private void updateBreadcrumb() {
        StringBuilder builder = new StringBuilder("Start");
        for (PathEntry entry : path) {
            builder.append("  ›  ").append(entry.san);
        }
        breadcrumbLabel.setText(builder.toString());
    }

    private String createSelectedMoveText(int depth, String san) {
        boolean rootWhite = rootPosition == null
                || rootPosition.getSideToMove() == main.java.chess.model.Color.WHITE;

        boolean whiteMove = rootWhite
                ? depth % 2 == 0
                : depth % 2 != 0;

        int moveNumber = depth / 2 + 1;
        return whiteMove
                ? moveNumber + ". " + san
                : moveNumber + "... " + san;
    }

    private void updateBackButton() {
        backButton.setEnabled(!path.isEmpty());
    }

    private void styleBackButton() {
        java.awt.Color background = darkTheme
                ? new java.awt.Color(19, 27, 35)
                : new java.awt.Color(244, 246, 249);
        java.awt.Color border = darkTheme
                ? new java.awt.Color(45, 56, 68)
                : new java.awt.Color(196, 202, 210);

        backButton.setForeground(
                darkTheme ? PRIMARY_TEXT : new java.awt.Color(55, 61, 70)
        );
        backButton.setBackground(background);
        backButton.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(border, 1, true),
                        BorderFactory.createEmptyBorder(6, 12, 6, 12)
                )
        );
    }

    private void refreshCards() {
        cardsPanel.revalidate();
        cardsPanel.repaint();
    }

    private Position effectiveRoot(Position fallbackRoot) {
        return rootPosition != null ? rootPosition : fallbackRoot;
    }

    private String formatAbsolute(int whiteValue) {
        if (whiteValue == 0) {
            return "Equal";
        }
        return whiteValue > 0
                ? String.format("White advantage %.2f", whiteValue / 100.0)
                : String.format("Black advantage %.2f", -whiteValue / 100.0);
    }

    private String formatMoveImpact(int whiteDelta) {
        int shownDelta = blackPerspective ? -whiteDelta : whiteDelta;

        if (shownDelta == 0) {
            return "0.00 No change";
        }

        String side = blackPerspective ? "Black" : "White";
        String meaning = shownDelta > 0 ? "advantage" : "disadvantage";

        return String.format(
                "%+.2f %s %s",
                shownDelta / 100.0,
                side,
                meaning
        );
    }

    private boolean samePosition(Position left, Position right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.createPositionKey().equals(right.createPositionKey());
    }

    private Border createCardBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
                BorderFactory.createEmptyBorder(0, 0, 0, 0)
        );
    }

    private final class AnalysisCard extends JPanel {
        private final JPanel cardSurface;
        private Runnable clickAction;

        private AnalysisCard(
                Position position,
                String moveText,
                int evaluation,
                int parentEvaluation,
                int detailDepth,
                boolean best,
                boolean selected
        ) {
            setOpaque(false);
            setLayout(new BorderLayout());
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setMaximumSize(
                    new Dimension(
                            Integer.MAX_VALUE,
                            198
                    )
            );
            setPreferredSize(
                    new Dimension(
                            450,
                            190
                    )
            );

            cardSurface = new JPanel(new BorderLayout(14, 0));
            cardSurface.setBackground(selected ? CARD_SELECTED : CARD_BACKGROUND);
            cardSurface.setBorder(createCardBorder());
            cardSurface.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            cardSurface.setMaximumSize(new Dimension(Integer.MAX_VALUE, 198));
            cardSurface.setPreferredSize(new Dimension(450, 190));

            MiniChessBoardPanel miniBoard = new MiniChessBoardPanel(position);

            JPanel boardWrapper = new JPanel(new GridBagLayout());
            boardWrapper.setOpaque(false);
            boardWrapper.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 0));
            boardWrapper.add(miniBoard);
            cardSurface.add(boardWrapper, BorderLayout.WEST);

            JPanel information = new JPanel();
            information.setOpaque(false);
            information.setLayout(new BoxLayout(information, BoxLayout.Y_AXIS));
            information.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 12));

            if (best) {
                JLabel bestLabel = new JLabel("BEST LINE");
                bestLabel.setForeground(ACCENT);
                bestLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
                bestLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                information.add(bestLabel);
                information.add(Box.createVerticalStrut(2));
            }

            JLabel moveLabel = new JLabel(moveText);
            moveLabel.setForeground(PRIMARY_TEXT);
            moveLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
            moveLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            information.add(moveLabel);
            information.add(Box.createVerticalStrut(4));

            JLabel impactLabel = new JLabel(
                    "Move impact  "
                            + formatMoveImpact(evaluation - parentEvaluation)
            );
            impactLabel.setForeground(PRIMARY_TEXT);
            impactLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 16));
            impactLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            information.add(impactLabel);
            information.add(Box.createVerticalStrut(3));

            JLabel detailLabel = new JLabel(
                    "Stockfish  ·  Evaluation: "
                            + formatAbsolute(evaluation)
                            + "  ·  Depth "
                            + Math.max(
                            0,
                            detailDepth
                    )
            );
            detailLabel.setForeground(SECONDARY_TEXT);
            detailLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            detailLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            information.add(detailLabel);

            information.add(Box.createVerticalGlue());

            JLabel bottomLabel =
                    new JLabel(
                            selected
                                    ? "Selected line • click to collapse here"
                                    : "Inspect continuations  ›"
                    );

            bottomLabel.setForeground(ACCENT);
            bottomLabel.setFont(
                    new Font(
                            Font.SANS_SERIF,
                            Font.BOLD,
                            11
                    )
            );
            bottomLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            information.add(bottomLabel);

            cardSurface.add(information, BorderLayout.CENTER);
            add(cardSurface, BorderLayout.CENTER);

            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent event) {
                    if (!selected) {
                        cardSurface.setBackground(CARD_HOVER);
                    }
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    cardSurface.setBackground(
                            selected ? CARD_SELECTED : CARD_BACKGROUND
                    );
                }

                @Override
                public void mousePressed(MouseEvent event) {
                    /*
                     * Candidate results can be replaced by a background
                     * Stockfish refresh before mouseReleased arrives. Select
                     * on the left press so the click cannot disappear with the
                     * component that received the press.
                     */
                    if (SwingUtilities.isLeftMouseButton(event)
                            && clickAction != null) {
                        clickAction.run();
                    }
                }
            };

            addMouseListenerRecursively(
                    cardSurface,
                    mouse
            );
        }

        private void addMouseListenerRecursively(
                Component component,
                MouseAdapter adapter
        ) {

            if (component instanceof javax.swing.AbstractButton) {
                return;
            }

            component.addMouseListener(
                    adapter
            );

            if (component instanceof java.awt.Container container) {

                for (Component child :
                        container.getComponents()) {

                    addMouseListenerRecursively(
                            child,
                            adapter
                    );
                }
            }
        }

        private void setClickAction(Runnable action) {
            clickAction = action;
        }
    }

    private static final class PathEntry {
        private final Position position;
        private final String san;
        private int evaluation;
        private final int parentEvaluation;

        private PathEntry(
                Position position,
                String san,
                int evaluation,
                int parentEvaluation
        ) {
            this.position = position;
            this.san = san;
            this.evaluation = evaluation;
            this.parentEvaluation = parentEvaluation;
        }
    }
}

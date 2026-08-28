package main.java.chess.gui;

import main.java.chess.analysis.MoveAnalysis;
import main.java.chess.analysis.PositionAnalysis;
import main.java.chess.analysis.VariationNode;
import main.java.chess.model.Position;
import main.java.chess.search.SearchOutcome;

import javax.swing.*;
import javax.swing.border.Border;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;


public class AnalysisPanel extends JPanel {

    // =========================================================
    // Appearance
    // =========================================================

    private static java.awt.Color BACKGROUND =
            new java.awt.Color(
                    242,
                    244,
                    247
            );


    private static java.awt.Color CARD_BACKGROUND =
            new java.awt.Color(
                    255,
                    255,
                    255
            );


    private static java.awt.Color CARD_HOVER =
            new java.awt.Color(
                    248,
                    250,
                    252
            );


    private static java.awt.Color CARD_SELECTED =
            new java.awt.Color(
                    238,
                    244,
                    252
            );


    private static java.awt.Color BORDER_COLOR =
            new java.awt.Color(
                    215,
                    220,
                    227
            );


    private static java.awt.Color PRIMARY_TEXT =
            new java.awt.Color(
                    31,
                    35,
                    41
            );


    private static java.awt.Color SECONDARY_TEXT =
            new java.awt.Color(
                    100,
                    107,
                    117
            );


    private static java.awt.Color ACCENT =
            new java.awt.Color(
                    64,
                    100,
                    145
            );


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


    private boolean darkTheme;


    private static final int BRANCH_INDENT =
            24;


    // =========================================================
    // Header
    // =========================================================

    private final JLabel titleLabel;

    private final JLabel outcomeLabel;

    private final JLabel evaluationLabel;

    private final JLabel breadcrumbLabel;

    private final JButton backButton;



    // =========================================================
    // Principal variation
    // =========================================================

    private final JPanel principalVariationPanel;

    private final JPanel principalVariationMovesPanel;


    // =========================================================
    // Cards
    // =========================================================

    private final JPanel cardsPanel;

    private final JScrollPane scrollPane;


    // =========================================================
    // Analysis state
    // =========================================================

    private PositionAnalysis currentAnalysis;

    private final List<PathEntry> path;


    // =========================================================
    // Callbacks
    // =========================================================

    private Consumer<MoveAnalysis>
            moveSelectionListener;


    private Consumer<VariationNode>
            variationSelectionListener;


    private Consumer<Position>
            explorePositionListener;

    private Function<Position, List<VariationNode>> continuationProvider;


    private Runnable backListener;


    // =========================================================
    // Constructor
    // =========================================================

    public AnalysisPanel() {

        setLayout(
                new BorderLayout()
        );


        setPreferredSize(
                new Dimension(
                        510,
                        690
                )
        );


        setBackground(
                BACKGROUND
        );


        path =
                new ArrayList<>();


        JPanel northPanel =
                new JPanel();


        northPanel.setOpaque(
                false
        );


        northPanel.setLayout(
                new BoxLayout(
                        northPanel,
                        BoxLayout.Y_AXIS
                )
        );


        northPanel.setBorder(
                BorderFactory.createEmptyBorder(
                        18,
                        18,
                        12,
                        18
                )
        );


        // =====================================================
        // Header
        // =====================================================

        JPanel header =
                new JPanel(
                        new BorderLayout(
                                14,
                                0
                        )
                );


        header.setOpaque(
                false
        );


        header.setAlignmentX(
                Component.LEFT_ALIGNMENT
        );


        backButton =
                createBackButton();


        header.add(
                backButton,
                BorderLayout.WEST
        );


        JPanel headingText =
                new JPanel();


        headingText.setOpaque(
                false
        );


        headingText.setLayout(
                new BoxLayout(
                        headingText,
                        BoxLayout.Y_AXIS
                )
        );


        titleLabel =
                new JLabel(
                        "Current Position"
                );


        titleLabel.setForeground(
                PRIMARY_TEXT
        );


        titleLabel.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.BOLD,
                        21
                )
        );


        outcomeLabel =
                new JLabel(
                        "Outcome  —"
                );


        outcomeLabel.setForeground(
                SECONDARY_TEXT
        );


        outcomeLabel.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.PLAIN,
                        13
                )
        );


        evaluationLabel =
                new JLabel(
                        "Evaluation  —"
                );


        evaluationLabel.setForeground(
                PRIMARY_TEXT
        );


        evaluationLabel.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.BOLD,
                        14
                )
        );


        breadcrumbLabel =
                new JLabel(
                        "Start"
                );


        breadcrumbLabel.setForeground(
                ACCENT
        );


        breadcrumbLabel.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.PLAIN,
                        12
                )
        );


        headingText.add(
                titleLabel
        );


        headingText.add(
                Box.createVerticalStrut(
                        4
                )
        );


        headingText.add(
                outcomeLabel
        );


        headingText.add(
                Box.createVerticalStrut(
                        2
                )
        );


        headingText.add(
                evaluationLabel
        );


        headingText.add(
                Box.createVerticalStrut(
                        7
                )
        );


        headingText.add(
                breadcrumbLabel
        );


        header.add(
                headingText,
                BorderLayout.CENTER
        );


        northPanel.add(
                header
        );


        northPanel.add(
                Box.createVerticalStrut(
                        14
                )
        );


        // =====================================================
        // Principal variation
        // =====================================================

        principalVariationMovesPanel =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.LEFT,
                                4,
                                0
                        )
                );


        principalVariationMovesPanel.setOpaque(
                false
        );


        principalVariationPanel =
                createPrincipalVariationPanel();


        northPanel.add(
                principalVariationPanel
        );


        add(
                northPanel,
                BorderLayout.NORTH
        );


        // =====================================================
        // Cards
        // =====================================================

        cardsPanel =
                new JPanel();


        cardsPanel.setOpaque(
                false
        );


        cardsPanel.setLayout(
                new BoxLayout(
                        cardsPanel,
                        BoxLayout.Y_AXIS
                )
        );


        cardsPanel.setBorder(
                BorderFactory.createEmptyBorder(
                        2,
                        18,
                        18,
                        18
                )
        );


        scrollPane =
                new JScrollPane(
                        cardsPanel
                );


        scrollPane.setBorder(
                BorderFactory.createEmptyBorder()
        );


        scrollPane.setOpaque(
                false
        );


        scrollPane
                .getViewport()
                .setOpaque(
                        false
                );


        scrollPane
                .getVerticalScrollBar()
                .setUnitIncrement(
                        18
                );


        scrollPane.setHorizontalScrollBarPolicy(
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        );


        add(
                scrollPane,
                BorderLayout.CENTER
        );
    }


    // =========================================================
    // Back button
    // =========================================================

    private JButton createBackButton() {

        JButton button =
                new JButton(
                        "←  Back"
                ) {

                    @Override
                    protected void paintComponent(
                            Graphics graphics
                    ) {

                        Graphics2D g2 =
                                (Graphics2D) graphics.create();


                        g2.setRenderingHint(
                                RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_ON
                        );


                        boolean pressed =
                                getModel().isPressed()
                                        &&
                                        getModel().isArmed();


                        java.awt.Color fill;


                        if (darkTheme) {

                            fill =
                                    pressed
                                            ? new java.awt.Color(
                                            72,
                                            78,
                                            86
                                    )
                                            : new java.awt.Color(
                                            19,
                                            27,
                                            35
                                    );

                        } else {

                            fill =
                                    pressed
                                            ? new java.awt.Color(
                                            174,
                                            199,
                                            226
                                    )
                                            : new java.awt.Color(
                                            244,
                                            246,
                                            249
                                    );
                        }


                        g2.setColor(
                                fill
                        );


                        g2.fillRoundRect(
                                0,
                                0,
                                getWidth(),
                                getHeight(),
                                10,
                                10
                        );


                        g2.dispose();


                        super.paintComponent(
                                graphics
                        );
                    }
                };


        button.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.BOLD,
                        13
                )
        );


        button.setFocusPainted(
                false
        );


        button.setFocusable(
                false
        );


        button.setRolloverEnabled(
                false
        );


        button.setBorderPainted(
                true
        );


        button.setContentAreaFilled(
                false
        );


        button.setOpaque(
                false
        );


        button.setCursor(
                Cursor.getPredefinedCursor(
                        Cursor.HAND_CURSOR
                )
        );


        button.setPreferredSize(
                new Dimension(
                        94,
                        46
                )
        );


        button.setMinimumSize(
                new Dimension(
                        94,
                        46
                )
        );


        button.setMargin(
                new Insets(
                        4,
                        10,
                        4,
                        10
                )
        );


        button.setEnabled(
                false
        );



        button.addActionListener(
                event -> {

                    if (backListener != null) {

                        backListener.run();
                    }
                }
        );


        return button;
    }


    // =========================================================
    // Principal variation
    // =========================================================

    private JPanel createPrincipalVariationPanel() {

        JPanel outer =
                new JPanel(
                        new BorderLayout()
                );


        outer.setBackground(
                CARD_BACKGROUND
        );


        outer.setAlignmentX(
                Component.LEFT_ALIGNMENT
        );


        outer.setBorder(
                createCardBorder()
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
                        10,
                        12,
                        10,
                        12
                )
        );


        JLabel heading =
                new JLabel(
                        "PRINCIPAL VARIATION"
                );


        heading.setForeground(
                SECONDARY_TEXT
        );


        heading.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.BOLD,
                        11
                )
        );


        heading.setAlignmentX(
                Component.LEFT_ALIGNMENT
        );


        principalVariationMovesPanel.setAlignmentX(
                Component.LEFT_ALIGNMENT
        );


        content.add(
                heading
        );


        content.add(
                Box.createVerticalStrut(
                        7
                )
        );


        content.add(
                principalVariationMovesPanel
        );


        outer.add(
                content,
                BorderLayout.CENTER
        );


        return outer;
    }


    // =========================================================
    // Set analysis
    // =========================================================

    public void setAnalysis(
            PositionAnalysis analysis,
            boolean canGoBack
    ) {

        currentAnalysis =
                analysis;


        path.clear();


        populatePrincipalVariation(
                analysis
        );


        updateBreadcrumb();


        restoreHeaderForCurrentPath();


        backButton.setEnabled(
                canGoBack
        );


        rebuildVerticalDisplay();
    }


    // =========================================================
    // Restore path after refreshed analysis
    // =========================================================

    public void restorePath(
            PositionAnalysis analysis,
            List<String> sanPath,
            boolean manualBackAvailable
    ) {

        currentAnalysis =
                analysis;


        path.clear();


        populatePrincipalVariation(
                analysis
        );


        if (sanPath != null
                &&
                !sanPath.isEmpty()) {

            MoveAnalysis rootMove =
                    findRootMoveBySan(
                            sanPath.get(0)
                    );


            if (rootMove != null) {

                PathEntry rootEntry =
                        PathEntry.forMove(
                                rootMove
                        );


                path.add(
                        rootEntry
                );


                PathEntry current =
                        rootEntry;


                for (int index = 1;
                     index < sanPath.size();
                     index++) {

                    String san =
                            sanPath.get(
                                    index
                            );


                    VariationNode child =
                            findVariationBySan(
                                    getChildren(
                                            current
                                    ),
                                    san
                            );


                    if (child == null) {

                        break;
                    }


                    PathEntry childEntry =
                            PathEntry.forVariation(
                                    child
                            );


                    path.add(
                            childEntry
                    );


                    current =
                            childEntry;
                }
            }
        }


        updateBreadcrumb();


        restoreHeaderForCurrentPath();


        backButton.setEnabled(
                !path.isEmpty()
                        ||
                        manualBackAvailable
        );


        rebuildVerticalDisplay();
    }


    private MoveAnalysis findRootMoveBySan(
            String san
    ) {

        if (currentAnalysis == null
                ||
                san == null) {

            return null;
        }


        for (MoveAnalysis move :
                currentAnalysis.getMoves()) {

            if (san.equals(
                    move.getSan()
            )) {

                return move;
            }
        }


        return null;
    }


    private VariationNode findVariationBySan(
            List<VariationNode> variations,
            String san
    ) {

        if (variations == null
                ||
                san == null) {

            return null;
        }


        for (VariationNode variation :
                variations) {

            if (san.equals(
                    variation.getSan()
            )) {

                return variation;
            }
        }


        return null;
    }


    // =========================================================
    // Rebuild vertical display
    // =========================================================

    private void rebuildVerticalDisplay() {

        cardsPanel.removeAll();


        if (currentAnalysis == null) {

            refreshCards();

            return;
        }


        // =====================================================
        // Root candidates
        // =====================================================

        if (path.isEmpty()) {

            addSectionHeading(
                    "CANDIDATE MOVES",
                    0
            );


            int rank =
                    1;


            for (MoveAnalysis move :
                    currentAnalysis.getMoves()) {

                int moveRank =
                        rank;


                AnalysisCard card =
                        new AnalysisCard(
                                move.getPosition(),

                                moveRank
                                        + ". "
                                        + move.getSan(),

                                move.getOutcome(),

                                move.getMateDistance(),

                                move.getMateDisplay(),

                                move.getEvaluation(),

                                move.getSearchValue(),

                                move.getGeneratedPositions(),

                                move.getExploredPositions(),

                                move.getSolvedPositions(),

                                moveRank == 1,

                                move.hasVariations(),

                                false
                        );


                card.setClickAction(
                        () -> selectRootMove(
                                move
                        )
                );


                addCard(
                        card,
                        0
                );


                rank++;
            }


            refreshCards();

            return;
        }


        // =====================================================
        // Selected path
        // =====================================================

        /*
         * Materialize only the endpoint's immediate continuations.
         *
         * This is the key to unlimited-depth analysis navigation:
         * no recursive GUI tree is built, but whenever the user lands
         * on a position we ask the persistent graph for exactly one
         * more generated level.
         */
        if (!path.isEmpty()) {

            ensureChildrenLoaded(
                    path.get(
                            path.size() - 1
                    )
            );
        }


        addSectionHeading(
                "SELECTED LINE",
                0
        );


        for (int depth = 0;
             depth < path.size();
             depth++) {

            PathEntry entry =
                    path.get(
                            depth
                    );


            boolean endpoint =
                    depth
                            == path.size() - 1;


            AnalysisCard selectedCard;


            if (entry.moveAnalysis != null) {

                MoveAnalysis move =
                        entry.moveAnalysis;


                selectedCard =
                        new AnalysisCard(
                                move.getPosition(),

                                createSelectedMoveText(
                                        depth,
                                        move.getSan()
                                ),

                                move.getOutcome(),

                                move.getMateDistance(),

                                move.getMateDisplay(),

                                move.getEvaluation(),

                                move.getSearchValue(),

                                move.getGeneratedPositions(),

                                move.getExploredPositions(),

                                move.getSolvedPositions(),

                                depth == 0,

                                move.hasVariations(),

                                endpoint
                        );


            } else {

                VariationNode variation =
                        entry.variationNode;


                selectedCard =
                        new AnalysisCard(
                                variation.getPosition(),

                                createSelectedMoveText(
                                        depth,
                                        variation.getSan()
                                ),

                                variation.getOutcome(),

                                variation.getMateDistance(),

                                createMateDisplay(
                                        variation
                                ),

                                variation.getStaticEvaluation(),

                                variation.getSearchValue(),

                                variation.getGeneratedPositions(),

                                variation.getExploredPositions(),

                                variation.getSolvedPositions(),

                                false,

                                variation.hasChildren(),

                                endpoint
                        );
            }


            selectedCard.setSelected(
                    true
            );


            int indent =
                    depth == 0
                            ? 0
                            : BRANCH_INDENT;


            addCard(
                    selectedCard,
                    indent
            );


            if (depth
                    < path.size() - 1) {

                addConnector(
                        BRANCH_INDENT
                );
            }
        }


        // =====================================================
        // Continuations
        // =====================================================

        PathEntry current =
                path.get(
                        path.size() - 1
                );


        List<VariationNode> children =
                getChildren(
                        current
                );


        if (children != null
                &&
                !children.isEmpty()) {

            addConnector(
                    BRANCH_INDENT
            );


            addSectionHeading(
                    "CONTINUATIONS AFTER "
                            + current.getSan(),

                    BRANCH_INDENT
            );


            int nextDepth =
                    path.size();


            for (VariationNode child :
                    children) {

                AnalysisCard card =
                        new AnalysisCard(
                                child.getPosition(),

                                child.getSan(),

                                child.getOutcome(),

                                child.getMateDistance(),

                                createMateDisplay(
                                        child
                                ),

                                child.getStaticEvaluation(),

                                child.getSearchValue(),

                                child.getGeneratedPositions(),

                                child.getExploredPositions(),

                                child.getSolvedPositions(),

                                false,

                                child.hasChildren(),

                                false
                        );


                card.setClickAction(
                        () -> selectVariation(
                                child,
                                nextDepth
                        )
                );


                addCard(
                        card,
                        BRANCH_INDENT
                );
            }
        }


        refreshCards();
    }


    // =========================================================
    // Add card
    // =========================================================

    private void addCard(
            AnalysisCard card,
            int indent
    ) {

        if (indent <= 0) {

            cardsPanel.add(
                    card
            );


        } else {

            JPanel wrapper =
                    new JPanel(
                            new BorderLayout()
                    );


            wrapper.setOpaque(
                    false
            );


            wrapper.setAlignmentX(
                    Component.LEFT_ALIGNMENT
            );


            wrapper.setBorder(
                    BorderFactory.createEmptyBorder(
                            0,
                            indent,
                            0,
                            0
                    )
            );


            wrapper.setMaximumSize(
                    new Dimension(
                            Integer.MAX_VALUE,
                            218
                    )
            );


            wrapper.add(
                    card,
                    BorderLayout.CENTER
            );


            cardsPanel.add(
                    wrapper
            );
        }


        cardsPanel.add(
                Box.createVerticalStrut(
                        10
                )
        );
    }


    // =========================================================
    // Select root
    // =========================================================

    private void selectRootMove(
            MoveAnalysis move
    ) {

        path.clear();


        path.add(
                PathEntry.forMove(
                        move
                )
        );


        if (moveSelectionListener != null) {

            moveSelectionListener.accept(
                    move
            );
        }


        updateBreadcrumb();


        updateHeaderForMove(
                move
        );


        backButton.setEnabled(
                true
        );


        rebuildVerticalDisplay();
    }


    // =========================================================
    // Select continuation
    // =========================================================

    private void selectVariation(
            VariationNode variation,
            int depth
    ) {

        while (path.size()
                > depth) {

            path.remove(
                    path.size() - 1
            );
        }


        path.add(
                PathEntry.forVariation(
                        variation
                )
        );


        if (variationSelectionListener != null) {

            variationSelectionListener.accept(
                    variation
            );
        }


        updateBreadcrumb();


        updateHeaderForVariation(
                variation
        );


        backButton.setEnabled(
                true
        );


        rebuildVerticalDisplay();
    }


    // =========================================================
    // Back one level
    // =========================================================

    public void goBackOneLevel() {

        if (path.isEmpty()) {

            return;
        }


        path.remove(
                path.size() - 1
        );


        updateBreadcrumb();


        restoreHeaderForCurrentPath();


        rebuildVerticalDisplay();
    }


    // =========================================================
    // Selected path info
    // =========================================================

    // =========================================================
    // Commit a manual board move into the visible analysis path
    // =========================================================

    /*
     * A manual move and a clicked analysis move are two different
     * ways of traversing the SAME search graph.
     *
     * This method finds the already-generated analysis node whose
     * resulting Position matches the board's new actual Position and
     * appends that node to the selected-line stack without firing the
     * normal preview-selection callback.
     */
    public boolean commitPositionToPath(
            Position position
    ) {

        if (position == null
                ||
                currentAnalysis == null) {

            return false;
        }


        // =====================================================
        // First committed move from the analysis root
        // =====================================================

        if (path.isEmpty()) {

            for (MoveAnalysis move :
                    currentAnalysis.getMoves()) {

                if (samePosition(
                        move.getPosition(),
                        position
                )) {

                    path.add(
                            PathEntry.forMove(
                                    move
                            )
                    );


                    updateBreadcrumb();


                    updateHeaderForMove(
                            move
                    );


                    backButton.setEnabled(
                            true
                    );


                    rebuildVerticalDisplay();


                    return true;
                }
            }


            return false;
        }


        // =====================================================
        // Deeper committed move
        // =====================================================

        PathEntry current =
                path.get(
                        path.size() - 1
                );


        List<VariationNode> children =
                getChildren(
                        current
                );


        /*
         * First try the children already materialized in the panel.
         */
        VariationNode matchingChild =
                findChildByPosition(
                        children,
                        position
                );


        /*
         * IMPORTANT:
         *
         * Lazy GUI loading means "children is non-empty" no longer
         * implies that every graph edge currently available from this
         * position is represented in this particular GUI object.
         *
         * The scheduler may have added the manually played edge after
         * these children were first loaded. ChessWindow has already
         * ensured that edge exists in the persistent PositionGraph.
         *
         * Therefore, if the manual position is not in the stale local
         * child list, refresh exactly one immediate level directly
         * from the graph and merge it into the current PathEntry.
         */
        if (matchingChild == null
                &&
                continuationProvider != null) {

            List<VariationNode> refreshedChildren =
                    continuationProvider.apply(
                            current.getPosition()
                    );


            mergeChildrenIntoEntry(
                    current,
                    refreshedChildren
            );


            matchingChild =
                    findChildByPosition(
                            refreshedChildren,
                            position
                    );
        }


        if (matchingChild == null) {

            return false;
        }


        path.add(
                PathEntry.forVariation(
                        matchingChild
                )
        );


        updateBreadcrumb();


        updateHeaderForVariation(
                matchingChild
        );


        backButton.setEnabled(
                true
        );


        rebuildVerticalDisplay();


        return true;
    }


    /*
     * Find a child by canonical PositionKey rather than object identity.
     */
    private VariationNode findChildByPosition(
            List<VariationNode> children,
            Position position
    ) {

        if (children == null
                ||
                position == null) {

            return null;
        }


        for (VariationNode child :
                children) {

            if (child != null
                    &&
                    samePosition(
                            child.getPosition(),
                            position
                    )) {

                return child;
            }
        }


        return null;
    }


    /*
     * Merge newly-read one-level graph continuations into the GUI
     * object that backs this PathEntry.
     *
     * PositionKey equality prevents duplicate continuation cards when
     * background exploration refreshes the same edge repeatedly.
     */
    private void mergeChildrenIntoEntry(
            PathEntry entry,
            List<VariationNode> incoming
    ) {

        if (entry == null
                ||
                incoming == null
                ||
                incoming.isEmpty()) {

            return;
        }


        List<VariationNode> existing;


        if (entry.moveAnalysis != null) {

            existing =
                    entry.moveAnalysis
                            .getVariations();


        } else if (entry.variationNode != null) {

            existing =
                    entry.variationNode
                            .getChildren();


        } else {

            return;
        }


        for (VariationNode candidate :
                incoming) {

            if (candidate == null
                    ||
                    candidate.getPosition() == null) {

                continue;
            }


            if (findChildByPosition(
                    existing,
                    candidate.getPosition()
            ) != null) {

                continue;
            }


            if (entry.moveAnalysis != null) {

                entry.moveAnalysis.addVariations(
                        List.of(
                                candidate
                        )
                );


            } else {

                entry.variationNode.addChild(
                        candidate
                );
            }
        }
    }


    private boolean samePosition(
            Position first,
            Position second
    ) {

        if (first == second) {

            return true;
        }


        if (first == null
                ||
                second == null) {

            return false;
        }


        return first
                .createPositionKey()
                .equals(
                        second.createPositionKey()
                );
    }


    public int getPathDepth() {

        return path.size();
    }


    public List<String> getSelectedPathSan() {

        List<String> result =
                new ArrayList<>();


        for (PathEntry entry :
                path) {

            result.add(
                    entry.getSan()
            );
        }


        return Collections.unmodifiableList(
                result
        );
    }


    public Position getSelectedPosition() {

        if (path.isEmpty()) {

            return null;
        }


        return path
                .get(
                        path.size() - 1
                )
                .getPosition();
    }


    public int getSelectedSearchValue() {

        if (path.isEmpty()) {

            return currentAnalysis == null
                    ? 0
                    : currentAnalysis.getSearchValue();
        }


        return path
                .get(
                        path.size() - 1
                )
                .getSearchValue();
    }


    public SearchOutcome getSelectedOutcome() {

        if (path.isEmpty()) {

            return currentAnalysis == null
                    ? SearchOutcome.UNKNOWN
                    : currentAnalysis.getOutcome();
        }


        return path
                .get(
                        path.size() - 1
                )
                .getOutcome();
    }


    public int getSelectedMateDistance() {

        if (path.isEmpty()) {

            return currentAnalysis == null
                    ? -1
                    : currentAnalysis.getMateDistance();
        }


        return path
                .get(
                        path.size() - 1
                )
                .getMateDistance();
    }


    public int getSelectedGeneratedPositions() {

        if (path.isEmpty()) {

            return 0;
        }


        return path
                .get(
                        path.size() - 1
                )
                .getGeneratedPositions();
    }


    public int getSelectedExploredPositions() {

        if (path.isEmpty()) {

            return 0;
        }


        return path
                .get(
                        path.size() - 1
                )
                .getExploredPositions();
    }


    public int getSelectedSolvedPositions() {

        if (path.isEmpty()) {

            return 0;
        }


        return path
                .get(
                        path.size() - 1
                )
                .getSolvedPositions();
    }


    public List<PreviewData> getSelectedPathPreviewData() {

        List<PreviewData> result =
                new ArrayList<>();


        for (PathEntry entry :
                path) {

            result.add(
                    new PreviewData(
                            entry.getPosition(),
                            entry.getSearchValue(),
                            entry.getOutcome(),
                            entry.getMateDistance()
                    )
            );
        }


        return Collections.unmodifiableList(
                result
        );
    }


    // =========================================================
    // Child access
    // =========================================================

    private List<VariationNode> getChildren(
            PathEntry entry
    ) {

        ensureChildrenLoaded(
                entry
        );


        if (entry == null) {

            return List.of();
        }


        if (entry.moveAnalysis != null) {

            return entry.moveAnalysis
                    .getVariations();
        }


        if (entry.variationNode != null) {

            return entry.variationNode
                    .getChildren();
        }


        return List.of();
    }


    /*
     * Attach exactly one immediate continuation level when the
     * selected path reaches a node whose children were not included
     * in the current lightweight analysis snapshot.
     *
     * If there are currently no generated outgoing edges we leave the
     * list empty. A later background refresh may then try again after
     * exploration has added more graph edges.
     */
    private void ensureChildrenLoaded(
            PathEntry entry
    ) {

        if (entry == null
                ||
                continuationProvider == null) {

            return;
        }


        List<VariationNode> existing;


        Position position;


        if (entry.moveAnalysis != null) {

            existing =
                    entry.moveAnalysis
                            .getVariations();

            position =
                    entry.moveAnalysis
                            .getPosition();


        } else if (entry.variationNode != null) {

            existing =
                    entry.variationNode
                            .getChildren();

            position =
                    entry.variationNode
                            .getPosition();


        } else {

            return;
        }


        if (existing != null
                &&
                !existing.isEmpty()) {

            return;
        }


        if (position == null) {

            return;
        }


        List<VariationNode> loaded =
                continuationProvider.apply(
                        position
                );


        if (loaded == null
                ||
                loaded.isEmpty()) {

            return;
        }


        if (entry.moveAnalysis != null) {

            entry.moveAnalysis.addVariations(
                    loaded
            );


        } else {

            for (VariationNode child :
                    loaded) {

                entry.variationNode.addChild(
                        child
                );
            }
        }
    }


    // =========================================================
    // Section heading
    // =========================================================

    private void addSectionHeading(
            String text,
            int indent
    ) {

        JPanel wrapper =
                new JPanel(
                        new BorderLayout()
                );


        wrapper.setOpaque(
                false
        );


        wrapper.setAlignmentX(
                Component.LEFT_ALIGNMENT
        );


        wrapper.setBorder(
                BorderFactory.createEmptyBorder(
                        4,
                        indent + 2,
                        8,
                        0
                )
        );


        JLabel label =
                new JLabel(
                        text
                );


        label.setForeground(
                SECONDARY_TEXT
        );


        label.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.BOLD,
                        10
                )
        );


        wrapper.add(
                label,
                BorderLayout.WEST
        );


        cardsPanel.add(
                wrapper
        );
    }


    // =========================================================
    // Connector
    // =========================================================

    private void addConnector(
            int indent
    ) {

        JPanel connector =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.LEFT,
                                indent + 14,
                                0
                        )
                );


        connector.setOpaque(
                false
        );


        connector.setAlignmentX(
                Component.LEFT_ALIGNMENT
        );


        connector.setMaximumSize(
                new Dimension(
                        Integer.MAX_VALUE,
                        30
                )
        );


        JLabel arrow =
                new JLabel(
                        "↓"
                );


        arrow.setForeground(
                ACCENT
        );


        arrow.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.BOLD,
                        18
                )
        );


        connector.add(
                arrow
        );


        cardsPanel.add(
                connector
        );
    }


    // =========================================================
    // Breadcrumb
    // =========================================================

    private void updateBreadcrumb() {

        StringBuilder builder =
                new StringBuilder(
                        "Start"
                );


        for (PathEntry entry :
                path) {

            builder.append(
                    "  ›  "
            );


            builder.append(
                    entry.getSan()
            );
        }


        breadcrumbLabel.setText(
                builder.toString()
        );
    }


    // =========================================================
    // Move numbering
    // =========================================================

    private String createSelectedMoveText(
            int depth,
            String san
    ) {

        if (currentAnalysis == null) {

            return san;
        }


        boolean rootWhite =
                currentAnalysis
                        .getSideToMove()
                        == main.java.chess.model.Color.WHITE;


        boolean whiteMove;


        if (rootWhite) {

            whiteMove =
                    depth % 2 == 0;


        } else {

            whiteMove =
                    depth % 2 != 0;
        }


        int moveNumber =
                depth / 2
                        + 1;


        if (whiteMove) {

            return moveNumber
                    + ". "
                    + san;
        }


        return moveNumber
                + "... "
                + san;
    }


    // =========================================================
    // Header
    // =========================================================

    private void updateHeaderForMove(
            MoveAnalysis move
    ) {

        titleLabel.setText(
                "Analysis Variation"
        );


        outcomeLabel.setText(
                "Outcome  "
                        + formatOutcome(
                        move.getOutcome()
                )
        );


        evaluationLabel.setText(
                "Evaluation  "
                        + formatEvaluation(
                        move.getSearchValue()
                )
        );
    }


    private void updateHeaderForVariation(
            VariationNode variation
    ) {

        titleLabel.setText(
                "Analysis Variation"
        );


        outcomeLabel.setText(
                "Outcome  "
                        + formatOutcome(
                        variation.getOutcome()
                )
        );


        evaluationLabel.setText(
                "Evaluation  "
                        + formatEvaluation(
                        variation.getSearchValue()
                )
        );
    }


    public void restoreHeaderForCurrentPath() {

        if (currentAnalysis == null) {

            return;
        }


        if (path.isEmpty()) {

            titleLabel.setText(
                    "Current Position"
            );


            outcomeLabel.setText(
                    "Outcome  "
                            + formatOutcome(
                            currentAnalysis.getOutcome()
                    )
            );


            evaluationLabel.setText(
                    "Evaluation  "
                            + formatEvaluation(
                            currentAnalysis.getSearchValue()
                    )
            );


            return;
        }


        PathEntry last =
                path.get(
                        path.size() - 1
                );


        if (last.moveAnalysis != null) {

            updateHeaderForMove(
                    last.moveAnalysis
            );


        } else {

            updateHeaderForVariation(
                    last.variationNode
            );
        }
    }


    // =========================================================
    // Principal variation
    // =========================================================

    private void populatePrincipalVariation(
            PositionAnalysis analysis
    ) {

        principalVariationMovesPanel.removeAll();


        List<VariationNode> pv =
                analysis.getPrincipalVariation();


        if (pv == null
                ||
                pv.isEmpty()) {

            JLabel empty =
                    new JLabel(
                            "No line available"
                    );


            empty.setForeground(
                    SECONDARY_TEXT
            );


            principalVariationMovesPanel.add(
                    empty
            );


            principalVariationMovesPanel.revalidate();

            principalVariationMovesPanel.repaint();

            return;
        }


        boolean whiteToMove =
                analysis
                        .getSideToMove()
                        == main.java.chess.model.Color.WHITE;


        int moveNumber =
                1;


        for (int index = 0;
             index < pv.size();
             index++) {

            VariationNode node =
                    pv.get(
                            index
                    );


            String prefix;


            if (whiteToMove) {

                prefix =
                        moveNumber
                                + ". ";


            } else {

                prefix =
                        index == 0
                                ? moveNumber
                                + "... "
                                : "";
            }


            JButton button =
                    createPvButton(
                            prefix
                                    + node.getSan(),
                            node
                    );


            principalVariationMovesPanel.add(
                    button
            );


            if (!whiteToMove) {

                moveNumber++;
            }


            whiteToMove =
                    !whiteToMove;
        }


        principalVariationMovesPanel.revalidate();

        principalVariationMovesPanel.repaint();
    }


    private JButton createPvButton(
            String text,
            VariationNode node
    ) {

        JButton button =
                new JButton(
                        text
                );


        button.setFont(
                new Font(
                        Font.MONOSPACED,
                        Font.BOLD,
                        13
                )
        );


        button.setForeground(
                ACCENT
        );


        button.setBorderPainted(
                false
        );


        button.setContentAreaFilled(
                false
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


        button.setMargin(
                new Insets(
                        1,
                        3,
                        1,
                        3
                )
        );


        button.addActionListener(
                event -> {

                    if (variationSelectionListener != null) {

                        variationSelectionListener.accept(
                                node
                        );
                    }


                    backButton.setEnabled(
                            true
                    );
                }
        );


        return button;
    }


    // =========================================================
    // Analysis state
    // =========================================================

    public void setAnalyzing(
            boolean canGoBack
    ) {

        currentAnalysis =
                null;


        path.clear();


        titleLabel.setText(
                "Current Position"
        );


        outcomeLabel.setText(
                "Analyzing…"
        );


        evaluationLabel.setText(
                ""
        );


        breadcrumbLabel.setText(
                "Start"
        );


        backButton.setEnabled(
                canGoBack
        );


        principalVariationMovesPanel.removeAll();


        JLabel calculating =
                new JLabel(
                        "Calculating line…"
                );


        calculating.setForeground(
                SECONDARY_TEXT
        );


        principalVariationMovesPanel.add(
                calculating
        );


        cardsPanel.removeAll();


        JLabel loading =
                new JLabel(
                        "Analyzing candidate moves…"
                );


        loading.setForeground(
                SECONDARY_TEXT
        );


        loading.setBorder(
                BorderFactory.createEmptyBorder(
                        16,
                        8,
                        8,
                        8
                )
        );


        cardsPanel.add(
                loading
        );


        refreshCards();
    }


    public void setExploring(
            boolean exploring
    ) {

        /*
         * Continuous exploration is background engine state, not
         * navigation state. Do not rewrite the visible header here.
         *
         * The current title, outcome, evaluation, and breadcrumb stay
         * on screen while refreshed analysis snapshots update their
         * actual values through restoreHeaderForCurrentPath().
         *
         * This prevents the header from flashing between:
         *
         *     Current Position / Analysis Variation
         *
         * and:
         *
         *     Searching Variation / Generating positions…
         *
         * on every automatic search batch.
         */
    }


    // =========================================================
    // Theme
    // =========================================================

    public void setDarkTheme(
            boolean dark
    ) {

        darkTheme =
                dark;


        BACKGROUND =
                dark
                        ? DARK_BACKGROUND
                        : LIGHT_BACKGROUND;


        CARD_BACKGROUND =
                dark
                        ? DARK_CARD_BACKGROUND
                        : LIGHT_CARD_BACKGROUND;


        CARD_HOVER =
                dark
                        ? DARK_CARD_HOVER
                        : LIGHT_CARD_HOVER;


        CARD_SELECTED =
                dark
                        ? DARK_CARD_SELECTED
                        : LIGHT_CARD_SELECTED;


        BORDER_COLOR =
                dark
                        ? DARK_BORDER_COLOR
                        : LIGHT_BORDER_COLOR;


        PRIMARY_TEXT =
                dark
                        ? DARK_PRIMARY_TEXT
                        : LIGHT_PRIMARY_TEXT;


        SECONDARY_TEXT =
                dark
                        ? DARK_SECONDARY_TEXT
                        : LIGHT_SECONDARY_TEXT;


        ACCENT =
                dark
                        ? DARK_ACCENT
                        : LIGHT_ACCENT;


        applyThemeRecursively(
                this
        );


        if (currentAnalysis != null) {

            List<String> savedPath =
                    new ArrayList<>(
                            getSelectedPathSan()
                    );


            boolean backEnabled =
                    backButton.isEnabled();


            if (savedPath.isEmpty()) {

                setAnalysis(
                        currentAnalysis,
                        backEnabled
                );

            } else {

                restorePath(
                        currentAnalysis,
                        savedPath,
                        backEnabled
                );
            }

        } else {

            repaint();
        }
    }


    public boolean isDarkTheme() {

        return darkTheme;
    }


    private void applyThemeRecursively(
            Component component
    ) {

        if (component instanceof JPanel panel) {

            if (panel == this
                    ||
                    panel.isOpaque()) {

                panel.setBackground(
                        panel == this
                                ? BACKGROUND
                                : CARD_BACKGROUND
                );
            }
        }


        if (component instanceof JLabel label) {

            label.setForeground(
                    PRIMARY_TEXT
            );
        }


        if (component instanceof JButton button) {

            button.setCursor(
                    Cursor.getPredefinedCursor(
                            Cursor.HAND_CURSOR
                    )
            );


            if (button == backButton) {

                java.awt.Color backBackground =
                        darkTheme
                                ? new java.awt.Color(19, 27, 35)
                                : new java.awt.Color(244, 246, 249);


                java.awt.Color backBorder =
                        darkTheme
                                ? new java.awt.Color(45, 56, 68)
                                : new java.awt.Color(196, 202, 210);


                button.setForeground(
                        darkTheme
                                ? PRIMARY_TEXT
                                : new java.awt.Color(55, 61, 70)
                );


                button.setBackground(
                        backBackground
                );


                button.setBorder(
                        BorderFactory.createCompoundBorder(
                                BorderFactory.createLineBorder(
                                        backBorder,
                                        1,
                                        true
                                ),
                                BorderFactory.createEmptyBorder(
                                        4,
                                        10,
                                        4,
                                        10
                                )
                        )
                );


                button.setFocusPainted(
                        false
                );


                button.setRolloverEnabled(
                        false
                );


                button.setBorderPainted(
                        true
                );


                button.setContentAreaFilled(
                        false
                );


                button.setOpaque(
                        false
                );

            } else {

                button.setForeground(
                        darkTheme
                                ? PRIMARY_TEXT
                                : SECONDARY_TEXT
                );


                button.setBackground(
                        darkTheme
                                ? CARD_BACKGROUND
                                : LIGHT_CARD_BACKGROUND
                );
            }
        }


        if (component instanceof Container container) {

            for (Component child :
                    container.getComponents()) {

                applyThemeRecursively(
                        child
                );
            }
        }


        setBackground(
                BACKGROUND
        );


        repaint();
    }


    // =========================================================
    // Public callbacks
    // =========================================================

    public void setMoveSelectionListener(
            Consumer<MoveAnalysis> listener
    ) {

        moveSelectionListener =
                listener;
    }


    public void setVariationSelectionListener(
            Consumer<VariationNode> listener
    ) {

        variationSelectionListener =
                listener;
    }


    public void setExplorePositionListener(
            Consumer<Position> listener
    ) {

        explorePositionListener =
                listener;
    }


    /*
     * Supplies one immediate generated continuation level for a
     * position. This keeps the panel independent of PositionGraph
     * while allowing navigation to arbitrary depth on demand.
     */
    public void setContinuationProvider(
            Function<Position, List<VariationNode>> provider
    ) {

        continuationProvider =
                provider;
    }


    public void setBackListener(
            Runnable listener
    ) {

        backListener =
                listener;
    }


    public void setBackEnabled(
            boolean enabled
    ) {

        backButton.setEnabled(
                enabled
        );
    }


    public void clearMoveSelection() {

        path.clear();


        updateBreadcrumb();


        restoreHeaderForCurrentPath();


        rebuildVerticalDisplay();
    }


    // =========================================================
    // Statistic display
    // =========================================================

    private JPanel createStatisticPanel(
            String headingText,
            int generatedPositions,
            int countedPositions
    ) {

        int safeGenerated =
                Math.max(
                        0,
                        generatedPositions
                );


        int safeCounted =
                Math.max(
                        0,
                        Math.min(
                                countedPositions,
                                safeGenerated
                        )
                );


        double fraction =
                safeGenerated <= 0
                        ? 0.0
                        : (double) safeCounted
                        / (double) safeGenerated;


        JPanel panel =
                new JPanel();


        panel.setOpaque(
                false
        );


        panel.setLayout(
                new BoxLayout(
                        panel,
                        BoxLayout.Y_AXIS
                )
        );


        panel.setAlignmentX(
                Component.LEFT_ALIGNMENT
        );


        JLabel heading =
                new JLabel(
                        headingText
                );


        heading.setForeground(
                SECONDARY_TEXT
        );


        heading.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.PLAIN,
                        10
                )
        );


        heading.setAlignmentX(
                Component.LEFT_ALIGNMENT
        );


        JPanel countRow =
                new JPanel(
                        new BorderLayout(
                                8,
                                0
                        )
                );


        countRow.setOpaque(
                false
        );


        countRow.setAlignmentX(
                Component.LEFT_ALIGNMENT
        );


        countRow.setMaximumSize(
                new Dimension(
                        205,
                        18
                )
        );


        JLabel counts =
                new JLabel(
                        safeCounted
                                + " / "
                                + safeGenerated
                                + " generated"
                );


        counts.setForeground(
                SECONDARY_TEXT
        );


        counts.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.PLAIN,
                        10
                )
        );


        JLabel percentage =
                new JLabel(
                        String.format(
                                "%.1f%%",
                                fraction * 100.0
                        )
                );


        percentage.setForeground(
                PRIMARY_TEXT
        );


        percentage.setFont(
                new Font(
                        Font.MONOSPACED,
                        Font.BOLD,
                        10
                )
        );


        countRow.add(
                counts,
                BorderLayout.WEST
        );


        countRow.add(
                percentage,
                BorderLayout.EAST
        );


        JProgressBar progressBar =
                new JProgressBar(
                        0,
                        1000
                );


        progressBar.setValue(
                (int) Math.round(
                        fraction * 1000.0
                )
        );


        progressBar.setStringPainted(
                false
        );


        progressBar.setBorderPainted(
                false
        );


        progressBar.setPreferredSize(
                new Dimension(
                        205,
                        6
                )
        );


        progressBar.setMinimumSize(
                new Dimension(
                        80,
                        6
                )
        );


        progressBar.setMaximumSize(
                new Dimension(
                        205,
                        6
                )
        );


        progressBar.setAlignmentX(
                Component.LEFT_ALIGNMENT
        );


        panel.add(
                heading
        );


        panel.add(
                Box.createVerticalStrut(
                        2
                )
        );


        panel.add(
                countRow
        );


        panel.add(
                Box.createVerticalStrut(
                        3
                )
        );


        panel.add(
                progressBar
        );


        return panel;
    }


    // =========================================================
    // Formatting
    // =========================================================

    private String formatEvaluation(
            int evaluation
    ) {

        double pawns =
                evaluation / 100.0;


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


    private String formatOutcome(
            SearchOutcome outcome
    ) {

        if (outcome == null) {

            return "Unknown";
        }


        return switch (outcome) {

            case WHITE_WIN ->
                    "White win";

            case BLACK_WIN ->
                    "Black win";

            case DRAW ->
                    "Draw";

            case UNKNOWN ->
                    "Unknown";
        };
    }


    private String createMateDisplay(
            VariationNode variation
    ) {

        if (!variation.hasProvenMate()) {

            return "-";
        }


        if (variation.isTerminal()
                &&
                variation.getMateDistance() == 0) {

            return "CHECKMATE";
        }


        if (variation.getMateDistance()
                < 0) {

            return "-";
        }


        int moves =
                (
                        variation.getMateDistance()
                                + 1
                ) / 2;


        return "MATE IN "
                + moves;
    }


    private Border createCardBorder() {

        return BorderFactory.createLineBorder(
                BORDER_COLOR,
                1,
                true
        );
    }


    private void refreshCards() {

        cardsPanel.revalidate();

        cardsPanel.repaint();

        revalidate();

        repaint();
    }


    // =========================================================
    // Preview data
    // =========================================================

    public static class PreviewData {

        private final Position position;

        private final int searchValue;

        private final SearchOutcome outcome;

        private final int mateDistance;


        public PreviewData(
                Position position,
                int searchValue,
                SearchOutcome outcome,
                int mateDistance
        ) {

            this.position =
                    position;


            this.searchValue =
                    searchValue;


            this.outcome =
                    outcome;


            this.mateDistance =
                    mateDistance;
        }


        public Position getPosition() {

            return position;
        }


        public int getSearchValue() {

            return searchValue;
        }


        public SearchOutcome getOutcome() {

            return outcome;
        }


        public int getMateDistance() {

            return mateDistance;
        }
    }


    // =========================================================
    // Path entry
    // =========================================================

    private static class PathEntry {

        private final MoveAnalysis moveAnalysis;

        private final VariationNode variationNode;


        private PathEntry(
                MoveAnalysis moveAnalysis,
                VariationNode variationNode
        ) {

            this.moveAnalysis =
                    moveAnalysis;


            this.variationNode =
                    variationNode;
        }


        static PathEntry forMove(
                MoveAnalysis move
        ) {

            return new PathEntry(
                    move,
                    null
            );
        }


        static PathEntry forVariation(
                VariationNode variation
        ) {

            return new PathEntry(
                    null,
                    variation
            );
        }


        String getSan() {

            if (moveAnalysis != null) {

                return moveAnalysis.getSan();
            }


            if (variationNode != null) {

                return variationNode.getSan();
            }


            return "";
        }


        Position getPosition() {

            if (moveAnalysis != null) {

                return moveAnalysis.getPosition();
            }


            if (variationNode != null) {

                return variationNode.getPosition();
            }


            return null;
        }


        int getSearchValue() {

            if (moveAnalysis != null) {

                return moveAnalysis.getSearchValue();
            }


            if (variationNode != null) {

                return variationNode.getSearchValue();
            }


            return 0;
        }


        SearchOutcome getOutcome() {

            if (moveAnalysis != null) {

                return moveAnalysis.getOutcome();
            }


            if (variationNode != null) {

                return variationNode.getOutcome();
            }


            return SearchOutcome.UNKNOWN;
        }


        int getMateDistance() {

            if (moveAnalysis != null) {

                return moveAnalysis.getMateDistance();
            }


            if (variationNode != null) {

                return variationNode.getMateDistance();
            }


            return -1;
        }


        int getGeneratedPositions() {

            if (moveAnalysis != null) {

                return moveAnalysis.getGeneratedPositions();
            }


            if (variationNode != null) {

                return variationNode.getGeneratedPositions();
            }


            return 0;
        }


        int getExploredPositions() {

            if (moveAnalysis != null) {

                return moveAnalysis.getExploredPositions();
            }


            if (variationNode != null) {

                return variationNode.getExploredPositions();
            }


            return 0;
        }


        int getSolvedPositions() {

            if (moveAnalysis != null) {

                return moveAnalysis.getSolvedPositions();
            }


            if (variationNode != null) {

                return variationNode.getSolvedPositions();
            }


            return 0;
        }
    }


    // =========================================================
    // Card
    // =========================================================

    private class AnalysisCard extends JPanel {

        private final JPanel cardSurface;

        private boolean selected;

        private Runnable clickAction;


        private AnalysisCard(
                Position position,
                String moveText,
                SearchOutcome outcome,
                int mateDistance,
                String mateDisplay,
                int staticEvaluation,
                int searchValue,
                int generatedPositions,
                int exploredPositions,
                int solvedPositions,
                boolean best,
                boolean hasContinuations,
                boolean selectedEndpoint
        ) {

            setOpaque(
                    false
            );


            setLayout(
                    new BorderLayout()
            );


            setAlignmentX(
                    Component.LEFT_ALIGNMENT
            );


            cardSurface =
                    new JPanel(
                            new BorderLayout(
                                    14,
                                    0
                            )
                    );


            cardSurface.setBackground(
                    CARD_BACKGROUND
            );


            cardSurface.setBorder(
                    createCardBorder()
            );


            cardSurface.setCursor(
                    Cursor.getPredefinedCursor(
                            Cursor.HAND_CURSOR
                    )
            );


            cardSurface.setMaximumSize(
                    new Dimension(
                            Integer.MAX_VALUE,
                            218
                    )
            );


            cardSurface.setPreferredSize(
                    new Dimension(
                            450,
                            210
                    )
            );


            // =================================================
            // Mini board
            // =================================================

            MiniChessBoardPanel miniBoard =
                    new MiniChessBoardPanel(
                            position
                    );


            JPanel boardWrapper =
                    new JPanel(
                            new GridBagLayout()
                    );


            boardWrapper.setOpaque(
                    false
            );


            boardWrapper.setBorder(
                    BorderFactory.createEmptyBorder(
                            8,
                            8,
                            8,
                            0
                    )
            );


            boardWrapper.add(
                    miniBoard
            );


            cardSurface.add(
                    boardWrapper,
                    BorderLayout.WEST
            );


            // =================================================
            // Info column
            // =================================================

            JPanel information =
                    new JPanel();


            information.setOpaque(
                    false
            );


            information.setLayout(
                    new BoxLayout(
                            information,
                            BoxLayout.Y_AXIS
                    )
            );


            information.setBorder(
                    BorderFactory.createEmptyBorder(
                            8,
                            0,
                            8,
                            12
                    )
            );


            if (best) {

                JLabel bestLabel =
                        new JLabel(
                                "BEST LINE"
                        );


                bestLabel.setForeground(
                        ACCENT
                );


                bestLabel.setFont(
                        new Font(
                                Font.SANS_SERIF,
                                Font.BOLD,
                                10
                        )
                );


                bestLabel.setAlignmentX(
                        Component.LEFT_ALIGNMENT
                );


                information.add(
                        bestLabel
                );


                information.add(
                        Box.createVerticalStrut(
                                2
                        )
                );
            }


            JLabel moveLabel =
                    new JLabel(
                            moveText
                    );


            moveLabel.setForeground(
                    PRIMARY_TEXT
            );


            moveLabel.setFont(
                    new Font(
                            Font.SANS_SERIF,
                            Font.BOLD,
                            17
                    )
            );


            moveLabel.setAlignmentX(
                    Component.LEFT_ALIGNMENT
            );


            information.add(
                    moveLabel
            );


            information.add(
                    Box.createVerticalStrut(
                            4
                    )
            );


            String mainValue;


            if (mateDisplay != null
                    &&
                    !mateDisplay.isBlank()
                    &&
                    !"-".equals(
                            mateDisplay
                    )) {

                mainValue =
                        mateDisplay;


            } else {

                mainValue =
                        formatEvaluation(
                                searchValue
                        );
            }


            JLabel primaryValueLabel =
                    new JLabel(
                            mainValue
                    );


            primaryValueLabel.setForeground(
                    PRIMARY_TEXT
            );


            primaryValueLabel.setFont(
                    new Font(
                            Font.MONOSPACED,
                            Font.BOLD,
                            16
                    )
            );


            primaryValueLabel.setAlignmentX(
                    Component.LEFT_ALIGNMENT
            );


            information.add(
                    primaryValueLabel
            );


            information.add(
                    Box.createVerticalStrut(
                            3
                    )
            );


            StringBuilder detail =
                    new StringBuilder();


            detail.append(
                    formatOutcome(
                            outcome
                    )
            );


            if (staticEvaluation
                    != searchValue) {

                detail.append(
                        "  ·  static "
                );


                detail.append(
                        formatEvaluation(
                                staticEvaluation
                        )
                );
            }


            JLabel detailLabel =
                    new JLabel(
                            detail.toString()
                    );


            detailLabel.setForeground(
                    SECONDARY_TEXT
            );


            detailLabel.setFont(
                    new Font(
                            Font.SANS_SERIF,
                            Font.PLAIN,
                            11
                    )
            );


            detailLabel.setAlignmentX(
                    Component.LEFT_ALIGNMENT
            );


            information.add(
                    detailLabel
            );


            // =================================================
            // Explored
            // =================================================

            information.add(
                    Box.createVerticalStrut(
                            5
                    )
            );


            information.add(
                    createStatisticPanel(
                            "Explored positions",
                            generatedPositions,
                            exploredPositions
                    )
            );


            // =================================================
            // Solved
            // =================================================

            information.add(
                    Box.createVerticalStrut(
                            5
                    )
            );


            information.add(
                    createStatisticPanel(
                            "Solved positions",
                            generatedPositions,
                            solvedPositions
                    )
            );


            information.add(
                    Box.createVerticalGlue()
            );


            String bottomText;


            java.awt.Color bottomColor;


            if (selectedEndpoint
                    &&
                    outcome == SearchOutcome.UNKNOWN) {

                bottomText =
                        "Selected search frontier";

                bottomColor =
                        ACCENT;


            } else if (hasContinuations) {

                bottomText =
                        "Inspect continuations  ›";

                bottomColor =
                        ACCENT;


            } else if (outcome != SearchOutcome.UNKNOWN) {

                bottomText =
                        "Solved position";

                bottomColor =
                        SECONDARY_TEXT;


            } else {

                bottomText =
                        "Generated frontier";

                bottomColor =
                        SECONDARY_TEXT;
            }


            JLabel bottomLabel =
                    new JLabel(
                            bottomText
                    );


            bottomLabel.setForeground(
                    bottomColor
            );


            bottomLabel.setFont(
                    new Font(
                            Font.SANS_SERIF,
                            hasContinuations
                                    ? Font.BOLD
                                    : Font.PLAIN,
                            11
                    )
            );


            bottomLabel.setAlignmentX(
                    Component.LEFT_ALIGNMENT
            );


            information.add(
                    bottomLabel
            );


            cardSurface.add(
                    information,
                    BorderLayout.CENTER
            );


            add(
                    cardSurface,
                    BorderLayout.CENTER
            );


            selected =
                    false;


            MouseAdapter adapter =
                    new MouseAdapter() {

                        @Override
                        public void mouseClicked(
                                MouseEvent event
                        ) {

                            if (clickAction != null) {

                                clickAction.run();
                            }
                        }


                        @Override
                        public void mouseEntered(
                                MouseEvent event
                        ) {

                            if (!selected) {

                                cardSurface.setBackground(
                                        CARD_HOVER
                                );
                            }
                        }


                        @Override
                        public void mouseExited(
                                MouseEvent event
                        ) {

                            if (!selected) {

                                cardSurface.setBackground(
                                        CARD_BACKGROUND
                                );
                            }
                        }
                    };


            addMouseListenerRecursively(
                    cardSurface,
                    adapter
            );
        }


        private void addMouseListenerRecursively(
                Component component,
                MouseAdapter adapter
        ) {

            if (component instanceof AbstractButton) {

                return;
            }


            component.addMouseListener(
                    adapter
            );


            if (component
                    instanceof Container container) {

                for (Component child :
                        container.getComponents()) {

                    addMouseListenerRecursively(
                            child,
                            adapter
                    );
                }
            }
        }


        void setClickAction(
                Runnable clickAction
        ) {

            this.clickAction =
                    clickAction;
        }


        void setSelected(
                boolean selected
        ) {

            this.selected =
                    selected;


            cardSurface.setBackground(
                    selected
                            ? CARD_SELECTED
                            : CARD_BACKGROUND
            );


            cardSurface.setBorder(
                    selected
                            ? BorderFactory.createLineBorder(
                            ACCENT,
                            1,
                            true
                    )
                            : createCardBorder()
            );
        }
    }
}


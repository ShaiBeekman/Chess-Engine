package main.java.chess.gui;

import main.java.chess.analysis.MoveAnalysis;
import main.java.chess.analysis.PositionAnalysis;
import main.java.chess.analysis.VariationNode;
import main.java.chess.engine.ChessEngine;
import main.java.chess.endgame.EndgameGenerator;
import main.java.chess.endgame.EndgameSettings;
import main.java.chess.endgame.EndgameStudyEligibility;
import main.java.chess.model.FenCodec;
import main.java.chess.model.Position;
import main.java.chess.search.SearchOutcome;

import javax.swing.*;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import java.util.ArrayList;
import java.util.List;


public class ChessWindow extends JFrame {

    // =========================================================
    // Persistent dovetail tuning
    // =========================================================

    /*
     * Keep this deliberately modest.
     *
     * The scheduler itself persists across batches, so these are
     * continuation slices of ONE search rather than restarted
     * searches.
     */
    /*
     * Let the persistent scheduler do substantially more useful
     * graph work before asking Swing to rebuild the analysis UI.
     */
    private static final int AUTO_WORK_UNITS =
            64;


    /*
     * The search remains continuous in the background, while the
     * visible analysis refreshes about four times per second.
     */
    private static final int AUTO_BATCH_DELAY_MS =
            250;


    /*
     * Endgame Study is not a normal GUI-browsing search.  While a
     * generated position is being certified, spend much larger batches
     * on the exact root proof and skip rebuilding the public analysis
     * tree after every batch.
     */
    private static final int ENDGAME_PROOF_WORK_UNITS =
            8192;


    // =========================================================
    // Window appearance
    // =========================================================

    private static final Color APP_BACKGROUND =
            new Color(
                    34,
                    37,
                    43
            );


    private static final Color HEADER_BACKGROUND =
            new Color(
                    28,
                    31,
                    36
            );


    private static final Color HEADER_BORDER =
            new Color(
                    55,
                    59,
                    67
            );


    private static final Color HEADER_PRIMARY_TEXT =
            new Color(
                    242,
                    244,
                    247
            );


    private static final Color HEADER_SECONDARY_TEXT =
            new Color(
                    153,
                    160,
                    171
            );


    private static final Color BADGE_BACKGROUND =
            new Color(
                    51,
                    57,
                    66
            );


    // =========================================================
    // GUI
    // =========================================================

    private final ChessBoardPanel boardPanel;

    private final AnalysisPanel analysisPanel;

    private final EvaluationBar evaluationBar;
    private final SetupPanel setupPanel;
    private final EndgameStudyPanel endgameStudyPanel;
    private JPanel analysisArea;


    // =========================================================
    // Engine
    // =========================================================

    private final ChessEngine engine;


    // =========================================================
    // Analysis state
    // =========================================================

    private PositionAnalysis currentAnalysis;

    private boolean darkTheme = true;

    private JPanel applicationHeader;
    private JPanel workspace;
    private JPanel boardArea;
    private JLabel headerTitleLabel;
    private JLabel headerSubtitleLabel;
    private JButton darkThemeButton;
    private JButton lightThemeButton;
    private JLabel darkThemeIcon;
    private JLabel lightThemeIcon;
    private JPanel themeControls;
    private JPanel versionBadge;
    private JLabel versionBadgeLabel;
    private JButton loadFenButton;
    private JButton copyFenButton;
    private JButton resetPositionButton;
    private JButton setupPositionButton;
    private JButton endgameButton;
    private PiecePalettePanel piecePalettePanel;

    private final EndgameGenerator endgameGenerator;
    private EndgameSettings lastEndgameSettings;
    private boolean endgameStudyMode;
    private boolean endgameStudyReady;
    private boolean regeneratingRejectedEndgame;
    private long endgameProofGeneration;

    private long analysisRequestId;


    // =========================================================
    // Always-on worker chain
    // =========================================================

    /*
     * This generation controls GUI worker callbacks only.
     *
     * Changing selection does NOT reset the engine scheduler.
     * It merely invalidates the old UI batch chain and starts a
     * new one with a different focus bias.
     */
    private long explorationGeneration;

    private boolean automaticExplorationActive;


    // =========================================================
    // Preview / manual history
    // =========================================================

    private final List<PreviewState>
            previewHistory;


    private final List<Position>
            gameHistory;


    // =========================================================
    // Constructor
    // =========================================================

    public ChessWindow(
            Position position
    ) {

        super(
                "Chess Engine — Persistent Graph Explorer"
        );


        if (position == null) {

            throw new IllegalArgumentException(
                    "Position cannot be null."
            );
        }


        setDefaultCloseOperation(
                JFrame.EXIT_ON_CLOSE
        );


        getContentPane().setBackground(
                APP_BACKGROUND
        );


        setLayout(
                new BorderLayout()
        );


        engine =
                new ChessEngine(
                        3
                );


        endgameGenerator =
                new EndgameGenerator();


        lastEndgameSettings =
                EndgameSettings.fixed(
                        3
                );


        boardPanel =
                new ChessBoardPanel(
                        position
                );


        evaluationBar =
                new EvaluationBar();


        analysisPanel =
                new AnalysisPanel();


        setupPanel =
                new SetupPanel();


        setupPanel.setVisible(
                false
        );


        endgameStudyPanel =
                new EndgameStudyPanel();


        endgameStudyPanel.setVisible(
                false
        );


        endgameStudyPanel.setNextListener(
                () ->
                        generateEndgame(
                                lastEndgameSettings
                        )
        );


        endgameStudyPanel.setNewListener(
                this::openEndgameGenerator
        );


        endgameStudyPanel.setHintListener(
                () ->
                        endgameStudyPanel.setStatus(
                                "Hint support will use the proven solution."
                        )
        );


        endgameStudyPanel.setGiveUpListener(
                () ->
                        revealEndgameAnalysis()
        );


        boardPanel.setSetupChangeListener(
                this::refreshSetupPanel
        );


        piecePalettePanel =
                new PiecePalettePanel(
                        boardPanel
                );


        piecePalettePanel.setVisible(
                false
        );


        piecePalettePanel.setAnalyzeListener(
                this::commitPositionSetup
        );


        piecePalettePanel.setCancelListener(
                this::cancelPositionSetup
        );


        piecePalettePanel.setAnalyzeListener(
                this::commitPositionSetup
        );


        currentAnalysis =
                null;


        analysisRequestId =
                0;


        explorationGeneration =
                0;


        automaticExplorationActive =
                false;


        previewHistory =
                new ArrayList<>();


        gameHistory =
                new ArrayList<>();


        gameHistory.add(
                position
        );


        // =====================================================
        // Application header
        // =====================================================

        add(
                createApplicationHeader(),
                BorderLayout.NORTH
        );


        // =====================================================
        // Main workspace
        // =====================================================

        workspace =
                new JPanel(
                        new BorderLayout()
                );


        workspace.setBackground(
                APP_BACKGROUND
        );


        boardArea =
                new JPanel(
                        new BorderLayout(
                                8,
                                0
                        )
                );


        boardArea.setBackground(
                APP_BACKGROUND
        );


        boardArea.setBorder(
                BorderFactory.createEmptyBorder(
                        16,
                        16,
                        16,
                        6
                )
        );


        boardArea.add(
                evaluationBar,
                BorderLayout.WEST
        );


        boardArea.add(
                boardPanel,
                BorderLayout.CENTER
        );


        boardArea.add(
                piecePalettePanel,
                BorderLayout.SOUTH
        );


        workspace.add(
                boardArea,
                BorderLayout.CENTER
        );


        analysisArea =
                new JPanel(
                        new BorderLayout()
                );


        analysisArea.setOpaque(
                false
        );


        analysisArea.setBorder(
                BorderFactory.createEmptyBorder(
                        16,
                        6,
                        16,
                        16
                )
        );


        analysisArea.add(
                analysisPanel,
                BorderLayout.CENTER
        );


        analysisArea.add(
                setupPanel,
                BorderLayout.EAST
        );


        analysisArea.add(
                endgameStudyPanel,
                BorderLayout.WEST
        );


        workspace.add(
                analysisArea,
                BorderLayout.EAST
        );


        add(
                workspace,
                BorderLayout.CENTER
        );


        // =====================================================
        // Manual board move
        // =====================================================

        boardPanel.setPositionChangeListener(
                this::handleRealPositionChange
        );

        boardPanel.setPreviewCommitListener(
                this::commitPreviewForManualPlay
        );


        // =====================================================
        // Analysis selection
        // =====================================================

        analysisPanel.setMoveSelectionListener(
                this::handleMoveSelection
        );


        analysisPanel.setVariationSelectionListener(
                this::handleVariationSelection
        );


        /*
         * AnalysisPanel no longer receives a recursively materialized
         * fixed-depth variation tree. It asks the persistent engine
         * for exactly one more generated level whenever navigation
         * reaches a new endpoint.
         */
        analysisPanel.setContinuationProvider(
                engine::getImmediateContinuations
        );


        /*
         * Compatibility with the older callback.
         */
        analysisPanel.setExplorePositionListener(
                ignored ->
                        retargetSearchBias()
        );


        // =====================================================
        // Unified Back
        // =====================================================

        analysisPanel.setBackListener(
                this::handleBack
        );


        updateBackButton();


        applyTheme(
                true
        );


        pack();


        setMinimumSize(
                new Dimension(
                        1240,
                        760
                )
        );


        setSize(
                Math.max(
                        getWidth(),
                        1280
                ),
                Math.max(
                        getHeight(),
                        800
                )
        );


        setLocationRelativeTo(
                null
        );


        analyzeCurrentPosition();
    }


    // =========================================================
    // Application header
    // =========================================================

    private JPanel createApplicationHeader() {

        applicationHeader =
                new JPanel(
                        new BorderLayout(
                                18,
                                0
                        )
                );


        JPanel text =
                new JPanel();


        text.setOpaque(
                false
        );


        text.setLayout(
                new BoxLayout(
                        text,
                        BoxLayout.Y_AXIS
                )
        );


        headerTitleLabel =
                new JLabel(
                        "Chess Engine"
                );


        headerTitleLabel.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.BOLD,
                        18
                )
        );


        headerSubtitleLabel =
                new JLabel(
                        "Persistent graph exploration and tactical analysis"
                );


        headerSubtitleLabel.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.PLAIN,
                        11
                )
        );


        text.add(
                headerTitleLabel
        );


        text.add(
                Box.createVerticalStrut(
                        2
                )
        );


        text.add(
                headerSubtitleLabel
        );


        applicationHeader.add(
                text,
                BorderLayout.WEST
        );


        themeControls =
                new JPanel(
                        new GridBagLayout()
                );


        themeControls.setOpaque(
                false
        );


        themeControls.setPreferredSize(
                new Dimension(
                        205,
                        40
                )
        );


        themeControls.setMinimumSize(
                new Dimension(
                        205,
                        40
                )
        );


        themeControls.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(
                                new Color(
                                        42,
                                        53,
                                        64
                                ),
                                1,
                                true
                        ),
                        BorderFactory.createEmptyBorder(
                                2,
                                8,
                                2,
                                8
                        )
                )
        );


        darkThemeButton =
                createThemeButton(
                        "Dark",
                        true
                );


        lightThemeButton =
                createThemeButton(
                        "Light",
                        false
                );


        darkThemeIcon =
                createThemeIcon(
                        "☾"
                );


        lightThemeIcon =
                createThemeIcon(
                        "☀"
                );


        JPanel centeredThemeRow =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.CENTER,
                                10,
                                0
                        )
                );


        centeredThemeRow.setOpaque(
                false
        );


        centeredThemeRow.add(
                createThemeSegment(
                        darkThemeIcon,
                        darkThemeButton
                )
        );


        JLabel themeDivider =
                new JLabel(
                        "│"
                );


        themeDivider.setForeground(
                new Color(
                        88,
                        98,
                        110
                )
        );


        themeDivider.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.PLAIN,
                        14
                )
        );


        centeredThemeRow.add(
                themeDivider
        );


        centeredThemeRow.add(
                createThemeSegment(
                        lightThemeIcon,
                        lightThemeButton
                )
        );


        themeControls.add(
                centeredThemeRow
        );


        loadFenButton =
                createHeaderActionButton(
                        "Load FEN"
                );


        loadFenButton.addActionListener(
                event ->
                        showLoadFenDialog()
        );


        copyFenButton =
                createHeaderActionButton(
                        "Copy FEN"
                );


        copyFenButton.addActionListener(
                event ->
                        copyCurrentFen()
        );


        resetPositionButton =
                createHeaderActionButton(
                        "Reset"
                );


        resetPositionButton.addActionListener(
                event -> {

                    if (boardPanel.isSetupMode()) {

                        cancelPositionSetup();

                    } else {

                        resetToStartingPosition();
                    }
                }
        );


        setupPositionButton =
                createHeaderActionButton(
                        "Setup"
                );


        setupPositionButton.addActionListener(
                event ->
                        beginPositionSetup()
        );


        endgameButton =
                createHeaderActionButton(
                        "Endgame"
                );


        endgameButton.addActionListener(
                event ->
                        openEndgameGenerator()
        );


        versionBadge =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.CENTER,
                                10,
                                5
                        )
                );


        versionBadgeLabel =
                new JLabel(
                        "v1.0"
                );


        versionBadgeLabel.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.BOLD,
                        11
                )
        );


        versionBadge.add(
                versionBadgeLabel
        );


        JPanel badgeWrapper =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.RIGHT,
                                10,
                                0
                        )
                );


        badgeWrapper.setOpaque(
                false
        );


        badgeWrapper.add(
                resetPositionButton
        );


        badgeWrapper.add(
                setupPositionButton
        );


        badgeWrapper.add(
                endgameButton
        );


        badgeWrapper.add(
                loadFenButton
        );


        badgeWrapper.add(
                copyFenButton
        );


        badgeWrapper.add(
                themeControls
        );


        badgeWrapper.add(
                versionBadge
        );


        applicationHeader.add(
                badgeWrapper,
                BorderLayout.EAST
        );


        return applicationHeader;
    }


    private void styleHeaderActionButton(
            JButton button,
            Color foreground,
            Color background,
            Color border
    ) {

        if (button == null) {

            return;
        }


        button.setForeground(
                foreground
        );


        button.setBackground(
                background
        );


        button.setBorder(
                BorderFactory.createLineBorder(
                        border,
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


    private JButton createHeaderActionButton(
            String text
    ) {

        JButton button =
                new JButton(
                        text
                );


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


        button.setCursor(
                Cursor.getPredefinedCursor(
                        Cursor.HAND_CURSOR
                )
        );


        int width =
                switch (text) {

                    case "Reset", "Setup" ->
                            68;

                    case "Endgame" ->
                            78;

                    case "Copy FEN" ->
                            86;

                    default ->
                            86;
                };


        button.setPreferredSize(
                new Dimension(
                        width,
                        40
                )
        );


        button.setMargin(
                new Insets(
                        0,
                        12,
                        0,
                        12
                )
        );


        return button;
    }


    private JLabel createThemeIcon(
            String symbol
    ) {

        JLabel icon =
                new JLabel(
                        symbol
                );


        icon.setForeground(
                symbol.equals(
                        "☾"
                )
                        ? new Color(
                        190,
                        198,
                        207
                )
                        : new Color(
                        232,
                        193,
                        74
                )
        );


        icon.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.PLAIN,
                        18
                )
        );


        return icon;
    }


    private JPanel createThemeSegment(
            JLabel icon,
            JButton button
    ) {

        JPanel segment =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.CENTER,
                                6,
                                5
                        )
                );


        segment.setOpaque(
                false
        );


        segment.setCursor(
                Cursor.getPredefinedCursor(
                        Cursor.HAND_CURSOR
                )
        );


        icon.setCursor(
                Cursor.getPredefinedCursor(
                        Cursor.HAND_CURSOR
                )
        );


        segment.add(
                icon
        );


        segment.add(
                button
        );


        MouseAdapter toggleTheme =
                new MouseAdapter() {

                    @Override
                    public void mouseClicked(
                            MouseEvent event
                    ) {

                        button.doClick();
                    }
                };


        segment.addMouseListener(
                toggleTheme
        );


        icon.addMouseListener(
                toggleTheme
        );


        return segment;
    }


    private JButton createThemeButton(
            String text,
            boolean dark
    ) {

        JButton button =
                new JButton(
                        text
                );


        button.setFocusPainted(
                false
        );


        button.setCursor(
                Cursor.getPredefinedCursor(
                        Cursor.HAND_CURSOR
                )
        );


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


        button.setRolloverEnabled(
                false
        );


        button.addActionListener(
                event -> applyTheme(
                        dark
                )
        );


        return button;
    }


    private void applyTheme(
            boolean dark
    ) {

        darkTheme =
                dark;


        Color appBackground =
                dark
                        ? new Color(15, 21, 27)
                        : new Color(238, 241, 245);


        Color headerBackground =
                dark
                        ? new Color(12, 18, 24)
                        : new Color(255, 255, 255);


        Color border =
                dark
                        ? new Color(42, 53, 64)
                        : new Color(210, 216, 224);


        Color primary =
                dark
                        ? new Color(242, 244, 247)
                        : new Color(31, 35, 41);


        Color secondary =
                dark
                        ? new Color(164, 173, 184)
                        : new Color(100, 107, 117);


        Color selected =
                dark
                        ? new Color(31, 62, 94)
                        : new Color(64, 100, 145);


        Color unselected =
                dark
                        ? headerBackground
                        : new Color(246, 248, 250);


        getContentPane().setBackground(
                appBackground
        );


        if (workspace != null) {

            workspace.setBackground(
                    appBackground
            );


            workspace.setBorder(
                    BorderFactory.createEmptyBorder()
            );
        }


        if (boardArea != null) {

            boardArea.setBackground(
                    appBackground
            );


            boardPanel.setBackground(
                    appBackground
            );


            boardArea.setBorder(
                    BorderFactory.createEmptyBorder(
                            20,
                            20,
                            20,
                            12
                    )
            );
        }


        if (applicationHeader != null) {

            applicationHeader.setBackground(
                    headerBackground
            );


            applicationHeader.setBorder(
                    BorderFactory.createCompoundBorder(
                            BorderFactory.createMatteBorder(
                                    0,
                                    0,
                                    1,
                                    0,
                                    border
                            ),
                            BorderFactory.createEmptyBorder(
                                    10,
                                    20,
                                    10,
                                    18
                            )
                    )
            );
        }


        if (headerTitleLabel != null) {

            headerTitleLabel.setForeground(
                    primary
            );
        }


        if (headerSubtitleLabel != null) {

            headerSubtitleLabel.setForeground(
                    secondary
            );
        }


        styleThemeButton(
                darkThemeButton,
                dark,
                selected,
                unselected,
                primary,
                border
        );


        styleThemeButton(
                lightThemeButton,
                !dark,
                selected,
                unselected,
                primary,
                border
        );


        if (themeControls != null) {

            themeControls.setBorder(
                    BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(
                                    border,
                                    1,
                                    true
                            ),
                            BorderFactory.createEmptyBorder(
                                    2,
                                    8,
                                    2,
                                    8
                            )
                    )
            );
        }


        Color moonIconColor =
                dark
                        ? new Color(
                        190,
                        198,
                        207
                )
                        : new Color(
                        105,
                        112,
                        122
                );


        Color sunIconColor =
                new Color(
                        232,
                        193,
                        74
                );


        if (darkThemeIcon != null) {

            darkThemeIcon.setForeground(
                    moonIconColor
            );
        }


        if (lightThemeIcon != null) {

            lightThemeIcon.setForeground(
                    sunIconColor
            );
        }


        styleHeaderActionButton(
                loadFenButton,
                primary,
                unselected,
                border
        );


        styleHeaderActionButton(
                copyFenButton,
                primary,
                unselected,
                border
        );


        styleHeaderActionButton(
                resetPositionButton,
                primary,
                unselected,
                border
        );


        styleHeaderActionButton(
                setupPositionButton,
                primary,
                unselected,
                border
        );


        styleHeaderActionButton(
                endgameButton,
                primary,
                unselected,
                border
        );


        if (piecePalettePanel != null) {

            piecePalettePanel.applyTheme(
                    dark
            );
        }


        if (setupPanel != null) {

            setupPanel.applyTheme(
                    dark
            );
        }


        if (endgameStudyPanel != null) {

            endgameStudyPanel.applyTheme(
                    dark
            );
        }


        if (versionBadge != null) {

            versionBadge.setBackground(
                    unselected
            );


            versionBadge.setBorder(
                    BorderFactory.createLineBorder(
                            border,
                            1,
                            true
                    )
            );
        }


        if (versionBadgeLabel != null) {

            versionBadgeLabel.setForeground(
                    dark
                            ? new Color(117, 176, 255)
                            : new Color(64, 100, 145)
            );
        }


        analysisPanel.setDarkTheme(
                dark
        );


        revalidate();
        repaint();
    }


    private void styleThemeButton(
            JButton button,
            boolean selected,
            Color selectedBackground,
            Color normalBackground,
            Color foreground,
            Color border
    ) {

        if (button == null) {

            return;
        }


        button.setForeground(
                foreground
        );


        button.setBackground(
                normalBackground
        );


        button.setBorder(
                BorderFactory.createEmptyBorder(
                        5,
                        5,
                        5,
                        8
                )
        );


        button.setContentAreaFilled(
                false
        );


        button.setOpaque(
                false
        );
    }


    // =========================================================
    // Move selection
    // =========================================================

    private void handleMoveSelection(
            MoveAnalysis move
    ) {

        previewMove(
                move
        );


        retargetSearchBias();
    }


    // =========================================================
    // Continuation selection
    // =========================================================

    private void handleVariationSelection(
            VariationNode variation
    ) {

        previewVariation(
                variation
        );


        /*
         * A PV button may preview a node without changing the
         * selected card path.
         */
        if (analysisPanel.getPathDepth() > 0) {

            retargetSearchBias();
        }
    }


    // =========================================================
    // Search bias
    // =========================================================

    /*
     * This no longer restarts search.
     *
     * It only changes which subtree receives bonus scheduler
     * turns while global dovetailing continues.
     */
    private void retargetSearchBias() {

        if (!engine.hasActiveAnalysis()) {

            return;
        }


        Position selected =
                analysisPanel.getSelectedPosition();


        if (analysisPanel.getPathDepth() > 0
                &&
                selected != null) {

            engine.setExplorationFocus(
                    selected
            );


        } else {

            engine.clearExplorationFocus();
        }


        restartGuiExplorationChain();
    }


    // =========================================================
    // Always-on GUI search chain
    // =========================================================

    private void startGuiExplorationChain() {

        if (!engine.hasActiveAnalysis()) {

            return;
        }


        automaticExplorationActive =
                true;


        long generation =
                explorationGeneration;


        runExplorationBatch(
                generation
        );
    }


    private void restartGuiExplorationChain() {

        /*
         * Engine scheduler is untouched.
         *
         * Only invalidate any old SwingWorker completion callback.
         */
        explorationGeneration++;


        automaticExplorationActive =
                true;


        long generation =
                explorationGeneration;


        runExplorationBatch(
                generation
        );
    }


    private void stopGuiExplorationChain() {

        automaticExplorationActive =
                false;


        explorationGeneration++;


        analysisPanel.setExploring(
                false
        );
    }


    // =========================================================
    // One persistent-scheduler batch
    // =========================================================

    private void runExplorationBatch(
            long generation
    ) {

        if (!automaticExplorationActive
                ||
                generation
                        != explorationGeneration) {

            return;
        }


        /*
         * Save the logical selected path only for GUI rebuilding.
         *
         * This does NOT determine where the search starts.
         * The persistent scheduler already knows where it left off.
         */
        List<String> savedPath =
                new ArrayList<>(
                        analysisPanel.getSelectedPathSan()
                );


        analysisPanel.setExploring(
                true
        );


        SwingWorker<
                PositionAnalysis,
                Void
                > worker =

                new SwingWorker<>() {


                    @Override
                    protected PositionAnalysis
                    doInBackground() {

                        /*
                         * First spend a meaningful chunk of time on
                         * the persistent dovetail itself.
                         */
                        engine.advanceExplorationWork(
                                AUTO_WORK_UNITS
                        );


                        /*
                         * Then pay the expensive graph-propagation
                         * and GUI-analysis cost once.
                         */
                        return engine.createAnalysisSnapshot();
                    }


                    @Override
                    protected void done() {

                        if (!automaticExplorationActive
                                ||
                                generation
                                        != explorationGeneration) {

                            return;
                        }


                        try {

                            PositionAnalysis result =
                                    get();


                            currentAnalysis =
                                    result;


                            if (endgameStudyMode) {

                                updateEndgameStudyProofState();
                            }


                            // =========================================
                            // Preserve selected line if one exists
                            // =========================================

                            if (savedPath.isEmpty()) {

                                analysisPanel.setAnalysis(
                                        result,
                                        gameHistory.size() > 1
                                );


                                previewHistory.clear();


                                /*
                                 * No analysis preview exists here.
                                 * Do not call clearPreview() repeatedly
                                 * during background refreshes because it
                                 * can erase a piece selection.
                                 */
                                if (boardPanel.isPreviewing()) {

                                    boardPanel.clearPreview();
                                }


                                evaluationBar.setAnalysis(
                                        result.getSearchValue(),
                                        result.getOutcome(),
                                        result.getMateDistance()
                                );


                            } else {

                                analysisPanel.restorePath(
                                        result,
                                        savedPath,
                                        gameHistory.size() > 1
                                );


                                rebuildPreviewHistoryFromPanel();


                                Position selected =
                                        analysisPanel.getSelectedPosition();


                                /*
                                 * previewHistory is the single authority
                                 * for whether the board is in analysis
                                 * preview mode.
                                 *
                                 * A restored AnalysisPanel path can contain
                                 * already-committed manual moves. Those
                                 * moves must NEVER be turned back into a
                                 * board preview during an automatic refresh.
                                 */
                                if (!previewHistory.isEmpty()) {

                                    PreviewState state =
                                            previewHistory.get(
                                                    previewHistory.size() - 1
                                            );


                                    showPreviewState(
                                            state
                                    );


                                } else if (selected != null) {

                                    /*
                                     * The selected AnalysisPanel endpoint
                                     * belongs entirely to the committed
                                     * game path. Keep the real board
                                     * interactive.
                                     */
                                    if (boardPanel.isPreviewing()) {

                                        boardPanel.clearPreview();
                                    }


                                    if (samePosition(
                                            selected,
                                            boardPanel.getPosition()
                                    )) {

                                        evaluationBar.setAnalysis(
                                                analysisPanel
                                                        .getSelectedSearchValue(),

                                                analysisPanel
                                                        .getSelectedOutcome(),

                                                analysisPanel
                                                        .getSelectedMateDistance()
                                        );


                                    } else if (currentAnalysis != null) {

                                        /*
                                         * A live snapshot can briefly restore
                                         * only part of a selected path while
                                         * deeper public GUI nodes are being
                                         * regenerated. Never let that stale
                                         * endpoint replace the real board.
                                         */
                                        evaluationBar.setAnalysis(
                                                currentAnalysis.getSearchValue(),
                                                currentAnalysis.getOutcome(),
                                                currentAnalysis.getMateDistance()
                                        );
                                    }


                                } else {

                                    /*
                                     * Path disappeared from the public
                                     * analysis tree. Return to root view
                                     * and remove focus bias.
                                     */
                                    engine.clearExplorationFocus();


                                    previewHistory.clear();


                                    boardPanel.clearPreview();


                                    evaluationBar.setAnalysis(
                                            result.getSearchValue(),
                                            result.getOutcome(),
                                            result.getMateDistance()
                                    );
                                }
                            }


                            updateBackButton();


                            if (!engine.hasMoreExplorationWork()) {

                                automaticExplorationActive =
                                        false;


                                analysisPanel.setExploring(
                                        false
                                );


                                return;
                            }


                            scheduleNextBatch(
                                    generation
                            );


                        } catch (Exception exception) {

                            automaticExplorationActive =
                                    false;


                            analysisPanel.setExploring(
                                    false
                            );


                            exception.printStackTrace();


                            JOptionPane.showMessageDialog(
                                    ChessWindow.this,

                                    "Automatic exploration failed:\n"
                                            + getUsefulMessage(
                                            exception
                                    ),

                                    "Chess Engine",

                                    JOptionPane.ERROR_MESSAGE
                            );


                            updateBackButton();
                        }
                    }
                };


        worker.execute();
    }


    // =========================================================
    // Schedule next batch
    // =========================================================

    private void scheduleNextBatch(
            long generation
    ) {

        Timer timer =
                new Timer(
                        AUTO_BATCH_DELAY_MS,
                        event -> {

                            if (!automaticExplorationActive
                                    ||
                                    generation
                                            != explorationGeneration) {

                                return;
                            }


                            runExplorationBatch(
                                    generation
                            );
                        }
                );


        timer.setRepeats(
                false
        );


        timer.start();
    }


    // =========================================================
    // Unified Back
    // =========================================================

    private void handleBack() {

        if (!previewHistory.isEmpty()) {

            previewHistory.remove(
                    previewHistory.size() - 1
            );


            analysisPanel.goBackOneLevel();


            if (previewHistory.isEmpty()) {

                restoreActualPositionView();


            } else {

                PreviewState previous =
                        previewHistory.get(
                                previewHistory.size() - 1
                        );


                showPreviewState(
                        previous
                );


                analysisPanel.restoreHeaderForCurrentPath();
            }


            /*
             * Back changes bias only.
             */
            retargetSearchBias();


            updateBackButton();


            return;
        }


        if (gameHistory.size() > 1) {

            undoManualMove();
        }
    }


    private void updateBackButton() {

        analysisPanel.setBackEnabled(
                !previewHistory.isEmpty()
                        ||
                        gameHistory.size() > 1
        );
    }


    // =========================================================
    // Promote selected preview path before a manual move
    // =========================================================

    private void commitPreviewForManualPlay(
            Position previewPosition
    ) {

        if (previewPosition == null) {

            return;
        }


        List<AnalysisPanel.PreviewData> selectedPath =
                analysisPanel.getSelectedPathPreviewData();


        if (selectedPath == null
                ||
                selectedPath.isEmpty()) {

            return;
        }


        /*
         * Find the longest prefix shared by:
         *
         *     gameHistory[1..]
         *
         * and the selected analysis-card path.
         *
         * This matters when the user previews a branch that diverges
         * from already-committed play. We must truncate the old
         * committed suffix before promoting the new branch.
         */
        int commonDepth =
                getCommittedSelectedPrefixLength(
                        selectedPath
                );


        while (gameHistory.size()
                > commonDepth + 1) {

            gameHistory.remove(
                    gameHistory.size() - 1
            );
        }


        /*
         * Promote every selected card beyond the common committed
         * prefix. The user's actual manual move will be appended by
         * handleRealPositionChange(...) immediately afterward.
         */
        for (int index = commonDepth;
             index < selectedPath.size();
             index++) {

            Position pathPosition =
                    selectedPath.get(
                            index
                    ).getPosition();


            if (pathPosition == null) {

                break;
            }


            Position lastCommitted =
                    gameHistory.get(
                            gameHistory.size() - 1
                    );


            if (!samePosition(
                    lastCommitted,
                    pathPosition
            )) {

                gameHistory.add(
                        pathPosition
                );
            }
        }


        /*
         * The selected card path is committed now, so there is no
         * remaining preview suffix. ChessBoardPanel is in the middle
         * of executing the user's move and will leave preview mode
         * itself.
         */
        previewHistory.clear();


        updateBackButton();
    }


    // =========================================================
    // Manual move
    // =========================================================

    private void handleRealPositionChange(
            Position position
    ) {

        if (position == null) {

            return;
        }


        if (endgameStudyMode) {

            if (endgameStudyReady) {

                endgameStudyPanel.setStatus(
                        "Move played — verifying continuation."
                );

            } else {

                endgameStudyPanel.setStatus(
                        "Exact proof is still being established."
                );
            }
        }


        /*
         * IMPORTANT:
         *
         * A manual move is NOT a new search.
         *
         * The move is simply a committed traversal through the
         * already-active PositionGraph.  This preserves every bit
         * of generated / explored / solved progress at the old root.
         *
         * It also makes the manual move appear in AnalysisPanel's
         * selected-line stack exactly like a clicked analysis move.
         */
        /*
         * The GUI path is only a view of the persistent graph.
         *
         * Before asking AnalysisPanel to append the new card, ensure
         * that the manually played legal move exists as an actual
         * graph edge. This removes the old dependency on whether the
         * continuation had already been materialized by the GUI.
         */
        Position parentPosition =
                gameHistory.get(
                        gameHistory.size() - 1
                );


        boolean representedInGraph =
                engine.ensureManualContinuation(
                        parentPosition,
                        position
                );


        boolean addedToPath =
                analysisPanel.commitPositionToPath(
                        position
                );


        if (representedInGraph
                &&
                !addedToPath) {

            /*
             * This should now be rare. If it happens, it means the
             * graph contains the manual edge but AnalysisPanel could
             * not mirror it, which gives us a precise diagnostic.
             */
            System.out.println(
                    "Manual move graph edge exists, "
                            + "but analysis path commit failed."
            );
        }


        gameHistory.add(
                position
        );


        previewHistory.clear();


        /*
         * executeMove() normally leaves preview mode itself. Avoid
         * unnecessary clearPreview() calls because they also clear
         * board selection state.
         */
        if (boardPanel.isPreviewing()) {
            boardPanel.clearPreview();
        }


        /*
         * Keep the same currentAnalysis and the same engine graph.
         *
         * If the position was already represented in the public
         * analysis tree, commitPositionToPath() has added its card
         * and connector to the visible path.
         */
        if (addedToPath) {

            evaluationBar.setAnalysis(
                    analysisPanel.getSelectedSearchValue(),
                    analysisPanel.getSelectedOutcome(),
                    analysisPanel.getSelectedMateDistance()
            );
        }


        /*
         * Manual navigation changes only the scheduler's focus.
         * Global dovetailing continues across the whole graph.
         */
        retargetSearchBias();


        updateBackButton();
    }


    // =========================================================
    // Undo manual move
    // =========================================================

    private void undoManualMove() {

        if (gameHistory.size() <= 1) {

            return;
        }


        /*
         * Remove only the committed manual position.
         *
         * DO NOT call engine.analyze(...).
         * DO NOT create a new PositionGraph.
         * DO NOT recreate the scheduler.
         */
        gameHistory.remove(
                gameHistory.size() - 1
        );


        Position previous =
                gameHistory.get(
                        gameHistory.size() - 1
                );


        previewHistory.clear();


        boardPanel.clearPreview();


        /*
         * The visible analysis path mirrors the committed manual
         * move history, so Back removes exactly one path card.
         */
        analysisPanel.goBackOneLevel();


        boardPanel.setPosition(
                previous
        );


        /*
         * boardPanel.setPosition(...) is programmatic and should not
         * represent a new manual move.  The search remains untouched.
         */
        Position selected =
                analysisPanel.getSelectedPosition();


        if (selected != null) {

            evaluationBar.setAnalysis(
                    analysisPanel.getSelectedSearchValue(),
                    analysisPanel.getSelectedOutcome(),
                    analysisPanel.getSelectedMateDistance()
            );

        } else if (currentAnalysis != null) {

            evaluationBar.setAnalysis(
                    currentAnalysis.getSearchValue(),
                    currentAnalysis.getOutcome(),
                    currentAnalysis.getMateDistance()
            );
        }


        retargetSearchBias();


        updateBackButton();
    }


    // =========================================================
    // Preview root move
    // =========================================================

    private void previewMove(
            MoveAnalysis move
    ) {

        if (move == null
                ||
                move.getPosition() == null) {

            return;
        }


        /*
         * Rebuild from AnalysisPanel instead of assuming this card is
         * the whole preview path. This preserves the invariant that
         * previewHistory contains ONLY positions beyond the committed
         * gameHistory prefix.
         */
        rebuildPreviewHistoryFromPanel();


        if (!previewHistory.isEmpty()) {

            showPreviewState(
                    previewHistory.get(
                            previewHistory.size() - 1
                    )
            );


        } else {

            if (boardPanel.isPreviewing()) {

                boardPanel.clearPreview();
            }
        }


        updateBackButton();
    }


    // =========================================================
    // Preview variation
    // =========================================================

    private void previewVariation(
            VariationNode variation
    ) {

        if (variation == null
                ||
                variation.getPosition() == null) {

            return;
        }


        rebuildPreviewHistoryFromPanel();


        if (!previewHistory.isEmpty()) {

            showPreviewState(
                    previewHistory.get(
                            previewHistory.size() - 1
                    )
            );


        } else {

            if (boardPanel.isPreviewing()) {

                boardPanel.clearPreview();
            }
        }


        updateBackButton();
    }


    // =========================================================
    // Rebuild preview history
    // =========================================================

    private void rebuildPreviewHistoryFromPanel() {

        previewHistory.clear();


        List<AnalysisPanel.PreviewData> selectedPath =
                analysisPanel.getSelectedPathPreviewData();


        if (selectedPath == null
                ||
                selectedPath.isEmpty()) {

            return;
        }


        /*
         * Only the suffix beyond the committed game path is preview
         * navigation.
         *
         * Example:
         *
         * committed: e4, e5, Nf3
         * selected:  e4, e5, Nf3, Nc6, Bb5
         *
         * previewHistory becomes:
         *
         * Nc6, Bb5
         *
         * A divergent selected line also works correctly because we
         * use the longest matching prefix instead of merely skipping
         * gameHistory.size() entries.
         */
        int commonDepth =
                getCommittedSelectedPrefixLength(
                        selectedPath
                );


        for (int index = commonDepth;
             index < selectedPath.size();
             index++) {

            AnalysisPanel.PreviewData data =
                    selectedPath.get(
                            index
                    );


            if (data.getPosition() == null) {

                break;
            }


            previewHistory.add(
                    new PreviewState(
                            data.getPosition(),
                            data.getSearchValue(),
                            data.getOutcome(),
                            data.getMateDistance()
                    )
            );
        }
    }


    private int getCommittedSelectedPrefixLength(
            List<AnalysisPanel.PreviewData> selectedPath
    ) {

        if (selectedPath == null
                ||
                selectedPath.isEmpty()) {

            return 0;
        }


        int committedMoveCount =
                gameHistory.size() - 1;


        int maximumComparable =
                Math.min(
                        committedMoveCount,
                        selectedPath.size()
                );


        int commonDepth =
                0;


        while (commonDepth < maximumComparable) {

            Position committedPosition =
                    gameHistory.get(
                            commonDepth + 1
                    );


            Position selectedPosition =
                    selectedPath.get(
                            commonDepth
                    ).getPosition();


            if (!samePosition(
                    committedPosition,
                    selectedPosition
            )) {

                break;
            }


            commonDepth++;
        }


        return commonDepth;
    }


    // =========================================================
    // Display preview state
    // =========================================================

    private void showPreviewState(
            PreviewState state
    ) {

        if (state == null
                ||
                state.position == null) {

            return;
        }


        boardPanel.setPreviewPosition(
                state.position
        );


        evaluationBar.setAnalysis(
                state.searchValue,
                state.outcome,
                state.mateDistance
        );
    }


    // =========================================================
    // Actual board view
    // =========================================================

    private void restoreActualPositionView() {

        previewHistory.clear();


        if (boardPanel.isPreviewing()) {

            boardPanel.clearPreview();
        }


        analysisPanel.restoreHeaderForCurrentPath();


        if (currentAnalysis != null) {

            evaluationBar.setAnalysis(
                    currentAnalysis.getSearchValue(),
                    currentAnalysis.getOutcome(),
                    currentAnalysis.getMateDistance()
            );


        } else {

            evaluationBar.setAnalysis(
                    0,
                    SearchOutcome.UNKNOWN,
                    -1
            );
        }


        updateBackButton();
    }


    // =========================================================
    // Free board setup
    // =========================================================

    private void openEndgameGenerator() {

        if (boardPanel.isSetupMode()) {
            cancelPositionSetup();
        }


        String[] pieceCounts = {
                "3",
                "4",
                "5",
                "6",
                "7"
        };


        JComboBox<String> modeBox =
                new JComboBox<>(
                        new String[] {
                                "Fixed",
                                "Variable"
                        }
                );


        JComboBox<String> fixedPieceBox =
                new JComboBox<>(
                        pieceCounts
                );


        JComboBox<String> variableMinimumBox =
                new JComboBox<>(
                        pieceCounts
                );


        JComboBox<String> variableMaximumBox =
                new JComboBox<>(
                        pieceCounts
                );


        if (lastEndgameSettings.isFixed()) {

            modeBox.setSelectedItem(
                    "Fixed"
            );

            fixedPieceBox.setSelectedItem(
                    Integer.toString(
                            lastEndgameSettings.minimumPieces()
                    )
            );

        } else {

            modeBox.setSelectedItem(
                    "Variable"
            );

            variableMinimumBox.setSelectedItem(
                    Integer.toString(
                            lastEndgameSettings.minimumPieces()
                    )
            );

            variableMaximumBox.setSelectedItem(
                    Integer.toString(
                            lastEndgameSettings.maximumPieces()
                    )
            );
        }


        JPanel fixedPanel =
                new JPanel(
                        new GridLayout(
                                1,
                                2,
                                10,
                                10
                        )
                );


        fixedPanel.add(
                new JLabel(
                        "Pieces"
                )
        );

        fixedPanel.add(
                fixedPieceBox
        );


        JPanel variablePanel =
                new JPanel(
                        new GridLayout(
                                2,
                                2,
                                10,
                                10
                        )
                );


        variablePanel.add(
                new JLabel(
                        "Minimum"
                )
        );

        variablePanel.add(
                variableMinimumBox
        );

        variablePanel.add(
                new JLabel(
                        "Maximum"
                )
        );

        variablePanel.add(
                variableMaximumBox
        );


        CardLayout modeLayout =
                new CardLayout();


        JPanel modeOptions =
                new JPanel(
                        modeLayout
                );


        modeOptions.add(
                fixedPanel,
                "Fixed"
        );

        modeOptions.add(
                variablePanel,
                "Variable"
        );


        modeBox.addActionListener(
                event -> {

                    String selectedMode =
                            (String)
                                    modeBox.getSelectedItem();


                    modeLayout.show(
                            modeOptions,
                            selectedMode
                    );
                }
        );


        String initialMode =
                (String)
                        modeBox.getSelectedItem();


        modeLayout.show(
                modeOptions,
                initialMode
        );


        JPanel modeSelector =
                new JPanel(
                        new GridLayout(
                                1,
                                2,
                                10,
                                10
                        )
                );


        modeSelector.add(
                new JLabel(
                        "Piece count"
                )
        );

        modeSelector.add(
                modeBox
        );


        JPanel optionsPanel =
                new JPanel();

        optionsPanel.setLayout(
                new BoxLayout(
                        optionsPanel,
                        BoxLayout.Y_AXIS
                )
        );


        optionsPanel.add(
                modeSelector
        );

        optionsPanel.add(
                Box.createVerticalStrut(
                        12
                )
        );

        optionsPanel.add(
                modeOptions
        );


        int choice =
                JOptionPane.showConfirmDialog(
                        this,
                        optionsPanel,
                        "Generate Endgame",
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.PLAIN_MESSAGE
                );


        if (choice
                != JOptionPane.OK_OPTION) {
            return;
        }


        String selectedMode =
                (String)
                        modeBox.getSelectedItem();


        EndgameSettings settings;


        if ("Fixed".equals(
                selectedMode
        )) {

            int pieces =
                    Integer.parseInt(
                            (String)
                                    fixedPieceBox.getSelectedItem()
                    );


            settings =
                    EndgameSettings.fixed(
                            pieces
                    );

        } else {

            int minimum =
                    Integer.parseInt(
                            (String)
                                    variableMinimumBox.getSelectedItem()
                    );

            int maximum =
                    Integer.parseInt(
                            (String)
                                    variableMaximumBox.getSelectedItem()
                    );


            if (maximum < minimum) {

                JOptionPane.showMessageDialog(
                        this,
                        "Maximum pieces must be at least the minimum pieces.",
                        "Variable Piece Count",
                        JOptionPane.WARNING_MESSAGE
                );

                return;
            }


            settings =
                    EndgameSettings.variable(
                            minimum,
                            maximum
                    );
        }


        generateEndgame(
                settings
        );
    }


    private void generateEndgame(
            EndgameSettings settings
    ) {

        try {

            Position position =
                    endgameGenerator.generate(
                            settings
                    );


            lastEndgameSettings =
                    settings;


            loadEndgameStudyPosition(
                    position,
                    settings
            );

        } catch (RuntimeException exception) {

            JOptionPane.showMessageDialog(
                    this,
                    exception.getMessage(),
                    "Endgame Generator",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }


    private void loadEndgameStudyPosition(
            Position position,
            EndgameSettings settings
    ) {

        if (position == null) {

            return;
        }


        /*
         * This is intentionally NOT loadFenPosition().
         *
         * loadFenPosition() launches the ordinary GUI analysis chain.
         * Endgame Study instead installs the position and immediately
         * launches its dedicated proof worker.
         */
        stopGuiExplorationChain();


        analysisRequestId++;


        endgameProofGeneration++;


        long proofGeneration =
                endgameProofGeneration;


        previewHistory.clear();


        gameHistory.clear();


        gameHistory.add(
                position
        );


        boardPanel.clearPreview();


        boardPanel.setPosition(
                position
        );


        boardPanel.revalidate();
        boardPanel.repaint();


        analysisPanel.clearMoveSelection();


        currentAnalysis =
                null;


        evaluationBar.setAnalysis(
                0,
                SearchOutcome.UNKNOWN,
                -1
        );


        updateBackButton();


        enterEndgameStudyMode(
                position,
                settings
        );


        /*
         * Start proving NOW — no click or first move is required.
         */
        startEndgameProof(
                position,
                settings,
                proofGeneration
        );
    }


    private void startEndgameProof(
            Position position,
            EndgameSettings settings,
            long proofGeneration
    ) {

        endgameStudyPanel.setProving();


        SwingWorker<
                EndgameStudyEligibility,
                Void
                > worker =

                new SwingWorker<>() {


                    @Override
                    protected EndgameStudyEligibility
                    doInBackground() {

                        /*
                         * Build the fresh graph and its persistent
                         * scheduler immediately for the generated root.
                         */
                        engine.analyze(
                                position
                        );


                        engine.setExplorationFocus(
                                position
                        );


                        EndgameStudyEligibility eligibility =
                                EndgameStudyEligibility.fromRoot(
                                        engine.getActiveRoot()
                                );


                        while (proofGeneration
                                == endgameProofGeneration
                                &&
                                eligibility.status()
                                        == EndgameStudyEligibility.Status.PROVING) {

                            engine.advanceEndgameProofWork(
                                    ENDGAME_PROOF_WORK_UNITS
                            );


                            eligibility =
                                    EndgameStudyEligibility.fromRoot(
                                            engine.getActiveRoot()
                                    );
                        }


                        return eligibility;
                    }


                    @Override
                    protected void done() {

                        if (proofGeneration
                                != endgameProofGeneration
                                ||
                                !endgameStudyMode) {

                            return;
                        }


                        try {

                            EndgameStudyEligibility eligibility =
                                    get();


                            switch (eligibility.status()) {

                                case PROVEN_MATE -> {

                                    endgameStudyReady =
                                            true;


                                    endgameStudyPanel.setProvenMate(
                                            eligibility.mateInMoves()
                                    );


                                    boardPanel.setEnabled(
                                            true
                                    );


                                    /*
                                     * Build the normal public analysis only
                                     * once, after the exact proof exists.
                                     * It remains hidden until Give Up /
                                     * review is requested.
                                     */
                                    currentAnalysis =
                                            engine.createAnalysisSnapshot();
                                }


                                case PROVEN_DRAW -> {

                                    rejectCurrentEndgame(
                                            "Exact search proved this position is drawn."
                                    );
                                }


                                case PROVEN_LOSS -> {

                                    rejectCurrentEndgame(
                                            "The side to move does not have a forced mate."
                                    );
                                }


                                case PROVING -> {

                                    /*
                                     * Reaching this branch means this worker
                                     * was superseded by a newer generated
                                     * position.  The generation guard above
                                     * normally returns before this point.
                                     */
                                }
                            }


                        } catch (Exception exception) {

                            exception.printStackTrace();


                            endgameStudyPanel.setStatus(
                                    "Endgame proof failed: "
                                            + getUsefulMessage(
                                            exception
                                    )
                            );
                        }
                    }
                };


        worker.execute();
    }


    private void enterEndgameStudyMode(
            Position position,
            EndgameSettings settings
    ) {

        endgameStudyMode =
                true;


        endgameStudyReady =
                false;


        regeneratingRejectedEndgame =
                false;


        boardPanel.setEnabled(
                false
        );


        evaluationBar.setVisible(
                false
        );


        analysisPanel.setVisible(
                false
        );


        setupPanel.setVisible(
                false
        );


        endgameStudyPanel.setVisible(
                true
        );


        endgameStudyPanel.setPosition(
                position,
                settings
        );


        headerSubtitleLabel.setText(
                settings.displayName()
                        + " endgame study"
        );


        analysisArea.revalidate();
        analysisArea.repaint();
    }


    private void updateEndgameStudyProofState() {

        if (!endgameStudyMode) {

            return;
        }


        EndgameStudyEligibility eligibility =
                EndgameStudyEligibility.fromRoot(
                        engine.getActiveRoot()
                );


        switch (eligibility.status()) {

            case PROVING -> {

                endgameStudyReady =
                        false;


                endgameStudyPanel.setProving();
            }


            case PROVEN_MATE -> {

                endgameStudyReady =
                        true;


                endgameStudyPanel.setProvenMate(
                        eligibility.mateInMoves()
                );


                boardPanel.setEnabled(
                        true
                );
            }


            case PROVEN_DRAW -> {

                rejectCurrentEndgame(
                        "Exact search proved this position is drawn."
                );
            }


            case PROVEN_LOSS -> {

                rejectCurrentEndgame(
                        "The side to move does not have a forced mate."
                );
            }
        }
    }


    private void rejectCurrentEndgame(
            String reason
    ) {

        if (regeneratingRejectedEndgame) {

            return;
        }


        regeneratingRejectedEndgame =
                true;


        endgameStudyReady =
                false;


        endgameStudyPanel.setRejected(
                reason
        );


        SwingUtilities.invokeLater(
                () -> {

                    if (!endgameStudyMode) {

                        regeneratingRejectedEndgame =
                                false;

                        return;
                    }


                    generateEndgame(
                            lastEndgameSettings
                    );
                }
        );
    }


    private void revealEndgameAnalysis() {

        endgameStudyMode =
                false;


        endgameProofGeneration++;


        boardPanel.setEnabled(
                true
        );


        endgameStudyPanel.setVisible(
                false
        );


        evaluationBar.setVisible(
                true
        );


        analysisPanel.setVisible(
                true
        );


        headerSubtitleLabel.setText(
                "Endgame analysis"
        );


        analysisArea.revalidate();
        analysisArea.repaint();
    }


    private void beginPositionSetup() {

        endgameProofGeneration++;


        endgameStudyMode =
                false;


        endgameStudyReady =
                false;


        boardPanel.setEnabled(
                true
        );



        if (boardPanel.isSetupMode()) {
            return;
        }


        stopGuiExplorationChain();


        analysisRequestId++;


        boardPanel.beginSetupMode();


        piecePalettePanel.setVisible(
                true
        );


        evaluationBar.setVisible(
                false
        );


        endgameStudyMode =
                false;


        endgameStudyPanel.setVisible(
                false
        );


        analysisPanel.setVisible(
                false
        );


        setupPanel.setVisible(
                true
        );


        refreshSetupPanel();


        headerSubtitleLabel.setText(
                "Position setup — drag pieces freely, then analyze"
        );


        boardArea.revalidate();
        boardArea.repaint();


        resizeForCurrentMode();
    }


    private void cancelPositionSetup() {

        if (!boardPanel.isSetupMode()) {
            return;
        }


        boardPanel.cancelSetupMode();


        piecePalettePanel.setVisible(
                false
        );


        evaluationBar.setVisible(
                true
        );


        setupPanel.setVisible(
                false
        );


        analysisPanel.setVisible(
                true
        );


        headerSubtitleLabel.setText(
                "Persistent graph exploration and tactical analysis"
        );


        boardArea.revalidate();
        boardArea.repaint();


        resizeForCurrentMode();


        analyzeCurrentPosition();
    }


    private void commitPositionSetup() {

        try {

            Position setupPosition =
                    boardPanel.finishSetupMode();


            piecePalettePanel.setVisible(
                    false
            );


            evaluationBar.setVisible(
                    true
            );


            setupPanel.setVisible(
                    false
            );


            analysisPanel.setVisible(
                    true
            );


            headerSubtitleLabel.setText(
                    "Persistent graph exploration and tactical analysis"
            );


            boardArea.revalidate();
            boardArea.repaint();


            resizeForCurrentMode();


            loadFenPosition(
                    setupPosition
            );


        } catch (IllegalArgumentException exception) {

            JOptionPane.showMessageDialog(
                    this,
                    exception.getMessage(),
                    "Invalid Setup Position",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }


    private void refreshSetupPanel() {

        if (setupPanel == null
                || !boardPanel.isSetupMode()) {
            return;
        }


        setupPanel.updateFromBoard(
                boardPanel.getSetupBoardSnapshot(),
                boardPanel.getSetupSideToMove()
        );
    }


    private void resizeForCurrentMode() {

        /*
         * pack() is useful here because the setup palette changes the
         * window's preferred height. Preserve a comfortable minimum
         * workspace while allowing setup mode to grow enough that the
         * piece palette is never clipped.
         */
        Dimension preferred =
                getPreferredSize();


        int targetWidth =
                Math.max(
                        1280,
                        preferred.width
                );


        int targetHeight =
                Math.max(
                        800,
                        preferred.height
                );


        setSize(
                targetWidth,
                targetHeight
        );


        setLocationRelativeTo(
                null
        );


        revalidate();
        repaint();
    }


    // =========================================================
    // Position controls
    // =========================================================

    private void copyCurrentFen() {

        try {

            String fen =
                    FenCodec.toFen(
                            boardPanel.getPosition()
                    );


            Toolkit
                    .getDefaultToolkit()
                    .getSystemClipboard()
                    .setContents(
                            new StringSelection(
                                    fen
                            ),
                            null
                    );


            System.out.println(
                    "Copied FEN: "
                            + fen
            );


        } catch (RuntimeException exception) {

            exception.printStackTrace();


            JOptionPane.showMessageDialog(
                    this,
                    "Could not copy FEN:\n"
                            + getUsefulMessage(
                            exception
                    ),
                    "Copy FEN",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }


    private void resetToStartingPosition() {

        int result =
                JOptionPane.showConfirmDialog(
                        this,
                        "Reset the board and analysis to the standard starting position?",
                        "Reset Position",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE
                );


        if (result != JOptionPane.YES_OPTION) {

            return;
        }


        Position startingPosition =
                createStandardStartingPosition();


        loadFenPosition(
                startingPosition
        );
    }


    private Position createStandardStartingPosition() {

        return FenCodec.parse(
                "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        );
    }


    // =========================================================
    // FEN loading
    // =========================================================

    private void showLoadFenDialog() {

        JTextField field =
                new JTextField(
                        54
                );


        field.setToolTipText(
                "Example: 8/8/8/8/8/8/4K3/7k w - - 0 1"
        );


        JPanel panel =
                new JPanel(
                        new BorderLayout(
                                0,
                                8
                        )
                );


        JLabel instructions =
                new JLabel(
                        "<html>Enter a six-field FEN position:<br>"
                                + "<span style='font-size:9px'>"
                                + "Example: 8/8/8/8/8/8/4K3/7k w - - 0 1"
                                + "</span></html>"
                );


        panel.add(
                instructions,
                BorderLayout.NORTH
        );


        panel.add(
                field,
                BorderLayout.CENTER
        );


        SwingUtilities.invokeLater(
                field::requestFocusInWindow
        );


        int result =
                JOptionPane.showConfirmDialog(
                        this,
                        panel,
                        "Load FEN",
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.PLAIN_MESSAGE
                );


        if (result != JOptionPane.OK_OPTION) {

            return;
        }


        String fen =
                field
                        .getText()
                        .trim();


        if (fen.isEmpty()) {

            JOptionPane.showMessageDialog(
                    this,
                    "Enter a FEN position first.",
                    "Load FEN",
                    JOptionPane.WARNING_MESSAGE
            );

            return;
        }


        try {

            Position loadedPosition =
                    FenCodec.parse(
                            fen
                    );


            /*
             * Normalize through the serializer before committing.
             * This gives us a concrete verification that the parser
             * produced the position the user actually entered.
             */
            String normalizedFen =
                    FenCodec.toFen(
                            loadedPosition
                    );


            System.out.println(
                    "Loading FEN: "
                            + normalizedFen
            );


            loadFenPosition(
                    loadedPosition
            );


            /*
             * Verify the board model immediately after the handoff.
             * If this ever differs, we want a precise diagnostic
             * instead of a silent GUI failure.
             */
            String boardFen =
                    FenCodec.toFen(
                            boardPanel.getPosition()
                    );


            if (!normalizedFen.equals(
                    boardFen
            )) {

                throw new IllegalStateException(
                        "FEN handoff mismatch. Parsed "
                                + normalizedFen
                                + " but board contains "
                                + boardFen
                );
            }


        } catch (RuntimeException exception) {

            exception.printStackTrace();


            JOptionPane.showMessageDialog(
                    this,
                    "Could not load FEN:\n"
                            + getUsefulMessage(
                            exception
                    ),
                    "Invalid FEN",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }


    private void loadFenPosition(
            Position position
    ) {

        if (position == null) {

            return;
        }


        endgameProofGeneration++;


        endgameStudyMode =
                false;


        endgameStudyReady =
                false;


        endgameStudyPanel.setVisible(
                false
        );


        boardPanel.setEnabled(
                true
        );


        /*
         * A loaded FEN is a completely new analysis root.
         *
         * FEN itself contains no previous move/repetition path, so
         * both visible navigation histories must begin fresh here.
         */
        stopGuiExplorationChain();


        analysisRequestId++;


        previewHistory.clear();


        gameHistory.clear();


        gameHistory.add(
                position
        );


        boardPanel.clearPreview();


        boardPanel.setPosition(
                position
        );


        boardPanel.revalidate();
        boardPanel.repaint();


        /*
         * The FEN dialog is modal. Queue one more repaint for the
         * next Swing event-loop turn so the newly loaded board is
         * guaranteed to be painted after the dialog disappears.
         */
        SwingUtilities.invokeLater(
                () -> {

                    boardPanel.revalidate();
                    boardPanel.repaint();
                }
        );


        analysisPanel.clearMoveSelection();


        currentAnalysis =
                null;


        evaluationBar.setAnalysis(
                0,
                SearchOutcome.UNKNOWN,
                -1
        );


        updateBackButton();


        System.out.println(
                "Board root after FEN load: "
                        + FenCodec.toFen(
                        boardPanel.getPosition()
                )
        );


        analyzeCurrentPosition();
    }


    // =========================================================
    // Initial analysis
    // =========================================================

    private void analyzeCurrentPosition() {

        stopGuiExplorationChain();


        Position position =
                boardPanel.getPosition();


        if (position == null) {

            return;
        }


        previewHistory.clear();


        boardPanel.clearPreview();


        analysisPanel.clearMoveSelection();


        currentAnalysis =
                null;


        evaluationBar.setAnalysis(
                0,
                SearchOutcome.UNKNOWN,
                -1
        );


        long requestId =
                ++analysisRequestId;


        analysisPanel.setAnalyzing(
                gameHistory.size() > 1
        );


        SwingWorker<
                PositionAnalysis,
                Void
                > worker =

                new SwingWorker<>() {


                    @Override
                    protected PositionAnalysis
                    doInBackground() {

                        return engine.analyze(
                                position
                        );
                    }


                    @Override
                    protected void done() {

                        if (requestId
                                != analysisRequestId) {

                            return;
                        }


                        try {

                            PositionAnalysis result =
                                    get();


                            /*
                             * The player is allowed to make manual
                             * moves while the initial root analysis is
                             * still running.
                             *
                             * Therefore the actual board position may
                             * legitimately be a descendant of
                             * 'position' by the time this worker
                             * finishes.  Do NOT discard the completed
                             * root analysis merely because the board
                             * object has changed.
                             */
                            currentAnalysis =
                                    result;


                            analysisPanel.setAnalysis(
                                    result,
                                    gameHistory.size() > 1
                            );


                            /*
                             * Reconstruct every manual move that was
                             * committed while the initial analysis was
                             * running.
                             *
                             * gameHistory[0] is the analyzed root.
                             * Each later entry is the resulting
                             * position after one committed manual move.
                             *
                             * commitPositionToPath(...) finds the
                             * corresponding MoveAnalysis /
                             * VariationNode and recreates the same
                             * visible card-and-connector path that
                             * would have existed if analysis had
                             * already been available when the move was
                             * made.
                             */
                            for (int index = 1;
                                 index < gameHistory.size();
                                 index++) {

                                boolean restored =
                                        analysisPanel.commitPositionToPath(
                                                gameHistory.get(
                                                        index
                                                )
                                        );


                                /*
                                 * If the public analysis tree has not
                                 * generated this far yet, stop at the
                                 * deepest currently representable
                                 * committed move.  Later live snapshots
                                 * can extend the path once that
                                 * continuation exists.
                                 */
                                if (!restored) {

                                    break;
                                }
                            }


                            /*
                             * Initial analysis completion must not
                             * clear an in-progress piece selection on
                             * the real board.  clearPreview() also
                             * clears selectedSquare, so only use it
                             * when a preview actually exists.
                             */
                            if (boardPanel.isPreviewing()) {

                                boardPanel.clearPreview();
                            }


                            Position selected =
                                    analysisPanel.getSelectedPosition();


                            if (selected != null
                                    &&
                                    samePosition(
                                            selected,
                                            boardPanel.getPosition()
                                    )) {

                                evaluationBar.setAnalysis(
                                        analysisPanel.getSelectedSearchValue(),
                                        analysisPanel.getSelectedOutcome(),
                                        analysisPanel.getSelectedMateDistance()
                                );


                                /*
                                 * The manual path already exists when
                                 * the scheduler is created, so bias its
                                 * bonus work toward the committed
                                 * endpoint while global dovetailing
                                 * continues normally.
                                 */
                                engine.setExplorationFocus(
                                        selected
                                );


                            } else {

                                evaluationBar.setAnalysis(
                                        result.getSearchValue(),
                                        result.getOutcome(),
                                        result.getMateDistance()
                                );


                                engine.clearExplorationFocus();
                            }


                            explorationGeneration++;


                            startGuiExplorationChain();


                            updateBackButton();


                        } catch (Exception exception) {

                            exception.printStackTrace();


                            JOptionPane.showMessageDialog(
                                    ChessWindow.this,

                                    "Analysis failed:\n"
                                            + getUsefulMessage(
                                            exception
                                    ),

                                    "Chess Engine",

                                    JOptionPane.ERROR_MESSAGE
                            );


                            updateBackButton();
                        }
                    }
                };


        worker.execute();
    }


    // =========================================================
    // Position identity helper
    // =========================================================

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


    // =========================================================
    // Error helper
    // =========================================================

    private String getUsefulMessage(
            Exception exception
    ) {

        Throwable cause =
                exception;


        while (cause.getCause()
                != null) {

            cause =
                    cause.getCause();
        }


        if (cause.getMessage()
                != null) {

            return cause.getMessage();
        }


        return cause
                .getClass()
                .getSimpleName();
    }


    // =========================================================
    // Access
    // =========================================================

    public ChessBoardPanel getBoardPanel() {

        return boardPanel;
    }


    // =========================================================
    // Preview state
    // =========================================================

    private static class PreviewState {

        private final Position position;

        private final int searchValue;

        private final SearchOutcome outcome;

        private final int mateDistance;


        private PreviewState(
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
    }
}

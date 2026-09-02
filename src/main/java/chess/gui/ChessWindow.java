package main.java.chess.gui;

import main.java.chess.analysis.MoveAnalysis;
import main.java.chess.analysis.PositionAnalysis;
import main.java.chess.analysis.VariationNode;
import main.java.chess.engine.ChessEngine;
import main.java.chess.engine.EndgameMoveController;
import main.java.chess.endgame.EndgameGenerator;
import main.java.chess.endgame.EndgameSettings;
import main.java.chess.endgame.EndgameStudyEligibility;
import main.java.chess.endgame.EndgameStudyProgress;
import main.java.chess.endgame.EndgameStudyProgressStore;
import main.java.chess.endgame.ThreePieceTablebase;
import main.java.chess.endgame.ThreePieceTablebaseService;
import main.java.chess.endgame.FourPieceTablebase;
import main.java.chess.endgame.FourPieceTablebaseService;
import main.java.chess.endgame.ExactEndgameTablebase;
import main.java.chess.endgame.FourPieceMaterialClass;
import main.java.chess.endgame.FourPieceStudyPositionGenerator;
import main.java.chess.endgame.EndgameTrainerRules;
import main.java.chess.endgame.EndgameCurriculumMetadata;
import main.java.chess.model.PieceType;
import main.java.chess.model.Board;
import main.java.chess.model.PositionKey;
import main.java.chess.model.Piece;
import main.java.chess.model.Square;
import main.java.chess.model.Move;
import main.java.chess.model.FenCodec;
import main.java.chess.model.Position;
import main.java.chess.search.SearchOutcome;
import main.java.chess.stockfish.StockfishClient;
import main.java.chess.stockfish.StockfishScorePerspective;
import main.java.chess.stockfish.StockfishPvFormatter;
import main.java.chess.stockfish.StockfishMoveAdapter;
import main.java.chess.rules.MoveGenerator;
import main.java.chess.util.SanMoveFormatter;

import javax.swing.*;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.KeyEvent;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.Duration;
import java.util.zip.GZIPInputStream;


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


    /*
     * Curriculum review pacing. A deferred study enters the review stack
     * immediately, but it is not eligible to interrupt the fresh curriculum
     * until four fresh positions from that family have been delivered.
     *
     * This gives the trainer a stable rhythm:
     *
     * fresh -> fresh -> fresh -> fresh -> review
     */
    private static final int ENDGAME_FRESH_STUDIES_BEFORE_REVIEW =
            EndgameTrainerRules.FRESH_STUDIES_BEFORE_REVIEW;


    /*
     * Exact curriculum denominators for the three-piece canonical families.
     * These are WIN-state counts for one canonical color orientation; the
     * trainer color-reverses positions rather than double-counting symmetry.
     */
    private static final long KQK_CURRICULUM_TOTAL = 144_508L;
    private static final long KRK_CURRICULUM_TOTAL = 175_168L;
    private static final long KPK_CURRICULUM_TOTAL = 124_960L;

    private static final int FOUR_PIECE_GENERIC_MAGIC = 0x34475442; // 4GTB
    private static final int FOUR_PIECE_GENERIC_VERSION = 1;
    private static final int FOUR_PIECE_KPKP_MAGIC = 0x4B504B50;   // KPKP
    private static final int FOUR_PIECE_KPKP_VERSION = 1;

    private static final Path FOUR_PIECE_TABLEBASE_DIRECTORY =
            Path.of("tablebases", "four-piece");

    private static final Path DEVELOPMENT_FOUR_PIECE_TABLEBASE_DIRECTORY =
            Path.of("src", "main", "resources", "tablebases", "four-piece");

    /*
     * Endgame and Setup can use more horizontal room than their original
     * 410px utility panes, but should not turn into giant stretched forms.
     */
    private static final int AUXILIARY_MODE_MAX_WIDTH = 700;


    // =========================================================
    // Stockfish comparison tuning
    // =========================================================

    /*
     * Stockfish is an optional external reference engine only.
     * It never contributes nodes, scores, or moves to ChessEngine's
     * persistent graph.
     */
    private static final int STOCKFISH_COMPARISON_DEPTH =
            12;

    private static final Duration STOCKFISH_COMPARISON_TIMEOUT =
            Duration.ofSeconds(10);

    /*
     * Candidate breadth does not need the same depth as the authoritative
     * #1 move. A slightly shallower MultiPV search produces a useful set of
     * alternatives much sooner, especially on ordinary laptops.
     */
    /*
     * MultiPV candidate breadth is intentionally shallower than the
     * authoritative #1 search, but deep enough to rank a full move set.
     */
    /*
     * Progressive Stockfish mode
     *
     * Pass 1: broad visibility of the whole move space.
     * Pass 2: refine the strongest eight.
     * Pass 3: deepen the strongest three.
     * Pass 4: authoritative single-PV best move.
     *
     * The panel therefore gets useful breadth quickly while the strongest
     * candidates continue improving in place.
     */
    private static final int STOCKFISH_MODE_DEPTH =
            10;

    private static final int STOCKFISH_MODE_MAX_MULTIPV =
            20;

    private static final Duration STOCKFISH_MODE_MULTIPV_TIMEOUT =
            Duration.ofSeconds(
                    12
            );


    private static final int STOCKFISH_REFINEMENT_MULTIPV =
            8;

    private static final int STOCKFISH_REFINEMENT_DEPTH =
            14;

    private static final Duration STOCKFISH_REFINEMENT_TIMEOUT =
            Duration.ofSeconds(
                    15
            );


    private static final int STOCKFISH_DEEP_MULTIPV =
            3;

    private static final int STOCKFISH_DEEP_DEPTH =
            18;

    private static final Duration STOCKFISH_DEEP_TIMEOUT =
            Duration.ofSeconds(
                    20
            );


    // MultiPV=1 is authoritative for the move finally presented as #1.
    private static final int STOCKFISH_BEST_MOVE_DEPTH =
            20;

    private static final Duration STOCKFISH_BEST_MOVE_TIMEOUT =
            Duration.ofSeconds(
                    30
            );


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
    private final StockfishCandidatePanel stockfishCandidatePanel;

    private final EvaluationBar evaluationBar;
    private final SetupPanel setupPanel;
    private final EndgameCurriculumPanel endgameStudyPanel;
    private JPanel analysisArea;
    private JPanel analysisModeCards;
    private JPanel analysisEngineCards;
    private JPanel auxiliaryAnalysisCards;
    private JPanel setupAnalysisStage;
    private JPanel endgameAnalysisStage;


    // =========================================================
    // Engine
    // =========================================================

    private final ChessEngine engine;

    private final StockfishClient stockfishClient;
    private long stockfishComparisonRequestId;

    /*
     * Only one Stockfish-mode progressive pipeline should remain relevant at
     * a time. A new board/preview position cancels the older worker so an old
     * deepening pass cannot delay the newly selected position.
     */
    private SwingWorker<?, ?> stockfishModeWorker;

    /*
     * Exact endgame playing policy.
     *
     * This is intentionally separate from ChessEngine's persistent
     * analysis graph. Normal mode remains exact; practice mode can
     * deliberately choose tablebase-classified suboptimal replies.
     */
    private final EndgameMoveController endgameMoveController;


    // =========================================================
    // Analysis state
    // =========================================================

    private PositionAnalysis currentAnalysis;

    private boolean darkTheme = true;

    private JPanel applicationHeader;
    private JPanel workspace;
    private JPanel boardArea;
    private JPanel setupPaletteHost;
    private JPanel headerActionsWrapper;
    private JPanel boardStack;
    private BoardLoadingOverlay boardLoadingOverlay;
    private long boardLoadingGeneration = -1L;
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
    private JButton engineHomeButton;
    private JButton resetPositionButton;
    private JButton setupPositionButton;
    private JButton endgameButton;
    private JButton flipBoardButton;
    private JButton engineModeDropdownButton;
    private JPopupMenu engineModeMenu;

    private enum AnalysisEngineMode {
        DOVETAIL,
        HYBRID,
        STOCKFISH
    }

    private AnalysisEngineMode analysisEngineMode;
    private long stockfishModeRequestId;

    /*
     * Keep one stable Stockfish result per exact FEN during the GUI session.
     * Fixed-depth Stockfish searches can vary by a few centipawns when the
     * transposition-table/search history differs. Reusing an already computed
     * result makes returning to the exact same position visually stable.
     */
    private final Map<String, StockfishModeSnapshot> stockfishModeCache =
            new HashMap<>();
    private PiecePalettePanel piecePalettePanel;

    private final EndgameGenerator endgameGenerator;
    private EndgameSettings lastEndgameSettings;
    private boolean endgameStudyMode;
    private boolean endgameStudyReady;
    private boolean regeneratingRejectedEndgame;
    private long endgameProofGeneration;

    /*
     * Exact three-piece study support.
     *
     * KQK and KRK are precomputed offline and loaded through
     * ThreePieceTablebaseService.  No retrograde construction is needed
     * during normal Endgame Study use.
     */
    private final java.util.Random endgameStudyRandom =
            new java.util.Random();

    private final ThreePieceTablebaseService threePieceTablebaseService;
    private final FourPieceTablebaseService fourPieceTablebaseService;
    private final FourPieceStudyPositionGenerator fourPieceStudyPositionGenerator;

    private ExactEndgameTablebase activeEndgameTablebase;

    // M68E — persistent exact-study curriculum state.
    private final EndgameStudyProgressStore endgameProgressStore =
            new EndgameStudyProgressStore();
    private final EndgameStudyProgress endgameProgress =
            endgameProgressStore.load();
    private String selectedEndgameFamily = "Mixed";
    private String pendingEndgameFamily = "Mixed";

    /*
     * Mixed chooses the next study size randomly. Fresh Mixed studies have an
     * even 50/50 chance of being three-piece or four-piece; review pacing can
     * still select the only bucket that contains an eligible due review.
     */
    private String currentEndgameStudyId;
    private String currentEndgameFamily;
    private boolean currentEndgameStudyClean = true;
    private boolean currentEndgameAttemptRecorded;
    private long currentEndgameCurriculumTotal;
    private long currentEndgameLegalTotal;

    /*
     * Lightweight curriculum metadata cache. Four-piece totals are read from
     * the small gzip header only; the large outcome/distance arrays are never
     * loaded merely to draw a progress denominator.
     */
    private final Map<String, Long> endgameCurriculumTotalCache =
            new HashMap<>();
    private long mixedEndgameCurriculumTotal = -1L;
    private boolean mixedEndgameMetadataWarningLogged;

    /*
     * Transient pacing state only. Progress/review membership itself remains
     * persisted by EndgameStudyProgressStore; this map simply prevents the
     * review stack from taking over the next-position flow.
     */
    private final Map<String, Integer> endgameFreshStudiesSinceReview =
            new java.util.concurrent.ConcurrentHashMap<>();
    private int endgameMoveReviewIndex;
    private KeyEventDispatcher endgameMoveReviewKeyDispatcher;
    private KeyEventDispatcher analysisMoveNavigationKeyDispatcher;
    private boolean analysisMoveNavigationArmed;

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
    private boolean userExplorationPaused;


    // =========================================================
    // Preview / manual history
    // =========================================================

    private final List<PreviewState>
            previewHistory;


    private final List<Position>
            gameHistory;

    /*
     * Positions removed by keyboard/back navigation. New manual play clears
     * this stack; Right/Down can replay it without restarting the search.
     */
    private final Deque<Position>
            manualRedoHistory;


    // =========================================================
    // Constructor
    // =========================================================

    public ChessWindow(
            Position position
    ) {

        super(
                "Dovetail Engine — Persistent Graph Explorer"
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


        /*
         * Build one lazy client from every plausible local Stockfish binary.
         * The first comparison runs the UCI handshake off the Swing thread
         * and automatically falls through stale / non-UCI executables.
         */
        stockfishClient =
                StockfishClient.createConfiguredClient();


        stockfishComparisonRequestId =
                0L;


        addWindowListener(
                new java.awt.event.WindowAdapter() {

                    @Override
                    public void windowClosing(
                            java.awt.event.WindowEvent event
                    ) {

                        deferCurrentEndgameIfIncomplete();

                        if (endgameMoveReviewKeyDispatcher != null) {
                            KeyboardFocusManager
                                    .getCurrentKeyboardFocusManager()
                                    .removeKeyEventDispatcher(
                                            endgameMoveReviewKeyDispatcher
                                    );
                            endgameMoveReviewKeyDispatcher = null;
                        }

                        if (analysisMoveNavigationKeyDispatcher != null) {
                            KeyboardFocusManager
                                    .getCurrentKeyboardFocusManager()
                                    .removeKeyEventDispatcher(
                                            analysisMoveNavigationKeyDispatcher
                                    );
                            analysisMoveNavigationKeyDispatcher = null;
                        }

                        if (stockfishClient != null) {

                            stockfishClient.close();
                        }
                    }
                }
        );


        endgameMoveController =
                new EndgameMoveController(
                        endgameStudyRandom
                );


        endgameGenerator =
                new EndgameGenerator();


        threePieceTablebaseService =
                new ThreePieceTablebaseService();


        fourPieceTablebaseService =
                new FourPieceTablebaseService();


        fourPieceStudyPositionGenerator =
                new FourPieceStudyPositionGenerator();


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

        stockfishCandidatePanel = new StockfishCandidatePanel();
        stockfishCandidatePanel.setVisible(false);

        analysisEngineMode =
                AnalysisEngineMode.DOVETAIL;

        engine.setSearchMode(
                ChessEngine.SearchMode.DOVETAIL
        );

        stockfishModeRequestId = 0L;

        analysisMoveNavigationArmed =
                false;


        if (stockfishClient == null) {

            analysisPanel.setStockfishUnavailable(
                    "Stockfish is not configured."
            );

        } else {

            analysisPanel.setStockfishIdle(
                    "Stockfish reference follows the viewed position."
            );
        }


        setupPanel =
                new SetupPanel();


        setupPanel.setVisible(
                false
        );


        /*
         * The current SetupPanel owns its own Analyze Position button.
         * Wire it to the same commit path as the bottom PiecePalettePanel.
         */
        setupPanel.setAnalyzeListener(
                this::commitPositionSetup
        );


        endgameStudyPanel =
                new EndgameCurriculumPanel();


        endgameStudyPanel.setVisible(
                false
        );


        endgameStudyPanel.setFamilies(
                endgameCurriculumFamilies(),
                selectedEndgameFamily
        );

        endgameStudyPanel.setFamilyListener(
                this::selectEndgameFamily
        );

        endgameStudyPanel.setNextListener(
                this::advanceCurriculumPosition
        );

        endgameStudyPanel.setPreviousMoveListener(
                this::reviewPreviousEndgameMove
        );

        endgameStudyPanel.setNextMoveListener(
                this::reviewNextEndgameMove
        );

        endgameStudyPanel.setHintListener(
                () -> {
                    markCurrentEndgameAttempt(false);
                    currentEndgameStudyClean = false;
                    endgameStudyPanel.setStatus(
                            "Hint used — this attempt will not count as mastered."
                    );
                }
        );

        endgameStudyPanel.setGiveUpListener(
                () -> {
                    markCurrentEndgameAttempt(false);
                    currentEndgameStudyClean = false;
                    deferCurrentEndgameIfIncomplete();
                    revealEndgameAnalysis();
                }
        );

        endgameStudyPanel.setResetProgressListener(
                this::confirmResetEndgameProgress
        );
        endgameStudyPanel.setResetFamilyListener(this::confirmResetCurrentEndgameFamily);
        endgameStudyPanel.setOrderListener(this::setEndgameStudyOrder);

        refreshEndgameProgressPanel();


        /*
         * Endgame is a mastery mode: defense is always exact.
         * Keep the underlying practice policy available internally for
         * verification/experimentation, but do not expose it in the UI.
         */
        endgameMoveController.setPracticeMode(false);
        endgameMoveController.setPracticeStrength(100);

        installEndgameMoveReviewKeyDispatcher();
        installAnalysisMoveNavigationKeyDispatcher();


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

        userExplorationPaused =
                false;


        previewHistory =
                new ArrayList<>();


        gameHistory =
                new ArrayList<>();

        manualRedoHistory =
                new ArrayDeque<>();


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


        /*
         * Keep the board and its loading feedback in the same bounds.
         * Exact tablebases are loaded by SwingWorker, so the EDT remains free
         * to animate this overlay while the worker reads/decompresses the asset.
         */
        boardStack =
                new JPanel();

        boardStack.setOpaque(false);
        boardStack.setLayout(new OverlayLayout(boardStack));

        Dimension boardSize =
                boardPanel.getPreferredSize();

        boardStack.setPreferredSize(boardSize);
        boardStack.setMinimumSize(boardSize);

        /*
         * ChessBoardPanel paints its fixed 640 x 640 board from the component's
         * top-left corner.  BorderLayout may make boardStack taller than that
         * when the window is maximized, so both overlay children must use the
         * same fixed bounds and top-left alignment.  Center-aligning the
         * fixed-size overlay inside the taller stack leaves an uncovered strip
         * across the top of the painted board.
         */
        boardPanel.setAlignmentX(0.0f);
        boardPanel.setAlignmentY(0.0f);
        boardPanel.setMaximumSize(boardSize);

        boardLoadingOverlay =
                new BoardLoadingOverlay();

        boardLoadingOverlay.setAlignmentX(0.0f);
        boardLoadingOverlay.setAlignmentY(0.0f);
        boardLoadingOverlay.setPreferredSize(boardSize);
        boardLoadingOverlay.setMinimumSize(boardSize);
        boardLoadingOverlay.setMaximumSize(boardSize);

        // Component index 0 is the top-most child for Swing z-order.
        boardStack.add(boardLoadingOverlay);
        boardStack.add(boardPanel);
        boardStack.setComponentZOrder(boardLoadingOverlay, 0);
        boardStack.setComponentZOrder(boardPanel, 1);

        boardArea.add(
                boardStack,
                BorderLayout.CENTER
        );


        /*
         * Keep the fixed 640px board column at its natural width.  When the
         * board area lived in BorderLayout.CENTER it absorbed every extra
         * fullscreen pixel even though ChessBoardPanel still paints only its
         * fixed 8 x 80 board.  The result was the large empty strip between
         * the board and whichever right-side mode was active.
         */
        workspace.add(
                boardArea,
                BorderLayout.WEST
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


        analysisEngineCards = new JPanel(new CardLayout());
        analysisEngineCards.setOpaque(false);
        analysisEngineCards.add(analysisPanel, "DOVETAIL");
        analysisEngineCards.add(stockfishCandidatePanel, "STOCKFISH");


        /*
         * Setup and Endgame now own the entire right-side workspace while
         * their actual controls remain centered at a comfortable width.
         * This makes fullscreen intentional instead of leaving a large strip
         * of unrelated application background to the right of a 410px panel.
         */
        setupAnalysisStage =
                new CenteredModeHost(
                        setupPanel,
                        AUXILIARY_MODE_MAX_WIDTH
                );

        endgameAnalysisStage =
                new CenteredModeHost(
                        endgameStudyPanel,
                        AUXILIARY_MODE_MAX_WIDTH
                );

        auxiliaryAnalysisCards =
                new JPanel(
                        new CardLayout()
                );

        auxiliaryAnalysisCards.setOpaque(false);
        auxiliaryAnalysisCards.add(setupAnalysisStage, "SETUP");
        auxiliaryAnalysisCards.add(endgameAnalysisStage, "ENDGAME");


        /*
         * A top-level CardLayout lets Engine and auxiliary modes each fill
         * the complete analysis region. Engine remains fully expandable;
         * Setup/Endgame use the centered responsive hosts above.
         */
        analysisModeCards =
                new JPanel(
                        new CardLayout()
                );

        analysisModeCards.setOpaque(false);
        analysisModeCards.add(analysisEngineCards, "ENGINE");
        analysisModeCards.add(auxiliaryAnalysisCards, "AUXILIARY");

        analysisArea.add(
                analysisModeCards,
                BorderLayout.CENTER
        );


        workspace.add(
                analysisArea,
                BorderLayout.CENTER
        );


        /*
         * Preserve the original M68C6E PiecePalettePanel exactly, but keep its
         * wide preferred width from inflating the BorderLayout.WEST board
         * column. The transparent host spans the workspace; the unchanged
         * palette itself remains left-aligned at the bottom.
         */
        setupPaletteHost =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.LEFT,
                                0,
                                0
                        )
                );

        setupPaletteHost.setOpaque(
                false
        );

        setupPaletteHost.add(
                piecePalettePanel
        );

        setupPaletteHost.setVisible(
                false
        );

        workspace.add(
                setupPaletteHost,
                BorderLayout.SOUTH
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


        analysisPanel.setSearchControlListener(
                this::toggleGuiExplorationPause
        );


        analysisPanel.setNewAnalysisListener(
                this::startFreshAnalysisFromCurrentPosition
        );


        analysisPanel.setPathCollapseListener(
                this::handlePathCollapse
        );

        stockfishCandidatePanel.setSelectionListener(
                candidate -> {

                    armStockfishMoveNavigation();

                    boardPanel.setPreviewPosition(
                            candidate.position()
                    );

                    evaluationBar.setAnalysis(
                            candidate.evaluation(),
                            SearchOutcome.UNKNOWN,
                            -1
                    );

                    requestStockfishModeCandidates(
                            candidate.position()
                    );
                }
        );

        stockfishCandidatePanel.setCollapseListener(
                () -> {

                    /*
                     * Do NOT clear the selected-line keyboard snapshot here.
                     *
                     * Clicking the selected Stockfish card is only a visual
                     * collapse/unselect operation. Right/Up must still be able
                     * to restore the deepest line the user had selected.
                     *
                     * True history-changing actions (manual move, Back/undo,
                     * reset, new root) still clear keyboard navigation in
                     * their own handlers.
                     */
                    Position fallbackRoot =
                            gameHistory.isEmpty()
                                    ? boardPanel.getPosition()
                                    : gameHistory.get(0);

                    Position target =
                            stockfishCandidatePanel.getCurrentPosition(
                                    fallbackRoot
                            );

                    trimCommittedHistoryToPosition(
                            target
                    );

                    Position actual =
                            gameHistory.isEmpty()
                                    ? null
                                    : gameHistory.get(gameHistory.size() - 1);

                    if (actual != null
                            && samePosition(actual, target)) {

                        boardPanel.clearPreview();
                        boardPanel.setPosition(actual);

                    } else {

                        boardPanel.setPreviewPosition(target);
                    }

                    requestStockfishModeCandidates(target);
                    updateBackButton();
                }
        );

        stockfishCandidatePanel.setBackListener(() -> {

            stockfishCandidatePanel.clearKeyboardPathNavigation();

            Position fallbackRoot =
                    gameHistory.isEmpty()
                            ? boardPanel.getPosition()
                            : gameHistory.get(0);

            Position target =
                    stockfishCandidatePanel.goBack(
                            fallbackRoot
                    );

            trimCommittedHistoryToPosition(
                    target
            );

            Position actual =
                    gameHistory.isEmpty()
                            ? null
                            : gameHistory.get(gameHistory.size() - 1);

            if (actual != null
                    && samePosition(actual, target)) {

                boardPanel.clearPreview();
                boardPanel.setPosition(actual);

            } else {

                boardPanel.setPreviewPosition(target);
            }

            requestStockfishModeCandidates(target);
            updateBackButton();
        });


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


        /*
         * M88.3 startup-fit polish:
         *
         * Use the same taskbar-aware sizing path that Setup and Endgame already
         * use instead of forcing the packed startup window to at least 1280x800.
         * On scaled / shorter Windows desktops the old startup path could extend
         * below the usable work area even though resizeForCurrentMode() already
         * knows how to clamp the window to the actual screen bounds.
         *
         * Center first, then let the existing helper shrink/reposition only when
         * necessary. On a large enough display the normal preferred size is kept.
         */
        setLocationRelativeTo(
                null
        );


        resizeForCurrentMode();


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


        /*
         * M68C3: one engine identity in the top-left.
         * Clicking it opens the engine menu instead of showing two competing
         * title-sized buttons.
         */
        engineModeDropdownButton =
                createHeaderActionButton(
                        "Dovetail ▾"
                );

        engineModeDropdownButton.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.BOLD,
                        18
                )
        );

        engineModeDropdownButton.setHorizontalAlignment(
                SwingConstants.LEFT
        );

        engineModeDropdownButton.setToolTipText(
                "Choose the analysis engine."
        );

        engineModeMenu =
                new JPopupMenu();

        JMenuItem dovetailItem =
                new JMenuItem(
                        "Dovetail"
                );

        JMenuItem hybridItem =
                new JMenuItem(
                        "Hybrid"
                );

        JMenuItem stockfishItem =
                new JMenuItem(
                        "Stockfish"
                );

        dovetailItem.addActionListener(
                event -> setAnalysisEngineMode(
                        AnalysisEngineMode.DOVETAIL
                )
        );

        hybridItem.addActionListener(
                event -> setAnalysisEngineMode(
                        AnalysisEngineMode.HYBRID
                )
        );

        stockfishItem.addActionListener(
                event -> setAnalysisEngineMode(
                        AnalysisEngineMode.STOCKFISH
                )
        );

        engineModeMenu.add(
                dovetailItem
        );

        engineModeMenu.add(
                hybridItem
        );

        engineModeMenu.add(
                stockfishItem
        );

        engineModeDropdownButton.addActionListener(
                event -> engineModeMenu.show(
                        engineModeDropdownButton,
                        0,
                        engineModeDropdownButton.getHeight()
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
                engineModeDropdownButton
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


        engineHomeButton =
                createHeaderActionButton(
                        "Home"
                );


        engineHomeButton.setToolTipText(
                "Return to the standard starting position and normal engine workspace."
        );


        engineHomeButton.addActionListener(
                event ->
                        returnToEngineHome()
        );


        resetPositionButton =
                createHeaderActionButton(
                        "Reset"
                );


        resetPositionButton.addActionListener(
                event -> {

                    if (boardPanel.isSetupMode()) {

                        piecePalettePanel.cancelActiveDrag();
                        boardPanel.resetSetupEditsToStart();
                        refreshSetupPanel();

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
                        showEndgameCurriculum()
        );


        flipBoardButton =
                createHeaderActionButton(
                        "Flip Board"
                );

        flipBoardButton.setToolTipText(
                "Flip the board. Move-impact signs will switch to the side now viewed from the bottom."
        );

        flipBoardButton.addActionListener(
                event ->
                        flipBoardPerspective()
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


        headerActionsWrapper =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.RIGHT,
                                10,
                                0
                        )
                );


        headerActionsWrapper.setOpaque(
                false
        );


        headerActionsWrapper.add(
                engineHomeButton
        );


        headerActionsWrapper.add(
                resetPositionButton
        );


        headerActionsWrapper.add(
                setupPositionButton
        );


        headerActionsWrapper.add(
                endgameButton
        );

        headerActionsWrapper.add(
                flipBoardButton
        );

        headerActionsWrapper.add(
                loadFenButton
        );


        headerActionsWrapper.add(
                copyFenButton
        );


        headerActionsWrapper.add(
                themeControls
        );


        headerActionsWrapper.add(
                versionBadge
        );


        applicationHeader.add(
                headerActionsWrapper,
                BorderLayout.EAST
        );


        return applicationHeader;
    }


    private void flipBoardPerspective() {

        boardPanel.flipBoard();

        analysisPanel.setBlackPerspective(
                boardPanel.isFlipped()
        );

        stockfishCandidatePanel.setBlackPerspective(
                boardPanel.isFlipped()
        );

        flipBoardButton.setToolTipText(
                boardPanel.isFlipped()
                        ? "Black is at the bottom. Positive move impact means the move helped Black."
                        : "White is at the bottom. Positive move impact means the move helped White."
        );
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

                    case "Home" ->
                            68;

                    case "Copy FEN" ->
                            86;

                    case "Flip Board" ->
                            96;

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


            updateBoardAreaInsetsForCurrentMode();
        }


        if (applicationHeader != null) {

            applicationHeader.setBackground(
                    headerBackground
            );


            updateApplicationHeaderBorderForCurrentMode();
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
                engineHomeButton,
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


        styleHeaderActionButton(
                flipBoardButton,
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


        /*
         * The responsive auxiliary hosts deliberately continue the visual
         * surface of their child panels across the unused side margins.
         */
        if (setupAnalysisStage != null
                && setupPanel != null) {

            setupAnalysisStage.setBackground(
                    setupPanel.getBackground()
            );
        }

        if (endgameAnalysisStage != null
                && endgameStudyPanel != null) {

            endgameAnalysisStage.setBackground(
                    endgameStudyPanel.getBackground()
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

        stockfishCandidatePanel.setDarkTheme(
                dark
        );

        styleEngineSelectorButtons();


        revalidate();
        repaint();
    }


    private void styleEngineSelectorButtons() {

        if (engineModeDropdownButton == null) {
            return;
        }

        Color background =
                darkTheme
                        ? new Color(12, 18, 24)
                        : new Color(246, 248, 250);

        Color foreground =
                darkTheme
                        ? new Color(242, 244, 247)
                        : new Color(31, 35, 41);

        Color border =
                darkTheme
                        ? new Color(42, 53, 64)
                        : new Color(210, 216, 224);

        boolean selectorEnabled =
                engineModeDropdownButton.isEnabled();

        Color selectorForeground =
                selectorEnabled
                        ? foreground
                        : (darkTheme
                        ? new Color(101, 110, 121)
                        : new Color(145, 151, 160));

        Color selectorBorder =
                selectorEnabled
                        ? border
                        : (darkTheme
                        ? new Color(50, 56, 64)
                        : new Color(222, 226, 232));

        engineModeDropdownButton.setText(
                activeAnalysisEngineLabel()
                        + " ▾"
        );

        engineModeDropdownButton.setForeground(
                selectorForeground
        );

        engineModeDropdownButton.setBackground(
                background
        );

        engineModeDropdownButton.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(
                                selectorBorder,
                                1,
                                true
                        ),
                        BorderFactory.createEmptyBorder(
                                5,
                                10,
                                5,
                                10
                        )
                )
        );

        engineModeDropdownButton.setFocusPainted(false);
        engineModeDropdownButton.setCursor(
                Cursor.getPredefinedCursor(
                        engineModeDropdownButton.isEnabled()
                                ? Cursor.HAND_CURSOR
                                : Cursor.DEFAULT_CURSOR
                )
        );

        if (engineModeMenu != null) {
            engineModeMenu.setBackground(background);
            engineModeMenu.setForeground(foreground);
            engineModeMenu.setBorder(
                    BorderFactory.createLineBorder(
                            border,
                            1
                    )
            );

            for (Component component : engineModeMenu.getComponents()) {
                if (component instanceof JMenuItem item) {
                    item.setBackground(background);
                    item.setForeground(foreground);
                    item.setFont(
                            new Font(
                                    Font.SANS_SERIF,
                                    Font.PLAIN,
                                    13
                            )
                    );
                }
            }
        }
    }


    /**
     * Setup and Endgame are dedicated workspaces, so the normal analysis-engine
     * selector is intentionally disabled while either workspace owns the board.
     */
    private void setAnalysisEngineSelectorEnabled(
            boolean enabled
    ) {

        if (engineModeDropdownButton == null) {
            return;
        }


        if (!enabled
                && engineModeMenu != null) {

            engineModeMenu.setVisible(
                    false
            );
        }


        engineModeDropdownButton.setEnabled(
                enabled
        );


        engineModeDropdownButton.setToolTipText(
                enabled
                        ? "Choose the analysis engine."
                        : "Analysis-engine selection is unavailable in Setup and Endgame."
        );


        styleEngineSelectorButtons();
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

        armAnalysisMoveNavigation();


        retargetSearchBias();


        requestStockfishComparison(
                move.getPosition()
        );
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

        armAnalysisMoveNavigation();


        /*
         * A PV button may preview a node without changing the
         * selected card path.
         */
        if (analysisPanel.getPathDepth() > 0) {

            retargetSearchBias();
        }


        requestStockfishComparison(
                variation.getPosition()
        );
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


        refreshSearchTelemetry();


        restartGuiExplorationChain();
    }


    // =========================================================
    // Always-on GUI search chain
    // =========================================================

    private void startGuiExplorationChain() {

        if (!engine.hasActiveAnalysis()) {

            return;
        }


        userExplorationPaused =
                false;

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


        if (userExplorationPaused) {

            automaticExplorationActive =
                    false;

            refreshSearchTelemetry();
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


    private void startFreshAnalysisFromCurrentPosition() {

        int choice =
                JOptionPane.showConfirmDialog(
                        this,
                        "Start a new analysis?\n\n"
                                + "This will discard the current search graph and all accumulated discoveries "
                                + "for this analysis.\n"
                                + "The board position itself will not change.",
                        "New Analysis",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                );


        if (choice != JOptionPane.YES_OPTION) {

            return;
        }


        /*
         * This is intentionally different from Reset Position.
         *
         * Reset now preserves a known graph.  New Analysis is the explicit
         * destructive action: analyzeCurrentPosition() ultimately calls the
         * engine's normal analyze(...) path, which creates a fresh graph and
         * resets cumulative exploration telemetry.
         */
        userExplorationPaused =
                false;

        analyzeCurrentPosition();
    }


    private void toggleGuiExplorationPause() {

        if (!engine.hasActiveAnalysis()
                || !engine.hasMoreExplorationWork()) {

            return;
        }


        if (userExplorationPaused) {

            userExplorationPaused =
                    false;

            explorationGeneration++;

            automaticExplorationActive =
                    true;

            refreshSearchTelemetry();

            runExplorationBatch(
                    explorationGeneration
            );

        } else {

            userExplorationPaused =
                    true;

            automaticExplorationActive =
                    false;

            explorationGeneration++;

            analysisPanel.setExploring(
                    false
            );

            refreshSearchTelemetry();
        }
    }


    private void stopGuiExplorationChain() {

        automaticExplorationActive =
                false;


        explorationGeneration++;


        analysisPanel.setExploring(
                false
        );


        refreshSearchTelemetry();
    }


    private void refreshSearchTelemetry() {

        ChessEngine.SearchTelemetry telemetry =
                engine.getSearchTelemetry();


        List<String> selectedPath =
                analysisPanel.getSelectedPathSan();


        boolean focused =
                isHybridAnalysisMode()
                        && analysisPanel.getPathDepth() > 0
                        && !selectedPath.isEmpty();


        analysisPanel.setSearchTelemetry(
                engine.getSearchMode().name(),
                automaticExplorationActive
                        && telemetry.searching(),
                telemetry.graphNodes(),
                telemetry.workUnits(),
                telemetry.walkerSteps(),
                telemetry.coverageSteps(),
                telemetry.walkers(),
                telemetry.activeWalkers(),
                telemetry.maximumWalkerDepth(),
                telemetry.walkerPathRevisits(),
                telemetry.transpositionNodes(),
                telemetry.transpositionLinks(),
                telemetry.globalQueueSize(),
                telemetry.focusQueueSize(),
                focused,
                selectedPath,
                telemetry.searching(),
                userExplorationPaused
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


        refreshSearchTelemetry();


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


                            refreshSearchTelemetry();


                            if (endgameStudyMode) {

                                updateEndgameStudyProofState();
                            }


                            // =========================================
                            // Preserve the LIVE selected line
                            // =========================================
                            /*
                             * A batch may have started before the user clicked
                             * a card. Using the path captured at batch start can
                             * therefore erase a brand-new selection when the
                             * worker finishes. Re-read the path on the EDT at
                             * completion time; this makes panel clicks sticky
                             * even while search snapshots are refreshing.
                             */
                            List<String> livePath =
                                    new ArrayList<>(
                                            analysisPanel.getSelectedPathSan()
                                    );


                            if (livePath.isEmpty()) {

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
                                        result.getEvaluation(),
                                        result.getOutcome(),
                                        result.getMateDistance()
                                );


                            } else {

                                analysisPanel.restorePath(
                                        result,
                                        livePath,
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
                                                        .getSelectedEvaluation(),

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
                                                currentAnalysis.getEvaluation(),
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
                                            result.getEvaluation(),
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


                                refreshSearchTelemetry();


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


                            refreshSearchTelemetry();


                            exception.printStackTrace();


                            JOptionPane.showMessageDialog(
                                    ChessWindow.this,

                                    "Automatic exploration failed:\n"
                                            + getUsefulMessage(
                                            exception
                                    ),

                                    "Dovetail Engine",

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
    // Collapse selected analysis branch
    // =========================================================

    private void handlePathCollapse() {

        /*
         * Collapse is also an undo operation for manual moves that belong
         * to the collapsed branch.  The selected-line UI and gameHistory
         * must never describe two different endpoints.
         */
        trimCommittedHistoryToSelectedPath();

        rebuildPreviewHistoryFromPanel();


        if (previewHistory.isEmpty()) {

            restoreActualPositionView();

        } else {

            PreviewState endpoint =
                    previewHistory.get(
                            previewHistory.size() - 1
                    );

            showPreviewState(
                    endpoint
            );

            analysisPanel.restoreHeaderForCurrentPath();
        }


        retargetSearchBias();

        requestStockfishComparison(
                getStockfishComparisonPosition()
        );

        updateBackButton();
    }


    private void trimCommittedHistoryToSelectedPath() {

        Position target;

        List<AnalysisPanel.PreviewData> selectedPath =
                analysisPanel.getSelectedPathPreviewData();

        if (selectedPath == null
                || selectedPath.isEmpty()) {

            target =
                    gameHistory.isEmpty()
                            ? null
                            : gameHistory.get(0);

        } else {

            target =
                    selectedPath.get(
                            selectedPath.size() - 1
                    ).getPosition();
        }

        trimCommittedHistoryToPosition(
                target
        );
    }


    /**
     * Rewind committed play to a visible navigation endpoint.
     *
     * Matching the endpoint itself is intentionally stronger than the old
     * prefix-length test: manual moves can be interleaved with preview /
     * selected-line navigation, so UI depth is not guaranteed to equal the
     * number of committed moves.
     */
    private void trimCommittedHistoryToPosition(
            Position target
    ) {

        if (target == null
                || gameHistory.isEmpty()) {

            return;
        }

        int matchIndex =
                -1;

        for (int index = gameHistory.size() - 1;
             index >= 0;
             index--) {

            if (samePosition(
                    gameHistory.get(index),
                    target
            )) {

                matchIndex = index;
                break;
            }
        }

        if (matchIndex < 0) {
            return;
        }

        while (gameHistory.size() > matchIndex + 1) {
            gameHistory.remove(gameHistory.size() - 1);
        }

        previewHistory.clear();
        boardPanel.clearPreview();
        boardPanel.setPosition(
                gameHistory.get(gameHistory.size() - 1)
        );
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


            requestStockfishComparison(
                    getStockfishComparisonPosition()
            );


            updateBackButton();


            return;
        }


        if (gameHistory.size() > 1) {

            undoManualMove();


            requestStockfishComparison(
                    getStockfishComparisonPosition()
            );
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


        if (isStockfishAnalysisMode()) {

            List<Position> selectedPositions =
                    stockfishCandidatePanel.getSelectedPathPositions();

            if (selectedPositions == null
                    || selectedPositions.isEmpty()) {

                return;
            }

            Position stockfishRoot =
                    stockfishCandidatePanel.getRootPosition(
                            gameHistory.isEmpty()
                                    ? boardPanel.getPosition()
                                    : gameHistory.get(gameHistory.size() - 1)
                    );

            trimCommittedHistoryToPosition(
                    stockfishRoot
            );

            for (Position pathPosition : selectedPositions) {

                if (pathPosition == null) {
                    break;
                }

                Position lastCommitted =
                        gameHistory.get(gameHistory.size() - 1);

                if (!samePosition(lastCommitted, pathPosition)) {
                    gameHistory.add(pathPosition);
                }
            }

            previewHistory.clear();
            updateBackButton();
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

        analysisMoveNavigationArmed =
                false;

        analysisPanel.clearKeyboardPathNavigation();
        stockfishCandidatePanel.clearKeyboardPathNavigation();


        if (position == null) {

            return;
        }


        if (endgameStudyMode) {

            if (!endgameStudyReady) {

                endgameStudyPanel.setStatus(
                        "Exact proof is still being established."
                );

                restoreCurrentEndgameStudyPosition();
                return;
            }


            if (activeEndgameTablebase != null) {

                Position parentPosition =
                        gameHistory.get(
                                gameHistory.size() - 1
                        );


                markCurrentEndgameAttempt(true);

                if (!isExactEndgameStudyBestMove(
                        parentPosition,
                        position
                )) {

                    currentEndgameStudyClean = false;

                    endgameStudyPanel.setStatus(
                            "Not the best move — Try again."
                    );

                    restoreCurrentEndgameStudyPosition();
                    return;
                }


                endgameStudyPanel.setStatus(
                        "Best move ✓"
                );


                /*
                 * Exact Endgame Study has its own move/history loop.
                 *
                 * Do NOT fall through into the ordinary persistent-graph
                 * manual-move path below. That path can leave AnalysisPanel
                 * and the graph focused on a different position than the
                 * programmatically played tablebase reply, which prevents
                 * the next study move from behaving like a fresh move.
                 */
                gameHistory.add(
                        position
                );

                previewHistory.clear();

                if (boardPanel.isPreviewing()) {
                    boardPanel.clearPreview();
                }

                boardPanel.setPosition(
                        position
                );

                boardPanel.setEnabled(
                        true
                );

                syncEndgameMoveReviewToLatest();

                updateBackButton();

                playExactEndgameStudyDefense();

                return;
            }
        }


        if (isStockfishAnalysisMode()) {

            /*
             * A genuinely new manual Stockfish move creates a new committed
             * branch, just like Dovetail/Hybrid. Old redo positions no longer
             * belong to that branch.
             */
            manualRedoHistory.clear();

            Position parentPosition =
                    gameHistory.get(
                            gameHistory.size() - 1
                    );

            String manualSan =
                    findManualMoveSan(
                            parentPosition,
                            position
                    );

            gameHistory.add(position);
            previewHistory.clear();

            if (boardPanel.isPreviewing()) {
                boardPanel.clearPreview();
            }

            stockfishCandidatePanel.commitManualPosition(
                    parentPosition,
                    position,
                    manualSan
            );

            requestStockfishModeCandidates(position);
            updateBackButton();
            return;
        }

        /*
         * Any genuinely new manual move creates a new committed branch.
         * Old redo positions no longer belong to that branch.
         */
        manualRedoHistory.clear();


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
                    analysisPanel.getSelectedEvaluation(),
                    analysisPanel.getSelectedOutcome(),
                    analysisPanel.getSelectedMateDistance()
            );
        }


        /*
         * Manual navigation changes only the scheduler's focus.
         * Global dovetailing continues across the whole graph.
         */
        retargetSearchBias();


        requestStockfishComparison(
                position
        );


        updateBackButton();
    }


    private String findManualMoveSan(
            Position parentPosition,
            Position childPosition
    ) {

        if (parentPosition == null
                || childPosition == null) {

            return "move";
        }

        MoveGenerator moveGenerator =
                new MoveGenerator();

        SanMoveFormatter sanFormatter =
                new SanMoveFormatter();

        for (Move move :
                moveGenerator.generateLegalMoves(
                        parentPosition
                )) {

            Position expected =
                    parentPosition.makeMove(
                            move
                    );

            if (samePosition(
                    expected,
                    childPosition
            )) {

                return sanFormatter.format(
                        parentPosition,
                        move
                );
            }
        }

        return "move";
    }


    // =========================================================
    // Legacy internal endgame-strength API
    // =========================================================
    //
    // Kept for existing verification code. The Endgame UI no longer exposes
    // practice defense, and live Endgame play forces exact defense.

    public void setEndgamePracticeMode(
            boolean enabled
    ) {
        endgameMoveController.setPracticeMode(enabled);
    }


    public boolean isEndgamePracticeMode() {
        return endgameMoveController.isPracticeMode();
    }


    public void setEndgamePracticeStrength(
            int strength
    ) {
        endgameMoveController.setPracticeStrength(strength);
    }


    public int getEndgamePracticeStrength() {
        return endgameMoveController.getPracticeStrength();
    }


    // =========================================================
    // Exact Endgame Study automatic defense
    // =========================================================

    private void playExactEndgameStudyDefense() {

        if (gameHistory.isEmpty()
                || activeEndgameTablebase == null) {

            return;
        }


        Position position =
                gameHistory.get(
                        gameHistory.size() - 1
                );


        ExactEndgameTablebase tablebase =
                tablebaseForExactPosition(
                        position
                );


        if (tablebase == null) {

            endgameStudyPanel.setStatus(
                    "Best move ✓ — exact continuation is not supported."
            );

            return;
        }


        activeEndgameTablebase =
                tablebase;


        ExactEndgameTablebase.Probe probe =
                tablebase.probe(
                        position
                );


        if (probe.outcome()
                == ExactEndgameTablebase.Outcome.UNSUPPORTED) {

            endgameStudyPanel.setStatus(
                    "Best move ✓ — exact continuation is not supported."
            );

            return;
        }


        List<Move> replies =
                tablebase.bestMoves(
                        position
                );


        if (replies.isEmpty()) {

            if (currentEndgameStudyClean) {
                markCurrentEndgameMastered();
                endgameStudyPanel.setStatus(
                        "Checkmate — study mastered ✓"
                );
            } else {
                markCurrentEndgameCompleted();
                endgameStudyPanel.setStatus(
                        "Checkmate — study complete. Try again for mastery."
                );
            }

            endgameStudyReady =
                    false;

            boardPanel.setEnabled(
                    false
            );

            syncEndgameMoveReviewToLatest();

            return;
        }


        /*
         * Endgame is a mastery mode, so automatic defense is always exact.
         * The controller remains the authority for supported four-piece
         * replies, but practice weakening is explicitly disabled here.
         *
         * Three-piece studies are not handled by the generic Tier-0
         * controller, so they retain the proven exact-tablebase fallback
         * below.
         */
        endgameMoveController.setPracticeMode(false);
        endgameMoveController.setPracticeStrength(100);

        Move reply =
                endgameMoveController.chooseMove(
                        position
                );


        if (reply == null) {

            /*
             * Exact three-piece fallback (KQK / KRK / KPK), plus any
             * legacy exact position not owned by the generic controller.
             *
             * bestMoves() has already restricted this list to exact
             * game-theoretic optima. Randomness only breaks exact ties.
             */
            reply =
                    replies.get(
                            endgameStudyRandom.nextInt(
                                    replies.size()
                            )
                    );
        }


        Position defendedPosition =
                position.makeMove(
                        reply
                );


        gameHistory.add(
                defendedPosition
        );


        previewHistory.clear();

        boardPanel.clearPreview();

        boardPanel.setPosition(
                defendedPosition
        );

        syncEndgameMoveReviewToLatest();

        boardPanel.setEnabled(
                true
        );


        boardPanel.revalidate();
        boardPanel.repaint();
        boardPanel.requestFocusInWindow();


        ExactEndgameTablebase continuationTablebase =
                tablebaseForExactPosition(
                        defendedPosition
                );


        if (continuationTablebase != null) {

            activeEndgameTablebase =
                    continuationTablebase;
        }


        ExactEndgameTablebase.Probe continuation =
                continuationTablebase != null
                        ? continuationTablebase.probe(
                        defendedPosition
                )
                        : new ExactEndgameTablebase.Probe(
                        ExactEndgameTablebase.Outcome.UNSUPPORTED,
                        -1
                );


        if (continuation.outcome()
                == ExactEndgameTablebase.Outcome.WIN) {

            endgameStudyPanel.setStatus(
                    "Best move ✓ — opponent played the longest defense."
            );

        } else {

            endgameStudyPanel.setStatus(
                    "Exact defense played."
            );
        }


        updateBackButton();
    }


    // =========================================================
    // Exact Endgame Study tablebase transition
    // =========================================================

    private ExactEndgameTablebase tablebaseForExactPosition(
            Position position
    ) {

        if (position == null) {
            return null;
        }


        List<Piece> nonKings =
                new ArrayList<>();


        for (int rank = 0;
             rank < 8;
             rank++) {

            for (int file = 0;
                 file < 8;
                 file++) {

                Piece piece =
                        position.getBoard()
                                .getPiece(
                                        new Square(
                                                file,
                                                rank
                                        )
                                );


                if (piece == null
                        || piece.type()
                        == PieceType.KING) {

                    continue;
                }


                nonKings.add(
                        piece
                );


                if (nonKings.size() > 2) {
                    return null;
                }
            }
        }


        if (nonKings.size() == 1) {

            Piece piece =
                    nonKings.get(
                            0
                    );


            PieceType type =
                    piece.type();


            if (type != PieceType.QUEEN
                    && type != PieceType.ROOK
                    && type != PieceType.PAWN) {

                return null;
            }


            return ExactEndgameTablebase.of(
                    threePieceTablebaseService.get(
                            type,
                            piece.color()
                    )
            );
        }


        if (nonKings.size() == 2) {

            /*
             * M63D:
             *
             * The unified exact facade is now the sole four-piece routing
             * authority.  It owns the complete persisted 30-family catalog:
             * Tier 0, Tier 1, KPPK, and EP-aware KP-KP.
             *
             * Keeping material knowledge out of ChessWindow also means both
             * SAME_SIDE and SPLIT (2-v-2) continuations stay exact after every
             * move.
             */
            ExactEndgameTablebase exactTablebase =
                    endgameMoveController.getTablebase();


            ExactEndgameTablebase.Probe probe =
                    exactTablebase.probe(
                            position
                    );


            if (probe.outcome()
                    == ExactEndgameTablebase.Outcome.UNSUPPORTED) {

                return null;
            }


            return exactTablebase;
        }


        return null;
    }


    // =========================================================
    // Exact Endgame Study move validation
    // =========================================================

    private boolean isExactEndgameStudyBestMove(
            Position parentPosition,
            Position playedPosition
    ) {

        if (activeEndgameTablebase == null
                || parentPosition == null
                || playedPosition == null) {

            return false;
        }


        ExactEndgameTablebase tablebase =
                tablebaseForExactPosition(
                        parentPosition
                );


        if (tablebase == null) {
            return false;
        }


        activeEndgameTablebase =
                tablebase;


        List<Move> bestMoves =
                tablebase.bestMoves(
                        parentPosition
                );


        for (Move bestMove :
                bestMoves) {

            Position expected =
                    parentPosition.makeMove(
                            bestMove
                    );


            if (samePosition(
                    expected,
                    playedPosition
            )) {

                return true;
            }
        }


        return false;
    }


    private void restoreCurrentEndgameStudyPosition() {

        if (gameHistory.isEmpty()) {
            return;
        }


        Position current =
                gameHistory.get(
                        gameHistory.size() - 1
                );


        previewHistory.clear();

        boardPanel.clearPreview();

        boardPanel.setPosition(
                current
        );

        boardPanel.revalidate();
        boardPanel.repaint();

        updateBackButton();
    }


    // =========================================================
    // Undo manual move
    // =========================================================

    private void undoManualMove() {

        if (gameHistory.size() <= 1) {

            return;
        }


        Position removed =
                gameHistory.remove(
                        gameHistory.size() - 1
                );


        manualRedoHistory.push(
                removed
        );


        Position previous =
                gameHistory.get(
                        gameHistory.size() - 1
                );


        if (isStockfishAnalysisMode()) {

            previewHistory.clear();

            analysisMoveNavigationArmed =
                    false;

            analysisPanel.clearKeyboardPathNavigation();
            stockfishCandidatePanel.clearKeyboardPathNavigation();

            boardPanel.clearPreview();

            stockfishCandidatePanel.rewindToCommittedPosition(
                    previous,
                    gameHistory.get(0)
            );

            boardPanel.setPosition(
                    previous
            );

            requestStockfishModeCandidates(
                    previous
            );

            updateBackButton();

            return;
        }


        previewHistory.clear();

        analysisMoveNavigationArmed =
                false;

        analysisPanel.clearKeyboardPathNavigation();


        boardPanel.clearPreview();


        /*
         * The visible analysis path mirrors committed manual history.
         */
        analysisPanel.goBackOneLevel();


        boardPanel.setPosition(
                previous
        );


        Position selected =
                analysisPanel.getSelectedPosition();


        if (selected != null) {

            evaluationBar.setAnalysis(
                    analysisPanel.getSelectedEvaluation(),
                    analysisPanel.getSelectedOutcome(),
                    analysisPanel.getSelectedMateDistance()
            );

        } else if (currentAnalysis != null) {

            evaluationBar.setAnalysis(
                    currentAnalysis.getEvaluation(),
                    currentAnalysis.getOutcome(),
                    currentAnalysis.getMateDistance()
            );
        }


        retargetSearchBias();

        requestStockfishComparison(
                previous
        );


        updateBackButton();
    }


    private boolean redoManualMove() {

        if (manualRedoHistory.isEmpty()
                || gameHistory.isEmpty()) {

            return false;
        }


        Position restored =
                manualRedoHistory.pop();

        Position parent =
                gameHistory.get(
                        gameHistory.size() - 1
                );


        if (isStockfishAnalysisMode()) {

            String manualSan =
                    findManualMoveSan(
                            parent,
                            restored
                    );


            gameHistory.add(
                    restored
            );


            previewHistory.clear();

            analysisMoveNavigationArmed =
                    false;

            analysisPanel.clearKeyboardPathNavigation();
            stockfishCandidatePanel.clearKeyboardPathNavigation();

            boardPanel.clearPreview();

            stockfishCandidatePanel.commitManualPosition(
                    parent,
                    restored,
                    manualSan
            );

            boardPanel.setPosition(
                    restored
            );

            requestStockfishModeCandidates(
                    restored
            );

            updateBackButton();

            return true;
        }


        if (!engine.ensureManualContinuation(
                parent,
                restored
        )) {

            /*
             * The position may still be valid if the graph already owned the
             * edge through another canonical route. Continue with the visual
             * path reconstruction rather than restarting search.
             */
        }


        analysisPanel.commitPositionToPath(
                restored
        );


        gameHistory.add(
                restored
        );


        previewHistory.clear();
        boardPanel.clearPreview();

        boardPanel.setPosition(
                restored
        );


        Position selected =
                analysisPanel.getSelectedPosition();


        if (selected != null) {

            evaluationBar.setAnalysis(
                    analysisPanel.getSelectedEvaluation(),
                    analysisPanel.getSelectedOutcome(),
                    analysisPanel.getSelectedMateDistance()
            );
        }


        retargetSearchBias();

        requestStockfishComparison(
                restored
        );

        updateBackButton();

        return true;
    }


    private boolean resetManualHistoryToStart() {

        if (gameHistory.size() <= 1) {
            return false;
        }


        boolean changed =
                false;


        while (gameHistory.size() > 1) {

            undoManualMove();
            changed =
                    true;
        }


        return changed;
    }


    private boolean restoreManualHistoryToLatest() {

        if (manualRedoHistory.isEmpty()) {
            return false;
        }


        boolean changed =
                false;


        while (!manualRedoHistory.isEmpty()) {

            if (!redoManualMove()) {
                break;
            }

            changed =
                    true;
        }


        return changed;
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
                            data.getEvaluation(),
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
    // Board loading overlay
    // =========================================================

    private void showBoardLoading(
            long generation,
            String tablebaseName
    ) {

        boardLoadingGeneration =
                generation;

        /*
         * The board paints its own dimming scrim using the exact same
         * 640 x 640 coordinates as the chess squares.  Keeping the scrim in
         * ChessBoardPanel removes the one-pixel/edge mismatch that can occur
         * when a separate Swing overlay is laid out independently.
         */
        if (boardPanel != null) {
            boardPanel.setLoadingDimmed(true);
        }

        if (boardLoadingOverlay == null) {
            return;
        }

        String detail =
                tablebaseName == null || tablebaseName.isBlank()
                        ? "Preparing the next exact study..."
                        : "Loading exact "
                        + tablebaseName
                        + " tablebase...";

        boardLoadingOverlay.showLoading(
                "Loading exact position...",
                detail
        );

        if (boardStack != null) {
            boardStack.revalidate();
            boardStack.repaint();
        }
    }


    private void hideBoardLoading(
            long generation
    ) {

        if (boardLoadingGeneration != generation) {
            return;
        }

        boardLoadingGeneration =
                -1L;

        if (boardPanel != null) {
            boardPanel.setLoadingDimmed(false);
        }

        if (boardLoadingOverlay != null) {
            boardLoadingOverlay.hideLoading();
        }

        if (boardStack != null) {
            boardStack.repaint();
        }
    }


    private void hideBoardLoading() {

        boardLoadingGeneration =
                -1L;

        if (boardPanel != null) {
            boardPanel.setLoadingDimmed(false);
        }

        if (boardLoadingOverlay != null) {
            boardLoadingOverlay.hideLoading();
        }

        if (boardStack != null) {
            boardStack.repaint();
        }
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
                state.evaluation,
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
                    currentAnalysis.getEvaluation(),
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

    private void showEndgameCurriculum() {

        piecePalettePanel.cancelActiveDrag();
        setAnalysisEngineSelectorEnabled(false);


        /*
         * Preserve the original pre-M73 Setup layout exactly.
         *
         * The only bug being fixed here is the stray Setup footer/marks that
         * could remain visible when Endgame was opened directly from Setup.
         * Do not move or redesign PiecePalettePanel.
         */
        if (boardPanel.isSetupMode()) {

            boardPanel.cancelSetupMode();

            updateBoardAreaInsetsForCurrentMode();
            updateApplicationHeaderBorderForCurrentMode();

            piecePalettePanel.setVisible(
                    false
            );

            if (setupPaletteHost != null) {

                setupPaletteHost.setVisible(
                        false
                );
            }

            setupPanel.setVisible(
                    false
            );

            refreshWorkspaceLayout();
        }


        // Endgame is now a single exact-training experience.
        // The former Solver chooser and practice-strength UI are gone.
        endgameMoveController.setPracticeMode(false);
        endgameMoveController.setPracticeStrength(100);
        endgameStudyMode = true;
        generateNextCurriculumStudy();
    }


    private void generateEndgame(
            EndgameSettings settings
    ) {

        if (settings == null) {
            return;
        }

        /*
         * Fixed 3 is now a true tablebase-backed study mode.
         *
         * Instead of generating an arbitrary position and then spending
         * seconds/minutes trying to prove it, choose a solved material
         * class first and ask its prebuilt tablebase for a position that
         * is already known to be a WIN for the side to move.
         */
        if (settings.isFixed()
                && settings.minimumPieces() == 3) {

            generateExactThreePieceStudy(
                    settings
            );

            return;
        }


        if (settings.isFixed()
                && settings.minimumPieces() == 4) {

            generateExactFourPieceStudy(
                    settings
            );

            return;
        }


        /*
         * Larger endgames still use the existing generator/proof path
         * until their exact tablebases are implemented.
         */
        activeEndgameTablebase =
                null;

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


    private void generateExactThreePieceStudy(
            EndgameSettings settings
    ) {

        stopGuiExplorationChain();

        analysisRequestId++;
        endgameProofGeneration++;

        endgameStudyMode =
                true;

        endgameStudyReady =
                false;

        resetEndgameMoveReview();

        regeneratingRejectedEndgame =
                false;

        lastEndgameSettings =
                settings;


        java.util.Random studyRandom =
                curriculumRandom(
                        tablebaseNameForSelection(
                                selectedEndgameFamily
                        )
                );


        /*
         * Choose a review BEFORE choosing the three-piece tablebase.
         *
         * This matters for two reasons:
         *
         * 1. Mixed mode must be able to pull a review from KQK, KRK, or KPK
         *    instead of first choosing a random family and hoping that family
         *    happens to have a queued review.
         *
         * 2. A queued review already determines whether the strong piece is
         *    White or Black.  The old code chose that color randomly first,
         *    which meant a perfectly valid review could fail its tablebase
         *    probe about half the time and be delayed by another fresh study.
         */
        String pacingKey =
                endgameReviewPacingKey(
                        selectedEndgameFamily
                );

        String scheduledReviewId =
                null;

        Position scheduledReviewPosition =
                null;

        if (shouldServeEndgameReview(pacingKey)) {

            if ("Mixed".equals(selectedEndgameFamily)) {

                scheduledReviewId =
                        nextMixedThreePieceReviewId(
                                studyRandom,
                                currentEndgameStudyId
                        );

            } else {

                scheduledReviewId =
                        endgameProgress.nextReviewId(
                                selectedEndgameFamily,
                                endgameProgress.studyOrder(
                                        selectedEndgameFamily
                                ),
                                studyRandom,
                                currentEndgameStudyId
                        );
            }

            if (scheduledReviewId != null) {
                try {
                    String reviewFen =
                            scheduledReviewId.substring(
                                    scheduledReviewId.indexOf('|') + 1
                            );

                    scheduledReviewPosition =
                            FenCodec.parse(reviewFen);

                } catch (RuntimeException ignored) {
                    scheduledReviewId = null;
                    scheduledReviewPosition = null;
                }
            }
        }


        String materialSelection =
                scheduledReviewId == null
                        ? selectedEndgameFamily
                        : familyFromEndgameStudyId(
                        scheduledReviewId
                );


        PieceType[] supportedMaterials = {
                PieceType.QUEEN,
                PieceType.ROOK,
                PieceType.PAWN
        };


        PieceType majorType =
                switch (materialSelection) {
                    case "KQK" -> PieceType.QUEEN;
                    case "KRK" -> PieceType.ROOK;
                    case "KPK" -> PieceType.PAWN;
                    default -> supportedMaterials[
                            endgameStudyRandom.nextInt(
                                    supportedMaterials.length
                            )
                            ];
                };


        main.java.chess.model.Color majorColor =
                scheduledReviewPosition == null
                        ? (studyRandom.nextBoolean()
                        ? main.java.chess.model.Color.WHITE
                        : main.java.chess.model.Color.BLACK)
                        : majorPieceColor(
                        scheduledReviewPosition,
                        majorType
                );


        String tablebaseName =
                switch (majorType) {

                    case QUEEN ->
                            "KQK";

                    case ROOK ->
                            "KRK";

                    case PAWN ->
                            "KPK";

                    default ->
                            throw new IllegalStateException(
                                    "Unsupported exact three-piece study material: "
                                            + majorType
                            );
                };


        endgameStudyPanel.setVisible(
                true
        );


        evaluationBar.setVisible(
                false
        );


        analysisEngineCards.setVisible(false);


        setupPanel.setVisible(
                false
        );

        showAuxiliaryAnalysisCard(
                "ENDGAME"
        );


        boardPanel.setEnabled(
                false
        );


        pendingEndgameFamily = tablebaseName;

        endgameStudyPanel.setLoadingTablebase(
                tablebaseName
        );


        headerSubtitleLabel.setText(
                settings.displayName()
                        + " exact endgame study"
        );


        analysisArea.revalidate();
        analysisArea.repaint();


        long generation =
                endgameProofGeneration;

        showBoardLoading(
                generation,
                tablebaseName
        );

        final String reviewIdForWorker =
                scheduledReviewId;

        final Position reviewPositionForWorker =
                scheduledReviewPosition;

        final String pacingKeyForDelivery =
                pacingKey;


        SwingWorker<
                ExactStudyLoad,
                Void
                > worker =

                new SwingWorker<>() {


                    @Override
                    protected ExactStudyLoad doInBackground() {

                        ThreePieceTablebase tablebase =
                                threePieceTablebaseService.get(
                                        majorType,
                                        majorColor
                                );


                        Position position = null;
                        Position fallback = null;
                        boolean reviewPositionUsed = false;


                        if (reviewIdForWorker != null
                                && reviewPositionForWorker != null) {

                            try {
                                if (tablebase.probe(
                                        reviewPositionForWorker
                                ).outcome()
                                        == ThreePieceTablebase.Outcome.WIN) {

                                    position =
                                            reviewPositionForWorker;

                                    reviewPositionUsed =
                                            true;
                                }

                            } catch (RuntimeException ignored) {
                                // Corrupt/stale review entries safely fall back.
                            }
                        }


                        // Curriculum rule: prefer a position never studied before.
                        for (int attempt = 0;
                             position == null && attempt < 64;
                             attempt++) {

                            Position candidate =
                                    tablebase.randomWinningPosition(
                                            studyRandom
                                    );

                            if (fallback == null) {
                                fallback = candidate;
                            }

                            String candidateId =
                                    tablebaseName
                                            + "|"
                                            + FenCodec.toFen(candidate);

                            if (endgameProgress.get(candidateId).status()
                                    == EndgameStudyProgress.Status.UNSEEN) {
                                position = candidate;
                                break;
                            }
                        }


                        if (position == null) {
                            position = fallback;
                        }


                        return new ExactStudyLoad(
                                ExactEndgameTablebase.of(tablebase),
                                position,
                                tablebase.getWinCount(),
                                tablebase.getLegalStateCount(),
                                reviewPositionUsed
                        );
                    }


                    @Override
                    protected void done() {

                        if (generation
                                != endgameProofGeneration
                                ||
                                !endgameStudyMode) {

                            hideBoardLoading(
                                    generation
                            );

                            return;
                        }


                        try {

                            ExactStudyLoad loaded =
                                    get();


                            activeEndgameTablebase =
                                    loaded.tablebase;

                            currentEndgameCurriculumTotal =
                                    loaded.curriculumTotal;

                            currentEndgameLegalTotal =
                                    loaded.legalTotal;


                            installExactStudyPosition(
                                    loaded.position,
                                    settings
                            );

                            recordEndgameStudyDelivery(
                                    pacingKeyForDelivery,
                                    loaded.reviewPosition
                            );


                        } catch (Exception exception) {

                            exception.printStackTrace();


                            activeEndgameTablebase =
                                    null;


                            endgameStudyReady =
                                    false;


                            endgameStudyPanel.setStatus(
                                    "Could not load exact study: "
                                            + getUsefulMessage(
                                            exception
                                    )
                            );


                            boardPanel.setEnabled(
                                    true
                            );

                        } finally {

                            hideBoardLoading(
                                    generation
                            );
                        }
                    }
                };


        worker.execute();
    }


    private void generateExactFourPieceStudy(
            EndgameSettings settings
    ) {

        stopGuiExplorationChain();

        analysisRequestId++;
        endgameProofGeneration++;

        endgameStudyMode =
                true;

        endgameStudyReady =
                false;

        resetEndgameMoveReview();

        regeneratingRejectedEndgame =
                false;

        lastEndgameSettings =
                settings;


        java.util.Random studyRandom =
                curriculumRandom(
                        tablebaseNameForSelection(
                                selectedEndgameFamily
                        )
                );

        String pacingKey =
                endgameReviewPacingKey(
                        selectedEndgameFamily
                );

        String scheduledReviewId =
                null;

        Position scheduledReviewPosition =
                null;

        if (shouldServeEndgameReview(pacingKey)) {
            if ("Mixed".equals(selectedEndgameFamily)) {
                scheduledReviewId =
                        nextMixedFourPieceReviewId(
                                studyRandom,
                                currentEndgameStudyId
                        );
            } else {
                scheduledReviewId =
                        endgameProgress.nextReviewId(
                                selectedEndgameFamily,
                                endgameProgress.studyOrder(
                                        selectedEndgameFamily
                                ),
                                studyRandom,
                                currentEndgameStudyId
                        );
            }

            if (scheduledReviewId != null) {
                try {
                    String reviewFen =
                            scheduledReviewId.substring(
                                    scheduledReviewId.indexOf('|') + 1
                            );

                    scheduledReviewPosition =
                            FenCodec.parse(reviewFen);

                } catch (RuntimeException ignored) {
                    scheduledReviewId = null;
                    scheduledReviewPosition = null;
                }
            }
        }


        /*
         * M63D:
         *
         * Four-piece Endgame Study samples the complete canonical catalog.
         * When a Mixed review is due, the queued review chooses the material
         * family first so the schedule cannot drift by one or more studies.
         */
        FourPieceMaterialClass selectedMaterial =
                scheduledReviewId == null
                        ? selectedFourPieceMaterialOrRandom()
                        : fourPieceMaterialForFamily(
                        familyFromEndgameStudyId(
                                scheduledReviewId
                        )
                );

        if (selectedMaterial == null) {
            selectedMaterial = selectedFourPieceMaterialOrRandom();
            scheduledReviewId = null;
            scheduledReviewPosition = null;
        }

        final FourPieceMaterialClass material =
                selectedMaterial;

        String materialName =
                material.assetStem();

        pendingEndgameFamily = materialName;

        final Position reviewPositionForWorker =
                scheduledReviewPosition;

        final String reviewIdForWorker =
                scheduledReviewId;

        final String pacingKeyForDelivery =
                pacingKey;


        endgameStudyPanel.setVisible(
                true
        );


        evaluationBar.setVisible(
                false
        );


        analysisEngineCards.setVisible(false);


        setupPanel.setVisible(
                false
        );

        showAuxiliaryAnalysisCard(
                "ENDGAME"
        );


        boardPanel.setEnabled(
                false
        );


        endgameStudyPanel.setLoadingTablebase(
                materialName
        );


        headerSubtitleLabel.setText(
                settings.displayName()
                        + " exact "
                        + materialName
                        + " study"
        );


        analysisArea.revalidate();
        analysisArea.repaint();


        long generation =
                endgameProofGeneration;

        showBoardLoading(
                generation,
                materialName
        );


        SwingWorker<
                ExactStudyLoad,
                Void
                > worker =

                new SwingWorker<>() {

                    @Override
                    protected ExactStudyLoad doInBackground() {

                        ExactEndgameTablebase exactTablebase =
                                endgameMoveController.getTablebase();


                        Position position = null;


                        boolean reviewPositionUsed = false;

                        if (reviewIdForWorker != null
                                && reviewPositionForWorker != null) {
                            try {
                                if (exactTablebase.probe(
                                        reviewPositionForWorker
                                ).outcome()
                                        == ExactEndgameTablebase.Outcome.WIN) {

                                    position = reviewPositionForWorker;
                                    reviewPositionUsed = true;
                                }
                            } catch (RuntimeException ignored) {
                                position = null;
                            }
                        }


                        if (position == null) {

                            Position fallback = null;

                            /*
                             * Curriculum rule: prefer an unseen exact root in
                             * this family.
                             *
                             * IMPORTANT: generateWinningStudy(...) now
                             * selects directly from the solved tablebase WIN
                             * array. It no longer throws away random board
                             * geometries hoping to stumble onto a rare WIN.
                             * This makes Next Position reliable even for very
                             * draw-heavy four-piece families.
                             */
                            for (int curriculumAttempt = 0;
                                 curriculumAttempt < 12;
                                 curriculumAttempt++) {

                                FourPieceStudyPositionGenerator.StudyRoot root =
                                        fourPieceStudyPositionGenerator.generateWinningStudy(
                                                material,
                                                exactTablebase,
                                                studyRandom
                                        );

                                Position candidate =
                                        root.position();

                                if (fallback == null) {
                                    fallback = candidate;
                                }

                                String candidateId =
                                        materialName
                                                + "|"
                                                + FenCodec.toFen(
                                                candidate
                                        );

                                if (endgameProgress.get(candidateId).status()
                                        == EndgameStudyProgress.Status.UNSEEN) {

                                    position = candidate;
                                    break;
                                }
                            }

                            if (position == null) {
                                position = fallback;
                            }
                        }


                        /*
                         * Prewarm and verify the exact runtime on the worker
                         * thread before enabling the board.
                         */
                        ExactEndgameTablebase.Probe probe =
                                exactTablebase.probe(
                                        position
                                );


                        if (probe.outcome()
                                != ExactEndgameTablebase.Outcome.WIN) {

                            throw new IllegalStateException(
                                    "The exact runtime did not recognize the generated "
                                            + materialName
                                            + " study root as a WIN."
                            );
                        }


                        return new ExactStudyLoad(
                                exactTablebase,
                                position,
                                0,
                                0,
                                reviewPositionUsed
                        );
                    }


                    @Override
                    protected void done() {

                        if (generation
                                != endgameProofGeneration
                                ||
                                !endgameStudyMode) {

                            hideBoardLoading(
                                    generation
                            );

                            return;
                        }


                        try {

                            ExactStudyLoad loaded =
                                    get();


                            activeEndgameTablebase =
                                    loaded.tablebase;
                            currentEndgameCurriculumTotal = loaded.curriculumTotal;
                            currentEndgameLegalTotal = loaded.legalTotal;


                            installExactStudyPosition(
                                    loaded.position,
                                    settings
                            );

                            recordEndgameStudyDelivery(
                                    pacingKeyForDelivery,
                                    loaded.reviewPosition
                            );


                            if (material.distribution()
                                    == FourPieceMaterialClass.Distribution.SPLIT) {

                                endgameStudyPanel.setStatus(
                                        "Exact "
                                                + materialName
                                                + " 2-v-2 solution loaded."
                                );

                            } else if (material.equals(
                                    FourPieceMaterialClass.sameSide(
                                            PieceType.QUEEN,
                                            PieceType.PAWN
                                    )
                            )) {

                                endgameStudyPanel.setStatus(
                                        "Exact KQPK solution loaded."
                                );
                            }

                        } catch (Exception exception) {

                            exception.printStackTrace();


                            activeEndgameTablebase =
                                    null;

                            endgameStudyReady =
                                    false;


                            endgameStudyPanel.setStatus(
                                    "Could not load exact "
                                            + materialName
                                            + " study: "
                                            + getUsefulMessage(
                                            exception
                                    )
                            );


                            boardPanel.setEnabled(
                                    true
                            );

                        } finally {

                            hideBoardLoading(
                                    generation
                            );
                        }
                    }
                };


        worker.execute();
    }


    private void installExactStudyPosition(
            Position position,
            EndgameSettings settings
    ) {

        if (position == null
                || activeEndgameTablebase == null) {

            return;
        }


        previewHistory.clear();


        gameHistory.clear();
        manualRedoHistory.clear();


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


        currentEndgameFamily = pendingEndgameFamily;
        currentEndgameStudyId =
                currentEndgameFamily + "|" + FenCodec.toFen(position);
        currentEndgameStudyClean = true;
        currentEndgameAttemptRecorded = false;
        String cursorFamily = selectedEndgameFamily == null ? currentEndgameFamily : selectedEndgameFamily;
        endgameProgress.advanceCursor(cursorFamily);
        saveEndgameProgress();

        endgameStudyPanel.setPosition(
                position,
                settings
        );
        endgameStudyPanel.setFamilyDisplay(currentEndgameFamily);
        syncEndgameMoveReviewToLatest();
        refreshEndgameProgressPanel();


        /*
         * The position came directly from randomWinningPosition(), so
         * exact proof already exists in the loaded tablebase.
         */
        ExactEndgameTablebase.Probe probe =
                activeEndgameTablebase.probe(
                        position
                );


        if (probe.outcome()
                != ExactEndgameTablebase.Outcome.WIN) {

            throw new IllegalStateException(
                    "Tablebase selected a study root that is not a WIN."
            );
        }


        endgameStudyReady =
                true;


        endgameStudyPanel.setProvenMate(
                probe.mateInMoves()
        );


        boardPanel.setEnabled(
                true
        );


        updateBackButton();


        analysisArea.revalidate();
        analysisArea.repaint();
    }


    private void loadEndgameStudyPosition(
            Position position,
            EndgameSettings settings
    ) {

        if (position == null) {

            return;
        }


        activeEndgameTablebase =
                null;


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
        manualRedoHistory.clear();


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
         * Preload the generic four-piece tablebase off the Swing event
         * thread. The first controller probe can require reading and
         * decoding a compressed tablebase asset; doing that only after
         * the player's first best move would make the GUI appear to
         * freeze before the automatic reply.
         *
         * The service caches the loaded tablebase, so later probes and
         * move selections for this material class are fast.
         */
        prewarmEndgameTablebase(
                position,
                proofGeneration
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


    // =========================================================
    // Endgame Study tablebase prewarming
    // =========================================================

    private void prewarmEndgameTablebase(
            Position position,
            long proofGeneration
    ) {

        if (position == null) {

            return;
        }


        SwingWorker<
                Void,
                Void
                > worker =

                new SwingWorker<>() {


                    @Override
                    protected Void doInBackground() {

                        /*
                         * EndgameMoveController owns the generic Tier-0
                         * tablebase service used later by automatic
                         * defense. Probing here forces the relevant asset
                         * to load into that same service's cache.
                         *
                         * No Swing component is touched from this worker.
                         */
                        endgameMoveController.probe(
                                position
                        );


                        return null;
                    }


                    @Override
                    protected void done() {

                        /*
                         * There is deliberately no visible success state.
                         * Prewarming is a performance optimization, not a
                         * prerequisite for the study proof.
                         *
                         * If a newer endgame has replaced this one, the
                         * completed cache load is harmless and may still
                         * be useful later.
                         */
                        if (proofGeneration
                                != endgameProofGeneration) {

                            return;
                        }


                        try {

                            get();

                        } catch (Exception exception) {

                            /*
                             * Do not reject an otherwise valid study just
                             * because speculative prewarming failed.
                             * The normal controller/tablebase path retains
                             * its existing unsupported/fallback behavior.
                             */
                            System.err.println(
                                    "Endgame tablebase prewarm failed: "
                                            + getUsefulMessage(
                                            exception
                                    )
                            );
                        }
                    }
                };


        worker.execute();
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

        piecePalettePanel.cancelActiveDrag();
        setAnalysisEngineSelectorEnabled(false);


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


        analysisEngineCards.setVisible(false);


        setupPanel.setVisible(
                false
        );


        endgameStudyPanel.setVisible(
                true
        );

        showAuxiliaryAnalysisCard(
                "ENDGAME"
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


    private List<String> endgameCurriculumFamilies() {
        List<String> families = new ArrayList<>();
        families.add("Mixed");
        families.add("KQK");
        families.add("KRK");
        families.add("KPK");
        for (FourPieceMaterialClass material : fourPieceStudyPositionGenerator.catalog()) {
            families.add(material.assetStem());
        }
        return List.copyOf(families);
    }

    private void selectEndgameFamily(String family) {
        if (family == null || family.isBlank()) return;
        deferCurrentEndgameIfIncomplete();
        selectedEndgameFamily = family;
        currentEndgameCurriculumTotal = 0;
        currentEndgameLegalTotal = 0;
        refreshEndgameProgressPanel();
        if ("KQK".equals(family) || "KRK".equals(family) || "KPK".equals(family)) {
            generateEndgame(EndgameSettings.fixed(3));
        } else if (!"Mixed".equals(family)) {
            generateEndgame(EndgameSettings.fixed(4));
        } else {
            /*
             * Switching back to Mixed immediately delivers a new Mixed study.
             * The study size is selected randomly rather than alternating.
             */
            generateNextCurriculumStudy();
        }
    }


    private String tablebaseNameForSelection(String family) {
        if ("KQK".equals(family) || "KRK".equals(family) || "KPK".equals(family)) return family;
        return family == null || "Mixed".equals(family) ? "Mixed" : family;
    }

    private FourPieceMaterialClass selectedFourPieceMaterialOrRandom() {
        if (!"Mixed".equals(selectedEndgameFamily)) {
            for (FourPieceMaterialClass material : fourPieceStudyPositionGenerator.catalog()) {
                if (material.assetStem().equals(selectedEndgameFamily)) return material;
            }
        }
        return fourPieceStudyPositionGenerator.randomMaterial(endgameStudyRandom);
    }

    private void generateNextCurriculumStudy() {
        if ("KQK".equals(selectedEndgameFamily)
                || "KRK".equals(selectedEndgameFamily)
                || "KPK".equals(selectedEndgameFamily)) {

            generateEndgame(EndgameSettings.fixed(3));

        } else if (!"Mixed".equals(selectedEndgameFamily)) {

            generateEndgame(EndgameSettings.fixed(4));

        } else {
            /*
             * Mixed must choose its own study size instead of reusing
             * lastEndgameSettings (which historically left Mixed stuck on
             * three-piece studies). Fresh studies are a true 50/50 random
             * choice between three and four pieces.
             */
            generateEndgame(
                    EndgameSettings.fixed(
                            nextMixedStudyPieceCount()
                    )
            );
        }
    }


    private int nextMixedStudyPieceCount() {
        java.util.Random random =
                curriculumRandom(
                        "Mixed"
                );

        boolean reviewDue =
                shouldServeEndgameReview(
                        "Mixed"
                );

        boolean threePieceReview =
                reviewDue
                        && hasEligibleMixedThreePieceReview(
                        currentEndgameStudyId
                );

        boolean fourPieceReview =
                reviewDue
                        && hasEligibleMixedFourPieceReview(
                        currentEndgameStudyId
                );

        return EndgameTrainerRules.chooseMixedPieceCount(
                random,
                reviewDue,
                threePieceReview,
                fourPieceReview
        );
    }


    private void advanceCurriculumPosition() {
        deferCurrentEndgameIfIncomplete();
        generateNextCurriculumStudy();
    }


    // =========================================================
    // Endgame review pacing
    // =========================================================

    /**
     * Mixed is paced as one curriculum. Specific families keep independent
     * pacing counters.
     */
    private String endgameReviewPacingKey(
            String actualFamily
    ) {
        if ("Mixed".equals(selectedEndgameFamily)) {
            return "Mixed";
        }

        return actualFamily == null || actualFamily.isBlank()
                ? selectedEndgameFamily
                : actualFamily;
    }


    /**
     * Mixed has no physical queue of its own; its visible review stack is the
     * sum of the real material-family queues.
     */
    private int endgameReviewCountForSelection(
            String family
    ) {
        if (!"Mixed".equals(family)) {
            return family == null
                    ? 0
                    : endgameProgress.reviewCount(family);
        }

        return EndgameTrainerRules.aggregateReviewCount(
                endgameProgress,
                endgameCurriculumFamilies()
        );
    }


    /**
     * Returns true only when the selected curriculum has a review backlog and
     * exactly four fresh studies have already been delivered since the previous
     * review.
     */
    private boolean shouldServeEndgameReview(
            String pacingKey
    ) {
        if (pacingKey == null || pacingKey.isBlank()) {
            return false;
        }

        return EndgameTrainerRules.reviewDue(
                endgameReviewCountForSelection(
                        pacingKey
                ),
                endgameFreshStudiesSinceReview.getOrDefault(
                        pacingKey,
                        0
                )
        );
    }


    /**
     * Updates only the pacing counter. The persistent review queue itself is
     * still owned by EndgameStudyProgress.
     */
    private void recordEndgameStudyDelivery(
            String pacingKey,
            boolean reviewPosition
    ) {
        if (pacingKey == null || pacingKey.isBlank()) {
            return;
        }

        int currentFresh =
                endgameFreshStudiesSinceReview.getOrDefault(
                        pacingKey,
                        0
                );

        endgameFreshStudiesSinceReview.put(
                pacingKey,
                EndgameTrainerRules.freshCountAfterDelivery(
                        currentFresh,
                        reviewPosition
                )
        );
    }


    private boolean hasEligibleMixedThreePieceReview(
            String excludeId
    ) {
        Map<String, List<String>> snapshot =
                endgameProgress.reviewSnapshot();

        for (String family : List.of(
                "KQK",
                "KRK",
                "KPK"
        )) {
            if (hasEligibleReviewInQueue(
                    snapshot.get(family),
                    excludeId
            )) {
                return true;
            }
        }

        return false;
    }


    private boolean hasEligibleMixedFourPieceReview(
            String excludeId
    ) {
        Map<String, List<String>> snapshot =
                endgameProgress.reviewSnapshot();

        for (FourPieceMaterialClass material
                : fourPieceStudyPositionGenerator.catalog()) {
            if (hasEligibleReviewInQueue(
                    snapshot.get(
                            material.assetStem()
                    ),
                    excludeId
            )) {
                return true;
            }
        }

        return false;
    }


    private boolean hasEligibleReviewInQueue(
            List<String> queue,
            String excludeId
    ) {
        if (queue == null) {
            return false;
        }

        for (String id : queue) {
            if (id == null
                    || id.equals(excludeId)
                    || endgameProgress.get(id).status()
                    == EndgameStudyProgress.Status.MASTERED) {
                continue;
            }

            return true;
        }

        return false;
    }


    /**
     * Mixed three-piece review selection is global across KQK, KRK, and KPK.
     * This avoids choosing a random material first and then discovering that
     * the due review belongs to a different family.
     */
    private String nextMixedThreePieceReviewId(
            java.util.Random random,
            String excludeId
    ) {
        List<String> eligible =
                new ArrayList<>();

        Map<String, List<String>> snapshot =
                endgameProgress.reviewSnapshot();

        for (String family : List.of(
                "KQK",
                "KRK",
                "KPK"
        )) {
            List<String> queue =
                    snapshot.get(family);

            if (queue == null) {
                continue;
            }

            for (String id : queue) {
                if (id == null
                        || id.equals(excludeId)
                        || endgameProgress.get(id).status()
                        == EndgameStudyProgress.Status.MASTERED) {
                    continue;
                }

                eligible.add(id);
            }
        }

        if (eligible.isEmpty()) {
            return null;
        }

        if (endgameProgress.studyOrder("Mixed")
                == EndgameStudyProgress.StudyOrder.SHUFFLE) {
            return eligible.get(
                    random.nextInt(eligible.size())
            );
        }

        return eligible.get(0);
    }


    private String nextMixedFourPieceReviewId(
            java.util.Random random,
            String excludeId
    ) {
        List<String> eligible =
                new ArrayList<>();

        Map<String, List<String>> snapshot =
                endgameProgress.reviewSnapshot();

        for (FourPieceMaterialClass material
                : fourPieceStudyPositionGenerator.catalog()) {

            String family =
                    material.assetStem();

            List<String> queue =
                    snapshot.get(family);

            if (queue == null) {
                continue;
            }

            for (String id : queue) {
                if (id == null
                        || id.equals(excludeId)
                        || endgameProgress.get(id).status()
                        == EndgameStudyProgress.Status.MASTERED) {
                    continue;
                }

                eligible.add(id);
            }
        }

        if (eligible.isEmpty()) {
            return null;
        }

        if (endgameProgress.studyOrder("Mixed")
                == EndgameStudyProgress.StudyOrder.SHUFFLE) {
            return eligible.get(
                    random.nextInt(eligible.size())
            );
        }

        return eligible.get(0);
    }


    private FourPieceMaterialClass fourPieceMaterialForFamily(
            String family
    ) {
        if (family == null || family.isBlank()) {
            return null;
        }

        for (FourPieceMaterialClass material
                : fourPieceStudyPositionGenerator.catalog()) {
            if (family.equals(material.assetStem())) {
                return material;
            }
        }

        return null;
    }


    private String familyFromEndgameStudyId(
            String id
    ) {
        if (id == null) {
            return null;
        }

        int split = id.indexOf('|');

        return split <= 0
                ? null
                : id.substring(0, split);
    }


    private main.java.chess.model.Color majorPieceColor(
            Position position,
            PieceType majorType
    ) {
        for (int rank = 0; rank < 8; rank++) {
            for (int file = 0; file < 8; file++) {
                Piece piece =
                        position.getBoard().getPiece(
                                new Square(file, rank)
                        );

                if (piece != null
                        && piece.type() == majorType) {
                    return piece.color();
                }
            }
        }

        throw new IllegalArgumentException(
                "Review position does not contain expected piece: "
                        + majorType
        );
    }


    // =========================================================
    // Endgame move review
    // =========================================================

    // =========================================================
    // Engine move-panel arrow-key navigation
    // =========================================================

    private void armStockfishMoveNavigation() {

        if (!isStockfishAnalysisMode()
                ||
                endgameStudyMode
                ||
                boardPanel.isSetupMode()) {

            stockfishCandidatePanel.clearKeyboardPathNavigation();

            return;
        }


        List<Position> selectedPath =
                stockfishCandidatePanel.getSelectedPathPositions();


        int floorDepth =
                getCommittedStockfishPrefixLength(
                        selectedPath
                );


        stockfishCandidatePanel.armKeyboardPathNavigation(
                floorDepth
        );
    }


    private int getCommittedStockfishPrefixLength(
            List<Position> selectedPath
    ) {

        if (selectedPath == null
                ||
                selectedPath.isEmpty()
                ||
                gameHistory.size() <= 1) {

            return 0;
        }


        int limit =
                Math.min(
                        selectedPath.size(),
                        gameHistory.size() - 1
                );


        int matched =
                0;


        while (matched < limit
                &&
                samePosition(
                        selectedPath.get(
                                matched
                        ),
                        gameHistory.get(
                                matched + 1
                        )
                )) {

            matched++;
        }


        return matched;
    }


    private Position stockfishNavigationFallbackRoot() {

        return stockfishCandidatePanel.getRootPosition(
                gameHistory.isEmpty()
                        ? boardPanel.getPosition()
                        : gameHistory.get(0)
        );
    }


    private void syncStockfishPreviewAfterKeyboardNavigation(
            Position target
    ) {

        if (target == null) {
            return;
        }


        Position actual =
                gameHistory.isEmpty()
                        ? null
                        : gameHistory.get(
                        gameHistory.size() - 1
                );


        if (actual != null
                &&
                samePosition(
                        actual,
                        target
                )) {

            boardPanel.clearPreview();

            boardPanel.setPosition(
                    actual
            );

        } else {

            boardPanel.setPreviewPosition(
                    target
            );
        }


        requestStockfishModeCandidates(
                target
        );


        updateBackButton();
    }


    private void armAnalysisMoveNavigation() {

        if (isStockfishAnalysisMode()
                ||
                endgameStudyMode
                ||
                boardPanel.isSetupMode()) {

            analysisMoveNavigationArmed =
                    false;

            analysisPanel.clearKeyboardPathNavigation();

            return;
        }

        List<AnalysisPanel.PreviewData> selectedPath =
                analysisPanel.getSelectedPathPreviewData();

        int floorDepth =
                getCommittedSelectedPrefixLength(
                        selectedPath
                );

        analysisPanel.armKeyboardPathNavigation(
                floorDepth
        );

        analysisMoveNavigationArmed =
                analysisPanel.hasKeyboardPathNavigation();
    }


    /**
     * Chess.com-style navigation for a clicked Dovetail/Hybrid move panel:
     *
     * Left  = previous preview move
     * Right = re-add / redo preview move
     * Up    = re-setup / restore deepest clicked preview line
     * Down  = reset preview suffix
     *
     * This is preview navigation only. It never mutates gameHistory and never
     * restarts the persistent search.
     */
    private void installAnalysisMoveNavigationKeyDispatcher() {

        if (analysisMoveNavigationKeyDispatcher != null) {

            return;
        }


        analysisMoveNavigationKeyDispatcher =
                event -> {

                    /*
                     * Setup has its own edit-history navigation.
                     *
                     * Left  = undo setup edit
                     * Right = redo setup edit
                     * Up    = restore latest edited setup state
                     * Down  = return to the setup starting state
                     *
                     * This never touches gameHistory or the persistent search.
                     */
                    if (boardPanel.isSetupMode()) {

                        if (!isSetupEditorArrowKey(
                                event
                        )) {

                            return false;
                        }


                        /*
                         * Consume both press and release so a focused setup
                         * button cannot also interpret the same arrow key.
                         */
                        if (event.getID()
                                != KeyEvent.KEY_PRESSED) {

                            return true;
                        }


                        switch (event.getKeyCode()) {

                            case KeyEvent.VK_LEFT ->
                                    boardPanel.undoSetupEdit();

                            case KeyEvent.VK_RIGHT ->
                                    boardPanel.redoSetupEdit();

                            case KeyEvent.VK_UP ->
                                    boardPanel.restoreLatestSetupEdit();

                            case KeyEvent.VK_DOWN ->
                                    boardPanel.resetSetupEditsToStart();

                            default -> {
                                return false;
                            }
                        }


                        return true;
                    }


                    if (!isAnalysisMoveNavigationArrowKey(
                            event
                    )) {

                        return false;
                    }


                    /*
                     * Consume both key press and release so focused Swing
                     * controls cannot also interpret the same arrow.
                     */
                    if (event.getID()
                            != KeyEvent.KEY_PRESSED) {

                        return true;
                    }


                    if (isStockfishAnalysisMode()
                            &&
                            stockfishCandidatePanel.hasKeyboardPathNavigation()) {

                        Position fallbackRoot =
                                stockfishNavigationFallbackRoot();


                        Position target =
                                switch (event.getKeyCode()) {

                                    case KeyEvent.VK_LEFT ->
                                            stockfishCandidatePanel
                                                    .keyboardPathPrevious(
                                                            fallbackRoot
                                                    );

                                    case KeyEvent.VK_RIGHT ->
                                            stockfishCandidatePanel
                                                    .keyboardPathNext(
                                                            fallbackRoot
                                                    );

                                    case KeyEvent.VK_UP ->
                                            stockfishCandidatePanel
                                                    .keyboardPathRestore(
                                                            fallbackRoot
                                                    );

                                    case KeyEvent.VK_DOWN ->
                                            stockfishCandidatePanel
                                                    .keyboardPathReset(
                                                            fallbackRoot
                                                    );

                                    default ->
                                            null;
                                };


                        if (target != null) {

                            syncStockfishPreviewAfterKeyboardNavigation(
                                    target
                            );
                        }


                        return true;
                    }


                    boolean previewNavigation =
                            !isStockfishAnalysisMode()
                                    &&
                                    analysisMoveNavigationArmed
                                    &&
                                    analysisPanel.hasKeyboardPathNavigation();


                    if (previewNavigation) {

                        boolean changed =
                                switch (event.getKeyCode()) {

                                    case KeyEvent.VK_LEFT ->
                                            analysisPanel.keyboardPathPrevious();

                                    case KeyEvent.VK_RIGHT ->
                                            analysisPanel.keyboardPathNext();

                                    case KeyEvent.VK_UP ->
                                            analysisPanel.keyboardPathRestore();

                                    case KeyEvent.VK_DOWN ->
                                            analysisPanel.keyboardPathReset();

                                    default ->
                                            false;
                                };


                        if (changed) {

                            syncAnalysisPreviewAfterKeyboardNavigation();
                        }


                        return true;
                    }


                    /*
                     * No selected preview line is active. The same keys now
                     * navigate committed manual moves in ALL three analysis
                     * modes, including Stockfish.
                     */
                    switch (event.getKeyCode()) {

                        case KeyEvent.VK_LEFT ->
                                undoManualMove();

                        case KeyEvent.VK_RIGHT ->
                                redoManualMove();

                        case KeyEvent.VK_UP ->
                                restoreManualHistoryToLatest();

                        case KeyEvent.VK_DOWN ->
                                resetManualHistoryToStart();

                        default -> {
                        }
                    }


                    return true;
                };


        KeyboardFocusManager
                .getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(
                        analysisMoveNavigationKeyDispatcher
                );
    }


    private boolean isSetupEditorArrowKey(
            KeyEvent event
    ) {

        if (event == null
                ||
                !boardPanel.isSetupMode()
                ||
                endgameStudyMode) {

            return false;
        }


        if (event.getModifiersEx()
                != 0) {

            return false;
        }


        int keyCode =
                event.getKeyCode();


        if (keyCode != KeyEvent.VK_LEFT
                &&
                keyCode != KeyEvent.VK_RIGHT
                &&
                keyCode != KeyEvent.VK_UP
                &&
                keyCode != KeyEvent.VK_DOWN) {

            return false;
        }


        Window activeWindow =
                KeyboardFocusManager
                        .getCurrentKeyboardFocusManager()
                        .getActiveWindow();


        return activeWindow == this;
    }


    private boolean isAnalysisMoveNavigationArrowKey(
            KeyEvent event
    ) {

        if (event == null
                ||
                endgameStudyMode
                ||
                boardPanel.isSetupMode()) {

            return false;
        }


        boolean previewNavigation =
                isStockfishAnalysisMode()
                        ? stockfishCandidatePanel.hasKeyboardPathNavigation()
                        : analysisMoveNavigationArmed
                        && analysisPanel.hasKeyboardPathNavigation();


        boolean manualNavigation =
                gameHistory.size() > 1
                        || !manualRedoHistory.isEmpty();


        if (!previewNavigation
                && !manualNavigation) {

            return false;
        }


        if (event.getModifiersEx()
                != 0) {

            return false;
        }


        int keyCode =
                event.getKeyCode();


        if (keyCode != KeyEvent.VK_LEFT
                &&
                keyCode != KeyEvent.VK_RIGHT
                &&
                keyCode != KeyEvent.VK_UP
                &&
                keyCode != KeyEvent.VK_DOWN) {

            return false;
        }


        Window activeWindow =
                KeyboardFocusManager
                        .getCurrentKeyboardFocusManager()
                        .getActiveWindow();


        return activeWindow == this;
    }


    private void syncAnalysisPreviewAfterKeyboardNavigation() {

        rebuildPreviewHistoryFromPanel();


        if (previewHistory.isEmpty()) {

            restoreActualPositionView();

        } else {

            PreviewState endpoint =
                    previewHistory.get(
                            previewHistory.size() - 1
                    );

            showPreviewState(
                    endpoint
            );

            analysisPanel.restoreHeaderForCurrentPath();
        }


        retargetSearchBias();


        requestStockfishComparison(
                getStockfishComparisonPosition()
        );


        updateBackButton();
    }


    private void resetEndgameMoveReview() {
        endgameMoveReviewIndex = 0;
        endgameStudyPanel.setMoveReviewState(0, 0);
    }

    private void syncEndgameMoveReviewToLatest() {
        if (gameHistory.isEmpty()) {
            resetEndgameMoveReview();
            return;
        }

        endgameMoveReviewIndex = gameHistory.size() - 1;

        Position latest =
                gameHistory.get(endgameMoveReviewIndex);

        endgameStudyPanel.setDisplayedPosition(
                latest
        );

        endgameStudyPanel.setMoveReviewState(
                endgameMoveReviewIndex,
                gameHistory.size() - 1
        );
    }

    /**
     * Chess.com-style move-history navigation while Endgame Curriculum is
     * active:
     *
     * Left  = previous move
     * Right = next move
     * Up    = starting position
     * Down  = latest position
     *
     * These are visual review controls only. They never mutate gameHistory or
     * curriculum progress.
     */
    private void installEndgameMoveReviewKeyDispatcher() {
        if (endgameMoveReviewKeyDispatcher != null) {
            return;
        }

        endgameMoveReviewKeyDispatcher = event -> {
            if (!isEndgameMoveReviewArrowKey(event)) {
                return false;
            }

            /*
             * Consume both press and release so the focused Swing control
             * never gets a second chance to use the same arrow key.
             * This prevents combo boxes, sliders, buttons, and other controls
             * from accidentally swallowing Trainer navigation.
             */
            if (event.getID() != KeyEvent.KEY_PRESSED) {
                return true;
            }

            switch (event.getKeyCode()) {
                case KeyEvent.VK_LEFT ->
                        reviewPreviousEndgameMove();

                case KeyEvent.VK_RIGHT ->
                        reviewNextEndgameMove();

                case KeyEvent.VK_UP ->
                        reviewFirstEndgameMove();

                case KeyEvent.VK_DOWN ->
                        reviewLatestEndgameMove();

                default -> {
                    return false;
                }
            }

            return true;
        };

        KeyboardFocusManager
                .getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(
                        endgameMoveReviewKeyDispatcher
                );
    }

    private boolean isEndgameMoveReviewArrowKey(
            KeyEvent event
    ) {
        if (event == null
                || !endgameStudyMode
                || endgameStudyPanel == null
                || !endgameStudyPanel.isShowing()
                || gameHistory.isEmpty()) {
            return false;
        }

        /*
         * Only intercept unmodified arrow keys. Shortcuts such as
         * Ctrl+Arrow / Alt+Arrow remain available to the operating system
         * or any future application command that deliberately uses them.
         */
        if (event.getModifiersEx() != 0) {
            return false;
        }

        int keyCode = event.getKeyCode();

        if (keyCode != KeyEvent.VK_LEFT
                && keyCode != KeyEvent.VK_RIGHT
                && keyCode != KeyEvent.VK_UP
                && keyCode != KeyEvent.VK_DOWN) {
            return false;
        }

        /*
         * KeyboardFocusManager is JVM-global. Only consume keys while this
         * ChessWindow is the active application window, so dialogs and any
         * second window keep their own keyboard behavior.
         */
        Window activeWindow =
                KeyboardFocusManager
                        .getCurrentKeyboardFocusManager()
                        .getActiveWindow();

        return activeWindow == this;
    }

    private void reviewFirstEndgameMove() {
        if (!endgameStudyMode
                || gameHistory.isEmpty()) {
            return;
        }

        endgameMoveReviewIndex = 0;
        showEndgameMoveReviewPosition();
    }

    private void reviewLatestEndgameMove() {
        if (!endgameStudyMode
                || gameHistory.isEmpty()) {
            return;
        }

        endgameMoveReviewIndex =
                gameHistory.size() - 1;
        showEndgameMoveReviewPosition();
    }


    private void reviewPreviousEndgameMove() {
        if (!endgameStudyMode
                || gameHistory.isEmpty()) {
            return;
        }

        int lastIndex =
                gameHistory.size() - 1;

        endgameMoveReviewIndex =
                Math.max(
                        0,
                        Math.min(
                                endgameMoveReviewIndex,
                                lastIndex
                        )
                );

        if (endgameMoveReviewIndex == 0) {
            return;
        }

        endgameMoveReviewIndex--;
        showEndgameMoveReviewPosition();
    }

    private void reviewNextEndgameMove() {
        if (!endgameStudyMode
                || gameHistory.isEmpty()) {
            return;
        }

        int lastIndex =
                gameHistory.size() - 1;

        endgameMoveReviewIndex =
                Math.max(
                        0,
                        Math.min(
                                endgameMoveReviewIndex,
                                lastIndex
                        )
                );

        if (endgameMoveReviewIndex >= lastIndex) {
            return;
        }

        endgameMoveReviewIndex++;
        showEndgameMoveReviewPosition();
    }

    private void showEndgameMoveReviewPosition() {
        if (!endgameStudyMode
                || gameHistory.isEmpty()) {
            return;
        }

        int lastIndex =
                gameHistory.size() - 1;

        endgameMoveReviewIndex =
                Math.max(
                        0,
                        Math.min(
                                endgameMoveReviewIndex,
                                lastIndex
                        )
                );

        Position displayed =
                gameHistory.get(
                        endgameMoveReviewIndex
                );

        previewHistory.clear();
        boardPanel.clearPreview();
        boardPanel.setPosition(
                displayed
        );
        boardPanel.revalidate();
        boardPanel.repaint();

        endgameStudyPanel.setDisplayedPosition(
                displayed
        );

        endgameStudyPanel.setMoveReviewState(
                endgameMoveReviewIndex,
                lastIndex
        );

        boolean viewingLatest =
                endgameMoveReviewIndex == lastIndex;

        /*
         * Earlier positions are visual review only. Returning to the latest
         * position restores normal Trainer input when the study is still live.
         */
        boardPanel.setEnabled(
                viewingLatest
                        && endgameStudyReady
        );

        if (viewingLatest) {
            boardPanel.requestFocusInWindow();
        }
    }

    private void deferCurrentEndgameIfIncomplete() {
        if (currentEndgameStudyId == null) return;
        EndgameStudyProgress.PositionProgress progress =
                endgameProgress.get(currentEndgameStudyId);
        if (progress.status() != EndgameStudyProgress.Status.MASTERED) {
            if (progress.status() == EndgameStudyProgress.Status.UNSEEN) {
                endgameProgress.recordAttempt(currentEndgameStudyId);
                currentEndgameAttemptRecorded = true;
            }

            String family =
                    currentEndgameFamily;

            String pacingKey =
                    endgameReviewPacingKey(family);

            int reviewCountBefore =
                    endgameReviewCountForSelection(
                            pacingKey
                    );

            endgameProgress.enqueueReview(
                    currentEndgameStudyId
            );

            /*
             * Start one spacing window when the selected curriculum first gets
             * a backlog. In Mixed mode this is a single global Mixed window, not
             * a separate hidden timer for KQK/KRK/KPK.
             */
            if (reviewCountBefore == 0
                    && endgameReviewCountForSelection(
                    pacingKey
            ) > 0) {
                endgameFreshStudiesSinceReview.put(
                        pacingKey,
                        0
                );
            }

            saveEndgameProgress();
            refreshEndgameProgressPanel();
        }
    }

    private void markCurrentEndgameAttempt(boolean clean) {
        if (currentEndgameStudyId == null) return;
        if (!clean) currentEndgameStudyClean = false;
        if (!currentEndgameAttemptRecorded) {
            endgameProgress.recordAttempt(currentEndgameStudyId);
            currentEndgameAttemptRecorded = true;
            saveEndgameProgress();
        }
        refreshEndgameProgressPanel();
    }

    private void markCurrentEndgameCompleted() {
        if (currentEndgameStudyId == null) return;
        markCurrentEndgameAttempt(true);
        endgameProgress.recordCompletion(currentEndgameStudyId);
        saveEndgameProgress();
        refreshEndgameProgressPanel();
    }

    private void markCurrentEndgameMastered() {
        if (currentEndgameStudyId == null) return;
        markCurrentEndgameAttempt(true);
        endgameProgress.recordMastery(currentEndgameStudyId);
        saveEndgameProgress();
        refreshEndgameProgressPanel();
    }

    private void refreshEndgameProgressPanel() {
        String family = currentEndgameFamily != null ? currentEndgameFamily : selectedEndgameFamily;
        if (family == null || family.isBlank()) family = "Mixed";

        EndgameStudyProgress.Status status = currentEndgameStudyId == null
                ? EndgameStudyProgress.Status.UNSEEN
                : endgameProgress.get(currentEndgameStudyId).status();

        boolean mixed = "Mixed".equals(selectedEndgameFamily);

        int inProgress = mixed
                ? endgameProgress.attemptedCount()
                : endgameProgress.attemptedCount(selectedEndgameFamily);

        int completed = mixed
                ? endgameProgress.completedCount()
                : endgameProgress.completedCount(selectedEndgameFamily);

        int mastered = mixed
                ? endgameProgress.masteredCount()
                : endgameProgress.masteredCount(selectedEndgameFamily);

        String progressFamily = mixed
                ? "Mixed"
                : selectedEndgameFamily;

        int reviewCount =
                endgameReviewCountForSelection(
                        progressFamily
                );

        /*
         * Mixed is a real curriculum, so its denominator is the sum of every
         * canonical three- and four-piece WIN state. Four-piece totals come
         * from metadata headers only; no large tablebase arrays are loaded.
         */
        long displayCurriculumTotal =
                mixed
                        ? mixedEndgameCurriculumTotal()
                        : curriculumTotalForFamily(
                        selectedEndgameFamily
                );

        if (!mixed
                && displayCurriculumTotal <= 0) {

            displayCurriculumTotal =
                    currentEndgameCurriculumTotal;
        }

        endgameStudyPanel.setProgress(
                progressFamily,
                status,
                inProgress,
                completed,
                mastered,
                displayCurriculumTotal,
                endgameProgress.cursor(progressFamily),
                reviewCount,
                endgameProgress.studyOrder(progressFamily)
        );
    }

    // =========================================================
    // Endgame curriculum metadata
    // =========================================================

    private long mixedEndgameCurriculumTotal() {

        if (mixedEndgameCurriculumTotal >= 0) {
            return mixedEndgameCurriculumTotal;
        }

        try {
            mixedEndgameCurriculumTotal =
                    EndgameCurriculumMetadata.mixedWinTotal(
                            fourPieceStudyPositionGenerator.catalog()
                    );

            return mixedEndgameCurriculumTotal;

        } catch (IOException exception) {

            if (!mixedEndgameMetadataWarningLogged) {
                mixedEndgameMetadataWarningLogged = true;
                System.err.println(
                        "Could not calculate the complete Mixed endgame "
                                + "curriculum denominator: "
                                + exception.getMessage()
                );
            }

            /* Never display a partial grand total as though it were exact. */
            return 0L;
        }
    }


    private long curriculumTotalForFamily(
            String family
    ) {

        if (family == null
                || family.isBlank()) {
            return 0L;
        }

        if ("Mixed".equals(family)) {
            return mixedEndgameCurriculumTotal();
        }

        return switch (family) {

            case "KQK" -> EndgameCurriculumMetadata.KQK_WIN_TOTAL;
            case "KRK" -> EndgameCurriculumMetadata.KRK_WIN_TOTAL;
            case "KPK" -> EndgameCurriculumMetadata.KPK_WIN_TOTAL;

            default -> {

                Long cached =
                        endgameCurriculumTotalCache.get(
                                family
                        );

                if (cached != null) {
                    yield cached;
                }

                FourPieceMaterialClass material =
                        fourPieceMaterialForFamily(
                                family
                        );

                if (material == null) {
                    yield 0L;
                }

                try {

                    long total =
                            EndgameCurriculumMetadata.fourPieceWinTotal(
                                    material
                            );

                    endgameCurriculumTotalCache.put(
                            family,
                            total
                    );

                    yield total;

                } catch (IOException exception) {

                    yield 0L;
                }
            }
        };
    }


    private long fourPieceCurriculumTotal(
            FourPieceMaterialClass material
    ) throws IOException {

        if (material == null) {
            return 0L;
        }

        String family = material.assetStem();
        Long cached = endgameCurriculumTotalCache.get(family);

        if (cached != null) {
            return cached;
        }

        String fileName =
                fourPieceCurriculumAssetFileName(
                        material
                );

        try (InputStream raw =
                     openFourPieceCurriculumAsset(
                             fileName
                     )) {

            if (raw == null) {
                throw new IOException(
                        "Tablebase asset not found: "
                                + fileName
                );
            }

            long wins =
                    readFourPieceWinCount(
                            raw,
                            material,
                            fileName
                    );

            endgameCurriculumTotalCache.put(
                    family,
                    wins
            );

            return wins;
        }
    }


    private String fourPieceCurriculumAssetFileName(
            FourPieceMaterialClass material
    ) {

        boolean splitPawnPawn =
                material.pawnCount() == 2
                        && material.distribution()
                        == FourPieceMaterialClass.Distribution.SPLIT;

        if (splitPawnPawn) {
            return material.assetStem()
                    + "-canonical-ep.ftb.gz";
        }

        String suffix =
                material.distribution()
                        == FourPieceMaterialClass.Distribution.SAME_SIDE
                        ? "-white.ftb.gz"
                        : "-canonical.ftb.gz";

        return material.assetStem()
                + suffix;
    }


    private InputStream openFourPieceCurriculumAsset(
            String fileName
    ) throws IOException {

        Path runtimePath =
                FOUR_PIECE_TABLEBASE_DIRECTORY.resolve(
                        fileName
                );

        if (Files.isRegularFile(runtimePath)) {
            return Files.newInputStream(runtimePath);
        }

        Path developmentPath =
                DEVELOPMENT_FOUR_PIECE_TABLEBASE_DIRECTORY.resolve(
                        fileName
                );

        if (Files.isRegularFile(developmentPath)) {
            return Files.newInputStream(developmentPath);
        }

        return ChessWindow.class.getResourceAsStream(
                "/tablebases/four-piece/"
                        + fileName
        );
    }


    private long readFourPieceWinCount(
            InputStream raw,
            FourPieceMaterialClass expectedMaterial,
            String sourceName
    ) throws IOException {

        try (DataInputStream input =
                     new DataInputStream(
                             new GZIPInputStream(
                                     new BufferedInputStream(raw),
                                     32 * 1024
                             )
                     )) {

            int magic = input.readInt();

            if (magic == FOUR_PIECE_KPKP_MAGIC) {

                int version = input.readInt();

                if (version != FOUR_PIECE_KPKP_VERSION) {
                    throw new IOException(
                            "Unsupported KP-KP metadata version in "
                                    + sourceName
                    );
                }

                FourPieceMaterialClass material =
                        readFourPieceMaterialIdentity(
                                input
                        );

                if (!material.equals(expectedMaterial)) {
                    throw wrongFourPieceMaterial(
                            expectedMaterial,
                            material,
                            sourceName
                    );
                }

                input.readInt();   // state count
                input.readLong();  // legal states
                input.readLong();  // legal en-passant states
                long wins = input.readLong();

                if (wins < 0) {
                    throw new IOException(
                            "Negative WIN count in "
                                    + sourceName
                    );
                }

                return wins;
            }

            if (magic != FOUR_PIECE_GENERIC_MAGIC) {
                throw new IOException(
                        "Unknown four-piece tablebase format in "
                                + sourceName
                );
            }

            int version = input.readInt();

            if (version != FOUR_PIECE_GENERIC_VERSION) {
                throw new IOException(
                        "Unsupported four-piece metadata version in "
                                + sourceName
                );
            }

            FourPieceMaterialClass material =
                    readFourPieceMaterialIdentity(
                            input
                    );

            if (!material.equals(expectedMaterial)) {
                throw wrongFourPieceMaterial(
                        expectedMaterial,
                        material,
                        sourceName
                );
            }

            input.readBoolean(); // canonical-orientation flag
            input.readInt();     // state count
            input.readLong();    // legal states
            long wins = input.readLong();

            if (wins < 0) {
                throw new IOException(
                        "Negative WIN count in "
                                + sourceName
                );
            }

            return wins;
        }
    }


    private FourPieceMaterialClass readFourPieceMaterialIdentity(
            DataInputStream input
    ) throws IOException {

        try {

            FourPieceMaterialClass.Distribution distribution =
                    FourPieceMaterialClass.Distribution.valueOf(
                            input.readUTF()
                    );

            PieceType first =
                    PieceType.valueOf(
                            input.readUTF()
                    );

            PieceType second =
                    PieceType.valueOf(
                            input.readUTF()
                    );

            return distribution
                    == FourPieceMaterialClass.Distribution.SAME_SIDE
                    ? FourPieceMaterialClass.sameSide(
                    first,
                    second
            )
                    : FourPieceMaterialClass.split(
                    first,
                    second
            );

        } catch (IllegalArgumentException exception) {

            throw new IOException(
                    "Invalid material identity in four-piece tablebase header.",
                    exception
            );
        }
    }


    private IOException wrongFourPieceMaterial(
            FourPieceMaterialClass expected,
            FourPieceMaterialClass actual,
            String sourceName
    ) {

        return new IOException(
                "Tablebase metadata mismatch in "
                        + sourceName
                        + ": expected "
                        + expected.assetStem()
                        + ", found "
                        + actual.assetStem()
        );
    }


    private void setEndgameStudyOrder(EndgameStudyProgress.StudyOrder order) {
        String family = selectedEndgameFamily == null ? "Mixed" : selectedEndgameFamily;
        endgameProgress.setStudyOrder(family, order);
        saveEndgameProgress();
        refreshEndgameProgressPanel();
    }

    private void confirmResetCurrentEndgameFamily() {
        String family = selectedEndgameFamily;
        if (family == null || family.isBlank() || "Mixed".equals(family)) {
            JOptionPane.showMessageDialog(this, "Choose a specific endgame family before resetting family progress.", "Reset Family Progress", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int first = JOptionPane.showConfirmDialog(this,
                "This will permanently erase every " + family + " attempt, mastery, studied-position record, and ordered-study position.\n\nOther families will not be changed. Continue?",
                "Reset " + family + " Progress — Warning 1 of 2", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (first != JOptionPane.YES_OPTION) return;
        int second = JOptionPane.showConfirmDialog(this,
                "FINAL WARNING\n\nAll " + family + " curriculum progress will be permanently deleted and Ordered mode will restart at Position 1.\n\nAre you absolutely sure?",
                "Reset " + family + " Progress — Final Warning", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE);
        if (second != JOptionPane.YES_OPTION) return;
        endgameProgress.resetFamily(family);
        endgameFreshStudiesSinceReview.remove(family);
        saveEndgameProgress();
        currentEndgameStudyId = null;
        currentEndgameAttemptRecorded = false;
        currentEndgameStudyClean = true;
        refreshEndgameProgressPanel();
        endgameStudyPanel.setStatus(family + " curriculum progress reset. Ordered study restarts at Position 1.");
    }

    private java.util.Random curriculumRandom(String family) {
        EndgameStudyProgress.StudyOrder order = endgameProgress.studyOrder(family);
        if (order == EndgameStudyProgress.StudyOrder.SHUFFLE) return endgameStudyRandom;
        long index = endgameProgress.cursor(family);
        long seed = 0x9E3779B97F4A7C15L ^ ((long) family.hashCode() << 32) ^ index * 0xBF58476D1CE4E5B9L;
        return new java.util.Random(seed);
    }

    private void saveEndgameProgress() {
        try {
            endgameProgressStore.save(endgameProgress);
        } catch (java.io.IOException exception) {
            JOptionPane.showMessageDialog(
                    this,
                    "Endgame progress could not be saved:\n" + exception.getMessage(),
                    "Endgame Progress",
                    JOptionPane.WARNING_MESSAGE
            );
        }
    }

    private void confirmResetEndgameProgress() {
        int first = JOptionPane.showConfirmDialog(
                this,
                "This will permanently erase every Endgame Study attempt, mastery, and studied-position record.\n\nContinue?",
                "Reset Endgame Progress — Warning 1 of 2",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (first != JOptionPane.YES_OPTION) return;

        int second = JOptionPane.showConfirmDialog(
                this,
                "FINAL WARNING\n\nAll Endgame Study progress will be permanently deleted. This cannot be undone.\n\nAre you absolutely sure?",
                "Reset Endgame Progress — Final Warning",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE
        );
        if (second != JOptionPane.YES_OPTION) return;

        try {
            endgameProgressStore.clear();
            endgameProgress.clear();
            endgameFreshStudiesSinceReview.clear();
            currentEndgameStudyId = null;
            currentEndgameAttemptRecorded = false;
            currentEndgameStudyClean = true;
            refreshEndgameProgressPanel();
            endgameStudyPanel.setStatus("Endgame Study progress reset.");
        } catch (java.io.IOException exception) {
            JOptionPane.showMessageDialog(this,
                    "Endgame progress could not be reset:\n" + exception.getMessage(),
                    "Endgame Progress", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void revealEndgameAnalysis() {

        hideBoardLoading();
        setAnalysisEngineSelectorEnabled(true);

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


        showActiveAnalysisEngineCard();


        headerSubtitleLabel.setText(
                "Endgame analysis"
        );


        analysisArea.revalidate();
        analysisArea.repaint();
    }


    private void beginPositionSetup() {

        /*
         * Setup is a clean position-construction workspace. It always starts
         * from the standard chess position, regardless of the position that was
         * being viewed beforehand. This includes:
         *
         * - manual moves,
         * - Dovetail/Hybrid selected continuations,
         * - Stockfish continuations, and
         * - Endgame Curriculum positions.
         *
         * The real board underneath Setup is still preserved by ChessBoardPanel,
         * so Cancel can return to the user's pre-Setup analysis position.
         */
        hideBoardLoading();

        endgameProofGeneration++;


        endgameStudyMode =
                false;


        endgameStudyReady =
                false;


        activeEndgameTablebase =
                null;


        boardPanel.setEnabled(
                true
        );


        piecePalettePanel.cancelActiveDrag();
        setAnalysisEngineSelectorEnabled(false);


        if (boardPanel.isSetupMode()) {
            return;
        }


        stopGuiExplorationChain();


        /*
         * Setup mode temporarily suspends whichever analysis engine is active.
         * In Stockfish mode invalidate any in-flight MultiPV callback so it
         * cannot repaint the hidden analysis panel while pieces are being moved.
         */
        if (isStockfishAnalysisMode()) {
            stockfishModeRequestId++;
        }


        analysisRequestId++;


        boardPanel.beginSetupMode(
                createStandardStartingPosition()
        );


        piecePalettePanel.setVisible(
                true
        );

        if (setupPaletteHost != null) {

            setupPaletteHost.setVisible(
                    true
            );
        }

        updateBoardAreaInsetsForCurrentMode();
        updateApplicationHeaderBorderForCurrentMode();


        evaluationBar.setVisible(
                false
        );


        endgameStudyMode =
                false;


        endgameStudyPanel.setVisible(
                false
        );


        analysisEngineCards.setVisible(false);


        setupPanel.setVisible(
                true
        );

        showAuxiliaryAnalysisCard(
                "SETUP"
        );


        refreshSetupPanel();


        headerSubtitleLabel.setText(
                "Position setup — drag pieces freely, then analyze"
        );


        refreshWorkspaceLayout();


        resizeForCurrentMode();
    }


    private void cancelPositionSetup() {

        piecePalettePanel.cancelActiveDrag();


        if (!boardPanel.isSetupMode()) {
            return;
        }


        boardPanel.cancelSetupMode();
        setAnalysisEngineSelectorEnabled(true);

        updateBoardAreaInsetsForCurrentMode();
        updateApplicationHeaderBorderForCurrentMode();


        piecePalettePanel.setVisible(
                false
        );

        if (setupPaletteHost != null) {

            setupPaletteHost.setVisible(
                    false
            );
        }


        evaluationBar.setVisible(
                true
        );


        setupPanel.setVisible(
                false
        );


        showActiveAnalysisEngineCard();


        headerSubtitleLabel.setText(
                activeAnalysisEngineSubtitle()
        );


        refreshWorkspaceLayout();


        resizeForCurrentMode();


        /*
         * Cancel returns to the pre-setup board. Resume the engine that the
         * user actually selected instead of always restarting Dovetail.
         */
        if (isStockfishAnalysisMode()) {

            Position position =
                    boardPanel.getPosition();

            stockfishCandidatePanel.clearPath(
                    position
            );

            requestStockfishModeCandidates(
                    position
            );

        } else {

            analyzeCurrentPosition();
        }
    }


    private void commitPositionSetup() {

        piecePalettePanel.cancelActiveDrag();


        try {

            Position setupPosition =
                    boardPanel.finishSetupMode();

            setAnalysisEngineSelectorEnabled(true);
            updateBoardAreaInsetsForCurrentMode();
            updateApplicationHeaderBorderForCurrentMode();


            piecePalettePanel.setVisible(
                    false
            );

            if (setupPaletteHost != null) {

                setupPaletteHost.setVisible(
                        false
                );
            }


            evaluationBar.setVisible(
                    true
            );


            setupPanel.setVisible(
                    false
            );


            showActiveAnalysisEngineCard();


            headerSubtitleLabel.setText(
                    activeAnalysisEngineSubtitle()
            );


            refreshWorkspaceLayout();


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


    /**
     * Preserve the original board/palette design while reclaiming just enough
     * vertical room in Setup for the full 640px board + original bottom
     * PiecePalettePanel to fit above the taskbar.
     *
     * This does NOT change PiecePalettePanel itself.
     */
    private void updateBoardAreaInsetsForCurrentMode() {

        if (boardArea == null) {
            return;
        }


        boolean compactForSetup =
                boardPanel != null
                        && boardPanel.isSetupMode();


        boardArea.setBorder(
                BorderFactory.createEmptyBorder(
                        compactForSetup
                                ? 0
                                : 20,
                        20,
                        compactForSetup
                                ? 0
                                : 20,
                        12
                )
        );
    }


    private void refreshWorkspaceLayout() {

        if (workspace != null) {

            workspace.revalidate();
            workspace.repaint();
        }


        if (boardArea != null) {

            boardArea.revalidate();
            boardArea.repaint();
        }
    }


    /**
     * Setup keeps the original two-row bottom palette. On shorter logical
     * desktops, reclaim a few vertical pixels from the application header
     * rather than clipping the bottom rank or redesigning the palette.
     */
    private void updateApplicationHeaderBorderForCurrentMode() {

        if (applicationHeader == null) {
            return;
        }


        Color border =
                darkTheme
                        ? new Color(
                        42,
                        53,
                        64
                )
                        : new Color(
                        210,
                        216,
                        224
                );


        boolean compactForSetup =
                boardPanel != null
                        && boardPanel.isSetupMode();


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
                                compactForSetup
                                        ? 2
                                        : 10,
                                20,
                                compactForSetup
                                        ? 2
                                        : 10,
                                18
                        )
                )
        );


        /*
         * Setup deliberately removes 8 px from the header's top inset
         * (10 -> 2) to preserve vertical room for the original two-row
         * piece palette. Keep that compact header, but restore the action
         * controls to their normal screen position by giving only the
         * right-side action row those 8 px back internally.
         *
         * Normal / Endgame mode remains completely unchanged.
         */
        if (headerActionsWrapper != null) {

            headerActionsWrapper.setBorder(
                    BorderFactory.createEmptyBorder(
                            compactForSetup
                                    ? 8
                                    : 0,
                            0,
                            0,
                            0
                    )
            );
        }
    }


    private void resizeForCurrentMode() {

        /*
         * Do not knock a maximized window out of maximized state when Setup or
         * Endgame is opened. The old setSize(...) call was the source of the
         * bottom clipping / stray glyph fragments near the taskbar.
         */
        if ((getExtendedState()
                & JFrame.MAXIMIZED_BOTH)
                == JFrame.MAXIMIZED_BOTH) {

            revalidate();
            repaint();
            return;
        }


        GraphicsConfiguration configuration =
                getGraphicsConfiguration();


        if (configuration == null) {

            revalidate();
            repaint();
            return;
        }


        Rectangle screenBounds =
                configuration.getBounds();


        Insets screenInsets =
                Toolkit
                        .getDefaultToolkit()
                        .getScreenInsets(
                                configuration
                        );


        int usableX =
                screenBounds.x
                        + screenInsets.left;

        int usableY =
                screenBounds.y
                        + screenInsets.top;

        int usableWidth =
                Math.max(
                        1,
                        screenBounds.width
                                - screenInsets.left
                                - screenInsets.right
                );

        int usableHeight =
                Math.max(
                        1,
                        screenBounds.height
                                - screenInsets.top
                                - screenInsets.bottom
                );


        Dimension preferred =
                getPreferredSize();


        int targetWidth =
                Math.min(
                        usableWidth,
                        Math.max(
                                1280,
                                preferred.width
                        )
                );


        int targetHeight =
                Math.min(
                        usableHeight,
                        Math.max(
                                800,
                                preferred.height
                        )
                );


        setSize(
                targetWidth,
                targetHeight
        );


        int maximumX =
                usableX
                        + usableWidth
                        - targetWidth;

        int maximumY =
                usableY
                        + usableHeight
                        - targetHeight;


        setLocation(
                Math.max(
                        usableX,
                        Math.min(
                                getX(),
                                maximumX
                        )
                ),
                Math.max(
                        usableY,
                        Math.min(
                                getY(),
                                maximumY
                        )
                )
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


    private void returnToEngineHome() {

        /*
         * Engine Home is an unconditional navigation action rather than a
         * confirmation-based Reset. It exits Setup/Endgame and returns the
         * application to the normal engine workspace on the standard position.
         * The user's selected analysis engine is preserved.
         */
        piecePalettePanel.cancelActiveDrag();


        if (boardPanel.isSetupMode()) {
            boardPanel.cancelSetupMode();
        }


        piecePalettePanel.setVisible(
                false
        );


        if (setupPaletteHost != null) {
            setupPaletteHost.setVisible(
                    false
            );
        }


        setupPanel.setVisible(
                false
        );


        setAnalysisEngineSelectorEnabled(true);


        loadFenPosition(
                createStandardStartingPosition(),
                true
        );
    }


    private void resetToStartingPosition() {

        int result =
                JOptionPane.showConfirmDialog(
                        this,
                        "Reset the board to the standard starting position?\nExisting search discoveries will be preserved when available.",
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
                startingPosition,
                true
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

        loadFenPosition(
                position,
                false
        );
    }


    private void loadFenPosition(
            Position position,
            boolean preserveKnownAnalysis
    ) {

        if (position == null) {

            return;
        }


        hideBoardLoading();
        piecePalettePanel.cancelActiveDrag();
        setAnalysisEngineSelectorEnabled(true);

        endgameProofGeneration++;


        endgameStudyMode =
                false;


        endgameStudyReady =
                false;


        activeEndgameTablebase =
                null;


        endgameStudyPanel.setVisible(
                false
        );


        /*
         * Leaving Endgame Study must restore the normal analysis
         * workspace. Endgame mode hides these components explicitly,
         * so merely hiding the study panel is not enough.
         */
        evaluationBar.setVisible(
                true
        );


        showActiveAnalysisEngineCard();


        setupPanel.setVisible(
                false
        );


        piecePalettePanel.setVisible(
                false
        );


        headerSubtitleLabel.setText(
                activeAnalysisEngineSubtitle()
        );


        boardPanel.setEnabled(
                true
        );


        analysisArea.revalidate();
        analysisArea.repaint();
        boardArea.revalidate();
        boardArea.repaint();
        resizeForCurrentMode();


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
        manualRedoHistory.clear();


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


        /*
         * A newly loaded/setup position belongs to the currently selected
         * analysis engine.  Do not silently route Setup/FEN through Dovetail
         * while Stockfish mode is active.
         */
        if (isStockfishAnalysisMode()) {

            stockfishCandidatePanel.clearPath(
                    position
            );

            requestStockfishModeCandidates(
                    position
            );

        } else {

            analyzeCurrentPosition(
                    preserveKnownAnalysis
            );
        }
    }



    // =========================================================
    // Analysis engine card visibility
    // =========================================================

    /**
     * CardLayout is the single authority for which analysis panel is visible.
     * Keeping child visibility out of the rest of ChessWindow prevents
     * Dovetail and Stockfish controls from being painted at the same time.
     */
    private void showAuxiliaryAnalysisCard(
            String cardName
    ) {
        if (auxiliaryAnalysisCards == null
                || analysisModeCards == null
                || cardName == null) {
            return;
        }

        CardLayout auxiliaryLayout =
                (CardLayout) auxiliaryAnalysisCards.getLayout();

        auxiliaryLayout.show(
                auxiliaryAnalysisCards,
                cardName
        );

        CardLayout modeLayout =
                (CardLayout) analysisModeCards.getLayout();

        modeLayout.show(
                analysisModeCards,
                "AUXILIARY"
        );

        auxiliaryAnalysisCards.setVisible(true);
        auxiliaryAnalysisCards.revalidate();
        auxiliaryAnalysisCards.repaint();
    }


    private void showActiveAnalysisEngineCard() {

        if (analysisEngineCards == null
                || analysisModeCards == null) {
            return;
        }

        CardLayout layout =
                (CardLayout) analysisEngineCards.getLayout();

        layout.show(
                analysisEngineCards,
                isStockfishAnalysisMode()
                        ? "STOCKFISH"
                        : "DOVETAIL"
        );

        analysisEngineCards.setVisible(
                true
        );

        CardLayout modeLayout =
                (CardLayout) analysisModeCards.getLayout();

        modeLayout.show(
                analysisModeCards,
                "ENGINE"
        );

        analysisEngineCards.revalidate();
        analysisEngineCards.repaint();
    }


    // =========================================================
    // Analysis engine toggle / Stockfish candidate mode
    // =========================================================

    private void setAnalysisEngineMode(
            AnalysisEngineMode mode
    ) {

        if (mode == null) {
            return;
        }


        if (mode == AnalysisEngineMode.STOCKFISH
                && stockfishClient == null) {

            JOptionPane.showMessageDialog(
                    this,
                    "Stockfish is not configured.",
                    "Stockfish",
                    JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }


        if (analysisEngineMode == mode) {
            return;
        }


        /*
         * Engine switching is a hard session boundary. The algorithms remain
         * separate rather than sharing scheduler state:
         *
         * Dovetail  = diagonal persistent walkers only
         * Hybrid    = the same walkers + fair node/edge coverage
         * Stockfish = external conventional engine
         */
        stopGuiExplorationChain();
        analysisRequestId++;
        stockfishModeRequestId++;


        if (boardPanel.isSetupMode()) {

            boardPanel.cancelSetupMode();
            piecePalettePanel.setVisible(false);
            setupPanel.setVisible(false);
            evaluationBar.setVisible(true);
        }


        analysisEngineMode =
                mode;


        if (mode == AnalysisEngineMode.DOVETAIL) {

            engine.setSearchMode(
                    ChessEngine.SearchMode.DOVETAIL
            );

        } else if (mode == AnalysisEngineMode.HYBRID) {

            engine.setSearchMode(
                    ChessEngine.SearchMode.HYBRID
            );
        }


        showActiveAnalysisEngineCard();

        headerSubtitleLabel.setText(
                activeAnalysisEngineSubtitle()
        );

        styleEngineSelectorButtons();

        analysisArea.revalidate();
        analysisArea.repaint();


        /*
         * Each mode begins from a clean standard-board session. This makes
         * Dovetail-vs-Hybrid comparisons meaningful and prevents discoveries
         * made by one algorithm from leaking into the other mode.
         */
        Position startingPosition =
                createStandardStartingPosition();


        loadFenPosition(
                startingPosition,
                false
        );
    }


    private boolean isStockfishAnalysisMode() {

        return analysisEngineMode
                == AnalysisEngineMode.STOCKFISH;
    }


    private boolean isHybridAnalysisMode() {

        return analysisEngineMode
                == AnalysisEngineMode.HYBRID;
    }


    private String activeAnalysisEngineLabel() {

        return switch (analysisEngineMode) {

            case DOVETAIL -> "Dovetail";
            case HYBRID -> "Hybrid";
            case STOCKFISH -> "Stockfish";
        };
    }


    private String activeAnalysisEngineSubtitle() {

        return switch (analysisEngineMode) {

            case DOVETAIL ->
                    "Diagonal line-walker exploration • A1, B1, A2, C1, B2, A3...";

            case HYBRID ->
                    "Dovetail line walkers + systematic node coverage";

            case STOCKFISH ->
                    "Progressive Stockfish • 20 broad → 8 refined → 3 deep → 1 authoritative";
        };
    }


    private void requestStockfishModeCandidates(
            Position position
    ) {

        if (!isStockfishAnalysisMode()
                || stockfishClient == null
                || position == null) {

            return;
        }


        String fen =
                FenCodec.toFen(
                        position
                );


        /*
         * Invalidate the old progressive pipeline immediately. SwingWorker
         * cancellation interrupts StockfishClient's blocking poll and its
         * finally block restores MultiPV=1 before the next request proceeds.
         */
        long requestId =
                ++stockfishModeRequestId;


        if (stockfishModeWorker != null
                && !stockfishModeWorker.isDone()) {

            stockfishModeWorker.cancel(
                    true
            );
        }


        StockfishModeSnapshot cached =
                stockfishModeCache.get(
                        fen
                );


        if (cached != null) {

            stockfishCandidatePanel.setProgressiveCandidates(
                    position,
                    cached.parentEvaluation(),
                    STOCKFISH_BEST_MOVE_DEPTH,
                    "complete • best depth "
                            + STOCKFISH_BEST_MOVE_DEPTH,
                    false,
                    cached.candidates()
            );


            evaluationBar.setAnalysis(
                    cached.parentEvaluation(),
                    SearchOutcome.UNKNOWN,
                    -1
            );

            return;
        }


        stockfishCandidatePanel.setAnalyzing(
                position,
                STOCKFISH_MODE_DEPTH
        );


        SwingWorker<
                StockfishModeSnapshot,
                StockfishProgressStage
                > worker =
                new SwingWorker<>() {

                    @Override
                    protected StockfishModeSnapshot doInBackground()
                            throws Exception {

                        int legalMoveCount =
                                new MoveGenerator()
                                        .generateLegalMoves(
                                                position
                                        )
                                        .size();


                        int broadCount =
                                Math.max(
                                        1,
                                        Math.min(
                                                legalMoveCount,
                                                STOCKFISH_MODE_MAX_MULTIPV
                                        )
                                );


                        java.util.List<StockfishClient.Analysis> merged =
                                java.util.List.of();


                        // -----------------------------------------
                        // Pass 1 — whole move space, shallow/fast.
                        // -----------------------------------------

                        if (!isCancelled()) {

                            java.util.List<StockfishClient.Analysis> broad =
                                    tryStockfishMultiPvStage(
                                            fen,
                                            STOCKFISH_MODE_DEPTH,
                                            broadCount,
                                            STOCKFISH_MODE_MULTIPV_TIMEOUT,
                                            "breadth"
                                    );


                            merged =
                                    mergeStockfishAnalyses(
                                            merged,
                                            broad,
                                            null
                                    );


                            publishStockfishStage(
                                    position,
                                    merged,
                                    STOCKFISH_MODE_DEPTH,
                                    "breadth pass • depth "
                                            + STOCKFISH_MODE_DEPTH,
                                    true
                            );
                        }


                        // -----------------------------------------
                        // Pass 2 — strongest eight.
                        // -----------------------------------------

                        if (!isCancelled()) {

                            int refineCount =
                                    Math.max(
                                            1,
                                            Math.min(
                                                    legalMoveCount,
                                                    STOCKFISH_REFINEMENT_MULTIPV
                                            )
                                    );


                            java.util.List<StockfishClient.Analysis> refined =
                                    tryStockfishMultiPvStage(
                                            fen,
                                            STOCKFISH_REFINEMENT_DEPTH,
                                            refineCount,
                                            STOCKFISH_REFINEMENT_TIMEOUT,
                                            "top-eight refinement"
                                    );


                            merged =
                                    mergeStockfishAnalyses(
                                            merged,
                                            refined,
                                            null
                                    );


                            publishStockfishStage(
                                    position,
                                    merged,
                                    STOCKFISH_REFINEMENT_DEPTH,
                                    "top "
                                            + refineCount
                                            + " refined • depth "
                                            + STOCKFISH_REFINEMENT_DEPTH,
                                    true
                            );
                        }


                        // -----------------------------------------
                        // Pass 3 — strongest three.
                        // -----------------------------------------

                        if (!isCancelled()) {

                            int deepCount =
                                    Math.max(
                                            1,
                                            Math.min(
                                                    legalMoveCount,
                                                    STOCKFISH_DEEP_MULTIPV
                                            )
                                    );


                            java.util.List<StockfishClient.Analysis> deep =
                                    tryStockfishMultiPvStage(
                                            fen,
                                            STOCKFISH_DEEP_DEPTH,
                                            deepCount,
                                            STOCKFISH_DEEP_TIMEOUT,
                                            "top-three deepening"
                                    );


                            merged =
                                    mergeStockfishAnalyses(
                                            merged,
                                            deep,
                                            null
                                    );


                            publishStockfishStage(
                                    position,
                                    merged,
                                    STOCKFISH_DEEP_DEPTH,
                                    "top "
                                            + deepCount
                                            + " deep • depth "
                                            + STOCKFISH_DEEP_DEPTH,
                                    true
                            );
                        }


                        // -----------------------------------------
                        // Pass 4 — authoritative #1.
                        // -----------------------------------------

                        StockfishClient.Analysis authoritativeBest =
                                null;


                        if (!isCancelled()) {

                            try {

                                authoritativeBest =
                                        stockfishClient.analyzeFen(
                                                fen,
                                                STOCKFISH_BEST_MOVE_DEPTH,
                                                STOCKFISH_BEST_MOVE_TIMEOUT
                                        );

                            } catch (Exception failure) {

                                System.err.println(
                                        "Stockfish authoritative best-move search failed: "
                                                + failure.getMessage()
                                );
                            }
                        }


                        merged =
                                mergeStockfishAnalyses(
                                        merged,
                                        java.util.List.of(),
                                        authoritativeBest
                                );


                        if (merged.isEmpty()) {

                            if (isCancelled()) {

                                throw new java.util.concurrent.CancellationException(
                                        "Stockfish progressive search cancelled."
                                );
                            }


                            throw new IOException(
                                    "Stockfish produced no candidate moves."
                            );
                        }


                        Integer parentEvaluation =
                                authoritativeBest == null
                                        ? whitePerspectiveScore(
                                        position,
                                        merged.get(0)
                                )
                                        : whitePerspectiveScore(
                                        position,
                                        authoritativeBest
                                );


                        if (parentEvaluation == null) {

                            parentEvaluation =
                                    whitePerspectiveScore(
                                            position,
                                            merged.get(0)
                                    );
                        }


                        if (parentEvaluation == null) {

                            parentEvaluation =
                                    0;
                        }


                        java.util.List<StockfishCandidatePanel.Candidate> candidates =
                                candidatesFromStockfishAnalyses(
                                        position,
                                        merged
                                );


                        return new StockfishModeSnapshot(
                                parentEvaluation,
                                candidates
                        );
                    }


                    @Override
                    protected void process(
                            java.util.List<StockfishProgressStage> stages
                    ) {

                        if (stages == null
                                || stages.isEmpty()
                                || requestId != stockfishModeRequestId
                                || !isStockfishAnalysisMode()
                                || isCancelled()) {

                            return;
                        }


                        StockfishProgressStage stage =
                                stages.get(
                                        stages.size() - 1
                                );


                        java.util.List<StockfishCandidatePanel.Candidate> candidates =
                                candidatesFromStockfishAnalyses(
                                        position,
                                        stage.analyses()
                                );


                        if (candidates.isEmpty()) {
                            return;
                        }


                        Integer parentEvaluation =
                                whitePerspectiveScore(
                                        position,
                                        stage.analyses().get(0)
                                );


                        if (parentEvaluation == null) {

                            parentEvaluation =
                                    0;
                        }


                        stockfishCandidatePanel.setProgressiveCandidates(
                                position,
                                parentEvaluation,
                                stage.displayDepth(),
                                stage.phaseLabel(),
                                stage.refining(),
                                candidates
                        );


                        evaluationBar.setAnalysis(
                                parentEvaluation,
                                SearchOutcome.UNKNOWN,
                                -1
                        );
                    }


                    @Override
                    protected void done() {

                        if (requestId != stockfishModeRequestId
                                || !isStockfishAnalysisMode()
                                || isCancelled()) {

                            return;
                        }


                        try {

                            StockfishModeSnapshot snapshot =
                                    get();


                            stockfishModeCache.put(
                                    fen,
                                    snapshot
                            );


                            stockfishCandidatePanel.setProgressiveCandidates(
                                    position,
                                    snapshot.parentEvaluation(),
                                    STOCKFISH_BEST_MOVE_DEPTH,
                                    "complete • best depth "
                                            + STOCKFISH_BEST_MOVE_DEPTH,
                                    false,
                                    snapshot.candidates()
                            );


                            evaluationBar.setAnalysis(
                                    snapshot.parentEvaluation(),
                                    SearchOutcome.UNKNOWN,
                                    -1
                            );


                        } catch (java.util.concurrent.CancellationException ignored) {

                            // A newer position owns Stockfish now.

                        } catch (Exception ex) {

                            Throwable cause =
                                    ex.getCause() == null
                                            ? ex
                                            : ex.getCause();


                            stockfishCandidatePanel.setUnavailable(
                                    cause.getMessage() == null
                                            ? "Stockfish analysis failed."
                                            : cause.getMessage()
                            );
                        }
                    }


                    private void publishStockfishStage(
                            Position parent,
                            java.util.List<StockfishClient.Analysis> analyses,
                            int displayDepth,
                            String phaseLabel,
                            boolean refining
                    ) {

                        if (isCancelled()
                                || analyses == null
                                || analyses.isEmpty()) {

                            return;
                        }


                        publish(
                                new StockfishProgressStage(
                                        java.util.List.copyOf(
                                                analyses
                                        ),
                                        displayDepth,
                                        phaseLabel,
                                        refining
                                )
                        );
                    }
                };


        stockfishModeWorker =
                worker;

        worker.execute();
    }


    private java.util.List<StockfishClient.Analysis> tryStockfishMultiPvStage(
            String fen,
            int depth,
            int multiPv,
            Duration timeout,
            String stageName
    ) {

        try {

            return stockfishClient.analyzeFenMultiPv(
                    fen,
                    depth,
                    multiPv,
                    timeout
            );

        } catch (Exception failure) {

            System.err.println(
                    "Stockfish "
                            + stageName
                            + " search failed: "
                            + failure.getMessage()
            );


            return java.util.List.of();
        }
    }


    /**
     * Merge a deeper pass into the existing candidate set.
     *
     * The newly refined lines lead the ordering. Any moves not included in
     * that smaller/deeper pass remain behind them at their previous shallower
     * depth. The final authoritative single-PV move is forcibly placed first.
     */
    private java.util.List<StockfishClient.Analysis> mergeStockfishAnalyses(
            java.util.List<StockfishClient.Analysis> previous,
            java.util.List<StockfishClient.Analysis> refined,
            StockfishClient.Analysis authoritativeBest
    ) {

        java.util.LinkedHashMap<String, StockfishClient.Analysis> merged =
                new java.util.LinkedHashMap<>();


        if (authoritativeBest != null
                && authoritativeBest.bestMove() != null) {

            merged.put(
                    authoritativeBest.bestMove(),
                    authoritativeBest
            );
        }


        if (refined != null) {

            for (StockfishClient.Analysis analysis :
                    refined) {

                if (analysis == null
                        || analysis.bestMove() == null) {

                    continue;
                }


                merged.putIfAbsent(
                        analysis.bestMove(),
                        analysis
                );
            }
        }


        if (previous != null) {

            for (StockfishClient.Analysis analysis :
                    previous) {

                if (analysis == null
                        || analysis.bestMove() == null) {

                    continue;
                }


                merged.putIfAbsent(
                        analysis.bestMove(),
                        analysis
                );
            }
        }


        java.util.List<StockfishClient.Analysis> result =
                new java.util.ArrayList<>(
                        merged.values()
                );


        if (result.size()
                > STOCKFISH_MODE_MAX_MULTIPV) {

            result =
                    new java.util.ArrayList<>(
                            result.subList(
                                    0,
                                    STOCKFISH_MODE_MAX_MULTIPV
                            )
                    );
        }


        return java.util.List.copyOf(
                result
        );
    }


    private java.util.List<StockfishCandidatePanel.Candidate> candidatesFromStockfishAnalyses(
            Position position,
            java.util.List<StockfishClient.Analysis> analyses
    ) {

        if (position == null
                || analyses == null
                || analyses.isEmpty()) {

            return java.util.List.of();
        }


        java.util.List<StockfishCandidatePanel.Candidate> candidates =
                new java.util.ArrayList<>();


        for (StockfishClient.Analysis analysis :
                analyses) {

            StockfishCandidatePanel.Candidate candidate =
                    candidateFromStockfishAnalysis(
                            position,
                            analysis
                    );


            if (candidate != null) {

                candidates.add(
                        candidate
                );
            }
        }


        return java.util.List.copyOf(
                candidates
        );
    }


    private Integer whitePerspectiveScore(
            Position position,
            StockfishClient.Analysis analysis
    ) {
        if (position == null || analysis == null) {
            return null;
        }

        Integer whiteCp =
                StockfishScorePerspective.toWhiteCentipawns(
                        position.getSideToMove(),
                        analysis.centipawns()
                );

        if (whiteCp != null) {
            return whiteCp;
        }

        Integer mate =
                StockfishScorePerspective.toWhiteMateScore(
                        position.getSideToMove(),
                        analysis.mateIn()
                );

        if (mate == null) {
            return null;
        }

        return mate > 0
                ? 100000 - Math.abs(mate)
                : -100000 + Math.abs(mate);
    }


    private StockfishCandidatePanel.Candidate candidateFromStockfishAnalysis(
            Position position,
            StockfishClient.Analysis analysis
    ) {
        if (position == null
                || analysis == null
                || analysis.bestMove() == null) {
            return null;
        }

        Integer whiteCp =
                whitePerspectiveScore(position, analysis);

        if (whiteCp == null) {
            return null;
        }

        Position child =
                StockfishMoveAdapter.resultingPosition(
                        position,
                        analysis.bestMove()
                );

        if (child == null) {
            return null;
        }

        return new StockfishCandidatePanel.Candidate(
                child,
                StockfishMoveAdapter.san(
                        position,
                        analysis.bestMove()
                ),
                whiteCp,
                analysis.depth(),
                StockfishPvFormatter.format(
                        position,
                        analysis.principalVariation()
                )
        );
    }


    private record StockfishProgressStage(
            java.util.List<StockfishClient.Analysis> analyses,
            int displayDepth,
            String phaseLabel,
            boolean refining
    ) {

        private StockfishProgressStage {

            analyses =
                    analyses == null
                            ? java.util.List.of()
                            : java.util.List.copyOf(
                            analyses
                    );
        }
    }


    /**
     * Full-width auxiliary-mode surface.
     *
     * Setup and Endgame should begin immediately beside the board and resize
     * with the available analysis region. The old centered width cap created
     * the large empty gap the user was seeing between board and panel.
     */
    private static final class CenteredModeHost extends JPanel {

        private final JComponent content;
        private final int maxContentWidth;

        private CenteredModeHost(
                JComponent content,
                int maxContentWidth
        ) {

            if (content == null) {
                throw new IllegalArgumentException(
                        "Centered mode content cannot be null."
                );
            }

            this.content = content;
            this.maxContentWidth =
                    Math.max(
                            1,
                            maxContentWidth
                    );

            setLayout(null);
            setOpaque(true);
            add(content);
        }


        @Override
        public void doLayout() {

            Insets insets = getInsets();

            int availableWidth =
                    Math.max(
                            0,
                            getWidth()
                                    - insets.left
                                    - insets.right
                    );

            int availableHeight =
                    Math.max(
                            0,
                            getHeight()
                                    - insets.top
                                    - insets.bottom
                    );

            /*
             * Fill the complete right-side analysis region. Content starts at
             * the left edge next to the board instead of being centered inside
             * a width cap.
             */
            content.setBounds(
                    insets.left,
                    insets.top,
                    availableWidth,
                    availableHeight
            );
        }


        @Override
        public Dimension getPreferredSize() {

            Dimension preferred = content.getPreferredSize();

            return new Dimension(
                    Math.max(
                            1,
                            preferred.width
                    ),
                    Math.max(
                            1,
                            preferred.height
                    )
            );
        }
    }


    private record StockfishModeSnapshot(
            int parentEvaluation,
            List<StockfishCandidatePanel.Candidate> candidates
    ) {
        private StockfishModeSnapshot {
            candidates =
                    candidates == null
                            ? List.of()
                            : List.copyOf(candidates);
        }
    }


    // =========================================================
    // Stockfish comparison
    // =========================================================

    private Position getStockfishComparisonPosition() {

        Position selected =
                analysisPanel.getSelectedPosition();


        if (selected != null) {

            return selected;
        }


        return boardPanel.getPosition();
    }


    private void requestStockfishComparison(
            Position position
    ) {

        long requestId =
                ++stockfishComparisonRequestId;


        if (stockfishClient == null) {

            analysisPanel.setStockfishUnavailable(
                    "Stockfish is not configured."
            );

            return;
        }


        if (position == null) {

            analysisPanel.setStockfishIdle(
                    "No position available for comparison."
            );

            return;
        }


        String fen =
                FenCodec.toFen(
                        position
                );


        analysisPanel.setStockfishAnalyzing(
                STOCKFISH_COMPARISON_DEPTH
        );


        SwingWorker<
                StockfishClient.Analysis,
                Void
                > worker =

                new SwingWorker<>() {


                    @Override
                    protected StockfishClient.Analysis
                    doInBackground() throws Exception {

                        return stockfishClient.analyzeFen(
                                fen,
                                STOCKFISH_COMPARISON_DEPTH,
                                STOCKFISH_COMPARISON_TIMEOUT
                        );
                    }


                    @Override
                    protected void done() {

                        if (requestId
                                != stockfishComparisonRequestId) {

                            return;
                        }


                        try {

                            StockfishClient.Analysis result =
                                    get();


                            String whitePerspectiveScore =
                                    StockfishScorePerspective
                                            .formatWhitePerspective(
                                                    position.getSideToMove(),
                                                    result
                                            );


                            analysisPanel.setStockfishAnalysis(
                                    result.engineName(),
                                    result.depth(),
                                    whitePerspectiveScore,
                                    result.bestMove(),
                                    result.nodes(),
                                    result.nps(),
                                    StockfishPvFormatter.format(
                                            position,
                                            result.principalVariation()
                                    )
                            );


                        } catch (Exception exception) {

                            Throwable cause =
                                    exception.getCause() == null
                                            ? exception
                                            : exception.getCause();


                            String message =
                                    cause.getMessage();


                            analysisPanel.setStockfishUnavailable(
                                    message == null
                                            || message.isBlank()
                                            ? "Stockfish comparison failed."
                                            : message
                            );
                        }
                    }
                };


        worker.execute();
    }


    // =========================================================
    // Initial analysis
    // =========================================================

    private void analyzeCurrentPosition() {

        analyzeCurrentPosition(
                false
        );
    }


    private void analyzeCurrentPosition(
            boolean preserveKnownAnalysis
    ) {

        userExplorationPaused =
                false;

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


        requestStockfishComparison(
                position
        );


        SwingWorker<
                PositionAnalysis,
                Void
                > worker =

                new SwingWorker<>() {


                    @Override
                    protected PositionAnalysis
                    doInBackground() {

                        if (preserveKnownAnalysis) {

                            return engine
                                    .analyzePreservingGraphIfKnown(
                                            position
                                    );
                        }


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
                                        analysisPanel.getSelectedEvaluation(),
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
                                        result.getEvaluation(),
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

                                    "Dovetail Engine",

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

    private static class ExactStudyLoad {
        private final ExactEndgameTablebase tablebase;
        private final Position position;
        private final long curriculumTotal;
        private final long legalTotal;
        private final boolean reviewPosition;

        private ExactStudyLoad(
                ExactEndgameTablebase tablebase,
                Position position,
                long curriculumTotal,
                long legalTotal,
                boolean reviewPosition
        ) {
            this.tablebase = tablebase;
            this.position = position;
            this.curriculumTotal = curriculumTotal;
            this.legalTotal = legalTotal;
            this.reviewPosition = reviewPosition;
        }
    }


    private static class PreviewState {

        private final Position position;

        private final int evaluation;

        private final SearchOutcome outcome;

        private final int mateDistance;


        private PreviewState(
                Position position,
                int evaluation,
                SearchOutcome outcome,
                int mateDistance
        ) {

            this.position =
                    position;


            this.evaluation =
                    evaluation;


            this.outcome =
                    outcome;


            this.mateDistance =
                    mateDistance;
        }
    }
}

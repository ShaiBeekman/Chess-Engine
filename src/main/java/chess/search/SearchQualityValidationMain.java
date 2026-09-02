package main.java.chess.search;

import main.java.chess.engine.ChessEngine;
import main.java.chess.model.Color;
import main.java.chess.model.FenCodec;
import main.java.chess.model.Move;
import main.java.chess.model.Position;
import main.java.chess.rules.MoveGenerator;
import main.java.chess.stockfish.StockfishClient;
import main.java.chess.stockfish.StockfishMoveAdapter;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * M79 — Dovetail / Hybrid move-quality validation against Stockfish.
 *
 * This milestone changes NO Dovetail or Hybrid search behavior.
 *
 * M79 answers the question left open by M78.1:
 *
 *     Does Dovetail's greater line depth produce stronger moves,
 *     or does Hybrid's broader graph coverage produce stronger moves?
 *
 * Procedure:
 *
 *     1. Run Dovetail and Hybrid on the same M78.1 positions.
 *     2. Capture each engine's selected move at:
 *
 *            500
 *          1,000
 *          2,500
 *          5,000
 *
 *        work units.
 *
 *     3. Ask Stockfish for an authoritative best move.
 *     4. Ask Stockfish MultiPV for candidate-move evaluations.
 *     5. Fall back to direct child analysis when an engine move is not
 *        contained in the requested MultiPV set.
 *     6. Measure:
 *
 *          - exact best-move agreement,
 *          - Stockfish evaluation of the chosen move,
 *          - centipawn loss when both scores are ordinary CP scores,
 *          - preservation of a Stockfish-proven mating continuation.
 *
 * Stockfish remains a reference engine only. It never participates in
 * PositionGraph exploration.
 */
public final class SearchQualityValidationMain {

    public static final String BUILD_ID =
            "M79-STOCKFISH-SEARCH-QUALITY-VALIDATION-V1";


    // =========================================================
    // Search checkpoints
    // =========================================================

    private static final int[] WORK_BUDGETS = {
            500,
            1_000,
            2_500,
            5_000
    };


    // =========================================================
    // Stockfish settings
    // =========================================================

    /*
     * Single-PV search decides the authoritative best move.
     *
     * This mirrors the GUI philosophy that ordinary Stockfish search,
     * rather than MultiPV ordering, decides which move is #1.
     */
    private static final int STOCKFISH_REFERENCE_DEPTH =
            18;

    /*
     * MultiPV is used only to obtain comparable scores for alternative
     * root moves chosen by Dovetail / Hybrid.
     */
    private static final int STOCKFISH_MULTIPV_DEPTH =
            18;

    /*
     * We do not need all 30-45 legal moves in most positions.
     *
     * If one of our engines chooses a move outside the top 20,
     * M79 performs a direct child-position fallback evaluation.
     */
    private static final int STOCKFISH_MAX_MULTIPV =
            20;

    /*
     * A direct child search approximates the same root horizon with one
     * ply already consumed.
     */
    private static final int STOCKFISH_FALLBACK_CHILD_DEPTH =
            STOCKFISH_REFERENCE_DEPTH - 1;


    private static final Duration REFERENCE_TIMEOUT =
            Duration.ofSeconds(
                    60
            );

    private static final Duration MULTIPV_TIMEOUT =
            Duration.ofSeconds(
                    90
            );

    private static final Duration FALLBACK_TIMEOUT =
            Duration.ofSeconds(
                    45
            );


    /*
     * Used only to order mate scores against ordinary centipawn scores
     * when producing an internal comparison value.
     *
     * It is NOT reported as literal centipawns.
     */
    private static final int MATE_SCORE_BASE =
            100_000;


    // =========================================================
    // Benchmark positions
    // =========================================================

    private static final List<BenchmarkPosition> POSITIONS =
            List.of(

                    new BenchmarkPosition(
                            "Starting position",
                            "General opening breadth",
                            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
                    ),

                    new BenchmarkPosition(
                            "Italian development",
                            "Ordinary developed opening",
                            "r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R b KQkq - 3 3"
                    ),

                    new BenchmarkPosition(
                            "Kiwipete",
                            "Complex middlegame / castling / tactical structure",
                            "r3k2r/p1ppqpb1/bn2pnp1/2pP4/1p2P3/2N2N2/PPQBBPPP/R3K2R w KQkq - 0 1"
                    ),

                    new BenchmarkPosition(
                            "Immediate tactical threat",
                            "Full-material forcing position with mate in one available",
                            "r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4"
                    ),

                    new BenchmarkPosition(
                            "Knight transpositions",
                            "Developed position with many move-order transpositions",
                            "r1bqkb1r/pppp1ppp/2n2n2/4p3/4P3/2N2N2/PPPP1PPP/R1BQKB1R w KQkq - 4 4"
                    )
            );


    private SearchQualityValidationMain() {
    }


    // =========================================================
    // Main
    // =========================================================

    public static void main(
            String[] args
    ) throws Exception {

        printHeader();

        verifyInstalledBuilds();


        System.out.println();

        try (StockfishClient stockfish =
                     StockfishClient.createConfiguredClient()) {

            System.out.println(
                    "Starting Stockfish reference engine..."
            );

            stockfish.start();


            System.out.println(
                    "Stockfish: "
                            + stockfish.getEngineName()
            );

            System.out.println(
                    "Executable: "
                            + stockfish.getExecutable()
            );

            System.out.println(
                    "Stockfish reference engine READY"
            );


            ValidationTotals dovetailTotals =
                    new ValidationTotals(
                            ChessEngine.SearchMode.DOVETAIL
                    );

            ValidationTotals hybridTotals =
                    new ValidationTotals(
                            ChessEngine.SearchMode.HYBRID
                    );


            System.out.println();


            int positionNumber =
                    1;

            for (BenchmarkPosition benchmarkPosition :
                    POSITIONS) {

                validatePosition(
                        stockfish,
                        benchmarkPosition,
                        positionNumber,
                        dovetailTotals,
                        hybridTotals
                );

                positionNumber++;
            }


            printOverallSummary(
                    dovetailTotals,
                    hybridTotals
            );
        }


        System.out.println();

        System.out.println(
                "============================================================"
        );

        System.out.println(
                "M79 STOCKFISH SEARCH-QUALITY VALIDATION COMPLETE"
        );

        System.out.println(
                "============================================================"
        );

        System.out.println();

        System.out.println(
                "No M76/M77 search tuning was performed."
        );

        System.out.println(
                "Use these results to decide whether M80 should tune"
        );

        System.out.println(
                "Dovetail, Hybrid, both, or neither."
        );
    }


    // =========================================================
    // Header
    // =========================================================

    private static void printHeader() {

        System.out.println(
                "Dovetail / Hybrid Stockfish quality validation"
        );

        System.out.println(
                "============================================================"
        );

        System.out.println(
                "Validation BUILD_ID: "
                        + BUILD_ID
        );

        System.out.println(
                "Walker BUILD_ID:     "
                        + DovetailWalker.BUILD_ID
        );

        System.out.println(
                "Coverage BUILD_ID:   "
                        + ExplorationScheduler.COVERAGE_BUILD_ID
        );

        System.out.println(
                "Engine mode BUILD:   "
                        + ChessEngine.MODE_BUILD_ID
        );

        System.out.println(
                "M78.1 BUILD_ID:      "
                        + SearchModeBenchmarkMain.BUILD_ID
        );

        System.out.println(
                "Stockfish depth:     "
                        + STOCKFISH_REFERENCE_DEPTH
        );

        System.out.println(
                "MultiPV depth:       "
                        + STOCKFISH_MULTIPV_DEPTH
        );

        System.out.println(
                "MultiPV maximum:     "
                        + STOCKFISH_MAX_MULTIPV
        );

        System.out.print(
                "Work checkpoints:    "
        );


        for (int index = 0;
             index < WORK_BUDGETS.length;
             index++) {

            if (index > 0) {

                System.out.print(
                        ", "
                );
            }


            System.out.printf(
                    "%,d",
                    WORK_BUDGETS[index]
            );
        }


        System.out.println();
    }


    // =========================================================
    // Build gate
    // =========================================================

    private static void verifyInstalledBuilds() {

        require(
                "M76-STRENGTH-AWARE-DOVETAIL-WALKERS-V1"
                        .equals(
                                DovetailWalker.BUILD_ID
                        ),
                "M76 walker build is not installed."
        );


        require(
                "M77-STRENGTH-AWARE-HYBRID-COVERAGE-V1"
                        .equals(
                                ExplorationScheduler.COVERAGE_BUILD_ID
                        ),
                "M77 Hybrid coverage build is not installed."
        );


        require(
                "M71-SEPARATE-DOVETAIL-HYBRID-V1"
                        .equals(
                                ChessEngine.MODE_BUILD_ID
                        ),
                "Dovetail / Hybrid engine-mode separation is missing."
        );


        require(
                "M78.1-DOVETAIL-HYBRID-CONTROLLED-BENCHMARK-V2"
                        .equals(
                                SearchModeBenchmarkMain.BUILD_ID
                        ),
                "M78.1 benchmark build is not installed."
        );


        System.out.println(
                "M76 walker build preserved PASSED"
        );

        System.out.println(
                "M77 Hybrid coverage build preserved PASSED"
        );

        System.out.println(
                "Dovetail / Hybrid routing preserved PASSED"
        );

        System.out.println(
                "M78.1 benchmark baseline preserved PASSED"
        );
    }


    // =========================================================
    // One benchmark position
    // =========================================================

    private static void validatePosition(
            StockfishClient stockfish,
            BenchmarkPosition benchmarkPosition,
            int positionNumber,
            ValidationTotals dovetailTotals,
            ValidationTotals hybridTotals
    ) throws IOException {

        System.out.println(
                "============================================================"
        );

        System.out.printf(
                "[%d / %d] %s%n",
                positionNumber,
                POSITIONS.size(),
                benchmarkPosition.name()
        );

        System.out.println(
                benchmarkPosition.description()
        );

        System.out.println(
                "FEN: "
                        + benchmarkPosition.fen()
        );

        System.out.println(
                "============================================================"
        );

        System.out.println();


        Position rootPosition =
                FenCodec.parse(
                        benchmarkPosition.fen()
                );


        /*
         * Run both custom engines independently from clean graphs.
         */
        ModeRun dovetail =
                runMode(
                        benchmarkPosition,
                        ChessEngine.SearchMode.DOVETAIL
                );

        ModeRun hybrid =
                runMode(
                        benchmarkPosition,
                        ChessEngine.SearchMode.HYBRID
                );


        /*
         * Build one Stockfish reference for the root.
         */
        StockfishReference reference =
                buildStockfishReference(
                        stockfish,
                        benchmarkPosition,
                        rootPosition
                );


        System.out.println(
                "STOCKFISH REFERENCE"
        );

        System.out.println(
                "-------------------"
        );

        System.out.printf(
                "Engine:      %s%n",
                reference.engineName()
        );

        System.out.printf(
                "Depth:       %d%n",
                reference.depth()
        );

        System.out.printf(
                "Best move:   %s%n",
                reference.bestMove()
        );

        System.out.printf(
                "Best SAN:    %s%n",
                StockfishMoveAdapter.san(
                        rootPosition,
                        reference.bestMove()
                )
        );

        System.out.printf(
                "Evaluation:  %s%n",
                reference.bestScore()
                        .display()
        );

        System.out.printf(
                "PV:          %s%n",
                reference.principalVariation()
                        .isEmpty()
                        ? "-"
                        : String.join(
                        " ",
                        reference.principalVariation()
                )
        );

        System.out.println();


        /*
         * Cache any fallback Stockfish evaluations so the same chosen move
         * is never searched repeatedly.
         */
        Map<String, StockfishScore> candidateCache =
                new HashMap<>(
                        reference.candidateScores()
                );


        System.out.println(
                "QUALITY CHECKPOINT COMPARISON"
        );

        System.out.println(
                "------------------------------------------------------------------------------------------------"
        );

        System.out.printf(
                "%-7s %-9s %-9s %-9s %-7s %-13s %-11s%n",
                "Budget",
                "Mode",
                "Move",
                "SF best",
                "Exact",
                "SF move score",
                "CP loss"
        );

        System.out.println(
                "------------------------------------------------------------------------------------------------"
        );


        for (int index = 0;
             index < WORK_BUDGETS.length;
             index++) {

            ModeCheckpoint d =
                    dovetail.checkpoints()
                            .get(
                                    index
                            );

            ModeCheckpoint h =
                    hybrid.checkpoints()
                            .get(
                                    index
                            );


            ValidationResult dResult =
                    validateCheckpoint(
                            stockfish,
                            rootPosition,
                            reference,
                            d,
                            candidateCache
                    );

            ValidationResult hResult =
                    validateCheckpoint(
                            stockfish,
                            rootPosition,
                            reference,
                            h,
                            candidateCache
                    );


            printValidationResult(
                    "DOVETAIL",
                    dResult
            );

            printValidationResult(
                    "HYBRID",
                    hResult
            );

            System.out.println();


            boolean finalCheckpoint =
                    index
                            == WORK_BUDGETS.length
                            - 1;


            dovetailTotals.add(
                    dResult,
                    finalCheckpoint
            );

            hybridTotals.add(
                    hResult,
                    finalCheckpoint
            );
        }


        printPositionSummary(
                reference,
                dovetail,
                hybrid
        );


        System.out.println();
    }


    // =========================================================
    // Run one custom mode
    // =========================================================

    private static ModeRun runMode(
            BenchmarkPosition benchmarkPosition,
            ChessEngine.SearchMode mode
    ) {

        Position position =
                FenCodec.parse(
                        benchmarkPosition.fen()
                );


        /*
         * Zero fixed-depth expansion, exactly as M78.1.
         */
        ChessEngine engine =
                new ChessEngine(
                        0
                );

        engine.setSearchMode(
                mode
        );

        engine.analyze(
                position
        );


        ExplorationScheduler scheduler =
                engine.getExplorationScheduler();


        require(
                scheduler != null,
                mode
                        + " created no scheduler."
        );


        require(
                scheduler.getMode()
                        ==
                        (
                                mode
                                        == ChessEngine.SearchMode.DOVETAIL
                                        ? ExplorationScheduler.Mode.DOVETAIL
                                        : ExplorationScheduler.Mode.HYBRID
                        ),
                mode
                        + " routed to the wrong scheduler mode."
        );


        List<ModeCheckpoint> checkpoints =
                new ArrayList<>();


        int previousBudget =
                0;


        for (int targetBudget :
                WORK_BUDGETS) {

            int additional =
                    targetBudget
                            - previousBudget;


            engine.advanceExplorationWork(
                    additional
            );


            /*
             * Force coherent minimax / proof propagation before choosing
             * the engine's current best move.
             */
            engine.createAnalysisSnapshot();


            PositionNode root =
                    engine.getActiveRoot();


            require(
                    root != null,
                    mode
                            + " lost its active root."
            );


            SearchEdge bestEdge =
                    bestRootEdge(
                            root
                    );


            String bestMove =
                    bestEdge == null
                            ? "-"
                            : moveText(
                            bestEdge.getMove()
                    );


            PositionNode bestTarget =
                    bestEdge == null
                            ? null
                            : bestEdge.getTarget();


            checkpoints.add(
                    new ModeCheckpoint(
                            targetBudget,
                            bestMove,
                            bestTarget == null
                                    ? 0
                                    : bestTarget.getSearchValue(),
                            bestTarget == null
                                    ? SearchOutcome.UNKNOWN
                                    : bestTarget.getSearchOutcome(),
                            bestTarget == null
                                    ? -1
                                    : bestTarget.getMateDistance(),
                            scheduler.getMaximumWalkerDepth(),
                            scheduler.getWalkerSteps(),
                            scheduler.getCoverageSteps()
                    )
            );


            previousBudget =
                    targetBudget;
        }


        if (mode
                == ChessEngine.SearchMode.DOVETAIL) {

            require(
                    scheduler.getCoverageSteps()
                            == 0L,
                    "Dovetail leaked Hybrid coverage during M79."
            );

        } else {

            require(
                    scheduler.getCoverageSteps()
                            > 0L,
                    "Hybrid performed no coverage work during M79."
            );
        }


        return new ModeRun(
                mode,
                List.copyOf(
                        checkpoints
                )
        );
    }


    // =========================================================
    // Stockfish reference
    // =========================================================

    private static StockfishReference buildStockfishReference(
            StockfishClient stockfish,
            BenchmarkPosition benchmarkPosition,
            Position rootPosition
    ) throws IOException {

        String fen =
                benchmarkPosition.fen();


        System.out.println(
                "Running Stockfish reference..."
        );


        /*
         * Ordinary single-PV search is authoritative for #1.
         */
        StockfishClient.Analysis authoritative =
                stockfish.analyzeFen(
                        fen,
                        STOCKFISH_REFERENCE_DEPTH,
                        REFERENCE_TIMEOUT
                );


        require(
                authoritative.bestMove()
                        != null,
                "Stockfish returned no authoritative best move."
        );


        StockfishScore bestScore =
                stockfishRootScore(
                        authoritative
                );


        /*
         * Populate likely alternative scores through MultiPV.
         */
        int legalMoveCount =
                new MoveGenerator()
                        .generateLegalMoves(
                                rootPosition
                        )
                        .size();


        int requestedMultiPv =
                Math.max(
                        1,
                        Math.min(
                                legalMoveCount,
                                STOCKFISH_MAX_MULTIPV
                        )
                );


        Map<String, StockfishScore> candidateScores =
                new HashMap<>();


        try {

            List<StockfishClient.Analysis> alternatives =
                    stockfish.analyzeFenMultiPv(
                            fen,
                            STOCKFISH_MULTIPV_DEPTH,
                            requestedMultiPv,
                            MULTIPV_TIMEOUT
                    );


            for (StockfishClient.Analysis alternative :
                    alternatives) {

                if (alternative == null
                        ||
                        alternative.bestMove()
                                == null) {

                    continue;
                }


                candidateScores.put(
                        normalizeUci(
                                alternative.bestMove()
                        ),
                        stockfishRootScore(
                                alternative
                        )
                );
            }


        } catch (IOException multiPvFailure) {

            /*
             * MultiPV is an optimization, not a requirement.
             *
             * M79 can still validate every selected custom-engine move by
             * directly evaluating the resulting child position.
             */
            System.out.println(
                    "MultiPV warning: "
                            + multiPvFailure.getMessage()
            );

            System.out.println(
                    "Falling back to direct candidate evaluation where needed."
            );
        }


        /*
         * The authoritative best move and score always take precedence over
         * a separately reported MultiPV copy.
         */
        candidateScores.put(
                normalizeUci(
                        authoritative.bestMove()
                ),
                bestScore
        );


        return new StockfishReference(
                authoritative.engineName(),
                authoritative.depth(),
                normalizeUci(
                        authoritative.bestMove()
                ),
                bestScore,
                authoritative.principalVariation(),
                candidateScores
        );
    }


    // =========================================================
    // Validate one custom-engine checkpoint
    // =========================================================

    private static ValidationResult validateCheckpoint(
            StockfishClient stockfish,
            Position rootPosition,
            StockfishReference reference,
            ModeCheckpoint checkpoint,
            Map<String, StockfishScore> candidateCache
    ) throws IOException {

        String customMove =
                normalizeUci(
                        checkpoint.bestMove()
                );


        boolean exact =
                customMove.equals(
                        reference.bestMove()
                );


        StockfishScore moveScore =
                candidateCache.get(
                        customMove
                );


        if (moveScore == null
                &&
                !"-".equals(
                        customMove
                )) {

            moveScore =
                    evaluateChosenMove(
                            stockfish,
                            rootPosition,
                            customMove
                    );


            candidateCache.put(
                    customMove,
                    moveScore
            );
        }


        Integer centipawnLoss =
                literalCentipawnLoss(
                        reference.bestScore(),
                        moveScore,
                        exact
                );


        Integer comparisonLoss =
                comparisonLoss(
                        reference.bestScore(),
                        moveScore,
                        exact
                );


        boolean winningMatePreserved =
                reference.bestScore()
                        .isWinningMate()
                        &&
                        moveScore != null
                        &&
                        moveScore.isWinningMate();


        return new ValidationResult(
                checkpoint,
                reference.bestMove(),
                exact,
                moveScore,
                centipawnLoss,
                comparisonLoss,
                winningMatePreserved
        );
    }


    // =========================================================
    // Direct candidate fallback
    // =========================================================

    private static StockfishScore evaluateChosenMove(
            StockfishClient stockfish,
            Position rootPosition,
            String rootMove
    ) throws IOException {

        Position child =
                StockfishMoveAdapter.resultingPosition(
                        rootPosition,
                        rootMove
                );


        require(
                child != null,
                "Custom engine selected move "
                        + rootMove
                        + " but StockfishMoveAdapter could not map it "
                        + "to a legal move."
        );


        StockfishClient.Analysis childAnalysis =
                stockfish.analyzeFen(
                        FenCodec.toFen(
                                child
                        ),
                        STOCKFISH_FALLBACK_CHILD_DEPTH,
                        FALLBACK_TIMEOUT
                );


        /*
         * IMPORTANT:
         *
         * Stockfish's child score is from the CHILD side-to-move
         * perspective.
         *
         * The root player made the selected move, so invert the score back
         * to the original root player's perspective.
         */
        Integer rootCentipawns =
                childAnalysis.centipawns()
                        == null
                        ? null
                        : -childAnalysis.centipawns();


        Integer rootMate =
                childAnalysis.mateIn()
                        == null
                        ? null
                        : -childAnalysis.mateIn();


        return new StockfishScore(
                rootCentipawns,
                rootMate
        );
    }


    // =========================================================
    // Stockfish score handling
    // =========================================================

    private static StockfishScore stockfishRootScore(
            StockfishClient.Analysis analysis
    ) {

        if (analysis == null) {

            return StockfishScore.unknown();
        }


        return new StockfishScore(
                analysis.centipawns(),
                analysis.mateIn()
        );
    }


    /*
     * Literal CPL is meaningful only when both Stockfish results are
     * ordinary centipawn evaluations.
     */
    private static Integer literalCentipawnLoss(
            StockfishScore best,
            StockfishScore chosen,
            boolean exact
    ) {

        if (exact) {

            return 0;
        }


        if (best == null
                ||
                chosen == null
                ||
                best.centipawns()
                        == null
                ||
                chosen.centipawns()
                        == null) {

            return null;
        }


        return Math.max(
                0,
                best.centipawns()
                        - chosen.centipawns()
        );
    }


    /*
     * A secondary ordering metric that also handles mate scores.
     *
     * This is useful internally and for deciding whether a non-exact move
     * discarded a forced mate, but it is NOT labeled as centipawn loss.
     */
    private static Integer comparisonLoss(
            StockfishScore best,
            StockfishScore chosen,
            boolean exact
    ) {

        if (exact) {

            return 0;
        }


        if (best == null
                ||
                chosen == null
                ||
                !best.isKnown()
                ||
                !chosen.isKnown()) {

            return null;
        }


        return Math.max(
                0,
                best.comparisonValue()
                        - chosen.comparisonValue()
        );
    }


    // =========================================================
    // Best custom-engine root move
    // =========================================================

    private static SearchEdge bestRootEdge(
            PositionNode root
    ) {

        if (root == null
                ||
                root.getOutgoingEdges()
                        .isEmpty()) {

            return null;
        }


        Color sideToMove =
                root.getPosition()
                        .getSideToMove();


        SearchEdge best =
                null;


        for (SearchEdge edge :
                root.getOutgoingEdges()) {

            if (best == null
                    ||
                    compareEdges(
                            edge,
                            best,
                            sideToMove
                    ) < 0) {

                best =
                        edge;
            }
        }


        return best;
    }


    private static int compareEdges(
            SearchEdge left,
            SearchEdge right,
            Color sideToMove
    ) {

        PositionNode leftNode =
                left.getTarget();

        PositionNode rightNode =
                right.getTarget();


        SearchOutcome winningOutcome =
                sideToMove
                        == Color.WHITE
                        ? SearchOutcome.WHITE_WIN
                        : SearchOutcome.BLACK_WIN;

        SearchOutcome losingOutcome =
                sideToMove
                        == Color.WHITE
                        ? SearchOutcome.BLACK_WIN
                        : SearchOutcome.WHITE_WIN;


        int leftCategory =
                outcomeCategory(
                        leftNode,
                        winningOutcome,
                        losingOutcome
                );

        int rightCategory =
                outcomeCategory(
                        rightNode,
                        winningOutcome,
                        losingOutcome
                );


        if (leftCategory
                != rightCategory) {

            return Integer.compare(
                    leftCategory,
                    rightCategory
            );
        }


        // -----------------------------------------------------
        // Proven win: shorter mate is better
        // -----------------------------------------------------

        if (leftNode.getSearchOutcome()
                == winningOutcome) {

            int comparison =
                    Integer.compare(
                            safeWinningMateDistance(
                                    leftNode
                            ),
                            safeWinningMateDistance(
                                    rightNode
                            )
                    );


            if (comparison != 0) {

                return comparison;
            }
        }


        // -----------------------------------------------------
        // Proven loss: longer survival is better
        // -----------------------------------------------------

        if (leftNode.getSearchOutcome()
                == losingOutcome) {

            int comparison =
                    Integer.compare(
                            safeLosingMateDistance(
                                    rightNode
                            ),
                            safeLosingMateDistance(
                                    leftNode
                            )
                    );


            if (comparison != 0) {

                return comparison;
            }
        }


        // -----------------------------------------------------
        // Otherwise backed-up search value
        // -----------------------------------------------------

        int valueComparison;


        if (sideToMove
                == Color.WHITE) {

            valueComparison =
                    Integer.compare(
                            rightNode.getSearchValue(),
                            leftNode.getSearchValue()
                    );

        } else {

            valueComparison =
                    Integer.compare(
                            leftNode.getSearchValue(),
                            rightNode.getSearchValue()
                    );
        }


        if (valueComparison != 0) {

            return valueComparison;
        }


        return moveText(
                left.getMove()
        ).compareTo(
                moveText(
                        right.getMove()
                )
        );
    }


    private static int outcomeCategory(
            PositionNode node,
            SearchOutcome winningOutcome,
            SearchOutcome losingOutcome
    ) {

        if (node.getSearchOutcome()
                == winningOutcome) {

            return 0;
        }


        if (node.getSearchOutcome()
                == SearchOutcome.UNKNOWN
                ||
                node.getSearchOutcome()
                        == SearchOutcome.DRAW) {

            return 1;
        }


        if (node.getSearchOutcome()
                == losingOutcome) {

            return 2;
        }


        return 1;
    }


    private static int safeWinningMateDistance(
            PositionNode node
    ) {

        return node.getMateDistance()
                < 0
                ? Integer.MAX_VALUE
                : node.getMateDistance();
    }


    private static int safeLosingMateDistance(
            PositionNode node
    ) {

        return node.getMateDistance()
                < 0
                ? 0
                : node.getMateDistance();
    }


    // =========================================================
    // Position result output
    // =========================================================

    private static void printValidationResult(
            String modeName,
            ValidationResult result
    ) {

        System.out.printf(
                "%-7s %-9s %-9s %-9s %-7s %-13s %-11s%n",
                formatInteger(
                        result.checkpoint()
                                .budget()
                ),
                modeName,
                result.checkpoint()
                        .bestMove(),
                result.stockfishBestMove(),
                result.exactAgreement()
                        ? "YES"
                        : "NO",
                result.stockfishMoveScore()
                        == null
                        ? "?"
                        : result.stockfishMoveScore()
                        .display(),
                centipawnLossDisplay(
                        result
                )
        );
    }


    private static String centipawnLossDisplay(
            ValidationResult result
    ) {

        if (result.exactAgreement()) {

            return "0";
        }


        if (result.centipawnLoss()
                != null) {

            return Integer.toString(
                    result.centipawnLoss()
            );
        }


        StockfishScore reference =
                result.stockfishMoveScore();


        if (result.winningMatePreserved()) {

            return "MATE OK";
        }


        if (result.comparisonLoss()
                != null
                &&
                result.comparisonLoss()
                        >= MATE_SCORE_BASE / 2) {

            return "MATE LOST";
        }


        if (reference != null
                &&
                reference.hasMateScore()) {

            return "MATE";
        }


        return "n/a";
    }


    private static void printPositionSummary(
            StockfishReference reference,
            ModeRun dovetail,
            ModeRun hybrid
    ) {

        ModeCheckpoint dFinal =
                dovetail.checkpoints()
                        .get(
                                dovetail.checkpoints()
                                        .size()
                                        - 1
                        );

        ModeCheckpoint hFinal =
                hybrid.checkpoints()
                        .get(
                                hybrid.checkpoints()
                                        .size()
                                        - 1
                        );


        System.out.println(
                "FINAL CHECKPOINT"
        );

        System.out.println(
                "----------------"
        );

        System.out.printf(
                "Stockfish: %s%n",
                reference.bestMove()
        );

        System.out.printf(
                "Dovetail:  %s | depth %,d%n",
                dFinal.bestMove(),
                dFinal.maximumWalkerDepth()
        );

        System.out.printf(
                "Hybrid:    %s | depth %,d%n",
                hFinal.bestMove(),
                hFinal.maximumWalkerDepth()
        );


        if (dFinal.bestMove()
                .equals(
                        hFinal.bestMove()
                )) {

            System.out.println(
                    "Custom engines agree on final move."
            );

        } else {

            System.out.println(
                    "Custom engines DISAGREE on final move."
            );
        }
    }


    // =========================================================
    // Overall totals
    // =========================================================

    private static void printOverallSummary(
            ValidationTotals dovetail,
            ValidationTotals hybrid
    ) {

        System.out.println(
                "============================================================"
        );

        System.out.println(
                "OVERALL STOCKFISH QUALITY SUMMARY"
        );

        System.out.println(
                "============================================================"
        );

        System.out.println();


        printTotals(
                dovetail
        );

        System.out.println();

        printTotals(
                hybrid
        );

        System.out.println();


        System.out.println(
                "DIRECT COMPARISON"
        );

        System.out.println(
                "-----------------"
        );


        if (dovetail.exactAgreements
                >
                hybrid.exactAgreements) {

            System.out.println(
                    "Exact Stockfish agreement: DOVETAIL higher"
            );

        } else if (hybrid.exactAgreements
                >
                dovetail.exactAgreements) {

            System.out.println(
                    "Exact Stockfish agreement: HYBRID higher"
            );

        } else {

            System.out.println(
                    "Exact Stockfish agreement: TIED"
            );
        }


        if (dovetail.finalExactAgreements
                >
                hybrid.finalExactAgreements) {

            System.out.println(
                    "Final-checkpoint Stockfish agreement: DOVETAIL higher"
            );

        } else if (hybrid.finalExactAgreements
                >
                dovetail.finalExactAgreements) {

            System.out.println(
                    "Final-checkpoint Stockfish agreement: HYBRID higher"
            );

        } else {

            System.out.println(
                    "Final-checkpoint Stockfish agreement: TIED"
            );
        }


        Double dAverage =
                dovetail.averageCentipawnLoss();

        Double hAverage =
                hybrid.averageCentipawnLoss();


        if (dAverage != null
                &&
                hAverage != null) {

            if (dAverage
                    <
                    hAverage) {

                System.out.println(
                        "Average ordinary-position CP loss: DOVETAIL lower"
                );

            } else if (hAverage
                    <
                    dAverage) {

                System.out.println(
                        "Average ordinary-position CP loss: HYBRID lower"
                );

            } else {

                System.out.println(
                        "Average ordinary-position CP loss: TIED"
                );
            }
        }
    }


    private static void printTotals(
            ValidationTotals totals
    ) {

        System.out.println(
                totals.mode
                        .name()
        );

        System.out.println(
                "-".repeat(
                        totals.mode
                                .name()
                                .length()
                )
        );


        System.out.printf(
                "Checkpoints judged:             %,d%n",
                totals.totalCheckpoints
        );

        System.out.printf(
                "Exact Stockfish agreements:     %,d / %,d (%.1f%%)%n",
                totals.exactAgreements,
                totals.totalCheckpoints,
                percentage(
                        totals.exactAgreements,
                        totals.totalCheckpoints
                )
        );

        System.out.printf(
                "Final-position agreements:      %,d / %,d (%.1f%%)%n",
                totals.finalExactAgreements,
                totals.finalCheckpointCount,
                percentage(
                        totals.finalExactAgreements,
                        totals.finalCheckpointCount
                )
        );

        System.out.printf(
                "Ordinary CP comparisons:        %,d%n",
                totals.centipawnComparisons
        );


        if (totals.centipawnComparisons
                > 0) {

            System.out.printf(
                    "Average centipawn loss:        %.2f%n",
                    totals.averageCentipawnLoss()
            );

            System.out.printf(
                    "Maximum centipawn loss:        %,d%n",
                    totals.maximumCentipawnLoss
            );

        } else {

            System.out.println(
                    "Average centipawn loss:        n/a"
            );

            System.out.println(
                    "Maximum centipawn loss:        n/a"
            );
        }


        System.out.printf(
                "Winning-mate checkpoints:       %,d%n",
                totals.winningMateCheckpoints
        );

        System.out.printf(
                "Winning mating line preserved:  %,d%n",
                totals.winningMatePreserved
        );
    }


    // =========================================================
    // Move formatting
    // =========================================================

    private static String moveText(
            Move move
    ) {

        if (move == null) {

            return "-";
        }


        String result =
                squareText(
                        move.from()
                                .file(),
                        move.from()
                                .rank()
                )
                        +
                        squareText(
                                move.to()
                                        .file(),
                                move.to()
                                        .rank()
                        );


        if (move.promotion()
                != null) {

            result +=
                    Character.toLowerCase(
                            move.promotion()
                                    .name()
                                    .charAt(
                                            0
                                    )
                    );
        }


        return result;
    }


    private static String squareText(
            int file,
            int rank
    ) {

        return ""
                + (char) (
                'a'
                        + file
        )
                + (
                rank
                        + 1
        );
    }


    private static String normalizeUci(
            String move
    ) {

        if (move == null
                ||
                move.isBlank()) {

            return "-";
        }


        return move.trim()
                .toLowerCase();
    }


    // =========================================================
    // Misc formatting
    // =========================================================

    private static String formatInteger(
            long value
    ) {

        return String.format(
                "%,d",
                value
        );
    }


    private static double percentage(
            long numerator,
            long denominator
    ) {

        if (denominator <= 0L) {

            return 0.0;
        }


        return 100.0
                * numerator
                / denominator;
    }


    // =========================================================
    // Assertion helper
    // =========================================================

    private static void require(
            boolean condition,
            String message
    ) {

        if (!condition) {

            throw new IllegalStateException(
                    message
            );
        }
    }


    // =========================================================
    // Mutable totals
    // =========================================================

    private static final class ValidationTotals {

        private final ChessEngine.SearchMode mode;

        private int totalCheckpoints;
        private int exactAgreements;

        private int finalCheckpointCount;
        private int finalExactAgreements;

        private int centipawnComparisons;
        private long totalCentipawnLoss;
        private int maximumCentipawnLoss;

        private int winningMateCheckpoints;
        private int winningMatePreserved;


        private ValidationTotals(
                ChessEngine.SearchMode mode
        ) {

            this.mode =
                    mode;
        }


        private void add(
                ValidationResult result,
                boolean finalCheckpoint
        ) {

            totalCheckpoints++;


            if (result.exactAgreement()) {

                exactAgreements++;
            }


            if (finalCheckpoint) {

                finalCheckpointCount++;


                if (result.exactAgreement()) {

                    finalExactAgreements++;
                }
            }


            if (result.centipawnLoss()
                    != null) {

                centipawnComparisons++;

                totalCentipawnLoss +=
                        result.centipawnLoss();

                maximumCentipawnLoss =
                        Math.max(
                                maximumCentipawnLoss,
                                result.centipawnLoss()
                        );
            }


            if (result.winningMatePreserved()
                    ||
                    (
                            result.comparisonLoss()
                                    != null
                                    &&
                                    result.comparisonLoss()
                                            >= MATE_SCORE_BASE / 2
                    )) {

                winningMateCheckpoints++;


                if (result.winningMatePreserved()) {

                    winningMatePreserved++;
                }
            }
        }


        private Double averageCentipawnLoss() {

            if (centipawnComparisons
                    == 0) {

                return null;
            }


            return (double) totalCentipawnLoss
                    / centipawnComparisons;
        }
    }


    // =========================================================
    // Records
    // =========================================================

    private record BenchmarkPosition(
            String name,
            String description,
            String fen
    ) {
    }


    private record ModeRun(
            ChessEngine.SearchMode mode,
            List<ModeCheckpoint> checkpoints
    ) {
    }


    private record ModeCheckpoint(
            int budget,
            String bestMove,
            int engineSearchValue,
            SearchOutcome engineOutcome,
            int engineMateDistance,
            int maximumWalkerDepth,
            long walkerSteps,
            long coverageSteps
    ) {
    }


    private record StockfishReference(
            String engineName,
            int depth,
            String bestMove,
            StockfishScore bestScore,
            List<String> principalVariation,
            Map<String, StockfishScore> candidateScores
    ) {

        private StockfishReference {

            principalVariation =
                    principalVariation == null
                            ? List.of()
                            : List.copyOf(
                            principalVariation
                    );

            candidateScores =
                    candidateScores == null
                            ? new HashMap<>()
                            : new HashMap<>(
                            candidateScores
                    );
        }
    }


    private record StockfishScore(
            Integer centipawns,
            Integer mateIn
    ) {

        private static StockfishScore unknown() {

            return new StockfishScore(
                    null,
                    null
            );
        }


        private boolean isKnown() {

            return centipawns != null
                    ||
                    mateIn != null;
        }


        private boolean hasMateScore() {

            return mateIn != null;
        }


        private boolean isWinningMate() {

            return mateIn != null
                    &&
                    mateIn > 0;
        }


        private int comparisonValue() {

            if (mateIn != null) {

                if (mateIn > 0) {

                    return MATE_SCORE_BASE
                            - Math.abs(
                            mateIn
                    );
                }


                if (mateIn < 0) {

                    return -MATE_SCORE_BASE
                            + Math.abs(
                            mateIn
                    );
                }


                return MATE_SCORE_BASE;
            }


            if (centipawns != null) {

                return centipawns;
            }


            return 0;
        }


        private String display() {

            if (mateIn != null) {

                if (mateIn > 0) {

                    return "mate +"
                            + mateIn;
                }


                return "mate "
                        + mateIn;
            }


            if (centipawns != null) {

                return String.format(
                        "%+.2f",
                        centipawns
                                / 100.0
                );
            }


            return "?";
        }
    }


    private record ValidationResult(
            ModeCheckpoint checkpoint,
            String stockfishBestMove,
            boolean exactAgreement,
            StockfishScore stockfishMoveScore,
            Integer centipawnLoss,
            Integer comparisonLoss,
            boolean winningMatePreserved
    ) {
    }
}
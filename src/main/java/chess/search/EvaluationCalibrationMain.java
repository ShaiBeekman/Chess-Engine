package main.java.chess.search;

import main.java.chess.engine.ChessEngine;
import main.java.chess.evaluation.PositionEvaluator;
import main.java.chess.model.Color;
import main.java.chess.model.FenCodec;
import main.java.chess.model.Move;
import main.java.chess.model.PieceType;
import main.java.chess.model.Position;
import main.java.chess.rules.MoveGenerator;
import main.java.chess.stockfish.StockfishClient;
import main.java.chess.stockfish.StockfishMoveAdapter;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * M80 — Stockfish-calibrated shared evaluation diagnostics.
 *
 * This milestone changes NO engine search behavior and NO evaluation weights.
 *
 * It asks where the shared Dovetail / Hybrid strength error begins:
 *
 *   1. Rank every legal root move using the current PositionEvaluator only.
 *   2. Obtain Stockfish's authoritative best move.
 *   3. Reproduce the final M79 Dovetail and Hybrid choices using the same
 *      checkpoint/snapshot cadence.
 *   4. Compare the Stockfish move and both search moves against the static
 *      evaluator's complete root-move ranking.
 *   5. Ask Stockfish to score the specific competing moves.
 *
 * Interpretation:
 *
 *   - If PositionEvaluator itself ranks the bad search move above the
 *     Stockfish move, shared static evaluation is a likely contributor.
 *
 *   - If PositionEvaluator ranks Stockfish's move first but the persistent
 *     search later chooses something else, graph backup / deeper search
 *     interaction becomes the stronger suspect.
 *
 * M80 is diagnostic only. Any coefficient or search-policy tuning belongs
 * to the next milestone after these results are observed.
 */
public final class EvaluationCalibrationMain {

    public static final String BUILD_ID =
            "M80-STOCKFISH-CALIBRATED-EVALUATION-DIAGNOSTICS-V1";


    // =========================================================
    // Search reproduction
    // =========================================================

    private static final int[] SEARCH_CHECKPOINTS = {
            500,
            1_000,
            2_500,
            5_000
    };


    // =========================================================
    // Stockfish reference settings
    // =========================================================

    private static final int STOCKFISH_ROOT_DEPTH =
            18;

    private static final int STOCKFISH_CHILD_DEPTH =
            STOCKFISH_ROOT_DEPTH - 1;

    private static final Duration STOCKFISH_ROOT_TIMEOUT =
            Duration.ofSeconds(60);

    private static final Duration STOCKFISH_CHILD_TIMEOUT =
            Duration.ofSeconds(45);


    // =========================================================
    // Output settings
    // =========================================================

    private static final int STATIC_TOP_COUNT =
            10;


    // =========================================================
    // Diagnostic positions
    // =========================================================

    private static final List<DiagnosticPosition> POSITIONS =
            List.of(

                    new DiagnosticPosition(
                            "Starting position",
                            "Control: both engines matched Stockfish throughout M79.",
                            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
                    ),

                    new DiagnosticPosition(
                            "Italian development",
                            "M79 miss: Stockfish Bc5; Dovetail Nd4; Hybrid d5.",
                            "r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R b KQkq - 3 3"
                    ),

                    new DiagnosticPosition(
                            "Prior complex castling fixture",
                            "This is the M78/M79 position previously mislabeled as Kiwipete; retained for exact comparability.",
                            "r3k2r/p1ppqpb1/bn2pnp1/2pP4/1p2P3/2N2N2/PPQBBPPP/R3K2R w KQkq - 0 1"
                    ),

                    new DiagnosticPosition(
                            "Immediate tactical threat",
                            "Control: both engines and Stockfish found Qxf7#.",
                            "r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4"
                    ),

                    new DiagnosticPosition(
                            "Knight transpositions",
                            "M79 shared miss: Stockfish Bb5; both custom modes chose Nxe5.",
                            "r1bqkb1r/pppp1ppp/2n2n2/4p3/4P3/2N2N2/PPPP1PPP/R1BQKB1R w KQkq - 4 4"
                    ),

                    new DiagnosticPosition(
                            "Canonical Kiwipete",
                            "Peter McKenzie's standard perft stress position; added as a correctly named control.",
                            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"
                    )
            );


    private EvaluationCalibrationMain() {
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

        PositionEvaluator evaluator =
                new PositionEvaluator();

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

            System.out.println();

            DiagnosticTotals totals =
                    new DiagnosticTotals();

            int index =
                    1;

            for (DiagnosticPosition diagnosticPosition :
                    POSITIONS) {

                diagnosePosition(
                        stockfish,
                        evaluator,
                        diagnosticPosition,
                        index,
                        totals
                );

                index++;
            }

            printOverallSummary(
                    totals
            );
        }

        System.out.println();
        System.out.println(
                "============================================================"
        );
        System.out.println(
                "M80 EVALUATION DIAGNOSTIC GATE COMPLETE"
        );
        System.out.println(
                "============================================================"
        );
        System.out.println();
        System.out.println(
                "No PositionEvaluator weights were changed."
        );
        System.out.println(
                "No Dovetail / Hybrid scheduling behavior was changed."
        );
        System.out.println(
                "Use this output to decide the first actual tuning milestone."
        );
    }


    // =========================================================
    // Header / build gate
    // =========================================================

    private static void printHeader() {

        System.out.println(
                "Stockfish-calibrated shared evaluation diagnostics"
        );
        System.out.println(
                "============================================================"
        );
        System.out.println(
                "Diagnostic BUILD_ID: "
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
                "M79 BUILD_ID:        "
                        + SearchQualityValidationMain.BUILD_ID
        );
        System.out.println(
                "Stockfish depth:     "
                        + STOCKFISH_ROOT_DEPTH
        );
        System.out.println(
                "Final search budget: 5,000 work units"
        );
        System.out.println(
                "Search snapshots:    500, 1,000, 2,500, 5,000"
        );
        System.out.println(
                "Evaluation tuning:   DISABLED"
        );
    }


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
                "Dovetail / Hybrid mode separation is missing."
        );

        require(
                "M78.1-DOVETAIL-HYBRID-CONTROLLED-BENCHMARK-V2"
                        .equals(
                                SearchModeBenchmarkMain.BUILD_ID
                        ),
                "M78.1 benchmark baseline is not installed."
        );

        require(
                "M79-STOCKFISH-SEARCH-QUALITY-VALIDATION-V1"
                        .equals(
                                SearchQualityValidationMain.BUILD_ID
                        ),
                "M79 quality-validation baseline is not installed."
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
        System.out.println(
                "M79 Stockfish baseline preserved PASSED"
        );
    }


    // =========================================================
    // One position
    // =========================================================

    private static void diagnosePosition(
            StockfishClient stockfish,
            PositionEvaluator evaluator,
            DiagnosticPosition diagnosticPosition,
            int positionNumber,
            DiagnosticTotals totals
    ) throws IOException {

        System.out.println(
                "============================================================"
        );
        System.out.printf(
                "[%d / %d] %s%n",
                positionNumber,
                POSITIONS.size(),
                diagnosticPosition.name()
        );
        System.out.println(
                diagnosticPosition.description()
        );
        System.out.println(
                "FEN: "
                        + diagnosticPosition.fen()
        );
        System.out.println(
                "============================================================"
        );
        System.out.println();

        Position root =
                FenCodec.parse(
                        diagnosticPosition.fen()
                );

        List<StaticCandidate> staticRanking =
                staticRanking(
                        root,
                        evaluator
                );

        require(
                !staticRanking.isEmpty(),
                "No legal moves in diagnostic position: "
                        + diagnosticPosition.name()
        );

        SearchResult dovetail =
                reproduceM79FinalChoice(
                        root,
                        ChessEngine.SearchMode.DOVETAIL
                );

        SearchResult hybrid =
                reproduceM79FinalChoice(
                        root,
                        ChessEngine.SearchMode.HYBRID
                );

        StockfishClient.Analysis stockfishRoot =
                stockfish.analyzeFen(
                        diagnosticPosition.fen(),
                        STOCKFISH_ROOT_DEPTH,
                        STOCKFISH_ROOT_TIMEOUT
                );

        String stockfishBest =
                normalizeUci(
                        stockfishRoot.bestMove()
                );

        StockfishScore stockfishBestScore =
                new StockfishScore(
                        stockfishRoot.centipawns(),
                        stockfishRoot.mateIn()
                );

        String staticBest =
                staticRanking.get(0)
                        .uci();

        int stockfishStaticRank =
                staticRankOf(
                        staticRanking,
                        stockfishBest
                );

        int dovetailStaticRank =
                staticRankOf(
                        staticRanking,
                        dovetail.move()
                );

        int hybridStaticRank =
                staticRankOf(
                        staticRanking,
                        hybrid.move()
                );

        StaticCandidate stockfishStaticCandidate =
                staticCandidate(
                        staticRanking,
                        stockfishBest
                );

        Map<String, StockfishScore> scoreCache =
                new HashMap<>();

        scoreCache.put(
                stockfishBest,
                stockfishBestScore
        );

        StockfishScore staticBestStockfishScore =
                scoreRootMove(
                        stockfish,
                        root,
                        staticBest,
                        stockfishBest,
                        stockfishBestScore,
                        scoreCache
                );

        StockfishScore dovetailStockfishScore =
                scoreRootMove(
                        stockfish,
                        root,
                        dovetail.move(),
                        stockfishBest,
                        stockfishBestScore,
                        scoreCache
                );

        StockfishScore hybridStockfishScore =
                scoreRootMove(
                        stockfish,
                        root,
                        hybrid.move(),
                        stockfishBest,
                        stockfishBestScore,
                        scoreCache
                );

        String diagnosis =
                diagnose(
                        stockfishBest,
                        staticBest,
                        stockfishStaticRank,
                        dovetail.move(),
                        dovetailStaticRank,
                        hybrid.move(),
                        hybridStaticRank
                );

        printStaticRanking(
                root,
                staticRanking,
                stockfishBest,
                dovetail.move(),
                hybrid.move()
        );

        System.out.println();
        System.out.println(
                "CALIBRATION SUMMARY"
        );
        System.out.println(
                "-------------------"
        );
        System.out.printf(
                "Stockfish best:           %-8s %-8s | %s%n",
                stockfishBest,
                StockfishMoveAdapter.san(
                        root,
                        stockfishBest
                ),
                stockfishBestScore.display()
        );
        System.out.printf(
                "Static evaluator best:    %-8s rank 1 | local %s | Stockfish %s%n",
                staticBest,
                formatLocalScore(
                        staticRanking.get(0)
                                .rootScore()
                ),
                staticBestStockfishScore.display()
        );
        System.out.printf(
                "Stockfish move static:    rank %-3d | local %s%n",
                stockfishStaticRank,
                stockfishStaticCandidate == null
                        ? "?"
                        : formatLocalScore(
                        stockfishStaticCandidate.rootScore()
                )
        );
        System.out.printf(
                "Dovetail final:            %-8s rank %-3d | depth %-3d | Stockfish %s%n",
                dovetail.move(),
                dovetailStaticRank,
                dovetail.maximumWalkerDepth(),
                dovetailStockfishScore.display()
        );
        System.out.printf(
                "Hybrid final:              %-8s rank %-3d | depth %-3d | Stockfish %s%n",
                hybrid.move(),
                hybridStaticRank,
                hybrid.maximumWalkerDepth(),
                hybridStockfishScore.display()
        );

        printLossLine(
                "Static-best loss",
                stockfishBestScore,
                staticBestStockfishScore,
                staticBest.equals(
                        stockfishBest
                )
        );
        printLossLine(
                "Dovetail loss",
                stockfishBestScore,
                dovetailStockfishScore,
                dovetail.move()
                        .equals(
                                stockfishBest
                        )
        );
        printLossLine(
                "Hybrid loss",
                stockfishBestScore,
                hybridStockfishScore,
                hybrid.move()
                        .equals(
                                stockfishBest
                        )
        );

        System.out.println(
                "Diagnosis:                "
                        + diagnosis
        );

        System.out.println();

        totals.positions++;

        if (staticBest.equals(
                stockfishBest
        )) {
            totals.staticBestMatchesStockfish++;
        }

        if (dovetail.move()
                .equals(
                        stockfishBest
                )) {
            totals.dovetailMatchesStockfish++;
        }

        if (hybrid.move()
                .equals(
                        stockfishBest
                )) {
            totals.hybridMatchesStockfish++;
        }

        if (diagnosis.startsWith(
                "STATIC"
        )) {
            totals.staticSuspect++;
        }

        if (diagnosis.startsWith(
                "SEARCH"
        )) {
            totals.searchSuspect++;
        }

        if (diagnosis.startsWith(
                "CONTROL"
        )) {
            totals.controls++;
        }

        if (diagnosis.startsWith(
                "MIXED"
        )) {
            totals.mixed++;
        }
    }


    // =========================================================
    // Complete static root ranking
    // =========================================================

    private static List<StaticCandidate> staticRanking(
            Position root,
            PositionEvaluator evaluator
    ) {

        MoveGenerator moveGenerator =
                new MoveGenerator();

        List<Move> legalMoves =
                moveGenerator.generateLegalMoves(
                        root
                );

        List<StaticCandidate> candidates =
                new ArrayList<>();

        Color rootSide =
                root.getSideToMove();

        for (Move move :
                legalMoves) {

            Position child =
                    root.makeMove(
                            move
                    );

            int whiteScore =
                    evaluator.evaluate(
                            child
                    );

            int rootScore =
                    rootSide == Color.WHITE
                            ? whiteScore
                            : -whiteScore;

            String uci =
                    moveText(
                            move
                    );

            candidates.add(
                    new StaticCandidate(
                            move,
                            uci,
                            StockfishMoveAdapter.san(
                                    root,
                                    uci
                            ),
                            whiteScore,
                            rootScore
                    )
            );
        }

        candidates.sort(
                (
                        left,
                        right
                ) -> {

                    int scoreComparison =
                            Integer.compare(
                                    right.rootScore(),
                                    left.rootScore()
                            );

                    if (scoreComparison != 0) {
                        return scoreComparison;
                    }

                    return left.uci()
                            .compareTo(
                                    right.uci()
                            );
                }
        );

        return List.copyOf(
                candidates
        );
    }


    private static int staticRankOf(
            List<StaticCandidate> ranking,
            String uci
    ) {

        String normalized =
                normalizeUci(
                        uci
                );

        for (int index = 0;
             index < ranking.size();
             index++) {

            if (ranking.get(index)
                    .uci()
                    .equals(
                            normalized
                    )) {

                return index + 1;
            }
        }

        return -1;
    }


    private static StaticCandidate staticCandidate(
            List<StaticCandidate> ranking,
            String uci
    ) {

        String normalized =
                normalizeUci(
                        uci
                );

        for (StaticCandidate candidate :
                ranking) {

            if (candidate.uci()
                    .equals(
                            normalized
                    )) {

                return candidate;
            }
        }

        return null;
    }


    // =========================================================
    // Reproduce M79 final search choice
    // =========================================================

    private static SearchResult reproduceM79FinalChoice(
            Position root,
            ChessEngine.SearchMode mode
    ) {

        ChessEngine engine =
                new ChessEngine(
                        0
                );

        engine.setSearchMode(
                mode
        );

        engine.analyze(
                root
        );

        int previous =
                0;

        for (int checkpoint :
                SEARCH_CHECKPOINTS) {

            engine.advanceExplorationWork(
                    checkpoint - previous
            );

            engine.createAnalysisSnapshot();

            previous =
                    checkpoint;
        }

        PositionNode rootNode =
                engine.getActiveRoot();

        ExplorationScheduler scheduler =
                engine.getExplorationScheduler();

        require(
                rootNode != null,
                mode
                        + " lost its root during M80."
        );

        require(
                scheduler != null,
                mode
                        + " lost its scheduler during M80."
        );

        SearchEdge best =
                bestRootEdge(
                        rootNode
                );

        require(
                best != null,
                mode
                        + " produced no root move during M80."
        );

        if (mode == ChessEngine.SearchMode.DOVETAIL) {

            require(
                    scheduler.getCoverageSteps()
                            == 0L,
                    "Dovetail leaked Hybrid coverage during M80."
            );

        } else {

            require(
                    scheduler.getCoverageSteps()
                            > 0L,
                    "Hybrid produced no coverage during M80."
            );
        }

        return new SearchResult(
                moveText(
                        best.getMove()
                ),
                scheduler.getMaximumWalkerDepth()
        );
    }


    private static SearchEdge bestRootEdge(
            PositionNode root
    ) {

        if (root == null
                || root.getOutgoingEdges()
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
                    || compareEdges(
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
                sideToMove == Color.WHITE
                        ? SearchOutcome.WHITE_WIN
                        : SearchOutcome.BLACK_WIN;

        SearchOutcome losingOutcome =
                sideToMove == Color.WHITE
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

        if (leftCategory != rightCategory) {

            return Integer.compare(
                    leftCategory,
                    rightCategory
            );
        }

        if (leftNode.getSearchOutcome()
                == winningOutcome) {

            int mateComparison =
                    Integer.compare(
                            safeWinningMateDistance(
                                    leftNode
                            ),
                            safeWinningMateDistance(
                                    rightNode
                            )
                    );

            if (mateComparison != 0) {
                return mateComparison;
            }
        }

        if (leftNode.getSearchOutcome()
                == losingOutcome) {

            int mateComparison =
                    Integer.compare(
                            safeLosingMateDistance(
                                    rightNode
                            ),
                            safeLosingMateDistance(
                                    leftNode
                            )
                    );

            if (mateComparison != 0) {
                return mateComparison;
            }
        }

        int valueComparison;

        if (sideToMove == Color.WHITE) {

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
                || node.getSearchOutcome()
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

        return node.getMateDistance() < 0
                ? Integer.MAX_VALUE
                : node.getMateDistance();
    }


    private static int safeLosingMateDistance(
            PositionNode node
    ) {

        return node.getMateDistance() < 0
                ? 0
                : node.getMateDistance();
    }


    // =========================================================
    // Stockfish candidate scoring
    // =========================================================

    private static StockfishScore scoreRootMove(
            StockfishClient stockfish,
            Position root,
            String rootMove,
            String stockfishBest,
            StockfishScore stockfishBestScore,
            Map<String, StockfishScore> cache
    ) throws IOException {

        String normalized =
                normalizeUci(
                        rootMove
                );

        StockfishScore cached =
                cache.get(
                        normalized
                );

        if (cached != null) {
            return cached;
        }

        if (normalized.equals(
                stockfishBest
        )) {
            return stockfishBestScore;
        }

        Position child =
                StockfishMoveAdapter.resultingPosition(
                        root,
                        normalized
                );

        require(
                child != null,
                "Could not map root move to legal child: "
                        + normalized
        );

        StockfishClient.Analysis childAnalysis =
                stockfish.analyzeFen(
                        FenCodec.toFen(
                                child
                        ),
                        STOCKFISH_CHILD_DEPTH,
                        STOCKFISH_CHILD_TIMEOUT
                );

        /*
         * Child analysis is reported from the CHILD side-to-move perspective.
         * Negate it to recover the original root player's perspective.
         */
        Integer rootCentipawns =
                childAnalysis.centipawns() == null
                        ? null
                        : -childAnalysis.centipawns();

        Integer rootMate =
                childAnalysis.mateIn() == null
                        ? null
                        : -childAnalysis.mateIn();

        StockfishScore result =
                new StockfishScore(
                        rootCentipawns,
                        rootMate
                );

        cache.put(
                normalized,
                result
        );

        return result;
    }


    private static Integer literalCentipawnLoss(
            StockfishScore best,
            StockfishScore candidate,
            boolean exact
    ) {

        if (exact) {
            return 0;
        }

        if (best == null
                || candidate == null
                || best.centipawns() == null
                || candidate.centipawns() == null) {
            return null;
        }

        return Math.max(
                0,
                best.centipawns()
                        - candidate.centipawns()
        );
    }


    // =========================================================
    // Diagnosis
    // =========================================================

    private static String diagnose(
            String stockfishBest,
            String staticBest,
            int stockfishStaticRank,
            String dovetailMove,
            int dovetailStaticRank,
            String hybridMove,
            int hybridStaticRank
    ) {

        boolean dCorrect =
                dovetailMove.equals(
                        stockfishBest
                );

        boolean hCorrect =
                hybridMove.equals(
                        stockfishBest
                );

        boolean staticCorrect =
                staticBest.equals(
                        stockfishBest
                );

        if (dCorrect
                && hCorrect) {

            return "CONTROL — both persistent searches agree with Stockfish.";
        }

        if (staticCorrect) {

            return "SEARCH SUSPECT — the one-ply static evaluator ranks Stockfish first, but persistent search moves away from it.";
        }

        if (dovetailMove.equals(
                staticBest
        )
                && hybridMove.equals(
                staticBest
        )) {

            return "STATIC SUSPECT — both search modes follow the static evaluator's #1 move instead of Stockfish.";
        }

        boolean bothPreferStaticTopTier =
                dovetailStaticRank > 0
                        && dovetailStaticRank <= 3
                        && hybridStaticRank > 0
                        && hybridStaticRank <= 3;

        if (bothPreferStaticTopTier
                && stockfishStaticRank > 3) {

            return "STATIC SUSPECT — both searches remain in the evaluator's top tier while Stockfish's move is ranked lower.";
        }

        if (stockfishStaticRank == 1
                || stockfishStaticRank == 2) {

            return "SEARCH SUSPECT — Stockfish's move is already near the top of static evaluation, but search does not retain it.";
        }

        return "MIXED — static ordering and deeper graph behavior both appear relevant.";
    }


    // =========================================================
    // Output
    // =========================================================

    private static void printStaticRanking(
            Position root,
            List<StaticCandidate> ranking,
            String stockfishBest,
            String dovetailMove,
            String hybridMove
    ) {

        System.out.println(
                "STATIC POSITIONEVALUATOR ROOT RANKING"
        );
        System.out.println(
                "------------------------------------------------------------"
        );
        System.out.printf(
                "%-5s %-8s %-10s %-10s %-12s%n",
                "Rank",
                "Move",
                "SAN",
                "Local",
                "Markers"
        );
        System.out.println(
                "------------------------------------------------------------"
        );

        int shown =
                Math.min(
                        STATIC_TOP_COUNT,
                        ranking.size()
                );

        for (int index = 0;
             index < shown;
             index++) {

            StaticCandidate candidate =
                    ranking.get(
                            index
                    );

            System.out.printf(
                    "%-5d %-8s %-10s %-10s %-12s%n",
                    index + 1,
                    candidate.uci(),
                    candidate.san(),
                    formatLocalScore(
                            candidate.rootScore()
                    ),
                    markers(
                            candidate.uci(),
                            stockfishBest,
                            dovetailMove,
                            hybridMove
                    )
            );
        }

        /*
         * If a specifically important move is outside the printed top ten,
         * print it explicitly so no diagnostic target disappears from view.
         */
        printImportantOutsideTop(
                ranking,
                stockfishBest,
                "SF",
                shown
        );
        printImportantOutsideTop(
                ranking,
                dovetailMove,
                "D",
                shown
        );
        printImportantOutsideTop(
                ranking,
                hybridMove,
                "H",
                shown
        );
    }


    private static void printImportantOutsideTop(
            List<StaticCandidate> ranking,
            String move,
            String marker,
            int shown
    ) {

        int rank =
                staticRankOf(
                        ranking,
                        move
                );

        if (rank <= 0
                || rank <= shown) {
            return;
        }

        StaticCandidate candidate =
                ranking.get(
                        rank - 1
                );

        System.out.printf(
                "%-5d %-8s %-10s %-10s %-12s%n",
                rank,
                candidate.uci(),
                candidate.san(),
                formatLocalScore(
                        candidate.rootScore()
                ),
                marker
        );
    }


    private static String markers(
            String move,
            String stockfishBest,
            String dovetailMove,
            String hybridMove
    ) {

        StringBuilder result =
                new StringBuilder();

        if (move.equals(
                stockfishBest
        )) {
            result.append(
                    "SF "
            );
        }

        if (move.equals(
                dovetailMove
        )) {
            result.append(
                    "D "
            );
        }

        if (move.equals(
                hybridMove
        )) {
            result.append(
                    "H "
            );
        }

        String text =
                result.toString()
                        .trim();

        return text.isEmpty()
                ? "-"
                : text;
    }


    private static void printLossLine(
            String label,
            StockfishScore best,
            StockfishScore candidate,
            boolean exact
    ) {

        Integer loss =
                literalCentipawnLoss(
                        best,
                        candidate,
                        exact
                );

        String display;

        if (exact) {

            display =
                    "0 cp";

        } else if (loss != null) {

            display =
                    loss
                            + " cp";

        } else if (best != null
                && best.isWinningMate()
                && candidate != null
                && candidate.isWinningMate()) {

            display =
                    "winning mate preserved";

        } else if (best != null
                && best.isWinningMate()) {

            display =
                    "winning mate lost";

        } else {

            display =
                    "n/a";
        }

        System.out.printf(
                "%-25s %s%n",
                label + ":",
                display
        );
    }


    private static void printOverallSummary(
            DiagnosticTotals totals
    ) {

        System.out.println(
                "============================================================"
        );
        System.out.println(
                "M80 OVERALL DIAGNOSTIC SUMMARY"
        );
        System.out.println(
                "============================================================"
        );
        System.out.println();
        System.out.printf(
                "Positions diagnosed:                 %d%n",
                totals.positions
        );
        System.out.printf(
                "Static #1 matches Stockfish:         %d / %d%n",
                totals.staticBestMatchesStockfish,
                totals.positions
        );
        System.out.printf(
                "Dovetail final matches Stockfish:    %d / %d%n",
                totals.dovetailMatchesStockfish,
                totals.positions
        );
        System.out.printf(
                "Hybrid final matches Stockfish:      %d / %d%n",
                totals.hybridMatchesStockfish,
                totals.positions
        );
        System.out.printf(
                "CONTROL classifications:             %d%n",
                totals.controls
        );
        System.out.printf(
                "STATIC SUSPECT classifications:      %d%n",
                totals.staticSuspect
        );
        System.out.printf(
                "SEARCH SUSPECT classifications:      %d%n",
                totals.searchSuspect
        );
        System.out.printf(
                "MIXED classifications:               %d%n",
                totals.mixed
        );
        System.out.println();
        System.out.println(
                "Interpretation rule:"
        );
        System.out.println(
                "  STATIC SUSPECT -> inspect PositionEvaluator terms/weights next."
        );
        System.out.println(
                "  SEARCH SUSPECT -> inspect graph backup / propagated values next."
        );
        System.out.println(
                "  MIXED          -> inspect both before tuning either one."
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
                        + squareText(
                        move.to()
                                .file(),
                        move.to()
                                .rank()
                );

        PieceType promotion =
                move.promotion();

        if (promotion != null) {

            result +=
                    switch (promotion) {
                        case QUEEN -> "q";
                        case ROOK -> "r";
                        case BISHOP -> "b";
                        case KNIGHT -> "n";
                        default -> "";
                    };
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
                || move.isBlank()) {
            return "-";
        }

        return move.trim()
                .toLowerCase();
    }


    private static String formatLocalScore(
            int centipawns
    ) {

        return String.format(
                "%+.2f",
                centipawns
                        / 100.0
        );
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
    // Records / totals
    // =========================================================

    private record DiagnosticPosition(
            String name,
            String description,
            String fen
    ) {
    }


    private record StaticCandidate(
            Move move,
            String uci,
            String san,
            int whiteScore,
            int rootScore
    ) {
    }


    private record SearchResult(
            String move,
            int maximumWalkerDepth
    ) {
    }


    private record StockfishScore(
            Integer centipawns,
            Integer mateIn
    ) {

        private boolean isWinningMate() {

            return mateIn != null
                    && mateIn > 0;
        }


        private String display() {

            if (mateIn != null) {

                return mateIn > 0
                        ? "mate +"
                        + mateIn
                        : "mate "
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


    private static final class DiagnosticTotals {

        private int positions;
        private int staticBestMatchesStockfish;
        private int dovetailMatchesStockfish;
        private int hybridMatchesStockfish;
        private int controls;
        private int staticSuspect;
        private int searchSuspect;
        private int mixed;
    }
}

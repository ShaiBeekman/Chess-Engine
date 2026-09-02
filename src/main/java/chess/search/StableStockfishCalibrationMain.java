package main.java.chess.search;

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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * M81 — Stable all-move Stockfish calibration.
 *
 * M80 established that the shared one-ply PositionEvaluator is the primary
 * suspect, but M79/M80 also exposed an important reference-measurement issue:
 * ordinary single-PV, broad MultiPV, and child-position fallback searches can
 * produce scores at different effective horizons and with different hash
 * history.
 *
 * M81 removes that ambiguity before any evaluation tuning occurs.
 *
 * For every diagnostic position:
 *
 *   1. Generate EVERY legal root move locally.
 *   2. Rank EVERY legal child with the unchanged PositionEvaluator.
 *   3. Run ONE Stockfish root search with MultiPV equal to the complete legal
 *      move count.
 *   4. Accept the Stockfish result only when every MultiPV index was completed
 *      at the exact same depth.
 *   5. Compare the complete local ranking with the complete Stockfish ranking.
 *
 * No Dovetail/Hybrid behavior and no PositionEvaluator coefficient changes are
 * made in this milestone.
 */
public final class StableStockfishCalibrationMain {

    public static final String BUILD_ID =
            "M81-STABLE-ALL-MOVE-STOCKFISH-CALIBRATION-V1";


    // =========================================================
    // Strict Stockfish settings
    // =========================================================

    /*
     * All legal root moves must complete this exact depth.
     *
     * This is deliberately lower than the old single-PV depth 18 because
     * MultiPV may contain 40-50 lines. Consistency across every legal move is
     * more important here than allowing one principal line to search deeper.
     */
    private static final int STOCKFISH_COMMON_DEPTH =
            14;

    private static final Duration STOCKFISH_TIMEOUT =
            Duration.ofSeconds(
                    180
            );

    private static final int DISPLAY_TOP_COUNT =
            12;


    // =========================================================
    // Diagnostic positions
    // =========================================================

    private static final List<DiagnosticPosition> POSITIONS =
            List.of(

                    new DiagnosticPosition(
                            "Starting position",
                            "Control position.",
                            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
                    ),

                    new DiagnosticPosition(
                            "Italian development",
                            "M80 static-evaluation suspect.",
                            "r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R b KQkq - 3 3"
                    ),

                    new DiagnosticPosition(
                            "Prior complex castling fixture",
                            "M78/M79 complex fixture retained for continuity.",
                            "r3k2r/p1ppqpb1/bn2pnp1/2pP4/1p2P3/2N2N2/PPQBBPPP/R3K2R w KQkq - 0 1"
                    ),

                    new DiagnosticPosition(
                            "Immediate tactical threat",
                            "Mate-in-one control: Qxf7# is available.",
                            "r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4"
                    ),

                    new DiagnosticPosition(
                            "Knight transpositions",
                            "M80 shared static-evaluation miss centered on Nxe5.",
                            "r1bqkb1r/pppp1ppp/2n2n2/4p3/4P3/2N2N2/PPPP1PPP/R1BQKB1R w KQkq - 4 4"
                    ),

                    new DiagnosticPosition(
                            "Canonical Kiwipete",
                            "Peter McKenzie's canonical perft stress position.",
                            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"
                    )
            );


    private StableStockfishCalibrationMain() {
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

        CalibrationTotals totals =
                new CalibrationTotals();

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
                    "Stockfish strict common-depth reference READY"
            );

            System.out.println();

            int positionNumber =
                    1;

            for (DiagnosticPosition diagnosticPosition :
                    POSITIONS) {

                calibratePosition(
                        stockfish,
                        evaluator,
                        diagnosticPosition,
                        positionNumber,
                        totals
                );

                positionNumber++;
            }
        }

        printOverallSummary(
                totals
        );

        System.out.println();
        System.out.println(
                "============================================================"
        );
        System.out.println(
                "M81 STABLE ALL-MOVE CALIBRATION GATE PASSED"
        );
        System.out.println(
                "============================================================"
        );
        System.out.println();
        System.out.println(
                "Every accepted Stockfish candidate set came from one root search"
        );
        System.out.println(
                "at one common completed depth."
        );
        System.out.println(
                "No PositionEvaluator weights were changed."
        );
        System.out.println(
                "No Dovetail / Hybrid scheduling behavior was changed."
        );
    }


    // =========================================================
    // Header / build gate
    // =========================================================

    private static void printHeader() {

        System.out.println(
                "Stable all-move Stockfish evaluation calibration"
        );
        System.out.println(
                "============================================================"
        );
        System.out.println(
                "Calibration BUILD_ID: "
                        + BUILD_ID
        );
        System.out.println(
                "Stockfish API BUILD:   "
                        + StockfishClient.CALIBRATION_BUILD_ID
        );
        System.out.println(
                "M80 BUILD_ID:          "
                        + EvaluationCalibrationMain.BUILD_ID
        );
        System.out.println(
                "Required common depth: "
                        + STOCKFISH_COMMON_DEPTH
        );
        System.out.println(
                "MultiPV width:          ALL LEGAL ROOT MOVES"
        );
        System.out.println(
                "Hash isolation:         ucinewgame before each position"
        );
        System.out.println(
                "Evaluation tuning:      DISABLED"
        );
    }


    private static void verifyInstalledBuilds() {

        require(
                "M81-COMMON-DEPTH-MULTIPV-V1"
                        .equals(
                                StockfishClient.CALIBRATION_BUILD_ID
                        ),
                "M81 strict Stockfish MultiPV API is not installed."
        );

        require(
                "M80-STOCKFISH-CALIBRATED-EVALUATION-DIAGNOSTICS-V1"
                        .equals(
                                EvaluationCalibrationMain.BUILD_ID
                        ),
                "M80 diagnostic baseline is not installed."
        );

        require(
                "M76-STRENGTH-AWARE-DOVETAIL-WALKERS-V1"
                        .equals(
                                DovetailWalker.BUILD_ID
                        ),
                "M76 walker baseline is not installed."
        );

        require(
                "M77-STRENGTH-AWARE-HYBRID-COVERAGE-V1"
                        .equals(
                                ExplorationScheduler.COVERAGE_BUILD_ID
                        ),
                "M77 Hybrid coverage baseline is not installed."
        );

        System.out.println(
                "M81 strict Stockfish API installed PASSED"
        );
        System.out.println(
                "M80 diagnostic baseline preserved PASSED"
        );
        System.out.println(
                "M76 / M77 search builds preserved PASSED"
        );
    }


    // =========================================================
    // One position
    // =========================================================

    private static void calibratePosition(
            StockfishClient stockfish,
            PositionEvaluator evaluator,
            DiagnosticPosition diagnosticPosition,
            int positionNumber,
            CalibrationTotals totals
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

        List<StaticCandidate> localRanking =
                staticRanking(
                        root,
                        evaluator
                );

        require(
                !localRanking.isEmpty(),
                "Diagnostic position has no legal moves: "
                        + diagnosticPosition.name()
        );

        int legalMoveCount =
                localRanking.size();

        System.out.printf(
                "Requesting Stockfish MultiPV=%d at depth %d...%n",
                legalMoveCount,
                STOCKFISH_COMMON_DEPTH
        );

        StockfishClient.MultiPvSnapshot snapshot =
                stockfish.analyzeFenMultiPvCommonDepth(
                        diagnosticPosition.fen(),
                        STOCKFISH_COMMON_DEPTH,
                        legalMoveCount,
                        STOCKFISH_TIMEOUT
                );

        require(
                snapshot.commonDepth()
                        == STOCKFISH_COMMON_DEPTH,
                "Strict Stockfish calibration did not complete requested depth "
                        + STOCKFISH_COMMON_DEPTH
                        + " for "
                        + diagnosticPosition.name()
                        + ". Highest complete common depth was "
                        + snapshot.commonDepth()
                        + "."
        );

        require(
                snapshot.lines().size()
                        == legalMoveCount,
                "Stockfish strict snapshot line count does not equal legal move count."
        );

        List<StockfishCandidate> stockfishRanking =
                stockfishRanking(
                        root,
                        snapshot
                );

        verifyCompleteMoveSet(
                localRanking,
                stockfishRanking,
                diagnosticPosition.name()
        );

        RankingMetrics metrics =
                rankingMetrics(
                        localRanking,
                        stockfishRanking
                );

        StaticCandidate localBest =
                localRanking.get(
                        0
                );

        StockfishCandidate stockfishBest =
                stockfishRanking.get(
                        0
                );

        int stockfishBestLocalRank =
                localRankOf(
                        localRanking,
                        stockfishBest.uci()
                );

        int localBestStockfishRank =
                stockfishRankOf(
                        stockfishRanking,
                        localBest.uci()
                );

        StockfishCandidate localBestStockfishCandidate =
                stockfishCandidate(
                        stockfishRanking,
                        localBest.uci()
                );

        require(
                localBestStockfishCandidate != null,
                "Stockfish ranking omitted local static best move."
        );

        printRanking(
                stockfishRanking,
                localRanking,
                localBest.uci()
        );

        System.out.println();
        System.out.println(
                "STRICT CALIBRATION SUMMARY"
        );
        System.out.println(
                "--------------------------"
        );
        System.out.printf(
                "Legal root moves:          %d%n",
                legalMoveCount
        );
        System.out.printf(
                "Requested depth:           %d%n",
                snapshot.requestedDepth()
        );
        System.out.printf(
                "Common completed depth:    %d%n",
                snapshot.commonDepth()
        );
        System.out.printf(
                "Stockfish best:            %-8s %-10s | %s%n",
                stockfishBest.uci(),
                stockfishBest.san(),
                stockfishBest.score().display()
        );
        System.out.printf(
                "Local static best:         %-8s %-10s | local %s | SF rank %d | SF %s%n",
                localBest.uci(),
                localBest.san(),
                formatLocalScore(
                        localBest.rootScore()
                ),
                localBestStockfishRank,
                localBestStockfishCandidate.score()
                        .display()
        );
        System.out.printf(
                "Stockfish-best local rank: %d | local %s%n",
                stockfishBestLocalRank,
                formatLocalScore(
                        localCandidate(
                                localRanking,
                                stockfishBest.uci()
                        ).rootScore()
                )
        );
        System.out.printf(
                "Top-3 set overlap:         %d / 3%n",
                metrics.top3Overlap()
        );
        System.out.printf(
                "Top-5 set overlap:         %d / 5%n",
                metrics.top5Overlap()
        );
        System.out.printf(
                "Mean absolute rank error:  %.2f%n",
                metrics.meanAbsoluteRankError()
        );
        System.out.printf(
                "Spearman rank correlation: %.4f%n",
                metrics.spearmanRho()
        );
        System.out.printf(
                "Static-best SF loss:       %s%n",
                lossDisplay(
                        stockfishBest.score(),
                        localBestStockfishCandidate.score(),
                        localBest.uci()
                                .equals(
                                        stockfishBest.uci()
                                )
                )
        );

        String classification =
                classify(
                        stockfishBestLocalRank,
                        localBestStockfishRank,
                        stockfishBest.score(),
                        localBestStockfishCandidate.score(),
                        localBest.uci()
                                .equals(
                                        stockfishBest.uci()
                                )
                );

        System.out.println(
                "Calibration class:          "
                        + classification
        );
        System.out.println();

        totals.positions++;
        totals.totalLegalMoves +=
                legalMoveCount;
        totals.minimumCommonDepth =
                Math.min(
                        totals.minimumCommonDepth,
                        snapshot.commonDepth()
                );
        totals.totalSpearman +=
                metrics.spearmanRho();
        totals.totalMeanAbsoluteRankError +=
                metrics.meanAbsoluteRankError();
        totals.totalTop3Overlap +=
                metrics.top3Overlap();
        totals.totalTop5Overlap +=
                metrics.top5Overlap();

        if (localBest.uci()
                .equals(
                        stockfishBest.uci()
                )) {

            totals.top1Matches++;
        }

        if (stockfishBestLocalRank <= 3) {
            totals.stockfishBestInLocalTop3++;
        }

        if (stockfishBestLocalRank <= 5) {
            totals.stockfishBestInLocalTop5++;
        }

        Integer literalLoss =
                literalCentipawnLoss(
                        stockfishBest.score(),
                        localBestStockfishCandidate.score(),
                        localBest.uci()
                                .equals(
                                        stockfishBest.uci()
                                )
                );

        if (literalLoss != null) {
            totals.ordinaryCpPositions++;
            totals.totalStaticBestCpLoss +=
                    literalLoss;
            totals.maximumStaticBestCpLoss =
                    Math.max(
                            totals.maximumStaticBestCpLoss,
                            literalLoss
                    );
        }

        if (classification.startsWith(
                "CALIBRATION TARGET"
        )) {
            totals.calibrationTargets++;
        }

        if (classification.startsWith(
                "LOW-COST"
        )) {
            totals.lowCostRankDisagreements++;
        }
    }


    // =========================================================
    // Local static ranking
    // =========================================================

    private static List<StaticCandidate> staticRanking(
            Position root,
            PositionEvaluator evaluator
    ) {

        List<Move> legalMoves =
                new MoveGenerator()
                        .generateLegalMoves(
                                root
                        );

        Color rootSide =
                root.getSideToMove();

        List<StaticCandidate> result =
                new ArrayList<>();

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

            result.add(
                    new StaticCandidate(
                            move,
                            uci,
                            StockfishMoveAdapter.san(
                                    root,
                                    uci
                            ),
                            rootScore
                    )
            );
        }

        result.sort(
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
                result
        );
    }


    // =========================================================
    // Strict Stockfish ranking
    // =========================================================

    private static List<StockfishCandidate> stockfishRanking(
            Position root,
            StockfishClient.MultiPvSnapshot snapshot
    ) {

        List<StockfishCandidate> result =
                new ArrayList<>();

        int rank =
                1;

        for (StockfishClient.Analysis analysis :
                snapshot.lines()) {

            require(
                    analysis.depth()
                            == snapshot.commonDepth(),
                    "Strict MultiPV snapshot contains mixed depths."
            );

            String uci =
                    normalizeUci(
                            analysis.bestMove()
                    );

            require(
                    StockfishMoveAdapter.findLegalMove(
                            root,
                            uci
                    ) != null,
                    "Stockfish strict MultiPV returned a move not recognized by local rules: "
                            + uci
            );

            result.add(
                    new StockfishCandidate(
                            rank,
                            uci,
                            StockfishMoveAdapter.san(
                                    root,
                                    uci
                            ),
                            new StockfishScore(
                                    analysis.centipawns(),
                                    analysis.mateIn()
                            )
                    )
            );

            rank++;
        }

        return List.copyOf(
                result
        );
    }


    private static void verifyCompleteMoveSet(
            List<StaticCandidate> localRanking,
            List<StockfishCandidate> stockfishRanking,
            String positionName
    ) {

        Set<String> localMoves =
                new HashSet<>();

        for (StaticCandidate candidate :
                localRanking) {

            localMoves.add(
                    candidate.uci()
            );
        }

        Set<String> stockfishMoves =
                new HashSet<>();

        for (StockfishCandidate candidate :
                stockfishRanking) {

            stockfishMoves.add(
                    candidate.uci()
            );
        }

        require(
                localMoves.size()
                        == localRanking.size(),
                "Duplicate move in local legal-move set for "
                        + positionName
        );

        require(
                stockfishMoves.size()
                        == stockfishRanking.size(),
                "Duplicate move in Stockfish strict MultiPV set for "
                        + positionName
        );

        if (!localMoves.equals(
                stockfishMoves
        )) {

            Set<String> missingFromStockfish =
                    new HashSet<>(
                            localMoves
                    );

            missingFromStockfish.removeAll(
                    stockfishMoves
            );

            Set<String> extraFromStockfish =
                    new HashSet<>(
                            stockfishMoves
                    );

            extraFromStockfish.removeAll(
                    localMoves
            );

            throw new IllegalStateException(
                    "Local/Stockfish legal root-move sets disagree for "
                            + positionName
                            + ". Missing from Stockfish: "
                            + missingFromStockfish
                            + "; extra from Stockfish: "
                            + extraFromStockfish
            );
        }
    }


    // =========================================================
    // Ranking metrics
    // =========================================================

    private static RankingMetrics rankingMetrics(
            List<StaticCandidate> localRanking,
            List<StockfishCandidate> stockfishRanking
    ) {

        int count =
                localRanking.size();

        require(
                count == stockfishRanking.size(),
                "Ranking metric inputs have different sizes."
        );

        Map<String, Integer> localRank =
                new HashMap<>();

        Map<String, Integer> stockfishRank =
                new HashMap<>();

        for (int index = 0;
             index < count;
             index++) {

            localRank.put(
                    localRanking.get(index)
                            .uci(),
                    index + 1
            );

            stockfishRank.put(
                    stockfishRanking.get(index)
                            .uci(),
                    index + 1
            );
        }

        long squaredDifferenceSum =
                0L;

        long absoluteDifferenceSum =
                0L;

        for (String move :
                localRank.keySet()) {

            int difference =
                    localRank.get(
                            move
                    )
                            - stockfishRank.get(
                            move
                    );

            squaredDifferenceSum +=
                    (long) difference
                            * difference;

            absoluteDifferenceSum +=
                    Math.abs(
                            difference
                    );
        }

        double spearman;

        if (count <= 1) {

            spearman =
                    1.0;

        } else {

            double denominator =
                    (double) count
                            * (
                            (double) count
                                    * count
                                    - 1.0
                    );

            spearman =
                    1.0
                            - 6.0
                            * squaredDifferenceSum
                            / denominator;
        }

        double meanAbsoluteRankError =
                count == 0
                        ? 0.0
                        : (double) absoluteDifferenceSum
                        / count;

        return new RankingMetrics(
                overlap(
                        localRanking,
                        stockfishRanking,
                        3
                ),
                overlap(
                        localRanking,
                        stockfishRanking,
                        5
                ),
                meanAbsoluteRankError,
                spearman
        );
    }


    private static int overlap(
            List<StaticCandidate> localRanking,
            List<StockfishCandidate> stockfishRanking,
            int requestedCount
    ) {

        int count =
                Math.min(
                        requestedCount,
                        Math.min(
                                localRanking.size(),
                                stockfishRanking.size()
                        )
                );

        Set<String> localTop =
                new HashSet<>();

        for (int index = 0;
             index < count;
             index++) {

            localTop.add(
                    localRanking.get(index)
                            .uci()
            );
        }

        int result =
                0;

        for (int index = 0;
             index < count;
             index++) {

            if (localTop.contains(
                    stockfishRanking.get(index)
                            .uci()
            )) {

                result++;
            }
        }

        return result;
    }


    // =========================================================
    // Output
    // =========================================================

    private static void printRanking(
            List<StockfishCandidate> stockfishRanking,
            List<StaticCandidate> localRanking,
            String localBestMove
    ) {

        System.out.println();
        System.out.println(
                "COMMON-DEPTH STOCKFISH ROOT RANKING"
        );
        System.out.println(
                "----------------------------------------------------------------------------"
        );
        System.out.printf(
                "%-5s %-8s %-10s %-12s %-8s %-10s %-10s%n",
                "SF#",
                "Move",
                "SAN",
                "SF score",
                "Local#",
                "Local",
                "Markers"
        );
        System.out.println(
                "----------------------------------------------------------------------------"
        );

        int shown =
                Math.min(
                        DISPLAY_TOP_COUNT,
                        stockfishRanking.size()
                );

        for (int index = 0;
             index < shown;
             index++) {

            printStockfishRow(
                    stockfishRanking.get(
                            index
                    ),
                    localRanking,
                    localBestMove
            );
        }

        int localBestStockfishRank =
                stockfishRankOf(
                        stockfishRanking,
                        localBestMove
                );

        if (localBestStockfishRank > shown) {

            printStockfishRow(
                    stockfishRanking.get(
                            localBestStockfishRank - 1
                    ),
                    localRanking,
                    localBestMove
            );
        }
    }


    private static void printStockfishRow(
            StockfishCandidate stockfishCandidate,
            List<StaticCandidate> localRanking,
            String localBestMove
    ) {

        StaticCandidate local =
                localCandidate(
                        localRanking,
                        stockfishCandidate.uci()
                );

        require(
                local != null,
                "Could not find Stockfish move in local ranking: "
                        + stockfishCandidate.uci()
        );

        int localRank =
                localRankOf(
                        localRanking,
                        stockfishCandidate.uci()
                );

        String markers =
                markers(
                        stockfishCandidate.rank(),
                        stockfishCandidate.uci(),
                        localBestMove
                );

        System.out.printf(
                "%-5d %-8s %-10s %-12s %-8d %-10s %-10s%n",
                stockfishCandidate.rank(),
                stockfishCandidate.uci(),
                stockfishCandidate.san(),
                stockfishCandidate.score()
                        .display(),
                localRank,
                formatLocalScore(
                        local.rootScore()
                ),
                markers
        );
    }


    private static String markers(
            int stockfishRank,
            String move,
            String localBestMove
    ) {

        StringBuilder result =
                new StringBuilder();

        if (stockfishRank == 1) {
            result.append(
                    "SF "
            );
        }

        if (move.equals(
                localBestMove
        )) {
            result.append(
                    "L "
            );
        }

        String value =
                result.toString()
                        .trim();

        return value.isEmpty()
                ? "-"
                : value;
    }


    private static String classify(
            int stockfishBestLocalRank,
            int localBestStockfishRank,
            StockfishScore stockfishBest,
            StockfishScore localBest,
            boolean exact
    ) {

        if (exact) {

            return "TOP-1 ALIGNED — local evaluator and strict Stockfish agree.";
        }

        Integer loss =
                literalCentipawnLoss(
                        stockfishBest,
                        localBest,
                        false
                );

        if (loss != null
                && loss <= 30) {

            return "LOW-COST RANK DISAGREEMENT — ordering differs, but local #1 is within 30 cp of strict Stockfish #1.";
        }

        if (stockfishBestLocalRank <= 3
                && localBestStockfishRank <= 3) {

            return "TOP-TIER DISAGREEMENT — both systems place the competing moves near the top.";
        }

        return "CALIBRATION TARGET — local and strict Stockfish rankings materially disagree.";
    }


    private static void printOverallSummary(
            CalibrationTotals totals
    ) {

        System.out.println(
                "============================================================"
        );
        System.out.println(
                "M81 OVERALL CALIBRATION SUMMARY"
        );
        System.out.println(
                "============================================================"
        );
        System.out.println();
        System.out.printf(
                "Positions calibrated:               %d%n",
                totals.positions
        );
        System.out.printf(
                "Total legal root moves calibrated:  %d%n",
                totals.totalLegalMoves
        );
        System.out.printf(
                "Minimum common completed depth:     %d%n",
                totals.minimumCommonDepth
        );
        System.out.printf(
                "Local #1 matches Stockfish #1:       %d / %d%n",
                totals.top1Matches,
                totals.positions
        );
        System.out.printf(
                "SF #1 appears in local top 3:        %d / %d%n",
                totals.stockfishBestInLocalTop3,
                totals.positions
        );
        System.out.printf(
                "SF #1 appears in local top 5:        %d / %d%n",
                totals.stockfishBestInLocalTop5,
                totals.positions
        );

        if (totals.positions > 0) {

            System.out.printf(
                    "Average top-3 overlap:              %.2f / 3%n",
                    (double) totals.totalTop3Overlap
                            / totals.positions
            );
            System.out.printf(
                    "Average top-5 overlap:              %.2f / 5%n",
                    (double) totals.totalTop5Overlap
                            / totals.positions
            );
            System.out.printf(
                    "Average mean absolute rank error:   %.2f%n",
                    totals.totalMeanAbsoluteRankError
                            / totals.positions
            );
            System.out.printf(
                    "Average Spearman correlation:       %.4f%n",
                    totals.totalSpearman
                            / totals.positions
            );
        }

        System.out.printf(
                "Ordinary-CP static-best positions:   %d%n",
                totals.ordinaryCpPositions
        );

        if (totals.ordinaryCpPositions > 0) {

            System.out.printf(
                    "Average static-best CP loss:        %.2f%n",
                    (double) totals.totalStaticBestCpLoss
                            / totals.ordinaryCpPositions
            );
            System.out.printf(
                    "Maximum static-best CP loss:        %d%n",
                    totals.maximumStaticBestCpLoss
            );
        }

        System.out.printf(
                "Low-cost rank disagreements:         %d%n",
                totals.lowCostRankDisagreements
        );
        System.out.printf(
                "Material calibration targets:        %d%n",
                totals.calibrationTargets
        );
    }


    // =========================================================
    // Rank helpers
    // =========================================================

    private static int localRankOf(
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


    private static int stockfishRankOf(
            List<StockfishCandidate> ranking,
            String uci
    ) {

        String normalized =
                normalizeUci(
                        uci
                );

        for (StockfishCandidate candidate :
                ranking) {

            if (candidate.uci()
                    .equals(
                            normalized
                    )) {

                return candidate.rank();
            }
        }

        return -1;
    }


    private static StaticCandidate localCandidate(
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


    private static StockfishCandidate stockfishCandidate(
            List<StockfishCandidate> ranking,
            String uci
    ) {

        String normalized =
                normalizeUci(
                        uci
                );

        for (StockfishCandidate candidate :
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
    // Score helpers
    // =========================================================

    private static Integer literalCentipawnLoss(
            StockfishScore best,
            StockfishScore candidate,
            boolean exact
    ) {

        if (best == null
                || candidate == null
                || best.centipawns() == null
                || candidate.centipawns() == null) {

            return null;
        }

        if (exact) {
            return 0;
        }

        return Math.max(
                0,
                best.centipawns()
                        - candidate.centipawns()
        );
    }


    private static String lossDisplay(
            StockfishScore best,
            StockfishScore candidate,
            boolean exact
    ) {

        if (exact) {
            return "0 cp";
        }

        Integer cpLoss =
                literalCentipawnLoss(
                        best,
                        candidate,
                        false
                );

        if (cpLoss != null) {
            return cpLoss
                    + " cp";
        }

        if (best != null
                && best.isWinningMate()
                && candidate != null
                && candidate.isWinningMate()) {

            return "winning mate preserved";
        }

        if (best != null
                && best.isWinningMate()) {

            return "winning mate lost";
        }

        return "n/a";
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
            int rootScore
    ) {
    }


    private record StockfishCandidate(
            int rank,
            String uci,
            String san,
            StockfishScore score
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


    private record RankingMetrics(
            int top3Overlap,
            int top5Overlap,
            double meanAbsoluteRankError,
            double spearmanRho
    ) {
    }


    private static final class CalibrationTotals {

        private int positions;
        private int totalLegalMoves;
        private int minimumCommonDepth =
                Integer.MAX_VALUE;

        private int top1Matches;
        private int stockfishBestInLocalTop3;
        private int stockfishBestInLocalTop5;

        private long totalTop3Overlap;
        private long totalTop5Overlap;
        private double totalMeanAbsoluteRankError;
        private double totalSpearman;

        private int ordinaryCpPositions;
        private long totalStaticBestCpLoss;
        private int maximumStaticBestCpLoss;

        private int lowCostRankDisagreements;
        private int calibrationTargets;
    }
}

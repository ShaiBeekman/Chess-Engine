package main.java.chess.search;

import main.java.chess.evaluation.PositionEvaluator;
import main.java.chess.model.Color;
import main.java.chess.model.FenCodec;
import main.java.chess.model.GameState;
import main.java.chess.model.Move;
import main.java.chess.model.PieceType;
import main.java.chess.model.Position;
import main.java.chess.rules.GameStateEvaluator;
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
 * M82 — One-reply tactical-horizon diagnostic.
 *
 * M81 established a rigorous all-legal-move Stockfish reference at one common
 * completed depth. M82 now asks a narrower question before any evaluator weight
 * is changed:
 *
 *     Are the major static-evaluation misses caused mainly by looking at the
 *     board immediately after our move, rather than considering the opponent's
 *     strongest immediate reply?
 *
 * For every legal root move M82 computes two local scores:
 *
 *     STATIC
 *         PositionEvaluator(position after our move)
 *
 *     ONE-REPLY
 *         our move
 *             -> enumerate every legal opponent reply
 *             -> score every resulting position
 *             -> assume the opponent chooses the worst result for us
 *
 * Checkmate and automatic draw states receive exact precedence instead of a
 * heuristic PositionEvaluator score.
 *
 * The two complete local rankings are then compared against the same strict
 * common-depth Stockfish ranking introduced by M81.
 *
 * M82 is diagnostic only:
 *
 *     - no PositionEvaluator coefficient changes,
 *     - no Dovetail / Hybrid changes,
 *     - no PositionGraph changes,
 *     - no GUI changes.
 */
public final class OneReplyHorizonDiagnosticMain {

    public static final String BUILD_ID =
            "M82-ONE-REPLY-TACTICAL-HORIZON-DIAGNOSTIC-V1";


    // =========================================================
    // Strict Stockfish reference
    // =========================================================

    private static final int STOCKFISH_COMMON_DEPTH =
            14;

    private static final Duration STOCKFISH_TIMEOUT =
            Duration.ofSeconds(
                    180
            );


    // =========================================================
    // Local exact terminal ordering
    // =========================================================

    /*
     * PositionEvaluator uses ordinary centipawn-like integer scores.
     * Keep exact terminal outcomes far outside the heuristic range.
     */
    private static final int TERMINAL_WIN_SCORE =
            1_000_000;

    private static final int TERMINAL_LOSS_SCORE =
            -TERMINAL_WIN_SCORE;

    private static final int TERMINAL_DRAW_SCORE =
            0;


    // =========================================================
    // Reporting / classification
    // =========================================================

    private static final int DISPLAY_TOP_COUNT =
            12;

    private static final int MATERIAL_IMPROVEMENT_CP =
            50;

    private static final int MODEST_IMPROVEMENT_CP =
            20;


    // =========================================================
    // Diagnostic positions — identical to M81
    // =========================================================

    private static final List<DiagnosticPosition> POSITIONS =
            List.of(

                    new DiagnosticPosition(
                            "Starting position",
                            "Low-cost opening-order disagreement control.",
                            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
                    ),

                    new DiagnosticPosition(
                            "Italian development",
                            "M81 calibration target: static d5 was 101 cp behind strict Stockfish #1.",
                            "r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R b KQkq - 3 3"
                    ),

                    new DiagnosticPosition(
                            "Prior complex castling fixture",
                            "M81 top-1 aligned control.",
                            "r3k2r/p1ppqpb1/bn2pnp1/2pP4/1p2P3/2N2N2/PPQBBPPP/R3K2R w KQkq - 0 1"
                    ),

                    new DiagnosticPosition(
                            "Immediate tactical threat",
                            "Mate-in-one control: one-reply logic must recognize Qxf7# exactly.",
                            "r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4"
                    ),

                    new DiagnosticPosition(
                            "Knight transpositions",
                            "M81 calibration target: static Nxe5 was 174 cp behind strict Stockfish #1.",
                            "r1bqkb1r/pppp1ppp/2n2n2/4p3/4P3/2N2N2/PPPP1PPP/R1BQKB1R w KQkq - 4 4"
                    ),

                    new DiagnosticPosition(
                            "Canonical Kiwipete",
                            "M81 top-1 aligned control.",
                            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"
                    )
            );


    private OneReplyHorizonDiagnosticMain() {
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

        MoveGenerator moveGenerator =
                new MoveGenerator();

        GameStateEvaluator gameStateEvaluator =
                new GameStateEvaluator();

        DiagnosticTotals totals =
                new DiagnosticTotals();


        try (StockfishClient stockfish =
                     StockfishClient.createConfiguredClient()) {

            System.out.println(
                    "Starting Stockfish strict reference engine..."
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

                diagnosePosition(
                        stockfish,
                        evaluator,
                        moveGenerator,
                        gameStateEvaluator,
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
                "M82 ONE-REPLY HORIZON DIAGNOSTIC GATE COMPLETE"
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
                "Use this result to decide whether M83 should add tactical"
        );
        System.out.println(
                "reply awareness or tune evaluator features / weights."
        );
    }


    // =========================================================
    // Header / build gate
    // =========================================================

    private static void printHeader() {

        System.out.println(
                "One-reply tactical-horizon evaluation diagnostic"
        );
        System.out.println(
                "============================================================"
        );
        System.out.println(
                "Diagnostic BUILD_ID: "
                        + BUILD_ID
        );
        System.out.println(
                "M81 BUILD_ID:        "
                        + StableStockfishCalibrationMain.BUILD_ID
        );
        System.out.println(
                "Stockfish API BUILD: "
                        + StockfishClient.CALIBRATION_BUILD_ID
        );
        System.out.println(
                "Common SF depth:     "
                        + STOCKFISH_COMMON_DEPTH
        );
        System.out.println(
                "Local horizon:       root move + every opponent reply"
        );
        System.out.println(
                "Terminal precedence: enabled"
        );
        System.out.println(
                "Evaluation tuning:   DISABLED"
        );
    }


    private static void verifyInstalledBuilds() {

        require(
                "M81-STABLE-ALL-MOVE-STOCKFISH-CALIBRATION-V1"
                        .equals(
                                StableStockfishCalibrationMain.BUILD_ID
                        ),
                "M81 stable calibration baseline is not installed."
        );

        require(
                "M81-COMMON-DEPTH-MULTIPV-V1"
                        .equals(
                                StockfishClient.CALIBRATION_BUILD_ID
                        ),
                "M81 strict Stockfish API is not installed."
        );

        require(
                "M80-STOCKFISH-CALIBRATED-EVALUATION-DIAGNOSTICS-V1"
                        .equals(
                                EvaluationCalibrationMain.BUILD_ID
                        ),
                "M80 evaluation diagnostic baseline is not installed."
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
                "M81 stable calibration baseline preserved PASSED"
        );
        System.out.println(
                "M81 strict Stockfish API preserved PASSED"
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

    private static void diagnosePosition(
            StockfishClient stockfish,
            PositionEvaluator evaluator,
            MoveGenerator moveGenerator,
            GameStateEvaluator gameStateEvaluator,
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

        Color rootSide =
                root.getSideToMove();


        List<Move> legalMoves =
                moveGenerator.generateLegalMoves(
                        root
                );

        require(
                !legalMoves.isEmpty(),
                "Diagnostic position has no legal root moves: "
                        + diagnosticPosition.name()
        );


        List<LocalCandidate> staticRanking =
                staticRanking(
                        root,
                        rootSide,
                        legalMoves,
                        evaluator
                );

        List<ReplyCandidate> replyRanking =
                replyAwareRanking(
                        root,
                        rootSide,
                        legalMoves,
                        evaluator,
                        moveGenerator,
                        gameStateEvaluator
                );


        System.out.printf(
                "Requesting strict Stockfish MultiPV=%d at depth %d...%n",
                legalMoves.size(),
                STOCKFISH_COMMON_DEPTH
        );


        StockfishClient.MultiPvSnapshot snapshot =
                stockfish.analyzeFenMultiPvCommonDepth(
                        diagnosticPosition.fen(),
                        STOCKFISH_COMMON_DEPTH,
                        legalMoves.size(),
                        STOCKFISH_TIMEOUT
                );


        require(
                snapshot.commonDepth()
                        == STOCKFISH_COMMON_DEPTH,
                "M82 Stockfish reference did not complete required common depth "
                        + STOCKFISH_COMMON_DEPTH
                        + " for "
                        + diagnosticPosition.name()
                        + ". Highest common depth: "
                        + snapshot.commonDepth()
        );

        require(
                snapshot.lines().size()
                        == legalMoves.size(),
                "Strict Stockfish snapshot does not cover every legal root move."
        );


        List<StockfishCandidate> stockfishRanking =
                stockfishRanking(
                        root,
                        snapshot
                );


        verifyCompleteMoveSet(
                staticRanking,
                replyRanking,
                stockfishRanking,
                diagnosticPosition.name()
        );


        RankingMetrics staticMetrics =
                rankingMetrics(
                        staticRanking,
                        stockfishRanking
                );

        RankingMetrics replyMetrics =
                rankingMetrics(
                        replyRanking,
                        stockfishRanking
                );


        LocalCandidate staticBest =
                staticRanking.get(
                        0
                );

        ReplyCandidate replyBest =
                replyRanking.get(
                        0
                );

        StockfishCandidate stockfishBest =
                stockfishRanking.get(
                        0
                );


        StockfishCandidate staticBestSf =
                stockfishCandidate(
                        stockfishRanking,
                        staticBest.uci()
                );

        StockfishCandidate replyBestSf =
                stockfishCandidate(
                        stockfishRanking,
                        replyBest.uci()
                );


        require(
                staticBestSf != null,
                "Stockfish ranking omitted static best move."
        );

        require(
                replyBestSf != null,
                "Stockfish ranking omitted reply-aware best move."
        );


        int stockfishBestStaticRank =
                localRankOf(
                        staticRanking,
                        stockfishBest.uci()
                );

        int stockfishBestReplyRank =
                replyRankOf(
                        replyRanking,
                        stockfishBest.uci()
                );

        int staticBestStockfishRank =
                stockfishRankOf(
                        stockfishRanking,
                        staticBest.uci()
                );

        int replyBestStockfishRank =
                stockfishRankOf(
                        stockfishRanking,
                        replyBest.uci()
                );


        int staticComparisonLoss =
                comparisonLoss(
                        stockfishBest.score(),
                        staticBestSf.score()
                );

        int replyComparisonLoss =
                comparisonLoss(
                        stockfishBest.score(),
                        replyBestSf.score()
                );


        Integer staticCpLoss =
                literalCentipawnLoss(
                        stockfishBest.score(),
                        staticBestSf.score()
                );

        Integer replyCpLoss =
                literalCentipawnLoss(
                        stockfishBest.score(),
                        replyBestSf.score()
                );


        String classification =
                classify(
                        staticBest,
                        replyBest,
                        stockfishBest,
                        staticBestStockfishRank,
                        replyBestStockfishRank,
                        stockfishBestStaticRank,
                        stockfishBestReplyRank,
                        staticComparisonLoss,
                        replyComparisonLoss
                );


        printComparisonRanking(
                stockfishRanking,
                staticRanking,
                replyRanking,
                staticBest.uci(),
                replyBest.uci()
        );


        System.out.println();
        System.out.println(
                "ONE-REPLY HORIZON SUMMARY"
        );
        System.out.println(
                "-------------------------"
        );
        System.out.printf(
                "Legal root moves:             %d%n",
                legalMoves.size()
        );
        System.out.printf(
                "Strict Stockfish depth:       %d%n",
                snapshot.commonDepth()
        );
        System.out.printf(
                "Stockfish best:               %-8s %-10s | %s%n",
                stockfishBest.uci(),
                stockfishBest.san(),
                stockfishBest.score()
                        .display()
        );
        System.out.printf(
                "Static best:                  %-8s %-10s | static %s | SF rank %d | SF %s%n",
                staticBest.uci(),
                staticBest.san(),
                formatLocalScore(
                        staticBest.rootScore()
                ),
                staticBestStockfishRank,
                staticBestSf.score()
                        .display()
        );
        System.out.printf(
                "One-reply best:               %-8s %-10s | reply %s | SF rank %d | SF %s%n",
                replyBest.uci(),
                replyBest.san(),
                formatReplyScore(
                        replyBest.rootScore()
                ),
                replyBestStockfishRank,
                replyBestSf.score()
                        .display()
        );
        System.out.printf(
                "One-reply best worst reply:   %-8s %-10s%n",
                replyBest.worstReplyUci(),
                replyBest.worstReplySan()
        );
        System.out.printf(
                "SF-best static rank:          %d%n",
                stockfishBestStaticRank
        );
        System.out.printf(
                "SF-best one-reply rank:       %d%n",
                stockfishBestReplyRank
        );
        System.out.printf(
                "Static-best SF loss:          %s%n",
                lossDisplay(
                        stockfishBest.score(),
                        staticBestSf.score(),
                        staticBest.uci()
                                .equals(
                                        stockfishBest.uci()
                                )
                )
        );
        System.out.printf(
                "One-reply-best SF loss:       %s%n",
                lossDisplay(
                        stockfishBest.score(),
                        replyBestSf.score(),
                        replyBest.uci()
                                .equals(
                                        stockfishBest.uci()
                                )
                )
        );
        System.out.printf(
                "Static Spearman vs SF:        %.4f%n",
                staticMetrics.spearmanRho()
        );
        System.out.printf(
                "One-reply Spearman vs SF:     %.4f%n",
                replyMetrics.spearmanRho()
        );
        System.out.printf(
                "Static mean rank error:       %.2f%n",
                staticMetrics.meanAbsoluteRankError()
        );
        System.out.printf(
                "One-reply mean rank error:    %.2f%n",
                replyMetrics.meanAbsoluteRankError()
        );
        System.out.printf(
                "Static top-5 overlap:         %d / 5%n",
                staticMetrics.top5Overlap()
        );
        System.out.printf(
                "One-reply top-5 overlap:      %d / 5%n",
                replyMetrics.top5Overlap()
        );
        System.out.println(
                "Diagnosis:                    "
                        + classification
        );
        System.out.println();


        // -----------------------------------------------------
        // Aggregate results
        // -----------------------------------------------------

        totals.positions++;
        totals.totalLegalMoves +=
                legalMoves.size();

        totals.totalStaticSpearman +=
                staticMetrics.spearmanRho();

        totals.totalReplySpearman +=
                replyMetrics.spearmanRho();

        totals.totalStaticMeanRankError +=
                staticMetrics.meanAbsoluteRankError();

        totals.totalReplyMeanRankError +=
                replyMetrics.meanAbsoluteRankError();

        totals.totalStaticTop5Overlap +=
                staticMetrics.top5Overlap();

        totals.totalReplyTop5Overlap +=
                replyMetrics.top5Overlap();


        if (staticBest.uci()
                .equals(
                        stockfishBest.uci()
                )) {

            totals.staticTop1Matches++;
        }

        if (replyBest.uci()
                .equals(
                        stockfishBest.uci()
                )) {

            totals.replyTop1Matches++;
        }


        if (staticCpLoss != null) {

            totals.staticCpPositions++;
            totals.totalStaticCpLoss +=
                    staticCpLoss;
            totals.maximumStaticCpLoss =
                    Math.max(
                            totals.maximumStaticCpLoss,
                            staticCpLoss
                    );
        }

        if (replyCpLoss != null) {

            totals.replyCpPositions++;
            totals.totalReplyCpLoss +=
                    replyCpLoss;
            totals.maximumReplyCpLoss =
                    Math.max(
                            totals.maximumReplyCpLoss,
                            replyCpLoss
                    );
        }


        if (replyComparisonLoss
                < staticComparisonLoss) {

            totals.positionsImproved++;
        }

        if (classification.startsWith(
                "HORIZON CONFIRMED"
        )) {

            totals.horizonConfirmed++;
        }

        if (classification.startsWith(
                "HORIZON HELPED"
        )) {

            totals.horizonHelped++;
        }

        if (classification.startsWith(
                "FEATURE / WEIGHT"
        )) {

            totals.featureWeightSuspect++;
        }

        if (classification.startsWith(
                "CONTROL"
        )) {

            totals.controls++;
        }
    }


    // =========================================================
    // Static ranking — unchanged M81 concept
    // =========================================================

    private static List<LocalCandidate> staticRanking(
            Position root,
            Color rootSide,
            List<Move> legalMoves,
            PositionEvaluator evaluator
    ) {

        List<LocalCandidate> candidates =
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
                    scoreForRootSide(
                            whiteScore,
                            rootSide
                    );

            String uci =
                    moveText(
                            move
                    );


            candidates.add(
                    new LocalCandidate(
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


        candidates.sort(
                (
                        left,
                        right
                ) -> compareLocalScores(
                        left.rootScore(),
                        left.uci(),
                        right.rootScore(),
                        right.uci()
                )
        );


        return List.copyOf(
                candidates
        );
    }


    // =========================================================
    // One-opponent-reply ranking
    // =========================================================

    private static List<ReplyCandidate> replyAwareRanking(
            Position root,
            Color rootSide,
            List<Move> legalMoves,
            PositionEvaluator evaluator,
            MoveGenerator moveGenerator,
            GameStateEvaluator gameStateEvaluator
    ) {

        List<ReplyCandidate> candidates =
                new ArrayList<>();


        for (Move move :
                legalMoves) {

            Position child =
                    root.makeMove(
                            move
                    );

            String uci =
                    moveText(
                            move
                    );

            String san =
                    StockfishMoveAdapter.san(
                            root,
                            uci
                    );


            GameState childState =
                    gameStateEvaluator.evaluate(
                            child
                    );


            // -------------------------------------------------
            // Our root move immediately ends the game.
            // -------------------------------------------------

            if (childState
                    == GameState.CHECKMATE) {

                /*
                 * After our move the opponent is side to move.
                 * CHECKMATE therefore means our root move won.
                 */
                candidates.add(
                        new ReplyCandidate(
                                move,
                                uci,
                                san,
                                TERMINAL_WIN_SCORE,
                                "#",
                                "checkmate",
                                0,
                                true
                        )
                );

                continue;
            }


            if (isDraw(
                    childState
            )) {

                candidates.add(
                        new ReplyCandidate(
                                move,
                                uci,
                                san,
                                TERMINAL_DRAW_SCORE,
                                "draw",
                                "draw",
                                0,
                                true
                        )
                );

                continue;
            }


            List<Move> replies =
                    moveGenerator.generateLegalMoves(
                            child
                    );


            require(
                    !replies.isEmpty(),
                    "Non-terminal child unexpectedly has no legal opponent replies after "
                            + uci
            );


            int worstRootScore =
                    Integer.MAX_VALUE;

            String worstReplyUci =
                    "-";

            String worstReplySan =
                    "-";


            for (Move reply :
                    replies) {

                Position grandchild =
                        child.makeMove(
                                reply
                        );

                int replyScore =
                        scoreLeafForRoot(
                                grandchild,
                                rootSide,
                                evaluator,
                                gameStateEvaluator
                        );


                String replyUci =
                        moveText(
                                reply
                        );


                if (replyScore
                        < worstRootScore
                        || (
                        replyScore
                                == worstRootScore
                                && replyUci.compareTo(
                                worstReplyUci
                        ) < 0
                )) {

                    worstRootScore =
                            replyScore;

                    worstReplyUci =
                            replyUci;

                    worstReplySan =
                            StockfishMoveAdapter.san(
                                    child,
                                    replyUci
                            );
                }
            }


            candidates.add(
                    new ReplyCandidate(
                            move,
                            uci,
                            san,
                            worstRootScore,
                            worstReplyUci,
                            worstReplySan,
                            replies.size(),
                            false
                    )
            );
        }


        candidates.sort(
                (
                        left,
                        right
                ) -> compareLocalScores(
                        left.rootScore(),
                        left.uci(),
                        right.rootScore(),
                        right.uci()
                )
        );


        return List.copyOf(
                candidates
        );
    }


    private static int scoreLeafForRoot(
            Position position,
            Color rootSide,
            PositionEvaluator evaluator,
            GameStateEvaluator gameStateEvaluator
    ) {

        GameState state =
                gameStateEvaluator.evaluate(
                        position
                );


        if (state
                == GameState.CHECKMATE) {

            /*
             * After the opponent's reply it is the original root player's turn.
             * CHECKMATE therefore means the opponent has mated the root player.
             */
            return TERMINAL_LOSS_SCORE;
        }


        if (isDraw(
                state
        )) {

            return TERMINAL_DRAW_SCORE;
        }


        int whiteScore =
                evaluator.evaluate(
                        position
                );


        return scoreForRootSide(
                whiteScore,
                rootSide
        );
    }


    private static boolean isDraw(
            GameState state
    ) {

        return state
                == GameState.STALEMATE
                || state
                == GameState.DRAW_75_MOVE
                || state
                == GameState.DRAW_FIVEFOLD_REPETITION;
    }


    private static int scoreForRootSide(
            int whiteScore,
            Color rootSide
    ) {

        return rootSide
                == Color.WHITE
                ? whiteScore
                : -whiteScore;
    }


    private static int compareLocalScores(
            int leftScore,
            String leftMove,
            int rightScore,
            String rightMove
    ) {

        int scoreComparison =
                Integer.compare(
                        rightScore,
                        leftScore
                );

        if (scoreComparison != 0) {

            return scoreComparison;
        }


        return leftMove.compareTo(
                rightMove
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

        for (StockfishClient.Analysis line :
                snapshot.lines()) {

            String uci =
                    normalizeUci(
                            line.bestMove()
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
                                    line.centipawns(),
                                    line.mateIn()
                            )
                    )
            );

            rank++;
        }


        return List.copyOf(
                result
        );
    }


    // =========================================================
    // Move-set verification
    // =========================================================

    private static void verifyCompleteMoveSet(
            List<LocalCandidate> staticRanking,
            List<ReplyCandidate> replyRanking,
            List<StockfishCandidate> stockfishRanking,
            String positionName
    ) {

        Set<String> staticMoves =
                new HashSet<>();

        for (LocalCandidate candidate :
                staticRanking) {

            staticMoves.add(
                    candidate.uci()
            );
        }


        Set<String> replyMoves =
                new HashSet<>();

        for (ReplyCandidate candidate :
                replyRanking) {

            replyMoves.add(
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
                staticMoves.size()
                        == staticRanking.size(),
                "Duplicate move in static ranking for "
                        + positionName
        );

        require(
                replyMoves.size()
                        == replyRanking.size(),
                "Duplicate move in reply-aware ranking for "
                        + positionName
        );

        require(
                stockfishMoves.size()
                        == stockfishRanking.size(),
                "Duplicate move in Stockfish ranking for "
                        + positionName
        );

        require(
                staticMoves.equals(
                        replyMoves
                ),
                "Static and reply-aware move sets differ for "
                        + positionName
        );

        require(
                staticMoves.equals(
                        stockfishMoves
                ),
                "Local and Stockfish move sets differ for "
                        + positionName
        );
    }


    // =========================================================
    // Ranking metrics
    // =========================================================

    private static RankingMetrics rankingMetrics(
            List<? extends RankedMove> localRanking,
            List<StockfishCandidate> stockfishRanking
    ) {

        Map<String, Integer> localRanks =
                new HashMap<>();

        Map<String, Integer> stockfishRanks =
                new HashMap<>();


        for (int index = 0;
             index < localRanking.size();
             index++) {

            localRanks.put(
                    localRanking.get(index)
                            .uci(),
                    index + 1
            );
        }


        for (StockfishCandidate candidate :
                stockfishRanking) {

            stockfishRanks.put(
                    candidate.uci(),
                    candidate.rank()
            );
        }


        require(
                localRanks.keySet()
                        .equals(
                                stockfishRanks.keySet()
                        ),
                "Cannot compute ranking metrics for different move sets."
        );


        int count =
                localRanks.size();

        long squaredDifferenceSum =
                0L;

        long absoluteDifferenceSum =
                0L;


        for (String move :
                localRanks.keySet()) {

            int difference =
                    localRanks.get(
                            move
                    )
                            - stockfishRanks.get(
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
            List<? extends RankedMove> localRanking,
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
    // Rank / candidate lookup
    // =========================================================

    private static int localRankOf(
            List<LocalCandidate> ranking,
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


    private static int replyRankOf(
            List<ReplyCandidate> ranking,
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

        StockfishCandidate candidate =
                stockfishCandidate(
                        ranking,
                        uci
                );


        return candidate == null
                ? -1
                : candidate.rank();
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


    private static LocalCandidate localCandidate(
            List<LocalCandidate> ranking,
            String uci
    ) {

        String normalized =
                normalizeUci(
                        uci
                );


        for (LocalCandidate candidate :
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


    private static ReplyCandidate replyCandidate(
            List<ReplyCandidate> ranking,
            String uci
    ) {

        String normalized =
                normalizeUci(
                        uci
                );


        for (ReplyCandidate candidate :
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
    // Output
    // =========================================================

    private static void printComparisonRanking(
            List<StockfishCandidate> stockfishRanking,
            List<LocalCandidate> staticRanking,
            List<ReplyCandidate> replyRanking,
            String staticBestMove,
            String replyBestMove
    ) {

        System.out.println();
        System.out.println(
                "STATIC VS ONE-REPLY VS STOCKFISH"
        );
        System.out.println(
                "------------------------------------------------------------------------------------------------------------"
        );
        System.out.printf(
                "%-4s %-8s %-10s %-11s %-8s %-10s %-8s %-10s %-10s %-10s%n",
                "SF#",
                "Move",
                "SAN",
                "SF score",
                "Static#",
                "Static",
                "Reply#",
                "Reply",
                "Worst",
                "Markers"
        );
        System.out.println(
                "------------------------------------------------------------------------------------------------------------"
        );


        int shown =
                Math.min(
                        DISPLAY_TOP_COUNT,
                        stockfishRanking.size()
                );


        Set<Integer> rows =
                new HashSet<>();

        for (int index = 0;
             index < shown;
             index++) {

            rows.add(
                    index
            );
        }


        int staticBestSfRank =
                stockfishRankOf(
                        stockfishRanking,
                        staticBestMove
                );

        int replyBestSfRank =
                stockfishRankOf(
                        stockfishRanking,
                        replyBestMove
                );


        if (staticBestSfRank > 0) {

            rows.add(
                    staticBestSfRank - 1
            );
        }

        if (replyBestSfRank > 0) {

            rows.add(
                    replyBestSfRank - 1
            );
        }


        List<Integer> sortedRows =
                new ArrayList<>(
                        rows
                );

        sortedRows.sort(
                Integer::compareTo
        );


        for (int row :
                sortedRows) {

            StockfishCandidate sf =
                    stockfishRanking.get(
                            row
                    );

            LocalCandidate local =
                    localCandidate(
                            staticRanking,
                            sf.uci()
                    );

            ReplyCandidate reply =
                    replyCandidate(
                            replyRanking,
                            sf.uci()
                    );


            require(
                    local != null
                            && reply != null,
                    "Could not map ranking row for move "
                            + sf.uci()
            );


            System.out.printf(
                    "%-4d %-8s %-10s %-11s %-8d %-10s %-8d %-10s %-10s %-10s%n",
                    sf.rank(),
                    sf.uci(),
                    sf.san(),
                    sf.score()
                            .display(),
                    localRankOf(
                            staticRanking,
                            sf.uci()
                    ),
                    formatLocalScore(
                            local.rootScore()
                    ),
                    replyRankOf(
                            replyRanking,
                            sf.uci()
                    ),
                    formatReplyScore(
                            reply.rootScore()
                    ),
                    reply.worstReplyUci(),
                    markers(
                            sf.rank(),
                            sf.uci(),
                            staticBestMove,
                            replyBestMove
                    )
            );
        }
    }


    private static String markers(
            int stockfishRank,
            String move,
            String staticBestMove,
            String replyBestMove
    ) {

        StringBuilder result =
                new StringBuilder();


        if (stockfishRank == 1) {

            result.append(
                    "SF "
            );
        }

        if (move.equals(
                staticBestMove
        )) {

            result.append(
                    "S "
            );
        }

        if (move.equals(
                replyBestMove
        )) {

            result.append(
                    "R "
            );
        }


        String value =
                result.toString()
                        .trim();


        return value.isEmpty()
                ? "-"
                : value;
    }


    // =========================================================
    // Diagnosis
    // =========================================================

    private static String classify(
            LocalCandidate staticBest,
            ReplyCandidate replyBest,
            StockfishCandidate stockfishBest,
            int staticBestStockfishRank,
            int replyBestStockfishRank,
            int stockfishBestStaticRank,
            int stockfishBestReplyRank,
            int staticComparisonLoss,
            int replyComparisonLoss
    ) {

        boolean staticExact =
                staticBest.uci()
                        .equals(
                                stockfishBest.uci()
                        );

        boolean replyExact =
                replyBest.uci()
                        .equals(
                                stockfishBest.uci()
                        );


        if (staticExact) {

            if (replyExact) {

                return "CONTROL — static and one-reply rankings preserve Stockfish #1.";
            }

            return "FEATURE / WEIGHT SUSPECT — static was already right, but one-reply minimax moved away from Stockfish #1.";
        }


        if (replyExact) {

            return "HORIZON CONFIRMED — one opponent reply repairs the static top-move miss exactly.";
        }


        int lossImprovement =
                staticComparisonLoss
                        - replyComparisonLoss;

        int sfRankImprovement =
                staticBestStockfishRank
                        - replyBestStockfishRank;

        int targetRankImprovement =
                stockfishBestStaticRank
                        - stockfishBestReplyRank;


        if (lossImprovement
                >= MATERIAL_IMPROVEMENT_CP
                || sfRankImprovement
                >= 5
                || targetRankImprovement
                >= 5) {

            return "HORIZON CONFIRMED — one-reply minimax materially improves the Stockfish loss/ranking even though #1 is not exact.";
        }


        if (lossImprovement
                >= MODEST_IMPROVEMENT_CP
                || sfRankImprovement
                > 0
                || targetRankImprovement
                > 0) {

            return "HORIZON HELPED — immediate reply awareness improves calibration, but a residual evaluation mismatch remains.";
        }


        return "FEATURE / WEIGHT SUSPECT — one-reply minimax does not materially repair the static disagreement.";
    }


    // =========================================================
    // Stockfish score handling
    // =========================================================

    private static Integer literalCentipawnLoss(
            StockfishScore best,
            StockfishScore candidate
    ) {

        if (best == null
                || candidate == null
                || best.centipawns()
                == null
                || candidate.centipawns()
                == null) {

            return null;
        }


        return Math.max(
                0,
                best.centipawns()
                        - candidate.centipawns()
        );
    }


    private static int comparisonLoss(
            StockfishScore best,
            StockfishScore candidate
    ) {

        require(
                best != null
                        && candidate != null
                        && best.isKnown()
                        && candidate.isKnown(),
                "Cannot compare unknown Stockfish score."
        );


        return Math.max(
                0,
                best.comparisonValue()
                        - candidate.comparisonValue()
        );
    }


    private static String lossDisplay(
            StockfishScore best,
            StockfishScore candidate,
            boolean exactMove
    ) {

        if (exactMove) {

            return "0 cp";
        }


        Integer cpLoss =
                literalCentipawnLoss(
                        best,
                        candidate
                );

        if (cpLoss != null) {

            return cpLoss
                    + " cp";
        }


        if (best != null
                && best.isWinningMate()) {

            if (candidate != null
                    && candidate.isWinningMate()) {

                return "winning mate preserved";
            }

            return "winning mate lost";
        }


        return "n/a";
    }


    // =========================================================
    // Overall summary
    // =========================================================

    private static void printOverallSummary(
            DiagnosticTotals totals
    ) {

        System.out.println(
                "============================================================"
        );
        System.out.println(
                "M82 OVERALL HORIZON SUMMARY"
        );
        System.out.println(
                "============================================================"
        );
        System.out.println();

        System.out.printf(
                "Positions diagnosed:                  %d%n",
                totals.positions
        );
        System.out.printf(
                "Total legal root moves:               %d%n",
                totals.totalLegalMoves
        );
        System.out.printf(
                "Static #1 matches Stockfish:          %d / %d%n",
                totals.staticTop1Matches,
                totals.positions
        );
        System.out.printf(
                "One-reply #1 matches Stockfish:       %d / %d%n",
                totals.replyTop1Matches,
                totals.positions
        );
        System.out.printf(
                "Positions with lower SF loss:         %d / %d%n",
                totals.positionsImproved,
                totals.positions
        );
        System.out.printf(
                "Average static Spearman:              %.4f%n",
                average(
                        totals.totalStaticSpearman,
                        totals.positions
                )
        );
        System.out.printf(
                "Average one-reply Spearman:           %.4f%n",
                average(
                        totals.totalReplySpearman,
                        totals.positions
                )
        );
        System.out.printf(
                "Average static mean rank error:       %.2f%n",
                average(
                        totals.totalStaticMeanRankError,
                        totals.positions
                )
        );
        System.out.printf(
                "Average one-reply mean rank error:    %.2f%n",
                average(
                        totals.totalReplyMeanRankError,
                        totals.positions
                )
        );
        System.out.printf(
                "Average static top-5 overlap:         %.2f / 5%n",
                average(
                        totals.totalStaticTop5Overlap,
                        totals.positions
                )
        );
        System.out.printf(
                "Average one-reply top-5 overlap:      %.2f / 5%n",
                average(
                        totals.totalReplyTop5Overlap,
                        totals.positions
                )
        );


        if (totals.staticCpPositions > 0) {

            System.out.printf(
                    "Average static-best CP loss:         %.2f%n",
                    (double) totals.totalStaticCpLoss
                            / totals.staticCpPositions
            );
            System.out.printf(
                    "Maximum static-best CP loss:         %d%n",
                    totals.maximumStaticCpLoss
            );
        }


        if (totals.replyCpPositions > 0) {

            System.out.printf(
                    "Average one-reply-best CP loss:      %.2f%n",
                    (double) totals.totalReplyCpLoss
                            / totals.replyCpPositions
            );
            System.out.printf(
                    "Maximum one-reply-best CP loss:      %d%n",
                    totals.maximumReplyCpLoss
            );
        }


        System.out.println();
        System.out.printf(
                "HORIZON CONFIRMED classifications:   %d%n",
                totals.horizonConfirmed
        );
        System.out.printf(
                "HORIZON HELPED classifications:      %d%n",
                totals.horizonHelped
        );
        System.out.printf(
                "FEATURE / WEIGHT SUSPECT:            %d%n",
                totals.featureWeightSuspect
        );
        System.out.printf(
                "CONTROL classifications:             %d%n",
                totals.controls
        );

        System.out.println();
        System.out.println(
                "Interpretation:"
        );
        System.out.println(
                "  HORIZON CONFIRMED -> M83 should add bounded reply-aware guidance."
        );
        System.out.println(
                "  HORIZON HELPED    -> reply awareness helps, then inspect residual evaluator features."
        );
        System.out.println(
                "  FEATURE / WEIGHT  -> inspect PositionEvaluator terms before adding tactical depth."
        );
    }


    private static double average(
            double total,
            int count
    ) {

        return count <= 0
                ? 0.0
                : total
                / count;
    }


    // =========================================================
    // Formatting / move helpers
    // =========================================================

    private static String formatLocalScore(
            int score
    ) {

        return String.format(
                "%+.2f",
                score
                        / 100.0
        );
    }


    private static String formatReplyScore(
            int score
    ) {

        if (score
                >= TERMINAL_WIN_SCORE) {

            return "WIN";
        }

        if (score
                <= TERMINAL_LOSS_SCORE) {

            return "LOSS";
        }


        return formatLocalScore(
                score
        );
    }


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


        if (move.promotion()
                != null) {

            result +=
                    promotionCharacter(
                            move.promotion()
                    );
        }


        return result;
    }


    private static char promotionCharacter(
            PieceType type
    ) {

        return switch (type) {
            case QUEEN -> 'q';
            case ROOK -> 'r';
            case BISHOP -> 'b';
            case KNIGHT -> 'n';
            default -> Character.toLowerCase(
                    type.name()
                            .charAt(
                                    0
                            )
            );
        };
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
    // Shared ranking interface / records
    // =========================================================

    private interface RankedMove {

        String uci();
    }


    private record DiagnosticPosition(
            String name,
            String description,
            String fen
    ) {
    }


    private record LocalCandidate(
            Move move,
            String uci,
            String san,
            int rootScore
    ) implements RankedMove {
    }


    private record ReplyCandidate(
            Move move,
            String uci,
            String san,
            int rootScore,
            String worstReplyUci,
            String worstReplySan,
            int replyCount,
            boolean terminalAfterRootMove
    ) implements RankedMove {
    }


    private record StockfishCandidate(
            int rank,
            String uci,
            String san,
            StockfishScore score
    ) {
    }


    private record RankingMetrics(
            int top3Overlap,
            int top5Overlap,
            double meanAbsoluteRankError,
            double spearmanRho
    ) {
    }


    private record StockfishScore(
            Integer centipawns,
            Integer mateIn
    ) {

        private boolean isKnown() {

            return centipawns != null
                    || mateIn != null;
        }


        private boolean isWinningMate() {

            return mateIn != null
                    && mateIn > 0;
        }


        private int comparisonValue() {

            if (mateIn != null) {

                if (mateIn > 0) {

                    return 100_000
                            - Math.abs(
                            mateIn
                    );
                }

                if (mateIn < 0) {

                    return -100_000
                            + Math.abs(
                            mateIn
                    );
                }

                return 100_000;
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


    // =========================================================
    // Mutable totals
    // =========================================================

    private static final class DiagnosticTotals {

        private int positions;
        private int totalLegalMoves;

        private int staticTop1Matches;
        private int replyTop1Matches;
        private int positionsImproved;

        private double totalStaticSpearman;
        private double totalReplySpearman;
        private double totalStaticMeanRankError;
        private double totalReplyMeanRankError;
        private double totalStaticTop5Overlap;
        private double totalReplyTop5Overlap;

        private int staticCpPositions;
        private long totalStaticCpLoss;
        private int maximumStaticCpLoss;

        private int replyCpPositions;
        private long totalReplyCpLoss;
        private int maximumReplyCpLoss;

        private int horizonConfirmed;
        private int horizonHelped;
        private int featureWeightSuspect;
        private int controls;
    }
}

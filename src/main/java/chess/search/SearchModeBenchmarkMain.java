package main.java.chess.search;

import main.java.chess.engine.ChessEngine;
import main.java.chess.model.Color;
import main.java.chess.model.FenCodec;
import main.java.chess.model.Move;
import main.java.chess.model.Position;
import main.java.chess.rules.MoveGenerator;

import java.util.ArrayList;
import java.util.List;


/**
 * M78.1 controlled Dovetail-vs-Hybrid benchmark.
 *
 * M78.1 changes NO search behavior.
 *
 * It compares:
 *
 *     DOVETAIL
 *         M76 strength-aware persistent walkers only
 *
 *     HYBRID
 *         those same M76 walkers
 *             +
 *         M77 strength-aware fair node coverage
 *
 * Both engines receive:
 *
 *     - the exact same FEN,
 *     - zero fixed-depth pre-expansion,
 *     - the exact same total search-work budget.
 *
 * M78.1 also:
 *
 *     - performs an unmeasured JVM/JIT warm-up,
 *     - alternates which mode runs first,
 *     - avoids intentionally low-material benchmark positions
 *       that can pull the exact four-piece tablebase system
 *       into what is supposed to be a search-policy benchmark.
 *
 * This remains a measurement milestone, not a tuning milestone.
 */
public final class SearchModeBenchmarkMain {

    public static final String BUILD_ID =
            "M78.1-DOVETAIL-HYBRID-CONTROLLED-BENCHMARK-V2";


    private static final String START_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";


    // =========================================================
    // Warm-up
    // =========================================================

    /*
     * These work units are deliberately NOT included in benchmark results.
     *
     * The goal is simply to load commonly used classes and give HotSpot an
     * opportunity to compile frequently executed engine paths before timing.
     */
    private static final int WARM_UP_ROUNDS =
            2;

    private static final int WARM_UP_WORK_UNITS =
            250;


    // =========================================================
    // Work budgets
    // =========================================================

    /*
     * Normal run:
     *
     *     500
     *     1,000
     *     2,500
     *     5,000
     *
     * Full run:
     *
     *     add "full" as the command-line argument
     *
     *     500
     *     1,000
     *     2,500
     *     5,000
     *     10,000
     */
    private static final int[] STANDARD_BUDGETS = {
            500,
            1_000,
            2_500,
            5_000
    };

    private static final int[] FULL_BUDGETS = {
            500,
            1_000,
            2_500,
            5_000,
            10_000
    };


    // =========================================================
    // Benchmark positions
    // =========================================================

    private static final List<BenchmarkPosition> POSITIONS =
            List.of(

                    new BenchmarkPosition(
                            "Starting position",
                            "General opening breadth",
                            START_FEN
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

                    /*
                     * Full-material forcing fixture.
                     *
                     * White has Qxf7# available immediately.
                     *
                     * The previous low-material queen attack fixture allowed
                     * deep walkers to simplify rapidly into exact four-piece
                     * material, causing the KPKP tablebase asset to be loaded
                     * during a benchmark that is supposed to measure the
                     * search policies themselves.
                     */
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


    private SearchModeBenchmarkMain() {
    }


    // =========================================================
    // Main
    // =========================================================

    public static void main(
            String[] args
    ) {

        boolean full =
                args.length > 0
                        &&
                        "full".equalsIgnoreCase(
                                args[0]
                        );

        int[] budgets =
                full
                        ? FULL_BUDGETS
                        : STANDARD_BUDGETS;


        printHeader(
                full,
                budgets
        );

        verifyInstalledBuilds();


        System.out.println();

        warmUpJvm();

        System.out.println();


        int positionNumber =
                1;

        for (BenchmarkPosition benchmarkPosition :
                POSITIONS) {

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


            /*
             * Alternate execution order.
             *
             * Odd benchmark positions:
             *
             *     Dovetail -> Hybrid
             *
             * Even benchmark positions:
             *
             *     Hybrid -> Dovetail
             *
             * This does not make single-run wall-clock measurement perfect,
             * but prevents one mode from systematically receiving the warmer
             * execution slot throughout the entire suite.
             */
            boolean dovetailFirst =
                    positionNumber % 2
                            == 1;


            ChessEngine.SearchMode firstMode =
                    dovetailFirst
                            ? ChessEngine.SearchMode.DOVETAIL
                            : ChessEngine.SearchMode.HYBRID;

            ChessEngine.SearchMode secondMode =
                    dovetailFirst
                            ? ChessEngine.SearchMode.HYBRID
                            : ChessEngine.SearchMode.DOVETAIL;


            System.out.println(
                    "Measurement order: "
                            + firstMode
                            + " -> "
                            + secondMode
            );

            System.out.println();


            ModeRun firstRun =
                    benchmarkMode(
                            benchmarkPosition,
                            firstMode,
                            budgets
                    );

            ModeRun secondRun =
                    benchmarkMode(
                            benchmarkPosition,
                            secondMode,
                            budgets
                    );


            ModeRun dovetail =
                    firstRun.mode()
                            == ChessEngine.SearchMode.DOVETAIL
                            ? firstRun
                            : secondRun;

            ModeRun hybrid =
                    firstRun.mode()
                            == ChessEngine.SearchMode.HYBRID
                            ? firstRun
                            : secondRun;


            verifyModeArchitecture(
                    dovetail,
                    hybrid
            );

            printComparison(
                    benchmarkPosition,
                    dovetail,
                    hybrid
            );

            printFinalObservation(
                    dovetail,
                    hybrid
            );


            System.out.println();

            positionNumber++;
        }


        System.out.println(
                "============================================================"
        );

        System.out.println(
                "M78.1 CONTROLLED BENCHMARK COMPLETE"
        );

        System.out.println(
                "============================================================"
        );

        System.out.println();

        System.out.println(
                "No M76/M77 search tuning was performed."
        );

        System.out.println(
                "Wall-clock timing is informational; structural search results"
        );

        System.out.println(
                "are the primary M78.1 comparison."
        );

        System.out.println();

        System.out.println(
                "Use these measurements to decide M79."
        );
    }


    // =========================================================
    // Header
    // =========================================================

    private static void printHeader(
            boolean full,
            int[] budgets
    ) {

        System.out.println(
                "Dovetail / Hybrid controlled search benchmark"
        );

        System.out.println(
                "============================================================"
        );

        System.out.println(
                "Benchmark BUILD_ID: "
                        + BUILD_ID
        );

        System.out.println(
                "Walker BUILD_ID:    "
                        + DovetailWalker.BUILD_ID
        );

        System.out.println(
                "Coverage BUILD_ID:  "
                        + ExplorationScheduler.COVERAGE_BUILD_ID
        );

        System.out.println(
                "Engine mode BUILD:  "
                        + ChessEngine.MODE_BUILD_ID
        );

        System.out.println(
                "Run type:           "
                        + (
                        full
                                ? "FULL"
                                : "STANDARD"
                )
        );

        System.out.print(
                "Work checkpoints:   "
        );


        for (int index = 0;
             index < budgets.length;
             index++) {

            if (index > 0) {

                System.out.print(
                        ", "
                );
            }


            System.out.printf(
                    "%,d",
                    budgets[index]
            );
        }


        System.out.println();

        System.out.println(
                "Initial depth:       0"
        );

        System.out.println(
                "Fixed-depth head start disabled for both modes."
        );

        System.out.println(
                "JVM warm-up:         enabled"
        );

        System.out.println(
                "Mode order:          alternates by benchmark position"
        );

        System.out.println(
                "Timing status:       informational"
        );
    }


    // =========================================================
    // Installed build verification
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
                "Dovetail/Hybrid engine-mode separation build is missing."
        );


        System.out.println(
                "M76 walker build preserved PASSED"
        );

        System.out.println(
                "M77 Hybrid coverage build preserved PASSED"
        );

        System.out.println(
                "Dovetail / Hybrid routing build preserved PASSED"
        );
    }


    // =========================================================
    // JVM / JIT warm-up
    // =========================================================

    private static void warmUpJvm() {

        System.out.println(
                "JVM / JIT warm-up"
        );

        System.out.println(
                "-----------------"
        );


        Position position =
                FenCodec.parse(
                        START_FEN
                );


        for (int round = 0;
             round < WARM_UP_ROUNDS;
             round++) {

            warmUpMode(
                    position,
                    ChessEngine.SearchMode.DOVETAIL
            );

            warmUpMode(
                    position,
                    ChessEngine.SearchMode.HYBRID
            );
        }


        System.out.printf(
                "warm-up rounds: %d%n",
                WARM_UP_ROUNDS
        );

        System.out.printf(
                "work per mode per round: %,d%n",
                WARM_UP_WORK_UNITS
        );

        System.out.println(
                "warm-up measurements discarded PASSED"
        );
    }


    private static void warmUpMode(
            Position position,
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
                position
        );

        engine.advanceExplorationWork(
                WARM_UP_WORK_UNITS
        );

        engine.createAnalysisSnapshot();


        ExplorationScheduler scheduler =
                engine.getExplorationScheduler();


        require(
                scheduler != null,
                "Warm-up failed to create "
                        + mode
                        + " scheduler."
        );
    }


    // =========================================================
    // One complete mode run
    // =========================================================

    private static ModeRun benchmarkMode(
            BenchmarkPosition benchmarkPosition,
            ChessEngine.SearchMode mode,
            int[] budgets
    ) {

        Position position =
                FenCodec.parse(
                        benchmarkPosition.fen()
                );


        /*
         * Search depth ZERO is intentional.
         *
         * ChessEngine.analyze(...) normally performs a fixed-depth expansion
         * before creating the persistent scheduler.
         *
         * M78.1 wants to measure ONLY the persistent search policies.
         */
        ChessEngine engine =
                new ChessEngine(
                        0
                );

        engine.setSearchMode(
                mode
        );


        long initializationStart =
                System.nanoTime();

        engine.analyze(
                position
        );

        long initializationNanos =
                System.nanoTime()
                        - initializationStart;


        require(
                engine.getSearchMode()
                        == mode,
                "Engine did not retain requested mode "
                        + mode
        );


        ExplorationScheduler scheduler =
                engine.getExplorationScheduler();


        require(
                scheduler != null,
                mode
                        + " created no scheduler."
        );


        ExplorationScheduler.Mode expectedSchedulerMode =
                mode
                        == ChessEngine.SearchMode.DOVETAIL
                        ? ExplorationScheduler.Mode.DOVETAIL
                        : ExplorationScheduler.Mode.HYBRID;


        require(
                scheduler.getMode()
                        == expectedSchedulerMode,
                mode
                        + " routed to wrong scheduler policy."
        );


        List<Checkpoint> checkpoints =
                new ArrayList<>();


        int previousBudget =
                0;

        long cumulativeSearchNanos =
                0L;

        long cumulativeSnapshotNanos =
                0L;

        String previousBestMove =
                null;

        int bestMoveChanges =
                0;


        for (int targetBudget :
                budgets) {

            int additionalWork =
                    targetBudget
                            - previousBudget;


            require(
                    additionalWork > 0,
                    "Benchmark budgets must be strictly increasing."
            );


            // -------------------------------------------------
            // Search work
            // -------------------------------------------------

            long searchStart =
                    System.nanoTime();

            engine.advanceExplorationWork(
                    additionalWork
            );

            cumulativeSearchNanos +=
                    System.nanoTime()
                            - searchStart;


            // -------------------------------------------------
            // Coherent snapshot
            // -------------------------------------------------

            /*
             * Search-value and proven-outcome propagation is performed here.
             *
             * Both modes receive the exact same snapshot schedule.
             */
            long snapshotStart =
                    System.nanoTime();

            engine.createAnalysisSnapshot();

            cumulativeSnapshotNanos +=
                    System.nanoTime()
                            - snapshotStart;


            PositionGraph graph =
                    engine.getActiveGraph();

            PositionNode root =
                    engine.getActiveRoot();

            scheduler =
                    engine.getExplorationScheduler();


            require(
                    graph != null,
                    mode
                            + " lost its graph."
            );

            require(
                    root != null,
                    mode
                            + " lost its root."
            );


            // -------------------------------------------------
            // Best root move
            // -------------------------------------------------

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


            if (previousBestMove != null
                    &&
                    !previousBestMove.equals(
                            bestMove
                    )) {

                bestMoveChanges++;
            }


            previousBestMove =
                    bestMove;


            // -------------------------------------------------
            // Root coverage
            // -------------------------------------------------

            int legalRootMoves =
                    new MoveGenerator()
                            .generateLegalMoves(
                                    root.getPosition()
                            )
                            .size();

            int generatedRootMoves =
                    root.getOutgoingEdges()
                            .size();


            // -------------------------------------------------
            // Graph statistics
            // -------------------------------------------------

            GraphStatistics graphStatistics =
                    graphStatistics(
                            graph
                    );


            PositionNode bestChild =
                    bestEdge == null
                            ? null
                            : bestEdge.getTarget();


            long actualWork =
                    scheduler.getWalkerSteps()
                            +
                            scheduler.getCoverageSteps();


            Checkpoint checkpoint =
                    new Checkpoint(
                            targetBudget,
                            actualWork,
                            scheduler.getWalkerSteps(),
                            scheduler.getCoverageSteps(),
                            graph.size(),
                            scheduler.getWalkerCount(),
                            scheduler.getMaximumWalkerDepth(),
                            generatedRootMoves,
                            legalRootMoves,
                            graphStatistics.transpositionNodes(),
                            graphStatistics.extraIncomingLinks(),
                            graphStatistics.solvedNodes(),
                            graphStatistics.provenWinNodes(),
                            root.getSearchValue(),
                            root.getSearchOutcome(),
                            root.getMateDistance(),
                            bestMove,
                            bestChild == null
                                    ? 0
                                    : bestChild.getSearchValue(),
                            bestChild == null
                                    ? SearchOutcome.UNKNOWN
                                    : bestChild.getSearchOutcome(),
                            bestChild == null
                                    ? -1
                                    : bestChild.getMateDistance(),
                            cumulativeSearchNanos,
                            cumulativeSnapshotNanos,
                            bestMoveChanges
                    );


            checkpoints.add(
                    checkpoint
            );


            previousBudget =
                    targetBudget;
        }


        return new ModeRun(
                mode,
                initializationNanos,
                List.copyOf(
                        checkpoints
                )
        );
    }


    // =========================================================
    // Architecture invariants
    // =========================================================

    private static void verifyModeArchitecture(
            ModeRun dovetail,
            ModeRun hybrid
    ) {

        for (Checkpoint checkpoint :
                dovetail.checkpoints()) {

            require(
                    checkpoint.walkerSteps() > 0L,
                    "Dovetail performed no walker work."
            );

            require(
                    checkpoint.coverageSteps() == 0L,
                    "Pure Dovetail leaked Hybrid coverage work."
            );

            require(
                    checkpoint.actualWork()
                            == checkpoint.walkerSteps(),
                    "Dovetail work accounting mismatch."
            );
        }


        for (Checkpoint checkpoint :
                hybrid.checkpoints()) {

            require(
                    checkpoint.walkerSteps() > 0L,
                    "Hybrid performed no walker work."
            );

            require(
                    checkpoint.coverageSteps() > 0L,
                    "Hybrid performed no coverage work."
            );

            require(
                    Math.abs(
                            checkpoint.walkerSteps()
                                    -
                                    checkpoint.coverageSteps()
                    ) <= 1L,
                    "Hybrid walker/coverage split is no longer approximately 50/50."
            );

            require(
                    checkpoint.actualWork()
                            ==
                            checkpoint.walkerSteps()
                                    +
                                    checkpoint.coverageSteps(),
                    "Hybrid work accounting mismatch."
            );
        }
    }


    // =========================================================
    // Graph metrics
    // =========================================================

    private static GraphStatistics graphStatistics(
            PositionGraph graph
    ) {

        int transpositionNodes =
                0;

        long extraIncomingLinks =
                0L;

        int solvedNodes =
                0;

        int provenWinNodes =
                0;


        for (PositionNode node :
                graph.getNodes()) {

            int incoming =
                    node.getIncomingNodes()
                            .size();


            /*
             * One incoming parent is ordinary tree structure.
             *
             * Two or more incoming parents means different explored lines
             * reached the same canonical position.
             */
            if (incoming > 1) {

                transpositionNodes++;

                extraIncomingLinks +=
                        incoming - 1L;
            }


            SearchOutcome outcome =
                    node.getSearchOutcome();


            if (outcome != null
                    &&
                    outcome != SearchOutcome.UNKNOWN) {

                solvedNodes++;
            }


            if (outcome == SearchOutcome.WHITE_WIN
                    ||
                    outcome == SearchOutcome.BLACK_WIN) {

                provenWinNodes++;
            }
        }


        return new GraphStatistics(
                transpositionNodes,
                extraIncomingLinks,
                solvedNodes,
                provenWinNodes
        );
    }


    // =========================================================
    // Best root move
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


    /*
     * Mirrors the engine's mate-aware move preference:
     *
     *     proven win
     *     draw / unresolved
     *     proven loss
     *
     * then search value.
     */
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


        if (leftCategory
                != rightCategory) {

            return Integer.compare(
                    leftCategory,
                    rightCategory
            );
        }


        // -----------------------------------------------------
        // Proven win: mate sooner
        // -----------------------------------------------------

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


        // -----------------------------------------------------
        // Proven loss: survive longer
        // -----------------------------------------------------

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


        // -----------------------------------------------------
        // Otherwise use backed-up positional value
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


        /*
         * Stable tie ordering makes benchmark output easier to compare
         * between separate executions.
         */
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
    // Comparison printing
    // =========================================================

    private static void printComparison(
            BenchmarkPosition benchmarkPosition,
            ModeRun dovetail,
            ModeRun hybrid
    ) {

        System.out.println(
                "CONTROLLED CHECKPOINT COMPARISON"
        );

        System.out.println(
                "---------------------------------------------------------------------------------------------------------------------------------------"
        );

        System.out.printf(
                "%-7s %-9s %-13s %-8s %-7s %-9s %-11s %-8s %-9s %-9s %-10s %-10s %-7s%n",
                "Budget",
                "Mode",
                "Walker/Cov",
                "Nodes",
                "Depth",
                "Root",
                "Trans N/L",
                "Solved",
                "Best",
                "Value",
                "Search ms",
                "Snap ms",
                "Changes"
        );

        System.out.println(
                "---------------------------------------------------------------------------------------------------------------------------------------"
        );


        for (int index = 0;
             index < dovetail.checkpoints()
                     .size();
             index++) {

            Checkpoint dovetailCheckpoint =
                    dovetail.checkpoints()
                            .get(
                                    index
                            );

            Checkpoint hybridCheckpoint =
                    hybrid.checkpoints()
                            .get(
                                    index
                            );


            printCheckpoint(
                    "DOVETAIL",
                    dovetailCheckpoint
            );

            printCheckpoint(
                    "HYBRID",
                    hybridCheckpoint
            );

            System.out.println();
        }


        System.out.println(
                "Position: "
                        + benchmarkPosition.name()
        );

        System.out.printf(
                "Initialization: Dovetail %.3f ms | Hybrid %.3f ms%n",
                nanosToMilliseconds(
                        dovetail.initializationNanos()
                ),
                nanosToMilliseconds(
                        hybrid.initializationNanos()
                )
        );

        System.out.println(
                "Timing note: wall-clock values are informational only."
        );
    }


    private static void printCheckpoint(
            String mode,
            Checkpoint checkpoint
    ) {

        String laneSplit =
                String.format(
                        "%d/%d",
                        checkpoint.walkerSteps(),
                        checkpoint.coverageSteps()
                );


        String rootCoverage =
                checkpoint.generatedRootMoves()
                        + "/"
                        + checkpoint.legalRootMoves();


        String transpositions =
                checkpoint.transpositionNodes()
                        + "/"
                        + checkpoint.extraIncomingLinks();


        System.out.printf(
                "%-7s %-9s %-13s %,8d %,7d %-9s %-11s %,8d %-9s %-9s %,10.2f %,10.2f %,7d%n",
                formatInteger(
                        checkpoint.requestedBudget()
                ),
                mode,
                laneSplit,
                checkpoint.graphNodes(),
                checkpoint.maximumWalkerDepth(),
                rootCoverage,
                transpositions,
                checkpoint.solvedNodes(),
                checkpoint.bestMove(),
                formatEvaluation(
                        checkpoint.bestChildSearchValue()
                ),
                nanosToMilliseconds(
                        checkpoint.searchNanos()
                ),
                nanosToMilliseconds(
                        checkpoint.snapshotNanos()
                ),
                checkpoint.bestMoveChanges()
        );
    }


    // =========================================================
    // Final observations
    // =========================================================

    private static void printFinalObservation(
            ModeRun dovetail,
            ModeRun hybrid
    ) {

        Checkpoint d =
                dovetail.checkpoints()
                        .get(
                                dovetail.checkpoints()
                                        .size()
                                        - 1
                        );

        Checkpoint h =
                hybrid.checkpoints()
                        .get(
                                hybrid.checkpoints()
                                        .size()
                                        - 1
                        );


        System.out.println();

        System.out.println(
                "FINAL CHECKPOINT OBSERVATION"
        );

        System.out.println(
                "----------------------------"
        );


        System.out.printf(
                "Graph nodes:       Dovetail %,d | Hybrid %,d | %s%n",
                d.graphNodes(),
                h.graphNodes(),
                comparisonLabel(
                        d.graphNodes(),
                        h.graphNodes(),
                        "Dovetail",
                        "Hybrid"
                )
        );

        System.out.printf(
                "Walker depth:      Dovetail %,d | Hybrid %,d | %s%n",
                d.maximumWalkerDepth(),
                h.maximumWalkerDepth(),
                comparisonLabel(
                        d.maximumWalkerDepth(),
                        h.maximumWalkerDepth(),
                        "Dovetail",
                        "Hybrid"
                )
        );

        System.out.printf(
                "Transpositions:    Dovetail %,d | Hybrid %,d | %s%n",
                d.transpositionNodes(),
                h.transpositionNodes(),
                comparisonLabel(
                        d.transpositionNodes(),
                        h.transpositionNodes(),
                        "Dovetail",
                        "Hybrid"
                )
        );

        System.out.printf(
                "Extra trans links: Dovetail %,d | Hybrid %,d | %s%n",
                d.extraIncomingLinks(),
                h.extraIncomingLinks(),
                comparisonLabel(
                        d.extraIncomingLinks(),
                        h.extraIncomingLinks(),
                        "Dovetail",
                        "Hybrid"
                )
        );

        System.out.printf(
                "Solved nodes:      Dovetail %,d | Hybrid %,d | %s%n",
                d.solvedNodes(),
                h.solvedNodes(),
                comparisonLabel(
                        d.solvedNodes(),
                        h.solvedNodes(),
                        "Dovetail",
                        "Hybrid"
                )
        );

        System.out.printf(
                "Proven wins:       Dovetail %,d | Hybrid %,d | %s%n",
                d.provenWinNodes(),
                h.provenWinNodes(),
                comparisonLabel(
                        d.provenWinNodes(),
                        h.provenWinNodes(),
                        "Dovetail",
                        "Hybrid"
                )
        );

        System.out.printf(
                "Best-move changes: Dovetail %,d | Hybrid %,d | %s%n",
                d.bestMoveChanges(),
                h.bestMoveChanges(),
                lowerComparisonLabel(
                        d.bestMoveChanges(),
                        h.bestMoveChanges(),
                        "Dovetail",
                        "Hybrid"
                )
        );

        System.out.printf(
                "Final best move:   Dovetail %-8s | Hybrid %-8s%n",
                d.bestMove(),
                h.bestMove()
        );

        System.out.printf(
                "Final best value:  Dovetail %-8s | Hybrid %-8s%n",
                formatEvaluation(
                        d.bestChildSearchValue()
                ),
                formatEvaluation(
                        h.bestChildSearchValue()
                )
        );

        System.out.printf(
                "Root value:        Dovetail %-8s | Hybrid %-8s%n",
                formatEvaluation(
                        d.rootSearchValue()
                ),
                formatEvaluation(
                        h.rootSearchValue()
                )
        );

        System.out.printf(
                "Root outcome:      Dovetail %-14s | Hybrid %-14s%n",
                formatOutcome(
                        d.rootOutcome(),
                        d.rootMateDistance()
                ),
                formatOutcome(
                        h.rootOutcome(),
                        h.rootMateDistance()
                )
        );


        /*
         * The expected structural distinction is informative rather
         * than asserted as a benchmark "winner":
         *
         * Dovetail normally has more walker depth because every unit
         * goes to walkers.
         *
         * Hybrid normally sacrifices some walker depth for systematic
         * graph coverage.
         */
        require(
                d.coverageSteps() == 0L,
                "Dovetail gained coverage work during benchmark."
        );

        require(
                h.coverageSteps() > 0L,
                "Hybrid lost its coverage lane during benchmark."
        );
    }


    // =========================================================
    // Formatting
    // =========================================================

    private static String formatInteger(
            long value
    ) {

        return String.format(
                "%,d",
                value
        );
    }


    private static String formatEvaluation(
            int centipawns
    ) {

        return String.format(
                "%+.2f",
                centipawns / 100.0
        );
    }


    private static String formatOutcome(
            SearchOutcome outcome,
            int mateDistance
    ) {

        if (outcome == null
                ||
                outcome == SearchOutcome.UNKNOWN) {

            return "UNKNOWN";
        }


        if (outcome
                == SearchOutcome.DRAW) {

            return "DRAW";
        }


        if (mateDistance < 0) {

            return outcome.name();
        }


        return outcome.name()
                + "("
                + mateDistance
                + ")";
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


    private static double nanosToMilliseconds(
            long nanos
    ) {

        return nanos
                / 1_000_000.0;
    }


    private static String comparisonLabel(
            long left,
            long right,
            String leftName,
            String rightName
    ) {

        if (left > right) {

            return leftName
                    + " higher";
        }


        if (right > left) {

            return rightName
                    + " higher";
        }


        return "equal";
    }


    private static String lowerComparisonLabel(
            long left,
            long right,
            String leftName,
            String rightName
    ) {

        if (left < right) {

            return leftName
                    + " more stable";
        }


        if (right < left) {

            return rightName
                    + " more stable";
        }


        return "equal";
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
            long initializationNanos,
            List<Checkpoint> checkpoints
    ) {
    }


    private record GraphStatistics(
            int transpositionNodes,
            long extraIncomingLinks,
            int solvedNodes,
            int provenWinNodes
    ) {
    }


    private record Checkpoint(
            int requestedBudget,
            long actualWork,
            long walkerSteps,
            long coverageSteps,
            int graphNodes,
            int walkerCount,
            int maximumWalkerDepth,
            int generatedRootMoves,
            int legalRootMoves,
            int transpositionNodes,
            long extraIncomingLinks,
            int solvedNodes,
            int provenWinNodes,
            int rootSearchValue,
            SearchOutcome rootOutcome,
            int rootMateDistance,
            String bestMove,
            int bestChildSearchValue,
            SearchOutcome bestChildOutcome,
            int bestChildMateDistance,
            long searchNanos,
            long snapshotNanos,
            int bestMoveChanges
    ) {
    }
}
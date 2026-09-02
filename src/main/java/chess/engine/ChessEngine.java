package main.java.chess.engine;

import main.java.chess.analysis.PositionAnalysis;
import main.java.chess.analysis.VariationNode;
import main.java.chess.model.Position;
import main.java.chess.rules.MoveGenerator;
import main.java.chess.search.ExplorationScheduler;
import main.java.chess.search.PositionGraph;
import main.java.chess.search.PositionNode;
import main.java.chess.search.SearchEdge;

import java.util.ArrayList;
import java.util.List;


public class ChessEngine {

    public static final String TELEMETRY_BUILD_ID =
            "M70-LIVE-DOVETAIL-TELEMETRY-V1";

    public static final String MODE_BUILD_ID =
            "M71-SEPARATE-DOVETAIL-HYBRID-V1";

    public enum SearchMode {
        DOVETAIL,
        HYBRID
    }

    private final MoveGenerator moveGenerator;

    private int searchDepth;


    // =========================================================
    // Persistent active search
    // =========================================================

    private PositionGraph activeGraph;

    private PositionNode activeRoot;

    private ExplorationScheduler explorationScheduler;

    /*
     * Backward-compatible engine default remains HYBRID so existing non-GUI
     * tests retain M69 behavior. ChessWindow explicitly selects DOVETAIL or
     * HYBRID before starting a GUI analysis session.
     */
    private SearchMode searchMode;


    // =========================================================
    // Lightweight performance instrumentation
    // =========================================================

    /*
     * Report every N graph-generation batches. Timings are accumulated
     * instead of printed every refresh so profiling itself stays cheap.
     */
    private static final int PERFORMANCE_REPORT_INTERVAL_BATCHES =
            20;

    private long performanceBatchCount;
    private long performanceSnapshotCount;
    private long performanceWorkUnits;

    /* Cumulative for the current active analysis; unlike the profiler window,
     * this is never reset by periodic performance reports. */
    private long totalExplorationWorkUnits;

    private long explorationNanos;
    private long outcomePropagationNanos;
    private long searchValuePropagationNanos;
    private long snapshotBuildNanos;

    private long outcomeChanges;
    private long searchValueChanges;
    private long statisticsCacheInvalidations;

    private boolean performanceReportPending;


    // =========================================================
    // Constructors
    // =========================================================

    public ChessEngine() {

        this.moveGenerator =
                new MoveGenerator();


        this.searchDepth =
                3;


        this.activeGraph =
                null;


        this.activeRoot =
                null;


        this.explorationScheduler =
                null;

        this.searchMode =
                SearchMode.HYBRID;


        resetPerformanceWindow();
    }


    public ChessEngine(
            int searchDepth
    ) {

        this();


        setSearchDepth(
                searchDepth
        );
    }


    // =========================================================
    // Initial analysis of a new actual board position
    // =========================================================

    public synchronized PositionAnalysis analyze(
            Position position
    ) {

        if (position == null) {

            throw new IllegalArgumentException(
                    "Position cannot be null."
            );
        }


        startNewAnalysis(
                position
        );


        /*
         * The initial fixed-depth search gives the GUI something
         * useful immediately.
         */
        expandToDepth(
                activeGraph,
                activeRoot,
                searchDepth
        );


        recalculateGraph();


        /*
         * IMPORTANT:
         *
         * Create the persistent dovetail scheduler only AFTER the
         * initial graph has been generated.
         *
         * It therefore begins with the already-generated frontier
         * and remembers its position forever after.
         */
        explorationScheduler =
                createExplorationScheduler();


        return activeGraph.analyzePosition(
                activeRoot
        );
    }


    // =========================================================
    // Reuse an already-known analysis root
    // =========================================================

    /**
     * Analyze {@code position} without discarding the current graph when that
     * position is already present in it.  This is used by GUI navigation such
     * as Reset Position: returning to a previously explored root should not
     * erase discoveries that were already paid for.
     *
     * If the position is not known to the current graph, this deliberately
     * falls back to the normal fresh-analysis path.
     */
    public synchronized PositionAnalysis analyzePreservingGraphIfKnown(
            Position position
    ) {

        if (position == null) {

            throw new IllegalArgumentException(
                    "Position cannot be null."
            );
        }


        if (activeGraph == null
                || activeRoot == null) {

            return analyze(
                    position
            );
        }


        PositionNode knownNode =
                null;

        for (PositionNode node :
                activeGraph.getNodes()) {

            if (node.getKey()
                    .equals(
                            position.createPositionKey()
                    )) {

                knownNode =
                        node;

                break;
            }
        }


        if (knownNode == null) {

            return analyze(
                    position
            );
        }


        /*
         * Usually Reset returns to the graph's existing original root, in
         * which case the scheduler itself can continue untouched.  If a known
         * non-root position is selected as the new analysis root, rebuild only
         * the scheduler frontier from that node; the PositionGraph and every
         * discovered PositionNode remain intact.
         */
        if (knownNode != activeRoot) {

            activeRoot =
                    knownNode;

            explorationScheduler =
                    createExplorationScheduler();

        } else if (explorationScheduler == null) {

            explorationScheduler =
                    createExplorationScheduler();
        }


        clearExplorationFocus();

        recalculateGraph();


        return activeGraph.analyzePosition(
                activeRoot
        );
    }


    // =========================================================
    // Fresh active graph
    // =========================================================

    private void startNewAnalysis(
            Position position
    ) {

        activeGraph =
                new PositionGraph();


        activeRoot =
                activeGraph.getOrCreateNode(
                        position
                );


        explorationScheduler =
                null;

        resetPerformanceWindow();

        totalExplorationWorkUnits =
                0L;
    }


    // =========================================================
    // Fast persistent dovetail work
    // =========================================================

    /*
     * Perform graph-generation work only.
     *
     * This intentionally does NOT:
     *
     * - re-evaluate the entire graph
     * - propagate outcomes
     * - propagate minimax/search values
     * - build PositionAnalysis / VariationNode trees
     *
     * Those expensive operations are deferred until the GUI asks
     * for a snapshot.
     */
    public synchronized void advanceExplorationWork(
            int workUnits
    ) {

        if (workUnits <= 0) {

            return;
        }


        if (activeGraph == null
                ||
                activeRoot == null) {

            return;
        }


        ensureScheduler();


        long startNanos =
                System.nanoTime();


        explorationScheduler.advance(
                workUnits
        );


        explorationNanos +=
                System.nanoTime()
                        - startNanos;

        performanceBatchCount++;

        performanceWorkUnits +=
                workUnits;

        totalExplorationWorkUnits +=
                workUnits;


        if (performanceBatchCount
                >= PERFORMANCE_REPORT_INTERVAL_BATCHES) {

            performanceReportPending =
                    true;
        }
    }


    // =========================================================
    // Endgame proof work
    // =========================================================

    /**
     * Spend a large batch of work on the current graph and immediately
     * propagate the exact search state, without building the expensive
     * public GUI analysis tree.
     *
     * Endgame Study uses this while it is proving whether the generated
     * root is a forced mate.  It deliberately reuses the normal
     * PositionGraph and ExplorationScheduler; there is still only one
     * chess/search implementation.
     */
    public synchronized PositionNode advanceEndgameProofWork(
            int workUnits
    ) {

        if (workUnits <= 0) {

            return activeRoot;
        }


        if (activeGraph == null
                ||
                activeRoot == null) {

            return null;
        }


        ensureScheduler();


        /*
         * In study mode the generated root is the thing we care about.
         * Give its reachable subtree the scheduler's focus bonus.
         */
        explorationScheduler.setFocus(
                activeRoot
        );


        explorationScheduler.advance(
                workUnits
        );


        /*
         * Unlike ordinary GUI exploration, proof mode wants to know
         * as soon as an exact outcome reaches the root.  Propagate now,
         * but do NOT build PositionAnalysis / VariationNode objects.
         */
        recalculateGraph();


        return activeRoot;
    }


    // =========================================================
    // Coherent analysis snapshot
    // =========================================================

    /*
     * Build one expensive public-analysis snapshot after a larger
     * amount of graph work has accumulated.
     */
    public synchronized PositionAnalysis createAnalysisSnapshot() {

        if (activeGraph == null
                ||
                activeRoot == null) {

            return null;
        }


        recalculateGraph();


        long invalidationsBefore =
                activeGraph
                        .getTotalStatisticsCacheInvalidations();


        long snapshotStartNanos =
                System.nanoTime();


        PositionAnalysis snapshot =
                activeGraph.analyzePosition(
                        activeRoot
                );


        snapshotBuildNanos +=
                System.nanoTime()
                        - snapshotStartNanos;

        performanceSnapshotCount++;


        long invalidationsAfter =
                activeGraph
                        .getTotalStatisticsCacheInvalidations();


        statisticsCacheInvalidations +=
                Math.max(
                        0L,
                        invalidationsAfter
                                - invalidationsBefore
                );


        maybePrintPerformanceReport();


        return snapshot;
    }


    // =========================================================
    // Selected-line bias
    // =========================================================

    /*
     * Selection changes PRIORITY only.
     *
     * Global dovetailing continues regardless.
     */
    public synchronized void setExplorationFocus(
            Position position
    ) {

        ensureScheduler();


        /*
         * Pure Dovetail preserves its countable diagonal walker schedule.
         * Selecting a GUI line is therefore view-only in DOVETAIL mode.
         * HYBRID retains the selected-subtree bonus in its coverage lane.
         */
        if (searchMode == SearchMode.DOVETAIL) {

            explorationScheduler.clearFocus();
            return;
        }


        if (position == null) {

            explorationScheduler.clearFocus();

            return;
        }


        PositionNode focusNode =
                activeGraph.getOrCreateNode(
                        position
                );


        explorationScheduler.setFocus(
                focusNode
        );
    }


    public synchronized void clearExplorationFocus() {

        if (explorationScheduler != null) {

            explorationScheduler.clearFocus();
        }
    }


    // =========================================================
    // Work availability
    // =========================================================

    public synchronized boolean hasMoreExplorationWork() {

        if (explorationScheduler == null) {

            return false;
        }


        return explorationScheduler.hasGlobalWork();
    }


    private void ensureScheduler() {

        if (activeGraph == null
                ||
                activeRoot == null) {

            throw new IllegalStateException(
                    "No active graph exists."
            );
        }


        if (explorationScheduler == null) {

            explorationScheduler =
                    createExplorationScheduler();
        }
    }


    private ExplorationScheduler createExplorationScheduler() {

        ExplorationScheduler.Mode schedulerMode =
                searchMode == SearchMode.DOVETAIL
                        ? ExplorationScheduler.Mode.DOVETAIL
                        : ExplorationScheduler.Mode.HYBRID;


        return new ExplorationScheduler(
                activeGraph,
                moveGenerator,
                activeRoot,
                schedulerMode
        );
    }


    // =========================================================
    // Search mode
    // =========================================================

    public synchronized SearchMode getSearchMode() {

        return searchMode;
    }


    public synchronized void setSearchMode(
            SearchMode searchMode
    ) {

        if (searchMode == null) {

            throw new IllegalArgumentException(
                    "Search mode cannot be null."
            );
        }


        if (this.searchMode == searchMode) {

            return;
        }


        this.searchMode =
                searchMode;


        /*
         * Preserve the canonical graph if a caller changes policy in place,
         * but restart scheduler state so the new mode begins cleanly.
         * ChessWindow uses an even stronger session boundary and immediately
         * starts a fresh root after changing modes.
         */
        if (activeGraph != null
                &&
                activeRoot != null) {

            explorationScheduler =
                    createExplorationScheduler();
        }
    }


    // =========================================================
    // Initial fixed-depth expansion
    // =========================================================

    private void expandToDepth(
            PositionGraph graph,
            PositionNode root,
            int depth
    ) {

        List<PositionNode> frontier =
                new ArrayList<>();


        frontier.add(
                root
        );


        for (int currentDepth = 0;
             currentDepth < depth;
             currentDepth++) {

            List<PositionNode> nextFrontier =
                    new ArrayList<>();


            for (PositionNode node :
                    frontier) {

                /*
                 * Fully expose the immediate legal moves at each
                 * node in the initial depth-limited search.
                 */
                while (node.hasUnexploredEdges(
                        moveGenerator
                )) {

                    graph.expandOneEdge(
                            node,
                            moveGenerator
                    );
                }


                for (SearchEdge edge :
                        node.getOutgoingEdges()) {

                    PositionNode child =
                            edge.getTarget();


                    if (!nextFrontier.contains(
                            child
                    )) {

                        nextFrontier.add(
                                child
                        );
                    }
                }
            }


            frontier =
                    nextFrontier;


            if (frontier.isEmpty()) {

                break;
            }
        }
    }


    // =========================================================
    // Recalculate graph
    // =========================================================

    private void recalculateGraph() {

        if (activeGraph == null) {

            return;
        }


        /*
         * Static evaluations and terminal base outcomes are seeded
         * when PositionGraph creates each canonical PositionNode.
         */


        /*
         * Mate / outcome propagation is now fully incremental.
         *
         * Edge expansion marks the affected parent outcome-dirty.
         * If that node's SearchOutcome or mateDistance changes,
         * only its canonical incoming parents are awakened.
         */
        long outcomeStartNanos =
                System.nanoTime();


        int changedOutcomes =
                activeGraph.propagateDirtyOutcomes();


        outcomePropagationNanos +=
                System.nanoTime()
                        - outcomeStartNanos;

        outcomeChanges +=
                changedOutcomes;


        /*
         * Numerical search values are also fully incremental.
         *
         * There is intentionally no full-graph verification pass
         * in the normal execution path anymore.
         */
        long valueStartNanos =
                System.nanoTime();


        int changedValues =
                activeGraph.propagateDirtySearchValues();


        searchValuePropagationNanos +=
                System.nanoTime()
                        - valueStartNanos;

        searchValueChanges +=
                changedValues;
    }


    // =========================================================
    // Full synchronous mate propagation
    // Retained as verification / fallback implementation
    // =========================================================

    private void propagateMateOutcomes(
            PositionGraph graph
    ) {

        int maximumPasses =
                Math.max(
                        16,
                        graph.size() + 2
                );


        for (int pass = 0;
             pass < maximumPasses;
             pass++) {

            int changedNodes =
                    graph.propagateMateOutcomesOnePass();


            if (changedNodes == 0) {

                return;
            }
        }
    }


    // =========================================================
    // Full synchronous search-value propagation
    // Retained as a debugging/reference implementation
    // =========================================================

    private void propagateSearchValues(
            PositionGraph graph
    ) {

        int maximumPasses =
                Math.max(
                        16,
                        graph.size() + 2
                );


        for (int pass = 0;
             pass < maximumPasses;
             pass++) {

            int changedNodes =
                    graph.backupAllNodesSynchronously();


            if (changedNodes == 0) {

                return;
            }
        }
    }


    // =========================================================
    // Performance reporting
    // =========================================================

    private void maybePrintPerformanceReport() {

        if (!performanceReportPending) {

            return;
        }


        double explorationAverageMs =
                averageMilliseconds(
                        explorationNanos,
                        performanceBatchCount
                );

        double outcomeAverageMs =
                averageMilliseconds(
                        outcomePropagationNanos,
                        performanceSnapshotCount
                );

        double valueAverageMs =
                averageMilliseconds(
                        searchValuePropagationNanos,
                        performanceSnapshotCount
                );

        double snapshotAverageMs =
                averageMilliseconds(
                        snapshotBuildNanos,
                        performanceSnapshotCount
                );


        System.out.println();
        System.out.println(
                "=== Engine Performance ==="
        );

        System.out.println(
                "Graph nodes: "
                        + (
                        activeGraph == null
                                ? 0
                                : activeGraph.size()
                )
        );

        System.out.println(
                "Batches: "
                        + performanceBatchCount
                        + "   Work units: "
                        + performanceWorkUnits
                        + "   Snapshots: "
                        + performanceSnapshotCount
        );

        System.out.printf(
                "Average per batch:%n"
        );

        System.out.printf(
                "  Exploration:          %.3f ms%n",
                explorationAverageMs
        );

        System.out.printf(
                "Average per snapshot:%n"
        );

        System.out.printf(
                "  Outcome propagation:  %.3f ms%n",
                outcomeAverageMs
        );

        System.out.printf(
                "  Value propagation:    %.3f ms%n",
                valueAverageMs
        );

        System.out.printf(
                "  Snapshot/statistics:  %.3f ms%n",
                snapshotAverageMs
        );

        System.out.println(
                "Incremental changes:"
        );

        System.out.println(
                "  Outcomes changed:     "
                        + outcomeChanges
        );

        System.out.println(
                "  Values changed:       "
                        + searchValueChanges
        );

        System.out.println(
                "  Stats invalidated:    "
                        + statisticsCacheInvalidations
        );

        System.out.println(
                "=========================="
        );


        resetPerformanceWindow();
    }


    private double averageMilliseconds(
            long nanoseconds,
            long count
    ) {

        if (count <= 0L) {

            return 0.0;
        }


        return (
                nanoseconds
                        / 1_000_000.0
        ) / count;
    }


    private void resetPerformanceWindow() {

        performanceBatchCount =
                0L;

        performanceSnapshotCount =
                0L;

        performanceWorkUnits =
                0L;

        explorationNanos =
                0L;

        outcomePropagationNanos =
                0L;

        searchValuePropagationNanos =
                0L;

        snapshotBuildNanos =
                0L;

        outcomeChanges =
                0L;

        searchValueChanges =
                0L;

        statisticsCacheInvalidations =
                0L;

        performanceReportPending =
                false;
    }


    // =========================================================
    // Live search telemetry
    // =========================================================

    public synchronized SearchTelemetry getSearchTelemetry() {

        int graphNodes =
                activeGraph == null
                        ? 0
                        : activeGraph.size();


        boolean searching =
                explorationScheduler != null
                        && explorationScheduler.hasGlobalWork();


        long walkerSteps =
                0L;

        long coverageSteps =
                0L;

        int walkers =
                0;

        int activeWalkers =
                0;

        int maximumWalkerDepth =
                0;

        long walkerPathRevisits =
                0L;

        int globalQueueSize =
                0;

        int focusQueueSize =
                0;


        if (explorationScheduler != null) {

            ExplorationScheduler.DovetailTelemetry dovetail =
                    explorationScheduler.telemetry();


            walkerSteps =
                    dovetail.walkerSteps();

            coverageSteps =
                    dovetail.coverageSteps();

            walkers =
                    dovetail.walkers();

            activeWalkers =
                    dovetail.activeWalkers();

            maximumWalkerDepth =
                    dovetail.maximumWalkerDepth();

            walkerPathRevisits =
                    dovetail.walkerPathRevisits();

            globalQueueSize =
                    explorationScheduler.getGlobalQueueSize();

            focusQueueSize =
                    explorationScheduler.getFocusQueueSize();
        }


        int transpositionNodes =
                activeGraph == null
                        ? 0
                        : activeGraph.getTranspositionNodeCount();

        long transpositionLinks =
                activeGraph == null
                        ? 0L
                        : activeGraph.getTranspositionLinkCount();


        return new SearchTelemetry(
                searching,
                graphNodes,
                totalExplorationWorkUnits,
                walkerSteps,
                coverageSteps,
                walkers,
                activeWalkers,
                maximumWalkerDepth,
                walkerPathRevisits,
                transpositionNodes,
                transpositionLinks,
                globalQueueSize,
                focusQueueSize
        );
    }


    public record SearchTelemetry(
            boolean searching,
            int graphNodes,
            long workUnits,
            long walkerSteps,
            long coverageSteps,
            int walkers,
            int activeWalkers,
            int maximumWalkerDepth,
            long walkerPathRevisits,
            int transpositionNodes,
            long transpositionLinks,
            int globalQueueSize,
            int focusQueueSize
    ) {
    }


    // =========================================================
    // Access
    // =========================================================

    public synchronized boolean hasActiveAnalysis() {

        return activeGraph != null
                &&
                activeRoot != null;
    }


    // =========================================================
    // Manual move graph commit
    // =========================================================

    /*
     * Make a manually played legal move part of the active persistent
     * graph before AnalysisPanel attempts to represent it visually.
     */
    public synchronized boolean ensureManualContinuation(
            Position parentPosition,
            Position childPosition
    ) {

        if (activeGraph == null
                ||
                parentPosition == null
                ||
                childPosition == null) {

            return false;
        }


        return activeGraph.ensureManualContinuation(
                parentPosition,
                childPosition
        );
    }


    // =========================================================
    // Lazy GUI continuation access
    // =========================================================

    /*
     * Read the immediate generated continuations of a position from
     * the persistent active graph.
     *
     * No new search is started here. The synchronized engine boundary
     * also prevents Swing from reading the graph while a background
     * exploration batch is mutating it.
     */
    public synchronized List<VariationNode> getImmediateContinuations(
            Position position
    ) {

        if (activeGraph == null
                ||
                position == null) {

            return List.of();
        }


        return activeGraph.buildImmediateVariationChildren(
                position
        );
    }


    public synchronized PositionGraph getActiveGraph() {

        return activeGraph;
    }


    public synchronized PositionNode getActiveRoot() {

        return activeRoot;
    }


    public synchronized ExplorationScheduler
    getExplorationScheduler() {

        return explorationScheduler;
    }


    // =========================================================
    // Search depth
    // =========================================================

    public int getSearchDepth() {

        return searchDepth;
    }


    public void setSearchDepth(
            int searchDepth
    ) {

        if (searchDepth < 0) {

            throw new IllegalArgumentException(
                    "Search depth cannot be negative."
            );
        }


        this.searchDepth =
                searchDepth;
    }
}

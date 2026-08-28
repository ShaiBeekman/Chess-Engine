package main.java.chess.search;

import main.java.chess.evaluation.PositionEvaluator;

import main.java.chess.model.Color;
import main.java.chess.model.GameState;
import main.java.chess.model.Move;
import main.java.chess.model.Position;
import main.java.chess.model.PositionKey;

import main.java.chess.rules.GameStateEvaluator;
import main.java.chess.rules.MoveGenerator;

import main.java.chess.util.SanMoveFormatter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import main.java.chess.analysis.MoveAnalysis;
import main.java.chess.analysis.VariationNode;

import main.java.chess.analysis.PositionAnalysis;

public class PositionGraph {

    /*
     * Maximum number of already-generated continuation plies
     * exposed to the GUI below each root move.
     *
     * This does not cause search. It only lets the live analysis
     * panel display deeper positions that automatic exploration
     * has already added to the graph.
     */
    /*
     * Keep the public Swing-facing analysis tree intentionally
     * shallow. The underlying PositionGraph may continue growing
     * to arbitrary search depth; this controls only how much of
     * that graph is recursively copied into GUI analysis objects
     * on each live refresh.
     */
    private static final int GUI_VARIATION_DEPTH =
            4;


    /*
     * Canonical graph storage.
     *
     * Equivalent chess positions share
     * the same PositionNode.
     */
    private final Map<PositionKey, PositionNode> nodes;

    private final PositionEvaluator evaluator;

    private final MoveGenerator moveGenerator;

    private final GameStateEvaluator gameStateEvaluator;

    private final SanMoveFormatter sanMoveFormatter;

    /*
     * Permanent SAN cache.
     *
     * A SearchEdge permanently represents one move from one fixed
     * canonical parent position, so its SAN text never changes.
     * IdentityHashMap deliberately keys by the persistent edge object
     * itself and does not depend on SearchEdge.equals/hashCode.
     */
    private final Map<SearchEdge, String> sanCache;

    /*
     * Independent dirty tracking for the two derived systems.
     *
     * Search-value dirtiness and outcome dirtiness must not share
     * one queue because each propagation pass drains its own work.
     */
    private final Queue<PositionNode> searchValueDirtyNodes;
    private final Set<PositionNode> searchValueDirtySet;

    private final Queue<PositionNode> outcomeDirtyNodes;
    private final Set<PositionNode> outcomeDirtySet;

    /*
     * Independent dirtiness for exact GUI subtree-statistics caches.
     * This queue propagates cache invalidation backward through
     * canonical incoming-parent links.
     */
    private final Queue<PositionNode> statisticsDirtyNodes;
    private final Set<PositionNode> statisticsDirtySet;

    /*
     * Diagnostic-only cumulative counter. This does not participate in
     * search logic; ChessEngine reads it to report how much subtree
     * statistics cache invalidation actually occurred.
     */
    private long totalStatisticsCacheInvalidations;

    private static final int SNAPSHOT_PROFILE_INTERVAL = 20;

    private long profileSnapshotCount;
    private long profileMoveSortNanos;
    private long profileSanNanos;
    private long profileSanCacheHits;
    private long profileSanCacheMisses;
    private long profileStatisticsNanos;
    private long profileVariationNanos;
    private long profilePrincipalVariationNanos;
    private long profileOtherNanos;

    private long profileStatisticsCalls;
    private long profileStatisticsCacheHits;
    private long profileStatisticsExactRecomputations;
    private long profileStatisticsVisitedNodes;


    public PositionGraph() {

        this.nodes =
                new HashMap<>();

        this.evaluator =
                new PositionEvaluator();

        this.moveGenerator =
                new MoveGenerator();

        this.gameStateEvaluator =
                new GameStateEvaluator();

        this.sanMoveFormatter =
                new SanMoveFormatter();

        this.sanCache =
                new IdentityHashMap<>();

        this.searchValueDirtyNodes =
                new ArrayDeque<>();

        this.searchValueDirtySet =
                new HashSet<>();

        this.outcomeDirtyNodes =
                new ArrayDeque<>();

        this.outcomeDirtySet =
                new HashSet<>();

        this.statisticsDirtyNodes =
                new ArrayDeque<>();

        this.statisticsDirtySet =
                new HashSet<>();

        this.totalStatisticsCacheInvalidations =
                0L;

        resetSnapshotProfiler();
    }


    // =========================================================
    // NODE CREATION / LOOKUP
    // =========================================================

    public PositionNode getOrCreateNode(
            Position position
    ) {

        PositionKey key =
                position.createPositionKey();

        PositionNode existingNode =
                nodes.get(key);

        if (existingNode != null) {

            return existingNode;
        }


        PositionNode newNode =
                new PositionNode(
                        position
                );


        /*
         * Static evaluation.
         */
        int evaluation =
                evaluator.evaluate(
                        position
                );

        newNode.setEvaluation(
                evaluation
        );

        /*
         * Search value initially equals
         * static evaluation.
         */
        newNode.setSearchValue(
                evaluation
        );


        /*
         * Determine whether this position
         * is already terminal.
         */
        seedSearchOutcome(
                newNode
        );


        nodes.put(
                key,
                newNode
        );

        return newNode;
    }


    // =========================================================
    // TERMINAL OUTCOME SEEDING
    // =========================================================

    private void seedSearchOutcome(
            PositionNode node
    ) {

        Position position =
                node.getPosition();

        GameState state =
                gameStateEvaluator.evaluate(
                        position
                );


        switch (state) {

            // -------------------------------------------------
            // Checkmate
            // -------------------------------------------------

            case CHECKMATE -> {

                /*
                 * The side to move is the
                 * side that has been mated.
                 */

                if (position.getSideToMove()
                        == Color.WHITE) {

                    setNodeOutcome(
                            node,
                            SearchOutcome.BLACK_WIN,
                            0
                    );

                } else {

                    setNodeOutcome(
                            node,
                            SearchOutcome.WHITE_WIN,
                            0
                    );
                }
            }


            // -------------------------------------------------
            // Draws
            // -------------------------------------------------

            case STALEMATE,
                 DRAW_75_MOVE,
                 DRAW_FIVEFOLD_REPETITION -> {

                node.setSearchOutcome(
                        SearchOutcome.DRAW
                );

                node.setMateDistance(
                        -1
                );
            }


            // -------------------------------------------------
            // Non-terminal
            // -------------------------------------------------

            case CHECK,
                 ONGOING -> {

                node.setSearchOutcome(
                        SearchOutcome.UNKNOWN
                );

                node.setMateDistance(
                        -1
                );
            }
        }
    }


    private void setNodeOutcome(
            PositionNode node,
            SearchOutcome outcome,
            int mateDistance
    ) {

        node.setSearchOutcome(
                outcome
        );

        node.setMateDistance(
                mateDistance
        );
    }


    // =========================================================
    // BASIC GRAPH ACCESS
    // =========================================================

    public int size() {

        return nodes.size();
    }


    public Collection<PositionNode> getNodes() {

        return nodes.values();
    }


    // =========================================================
    // CACHED LEGAL MOVES
    // =========================================================

    private List<Move> getLegalMoves(
            PositionNode node
    ) {

        if (node == null) {

            return List.of();
        }


        return node.getOrCacheLegalMoves(
                moveGenerator
        );
    }


    // =========================================================
    // DIRTY NODE TRACKING
    // =========================================================

    private void markSearchValueDirty(
            PositionNode node
    ) {

        if (node == null) {
            return;
        }

        if (!searchValueDirtySet.add(
                node
        )) {
            return;
        }

        searchValueDirtyNodes.add(
                node
        );
    }


    private void markOutcomeDirty(
            PositionNode node
    ) {

        if (node == null) {
            return;
        }

        if (!outcomeDirtySet.add(
                node
        )) {
            return;
        }

        outcomeDirtyNodes.add(
                node
        );
    }


    public boolean hasSearchValueDirtyNodes() {
        return !searchValueDirtyNodes.isEmpty();
    }


    public int getSearchValueDirtyNodeCount() {
        return searchValueDirtyNodes.size();
    }


    public PositionNode pollSearchValueDirtyNode() {

        PositionNode node =
                searchValueDirtyNodes.poll();

        if (node != null) {
            searchValueDirtySet.remove(
                    node
            );
        }

        return node;
    }


    public void clearSearchValueDirtyNodes() {
        searchValueDirtyNodes.clear();
        searchValueDirtySet.clear();
    }


    public boolean hasOutcomeDirtyNodes() {
        return !outcomeDirtyNodes.isEmpty();
    }


    public int getOutcomeDirtyNodeCount() {
        return outcomeDirtyNodes.size();
    }


    public PositionNode pollOutcomeDirtyNode() {

        PositionNode node =
                outcomeDirtyNodes.poll();

        if (node != null) {
            outcomeDirtySet.remove(
                    node
            );
        }

        return node;
    }


    public void clearOutcomeDirtyNodes() {
        outcomeDirtyNodes.clear();
        outcomeDirtySet.clear();
    }


    private void markStatisticsDirty(
            PositionNode node
    ) {

        if (node == null) {

            return;
        }


        if (!statisticsDirtySet.add(
                node
        )) {

            return;
        }


        statisticsDirtyNodes.add(
                node
        );
    }


    public boolean hasStatisticsDirtyNodes() {

        return !statisticsDirtyNodes.isEmpty();
    }


    public int getStatisticsDirtyNodeCount() {

        return statisticsDirtyNodes.size();
    }


    /*
     * Invalidate only the canonical ancestor region affected by a
     * graph-shape or solved/explored-state change.
     */
    public int propagateStatisticsDirtiness() {

        int invalidatedNodes =
                0;

        /*
         * Canonical chess graphs can contain cycles.
         *
         * statisticsDirtySet prevents duplicate queue entries only
         * while a node is currently waiting in the queue. Once a node
         * is polled, that set entry is removed, so without a separate
         * per-wave processed set a cycle could enqueue the same nodes
         * forever:
         *
         *     A -> B -> C
         *     ^         |
         *     |_________|
         *
         * processedThisWave gives this invalidation pass a strict
         * upper bound of one processing step per canonical node.
         */
        Set<PositionNode> processedThisWave =
                new HashSet<>();


        while (!statisticsDirtyNodes.isEmpty()) {

            PositionNode node =
                    statisticsDirtyNodes.poll();


            if (node == null) {

                continue;
            }


            statisticsDirtySet.remove(
                    node
            );


            /*
             * A node reached again through a transposition/cycle has
             * already been invalidated during this wave.
             */
            if (!processedThisWave.add(
                    node
            )) {

                continue;
            }


            boolean wasValid =
                    node.hasValidSubtreeStatistics();


            node.invalidateSubtreeStatistics();


            if (wasValid) {

                invalidatedNodes++;
            }


            for (PositionNode parent :
                    node.getIncomingNodes()) {

                if (!processedThisWave.contains(
                        parent
                )) {

                    markStatisticsDirty(
                            parent
                    );
                }
            }
        }


        totalStatisticsCacheInvalidations +=
                invalidatedNodes;


        return invalidatedNodes;
    }


    public long getTotalStatisticsCacheInvalidations() {

        return totalStatisticsCacheInvalidations;
    }


    public void clearStatisticsDirtyNodes() {

        statisticsDirtyNodes.clear();
        statisticsDirtySet.clear();
    }


    /*
     * Compatibility helpers for the already-live incremental
     * numerical propagation code. These now refer specifically
     * to search-value dirtiness.
     */
    public boolean hasDirtyNodes() {
        return hasSearchValueDirtyNodes();
    }


    public int getDirtyNodeCount() {
        return getSearchValueDirtyNodeCount();
    }


    public PositionNode pollDirtyNode() {
        return pollSearchValueDirtyNode();
    }


    public void clearDirtyNodes() {
        clearSearchValueDirtyNodes();
    }


    // =========================================================
    // INCREMENTAL SEARCH-VALUE PROPAGATION
    // =========================================================

    public int propagateDirtySearchValues() {

        int changedNodes =
                0;


        while (hasDirtyNodes()) {

            PositionNode node =
                    pollDirtyNode();


            if (node == null) {

                continue;
            }


            int oldValue =
                    node.getSearchValue();


            int newValue =
                    calculateCoverageAwareValue(
                            node
                    );


            /*
             * If this node's derived search value is unchanged,
             * nothing above it needs to be awakened.
             */
            if (oldValue == newValue) {

                continue;
            }


            node.setSearchValue(
                    newValue
            );

            changedNodes++;


            /*
             * A changed child can make every canonical parent stale.
             * Incoming-node tracking lets the update travel backward
             * through all transpositions without scanning the graph.
             */
            for (PositionNode parent :
                    node.getIncomingNodes()) {

                markSearchValueDirty(
                        parent
                );
            }
        }


        return changedNodes;
    }


    // =========================================================
    // FIND NODE WITH UNEXPLORED EDGES
    // =========================================================

    public PositionNode findNodeWithUnexploredEdges(
            MoveGenerator moveGenerator
    ) {

        for (PositionNode node :
                nodes.values()) {

            if (node.hasUnexploredEdges(
                    moveGenerator
            )) {

                return node;
            }
        }

        return null;
    }


    // =========================================================
    // NODE-DOVETAIL EDGE EXPANSION
    // =========================================================

    public boolean expandOneEdge(
            PositionNode node,
            MoveGenerator moveGenerator
    ) {

        Position position =
                node.getPosition();

        List<Move> legalMoves =
                getLegalMoves(
                        node
                );

        if (legalMoves.isEmpty()) {

            return false;
        }


        int index =
                node.getNextEdgeIndex();

        index =
                index % legalMoves.size();


        Move chosenMove =
                legalMoves.get(
                        index
                );


        // -----------------------------------------------------
        // Record dovetail work
        // -----------------------------------------------------

        node.incrementExpansionCount();

        node.incrementDovetailEdgeVisit(
                chosenMove
        );

        node.advanceEdgeIndex(
                legalMoves.size()
        );


        // -----------------------------------------------------
        // Generate child position
        // -----------------------------------------------------

        Position nextPosition =
                position.makeMove(
                        chosenMove
                );

        PositionNode nextNode =
                getOrCreateNode(
                        nextPosition
                );


        // -----------------------------------------------------
        // Store unique graph edge
        // -----------------------------------------------------

        node.addOutgoingEdge(
                chosenMove,
                nextNode
        );


        /*
         * Record the reverse dependency as well.
         *
         * A canonical child may have several parents because
         * different chess lines can transpose into the same node.
         * PositionNode stores incoming parents in a Set, so repeated
         * traversal of an already-known edge is harmless.
         */
        nextNode.addIncomingNode(
                node
        );


        /*
         * The expanded node's local coverage and potentially its
         * backed-up search information may now be stale.
         *
         * For this checkpoint we only TRACK dirtiness. Existing
         * full-graph propagation remains unchanged.
         */
        markSearchValueDirty(
                node
        );

        markOutcomeDirty(
                node
        );

        markStatisticsDirty(
                node
        );


        nextNode.incrementVisitCount();


        return true;
    }


    // =========================================================
    // COVERAGE-AWARE POSITION VALUE
    // =========================================================

    private int calculateCoverageAwareValue(
            PositionNode node
    ) {

        List<SearchEdge> outgoingEdges =
                node.getOutgoingEdges();


        /*
         * No explored continuation:
         * keep static evaluation.
         */
        if (outgoingEdges.isEmpty()) {

            return node.getEvaluation();
        }


        Color sideToMove =
                node.getPosition()
                        .getSideToMove();


        int minimaxValue;


        // -----------------------------------------------------
        // White maximizes
        // -----------------------------------------------------

        if (sideToMove == Color.WHITE) {

            minimaxValue =
                    Integer.MIN_VALUE;

            for (SearchEdge edge :
                    outgoingEdges) {

                int childValue =
                        edge.getTarget()
                                .getSearchValue();

                minimaxValue =
                        Math.max(
                                minimaxValue,
                                childValue
                        );
            }


            // -----------------------------------------------------
            // Black minimizes
            // -----------------------------------------------------

        } else {

            minimaxValue =
                    Integer.MAX_VALUE;

            for (SearchEdge edge :
                    outgoingEdges) {

                int childValue =
                        edge.getTarget()
                                .getSearchValue();

                minimaxValue =
                        Math.min(
                                minimaxValue,
                                childValue
                        );
            }
        }


        double coverage =
                node.getLocalCoverage(
                        moveGenerator
                );


        /*
         * Defensive clamp.
         */
        coverage =
                Math.max(
                        0.0,
                        Math.min(
                                1.0,
                                coverage
                        )
                );


        int staticEvaluation =
                node.getEvaluation();


        /*
         * Blend static evaluation toward
         * minimax according to how much of
         * this node has actually been explored.
         */
        double blendedValue =
                staticEvaluation
                        + coverage
                        * (
                        minimaxValue
                                - staticEvaluation
                );


        return (int) Math.round(
                blendedValue
        );
    }


    // =========================================================
    // SYNCHRONOUS POSITION-VALUE PROPAGATION
    // =========================================================

    public int backupAllNodesSynchronously() {

        Map<PositionNode, Integer> nextValues =
                new HashMap<>();

        int changedNodes = 0;


        /*
         * Phase 1:
         * calculate all new values from
         * the current generation.
         */
        for (PositionNode node :
                nodes.values()) {

            int newValue =
                    calculateCoverageAwareValue(
                            node
                    );

            nextValues.put(
                    node,
                    newValue
            );
        }


        /*
         * Phase 2:
         * apply simultaneously.
         */
        for (Map.Entry<PositionNode, Integer> entry :
                nextValues.entrySet()) {

            PositionNode node =
                    entry.getKey();

            int newValue =
                    entry.getValue();


            if (newValue
                    != node.getSearchValue()) {

                changedNodes++;
            }


            node.setSearchValue(
                    newValue
            );
        }


        return changedNodes;
    }


    // =========================================================
    // INCREMENTAL MATE / OUTCOME PROPAGATION
    // =========================================================

    public int propagateDirtyOutcomes() {

        int changedNodes =
                0;


        while (hasOutcomeDirtyNodes()) {

            PositionNode node =
                    pollOutcomeDirtyNode();


            if (node == null) {

                continue;
            }


            SearchOutcome oldOutcome =
                    node.getSearchOutcome();

            int oldMateDistance =
                    node.getMateDistance();


            recalculateSingleNodeOutcome(
                    node
            );


            boolean changed =
                    oldOutcome
                            != node.getSearchOutcome()
                            ||
                            oldMateDistance
                                    != node.getMateDistance();


            /*
             * If neither the proven result nor its mate distance
             * changed, no canonical parent needs to be awakened.
             */
            if (!changed) {

                continue;
            }


            changedNodes++;

            /*
             * SearchOutcome directly affects the GUI's solved and
             * explored counts.
             */
            markStatisticsDirty(
                    node
            );


            /*
             * Every canonical parent depends on this child's
             * outcome. A transposed position may therefore awaken
             * several different parents.
             */
            for (PositionNode parent :
                    node.getIncomingNodes()) {

                markOutcomeDirty(
                        parent
                );
            }
        }


        return changedNodes;
    }


    /*
     * Recalculate exactly one node using the same rules as the
     * established full-graph mate propagation.
     *
     * This intentionally handles only propagated wins/losses.
     * Terminal checkmates and terminal draws are seeded when the
     * PositionNode is created.
     */
    private void recalculateSingleNodeOutcome(
            PositionNode node
    ) {

        if (node == null) {

            return;
        }


        /*
         * A terminal checkmate is a base fact and must never
         * be overwritten by propagated information.
         */
        if (node.hasProvenMate()
                && node.getMateDistance() == 0) {

            return;
        }


        List<SearchEdge> edges =
                node.getOutgoingEdges();


        if (edges.isEmpty()) {

            return;
        }


        Color sideToMove =
                node.getPosition()
                        .getSideToMove();


        SearchOutcome sideToMoveWin =
                sideToMove == Color.WHITE
                        ? SearchOutcome.WHITE_WIN
                        : SearchOutcome.BLACK_WIN;


        SearchOutcome opponentWin =
                sideToMove == Color.WHITE
                        ? SearchOutcome.BLACK_WIN
                        : SearchOutcome.WHITE_WIN;


        // =====================================================
        // CASE 1:
        // SIDE TO MOVE CAN FORCE A WIN
        // =====================================================
        //
        // A single proven winning child is sufficient.
        // Among winning continuations, choose the shortest mate.
        // =====================================================

        boolean winningChildExists =
                false;

        int shortestWinningDistance =
                Integer.MAX_VALUE;


        for (SearchEdge edge :
                edges) {

            PositionNode child =
                    edge.getTarget();


            if (child.getSearchOutcome()
                    != sideToMoveWin) {

                continue;
            }


            if (child.getMateDistance()
                    < 0) {

                continue;
            }


            winningChildExists =
                    true;


            shortestWinningDistance =
                    Math.min(
                            shortestWinningDistance,
                            child.getMateDistance()
                    );
        }


        if (winningChildExists) {

            node.setSearchOutcome(
                    sideToMoveWin
            );

            node.setMateDistance(
                    shortestWinningDistance + 1
            );

            return;
        }


        // =====================================================
        // CASE 2:
        // SIDE TO MOVE IS FORCED TO LOSE
        // =====================================================
        //
        // A loss is provable only when every legal continuation
        // has been generated and every child is an opponent win.
        // The losing side chooses the continuation that delays
        // mate the longest.
        // =====================================================

        if (!node.isFullyExpanded(
                moveGenerator
        )) {

            return;
        }


        boolean allChildrenOpponentWins =
                true;

        int longestLosingDistance =
                Integer.MIN_VALUE;


        for (SearchEdge edge :
                edges) {

            PositionNode child =
                    edge.getTarget();


            if (child.getSearchOutcome()
                    != opponentWin) {

                allChildrenOpponentWins =
                        false;

                break;
            }


            if (child.getMateDistance()
                    < 0) {

                allChildrenOpponentWins =
                        false;

                break;
            }


            longestLosingDistance =
                    Math.max(
                            longestLosingDistance,
                            child.getMateDistance()
                    );
        }


        if (allChildrenOpponentWins) {

            node.setSearchOutcome(
                    opponentWin
            );

            node.setMateDistance(
                    longestLosingDistance + 1
            );
        }
    }


    // =========================================================
    // FULL MATE OUTCOME PROPAGATION
    // Reference / verification implementation
    // =========================================================

    public int propagateMateOutcomesOnePass() {

        int changedNodes = 0;


        for (PositionNode node :
                nodes.values()) {


            /*
             * Terminal checkmates must never
             * be overwritten.
             */
            if (node.hasProvenMate()
                    && node.getMateDistance() == 0) {

                continue;
            }


            List<SearchEdge> edges =
                    node.getOutgoingEdges();


            if (edges.isEmpty()) {

                continue;
            }


            Color sideToMove =
                    node.getPosition()
                            .getSideToMove();


            SearchOutcome sideToMoveWin =
                    sideToMove == Color.WHITE
                            ? SearchOutcome.WHITE_WIN
                            : SearchOutcome.BLACK_WIN;


            SearchOutcome opponentWin =
                    sideToMove == Color.WHITE
                            ? SearchOutcome.BLACK_WIN
                            : SearchOutcome.WHITE_WIN;


            // =================================================
            // CASE 1:
            // SIDE TO MOVE CAN FORCE A WIN
            // =================================================
            //
            // One winning child is sufficient.
            //
            // If several exist, choose the
            // shortest mate.
            // =================================================

            boolean winningChildExists =
                    false;

            int shortestWinningDistance =
                    Integer.MAX_VALUE;


            for (SearchEdge edge :
                    edges) {

                PositionNode child =
                        edge.getTarget();


                if (child.getSearchOutcome()
                        != sideToMoveWin) {

                    continue;
                }


                if (child.getMateDistance()
                        < 0) {

                    continue;
                }


                winningChildExists =
                        true;


                shortestWinningDistance =
                        Math.min(
                                shortestWinningDistance,
                                child.getMateDistance()
                        );
            }


            if (winningChildExists) {

                int newDistance =
                        shortestWinningDistance + 1;


                if (node.getSearchOutcome()
                        != sideToMoveWin
                        ||
                        node.getMateDistance()
                                != newDistance) {

                    node.setSearchOutcome(
                            sideToMoveWin
                    );

                    node.setMateDistance(
                            newDistance
                    );

                    changedNodes++;
                }


                continue;
            }


            // =================================================
            // CASE 2:
            // SIDE TO MOVE IS FORCED TO LOSE
            // =================================================
            //
            // We can prove this only when
            // EVERY legal move is known.
            //
            // The losing player chooses the
            // continuation that delays mate
            // the longest.
            // =================================================

            if (!node.isFullyExpanded(
                    moveGenerator
            )) {

                continue;
            }


            boolean allChildrenOpponentWins =
                    true;

            int longestLosingDistance =
                    Integer.MIN_VALUE;


            for (SearchEdge edge :
                    edges) {

                PositionNode child =
                        edge.getTarget();


                if (child.getSearchOutcome()
                        != opponentWin) {

                    allChildrenOpponentWins =
                            false;

                    break;
                }


                if (child.getMateDistance()
                        < 0) {

                    allChildrenOpponentWins =
                            false;

                    break;
                }


                longestLosingDistance =
                        Math.max(
                                longestLosingDistance,
                                child.getMateDistance()
                        );
            }


            if (allChildrenOpponentWins) {

                int newDistance =
                        longestLosingDistance + 1;


                if (node.getSearchOutcome()
                        != opponentWin
                        ||
                        node.getMateDistance()
                                != newDistance) {

                    node.setSearchOutcome(
                            opponentWin
                    );

                    node.setMateDistance(
                            newDistance
                    );

                    changedNodes++;
                }
            }
        }


        return changedNodes;
    }


    public void propagateMateOutcomesUntilStable(
            int maximumPasses
    ) {

        System.out.println();
        System.out.println(
                "MATE PROPAGATION"
        );


        for (int pass = 1;
             pass <= maximumPasses;
             pass++) {

            int changed =
                    propagateMateOutcomesOnePass();


            System.out.println(
                    "Pass "
                            + pass
                            + ": "
                            + changed
                            + " nodes changed"
            );


            if (changed == 0) {

                System.out.println(
                        "Mate propagation stabilized."
                );

                return;
            }
        }


        System.out.println(
                "Mate propagation reached pass limit."
        );
    }


    // =========================================================
    // MOVE RANKING
    // =========================================================

    public void printMoveRanking(
            PositionNode node
    ) {

        List<MoveAnalysis> analyses =
                analyzeMoves(
                        node
                );


        System.out.println();
        System.out.println(
                "MOVE RANKING"
        );

        System.out.println(
                "Side to move: "
                        + node.getPosition()
                        .getSideToMove()
        );


        int rank = 1;


        for (MoveAnalysis analysis :
                analyses) {

            double exploredPercent =
                    analysis.getGeneratedPositions() == 0
                            ? 0.0
                            : 100.0
                            * analysis.getExploredPositions()
                            / analysis.getGeneratedPositions();


            double solvedPercent =
                    analysis.getGeneratedPositions() == 0
                            ? 0.0
                            : 100.0
                            * analysis.getSolvedPositions()
                            / analysis.getGeneratedPositions();


            System.out.printf(
                    "%2d. %-10s"
                            + " | %-12s"
                            + " | value %6d"
                            + " | explored %d / %d"
                            + " (%6.2f%%)"
                            + " | solved %d / %d"
                            + " (%6.2f%%)%n",
                    rank,
                    analysis.getSan(),
                    analysis.getMateDisplay(),
                    analysis.getSearchValue(),
                    analysis.getExploredPositions(),
                    analysis.getGeneratedPositions(),
                    exploredPercent,
                    analysis.getSolvedPositions(),
                    analysis.getGeneratedPositions(),
                    solvedPercent
            );


            rank++;
        }
    }


    // =========================================================
    // MATE-AWARE MOVE COMPARATOR
    // =========================================================

    private int compareMovesForSide(
            SearchEdge edgeA,
            SearchEdge edgeB,
            Color sideToMove
    ) {

        PositionNode a =
                edgeA.getTarget();

        PositionNode b =
                edgeB.getTarget();


        SearchOutcome winningOutcome =
                sideToMove == Color.WHITE
                        ? SearchOutcome.WHITE_WIN
                        : SearchOutcome.BLACK_WIN;


        SearchOutcome losingOutcome =
                sideToMove == Color.WHITE
                        ? SearchOutcome.BLACK_WIN
                        : SearchOutcome.WHITE_WIN;


        int categoryA =
                getOutcomeCategory(
                        a,
                        winningOutcome,
                        losingOutcome
                );


        int categoryB =
                getOutcomeCategory(
                        b,
                        winningOutcome,
                        losingOutcome
                );


        /*
         * Lower category number is better.
         */
        if (categoryA != categoryB) {

            return Integer.compare(
                    categoryA,
                    categoryB
            );
        }


        // -----------------------------------------------------
        // Proven wins:
        // shorter mate is better
        // -----------------------------------------------------

        if (a.getSearchOutcome()
                == winningOutcome) {

            return Integer.compare(
                    a.getMateDistance(),
                    b.getMateDistance()
            );
        }


        // -----------------------------------------------------
        // Proven losses:
        // longer survival is better
        // -----------------------------------------------------

        if (a.getSearchOutcome()
                == losingOutcome) {

            return Integer.compare(
                    b.getMateDistance(),
                    a.getMateDistance()
            );
        }


        // -----------------------------------------------------
        // Otherwise use positional value
        // -----------------------------------------------------

        if (sideToMove == Color.WHITE) {

            return Integer.compare(
                    b.getSearchValue(),
                    a.getSearchValue()
            );

        } else {

            return Integer.compare(
                    a.getSearchValue(),
                    b.getSearchValue()
            );
        }
    }


    private int getOutcomeCategory(
            PositionNode node,
            SearchOutcome winningOutcome,
            SearchOutcome losingOutcome
    ) {

        /*
         * Category 0:
         * proven win
         */
        if (node.getSearchOutcome()
                == winningOutcome) {

            return 0;
        }


        /*
         * Category 1:
         * draw or unresolved
         *
         * We can distinguish DRAW from
         * UNKNOWN more carefully later.
         */
        if (node.getSearchOutcome()
                == SearchOutcome.UNKNOWN
                ||
                node.getSearchOutcome()
                        == SearchOutcome.DRAW) {

            return 1;
        }


        /*
         * Category 2:
         * proven loss
         */
        if (node.getSearchOutcome()
                == losingOutcome) {

            return 2;
        }


        return 1;
    }


    // =========================================================
    // HUMAN-FRIENDLY MATE LABEL
    // =========================================================

    private String formatMateForMove(
            PositionNode child,
            Color sideToMove
    ) {

        SearchOutcome outcome =
                child.getSearchOutcome();


        if (outcome
                != SearchOutcome.WHITE_WIN
                &&
                outcome
                        != SearchOutcome.BLACK_WIN) {

            return "-";
        }


        /*
         * Child distance counts from the
         * resulting position.
         *
         * Add one ply for the move itself.
         */
        int pliesFromCurrentPosition =
                child.getMateDistance() + 1;


        /*
         * Convert ply distance to normal
         * human "mate in N moves".
         */
        int movesToMate =
                (pliesFromCurrentPosition + 1)
                        / 2;


        boolean sideToMoveWins =
                (
                        sideToMove == Color.WHITE
                                &&
                                outcome
                                        == SearchOutcome.WHITE_WIN
                )
                        ||
                        (
                                sideToMove == Color.BLACK
                                        &&
                                        outcome
                                                == SearchOutcome.BLACK_WIN
                        );


        if (sideToMoveWins) {

            return "MATE IN "
                    + movesToMate;
        }


        return "MATED IN "
                + movesToMate;
    }


    // =========================================================
    // PRINCIPAL MATE LINE
    // =========================================================

    public void printPrincipalMateLine(
            PositionNode startingNode
    ) {

        if (!startingNode.hasProvenMate()) {

            System.out.println();
            System.out.println(
                    "No proven mating line from this position."
            );

            return;
        }


        System.out.println();
        System.out.println(
                "PRINCIPAL MATE LINE"
        );

        System.out.println(
                "Outcome: "
                        + startingNode
                        .getSearchOutcome()
        );

        System.out.println(
                "Distance: "
                        + startingNode
                        .getMateDistance()
                        + " plies"
        );


        PositionNode current =
                startingNode;

        int ply = 1;


        while (current.hasProvenMate()
                &&
                current.getMateDistance() > 0) {

            SearchEdge bestEdge =
                    choosePrincipalMateEdge(
                            current
                    );


            if (bestEdge == null) {

                System.out.println(
                        "Principal line incomplete."
                );

                return;
            }


            PositionNode child =
                    bestEdge.getTarget();


            String moveText =
                    getOrCreateSan(
                            current,
                            bestEdge
                    );


            System.out.println(
                    "Ply "
                            + ply
                            + ": "
                            + moveText
                            + " -> "
                            + child.getSearchOutcome()
                            + " | distance "
                            + child.getMateDistance()
            );


            current =
                    child;

            ply++;
        }


        if (current.hasProvenMate()
                &&
                current.getMateDistance() == 0) {

            System.out.println(
                    "CHECKMATE"
            );
        }
    }


    // =========================================================
    // CHOOSE ONE PRINCIPAL MATE EDGE
    // =========================================================

    private SearchEdge choosePrincipalMateEdge(
            PositionNode node
    ) {

        SearchOutcome outcome =
                node.getSearchOutcome();


        if (outcome
                != SearchOutcome.WHITE_WIN
                &&
                outcome
                        != SearchOutcome.BLACK_WIN) {

            return null;
        }


        Color sideToMove =
                node.getPosition()
                        .getSideToMove();


        boolean winningSideToMove =
                (
                        sideToMove == Color.WHITE
                                &&
                                outcome
                                        == SearchOutcome.WHITE_WIN
                )
                        ||
                        (
                                sideToMove == Color.BLACK
                                        &&
                                        outcome
                                                == SearchOutcome.BLACK_WIN
                        );


        SearchEdge chosenEdge =
                null;


        // -----------------------------------------------------
        // Winner to move:
        // shortest continuation
        // -----------------------------------------------------

        if (winningSideToMove) {

            int shortestDistance =
                    Integer.MAX_VALUE;


            for (SearchEdge edge :
                    node.getOutgoingEdges()) {

                PositionNode child =
                        edge.getTarget();


                if (child.getSearchOutcome()
                        != outcome) {

                    continue;
                }


                if (child.getMateDistance()
                        < 0) {

                    continue;
                }


                if (child.getMateDistance()
                        < shortestDistance) {

                    shortestDistance =
                            child.getMateDistance();

                    chosenEdge =
                            edge;
                }
            }


            // -----------------------------------------------------
            // Loser to move:
            // longest surviving continuation
            // -----------------------------------------------------

        } else {

            int longestDistance =
                    Integer.MIN_VALUE;


            for (SearchEdge edge :
                    node.getOutgoingEdges()) {

                PositionNode child =
                        edge.getTarget();


                if (child.getSearchOutcome()
                        != outcome) {

                    continue;
                }


                if (child.getMateDistance()
                        < 0) {

                    continue;
                }


                if (child.getMateDistance()
                        > longestDistance) {

                    longestDistance =
                            child.getMateDistance();

                    chosenEdge =
                            edge;
                }
            }
        }


        return chosenEdge;
    }


    // =========================================================
    // ALL EQUALLY OPTIMAL MATE EDGES
    // =========================================================

    public List<SearchEdge> getOptimalMateEdges(
            PositionNode node
    ) {

        List<SearchEdge> optimalEdges =
                new ArrayList<>();


        SearchOutcome outcome =
                node.getSearchOutcome();


        if (outcome
                != SearchOutcome.WHITE_WIN
                &&
                outcome
                        != SearchOutcome.BLACK_WIN) {

            return optimalEdges;
        }


        if (node.getMateDistance()
                <= 0) {

            return optimalEdges;
        }


        Color sideToMove =
                node.getPosition()
                        .getSideToMove();


        boolean winningSideToMove =
                (
                        sideToMove == Color.WHITE
                                &&
                                outcome
                                        == SearchOutcome.WHITE_WIN
                )
                        ||
                        (
                                sideToMove == Color.BLACK
                                        &&
                                        outcome
                                                == SearchOutcome.BLACK_WIN
                        );


        // -----------------------------------------------------
        // Winning side:
        // all shortest mates
        // -----------------------------------------------------

        if (winningSideToMove) {

            int bestDistance =
                    Integer.MAX_VALUE;


            for (SearchEdge edge :
                    node.getOutgoingEdges()) {

                PositionNode child =
                        edge.getTarget();


                if (child.getSearchOutcome()
                        != outcome) {

                    continue;
                }


                if (child.getMateDistance()
                        < 0) {

                    continue;
                }


                int distance =
                        child.getMateDistance();


                if (distance
                        < bestDistance) {

                    bestDistance =
                            distance;

                    optimalEdges.clear();

                    optimalEdges.add(
                            edge
                    );

                } else if (distance
                        == bestDistance) {

                    optimalEdges.add(
                            edge
                    );
                }
            }


            // -----------------------------------------------------
            // Losing side:
            // all longest defenses
            // -----------------------------------------------------

        } else {

            int bestDistance =
                    Integer.MIN_VALUE;


            for (SearchEdge edge :
                    node.getOutgoingEdges()) {

                PositionNode child =
                        edge.getTarget();


                if (child.getSearchOutcome()
                        != outcome) {

                    continue;
                }


                if (child.getMateDistance()
                        < 0) {

                    continue;
                }


                int distance =
                        child.getMateDistance();


                if (distance
                        > bestDistance) {

                    bestDistance =
                            distance;

                    optimalEdges.clear();

                    optimalEdges.add(
                            edge
                    );

                } else if (distance
                        == bestDistance) {

                    optimalEdges.add(
                            edge
                    );
                }
            }
        }


        return optimalEdges;
    }


    // =========================================================
    // OPTIMAL MATE TREE
    // =========================================================

    public void printOptimalMateTree(
            PositionNode startingNode
    ) {

        System.out.println();
        System.out.println(
                "OPTIMAL MATE TREE"
        );


        if (!startingNode.hasProvenMate()) {

            System.out.println(
                    "No proven mate from this position."
            );

            return;
        }


        System.out.println(
                "Outcome: "
                        + startingNode
                        .getSearchOutcome()
        );

        System.out.println(
                "Distance: "
                        + startingNode
                        .getMateDistance()
                        + " plies"
        );


        printOptimalMateTreeRecursive(
                startingNode,
                "",
                true
        );
    }


    private void printOptimalMateTreeRecursive(
            PositionNode node,
            String prefix,
            boolean root
    ) {

        if (node.getMateDistance()
                == 0) {

            if (!root) {

                System.out.println(
                        prefix
                                + "CHECKMATE"
                );
            }

            return;
        }


        List<SearchEdge> optimalEdges =
                getOptimalMateEdges(
                        node
                );


        if (optimalEdges.isEmpty()) {

            System.out.println(
                    prefix
                            + "[LINE INCOMPLETE]"
            );

            return;
        }


        for (int i = 0;
             i < optimalEdges.size();
             i++) {

            SearchEdge edge =
                    optimalEdges.get(i);

            PositionNode child =
                    edge.getTarget();


            boolean last =
                    i
                            == optimalEdges.size()
                            - 1;


            String connector =
                    last
                            ? "└── "
                            : "├── ";


            String moveText =
                    getOrCreateSan(
                            node,
                            edge
                    );


            System.out.println(
                    prefix
                            + connector
                            + moveText
                            + " | "
                            + child.getSearchOutcome()
                            + " | distance "
                            + child.getMateDistance()
            );


            String childPrefix =
                    prefix
                            + (
                            last
                                    ? "    "
                                    : "│   "
                    );


            printOptimalMateTreeRecursive(
                    child,
                    childPrefix,
                    false
            );
        }
    }


    // =========================================================
    // OUTCOME SUMMARY
    // =========================================================

    public void printOutcomeSummary() {

        int unknown = 0;

        int whiteWins = 0;

        int blackWins = 0;

        int draws = 0;

        int terminalMates = 0;


        for (PositionNode node :
                nodes.values()) {

            switch (
                    node.getSearchOutcome()
            ) {

                case UNKNOWN ->

                        unknown++;


                case WHITE_WIN -> {

                    whiteWins++;

                    if (node.getMateDistance()
                            == 0) {

                        terminalMates++;
                    }
                }


                case BLACK_WIN -> {

                    blackWins++;

                    if (node.getMateDistance()
                            == 0) {

                        terminalMates++;
                    }
                }


                case DRAW ->

                        draws++;
            }
        }


        System.out.println();
        System.out.println(
                "SEARCH OUTCOMES"
        );

        System.out.println(
                "Unknown: "
                        + unknown
        );

        System.out.println(
                "White wins: "
                        + whiteWins
        );

        System.out.println(
                "Black wins: "
                        + blackWins
        );

        System.out.println(
                "Draws: "
                        + draws
        );

        System.out.println(
                "Terminal checkmates: "
                        + terminalMates
        );
    }


    // =========================================================
    // EVALUATION SUMMARY
    // =========================================================

    public void printEvaluationSummary() {

        if (nodes.isEmpty()) {

            return;
        }


        int bestWhiteEvaluation =
                Integer.MIN_VALUE;

        int bestBlackEvaluation =
                Integer.MAX_VALUE;

        int equalEvaluationPositions =
                0;


        for (PositionNode node :
                nodes.values()) {

            int evaluation =
                    node.getEvaluation();


            bestWhiteEvaluation =
                    Math.max(
                            bestWhiteEvaluation,
                            evaluation
                    );


            bestBlackEvaluation =
                    Math.min(
                            bestBlackEvaluation,
                            evaluation
                    );


            if (evaluation == 0) {

                equalEvaluationPositions++;
            }
        }


        System.out.println();
        System.out.println(
                "GRAPH EVALUATIONS"
        );

        System.out.println(
                "Positions evaluated: "
                        + nodes.size()
        );

        System.out.println(
                "Best for White: "
                        + bestWhiteEvaluation
        );

        System.out.println(
                "Best for Black: "
                        + bestBlackEvaluation
        );

        System.out.println(
                "Equal evaluation positions: "
                        + equalEvaluationPositions
        );
    }

    // =========================================================
    // SUBTREE GENERATED / EXPLORED / SOLVED STATISTICS
    // =========================================================

    /*
     * Returns:
     *
     *     index 0 = generated positions
     *     index 1 = explored positions
     *     index 2 = solved positions
     *
     * Positions are counted once even when several
     * move sequences transpose into the same graph node.
     *
     * The starting node itself is included.
     *
     * "Explored" means either:
     *
     *     - the position is already solved/terminal, or
     *     - every immediate legal continuation has been generated.
     *
     * "Solved" means SearchOutcome is no longer UNKNOWN.
     */
    private int[] calculateSubtreeStatistics(
            PositionNode startingNode
    ) {

        long statisticsStartNanos =
                System.nanoTime();

        profileStatisticsCalls++;


        if (startingNode == null) {

            profileStatisticsNanos +=
                    System.nanoTime()
                            - statisticsStartNanos;

            return new int[]{
                    0,
                    0,
                    0
            };
        }


        /*
         * Flush pending reverse invalidation before consulting a
         * cached result.
         */
        propagateStatisticsDirtiness();


        if (startingNode.hasValidSubtreeStatistics()) {

            profileStatisticsCacheHits++;

            profileStatisticsNanos +=
                    System.nanoTime()
                            - statisticsStartNanos;

            return new int[]{
                    startingNode.getCachedGeneratedPositions(),
                    startingNode.getCachedExploredPositions(),
                    startingNode.getCachedSolvedPositions()
            };
        }


        profileStatisticsExactRecomputations++;


        /*
         * Exact recomputation/reference path.
         *
         * The visited set remains necessary because two different
         * branches may transpose into the same canonical node.
         */
        Set<PositionNode> visited =
                new HashSet<>();

        List<PositionNode> frontier =
                new ArrayList<>();

        frontier.add(
                startingNode
        );


        int generatedPositions =
                0;

        int exploredPositions =
                0;

        int solvedPositions =
                0;


        while (!frontier.isEmpty()) {

            PositionNode current =
                    frontier.remove(
                            frontier.size() - 1
                    );


            if (!visited.add(
                    current
            )) {

                continue;
            }


            generatedPositions++;


            boolean solved =
                    current.getSearchOutcome()
                            != SearchOutcome.UNKNOWN;


            if (solved) {

                solvedPositions++;
            }


            boolean explored =
                    solved
                            ||
                            current.isFullyExpanded(
                                    moveGenerator
                            );


            if (explored) {

                exploredPositions++;
            }


            for (SearchEdge edge :
                    current.getOutgoingEdges()) {

                PositionNode child =
                        edge.getTarget();


                if (!visited.contains(
                        child
                )) {

                    frontier.add(
                            child
                    );
                }
            }
        }


        startingNode.cacheSubtreeStatistics(
                generatedPositions,
                exploredPositions,
                solvedPositions
        );


        profileStatisticsVisitedNodes +=
                generatedPositions;

        profileStatisticsNanos +=
                System.nanoTime()
                        - statisticsStartNanos;


        return new int[]{
                generatedPositions,
                exploredPositions,
                solvedPositions
        };
    }


    // =========================================================
    // POSITION ANALYSIS
    // =========================================================

    public PositionAnalysis analyzePosition(
            PositionNode node
    ) {

        long snapshotStartNanos =
                System.nanoTime();

        long beforeSort =
                profileMoveSortNanos;

        long beforeSan =
                profileSanNanos;

        long beforeStatistics =
                profileStatisticsNanos;

        long beforeVariation =
                profileVariationNanos;


        List<MoveAnalysis> moveAnalyses =
                analyzeMoves(
                        node
                );


        long afterMovesNanos =
                System.nanoTime();


        PositionAnalysis analysis =
                new PositionAnalysis(
                        node.getPosition()
                                .getSideToMove(),

                        node.getSearchOutcome(),

                        node.getMateDistance(),

                        node.getEvaluation(),

                        node.getSearchValue(),

                        moveAnalyses
                );


        long pvStartNanos =
                System.nanoTime();


        List<VariationNode> principalVariation =
                buildPrincipalVariation(
                        node,
                        20
                );


        for (VariationNode variation :
                principalVariation) {

            analysis.addPrincipalVariationNode(
                    variation
            );
        }


        long pvElapsed =
                System.nanoTime()
                        - pvStartNanos;

        profilePrincipalVariationNanos +=
                pvElapsed;


        long categorizedMoveTime =
                (profileMoveSortNanos - beforeSort)
                        +
                        (profileSanNanos - beforeSan)
                        +
                        (profileStatisticsNanos - beforeStatistics)
                        +
                        (profileVariationNanos - beforeVariation);


        long moveAnalysisElapsed =
                afterMovesNanos
                        - snapshotStartNanos;


        long uncategorizedMoveTime =
                moveAnalysisElapsed
                        - categorizedMoveTime;


        long positionAnalysisConstruction =
                pvStartNanos
                        - afterMovesNanos;


        long other =
                uncategorizedMoveTime
                        +
                        positionAnalysisConstruction;


        if (other > 0L) {

            profileOtherNanos +=
                    other;
        }


        profileSnapshotCount++;


        if (profileSnapshotCount
                >= SNAPSHOT_PROFILE_INTERVAL) {

            printSnapshotProfiler();

            resetSnapshotProfiler();
        }


        return analysis;
    }


    // =========================================================
    // MOVE ANALYSIS
    // =========================================================

    public List<MoveAnalysis> analyzeMoves(
            PositionNode node
    ) {

        List<SearchEdge> edges =
                new ArrayList<>(
                        node.getOutgoingEdges()
                );


        Color sideToMove =
                node.getPosition()
                        .getSideToMove();


        /*
         * Rank root moves using the exact same
         * mate-first / evaluation-second logic
         * used throughout the graph.
         */
        long sortStartNanos =
                System.nanoTime();


        edges.sort(
                (edgeA, edgeB) ->
                        compareMovesForSide(
                                edgeA,
                                edgeB,
                                sideToMove
                        )
        );


        profileMoveSortNanos +=
                System.nanoTime()
                        - sortStartNanos;


        List<MoveAnalysis> analyses =
                new ArrayList<>();


        for (SearchEdge edge :
                edges) {

            PositionNode child =
                    edge.getTarget();


            long sanStartNanos =
                    System.nanoTime();


            String san =
                    getOrCreateSan(
                            node,
                            edge
                    );


            profileSanNanos +=
                    System.nanoTime()
                            - sanStartNanos;


            String mateDisplay =
                    formatMateForMove(
                            child,
                            sideToMove
                    );


            int[] statistics =
                    calculateSubtreeStatistics(
                            child
                    );


            int generatedPositions =
                    statistics[0];


            int exploredPositions =
                    statistics[1];


            int solvedPositions =
                    statistics[2];


            List<VariationNode> variations;


            /*
             * Proven mates:
             *
             * Show the optimal mate tree rather
             * than every irrelevant searched move.
             */
            /*
             * GUI continuations are always materialized lazily and
             * always preserve every generated edge.
             *
             * Proven mate information affects ranking through the
             * existing mate-aware comparator, but it no longer filters
             * non-optimal continuations out of the visible graph.
             */
            long variationStartNanos =
                    System.nanoTime();


            variations =
                    buildVariationChildren(
                            child,
                            1
                    );


            profileVariationNanos +=
                    System.nanoTime()
                            - variationStartNanos;


            MoveAnalysis analysis =
                    new MoveAnalysis(
                            edge.getMove(),
                            san,
                            child.getPosition(),
                            child.getSearchOutcome(),
                            child.getMateDistance(),
                            mateDisplay,
                            child.getEvaluation(),
                            child.getSearchValue(),
                            generatedPositions,
                            exploredPositions,
                            solvedPositions
                    );


            analysis.addVariations(
                    variations
            );


            analyses.add(
                    analysis
            );
        }


        return analyses;
    }


    // =========================================================
    // MANUAL MOVE GRAPH COMMIT
    // =========================================================

    /*
     * Ensure that a manually played legal move is represented as an
     * edge in the SAME persistent PositionGraph.
     *
     * Manual play must not depend on whether Swing happened to have
     * materialized the corresponding continuation card already.
     *
     * Returns true when:
     *
     *   parentPosition -> childPosition
     *
     * is already an existing graph edge, or when the legal move that
     * produces childPosition is found and the edge is added here.
     *
     * This does not restart analysis and does not create a new graph.
     */
    public boolean ensureManualContinuation(
            Position parentPosition,
            Position childPosition
    ) {

        if (parentPosition == null
                ||
                childPosition == null) {

            return false;
        }


        PositionNode parent =
                nodes.get(
                        parentPosition.createPositionKey()
                );


        if (parent == null) {

            /*
             * The committed/manual position should normally already
             * exist in the active graph. Creating it here is still
             * preferable to breaking path persistence.
             */
            parent =
                    getOrCreateNode(
                            parentPosition
                    );
        }


        PositionKey childKey =
                childPosition.createPositionKey();


        // -----------------------------------------------------
        // Existing edge?
        // -----------------------------------------------------

        for (SearchEdge edge :
                parent.getOutgoingEdges()) {

            PositionNode target =
                    edge.getTarget();


            if (target != null
                    &&
                    target.getPosition()
                            .createPositionKey()
                            .equals(
                                    childKey
                            )) {

                return true;
            }
        }


        // -----------------------------------------------------
        // Find the legal move that produces this exact position.
        // -----------------------------------------------------

        List<Move> legalMoves =
                getLegalMoves(
                        parent
                );


        for (Move move :
                legalMoves) {

            Position generatedChild =
                    parentPosition.makeMove(
                            move
                    );


            if (!generatedChild
                    .createPositionKey()
                    .equals(
                            childKey
                    )) {

                continue;
            }


            PositionNode child =
                    getOrCreateNode(
                            generatedChild
                    );


            parent.addOutgoingEdge(
                    move,
                    child
            );


            child.addIncomingNode(
                    parent
            );


            /*
             * Adding this edge changes local coverage and may change
             * both the derived numerical value and proven outcome.
             */
            markSearchValueDirty(
                    parent
            );

            markOutcomeDirty(
                    parent
            );

            markStatisticsDirty(
                    parent
            );


            return true;
        }


        /*
         * ChessBoardPanel only reports legal moves, so reaching this
         * branch indicates a genuine state-identity mismatch rather
         * than an ordinary GUI-materialization issue.
         */
        return false;
    }


    // =========================================================
    // LAZY GUI CONTINUATION ACCESS
    // =========================================================

    /*
     * Return only the immediate already-generated continuations of
     * an existing canonical graph position.
     *
     * This method does NOT generate new chess moves and does NOT
     * create a disconnected graph node. It is purely a view over the
     * persistent PositionGraph for on-demand GUI navigation.
     */
    public List<VariationNode> buildImmediateVariationChildren(
            Position position
    ) {

        if (position == null) {

            return List.of();
        }


        PositionNode node =
                nodes.get(
                        position.createPositionKey()
                );


        if (node == null) {

            return List.of();
        }


        /*
         * Always return every immediate generated continuation.
         *
         * The variation builder sorts edges with the mate-aware move
         * comparator, so shortest winning mates still appear first and
         * longest defenses remain preferred for the losing side.
         *
         * Crucially, proving mate no longer hides the rest of the
         * already-generated graph from the GUI.
         */
        return buildVariationChildren(
                node,
                1
        );
    }


    /*
     * Retained as a debugging/reference helper for constructing only
     * optimal mate continuations. Normal GUI navigation no longer uses
     * this filter because all generated branches should remain visible.
     */
    private List<VariationNode> buildImmediateOptimalVariationChildren(
            PositionNode node
    ) {

        List<VariationNode> result =
                new ArrayList<>();


        if (node == null
                ||
                node.getMateDistance() <= 0) {

            return result;
        }


        List<SearchEdge> optimalEdges =
                getOptimalMateEdges(
                        node
                );


        for (SearchEdge edge :
                optimalEdges) {

            PositionNode child =
                    edge.getTarget();


            String san =
                    getOrCreateSan(
                            node,
                            edge
                    );


            boolean terminal =
                    child.hasProvenMate()
                            &&
                            child.getMateDistance() == 0;


            int[] statistics =
                    calculateSubtreeStatistics(
                            child
                    );


            VariationNode variation =
                    new VariationNode(
                            san,
                            child.getPosition(),
                            child.getSearchOutcome(),
                            child.getMateDistance(),
                            child.getEvaluation(),
                            child.getSearchValue(),
                            terminal,
                            statistics[0],
                            statistics[1],
                            statistics[2]
                    );


            result.add(
                    variation
            );
        }


        return result;
    }


    // =========================================================
    // OPTIMAL MATE VARIATION TREE
    // Retained as a reference/debugging implementation.
    // The normal GUI no longer materializes this recursively.
    // =========================================================

    private List<VariationNode> buildOptimalVariationChildren(
            PositionNode node
    ) {

        List<VariationNode> result =
                new ArrayList<>();


        /*
         * Distance zero means the current node
         * itself is already checkmate.
         */
        if (node.getMateDistance() <= 0) {

            return result;
        }


        /*
         * Winning side:
         * all shortest mates.
         *
         * Losing side:
         * all longest defenses.
         */
        List<SearchEdge> optimalEdges =
                getOptimalMateEdges(
                        node
                );


        for (SearchEdge edge :
                optimalEdges) {

            PositionNode child =
                    edge.getTarget();


            String san =
                    getOrCreateSan(
                            node,
                            edge
                    );


            Position childPosition =
                    child.getPosition();


            boolean terminal =
                    child.hasProvenMate()
                            &&
                            child.getMateDistance() == 0;


            int[] statistics =
                    calculateSubtreeStatistics(
                            child
                    );


            int generatedPositions =
                    statistics[0];


            int exploredPositions =
                    statistics[1];


            int solvedPositions =
                    statistics[2];


            VariationNode variationNode =
                    new VariationNode(
                            san,
                            childPosition,
                            child.getSearchOutcome(),
                            child.getMateDistance(),
                            child.getEvaluation(),
                            child.getSearchValue(),
                            terminal,
                            generatedPositions,
                            exploredPositions,
                            solvedPositions
                    );


            List<VariationNode> childVariations =
                    buildOptimalVariationChildren(
                            child
                    );


            for (VariationNode childVariation :
                    childVariations) {

                variationNode.addChild(
                        childVariation
                );
            }


            result.add(
                    variationNode
            );
        }


        return result;
    }


    // =========================================================
    // ORDINARY SEARCH VARIATION TREE
    // =========================================================

    private List<VariationNode> buildVariationChildren(
            PositionNode node,
            int remainingDepth
    ) {

        List<VariationNode> result =
                new ArrayList<>();


        // -----------------------------------------------------
        // Depth limit
        // -----------------------------------------------------

        if (remainingDepth <= 0) {

            return result;
        }


        // -----------------------------------------------------
        // No explored continuations
        // -----------------------------------------------------

        if (node.getOutgoingEdges().isEmpty()) {

            return result;
        }


        /*
         * Work with a copy so sorting for the
         * GUI never mutates graph storage.
         */
        List<SearchEdge> rankedEdges =
                new ArrayList<>(
                        node.getOutgoingEdges()
                );


        /*
         * Critical:
         *
         * Each ply must be ranked from the
         * perspective of the player whose turn
         * it is AT THAT NODE.
         *
         * White maximizes.
         * Black minimizes.
         *
         * Proven outcomes still outrank
         * numerical evaluation because
         * compareMovesForSide(...) is
         * mate-aware.
         */
        Color sideToMove =
                node.getPosition()
                        .getSideToMove();


        long sortStartNanos =
                System.nanoTime();


        rankedEdges.sort(
                (edgeA, edgeB) ->
                        compareMovesForSide(
                                edgeA,
                                edgeB,
                                sideToMove
                        )
        );


        profileMoveSortNanos +=
                System.nanoTime()
                        - sortStartNanos;


        for (SearchEdge edge :
                rankedEdges) {

            PositionNode child =
                    edge.getTarget();


            long sanStartNanos =
                    System.nanoTime();


            String san =
                    getOrCreateSan(
                            node,
                            edge
                    );


            profileSanNanos +=
                    System.nanoTime()
                            - sanStartNanos;


            boolean terminal =
                    child.getOutgoingEdges()
                            .isEmpty()
                            &&
                            child.getSearchOutcome()
                                    != SearchOutcome.UNKNOWN;


            int[] statistics =
                    calculateSubtreeStatistics(
                            child
                    );


            int generatedPositions =
                    statistics[0];


            int exploredPositions =
                    statistics[1];


            int solvedPositions =
                    statistics[2];


            VariationNode variation =
                    new VariationNode(
                            san,
                            child.getPosition(),
                            child.getSearchOutcome(),
                            child.getMateDistance(),
                            child.getEvaluation(),
                            child.getSearchValue(),
                            terminal,
                            generatedPositions,
                            exploredPositions,
                            solvedPositions
                    );


            /*
             * Recursively expose the portion
             * of the graph already searched.
             */
            List<VariationNode> grandchildren =
                    buildVariationChildren(
                            child,
                            remainingDepth - 1
                    );


            for (VariationNode grandchild :
                    grandchildren) {

                variation.addChild(
                        grandchild
                );
            }


            result.add(
                    variation
            );
        }


        return result;
    }


    // =========================================================
    // PRINCIPAL VARIATION
    // =========================================================

    /*
     * Returns the engine's currently preferred
     * path through the already-searched graph.
     *
     * This does NOT perform a new search.
     *
     * At each position:
     *
     *     1. rank the outgoing edges
     *     2. choose index 0
     *     3. continue from that child
     *
     * Proven mate information automatically
     * takes priority because the same
     * compareMovesForSide(...) method is used.
     */
    public List<VariationNode> buildPrincipalVariation(
            PositionNode startingNode,
            int maximumPlies
    ) {

        List<VariationNode> principalVariation =
                new ArrayList<>();


        if (startingNode == null) {

            return principalVariation;
        }


        if (maximumPlies <= 0) {

            return principalVariation;
        }


        PositionNode current =
                startingNode;


        /*
         * Because PositionGraph is a graph rather
         * than necessarily a pure tree, guard
         * against accidentally following a cycle.
         */
        List<PositionNode> visited =
                new ArrayList<>();


        for (int ply = 0;
             ply < maximumPlies;
             ply++) {


            if (visited.contains(
                    current
            )) {

                break;
            }


            visited.add(
                    current
            );


            List<SearchEdge> edges =
                    new ArrayList<>(
                            current.getOutgoingEdges()
                    );


            if (edges.isEmpty()) {

                break;
            }


            Color sideToMove =
                    current.getPosition()
                            .getSideToMove();


            edges.sort(
                    (edgeA, edgeB) ->
                            compareMovesForSide(
                                    edgeA,
                                    edgeB,
                                    sideToMove
                            )
            );


            /*
             * The comparator puts the preferred
             * move first.
             */
            SearchEdge bestEdge =
                    edges.get(0);


            PositionNode child =
                    bestEdge.getTarget();


            String san =
                    getOrCreateSan(
                            current,
                            bestEdge
                    );


            boolean terminal =
                    child.getOutgoingEdges()
                            .isEmpty()
                            &&
                            child.getSearchOutcome()
                                    != SearchOutcome.UNKNOWN;


            int[] statistics =
                    calculateSubtreeStatistics(
                            child
                    );


            int generatedPositions =
                    statistics[0];


            int exploredPositions =
                    statistics[1];


            int solvedPositions =
                    statistics[2];


            VariationNode pvNode =
                    new VariationNode(
                            san,
                            child.getPosition(),
                            child.getSearchOutcome(),
                            child.getMateDistance(),
                            child.getEvaluation(),
                            child.getSearchValue(),
                            terminal,
                            generatedPositions,
                            exploredPositions,
                            solvedPositions
                    );


            principalVariation.add(
                    pvNode
            );


            /*
             * Terminal checkmate/draw:
             * no reason to continue.
             */
            if (terminal) {

                break;
            }


            current =
                    child;
        }


        return principalVariation;
    }


    // =========================================================
    // PRINCIPAL VARIATION PRINTING
    // =========================================================

    public void printPrincipalVariation(
            PositionNode startingNode,
            int maximumPlies
    ) {

        List<VariationNode> pv =
                buildPrincipalVariation(
                        startingNode,
                        maximumPlies
                );


        System.out.println();

        System.out.println(
                "PRINCIPAL VARIATION"
        );


        if (pv.isEmpty()) {

            System.out.println(
                    "No principal variation available."
            );

            return;
        }


        int ply = 1;


        for (VariationNode node :
                pv) {

            System.out.println(
                    "Ply "
                            + ply
                            + ": "
                            + node.getSan()
                            + " | outcome "
                            + node.getOutcome()
                            + " | value "
                            + node.getSearchValue()
                            + (
                            node.getMateDistance() >= 0
                                    ? " | mateDistance "
                                    + node.getMateDistance()
                                    : ""
                    )
            );


            ply++;
        }
    }


    // =========================================================
    // POSITION ANALYSIS CONSOLE OUTPUT
    // =========================================================

    public void printPositionAnalysis(
            PositionNode node
    ) {

        PositionAnalysis analysis =
                analyzePosition(
                        node
                );


        System.out.println();

        System.out.println(
                "POSITION ANALYSIS"
        );


        System.out.println(
                "Side to move: "
                        + analysis.getSideToMove()
        );


        System.out.println(
                "Outcome: "
                        + analysis.getOutcome()
        );


        System.out.println(
                "Mate distance: "
                        + analysis.getMateDistance()
        );


        System.out.println(
                "Static evaluation: "
                        + analysis.getEvaluation()
        );


        System.out.println(
                "Search value: "
                        + analysis.getSearchValue()
        );


        System.out.println(
                "Analyzed moves: "
                        + analysis.getMoves().size()
        );


        System.out.println();

        System.out.println(
                "RANKED MOVES"
        );


        int rank = 1;


        for (MoveAnalysis move :
                analysis.getMoves()) {

            double exploredPercent =
                    move.getGeneratedPositions() == 0
                            ? 0.0
                            : 100.0
                            * move.getExploredPositions()
                            / move.getGeneratedPositions();


            double solvedPercent =
                    move.getGeneratedPositions() == 0
                            ? 0.0
                            : 100.0
                            * move.getSolvedPositions()
                            / move.getGeneratedPositions();


            System.out.printf(
                    "%2d. %-10s"
                            + " | %-12s"
                            + " | value %6d"
                            + " | explored %d / %d"
                            + " (%6.2f%%)"
                            + " | solved %d / %d"
                            + " (%6.2f%%)%n",

                    rank,

                    move.getSan(),

                    move.getMateDisplay(),

                    move.getSearchValue(),

                    move.getExploredPositions(),

                    move.getGeneratedPositions(),

                    exploredPercent,

                    move.getSolvedPositions(),

                    move.getGeneratedPositions(),

                    solvedPercent
            );


            rank++;
        }


        /*
         * Also display the currently preferred
         * searched continuation.
         */
        printPrincipalVariation(
                node,
                20
        );
    }


    // =========================================================
    // SAN CACHE
    // =========================================================

    private String getOrCreateSan(
            PositionNode parent,
            SearchEdge edge
    ) {

        String cached =
                sanCache.get(
                        edge
                );


        if (cached != null) {

            profileSanCacheHits++;

            return cached;
        }


        profileSanCacheMisses++;


        String san =
                sanMoveFormatter.format(
                        parent.getPosition(),
                        edge.getMove()
                );


        sanCache.put(
                edge,
                san
        );


        return san;
    }


    // =========================================================
    // DETAILED SNAPSHOT PROFILER
    // =========================================================

    private void printSnapshotProfiler() {

        double snapshots =
                Math.max(
                        1L,
                        profileSnapshotCount
                );


        long categorized =
                profileMoveSortNanos
                        +
                        profileSanNanos
                        +
                        profileStatisticsNanos
                        +
                        profileVariationNanos
                        +
                        profilePrincipalVariationNanos
                        +
                        profileOtherNanos;


        System.out.println();

        System.out.println(
                "=== Snapshot Breakdown ==="
        );

        System.out.printf(
                "Snapshots: %d%n",
                profileSnapshotCount
        );

        System.out.println(
                "Average per snapshot:"
        );

        System.out.printf(
                "  Move sorting/ranking:    %.3f ms%n",
                nanosToMillis(
                        profileMoveSortNanos
                                / snapshots
                )
        );

        System.out.printf(
                "  SAN lookup/formatting:   %.3f ms%n",
                nanosToMillis(
                        profileSanNanos
                                / snapshots
                )
        );

        System.out.println(
                "SAN cache activity:"
        );

        System.out.printf(
                "  Cache hits:              %d%n",
                profileSanCacheHits
        );

        System.out.printf(
                "  Cache misses:            %d%n",
                profileSanCacheMisses
        );

        long sanRequests =
                profileSanCacheHits
                        +
                        profileSanCacheMisses;

        if (sanRequests > 0L) {

            double sanHitRate =
                    100.0
                            *
                            profileSanCacheHits
                            /
                            sanRequests;

            System.out.printf(
                    "  Cache hit rate:          %.2f%%%n",
                    sanHitRate
            );
        }

        System.out.printf(
                "  Cached SAN entries:      %d%n",
                sanCache.size()
        );

        System.out.printf(
                "  Subtree statistics:      %.3f ms%n",
                nanosToMillis(
                        profileStatisticsNanos
                                / snapshots
                )
        );

        System.out.printf(
                "  Variation construction:  %.3f ms%n",
                nanosToMillis(
                        profileVariationNanos
                                / snapshots
                )
        );

        System.out.printf(
                "  Principal variation:     %.3f ms%n",
                nanosToMillis(
                        profilePrincipalVariationNanos
                                / snapshots
                )
        );

        System.out.printf(
                "  Other snapshot work:     %.3f ms%n",
                nanosToMillis(
                        profileOtherNanos
                                / snapshots
                )
        );

        System.out.println(
                "Statistics activity:"
        );

        System.out.printf(
                "  Calls:                   %d%n",
                profileStatisticsCalls
        );

        System.out.printf(
                "  Cache hits:              %d%n",
                profileStatisticsCacheHits
        );

        System.out.printf(
                "  Exact recomputations:    %d%n",
                profileStatisticsExactRecomputations
        );

        System.out.printf(
                "  DFS nodes visited:       %d%n",
                profileStatisticsVisitedNodes
        );


        if (profileStatisticsCalls > 0L) {

            double cacheHitRate =
                    100.0
                            *
                            profileStatisticsCacheHits
                            /
                            profileStatisticsCalls;

            System.out.printf(
                    "  Cache hit rate:          %.2f%%%n",
                    cacheHitRate
            );
        }


        System.out.printf(
                "  Categorized avg total:   %.3f ms%n",
                nanosToMillis(
                        categorized
                                / snapshots
                )
        );

        System.out.println(
                "============================"
        );

        System.out.println();
    }


    private void resetSnapshotProfiler() {

        profileSnapshotCount =
                0L;

        profileMoveSortNanos =
                0L;

        profileSanNanos =
                0L;

        profileSanCacheHits =
                0L;

        profileSanCacheMisses =
                0L;

        profileStatisticsNanos =
                0L;

        profileVariationNanos =
                0L;

        profilePrincipalVariationNanos =
                0L;

        profileOtherNanos =
                0L;

        profileStatisticsCalls =
                0L;

        profileStatisticsCacheHits =
                0L;

        profileStatisticsExactRecomputations =
                0L;

        profileStatisticsVisitedNodes =
                0L;
    }


    private double nanosToMillis(
            double nanos
    ) {

        return nanos
                /
                1_000_000.0;
    }

}

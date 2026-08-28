package main.java.chess.search;

import main.java.chess.model.Move;
import main.java.chess.model.Position;
import main.java.chess.model.PositionKey;
import main.java.chess.rules.MoveGenerator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PositionNode {

    private final PositionKey key;
    private final Position position;

    /*
     * Combined number of traversals
     * for each outgoing move.
     */
    private final Map<Move, Integer> edgeVisitCounts;

    /*
     * Traversals made specifically
     * by walkers.
     */
    private final Map<Move, Integer> walkerEdgeVisitCounts;

    /*
     * Traversals made specifically
     * by the node dovetail.
     */
    private final Map<Move, Integer> dovetailEdgeVisitCounts;

    /*
     * Unique graph edges.
     *
     * There should be at most one
     * SearchEdge for each legal Move
     * from this position.
     */
    private final List<SearchEdge> outgoingEdges;

    /*
     * Canonical parent nodes that currently have an outgoing
     * SearchEdge into this node.
     *
     * This is a Set because chess transpositions can cause many
     * different move sequences to reach the same canonical node,
     * while the same parent should only be recorded once.
     *
     * Incoming-node tracking lets later incremental propagation
     * move backward through the graph without scanning every node.
     */
    private final Set<PositionNode> incomingNodes;

    /*
     * Lazily computed immutable legal-move list.
     *
     * A PositionNode represents one fixed Position, so its legal
     * moves never change.  Once generated, this list can safely be
     * reused by expansion, coverage checks, scheduler checks, and
     * GUI statistics.
     *
     * null means "not computed yet".
     */
    private List<Move> cachedLegalMoves;

    /*
     * Number of times walkers have
     * entered this position.
     */
    private int visitCount;

    /*
     * Number of node-dovetail
     * expansion opportunities.
     */
    private int expansionCount;

    /*
     * Deterministic round-robin
     * outgoing-edge pointer.
     */
    private int nextEdgeIndex;

    /*
     * Static evaluation of this
     * exact position.
     */
    private int evaluation;

    /*
     * Propagated search value.
     */
    private int searchValue;

    private SearchOutcome searchOutcome;

    private int mateDistance;

    /*
     * Exact reachable-subtree statistics cache.
     *
     * PositionGraph still uses a visited set when recomputing because
     * transpositions make simple child-count addition incorrect.
     */
    private boolean subtreeStatisticsValid;
    private int cachedGeneratedPositions;
    private int cachedExploredPositions;
    private int cachedSolvedPositions;


    public PositionNode(
            Position position
    ) {

        this.position =
                position;

        this.key =
                position.createPositionKey();

        this.edgeVisitCounts =
                new HashMap<>();

        this.walkerEdgeVisitCounts =
                new HashMap<>();

        this.dovetailEdgeVisitCounts =
                new HashMap<>();

        this.outgoingEdges =
                new ArrayList<>();

        this.incomingNodes =
                new HashSet<>();

        this.cachedLegalMoves =
                null;

        this.visitCount = 0;

        this.expansionCount = 0;

        this.nextEdgeIndex = 0;

        this.evaluation = 0;

        this.searchValue = 0;

        this.searchOutcome =
                SearchOutcome.UNKNOWN;

        this.mateDistance =
                -1;

        this.subtreeStatisticsValid =
                false;

        this.cachedGeneratedPositions =
                0;

        this.cachedExploredPositions =
                0;

        this.cachedSolvedPositions =
                0;
    }


    // =========================
    // Position
    // =========================

    public PositionKey getKey() {
        return key;
    }

    public Position getPosition() {
        return position;
    }


    // =========================
    // Incoming graph nodes
    // =========================

    public Set<PositionNode> getIncomingNodes() {

        return Set.copyOf(
                incomingNodes
        );
    }


    public void addIncomingNode(
            PositionNode parent
    ) {

        if (parent == null) {

            throw new IllegalArgumentException(
                    "Incoming parent cannot be null."
            );
        }


        incomingNodes.add(
                parent
        );
    }


    public int getIncomingNodeCount() {

        return incomingNodes.size();
    }


    // =========================
    // Cached legal moves
    // =========================

    public boolean hasCachedLegalMoves() {

        return cachedLegalMoves != null;
    }


    public List<Move> getCachedLegalMoves() {

        return cachedLegalMoves;
    }


    public List<Move> getOrCacheLegalMoves(
            MoveGenerator moveGenerator
    ) {

        if (cachedLegalMoves != null) {

            return cachedLegalMoves;
        }


        if (moveGenerator == null) {

            throw new IllegalArgumentException(
                    "MoveGenerator cannot be null."
            );
        }


        List<Move> generatedMoves =
                new ArrayList<>(
                        moveGenerator.generateLegalMoves(
                                position
                        )
                );


        /*
         * Store the same deterministic order used by graph
         * expansion. Because the cached list is immutable, no later
         * caller can accidentally reorder or alter the node's legal
         * move universe.
         */
        generatedMoves.sort(
                Comparator.comparing(
                        Move::toString
                )
        );


        cachedLegalMoves =
                List.copyOf(
                        generatedMoves
                );


        return cachedLegalMoves;
    }


    public int getLegalMoveCount(
            MoveGenerator moveGenerator
    ) {

        return getOrCacheLegalMoves(
                moveGenerator
        ).size();
    }


    // =========================
    // Node visits
    // =========================

    public int getVisitCount() {
        return visitCount;
    }

    public void incrementVisitCount() {
        visitCount++;
    }


    // =========================
    // Combined edge visits
    // =========================

    public int getEdgeVisitCount(
            Move move
    ) {

        return edgeVisitCounts.getOrDefault(
                move,
                0
        );
    }

    public void incrementEdgeVisit(
            Move move
    ) {

        edgeVisitCounts.put(
                move,
                getEdgeVisitCount(move) + 1
        );
    }

    public Map<Move, Integer>
    getEdgeVisitCounts() {

        return edgeVisitCounts;
    }

    public int getExploredEdgeCount() {

        return edgeVisitCounts.size();
    }

    public int getTotalEdgeTraversals() {

        int total = 0;

        for (int count :
                edgeVisitCounts.values()) {

            total += count;
        }

        return total;
    }

    public int getMinExploredEdgeVisits() {

        if (edgeVisitCounts.isEmpty()) {
            return 0;
        }

        int min =
                Integer.MAX_VALUE;

        for (int count :
                edgeVisitCounts.values()) {

            min =
                    Math.min(
                            min,
                            count
                    );
        }

        return min;
    }

    public int getMaxExploredEdgeVisits() {

        int max = 0;

        for (int count :
                edgeVisitCounts.values()) {

            max =
                    Math.max(
                            max,
                            count
                    );
        }

        return max;
    }

    public boolean hasUnexploredEdges(
            MoveGenerator moveGenerator
    ) {

        for (Move move :
                getOrCacheLegalMoves(
                        moveGenerator
                )) {

            if (getEdgeVisitCount(move)
                    == 0) {

                return true;
            }
        }

        return false;
    }


    // =========================
    // Walker edge visits
    // =========================

    public int getWalkerEdgeVisitCount(
            Move move
    ) {

        return walkerEdgeVisitCounts
                .getOrDefault(
                        move,
                        0
                );
    }

    public void incrementWalkerEdgeVisit(
            Move move
    ) {

        walkerEdgeVisitCounts.put(
                move,
                getWalkerEdgeVisitCount(move)
                        + 1
        );

        incrementEdgeVisit(
                move
        );
    }


    // =========================
    // Dovetail edge visits
    // =========================

    public int getDovetailEdgeVisitCount(
            Move move
    ) {

        return dovetailEdgeVisitCounts
                .getOrDefault(
                        move,
                        0
                );
    }

    public void incrementDovetailEdgeVisit(
            Move move
    ) {

        dovetailEdgeVisitCounts.put(
                move,
                getDovetailEdgeVisitCount(move)
                        + 1
        );

        incrementEdgeVisit(
                move
        );
    }


    // =========================
    // Dovetail expansions
    // =========================

    public void incrementExpansionCount() {
        expansionCount++;
    }

    public int getExpansionCount() {
        return expansionCount;
    }


    // =========================
    // Deterministic edge index
    // =========================

    public int getNextEdgeIndex() {
        return nextEdgeIndex;
    }

    public void advanceEdgeIndex(
            int legalMoveCount
    ) {

        if (legalMoveCount == 0) {
            return;
        }

        nextEdgeIndex =
                (nextEdgeIndex + 1)
                        % legalMoveCount;
    }


    // =========================
    // Static evaluation
    // =========================

    public int getEvaluation() {
        return evaluation;
    }

    public void setEvaluation(
            int evaluation
    ) {

        this.evaluation =
                evaluation;
    }


    // =========================
    // Search value
    // =========================

    public int getSearchValue() {
        return searchValue;
    }

    public void setSearchValue(
            int searchValue
    ) {

        this.searchValue =
                searchValue;
    }


    // =========================
    // Graph edges
    // =========================

    public List<SearchEdge>
    getOutgoingEdges() {

        return outgoingEdges;
    }

    public void addOutgoingEdge(
            Move move,
            PositionNode target
    ) {

        /*
         * A move from one fixed position
         * represents exactly one logical
         * outgoing edge.
         *
         * Do NOT add another SearchEdge
         * merely because the same move was
         * traversed again.
         */
        for (SearchEdge edge :
                outgoingEdges) {

            if (edge.getMove()
                    .equals(move)) {

                return;
            }
        }

        outgoingEdges.add(
                new SearchEdge(
                        move,
                        target
                )
        );
    }


    // =========================
    // Local graph coverage
    // =========================

    public double getLocalCoverage(
            MoveGenerator moveGenerator
    ) {

        int legalMoveCount =
                getLegalMoveCount(
                        moveGenerator
                );

        /*
         * Terminal position:
         * no legal continuations remain.
         */
        if (legalMoveCount == 0) {
            return 1.0;
        }

        int knownEdgeCount =
                outgoingEdges.size();

        return (double) knownEdgeCount
                / legalMoveCount;
    }


    public boolean isFullyExpanded(
            MoveGenerator moveGenerator
    ) {

        int legalMoveCount =
                getLegalMoveCount(
                        moveGenerator
                );

        if (legalMoveCount == 0) {
            return true;
        }

        return outgoingEdges.size()
                == legalMoveCount;
    }

    public SearchOutcome getSearchOutcome() {
        return searchOutcome;
    }

    public void setSearchOutcome(
            SearchOutcome searchOutcome
    ) {
        this.searchOutcome =
                searchOutcome;
    }

    public int getMateDistance() {
        return mateDistance;
    }

    public void setMateDistance(
            int mateDistance
    ) {
        this.mateDistance =
                mateDistance;
    }

    public boolean hasProvenMate() {

        return searchOutcome
                == SearchOutcome.WHITE_WIN
                || searchOutcome
                == SearchOutcome.BLACK_WIN;
    }


    // =========================
    // Cached subtree statistics
    // =========================

    public boolean hasValidSubtreeStatistics() {

        return subtreeStatisticsValid;
    }


    public int getCachedGeneratedPositions() {

        return cachedGeneratedPositions;
    }


    public int getCachedExploredPositions() {

        return cachedExploredPositions;
    }


    public int getCachedSolvedPositions() {

        return cachedSolvedPositions;
    }


    public void cacheSubtreeStatistics(
            int generatedPositions,
            int exploredPositions,
            int solvedPositions
    ) {

        this.cachedGeneratedPositions =
                generatedPositions;

        this.cachedExploredPositions =
                exploredPositions;

        this.cachedSolvedPositions =
                solvedPositions;

        this.subtreeStatisticsValid =
                true;
    }


    public void invalidateSubtreeStatistics() {

        this.subtreeStatisticsValid =
                false;
    }

}

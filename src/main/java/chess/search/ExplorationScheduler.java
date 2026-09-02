package main.java.chess.search;

import main.java.chess.evaluation.PositionEvaluator;
import main.java.chess.model.Color;
import main.java.chess.model.GameState;
import main.java.chess.model.Move;
import main.java.chess.model.Position;
import main.java.chess.rules.GameStateEvaluator;
import main.java.chess.rules.MoveGenerator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * M69/M71 persistent Dovetail scheduler.
 *
 * DOVETAIL:
 *   line walkers follow the countable diagonal schedule
 *   A1, B1, A2, C1, B2, A3, ...
 *
 * HYBRID:
 *   the exact same line walkers plus persistent fair node/edge coverage.
 *
 * Both modes write into one canonical PositionGraph. Selected-line focus is
 * intentionally a Hybrid-only coverage bias; pure Dovetail never changes its
 * diagonal enumeration because a GUI line was selected.
 */
public class ExplorationScheduler {

    public static final String BUILD_ID =
            "M69-HYBRID-LINE-NODE-DOVETAIL-V1";

    public static final String MODE_BUILD_ID =
            "M71-SEPARATE-DOVETAIL-HYBRID-V1";

    public static final String COVERAGE_BUILD_ID =
            "M77-STRENGTH-AWARE-HYBRID-COVERAGE-V1";

    /**
     * DOVETAIL is the original diagonal line-walker search:
     *
     *     A1, B1, A2, C1, B2, A3, ...
     *
     * HYBRID keeps that exact walker schedule and adds the persistent
     * fair node/edge-coverage lane.
     */
    public enum Mode {
        DOVETAIL,
        HYBRID
    }

    // =========================================================
    // Search dependencies
    // =========================================================

    private final PositionGraph graph;
    private final MoveGenerator moveGenerator;
    private final GameStateEvaluator gameStateEvaluator;
    private final PositionEvaluator positionEvaluator;
    private final PositionNode walkerRoot;
    private final Mode mode;

    // =========================================================
    // Existing fair node-coverage lane
    // =========================================================

    private final Queue<PositionNode> globalQueue;
    private final Set<PositionNode> globalQueued;

    private final Queue<PositionNode> focusQueue;
    private final Set<PositionNode> focusQueued;
    private final Set<PositionNode> focusKnownNodes;

    private PositionNode focusNode;
    private int coverageTurnCounter;

    // =========================================================
    // Persistent line-walker lane
    // =========================================================

    private final List<DovetailWalker> walkers;

    /*
     * Diagonal d visits walker indices:
     *
     *   d, d-1, ..., 0
     *
     * therefore:
     *
     *   d=0: A1
     *   d=1: B1, A2
     *   d=2: C1, B2, A3
     */
    private int walkerDiagonal;
    private int walkerOffset;

    /*
     * Alternate successful work between the two lanes when both are live.
     * 0 = walker preference, 1 = coverage preference.
     */
    private int hybridTurnCounter;

    // =========================================================
    // Telemetry
    // =========================================================

    private long walkerSteps;
    private long coverageSteps;

    // =========================================================
    // Constructor
    // =========================================================

    /**
     * Backward-compatible constructor.
     *
     * M69 introduced the hybrid scheduler under this three-argument API.
     * Keeping HYBRID as the default preserves the existing M69/M70 regression
     * gates and any older callers. New GUI code selects the mode explicitly.
     */
    public ExplorationScheduler(
            PositionGraph graph,
            MoveGenerator moveGenerator,
            PositionNode root
    ) {
        this(
                graph,
                moveGenerator,
                root,
                Mode.HYBRID
        );
    }

    public ExplorationScheduler(
            PositionGraph graph,
            MoveGenerator moveGenerator,
            PositionNode root,
            Mode mode
    ) {
        if (graph == null) {
            throw new IllegalArgumentException(
                    "PositionGraph cannot be null."
            );
        }

        if (moveGenerator == null) {
            throw new IllegalArgumentException(
                    "MoveGenerator cannot be null."
            );
        }

        if (mode == null) {
            throw new IllegalArgumentException(
                    "Exploration mode cannot be null."
            );
        }

        this.graph = graph;
        this.moveGenerator = moveGenerator;
        this.gameStateEvaluator = new GameStateEvaluator();
        this.positionEvaluator = new PositionEvaluator();
        this.walkerRoot = root;
        this.mode = mode;

        this.globalQueue = new ArrayDeque<>();
        this.globalQueued = new HashSet<>();

        this.focusQueue = new ArrayDeque<>();
        this.focusQueued = new HashSet<>();
        this.focusKnownNodes = new HashSet<>();

        this.focusNode = null;
        this.coverageTurnCounter = 0;

        this.walkers = new ArrayList<>();
        this.walkerDiagonal = 0;
        this.walkerOffset = 0;
        this.hybridTurnCounter = 0;

        this.walkerSteps = 0L;
        this.coverageSteps = 0L;

        if (mode == Mode.HYBRID) {
            seedGlobalQueue(root);
        }
    }

    // =========================================================
    // Public diagonal preview helpers
    // =========================================================

    public static List<Integer> previewWalkerSchedule(
            int count
    ) {
        if (count <= 0) {
            return List.of();
        }

        List<Integer> result =
                new ArrayList<>(count);

        int diagonal = 0;
        int offset = 0;

        while (result.size() < count) {
            result.add(diagonal - offset);

            offset++;
            if (offset > diagonal) {
                diagonal++;
                offset = 0;
            }
        }

        return List.copyOf(result);
    }

    public static List<String> previewWalkerScheduleLabels(
            int count
    ) {
        if (count <= 0) {
            return List.of();
        }

        List<String> result =
                new ArrayList<>(count);

        int diagonal = 0;
        int offset = 0;

        while (result.size() < count) {
            int walkerIndex = diagonal - offset;
            int stepNumber = offset + 1;

            result.add(
                    DovetailWalker.labelFor(walkerIndex)
                            + stepNumber
            );

            offset++;
            if (offset > diagonal) {
                diagonal++;
                offset = 0;
            }
        }

        return List.copyOf(result);
    }

    // =========================================================
    // Seed coverage queue from already-generated graph
    // =========================================================

    public final void seedGlobalQueue(
            PositionNode root
    ) {
        globalQueue.clear();
        globalQueued.clear();

        if (root == null) {
            return;
        }

        Queue<PositionNode> traversal =
                new ArrayDeque<>();
        Set<PositionNode> visited =
                new HashSet<>();

        traversal.add(root);

        while (!traversal.isEmpty()) {
            PositionNode node = traversal.remove();

            if (!visited.add(node)) {
                continue;
            }

            if (node.hasUnexploredEdges(moveGenerator)) {
                addGlobal(node);
            }

            for (SearchEdge edge : node.getOutgoingEdges()) {
                PositionNode child = edge.getTarget();

                if (child != null && !visited.contains(child)) {
                    traversal.add(child);
                }
            }
        }
    }

    // =========================================================
    // Selected-line focus
    // =========================================================

    public void setFocus(
            PositionNode node
    ) {
        if (mode == Mode.DOVETAIL) {
            focusNode = null;
            focusQueue.clear();
            focusQueued.clear();
            focusKnownNodes.clear();
            return;
        }

        focusNode = node;

        focusQueue.clear();
        focusQueued.clear();
        focusKnownNodes.clear();

        if (focusNode == null) {
            return;
        }

        Queue<PositionNode> traversal =
                new ArrayDeque<>();
        traversal.add(focusNode);

        while (!traversal.isEmpty()) {
            PositionNode current = traversal.remove();

            if (!focusKnownNodes.add(current)) {
                continue;
            }

            if (current.hasUnexploredEdges(moveGenerator)) {
                addFocus(current);
            }

            for (SearchEdge edge : current.getOutgoingEdges()) {
                PositionNode child = edge.getTarget();

                if (child != null && !focusKnownNodes.contains(child)) {
                    traversal.add(child);
                }
            }
        }
    }

    public void clearFocus() {
        setFocus(null);
    }

    public PositionNode getFocusNode() {
        return focusNode;
    }

    // =========================================================
    // Advance hybrid search
    // =========================================================

    /**
     * Spend up to workUnits successful search steps.
     *
     * A successful walker move counts as one unit. A successful node-edge
     * expansion counts as one unit. Failed/retired walker schedule slots do
     * not count against the requested work budget.
     */
    public int advance(
            int workUnits
    ) {
        if (workUnits <= 0) {
            return 0;
        }

        if (mode == Mode.DOVETAIL) {
            return advanceDovetailOnly(workUnits);
        }

        int workDone = 0;
        int attempts = 0;
        int safety = Math.max(256, workUnits * 200);

        while (workDone < workUnits && attempts < safety) {
            attempts++;

            boolean preferWalker =
                    hybridTurnCounter % 2 == 0;
            hybridTurnCounter++;

            boolean progressed;

            if (preferWalker) {
                progressed = performWalkerWork();
                if (!progressed) {
                    progressed = performCoverageWork();
                }
            } else {
                progressed = performCoverageWork();
                if (!progressed) {
                    progressed = performWalkerWork();
                }
            }

            if (progressed) {
                workDone++;
                continue;
            }

            /*
             * Neither lane could make progress. If neither lane has future
             * work, this search root is exhausted/terminal.
             */
            if (!hasCoverageWork() && !walkerLaneCanEverWork()) {
                break;
            }
        }

        return workDone;
    }

    private int advanceDovetailOnly(
            int workUnits
    ) {
        int workDone = 0;
        int attempts = 0;
        int safety = Math.max(256, workUnits * 200);

        while (workDone < workUnits && attempts < safety) {
            attempts++;

            if (performWalkerWork()) {
                workDone++;
                continue;
            }

            if (!walkerLaneCanEverWork()) {
                break;
            }
        }

        return workDone;
    }


    // =========================================================
    // Walker lane
    // =========================================================

    private boolean performWalkerWork() {
        if (!walkerLaneCanEverWork()) {
            return false;
        }

        /*
         * A diagonal may contain retired walkers. Scan enough schedule slots
         * to reach a live/new walker without allowing dead slots to dominate
         * one GUI batch.
         */
        for (int scan = 0; scan < 128; scan++) {
            int walkerIndex = nextWalkerIndex();
            DovetailWalker walker = ensureWalker(walkerIndex);

            if (walker == null || walker.isRetired()) {
                continue;
            }

            DovetailWalker.StepResult result =
                    walker.step(
                            graph,
                            moveGenerator,
                            gameStateEvaluator
                    );

            if (!result.advanced()) {
                continue;
            }

            walkerSteps++;
            registerWalkerContinuation(result);
            return true;
        }

        return false;
    }

    private int nextWalkerIndex() {
        int result = walkerDiagonal - walkerOffset;

        walkerOffset++;
        if (walkerOffset > walkerDiagonal) {
            walkerDiagonal++;
            walkerOffset = 0;
        }

        return result;
    }

    private DovetailWalker ensureWalker(
            int index
    ) {
        if (walkerRoot == null || !walkerLaneCanEverWork()) {
            return null;
        }

        while (walkers.size() <= index) {
            int newIndex = walkers.size();

            long seed =
                    0xD0E7A11L
                            ^ (0x9E3779B97F4A7C15L * (newIndex + 1L));

            walkers.add(
                    new DovetailWalker(
                            newIndex,
                            walkerRoot,
                            seed
                    )
            );
        }

        return walkers.get(index);
    }

    private boolean walkerLaneCanEverWork() {
        if (walkerRoot == null) {
            return false;
        }

        GameState state =
                gameStateEvaluator.evaluate(
                        walkerRoot.getPosition()
                );

        if (isTerminal(state)) {
            return false;
        }

        return !walkerRoot
                .getOrCacheLegalMoves(moveGenerator)
                .isEmpty();
    }

    private void registerWalkerContinuation(
            DovetailWalker.StepResult result
    ) {
        if (mode == Mode.DOVETAIL) {
            return;
        }

        PositionNode from = result.from();
        PositionNode to = result.to();

        if (from != null && from.hasUnexploredEdges(moveGenerator)) {
            addGlobal(from);
        }

        if (to != null && to.hasUnexploredEdges(moveGenerator)) {
            addGlobal(to);
        }

        boolean fromInFocus =
                from != null && focusKnownNodes.contains(from);

        if (fromInFocus && to != null) {
            focusKnownNodes.add(to);

            if (from.hasUnexploredEdges(moveGenerator)) {
                addFocus(from);
            }

            if (to.hasUnexploredEdges(moveGenerator)) {
                addFocus(to);
            }
        }
    }

    // =========================================================
    // Existing fair coverage lane
    // =========================================================

    private boolean performCoverageWork() {
        PositionNode node =
                pollCoverageNode();

        if (node == null) {
            return false;
        }

        /*
         * M77 keeps node selection completely fair. The global/focus queues
         * still decide WHICH node receives this coverage turn.
         *
         * Strength is introduced only one level lower: once a fair node has
         * been selected, choose the best currently-unexplored legal move for
         * the side to move.
         *
         * This is intentionally not a best-first node queue. A strategically
         * attractive region therefore cannot starve the rest of the graph.
         */
        Move chosenMove =
                chooseStrengthAwareCoverageMove(
                        node
                );

        if (chosenMove == null) {
            return false;
        }

        boolean expanded =
                expandCoverageMove(
                        node,
                        chosenMove
                );

        if (!expanded) {
            return false;
        }

        coverageSteps++;

        if (node.hasUnexploredEdges(moveGenerator)) {
            addGlobal(node);
        }

        boolean nodeInFocus =
                focusKnownNodes.contains(node);

        if (nodeInFocus
                && node.hasUnexploredEdges(moveGenerator)) {
            addFocus(node);
        }

        for (SearchEdge edge :
                node.getOutgoingEdges()) {

            PositionNode child =
                    edge.getTarget();

            if (child == null) {
                continue;
            }

            if (child.hasUnexploredEdges(moveGenerator)) {
                addGlobal(child);
            }

            if (nodeInFocus) {
                focusKnownNodes.add(child);

                if (child.hasUnexploredEdges(moveGenerator)) {
                    addFocus(child);
                }
            }
        }

        return true;
    }


    /**
     * Select the strongest still-unvisited move at this node while leaving the
     * scheduler's node fairness untouched.
     *
     * PositionEvaluator values are White-perspective values, so Black simply
     * reverses the comparison. Ties are broken deterministically by Move text
     * to keep verification runs reproducible.
     */
    private Move chooseStrengthAwareCoverageMove(
            PositionNode node
    ) {
        if (node == null) {
            return null;
        }

        List<Move> legalMoves =
                node.getOrCacheLegalMoves(
                        moveGenerator
                );

        if (legalMoves.isEmpty()) {
            return null;
        }

        Color sideToMove =
                node.getPosition()
                        .getSideToMove();

        Move bestMove =
                null;

        int bestSideScore =
                Integer.MIN_VALUE;

        for (Move move :
                legalMoves) {

            /*
             * Coverage means first exposure of an edge. Walker traversals and
             * earlier coverage traversals both count, so never spend a coverage
             * turn revisiting an already-visited move while a fresh one exists.
             */
            if (node.getEdgeVisitCount(move)
                    != 0) {
                continue;
            }

            Position childPosition =
                    node.getPosition()
                            .makeMove(
                                    move
                            );

            int whitePerspectiveScore =
                    positionEvaluator.evaluate(
                            childPosition
                    );

            int sideScore =
                    sideToMove == Color.WHITE
                            ? whitePerspectiveScore
                            : -whitePerspectiveScore;

            if (bestMove == null
                    || sideScore > bestSideScore
                    || (
                    sideScore == bestSideScore
                            && move.toString()
                            .compareTo(
                                    bestMove.toString()
                            ) < 0
            )) {

                bestMove =
                        move;

                bestSideScore =
                        sideScore;
            }
        }

        return bestMove;
    }


    /**
     * Commit one specifically selected Hybrid coverage move without changing
     * PositionGraph's general-purpose FIFO expansion API.
     *
     * ensureManualContinuation(...) owns canonical graph mutation and dirty
     * propagation. The scheduler records only the coverage-lane accounting.
     */
    private boolean expandCoverageMove(
            PositionNode node,
            Move move
    ) {
        if (node == null
                || move == null) {
            return false;
        }

        Position parentPosition =
                node.getPosition();

        Position childPosition =
                parentPosition.makeMove(
                        move
                );

        if (!graph.ensureManualContinuation(
                parentPosition,
                childPosition
        )) {
            return false;
        }

        node.incrementExpansionCount();

        node.incrementDovetailEdgeVisit(
                move
        );

        /*
         * Preserve the historical expansion cursor as telemetry/compatibility
         * state even though M77's Hybrid coverage move is score-selected.
         */
        node.advanceEdgeIndex(
                node.getOrCacheLegalMoves(
                        moveGenerator
                ).size()
        );

        PositionNode child =
                graph.getOrCreateNode(
                        childPosition
                );

        child.incrementVisitCount();

        return true;
    }

    private PositionNode pollCoverageNode() {
        boolean focusTurn =
                focusNode != null
                        && coverageTurnCounter % 4 == 3;
        coverageTurnCounter++;

        PositionNode node;

        if (focusTurn) {
            node = pollExpandable(focusQueue, focusQueued);

            if (node == null) {
                node = pollExpandable(globalQueue, globalQueued);
            }
        } else {
            node = pollExpandable(globalQueue, globalQueued);

            if (node == null) {
                node = pollExpandable(focusQueue, focusQueued);
            }
        }

        if (node != null) {
            return node;
        }

        refreshGlobalQueue();
        if (focusNode != null) {
            refreshFocusQueue();
        }

        node = pollExpandable(globalQueue, globalQueued);

        if (node == null && focusNode != null) {
            node = pollExpandable(focusQueue, focusQueued);
        }

        return node;
    }

    // =========================================================
    // Work availability
    // =========================================================

    /**
     * Historical ChessEngine API name. In M69 this means either global
     * coverage work OR global line-walker work remains.
     */
    public boolean hasGlobalWork() {
        if (mode == Mode.DOVETAIL) {
            return walkerLaneCanEverWork();
        }

        return hasCoverageWork() || walkerLaneCanEverWork();
    }

    public boolean hasFocusWork() {
        if (mode == Mode.DOVETAIL) {
            return false;
        }

        if (focusNode == null) {
            return false;
        }

        if (hasExpandableNode(focusQueue)) {
            return true;
        }

        refreshFocusQueue();
        return hasExpandableNode(focusQueue);
    }

    private boolean hasCoverageWork() {
        if (hasExpandableNode(globalQueue)) {
            return true;
        }

        refreshGlobalQueue();
        return hasExpandableNode(globalQueue);
    }

    // =========================================================
    // Queue helpers
    // =========================================================

    private void addGlobal(
            PositionNode node
    ) {
        if (node == null || !node.hasUnexploredEdges(moveGenerator)) {
            return;
        }

        if (globalQueued.add(node)) {
            globalQueue.add(node);
        }
    }

    private void addFocus(
            PositionNode node
    ) {
        if (node == null || !node.hasUnexploredEdges(moveGenerator)) {
            return;
        }

        if (focusQueued.add(node)) {
            focusQueue.add(node);
        }
    }

    private PositionNode pollExpandable(
            Queue<PositionNode> queue,
            Set<PositionNode> queued
    ) {
        while (!queue.isEmpty()) {
            PositionNode node = queue.remove();
            queued.remove(node);

            if (node.hasUnexploredEdges(moveGenerator)) {
                return node;
            }
        }

        return null;
    }

    private boolean hasExpandableNode(
            Queue<PositionNode> queue
    ) {
        for (PositionNode node : queue) {
            if (node.hasUnexploredEdges(moveGenerator)) {
                return true;
            }
        }

        return false;
    }

    // =========================================================
    // Queue refresh
    // =========================================================

    private void refreshGlobalQueue() {
        for (PositionNode node : graph.getNodes()) {
            if (node.hasUnexploredEdges(moveGenerator)) {
                addGlobal(node);
            }
        }
    }

    private void refreshFocusQueue() {
        if (focusNode == null) {
            return;
        }

        Queue<PositionNode> traversal =
                new ArrayDeque<>();
        Set<PositionNode> visited =
                new HashSet<>();

        traversal.add(focusNode);

        while (!traversal.isEmpty()) {
            PositionNode node = traversal.remove();

            if (!visited.add(node)) {
                continue;
            }

            focusKnownNodes.add(node);

            if (node.hasUnexploredEdges(moveGenerator)) {
                addFocus(node);
            }

            for (SearchEdge edge : node.getOutgoingEdges()) {
                PositionNode child = edge.getTarget();

                if (child != null && !visited.contains(child)) {
                    traversal.add(child);
                }
            }
        }
    }

    // =========================================================
    // Telemetry / verification access
    // =========================================================

    public Mode getMode() {
        return mode;
    }

    public long getWalkerSteps() {
        return walkerSteps;
    }

    public long getCoverageSteps() {
        return coverageSteps;
    }

    public int getWalkerCount() {
        return walkers.size();
    }

    public int getActiveWalkerCount() {
        int active = 0;

        for (DovetailWalker walker : walkers) {
            if (!walker.isRetired()) {
                active++;
            }
        }

        return active;
    }

    public int getMaximumWalkerDepth() {
        int maximum = 0;

        for (DovetailWalker walker : walkers) {
            maximum = Math.max(maximum, walker.getDepth());
        }

        return maximum;
    }

    public long getWalkerPathRevisits() {
        long total = 0L;

        for (DovetailWalker walker : walkers) {
            total += walker.getPathRevisits();
        }

        return total;
    }

    public DovetailTelemetry telemetry() {
        return new DovetailTelemetry(
                walkerSteps,
                coverageSteps,
                getWalkerCount(),
                getActiveWalkerCount(),
                getMaximumWalkerDepth(),
                graph.size(),
                getWalkerPathRevisits()
        );
    }

    public int getGlobalQueueSize() {
        return globalQueue.size();
    }

    public int getFocusQueueSize() {
        return focusQueue.size();
    }

    public record DovetailTelemetry(
            long walkerSteps,
            long coverageSteps,
            int walkers,
            int activeWalkers,
            int maximumWalkerDepth,
            int graphNodes,
            long walkerPathRevisits
    ) {
    }

    // =========================================================
    // Terminal helper
    // =========================================================

    private static boolean isTerminal(
            GameState state
    ) {
        return switch (state) {
            case CHECKMATE,
                 STALEMATE,
                 DRAW_75_MOVE,
                 DRAW_FIVEFOLD_REPETITION -> true;
            case CHECK,
                 ONGOING -> false;
        };
    }
}

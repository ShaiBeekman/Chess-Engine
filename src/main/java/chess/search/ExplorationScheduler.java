package main.java.chess.search;

import main.java.chess.model.Position;
import main.java.chess.rules.MoveGenerator;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;


/**
 * Persistent fair scheduler for incremental graph expansion.
 *
 * The important difference from repeatedly searching from the root is
 * that this object REMEMBERS where exploration left off between GUI
 * refreshes.
 *
 * Global work is always preserved.  Selecting a line adds extra turns
 * for the selected subtree, but never stops the global dovetail.
 */
public class ExplorationScheduler {

    // =========================================================
    // Search dependencies
    // =========================================================

    private final PositionGraph graph;

    private final MoveGenerator moveGenerator;


    // =========================================================
    // Global dovetail queue
    // =========================================================

    private final Queue<PositionNode> globalQueue;

    private final Set<PositionNode> globalQueued;


    // =========================================================
    // Optional selected-subtree bias
    // =========================================================

    private final Queue<PositionNode> focusQueue;

    private final Set<PositionNode> focusQueued;

    /*
     * Every position known to be reachable from the selected
     * focus node.
     *
     * If a globally scheduled expansion occurs inside this set,
     * its newly generated child is also part of the focus subtree.
     */
    private final Set<PositionNode> focusKnownNodes;

    private PositionNode focusNode;


    // =========================================================
    // Scheduling state
    // =========================================================

    /*
     * 0,1,2 = global turn
     * 3     = focus bonus turn when available
     *
     * Therefore selection biases search without starving the rest
     * of the graph.
     */
    private int turnCounter;


    // =========================================================
    // Constructor
    // =========================================================

    public ExplorationScheduler(
            PositionGraph graph,
            MoveGenerator moveGenerator,
            PositionNode root
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


        this.graph =
                graph;


        this.moveGenerator =
                moveGenerator;


        this.globalQueue =
                new ArrayDeque<>();


        this.globalQueued =
                new HashSet<>();


        this.focusQueue =
                new ArrayDeque<>();


        this.focusQueued =
                new HashSet<>();


        this.focusKnownNodes =
                new HashSet<>();


        this.focusNode =
                null;


        this.turnCounter =
                0;


        seedGlobalQueue(
                root
        );
    }


    // =========================================================
    // Seed global queue from already-generated graph
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


        traversal.add(
                root
        );


        while (!traversal.isEmpty()) {

            PositionNode node =
                    traversal.remove();


            if (!visited.add(
                    node
            )) {

                continue;
            }


            if (node.hasUnexploredEdges(
                    moveGenerator
            )) {

                addGlobal(
                        node
                );
            }


            for (SearchEdge edge :
                    node.getOutgoingEdges()) {

                PositionNode child =
                        edge.getTarget();


                if (!visited.contains(
                        child
                )) {

                    traversal.add(
                            child
                    );
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

        focusNode =
                node;


        focusQueue.clear();

        focusQueued.clear();

        focusKnownNodes.clear();


        if (focusNode == null) {

            return;
        }


        Queue<PositionNode> traversal =
                new ArrayDeque<>();


        traversal.add(
                focusNode
        );


        while (!traversal.isEmpty()) {

            PositionNode current =
                    traversal.remove();


            if (!focusKnownNodes.add(
                    current
            )) {

                continue;
            }


            if (current.hasUnexploredEdges(
                    moveGenerator
            )) {

                addFocus(
                        current
                );
            }


            for (SearchEdge edge :
                    current.getOutgoingEdges()) {

                PositionNode child =
                        edge.getTarget();


                if (!focusKnownNodes.contains(
                        child
                )) {

                    traversal.add(
                            child
                    );
                }
            }
        }
    }


    public void clearFocus() {

        setFocus(
                null
        );
    }


    public PositionNode getFocusNode() {

        return focusNode;
    }


    // =========================================================
    // Advance persistent dovetail
    // =========================================================

    /*
     * A work unit is one successful newly attempted graph-edge
     * expansion.
     *
     * Because the queues persist, the next call continues exactly
     * where this call stopped.
     */
    public int advance(
            int workUnits
    ) {

        if (workUnits <= 0) {

            return 0;
        }


        int workDone =
                0;


        int safety =
                Math.max(
                        100,
                        workUnits * 100
                );


        int attempts =
                0;


        while (workDone < workUnits
                &&
                attempts < safety) {

            attempts++;


            boolean focusTurn =
                    focusNode != null
                            &&
                            turnCounter % 4 == 3;


            turnCounter++;


            PositionNode node;


            if (focusTurn) {

                node =
                        pollExpandable(
                                focusQueue,
                                focusQueued
                        );


                /*
                 * If the focus queue temporarily has no work,
                 * the global dovetail still receives the turn.
                 */
                if (node == null) {

                    node =
                            pollExpandable(
                                    globalQueue,
                                    globalQueued
                            );
                }


            } else {

                node =
                        pollExpandable(
                                globalQueue,
                                globalQueued
                        );


                /*
                 * If global queue is momentarily empty but focus
                 * still contains expandable positions, use them.
                 */
                if (node == null) {

                    node =
                            pollExpandable(
                                    focusQueue,
                                    focusQueued
                            );
                }
            }


            if (node == null) {

                /*
                 * Queues may contain no work because all currently
                 * queued nodes just became complete.
                 *
                 * Re-scan the graph once to catch any expandable
                 * nodes that were added through transpositions.
                 */
                refreshGlobalQueue();


                if (focusNode != null) {

                    refreshFocusQueue();
                }


                node =
                        pollExpandable(
                                globalQueue,
                                globalQueued
                        );


                if (node == null
                        &&
                        focusNode != null) {

                    node =
                            pollExpandable(
                                    focusQueue,
                                    focusQueued
                            );
                }


                if (node == null) {

                    break;
                }
            }


            PositionNode beforeLastChild =
                    lastOutgoingTarget(
                            node
                    );


            boolean expanded =
                    graph.expandOneEdge(
                            node,
                            moveGenerator
                    );


            PositionNode afterLastChild =
                    lastOutgoingTarget(
                            node
                    );


            if (expanded) {

                workDone++;
            }


            /*
             * Keep the unfinished node in the global dovetail.
             */
            if (node.hasUnexploredEdges(
                    moveGenerator
            )) {

                addGlobal(
                        node
                );
            }


            /*
             * If the node belongs to the selected subtree,
             * it also remains eligible for focus bonus turns.
             */
            boolean nodeInFocus =
                    focusKnownNodes.contains(
                            node
                    );


            if (nodeInFocus
                    &&
                    node.hasUnexploredEdges(
                            moveGenerator
                    )) {

                addFocus(
                        node
                );
            }


            /*
             * expandOneEdge may add a new unique child edge.
             *
             * Looking at all outgoing children is robust even
             * when a transposition means the canonical child node
             * already existed elsewhere in the graph.
             */
            for (SearchEdge edge :
                    node.getOutgoingEdges()) {

                PositionNode child =
                        edge.getTarget();


                if (child.hasUnexploredEdges(
                        moveGenerator
                )) {

                    addGlobal(
                            child
                    );
                }


                if (nodeInFocus) {

                    focusKnownNodes.add(
                            child
                    );


                    if (child.hasUnexploredEdges(
                            moveGenerator
                    )) {

                        addFocus(
                                child
                        );
                    }
                }
            }
        }


        return workDone;
    }


    // =========================================================
    // Work availability
    // =========================================================

    public boolean hasGlobalWork() {

        if (hasExpandableNode(
                globalQueue
        )) {

            return true;
        }


        refreshGlobalQueue();


        return hasExpandableNode(
                globalQueue
        );
    }


    public boolean hasFocusWork() {

        if (focusNode == null) {

            return false;
        }


        if (hasExpandableNode(
                focusQueue
        )) {

            return true;
        }


        refreshFocusQueue();


        return hasExpandableNode(
                focusQueue
        );
    }


    // =========================================================
    // Queue helpers
    // =========================================================

    private void addGlobal(
            PositionNode node
    ) {

        if (node == null) {

            return;
        }


        if (!node.hasUnexploredEdges(
                moveGenerator
        )) {

            return;
        }


        if (globalQueued.add(
                node
        )) {

            globalQueue.add(
                    node
            );
        }
    }


    private void addFocus(
            PositionNode node
    ) {

        if (node == null) {

            return;
        }


        if (!node.hasUnexploredEdges(
                moveGenerator
        )) {

            return;
        }


        if (focusQueued.add(
                node
        )) {

            focusQueue.add(
                    node
            );
        }
    }


    private PositionNode pollExpandable(
            Queue<PositionNode> queue,
            Set<PositionNode> queued
    ) {

        while (!queue.isEmpty()) {

            PositionNode node =
                    queue.remove();


            queued.remove(
                    node
            );


            if (node.hasUnexploredEdges(
                    moveGenerator
            )) {

                return node;
            }
        }


        return null;
    }


    private boolean hasExpandableNode(
            Queue<PositionNode> queue
    ) {

        for (PositionNode node :
                queue) {

            if (node.hasUnexploredEdges(
                    moveGenerator
            )) {

                return true;
            }
        }


        return false;
    }


    // =========================================================
    // Queue refresh
    // =========================================================

    private void refreshGlobalQueue() {

        for (PositionNode node :
                graph.getNodes()) {

            if (node.hasUnexploredEdges(
                    moveGenerator
            )) {

                addGlobal(
                        node
                );
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


        traversal.add(
                focusNode
        );


        while (!traversal.isEmpty()) {

            PositionNode node =
                    traversal.remove();


            if (!visited.add(
                    node
            )) {

                continue;
            }


            focusKnownNodes.add(
                    node
            );


            if (node.hasUnexploredEdges(
                    moveGenerator
            )) {

                addFocus(
                        node
                );
            }


            for (SearchEdge edge :
                    node.getOutgoingEdges()) {

                PositionNode child =
                        edge.getTarget();


                if (!visited.contains(
                        child
                )) {

                    traversal.add(
                            child
                    );
                }
            }
        }
    }


    // =========================================================
    // Small debug/access helpers
    // =========================================================

    public int getGlobalQueueSize() {

        return globalQueue.size();
    }


    public int getFocusQueueSize() {

        return focusQueue.size();
    }


    /*
     * Kept private; useful while debugging edge addition.
     */
    private PositionNode lastOutgoingTarget(
            PositionNode node
    ) {

        PositionNode result =
                null;


        for (SearchEdge edge :
                node.getOutgoingEdges()) {

            result =
                    edge.getTarget();
        }


        return result;
    }
}

package main.java.chess.search;

import main.java.chess.model.Move;
import main.java.chess.rules.MoveGenerator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class NodeDovetailScheduler {

    private final PositionGraph graph;
    private final SearchMetrics metrics;

    private final MoveGenerator moveGenerator;

    private final List<PositionNode> enumeration;

    private int round;

    public NodeDovetailScheduler(
            PositionGraph graph,
            SearchMetrics metrics
    ) {

        this.graph = graph;
        this.metrics = metrics;

        this.moveGenerator =
                new MoveGenerator();

        this.enumeration =
                new ArrayList<>();

        this.round = 0;
    }

    public void synchronizeEnumeration() {

        for (PositionNode node :
                graph.getNodes()) {

            if (!enumeration.contains(node)) {
                enumeration.add(node);
            }
        }
    }

    public void runNextRound() {

        synchronizeEnumeration();

        if (enumeration.isEmpty()) {
            return;
        }

        round++;

        /*
         * Only nodes already eligible at the
         * start of this round get work.
         *
         * Newly discovered nodes are added after
         * the round finishes.
         */
        int eligibleNodeCount =
                Math.min(
                        round,
                        enumeration.size()
                );

        /*
         * Node staircase:
         *
         * Round 1:
         * P0
         *
         * Round 2:
         * P1 P0
         *
         * Round 3:
         * P2 P1 P0
         *
         * Round 4:
         * P3 P2 P1 P0
         */
        for (int i = eligibleNodeCount - 1;
             i >= 0;
             i--) {

            PositionNode node =
                    enumeration.get(i);

            int graphSizeBefore =
                    graph.size();

            boolean expanded =
                    graph.expandOneEdge(
                            node,
                            moveGenerator
                    );

            int graphSizeAfter =
                    graph.size();

            if (expanded) {

                boolean discoveredNewPosition =
                        graphSizeAfter
                                > graphSizeBefore;

                metrics.recordNodeDovetailStep(
                        discoveredNewPosition
                );
            }
        }

        /*
         * Include any positions discovered
         * during this round in the enumeration
         * for future rounds.
         */
        synchronizeEnumeration();
    }

    public int getRound() {
        return round;
    }

    public int getEnumeratedNodeCount() {
        return enumeration.size();
    }

    public void printFairnessReport() {

        if (enumeration.isEmpty()) {
            return;
        }

        int minimumExpansions =
                Integer.MAX_VALUE;

        int maximumExpansions =
                Integer.MIN_VALUE;

        long totalExpansions = 0;

        int nodesWithZeroExpansions = 0;

        for (PositionNode node : enumeration) {

            int expansions =
                    node.getExpansionCount();

            totalExpansions += expansions;

            if (expansions == 0) {
                nodesWithZeroExpansions++;
            }

            minimumExpansions =
                    Math.min(
                            minimumExpansions,
                            expansions
                    );

            maximumExpansions =
                    Math.max(
                            maximumExpansions,
                            expansions
                    );
        }

        double averageExpansions =
                (double) totalExpansions
                        / enumeration.size();

        System.out.println();
        System.out.println("NODE FAIRNESS");

        System.out.println(
                "Enumerated nodes: "
                        + enumeration.size()
        );

        System.out.println(
                "Nodes with zero expansions: "
                        + nodesWithZeroExpansions
        );

        System.out.println(
                "Minimum expansions per node: "
                        + minimumExpansions
        );

        System.out.println(
                "Maximum expansions per node: "
                        + maximumExpansions
        );

        System.out.printf(
                "Average expansions per node: %.2f%n",
                averageExpansions
        );
    }

    public void printFirstNodeExpansions(
            int count
    ) {

        System.out.println();
        System.out.println(
                "FIRST NODE EXPANSIONS"
        );

        int limit =
                Math.min(
                        count,
                        enumeration.size()
                );

        for (int i = 0;
             i < limit;
             i++) {

            PositionNode node =
                    enumeration.get(i);

            System.out.println(
                    "P"
                            + i
                            + ": "
                            + node.getExpansionCount()
            );
        }
    }

    public void printFirstNodeEdgeCounts(
            int nodeIndex
    ) {

        if (nodeIndex < 0
                || nodeIndex >= enumeration.size()) {
            return;
        }

        PositionNode node =
                enumeration.get(nodeIndex);

        MoveGenerator moveGenerator =
                new MoveGenerator();

        List<Move> legalMoves =
                moveGenerator.generateLegalMoves(
                        node.getPosition()
                );

        legalMoves.sort(
                Comparator.comparing(
                        Move::toString
                )
        );

        System.out.println();
        System.out.println(
                "P"
                        + nodeIndex
                        + " EDGE COUNTS"
        );

        for (Move move : legalMoves) {

            System.out.println(
                    move
                            + " | dovetail "
                            + node.getDovetailEdgeVisitCount(move)
                            + " | walker "
                            + node.getWalkerEdgeVisitCount(move)
                            + " | total "
                            + node.getEdgeVisitCount(move)
            );
        }
    }

    public void printNodeOutgoingEdges(
            int nodeIndex
    ) {

        if (nodeIndex < 0
                || nodeIndex >= enumeration.size()) {
            return;
        }

        PositionNode node =
                enumeration.get(nodeIndex);

        System.out.println();
        System.out.println(
                "P"
                        + nodeIndex
                        + " OUTGOING EDGES"
        );

        for (SearchEdge edge :
                node.getOutgoingEdges()) {

            System.out.println(
                    edge.getMove()
                            + " -> evaluation "
                            + edge.getTarget()
                            .getEvaluation()
            );
        }
    }

    public void printNodeCoverage(
            int nodeIndex
    ) {

        if (nodeIndex < 0
                || nodeIndex >= enumeration.size()) {
            return;
        }

        PositionNode node =
                enumeration.get(nodeIndex);

        MoveGenerator moveGenerator =
                new MoveGenerator();

        int legalMoves =
                moveGenerator
                        .generateLegalMoves(
                                node.getPosition()
                        )
                        .size();

        int knownEdges =
                node.getOutgoingEdges()
                        .size();

        double coverage =
                node.getLocalCoverage(
                        moveGenerator
                );

        System.out.println();
        System.out.println(
                "P"
                        + nodeIndex
                        + " COVERAGE"
        );

        System.out.println(
                "Legal moves: "
                        + legalMoves
        );

        System.out.println(
                "Known outgoing edges: "
                        + knownEdges
        );

        System.out.printf(
                "Local coverage: %.2f%%%n",
                coverage * 100.0
        );

        System.out.println(
                "Fully expanded: "
                        + node.isFullyExpanded(
                        moveGenerator
                )
        );
    }

    public void printChildCoverage(
            int nodeIndex
    ) {

        if (nodeIndex < 0
                || nodeIndex >= enumeration.size()) {
            return;
        }

        PositionNode node =
                enumeration.get(nodeIndex);

        MoveGenerator moveGenerator =
                new MoveGenerator();

        System.out.println();
        System.out.println(
                "P"
                        + nodeIndex
                        + " CHILD COVERAGE"
        );

        for (SearchEdge edge :
                node.getOutgoingEdges()) {

            PositionNode child =
                    edge.getTarget();

            int legalMoves =
                    moveGenerator
                            .generateLegalMoves(
                                    child.getPosition()
                            )
                            .size();

            int knownEdges =
                    child.getOutgoingEdges()
                            .size();

            double coverage =
                    child.getLocalCoverage(
                            moveGenerator
                    );

            System.out.printf(
                    "%s"
                            + " | legal "
                            + legalMoves
                            + " | known "
                            + knownEdges
                            + " | %.2f%%"
                            + " | full "
                            + child.isFullyExpanded(
                            moveGenerator
                    )
                            + "%n",
                    edge.getMove(),
                    coverage * 100.0
            );
        }
    }

}
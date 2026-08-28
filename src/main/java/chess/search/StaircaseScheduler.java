package main.java.chess.search;

import main.java.chess.model.Position;
import main.java.chess.rules.MoveGenerator;

import java.util.ArrayList;
import java.util.List;

public class StaircaseScheduler {

    private final Position startingPosition;
    private final PositionGraph graph;

    private final List<Walker> walkers;
    private final SearchMetrics metrics;

    private int nextWalkerId;
    private int round;

    public StaircaseScheduler(
            Position startingPosition,
            PositionGraph graph
    ) {
        this.startingPosition = startingPosition;
        this.graph = graph;

        this.walkers = new ArrayList<>();
        this.metrics = new SearchMetrics();

        this.nextWalkerId = 1;
        this.round = 0;
    }

    public void runNextRound() {

        round++;

        /*
         * Every new walker starts from
         * the original chess position.
         *
         * The NodeDovetailScheduler is now
         * responsible for revisiting later
         * graph positions.
         */
        Walker newWalker =
                new Walker(
                        nextWalkerId,
                        startingPosition,
                        graph
                );

        nextWalkerId++;

        walkers.add(newWalker);

        System.out.println();
        System.out.println(
                "ROUND " + round
        );

        /*
         * Run newest walker -> oldest walker.
         *
         * Round 1:
         * A
         *
         * Round 2:
         * B A
         *
         * Round 3:
         * C B A
         *
         * Round 4:
         * D C B A
         */
        for (int i = walkers.size() - 1;
             i >= 0;
             i--) {

            Walker walker =
                    walkers.get(i);

            int graphSizeBefore =
                    graph.size();

            boolean moved =
                    walker.step();

            int graphSizeAfter =
                    graph.size();

            String name =
                    walkerName(
                            walker.getId()
                    );

            if (moved) {

                boolean discoveredNewPosition =
                        graphSizeAfter
                                > graphSizeBefore;

                metrics.recordWalkerStep(
                        discoveredNewPosition,
                        walker.getDepth()
                );

                System.out.println(
                        name
                                + " | depth "
                                + walker.getDepth()
                                + " | graph "
                                + graph.size()
                );

            } else {

                System.out.println(
                        name
                                + " | TERMINAL"
                                + " | depth "
                                + walker.getDepth()
                );
            }
        }
    }

    private String walkerName(
            int id
    ) {

        if (id >= 1 && id <= 26) {

            char letter =
                    (char) ('A' + id - 1);

            return String.valueOf(letter);
        }

        return "W" + id;
    }

    public int getRound() {
        return round;
    }

    public int getWalkerCount() {
        return walkers.size();
    }

    public SearchMetrics getMetrics() {
        return metrics;
    }

    public void printMetrics() {

        System.out.println();
        System.out.println("SEARCH METRICS");

        System.out.println(
                "Rounds: "
                        + round
        );

        System.out.println(
                "Walkers: "
                        + walkers.size()
        );

        System.out.println(
                "Walker steps: "
                        + metrics.getWalkerSteps()
        );

        System.out.println(
                "Node-dovetail steps: "
                        + metrics.getNodeDovetailSteps()
        );

        System.out.println(
                "Total search steps: "
                        + metrics.getTotalSteps()
        );

        System.out.println(
                "New positions: "
                        + metrics.getNewPositions()
        );

        System.out.println(
                "Revisits: "
                        + metrics.getRevisits()
        );

        System.out.printf(
                "Discovery rate: %.2f%%%n",
                metrics.getDiscoveryRate()
                        * 100.0
        );

        System.out.println(
                "Maximum walker depth: "
                        + metrics.getMaxDepth()
        );

        System.out.println(
                "Unique positions in graph: "
                        + graph.size()
        );
    }

    public void printEdgeCoverage() {

        MoveGenerator moveGenerator =
                new MoveGenerator();

        long totalLegalEdges = 0;
        long totalExploredEdges = 0;

        int exploredNodes = 0;
        int fullyCoveredNodes = 0;

        double lowestCoverage = 100.0;
        double highestCoverage = 0.0;

        PositionNode lowestCoverageNode = null;
        PositionNode highestCoverageNode = null;

        for (PositionNode node :
                graph.getNodes()) {

            Position position =
                    node.getPosition();

            int legalEdgeCount =
                    moveGenerator
                            .generateLegalMoves(
                                    position
                            )
                            .size();

            if (legalEdgeCount == 0) {
                continue;
            }

            int exploredEdgeCount =
                    node.getExploredEdgeCount();

            /*
             * Ignore positions which have
             * been discovered but never expanded.
             */
            if (exploredEdgeCount == 0) {
                continue;
            }

            exploredNodes++;

            totalLegalEdges +=
                    legalEdgeCount;

            totalExploredEdges +=
                    exploredEdgeCount;

            double coverage =
                    (double) exploredEdgeCount
                            / legalEdgeCount
                            * 100.0;

            if (exploredEdgeCount
                    == legalEdgeCount) {

                fullyCoveredNodes++;
            }

            if (coverage
                    < lowestCoverage) {

                lowestCoverage =
                        coverage;

                lowestCoverageNode =
                        node;
            }

            if (coverage
                    > highestCoverage) {

                highestCoverage =
                        coverage;

                highestCoverageNode =
                        node;
            }
        }

        double overallCoverage;

        if (totalLegalEdges == 0) {

            overallCoverage = 0.0;

        } else {

            overallCoverage =
                    (double) totalExploredEdges
                            / totalLegalEdges
                            * 100.0;
        }

        System.out.println();
        System.out.println(
                "EDGE COVERAGE"
        );

        System.out.println(
                "Expanded nodes: "
                        + exploredNodes
        );

        System.out.println(
                "Total legal outgoing edges: "
                        + totalLegalEdges
        );

        System.out.println(
                "Distinct explored edges: "
                        + totalExploredEdges
        );

        System.out.printf(
                "Overall edge coverage: %.2f%%%n",
                overallCoverage
        );

        System.out.println(
                "Fully covered nodes: "
                        + fullyCoveredNodes
        );

        if (lowestCoverageNode != null) {

            System.out.printf(
                    "Lowest node coverage: %.2f%%%n",
                    lowestCoverage
            );
        }

        if (highestCoverageNode != null) {

            System.out.printf(
                    "Highest node coverage: %.2f%%%n",
                    highestCoverage
            );
        }
    }
}
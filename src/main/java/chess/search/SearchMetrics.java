package main.java.chess.search;

public class SearchMetrics {

    private long walkerSteps;
    private long nodeDovetailSteps;

    private long newPositions;
    private long revisits;

    private int maxDepth;

    public void recordWalkerStep(
            boolean discoveredNewPosition,
            int depth
    ) {
        walkerSteps++;

        if (discoveredNewPosition) {
            newPositions++;
        } else {
            revisits++;
        }

        if (depth > maxDepth) {
            maxDepth = depth;
        }
    }

    public void recordNodeDovetailStep(
            boolean discoveredNewPosition
    ) {
        nodeDovetailSteps++;

        if (discoveredNewPosition) {
            newPositions++;
        } else {
            revisits++;
        }
    }

    public long getWalkerSteps() {
        return walkerSteps;
    }

    public long getNodeDovetailSteps() {
        return nodeDovetailSteps;
    }

    public long getTotalSteps() {
        return walkerSteps + nodeDovetailSteps;
    }

    public long getNewPositions() {
        return newPositions;
    }

    public long getRevisits() {
        return revisits;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public double getDiscoveryRate() {

        if (getTotalSteps() == 0) {
            return 0.0;
        }

        return (double) newPositions
                / getTotalSteps();
    }
}
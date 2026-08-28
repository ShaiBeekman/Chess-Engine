package main.java.chess.analysis;

public class SubtreeStatistics {

    private final int generatedPositions;

    private final int solvedPositions;


    public SubtreeStatistics(
            int generatedPositions,
            int solvedPositions
    ) {

        if (generatedPositions < 0) {

            throw new IllegalArgumentException(
                    "Generated position count cannot be negative."
            );
        }


        if (solvedPositions < 0) {

            throw new IllegalArgumentException(
                    "Solved position count cannot be negative."
            );
        }


        if (solvedPositions > generatedPositions) {

            throw new IllegalArgumentException(
                    "Solved position count cannot exceed generated position count."
            );
        }


        this.generatedPositions =
                generatedPositions;


        this.solvedPositions =
                solvedPositions;
    }


    public int getGeneratedPositions() {

        return generatedPositions;
    }


    public int getSolvedPositions() {

        return solvedPositions;
    }


    public int getUnsolvedPositions() {

        return generatedPositions
                - solvedPositions;
    }


    public double getSolvedFraction() {

        if (generatedPositions == 0) {

            return 0.0;
        }


        return (double) solvedPositions
                / generatedPositions;
    }


    public double getSolvedPercentage() {

        return getSolvedFraction()
                * 100.0;
    }


    public boolean isEmpty() {

        return generatedPositions == 0;
    }
}

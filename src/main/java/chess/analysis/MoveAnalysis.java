package main.java.chess.analysis;

import main.java.chess.model.Move;
import main.java.chess.model.Position;
import main.java.chess.search.SearchOutcome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class MoveAnalysis {

    private final Move move;

    private final String san;

    private final Position position;

    private final SearchOutcome outcome;

    private final int mateDistance;

    private final String mateDisplay;

    private final int evaluation;

    private final int searchValue;


    // =========================================================
    // Subtree statistics
    // =========================================================

    private final int generatedPositions;

    private final int exploredPositions;

    private final int solvedPositions;


    // =========================================================
    // Variations
    // =========================================================

    private final List<VariationNode> variations;


    // =========================================================
    // Constructor
    // =========================================================

    public MoveAnalysis(
            Move move,
            String san,
            Position position,
            SearchOutcome outcome,
            int mateDistance,
            String mateDisplay,
            int evaluation,
            int searchValue,
            int generatedPositions,
            int exploredPositions,
            int solvedPositions
    ) {

        this.move =
                move;


        this.san =
                san;


        this.position =
                position;


        this.outcome =
                outcome;


        this.mateDistance =
                mateDistance;


        this.mateDisplay =
                mateDisplay;


        this.evaluation =
                evaluation;


        this.searchValue =
                searchValue;


        this.generatedPositions =
                Math.max(
                        0,
                        generatedPositions
                );


        this.exploredPositions =
                Math.max(
                        0,
                        Math.min(
                                exploredPositions,
                                this.generatedPositions
                        )
                );


        this.solvedPositions =
                Math.max(
                        0,
                        Math.min(
                                solvedPositions,
                                this.generatedPositions
                        )
                );


        this.variations =
                new ArrayList<>();
    }


    // =========================================================
    // Move
    // =========================================================

    public Move getMove() {

        return move;
    }


    // =========================================================
    // SAN
    // =========================================================

    public String getSan() {

        return san;
    }


    // =========================================================
    // Position
    // =========================================================

    public Position getPosition() {

        return position;
    }


    // =========================================================
    // Outcome
    // =========================================================

    public SearchOutcome getOutcome() {

        return outcome;
    }


    public boolean isSolved() {

        return outcome != null
                &&
                outcome != SearchOutcome.UNKNOWN;
    }


    public boolean hasProvenMate() {

        return outcome == SearchOutcome.WHITE_WIN
                ||
                outcome == SearchOutcome.BLACK_WIN;
    }


    public boolean isDraw() {

        return outcome == SearchOutcome.DRAW;
    }


    public boolean isUnknown() {

        return outcome == null
                ||
                outcome == SearchOutcome.UNKNOWN;
    }


    // =========================================================
    // Mate distance
    // =========================================================

    public int getMateDistance() {

        return mateDistance;
    }


    public String getMateDisplay() {

        return mateDisplay;
    }


    // =========================================================
    // Evaluation
    // =========================================================

    public int getEvaluation() {

        return evaluation;
    }


    public int getSearchValue() {

        return searchValue;
    }


    // =========================================================
    // Generated positions
    // =========================================================

    public int getGeneratedPositions() {

        return generatedPositions;
    }


    // =========================================================
    // Explored positions
    // =========================================================

    public int getExploredPositions() {

        return exploredPositions;
    }


    public int getUnexploredPositions() {

        return Math.max(
                0,
                generatedPositions
                        - exploredPositions
        );
    }


    public double getExploredFraction() {

        if (generatedPositions <= 0) {

            return 0.0;
        }


        return (double) exploredPositions
                / (double) generatedPositions;
    }


    public double getExploredPercentage() {

        return getExploredFraction()
                * 100.0;
    }


    // =========================================================
    // Solved positions
    // =========================================================

    public int getSolvedPositions() {

        return solvedPositions;
    }


    public int getUnsolvedPositions() {

        return Math.max(
                0,
                generatedPositions
                        - solvedPositions
        );
    }


    public double getSolvedFraction() {

        if (generatedPositions <= 0) {

            return 0.0;
        }


        return (double) solvedPositions
                / (double) generatedPositions;
    }


    public double getSolvedPercentage() {

        return getSolvedFraction()
                * 100.0;
    }


    // =========================================================
    // Variations
    // =========================================================

    public void addVariation(
            VariationNode variation
    ) {

        if (variation == null) {

            return;
        }


        variations.add(
                variation
        );
    }


    public void addVariations(
            List<VariationNode> variations
    ) {

        if (variations == null) {

            return;
        }


        for (VariationNode variation :
                variations) {

            addVariation(
                    variation
            );
        }
    }


    public List<VariationNode> getVariations() {

        return Collections.unmodifiableList(
                variations
        );
    }


    public boolean hasVariations() {

        return !variations.isEmpty();
    }
}
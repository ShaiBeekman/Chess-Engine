package main.java.chess.analysis;

import main.java.chess.model.Position;
import main.java.chess.search.SearchOutcome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class VariationNode {

    private final String san;

    private final Position position;

    private final SearchOutcome outcome;

    private final int mateDistance;

    private final int staticEvaluation;

    private final int searchValue;

    private final boolean terminal;


    // =========================================================
    // Subtree statistics
    // =========================================================

    private final int generatedPositions;

    private final int exploredPositions;

    private final int solvedPositions;


    // =========================================================
    // Children
    // =========================================================

    private final List<VariationNode> children;


    // =========================================================
    // Constructor
    // =========================================================

    public VariationNode(
            String san,
            Position position,
            SearchOutcome outcome,
            int mateDistance,
            int staticEvaluation,
            int searchValue,
            boolean terminal,
            int generatedPositions,
            int exploredPositions,
            int solvedPositions
    ) {

        this.san =
                san;


        this.position =
                position;


        this.outcome =
                outcome;


        this.mateDistance =
                mateDistance;


        this.staticEvaluation =
                staticEvaluation;


        this.searchValue =
                searchValue;


        this.terminal =
                terminal;


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


        this.children =
                new ArrayList<>();
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


    // =========================================================
    // Static evaluation
    // =========================================================

    public int getStaticEvaluation() {

        return staticEvaluation;
    }


    // =========================================================
    // Search value
    // =========================================================

    public int getSearchValue() {

        return searchValue;
    }


    // =========================================================
    // Terminal
    // =========================================================

    public boolean isTerminal() {

        return terminal;
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
    // Children
    // =========================================================

    public void addChild(
            VariationNode child
    ) {

        if (child == null) {

            return;
        }


        children.add(
                child
        );
    }


    public List<VariationNode> getChildren() {

        return Collections.unmodifiableList(
                children
        );
    }


    public boolean hasChildren() {

        return !children.isEmpty();
    }
}
package main.java.chess.analysis;

import main.java.chess.model.Color;
import main.java.chess.search.SearchOutcome;

import java.util.List;

import java.util.ArrayList;
import java.util.Collections;

public class PositionAnalysis {

    private final Color sideToMove;

    private final SearchOutcome outcome;

    private final int mateDistance;

    private final int evaluation;

    private final int searchValue;

    private final List<MoveAnalysis> moves;

    private final List<VariationNode> principalVariation;

    public PositionAnalysis(
            Color sideToMove,
            SearchOutcome outcome,
            int mateDistance,
            int evaluation,
            int searchValue,
            List<MoveAnalysis> moves
    ) {

        this.sideToMove =
                sideToMove;

        this.outcome =
                outcome;

        this.mateDistance =
                mateDistance;

        this.evaluation =
                evaluation;

        this.searchValue =
                searchValue;

        this.moves =
                moves;

        this.principalVariation =
                new ArrayList<>();
    }


    public Color getSideToMove() {
        return sideToMove;
    }


    public SearchOutcome getOutcome() {
        return outcome;
    }


    public int getMateDistance() {
        return mateDistance;
    }


    public int getEvaluation() {
        return evaluation;
    }


    public int getSearchValue() {
        return searchValue;
    }


    public List<MoveAnalysis> getMoves() {
        return moves;
    }


    public boolean hasProvenMate() {

        return outcome == SearchOutcome.WHITE_WIN
                || outcome == SearchOutcome.BLACK_WIN;
    }


    public boolean isDraw() {

        return outcome == SearchOutcome.DRAW;
    }


    public boolean isUnknown() {

        return outcome == SearchOutcome.UNKNOWN;
    }

    public void addPrincipalVariationNode(
            VariationNode node
    ) {

        principalVariation.add(
                node
        );
    }


    public List<VariationNode> getPrincipalVariation() {

        return Collections.unmodifiableList(
                principalVariation
        );
    }
}
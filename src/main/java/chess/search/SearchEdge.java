package main.java.chess.search;

import main.java.chess.model.Move;

public class SearchEdge {

    private final Move move;
    private final PositionNode target;

    public SearchEdge(
            Move move,
            PositionNode target
    ) {
        this.move = move;
        this.target = target;
    }

    public Move getMove() {
        return move;
    }

    public PositionNode getTarget() {
        return target;
    }
}
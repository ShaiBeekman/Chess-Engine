package main.java.chess.search;

import main.java.chess.model.GameState;
import main.java.chess.model.Move;
import main.java.chess.model.Position;
import main.java.chess.rules.GameStateEvaluator;
import main.java.chess.rules.MoveGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Walker {

    private final int id;

    private Position currentPosition;

    private final PositionGraph graph;
    private final MoveGenerator moveGenerator;
    private final GameStateEvaluator gameStateEvaluator;
    private final Random random;

    private int depth;

    public Walker(
            int id,
            Position startingPosition,
            PositionGraph graph
    ) {
        this.id = id;
        this.currentPosition = startingPosition;
        this.graph = graph;

        this.moveGenerator =
                new MoveGenerator();

        this.gameStateEvaluator =
                new GameStateEvaluator();

        this.random =
                new Random();

        this.depth = 0;

        PositionNode startingNode =
                graph.getOrCreateNode(
                        startingPosition
                );

        startingNode.incrementVisitCount();
    }

    public int getId() {
        return id;
    }

    public Position getCurrentPosition() {
        return currentPosition;
    }

    public int getDepth() {
        return depth;
    }

    public boolean step() {

        GameState gameState =
                gameStateEvaluator.evaluate(
                        currentPosition
                );

        if (gameState == GameState.CHECKMATE
                || gameState == GameState.STALEMATE
                || gameState == GameState.DRAW_75_MOVE
                || gameState == GameState.DRAW_FIVEFOLD_REPETITION) {

            return false;
        }

        List<Move> legalMoves =
                moveGenerator.generateLegalMoves(
                        currentPosition
                );

        if (legalMoves.isEmpty()) {
            return false;
        }

        PositionNode currentNode =
                graph.getOrCreateNode(
                        currentPosition
                );

        Move chosenMove =
                chooseLeastExploredEdge(
                        currentNode,
                        legalMoves
                );

        /*
         * Record that this particular direction
         * from this particular position was explored.
         */
        currentNode.incrementWalkerEdgeVisit(
                chosenMove
        );

        Position nextPosition =
                currentPosition.makeMove(
                        chosenMove
                );

        PositionNode nextNode =
                graph.getOrCreateNode(
                        nextPosition
                );

        currentNode.addOutgoingEdge(
                chosenMove,
                nextNode
        );

        nextNode.incrementVisitCount();

        currentPosition =
                nextPosition;

        depth++;

        return true;
    }

    private Move chooseLeastExploredEdge(
            PositionNode currentNode,
            List<Move> legalMoves
    ) {

        int lowestEdgeVisitCount =
                Integer.MAX_VALUE;

        List<Move> bestMoves =
                new ArrayList<>();

        for (Move move : legalMoves) {

            int edgeVisits =
                    currentNode.getEdgeVisitCount(
                            move
                    );

            if (edgeVisits < lowestEdgeVisitCount) {

                lowestEdgeVisitCount =
                        edgeVisits;

                bestMoves.clear();
                bestMoves.add(move);

            } else if (
                    edgeVisits
                            == lowestEdgeVisitCount
            ) {

                bestMoves.add(move);
            }
        }

        return bestMoves.get(
                random.nextInt(
                        bestMoves.size()
                )
        );
    }
}
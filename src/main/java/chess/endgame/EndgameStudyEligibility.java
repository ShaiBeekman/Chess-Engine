package main.java.chess.endgame;

import main.java.chess.model.Color;
import main.java.chess.search.PositionNode;
import main.java.chess.search.SearchOutcome;

public final class EndgameStudyEligibility {

    public enum Status {
        PROVING,
        PROVEN_MATE,
        PROVEN_DRAW,
        PROVEN_LOSS
    }


    private final Status status;
    private final int mateDistance;


    private EndgameStudyEligibility(
            Status status,
            int mateDistance
    ) {

        this.status =
                status;

        this.mateDistance =
                mateDistance;
    }


    public static EndgameStudyEligibility fromRoot(
            PositionNode root
    ) {

        if (root == null
                || root.getSearchOutcome() == null
                || root.getSearchOutcome()
                == SearchOutcome.UNKNOWN) {

            return new EndgameStudyEligibility(
                    Status.PROVING,
                    -1
            );
        }


        SearchOutcome outcome =
                root.getSearchOutcome();


        if (outcome
                == SearchOutcome.DRAW) {

            return new EndgameStudyEligibility(
                    Status.PROVEN_DRAW,
                    -1
            );
        }


        Color sideToMove =
                root.getPosition()
                        .getSideToMove();


        boolean sideToMoveWins =
                (
                        sideToMove == Color.WHITE
                                &&
                                outcome
                                        == SearchOutcome.WHITE_WIN
                )
                        ||
                        (
                                sideToMove == Color.BLACK
                                        &&
                                        outcome
                                                == SearchOutcome.BLACK_WIN
                        );


        if (!sideToMoveWins) {

            return new EndgameStudyEligibility(
                    Status.PROVEN_LOSS,
                    root.getMateDistance()
            );
        }


        if (root.getMateDistance()
                <= 0) {

            return new EndgameStudyEligibility(
                    Status.PROVING,
                    -1
            );
        }


        return new EndgameStudyEligibility(
                Status.PROVEN_MATE,
                root.getMateDistance()
        );
    }


    public Status status() {

        return status;
    }


    public int mateDistance() {

        return mateDistance;
    }


    public boolean isReadyPuzzle() {

        return status
                == Status.PROVEN_MATE;
    }


    public int mateInMoves() {

        if (!isReadyPuzzle()) {

            return -1;
        }


        return (
                mateDistance
                        + 1
        ) / 2;
    }
}

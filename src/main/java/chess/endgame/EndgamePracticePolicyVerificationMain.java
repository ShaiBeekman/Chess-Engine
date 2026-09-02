package main.java.chess.endgame;

import main.java.chess.model.Board;
import main.java.chess.model.Color;
import main.java.chess.model.Move;
import main.java.chess.model.Piece;
import main.java.chess.model.PieceType;
import main.java.chess.model.Position;
import main.java.chess.model.Square;

import java.util.HashMap;
import java.util.List;
import java.util.Random;


public final class EndgamePracticePolicyVerificationMain {

    private EndgamePracticePolicyVerificationMain() {
    }


    public static void main(
            String[] args
    ) {

        ExactEndgameTablebase tablebase =
                ExactEndgameTablebase.tierZeroCatalog();


        Position position =
                position(
                        Color.WHITE,

                        piece(
                                "a1",
                                PieceType.KING,
                                Color.WHITE
                        ),

                        piece(
                                "g8",
                                PieceType.KING,
                                Color.BLACK
                        ),

                        piece(
                                "d4",
                                PieceType.QUEEN,
                                Color.WHITE
                        ),

                        piece(
                                "e4",
                                PieceType.BISHOP,
                                Color.WHITE
                        )
                );


        System.out.println(
                "Variable endgame practice verification"
        );

        System.out.println(
                "======================================"
        );


        ExactEndgameTablebase.Probe current =
                tablebase.probe(
                        position
                );


        require(
                current.outcome()
                        == ExactEndgameTablebase.Outcome.WIN,
                "Expected KQBK fixture to be WIN."
        );


        System.out.println(
                "Position: KQBK WIN"
        );

        System.out.println(
                "Current DTM: "
                        + current.mateDistance()
        );


        List<ExactEndgameTablebase.MoveAnalysis>
                analysis =
                tablebase.analyzeMoves(
                        position
                );


        require(
                !analysis.isEmpty(),
                "Expected exact move analysis."
        );


        int optimalCount = 0;
        int nonOptimalCount = 0;


        for (ExactEndgameTablebase.MoveAnalysis move :
                analysis) {

            if (move.optimal()) {
                optimalCount++;
            } else {
                nonOptimalCount++;
            }
        }


        System.out.println(
                "Classified legal moves: "
                        + analysis.size()
        );

        System.out.println(
                "Optimal moves: "
                        + optimalCount
        );

        System.out.println(
                "Non-optimal moves: "
                        + nonOptimalCount
        );


        require(
                optimalCount > 0,
                "Expected at least one optimal move."
        );


        require(
                nonOptimalCount > 0,
                "Fixture should provide non-optimal practice moves."
        );


        verifyPerfectStrength(
                tablebase,
                position
        );


        verifyVariableStrength(
                tablebase,
                position,
                90,
                10_000
        );


        verifyVariableStrength(
                tablebase,
                position,
                50,
                10_000
        );


        verifyVariableStrength(
                tablebase,
                position,
                10,
                10_000
        );


        verifyZeroStrength(
                tablebase,
                position
        );


        verifyBounds(
                tablebase
        );


        System.out.println();

        System.out.println(
                "VARIABLE ENDGAME PRACTICE POLICY PASSED"
        );

        System.out.println(
                "TABLEBASE KNOWLEDGE AND PLAYING STRENGTH ARE NOW SEPARATE"
        );
    }


    private static void verifyPerfectStrength(
            ExactEndgameTablebase tablebase,
            Position position
    ) {

        EndgamePracticePolicy policy =
                new EndgamePracticePolicy(
                        tablebase,
                        new Random(
                                123456789L
                        )
                );


        List<Move> exactBest =
                tablebase.bestMoves(
                        position
                );


        require(
                !exactBest.isEmpty(),
                "Exact best-move set cannot be empty."
        );


        for (int iteration = 0;
             iteration < 10_000;
             iteration++) {

            Move selected =
                    policy.chooseMove(
                            position,
                            100
                    );


            require(
                    selected != null,
                    "Strength 100 returned null."
            );


            require(
                    exactBest.contains(
                            selected
                    ),
                    "Strength 100 selected a non-optimal move."
            );
        }


        System.out.println();

        System.out.println(
                "Strength 100"
        );

        System.out.println(
                "  10,000 / 10,000 moves optimal"
        );

        System.out.println(
                "  Perfect-play guarantee: PASSED"
        );
    }


    private static void verifyVariableStrength(
            ExactEndgameTablebase tablebase,
            Position position,
            int strength,
            int samples
    ) {

        EndgamePracticePolicy policy =
                new EndgamePracticePolicy(
                        tablebase,
                        new Random(
                                1000L + strength
                        )
                );


        List<Move> exactBest =
                tablebase.bestMoves(
                        position
                );


        int optimal =
                0;


        int mistakes =
                0;


        for (int iteration = 0;
             iteration < samples;
             iteration++) {

            Move selected =
                    policy.chooseMove(
                            position,
                            strength
                    );


            require(
                    selected != null,
                    "Practice policy returned null."
            );


            if (exactBest.contains(
                    selected
            )) {

                optimal++;

            } else {

                mistakes++;
            }
        }


        double percentage =
                100.0
                        * optimal
                        / samples;


        System.out.println();

        System.out.println(
                "Strength "
                        + strength
        );

        System.out.printf(
                "  Optimal: %d / %d (%.2f%%)%n",
                optimal,
                samples,
                percentage
        );

        System.out.println(
                "  Intentional mistakes: "
                        + mistakes
        );


        /*
         * Statistical sanity check.
         *
         * 5 percentage points is vastly wider than normal sampling
         * noise at 10,000 samples, so this is deterministic enough
         * for a regression verification without being brittle.
         */
        require(
                Math.abs(
                        percentage
                                - strength
                ) < 5.0,
                "Observed optimal frequency is unexpectedly far from requested strength."
        );
    }


    private static void verifyZeroStrength(
            ExactEndgameTablebase tablebase,
            Position position
    ) {

        EndgamePracticePolicy policy =
                new EndgamePracticePolicy(
                        tablebase,
                        new Random(
                                987654321L
                        )
                );


        List<Move> exactBest =
                tablebase.bestMoves(
                        position
                );


        int nonOptimal =
                0;


        for (int iteration = 0;
             iteration < 1_000;
             iteration++) {

            Move selected =
                    policy.chooseMove(
                            position,
                            0
                    );


            require(
                    selected != null,
                    "Strength 0 returned null."
            );


            if (!exactBest.contains(
                    selected
            )) {

                nonOptimal++;
            }
        }


        require(
                nonOptimal == 1_000,
                "Strength 0 should select from the non-optimal pool when one exists."
        );


        System.out.println();

        System.out.println(
                "Strength 0"
        );

        System.out.println(
                "  1,000 / 1,000 selections from practice-mistake pool"
        );
    }


    private static void verifyBounds(
            ExactEndgameTablebase tablebase
    ) {

        EndgamePracticePolicy policy =
                new EndgamePracticePolicy(
                        tablebase,
                        new Random(
                                1L
                        )
                );


        boolean lowRejected =
                false;


        boolean highRejected =
                false;


        try {

            policy.chooseMove(
                    null,
                    -1
            );

        } catch (IllegalArgumentException expected) {

            lowRejected =
                    true;
        }


        try {

            policy.chooseMove(
                    null,
                    101
            );

        } catch (IllegalArgumentException expected) {

            highRejected =
                    true;
        }


        require(
                lowRejected,
                "Strength -1 should be rejected."
        );


        require(
                highRejected,
                "Strength 101 should be rejected."
        );


        System.out.println();

        System.out.println(
                "Strength bounds: PASSED"
        );
    }


    private static Position position(
            Color sideToMove,
            PlacedPiece... pieces
    ) {

        Board board =
                new Board();


        for (PlacedPiece placed :
                pieces) {

            board.setPiece(
                    placed.square(),
                    placed.piece()
            );
        }


        return new Position(
                board,
                sideToMove,
                false,
                false,
                false,
                false,
                null,
                0,
                1,
                new HashMap<>()
        );
    }


    private static PlacedPiece piece(
            String square,
            PieceType type,
            Color color
    ) {

        return new PlacedPiece(
                Square.fromAlgebraic(
                        square
                ),
                new Piece(
                        type,
                        color
                )
        );
    }


    private static void require(
            boolean condition,
            String message
    ) {

        if (!condition) {

            throw new IllegalStateException(
                    message
            );
        }
    }


    private record PlacedPiece(
            Square square,
            Piece piece
    ) {
    }
}
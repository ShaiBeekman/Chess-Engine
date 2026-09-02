package main.java.chess.endgame;

import main.java.chess.engine.EndgameMoveController;
import main.java.chess.model.Board;
import main.java.chess.model.Color;
import main.java.chess.model.Move;
import main.java.chess.model.Piece;
import main.java.chess.model.PieceType;
import main.java.chess.model.Position;
import main.java.chess.model.PositionKey;
import main.java.chess.model.Square;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;


/**
 * M41 non-Swing gate for the GUI practice scenario.
 *
 * Finds a KQPK WIN root with an optimal first move that reaches a LOSS
 * position for the defender where at least one non-optimal move exists.
 *
 * Then verifies:
 *
 *     practice 100 -> exact optimal defense
 *     practice   0 -> non-optimal defense
 */
public final class FourPieceTierOneKqpkLivePracticeVerificationMain {

    private static final int MAX_ATTEMPTS =
            50_000;


    private FourPieceTierOneKqpkLivePracticeVerificationMain() {
    }


    public static void main(
            String[] args
    ) {

        System.out.println(
                "KQPK Tier-1 GUI/live-practice gate"
        );

        System.out.println(
                "================================="
        );


        ExactEndgameTablebase tablebase =
                ExactEndgameTablebase.tierZeroCatalog();

        Random random =
                new Random(
                        0x4D34314BL
                );


        Fixture fixture =
                findFixture(
                        tablebase,
                        random
                );


        System.out.println();

        System.out.println(
                "Practice-capable fixture found"
        );

        System.out.println(
                "  attempts: "
                        + fixture.attempts()
        );

        System.out.println(
                "  root: "
                        + describe(
                        fixture.root()
                )
        );

        System.out.println(
                "  root outcome: "
                        + tablebase.probe(
                                fixture.root()
                        )
                        .outcome()
        );

        System.out.println(
                "  optimal first move: "
                        + fixture.firstMove()
        );

        System.out.println(
                "  defender: "
                        + describe(
                        fixture.defender()
                )
        );


        List<ExactEndgameTablebase.MoveAnalysis> defenderAnalysis =
                tablebase.analyzeMoves(
                        fixture.defender()
                );


        int optimal =
                0;

        int mistakes =
                0;


        for (ExactEndgameTablebase.MoveAnalysis analysis :
                defenderAnalysis) {

            if (analysis.optimal()) {

                optimal++;

            } else {

                mistakes++;
            }
        }


        System.out.println(
                "  defender legal exact moves: "
                        + defenderAnalysis.size()
        );

        System.out.println(
                "  defender optimal moves: "
                        + optimal
        );

        System.out.println(
                "  defender mistake moves: "
                        + mistakes
        );


        if (mistakes == 0) {

            throw new IllegalStateException(
                    "Fixture is not practice-capable."
            );
        }


        verifyStrength100(
                fixture.defender()
        );


        verifyStrength0(
                fixture.defender()
        );


        System.out.println();

        System.out.println(
                "KQPK GUI / LIVE PRACTICE GATE PASSED"
        );

        System.out.println(
                "NEXT: GENERALIZE TIER-1 SOLVER TO THE REMAINING ONE-PAWN FAMILIES"
        );
    }


    private static void verifyStrength100(
            Position defender
    ) {

        EndgameMoveController controller =
                new EndgameMoveController(
                        new Random(
                                100
                        )
                );


        controller.setPracticeMode(
                true
        );

        controller.setPracticeStrength(
                100
        );


        List<Move> exact =
                controller.exactBestMoves(
                        defender
                );


        Move chosen =
                controller.chooseMove(
                        defender
                );


        if (chosen == null
                || !exact.contains(
                chosen
        )) {

            throw new IllegalStateException(
                    "Practice strength 100 did not choose exact defense."
            );
        }


        System.out.println();

        System.out.println(
                "Practice strength 100"
        );

        System.out.println(
                "  chosen move: "
                        + chosen
        );

        System.out.println(
                "  exact-optimal: true"
        );

        System.out.println(
                "  PASSED"
        );
    }


    private static void verifyStrength0(
            Position defender
    ) {

        EndgameMoveController controller =
                new EndgameMoveController(
                        new Random(
                                0
                        )
                );


        controller.setPracticeMode(
                true
        );

        controller.setPracticeStrength(
                0
        );


        List<Move> exact =
                controller.exactBestMoves(
                        defender
                );


        Move chosen =
                controller.chooseMove(
                        defender
                );


        if (chosen == null) {

            throw new IllegalStateException(
                    "Practice strength 0 returned no move."
            );
        }


        if (exact.contains(
                chosen
        )) {

            throw new IllegalStateException(
                    "Practice strength 0 chose an exact move even though "
                            + "a tablebase-classified mistake was available."
            );
        }


        System.out.println();

        System.out.println(
                "Practice strength 0"
        );

        System.out.println(
                "  chosen move: "
                        + chosen
        );

        System.out.println(
                "  exact-optimal: false"
        );

        System.out.println(
                "  visible deliberate mistake: PASSED"
        );
    }


    private static Fixture findFixture(
            ExactEndgameTablebase tablebase,
            Random random
    ) {

        for (int attempt = 1;
             attempt <= MAX_ATTEMPTS;
             attempt++) {

            Color strong =
                    random.nextBoolean()
                            ? Color.WHITE
                            : Color.BLACK;


            Position root =
                    randomKqpk(
                            random,
                            strong
                    );


            ExactEndgameTablebase.Probe rootProbe =
                    tablebase.probe(
                            root
                    );


            if (rootProbe.outcome()
                    != ExactEndgameTablebase.Outcome.WIN) {

                continue;
            }


            List<Move> bestMoves =
                    tablebase.bestMoves(
                            root
                    );


            for (Move first :
                    bestMoves) {

                Position defender =
                        root.makeMove(
                                first
                        );


                ExactEndgameTablebase.Probe defenderProbe =
                        tablebase.probe(
                                defender
                        );


                if (defenderProbe.outcome()
                        != ExactEndgameTablebase.Outcome.LOSS) {

                    continue;
                }


                List<ExactEndgameTablebase.MoveAnalysis> analyses =
                        tablebase.analyzeMoves(
                                defender
                        );


                if (analyses.size() < 2) {

                    continue;
                }


                boolean hasMistake =
                        false;


                for (ExactEndgameTablebase.MoveAnalysis analysis :
                        analyses) {

                    if (!analysis.optimal()) {

                        hasMistake =
                                true;

                        break;
                    }
                }


                if (hasMistake) {

                    return new Fixture(
                            root,
                            first,
                            defender,
                            attempt
                    );
                }
            }
        }


        throw new IllegalStateException(
                "Could not find a KQPK practice fixture in "
                        + MAX_ATTEMPTS
                        + " attempts."
        );
    }


    private static Position randomKqpk(
            Random random,
            Color strong
    ) {

        Board board =
                new Board();


        board.setPiece(
                randomSquare(
                        random
                ),
                new Piece(
                        PieceType.KING,
                        Color.WHITE
                )
        );


        board.setPiece(
                randomSquare(
                        random
                ),
                new Piece(
                        PieceType.KING,
                        Color.BLACK
                )
        );


        board.setPiece(
                randomSquare(
                        random
                ),
                new Piece(
                        PieceType.QUEEN,
                        strong
                )
        );


        Square pawn =
                new Square(
                        random.nextInt(
                                8
                        ),
                        1 + random.nextInt(
                                6
                        )
                );


        board.setPiece(
                pawn,
                new Piece(
                        PieceType.PAWN,
                        strong
                )
        );


        Position temporary =
                new Position(
                        board,
                        strong,
                        false,
                        false,
                        false,
                        false,
                        null,
                        0,
                        1,
                        new HashMap<>()
                );


        Map<PositionKey, Integer> repetitions =
                new HashMap<>();


        repetitions.put(
                temporary.createPositionKey(),
                1
        );


        return new Position(
                board,
                strong,
                false,
                false,
                false,
                false,
                null,
                0,
                1,
                repetitions
        );
    }


    private static Square randomSquare(
            Random random
    ) {

        int square =
                random.nextInt(
                        64
                );


        return new Square(
                square & 7,
                square >>> 3
        );
    }


    private static String describe(
            Position position
    ) {

        StringBuilder result =
                new StringBuilder();


        for (int rank = 0;
             rank < 8;
             rank++) {

            for (int file = 0;
                 file < 8;
                 file++) {

                Square square =
                        new Square(
                                file,
                                rank
                        );


                Piece piece =
                        position.getBoard()
                                .getPiece(
                                        square
                                );


                if (piece == null) {

                    continue;
                }


                if (!result.isEmpty()) {

                    result.append(
                            ", "
                    );
                }


                result.append(
                        piece.color()
                );

                result.append(
                        " "
                );

                result.append(
                        piece.type()
                );

                result.append(
                        "@"
                );

                result.append(
                        square.toAlgebraic()
                );
            }
        }


        result.append(
                " | stm="
        );

        result.append(
                position.getSideToMove()
        );


        return result.toString();
    }


    private record Fixture(
            Position root,
            Move firstMove,
            Position defender,
            int attempts
    ) {
    }
}

package main.java.chess.engine;

import main.java.chess.endgame.ExactEndgameTablebase;
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


public final class EndgameMoveControllerVerificationMain {

    private EndgameMoveControllerVerificationMain() {
    }


    public static void main(
            String[] args
    ) {

        EndgameMoveController controller =
                new EndgameMoveController(
                        new Random(
                                20260829L
                        )
                );


        Position winning =
                winningKqbkPosition();


        System.out.println(
                "Engine endgame move controller verification"
        );

        System.out.println(
                "==========================================="
        );


        verifySupport(
                controller,
                winning
        );


        verifyNormalPerfectPlay(
                controller,
                winning
        );


        verifyPractice100(
                controller,
                winning
        );


        verifyPractice90(
                controller,
                winning
        );


        verifyPractice50(
                controller,
                winning
        );


        verifyPractice0(
                controller,
                winning
        );


        verifyModeSeparation(
                controller,
                winning
        );


        verifyUnsupportedFallback(
                controller
        );


        verifyBounds(
                controller
        );


        System.out.println();

        System.out.println(
                "ENGINE ENDGAME MOVE CONTROLLER PASSED"
        );

        System.out.println(
                "PERFECT PLAY AND VARIABLE PRACTICE PLAY ARE NOW ENGINE-FACING"
        );
    }


    // ============================================================
    // Support
    // ============================================================

    private static void verifySupport(
            EndgameMoveController controller,
            Position position
    ) {

        require(
                controller.supports(
                        position
                ),
                "Expected KQBK position to be supported."
        );


        ExactEndgameTablebase.Probe probe =
                controller.probe(
                        position
                );


        require(
                probe.outcome()
                        == ExactEndgameTablebase.Outcome.WIN,
                "Expected KQBK fixture to be WIN."
        );


        require(
                probe.mateDistance()
                        == 7,
                "Expected KQBK fixture DTM 7."
        );


        System.out.println();

        System.out.println(
                "Tier-0 recognition"
        );

        System.out.println(
                "  Outcome: "
                        + probe.outcome()
        );

        System.out.println(
                "  DTM: "
                        + probe.mateDistance()
        );

        System.out.println(
                "  Support: PASSED"
        );
    }


    // ============================================================
    // Normal mode
    // ============================================================

    private static void verifyNormalPerfectPlay(
            EndgameMoveController controller,
            Position position
    ) {

        controller.disablePracticeMode();

        controller.setPracticeStrength(
                0
        );


        List<Move> exactBest =
                controller.exactBestMoves(
                        position
                );


        require(
                !exactBest.isEmpty(),
                "Exact best-move set cannot be empty."
        );


        Move expected =
                exactBest.getFirst();


        for (int iteration = 0;
             iteration < 1_000;
             iteration++) {

            Move selected =
                    controller.chooseMove(
                            position
                    );


            require(
                    selected != null,
                    "Normal mode returned null."
            );


            require(
                    selected.equals(
                            expected
                    ),
                    "Normal mode did not select deterministic exact move."
            );
        }


        System.out.println();

        System.out.println(
                "Normal engine mode"
        );

        System.out.println(
                "  Stored practice strength: 0"
        );

        System.out.println(
                "  Practice mode: false"
        );

        System.out.println(
                "  1,000 / 1,000 exact"
        );

        System.out.println(
                "  Perfect-play isolation: PASSED"
        );
    }


    // ============================================================
    // Practice strengths
    // ============================================================

    private static void verifyPractice100(
            EndgameMoveController controller,
            Position position
    ) {

        controller.enablePracticeMode();

        controller.setPracticeStrength(
                100
        );


        List<Move> best =
                controller.exactBestMoves(
                        position
                );


        for (int iteration = 0;
             iteration < 1_000;
             iteration++) {

            Move selected =
                    controller.chooseMove(
                            position
                    );


            require(
                    selected != null,
                    "Practice 100 returned null."
            );


            require(
                    best.contains(
                            selected
                    ),
                    "Practice 100 selected non-optimal move."
            );
        }


        System.out.println();

        System.out.println(
                "Practice strength 100"
        );

        System.out.println(
                "  1,000 / 1,000 optimal"
        );
    }


    private static void verifyPractice90(
            EndgameMoveController controller,
            Position position
    ) {

        samplePracticeStrength(
                controller,
                position,
                90,
                10_000
        );
    }


    private static void verifyPractice50(
            EndgameMoveController controller,
            Position position
    ) {

        samplePracticeStrength(
                controller,
                position,
                50,
                10_000
        );
    }


    private static void samplePracticeStrength(
            EndgameMoveController controller,
            Position position,
            int strength,
            int samples
    ) {

        controller.enablePracticeMode();

        controller.setPracticeStrength(
                strength
        );


        List<Move> best =
                controller.exactBestMoves(
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
                    controller.chooseMove(
                            position
                    );


            require(
                    selected != null,
                    "Practice controller returned null."
            );


            if (best.contains(
                    selected
            )) {

                optimal++;

            } else {

                mistakes++;
            }
        }


        double optimalPercent =
                100.0
                        * optimal
                        / samples;


        System.out.println();

        System.out.println(
                "Practice strength "
                        + strength
        );

        System.out.printf(
                "  Optimal: %d / %d (%.2f%%)%n",
                optimal,
                samples,
                optimalPercent
        );

        System.out.println(
                "  Intentional mistakes: "
                        + mistakes
        );


        require(
                Math.abs(
                        optimalPercent
                                - strength
                ) < 5.0,
                "Observed practice strength is unexpectedly far from requested strength."
        );
    }


    private static void verifyPractice0(
            EndgameMoveController controller,
            Position position
    ) {

        controller.enablePracticeMode();

        controller.setPracticeStrength(
                0
        );


        List<Move> best =
                controller.exactBestMoves(
                        position
                );


        int mistakes =
                0;


        for (int iteration = 0;
             iteration < 1_000;
             iteration++) {

            Move selected =
                    controller.chooseMove(
                            position
                    );


            require(
                    selected != null,
                    "Practice 0 returned null."
            );


            if (!best.contains(
                    selected
            )) {

                mistakes++;
            }
        }


        require(
                mistakes == 1_000,
                "Practice 0 should select from the mistake pool when one exists."
        );


        System.out.println();

        System.out.println(
                "Practice strength 0"
        );

        System.out.println(
                "  1,000 / 1,000 intentional mistakes"
        );
    }


    // ============================================================
    // Critical mode-separation test
    // ============================================================

    private static void verifyModeSeparation(
            EndgameMoveController controller,
            Position position
    ) {

        /*
         * Leave the stored practice strength at zero -- maximum
         * imperfection.
         */
        controller.setPracticeStrength(
                0
        );


        controller.enablePracticeMode();


        List<Move> best =
                controller.exactBestMoves(
                        position
                );


        Move practiceMove =
                controller.chooseMove(
                        position
                );


        require(
                practiceMove != null,
                "Practice mode returned null."
        );


        require(
                !best.contains(
                        practiceMove
                ),
                "Strength-zero practice should make a classified mistake."
        );


        /*
         * Now turn practice mode OFF without touching strength.
         *
         * The same controller must immediately return to perfect
         * tablebase behavior.
         */
        controller.disablePracticeMode();


        Move normalMove =
                controller.chooseMove(
                        position
                );


        require(
                normalMove != null,
                "Normal mode returned null."
        );


        require(
                best.contains(
                        normalMove
                ),
                "Disabling practice mode failed to restore perfect play."
        );


        System.out.println();

        System.out.println(
                "Mode separation"
        );

        System.out.println(
                "  Practice strength remains: "
                        + controller.getPracticeStrength()
        );

        System.out.println(
                "  Practice mode mistake: confirmed"
        );

        System.out.println(
                "  Normal mode perfect move: confirmed"
        );

        System.out.println(
                "  Mode separation: PASSED"
        );
    }


    // ============================================================
    // Unsupported fallback
    // ============================================================

    private static void verifyUnsupportedFallback(
            EndgameMoveController controller
    ) {

        Position unsupported =
                position(
                        Color.WHITE,

                        piece(
                                "a1",
                                PieceType.KING,
                                Color.WHITE
                        ),

                        piece(
                                "h8",
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
                                PieceType.PAWN,
                                Color.WHITE
                        )
                );


        require(
                !controller.supports(
                        unsupported
                ),
                "Tier-1 pawn material should not yet be supported."
        );


        Move selected =
                controller.chooseMove(
                        unsupported
                );


        require(
                selected == null,
                "Unsupported position should return null for ordinary-search fallback."
        );


        System.out.println();

        System.out.println(
                "Unsupported Tier-1 fallback"
        );

        System.out.println(
                "  Controller returned null"
        );

        System.out.println(
                "  Ordinary engine search may take over: PASSED"
        );
    }


    // ============================================================
    // Bounds
    // ============================================================

    private static void verifyBounds(
            EndgameMoveController controller
    ) {

        boolean lowRejected =
                false;

        boolean highRejected =
                false;


        try {

            controller.setPracticeStrength(
                    -1
            );

        } catch (IllegalArgumentException expected) {

            lowRejected =
                    true;
        }


        try {

            controller.setPracticeStrength(
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
                "Practice-strength bounds: PASSED"
        );
    }


    // ============================================================
    // Fixture
    // ============================================================

    private static Position winningKqbkPosition() {

        return position(
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
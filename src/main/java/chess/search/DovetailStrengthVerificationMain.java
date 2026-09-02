package main.java.chess.search;

import main.java.chess.model.FenCodec;
import main.java.chess.model.Move;
import main.java.chess.model.Position;
import main.java.chess.rules.GameStateEvaluator;
import main.java.chess.rules.MoveGenerator;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * M76 verification gate.
 *
 * This is intentionally independent of Swing and Stockfish.
 *
 * It verifies:
 *  1. the A/B/C/D profile cycle,
 *  2. the diagonal scheduler sequence is unchanged,
 *  3. evaluation bias does NOT destroy root breadth,
 *  4. stronger profiles increasingly prefer a better White move,
 *  5. the same preference is correctly inverted for Black.
 */
public final class DovetailStrengthVerificationMain {

    private static final String WHITE_START_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private static final String BLACK_START_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR b KQkq - 0 1";

    private static final int GUIDANCE_TRIALS =
            600;


    private DovetailStrengthVerificationMain() {
    }


    public static void main(
            String[] args
    ) {

        System.out.println(
                "Dovetail strength-aware walker verification gate"
        );

        System.out.println(
                "Walker BUILD_ID: "
                        + DovetailWalker.BUILD_ID
        );

        System.out.println();


        verifyProfileCycle();
        verifyDiagonalScheduleUnchanged();
        verifyRootBreadth();
        verifyGuidance(
                WHITE_START_FEN,
                true
        );
        verifyGuidance(
                BLACK_START_FEN,
                false
        );


        System.out.println();
        System.out.println(
                "DOVETAIL STRENGTH-AWARE WALKER GATE PASSED"
        );
    }


    // =========================================================
    // Profile cycle
    // =========================================================

    private static void verifyProfileCycle() {

        DovetailWalker.BiasProfile[] expected = {
                DovetailWalker.BiasProfile.EXPLORER,
                DovetailWalker.BiasProfile.GUIDED,
                DovetailWalker.BiasProfile.STRONG,
                DovetailWalker.BiasProfile.PRINCIPAL,
                DovetailWalker.BiasProfile.EXPLORER,
                DovetailWalker.BiasProfile.GUIDED,
                DovetailWalker.BiasProfile.STRONG,
                DovetailWalker.BiasProfile.PRINCIPAL
        };


        for (int index = 0;
             index < expected.length;
             index++) {

            DovetailWalker.BiasProfile actual =
                    DovetailWalker.BiasProfile
                            .forWalkerIndex(
                                    index
                            );


            require(
                    actual
                            == expected[
                            index
                            ],
                    "Unexpected profile at walker "
                            + DovetailWalker.labelFor(
                            index
                    )
            );
        }


        System.out.println(
                "profile cycle: A explorer, B guided, C strong, D principal ... PASSED"
        );
    }


    // =========================================================
    // Diagonal schedule
    // =========================================================

    private static void verifyDiagonalScheduleUnchanged() {

        List<String> expected =
                List.of(
                        "A1",
                        "B1",
                        "A2",
                        "C1",
                        "B2",
                        "A3",
                        "D1",
                        "C2",
                        "B3",
                        "A4"
                );


        List<String> actual =
                ExplorationScheduler
                        .previewWalkerScheduleLabels(
                                expected.size()
                        );


        require(
                expected.equals(
                        actual
                ),
                "Diagonal walker enumeration changed: "
                        + actual
        );


        System.out.println(
                String.join(
                        ", ",
                        actual
                )
        );

        System.out.println(
                "exact diagonal enumeration unchanged PASSED"
        );
    }


    // =========================================================
    // Breadth preservation
    // =========================================================

    private static void verifyRootBreadth() {

        Position rootPosition =
                FenCodec.parse(
                        WHITE_START_FEN
                );


        MoveGenerator moveGenerator =
                new MoveGenerator();

        GameStateEvaluator gameStateEvaluator =
                new GameStateEvaluator();

        PositionGraph graph =
                new PositionGraph();

        PositionNode root =
                graph.getOrCreateNode(
                        rootPosition
                );


        List<Move> legalMoves =
                root.getOrCacheLegalMoves(
                        moveGenerator
                );


        require(
                legalMoves.size()
                        == 20,
                "Starting position should have 20 legal moves."
        );


        Set<Move> firstMoves =
                new HashSet<>();


        for (int walkerIndex = 0;
             walkerIndex < legalMoves.size();
             walkerIndex++) {

            long seed =
                    0xD0E7A11L
                            ^ (
                            0x9E3779B97F4A7C15L
                                    * (
                                    walkerIndex + 1L
                            )
                    );


            DovetailWalker walker =
                    new DovetailWalker(
                            walkerIndex,
                            root,
                            seed
                    );


            DovetailWalker.StepResult result =
                    walker.step(
                            graph,
                            moveGenerator,
                            gameStateEvaluator
                    );


            require(
                    result.advanced(),
                    "Walker "
                            + walker.getLabel()
                            + " failed to advance."
            );


            firstMoves.add(
                    result.move()
            );
        }


        require(
                firstMoves.size()
                        == legalMoves.size(),
                "Strength bias repeated a root move before all 20 legal moves were sampled."
        );


        for (Move move :
                legalMoves) {

            require(
                    root.getWalkerEdgeVisitCount(
                            move
                    )
                            == 1,
                    "Root breadth visit count should be exactly 1 for "
                            + move
            );
        }


        System.out.printf(
                "root breadth: %d / %d legal moves sampled before repetition PASSED%n",
                firstMoves.size(),
                legalMoves.size()
        );
    }


    // =========================================================
    // Guidance verification
    // =========================================================

    private static void verifyGuidance(
            String fen,
            boolean whiteToMove
    ) {

        Map<DovetailWalker.BiasProfile, Integer> bestMoveSelections =
                new EnumMap<>(
                        DovetailWalker.BiasProfile.class
                );


        for (DovetailWalker.BiasProfile profile :
                DovetailWalker.BiasProfile.values()) {

            bestMoveSelections.put(
                    profile,
                    0
            );
        }


        for (int trial = 0;
             trial < GUIDANCE_TRIALS;
             trial++) {

            long seed =
                    0x51A7E000L
                            + trial * 0x9E3779B9L;


            for (int profileIndex = 0;
                 profileIndex < 4;
                 profileIndex++) {

                TrialPosition trialPosition =
                        createArtificiallyRankedPosition(
                                fen,
                                whiteToMove
                        );


                DovetailWalker walker =
                        new DovetailWalker(
                                profileIndex,
                                trialPosition.root(),
                                seed
                        );


                DovetailWalker.StepResult result =
                        walker.step(
                                trialPosition.graph(),
                                trialPosition.moveGenerator(),
                                trialPosition.gameStateEvaluator()
                        );


                require(
                        result.advanced(),
                        "Guidance trial walker failed to advance."
                );


                if (result
                        .move()
                        .equals(
                                trialPosition.bestMove()
                        )) {

                    DovetailWalker.BiasProfile profile =
                            walker.getBiasProfile();


                    bestMoveSelections.put(
                            profile,
                            bestMoveSelections.get(
                                    profile
                            ) + 1
                    );
                }
            }
        }


        double explorerRate =
                rate(
                        bestMoveSelections.get(
                                DovetailWalker.BiasProfile.EXPLORER
                        )
                );

        double guidedRate =
                rate(
                        bestMoveSelections.get(
                                DovetailWalker.BiasProfile.GUIDED
                        )
                );

        double strongRate =
                rate(
                        bestMoveSelections.get(
                                DovetailWalker.BiasProfile.STRONG
                        )
                );

        double principalRate =
                rate(
                        bestMoveSelections.get(
                                DovetailWalker.BiasProfile.PRINCIPAL
                        )
                );


        /*
         * The same random ticket is used across profiles in each trial.
         * Because the best candidate is first in the weighted interval and its
         * probability grows with the exponent, these counts should be monotone.
         */
        require(
                guidedRate
                        >= explorerRate,
                "Guided profile did not improve on explorer."
        );

        require(
                strongRate
                        >= guidedRate,
                "Strong profile did not improve on guided."
        );

        require(
                principalRate
                        >= strongRate,
                "Principal profile did not improve on strong."
        );

        require(
                principalRate
                        >= explorerRate + 0.15,
                "Principal guidance is not materially stronger than explorer."
        );


        System.out.printf(
                "%s guidance best-move rate: explorer %.1f%% | guided %.1f%% | strong %.1f%% | principal %.1f%%%n",
                whiteToMove
                        ? "White"
                        : "Black",
                explorerRate * 100.0,
                guidedRate * 100.0,
                strongRate * 100.0,
                principalRate * 100.0
        );

        System.out.println(
                (whiteToMove
                        ? "White"
                        : "Black")
                        + " side-aware stochastic guidance PASSED"
        );
    }


    /**
     * Give the already-known root children artificial, strictly ordered search
     * values. This isolates the M76 walker policy from the details of the
     * current PositionEvaluator.
     *
     * White's best child receives the largest value.
     * Black's best child receives the smallest value.
     */
    private static TrialPosition createArtificiallyRankedPosition(
            String fen,
            boolean whiteToMove
    ) {

        Position position =
                FenCodec.parse(
                        fen
                );


        MoveGenerator moveGenerator =
                new MoveGenerator();

        GameStateEvaluator gameStateEvaluator =
                new GameStateEvaluator();

        PositionGraph graph =
                new PositionGraph();

        PositionNode root =
                graph.getOrCreateNode(
                        position
                );


        List<Move> legalMoves =
                root.getOrCacheLegalMoves(
                        moveGenerator
                );


        require(
                !legalMoves.isEmpty(),
                "Artificial guidance position has no legal moves."
        );


        Move bestMove =
                legalMoves.get(
                        legalMoves.size() - 1
                );


        for (int index = 0;
             index < legalMoves.size();
             index++) {

            Move move =
                    legalMoves.get(
                            index
                    );


            Position child =
                    position.makeMove(
                            move
                    );


            require(
                    graph.ensureManualContinuation(
                            position,
                            child
                    ),
                    "Could not seed legal guidance edge."
            );


            PositionNode childNode =
                    graph.getOrCreateNode(
                            child
                    );


            int orderedMagnitude =
                    (index + 1)
                            * 100;


            childNode.setSearchValue(
                    whiteToMove
                            ? orderedMagnitude
                            : -orderedMagnitude
            );
        }


        return new TrialPosition(
                graph,
                root,
                bestMove,
                moveGenerator,
                gameStateEvaluator
        );
    }


    private static double rate(
            int selections
    ) {

        return selections
                / (double) GUIDANCE_TRIALS;
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


    private record TrialPosition(
            PositionGraph graph,
            PositionNode root,
            Move bestMove,
            MoveGenerator moveGenerator,
            GameStateEvaluator gameStateEvaluator
    ) {
    }
}

package main.java.chess.endgame;

import main.java.chess.model.Move;
import main.java.chess.model.Position;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Random;


/**
 * Selects moves for endgame practice while retaining exact
 * tablebase knowledge underneath.
 *
 * Practice strength:
 *
 *     100 = always exact optimal play
 *       0 = always choose from the full mistake spectrum when
 *           a mistake is available
 *
 * Strength controls two separate things:
 *
 *     1. mistake frequency
 *     2. mistake severity
 *
 * The exact tablebase remains unchanged and mathematically perfect.
 */
public final class EndgamePracticePolicy {

    /**
     * Semantic classification of an intentional tablebase mistake.
     *
     * Severity increases as we move down this list conceptually:
     *
     *     SLOWER_WIN
     *     SHORTER_RESISTANCE
     *     WIN_TO_DRAW
     *     DRAW_TO_LOSS
     *     WIN_TO_LOSS
     *
     * SHORTER_RESISTANCE only occurs when the current position was
     * already lost, so it does not change WDL -- it merely allows
     * mate sooner.
     */
    public enum MistakeKind {

        SLOWER_WIN,

        SHORTER_RESISTANCE,

        WIN_TO_DRAW,

        DRAW_TO_LOSS,

        WIN_TO_LOSS
    }


    /**
     * Public description of one non-optimal practice move.
     *
     * severityRank:
     *
     *     0 = mildest available mistake
     *     larger values = progressively more damaging
     */
    public record PracticeMove(
            Move move,
            MistakeKind kind,
            ExactEndgameTablebase.Outcome childOutcome,
            int childMateDistance,
            int severityRank
    ) {
    }


    private final ExactEndgameTablebase tablebase;

    private final Random random;


    public EndgamePracticePolicy(
            ExactEndgameTablebase tablebase
    ) {

        this(
                tablebase,
                new Random()
        );
    }


    public EndgamePracticePolicy(
            ExactEndgameTablebase tablebase,
            Random random
    ) {

        this.tablebase =
                Objects.requireNonNull(
                        tablebase,
                        "tablebase"
                );

        this.random =
                Objects.requireNonNull(
                        random,
                        "random"
                );
    }


    /**
     * Select a practice move.
     *
     * Returns null when the position is unsupported or has no
     * exact-classified legal move.
     */
    public Move chooseMove(
            Position position,
            int strength
    ) {

        validateStrength(
                strength
        );


        ExactEndgameTablebase.Probe current =
                tablebase.probe(
                        position
                );


        if (current.outcome()
                == ExactEndgameTablebase.Outcome.UNSUPPORTED) {

            return null;
        }


        List<ExactEndgameTablebase.MoveAnalysis>
                analysis =
                tablebase.analyzeMoves(
                        position
                );


        if (analysis.isEmpty()) {

            return null;
        }


        List<ExactEndgameTablebase.MoveAnalysis>
                optimal =
                new ArrayList<>();


        for (ExactEndgameTablebase.MoveAnalysis move :
                analysis) {

            if (move.optimal()) {

                optimal.add(
                        move
                );
            }
        }


        if (optimal.isEmpty()) {

            return randomAnalyzedMove(
                    analysis
            );
        }


        /*
         * Strength 100 is a hard guarantee.
         */
        if (strength == 100) {

            return randomAnalyzedMove(
                    optimal
            );
        }


        List<PracticeMove> mistakes =
                classifyMistakes(
                        current,
                        analysis
                );


        /*
         * Some positions genuinely have no non-optimal exact move.
         */
        if (mistakes.isEmpty()) {

            return randomAnalyzedMove(
                    optimal
            );
        }


        /*
         * Strength is the probability of exact optimal play.
         */
        boolean playOptimal =
                random.nextInt(
                        100
                ) < strength;


        if (playOptimal) {

            return randomAnalyzedMove(
                    optimal
            );
        }


        return chooseMistake(
                mistakes,
                strength
        ).move();
    }


    /**
     * Expose the exact semantic mistake spectrum for testing,
     * diagnostics, and eventually the GUI.
     *
     * The returned list is ordered from mildest to most severe.
     */
    public List<PracticeMove> analyzeMistakes(
            Position position
    ) {

        ExactEndgameTablebase.Probe current =
                tablebase.probe(
                        position
                );


        if (current.outcome()
                == ExactEndgameTablebase.Outcome.UNSUPPORTED) {

            return List.of();
        }


        List<ExactEndgameTablebase.MoveAnalysis>
                analysis =
                tablebase.analyzeMoves(
                        position
                );


        if (analysis.isEmpty()) {

            return List.of();
        }


        return List.copyOf(
                classifyMistakes(
                        current,
                        analysis
                )
        );
    }


    // ============================================================
    // Mistake classification
    // ============================================================

    private List<PracticeMove> classifyMistakes(
            ExactEndgameTablebase.Probe current,
            List<ExactEndgameTablebase.MoveAnalysis> analysis
    ) {

        List<UnrankedPracticeMove> unranked =
                new ArrayList<>();


        for (ExactEndgameTablebase.MoveAnalysis move :
                analysis) {

            if (move.optimal()) {

                continue;
            }


            MistakeKind kind =
                    classifyMistake(
                            current,
                            move
                    );


            if (kind == null) {

                /*
                 * This would indicate an unexpected WDL relationship.
                 * Do not invent a practice interpretation for it.
                 */
                continue;
            }


            unranked.add(
                    new UnrankedPracticeMove(
                            move.move(),
                            kind,
                            move.childOutcome(),
                            move.childMateDistance()
                    )
            );
        }


        unranked.sort(
                mistakeComparator()
        );


        List<PracticeMove> ranked =
                new ArrayList<>(
                        unranked.size()
                );


        for (int index = 0;
             index < unranked.size();
             index++) {

            UnrankedPracticeMove move =
                    unranked.get(
                            index
                    );


            ranked.add(
                    new PracticeMove(
                            move.move(),
                            move.kind(),
                            move.childOutcome(),
                            move.childMateDistance(),
                            index
                    )
            );
        }


        return ranked;
    }


    private MistakeKind classifyMistake(
            ExactEndgameTablebase.Probe current,
            ExactEndgameTablebase.MoveAnalysis move
    ) {

        return switch (current.outcome()) {

            case WIN ->
                    classifyWinningPositionMistake(
                            move
                    );

            case DRAW ->
                    classifyDrawnPositionMistake(
                            move
                    );

            case LOSS ->
                    classifyLostPositionMistake(
                            move
                    );

            case UNSUPPORTED ->
                    null;
        };
    }


    private MistakeKind classifyWinningPositionMistake(
            ExactEndgameTablebase.MoveAnalysis move
    ) {

        /*
         * Child outcome is from the opponent's perspective.
         *
         * Opponent LOSS:
         *     we still win, just less efficiently.
         *
         * Opponent DRAW:
         *     we threw away the win.
         *
         * Opponent WIN:
         *     we turned the win into a loss.
         */
        return switch (move.childOutcome()) {

            case LOSS ->
                    MistakeKind.SLOWER_WIN;

            case DRAW ->
                    MistakeKind.WIN_TO_DRAW;

            case WIN ->
                    MistakeKind.WIN_TO_LOSS;

            case UNSUPPORTED ->
                    null;
        };
    }


    private MistakeKind classifyDrawnPositionMistake(
            ExactEndgameTablebase.MoveAnalysis move
    ) {

        /*
         * From a correctly solved DRAW, the meaningful WDL mistake
         * is allowing the opponent to obtain a WIN.
         */
        if (move.childOutcome()
                == ExactEndgameTablebase.Outcome.WIN) {

            return MistakeKind.DRAW_TO_LOSS;
        }


        return null;
    }


    private MistakeKind classifyLostPositionMistake(
            ExactEndgameTablebase.MoveAnalysis move
    ) {

        /*
         * From a correctly solved LOSS, every legal exact child
         * should remain WIN for the opponent.
         *
         * A non-optimal move therefore shortens resistance.
         */
        if (move.childOutcome()
                == ExactEndgameTablebase.Outcome.WIN) {

            return MistakeKind.SHORTER_RESISTANCE;
        }


        return null;
    }


    // ============================================================
    // Severity ordering
    // ============================================================

    private Comparator<UnrankedPracticeMove>
    mistakeComparator() {

        return (first, second) -> {

            int kindComparison =
                    Integer.compare(
                            kindSeverity(
                                    first.kind()
                            ),
                            kindSeverity(
                                    second.kind()
                            )
                    );


            if (kindComparison != 0) {

                return kindComparison;
            }


            /*
             * Within a slower win:
             *
             * child LOSS DTM 8 is milder than child LOSS DTM 20.
             *
             * Smaller DTM is therefore preferable.
             */
            if (first.kind()
                    == MistakeKind.SLOWER_WIN) {

                return Integer.compare(
                        normalizedDistance(
                                first.childMateDistance()
                        ),
                        normalizedDistance(
                                second.childMateDistance()
                        )
                );
            }


            /*
             * Within a losing continuation:
             *
             * opponent WIN DTM 20 is preferable to opponent WIN DTM 5
             * because we survive longer.
             *
             * Larger DTM is therefore milder.
             */
            if (first.kind()
                    == MistakeKind.SHORTER_RESISTANCE
                    || first.kind()
                    == MistakeKind.DRAW_TO_LOSS
                    || first.kind()
                    == MistakeKind.WIN_TO_LOSS) {

                return Integer.compare(
                        normalizedDistance(
                                second.childMateDistance()
                        ),
                        normalizedDistance(
                                first.childMateDistance()
                        )
                );
            }


            /*
             * WIN_TO_DRAW has no DTM ordering.
             */
            return 0;
        };
    }


    /**
     * Global conceptual severity.
     *
     * Note that SHORTER_RESISTANCE does not change WDL and is
     * therefore intentionally considered milder than throwing
     * away a win or draw.
     */
    private static int kindSeverity(
            MistakeKind kind
    ) {

        return switch (kind) {

            case SLOWER_WIN ->
                    0;

            case SHORTER_RESISTANCE ->
                    1;

            case WIN_TO_DRAW ->
                    2;

            case DRAW_TO_LOSS ->
                    3;

            case WIN_TO_LOSS ->
                    4;
        };
    }


    // ============================================================
    // Strength-dependent selection
    // ============================================================

    private PracticeMove chooseMistake(
            List<PracticeMove> mistakes,
            int strength
    ) {

        if (mistakes.size() == 1) {

            return mistakes.getFirst();
        }


        /*
         * Higher strength exposes only the mildest part of the
         * mistake spectrum.
         *
         * Lower strength progressively opens the complete spectrum.
         */
        double admittedFraction =
                (100.0 - strength)
                        / 100.0;


        int admitted =
                (int) Math.ceil(
                        mistakes.size()
                                * admittedFraction
                );


        admitted =
                Math.max(
                        1,
                        Math.min(
                                admitted,
                                mistakes.size()
                        )
                );


        return mistakes.get(
                random.nextInt(
                        admitted
                )
        );
    }


    // ============================================================
    // Helpers
    // ============================================================

    private Move randomAnalyzedMove(
            List<ExactEndgameTablebase.MoveAnalysis> moves
    ) {

        return moves.get(
                random.nextInt(
                        moves.size()
                )
        ).move();
    }


    private static int normalizedDistance(
            int distance
    ) {

        return Math.max(
                distance,
                0
        );
    }


    private static void validateStrength(
            int strength
    ) {

        if (strength < 0
                || strength > 100) {

            throw new IllegalArgumentException(
                    "Practice strength must be between 0 and 100: "
                            + strength
            );
        }
    }


    private record UnrankedPracticeMove(
            Move move,
            MistakeKind kind,
            ExactEndgameTablebase.Outcome childOutcome,
            int childMateDistance
    ) {
    }
}
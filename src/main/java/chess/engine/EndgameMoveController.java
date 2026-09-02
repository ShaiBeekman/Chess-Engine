package main.java.chess.engine;

import main.java.chess.endgame.EndgamePracticePolicy;
import main.java.chess.endgame.ExactEndgameTablebase;
import main.java.chess.model.Move;
import main.java.chess.model.Position;

import java.util.List;
import java.util.Objects;
import java.util.Random;


/**
 * Engine-facing controller for exact endgame move selection.
 *
 * This class deliberately sits outside ChessEngine's persistent
 * PositionGraph / ExplorationScheduler analysis machinery.
 *
 * The analysis engine remains deterministic.
 *
 * This controller answers a different question:
 *
 *     "If the computer is going to PLAY a move from this exact
 *      endgame position, which move should it play?"
 *
 * Normal mode:
 *
 *     always exact tablebase play
 *
 * Practice mode:
 *
 *     exact tablebase knowledge remains available, but the playing
 *     policy may intentionally choose a classified suboptimal move.
 */
public final class EndgameMoveController {

    public static final int PERFECT_STRENGTH =
            100;

    public static final int DEFAULT_PRACTICE_STRENGTH =
            100;


    private final ExactEndgameTablebase tablebase;

    private final EndgamePracticePolicy practicePolicy;


    private boolean practiceMode;

    private int practiceStrength;


    public EndgameMoveController() {

        this(
                ExactEndgameTablebase.tierZeroCatalog(),
                new Random()
        );
    }


    public EndgameMoveController(
            Random random
    ) {

        this(
                ExactEndgameTablebase.tierZeroCatalog(),
                random
        );
    }


    public EndgameMoveController(
            ExactEndgameTablebase tablebase,
            Random random
    ) {

        this.tablebase =
                Objects.requireNonNull(
                        tablebase,
                        "tablebase"
                );


        this.practicePolicy =
                new EndgamePracticePolicy(
                        tablebase,
                        Objects.requireNonNull(
                                random,
                                "random"
                        )
                );


        /*
         * Normal engine behavior is mathematically perfect.
         */
        this.practiceMode =
                false;


        this.practiceStrength =
                DEFAULT_PRACTICE_STRENGTH;
    }


    // ============================================================
    // Move selection
    // ============================================================

    /**
     * Select an exact endgame move for the current engine mode.
     *
     * Returns null when the position is not handled by the generic
     * Tier-0 tablebase or when no move is available.
     *
     * The caller can therefore fall back to ordinary engine search.
     */
    public Move chooseMove(
            Position position
    ) {

        if (position == null) {

            return null;
        }


        if (!practiceMode) {

            return choosePerfectMove(
                    position
            );
        }


        return practicePolicy.chooseMove(
                position,
                practiceStrength
        );
    }


    /**
     * Explicit perfect-play selection.
     *
     * This ignores practice mode and practice strength.
     */
    public Move choosePerfectMove(
            Position position
    ) {

        if (position == null) {

            return null;
        }


        List<Move> bestMoves =
                tablebase.bestMoves(
                        position
                );


        if (bestMoves.isEmpty()) {

            return null;
        }


        /*
         * Exact bestMoves() contains only mathematically equivalent
         * optimal choices.
         *
         * Selecting the first makes normal engine behavior
         * deterministic.
         */
        return bestMoves.getFirst();
    }


    /**
     * Explicit practice selection.
     *
     * This ignores the controller's stored practiceMode flag and is
     * useful for callers that want one-off practice behavior.
     */
    public Move choosePracticeMove(
            Position position
    ) {

        if (position == null) {

            return null;
        }


        return practicePolicy.chooseMove(
                position,
                practiceStrength
        );
    }


    // ============================================================
    // Exact support
    // ============================================================

    public boolean supports(
            Position position
    ) {

        if (position == null) {

            return false;
        }


        return tablebase.probe(
                position
        ).outcome()
                != ExactEndgameTablebase.Outcome.UNSUPPORTED;
    }


    public ExactEndgameTablebase.Probe probe(
            Position position
    ) {

        return tablebase.probe(
                position
        );
    }


    public List<Move> exactBestMoves(
            Position position
    ) {

        if (position == null) {

            return List.of();
        }


        return tablebase.bestMoves(
                position
        );
    }


    public List<ExactEndgameTablebase.MoveAnalysis>
    analyzeMoves(
            Position position
    ) {

        if (position == null) {

            return List.of();
        }


        return tablebase.analyzeMoves(
                position
        );
    }


    public List<EndgamePracticePolicy.PracticeMove>
    analyzeMistakes(
            Position position
    ) {

        if (position == null) {

            return List.of();
        }


        return practicePolicy.analyzeMistakes(
                position
        );
    }


    // ============================================================
    // Practice mode
    // ============================================================

    public boolean isPracticeMode() {

        return practiceMode;
    }


    public void setPracticeMode(
            boolean practiceMode
    ) {

        this.practiceMode =
                practiceMode;
    }


    public void enablePracticeMode() {

        practiceMode =
                true;
    }


    public void disablePracticeMode() {

        practiceMode =
                false;
    }


    // ============================================================
    // Practice strength
    // ============================================================

    public int getPracticeStrength() {

        return practiceStrength;
    }


    public void setPracticeStrength(
            int practiceStrength
    ) {

        if (practiceStrength < 0
                || practiceStrength > 100) {

            throw new IllegalArgumentException(
                    "Practice strength must be between 0 and 100: "
                            + practiceStrength
            );
        }


        this.practiceStrength =
                practiceStrength;
    }


    // ============================================================
    // Access
    // ============================================================

    public ExactEndgameTablebase getTablebase() {

        return tablebase;
    }
}
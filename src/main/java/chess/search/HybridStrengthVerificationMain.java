package main.java.chess.search;

import main.java.chess.evaluation.PositionEvaluator;
import main.java.chess.model.Color;
import main.java.chess.model.FenCodec;
import main.java.chess.model.Move;
import main.java.chess.model.Position;
import main.java.chess.rules.GameStateEvaluator;
import main.java.chess.rules.MoveGenerator;

import java.util.ArrayList;
import java.util.List;

/**
 * M77 verification gate.
 *
 * M76 strengthened the persistent Dovetail walkers without changing their
 * diagonal enumeration. M77 strengthens the other half of HYBRID: fair node
 * coverage now chooses the strongest still-unexplored edge at the selected
 * node.
 *
 * This gate verifies:
 *  1. M76 walker behavior remains installed,
 *  2. the diagonal schedule is unchanged,
 *  3. pure Dovetail still performs zero coverage work,
 *  4. Hybrid still performs both walker and coverage work,
 *  5. Hybrid coverage chooses the best remaining move for White,
 *  6. the same choice is correctly inverted for Black,
 *  7. fair node coverage still reaches every initial root branch,
 *  8. focus bias still does not starve global coverage.
 */
public final class HybridStrengthVerificationMain {

    private static final String START_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    /*
     * White can capture the black queen with Qxd5.
     * The position is intentionally simple so static evaluation has a clear
     * preference among the still-unexplored moves.
     */
    private static final String WHITE_GUIDANCE_FEN =
            "7k/8/8/3q4/8/8/8/K2Q4 w - - 0 1";

    /*
     * Color-reversed companion: Black can capture the white queen with Qxd4.
     */
    private static final String BLACK_GUIDANCE_FEN =
            "k2q4/8/8/8/3Q4/8/8/7K b - - 0 1";

    private static final int MODE_WORK_UNITS =
            600;

    private static final long FIRST_WALKER_SEED =
            0xD0E7A11L
                    ^ (0x9E3779B97F4A7C15L * 1L);


    private HybridStrengthVerificationMain() {
    }


    public static void main(
            String[] args
    ) {
        System.out.println(
                "Hybrid strength-aware coverage verification gate"
        );

        System.out.println(
                "Walker BUILD_ID:   "
                        + DovetailWalker.BUILD_ID
        );

        System.out.println(
                "Coverage BUILD_ID: "
                        + ExplorationScheduler.COVERAGE_BUILD_ID
        );

        System.out.println();

        verifyBuildIds();
        verifyDiagonalScheduleUnchanged();
        verifyModeSeparation();
        verifyGuidance(
                WHITE_GUIDANCE_FEN,
                Color.WHITE
        );
        verifyGuidance(
                BLACK_GUIDANCE_FEN,
                Color.BLACK
        );
        verifyFairCoverage();
        verifyFocusDoesNotStarveGlobalCoverage();

        System.out.println();
        System.out.println(
                "HYBRID STRENGTH-AWARE COVERAGE GATE PASSED"
        );
    }


    // =========================================================
    // Build identity
    // =========================================================

    private static void verifyBuildIds() {
        require(
                "M76-STRENGTH-AWARE-DOVETAIL-WALKERS-V1"
                        .equals(
                                DovetailWalker.BUILD_ID
                        ),
                "M76 Dovetail walker build is not installed."
        );

        require(
                "M77-STRENGTH-AWARE-HYBRID-COVERAGE-V1"
                        .equals(
                                ExplorationScheduler.COVERAGE_BUILD_ID
                        ),
                "Unexpected M77 coverage BUILD_ID."
        );

        System.out.println(
                "M76 walker build preserved PASSED"
        );

        System.out.println(
                "M77 Hybrid coverage build installed PASSED"
        );
    }


    // =========================================================
    // Diagonal schedule preservation
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
                "M77 changed the Dovetail diagonal schedule: "
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
    // Mode separation
    // =========================================================

    private static void verifyModeSeparation() {
        Fixture dovetail =
                fixture(
                        START_FEN,
                        ExplorationScheduler.Mode.DOVETAIL
                );

        int dovetailWork =
                dovetail.scheduler()
                        .advance(
                                MODE_WORK_UNITS
                        );

        require(
                dovetailWork > 0,
                "Pure Dovetail performed no work."
        );

        require(
                dovetail.scheduler()
                        .getWalkerSteps()
                        > 0L,
                "Pure Dovetail produced no walker steps."
        );

        require(
                dovetail.scheduler()
                        .getCoverageSteps()
                        == 0L,
                "Pure Dovetail leaked Hybrid coverage work."
        );

        Fixture hybrid =
                fixture(
                        START_FEN,
                        ExplorationScheduler.Mode.HYBRID
                );

        int hybridWork =
                hybrid.scheduler()
                        .advance(
                                MODE_WORK_UNITS
                        );

        require(
                hybridWork > 0,
                "Hybrid performed no work."
        );

        require(
                hybrid.scheduler()
                        .getWalkerSteps()
                        > 0L,
                "Hybrid produced no walker work."
        );

        require(
                hybrid.scheduler()
                        .getCoverageSteps()
                        > 0L,
                "Hybrid produced no coverage work."
        );

        System.out.printf(
                "Dovetail: walkers %,d | coverage %,d%n",
                dovetail.scheduler().getWalkerSteps(),
                dovetail.scheduler().getCoverageSteps()
        );

        System.out.printf(
                "Hybrid:   walkers %,d | coverage %,d%n",
                hybrid.scheduler().getWalkerSteps(),
                hybrid.scheduler().getCoverageSteps()
        );

        System.out.println(
                "Dovetail / Hybrid separation preserved PASSED"
        );
    }


    // =========================================================
    // Strength-aware coverage guidance
    // =========================================================

    private static void verifyGuidance(
            String fen,
            Color sideToMove
    ) {
        Position position =
                FenCodec.parse(
                        fen
                );

        Move predictedWalkerMove =
                predictFirstWalkerMove(
                        position
                );

        MoveGenerator moveGenerator =
                new MoveGenerator();

        PositionEvaluator evaluator =
                new PositionEvaluator();

        List<Move> legalMoves =
                moveGenerator.generateLegalMoves(
                        position
                );

        require(
                legalMoves.size() > 1,
                "Guidance fixture needs multiple legal moves."
        );

        int expectedBestSideScore =
                Integer.MIN_VALUE;

        for (Move move :
                legalMoves) {
            if (move.equals(
                    predictedWalkerMove
            )) {
                continue;
            }

            int sideScore =
                    sideScore(
                            position,
                            move,
                            sideToMove,
                            evaluator
                    );

            expectedBestSideScore =
                    Math.max(
                            expectedBestSideScore,
                            sideScore
                    );
        }

        require(
                expectedBestSideScore
                        != Integer.MIN_VALUE,
                "Walker consumed the only legal move in guidance fixture."
        );

        Fixture fixture =
                fixture(
                        fen,
                        ExplorationScheduler.Mode.HYBRID
                );

        int completed =
                fixture.scheduler()
                        .advance(
                                2
                        );

        require(
                completed == 2,
                "Hybrid did not complete one walker + one coverage step."
        );

        require(
                fixture.scheduler()
                        .getWalkerSteps()
                        == 1L,
                "Expected exactly one walker step."
        );

        require(
                fixture.scheduler()
                        .getCoverageSteps()
                        == 1L,
                "Expected exactly one coverage step."
        );

        require(
                fixture.root()
                        .getWalkerEdgeVisitCount(
                                predictedWalkerMove
                        )
                        == 1,
                "Scheduler's first walker move did not match deterministic prediction."
        );

        List<Move> coverageMoves =
                new ArrayList<>();

        for (Move move :
                legalMoves) {
            if (fixture.root()
                    .getDovetailEdgeVisitCount(
                            move
                    ) > 0) {
                coverageMoves.add(
                        move
                );
            }
        }

        require(
                coverageMoves.size() == 1,
                "Expected exactly one M77 coverage move, found "
                        + coverageMoves.size()
        );

        Move coverageMove =
                coverageMoves.get(
                        0
                );

        require(
                !coverageMove.equals(
                        predictedWalkerMove
                ),
                "Coverage revisited the walker edge instead of choosing a fresh edge."
        );

        int actualSideScore =
                sideScore(
                        position,
                        coverageMove,
                        sideToMove,
                        evaluator
                );

        require(
                actualSideScore
                        == expectedBestSideScore,
                "Coverage did not choose a best remaining move for "
                        + sideToMove
                        + ". Expected side score "
                        + expectedBestSideScore
                        + " but got "
                        + actualSideScore
                        + " via "
                        + coverageMove
        );

        System.out.printf(
                "%s coverage selected best remaining move %s | side score %+d PASSED%n",
                sideToMove,
                coverageMove,
                actualSideScore
        );
    }


    private static Move predictFirstWalkerMove(
            Position position
    ) {
        PositionGraph graph =
                new PositionGraph();

        PositionNode root =
                graph.getOrCreateNode(
                        position
                );

        DovetailWalker walker =
                new DovetailWalker(
                        0,
                        root,
                        FIRST_WALKER_SEED
                );

        DovetailWalker.StepResult result =
                walker.step(
                        graph,
                        new MoveGenerator(),
                        new GameStateEvaluator()
                );

        require(
                result.advanced(),
                "Could not predict the first M76 walker move."
        );

        return result.move();
    }


    private static int sideScore(
            Position position,
            Move move,
            Color sideToMove,
            PositionEvaluator evaluator
    ) {
        int whitePerspective =
                evaluator.evaluate(
                        position.makeMove(
                                move
                        )
                );

        return sideToMove == Color.WHITE
                ? whitePerspective
                : -whitePerspective;
    }


    // =========================================================
    // Fair global coverage
    // =========================================================

    private static void verifyFairCoverage() {
        CoverageFixture fixture =
                coverageFixture();

        int rootBranches =
                fixture.rootChildren()
                        .size();

        require(
                rootBranches > 1,
                "Fairness fixture needs multiple root branches."
        );

        long coverageBefore =
                fixture.scheduler()
                        .getCoverageSteps();

        int requestedWork =
                rootBranches * 2;

        int completed =
                fixture.scheduler()
                        .advance(
                                requestedWork
                        );

        require(
                completed == requestedWork,
                "Hybrid did not complete requested fairness work."
        );

        long coverageDelta =
                fixture.scheduler()
                        .getCoverageSteps()
                        - coverageBefore;

        require(
                coverageDelta
                        == rootBranches,
                "Expected one coverage turn per initial root branch."
        );

        int touched =
                0;

        for (PositionNode child :
                fixture.rootChildren()) {
            if (child.getExpansionCount()
                    > 0) {
                touched++;
            }
        }

        require(
                touched == rootBranches,
                "Strength guidance starved an initial root branch. Reached "
                        + touched
                        + " / "
                        + rootBranches
        );

        System.out.printf(
                "fair coverage reached %d / %d initial root branches PASSED%n",
                touched,
                rootBranches
        );
    }


    // =========================================================
    // Focus remains only a bias
    // =========================================================

    private static void verifyFocusDoesNotStarveGlobalCoverage() {
        CoverageFixture fixture =
                coverageFixture();

        List<PositionNode> rootChildren =
                fixture.rootChildren();

        PositionNode focus =
                rootChildren.get(
                        0
                );

        fixture.scheduler()
                .setFocus(
                        focus
                );

        int requestedWork =
                rootChildren.size()
                        * 4;

        int completed =
                fixture.scheduler()
                        .advance(
                                requestedWork
                        );

        require(
                completed == requestedWork,
                "Focused Hybrid did not complete requested work."
        );

        int nonFocusReached =
                0;

        for (int index = 1;
             index < rootChildren.size();
             index++) {
            if (rootChildren.get(index)
                    .getExpansionCount()
                    > 0) {
                nonFocusReached++;
            }
        }

        require(
                nonFocusReached
                        == rootChildren.size() - 1,
                "Focus bias starved global coverage. Reached "
                        + nonFocusReached
                        + " / "
                        + (rootChildren.size() - 1)
        );

        require(
                fixture.scheduler()
                        .getFocusNode()
                        == focus,
                "Hybrid lost its selected focus node."
        );

        System.out.printf(
                "focus active; non-focus root branches reached %d / %d PASSED%n",
                nonFocusReached,
                rootChildren.size() - 1
        );
    }


    // =========================================================
    // Fixtures
    // =========================================================

    private static Fixture fixture(
            String fen,
            ExplorationScheduler.Mode mode
    ) {
        Position position =
                FenCodec.parse(
                        fen
                );

        PositionGraph graph =
                new PositionGraph();

        MoveGenerator moveGenerator =
                new MoveGenerator();

        PositionNode root =
                graph.getOrCreateNode(
                        position
                );

        ExplorationScheduler scheduler =
                new ExplorationScheduler(
                        graph,
                        moveGenerator,
                        root,
                        mode
                );

        return new Fixture(
                graph,
                moveGenerator,
                root,
                scheduler
        );
    }


    /**
     * Fully expose the starting root before constructing Hybrid. This leaves
     * the root itself complete while its twenty child positions form the
     * initial fair global coverage queue.
     */
    private static CoverageFixture coverageFixture() {
        Position position =
                FenCodec.parse(
                        START_FEN
                );

        PositionGraph graph =
                new PositionGraph();

        MoveGenerator moveGenerator =
                new MoveGenerator();

        PositionNode root =
                graph.getOrCreateNode(
                        position
                );

        int safety =
                0;

        while (root.hasUnexploredEdges(
                moveGenerator
        )) {
            require(
                    graph.expandOneEdge(
                            root,
                            moveGenerator
                    ),
                    "Could not pre-expand a root edge."
            );

            safety++;

            require(
                    safety <= 128,
                    "Root pre-expansion exceeded safety limit."
            );
        }

        List<PositionNode> rootChildren =
                new ArrayList<>();

        for (SearchEdge edge :
                root.getOutgoingEdges()) {
            PositionNode child =
                    edge.getTarget();

            if (child != null
                    && !rootChildren.contains(
                    child
            )) {
                rootChildren.add(
                        child
                );
            }
        }

        ExplorationScheduler scheduler =
                new ExplorationScheduler(
                        graph,
                        moveGenerator,
                        root,
                        ExplorationScheduler.Mode.HYBRID
                );

        return new CoverageFixture(
                graph,
                moveGenerator,
                root,
                rootChildren,
                scheduler
        );
    }


    // =========================================================
    // Assertion helper
    // =========================================================

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


    private record Fixture(
            PositionGraph graph,
            MoveGenerator moveGenerator,
            PositionNode root,
            ExplorationScheduler scheduler
    ) {
    }


    private record CoverageFixture(
            PositionGraph graph,
            MoveGenerator moveGenerator,
            PositionNode root,
            List<PositionNode> rootChildren,
            ExplorationScheduler scheduler
    ) {
    }
}

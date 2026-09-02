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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Milestone 55.
 *
 * Batch-complete the three remaining SPLIT Tier-1 families:
 *
 *     KR-KP
 *     KB-KP
 *     KN-KP
 *
 * For each material class this main:
 *
 *   1. solves the canonical split orientation (WHITE non-pawn / BLACK pawn);
 *   2. checks WDL metadata consistency;
 *   3. samples the exact Bellman recurrence on internal-only states;
 *   4. saves <assetStem>-canonical.ftb.gz;
 *   5. reloads and verifies full content-digest identity;
 *   6. checks generic-service split normalization in both physical colors;
 *   7. checks ExactEndgameTablebase probe/move analysis/best moves;
 *   8. checks EndgameMoveController normal mode chooses an exact-optimal move.
 *
 * KQ-KP already proved this split architecture end-to-end in M53/M54, so the
 * remaining three equivalent families are intentionally batched here.
 */
public final class FourPieceTierOneRemainingSplitBatchMain {

    private static final int DEFAULT_BELLMAN_SAMPLES =
            500_000;

    private static final Family[] FAMILIES = {
            new Family(
                    PieceType.ROOK,
                    "KR-KP",
                    0x4B524B505501L
            ),
            new Family(
                    PieceType.BISHOP,
                    "KB-KP",
                    0x4B424B505502L
            ),
            new Family(
                    PieceType.KNIGHT,
                    "KN-KP",
                    0x4B4E4B505503L
            )
    };

    private FourPieceTierOneRemainingSplitBatchMain() {
    }

    public static void main(
            String[] args
    ) throws Exception {

        if (args.length > 2) {
            throw new IllegalArgumentException(
                    "Usage: FourPieceTierOneRemainingSplitBatchMain "
                            + "[tablebase-directory] [bellman-samples]"
            );
        }

        Path directory =
                args.length >= 1
                        ? Path.of(
                        args[0]
                )
                        : FourPieceTierOneTablebaseService.DEFAULT_DIRECTORY;

        int bellmanSamples =
                args.length >= 2
                        ? Integer.parseInt(
                        args[1]
                )
                        : DEFAULT_BELLMAN_SAMPLES;

        if (bellmanSamples < 1) {
            throw new IllegalArgumentException(
                    "Bellman sample count must be positive."
            );
        }

        directory =
                directory.toAbsolutePath()
                        .normalize();

        System.out.println(
                "Remaining SPLIT Tier-1 batch gate"
        );
        System.out.println(
                "================================="
        );
        System.out.println(
                "Directory: "
                        + directory
        );
        System.out.println(
                "Bellman samples per family: "
                        + bellmanSamples
        );

        long batchStarted =
                System.nanoTime();

        BatchSummary[] summaries =
                new BatchSummary[FAMILIES.length];

        for (int i = 0;
             i < FAMILIES.length;
             i++) {

            Family family =
                    FAMILIES[i];

            System.out.println();
            System.out.println(
                    "============================================================"
            );
            System.out.println(
                    "["
                            + (i + 1)
                            + " / "
                            + FAMILIES.length
                            + "] "
                            + family.label()
            );
            System.out.println(
                    "Canonical orientation: WHITE "
                            + family.nonPawnType()
                            + " / BLACK pawn"
            );
            System.out.println(
                    "============================================================"
            );

            summaries[i] =
                    solveValidatePersistAndRoute(
                            directory,
                            family,
                            bellmanSamples
                    );

            System.gc();
        }

        double batchSeconds =
                (System.nanoTime()
                        - batchStarted)
                        / 1_000_000_000.0;

        System.out.println();
        System.out.println(
                "============================================================"
        );
        System.out.println(
                "BATCH SUMMARY"
        );
        System.out.println(
                "============================================================"
        );

        for (BatchSummary summary :
                summaries) {

            System.out.println(
                    summary.label()
                            + ":"
            );
            System.out.println(
                    "  legal: "
                            + summary.legalStates()
            );
            System.out.println(
                    "  WIN: "
                            + summary.wins()
            );
            System.out.println(
                    "  LOSS: "
                            + summary.losses()
            );
            System.out.println(
                    "  DRAW: "
                            + summary.draws()
            );
            System.out.println(
                    "  max DTM: "
                            + summary.maximumDistance()
            );
            System.out.println(
                    "  compressed: "
                            + String.format(
                            "%.2f MiB",
                            summary.compressedMiB()
                    )
            );
            System.out.println(
                    "  digest: "
                            + summary.digest()
            );
        }

        System.out.printf(
                "Total batch elapsed: %.3f sec%n",
                batchSeconds
        );

        System.out.println();
        System.out.println(
                "REMAINING SPLIT TIER-1 BATCH GATE PASSED"
        );
        System.out.println(
                "TIER-1 COMPLETE: ALL 8 ONE-PAWN FOUR-PIECE FAMILIES RUNTIME-READY"
        );
        System.out.println(
                "NEXT: TIER-2 KPPK, THEN EP-AWARE KP-KP"
        );
    }

    private static BatchSummary solveValidatePersistAndRoute(
            Path directory,
            Family family,
            int bellmanSamples
    ) throws Exception {

        FourPieceMaterialClass material =
                FourPieceMaterialClass.split(
                        family.nonPawnType(),
                        PieceType.PAWN
                );

        FourPieceTierOneTablebaseService service =
                new FourPieceTierOneTablebaseService(
                        directory
                );

        Path path =
                service.assetPath(
                        material
                );

        String expectedFileName =
                material.assetStem()
                        + "-canonical.ftb.gz";

        if (!path.getFileName()
                .toString()
                .equals(
                        expectedFileName
                )) {
            throw new IllegalStateException(
                    family.label()
                            + " split asset-path mismatch."
                            + "\nExpected: "
                            + expectedFileName
                            + "\nActual:   "
                            + path.getFileName()
            );
        }

        System.out.println(
                "Asset: "
                        + path
        );

        System.out.println();
        System.out.println(
                "Solving..."
        );

        long solveStarted =
                System.nanoTime();

        FourPieceTierOneRetrogradeBuilder.Result result =
                new FourPieceTierOneRetrogradeBuilder(
                        material,
                        true
                ).build();

        double solveSeconds =
                (System.nanoTime()
                        - solveStarted)
                        / 1_000_000_000.0;

        FourPieceGenericTablebase solved =
                result.toTablebase();

        verifyMetadataConsistency(
                family,
                material,
                solved
        );

        String solvedDigest =
                FourPieceGenericTablebaseCodec.contentDigest(
                        solved
                );

        System.out.println();
        System.out.printf(
                "Solve elapsed: %.3f sec%n",
                solveSeconds
        );
        System.out.println(
                "Legal states: "
                        + solved.legalStates()
        );
        System.out.println(
                "WIN: "
                        + solved.wins()
        );
        System.out.println(
                "LOSS: "
                        + solved.losses()
        );
        System.out.println(
                "DRAW: "
                        + solved.draws()
        );
        System.out.println(
                "Maximum DTM: "
                        + solved.maximumDistance()
        );
        System.out.println(
                "Solved digest: "
                        + solvedDigest
        );

        BellmanCounts bellman =
                validateBellman(
                        family,
                        material,
                        result,
                        bellmanSamples,
                        family.seed()
                );

        printBellman(
                family,
                bellman
        );

        System.out.println();
        System.out.println(
                "Saving..."
        );

        long saveStarted =
                System.nanoTime();

        FourPieceGenericTablebaseCodec.save(
                solved,
                path
        );

        double saveSeconds =
                (System.nanoTime()
                        - saveStarted)
                        / 1_000_000_000.0;

        double compressedMiB =
                Files.size(
                        path
                ) / (1024.0 * 1024.0);

        System.out.printf(
                "Saved in %.3f sec%n",
                saveSeconds
        );
        System.out.printf(
                "Compressed size: %.2f MiB%n",
                compressedMiB
        );

        System.out.println();
        System.out.println(
                "Reloading..."
        );

        long loadStarted =
                System.nanoTime();

        FourPieceGenericTablebase loaded =
                FourPieceGenericTablebaseCodec.load(
                        path
                );

        double loadSeconds =
                (System.nanoTime()
                        - loadStarted)
                        / 1_000_000_000.0;

        verifyMetadataIdentity(
                family,
                solved,
                loaded
        );

        String loadedDigest =
                FourPieceGenericTablebaseCodec.contentDigest(
                        loaded
                );

        if (!solvedDigest.equals(
                loadedDigest
        )) {
            throw new IllegalStateException(
                    family.label()
                            + " persisted digest mismatch."
                            + "\nSolved: "
                            + solvedDigest
                            + "\nLoaded: "
                            + loadedDigest
            );
        }

        System.out.printf(
                "Loaded in %.3f sec%n",
                loadSeconds
        );
        System.out.println(
                "Loaded digest: "
                        + loadedDigest
        );
        System.out.println(
                "Persistence digest identity: PASSED"
        );

        int fixtureState =
                findDecisiveFixture(
                        family,
                        loaded
                );

        Position canonical =
                canonicalPosition(
                        fixtureState,
                        family.nonPawnType()
                );

        Position reversed =
                colorReversedPosition(
                        fixtureState,
                        family.nonPawnType()
                );

        verifyGenericService(
                family,
                service,
                material,
                fixtureState,
                canonical,
                reversed
        );

        verifyExactFacade(
                family,
                fixtureState,
                loaded,
                canonical,
                reversed
        );

        verifyController(
                family,
                canonical,
                "canonical WHITE-"
                        + shortPiece(
                        family.nonPawnType()
                )
                        + " / BLACK-P",
                family.seed()
                        ^ 0x1111L
        );

        verifyController(
                family,
                reversed,
                "reversed BLACK-"
                        + shortPiece(
                        family.nonPawnType()
                )
                        + " / WHITE-P",
                family.seed()
                        ^ 0x2222L
        );

        service.clearCache();

        if (service.loadedCount()
                != 0) {
            throw new IllegalStateException(
                    family.label()
                            + " Tier-1 cache did not clear."
            );
        }

        System.out.println();
        System.out.println(
                family.label()
                        + " cache clear: PASSED"
        );
        System.out.println(
                family.label()
                        + " COMPLETE"
        );

        return new BatchSummary(
                family.label(),
                solved.legalStates(),
                solved.wins(),
                solved.losses(),
                solved.draws(),
                solved.maximumDistance(),
                compressedMiB,
                solvedDigest
        );
    }

    private static void verifyMetadataConsistency(
            Family family,
            FourPieceMaterialClass material,
            FourPieceGenericTablebase tablebase
    ) {

        if (!tablebase.material()
                .equals(
                        material
                )) {
            throw new IllegalStateException(
                    family.label()
                            + " solved material mismatch."
            );
        }

        if (!tablebase.sameSideOwnerIsWhite()) {
            throw new IllegalStateException(
                    family.label()
                            + " canonical split-orientation flag is not true."
            );
        }

        long total =
                tablebase.wins()
                        + tablebase.losses()
                        + tablebase.draws();

        if (total
                != tablebase.legalStates()) {
            throw new IllegalStateException(
                    family.label()
                            + " WDL counts do not sum to legal states."
            );
        }

        if (tablebase.legalStates()
                <= 0
                || tablebase.maximumDistance()
                < 0) {
            throw new IllegalStateException(
                    family.label()
                            + " solved metadata is invalid."
            );
        }

        System.out.println(
                "Solve metadata consistency: PASSED"
        );
    }

    private static void verifyMetadataIdentity(
            Family family,
            FourPieceGenericTablebase expected,
            FourPieceGenericTablebase actual
    ) {

        if (!expected.material()
                .equals(
                        actual.material()
                )
                || expected.sameSideOwnerIsWhite()
                != actual.sameSideOwnerIsWhite()
                || expected.legalStates()
                != actual.legalStates()
                || expected.wins()
                != actual.wins()
                || expected.losses()
                != actual.losses()
                || expected.draws()
                != actual.draws()
                || expected.maximumDistance()
                != actual.maximumDistance()) {

            throw new IllegalStateException(
                    family.label()
                            + " reloaded metadata mismatch."
            );
        }
    }

    private static BellmanCounts validateBellman(
            Family family,
            FourPieceMaterialClass material,
            FourPieceTierOneRetrogradeBuilder.Result result,
            int samples,
            long seed
    ) {

        Random random =
                new Random(
                        seed
                );

        FourPieceTierOnePrimitiveMoveGenerator.Buffer moves =
                new FourPieceTierOnePrimitiveMoveGenerator.Buffer(
                        64
                );

        int checked =
                0;

        long attempts =
                0;

        long edges =
                0;

        long wins =
                0;

        long losses =
                0;

        long draws =
                0;

        long terminals =
                0;

        while (checked
                < samples) {

            attempts++;

            int state =
                    randomState(
                            random
                    );

            byte outcome =
                    result.outcome()[state];

            if (outcome
                    == FourPieceTablebase.INVALID) {
                continue;
            }

            int moveCount =
                    FourPieceTierOnePrimitiveMoveGenerator
                            .generateLegalSuccessors(
                                    state,
                                    material,
                                    true,
                                    moves
                            );

            if (moveCount
                    == 0) {

                terminals++;

                if (outcome
                        == FourPieceTablebase.LOSS
                        && result.distance()[state]
                        != 0) {
                    failBellman(
                            family,
                            "terminal LOSS DTM != 0",
                            state,
                            outcome,
                            result.distance()[state]
                    );
                }

                if (outcome
                        != FourPieceTablebase.LOSS
                        && outcome
                        != FourPieceTablebase.DRAW) {
                    failBellman(
                            family,
                            "terminal is neither LOSS nor DRAW",
                            state,
                            outcome,
                            result.distance()[state]
                    );
                }

                checked++;
                continue;
            }

            boolean external =
                    false;

            int lossChildren =
                    0;

            int winChildren =
                    0;

            int drawChildren =
                    0;

            int minimumLossDistance =
                    Integer.MAX_VALUE;

            int maximumWinDistance =
                    Integer.MIN_VALUE;

            for (int i = 0;
                 i < moveCount;
                 i++) {

                if (moves.boundaryType(
                        i
                ) != FourPieceTierOnePrimitiveMoveGenerator.BOUNDARY_NONE) {

                    external =
                            true;

                    break;
                }

                edges++;

                int child =
                        moves.state(
                                i
                        );

                byte childOutcome =
                        result.outcome()[child];

                short childDistance =
                        result.distance()[child];

                if (childOutcome
                        == FourPieceTablebase.LOSS) {

                    lossChildren++;

                    minimumLossDistance =
                            Math.min(
                                    minimumLossDistance,
                                    childDistance
                            );

                } else if (childOutcome
                        == FourPieceTablebase.WIN) {

                    winChildren++;

                    maximumWinDistance =
                            Math.max(
                                    maximumWinDistance,
                                    childDistance
                            );

                } else if (childOutcome
                        == FourPieceTablebase.DRAW) {

                    drawChildren++;

                } else {

                    failBellman(
                            family,
                            "same-class child unresolved/invalid",
                            child,
                            childOutcome,
                            childDistance
                    );
                }
            }

            /*
             * As in M53, dependency-edge states are skipped here. Their exact
             * KXK/KPK/Tier-0 boundary values are already incorporated by the
             * dependency-aware retrograde builder. This gate checks the full
             * Bellman recurrence wherever every legal child remains in-family.
             */
            if (external) {
                continue;
            }

            short distance =
                    result.distance()[state];

            if (outcome
                    == FourPieceTablebase.WIN) {

                wins++;

                if (lossChildren
                        == 0) {
                    failBellman(
                            family,
                            "WIN has no LOSS child",
                            state,
                            outcome,
                            distance
                    );
                }

                if (distance
                        != minimumLossDistance + 1) {
                    failBellman(
                            family,
                            "WIN DTM recurrence mismatch",
                            state,
                            outcome,
                            distance
                    );
                }

            } else if (outcome
                    == FourPieceTablebase.LOSS) {

                losses++;

                if (winChildren
                        != moveCount) {
                    failBellman(
                            family,
                            "LOSS does not have all WIN children",
                            state,
                            outcome,
                            distance
                    );
                }

                if (distance
                        != maximumWinDistance + 1) {
                    failBellman(
                            family,
                            "LOSS DTM recurrence mismatch",
                            state,
                            outcome,
                            distance
                    );
                }

            } else if (outcome
                    == FourPieceTablebase.DRAW) {

                draws++;

                if (lossChildren
                        != 0) {
                    failBellman(
                            family,
                            "DRAW has LOSS child",
                            state,
                            outcome,
                            distance
                    );
                }

                if (drawChildren
                        == 0) {
                    failBellman(
                            family,
                            "DRAW has no DRAW child",
                            state,
                            outcome,
                            distance
                    );
                }

                if (distance
                        != -1) {
                    failBellman(
                            family,
                            "DRAW DTM != -1",
                            state,
                            outcome,
                            distance
                    );
                }

            } else {

                failBellman(
                        family,
                        "legal state unresolved/invalid",
                        state,
                        outcome,
                        distance
                );
            }

            checked++;
        }

        return new BellmanCounts(
                checked,
                attempts,
                edges,
                wins,
                losses,
                draws,
                terminals
        );
    }

    private static void printBellman(
            Family family,
            BellmanCounts counts
    ) {

        System.out.println();
        System.out.println(
                "Bellman recurrence — "
                        + family.label()
        );
        System.out.println(
                "===================================="
        );
        System.out.println(
                "Legal states checked: "
                        + counts.legalStatesChecked()
        );
        System.out.println(
                "Random attempts: "
                        + counts.randomAttempts()
        );
        System.out.println(
                "Same-class edges checked: "
                        + counts.sameClassEdgesChecked()
        );
        System.out.println(
                "WIN states checked: "
                        + counts.winsChecked()
        );
        System.out.println(
                "LOSS states checked: "
                        + counts.lossesChecked()
        );
        System.out.println(
                "DRAW states checked: "
                        + counts.drawsChecked()
        );
        System.out.println(
                "Terminal states checked: "
                        + counts.terminalStatesChecked()
        );
        System.out.println(
                "Bellman mismatches: 0"
        );
        System.out.println(
                "PASSED"
        );
    }

    private static int findDecisiveFixture(
            Family family,
            FourPieceGenericTablebase tablebase
    ) {

        for (int state = 0;
             state < FourPieceGenericPrimitiveState.STATE_COUNT;
             state++) {

            byte outcome =
                    tablebase.outcome(
                            state
                    );

            if ((outcome
                    == FourPieceTablebase.WIN
                    || outcome
                    == FourPieceTablebase.LOSS)
                    && tablebase.distance(
                    state
            ) >= 4) {

                return state;
            }
        }

        throw new IllegalStateException(
                "Unable to find decisive "
                        + family.label()
                        + " runtime fixture."
        );
    }

    private static void verifyGenericService(
            Family family,
            FourPieceTierOneTablebaseService service,
            FourPieceMaterialClass material,
            int fixtureState,
            Position canonical,
            Position reversed
    ) throws Exception {

        Optional<FourPieceTierOneTablebaseService.ProbeResult> canonicalOptional =
                service.probe(
                        canonical
                );

        Optional<FourPieceTierOneTablebaseService.ProbeResult> reversedOptional =
                service.probe(
                        reversed
                );

        if (canonicalOptional.isEmpty()
                || reversedOptional.isEmpty()) {
            throw new IllegalStateException(
                    "Generic Tier-1 service rejected "
                            + family.label()
                            + " runtime fixture."
            );
        }

        FourPieceTierOneTablebaseService.ProbeResult canonicalProbe =
                canonicalOptional.get();

        FourPieceTierOneTablebaseService.ProbeResult reversedProbe =
                reversedOptional.get();

        if (!canonicalProbe.material()
                .equals(
                        material
                )
                || !reversedProbe.material()
                .equals(
                        material
                )) {
            throw new IllegalStateException(
                    "Generic Tier-1 service returned wrong material for "
                            + family.label()
                            + "."
            );
        }

        if (canonicalProbe.primitiveState()
                != fixtureState
                || reversedProbe.primitiveState()
                != fixtureState) {

            throw new IllegalStateException(
                    family.label()
                            + " split normalization changed primitive state."
            );
        }

        if (canonicalProbe.outcome()
                != reversedProbe.outcome()
                || canonicalProbe.distance()
                != reversedProbe.distance()) {

            throw new IllegalStateException(
                    family.label()
                            + " split normalization changed WDL/DTM."
            );
        }

        if (canonicalProbe.colorReversed()
                || !reversedProbe.colorReversed()) {

            throw new IllegalStateException(
                    family.label()
                            + " split color-reversal flags are incorrect."
            );
        }

        System.out.println();
        System.out.println(
                "Generic-service SPLIT normalization"
        );
        System.out.println(
                "==================================="
        );
        System.out.println(
                "Canonical state: "
                        + fixtureState
        );
        System.out.println(
                "Outcome: "
                        + canonicalProbe.outcomeName()
        );
        System.out.println(
                "DTM: "
                        + canonicalProbe.distance()
        );
        System.out.println(
                "Canonical primitive state: "
                        + canonicalProbe.primitiveState()
        );
        System.out.println(
                "Reversed normalized state: "
                        + reversedProbe.primitiveState()
        );
        System.out.println(
                "SPLIT normalization: PASSED"
        );
    }

    private static void verifyExactFacade(
            Family family,
            int fixtureState,
            FourPieceGenericTablebase loaded,
            Position canonical,
            Position reversed
    ) {

        ExactEndgameTablebase tablebase =
                ExactEndgameTablebase.tierZeroCatalog();

        ExactEndgameTablebase.Outcome expectedOutcome =
                mapPrimitive(
                        loaded.outcome(
                                fixtureState
                        )
                );

        int expectedDistance =
                loaded.distance(
                        fixtureState
                );

        verifyExactPosition(
                family,
                tablebase,
                canonical,
                "canonical",
                expectedOutcome,
                expectedDistance
        );

        verifyExactPosition(
                family,
                tablebase,
                reversed,
                "reversed",
                expectedOutcome,
                expectedDistance
        );
    }

    private static void verifyExactPosition(
            Family family,
            ExactEndgameTablebase tablebase,
            Position position,
            String orientation,
            ExactEndgameTablebase.Outcome expectedOutcome,
            int expectedDistance
    ) {

        ExactEndgameTablebase.Probe probe =
                tablebase.probe(
                        position
                );

        if (probe.outcome()
                != expectedOutcome
                || probe.mateDistance()
                != expectedDistance) {

            throw new IllegalStateException(
                    "ExactEndgameTablebase "
                            + family.label()
                            + " "
                            + orientation
                            + " probe mismatch."
            );
        }

        List<ExactEndgameTablebase.MoveAnalysis> analyses =
                tablebase.analyzeMoves(
                        position
                );

        List<Move> bestMoves =
                tablebase.bestMoves(
                        position
                );

        if (analyses.isEmpty()
                || bestMoves.isEmpty()) {

            throw new IllegalStateException(
                    "Exact "
                            + family.label()
                            + " move classification is incomplete for "
                            + orientation
                            + "."
            );
        }

        int optimal =
                0;

        for (ExactEndgameTablebase.MoveAnalysis analysis :
                analyses) {

            if (analysis.childOutcome()
                    == ExactEndgameTablebase.Outcome.UNSUPPORTED) {

                throw new IllegalStateException(
                        "Exact "
                                + family.label()
                                + " analysis contains unsupported child."
                );
            }

            if (analysis.optimal()) {

                optimal++;

                if (!bestMoves.contains(
                        analysis.move()
                )) {
                    throw new IllegalStateException(
                            "Optimal "
                                    + family.label()
                                    + " move missing from bestMoves()."
                    );
                }
            }
        }

        if (optimal
                != bestMoves.size()) {
            throw new IllegalStateException(
                    family.label()
                            + " bestMoves()/analyzeMoves() optimal-count mismatch."
            );
        }

        System.out.println();
        System.out.println(
                "Exact facade — "
                        + family.label()
                        + " "
                        + orientation
        );
        System.out.println(
                "  outcome: "
                        + probe.outcome()
        );
        System.out.println(
                "  DTM: "
                        + probe.mateDistance()
        );
        System.out.println(
                "  legal exact moves: "
                        + analyses.size()
        );
        System.out.println(
                "  optimal moves: "
                        + optimal
        );
        System.out.println(
                "  PASSED"
        );
    }

    private static void verifyController(
            Family family,
            Position position,
            String label,
            long seed
    ) {

        EndgameMoveController controller =
                new EndgameMoveController(
                        new Random(
                                seed
                        )
                );

        if (!controller.supports(
                position
        )) {
            throw new IllegalStateException(
                    "EndgameMoveController does not support "
                            + family.label()
                            + " "
                            + label
                            + "."
            );
        }

        controller.setPracticeMode(
                false
        );

        List<Move> exact =
                controller.exactBestMoves(
                        position
                );

        if (exact.isEmpty()) {
            throw new IllegalStateException(
                    "Controller returned no exact "
                            + family.label()
                            + " moves for "
                            + label
                            + "."
            );
        }

        Move chosen =
                controller.chooseMove(
                        position
                );

        if (chosen == null
                || !exact.contains(
                chosen
        )) {

            throw new IllegalStateException(
                    "Normal-mode controller "
                            + family.label()
                            + " move is not exact-optimal for "
                            + label
                            + "."
            );
        }

        System.out.println();
        System.out.println(
                "Controller — "
                        + family.label()
                        + " "
                        + label
        );
        System.out.println(
                "  supported: true"
        );
        System.out.println(
                "  exact best moves: "
                        + exact.size()
        );
        System.out.println(
                "  chosen move: "
                        + chosen
        );
        System.out.println(
                "  chosen move is exact-optimal: true"
        );
        System.out.println(
                "  PASSED"
        );
    }

    /**
     * Canonical SPLIT Tier-1 orientation:
     * WHITE non-pawn / BLACK pawn.
     */
    private static Position canonicalPosition(
            int state,
            PieceType nonPawnType
    ) {

        Board board =
                new Board();

        board.setPiece(
                square(
                        FourPieceGenericPrimitiveState.whiteKing(
                                state
                        )
                ),
                new Piece(
                        PieceType.KING,
                        Color.WHITE
                )
        );

        board.setPiece(
                square(
                        FourPieceGenericPrimitiveState.blackKing(
                                state
                        )
                ),
                new Piece(
                        PieceType.KING,
                        Color.BLACK
                )
        );

        board.setPiece(
                square(
                        FourPieceGenericPrimitiveState.firstExtra(
                                state
                        )
                ),
                new Piece(
                        nonPawnType,
                        Color.WHITE
                )
        );

        board.setPiece(
                square(
                        FourPieceGenericPrimitiveState.secondExtra(
                                state
                        )
                ),
                new Piece(
                        PieceType.PAWN,
                        Color.BLACK
                )
        );

        return position(
                board,
                FourPieceGenericPrimitiveState.blackToMove(
                        state
                )
                        ? Color.BLACK
                        : Color.WHITE
        );
    }

    /**
     * Physical color reverse:
     * BLACK non-pawn / WHITE pawn.
     *
     * Rank-flipping is required so pawn direction is preserved under the
     * canonical color swap.
     */
    private static Position colorReversedPosition(
            int canonicalState,
            PieceType nonPawnType
    ) {

        Board board =
                new Board();

        board.setPiece(
                square(
                        flipRank(
                                FourPieceGenericPrimitiveState.blackKing(
                                        canonicalState
                                )
                        )
                ),
                new Piece(
                        PieceType.KING,
                        Color.WHITE
                )
        );

        board.setPiece(
                square(
                        flipRank(
                                FourPieceGenericPrimitiveState.whiteKing(
                                        canonicalState
                                )
                        )
                ),
                new Piece(
                        PieceType.KING,
                        Color.BLACK
                )
        );

        board.setPiece(
                square(
                        flipRank(
                                FourPieceGenericPrimitiveState.firstExtra(
                                        canonicalState
                                )
                        )
                ),
                new Piece(
                        nonPawnType,
                        Color.BLACK
                )
        );

        board.setPiece(
                square(
                        flipRank(
                                FourPieceGenericPrimitiveState.secondExtra(
                                        canonicalState
                                )
                        )
                ),
                new Piece(
                        PieceType.PAWN,
                        Color.WHITE
                )
        );

        Color canonicalSide =
                FourPieceGenericPrimitiveState.blackToMove(
                        canonicalState
                )
                        ? Color.BLACK
                        : Color.WHITE;

        return position(
                board,
                canonicalSide.opposite()
        );
    }

    private static Position position(
            Board board,
            Color sideToMove
    ) {

        Position temporary =
                new Position(
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

        Map<PositionKey, Integer> repetitions =
                new HashMap<>();

        repetitions.put(
                temporary.createPositionKey(),
                1
        );

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
                repetitions
        );
    }

    private static int randomState(
            Random random
    ) {

        while (true) {

            int whiteKing =
                    random.nextInt(
                            64
                    );

            int blackKing =
                    random.nextInt(
                            64
                    );

            int nonPawn =
                    random.nextInt(
                            64
                    );

            int pawn =
                    (1 + random.nextInt(
                            6
                    )) * 8
                            + random.nextInt(
                            8
                    );

            if (whiteKing
                    == blackKing
                    || whiteKing
                    == nonPawn
                    || whiteKing
                    == pawn
                    || blackKing
                    == nonPawn
                    || blackKing
                    == pawn
                    || nonPawn
                    == pawn) {
                continue;
            }

            return FourPieceGenericPrimitiveState.encode(
                    whiteKing,
                    blackKing,
                    nonPawn,
                    pawn,
                    random.nextBoolean()
            );
        }
    }

    private static void failBellman(
            Family family,
            String why,
            int state,
            byte outcome,
            short distance
    ) {

        throw new IllegalStateException(
                family.label()
                        + " Bellman validation failed: "
                        + why
                        + "\n  state: "
                        + describe(
                        state
                )
                        + "\n  outcome: "
                        + outcomeName(
                        outcome
                )
                        + "\n  DTM: "
                        + distance
        );
    }

    private static String describe(
            int state
    ) {

        return "WK="
                + algebraic(
                FourPieceGenericPrimitiveState.whiteKing(
                        state
                )
        )
                + " BK="
                + algebraic(
                FourPieceGenericPrimitiveState.blackKing(
                        state
                )
        )
                + " X="
                + algebraic(
                FourPieceGenericPrimitiveState.firstExtra(
                        state
                )
        )
                + " P="
                + algebraic(
                FourPieceGenericPrimitiveState.secondExtra(
                        state
                )
        )
                + " stm="
                + (FourPieceGenericPrimitiveState.blackToMove(
                state
        )
                ? "BLACK"
                : "WHITE")
                + " ["
                + state
                + "]";
    }

    private static String algebraic(
            int square
    ) {

        return ""
                + (char) ('a' + (square & 7))
                + ((square >>> 3) + 1);
    }

    private static String shortPiece(
            PieceType type
    ) {

        return switch (type) {
            case QUEEN -> "Q";
            case ROOK -> "R";
            case BISHOP -> "B";
            case KNIGHT -> "N";
            case PAWN -> "P";
            case KING -> "K";
        };
    }

    private static ExactEndgameTablebase.Outcome mapPrimitive(
            byte outcome
    ) {

        if (outcome
                == FourPieceTablebase.WIN) {
            return ExactEndgameTablebase.Outcome.WIN;
        }

        if (outcome
                == FourPieceTablebase.LOSS) {
            return ExactEndgameTablebase.Outcome.LOSS;
        }

        if (outcome
                == FourPieceTablebase.DRAW) {
            return ExactEndgameTablebase.Outcome.DRAW;
        }

        return ExactEndgameTablebase.Outcome.UNSUPPORTED;
    }

    private static String outcomeName(
            byte outcome
    ) {

        if (outcome
                == FourPieceTablebase.WIN) {
            return "WIN";
        }

        if (outcome
                == FourPieceTablebase.LOSS) {
            return "LOSS";
        }

        if (outcome
                == FourPieceTablebase.DRAW) {
            return "DRAW";
        }

        if (outcome
                == FourPieceTablebase.INVALID) {
            return "INVALID";
        }

        return "UNKNOWN";
    }

    private static Square square(
            int primitive
    ) {

        return new Square(
                primitive & 7,
                primitive >>> 3
        );
    }

    private static int flipRank(
            int square
    ) {

        return (7 - (square >>> 3)) * 8
                + (square & 7);
    }

    private record Family(
            PieceType nonPawnType,
            String label,
            long seed
    ) {
    }

    private record BellmanCounts(
            int legalStatesChecked,
            long randomAttempts,
            long sameClassEdgesChecked,
            long winsChecked,
            long lossesChecked,
            long drawsChecked,
            long terminalStatesChecked
    ) {
    }

    private record BatchSummary(
            String label,
            long legalStates,
            long wins,
            long losses,
            long draws,
            int maximumDistance,
            double compressedMiB,
            String digest
    ) {
    }
}

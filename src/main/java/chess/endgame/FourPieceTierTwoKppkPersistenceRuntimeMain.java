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
 * Milestone 59.
 *
 * Persist the M58-validated canonical WHITE-owned KPPK tablebase, reload it,
 * verify its exact digest, then prove the already-Tier-2 KPPK runtime path:
 *
 *     KPPK asset
 *         -> FourPieceTierTwoKppkTablebaseService
 *         -> ExactEndgameTablebase
 *         -> exact move analysis / best moves
 *         -> EndgameMoveController
 *
 * Both physical WHITE-owned and BLACK-owned orientations are checked.
 */
public final class FourPieceTierTwoKppkPersistenceRuntimeMain {

    private static final String EXPECTED_WHITE_DIGEST =
            "0c4609bf0fddb5dc0f92c230866f45c218c24d76d6a32435d12983a57e8b4638";

    private static final FourPieceMaterialClass MATERIAL =
            FourPieceMaterialClass.sameSide(
                    PieceType.PAWN,
                    PieceType.PAWN
            );

    private FourPieceTierTwoKppkPersistenceRuntimeMain() {
    }

    public static void main(
            String[] args
    ) throws Exception {

        if (args.length > 1) {
            throw new IllegalArgumentException(
                    "Usage: FourPieceTierTwoKppkPersistenceRuntimeMain [tablebase-directory]"
            );
        }

        Path directory =
                args.length == 0
                        ? FourPieceTierTwoKppkTablebaseService.DEFAULT_DIRECTORY
                        : Path.of(
                        args[0]
                );

        directory =
                directory.toAbsolutePath()
                        .normalize();

        FourPieceTierTwoKppkTablebaseService service =
                new FourPieceTierTwoKppkTablebaseService(
                        directory
                );

        Path path =
                service.assetPath(
                        MATERIAL
                );

        System.out.println(
                "KPPK Tier-2 persistence / exact-runtime gate"
        );
        System.out.println(
                "==========================================="
        );
        System.out.println(
                "Output: "
                        + path
        );

        System.out.println();
        System.out.println(
                "Building canonical WHITE-owned KPPK..."
        );

        FourPieceTierTwoKppkRetrogradeBuilder.Result result =
                new FourPieceTierTwoKppkRetrogradeBuilder(
                        true
                ).build();

        FourPieceGenericTablebase solved =
                result.toTablebase();

        String solvedDigest =
                FourPieceGenericTablebaseCodec.contentDigest(
                        solved
                );

        System.out.println(
                "Solved digest: "
                        + solvedDigest
        );

        if (!EXPECTED_WHITE_DIGEST.equals(
                solvedDigest
        )) {
            throw new IllegalStateException(
                    "KPPK solve digest changed."
                            + "\nExpected: "
                            + EXPECTED_WHITE_DIGEST
                            + "\nActual:   "
                            + solvedDigest
            );
        }

        verifyKnownMetadata(
                solved
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

        System.out.printf(
                "Saved in %.3f sec%n",
                saveSeconds
        );

        System.out.printf(
                "Compressed size: %.2f MiB%n",
                Files.size(
                        path
                ) / (1024.0 * 1024.0)
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

        System.out.printf(
                "Loaded in %.3f sec%n",
                loadSeconds
        );

        verifyMetadataIdentity(
                solved,
                loaded
        );

        String loadedDigest =
                FourPieceGenericTablebaseCodec.contentDigest(
                        loaded
                );

        System.out.println(
                "Loaded digest: "
                        + loadedDigest
        );

        if (!EXPECTED_WHITE_DIGEST.equals(
                loadedDigest
        )) {
            throw new IllegalStateException(
                    "Reloaded KPPK digest does not match M58."
            );
        }

        System.out.println(
                "Byte-for-byte persistence identity: PASSED"
        );

        int fixtureState =
                findDecisiveFixture(
                        loaded
                );

        Position white =
                canonicalPosition(
                        fixtureState
                );

        Position black =
                colorReversedPosition(
                        fixtureState
                );

        verifyGenericService(
                service,
                fixtureState,
                white,
                black
        );

        verifyExactFacade(
                fixtureState,
                loaded,
                white,
                black
        );

        verifyController(
                white,
                "WHITE-owned",
                0x4B50504B5901L
        );

        verifyController(
                black,
                "BLACK-owned",
                0x4B50504B5902L
        );

        service.clearCache();

        if (service.loadedCount()
                != 0) {
            throw new IllegalStateException(
                    "Tier-2 KPPK service cache did not clear."
            );
        }

        System.out.println();
        System.out.println(
                "Tier-2 KPPK cache clear: PASSED"
        );

        System.out.println();
        System.out.println(
                "KPPK PERSISTENCE / EXACT-RUNTIME GATE PASSED"
        );
        System.out.println(
                "29 / 30 FOUR-PIECE FAMILIES RUNTIME-READY\nNEXT: EP-AWARE KP-KP STATE REPRESENTATION / MOVE FOUNDATION"
        );
    }

    private static void verifyKnownMetadata(
            FourPieceGenericTablebase tablebase
    ) {

        if (!tablebase.material()
                .equals(
                        MATERIAL
                )) {
            throw new IllegalStateException(
                    "KPPK solved material mismatch."
            );
        }

        if (!tablebase.sameSideOwnerIsWhite()) {
            throw new IllegalStateException(
                    "KPPK canonical asset is not strong-WHITE."
            );
        }

        if (tablebase.legalStates()
                != 7_438_086L
                || tablebase.wins()
                != 3_555_030L
                || tablebase.losses()
                != 3_522_058L
                || tablebase.draws()
                != 360_998L
                || tablebase.maximumDistance()
                != 64) {

            throw new IllegalStateException(
                    "KPPK solved metadata no longer matches M48."
            );
        }

        System.out.println(
                "M58 metadata regression: PASSED"
        );
    }

    private static void verifyMetadataIdentity(
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
                    "Reloaded KPPK metadata does not match solved metadata."
            );
        }
    }

    private static int findDecisiveFixture(
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
                "Unable to find decisive KPPK runtime fixture."
        );
    }

    private static void verifyGenericService(
            FourPieceTierTwoKppkTablebaseService service,
            int fixtureState,
            Position white,
            Position black
    ) throws Exception {

        Optional<FourPieceTierTwoKppkTablebaseService.ProbeResult> whiteOptional =
                service.probe(
                        white
                );

        Optional<FourPieceTierTwoKppkTablebaseService.ProbeResult> blackOptional =
                service.probe(
                        black
                );

        if (whiteOptional.isEmpty()
                || blackOptional.isEmpty()) {
            throw new IllegalStateException(
                    "Tier-2 KPPK service rejected KPPK runtime fixture."
            );
        }

        FourPieceTierTwoKppkTablebaseService.ProbeResult whiteProbe =
                whiteOptional.get();

        FourPieceTierTwoKppkTablebaseService.ProbeResult blackProbe =
                blackOptional.get();

        if (!whiteProbe.material()
                .equals(
                        MATERIAL
                )
                || !blackProbe.material()
                .equals(
                        MATERIAL
                )) {
            throw new IllegalStateException(
                    "Tier-2 KPPK service returned wrong material for KPPK."
            );
        }

        if (whiteProbe.primitiveState()
                != fixtureState
                || blackProbe.primitiveState()
                != fixtureState) {

            throw new IllegalStateException(
                    "KPPK color normalization changed primitive state."
            );
        }

        if (whiteProbe.outcome()
                != blackProbe.outcome()
                || whiteProbe.distance()
                != blackProbe.distance()) {

            throw new IllegalStateException(
                    "KPPK color normalization changed WDL/DTM."
            );
        }

        if (whiteProbe.colorReversed()
                || !blackProbe.colorReversed()) {

            throw new IllegalStateException(
                    "KPPK color-reversal flags are incorrect."
            );
        }

        System.out.println();
        System.out.println(
                "Generic-service normalization fixture"
        );
        System.out.println(
                "====================================="
        );
        System.out.println(
                "Canonical state: "
                        + fixtureState
        );
        System.out.println(
                "Outcome: "
                        + whiteProbe.outcomeName()
        );
        System.out.println(
                "DTM: "
                        + whiteProbe.distance()
        );
        System.out.println(
                "Strong-WHITE primitive state: "
                        + whiteProbe.primitiveState()
        );
        System.out.println(
                "Strong-BLACK normalized state: "
                        + blackProbe.primitiveState()
        );
        System.out.println(
                "Color normalization: PASSED"
        );
    }

    private static void verifyExactFacade(
            int fixtureState,
            FourPieceGenericTablebase loaded,
            Position white,
            Position black
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
                tablebase,
                white,
                "WHITE-owned",
                expectedOutcome,
                expectedDistance
        );

        verifyExactPosition(
                tablebase,
                black,
                "BLACK-owned",
                expectedOutcome,
                expectedDistance
        );
    }

    private static void verifyExactPosition(
            ExactEndgameTablebase tablebase,
            Position position,
            String label,
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
                    "ExactEndgameTablebase KPPK "
                            + label
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
                    "Exact KPPK move classification is incomplete for "
                            + label
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
                        "Exact KPPK analysis contains unsupported child."
                );
            }

            if (analysis.optimal()) {
                optimal++;

                if (!bestMoves.contains(
                        analysis.move()
                )) {
                    throw new IllegalStateException(
                            "Optimal KPPK move missing from bestMoves()."
                    );
                }
            }
        }

        if (optimal
                != bestMoves.size()) {

            throw new IllegalStateException(
                    "KPPK bestMoves()/analyzeMoves() optimal-count mismatch."
            );
        }

        System.out.println();
        System.out.println(
                "Exact facade — KPPK "
                        + label
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
                    "EndgameMoveController does not support KPPK "
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
                    "Controller returned no exact KPPK moves for "
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
                    "Normal-mode controller KPPK move is not exact-optimal for "
                            + label
                            + "."
            );
        }

        System.out.println();
        System.out.println(
                "Controller — KPPK "
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

    private static Position canonicalPosition(
            int state
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
                        PieceType.PAWN,
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
                        Color.WHITE
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

    private static Position colorReversedPosition(
            int canonicalState
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
                        PieceType.PAWN,
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
                        Color.BLACK
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
}

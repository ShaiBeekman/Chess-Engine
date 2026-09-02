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
 * Milestone 51.
 *
 * Persist the M48-validated canonical KQ-KP (WHITE queen / BLACK pawn) tablebase, reload it,
 * verify its exact digest, then prove the already-generic Tier-1 runtime path:
 *
 *     KQ-KP asset
 *         -> FourPieceTierOneTablebaseService
 *         -> ExactEndgameTablebase
 *         -> exact move analysis / best moves
 *         -> EndgameMoveController
 *
 * Both physical split color orientations are checked.
 */
public final class FourPieceTierOneKqKpPersistenceRuntimeMain {

    private static final String EXPECTED_WHITE_DIGEST =
            "0d6dd145265e68c2f7586016e244a0ffdfcfaab392a8155069c42c3b2eea425c";

    private static final FourPieceMaterialClass MATERIAL =
            FourPieceMaterialClass.split(
                    PieceType.QUEEN,
                    PieceType.PAWN
            );

    private FourPieceTierOneKqKpPersistenceRuntimeMain() {
    }

    public static void main(
            String[] args
    ) throws Exception {

        if (args.length > 1) {
            throw new IllegalArgumentException(
                    "Usage: FourPieceTierOneKqKpPersistenceRuntimeMain [tablebase-directory]"
            );
        }

        Path directory =
                args.length == 0
                        ? FourPieceTierOneTablebaseService.DEFAULT_DIRECTORY
                        : Path.of(
                        args[0]
                );

        directory =
                directory.toAbsolutePath()
                        .normalize();

        FourPieceTierOneTablebaseService service =
                new FourPieceTierOneTablebaseService(
                        directory
                );

        Path path =
                service.assetPath(
                        MATERIAL
                );

        if (!path.getFileName()
                .toString()
                .equals(
                        "KQ-KP-canonical.ftb.gz"
                )) {
            throw new IllegalStateException(
                    "KQ-KP SPLIT asset path regression: "
                            + path
            );
        }

        System.out.println(
                "KQ-KP Tier-1 persistence / exact-runtime gate"
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
                "Building canonical KQ-KP (WHITE queen / BLACK pawn)..."
        );

        FourPieceTierOneRetrogradeBuilder.Result result =
                new FourPieceTierOneRetrogradeBuilder(
                        MATERIAL,
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
                    "KQ-KP solve digest changed."
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
                    "Reloaded KQ-KP digest does not match M53."
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
                "canonical WHITE-Q / BLACK-P",
                0x4B4E504B5101L
        );

        verifyController(
                black,
                "reversed BLACK-Q / WHITE-P",
                0x4B4E504B5102L
        );

        service.clearCache();

        if (service.loadedCount()
                != 0) {
            throw new IllegalStateException(
                    "Generic Tier-1 service cache did not clear."
            );
        }

        System.out.println();
        System.out.println(
                "Generic Tier-1 cache clear: PASSED"
        );

        System.out.println();
        System.out.println(
                "KQ-KP PERSISTENCE / EXACT-RUNTIME GATE PASSED"
        );
        System.out.println(
                "NEXT: BATCH SOLVE / VALIDATE / PERSIST KR-KP, KB-KP, KN-KP"
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
                    "KQ-KP solved material mismatch."
            );
        }

        if (!tablebase.sameSideOwnerIsWhite()) {
            throw new IllegalStateException(
                    "KQ-KP canonical split-orientation flag is not true."
            );
        }

        if (tablebase.legalStates()
                != 16_704_944L
                || tablebase.wins()
                != 7_471_214L
                || tablebase.losses()
                != 7_987_630L
                || tablebase.draws()
                != 1_246_100L
                || tablebase.maximumDistance()
                != 57) {

            throw new IllegalStateException(
                    "KQ-KP solved metadata no longer matches M53."
            );
        }

        System.out.println(
                "M53 metadata regression: PASSED"
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
                    "Reloaded KQ-KP metadata does not match solved metadata."
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
                "Unable to find decisive KQ-KP runtime fixture."
        );
    }

    private static void verifyGenericService(
            FourPieceTierOneTablebaseService service,
            int fixtureState,
            Position white,
            Position black
    ) throws Exception {

        Optional<FourPieceTierOneTablebaseService.ProbeResult> whiteOptional =
                service.probe(
                        white
                );

        Optional<FourPieceTierOneTablebaseService.ProbeResult> blackOptional =
                service.probe(
                        black
                );

        if (whiteOptional.isEmpty()
                || blackOptional.isEmpty()) {
            throw new IllegalStateException(
                    "Generic Tier-1 service rejected KQ-KP runtime fixture."
            );
        }

        FourPieceTierOneTablebaseService.ProbeResult whiteProbe =
                whiteOptional.get();

        FourPieceTierOneTablebaseService.ProbeResult blackProbe =
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
                    "Generic Tier-1 service returned wrong material for KQ-KP."
            );
        }

        if (whiteProbe.primitiveState()
                != fixtureState
                || blackProbe.primitiveState()
                != fixtureState) {

            throw new IllegalStateException(
                    "KQ-KP color normalization changed primitive state."
            );
        }

        if (whiteProbe.outcome()
                != blackProbe.outcome()
                || whiteProbe.distance()
                != blackProbe.distance()) {

            throw new IllegalStateException(
                    "KQ-KP color normalization changed WDL/DTM."
            );
        }

        if (whiteProbe.colorReversed()
                || !blackProbe.colorReversed()) {

            throw new IllegalStateException(
                    "KQ-KP color-reversal flags are incorrect."
            );
        }

        System.out.println();
        System.out.println(
                "Generic-service SPLIT normalization fixture"
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
                "SPLIT color normalization: PASSED"
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
                "canonical WHITE-Q / BLACK-P",
                expectedOutcome,
                expectedDistance
        );

        verifyExactPosition(
                tablebase,
                black,
                "reversed BLACK-Q / WHITE-P",
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
                    "ExactEndgameTablebase KQ-KP "
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
                    "Exact KQ-KP move classification is incomplete for "
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
                        "Exact KQ-KP analysis contains unsupported child."
                );
            }

            if (analysis.optimal()) {
                optimal++;

                if (!bestMoves.contains(
                        analysis.move()
                )) {
                    throw new IllegalStateException(
                            "Optimal KQ-KP move missing from bestMoves()."
                    );
                }
            }
        }

        if (optimal
                != bestMoves.size()) {

            throw new IllegalStateException(
                    "KQ-KP bestMoves()/analyzeMoves() optimal-count mismatch."
            );
        }

        System.out.println();
        System.out.println(
                "Exact facade — KQ-KP "
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
                    "EndgameMoveController does not support KQ-KP "
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
                    "Controller returned no exact KQ-KP moves for "
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
                    "Normal-mode controller KQ-KP move is not exact-optimal for "
                            + label
                            + "."
            );
        }

        System.out.println();
        System.out.println(
                "Controller — KQ-KP "
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
                        PieceType.QUEEN,
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
                        PieceType.QUEEN,
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

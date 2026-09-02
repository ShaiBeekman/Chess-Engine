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
 * M63.
 *
 * Persist the M62B exact EP-aware KP-KP solve, reload it, verify the digest,
 * prove ordinary and EP runtime routing, and finish the four-piece catalog.
 */
public final class FourPieceTierTwoKpkpPersistenceRuntimeMain {

    private static final String EXPECTED_DIGEST =
            "65e248648ada7b4372976a0b41ef7d0bc7a684ce51fa85be17c51453688e7cc9";

    private static final FourPieceMaterialClass MATERIAL =
            FourPieceMaterialClass.split(
                    PieceType.PAWN,
                    PieceType.PAWN
            );

    private FourPieceTierTwoKpkpPersistenceRuntimeMain() {
    }

    public static void main(
            String[] args
    ) throws Exception {

        if (args.length > 1) {
            throw new IllegalArgumentException(
                    "Usage: FourPieceTierTwoKpkpPersistenceRuntimeMain [tablebase-directory]"
            );
        }

        Path directory =
                args.length == 0
                        ? FourPieceTierTwoKpkpTablebaseService.DEFAULT_DIRECTORY
                        : Path.of(args[0]);

        directory =
                directory.toAbsolutePath()
                        .normalize();

        FourPieceTierTwoKpkpTablebaseService service =
                new FourPieceTierTwoKpkpTablebaseService(
                        directory
                );

        Path path =
                service.assetPath(
                        MATERIAL
                );

        System.out.println(
                "KP-KP Tier-2 persistence / final exact-runtime gate"
        );

        System.out.println(
                "==================================================="
        );

        System.out.println(
                "Output: "
                        + path
        );

        System.out.println();
        System.out.println(
                "Building exact EP-aware KP-KP..."
        );

        FourPieceTierTwoKpkpRetrogradeBuilder.Result result =
                new FourPieceTierTwoKpkpRetrogradeBuilder()
                        .build();

        FourPieceTierTwoKpkpTablebaseCodec.Tablebase solved =
                result.toTablebase();

        String solvedDigest =
                FourPieceTierTwoKpkpTablebaseCodec
                        .contentDigest(
                                solved
                        );

        System.out.println(
                "Solved digest: "
                        + solvedDigest
        );

        require(
                EXPECTED_DIGEST.equals(
                        solvedDigest
                ),
                "KP-KP solve digest changed."
                        + "\nExpected: "
                        + EXPECTED_DIGEST
                        + "\nActual:   "
                        + solvedDigest
        );

        verifyKnownMetadata(
                solved
        );

        System.out.println();
        System.out.println(
                "Saving..."
        );

        long saveStarted =
                System.nanoTime();

        FourPieceTierTwoKpkpTablebaseCodec.save(
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
                Files.size(path)
                        / (1024.0 * 1024.0)
        );

        System.out.println();
        System.out.println(
                "Reloading..."
        );

        long loadStarted =
                System.nanoTime();

        FourPieceTierTwoKpkpTablebaseCodec.Tablebase loaded =
                FourPieceTierTwoKpkpTablebaseCodec.load(
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
                FourPieceTierTwoKpkpTablebaseCodec
                        .contentDigest(
                                loaded
                        );

        System.out.println(
                "Loaded digest: "
                        + loadedDigest
        );

        require(
                EXPECTED_DIGEST.equals(
                        loadedDigest
                ),
                "Reloaded KP-KP digest changed."
        );

        System.out.println(
                "Persistence digest identity: PASSED"
        );

        int ordinaryState =
                findDecisiveOrdinaryFixture(
                        loaded
                );

        Position ordinary =
                positionForState(
                        ordinaryState
                );

        verifyServiceFixture(
                service,
                loaded,
                ordinaryState,
                ordinary,
                "ordinary"
        );

        int epState =
                findDecisiveEpFixture(
                        loaded
                );

        Position ep =
                positionForState(
                        epState
                );

        verifyServiceFixture(
                service,
                loaded,
                epState,
                ep,
                "EP-aware"
        );

        verifyExactFacade(
                loaded,
                ordinaryState,
                ordinary,
                "ordinary"
        );

        verifyExactFacade(
                loaded,
                epState,
                ep,
                "EP-aware"
        );

        verifyController(
                ordinary,
                "ordinary",
                0x4B504B504D6301L
        );

        verifyController(
                ep,
                "EP-aware",
                0x4B504B504D6302L
        );

        verifyIrrelevantEpNormalization(
                service,
                loaded
        );

        service.clearCache();

        require(
                service.loadedCount() == 0,
                "KP-KP service cache did not clear."
        );

        System.out.println();
        System.out.println(
                "Tier-2 KP-KP cache clear: PASSED"
        );

        System.out.println();
        System.out.println(
                "KP-KP PERSISTENCE / EXACT-RUNTIME GATE PASSED"
        );

        System.out.println(
                "30 / 30 FOUR-PIECE FAMILIES EXACTLY SOLVED, PERSISTED, AND RUNTIME-READY"
        );

        System.out.println(
                "FOUR-PIECE TABLEBASE CATALOG COMPLETE"
        );
    }

    private static void verifyKnownMetadata(
            FourPieceTierTwoKpkpTablebaseCodec.Tablebase tablebase
    ) {

        require(
                tablebase.legalStates()
                        == 14_959_748L,
                "KP-KP legal-state count changed."
        );

        require(
                tablebase.legalEpStates()
                        == 87_572L,
                "KP-KP legal EP-state count changed."
        );

        require(
                tablebase.wins()
                        == 6_494_984L,
                "KP-KP WIN count changed."
        );

        require(
                tablebase.losses()
                        == 3_475_940L,
                "KP-KP LOSS count changed."
        );

        require(
                tablebase.draws()
                        == 4_988_824L,
                "KP-KP DRAW count changed."
        );

        require(
                tablebase.maximumDistance()
                        == 66,
                "KP-KP maximum DTM changed."
        );

        System.out.println(
                "M62B metadata regression: PASSED"
        );
    }

    private static void verifyMetadataIdentity(
            FourPieceTierTwoKpkpTablebaseCodec.Tablebase expected,
            FourPieceTierTwoKpkpTablebaseCodec.Tablebase actual
    ) {

        require(
                expected.legalStates()
                        == actual.legalStates()
                        && expected.legalEpStates()
                        == actual.legalEpStates()
                        && expected.wins()
                        == actual.wins()
                        && expected.losses()
                        == actual.losses()
                        && expected.draws()
                        == actual.draws()
                        && expected.maximumDistance()
                        == actual.maximumDistance(),
                "Reloaded KP-KP metadata differs from solved metadata."
        );
    }

    private static int findDecisiveOrdinaryFixture(
            FourPieceTierTwoKpkpTablebaseCodec.Tablebase tablebase
    ) {

        for (int state = 0;
             state < FourPieceTierTwoKpkpStateIndex.BASE_STATE_COUNT;
             state++) {

            if (decisive(
                    tablebase,
                    state
            )) {
                return state;
            }
        }

        throw new IllegalStateException(
                "Unable to find decisive ordinary KP-KP fixture."
        );
    }

    private static int findDecisiveEpFixture(
            FourPieceTierTwoKpkpTablebaseCodec.Tablebase tablebase
    ) {

        for (int state =
             FourPieceTierTwoKpkpStateIndex.BASE_STATE_COUNT;
             state < FourPieceTierTwoKpkpStateIndex.STATE_COUNT;
             state++) {

            if (decisive(
                    tablebase,
                    state
            )) {
                return state;
            }
        }

        throw new IllegalStateException(
                "Unable to find decisive EP KP-KP fixture."
        );
    }

    private static boolean decisive(
            FourPieceTierTwoKpkpTablebaseCodec.Tablebase tablebase,
            int state
    ) {

        byte outcome =
                tablebase.outcome(state);

        return (outcome
                == FourPieceTablebase.WIN
                || outcome
                == FourPieceTablebase.LOSS)
                && tablebase.distance(state)
                >= 4;
    }

    private static void verifyServiceFixture(
            FourPieceTierTwoKpkpTablebaseService service,
            FourPieceTierTwoKpkpTablebaseCodec.Tablebase loaded,
            int expectedState,
            Position position,
            String label
    ) throws Exception {

        Optional<FourPieceTierTwoKpkpTablebaseService.ProbeResult>
                optional =
                service.probe(
                        position
                );

        require(
                optional.isPresent(),
                "KP-KP service rejected "
                        + label
                        + " fixture."
        );

        FourPieceTierTwoKpkpTablebaseService.ProbeResult probe =
                optional.get();

        require(
                probe.primitiveState()
                        == expectedState,
                "KP-KP service normalized "
                        + label
                        + " fixture to wrong state."
        );

        require(
                probe.outcome()
                        == loaded.outcome(
                        expectedState
                )
                        && probe.distance()
                        == loaded.distance(
                        expectedState
                ),
                "KP-KP service result differs from persisted table."
        );

        require(
                probe.enPassantAvailable()
                        == FourPieceTierTwoKpkpStateIndex
                        .enPassantAvailable(
                                expectedState
                        ),
                "KP-KP service EP identity mismatch."
        );

        System.out.println();
        System.out.println(
                "KP-KP service — "
                        + label
        );

        System.out.println(
                "  state: "
                        + expectedState
        );

        System.out.println(
                "  outcome: "
                        + probe.outcomeName()
        );

        System.out.println(
                "  DTM: "
                        + probe.distance()
        );

        System.out.println(
                "  EP: "
                        + probe.enPassantAvailable()
        );

        System.out.println(
                "  PASSED"
        );
    }

    private static void verifyExactFacade(
            FourPieceTierTwoKpkpTablebaseCodec.Tablebase loaded,
            int state,
            Position position,
            String label
    ) {

        ExactEndgameTablebase exact =
                ExactEndgameTablebase.tierZeroCatalog();

        ExactEndgameTablebase.Probe probe =
                exact.probe(
                        position
                );

        require(
                probe.outcome()
                        == mapPrimitive(
                        loaded.outcome(
                                state
                        )
                ),
                "Exact facade KP-KP outcome mismatch."
        );

        require(
                probe.mateDistance()
                        == loaded.distance(
                        state
                ),
                "Exact facade KP-KP DTM mismatch."
        );

        List<ExactEndgameTablebase.MoveAnalysis> analyses =
                exact.analyzeMoves(
                        position
                );

        List<Move> best =
                exact.bestMoves(
                        position
                );

        require(
                !analyses.isEmpty(),
                "Exact facade could not classify all KP-KP "
                        + label
                        + " legal children."
        );

        require(
                !best.isEmpty(),
                "Exact facade returned no KP-KP "
                        + label
                        + " optimal move."
        );

        System.out.println();
        System.out.println(
                "Exact facade — KP-KP "
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
                        + best.size()
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
                        new Random(seed)
                );

        require(
                controller.supports(
                        position
                ),
                "EndgameMoveController does not support KP-KP "
                        + label
        );

        ExactEndgameTablebase exact =
                ExactEndgameTablebase.tierZeroCatalog();

        List<Move> best =
                exact.bestMoves(
                        position
                );

        Move chosen =
                controller.chooseMove(
                        position
                );

        require(
                chosen != null
                        && best.contains(
                        chosen
                ),
                "Controller KP-KP move is not exact-optimal."
        );

        System.out.println();
        System.out.println(
                "Controller — KP-KP "
                        + label
        );

        System.out.println(
                "  supported: true"
        );

        System.out.println(
                "  exact best moves: "
                        + best.size()
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
     * A non-null engine EP target that cannot be used by the opposing pawn must
     * normalize to the ordinary compact state, exactly as M59 did for KPPK.
     */
    private static void verifyIrrelevantEpNormalization(
            FourPieceTierTwoKpkpTablebaseService service,
            FourPieceTierTwoKpkpTablebaseCodec.Tablebase loaded
    ) throws Exception {

        int fixture =
                -1;

        for (int state = 0;
             state < FourPieceTierTwoKpkpStateIndex.BASE_STATE_COUNT;
             state++) {

            if (!decisive(
                    loaded,
                    state
            )) {
                continue;
            }

            int wp =
                    FourPieceTierTwoKpkpStateIndex.whitePawn(
                            state
                    );

            int bp =
                    FourPieceTierTwoKpkpStateIndex.blackPawn(
                            state
                    );

            /*
             * Choose a board where the pawns are not adjacent on the relevant
             * EP ranks, so no EP overlay can be carried.
             */
            if (!FourPieceTierTwoKpkpPrimitiveState
                    .canCarryEnPassant(
                            FourPieceTierTwoKpkpStateIndex.baseState(
                                    state
                            )
                    )) {

                fixture =
                        state;

                break;
            }
        }

        require(
                fixture >= 0,
                "Unable to find irrelevant-EP KP-KP fixture."
        );

        Position ordinary =
                positionForState(
                        fixture
                );

        Square irrelevantTarget =
                Square.fromAlgebraic(
                        "a3"
                );

        Position withIrrelevantEp =
                copyWithEp(
                        ordinary,
                        irrelevantTarget
                );

        Optional<FourPieceTierTwoKpkpTablebaseService.ProbeResult>
                probe =
                service.probe(
                        withIrrelevantEp
                );

        require(
                probe.isPresent()
                        && probe.get()
                        .primitiveState()
                        == fixture
                        && !probe.get()
                        .enPassantAvailable(),
                "Irrelevant engine EP target was not normalized away."
        );

        System.out.println();
        System.out.println(
                "Irrelevant transient EP target normalization: PASSED"
        );
    }

    private static Position positionForState(
            int state
    ) {

        Board board =
                new Board();

        board.setPiece(
                square(
                        FourPieceTierTwoKpkpStateIndex.whiteKing(
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
                        FourPieceTierTwoKpkpStateIndex.blackKing(
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
                        FourPieceTierTwoKpkpStateIndex.whitePawn(
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
                        FourPieceTierTwoKpkpStateIndex.blackPawn(
                                state
                        )
                ),
                new Piece(
                        PieceType.PAWN,
                        Color.BLACK
                )
        );

        Color side =
                FourPieceTierTwoKpkpStateIndex.blackToMove(
                        state
                )
                        ? Color.BLACK
                        : Color.WHITE;

        Square ep =
                FourPieceTierTwoKpkpStateIndex.enPassantAvailable(
                        state
                )
                        ? square(
                        FourPieceTierTwoKpkpStateIndex
                                .enPassantTargetSquare(
                                        state
                                )
                )
                        : null;

        return position(
                board,
                side,
                ep
        );
    }

    private static Position copyWithEp(
            Position position,
            Square ep
    ) {

        return position(
                new Board(
                        position.getBoard()
                ),
                position.getSideToMove(),
                ep
        );
    }

    private static Position position(
            Board board,
            Color sideToMove,
            Square ep
    ) {

        Position temporary =
                new Position(
                        board,
                        sideToMove,
                        false,
                        false,
                        false,
                        false,
                        ep,
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
                ep,
                0,
                1,
                repetitions
        );
    }

    private static Square square(
            int primitive
    ) {

        return new Square(
                primitive & 7,
                primitive >>> 3
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
}

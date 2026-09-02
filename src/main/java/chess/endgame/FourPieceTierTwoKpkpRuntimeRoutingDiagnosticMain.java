package main.java.chess.endgame;

import main.java.chess.model.Board;
import main.java.chess.model.Color;
import main.java.chess.model.Piece;
import main.java.chess.model.PieceType;
import main.java.chess.model.Position;
import main.java.chess.model.PositionKey;
import main.java.chess.model.Square;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * M63A — no-rebuild runtime routing diagnostic.
 *
 * Uses the already-persisted M63 KP-KP asset and probes the exact same
 * ordinary and EP-aware fixtures through every four-piece runtime layer.
 */
public final class FourPieceTierTwoKpkpRuntimeRoutingDiagnosticMain {

    private static final int ORDINARY_STATE =
            17_428;

    private static final int EP_STATE =
            33_554_488;

    private FourPieceTierTwoKpkpRuntimeRoutingDiagnosticMain() {
    }

    public static void main(
            String[] args
    ) throws Exception {

        if (args.length > 1) {
            throw new IllegalArgumentException(
                    "Usage: FourPieceTierTwoKpkpRuntimeRoutingDiagnosticMain "
                            + "[tablebase-directory]"
            );
        }

        Path directory =
                args.length == 0
                        ? Path.of(
                        "tablebases",
                        "four-piece"
                )
                        : Path.of(
                        args[0]
                );

        directory =
                directory.toAbsolutePath()
                        .normalize();

        FourPieceTierTwoKpkpTablebaseService kpkp =
                new FourPieceTierTwoKpkpTablebaseService(
                        directory
                );

        FourPieceTierTwoKpkpTablebaseCodec.Tablebase tablebase =
                FourPieceTierTwoKpkpTablebaseCodec.load(
                        kpkp.assetPath()
                );

        System.out.println(
                "M63A KP-KP runtime routing diagnostic"
        );
        System.out.println(
                "===================================="
        );
        System.out.println(
                "Directory: "
                        + directory
        );

        runFixture(
                "ordinary",
                ORDINARY_STATE,
                tablebase,
                directory
        );

        runFixture(
                "EP-aware",
                EP_STATE,
                tablebase,
                directory
        );

        System.out.println();
        System.out.println(
                "M63A DIAGNOSTIC COMPLETE"
        );
    }

    private static void runFixture(
            String label,
            int state,
            FourPieceTierTwoKpkpTablebaseCodec.Tablebase tablebase,
            Path directory
    ) throws Exception {

        Position position =
                positionForState(
                        state
                );

        System.out.println();
        System.out.println(
                "------------------------------------------------------------"
        );
        System.out.println(
                "Fixture: "
                        + label
        );
        System.out.println(
                "State: "
                        + state
        );
        System.out.println(
                "Expected outcome: "
                        + outcomeName(
                        tablebase.outcome(
                                state
                        )
                )
        );
        System.out.println(
                "Expected DTM: "
                        + tablebase.distance(
                        state
                )
        );
        System.out.println(
                "EP target: "
                        + position.getEnPassantTarget()
        );

        probeTierZero(
                position,
                directory
        );

        probeTierOne(
                position,
                directory
        );

        probeKppk(
                position,
                directory
        );

        probeKpkp(
                position,
                directory
        );

        ExactEndgameTablebase exact =
                ExactEndgameTablebase.tierZeroCatalog();

        ExactEndgameTablebase.Probe facade =
                exact.probe(
                        position
                );

        System.out.println(
                "Exact facade:"
        );
        System.out.println(
                "  outcome: "
                        + facade.outcome()
        );
        System.out.println(
                "  DTM: "
                        + facade.mateDistance()
        );
    }

    private static void probeTierZero(
            Position position,
            Path directory
    ) {

        FourPieceGenericTablebaseService service =
                new FourPieceGenericTablebaseService(
                        directory
                );

        System.out.println(
                "Tier-0 service:"
        );

        try {
            System.out.println(
                    "  supports: "
                            + service.supports(
                            position
                    )
            );

            Optional<FourPieceGenericTablebaseService.ProbeResult> probe =
                    service.probe(
                            position
                    );

            if (probe.isPresent()) {
                System.out.println(
                        "  probe: "
                                + outcomeName(
                                probe.get()
                                        .outcome()
                        )
                                + " DTM "
                                + probe.get()
                                .distance()
                );
            } else {
                System.out.println(
                        "  probe: empty"
                );
            }

        } catch (Exception exception) {
            printException(
                    exception
            );
        }
    }

    private static void probeTierOne(
            Position position,
            Path directory
    ) {

        FourPieceTierOneTablebaseService service =
                new FourPieceTierOneTablebaseService(
                        directory
                );

        System.out.println(
                "Tier-1 service:"
        );

        try {
            System.out.println(
                    "  supports: "
                            + service.supports(
                            position
                    )
            );

            Optional<FourPieceTierOneTablebaseService.ProbeResult> probe =
                    service.probe(
                            position
                    );

            if (probe.isPresent()) {
                System.out.println(
                        "  probe: "
                                + outcomeName(
                                probe.get()
                                        .outcome()
                        )
                                + " DTM "
                                + probe.get()
                                .distance()
                );
            } else {
                System.out.println(
                        "  probe: empty"
                );
            }

        } catch (Exception exception) {
            printException(
                    exception
            );
        }
    }

    private static void probeKppk(
            Position position,
            Path directory
    ) {

        FourPieceTierTwoKppkTablebaseService service =
                new FourPieceTierTwoKppkTablebaseService(
                        directory
                );

        System.out.println(
                "KPPK service:"
        );

        try {
            System.out.println(
                    "  supports: "
                            + service.supports(
                            position
                    )
            );

            Optional<FourPieceTierTwoKppkTablebaseService.ProbeResult> probe =
                    service.probe(
                            position
                    );

            if (probe.isPresent()) {
                System.out.println(
                        "  probe: "
                                + outcomeName(
                                probe.get()
                                        .outcome()
                        )
                                + " DTM "
                                + probe.get()
                                .distance()
                );
            } else {
                System.out.println(
                        "  probe: empty"
                );
            }

        } catch (Exception exception) {
            printException(
                    exception
            );
        }
    }

    private static void probeKpkp(
            Position position,
            Path directory
    ) {

        FourPieceTierTwoKpkpTablebaseService service =
                new FourPieceTierTwoKpkpTablebaseService(
                        directory
                );

        System.out.println(
                "KP-KP service:"
        );

        try {
            System.out.println(
                    "  supports: "
                            + service.supports(
                            position
                    )
            );

            Optional<FourPieceTierTwoKpkpTablebaseService.ProbeResult> probe =
                    service.probe(
                            position
                    );

            if (probe.isPresent()) {
                System.out.println(
                        "  probe: "
                                + outcomeName(
                                probe.get()
                                        .outcome()
                        )
                                + " DTM "
                                + probe.get()
                                .distance()
                );
                System.out.println(
                        "  primitive state: "
                                + probe.get()
                                .primitiveState()
                );
                System.out.println(
                        "  EP identity: "
                                + probe.get()
                                .enPassantAvailable()
                );
            } else {
                System.out.println(
                        "  probe: empty"
                );
            }

        } catch (Exception exception) {
            printException(
                    exception
            );
        }
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

        Color sideToMove =
                FourPieceTierTwoKpkpStateIndex.blackToMove(
                        state
                )
                        ? Color.BLACK
                        : Color.WHITE;

        Square enPassantTarget =
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
                sideToMove,
                enPassantTarget
        );
    }

    private static Position position(
            Board board,
            Color sideToMove,
            Square enPassantTarget
    ) {

        Position temporary =
                new Position(
                        board,
                        sideToMove,
                        false,
                        false,
                        false,
                        false,
                        enPassantTarget,
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
                enPassantTarget,
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

        return "UNKNOWN("
                + outcome
                + ")";
    }

    private static void printException(
            Exception exception
    ) {

        System.out.println(
                "  EXCEPTION: "
                        + exception.getClass()
                        .getSimpleName()
                        + ": "
                        + exception.getMessage()
        );
    }
}

package main.java.chess.endgame;

import main.java.chess.model.Board;
import main.java.chess.model.Color;
import main.java.chess.model.Move;
import main.java.chess.model.Piece;
import main.java.chess.model.PieceType;
import main.java.chess.model.Position;
import main.java.chess.model.PositionKey;
import main.java.chess.model.Square;
import main.java.chess.rules.MoveGenerator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * M59 diagnostic v2.
 *
 * Separately probes every child through:
 *
 *   1. FourPieceTierTwoKppkTablebaseService directly
 *   2. ExactEndgameTablebase
 *
 * This tells us whether the remaining gap is in the KPPK service itself
 * or in the unified exact facade.
 *
 * No tablebase is rebuilt.
 */
public final class FourPieceTierTwoKppkRuntimeChildDiagnosticV2Main {

    private static final int FIXTURE_STATE = 17426;

    private FourPieceTierTwoKppkRuntimeChildDiagnosticV2Main() {
    }

    public static void main(String[] args) throws Exception {

        if (args.length != 0) {
            throw new IllegalArgumentException(
                    "Usage: FourPieceTierTwoKppkRuntimeChildDiagnosticV2Main"
            );
        }

        Position parent =
                canonicalPosition(
                        FIXTURE_STATE
                );

        FourPieceTierTwoKppkTablebaseService direct =
                new FourPieceTierTwoKppkTablebaseService();

        ExactEndgameTablebase exact =
                ExactEndgameTablebase.tierZeroCatalog();

        MoveGenerator generator =
                new MoveGenerator();

        System.out.println(
                "KPPK runtime child diagnostic v2"
        );

        System.out.println(
                "================================"
        );

        System.out.println(
                "Fixture state: "
                        + FIXTURE_STATE
        );

        System.out.println(
                "Asset: "
                        + direct.assetPath()
        );

        System.out.println(
                "Direct parent supports: "
                        + direct.supports(
                        parent
                )
        );

        printDirectProbe(
                "Direct parent",
                direct.probe(
                        parent
                )
        );

        ExactEndgameTablebase.Probe parentExact =
                exact.probe(
                        parent
                );

        System.out.println(
                "Exact parent: "
                        + parentExact.outcome()
                        + " DTM "
                        + parentExact.mateDistance()
        );

        List<Move> moves =
                generator.generateLegalMoves(
                        parent
                );

        System.out.println(
                "Legal moves: "
                        + moves.size()
        );

        System.out.println();

        int directUnsupported =
                0;

        int exactUnsupported =
                0;

        for (int i = 0;
             i < moves.size();
             i++) {

            Move move =
                    moves.get(
                            i
                    );

            Position child =
                    parent.makeMove(
                            move
                    );

            boolean supports =
                    direct.supports(
                            child
                    );

            Optional<FourPieceTierTwoKppkTablebaseService.ProbeResult>
                    directOptional =
                    direct.probe(
                            child
                    );

            ExactEndgameTablebase.Probe exactProbe =
                    exact.probe(
                            child
                    );

            if (directOptional.isEmpty()) {
                directUnsupported++;
            }

            if (exactProbe.outcome()
                    == ExactEndgameTablebase.Outcome.UNSUPPORTED) {

                exactUnsupported++;
            }

            System.out.println(
                    "["
                            + i
                            + "] "
                            + move
            );

            System.out.println(
                    "    EP target: "
                            + child.getEnPassantTarget()
            );

            System.out.println(
                    "    direct supports(): "
                            + supports
            );

            if (directOptional.isPresent()) {

                FourPieceTierTwoKppkTablebaseService.ProbeResult probe =
                        directOptional.get();

                System.out.println(
                        "    direct probe: "
                                + probe.outcomeName()
                                + " DTM "
                                + probe.distance()
                                + " state "
                                + probe.primitiveState()
                );

            } else {

                System.out.println(
                        "    direct probe: EMPTY"
                );
            }

            System.out.println(
                    "    exact probe: "
                            + exactProbe.outcome()
                            + " DTM "
                            + exactProbe.mateDistance()
            );

            System.out.println(
                    "    material: "
                            + material(
                            child
                    )
            );
        }

        System.out.println();

        System.out.println(
                "Direct-service unsupported children: "
                        + directUnsupported
        );

        System.out.println(
                "Exact-facade unsupported children: "
                        + exactUnsupported
        );

        System.out.println();

        if (directUnsupported
                > 0) {

            System.out.println(
                    "RESULT: the remaining rejection is inside "
                            + "FourPieceTierTwoKppkTablebaseService."
            );

            System.out.println(
                    "Check that the project source actually contains NO "
                            + "getEnPassantTarget() rejection."
            );

        } else if (exactUnsupported
                > 0) {

            System.out.println(
                    "RESULT: direct KPPK probing works; the remaining gap is "
                            + "inside ExactEndgameTablebase routing."
            );

        } else {

            System.out.println(
                    "RESULT: all direct and facade child probes are exact."
            );

            System.out.println(
                    "M59 can be rerun."
            );
        }
    }


    private static void printDirectProbe(
            String label,
            Optional<FourPieceTierTwoKppkTablebaseService.ProbeResult> optional
    ) {

        if (optional.isEmpty()) {

            System.out.println(
                    label
                            + ": EMPTY"
            );

            return;
        }

        FourPieceTierTwoKppkTablebaseService.ProbeResult probe =
                optional.get();

        System.out.println(
                label
                        + ": "
                        + probe.outcomeName()
                        + " DTM "
                        + probe.distance()
                        + " state "
                        + probe.primitiveState()
        );
    }


    private static String material(
            Position position
    ) {

        StringBuilder builder =
                new StringBuilder();

        for (int rank = 0;
             rank < 8;
             rank++) {

            for (int file = 0;
                 file < 8;
                 file++) {

                Square square =
                        new Square(
                                file,
                                rank
                        );

                Piece piece =
                        position.getBoard()
                                .getPiece(
                                        square
                                );

                if (piece == null) {

                    continue;
                }

                if (builder.length()
                        > 0) {

                    builder.append(
                            ", "
                    );
                }

                builder.append(
                                piece.color()
                        )
                        .append(
                                ' '
                        )
                        .append(
                                piece.type()
                        )
                        .append(
                                '@'
                        )
                        .append(
                                (char) ('a' + file)
                        )
                        .append(
                                rank + 1
                        );
            }
        }

        return builder.toString();
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


    private static Square square(
            int primitive
    ) {

        return new Square(
                primitive & 7,
                primitive >>> 3
        );
    }
}

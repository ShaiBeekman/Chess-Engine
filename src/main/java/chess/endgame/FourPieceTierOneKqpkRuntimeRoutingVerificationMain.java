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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;


/**
 * M40 verification:
 *
 *     persisted KQPK
 *         -> ExactEndgameTablebase
 *         -> exact move analysis
 *         -> EndgameMoveController
 *
 * The canonical fixture is the M39 runtime fixture:
 *
 *     primitive state 16529
 *     LOSS
 *     DTM 10
 */
public final class FourPieceTierOneKqpkRuntimeRoutingVerificationMain {

    private static final int FIXTURE_STATE =
            16529;


    private FourPieceTierOneKqpkRuntimeRoutingVerificationMain() {
    }


    public static void main(
            String[] args
    ) {

        System.out.println(
                "KQPK Tier-1 exact runtime routing verification"
        );

        System.out.println(
                "============================================"
        );


        ExactEndgameTablebase tablebase =
                ExactEndgameTablebase.tierZeroCatalog();


        Position white =
                canonicalPosition(
                        FIXTURE_STATE
                );


        Position black =
                colorReversedPosition(
                        FIXTURE_STATE
                );


        verifyProbe(
                tablebase,
                white,
                "strong WHITE"
        );


        verifyProbe(
                tablebase,
                black,
                "strong BLACK"
        );


        verifyExactMoveAnalysis(
                tablebase,
                white,
                "strong WHITE"
        );


        verifyExactMoveAnalysis(
                tablebase,
                black,
                "strong BLACK"
        );


        verifyController(
                white,
                "strong WHITE"
        );


        verifyController(
                black,
                "strong BLACK"
        );


        System.out.println();

        System.out.println(
                "KQPK TIER-1 EXACT RUNTIME ROUTING PASSED"
        );

        System.out.println(
                "NEXT: GUI KQPK STUDY ROUTING / LIVE PRACTICE GATE"
        );
    }


    private static void verifyProbe(
            ExactEndgameTablebase tablebase,
            Position position,
            String label
    ) {

        ExactEndgameTablebase.Probe probe =
                tablebase.probe(
                        position
                );


        System.out.println();

        System.out.println(
                "Probe — "
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


        if (probe.outcome()
                != ExactEndgameTablebase.Outcome.LOSS
                || probe.mateDistance()
                != 10) {

            throw new IllegalStateException(
                    "KQPK "
                            + label
                            + " fixture did not route to LOSS DTM 10."
            );
        }
    }


    private static void verifyExactMoveAnalysis(
            ExactEndgameTablebase tablebase,
            Position position,
            String label
    ) {

        List<ExactEndgameTablebase.MoveAnalysis> analyses =
                tablebase.analyzeMoves(
                        position
                );


        if (analyses.isEmpty()) {

            throw new IllegalStateException(
                    "No exact KQPK move analysis for "
                            + label
                            + "."
            );
        }


        List<Move> bestMoves =
                tablebase.bestMoves(
                        position
                );


        if (bestMoves.isEmpty()) {

            throw new IllegalStateException(
                    "No exact KQPK best move for "
                            + label
                            + "."
            );
        }


        int optimal =
                0;

        int nonOptimal =
                0;


        for (ExactEndgameTablebase.MoveAnalysis analysis :
                analyses) {

            if (analysis.childOutcome()
                    == ExactEndgameTablebase.Outcome.UNSUPPORTED) {

                throw new IllegalStateException(
                        "Exact KQPK move analysis contains unsupported child."
                );
            }


            if (analysis.optimal()) {

                optimal++;


                if (!bestMoves.contains(
                        analysis.move()
                )) {

                    throw new IllegalStateException(
                            "Optimal KQPK move missing from bestMoves()."
                    );
                }


                /*
                 * Parent is LOSS, therefore every exact optimal move must
                 * move to a child WIN and maximize resistance.
                 */
                if (analysis.childOutcome()
                        != ExactEndgameTablebase.Outcome.WIN) {

                    throw new IllegalStateException(
                            "LOSS-parent optimal KQPK move does not lead to child WIN."
                    );
                }

            } else {

                nonOptimal++;
            }
        }


        if (optimal != bestMoves.size()) {

            throw new IllegalStateException(
                    "KQPK bestMoves()/analyzeMoves() optimal-count mismatch."
            );
        }


        System.out.println();

        System.out.println(
                "Move analysis — "
                        + label
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
                "  non-optimal moves: "
                        + nonOptimal
        );

        System.out.println(
                "  PASSED"
        );
    }


    private static void verifyController(
            Position position,
            String label
    ) {

        EndgameMoveController controller =
                new EndgameMoveController(
                        new Random(
                                0x4B51504BL
                        )
                );


        if (!controller.supports(
                position
        )) {

            throw new IllegalStateException(
                    "EndgameMoveController does not support KQPK "
                            + label
                            + "."
            );
        }


        controller.setPracticeMode(
                false
        );


        Move chosen =
                controller.chooseMove(
                        position
                );


        if (chosen == null) {

            throw new IllegalStateException(
                    "EndgameMoveController returned null for KQPK "
                            + label
                            + "."
            );
        }


        List<Move> exact =
                controller.exactBestMoves(
                        position
                );


        if (!exact.contains(
                chosen
        )) {

            throw new IllegalStateException(
                    "Normal-mode controller KQPK move is not exact-optimal."
            );
        }


        System.out.println();

        System.out.println(
                "Controller — "
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
            int state
    ) {

        Board board =
                new Board();


        board.setPiece(
                square(
                        flipRank(
                                FourPieceGenericPrimitiveState.blackKing(
                                        state
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
                                        state
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
                                        state
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
                                        state
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
                        state
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
            Color side
    ) {

        Position temporary =
                new Position(
                        board,
                        side,
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
                side,
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


    private static int flipRank(
            int square
    ) {

        return (7 - (square >>> 3)) * 8
                + (square & 7);
    }
}

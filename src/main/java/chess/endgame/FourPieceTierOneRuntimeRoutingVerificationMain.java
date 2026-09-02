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
 * Milestone 47.
 *
 * End-to-end runtime regression for both persisted Tier-1 SAME_SIDE assets:
 *
 *     KQPK -> generic Tier-1 service -> ExactEndgameTablebase -> controller
 *     KRPK -> generic Tier-1 service -> ExactEndgameTablebase -> controller
 *
 * Both canonical strong-WHITE fixtures and their physical strong-BLACK
 * color-reversed counterparts are checked.
 */
public final class FourPieceTierOneRuntimeRoutingVerificationMain {

    private static final int FIXTURE_STATE = 16529;

    private FourPieceTierOneRuntimeRoutingVerificationMain() {
    }

    public static void main(String[] args) {
        System.out.println("Generic Tier-1 exact runtime routing verification");
        System.out.println("=================================================");

        ExactEndgameTablebase tablebase =
                ExactEndgameTablebase.tierZeroCatalog();

        verifyFamily(
                tablebase,
                PieceType.QUEEN,
                "KQPK",
                10
        );

        verifyFamily(
                tablebase,
                PieceType.ROOK,
                "KRPK",
                20
        );

        System.out.println();
        System.out.println("GENERIC TIER-1 EXACT RUNTIME ROUTING PASSED");
        System.out.println("NEXT: SOLVE / VALIDATE KBPK");
    }

    private static void verifyFamily(
            ExactEndgameTablebase tablebase,
            PieceType nonPawnType,
            String name,
            int expectedDtm
    ) {
        System.out.println();
        System.out.println(name);
        System.out.println("-".repeat(name.length()));

        Position white =
                canonicalPosition(
                        FIXTURE_STATE,
                        nonPawnType
                );

        Position black =
                colorReversedPosition(
                        FIXTURE_STATE,
                        nonPawnType
                );

        verifyProbe(
                tablebase,
                white,
                name,
                "strong WHITE",
                expectedDtm
        );

        verifyProbe(
                tablebase,
                black,
                name,
                "strong BLACK",
                expectedDtm
        );

        verifyExactMoveAnalysis(
                tablebase,
                white,
                name,
                "strong WHITE"
        );

        verifyExactMoveAnalysis(
                tablebase,
                black,
                name,
                "strong BLACK"
        );

        verifyController(
                white,
                name,
                "strong WHITE",
                0x4D343700L + nonPawnType.ordinal()
        );

        verifyController(
                black,
                name,
                "strong BLACK",
                0x4D343710L + nonPawnType.ordinal()
        );

        System.out.println(
                name
                        + " end-to-end routing: PASSED"
        );
    }

    private static void verifyProbe(
            ExactEndgameTablebase tablebase,
            Position position,
            String family,
            String label,
            int expectedDtm
    ) {
        ExactEndgameTablebase.Probe probe =
                tablebase.probe(
                        position
                );

        System.out.println();
        System.out.println(
                "Probe — "
                        + family
                        + " "
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
                != expectedDtm) {

            throw new IllegalStateException(
                    family
                            + " "
                            + label
                            + " fixture did not route to LOSS DTM "
                            + expectedDtm
                            + "."
            );
        }

        System.out.println(
                "  PASSED"
        );
    }

    private static void verifyExactMoveAnalysis(
            ExactEndgameTablebase tablebase,
            Position position,
            String family,
            String label
    ) {
        List<ExactEndgameTablebase.MoveAnalysis> analyses =
                tablebase.analyzeMoves(
                        position
                );

        if (analyses.isEmpty()) {
            throw new IllegalStateException(
                    "No exact "
                            + family
                            + " move analysis for "
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
                    "No exact "
                            + family
                            + " best move for "
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
                        "Exact "
                                + family
                                + " move analysis contains unsupported child."
                );
            }

            if (analysis.optimal()) {
                optimal++;

                if (!bestMoves.contains(
                        analysis.move()
                )) {
                    throw new IllegalStateException(
                            "Optimal "
                                    + family
                                    + " move missing from bestMoves()."
                    );
                }

                /*
                 * FIXTURE_STATE is a LOSS parent in both families.  Therefore
                 * every optimal move must reach a child WIN and maximize DTM.
                 */
                if (analysis.childOutcome()
                        != ExactEndgameTablebase.Outcome.WIN) {

                    throw new IllegalStateException(
                            "LOSS-parent optimal "
                                    + family
                                    + " move does not lead to child WIN."
                    );
                }

            } else {
                nonOptimal++;
            }
        }

        if (optimal
                != bestMoves.size()) {

            throw new IllegalStateException(
                    family
                            + " bestMoves()/analyzeMoves() optimal-count mismatch."
            );
        }

        System.out.println();
        System.out.println(
                "Move analysis — "
                        + family
                        + " "
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
            String family,
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
                            + family
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
                    "EndgameMoveController returned no exact best moves for "
                            + family
                            + " "
                            + label
                            + "."
            );
        }

        Move chosen =
                controller.chooseMove(
                        position
                );

        if (chosen == null) {
            throw new IllegalStateException(
                    "EndgameMoveController returned null for "
                            + family
                            + " "
                            + label
                            + "."
            );
        }

        if (!exact.contains(
                chosen
        )) {
            throw new IllegalStateException(
                    "Normal-mode controller "
                            + family
                            + " move is not exact-optimal."
            );
        }

        System.out.println();
        System.out.println(
                "Controller — "
                        + family
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
            int state,
            PieceType nonPawnType
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
                        nonPawnType,
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

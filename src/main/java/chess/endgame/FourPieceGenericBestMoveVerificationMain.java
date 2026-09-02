package main.java.chess.endgame;

import main.java.chess.model.Board;
import main.java.chess.model.Color;
import main.java.chess.model.Move;
import main.java.chess.model.Piece;
import main.java.chess.model.PieceType;
import main.java.chess.model.Position;
import main.java.chess.model.Square;

import java.util.HashMap;
import java.util.List;


public final class FourPieceGenericBestMoveVerificationMain {

    private FourPieceGenericBestMoveVerificationMain() {

    }


    public static void main(
            String[] args
    ) {

        ExactEndgameTablebase tablebase =
                ExactEndgameTablebase.tierZeroCatalog();


        System.out.println(
                "Generic Tier-0 exact move verification"
        );

        System.out.println(
                "====================================="
        );


        verifyWinningPosition(
                tablebase
        );


        verifyDrawPosition(
                tablebase
        );


        verifyColorReversal(
                tablebase
        );


        System.out.println();

        System.out.println(
                "GENERIC TIER-0 EXACT MOVE SELECTION PASSED"
        );

        System.out.println(
                "THE ENGINE CAN NOW PLAY PAWNLESS FOUR-PIECE ENDGAMES EXACTLY"
        );
    }


    private static void verifyWinningPosition(
            ExactEndgameTablebase tablebase
    ) {

        Position position =
                position(
                        Color.WHITE,

                        piece(
                                "a1",
                                PieceType.KING,
                                Color.WHITE
                        ),

                        piece(
                                "g8",
                                PieceType.KING,
                                Color.BLACK
                        ),

                        piece(
                                "d4",
                                PieceType.QUEEN,
                                Color.WHITE
                        ),

                        piece(
                                "e4",
                                PieceType.BISHOP,
                                Color.WHITE
                        )
                );


        ExactEndgameTablebase.Probe current =
                tablebase.probe(
                        position
                );


        require(
                current.outcome()
                        == ExactEndgameTablebase.Outcome.WIN,
                "Expected deterministic KQBK position to be WIN."
        );


        List<Move> bestMoves =
                tablebase.bestMoves(
                        position
                );


        require(
                !bestMoves.isEmpty(),
                "Winning KQBK position returned no exact best move."
        );


        System.out.println();

        System.out.println(
                "KQBK exact WIN"
        );

        System.out.println(
                "  Current DTM: "
                        + current.mateDistance()
        );

        System.out.println(
                "  Best moves: "
                        + bestMoves.size()
        );


        for (Move move :
                bestMoves) {

            Position child =
                    position.makeMove(
                            move
                    );


            ExactEndgameTablebase.Probe childProbe =
                    tablebase.probe(
                            child
                    );


            require(
                    childProbe.outcome()
                            == ExactEndgameTablebase.Outcome.LOSS,
                    "Winning best move did not produce opponent LOSS."
            );


            require(
                    childProbe.mateDistance()
                            == current.mateDistance() - 1,
                    "Winning best move did not reduce DTM by exactly one ply."
            );


            System.out.println(
                    "    "
                            + describe(
                            move
                    )
                            + " -> opponent LOSS DTM "
                            + childProbe.mateDistance()
            );
        }
    }


    private static void verifyDrawPosition(
            ExactEndgameTablebase tablebase
    ) {

        Position position =
                position(
                        Color.WHITE,

                        piece(
                                "a1",
                                PieceType.KING,
                                Color.WHITE
                        ),

                        piece(
                                "h8",
                                PieceType.KING,
                                Color.BLACK
                        ),

                        piece(
                                "c3",
                                PieceType.KNIGHT,
                                Color.WHITE
                        ),

                        piece(
                                "f5",
                                PieceType.KNIGHT,
                                Color.WHITE
                        )
                );


        ExactEndgameTablebase.Probe current =
                tablebase.probe(
                        position
                );


        require(
                current.outcome()
                        == ExactEndgameTablebase.Outcome.DRAW,
                "Expected deterministic KNNK position to be DRAW."
        );


        List<Move> bestMoves =
                tablebase.bestMoves(
                        position
                );


        require(
                !bestMoves.isEmpty(),
                "Drawn KNNK position returned no drawing moves."
        );


        System.out.println();

        System.out.println(
                "KNNK exact DRAW"
        );

        System.out.println(
                "  Drawing moves: "
                        + bestMoves.size()
        );


        for (Move move :
                bestMoves) {

            Position child =
                    position.makeMove(
                            move
                    );


            ExactEndgameTablebase.Probe childProbe =
                    tablebase.probe(
                            child
                    );


            require(
                    childProbe.outcome()
                            == ExactEndgameTablebase.Outcome.DRAW,
                    "Exact drawing move failed to preserve DRAW."
            );


            System.out.println(
                    "    "
                            + describe(
                            move
                    )
                            + " -> DRAW"
            );
        }
    }


    private static void verifyColorReversal(
            ExactEndgameTablebase tablebase
    ) {

        Position position =
                position(
                        Color.BLACK,

                        piece(
                                "a1",
                                PieceType.KING,
                                Color.BLACK
                        ),

                        piece(
                                "g8",
                                PieceType.KING,
                                Color.WHITE
                        ),

                        piece(
                                "d4",
                                PieceType.QUEEN,
                                Color.BLACK
                        ),

                        piece(
                                "e4",
                                PieceType.BISHOP,
                                Color.BLACK
                        )
                );


        ExactEndgameTablebase.Probe current =
                tablebase.probe(
                        position
                );


        require(
                current.outcome()
                        == ExactEndgameTablebase.Outcome.WIN,
                "Color-reversed KQBK should remain WIN."
        );


        List<Move> bestMoves =
                tablebase.bestMoves(
                        position
                );


        require(
                !bestMoves.isEmpty(),
                "Color-reversed KQBK returned no best move."
        );


        for (Move move :
                bestMoves) {

            Position child =
                    position.makeMove(
                            move
                    );


            ExactEndgameTablebase.Probe childProbe =
                    tablebase.probe(
                            child
                    );


            require(
                    childProbe.outcome()
                            == ExactEndgameTablebase.Outcome.LOSS,
                    "Color-reversed best move failed WDL inversion."
            );


            require(
                    childProbe.mateDistance()
                            == current.mateDistance() - 1,
                    "Color-reversed best move failed DTM ordering."
            );
        }


        System.out.println();

        System.out.println(
                "KQBK color-reversed exact move selection: PASSED"
        );
    }


    private static Position position(
            Color sideToMove,
            PlacedPiece... pieces
    ) {

        Board board =
                new Board();


        for (PlacedPiece placed :
                pieces) {

            board.setPiece(
                    placed.square(),
                    placed.piece()
            );
        }


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
                new HashMap<>()
        );
    }


    private static PlacedPiece piece(
            String square,
            PieceType type,
            Color color
    ) {

        return new PlacedPiece(
                Square.fromAlgebraic(
                        square
                ),
                new Piece(
                        type,
                        color
                )
        );
    }


    private static String describe(
            Move move
    ) {

        String description =
                move.from()
                        .toAlgebraic()
                        + " -> "
                        + move.to()
                        .toAlgebraic();


        if (move.isPromotion()) {

            description +=
                    " = "
                            + move.promotion();
        }


        return description;
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


    private record PlacedPiece(
            Square square,
            Piece piece
    ) {

    }
}
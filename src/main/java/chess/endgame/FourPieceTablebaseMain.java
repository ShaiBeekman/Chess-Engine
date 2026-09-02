package main.java.chess.endgame;

import main.java.chess.model.Board;
import main.java.chess.model.Color;
import main.java.chess.model.Piece;
import main.java.chess.model.PieceType;
import main.java.chess.model.Position;
import main.java.chess.model.PositionKey;
import main.java.chess.model.Square;

import java.util.HashMap;
import java.util.Map;


/**
 * Small architecture smoke test for the first four-piece milestone.
 *
 * This is not the retrograde builder. It validates:
 *
 *     - KQRK recognition/encoding
 *     - unsupported response before a 4-piece asset exists
 *     - KQRK -> KQK exact transition after rook capture
 *     - KQRK -> KRK exact transition after queen capture
 */
public final class FourPieceTablebaseMain {

    public static void main(
            String[] args
    ) {

        FourPieceTablebaseService service =
                new FourPieceTablebaseService();


        FourPieceTablebase tablebase =
                service.getKqrk(
                        Color.WHITE
                );


        Position kqrk =
                position(
                        Color.WHITE,
                        new PlacedPiece(
                                PieceType.KING,
                                Color.WHITE,
                                "e1"
                        ),
                        new PlacedPiece(
                                PieceType.QUEEN,
                                Color.WHITE,
                                "d1"
                        ),
                        new PlacedPiece(
                                PieceType.ROOK,
                                Color.WHITE,
                                "a1"
                        ),
                        new PlacedPiece(
                                PieceType.KING,
                                Color.BLACK,
                                "e8"
                        )
                );


        System.out.println(
                "Material: "
                        + tablebase.materialName()
        );

        System.out.println(
                "Raw state count: "
                        + tablebase.stateCount()
        );

        System.out.println(
                "Exact asset loaded: "
                        + tablebase.isBuilt()
        );

        System.out.println(
                "KQRK probe before asset: "
                        + tablebase.probe(
                        kqrk
                )
        );


        Position kqk =
                position(
                        Color.BLACK,
                        new PlacedPiece(
                                PieceType.KING,
                                Color.WHITE,
                                "e1"
                        ),
                        new PlacedPiece(
                                PieceType.QUEEN,
                                Color.WHITE,
                                "d1"
                        ),
                        new PlacedPiece(
                                PieceType.KING,
                                Color.BLACK,
                                "e8"
                        )
                );


        Position krk =
                position(
                        Color.BLACK,
                        new PlacedPiece(
                                PieceType.KING,
                                Color.WHITE,
                                "e1"
                        ),
                        new PlacedPiece(
                                PieceType.ROOK,
                                Color.WHITE,
                                "a1"
                        ),
                        new PlacedPiece(
                                PieceType.KING,
                                Color.BLACK,
                                "e8"
                        )
                );


        System.out.println(
                "External KQK probe: "
                        + tablebase.probe(
                        kqk
                )
        );

        System.out.println(
                "External KRK probe: "
                        + tablebase.probe(
                        krk
                )
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
                    square(
                            placed.square()
                    ),
                    new Piece(
                            placed.type(),
                            placed.color()
                    )
            );
        }


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
                        Map.of()
                );


        Map<PositionKey, Integer>
                repetition =
                new HashMap<>();


        repetition.put(
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
                repetition
        );
    }


    private static Square square(
            String algebraic
    ) {

        int file =
                algebraic.charAt(0)
                        - 'a';

        int rank =
                algebraic.charAt(1)
                        - '1';


        return new Square(
                file,
                rank
        );
    }


    private record PlacedPiece(
            PieceType type,
            Color color,
            String square
    ) {
    }
}

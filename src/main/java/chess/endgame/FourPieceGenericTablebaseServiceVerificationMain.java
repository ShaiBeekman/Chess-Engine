package main.java.chess.endgame;

import main.java.chess.model.Board;
import main.java.chess.model.Color;
import main.java.chess.model.Piece;
import main.java.chess.model.PieceType;
import main.java.chess.model.Position;
import main.java.chess.model.Square;

import java.io.IOException;
import java.util.HashMap;
import java.util.Optional;


/**
 * Milestone 21 verification.
 *
 * Proves that normal engine Position objects can be mapped into the
 * persisted Tier-0 generic tablebase catalog.
 */
public final class FourPieceGenericTablebaseServiceVerificationMain {

    private FourPieceGenericTablebaseServiceVerificationMain() {

    }


    public static void main(
            String[] args
    ) throws IOException {

        FourPieceGenericTablebaseService service =
                new FourPieceGenericTablebaseService();


        System.out.println(
                "Generic Tier-0 runtime tablebase verification"
        );

        System.out.println(
                "============================================"
        );

        System.out.println(
                "Directory: "
                        + service.directory()
        );


        verifySameSide(
                service
        );


        verifySameSideColorReversal(
                service
        );


        verifyIdenticalExtras(
                service
        );


        verifySplit(
                service
        );


        verifySplitColorReversal(
                service
        );


        verifyUnsupportedPawn(
                service
        );


        verifyCastlingRejection(
                service
        );


        verifyCache(
                service
        );


        System.out.println();

        System.out.println(
                "GENERIC TIER-0 RUNTIME TABLEBASE SERVICE PASSED"
        );

        System.out.println(
                "ENGINE POSITIONS CAN NOW PROBE THE 20-CLASS TIER-0 CATALOG"
        );
    }


    private static void verifySameSide(
            FourPieceGenericTablebaseService service
    ) throws IOException {

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


        FourPieceGenericTablebaseService.ProbeResult result =
                requireProbe(
                        service,
                        position,
                        "same-side KQBK"
                );


        require(
                result.material()
                        .equals(
                                FourPieceMaterialClass.sameSide(
                                        PieceType.QUEEN,
                                        PieceType.BISHOP
                                )
                        ),
                "KQBK material recognition failed."
        );


        require(
                !result.colorReversed(),
                "White-owned KQBK should already be canonical."
        );


        printProbe(
                "KQBK same-side",
                result
        );
    }


    private static void verifySameSideColorReversal(
            FourPieceGenericTablebaseService service
    ) throws IOException {

        Position whiteOwned =
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


        Position blackOwnedReversal =
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


        FourPieceGenericTablebaseService.ProbeResult first =
                requireProbe(
                        service,
                        whiteOwned,
                        "white-owned KQBK"
                );


        FourPieceGenericTablebaseService.ProbeResult second =
                requireProbe(
                        service,
                        blackOwnedReversal,
                        "black-owned KQBK color reversal"
                );


        require(
                second.colorReversed(),
                "Black-owned KQBK was not color-reversed."
        );


        requireEquivalent(
                first,
                second,
                "KQBK color reversal"
        );


        System.out.println(
                "KQBK color reversal: PASSED"
        );
    }


    private static void verifyIdenticalExtras(
            FourPieceGenericTablebaseService service
    ) throws IOException {

        Position firstOrdering =
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


        FourPieceGenericTablebaseService.ProbeResult result =
                requireProbe(
                        service,
                        firstOrdering,
                        "KNNK identical-extra canonicalization"
                );


        int firstSquare =
                FourPieceGenericPrimitiveState.firstExtra(
                        result.primitiveState()
                );

        int secondSquare =
                FourPieceGenericPrimitiveState.secondExtra(
                        result.primitiveState()
                );


        require(
                firstSquare < secondSquare,
                "Identical same-side extras were not canonicalized."
        );


        require(
                result.material()
                        .equals(
                                FourPieceMaterialClass.sameSide(
                                        PieceType.KNIGHT,
                                        PieceType.KNIGHT
                                )
                        ),
                "KNNK material recognition failed."
        );


        printProbe(
                "KNNK identical extras",
                result
        );
    }


    private static void verifySplit(
            FourPieceGenericTablebaseService service
    ) throws IOException {

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
                                "e5",
                                PieceType.ROOK,
                                Color.BLACK
                        )
                );


        FourPieceGenericTablebaseService.ProbeResult result =
                requireProbe(
                        service,
                        position,
                        "split KQ-KR"
                );


        require(
                result.material()
                        .equals(
                                FourPieceMaterialClass.split(
                                        PieceType.QUEEN,
                                        PieceType.ROOK
                                )
                        ),
                "KQ-KR material recognition failed."
        );


        require(
                !result.colorReversed(),
                "Canonical KQ-KR was unexpectedly color-reversed."
        );


        printProbe(
                "KQ-KR split",
                result
        );
    }


    private static void verifySplitColorReversal(
            FourPieceGenericTablebaseService service
    ) throws IOException {

        Position canonical =
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
                                "e5",
                                PieceType.ROOK,
                                Color.BLACK
                        )
                );


        Position reversed =
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
                                "e5",
                                PieceType.ROOK,
                                Color.WHITE
                        )
                );


        FourPieceGenericTablebaseService.ProbeResult first =
                requireProbe(
                        service,
                        canonical,
                        "canonical KQ-KR"
                );


        FourPieceGenericTablebaseService.ProbeResult second =
                requireProbe(
                        service,
                        reversed,
                        "reversed KR-KQ"
                );


        require(
                second.colorReversed(),
                "Split color reversal was not detected."
        );


        requireEquivalent(
                first,
                second,
                "split KQ-KR color reversal"
        );


        System.out.println(
                "KQ-KR split color reversal: PASSED"
        );
    }


    private static void verifyUnsupportedPawn(
            FourPieceGenericTablebaseService service
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
                                "d4",
                                PieceType.QUEEN,
                                Color.WHITE
                        ),

                        piece(
                                "e4",
                                PieceType.PAWN,
                                Color.WHITE
                        )
                );


        require(
                !service.supports(
                        position
                ),
                "Tier-0 service accepted pawn material."
        );


        System.out.println(
                "Pawn rejection: PASSED"
        );
    }


    private static void verifyCastlingRejection(
            FourPieceGenericTablebaseService service
    ) {

        Board board =
                new Board();


        board.setPiece(
                Square.fromAlgebraic(
                        "e1"
                ),
                new Piece(
                        PieceType.KING,
                        Color.WHITE
                )
        );


        board.setPiece(
                Square.fromAlgebraic(
                        "e8"
                ),
                new Piece(
                        PieceType.KING,
                        Color.BLACK
                )
        );


        board.setPiece(
                Square.fromAlgebraic(
                        "h1"
                ),
                new Piece(
                        PieceType.ROOK,
                        Color.WHITE
                )
        );


        board.setPiece(
                Square.fromAlgebraic(
                        "a8"
                ),
                new Piece(
                        PieceType.ROOK,
                        Color.BLACK
                )
        );


        Position positionWithRights =
                new Position(
                        board,
                        Color.WHITE
                );


        require(
                !service.supports(
                        positionWithRights
                ),
                "Tier-0 service accepted a position with castling rights."
        );


        System.out.println(
                "Castling-right rejection: PASSED"
        );
    }


    private static void verifyCache(
            FourPieceGenericTablebaseService service
    ) throws IOException {

        int before =
                service.loadedTablebaseCount();


        Position position =
                position(
                        Color.BLACK,

                        piece(
                                "b2",
                                PieceType.KING,
                                Color.WHITE
                        ),

                        piece(
                                "g7",
                                PieceType.KING,
                                Color.BLACK
                        ),

                        piece(
                                "c4",
                                PieceType.QUEEN,
                                Color.WHITE
                        ),

                        piece(
                                "f5",
                                PieceType.BISHOP,
                                Color.WHITE
                        )
                );


        requireProbe(
                service,
                position,
                "cache verification first probe"
        );


        int afterFirst =
                service.loadedTablebaseCount();


        requireProbe(
                service,
                position,
                "cache verification second probe"
        );


        int afterSecond =
                service.loadedTablebaseCount();


        require(
                afterFirst >= before,
                "Cache size unexpectedly decreased."
        );


        require(
                afterSecond == afterFirst,
                "Second probe loaded a duplicate tablebase."
        );


        System.out.println(
                "Cache reuse: PASSED"
        );

        System.out.println(
                "Loaded tablebases: "
                        + afterSecond
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


    private static FourPieceGenericTablebaseService.ProbeResult
    requireProbe(
            FourPieceGenericTablebaseService service,
            Position position,
            String description
    ) throws IOException {

        Optional<FourPieceGenericTablebaseService.ProbeResult>
                result =
                service.probe(
                        position
                );


        if (result.isEmpty()) {

            throw new IllegalStateException(
                    "Probe failed: "
                            + description
            );
        }


        return result.get();
    }


    private static void requireEquivalent(
            FourPieceGenericTablebaseService.ProbeResult first,
            FourPieceGenericTablebaseService.ProbeResult second,
            String description
    ) {

        require(
                first.material()
                        .equals(
                                second.material()
                        ),
                description
                        + ": material mismatch."
        );


        require(
                first.primitiveState()
                        == second.primitiveState(),
                description
                        + ": primitive-state mismatch."
        );


        require(
                first.outcome()
                        == second.outcome(),
                description
                        + ": WDL mismatch."
        );


        require(
                first.distance()
                        == second.distance(),
                description
                        + ": DTM mismatch."
        );
    }


    private static void printProbe(
            String name,
            FourPieceGenericTablebaseService.ProbeResult result
    ) {

        System.out.println();

        System.out.println(
                name
        );

        System.out.println(
                "  Material: "
                        + result.material()
                        .displayName()
        );

        System.out.println(
                "  Outcome: "
                        + result.outcomeName()
        );

        System.out.println(
                "  DTM: "
                        + result.distance()
        );

        System.out.println(
                "  Primitive state: "
                        + result.primitiveState()
        );

        System.out.println(
                "  Color reversed: "
                        + result.colorReversed()
        );
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
package main.java.chess.tests;

import main.java.chess.model.Color;
import main.java.chess.model.FenCodec;
import main.java.chess.model.Piece;
import main.java.chess.model.PieceType;
import main.java.chess.model.Position;
import main.java.chess.model.Square;


/**
 * Version 1.0 correctness suite — FEN codec.
 *
 * Run FenCodecTestHarness.run() once from Main while this step
 * is being verified.
 */
public final class FenCodecTestHarness {

    private int passed;
    private int failed;


    public FenCodecTestHarness() {

        this.passed =
                0;

        this.failed =
                0;
    }


    public static void run() {

        new FenCodecTestHarness()
                .runAll();
    }


    private void runAll() {

        System.out.println();

        System.out.println(
                "========================================"
        );

        System.out.println(
                "Chess Engine v1.0 FEN Tests"
        );

        System.out.println(
                "========================================"
        );

        System.out.println();


        testStartingPosition();
        testAllSixFields();
        testRoundTrip();
        testFullmoveProgression();
        testFreshRepetitionHistory();
        testInvalidFen();


        System.out.println();

        System.out.println(
                "========================================"
        );

        System.out.printf(
                "Tests passed: %d%n",
                passed
        );

        System.out.printf(
                "Tests failed: %d%n",
                failed
        );

        System.out.println(
                "========================================"
        );

        System.out.println();


        if (failed > 0) {

            throw new IllegalStateException(
                    "FEN correctness suite failed: "
                            + failed
                            + " test(s) failed."
            );
        }
    }


    private void testStartingPosition() {

        String fen =
                "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";


        Position position =
                FenCodec.parse(
                        fen
                );


        expectEquals(
                "Starting FEN round-trips exactly",
                fen,
                FenCodec.toFen(
                        position
                )
        );


        expectEquals(
                "Starting FEN side to move is White",
                Color.WHITE,
                position.getSideToMove()
        );


        expectTrue(
                "Starting FEN preserves all castling rights",
                position.canWhiteCastleKingSide()
                        && position.canWhiteCastleQueenSide()
                        && position.canBlackCastleKingSide()
                        && position.canBlackCastleQueenSide()
        );
    }


    private void testAllSixFields() {

        String fen =
                "r3k2r/ppp2ppp/2n5/3pp3/8/2N5/PPP2PPP/R3K2R b Kq e3 17 42";


        Position position =
                FenCodec.parse(
                        fen
                );


        expectEquals(
                "Active color parsed",
                Color.BLACK,
                position.getSideToMove()
        );


        expectTrue(
                "White kingside castling parsed",
                position.canWhiteCastleKingSide()
        );


        expectTrue(
                "White queenside castling absent",
                !position.canWhiteCastleQueenSide()
        );


        expectTrue(
                "Black kingside castling absent",
                !position.canBlackCastleKingSide()
        );


        expectTrue(
                "Black queenside castling parsed",
                position.canBlackCastleQueenSide()
        );


        expectEquals(
                "En-passant target parsed",
                "e3",
                position
                        .getEnPassantTarget()
                        .toAlgebraic()
        );


        expectEquals(
                "Halfmove clock parsed",
                17,
                position.getHalfmoveClock()
        );


        expectEquals(
                "Fullmove number parsed",
                42,
                position.getFullmoveNumber()
        );


        Piece blackKing =
                position
                        .getBoard()
                        .getPiece(
                                Square.fromAlgebraic(
                                        "e8"
                                )
                        );


        expectTrue(
                "Piece placement parsed",
                blackKing != null
                        && blackKing.type() == PieceType.KING
                        && blackKing.color() == Color.BLACK
        );
    }


    private void testRoundTrip() {

        String[] fens = {

                "8/8/8/8/8/8/4K3/7k w - - 0 1",

                "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 8 21",

                "8/8/8/3pP3/8/8/4K3/7k w - d6 0 19"
        };


        for (String fen :
                fens) {

            expectEquals(
                    "Round-trip: "
                            + fen,
                    fen,
                    FenCodec.toFen(
                            FenCodec.parse(
                                    fen
                            )
                    )
            );
        }
    }


    private void testFullmoveProgression() {

        Position position =
                FenCodec.parse(
                        "8/8/8/8/8/8/4K3/7k w - - 0 7"
                );


        expectEquals(
                "Loaded fullmove number is 7",
                7,
                position.getFullmoveNumber()
        );


        /*
         * We test the Position rule directly with legal-looking king moves.
         * Fullmove number remains unchanged after White and increments
         * immediately after Black.
         */
        position =
                position.makeMove(
                        new main.java.chess.model.Move(
                                Square.fromAlgebraic(
                                        "e2"
                                ),
                                Square.fromAlgebraic(
                                        "e3"
                                )
                        )
                );


        expectEquals(
                "Fullmove number unchanged after White move",
                7,
                position.getFullmoveNumber()
        );


        position =
                position.makeMove(
                        new main.java.chess.model.Move(
                                Square.fromAlgebraic(
                                        "h1"
                                ),
                                Square.fromAlgebraic(
                                        "h2"
                                )
                        )
                );


        expectEquals(
                "Fullmove number increments after Black move",
                8,
                position.getFullmoveNumber()
        );
    }


    private void testFreshRepetitionHistory() {

        Position position =
                FenCodec.parse(
                        "8/8/8/8/8/8/4K3/7k w - - 0 31"
                );


        expectEquals(
                "Loaded FEN starts at repetition count 1",
                1,
                position.getCurrentRepetitionCount()
        );
    }


    private void testInvalidFen() {

        expectThrows(
                "Rejects five-field FEN",
                "8/8/8/8/8/8/4K3/7k w - - 0"
        );


        expectThrows(
                "Rejects malformed rank",
                "9/8/8/8/8/8/4K3/7k w - - 0 1"
        );


        expectThrows(
                "Rejects invalid active color",
                "8/8/8/8/8/8/4K3/7k x - - 0 1"
        );


        expectThrows(
                "Rejects invalid castling field",
                "8/8/8/8/8/8/4K3/7k w Z - 0 1"
        );


        expectThrows(
                "Rejects invalid en-passant rank",
                "8/8/8/8/8/8/4K3/7k w - e4 0 1"
        );


        expectThrows(
                "Rejects negative halfmove clock",
                "8/8/8/8/8/8/4K3/7k w - - -1 1"
        );


        expectThrows(
                "Rejects zero fullmove number",
                "8/8/8/8/8/8/4K3/7k w - - 0 0"
        );
    }


    private void expectThrows(
            String name,
            String fen
    ) {

        try {

            FenCodec.parse(
                    fen
            );


            fail(
                    name,
                    "Expected IllegalArgumentException."
            );

        } catch (IllegalArgumentException expected) {

            pass(
                    name
            );
        }
    }


    private void expectTrue(
            String name,
            boolean condition
    ) {

        if (condition) {

            pass(
                    name
            );

        } else {

            fail(
                    name,
                    "Expected true."
            );
        }
    }


    private void expectEquals(
            String name,
            Object expected,
            Object actual
    ) {

        if (
                expected == null
                        ? actual == null
                        : expected.equals(
                        actual
                )
        ) {

            pass(
                    name
            );

        } else {

            fail(
                    name,
                    "Expected "
                            + expected
                            + " but got "
                            + actual
                            + "."
            );
        }
    }


    private void pass(
            String name
    ) {

        passed++;

        System.out.println(
                "[PASS] "
                        + name
        );
    }


    private void fail(
            String name,
            String reason
    ) {

        failed++;

        System.out.println(
                "[FAIL] "
                        + name
                        + " — "
                        + reason
        );
    }
}

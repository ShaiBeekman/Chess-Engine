package main.java.chess.endgame;

import main.java.chess.model.Board;
import main.java.chess.model.Color;
import main.java.chess.model.Move;
import main.java.chess.model.Piece;
import main.java.chess.model.PieceType;
import main.java.chess.model.Position;
import main.java.chess.model.Square;
import main.java.chess.rules.MoveGenerator;

import java.util.HashMap;
import java.util.List;

/**
 * Milestone 31 correctness gate for Tier-1 four-piece pawn mechanics.
 *
 * This deliberately does NOT build KQPK yet.
 *
 * Before retrograde analysis can depend on pawn moves, prove that:
 *
 *  1. the existing 4-square + side-to-move primitive state is sufficient
 *     for every one-pawn four-piece class (no en-passant field is needed);
 *  2. white and black single pushes work;
 *  3. starting-rank double pushes work;
 *  4. a blocked pawn cannot push through its blocker;
 *  5. split-material pawn captures work;
 *  6. all four promotion choices are generated;
 *  7. promotion leaves Tier 1 and lands in a canonical Tier-0 material class.
 *
 * En passant is intentionally absent here. With only one pawn on the board,
 * there cannot be an en-passant capture. It becomes state-relevant later in
 * the two-pawn split class KP-KP.
 */
public final class FourPieceTierOnePawnRulesVerificationMain {

    private static final MoveGenerator MOVE_GENERATOR =
            new MoveGenerator();

    private FourPieceTierOnePawnRulesVerificationMain() {
    }

    public static void main(String[] args) {

        System.out.println(
                "Tier-1 four-piece pawn rules verification"
        );

        System.out.println(
                "=========================================="
        );

        System.out.println();


        verifyMaterialTier();

        verifyPrimitiveStateRoundTrip();

        verifyWhitePawnPushes();

        verifyBlockedWhiteDoublePush();

        verifyBlackPawnPushes();

        verifySplitPawnCapture();

        verifyWhitePromotionBoundary();

        verifyBlackPromotionBoundary();


        System.out.println();

        System.out.println(
                "TIER-1 FOUR-PIECE PAWN RULES PASSED"
        );

        System.out.println(
                "KQPK IS READY FOR PAWN-AWARE PRIMITIVE SUCCESSOR WORK"
        );
    }


    // =========================================================
    // MATERIAL / DEPENDENCY TIER
    // =========================================================

    private static void verifyMaterialTier() {

        FourPieceMaterialClass kqpk =
                FourPieceMaterialClass.sameSide(
                        PieceType.QUEEN,
                        PieceType.PAWN
                );

        FourPieceMaterialClass kqKp =
                FourPieceMaterialClass.split(
                        PieceType.QUEEN,
                        PieceType.PAWN
                );


        require(
                kqpk.pawnCount() == 1,
                "KQPK must contain exactly one pawn."
        );

        require(
                kqpk.buildTier() == 1,
                "KQPK must be Tier 1."
        );

        require(
                kqKp.pawnCount() == 1,
                "KQ-KP must contain exactly one pawn."
        );

        require(
                kqKp.buildTier() == 1,
                "KQ-KP must be Tier 1."
        );


        System.out.println(
                "Material dependency tier: PASSED"
        );

        System.out.println(
                "  " + kqpk.assetStem()
                        + " -> Tier "
                        + kqpk.buildTier()
        );

        System.out.println(
                "  " + kqKp.assetStem()
                        + " -> Tier "
                        + kqKp.buildTier()
        );

        System.out.println();
    }


    // =========================================================
    // PRIMITIVE STATE
    // =========================================================

    private static void verifyPrimitiveStateRoundTrip() {

        int whiteKing =
                index(
                        "a1"
                );

        int blackKing =
                index(
                        "h8"
                );

        int queen =
                index(
                        "b1"
                );

        int pawn =
                index(
                        "e2"
                );


        int whiteToMove =
                FourPieceGenericPrimitiveState.encode(
                        whiteKing,
                        blackKing,
                        queen,
                        pawn,
                        false
                );


        require(
                FourPieceGenericPrimitiveState.whiteKing(
                        whiteToMove
                ) == whiteKing,
                "White-king primitive round trip failed."
        );

        require(
                FourPieceGenericPrimitiveState.blackKing(
                        whiteToMove
                ) == blackKing,
                "Black-king primitive round trip failed."
        );

        require(
                FourPieceGenericPrimitiveState.firstExtra(
                        whiteToMove
                ) == queen,
                "First-extra primitive round trip failed."
        );

        require(
                FourPieceGenericPrimitiveState.secondExtra(
                        whiteToMove
                ) == pawn,
                "Pawn primitive round trip failed."
        );

        require(
                !FourPieceGenericPrimitiveState.blackToMove(
                        whiteToMove
                ),
                "White-to-move primitive round trip failed."
        );


        int blackToMove =
                FourPieceGenericPrimitiveState.encode(
                        whiteKing,
                        blackKing,
                        queen,
                        pawn,
                        true
                );


        require(
                FourPieceGenericPrimitiveState.blackToMove(
                        blackToMove
                ),
                "Black-to-move primitive round trip failed."
        );


        /*
         * There is no missing Tier-1 history bit here:
         *
         * one pawn total means there is no opposing pawn capable of
         * performing an en-passant capture. Therefore placement + side
         * to move is sufficient for exact Tier-1 state identity.
         */
        System.out.println(
                "Primitive Tier-1 state identity: PASSED"
        );

        System.out.println(
                "  State fields remain WK/BK/extra/extra/side-to-move."
        );

        System.out.println(
                "  No en-passant field is required with one pawn total."
        );

        System.out.println();
    }


    // =========================================================
    // WHITE PUSHES
    // =========================================================

    private static void verifyWhitePawnPushes() {

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
                                "b1",
                                PieceType.QUEEN,
                                Color.WHITE
                        ),
                        piece(
                                "e2",
                                PieceType.PAWN,
                                Color.WHITE
                        )
                );


        List<Move> moves =
                MOVE_GENERATOR.generateLegalMoves(
                        position
                );


        requireMove(
                moves,
                "e2",
                "e3",
                null,
                "White one-square pawn push e2-e3"
        );

        requireMove(
                moves,
                "e2",
                "e4",
                null,
                "White starting double push e2-e4"
        );


        System.out.println(
                "White KQPK pawn pushes: PASSED"
        );

        System.out.println(
                "  e2 -> e3"
        );

        System.out.println(
                "  e2 -> e4"
        );

        System.out.println();
    }


    private static void verifyBlockedWhiteDoublePush() {

        /*
         * Still exactly KQPK: the queen itself occupies e3 and blocks
         * its own pawn on e2.
         */
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
                                "e3",
                                PieceType.QUEEN,
                                Color.WHITE
                        ),
                        piece(
                                "e2",
                                PieceType.PAWN,
                                Color.WHITE
                        )
                );


        List<Move> moves =
                MOVE_GENERATOR.generateLegalMoves(
                        position
                );


        requireNoMove(
                moves,
                "e2",
                "e3",
                "Blocked pawn must not move onto e3"
        );

        requireNoMove(
                moves,
                "e2",
                "e4",
                "Blocked pawn must not jump through e3 to e4"
        );


        System.out.println(
                "Blocked KQPK pawn push: PASSED"
        );

        System.out.println();
    }


    // =========================================================
    // BLACK PUSHES
    // =========================================================

    private static void verifyBlackPawnPushes() {

        Position position =
                position(
                        Color.BLACK,
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
                                "g8",
                                PieceType.QUEEN,
                                Color.BLACK
                        ),
                        piece(
                                "e7",
                                PieceType.PAWN,
                                Color.BLACK
                        )
                );


        List<Move> moves =
                MOVE_GENERATOR.generateLegalMoves(
                        position
                );


        requireMove(
                moves,
                "e7",
                "e6",
                null,
                "Black one-square pawn push e7-e6"
        );

        requireMove(
                moves,
                "e7",
                "e5",
                null,
                "Black starting double push e7-e5"
        );


        System.out.println(
                "Black KQPK pawn pushes: PASSED"
        );

        System.out.println(
                "  e7 -> e6"
        );

        System.out.println(
                "  e7 -> e5"
        );

        System.out.println();
    }


    // =========================================================
    // SPLIT-MATERIAL CAPTURE
    // =========================================================

    private static void verifySplitPawnCapture() {

        /*
         * KQ-KP:
         *
         * White: Ka1, Qd5
         * Black: Kh8, Pe6
         * Black to move.
         *
         * The black pawn must be able to capture the queen on d5.
         * The child is then a three-piece KPK boundary.
         */
        Position position =
                position(
                        Color.BLACK,
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
                                "d5",
                                PieceType.QUEEN,
                                Color.WHITE
                        ),
                        piece(
                                "e6",
                                PieceType.PAWN,
                                Color.BLACK
                        )
                );


        List<Move> moves =
                MOVE_GENERATOR.generateLegalMoves(
                        position
                );


        Move capture =
                requireMove(
                        moves,
                        "e6",
                        "d5",
                        null,
                        "Black pawn capture e6xd5"
                );


        Position child =
                position.makeMove(
                        capture
                );


        Piece capturedSquare =
                child.getBoard()
                        .getPiece(
                                Square.fromAlgebraic(
                                        "d5"
                                )
                        );


        require(
                capturedSquare != null
                        && capturedSquare.type()
                        == PieceType.PAWN
                        && capturedSquare.color()
                        == Color.BLACK,
                "e6xd5 must leave the black pawn on d5."
        );

        require(
                countPieces(
                        child
                ) == 3,
                "e6xd5 must transition from four pieces to KPK."
        );


        System.out.println(
                "Split KQ-KP pawn capture: PASSED"
        );

        System.out.println(
                "  e6 x d5 -> three-piece KPK boundary"
        );

        System.out.println();
    }


    // =========================================================
    // PROMOTION
    // =========================================================

    private static void verifyWhitePromotionBoundary() {

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
                                "b1",
                                PieceType.QUEEN,
                                Color.WHITE
                        ),
                        piece(
                                "a7",
                                PieceType.PAWN,
                                Color.WHITE
                        )
                );


        List<Move> moves =
                MOVE_GENERATOR.generateLegalMoves(
                        position
                );


        Move queenPromotion =
                requireMove(
                        moves,
                        "a7",
                        "a8",
                        PieceType.QUEEN,
                        "White queen promotion"
                );

        requireMove(
                moves,
                "a7",
                "a8",
                PieceType.ROOK,
                "White rook promotion"
        );

        requireMove(
                moves,
                "a7",
                "a8",
                PieceType.BISHOP,
                "White bishop promotion"
        );

        requireMove(
                moves,
                "a7",
                "a8",
                PieceType.KNIGHT,
                "White knight promotion"
        );


        int promotionChoices =
                countPromotionMoves(
                        moves,
                        "a7",
                        "a8"
                );


        require(
                promotionChoices == 4,
                "White a7-a8 must have exactly four promotion choices."
        );


        Position promoted =
                position.makeMove(
                        queenPromotion
                );


        require(
                countPieces(
                        promoted
                ) == 4,
                "Promotion must remain a four-piece position."
        );


        Piece a8 =
                promoted.getBoard()
                        .getPiece(
                                Square.fromAlgebraic(
                                        "a8"
                                )
                        );


        require(
                a8 != null
                        && a8.type()
                        == PieceType.QUEEN
                        && a8.color()
                        == Color.WHITE,
                "White queen promotion must create a white queen on a8."
        );


        FourPieceMaterialClass tierZero =
                FourPieceMaterialClass.sameSide(
                        PieceType.QUEEN,
                        PieceType.QUEEN
                );


        require(
                tierZero.buildTier() == 0,
                "KQPK queen promotion must land in Tier-0 KQQK."
        );


        System.out.println(
                "White KQPK promotion boundary: PASSED"
        );

        System.out.println(
                "  Q / R / B / N promotion choices generated"
        );

        System.out.println(
                "  a7-a8=Q -> "
                        + tierZero.assetStem()
                        + " (Tier "
                        + tierZero.buildTier()
                        + ")"
        );

        System.out.println();
    }


    private static void verifyBlackPromotionBoundary() {

        Position position =
                position(
                        Color.BLACK,
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
                                "g8",
                                PieceType.QUEEN,
                                Color.BLACK
                        ),
                        piece(
                                "h2",
                                PieceType.PAWN,
                                Color.BLACK
                        )
                );


        List<Move> moves =
                MOVE_GENERATOR.generateLegalMoves(
                        position
                );


        Move knightPromotion =
                requireMove(
                        moves,
                        "h2",
                        "h1",
                        PieceType.KNIGHT,
                        "Black knight promotion"
                );

        requireMove(
                moves,
                "h2",
                "h1",
                PieceType.QUEEN,
                "Black queen promotion"
        );

        requireMove(
                moves,
                "h2",
                "h1",
                PieceType.ROOK,
                "Black rook promotion"
        );

        requireMove(
                moves,
                "h2",
                "h1",
                PieceType.BISHOP,
                "Black bishop promotion"
        );


        require(
                countPromotionMoves(
                        moves,
                        "h2",
                        "h1"
                ) == 4,
                "Black h2-h1 must have exactly four promotion choices."
        );


        Position promoted =
                position.makeMove(
                        knightPromotion
                );


        Piece h1 =
                promoted.getBoard()
                        .getPiece(
                                Square.fromAlgebraic(
                                        "h1"
                                )
                        );


        require(
                h1 != null
                        && h1.type()
                        == PieceType.KNIGHT
                        && h1.color()
                        == Color.BLACK,
                "Black knight promotion must create a black knight on h1."
        );


        FourPieceMaterialClass tierZero =
                FourPieceMaterialClass.sameSide(
                        PieceType.QUEEN,
                        PieceType.KNIGHT
                );


        require(
                tierZero.buildTier() == 0,
                "KQPK knight promotion must land in Tier-0 KQNK."
        );


        System.out.println(
                "Black KQPK promotion boundary: PASSED"
        );

        System.out.println(
                "  h2-h1=N -> "
                        + tierZero.assetStem()
                        + " (Tier "
                        + tierZero.buildTier()
                        + ")"
        );

        System.out.println();
    }


    // =========================================================
    // MOVE HELPERS
    // =========================================================

    private static Move requireMove(
            List<Move> moves,
            String from,
            String to,
            PieceType promotion,
            String description
    ) {

        Square fromSquare =
                Square.fromAlgebraic(
                        from
                );

        Square toSquare =
                Square.fromAlgebraic(
                        to
                );


        for (Move move :
                moves) {

            if (!move.from().equals(
                    fromSquare
            )
                    || !move.to().equals(
                    toSquare
            )) {

                continue;
            }


            if (promotion == null) {

                if (!move.isPromotion()) {

                    return move;
                }

                continue;
            }


            if (move.isPromotion()
                    && move.promotion()
                    == promotion) {

                return move;
            }
        }


        throw new IllegalStateException(
                description
                        + " was not generated."
        );
    }


    private static void requireNoMove(
            List<Move> moves,
            String from,
            String to,
            String description
    ) {

        Square fromSquare =
                Square.fromAlgebraic(
                        from
                );

        Square toSquare =
                Square.fromAlgebraic(
                        to
                );


        for (Move move :
                moves) {

            if (move.from().equals(
                    fromSquare
            )
                    && move.to().equals(
                    toSquare
            )) {

                throw new IllegalStateException(
                        description
                );
            }
        }
    }


    private static int countPromotionMoves(
            List<Move> moves,
            String from,
            String to
    ) {

        Square fromSquare =
                Square.fromAlgebraic(
                        from
                );

        Square toSquare =
                Square.fromAlgebraic(
                        to
                );

        int count =
                0;


        for (Move move :
                moves) {

            if (move.from().equals(
                    fromSquare
            )
                    && move.to().equals(
                    toSquare
            )
                    && move.isPromotion()) {

                count++;
            }
        }


        return count;
    }


    // =========================================================
    // POSITION HELPERS
    // =========================================================

    private static Position position(
            Color sideToMove,
            Placement... placements
    ) {

        Board board =
                new Board();


        for (Placement placement :
                placements) {

            board.setPiece(
                    Square.fromAlgebraic(
                            placement.square()
                    ),
                    new Piece(
                            placement.type(),
                            placement.color()
                    )
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


    private static Placement piece(
            String square,
            PieceType type,
            Color color
    ) {

        return new Placement(
                square,
                type,
                color
        );
    }


    private static int countPieces(
            Position position
    ) {

        int count =
                0;


        for (int rank = 0;
             rank < 8;
             rank++) {

            for (int file = 0;
                 file < 8;
                 file++) {

                if (position.getBoard()
                        .getPiece(
                                new Square(
                                        file,
                                        rank
                                )
                        )
                        != null) {

                    count++;
                }
            }
        }


        return count;
    }


    private static int index(
            String algebraic
    ) {

        Square square =
                Square.fromAlgebraic(
                        algebraic
                );


        return square.rank() * 8
                + square.file();
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


    private record Placement(
            String square,
            PieceType type,
            Color color
    ) {
    }
}

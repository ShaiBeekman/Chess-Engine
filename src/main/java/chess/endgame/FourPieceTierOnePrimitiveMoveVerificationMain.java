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
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;


/**
 * Milestone 42 verification.
 *
 * Compares the allocation-free generic Tier-1 primitive generator against
 * the ordinary Position + MoveGenerator chess rules for all eight canonical
 * one-pawn material families.
 *
 * KQPK is additionally compared against the already-proven M33 primitive
 * generator, so the generalization must preserve the old exact semantics.
 */
public final class FourPieceTierOnePrimitiveMoveVerificationMain {

    private static final int SAMPLES_PER_ORIENTATION =
            10_000;

    private static final MoveGenerator MOVE_GENERATOR =
            new MoveGenerator();


    private FourPieceTierOnePrimitiveMoveVerificationMain() {
    }


    public static void main(
            String[] args
    ) {

        System.out.println(
                "Generic Tier-1 primitive move verification"
        );

        System.out.println(
                "========================================"
        );


        PieceType[] nonPawns = {
                PieceType.QUEEN,
                PieceType.ROOK,
                PieceType.BISHOP,
                PieceType.KNIGHT
        };


        long seed =
                0x4D34324CL;


        for (PieceType type :
                nonPawns) {

            FourPieceMaterialClass sameSide =
                    FourPieceMaterialClass.sameSide(
                            type,
                            PieceType.PAWN
                    );


            verifyAgainstModel(
                    sameSide,
                    true,
                    SAMPLES_PER_ORIENTATION,
                    seed++
            );


            verifyAgainstModel(
                    sameSide,
                    false,
                    SAMPLES_PER_ORIENTATION,
                    seed++
            );


            FourPieceMaterialClass split =
                    FourPieceMaterialClass.split(
                            type,
                            PieceType.PAWN
                    );


            /*
             * SPLIT has a single canonical ownership orientation:
             * firstExtra WHITE, pawn BLACK. The boolean is intentionally
             * ignored by the generator for SPLIT material.
             */
            verifyAgainstModel(
                    split,
                    true,
                    SAMPLES_PER_ORIENTATION,
                    seed++
            );
        }


        verifyKqpkRegression(
                true,
                20_000,
                seed++
        );


        verifyKqpkRegression(
                false,
                20_000,
                seed
        );


        System.out.println();

        System.out.println(
                "GENERIC TIER-1 PRIMITIVE MOVE GATE PASSED"
        );

        System.out.println(
                "NEXT: GENERALIZE TIER-1 PREDECESSOR GENERATION"
        );
    }


    private static void verifyAgainstModel(
            FourPieceMaterialClass material,
            boolean sameSideOwnerIsWhite,
            int samples,
            long seed
    ) {

        Random random =
                new Random(
                        seed
                );

        FourPieceTierOnePrimitiveMoveGenerator.Buffer primitive =
                new FourPieceTierOnePrimitiveMoveGenerator.Buffer(
                        64
                );


        int checked =
                0;

        long attempts =
                0;

        long edges =
                0;

        long sameClass =
                0;

        long threePiece =
                0;

        long promotions =
                0;


        while (checked
                < samples) {

            attempts++;


            int pawnRank =
                    1 + random.nextInt(
                            6
                    );

            int pawn =
                    pawnRank * 8
                            + random.nextInt(
                            8
                    );

            int wk =
                    random.nextInt(
                            64
                    );

            int bk =
                    random.nextInt(
                            64
                    );

            int first =
                    random.nextInt(
                            64
                    );


            if (!distinct(
                    wk,
                    bk,
                    first,
                    pawn
            )) {

                continue;
            }


            int state =
                    FourPieceGenericPrimitiveState.encode(
                            wk,
                            bk,
                            first,
                            pawn,
                            random.nextBoolean()
                    );


            if (!FourPieceTierOnePrimitiveMoveGenerator
                    .isStructurallyLegal(
                            state,
                            material,
                            sameSideOwnerIsWhite
                    )) {

                continue;
            }


            Position position =
                    positionForState(
                            state,
                            material,
                            sameSideOwnerIsWhite
                    );


            List<Move> modelMoves =
                    MOVE_GENERATOR.generateLegalMoves(
                            position
                    );


            Set<String> expected =
                    modelKeys(
                            position,
                            modelMoves,
                            material,
                            sameSideOwnerIsWhite
                    );


            int count =
                    FourPieceTierOnePrimitiveMoveGenerator
                            .generateLegalSuccessors(
                                    state,
                                    material,
                                    sameSideOwnerIsWhite,
                                    primitive
                            );


            Set<String> actual =
                    primitiveKeys(
                            primitive,
                            count
                    );


            if (!expected.equals(
                    actual
            )) {

                System.out.println();

                System.out.println(
                        "Tier-1 primitive mismatch"
                );

                System.out.println(
                        "  material: "
                                + material.displayName()
                );

                System.out.println(
                        "  same-side owner white: "
                                + sameSideOwnerIsWhite
                );

                System.out.println(
                        "  state: "
                                + state
                );

                System.out.println(
                        "  expected: "
                                + expected
                );

                System.out.println(
                        "  actual: "
                                + actual
                );


                Set<String> missing =
                        new HashSet<>(
                                expected
                        );

                missing.removeAll(
                        actual
                );


                Set<String> extra =
                        new HashSet<>(
                                actual
                        );

                extra.removeAll(
                        expected
                );


                System.out.println(
                        "  missing: "
                                + missing
                );

                System.out.println(
                        "  extra: "
                                + extra
                );


                throw new IllegalStateException(
                        "Generic Tier-1 primitive move mismatch."
                );
            }


            if (count
                    != actual.size()) {

                throw new IllegalStateException(
                        "Generic Tier-1 primitive generator produced duplicate edges."
                );
            }


            for (int i = 0;
                 i < count;
                 i++) {

                switch (primitive.boundaryType(
                        i
                )) {

                    case FourPieceTierOnePrimitiveMoveGenerator
                                 .BOUNDARY_NONE ->
                            sameClass++;

                    case FourPieceTierOnePrimitiveMoveGenerator
                                 .BOUNDARY_THREE_PIECE ->
                            threePiece++;

                    case FourPieceTierOnePrimitiveMoveGenerator
                                 .BOUNDARY_TIER_ZERO_PROMOTION ->
                            promotions++;

                    default ->
                            throw new IllegalStateException(
                                    "Unknown generic Tier-1 boundary."
                            );
                }
            }


            edges +=
                    count;

            checked++;
        }


        System.out.println();

        System.out.println(
                material.displayName()
                        + " — "
                        + orientationName(
                        material,
                        sameSideOwnerIsWhite
                )
        );

        System.out.println(
                "  positions checked: "
                        + checked
        );

        System.out.println(
                "  attempts: "
                        + attempts
        );

        System.out.println(
                "  edges: "
                        + edges
        );

        System.out.println(
                "  same-class: "
                        + sameClass
        );

        System.out.println(
                "  three-piece: "
                        + threePiece
        );

        System.out.println(
                "  Tier-0 promotions: "
                        + promotions
        );

        System.out.println(
                "  Position/MoveGenerator oracle: PASSED"
        );
    }


    private static void verifyKqpkRegression(
            boolean strongIsWhite,
            int samples,
            long seed
    ) {

        FourPieceMaterialClass material =
                FourPieceMaterialClass.sameSide(
                        PieceType.QUEEN,
                        PieceType.PAWN
                );

        Random random =
                new Random(
                        seed
                );

        FourPieceTierOnePrimitiveMoveGenerator.Buffer generic =
                new FourPieceTierOnePrimitiveMoveGenerator.Buffer(
                        64
                );

        FourPieceTierOneKqpkPrimitiveMoveGenerator.Buffer proven =
                new FourPieceTierOneKqpkPrimitiveMoveGenerator.Buffer(
                        64
                );


        int checked =
                0;


        while (checked
                < samples) {

            int pawn =
                    (1 + random.nextInt(6)) * 8
                            + random.nextInt(8);

            int wk =
                    random.nextInt(64);

            int bk =
                    random.nextInt(64);

            int queen =
                    random.nextInt(64);


            if (!distinct(
                    wk,
                    bk,
                    queen,
                    pawn
            )) {

                continue;
            }


            int state =
                    FourPieceGenericPrimitiveState.encode(
                            wk,
                            bk,
                            queen,
                            pawn,
                            random.nextBoolean()
                    );


            boolean oldLegal =
                    FourPieceTierOneKqpkPrimitiveMoveGenerator
                            .isStructurallyLegal(
                                    state,
                                    strongIsWhite
                            );

            boolean newLegal =
                    FourPieceTierOnePrimitiveMoveGenerator
                            .isStructurallyLegal(
                                    state,
                                    material,
                                    strongIsWhite
                            );


            if (oldLegal
                    != newLegal) {

                throw new IllegalStateException(
                        "KQPK structural-legality regression at state "
                                + state
                );
            }


            if (!oldLegal) {

                continue;
            }


            int oldCount =
                    FourPieceTierOneKqpkPrimitiveMoveGenerator
                            .generateLegalSuccessors(
                                    state,
                                    strongIsWhite,
                                    proven
                            );

            int newCount =
                    FourPieceTierOnePrimitiveMoveGenerator
                            .generateLegalSuccessors(
                                    state,
                                    material,
                                    strongIsWhite,
                                    generic
                            );


            Set<String> oldKeys =
                    oldKqpkKeys(
                            proven,
                            oldCount
                    );

            Set<String> newKeys =
                    primitiveKeys(
                            generic,
                            newCount
                    );


            if (!oldKeys.equals(
                    newKeys
            )) {

                throw new IllegalStateException(
                        "Generic KQPK successor semantics differ from proven M33 "
                                + "at state "
                                + state
                                + ".\nold="
                                + oldKeys
                                + "\nnew="
                                + newKeys
                );
            }


            checked++;
        }


        System.out.println();

        System.out.println(
                "KQPK M33 regression — strong "
                        + (strongIsWhite
                        ? "WHITE"
                        : "BLACK")
        );

        System.out.println(
                "  states checked: "
                        + checked
        );

        System.out.println(
                "  exact semantic equivalence: PASSED"
        );
    }


    private static Set<String> modelKeys(
            Position position,
            List<Move> moves,
            FourPieceMaterialClass material,
            boolean sameSideOwnerIsWhite
    ) {

        Set<String> result =
                new HashSet<>();


        for (Move move :
                moves) {

            Position child =
                    position.makeMove(
                            move
                    );


            result.add(
                    modelKey(
                            child,
                            move,
                            material,
                            sameSideOwnerIsWhite
                    )
            );
        }


        return result;
    }


    private static String modelKey(
            Position child,
            Move move,
            FourPieceMaterialClass material,
            boolean sameSideOwnerIsWhite
    ) {

        int pieceCount =
                0;

        int wk =
                -1;

        int bk =
                -1;

        int first =
                -1;

        int pawn =
                -1;

        PieceType survivingType =
                null;

        int survivingSquare =
                -1;


        boolean firstOwnerWhite =
                material.distribution()
                        == FourPieceMaterialClass.Distribution.SAME_SIDE
                        ? sameSideOwnerIsWhite
                        : true;

        boolean pawnOwnerWhite =
                material.distribution()
                        == FourPieceMaterialClass.Distribution.SAME_SIDE
                        ? sameSideOwnerIsWhite
                        : false;


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
                        child.getBoard()
                                .getPiece(
                                        square
                                );


                if (piece == null) {

                    continue;
                }


                pieceCount++;

                int index =
                        rank * 8 + file;


                if (piece.type()
                        == PieceType.KING) {

                    if (piece.color()
                            == Color.WHITE) {

                        wk =
                                index;

                    } else {

                        bk =
                                index;
                    }

                    continue;
                }


                survivingType =
                        piece.type();

                survivingSquare =
                        index;


                if (piece.type()
                        == material.firstType()
                        && (piece.color() == Color.WHITE)
                        == firstOwnerWhite) {

                    first =
                            index;
                }


                if (piece.type()
                        == PieceType.PAWN
                        && (piece.color() == Color.WHITE)
                        == pawnOwnerWhite) {

                    pawn =
                            index;
                }
            }
        }


        int from =
                index(
                        move.from()
                );

        int to =
                index(
                        move.to()
                );

        PieceType promotion =
                move.isPromotion()
                        ? move.promotion()
                        : null;


        if (pieceCount == 3) {

            return threePieceKey(
                    survivingType,
                    survivingSquare,
                    promotion,
                    from,
                    to
            );
        }


        if (pieceCount != 4) {

            throw new IllegalStateException(
                    "Unexpected Tier-1 child piece count: "
                            + pieceCount
            );
        }


        if (promotion != null) {

            return promotionKey(
                    promotion,
                    from,
                    to
            );
        }


        if (first < 0
                || pawn < 0
                || wk < 0
                || bk < 0) {

            throw new IllegalStateException(
                    "Could not classify in-class Tier-1 child."
            );
        }


        int state =
                FourPieceGenericPrimitiveState.encode(
                        wk,
                        bk,
                        first,
                        pawn,
                        child.getSideToMove()
                                == Color.BLACK
                );


        return inClassKey(
                state,
                from,
                to
        );
    }


    private static Set<String> primitiveKeys(
            FourPieceTierOnePrimitiveMoveGenerator.Buffer buffer,
            int count
    ) {

        Set<String> result =
                new HashSet<>();


        for (int i = 0;
             i < count;
             i++) {

            int boundary =
                    buffer.boundaryType(
                            i
                    );


            if (boundary
                    == FourPieceTierOnePrimitiveMoveGenerator
                    .BOUNDARY_NONE) {

                result.add(
                        inClassKey(
                                buffer.state(i),
                                buffer.fromSquare(i),
                                buffer.toSquare(i)
                        )
                );

            } else if (boundary
                    == FourPieceTierOnePrimitiveMoveGenerator
                    .BOUNDARY_THREE_PIECE) {

                result.add(
                        threePieceKey(
                                buffer.survivingType(i),
                                buffer.survivingPieceSquare(i),
                                buffer.promotionType(i),
                                buffer.fromSquare(i),
                                buffer.toSquare(i)
                        )
                );

            } else if (boundary
                    == FourPieceTierOnePrimitiveMoveGenerator
                    .BOUNDARY_TIER_ZERO_PROMOTION) {

                result.add(
                        promotionKey(
                                buffer.promotionType(i),
                                buffer.fromSquare(i),
                                buffer.toSquare(i)
                        )
                );

            } else {

                throw new IllegalStateException(
                        "Unknown generic boundary."
                );
            }
        }


        return result;
    }


    private static Set<String> oldKqpkKeys(
            FourPieceTierOneKqpkPrimitiveMoveGenerator.Buffer buffer,
            int count
    ) {

        Set<String> result =
                new HashSet<>();


        for (int i = 0;
             i < count;
             i++) {

            int boundary =
                    buffer.boundaryType(
                            i
                    );


            if (boundary
                    == FourPieceTierOneKqpkPrimitiveMoveGenerator
                    .BOUNDARY_NONE) {

                result.add(
                        inClassKey(
                                buffer.state(i),
                                buffer.fromSquare(i),
                                buffer.toSquare(i)
                        )
                );

            } else if (boundary
                    == FourPieceTierOneKqpkPrimitiveMoveGenerator
                    .BOUNDARY_KQK) {

                result.add(
                        threePieceKey(
                                PieceType.QUEEN,
                                buffer.survivingPieceSquare(i),
                                null,
                                buffer.fromSquare(i),
                                buffer.toSquare(i)
                        )
                );

            } else if (boundary
                    == FourPieceTierOneKqpkPrimitiveMoveGenerator
                    .BOUNDARY_KPK) {

                result.add(
                        threePieceKey(
                                PieceType.PAWN,
                                buffer.survivingPieceSquare(i),
                                null,
                                buffer.fromSquare(i),
                                buffer.toSquare(i)
                        )
                );

            } else if (boundary
                    == FourPieceTierOneKqpkPrimitiveMoveGenerator
                    .BOUNDARY_PROMOTION) {

                result.add(
                        promotionKey(
                                buffer.promotionType(i),
                                buffer.fromSquare(i),
                                buffer.toSquare(i)
                        )
                );
            }
        }


        return result;
    }


    private static Position positionForState(
            int state,
            FourPieceMaterialClass material,
            boolean sameSideOwnerIsWhite
    ) {

        int wk =
                FourPieceGenericPrimitiveState.whiteKing(
                        state
                );

        int bk =
                FourPieceGenericPrimitiveState.blackKing(
                        state
                );

        int first =
                FourPieceGenericPrimitiveState.firstExtra(
                        state
                );

        int pawn =
                FourPieceGenericPrimitiveState.secondExtra(
                        state
                );


        boolean firstOwnerWhite =
                material.distribution()
                        == FourPieceMaterialClass.Distribution.SAME_SIDE
                        ? sameSideOwnerIsWhite
                        : true;

        boolean pawnOwnerWhite =
                material.distribution()
                        == FourPieceMaterialClass.Distribution.SAME_SIDE
                        ? sameSideOwnerIsWhite
                        : false;


        Board board =
                new Board();


        board.setPiece(
                square(
                        wk
                ),
                new Piece(
                        PieceType.KING,
                        Color.WHITE
                )
        );


        board.setPiece(
                square(
                        bk
                ),
                new Piece(
                        PieceType.KING,
                        Color.BLACK
                )
        );


        board.setPiece(
                square(
                        first
                ),
                new Piece(
                        material.firstType(),
                        firstOwnerWhite
                                ? Color.WHITE
                                : Color.BLACK
                )
        );


        board.setPiece(
                square(
                        pawn
                ),
                new Piece(
                        PieceType.PAWN,
                        pawnOwnerWhite
                                ? Color.WHITE
                                : Color.BLACK
                )
        );


        return new Position(
                board,
                FourPieceGenericPrimitiveState.blackToMove(
                        state
                )
                        ? Color.BLACK
                        : Color.WHITE,
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


    private static String inClassKey(
            int state,
            int from,
            int to
    ) {

        return "I:"
                + state
                + ":"
                + from
                + ":"
                + to;
    }


    private static String threePieceKey(
            PieceType survivingType,
            int survivingSquare,
            PieceType promotion,
            int from,
            int to
    ) {

        return "T:"
                + survivingType
                + ":"
                + survivingSquare
                + ":"
                + promotion
                + ":"
                + from
                + ":"
                + to;
    }


    private static String promotionKey(
            PieceType promotion,
            int from,
            int to
    ) {

        return "P:"
                + promotion
                + ":"
                + from
                + ":"
                + to;
    }


    private static String orientationName(
            FourPieceMaterialClass material,
            boolean sameSideOwnerIsWhite
    ) {

        if (material.distribution()
                == FourPieceMaterialClass.Distribution.SPLIT) {

            return "canonical split (first WHITE / pawn BLACK)";
        }


        return sameSideOwnerIsWhite
                ? "same-side owner WHITE"
                : "same-side owner BLACK";
    }


    private static int index(
            Square square
    ) {

        return square.rank() * 8
                + square.file();
    }


    private static Square square(
            int index
    ) {

        return new Square(
                index & 7,
                index >>> 3
        );
    }


    private static boolean distinct(
            int a,
            int b,
            int c,
            int d
    ) {

        return a != b
                && a != c
                && a != d
                && b != c
                && b != d
                && c != d;
    }
}

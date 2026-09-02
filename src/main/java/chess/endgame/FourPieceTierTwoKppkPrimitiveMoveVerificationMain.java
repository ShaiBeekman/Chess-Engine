package main.java.chess.endgame;

import main.java.chess.model.PieceType;

import java.util.HashSet;
import java.util.Set;

/**
 * Milestone 56 verification.
 *
 * Mechanical foundation gate for exact SAME_SIDE KPPK successor semantics.
 *
 * This does NOT solve KPPK yet. It proves the representation and forward
 * move/boundary rules needed before the predecessor and retrograde milestones.
 */
public final class FourPieceTierTwoKppkPrimitiveMoveVerificationMain {

    private static final FourPieceMaterialClass KPPK =
            FourPieceMaterialClass.sameSide(
                    PieceType.PAWN,
                    PieceType.PAWN
            );

    private static final FourPieceMaterialClass KP_KP =
            FourPieceMaterialClass.split(
                    PieceType.PAWN,
                    PieceType.PAWN
            );

    private FourPieceTierTwoKppkPrimitiveMoveVerificationMain() {
    }


    public static void main(
            String[] args
    ) {

        System.out.println(
                "KPPK Tier-2 primitive move foundation gate"
        );
        System.out.println(
                "=========================================="
        );

        verifyMaterialSupport();
        verifyPrimitiveCanonicalization();
        verifyWhitePawnPushes();
        verifyBlackPawnPushes();
        verifyPromotionBoundary();
        verifyKingCaptureBoundary();
        verifyNoPawnCaptureOrEnPassant();
        verifyCanonicalSuccessors();

        System.out.println();
        System.out.println(
                "KPPK TIER-2 PRIMITIVE MOVE FOUNDATION PASSED"
        );
        System.out.println(
                "NEXT: KPPK SAME-CLASS PREDECESSOR GENERATOR / COMPLETENESS"
        );
    }


    private static void verifyMaterialSupport() {

        require(
                FourPieceTierTwoKppkPrimitiveMoveGenerator.supports(
                        KPPK
                ),
                "KPPK must be supported."
        );

        require(
                !FourPieceTierTwoKppkPrimitiveMoveGenerator.supports(
                        KP_KP
                ),
                "KP-KP must not be accepted by the KPPK generator."
        );

        require(
                KPPK.buildTier()
                        == 2,
                "KPPK must be Tier-2."
        );

        System.out.println();
        System.out.println(
                "Material support"
        );
        System.out.println(
                "----------------"
        );
        System.out.println(
                "KPPK supported: true"
        );
        System.out.println(
                "KP-KP deliberately rejected: true"
        );
        System.out.println(
                "PASSED"
        );
    }


    private static void verifyPrimitiveCanonicalization() {

        int nonCanonical =
                FourPieceGenericPrimitiveState.encode(
                        sq("a1"),
                        sq("h8"),
                        sq("e4"),
                        sq("c3"),
                        false
                );

        require(
                !FourPieceGenericPrimitiveState.isCanonical(
                        nonCanonical,
                        KPPK
                ),
                "Reversed identical-pawn slots should be noncanonical."
        );

        int canonical =
                FourPieceGenericPrimitiveState.canonicalize(
                        nonCanonical,
                        KPPK
                );

        require(
                FourPieceGenericPrimitiveState.isCanonical(
                        canonical,
                        KPPK
                ),
                "KPPK canonicalization failed."
        );

        require(
                FourPieceGenericPrimitiveState.firstExtra(
                        canonical
                )
                        == sq("c3")
                        && FourPieceGenericPrimitiveState.secondExtra(
                        canonical
                )
                        == sq("e4"),
                "KPPK canonicalization did not sort pawn squares."
        );

        System.out.println();
        System.out.println(
                "Primitive identical-pawn canonicalization"
        );
        System.out.println(
                "-----------------------------------------"
        );
        System.out.println(
                "firstExtra < secondExtra: PASSED"
        );
    }


    private static void verifyWhitePawnPushes() {

        /*
         * WHITE: Ka1, pawns c2/f2; BLACK: Kh8; WHITE to move.
         * Both pawns have a single and double push.
         */
        int state =
                canonicalState(
                        "a1",
                        "h8",
                        "c2",
                        "f2",
                        false
                );

        FourPieceTierTwoKppkPrimitiveMoveGenerator.Buffer moves =
                generate(
                        state,
                        true
                );

        requireInClassMove(
                moves,
                "c2",
                "c3"
        );

        requireInClassMove(
                moves,
                "c2",
                "c4"
        );

        requireInClassMove(
                moves,
                "f2",
                "f3"
        );

        requireInClassMove(
                moves,
                "f2",
                "f4"
        );

        System.out.println();
        System.out.println(
                "WHITE pawn single/double pushes"
        );
        System.out.println(
                "--------------------------------"
        );
        System.out.println(
                "c2-c3 / c2-c4: PASSED"
        );
        System.out.println(
                "f2-f3 / f2-f4: PASSED"
        );
    }


    private static void verifyBlackPawnPushes() {

        /*
         * BLACK: Kh8, pawns c7/f7; WHITE: Ka1; BLACK to move.
         */
        int state =
                canonicalState(
                        "a1",
                        "h8",
                        "c7",
                        "f7",
                        true
                );

        FourPieceTierTwoKppkPrimitiveMoveGenerator.Buffer moves =
                generate(
                        state,
                        false
                );

        requireInClassMove(
                moves,
                "c7",
                "c6"
        );

        requireInClassMove(
                moves,
                "c7",
                "c5"
        );

        requireInClassMove(
                moves,
                "f7",
                "f6"
        );

        requireInClassMove(
                moves,
                "f7",
                "f5"
        );

        System.out.println();
        System.out.println(
                "BLACK pawn single/double pushes"
        );
        System.out.println(
                "--------------------------------"
        );
        System.out.println(
                "c7-c6 / c7-c5: PASSED"
        );
        System.out.println(
                "f7-f6 / f7-f5: PASSED"
        );
    }


    private static void verifyPromotionBoundary() {

        /*
         * WHITE pawn c7 promotes on c8; the second pawn remains on f4.
         * Each promotion enters a solved Tier-1 SAME_SIDE family.
         */
        int state =
                canonicalState(
                        "a1",
                        "h6",
                        "c7",
                        "f4",
                        false
                );

        FourPieceTierTwoKppkPrimitiveMoveGenerator.Buffer moves =
                generate(
                        state,
                        true
                );

        Set<PieceType> promotions =
                new HashSet<>();

        for (int i = 0;
             i < moves.size();
             i++) {

            if (moves.fromSquare(
                    i
            ) != sq("c7")
                    || moves.toSquare(
                    i
            ) != sq("c8")) {

                continue;
            }

            require(
                    moves.boundaryType(
                            i
                    ) == FourPieceTierTwoKppkPrimitiveMoveGenerator
                            .BOUNDARY_TIER_ONE_PROMOTION,
                    "KPPK promotion must enter Tier-1."
            );

            require(
                    moves.remainingPawnSquare(
                            i
                    ) == sq("f4"),
                    "Promotion boundary lost the remaining pawn square."
            );

            promotions.add(
                    moves.promotionType(
                            i
                    )
            );
        }

        require(
                promotions.size()
                        == 4
                        && promotions.contains(
                        PieceType.QUEEN
                )
                        && promotions.contains(
                        PieceType.ROOK
                )
                        && promotions.contains(
                        PieceType.BISHOP
                )
                        && promotions.contains(
                        PieceType.KNIGHT
                ),
                "Expected all four promotion types."
        );

        System.out.println();
        System.out.println(
                "Promotion dependency boundary"
        );
        System.out.println(
                "-----------------------------"
        );
        System.out.println(
                "KPPK -> KQPK/KRPK/KBPK/KNPK: PASSED"
        );
    }


    private static void verifyKingCaptureBoundary() {

        /*
         * WHITE owns the pawns. BLACK king on d4 can capture c3.
         * The surviving pawn on f5 gives an exact three-piece KPK child.
         */
        int state =
                canonicalState(
                        "a1",
                        "d4",
                        "c3",
                        "f5",
                        true
                );

        FourPieceTierTwoKppkPrimitiveMoveGenerator.Buffer moves =
                generate(
                        state,
                        true
                );

        int index =
                findMove(
                        moves,
                        "d4",
                        "c3"
                );

        require(
                index >= 0,
                "Expected black king d4xc3."
        );

        require(
                moves.boundaryType(
                        index
                ) == FourPieceTierTwoKppkPrimitiveMoveGenerator
                        .BOUNDARY_THREE_PIECE,
                "King capture of one pawn must enter three-piece KPK."
        );

        require(
                moves.survivingType(
                        index
                ) == PieceType.PAWN
                        && moves.survivingPieceSquare(
                        index
                ) == sq("f5"),
                "KPK boundary has wrong surviving pawn."
        );

        System.out.println();
        System.out.println(
                "King-capture dependency boundary"
        );
        System.out.println(
                "--------------------------------"
        );
        System.out.println(
                "KPPK -> KPK: PASSED"
        );
    }


    private static void verifyNoPawnCaptureOrEnPassant() {

        /*
         * Same-side pawns can never capture one another. There is no opposing
         * pawn, so an en-passant capture cannot exist in KPPK.
         *
         * Put the pawns diagonally adjacent and ensure neither diagonal move
         * is generated.
         */
        int state =
                canonicalState(
                        "a1",
                        "h8",
                        "d4",
                        "e5",
                        false
                );

        FourPieceTierTwoKppkPrimitiveMoveGenerator.Buffer moves =
                generate(
                        state,
                        true
                );

        require(
                findMove(
                        moves,
                        "d4",
                        "e5"
                ) < 0,
                "Same-side pawn capture was generated."
        );

        require(
                findMove(
                        moves,
                        "d4",
                        "e5"
                ) < 0,
                "Impossible en-passant-like move was generated."
        );

        System.out.println();
        System.out.println(
                "No en passant in KPPK"
        );
        System.out.println(
                "---------------------"
        );
        System.out.println(
                "No diagonal pawn capture generated: PASSED"
        );
    }


    private static void verifyCanonicalSuccessors() {

        int state =
                canonicalState(
                        "a1",
                        "h8",
                        "c3",
                        "c4",
                        false
                );

        FourPieceTierTwoKppkPrimitiveMoveGenerator.Buffer moves =
                generate(
                        state,
                        true
                );

        int inClass =
                0;

        for (int i = 0;
             i < moves.size();
             i++) {

            if (moves.boundaryType(
                    i
            ) != FourPieceTierTwoKppkPrimitiveMoveGenerator.BOUNDARY_NONE) {

                continue;
            }

            inClass++;

            require(
                    FourPieceGenericPrimitiveState.isCanonical(
                            moves.state(
                                    i
                            ),
                            KPPK
                    ),
                    "Generated KPPK in-class child is not canonical."
            );
        }

        require(
                inClass > 0,
                "Canonical-successor fixture generated no in-class moves."
        );

        System.out.println();
        System.out.println(
                "Canonical successor invariant"
        );
        System.out.println(
                "-----------------------------"
        );
        System.out.println(
                "In-class children checked: "
                        + inClass
        );
        System.out.println(
                "All firstExtra < secondExtra: PASSED"
        );
    }


    private static FourPieceTierTwoKppkPrimitiveMoveGenerator.Buffer generate(
            int state,
            boolean pawnOwnerIsWhite
    ) {

        FourPieceTierTwoKppkPrimitiveMoveGenerator.Buffer moves =
                new FourPieceTierTwoKppkPrimitiveMoveGenerator.Buffer(
                        32
                );

        FourPieceTierTwoKppkPrimitiveMoveGenerator.generateLegalSuccessors(
                state,
                KPPK,
                pawnOwnerIsWhite,
                moves
        );

        return moves;
    }


    private static int canonicalState(
            String whiteKing,
            String blackKing,
            String firstPawn,
            String secondPawn,
            boolean blackToMove
    ) {

        int state =
                FourPieceGenericPrimitiveState.encode(
                        sq(
                                whiteKing
                        ),
                        sq(
                                blackKing
                        ),
                        sq(
                                firstPawn
                        ),
                        sq(
                                secondPawn
                        ),
                        blackToMove
                );

        return FourPieceGenericPrimitiveState.canonicalize(
                state,
                KPPK
        );
    }


    private static void requireInClassMove(
            FourPieceTierTwoKppkPrimitiveMoveGenerator.Buffer moves,
            String from,
            String to
    ) {

        int index =
                findMove(
                        moves,
                        from,
                        to
                );

        require(
                index >= 0,
                "Missing move "
                        + from
                        + "-"
                        + to
                        + "."
        );

        require(
                moves.boundaryType(
                        index
                ) == FourPieceTierTwoKppkPrimitiveMoveGenerator.BOUNDARY_NONE,
                "Expected in-class move "
                        + from
                        + "-"
                        + to
                        + "."
        );
    }


    private static int findMove(
            FourPieceTierTwoKppkPrimitiveMoveGenerator.Buffer moves,
            String from,
            String to
    ) {

        int fromSquare =
                sq(
                        from
                );

        int toSquare =
                sq(
                        to
                );

        for (int i = 0;
             i < moves.size();
             i++) {

            if (moves.fromSquare(
                    i
            ) == fromSquare
                    && moves.toSquare(
                    i
            ) == toSquare) {

                return i;
            }
        }

        return -1;
    }


    private static int sq(
            String algebraic
    ) {

        if (algebraic == null
                || algebraic.length() != 2) {

            throw new IllegalArgumentException(
                    "Bad square: "
                            + algebraic
            );
        }

        int file =
                algebraic.charAt(
                        0
                ) - 'a';

        int rank =
                algebraic.charAt(
                        1
                ) - '1';

        if (file < 0
                || file >= 8
                || rank < 0
                || rank >= 8) {

            throw new IllegalArgumentException(
                    "Bad square: "
                            + algebraic
            );
        }

        return rank * 8
                + file;
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
}

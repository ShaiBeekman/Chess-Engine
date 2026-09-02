
        package main.java.chess.endgame;

import main.java.chess.model.PieceType;

/**
 * Milestone 60 verification.
 *
 * Focused exact-rule gate for the new KP-KP board/history representation and
 * primitive successor generator.
 */
public final class FourPieceTierTwoKpkpPrimitiveMoveVerificationMain {

    public static final String BUILD_ID =
            "M60-KPKP-KING-CAPTURE-FIX-V2";

    private static final FourPieceMaterialClass MATERIAL =
            FourPieceMaterialClass.split(
                    PieceType.PAWN,
                    PieceType.PAWN
            );

    private FourPieceTierTwoKpkpPrimitiveMoveVerificationMain() {
    }

    public static void main(
            String[] args
    ) {

        if (args.length != 0) {

            throw new IllegalArgumentException(
                    "Usage: FourPieceTierTwoKpkpPrimitiveMoveVerificationMain"
            );
        }



        System.out.println(
                "KP-KP Tier-2 EP-aware primitive move verification"
        );

        System.out.println(
                "Verification BUILD_ID: "
                        + BUILD_ID
        );

        System.out.println(
                "================================================="
        );

        verifyMaterialSupport();
        verifySparseEpState();
        verifyWhiteSingleAndDoublePush();
        verifyBlackSingleAndDoublePush();
        verifyWhiteEnPassant();
        verifyBlackEnPassant();
        verifyEpExpires();
        verifyNormalPawnCaptureBoundary();
        verifyKingCaptureBoundary();
        verifyWhitePromotionBoundary();
        verifyBlackPromotionBoundary();

        System.out.println();
        System.out.println(
                "KP-KP EP-AWARE STATE / MOVE FOUNDATION PASSED"
        );
        System.out.println(
                "NEXT: KP-KP EP-AWARE PREDECESSOR / COMPLETENESS GATE"
        );
    }

    private static void verifyMaterialSupport() {

        require(
                FourPieceTierTwoKpkpPrimitiveMoveGenerator.supports(
                        MATERIAL
                ),
                "KP-KP must be supported."
        );

        require(
                !FourPieceTierTwoKpkpPrimitiveMoveGenerator.supports(
                        FourPieceMaterialClass.sameSide(
                                PieceType.PAWN,
                                PieceType.PAWN
                        )
                ),
                "KPPK must not be accepted by KP-KP generator."
        );

        System.out.println(
                "Material support: PASSED"
        );
    }

    private static void verifySparseEpState() {

        FourPieceTierTwoKpkpPrimitiveState.State ordinary =
                state(
                        "a1",
                        "h8",
                        "e5",
                        "d5",
                        false,
                        false
                );

        FourPieceTierTwoKpkpPrimitiveState.State ep =
                state(
                        "a1",
                        "h8",
                        "e5",
                        "d5",
                        false,
                        true
                );

        require(
                ordinary.baseState()
                        == ep.baseState(),
                "EP overlay must not duplicate the dense board encoding."
        );

        require(
                !ordinary.enPassantAvailable()
                        && ep.enPassantAvailable(),
                "EP overlay bit mismatch."
        );

        require(
                FourPieceTierTwoKpkpPrimitiveState.enPassantTargetSquare(
                        ep
                ) == sq(
                        "d6"
                ),
                "WHITE EP target must be d6."
        );

        boolean rejected =
                false;

        try {

            state(
                    "a1",
                    "h8",
                    "e4",
                    "d5",
                    false,
                    true
            );

        } catch (IllegalArgumentException expected) {

            rejected =
                    true;
        }

        require(
                rejected,
                "Impossible EP overlay must be rejected."
        );

        System.out.println(
                "Sparse EP representation: PASSED"
        );
    }

    private static void verifyWhiteSingleAndDoublePush() {

        FourPieceTierTwoKpkpPrimitiveState.State parent =
                state(
                        "a1",
                        "h8",
                        "e2",
                        "d4",
                        false,
                        false
                );

        FourPieceTierTwoKpkpPrimitiveMoveGenerator.Buffer buffer =
                generate(
                        parent
                );

        int single =
                findInClass(
                        buffer,
                        "e2",
                        "e3"
                );

        int doub =
                findInClass(
                        buffer,
                        "e2",
                        "e4"
                );

        require(
                single >= 0
                        && doub >= 0,
                "WHITE e2-e3/e4 missing."
        );

        require(
                !buffer.state(
                        single
                ).enPassantAvailable(),
                "WHITE single push must not set EP."
        );

        require(
                buffer.state(
                        doub
                ).enPassantAvailable(),
                "WHITE double push beside BLACK pawn must set EP."
        );

        require(
                FourPieceTierTwoKpkpPrimitiveState.enPassantTargetSquare(
                        buffer.state(
                                doub
                        )
                ) == sq(
                        "e3"
                ),
                "WHITE double-push EP target must be e3."
        );

        System.out.println(
                "WHITE single/double push + EP creation: PASSED"
        );
    }

    private static void verifyBlackSingleAndDoublePush() {

        FourPieceTierTwoKpkpPrimitiveState.State parent =
                state(
                        "a1",
                        "h8",
                        "e5",
                        "d7",
                        true,
                        false
                );

        FourPieceTierTwoKpkpPrimitiveMoveGenerator.Buffer buffer =
                generate(
                        parent
                );

        int single =
                findInClass(
                        buffer,
                        "d7",
                        "d6"
                );

        int doub =
                findInClass(
                        buffer,
                        "d7",
                        "d5"
                );

        require(
                single >= 0
                        && doub >= 0,
                "BLACK d7-d6/d5 missing."
        );

        require(
                !buffer.state(
                        single
                ).enPassantAvailable(),
                "BLACK single push must not set EP."
        );

        require(
                buffer.state(
                        doub
                ).enPassantAvailable(),
                "BLACK double push beside WHITE pawn must set EP."
        );

        require(
                FourPieceTierTwoKpkpPrimitiveState.enPassantTargetSquare(
                        buffer.state(
                                doub
                        )
                ) == sq(
                        "d6"
                ),
                "BLACK double-push EP target must be d6."
        );

        System.out.println(
                "BLACK single/double push + EP creation: PASSED"
        );
    }

    private static void verifyWhiteEnPassant() {

        FourPieceTierTwoKpkpPrimitiveState.State parent =
                state(
                        "a1",
                        "h8",
                        "e5",
                        "d5",
                        false,
                        true
                );

        FourPieceTierTwoKpkpPrimitiveMoveGenerator.Buffer buffer =
                generate(
                        parent
                );

        int edge =
                findBoundary(
                        buffer,
                        FourPieceTierTwoKpkpPrimitiveMoveGenerator
                                .BOUNDARY_THREE_PIECE,
                        "e5",
                        "d6"
                );

        require(
                edge >= 0,
                "WHITE e5xd6 e.p. missing."
        );

        require(
                buffer.isEnPassantMove(
                        edge
                ),
                "WHITE EP edge not marked."
        );

        require(
                buffer.survivingPawnIsWhite(
                        edge
                ),
                "WHITE must be surviving KPK pawn after e.p."
        );

        require(
                buffer.survivingPieceSquare(
                        edge
                ) == sq(
                        "d6"
                ),
                "WHITE EP surviving pawn square must be d6."
        );

        System.out.println(
                "WHITE en passant: PASSED"
        );
    }

    private static void verifyBlackEnPassant() {

        FourPieceTierTwoKpkpPrimitiveState.State parent =
                state(
                        "a1",
                        "h8",
                        "e4",
                        "d4",
                        true,
                        true
                );

        FourPieceTierTwoKpkpPrimitiveMoveGenerator.Buffer buffer =
                generate(
                        parent
                );

        int edge =
                findBoundary(
                        buffer,
                        FourPieceTierTwoKpkpPrimitiveMoveGenerator
                                .BOUNDARY_THREE_PIECE,
                        "d4",
                        "e3"
                );

        require(
                edge >= 0,
                "BLACK d4xe3 e.p. missing."
        );

        require(
                buffer.isEnPassantMove(
                        edge
                ),
                "BLACK EP edge not marked."
        );

        require(
                !buffer.survivingPawnIsWhite(
                        edge
                ),
                "BLACK must be surviving KPK pawn after e.p."
        );

        require(
                buffer.survivingPieceSquare(
                        edge
                ) == sq(
                        "e3"
                ),
                "BLACK EP surviving pawn square must be e3."
        );

        System.out.println(
                "BLACK en passant: PASSED"
        );
    }

    private static void verifyEpExpires() {

        FourPieceTierTwoKpkpPrimitiveState.State parent =
                state(
                        "a1",
                        "h8",
                        "e5",
                        "d5",
                        false,
                        true
                );

        FourPieceTierTwoKpkpPrimitiveMoveGenerator.Buffer buffer =
                generate(
                        parent
                );

        int kingMove =
                findInClass(
                        buffer,
                        "a1",
                        "a2"
                );

        require(
                kingMove >= 0,
                "Expected WHITE king waiting move a1-a2."
        );

        require(
                !buffer.state(
                        kingMove
                ).enPassantAvailable(),
                "EP right must expire after a non-EP move."
        );

        System.out.println(
                "EP expiry after other move: PASSED"
        );
    }

    private static void verifyNormalPawnCaptureBoundary() {

        FourPieceTierTwoKpkpPrimitiveState.State parent =
                state(
                        "a1",
                        "h8",
                        "e4",
                        "d5",
                        false,
                        false
                );

        FourPieceTierTwoKpkpPrimitiveMoveGenerator.Buffer buffer =
                generate(
                        parent
                );

        int edge =
                findBoundary(
                        buffer,
                        FourPieceTierTwoKpkpPrimitiveMoveGenerator
                                .BOUNDARY_THREE_PIECE,
                        "e4",
                        "d5"
                );

        require(
                edge >= 0,
                "WHITE e4xd5 KPK boundary missing."
        );

        require(
                !buffer.isEnPassantMove(
                        edge
                ),
                "Ordinary capture must not be marked EP."
        );

        require(
                buffer.survivingPawnIsWhite(
                        edge
                )
                        && buffer.survivingPieceSquare(
                        edge
                ) == sq(
                        "d5"
                ),
                "Ordinary capture KPK metadata mismatch."
        );

        System.out.println(
                "Normal pawn-capture KPK boundary: PASSED"
        );
    }

    private static void verifyKingCaptureBoundary() {

        FourPieceTierTwoKpkpPrimitiveState.State parent =
                state(
                        "a1",
                        "e6",
                        "d5",
                        "h7",
                        true,
                        false
                );

        FourPieceTierTwoKpkpPrimitiveMoveGenerator.Buffer buffer =
                generate(
                        parent
                );

        int edge =
                findBoundary(
                        buffer,
                        FourPieceTierTwoKpkpPrimitiveMoveGenerator
                                .BOUNDARY_THREE_PIECE,
                        "e6",
                        "d5"
                );

        require(
                edge >= 0,
                "BLACK king capture e6xd5 missing."
        );

        require(
                !buffer.survivingPawnIsWhite(
                        edge
                )
                        && buffer.survivingPieceSquare(
                        edge
                ) == sq(
                        "h7"
                ),
                "King-capture KPK metadata mismatch."
        );

        System.out.println(
                "King-capture KPK boundary: PASSED"
        );
    }

    private static void verifyWhitePromotionBoundary() {

        FourPieceTierTwoKpkpPrimitiveState.State parent =
                state(
                        "a1",
                        "h8",
                        "e7",
                        "d5",
                        false,
                        false
                );

        FourPieceTierTwoKpkpPrimitiveMoveGenerator.Buffer buffer =
                generate(
                        parent
                );

        verifyPromotions(
                buffer,
                "e7",
                "e8",
                true,
                "d5"
        );

        System.out.println(
                "WHITE promotion -> split Tier-1: PASSED"
        );
    }

    private static void verifyBlackPromotionBoundary() {

        FourPieceTierTwoKpkpPrimitiveState.State parent =
                state(
                        "a1",
                        "h8",
                        "e4",
                        "d2",
                        true,
                        false
                );

        FourPieceTierTwoKpkpPrimitiveMoveGenerator.Buffer buffer =
                generate(
                        parent
                );

        verifyPromotions(
                buffer,
                "d2",
                "d1",
                false,
                "e4"
        );

        System.out.println(
                "BLACK promotion -> split Tier-1: PASSED"
        );
    }

    private static void verifyPromotions(
            FourPieceTierTwoKpkpPrimitiveMoveGenerator.Buffer buffer,
            String from,
            String to,
            boolean promotedPawnIsWhite,
            String remainingPawn
    ) {

        PieceType[] types = {
                PieceType.QUEEN,
                PieceType.ROOK,
                PieceType.BISHOP,
                PieceType.KNIGHT
        };

        for (PieceType type :
                types) {

            int found =
                    -1;

            for (int i = 0;
                 i < buffer.size();
                 i++) {

                if (buffer.boundaryType(
                        i
                ) == FourPieceTierTwoKpkpPrimitiveMoveGenerator
                        .BOUNDARY_TIER_ONE_PROMOTION
                        && buffer.fromSquare(
                        i
                ) == sq(
                        from
                )
                        && buffer.toSquare(
                        i
                ) == sq(
                        to
                )
                        && buffer.promotionType(
                        i
                ) == type) {

                    found =
                            i;

                    break;
                }
            }

            require(
                    found >= 0,
                    "Missing promotion "
                            + from
                            + "-"
                            + to
                            + "="
                            + type
            );

            require(
                    buffer.promotedPawnIsWhite(
                            found
                    ) == promotedPawnIsWhite,
                    "Promotion owner mismatch for "
                            + type
            );

            require(
                    buffer.remainingPawnSquare(
                            found
                    ) == sq(
                            remainingPawn
                    ),
                    "Remaining pawn square mismatch for "
                            + type
            );
        }
    }

    private static FourPieceTierTwoKpkpPrimitiveMoveGenerator.Buffer generate(
            FourPieceTierTwoKpkpPrimitiveState.State state
    ) {

        require(
                FourPieceTierTwoKpkpPrimitiveMoveGenerator
                        .isStructurallyLegal(
                                state,
                                MATERIAL
                        ),
                "Verification fixture is structurally illegal: "
                        + state
        );

        FourPieceTierTwoKpkpPrimitiveMoveGenerator.Buffer buffer =
                new FourPieceTierTwoKpkpPrimitiveMoveGenerator.Buffer(
                        32
                );

        FourPieceTierTwoKpkpPrimitiveMoveGenerator
                .generateLegalSuccessors(
                        state,
                        MATERIAL,
                        buffer
                );

        return buffer;
    }

    private static int findInClass(
            FourPieceTierTwoKpkpPrimitiveMoveGenerator.Buffer buffer,
            String from,
            String to
    ) {

        for (int i = 0;
             i < buffer.size();
             i++) {

            if (buffer.boundaryType(
                    i
            ) == FourPieceTierTwoKpkpPrimitiveMoveGenerator.BOUNDARY_NONE
                    && buffer.fromSquare(
                    i
            ) == sq(
                    from
            )
                    && buffer.toSquare(
                    i
            ) == sq(
                    to
            )) {

                return i;
            }
        }

        return -1;
    }

    private static int findBoundary(
            FourPieceTierTwoKpkpPrimitiveMoveGenerator.Buffer buffer,
            int boundary,
            String from,
            String to
    ) {

        for (int i = 0;
             i < buffer.size();
             i++) {

            if (buffer.boundaryType(
                    i
            ) == boundary
                    && buffer.fromSquare(
                    i
            ) == sq(
                    from
            )
                    && buffer.toSquare(
                    i
            ) == sq(
                    to
            )) {

                return i;
            }
        }

        return -1;
    }

    private static FourPieceTierTwoKpkpPrimitiveState.State state(
            String wk,
            String bk,
            String whitePawn,
            String blackPawn,
            boolean blackToMove,
            boolean enPassantAvailable
    ) {

        return FourPieceTierTwoKpkpPrimitiveState.encode(
                sq(
                        wk
                ),
                sq(
                        bk
                ),
                sq(
                        whitePawn
                ),
                sq(
                        blackPawn
                ),
                blackToMove,
                enPassantAvailable
        );
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
                || file > 7
                || rank < 0
                || rank > 7) {

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
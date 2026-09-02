package main.java.chess.endgame;

import main.java.chess.model.PieceType;

import java.util.Arrays;


/**
 * Milestone 42.
 *
 * Allocation-free primitive successor generator for every canonical
 * one-pawn four-piece material family:
 *
 * SAME_SIDE:
 *     KQPK, KRPK, KBPK, KNPK
 *
 * SPLIT:
 *     KQ-KP, KR-KP, KB-KP, KN-KP
 *
 * Canonical primitive slot convention:
 *
 *     firstExtra  = the non-pawn (Q/R/B/N)
 *     secondExtra = the pawn
 *
 * Ownership convention:
 *
 *     SAME_SIDE:
 *         both extras belong to sameSideOwnerIsWhite
 *
 *     SPLIT:
 *         firstExtra belongs to WHITE
 *         secondExtra (pawn) belongs to BLACK
 *
 * The SPLIT convention is the same canonical orientation already used by
 * FourPieceGenericTablebaseService.
 *
 * There is exactly one pawn total, so en passant is impossible.
 */
public final class FourPieceTierOnePrimitiveMoveGenerator {

    public static final int BOUNDARY_NONE =
            0;

    public static final int BOUNDARY_THREE_PIECE =
            1;

    public static final int BOUNDARY_TIER_ZERO_PROMOTION =
            2;


    private static final int TYPE_NONE =
            0;

    private static final int TYPE_QUEEN =
            1;

    private static final int TYPE_ROOK =
            2;

    private static final int TYPE_BISHOP =
            3;

    private static final int TYPE_KNIGHT =
            4;

    private static final int TYPE_PAWN =
            5;


    private static final int[] KING_DF = {
            -1, -1, -1, 0, 0, 1, 1, 1
    };

    private static final int[] KING_DR = {
            -1, 0, 1, -1, 1, -1, 0, 1
    };

    private static final int[] KNIGHT_DF = {
            -2, -2, -1, -1, 1, 1, 2, 2
    };

    private static final int[] KNIGHT_DR = {
            -1, 1, -2, 2, -2, 2, -1, 1
    };


    private FourPieceTierOnePrimitiveMoveGenerator() {
    }


    public static final class Buffer {

        private int[] states;

        private byte[] boundaryTypes;

        private byte[] survivingTypes;

        private int[] survivingSquares;

        private byte[] promotionTypes;

        private byte[] fromSquares;

        private byte[] toSquares;

        private int size;


        public Buffer() {

            this(
                    64
            );
        }


        public Buffer(
                int capacity
        ) {

            int actual =
                    Math.max(
                            16,
                            capacity
                    );


            states =
                    new int[actual];

            boundaryTypes =
                    new byte[actual];

            survivingTypes =
                    new byte[actual];

            survivingSquares =
                    new int[actual];

            promotionTypes =
                    new byte[actual];

            fromSquares =
                    new byte[actual];

            toSquares =
                    new byte[actual];
        }


        public void clear() {

            size =
                    0;
        }


        public int size() {

            return size;
        }


        public int state(
                int index
        ) {

            return states[index];
        }


        public int boundaryType(
                int index
        ) {

            return boundaryTypes[index]
                    & 0xFF;
        }


        public PieceType survivingType(
                int index
        ) {

            return decodeType(
                    survivingTypes[index]
                            & 0xFF
            );
        }


        public int survivingPieceSquare(
                int index
        ) {

            return survivingSquares[index];
        }


        public PieceType promotionType(
                int index
        ) {

            return decodeType(
                    promotionTypes[index]
                            & 0xFF
            );
        }


        public int fromSquare(
                int index
        ) {

            return fromSquares[index]
                    & 0xFF;
        }


        public int toSquare(
                int index
        ) {

            return toSquares[index]
                    & 0xFF;
        }


        public int promotionSquare(
                int index
        ) {

            return promotionType(index) == null
                    ? -1
                    : toSquare(index);
        }


        private void addInClass(
                int state,
                int from,
                int to
        ) {

            ensureCapacity();


            states[size] =
                    state;

            boundaryTypes[size] =
                    BOUNDARY_NONE;

            survivingTypes[size] =
                    TYPE_NONE;

            survivingSquares[size] =
                    -1;

            promotionTypes[size] =
                    TYPE_NONE;

            fromSquares[size] =
                    (byte) from;

            toSquares[size] =
                    (byte) to;

            size++;
        }


        private void addThreePiece(
                PieceType survivingType,
                int survivingSquare,
                PieceType promotionType,
                int from,
                int to
        ) {

            ensureCapacity();


            states[size] =
                    -1;

            boundaryTypes[size] =
                    BOUNDARY_THREE_PIECE;

            survivingTypes[size] =
                    encodeType(
                            survivingType
                    );

            survivingSquares[size] =
                    survivingSquare;

            promotionTypes[size] =
                    encodeType(
                            promotionType
                    );

            fromSquares[size] =
                    (byte) from;

            toSquares[size] =
                    (byte) to;

            size++;
        }


        private void addTierZeroPromotion(
                PieceType promotionType,
                int from,
                int to
        ) {

            ensureCapacity();


            states[size] =
                    -1;

            boundaryTypes[size] =
                    BOUNDARY_TIER_ZERO_PROMOTION;

            survivingTypes[size] =
                    TYPE_NONE;

            survivingSquares[size] =
                    -1;

            promotionTypes[size] =
                    encodeType(
                            promotionType
                    );

            fromSquares[size] =
                    (byte) from;

            toSquares[size] =
                    (byte) to;

            size++;
        }


        private void ensureCapacity() {

            if (size
                    < states.length) {

                return;
            }


            int next =
                    states.length * 2;


            states =
                    Arrays.copyOf(
                            states,
                            next
                    );

            boundaryTypes =
                    Arrays.copyOf(
                            boundaryTypes,
                            next
                    );

            survivingTypes =
                    Arrays.copyOf(
                            survivingTypes,
                            next
                    );

            survivingSquares =
                    Arrays.copyOf(
                            survivingSquares,
                            next
                    );

            promotionTypes =
                    Arrays.copyOf(
                            promotionTypes,
                            next
                    );

            fromSquares =
                    Arrays.copyOf(
                            fromSquares,
                            next
                    );

            toSquares =
                    Arrays.copyOf(
                            toSquares,
                            next
                    );
        }
    }


    public static boolean supports(
            FourPieceMaterialClass material
    ) {

        return material != null
                && material.buildTier() == 1
                && material.secondType()
                == PieceType.PAWN
                && material.firstType()
                != PieceType.PAWN;
    }


    public static int generateLegalSuccessors(
            int state,
            FourPieceMaterialClass material,
            boolean sameSideOwnerIsWhite,
            Buffer output
    ) {

        requireMaterial(
                material
        );


        if (output == null) {

            throw new IllegalArgumentException(
                    "Output buffer cannot be null."
            );
        }


        output.clear();


        if (!isStructurallyLegal(
                state,
                material,
                sameSideOwnerIsWhite
        )) {

            return 0;
        }


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

        boolean blackToMove =
                FourPieceGenericPrimitiveState.blackToMove(
                        state
                );

        boolean moverWhite =
                !blackToMove;

        boolean firstOwnerWhite =
                firstOwnerIsWhite(
                        material,
                        sameSideOwnerIsWhite
                );

        boolean pawnOwnerWhite =
                pawnOwnerIsWhite(
                        material,
                        sameSideOwnerIsWhite
                );


        int moverKing =
                moverWhite
                        ? wk
                        : bk;


        generateKing(
                wk,
                bk,
                first,
                pawn,
                moverKing,
                moverWhite,
                firstOwnerWhite,
                pawnOwnerWhite,
                material.firstType(),
                blackToMove,
                output
        );


        if (moverWhite
                == firstOwnerWhite) {

            generateFirstExtra(
                    wk,
                    bk,
                    first,
                    pawn,
                    moverWhite,
                    firstOwnerWhite,
                    pawnOwnerWhite,
                    material.firstType(),
                    blackToMove,
                    output
            );
        }


        if (moverWhite
                == pawnOwnerWhite) {

            generatePawn(
                    wk,
                    bk,
                    first,
                    pawn,
                    moverWhite,
                    firstOwnerWhite,
                    pawnOwnerWhite,
                    material.firstType(),
                    blackToMove,
                    output
            );
        }


        return output.size();
    }


    /**
     * Exact primitive-domain legality.
     *
     * As in the proven KQPK generator, legality is defined by the resulting
     * position: the side that moved previously may not have left its own king
     * in check. The current side to move may be in check.
     */
    public static boolean isStructurallyLegal(
            int state,
            FourPieceMaterialClass material,
            boolean sameSideOwnerIsWhite
    ) {

        requireMaterial(
                material
        );


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


        if (!distinct(
                wk,
                bk,
                first,
                pawn
        )) {

            return false;
        }


        if (adjacent(
                wk,
                bk
        )) {

            return false;
        }


        int pawnRank =
                pawn >>> 3;


        if (pawnRank == 0
                || pawnRank == 7) {

            return false;
        }


        boolean firstOwnerWhite =
                firstOwnerIsWhite(
                        material,
                        sameSideOwnerIsWhite
                );

        boolean pawnOwnerWhite =
                pawnOwnerIsWhite(
                        material,
                        sameSideOwnerIsWhite
                );

        boolean blackToMove =
                FourPieceGenericPrimitiveState.blackToMove(
                        state
                );

        boolean previousMoverWhite =
                blackToMove;

        int previousKing =
                previousMoverWhite
                        ? wk
                        : bk;


        return !isSquareAttackedBy(
                previousKing,
                !previousMoverWhite,
                wk,
                bk,
                first,
                true,
                material.firstType(),
                firstOwnerWhite,
                pawn,
                true,
                PieceType.PAWN,
                pawnOwnerWhite
        );
    }


    private static void generateKing(
            int wk,
            int bk,
            int first,
            int pawn,
            int from,
            boolean moverWhite,
            boolean firstOwnerWhite,
            boolean pawnOwnerWhite,
            PieceType firstType,
            boolean blackToMove,
            Buffer output
    ) {

        int ff =
                from & 7;

        int fr =
                from >>> 3;


        for (int i = 0;
             i < KING_DF.length;
             i++) {

            int nf =
                    ff + KING_DF[i];

            int nr =
                    fr + KING_DR[i];


            if (!inside(
                    nf,
                    nr
            )) {

                continue;
            }


            int to =
                    nr * 8 + nf;

            int enemyKing =
                    moverWhite
                            ? bk
                            : wk;


            if (to == enemyKing
                    || adjacent(
                    to,
                    enemyKing
            )) {

                continue;
            }


            if (to == first
                    && firstOwnerWhite
                    == moverWhite) {

                continue;
            }


            if (to == pawn
                    && pawnOwnerWhite
                    == moverWhite) {

                continue;
            }


            boolean firstPresent =
                    to != first;

            boolean pawnPresent =
                    to != pawn;


            int nwk =
                    moverWhite
                            ? to
                            : wk;

            int nbk =
                    moverWhite
                            ? bk
                            : to;


            if (!kingSafe(
                    moverWhite,
                    nwk,
                    nbk,
                    first,
                    firstPresent,
                    firstType,
                    firstOwnerWhite,
                    pawn,
                    pawnPresent,
                    PieceType.PAWN,
                    pawnOwnerWhite
            )) {

                continue;
            }


            if (!firstPresent) {

                output.addThreePiece(
                        PieceType.PAWN,
                        pawn,
                        null,
                        from,
                        to
                );

                continue;
            }


            if (!pawnPresent) {

                output.addThreePiece(
                        firstType,
                        first,
                        null,
                        from,
                        to
                );

                continue;
            }


            output.addInClass(
                    FourPieceGenericPrimitiveState.encode(
                            nwk,
                            nbk,
                            first,
                            pawn,
                            !blackToMove
                    ),
                    from,
                    to
            );
        }
    }


    private static void generateFirstExtra(
            int wk,
            int bk,
            int first,
            int pawn,
            boolean moverWhite,
            boolean firstOwnerWhite,
            boolean pawnOwnerWhite,
            PieceType firstType,
            boolean blackToMove,
            Buffer output
    ) {

        switch (firstType) {

            case QUEEN ->
                    generateSlider(
                            wk,
                            bk,
                            first,
                            pawn,
                            moverWhite,
                            firstOwnerWhite,
                            pawnOwnerWhite,
                            firstType,
                            blackToMove,
                            true,
                            true,
                            output
                    );

            case ROOK ->
                    generateSlider(
                            wk,
                            bk,
                            first,
                            pawn,
                            moverWhite,
                            firstOwnerWhite,
                            pawnOwnerWhite,
                            firstType,
                            blackToMove,
                            true,
                            false,
                            output
                    );

            case BISHOP ->
                    generateSlider(
                            wk,
                            bk,
                            first,
                            pawn,
                            moverWhite,
                            firstOwnerWhite,
                            pawnOwnerWhite,
                            firstType,
                            blackToMove,
                            false,
                            true,
                            output
                    );

            case KNIGHT ->
                    generateKnight(
                            wk,
                            bk,
                            first,
                            pawn,
                            moverWhite,
                            firstOwnerWhite,
                            pawnOwnerWhite,
                            firstType,
                            blackToMove,
                            output
                    );

            default ->
                    throw new IllegalStateException(
                            "Unsupported Tier-1 non-pawn type: "
                                    + firstType
                    );
        }
    }


    private static void generateSlider(
            int wk,
            int bk,
            int first,
            int pawn,
            boolean moverWhite,
            boolean firstOwnerWhite,
            boolean pawnOwnerWhite,
            PieceType firstType,
            boolean blackToMove,
            boolean orthogonal,
            boolean diagonal,
            Buffer output
    ) {

        int[][] directions =
                {
                        {1, 0}, {-1, 0}, {0, 1}, {0, -1},
                        {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
                };


        int ff =
                first & 7;

        int fr =
                first >>> 3;


        for (int[] direction :
                directions) {

            boolean isDiagonal =
                    direction[0] != 0
                            && direction[1] != 0;


            if (isDiagonal
                    && !diagonal) {

                continue;
            }


            if (!isDiagonal
                    && !orthogonal) {

                continue;
            }


            int nf =
                    ff + direction[0];

            int nr =
                    fr + direction[1];


            while (inside(
                    nf,
                    nr
            )) {

                int to =
                        nr * 8 + nf;


                if (to == wk
                        || to == bk) {

                    break;
                }


                if (to == pawn) {

                    if (pawnOwnerWhite
                            == moverWhite) {

                        break;
                    }


                    if (kingSafe(
                            moverWhite,
                            wk,
                            bk,
                            to,
                            true,
                            firstType,
                            firstOwnerWhite,
                            pawn,
                            false,
                            PieceType.PAWN,
                            pawnOwnerWhite
                    )) {

                        output.addThreePiece(
                                firstType,
                                to,
                                null,
                                first,
                                to
                        );
                    }


                    break;
                }


                if (kingSafe(
                        moverWhite,
                        wk,
                        bk,
                        to,
                        true,
                        firstType,
                        firstOwnerWhite,
                        pawn,
                        true,
                        PieceType.PAWN,
                        pawnOwnerWhite
                )) {

                    output.addInClass(
                            FourPieceGenericPrimitiveState.encode(
                                    wk,
                                    bk,
                                    to,
                                    pawn,
                                    !blackToMove
                            ),
                            first,
                            to
                    );
                }


                nf +=
                        direction[0];

                nr +=
                        direction[1];
            }
        }
    }


    private static void generateKnight(
            int wk,
            int bk,
            int first,
            int pawn,
            boolean moverWhite,
            boolean firstOwnerWhite,
            boolean pawnOwnerWhite,
            PieceType firstType,
            boolean blackToMove,
            Buffer output
    ) {

        int ff =
                first & 7;

        int fr =
                first >>> 3;


        for (int i = 0;
             i < KNIGHT_DF.length;
             i++) {

            int nf =
                    ff + KNIGHT_DF[i];

            int nr =
                    fr + KNIGHT_DR[i];


            if (!inside(
                    nf,
                    nr
            )) {

                continue;
            }


            int to =
                    nr * 8 + nf;


            if (to == wk
                    || to == bk) {

                continue;
            }


            if (to == pawn) {

                if (pawnOwnerWhite
                        == moverWhite) {

                    continue;
                }


                if (kingSafe(
                        moverWhite,
                        wk,
                        bk,
                        to,
                        true,
                        firstType,
                        firstOwnerWhite,
                        pawn,
                        false,
                        PieceType.PAWN,
                        pawnOwnerWhite
                )) {

                    output.addThreePiece(
                            firstType,
                            to,
                            null,
                            first,
                            to
                    );
                }


                continue;
            }


            if (kingSafe(
                    moverWhite,
                    wk,
                    bk,
                    to,
                    true,
                    firstType,
                    firstOwnerWhite,
                    pawn,
                    true,
                    PieceType.PAWN,
                    pawnOwnerWhite
            )) {

                output.addInClass(
                        FourPieceGenericPrimitiveState.encode(
                                wk,
                                bk,
                                to,
                                pawn,
                                !blackToMove
                        ),
                        first,
                        to
                );
            }
        }
    }


    private static void generatePawn(
            int wk,
            int bk,
            int first,
            int pawn,
            boolean moverWhite,
            boolean firstOwnerWhite,
            boolean pawnOwnerWhite,
            PieceType firstType,
            boolean blackToMove,
            Buffer output
    ) {

        int file =
                pawn & 7;

        int rank =
                pawn >>> 3;

        int direction =
                pawnOwnerWhite
                        ? 1
                        : -1;

        int promotionRank =
                pawnOwnerWhite
                        ? 7
                        : 0;

        int startRank =
                pawnOwnerWhite
                        ? 1
                        : 6;


        int oneRank =
                rank + direction;


        if (inside(
                file,
                oneRank
        )) {

            int one =
                    oneRank * 8 + file;


            if (empty(
                    one,
                    wk,
                    bk,
                    first,
                    pawn
            )) {

                if (oneRank
                        == promotionRank) {

                    addPromotionPushes(
                            wk,
                            bk,
                            first,
                            pawn,
                            one,
                            moverWhite,
                            firstOwnerWhite,
                            pawnOwnerWhite,
                            firstType,
                            output
                    );

                } else {

                    if (kingSafe(
                            moverWhite,
                            wk,
                            bk,
                            first,
                            true,
                            firstType,
                            firstOwnerWhite,
                            one,
                            true,
                            PieceType.PAWN,
                            pawnOwnerWhite
                    )) {

                        output.addInClass(
                                FourPieceGenericPrimitiveState.encode(
                                        wk,
                                        bk,
                                        first,
                                        one,
                                        !blackToMove
                                ),
                                pawn,
                                one
                        );
                    }


                    if (rank
                            == startRank) {

                        int twoRank =
                                rank
                                        + 2 * direction;

                        int two =
                                twoRank * 8
                                        + file;


                        if (empty(
                                two,
                                wk,
                                bk,
                                first,
                                pawn
                        )
                                && kingSafe(
                                moverWhite,
                                wk,
                                bk,
                                first,
                                true,
                                firstType,
                                firstOwnerWhite,
                                two,
                                true,
                                PieceType.PAWN,
                                pawnOwnerWhite
                        )) {

                            output.addInClass(
                                    FourPieceGenericPrimitiveState.encode(
                                            wk,
                                            bk,
                                            first,
                                            two,
                                            !blackToMove
                                    ),
                                    pawn,
                                    two
                            );
                        }
                    }
                }
            }
        }


        for (int df :
                new int[] {-1, 1}) {

            int captureFile =
                    file + df;

            int captureRank =
                    rank + direction;


            if (!inside(
                    captureFile,
                    captureRank
            )) {

                continue;
            }


            int to =
                    captureRank * 8
                            + captureFile;


            /*
             * With one pawn total the only capturable non-king is firstExtra,
             * and only in SPLIT material.
             */
            if (to != first
                    || firstOwnerWhite
                    == moverWhite) {

                continue;
            }


            if (captureRank
                    == promotionRank) {

                addPromotionCaptures(
                        wk,
                        bk,
                        first,
                        pawn,
                        to,
                        moverWhite,
                        firstOwnerWhite,
                        pawnOwnerWhite,
                        firstType,
                        output
                );

            } else {

                if (kingSafe(
                        moverWhite,
                        wk,
                        bk,
                        first,
                        false,
                        firstType,
                        firstOwnerWhite,
                        to,
                        true,
                        PieceType.PAWN,
                        pawnOwnerWhite
                )) {

                    output.addThreePiece(
                            PieceType.PAWN,
                            to,
                            null,
                            pawn,
                            to
                    );
                }
            }
        }
    }


    private static void addPromotionPushes(
            int wk,
            int bk,
            int first,
            int pawn,
            int to,
            boolean moverWhite,
            boolean firstOwnerWhite,
            boolean pawnOwnerWhite,
            PieceType firstType,
            Buffer output
    ) {

        for (PieceType promotion :
                promotionTypes()) {

            if (!kingSafe(
                    moverWhite,
                    wk,
                    bk,
                    first,
                    true,
                    firstType,
                    firstOwnerWhite,
                    to,
                    true,
                    promotion,
                    pawnOwnerWhite
            )) {

                continue;
            }


            output.addTierZeroPromotion(
                    promotion,
                    pawn,
                    to
            );
        }
    }


    private static void addPromotionCaptures(
            int wk,
            int bk,
            int first,
            int pawn,
            int to,
            boolean moverWhite,
            boolean firstOwnerWhite,
            boolean pawnOwnerWhite,
            PieceType firstType,
            Buffer output
    ) {

        for (PieceType promotion :
                promotionTypes()) {

            if (!kingSafe(
                    moverWhite,
                    wk,
                    bk,
                    first,
                    false,
                    firstType,
                    firstOwnerWhite,
                    to,
                    true,
                    promotion,
                    pawnOwnerWhite
            )) {

                continue;
            }


            /*
             * The pawn captured the only opposing non-king while promoting.
             * The result therefore has THREE pieces, not four.
             */
            output.addThreePiece(
                    promotion,
                    to,
                    promotion,
                    pawn,
                    to
            );
        }
    }


    private static boolean kingSafe(
            boolean moverWhite,
            int wk,
            int bk,
            int first,
            boolean firstPresent,
            PieceType firstType,
            boolean firstOwnerWhite,
            int second,
            boolean secondPresent,
            PieceType secondType,
            boolean secondOwnerWhite
    ) {

        int king =
                moverWhite
                        ? wk
                        : bk;


        return !isSquareAttackedBy(
                king,
                !moverWhite,
                wk,
                bk,
                first,
                firstPresent,
                firstType,
                firstOwnerWhite,
                second,
                secondPresent,
                secondType,
                secondOwnerWhite
        );
    }


    private static boolean isSquareAttackedBy(
            int target,
            boolean attackingWhite,
            int wk,
            int bk,
            int first,
            boolean firstPresent,
            PieceType firstType,
            boolean firstOwnerWhite,
            int second,
            boolean secondPresent,
            PieceType secondType,
            boolean secondOwnerWhite
    ) {

        int attackingKing =
                attackingWhite
                        ? wk
                        : bk;


        if (adjacent(
                attackingKing,
                target
        )) {

            return true;
        }


        if (firstPresent
                && firstOwnerWhite
                == attackingWhite
                && pieceAttacks(
                firstType,
                first,
                target,
                attackingWhite,
                wk,
                bk,
                first,
                firstPresent,
                second,
                secondPresent
        )) {

            return true;
        }


        return secondPresent
                && secondOwnerWhite
                == attackingWhite
                && pieceAttacks(
                secondType,
                second,
                target,
                attackingWhite,
                wk,
                bk,
                first,
                firstPresent,
                second,
                secondPresent
        );
    }


    private static boolean pieceAttacks(
            PieceType type,
            int from,
            int target,
            boolean pieceIsWhite,
            int wk,
            int bk,
            int first,
            boolean firstPresent,
            int second,
            boolean secondPresent
    ) {

        if (type
                == PieceType.PAWN) {

            return pawnAttacks(
                    from,
                    target,
                    pieceIsWhite
            );
        }


        if (type
                == PieceType.KNIGHT) {

            int df =
                    Math.abs(
                            (from & 7)
                                    - (target & 7)
                    );

            int dr =
                    Math.abs(
                            (from >>> 3)
                                    - (target >>> 3)
                    );


            return (df == 1
                    && dr == 2)
                    ||
                    (df == 2
                            && dr == 1);
        }


        int ff =
                from & 7;

        int fr =
                from >>> 3;

        int tf =
                target & 7;

        int tr =
                target >>> 3;

        int df =
                Integer.compare(
                        tf,
                        ff
                );

        int dr =
                Integer.compare(
                        tr,
                        fr
                );

        int absFile =
                Math.abs(
                        tf - ff
                );

        int absRank =
                Math.abs(
                        tr - fr
                );


        boolean orthogonal =
                ff == tf
                        || fr == tr;

        boolean diagonal =
                absFile
                        == absRank;


        boolean geometry =
                switch (type) {

                    case QUEEN ->
                            orthogonal
                                    || diagonal;

                    case ROOK ->
                            orthogonal;

                    case BISHOP ->
                            diagonal;

                    default ->
                            false;
                };


        if (!geometry) {

            return false;
        }


        int file =
                ff + df;

        int rank =
                fr + dr;


        while (file != tf
                || rank != tr) {

            int square =
                    rank * 8 + file;


            if (square == wk
                    || square == bk
                    || (firstPresent
                    && square == first)
                    || (secondPresent
                    && square == second)) {

                return false;
            }


            file +=
                    df;

            rank +=
                    dr;
        }


        return true;
    }


    private static boolean pawnAttacks(
            int pawn,
            int target,
            boolean pawnIsWhite
    ) {

        int pf =
                pawn & 7;

        int pr =
                pawn >>> 3;

        int tf =
                target & 7;

        int tr =
                target >>> 3;


        return tr
                == pr
                + (pawnIsWhite
                ? 1
                : -1)
                && Math.abs(
                tf - pf
        ) == 1;
    }


    private static boolean firstOwnerIsWhite(
            FourPieceMaterialClass material,
            boolean sameSideOwnerIsWhite
    ) {

        return material.distribution()
                == FourPieceMaterialClass.Distribution.SAME_SIDE
                ? sameSideOwnerIsWhite
                : true;
    }


    private static boolean pawnOwnerIsWhite(
            FourPieceMaterialClass material,
            boolean sameSideOwnerIsWhite
    ) {

        return material.distribution()
                == FourPieceMaterialClass.Distribution.SAME_SIDE
                ? sameSideOwnerIsWhite
                : false;
    }


    private static PieceType[] promotionTypes() {

        return new PieceType[] {
                PieceType.QUEEN,
                PieceType.ROOK,
                PieceType.BISHOP,
                PieceType.KNIGHT
        };
    }


    private static boolean empty(
            int square,
            int wk,
            int bk,
            int first,
            int pawn
    ) {

        return square != wk
                && square != bk
                && square != first
                && square != pawn;
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


    private static boolean adjacent(
            int first,
            int second
    ) {

        int ff =
                first & 7;

        int fr =
                first >>> 3;

        int sf =
                second & 7;

        int sr =
                second >>> 3;


        return Math.max(
                Math.abs(
                        ff - sf
                ),
                Math.abs(
                        fr - sr
                )
        ) <= 1;
    }


    private static boolean inside(
            int file,
            int rank
    ) {

        return file >= 0
                && file < 8
                && rank >= 0
                && rank < 8;
    }


    private static void requireMaterial(
            FourPieceMaterialClass material
    ) {

        if (!supports(
                material
        )) {

            throw new IllegalArgumentException(
                    "Expected a canonical one-pawn four-piece material class."
            );
        }
    }


    private static byte encodeType(
            PieceType type
    ) {

        if (type == null) {

            return TYPE_NONE;
        }


        return switch (type) {

            case QUEEN ->
                    TYPE_QUEEN;

            case ROOK ->
                    TYPE_ROOK;

            case BISHOP ->
                    TYPE_BISHOP;

            case KNIGHT ->
                    TYPE_KNIGHT;

            case PAWN ->
                    TYPE_PAWN;

            default ->
                    throw new IllegalArgumentException(
                            "Unsupported primitive non-king type: "
                                    + type
                    );
        };
    }


    private static PieceType decodeType(
            int code
    ) {

        return switch (code) {

            case TYPE_NONE ->
                    null;

            case TYPE_QUEEN ->
                    PieceType.QUEEN;

            case TYPE_ROOK ->
                    PieceType.ROOK;

            case TYPE_BISHOP ->
                    PieceType.BISHOP;

            case TYPE_KNIGHT ->
                    PieceType.KNIGHT;

            case TYPE_PAWN ->
                    PieceType.PAWN;

            default ->
                    throw new IllegalStateException(
                            "Unknown primitive piece-type code: "
                                    + code
                    );
        };
    }
}

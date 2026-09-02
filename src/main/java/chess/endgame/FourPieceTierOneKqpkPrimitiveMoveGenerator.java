package main.java.chess.endgame;

import main.java.chess.model.PieceType;

import java.util.Arrays;

/**
 * Milestone 33.
 *
 * Allocation-free primitive legal-successor generator for KQPK.
 *
 * Primitive slot convention:
 *     whiteKing
 *     blackKing
 *     firstExtra  = queen
 *     secondExtra = pawn
 *     side to move
 *
 * The strong side owns both queen and pawn.
 *
 * Successors are represented without Position/Board/Move allocation:
 *
 *     BOUNDARY_NONE
 *         child remains KQPK; state(index) is the encoded child.
 *
 *     BOUNDARY_KQK / BOUNDARY_KPK
 *         the weak king captured one strong-side extra piece and the child
 *         belongs to an exact three-piece tablebase.
 *
 *     BOUNDARY_PROMOTION
 *         the pawn promoted. promotionType(index) identifies Q/R/B/N and
 *         promotionSquare(index) identifies the promoted piece square.
 *
 * There is no en-passant state in KQPK because there is only one pawn total.
 */
public final class FourPieceTierOneKqpkPrimitiveMoveGenerator {

    public static final int BOUNDARY_NONE = 0;
    public static final int BOUNDARY_KQK = 1;
    public static final int BOUNDARY_KPK = 2;
    public static final int BOUNDARY_PROMOTION = 3;

    private static final int PROMOTION_NONE = 0;
    private static final int PROMOTION_QUEEN = 1;
    private static final int PROMOTION_ROOK = 2;
    private static final int PROMOTION_BISHOP = 3;
    private static final int PROMOTION_KNIGHT = 4;

    private static final int[] KING_DF =
            {-1, -1, -1, 0, 0, 1, 1, 1};

    private static final int[] KING_DR =
            {-1, 0, 1, -1, 1, -1, 0, 1};

    private static final int[][] QUEEN_DIRS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };


    private FourPieceTierOneKqpkPrimitiveMoveGenerator() {
    }


    public static final class Buffer {

        private int[] states;
        private byte[] boundaryTypes;
        private byte[] promotionTypes;
        private byte[] fromSquares;
        private byte[] toSquares;
        private int[] survivingPieceSquares;
        private int size;


        public Buffer() {
            this(64);
        }


        public Buffer(int capacity) {

            int actual =
                    Math.max(
                            16,
                            capacity
                    );

            states =
                    new int[actual];

            boundaryTypes =
                    new byte[actual];

            promotionTypes =
                    new byte[actual];

            fromSquares =
                    new byte[actual];

            toSquares =
                    new byte[actual];

            survivingPieceSquares =
                    new int[actual];
        }


        public void clear() {
            size = 0;
        }


        public int size() {
            return size;
        }


        public int state(int index) {
            return states[index];
        }


        public int boundaryType(int index) {
            return boundaryTypes[index] & 0xFF;
        }


        public int fromSquare(int index) {
            return fromSquares[index] & 0xFF;
        }


        public int toSquare(int index) {
            return toSquares[index] & 0xFF;
        }


        public PieceType promotionType(int index) {

            return switch (promotionTypes[index] & 0xFF) {

                case PROMOTION_NONE ->
                        null;

                case PROMOTION_QUEEN ->
                        PieceType.QUEEN;

                case PROMOTION_ROOK ->
                        PieceType.ROOK;

                case PROMOTION_BISHOP ->
                        PieceType.BISHOP;

                case PROMOTION_KNIGHT ->
                        PieceType.KNIGHT;

                default ->
                        throw new IllegalStateException(
                                "Unknown primitive promotion code."
                        );
            };
        }


        /**
         * For KQK/KPK boundaries, this is the square occupied by the
         * surviving non-king piece.
         *
         * For promotion boundaries this is -1; use promotionSquare().
         */
        public int survivingPieceSquare(int index) {
            return survivingPieceSquares[index];
        }


        public int promotionSquare(int index) {

            if (boundaryType(index)
                    != BOUNDARY_PROMOTION) {

                return -1;
            }

            return toSquare(index);
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

            promotionTypes[size] =
                    PROMOTION_NONE;

            fromSquares[size] =
                    (byte) from;

            toSquares[size] =
                    (byte) to;

            survivingPieceSquares[size] =
                    -1;

            size++;
        }


        private void addThreePieceBoundary(
                int boundaryType,
                int survivingPieceSquare,
                int from,
                int to
        ) {

            ensureCapacity();

            states[size] =
                    -1;

            boundaryTypes[size] =
                    (byte) boundaryType;

            promotionTypes[size] =
                    PROMOTION_NONE;

            fromSquares[size] =
                    (byte) from;

            toSquares[size] =
                    (byte) to;

            survivingPieceSquares[size] =
                    survivingPieceSquare;

            size++;
        }


        private void addPromotion(
                PieceType promotionType,
                int from,
                int to
        ) {

            ensureCapacity();

            states[size] =
                    -1;

            boundaryTypes[size] =
                    BOUNDARY_PROMOTION;

            promotionTypes[size] =
                    promotionCode(
                            promotionType
                    );

            fromSquares[size] =
                    (byte) from;

            toSquares[size] =
                    (byte) to;

            survivingPieceSquares[size] =
                    -1;

            size++;
        }


        private void ensureCapacity() {

            if (size < states.length) {
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

            survivingPieceSquares =
                    Arrays.copyOf(
                            survivingPieceSquares,
                            next
                    );
        }
    }


    public static int generateLegalSuccessors(
            int state,
            boolean strongIsWhite,
            Buffer output
    ) {

        if (output == null) {
            throw new IllegalArgumentException(
                    "Output buffer cannot be null."
            );
        }

        output.clear();

        if (!isStructurallyLegal(
                state,
                strongIsWhite
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

        int queen =
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

        boolean sideIsWhite =
                !blackToMove;

        boolean strongToMove =
                sideIsWhite
                        == strongIsWhite;


        if (strongToMove) {

            int strongKing =
                    strongIsWhite
                            ? wk
                            : bk;

            generateStrongKing(
                    wk,
                    bk,
                    queen,
                    pawn,
                    strongKing,
                    strongIsWhite,
                    blackToMove,
                    output
            );

            generateQueen(
                    wk,
                    bk,
                    queen,
                    pawn,
                    strongIsWhite,
                    blackToMove,
                    output
            );

            generatePawn(
                    wk,
                    bk,
                    queen,
                    pawn,
                    strongIsWhite,
                    blackToMove,
                    output
            );

        } else {

            int weakKing =
                    strongIsWhite
                            ? bk
                            : wk;

            generateWeakKing(
                    wk,
                    bk,
                    queen,
                    pawn,
                    weakKing,
                    strongIsWhite,
                    blackToMove,
                    output
            );
        }


        return output.size();
    }


    /**
     * Exact structural legality for an in-class KQPK primitive state.
     *
     * The side that moved previously may not have left its own king in check.
     * The current side to move is allowed to be in check.
     */
    public static boolean isStructurallyLegal(
            int state,
            boolean strongIsWhite
    ) {

        int wk =
                FourPieceGenericPrimitiveState.whiteKing(
                        state
                );

        int bk =
                FourPieceGenericPrimitiveState.blackKing(
                        state
                );

        int queen =
                FourPieceGenericPrimitiveState.firstExtra(
                        state
                );

        int pawn =
                FourPieceGenericPrimitiveState.secondExtra(
                        state
                );


        if (wk == bk
                || wk == queen
                || wk == pawn
                || bk == queen
                || bk == pawn
                || queen == pawn) {

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


        boolean blackToMove =
                FourPieceGenericPrimitiveState.blackToMove(
                        state
                );

        boolean previousMoverIsWhite =
                blackToMove;

        boolean previousMoverIsStrong =
                previousMoverIsWhite
                        == strongIsWhite;


        if (previousMoverIsStrong) {

            int strongKing =
                    strongIsWhite
                            ? wk
                            : bk;

            int weakKing =
                    strongIsWhite
                            ? bk
                            : wk;

            /*
             * A strong-side previous move is impossible if it left its king
             * attacked by the weak king. There is no other weak material.
             */
            return !adjacent(
                    strongKing,
                    weakKing
            );
        }


        int weakKing =
                strongIsWhite
                        ? bk
                        : wk;

        int strongKing =
                strongIsWhite
                        ? wk
                        : bk;


        /*
         * The weak side just moved. It may not have ended its move in check
         * from the strong king, queen, or pawn.
         */
        return !squareAttackedByStrong(
                weakKing,
                strongKing,
                queen,
                pawn,
                strongIsWhite
        );
    }


    private static void generateStrongKing(
            int wk,
            int bk,
            int queen,
            int pawn,
            int from,
            boolean strongIsWhite,
            boolean blackToMove,
            Buffer output
    ) {

        int ff =
                from & 7;

        int fr =
                from >>> 3;

        int enemyKing =
                strongIsWhite
                        ? bk
                        : wk;


        for (int i = 0;
             i < 8;
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


            if (to == queen
                    || to == pawn
                    || to == enemyKing
                    || adjacent(
                    to,
                    enemyKing
            )) {

                continue;
            }


            int nwk =
                    strongIsWhite
                            ? to
                            : wk;

            int nbk =
                    strongIsWhite
                            ? bk
                            : to;


            addIfLegal(
                    nwk,
                    nbk,
                    queen,
                    pawn,
                    !blackToMove,
                    strongIsWhite,
                    from,
                    to,
                    output
            );
        }
    }


    private static void generateWeakKing(
            int wk,
            int bk,
            int queen,
            int pawn,
            int from,
            boolean strongIsWhite,
            boolean blackToMove,
            Buffer output
    ) {

        int ff =
                from & 7;

        int fr =
                from >>> 3;

        int strongKing =
                strongIsWhite
                        ? wk
                        : bk;


        for (int i = 0;
             i < 8;
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


            if (to == strongKing
                    || adjacent(
                    to,
                    strongKing
            )) {

                continue;
            }


            boolean capturesQueen =
                    to == queen;

            boolean capturesPawn =
                    to == pawn;


            if (capturesQueen) {

                /*
                 * After KxQ the surviving pawn must not attack the weak king.
                 */
                if (pawnAttacks(
                        pawn,
                        to,
                        strongIsWhite
                )) {

                    continue;
                }


                output.addThreePieceBoundary(
                        BOUNDARY_KPK,
                        pawn,
                        from,
                        to
                );

                continue;
            }


            if (capturesPawn) {

                /*
                 * After KxP the surviving queen must not attack the weak king.
                 * The captured pawn is removed from the blocker set.
                 */
                if (queenAttacks(
                        queen,
                        to,
                        strongKing,
                        -1
                )) {

                    continue;
                }


                output.addThreePieceBoundary(
                        BOUNDARY_KQK,
                        queen,
                        from,
                        to
                );

                continue;
            }


            int nwk =
                    strongIsWhite
                            ? wk
                            : to;

            int nbk =
                    strongIsWhite
                            ? to
                            : bk;


            addIfLegal(
                    nwk,
                    nbk,
                    queen,
                    pawn,
                    !blackToMove,
                    strongIsWhite,
                    from,
                    to,
                    output
            );
        }
    }


    private static void generateQueen(
            int wk,
            int bk,
            int queen,
            int pawn,
            boolean strongIsWhite,
            boolean blackToMove,
            Buffer output
    ) {

        int ff =
                queen & 7;

        int fr =
                queen >>> 3;


        for (int[] dir :
                QUEEN_DIRS) {

            int nf =
                    ff + dir[0];

            int nr =
                    fr + dir[1];


            while (inside(
                    nf,
                    nr
            )) {

                int to =
                        nr * 8 + nf;


                if (to == wk
                        || to == bk
                        || to == pawn) {

                    break;
                }


                addIfLegal(
                        wk,
                        bk,
                        to,
                        pawn,
                        !blackToMove,
                        strongIsWhite,
                        queen,
                        to,
                        output
                );


                nf +=
                        dir[0];

                nr +=
                        dir[1];
            }
        }
    }


    private static void generatePawn(
            int wk,
            int bk,
            int queen,
            int pawn,
            boolean strongIsWhite,
            boolean blackToMove,
            Buffer output
    ) {

        int file =
                pawn & 7;

        int rank =
                pawn >>> 3;

        int direction =
                strongIsWhite
                        ? 1
                        : -1;

        int promotionRank =
                strongIsWhite
                        ? 7
                        : 0;

        int startRank =
                strongIsWhite
                        ? 1
                        : 6;


        // -----------------------------------------------------
        // SINGLE PUSH / PROMOTION
        // -----------------------------------------------------

        int oneRank =
                rank + direction;


        if (oneRank >= 0
                && oneRank < 8) {

            int one =
                    oneRank * 8
                            + file;


            if (empty(
                    one,
                    wk,
                    bk,
                    queen,
                    pawn
            )) {

                if (oneRank
                        == promotionRank) {

                    addPromotionsIfLegal(
                            wk,
                            bk,
                            queen,
                            pawn,
                            one,
                            strongIsWhite,
                            output
                    );

                } else {

                    addIfLegal(
                            wk,
                            bk,
                            queen,
                            one,
                            !blackToMove,
                            strongIsWhite,
                            pawn,
                            one,
                            output
                    );


                    // -----------------------------------------
                    // STARTING DOUBLE PUSH
                    // -----------------------------------------

                    if (rank == startRank) {

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
                                queen,
                                pawn
                        )) {

                            addIfLegal(
                                    wk,
                                    bk,
                                    queen,
                                    two,
                                    !blackToMove,
                                    strongIsWhite,
                                    pawn,
                                    two,
                                    output
                            );
                        }
                    }
                }
            }
        }


        /*
         * KQPK contains no weak non-king piece, so the strong pawn has no
         * diagonal capture to generate while the position remains KQPK.
         *
         * Promotion captures likewise cannot occur in same-side KQPK.
         */
    }


    private static void addPromotionsIfLegal(
            int wk,
            int bk,
            int queen,
            int pawnFrom,
            int promotionSquare,
            boolean strongIsWhite,
            Buffer output
    ) {

        int strongKing =
                strongIsWhite
                        ? wk
                        : bk;

        int weakKing =
                strongIsWhite
                        ? bk
                        : wk;


        /*
         * The pawn move must not leave the strong king adjacent to the weak
         * king. That condition is already invariant for a legal parent, but
         * keep the boundary validation self-contained.
         */
        if (adjacent(
                strongKing,
                weakKing
        )) {

            return;
        }


        output.addPromotion(
                PieceType.QUEEN,
                pawnFrom,
                promotionSquare
        );

        output.addPromotion(
                PieceType.ROOK,
                pawnFrom,
                promotionSquare
        );

        output.addPromotion(
                PieceType.BISHOP,
                pawnFrom,
                promotionSquare
        );

        output.addPromotion(
                PieceType.KNIGHT,
                pawnFrom,
                promotionSquare
        );
    }


    private static void addIfLegal(
            int wk,
            int bk,
            int queen,
            int pawn,
            boolean nextBlackToMove,
            boolean strongIsWhite,
            int from,
            int to,
            Buffer output
    ) {

        int next =
                FourPieceGenericPrimitiveState.encode(
                        wk,
                        bk,
                        queen,
                        pawn,
                        nextBlackToMove
                );


        if (isStructurallyLegal(
                next,
                strongIsWhite
        )) {

            output.addInClass(
                    next,
                    from,
                    to
            );
        }
    }


    private static boolean squareAttackedByStrong(
            int target,
            int strongKing,
            int queen,
            int pawn,
            boolean strongIsWhite
    ) {

        if (adjacent(
                target,
                strongKing
        )) {

            return true;
        }


        if (queenAttacks(
                queen,
                target,
                strongKing,
                pawn
        )) {

            return true;
        }


        return pawnAttacks(
                pawn,
                target,
                strongIsWhite
        );
    }


    private static boolean queenAttacks(
            int queen,
            int target,
            int blockerOne,
            int blockerTwo
    ) {

        int qf =
                queen & 7;

        int qr =
                queen >>> 3;

        int tf =
                target & 7;

        int tr =
                target >>> 3;

        int df =
                Integer.compare(
                        tf,
                        qf
                );

        int dr =
                Integer.compare(
                        tr,
                        qr
                );


        boolean rookLine =
                qf == tf
                        || qr == tr;

        boolean bishopLine =
                Math.abs(
                        tf - qf
                )
                        == Math.abs(
                        tr - qr
                );


        if (!rookLine
                && !bishopLine) {

            return false;
        }


        int file =
                qf + df;

        int rank =
                qr + dr;


        while (file != tf
                || rank != tr) {

            int square =
                    rank * 8
                            + file;


            if (square == blockerOne
                    || square == blockerTwo) {

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

        int expectedRank =
                pr
                        + (pawnIsWhite
                        ? 1
                        : -1);


        return tr == expectedRank
                && Math.abs(
                tf - pf
        ) == 1;
    }


    private static boolean empty(
            int square,
            int wk,
            int bk,
            int queen,
            int pawn
    ) {

        return square != wk
                && square != bk
                && square != queen
                && square != pawn;
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


    private static byte promotionCode(
            PieceType type
    ) {

        return switch (type) {

            case QUEEN ->
                    PROMOTION_QUEEN;

            case ROOK ->
                    PROMOTION_ROOK;

            case BISHOP ->
                    PROMOTION_BISHOP;

            case KNIGHT ->
                    PROMOTION_KNIGHT;

            default ->
                    throw new IllegalArgumentException(
                            "Not a legal promotion type: "
                                    + type
                    );
        };
    }
}

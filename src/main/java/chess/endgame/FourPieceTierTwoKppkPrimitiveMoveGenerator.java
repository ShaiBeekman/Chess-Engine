package main.java.chess.endgame;

import main.java.chess.model.PieceType;

import java.util.Arrays;

/**
 * Milestone 56.
 *
 * Allocation-free primitive successor generator for canonical KPPK.
 *
 * Material:
 *     SAME_SIDE KPPK
 *
 * Primitive slots:
 *     firstExtra  = pawn
 *     secondExtra = pawn
 *
 * Because the two pawns are physically indistinguishable, every in-class
 * state is canonicalized with:
 *
 *     firstExtra < secondExtra
 *
 * The existing FourPieceGenericPrimitiveState already provides exactly that
 * canonicalization for SAME_SIDE identical extras.
 *
 * There is no en passant in KPPK because both pawns have the same owner.
 *
 * External boundaries:
 *
 *     BOUNDARY_THREE_PIECE
 *         the pawnless king captures one pawn -> KPK
 *
 *     BOUNDARY_TIER_ONE_PROMOTION
 *         one pawn promotes while the other pawn remains -> KQPK/KRPK/KBPK/KNPK
 *
 * Promotion therefore enters already-solved Tier-1, not Tier-0.
 */
public final class FourPieceTierTwoKppkPrimitiveMoveGenerator {

    public static final int BOUNDARY_NONE =
            0;

    public static final int BOUNDARY_THREE_PIECE =
            1;

    public static final int BOUNDARY_TIER_ONE_PROMOTION =
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

    private static final int[] PAWN_CAPTURE_DF = {
            -1, 1
    };


    private FourPieceTierTwoKppkPrimitiveMoveGenerator() {
    }


    public static final class Buffer {

        private int[] states;

        private byte[] boundaryTypes;

        private byte[] survivingTypes;

        private int[] survivingSquares;

        private byte[] promotionTypes;

        private int[] remainingPawnSquares;

        private byte[] fromSquares;

        private byte[] toSquares;

        private int size;


        public Buffer() {

            this(
                    32
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

            remainingPawnSquares =
                    new int[actual];

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


        /**
         * For BOUNDARY_THREE_PIECE this is PAWN.
         * Otherwise null.
         */
        public PieceType survivingType(
                int index
        ) {

            return decodeType(
                    survivingTypes[index]
                            & 0xFF
            );
        }


        /**
         * For BOUNDARY_THREE_PIECE this is the surviving pawn square.
         * Otherwise -1.
         */
        public int survivingPieceSquare(
                int index
        ) {

            return survivingSquares[index];
        }


        /**
         * For BOUNDARY_TIER_ONE_PROMOTION this is Q/R/B/N.
         * Otherwise null.
         */
        public PieceType promotionType(
                int index
        ) {

            return decodeType(
                    promotionTypes[index]
                            & 0xFF
            );
        }


        /**
         * For BOUNDARY_TIER_ONE_PROMOTION this is the square of the pawn
         * that did not promote. Otherwise -1.
         */
        public int remainingPawnSquare(
                int index
        ) {

            return remainingPawnSquares[index];
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

            return promotionType(
                    index
            ) == null
                    ? -1
                    : toSquare(
                    index
            );
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

            remainingPawnSquares[size] =
                    -1;

            fromSquares[size] =
                    (byte) from;

            toSquares[size] =
                    (byte) to;

            size++;
        }


        private void addThreePiecePawn(
                int survivingPawnSquare,
                int from,
                int to
        ) {

            ensureCapacity();

            states[size] =
                    -1;

            boundaryTypes[size] =
                    BOUNDARY_THREE_PIECE;

            survivingTypes[size] =
                    TYPE_PAWN;

            survivingSquares[size] =
                    survivingPawnSquare;

            promotionTypes[size] =
                    TYPE_NONE;

            remainingPawnSquares[size] =
                    -1;

            fromSquares[size] =
                    (byte) from;

            toSquares[size] =
                    (byte) to;

            size++;
        }


        private void addTierOnePromotion(
                PieceType promotionType,
                int remainingPawnSquare,
                int from,
                int to
        ) {

            ensureCapacity();

            states[size] =
                    -1;

            boundaryTypes[size] =
                    BOUNDARY_TIER_ONE_PROMOTION;

            survivingTypes[size] =
                    TYPE_NONE;

            survivingSquares[size] =
                    -1;

            promotionTypes[size] =
                    encodeType(
                            promotionType
                    );

            remainingPawnSquares[size] =
                    remainingPawnSquare;

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

            remainingPawnSquares =
                    Arrays.copyOf(
                            remainingPawnSquares,
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
                && material.buildTier() == 2
                && material.distribution()
                == FourPieceMaterialClass.Distribution.SAME_SIDE
                && material.firstType()
                == PieceType.PAWN
                && material.secondType()
                == PieceType.PAWN;
    }


    public static int generateLegalSuccessors(
            int state,
            FourPieceMaterialClass material,
            boolean pawnOwnerIsWhite,
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
                pawnOwnerIsWhite
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

        int firstPawn =
                FourPieceGenericPrimitiveState.firstExtra(
                        state
                );

        int secondPawn =
                FourPieceGenericPrimitiveState.secondExtra(
                        state
                );

        boolean blackToMove =
                FourPieceGenericPrimitiveState.blackToMove(
                        state
                );

        boolean moverWhite =
                !blackToMove;

        int moverKing =
                moverWhite
                        ? wk
                        : bk;

        generateKing(
                wk,
                bk,
                firstPawn,
                secondPawn,
                moverKing,
                moverWhite,
                pawnOwnerIsWhite,
                blackToMove,
                material,
                output
        );

        if (moverWhite
                == pawnOwnerIsWhite) {

            generatePawn(
                    wk,
                    bk,
                    firstPawn,
                    secondPawn,
                    firstPawn,
                    secondPawn,
                    moverWhite,
                    pawnOwnerIsWhite,
                    blackToMove,
                    material,
                    output
            );

            generatePawn(
                    wk,
                    bk,
                    firstPawn,
                    secondPawn,
                    secondPawn,
                    firstPawn,
                    moverWhite,
                    pawnOwnerIsWhite,
                    blackToMove,
                    material,
                    output
            );
        }

        return output.size();
    }


    /**
     * Exact primitive-domain legality for KPPK.
     *
     * The two pawns must:
     *   - occupy distinct squares;
     *   - remain off ranks 1 and 8;
     *   - use the canonical identical-piece ordering firstExtra < secondExtra.
     *
     * As in Tier-1, the side that moved previously may not have left its own
     * king in check by the side that is now to move.
     */
    public static boolean isStructurallyLegal(
            int state,
            FourPieceMaterialClass material,
            boolean pawnOwnerIsWhite
    ) {

        requireMaterial(
                material
        );

        if (!FourPieceGenericPrimitiveState.isCanonical(
                state,
                material
        )) {

            return false;
        }

        int wk =
                FourPieceGenericPrimitiveState.whiteKing(
                        state
                );

        int bk =
                FourPieceGenericPrimitiveState.blackKing(
                        state
                );

        int firstPawn =
                FourPieceGenericPrimitiveState.firstExtra(
                        state
                );

        int secondPawn =
                FourPieceGenericPrimitiveState.secondExtra(
                        state
                );

        if (!distinct(
                wk,
                bk,
                firstPawn,
                secondPawn
        )) {

            return false;
        }

        if (adjacent(
                wk,
                bk
        )) {

            return false;
        }

        int firstRank =
                firstPawn >>> 3;

        int secondRank =
                secondPawn >>> 3;

        if (firstRank == 0
                || firstRank == 7
                || secondRank == 0
                || secondRank == 7) {

            return false;
        }

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

        boolean attackingWhite =
                !previousMoverWhite;

        if (attackingWhite
                != pawnOwnerIsWhite) {

            return true;
        }

        return !pawnAttacks(
                firstPawn,
                previousKing,
                pawnOwnerIsWhite
        )
                && !pawnAttacks(
                secondPawn,
                previousKing,
                pawnOwnerIsWhite
        );
    }


    private static void generateKing(
            int wk,
            int bk,
            int firstPawn,
            int secondPawn,
            int from,
            boolean moverWhite,
            boolean pawnOwnerIsWhite,
            boolean blackToMove,
            FourPieceMaterialClass material,
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
                    nr * 8
                            + nf;

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

            if (moverWhite
                    == pawnOwnerIsWhite
                    && (to == firstPawn
                    || to == secondPawn)) {

                continue;
            }

            boolean firstPresent =
                    to != firstPawn;

            boolean secondPresent =
                    to != secondPawn;

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
                    firstPawn,
                    firstPresent,
                    secondPawn,
                    secondPresent,
                    pawnOwnerIsWhite
            )) {

                continue;
            }

            if (!firstPresent) {

                output.addThreePiecePawn(
                        secondPawn,
                        from,
                        to
                );

                continue;
            }

            if (!secondPresent) {

                output.addThreePiecePawn(
                        firstPawn,
                        from,
                        to
                );

                continue;
            }

            int child =
                    FourPieceGenericPrimitiveState.encode(
                            nwk,
                            nbk,
                            firstPawn,
                            secondPawn,
                            !blackToMove
                    );

            output.addInClass(
                    FourPieceGenericPrimitiveState.canonicalize(
                            child,
                            material
                    ),
                    from,
                    to
            );
        }
    }


    private static void generatePawn(
            int wk,
            int bk,
            int firstPawn,
            int secondPawn,
            int pawn,
            int otherPawn,
            boolean moverWhite,
            boolean pawnOwnerIsWhite,
            boolean blackToMove,
            FourPieceMaterialClass material,
            Buffer output
    ) {

        int file =
                pawn & 7;

        int rank =
                pawn >>> 3;

        int direction =
                pawnOwnerIsWhite
                        ? 1
                        : -1;

        int promotionRank =
                pawnOwnerIsWhite
                        ? 7
                        : 0;

        int startRank =
                pawnOwnerIsWhite
                        ? 1
                        : 6;

        int oneRank =
                rank + direction;

        if (!inside(
                file,
                oneRank
        )) {

            return;
        }

        int one =
                oneRank * 8
                        + file;

        if (!empty(
                one,
                wk,
                bk,
                firstPawn,
                secondPawn
        )) {

            return;
        }

        if (oneRank
                == promotionRank) {

            addPromotionPushes(
                    wk,
                    bk,
                    pawn,
                    otherPawn,
                    one,
                    moverWhite,
                    pawnOwnerIsWhite,
                    output
            );

            return;
        }

        if (kingSafeAfterPawnMove(
                moverWhite,
                wk,
                bk,
                pawn,
                one,
                otherPawn,
                pawnOwnerIsWhite
        )) {

            int movedFirst =
                    pawn == firstPawn
                            ? one
                            : firstPawn;

            int movedSecond =
                    pawn == secondPawn
                            ? one
                            : secondPawn;

            int child =
                    FourPieceGenericPrimitiveState.encode(
                            wk,
                            bk,
                            movedFirst,
                            movedSecond,
                            !blackToMove
                    );

            output.addInClass(
                    FourPieceGenericPrimitiveState.canonicalize(
                            child,
                            material
                    ),
                    pawn,
                    one
            );
        }

        if (rank
                != startRank) {

            return;
        }

        int twoRank =
                rank + 2 * direction;

        int two =
                twoRank * 8
                        + file;

        if (!empty(
                two,
                wk,
                bk,
                firstPawn,
                secondPawn
        )) {

            return;
        }

        if (!kingSafeAfterPawnMove(
                moverWhite,
                wk,
                bk,
                pawn,
                two,
                otherPawn,
                pawnOwnerIsWhite
        )) {

            return;
        }

        int movedFirst =
                pawn == firstPawn
                        ? two
                        : firstPawn;

        int movedSecond =
                pawn == secondPawn
                        ? two
                        : secondPawn;

        int child =
                FourPieceGenericPrimitiveState.encode(
                        wk,
                        bk,
                        movedFirst,
                        movedSecond,
                        !blackToMove
                );

        output.addInClass(
                FourPieceGenericPrimitiveState.canonicalize(
                        child,
                        material
                ),
                pawn,
                two
        );
    }


    private static void addPromotionPushes(
            int wk,
            int bk,
            int pawn,
            int otherPawn,
            int to,
            boolean moverWhite,
            boolean pawnOwnerIsWhite,
            Buffer output
    ) {

        for (PieceType promotion :
                promotionTypes()) {

            if (!kingSafeAfterPromotion(
                    moverWhite,
                    wk,
                    bk,
                    to,
                    promotion,
                    otherPawn,
                    pawnOwnerIsWhite
            )) {

                continue;
            }

            output.addTierOnePromotion(
                    promotion,
                    otherPawn,
                    pawn,
                    to
            );
        }
    }


    private static boolean kingSafeAfterPawnMove(
            boolean moverWhite,
            int wk,
            int bk,
            int oldPawn,
            int newPawn,
            int otherPawn,
            boolean pawnOwnerIsWhite
    ) {

        /*
         * Both non-kings belong to pawnOwnerIsWhite. If that is the moving
         * side, neither pawn can attack its own king, so the only opposing
         * attacker is the opposing king. Keeping this helper explicit makes
         * the Tier-2 ownership rule obvious and keeps promotion checks aligned.
         */
        int king =
                moverWhite
                        ? wk
                        : bk;

        int enemyKing =
                moverWhite
                        ? bk
                        : wk;

        if (adjacent(
                king,
                enemyKing
        )) {

            return false;
        }

        if (pawnOwnerIsWhite
                == moverWhite) {

            return true;
        }

        return !pawnAttacks(
                newPawn,
                king,
                pawnOwnerIsWhite
        )
                && !pawnAttacks(
                otherPawn,
                king,
                pawnOwnerIsWhite
        );
    }


    private static boolean kingSafeAfterPromotion(
            boolean moverWhite,
            int wk,
            int bk,
            int promotionSquare,
            PieceType promotionType,
            int otherPawn,
            boolean pawnOwnerIsWhite
    ) {

        int king =
                moverWhite
                        ? wk
                        : bk;

        int enemyKing =
                moverWhite
                        ? bk
                        : wk;

        if (adjacent(
                king,
                enemyKing
        )) {

            return false;
        }

        /*
         * Promotion is made by the pawn-owning side, so the promoted piece
         * and remaining pawn are friendly to the mover's king. There is no
         * opposing non-king in KPPK.
         */
        return pawnOwnerIsWhite
                == moverWhite;
    }


    private static boolean kingSafe(
            boolean moverWhite,
            int wk,
            int bk,
            int firstPawn,
            boolean firstPresent,
            int secondPawn,
            boolean secondPresent,
            boolean pawnOwnerIsWhite
    ) {

        int king =
                moverWhite
                        ? wk
                        : bk;

        int enemyKing =
                moverWhite
                        ? bk
                        : wk;

        if (adjacent(
                king,
                enemyKing
        )) {

            return false;
        }

        if (pawnOwnerIsWhite
                == moverWhite) {

            return true;
        }

        return (!firstPresent
                || !pawnAttacks(
                firstPawn,
                king,
                pawnOwnerIsWhite
        ))
                && (!secondPresent
                || !pawnAttacks(
                secondPawn,
                king,
                pawnOwnerIsWhite
        ));
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
            int firstPawn,
            int secondPawn
    ) {

        return square != wk
                && square != bk
                && square != firstPawn
                && square != secondPawn;
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
                    "Expected canonical SAME_SIDE KPPK material."
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

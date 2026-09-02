package main.java.chess.endgame;

import main.java.chess.model.PieceType;

import java.util.Arrays;

/**
 * Milestone 60.
 *
 * Allocation-free primitive successor generator for canonical SPLIT KP-KP.
 *
 * Primitive slots:
 *     firstExtra  = WHITE pawn
 *     secondExtra = BLACK pawn
 *
 * Exact history:
 *     FourPieceTierTwoKpkpPrimitiveState.State carries a sparse
 *     enPassantAvailable overlay.
 *
 * External boundaries:
 *
 *     BOUNDARY_THREE_PIECE
 *         a king captures the opposing pawn, or a pawn captures the opposing
 *         pawn normally/en-passant -> KPK
 *
 *     BOUNDARY_TIER_ONE_PROMOTION
 *         a pawn promotes while the opposing pawn remains ->
 *         KQ-KP / KR-KP / KB-KP / KN-KP
 *
 * Ordinary moves clear any existing en-passant right. A legal two-square pawn
 * push sets the child EP flag only when the opposing pawn is actually in a
 * position to capture en passant on the next move.
 */
public final class FourPieceTierTwoKpkpPrimitiveMoveGenerator {

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

    private FourPieceTierTwoKpkpPrimitiveMoveGenerator() {
    }

    public static final class Buffer {

        private FourPieceTierTwoKpkpPrimitiveState.State[] states;
        private byte[] boundaryTypes;
        private byte[] survivingPawnOwners;
        private int[] survivingPawnSquares;
        private byte[] promotionTypes;
        private byte[] promotedPawnOwners;
        private int[] remainingPawnSquares;
        private byte[] fromSquares;
        private byte[] toSquares;
        private boolean[] enPassantMoves;
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
                    new FourPieceTierTwoKpkpPrimitiveState.State[actual];

            boundaryTypes =
                    new byte[actual];

            survivingPawnOwners =
                    new byte[actual];

            survivingPawnSquares =
                    new int[actual];

            promotionTypes =
                    new byte[actual];

            promotedPawnOwners =
                    new byte[actual];

            remainingPawnSquares =
                    new int[actual];

            fromSquares =
                    new byte[actual];

            toSquares =
                    new byte[actual];

            enPassantMoves =
                    new boolean[actual];
        }

        public void clear() {
            size = 0;
        }

        public int size() {
            return size;
        }

        public FourPieceTierTwoKpkpPrimitiveState.State state(
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

        public boolean survivingPawnIsWhite(
                int index
        ) {

            if (boundaryType(
                    index
            ) != BOUNDARY_THREE_PIECE) {

                throw new IllegalStateException(
                        "No surviving-pawn owner for non-three-piece boundary."
                );
            }

            return survivingPawnOwners[index]
                    != 0;
        }

        public PieceType survivingType(
                int index
        ) {

            return boundaryType(
                    index
            ) == BOUNDARY_THREE_PIECE
                    ? PieceType.PAWN
                    : null;
        }

        public int survivingPieceSquare(
                int index
        ) {
            return survivingPawnSquares[index];
        }

        public PieceType promotionType(
                int index
        ) {
            return decodeType(
                    promotionTypes[index]
                            & 0xFF
            );
        }

        public boolean promotedPawnIsWhite(
                int index
        ) {

            if (boundaryType(
                    index
            ) != BOUNDARY_TIER_ONE_PROMOTION) {

                throw new IllegalStateException(
                        "No promoted-pawn owner for non-promotion boundary."
                );
            }

            return promotedPawnOwners[index]
                    != 0;
        }

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

        public boolean isEnPassantMove(
                int index
        ) {
            return enPassantMoves[index];
        }

        private void addInClass(
                FourPieceTierTwoKpkpPrimitiveState.State state,
                int from,
                int to
        ) {

            ensureCapacity();

            states[size] =
                    state;

            boundaryTypes[size] =
                    BOUNDARY_NONE;

            survivingPawnOwners[size] =
                    0;

            survivingPawnSquares[size] =
                    -1;

            promotionTypes[size] =
                    TYPE_NONE;

            promotedPawnOwners[size] =
                    0;

            remainingPawnSquares[size] =
                    -1;

            fromSquares[size] =
                    (byte) from;

            toSquares[size] =
                    (byte) to;

            enPassantMoves[size] =
                    false;

            size++;
        }

        private void addThreePiecePawn(
                boolean survivingPawnIsWhite,
                int survivingPawnSquare,
                int from,
                int to,
                boolean enPassant
        ) {

            ensureCapacity();

            states[size] =
                    null;

            boundaryTypes[size] =
                    BOUNDARY_THREE_PIECE;

            survivingPawnOwners[size] =
                    (byte) (survivingPawnIsWhite
                            ? 1
                            : 0);

            survivingPawnSquares[size] =
                    survivingPawnSquare;

            promotionTypes[size] =
                    TYPE_NONE;

            promotedPawnOwners[size] =
                    0;

            remainingPawnSquares[size] =
                    -1;

            fromSquares[size] =
                    (byte) from;

            toSquares[size] =
                    (byte) to;

            enPassantMoves[size] =
                    enPassant;

            size++;
        }

        private void addTierOnePromotion(
                PieceType promotionType,
                boolean promotedPawnIsWhite,
                int remainingPawnSquare,
                int from,
                int to
        ) {

            ensureCapacity();

            states[size] =
                    null;

            boundaryTypes[size] =
                    BOUNDARY_TIER_ONE_PROMOTION;

            survivingPawnOwners[size] =
                    0;

            survivingPawnSquares[size] =
                    -1;

            promotionTypes[size] =
                    encodeType(
                            promotionType
                    );

            promotedPawnOwners[size] =
                    (byte) (promotedPawnIsWhite
                            ? 1
                            : 0);

            remainingPawnSquares[size] =
                    remainingPawnSquare;

            fromSquares[size] =
                    (byte) from;

            toSquares[size] =
                    (byte) to;

            enPassantMoves[size] =
                    false;

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

            survivingPawnOwners =
                    Arrays.copyOf(
                            survivingPawnOwners,
                            next
                    );

            survivingPawnSquares =
                    Arrays.copyOf(
                            survivingPawnSquares,
                            next
                    );

            promotionTypes =
                    Arrays.copyOf(
                            promotionTypes,
                            next
                    );

            promotedPawnOwners =
                    Arrays.copyOf(
                            promotedPawnOwners,
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

            enPassantMoves =
                    Arrays.copyOf(
                            enPassantMoves,
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
                == FourPieceMaterialClass.Distribution.SPLIT
                && material.firstType()
                == PieceType.PAWN
                && material.secondType()
                == PieceType.PAWN;
    }

    public static int generateLegalSuccessors(
            FourPieceTierTwoKpkpPrimitiveState.State state,
            FourPieceMaterialClass material,
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
                material
        )) {

            return 0;
        }

        int wk =
                FourPieceTierTwoKpkpPrimitiveState.whiteKing(
                        state
                );

        int bk =
                FourPieceTierTwoKpkpPrimitiveState.blackKing(
                        state
                );

        int whitePawn =
                FourPieceTierTwoKpkpPrimitiveState.whitePawn(
                        state
                );

        int blackPawn =
                FourPieceTierTwoKpkpPrimitiveState.blackPawn(
                        state
                );

        boolean blackToMove =
                FourPieceTierTwoKpkpPrimitiveState.blackToMove(
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
                whitePawn,
                blackPawn,
                moverKing,
                moverWhite,
                blackToMove,
                output
        );

        generatePawn(
                state,
                wk,
                bk,
                whitePawn,
                blackPawn,
                moverWhite,
                blackToMove,
                output
        );

        return output.size();
    }

    /**
     * Exact primitive-domain legality for a KP-KP board/history state.
     *
     * The state is rejected when:
     *   - pieces overlap;
     *   - kings are adjacent;
     *   - either pawn is on rank 1 or rank 8;
     *   - the side that moved previously left its king attacked by the pawn
     *     belonging to the side that is now to move;
     *   - the EP overlay is geometrically impossible.
     */
    public static boolean isStructurallyLegal(
            FourPieceTierTwoKpkpPrimitiveState.State state,
            FourPieceMaterialClass material
    ) {

        requireMaterial(
                material
        );

        if (state == null) {

            return false;
        }

        int wk =
                FourPieceTierTwoKpkpPrimitiveState.whiteKing(
                        state
                );

        int bk =
                FourPieceTierTwoKpkpPrimitiveState.blackKing(
                        state
                );

        int whitePawn =
                FourPieceTierTwoKpkpPrimitiveState.whitePawn(
                        state
                );

        int blackPawn =
                FourPieceTierTwoKpkpPrimitiveState.blackPawn(
                        state
                );

        if (!distinct(
                wk,
                bk,
                whitePawn,
                blackPawn
        )) {

            return false;
        }

        if (adjacent(
                wk,
                bk
        )) {

            return false;
        }

        int whitePawnRank =
                whitePawn >>> 3;

        int blackPawnRank =
                blackPawn >>> 3;

        if (whitePawnRank == 0
                || whitePawnRank == 7
                || blackPawnRank == 0
                || blackPawnRank == 7) {

            return false;
        }

        boolean blackToMove =
                FourPieceTierTwoKpkpPrimitiveState.blackToMove(
                        state
                );

        boolean previousMoverWhite =
                blackToMove;

        int previousKing =
                previousMoverWhite
                        ? wk
                        : bk;

        int currentMoverPawn =
                blackToMove
                        ? blackPawn
                        : whitePawn;

        boolean currentMoverPawnIsWhite =
                !blackToMove;

        if (pawnAttacks(
                currentMoverPawn,
                previousKing,
                currentMoverPawnIsWhite
        )) {

            return false;
        }

        return !state.enPassantAvailable()
                || FourPieceTierTwoKpkpPrimitiveState.canCarryEnPassant(
                state.baseState()
        );
    }

    private static void generateKing(
            int wk,
            int bk,
            int whitePawn,
            int blackPawn,
            int from,
            boolean moverWhite,
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

            int friendlyPawn =
                    moverWhite
                            ? whitePawn
                            : blackPawn;

            if (to == friendlyPawn) {

                continue;
            }

            int enemyPawn =
                    moverWhite
                            ? blackPawn
                            : whitePawn;

            boolean capturesEnemyPawn =
                    to == enemyPawn;

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
                    whitePawn,
                    !capturesEnemyPawn || moverWhite,
                    blackPawn,
                    !capturesEnemyPawn || !moverWhite
            )) {

                continue;
            }

            if (capturesEnemyPawn) {

                output.addThreePiecePawn(
                        moverWhite,
                        friendlyPawn,
                        from,
                        to,
                        false
                );

                continue;
            }

            int childBase =
                    FourPieceGenericPrimitiveState.encode(
                            nwk,
                            nbk,
                            whitePawn,
                            blackPawn,
                            !blackToMove
                    );

            output.addInClass(
                    FourPieceTierTwoKpkpPrimitiveState.of(
                            childBase,
                            false
                    ),
                    from,
                    to
            );
        }
    }

    private static void generatePawn(
            FourPieceTierTwoKpkpPrimitiveState.State state,
            int wk,
            int bk,
            int whitePawn,
            int blackPawn,
            boolean moverWhite,
            boolean blackToMove,
            Buffer output
    ) {

        int pawn =
                moverWhite
                        ? whitePawn
                        : blackPawn;

        int enemyPawn =
                moverWhite
                        ? blackPawn
                        : whitePawn;

        int file =
                pawn & 7;

        int rank =
                pawn >>> 3;

        int direction =
                moverWhite
                        ? 1
                        : -1;

        int promotionRank =
                moverWhite
                        ? 7
                        : 0;

        int startRank =
                moverWhite
                        ? 1
                        : 6;

        int oneRank =
                rank + direction;

        if (inside(
                file,
                oneRank
        )) {

            int one =
                    oneRank * 8
                            + file;

            if (empty(
                    one,
                    wk,
                    bk,
                    whitePawn,
                    blackPawn
            )) {

                if (oneRank
                        == promotionRank) {

                    addPromotionPushes(
                            wk,
                            bk,
                            moverWhite,
                            enemyPawn,
                            pawn,
                            one,
                            output
                    );

                } else if (kingSafeAfterPawnMove(
                        moverWhite,
                        wk,
                        bk,
                        one,
                        enemyPawn
                )) {

                    output.addInClass(
                            childState(
                                    wk,
                                    bk,
                                    moverWhite
                                            ? one
                                            : whitePawn,
                                    moverWhite
                                            ? blackPawn
                                            : one,
                                    !blackToMove,
                                    false
                            ),
                            pawn,
                            one
                    );

                    if (rank
                            == startRank) {

                        int twoRank =
                                rank + 2 * direction;

                        int two =
                                twoRank * 8
                                        + file;

                        if (empty(
                                two,
                                wk,
                                bk,
                                whitePawn,
                                blackPawn
                        )
                                && kingSafeAfterPawnMove(
                                moverWhite,
                                wk,
                                bk,
                                two,
                                enemyPawn
                        )) {

                            int childWhitePawn =
                                    moverWhite
                                            ? two
                                            : whitePawn;

                            int childBlackPawn =
                                    moverWhite
                                            ? blackPawn
                                            : two;

                            int childBase =
                                    FourPieceGenericPrimitiveState.encode(
                                            wk,
                                            bk,
                                            childWhitePawn,
                                            childBlackPawn,
                                            !blackToMove
                                    );

                            boolean epAvailable =
                                    FourPieceTierTwoKpkpPrimitiveState
                                            .canCarryEnPassant(
                                                    childBase
                                            );

                            output.addInClass(
                                    FourPieceTierTwoKpkpPrimitiveState.of(
                                            childBase,
                                            epAvailable
                                    ),
                                    pawn,
                                    two
                            );
                        }
                    }
                }
            }
        }

        generatePawnCaptures(
                state,
                wk,
                bk,
                whitePawn,
                blackPawn,
                moverWhite,
                pawn,
                enemyPawn,
                file,
                rank,
                direction,
                promotionRank,
                output
        );
    }

    private static void generatePawnCaptures(
            FourPieceTierTwoKpkpPrimitiveState.State state,
            int wk,
            int bk,
            int whitePawn,
            int blackPawn,
            boolean moverWhite,
            int pawn,
            int enemyPawn,
            int file,
            int rank,
            int direction,
            int promotionRank,
            Buffer output
    ) {

        int captureRank =
                rank + direction;

        for (int df :
                PAWN_CAPTURE_DF) {

            int captureFile =
                    file + df;

            if (!inside(
                    captureFile,
                    captureRank
            )) {

                continue;
            }

            int to =
                    captureRank * 8
                            + captureFile;

            if (to
                    == enemyPawn) {

                if (captureRank
                        == promotionRank) {

                    /*
                     * Capturing the only opposing pawn while promoting leaves a
                     * three-piece KXK position, not a Tier-1 four-piece family.
                     * M60 keeps promotion-capture semantics explicit but does
                     * not expose a misleading KPK boundary. This geometry is
                     * impossible for a legal non-promoted opposing pawn because
                     * the enemy pawn cannot occupy rank 8/1. Therefore no edge
                     * is emitted here.
                     */
                    continue;
                }

                if (kingSafeAfterPawnCapture(
                        moverWhite,
                        wk,
                        bk
                )) {

                    output.addThreePiecePawn(
                            moverWhite,
                            to,
                            pawn,
                            to,
                            false
                    );
                }

                continue;
            }

            if (!state.enPassantAvailable()) {

                continue;
            }

            int epTarget =
                    FourPieceTierTwoKpkpPrimitiveState
                            .enPassantTargetSquare(
                                    state
                            );

            if (to
                    != epTarget) {

                continue;
            }

            /*
             * En passant always lands on an empty square. Keep this defensive
             * rule check even though valid EP history already implies it.
             */
            if (!empty(
                    to,
                    wk,
                    bk,
                    whitePawn,
                    blackPawn
            )) {

                continue;
            }

            if (!kingSafeAfterPawnCapture(
                    moverWhite,
                    wk,
                    bk
            )) {

                continue;
            }

            output.addThreePiecePawn(
                    moverWhite,
                    to,
                    pawn,
                    to,
                    true
            );
        }
    }

    private static void addPromotionPushes(
            int wk,
            int bk,
            boolean moverWhite,
            int remainingEnemyPawn,
            int from,
            int to,
            Buffer output
    ) {

        if (!kingSafeAfterPawnMove(
                moverWhite,
                wk,
                bk,
                to,
                remainingEnemyPawn
        )) {

            return;
        }

        for (PieceType promotion :
                promotionTypes()) {

            output.addTierOnePromotion(
                    promotion,
                    moverWhite,
                    remainingEnemyPawn,
                    from,
                    to
            );
        }
    }

    private static FourPieceTierTwoKpkpPrimitiveState.State childState(
            int wk,
            int bk,
            int whitePawn,
            int blackPawn,
            boolean childBlackToMove,
            boolean enPassantAvailable
    ) {

        return FourPieceTierTwoKpkpPrimitiveState.encode(
                wk,
                bk,
                whitePawn,
                blackPawn,
                childBlackToMove,
                enPassantAvailable
        );
    }

    private static boolean kingSafeAfterPawnMove(
            boolean moverWhite,
            int wk,
            int bk,
            int movedPawn,
            int enemyPawn
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

        return !pawnAttacks(
                enemyPawn,
                king,
                !moverWhite
        );
    }

    private static boolean kingSafeAfterPawnCapture(
            boolean moverWhite,
            int wk,
            int bk
    ) {

        int king =
                moverWhite
                        ? wk
                        : bk;

        int enemyKing =
                moverWhite
                        ? bk
                        : wk;

        return !adjacent(
                king,
                enemyKing
        );
    }

    private static boolean kingSafe(
            boolean moverWhite,
            int wk,
            int bk,
            int whitePawn,
            boolean whitePawnPresent,
            int blackPawn,
            boolean blackPawnPresent
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

        if (moverWhite) {

            return !blackPawnPresent
                    || !pawnAttacks(
                    blackPawn,
                    king,
                    false
            );
        }

        return !whitePawnPresent
                || !pawnAttacks(
                whitePawn,
                king,
                true
        );
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
            int whitePawn,
            int blackPawn
    ) {

        return square != wk
                && square != bk
                && square != whitePawn
                && square != blackPawn;
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
                    "Expected canonical SPLIT KP-KP material."
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

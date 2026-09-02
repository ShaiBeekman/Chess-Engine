package main.java.chess.endgame;

import main.java.chess.model.PieceType;

import java.util.Arrays;

/**
 * Milestone 62 allocation-free compact successor generator for SPLIT KP-KP.
 *
 * Semantics are intentionally identical to the M60 verified State-based
 * generator, but same-class children are emitted directly as compact M62 IDs.
 */
public final class FourPieceTierTwoKpkpCompactMoveGenerator {

    public static final int BOUNDARY_NONE = 0;
    public static final int BOUNDARY_THREE_PIECE = 1;
    public static final int BOUNDARY_TIER_ONE_PROMOTION = 2;

    private static final int TYPE_NONE = 0;
    private static final int TYPE_QUEEN = 1;
    private static final int TYPE_ROOK = 2;
    private static final int TYPE_BISHOP = 3;
    private static final int TYPE_KNIGHT = 4;

    private static final int[] KING_DF = {
            -1, -1, -1, 0, 0, 1, 1, 1
    };

    private static final int[] KING_DR = {
            -1, 0, 1, -1, 1, -1, 0, 1
    };

    private static final int[] PAWN_CAPTURE_DF = {
            -1, 1
    };

    private FourPieceTierTwoKpkpCompactMoveGenerator() {
    }

    public static final class Buffer {

        private int[] states;
        private byte[] boundaryTypes;
        private byte[] survivingPawnOwners;
        private byte[] survivingPawnSquares;
        private byte[] promotionTypes;
        private byte[] promotedPawnOwners;
        private byte[] remainingPawnSquares;
        private byte[] fromSquares;
        private byte[] toSquares;
        private boolean[] enPassantMoves;
        private int size;

        public Buffer() {
            this(32);
        }

        public Buffer(int capacity) {

            int actual =
                    Math.max(16, capacity);

            states =
                    new int[actual];

            boundaryTypes =
                    new byte[actual];

            survivingPawnOwners =
                    new byte[actual];

            survivingPawnSquares =
                    new byte[actual];

            promotionTypes =
                    new byte[actual];

            promotedPawnOwners =
                    new byte[actual];

            remainingPawnSquares =
                    new byte[actual];

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

        public int state(int index) {
            return states[index];
        }

        public int boundaryType(int index) {
            return boundaryTypes[index] & 0xFF;
        }

        public boolean survivingPawnIsWhite(int index) {

            if (boundaryType(index)
                    != BOUNDARY_THREE_PIECE) {
                throw new IllegalStateException(
                        "No surviving-pawn owner for this edge."
                );
            }

            return survivingPawnOwners[index] != 0;
        }

        public int survivingPawnSquare(int index) {
            return survivingPawnSquares[index] & 0xFF;
        }

        public PieceType promotionType(int index) {

            return decodeType(
                    promotionTypes[index] & 0xFF
            );
        }

        public boolean promotedPawnIsWhite(int index) {

            if (boundaryType(index)
                    != BOUNDARY_TIER_ONE_PROMOTION) {
                throw new IllegalStateException(
                        "No promoted-pawn owner for this edge."
                );
            }

            return promotedPawnOwners[index] != 0;
        }

        public int remainingPawnSquare(int index) {
            return remainingPawnSquares[index] & 0xFF;
        }

        public int fromSquare(int index) {
            return fromSquares[index] & 0xFF;
        }

        public int toSquare(int index) {
            return toSquares[index] & 0xFF;
        }

        public boolean isEnPassantMove(int index) {
            return enPassantMoves[index];
        }

        private void addInClass(
                int state,
                int from,
                int to
        ) {

            ensureCapacity();

            states[size] = state;
            boundaryTypes[size] = BOUNDARY_NONE;
            survivingPawnOwners[size] = 0;
            survivingPawnSquares[size] = -1;
            promotionTypes[size] = TYPE_NONE;
            promotedPawnOwners[size] = 0;
            remainingPawnSquares[size] = -1;
            fromSquares[size] = (byte) from;
            toSquares[size] = (byte) to;
            enPassantMoves[size] = false;
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

            states[size] = -1;
            boundaryTypes[size] = BOUNDARY_THREE_PIECE;
            survivingPawnOwners[size] =
                    (byte) (survivingPawnIsWhite ? 1 : 0);
            survivingPawnSquares[size] =
                    (byte) survivingPawnSquare;
            promotionTypes[size] = TYPE_NONE;
            promotedPawnOwners[size] = 0;
            remainingPawnSquares[size] = -1;
            fromSquares[size] = (byte) from;
            toSquares[size] = (byte) to;
            enPassantMoves[size] = enPassant;
            size++;
        }

        private void addPromotion(
                PieceType type,
                boolean promotedPawnIsWhite,
                int remainingPawnSquare,
                int from,
                int to
        ) {

            ensureCapacity();

            states[size] = -1;
            boundaryTypes[size] = BOUNDARY_TIER_ONE_PROMOTION;
            survivingPawnOwners[size] = 0;
            survivingPawnSquares[size] = -1;
            promotionTypes[size] = encodeType(type);
            promotedPawnOwners[size] =
                    (byte) (promotedPawnIsWhite ? 1 : 0);
            remainingPawnSquares[size] =
                    (byte) remainingPawnSquare;
            fromSquares[size] = (byte) from;
            toSquares[size] = (byte) to;
            enPassantMoves[size] = false;
            size++;
        }

        private void ensureCapacity() {

            if (size < states.length) {
                return;
            }

            int next =
                    states.length * 2;

            states =
                    Arrays.copyOf(states, next);

            boundaryTypes =
                    Arrays.copyOf(boundaryTypes, next);

            survivingPawnOwners =
                    Arrays.copyOf(survivingPawnOwners, next);

            survivingPawnSquares =
                    Arrays.copyOf(survivingPawnSquares, next);

            promotionTypes =
                    Arrays.copyOf(promotionTypes, next);

            promotedPawnOwners =
                    Arrays.copyOf(promotedPawnOwners, next);

            remainingPawnSquares =
                    Arrays.copyOf(remainingPawnSquares, next);

            fromSquares =
                    Arrays.copyOf(fromSquares, next);

            toSquares =
                    Arrays.copyOf(toSquares, next);

            enPassantMoves =
                    Arrays.copyOf(enPassantMoves, next);
        }
    }

    public static boolean supports(
            FourPieceMaterialClass material
    ) {

        return material != null
                && material.buildTier() == 2
                && material.distribution()
                == FourPieceMaterialClass.Distribution.SPLIT
                && material.firstType() == PieceType.PAWN
                && material.secondType() == PieceType.PAWN;
    }

    public static int generateLegalSuccessors(
            int state,
            FourPieceMaterialClass material,
            Buffer output
    ) {

        requireMaterial(material);

        if (output == null) {
            throw new IllegalArgumentException(
                    "Output buffer cannot be null."
            );
        }

        output.clear();

        if (!isStructurallyLegal(state, material)) {
            return 0;
        }

        int wk =
                FourPieceTierTwoKpkpStateIndex.whiteKing(state);

        int bk =
                FourPieceTierTwoKpkpStateIndex.blackKing(state);

        int whitePawn =
                FourPieceTierTwoKpkpStateIndex.whitePawn(state);

        int blackPawn =
                FourPieceTierTwoKpkpStateIndex.blackPawn(state);

        boolean blackToMove =
                FourPieceTierTwoKpkpStateIndex.blackToMove(state);

        boolean moverWhite =
                !blackToMove;

        generateKing(
                wk,
                bk,
                whitePawn,
                blackPawn,
                moverWhite ? wk : bk,
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

    public static boolean isStructurallyLegal(
            int state,
            FourPieceMaterialClass material
    ) {

        requireMaterial(material);

        if (state < 0
                || state >= FourPieceTierTwoKpkpStateIndex.STATE_COUNT) {
            return false;
        }

        int wk =
                FourPieceTierTwoKpkpStateIndex.whiteKing(state);

        int bk =
                FourPieceTierTwoKpkpStateIndex.blackKing(state);

        int whitePawn =
                FourPieceTierTwoKpkpStateIndex.whitePawn(state);

        int blackPawn =
                FourPieceTierTwoKpkpStateIndex.blackPawn(state);

        if (!distinct(
                wk,
                bk,
                whitePawn,
                blackPawn
        )) {
            return false;
        }

        if (adjacent(wk, bk)) {
            return false;
        }

        int whiteRank =
                whitePawn >>> 3;

        int blackRank =
                blackPawn >>> 3;

        if (whiteRank == 0
                || whiteRank == 7
                || blackRank == 0
                || blackRank == 7) {
            return false;
        }

        boolean blackToMove =
                FourPieceTierTwoKpkpStateIndex.blackToMove(state);

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

        if (pawnAttacks(
                currentMoverPawn,
                previousKing,
                !blackToMove
        )) {
            return false;
        }

        if (FourPieceTierTwoKpkpStateIndex
                .enPassantAvailable(state)) {

            return FourPieceTierTwoKpkpPrimitiveState
                    .canCarryEnPassant(
                            FourPieceTierTwoKpkpStateIndex
                                    .baseState(state)
                    );
        }

        return true;
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

        for (int i = 0; i < KING_DF.length; i++) {

            int nf =
                    ff + KING_DF[i];

            int nr =
                    fr + KING_DR[i];

            if (!inside(nf, nr)) {
                continue;
            }

            int to =
                    nr * 8 + nf;

            int enemyKing =
                    moverWhite ? bk : wk;

            if (to == enemyKing
                    || adjacent(to, enemyKing)) {
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

            boolean capture =
                    to == enemyPawn;

            if (!capture
                    && pawnAttacks(
                    enemyPawn,
                    to,
                    !moverWhite
            )) {
                continue;
            }

            if (capture) {

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
                            moverWhite ? to : wk,
                            moverWhite ? bk : to,
                            whitePawn,
                            blackPawn,
                            !blackToMove
                    );

            output.addInClass(
                    FourPieceTierTwoKpkpStateIndex.ofBase(
                            childBase,
                            false
                    ),
                    from,
                    to
            );
        }
    }

    private static void generatePawn(
            int state,
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
                moverWhite ? 1 : -1;

        int promotionRank =
                moverWhite ? 7 : 0;

        int startRank =
                moverWhite ? 1 : 6;

        int oneRank =
                rank + direction;

        if (inside(file, oneRank)) {

            int one =
                    oneRank * 8 + file;

            if (empty(
                    one,
                    wk,
                    bk,
                    whitePawn,
                    blackPawn
            )) {

                if (oneRank == promotionRank) {

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
                        enemyPawn
                )) {

                    int childWhite =
                            moverWhite
                                    ? one
                                    : whitePawn;

                    int childBlack =
                            moverWhite
                                    ? blackPawn
                                    : one;

                    int childBase =
                            FourPieceGenericPrimitiveState.encode(
                                    wk,
                                    bk,
                                    childWhite,
                                    childBlack,
                                    !blackToMove
                            );

                    output.addInClass(
                            FourPieceTierTwoKpkpStateIndex.ofBase(
                                    childBase,
                                    false
                            ),
                            pawn,
                            one
                    );

                    if (rank == startRank) {

                        int two =
                                (rank + 2 * direction) * 8
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
                                enemyPawn
                        )) {

                            childWhite =
                                    moverWhite
                                            ? two
                                            : whitePawn;

                            childBlack =
                                    moverWhite
                                            ? blackPawn
                                            : two;

                            childBase =
                                    FourPieceGenericPrimitiveState.encode(
                                            wk,
                                            bk,
                                            childWhite,
                                            childBlack,
                                            !blackToMove
                                    );

                            boolean ep =
                                    FourPieceTierTwoKpkpPrimitiveState
                                            .canCarryEnPassant(
                                                    childBase
                                            );

                            output.addInClass(
                                    FourPieceTierTwoKpkpStateIndex
                                            .ofBase(
                                                    childBase,
                                                    ep
                                            ),
                                    pawn,
                                    two
                            );
                        }
                    }
                }
            }
        }

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

            if (to == enemyPawn) {

                if (captureRank == promotionRank) {
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

            if (!FourPieceTierTwoKpkpStateIndex
                    .enPassantAvailable(state)) {
                continue;
            }

            if (to
                    != FourPieceTierTwoKpkpStateIndex
                    .enPassantTargetSquare(state)) {
                continue;
            }

            /*
             * En passant always lands on an empty square. This also prevents a
             * stale/impossible raw EP overlay from manufacturing a KPK edge
             * onto a king-occupied target.
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
                        true
                );
            }
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
                remainingEnemyPawn
        )) {
            return;
        }

        output.addPromotion(
                PieceType.QUEEN,
                moverWhite,
                remainingEnemyPawn,
                from,
                to
        );

        output.addPromotion(
                PieceType.ROOK,
                moverWhite,
                remainingEnemyPawn,
                from,
                to
        );

        output.addPromotion(
                PieceType.BISHOP,
                moverWhite,
                remainingEnemyPawn,
                from,
                to
        );

        output.addPromotion(
                PieceType.KNIGHT,
                moverWhite,
                remainingEnemyPawn,
                from,
                to
        );
    }

    private static boolean kingSafeAfterPawnMove(
            boolean moverWhite,
            int wk,
            int bk,
            int enemyPawn
    ) {

        int king =
                moverWhite ? wk : bk;

        int enemyKing =
                moverWhite ? bk : wk;

        return !adjacent(
                king,
                enemyKing
        )
                && !pawnAttacks(
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

        return !adjacent(
                moverWhite ? wk : bk,
                moverWhite ? bk : wk
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

        return tr == pr + (pawnIsWhite ? 1 : -1)
                && Math.abs(tf - pf) == 1;
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
                Math.abs(ff - sf),
                Math.abs(fr - sr)
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

        if (!supports(material)) {
            throw new IllegalArgumentException(
                    "Expected canonical SPLIT KP-KP material."
            );
        }
    }

    private static byte encodeType(
            PieceType type
    ) {

        return switch (type) {
            case QUEEN -> TYPE_QUEEN;
            case ROOK -> TYPE_ROOK;
            case BISHOP -> TYPE_BISHOP;
            case KNIGHT -> TYPE_KNIGHT;
            default ->
                    throw new IllegalArgumentException(
                            "Unsupported KP-KP promotion type: "
                                    + type
                    );
        };
    }

    private static PieceType decodeType(
            int code
    ) {

        return switch (code) {
            case TYPE_NONE -> null;
            case TYPE_QUEEN -> PieceType.QUEEN;
            case TYPE_ROOK -> PieceType.ROOK;
            case TYPE_BISHOP -> PieceType.BISHOP;
            case TYPE_KNIGHT -> PieceType.KNIGHT;
            default ->
                    throw new IllegalStateException(
                            "Unknown KP-KP compact type code: "
                                    + code
                    );
        };
    }
}

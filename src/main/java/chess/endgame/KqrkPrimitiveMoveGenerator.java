package main.java.chess.endgame;

import java.util.Arrays;


/**
 * Allocation-free legal successor generator for KQRK.
 *
 * Material convention:
 *     strong side owns queen + rook
 *     weak side owns only its king
 *
 * In-class successors remain KQRK.  Weak-king captures of the queen or rook
 * are represented separately as exact 3-piece boundary transitions.
 */
public final class KqrkPrimitiveMoveGenerator {

    public static final int BOUNDARY_NONE = 0;
    public static final int BOUNDARY_KQK = 1;
    public static final int BOUNDARY_KRK = 2;
    public static final int BOUNDARY_KK = 3;

    private static final int[] KING_DF =
            {-1, -1, -1, 0, 0, 1, 1, 1};

    private static final int[] KING_DR =
            {-1, 0, 1, -1, 1, -1, 0, 1};

    private static final int[][] ROOK_DIRS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };

    private static final int[][] QUEEN_DIRS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };


    private KqrkPrimitiveMoveGenerator() {
    }


    public static final class Buffer {

        private int[] states;
        private byte[] boundaryTypes;
        private int[] capturedSquares;
        private int size;

        public Buffer() {
            this(64);
        }

        public Buffer(int capacity) {
            states = new int[Math.max(16, capacity)];
            boundaryTypes = new byte[states.length];
            capturedSquares = new int[states.length];
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

        /**
         * For a boundary transition this is the square occupied by the
         * surviving non-king piece, or -1 for K vs K.
         */
        public int survivingPieceSquare(int index) {
            return capturedSquares[index];
        }

        private void addInClass(int state) {
            ensureCapacity();
            states[size] = state;
            boundaryTypes[size] = BOUNDARY_NONE;
            capturedSquares[size] = -1;
            size++;
        }

        private void addBoundary(
                int boundaryType,
                int survivingPieceSquare
        ) {
            ensureCapacity();
            states[size] = -1;
            boundaryTypes[size] = (byte) boundaryType;
            capturedSquares[size] = survivingPieceSquare;
            size++;
        }

        private void ensureCapacity() {
            if (size < states.length) {
                return;
            }

            int next = states.length * 2;
            states = Arrays.copyOf(states, next);
            boundaryTypes = Arrays.copyOf(boundaryTypes, next);
            capturedSquares = Arrays.copyOf(capturedSquares, next);
        }
    }


    public static int generateLegalSuccessors(
            int state,
            boolean strongIsWhite,
            Buffer output
    ) {
        output.clear();

        if (!KqrkPrimitiveRules.isStructurallyLegal(
                state,
                strongIsWhite
        )) {
            return 0;
        }

        int wk = KqrkPrimitiveState.whiteKing(state);
        int bk = KqrkPrimitiveState.blackKing(state);
        int q = KqrkPrimitiveState.queen(state);
        int r = KqrkPrimitiveState.rook(state);

        boolean blackToMove =
                KqrkPrimitiveState.blackToMove(state);

        boolean sideIsWhite = !blackToMove;
        boolean strongToMove =
                sideIsWhite == strongIsWhite;

        if (strongToMove) {
            int strongKing = strongIsWhite ? wk : bk;

            generateStrongKing(
                    wk, bk, q, r,
                    strongKing,
                    strongIsWhite,
                    blackToMove,
                    output
            );

            generateSlider(
                    wk, bk, q, r,
                    q,
                    true,
                    strongIsWhite,
                    blackToMove,
                    output
            );

            generateSlider(
                    wk, bk, q, r,
                    r,
                    false,
                    strongIsWhite,
                    blackToMove,
                    output
            );
        } else {
            int weakKing = strongIsWhite ? bk : wk;

            generateWeakKing(
                    wk, bk, q, r,
                    weakKing,
                    strongIsWhite,
                    blackToMove,
                    output
            );
        }

        return output.size();
    }


    private static void generateStrongKing(
            int wk,
            int bk,
            int q,
            int r,
            int from,
            boolean strongIsWhite,
            boolean blackToMove,
            Buffer output
    ) {
        int ff = from & 7;
        int fr = from >>> 3;

        for (int i = 0; i < 8; i++) {
            int nf = ff + KING_DF[i];
            int nr = fr + KING_DR[i];

            if (!inside(nf, nr)) {
                continue;
            }

            int to = nr * 8 + nf;

            if (to == q || to == r) {
                continue;
            }

            int enemyKing = strongIsWhite ? bk : wk;

            if (to == enemyKing
                    || adjacent(to, enemyKing)) {
                continue;
            }

            int nwk = strongIsWhite ? to : wk;
            int nbk = strongIsWhite ? bk : to;

            addIfLegal(
                    nwk, nbk, q, r,
                    !blackToMove,
                    strongIsWhite,
                    output
            );
        }
    }


    private static void generateWeakKing(
            int wk,
            int bk,
            int q,
            int r,
            int from,
            boolean strongIsWhite,
            boolean blackToMove,
            Buffer output
    ) {
        int ff = from & 7;
        int fr = from >>> 3;

        int strongKing =
                strongIsWhite ? wk : bk;

        for (int i = 0; i < 8; i++) {
            int nf = ff + KING_DF[i];
            int nr = fr + KING_DR[i];

            if (!inside(nf, nr)) {
                continue;
            }

            int to = nr * 8 + nf;

            if (to == strongKing
                    || adjacent(to, strongKing)) {
                continue;
            }

            boolean capturesQueen = to == q;
            boolean capturesRook = to == r;

            if (capturesQueen || capturesRook) {
                int surviving =
                        capturesQueen ? r : q;

                boolean survivingIsRook =
                        capturesQueen;

                if (survivingAttacksKing(
                        surviving,
                        to,
                        strongKing,
                        survivingIsRook
                )) {
                    continue;
                }

                output.addBoundary(
                        capturesQueen
                                ? BOUNDARY_KRK
                                : BOUNDARY_KQK,
                        surviving
                );

                continue;
            }

            int nwk = strongIsWhite ? wk : to;
            int nbk = strongIsWhite ? to : bk;

            addIfLegal(
                    nwk, nbk, q, r,
                    !blackToMove,
                    strongIsWhite,
                    output
            );
        }
    }


    private static boolean survivingAttacksKing(
            int piece,
            int king,
            int blocker,
            boolean rook
    ) {
        if (rook) {
            return KqrkPrimitiveRules.rookAttacks(
                    piece,
                    king,
                    blocker,
                    -1,
                    -1
            );
        }

        return KqrkPrimitiveRules.queenAttacks(
                piece,
                king,
                blocker,
                -1,
                -1
        );
    }


    private static void generateSlider(
            int wk,
            int bk,
            int q,
            int r,
            int from,
            boolean queen,
            boolean strongIsWhite,
            boolean blackToMove,
            Buffer output
    ) {
        int[][] dirs =
                queen ? QUEEN_DIRS : ROOK_DIRS;

        int ff = from & 7;
        int fr = from >>> 3;

        for (int[] dir : dirs) {
            int nf = ff + dir[0];
            int nr = fr + dir[1];

            while (inside(nf, nr)) {
                int to = nr * 8 + nf;

                if (to == wk
                        || to == bk
                        || to == q
                        || to == r) {
                    break;
                }

                int nq = queen ? to : q;
                int nrk = queen ? r : to;

                addIfLegal(
                        wk, bk, nq, nrk,
                        !blackToMove,
                        strongIsWhite,
                        output
                );

                nf += dir[0];
                nr += dir[1];
            }
        }
    }


    private static void addIfLegal(
            int wk,
            int bk,
            int q,
            int r,
            boolean nextBlackToMove,
            boolean strongIsWhite,
            Buffer output
    ) {
        int next = KqrkPrimitiveState.encode(
                wk,
                bk,
                q,
                r,
                nextBlackToMove
        );

        if (KqrkPrimitiveRules.isStructurallyLegal(
                next,
                strongIsWhite
        )) {
            output.addInClass(next);
        }
    }


    private static boolean adjacent(
            int first,
            int second
    ) {
        int ff = first & 7;
        int fr = first >>> 3;
        int sf = second & 7;
        int sr = second >>> 3;

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
}

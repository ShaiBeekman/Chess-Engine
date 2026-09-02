package main.java.chess.endgame;

import java.util.Arrays;


/**
 * Allocation-free reverse move generator for in-class KQRK edges.
 *
 * Every KQRK -> KQRK move is non-capturing.  Therefore an in-class
 * predecessor is obtained by moving the previous mover's current piece
 * backward to an empty origin square, then validating the candidate by
 * generating its legal successors and confirming that the child is present.
 *
 * The validation step deliberately reuses KqrkPrimitiveMoveGenerator as the
 * source of truth.  This remains allocation-free while keeping reverse-move
 * logic simple and exact.
 */
public final class KqrkPrimitivePredecessorGenerator {

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

    private static final KqrkPrimitiveMoveGenerator.Buffer VERIFY =
            new KqrkPrimitiveMoveGenerator.Buffer(64);


    private KqrkPrimitivePredecessorGenerator() {
    }


    public static final class Buffer {

        private int[] states;
        private int size;

        public Buffer() {
            this(64);
        }

        public Buffer(int capacity) {
            states = new int[Math.max(16, capacity)];
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

        private void add(int state) {
            if (size == states.length) {
                states = Arrays.copyOf(
                        states,
                        states.length * 2
                );
            }

            states[size++] = state;
        }
    }


    public static int generateLegalPredecessors(
            int child,
            boolean strongIsWhite,
            Buffer output
    ) {
        output.clear();

        int wk = KqrkPrimitiveState.whiteKing(child);
        int bk = KqrkPrimitiveState.blackKing(child);
        int q = KqrkPrimitiveState.queen(child);
        int r = KqrkPrimitiveState.rook(child);

        boolean childBlackToMove =
                KqrkPrimitiveState.blackToMove(child);

        /*
         * Previous mover is opposite the child's side to move.
         */
        boolean previousMoverIsWhite =
                childBlackToMove;

        boolean previousMoverStrong =
                previousMoverIsWhite
                        == strongIsWhite;

        if (previousMoverStrong) {
            int strongKing =
                    strongIsWhite ? wk : bk;

            reverseKing(
                    child,
                    wk, bk, q, r,
                    strongKing,
                    true,
                    strongIsWhite,
                    childBlackToMove,
                    output
            );

            reverseSlider(
                    child,
                    wk, bk, q, r,
                    q,
                    true,
                    strongIsWhite,
                    childBlackToMove,
                    output
            );

            reverseSlider(
                    child,
                    wk, bk, q, r,
                    r,
                    false,
                    strongIsWhite,
                    childBlackToMove,
                    output
            );
        } else {
            int weakKing =
                    strongIsWhite ? bk : wk;

            reverseKing(
                    child,
                    wk, bk, q, r,
                    weakKing,
                    false,
                    strongIsWhite,
                    childBlackToMove,
                    output
            );
        }

        return output.size();
    }


    private static void reverseKing(
            int child,
            int wk,
            int bk,
            int q,
            int r,
            int destination,
            boolean strongKing,
            boolean strongIsWhite,
            boolean childBlackToMove,
            Buffer output
    ) {
        int df0 = destination & 7;
        int dr0 = destination >>> 3;

        for (int i = 0; i < 8; i++) {
            int file = df0 + KING_DF[i];
            int rank = dr0 + KING_DR[i];

            if (!inside(file, rank)) {
                continue;
            }

            int origin = rank * 8 + file;

            if (origin == wk
                    || origin == bk
                    || origin == q
                    || origin == r) {
                continue;
            }

            int pwk = wk;
            int pbk = bk;

            if (strongKing) {
                if (strongIsWhite) {
                    pwk = origin;
                } else {
                    pbk = origin;
                }
            } else {
                if (strongIsWhite) {
                    pbk = origin;
                } else {
                    pwk = origin;
                }
            }

            tryCandidate(
                    child,
                    pwk, pbk, q, r,
                    !childBlackToMove,
                    strongIsWhite,
                    output
            );
        }
    }


    private static void reverseSlider(
            int child,
            int wk,
            int bk,
            int q,
            int r,
            int destination,
            boolean queen,
            boolean strongIsWhite,
            boolean childBlackToMove,
            Buffer output
    ) {
        int[][] dirs =
                queen ? QUEEN_DIRS : ROOK_DIRS;

        int file0 = destination & 7;
        int rank0 = destination >>> 3;

        for (int[] dir : dirs) {
            int file = file0 + dir[0];
            int rank = rank0 + dir[1];

            while (inside(file, rank)) {
                int origin = rank * 8 + file;

                if (origin == wk
                        || origin == bk
                        || origin == q
                        || origin == r) {
                    break;
                }

                int pq = queen ? origin : q;
                int pr = queen ? r : origin;

                tryCandidate(
                        child,
                        wk, bk, pq, pr,
                        !childBlackToMove,
                        strongIsWhite,
                        output
                );

                file += dir[0];
                rank += dir[1];
            }
        }
    }


    private static void tryCandidate(
            int child,
            int wk,
            int bk,
            int q,
            int r,
            boolean predecessorBlackToMove,
            boolean strongIsWhite,
            Buffer output
    ) {
        int predecessor =
                KqrkPrimitiveState.encode(
                        wk,
                        bk,
                        q,
                        r,
                        predecessorBlackToMove
                );

        if (!KqrkPrimitiveRules.isStructurallyLegal(
                predecessor,
                strongIsWhite
        )) {
            return;
        }

        int count =
                KqrkPrimitiveMoveGenerator.generateLegalSuccessors(
                        predecessor,
                        strongIsWhite,
                        VERIFY
                );

        for (int i = 0; i < count; i++) {
            if (VERIFY.boundaryType(i)
                    == KqrkPrimitiveMoveGenerator.BOUNDARY_NONE
                    && VERIFY.state(i) == child) {
                output.add(predecessor);
                return;
            }
        }
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

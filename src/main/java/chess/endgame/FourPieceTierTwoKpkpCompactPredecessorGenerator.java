package main.java.chess.endgame;

import java.util.Arrays;

/**
 * Milestone 62 allocation-free same-class predecessor generator for compact
 * exact KP-KP state IDs.
 *
 * Reverse geometry is always forward-verified with the compact M62 successor
 * generator, so the exact EP-history transition is authoritative.
 */
public final class FourPieceTierTwoKpkpCompactPredecessorGenerator {

    private static final int[] KING_DF = {
            -1, -1, -1, 0, 0, 1, 1, 1
    };

    private static final int[] KING_DR = {
            -1, 0, 1, -1, 1, -1, 0, 1
    };

    private FourPieceTierTwoKpkpCompactPredecessorGenerator() {
    }

    public static final class Buffer {

        private int[] states;
        private int size;

        public Buffer() {
            this(32);
        }

        public Buffer(int capacity) {

            states =
                    new int[Math.max(16, capacity)];
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

        private void addUnique(int state) {

            for (int i = 0; i < size; i++) {
                if (states[i] == state) {
                    return;
                }
            }

            if (size == states.length) {
                states =
                        Arrays.copyOf(
                                states,
                                states.length * 2
                        );
            }

            states[size++] = state;
        }
    }

    public static int generatePredecessors(
            int child,
            FourPieceMaterialClass material,
            Buffer output,
            FourPieceTierTwoKpkpCompactMoveGenerator.Buffer forwardBuffer
    ) {

        if (!FourPieceTierTwoKpkpCompactMoveGenerator
                .supports(material)) {
            throw new IllegalArgumentException(
                    "Expected canonical SPLIT KP-KP material."
            );
        }

        if (output == null
                || forwardBuffer == null) {
            throw new IllegalArgumentException(
                    "KP-KP predecessor buffers cannot be null."
            );
        }

        output.clear();

        if (!FourPieceTierTwoKpkpCompactMoveGenerator
                .isStructurallyLegal(
                        child,
                        material
                )) {
            return 0;
        }

        int wk =
                FourPieceTierTwoKpkpStateIndex.whiteKing(child);

        int bk =
                FourPieceTierTwoKpkpStateIndex.blackKing(child);

        int whitePawn =
                FourPieceTierTwoKpkpStateIndex.whitePawn(child);

        int blackPawn =
                FourPieceTierTwoKpkpStateIndex.blackPawn(child);

        boolean childBlackToMove =
                FourPieceTierTwoKpkpStateIndex.blackToMove(child);

        boolean previousMoverWhite =
                childBlackToMove;

        boolean parentBlackToMove =
                !childBlackToMove;

        if (!FourPieceTierTwoKpkpStateIndex
                .enPassantAvailable(child)) {

            reverseKing(
                    child,
                    wk,
                    bk,
                    whitePawn,
                    blackPawn,
                    previousMoverWhite ? wk : bk,
                    parentBlackToMove,
                    material,
                    output,
                    forwardBuffer
            );
        }

        reversePawn(
                child,
                wk,
                bk,
                whitePawn,
                blackPawn,
                previousMoverWhite
                        ? whitePawn
                        : blackPawn,
                previousMoverWhite,
                parentBlackToMove,
                material,
                output,
                forwardBuffer
        );

        return output.size();
    }

    private static void reverseKing(
            int child,
            int wk,
            int bk,
            int whitePawn,
            int blackPawn,
            int currentKing,
            boolean parentBlackToMove,
            FourPieceMaterialClass material,
            Buffer output,
            FourPieceTierTwoKpkpCompactMoveGenerator.Buffer forwardBuffer
    ) {

        int file =
                currentKing & 7;

        int rank =
                currentKing >>> 3;

        for (int i = 0; i < KING_DF.length; i++) {

            int pf =
                    file + KING_DF[i];

            int pr =
                    rank + KING_DR[i];

            if (!inside(pf, pr)) {
                continue;
            }

            int previous =
                    pr * 8 + pf;

            if (occupied(
                    previous,
                    wk,
                    bk,
                    whitePawn,
                    blackPawn
            )) {
                continue;
            }

            validateBoardCandidate(
                    child,
                    currentKing == wk
                            ? previous
                            : wk,
                    currentKing == bk
                            ? previous
                            : bk,
                    whitePawn,
                    blackPawn,
                    parentBlackToMove,
                    material,
                    output,
                    forwardBuffer
            );
        }
    }

    private static void reversePawn(
            int child,
            int wk,
            int bk,
            int whitePawn,
            int blackPawn,
            int currentPawn,
            boolean movedPawnIsWhite,
            boolean parentBlackToMove,
            FourPieceMaterialClass material,
            Buffer output,
            FourPieceTierTwoKpkpCompactMoveGenerator.Buffer forwardBuffer
    ) {

        int file =
                currentPawn & 7;

        int rank =
                currentPawn >>> 3;

        int direction =
                movedPawnIsWhite ? 1 : -1;

        if (!FourPieceTierTwoKpkpStateIndex
                .enPassantAvailable(child)) {

            int previousRank =
                    rank - direction;

            if (previousRank > 0
                    && previousRank < 7) {

                int previous =
                        previousRank * 8 + file;

                if (!occupied(
                        previous,
                        wk,
                        bk,
                        whitePawn,
                        blackPawn
                )) {

                    validatePawnCandidate(
                            child,
                            wk,
                            bk,
                            whitePawn,
                            blackPawn,
                            previous,
                            movedPawnIsWhite,
                            parentBlackToMove,
                            material,
                            output,
                            forwardBuffer
                    );
                }
            }
        }

        int doubleDestinationRank =
                movedPawnIsWhite ? 3 : 4;

        int startRank =
                movedPawnIsWhite ? 1 : 6;

        if (rank == doubleDestinationRank) {

            int previous =
                    startRank * 8 + file;

            int intermediate =
                    (startRank + direction) * 8
                            + file;

            if (!occupied(
                    previous,
                    wk,
                    bk,
                    whitePawn,
                    blackPawn
            )
                    && !occupied(
                    intermediate,
                    wk,
                    bk,
                    whitePawn,
                    blackPawn
            )) {

                validatePawnCandidate(
                        child,
                        wk,
                        bk,
                        whitePawn,
                        blackPawn,
                        previous,
                        movedPawnIsWhite,
                        parentBlackToMove,
                        material,
                        output,
                        forwardBuffer
                );
            }
        }
    }

    private static void validatePawnCandidate(
            int child,
            int wk,
            int bk,
            int whitePawn,
            int blackPawn,
            int previousPawn,
            boolean movedPawnIsWhite,
            boolean parentBlackToMove,
            FourPieceMaterialClass material,
            Buffer output,
            FourPieceTierTwoKpkpCompactMoveGenerator.Buffer forwardBuffer
    ) {

        validateBoardCandidate(
                child,
                wk,
                bk,
                movedPawnIsWhite
                        ? previousPawn
                        : whitePawn,
                movedPawnIsWhite
                        ? blackPawn
                        : previousPawn,
                parentBlackToMove,
                material,
                output,
                forwardBuffer
        );
    }

    private static void validateBoardCandidate(
            int child,
            int wk,
            int bk,
            int whitePawn,
            int blackPawn,
            boolean parentBlackToMove,
            FourPieceMaterialClass material,
            Buffer output,
            FourPieceTierTwoKpkpCompactMoveGenerator.Buffer forwardBuffer
    ) {

        if (!distinct(
                wk,
                bk,
                whitePawn,
                blackPawn
        )) {
            return;
        }

        int base =
                FourPieceGenericPrimitiveState.encode(
                        wk,
                        bk,
                        whitePawn,
                        blackPawn,
                        parentBlackToMove
                );

        validateHistoryCandidate(
                child,
                FourPieceTierTwoKpkpStateIndex.ofBase(
                        base,
                        false
                ),
                material,
                output,
                forwardBuffer
        );

        if (FourPieceTierTwoKpkpPrimitiveState
                .canCarryEnPassant(base)) {

            validateHistoryCandidate(
                    child,
                    FourPieceTierTwoKpkpStateIndex.ofBase(
                            base,
                            true
                    ),
                    material,
                    output,
                    forwardBuffer
            );
        }
    }

    private static void validateHistoryCandidate(
            int child,
            int parent,
            FourPieceMaterialClass material,
            Buffer output,
            FourPieceTierTwoKpkpCompactMoveGenerator.Buffer forwardBuffer
    ) {

        if (!FourPieceTierTwoKpkpCompactMoveGenerator
                .isStructurallyLegal(
                        parent,
                        material
                )) {
            return;
        }

        int count =
                FourPieceTierTwoKpkpCompactMoveGenerator
                        .generateLegalSuccessors(
                                parent,
                                material,
                                forwardBuffer
                        );

        for (int i = 0; i < count; i++) {

            if (forwardBuffer.boundaryType(i)
                    == FourPieceTierTwoKpkpCompactMoveGenerator
                    .BOUNDARY_NONE
                    && forwardBuffer.state(i) == child) {

                output.addUnique(parent);
                return;
            }
        }
    }

    private static boolean occupied(
            int square,
            int wk,
            int bk,
            int whitePawn,
            int blackPawn
    ) {

        return square == wk
                || square == bk
                || square == whitePawn
                || square == blackPawn;
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

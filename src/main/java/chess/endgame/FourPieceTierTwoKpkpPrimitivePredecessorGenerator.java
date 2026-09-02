package main.java.chess.endgame;

import java.util.Arrays;

/**
 * Milestone 61.
 *
 * SAME-CLASS predecessor generator for exact EP-aware SPLIT KP-KP.
 *
 * Boundary transitions are excluded because retrograde propagation only needs
 * parents that remain KP-KP.
 *
 * Important history rule:
 *   - an EP-enabled child can only be produced by the immediately preceding
 *     two-square pawn push;
 *   - an ordinary child may have either an ordinary parent or an EP-enabled
 *     parent whose EP right expired because the mover chose another move.
 *
 * Candidate reverse geometry is therefore intentionally broader than the
 * history relation. The M60 forward generator is the final authority: every
 * candidate parent is forward-generated and retained only if it reaches the
 * exact child State, including its enPassantAvailable flag.
 */
public final class FourPieceTierTwoKpkpPrimitivePredecessorGenerator {

    private static final int[] KING_DF = {
            -1, -1, -1, 0, 0, 1, 1, 1
    };

    private static final int[] KING_DR = {
            -1, 0, 1, -1, 1, -1, 0, 1
    };

    private FourPieceTierTwoKpkpPrimitivePredecessorGenerator() {
    }

    public static final class Buffer {

        private FourPieceTierTwoKpkpPrimitiveState.State[] states;
        private int size;

        public Buffer() {
            this(32);
        }

        public Buffer(int capacity) {
            states =
                    new FourPieceTierTwoKpkpPrimitiveState.State[
                            Math.max(16, capacity)
                            ];
        }

        public void clear() {
            size = 0;
        }

        public int size() {
            return size;
        }

        public FourPieceTierTwoKpkpPrimitiveState.State state(int index) {
            return states[index];
        }

        private void addUnique(
                FourPieceTierTwoKpkpPrimitiveState.State state
        ) {

            for (int i = 0; i < size; i++) {
                if (states[i].equals(state)) {
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
            FourPieceTierTwoKpkpPrimitiveState.State child,
            FourPieceMaterialClass material,
            Buffer output,
            FourPieceTierTwoKpkpPrimitiveMoveGenerator.Buffer forwardBuffer
    ) {

        if (!FourPieceTierTwoKpkpPrimitiveMoveGenerator.supports(material)) {
            throw new IllegalArgumentException(
                    "Expected canonical SPLIT KP-KP material."
            );
        }

        if (output == null) {
            throw new IllegalArgumentException(
                    "Predecessor output buffer cannot be null."
            );
        }

        if (forwardBuffer == null) {
            throw new IllegalArgumentException(
                    "Forward-validation buffer cannot be null."
            );
        }

        output.clear();

        if (!FourPieceTierTwoKpkpPrimitiveMoveGenerator
                .isStructurallyLegal(child, material)) {
            return 0;
        }

        int wk =
                FourPieceTierTwoKpkpPrimitiveState.whiteKing(child);

        int bk =
                FourPieceTierTwoKpkpPrimitiveState.blackKing(child);

        int whitePawn =
                FourPieceTierTwoKpkpPrimitiveState.whitePawn(child);

        int blackPawn =
                FourPieceTierTwoKpkpPrimitiveState.blackPawn(child);

        boolean childBlackToMove =
                FourPieceTierTwoKpkpPrimitiveState.blackToMove(child);

        /*
         * child BLACK to move => WHITE moved immediately before child.
         * child WHITE to move => BLACK moved immediately before child.
         */
        boolean previousMoverWhite =
                childBlackToMove;

        boolean parentBlackToMove =
                !childBlackToMove;

        int previousMoverKing =
                previousMoverWhite
                        ? wk
                        : bk;

        /*
         * An EP-enabled child is created only by a double pawn push. A king
         * move can never preserve/create an EP right.
         */
        if (!child.enPassantAvailable()) {
            reverseKing(
                    child,
                    wk,
                    bk,
                    whitePawn,
                    blackPawn,
                    previousMoverKing,
                    parentBlackToMove,
                    material,
                    output,
                    forwardBuffer
            );
        }

        int movedPawn =
                previousMoverWhite
                        ? whitePawn
                        : blackPawn;

        reversePawnPush(
                child,
                wk,
                bk,
                whitePawn,
                blackPawn,
                movedPawn,
                previousMoverWhite,
                parentBlackToMove,
                material,
                output,
                forwardBuffer
        );

        return output.size();
    }

    public static int generatePredecessors(
            FourPieceTierTwoKpkpPrimitiveState.State child,
            FourPieceMaterialClass material,
            Buffer output
    ) {

        return generatePredecessors(
                child,
                material,
                output,
                new FourPieceTierTwoKpkpPrimitiveMoveGenerator.Buffer(32)
        );
    }

    private static void reverseKing(
            FourPieceTierTwoKpkpPrimitiveState.State child,
            int wk,
            int bk,
            int whitePawn,
            int blackPawn,
            int currentKingSquare,
            boolean parentBlackToMove,
            FourPieceMaterialClass material,
            Buffer output,
            FourPieceTierTwoKpkpPrimitiveMoveGenerator.Buffer forwardBuffer
    ) {

        int file =
                currentKingSquare & 7;

        int rank =
                currentKingSquare >>> 3;

        for (int i = 0; i < KING_DF.length; i++) {

            int previousFile =
                    file + KING_DF[i];

            int previousRank =
                    rank + KING_DR[i];

            if (!inside(previousFile, previousRank)) {
                continue;
            }

            int previousSquare =
                    previousRank * 8 + previousFile;

            if (occupied(
                    previousSquare,
                    wk,
                    bk,
                    whitePawn,
                    blackPawn
            )) {
                continue;
            }

            int parentWk =
                    wk;

            int parentBk =
                    bk;

            if (currentKingSquare == wk) {
                parentWk = previousSquare;
            } else if (currentKingSquare == bk) {
                parentBk = previousSquare;
            } else {
                throw new IllegalStateException(
                        "Reverse KP-KP king source is not a king."
                );
            }

            validateBoardCandidate(
                    child,
                    parentWk,
                    parentBk,
                    whitePawn,
                    blackPawn,
                    parentBlackToMove,
                    material,
                    output,
                    forwardBuffer
            );
        }
    }

    private static void reversePawnPush(
            FourPieceTierTwoKpkpPrimitiveState.State child,
            int wk,
            int bk,
            int whitePawn,
            int blackPawn,
            int currentPawn,
            boolean movedPawnIsWhite,
            boolean parentBlackToMove,
            FourPieceMaterialClass material,
            Buffer output,
            FourPieceTierTwoKpkpPrimitiveMoveGenerator.Buffer forwardBuffer
    ) {

        int file =
                currentPawn & 7;

        int rank =
                currentPawn >>> 3;

        int direction =
                movedPawnIsWhite
                        ? 1
                        : -1;

        /*
         * An EP-enabled child cannot have resulted from a one-square push.
         */
        if (!child.enPassantAvailable()) {

            int onePreviousRank =
                    rank - direction;

            if (onePreviousRank > 0
                    && onePreviousRank < 7) {

                int previousPawn =
                        onePreviousRank * 8 + file;

                if (!occupied(
                        previousPawn,
                        wk,
                        bk,
                        whitePawn,
                        blackPawn
                )) {

                    validatePawnBoardCandidate(
                            child,
                            wk,
                            bk,
                            whitePawn,
                            blackPawn,
                            previousPawn,
                            movedPawnIsWhite,
                            parentBlackToMove,
                            material,
                            output,
                            forwardBuffer
                    );
                }
            }
        }

        /*
         * Reverse a legal starting-rank double push:
         *
         * WHITE: rank 2 -> rank 4
         * BLACK: rank 7 -> rank 5
         *
         * This is the only reverse pawn geometry capable of producing an
         * EP-enabled child.
         */
        int doubleDestinationRank =
                movedPawnIsWhite
                        ? 3
                        : 4;

        int startRank =
                movedPawnIsWhite
                        ? 1
                        : 6;

        if (rank == doubleDestinationRank) {

            int previousPawn =
                    startRank * 8 + file;

            int intermediate =
                    (startRank + direction) * 8 + file;

            if (!occupied(
                    previousPawn,
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

                validatePawnBoardCandidate(
                        child,
                        wk,
                        bk,
                        whitePawn,
                        blackPawn,
                        previousPawn,
                        movedPawnIsWhite,
                        parentBlackToMove,
                        material,
                        output,
                        forwardBuffer
                );
            }
        }

        /*
         * Reverse pawn captures are excluded: any pawn capture removes the
         * opposing pawn and leaves KP-KP through a KPK boundary.
         *
         * Reverse promotions are likewise external Tier-1 boundaries.
         */
    }

    private static void validatePawnBoardCandidate(
            FourPieceTierTwoKpkpPrimitiveState.State child,
            int wk,
            int bk,
            int whitePawn,
            int blackPawn,
            int previousPawn,
            boolean movedPawnIsWhite,
            boolean parentBlackToMove,
            FourPieceMaterialClass material,
            Buffer output,
            FourPieceTierTwoKpkpPrimitiveMoveGenerator.Buffer forwardBuffer
    ) {

        int parentWhitePawn =
                movedPawnIsWhite
                        ? previousPawn
                        : whitePawn;

        int parentBlackPawn =
                movedPawnIsWhite
                        ? blackPawn
                        : previousPawn;

        validateBoardCandidate(
                child,
                wk,
                bk,
                parentWhitePawn,
                parentBlackPawn,
                parentBlackToMove,
                material,
                output,
                forwardBuffer
        );
    }

    /**
     * A board geometry can represent two distinct exact parents when an EP
     * capture is geometrically available: history says either the right exists
     * or it does not. Test both variants and let M60 forward generation decide.
     */
    private static void validateBoardCandidate(
            FourPieceTierTwoKpkpPrimitiveState.State child,
            int wk,
            int bk,
            int whitePawn,
            int blackPawn,
            boolean parentBlackToMove,
            FourPieceMaterialClass material,
            Buffer output,
            FourPieceTierTwoKpkpPrimitiveMoveGenerator.Buffer forwardBuffer
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
                base,
                false,
                material,
                output,
                forwardBuffer
        );

        if (FourPieceTierTwoKpkpPrimitiveState
                .canCarryEnPassant(base)) {

            validateHistoryCandidate(
                    child,
                    base,
                    true,
                    material,
                    output,
                    forwardBuffer
            );
        }
    }

    private static void validateHistoryCandidate(
            FourPieceTierTwoKpkpPrimitiveState.State child,
            int base,
            boolean enPassantAvailable,
            FourPieceMaterialClass material,
            Buffer output,
            FourPieceTierTwoKpkpPrimitiveMoveGenerator.Buffer forwardBuffer
    ) {

        FourPieceTierTwoKpkpPrimitiveState.State parent =
                FourPieceTierTwoKpkpPrimitiveState.of(
                        base,
                        enPassantAvailable
                );

        if (!FourPieceTierTwoKpkpPrimitiveMoveGenerator
                .isStructurallyLegal(
                        parent,
                        material
                )) {
            return;
        }

        int count =
                FourPieceTierTwoKpkpPrimitiveMoveGenerator
                        .generateLegalSuccessors(
                                parent,
                                material,
                                forwardBuffer
                        );

        for (int i = 0; i < count; i++) {

            if (forwardBuffer.boundaryType(i)
                    != FourPieceTierTwoKpkpPrimitiveMoveGenerator
                    .BOUNDARY_NONE) {
                continue;
            }

            if (child.equals(
                    forwardBuffer.state(i)
            )) {

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

package main.java.chess.endgame;

import main.java.chess.model.Color;
import main.java.chess.model.PieceType;

import java.util.Arrays;


/**
 * Primitive exact WDL/DTM retrograde builder for KQRK.
 *
 * Hot path:
 *   - packed int states
 *   - primitive legality
 *   - primitive legal successors
 *   - primitive predecessor generation
 *   - primitive arrays
 *
 * Existing 3-piece KQK/KRK assets are consulted only at capture boundaries.
 *
 * WDL:
 *   WIN  if any child is LOSS
 *   LOSS if all children are WIN
 *   unresolved at fixpoint = DRAW
 *
 * DTM:
 *   WIN  = 1 + minimum LOSS-child DTM
 *   LOSS = 1 + maximum WIN-child DTM
 *
 * The solved frontier is processed by increasing DTM with a primitive heap,
 * so the first LOSS child proving a WIN gives its exact minimum DTM.
 */
public final class KqrkPrimitiveRetrogradeBuilder {

    private static final byte INVALID = FourPieceTablebase.INVALID;
    private static final byte LOSS = FourPieceTablebase.LOSS;
    private static final byte UNKNOWN = FourPieceTablebase.UNKNOWN;
    private static final byte WIN = FourPieceTablebase.WIN;
    private static final byte DRAW = FourPieceTablebase.DRAW;

    private static final int PROGRESS = 1_000_000;

    private final boolean strongIsWhite;
    private final Color strongColor;
    private final ThreePieceTablebaseService threePieceService;

    private final ThreePieceTablebase kqk;
    private final ThreePieceTablebase krk;

    private final KqrkPrimitiveMoveGenerator.Buffer successors =
            new KqrkPrimitiveMoveGenerator.Buffer(64);

    private final KqrkPrimitivePredecessorGenerator.Buffer predecessors =
            new KqrkPrimitivePredecessorGenerator.Buffer(64);


    public KqrkPrimitiveRetrogradeBuilder(
            Color strongColor
    ) {
        this.strongColor = strongColor;
        this.strongIsWhite = strongColor == Color.WHITE;

        this.threePieceService =
                new ThreePieceTablebaseService();

        this.kqk =
                threePieceService.get(
                        PieceType.QUEEN,
                        strongColor
                );

        this.krk =
                threePieceService.get(
                        PieceType.ROOK,
                        strongColor
                );
    }


    public Result build() {

        final int count =
                KqrkPrimitiveState.STATE_COUNT;

        long started =
                System.currentTimeMillis();

        System.out.println(
                "Primitive KQRK retrograde — strong "
                        + strongColor
        );
        System.out.println(
                "Raw states: " + count
        );

        byte[] outcome = new byte[count];
        short[] distance = new short[count];

        /*
         * Number of children not yet proven WIN.
         * Draw boundary children deliberately remain in this count forever.
         */
        byte[] remaining = new byte[count];

        /*
         * Maximum DTM among already-known WIN children.
         */
        short[] maxWinChildDistance = new short[count];

        Arrays.fill(outcome, INVALID);
        Arrays.fill(distance, (short) -1);
        Arrays.fill(maxWinChildDistance, (short) -1);

        IntDistanceHeap frontier =
                new IntDistanceHeap(
                        1_000_000,
                        distance
                );

        long legalStates = 0;
        long terminalMates = 0;
        long terminalStalemates = 0;
        long boundaryWinSeeds = 0;
        long boundaryLossSeeds = 0;

        long classificationStarted =
                System.currentTimeMillis();

        // =====================================================
        // INITIAL CLASSIFICATION
        // =====================================================

        for (int state = 0; state < count; state++) {

            if (state > 0 && state % PROGRESS == 0) {
                long now = System.currentTimeMillis();
                System.out.println(
                        "  classified "
                                + state
                                + " / "
                                + count
                                + "  ("
                                + (now - classificationStarted)
                                + " ms)"
                );
            }

            if (!KqrkPrimitiveRules.isStructurallyLegal(
                    state,
                    strongIsWhite
            )) {
                continue;
            }

            legalStates++;
            outcome[state] = UNKNOWN;

            int moveCount =
                    KqrkPrimitiveMoveGenerator.generateLegalSuccessors(
                            state,
                            strongIsWhite,
                            successors
                    );

            if (moveCount == 0) {
                if (KqrkPrimitiveRules.sideToMoveIsInCheck(
                        state,
                        strongIsWhite
                )) {
                    solve(
                            state,
                            LOSS,
                            0,
                            outcome,
                            distance,
                            frontier
                    );
                    terminalMates++;
                } else {
                    outcome[state] = DRAW;
                    terminalStalemates++;
                }

                continue;
            }

            int unresolvedInClass = 0;
            int drawBoundaries = 0;
            int knownWinChildren = 0;

            int bestLossChildDistance =
                    Integer.MAX_VALUE;

            int maximumWinDistance = -1;

            for (int i = 0; i < moveCount; i++) {
                int boundary =
                        successors.boundaryType(i);

                if (boundary
                        == KqrkPrimitiveMoveGenerator.BOUNDARY_NONE) {
                    unresolvedInClass++;
                    continue;
                }

                BoundaryProbe probe =
                        probeBoundary(
                                state,
                                i,
                                boundary
                        );

                if (probe.outcome == LOSS) {
                    bestLossChildDistance =
                            Math.min(
                                    bestLossChildDistance,
                                    probe.distance
                            );
                } else if (probe.outcome == WIN) {
                    knownWinChildren++;
                    maximumWinDistance =
                            Math.max(
                                    maximumWinDistance,
                                    probe.distance
                            );
                } else if (probe.outcome == DRAW) {
                    drawBoundaries++;
                } else {
                    throw new IllegalStateException(
                            "Unsupported KQRK boundary transition."
                    );
                }
            }

            if (bestLossChildDistance
                    != Integer.MAX_VALUE) {
                solve(
                        state,
                        WIN,
                        bestLossChildDistance + 1,
                        outcome,
                        distance,
                        frontier
                );
                boundaryWinSeeds++;
                continue;
            }

            int unresolved =
                    unresolvedInClass + drawBoundaries;

            if (unresolved > 255) {
                throw new IllegalStateException(
                        "KQRK legal move count exceeds byte counter."
                );
            }

            remaining[state] = (byte) unresolved;

            if (maximumWinDistance >= 0) {
                maxWinChildDistance[state] =
                        checkedShort(maximumWinDistance);
            }

            if (unresolvedInClass == 0
                    && drawBoundaries == 0
                    && knownWinChildren == moveCount) {
                solve(
                        state,
                        LOSS,
                        maximumWinDistance + 1,
                        outcome,
                        distance,
                        frontier
                );
                boundaryLossSeeds++;
            }
        }

        long classificationMillis =
                System.currentTimeMillis()
                        - classificationStarted;

        System.out.println();
        System.out.println(
                "Initial classification complete."
        );
        System.out.println(
                "Legal states: " + legalStates
        );
        System.out.println(
                "Terminal mates: " + terminalMates
        );
        System.out.println(
                "Terminal stalemates: " + terminalStalemates
        );
        System.out.println(
                "Boundary WIN seeds: " + boundaryWinSeeds
        );
        System.out.println(
                "Boundary LOSS seeds: " + boundaryLossSeeds
        );
        System.out.println(
                "Initial frontier: " + frontier.size()
        );
        System.out.println(
                "Classification ms: " + classificationMillis
        );

        // =====================================================
        // RETROGRADE FRONTIER
        // =====================================================

        long propagationStarted =
                System.currentTimeMillis();

        long processed = 0;
        long predecessorEdges = 0;

        while (!frontier.isEmpty()) {

            int child =
                    frontier.remove();

            byte childOutcome =
                    outcome[child];

            int childDistance =
                    distance[child];

            int predecessorCount =
                    KqrkPrimitivePredecessorGenerator
                            .generateLegalPredecessors(
                                    child,
                                    strongIsWhite,
                                    predecessors
                            );

            predecessorEdges += predecessorCount;

            for (int i = 0;
                 i < predecessorCount;
                 i++) {

                int parent =
                        predecessors.state(i);

                if (outcome[parent] != UNKNOWN) {
                    continue;
                }

                if (childOutcome == LOSS) {
                    /*
                     * Distance-ordered frontier guarantees that this is the
                     * minimum losing-child DTM for the parent.
                     */
                    solve(
                            parent,
                            WIN,
                            childDistance + 1,
                            outcome,
                            distance,
                            frontier
                    );

                } else if (childOutcome == WIN) {

                    int left =
                            remaining[parent] & 0xFF;

                    if (left == 0) {
                        continue;
                    }

                    left--;
                    remaining[parent] = (byte) left;

                    if (childDistance
                            > maxWinChildDistance[parent]) {
                        maxWinChildDistance[parent] =
                                checkedShort(childDistance);
                    }

                    if (left == 0) {
                        int maxChild =
                                maxWinChildDistance[parent];

                        solve(
                                parent,
                                LOSS,
                                maxChild + 1,
                                outcome,
                                distance,
                                frontier
                        );
                    }
                }
            }

            processed++;

            if (processed % PROGRESS == 0) {
                long now = System.currentTimeMillis();

                System.out.println(
                        "  propagated "
                                + processed
                                + " states; frontier "
                                + frontier.size()
                                + "; predecessor edges "
                                + predecessorEdges
                                + "  ("
                                + (now - propagationStarted)
                                + " ms)"
                );
            }
        }

        long propagationMillis =
                System.currentTimeMillis()
                        - propagationStarted;

        // =====================================================
        // UNRESOLVED = DRAW
        // =====================================================

        long wins = 0;
        long losses = 0;
        long draws = 0;

        for (int state = 0; state < count; state++) {
            if (outcome[state] == UNKNOWN) {
                outcome[state] = DRAW;
                distance[state] = -1;
            }

            if (outcome[state] == WIN) {
                wins++;
            } else if (outcome[state] == LOSS) {
                losses++;
            } else if (outcome[state] == DRAW) {
                draws++;
            }
        }

        long totalMillis =
                System.currentTimeMillis() - started;

        return new Result(
                outcome,
                distance,
                legalStates,
                wins,
                losses,
                draws,
                processed,
                predecessorEdges,
                classificationMillis,
                propagationMillis,
                totalMillis
        );
    }


    private BoundaryProbe probeBoundary(
            int parentState,
            int successorIndex,
            int boundaryType
    ) {
        int surviving =
                successors.survivingPieceSquare(
                        successorIndex
                );

        /*
         * Boundary child side to move is opposite the KQRK parent.
         */
        boolean childBlackToMove =
                !KqrkPrimitiveState.blackToMove(
                        parentState
                );

        int wk =
                KqrkPrimitiveState.whiteKing(parentState);

        int bk =
                KqrkPrimitiveState.blackKing(parentState);

        /*
         * The weak king made the capture, so place it on the captured
         * queen/rook square.  The generator does not currently expose that
         * square directly, but in a KQRK boundary the weak king's child
         * square is the parent's queen or rook square.
         */
        if (boundaryType
                == KqrkPrimitiveMoveGenerator.BOUNDARY_KRK) {
            if (strongIsWhite) {
                bk = KqrkPrimitiveState.queen(parentState);
            } else {
                wk = KqrkPrimitiveState.queen(parentState);
            }

            return probeThreePiece(
                    krk,
                    wk,
                    bk,
                    surviving,
                    childBlackToMove
            );
        }

        if (boundaryType
                == KqrkPrimitiveMoveGenerator.BOUNDARY_KQK) {
            if (strongIsWhite) {
                bk = KqrkPrimitiveState.rook(parentState);
            } else {
                wk = KqrkPrimitiveState.rook(parentState);
            }

            return probeThreePiece(
                    kqk,
                    wk,
                    bk,
                    surviving,
                    childBlackToMove
            );
        }

        if (boundaryType
                == KqrkPrimitiveMoveGenerator.BOUNDARY_KK) {
            return new BoundaryProbe(
                    DRAW,
                    -1
            );
        }

        throw new IllegalStateException(
                "Unknown boundary type: " + boundaryType
        );
    }


    private BoundaryProbe probeThreePiece(
            ThreePieceTablebase tablebase,
            int wk,
            int bk,
            int pieceSquare,
            boolean blackToMove
    ) {
        /*
         * Construct the 3-piece state through its public primitive-compatible
         * probe adapter.  This adapter is added to ThreePieceTablebase below.
         */
        ThreePieceTablebase.Probe probe =
                tablebase.probeSquares(
                        wk,
                        bk,
                        pieceSquare,
                        blackToMove
                );

        byte mapped;

        switch (probe.outcome()) {
            case WIN -> mapped = WIN;
            case LOSS -> mapped = LOSS;
            case DRAW -> mapped = DRAW;
            default -> throw new IllegalStateException(
                    "Exact 3-piece boundary returned UNSUPPORTED."
            );
        }

        return new BoundaryProbe(
                mapped,
                probe.mateDistance()
        );
    }


    private void solve(
            int state,
            byte solvedOutcome,
            int solvedDistance,
            byte[] outcome,
            short[] distance,
            IntDistanceHeap frontier
    ) {
        if (outcome[state] == WIN
                || outcome[state] == LOSS) {
            return;
        }

        outcome[state] = solvedOutcome;
        distance[state] =
                checkedShort(solvedDistance);

        frontier.add(state);
    }


    private short checkedShort(int value) {
        if (value < 0
                || value > Short.MAX_VALUE) {
            throw new IllegalStateException(
                    "KQRK DTM exceeds short range: "
                            + value
            );
        }

        return (short) value;
    }


    private record BoundaryProbe(
            byte outcome,
            int distance
    ) {
    }


    public record Result(
            byte[] outcome,
            short[] distance,
            long legalStates,
            long wins,
            long losses,
            long draws,
            long propagatedStates,
            long predecessorEdges,
            long classificationMillis,
            long propagationMillis,
            long totalMillis
    ) {
    }


    /**
     * Primitive min-heap ordered by solved DTM, then state id.
     */
    private static final class IntDistanceHeap {

        private int[] heap;
        private int size;
        private final short[] distance;

        private IntDistanceHeap(
                int initialCapacity,
                short[] distance
        ) {
            heap = new int[Math.max(16, initialCapacity)];
            this.distance = distance;
        }

        private void add(int state) {
            ensureCapacity();

            int i = size++;
            heap[i] = state;

            while (i > 0) {
                int p = (i - 1) >>> 1;

                if (!less(heap[i], heap[p])) {
                    break;
                }

                int temp = heap[i];
                heap[i] = heap[p];
                heap[p] = temp;
                i = p;
            }
        }

        private int remove() {
            if (size == 0) {
                throw new IllegalStateException(
                        "Empty retrograde frontier."
                );
            }

            int result = heap[0];
            size--;

            if (size > 0) {
                heap[0] = heap[size];

                int i = 0;

                while (true) {
                    int left = i * 2 + 1;

                    if (left >= size) {
                        break;
                    }

                    int right = left + 1;
                    int best = left;

                    if (right < size
                            && less(heap[right], heap[left])) {
                        best = right;
                    }

                    if (!less(heap[best], heap[i])) {
                        break;
                    }

                    int temp = heap[i];
                    heap[i] = heap[best];
                    heap[best] = temp;
                    i = best;
                }
            }

            return result;
        }

        private boolean less(
                int a,
                int b
        ) {
            int da = distance[a];
            int db = distance[b];

            if (da != db) {
                return da < db;
            }

            return a < b;
        }

        private void ensureCapacity() {
            if (size < heap.length) {
                return;
            }

            heap = Arrays.copyOf(
                    heap,
                    heap.length * 2
            );
        }

        private boolean isEmpty() {
            return size == 0;
        }

        private int size() {
            return size;
        }
    }
}

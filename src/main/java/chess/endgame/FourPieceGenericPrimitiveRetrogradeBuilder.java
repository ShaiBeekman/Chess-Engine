package main.java.chess.endgame;

import main.java.chess.model.Color;
import main.java.chess.model.PieceType;

import java.util.Arrays;


/**
 * Exact generic Tier-0 four-piece WDL/DTM retrograde builder.
 *
 * Supported four-piece material:
 *
 *     pawnless canonical classes built from
 *
 *     QUEEN
 *     ROOK
 *     BISHOP
 *     KNIGHT
 *
 *
 * SAME_SIDE ownership:
 *
 *     sameSideOwnerIsWhite determines the owner of both extras.
 *
 * SPLIT ownership:
 *
 *     first extra  = White
 *     second extra = Black
 *
 *
 * The hot path is fully primitive:
 *
 *     packed int states
 *     primitive legality
 *     primitive successor generation
 *     primitive predecessor generation
 *     primitive result arrays
 *
 *
 * Out-of-class captures reduce to exact three-piece positions.
 *
 * Boundary handling:
 *
 *     KQK -> existing exact 3-piece tablebase
 *     KRK -> existing exact 3-piece tablebase
 *     KBK -> exact DRAW
 *     KNK -> exact DRAW
 *
 *
 * WDL:
 *
 *     WIN  if any legal child is LOSS
 *     LOSS if every legal child is WIN
 *     otherwise DRAW
 *
 *
 * DTM:
 *
 *     WIN  = 1 + minimum LOSS-child DTM
 *     LOSS = 1 + maximum WIN-child DTM
 *
 * The frontier is ordered by solved DTM, so the first LOSS child
 * encountered for an unresolved parent gives the exact minimum DTM
 * needed for a WIN.
 */
public final class FourPieceGenericPrimitiveRetrogradeBuilder {

    private static final byte INVALID =
            FourPieceTablebase.INVALID;

    private static final byte LOSS =
            FourPieceTablebase.LOSS;

    private static final byte UNKNOWN =
            FourPieceTablebase.UNKNOWN;

    private static final byte WIN =
            FourPieceTablebase.WIN;

    private static final byte DRAW =
            FourPieceTablebase.DRAW;


    private static final int PROGRESS =
            1_000_000;


    private final FourPieceMaterialClass material;

    private final boolean sameSideOwnerIsWhite;

    private final ThreePieceTablebaseService
            threePieceService;


    private final FourPieceGenericPrimitiveMoveGenerator.Buffer
            successors =
            new FourPieceGenericPrimitiveMoveGenerator.Buffer(
                    64
            );


    private final FourPieceGenericPrimitivePredecessorGenerator.Buffer
            predecessors =
            new FourPieceGenericPrimitivePredecessorGenerator.Buffer(
                    64
            );


    public FourPieceGenericPrimitiveRetrogradeBuilder(
            FourPieceMaterialClass material,
            boolean sameSideOwnerIsWhite
    ) {

        if (material == null) {

            throw new IllegalArgumentException(
                    "Material cannot be null."
            );
        }


        if (material.buildTier()
                != 0) {

            throw new IllegalArgumentException(
                    "Generic primitive retrograde currently supports only Tier-0 pawnless material: "
                            + material.displayName()
            );
        }


        this.material =
                material;

        this.sameSideOwnerIsWhite =
                sameSideOwnerIsWhite;

        this.threePieceService =
                new ThreePieceTablebaseService();
    }


    public Result build() {

        final int count =
                FourPieceGenericPrimitiveState.STATE_COUNT;


        long started =
                System.currentTimeMillis();


        System.out.println();

        System.out.println(
                "Generic Tier-0 primitive retrograde"
        );

        System.out.println(
                "================================="
        );

        System.out.println(
                "Material: "
                        + material.displayName()
        );

        System.out.println(
                "Distribution: "
                        + material.distribution()
        );


        if (material.distribution()
                == FourPieceMaterialClass.Distribution.SAME_SIDE) {

            System.out.println(
                    "Extra-piece owner: "
                            + (sameSideOwnerIsWhite
                            ? "WHITE"
                            : "BLACK")
            );

        } else {

            System.out.println(
                    "Split orientation: first=WHITE, second=BLACK"
            );
        }


        System.out.println(
                "Raw states: "
                        + String.format(
                        "%,d",
                        count
                )
        );


        byte[] outcome =
                new byte[count];

        short[] distance =
                new short[count];


        /*
         * Number of legal children not yet proven WIN.
         *
         * Exact DRAW boundary children deliberately remain in this count
         * forever. Therefore a parent with any reachable draw cannot
         * accidentally become a LOSS merely because all in-class children
         * were proven WIN.
         */
        byte[] remaining =
                new byte[count];


        /*
         * Maximum DTM among already-known WIN children.
         *
         * Required when all children become WIN and the parent is therefore
         * LOSS: optimal defense chooses the longest mate.
         */
        short[] maximumWinChildDistance =
                new short[count];


        Arrays.fill(
                outcome,
                INVALID
        );

        Arrays.fill(
                distance,
                (short) -1
        );

        Arrays.fill(
                maximumWinChildDistance,
                (short) -1
        );


        IntDistanceHeap frontier =
                new IntDistanceHeap(
                        1_000_000,
                        distance
                );


        long legalStates =
                0;

        long terminalMates =
                0;

        long terminalStalemates =
                0;

        long boundaryWinSeeds =
                0;

        long boundaryLossSeeds =
                0;

        long boundaryDrawChildren =
                0;

        long boundaryChildren =
                0;


        long classificationStarted =
                System.currentTimeMillis();


        // =====================================================
        // INITIAL CLASSIFICATION
        // =====================================================

        for (int state = 0;
             state < count;
             state++) {

            if (state > 0
                    && state % PROGRESS == 0) {

                long now =
                        System.currentTimeMillis();


                System.out.println(
                        "  classified "
                                + String.format(
                                "%,d",
                                state
                        )
                                + " / "
                                + String.format(
                                "%,d",
                                count
                        )
                                + "  ("
                                + (now - classificationStarted)
                                + " ms)"
                );
            }


            if (!FourPieceGenericPrimitiveRules
                    .isStructurallyLegal(
                            state,
                            material,
                            sameSideOwnerIsWhite
                    )) {

                continue;
            }


            legalStates++;

            outcome[state] =
                    UNKNOWN;


            int moveCount =
                    FourPieceGenericPrimitiveMoveGenerator
                            .generateLegalSuccessors(
                                    state,
                                    material,
                                    sameSideOwnerIsWhite,
                                    successors
                            );


            /*
             * No legal moves:
             *
             *     in check     -> checkmate -> LOSS
             *     not in check -> stalemate -> DRAW
             */
            if (moveCount == 0) {

                if (FourPieceGenericPrimitiveRules
                        .sideToMoveIsInCheck(
                                state,
                                material,
                                sameSideOwnerIsWhite
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

                    outcome[state] =
                            DRAW;

                    terminalStalemates++;
                }


                continue;
            }


            int unresolvedInClass =
                    0;

            int drawBoundaries =
                    0;

            int knownWinChildren =
                    0;


            int bestLossChildDistance =
                    Integer.MAX_VALUE;


            int maximumWinDistance =
                    -1;


            for (int i = 0;
                 i < moveCount;
                 i++) {

                if (!successors.isBoundary(
                        i
                )) {

                    unresolvedInClass++;

                    continue;
                }


                boundaryChildren++;


                BoundaryProbe probe =
                        probeBoundary(
                                i
                        );


                if (probe.outcome()
                        == LOSS) {

                    bestLossChildDistance =
                            Math.min(
                                    bestLossChildDistance,
                                    probe.distance()
                            );

                } else if (probe.outcome()
                        == WIN) {

                    knownWinChildren++;

                    maximumWinDistance =
                            Math.max(
                                    maximumWinDistance,
                                    probe.distance()
                            );

                } else if (probe.outcome()
                        == DRAW) {

                    drawBoundaries++;

                    boundaryDrawChildren++;

                } else {

                    throw new IllegalStateException(
                            "Unsupported three-piece boundary from "
                                    + material.displayName()
                    );
                }
            }


            /*
             * Any LOSS child immediately proves this parent WIN.
             */
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


            /*
             * remaining counts:
             *
             *     unresolved in-class children
             *     +
             *     known DRAW boundary children
             *
             * Known WIN boundaries are already removed from consideration.
             */
            int unresolved =
                    unresolvedInClass
                            + drawBoundaries;


            if (unresolved > 255) {

                throw new IllegalStateException(
                        material.displayName()
                                + " legal move count exceeds byte counter: "
                                + unresolved
                );
            }


            remaining[state] =
                    (byte) unresolved;


            if (maximumWinDistance
                    >= 0) {

                maximumWinChildDistance[state] =
                        checkedShort(
                                maximumWinDistance
                        );
            }


            /*
             * Every child was already known WIN.
             *
             * Therefore this state is immediately LOSS.
             */
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
                "Legal states: "
                        + String.format(
                        "%,d",
                        legalStates
                )
        );

        System.out.println(
                "Terminal mates: "
                        + String.format(
                        "%,d",
                        terminalMates
                )
        );

        System.out.println(
                "Terminal stalemates: "
                        + String.format(
                        "%,d",
                        terminalStalemates
                )
        );

        System.out.println(
                "Boundary children: "
                        + String.format(
                        "%,d",
                        boundaryChildren
                )
        );

        System.out.println(
                "Boundary DRAW children: "
                        + String.format(
                        "%,d",
                        boundaryDrawChildren
                )
        );

        System.out.println(
                "Boundary WIN seeds: "
                        + String.format(
                        "%,d",
                        boundaryWinSeeds
                )
        );

        System.out.println(
                "Boundary LOSS seeds: "
                        + String.format(
                        "%,d",
                        boundaryLossSeeds
                )
        );

        System.out.println(
                "Initial frontier: "
                        + String.format(
                        "%,d",
                        frontier.size()
                )
        );

        System.out.println(
                "Classification ms: "
                        + classificationMillis
        );


        // =====================================================
        // RETROGRADE FRONTIER
        // =====================================================

        long propagationStarted =
                System.currentTimeMillis();


        long processed =
                0;

        long predecessorEdges =
                0;


        while (!frontier.isEmpty()) {

            int child =
                    frontier.remove();


            byte childOutcome =
                    outcome[child];


            int childDistance =
                    distance[child];


            int predecessorCount =
                    FourPieceGenericPrimitivePredecessorGenerator
                            .generateLegalPredecessors(
                                    child,
                                    material,
                                    sameSideOwnerIsWhite,
                                    predecessors
                            );


            predecessorEdges +=
                    predecessorCount;


            for (int i = 0;
                 i < predecessorCount;
                 i++) {

                int parent =
                        predecessors.state(
                                i
                        );


                if (outcome[parent]
                        != UNKNOWN) {

                    continue;
                }


                /*
                 * One LOSS child is sufficient to prove parent WIN.
                 */
                if (childOutcome
                        == LOSS) {

                    /*
                     * The DTM-ordered frontier guarantees this is the
                     * minimum-distance LOSS child that can prove the parent.
                     */
                    solve(
                            parent,
                            WIN,
                            childDistance + 1,
                            outcome,
                            distance,
                            frontier
                    );

                    continue;
                }


                /*
                 * A WIN child removes one unresolved route from the parent.
                 */
                if (childOutcome
                        == WIN) {

                    int left =
                            remaining[parent]
                                    & 0xFF;


                    if (left == 0) {

                        continue;
                    }


                    left--;


                    remaining[parent] =
                            (byte) left;


                    if (childDistance
                            > maximumWinChildDistance[parent]) {

                        maximumWinChildDistance[parent] =
                                checkedShort(
                                        childDistance
                                );
                    }


                    /*
                     * If no unresolved/draw child remains, every legal child
                     * is WIN. Therefore parent is LOSS.
                     */
                    if (left == 0) {

                        int maximumChild =
                                maximumWinChildDistance[parent];


                        solve(
                                parent,
                                LOSS,
                                maximumChild + 1,
                                outcome,
                                distance,
                                frontier
                        );
                    }
                }
            }


            processed++;


            if (processed % PROGRESS
                    == 0) {

                long now =
                        System.currentTimeMillis();


                System.out.println(
                        "  propagated "
                                + String.format(
                                "%,d",
                                processed
                        )
                                + " states; frontier "
                                + String.format(
                                "%,d",
                                frontier.size()
                        )
                                + "; predecessor edges "
                                + String.format(
                                "%,d",
                                predecessorEdges
                        )
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
        // FIXPOINT: UNRESOLVED = DRAW
        // =====================================================

        long wins =
                0;

        long losses =
                0;

        long draws =
                0;


        int maximumDistance =
                -1;


        for (int state = 0;
             state < count;
             state++) {

            if (outcome[state]
                    == UNKNOWN) {

                outcome[state] =
                        DRAW;

                distance[state] =
                        -1;
            }


            if (outcome[state]
                    == WIN) {

                wins++;

                maximumDistance =
                        Math.max(
                                maximumDistance,
                                distance[state]
                        );

            } else if (outcome[state]
                    == LOSS) {

                losses++;

                maximumDistance =
                        Math.max(
                                maximumDistance,
                                distance[state]
                        );

            } else if (outcome[state]
                    == DRAW) {

                draws++;
            }
        }


        long totalMillis =
                System.currentTimeMillis()
                        - started;


        System.out.println();

        System.out.println(
                "Generic retrograde complete."
        );

        System.out.println(
                "WIN: "
                        + String.format(
                        "%,d",
                        wins
                )
        );

        System.out.println(
                "LOSS: "
                        + String.format(
                        "%,d",
                        losses
                )
        );

        System.out.println(
                "DRAW: "
                        + String.format(
                        "%,d",
                        draws
                )
        );

        System.out.println(
                "Maximum DTM: "
                        + maximumDistance
        );

        System.out.println(
                "Propagated states: "
                        + String.format(
                        "%,d",
                        processed
                )
        );

        System.out.println(
                "Predecessor edges: "
                        + String.format(
                        "%,d",
                        predecessorEdges
                )
        );

        System.out.println(
                "Propagation ms: "
                        + propagationMillis
        );

        System.out.println(
                "Total ms: "
                        + totalMillis
        );


        return new Result(
                material,
                sameSideOwnerIsWhite,
                outcome,
                distance,
                legalStates,
                wins,
                losses,
                draws,
                maximumDistance,
                processed,
                predecessorEdges,
                classificationMillis,
                propagationMillis,
                totalMillis
        );
    }


    /**
     * Probe the exact three-piece child represented by one boundary entry.
     */
    private BoundaryProbe probeBoundary(
            int successorIndex
    ) {

        PieceType survivingType =
                successors.survivingPieceType(
                        successorIndex
                );


        int whiteKing =
                successors.boundaryWhiteKing(
                        successorIndex
                );

        int blackKing =
                successors.boundaryBlackKing(
                        successorIndex
                );

        int survivingSquare =
                successors.survivingPieceSquare(
                        successorIndex
                );

        boolean survivingWhite =
                successors.survivingPieceIsWhite(
                        successorIndex
                );

        boolean blackToMove =
                successors.boundaryBlackToMove(
                        successorIndex
                );


        /*
         * K+B vs K and K+N vs K cannot force mate.
         *
         * These are exact draws, so no 3-piece asset is required.
         */
        if (survivingType
                == PieceType.BISHOP
                ||
                survivingType
                        == PieceType.KNIGHT) {

            return new BoundaryProbe(
                    DRAW,
                    -1
            );
        }


        /*
         * KQK / KRK use the existing exact 3-piece assets.
         */
        if (survivingType
                == PieceType.QUEEN
                ||
                survivingType
                        == PieceType.ROOK) {

            Color survivingColor =
                    survivingWhite
                            ? Color.WHITE
                            : Color.BLACK;


            ThreePieceTablebase tablebase =
                    threePieceService.get(
                            survivingType,
                            survivingColor
                    );


            ThreePieceTablebase.Probe probe =
                    tablebase.probeSquares(
                            whiteKing,
                            blackKing,
                            survivingSquare,
                            blackToMove
                    );


            byte mapped =
                    switch (probe.outcome()) {

                        case WIN ->
                                WIN;

                        case LOSS ->
                                LOSS;

                        case DRAW ->
                                DRAW;

                        case UNSUPPORTED ->
                                throw new IllegalStateException(
                                        "Exact "
                                                + survivingType
                                                + " three-piece boundary returned UNSUPPORTED."
                                );
                    };


            return new BoundaryProbe(
                    mapped,
                    probe.mateDistance()
            );
        }


        throw new IllegalStateException(
                "Unexpected Tier-0 boundary type: "
                        + survivingType
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

        if (outcome[state]
                == WIN
                ||
                outcome[state]
                        == LOSS) {

            return;
        }


        outcome[state] =
                solvedOutcome;


        distance[state] =
                checkedShort(
                        solvedDistance
                );


        frontier.add(
                state
        );
    }


    private short checkedShort(
            int value
    ) {

        if (value < 0
                || value > Short.MAX_VALUE) {

            throw new IllegalStateException(
                    material.displayName()
                            + " DTM exceeds short range: "
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
            FourPieceMaterialClass material,
            boolean sameSideOwnerIsWhite,
            byte[] outcome,
            short[] distance,
            long legalStates,
            long wins,
            long losses,
            long draws,
            int maximumDistance,
            long propagatedStates,
            long predecessorEdges,
            long classificationMillis,
            long propagationMillis,
            long totalMillis
    ) {

    }


    /**
     * Primitive min-heap ordered by solved DTM and then state id.
     *
     * No boxed Integer objects or PriorityQueue nodes are used.
     */
    private static final class IntDistanceHeap {

        private int[] heap;

        private int size;

        private final short[] distance;


        private IntDistanceHeap(
                int initialCapacity,
                short[] distance
        ) {

            heap =
                    new int[
                            Math.max(
                                    16,
                                    initialCapacity
                            )
                            ];

            this.distance =
                    distance;
        }


        private void add(
                int state
        ) {

            ensureCapacity();


            int index =
                    size++;


            heap[index] =
                    state;


            while (index > 0) {

                int parent =
                        (index - 1)
                                >>> 1;


                if (!less(
                        heap[index],
                        heap[parent]
                )) {

                    break;
                }


                int temporary =
                        heap[index];

                heap[index] =
                        heap[parent];

                heap[parent] =
                        temporary;


                index =
                        parent;
            }
        }


        private int remove() {

            if (size == 0) {

                throw new IllegalStateException(
                        "Empty generic retrograde frontier."
                );
            }


            int result =
                    heap[0];


            size--;


            if (size > 0) {

                heap[0] =
                        heap[size];


                int index =
                        0;


                while (true) {

                    int left =
                            index * 2
                                    + 1;


                    if (left >= size) {

                        break;
                    }


                    int right =
                            left + 1;


                    int best =
                            left;


                    if (right < size
                            && less(
                            heap[right],
                            heap[left]
                    )) {

                        best =
                                right;
                    }


                    if (!less(
                            heap[best],
                            heap[index]
                    )) {

                        break;
                    }


                    int temporary =
                            heap[index];

                    heap[index] =
                            heap[best];

                    heap[best] =
                            temporary;


                    index =
                            best;
                }
            }


            return result;
        }


        private boolean less(
                int first,
                int second
        ) {

            int firstDistance =
                    distance[first];

            int secondDistance =
                    distance[second];


            if (firstDistance
                    != secondDistance) {

                return firstDistance
                        < secondDistance;
            }


            return first
                    < second;
        }


        private void ensureCapacity() {

            if (size
                    < heap.length) {

                return;
            }


            heap =
                    Arrays.copyOf(
                            heap,
                            heap.length * 2
                    );
        }


        private boolean isEmpty() {

            return size
                    == 0;
        }


        private int size() {

            return size;
        }
    }
}
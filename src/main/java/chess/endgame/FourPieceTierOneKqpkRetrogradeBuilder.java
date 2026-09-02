package main.java.chess.endgame;

import main.java.chess.model.Board;
import main.java.chess.model.Color;
import main.java.chess.model.Piece;
import main.java.chess.model.PieceType;
import main.java.chess.model.Position;
import main.java.chess.model.PositionKey;
import main.java.chess.model.Square;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;


/**
 * Milestone 37.
 *
 * Dependency-aware exact retrograde builder for KQPK.
 *
 * State convention:
 *
 *     firstExtra  = queen
 *     secondExtra = pawn
 *
 * Both extras belong to {@code strongIsWhite ? WHITE : BLACK}.
 *
 * The KQPK graph has three kinds of successor:
 *
 *     1. SAME CLASS
 *            KQPK -> KQPK
 *
 *     2. THREE-PIECE BOUNDARY
 *            pawn captured  -> KQK
 *            queen captured -> KPK
 *
 *     3. TIER-0 PROMOTION BOUNDARY
 *            KQPK -> KQQK / KQRK / KQBK / KQNK
 *
 * Only same-class predecessors are propagated through the KQPK graph.
 * Three-piece and promotion successors are exact dependency seeds.
 *
 * WDL/DTM semantics:
 *
 *     WIN:
 *         at least one legal child is LOSS
 *         distance = 1 + minimum LOSS-child distance
 *
 *     LOSS:
 *         every legal child is WIN
 *         distance = 1 + maximum WIN-child distance
 *
 *     DRAW:
 *         every legal state left unresolved at the fixpoint
 *
 * Memory strategy:
 *
 *     - no giant predecessor CSR;
 *     - predecessors are generated on demand by the verified M35 generator;
 *     - remaining move counts use one unsigned byte per raw state;
 *     - solved states are processed by a primitive distance-bucket queue;
 *     - Tier-0 promotion dependencies are probed one material type at a time,
 *       clearing the runtime cache between passes so KQQK/KQRK/KQBK/KQNK
 *       are not all resident together.
 */
public final class FourPieceTierOneKqpkRetrogradeBuilder {

    private static final FourPieceMaterialClass MATERIAL =
            FourPieceMaterialClass.sameSide(
                    PieceType.QUEEN,
                    PieceType.PAWN
            );

    private static final int STATE_COUNT =
            FourPieceGenericPrimitiveState.STATE_COUNT;

    private static final int MAX_UNSIGNED_BYTE =
            255;

    private static final int MAX_DISTANCE =
            Short.MAX_VALUE;


    private final boolean strongIsWhite;

    private final Color strongColor;

    private final Color weakColor;

    private final ThreePieceTablebaseService threePieceService;

    private final FourPieceGenericTablebaseService tierZeroService;


    public FourPieceTierOneKqpkRetrogradeBuilder(
            boolean strongIsWhite
    ) {

        this(
                strongIsWhite,
                new ThreePieceTablebaseService(),
                new FourPieceGenericTablebaseService()
        );
    }


    public FourPieceTierOneKqpkRetrogradeBuilder(
            boolean strongIsWhite,
            ThreePieceTablebaseService threePieceService,
            FourPieceGenericTablebaseService tierZeroService
    ) {

        if (threePieceService == null) {

            throw new IllegalArgumentException(
                    "Three-piece tablebase service cannot be null."
            );
        }


        if (tierZeroService == null) {

            throw new IllegalArgumentException(
                    "Tier-0 tablebase service cannot be null."
            );
        }


        this.strongIsWhite =
                strongIsWhite;

        this.strongColor =
                strongIsWhite
                        ? Color.WHITE
                        : Color.BLACK;

        this.weakColor =
                strongColor.opposite();

        this.threePieceService =
                threePieceService;

        this.tierZeroService =
                tierZeroService;
    }


    public Result build() throws IOException {

        long started =
                System.currentTimeMillis();


        byte[] outcome =
                new byte[STATE_COUNT];

        short[] distance =
                new short[STATE_COUNT];

        byte[] remaining =
                new byte[STATE_COUNT];

        short[] maximumWinningChildDistance =
                new short[STATE_COUNT];


        Arrays.fill(
                outcome,
                FourPieceTablebase.INVALID
        );

        Arrays.fill(
                distance,
                (short) -1
        );


        FourPieceTierOneKqpkPrimitiveMoveGenerator.Buffer successors =
                new FourPieceTierOneKqpkPrimitiveMoveGenerator.Buffer(
                        64
                );

        FourPieceTierOneKqpkPrimitiveMoveGenerator.Buffer predecessorValidation =
                new FourPieceTierOneKqpkPrimitiveMoveGenerator.Buffer(
                        64
                );

        FourPieceTierOneKqpkPrimitivePredecessorGenerator.Buffer predecessors =
                new FourPieceTierOneKqpkPrimitivePredecessorGenerator.Buffer(
                        64
                );


        BuildCounters counters =
                new BuildCounters();


        System.out.println(
                "KQPK retrograde — strong "
                        + strongColor
        );

        System.out.println(
                "========================================"
        );

        System.out.println(
                "Pass 0: classify legal states, terminals, "
                        + "and three-piece boundaries..."
        );


        /*
         * =============================================================
         * PASS 0
         *
         * Classify the legal KQPK domain, record total legal-move counts,
         * seed checkmates/stalemates, and resolve KQK/KPK boundaries.
         *
         * Promotion boundaries remain counted in "remaining" but are
         * deliberately deferred to four material-specific passes below.
         * =============================================================
         */
        for (int state = 0;
             state < STATE_COUNT;
             state++) {

            if (!FourPieceTierOneKqpkPrimitiveMoveGenerator
                    .isStructurallyLegal(
                            state,
                            strongIsWhite
                    )) {

                continue;
            }


            outcome[state] =
                    FourPieceTablebase.UNKNOWN;

            distance[state] =
                    -1;

            counters.legalStates++;


            int moveCount =
                    FourPieceTierOneKqpkPrimitiveMoveGenerator
                            .generateLegalSuccessors(
                                    state,
                                    strongIsWhite,
                                    successors
                            );


            if (moveCount > MAX_UNSIGNED_BYTE) {

                throw new IllegalStateException(
                        "KQPK state has too many legal moves for "
                                + "the compact remaining-count array: "
                                + moveCount
                );
            }


            remaining[state] =
                    (byte) moveCount;


            if (moveCount == 0) {

                if (isSideToMoveInCheck(
                        state
                )) {

                    outcome[state] =
                            FourPieceTablebase.LOSS;

                    distance[state] =
                            0;

                    counters.terminalLosses++;

                } else {

                    outcome[state] =
                            FourPieceTablebase.DRAW;

                    distance[state] =
                            -1;

                    counters.terminalDraws++;
                }


                continue;
            }


            for (int i = 0;
                 i < moveCount;
                 i++) {

                int boundary =
                        successors.boundaryType(i);


                if (boundary
                        == FourPieceTierOneKqpkPrimitiveMoveGenerator
                        .BOUNDARY_NONE) {

                    counters.sameClassEdges++;

                    continue;
                }


                if (boundary
                        == FourPieceTierOneKqpkPrimitiveMoveGenerator
                        .BOUNDARY_PROMOTION) {

                    counters.promotionEdges++;

                    continue;
                }


                if (boundary
                        != FourPieceTierOneKqpkPrimitiveMoveGenerator
                        .BOUNDARY_KQK
                        && boundary
                        != FourPieceTierOneKqpkPrimitiveMoveGenerator
                        .BOUNDARY_KPK) {

                    throw new IllegalStateException(
                            "Unexpected KQPK boundary type: "
                                    + boundary
                    );
                }


                counters.threePieceEdges++;


                ExternalResult external =
                        resolveThreePieceBoundary(
                                state,
                                successors,
                                i
                        );


                applyExternalResult(
                        state,
                        external,
                        outcome,
                        distance,
                        remaining,
                        maximumWinningChildDistance,
                        counters
                );
            }
        }


        printPassZero(
                counters
        );


        /*
         * =============================================================
         * PASSES 1..4
         *
         * Resolve promotions one promoted-piece type at a time.
         *
         * This is intentionally four dense scans.  M34 showed dense KQPK
         * primitive successor generation is cheap compared with keeping four
         * 33.5M-entry Tier-0 tablebases resident simultaneously.
         * =============================================================
         */
        PieceType[] promotionTypes = {
                PieceType.QUEEN,
                PieceType.ROOK,
                PieceType.BISHOP,
                PieceType.KNIGHT
        };


        for (PieceType promotionType :
                promotionTypes) {

            System.out.println(
                    "Promotion dependency pass: "
                            + promotionType
                            + "..."
            );


            tierZeroService.clearCache();


            long matched =
                    0;

            long passStarted =
                    System.currentTimeMillis();


            for (int state = 0;
                 state < STATE_COUNT;
                 state++) {

                if (outcome[state]
                        == FourPieceTablebase.INVALID) {

                    continue;
                }


                int moveCount =
                        FourPieceTierOneKqpkPrimitiveMoveGenerator
                                .generateLegalSuccessors(
                                        state,
                                        strongIsWhite,
                                        successors
                                );


                for (int i = 0;
                     i < moveCount;
                     i++) {

                    if (successors.boundaryType(i)
                            != FourPieceTierOneKqpkPrimitiveMoveGenerator
                            .BOUNDARY_PROMOTION) {

                        continue;
                    }


                    if (successors.promotionType(i)
                            != promotionType) {

                        continue;
                    }


                    matched++;


                    ExternalResult external =
                            resolvePromotionBoundary(
                                    state,
                                    successors,
                                    i
                            );


                    applyExternalResult(
                            state,
                            external,
                            outcome,
                            distance,
                            remaining,
                            maximumWinningChildDistance,
                            counters
                    );
                }
            }


            tierZeroService.clearCache();


            System.out.printf(
                    "  matched %,d promotion edges in %.3f sec%n",
                    matched,
                    (System.currentTimeMillis()
                            - passStarted)
                            / 1000.0
            );
        }


        /*
         * If every legal move is already known to be an external WIN, the
         * state is an immediate LOSS before same-class propagation begins.
         */
        long externalOnlyLosses =
                0;


        for (int state = 0;
             state < STATE_COUNT;
             state++) {

            if (outcome[state]
                    != FourPieceTablebase.UNKNOWN) {

                continue;
            }


            if (remainingCount(
                    remaining,
                    state
            ) != 0) {

                continue;
            }


            outcome[state] =
                    FourPieceTablebase.LOSS;

            int parentDistance =
                    (maximumWinningChildDistance[state]
                            & 0xFFFF)
                            + 1;

            distance[state] =
                    safeShort(
                            parentDistance
                    );

            externalOnlyLosses++;
        }


        System.out.println(
                "External-only LOSS seeds: "
                        + externalOnlyLosses
        );


        /*
         * =============================================================
         * RETROGRADE PROPAGATION
         * =============================================================
         */
        DistanceBucketQueue queue =
                new DistanceBucketQueue();


        long initialQueueStates =
                0;


        for (int state = 0;
             state < STATE_COUNT;
             state++) {

            byte stateOutcome =
                    outcome[state];


            if (stateOutcome
                    != FourPieceTablebase.WIN
                    && stateOutcome
                    != FourPieceTablebase.LOSS) {

                continue;
            }


            int d =
                    distance[state];


            if (d < 0) {

                throw new IllegalStateException(
                        "Solved KQPK seed has no DTM: "
                                + state
                );
            }


            queue.add(
                    state,
                    d
            );

            initialQueueStates++;
        }


        System.out.println(
                "Initial solved queue: "
                        + initialQueueStates
        );

        System.out.println(
                "Retrograde propagation..."
        );


        long propagated =
                0;

        long predecessorEdges =
                0;

        long propagationStarted =
                System.currentTimeMillis();


        while (!queue.isEmpty()) {

            DistanceBucketQueue.Entry entry =
                    queue.remove();


            int child =
                    entry.state();

            int queuedDistance =
                    entry.distance();


            /*
             * A WIN may have been seeded by an external LOSS child and later
             * improved by a shorter same-class LOSS child.  Old bucket entries
             * are therefore allowed, but they must not propagate.
             */
            if (distance[child]
                    != queuedDistance) {

                continue;
            }


            byte childOutcome =
                    outcome[child];


            if (childOutcome
                    != FourPieceTablebase.WIN
                    && childOutcome
                    != FourPieceTablebase.LOSS) {

                throw new IllegalStateException(
                        "Unsolved state reached the solved queue: "
                                + child
                );
            }


            int predecessorCount =
                    FourPieceTierOneKqpkPrimitivePredecessorGenerator
                            .generatePredecessors(
                                    child,
                                    strongIsWhite,
                                    predecessors,
                                    predecessorValidation
                            );


            propagated++;

            predecessorEdges +=
                    predecessorCount;


            for (int i = 0;
                 i < predecessorCount;
                 i++) {

                int parent =
                        predecessors.state(i);

                byte parentOutcome =
                        outcome[parent];


                if (childOutcome
                        == FourPieceTablebase.LOSS) {

                    int candidateDistance =
                            queuedDistance + 1;


                    if (parentOutcome
                            == FourPieceTablebase.UNKNOWN) {

                        outcome[parent] =
                                FourPieceTablebase.WIN;

                        distance[parent] =
                                safeShort(
                                        candidateDistance
                                );

                        queue.add(
                                parent,
                                candidateDistance
                        );

                        continue;
                    }


                    if (parentOutcome
                            == FourPieceTablebase.WIN
                            && candidateDistance
                            < distance[parent]) {

                        distance[parent] =
                                safeShort(
                                        candidateDistance
                                );

                        queue.add(
                                parent,
                                candidateDistance
                        );

                        continue;
                    }


                    if (parentOutcome
                            == FourPieceTablebase.LOSS) {

                        throw new IllegalStateException(
                                "Retrograde contradiction: LOSS parent "
                                        + parent
                                        + " has a LOSS child "
                                        + child
                                        + "."
                        );
                    }


                    continue;
                }


                /*
                 * childOutcome == WIN
                 *
                 * A WIN child is bad for the player at the parent.  It removes
                 * one possible escape from LOSS.  DRAW children never reach
                 * this queue and therefore permanently keep remaining > 0.
                 */
                if (parentOutcome
                        != FourPieceTablebase.UNKNOWN) {

                    continue;
                }


                int remainingMoves =
                        remainingCount(
                                remaining,
                                parent
                        );


                if (remainingMoves <= 0) {

                    throw new IllegalStateException(
                            "KQPK remaining-count underflow at state "
                                    + parent
                    );
                }


                remainingMoves--;


                setRemainingCount(
                        remaining,
                        parent,
                        remainingMoves
                );


                int previousMaximum =
                        maximumWinningChildDistance[parent]
                                & 0xFFFF;


                if (queuedDistance
                        > previousMaximum) {

                    maximumWinningChildDistance[parent] =
                            safeShort(
                                    queuedDistance
                            );
                }


                if (remainingMoves
                        == 0) {

                    int parentDistance =
                            (maximumWinningChildDistance[parent]
                                    & 0xFFFF)
                                    + 1;


                    outcome[parent] =
                            FourPieceTablebase.LOSS;

                    distance[parent] =
                            safeShort(
                                    parentDistance
                            );


                    queue.add(
                            parent,
                            parentDistance
                    );
                }
            }
        }


        System.out.printf(
                "  propagated %,d solved states across %,d predecessor edges "
                        + "in %.3f sec%n",
                propagated,
                predecessorEdges,
                (System.currentTimeMillis()
                        - propagationStarted)
                        / 1000.0
        );


        /*
         * =============================================================
         * FIXPOINT: unresolved legal states are exact draws.
         * =============================================================
         */
        long wins =
                0;

        long losses =
                0;

        long draws =
                0;

        int maximumDistance =
                0;


        for (int state = 0;
             state < STATE_COUNT;
             state++) {

            if (outcome[state]
                    == FourPieceTablebase.UNKNOWN) {

                outcome[state] =
                        FourPieceTablebase.DRAW;

                distance[state] =
                        -1;
            }


            switch (outcome[state]) {

                case FourPieceTablebase.WIN -> {

                    wins++;

                    maximumDistance =
                            Math.max(
                                    maximumDistance,
                                    distance[state]
                            );
                }

                case FourPieceTablebase.LOSS -> {

                    losses++;

                    maximumDistance =
                            Math.max(
                                    maximumDistance,
                                    distance[state]
                            );
                }

                case FourPieceTablebase.DRAW ->
                        draws++;

                case FourPieceTablebase.INVALID -> {
                    // Not part of the legal KQPK state space.
                }

                default ->
                        throw new IllegalStateException(
                                "Unexpected KQPK outcome code at state "
                                        + state
                                        + ": "
                                        + outcome[state]
                        );
            }
        }


        long legalStates =
                counters.legalStates;


        if (wins + losses + draws
                != legalStates) {

            throw new IllegalStateException(
                    "KQPK result count mismatch: legal="
                            + legalStates
                            + " WIN="
                            + wins
                            + " LOSS="
                            + losses
                            + " DRAW="
                            + draws
            );
        }


        long buildMillis =
                System.currentTimeMillis()
                        - started;


        /*
         * Large work arrays are no longer needed after this point.
         * Only outcome/distance are retained in the Result.
         */
        remaining =
                null;

        maximumWinningChildDistance =
                null;


        return new Result(
                MATERIAL,
                strongIsWhite,
                outcome,
                distance,
                legalStates,
                wins,
                losses,
                draws,
                maximumDistance,
                buildMillis,
                counters.sameClassEdges,
                counters.threePieceEdges,
                counters.promotionEdges,
                counters.externalWinChildren,
                counters.externalLossChildren,
                counters.externalDrawChildren,
                predecessorEdges
        );
    }


    // =============================================================
    // EXTERNAL BOUNDARIES
    // =============================================================

    private ExternalResult resolveThreePieceBoundary(
            int parentState,
            FourPieceTierOneKqpkPrimitiveMoveGenerator.Buffer successors,
            int index
    ) {

        int boundary =
                successors.boundaryType(index);


        PieceType survivingType =
                switch (boundary) {

                    case FourPieceTierOneKqpkPrimitiveMoveGenerator
                                 .BOUNDARY_KQK ->
                            PieceType.QUEEN;

                    case FourPieceTierOneKqpkPrimitiveMoveGenerator
                                 .BOUNDARY_KPK ->
                            PieceType.PAWN;

                    default ->
                            throw new IllegalArgumentException(
                                    "Not a three-piece KQPK boundary: "
                                            + boundary
                            );
                };


        Position child =
                threePieceBoundaryPosition(
                        parentState,
                        survivingType,
                        successors.survivingPieceSquare(index),
                        successors.toSquare(index)
                );


        ThreePieceTablebase tablebase =
                threePieceService.get(
                        survivingType,
                        strongColor
                );


        ThreePieceTablebase.Probe probe =
                tablebase.probe(
                        child
                );


        return switch (probe.outcome()) {

            case WIN ->
                    new ExternalResult(
                            FourPieceTablebase.WIN,
                            probe.mateDistance()
                    );

            case LOSS ->
                    new ExternalResult(
                            FourPieceTablebase.LOSS,
                            probe.mateDistance()
                    );

            case DRAW ->
                    new ExternalResult(
                            FourPieceTablebase.DRAW,
                            -1
                    );

            case UNSUPPORTED ->
                    throw new IllegalStateException(
                            "Three-piece dependency rejected a legal "
                                    + survivingType
                                    + " boundary from KQPK."
                    );
        };
    }


    private ExternalResult resolvePromotionBoundary(
            int parentState,
            FourPieceTierOneKqpkPrimitiveMoveGenerator.Buffer successors,
            int index
    ) throws IOException {

        PieceType promotionType =
                successors.promotionType(index);


        if (promotionType == null) {

            throw new IllegalArgumentException(
                    "Promotion boundary has no promotion type."
            );
        }


        Position child =
                promotionBoundaryPosition(
                        parentState,
                        promotionType,
                        successors.promotionSquare(index)
                );


        Optional<FourPieceGenericTablebaseService.ProbeResult> optional =
                tierZeroService.probe(
                        child
                );


        if (optional.isEmpty()) {

            throw new IllegalStateException(
                    "Tier-0 catalog rejected a legal KQPK promotion to "
                            + promotionType
                            + "."
            );
        }


        FourPieceGenericTablebaseService.ProbeResult probe =
                optional.get();


        byte result =
                probe.outcome();


        if (result
                != FourPieceTablebase.WIN
                && result
                != FourPieceTablebase.LOSS
                && result
                != FourPieceTablebase.DRAW) {

            throw new IllegalStateException(
                    "Tier-0 promotion probe returned non-final outcome: "
                            + result
            );
        }


        return new ExternalResult(
                result,
                result == FourPieceTablebase.DRAW
                        ? -1
                        : probe.distance()
        );
    }


    private void applyExternalResult(
            int parent,
            ExternalResult child,
            byte[] outcome,
            short[] distance,
            byte[] remaining,
            short[] maximumWinningChildDistance,
            BuildCounters counters
    ) {

        if (child.outcome()
                == FourPieceTablebase.LOSS) {

            counters.externalLossChildren++;


            int candidate =
                    child.distance() + 1;


            if (outcome[parent]
                    == FourPieceTablebase.UNKNOWN) {

                outcome[parent] =
                        FourPieceTablebase.WIN;

                distance[parent] =
                        safeShort(
                                candidate
                        );

            } else if (outcome[parent]
                    == FourPieceTablebase.WIN
                    && candidate
                    < distance[parent]) {

                distance[parent] =
                        safeShort(
                                candidate
                        );
            }


            return;
        }


        if (child.outcome()
                == FourPieceTablebase.WIN) {

            counters.externalWinChildren++;


            /*
             * Once the parent is known WIN, "remaining" is no longer needed
             * for its own WDL classification.  Still updating it is harmless,
             * but skipping avoids underflow if another boundary also resolved
             * the parent earlier.
             */
            if (outcome[parent]
                    == FourPieceTablebase.UNKNOWN) {

                int remainingMoves =
                        remainingCount(
                                remaining,
                                parent
                        );


                if (remainingMoves <= 0) {

                    throw new IllegalStateException(
                            "External KQPK remaining-count underflow at "
                                    + parent
                    );
                }


                remainingMoves--;


                setRemainingCount(
                        remaining,
                        parent,
                        remainingMoves
                );


                int previousMaximum =
                        maximumWinningChildDistance[parent]
                                & 0xFFFF;


                if (child.distance()
                        > previousMaximum) {

                    maximumWinningChildDistance[parent] =
                            safeShort(
                                    child.distance()
                            );
                }
            }


            return;
        }


        if (child.outcome()
                == FourPieceTablebase.DRAW) {

            counters.externalDrawChildren++;

            return;
        }


        throw new IllegalStateException(
                "Unresolved external KQPK dependency."
        );
    }


    // =============================================================
    // CHILD POSITION CONSTRUCTION
    // =============================================================

    private Position threePieceBoundaryPosition(
            int parentState,
            PieceType survivingType,
            int survivingPieceSquare,
            int weakKingDestination
    ) {

        int whiteKing =
                FourPieceGenericPrimitiveState.whiteKing(
                        parentState
                );

        int blackKing =
                FourPieceGenericPrimitiveState.blackKing(
                        parentState
                );


        if (weakColor
                == Color.WHITE) {

            whiteKing =
                    weakKingDestination;

        } else {

            blackKing =
                    weakKingDestination;
        }


        Board board =
                new Board();


        board.setPiece(
                square(whiteKing),
                new Piece(
                        PieceType.KING,
                        Color.WHITE
                )
        );

        board.setPiece(
                square(blackKing),
                new Piece(
                        PieceType.KING,
                        Color.BLACK
                )
        );

        board.setPiece(
                square(survivingPieceSquare),
                new Piece(
                        survivingType,
                        strongColor
                )
        );


        return childPosition(
                board,
                childSideToMove(
                        parentState
                )
        );
    }


    private Position promotionBoundaryPosition(
            int parentState,
            PieceType promotionType,
            int promotionSquare
    ) {

        int whiteKing =
                FourPieceGenericPrimitiveState.whiteKing(
                        parentState
                );

        int blackKing =
                FourPieceGenericPrimitiveState.blackKing(
                        parentState
                );

        int queen =
                FourPieceGenericPrimitiveState.firstExtra(
                        parentState
                );


        Board board =
                new Board();


        board.setPiece(
                square(whiteKing),
                new Piece(
                        PieceType.KING,
                        Color.WHITE
                )
        );

        board.setPiece(
                square(blackKing),
                new Piece(
                        PieceType.KING,
                        Color.BLACK
                )
        );

        board.setPiece(
                square(queen),
                new Piece(
                        PieceType.QUEEN,
                        strongColor
                )
        );

        board.setPiece(
                square(promotionSquare),
                new Piece(
                        promotionType,
                        strongColor
                )
        );


        return childPosition(
                board,
                childSideToMove(
                        parentState
                )
        );
    }


    private static Position childPosition(
            Board board,
            Color sideToMove
    ) {

        Position temporary =
                new Position(
                        board,
                        sideToMove,
                        false,
                        false,
                        false,
                        false,
                        null,
                        0,
                        1,
                        new HashMap<>()
                );


        Map<PositionKey, Integer> repetitionCounts =
                new HashMap<>();


        repetitionCounts.put(
                temporary.createPositionKey(),
                1
        );


        return new Position(
                board,
                sideToMove,
                false,
                false,
                false,
                false,
                null,
                0,
                1,
                repetitionCounts
        );
    }


    private static Color childSideToMove(
            int parentState
    ) {

        return FourPieceGenericPrimitiveState.blackToMove(
                parentState
        )
                ? Color.WHITE
                : Color.BLACK;
    }


    private static Square square(
            int primitiveSquare
    ) {

        return new Square(
                primitiveSquare & 7,
                primitiveSquare >>> 3
        );
    }


    // =============================================================
    // TERMINAL CHECK DETECTION
    // =============================================================

    private boolean isSideToMoveInCheck(
            int state
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

        boolean blackToMove =
                FourPieceGenericPrimitiveState.blackToMove(
                        state
                );


        Color sideToMove =
                blackToMove
                        ? Color.BLACK
                        : Color.WHITE;


        int sideKing =
                blackToMove
                        ? bk
                        : wk;


        int opponentKing =
                blackToMove
                        ? wk
                        : bk;


        if (adjacent(
                sideKing,
                opponentKing
        )) {

            return true;
        }


        /*
         * The weak side has only a king.  Therefore only a weak king can be
         * attacked by the strong queen/pawn.  The strong king itself has no
         * additional weak material attacking it.
         */
        if (sideToMove
                == strongColor) {

            return false;
        }


        int strongKing =
                strongIsWhite
                        ? wk
                        : bk;


        return queenAttacks(
                queen,
                sideKing,
                strongKing,
                pawn
        )
                || pawnAttacks(
                pawn,
                sideKing,
                strongIsWhite
        );
    }


    private static boolean queenAttacks(
            int queen,
            int target,
            int blockerA,
            int blockerB
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


        boolean sameFile =
                qf == tf;

        boolean sameRank =
                qr == tr;

        boolean sameDiagonal =
                Math.abs(
                        tf - qf
                )
                        == Math.abs(
                        tr - qr
                );


        if (!sameFile
                && !sameRank
                && !sameDiagonal) {

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


            if (square == blockerA
                    || square == blockerB) {

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


        int attackRank =
                pr
                        + (pawnIsWhite
                        ? 1
                        : -1);


        return tr == attackRank
                && Math.abs(
                tf - pf
        ) == 1;
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
        ) == 1;
    }


    // =============================================================
    // COMPACT COUNTS / DISTANCE
    // =============================================================

    private static int remainingCount(
            byte[] remaining,
            int state
    ) {

        return remaining[state]
                & 0xFF;
    }


    private static void setRemainingCount(
            byte[] remaining,
            int state,
            int value
    ) {

        if (value < 0
                || value > MAX_UNSIGNED_BYTE) {

            throw new IllegalArgumentException(
                    "Remaining move count out of range: "
                            + value
            );
        }


        remaining[state] =
                (byte) value;
    }


    private static short safeShort(
            int value
    ) {

        if (value < 0
                || value > MAX_DISTANCE) {

            throw new IllegalStateException(
                    "KQPK DTM exceeds short range: "
                            + value
            );
        }


        return (short) value;
    }


    private static void printPassZero(
            BuildCounters counters
    ) {

        System.out.println(
                "  legal states: "
                        + counters.legalStates
        );

        System.out.println(
                "  terminal losses: "
                        + counters.terminalLosses
        );

        System.out.println(
                "  terminal draws: "
                        + counters.terminalDraws
        );

        System.out.println(
                "  same-class edges: "
                        + counters.sameClassEdges
        );

        System.out.println(
                "  three-piece boundary edges: "
                        + counters.threePieceEdges
        );

        System.out.println(
                "  promotion boundary edges: "
                        + counters.promotionEdges
        );
    }


    // =============================================================
    // RESULT
    // =============================================================

    public record Result(
            FourPieceMaterialClass material,
            boolean strongIsWhite,
            byte[] outcome,
            short[] distance,
            long legalStates,
            long wins,
            long losses,
            long draws,
            int maximumDistance,
            long buildMillis,
            long sameClassEdges,
            long threePieceEdges,
            long promotionEdges,
            long externalWinChildren,
            long externalLossChildren,
            long externalDrawChildren,
            long predecessorEdgesProcessed
    ) {

        public Result {

            if (material == null
                    || outcome == null
                    || distance == null) {

                throw new IllegalArgumentException(
                        "KQPK retrograde result data cannot be null."
                );
            }


            if (outcome.length
                    != STATE_COUNT
                    || distance.length
                    != STATE_COUNT) {

                throw new IllegalArgumentException(
                        "KQPK retrograde result arrays have incorrect size."
                );
            }


            if (wins + losses + draws
                    != legalStates) {

                throw new IllegalArgumentException(
                        "KQPK retrograde result counts do not balance."
                );
            }
        }


        public FourPieceGenericTablebase toTablebase() {

            return new FourPieceGenericTablebase(
                    material,
                    strongIsWhite,
                    outcome,
                    distance,
                    legalStates,
                    wins,
                    losses,
                    draws,
                    maximumDistance
            );
        }
    }


    private record ExternalResult(
            byte outcome,
            int distance
    ) {
    }


    private static final class BuildCounters {

        long legalStates;

        long terminalLosses;

        long terminalDraws;

        long sameClassEdges;

        long threePieceEdges;

        long promotionEdges;

        long externalWinChildren;

        long externalLossChildren;

        long externalDrawChildren;
    }


    // =============================================================
    // PRIMITIVE DISTANCE-BUCKET QUEUE
    // =============================================================

    /**
     * Primitive queue ordered by exact DTM.
     *
     * New retrograde states always have parentDistance = childDistance + 1.
     * External seeds can start at arbitrary dependency distances, so the
     * bucket array provides the same ordering guarantee as a priority queue
     * without one heap object per solved state.
     */
    private static final class DistanceBucketQueue {

        private final IntBucket[] buckets =
                new IntBucket[MAX_DISTANCE + 1];

        private int nextDistance =
                0;

        private long size =
                0;


        void add(
                int state,
                int distance
        ) {

            if (distance < 0
                    || distance > MAX_DISTANCE) {

                throw new IllegalArgumentException(
                        "Queue DTM out of range: "
                                + distance
                );
            }


            IntBucket bucket =
                    buckets[distance];


            if (bucket == null) {

                bucket =
                        new IntBucket();

                buckets[distance] =
                        bucket;
            }


            bucket.add(
                    state
            );

            size++;


            if (distance
                    < nextDistance) {

                nextDistance =
                        distance;
            }
        }


        boolean isEmpty() {

            return size == 0;
        }


        Entry remove() {

            if (size == 0) {

                throw new IllegalStateException(
                        "Cannot remove from empty distance queue."
                );
            }


            while (nextDistance
                    <= MAX_DISTANCE) {

                IntBucket bucket =
                        buckets[nextDistance];


                if (bucket == null
                        || bucket.isEmpty()) {

                    buckets[nextDistance] =
                            null;

                    nextDistance++;

                    continue;
                }


                int state =
                        bucket.remove();


                size--;


                if (bucket.isEmpty()) {

                    buckets[nextDistance] =
                            null;
                }


                return new Entry(
                        state,
                        nextDistance
                );
            }


            throw new IllegalStateException(
                    "Distance queue size is nonzero but no bucket contains data."
            );
        }


        record Entry(
                int state,
                int distance
        ) {
        }
    }


    private static final class IntBucket {

        private int[] data =
                new int[1024];

        private int size =
                0;


        void add(
                int value
        ) {

            if (size
                    == data.length) {

                data =
                        Arrays.copyOf(
                                data,
                                data.length * 2
                        );
            }


            data[size++] =
                    value;
        }


        int remove() {

            if (size == 0) {

                throw new IllegalStateException(
                        "Cannot remove from empty bucket."
                );
            }


            return data[--size];
        }


        boolean isEmpty() {

            return size == 0;
        }
    }
}

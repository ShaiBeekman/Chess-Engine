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
 * Milestone 58.
 *
 * Exact dependency-aware retrograde builder for SAME_SIDE KPPK.
 *
 * The primitive representation remains FourPieceGenericPrimitiveState:
 *
 *     white king
 *     black king
 *     pawn 1
 *     pawn 2
 *     side to move
 *
 * The identical pawns are canonicalized with firstExtra < secondExtra.
 *
 * External dependencies:
 *
 *     king captures one pawn
 *         -> exact three-piece KPK
 *
 *     one pawn promotes while the other remains
 *         -> exact Tier-1 KQPK/KRPK/KBPK/KNPK
 *
 * Only same-class KPPK predecessors participate in propagation.
 */
public final class FourPieceTierTwoKppkRetrogradeBuilder {

    private static final int STATE_COUNT =
            FourPieceGenericPrimitiveState.STATE_COUNT;

    private static final int MAX_UNSIGNED_BYTE =
            255;

    private static final int MAX_DISTANCE =
            Short.MAX_VALUE;

    private static final FourPieceMaterialClass MATERIAL =
            FourPieceMaterialClass.sameSide(
                    PieceType.PAWN,
                    PieceType.PAWN
            );


    private final boolean pawnOwnerIsWhite;

    private final ThreePieceTablebaseService threePieceService;

    private final FourPieceTierOneTablebaseService tierOneService;


    public FourPieceTierTwoKppkRetrogradeBuilder(
            boolean pawnOwnerIsWhite
    ) {

        this(
                pawnOwnerIsWhite,
                new ThreePieceTablebaseService(),
                new FourPieceTierOneTablebaseService()
        );
    }


    public FourPieceTierTwoKppkRetrogradeBuilder(
            boolean pawnOwnerIsWhite,
            ThreePieceTablebaseService threePieceService,
            FourPieceTierOneTablebaseService tierOneService
    ) {

        if (threePieceService == null) {

            throw new IllegalArgumentException(
                    "Three-piece tablebase service cannot be null."
            );
        }

        if (tierOneService == null) {

            throw new IllegalArgumentException(
                    "Tier-1 tablebase service cannot be null."
            );
        }

        this.pawnOwnerIsWhite =
                pawnOwnerIsWhite;

        this.threePieceService =
                threePieceService;

        this.tierOneService =
                tierOneService;
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

        FourPieceTierTwoKppkPrimitiveMoveGenerator.Buffer successors =
                new FourPieceTierTwoKppkPrimitiveMoveGenerator.Buffer(
                        32
                );

        FourPieceTierTwoKppkPrimitiveMoveGenerator.Buffer predecessorValidation =
                new FourPieceTierTwoKppkPrimitiveMoveGenerator.Buffer(
                        32
                );

        FourPieceTierTwoKppkPrimitivePredecessorGenerator.Buffer predecessors =
                new FourPieceTierTwoKppkPrimitivePredecessorGenerator.Buffer(
                        32
                );

        BuildCounters counters =
                new BuildCounters();

        System.out.println(
                "KPPK Tier-2 retrograde — pawn owner "
                        + (pawnOwnerIsWhite
                        ? "WHITE"
                        : "BLACK")
        );

        System.out.println(
                "============================================================"
        );

        System.out.println(
                "Pass 0: classify legal states, terminals, and KPK boundaries..."
        );

        for (int state = 0;
             state < STATE_COUNT;
             state++) {

            if (!FourPieceTierTwoKppkPrimitiveMoveGenerator
                    .isStructurallyLegal(
                            state,
                            MATERIAL,
                            pawnOwnerIsWhite
                    )) {

                continue;
            }

            outcome[state] =
                    FourPieceTablebase.UNKNOWN;

            distance[state] =
                    -1;

            counters.legalStates++;

            int moveCount =
                    FourPieceTierTwoKppkPrimitiveMoveGenerator
                            .generateLegalSuccessors(
                                    state,
                                    MATERIAL,
                                    pawnOwnerIsWhite,
                                    successors
                            );

            if (moveCount
                    > MAX_UNSIGNED_BYTE) {

                throw new IllegalStateException(
                        "KPPK state has too many legal moves for compact "
                                + "remaining-count storage: "
                                + moveCount
                );
            }

            remaining[state] =
                    (byte) moveCount;

            if (moveCount
                    == 0) {

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
                        successors.boundaryType(
                                i
                        );

                if (boundary
                        == FourPieceTierTwoKppkPrimitiveMoveGenerator
                        .BOUNDARY_NONE) {

                    counters.sameClassEdges++;

                    continue;
                }

                if (boundary
                        == FourPieceTierTwoKppkPrimitiveMoveGenerator
                        .BOUNDARY_TIER_ONE_PROMOTION) {

                    counters.promotionEdges++;

                    continue;
                }

                if (boundary
                        != FourPieceTierTwoKppkPrimitiveMoveGenerator
                        .BOUNDARY_THREE_PIECE) {

                    throw new IllegalStateException(
                            "Unexpected KPPK boundary type: "
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

        PieceType[] promotionTypes = {
                PieceType.QUEEN,
                PieceType.ROOK,
                PieceType.BISHOP,
                PieceType.KNIGHT
        };

        for (PieceType promotionType :
                promotionTypes) {

            System.out.println(
                    "Tier-1 promotion dependency pass: "
                            + promotionType
                            + "..."
            );

            tierOneService.clearCache();

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
                        FourPieceTierTwoKppkPrimitiveMoveGenerator
                                .generateLegalSuccessors(
                                        state,
                                        MATERIAL,
                                        pawnOwnerIsWhite,
                                        successors
                                );

                for (int i = 0;
                     i < moveCount;
                     i++) {

                    if (successors.boundaryType(
                            i
                    ) != FourPieceTierTwoKppkPrimitiveMoveGenerator
                            .BOUNDARY_TIER_ONE_PROMOTION) {

                        continue;
                    }

                    if (successors.promotionType(
                            i
                    ) != promotionType) {

                        continue;
                    }

                    matched++;

                    ExternalResult external =
                            resolveTierOnePromotionBoundary(
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

            tierOneService.clearCache();

            System.out.printf(
                    "  matched %,d promotion edges in %.3f sec%n",
                    matched,
                    (System.currentTimeMillis()
                            - passStarted)
                            / 1000.0
            );
        }

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
                        "Solved KPPK seed has no DTM: "
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
                        "Unsolved state reached KPPK solved queue: "
                                + child
                );
            }

            int predecessorCount =
                    FourPieceTierTwoKppkPrimitivePredecessorGenerator
                            .generatePredecessors(
                                    child,
                                    MATERIAL,
                                    pawnOwnerIsWhite,
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
                        predecessors.state(
                                i
                        );

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
                                "Retrograde contradiction: LOSS KPPK parent "
                                        + parent
                                        + " has LOSS child "
                                        + child
                                        + "."
                        );
                    }

                    continue;
                }

                if (parentOutcome
                        != FourPieceTablebase.UNKNOWN) {

                    continue;
                }

                int remainingMoves =
                        remainingCount(
                                remaining,
                                parent
                        );

                if (remainingMoves
                        <= 0) {

                    throw new IllegalStateException(
                            "KPPK remaining-count underflow at state "
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
                }

                default ->
                        throw new IllegalStateException(
                                "Unexpected KPPK outcome code at state "
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
                    "KPPK result count mismatch: legal="
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

        return new Result(
                MATERIAL,
                pawnOwnerIsWhite,
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


    private ExternalResult resolveThreePieceBoundary(
            int parentState,
            FourPieceTierTwoKppkPrimitiveMoveGenerator.Buffer successors,
            int index
    ) {

        if (successors.survivingType(
                index
        ) != PieceType.PAWN) {

            throw new IllegalStateException(
                    "KPPK three-piece boundary must leave exactly one pawn."
            );
        }

        Color pawnOwner =
                pawnOwnerIsWhite
                        ? Color.WHITE
                        : Color.BLACK;

        Position child =
                threePieceBoundaryPosition(
                        parentState,
                        successors,
                        index,
                        pawnOwner
                );

        ThreePieceTablebase tablebase =
                threePieceService.get(
                        PieceType.PAWN,
                        pawnOwner
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
                            "KPK dependency rejected legal KPPK capture boundary."
                    );
        };
    }


    private ExternalResult resolveTierOnePromotionBoundary(
            int parentState,
            FourPieceTierTwoKppkPrimitiveMoveGenerator.Buffer successors,
            int index
    ) throws IOException {

        PieceType promotionType =
                successors.promotionType(
                        index
                );

        if (promotionType == null) {

            throw new IllegalArgumentException(
                    "KPPK Tier-1 promotion boundary has no promotion type."
            );
        }

        Position child =
                tierOnePromotionBoundaryPosition(
                        parentState,
                        successors,
                        index,
                        promotionType
                );

        Optional<FourPieceTierOneTablebaseService.ProbeResult> optional =
                tierOneService.probe(
                        child
                );

        if (optional.isEmpty()) {

            throw new IllegalStateException(
                    "Tier-1 catalog rejected legal KPPK promotion to "
                            + promotionType
                            + ". Required asset: "
                            + FourPieceMaterialClass.sameSide(
                            promotionType,
                            PieceType.PAWN
                    ).assetStem()
            );
        }

        FourPieceTierOneTablebaseService.ProbeResult probe =
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
                    "Tier-1 promotion probe returned non-final outcome: "
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

            if (outcome[parent]
                    == FourPieceTablebase.UNKNOWN) {

                int remainingMoves =
                        remainingCount(
                                remaining,
                                parent
                        );

                if (remainingMoves
                        <= 0) {

                    throw new IllegalStateException(
                            "External KPPK remaining-count underflow at "
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
                "Unresolved external KPPK dependency."
        );
    }


    private Position threePieceBoundaryPosition(
            int parentState,
            FourPieceTierTwoKppkPrimitiveMoveGenerator.Buffer successors,
            int index,
            Color pawnOwner
    ) {

        int whiteKing =
                FourPieceGenericPrimitiveState.whiteKing(
                        parentState
                );

        int blackKing =
                FourPieceGenericPrimitiveState.blackKing(
                        parentState
                );

        int from =
                successors.fromSquare(
                        index
                );

        int to =
                successors.toSquare(
                        index
                );

        if (from
                == whiteKing) {

            whiteKing =
                    to;

        } else if (from
                == blackKing) {

            blackKing =
                    to;
        }

        Board board =
                new Board();

        board.setPiece(
                square(
                        whiteKing
                ),
                new Piece(
                        PieceType.KING,
                        Color.WHITE
                )
        );

        board.setPiece(
                square(
                        blackKing
                ),
                new Piece(
                        PieceType.KING,
                        Color.BLACK
                )
        );

        board.setPiece(
                square(
                        successors.survivingPieceSquare(
                                index
                        )
                ),
                new Piece(
                        PieceType.PAWN,
                        pawnOwner
                )
        );

        return childPosition(
                board,
                childSideToMove(
                        parentState
                )
        );
    }


    private Position tierOnePromotionBoundaryPosition(
            int parentState,
            FourPieceTierTwoKppkPrimitiveMoveGenerator.Buffer successors,
            int index,
            PieceType promotionType
    ) {

        int whiteKing =
                FourPieceGenericPrimitiveState.whiteKing(
                        parentState
                );

        int blackKing =
                FourPieceGenericPrimitiveState.blackKing(
                        parentState
                );

        Color owner =
                pawnOwnerIsWhite
                        ? Color.WHITE
                        : Color.BLACK;

        Board board =
                new Board();

        board.setPiece(
                square(
                        whiteKing
                ),
                new Piece(
                        PieceType.KING,
                        Color.WHITE
                )
        );

        board.setPiece(
                square(
                        blackKing
                ),
                new Piece(
                        PieceType.KING,
                        Color.BLACK
                )
        );

        board.setPiece(
                square(
                        successors.promotionSquare(
                                index
                        )
                ),
                new Piece(
                        promotionType,
                        owner
                )
        );

        board.setPiece(
                square(
                        successors.remainingPawnSquare(
                                index
                        )
                ),
                new Piece(
                        PieceType.PAWN,
                        owner
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

        boolean sideWhite =
                !blackToMove;

        int sideKing =
                sideWhite
                        ? wk
                        : bk;

        int opponentKing =
                sideWhite
                        ? bk
                        : wk;

        if (adjacent(
                sideKing,
                opponentKing
        )) {

            return true;
        }

        boolean attackingWhite =
                !sideWhite;

        if (pawnOwnerIsWhite
                != attackingWhite) {

            return false;
        }

        return pawnAttacks(
                firstPawn,
                sideKing,
                pawnOwnerIsWhite
        )
                || pawnAttacks(
                secondPawn,
                sideKing,
                pawnOwnerIsWhite
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


    private static Square square(
            int primitiveSquare
    ) {

        return new Square(
                primitiveSquare & 7,
                primitiveSquare >>> 3
        );
    }


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
                    "KPPK DTM exceeds short range: "
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
                "  KPK boundary edges: "
                        + counters.threePieceEdges
        );

        System.out.println(
                "  Tier-1 promotion boundary edges: "
                        + counters.promotionEdges
        );
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
                        "KPPK retrograde result data cannot be null."
                );
            }

            if (outcome.length
                    != STATE_COUNT
                    || distance.length
                    != STATE_COUNT) {

                throw new IllegalArgumentException(
                        "KPPK retrograde result arrays have incorrect size."
                );
            }

            if (wins + losses + draws
                    != legalStates) {

                throw new IllegalArgumentException(
                        "KPPK retrograde result counts do not balance."
                );
            }
        }


        public FourPieceGenericTablebase toTablebase() {

            return new FourPieceGenericTablebase(
                    material,
                    sameSideOwnerIsWhite,
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

            return size
                    == 0;
        }


        Entry remove() {

            if (size
                    == 0) {

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

            if (size
                    == 0) {

                throw new IllegalStateException(
                        "Cannot remove from empty bucket."
                );
            }

            return data[--size];
        }


        boolean isEmpty() {

            return size
                    == 0;
        }
    }
}

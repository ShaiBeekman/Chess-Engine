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
 * Milestone 62.
 *
 * Exact dependency-aware retrograde builder for SPLIT KP-KP, including the
 * sparse history-sensitive en-passant overlay.
 *
 * External dependencies:
 *   - king / pawn / en-passant capture -> exact three-piece KPK;
 *   - promotion while the opposing pawn remains -> exact split Tier-1
 *     KQ-KP / KR-KP / KB-KP / KN-KP.
 */
public final class FourPieceTierTwoKpkpRetrogradeBuilder {

    public static final String BUILD_ID =
            "M62B-EP-HISTORY-HARDENED-V1";

    private static final int STATE_COUNT =
            FourPieceTierTwoKpkpStateIndex.STATE_COUNT;

    private static final int MAX_UNSIGNED_BYTE =
            255;

    private static final int MAX_DISTANCE =
            Short.MAX_VALUE;

    private static final FourPieceMaterialClass MATERIAL =
            FourPieceMaterialClass.split(
                    PieceType.PAWN,
                    PieceType.PAWN
            );

    private final ThreePieceTablebaseService threePieceService;
    private final FourPieceTierOneTablebaseService tierOneService;

    public FourPieceTierTwoKpkpRetrogradeBuilder() {

        this(
                new ThreePieceTablebaseService(),
                new FourPieceTierOneTablebaseService()
        );
    }

    public FourPieceTierTwoKpkpRetrogradeBuilder(
            ThreePieceTablebaseService threePieceService,
            FourPieceTierOneTablebaseService tierOneService
    ) {

        if (threePieceService == null
                || tierOneService == null) {
            throw new IllegalArgumentException(
                    "KP-KP dependency services cannot be null."
            );
        }

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

        FourPieceTierTwoKpkpCompactMoveGenerator.Buffer successors =
                new FourPieceTierTwoKpkpCompactMoveGenerator.Buffer(32);

        FourPieceTierTwoKpkpCompactMoveGenerator.Buffer predecessorValidation =
                new FourPieceTierTwoKpkpCompactMoveGenerator.Buffer(32);

        FourPieceTierTwoKpkpCompactPredecessorGenerator.Buffer predecessors =
                new FourPieceTierTwoKpkpCompactPredecessorGenerator.Buffer(32);

        BuildCounters counters =
                new BuildCounters();

        System.out.println(
                "KP-KP Tier-2 exact retrograde"
        );
        System.out.println(
                "Builder BUILD_ID: "
                        + BUILD_ID
        );
        System.out.println(
                "=============================================="
        );
        System.out.println(
                "Exact compact state count: "
                        + STATE_COUNT
        );
        System.out.println(
                "  ordinary dense states: "
                        + FourPieceTierTwoKpkpStateIndex.BASE_STATE_COUNT
        );
        System.out.println(
                "  sparse EP overlay states: "
                        + FourPieceTierTwoKpkpStateIndex.EP_STATE_COUNT
        );
        System.out.println();
        System.out.println(
                "Pass 0: classify legal states, terminals, KPK boundaries..."
        );

        for (int state = 0;
             state < STATE_COUNT;
             state++) {

            if (!FourPieceTierTwoKpkpCompactMoveGenerator
                    .isStructurallyLegal(
                            state,
                            MATERIAL
                    )) {
                continue;
            }

            outcome[state] =
                    FourPieceTablebase.UNKNOWN;

            counters.legalStates++;

            if (FourPieceTierTwoKpkpStateIndex
                    .enPassantAvailable(state)) {
                counters.legalEpStates++;
            }

            int moveCount =
                    FourPieceTierTwoKpkpCompactMoveGenerator
                            .generateLegalSuccessors(
                                    state,
                                    MATERIAL,
                                    successors
                            );

            if (moveCount > MAX_UNSIGNED_BYTE) {
                throw new IllegalStateException(
                        "KP-KP state has too many legal moves: "
                                + moveCount
                );
            }

            remaining[state] =
                    (byte) moveCount;

            if (moveCount == 0) {

                if (isSideToMoveInCheck(state)) {

                    outcome[state] =
                            FourPieceTablebase.LOSS;

                    distance[state] =
                            0;

                    counters.terminalLosses++;

                } else {

                    outcome[state] =
                            FourPieceTablebase.DRAW;

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
                        == FourPieceTierTwoKpkpCompactMoveGenerator
                        .BOUNDARY_NONE) {

                    counters.sameClassEdges++;

                    if (FourPieceTierTwoKpkpStateIndex
                            .enPassantAvailable(
                                    successors.state(i)
                            )) {
                        counters.epChildEdges++;
                    }

                    continue;
                }

                if (boundary
                        == FourPieceTierTwoKpkpCompactMoveGenerator
                        .BOUNDARY_TIER_ONE_PROMOTION) {

                    counters.promotionEdges++;
                    continue;
                }

                if (boundary
                        != FourPieceTierTwoKpkpCompactMoveGenerator
                        .BOUNDARY_THREE_PIECE) {

                    throw new IllegalStateException(
                            "Unexpected KP-KP boundary type: "
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

        printPassZero(counters);

        PieceType[] promotionTypes = {
                PieceType.QUEEN,
                PieceType.ROOK,
                PieceType.BISHOP,
                PieceType.KNIGHT
        };

        for (PieceType promotionType :
                promotionTypes) {

            System.out.println(
                    "Tier-1 split promotion dependency pass: "
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

                /*
                 * Only the side to move can promote. Skip states whose mover pawn
                 * is not on rank 7 (WHITE) / rank 2 (BLACK). This keeps the four
                 * dependency passes much smaller than a full legal-state rescan.
                 */
                int moverPawn =
                        FourPieceTierTwoKpkpStateIndex.blackToMove(state)
                                ? FourPieceTierTwoKpkpStateIndex.blackPawn(state)
                                : FourPieceTierTwoKpkpStateIndex.whitePawn(state);

                int moverRank =
                        moverPawn >>> 3;

                if (FourPieceTierTwoKpkpStateIndex.blackToMove(state)
                        ? moverRank != 1
                        : moverRank != 6) {
                    continue;
                }

                int moveCount =
                        FourPieceTierTwoKpkpCompactMoveGenerator
                                .generateLegalSuccessors(
                                        state,
                                        MATERIAL,
                                        successors
                                );

                for (int i = 0;
                     i < moveCount;
                     i++) {

                    if (successors.boundaryType(i)
                            != FourPieceTierTwoKpkpCompactMoveGenerator
                            .BOUNDARY_TIER_ONE_PROMOTION
                            || successors.promotionType(i)
                            != promotionType) {
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
                    safeShort(parentDistance);

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

            if (stateOutcome != FourPieceTablebase.WIN
                    && stateOutcome != FourPieceTablebase.LOSS) {
                continue;
            }

            int d =
                    distance[state];

            if (d < 0) {
                throw new IllegalStateException(
                        "Solved KP-KP seed has no DTM: "
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

            int predecessorCount =
                    FourPieceTierTwoKpkpCompactPredecessorGenerator
                            .generatePredecessors(
                                    child,
                                    MATERIAL,
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
                                safeShort(candidateDistance);

                        queue.add(
                                parent,
                                candidateDistance
                        );

                    } else if (parentOutcome
                            == FourPieceTablebase.WIN
                            && candidateDistance
                            < distance[parent]) {

                        distance[parent] =
                                safeShort(candidateDistance);

                        queue.add(
                                parent,
                                candidateDistance
                        );

                    } else if (parentOutcome
                            == FourPieceTablebase.LOSS) {

                        throw new IllegalStateException(
                                "Retrograde contradiction: LOSS KP-KP parent "
                                        + parent
                                        + " has LOSS child "
                                        + child
                        );
                    }

                    continue;
                }

                if (childOutcome
                        != FourPieceTablebase.WIN) {
                    throw new IllegalStateException(
                            "Only solved WIN/LOSS states may propagate."
                    );
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

                if (remainingMoves <= 0) {
                    throw new IllegalStateException(
                            "KP-KP remaining-count underflow at state "
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
                            safeShort(queuedDistance);
                }

                if (remainingMoves == 0) {

                    int parentDistance =
                            (maximumWinningChildDistance[parent]
                                    & 0xFFFF)
                                    + 1;

                    outcome[parent] =
                            FourPieceTablebase.LOSS;

                    distance[parent] =
                            safeShort(parentDistance);

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
                                "Unexpected KP-KP outcome code at state "
                                        + state
                        );
            }
        }

        if (wins + losses + draws
                != counters.legalStates) {

            throw new IllegalStateException(
                    "KP-KP result count mismatch."
            );
        }

        tierOneService.clearCache();

        return new Result(
                MATERIAL,
                outcome,
                distance,
                counters.legalStates,
                counters.legalEpStates,
                wins,
                losses,
                draws,
                maximumDistance,
                System.currentTimeMillis() - started,
                counters.sameClassEdges,
                counters.epChildEdges,
                counters.threePieceEdges,
                counters.promotionEdges,
                counters.externalWinChildren,
                counters.externalLossChildren,
                counters.externalDrawChildren,
                predecessorEdges
        );
    }

    ExternalResult resolveBoundary(
            int parentState,
            FourPieceTierTwoKpkpCompactMoveGenerator.Buffer successors,
            int index
    ) throws IOException {

        int boundary =
                successors.boundaryType(index);

        if (boundary
                == FourPieceTierTwoKpkpCompactMoveGenerator
                .BOUNDARY_THREE_PIECE) {

            return resolveThreePieceBoundary(
                    parentState,
                    successors,
                    index
            );
        }

        if (boundary
                == FourPieceTierTwoKpkpCompactMoveGenerator
                .BOUNDARY_TIER_ONE_PROMOTION) {

            return resolveTierOnePromotionBoundary(
                    parentState,
                    successors,
                    index
            );
        }

        throw new IllegalArgumentException(
                "Requested external resolution for same-class KP-KP edge."
        );
    }

    private ExternalResult resolveThreePieceBoundary(
            int parentState,
            FourPieceTierTwoKpkpCompactMoveGenerator.Buffer successors,
            int index
    ) {

        Color pawnOwner =
                successors.survivingPawnIsWhite(index)
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
                tablebase.probe(child);

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
                            unsupportedThreePieceBoundaryMessage(
                                    parentState,
                                    successors,
                                    index,
                                    pawnOwner,
                                    child
                            )
                    );
        };
    }

    private ExternalResult resolveTierOnePromotionBoundary(
            int parentState,
            FourPieceTierTwoKpkpCompactMoveGenerator.Buffer successors,
            int index
    ) throws IOException {

        PieceType promotionType =
                successors.promotionType(index);

        Position child =
                tierOnePromotionBoundaryPosition(
                        parentState,
                        successors,
                        index,
                        promotionType
                );

        Optional<FourPieceTierOneTablebaseService.ProbeResult> optional =
                tierOneService.probe(child);

        if (optional.isEmpty()) {

            throw new IllegalStateException(
                    "Tier-1 catalog rejected legal KP-KP promotion to "
                            + promotionType
            );
        }

        FourPieceTierOneTablebaseService.ProbeResult probe =
                optional.get();

        byte result =
                probe.outcome();

        if (result != FourPieceTablebase.WIN
                && result != FourPieceTablebase.LOSS
                && result != FourPieceTablebase.DRAW) {

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

    private static String unsupportedThreePieceBoundaryMessage(
            int parentState,
            FourPieceTierTwoKpkpCompactMoveGenerator.Buffer successors,
            int index,
            Color pawnOwner,
            Position child
    ) {

        int whiteKing =
                FourPieceTierTwoKpkpStateIndex.whiteKing(parentState);

        int blackKing =
                FourPieceTierTwoKpkpStateIndex.blackKing(parentState);

        int whitePawn =
                FourPieceTierTwoKpkpStateIndex.whitePawn(parentState);

        int blackPawn =
                FourPieceTierTwoKpkpStateIndex.blackPawn(parentState);

        boolean blackToMove =
                FourPieceTierTwoKpkpStateIndex.blackToMove(parentState);

        int from =
                successors.fromSquare(index);

        int to =
                successors.toSquare(index);

        boolean kingMove =
                from == whiteKing
                        || from == blackKing;

        boolean moverWhite =
                !blackToMove;

        String captureKind;

        if (successors.isEnPassantMove(index)) {
            captureKind =
                    "EN_PASSANT";
        } else if (kingMove) {
            captureKind =
                    "KING_CAPTURE";
        } else {
            captureKind =
                    "PAWN_CAPTURE";
        }

        int childWhiteKing =
                whiteKing;

        int childBlackKing =
                blackKing;

        if (from == whiteKing) {
            childWhiteKing =
                    to;
        } else if (from == blackKing) {
            childBlackKing =
                    to;
        }

        int survivingPawn =
                successors.survivingPawnSquare(index);

        boolean pawnOnBackRank =
                (survivingPawn >>> 3) == 0
                        || (survivingPawn >>> 3) == 7;

        int childWhiteKingFile =
                childWhiteKing & 7;

        int childWhiteKingRank =
                childWhiteKing >>> 3;

        int childBlackKingFile =
                childBlackKing & 7;

        int childBlackKingRank =
                childBlackKing >>> 3;

        boolean childKingsAdjacent =
                Math.max(
                        Math.abs(
                                childWhiteKingFile
                                        - childBlackKingFile
                        ),
                        Math.abs(
                                childWhiteKingRank
                                        - childBlackKingRank
                        )
                ) <= 1;

        boolean survivingPawnAttacksWhiteKing =
                pawnAttacks(
                        survivingPawn,
                        childWhiteKing,
                        pawnOwner == Color.WHITE
                );

        boolean survivingPawnAttacksBlackKing =
                pawnAttacks(
                        survivingPawn,
                        childBlackKing,
                        pawnOwner == Color.WHITE
                );

        return "KPK dependency rejected KP-KP capture boundary."
                + "\nBuilder BUILD_ID: "
                + BUILD_ID
                + "\nparent state ID: "
                + parentState
                + "\nparent: WK="
                + algebraic(whiteKing)
                + " BK="
                + algebraic(blackKing)
                + " WP="
                + algebraic(whitePawn)
                + " BP="
                + algebraic(blackPawn)
                + " stm="
                + (blackToMove
                ? "BLACK"
                : "WHITE")
                + " ep="
                + FourPieceTierTwoKpkpStateIndex
                .enPassantAvailable(parentState)
                + "\nedge index: "
                + index
                + "\nmove: "
                + algebraic(from)
                + (kingMove
                ? "x"
                : "x")
                + algebraic(to)
                + "\ncapture kind: "
                + captureKind
                + "\nmover: "
                + (moverWhite
                ? "WHITE"
                : "BLACK")
                + "\nsurviving pawn: "
                + pawnOwner
                + " "
                + algebraic(survivingPawn)
                + "\nchild: WK="
                + algebraic(childWhiteKing)
                + " BK="
                + algebraic(childBlackKing)
                + " P="
                + algebraic(survivingPawn)
                + " pawnOwner="
                + pawnOwner
                + " stm="
                + child.getSideToMove()
                + "\nchild diagnostics:"
                + "\n  pawn on rank 1/8: "
                + pawnOnBackRank
                + "\n  kings adjacent: "
                + childKingsAdjacent
                + "\n  pawn attacks WHITE king: "
                + survivingPawnAttacksWhiteKing
                + "\n  pawn attacks BLACK king: "
                + survivingPawnAttacksBlackKing;
    }


    private static String algebraic(
            int square
    ) {

        if (square < 0
                || square >= 64) {
            return "??(" + square + ")";
        }

        return ""
                + (char) ('a' + (square & 7))
                + ((square >>> 3) + 1);
    }


    private static void applyExternalResult(
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
                        safeShort(candidate);

            } else if (outcome[parent]
                    == FourPieceTablebase.WIN
                    && candidate < distance[parent]) {

                distance[parent] =
                        safeShort(candidate);
            }

            return;
        }

        if (child.outcome()
                == FourPieceTablebase.WIN) {

            counters.externalWinChildren++;

            if (outcome[parent]
                    == FourPieceTablebase.UNKNOWN) {

                int count =
                        remainingCount(
                                remaining,
                                parent
                        );

                if (count <= 0) {
                    throw new IllegalStateException(
                            "External KP-KP remaining-count underflow at "
                                    + parent
                    );
                }

                count--;

                setRemainingCount(
                        remaining,
                        parent,
                        count
                );

                int previousMaximum =
                        maximumWinningChildDistance[parent]
                                & 0xFFFF;

                if (child.distance()
                        > previousMaximum) {

                    maximumWinningChildDistance[parent] =
                            safeShort(child.distance());
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
                "Unresolved external KP-KP dependency."
        );
    }

    private static Position threePieceBoundaryPosition(
            int parentState,
            FourPieceTierTwoKpkpCompactMoveGenerator.Buffer successors,
            int index,
            Color pawnOwner
    ) {

        int whiteKing =
                FourPieceTierTwoKpkpStateIndex.whiteKing(parentState);

        int blackKing =
                FourPieceTierTwoKpkpStateIndex.blackKing(parentState);

        int from =
                successors.fromSquare(index);

        int to =
                successors.toSquare(index);

        if (from == whiteKing) {
            whiteKing = to;
        } else if (from == blackKing) {
            blackKing = to;
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
                square(
                        successors.survivingPawnSquare(index)
                ),
                new Piece(
                        PieceType.PAWN,
                        pawnOwner
                )
        );

        return childPosition(
                board,
                childSideToMove(parentState)
        );
    }

    private static Position tierOnePromotionBoundaryPosition(
            int parentState,
            FourPieceTierTwoKpkpCompactMoveGenerator.Buffer successors,
            int index,
            PieceType promotionType
    ) {

        int whiteKing =
                FourPieceTierTwoKpkpStateIndex.whiteKing(parentState);

        int blackKing =
                FourPieceTierTwoKpkpStateIndex.blackKing(parentState);

        Color promotedOwner =
                successors.promotedPawnIsWhite(index)
                        ? Color.WHITE
                        : Color.BLACK;

        Color remainingPawnOwner =
                promotedOwner == Color.WHITE
                        ? Color.BLACK
                        : Color.WHITE;

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
                square(successors.toSquare(index)),
                new Piece(
                        promotionType,
                        promotedOwner
                )
        );

        board.setPiece(
                square(
                        successors.remainingPawnSquare(index)
                ),
                new Piece(
                        PieceType.PAWN,
                        remainingPawnOwner
                )
        );

        return childPosition(
                board,
                childSideToMove(parentState)
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

        return FourPieceTierTwoKpkpStateIndex
                .blackToMove(parentState)
                ? Color.WHITE
                : Color.BLACK;
    }

    private static boolean isSideToMoveInCheck(
            int state
    ) {

        boolean blackToMove =
                FourPieceTierTwoKpkpStateIndex.blackToMove(state);

        int sideKing =
                blackToMove
                        ? FourPieceTierTwoKpkpStateIndex.blackKing(state)
                        : FourPieceTierTwoKpkpStateIndex.whiteKing(state);

        int enemyPawn =
                blackToMove
                        ? FourPieceTierTwoKpkpStateIndex.whitePawn(state)
                        : FourPieceTierTwoKpkpStateIndex.blackPawn(state);

        return pawnAttacks(
                enemyPawn,
                sideKing,
                blackToMove
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

        return remaining[state] & 0xFF;
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
                    "KP-KP DTM exceeds short range: "
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
                "  legal EP states: "
                        + counters.legalEpStates
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
                "  EP-enabled child edges: "
                        + counters.epChildEdges
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
            byte[] outcome,
            short[] distance,
            long legalStates,
            long legalEpStates,
            long wins,
            long losses,
            long draws,
            int maximumDistance,
            long buildMillis,
            long sameClassEdges,
            long epChildEdges,
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
                        "KP-KP retrograde result data cannot be null."
                );
            }

            if (outcome.length != STATE_COUNT
                    || distance.length != STATE_COUNT) {

                throw new IllegalArgumentException(
                        "KP-KP retrograde result arrays have incorrect size."
                );
            }

            if (wins + losses + draws
                    != legalStates) {

                throw new IllegalArgumentException(
                        "KP-KP retrograde result counts do not balance."
                );
            }
        }


        public FourPieceTierTwoKpkpTablebaseCodec.Tablebase toTablebase() {

            return new FourPieceTierTwoKpkpTablebaseCodec.Tablebase(
                    outcome,
                    distance,
                    legalStates,
                    legalEpStates,
                    wins,
                    losses,
                    draws,
                    maximumDistance
            );
        }
    }

    record ExternalResult(
            byte outcome,
            int distance
    ) {
    }

    private static final class BuildCounters {

        long legalStates;
        long legalEpStates;
        long terminalLosses;
        long terminalDraws;
        long sameClassEdges;
        long epChildEdges;
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

            bucket.add(state);

            size++;

            if (distance < nextDistance) {
                nextDistance = distance;
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

            while (nextDistance <= MAX_DISTANCE) {

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
                    buckets[nextDistance] = null;
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

        void add(int value) {

            if (size == data.length) {

                data =
                        Arrays.copyOf(
                                data,
                                data.length * 2
                        );
            }

            data[size++] = value;
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

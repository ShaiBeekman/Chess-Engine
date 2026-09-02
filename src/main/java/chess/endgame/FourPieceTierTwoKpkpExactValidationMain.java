package main.java.chess.endgame;

import main.java.chess.model.PieceType;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Random;

/**
 * Milestone 62 exact KP-KP solve + validation gate.
 *
 * Validation layers:
 *   1. compact-index / M60 move-generator regression;
 *   2. full exact-domain color symmetry, including EP overlay states;
 *   3. sampled complete Bellman recurrence including KPK and Tier-1 boundaries;
 *   4. stable SHA-256 content digest.
 */
public final class FourPieceTierTwoKpkpExactValidationMain {

    private static final FourPieceMaterialClass KPKP =
            FourPieceMaterialClass.split(
                    PieceType.PAWN,
                    PieceType.PAWN
            );

    private static final int DEFAULT_REGRESSION_SAMPLES =
            250_000;

    private static final int DEFAULT_BELLMAN_SAMPLES =
            1_000_000;

    private FourPieceTierTwoKpkpExactValidationMain() {
    }

    public static void main(
            String[] args
    ) throws Exception {

        if (args.length > 2) {
            throw new IllegalArgumentException(
                    "Usage: FourPieceTierTwoKpkpExactValidationMain "
                            + "[bellman-samples] [regression-samples]"
            );
        }

        int bellmanSamples =
                args.length >= 1
                        ? Integer.parseInt(args[0])
                        : DEFAULT_BELLMAN_SAMPLES;

        int regressionSamples =
                args.length >= 2
                        ? Integer.parseInt(args[1])
                        : DEFAULT_REGRESSION_SAMPLES;

        if (bellmanSamples < 1
                || regressionSamples < 1) {

            throw new IllegalArgumentException(
                    "Validation sample counts must be positive."
            );
        }

        System.out.println(
                "KP-KP exact solve / validation gate"
        );
        System.out.println(
                "==================================="
        );

        verifyEpHistoryHardening();

        verifyCompactRegression(
                regressionSamples,
                0x4B504B504D363201L
        );

        System.out.println();
        System.out.println(
                "Building exact KP-KP..."
        );

        FourPieceTierTwoKpkpRetrogradeBuilder builder =
                new FourPieceTierTwoKpkpRetrogradeBuilder();

        FourPieceTierTwoKpkpRetrogradeBuilder.Result result =
                builder.build();

        validateCounts(result);

        long symmetryStarted =
                System.nanoTime();

        SymmetryCounts symmetry =
                validateFullColorSymmetry(result);

        System.out.println();
        System.out.println(
                "Full exact-domain color symmetry"
        );
        System.out.println(
                "================================"
        );
        System.out.println(
                "States compared: "
                        + symmetry.statesCompared()
        );
        System.out.println(
                "Legal states compared: "
                        + symmetry.legalStatesCompared()
        );
        System.out.println(
                "EP states compared: "
                        + symmetry.epStatesCompared()
        );
        System.out.println(
                "Outcome mismatches: 0"
        );
        System.out.println(
                "Distance mismatches: 0"
        );
        System.out.printf(
                "Elapsed: %.3f sec%n",
                (System.nanoTime()
                        - symmetryStarted)
                        / 1_000_000_000.0
        );
        System.out.println(
                "PASSED"
        );

        BellmanCounts bellman =
                validateBellman(
                        result,
                        builder,
                        bellmanSamples,
                        0x4B504B504D363202L
                );

        printBellman(bellman);

        String digest =
                digest(
                        result.outcome(),
                        result.distance()
                );

        System.out.println();
        System.out.println(
                "Solved-tablebase metadata"
        );
        System.out.println(
                "========================="
        );
        System.out.println(
                "Exact state domain: "
                        + FourPieceTierTwoKpkpStateIndex.STATE_COUNT
        );
        System.out.println(
                "Legal states: "
                        + result.legalStates()
        );
        System.out.println(
                "Legal EP states: "
                        + result.legalEpStates()
        );
        System.out.println(
                "WIN: "
                        + result.wins()
        );
        System.out.println(
                "LOSS: "
                        + result.losses()
        );
        System.out.println(
                "DRAW: "
                        + result.draws()
        );
        System.out.println(
                "Maximum DTM: "
                        + result.maximumDistance()
        );
        System.out.println(
                "Same-class edges: "
                        + result.sameClassEdges()
        );
        System.out.println(
                "EP-enabled child edges: "
                        + result.epChildEdges()
        );
        System.out.println(
                "KPK boundary edges: "
                        + result.threePieceEdges()
        );
        System.out.println(
                "Tier-1 promotion edges: "
                        + result.promotionEdges()
        );
        System.out.printf(
                "Build time: %.3f sec%n",
                result.buildMillis()
                        / 1000.0
        );
        System.out.println(
                "Digest: "
                        + digest
        );

        System.out.println();
        System.out.println(
                "KP-KP EXACT SOLVE / VALIDATION / COLOR-SYMMETRY GATE PASSED"
        );
        System.out.println(
                "NEXT: PERSIST KP-KP AND ADD FINAL TIER-2 RUNTIME ROUTING"
        );
    }

    /**
     * Proves the new allocation-free compact generator has exactly the same
     * edge semantics and ordering as the already-passed M60 State generator.
     */
    private static void verifyEpHistoryHardening() {

        int impossibleWhiteCrossedBase =
                FourPieceGenericPrimitiveState.encode(
                        sq("a1"),
                        sq("a3"),
                        sq("a4"),
                        sq("b4"),
                        true
                );

        requireFalse(
                "WHITE a2-a4 history with BLACK king on crossed square a3",
                FourPieceTierTwoKpkpPrimitiveState.canCarryEnPassant(
                        impossibleWhiteCrossedBase
                )
        );

        int impossibleBlackCrossedBase =
                FourPieceGenericPrimitiveState.encode(
                        sq("d6"),
                        sq("h8"),
                        sq("e5"),
                        sq("d5"),
                        false
                );

        requireFalse(
                "BLACK d7-d5 history with WHITE king on crossed square d6",
                FourPieceTierTwoKpkpPrimitiveState.canCarryEnPassant(
                        impossibleBlackCrossedBase
                )
        );

        int impossibleWhiteOriginBase =
                FourPieceGenericPrimitiveState.encode(
                        sq("a2"),
                        sq("h8"),
                        sq("a4"),
                        sq("b4"),
                        true
                );

        requireFalse(
                "WHITE a2-a4 history with king on origin square a2",
                FourPieceTierTwoKpkpPrimitiveState.canCarryEnPassant(
                        impossibleWhiteOriginBase
                )
        );

        int impossibleBlackOriginBase =
                FourPieceGenericPrimitiveState.encode(
                        sq("a1"),
                        sq("d7"),
                        sq("e5"),
                        sq("d5"),
                        false
                );

        requireFalse(
                "BLACK d7-d5 history with king on origin square d7",
                FourPieceTierTwoKpkpPrimitiveState.canCarryEnPassant(
                        impossibleBlackOriginBase
                )
        );

        int validWhiteBase =
                FourPieceGenericPrimitiveState.encode(
                        sq("a1"),
                        sq("h8"),
                        sq("a4"),
                        sq("b4"),
                        true
                );

        requireTrue(
                "valid WHITE a2-a4 EP history",
                FourPieceTierTwoKpkpPrimitiveState.canCarryEnPassant(
                        validWhiteBase
                )
        );

        int validBlackBase =
                FourPieceGenericPrimitiveState.encode(
                        sq("a1"),
                        sq("h8"),
                        sq("e5"),
                        sq("d5"),
                        false
                );

        requireTrue(
                "valid BLACK d7-d5 EP history",
                FourPieceTierTwoKpkpPrimitiveState.canCarryEnPassant(
                        validBlackBase
                )
        );

        System.out.println();
        System.out.println(
                "M62B EP-history legality hardening"
        );
        System.out.println(
                "================================="
        );
        System.out.println(
                "Crossed-square king blockers: REJECTED"
        );
        System.out.println(
                "Origin-square king blockers: REJECTED"
        );
        System.out.println(
                "Valid WHITE/BLACK double-push histories: ACCEPTED"
        );
        System.out.println(
                "PASSED"
        );
    }


    private static void verifyCompactRegression(
            int samples,
            long seed
    ) {

        Random random =
                new Random(seed);

        FourPieceTierTwoKpkpPrimitiveMoveGenerator.Buffer oldMoves =
                new FourPieceTierTwoKpkpPrimitiveMoveGenerator.Buffer(32);

        FourPieceTierTwoKpkpCompactMoveGenerator.Buffer compactMoves =
                new FourPieceTierTwoKpkpCompactMoveGenerator.Buffer(32);

        FourPieceTierTwoKpkpPrimitiveMoveGenerator.Buffer oldPredForward =
                new FourPieceTierTwoKpkpPrimitiveMoveGenerator.Buffer(32);

        FourPieceTierTwoKpkpCompactMoveGenerator.Buffer compactPredForward =
                new FourPieceTierTwoKpkpCompactMoveGenerator.Buffer(32);

        FourPieceTierTwoKpkpPrimitivePredecessorGenerator.Buffer oldPredecessors =
                new FourPieceTierTwoKpkpPrimitivePredecessorGenerator.Buffer(32);

        FourPieceTierTwoKpkpCompactPredecessorGenerator.Buffer compactPredecessors =
                new FourPieceTierTwoKpkpCompactPredecessorGenerator.Buffer(32);

        int checked =
                0;

        long attempts =
                0;

        long edges =
                0;

        long predecessorEdges =
                0;

        long epStates =
                0;

        while (checked < samples) {

            attempts++;

            int stateId =
                    randomExactState(random);

            boolean compactLegal =
                    FourPieceTierTwoKpkpCompactMoveGenerator
                            .isStructurallyLegal(
                                    stateId,
                                    KPKP
                            );

            /*
             * Raw compact EP overlay IDs deliberately include impossible
             * history slots. Those are INVALID array entries and cannot be
             * materialized as an M60 State record.
             */
            if (!compactLegal) {
                continue;
            }

            FourPieceTierTwoKpkpPrimitiveState.State oldState =
                    FourPieceTierTwoKpkpStateIndex.state(stateId);

            boolean oldLegal =
                    FourPieceTierTwoKpkpPrimitiveMoveGenerator
                            .isStructurallyLegal(
                                    oldState,
                                    KPKP
                            );

            if (!oldLegal) {

                throw new IllegalStateException(
                        "M62 compact state is legal but M60 rejects it at "
                                + describe(stateId)
                );
            }

            int oldCount =
                    FourPieceTierTwoKpkpPrimitiveMoveGenerator
                            .generateLegalSuccessors(
                                    oldState,
                                    KPKP,
                                    oldMoves
                            );

            int compactCount =
                    FourPieceTierTwoKpkpCompactMoveGenerator
                            .generateLegalSuccessors(
                                    stateId,
                                    KPKP,
                                    compactMoves
                            );

            if (oldCount != compactCount) {

                throw new IllegalStateException(
                        "M62 compact move count differs from M60 at "
                                + describe(stateId)
                                + ": "
                                + oldCount
                                + " != "
                                + compactCount
                );
            }

            for (int i = 0; i < oldCount; i++) {

                compareEdge(
                        stateId,
                        i,
                        oldMoves,
                        compactMoves
                );

                edges++;
            }

            int oldPredecessorCount =
                    FourPieceTierTwoKpkpPrimitivePredecessorGenerator
                            .generatePredecessors(
                                    oldState,
                                    KPKP,
                                    oldPredecessors,
                                    oldPredForward
                            );

            int compactPredecessorCount =
                    FourPieceTierTwoKpkpCompactPredecessorGenerator
                            .generatePredecessors(
                                    stateId,
                                    KPKP,
                                    compactPredecessors,
                                    compactPredForward
                            );

            if (oldPredecessorCount
                    != compactPredecessorCount) {

                throw new IllegalStateException(
                        "M62 compact predecessor count differs from M61 at "
                                + describe(stateId)
                                + ": "
                                + oldPredecessorCount
                                + " != "
                                + compactPredecessorCount
                );
            }

            for (int i = 0;
                 i < oldPredecessorCount;
                 i++) {

                int oldParent =
                        FourPieceTierTwoKpkpStateIndex.of(
                                oldPredecessors.state(i)
                        );

                if (!contains(
                        compactPredecessors,
                        compactPredecessorCount,
                        oldParent
                )) {

                    throw new IllegalStateException(
                            "M62 compact predecessor set differs from M61 at "
                                    + describe(stateId)
                                    + "\nmissing parent="
                                    + describe(oldParent)
                    );
                }

                predecessorEdges++;
            }

            if (FourPieceTierTwoKpkpStateIndex
                    .enPassantAvailable(stateId)) {
                epStates++;
            }

            checked++;
        }

        System.out.println();
        System.out.println(
                "M60 -> M62 compact-generator regression"
        );
        System.out.println(
                "======================================="
        );
        System.out.println(
                "Legal states checked: "
                        + checked
        );
        System.out.println(
                "Random attempts: "
                        + attempts
        );
        System.out.println(
                "EP states checked: "
                        + epStates
        );
        System.out.println(
                "Successor edges compared: "
                        + edges
        );
        System.out.println(
                "Predecessor edges compared: "
                        + predecessorEdges
        );
        System.out.println(
                "Mismatches: 0"
        );
        System.out.println(
                "PASSED"
        );
    }

    private static void compareEdge(
            int parent,
            int index,
            FourPieceTierTwoKpkpPrimitiveMoveGenerator.Buffer oldMoves,
            FourPieceTierTwoKpkpCompactMoveGenerator.Buffer compactMoves
    ) {

        int oldBoundary =
                oldMoves.boundaryType(index);

        int compactBoundary =
                compactMoves.boundaryType(index);

        if (oldBoundary != compactBoundary
                || oldMoves.fromSquare(index)
                != compactMoves.fromSquare(index)
                || oldMoves.toSquare(index)
                != compactMoves.toSquare(index)
                || oldMoves.isEnPassantMove(index)
                != compactMoves.isEnPassantMove(index)) {

            regressionFail(
                    "basic edge metadata",
                    parent,
                    index
            );
        }

        if (oldBoundary
                == FourPieceTierTwoKpkpPrimitiveMoveGenerator
                .BOUNDARY_NONE) {

            int oldChild =
                    FourPieceTierTwoKpkpStateIndex.of(
                            oldMoves.state(index)
                    );

            if (oldChild
                    != compactMoves.state(index)) {

                regressionFail(
                        "same-class child",
                        parent,
                        index
                );
            }

            return;
        }

        if (oldBoundary
                == FourPieceTierTwoKpkpPrimitiveMoveGenerator
                .BOUNDARY_THREE_PIECE) {

            if (oldMoves.survivingPawnIsWhite(index)
                    != compactMoves.survivingPawnIsWhite(index)
                    || oldMoves.survivingPieceSquare(index)
                    != compactMoves.survivingPawnSquare(index)) {

                regressionFail(
                        "KPK boundary metadata",
                        parent,
                        index
                );
            }

            return;
        }

        if (oldBoundary
                == FourPieceTierTwoKpkpPrimitiveMoveGenerator
                .BOUNDARY_TIER_ONE_PROMOTION) {

            if (oldMoves.promotionType(index)
                    != compactMoves.promotionType(index)
                    || oldMoves.promotedPawnIsWhite(index)
                    != compactMoves.promotedPawnIsWhite(index)
                    || oldMoves.remainingPawnSquare(index)
                    != compactMoves.remainingPawnSquare(index)) {

                regressionFail(
                        "Tier-1 promotion metadata",
                        parent,
                        index
                );
            }

            return;
        }

        regressionFail(
                "unknown boundary",
                parent,
                index
        );
    }

    private static void regressionFail(
            String why,
            int parent,
            int index
    ) {

        throw new IllegalStateException(
                "M60/M62 compact regression mismatch: "
                        + why
                        + "\nparent="
                        + describe(parent)
                        + "\nedge index="
                        + index
        );
    }

    private static boolean contains(
            FourPieceTierTwoKpkpCompactPredecessorGenerator.Buffer buffer,
            int count,
            int state
    ) {

        for (int i = 0;
             i < count;
             i++) {

            if (buffer.state(i) == state) {
                return true;
            }
        }

        return false;
    }

    private static void validateCounts(
            FourPieceTierTwoKpkpRetrogradeBuilder.Result result
    ) {

        if (!KPKP.equals(result.material())) {
            throw new IllegalStateException(
                    "KP-KP result material mismatch."
            );
        }

        if (result.wins()
                + result.losses()
                + result.draws()
                != result.legalStates()) {

            throw new IllegalStateException(
                    "KP-KP solved counts do not balance."
            );
        }

        if (result.legalEpStates() <= 0) {
            throw new IllegalStateException(
                    "KP-KP solve contains no legal EP states."
            );
        }
    }

    private static SymmetryCounts validateFullColorSymmetry(
            FourPieceTierTwoKpkpRetrogradeBuilder.Result result
    ) {

        long states =
                0;

        long legal =
                0;

        long ep =
                0;

        for (int state = 0;
             state < FourPieceTierTwoKpkpStateIndex.STATE_COUNT;
             state++) {

            int reversed =
                    FourPieceTierTwoKpkpStateIndex
                            .colorReverse(state);

            byte firstOutcome =
                    result.outcome()[state];

            byte secondOutcome =
                    result.outcome()[reversed];

            if (firstOutcome != secondOutcome) {

                throw new IllegalStateException(
                        "KP-KP color-symmetry outcome mismatch:"
                                + "\n  "
                                + describe(state)
                                + "\n  "
                                + describe(reversed)
                );
            }

            if (result.distance()[state]
                    != result.distance()[reversed]) {

                throw new IllegalStateException(
                        "KP-KP color-symmetry DTM mismatch:"
                                + "\n  "
                                + describe(state)
                                + "\n  "
                                + describe(reversed)
                );
            }

            if (firstOutcome
                    != FourPieceTablebase.INVALID) {
                legal++;
            }

            if (FourPieceTierTwoKpkpStateIndex
                    .enPassantAvailable(state)) {
                ep++;
            }

            states++;
        }

        return new SymmetryCounts(
                states,
                legal,
                ep
        );
    }

    private static BellmanCounts validateBellman(
            FourPieceTierTwoKpkpRetrogradeBuilder.Result result,
            FourPieceTierTwoKpkpRetrogradeBuilder builder,
            int samples,
            long seed
    ) throws IOException {

        Random random =
                new Random(seed);

        FourPieceTierTwoKpkpCompactMoveGenerator.Buffer moves =
                new FourPieceTierTwoKpkpCompactMoveGenerator.Buffer(32);

        int checked =
                0;

        long attempts =
                0;

        long edges =
                0;

        long sameClassEdges =
                0;

        long externalEdges =
                0;

        long epStates =
                0;

        long wins =
                0;

        long losses =
                0;

        long draws =
                0;

        long terminals =
                0;

        while (checked < samples) {

            attempts++;

            int state =
                    randomExactState(random);

            byte outcome =
                    result.outcome()[state];

            if (outcome
                    == FourPieceTablebase.INVALID) {
                continue;
            }

            int moveCount =
                    FourPieceTierTwoKpkpCompactMoveGenerator
                            .generateLegalSuccessors(
                                    state,
                                    KPKP,
                                    moves
                            );

            if (FourPieceTierTwoKpkpStateIndex
                    .enPassantAvailable(state)) {
                epStates++;
            }

            if (moveCount == 0) {

                terminals++;

                boolean check =
                        isSideToMoveInCheck(state);

                if (check) {

                    if (outcome != FourPieceTablebase.LOSS
                            || result.distance()[state] != 0) {

                        bellmanFail(
                                "checkmate terminal mismatch",
                                state,
                                outcome,
                                result.distance()[state]
                        );
                    }

                } else if (outcome
                        != FourPieceTablebase.DRAW) {

                    bellmanFail(
                            "stalemate terminal mismatch",
                            state,
                            outcome,
                            result.distance()[state]
                    );
                }

                checked++;
                continue;
            }

            int lossChildren =
                    0;

            int winChildren =
                    0;

            int drawChildren =
                    0;

            int minimumLossDistance =
                    Integer.MAX_VALUE;

            int maximumWinDistance =
                    Integer.MIN_VALUE;

            for (int i = 0;
                 i < moveCount;
                 i++) {

                byte childOutcome;
                int childDistance;

                if (moves.boundaryType(i)
                        == FourPieceTierTwoKpkpCompactMoveGenerator
                        .BOUNDARY_NONE) {

                    int child =
                            moves.state(i);

                    childOutcome =
                            result.outcome()[child];

                    childDistance =
                            result.distance()[child];

                    sameClassEdges++;

                } else {

                    FourPieceTierTwoKpkpRetrogradeBuilder.ExternalResult external =
                            builder.resolveBoundary(
                                    state,
                                    moves,
                                    i
                            );

                    childOutcome =
                            external.outcome();

                    childDistance =
                            external.distance();

                    externalEdges++;
                }

                edges++;

                if (childOutcome
                        == FourPieceTablebase.LOSS) {

                    lossChildren++;

                    minimumLossDistance =
                            Math.min(
                                    minimumLossDistance,
                                    childDistance
                            );

                } else if (childOutcome
                        == FourPieceTablebase.WIN) {

                    winChildren++;

                    maximumWinDistance =
                            Math.max(
                                    maximumWinDistance,
                                    childDistance
                            );

                } else if (childOutcome
                        == FourPieceTablebase.DRAW) {

                    drawChildren++;

                } else {

                    bellmanFail(
                            "child unresolved/invalid",
                            state,
                            childOutcome,
                            (short) childDistance
                    );
                }
            }

            short distance =
                    result.distance()[state];

            if (outcome
                    == FourPieceTablebase.WIN) {

                wins++;

                if (lossChildren == 0
                        || distance
                        != minimumLossDistance + 1) {

                    bellmanFail(
                            "WIN recurrence mismatch",
                            state,
                            outcome,
                            distance
                    );
                }

            } else if (outcome
                    == FourPieceTablebase.LOSS) {

                losses++;

                if (winChildren != moveCount
                        || distance
                        != maximumWinDistance + 1) {

                    bellmanFail(
                            "LOSS recurrence mismatch",
                            state,
                            outcome,
                            distance
                    );
                }

            } else if (outcome
                    == FourPieceTablebase.DRAW) {

                draws++;

                if (lossChildren != 0
                        || drawChildren == 0
                        || distance != -1) {

                    bellmanFail(
                            "DRAW recurrence mismatch",
                            state,
                            outcome,
                            distance
                    );
                }

            } else {

                bellmanFail(
                        "legal state unresolved/invalid",
                        state,
                        outcome,
                        distance
                );
            }

            checked++;
        }

        return new BellmanCounts(
                checked,
                attempts,
                edges,
                sameClassEdges,
                externalEdges,
                epStates,
                wins,
                losses,
                draws,
                terminals
        );
    }

    private static void printBellman(
            BellmanCounts counts
    ) {

        System.out.println();
        System.out.println(
                "Complete Bellman recurrence"
        );
        System.out.println(
                "==========================="
        );
        System.out.println(
                "Legal states checked: "
                        + counts.legalStatesChecked()
        );
        System.out.println(
                "Random attempts: "
                        + counts.randomAttempts()
        );
        System.out.println(
                "EP states checked: "
                        + counts.epStatesChecked()
        );
        System.out.println(
                "All child edges checked: "
                        + counts.edgesChecked()
        );
        System.out.println(
                "Same-class edges checked: "
                        + counts.sameClassEdgesChecked()
        );
        System.out.println(
                "External dependency edges checked: "
                        + counts.externalEdgesChecked()
        );
        System.out.println(
                "WIN states checked: "
                        + counts.winsChecked()
        );
        System.out.println(
                "LOSS states checked: "
                        + counts.lossesChecked()
        );
        System.out.println(
                "DRAW states checked: "
                        + counts.drawsChecked()
        );
        System.out.println(
                "Terminal states checked: "
                        + counts.terminalStatesChecked()
        );
        System.out.println(
                "Bellman mismatches: 0"
        );
        System.out.println(
                "PASSED"
        );
    }

    private static int randomExactState(
            Random random
    ) {

        /*
         * Sample the compact array domain directly. The sparse EP overlay then
         * naturally appears at its true exact-domain frequency.
         */
        return random.nextInt(
                FourPieceTierTwoKpkpStateIndex.STATE_COUNT
        );
    }

    private static boolean isSideToMoveInCheck(
            int state
    ) {

        boolean blackToMove =
                FourPieceTierTwoKpkpStateIndex.blackToMove(state);

        int king =
                blackToMove
                        ? FourPieceTierTwoKpkpStateIndex.blackKing(state)
                        : FourPieceTierTwoKpkpStateIndex.whiteKing(state);

        int enemyPawn =
                blackToMove
                        ? FourPieceTierTwoKpkpStateIndex.whitePawn(state)
                        : FourPieceTierTwoKpkpStateIndex.blackPawn(state);

        return pawnAttacks(
                enemyPawn,
                king,
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

    private static String digest(
            byte[] outcome,
            short[] distance
    ) throws NoSuchAlgorithmException {

        MessageDigest digest =
                MessageDigest.getInstance(
                        "SHA-256"
                );

        digest.update(outcome);

        byte[] buffer =
                new byte[1 << 20];

        int used =
                0;

        for (short value :
                distance) {

            buffer[used++] =
                    (byte) ((value >>> 8) & 0xFF);

            buffer[used++] =
                    (byte) (value & 0xFF);

            if (used == buffer.length) {

                digest.update(
                        buffer,
                        0,
                        used
                );

                used =
                        0;
            }
        }

        if (used != 0) {
            digest.update(
                    buffer,
                    0,
                    used
            );
        }

        return HexFormat.of()
                .formatHex(
                        digest.digest()
                );
    }

    private static void bellmanFail(
            String why,
            int state,
            byte outcome,
            short distance
    ) {

        throw new IllegalStateException(
                "KP-KP Bellman validation failed: "
                        + why
                        + "\n  state: "
                        + describe(state)
                        + "\n  outcome: "
                        + outcomeName(outcome)
                        + "\n  DTM: "
                        + distance
        );
    }

    private static String describe(
            int state
    ) {

        return "WK="
                + algebraic(
                FourPieceTierTwoKpkpStateIndex.whiteKing(state)
        )
                + " BK="
                + algebraic(
                FourPieceTierTwoKpkpStateIndex.blackKing(state)
        )
                + " WP="
                + algebraic(
                FourPieceTierTwoKpkpStateIndex.whitePawn(state)
        )
                + " BP="
                + algebraic(
                FourPieceTierTwoKpkpStateIndex.blackPawn(state)
        )
                + " stm="
                + (FourPieceTierTwoKpkpStateIndex.blackToMove(state)
                ? "BLACK"
                : "WHITE")
                + " ep="
                + FourPieceTierTwoKpkpStateIndex.enPassantAvailable(state)
                + " ["
                + state
                + "]";
    }

    private static int sq(
            String algebraic
    ) {

        int file =
                algebraic.charAt(0) - 'a';

        int rank =
                algebraic.charAt(1) - '1';

        return rank * 8 + file;
    }

    private static void requireTrue(
            String name,
            boolean value
    ) {

        if (!value) {
            throw new IllegalStateException(
                    "M62B expected TRUE: "
                            + name
            );
        }
    }

    private static void requireFalse(
            String name,
            boolean value
    ) {

        if (value) {
            throw new IllegalStateException(
                    "M62B expected FALSE: "
                            + name
            );
        }
    }


    private static String algebraic(
            int square
    ) {

        return ""
                + (char) ('a' + (square & 7))
                + ((square >>> 3) + 1);
    }

    private static String outcomeName(
            byte outcome
    ) {

        if (outcome == FourPieceTablebase.WIN) {
            return "WIN";
        }

        if (outcome == FourPieceTablebase.LOSS) {
            return "LOSS";
        }

        if (outcome == FourPieceTablebase.DRAW) {
            return "DRAW";
        }

        if (outcome == FourPieceTablebase.INVALID) {
            return "INVALID";
        }

        return "UNKNOWN";
    }

    private record SymmetryCounts(
            long statesCompared,
            long legalStatesCompared,
            long epStatesCompared
    ) {
    }

    private record BellmanCounts(
            int legalStatesChecked,
            long randomAttempts,
            long edgesChecked,
            long sameClassEdgesChecked,
            long externalEdgesChecked,
            long epStatesChecked,
            long winsChecked,
            long lossesChecked,
            long drawsChecked,
            long terminalStatesChecked
    ) {
    }
}

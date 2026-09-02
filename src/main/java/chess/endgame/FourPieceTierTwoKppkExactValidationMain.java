package main.java.chess.endgame;

import main.java.chess.model.PieceType;

import java.util.Random;

/**
 * Milestone 58 exact KPPK solve / validation / color-symmetry gate.
 *
 * Builds both pawn-owner orientations independently and validates:
 *
 *   1. metadata equality;
 *   2. full raw-state color symmetry, with identical-pawn canonicalization;
 *   3. sampled Bellman recurrence on internal-only KPPK states;
 *   4. stable content digests for both independently built orientations.
 *
 * Persistence/runtime routing intentionally remains M59.
 */
public final class FourPieceTierTwoKppkExactValidationMain {

    private static final int DEFAULT_BELLMAN_SAMPLES =
            1_000_000;

    private static final FourPieceMaterialClass KPPK =
            FourPieceMaterialClass.sameSide(
                    PieceType.PAWN,
                    PieceType.PAWN
            );


    private FourPieceTierTwoKppkExactValidationMain() {
    }


    public static void main(
            String[] args
    ) throws Exception {

        int samples =
                args.length == 0
                        ? DEFAULT_BELLMAN_SAMPLES
                        : Integer.parseInt(
                        args[0]
                );

        if (args.length > 1) {

            throw new IllegalArgumentException(
                    "Usage: FourPieceTierTwoKppkExactValidationMain [bellman-samples]"
            );
        }

        if (samples < 1) {

            throw new IllegalArgumentException(
                    "Bellman sample count must be positive."
            );
        }

        System.out.println(
                "KPPK exact solve / validation / color-symmetry gate"
        );

        System.out.println(
                "=================================================="
        );

        System.out.println();
        System.out.println(
                "Building pawn-owner-WHITE KPPK..."
        );

        FourPieceTierTwoKppkRetrogradeBuilder.Result white =
                new FourPieceTierTwoKppkRetrogradeBuilder(
                        true
                ).build();

        System.out.println();
        System.out.println(
                "Building pawn-owner-BLACK KPPK..."
        );

        FourPieceTierTwoKppkRetrogradeBuilder.Result black =
                new FourPieceTierTwoKppkRetrogradeBuilder(
                        false
                ).build();

        validateMetadata(
                white,
                black
        );

        long symmetryStarted =
                System.nanoTime();

        SymmetryCounts symmetry =
                validateSymmetry(
                        white,
                        black
                );

        System.out.println();
        System.out.println(
                "Full state-space color symmetry"
        );

        System.out.println(
                "==============================="
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
                "Outcome mismatches: "
                        + symmetry.outcomeMismatches()
        );

        System.out.println(
                "Distance mismatches: "
                        + symmetry.distanceMismatches()
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

        BellmanCounts whiteBellman =
                validateBellman(
                        white,
                        true,
                        samples,
                        0x4B50504B5801L
                );

        printBellman(
                "WHITE",
                whiteBellman
        );

        BellmanCounts blackBellman =
                validateBellman(
                        black,
                        false,
                        samples,
                        0x4B50504B5802L
                );

        printBellman(
                "BLACK",
                blackBellman
        );

        String whiteDigest =
                FourPieceGenericTablebaseCodec.contentDigest(
                        white.toTablebase()
                );

        String blackDigest =
                FourPieceGenericTablebaseCodec.contentDigest(
                        black.toTablebase()
                );

        System.out.println();
        System.out.println(
                "Solved-tablebase metadata"
        );

        System.out.println(
                "========================="
        );

        System.out.println(
                "Legal states: "
                        + white.legalStates()
        );

        System.out.println(
                "WIN: "
                        + white.wins()
        );

        System.out.println(
                "LOSS: "
                        + white.losses()
        );

        System.out.println(
                "DRAW: "
                        + white.draws()
        );

        System.out.println(
                "Maximum DTM: "
                        + white.maximumDistance()
        );

        System.out.println(
                "WHITE digest: "
                        + whiteDigest
        );

        System.out.println(
                "BLACK digest: "
                        + blackDigest
        );

        System.out.println();
        System.out.println(
                "KPPK EXACT SOLVE / VALIDATION / COLOR-SYMMETRY GATE PASSED"
        );

        System.out.println(
                "NEXT: PERSIST KPPK AND ADD TIER-2 RUNTIME ROUTING"
        );
    }


    private static void validateMetadata(
            FourPieceTierTwoKppkRetrogradeBuilder.Result white,
            FourPieceTierTwoKppkRetrogradeBuilder.Result black
    ) {

        require(
                "material",
                white.material(),
                black.material()
        );

        require(
                "legal states",
                white.legalStates(),
                black.legalStates()
        );

        require(
                "WIN count",
                white.wins(),
                black.wins()
        );

        require(
                "LOSS count",
                white.losses(),
                black.losses()
        );

        require(
                "DRAW count",
                white.draws(),
                black.draws()
        );

        require(
                "maximum DTM",
                white.maximumDistance(),
                black.maximumDistance()
        );

        require(
                "same-class edge count",
                white.sameClassEdges(),
                black.sameClassEdges()
        );

        require(
                "KPK boundary edge count",
                white.threePieceEdges(),
                black.threePieceEdges()
        );

        require(
                "promotion edge count",
                white.promotionEdges(),
                black.promotionEdges()
        );

        System.out.println();
        System.out.println(
                "Independent-orientation metadata symmetry: PASSED"
        );
    }


    private static SymmetryCounts validateSymmetry(
            FourPieceTierTwoKppkRetrogradeBuilder.Result white,
            FourPieceTierTwoKppkRetrogradeBuilder.Result black
    ) {

        long statesCompared =
                0;

        long legalStatesCompared =
                0;

        for (int state = 0;
             state < FourPieceGenericPrimitiveState.STATE_COUNT;
             state++) {

            /*
             * Noncanonical raw KPPK states are INVALID in both independent
             * builds. Compare those directly. Canonical states are transformed
             * and re-canonicalized after the color/rank reversal.
             */
            if (!FourPieceGenericPrimitiveState.isCanonical(
                    state,
                    KPPK
            )) {

                if (white.outcome()[state]
                        != FourPieceTablebase.INVALID
                        || black.outcome()[state]
                        != FourPieceTablebase.INVALID) {

                    throw new IllegalStateException(
                            "Noncanonical KPPK raw state became legal: "
                                    + describe(
                                    state
                            )
                    );
                }

                statesCompared++;

                continue;
            }

            int reversed =
                    colorReverseState(
                            state
                    );

            statesCompared++;

            byte whiteOutcome =
                    white.outcome()[state];

            byte blackOutcome =
                    black.outcome()[reversed];

            if (whiteOutcome
                    != FourPieceTablebase.INVALID) {

                legalStatesCompared++;
            }

            if (whiteOutcome
                    != blackOutcome) {

                throw new IllegalStateException(
                        "KPPK color-symmetry outcome mismatch: "
                                + describe(
                                state
                        )
                                + " <-> "
                                + describe(
                                reversed
                        )
                );
            }

            if (white.distance()[state]
                    != black.distance()[reversed]) {

                throw new IllegalStateException(
                        "KPPK color-symmetry DTM mismatch: "
                                + describe(
                                state
                        )
                                + " <-> "
                                + describe(
                                reversed
                        )
                );
            }
        }

        return new SymmetryCounts(
                statesCompared,
                legalStatesCompared,
                0,
                0
        );
    }


    private static int colorReverseState(
            int state
    ) {

        int raw =
                FourPieceGenericPrimitiveState.encode(
                        flip(
                                FourPieceGenericPrimitiveState.blackKing(
                                        state
                                )
                        ),
                        flip(
                                FourPieceGenericPrimitiveState.whiteKing(
                                        state
                                )
                        ),
                        flip(
                                FourPieceGenericPrimitiveState.firstExtra(
                                        state
                                )
                        ),
                        flip(
                                FourPieceGenericPrimitiveState.secondExtra(
                                        state
                                )
                        ),
                        !FourPieceGenericPrimitiveState.blackToMove(
                                state
                        )
                );

        return FourPieceGenericPrimitiveState.canonicalize(
                raw,
                KPPK
        );
    }


    private static int flip(
            int square
    ) {

        return (7 - (square >>> 3)) * 8
                + (square & 7);
    }


    private static BellmanCounts validateBellman(
            FourPieceTierTwoKppkRetrogradeBuilder.Result result,
            boolean ownerWhite,
            int samples,
            long seed
    ) {

        Random random =
                new Random(
                        seed
                );

        FourPieceTierTwoKppkPrimitiveMoveGenerator.Buffer moves =
                new FourPieceTierTwoKppkPrimitiveMoveGenerator.Buffer(
                        32
                );

        int checked =
                0;

        long attempts =
                0;

        long edges =
                0;

        long wins =
                0;

        long losses =
                0;

        long draws =
                0;

        long terminals =
                0;

        while (checked
                < samples) {

            attempts++;

            int state =
                    randomState(
                            random
                    );

            byte outcome =
                    result.outcome()[state];

            if (outcome
                    == FourPieceTablebase.INVALID) {

                continue;
            }

            int moveCount =
                    FourPieceTierTwoKppkPrimitiveMoveGenerator
                            .generateLegalSuccessors(
                                    state,
                                    KPPK,
                                    ownerWhite,
                                    moves
                            );

            if (moveCount
                    == 0) {

                terminals++;

                if (outcome
                        == FourPieceTablebase.LOSS
                        && result.distance()[state]
                        != 0) {

                    fail(
                            "terminal LOSS DTM != 0",
                            state,
                            outcome,
                            result.distance()[state]
                    );
                }

                if (outcome
                        != FourPieceTablebase.LOSS
                        && outcome
                        != FourPieceTablebase.DRAW) {

                    fail(
                            "terminal is neither LOSS nor DRAW",
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

            boolean external =
                    false;

            for (int i = 0;
                 i < moveCount;
                 i++) {

                if (moves.boundaryType(
                        i
                ) != FourPieceTierTwoKppkPrimitiveMoveGenerator.BOUNDARY_NONE) {

                    external =
                            true;

                    break;
                }

                edges++;

                int child =
                        moves.state(
                                i
                        );

                byte childOutcome =
                        result.outcome()[child];

                short childDistance =
                        result.distance()[child];

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

                    fail(
                            "same-class child unresolved/invalid",
                            child,
                            childOutcome,
                            childDistance
                    );
                }
            }

            /*
             * External KPK and Tier-1 promotion edges were consumed exactly by
             * the dependency-aware builder. This randomized Bellman gate checks
             * the complete recurrence only where every legal child remains KPPK.
             */
            if (external) {

                continue;
            }

            short distance =
                    result.distance()[state];

            if (outcome
                    == FourPieceTablebase.WIN) {

                wins++;

                if (lossChildren
                        == 0) {

                    fail(
                            "WIN has no LOSS child",
                            state,
                            outcome,
                            distance
                    );
                }

                if (distance
                        != minimumLossDistance + 1) {

                    fail(
                            "WIN DTM recurrence mismatch",
                            state,
                            outcome,
                            distance
                    );
                }

            } else if (outcome
                    == FourPieceTablebase.LOSS) {

                losses++;

                if (winChildren
                        != moveCount) {

                    fail(
                            "LOSS does not have all WIN children",
                            state,
                            outcome,
                            distance
                    );
                }

                if (distance
                        != maximumWinDistance + 1) {

                    fail(
                            "LOSS DTM recurrence mismatch",
                            state,
                            outcome,
                            distance
                    );
                }

            } else if (outcome
                    == FourPieceTablebase.DRAW) {

                draws++;

                if (lossChildren
                        != 0) {

                    fail(
                            "DRAW has LOSS child",
                            state,
                            outcome,
                            distance
                    );
                }

                if (drawChildren
                        == 0) {

                    fail(
                            "DRAW has no DRAW child",
                            state,
                            outcome,
                            distance
                    );
                }

                if (distance
                        != -1) {

                    fail(
                            "DRAW DTM != -1",
                            state,
                            outcome,
                            distance
                    );
                }

            } else {

                fail(
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
                wins,
                losses,
                draws,
                terminals
        );
    }


    private static int randomState(
            Random random
    ) {

        while (true) {

            int whiteKing =
                    random.nextInt(
                            64
                    );

            int blackKing =
                    random.nextInt(
                            64
                    );

            int firstPawn =
                    (1 + random.nextInt(
                            6
                    )) * 8
                            + random.nextInt(
                            8
                    );

            int secondPawn =
                    (1 + random.nextInt(
                            6
                    )) * 8
                            + random.nextInt(
                            8
                    );

            if (whiteKing == blackKing
                    || whiteKing == firstPawn
                    || whiteKing == secondPawn
                    || blackKing == firstPawn
                    || blackKing == secondPawn
                    || firstPawn == secondPawn) {

                continue;
            }

            int raw =
                    FourPieceGenericPrimitiveState.encode(
                            whiteKing,
                            blackKing,
                            firstPawn,
                            secondPawn,
                            random.nextBoolean()
                    );

            return FourPieceGenericPrimitiveState.canonicalize(
                    raw,
                    KPPK
            );
        }
    }


    private static void printBellman(
            String side,
            BellmanCounts counts
    ) {

        System.out.println();
        System.out.println(
                "Bellman recurrence — pawn owner "
                        + side
        );

        System.out.println(
                "===================================="
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
                "Same-class edges checked: "
                        + counts.sameClassEdgesChecked()
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


    private static void fail(
            String why,
            int state,
            byte outcome,
            short distance
    ) {

        throw new IllegalStateException(
                "KPPK Bellman validation failed: "
                        + why
                        + "\n  state: "
                        + describe(
                        state
                )
                        + "\n  outcome: "
                        + outcomeName(
                        outcome
                )
                        + "\n  DTM: "
                        + distance
        );
    }


    private static String describe(
            int state
    ) {

        return "WK="
                + algebraic(
                FourPieceGenericPrimitiveState.whiteKing(
                        state
                )
        )
                + " BK="
                + algebraic(
                FourPieceGenericPrimitiveState.blackKing(
                        state
                )
        )
                + " P1="
                + algebraic(
                FourPieceGenericPrimitiveState.firstExtra(
                        state
                )
        )
                + " P2="
                + algebraic(
                FourPieceGenericPrimitiveState.secondExtra(
                        state
                )
        )
                + " stm="
                + (FourPieceGenericPrimitiveState.blackToMove(
                state
        )
                ? "BLACK"
                : "WHITE")
                + " ["
                + state
                + "]";
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

        if (outcome
                == FourPieceTablebase.WIN) {

            return "WIN";
        }

        if (outcome
                == FourPieceTablebase.LOSS) {

            return "LOSS";
        }

        if (outcome
                == FourPieceTablebase.DRAW) {

            return "DRAW";
        }

        if (outcome
                == FourPieceTablebase.INVALID) {

            return "INVALID";
        }

        return "UNKNOWN";
    }


    private static void require(
            String name,
            long first,
            long second
    ) {

        if (first
                != second) {

            throw new IllegalStateException(
                    "KPPK metadata mismatch for "
                            + name
                            + ": "
                            + first
                            + " != "
                            + second
            );
        }
    }


    private static void require(
            String name,
            Object first,
            Object second
    ) {

        if (!first.equals(
                second
        )) {

            throw new IllegalStateException(
                    "KPPK metadata mismatch for "
                            + name
                            + ": "
                            + first
                            + " != "
                            + second
            );
        }
    }


    private record SymmetryCounts(
            long statesCompared,
            long legalStatesCompared,
            long outcomeMismatches,
            long distanceMismatches
    ) {
    }


    private record BellmanCounts(
            int legalStatesChecked,
            long randomAttempts,
            long sameClassEdgesChecked,
            long winsChecked,
            long lossesChecked,
            long drawsChecked,
            long terminalStatesChecked
    ) {
    }
}

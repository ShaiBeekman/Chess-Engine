package main.java.chess.endgame;

import main.java.chess.model.PieceType;

import java.util.Random;

/**
 * Milestone 53.
 *
 * Solve the first SPLIT Tier-1 family, KQ-KP, in the canonical orientation:
 *
 *     firstExtra = WHITE queen
 *     secondExtra = BLACK pawn
 *
 * Unlike SAME_SIDE KQPK/KRPK/KBPK/KNPK, SPLIT material has one canonical
 * builder orientation.  Therefore this gate does not fabricate a second
 * "strong-BLACK" build.  It validates:
 *
 *   1. the canonical solve;
 *   2. sampled Bellman recurrence on internal-only states;
 *   3. the canonical color-reversal normalization is an involution;
 *   4. a stable content digest to carry into persistence.
 *
 * No persistence/runtime asset is written here.
 */
public final class FourPieceTierOneKqKpExactValidationMain {

    private static final int DEFAULT_BELLMAN_SAMPLES =
            1_000_000;

    private static final int DEFAULT_NORMALIZATION_SAMPLES =
            1_000_000;

    private static final FourPieceMaterialClass MATERIAL =
            FourPieceMaterialClass.split(
                    PieceType.QUEEN,
                    PieceType.PAWN
            );

    private FourPieceTierOneKqKpExactValidationMain() {
    }

    public static void main(
            String[] args
    ) throws Exception {

        if (args.length > 2) {
            throw new IllegalArgumentException(
                    "Usage: FourPieceTierOneKqKpExactValidationMain "
                            + "[bellman-samples] [normalization-samples]"
            );
        }

        int bellmanSamples =
                args.length >= 1
                        ? Integer.parseInt(
                        args[0]
                )
                        : DEFAULT_BELLMAN_SAMPLES;

        int normalizationSamples =
                args.length >= 2
                        ? Integer.parseInt(
                        args[1]
                )
                        : DEFAULT_NORMALIZATION_SAMPLES;

        if (bellmanSamples < 1
                || normalizationSamples < 1) {
            throw new IllegalArgumentException(
                    "Validation sample counts must be positive."
            );
        }

        System.out.println(
                "KQ-KP exact solve / validation gate"
        );
        System.out.println(
                "==================================="
        );
        System.out.println(
                "Canonical orientation: WHITE queen / BLACK pawn"
        );

        System.out.println();
        System.out.println(
                "Building canonical KQ-KP..."
        );

        FourPieceTierOneRetrogradeBuilder.Result result =
                new FourPieceTierOneRetrogradeBuilder(
                        MATERIAL,
                        true
                ).build();

        verifyCanonicalMetadata(
                result
        );

        BellmanCounts bellman =
                validateBellman(
                        result,
                        bellmanSamples,
                        0x4B514B505301L
                );

        printBellman(
                bellman
        );

        NormalizationCounts normalization =
                validateColorReversalNormalization(
                        normalizationSamples,
                        0x4B514B505302L
                );

        printNormalization(
                normalization
        );

        FourPieceGenericTablebase tablebase =
                result.toTablebase();

        String digest =
                FourPieceGenericTablebaseCodec.contentDigest(
                        tablebase
                );

        System.out.println();
        System.out.println(
                "Solved-tablebase metadata"
        );
        System.out.println(
                "========================="
        );
        System.out.println(
                "Material: "
                        + result.material()
                        .displayName()
        );
        System.out.println(
                "Legal states: "
                        + result.legalStates()
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
                "Canonical digest: "
                        + digest
        );

        System.out.println();
        System.out.println(
                "KQ-KP EXACT SOLVE / VALIDATION GATE PASSED"
        );
        System.out.println(
                "NEXT: PERSIST KQ-KP AND VERIFY SPLIT RUNTIME NORMALIZATION"
        );
    }

    private static void verifyCanonicalMetadata(
            FourPieceTierOneRetrogradeBuilder.Result result
    ) {

        if (!MATERIAL.equals(
                result.material()
        )) {
            throw new IllegalStateException(
                    "KQ-KP result material mismatch."
            );
        }

        if (!result.sameSideOwnerIsWhite()) {
            throw new IllegalStateException(
                    "KQ-KP canonical orientation flag must be true."
            );
        }

        long total =
                result.wins()
                        + result.losses()
                        + result.draws();

        if (total
                != result.legalStates()) {
            throw new IllegalStateException(
                    "KQ-KP WDL counts do not sum to legal-state count."
                            + "\n  legal: "
                            + result.legalStates()
                            + "\n  W+L+D: "
                            + total
            );
        }

        if (result.legalStates()
                <= 0) {
            throw new IllegalStateException(
                    "KQ-KP solve produced no legal states."
            );
        }

        if (result.maximumDistance()
                < 0) {
            throw new IllegalStateException(
                    "KQ-KP maximum DTM is invalid."
            );
        }

        System.out.println();
        System.out.println(
                "Canonical solve metadata consistency: PASSED"
        );
    }

    private static BellmanCounts validateBellman(
            FourPieceTierOneRetrogradeBuilder.Result result,
            int samples,
            long seed
    ) {

        Random random =
                new Random(
                        seed
                );

        FourPieceTierOnePrimitiveMoveGenerator.Buffer moves =
                new FourPieceTierOnePrimitiveMoveGenerator.Buffer(
                        64
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
                    FourPieceTierOnePrimitiveMoveGenerator
                            .generateLegalSuccessors(
                                    state,
                                    MATERIAL,
                                    true,
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

            int internalChildren =
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
                ) != FourPieceTierOnePrimitiveMoveGenerator.BOUNDARY_NONE) {

                    external =
                            true;

                    break;
                }

                internalChildren++;
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
             * Dependency edges into KQK/KPK or Tier-0 promotion classes were
             * already solved by the dependency-aware builder.  This local
             * Bellman check intentionally samples states whose entire legal
             * successor set remains inside KQ-KP.
             */
            if (external) {
                continue;
            }

            if (internalChildren
                    != moveCount) {
                throw new IllegalStateException(
                        "Internal-only KQ-KP Bellman state lost a successor."
                );
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

    private static NormalizationCounts validateColorReversalNormalization(
            int samples,
            long seed
    ) {

        Random random =
                new Random(
                        seed
                );

        int checked =
                0;

        long attempts =
                0;

        while (checked
                < samples) {

            attempts++;

            int state =
                    randomState(
                            random
                    );

            if (!FourPieceTierOnePrimitiveMoveGenerator
                    .isStructurallyLegal(
                            state,
                            MATERIAL,
                            true
                    )) {
                continue;
            }

            /*
             * A canonical primitive KQ-KP state represents:
             *
             *   WK, BK, WHITE Q, BLACK P, stm.
             *
             * Color reversal produces the physical opposite:
             *
             *   WHITE P, BLACK Q.
             *
             * The runtime canonicalizer swaps king colors, rank-flips every
             * piece, and inverts stm, which must recover this exact primitive
             * state.  Applying that normalization transform twice must also be
             * the identity.
             */
            int reversed =
                    colorReverseState(
                            state
                    );

            int recovered =
                    colorReverseState(
                            reversed
                    );

            if (recovered
                    != state) {
                throw new IllegalStateException(
                        "KQ-KP color-reversal normalization is not an involution."
                                + "\n  original:  "
                                + describe(
                                state
                        )
                                + "\n  reversed:  "
                                + describe(
                                reversed
                        )
                                + "\n  recovered: "
                                + describe(
                                recovered
                        )
                );
            }

            checked++;
        }

        return new NormalizationCounts(
                checked,
                attempts
        );
    }

    private static int colorReverseState(
            int state
    ) {

        return FourPieceGenericPrimitiveState.encode(
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
    }

    private static int flip(
            int square
    ) {

        return (7 - (square >>> 3)) * 8
                + (square & 7);
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

            int queen =
                    random.nextInt(
                            64
                    );

            /*
             * Canonical SPLIT KQ-KP has a BLACK pawn, so only ranks 2..7 in
             * board coordinates are structurally representable here; rank 1
             * and rank 8 remain excluded exactly as in the primitive rules.
             */
            int pawn =
                    (1 + random.nextInt(
                            6
                    )) * 8
                            + random.nextInt(
                            8
                    );

            if (whiteKing
                    == blackKing
                    || whiteKing
                    == queen
                    || whiteKing
                    == pawn
                    || blackKing
                    == queen
                    || blackKing
                    == pawn
                    || queen
                    == pawn) {
                continue;
            }

            return FourPieceGenericPrimitiveState.encode(
                    whiteKing,
                    blackKing,
                    queen,
                    pawn,
                    random.nextBoolean()
            );
        }
    }

    private static void printBellman(
            BellmanCounts counts
    ) {

        System.out.println();
        System.out.println(
                "Bellman recurrence — canonical KQ-KP"
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

    private static void printNormalization(
            NormalizationCounts counts
    ) {

        System.out.println();
        System.out.println(
                "Split color-reversal normalization"
        );
        System.out.println(
                "=================================="
        );
        System.out.println(
                "Canonical legal states checked: "
                        + counts.statesChecked()
        );
        System.out.println(
                "Random attempts: "
                        + counts.randomAttempts()
        );
        System.out.println(
                "Round-trip mismatches: 0"
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
                "KQ-KP Bellman validation failed: "
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
                + " Q="
                + algebraic(
                FourPieceGenericPrimitiveState.firstExtra(
                        state
                )
        )
                + " P="
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

    private record NormalizationCounts(
            int statesChecked,
            long randomAttempts
    ) {
    }
}

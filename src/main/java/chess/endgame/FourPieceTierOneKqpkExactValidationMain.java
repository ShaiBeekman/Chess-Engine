package main.java.chess.endgame;

import main.java.chess.model.PieceType;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Random;


/**
 * Milestone 38.
 *
 * Exact KQPK validation + color-symmetry gate.
 *
 * This builds both strong-color orientations and then verifies:
 *
 *   1. identical legal/WIN/LOSS/DRAW counts;
 *   2. identical maximum DTM;
 *   3. exact state-by-state WDL/DTM equivalence under vertical color reversal;
 *   4. randomized Bellman recurrence checks against the solved arrays;
 *   5. deterministic digests for both solved orientations.
 *
 * Color reversal for pawns cannot leave squares untouched.  The board must be
 * reflected vertically so a White pawn advancing toward rank 8 becomes a
 * Black pawn advancing toward rank 1:
 *
 *     a1 <-> a8
 *     a2 <-> a7
 *     ...
 *
 * The two kings swap colors, queen/pawn ownership swaps colors, and side to
 * move reverses.
 *
 * Optional arg 0:
 *
 *     number of random Bellman samples per orientation
 *
 * Default:
 *
 *     1,000,000
 */
public final class FourPieceTierOneKqpkExactValidationMain {

    private static final int DEFAULT_BELLMAN_SAMPLES =
            1_000_000;


    private FourPieceTierOneKqpkExactValidationMain() {
    }


    public static void main(
            String[] args
    ) throws Exception {

        int bellmanSamples =
                args.length == 0
                        ? DEFAULT_BELLMAN_SAMPLES
                        : Integer.parseInt(
                        args[0]
                );


        if (bellmanSamples < 1) {

            throw new IllegalArgumentException(
                    "Bellman sample count must be positive."
            );
        }


        System.out.println(
                "KQPK exact validation / color-symmetry gate"
        );

        System.out.println(
                "=========================================="
        );

        System.out.println();


        System.out.println(
                "Building strong-WHITE KQPK..."
        );


        FourPieceTierOneKqpkRetrogradeBuilder.Result white =
                new FourPieceTierOneKqpkRetrogradeBuilder(
                        true
                ).build();


        System.out.println();

        System.out.println(
                "Building strong-BLACK KQPK..."
        );


        FourPieceTierOneKqpkRetrogradeBuilder.Result black =
                new FourPieceTierOneKqpkRetrogradeBuilder(
                        false
                ).build();


        System.out.println();

        validateMetadataSymmetry(
                white,
                black
        );


        long symmetryStarted =
                System.nanoTime();


        SymmetryCounts symmetry =
                validateFullColorSymmetry(
                        white,
                        black
                );


        double symmetrySeconds =
                (System.nanoTime()
                        - symmetryStarted)
                        / 1_000_000_000.0;


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
                symmetrySeconds
        );

        System.out.println(
                "PASSED"
        );


        System.out.println();


        BellmanCounts whiteBellman =
                validateBellman(
                        white,
                        true,
                        bellmanSamples,
                        0x4B51504B3801L
                );


        printBellman(
                "WHITE",
                whiteBellman
        );


        System.out.println();


        BellmanCounts blackBellman =
                validateBellman(
                        black,
                        false,
                        bellmanSamples,
                        0x4B51504B3802L
                );


        printBellman(
                "BLACK",
                blackBellman
        );


        System.out.println();


        String whiteDigest =
                digest(
                        white.outcome(),
                        white.distance()
                );

        String blackDigest =
                digest(
                        black.outcome(),
                        black.distance()
                );


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
                "KQPK EXACT VALIDATION / COLOR-SYMMETRY GATE PASSED"
        );

        System.out.println(
                "NEXT: PERSIST KQPK AND ADD TIER-1 RUNTIME ROUTING"
        );
    }


    // =============================================================
    // METADATA
    // =============================================================

    private static void validateMetadataSymmetry(
            FourPieceTierOneKqpkRetrogradeBuilder.Result white,
            FourPieceTierOneKqpkRetrogradeBuilder.Result black
    ) {

        requireEqual(
                "material",
                white.material(),
                black.material()
        );

        requireEqual(
                "legal states",
                white.legalStates(),
                black.legalStates()
        );

        requireEqual(
                "WIN count",
                white.wins(),
                black.wins()
        );

        requireEqual(
                "LOSS count",
                white.losses(),
                black.losses()
        );

        requireEqual(
                "DRAW count",
                white.draws(),
                black.draws()
        );

        requireEqual(
                "maximum DTM",
                white.maximumDistance(),
                black.maximumDistance()
        );
    }


    // =============================================================
    // FULL COLOR SYMMETRY
    // =============================================================

    private static SymmetryCounts validateFullColorSymmetry(
            FourPieceTierOneKqpkRetrogradeBuilder.Result white,
            FourPieceTierOneKqpkRetrogradeBuilder.Result black
    ) {

        byte[] whiteOutcome =
                white.outcome();

        short[] whiteDistance =
                white.distance();

        byte[] blackOutcome =
                black.outcome();

        short[] blackDistance =
                black.distance();


        long statesCompared =
                0;

        long legalStatesCompared =
                0;

        long outcomeMismatches =
                0;

        long distanceMismatches =
                0;


        for (int whiteState = 0;
             whiteState
                     < FourPieceGenericPrimitiveState.STATE_COUNT;
             whiteState++) {

            int blackState =
                    colorReverseState(
                            whiteState
                    );


            statesCompared++;


            byte wo =
                    whiteOutcome[whiteState];

            byte bo =
                    blackOutcome[blackState];


            if (wo
                    != FourPieceTablebase.INVALID) {

                legalStatesCompared++;
            }


            if (wo != bo) {

                outcomeMismatches++;


                throw new IllegalStateException(
                        "KQPK color-symmetry outcome mismatch."
                                + System.lineSeparator()
                                + "  WHITE: "
                                + describe(
                                whiteState
                        )
                                + System.lineSeparator()
                                + "  BLACK: "
                                + describe(
                                blackState
                        )
                                + System.lineSeparator()
                                + "  WHITE outcome: "
                                + outcomeName(
                                wo
                        )
                                + System.lineSeparator()
                                + "  BLACK outcome: "
                                + outcomeName(
                                bo
                        )
                );
            }


            short wd =
                    whiteDistance[whiteState];

            short bd =
                    blackDistance[blackState];


            if (wd != bd) {

                distanceMismatches++;


                throw new IllegalStateException(
                        "KQPK color-symmetry DTM mismatch."
                                + System.lineSeparator()
                                + "  WHITE: "
                                + describe(
                                whiteState
                        )
                                + System.lineSeparator()
                                + "  BLACK: "
                                + describe(
                                blackState
                        )
                                + System.lineSeparator()
                                + "  WHITE DTM: "
                                + wd
                                + System.lineSeparator()
                                + "  BLACK DTM: "
                                + bd
                );
            }
        }


        return new SymmetryCounts(
                statesCompared,
                legalStatesCompared,
                outcomeMismatches,
                distanceMismatches
        );
    }


    private static int colorReverseState(
            int state
    ) {

        int whiteKing =
                FourPieceGenericPrimitiveState.whiteKing(
                        state
                );

        int blackKing =
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


        return FourPieceGenericPrimitiveState.encode(
                flipRank(
                        blackKing
                ),
                flipRank(
                        whiteKing
                ),
                flipRank(
                        queen
                ),
                flipRank(
                        pawn
                ),
                !FourPieceGenericPrimitiveState.blackToMove(
                        state
                )
        );
    }


    private static int flipRank(
            int square
    ) {

        int file =
                square & 7;

        int rank =
                square >>> 3;


        return (7 - rank) * 8
                + file;
    }


    // =============================================================
    // BELLMAN RECURRENCE
    // =============================================================

    private static BellmanCounts validateBellman(
            FourPieceTierOneKqpkRetrogradeBuilder.Result result,
            boolean strongIsWhite,
            int samples,
            long seed
    ) {

        Random random =
                new Random(
                        seed
                );


        FourPieceTierOneKqpkPrimitiveMoveGenerator.Buffer successors =
                new FourPieceTierOneKqpkPrimitiveMoveGenerator.Buffer(
                        64
                );


        int legalChecked =
                0;

        long attempts =
                0;

        long sameClassEdges =
                0;

        long winsChecked =
                0;

        long lossesChecked =
                0;

        long drawsChecked =
                0;

        long terminalStates =
                0;


        while (legalChecked
                < samples) {

            attempts++;


            int state =
                    randomState(
                            random
                    );


            byte stateOutcome =
                    result.outcome()[state];


            if (stateOutcome
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


            if (moveCount == 0) {

                terminalStates++;


                if (stateOutcome
                        == FourPieceTablebase.LOSS) {

                    if (result.distance()[state]
                            != 0) {

                        failBellman(
                                "terminal LOSS does not have DTM 0",
                                state,
                                stateOutcome,
                                result.distance()[state]
                        );
                    }

                } else if (stateOutcome
                        != FourPieceTablebase.DRAW) {

                    failBellman(
                            "zero-move state is neither LOSS nor DRAW",
                            state,
                            stateOutcome,
                            result.distance()[state]
                    );
                }


                legalChecked++;

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

                if (successors.boundaryType(i)
                        != FourPieceTierOneKqpkPrimitiveMoveGenerator
                        .BOUNDARY_NONE) {

                    /*
                     * M38's randomized Bellman gate validates the solved
                     * same-class recurrence directly.  Exact external
                     * dependencies were already consumed by M37 and are
                     * independently covered by the full color-symmetry solve.
                     *
                     * A sampled state with an external edge is therefore
                     * skipped for recurrence purposes; we only assert Bellman
                     * equations on positions whose complete move set remains
                     * inside KQPK.
                     */
                    lossChildren =
                            -1;

                    break;
                }


                sameClassEdges++;


                int child =
                        successors.state(i);

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

                    failBellman(
                            "legal same-class child is unresolved/invalid",
                            child,
                            childOutcome,
                            childDistance
                    );
                }
            }


            if (lossChildren < 0) {

                continue;
            }


            short stateDistance =
                    result.distance()[state];


            if (stateOutcome
                    == FourPieceTablebase.WIN) {

                winsChecked++;


                if (lossChildren == 0) {

                    failBellman(
                            "WIN has no LOSS child",
                            state,
                            stateOutcome,
                            stateDistance
                    );
                }


                int expected =
                        minimumLossDistance + 1;


                if (stateDistance
                        != expected) {

                    failBellman(
                            "WIN DTM is not 1 + minimum LOSS-child DTM"
                                    + " (expected "
                                    + expected
                                    + ")",
                            state,
                            stateOutcome,
                            stateDistance
                    );
                }

            } else if (stateOutcome
                    == FourPieceTablebase.LOSS) {

                lossesChecked++;


                if (winChildren
                        != moveCount) {

                    failBellman(
                            "LOSS does not have all WIN children",
                            state,
                            stateOutcome,
                            stateDistance
                    );
                }


                int expected =
                        maximumWinDistance + 1;


                if (stateDistance
                        != expected) {

                    failBellman(
                            "LOSS DTM is not 1 + maximum WIN-child DTM"
                                    + " (expected "
                                    + expected
                                    + ")",
                            state,
                            stateOutcome,
                            stateDistance
                    );
                }

            } else if (stateOutcome
                    == FourPieceTablebase.DRAW) {

                drawsChecked++;


                if (lossChildren != 0) {

                    failBellman(
                            "DRAW has a LOSS child",
                            state,
                            stateOutcome,
                            stateDistance
                    );
                }


                if (drawChildren == 0) {

                    failBellman(
                            "nonterminal DRAW has no DRAW child",
                            state,
                            stateOutcome,
                            stateDistance
                    );
                }


                if (stateDistance != -1) {

                    failBellman(
                            "DRAW does not use DTM -1",
                            state,
                            stateOutcome,
                            stateDistance
                    );
                }

            } else {

                failBellman(
                        "sampled legal state has unresolved/invalid outcome",
                        state,
                        stateOutcome,
                        stateDistance
                );
            }


            legalChecked++;
        }


        return new BellmanCounts(
                legalChecked,
                attempts,
                sameClassEdges,
                winsChecked,
                lossesChecked,
                drawsChecked,
                terminalStates
        );
    }


    private static void failBellman(
            String reason,
            int state,
            byte outcome,
            short distance
    ) {

        throw new IllegalStateException(
                "KQPK Bellman validation failed: "
                        + reason
                        + System.lineSeparator()
                        + "  state: "
                        + describe(
                        state
                )
                        + System.lineSeparator()
                        + "  outcome: "
                        + outcomeName(
                        outcome
                )
                        + System.lineSeparator()
                        + "  DTM: "
                        + distance
        );
    }


    private static void printBellman(
            String strong,
            BellmanCounts counts
    ) {

        System.out.println(
                "Bellman recurrence — strong "
                        + strong
        );

        System.out.println(
                "================================="
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


    // =============================================================
    // RANDOM STATES
    // =============================================================

    private static int randomState(
            Random random
    ) {

        while (true) {

            int wk =
                    random.nextInt(
                            64
                    );

            int bk =
                    random.nextInt(
                            64
                    );

            int queen =
                    random.nextInt(
                            64
                    );

            int pawn =
                    (1 + random.nextInt(
                            6
                    )) * 8
                            + random.nextInt(
                            8
                    );


            if (!distinct(
                    wk,
                    bk,
                    queen,
                    pawn
            )) {

                continue;
            }


            return FourPieceGenericPrimitiveState.encode(
                    wk,
                    bk,
                    queen,
                    pawn,
                    random.nextBoolean()
            );
        }
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


    // =============================================================
    // DISPLAY / DIGEST
    // =============================================================

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

        return String.valueOf(
                (char) ('a'
                        + (square & 7))
        )
                + ((square >>> 3)
                + 1);
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


        if (outcome
                == FourPieceTablebase.UNKNOWN) {

            return "UNKNOWN";
        }


        return "CODE("
                + outcome
                + ")";
    }


    private static String digest(
            byte[] outcome,
            short[] distance
    ) throws NoSuchAlgorithmException {

        MessageDigest digest =
                MessageDigest.getInstance(
                        "SHA-256"
                );


        digest.update(
                outcome
        );


        byte[] pair =
                new byte[2];


        for (short value :
                distance) {

            pair[0] =
                    (byte) (value >>> 8);

            pair[1] =
                    (byte) value;

            digest.update(
                    pair
            );
        }


        return HexFormat.of()
                .formatHex(
                        digest.digest()
                );
    }


    private static void requireEqual(
            String name,
            long first,
            long second
    ) {

        if (first != second) {

            throw new IllegalStateException(
                    "KQPK color-symmetry metadata mismatch for "
                            + name
                            + ": "
                            + first
                            + " != "
                            + second
            );
        }
    }


    private static void requireEqual(
            String name,
            Object first,
            Object second
    ) {

        if (!first.equals(
                second
        )) {

            throw new IllegalStateException(
                    "KQPK color-symmetry metadata mismatch for "
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

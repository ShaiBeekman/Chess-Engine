package main.java.chess.endgame;

import main.java.chess.model.PieceType;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Random;


/**
 * Milestone 18:
 *
 * Build and validate the first genuinely new four-piece tablebase
 * produced entirely by the generic Tier-0 architecture:
 *
 *     KQBK
 *
 * Both color orientations are solved:
 *
 *     White owns Q+B
 *     Black owns Q+B
 *
 *
 * Validation:
 *
 * 1. Complete structural/result consistency.
 *
 * 2. Large deterministic sample of exact WDL/DTM recurrence:
 *
 *        WIN:
 *            at least one LOSS child
 *            DTM = 1 + minimum LOSS-child DTM
 *
 *        LOSS:
 *            every child WIN
 *            DTM = 1 + maximum WIN-child DTM
 *
 *        DRAW:
 *            no LOSS child
 *            and at least one DRAW child / drawing continuation
 *
 * 3. Complete color-symmetry comparison.
 *
 * Color reversal does not change square numbers. It swaps:
 *
 *     white king <-> black king
 *     side to move
 *
 * while queen and bishop remain on their current squares because
 * ownership changes together with the strong side.
 */
public final class FourPieceKqbkGenericTablebaseMain {

    private static final int DEFAULT_RECURRENCE_SAMPLE =
            1_000_000;


    private FourPieceKqbkGenericTablebaseMain() {

    }


    public static void main(
            String[] args
    ) {

        int recurrenceSample =
                args.length > 0
                        ? Integer.parseInt(
                        args[0]
                )
                        : DEFAULT_RECURRENCE_SAMPLE;


        if (recurrenceSample < 1) {

            throw new IllegalArgumentException(
                    "Recurrence sample must be positive."
            );
        }


        FourPieceMaterialClass material =
                FourPieceMaterialClass.sameSide(
                        PieceType.QUEEN,
                        PieceType.BISHOP
                );


        System.out.println(
                "KQBK generic tablebase build"
        );

        System.out.println(
                "============================"
        );

        System.out.println(
                "Material: "
                        + material.displayName()
        );

        System.out.println(
                "Recurrence sample/orientation: "
                        + String.format(
                        "%,d",
                        recurrenceSample
                )
        );


        // =====================================================
        // WHITE STRONG SIDE
        // =====================================================

        System.out.println();

        System.out.println(
                "BUILD 1 / 2 — WHITE owns Q+B"
        );


        FourPieceGenericPrimitiveRetrogradeBuilder.Result white =
                new FourPieceGenericPrimitiveRetrogradeBuilder(
                        material,
                        true
                ).build();


        validateBasicResult(
                white
        );


        validateRecurrenceSample(
                white,
                recurrenceSample,
                0x4B51424B57484954L
        );


        String whiteDigest =
                digest(
                        white.outcome(),
                        white.distance()
                );


        System.out.println();

        System.out.println(
                "WHITE KQBK digest: "
                        + whiteDigest
        );


        // =====================================================
        // BLACK STRONG SIDE
        // =====================================================

        System.out.println();

        System.out.println(
                "BUILD 2 / 2 — BLACK owns Q+B"
        );


        FourPieceGenericPrimitiveRetrogradeBuilder.Result black =
                new FourPieceGenericPrimitiveRetrogradeBuilder(
                        material,
                        false
                ).build();


        validateBasicResult(
                black
        );


        validateRecurrenceSample(
                black,
                recurrenceSample,
                0x4B51424B424C4143L
        );


        String blackDigest =
                digest(
                        black.outcome(),
                        black.distance()
                );


        System.out.println();

        System.out.println(
                "BLACK KQBK digest: "
                        + blackDigest
        );


        // =====================================================
        // COMPLETE COLOR-SYMMETRY VERIFICATION
        // =====================================================

        verifyColorSymmetry(
                white,
                black
        );


        System.out.println();

        System.out.println(
                "KQBK summary"
        );

        System.out.println(
                "============"
        );

        printSummary(
                "WHITE strong",
                white
        );

        printSummary(
                "BLACK strong",
                black
        );


        System.out.println();

        System.out.println(
                "KQBK GENERIC TABLEBASE BUILD PASSED"
        );

        System.out.println(
                "FIRST NEW GENERIC FOUR-PIECE TABLEBASE VERIFIED"
        );
    }


    private static void validateBasicResult(
            FourPieceGenericPrimitiveRetrogradeBuilder.Result result
    ) {

        byte[] outcome =
                result.outcome();

        short[] distance =
                result.distance();


        if (outcome == null
                || distance == null) {

            throw new IllegalStateException(
                    "Generic retrograde returned null result arrays."
            );
        }


        if (outcome.length
                != FourPieceGenericPrimitiveState.STATE_COUNT
                ||
                distance.length
                        != FourPieceGenericPrimitiveState.STATE_COUNT) {

            throw new IllegalStateException(
                    "Generic KQBK result arrays have the wrong size."
            );
        }


        long legal =
                0;

        long wins =
                0;

        long losses =
                0;

        long draws =
                0;


        int maximumDistance =
                -1;


        for (int state = 0;
             state < outcome.length;
             state++) {

            byte value =
                    outcome[state];


            if (value
                    == FourPieceTablebase.INVALID) {

                if (distance[state]
                        != -1) {

                    throw new IllegalStateException(
                            "INVALID state has a DTM at state "
                                    + state
                    );
                }


                continue;
            }


            legal++;


            if (value
                    == FourPieceTablebase.WIN) {

                wins++;


                if (distance[state]
                        < 1) {

                    throw new IllegalStateException(
                            "WIN has invalid DTM at state "
                                    + state
                                    + ": "
                                    + distance[state]
                    );
                }


                maximumDistance =
                        Math.max(
                                maximumDistance,
                                distance[state]
                        );


            } else if (value
                    == FourPieceTablebase.LOSS) {

                losses++;


                if (distance[state]
                        < 0) {

                    throw new IllegalStateException(
                            "LOSS has invalid DTM at state "
                                    + state
                                    + ": "
                                    + distance[state]
                    );
                }


                maximumDistance =
                        Math.max(
                                maximumDistance,
                                distance[state]
                        );


            } else if (value
                    == FourPieceTablebase.DRAW) {

                draws++;


                if (distance[state]
                        != -1) {

                    throw new IllegalStateException(
                            "DRAW unexpectedly has a DTM at state "
                                    + state
                                    + ": "
                                    + distance[state]
                    );
                }


            } else {

                throw new IllegalStateException(
                        "Unexpected solved outcome "
                                + value
                                + " at state "
                                + state
                );
            }
        }


        if (legal
                != result.legalStates()
                ||
                wins
                        != result.wins()
                ||
                losses
                        != result.losses()
                ||
                draws
                        != result.draws()
                ||
                maximumDistance
                        != result.maximumDistance()) {

            throw new IllegalStateException(
                    "Result metadata does not match the complete KQBK arrays."
            );
        }


        System.out.println();

        System.out.println(
                "Complete result-array consistency: PASSED"
        );
    }


    /**
     * Verify exact minimax WDL/DTM recurrence for a large deterministic
     * random sample of legal states.
     */
    private static void validateRecurrenceSample(
            FourPieceGenericPrimitiveRetrogradeBuilder.Result result,
            int target,
            long seed
    ) {

        FourPieceMaterialClass material =
                result.material();

        boolean sameSideOwnerIsWhite =
                result.sameSideOwnerIsWhite();


        byte[] outcome =
                result.outcome();

        short[] distance =
                result.distance();


        FourPieceGenericPrimitiveMoveGenerator.Buffer moves =
                new FourPieceGenericPrimitiveMoveGenerator.Buffer(
                        64
                );


        ThreePieceTablebaseService threePieceService =
                new ThreePieceTablebaseService();


        Random random =
                new Random(
                        seed
                );


        int checked =
                0;

        long attempts =
                0;

        long checkedEdges =
                0;


        while (checked < target) {

            int state =
                    random.nextInt(
                            FourPieceGenericPrimitiveState.STATE_COUNT
                    );


            attempts++;


            byte parentOutcome =
                    outcome[state];


            if (parentOutcome
                    == FourPieceTablebase.INVALID) {

                continue;
            }


            int moveCount =
                    FourPieceGenericPrimitiveMoveGenerator
                            .generateLegalSuccessors(
                                    state,
                                    material,
                                    sameSideOwnerIsWhite,
                                    moves
                            );


            /*
             * Terminal states.
             */
            if (moveCount == 0) {

                boolean check =
                        FourPieceGenericPrimitiveRules
                                .sideToMoveIsInCheck(
                                        state,
                                        material,
                                        sameSideOwnerIsWhite
                                );


                if (check) {

                    if (parentOutcome
                            != FourPieceTablebase.LOSS
                            ||
                            distance[state]
                                    != 0) {

                        throw new IllegalStateException(
                                "Terminal checkmate recurrence failed at state "
                                        + state
                        );
                    }

                } else {

                    if (parentOutcome
                            != FourPieceTablebase.DRAW
                            ||
                            distance[state]
                                    != -1) {

                        throw new IllegalStateException(
                                "Terminal stalemate recurrence failed at state "
                                        + state
                        );
                    }
                }


                checked++;

                continue;
            }


            boolean hasLossChild =
                    false;

            boolean hasDrawChild =
                    false;

            boolean allChildrenWin =
                    true;


            int minimumLossDistance =
                    Integer.MAX_VALUE;

            int maximumWinDistance =
                    -1;


            for (int i = 0;
                 i < moveCount;
                 i++) {

                ChildResult child;


                if (!moves.isBoundary(
                        i
                )) {

                    int childState =
                            moves.state(
                                    i
                            );


                    child =
                            new ChildResult(
                                    outcome[childState],
                                    distance[childState]
                            );

                } else {

                    child =
                            probeBoundary(
                                    moves,
                                    i,
                                    threePieceService
                            );
                }


                checkedEdges++;


                if (child.outcome()
                        == FourPieceTablebase.LOSS) {

                    hasLossChild =
                            true;

                    allChildrenWin =
                            false;


                    minimumLossDistance =
                            Math.min(
                                    minimumLossDistance,
                                    child.distance()
                            );


                } else if (child.outcome()
                        == FourPieceTablebase.DRAW) {

                    hasDrawChild =
                            true;

                    allChildrenWin =
                            false;


                } else if (child.outcome()
                        == FourPieceTablebase.WIN) {

                    maximumWinDistance =
                            Math.max(
                                    maximumWinDistance,
                                    child.distance()
                            );


                } else {

                    throw new IllegalStateException(
                            "Unsolved child encountered while validating state "
                                    + state
                    );
                }
            }


            if (parentOutcome
                    == FourPieceTablebase.WIN) {

                if (!hasLossChild) {

                    throw new IllegalStateException(
                            "WIN has no LOSS child at state "
                                    + state
                    );
                }


                int expected =
                        minimumLossDistance + 1;


                if (distance[state]
                        != expected) {

                    throw new IllegalStateException(
                            "WIN DTM mismatch at state "
                                    + state
                                    + ": expected "
                                    + expected
                                    + ", found "
                                    + distance[state]
                    );
                }


            } else if (parentOutcome
                    == FourPieceTablebase.LOSS) {

                if (!allChildrenWin) {

                    throw new IllegalStateException(
                            "LOSS has a non-WIN child at state "
                                    + state
                    );
                }


                int expected =
                        maximumWinDistance + 1;


                if (distance[state]
                        != expected) {

                    throw new IllegalStateException(
                            "LOSS DTM mismatch at state "
                                    + state
                                    + ": expected "
                                    + expected
                                    + ", found "
                                    + distance[state]
                    );
                }


            } else if (parentOutcome
                    == FourPieceTablebase.DRAW) {

                if (hasLossChild) {

                    throw new IllegalStateException(
                            "DRAW has a LOSS child at state "
                                    + state
                    );
                }


                if (!hasDrawChild) {

                    throw new IllegalStateException(
                            "Nonterminal DRAW has no DRAW child at state "
                                    + state
                    );
                }


                if (distance[state]
                        != -1) {

                    throw new IllegalStateException(
                            "DRAW has DTM at state "
                                    + state
                    );
                }


            } else {

                throw new IllegalStateException(
                        "Unexpected parent outcome at state "
                                + state
                );
            }


            checked++;
        }


        System.out.println();

        System.out.println(
                "Exact recurrence sample"
        );

        System.out.println(
                "Strong side: "
                        + (sameSideOwnerIsWhite
                        ? "WHITE"
                        : "BLACK")
        );

        System.out.println(
                "Legal states checked: "
                        + String.format(
                        "%,d",
                        checked
                )
        );

        System.out.println(
                "Random attempts: "
                        + String.format(
                        "%,d",
                        attempts
                )
        );

        System.out.println(
                "Child edges checked: "
                        + String.format(
                        "%,d",
                        checkedEdges
                )
        );

        System.out.println(
                "WDL/DTM recurrence: PASSED"
        );
    }


    /**
     * Exact color-reversal symmetry over the complete raw state space.
     */
    private static void verifyColorSymmetry(
            FourPieceGenericPrimitiveRetrogradeBuilder.Result white,
            FourPieceGenericPrimitiveRetrogradeBuilder.Result black
    ) {

        byte[] whiteOutcome =
                white.outcome();

        short[] whiteDistance =
                white.distance();

        byte[] blackOutcome =
                black.outcome();

        short[] blackDistance =
                black.distance();


        long checked =
                0;


        for (int state = 0;
             state < FourPieceGenericPrimitiveState.STATE_COUNT;
             state++) {

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

            int bishop =
                    FourPieceGenericPrimitiveState.secondExtra(
                            state
                    );

            boolean blackToMove =
                    FourPieceGenericPrimitiveState.blackToMove(
                            state
                    );


            int reversed =
                    FourPieceGenericPrimitiveState.encode(
                            bk,
                            wk,
                            queen,
                            bishop,
                            !blackToMove
                    );


            if (whiteOutcome[state]
                    != blackOutcome[reversed]) {

                throw new IllegalStateException(
                        "KQBK color-symmetry WDL mismatch: "
                                + state
                                + " <-> "
                                + reversed
                                + ", white="
                                + whiteOutcome[state]
                                + ", black="
                                + blackOutcome[reversed]
                );
            }


            if (whiteDistance[state]
                    != blackDistance[reversed]) {

                throw new IllegalStateException(
                        "KQBK color-symmetry DTM mismatch: "
                                + state
                                + " <-> "
                                + reversed
                                + ", white="
                                + whiteDistance[state]
                                + ", black="
                                + blackDistance[reversed]
                );
            }


            checked++;
        }


        System.out.println();

        System.out.println(
                "Complete color-symmetry verification"
        );

        System.out.println(
                "Raw states compared: "
                        + String.format(
                        "%,d",
                        checked
                )
        );

        System.out.println(
                "WDL symmetry: PASSED"
        );

        System.out.println(
                "DTM symmetry: PASSED"
        );
    }


    /**
     * Three-piece boundary probe used independently by the validation
     * harness.
     */
    private static ChildResult probeBoundary(
            FourPieceGenericPrimitiveMoveGenerator.Buffer moves,
            int index,
            ThreePieceTablebaseService service
    ) {

        PieceType survivingType =
                moves.survivingPieceType(
                        index
                );


        if (survivingType
                == PieceType.BISHOP
                ||
                survivingType
                        == PieceType.KNIGHT) {

            return new ChildResult(
                    FourPieceTablebase.DRAW,
                    -1
            );
        }


        if (survivingType
                != PieceType.QUEEN
                &&
                survivingType
                        != PieceType.ROOK) {

            throw new IllegalStateException(
                    "Unexpected Tier-0 boundary type: "
                            + survivingType
            );
        }


        ThreePieceTablebase tablebase =
                service.get(
                        survivingType,
                        moves.survivingPieceIsWhite(
                                index
                        )
                                ? main.java.chess.model.Color.WHITE
                                : main.java.chess.model.Color.BLACK
                );


        ThreePieceTablebase.Probe probe =
                tablebase.probeSquares(
                        moves.boundaryWhiteKing(
                                index
                        ),
                        moves.boundaryBlackKing(
                                index
                        ),
                        moves.survivingPieceSquare(
                                index
                        ),
                        moves.boundaryBlackToMove(
                                index
                        )
                );


        byte mapped =
                switch (probe.outcome()) {

                    case WIN ->
                            FourPieceTablebase.WIN;

                    case LOSS ->
                            FourPieceTablebase.LOSS;

                    case DRAW ->
                            FourPieceTablebase.DRAW;

                    case UNSUPPORTED ->
                            throw new IllegalStateException(
                                    "Three-piece boundary returned UNSUPPORTED."
                            );
                };


        return new ChildResult(
                mapped,
                probe.mateDistance()
        );
    }


    private static void printSummary(
            String label,
            FourPieceGenericPrimitiveRetrogradeBuilder.Result result
    ) {

        System.out.println();

        System.out.println(
                label
        );

        System.out.println(
                "  Legal states: "
                        + String.format(
                        "%,d",
                        result.legalStates()
                )
        );

        System.out.println(
                "  WIN: "
                        + String.format(
                        "%,d",
                        result.wins()
                )
        );

        System.out.println(
                "  LOSS: "
                        + String.format(
                        "%,d",
                        result.losses()
                )
        );

        System.out.println(
                "  DRAW: "
                        + String.format(
                        "%,d",
                        result.draws()
                )
        );

        System.out.println(
                "  Maximum DTM: "
                        + result.maximumDistance()
        );

        System.out.println(
                "  Build time: "
                        + result.totalMillis()
                        + " ms"
        );
    }


    private static String digest(
            byte[] outcome,
            short[] distance
    ) {

        final MessageDigest digest;


        try {

            digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

        } catch (NoSuchAlgorithmException exception) {

            throw new IllegalStateException(
                    "SHA-256 is unavailable.",
                    exception
            );
        }


        digest.update(
                outcome
        );


        byte[] buffer =
                new byte[
                        16 * 1024
                        ];


        int index =
                0;


        for (short value :
                distance) {

            buffer[index++] =
                    (byte) (
                            (value >>> 8)
                                    & 0xFF
                    );

            buffer[index++] =
                    (byte) (
                            value
                                    & 0xFF
                    );


            if (index
                    == buffer.length) {

                digest.update(
                        buffer
                );

                index =
                        0;
            }
        }


        if (index > 0) {

            digest.update(
                    buffer,
                    0,
                    index
            );
        }


        return HexFormat.of()
                .formatHex(
                        digest.digest()
                );
    }


    private record ChildResult(
            byte outcome,
            int distance
    ) {

    }
}
package main.java.chess.endgame;

import main.java.chess.model.Color;
import main.java.chess.model.Move;
import main.java.chess.model.PieceType;
import main.java.chess.model.Square;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Milestone 33 correctness gate.
 *
 * Compares the allocation-free primitive KQPK generator against the M32
 * object-model classifier on a large randomized sample for both strong colors.
 *
 * The comparison is by semantic edge identity:
 *
 *     same-class child state
 *     KQK/KPK boundary + surviving piece square
 *     promotion type + promotion square
 *
 * We also retain from/to squares in the primitive buffer so mismatches are
 * diagnosable without reconstructing a Position.
 */
public final class FourPieceTierOneKqpkPrimitiveMoveVerificationMain {

    private static final int DEFAULT_SAMPLES =
            100_000;


    private FourPieceTierOneKqpkPrimitiveMoveVerificationMain() {
    }


    public static void main(
            String[] args
    ) {

        int samples =
                args.length > 0
                        ? Integer.parseInt(
                        args[0]
                )
                        : DEFAULT_SAMPLES;


        if (samples < 1) {

            throw new IllegalArgumentException(
                    "Sample count must be positive."
            );
        }


        verify(
                samples,
                Color.WHITE,
                0x4B51504B3301L
        );

        verify(
                samples,
                Color.BLACK,
                0x4B51504B3302L
        );


        System.out.println();

        System.out.println(
                "ALLOCATION-FREE KQPK PRIMITIVE MOVE VERIFICATION PASSED"
        );

        System.out.println(
                "NEXT: BENCHMARK KQPK PRIMITIVE SUCCESSOR GENERATION"
        );
    }


    private static void verify(
            int samples,
            Color strongColor,
            long seed
    ) {

        boolean strongIsWhite =
                strongColor
                        == Color.WHITE;

        Random random =
                new Random(
                        seed
                );

        FourPieceTierOneKqpkSuccessorClassifier oracle =
                new FourPieceTierOneKqpkSuccessorClassifier();

        FourPieceTierOneKqpkPrimitiveMoveGenerator.Buffer primitive =
                new FourPieceTierOneKqpkPrimitiveMoveGenerator.Buffer(
                        64
                );


        int checked =
                0;

        long attempts =
                0;

        long edges =
                0;

        long sameClass =
                0;

        long threePiece =
                0;

        long promotions =
                0;


        while (checked < samples) {

            attempts++;


            int pawnRank =
                    1 + random.nextInt(
                            6
                    );

            int pawn =
                    pawnRank * 8
                            + random.nextInt(
                            8
                    );

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


            if (!distinct(
                    wk,
                    bk,
                    queen,
                    pawn
            )) {

                continue;
            }


            int state =
                    FourPieceGenericPrimitiveState.encode(
                            wk,
                            bk,
                            queen,
                            pawn,
                            random.nextBoolean()
                    );


            if (!FourPieceTierOneKqpkPrimitiveMoveGenerator
                    .isStructurallyLegal(
                            state,
                            strongIsWhite
                    )) {

                continue;
            }


            List<FourPieceTierOneKqpkSuccessorClassifier.Successor>
                    expected;


            try {

                expected =
                        oracle.generate(
                                state,
                                strongColor
                        );

            } catch (IllegalArgumentException exception) {

                /*
                 * The object-model bridge can reject a raw primitive state
                 * when its general-purpose move layer exposes an impossible
                 * king-capture artifact. Such a state is outside the oracle's
                 * usable domain and is not counted as checked.
                 */
                continue;
            }


            int count =
                    FourPieceTierOneKqpkPrimitiveMoveGenerator
                            .generateLegalSuccessors(
                                    state,
                                    strongIsWhite,
                                    primitive
                            );


            Set<String> expectedKeys =
                    oracleKeys(
                            expected
                    );

            Set<String> actualKeys =
                    primitiveKeys(
                            primitive,
                            count
                    );


            if (!expectedKeys.equals(
                    actualKeys
            )) {

                System.out.println();

                System.out.println(
                        "KQPK primitive mismatch"
                );

                System.out.println(
                        "  strong: "
                                + strongColor
                );

                System.out.println(
                        "  state: "
                                + state
                );

                System.out.println(
                        "  WK/BK/Q/P/stm: "
                                + algebraic(
                                wk
                        )
                                + " / "
                                + algebraic(
                                bk
                        )
                                + " / "
                                + algebraic(
                                queen
                        )
                                + " / "
                                + algebraic(
                                pawn
                        )
                                + " / "
                                + (FourPieceGenericPrimitiveState
                                .blackToMove(
                                        state
                                )
                                ? "BLACK"
                                : "WHITE")
                );

                System.out.println(
                        "  expected count: "
                                + expectedKeys.size()
                );

                System.out.println(
                        "  primitive count: "
                                + actualKeys.size()
                );

                Set<String> missing =
                        new HashSet<>(
                                expectedKeys
                        );

                missing.removeAll(
                        actualKeys
                );

                Set<String> extra =
                        new HashSet<>(
                                actualKeys
                        );

                extra.removeAll(
                        expectedKeys
                );

                System.out.println(
                        "  missing: "
                                + limited(
                                missing
                        )
                );

                System.out.println(
                        "  extra: "
                                + limited(
                                extra
                        )
                );


                throw new IllegalStateException(
                        "Allocation-free KQPK primitive move generator mismatch."
                );
            }


            if (count
                    != expected.size()) {

                throw new IllegalStateException(
                        "Primitive KQPK generator produced duplicate semantic edges."
                );
            }


            for (int i = 0;
                 i < count;
                 i++) {

                switch (primitive.boundaryType(
                        i
                )) {

                    case FourPieceTierOneKqpkPrimitiveMoveGenerator
                                 .BOUNDARY_NONE -> {

                        sameClass++;
                    }

                    case FourPieceTierOneKqpkPrimitiveMoveGenerator
                                 .BOUNDARY_KQK,
                         FourPieceTierOneKqpkPrimitiveMoveGenerator
                                 .BOUNDARY_KPK -> {

                        threePiece++;
                    }

                    case FourPieceTierOneKqpkPrimitiveMoveGenerator
                                 .BOUNDARY_PROMOTION -> {

                        promotions++;
                    }

                    default ->
                            throw new IllegalStateException(
                                    "Unknown primitive KQPK boundary."
                            );
                }
            }


            edges +=
                    count;

            checked++;
        }


        System.out.println();

        System.out.println(
                "Primitive KQPK verification — strong "
                        + strongColor
        );

        System.out.println(
                "  Positions checked: "
                        + checked
        );

        System.out.println(
                "  Attempts: "
                        + attempts
        );

        System.out.println(
                "  Successor edges: "
                        + edges
        );

        System.out.println(
                "  Same-class: "
                        + sameClass
        );

        System.out.println(
                "  Three-piece boundaries: "
                        + threePiece
        );

        System.out.println(
                "  Tier-0 promotions: "
                        + promotions
        );

        System.out.println(
                "  Semantic mismatches: 0"
        );

        System.out.println(
                "  Duplicate-edge mismatches: 0"
        );

        System.out.println(
                "  PASSED"
        );
    }


    private static Set<String> oracleKeys(
            List<FourPieceTierOneKqpkSuccessorClassifier.Successor>
                    successors
    ) {

        Set<String> result =
                new HashSet<>();


        for (FourPieceTierOneKqpkSuccessorClassifier.Successor successor :
                successors) {

            Move move =
                    successor.move();


            String key =
                    switch (successor.kind()) {

                        case SAME_CLASS ->
                                "S:"
                                        + successor.childState();

                        case THREE_PIECE_BOUNDARY ->
                                "B:"
                                        + (successor.threePieceType()
                                        == PieceType.QUEEN
                                        ? "Q"
                                        : "P")
                                        + ":CAP@"
                                        + index(
                                        move.to()
                                );

                        case TIER_ZERO_PROMOTION ->
                                "R:"
                                        + move.promotion()
                                        + ":"
                                        + index(
                                        move.to()
                                );
                    };


            if (!result.add(
                    key
            )) {

                throw new IllegalStateException(
                        "Duplicate oracle semantic edge: "
                                + key
                );
            }
        }


        return result;
    }


    private static Set<String> primitiveKeys(
            FourPieceTierOneKqpkPrimitiveMoveGenerator.Buffer primitive,
            int count
    ) {

        Set<String> result =
                new HashSet<>();


        for (int i = 0;
             i < count;
             i++) {

            int boundary =
                    primitive.boundaryType(
                            i
                    );


            String key;


            if (boundary
                    == FourPieceTierOneKqpkPrimitiveMoveGenerator
                    .BOUNDARY_NONE) {

                key =
                        "S:"
                                + primitive.state(
                                i
                        );

            } else if (boundary
                    == FourPieceTierOneKqpkPrimitiveMoveGenerator
                    .BOUNDARY_KQK) {

                key =
                        "B:Q:CAP@"
                                + primitive.toSquare(
                                i
                        );

            } else if (boundary
                    == FourPieceTierOneKqpkPrimitiveMoveGenerator
                    .BOUNDARY_KPK) {

                key =
                        "B:P:CAP@"
                                + primitive.toSquare(
                                i
                        );

            } else if (boundary
                    == FourPieceTierOneKqpkPrimitiveMoveGenerator
                    .BOUNDARY_PROMOTION) {

                key =
                        "R:"
                                + primitive.promotionType(
                                i
                        )
                                + ":"
                                + primitive.promotionSquare(
                                i
                        );

            } else {

                throw new IllegalStateException(
                        "Unknown primitive boundary type: "
                                + boundary
                );
            }


            if (!result.add(
                    key
            )) {

                throw new IllegalStateException(
                        "Duplicate primitive semantic edge: "
                                + key
                );
            }
        }


        return result;
    }


    private static int index(
            Square square
    ) {

        return square.rank() * 8
                + square.file();
    }


    private static String algebraic(
            int square
    ) {

        return new Square(
                square & 7,
                square >>> 3
        ).toAlgebraic();
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


    private static String limited(
            Set<String> values
    ) {

        if (values.isEmpty()) {
            return "[]";
        }


        StringBuilder builder =
                new StringBuilder(
                        "["
                );

        int shown =
                0;


        for (String value :
                values) {

            if (shown > 0) {
                builder.append(
                        ", "
                );
            }

            builder.append(
                    value
            );

            shown++;


            if (shown == 10) {
                break;
            }
        }


        if (values.size() > shown) {

            builder.append(
                    ", ..."
            );
        }


        builder.append(
                ']'
        );

        return builder.toString();
    }
}

package main.java.chess.endgame;

/**
 * Milestone 34.
 *
 * Performance gate for allocation-free Tier-1 KQPK primitive successor
 * generation.
 *
 * The benchmark walks a deterministic prefix of the dense four-piece state
 * space so RNG cost does not contaminate the measurement. For each raw state:
 *
 *     1. primitive structural legality is checked;
 *     2. legal KQPK states generate all primitive successors;
 *     3. successor categories are counted;
 *     4. a checksum consumes the output so the JVM cannot treat the work as
 *        dead computation.
 *
 * Run with an optional raw-state sample size:
 *
 *     FourPieceTierOneKqpkPrimitiveMoveBenchmarkMain 5000000
 *
 * Default: 1,000,000 raw states per strong-color orientation.
 */
public final class FourPieceTierOneKqpkPrimitiveMoveBenchmarkMain {

    private static final int DEFAULT_SAMPLE =
            1_000_000;

    private static final int MAX_SAMPLE =
            FourPieceGenericPrimitiveState.STATE_COUNT;


    private FourPieceTierOneKqpkPrimitiveMoveBenchmarkMain() {
    }


    public static void main(
            String[] args
    ) {

        int sample =
                args.length > 0
                        ? Integer.parseInt(
                        args[0]
                )
                        : DEFAULT_SAMPLE;


        if (sample < 1
                || sample > MAX_SAMPLE) {

            throw new IllegalArgumentException(
                    "Sample must be between 1 and "
                            + MAX_SAMPLE
                            + "."
            );
        }


        benchmark(
                sample,
                true
        );

        benchmark(
                sample,
                false
        );
    }


    private static void benchmark(
            int sample,
            boolean strongIsWhite
    ) {

        FourPieceTierOneKqpkPrimitiveMoveGenerator.Buffer buffer =
                new FourPieceTierOneKqpkPrimitiveMoveGenerator.Buffer(
                        64
                );


        int warmup =
                Math.min(
                        200_000,
                        sample
                );


        long warmupChecksum =
                0;


        for (int state = 0;
             state < warmup;
             state++) {

            if (!FourPieceTierOneKqpkPrimitiveMoveGenerator
                    .isStructurallyLegal(
                            state,
                            strongIsWhite
                    )) {

                continue;
            }


            int count =
                    FourPieceTierOneKqpkPrimitiveMoveGenerator
                            .generateLegalSuccessors(
                                    state,
                                    strongIsWhite,
                                    buffer
                            );


            warmupChecksum +=
                    count;


            for (int i = 0;
                 i < count;
                 i++) {

                warmupChecksum +=
                        consume(
                                buffer,
                                i
                        );
            }
        }


        long started =
                System.nanoTime();


        int legalStates =
                0;

        long successors =
                0;

        long sameClass =
                0;

        long threePieceBoundaries =
                0;

        long promotions =
                0;

        long checksum =
                0;


        for (int state = 0;
             state < sample;
             state++) {

            if (!FourPieceTierOneKqpkPrimitiveMoveGenerator
                    .isStructurallyLegal(
                            state,
                            strongIsWhite
                    )) {

                continue;
            }


            legalStates++;


            int count =
                    FourPieceTierOneKqpkPrimitiveMoveGenerator
                            .generateLegalSuccessors(
                                    state,
                                    strongIsWhite,
                                    buffer
                            );


            successors +=
                    count;


            for (int i = 0;
                 i < count;
                 i++) {

                int boundary =
                        buffer.boundaryType(
                                i
                        );


                switch (boundary) {

                    case FourPieceTierOneKqpkPrimitiveMoveGenerator
                                 .BOUNDARY_NONE -> {

                        sameClass++;
                    }

                    case FourPieceTierOneKqpkPrimitiveMoveGenerator
                                 .BOUNDARY_KQK,
                         FourPieceTierOneKqpkPrimitiveMoveGenerator
                                 .BOUNDARY_KPK -> {

                        threePieceBoundaries++;
                    }

                    case FourPieceTierOneKqpkPrimitiveMoveGenerator
                                 .BOUNDARY_PROMOTION -> {

                        promotions++;
                    }

                    default ->
                            throw new IllegalStateException(
                                    "Unknown KQPK primitive boundary: "
                                            + boundary
                            );
                }


                checksum +=
                        consume(
                                buffer,
                                i
                        );
            }
        }


        long elapsed =
                System.nanoTime()
                        - started;


        double seconds =
                elapsed
                        / 1_000_000_000.0;

        double millis =
                elapsed
                        / 1_000_000.0;

        double rawStatesPerSecond =
                sample
                        / seconds;

        double legalStatesPerSecond =
                legalStates == 0
                        ? 0.0
                        : legalStates
                        / seconds;

        double successorsPerSecond =
                successors == 0
                        ? 0.0
                        : successors
                        / seconds;

        double projectedFullRawSeconds =
                FourPieceGenericPrimitiveState.STATE_COUNT
                        / rawStatesPerSecond;


        System.out.println();

        System.out.println(
                "Primitive KQPK move benchmark — strong "
                        + (strongIsWhite
                        ? "WHITE"
                        : "BLACK")
        );

        System.out.println(
                "============================================"
        );

        System.out.println(
                "Sample raw states: "
                        + sample
        );

        System.out.println(
                "Structurally legal: "
                        + legalStates
        );

        System.out.println(
                "Generated successors: "
                        + successors
        );

        System.out.println(
                "Same-class successors: "
                        + sameClass
        );

        System.out.println(
                "Three-piece boundaries: "
                        + threePieceBoundaries
        );

        System.out.println(
                "Tier-0 promotions: "
                        + promotions
        );

        System.out.printf(
                "Elapsed: %.3f ms%n",
                millis
        );

        System.out.printf(
                "Raw throughput: %,.0f states/sec%n",
                rawStatesPerSecond
        );

        System.out.printf(
                "Legal-state throughput: %,.0f states/sec%n",
                legalStatesPerSecond
        );

        System.out.printf(
                "Successor throughput: %,.0f edges/sec%n",
                successorsPerSecond
        );

        System.out.printf(
                "Projected full raw scan: %.3f sec%n",
                projectedFullRawSeconds
        );

        System.out.println(
                "Checksum: "
                        + checksum
        );

        System.out.println(
                "Warmup checksum: "
                        + warmupChecksum
        );
    }


    private static long consume(
            FourPieceTierOneKqpkPrimitiveMoveGenerator.Buffer buffer,
            int index
    ) {

        int boundary =
                buffer.boundaryType(
                        index
                );


        long value =
                boundary * 31L
                        + buffer.fromSquare(
                        index
                ) * 17L
                        + buffer.toSquare(
                        index
                ) * 13L;


        if (boundary
                == FourPieceTierOneKqpkPrimitiveMoveGenerator
                .BOUNDARY_NONE) {

            value +=
                    buffer.state(
                            index
                    ) * 7L;

        } else if (boundary
                == FourPieceTierOneKqpkPrimitiveMoveGenerator
                .BOUNDARY_PROMOTION) {

            value +=
                    promotionCode(
                            buffer.promotionType(
                                    index
                            )
                    ) * 19L
                            + buffer.promotionSquare(
                            index
                    );

        } else {

            value +=
                    buffer.survivingPieceSquare(
                            index
                    ) * 23L;
        }


        return value;
    }


    private static int promotionCode(
            main.java.chess.model.PieceType type
    ) {

        if (type == null) {
            return 0;
        }


        return switch (type) {

            case QUEEN ->
                    1;

            case ROOK ->
                    2;

            case BISHOP ->
                    3;

            case KNIGHT ->
                    4;

            default ->
                    throw new IllegalArgumentException(
                            "Unexpected promotion type: "
                                    + type
                    );
        };
    }
}

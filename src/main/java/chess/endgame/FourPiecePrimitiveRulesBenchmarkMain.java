package main.java.chess.endgame;


/**
 * Benchmarks primitive KQRK structural legality and check detection.
 *
 * This is the second performance gate before implementing primitive move
 * generation and reconnecting retrograde analysis.
 */
public final class FourPiecePrimitiveRulesBenchmarkMain {

    private static final int DEFAULT_SAMPLE =
            1_000_000;


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
                || sample
                > KqrkPrimitiveState.STATE_COUNT) {

            throw new IllegalArgumentException(
                    "Sample must be between 1 and "
                            + KqrkPrimitiveState.STATE_COUNT
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

        String color =
                strongIsWhite
                        ? "WHITE"
                        : "BLACK";


        System.out.println();
        System.out.println(
                "Primitive KQRK rules benchmark — strong "
                        + color
        );

        System.out.println(
                "Sample states: "
                        + sample
        );


        /*
         * Small warm-up so the result better reflects optimized JIT code.
         */
        int warmup =
                Math.min(
                        100_000,
                        sample
                );


        long warmupChecksum =
                0;


        for (int state = 0;
             state < warmup;
             state++) {

            if (KqrkPrimitiveRules.isStructurallyLegal(
                    state,
                    strongIsWhite
            )) {

                warmupChecksum +=
                        state;
            }
        }


        long started =
                System.nanoTime();


        int legal =
                0;

        int checkedSideToMove =
                0;

        long checksum =
                0;


        for (int state = 0;
             state < sample;
             state++) {

            if (!KqrkPrimitiveRules.isStructurallyLegal(
                    state,
                    strongIsWhite
            )) {

                continue;
            }


            legal++;


            boolean inCheck =
                    KqrkPrimitiveRules.sideToMoveIsInCheck(
                            state,
                            strongIsWhite
                    );


            if (inCheck) {

                checkedSideToMove++;
            }


            checksum +=
                    state * 31L
                            + (inCheck
                            ? 7
                            : 3);
        }


        long elapsedNanos =
                System.nanoTime()
                        - started;


        double elapsedMs =
                elapsedNanos
                        / 1_000_000.0;


        double statesPerSecond =
                sample
                        / (elapsedNanos
                        / 1_000_000_000.0);


        double projectedSeconds =
                KqrkPrimitiveState.STATE_COUNT
                        / statesPerSecond;


        System.out.printf(
                "Elapsed: %.3f ms%n",
                elapsedMs
        );

        System.out.printf(
                "Throughput: %,.0f raw states/sec%n",
                statesPerSecond
        );

        System.out.println(
                "Structurally legal: "
                        + legal
        );

        System.out.println(
                "Legal with side-to-move in check: "
                        + checkedSideToMove
        );

        System.out.printf(
                "Projected full primitive legality/check scan: %.3f sec%n",
                projectedSeconds
        );

        System.out.println(
                "Checksum: "
                        + checksum
        );

        /*
         * Keep the warm-up observable as well.
         */
        if (warmupChecksum
                == Long.MIN_VALUE) {

            System.out.println(
                    "Warmup checksum: "
                            + warmupChecksum
            );
        }
    }
}

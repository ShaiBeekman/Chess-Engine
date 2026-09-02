package main.java.chess.endgame;


/**
 * Performance gate for allocation-free KQRK legal successor generation.
 */
public final class FourPiecePrimitiveMoveBenchmarkMain {

    private static final int DEFAULT_SAMPLE = 1_000_000;


    public static void main(String[] args) {

        int sample =
                args.length > 0
                        ? Integer.parseInt(args[0])
                        : DEFAULT_SAMPLE;

        if (sample < 1
                || sample > KqrkPrimitiveState.STATE_COUNT) {
            throw new IllegalArgumentException(
                    "Sample must be between 1 and "
                            + KqrkPrimitiveState.STATE_COUNT
            );
        }

        benchmark(sample, true);
        benchmark(sample, false);
    }


    private static void benchmark(
            int sample,
            boolean strongIsWhite
    ) {
        KqrkPrimitiveMoveGenerator.Buffer buffer =
                new KqrkPrimitiveMoveGenerator.Buffer(64);

        int warmup = Math.min(100_000, sample);

        long warmupChecksum = 0;

        for (int state = 0; state < warmup; state++) {
            if (!KqrkPrimitiveRules.isStructurallyLegal(
                    state,
                    strongIsWhite
            )) {
                continue;
            }

            int count =
                    KqrkPrimitiveMoveGenerator.generateLegalSuccessors(
                            state,
                            strongIsWhite,
                            buffer
                    );

            warmupChecksum += count;
        }

        long started = System.nanoTime();

        int legalStates = 0;
        long successors = 0;
        long inClass = 0;
        long boundaries = 0;
        long checksum = 0;

        for (int state = 0; state < sample; state++) {
            if (!KqrkPrimitiveRules.isStructurallyLegal(
                    state,
                    strongIsWhite
            )) {
                continue;
            }

            legalStates++;

            int count =
                    KqrkPrimitiveMoveGenerator.generateLegalSuccessors(
                            state,
                            strongIsWhite,
                            buffer
                    );

            successors += count;

            for (int i = 0; i < count; i++) {
                int boundary = buffer.boundaryType(i);

                if (boundary
                        == KqrkPrimitiveMoveGenerator.BOUNDARY_NONE) {
                    inClass++;
                    checksum += buffer.state(i) * 17L + i;
                } else {
                    boundaries++;
                    checksum += boundary * 31L
                            + buffer.survivingPieceSquare(i);
                }
            }
        }

        long elapsed = System.nanoTime() - started;

        double seconds = elapsed / 1_000_000_000.0;
        double ms = elapsed / 1_000_000.0;
        double rawPerSecond = sample / seconds;
        double successorPerSecond =
                successors == 0 ? 0 : successors / seconds;
        double projected =
                KqrkPrimitiveState.STATE_COUNT / rawPerSecond;

        System.out.println();
        System.out.println(
                "Primitive KQRK move benchmark — strong "
                        + (strongIsWhite ? "WHITE" : "BLACK")
        );
        System.out.println("Sample raw states: " + sample);
        System.out.println("Structurally legal: " + legalStates);
        System.out.println("Generated successors: " + successors);
        System.out.println("In-class successors: " + inClass);
        System.out.println("3-piece boundaries: " + boundaries);
        System.out.printf("Elapsed: %.3f ms%n", ms);
        System.out.printf(
                "Raw throughput: %,.0f states/sec%n",
                rawPerSecond
        );
        System.out.printf(
                "Move throughput: %,.0f successors/sec%n",
                successorPerSecond
        );
        System.out.printf(
                "Projected full raw-state move scan: %.3f sec%n",
                projected
        );
        System.out.println("Checksum: " + checksum);

        if (warmupChecksum == Long.MIN_VALUE) {
            System.out.println(warmupChecksum);
        }
    }
}

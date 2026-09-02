package main.java.chess.endgame;

/**
 * M64 — Five-Piece Feasibility Benchmark.
 *
 * This milestone deliberately does NOT solve a five-piece tablebase.
 *
 * It measures the cost of scanning a raw five-piece packed state space and
 * reports exact structural counts / storage estimates that we can use to
 * choose the representation for 5+ piece tablebases.
 *
 * Raw five-piece layout:
 *
 *   64^5 * 2 = 2,147,483,648 states
 *
 * Conceptual packed fields:
 *
 *   bit 0       side to move
 *   bits 1..6   third extra piece
 *   bits 7..12  second extra piece
 *   bits 13..18 first extra piece
 *   bits 19..24 black king
 *   bits 25..30 white king
 *
 * The full raw domain is one state larger than Integer.MAX_VALUE, so five
 * pieces are also the point where a signed int can no longer represent every
 * raw state ID.  M64 therefore uses long state IDs.
 */
public final class FivePieceFeasibilityBenchmarkMain {

    public static final String BUILD_ID =
            "M64-FIVE-PIECE-FEASIBILITY-V1";

    private static final long RAW_STATE_COUNT =
            64L * 64L * 64L * 64L * 64L * 2L;

    private static final long DEFAULT_SAMPLE =
            5_000_000L;

    private FivePieceFeasibilityBenchmarkMain() {
    }

    public static void main(
            String[] args
    ) {

        if (args.length > 1) {
            throw new IllegalArgumentException(
                    "Usage: FivePieceFeasibilityBenchmarkMain [sample-size]"
            );
        }

        long sample =
                args.length == 0
                        ? DEFAULT_SAMPLE
                        : Long.parseLong(args[0]);

        if (sample < 1L
                || sample > RAW_STATE_COUNT) {

            throw new IllegalArgumentException(
                    "Sample must be between 1 and "
                            + RAW_STATE_COUNT
                            + "."
            );
        }

        System.out.println(
                "M64 — Five-Piece Feasibility Benchmark"
        );
        System.out.println(
                "======================================"
        );
        System.out.println(
                "BUILD_ID: "
                        + BUILD_ID
        );

        printStateSpace();
        printStorageEstimates();
        printSymmetryEstimates();

        /*
         * Run twice.  The first pass warms the JIT; the second is the
         * measurement we report.
         */
        long warmup =
                Math.min(
                        sample,
                        500_000L
                );

        scan(
                warmup,
                false
        );

        ScanResult result =
                scan(
                        sample,
                        true
                );

        printConclusion(
                result
        );
    }

    private static void printStateSpace() {

        long fourPieceRaw =
                64L * 64L * 64L * 64L * 2L;

        double multiplier =
                (double) RAW_STATE_COUNT
                        / fourPieceRaw;

        long distinctSquareStates =
                permutation(
                        64,
                        5
                ) * 2L;

        double distinctFraction =
                (double) distinctSquareStates
                        / RAW_STATE_COUNT;

        System.out.println();
        System.out.println(
                "Raw state-space"
        );
        System.out.println(
                "---------------"
        );

        System.out.printf(
                "Four-piece raw states: %,d%n",
                fourPieceRaw
        );

        System.out.printf(
                "Five-piece raw states: %,d%n",
                RAW_STATE_COUNT
        );

        System.out.printf(
                "Raw growth factor: %.1fx%n",
                multiplier
        );

        System.out.printf(
                "Five-piece raw IDs exceed Integer.MAX_VALUE by: %,d%n",
                RAW_STATE_COUNT
                        - Integer.MAX_VALUE
        );

        System.out.println(
                "Primitive state ID required: long"
        );

        System.out.printf(
                "Distinct-square raw states: %,d (%.2f%%)%n",
                distinctSquareStates,
                distinctFraction * 100.0
        );
    }

    private static void printStorageEstimates() {

        System.out.println();
        System.out.println(
                "Flat-array storage estimates"
        );
        System.out.println(
                "----------------------------"
        );

        printStorage(
                "1 byte/state",
                RAW_STATE_COUNT
        );

        printStorage(
                "2 bytes/state",
                RAW_STATE_COUNT * 2L
        );

        printStorage(
                "4 bytes/state",
                RAW_STATE_COUNT * 4L
        );

        printStorage(
                "8 bytes/state",
                RAW_STATE_COUNT * 8L
        );

        long outcome2BitBytes =
                divideRoundUp(
                        RAW_STATE_COUNT * 2L,
                        8L
                );

        printStorage(
                "2-bit outcome/state",
                outcome2BitBytes
        );

        long oneBitBytes =
                divideRoundUp(
                        RAW_STATE_COUNT,
                        8L
                );

        printStorage(
                "1-bit bitmap/state",
                oneBitBytes
        );

        /*
         * A useful lower-bound-style working-set example:
         * 2-bit outcome + one byte distance + two 1-bit bitmaps.
         *
         * This is not a solver design commitment.  It simply demonstrates
         * why packed/chunked storage is worth pursuing before M65+.
         */
        long packedExample =
                outcome2BitBytes
                        + RAW_STATE_COUNT
                        + oneBitBytes * 2L;

        printStorage(
                "Example packed working set",
                packedExample
        );
    }

    private static void printSymmetryEstimates() {

        /*
         * These are structural estimates only.
         *
         * If two extra pieces are identical, canonical ordering can remove
         * the 2! permutation duplication of those two slots.
         *
         * If all three extras are identical, canonical ordering can remove
         * the 3! permutation duplication.
         *
         * Actual legal/canonical tablebase counts will be lower and depend
         * on material, pawn rules, checks, promotions, EP history, etc.
         */
        long twoIdentical =
                RAW_STATE_COUNT / 2L;

        long threeIdentical =
                RAW_STATE_COUNT / 6L;

        System.out.println();
        System.out.println(
                "Simple identical-piece canonicalization estimates"
        );
        System.out.println(
                "-----------------------------------------------"
        );

        System.out.printf(
                "No identical-extra reduction: %,d states%n",
                RAW_STATE_COUNT
        );

        System.out.printf(
                "Two identical extras (~2!): %,d states%n",
                twoIdentical
        );

        System.out.printf(
                "Three identical extras (~3!): %,d states%n",
                threeIdentical
        );

        System.out.println(
                "NOTE: these are feasibility estimates, not exact legal counts."
        );
    }

    private static ScanResult scan(
            long sample,
            boolean measured
    ) {

        long distinctSquares =
                0L;

        long nonAdjacentKings =
                0L;

        long checksum =
                0L;

        long started =
                System.nanoTime();

        for (long state = 0L;
             state < sample;
             state++) {

            long packed =
                    state >>> 1;

            int thirdExtra =
                    (int) (packed & 63L);

            packed >>>= 6;

            int secondExtra =
                    (int) (packed & 63L);

            packed >>>= 6;

            int firstExtra =
                    (int) (packed & 63L);

            packed >>>= 6;

            int blackKing =
                    (int) (packed & 63L);

            packed >>>= 6;

            int whiteKing =
                    (int) (packed & 63L);

            if (!allDistinct(
                    whiteKing,
                    blackKing,
                    firstExtra,
                    secondExtra,
                    thirdExtra
            )) {
                continue;
            }

            distinctSquares++;

            if (kingsAdjacent(
                    whiteKing,
                    blackKing
            )) {
                continue;
            }

            nonAdjacentKings++;

            /*
             * Prevent dead-code elimination while exercising all decoded
             * fields.
             */
            checksum +=
                    whiteKing
                            + blackKing * 3L
                            + firstExtra * 5L
                            + secondExtra * 7L
                            + thirdExtra * 11L
                            + (state & 1L);
        }

        long elapsed =
                System.nanoTime()
                        - started;

        double seconds =
                elapsed
                        / 1_000_000_000.0;

        double throughput =
                sample
                        / seconds;

        double projectedFullScanSeconds =
                RAW_STATE_COUNT
                        / throughput;

        ScanResult result =
                new ScanResult(
                        sample,
                        distinctSquares,
                        nonAdjacentKings,
                        elapsed,
                        throughput,
                        projectedFullScanSeconds,
                        checksum
                );

        if (measured) {
            printScan(
                    result
            );
        }

        return result;
    }

    private static void printScan(
            ScanResult result
    ) {

        System.out.println();
        System.out.println(
                "Primitive packed-state scan"
        );
        System.out.println(
                "---------------------------"
        );

        System.out.printf(
                "Sample raw states: %,d%n",
                result.sample()
        );

        System.out.printf(
                "Distinct-square sample states: %,d%n",
                result.distinctSquares()
        );

        System.out.printf(
                "Non-adjacent-king sample states: %,d%n",
                result.nonAdjacentKings()
        );

        System.out.printf(
                "Elapsed: %.3f ms%n",
                result.elapsedNanos()
                        / 1_000_000.0
        );

        System.out.printf(
                "Raw decode/filter throughput: %,.0f states/sec%n",
                result.throughput()
        );

        System.out.printf(
                "Projected full 2,147,483,648-state structural scan: %.3f sec (%.2f min)%n",
                result.projectedFullScanSeconds(),
                result.projectedFullScanSeconds()
                        / 60.0
        );

        System.out.println(
                "Checksum: "
                        + result.checksum()
        );
    }

    private static void printConclusion(
            ScanResult result
    ) {

        double oneByteGiB =
                gibibytes(
                        RAW_STATE_COUNT
                );

        System.out.println();
        System.out.println(
                "M64 feasibility conclusion"
        );
        System.out.println(
                "--------------------------"
        );

        System.out.println(
                "1. Five-piece raw state space is exactly 64x four-piece."
        );

        System.out.println(
                "2. Raw five-piece IDs require long, not int."
        );

        System.out.printf(
                "3. Even one byte per raw state costs %.3f GiB.%n",
                oneByteGiB
        );

        System.out.printf(
                "4. This machine projects a structural raw-domain scan at about %.2f minutes.%n",
                result.projectedFullScanSeconds()
                        / 60.0
        );

        System.out.println(
                "5. Do not begin a naive full-domain five-piece retrograde build."
        );

        System.out.println(
                "6. Next 5+ milestone should design compact material-specific indexing,"
        );

        System.out.println(
                "   partitioning, checkpoint/resume, and disk-aware storage first."
        );

        System.out.println();
        System.out.println(
                "M64 FIVE-PIECE FEASIBILITY BENCHMARK PASSED"
        );
    }

    private static boolean allDistinct(
            int a,
            int b,
            int c,
            int d,
            int e
    ) {

        return a != b
                && a != c
                && a != d
                && a != e
                && b != c
                && b != d
                && b != e
                && c != d
                && c != e
                && d != e;
    }

    private static boolean kingsAdjacent(
            int first,
            int second
    ) {

        int firstFile =
                first & 7;

        int firstRank =
                first >>> 3;

        int secondFile =
                second & 7;

        int secondRank =
                second >>> 3;

        return Math.abs(
                firstFile
                        - secondFile
        ) <= 1
                && Math.abs(
                firstRank
                        - secondRank
        ) <= 1;
    }

    private static long permutation(
            int n,
            int k
    ) {

        long result =
                1L;

        for (int i = 0;
             i < k;
             i++) {

            result *=
                    n - i;
        }

        return result;
    }

    private static long divideRoundUp(
            long numerator,
            long denominator
    ) {

        return numerator / denominator
                + (numerator % denominator == 0L
                ? 0L
                : 1L);
    }

    private static void printStorage(
            String label,
            long bytes
    ) {

        System.out.printf(
                "%-30s %,15d bytes  %8.3f GiB%n",
                label + ":",
                bytes,
                gibibytes(
                        bytes
                )
        );
    }

    private static double gibibytes(
            long bytes
    ) {

        return bytes
                / (1024.0
                * 1024.0
                * 1024.0);
    }

    private record ScanResult(
            long sample,
            long distinctSquares,
            long nonAdjacentKings,
            long elapsedNanos,
            double throughput,
            double projectedFullScanSeconds,
            long checksum
    ) {
    }
}

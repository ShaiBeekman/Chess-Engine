package main.java.chess.endgame;

/**
 * Fast primitive benchmark for the KQRK four-piece state representation.
 *
 * This deliberately does NOT create Board, Position, Piece, Square,
 * HashMap, or Move objects.  It measures the cost of decoding and applying
 * the structural legality rules directly to packed square indexes.
 *
 * Run this before another full retrograde attempt.
 */
public final class FourPiecePrimitiveBenchmarkMain {

    private static final int RAW_STATE_COUNT =
            64 * 64 * 64 * 64 * 2;

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
                || sample > RAW_STATE_COUNT) {

            throw new IllegalArgumentException(
                    "Sample must be between 1 and "
                            + RAW_STATE_COUNT
                            + "."
            );
        }


        System.out.println(
                "Primitive KQRK benchmark"
        );

        System.out.println(
                "Sample states: "
                        + sample
        );


        long started =
                System.nanoTime();


        int distinctSquares =
                0;

        int nonAdjacentKings =
                0;

        long checksum =
                0;


        for (int state = 0;
             state < sample;
             state++) {

            int packed =
                    state >>> 1;


            int rook =
                    packed & 63;

            packed >>>= 6;


            int queen =
                    packed & 63;

            packed >>>= 6;


            int blackKing =
                    packed & 63;

            packed >>>= 6;


            int whiteKing =
                    packed & 63;


            if (whiteKing == blackKing
                    || whiteKing == queen
                    || whiteKing == rook
                    || blackKing == queen
                    || blackKing == rook
                    || queen == rook) {

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
             * Prevent the JIT from treating the loop as dead work.
             */
            checksum +=
                    whiteKing
                            + blackKing * 3L
                            + queen * 5L
                            + rook * 7L
                            + (state & 1);
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
                RAW_STATE_COUNT
                        / statesPerSecond;


        System.out.printf(
                "Elapsed: %.3f ms%n",
                elapsedMs
        );

        System.out.printf(
                "Throughput: %,.0f states/sec%n",
                statesPerSecond
        );

        System.out.println(
                "Distinct-square states: "
                        + distinctSquares
        );

        System.out.println(
                "Non-adjacent-king states: "
                        + nonAdjacentKings
        );

        System.out.printf(
                "Projected raw 33,554,432-state primitive scan: %.3f sec%n",
                projectedSeconds
        );

        System.out.println(
                "Checksum: "
                        + checksum
        );
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


        int fileDistance =
                Math.abs(
                        firstFile
                                - secondFile
                );

        int rankDistance =
                Math.abs(
                        firstRank
                                - secondRank
                );


        return Math.max(
                fileDistance,
                rankDistance
        ) <= 1;
    }
}

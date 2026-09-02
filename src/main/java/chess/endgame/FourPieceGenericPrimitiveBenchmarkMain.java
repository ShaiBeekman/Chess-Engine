package main.java.chess.endgame;

import main.java.chess.model.PieceType;

import java.util.List;


/**
 * Milestone 12 benchmark for a generic four-piece primitive state space.
 *
 * The existing FourPiecePrimitiveBenchmarkMain proves that the specialized
 * KQRK representation can scan the complete raw four-piece index space
 * without allocating Board, Position, Piece, Square, Move, or collection
 * objects.
 *
 * This benchmark asks the next question:
 *
 *     Can the same primitive square representation describe arbitrary
 *     canonical four-piece material families?
 *
 * Every four-piece position contains:
 *
 *     white king
 *     black king
 *     first non-king piece
 *     second non-king piece
 *     side to move
 *
 * Therefore the unsymmetrized raw index space remains:
 *
 *     64 * 64 * 64 * 64 * 2
 *     = 33,554,432 states
 *
 * Material type and ownership are metadata supplied by
 * FourPieceMaterialClass rather than encoded into every state.
 *
 * One important complication appears once the material catalog is generic:
 *
 *     KQQK
 *     KRRK
 *     KBBK
 *     KNNK
 *     KPPK
 *
 * contain two identical pieces owned by the same side.
 *
 * Exchanging those two pieces does not create a different chess position.
 * We therefore canonicalize their primitive square slots by requiring:
 *
 *     firstExtraSquare < secondExtraSquare
 *
 * Split classes such as KQ vs KQ do NOT use that reduction because the two
 * queens belong to different colors and are therefore distinguishable.
 *
 * This benchmark measures both forms.
 */
public final class FourPieceGenericPrimitiveBenchmarkMain {

    private static final int RAW_STATE_COUNT =
            64 * 64 * 64 * 64 * 2;

    private static final int DEFAULT_SAMPLE =
            1_000_000;


    private FourPieceGenericPrimitiveBenchmarkMain() {

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
                || sample > RAW_STATE_COUNT) {

            throw new IllegalArgumentException(
                    "Sample must be between 1 and "
                            + RAW_STATE_COUNT
                            + "."
            );
        }


        List<FourPieceMaterialClass> catalog =
                FourPieceMaterialClass.allCanonical();


        if (catalog.size()
                != 30) {

            throw new IllegalStateException(
                    "Expected 30 canonical four-piece material classes, found "
                            + catalog.size()
                            + "."
            );
        }


        long tierZeroCount =
                catalog.stream()
                        .filter(
                                material ->
                                        material.buildTier()
                                                == 0
                        )
                        .count();


        long tierOneCount =
                catalog.stream()
                        .filter(
                                material ->
                                        material.buildTier()
                                                == 1
                        )
                        .count();


        long tierTwoCount =
                catalog.stream()
                        .filter(
                                material ->
                                        material.buildTier()
                                                == 2
                        )
                        .count();


        System.out.println(
                "Generic four-piece primitive benchmark"
        );

        System.out.println(
                "======================================"
        );

        System.out.println(
                "Canonical material classes: "
                        + catalog.size()
        );

        System.out.println(
                "Tier 0 classes: "
                        + tierZeroCount
        );

        System.out.println(
                "Tier 1 classes: "
                        + tierOneCount
        );

        System.out.println(
                "Tier 2 classes: "
                        + tierTwoCount
        );

        System.out.println(
                "Raw states per material orientation: "
                        + String.format(
                        "%,d",
                        RAW_STATE_COUNT
                )
        );

        System.out.println(
                "Sample states per benchmark: "
                        + String.format(
                        "%,d",
                        sample
                )
        );

        System.out.println();


        /*
         * Existing proven shape:
         *
         *     King + Queen + Rook versus King
         *
         * The two extra pieces are distinguishable.
         */
        benchmark(
                FourPieceMaterialClass.sameSide(
                        PieceType.QUEEN,
                        PieceType.ROOK
                ),
                sample
        );


        /*
         * New generic-state problem:
         *
         *     King + Queen + Queen versus King
         *
         * The two queens are same-type, same-owner pieces.
         * Their square slots must therefore be canonicalized.
         */
        benchmark(
                FourPieceMaterialClass.sameSide(
                        PieceType.QUEEN,
                        PieceType.QUEEN
                ),
                sample
        );


        /*
         * Same piece type, different ownership:
         *
         *     King + Queen versus King + Queen
         *
         * These queens cannot be exchanged because ownership distinguishes
         * them.
         */
        benchmark(
                FourPieceMaterialClass.split(
                        PieceType.QUEEN,
                        PieceType.QUEEN
                ),
                sample
        );


        verifyCatalogPolicies(
                catalog
        );


        System.out.println();

        System.out.println(
                "GENERIC FOUR-PIECE PRIMITIVE BENCHMARK PASSED"
        );
    }


    private static void benchmark(
            FourPieceMaterialClass material,
            int sample
    ) {

        boolean interchangeableExtras =
                hasInterchangeableExtras(
                        material
                );


        System.out.println(
                material.assetStem()
                        + "  "
                        + material.displayName()
        );

        System.out.println(
                "Distribution: "
                        + material.distribution()
        );

        System.out.println(
                "Interchangeable extra pieces: "
                        + interchangeableExtras
        );


        long started =
                System.nanoTime();


        int distinctSquares =
                0;

        int nonAdjacentKings =
                0;

        int canonicalStates =
                0;

        long checksum =
                0;


        for (int state = 0;
             state < sample;
             state++) {

            int packed =
                    state >>> 1;


            /*
             * Generic primitive slot layout:
             *
             * bits  0..5   second extra piece
             * bits  6..11  first extra piece
             * bits 12..17  black king
             * bits 18..23  white king
             *
             * state bit 0 stores side to move.
             *
             * This intentionally matches the square ordering already used
             * by the specialized KQRK primitive benchmark:
             *
             *     rook       -> second extra
             *     queen      -> first extra
             *     black king
             *     white king
             */
            int secondExtra =
                    packed & 63;

            packed >>>= 6;


            int firstExtra =
                    packed & 63;

            packed >>>= 6;


            int blackKing =
                    packed & 63;

            packed >>>= 6;


            int whiteKing =
                    packed & 63;


            if (!allSquaresDistinct(
                    whiteKing,
                    blackKing,
                    firstExtra,
                    secondExtra
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
             * Only identical pieces belonging to the same side are
             * interchangeable.
             *
             * Requiring firstExtra < secondExtra chooses one representative
             * from each pair of equivalent primitive encodings.
             */
            if (interchangeableExtras
                    && firstExtra
                    >= secondExtra) {

                continue;
            }


            canonicalStates++;


            checksum +=
                    whiteKing
                            + blackKing * 3L
                            + firstExtra * 5L
                            + secondExtra * 7L
                            + (state & 1);
        }


        long elapsedNanos =
                System.nanoTime()
                        - started;


        double elapsedSeconds =
                elapsedNanos
                        / 1_000_000_000.0;


        double elapsedMilliseconds =
                elapsedNanos
                        / 1_000_000.0;


        double statesPerSecond =
                sample
                        / elapsedSeconds;


        double projectedSeconds =
                RAW_STATE_COUNT
                        / statesPerSecond;


        System.out.printf(
                "Elapsed: %.3f ms%n",
                elapsedMilliseconds
        );

        System.out.printf(
                "Throughput: %,.0f states/sec%n",
                statesPerSecond
        );

        System.out.println(
                "Distinct-square states: "
                        + String.format(
                        "%,d",
                        distinctSquares
                )
        );

        System.out.println(
                "Non-adjacent-king states: "
                        + String.format(
                        "%,d",
                        nonAdjacentKings
                )
        );

        System.out.println(
                "Canonical primitive states: "
                        + String.format(
                        "%,d",
                        canonicalStates
                )
        );

        System.out.printf(
                "Projected full raw scan: %.3f sec%n",
                projectedSeconds
        );

        System.out.println(
                "Checksum: "
                        + checksum
        );

        System.out.println();
    }


    /**
     * Verify that every material class in the 30-family catalog has an
     * unambiguous primitive-slot policy.
     */
    private static void verifyCatalogPolicies(
            List<FourPieceMaterialClass> catalog
    ) {

        int interchangeableClasses =
                0;

        int orderedClasses =
                0;


        for (FourPieceMaterialClass material :
                catalog) {

            if (hasInterchangeableExtras(
                    material
            )) {

                interchangeableClasses++;
            }
            else {

                orderedClasses++;
            }
        }


        /*
         * SAME_SIDE identical pairs:
         *
         *     KQQK
         *     KRRK
         *     KBBK
         *     KNNK
         *     KPPK
         *
         * Exactly five classes.
         */
        if (interchangeableClasses
                != 5) {

            throw new IllegalStateException(
                    "Expected 5 same-side interchangeable material classes, found "
                            + interchangeableClasses
                            + "."
            );
        }


        if (orderedClasses
                != 25) {

            throw new IllegalStateException(
                    "Expected 25 ordered-slot material classes, found "
                            + orderedClasses
                            + "."
            );
        }


        System.out.println(
                "Catalog primitive-slot policy"
        );

        System.out.println(
                "Interchangeable-slot classes: "
                        + interchangeableClasses
        );

        System.out.println(
                "Ordered-slot classes: "
                        + orderedClasses
        );
    }


    private static boolean hasInterchangeableExtras(
            FourPieceMaterialClass material
    ) {

        return material.distribution()
                == FourPieceMaterialClass.Distribution.SAME_SIDE
                && material.firstType()
                == material.secondType();
    }


    private static boolean allSquaresDistinct(
            int whiteKing,
            int blackKing,
            int firstExtra,
            int secondExtra
    ) {

        return whiteKing
                != blackKing
                && whiteKing
                != firstExtra
                && whiteKing
                != secondExtra
                && blackKing
                != firstExtra
                && blackKing
                != secondExtra
                && firstExtra
                != secondExtra;
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
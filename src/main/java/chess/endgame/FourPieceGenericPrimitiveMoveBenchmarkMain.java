package main.java.chess.endgame;

import main.java.chess.model.PieceType;


/**
 * Milestone 15 performance gate for generic Tier-0 primitive
 * four-piece legal successor generation.
 *
 * The specialized KQRK primitive generator has already demonstrated
 * that allocation-free successor generation is fast enough for
 * large retrograde scans.
 *
 * This benchmark measures whether the generic material-driven
 * implementation remains practical across representative Tier-0
 * material families.
 *
 * Representative classes:
 *
 *     KQRK       queen + rook
 *     KQBK       bishop slider
 *     KQNK       knight
 *     KQQK       interchangeable same-side pieces
 *     KQ vs KR   split ownership
 *     KB vs KN   lower-mobility split material
 *
 * This is intentionally still a raw-state benchmark.  It does not
 * allocate Board, Position, Move, Piece, Square, or collection objects
 * inside the measured successor loop.
 */
public final class FourPieceGenericPrimitiveMoveBenchmarkMain {

    private static final int DEFAULT_SAMPLE =
            1_000_000;


    private FourPieceGenericPrimitiveMoveBenchmarkMain() {

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
                || sample
                > FourPieceGenericPrimitiveState.STATE_COUNT) {

            throw new IllegalArgumentException(
                    "Sample must be between 1 and "
                            + FourPieceGenericPrimitiveState.STATE_COUNT
                            + "."
            );
        }


        System.out.println(
                "Generic Tier-0 primitive move benchmark"
        );

        System.out.println(
                "======================================="
        );

        System.out.println(
                "Raw sample per material: "
                        + String.format(
                        "%,d",
                        sample
                )
        );


        /*
         * Existing reference shape.
         */
        benchmark(
                FourPieceMaterialClass.sameSide(
                        PieceType.QUEEN,
                        PieceType.ROOK
                ),
                true,
                sample
        );


        /*
         * Bishop slider.
         */
        benchmark(
                FourPieceMaterialClass.sameSide(
                        PieceType.QUEEN,
                        PieceType.BISHOP
                ),
                true,
                sample
        );


        /*
         * Non-slider extra piece.
         */
        benchmark(
                FourPieceMaterialClass.sameSide(
                        PieceType.QUEEN,
                        PieceType.KNIGHT
                ),
                true,
                sample
        );


        /*
         * Same-type / same-owner canonicalization.
         */
        benchmark(
                FourPieceMaterialClass.sameSide(
                        PieceType.QUEEN,
                        PieceType.QUEEN
                ),
                true,
                sample
        );


        /*
         * Split ownership and captures by either side.
         */
        benchmark(
                FourPieceMaterialClass.split(
                        PieceType.QUEEN,
                        PieceType.ROOK
                ),
                true,
                sample
        );


        /*
         * Lower-mobility split material.
         */
        benchmark(
                FourPieceMaterialClass.split(
                        PieceType.BISHOP,
                        PieceType.KNIGHT
                ),
                true,
                sample
        );


        System.out.println();

        System.out.println(
                "GENERIC TIER-0 PRIMITIVE MOVE BENCHMARK COMPLETE"
        );
    }


    private static void benchmark(
            FourPieceMaterialClass material,
            boolean sameSideOwnerIsWhite,
            int sample
    ) {

        FourPieceGenericPrimitiveMoveGenerator.Buffer buffer =
                new FourPieceGenericPrimitiveMoveGenerator.Buffer(
                        64
                );


        /*
         * JIT warm-up.
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

            if (!FourPieceGenericPrimitiveRules.isStructurallyLegal(
                    state,
                    material,
                    sameSideOwnerIsWhite
            )) {

                continue;
            }


            int count =
                    FourPieceGenericPrimitiveMoveGenerator.generateLegalSuccessors(
                            state,
                            material,
                            sameSideOwnerIsWhite,
                            buffer
                    );


            warmupChecksum +=
                    count;
        }


        long started =
                System.nanoTime();


        int legalStates =
                0;

        long successors =
                0;

        long inClass =
                0;

        long boundaries =
                0;

        long checksum =
                0;


        for (int state = 0;
             state < sample;
             state++) {

            if (!FourPieceGenericPrimitiveRules.isStructurallyLegal(
                    state,
                    material,
                    sameSideOwnerIsWhite
            )) {

                continue;
            }


            legalStates++;


            int count =
                    FourPieceGenericPrimitiveMoveGenerator.generateLegalSuccessors(
                            state,
                            material,
                            sameSideOwnerIsWhite,
                            buffer
                    );


            successors +=
                    count;


            for (int i = 0;
                 i < count;
                 i++) {

                if (!buffer.isBoundary(
                        i
                )) {

                    inClass++;


                    checksum +=
                            buffer.state(
                                    i
                            )
                                    * 17L
                                    + i;

                } else {

                    boundaries++;


                    PieceType surviving =
                            buffer.survivingPieceType(
                                    i
                            );


                    checksum +=
                            surviving.ordinal()
                                    * 31L
                                    + buffer.survivingPieceSquare(
                                    i
                            )
                                    * 7L
                                    + buffer.boundaryWhiteKing(
                                    i
                            )
                                    * 3L
                                    + buffer.boundaryBlackKing(
                                    i
                            )
                                    * 5L
                                    + (buffer.survivingPieceIsWhite(
                                    i
                            )
                                    ? 11L
                                    : 13L)
                                    + (buffer.boundaryBlackToMove(
                                    i
                            )
                                    ? 19L
                                    : 23L);
                }
            }
        }


        long elapsed =
                System.nanoTime()
                        - started;


        double seconds =
                elapsed
                        / 1_000_000_000.0;


        double milliseconds =
                elapsed
                        / 1_000_000.0;


        double rawStatesPerSecond =
                sample
                        / seconds;


        double successorsPerSecond =
                successors == 0
                        ? 0.0
                        : successors
                        / seconds;


        double projectedFullScanSeconds =
                FourPieceGenericPrimitiveState.STATE_COUNT
                        / rawStatesPerSecond;


        double averageSuccessors =
                legalStates == 0
                        ? 0.0
                        : (double) successors
                        / legalStates;


        System.out.println();

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
                "Structurally legal: "
                        + String.format(
                        "%,d",
                        legalStates
                )
        );

        System.out.println(
                "Generated successors: "
                        + String.format(
                        "%,d",
                        successors
                )
        );

        System.out.println(
                "In-class successors: "
                        + String.format(
                        "%,d",
                        inClass
                )
        );

        System.out.println(
                "Three-piece boundaries: "
                        + String.format(
                        "%,d",
                        boundaries
                )
        );

        System.out.printf(
                "Average successors/legal state: %.3f%n",
                averageSuccessors
        );

        System.out.printf(
                "Elapsed: %.3f ms%n",
                milliseconds
        );

        System.out.printf(
                "Raw throughput: %,.0f states/sec%n",
                rawStatesPerSecond
        );

        System.out.printf(
                "Move throughput: %,.0f successors/sec%n",
                successorsPerSecond
        );

        System.out.printf(
                "Projected full raw-state move scan: %.3f sec%n",
                projectedFullScanSeconds
        );

        System.out.println(
                "Checksum: "
                        + checksum
        );


        /*
         * Keep warm-up work observable to the JIT.
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
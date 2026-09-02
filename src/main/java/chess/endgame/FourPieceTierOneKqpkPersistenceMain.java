package main.java.chess.endgame;

import main.java.chess.model.Board;
import main.java.chess.model.Color;
import main.java.chess.model.Piece;
import main.java.chess.model.PieceType;
import main.java.chess.model.Position;
import main.java.chess.model.PositionKey;
import main.java.chess.model.Square;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;


/**
 * Milestone 39.
 *
 * Build canonical strong-WHITE KQPK, persist it through the already-proven
 * generic four-piece codec, reload it, verify byte-for-byte identity, then
 * exercise the KQPK runtime service in both physical color orientations.
 *
 * The resulting asset is:
 *
 *     tablebases/four-piece/KQPK-white.ftb.gz
 *
 * No GUI/evaluation/controller routing changes are made in M39.  This gate
 * proves persistence + runtime normalization first; the exact router can then
 * consume this service in the next milestone without mixing concerns.
 */
public final class FourPieceTierOneKqpkPersistenceMain {

    private static final String EXPECTED_DIGEST =
            "7eaacc1e82deaf4e5a4b1ef8c0b9abd6304094f3f7c78a18060ae7769b8fbffb";


    private FourPieceTierOneKqpkPersistenceMain() {
    }


    public static void main(
            String[] args
    ) throws Exception {

        Path path =
                args.length == 0
                        ? FourPieceTierOneKqpkTablebaseService.DEFAULT_PATH
                        : Path.of(
                        args[0]
                );


        if (args.length > 1) {

            throw new IllegalArgumentException(
                    "Usage: FourPieceTierOneKqpkPersistenceMain [output-path]"
            );
        }


        path =
                path.toAbsolutePath()
                        .normalize();


        System.out.println(
                "KQPK Tier-1 persistence / runtime-service gate"
        );

        System.out.println(
                "============================================"
        );

        System.out.println(
                "Output: "
                        + path
        );

        System.out.println();


        System.out.println(
                "Building canonical strong-WHITE KQPK..."
        );


        FourPieceTierOneKqpkRetrogradeBuilder.Result result =
                new FourPieceTierOneKqpkRetrogradeBuilder(
                        true
                ).build();


        String solvedDigest =
                digest(
                        result.outcome(),
                        result.distance()
                );


        System.out.println(
                "Solved digest: "
                        + solvedDigest
        );


        if (!EXPECTED_DIGEST.equals(
                solvedDigest
        )) {

            throw new IllegalStateException(
                    "KQPK solve digest changed. Expected "
                            + EXPECTED_DIGEST
                            + ", found "
                            + solvedDigest
                            + "."
            );
        }


        FourPieceGenericTablebase solved =
                result.toTablebase();


        System.out.println();

        System.out.println(
                "Saving..."
        );


        long saveStarted =
                System.nanoTime();


        FourPieceGenericTablebaseCodec.save(
                solved,
                path
        );

        double saveSeconds =
                (System.nanoTime()
                        - saveStarted)
                        / 1_000_000_000.0;


        long compressedBytes =
                Files.size(
                        path
                );


        System.out.printf(
                "Saved in %.3f sec%n",
                saveSeconds
        );

        System.out.printf(
                "Compressed size: %.2f MiB%n",
                compressedBytes
                        / (1024.0 * 1024.0)
        );


        /*
         * Drop the solved container before load verification becomes the
         * dominant phase.  The arrays remain referenced by result until this
         * method ends, so we deliberately do not try to force GC; M39's goal
         * is correctness, not a heap benchmark.
         */


        System.out.println();

        System.out.println(
                "Reloading..."
        );


        long loadStarted =
                System.nanoTime();


        FourPieceGenericTablebase loaded =
                FourPieceGenericTablebaseCodec.load(
                        path
                );


        double loadSeconds =
                (System.nanoTime()
                        - loadStarted)
                        / 1_000_000_000.0;


        System.out.printf(
                "Loaded in %.3f sec%n",
                loadSeconds
        );


        verifyMetadata(
                solved,
                loaded
        );


        String loadedDigest =
                digest(
                        loaded.outcomeData(),
                        loaded.distanceData()
                );


        System.out.println(
                "Loaded digest: "
                        + loadedDigest
        );


        if (!solvedDigest.equals(
                loadedDigest
        )) {

            throw new IllegalStateException(
                    "KQPK persistence digest mismatch."
            );
        }


        System.out.println(
                "Byte-for-byte persistence identity: PASSED"
        );


        /*
         * Pick a decisive state directly from the solved array.  We construct
         * a normal engine Position from it, then construct its physical
         * strong-BLACK color-reversed counterpart.  Both must normalize to the
         * same persisted primitive state and therefore return identical WDL/DTM.
         */
        int fixtureState =
                findDecisiveFixture(
                        loaded
                );


        Position whiteFixture =
                positionFromCanonicalState(
                        fixtureState
                );

        Position blackFixture =
                colorReversePosition(
                        fixtureState
                );


        FourPieceTierOneKqpkTablebaseService service =
                new FourPieceTierOneKqpkTablebaseService(
                        path
                );


        Optional<FourPieceTierOneKqpkTablebaseService.ProbeResult>
                whiteOptional =
                service.probe(
                        whiteFixture
                );


        Optional<FourPieceTierOneKqpkTablebaseService.ProbeResult>
                blackOptional =
                service.probe(
                        blackFixture
                );


        if (whiteOptional.isEmpty()
                || blackOptional.isEmpty()) {

            throw new IllegalStateException(
                    "KQPK runtime service rejected a persistence fixture."
            );
        }


        FourPieceTierOneKqpkTablebaseService.ProbeResult whiteProbe =
                whiteOptional.get();

        FourPieceTierOneKqpkTablebaseService.ProbeResult blackProbe =
                blackOptional.get();


        if (whiteProbe.primitiveState()
                != fixtureState) {

            throw new IllegalStateException(
                    "Strong-WHITE fixture did not preserve canonical primitive state."
            );
        }


        if (blackProbe.primitiveState()
                != fixtureState) {

            throw new IllegalStateException(
                    "Strong-BLACK fixture did not normalize to canonical primitive state."
            );
        }


        if (whiteProbe.outcome()
                != blackProbe.outcome()
                || whiteProbe.distance()
                != blackProbe.distance()) {

            throw new IllegalStateException(
                    "KQPK runtime color normalization changed WDL/DTM."
            );
        }


        if (whiteProbe.colorReversed()) {

            throw new IllegalStateException(
                    "Strong-WHITE fixture was incorrectly marked color-reversed."
            );
        }


        if (!blackProbe.colorReversed()) {

            throw new IllegalStateException(
                    "Strong-BLACK fixture was not marked color-reversed."
            );
        }


        System.out.println();

        System.out.println(
                "Runtime normalization fixture"
        );

        System.out.println(
                "============================="
        );

        System.out.println(
                "Canonical state: "
                        + fixtureState
        );

        System.out.println(
                "Outcome: "
                        + whiteProbe.outcomeName()
        );

        System.out.println(
                "DTM: "
                        + whiteProbe.distance()
        );

        System.out.println(
                "Strong-WHITE primitive state: "
                        + whiteProbe.primitiveState()
        );

        System.out.println(
                "Strong-BLACK normalized state: "
                        + blackProbe.primitiveState()
        );

        System.out.println(
                "Color normalization: PASSED"
        );


        service.clearCache();


        if (service.isLoaded()) {

            throw new IllegalStateException(
                    "KQPK runtime cache did not clear."
            );
        }


        System.out.println(
                "Runtime cache clear: PASSED"
        );


        System.out.println();

        System.out.println(
                "KQPK TIER-1 PERSISTENCE / RUNTIME-SERVICE GATE PASSED"
        );

        System.out.println(
                "NEXT: ROUTE KQPK THROUGH ExactEndgameTablebase / CONTROLLER"
        );
    }


    private static void verifyMetadata(
            FourPieceGenericTablebase expected,
            FourPieceGenericTablebase actual
    ) {

        if (!expected.material()
                .equals(
                        actual.material()
                )
                || expected.sameSideOwnerIsWhite()
                != actual.sameSideOwnerIsWhite()
                || expected.legalStates()
                != actual.legalStates()
                || expected.wins()
                != actual.wins()
                || expected.losses()
                != actual.losses()
                || expected.draws()
                != actual.draws()
                || expected.maximumDistance()
                != actual.maximumDistance()) {

            throw new IllegalStateException(
                    "Reloaded KQPK metadata does not match solved metadata."
            );
        }
    }


    private static int findDecisiveFixture(
            FourPieceGenericTablebase tablebase
    ) {

        for (int state = 0;
             state < FourPieceGenericPrimitiveState.STATE_COUNT;
             state++) {

            byte outcome =
                    tablebase.outcome(
                            state
                    );


            if ((outcome
                    == FourPieceTablebase.WIN
                    || outcome
                    == FourPieceTablebase.LOSS)
                    && tablebase.distance(
                    state
            ) >= 4) {

                return state;
            }
        }


        throw new IllegalStateException(
                "Unable to find decisive KQPK runtime fixture."
        );
    }


    private static Position positionFromCanonicalState(
            int state
    ) {

        Board board =
                new Board();


        board.setPiece(
                square(
                        FourPieceGenericPrimitiveState.whiteKing(
                                state
                        )
                ),
                new Piece(
                        PieceType.KING,
                        Color.WHITE
                )
        );

        board.setPiece(
                square(
                        FourPieceGenericPrimitiveState.blackKing(
                                state
                        )
                ),
                new Piece(
                        PieceType.KING,
                        Color.BLACK
                )
        );

        board.setPiece(
                square(
                        FourPieceGenericPrimitiveState.firstExtra(
                                state
                        )
                ),
                new Piece(
                        PieceType.QUEEN,
                        Color.WHITE
                )
        );

        board.setPiece(
                square(
                        FourPieceGenericPrimitiveState.secondExtra(
                                state
                        )
                ),
                new Piece(
                        PieceType.PAWN,
                        Color.WHITE
                )
        );


        return position(
                board,
                FourPieceGenericPrimitiveState.blackToMove(
                        state
                )
                        ? Color.BLACK
                        : Color.WHITE
        );
    }


    private static Position colorReversePosition(
            int canonicalState
    ) {

        Board board =
                new Board();


        board.setPiece(
                square(
                        flipRank(
                                FourPieceGenericPrimitiveState.blackKing(
                                        canonicalState
                                )
                        )
                ),
                new Piece(
                        PieceType.KING,
                        Color.WHITE
                )
        );

        board.setPiece(
                square(
                        flipRank(
                                FourPieceGenericPrimitiveState.whiteKing(
                                        canonicalState
                                )
                        )
                ),
                new Piece(
                        PieceType.KING,
                        Color.BLACK
                )
        );

        board.setPiece(
                square(
                        flipRank(
                                FourPieceGenericPrimitiveState.firstExtra(
                                        canonicalState
                                )
                        )
                ),
                new Piece(
                        PieceType.QUEEN,
                        Color.BLACK
                )
        );

        board.setPiece(
                square(
                        flipRank(
                                FourPieceGenericPrimitiveState.secondExtra(
                                        canonicalState
                                )
                        )
                ),
                new Piece(
                        PieceType.PAWN,
                        Color.BLACK
                )
        );


        Color canonicalSide =
                FourPieceGenericPrimitiveState.blackToMove(
                        canonicalState
                )
                        ? Color.BLACK
                        : Color.WHITE;


        return position(
                board,
                canonicalSide.opposite()
        );
    }


    private static Position position(
            Board board,
            Color sideToMove
    ) {

        Position temporary =
                new Position(
                        board,
                        sideToMove,
                        false,
                        false,
                        false,
                        false,
                        null,
                        0,
                        1,
                        new HashMap<>()
                );


        Map<PositionKey, Integer> repetitions =
                new HashMap<>();


        repetitions.put(
                temporary.createPositionKey(),
                1
        );


        return new Position(
                board,
                sideToMove,
                false,
                false,
                false,
                false,
                null,
                0,
                1,
                repetitions
        );
    }


    private static Square square(
            int primitive
    ) {

        return new Square(
                primitive & 7,
                primitive >>> 3
        );
    }


    private static int flipRank(
            int square
    ) {

        return (7 - (square >>> 3)) * 8
                + (square & 7);
    }


    private static String digest(
            byte[] outcome,
            short[] distance
    ) throws Exception {

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
}

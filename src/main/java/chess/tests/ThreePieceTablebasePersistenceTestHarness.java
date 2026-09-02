package main.java.chess.tests;

import main.java.chess.endgame.ThreePieceTablebase;
import main.java.chess.endgame.ThreePieceTablebaseCodec;
import main.java.chess.model.Color;
import main.java.chess.model.Move;
import main.java.chess.model.PieceType;
import main.java.chess.model.Position;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Regression harness for binary three-piece tablebase persistence.
 *
 * It proves that a tablebase can be:
 *
 *     built once
 *     saved
 *     loaded into a fresh object without retrograde analysis
 *     saved again byte-for-byte identically
 *
 * It also compares metrics, probes, deterministic random study selection,
 * and optimal move lists between the built and loaded tables.
 */
public final class ThreePieceTablebasePersistenceTestHarness {

    private static final int RANDOM_PROBE_TESTS =
            250;

    private static final long RANDOM_SEED =
            0x5A17C0DEL;


    private ThreePieceTablebasePersistenceTestHarness() {
    }


    public static void main(
            String[] args
    ) throws Exception {

        System.out.println();
        System.out.println(
                "============================================="
        );
        System.out.println(
                "THREE-PIECE TABLEBASE PERSISTENCE TEST"
        );
        System.out.println(
                "============================================="
        );


        Path outputDirectory =
                Path.of(
                        "tablebase-test-output"
                );

        Files.createDirectories(
                outputDirectory
        );


        testMaterial(
                PieceType.QUEEN,
                Color.WHITE,
                outputDirectory.resolve(
                        "KQK-white-v1.tb"
                )
        );


        testMaterial(
                PieceType.ROOK,
                Color.WHITE,
                outputDirectory.resolve(
                        "KRK-white-v1.tb"
                )
        );


        System.out.println();
        System.out.println(
                "============================================="
        );
        System.out.println(
                "ALL PERSISTENCE TESTS PASSED"
        );
        System.out.println(
                "============================================="
        );
    }


    private static void testMaterial(
            PieceType majorType,
            Color majorColor,
            Path firstFile
    ) throws Exception {

        String label =
                majorType == PieceType.QUEEN
                        ? "KQK"
                        : "KRK";

        Path roundTripFile =
                firstFile.resolveSibling(
                        firstFile.getFileName()
                                + ".roundtrip"
                );


        Files.deleteIfExists(
                firstFile
        );

        Files.deleteIfExists(
                roundTripFile
        );


        System.out.println();
        System.out.println(
                "--- " + label + " ---"
        );


        // -----------------------------------------------------
        // 1. Build the original exact tablebase.
        // -----------------------------------------------------

        ThreePieceTablebase original =
                new ThreePieceTablebase(
                        majorType,
                        majorColor
                );


        long buildStarted =
                System.nanoTime();

        original.build();

        long buildMillis =
                elapsedMillis(
                        buildStarted
                );


        System.out.println(
                "Build time:       "
                        + buildMillis
                        + " ms"
        );


        // -----------------------------------------------------
        // 2. Save the built tablebase.
        // -----------------------------------------------------

        long saveStarted =
                System.nanoTime();

        ThreePieceTablebaseCodec.save(
                firstFile,
                original
        );

        long saveMillis =
                elapsedMillis(
                        saveStarted
                );

        long fileSize =
                Files.size(
                        firstFile
                );


        System.out.println(
                "Save time:        "
                        + saveMillis
                        + " ms"
        );

        System.out.println(
                "File size:        "
                        + formatBytes(
                        fileSize
                )
                        + " ("
                        + fileSize
                        + " bytes)"
        );

        System.out.println(
                "Saved file:       "
                        + firstFile.toAbsolutePath()
        );


        // -----------------------------------------------------
        // 3. Load into a completely fresh tablebase.
        // -----------------------------------------------------

        long loadStarted =
                System.nanoTime();

        ThreePieceTablebase loaded =
                ThreePieceTablebaseCodec.load(
                        firstFile
                );

        long loadMillis =
                elapsedMillis(
                        loadStarted
                );


        require(
                loaded.isBuilt(),
                label + " loaded table was not marked built."
        );


        System.out.println(
                "Load time:        "
                        + loadMillis
                        + " ms"
        );


        // -----------------------------------------------------
        // 4. Compare public metadata and solved-state counts.
        // -----------------------------------------------------

        require(
                original.getMajorType()
                        == loaded.getMajorType(),
                label + " material type changed after loading."
        );

        require(
                original.getMajorColor()
                        == loaded.getMajorColor(),
                label + " major color changed after loading."
        );

        require(
                original.getLegalStateCount()
                        == loaded.getLegalStateCount(),
                label + " legal-state count changed after loading."
        );

        require(
                original.getWinCount()
                        == loaded.getWinCount(),
                label + " WIN count changed after loading."
        );

        require(
                original.getLossCount()
                        == loaded.getLossCount(),
                label + " LOSS count changed after loading."
        );

        require(
                original.getDrawCount()
                        == loaded.getDrawCount(),
                label + " DRAW count changed after loading."
        );


        System.out.println(
                "Metadata/counts:  MATCH"
        );


        // -----------------------------------------------------
        // 5. Save the loaded object again and compare every byte.
        //
        // Since the serialized payload contains every outcome byte and
        // every mate-distance short, byte identity verifies all 524,288
        // encoded states, not merely a sample.
        // -----------------------------------------------------

        ThreePieceTablebaseCodec.save(
                roundTripFile,
                loaded
        );


        long mismatch =
                Files.mismatch(
                        firstFile,
                        roundTripFile
                );


        require(
                mismatch == -1,
                label
                        + " serialized data changed after round trip at byte "
                        + mismatch
                        + "."
        );


        System.out.println(
                "All raw states:   MATCH"
        );


        // -----------------------------------------------------
        // 6. Deterministic study selection must identify the same state.
        // -----------------------------------------------------

        Position originalStudy =
                original.randomWinningPosition(
                        new Random(
                                RANDOM_SEED
                        )
                );

        Position loadedStudy =
                loaded.randomWinningPosition(
                        new Random(
                                RANDOM_SEED
                        )
                );


        require(
                originalStudy.createPositionKey()
                        .equals(
                                loadedStudy.createPositionKey()
                        ),
                label + " deterministic study selection changed after loading."
        );


        System.out.println(
                "Study selection:  MATCH"
        );


        // -----------------------------------------------------
        // 7. Compare probes and best moves across many exact WIN states.
        // -----------------------------------------------------

        Random sampleRandom =
                new Random(
                        RANDOM_SEED ^ 0x6D2B79F5L
                );


        for (int test = 0;
             test < RANDOM_PROBE_TESTS;
             test++) {

            Position sample =
                    original.randomWinningPosition(
                            sampleRandom
                    );


            ThreePieceTablebase.Probe originalProbe =
                    original.probe(
                            sample
                    );

            ThreePieceTablebase.Probe loadedProbe =
                    loaded.probe(
                            sample
                    );


            require(
                    originalProbe.equals(
                            loadedProbe
                    ),
                    label
                            + " probe mismatch on sample "
                            + test
                            + "."
            );


            List<String> originalMoves =
                    normalizedMoves(
                            original.bestMoves(
                                    sample
                            )
                    );

            List<String> loadedMoves =
                    normalizedMoves(
                            loaded.bestMoves(
                                    sample
                            )
                    );


            require(
                    originalMoves.equals(
                            loadedMoves
                    ),
                    label
                            + " best-move mismatch on sample "
                            + test
                            + "."
            );
        }


        System.out.println(
                "Random probes:    PASS ("
                        + RANDOM_PROBE_TESTS
                        + ")"
        );

        System.out.println(
                "Best moves:       PASS ("
                        + RANDOM_PROBE_TESTS
                        + ")"
        );


        // -----------------------------------------------------
        // 8. Final summary for this material class.
        // -----------------------------------------------------

        System.out.println(
                "Legal states:     "
                        + loaded.getLegalStateCount()
        );

        System.out.println(
                "WIN:              "
                        + loaded.getWinCount()
        );

        System.out.println(
                "LOSS:             "
                        + loaded.getLossCount()
        );

        System.out.println(
                "DRAW:             "
                        + loaded.getDrawCount()
        );

        System.out.println(
                "Persistence test: PASSED"
        );
    }


    private static List<String> normalizedMoves(
            List<Move> moves
    ) {

        List<String> normalized =
                new ArrayList<>();


        for (Move move :
                moves) {

            normalized.add(
                    move.toString()
            );
        }


        Collections.sort(
                normalized
        );


        return List.copyOf(
                normalized
        );
    }


    private static long elapsedMillis(
            long startedNanos
    ) {

        return (System.nanoTime()
                - startedNanos)
                / 1_000_000L;
    }


    private static String formatBytes(
            long bytes
    ) {

        double mebibytes =
                bytes
                        / (1024.0 * 1024.0);


        return String.format(
                "%.3f MiB",
                mebibytes
        );
    }


    private static void require(
            boolean condition,
            String message
    ) throws IOException {

        if (!condition) {

            throw new IOException(
                    message
            );
        }
    }
}

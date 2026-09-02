package main.java.chess.release;

import main.java.chess.endgame.FourPieceTierTwoKpkpTablebaseCodec;
import main.java.chess.engine.ChessEngine;
import main.java.chess.search.DovetailWalker;
import main.java.chess.search.ExplorationScheduler;
import main.java.chess.stockfish.StockfishClient;

import java.io.File;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.List;


/**
 * M87 — automated v1.0 release regression + performance gate.
 *
 * Every subsystem gate runs in a fresh JVM. This is intentional:
 *
 *   - caches from one tablebase test cannot leak into another,
 *   - a subsystem failure has a hard process exit boundary,
 *   - elapsed time is reported per gate,
 *   - the M86 gate can launch its own isolated 128-MiB child exactly as before.
 *
 * This class changes no engine, GUI, search, Stockfish, or tablebase behavior.
 */
public final class V1ReleaseRegressionMain {

    public static final String BUILD_ID =
            "M87-V1-RELEASE-REGRESSION-V1";


    private static final List<Gate> GATES =
            List.of(

                    new Gate(
                            "Core chess / graph / FEN",
                            "main.java.chess.tests.V1CoreRegressionVerificationMain"
                    ),

                    new Gate(
                            "M76 Dovetail frozen search",
                            "main.java.chess.search.DovetailStrengthVerificationMain"
                    ),

                    new Gate(
                            "M77 Hybrid frozen search",
                            "main.java.chess.search.HybridStrengthVerificationMain"
                    ),

                    new Gate(
                            "Three-piece packaged tablebases",
                            "main.java.chess.tests.ThreePieceTablebaseResourceTestHarness"
                    ),

                    new Gate(
                            "30-family four-piece catalog",
                            "main.java.chess.endgame.FourPieceCatalogCompletionMain"
                    ),

                    new Gate(
                            "Endgame move controller / practice strength",
                            "main.java.chess.engine.EndgameMoveControllerVerificationMain"
                    ),

                    new Gate(
                            "Stockfish process / forced-mate smoke",
                            "main.java.chess.stockfish.StockfishReleaseSmokeVerificationMain"
                    ),

                    new Gate(
                            "M86 KPKP packed runtime / 128-MiB heap",
                            "main.java.chess.endgame.FourPieceTierTwoKpkpPackedRuntimeVerificationMain"
                    )
            );


    private V1ReleaseRegressionMain() {
    }


    public static void main(
            String[] args
    ) throws Exception {

        if (args.length != 0) {
            throw new IllegalArgumentException(
                    "Usage: V1ReleaseRegressionMain"
            );
        }


        System.out.println(
                "M87 Chess Engine v1.0 release regression gate"
        );

        System.out.println(
                "============================================================"
        );

        System.out.println(
                "BUILD_ID: "
                        + BUILD_ID
        );

        System.out.println(
                "Working directory: "
                        + workingDirectory()
        );

        System.out.println(
                "Java: "
                        + System.getProperty(
                        "java.version"
                )
        );

        System.out.println(
                "Parent max heap: "
                        + formatMiB(
                        Runtime.getRuntime()
                                .maxMemory()
                )
        );

        System.out.println();


        verifyFrozenBuilds();


        List<GateResult> results =
                new ArrayList<>();

        long suiteStarted =
                System.nanoTime();


        for (int index = 0;
             index < GATES.size();
             index++) {

            Gate gate =
                    GATES.get(index);

            System.out.println();
            System.out.println(
                    "============================================================"
            );

            System.out.printf(
                    "[%d / %d] %s%n",
                    index + 1,
                    GATES.size(),
                    gate.label()
            );

            System.out.println(
                    gate.className()
            );

            System.out.println(
                    "============================================================"
            );

            System.out.println();


            long started =
                    System.nanoTime();

            int exit =
                    runChild(
                            gate.className()
                    );

            double seconds =
                    elapsedSeconds(
                            started
                    );


            if (exit != 0) {

                System.out.println();
                System.out.printf(
                        "FAILED: %s | exit %d | %.3f sec%n",
                        gate.label(),
                        exit,
                        seconds
                );

                throw new IllegalStateException(
                        "M87 stopped at gate "
                                + (index + 1)
                                + ": "
                                + gate.label()
                                + " (exit "
                                + exit
                                + ")"
                );
            }


            results.add(
                    new GateResult(
                            gate.label(),
                            seconds
                    )
            );


            System.out.println();

            System.out.printf(
                    "PASSED: %s | %.3f sec%n",
                    gate.label(),
                    seconds
            );
        }


        double totalSeconds =
                elapsedSeconds(
                        suiteStarted
                );


        System.out.println();
        System.out.println(
                "============================================================"
        );

        System.out.println(
                "M87 AUTOMATED RELEASE SUMMARY"
        );

        System.out.println(
                "============================================================"
        );


        for (int index = 0;
             index < results.size();
             index++) {

            GateResult result =
                    results.get(index);

            System.out.printf(
                    "%2d. %-45s PASS  %9.3f sec%n",
                    index + 1,
                    result.label(),
                    result.seconds()
            );
        }


        System.out.println(
                "------------------------------------------------------------"
        );

        System.out.printf(
                "Automated gates passed: %d / %d%n",
                results.size(),
                GATES.size()
        );

        System.out.printf(
                "Total elapsed: %.3f sec%n",
                totalSeconds
        );


        System.out.println();

        System.out.println(
                "============================================================"
        );

        System.out.println(
                "M87 AUTOMATED V1.0 REGRESSION GATE PASSED"
        );

        System.out.println(
                "============================================================"
        );

        System.out.println();

        System.out.println(
                "One manual GUI smoke check remains before M87 is fully signed off."
        );

        System.out.println(
                "Do not modify frozen search/tablebase code after this gate."
        );
    }


    // =========================================================
    // Frozen release identities
    // =========================================================

    private static void verifyFrozenBuilds() {

        require(
                "M76-STRENGTH-AWARE-DOVETAIL-WALKERS-V1"
                        .equals(
                                DovetailWalker.BUILD_ID
                        ),
                "Frozen M76 Dovetail build changed."
        );

        require(
                "M77-STRENGTH-AWARE-HYBRID-COVERAGE-V1"
                        .equals(
                                ExplorationScheduler.COVERAGE_BUILD_ID
                        ),
                "Frozen M77 Hybrid coverage build changed."
        );

        require(
                "M71-SEPARATE-DOVETAIL-HYBRID-V1"
                        .equals(
                                ChessEngine.MODE_BUILD_ID
                        ),
                "Frozen M71 Dovetail/Hybrid mode build changed."
        );

        require(
                "M81-COMMON-DEPTH-MULTIPV-V1"
                        .equals(
                                StockfishClient.CALIBRATION_BUILD_ID
                        ),
                "Frozen M81 Stockfish API build changed."
        );

        require(
                "M86-KPKP-PACKED-RUNTIME-V1"
                        .equals(
                                FourPieceTierTwoKpkpTablebaseCodec.RUNTIME_BUILD_ID
                        ),
                "Frozen M86 KPKP packed runtime build changed."
        );


        System.out.println(
                "Frozen release identities"
        );

        System.out.println(
                "-------------------------"
        );

        System.out.println(
                "M71 mode separation       PASSED"
        );

        System.out.println(
                "M76 Dovetail              PASSED"
        );

        System.out.println(
                "M77 Hybrid coverage       PASSED"
        );

        System.out.println(
                "M81 Stockfish API         PASSED"
        );

        System.out.println(
                "M86 KPKP packed runtime   PASSED"
        );
    }


    // =========================================================
    // Child-JVM execution
    // =========================================================

    private static int runChild(
            String className
    ) throws Exception {

        List<String> command =
                new ArrayList<>();

        command.add(
                javaExecutable()
        );

        command.add(
                "-Dfile.encoding=UTF-8"
        );

        /*
         * Preserve an explicit Stockfish path if the parent JVM was launched
         * with one. Otherwise StockfishClient's normal discovery remains active.
         */
        String stockfishPath =
                System.getProperty(
                        StockfishClient.PATH_PROPERTY
                );

        if (stockfishPath != null
                && !stockfishPath.isBlank()) {

            command.add(
                    "-D"
                            + StockfishClient.PATH_PROPERTY
                            + "="
                            + stockfishPath
            );
        }


        command.add(
                "-cp"
        );

        command.add(
                System.getProperty(
                        "java.class.path"
                )
        );

        command.add(
                className
        );


        ProcessBuilder builder =
                new ProcessBuilder(
                        command
                );

        builder.directory(
                workingDirectory()
                        .toFile()
        );

        builder.inheritIO();


        Process process =
                builder.start();


        return process.waitFor();
    }


    private static String javaExecutable() {

        String executable =
                System.getProperty(
                                "os.name",
                                ""
                        ).toLowerCase()
                        .contains(
                                "win"
                        )
                        ? "java.exe"
                        : "java";


        return Path.of(
                System.getProperty(
                        "java.home"
                ),
                "bin",
                executable
        ).toString();
    }


    private static Path workingDirectory() {

        return Path.of("")
                .toAbsolutePath()
                .normalize();
    }


    private static double elapsedSeconds(
            long started
    ) {

        return (
                System.nanoTime()
                        - started
        ) / 1_000_000_000.0;
    }


    private static String formatMiB(
            long bytes
    ) {

        return String.format(
                "%.2f MiB",
                bytes
                        / (1024.0 * 1024.0)
        );
    }


    private static void require(
            boolean condition,
            String message
    ) {

        if (!condition) {
            throw new IllegalStateException(
                    message
            );
        }
    }


    private record Gate(
            String label,
            String className
    ) {
    }


    private record GateResult(
            String label,
            double seconds
    ) {
    }
}

package main.java.chess.endgame;

import main.java.chess.model.Board;
import main.java.chess.model.Color;
import main.java.chess.model.Piece;
import main.java.chess.model.PieceType;
import main.java.chess.model.Position;
import main.java.chess.model.PositionKey;
import main.java.chess.model.Square;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;


/**
 * M86 release gate for the KP-KP runtime-memory blocker.
 *
 * Parent mode launches a fresh child JVM capped at 128 MiB. Passing inside
 * that deliberately small heap is stronger than merely succeeding under the
 * machine's ordinary/default Java heap and directly guards against regression
 * to M63's ~96 MiB pair of dense arrays.
 *
 * Child mode verifies:
 *   - unchanged M63 persisted SHA-256 identity,
 *   - exact M62B metadata,
 *   - one-byte/state packed runtime storage,
 *   - ordinary and EP-aware direct service probing,
 *   - ordinary and EP-aware ExactEndgameTablebase facade routing.
 */
public final class FourPieceTierTwoKpkpPackedRuntimeVerificationMain {

    public static final String BUILD_ID =
            "M86-KPKP-PACKED-RUNTIME-RELEASE-GATE-V1";

    private static final String CHILD_ARGUMENT =
            "--m86-child";

    private static final String EXPECTED_DIGEST =
            "65e248648ada7b4372976a0b41ef7d0bc7a684ce51fa85be17c51453688e7cc9";

    private static final int CHILD_HEAP_MIB =
            128;

    private static final int ORDINARY_STATE =
            17_428;

    private static final int EP_STATE =
            33_554_488;

    private FourPieceTierTwoKpkpPackedRuntimeVerificationMain() {
    }


    public static void main(
            String[] args
    ) throws Exception {

        if (args.length > 0
                && CHILD_ARGUMENT.equals(
                args[0]
        )) {

            Path directory =
                    args.length >= 2
                            ? Path.of(args[1])
                            : FourPieceTierTwoKpkpTablebaseService.DEFAULT_DIRECTORY;

            runChild(
                    directory.toAbsolutePath()
                            .normalize()
            );

            return;
        }

        if (args.length > 1) {
            throw new IllegalArgumentException(
                    "Usage: FourPieceTierTwoKpkpPackedRuntimeVerificationMain "
                            + "[tablebase-directory]"
            );
        }

        Path directory =
                args.length == 0
                        ? FourPieceTierTwoKpkpTablebaseService.DEFAULT_DIRECTORY
                        : Path.of(args[0]);

        directory =
                directory.toAbsolutePath()
                        .normalize();

        System.out.println(
                "M86 KP-KP packed runtime / default-heap release gate"
        );

        System.out.println(
                "====================================================="
        );

        System.out.println(
                "Verification BUILD_ID: "
                        + BUILD_ID
        );

        System.out.println(
                "Codec runtime BUILD:    "
                        + FourPieceTierTwoKpkpTablebaseCodec.RUNTIME_BUILD_ID
        );

        System.out.println(
                "Asset directory:        "
                        + directory
        );

        System.out.println(
                "Parent max heap:        "
                        + formatMiB(
                        Runtime.getRuntime()
                                .maxMemory()
                )
        );

        System.out.println(
                "Child verification heap: "
                        + CHILD_HEAP_MIB
                        + " MiB"
        );

        Path asset =
                new FourPieceTierTwoKpkpTablebaseService(
                        directory
                ).assetPath();

        require(
                Files.isRegularFile(asset),
                "Missing KP-KP asset: "
                        + asset
        );

        System.out.println();
        System.out.println(
                "Launching isolated -Xmx"
                        + CHILD_HEAP_MIB
                        + "m verification JVM..."
        );

        Process process =
                new ProcessBuilder(
                        javaExecutable(),
                        "-Xmx"
                                + CHILD_HEAP_MIB
                                + "m",
                        "-Dfile.encoding=UTF-8",
                        "-cp",
                        System.getProperty(
                                "java.class.path"
                        ),
                        FourPieceTierTwoKpkpPackedRuntimeVerificationMain.class
                                .getName(),
                        CHILD_ARGUMENT,
                        directory.toString()
                )
                        .inheritIO()
                        .start();

        int exit =
                process.waitFor();

        require(
                exit == 0,
                "M86 128-MiB child JVM failed with exit code "
                        + exit
        );

        System.out.println();
        System.out.println(
                "====================================================="
        );

        System.out.println(
                "M86 KP-KP PACKED RUNTIME MEMORY GATE PASSED"
        );

        System.out.println(
                "====================================================="
        );

        System.out.println();
        System.out.println(
                "Existing M63 KP-KP asset retained; no tablebase rebuild required."
        );

        System.out.println(
                "NEXT: M87 complete v1.0 regression + performance gate."
        );
    }


    private static void runChild(
            Path directory
    ) throws Exception {

        System.out.println();
        System.out.println(
                "[M86 child] max heap: "
                        + formatMiB(
                        Runtime.getRuntime()
                                .maxMemory()
                )
        );

        FourPieceTierTwoKpkpTablebaseService pathService =
                new FourPieceTierTwoKpkpTablebaseService(
                        directory
                );

        Path asset =
                pathService.assetPath();

        require(
                Files.isRegularFile(asset),
                "Missing KP-KP asset in child: "
                        + asset
        );

        System.out.println(
                "[M86 child] asset: "
                        + asset
        );

        long beforeLoad =
                usedHeap();

        long loadStarted =
                System.nanoTime();

        FourPieceTierTwoKpkpTablebaseCodec.Tablebase loaded =
                FourPieceTierTwoKpkpTablebaseCodec.load(
                        asset
                );

        double loadSeconds =
                (System.nanoTime()
                        - loadStarted)
                        / 1_000_000_000.0;

        long afterLoad =
                usedHeap();

        require(
                loaded.isPackedRuntime(),
                "Persisted KP-KP load did not use packed runtime storage."
        );

        require(
                loaded.runtimeStorageBytes()
                        == FourPieceTierTwoKpkpStateIndex.STATE_COUNT,
                "Packed KP-KP runtime is not exactly one byte/state."
        );

        verifyMetadata(
                loaded
        );

        String digest =
                FourPieceTierTwoKpkpTablebaseCodec.contentDigest(
                        loaded
                );

        require(
                EXPECTED_DIGEST.equals(
                        digest
                ),
                "KP-KP persisted digest changed."
                        + System.lineSeparator()
                        + "Expected: "
                        + EXPECTED_DIGEST
                        + System.lineSeparator()
                        + "Actual:   "
                        + digest
        );

        Fixture expectedOrdinary =
                fixture(
                        loaded,
                        ORDINARY_STATE
                );

        Fixture expectedEp =
                fixture(
                        loaded,
                        EP_STATE
                );

        System.out.printf(
                "[M86 child] packed load: %.3f sec%n",
                loadSeconds
        );

        System.out.println(
                "[M86 child] runtime storage: "
                        + formatMiB(
                        loaded.runtimeStorageBytes()
                )
                        + " ("
                        + String.format(
                        "%,d",
                        loaded.runtimeStorageBytes()
                )
                        + " bytes)"
        );

        System.out.println(
                "[M86 child] heap before/after direct load: "
                        + formatMiB(beforeLoad)
                        + " -> "
                        + formatMiB(afterLoad)
        );

        System.out.println(
                "[M86 child] M63 digest identity PASSED"
        );

        System.out.println(
                "[M86 child] M62B metadata identity PASSED"
        );

        /* Release the direct codec instance before exercising runtime routing. */
        loaded = null;
        forceCollection();

        Position ordinary =
                positionForState(
                        ORDINARY_STATE
                );

        Position ep =
                positionForState(
                        EP_STATE
                );

        FourPieceTierTwoKpkpTablebaseService service =
                new FourPieceTierTwoKpkpTablebaseService(
                        directory
                );

        verifyServiceProbe(
                service,
                ordinary,
                expectedOrdinary,
                "ordinary"
        );

        verifyServiceProbe(
                service,
                ep,
                expectedEp,
                "EP-aware"
        );

        require(
                service.loadedCount() == 1,
                "KP-KP service did not retain exactly one packed tablebase."
        );

        System.out.println(
                "[M86 child] direct service ordinary + EP routing PASSED"
        );

        service.clearCache();
        service = null;
        forceCollection();

        ExactEndgameTablebase exact =
                ExactEndgameTablebase.tierZeroCatalog();

        verifyExactProbe(
                exact,
                ordinary,
                expectedOrdinary,
                "ordinary"
        );

        verifyExactProbe(
                exact,
                ep,
                expectedEp,
                "EP-aware"
        );

        System.out.println(
                "[M86 child] ExactEndgameTablebase ordinary + EP routing PASSED"
        );

        System.out.println(
                "[M86 child] final used heap: "
                        + formatMiB(
                        usedHeap()
                )
        );

        System.out.println(
                "[M86 child] 128-MiB heap gate PASSED"
        );
    }


    private static void verifyMetadata(
            FourPieceTierTwoKpkpTablebaseCodec.Tablebase tablebase
    ) {

        require(
                tablebase.legalStates()
                        == 14_959_748L,
                "KP-KP legal-state count changed."
        );

        require(
                tablebase.legalEpStates()
                        == 87_572L,
                "KP-KP legal EP-state count changed."
        );

        require(
                tablebase.wins()
                        == 6_494_984L,
                "KP-KP WIN count changed."
        );

        require(
                tablebase.losses()
                        == 3_475_940L,
                "KP-KP LOSS count changed."
        );

        require(
                tablebase.draws()
                        == 4_988_824L,
                "KP-KP DRAW count changed."
        );

        require(
                tablebase.maximumDistance()
                        == 66,
                "KP-KP maximum DTM changed."
        );
    }


    private static Fixture fixture(
            FourPieceTierTwoKpkpTablebaseCodec.Tablebase tablebase,
            int state
    ) {

        byte outcome =
                tablebase.outcome(
                        state
                );

        short distance =
                tablebase.distance(
                        state
                );

        require(
                outcome != FourPieceTablebase.INVALID
                        && outcome != FourPieceTablebase.UNKNOWN,
                "Verification fixture is not a solved KP-KP state: "
                        + state
        );

        return new Fixture(
                state,
                outcome,
                distance,
                FourPieceTierTwoKpkpStateIndex
                        .enPassantAvailable(
                                state
                        )
        );
    }


    private static void verifyServiceProbe(
            FourPieceTierTwoKpkpTablebaseService service,
            Position position,
            Fixture expected,
            String label
    ) throws IOException {

        Optional<FourPieceTierTwoKpkpTablebaseService.ProbeResult> optional =
                service.probe(
                        position
                );

        require(
                optional.isPresent(),
                "KP-KP service rejected "
                        + label
                        + " fixture."
        );

        FourPieceTierTwoKpkpTablebaseService.ProbeResult probe =
                optional.get();

        require(
                probe.primitiveState()
                        == expected.state,
                "KP-KP service normalized "
                        + label
                        + " fixture to wrong state."
        );

        require(
                probe.outcome()
                        == expected.outcome
                        && probe.distance()
                        == expected.distance,
                "KP-KP service changed "
                        + label
                        + " WDL/DTM."
        );

        require(
                probe.enPassantAvailable()
                        == expected.ep,
                "KP-KP service changed "
                        + label
                        + " EP identity."
        );
    }


    private static void verifyExactProbe(
            ExactEndgameTablebase exact,
            Position position,
            Fixture expected,
            String label
    ) {

        ExactEndgameTablebase.Probe probe =
                exact.probe(
                        position
                );

        require(
                probe.mateDistance()
                        == expected.distance,
                "Exact facade changed "
                        + label
                        + " DTM."
        );

        ExactEndgameTablebase.Outcome expectedOutcome =
                switch (expected.outcome) {
                    case FourPieceTablebase.WIN ->
                            ExactEndgameTablebase.Outcome.WIN;
                    case FourPieceTablebase.LOSS ->
                            ExactEndgameTablebase.Outcome.LOSS;
                    case FourPieceTablebase.DRAW ->
                            ExactEndgameTablebase.Outcome.DRAW;
                    default ->
                            ExactEndgameTablebase.Outcome.UNSUPPORTED;
                };

        require(
                probe.outcome()
                        == expectedOutcome,
                "Exact facade changed "
                        + label
                        + " outcome. Expected "
                        + expectedOutcome
                        + ", found "
                        + probe.outcome()
        );
    }


    private static Position positionForState(
            int state
    ) {

        Board board =
                new Board();

        board.setPiece(
                square(
                        FourPieceTierTwoKpkpStateIndex.whiteKing(
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
                        FourPieceTierTwoKpkpStateIndex.blackKing(
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
                        FourPieceTierTwoKpkpStateIndex.whitePawn(
                                state
                        )
                ),
                new Piece(
                        PieceType.PAWN,
                        Color.WHITE
                )
        );

        board.setPiece(
                square(
                        FourPieceTierTwoKpkpStateIndex.blackPawn(
                                state
                        )
                ),
                new Piece(
                        PieceType.PAWN,
                        Color.BLACK
                )
        );

        Color sideToMove =
                FourPieceTierTwoKpkpStateIndex.blackToMove(
                        state
                )
                        ? Color.BLACK
                        : Color.WHITE;

        Square enPassantTarget =
                FourPieceTierTwoKpkpStateIndex.enPassantAvailable(
                        state
                )
                        ? square(
                        FourPieceTierTwoKpkpStateIndex
                                .enPassantTargetSquare(
                                        state
                                )
                )
                        : null;

        return position(
                board,
                sideToMove,
                enPassantTarget
        );
    }


    private static Position position(
            Board board,
            Color sideToMove,
            Square enPassantTarget
    ) {

        Position temporary =
                new Position(
                        board,
                        sideToMove,
                        false,
                        false,
                        false,
                        false,
                        enPassantTarget,
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
                enPassantTarget,
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


    private static long usedHeap() {

        Runtime runtime =
                Runtime.getRuntime();

        return runtime.totalMemory()
                - runtime.freeMemory();
    }


    private static void forceCollection()
            throws InterruptedException {

        System.gc();
        Thread.sleep(100L);
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


    private record Fixture(
            int state,
            byte outcome,
            short distance,
            boolean ep
    ) {
    }
}

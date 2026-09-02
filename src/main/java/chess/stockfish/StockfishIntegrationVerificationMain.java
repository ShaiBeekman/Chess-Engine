package main.java.chess.stockfish;

import java.nio.file.Path;
import java.time.Duration;

/**
 * M67A smoke test for the optional external Stockfish/UCI integration.
 *
 * Usage examples:
 *   java -Dstockfish.path="C:\\path\\to\\stockfish.exe" ...StockfishIntegrationVerificationMain
 *
 * or set environment variable STOCKFISH_PATH.
 */
public final class StockfishIntegrationVerificationMain {

    private static final String BUILD_ID =
            "M67A-STOCKFISH-UCI-FOUNDATION-V1";

    private static final String STARTING_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private StockfishIntegrationVerificationMain() {
    }

    public static void main(String[] args) throws Exception {
        System.out.println(BUILD_ID);
        System.out.println("========================================");

        Path executable = resolveExecutable(args);

        if (executable == null) {
            System.out.println("Stockfish status: NOT CONFIGURED");
            System.out.println();
            System.out.println("Configure one of:");
            System.out.println("  -Dstockfish.path=FULL_PATH_TO_STOCKFISH");
            System.out.println("  STOCKFISH_PATH environment variable");
            System.out.println("  ./stockfish/stockfish[.exe]");
            System.out.println();
            System.out.println("M67A FOUNDATION INSTALLED; LIVE UCI TEST SKIPPED");
            return;
        }

        System.out.println("Executable: " + executable);

        try (StockfishClient client = new StockfishClient(executable)) {
            client.start();

            System.out.println("UCI handshake: PASS");
            System.out.println("Engine: " + client.getEngineName());

            StockfishClient.Analysis analysis = client.analyzeFen(
                    STARTING_FEN,
                    12,
                    Duration.ofSeconds(20)
            );

            System.out.println("Analysis: PASS");
            System.out.println("Depth: " + analysis.depth());
            System.out.println("Score (side to move): " + analysis.scoreDisplay());
            System.out.println("Best move: " + analysis.bestMove());
            System.out.println("Nodes: " + analysis.nodes());
            System.out.println("NPS: " + analysis.nps());
            System.out.println("PV: " + String.join(" ", analysis.principalVariation()));

            if (analysis.bestMove() == null) {
                throw new IllegalStateException("Stockfish did not return a best move.");
            }

            if (analysis.depth() < 1) {
                throw new IllegalStateException("Stockfish did not report a valid depth.");
            }
        }

        System.out.println();
        System.out.println("M67A STOCKFISH UCI FOUNDATION PASSED");
    }

    private static Path resolveExecutable(String[] args) {
        if (args != null && args.length > 0 && args[0] != null && !args[0].isBlank()) {
            return Path.of(args[0]).toAbsolutePath().normalize();
        }

        return StockfishClient.locateConfiguredExecutable();
    }
}

package main.java.chess.stockfish;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Small, dependency-free UCI client used only for optional Stockfish
 * comparison.  It is deliberately separate from ChessEngine: Stockfish is a
 * reference engine, never part of the persistent graph search itself.
 */
public final class StockfishClient implements AutoCloseable {

    public static final String PATH_PROPERTY = "stockfish.path";
    public static final String PATH_ENVIRONMENT = "STOCKFISH_PATH";

    /** M81 strict same-root/same-depth MultiPV calibration support. */
    public static final String CALIBRATION_BUILD_ID =
            "M81-COMMON-DEPTH-MULTIPV-V1";

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(12);
    private static final Duration READY_TIMEOUT = Duration.ofSeconds(8);
    private static final String PROCESS_ENDED = "\u0000PROCESS_ENDED\u0000";

    private Path executable;
    private List<Path> executableCandidates;
    private boolean discoveryAttempted;
    private final BlockingQueue<String> outputLines;

    private Process process;
    private BufferedWriter input;
    private Thread readerThread;
    private String engineName;

    public StockfishClient(Path executable) {
        if (executable == null) {
            throw new IllegalArgumentException("Stockfish executable cannot be null.");
        }

        Path normalized =
                executable.toAbsolutePath().normalize();

        this.executable =
                normalized;

        this.executableCandidates =
                List.of(
                        normalized
                );

        this.discoveryAttempted =
                true;

        this.outputLines =
                new LinkedBlockingQueue<>();

        this.engineName =
                "Stockfish";
    }


    private StockfishClient(
            List<Path> executableCandidates
    ) {
        if (executableCandidates == null) {

            throw new IllegalArgumentException(
                    "Stockfish executable candidates cannot be null."
            );
        }

        this.executableCandidates =
                List.copyOf(
                        executableCandidates
                );

        this.executable =
                this.executableCandidates.isEmpty()
                        ? null
                        : this.executableCandidates.get(0);

        this.discoveryAttempted =
                !this.executableCandidates.isEmpty();

        this.outputLines =
                new LinkedBlockingQueue<>();

        this.engineName =
                "Stockfish";
    }


    /**
     * Create a lazily-started client that can fall through multiple likely
     * Stockfish executable names. No process is launched here.
     */
    public static StockfishClient createConfiguredClient() {

        /*
         * Do not scan Downloads/Desktop on Swing's event thread. Candidate
         * discovery happens lazily inside start(), which ChessWindow invokes
         * from a SwingWorker.
         */
        return new StockfishClient(
                List.of()
        );
    }

    public synchronized void start() throws IOException {

        if (isRunning()) {
            return;
        }


        IOException lastFailure =
                null;

        List<String> attempted =
                new ArrayList<>();

        if (!discoveryAttempted) {

            executableCandidates =
                    locateConfiguredExecutables();

            discoveryAttempted =
                    true;
        }


        List<Path> candidates =
                executableCandidates;


        if (candidates.isEmpty()) {

            throw new IOException(
                    "Stockfish executable was not found. Configure -D"
                            + PATH_PROPERTY
                            + "=FULL_PATH, set "
                            + PATH_ENVIRONMENT
                            + ", or place an extracted Stockfish executable "
                            + "in the project, Downloads, or Desktop."
            );
        }


        for (Path candidate :
                candidates) {

            attempted.add(
                    candidate.toString()
            );


            try {

                startCandidate(
                        candidate
                );

                return;


            } catch (IOException failure) {

                lastFailure =
                        failure;

                close();
            }
        }


        String detail =
                lastFailure == null
                        || lastFailure.getMessage() == null
                        ? "Unknown UCI startup failure."
                        : lastFailure.getMessage();


        throw new IOException(
                "No usable Stockfish UCI executable was found. Tried: "
                        + String.join(
                        " ; ",
                        attempted
                )
                        + ". Last error: "
                        + detail,
                lastFailure
        );
    }


    private void startCandidate(
            Path candidate
    ) throws IOException {

        Path normalized =
                candidate.toAbsolutePath().normalize();


        if (!Files.isRegularFile(
                normalized
        )) {

            throw new IOException(
                    "Stockfish executable was not found: "
                            + normalized
            );
        }


        executable =
                normalized;

        engineName =
                "Stockfish";


        ProcessBuilder builder =
                new ProcessBuilder(
                        normalized.toString()
                );

        builder.redirectErrorStream(
                true
        );


        process =
                builder.start();

        input =
                new BufferedWriter(
                        new OutputStreamWriter(
                                process.getOutputStream(),
                                StandardCharsets.UTF_8
                        )
                );


        outputLines.clear();


        readerThread =
                new Thread(
                        this::readProcessOutput,
                        "stockfish-uci-reader"
                );

        readerThread.setDaemon(
                true
        );

        readerThread.start();


        List<String> transcript =
                new ArrayList<>();


        boolean uciOk =
                performUciHandshake(
                        transcript
                );


        if (!uciOk
                && isRunning()) {

            uciOk =
                    performUciHandshake(
                            transcript
                    );
        }


        if (!uciOk) {

            String startupOutput =
                    transcript.isEmpty()
                            ? "(no output received)"
                            : String.join(
                            " | ",
                            transcript.subList(
                                    0,
                                    Math.min(
                                            transcript.size(),
                                            12
                                    )
                            )
                    );


            throw new IOException(
                    "Stockfish did not complete the UCI handshake. "
                            + "Executable: "
                            + normalized
                            + ". Startup output: "
                            + startupOutput
            );
        }


        waitUntilReady();
    }


    private boolean performUciHandshake(
            List<String> transcript
    ) throws IOException {

        outputLines.clear();

        send(
                "uci"
        );


        long deadline =
                System.nanoTime()
                        + STARTUP_TIMEOUT.toNanos();


        while (System.nanoTime()
                < deadline) {

            String line =
                    pollUntil(
                            deadline
                    );


            if (line == null) {
                break;
            }


            String normalized =
                    line.trim();


            if (!normalized.isEmpty()) {

                transcript.add(
                        normalized
                );
            }


            if (normalized.regionMatches(
                    true,
                    0,
                    "id name ",
                    0,
                    "id name ".length()
            )) {

                String discovered =
                        normalized
                                .substring(
                                        "id name ".length()
                                )
                                .trim();


                if (!discovered.isEmpty()) {

                    engineName =
                            discovered;
                }
            }


            if ("uciok".equalsIgnoreCase(
                    normalized
            )) {

                return true;
            }
        }


        return false;
    }


    public synchronized Analysis analyzeFen(
            String fen,
            int depth,
            Duration timeout
    ) throws IOException {
        Objects.requireNonNull(fen, "FEN cannot be null.");
        Objects.requireNonNull(timeout, "Timeout cannot be null.");

        if (fen.isBlank()) {
            throw new IllegalArgumentException("FEN cannot be blank.");
        }

        if (depth <= 0) {
            throw new IllegalArgumentException("Depth must be positive.");
        }

        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Timeout must be positive.");
        }

        ensureRunning();

        /*
         * Stop any prior search defensively, then synchronize before issuing
         * the next position.  This keeps GUI requests deterministic when a
         * later milestone starts replacing one comparison request with another.
         */
        send("stop");
        waitUntilReady();

        outputLines.clear();
        send("position fen " + fen);
        send("go depth " + depth);

        long deadline = System.nanoTime() + timeout.toNanos();

        int reportedDepth = -1;
        long nodes = -1L;
        long nps = -1L;
        Integer centipawns = null;
        Integer mateIn = null;
        List<String> principalVariation = List.of();
        String bestMove = null;
        String ponderMove = null;

        while (System.nanoTime() < deadline) {
            String line = pollUntil(deadline);
            if (line == null) {
                break;
            }

            if (line.startsWith("info ")) {
                ParsedInfo parsed = parseInfo(line);

                if (parsed.depth >= 0) {
                    reportedDepth = parsed.depth;
                }
                if (parsed.nodes >= 0L) {
                    nodes = parsed.nodes;
                }
                if (parsed.nps >= 0L) {
                    nps = parsed.nps;
                }
                if (parsed.centipawns != null || parsed.mateIn != null) {
                    centipawns = parsed.centipawns;
                    mateIn = parsed.mateIn;
                }
                if (!parsed.principalVariation.isEmpty()) {
                    principalVariation = parsed.principalVariation;
                }

                continue;
            }

            if (line.startsWith("bestmove ")) {
                String[] tokens = line.trim().split("\\s+");
                if (tokens.length >= 2) {
                    bestMove = normalizeUciMove(tokens[1]);
                }
                if (tokens.length >= 4 && "ponder".equals(tokens[2])) {
                    ponderMove = normalizeUciMove(tokens[3]);
                }
                break;
            }
        }

        if (bestMove == null) {
            send("stop");
            throw new IOException(
                    "Stockfish analysis timed out after "
                            + timeout.toMillis()
                            + " ms."
            );
        }

        return new Analysis(
                engineName,
                fen,
                reportedDepth,
                centipawns,
                mateIn,
                nodes,
                nps,
                bestMove,
                ponderMove,
                principalVariation
        );
    }


    /**
     * Analyze several candidate moves from the same root using UCI MultiPV.
     * Scores are returned exactly as Stockfish reports them (root side-to-move
     * perspective); the GUI is responsible for converting to White-positive.
     */
    public synchronized List<Analysis> analyzeFenMultiPv(
            String fen,
            int depth,
            int multiPv,
            Duration timeout
    ) throws IOException {

        Objects.requireNonNull(
                fen,
                "FEN cannot be null."
        );

        Objects.requireNonNull(
                timeout,
                "Timeout cannot be null."
        );


        if (fen.isBlank()) {

            throw new IllegalArgumentException(
                    "FEN cannot be blank."
            );
        }

        if (depth <= 0) {

            throw new IllegalArgumentException(
                    "Depth must be positive."
            );
        }

        if (multiPv <= 0) {

            throw new IllegalArgumentException(
                    "MultiPV must be positive."
            );
        }

        if (timeout.isZero()
                || timeout.isNegative()) {

            throw new IllegalArgumentException(
                    "Timeout must be positive."
            );
        }


        ensureRunning();

        send(
                "stop"
        );

        waitUntilReady();


        /*
         * Always restore MultiPV=1 before returning, including timeout/error
         * paths. The older implementation threw before resetting the option,
         * which could leave later ordinary Stockfish searches accidentally
         * running in MultiPV mode.
         */
        boolean multiPvEnabled =
                false;


        try {

            send(
                    "setoption name MultiPV value "
                            + multiPv
            );

            multiPvEnabled =
                    true;

            waitUntilReady();


            outputLines.clear();

            send(
                    "position fen "
                            + fen
            );

            send(
                    "go depth "
                            + depth
            );


            long deadline =
                    System.nanoTime()
                            + timeout.toNanos();


            java.util.Map<Integer, Analysis> latest =
                    new java.util.TreeMap<>();


            boolean receivedBestMove =
                    false;


            /*
             * Stockfish emits a new line for each multipv index at every
             * completed depth. Keep the newest line for every index.
             */
            while (System.nanoTime()
                    < deadline) {

                String line =
                        pollUntil(
                                deadline
                        );


                if (line == null) {
                    break;
                }


                if (line.startsWith(
                        "info "
                )) {

                    collectMultiPvInfo(
                            fen,
                            line,
                            latest
                    );

                    continue;
                }


                if (line.startsWith(
                        "bestmove "
                )) {

                    receivedBestMove =
                            true;

                    break;
                }
            }


            /*
             * A broad MultiPV search is much more expensive than a single-PV
             * search. Missing the requested final depth is not a reason to
             * discard all the perfectly useful completed lower-depth lines.
             *
             * Ask Stockfish to stop and give it a short grace period to flush
             * its latest full MultiPV set plus bestmove.
             */
            if (!receivedBestMove) {

                send(
                        "stop"
                );


                long stopDeadline =
                        System.nanoTime()
                                + Duration
                                .ofSeconds(
                                        2
                                )
                                .toNanos();


                while (System.nanoTime()
                        < stopDeadline) {

                    String line =
                            pollUntil(
                                    stopDeadline
                            );


                    if (line == null) {
                        break;
                    }


                    if (line.startsWith(
                            "info "
                    )) {

                        collectMultiPvInfo(
                                fen,
                                line,
                                latest
                        );

                        continue;
                    }


                    if (line.startsWith(
                            "bestmove "
                    )) {

                        receivedBestMove =
                                true;

                        break;
                    }
                }
            }


            if (latest.isEmpty()) {

                throw new IOException(
                        "Stockfish MultiPV produced no candidate lines within "
                                + timeout.toMillis()
                                + " ms."
                );
            }


            /*
             * Returning partial completed-depth lines is intentional.
             * Example: if MultiPV=8 reaches depth 12 for all eight lines but
             * times out while working on depth 13/14, the GUI should still
             * receive those eight real Stockfish candidates instead of falling
             * back to one authoritative move.
             */
            return List.copyOf(
                    latest.values()
            );


        } finally {

            if (multiPvEnabled) {

                try {

                    send(
                            "stop"
                    );

                    send(
                            "setoption name MultiPV value 1"
                    );

                    waitUntilReady();

                } catch (IOException ignored) {

                    /*
                     * If the process died, the original exception/result is
                     * more useful than masking it with cleanup failure.
                     */
                }
            }
        }
    }


    /**
     * M81 strict calibration search.
     *
     * Unlike {@link #analyzeFenMultiPv(String, int, int, Duration)}, this
     * method never returns a mixture of MultiPV lines from different depths.
     * It records every completed MultiPV line by depth and returns only the
     * highest depth for which every requested MultiPV index 1..multiPv was
     * present with a distinct root move.
     *
     * A fresh UCI game is announced before the search so previous searches do
     * not influence the calibration through a warm transposition table.
     * Scores remain in Stockfish's root-side-to-move perspective.
     */
    public synchronized MultiPvSnapshot analyzeFenMultiPvCommonDepth(
            String fen,
            int depth,
            int multiPv,
            Duration timeout
    ) throws IOException {

        Objects.requireNonNull(
                fen,
                "FEN cannot be null."
        );

        Objects.requireNonNull(
                timeout,
                "Timeout cannot be null."
        );

        if (fen.isBlank()) {
            throw new IllegalArgumentException(
                    "FEN cannot be blank."
            );
        }

        if (depth <= 0) {
            throw new IllegalArgumentException(
                    "Depth must be positive."
            );
        }

        if (multiPv <= 0) {
            throw new IllegalArgumentException(
                    "MultiPV must be positive."
            );
        }

        if (timeout.isZero()
                || timeout.isNegative()) {

            throw new IllegalArgumentException(
                    "Timeout must be positive."
            );
        }

        ensureRunning();

        send(
                "stop"
        );

        waitUntilReady();

        boolean multiPvEnabled =
                false;

        try {

            send(
                    "setoption name MultiPV value "
                            + multiPv
            );

            multiPvEnabled =
                    true;

            waitUntilReady();

            /*
             * Calibration must not inherit a transposition table populated by
             * whichever benchmark happened to run immediately beforehand.
             */
            send(
                    "ucinewgame"
            );

            waitUntilReady();

            outputLines.clear();

            send(
                    "position fen "
                            + fen
            );

            send(
                    "go depth "
                            + depth
            );

            long deadline =
                    System.nanoTime()
                            + timeout.toNanos();

            java.util.NavigableMap<
                    Integer,
                    java.util.Map<Integer, Analysis>
                    > byDepth =
                    new java.util.TreeMap<>();

            boolean receivedBestMove =
                    false;

            while (System.nanoTime()
                    < deadline) {

                String line =
                        pollUntil(
                                deadline
                        );

                if (line == null) {
                    break;
                }

                if (line.startsWith(
                        "info "
                )) {

                    collectMultiPvInfoByDepth(
                            fen,
                            line,
                            byDepth
                    );

                    continue;
                }

                if (line.startsWith(
                        "bestmove "
                )) {

                    receivedBestMove =
                            true;

                    break;
                }
            }

            if (!receivedBestMove) {

                send(
                        "stop"
                );

                long stopDeadline =
                        System.nanoTime()
                                + Duration
                                .ofSeconds(
                                        2
                                )
                                .toNanos();

                while (System.nanoTime()
                        < stopDeadline) {

                    String line =
                            pollUntil(
                                    stopDeadline
                            );

                    if (line == null) {
                        break;
                    }

                    if (line.startsWith(
                            "info "
                    )) {

                        collectMultiPvInfoByDepth(
                                fen,
                                line,
                                byDepth
                        );

                        continue;
                    }

                    if (line.startsWith(
                            "bestmove "
                    )) {

                        receivedBestMove =
                                true;

                        break;
                    }
                }
            }

            int commonDepth =
                    -1;

            java.util.Map<Integer, Analysis> commonLines =
                    null;

            for (java.util.Map.Entry<
                    Integer,
                    java.util.Map<Integer, Analysis>
                    > entry :
                    byDepth.descendingMap()
                            .entrySet()) {

                if (!isCompleteMultiPvDepth(
                        entry.getValue(),
                        multiPv
                )) {
                    continue;
                }

                commonDepth =
                        entry.getKey();

                commonLines =
                        entry.getValue();

                break;
            }

            if (commonLines == null) {

                int deepestObservedDepth =
                        byDepth.isEmpty()
                                ? -1
                                : byDepth.lastKey();

                int deepestObservedLines =
                        deepestObservedDepth < 0
                                ? 0
                                : byDepth.get(
                                deepestObservedDepth
                        ).size();

                throw new IOException(
                        "Stockfish produced no complete same-depth MultiPV set. "
                                + "Requested "
                                + multiPv
                                + " lines through depth "
                                + depth
                                + "; deepest observed depth was "
                                + deepestObservedDepth
                                + " with "
                                + deepestObservedLines
                                + " collected indices."
                );
            }

            List<Analysis> ordered =
                    new ArrayList<>(
                            multiPv
                    );

            for (int pvIndex = 1;
                 pvIndex <= multiPv;
                 pvIndex++) {

                ordered.add(
                        commonLines.get(
                                pvIndex
                        )
                );
            }

            return new MultiPvSnapshot(
                    engineName,
                    fen,
                    depth,
                    commonDepth,
                    multiPv,
                    ordered
            );

        } finally {

            if (multiPvEnabled) {

                try {

                    send(
                            "stop"
                    );

                    send(
                            "setoption name MultiPV value 1"
                    );

                    waitUntilReady();

                } catch (IOException ignored) {

                    /*
                     * Preserve the original result/failure if the process
                     * disappeared during cleanup.
                     */
                }
            }
        }
    }


    private void collectMultiPvInfoByDepth(
            String fen,
            String line,
            java.util.Map<
                    Integer,
                    java.util.Map<Integer, Analysis>
                    > byDepth
    ) {

        if (line == null
                || byDepth == null
                || !line.startsWith(
                "info "
        )) {

            return;
        }

        String[] tokens =
                line
                        .trim()
                        .split(
                                "\\s+"
                        );

        int pvIndex =
                1;

        for (int i = 0;
             i + 1 < tokens.length;
             i++) {

            if (!"multipv".equals(
                    tokens[i]
            )) {
                continue;
            }

            try {

                pvIndex =
                        Integer.parseInt(
                                tokens[i + 1]
                        );

            } catch (NumberFormatException ignored) {

                pvIndex =
                        1;
            }

            break;
        }

        ParsedInfo parsed =
                parseInfo(
                        line
                );

        if (parsed.depth < 0
                || parsed.principalVariation.isEmpty()
                || (parsed.centipawns == null
                && parsed.mateIn == null)) {

            return;
        }

        String candidateMove =
                normalizeUciMove(
                        parsed.principalVariation.get(
                                0
                        )
                );

        Analysis analysis =
                new Analysis(
                        engineName,
                        fen,
                        parsed.depth,
                        parsed.centipawns,
                        parsed.mateIn,
                        parsed.nodes,
                        parsed.nps,
                        candidateMove,
                        null,
                        parsed.principalVariation
                );

        byDepth
                .computeIfAbsent(
                        parsed.depth,
                        ignored ->
                                new java.util.TreeMap<>()
                )
                .put(
                        pvIndex,
                        analysis
                );
    }


    private boolean isCompleteMultiPvDepth(
            java.util.Map<Integer, Analysis> lines,
            int multiPv
    ) {

        if (lines == null
                || lines.size() < multiPv) {

            return false;
        }

        Set<String> distinctMoves =
                new LinkedHashSet<>();

        for (int pvIndex = 1;
             pvIndex <= multiPv;
             pvIndex++) {

            Analysis analysis =
                    lines.get(
                            pvIndex
                    );

            if (analysis == null
                    || analysis.depth() < 0
                    || analysis.bestMove() == null
                    || analysis.bestMove().isBlank()) {

                return false;
            }

            distinctMoves.add(
                    analysis.bestMove()
            );
        }

        return distinctMoves.size()
                == multiPv;
    }


    private void collectMultiPvInfo(
            String fen,
            String line,
            java.util.Map<Integer, Analysis> latest
    ) {

        if (line == null
                || latest == null
                || !line.startsWith(
                "info "
        )) {

            return;
        }


        String[] tokens =
                line
                        .trim()
                        .split(
                                "\\s+"
                        );


        int pvIndex =
                1;


        for (int i = 0;
             i + 1 < tokens.length;
             i++) {

            if (!"multipv".equals(
                    tokens[i]
            )) {

                continue;
            }


            try {

                pvIndex =
                        Integer.parseInt(
                                tokens[i + 1]
                        );

            } catch (NumberFormatException ignored) {

                pvIndex =
                        1;
            }


            break;
        }


        ParsedInfo parsed =
                parseInfo(
                        line
                );


        if (parsed.principalVariation.isEmpty()
                ||
                (parsed.centipawns == null
                        && parsed.mateIn == null)) {

            return;
        }


        String candidateMove =
                normalizeUciMove(
                        parsed.principalVariation.get(
                                0
                        )
                );


        latest.put(
                pvIndex,
                new Analysis(
                        engineName,
                        fen,
                        parsed.depth,
                        parsed.centipawns,
                        parsed.mateIn,
                        parsed.nodes,
                        parsed.nps,
                        candidateMove,
                        null,
                        parsed.principalVariation
                )
        );
    }


    public synchronized Analysis analyzeFen(String fen, int depth)
            throws IOException {
        return analyzeFen(fen, depth, Duration.ofSeconds(15));
    }

    public synchronized String getEngineName() {
        return engineName;
    }

    public Path getExecutable() {
        return executable;
    }

    public synchronized boolean isRunning() {
        return process != null && process.isAlive();
    }

    private void ensureRunning() throws IOException {
        if (!isRunning()) {
            start();
        }
    }

    private void waitUntilReady() throws IOException {
        send("isready");

        long deadline = System.nanoTime() + READY_TIMEOUT.toNanos();

        while (System.nanoTime() < deadline) {
            String line = pollUntil(deadline);
            if (line == null) {
                break;
            }

            if ("readyok".equals(line)) {
                return;
            }
        }

        throw new IOException("Stockfish did not answer isready in time.");
    }

    private void send(String command) throws IOException {
        if (input == null) {
            throw new IOException("Stockfish process is not available.");
        }

        input.write(command);
        input.newLine();
        input.flush();
    }

    private String pollUntil(long deadlineNanos) throws IOException {
        long remaining = deadlineNanos - System.nanoTime();
        if (remaining <= 0L) {
            return null;
        }

        try {
            String line = outputLines.poll(remaining, TimeUnit.NANOSECONDS);

            if (PROCESS_ENDED.equals(line)) {
                throw new IOException("Stockfish process ended unexpectedly.");
            }

            return line;

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for Stockfish.", exception);
        }
    }

    private void readProcessOutput() {
        Process localProcess = process;
        if (localProcess == null) {
            return;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                localProcess.getInputStream(),
                StandardCharsets.UTF_8
        ))) {
            String line;
            while ((line = reader.readLine()) != null) {
                outputLines.offer(line.trim());
            }
        } catch (IOException ignored) {
            // close() intentionally tears the process down.
        } finally {
            outputLines.offer(PROCESS_ENDED);
        }
    }

    private ParsedInfo parseInfo(String line) {
        String[] tokens = line.trim().split("\\s+");

        int depth = -1;
        long nodes = -1L;
        long nps = -1L;
        Integer centipawns = null;
        Integer mateIn = null;
        List<String> pv = List.of();

        for (int index = 1; index < tokens.length; index++) {
            String token = tokens[index];

            switch (token) {
                case "depth" -> {
                    if (index + 1 < tokens.length) {
                        depth = parseInt(tokens[++index], depth);
                    }
                }
                case "nodes" -> {
                    if (index + 1 < tokens.length) {
                        nodes = parseLong(tokens[++index], nodes);
                    }
                }
                case "nps" -> {
                    if (index + 1 < tokens.length) {
                        nps = parseLong(tokens[++index], nps);
                    }
                }
                case "score" -> {
                    if (index + 2 < tokens.length) {
                        String scoreType = tokens[++index];
                        String scoreValue = tokens[++index];

                        if ("cp".equals(scoreType)) {
                            centipawns = parseNullableInt(scoreValue);
                            mateIn = null;
                        } else if ("mate".equals(scoreType)) {
                            mateIn = parseNullableInt(scoreValue);
                            centipawns = null;
                        }
                    }
                }
                case "pv" -> {
                    ArrayList<String> moves = new ArrayList<>();
                    for (int pvIndex = index + 1; pvIndex < tokens.length; pvIndex++) {
                        moves.add(tokens[pvIndex]);
                    }
                    pv = List.copyOf(moves);
                    index = tokens.length;
                }
                default -> {
                    // UCI info has many optional fields; ignore those we do not need.
                }
            }
        }

        return new ParsedInfo(depth, nodes, nps, centipawns, mateIn, pv);
    }

    private static int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static long parseLong(String text, long fallback) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static Integer parseNullableInt(String text) {
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String normalizeUciMove(String move) {
        if (move == null || move.isBlank() || "(none)".equals(move)) {
            return null;
        }
        return move;
    }

    @Override
    public synchronized void close() {
        if (process == null) {
            return;
        }

        try {
            if (process.isAlive() && input != null) {
                try {
                    send("quit");
                } catch (IOException ignored) {
                    // Fall through to forcible cleanup when necessary.
                }

                try {
                    if (!process.waitFor(800, TimeUnit.MILLISECONDS)) {
                        process.destroy();
                    }
                    if (process.isAlive()
                            && !process.waitFor(500, TimeUnit.MILLISECONDS)) {
                        process.destroyForcibly();
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    process.destroyForcibly();
                }
            }
        } finally {
            input = null;
            process = null;
            readerThread = null;
            outputLines.clear();
        }
    }

    /**
     * Resolve Stockfish without baking a machine-specific path into source.
     *
     * Official Windows releases are commonly named things such as
     * stockfish-windows-x86-64-avx2.exe rather than simply stockfish.exe, so
     * resolution keeps a candidate list instead of assuming one basename.
     */
    public static Path locateConfiguredExecutable() {

        List<Path> candidates =
                locateConfiguredExecutables();

        return candidates.isEmpty()
                ? null
                : candidates.get(0);
    }


    public static List<Path> locateConfiguredExecutables() {

        Set<Path> resolved =
                new LinkedHashSet<>();


        addExistingPath(
                resolved,
                System.getProperty(
                        PATH_PROPERTY
                )
        );


        addExistingPath(
                resolved,
                System.getenv(
                        PATH_ENVIRONMENT
                )
        );


        boolean windows =
                System.getProperty(
                                "os.name",
                                ""
                        )
                        .toLowerCase(
                                Locale.ROOT
                        )
                        .contains(
                                "win"
                        );


        String executableName =
                windows
                        ? "stockfish.exe"
                        : "stockfish";


        List<Path> exactCandidates =
                List.of(
                        Path.of(
                                "stockfish",
                                executableName
                        ),
                        Path.of(
                                "tools",
                                "stockfish",
                                executableName
                        ),
                        Path.of(
                                "engine",
                                executableName
                        ),
                        Path.of(
                                "src",
                                "main",
                                "java",
                                "chess",
                                "stockfish",
                                executableName
                        ),
                        Path.of(
                                executableName
                        )
                );


        for (Path candidate :
                exactCandidates) {

            addCandidate(
                    resolved,
                    candidate
            );
        }


        List<Path> scanRoots =
                new ArrayList<>();


        scanRoots.add(
                Path.of(
                        "."
                )
        );


        scanRoots.add(
                Path.of(
                        "stockfish"
                )
        );

        scanRoots.add(
                Path.of(
                        "tools",
                        "stockfish"
                )
        );

        scanRoots.add(
                Path.of(
                        "engine"
                )
        );

        scanRoots.add(
                Path.of(
                        "src",
                        "main",
                        "java",
                        "chess",
                        "stockfish"
                )
        );


        String userHome =
                System.getProperty(
                        "user.home"
                );


        if (userHome != null
                && !userHome.isBlank()) {

            scanRoots.add(
                    Path.of(
                            userHome,
                            "Downloads"
                    )
            );

            scanRoots.add(
                    Path.of(
                            userHome,
                            "Desktop"
                    )
            );
        }


        for (Path root :
                scanRoots) {

            addStockfishExecutablesUnder(
                    resolved,
                    root,
                    windows
            );
        }


        return List.copyOf(
                resolved
        );
    }


    private static void addStockfishExecutablesUnder(
            Set<Path> resolved,
            Path root,
            boolean windows
    ) {

        if (root == null
                || !Files.isDirectory(
                root
        )) {

            return;
        }


        try (var stream =
                     Files.find(
                             root,
                             3,
                             (
                                     path,
                                     attributes
                             ) -> {

                                 if (!attributes.isRegularFile()) {
                                     return false;
                                 }


                                 String name =
                                         path.getFileName()
                                                 .toString()
                                                 .toLowerCase(
                                                         Locale.ROOT
                                                 );


                                 if (!name.startsWith(
                                         "stockfish"
                                 )) {

                                     return false;
                                 }


                                 return windows
                                         ? name.endsWith(
                                         ".exe"
                                 )
                                         : !name.endsWith(
                                         ".zip"
                                 );
                             }
                     )) {

            stream
                    .sorted()
                    .forEach(
                            path ->
                                    addCandidate(
                                            resolved,
                                            path
                                    )
                    );


        } catch (IOException
                 | RuntimeException ignored) {

            // Best-effort discovery.
        }
    }


    private static void addExistingPath(
            Set<Path> resolved,
            String value
    ) {

        if (value == null
                || value.isBlank()) {

            return;
        }


        try {

            addCandidate(
                    resolved,
                    Path.of(
                            value.trim()
                    )
            );


        } catch (RuntimeException ignored) {

            // Invalid explicit path; other candidates may still work.
        }
    }


    private static void addCandidate(
            Set<Path> resolved,
            Path candidate
    ) {

        if (candidate == null) {
            return;
        }


        try {

            Path normalized =
                    candidate.toAbsolutePath()
                            .normalize();


            if (Files.isRegularFile(
                    normalized
            )) {

                resolved.add(
                        normalized
                );
            }


        } catch (RuntimeException ignored) {

            // Ignore malformed or inaccessible candidates.
        }
    }


    /**
     * A strict MultiPV snapshot whose lines all come from the same root search
     * and the same completed Stockfish depth. Lines are ordered by MultiPV
     * index, so lines().get(0) is Stockfish rank #1 for this snapshot.
     */
    public record MultiPvSnapshot(
            String engineName,
            String fen,
            int requestedDepth,
            int commonDepth,
            int requestedMultiPv,
            List<Analysis> lines
    ) {

        public MultiPvSnapshot {

            lines =
                    lines == null
                            ? List.of()
                            : List.copyOf(
                            lines
                    );
        }

        public boolean reachedRequestedDepth() {

            return commonDepth
                    >= requestedDepth;
        }
    }


    private record ParsedInfo(
            int depth,
            long nodes,
            long nps,
            Integer centipawns,
            Integer mateIn,
            List<String> principalVariation
    ) {
    }

    public record Analysis(
            String engineName,
            String fen,
            int depth,
            Integer centipawns,
            Integer mateIn,
            long nodes,
            long nps,
            String bestMove,
            String ponderMove,
            List<String> principalVariation
    ) {
        public Analysis {
            principalVariation = principalVariation == null
                    ? List.of()
                    : List.copyOf(principalVariation);
        }

        public boolean hasCentipawnScore() {
            return centipawns != null;
        }

        public boolean hasMateScore() {
            return mateIn != null;
        }

        public String scoreDisplay() {
            if (mateIn != null) {
                return "mate " + mateIn;
            }
            if (centipawns != null) {
                return String.format(Locale.ROOT, "%+.2f", centipawns / 100.0);
            }
            return "—";
        }
    }
}

package main.java.chess.endgame;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class EndgameStudyProgressStore {

    private static final String VERSION =
            "DOVETAIL_ENDGAME_PROGRESS_V4";

    private final Path file;


    public EndgameStudyProgressStore() {
        this(defaultPath());
    }


    public EndgameStudyProgressStore(Path file) {
        if (file == null) {
            throw new IllegalArgumentException(
                    "Progress path cannot be null."
            );
        }

        this.file = file.toAbsolutePath().normalize();
    }


    public Path file() {
        return file;
    }


    public EndgameStudyProgress load() {
        EndgameStudyProgress result =
                new EndgameStudyProgress();

        if (!Files.isRegularFile(file)) {
            return result;
        }

        try {
            List<String> lines =
                    Files.readAllLines(
                            file,
                            StandardCharsets.UTF_8
                    );

            if (lines.isEmpty()
                    || !isSupportedVersion(lines.get(0))) {
                return result;
            }

            Map<String, EndgameStudyProgress.PositionProgress> loaded =
                    new LinkedHashMap<>();

            Map<String, Long> cursors =
                    new LinkedHashMap<>();

            Map<String, EndgameStudyProgress.StudyOrder> orders =
                    new LinkedHashMap<>();

            Map<String, List<String>> reviews =
                    new LinkedHashMap<>();

            Set<String> completed =
                    new LinkedHashSet<>();

            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);

                if (line.isBlank()) {
                    continue;
                }

                String[] parts = line.split("\\t", -1);

                if (parts.length == 3
                        && "@CURSOR".equals(parts[0])) {

                    try {
                        cursors.put(
                                unescape(parts[1]),
                                Long.parseLong(parts[2])
                        );
                    } catch (RuntimeException ignored) {
                    }

                    continue;
                }

                if (parts.length == 3
                        && "@ORDER".equals(parts[0])) {

                    try {
                        orders.put(
                                unescape(parts[1]),
                                EndgameStudyProgress.StudyOrder.valueOf(
                                        parts[2]
                                )
                        );
                    } catch (RuntimeException ignored) {
                    }

                    continue;
                }

                if (parts.length == 3
                        && "@REVIEW".equals(parts[0])) {

                    try {
                        reviews.computeIfAbsent(
                                unescape(parts[1]),
                                ignored -> new java.util.ArrayList<>()
                        ).add(
                                unescape(parts[2])
                        );
                    } catch (RuntimeException ignored) {
                    }

                    continue;
                }

                if (parts.length == 2
                        && "@COMPLETE".equals(parts[0])) {

                    try {
                        completed.add(
                                unescape(parts[1])
                        );
                    } catch (RuntimeException ignored) {
                    }

                    continue;
                }

                if (parts.length != 4) {
                    continue;
                }

                try {
                    String id =
                            unescape(parts[0]);

                    String savedStatus =
                            parts[1];

                    EndgameStudyProgress.Status status;

                    /*
                     * Short-lived builds used a fourth COMPLETED enum value.
                     * Migrate those records into the compatible three-state
                     * model plus the new completion set.
                     */
                    if ("COMPLETED".equals(savedStatus)) {
                        status =
                                EndgameStudyProgress.Status.ATTEMPTED;

                        completed.add(id);
                    } else {
                        status =
                                EndgameStudyProgress.Status.valueOf(
                                        savedStatus
                                );
                    }

                    int attempts =
                            Integer.parseInt(parts[2]);

                    int masteries =
                            Integer.parseInt(parts[3]);

                    loaded.put(
                            id,
                            new EndgameStudyProgress.PositionProgress(
                                    status,
                                    attempts,
                                    masteries
                            )
                    );

                    if (status
                            == EndgameStudyProgress.Status.MASTERED) {
                        completed.add(id);
                    }

                } catch (RuntimeException ignored) {
                    /*
                     * Ignore one malformed record without losing the rest of
                     * the saved progress.
                     */
                }
            }

            result.replaceAll(loaded);
            result.replaceCursors(cursors);
            result.replaceOrders(orders);
            result.replaceReviews(reviews);
            result.replaceCompleted(completed);

            return result;

        } catch (IOException ignored) {
            return result;
        }
    }


    public synchronized void save(
            EndgameStudyProgress progress
    ) throws IOException {

        if (progress == null) {
            throw new IllegalArgumentException(
                    "Progress cannot be null."
            );
        }

        Path parent = file.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        StringBuilder text =
                new StringBuilder();

        text.append(VERSION).append('\n');

        for (Map.Entry<String, EndgameStudyProgress.PositionProgress> entry
                : progress.snapshot().entrySet()) {

            EndgameStudyProgress.PositionProgress value =
                    entry.getValue();

            text.append(escape(entry.getKey()))
                    .append('\t')
                    .append(value.status())
                    .append('\t')
                    .append(value.attempts())
                    .append('\t')
                    .append(value.masteries())
                    .append('\n');
        }

        for (Map.Entry<String, Long> entry
                : progress.cursorSnapshot().entrySet()) {

            text.append("@CURSOR\t")
                    .append(escape(entry.getKey()))
                    .append('\t')
                    .append(entry.getValue())
                    .append('\n');
        }

        for (Map.Entry<String, EndgameStudyProgress.StudyOrder> entry
                : progress.orderSnapshot().entrySet()) {

            text.append("@ORDER\t")
                    .append(escape(entry.getKey()))
                    .append('\t')
                    .append(entry.getValue())
                    .append('\n');
        }

        for (Map.Entry<String, List<String>> entry
                : progress.reviewSnapshot().entrySet()) {

            for (String id : entry.getValue()) {
                text.append("@REVIEW\t")
                        .append(escape(entry.getKey()))
                        .append('\t')
                        .append(escape(id))
                        .append('\n');
            }
        }

        for (String id : progress.completedSnapshot()) {
            text.append("@COMPLETE\t")
                    .append(escape(id))
                    .append('\n');
        }

        Path temporaryFile =
                file.resolveSibling(
                        file.getFileName() + ".tmp"
                );

        Files.writeString(
                temporaryFile,
                text.toString(),
                StandardCharsets.UTF_8
        );

        try {
            Files.move(
                    temporaryFile,
                    file,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(
                    temporaryFile,
                    file,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }


    public synchronized void clear()
            throws IOException {

        Files.deleteIfExists(file);

        Files.deleteIfExists(
                file.resolveSibling(
                        file.getFileName() + ".tmp"
                )
        );
    }


    private static boolean isSupportedVersion(
            String version
    ) {
        return VERSION.equals(version)
                || "DOVETAIL_ENDGAME_PROGRESS_V3".equals(version)
                || "DOVETAIL_ENDGAME_PROGRESS_V2".equals(version)
                || "DOVETAIL_ENDGAME_PROGRESS_V1".equals(version);
    }


    private static Path defaultPath() {
        String operatingSystem =
                System.getProperty(
                        "os.name",
                        ""
                ).toLowerCase();

        if (operatingSystem.contains("win")) {
            String appData =
                    System.getenv("APPDATA");

            if (appData != null
                    && !appData.isBlank()) {

                return Path.of(
                        appData,
                        "Dovetail Engine",
                        "endgame-study-progress.tsv"
                );
            }
        }

        String home =
                System.getProperty(
                        "user.home",
                        "."
                );

        return Path.of(
                home,
                ".dovetail-engine",
                "endgame-study-progress.tsv"
        );
    }


    private static String escape(String text) {
        return text
                .replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\n", "\\n");
    }


    private static String unescape(String text) {
        StringBuilder result =
                new StringBuilder();

        boolean escaped = false;

        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);

            if (escaped) {
                switch (character) {
                    case 't' -> result.append('\t');
                    case 'n' -> result.append('\n');
                    case '\\' -> result.append('\\');
                    default -> result.append(character);
                }

                escaped = false;

            } else if (character == '\\') {
                escaped = true;

            } else {
                result.append(character);
            }
        }

        if (escaped) {
            result.append('\\');
        }

        return result.toString();
    }
}

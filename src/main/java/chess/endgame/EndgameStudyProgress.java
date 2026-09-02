package main.java.chess.endgame;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class EndgameStudyProgress {

    /*
     * Keep the original three-state enum.
     *
     * Other GUI classes already switch exhaustively over these values, so
     * completion is tracked independently instead of adding a fourth enum
     * constant and breaking those switches.
     */
    public enum Status {
        UNSEEN,
        ATTEMPTED,
        MASTERED
    }

    public enum StudyOrder {
        ORDERED,
        SHUFFLE
    }

    public record PositionProgress(
            Status status,
            int attempts,
            int masteries
    ) {
        public PositionProgress {
            if (status == null || attempts < 0 || masteries < 0) {
                throw new IllegalArgumentException("Invalid endgame progress.");
            }
        }
    }

    private final Map<String, PositionProgress> positions =
            new LinkedHashMap<>();

    private final Map<String, Long> cursors =
            new LinkedHashMap<>();

    private final Map<String, StudyOrder> orders =
            new LinkedHashMap<>();

    private final Map<String, List<String>> reviewQueues =
            new LinkedHashMap<>();

    /*
     * A study may be successfully completed without being mastered.
     * Keeping this separately lets the old Status enum remain compatible with
     * every existing switch in the application.
     */
    private final Set<String> completedPositions =
            new LinkedHashSet<>();


    // =========================================================
    // Position progress
    // =========================================================

    public synchronized PositionProgress get(String id) {
        PositionProgress progress = positions.get(id);

        return progress == null
                ? new PositionProgress(Status.UNSEEN, 0, 0)
                : progress;
    }


    public synchronized void recordAttempt(String id) {
        requireId(id);

        PositionProgress old = get(id);

        positions.put(
                id,
                new PositionProgress(
                        old.status() == Status.MASTERED
                                ? Status.MASTERED
                                : Status.ATTEMPTED,
                        old.attempts() + 1,
                        old.masteries()
                )
        );
    }


    /**
     * Records a successful finish even when the attempt was not clean enough
     * to count as mastery.
     */
    public synchronized void recordCompletion(String id) {
        requireId(id);

        PositionProgress old = get(id);

        /*
         * A completion should always have at least one recorded attempt. This
         * normally already exists because ChessWindow records the first move,
         * but keeping this guard makes the model correct on its own too.
         */
        int attempts = Math.max(1, old.attempts());

        positions.put(
                id,
                new PositionProgress(
                        old.status() == Status.MASTERED
                                ? Status.MASTERED
                                : Status.ATTEMPTED,
                        attempts,
                        old.masteries()
                )
        );

        completedPositions.add(id);
    }


    /**
     * Mastery necessarily implies completion.
     */
    public synchronized void recordMastery(String id) {
        requireId(id);

        PositionProgress old = get(id);

        positions.put(
                id,
                new PositionProgress(
                        Status.MASTERED,
                        Math.max(1, old.attempts()),
                        old.masteries() + 1
                )
        );

        completedPositions.add(id);
        removeFromReview(id);
    }


    public synchronized boolean isCompleted(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }

        return completedPositions.contains(id)
                || get(id).status() == Status.MASTERED;
    }


    // =========================================================
    // Counts
    // =========================================================

    /**
     * "Attempted" is the Trainer's In Progress count: positions that have
     * started but have not yet been completed or mastered.
     */
    public synchronized int attemptedCount() {
        return attemptedCountInternal(null);
    }


    public synchronized int attemptedCount(String family) {
        return attemptedCountInternal(family);
    }


    public synchronized int completedCount() {
        return completedCountInternal(null);
    }


    public synchronized int completedCount(String family) {
        return completedCountInternal(family);
    }


    public synchronized int masteredCount() {
        return countStatus(null, Status.MASTERED);
    }


    public synchronized int masteredCount(String family) {
        return countStatus(family, Status.MASTERED);
    }


    public synchronized int studiedCount(String family) {
        return attemptedCount(family)
                + completedCount(family);
    }


    private int attemptedCountInternal(String family) {
        int count = 0;
        String prefix = family == null ? null : family + "|";

        for (Map.Entry<String, PositionProgress> entry : positions.entrySet()) {
            if (prefix != null && !entry.getKey().startsWith(prefix)) {
                continue;
            }

            if (entry.getValue().status() == Status.ATTEMPTED
                    && !completedPositions.contains(entry.getKey())) {
                count++;
            }
        }

        return count;
    }


    private int completedCountInternal(String family) {
        int count = 0;
        String prefix = family == null ? null : family + "|";

        for (String id : completedPositions) {
            if (prefix == null || id.startsWith(prefix)) {
                count++;
            }
        }

        /*
         * Old progress files may contain mastered records without an explicit
         * completion marker. Count those as complete as well.
         */
        for (Map.Entry<String, PositionProgress> entry : positions.entrySet()) {
            if (prefix != null && !entry.getKey().startsWith(prefix)) {
                continue;
            }

            if (entry.getValue().status() == Status.MASTERED
                    && !completedPositions.contains(entry.getKey())) {
                count++;
            }
        }

        return count;
    }


    private int countStatus(
            String family,
            Status status
    ) {
        int count = 0;
        String prefix = family == null ? null : family + "|";

        for (Map.Entry<String, PositionProgress> entry : positions.entrySet()) {
            if ((prefix == null || entry.getKey().startsWith(prefix))
                    && entry.getValue().status() == status) {
                count++;
            }
        }

        return count;
    }


    // =========================================================
    // Curriculum cursor / order
    // =========================================================

    public synchronized long cursor(String family) {
        return cursors.getOrDefault(family, 0L);
    }


    public synchronized long advanceCursor(String family) {
        long next = cursor(family) + 1;
        cursors.put(family, next);
        return next;
    }


    public synchronized void setCursor(
            String family,
            long value
    ) {
        requireId(family);
        cursors.put(family, Math.max(0, value));
    }


    public synchronized StudyOrder studyOrder(String family) {
        return orders.getOrDefault(
                family,
                StudyOrder.ORDERED
        );
    }


    public synchronized void setStudyOrder(
            String family,
            StudyOrder order
    ) {
        requireId(family);

        orders.put(
                family,
                order == null
                        ? StudyOrder.ORDERED
                        : order
        );
    }


    // =========================================================
    // Review queue
    // =========================================================

    /**
     * Adds an unfinished or completed-but-not-mastered study to that family's
     * persistent review backlog.
     */
    public synchronized void enqueueReview(String id) {
        requireId(id);

        if (get(id).status() == Status.MASTERED) {
            return;
        }

        String family = familyOf(id);

        List<String> queue =
                reviewQueues.computeIfAbsent(
                        family,
                        ignored -> new ArrayList<>()
                );

        if (!queue.contains(id)) {
            queue.add(id);
        }
    }


    public synchronized String nextReviewId(
            String family,
            StudyOrder order,
            Random random,
            String excludeId
    ) {
        List<String> queue = reviewQueues.get(family);

        if (queue == null || queue.isEmpty()) {
            return null;
        }

        List<String> eligible = new ArrayList<>();

        for (String id : queue) {
            if (get(id).status() == Status.MASTERED) {
                continue;
            }

            if (excludeId != null && excludeId.equals(id)) {
                continue;
            }

            eligible.add(id);
        }

        if (eligible.isEmpty()) {
            return null;
        }

        if (order == StudyOrder.SHUFFLE) {
            Random chosenRandom = random == null ? new Random() : random;

            return eligible.get(
                    chosenRandom.nextInt(
                            eligible.size()
                    )
            );
        }

        return eligible.get(0);
    }


    public synchronized int reviewCount(String family) {
        List<String> queue = reviewQueues.get(family);

        if (queue == null) {
            return 0;
        }

        int count = 0;

        for (String id : queue) {
            if (get(id).status() != Status.MASTERED) {
                count++;
            }
        }

        return count;
    }


    public synchronized void removeFromReview(String id) {
        if (id == null || id.isBlank()) {
            return;
        }

        String family = familyOf(id);
        List<String> queue = reviewQueues.get(family);

        if (queue == null) {
            return;
        }

        queue.removeIf(id::equals);

        if (queue.isEmpty()) {
            reviewQueues.remove(family);
        }
    }


    // =========================================================
    // Reset / snapshots
    // =========================================================

    public synchronized void resetFamily(String family) {
        String prefix = family + "|";

        positions.entrySet().removeIf(
                entry -> entry.getKey().startsWith(prefix)
        );

        completedPositions.removeIf(
                id -> id.startsWith(prefix)
        );

        cursors.remove(family);
        orders.remove(family);
        reviewQueues.remove(family);
    }


    public synchronized Map<String, PositionProgress> snapshot() {
        return Collections.unmodifiableMap(
                new LinkedHashMap<>(positions)
        );
    }


    public synchronized Map<String, Long> cursorSnapshot() {
        return Collections.unmodifiableMap(
                new LinkedHashMap<>(cursors)
        );
    }


    public synchronized Map<String, StudyOrder> orderSnapshot() {
        return Collections.unmodifiableMap(
                new LinkedHashMap<>(orders)
        );
    }


    public synchronized Map<String, List<String>> reviewSnapshot() {
        Map<String, List<String>> copy = new LinkedHashMap<>();

        for (Map.Entry<String, List<String>> entry
                : reviewQueues.entrySet()) {

            copy.put(
                    entry.getKey(),
                    List.copyOf(entry.getValue())
            );
        }

        return Collections.unmodifiableMap(copy);
    }


    public synchronized Set<String> completedSnapshot() {
        return Collections.unmodifiableSet(
                new LinkedHashSet<>(completedPositions)
        );
    }


    public synchronized void replaceAll(
            Map<String, PositionProgress> replacement
    ) {
        positions.clear();

        if (replacement != null) {
            positions.putAll(replacement);
        }

        /* Mastered records from any older save are always complete. */
        for (Map.Entry<String, PositionProgress> entry : positions.entrySet()) {
            if (entry.getValue().status() == Status.MASTERED) {
                completedPositions.add(entry.getKey());
            }
        }
    }


    public synchronized void replaceCursors(
            Map<String, Long> replacement
    ) {
        cursors.clear();

        if (replacement != null) {
            cursors.putAll(replacement);
        }
    }


    public synchronized void replaceOrders(
            Map<String, StudyOrder> replacement
    ) {
        orders.clear();

        if (replacement != null) {
            orders.putAll(replacement);
        }
    }


    public synchronized void replaceReviews(
            Map<String, List<String>> replacement
    ) {
        reviewQueues.clear();

        if (replacement == null) {
            return;
        }

        for (Map.Entry<String, List<String>> entry
                : replacement.entrySet()) {

            reviewQueues.put(
                    entry.getKey(),
                    new ArrayList<>(entry.getValue())
            );
        }
    }


    public synchronized void replaceCompleted(
            Iterable<String> replacement
    ) {
        completedPositions.clear();

        if (replacement != null) {
            for (String id : replacement) {
                if (id != null && !id.isBlank()) {
                    completedPositions.add(id);
                }
            }
        }

        /* Mastery always implies completion, even for an older save. */
        for (Map.Entry<String, PositionProgress> entry : positions.entrySet()) {
            if (entry.getValue().status() == Status.MASTERED) {
                completedPositions.add(entry.getKey());
            }
        }
    }


    public synchronized void clear() {
        positions.clear();
        cursors.clear();
        orders.clear();
        reviewQueues.clear();
        completedPositions.clear();
    }


    // =========================================================
    // Helpers
    // =========================================================

    private static String familyOf(String id) {
        int split = id.indexOf('|');

        if (split <= 0) {
            throw new IllegalArgumentException(
                    "Study ID does not contain a family: " + id
            );
        }

        return id.substring(0, split);
    }


    private static void requireId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "ID cannot be blank."
            );
        }
    }
}

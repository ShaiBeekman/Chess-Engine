package main.java.chess.endgame;

import main.java.chess.model.FenCodec;
import java.util.*;

/** Original puzzle definitions and their explicit playback order, independent of attempts. */
public final class EndgameStudySequence {
    public record Puzzle(String family, String fen, EndgameSettings settings) {
        public Puzzle {
            if (family == null || family.isBlank() || settings == null)
                throw new IllegalArgumentException("Invalid saved endgame.");
            fen = FenCodec.toFen(FenCodec.parse(fen));
        }
        public String id() { return family + "|" + fen; }
    }

    public record Collection(List<Puzzle> originals, List<Integer> order) {
        public Collection {
            originals = List.copyOf(originals);
            order = List.copyOf(order);
            int size = originals.size();
            if (order.size() != size || new HashSet<>(order).size() != order.size()
                    || order.stream().anyMatch(i -> i < 0 || i >= size))
                throw new IllegalArgumentException("Invalid saved endgame order.");
        }
        public Puzzle at(int index) { return originals.get(order.get(index)); }
    }

    private final Map<String, Collection> collections = new LinkedHashMap<>();

    public synchronized int size(String family) {
        Collection c = collections.get(family);
        return c == null ? 0 : c.order().size();
    }

    public synchronized Puzzle at(String family, int index) {
        return index < 0 || index >= size(family) ? null : collections.get(family).at(index);
    }

    public synchronized void append(String family, Puzzle puzzle) {
        Collection old = collections.getOrDefault(family, new Collection(List.of(), List.of()));
        List<Puzzle> puzzles = new ArrayList<>(old.originals());
        List<Integer> order = new ArrayList<>(old.order());
        order.add(puzzles.size());
        puzzles.add(puzzle);
        collections.put(family, new Collection(puzzles, order));
    }

    /** Called only for an explicit Ordered / Shuffle selection. */
    public synchronized void reorder(String family, EndgameStudyProgress.StudyOrder mode, Random random) {
        Collection old = collections.get(family);
        if (old == null) return;
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < old.originals().size(); i++) order.add(i);
        if (mode == EndgameStudyProgress.StudyOrder.SHUFFLE) Collections.shuffle(order, random);
        collections.put(family, new Collection(old.originals(), order));
    }

    public synchronized Map<String, Collection> snapshot() { return new LinkedHashMap<>(collections); }
    public synchronized void restore(String family, Collection collection) { collections.put(family, collection); }

    /** Old saves contain FENs in insertion order, but never recorded unattempted roots or delivery order. */
    public static EndgameStudySequence migrate(EndgameStudyProgress progress) {
        EndgameStudySequence result = new EndgameStudySequence();
        for (String id : progress.snapshot().keySet()) {
            try {
                int separator = id.indexOf('|');
                String family = id.substring(0, separator);
                String fen = id.substring(separator + 1);
                int pieces = 0;
                for (char c : fen.substring(0, fen.indexOf(' ')).toCharArray())
                    if (Character.isLetter(c)) pieces++;
                Puzzle puzzle = new Puzzle(family, fen, EndgameSettings.fixed(pieces));
                result.append("Mixed", puzzle);
                result.append(family, puzzle);
            } catch (RuntimeException ignored) { /* Preserve other recoverable legacy entries. */ }
        }
        return result;
    }
}

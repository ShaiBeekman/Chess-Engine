package main.java.chess.endgame;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Atomic persistence of puzzle definitions and order. Progress resets never delete this file. */
public final class EndgameStudySequenceStore {
    private final Path file;
    public EndgameStudySequenceStore(Path file) { this.file = file.toAbsolutePath().normalize(); }
    public boolean exists() { return Files.exists(file); }

    public EndgameStudySequence load() throws IOException {
        EndgameStudySequence result = new EndgameStudySequence();
        if (!exists()) return result;
        Properties p = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { p.load(reader); }
        try {
            if (!"1".equals(p.getProperty("version"))) throw new IllegalArgumentException("Unknown sequence version");
            int count = Integer.parseInt(p.getProperty("collections"));
            if (count < 0) throw new IllegalArgumentException("Invalid collection count");
            for (int c = 0; c < count; c++) {
                String prefix = "collection." + c + ".";
                String family = Objects.requireNonNull(p.getProperty(prefix + "family"));
                int size = Integer.parseInt(p.getProperty(prefix + "size"));
                if (size < 0) throw new IllegalArgumentException("Invalid puzzle count");
                List<EndgameStudySequence.Puzzle> puzzles = new ArrayList<>();
                for (int i = 0; i < size; i++) {
                    String key = prefix + "puzzle." + i + ".";
                    puzzles.add(new EndgameStudySequence.Puzzle(p.getProperty(key + "family"),
                            p.getProperty(key + "fen"), new EndgameSettings(
                            Integer.parseInt(p.getProperty(key + "minimum")),
                            Integer.parseInt(p.getProperty(key + "maximum")))));
                }
                List<Integer> order = new ArrayList<>();
                String savedOrder = Objects.requireNonNull(p.getProperty(prefix + "order"));
                if (!savedOrder.isEmpty()) for (String index : savedOrder.split(",")) order.add(Integer.parseInt(index));
                result.restore(family, new EndgameStudySequence.Collection(puzzles, order));
            }
        } catch (RuntimeException exception) { throw new IOException("Invalid saved endgame sequence: " + file, exception); }
        return result;
    }

    public void save(EndgameStudySequence sequence) throws IOException {
        Properties p = new Properties();
        p.setProperty("version", "1");
        var collections = sequence.snapshot();
        p.setProperty("collections", Integer.toString(collections.size()));
        int c = 0;
        for (var entry : collections.entrySet()) {
            String prefix = "collection." + c++ + ".";
            p.setProperty(prefix + "family", entry.getKey());
            var collection = entry.getValue();
            p.setProperty(prefix + "size", Integer.toString(collection.originals().size()));
            p.setProperty(prefix + "order", String.join(",", collection.order().stream().map(Object::toString).toList()));
            for (int i = 0; i < collection.originals().size(); i++) {
                String key = prefix + "puzzle." + i + ".";
                var puzzle = collection.originals().get(i);
                p.setProperty(key + "family", puzzle.family());
                p.setProperty(key + "fen", puzzle.fen());
                p.setProperty(key + "minimum", Integer.toString(puzzle.settings().minimumPieces()));
                p.setProperty(key + "maximum", Integer.toString(puzzle.settings().maximumPieces()));
            }
        }
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            p.store(writer, "Endgame puzzle definitions and playback order; progress is stored separately");
        }
        try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException exception) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
    }
}

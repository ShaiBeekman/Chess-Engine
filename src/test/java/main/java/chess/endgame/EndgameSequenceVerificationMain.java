package main.java.chess.endgame;

import java.nio.file.*;
import java.util.*;

public final class EndgameSequenceVerificationMain {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("endgame-sequence-check-");
        var sequenceStore = new EndgameStudySequenceStore(dir.resolve("sequence.properties"));
        var progressStore = new EndgameStudyProgressStore(dir.resolve("progress.tsv"));
        var sequence = new EndgameStudySequence();
        var puzzles = List.of(
                new EndgameStudySequence.Puzzle("KQK", "7k/8/8/8/8/4K3/6Q1/8 w - - 0 1", EndgameSettings.fixed(3)),
                new EndgameStudySequence.Puzzle("KRK", "8/8/8/8/4k3/8/6r1/7K b - - 0 1", EndgameSettings.fixed(3)),
                new EndgameStudySequence.Puzzle("KPK", "8/4P3/3K4/8/8/8/8/7k w - - 0 1", EndgameSettings.fixed(3)));
        for (var p : puzzles) { sequence.append("Mixed", p); sequence.append(p.family(), p); }
        // Repeated roots are distinct sequence occurrences and must not be deduplicated.
        sequence.append("Mixed", puzzles.getFirst());
        sequenceStore.save(sequence);
        require(sequence.snapshot().equals(sequenceStore.load().snapshot()), "Complete sequence round trip");
        var progress = new EndgameStudyProgress();
        progress.recordAttempt(puzzles.get(1).id());
        progress.selectFamily("Mixed");
        progress.setSession("Mixed", new EndgameStudyProgress.Session(1, List.of(puzzles.get(1).fen()), false, true, true));
        progressStore.save(progress);
        require(Files.readAllLines(progressStore.file()).getFirst().equals("DOVETAIL_ENDGAME_PROGRESS_V4"), "Keep existing progress readers compatible");
        var loaded = progressStore.load();
        require(loaded.sessionSnapshot().equals(progress.sessionSnapshot()), "Session index, history and assistance flags round trip");
        sequence.reorder("Mixed", EndgameStudyProgress.StudyOrder.SHUFFLE, new Random(42));
        sequenceStore.save(sequence);
        var shuffled = sequenceStore.load().snapshot();
        byte[] originalBytes = Files.readAllBytes(dir.resolve("sequence.properties"));
        loaded.resetFamily("KRK");
        loaded.clear();
        progressStore.save(loaded);
        require(Arrays.equals(originalBytes, Files.readAllBytes(dir.resolve("sequence.properties"))), "Progress reset changed collection file");
        require(sequenceStore.load().snapshot().equals(shuffled), "Reset/restart changed shuffled order");
        sequence.reorder("Mixed", EndgameStudyProgress.StudyOrder.ORDERED, new Random());
        for (int i = 0; i < puzzles.size(); i++) require(sequence.at("Mixed", i).equals(puzzles.get(i)), "Original order lost");
        for (int v = 1; v <= 4; v++) {
            Files.writeString(progressStore.file(), "DOVETAIL_ENDGAME_PROGRESS_V" + v + "\n"
                    + puzzles.get(0).id() + "\tATTEMPTED\t2\t0\n"
                    + puzzles.get(1).id() + "\tMASTERED\t1\t1\n"
                    + "malformed record\n@CURSOR\tMixed\t8\n@ORDER\tMixed\tSHUFFLE\n");
            var legacy = progressStore.load();
            require(legacy.get(puzzles.get(0).id()).attempts() == 2 && legacy.isCompleted(puzzles.get(1).id()), "Legacy V" + v);
            var migrated = EndgameStudySequence.migrate(legacy);
            require(migrated.at("Mixed", 0).equals(puzzles.get(0)) && migrated.at("Mixed", 1).equals(puzzles.get(1)), "Legacy FEN recovery/order");
            progressStore.save(legacy);
            require(progressStore.load().snapshot().equals(legacy.snapshot()), "Legacy V" + v + " resave");
        }
        Files.writeString(dir.resolve("sequence.properties"), "version=999\n");
        boolean rejected = false;
        try { sequenceStore.load(); } catch (java.io.IOException expected) { rejected = true; }
        require(rejected, "Unsupported sequence must not silently regenerate");
        System.out.println("ENDGAME SEQUENCE, RESET, SESSION AND V1-V4 COMPATIBILITY PASSED");
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}

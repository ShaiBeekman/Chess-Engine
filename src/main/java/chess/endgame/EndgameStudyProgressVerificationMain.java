package main.java.chess.endgame;

import java.nio.file.Files;
import java.nio.file.Path;

public final class EndgameStudyProgressVerificationMain {

    private EndgameStudyProgressVerificationMain() {
    }

    public static void main(String[] args) throws Exception {
        Path directory =
                Files.createTempDirectory("dovetail-endgame-progress-");

        Path file =
                directory.resolve("progress.tsv");

        EndgameStudyProgressStore store =
                new EndgameStudyProgressStore(file);

        EndgameStudyProgress progress =
                new EndgameStudyProgress();

        progress.recordAttempt("KQK|example-a");
        progress.recordAttempt("KQK|example-a");
        progress.recordAttempt("KRK|example-b");
        progress.recordMastery("KRK|example-b");

        store.save(progress);

        EndgameStudyProgress loaded =
                store.load();

        require(
                loaded.get("KQK|example-a").attempts() == 2,
                "Attempt count did not persist."
        );

        require(
                loaded.get("KRK|example-b").status()
                        == EndgameStudyProgress.Status.MASTERED,
                "Mastery did not persist."
        );

        require(
                loaded.masteredCount() == 1,
                "Mastered total is wrong."
        );

        store.clear();

        require(
                !Files.exists(file),
                "Progress file was not cleared."
        );

        require(
                store.load().snapshot().isEmpty(),
                "Cleared progress unexpectedly reloaded data."
        );

        System.out.println(
                "M68E1 ENDGAME PROGRESS PERSISTENCE PASSED"
        );
        System.out.println(
                "Temporary verification file: " + file
        );
    }

    private static void require(
            boolean condition,
            String message
    ) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}

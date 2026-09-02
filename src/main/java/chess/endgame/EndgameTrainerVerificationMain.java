package main.java.chess.endgame;

import main.java.chess.model.Color;
import main.java.chess.model.PieceType;
import main.java.chess.model.Position;
import main.java.chess.model.Square;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * End-to-end regression gate for the user-facing Endgame Trainer.
 *
 * This deliberately tests the same pure scheduling rules used by ChessWindow,
 * the persistent progress model/store, all three exact three-piece families,
 * all 30 canonical four-piece families, exact WIN probing, and optimal-move
 * availability.
 *
 * Optional argument:
 *     quick   -> 1 generated root per four-piece family
 *     N       -> N generated roots per four-piece family
 *
 * Default: 2 roots per four-piece family.
 */
public final class EndgameTrainerVerificationMain {

    private static final int DEFAULT_FOUR_PIECE_SAMPLES = 2;
    private static final int MIXED_RANDOM_SAMPLES = 10_000;

    private EndgameTrainerVerificationMain() {
    }

    public static void main(String[] args) throws Exception {
        int fourPieceSamples = parseFourPieceSamples(args);
        long started = System.nanoTime();

        System.out.println("Endgame Trainer regression gate");
        System.out.println("===============================");
        System.out.println("Four-piece roots per family: " + fourPieceSamples);

        verifySchedulingRules();
        verifyProgressSemantics();
        verifyProgressPersistence();

        FourPieceStudyPositionGenerator fourPieceGenerator =
                new FourPieceStudyPositionGenerator();

        List<FourPieceMaterialClass> catalog =
                fourPieceGenerator.catalog();

        verifyFourPieceCatalog(catalog);
        verifyCurriculumMetadata(catalog);
        verifyThreePieceFamilies();
        verifyFourPieceFamilies(
                fourPieceGenerator,
                catalog,
                fourPieceSamples
        );

        double elapsedSeconds =
                (System.nanoTime() - started) / 1_000_000_000.0;

        System.out.println();
        System.out.println("=============================================");
        System.out.println("ENDGAME TRAINER REGRESSION GATE PASSED");
        System.out.println("=============================================");
        System.out.printf("Elapsed: %.3f sec%n", elapsedSeconds);
    }

    private static void verifySchedulingRules() {
        section("Trainer scheduling rules");

        int fresh = 0;

        for (int delivered = 0;
             delivered < EndgameTrainerRules.FRESH_STUDIES_BEFORE_REVIEW;
             delivered++) {

            require(
                    !EndgameTrainerRules.reviewDue(1, fresh),
                    "Review became due before four fresh studies."
            );

            fresh = EndgameTrainerRules.freshCountAfterDelivery(
                    fresh,
                    false
            );
        }

        require(
                fresh == 4,
                "Fresh-study pacing counter did not stop at four."
        );

        require(
                EndgameTrainerRules.reviewDue(1, fresh),
                "Review was not due after exactly four fresh studies."
        );

        require(
                !EndgameTrainerRules.reviewDue(0, fresh),
                "Review became due with an empty review stack."
        );

        require(
                EndgameTrainerRules.freshCountAfterDelivery(
                        fresh,
                        true
                ) == 0,
                "Review delivery did not reset the pacing window."
        );

        Random mixedRandom =
                new Random(0x4D49584544524E44L);

        int threePiece = 0;
        int fourPiece = 0;

        for (int sample = 0;
             sample < MIXED_RANDOM_SAMPLES;
             sample++) {

            int pieceCount =
                    EndgameTrainerRules.chooseMixedPieceCount(
                            mixedRandom,
                            false,
                            false,
                            false
                    );

            if (pieceCount == 3) {
                threePiece++;
            } else if (pieceCount == 4) {
                fourPiece++;
            } else {
                fail("Mixed returned illegal piece count: " + pieceCount);
            }
        }

        require(
                threePiece > MIXED_RANDOM_SAMPLES / 3
                        && fourPiece > MIXED_RANDOM_SAMPLES / 3,
                "Mixed no longer behaves randomly across 3- and 4-piece studies."
        );

        for (int i = 0; i < 32; i++) {
            require(
                    EndgameTrainerRules.chooseMixedPieceCount(
                            mixedRandom,
                            true,
                            true,
                            false
                    ) == 3,
                    "Due three-piece-only review did not select 3 pieces."
            );

            require(
                    EndgameTrainerRules.chooseMixedPieceCount(
                            mixedRandom,
                            true,
                            false,
                            true
                    ) == 4,
                    "Due four-piece-only review did not select 4 pieces."
            );
        }

        boolean bothSawThree = false;
        boolean bothSawFour = false;

        for (int i = 0; i < 128; i++) {
            int pieceCount =
                    EndgameTrainerRules.chooseMixedPieceCount(
                            mixedRandom,
                            true,
                            true,
                            true
                    );

            bothSawThree |= pieceCount == 3;
            bothSawFour |= pieceCount == 4;
        }

        require(
                bothSawThree && bothSawFour,
                "Mixed review routing is deterministic when both buckets are eligible."
        );

        EndgameStudyProgress aggregateProgress =
                new EndgameStudyProgress();

        String kqk = "KQK|aggregate-a";
        String krk = "KRK|aggregate-b";
        String four = "KQBK|aggregate-c";

        aggregateProgress.recordAttempt(kqk);
        aggregateProgress.enqueueReview(kqk);
        aggregateProgress.recordAttempt(krk);
        aggregateProgress.enqueueReview(krk);
        aggregateProgress.recordAttempt(four);
        aggregateProgress.enqueueReview(four);

        List<String> aggregateFamilies =
                List.of(
                        "Mixed",
                        "KQK",
                        "KRK",
                        "KPK",
                        "KQBK"
                );

        require(
                EndgameTrainerRules.aggregateReviewCount(
                        aggregateProgress,
                        aggregateFamilies
                ) == 3,
                "Mixed review-stack aggregation is incorrect."
        );

        System.out.println("  review pacing: PASSED");
        System.out.println("  Mixed 3-piece draws: " + threePiece);
        System.out.println("  Mixed 4-piece draws: " + fourPiece);
        System.out.println("  Mixed randomness / review routing: PASSED");
        System.out.println("  Mixed review aggregation: PASSED");
    }

    private static void verifyProgressSemantics() {
        section("Progress / completion / mastery semantics");

        EndgameStudyProgress progress =
                new EndgameStudyProgress();

        String dirty = "KQK|dirty-solve";

        require(
                progress.get(dirty).status()
                        == EndgameStudyProgress.Status.UNSEEN,
                "New study did not begin UNSEEN."
        );

        progress.recordAttempt(dirty);
        progress.enqueueReview(dirty);

        require(
                progress.attemptedCount("KQK") == 1,
                "Attempt was not counted as In Progress."
        );
        require(
                progress.reviewCount("KQK") == 1,
                "Deferred attempt did not enter review stack."
        );

        progress.recordCompletion(dirty);

        require(progress.isCompleted(dirty), "Dirty solve was not completed.");
        require(
                progress.completedCount("KQK") == 1,
                "Completion count did not increase."
        );
        require(
                progress.attemptedCount("KQK") == 0,
                "Completed study remained in In Progress."
        );
        require(
                progress.get(dirty).status()
                        == EndgameStudyProgress.Status.ATTEMPTED,
                "Completed-but-not-mastered study should remain ATTEMPTED internally."
        );
        require(
                progress.reviewCount("KQK") == 1,
                "Completed-but-not-mastered study disappeared from review."
        );

        progress.recordMastery(dirty);

        require(
                progress.get(dirty).status()
                        == EndgameStudyProgress.Status.MASTERED,
                "Clean solve did not become MASTERED."
        );
        require(
                progress.masteredCount("KQK") == 1,
                "Mastery count did not increase."
        );
        require(
                progress.completedCount("KQK") == 1,
                "Mastery stopped counting as completion."
        );
        require(
                progress.reviewCount("KQK") == 0,
                "Mastery did not remove the study from review."
        );

        String retained = "KRK|retain-me";
        progress.recordAttempt(retained);
        progress.enqueueReview(retained);
        progress.setCursor("KRK", 17);
        progress.setStudyOrder(
                "KRK",
                EndgameStudyProgress.StudyOrder.SHUFFLE
        );

        progress.resetFamily("KQK");

        require(
                progress.masteredCount("KQK") == 0
                        && progress.completedCount("KQK") == 0,
                "Family reset did not clear the requested family."
        );
        require(
                progress.attemptedCount("KRK") == 1
                        && progress.reviewCount("KRK") == 1
                        && progress.cursor("KRK") == 17,
                "Family reset damaged another family's progress."
        );

        System.out.println("  completion separate from mastery: PASSED");
        System.out.println("  mastery removes review: PASSED");
        System.out.println("  family-isolated reset: PASSED");
    }

    private static void verifyProgressPersistence() throws IOException {
        section("Progress V4 persistence round-trip");

        Path directory =
                Files.createTempDirectory(
                        "dovetail-endgame-verification-"
                );

        Path file = directory.resolve("progress.tsv");
        EndgameStudyProgressStore store =
                new EndgameStudyProgressStore(file);

        try {
            EndgameStudyProgress original =
                    new EndgameStudyProgress();

            String completed = "KQK|persist-complete";
            String mastered = "KRK|persist-mastered";
            String review = "KPK|persist-review";

            original.recordAttempt(completed);
            original.recordCompletion(completed);
            original.enqueueReview(completed);

            original.recordAttempt(mastered);
            original.recordMastery(mastered);

            original.recordAttempt(review);
            original.enqueueReview(review);

            original.setCursor("KQK", 27);
            original.setCursor("Mixed", 91);
            original.setStudyOrder(
                    "KQK",
                    EndgameStudyProgress.StudyOrder.SHUFFLE
            );
            original.setStudyOrder(
                    "Mixed",
                    EndgameStudyProgress.StudyOrder.ORDERED
            );

            store.save(original);

            EndgameStudyProgress loaded =
                    store.load();

            require(
                    original.snapshot().equals(loaded.snapshot()),
                    "Position-progress records changed after save/load."
            );
            require(
                    original.completedSnapshot().equals(
                            loaded.completedSnapshot()
                    ),
                    "Completion markers changed after save/load."
            );
            require(
                    original.cursorSnapshot().equals(
                            loaded.cursorSnapshot()
                    ),
                    "Curriculum cursors changed after save/load."
            );
            require(
                    original.orderSnapshot().equals(
                            loaded.orderSnapshot()
                    ),
                    "Study-order settings changed after save/load."
            );
            require(
                    original.reviewSnapshot().equals(
                            loaded.reviewSnapshot()
                    ),
                    "Review queues changed after save/load."
            );

            System.out.println("  attempts / completion / mastery: PASSED");
            System.out.println("  cursors / order / review queues: PASSED");

        } finally {
            store.clear();
            Files.deleteIfExists(directory);
        }
    }

    private static void verifyFourPieceCatalog(
            List<FourPieceMaterialClass> catalog
    ) {
        section("Four-piece curriculum catalog");

        require(catalog != null, "Four-piece catalog is null.");
        require(catalog.size() == 30, "Expected exactly 30 four-piece families.");

        int sameSide = 0;
        int split = 0;
        int tierZero = 0;
        int tierOne = 0;
        int tierTwo = 0;
        Set<String> names = new HashSet<>();

        for (FourPieceMaterialClass material : catalog) {
            require(
                    names.add(material.assetStem()),
                    "Duplicate four-piece family: " + material.assetStem()
            );

            if (material.distribution()
                    == FourPieceMaterialClass.Distribution.SAME_SIDE) {
                sameSide++;
            } else {
                split++;
            }

            switch (material.pawnCount()) {
                case 0 -> tierZero++;
                case 1 -> tierOne++;
                case 2 -> tierTwo++;
                default -> fail(
                        "Illegal pawn tier for " + material.assetStem()
                );
            }
        }

        require(
                sameSide == 15 && split == 15,
                "Expected 15 SAME_SIDE and 15 SPLIT families."
        );
        require(
                tierZero == 20 && tierOne == 8 && tierTwo == 2,
                "Expected Tier counts 20 / 8 / 2."
        );

        System.out.println("  canonical families: 30");
        System.out.println("  SAME_SIDE / SPLIT: 15 / 15");
        System.out.println("  Tier 0 / 1 / 2: 20 / 8 / 2");
        System.out.println("  catalog structure: PASSED");
    }

    private static void verifyCurriculumMetadata(
            List<FourPieceMaterialClass> catalog
    ) throws IOException {
        section("Curriculum denominators");

        EndgameCurriculumMetadata.clearCache();

        long fourPieceTotal = 0;

        for (FourPieceMaterialClass material : catalog) {
            long wins =
                    EndgameCurriculumMetadata.fourPieceWinTotal(
                            material
                    );

            require(
                    wins > 0,
                    "No winning curriculum states for " + material.assetStem()
            );

            fourPieceTotal = Math.addExact(fourPieceTotal, wins);
        }

        long expectedMixed =
                EndgameCurriculumMetadata.KQK_WIN_TOTAL
                        + EndgameCurriculumMetadata.KRK_WIN_TOTAL
                        + EndgameCurriculumMetadata.KPK_WIN_TOTAL
                        + fourPieceTotal;

        long actualMixed =
                EndgameCurriculumMetadata.mixedWinTotal(catalog);

        require(
                actualMixed == expectedMixed,
                "Mixed denominator does not equal the sum of all family denominators."
        );
        require(
                actualMixed > EndgameCurriculumMetadata.KQK_WIN_TOTAL,
                "Mixed denominator is implausibly smaller than KQK alone."
        );

        System.out.printf(
                "  KQK / KRK / KPK: %,d / %,d / %,d%n",
                EndgameCurriculumMetadata.KQK_WIN_TOTAL,
                EndgameCurriculumMetadata.KRK_WIN_TOTAL,
                EndgameCurriculumMetadata.KPK_WIN_TOTAL
        );
        System.out.printf("  four-piece WIN total: %,d%n", fourPieceTotal);
        System.out.printf("  Mixed WIN total: %,d%n", actualMixed);
        System.out.println("  exact denominator accounting: PASSED");
    }

    private static void verifyThreePieceFamilies() {
        section("Three-piece exact study generation");

        ThreePieceTablebaseService service =
                new ThreePieceTablebaseService();

        PieceType[] materialTypes = {
                PieceType.QUEEN,
                PieceType.ROOK,
                PieceType.PAWN
        };

        long[] expectedWins = {
                EndgameCurriculumMetadata.KQK_WIN_TOTAL,
                EndgameCurriculumMetadata.KRK_WIN_TOTAL,
                EndgameCurriculumMetadata.KPK_WIN_TOTAL
        };

        try {
            for (int materialIndex = 0;
                 materialIndex < materialTypes.length;
                 materialIndex++) {

                PieceType type = materialTypes[materialIndex];

                for (Color color : Color.values()) {
                    long started = System.nanoTime();

                    ThreePieceTablebase tablebase =
                            service.get(type, color);

                    require(
                            tablebase != null && tablebase.isBuilt(),
                            type + " / " + color + " tablebase did not load."
                    );
                    require(
                            tablebase.getWinCount() == expectedWins[materialIndex],
                            type + " / " + color + " WIN count changed: "
                                    + tablebase.getWinCount()
                    );

                    Random random =
                            new Random(
                                    0x335049454345L
                                            ^ type.ordinal() * 131L
                                            ^ color.ordinal() * 977L
                            );

                    Position position =
                            tablebase.randomWinningPosition(random);

                    require(
                            tablebase.probe(position).outcome()
                                    == ThreePieceTablebase.Outcome.WIN,
                            type + " / " + color + " sampler returned non-WIN."
                    );
                    require(
                            !tablebase.bestMoves(position).isEmpty(),
                            type + " / " + color + " WIN has no optimal move."
                    );

                    ExactEndgameTablebase exact =
                            ExactEndgameTablebase.of(tablebase);

                    require(
                            exact != null
                                    && exact.probe(position).outcome()
                                    == ExactEndgameTablebase.Outcome.WIN,
                            type + " / " + color + " exact facade rejected WIN."
                    );
                    require(
                            !exact.bestMoves(position).isEmpty(),
                            type + " / " + color
                                    + " exact facade found no optimal move."
                    );

                    double elapsed =
                            (System.nanoTime() - started) / 1_000_000_000.0;

                    System.out.printf(
                            "  %-5s %-5s  WIN %,d  %.3f sec  PASSED%n",
                            familyName(type),
                            color,
                            tablebase.getWinCount(),
                            elapsed
                    );
                }
            }
        } finally {
            service.clear();
        }
    }

    private static void verifyFourPieceFamilies(
            FourPieceStudyPositionGenerator generator,
            List<FourPieceMaterialClass> catalog,
            int samplesPerFamily
    ) {
        section("All 30 four-piece exact study families");

        ExactEndgameTablebase exact =
                ExactEndgameTablebase.tierZeroCatalog();

        int familyNumber = 0;
        int generatedRoots = 0;

        for (FourPieceMaterialClass material : catalog) {
            familyNumber++;
            long started = System.nanoTime();

            try {
                for (int sample = 0;
                     sample < samplesPerFamily;
                     sample++) {

                    Random random =
                            new Random(
                                    0x34454E4447414D45L
                                            ^ ((long) material.assetStem().hashCode() << 17)
                                            ^ sample * 0x9E3779B97F4A7C15L
                            );

                    FourPieceStudyPositionGenerator.StudyRoot root =
                            generator.generateWinningStudy(
                                    material,
                                    exact,
                                    random
                            );

                    require(
                            root.material().equals(material),
                            "Generated wrong family for " + material.assetStem()
                    );
                    require(
                            countPieces(root.position()) == 4,
                            material.assetStem()
                                    + " generated a non-four-piece root."
                    );

                    ExactEndgameTablebase.Probe probe =
                            exact.probe(root.position());

                    require(
                            probe.outcome()
                                    == ExactEndgameTablebase.Outcome.WIN,
                            material.assetStem()
                                    + " generated non-WIN root: "
                                    + probe.outcome()
                    );
                    require(
                            !exact.bestMoves(root.position()).isEmpty(),
                            material.assetStem()
                                    + " exact WIN has no optimal move."
                    );

                    generatedRoots++;
                }

                double elapsed =
                        (System.nanoTime() - started) / 1_000_000_000.0;

                System.out.printf(
                        "  [%2d / 30] Tier %d  %-8s  %d root%s  %.3f sec  PASSED%n",
                        familyNumber,
                        material.buildTier(),
                        material.assetStem(),
                        samplesPerFamily,
                        samplesPerFamily == 1 ? "" : "s",
                        elapsed
                );

            } finally {
                /*
                 * Do not retain all 30 large exact arrays at once. The next
                 * family reloads only the assets it needs.
                 */
                exact.clearRuntimeCaches();
            }
        }

        require(
                familyNumber == 30,
                "Four-piece verification did not visit all 30 families."
        );
        require(
                generatedRoots == 30 * samplesPerFamily,
                "Four-piece repeated Next Position simulation lost a root."
        );

        System.out.println();
        System.out.println(
                "  repeated exact roots generated: " + generatedRoots
        );
        System.out.println("  all families support exact Next Position: PASSED");
    }

    private static int countPieces(Position position) {
        if (position == null || position.getBoard() == null) {
            return 0;
        }

        int pieces = 0;

        for (int rank = 0; rank < 8; rank++) {
            for (int file = 0; file < 8; file++) {
                if (position.getBoard().getPiece(
                        new Square(file, rank)
                ) != null) {
                    pieces++;
                }
            }
        }

        return pieces;
    }

    private static String familyName(PieceType type) {
        return switch (type) {
            case QUEEN -> "KQK";
            case ROOK -> "KRK";
            case PAWN -> "KPK";
            default -> throw new IllegalArgumentException(
                    "Unsupported three-piece type: " + type
            );
        };
    }

    private static int parseFourPieceSamples(String[] args) {
        if (args == null || args.length == 0) {
            return DEFAULT_FOUR_PIECE_SAMPLES;
        }

        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Usage: EndgameTrainerVerificationMain [quick|samples-per-family]"
            );
        }

        if ("quick".equalsIgnoreCase(args[0])) {
            return 1;
        }

        int samples;

        try {
            samples = Integer.parseInt(args[0]);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Four-piece sample count must be a positive integer or 'quick'.",
                    exception
            );
        }

        if (samples < 1) {
            throw new IllegalArgumentException(
                    "Four-piece sample count must be positive."
            );
        }

        return samples;
    }

    private static void section(String title) {
        System.out.println();
        System.out.println(title);
        System.out.println("-".repeat(title.length()));
    }

    private static void require(
            boolean condition,
            String message
    ) {
        if (!condition) {
            fail(message);
        }
    }

    private static void fail(String message) {
        throw new IllegalStateException(message);
    }
}

package main.java.chess.endgame;

import main.java.chess.model.PieceType;
import main.java.chess.model.Position;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;


/**
 * M63D — exact four-piece Endgame Study root generator.
 *
 * The tablebase catalog was already complete; this class closes the remaining
 * GUI-generation gap by sampling from all 30 canonical four-piece material
 * families rather than the older KQRK / KQPK-only study path.
 *
 * It does not solve anything. It selects roots from the already-persisted exact
 * WIN sets and lets ExactEndgameTablebase reconstruct and verify the position.
 */
public final class FourPieceStudyPositionGenerator {

    public static final String BUILD_ID =
            "M63D-FOUR-PIECE-STUDY-GENERATOR-V1";

    private static final List<FourPieceMaterialClass> CATALOG =
            FourPieceMaterialClass.allCanonical();

    private static final List<FourPieceMaterialClass> SAME_SIDE =
            filter(
                    FourPieceMaterialClass.Distribution.SAME_SIDE
            );

    private static final List<FourPieceMaterialClass> SPLIT =
            filter(
                    FourPieceMaterialClass.Distribution.SPLIT
            );


    public FourPieceStudyPositionGenerator() {
    }


    /**
     * Pick SAME_SIDE versus SPLIT with equal probability, then pick uniformly
     * inside that 15-family half of the canonical catalog.
     *
     * This makes 2-v-2 studies a first-class generation path instead of an
     * accidental rarity.
     */
    public FourPieceMaterialClass randomMaterial(
            Random random
    ) {

        requireRandom(
                random
        );


        List<FourPieceMaterialClass> source =
                random.nextBoolean()
                        ? SAME_SIDE
                        : SPLIT;


        return source.get(
                random.nextInt(
                        source.size()
                )
        );
    }


    /**
     * Generate a proven winning study root for one exact material family.
     *
     * The exact runtime selects directly from the persisted WIN set for the
     * requested family, then may color-reverse the presentation when that
     * symmetry is valid. This avoids rejection-sampling failures in families
     * where winning states are exceptionally rare.
     */
    public StudyRoot generateWinningStudy(
            FourPieceMaterialClass material,
            ExactEndgameTablebase tablebase,
            Random random
    ) {

        if (material == null) {
            throw new IllegalArgumentException(
                    "Four-piece study material cannot be null."
            );
        }

        if (tablebase == null) {
            throw new IllegalArgumentException(
                    "Exact endgame tablebase cannot be null."
            );
        }

        requireRandom(
                random
        );


        /*
         * Select from the exact solved WIN set itself rather than generating
         * arbitrary board geometries and hoping to hit a win.  Several exact
         * four-piece families are overwhelmingly draws (some have only dozens
         * of WIN states among tens of millions of legal states), so bounded
         * rejection sampling can legitimately miss every winning root.
         */
        Position candidate =
                tablebase.randomWinningPosition(
                        material,
                        random
                );


        ExactEndgameTablebase.Probe probe =
                tablebase.probe(
                        candidate
                );


        if (probe.outcome()
                != ExactEndgameTablebase.Outcome.WIN) {

            throw new IllegalStateException(
                    "Exact WIN sampler returned a non-WIN root for "
                            + material.assetStem()
                            + ": "
                            + probe.outcome()
            );
        }


        if (tablebase.bestMoves(
                candidate
        ).isEmpty()) {

            throw new IllegalStateException(
                    "Exact WIN sampler returned a terminal root for "
                            + material.assetStem()
                            + "."
            );
        }


        return new StudyRoot(
                material,
                candidate,
                1
        );
    }


    /**
     * Convenience path used by the GUI.
     *
     * Choose a material first. If an asset is unavailable or another runtime
     * problem prevents that family from producing a root, try another canonical
     * family rather than failing the Endgame Study dialog immediately.
     */
    public StudyRoot generateRandomWinningStudy(
            ExactEndgameTablebase tablebase,
            Random random
    ) {

        if (tablebase == null) {
            throw new IllegalArgumentException(
                    "Exact endgame tablebase cannot be null."
            );
        }

        requireRandom(
                random
        );


        List<FourPieceMaterialClass> remaining =
                new ArrayList<>(
                        CATALOG
                );


        /*
         * Preserve the 50/50 SAME_SIDE/SPLIT choice for the first material.
         * Subsequent retries are shuffled fallbacks only.
         */
        FourPieceMaterialClass first =
                randomMaterial(
                        random
                );


        remaining.remove(
                first
        );

        shuffle(
                remaining,
                random
        );

        remaining.add(
                0,
                first
        );


        RuntimeException lastFailure =
                null;


        for (FourPieceMaterialClass material :
                remaining) {

            try {

                return generateWinningStudy(
                        material,
                        tablebase,
                        random
                );

            } catch (RuntimeException exception) {

                lastFailure =
                        exception;
            }
        }


        throw new IllegalStateException(
                "Unable to generate a winning study from the complete "
                        + "30-family four-piece catalog.",
                lastFailure
        );
    }


    public List<FourPieceMaterialClass> catalog() {

        return CATALOG;
    }


    private static List<FourPieceMaterialClass> filter(
            FourPieceMaterialClass.Distribution distribution
    ) {

        List<FourPieceMaterialClass> result =
                new ArrayList<>(
                        15
                );


        for (FourPieceMaterialClass material :
                CATALOG) {

            if (material.distribution()
                    == distribution) {

                result.add(
                        material
                );
            }
        }


        if (result.size()
                != 15) {

            throw new IllegalStateException(
                    "Expected exactly 15 "
                            + distribution
                            + " four-piece material families."
            );
        }


        return List.copyOf(
                result
        );
    }


    private static void shuffle(
            List<FourPieceMaterialClass> list,
            Random random
    ) {

        for (int i = list.size() - 1;
             i > 0;
             i--) {

            int j =
                    random.nextInt(
                            i + 1
                    );


            FourPieceMaterialClass temporary =
                    list.get(
                            i
                    );

            list.set(
                    i,
                    list.get(
                            j
                    )
            );

            list.set(
                    j,
                    temporary
            );
        }
    }


    private static void requireRandom(
            Random random
    ) {

        if (random == null) {

            throw new IllegalArgumentException(
                    "Random cannot be null."
            );
        }
    }


    public record StudyRoot(
            FourPieceMaterialClass material,
            Position position,
            int attempts
    ) {

        public StudyRoot {

            if (material == null
                    || position == null
                    || attempts < 1) {

                throw new IllegalArgumentException(
                        "Invalid four-piece study root."
                );
            }
        }


        public String materialName() {

            return material.assetStem();
        }


        public boolean split() {

            return material.distribution()
                    == FourPieceMaterialClass.Distribution.SPLIT;
        }
    }
}

package main.java.chess.endgame;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;


/**
 * Milestone 20:
 *
 * Automated, restartable builder for the entire four-piece Tier-0
 * (pawnless) material catalog.
 *
 * Behavior:
 *
 *  - Enumerates every canonical four-piece material class.
 *  - Keeps only buildTier() == 0.
 *  - Uses one canonical orientation per material.
 *  - If an asset already exists:
 *        load it
 *        verify its metadata/checksum
 *        skip rebuilding it
 *
 *  - If an asset is missing:
 *        solve it
 *        save it
 *        reload it
 *        verify exact WDL/DTM persistence
 *        continue automatically
 *
 * This program is intentionally sequential.
 *
 * A single four-piece retrograde build already occupies large primitive
 * arrays. Sequential generation keeps peak memory predictable while still
 * eliminating the need to manually launch one class per material.
 */
public final class FourPieceTierZeroCatalogBuildMain {

    private static final Path DEFAULT_DIRECTORY =
            Path.of(
                    "tablebases",
                    "four-piece"
            );


    private FourPieceTierZeroCatalogBuildMain() {

    }


    public static void main(
            String[] args
    ) throws IOException {

        Path directory =
                args.length > 0
                        ? Path.of(args[0])
                        : DEFAULT_DIRECTORY;


        directory =
                directory.toAbsolutePath()
                        .normalize();


        Files.createDirectories(
                directory
        );


        List<FourPieceMaterialClass> tierZero =
                tierZeroMaterials();


        System.out.println(
                "Four-piece Tier-0 catalog builder"
        );

        System.out.println(
                "================================"
        );

        System.out.println(
                "Directory: "
                        + directory
        );

        System.out.println(
                "Tier-0 material classes: "
                        + tierZero.size()
        );

        System.out.println(
                "Mode: restartable sequential build"
        );


        long catalogStart =
                System.nanoTime();


        int alreadyPresent =
                0;

        int built =
                0;

        int failed =
                0;


        long totalCompressedBytes =
                0L;


        for (int index = 0;
             index < tierZero.size();
             index++) {

            FourPieceMaterialClass material =
                    tierZero.get(index);


            Path path =
                    assetPath(
                            directory,
                            material
                    );


            System.out.println();

            System.out.println(
                    "============================================================"
            );

            System.out.println(
                    "["
                            + (index + 1)
                            + " / "
                            + tierZero.size()
                            + "] "
                            + material.displayName()
            );

            System.out.println(
                    "Distribution: "
                            + material.distribution()
            );

            System.out.println(
                    "Asset: "
                            + path.getFileName()
            );

            System.out.println(
                    "============================================================"
            );


            try {

                if (Files.exists(path)) {

                    ExistingAssetResult existing =
                            validateExistingAsset(
                                    material,
                                    path
                            );


                    alreadyPresent++;

                    totalCompressedBytes +=
                            existing.fileBytes();


                    System.out.println(
                            "STATUS: VALID ASSET EXISTS — SKIPPED"
                    );

                    System.out.println(
                            "Digest: "
                                    + existing.digest()
                    );

                    continue;
                }


                BuildAssetResult created =
                        buildAndPersist(
                                material,
                                path
                        );


                built++;

                totalCompressedBytes +=
                        created.fileBytes();


                System.out.println();

                System.out.println(
                        "STATUS: BUILT AND VERIFIED"
                );

                System.out.println(
                        "Digest: "
                                + created.digest()
                );


                /*
                 * Encourage reclamation of the enormous primitive arrays
                 * before starting the next material.
                 *
                 * System.gc() is only a request, but the strong references
                 * from buildAndPersist() are gone by this point.
                 */
                System.gc();


            } catch (Throwable throwable) {

                failed++;


                System.out.println();

                System.out.println(
                        "STATUS: FAILED"
                );

                System.out.println(
                        throwable.getClass()
                                .getSimpleName()
                                + ": "
                                + throwable.getMessage()
                );


                System.out.println();

                System.out.println(
                        "Catalog build stopped."
                );

                System.out.println(
                        "The successfully written assets remain on disk."
                );

                System.out.println(
                        "Run this program again after fixing the problem;"
                );

                System.out.println(
                        "already verified assets will be skipped."
                );


                printCatalogSummary(
                        tierZero.size(),
                        alreadyPresent,
                        built,
                        failed,
                        totalCompressedBytes,
                        catalogStart
                );


                if (throwable instanceof IOException ioException) {

                    throw ioException;
                }


                if (throwable instanceof RuntimeException runtimeException) {

                    throw runtimeException;
                }


                if (throwable instanceof Error error) {

                    throw error;
                }


                throw new IllegalStateException(
                        "Unexpected Tier-0 build failure.",
                        throwable
                );
            }
        }


        printCatalogSummary(
                tierZero.size(),
                alreadyPresent,
                built,
                failed,
                totalCompressedBytes,
                catalogStart
        );


        if (failed != 0) {

            throw new IllegalStateException(
                    "Tier-0 catalog contains failed materials."
            );
        }


        System.out.println();

        System.out.println(
                "FOUR-PIECE TIER-0 CATALOG BUILD PASSED"
        );

        System.out.println(
                "ALL PAWNLESS FOUR-PIECE MATERIAL ASSETS ARE AVAILABLE"
        );
    }


    /**
     * Extract the complete canonical pawnless catalog.
     *
     * The material catalog already defines all 30 canonical four-piece
     * classes. Tier 0 is exactly the pawnless subset.
     */
    private static List<FourPieceMaterialClass> tierZeroMaterials() {

        List<FourPieceMaterialClass> result =
                new ArrayList<>();


        for (FourPieceMaterialClass material
                : FourPieceMaterialClass.allCanonical()) {

            if (material.buildTier()
                    == 0) {

                result.add(
                        material
                );
            }
        }


        if (result.size()
                != 20) {

            throw new IllegalStateException(
                    "Expected 20 Tier-0 materials, found "
                            + result.size()
            );
        }


        return List.copyOf(
                result
        );
    }


    /**
     * Canonical orientation used for persisted Tier-0 assets.
     *
     * SAME_SIDE:
     *
     *     the side owning both extras is WHITE.
     *
     *     Black-owned versions can later be obtained by color reversal,
     *     which Milestone 18 already validated exactly for KQBK.
     *
     * SPLIT:
     *
     *     FourPieceGenericPrimitiveRules defines the canonical split
     *     orientation as first extra = WHITE, second extra = BLACK.
     *
     *     The boolean constructor argument is therefore irrelevant to
     *     ownership in the split case, but true is used consistently.
     */
    private static boolean canonicalOwnerFlag(
            FourPieceMaterialClass material
    ) {

        return true;
    }


    private static Path assetPath(
            Path directory,
            FourPieceMaterialClass material
    ) {

        String suffix =
                switch (material.distribution()) {

                    case SAME_SIDE ->
                            "-white.ftb.gz";

                    case SPLIT ->
                            "-canonical.ftb.gz";
                };


        return directory.resolve(
                material.assetStem()
                        + suffix
        );
    }


    /**
     * Validate an asset produced by an earlier run.
     *
     * FourPieceGenericTablebaseCodec.load() already performs:
     *
     *  - magic/version verification
     *  - state-count verification
     *  - metadata parsing
     *  - full decompression
     *  - SHA-256 verification over all WDL/DTM data
     *
     * Here we additionally verify that the loaded asset belongs to the
     * material/orientation expected at this catalog slot.
     */
    private static ExistingAssetResult validateExistingAsset(
            FourPieceMaterialClass expectedMaterial,
            Path path
    ) throws IOException {

        System.out.println(
                "Existing asset found."
        );

        System.out.println(
                "Validating..."
        );


        long start =
                System.nanoTime();


        FourPieceGenericTablebase loaded =
                FourPieceGenericTablebaseCodec.load(
                        path
                );


        long loadMillis =
                elapsedMillis(
                        start
                );


        validateIdentity(
                loaded,
                expectedMaterial
        );


        String digest =
                FourPieceGenericTablebaseCodec.contentDigest(
                        loaded
                );


        long bytes =
                Files.size(
                        path
                );


        System.out.println(
                "Checksum: PASSED"
        );

        System.out.println(
                "Legal states: "
                        + format(
                        loaded.legalStates()
                )
        );

        System.out.println(
                "WIN: "
                        + format(
                        loaded.wins()
                )
        );

        System.out.println(
                "LOSS: "
                        + format(
                        loaded.losses()
                )
        );

        System.out.println(
                "DRAW: "
                        + format(
                        loaded.draws()
                )
        );

        System.out.println(
                "Maximum DTM: "
                        + loaded.maximumDistance()
        );

        System.out.println(
                "Compressed size: "
                        + formatMiB(
                        bytes
                )
        );

        System.out.println(
                "Validation/load time: "
                        + formatDuration(
                        loadMillis
                )
        );


        return new ExistingAssetResult(
                digest,
                bytes
        );
    }


    /**
     * Solve one missing material, persist it, discard the build result,
     * reload it, and prove persistence did not alter any WDL/DTM entry.
     */
    private static BuildAssetResult buildAndPersist(
            FourPieceMaterialClass material,
            Path path
    ) throws IOException {

        boolean ownerFlag =
                canonicalOwnerFlag(
                        material
                );


        System.out.println(
                "Asset missing."
        );

        System.out.println(
                "Solving "
                        + material.displayName()
                        + "..."
        );


        long buildStart =
                System.nanoTime();


        FourPieceGenericPrimitiveRetrogradeBuilder.Result result =
                new FourPieceGenericPrimitiveRetrogradeBuilder(
                        material,
                        ownerFlag
                ).build();


        long buildMillis =
                elapsedMillis(
                        buildStart
                );


        FourPieceGenericTablebase tablebase =
                FourPieceGenericTablebase.fromResult(
                        result
                );


        validateIdentity(
                tablebase,
                material
        );


        String originalDigest =
                FourPieceGenericTablebaseCodec.contentDigest(
                        tablebase
                );


        System.out.println();

        System.out.println(
                "Solve complete."
        );

        System.out.println(
                "Build time: "
                        + formatDuration(
                        buildMillis
                )
        );

        System.out.println(
                "Legal states: "
                        + format(
                        tablebase.legalStates()
                )
        );

        System.out.println(
                "WIN: "
                        + format(
                        tablebase.wins()
                )
        );

        System.out.println(
                "LOSS: "
                        + format(
                        tablebase.losses()
                )
        );

        System.out.println(
                "DRAW: "
                        + format(
                        tablebase.draws()
                )
        );

        System.out.println(
                "Maximum DTM: "
                        + tablebase.maximumDistance()
        );

        System.out.println(
                "Original digest: "
                        + originalDigest
        );


        // =====================================================
        // SAVE
        // =====================================================

        System.out.println();

        System.out.println(
                "Saving..."
        );


        long saveStart =
                System.nanoTime();


        FourPieceGenericTablebaseCodec.save(
                tablebase,
                path
        );


        long saveMillis =
                elapsedMillis(
                        saveStart
                );


        long fileBytes =
                Files.size(
                        path
                );


        System.out.println(
                "Save complete."
        );

        System.out.println(
                "Save time: "
                        + formatDuration(
                        saveMillis
                )
        );

        System.out.println(
                "Compressed size: "
                        + formatMiB(
                        fileBytes
                )
        );


        /*
         * Release all build arrays before the reload check.
         */
        tablebase =
                null;

        result =
                null;


        System.gc();


        // =====================================================
        // RELOAD
        // =====================================================

        System.out.println();

        System.out.println(
                "Reloading persisted asset..."
        );


        long loadStart =
                System.nanoTime();


        FourPieceGenericTablebase loaded =
                FourPieceGenericTablebaseCodec.load(
                        path
                );


        long loadMillis =
                elapsedMillis(
                        loadStart
                );


        validateIdentity(
                loaded,
                material
        );


        String loadedDigest =
                FourPieceGenericTablebaseCodec.contentDigest(
                        loaded
                );


        boolean digestMatch =
                originalDigest.equals(
                        loadedDigest
                );


        System.out.println(
                "Reload time: "
                        + formatDuration(
                        loadMillis
                )
        );

        System.out.println(
                "Reloaded digest: "
                        + loadedDigest
        );

        System.out.println(
                "Full WDL/DTM digest match: "
                        + digestMatch
        );


        if (!digestMatch) {

            throw new IllegalStateException(
                    "Persisted "
                            + material.displayName()
                            + " tablebase differs from the solver result."
            );
        }


        return new BuildAssetResult(
                loadedDigest,
                fileBytes
        );
    }


    private static void validateIdentity(
            FourPieceGenericTablebase tablebase,
            FourPieceMaterialClass expectedMaterial
    ) {

        if (!tablebase.material()
                .equals(
                        expectedMaterial
                )) {

            throw new IllegalStateException(
                    "Material mismatch. Expected "
                            + expectedMaterial.displayName()
                            + ", loaded "
                            + tablebase.material()
                            .displayName()
                            + "."
            );
        }


        boolean expectedOwnerFlag =
                canonicalOwnerFlag(
                        expectedMaterial
                );


        if (tablebase.sameSideOwnerIsWhite()
                != expectedOwnerFlag) {

            throw new IllegalStateException(
                    "Canonical orientation mismatch for "
                            + expectedMaterial.displayName()
                            + "."
            );
        }


        if (tablebase.legalStates()
                < 1) {

            throw new IllegalStateException(
                    "Tablebase contains no legal states: "
                            + expectedMaterial.displayName()
            );
        }


        if (tablebase.wins()
                + tablebase.losses()
                + tablebase.draws()
                != tablebase.legalStates()) {

            throw new IllegalStateException(
                    "Outcome counts do not equal legal-state count for "
                            + expectedMaterial.displayName()
            );
        }


        if (tablebase.maximumDistance()
                < 0) {

            throw new IllegalStateException(
                    "Invalid maximum DTM for "
                            + expectedMaterial.displayName()
                            + ": "
                            + tablebase.maximumDistance()
            );
        }
    }


    private static void printCatalogSummary(
            int total,
            int alreadyPresent,
            int built,
            int failed,
            long compressedBytes,
            long startNanos
    ) {

        long elapsedMillis =
                (System.nanoTime()
                        - startNanos)
                        / 1_000_000L;


        int completed =
                alreadyPresent
                        + built;


        System.out.println();

        System.out.println(
                "============================================================"
        );

        System.out.println(
                "Tier-0 catalog summary"
        );

        System.out.println(
                "============================================================"
        );

        System.out.println(
                "Catalog materials: "
                        + total
        );

        System.out.println(
                "Completed: "
                        + completed
                        + " / "
                        + total
        );

        System.out.println(
                "Already present: "
                        + alreadyPresent
        );

        System.out.println(
                "Built this run: "
                        + built
        );

        System.out.println(
                "Failed: "
                        + failed
        );

        System.out.println(
                "Known compressed assets: "
                        + formatMiB(
                        compressedBytes
                )
        );

        System.out.println(
                "Elapsed: "
                        + formatDuration(
                        elapsedMillis
                )
        );
    }


    private static long elapsedMillis(
            long startNanos
    ) {

        return (System.nanoTime()
                - startNanos)
                / 1_000_000L;
    }


    private static String format(
            long value
    ) {

        return String.format(
                "%,d",
                value
        );
    }


    private static String formatMiB(
            long bytes
    ) {

        return String.format(
                "%.2f MiB",
                bytes
                        / (1024.0 * 1024.0)
        );
    }


    private static String formatDuration(
            long millis
    ) {

        long totalSeconds =
                millis / 1_000L;


        long hours =
                totalSeconds / 3_600L;

        long minutes =
                (totalSeconds % 3_600L)
                        / 60L;

        long seconds =
                totalSeconds % 60L;


        if (hours > 0) {

            return String.format(
                    "%dh %02dm %02ds (%d ms)",
                    hours,
                    minutes,
                    seconds,
                    millis
            );
        }


        if (minutes > 0) {

            return String.format(
                    "%dm %02ds (%d ms)",
                    minutes,
                    seconds,
                    millis
            );
        }


        return String.format(
                "%.3f s (%d ms)",
                millis / 1000.0,
                millis
        );
    }


    private record ExistingAssetResult(
            String digest,
            long fileBytes
    ) {

    }


    private record BuildAssetResult(
            String digest,
            long fileBytes
    ) {

    }
}
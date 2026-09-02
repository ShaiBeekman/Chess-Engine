package main.java.chess.endgame;

import main.java.chess.model.PieceType;

import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;


/**
 * Milestone 19:
 *
 * Build KQBK once, save the complete generic tablebase, release the
 * build result from memory, load the tablebase back from disk, and
 * verify that its complete WDL/DTM digest is unchanged.
 *
 * This is the transition from:
 *
 *     solve every time
 *
 * to:
 *
 *     solve once -> persist -> load thereafter
 */
public final class FourPieceGenericTablebasePersistenceMain {

    private static final Path DEFAULT_PATH =
            Path.of(
                    "tablebases",
                    "four-piece",
                    "KQBK-white.ftb.gz"
            );


    private FourPieceGenericTablebasePersistenceMain() {

    }


    public static void main(
            String[] args
    ) throws IOException {

        Path outputPath =
                args.length > 0
                        ? Path.of(
                        args[0]
                )
                        : DEFAULT_PATH;


        FourPieceMaterialClass material =
                FourPieceMaterialClass.sameSide(
                        PieceType.QUEEN,
                        PieceType.BISHOP
                );


        System.out.println(
                "Generic four-piece persistence verification"
        );

        System.out.println(
                "==========================================="
        );

        System.out.println(
                "Material: "
                        + material.displayName()
        );

        System.out.println(
                "Orientation: WHITE owns Q+B"
        );

        System.out.println(
                "Output: "
                        + outputPath.toAbsolutePath()
                        .normalize()
        );


        // =====================================================
        // BUILD
        // =====================================================

        System.out.println();

        System.out.println(
                "Building KQBK..."
        );


        FourPieceGenericPrimitiveRetrogradeBuilder builder =
                new FourPieceGenericPrimitiveRetrogradeBuilder(
                        material,
                        true
                );


        FourPieceGenericPrimitiveRetrogradeBuilder.Result result =
                builder.build();


        FourPieceGenericTablebase tablebase =
                FourPieceGenericTablebase.fromResult(
                        result
                );


        String originalDigest =
                FourPieceGenericTablebaseCodec.contentDigest(
                        tablebase
                );


        System.out.println();

        System.out.println(
                "Original digest: "
                        + originalDigest
        );


        // =====================================================
        // SAVE
        // =====================================================

        long saveStart =
                System.nanoTime();


        FourPieceGenericTablebaseCodec.save(
                tablebase,
                outputPath
        );


        long saveMillis =
                (System.nanoTime()
                        - saveStart)
                        / 1_000_000L;


        long fileBytes =
                Files.size(
                        outputPath
                );


        System.out.println();

        System.out.println(
                "Tablebase saved."
        );

        System.out.println(
                "Save time: "
                        + saveMillis
                        + " ms"
        );

        System.out.println(
                "Compressed file size: "
                        + String.format(
                        "%,d",
                        fileBytes
                )
                        + " bytes"
        );

        System.out.println(
                "Compressed file size: "
                        + String.format(
                        "%.2f MiB",
                        fileBytes
                                / (1024.0 * 1024.0)
                )
        );


        /*
         * Drop the original arrays before loading.
         *
         * This makes the verification closer to what a future application
         * launch will actually do and avoids deliberately holding two full
         * tablebases in memory.
         */
        tablebase =
                null;

        result =
                null;

        builder =
                null;


        System.gc();


        // =====================================================
        // LOAD
        // =====================================================

        long loadStart =
                System.nanoTime();


        FourPieceGenericTablebase loaded =
                FourPieceGenericTablebaseCodec.load(
                        outputPath
                );


        long loadMillis =
                (System.nanoTime()
                        - loadStart)
                        / 1_000_000L;


        String loadedDigest =
                FourPieceGenericTablebaseCodec.contentDigest(
                        loaded
                );


        System.out.println();

        System.out.println(
                "Tablebase loaded."
        );

        System.out.println(
                "Load time: "
                        + loadMillis
                        + " ms"
        );

        System.out.println(
                "Loaded digest: "
                        + loadedDigest
        );


        // =====================================================
        // VERIFY METADATA
        // =====================================================

        if (!loaded.material()
                .equals(
                        material
                )) {

            throw new IllegalStateException(
                    "Loaded material mismatch."
            );
        }


        if (!loaded.sameSideOwnerIsWhite()) {

            throw new IllegalStateException(
                    "Loaded orientation mismatch."
            );
        }


        if (loaded.legalStates()
                != 21_359_016L) {

            throw new IllegalStateException(
                    "Unexpected KQBK legal-state count: "
                            + loaded.legalStates()
            );
        }


        if (loaded.wins()
                != 7_698_432L) {

            throw new IllegalStateException(
                    "Unexpected KQBK WIN count: "
                            + loaded.wins()
            );
        }


        if (loaded.losses()
                != 12_379_568L) {

            throw new IllegalStateException(
                    "Unexpected KQBK LOSS count: "
                            + loaded.losses()
            );
        }


        if (loaded.draws()
                != 1_281_016L) {

            throw new IllegalStateException(
                    "Unexpected KQBK DRAW count: "
                            + loaded.draws()
            );
        }


        if (loaded.maximumDistance()
                != 20) {

            throw new IllegalStateException(
                    "Unexpected KQBK maximum DTM: "
                            + loaded.maximumDistance()
            );
        }


        boolean digestMatch =
                originalDigest.equals(
                        loadedDigest
                );


        System.out.println();

        System.out.println(
                "Persistence comparison"
        );

        System.out.println(
                "======================"
        );

        System.out.println(
                "Legal states: "
                        + String.format(
                        "%,d",
                        loaded.legalStates()
                )
        );

        System.out.println(
                "WIN: "
                        + String.format(
                        "%,d",
                        loaded.wins()
                )
        );

        System.out.println(
                "LOSS: "
                        + String.format(
                        "%,d",
                        loaded.losses()
                )
        );

        System.out.println(
                "DRAW: "
                        + String.format(
                        "%,d",
                        loaded.draws()
                )
        );

        System.out.println(
                "Maximum DTM: "
                        + loaded.maximumDistance()
        );

        System.out.println(
                "Full WDL/DTM digest match: "
                        + digestMatch
        );


        if (!digestMatch) {

            throw new IllegalStateException(
                    "Persisted KQBK table does not match the built table."
            );
        }


        System.out.println();

        System.out.println(
                "GENERIC FOUR-PIECE PERSISTENCE PASSED"
        );

        System.out.println(
                "KQBK CAN NOW BE LOADED WITHOUT REBUILDING"
        );
    }
}
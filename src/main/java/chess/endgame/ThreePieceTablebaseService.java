package main.java.chess.endgame;

import main.java.chess.model.Color;
import main.java.chess.model.PieceType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;


/**
 * Loads and caches exact 3-piece KQK/KRK/KPK tablebases.
 *
 * Lookup order:
 *
 *     1. in-memory cache
 *     2. external release file under tablebases
 *     3. packaged classpath resource
 *     4. development file under src/main/resources/tablebases
 *     5. retrograde build fallback
 *
 * The external tablebase path is the normal v1.0 release/runtime location.
 * Packaged resources remain supported for compatibility, and the development
 * path keeps IntelliJ development reliable even when resources are not copied
 * into the output directory.
 */
public final class ThreePieceTablebaseService {

    private static final String RESOURCE_ROOT =
            "/tablebases/";

    /*
     * M88.3A:
     *
     * Release tablebases live beside the application in:
     *
     *     tablebases/
     *
     * This mirrors the existing four-piece runtime layout:
     *
     *     tablebases/four-piece/
     *
     * The path is deliberately relative to the process working directory,
     * matching the rest of the current release asset lookup behavior.
     */
    private static final Path EXTERNAL_RESOURCE_ROOT =
            Path.of(
                    "tablebases"
            );

    private static final Path DEVELOPMENT_RESOURCE_ROOT =
            Path.of(
                    "src",
                    "main",
                    "resources",
                    "tablebases"
            );


    private final Map<Key, ThreePieceTablebase>
            cache =
            new HashMap<>();


    public synchronized ThreePieceTablebase get(
            PieceType majorType,
            Color majorColor
    ) {

        validateSupported(
                majorType,
                majorColor
        );


        Key key =
                new Key(
                        majorType,
                        majorColor
                );


        ThreePieceTablebase existing =
                cache.get(
                        key
                );

        if (existing != null) {
            return existing;
        }


        ThreePieceTablebase tablebase =
                loadExternalTablebase(
                        key
                );


        if (tablebase == null) {
            tablebase =
                    loadPackagedTablebase(
                            key
                    );
        }


        if (tablebase == null) {
            tablebase =
                    loadDevelopmentTablebase(
                            key
                    );
        }


        if (tablebase == null) {
            tablebase =
                    buildFallback(
                            key
                    );
        }


        cache.put(
                key,
                tablebase
        );


        return tablebase;
    }


    public synchronized boolean isBuilt(
            PieceType majorType,
            Color majorColor
    ) {

        ThreePieceTablebase tablebase =
                cache.get(
                        new Key(
                                majorType,
                                majorColor
                        )
                );


        return tablebase != null
                &&
                tablebase.isBuilt();
    }


    public synchronized void clear() {
        cache.clear();
    }


    // =========================================================
    // EXTERNAL RELEASE RESOURCE
    // =========================================================

    private ThreePieceTablebase loadExternalTablebase(
            Key key
    ) {

        Path path =
                EXTERNAL_RESOURCE_ROOT.resolve(
                        fileName(
                                key
                        )
                );


        if (!Files.isRegularFile(
                path
        )) {
            return null;
        }


        try {

            ThreePieceTablebase loaded =
                    ThreePieceTablebaseCodec.load(
                            path
                    );


            validateLoadedIdentity(
                    key,
                    loaded,
                    path.toAbsolutePath()
                            .toString()
            );


            return loaded;

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Failed to load external tablebase file "
                            + path.toAbsolutePath()
                            + ".",
                    exception
            );
        }
    }


    // =========================================================
    // PACKAGED CLASSPATH RESOURCE
    // =========================================================

    private ThreePieceTablebase loadPackagedTablebase(
            Key key
    ) {

        String resourcePath =
                RESOURCE_ROOT
                        + fileName(
                        key
                );


        try (InputStream input =
                     ThreePieceTablebaseService.class
                             .getResourceAsStream(
                                     resourcePath
                             )) {

            if (input == null) {
                return null;
            }


            ThreePieceTablebase loaded =
                    ThreePieceTablebaseCodec.load(
                            input
                    );


            validateLoadedIdentity(
                    key,
                    loaded,
                    resourcePath
            );


            return loaded;

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Failed to load packaged tablebase resource "
                            + resourcePath
                            + ".",
                    exception
            );
        }
    }


    // =========================================================
    // DEVELOPMENT SOURCE RESOURCE
    // =========================================================

    private ThreePieceTablebase loadDevelopmentTablebase(
            Key key
    ) {

        Path path =
                DEVELOPMENT_RESOURCE_ROOT.resolve(
                        fileName(
                                key
                        )
                );


        if (!Files.isRegularFile(
                path
        )) {
            return null;
        }


        try {

            ThreePieceTablebase loaded =
                    ThreePieceTablebaseCodec.load(
                            path
                    );


            validateLoadedIdentity(
                    key,
                    loaded,
                    path.toAbsolutePath()
                            .toString()
            );


            return loaded;

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Failed to load development tablebase file "
                            + path.toAbsolutePath()
                            + ".",
                    exception
            );
        }
    }


    // =========================================================
    // FINAL FALLBACK
    // =========================================================

    private ThreePieceTablebase buildFallback(
            Key key
    ) {

        ThreePieceTablebase tablebase =
                new ThreePieceTablebase(
                        key.majorType(),
                        key.majorColor()
                );


        tablebase.build();


        return tablebase;
    }


    // =========================================================
    // RESOURCE NAMING
    // =========================================================

    private String fileName(
            Key key
    ) {

        String material =
                switch (key.majorType()) {

                    case QUEEN ->
                            "KQK";

                    case ROOK ->
                            "KRK";

                    case PAWN ->
                            "KPK";

                    default ->
                            throw new IllegalArgumentException(
                                    "Unsupported tablebase material: "
                                            + key.majorType()
                            );
                };


        String color =
                key.majorColor() == Color.WHITE
                        ? "white"
                        : "black";


        return material
                + "-"
                + color
                + "-v1.tb";
    }


    // =========================================================
    // VALIDATION
    // =========================================================

    private void validateSupported(
            PieceType majorType,
            Color majorColor
    ) {

        if (majorType == null) {
            throw new IllegalArgumentException(
                    "Major-piece type cannot be null."
            );
        }


        if (majorColor == null) {
            throw new IllegalArgumentException(
                    "Major-piece color cannot be null."
            );
        }


        if (majorType != PieceType.QUEEN
                && majorType != PieceType.ROOK
                && majorType != PieceType.PAWN) {

            throw new IllegalArgumentException(
                    "Three-piece tablebase service currently supports only KQK, KRK, and KPK."
            );
        }
    }


    private void validateLoadedIdentity(
            Key requested,
            ThreePieceTablebase loaded,
            String source
    ) {

        if (loaded.getMajorType()
                != requested.majorType()) {

            throw new IllegalStateException(
                    "Tablebase "
                            + source
                            + " contains "
                            + loaded.getMajorType()
                            + " instead of "
                            + requested.majorType()
                            + "."
            );
        }


        if (loaded.getMajorColor()
                != requested.majorColor()) {

            throw new IllegalStateException(
                    "Tablebase "
                            + source
                            + " contains "
                            + loaded.getMajorColor()
                            + " instead of "
                            + requested.majorColor()
                            + "."
            );
        }
    }


    private record Key(
            PieceType majorType,
            Color majorColor
    ) {
    }
}

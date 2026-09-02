package main.java.chess.endgame;

import main.java.chess.model.Color;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;


/**
 * Loads and caches exact four-piece tablebases.
 *
 * Milestone 1 intentionally has no expensive runtime retrograde fallback.
 * A four-piece asset is large enough that exact generation belongs in an
 * explicit offline builder, not on the Swing event path.
 *
 * Lookup order:
 *
 *     1. in-memory cache
 *     2. packaged classpath resource
 *     3. development file under src/main/resources/tablebases
 *     4. unloaded architecture object
 *
 * The final object still provides encoding and transition validation, but
 * probe() returns UNSUPPORTED for in-class states until exact data exists.
 */
public final class FourPieceTablebaseService {

    private static final String RESOURCE_ROOT =
            "/tablebases/";

    private static final Path DEVELOPMENT_RESOURCE_ROOT =
            Path.of(
                    "src",
                    "main",
                    "resources",
                    "tablebases"
            );


    private final Map<Key, FourPieceTablebase>
            cache =
            new HashMap<>();


    public synchronized FourPieceTablebase getKqrk(
            Color strongColor
    ) {

        if (strongColor == null) {

            throw new IllegalArgumentException(
                    "Strong color cannot be null."
            );
        }


        Key key =
                new Key(
                        "KQRK",
                        strongColor
                );


        FourPieceTablebase existing =
                cache.get(
                        key
                );


        if (existing != null) {
            return existing;
        }


        FourPieceTablebase tablebase =
                loadPackagedTablebase(
                        key
                );


        if (tablebase == null) {

            tablebase =
                    loadDevelopmentTablebase(
                            key
                    );
        }


        if (tablebase == null) {

            /*
             * No surprise 30M-state build at runtime.
             */
            tablebase =
                    new FourPieceTablebase(
                            FourPieceMaterial.kqrk(
                                    strongColor
                            )
                    );
        }


        cache.put(
                key,
                tablebase
        );


        return tablebase;
    }


    public synchronized boolean isLoaded(
            Color strongColor
    ) {

        FourPieceTablebase tablebase =
                cache.get(
                        new Key(
                                "KQRK",
                                strongColor
                        )
                );


        return tablebase != null
                && tablebase.isBuilt();
    }


    public synchronized void clear() {

        cache.clear();
    }


    private FourPieceTablebase loadPackagedTablebase(
            Key key
    ) {

        String resourcePath =
                RESOURCE_ROOT
                        + fileName(
                        key
                );


        try (InputStream input =
                     FourPieceTablebaseService.class
                             .getResourceAsStream(
                                     resourcePath
                             )) {

            if (input == null) {
                return null;
            }


            FourPieceTablebase loaded =
                    FourPieceTablebaseCodec.load(
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
                    "Failed to load packaged four-piece tablebase resource "
                            + resourcePath
                            + ".",
                    exception
            );
        }
    }


    private FourPieceTablebase loadDevelopmentTablebase(
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

            FourPieceTablebase loaded =
                    FourPieceTablebaseCodec.load(
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
                    "Failed to load development four-piece tablebase file "
                            + path.toAbsolutePath()
                            + ".",
                    exception
            );
        }
    }


    private String fileName(
            Key key
    ) {

        String color =
                key.strongColor()
                        == Color.WHITE
                        ? "white"
                        : "black";


        return key.material()
                + "-"
                + color
                + "-v1.tb";
    }


    private void validateLoadedIdentity(
            Key requested,
            FourPieceTablebase loaded,
            String source
    ) {

        if (!loaded.materialName()
                .equals(
                        requested.material()
                )) {

            throw new IllegalStateException(
                    "Tablebase "
                            + source
                            + " contains "
                            + loaded.materialName()
                            + " instead of "
                            + requested.material()
                            + "."
            );
        }


        if (loaded.getMaterial()
                .strongColor()
                != requested.strongColor()) {

            throw new IllegalStateException(
                    "Tablebase "
                            + source
                            + " contains "
                            + loaded.getMaterial()
                            .strongColor()
                            + " instead of "
                            + requested.strongColor()
                            + "."
            );
        }
    }


    private record Key(
            String material,
            Color strongColor
    ) {
    }
}

package main.java.chess.endgame;

import main.java.chess.model.PieceType;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

/**
 * Lightweight curriculum metadata reader.  It reads only persisted tablebase
 * headers, so the UI can show exact denominators without loading the enormous
 * outcome/distance arrays.
 */
public final class EndgameCurriculumMetadata {

    public static final long KQK_WIN_TOTAL = 144_508L;
    public static final long KRK_WIN_TOTAL = 175_168L;
    public static final long KPK_WIN_TOTAL = 124_960L;

    private static final int FOUR_PIECE_GENERIC_MAGIC = 0x34475442; // 4GTB
    private static final int FOUR_PIECE_GENERIC_VERSION = 1;
    private static final int FOUR_PIECE_KPKP_MAGIC = 0x4B504B50;   // KPKP
    private static final int FOUR_PIECE_KPKP_VERSION = 1;

    private static final Path RUNTIME_DIRECTORY =
            Path.of("tablebases", "four-piece");

    private static final Path DEVELOPMENT_DIRECTORY =
            Path.of("src", "main", "resources", "tablebases", "four-piece");

    private static final Map<String, Long> FOUR_PIECE_WIN_CACHE =
            new ConcurrentHashMap<>();

    private EndgameCurriculumMetadata() {
    }

    public static long mixedWinTotal(
            List<FourPieceMaterialClass> fourPieceCatalog
    ) throws IOException {
        if (fourPieceCatalog == null || fourPieceCatalog.isEmpty()) {
            throw new IOException("Four-piece curriculum catalog is empty.");
        }

        long total = KQK_WIN_TOTAL + KRK_WIN_TOTAL + KPK_WIN_TOTAL;

        for (FourPieceMaterialClass material : fourPieceCatalog) {
            long wins = fourPieceWinTotal(material);

            if (wins <= 0) {
                throw new IOException(
                        "Non-positive WIN count for " + material.assetStem()
                );
            }

            total = Math.addExact(total, wins);
        }

        return total;
    }

    public static long fourPieceWinTotal(
            FourPieceMaterialClass material
    ) throws IOException {
        if (material == null) {
            throw new IllegalArgumentException(
                    "Four-piece material cannot be null."
            );
        }

        String family = material.assetStem();
        Long cached = FOUR_PIECE_WIN_CACHE.get(family);

        if (cached != null) {
            return cached;
        }

        String fileName = assetFileName(material);

        try (InputStream raw = openAsset(fileName)) {
            if (raw == null) {
                throw new IOException(
                        "Tablebase asset not found: " + fileName
                );
            }

            long wins = readWinCount(raw, material, fileName);
            FOUR_PIECE_WIN_CACHE.put(family, wins);
            return wins;
        }
    }

    public static void clearCache() {
        FOUR_PIECE_WIN_CACHE.clear();
    }

    private static String assetFileName(
            FourPieceMaterialClass material
    ) {
        boolean splitPawnPawn =
                material.pawnCount() == 2
                        && material.distribution()
                        == FourPieceMaterialClass.Distribution.SPLIT;

        if (splitPawnPawn) {
            return material.assetStem() + "-canonical-ep.ftb.gz";
        }

        String suffix =
                material.distribution()
                        == FourPieceMaterialClass.Distribution.SAME_SIDE
                        ? "-white.ftb.gz"
                        : "-canonical.ftb.gz";

        return material.assetStem() + suffix;
    }

    private static InputStream openAsset(
            String fileName
    ) throws IOException {
        Path runtime = RUNTIME_DIRECTORY.resolve(fileName);

        if (Files.isRegularFile(runtime)) {
            return Files.newInputStream(runtime);
        }

        Path development = DEVELOPMENT_DIRECTORY.resolve(fileName);

        if (Files.isRegularFile(development)) {
            return Files.newInputStream(development);
        }

        return EndgameCurriculumMetadata.class.getResourceAsStream(
                "/tablebases/four-piece/" + fileName
        );
    }

    private static long readWinCount(
            InputStream raw,
            FourPieceMaterialClass expectedMaterial,
            String sourceName
    ) throws IOException {
        try (DataInputStream input =
                     new DataInputStream(
                             new GZIPInputStream(
                                     new BufferedInputStream(raw),
                                     32 * 1024
                             )
                     )) {

            int magic = input.readInt();

            if (magic == FOUR_PIECE_KPKP_MAGIC) {
                int version = input.readInt();

                if (version != FOUR_PIECE_KPKP_VERSION) {
                    throw new IOException(
                            "Unsupported KP-KP metadata version in "
                                    + sourceName
                    );
                }

                FourPieceMaterialClass material = readMaterialIdentity(input);
                requireMaterial(expectedMaterial, material, sourceName);

                input.readInt();   // state count
                input.readLong();  // legal states
                input.readLong();  // legal en-passant states
                long wins = input.readLong();
                requireNonNegativeWins(wins, sourceName);
                return wins;
            }

            if (magic != FOUR_PIECE_GENERIC_MAGIC) {
                throw new IOException(
                        "Unknown four-piece tablebase format in " + sourceName
                );
            }

            int version = input.readInt();

            if (version != FOUR_PIECE_GENERIC_VERSION) {
                throw new IOException(
                        "Unsupported four-piece metadata version in "
                                + sourceName
                );
            }

            FourPieceMaterialClass material = readMaterialIdentity(input);
            requireMaterial(expectedMaterial, material, sourceName);

            input.readBoolean(); // canonical orientation flag
            input.readInt();     // state count
            input.readLong();    // legal states
            long wins = input.readLong();
            requireNonNegativeWins(wins, sourceName);
            return wins;
        }
    }

    private static FourPieceMaterialClass readMaterialIdentity(
            DataInputStream input
    ) throws IOException {
        try {
            FourPieceMaterialClass.Distribution distribution =
                    FourPieceMaterialClass.Distribution.valueOf(
                            input.readUTF()
                    );

            PieceType first = PieceType.valueOf(input.readUTF());
            PieceType second = PieceType.valueOf(input.readUTF());

            return distribution
                    == FourPieceMaterialClass.Distribution.SAME_SIDE
                    ? FourPieceMaterialClass.sameSide(first, second)
                    : FourPieceMaterialClass.split(first, second);

        } catch (IllegalArgumentException exception) {
            throw new IOException(
                    "Invalid material identity in four-piece tablebase header.",
                    exception
            );
        }
    }

    private static void requireMaterial(
            FourPieceMaterialClass expected,
            FourPieceMaterialClass actual,
            String sourceName
    ) throws IOException {
        if (!expected.equals(actual)) {
            throw new IOException(
                    "Tablebase metadata mismatch in "
                            + sourceName
                            + ": expected "
                            + expected.assetStem()
                            + ", found "
                            + actual.assetStem()
            );
        }
    }

    private static void requireNonNegativeWins(
            long wins,
            String sourceName
    ) throws IOException {
        if (wins < 0) {
            throw new IOException(
                    "Negative WIN count in " + sourceName
            );
        }
    }
}

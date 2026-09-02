package main.java.chess.tests;

import main.java.chess.endgame.ThreePieceTablebase;
import main.java.chess.endgame.ThreePieceTablebaseCodec;
import main.java.chess.model.Color;
import main.java.chess.model.PieceType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Development-only builder for permanent packaged 3-piece tablebases.
 *
 * By default the generated files are written to:
 *
 *     src/main/resources/tablebases
 *
 * You may optionally pass another output directory as the first
 * command-line argument.
 */
public final class ThreePieceTablebaseResourceBuilder {

    private static final Path DEFAULT_OUTPUT_DIRECTORY =
            Path.of(
                    "src",
                    "main",
                    "resources",
                    "tablebases"
            );


    private ThreePieceTablebaseResourceBuilder() {
    }


    public static void main(
            String[] args
    ) throws Exception {

        Path outputDirectory =
                args.length > 0
                        ? Path.of(args[0])
                        : DEFAULT_OUTPUT_DIRECTORY;

        Files.createDirectories(outputDirectory);

        System.out.println();
        System.out.println("=============================================");
        System.out.println("THREE-PIECE TABLEBASE RESOURCE BUILDER");
        System.out.println("=============================================");
        System.out.println(
                "Output: "
                        + outputDirectory.toAbsolutePath()
        );

        buildAndSave(
                PieceType.QUEEN,
                Color.WHITE,
                outputDirectory.resolve("KQK-white-v1.tb")
        );

        buildAndSave(
                PieceType.QUEEN,
                Color.BLACK,
                outputDirectory.resolve("KQK-black-v1.tb")
        );

        buildAndSave(
                PieceType.ROOK,
                Color.WHITE,
                outputDirectory.resolve("KRK-white-v1.tb")
        );

        buildAndSave(
                PieceType.ROOK,
                Color.BLACK,
                outputDirectory.resolve("KRK-black-v1.tb")
        );

        System.out.println();
        System.out.println("=============================================");
        System.out.println("ALL TABLEBASE RESOURCES GENERATED");
        System.out.println("=============================================");
    }


    private static void buildAndSave(
            PieceType majorType,
            Color majorColor,
            Path outputFile
    ) throws IOException {

        String name =
                displayName(
                        majorType,
                        majorColor
                );

        System.out.println();
        System.out.println(
                "--- "
                        + name
                        + " ---"
        );

        ThreePieceTablebase tablebase =
                new ThreePieceTablebase(
                        majorType,
                        majorColor
                );

        long buildStarted =
                System.currentTimeMillis();

        tablebase.build();

        long buildMillis =
                System.currentTimeMillis()
                        - buildStarted;

        long saveStarted =
                System.currentTimeMillis();

        ThreePieceTablebaseCodec.save(
                outputFile,
                tablebase
        );

        long saveMillis =
                System.currentTimeMillis()
                        - saveStarted;

        long bytes =
                Files.size(outputFile);

        System.out.println(
                "Build time:       "
                        + buildMillis
                        + " ms"
        );

        System.out.println(
                "Save time:        "
                        + saveMillis
                        + " ms"
        );

        System.out.printf(
                "File size:        %.3f MiB (%d bytes)%n",
                bytes / (1024.0 * 1024.0),
                bytes
        );

        System.out.println(
                "Legal states:     "
                        + tablebase.getLegalStateCount()
        );

        System.out.println(
                "WIN:              "
                        + tablebase.getWinCount()
        );

        System.out.println(
                "LOSS:             "
                        + tablebase.getLossCount()
        );

        System.out.println(
                "DRAW:             "
                        + tablebase.getDrawCount()
        );

        System.out.println(
                "Saved:            "
                        + outputFile.toAbsolutePath()
        );
    }


    private static String displayName(
            PieceType majorType,
            Color majorColor
    ) {

        String material =
                majorType == PieceType.QUEEN
                        ? "KQK"
                        : "KRK";

        String color =
                majorColor == Color.WHITE
                        ? "WHITE"
                        : "BLACK";

        return material
                + " / "
                + color;
    }
}

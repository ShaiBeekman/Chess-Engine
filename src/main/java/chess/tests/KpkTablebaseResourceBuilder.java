package main.java.chess.tests;

import main.java.chess.endgame.ThreePieceTablebase;
import main.java.chess.endgame.ThreePieceTablebaseCodec;
import main.java.chess.model.Color;
import main.java.chess.model.PieceType;

import java.nio.file.Files;
import java.nio.file.Path;


/**
 * One-time development builder for the two exact KPK resources.
 *
 * Run this class after KQK/KRK resources already exist.
 *
 * Output:
 *     src/main/resources/tablebases/KPK-white-v1.tb
 *     src/main/resources/tablebases/KPK-black-v1.tb
 */
public final class KpkTablebaseResourceBuilder {

    private static final Path OUTPUT_DIRECTORY =
            Path.of(
                    "src",
                    "main",
                    "resources",
                    "tablebases"
            );


    public static void main(
            String[] args
    ) throws Exception {

        System.out.println();
        System.out.println(
                "============================================="
        );
        System.out.println(
                "KPK TABLEBASE RESOURCE BUILDER"
        );
        System.out.println(
                "============================================="
        );
        System.out.println();


        Files.createDirectories(
                OUTPUT_DIRECTORY
        );


        build(
                Color.WHITE
        );


        build(
                Color.BLACK
        );


        System.out.println();
        System.out.println(
                "============================================="
        );
        System.out.println(
                "KPK RESOURCES COMPLETE"
        );
        System.out.println(
                "============================================="
        );
    }


    private static void build(
            Color pawnColor
    ) throws Exception {

        String colorName =
                pawnColor == Color.WHITE
                        ? "white"
                        : "black";


        Path output =
                OUTPUT_DIRECTORY.resolve(
                        "KPK-"
                                + colorName
                                + "-v1.tb"
                );


        System.out.println(
                "--- KPK / "
                        + pawnColor
                        + " ---"
        );


        ThreePieceTablebase tablebase =
                new ThreePieceTablebase(
                        PieceType.PAWN,
                        pawnColor
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
                output,
                tablebase
        );


        long saveMillis =
                System.currentTimeMillis()
                        - saveStarted;


        long bytes =
                Files.size(
                        output
                );


        double mebibytes =
                bytes
                        / 1024.0
                        / 1024.0;


        System.out.println(
                "Build:             "
                        + buildMillis
                        + " ms"
        );

        System.out.println(
                "Save:              "
                        + saveMillis
                        + " ms"
        );

        System.out.printf(
                "File:              %.3f MiB%n",
                mebibytes
        );

        System.out.println(
                "Legal states:      "
                        + tablebase.getLegalStateCount()
        );

        System.out.println(
                "WIN:               "
                        + tablebase.getWinCount()
        );

        System.out.println(
                "LOSS:              "
                        + tablebase.getLossCount()
        );

        System.out.println(
                "DRAW:              "
                        + tablebase.getDrawCount()
        );

        System.out.println(
                "Saved:             "
                        + output.toAbsolutePath()
        );

        System.out.println();
    }
}

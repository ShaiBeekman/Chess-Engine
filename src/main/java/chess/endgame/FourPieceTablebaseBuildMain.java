package main.java.chess.endgame;

import main.java.chess.model.Color;

import java.nio.file.Path;


/**
 * Offline KQRK asset generator.
 *
 * Run this class manually.  Do not invoke it from the GUI.
 */
public final class FourPieceTablebaseBuildMain {

    public static void main(
            String[] args
    ) throws Exception {

        Color strongColor =
                args.length > 0
                        && args[0].equalsIgnoreCase(
                        "black"
                )
                        ? Color.BLACK
                        : Color.WHITE;


        FourPieceTablebase tablebase =
                new FourPieceTablebase(
                        FourPieceMaterial.kqrk(
                                strongColor
                        )
                );


        FourPieceRetrogradeBuilder builder =
                new FourPieceRetrogradeBuilder(
                        tablebase
                );


        FourPieceRetrogradeBuilder.BuildResult result =
                builder.build();


        String colorName =
                strongColor == Color.WHITE
                        ? "white"
                        : "black";


        Path output =
                Path.of(
                        "src",
                        "main",
                        "resources",
                        "tablebases",
                        "KQRK-"
                                + colorName
                                + "-v1.tb"
                );


        FourPieceTablebaseCodec.save(
                output,
                tablebase
        );


        System.out.println();
        System.out.println(
                "KQRK build complete."
        );
        System.out.println(
                "Strong color: "
                        + strongColor
        );
        System.out.println(
                "Legal states: "
                        + result.legalStates()
        );
        System.out.println(
                "WIN: "
                        + result.wins()
        );
        System.out.println(
                "LOSS: "
                        + result.losses()
        );
        System.out.println(
                "DRAW: "
                        + result.draws()
        );
        System.out.println(
                "Sweeps: "
                        + result.sweeps()
        );
        System.out.println(
                "Build ms: "
                        + result.buildMillis()
        );
        System.out.println(
                "Saved: "
                        + output.toAbsolutePath()
        );
    }
}

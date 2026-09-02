package main.java.chess.endgame;

import main.java.chess.model.Color;

import java.nio.file.Path;


/**
 * Offline primitive KQRK exact-tablebase build.
 *
 * Optional first argument:
 *     white
 *     black
 *
 * Default: white
 */
public final class KqrkPrimitiveTablebaseBuildMain {

    public static void main(String[] args)
            throws Exception {

        Color strongColor =
                args.length > 0
                        && args[0].equalsIgnoreCase("black")
                        ? Color.BLACK
                        : Color.WHITE;

        KqrkPrimitiveRetrogradeBuilder builder =
                new KqrkPrimitiveRetrogradeBuilder(
                        strongColor
                );

        KqrkPrimitiveRetrogradeBuilder.Result result =
                builder.build();

        FourPieceTablebase tablebase =
                new FourPieceTablebase(
                        FourPieceMaterial.kqrk(
                                strongColor
                        )
                );

        tablebase.restoreFromPersistence(
                result.outcome(),
                result.distance()
        );

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
                "PRIMITIVE KQRK BUILD COMPLETE"
        );
        System.out.println(
                "Strong color: " + strongColor
        );
        System.out.println(
                "Legal states: " + result.legalStates()
        );
        System.out.println(
                "WIN: " + result.wins()
        );
        System.out.println(
                "LOSS: " + result.losses()
        );
        System.out.println(
                "DRAW: " + result.draws()
        );
        System.out.println(
                "Propagated states: "
                        + result.propagatedStates()
        );
        System.out.println(
                "Predecessor edges: "
                        + result.predecessorEdges()
        );
        System.out.println(
                "Classification ms: "
                        + result.classificationMillis()
        );
        System.out.println(
                "Propagation ms: "
                        + result.propagationMillis()
        );
        System.out.println(
                "Total ms: "
                        + result.totalMillis()
        );
        System.out.println(
                "Saved: "
                        + output.toAbsolutePath()
        );
    }
}

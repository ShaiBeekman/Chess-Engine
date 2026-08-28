package main.java.chess.tests;

import main.java.chess.endgame.ThreePieceTablebase;
import main.java.chess.model.Color;
import main.java.chess.model.Move;
import main.java.chess.model.PieceType;
import main.java.chess.model.Position;

import java.util.List;
import java.util.Random;


/**
 * First correctness/performance smoke test for the local retrograde
 * tablebase implementation.
 *
 * Run this class directly before wiring the tablebase into Endgame Study.
 */
public final class ThreePieceTablebaseTestHarness {

    private ThreePieceTablebaseTestHarness() {
    }


    public static void main(
            String[] args
    ) {

        runClass(
                PieceType.QUEEN,
                Color.WHITE
        );


        runClass(
                PieceType.ROOK,
                Color.WHITE
        );
    }


    private static void runClass(
            PieceType majorType,
            Color majorColor
    ) {

        System.out.println();
        System.out.println(
                "========================================"
        );

        System.out.println(
                "Building "
                        + majorType
                        + " tablebase ("
                        + majorColor
                        + " major side)"
        );

        System.out.println(
                "========================================"
        );


        ThreePieceTablebase tablebase =
                new ThreePieceTablebase(
                        majorType,
                        majorColor
                );


        long started =
                System.currentTimeMillis();


        tablebase.build();


        long elapsed =
                System.currentTimeMillis()
                        - started;


        System.out.println(
                "Build time: "
                        + elapsed
                        + " ms"
        );

        System.out.println(
                "Legal states: "
                        + tablebase.getLegalStateCount()
        );

        System.out.println(
                "WIN: "
                        + tablebase.getWinCount()
        );

        System.out.println(
                "LOSS: "
                        + tablebase.getLossCount()
        );

        System.out.println(
                "DRAW: "
                        + tablebase.getDrawCount()
        );


        Position sample =
                tablebase.randomWinningPosition(
                        new Random(1)
                );


        ThreePieceTablebase.Probe probe =
                tablebase.probe(
                        sample
                );


        List<Move> best =
                tablebase.bestMoves(
                        sample
                );


        System.out.println();
        System.out.println(
                "Random exact winning sample:"
        );

        System.out.println(
                "Outcome: "
                        + probe.outcome()
        );

        System.out.println(
                "Mate distance: "
                        + probe.mateDistance()
                        + " plies"
        );

        System.out.println(
                "Best moves found: "
                        + best.size()
        );


        for (Move move :
                best) {

            System.out.println(
                    "  "
                            + move
            );
        }


        if (probe.outcome()
                != ThreePieceTablebase.Outcome.WIN) {

            throw new IllegalStateException(
                    "Expected the generated study sample to be a WIN."
            );
        }


        if (best.isEmpty()) {

            throw new IllegalStateException(
                    "A winning tablebase position must have at least one optimal move."
            );
        }


        System.out.println(
                "Smoke test passed."
        );
    }
}

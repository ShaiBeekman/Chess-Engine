package main.java.chess.tests;

import main.java.chess.endgame.ThreePieceTablebase;
import main.java.chess.endgame.ThreePieceTablebaseService;
import main.java.chess.model.Color;
import main.java.chess.model.PieceType;


/**
 * Verifies that ThreePieceTablebaseService loads packaged resources
 * instead of rebuilding the retrograde tablebases at runtime.
 *
 * Expected behavior:
 * - first load for each material/color class should be very fast
 *   (typically milliseconds, not tens of seconds)
 * - second load should return the exact same cached object
 * - all four packaged resources should report the expected metadata
 */
public final class ThreePieceTablebaseResourceTestHarness {

    private ThreePieceTablebaseResourceTestHarness() {
    }


    public static void main(
            String[] args
    ) {

        ThreePieceTablebaseService service =
                new ThreePieceTablebaseService();


        System.out.println();
        System.out.println(
                "============================================="
        );
        System.out.println(
                "THREE-PIECE TABLEBASE RESOURCE TEST"
        );
        System.out.println(
                "============================================="
        );


        test(
                service,
                PieceType.QUEEN,
                Color.WHITE,
                368452,
                144508,
                200896,
                23048
        );


        test(
                service,
                PieceType.QUEEN,
                Color.BLACK,
                368452,
                144508,
                200896,
                23048
        );


        test(
                service,
                PieceType.ROOK,
                Color.WHITE,
                399112,
                175168,
                201700,
                22244
        );


        test(
                service,
                PieceType.ROOK,
                Color.BLACK,
                399112,
                175168,
                201700,
                22244
        );


        System.out.println();
        System.out.println(
                "============================================="
        );
        System.out.println(
                "ALL RESOURCE TESTS PASSED"
        );
        System.out.println(
                "============================================="
        );
    }


    private static void test(
            ThreePieceTablebaseService service,
            PieceType majorType,
            Color majorColor,
            int expectedLegalStates,
            int expectedWins,
            int expectedLosses,
            int expectedDraws
    ) {

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


        service.clear();


        long firstStarted =
                System.nanoTime();


        ThreePieceTablebase first =
                service.get(
                        majorType,
                        majorColor
                );


        long firstElapsedNanos =
                System.nanoTime()
                        - firstStarted;


        long firstMillis =
                firstElapsedNanos
                        / 1_000_000;


        if (!first.isBuilt()) {

            throw new AssertionError(
                    name
                            + " was returned without being built/loaded."
            );
        }


        if (first.getMajorType()
                != majorType) {

            throw new AssertionError(
                    name
                            + " returned wrong major piece type."
            );
        }


        if (first.getMajorColor()
                != majorColor) {

            throw new AssertionError(
                    name
                            + " returned wrong major-piece color."
            );
        }


        assertEquals(
                name + " legal states",
                expectedLegalStates,
                first.getLegalStateCount()
        );

        assertEquals(
                name + " wins",
                expectedWins,
                first.getWinCount()
        );

        assertEquals(
                name + " losses",
                expectedLosses,
                first.getLossCount()
        );

        assertEquals(
                name + " draws",
                expectedDraws,
                first.getDrawCount()
        );


        long secondStarted =
                System.nanoTime();


        ThreePieceTablebase second =
                service.get(
                        majorType,
                        majorColor
                );


        long secondElapsedNanos =
                System.nanoTime()
                        - secondStarted;


        long secondMicros =
                secondElapsedNanos
                        / 1_000;


        if (first != second) {

            throw new AssertionError(
                    name
                            + " was not reused from the service cache."
            );
        }


        if (!service.isBuilt(
                majorType,
                majorColor
        )) {

            throw new AssertionError(
                    name
                            + " isBuilt() returned false after load."
            );
        }


        System.out.println(
                "First load:        "
                        + firstMillis
                        + " ms"
        );

        System.out.println(
                "Cached load:       "
                        + secondMicros
                        + " us"
        );

        System.out.println(
                "Same object:       YES"
        );

        System.out.println(
                "Legal states:      "
                        + first.getLegalStateCount()
        );

        System.out.println(
                "WIN:               "
                        + first.getWinCount()
        );

        System.out.println(
                "LOSS:              "
                        + first.getLossCount()
        );

        System.out.println(
                "DRAW:              "
                        + first.getDrawCount()
        );


        /*
         * This is intentionally a generous threshold.
         *
         * A packaged resource should normally load in tens of milliseconds.
         * A retrograde fallback currently takes roughly 18-22 seconds, so
         * anything below 5 seconds is strong evidence that the resource path
         * was used.
         */
        if (firstMillis >= 5000) {

            throw new AssertionError(
                    name
                            + " took "
                            + firstMillis
                            + " ms to load. "
                            + "This strongly suggests that the packaged resource "
                            + "was not found and the service fell back to "
                            + "retrograde construction."
            );
        }


        System.out.println(
                "Resource loading:  PASS"
        );
    }


    private static void assertEquals(
            String label,
            int expected,
            int actual
    ) {

        if (expected != actual) {

            throw new AssertionError(
                    label
                            + " mismatch: expected "
                            + expected
                            + ", got "
                            + actual
                            + "."
            );
        }
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

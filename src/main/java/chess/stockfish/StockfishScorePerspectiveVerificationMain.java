package main.java.chess.stockfish;

import main.java.chess.model.Color;

import java.util.List;

public final class StockfishScorePerspectiveVerificationMain {

    private static int passed;
    private static int failed;


    private StockfishScorePerspectiveVerificationMain() {
    }


    public static void main(String[] args) {

        System.out.println("M67B2-EXPLICIT-EVALUATION-ADVANTAGE-V1");
        System.out.println("========================================");

        verifyCentipawnSigns();
        verifyMateSigns();
        verifyDisplayFormatting();

        System.out.println();
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);

        if (failed != 0) {
            throw new AssertionError(
                    "M67B2 explicit evaluation advantage verification failed."
            );
        }

        System.out.println();
        System.out.println(
                "M67B2 EXPLICIT EVALUATION ADVANTAGE PASSED"
        );
    }


    private static void verifyCentipawnSigns() {

        check(
                "White to move +33 remains White +33",
                33,
                StockfishScorePerspective.toWhiteCentipawns(
                        Color.WHITE,
                        33
                )
        );

        check(
                "White to move -33 remains White -33",
                -33,
                StockfishScorePerspective.toWhiteCentipawns(
                        Color.WHITE,
                        -33
                )
        );

        check(
                "Black to move +33 becomes White -33",
                -33,
                StockfishScorePerspective.toWhiteCentipawns(
                        Color.BLACK,
                        33
                )
        );

        check(
                "Black to move -33 becomes White +33",
                33,
                StockfishScorePerspective.toWhiteCentipawns(
                        Color.BLACK,
                        -33
                )
        );
    }


    private static void verifyMateSigns() {

        check(
                "White-to-move mate +4 remains White mate +4",
                4,
                StockfishScorePerspective.toWhiteMateScore(
                        Color.WHITE,
                        4
                )
        );

        check(
                "Black-to-move mate +4 becomes White mate -4",
                -4,
                StockfishScorePerspective.toWhiteMateScore(
                        Color.BLACK,
                        4
                )
        );

        check(
                "Black-to-move mate -4 becomes White mate +4",
                4,
                StockfishScorePerspective.toWhiteMateScore(
                        Color.BLACK,
                        -4
                )
        );
    }


    private static void verifyDisplayFormatting() {

        StockfishClient.Analysis cpAnalysis =
                new StockfishClient.Analysis(
                        "Stockfish",
                        "",
                        12,
                        -33,
                        null,
                        0L,
                        0L,
                        "d7d5",
                        null,
                        List.of()
                );

        check(
                "Black-to-move raw -0.33 displays as White +0.33",
                "White +0.33",
                StockfishScorePerspective.formatWhitePerspective(
                        Color.BLACK,
                        cpAnalysis
                )
        );

        StockfishClient.Analysis mateAnalysis =
                new StockfishClient.Analysis(
                        "Stockfish",
                        "",
                        20,
                        null,
                        3,
                        0L,
                        0L,
                        "a1a2",
                        null,
                        List.of()
                );

        check(
                "Black-to-move raw mate +3 displays Black mate in 3",
                "Black mate in 3",
                StockfishScorePerspective.formatWhitePerspective(
                        Color.BLACK,
                        mateAnalysis
                )
        );
    }


    private static void check(
            String label,
            Object expected,
            Object actual
    ) {

        boolean ok =
                expected == null
                        ? actual == null
                        : expected.equals(actual);

        System.out.printf(
                "%-58s %s%n",
                label,
                ok ? "PASS" : "FAIL"
        );

        if (!ok) {
            System.out.println("  expected: " + expected);
            System.out.println("  actual:   " + actual);
            failed++;
            return;
        }

        passed++;
    }
}

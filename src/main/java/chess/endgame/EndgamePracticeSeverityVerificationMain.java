package main.java.chess.endgame;

import main.java.chess.model.Board;
import main.java.chess.model.Color;
import main.java.chess.model.Move;
import main.java.chess.model.Piece;
import main.java.chess.model.PieceType;
import main.java.chess.model.Position;
import main.java.chess.model.Square;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;


public final class EndgamePracticeSeverityVerificationMain {

    private EndgamePracticeSeverityVerificationMain() {
    }


    public static void main(
            String[] args
    ) {

        ExactEndgameTablebase tablebase =
                ExactEndgameTablebase.tierZeroCatalog();


        EndgamePracticePolicy policy =
                new EndgamePracticePolicy(
                        tablebase,
                        new Random(
                                20260829L
                        )
                );


        Position position =
                winningKqbkPosition();


        System.out.println(
                "Endgame practice severity verification"
        );

        System.out.println(
                "======================================"
        );


        ExactEndgameTablebase.Probe current =
                tablebase.probe(
                        position
                );


        require(
                current.outcome()
                        == ExactEndgameTablebase.Outcome.WIN,
                "Expected KQBK fixture to be WIN."
        );


        System.out.println(
                "Position: KQBK WIN"
        );

        System.out.println(
                "Current DTM: "
                        + current.mateDistance()
        );


        List<EndgamePracticePolicy.PracticeMove>
                mistakes =
                policy.analyzeMistakes(
                        position
                );


        require(
                !mistakes.isEmpty(),
                "Expected classified practice mistakes."
        );


        Map<EndgamePracticePolicy.MistakeKind, Integer>
                counts =
                new EnumMap<>(
                        EndgamePracticePolicy.MistakeKind.class
                );


        for (EndgamePracticePolicy.PracticeMove move :
                mistakes) {

            counts.merge(
                    move.kind(),
                    1,
                    Integer::sum
            );
        }


        System.out.println();

        System.out.println(
                "Mistake spectrum"
        );


        for (EndgamePracticePolicy.MistakeKind kind :
                EndgamePracticePolicy.MistakeKind.values()) {

            int count =
                    counts.getOrDefault(
                            kind,
                            0
                    );


            System.out.println(
                    "  "
                            + kind
                            + ": "
                            + count
            );
        }


        System.out.println();

        System.out.println(
                "Ordered mistakes"
        );


        for (EndgamePracticePolicy.PracticeMove mistake :
                mistakes) {

            System.out.println(
                    "  #"
                            + mistake.severityRank()
                            + " "
                            + describe(
                            mistake.move()
                    )
                            + "  "
                            + mistake.kind()
                            + "  opponent "
                            + mistake.childOutcome()
                            + distanceText(
                            mistake
                    )
            );
        }


        verifyRankOrdering(
                mistakes
        );


        verifyStrength100(
                tablebase,
                policy,
                position
        );


        verifyStrengthBands(
                tablebase,
                policy,
                position
        );


        System.out.println();

        System.out.println(
                "SEVERITY-AWARE ENDGAME PRACTICE PASSED"
        );

        System.out.println(
                "PRACTICE MISTAKES NOW HAVE EXACT CHESS MEANING"
        );
    }


    private static void verifyRankOrdering(
            List<EndgamePracticePolicy.PracticeMove> mistakes
    ) {

        for (int index = 0;
             index < mistakes.size();
             index++) {

            require(
                    mistakes.get(
                            index
                    ).severityRank()
                            == index,
                    "Severity ranks are not contiguous."
            );
        }


        int previousKindSeverity =
                -1;


        for (EndgamePracticePolicy.PracticeMove mistake :
                mistakes) {

            int current =
                    kindSeverity(
                            mistake.kind()
                    );


            require(
                    current >= previousKindSeverity,
                    "Mistake categories are not ordered from mild to severe."
            );


            previousKindSeverity =
                    current;
        }


        System.out.println();

        System.out.println(
                "Severity ordering: PASSED"
        );
    }


    private static void verifyStrength100(
            ExactEndgameTablebase tablebase,
            EndgamePracticePolicy policy,
            Position position
    ) {

        List<Move> best =
                tablebase.bestMoves(
                        position
                );


        require(
                !best.isEmpty(),
                "Expected exact best moves."
        );


        for (int iteration = 0;
             iteration < 1_000;
             iteration++) {

            Move selected =
                    policy.chooseMove(
                            position,
                            100
                    );


            require(
                    selected != null,
                    "Strength 100 returned null."
            );


            require(
                    best.contains(
                            selected
                    ),
                    "Strength 100 selected a mistake."
            );
        }


        System.out.println(
                "Strength 100 perfect-play guarantee: PASSED"
        );
    }


    private static void verifyStrengthBands(
            ExactEndgameTablebase tablebase,
            EndgamePracticePolicy policy,
            Position position
    ) {

        List<EndgamePracticePolicy.PracticeMove>
                mistakes =
                policy.analyzeMistakes(
                        position
                );


        Map<Move, Integer> severityByMove =
                new HashMap<>();


        for (EndgamePracticePolicy.PracticeMove mistake :
                mistakes) {

            severityByMove.put(
                    mistake.move(),
                    mistake.severityRank()
            );
        }


        sampleStrength(
                tablebase,
                policy,
                position,
                severityByMove,
                90,
                10_000
        );


        sampleStrength(
                tablebase,
                policy,
                position,
                severityByMove,
                50,
                10_000
        );


        sampleStrength(
                tablebase,
                policy,
                position,
                severityByMove,
                10,
                10_000
        );


        sampleStrength(
                tablebase,
                policy,
                position,
                severityByMove,
                0,
                10_000
        );
    }


    private static void sampleStrength(
            ExactEndgameTablebase tablebase,
            EndgamePracticePolicy policy,
            Position position,
            Map<Move, Integer> severityByMove,
            int strength,
            int samples
    ) {

        List<Move> best =
                tablebase.bestMoves(
                        position
                );


        int optimal =
                0;

        int mistakes =
                0;

        int maximumObservedSeverity =
                -1;

        long totalSeverity =
                0;


        for (int iteration = 0;
             iteration < samples;
             iteration++) {

            Move selected =
                    policy.chooseMove(
                            position,
                            strength
                    );


            require(
                    selected != null,
                    "Practice policy returned null."
            );


            if (best.contains(
                    selected
            )) {

                optimal++;
                continue;
            }


            mistakes++;


            Integer severity =
                    severityByMove.get(
                            selected
                    );


            require(
                    severity != null,
                    "Selected non-optimal move has no severity classification."
            );


            maximumObservedSeverity =
                    Math.max(
                            maximumObservedSeverity,
                            severity
                    );


            totalSeverity +=
                    severity;
        }


        double optimalPercent =
                100.0
                        * optimal
                        / samples;


        double averageSeverity =
                mistakes == 0
                        ? 0.0
                        : (double) totalSeverity
                        / mistakes;


        System.out.println();

        System.out.println(
                "Strength "
                        + strength
        );

        System.out.printf(
                "  Optimal: %d / %d (%.2f%%)%n",
                optimal,
                samples,
                optimalPercent
        );

        System.out.println(
                "  Mistakes: "
                        + mistakes
        );

        System.out.printf(
                "  Average mistake severity rank: %.2f%n",
                averageSeverity
        );

        System.out.println(
                "  Maximum observed severity rank: "
                        + maximumObservedSeverity
        );


        if (strength == 0) {

            require(
                    optimal == 0,
                    "Strength 0 should use the mistake pool when mistakes exist."
            );
        }


        if (strength == 100) {

            require(
                    mistakes == 0,
                    "Strength 100 must never make a mistake."
            );
        }
    }


    private static Position winningKqbkPosition() {

        return position(
                Color.WHITE,

                piece(
                        "a1",
                        PieceType.KING,
                        Color.WHITE
                ),

                piece(
                        "g8",
                        PieceType.KING,
                        Color.BLACK
                ),

                piece(
                        "d4",
                        PieceType.QUEEN,
                        Color.WHITE
                ),

                piece(
                        "e4",
                        PieceType.BISHOP,
                        Color.WHITE
                )
        );
    }


    private static Position position(
            Color sideToMove,
            PlacedPiece... pieces
    ) {

        Board board =
                new Board();


        for (PlacedPiece placed :
                pieces) {

            board.setPiece(
                    placed.square(),
                    placed.piece()
            );
        }


        return new Position(
                board,
                sideToMove,
                false,
                false,
                false,
                false,
                null,
                0,
                1,
                new HashMap<>()
        );
    }


    private static PlacedPiece piece(
            String square,
            PieceType type,
            Color color
    ) {

        return new PlacedPiece(
                Square.fromAlgebraic(
                        square
                ),
                new Piece(
                        type,
                        color
                )
        );
    }


    private static int kindSeverity(
            EndgamePracticePolicy.MistakeKind kind
    ) {

        return switch (kind) {

            case SLOWER_WIN ->
                    0;

            case SHORTER_RESISTANCE ->
                    1;

            case WIN_TO_DRAW ->
                    2;

            case DRAW_TO_LOSS ->
                    3;

            case WIN_TO_LOSS ->
                    4;
        };
    }


    private static String distanceText(
            EndgamePracticePolicy.PracticeMove move
    ) {

        if (move.childMateDistance()
                < 0) {

            return "";
        }


        return " DTM "
                + move.childMateDistance();
    }


    private static String describe(
            Move move
    ) {

        String description =
                move.from()
                        .toAlgebraic()
                        + " -> "
                        + move.to()
                        .toAlgebraic();


        if (move.isPromotion()) {

            description +=
                    " = "
                            + move.promotion();
        }


        return description;
    }


    private static void require(
            boolean condition,
            String message
    ) {

        if (!condition) {

            throw new IllegalStateException(
                    message
            );
        }
    }


    private record PlacedPiece(
            Square square,
            Piece piece
    ) {
    }
}
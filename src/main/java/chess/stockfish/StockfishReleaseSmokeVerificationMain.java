package main.java.chess.stockfish;

import main.java.chess.model.FenCodec;
import main.java.chess.model.Move;
import main.java.chess.model.PieceType;
import main.java.chess.model.Position;
import main.java.chess.rules.MoveGenerator;

import java.time.Duration;
import java.util.List;


/**
 * M87 fast release smoke test for the real Stockfish process boundary.
 *
 * This is deliberately much smaller than M81 calibration:
 *
 *   - starts the configured/discovered Stockfish executable,
 *   - verifies the frozen M81 API build,
 *   - analyzes one forced mate-in-one position,
 *   - verifies Stockfish returns the legal mating move Qxf7#,
 *   - verifies mate/PV/depth data are received through the UCI client.
 *
 * No search calibration or engine tuning occurs here.
 */
public final class StockfishReleaseSmokeVerificationMain {

    public static final String BUILD_ID =
            "M87-STOCKFISH-RELEASE-SMOKE-V1";

    private static final String MATE_IN_ONE_FEN =
            "r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4";

    private static final int DEPTH =
            8;

    private static final Duration TIMEOUT =
            Duration.ofSeconds(
                    30
            );


    private StockfishReleaseSmokeVerificationMain() {
    }


    public static void main(
            String[] args
    ) throws Exception {

        if (args.length != 0) {
            throw new IllegalArgumentException(
                    "Usage: StockfishReleaseSmokeVerificationMain"
            );
        }


        System.out.println(
                "M87 Stockfish release smoke gate"
        );

        System.out.println(
                "================================"
        );

        System.out.println(
                "BUILD_ID: "
                        + BUILD_ID
        );

        System.out.println(
                "Stockfish API BUILD: "
                        + StockfishClient.CALIBRATION_BUILD_ID
        );


        require(
                "M81-COMMON-DEPTH-MULTIPV-V1"
                        .equals(
                                StockfishClient.CALIBRATION_BUILD_ID
                        ),
                "M81 Stockfish API build changed."
        );


        Position position =
                FenCodec.parse(
                        MATE_IN_ONE_FEN
                );

        List<Move> legalMoves =
                new MoveGenerator()
                        .generateLegalMoves(
                                position
                        );


        try (StockfishClient stockfish =
                     StockfishClient.createConfiguredClient()) {

            stockfish.start();

            System.out.println(
                    "Engine: "
                            + stockfish.getEngineName()
            );

            System.out.println(
                    "Executable: "
                            + stockfish.getExecutable()
            );


            require(
                    stockfish.isRunning(),
                    "Stockfish process is not running after start()."
            );


            StockfishClient.Analysis analysis =
                    stockfish.analyzeFen(
                            MATE_IN_ONE_FEN,
                            DEPTH,
                            TIMEOUT
                    );


            System.out.println(
                    "Depth: "
                            + analysis.depth()
            );

            System.out.println(
                    "Score: "
                            + analysis.scoreDisplay()
            );

            System.out.println(
                    "Best move: "
                            + analysis.bestMove()
            );

            System.out.println(
                    "PV: "
                            + String.join(
                            " ",
                            analysis.principalVariation()
                    )
            );


            require(
                    analysis.depth() > 0,
                    "Stockfish returned no completed analysis depth."
            );

            require(
                    "h5f7".equalsIgnoreCase(
                            analysis.bestMove()
                    ),
                    "Stockfish did not return forced mate Qxf7#. Found: "
                            + analysis.bestMove()
            );

            require(
                    containsMove(
                            legalMoves,
                            analysis.bestMove()
                    ),
                    "Stockfish best move is not legal in the root position."
            );

            require(
                    analysis.hasMateScore(),
                    "Forced mate fixture did not return a mate score."
            );

            require(
                    analysis.mateIn() != null
                            && analysis.mateIn() > 0,
                    "Forced mate score is not winning for the root side."
            );

            require(
                    !analysis.principalVariation()
                            .isEmpty(),
                    "Stockfish returned an empty principal variation."
            );
        }


        System.out.println();

        System.out.println(
                "M81 Stockfish API preserved PASSED"
        );

        System.out.println(
                "Stockfish process startup PASSED"
        );

        System.out.println(
                "Forced Qxf7# analysis PASSED"
        );

        System.out.println(
                "M87 STOCKFISH RELEASE SMOKE GATE PASSED"
        );
    }


    private static boolean containsMove(
            List<Move> legalMoves,
            String uci
    ) {

        if (uci == null) {
            return false;
        }


        for (Move move :
                legalMoves) {

            if (moveText(
                    move
            ).equalsIgnoreCase(
                    uci
            )) {

                return true;
            }
        }


        return false;
    }


    private static String moveText(
            Move move
    ) {

        String text =
                move.from()
                        .toAlgebraic()
                        + move.to()
                        .toAlgebraic();


        if (move.promotion()
                != null) {

            text +=
                    promotionLetter(
                            move.promotion()
                    );
        }


        return text;
    }


    private static char promotionLetter(
            PieceType pieceType
    ) {

        return switch (pieceType) {
            case QUEEN -> 'q';
            case ROOK -> 'r';
            case BISHOP -> 'b';
            case KNIGHT -> 'n';
            default ->
                    throw new IllegalArgumentException(
                            "Unsupported promotion type: "
                                    + pieceType
                    );
        };
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
}

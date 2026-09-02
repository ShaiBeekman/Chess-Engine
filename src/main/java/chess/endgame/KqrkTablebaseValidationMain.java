package main.java.chess.endgame;

import main.java.chess.model.Color;
import main.java.chess.model.Move;
import main.java.chess.model.Position;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;


/**
 * Validation gate for a persisted exact KQRK tablebase.
 *
 * Validates:
 *   1. persisted asset load
 *   2. sampled probe consistency
 *   3. exact WDL recurrence
 *   4. exact DTM recurrence
 *   5. FourPieceTablebase.bestMoves() consistency
 *
 * Default:
 *   KQRK-white-v1.tb
 *   100,000 random legal positions
 */
public final class KqrkTablebaseValidationMain {

    private static final int DEFAULT_SAMPLES = 100_000;


    public static void main(String[] args)
            throws Exception {

        Color strongColor =
                args.length > 0
                        && args[0].equalsIgnoreCase("black")
                        ? Color.BLACK
                        : Color.WHITE;

        int samples =
                args.length > 1
                        ? Integer.parseInt(args[1])
                        : DEFAULT_SAMPLES;

        String colorName =
                strongColor == Color.WHITE
                        ? "white"
                        : "black";

        Path path =
                Path.of(
                        "src",
                        "main",
                        "resources",
                        "tablebases",
                        "KQRK-" + colorName + "-v1.tb"
                );

        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException(
                    "Missing KQRK asset: "
                            + path.toAbsolutePath()
            );
        }

        long loadStarted =
                System.currentTimeMillis();

        FourPieceTablebase tablebase =
                FourPieceTablebaseCodec.load(path);

        long loadMillis =
                System.currentTimeMillis()
                        - loadStarted;

        if (!tablebase.isBuilt()) {
            throw new IllegalStateException(
                    "Loaded KQRK tablebase is not marked built."
            );
        }

        System.out.println(
                "Loaded: " + path.toAbsolutePath()
        );
        System.out.println(
                "Asset bytes: " + Files.size(path)
        );
        System.out.println(
                "Load ms: " + loadMillis
        );

        validate(
                tablebase,
                strongColor,
                samples
        );
    }


    private static void validate(
            FourPieceTablebase tablebase,
            Color strongColor,
            int samples
    ) {
        boolean strongIsWhite =
                strongColor == Color.WHITE;

        Random random =
                new Random(
                        strongColor == Color.WHITE
                                ? 0x4B51525601L
                                : 0x4B51525602L
                );

        KqrkPrimitiveMoveGenerator.Buffer primitive =
                new KqrkPrimitiveMoveGenerator.Buffer(64);

        ThreePieceTablebaseService threePieceService =
                new ThreePieceTablebaseService();

        ThreePieceTablebase kqk =
                threePieceService.get(
                        main.java.chess.model.PieceType.QUEEN,
                        strongColor
                );

        ThreePieceTablebase krk =
                threePieceService.get(
                        main.java.chess.model.PieceType.ROOK,
                        strongColor
                );

        int checked = 0;
        long attempts = 0;

        int winChecked = 0;
        int lossChecked = 0;
        int drawChecked = 0;

        int wdlFailures = 0;
        int dtmFailures = 0;
        int bestMoveFailures = 0;
        int probeFailures = 0;

        while (checked < samples) {

            attempts++;

            int state =
                    random.nextInt(
                            KqrkPrimitiveState.STATE_COUNT
                    );

            if (!KqrkPrimitiveRules.isStructurallyLegal(
                    state,
                    strongIsWhite
            )) {
                continue;
            }

            Position position =
                    tablebase.positionForState(state);

            FourPieceTablebase.Probe probe =
                    tablebase.probe(position);

            if (probe.outcome()
                    == FourPieceTablebase.Outcome.UNSUPPORTED) {
                probeFailures++;
                printFailure(
                        "UNSUPPORTED LEGAL STATE",
                        state,
                        probe,
                        null
                );
                checked++;
                continue;
            }

            int count =
                    KqrkPrimitiveMoveGenerator
                            .generateLegalSuccessors(
                                    state,
                                    strongIsWhite,
                                    primitive
                            );

            ChildSummary summary =
                    summarizeChildren(
                            state,
                            count,
                            primitive,
                            tablebase,
                            kqk,
                            krk,
                            strongIsWhite
                    );

            switch (probe.outcome()) {

                case WIN -> {
                    winChecked++;

                    if (!summary.hasLoss
                            || summary.minLossDistance
                            == Integer.MAX_VALUE) {
                        wdlFailures++;
                        printFailure(
                                "WIN WITHOUT LOSS CHILD",
                                state,
                                probe,
                                summary
                        );
                    } else {
                        int expected =
                                summary.minLossDistance + 1;

                        if (probe.mateDistance()
                                != expected) {
                            dtmFailures++;
                            printFailure(
                                    "WIN DTM MISMATCH",
                                    state,
                                    probe,
                                    summary
                            );
                        }
                    }
                }

                case LOSS -> {
                    lossChecked++;

                    if (count == 0) {
                        if (probe.mateDistance() != 0) {
                            dtmFailures++;
                            printFailure(
                                    "TERMINAL LOSS DTM != 0",
                                    state,
                                    probe,
                                    summary
                            );
                        }
                    } else {
                        if (!summary.allWin) {
                            wdlFailures++;
                            printFailure(
                                    "LOSS HAS NON-WIN CHILD",
                                    state,
                                    probe,
                                    summary
                            );
                        } else {
                            int expected =
                                    summary.maxWinDistance + 1;

                            if (probe.mateDistance()
                                    != expected) {
                                dtmFailures++;
                                printFailure(
                                        "LOSS DTM MISMATCH",
                                        state,
                                        probe,
                                        summary
                                );
                            }
                        }
                    }
                }

                case DRAW -> {
                    drawChecked++;

                    /*
                     * Nonterminal DRAW positions must have at least one
                     * draw-preserving child.  Terminal stalemate is allowed
                     * to have zero children.
                     */
                    if (count > 0
                            && !summary.hasDraw) {
                        wdlFailures++;
                        printFailure(
                                "DRAW WITHOUT DRAW CHILD",
                                state,
                                probe,
                                summary
                        );
                    }

                    if (probe.mateDistance() >= 0) {
                        dtmFailures++;
                        printFailure(
                                "DRAW HAS MATE DISTANCE",
                                state,
                                probe,
                                summary
                        );
                    }
                }

                default -> {
                    probeFailures++;
                }
            }

            if (!bestMovesAgree(
                    tablebase,
                    position,
                    state,
                    probe,
                    primitive,
                    count,
                    kqk,
                    krk,
                    strongIsWhite
            )) {
                bestMoveFailures++;

                if (bestMoveFailures <= 10) {
                    System.out.println();
                    System.out.println(
                            "BEST MOVE MISMATCH"
                    );
                    System.out.println(
                            "State: " + state
                    );
                    System.out.println(
                            "Probe: " + probe
                    );
                }
            }

            checked++;

            if (checked % 10_000 == 0) {
                System.out.println(
                        "  validated "
                                + checked
                                + " / "
                                + samples
                );
            }
        }

        System.out.println();
        System.out.println(
                "KQRK TABLEBASE VALIDATION — strong "
                        + strongColor
        );
        System.out.println(
                "Legal positions checked: " + checked
        );
        System.out.println(
                "Random raw attempts: " + attempts
        );
        System.out.println(
                "WIN checked: " + winChecked
        );
        System.out.println(
                "LOSS checked: " + lossChecked
        );
        System.out.println(
                "DRAW checked: " + drawChecked
        );
        System.out.println(
                "Probe failures: " + probeFailures
        );
        System.out.println(
                "WDL recurrence failures: " + wdlFailures
        );
        System.out.println(
                "DTM recurrence failures: " + dtmFailures
        );
        System.out.println(
                "Best-move failures: " + bestMoveFailures
        );

        if (probeFailures != 0
                || wdlFailures != 0
                || dtmFailures != 0
                || bestMoveFailures != 0) {
            throw new IllegalStateException(
                    "KQRK tablebase validation failed."
            );
        }

        System.out.println(
                "KQRK TABLEBASE VALIDATION PASSED"
        );
    }


    private static ChildSummary summarizeChildren(
            int parentState,
            int count,
            KqrkPrimitiveMoveGenerator.Buffer primitive,
            FourPieceTablebase fourPiece,
            ThreePieceTablebase kqk,
            ThreePieceTablebase krk,
            boolean strongIsWhite
    ) {
        boolean hasLoss = false;
        boolean hasDraw = false;
        boolean allWin = count > 0;

        int minLossDistance =
                Integer.MAX_VALUE;

        int maxWinDistance = -1;

        for (int i = 0; i < count; i++) {

            ChildProbe child =
                    probeChild(
                            parentState,
                            primitive,
                            i,
                            fourPiece,
                            kqk,
                            krk,
                            strongIsWhite
                    );

            if (child.outcome
                    == FourPieceTablebase.Outcome.LOSS) {
                hasLoss = true;
                allWin = false;
                minLossDistance =
                        Math.min(
                                minLossDistance,
                                child.distance
                        );

            } else if (child.outcome
                    == FourPieceTablebase.Outcome.WIN) {
                maxWinDistance =
                        Math.max(
                                maxWinDistance,
                                child.distance
                        );

            } else if (child.outcome
                    == FourPieceTablebase.Outcome.DRAW) {
                hasDraw = true;
                allWin = false;

            } else {
                allWin = false;
                throw new IllegalStateException(
                        "Unsupported child while validating state "
                                + parentState
                );
            }
        }

        return new ChildSummary(
                hasLoss,
                hasDraw,
                allWin,
                minLossDistance,
                maxWinDistance
        );
    }


    private static ChildProbe probeChild(
            int parentState,
            KqrkPrimitiveMoveGenerator.Buffer primitive,
            int index,
            FourPieceTablebase fourPiece,
            ThreePieceTablebase kqk,
            ThreePieceTablebase krk,
            boolean strongIsWhite
    ) {
        int boundary =
                primitive.boundaryType(index);

        if (boundary
                == KqrkPrimitiveMoveGenerator.BOUNDARY_NONE) {

            int childState =
                    primitive.state(index);

            Position childPosition =
                    fourPiece.positionForState(
                            childState
                    );

            FourPieceTablebase.Probe probe =
                    fourPiece.probe(
                            childPosition
                    );

            return new ChildProbe(
                    probe.outcome(),
                    probe.mateDistance()
            );
        }

        int surviving =
                primitive.survivingPieceSquare(index);

        int wk =
                KqrkPrimitiveState.whiteKing(
                        parentState
                );

        int bk =
                KqrkPrimitiveState.blackKing(
                        parentState
                );

        boolean childBlackToMove =
                !KqrkPrimitiveState.blackToMove(
                        parentState
                );

        ThreePieceTablebase.Probe probe;

        if (boundary
                == KqrkPrimitiveMoveGenerator.BOUNDARY_KRK) {

            if (strongIsWhite) {
                bk = KqrkPrimitiveState.queen(
                        parentState
                );
            } else {
                wk = KqrkPrimitiveState.queen(
                        parentState
                );
            }

            probe =
                    krk.probeSquares(
                            wk,
                            bk,
                            surviving,
                            childBlackToMove
                    );

        } else if (boundary
                == KqrkPrimitiveMoveGenerator.BOUNDARY_KQK) {

            if (strongIsWhite) {
                bk = KqrkPrimitiveState.rook(
                        parentState
                );
            } else {
                wk = KqrkPrimitiveState.rook(
                        parentState
                );
            }

            probe =
                    kqk.probeSquares(
                            wk,
                            bk,
                            surviving,
                            childBlackToMove
                    );

        } else {
            return new ChildProbe(
                    FourPieceTablebase.Outcome.DRAW,
                    -1
            );
        }

        FourPieceTablebase.Outcome mapped =
                switch (probe.outcome()) {
                    case WIN ->
                            FourPieceTablebase.Outcome.WIN;
                    case LOSS ->
                            FourPieceTablebase.Outcome.LOSS;
                    case DRAW ->
                            FourPieceTablebase.Outcome.DRAW;
                    default ->
                            FourPieceTablebase.Outcome.UNSUPPORTED;
                };

        return new ChildProbe(
                mapped,
                probe.mateDistance()
        );
    }


    private static boolean bestMovesAgree(
            FourPieceTablebase tablebase,
            Position position,
            int state,
            FourPieceTablebase.Probe parentProbe,
            KqrkPrimitiveMoveGenerator.Buffer primitive,
            int primitiveCount,
            ThreePieceTablebase kqk,
            ThreePieceTablebase krk,
            boolean strongIsWhite
    ) {
        List<Move> actualMoves =
                tablebase.bestMoves(position);

        /*
         * Terminal positions should have no best moves.
         */
        if (primitiveCount == 0) {
            return actualMoves.isEmpty();
        }

        Set<String> expectedChildren =
                new HashSet<>();

        int targetDistance;

        if (parentProbe.outcome()
                == FourPieceTablebase.Outcome.WIN) {
            targetDistance =
                    parentProbe.mateDistance() - 1;

            for (int i = 0; i < primitiveCount; i++) {
                ChildProbe child =
                        probeChild(
                                state,
                                primitive,
                                i,
                                tablebase,
                                kqk,
                                krk,
                                strongIsWhite
                        );

                if (child.outcome
                        == FourPieceTablebase.Outcome.LOSS
                        && child.distance == targetDistance) {
                    expectedChildren.add(
                            primitiveChildKey(
                                    state,
                                    primitive,
                                    i,
                                    strongIsWhite
                            )
                    );
                }
            }

        } else if (parentProbe.outcome()
                == FourPieceTablebase.Outcome.LOSS) {
            targetDistance =
                    parentProbe.mateDistance() - 1;

            for (int i = 0; i < primitiveCount; i++) {
                ChildProbe child =
                        probeChild(
                                state,
                                primitive,
                                i,
                                tablebase,
                                kqk,
                                krk,
                                strongIsWhite
                        );

                if (child.outcome
                        == FourPieceTablebase.Outcome.WIN
                        && child.distance == targetDistance) {
                    expectedChildren.add(
                            primitiveChildKey(
                                    state,
                                    primitive,
                                    i,
                                    strongIsWhite
                            )
                    );
                }
            }

        } else if (parentProbe.outcome()
                == FourPieceTablebase.Outcome.DRAW) {

            for (int i = 0; i < primitiveCount; i++) {
                ChildProbe child =
                        probeChild(
                                state,
                                primitive,
                                i,
                                tablebase,
                                kqk,
                                krk,
                                strongIsWhite
                        );

                if (child.outcome
                        == FourPieceTablebase.Outcome.DRAW) {
                    expectedChildren.add(
                            primitiveChildKey(
                                    state,
                                    primitive,
                                    i,
                                    strongIsWhite
                            )
                    );
                }
            }

        } else {
            return false;
        }

        Set<String> actualChildren =
                new HashSet<>();

        for (Move move : actualMoves) {
            Position child =
                    position.makeMove(move);

            actualChildren.add(
                    objectChildKey(
                            child,
                            tablebase,
                            strongIsWhite
                    )
            );
        }

        return expectedChildren.equals(
                actualChildren
        );
    }


    private static String primitiveChildKey(
            int parentState,
            KqrkPrimitiveMoveGenerator.Buffer primitive,
            int index,
            boolean strongIsWhite
    ) {
        int boundary =
                primitive.boundaryType(index);

        if (boundary
                == KqrkPrimitiveMoveGenerator.BOUNDARY_NONE) {
            return "S:"
                    + primitive.state(index);
        }

        int surviving =
                primitive.survivingPieceSquare(index);

        int wk =
                KqrkPrimitiveState.whiteKing(
                        parentState
                );

        int bk =
                KqrkPrimitiveState.blackKing(
                        parentState
                );

        if (boundary
                == KqrkPrimitiveMoveGenerator.BOUNDARY_KRK) {
            if (strongIsWhite) {
                bk = KqrkPrimitiveState.queen(
                        parentState
                );
            } else {
                wk = KqrkPrimitiveState.queen(
                        parentState
                );
            }

            return "KRK:"
                    + wk + ":"
                    + bk + ":"
                    + surviving;

        } else if (boundary
                == KqrkPrimitiveMoveGenerator.BOUNDARY_KQK) {
            if (strongIsWhite) {
                bk = KqrkPrimitiveState.rook(
                        parentState
                );
            } else {
                wk = KqrkPrimitiveState.rook(
                        parentState
                );
            }

            return "KQK:"
                    + wk + ":"
                    + bk + ":"
                    + surviving;
        }

        return "KK";
    }


    private static String objectChildKey(
            Position child,
            FourPieceTablebase tablebase,
            boolean strongIsWhite
    ) {
        int encoded =
                tablebase.encodePosition(child);

        if (encoded >= 0) {
            return "S:" + encoded;
        }

        int wk = -1;
        int bk = -1;
        int q = -1;
        int r = -1;

        for (int rank = 0; rank < 8; rank++) {
            for (int file = 0; file < 8; file++) {

                main.java.chess.model.Square square =
                        new main.java.chess.model.Square(
                                file,
                                rank
                        );

                main.java.chess.model.Piece piece =
                        child.getBoard().getPiece(square);

                if (piece == null) {
                    continue;
                }

                int encodedSquare =
                        rank * 8 + file;

                switch (piece.type()) {
                    case KING -> {
                        if (piece.color()
                                == Color.WHITE) {
                            wk = encodedSquare;
                        } else {
                            bk = encodedSquare;
                        }
                    }
                    case QUEEN -> q = encodedSquare;
                    case ROOK -> r = encodedSquare;
                    default -> {
                    }
                }
            }
        }

        if (q >= 0 && r < 0) {
            return "KQK:"
                    + wk + ":"
                    + bk + ":"
                    + q;
        }

        if (r >= 0 && q < 0) {
            return "KRK:"
                    + wk + ":"
                    + bk + ":"
                    + r;
        }

        return "KK";
    }


    private static void printFailure(
            String kind,
            int state,
            FourPieceTablebase.Probe probe,
            ChildSummary summary
    ) {
        System.out.println();
        System.out.println(kind);
        System.out.println(
                "State: " + state
        );
        System.out.println(
                "Probe: " + probe
        );

        if (summary != null) {
            System.out.println(
                    "Children: " + summary
            );
        }
    }


    private record ChildProbe(
            FourPieceTablebase.Outcome outcome,
            int distance
    ) {
    }


    private record ChildSummary(
            boolean hasLoss,
            boolean hasDraw,
            boolean allWin,
            int minLossDistance,
            int maxWinDistance
    ) {
    }
}

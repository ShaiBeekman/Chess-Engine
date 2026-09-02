package main.java.chess.endgame;

import main.java.chess.model.Move;
import main.java.chess.model.Position;
import main.java.chess.rules.MoveGenerator;

import java.util.Arrays;
import java.util.List;


/**
 * Memory-conscious exact retrograde builder for the first four-piece
 * material class (KQRK).
 *
 * Unlike the 3-piece builder, this deliberately does not construct a giant
 * predecessor CSR graph.  KQRK has 33,554,432 raw states, so storing every
 * reverse edge would dominate memory.
 *
 * Instead, this builder uses monotone retrograde sweeps:
 *
 *   LOSS: no legal move and checkmated, or every legal child is WIN.
 *   WIN : at least one legal child is LOSS.
 *   DRAW: every state left unresolved after the fixpoint.
 *
 * Exact distance-to-mate semantics are preserved:
 *
 *   WIN  distance = 1 + minimum LOSS-child distance.
 *   LOSS distance = 1 + maximum WIN-child distance.
 *
 * The tradeoff is intentional: much lower peak memory in exchange for more
 * CPU during the offline build.  Once saved, runtime lookup is O(1).
 */
public final class FourPieceRetrogradeBuilder {

    private final FourPieceTablebase tablebase;
    private final MoveGenerator moveGenerator;


    public FourPieceRetrogradeBuilder(
            FourPieceTablebase tablebase
    ) {

        if (tablebase == null) {

            throw new IllegalArgumentException(
                    "Four-piece tablebase cannot be null."
            );
        }


        if (!"KQRK".equals(
                tablebase.materialName()
        )) {

            throw new IllegalArgumentException(
                    "Milestone 2 builder currently supports only KQRK."
            );
        }


        this.tablebase =
                tablebase;

        this.moveGenerator =
                new MoveGenerator();
    }


    public BuildResult build() {

        long started =
                System.currentTimeMillis();


        int stateCount =
                tablebase.stateCount();


        byte[] outcome =
                new byte[
                        stateCount
                        ];


        short[] distance =
                new short[
                        stateCount
                        ];


        Arrays.fill(
                outcome,
                FourPieceTablebase.INVALID
        );

        Arrays.fill(
                distance,
                (short) -1
        );


        int legalStates =
                0;

        int terminalLosses =
                0;


        System.out.println(
                "KQRK retrograde: classifying "
                        + stateCount
                        + " raw states..."
        );


        /*
         * Pass 0:
         *
         * Identify structurally legal states and seed terminal positions.
         * A legal no-move position is LOSS exactly when it is checkmate.
         * We distinguish mate from stalemate by using the exact chess fact
         * that a no-move state is mate iff the side to move is in check.
         *
         * For KQRK, structural legality already rejects positions in which
         * the previous mover left its own king attacked.  To avoid coupling
         * this offline builder to a private AttackDetector API, the mate
         * seed is detected through child-free positions plus the local
         * "null side flip" test below.
         */
        for (int state = 0;
             state < stateCount;
             state++) {

            if (!tablebase.isLegalState(
                    state
            )) {

                continue;
            }


            outcome[state] =
                    FourPieceTablebase.UNKNOWN;

            legalStates++;


            Position position =
                    tablebase.positionForState(
                            state
                    );


            List<Move> moves =
                    moveGenerator.generateLegalMoves(
                            position
                    );


            if (!moves.isEmpty()) {

                continue;
            }


            if (isCheckmate(
                    position
            )) {

                outcome[state] =
                        FourPieceTablebase.LOSS;

                distance[state] =
                        0;

                terminalLosses++;

            } else {

                /*
                 * Stalemate is already an exact draw and never participates
                 * in mate propagation.
                 */
                outcome[state] =
                        FourPieceTablebase.DRAW;
            }
        }


        System.out.println(
                "Legal states: "
                        + legalStates
                        + ", terminal mates: "
                        + terminalLosses
        );


        int sweep =
                0;

        long totalResolved =
                terminalLosses;


        /*
         * Monotone fixed-point iteration.  Each sweep only turns UNKNOWN
         * states into WIN or LOSS; solved states never change.
         */
        while (true) {

            sweep++;

            int resolvedThisSweep =
                    0;


            long sweepStarted =
                    System.currentTimeMillis();


            for (int state = 0;
                 state < stateCount;
                 state++) {

                if (outcome[state]
                        != FourPieceTablebase.UNKNOWN) {

                    continue;
                }


                Position position =
                        tablebase.positionForState(
                                state
                        );


                List<Move> legalMoves =
                        moveGenerator.generateLegalMoves(
                                position
                        );


                boolean foundLossChild =
                        false;

                int minimumLossDistance =
                        Integer.MAX_VALUE;


                boolean allChildrenWin =
                        true;

                int maximumWinDistance =
                        -1;


                for (Move move :
                        legalMoves) {

                    Position next =
                            position.makeMove(
                                    move
                            );


                    Child child =
                            classifyChild(
                                    next,
                                    outcome,
                                    distance
                            );


                    if (child.outcome()
                            == FourPieceTablebase.LOSS) {

                        foundLossChild =
                                true;

                        minimumLossDistance =
                                Math.min(
                                        minimumLossDistance,
                                        child.distance()
                                );
                    }


                    if (child.outcome()
                            != FourPieceTablebase.WIN) {

                        allChildrenWin =
                                false;

                    } else {

                        maximumWinDistance =
                                Math.max(
                                        maximumWinDistance,
                                        child.distance()
                                );
                    }
                }


                if (foundLossChild) {

                    outcome[state] =
                            FourPieceTablebase.WIN;

                    distance[state] =
                            safeShort(
                                    minimumLossDistance
                                            + 1
                            );

                    resolvedThisSweep++;

                } else if (!legalMoves.isEmpty()
                        && allChildrenWin) {

                    outcome[state] =
                            FourPieceTablebase.LOSS;

                    distance[state] =
                            safeShort(
                                    maximumWinDistance
                                            + 1
                            );

                    resolvedThisSweep++;
                }
            }


            totalResolved +=
                    resolvedThisSweep;


            System.out.println(
                    "Sweep "
                            + sweep
                            + ": resolved "
                            + resolvedThisSweep
                            + " states in "
                            + (System.currentTimeMillis()
                            - sweepStarted)
                            + " ms"
            );


            if (resolvedThisSweep == 0) {
                break;
            }
        }


        int wins =
                0;

        int losses =
                0;

        int draws =
                0;


        for (int state = 0;
             state < stateCount;
             state++) {

            if (outcome[state]
                    == FourPieceTablebase.UNKNOWN) {

                outcome[state] =
                        FourPieceTablebase.DRAW;
            }


            switch (outcome[state]) {

                case FourPieceTablebase.WIN ->
                        wins++;

                case FourPieceTablebase.LOSS ->
                        losses++;

                case FourPieceTablebase.DRAW ->
                        draws++;

                default -> {
                }
            }
        }


        tablebase.restoreFromPersistence(
                outcome,
                distance
        );


        long elapsed =
                System.currentTimeMillis()
                        - started;


        return new BuildResult(
                legalStates,
                wins,
                losses,
                draws,
                sweep,
                elapsed
        );
    }


    private Child classifyChild(
            Position position,
            byte[] outcome,
            short[] distance
    ) {

        int childState =
                tablebase.encodePosition(
                        position
                );


        if (childState >= 0) {

            byte childOutcome =
                    outcome[
                            childState
                            ];


            if (childOutcome
                    == FourPieceTablebase.INVALID
                    ||
                    childOutcome
                            == FourPieceTablebase.UNKNOWN) {

                return new Child(
                        FourPieceTablebase.UNKNOWN,
                        -1
                );
            }


            return new Child(
                    childOutcome,
                    distance[
                            childState
                            ]
            );
        }


        /*
         * Leaving KQRK means a major piece was captured.  The tablebase's
         * public probe routes KQK/KRK into the already-exact 3-piece service.
         */
        FourPieceTablebase.Probe probe =
                tablebase.probe(
                        position
                );


        return new Child(
                switch (probe.outcome()) {

                    case WIN ->
                            FourPieceTablebase.WIN;

                    case LOSS ->
                            FourPieceTablebase.LOSS;

                    case DRAW ->
                            FourPieceTablebase.DRAW;

                    case UNSUPPORTED ->
                            FourPieceTablebase.UNKNOWN;
                },
                probe.mateDistance()
        );
    }


    private boolean isCheckmate(
            Position position
    ) {

        /*
         * Position.makeMove cannot be used for a null move, so check is
         * determined geometrically by asking whether the same board would
         * be structurally legal if the side-to-move were considered the
         * previous mover.  The helper lives in FourPieceTablebase because it
         * already contains the KQRK attack geometry used by the encoder.
         */
        return tablebase.isSideToMoveInCheckForBuild(
                position
        );
    }


    private short safeShort(
            int value
    ) {

        if (value < 0
                || value > Short.MAX_VALUE) {

            throw new IllegalStateException(
                    "Four-piece mate distance exceeds persistence range: "
                            + value
            );
        }


        return (short) value;
    }


    private record Child(
            byte outcome,
            int distance
    ) {
    }


    public record BuildResult(
            int legalStates,
            int wins,
            int losses,
            int draws,
            int sweeps,
            long buildMillis
    ) {
    }
}

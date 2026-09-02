package main.java.chess.endgame;

import main.java.chess.model.Board;
import main.java.chess.model.Color;
import main.java.chess.model.Move;
import main.java.chess.model.Piece;
import main.java.chess.model.PieceType;
import main.java.chess.model.Position;
import main.java.chess.model.PositionKey;
import main.java.chess.model.Square;
import main.java.chess.rules.MoveGenerator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;


/**
 * Exact retrograde tablebase for KQK, KRK, and KPK.
 *
 * A table instance represents one material class:
 *
 *     king + {queen|rook|pawn} versus king
 *
 * with a fixed color owning the major piece.
 *
 * The state space is encoded compactly:
 *
 *     white king square
 *     black king square
 *     major-piece square
 *     side to move
 *
 * No castling or en-passant state is needed in these material classes.
 *
 * Results are exact:
 *     WIN  = side to move can force mate
 *     LOSS = side to move is force-mated
 *     DRAW = neither side can force mate
 *
 * mateDistance is measured in plies.
 */
public final class ThreePieceTablebase {

    public enum Outcome {
        WIN,
        LOSS,
        DRAW,
        UNSUPPORTED
    }


    public record Probe(
            Outcome outcome,
            int mateDistance
    ) {

        public boolean hasForcedMate() {
            return outcome == Outcome.WIN
                    || outcome == Outcome.LOSS;
        }


        public int mateInMoves() {

            if (!hasForcedMate()
                    || mateDistance < 0) {

                return -1;
            }

            return (mateDistance + 1) / 2;
        }
    }


    private static final byte INVALID = -2;
    private static final byte LOSS = -1;
    private static final byte UNKNOWN = 0;
    private static final byte WIN = 1;
    private static final byte DRAW = 2;

    private static final int BOARD_SQUARES = 64;
    private static final int STATE_COUNT =
            BOARD_SQUARES
                    * BOARD_SQUARES
                    * BOARD_SQUARES
                    * 2;

    private final PieceType majorType;
    private final Color majorColor;

    private final MoveGenerator moveGenerator;

    /*
     * KPK promotions leave the KPK state space.  Queen and rook
     * promotions are resolved exactly by the already-solved KQK/KRK
     * tablebases. Bishop/knight promotion is K+B/N vs K and therefore
     * an exact draw.
     */
    private ThreePieceTablebase queenPromotionTablebase;
    private ThreePieceTablebase rookPromotionTablebase;

    private final byte[] outcome;
    private final short[] distance;

    private boolean built;

    private int legalStateCount;
    private int winCount;
    private int lossCount;
    private int drawCount;

    private long buildMillis;


    public ThreePieceTablebase(
            PieceType majorType,
            Color majorColor
    ) {

        if (majorType != PieceType.QUEEN
                && majorType != PieceType.ROOK
                && majorType != PieceType.PAWN) {

            throw new IllegalArgumentException(
                    "ThreePieceTablebase currently supports only KQK, KRK, and KPK."
            );
        }

        if (majorColor == null) {

            throw new IllegalArgumentException(
                    "Major-piece color cannot be null."
            );
        }


        this.majorType =
                majorType;

        this.majorColor =
                majorColor;

        this.moveGenerator =
                new MoveGenerator();

        this.outcome =
                new byte[STATE_COUNT];

        this.distance =
                new short[STATE_COUNT];

        this.built =
                false;
    }


    // =========================================================
    // BUILD
    // =========================================================

    public synchronized void build() {

        if (built) {

            return;
        }


        long started =
                System.currentTimeMillis();


        legalStateCount = 0;
        winCount = 0;
        lossCount = 0;
        drawCount = 0;


        if (majorType == PieceType.PAWN) {

            ensurePromotionTablebases();
        }


        int[] predecessorCount =
                new int[STATE_COUNT];

        int[] remaining =
                new int[STATE_COUNT];

        short[] maximumWinningChildDistance =
                new short[STATE_COUNT];


        // -----------------------------------------------------
        // 1. Identify structurally legal states.
        // -----------------------------------------------------

        for (int state = 0;
             state < STATE_COUNT;
             state++) {

            Decoded decoded =
                    decode(
                            state
                    );


            if (!isStructurallyLegal(
                    decoded
            )) {

                outcome[state] =
                        INVALID;

                distance[state] =
                        -1;

                continue;
            }


            outcome[state] =
                    UNKNOWN;

            distance[state] =
                    -1;

            legalStateCount++;
        }


        /*
         * Reverse adjacency is constructed in compact CSR form.
         *
         * We first count how many in-class predecessors each state
         * has. At the same time we seed terminal positions and record
         * each state's total legal-move count.
         */
        PriorityQueue<SolvedState> queue =
                new PriorityQueue<>(
                        (a, b) ->
                                Integer.compare(
                                        a.distance(),
                                        b.distance()
                                )
                );


        for (int state = 0;
             state < STATE_COUNT;
             state++) {

            if (outcome[state]
                    == INVALID) {

                continue;
            }


            Position position =
                    positionForState(
                            state
                    );


            List<Move> legalMoves =
                    moveGenerator.generateLegalMoves(
                            position
                    );


            remaining[state] =
                    legalMoves.size();


            if (legalMoves.isEmpty()) {

                if (isSideToMoveInCheck(
                        decode(state)
                )) {

                    outcome[state] =
                            LOSS;

                    distance[state] =
                            0;

                    queue.add(
                            new SolvedState(
                                    state,
                                    0
                            )
                    );

                } else {

                    outcome[state] =
                            DRAW;

                    distance[state] =
                            -1;
                }


                continue;
            }


            int seededWinDistance =
                    Integer.MAX_VALUE;


            for (Move move :
                    legalMoves) {

                Position next =
                        position.makeMove(
                                move
                        );


                int childState =
                        encodePosition(
                                next
                        );


                if (childState >= 0) {

                    predecessorCount[
                            childState
                            ]++;

                    continue;
                }


                /*
                 * The successor left this material class.
                 *
                 * Q/R capture -> K vs K draw.
                 * Pawn capture -> K vs K draw.
                 * Pawn promotion -> exact KQK/KRK lookup, or an exact
                 * K+B/N vs K draw.
                 */
                ExternalResult external =
                        classifyExternalSuccessor(
                                move,
                                next
                        );


                if (external.outcome()
                        == LOSS) {

                    int candidate =
                            external.distance()
                                    + 1;

                    if (candidate
                            < seededWinDistance) {

                        seededWinDistance =
                                candidate;
                    }

                } else if (external.outcome()
                        == WIN) {

                    remaining[state]--;


                    if (external.distance()
                            > maximumWinningChildDistance[
                            state
                            ]) {

                        maximumWinningChildDistance[
                                state
                                ] =
                                safeShort(
                                        external.distance()
                                );
                    }
                }
            }


            /*
             * A known external LOSS child immediately makes the parent a
             * WIN.  If every legal move is already known to be an
             * external WIN, the parent is immediately a LOSS.
             */
            if (seededWinDistance
                    != Integer.MAX_VALUE) {

                outcome[state] =
                        WIN;

                distance[state] =
                        safeShort(
                                seededWinDistance
                        );

                queue.add(
                        new SolvedState(
                                state,
                                seededWinDistance
                        )
                );

            } else if (remaining[state]
                    == 0) {

                int parentDistance =
                        maximumWinningChildDistance[
                                state
                                ]
                                + 1;

                outcome[state] =
                        LOSS;

                distance[state] =
                        safeShort(
                                parentDistance
                        );

                queue.add(
                        new SolvedState(
                                state,
                                parentDistance
                        )
                );
            }
        }


        // -----------------------------------------------------
        // 2. Allocate reverse-edge CSR.
        // -----------------------------------------------------

        int[] predecessorOffset =
                new int[STATE_COUNT + 1];


        for (int state = 0;
             state < STATE_COUNT;
             state++) {

            predecessorOffset[state + 1] =
                    predecessorOffset[state]
                            + predecessorCount[state];
        }


        int[] predecessors =
                new int[
                        predecessorOffset[
                                STATE_COUNT
                                ]
                        ];


        int[] fillCursor =
                predecessorOffset.clone();


        // -----------------------------------------------------
        // 3. Fill reverse edges.
        // -----------------------------------------------------

        for (int parentState = 0;
             parentState < STATE_COUNT;
             parentState++) {

            if (outcome[parentState]
                    == INVALID) {

                continue;
            }


            Position position =
                    positionForState(
                            parentState
                    );


            List<Move> legalMoves =
                    moveGenerator.generateLegalMoves(
                            position
                    );


            for (Move move :
                    legalMoves) {

                Position next =
                        position.makeMove(
                                move
                        );


                int childState =
                        encodePosition(
                                next
                        );


                if (childState < 0) {

                    continue;
                }


                int insertion =
                        fillCursor[
                                childState
                                ]++;


                predecessors[insertion] =
                        parentState;
            }
        }


        // -----------------------------------------------------
        // 4. Retrograde propagation.
        // -----------------------------------------------------

        while (!queue.isEmpty()) {

            SolvedState solved =
                    queue.remove();


            int childState =
                    solved.state();


            byte childOutcome =
                    outcome[
                            childState
                            ];


            int childDistance =
                    distance[
                            childState
                            ];


            int start =
                    predecessorOffset[
                            childState
                            ];

            int end =
                    predecessorOffset[
                            childState + 1
                            ];


            for (int index = start;
                 index < end;
                 index++) {

                int parentState =
                        predecessors[index];


                if (outcome[parentState]
                        != UNKNOWN) {

                    continue;
                }


                /*
                 * If a legal move reaches a LOSS for the opponent,
                 * this parent is a WIN.
                 *
                 * Because the priority queue processes solved nodes
                 * by increasing mate distance, the first LOSS child
                 * seen gives the shortest forced mate.
                 */
                if (childOutcome == LOSS) {

                    outcome[parentState] =
                            WIN;

                    int parentDistance =
                            childDistance + 1;

                    distance[parentState] =
                            safeShort(
                                    parentDistance
                            );


                    queue.add(
                            new SolvedState(
                                    parentState,
                                    parentDistance
                            )
                    );


                    continue;
                }


                /*
                 * A WIN child is bad for the player at the parent.
                 * Once every legal move is known to lead to a WIN
                 * for the opponent, the parent is a LOSS.
                 *
                 * For exact defensive play the losing side chooses
                 * the continuation that survives longest.
                 */
                if (childOutcome == WIN) {

                    remaining[parentState]--;


                    if (childDistance
                            > maximumWinningChildDistance[
                            parentState
                            ]) {

                        maximumWinningChildDistance[
                                parentState
                                ] =
                                safeShort(
                                        childDistance
                                );
                    }


                    if (remaining[parentState]
                            == 0) {

                        outcome[parentState] =
                                LOSS;


                        int parentDistance =
                                maximumWinningChildDistance[
                                        parentState
                                        ]
                                        + 1;


                        distance[parentState] =
                                safeShort(
                                        parentDistance
                                );


                        queue.add(
                                new SolvedState(
                                        parentState,
                                        parentDistance
                                )
                        );
                    }
                }
            }
        }


        // -----------------------------------------------------
        // 5. Every unresolved legal cycle is a draw.
        // -----------------------------------------------------

        for (int state = 0;
             state < STATE_COUNT;
             state++) {

            if (outcome[state]
                    == UNKNOWN) {

                outcome[state] =
                        DRAW;

                distance[state] =
                        -1;
            }


            switch (outcome[state]) {

                case WIN ->
                        winCount++;

                case LOSS ->
                        lossCount++;

                case DRAW ->
                        drawCount++;

                default -> {
                }
            }
        }


        buildMillis =
                System.currentTimeMillis()
                        - started;


        built =
                true;
    }


    // =========================================================
    // PROBE
    // =========================================================

    public Probe probe(
            Position position
    ) {

        ensureBuilt();


        int state =
                encodePosition(
                        position
                );


        if (state < 0
                ||
                outcome[state]
                        == INVALID) {

            return new Probe(
                    Outcome.UNSUPPORTED,
                    -1
            );
        }


        return new Probe(
                publicOutcome(
                        outcome[state]
                ),
                distance[state]
        );
    }


    // =========================================================
    // OPTIMAL MOVES
    // =========================================================

    public List<Move> bestMoves(
            Position position
    ) {

        ensureBuilt();


        int parentState =
                encodePosition(
                        position
                );


        if (parentState < 0) {

            return List.of();
        }


        byte parentOutcome =
                outcome[
                        parentState
                        ];


        if (parentOutcome
                == INVALID) {

            return List.of();
        }


        List<Move> legalMoves =
                moveGenerator.generateLegalMoves(
                        position
                );


        List<Move> best =
                new ArrayList<>();


        int bestDistance =
                parentOutcome == LOSS
                        ? Integer.MIN_VALUE
                        : Integer.MAX_VALUE;


        for (Move move :
                legalMoves) {

            Position next =
                    position.makeMove(
                            move
                    );


            int childState =
                    encodePosition(
                            next
                    );


            byte childOutcome;
            int childDistance;


            if (childState < 0) {

                ExternalResult external =
                        classifyExternalSuccessor(
                                move,
                                next
                        );

                childOutcome =
                        external.outcome();

                childDistance =
                        external.distance();

            } else {

                childOutcome =
                        outcome[
                                childState
                                ];

                childDistance =
                        distance[
                                childState
                                ];
            }


            if (parentOutcome == WIN) {

                if (childOutcome
                        != LOSS) {

                    continue;
                }


                if (childDistance
                        < bestDistance) {

                    best.clear();

                    best.add(
                            move
                    );

                    bestDistance =
                            childDistance;

                } else if (childDistance
                        == bestDistance) {

                    best.add(
                            move
                    );
                }


            } else if (parentOutcome == LOSS) {

                if (childOutcome
                        != WIN) {

                    continue;
                }


                if (childDistance
                        > bestDistance) {

                    best.clear();

                    best.add(
                            move
                    );

                    bestDistance =
                            childDistance;

                } else if (childDistance
                        == bestDistance) {

                    best.add(
                            move
                    );
                }


            } else if (parentOutcome == DRAW) {

                if (childOutcome
                        == DRAW) {

                    best.add(
                            move
                    );
                }
            }
        }


        return List.copyOf(
                best
        );
    }


    // =========================================================
    // RANDOM EXACT STUDY
    // =========================================================

    /**
     * Select a random tablebase WIN with the major-piece side to move.
     *
     * Reservoir sampling avoids storing a second list containing every
     * winning state.
     */
    public Position randomWinningPosition(
            Random random
    ) {

        ensureBuilt();


        if (random == null) {

            random =
                    new Random();
        }


        int selectedState =
                -1;

        int seen =
                0;


        for (int state = 0;
             state < STATE_COUNT;
             state++) {

            if (outcome[state]
                    != WIN) {

                continue;
            }


            Decoded decoded =
                    decode(
                            state
                    );


            if (decoded.sideToMove()
                    != majorColor) {

                continue;
            }


            seen++;


            if (random.nextInt(
                    seen
            ) == 0) {

                selectedState =
                        state;
            }
        }


        if (selectedState < 0) {

            throw new IllegalStateException(
                    "No winning study positions were found in "
                            + materialName()
                            + "."
            );
        }


        return positionForState(
                selectedState
        );
    }


    // =========================================================
    // OUT-OF-CLASS SUCCESSORS
    // =========================================================

    private ExternalResult classifyExternalSuccessor(
            Move move,
            Position next
    ) {

        if (majorType != PieceType.PAWN) {

            /*
             * In KQK/KRK the only legal way to leave the class is for
             * the lone king to capture the major piece, producing K vs K.
             */
            return new ExternalResult(
                    DRAW,
                    -1
            );
        }


        /*
         * If the pawn disappeared without a promoted replacement, it was
         * captured and the result is K vs K.
         */
        PieceType promotedType =
                promotedPieceType(
                        next
                );


        if (promotedType == null) {

            return new ExternalResult(
                    DRAW,
                    -1
            );
        }


        if (promotedType == PieceType.BISHOP
                || promotedType == PieceType.KNIGHT) {

            /*
             * K+B vs K and K+N vs K are exact dead-position draws.
             */
            return new ExternalResult(
                    DRAW,
                    -1
            );
        }


        ensurePromotionTablebases();


        ThreePieceTablebase promotionTablebase =
                switch (promotedType) {

                    case QUEEN ->
                            queenPromotionTablebase;

                    case ROOK ->
                            rookPromotionTablebase;

                    default ->
                            null;
                };


        if (promotionTablebase == null) {

            throw new IllegalStateException(
                    "Unsupported promotion successor: "
                            + promotedType
            );
        }


        Probe probe =
                promotionTablebase.probe(
                        next
                );


        return switch (probe.outcome()) {

            case WIN ->
                    new ExternalResult(
                            WIN,
                            probe.mateDistance()
                    );

            case LOSS ->
                    new ExternalResult(
                            LOSS,
                            probe.mateDistance()
                    );

            case DRAW ->
                    new ExternalResult(
                            DRAW,
                            -1
                    );

            case UNSUPPORTED ->
                    throw new IllegalStateException(
                            "Promotion produced a position that the "
                                    + promotedType
                                    + " tablebase could not encode."
                    );
        };
    }


    private void ensurePromotionTablebases() {

        if (majorType != PieceType.PAWN) {
            return;
        }


        if (queenPromotionTablebase != null
                && rookPromotionTablebase != null) {

            return;
        }


        ThreePieceTablebaseService service =
                new ThreePieceTablebaseService();


        queenPromotionTablebase =
                service.get(
                        PieceType.QUEEN,
                        majorColor
                );


        rookPromotionTablebase =
                service.get(
                        PieceType.ROOK,
                        majorColor
                );
    }


    private PieceType promotedPieceType(
            Position position
    ) {

        Board board =
                position.getBoard();


        int nonKingCount =
                0;

        PieceType found =
                null;


        for (int rank = 0;
             rank < 8;
             rank++) {

            for (int file = 0;
                 file < 8;
                 file++) {

                Piece piece =
                        board.getPiece(
                                new Square(
                                        file,
                                        rank
                                )
                        );


                if (piece == null
                        || piece.type()
                        == PieceType.KING) {

                    continue;
                }


                nonKingCount++;


                if (piece.color()
                        != majorColor) {

                    return null;
                }


                found =
                        piece.type();
            }
        }


        if (nonKingCount == 0) {

            return null;
        }


        if (nonKingCount != 1
                ||
                found == PieceType.PAWN) {

            throw new IllegalStateException(
                    "Unexpected KPK external successor."
            );
        }


        return found;
    }


    // =========================================================
    // POSITION ENCODING
    // =========================================================

    private int encodePosition(
            Position position
    ) {

        if (position == null) {

            return -1;
        }


        Board board =
                position.getBoard();


        int whiteKing =
                -1;

        int blackKing =
                -1;

        int major =
                -1;

        int pieceCount =
                0;


        for (int rank = 0;
             rank < 8;
             rank++) {

            for (int file = 0;
                 file < 8;
                 file++) {

                Square square =
                        new Square(
                                file,
                                rank
                        );


                Piece piece =
                        board.getPiece(
                                square
                        );


                if (piece == null) {

                    continue;
                }


                pieceCount++;


                int index =
                        squareIndex(
                                square
                        );


                if (piece.type()
                        == PieceType.KING) {

                    if (piece.color()
                            == Color.WHITE) {

                        if (whiteKing >= 0) {

                            return -1;
                        }

                        whiteKing =
                                index;

                    } else {

                        if (blackKing >= 0) {

                            return -1;
                        }

                        blackKing =
                                index;
                    }


                } else if (piece.type()
                        == majorType
                        &&
                        piece.color()
                                == majorColor) {

                    if (major >= 0) {

                        return -1;
                    }

                    major =
                            index;


                } else {

                    return -1;
                }
            }
        }


        if (pieceCount != 3
                ||
                whiteKing < 0
                ||
                blackKing < 0
                ||
                major < 0) {

            return -1;
        }


        int state =
                encode(
                        whiteKing,
                        blackKing,
                        major,
                        position.getSideToMove()
                );


        return outcome[state]
                == INVALID
                ? -1
                : state;
    }


    private int encode(
            int whiteKing,
            int blackKing,
            int major,
            Color sideToMove
    ) {

        int side =
                sideToMove == Color.WHITE
                        ? 0
                        : 1;


        return ((((whiteKing
                * 64)
                + blackKing)
                * 64)
                + major)
                * 2
                + side;
    }


    private Decoded decode(
            int state
    ) {

        int side =
                state & 1;


        int packed =
                state >>> 1;


        int major =
                packed % 64;

        packed /=
                64;


        int blackKing =
                packed % 64;

        packed /=
                64;


        int whiteKing =
                packed;


        return new Decoded(
                whiteKing,
                blackKing,
                major,
                side == 0
                        ? Color.WHITE
                        : Color.BLACK
        );
    }


    // =========================================================
    // LEGALITY
    // =========================================================

    private boolean isStructurallyLegal(
            Decoded state
    ) {

        if (state.whiteKing()
                == state.blackKing()
                ||
                state.whiteKing()
                        == state.major()
                ||
                state.blackKing()
                        == state.major()) {

            return false;
        }


        if (adjacent(
                state.whiteKing(),
                state.blackKing()
        )) {

            return false;
        }


        /*
         * A pawn cannot legally remain on its promotion rank or on the
         * opposite back rank. Promotion transitions are handled as
         * external successors instead.
         */
        if (majorType == PieceType.PAWN) {

            int pawnRank =
                    rankOf(
                            state.major()
                    );

            if (pawnRank == 0
                    || pawnRank == 7) {

                return false;
            }
        }


        /*
         * In a legally reachable chess position, the player who is
         * NOT to move cannot already have a king left in check by the
         * side that is about to move.
         *
         * The only non-king attacker is the third piece. Therefore an
         * additional structural impossibility occurs when that piece's
         * side is to move while the third piece is already attacking the
         * lone king. This applies to queen, rook, and pawn geometry.
         */
        if (state.sideToMove()
                == majorColor) {

            int loneKing =
                    majorColor == Color.WHITE
                            ? state.blackKing()
                            : state.whiteKing();


            int ownKing =
                    majorColor == Color.WHITE
                            ? state.whiteKing()
                            : state.blackKing();


            if (thirdPieceAttacks(
                    state.major(),
                    loneKing,
                    ownKing
            )) {

                return false;
            }
        }


        return true;
    }


    private boolean isSideToMoveInCheck(
            Decoded state
    ) {

        /*
         * Kings are never adjacent in a legal state.
         *
         * The major side can only be checked by the lone king, so in
         * this three-piece material class it is never in check after
         * the adjacency legality filter.
         */
        if (state.sideToMove()
                == majorColor) {

            return false;
        }


        int loneKing =
                majorColor == Color.WHITE
                        ? state.blackKing()
                        : state.whiteKing();


        int ownKing =
                majorColor == Color.WHITE
                        ? state.whiteKing()
                        : state.blackKing();


        return thirdPieceAttacks(
                state.major(),
                loneKing,
                ownKing
        );
    }


    // =========================================================
    // ATTACK GEOMETRY
    // =========================================================

    private boolean thirdPieceAttacks(
            int pieceSquare,
            int targetSquare,
            int blockerSquare
    ) {

        if (majorType == PieceType.PAWN) {

            return pawnAttacks(
                    pieceSquare,
                    targetSquare,
                    majorColor
            );
        }


        return slidingPieceAttacks(
                pieceSquare,
                targetSquare,
                blockerSquare
        );
    }


    private boolean pawnAttacks(
            int pawnSquare,
            int targetSquare,
            Color pawnColor
    ) {

        int pawnFile =
                fileOf(
                        pawnSquare
                );

        int pawnRank =
                rankOf(
                        pawnSquare
                );

        int targetFile =
                fileOf(
                        targetSquare
                );

        int targetRank =
                rankOf(
                        targetSquare
                );


        int expectedRankDelta =
                pawnColor == Color.WHITE
                        ? 1
                        : -1;


        return targetRank
                - pawnRank
                == expectedRankDelta
                &&
                Math.abs(
                        targetFile
                                - pawnFile
                )
                        == 1;
    }


    private boolean slidingPieceAttacks(
            int majorSquare,
            int targetSquare,
            int blockerSquare
    ) {

        int majorFile =
                fileOf(
                        majorSquare
                );

        int majorRank =
                rankOf(
                        majorSquare
                );

        int targetFile =
                fileOf(
                        targetSquare
                );

        int targetRank =
                rankOf(
                        targetSquare
                );


        int fileDelta =
                targetFile
                        - majorFile;

        int rankDelta =
                targetRank
                        - majorRank;


        boolean rookLine =
                fileDelta == 0
                        ||
                        rankDelta == 0;


        boolean bishopLine =
                Math.abs(
                        fileDelta
                )
                        ==
                        Math.abs(
                                rankDelta
                        );


        boolean lineAllowed =
                majorType == PieceType.ROOK
                        ? rookLine
                        : rookLine
                        || bishopLine;


        if (!lineAllowed) {

            return false;
        }


        int fileStep =
                Integer.compare(
                        targetFile,
                        majorFile
                );

        int rankStep =
                Integer.compare(
                        targetRank,
                        majorRank
                );


        int file =
                majorFile
                        + fileStep;

        int rank =
                majorRank
                        + rankStep;


        while (file != targetFile
                ||
                rank != targetRank) {

            int square =
                    rank * 8
                            + file;


            if (square
                    == blockerSquare) {

                return false;
            }


            file +=
                    fileStep;

            rank +=
                    rankStep;
        }


        return true;
    }


    private boolean adjacent(
            int first,
            int second
    ) {

        return Math.abs(
                fileOf(first)
                        - fileOf(second)
        ) <= 1
                &&
                Math.abs(
                        rankOf(first)
                                - rankOf(second)
                ) <= 1;
    }


    // =========================================================
    // POSITION CREATION
    // =========================================================

    private Position positionForState(
            int state
    ) {

        Decoded decoded =
                decode(
                        state
                );


        Board board =
                new Board();


        board.setPiece(
                squareOf(
                        decoded.whiteKing()
                ),
                new Piece(
                        PieceType.KING,
                        Color.WHITE
                )
        );


        board.setPiece(
                squareOf(
                        decoded.blackKing()
                ),
                new Piece(
                        PieceType.KING,
                        Color.BLACK
                )
        );


        board.setPiece(
                squareOf(
                        decoded.major()
                ),
                new Piece(
                        majorType,
                        majorColor
                )
        );


        Position temporary =
                new Position(
                        board,
                        decoded.sideToMove(),
                        false,
                        false,
                        false,
                        false,
                        null,
                        0,
                        1,
                        Map.of()
                );


        Map<PositionKey, Integer>
                repetitionCounts =
                new HashMap<>();


        repetitionCounts.put(
                temporary.createPositionKey(),
                1
        );


        return new Position(
                board,
                decoded.sideToMove(),
                false,
                false,
                false,
                false,
                null,
                0,
                1,
                repetitionCounts
        );
    }


    // =========================================================
    // PERSISTENCE SUPPORT
    // =========================================================

    static int persistenceStateCount() {
        return STATE_COUNT;
    }


    byte[] copyOutcomeDataForPersistence() {

        ensureBuilt();

        return outcome.clone();
    }


    short[] copyDistanceDataForPersistence() {

        ensureBuilt();

        return distance.clone();
    }


    synchronized void restoreFromPersistence(
            byte[] persistedOutcome,
            short[] persistedDistance
    ) {

        if (built) {

            throw new IllegalStateException(
                    "Cannot restore persistence data into an already built tablebase."
            );
        }


        if (persistedOutcome == null
                || persistedDistance == null) {

            throw new IllegalArgumentException(
                    "Persisted tablebase arrays cannot be null."
            );
        }


        if (persistedOutcome.length != STATE_COUNT
                || persistedDistance.length != STATE_COUNT) {

            throw new IllegalArgumentException(
                    "Persisted tablebase arrays have the wrong state count."
            );
        }


        int restoredLegalStateCount = 0;
        int restoredWinCount = 0;
        int restoredLossCount = 0;
        int restoredDrawCount = 0;


        for (int state = 0;
             state < STATE_COUNT;
             state++) {

            byte restoredOutcome =
                    persistedOutcome[state];

            short restoredDistance =
                    persistedDistance[state];


            switch (restoredOutcome) {

                case INVALID -> {

                    if (restoredDistance != -1) {

                        throw new IllegalArgumentException(
                                "Invalid state has a persisted mate distance at state "
                                        + state
                                        + "."
                        );
                    }
                }

                case WIN -> {

                    if (restoredDistance < 1) {

                        throw new IllegalArgumentException(
                                "Winning state has an invalid mate distance at state "
                                        + state
                                        + "."
                        );
                    }

                    restoredLegalStateCount++;
                    restoredWinCount++;
                }

                case LOSS -> {

                    if (restoredDistance < 0) {

                        throw new IllegalArgumentException(
                                "Losing state has an invalid mate distance at state "
                                        + state
                                        + "."
                        );
                    }

                    restoredLegalStateCount++;
                    restoredLossCount++;
                }

                case DRAW -> {

                    if (restoredDistance != -1) {

                        throw new IllegalArgumentException(
                                "Draw state has a persisted mate distance at state "
                                        + state
                                        + "."
                        );
                    }

                    restoredLegalStateCount++;
                    restoredDrawCount++;
                }

                default ->
                        throw new IllegalArgumentException(
                                "Unknown persisted outcome value "
                                        + restoredOutcome
                                        + " at state "
                                        + state
                                        + "."
                        );
            }
        }


        System.arraycopy(
                persistedOutcome,
                0,
                outcome,
                0,
                STATE_COUNT
        );

        System.arraycopy(
                persistedDistance,
                0,
                distance,
                0,
                STATE_COUNT
        );


        legalStateCount =
                restoredLegalStateCount;

        winCount =
                restoredWinCount;

        lossCount =
                restoredLossCount;

        drawCount =
                restoredDrawCount;

        buildMillis =
                0;

        built =
                true;
    }


    // =========================================================
    // HELPERS / METRICS
    // =========================================================

    public PieceType getMajorType() {
        return majorType;
    }


    public Color getMajorColor() {
        return majorColor;
    }


    public boolean isBuilt() {
        return built;
    }


    public long getBuildMillis() {

        ensureBuilt();

        return buildMillis;
    }


    public int getLegalStateCount() {

        ensureBuilt();

        return legalStateCount;
    }


    public int getWinCount() {

        ensureBuilt();

        return winCount;
    }


    public int getLossCount() {

        ensureBuilt();

        return lossCount;
    }


    public int getDrawCount() {

        ensureBuilt();

        return drawCount;
    }


    public String materialName() {

        String major =
                switch (majorType) {

                    case QUEEN ->
                            "Q";

                    case ROOK ->
                            "R";

                    case PAWN ->
                            "P";

                    default ->
                            throw new IllegalStateException(
                                    "Unsupported three-piece material: "
                                            + majorType
                            );
                };


        return majorColor == Color.WHITE
                ? "K" + major + "K"
                : "K" + major.toLowerCase() + "K";
    }


    private void ensureBuilt() {

        if (!built) {

            throw new IllegalStateException(
                    "Tablebase has not been built yet."
            );
        }
    }


    private Outcome publicOutcome(
            byte internal
    ) {

        return switch (internal) {

            case WIN ->
                    Outcome.WIN;

            case LOSS ->
                    Outcome.LOSS;

            case DRAW ->
                    Outcome.DRAW;

            default ->
                    Outcome.UNSUPPORTED;
        };
    }


    private short safeShort(
            int value
    ) {

        if (value
                > Short.MAX_VALUE) {

            throw new IllegalStateException(
                    "Mate distance exceeded short storage."
            );
        }


        return (short) value;
    }


    private int squareIndex(
            Square square
    ) {

        return square.rank()
                * 8
                + square.file();
    }


    private Square squareOf(
            int index
    ) {

        return new Square(
                fileOf(index),
                rankOf(index)
        );
    }


    private int fileOf(
            int index
    ) {

        return index % 8;
    }


    private int rankOf(
            int index
    ) {

        return index / 8;
    }


    private record Decoded(
            int whiteKing,
            int blackKing,
            int major,
            Color sideToMove
    ) {
    }


    private record ExternalResult(
            byte outcome,
            int distance
    ) {
    }


    private record SolvedState(
            int state,
            int distance
    ) {
    }

    public Probe probeSquares(
            int whiteKingSquare,
            int blackKingSquare,
            int majorPieceSquare,
            boolean blackToMove
    ) {
        int state =
                (((whiteKingSquare * 64
                        + blackKingSquare) * 64
                        + majorPieceSquare) * 2)
                        + (blackToMove ? 1 : 0);

        if (!built
                || state < 0
                || state >= outcome.length
                || outcome[state] == INVALID) {
            return new Probe(
                    Outcome.UNSUPPORTED,
                    -1
            );
        }

        Outcome mapped =
                switch (outcome[state]) {
                    case WIN -> Outcome.WIN;
                    case LOSS -> Outcome.LOSS;
                    case DRAW -> Outcome.DRAW;
                    default -> Outcome.UNSUPPORTED;
                };

        return new Probe(
                mapped,
                mapped == Outcome.WIN
                        || mapped == Outcome.LOSS
                        ? distance[state]
                        : -1
        );
    }
}


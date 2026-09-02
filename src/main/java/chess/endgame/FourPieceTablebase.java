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
import java.util.Random;


/**
 * Exact-four-piece tablebase foundation.
 *
 * Milestone 1 supports the KQRK material class and establishes:
 *
 *     - stable four-piece state encoding
 *     - structural legality filtering
 *     - exact transition into existing 3-piece tablebases
 *     - position probing API
 *     - optimal-move API contract
 *     - persistence hooks
 *
 * IMPORTANT:
 *
 * This class does NOT yet perform the full 33-million-state retrograde build.
 * Until the retrograde arrays have been restored from persistence, probe()
 * returns UNSUPPORTED for in-class KQRK states.
 *
 * That separation is deliberate: it lets us validate the generalized
 * four-piece representation and all cross-tablebase transitions before
 * committing to the much larger offline retrograde builder.
 */
public final class FourPieceTablebase {

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


    static final byte INVALID = -2;
    static final byte LOSS = -1;
    static final byte UNKNOWN = 0;
    static final byte WIN = 1;
    static final byte DRAW = 2;


    private static final int BOARD_SQUARES = 64;

    /*
     * Raw encoding:
     *
     *     white king
     *     black king
     *     queen
     *     rook
     *     side to move
     *
     * 64^4 * 2 = 33,554,432 raw states.
     *
     * We intentionally use a direct dense index now because it gives a
     * stable persistence key.  The later builder can use a compact legal
     * state list internally without changing the on-disk identity.
     */
    static final int STATE_COUNT =
            BOARD_SQUARES
                    * BOARD_SQUARES
                    * BOARD_SQUARES
                    * BOARD_SQUARES
                    * 2;


    private final FourPieceMaterial material;
    private final MoveGenerator moveGenerator;
    private final ThreePieceTablebaseService
            threePieceTablebaseService;


    private byte[] outcome;
    private short[] distance;

    private boolean built;


    public FourPieceTablebase(
            FourPieceMaterial material
    ) {

        if (material == null) {

            throw new IllegalArgumentException(
                    "Four-piece material cannot be null."
            );
        }


        this.material =
                material;

        this.moveGenerator =
                new MoveGenerator();

        this.threePieceTablebaseService =
                new ThreePieceTablebaseService();

        this.outcome =
                null;

        this.distance =
                null;

        this.built =
                false;
    }


    // =========================================================
    // PUBLIC IDENTITY
    // =========================================================

    public FourPieceMaterial getMaterial() {

        return material;
    }


    public boolean isBuilt() {

        return built;
    }


    public String materialName() {

        return material.materialName();
    }


    public int stateCount() {

        return STATE_COUNT;
    }


    // =========================================================
    // PROBE
    // =========================================================

    public Probe probe(
            Position position
    ) {

        /*
         * Before the offline retrograde asset exists we can still recognize
         * exact out-of-class transitions.  A normal in-class KQRK position
         * is intentionally UNSUPPORTED rather than guessed.
         */
        int state =
                encodePosition(
                        position
                );


        if (state < 0) {

            return classifyKnownThreePiecePosition(
                    position
            );
        }


        if (!built
                || outcome == null
                || distance == null) {

            return new Probe(
                    Outcome.UNSUPPORTED,
                    -1
            );
        }


        if (outcome[state]
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

        int parentState =
                encodePosition(
                        position
                );


        if (parentState < 0
                || !built
                || outcome == null
                || distance == null) {

            return List.of();
        }


        byte parentOutcome =
                outcome[parentState];


        if (parentOutcome == INVALID
                || parentOutcome == UNKNOWN) {

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


            ChildResult child =
                    classifyChild(
                            next
                    );


            if (!child.supported()) {
                continue;
            }


            if (parentOutcome == WIN) {

                if (child.outcome()
                        != LOSS) {

                    continue;
                }


                if (child.distance()
                        < bestDistance) {

                    best.clear();
                    best.add(move);

                    bestDistance =
                            child.distance();

                } else if (child.distance()
                        == bestDistance) {

                    best.add(move);
                }


            } else if (parentOutcome == LOSS) {

                if (child.outcome()
                        != WIN) {

                    continue;
                }


                if (child.distance()
                        > bestDistance) {

                    best.clear();
                    best.add(move);

                    bestDistance =
                            child.distance();

                } else if (child.distance()
                        == bestDistance) {

                    best.add(move);
                }


            } else if (parentOutcome == DRAW) {

                if (child.outcome()
                        == DRAW) {

                    best.add(move);
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

    public Position randomWinningPosition(
            Random random
    ) {

        if (!built
                || outcome == null) {

            throw new IllegalStateException(
                    materialName()
                            + " has no loaded exact retrograde data yet."
            );
        }


        if (random == null) {
            random = new Random();
        }


        int selected =
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
                    != material.strongColor()) {

                continue;
            }


            seen++;


            if (random.nextInt(
                    seen
            ) == 0) {

                selected =
                        state;
            }
        }


        if (selected < 0) {

            throw new IllegalStateException(
                    "No exact winning "
                            + materialName()
                            + " study states are available."
            );
        }


        return positionForState(
                selected
        );
    }


    // =========================================================
    // CHILD CLASSIFICATION
    // =========================================================

    private ChildResult classifyChild(
            Position position
    ) {

        int state =
                encodePosition(
                        position
                );


        if (state >= 0) {

            if (!built
                    || outcome == null
                    || outcome[state] == INVALID
                    || outcome[state] == UNKNOWN) {

                return ChildResult.unsupported();
            }


            return new ChildResult(
                    outcome[state],
                    distance[state],
                    true
            );
        }


        Probe external =
                classifyKnownThreePiecePosition(
                        position
                );


        if (external.outcome()
                == Outcome.UNSUPPORTED) {

            return ChildResult.unsupported();
        }


        return new ChildResult(
                internalOutcome(
                        external.outcome()
                ),
                external.mateDistance(),
                true
        );
    }


    /**
     * A KQRK move can leave the class only by capture of the queen or rook.
     * That yields one of the existing exact 3-piece classes:
     *
     *     queen captured -> KRK
     *     rook captured  -> KQK
     *     both gone      -> K vs K draw
     */
    Probe probeKnownThreePieceForBuild(
            Position position
    ) {

        return classifyKnownThreePiecePosition(
                position
        );
    }


    private Probe classifyKnownThreePiecePosition(
            Position position
    ) {

        if (position == null) {

            return new Probe(
                    Outcome.UNSUPPORTED,
                    -1
            );
        }


        Piece loneNonKing =
                null;

        int nonKingCount =
                0;


        for (int rank = 0;
             rank < 8;
             rank++) {

            for (int file = 0;
                 file < 8;
                 file++) {

                Piece piece =
                        position.getBoard()
                                .getPiece(
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
                loneNonKing =
                        piece;
            }
        }


        if (nonKingCount == 0) {

            return new Probe(
                    Outcome.DRAW,
                    -1
            );
        }


        if (nonKingCount != 1
                || loneNonKing == null) {

            return new Probe(
                    Outcome.UNSUPPORTED,
                    -1
            );
        }


        if (loneNonKing.type()
                != PieceType.QUEEN
                &&
                loneNonKing.type()
                        != PieceType.ROOK) {

            return new Probe(
                    Outcome.UNSUPPORTED,
                    -1
            );
        }


        ThreePieceTablebase tablebase =
                threePieceTablebaseService.get(
                        loneNonKing.type(),
                        loneNonKing.color()
                );


        ThreePieceTablebase.Probe probe =
                tablebase.probe(
                        position
                );


        return new Probe(
                switch (probe.outcome()) {

                    case WIN ->
                            Outcome.WIN;

                    case LOSS ->
                            Outcome.LOSS;

                    case DRAW ->
                            Outcome.DRAW;

                    case UNSUPPORTED ->
                            Outcome.UNSUPPORTED;
                },
                probe.mateDistance()
        );
    }


    // =========================================================
    // ENCODING
    // =========================================================

    int encodePosition(
            Position position
    ) {

        if (position == null) {
            return -1;
        }


        Square whiteKing =
                null;

        Square blackKing =
                null;

        Square queen =
                null;

        Square rook =
                null;

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
                        position.getBoard()
                                .getPiece(
                                        square
                                );


                if (piece == null) {
                    continue;
                }


                pieceCount++;


                if (piece.type()
                        == PieceType.KING) {

                    if (piece.color()
                            == Color.WHITE) {

                        if (whiteKing != null) {
                            return -1;
                        }

                        whiteKing =
                                square;

                    } else {

                        if (blackKing != null) {
                            return -1;
                        }

                        blackKing =
                                square;
                    }

                    continue;
                }


                if (piece.color()
                        != material.strongColor()) {

                    return -1;
                }


                if (piece.type()
                        == PieceType.QUEEN) {

                    if (queen != null) {
                        return -1;
                    }

                    queen =
                            square;

                } else if (piece.type()
                        == PieceType.ROOK) {

                    if (rook != null) {
                        return -1;
                    }

                    rook =
                            square;

                } else {

                    return -1;
                }
            }
        }


        if (pieceCount != 4
                || whiteKing == null
                || blackKing == null
                || queen == null
                || rook == null) {

            return -1;
        }


        Decoded decoded =
                new Decoded(
                        squareIndex(
                                whiteKing
                        ),
                        squareIndex(
                                blackKing
                        ),
                        squareIndex(
                                queen
                        ),
                        squareIndex(
                                rook
                        ),
                        position.getSideToMove()
                );


        if (!isStructurallyLegal(
                decoded
        )) {

            return -1;
        }


        return encode(
                decoded
        );
    }


    private int encode(
            Decoded decoded
    ) {

        int side =
                decoded.sideToMove()
                        == Color.WHITE
                        ? 0
                        : 1;


        long state =
                decoded.whiteKing();


        state =
                state * 64
                        + decoded.blackKing();

        state =
                state * 64
                        + decoded.queen();

        state =
                state * 64
                        + decoded.rook();

        state =
                state * 2
                        + side;


        return (int) state;
    }


    private Decoded decode(
            int state
    ) {

        int side =
                state & 1;

        int value =
                state >>> 1;


        int rook =
                value & 63;

        value >>>= 6;


        int queen =
                value & 63;

        value >>>= 6;


        int blackKing =
                value & 63;

        value >>>= 6;


        int whiteKing =
                value & 63;


        return new Decoded(
                whiteKing,
                blackKing,
                queen,
                rook,
                side == 0
                        ? Color.WHITE
                        : Color.BLACK
        );
    }


    private boolean isStructurallyLegal(
            Decoded decoded
    ) {

        int wk =
                decoded.whiteKing();

        int bk =
                decoded.blackKing();

        int q =
                decoded.queen();

        int r =
                decoded.rook();


        if (wk == bk
                || wk == q
                || wk == r
                || bk == q
                || bk == r
                || q == r) {

            return false;
        }


        if (kingsAdjacent(
                wk,
                bk
        )) {

            return false;
        }


        /*
         * The side that just moved may not have left its own king in check.
         * This catches impossible "both kings / wrong previous mover" states
         * while still allowing the current side to move to be in check.
         */
        Position position =
                positionForDecodedUnchecked(
                        decoded
                );


        Color previousMover =
                decoded.sideToMove()
                        .opposite();


        return !isKingInCheck(
                position,
                previousMover
        );
    }


    boolean isSideToMoveInCheckForBuild(
            Position position
    ) {

        if (position == null) {
            return false;
        }


        return isKingInCheck(
                position,
                position.getSideToMove()
        );
    }


    private boolean isKingInCheck(
            Position position,
            Color color
    ) {

        /*
         * MoveGenerator already embodies the engine's legal-move/check
         * semantics.  For this milestone we use a compact king-capture
         * test: switch side to the opponent and see whether any pseudo-legal
         * generated legal move lands on the king square is not available
         * through the public API, so use direct geometric attack logic for
         * KQRK only.
         */

        Square kingSquare =
                findKing(
                        position,
                        color
                );


        if (kingSquare == null) {
            return true;
        }


        Color attacker =
                color.opposite();


        Board board =
                position.getBoard();


        for (int rank = 0;
             rank < 8;
             rank++) {

            for (int file = 0;
                 file < 8;
                 file++) {

                Square from =
                        new Square(
                                file,
                                rank
                        );


                Piece piece =
                        board.getPiece(
                                from
                        );


                if (piece == null
                        || piece.color()
                        != attacker) {

                    continue;
                }


                if (piece.type()
                        == PieceType.KING) {

                    if (Math.max(
                            Math.abs(
                                    from.file()
                                            - kingSquare.file()
                            ),
                            Math.abs(
                                    from.rank()
                                            - kingSquare.rank()
                            )
                    ) == 1) {

                        return true;
                    }


                } else if (piece.type()
                        == PieceType.ROOK) {

                    if (attacksAlongLine(
                            board,
                            from,
                            kingSquare,
                            false
                    )) {

                        return true;
                    }


                } else if (piece.type()
                        == PieceType.QUEEN) {

                    if (attacksAlongLine(
                            board,
                            from,
                            kingSquare,
                            true
                    )) {

                        return true;
                    }
                }
            }
        }


        return false;
    }


    private boolean attacksAlongLine(
            Board board,
            Square from,
            Square target,
            boolean diagonalsAllowed
    ) {

        int df =
                Integer.compare(
                        target.file(),
                        from.file()
                );

        int dr =
                Integer.compare(
                        target.rank(),
                        from.rank()
                );


        int fileDistance =
                Math.abs(
                        target.file()
                                - from.file()
                );

        int rankDistance =
                Math.abs(
                        target.rank()
                                - from.rank()
                );


        boolean straight =
                fileDistance == 0
                        || rankDistance == 0;

        boolean diagonal =
                fileDistance
                        == rankDistance;


        if (!straight
                && !(diagonalsAllowed
                && diagonal)) {

            return false;
        }


        int file =
                from.file()
                        + df;

        int rank =
                from.rank()
                        + dr;


        while (file != target.file()
                || rank != target.rank()) {

            if (board.getPiece(
                    new Square(
                            file,
                            rank
                    )
            ) != null) {

                return false;
            }


            file += df;
            rank += dr;
        }


        return true;
    }


    private boolean kingsAdjacent(
            int first,
            int second
    ) {

        int firstFile =
                first & 7;

        int firstRank =
                first >>> 3;

        int secondFile =
                second & 7;

        int secondRank =
                second >>> 3;


        return Math.max(
                Math.abs(
                        firstFile
                                - secondFile
                ),
                Math.abs(
                        firstRank
                                - secondRank
                )
        ) <= 1;
    }


    private int squareIndex(
            Square square
    ) {

        return square.rank()
                * 8
                + square.file();
    }


    private Square squareForIndex(
            int index
    ) {

        return new Square(
                index & 7,
                index >>> 3
        );
    }


    // =========================================================
    // POSITION RECONSTRUCTION
    // =========================================================

    boolean isLegalState(
            int state
    ) {

        if (state < 0
                || state >= STATE_COUNT) {

            return false;
        }


        return isStructurallyLegal(
                decode(
                        state
                )
        );
    }


    Position positionForState(
            int state
    ) {

        if (state < 0
                || state >= STATE_COUNT) {

            throw new IllegalArgumentException(
                    "Four-piece state is outside the valid raw range."
            );
        }


        return positionForDecodedUnchecked(
                decode(
                        state
                )
        );
    }


    private Position positionForDecodedUnchecked(
            Decoded decoded
    ) {

        Board board =
                new Board();


        board.setPiece(
                squareForIndex(
                        decoded.whiteKing()
                ),
                new Piece(
                        PieceType.KING,
                        Color.WHITE
                )
        );


        board.setPiece(
                squareForIndex(
                        decoded.blackKing()
                ),
                new Piece(
                        PieceType.KING,
                        Color.BLACK
                )
        );


        board.setPiece(
                squareForIndex(
                        decoded.queen()
                ),
                new Piece(
                        PieceType.QUEEN,
                        material.strongColor()
                )
        );


        board.setPiece(
                squareForIndex(
                        decoded.rook()
                ),
                new Piece(
                        PieceType.ROOK,
                        material.strongColor()
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


    private Square findKing(
            Position position,
            Color color
    ) {

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
                        position.getBoard()
                                .getPiece(
                                        square
                                );


                if (piece != null
                        && piece.type()
                        == PieceType.KING
                        && piece.color()
                        == color) {

                    return square;
                }
            }
        }


        return null;
    }


    // =========================================================
    // PERSISTENCE
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


    void restoreFromPersistence(
            byte[] restoredOutcome,
            short[] restoredDistance
    ) {

        if (restoredOutcome == null
                || restoredDistance == null
                || restoredOutcome.length != STATE_COUNT
                || restoredDistance.length != STATE_COUNT) {

            throw new IllegalArgumentException(
                    "Four-piece persistence arrays have the wrong size."
            );
        }


        this.outcome =
                restoredOutcome.clone();

        this.distance =
                restoredDistance.clone();

        this.built =
                true;
    }


    private void ensureBuilt() {

        if (!built
                || outcome == null
                || distance == null) {

            throw new IllegalStateException(
                    materialName()
                            + " exact retrograde data has not been loaded."
            );
        }
    }


    // =========================================================
    // OUTCOME CONVERSION
    // =========================================================

    private Outcome publicOutcome(
            byte value
    ) {

        return switch (value) {

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


    private byte internalOutcome(
            Outcome value
    ) {

        return switch (value) {

            case WIN ->
                    WIN;

            case LOSS ->
                    LOSS;

            case DRAW ->
                    DRAW;

            case UNSUPPORTED ->
                    UNKNOWN;
        };
    }


    // =========================================================
    // INTERNAL RECORDS
    // =========================================================

    private record Decoded(
            int whiteKing,
            int blackKing,
            int queen,
            int rook,
            Color sideToMove
    ) {
    }


    private record ChildResult(
            byte outcome,
            int distance,
            boolean supported
    ) {

        private static ChildResult unsupported() {

            return new ChildResult(
                    UNKNOWN,
                    -1,
                    false
            );
        }
    }
}

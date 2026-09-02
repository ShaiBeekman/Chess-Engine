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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;


/**
 * Unified exact-endgame facade.
 *
 * Supported runtime families:
 *
 *     3 pieces:
 *         KQK, KRK, KPK
 *
 *     4 pieces, Tier 0:
 *         complete persisted pawnless generic catalog
 *
 *     4 pieces, Tier 1:
 *         complete persisted generic one-pawn catalog
 *
 *     4 pieces, Tier 2:
 *         persisted canonical KPPK and EP-aware KP-KP
 *
 * The public WDL convention is always from the side-to-move perspective.
 * DTM is measured in plies.
 */
public final class ExactEndgameTablebase {

    public static final String BUILD_ID =
            "M63B-KPKP-RUNTIME-ROUTING-V1";

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


    /**
     * Move classification used by EndgamePracticePolicy.
     *
     * childOutcome is from the CHILD side-to-move perspective.
     */
    public record MoveAnalysis(
            Move move,
            Outcome childOutcome,
            int childMateDistance,
            boolean optimal
    ) {

        public boolean childIsWin() {

            return childOutcome == Outcome.WIN;
        }


        public boolean childIsLoss() {

            return childOutcome == Outcome.LOSS;
        }


        public boolean childIsDraw() {

            return childOutcome == Outcome.DRAW;
        }
    }


    private final ThreePieceTablebase threePiece;

    private final FourPieceTablebase fourPiece;

    private final FourPieceGenericTablebaseService genericFourPiece;

    private final FourPieceTierOneTablebaseService tierOne;

    private final FourPieceTierTwoKppkTablebaseService tierTwoKppk;

    private final FourPieceTierTwoKpkpTablebaseService tierTwoKpkp;

    private final ThreePieceTablebaseService threePieceService;

    private final MoveGenerator moveGenerator;


    private ExactEndgameTablebase(
            ThreePieceTablebase threePiece,
            FourPieceTablebase fourPiece,
            FourPieceGenericTablebaseService genericFourPiece,
            FourPieceTierOneTablebaseService tierOne,
            FourPieceTierTwoKppkTablebaseService tierTwoKppk,
            FourPieceTierTwoKpkpTablebaseService tierTwoKpkp
    ) {

        this.threePiece =
                threePiece;

        this.fourPiece =
                fourPiece;

        this.genericFourPiece =
                genericFourPiece;

        this.tierOne =
                tierOne;

        this.tierTwoKppk =
                tierTwoKppk;

        this.tierTwoKpkp =
                tierTwoKpkp;

        this.threePieceService =
                new ThreePieceTablebaseService();

        this.moveGenerator =
                new MoveGenerator();
    }


    public static ExactEndgameTablebase of(
            ThreePieceTablebase tablebase
    ) {

        if (tablebase == null) {
            return null;
        }


        return new ExactEndgameTablebase(
                tablebase,
                null,
                null,
                null,
                null,
                null
        );
    }


    public static ExactEndgameTablebase of(
            FourPieceTablebase tablebase
    ) {

        if (tablebase == null
                || !tablebase.isBuilt()) {

            return null;
        }


        return new ExactEndgameTablebase(
                null,
                tablebase,
                null,
                null,
                null,
                null
        );
    }


    /**
     * Backward-compatible factory name.
     *
     * Historically this facade contained only the four-piece Tier-0 catalog.
     * It now also routes the persisted generic Tier-1 catalog, while retaining
     * the old factory name so callers do not need to change yet.
     */
    public static ExactEndgameTablebase tierZeroCatalog() {

        return new ExactEndgameTablebase(
                null,
                null,
                new FourPieceGenericTablebaseService(),
                new FourPieceTierOneTablebaseService(),
                new FourPieceTierTwoKppkTablebaseService(),
                new FourPieceTierTwoKpkpTablebaseService()
        );
    }


    /**
     * Releases runtime tablebase caches held by this facade.
     *
     * This is primarily useful for verification runs that deliberately walk
     * every four-piece family one after another.  Clearing between families
     * keeps the regression gate from retaining all large exact arrays at once.
     */
    public void clearRuntimeCaches() {

        if (genericFourPiece != null) {
            genericFourPiece.clearCache();
        }

        if (tierOne != null) {
            tierOne.clearCache();
        }

        if (tierTwoKppk != null) {
            tierTwoKppk.clearCache();
        }

        if (tierTwoKpkp != null) {
            tierTwoKpkp.clearCache();
        }

        threePieceService.clear();
    }


    public Probe probe(
            Position position
    ) {

        if (position == null) {

            return unsupported();
        }


        if (threePiece != null) {

            ThreePieceTablebase.Probe probe =
                    threePiece.probe(
                            position
                    );


            return new Probe(
                    map(
                            probe.outcome()
                    ),
                    probe.mateDistance()
            );
        }


        if (fourPiece != null) {

            FourPieceTablebase.Probe probe =
                    fourPiece.probe(
                            position
                    );


            return new Probe(
                    map(
                            probe.outcome()
                    ),
                    probe.mateDistance()
            );
        }


        Probe fourPieceProbe =
                probeRuntimeFourPiece(
                        position
                );


        if (fourPieceProbe.outcome()
                != Outcome.UNSUPPORTED) {

            return fourPieceProbe;
        }


        /*
         * The catalog facade is primarily four-piece, but exact move
         * traversal crosses material boundaries.  Allow direct three-piece
         * probing here too so callers can keep using the same facade after
         * a capture or promotion transition.
         */
        return probeThreePieceBoundary(
                position
        );
    }


    public List<Move> bestMoves(
            Position position
    ) {

        if (position == null) {

            return List.of();
        }


        if (threePiece != null) {

            return threePiece.bestMoves(
                    position
            );
        }


        if (fourPiece != null) {

            return fourPiece.bestMoves(
                    position
            );
        }


        List<MoveAnalysis> analyses =
                analyzeMoves(
                        position
                );


        if (analyses.isEmpty()) {

            return List.of();
        }


        List<Move> result =
                new ArrayList<>();


        for (MoveAnalysis analysis :
                analyses) {

            if (analysis.optimal()) {

                result.add(
                        analysis.move()
                );
            }
        }


        return List.copyOf(
                result
        );
    }


    /**
     * Returns every legal move for which the exact child is known, together
     * with the exact child WDL/DTM and whether the move is game-theoretically
     * optimal for the current position.
     */
    public List<MoveAnalysis> analyzeMoves(
            Position position
    ) {

        if (position == null
                || threePiece != null
                || fourPiece != null) {

            return List.of();
        }


        Probe parent =
                probe(
                        position
                );


        if (parent.outcome()
                == Outcome.UNSUPPORTED) {

            return List.of();
        }


        List<Move> legalMoves =
                moveGenerator.generateLegalMoves(
                        position
                );


        if (legalMoves.isEmpty()) {

            return List.of();
        }


        List<ChildMove> children =
                new ArrayList<>(
                        legalMoves.size()
                );


        for (Move move :
                legalMoves) {

            Position child =
                    position.makeMove(
                            move
                    );


            Probe childProbe =
                    probeExactChild(
                            child
                    );


            if (childProbe.outcome()
                    == Outcome.UNSUPPORTED) {

                /*
                 * Exact move classification is all-or-nothing.  If even one
                 * legal child is outside the exact dependency closure, do not
                 * pretend the move ranking is complete.
                 */
                return List.of();
            }


            children.add(
                    new ChildMove(
                            move,
                            childProbe
                    )
            );
        }


        List<MoveAnalysis> result =
                new ArrayList<>(
                        children.size()
                );


        for (ChildMove child :
                children) {

            result.add(
                    new MoveAnalysis(
                            child.move(),
                            child.probe().outcome(),
                            child.probe().mateDistance(),
                            isOptimalChild(
                                    parent.outcome(),
                                    child.probe(),
                                    children
                            )
                    )
            );
        }


        return List.copyOf(
                result
        );
    }


    public Position randomWinningPosition(
            Random random
    ) {

        if (threePiece != null) {

            return threePiece.randomWinningPosition(
                    random
            );
        }


        if (fourPiece != null) {

            return fourPiece.randomWinningPosition(
                    random
            );
        }


        throw new IllegalStateException(
                "Random winning-position generation is not exposed by "
                        + "the generic runtime catalog."
        );
    }


    /**
     * Return a proven exact WIN for a specific canonical four-piece family.
     *
     * The runtime services select directly from their persisted outcome
     * arrays, so this cannot fail merely because a family's winning states
     * are rare among all legal positions.
     */
    public Position randomWinningPosition(
            FourPieceMaterialClass material,
            Random random
    ) {

        if (material == null) {
            throw new IllegalArgumentException(
                    "Four-piece material cannot be null."
            );
        }

        if (random == null) {
            throw new IllegalArgumentException(
                    "Random source cannot be null."
            );
        }

        if (genericFourPiece == null) {
            throw new IllegalStateException(
                    "Material-specific winning-position generation requires "
                            + "the complete runtime catalog facade."
            );
        }

        try {
            int primitiveState;
            int enPassantTarget =
                    -1;

            if (material.pawnCount() == 0) {

                primitiveState =
                        genericFourPiece.randomWinningState(
                                material,
                                random
                        );

            } else if (material.pawnCount() == 1) {

                if (tierOne == null) {
                    throw new IllegalStateException(
                            "Tier-1 runtime service is unavailable."
                    );
                }

                primitiveState =
                        tierOne.randomWinningState(
                                material,
                                random
                        );

            } else if (material.pawnCount() == 2
                    && material.distribution()
                    == FourPieceMaterialClass.Distribution.SAME_SIDE) {

                if (tierTwoKppk == null) {
                    throw new IllegalStateException(
                            "Tier-2 KPPK runtime service is unavailable."
                    );
                }

                primitiveState =
                        tierTwoKppk.randomWinningState(
                                random
                        );

            } else if (material.pawnCount() == 2
                    && material.distribution()
                    == FourPieceMaterialClass.Distribution.SPLIT) {

                if (tierTwoKpkp == null) {
                    throw new IllegalStateException(
                            "Tier-2 KP-KP runtime service is unavailable."
                    );
                }

                int exactState =
                        tierTwoKpkp.randomWinningState(
                                random
                        );

                primitiveState =
                        FourPieceTierTwoKpkpStateIndex.baseState(
                                exactState
                        );

                if (FourPieceTierTwoKpkpStateIndex
                        .enPassantAvailable(
                                exactState
                        )) {

                    enPassantTarget =
                            FourPieceTierTwoKpkpStateIndex
                                    .enPassantTargetSquare(
                                            exactState
                                    );
                }

            } else {

                throw new IllegalArgumentException(
                        "Unsupported four-piece material family: "
                                + material.displayName()
                );
            }


            boolean colorReverse =
                    random.nextBoolean();

            Position result =
                    positionForCanonicalFourPieceState(
                            material,
                            primitiveState,
                            enPassantTarget,
                            colorReverse
                    );


            Probe verification =
                    probe(
                            result
                    );

            /*
             * Color reversal is presentation variety only. If a specialized
             * runtime ever declines a reversed root, fall back to the exact
             * canonical orientation rather than failing Next Position.
             */
            if (verification.outcome()
                    != Outcome.WIN
                    && colorReverse) {

                result =
                        positionForCanonicalFourPieceState(
                                material,
                                primitiveState,
                                enPassantTarget,
                                false
                        );

                verification =
                        probe(
                                result
                        );
            }


            if (verification.outcome()
                    != Outcome.WIN) {

                throw new IllegalStateException(
                        "Direct exact-WIN sampler produced a non-WIN root for "
                                + material.displayName()
                                + ": "
                                + verification.outcome()
                );
            }


            return result;

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Unable to load exact four-piece tablebase for "
                            + material.displayName()
                            + ".",
                    exception
            );
        }
    }


    private Probe probeRuntimeFourPiece(
            Position position
    ) {

        /*
         * Tier-2 KP-KP must be checked explicitly because it has its own
         * EP-aware compact state space and persistence format.
         */
        if (tierTwoKpkp != null
                && tierTwoKpkp.supports(
                position
        )) {

            try {

                Optional<FourPieceTierTwoKpkpTablebaseService.ProbeResult>
                        tierTwoProbe =
                        tierTwoKpkp.probe(
                                position
                        );

                if (tierTwoProbe.isPresent()) {

                    FourPieceTierTwoKpkpTablebaseService.ProbeResult probe =
                            tierTwoProbe.get();

                    return new Probe(
                            mapPrimitive(
                                    probe.outcome()
                            ),
                            probe.distance()
                    );
                }

            } catch (IOException exception) {

                return unsupported();
            }
        }


        if (tierTwoKppk != null
                && tierTwoKppk.supports(
                position
        )) {

            try {

                Optional<FourPieceTierTwoKppkTablebaseService.ProbeResult>
                        tierTwoProbe =
                        tierTwoKppk.probe(
                                position
                        );

                if (tierTwoProbe.isPresent()) {

                    FourPieceTierTwoKppkTablebaseService.ProbeResult probe =
                            tierTwoProbe.get();

                    return new Probe(
                            mapPrimitive(
                                    probe.outcome()
                            ),
                            probe.distance()
                    );
                }

            } catch (IOException exception) {

                return unsupported();
            }
        }


        if (tierOne != null
                && tierOne.supports(
                position
        )) {

            try {

                Optional<FourPieceTierOneTablebaseService.ProbeResult>
                        tierOneProbe =
                        tierOne.probe(
                                position
                        );

                if (tierOneProbe.isPresent()) {

                    FourPieceTierOneTablebaseService.ProbeResult probe =
                            tierOneProbe.get();

                    return new Probe(
                            mapPrimitive(
                                    probe.outcome()
                            ),
                            probe.distance()
                    );
                }

            } catch (IOException exception) {

                return unsupported();
            }
        }


        if (genericFourPiece != null
                && genericFourPiece.supports(
                position
        )) {

            try {

                Optional<FourPieceGenericTablebaseService.ProbeResult>
                        generic =
                        genericFourPiece.probe(
                                position
                        );

                if (generic.isPresent()) {

                    FourPieceGenericTablebaseService.ProbeResult probe =
                            generic.get();

                    return new Probe(
                            mapPrimitive(
                                    probe.outcome()
                            ),
                            probe.distance()
                    );
                }

            } catch (IOException exception) {

                return unsupported();
            }
        }


        return unsupported();
    }


    private Probe probeExactChild(
            Position position
    ) {

        int pieceCount =
                countPieces(
                        position
                );


        if (pieceCount == 4) {

            return probeRuntimeFourPiece(
                    position
            );
        }


        if (pieceCount == 3) {

            return probeThreePieceBoundary(
                    position
            );
        }


        return unsupported();
    }


    private Probe probeThreePieceBoundary(
            Position position
    ) {

        BoundaryMaterial material =
                recognizeThreePieceBoundary(
                        position
                );


        if (material == null) {

            return unsupported();
        }


        if (material.type()
                == PieceType.BISHOP
                || material.type()
                == PieceType.KNIGHT) {

            return new Probe(
                    Outcome.DRAW,
                    -1
            );
        }


        if (material.type()
                != PieceType.QUEEN
                && material.type()
                != PieceType.ROOK
                && material.type()
                != PieceType.PAWN) {

            return unsupported();
        }


        try {

            ThreePieceTablebase tablebase =
                    threePieceService.get(
                            material.type(),
                            material.color()
                    );


            ThreePieceTablebase.Probe probe =
                    tablebase.probe(
                            position
                    );


            return new Probe(
                    map(
                            probe.outcome()
                    ),
                    probe.mateDistance()
            );

        } catch (RuntimeException exception) {

            return unsupported();
        }
    }


    private static boolean isOptimalChild(
            Outcome parentOutcome,
            Probe candidate,
            List<ChildMove> allChildren
    ) {

        return switch (parentOutcome) {

            case WIN ->
                    candidate.outcome()
                            == Outcome.LOSS
                            && candidate.mateDistance()
                            == minimumDistance(
                            allChildren,
                            Outcome.LOSS
                    );

            case DRAW ->
                    candidate.outcome()
                            == Outcome.DRAW;

            case LOSS ->
                    candidate.outcome()
                            == Outcome.WIN
                            && candidate.mateDistance()
                            == maximumDistance(
                            allChildren,
                            Outcome.WIN
                    );

            case UNSUPPORTED ->
                    false;
        };
    }


    private static int minimumDistance(
            List<ChildMove> children,
            Outcome requiredOutcome
    ) {

        int best =
                Integer.MAX_VALUE;


        for (ChildMove child :
                children) {

            if (child.probe().outcome()
                    != requiredOutcome) {

                continue;
            }


            best =
                    Math.min(
                            best,
                            child.probe().mateDistance()
                    );
        }


        return best;
    }


    private static int maximumDistance(
            List<ChildMove> children,
            Outcome requiredOutcome
    ) {

        int best =
                Integer.MIN_VALUE;


        for (ChildMove child :
                children) {

            if (child.probe().outcome()
                    != requiredOutcome) {

                continue;
            }


            best =
                    Math.max(
                            best,
                            child.probe().mateDistance()
                    );
        }


        return best;
    }


    private static BoundaryMaterial recognizeThreePieceBoundary(
            Position position
    ) {

        if (position == null
                || position.getBoard() == null) {

            return null;
        }


        Board board =
                position.getBoard();


        int whiteKings =
                0;

        int blackKings =
                0;

        Piece extra =
                null;

        int pieceCount =
                0;


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


                if (piece == null) {

                    continue;
                }


                pieceCount++;


                if (piece.type()
                        == PieceType.KING) {

                    if (piece.color()
                            == Color.WHITE) {

                        whiteKings++;

                    } else {

                        blackKings++;
                    }

                } else {

                    if (extra != null) {

                        return null;
                    }


                    extra =
                            piece;
                }
            }
        }


        if (pieceCount != 3
                || whiteKings != 1
                || blackKings != 1
                || extra == null) {

            return null;
        }


        return new BoundaryMaterial(
                extra.type(),
                extra.color()
        );
    }


    private static int countPieces(
            Position position
    ) {

        if (position == null
                || position.getBoard() == null) {

            return 0;
        }


        int count =
                0;


        for (int rank = 0;
             rank < 8;
             rank++) {

            for (int file = 0;
                 file < 8;
                 file++) {

                if (position.getBoard()
                        .getPiece(
                                new Square(
                                        file,
                                        rank
                                )
                        )
                        != null) {

                    count++;
                }
            }
        }


        return count;
    }


    private static Position positionForCanonicalFourPieceState(
            FourPieceMaterialClass material,
            int primitiveState,
            int enPassantTarget,
            boolean colorReverse
    ) {

        int whiteKing =
                FourPieceGenericPrimitiveState.whiteKing(
                        primitiveState
                );

        int blackKing =
                FourPieceGenericPrimitiveState.blackKing(
                        primitiveState
                );

        int firstExtra =
                FourPieceGenericPrimitiveState.firstExtra(
                        primitiveState
                );

        int secondExtra =
                FourPieceGenericPrimitiveState.secondExtra(
                        primitiveState
                );

        boolean blackToMove =
                FourPieceGenericPrimitiveState.blackToMove(
                        primitiveState
                );

        boolean flipRanks =
                material.pawnCount() > 0;

        int actualWhiteKing =
                colorReverse
                        ? transformStudySquare(
                        blackKing,
                        flipRanks
                )
                        : whiteKing;

        int actualBlackKing =
                colorReverse
                        ? transformStudySquare(
                        whiteKing,
                        flipRanks
                )
                        : blackKing;

        int actualFirstExtra =
                colorReverse
                        ? transformStudySquare(
                        firstExtra,
                        flipRanks
                )
                        : firstExtra;

        int actualSecondExtra =
                colorReverse
                        ? transformStudySquare(
                        secondExtra,
                        flipRanks
                )
                        : secondExtra;

        int actualEnPassantTarget =
                enPassantTarget < 0
                        ? -1
                        : colorReverse
                        ? transformStudySquare(
                        enPassantTarget,
                        flipRanks
                )
                        : enPassantTarget;


        Color firstColor =
                colorReverse
                        ? Color.BLACK
                        : Color.WHITE;

        Color secondColor;

        if (material.distribution()
                == FourPieceMaterialClass.Distribution.SAME_SIDE) {

            secondColor =
                    firstColor;

        } else {

            secondColor =
                    colorReverse
                            ? Color.WHITE
                            : Color.BLACK;
        }


        Color sideToMove;

        if (!colorReverse) {

            sideToMove =
                    blackToMove
                            ? Color.BLACK
                            : Color.WHITE;

        } else {

            sideToMove =
                    blackToMove
                            ? Color.WHITE
                            : Color.BLACK;
        }


        Board board =
                new Board();

        board.setPiece(
                primitiveSquare(
                        actualWhiteKing
                ),
                new Piece(
                        PieceType.KING,
                        Color.WHITE
                )
        );

        board.setPiece(
                primitiveSquare(
                        actualBlackKing
                ),
                new Piece(
                        PieceType.KING,
                        Color.BLACK
                )
        );

        board.setPiece(
                primitiveSquare(
                        actualFirstExtra
                ),
                new Piece(
                        material.firstType(),
                        firstColor
                )
        );

        board.setPiece(
                primitiveSquare(
                        actualSecondExtra
                ),
                new Piece(
                        material.secondType(),
                        secondColor
                )
        );


        Square epSquare =
                actualEnPassantTarget < 0
                        ? null
                        : primitiveSquare(
                        actualEnPassantTarget
                );


        Position temporary =
                new Position(
                        board,
                        sideToMove,
                        false,
                        false,
                        false,
                        false,
                        epSquare,
                        0,
                        1,
                        new HashMap<>()
                );


        Map<PositionKey, Integer> repetitionCounts =
                new HashMap<>();

        repetitionCounts.put(
                temporary.createPositionKey(),
                1
        );


        return new Position(
                board,
                sideToMove,
                false,
                false,
                false,
                false,
                epSquare,
                0,
                1,
                repetitionCounts
        );
    }


    private static int transformStudySquare(
            int square,
            boolean flipRanks
    ) {

        if (!flipRanks) {
            return square;
        }

        return (7 - (square >>> 3)) * 8
                + (square & 7);
    }


    private static Square primitiveSquare(
            int square
    ) {

        return new Square(
                square & 7,
                square >>> 3
        );
    }


    private static Probe unsupported() {

        return new Probe(
                Outcome.UNSUPPORTED,
                -1
        );
    }


    private static Outcome mapPrimitive(
            byte outcome
    ) {

        if (outcome
                == FourPieceTablebase.WIN) {

            return Outcome.WIN;
        }


        if (outcome
                == FourPieceTablebase.LOSS) {

            return Outcome.LOSS;
        }


        if (outcome
                == FourPieceTablebase.DRAW) {

            return Outcome.DRAW;
        }


        return Outcome.UNSUPPORTED;
    }


    private static Outcome map(
            ThreePieceTablebase.Outcome outcome
    ) {

        return switch (outcome) {

            case WIN ->
                    Outcome.WIN;

            case LOSS ->
                    Outcome.LOSS;

            case DRAW ->
                    Outcome.DRAW;

            case UNSUPPORTED ->
                    Outcome.UNSUPPORTED;
        };
    }


    private static Outcome map(
            FourPieceTablebase.Outcome outcome
    ) {

        return switch (outcome) {

            case WIN ->
                    Outcome.WIN;

            case LOSS ->
                    Outcome.LOSS;

            case DRAW ->
                    Outcome.DRAW;

            case UNSUPPORTED ->
                    Outcome.UNSUPPORTED;
        };
    }


    private record ChildMove(
            Move move,
            Probe probe
    ) {
    }


    private record BoundaryMaterial(
            PieceType type,
            Color color
    ) {
    }
}

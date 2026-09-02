package main.java.chess.endgame;

import main.java.chess.model.Board;
import main.java.chess.model.Color;
import main.java.chess.model.Piece;
import main.java.chess.model.PieceType;
import main.java.chess.model.Position;
import main.java.chess.model.Square;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;


/**
 * Runtime service for the complete persisted four-piece Tier-0 catalog.
 *
 * Tier 0 means:
 *
 *     exactly four pieces
 *     exactly one white king
 *     exactly one black king
 *     exactly two non-king pieces
 *     no pawns
 *
 * The service:
 *
 *     Position
 *         ->
 *     recognize canonical material family
 *         ->
 *     normalize color/orientation
 *         ->
 *     encode FourPieceGenericPrimitiveState
 *         ->
 *     lazily load/cache the persisted tablebase
 *         ->
 *     return exact WDL/DTM
 *
 * No retrograde solving occurs here.
 */
public final class FourPieceGenericTablebaseService {

    private static final Path DEFAULT_DIRECTORY =
            Path.of(
                    "tablebases",
                    "four-piece"
            );


    private final Path directory;

    private final Map<FourPieceMaterialClass, FourPieceGenericTablebase>
            cache =
            new HashMap<>();


    public FourPieceGenericTablebaseService() {

        this(
                DEFAULT_DIRECTORY
        );
    }


    public FourPieceGenericTablebaseService(
            Path directory
    ) {

        if (directory == null) {

            throw new IllegalArgumentException(
                    "Tablebase directory cannot be null."
            );
        }


        this.directory =
                directory.toAbsolutePath()
                        .normalize();
    }


    /**
     * Probe a normal engine Position.
     *
     * Optional.empty() means the position is not eligible for this
     * particular runtime tablebase service.
     *
     * Examples:
     *
     *     not exactly four pieces
     *     pawn material
     *     malformed king count
     *     active castling rights
     *     primitive state marked INVALID
     */
    public Optional<ProbeResult> probe(
            Position position
    ) throws IOException {

        if (position == null) {

            throw new IllegalArgumentException(
                    "Position cannot be null."
            );
        }


        CanonicalPosition canonical =
                canonicalize(
                        position
                );


        if (canonical == null) {

            return Optional.empty();
        }


        FourPieceGenericTablebase tablebase =
                tablebase(
                        canonical.material()
                );


        int state =
                FourPieceGenericPrimitiveState.encode(
                        canonical.whiteKing(),
                        canonical.blackKing(),
                        canonical.firstExtra(),
                        canonical.secondExtra(),
                        canonical.blackToMove()
                );


        state =
                FourPieceGenericPrimitiveState.canonicalize(
                        state,
                        canonical.material()
                );


        byte outcome =
                tablebase.outcome(
                        state
                );


        if (outcome
                == FourPieceTablebase.INVALID) {

            return Optional.empty();
        }


        short distance =
                tablebase.distance(
                        state
                );


        return Optional.of(
                new ProbeResult(
                        canonical.material(),
                        outcome,
                        distance,
                        canonical.colorReversed(),
                        state
                )
        );
    }


    /**
     * Returns true when the position can be represented by the
     * persisted Tier-0 catalog.
     *
     * This does not load a tablebase file.
     */
    public boolean supports(
            Position position
    ) {

        if (position == null) {

            return false;
        }


        return canonicalize(
                position
        ) != null;
    }


    public int loadedTablebaseCount() {

        return cache.size();
    }


    public void clearCache() {

        cache.clear();
    }


    public Path directory() {

        return directory;
    }


    /**
     * Select a proven exact WIN directly from the persisted outcome array.
     *
     * This deliberately avoids random board rejection sampling. Some exact
     * four-piece families contain only a tiny number of winning states, so a
     * bounded random-position search can fail even though wins do exist.
     */
    public int randomWinningState(
            FourPieceMaterialClass material,
            Random random
    ) throws IOException {

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

        if (material.pawnCount() != 0) {
            throw new IllegalArgumentException(
                    "Tier-0 sampler requires pawnless material: "
                            + material.displayName()
            );
        }

        FourPieceGenericTablebase tablebase =
                tablebase(
                        material
                );

        return firstWinningStateFromRandomOffset(
                tablebase.outcomeData(),
                random,
                tablebase.material().displayName()
        );
    }


    private static int firstWinningStateFromRandomOffset(
            byte[] outcome,
            Random random,
            String label
    ) {

        if (outcome == null
                || outcome.length == 0) {
            throw new IllegalStateException(
                    "Empty exact outcome array for "
                            + label
                            + "."
            );
        }

        int start =
                random.nextInt(
                        outcome.length
                );

        for (int offset = 0;
             offset < outcome.length;
             offset++) {

            int state =
                    start + offset;

            if (state >= outcome.length) {
                state -= outcome.length;
            }

            if (outcome[state]
                    == FourPieceTablebase.WIN) {
                return state;
            }
        }

        throw new IllegalStateException(
                "Exact tablebase contains no WIN state for "
                        + label
                        + "."
        );
    }

    private FourPieceGenericTablebase tablebase(
            FourPieceMaterialClass material
    ) throws IOException {

        FourPieceGenericTablebase existing =
                cache.get(
                        material
                );


        if (existing != null) {

            return existing;
        }


        Path path =
                assetPath(
                        material
                );


        if (!Files.exists(
                path
        )) {

            throw new IOException(
                    "Missing Tier-0 tablebase asset for "
                            + material.displayName()
                            + ": "
                            + path
            );
        }


        FourPieceGenericTablebase loaded =
                FourPieceGenericTablebaseCodec.load(
                        path
                );


        validateLoadedTablebase(
                material,
                loaded,
                path
        );


        cache.put(
                material,
                loaded
        );


        return loaded;
    }


    private Path assetPath(
            FourPieceMaterialClass material
    ) {

        String suffix =
                switch (material.distribution()) {

                    case SAME_SIDE ->
                            "-white.ftb.gz";

                    case SPLIT ->
                            "-canonical.ftb.gz";
                };


        return directory.resolve(
                material.assetStem()
                        + suffix
        );
    }


    private static void validateLoadedTablebase(
            FourPieceMaterialClass expectedMaterial,
            FourPieceGenericTablebase tablebase,
            Path path
    ) throws IOException {

        if (!tablebase.material()
                .equals(
                        expectedMaterial
                )) {

            throw new IOException(
                    "Wrong material stored in "
                            + path
                            + ". Expected "
                            + expectedMaterial.displayName()
                            + ", found "
                            + tablebase.material()
                            .displayName()
                            + "."
            );
        }


        /*
         * Milestone 20 persisted the canonical orientation using true.
         *
         * SAME_SIDE:
         *     extras belong to White.
         *
         * SPLIT:
         *     first extra belongs to White,
         *     second extra belongs to Black.
         *
         * The generic solver still stores the boolean metadata for both.
         */
        if (!tablebase.sameSideOwnerIsWhite()) {

            throw new IOException(
                    "Non-canonical orientation stored in "
                            + path
            );
        }
    }


    /**
     * Convert an arbitrary engine position into the exact canonical
     * orientation used by the persisted Tier-0 catalog.
     */
    private static CanonicalPosition canonicalize(
            Position position
    ) {

        /*
         * Castling is not represented by the primitive Tier-0 state.
         *
         * We therefore must not silently probe positions where castling
         * remains legally available according to Position.
         */
        if (position.canWhiteCastleKingSide()
                || position.canWhiteCastleQueenSide()
                || position.canBlackCastleKingSide()
                || position.canBlackCastleQueenSide()) {

            return null;
        }


        Board board =
                position.getBoard();


        if (board == null
                || position.getSideToMove() == null) {

            return null;
        }


        int whiteKing =
                -1;

        int blackKing =
                -1;


        ExtraPiece firstFound =
                null;

        ExtraPiece secondFound =
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
                        board.getPiece(
                                square
                        );


                if (piece == null) {

                    continue;
                }


                pieceCount++;


                if (pieceCount > 4) {

                    return null;
                }


                int primitiveSquare =
                        primitiveSquare(
                                file,
                                rank
                        );


                if (piece.type()
                        == PieceType.KING) {

                    if (piece.color()
                            == Color.WHITE) {

                        if (whiteKing != -1) {

                            return null;
                        }


                        whiteKing =
                                primitiveSquare;

                    } else {

                        if (blackKing != -1) {

                            return null;
                        }


                        blackKing =
                                primitiveSquare;
                    }


                    continue;
                }


                /*
                 * Tier 0 contains no pawns.
                 */
                if (piece.type()
                        == PieceType.PAWN) {

                    return null;
                }


                if (!isTierZeroExtraType(
                        piece.type()
                )) {

                    return null;
                }


                ExtraPiece extra =
                        new ExtraPiece(
                                piece.type(),
                                piece.color(),
                                primitiveSquare
                        );


                if (firstFound == null) {

                    firstFound =
                            extra;

                } else if (secondFound == null) {

                    secondFound =
                            extra;

                } else {

                    return null;
                }
            }
        }


        if (pieceCount != 4
                || whiteKing == -1
                || blackKing == -1
                || firstFound == null
                || secondFound == null) {

            return null;
        }


        boolean blackToMove =
                position.getSideToMove()
                        == Color.BLACK;


        /*
         * ============================================================
         * SAME-SIDE MATERIAL
         * ============================================================
         *
         * Both extras have the same owner.
         *
         * Persisted canonical orientation:
         *
         *     both extras belong to WHITE.
         *
         * If they actually belong to Black, color reversal is enough:
         *
         *     white king <-> black king
         *     side to move reverses
         *
         * The physical board squares do not change.
         */
        if (firstFound.color()
                == secondFound.color()) {

            PieceType firstType =
                    earlierType(
                            firstFound.type(),
                            secondFound.type()
                    );

            PieceType secondType =
                    laterType(
                            firstFound.type(),
                            secondFound.type()
                    );


            FourPieceMaterialClass material =
                    FourPieceMaterialClass.sameSide(
                            firstType,
                            secondType
                    );


            ExtraPiece firstExtra =
                    selectExtra(
                            firstFound,
                            secondFound,
                            material.firstType(),
                            true
                    );


            ExtraPiece secondExtra =
                    selectExtra(
                            firstFound,
                            secondFound,
                            material.secondType(),
                            false
                    );


            /*
             * Identical same-side pieces are interchangeable.
             *
             * Use square ordering immediately. Primitive-state
             * canonicalization below provides a second safeguard.
             */
            int firstSquare =
                    firstExtra.square();

            int secondSquare =
                    secondExtra.square();


            if (material.firstType()
                    == material.secondType()
                    && firstSquare > secondSquare) {

                int temporary =
                        firstSquare;

                firstSquare =
                        secondSquare;

                secondSquare =
                        temporary;
            }


            boolean colorReversed =
                    firstFound.color()
                            == Color.BLACK;


            if (colorReversed) {

                int temporaryKing =
                        whiteKing;

                whiteKing =
                        blackKing;

                blackKing =
                        temporaryKing;


                blackToMove =
                        !blackToMove;
            }


            return new CanonicalPosition(
                    material,
                    whiteKing,
                    blackKing,
                    firstSquare,
                    secondSquare,
                    blackToMove,
                    colorReversed
            );
        }


        /*
         * ============================================================
         * SPLIT MATERIAL
         * ============================================================
         *
         * One extra belongs to each color.
         *
         * FourPieceMaterialClass canonicalizes the TYPE pair:
         *
         *     Q < R < B < N
         *
         * Persisted generic orientation requires:
         *
         *     firstType  = WHITE extra
         *     secondType = BLACK extra
         *
         * If the actual colors are opposite to that ordering, reverse
         * all colors.
         */
        ExtraPiece whiteExtra =
                firstFound.color()
                        == Color.WHITE
                        ? firstFound
                        : secondFound;


        ExtraPiece blackExtra =
                firstFound.color()
                        == Color.BLACK
                        ? firstFound
                        : secondFound;


        FourPieceMaterialClass material =
                FourPieceMaterialClass.split(
                        whiteExtra.type(),
                        blackExtra.type()
                );


        boolean colorReversed;


        int firstExtraSquare;

        int secondExtraSquare;


        if (whiteExtra.type()
                == material.firstType()
                && blackExtra.type()
                == material.secondType()) {

            /*
             * Already canonical.
             */
            colorReversed =
                    false;

            firstExtraSquare =
                    whiteExtra.square();

            secondExtraSquare =
                    blackExtra.square();

        } else if (blackExtra.type()
                == material.firstType()
                && whiteExtra.type()
                == material.secondType()) {

            /*
             * Color reversal makes the lower-ordered material type
             * become White's first extra.
             */
            colorReversed =
                    true;

            firstExtraSquare =
                    blackExtra.square();

            secondExtraSquare =
                    whiteExtra.square();


            int temporaryKing =
                    whiteKing;

            whiteKing =
                    blackKing;

            blackKing =
                    temporaryKing;


            blackToMove =
                    !blackToMove;

        } else {

            /*
             * This should be impossible if FourPieceMaterialClass and
             * the scan above are behaving correctly.
             */
            throw new IllegalStateException(
                    "Unable to canonicalize split material "
                            + whiteExtra.type()
                            + " / "
                            + blackExtra.type()
            );
        }


        return new CanonicalPosition(
                material,
                whiteKing,
                blackKing,
                firstExtraSquare,
                secondExtraSquare,
                blackToMove,
                colorReversed
        );
    }


    /**
     * Board scanning uses rank-major ordering, matching the primitive
     * square convention used throughout the tablebase implementation:
     *
     *     a1 = 0
     *     b1 = 1
     *     ...
     *     h1 = 7
     *     a2 = 8
     *     ...
     *     h8 = 63
     */
    private static int primitiveSquare(
            int file,
            int rank
    ) {

        return rank * 8
                + file;
    }


    private static boolean isTierZeroExtraType(
            PieceType type
    ) {

        return type == PieceType.QUEEN
                || type == PieceType.ROOK
                || type == PieceType.BISHOP
                || type == PieceType.KNIGHT;
    }


    private static PieceType earlierType(
            PieceType first,
            PieceType second
    ) {

        return typeOrder(first)
                <= typeOrder(second)
                ? first
                : second;
    }


    private static PieceType laterType(
            PieceType first,
            PieceType second
    ) {

        return typeOrder(first)
                <= typeOrder(second)
                ? second
                : first;
    }


    private static int typeOrder(
            PieceType type
    ) {

        return switch (type) {

            case QUEEN ->
                    0;

            case ROOK ->
                    1;

            case BISHOP ->
                    2;

            case KNIGHT ->
                    3;

            default ->
                    throw new IllegalArgumentException(
                            "Not Tier-0 material: "
                                    + type
                    );
        };
    }


    /**
     * Select an extra matching a material type.
     *
     * For different types selection is unambiguous.
     *
     * For identical types:
     *
     *     firstSelection = lower square
     *     secondSelection = higher square
     */
    private static ExtraPiece selectExtra(
            ExtraPiece first,
            ExtraPiece second,
            PieceType requiredType,
            boolean firstSelection
    ) {

        boolean firstMatches =
                first.type()
                        == requiredType;

        boolean secondMatches =
                second.type()
                        == requiredType;


        if (firstMatches
                && secondMatches) {

            if (firstSelection) {

                return first.square()
                        <= second.square()
                        ? first
                        : second;
            }


            return first.square()
                    <= second.square()
                    ? second
                    : first;
        }


        if (firstMatches) {

            return first;
        }


        if (secondMatches) {

            return second;
        }


        throw new IllegalStateException(
                "Required extra type not present: "
                        + requiredType
        );
    }


    public record ProbeResult(
            FourPieceMaterialClass material,
            byte outcome,
            short distance,
            boolean colorReversed,
            int primitiveState
    ) {

        public boolean isWin() {

            return outcome
                    == FourPieceTablebase.WIN;
        }


        public boolean isLoss() {

            return outcome
                    == FourPieceTablebase.LOSS;
        }


        public boolean isDraw() {

            return outcome
                    == FourPieceTablebase.DRAW;
        }


        public String outcomeName() {

            if (isWin()) {

                return "WIN";
            }


            if (isLoss()) {

                return "LOSS";
            }


            if (isDraw()) {

                return "DRAW";
            }


            return "UNKNOWN";
        }
    }


    private record ExtraPiece(
            PieceType type,
            Color color,
            int square
    ) {

    }


    private record CanonicalPosition(
            FourPieceMaterialClass material,
            int whiteKing,
            int blackKing,
            int firstExtra,
            int secondExtra,
            boolean blackToMove,
            boolean colorReversed
    ) {

    }
}

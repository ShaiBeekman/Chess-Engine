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
import java.util.Optional;
import java.util.Random;


/**
 * M63 runtime service for the persisted EP-aware SPLIT KP-KP asset.
 *
 * The physical orientation is already canonical: WHITE pawn first, BLACK pawn
 * second. Unlike KPPK, no color normalization is required for normal probing.
 */
public final class FourPieceTierTwoKpkpTablebaseService {

    public static final Path DEFAULT_DIRECTORY =
            Path.of("tablebases", "four-piece");

    private static final FourPieceMaterialClass MATERIAL =
            FourPieceMaterialClass.split(
                    PieceType.PAWN,
                    PieceType.PAWN
            );

    private final Path directory;

    private FourPieceTierTwoKpkpTablebaseCodec.Tablebase cached;

    public FourPieceTierTwoKpkpTablebaseService() {
        this(DEFAULT_DIRECTORY);
    }

    public FourPieceTierTwoKpkpTablebaseService(
            Path directory
    ) {

        if (directory == null) {
            throw new IllegalArgumentException(
                    "Tier-2 KP-KP directory cannot be null."
            );
        }

        this.directory =
                directory.toAbsolutePath()
                        .normalize();
    }

    public synchronized Optional<ProbeResult> probe(
            Position position
    ) throws IOException {

        CanonicalPosition canonical =
                canonicalize(position);

        if (canonical == null) {
            return Optional.empty();
        }

        Path asset =
                assetPath();

        if (!Files.isRegularFile(asset)) {
            return Optional.empty();
        }

        FourPieceTierTwoKpkpTablebaseCodec.Tablebase tablebase =
                tablebase();

        int base =
                FourPieceGenericPrimitiveState.encode(
                        canonical.whiteKing(),
                        canonical.blackKing(),
                        canonical.whitePawn(),
                        canonical.blackPawn(),
                        canonical.blackToMove()
                );

        boolean epAvailable =
                relevantEnPassant(
                        position,
                        base
                );

        int state =
                FourPieceTierTwoKpkpStateIndex.ofBase(
                        base,
                        epAvailable
                );

        byte outcome =
                tablebase.outcome(state);

        if (outcome
                == FourPieceTablebase.INVALID) {

            return Optional.empty();
        }

        return Optional.of(
                new ProbeResult(
                        MATERIAL,
                        outcome,
                        tablebase.distance(state),
                        false,
                        state,
                        epAvailable
                )
        );
    }

    public boolean supports(
            Position position
    ) {

        return canonicalize(position) != null
                && Files.isRegularFile(
                assetPath()
        );
    }

    public synchronized boolean isLoaded() {
        return cached != null;
    }

    public synchronized int loadedCount() {
        return cached == null
                ? 0
                : 1;
    }

    public synchronized void clearCache() {
        cached = null;
    }

    public Path directory() {
        return directory;
    }

    public Path assetPath() {
        return directory.resolve(
                MATERIAL.assetStem()
                        + "-canonical-ep.ftb.gz"
        );
    }

    public Path assetPath(
            FourPieceMaterialClass material
    ) {

        if (!MATERIAL.equals(material)) {
            throw new IllegalArgumentException(
                    "Expected canonical SPLIT KP-KP material."
            );
        }

        return assetPath();
    }


    /**
     * Select a proven exact KP-KP WIN directly from the complete persisted
     * outcome array, including the EP-aware overlay states.
     */
    public int randomWinningState(
            Random random
    ) throws IOException {

        if (random == null) {
            throw new IllegalArgumentException(
                    "Random source cannot be null."
            );
        }

        FourPieceTierTwoKpkpTablebaseCodec.Tablebase tablebase =
                tablebase();

        return firstWinningStateFromRandomOffset(
                tablebase,
                random
        );
    }


    private static int firstWinningStateFromRandomOffset(
            FourPieceTierTwoKpkpTablebaseCodec.Tablebase tablebase,
            Random random
    ) {

        if (tablebase == null
                || tablebase.stateCount() == 0) {
            throw new IllegalStateException(
                    "Exact KP-KP tablebase contains no WIN state."
            );
        }

        int stateCount =
                tablebase.stateCount();

        int start =
                random.nextInt(
                        stateCount
                );

        for (int offset = 0;
             offset < stateCount;
             offset++) {

            int state =
                    start + offset;

            if (state >= stateCount) {
                state -= stateCount;
            }

            if (tablebase.outcome(state)
                    == FourPieceTablebase.WIN) {
                return state;
            }
        }

        throw new IllegalStateException(
                "Exact KP-KP tablebase contains no WIN state."
        );
    }

    private synchronized FourPieceTierTwoKpkpTablebaseCodec.Tablebase
    tablebase() throws IOException {

        if (cached != null) {
            return cached;
        }

        Path path =
                assetPath();

        if (!Files.isRegularFile(path)) {
            throw new IOException(
                    "Missing Tier-2 KP-KP asset: "
                            + path
            );
        }

        cached =
                FourPieceTierTwoKpkpTablebaseCodec.load(
                        path
                );

        return cached;
    }

    private static CanonicalPosition canonicalize(
            Position position
    ) {

        if (position == null
                || position.getBoard() == null
                || position.getSideToMove() == null) {

            return null;
        }

        if (position.canWhiteCastleKingSide()
                || position.canWhiteCastleQueenSide()
                || position.canBlackCastleKingSide()
                || position.canBlackCastleQueenSide()) {

            return null;
        }

        Board board =
                position.getBoard();

        int whiteKing =
                -1;

        int blackKing =
                -1;

        int whitePawn =
                -1;

        int blackPawn =
                -1;

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

                if (pieceCount > 4) {
                    return null;
                }

                int square =
                        rank * 8
                                + file;

                if (piece.type()
                        == PieceType.KING) {

                    if (piece.color()
                            == Color.WHITE) {

                        if (whiteKing >= 0) {
                            return null;
                        }

                        whiteKing =
                                square;

                    } else {

                        if (blackKing >= 0) {
                            return null;
                        }

                        blackKing =
                                square;
                    }

                    continue;
                }

                if (piece.type()
                        != PieceType.PAWN
                        || rank == 0
                        || rank == 7) {

                    return null;
                }

                if (piece.color()
                        == Color.WHITE) {

                    if (whitePawn >= 0) {
                        return null;
                    }

                    whitePawn =
                            square;

                } else {

                    if (blackPawn >= 0) {
                        return null;
                    }

                    blackPawn =
                            square;
                }
            }
        }

        if (pieceCount != 4
                || whiteKing < 0
                || blackKing < 0
                || whitePawn < 0
                || blackPawn < 0) {

            return null;
        }

        return new CanonicalPosition(
                whiteKing,
                blackKing,
                whitePawn,
                blackPawn,
                position.getSideToMove()
                        == Color.BLACK
        );
    }

    /**
     * Engine Position records can contain an EP target after every double
     * push. The exact KP-KP overlay is set only when that target is the
     * history-relevant target for this board and side to move.
     */
    private static boolean relevantEnPassant(
            Position position,
            int base
    ) {

        Square target =
                position.getEnPassantTarget();

        if (target == null) {
            return false;
        }

        if (!FourPieceTierTwoKpkpPrimitiveState
                .canCarryEnPassant(base)) {

            return false;
        }

        FourPieceTierTwoKpkpPrimitiveState.State state =
                FourPieceTierTwoKpkpPrimitiveState.of(
                        base,
                        true
                );

        int expected =
                FourPieceTierTwoKpkpPrimitiveState
                        .enPassantTargetSquare(
                                state
                        );

        int actual =
                target.rank() * 8
                        + target.file();

        return actual == expected;
    }

    public record ProbeResult(
            FourPieceMaterialClass material,
            byte outcome,
            short distance,
            boolean colorReversed,
            int primitiveState,
            boolean enPassantAvailable
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

            return isWin()
                    ? "WIN"
                    : isLoss()
                    ? "LOSS"
                    : isDraw()
                    ? "DRAW"
                    : "UNKNOWN";
        }
    }

    private record CanonicalPosition(
            int whiteKing,
            int blackKing,
            int whitePawn,
            int blackPawn,
            boolean blackToMove
    ) {
    }
}

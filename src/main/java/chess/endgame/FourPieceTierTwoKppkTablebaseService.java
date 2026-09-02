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
 * Milestone 59 runtime service for the persisted canonical SAME_SIDE KPPK asset.
 *
 * The only stored orientation is WHITE-owned KPPK. BLACK-owned positions are
 * color-reversed and vertically reflected before probing.
 */
public final class FourPieceTierTwoKppkTablebaseService {

    public static final String BUILD_ID =
            "M59-KPPK-EP-IGNORED-V2";

    public static final Path DEFAULT_DIRECTORY =
            Path.of("tablebases", "four-piece");

    private static final FourPieceMaterialClass MATERIAL =
            FourPieceMaterialClass.sameSide(PieceType.PAWN, PieceType.PAWN);

    private final Path directory;
    private FourPieceGenericTablebase cached;

    public FourPieceTierTwoKppkTablebaseService() {
        this(DEFAULT_DIRECTORY);
    }

    public FourPieceTierTwoKppkTablebaseService(Path directory) {
        if (directory == null) {
            throw new IllegalArgumentException("Tier-2 KPPK tablebase directory cannot be null.");
        }
        this.directory = directory.toAbsolutePath().normalize();
    }

    public synchronized Optional<ProbeResult> probe(Position position) throws IOException {
        CanonicalPosition canonical = canonicalize(position);
        if (canonical == null) return Optional.empty();

        Path asset = assetPath();
        if (!Files.isRegularFile(asset)) return Optional.empty();

        FourPieceGenericTablebase tablebase = tablebase();

        int raw = FourPieceGenericPrimitiveState.encode(
                canonical.whiteKing(),
                canonical.blackKing(),
                canonical.firstPawn(),
                canonical.secondPawn(),
                canonical.blackToMove());

        int state = FourPieceGenericPrimitiveState.canonicalize(raw, MATERIAL);

        byte outcome = tablebase.outcome(state);
        if (outcome == FourPieceTablebase.INVALID) return Optional.empty();

        return Optional.of(new ProbeResult(
                MATERIAL,
                outcome,
                tablebase.distance(state),
                canonical.colorReversed(),
                state));
    }

    public boolean supports(Position position) {
        return canonicalize(position) != null && Files.isRegularFile(assetPath());
    }

    public synchronized boolean isLoaded() {
        return cached != null;
    }

    public synchronized int loadedCount() {
        return cached == null ? 0 : 1;
    }

    public synchronized void clearCache() {
        cached = null;
    }

    public Path directory() {
        return directory;
    }

    public Path assetPath() {
        return directory.resolve(MATERIAL.assetStem() + "-white.ftb.gz");
    }

    public Path assetPath(FourPieceMaterialClass material) {
        if (!MATERIAL.equals(material)) {
            throw new IllegalArgumentException("Expected canonical SAME_SIDE KPPK material.");
        }
        return assetPath();
    }


    /**
     * Select a proven exact KPPK WIN directly from the persisted outcome
     * array instead of searching random board geometries.
     */
    public int randomWinningState(
            Random random
    ) throws IOException {

        if (random == null) {
            throw new IllegalArgumentException(
                    "Random source cannot be null."
            );
        }

        FourPieceGenericTablebase tablebase =
                tablebase();

        return firstWinningStateFromRandomOffset(
                tablebase.outcomeData(),
                random
        );
    }


    private static int firstWinningStateFromRandomOffset(
            byte[] outcome,
            Random random
    ) {

        if (outcome == null
                || outcome.length == 0) {
            throw new IllegalStateException(
                    "Exact KPPK tablebase contains no WIN state."
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
                "Exact KPPK tablebase contains no WIN state."
        );
    }

    private synchronized FourPieceGenericTablebase tablebase() throws IOException {
        if (cached != null) return cached;

        Path path = assetPath();
        if (!Files.isRegularFile(path)) {
            throw new IOException("Missing Tier-2 KPPK tablebase asset: " + path);
        }

        FourPieceGenericTablebase loaded = FourPieceGenericTablebaseCodec.load(path);

        if (!loaded.material().equals(MATERIAL)) {
            throw new IOException("Wrong material stored in Tier-2 KPPK asset. Expected "
                    + MATERIAL.displayName() + ", found " + loaded.material().displayName() + ".");
        }

        if (!loaded.sameSideOwnerIsWhite()) {
            throw new IOException("Tier-2 KPPK asset is not canonical WHITE-owned KPPK.");
        }

        cached = loaded;
        return loaded;
    }

    private static CanonicalPosition canonicalize(Position position) {
        if (position == null || position.getBoard() == null || position.getSideToMove() == null) {
            return null;
        }

        if (position.canWhiteCastleKingSide() || position.canWhiteCastleQueenSide()
                || position.canBlackCastleKingSide() || position.canBlackCastleQueenSide()) {
            return null;
        }

        /*
         * KPPK has no opposing pawn, so an engine Position may transiently
         * retain a double-push EP target even though no en-passant capture is
         * possible. It is irrelevant to KPPK primitive identity and is ignored.
         */

        Board board = position.getBoard();
        int whiteKing = -1;
        int blackKing = -1;
        int firstPawn = -1;
        int secondPawn = -1;
        int pieceCount = 0;
        Color pawnColor = null;

        for (int rank = 0; rank < 8; rank++) {
            for (int file = 0; file < 8; file++) {
                Piece piece = board.getPiece(new Square(file, rank));
                if (piece == null) continue;

                if (++pieceCount > 4) return null;
                int square = rank * 8 + file;

                if (piece.type() == PieceType.KING) {
                    if (piece.color() == Color.WHITE) {
                        if (whiteKing != -1) return null;
                        whiteKing = square;
                    } else {
                        if (blackKing != -1) return null;
                        blackKing = square;
                    }
                    continue;
                }

                if (piece.type() != PieceType.PAWN || rank == 0 || rank == 7) {
                    return null;
                }

                if (pawnColor == null) {
                    pawnColor = piece.color();
                } else if (pawnColor != piece.color()) {
                    // KP-KP is a separate EP-aware Tier-2 family.
                    return null;
                }

                if (firstPawn == -1) firstPawn = square;
                else if (secondPawn == -1) secondPawn = square;
                else return null;
            }
        }

        if (pieceCount != 4 || whiteKing < 0 || blackKing < 0
                || firstPawn < 0 || secondPawn < 0 || pawnColor == null) {
            return null;
        }

        boolean blackToMove = position.getSideToMove() == Color.BLACK;
        boolean colorReversed = pawnColor == Color.BLACK;

        if (!colorReversed) {
            return new CanonicalPosition(
                    whiteKing, blackKing, firstPawn, secondPawn, blackToMove, false);
        }

        return new CanonicalPosition(
                flipRank(blackKing),
                flipRank(whiteKing),
                flipRank(firstPawn),
                flipRank(secondPawn),
                !blackToMove,
                true);
    }

    private static int flipRank(int square) {
        return (7 - (square >>> 3)) * 8 + (square & 7);
    }

    public record ProbeResult(
            FourPieceMaterialClass material,
            byte outcome,
            short distance,
            boolean colorReversed,
            int primitiveState) {

        public boolean isWin() { return outcome == FourPieceTablebase.WIN; }
        public boolean isLoss() { return outcome == FourPieceTablebase.LOSS; }
        public boolean isDraw() { return outcome == FourPieceTablebase.DRAW; }

        public String outcomeName() {
            return isWin() ? "WIN" : isLoss() ? "LOSS" : isDraw() ? "DRAW" : "UNKNOWN";
        }
    }

    private record CanonicalPosition(
            int whiteKing,
            int blackKing,
            int firstPawn,
            int secondPawn,
            boolean blackToMove,
            boolean colorReversed) {
    }
}

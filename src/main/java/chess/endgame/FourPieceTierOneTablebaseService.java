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
 * Milestone 46.
 *
 * Generic runtime service for persisted Tier-1 one-pawn four-piece assets.
 *
 * Supported material shapes are the eight canonical Tier-1 classes:
 *
 *   SAME_SIDE: KQPK, KRPK, KBPK, KNPK
 *   SPLIT:     KQ-KP, KR-KP, KB-KP, KN-KP
 *
 * Assets are stored in one canonical orientation:
 *
 *   SAME_SIDE: owner of firstExtra + pawn is WHITE.
 *   SPLIT:     firstExtra is WHITE and pawn is BLACK.
 *
 * SAME_SIDE positions whose material owner is BLACK are color-reversed and
 * vertically reflected before probing. SPLIT positions are normalized either
 * directly (white nonpawn / black pawn) or by the same color-reversal when the
 * physical colors are black nonpawn / white pawn.
 *
 * Vertical reflection is necessary because pawn direction is color-dependent.
 */
public final class FourPieceTierOneTablebaseService {

    public static final Path DEFAULT_DIRECTORY =
            Path.of("tablebases", "four-piece");

    private final Path directory;
    private final Map<FourPieceMaterialClass, FourPieceGenericTablebase> cache =
            new HashMap<>();

    public FourPieceTierOneTablebaseService() {
        this(DEFAULT_DIRECTORY);
    }

    public FourPieceTierOneTablebaseService(Path directory) {
        if (directory == null) {
            throw new IllegalArgumentException("Tier-1 tablebase directory cannot be null.");
        }
        this.directory = directory.toAbsolutePath().normalize();
    }

    public synchronized Optional<ProbeResult> probe(Position position) throws IOException {
        CanonicalPosition canonical = canonicalize(position);
        if (canonical == null) return Optional.empty();

        Path asset = assetPath(canonical.material());
        if (!Files.isRegularFile(asset)) {
            // An otherwise recognizable Tier-1 class is simply not runtime-supported
            // until its validated asset exists.
            return Optional.empty();
        }

        FourPieceGenericTablebase tablebase = tablebase(canonical.material());
        int state = FourPieceGenericPrimitiveState.encode(
                canonical.whiteKing(), canonical.blackKing(), canonical.firstExtra(),
                canonical.pawn(), canonical.blackToMove());

        byte outcome = tablebase.outcome(state);
        if (outcome == FourPieceTablebase.INVALID) return Optional.empty();

        return Optional.of(new ProbeResult(
                canonical.material(), outcome, tablebase.distance(state),
                canonical.colorReversed(), state));
    }

    public boolean supports(Position position) {
        CanonicalPosition canonical = canonicalize(position);
        return canonical != null && Files.isRegularFile(assetPath(canonical.material()));
    }

    public synchronized boolean isLoaded(FourPieceMaterialClass material) {
        return cache.containsKey(material);
    }

    public synchronized int loadedCount() {
        return cache.size();
    }

    public synchronized void clearCache() {
        cache.clear();
    }

    public Path directory() { return directory; }

    public Path assetPath(FourPieceMaterialClass material) {
        if (material == null || !FourPieceTierOnePrimitiveMoveGenerator.supports(material)) {
            throw new IllegalArgumentException("Expected a canonical Tier-1 one-pawn material class.");
        }
        String suffix =
                material.distribution()
                        == FourPieceMaterialClass.Distribution.SAME_SIDE
                        ? "-white.ftb.gz"
                        : "-canonical.ftb.gz";

        return directory.resolve(
                material.assetStem() + suffix
        );
    }


    /**
     * Select a proven exact WIN directly from the persisted Tier-1 outcome
     * array. This is reliable even for extremely draw-heavy material classes.
     */
    public int randomWinningState(
            FourPieceMaterialClass material,
            Random random
    ) throws IOException {

        if (material == null) {
            throw new IllegalArgumentException(
                    "Tier-1 material cannot be null."
            );
        }

        if (random == null) {
            throw new IllegalArgumentException(
                    "Random source cannot be null."
            );
        }

        if (!FourPieceTierOnePrimitiveMoveGenerator.supports(
                material
        )) {
            throw new IllegalArgumentException(
                    "Expected a canonical one-pawn Tier-1 material class."
            );
        }

        FourPieceGenericTablebase tablebase =
                tablebase(
                        material
                );

        return firstWinningStateFromRandomOffset(
                tablebase.outcomeData(),
                random,
                material.displayName()
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
                    "Exact Tier-1 tablebase contains no WIN state for "
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
                "Exact Tier-1 tablebase contains no WIN state for "
                        + label
                        + "."
        );
    }

    private synchronized FourPieceGenericTablebase tablebase(FourPieceMaterialClass material)
            throws IOException {
        FourPieceGenericTablebase cached = cache.get(material);
        if (cached != null) return cached;

        Path path = assetPath(material);
        if (!Files.isRegularFile(path)) {
            throw new IOException("Missing Tier-1 tablebase asset: " + path);
        }

        FourPieceGenericTablebase loaded = FourPieceGenericTablebaseCodec.load(path);
        if (!loaded.material().equals(material)) {
            throw new IOException("Wrong material stored in Tier-1 asset. Expected "
                    + material.displayName() + ", found " + loaded.material().displayName() + ".");
        }
        if (!loaded.sameSideOwnerIsWhite()) {
            throw new IOException("Tier-1 asset is not stored with canonical orientation flag=true: "
                    + material.displayName());
        }

        cache.put(material, loaded);
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

        Board board = position.getBoard();
        int whiteKing=-1, blackKing=-1, nonPawn=-1, pawn=-1, pieceCount=0;
        PieceType nonPawnType=null;
        Color nonPawnColor=null, pawnColor=null;

        for (int rank=0; rank<8; rank++) {
            for (int file=0; file<8; file++) {
                Piece piece = board.getPiece(new Square(file, rank));
                if (piece == null) continue;
                if (++pieceCount > 4) return null;
                int sq = rank*8+file;

                if (piece.type() == PieceType.KING) {
                    if (piece.color()==Color.WHITE) {
                        if (whiteKing!=-1) return null;
                        whiteKing=sq;
                    } else {
                        if (blackKing!=-1) return null;
                        blackKing=sq;
                    }
                    continue;
                }

                if (piece.type() == PieceType.PAWN) {
                    if (pawn!=-1 || rank==0 || rank==7) return null;
                    pawn=sq; pawnColor=piece.color();
                    continue;
                }

                if (piece.type()!=PieceType.QUEEN && piece.type()!=PieceType.ROOK
                        && piece.type()!=PieceType.BISHOP && piece.type()!=PieceType.KNIGHT) {
                    return null;
                }
                if (nonPawn!=-1) return null;
                nonPawn=sq; nonPawnType=piece.type(); nonPawnColor=piece.color();
            }
        }

        if (pieceCount!=4 || whiteKing<0 || blackKing<0 || nonPawn<0 || pawn<0
                || nonPawnType==null || nonPawnColor==null || pawnColor==null) return null;

        FourPieceMaterialClass material = nonPawnColor==pawnColor
                ? FourPieceMaterialClass.sameSide(nonPawnType, PieceType.PAWN)
                : FourPieceMaterialClass.split(nonPawnType, PieceType.PAWN);
        if (!FourPieceTierOnePrimitiveMoveGenerator.supports(material)) return null;

        boolean blackToMove = position.getSideToMove()==Color.BLACK;
        boolean colorReversed;

        if (material.distribution()==FourPieceMaterialClass.Distribution.SAME_SIDE) {
            colorReversed = nonPawnColor==Color.BLACK;
        } else {
            // Canonical split orientation is firstExtra WHITE / pawn BLACK.
            colorReversed = nonPawnColor==Color.BLACK;
        }

        if (!colorReversed) {
            // For split this necessarily means white nonpawn / black pawn.
            return new CanonicalPosition(material, whiteKing, blackKing, nonPawn, pawn,
                    blackToMove, false);
        }

        return new CanonicalPosition(material,
                flipRank(blackKing), flipRank(whiteKing), flipRank(nonPawn), flipRank(pawn),
                !blackToMove, true);
    }

    private static int flipRank(int square) {
        return (7-(square>>>3))*8 + (square&7);
    }

    public record ProbeResult(
            FourPieceMaterialClass material,
            byte outcome,
            short distance,
            boolean colorReversed,
            int primitiveState) {
        public boolean isWin(){return outcome==FourPieceTablebase.WIN;}
        public boolean isLoss(){return outcome==FourPieceTablebase.LOSS;}
        public boolean isDraw(){return outcome==FourPieceTablebase.DRAW;}
        public String outcomeName(){return isWin()?"WIN":isLoss()?"LOSS":isDraw()?"DRAW":"UNKNOWN";}
    }

    private record CanonicalPosition(
            FourPieceMaterialClass material,
            int whiteKing, int blackKing, int firstExtra, int pawn,
            boolean blackToMove, boolean colorReversed) {}
}

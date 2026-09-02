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


/**
 * Runtime service for the first solved Tier-1 four-piece tablebase: KQPK.
 *
 * Persisted canonical orientation:
 *
 *     strong side = WHITE
 *     firstExtra  = white queen
 *     secondExtra = white pawn
 *
 * A strong-BLACK engine position is normalized by:
 *
 *     1. swapping king colors;
 *     2. reflecting every square vertically (rank 1 <-> rank 8);
 *     3. reflecting queen and pawn squares vertically;
 *     4. reversing side to move.
 *
 * Vertical reflection is required because pawn direction is color-dependent.
 *
 * This service intentionally supports KQPK only.  The remaining seven Tier-1
 * families will be generalized after their exact solvers are established.
 */
public final class FourPieceTierOneKqpkTablebaseService {

    public static final Path DEFAULT_PATH =
            Path.of(
                    "tablebases",
                    "four-piece",
                    "KQPK-white.ftb.gz"
            );


    private static final FourPieceMaterialClass MATERIAL =
            FourPieceMaterialClass.sameSide(
                    PieceType.QUEEN,
                    PieceType.PAWN
            );


    private final Path path;

    private FourPieceGenericTablebase cached;


    public FourPieceTierOneKqpkTablebaseService() {

        this(
                DEFAULT_PATH
        );
    }


    public FourPieceTierOneKqpkTablebaseService(
            Path path
    ) {

        if (path == null) {

            throw new IllegalArgumentException(
                    "KQPK tablebase path cannot be null."
            );
        }


        this.path =
                path.toAbsolutePath()
                        .normalize();
    }


    public synchronized Optional<ProbeResult> probe(
            Position position
    ) throws IOException {

        CanonicalPosition canonical =
                canonicalize(
                        position
                );


        if (canonical == null) {

            return Optional.empty();
        }


        FourPieceGenericTablebase tablebase =
                tablebase();


        int state =
                FourPieceGenericPrimitiveState.encode(
                        canonical.whiteKing(),
                        canonical.blackKing(),
                        canonical.queen(),
                        canonical.pawn(),
                        canonical.blackToMove()
                );


        byte outcome =
                tablebase.outcome(
                        state
                );


        if (outcome
                == FourPieceTablebase.INVALID) {

            return Optional.empty();
        }


        return Optional.of(
                new ProbeResult(
                        outcome,
                        tablebase.distance(
                                state
                        ),
                        canonical.colorReversed(),
                        state
                )
        );
    }


    public boolean supports(
            Position position
    ) {

        return canonicalize(
                position
        ) != null;
    }


    public synchronized boolean isLoaded() {

        return cached != null;
    }


    public synchronized void clearCache() {

        cached =
                null;
    }


    public Path path() {

        return path;
    }


    private synchronized FourPieceGenericTablebase tablebase()
            throws IOException {

        if (cached != null) {

            return cached;
        }


        if (!Files.isRegularFile(
                path
        )) {

            throw new IOException(
                    "Missing KQPK Tier-1 tablebase asset: "
                            + path
            );
        }


        FourPieceGenericTablebase loaded =
                FourPieceGenericTablebaseCodec.load(
                        path
                );


        if (!loaded.material()
                .equals(
                        MATERIAL
                )) {

            throw new IOException(
                    "Wrong material stored in KQPK asset. Expected "
                            + MATERIAL.displayName()
                            + ", found "
                            + loaded.material()
                            .displayName()
                            + "."
            );
        }


        if (!loaded.sameSideOwnerIsWhite()) {

            throw new IOException(
                    "KQPK Tier-1 asset is not stored in canonical "
                            + "strong-WHITE orientation."
            );
        }


        cached =
                loaded;


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


        /*
         * One-pawn KQPK has no possible en-passant capture because there is no
         * opposing pawn.  A stale/non-null EP square therefore does not alter
         * legal KQPK moves and need not be part of this primitive identity.
         */


        Board board =
                position.getBoard();


        int whiteKing =
                -1;

        int blackKing =
                -1;

        int queen =
                -1;

        int pawn =
                -1;

        Color queenColor =
                null;

        Color pawnColor =
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


                int primitive =
                        rank * 8
                                + file;


                if (piece.type()
                        == PieceType.KING) {

                    if (piece.color()
                            == Color.WHITE) {

                        if (whiteKing != -1) {

                            return null;
                        }


                        whiteKing =
                                primitive;

                    } else {

                        if (blackKing != -1) {

                            return null;
                        }


                        blackKing =
                                primitive;
                    }


                    continue;
                }


                if (piece.type()
                        == PieceType.QUEEN) {

                    if (queen != -1) {

                        return null;
                    }


                    queen =
                            primitive;

                    queenColor =
                            piece.color();

                    continue;
                }


                if (piece.type()
                        == PieceType.PAWN) {

                    if (pawn != -1
                            || rank == 0
                            || rank == 7) {

                        return null;
                    }


                    pawn =
                            primitive;

                    pawnColor =
                            piece.color();

                    continue;
                }


                return null;
            }
        }


        if (pieceCount != 4
                || whiteKing == -1
                || blackKing == -1
                || queen == -1
                || pawn == -1
                || queenColor == null
                || pawnColor == null
                || queenColor != pawnColor) {

            return null;
        }


        boolean blackToMove =
                position.getSideToMove()
                        == Color.BLACK;


        boolean colorReversed =
                queenColor
                        == Color.BLACK;


        if (!colorReversed) {

            return new CanonicalPosition(
                    whiteKing,
                    blackKing,
                    queen,
                    pawn,
                    blackToMove,
                    false
            );
        }


        return new CanonicalPosition(
                flipRank(
                        blackKing
                ),
                flipRank(
                        whiteKing
                ),
                flipRank(
                        queen
                ),
                flipRank(
                        pawn
                ),
                !blackToMove,
                true
        );
    }


    private static int flipRank(
            int square
    ) {

        int file =
                square & 7;

        int rank =
                square >>> 3;


        return (7 - rank) * 8
                + file;
    }


    public record ProbeResult(
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


    private record CanonicalPosition(
            int whiteKing,
            int blackKing,
            int queen,
            int pawn,
            boolean blackToMove,
            boolean colorReversed
    ) {
    }
}

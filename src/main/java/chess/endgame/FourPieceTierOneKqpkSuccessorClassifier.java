package main.java.chess.endgame;

import main.java.chess.model.Board;
import main.java.chess.model.Color;
import main.java.chess.model.Move;
import main.java.chess.model.Piece;
import main.java.chess.model.PieceType;
import main.java.chess.model.Position;
import main.java.chess.model.Square;
import main.java.chess.rules.MoveGenerator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FourPieceTierOneKqpkSuccessorClassifier {

    public enum Kind {
        SAME_CLASS,
        THREE_PIECE_BOUNDARY,
        TIER_ZERO_PROMOTION
    }

    public record Successor(
            Move move,
            Kind kind,
            int childState,
            PieceType threePieceType,
            Color threePieceColor,
            FourPieceMaterialClass tierZeroMaterial
    ) {
        public Successor {
            if (move == null) {
                throw new IllegalArgumentException("Successor move cannot be null.");
            }
            if (kind == null) {
                throw new IllegalArgumentException("Successor kind cannot be null.");
            }
            switch (kind) {
                case SAME_CLASS -> {
                    if (childState < 0) {
                        throw new IllegalArgumentException("Same-class successor requires a primitive child state.");
                    }
                    if (threePieceType != null || threePieceColor != null || tierZeroMaterial != null) {
                        throw new IllegalArgumentException("Same-class successor cannot carry boundary metadata.");
                    }
                }
                case THREE_PIECE_BOUNDARY -> {
                    if (childState != -1) {
                        throw new IllegalArgumentException("Three-piece boundary cannot carry a primitive KQPK child state.");
                    }
                    if (threePieceType == null || threePieceColor == null) {
                        throw new IllegalArgumentException("Three-piece boundary requires surviving non-king material.");
                    }
                    if (tierZeroMaterial != null) {
                        throw new IllegalArgumentException("Three-piece boundary cannot carry Tier-0 material.");
                    }
                }
                case TIER_ZERO_PROMOTION -> {
                    if (childState != -1) {
                        throw new IllegalArgumentException("Promotion boundary cannot carry a primitive KQPK child state.");
                    }
                    if (tierZeroMaterial == null) {
                        throw new IllegalArgumentException("Promotion boundary requires Tier-0 material.");
                    }
                    if (threePieceType != null || threePieceColor != null) {
                        throw new IllegalArgumentException("Promotion boundary cannot carry three-piece metadata.");
                    }
                }
            }
        }

        public boolean staysInKqpk() {
            return kind == Kind.SAME_CLASS;
        }

        public boolean leavesTierOne() {
            return kind != Kind.SAME_CLASS;
        }
    }

    private final MoveGenerator moveGenerator;

    public FourPieceTierOneKqpkSuccessorClassifier() {
        moveGenerator = new MoveGenerator();
    }

    public List<Successor> generate(int state, Color strongColor) {
        requireStrongColor(strongColor);
        Position position = positionForState(state, strongColor);
        List<Move> legalMoves = moveGenerator.generateLegalMoves(position);
        List<Successor> result = new ArrayList<>(legalMoves.size());
        for (Move move : legalMoves) {
            Position child = position.makeMove(move);
            result.add(classifyChild(move, child, strongColor));
        }
        return List.copyOf(result);
    }

    public Position positionForState(int state, Color strongColor) {
        requireStrongColor(strongColor);

        int whiteKing = FourPieceGenericPrimitiveState.whiteKing(state);
        int blackKing = FourPieceGenericPrimitiveState.blackKing(state);
        int queen = FourPieceGenericPrimitiveState.firstExtra(state);
        int pawn = FourPieceGenericPrimitiveState.secondExtra(state);

        requireDistinct(whiteKing, blackKing, queen, pawn);

        int pawnRank = pawn >>> 3;
        if (pawnRank == 0 || pawnRank == 7) {
            throw new IllegalArgumentException("In-class KQPK pawn cannot occupy a promotion rank.");
        }

        Board board = new Board();
        board.setPiece(square(whiteKing), new Piece(PieceType.KING, Color.WHITE));
        board.setPiece(square(blackKing), new Piece(PieceType.KING, Color.BLACK));
        board.setPiece(square(queen), new Piece(PieceType.QUEEN, strongColor));
        board.setPiece(square(pawn), new Piece(PieceType.PAWN, strongColor));

        Color sideToMove = FourPieceGenericPrimitiveState.blackToMove(state)
                ? Color.BLACK
                : Color.WHITE;

        Position temporary = new Position(
                board,
                sideToMove,
                false,
                false,
                false,
                false,
                null,
                0,
                1,
                new HashMap<>()
        );

        Map<main.java.chess.model.PositionKey, Integer> repetitionCounts = new HashMap<>();
        repetitionCounts.put(temporary.createPositionKey(), 1);

        return new Position(
                board,
                sideToMove,
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

    public int encodeIfInClass(Position position, Color strongColor) {
        if (position == null) {
            return -1;
        }
        requireStrongColor(strongColor);

        LocatedMaterial located = locate(position);

        if (located.totalPieces() != 4
                || located.whiteKing() < 0
                || located.blackKing() < 0
                || located.queenSquares().size() != 1
                || located.pawnSquare() < 0
                || located.queenColors().size() != 1
                || located.queenColors().getFirst() != strongColor
                || located.pawnColor() != strongColor) {
            return -1;
        }

        int pawnRank = located.pawnSquare() >>> 3;
        if (pawnRank == 0 || pawnRank == 7) {
            return -1;
        }

        return FourPieceGenericPrimitiveState.encode(
                located.whiteKing(),
                located.blackKing(),
                located.queenSquares().getFirst(),
                located.pawnSquare(),
                position.getSideToMove() == Color.BLACK
        );
    }

    private Successor classifyChild(Move move, Position child, Color strongColor) {
        int inClass = encodeIfInClass(child, strongColor);
        if (inClass >= 0) {
            return new Successor(move, Kind.SAME_CLASS, inClass, null, null, null);
        }

        LocatedMaterial located = locate(child);

        /*
         * The dense primitive encoding includes raw placements that are not
         * reachable legal chess positions. In some such placements the normal
         * move layer can expose a king-capture artifact. A legal KQPK
         * successor must always retain both kings, so reject that raw parent
         * instead of mistaking the resulting three-piece board for a material
         * boundary.
         */
        if (located.whiteKing() < 0 || located.blackKing() < 0) {
            throw new IllegalArgumentException(
                    "Primitive state is outside the legal KQPK domain: "
                            + "a generated successor removed a king."
            );
        }

        if (located.totalPieces() == 3) {
            PieceType survivingType;
            Color survivingColor;

            if (located.queenSquares().size() == 1 && located.pawnSquare() < 0) {
                survivingType = PieceType.QUEEN;
                survivingColor = located.queenColors().getFirst();
            } else if (located.queenSquares().isEmpty() && located.pawnSquare() >= 0) {
                survivingType = PieceType.PAWN;
                survivingColor = located.pawnColor();
            } else {
                throw new IllegalStateException("Unexpected three-piece successor from KQPK.");
            }

            if (survivingColor != strongColor) {
                throw new IllegalStateException("KQPK boundary retained material of the wrong color.");
            }

            return new Successor(
                    move,
                    Kind.THREE_PIECE_BOUNDARY,
                    -1,
                    survivingType,
                    survivingColor,
                    null
            );
        }

        if (located.totalPieces() == 4 && located.pawnSquare() < 0) {
            List<PieceType> strongNonKings = strongNonKingTypes(child, strongColor);
            if (strongNonKings.size() != 2) {
                throw new IllegalStateException("Promotion successor must contain exactly two strong-side non-kings.");
            }

            FourPieceMaterialClass material = FourPieceMaterialClass.sameSide(
                    strongNonKings.get(0),
                    strongNonKings.get(1)
            );

            if (material.buildTier() != 0) {
                throw new IllegalStateException("KQPK promotion did not land in a Tier-0 material class.");
            }

            return new Successor(
                    move,
                    Kind.TIER_ZERO_PROMOTION,
                    -1,
                    null,
                    null,
                    material
            );
        }

        throw new IllegalStateException("Unclassified legal KQPK successor.");
    }

    private static List<PieceType> strongNonKingTypes(Position position, Color strongColor) {
        List<PieceType> types = new ArrayList<>(2);
        for (int rank = 0; rank < 8; rank++) {
            for (int file = 0; file < 8; file++) {
                Piece piece = position.getBoard().getPiece(new Square(file, rank));
                if (piece == null || piece.color() != strongColor || piece.type() == PieceType.KING) {
                    continue;
                }
                types.add(piece.type());
            }
        }
        return List.copyOf(types);
    }

    private static LocatedMaterial locate(Position position) {
        int whiteKing = -1;
        int blackKing = -1;
        List<Integer> queenSquares = new ArrayList<>(2);
        List<Color> queenColors = new ArrayList<>(2);
        int pawnSquare = -1;
        Color pawnColor = null;
        int totalPieces = 0;

        for (int rank = 0; rank < 8; rank++) {
            for (int file = 0; file < 8; file++) {
                Piece piece = position.getBoard().getPiece(new Square(file, rank));
                if (piece == null) {
                    continue;
                }
                totalPieces++;
                int index = rank * 8 + file;
                switch (piece.type()) {
                    case KING -> {
                        if (piece.color() == Color.WHITE) {
                            whiteKing = index;
                        } else {
                            blackKing = index;
                        }
                    }
                    case QUEEN -> {
                        queenSquares.add(index);
                        queenColors.add(piece.color());
                    }
                    case PAWN -> {
                        pawnSquare = index;
                        pawnColor = piece.color();
                    }
                    default -> {
                        // R/B/N can occur only after promotion.
                    }
                }
            }
        }

        return new LocatedMaterial(
                whiteKing,
                blackKing,
                List.copyOf(queenSquares),
                List.copyOf(queenColors),
                pawnSquare,
                pawnColor,
                totalPieces
        );
    }

    private static Square square(int index) {
        if (index < 0 || index >= 64) {
            throw new IllegalArgumentException("Square index must be 0..63: " + index);
        }
        return new Square(index & 7, index >>> 3);
    }

    private static void requireDistinct(int whiteKing, int blackKing, int queen, int pawn) {
        if (whiteKing == blackKing
                || whiteKing == queen
                || whiteKing == pawn
                || blackKing == queen
                || blackKing == pawn
                || queen == pawn) {
            throw new IllegalArgumentException("KQPK primitive state contains overlapping pieces.");
        }
    }

    private static void requireStrongColor(Color strongColor) {
        if (strongColor == null) {
            throw new IllegalArgumentException("Strong color cannot be null.");
        }
    }

    private record LocatedMaterial(
            int whiteKing,
            int blackKing,
            List<Integer> queenSquares,
            List<Color> queenColors,
            int pawnSquare,
            Color pawnColor,
            int totalPieces
    ) {
    }
}

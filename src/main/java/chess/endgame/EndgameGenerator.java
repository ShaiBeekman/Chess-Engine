package main.java.chess.endgame;

import main.java.chess.model.Board;
import main.java.chess.model.Color;
import main.java.chess.model.GameState;
import main.java.chess.model.Piece;
import main.java.chess.model.PieceType;
import main.java.chess.model.Position;
import main.java.chess.model.PositionKey;
import main.java.chess.model.Square;
import main.java.chess.rules.GameStateEvaluator;
import main.java.chess.rules.MoveGenerator;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class EndgameGenerator {

    private static final int MAX_ATTEMPTS =
            10_000;

    private final Random random =
            new Random();

    private final MoveGenerator moveGenerator =
            new MoveGenerator();

    private final GameStateEvaluator gameStateEvaluator =
            new GameStateEvaluator();


    public Position generate(
            EndgameSettings settings
    ) {

        int pieceCount =
                randomBetween(
                        settings.minimumPieces(),
                        settings.maximumPieces()
                );


        for (int attempt = 0;
             attempt < MAX_ATTEMPTS;
             attempt++) {

            Position position =
                    generateCandidate(
                            pieceCount
                    );


            if (position != null) {
                return position;
            }
        }


        throw new IllegalStateException(
                "Unable to generate an endgame position."
        );
    }


    private Position generateCandidate(
            int pieceCount
    ) {

        Board board =
                new Board();


        Square whiteKing =
                randomSquare();

        Square blackKing =
                randomSquare();


        if (whiteKing.equals(
                blackKing
        )
                || kingsAdjacent(
                whiteKing,
                blackKing
        )) {

            return null;
        }


        board.setPiece(
                whiteKing,
                new Piece(
                        PieceType.KING,
                        Color.WHITE
                )
        );


        board.setPiece(
                blackKing,
                new Piece(
                        PieceType.KING,
                        Color.BLACK
                )
        );


        for (int index = 2;
             index < pieceCount;
             index++) {

            Square square =
                    findEmptySquare(
                            board
                    );


            if (square == null) {
                return null;
            }


            PieceType type =
                    randomNonKingPiece();


            if (type == PieceType.PAWN
                    && (square.rank() == 0
                    || square.rank() == 7)) {

                type =
                        randomNonPawnPiece();
            }


            Color color =
                    random.nextBoolean()
                            ? Color.WHITE
                            : Color.BLACK;


            board.setPiece(
                    square,
                    new Piece(
                            type,
                            color
                    )
            );
        }


        Color sideToMove =
                random.nextBoolean()
                        ? Color.WHITE
                        : Color.BLACK;


        /*
         * Position intentionally does not expose its repetition map.
         * Build a temporary position first so we can obtain the
         * canonical key, then construct the final position with a
         * correctly initialized repetition history.
         */
        Position temporaryPosition =
                new Position(
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


        Map<PositionKey, Integer> repetitionCounts =
                new HashMap<>();


        repetitionCounts.put(
                temporaryPosition.createPositionKey(),
                1
        );


        Position position =
                new Position(
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


        if (!isStudyCandidate(
                position
        )) {

            return null;
        }


        return position;
    }


    private boolean isStudyCandidate(
            Position position
    ) {

        /*
         * Endgame Study should not offer positions in which mate is
         * impossible from the material on the board.
         */
        if (isDeadMaterial(
                position.getBoard()
        )) {

            return false;
        }


        /*
         * The side to move may legally be in check, but the position
         * must not already be terminal. This rejects checkmate,
         * stalemate and every automatic-draw state recognized by the
         * engine.
         */
        GameState state =
                gameStateEvaluator.evaluate(
                        position
                );


        if (state != GameState.ONGOING
                && state != GameState.CHECK) {

            return false;
        }


        if (moveGenerator.generateLegalMoves(
                position
        ).isEmpty()) {

            return false;
        }


        /*
         * A legal chess position cannot have the side that just moved
         * still in check. Test that by viewing the same board with the
         * opposite side to move. If that opposite side is in check,
         * the generated state could not have arisen from a legal move.
         */
        Color oppositeSide =
                position.getSideToMove()
                        == Color.WHITE
                        ? Color.BLACK
                        : Color.WHITE;


        Position oppositeView =
                createFreshPosition(
                        position.getBoard(),
                        oppositeSide
                );


        GameState oppositeState =
                gameStateEvaluator.evaluate(
                        oppositeView
                );


        return oppositeState != GameState.CHECK
                && oppositeState != GameState.CHECKMATE;
    }


    private boolean isDeadMaterial(
            Board board
    ) {

        int queens = 0;
        int rooks = 0;
        int pawns = 0;
        int bishops = 0;
        int knights = 0;

        int lightSquareBishops = 0;
        int darkSquareBishops = 0;


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


                if (piece == null
                        || piece.type()
                        == PieceType.KING) {

                    continue;
                }


                switch (piece.type()) {

                    case QUEEN ->
                            queens++;

                    case ROOK ->
                            rooks++;

                    case PAWN ->
                            pawns++;

                    case KNIGHT ->
                            knights++;

                    case BISHOP -> {

                        bishops++;


                        if ((file + rank) % 2 == 0) {

                            lightSquareBishops++;

                        } else {

                            darkSquareBishops++;
                        }
                    }

                    default -> {
                    }
                }
            }
        }


        /*
         * Any pawn, rook or queen means the position is not an
         * automatic dead-material rejection.
         */
        if (pawns > 0
                || rooks > 0
                || queens > 0) {

            return false;
        }


        int minorPieces =
                bishops
                        + knights;


        /*
         * K vs K
         * K+B vs K
         * K+N vs K
         */
        if (minorPieces <= 1) {

            return true;
        }


        /*
         * If there are bishops only and every bishop is confined to
         * the same color complex, checkmate is impossible.
         *
         * This covers, for example, K+B vs K+B when both bishops live
         * on the same colored squares, including positions containing
         * promoted same-color bishops.
         */
        if (knights == 0
                && bishops > 0) {

            return lightSquareBishops == 0
                    || darkSquareBishops == 0;
        }


        /*
         * Do not reject more complicated minor-piece material here.
         * Some such positions can permit mate depending on how the
         * material is distributed between the two sides. Exact Study
         * eligibility will make the stronger decision later.
         */
        return false;
    }


    private Position createFreshPosition(
            Board board,
            Color sideToMove
    ) {

        Position temporary =
                new Position(
                        new Board(
                                board
                        ),
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


        Map<PositionKey, Integer> repetitions =
                new HashMap<>();


        repetitions.put(
                temporary.createPositionKey(),
                1
        );


        return new Position(
                new Board(
                        board
                ),
                sideToMove,
                false,
                false,
                false,
                false,
                null,
                0,
                1,
                repetitions
        );
    }


    private Square findEmptySquare(
            Board board
    ) {

        for (int attempt = 0;
             attempt < 100;
             attempt++) {

            Square square =
                    randomSquare();


            if (board.isEmpty(
                    square
            )) {
                return square;
            }
        }


        return null;
    }


    private Square randomSquare() {

        return new Square(
                random.nextInt(
                        8
                ),
                random.nextInt(
                        8
                )
        );
    }


    private PieceType randomNonKingPiece() {

        PieceType[] choices = {
                PieceType.QUEEN,
                PieceType.ROOK,
                PieceType.BISHOP,
                PieceType.KNIGHT,
                PieceType.PAWN
        };


        return choices[
                random.nextInt(
                        choices.length
                )
                ];
    }


    private PieceType randomNonPawnPiece() {

        PieceType[] choices = {
                PieceType.QUEEN,
                PieceType.ROOK,
                PieceType.BISHOP,
                PieceType.KNIGHT
        };


        return choices[
                random.nextInt(
                        choices.length
                )
                ];
    }


    private boolean kingsAdjacent(
            Square first,
            Square second
    ) {

        return Math.abs(
                first.file()
                        - second.file()
        ) <= 1
                &&
                Math.abs(
                        first.rank()
                                - second.rank()
                ) <= 1;
    }


    private int randomBetween(
            int minimum,
            int maximum
    ) {

        return minimum
                + random.nextInt(
                maximum
                        - minimum
                        + 1
        );
    }
}

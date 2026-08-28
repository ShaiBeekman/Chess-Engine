package main.java.chess.model;

import java.util.HashMap;
import java.util.Map;


/**
 * Converts between Forsyth-Edwards Notation (FEN) and Position.
 *
 * All six standard FEN fields are preserved:
 *
 * 1. piece placement
 * 2. active color
 * 3. castling availability
 * 4. en-passant target square
 * 5. halfmove clock
 * 6. fullmove number
 *
 * A FEN string does not contain repetition history. Loading a FEN therefore
 * begins a fresh line whose loaded position has repetition count 1.
 */
public final class FenCodec {

    private FenCodec() {
    }


    public static Position parse(
            String fen
    ) {

        if (fen == null) {
            throw new IllegalArgumentException(
                    "FEN cannot be null."
            );
        }


        String trimmed =
                fen.trim();


        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(
                    "FEN cannot be empty."
            );
        }


        String[] fields =
                trimmed.split(
                        "\\s+"
                );


        if (fields.length != 6) {
            throw new IllegalArgumentException(
                    "FEN must contain exactly 6 fields."
            );
        }


        Board board =
                parseBoard(
                        fields[0]
                );


        Color sideToMove =
                parseSideToMove(
                        fields[1]
                );


        CastlingRights castlingRights =
                parseCastlingRights(
                        fields[2]
                );


        Square enPassantTarget =
                parseEnPassantTarget(
                        fields[3]
                );


        int halfmoveClock =
                parseNonNegativeInteger(
                        fields[4],
                        "halfmove clock"
                );


        int fullmoveNumber =
                parsePositiveInteger(
                        fields[5],
                        "fullmove number"
                );


        /*
         * FEN does not encode prior repetition history.
         *
         * First construct a temporary position so we can create the exact
         * PositionKey for the loaded state, then seed that state as the
         * first occurrence on this new analysis line.
         */
        Position temporary =
                new Position(
                        board,
                        sideToMove,
                        castlingRights.whiteKingSide(),
                        castlingRights.whiteQueenSide(),
                        castlingRights.blackKingSide(),
                        castlingRights.blackQueenSide(),
                        enPassantTarget,
                        halfmoveClock,
                        fullmoveNumber,
                        Map.of()
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
                castlingRights.whiteKingSide(),
                castlingRights.whiteQueenSide(),
                castlingRights.blackKingSide(),
                castlingRights.blackQueenSide(),
                enPassantTarget,
                halfmoveClock,
                fullmoveNumber,
                repetitionCounts
        );
    }


    public static String toFen(
            Position position
    ) {

        if (position == null) {
            throw new IllegalArgumentException(
                    "Position cannot be null."
            );
        }


        return encodeBoard(
                position.getBoard()
        )
                + " "
                + (
                position.getSideToMove() == Color.WHITE
                        ? "w"
                        : "b"
        )
                + " "
                + encodeCastlingRights(
                position
        )
                + " "
                + (
                position.getEnPassantTarget() == null
                        ? "-"
                        : position
                        .getEnPassantTarget()
                        .toAlgebraic()
        )
                + " "
                + position.getHalfmoveClock()
                + " "
                + position.getFullmoveNumber();
    }


    private static Board parseBoard(
            String field
    ) {

        String[] ranks =
                field.split(
                        "/",
                        -1
                );


        if (ranks.length != 8) {
            throw new IllegalArgumentException(
                    "FEN piece placement must contain exactly 8 ranks."
            );
        }


        Board board =
                new Board();


        for (int fenRank = 0;
             fenRank < 8;
             fenRank++) {

            String rankText =
                    ranks[fenRank];


            int boardRank =
                    7 - fenRank;


            int file =
                    0;


            for (int i = 0;
                 i < rankText.length();
                 i++) {

                char token =
                        rankText.charAt(
                                i
                        );


                if (token >= '1'
                        && token <= '8') {

                    file +=
                            token - '0';


                    if (file > 8) {
                        throw new IllegalArgumentException(
                                "A FEN rank expands beyond 8 files."
                        );
                    }


                    continue;
                }


                Piece piece =
                        pieceFromFenCharacter(
                                token
                        );


                if (file >= 8) {
                    throw new IllegalArgumentException(
                            "A FEN rank contains more than 8 files."
                    );
                }


                board.setPiece(
                        new Square(
                                file,
                                boardRank
                        ),
                        piece
                );


                file++;
            }


            if (file != 8) {
                throw new IllegalArgumentException(
                        "Every FEN rank must describe exactly 8 files."
                );
            }
        }


        return board;
    }


    private static Piece pieceFromFenCharacter(
            char token
    ) {

        Color color =
                Character.isUpperCase(
                        token
                )
                        ? Color.WHITE
                        : Color.BLACK;


        char lower =
                Character.toLowerCase(
                        token
                );


        PieceType type =
                switch (lower) {

                    case 'k' ->
                            PieceType.KING;

                    case 'q' ->
                            PieceType.QUEEN;

                    case 'r' ->
                            PieceType.ROOK;

                    case 'b' ->
                            PieceType.BISHOP;

                    case 'n' ->
                            PieceType.KNIGHT;

                    case 'p' ->
                            PieceType.PAWN;

                    default ->
                            throw new IllegalArgumentException(
                                    "Invalid FEN piece character: "
                                            + token
                            );
                };


        return new Piece(
                type,
                color
        );
    }


    private static Color parseSideToMove(
            String field
    ) {

        return switch (field) {

            case "w" ->
                    Color.WHITE;

            case "b" ->
                    Color.BLACK;

            default ->
                    throw new IllegalArgumentException(
                            "FEN active color must be 'w' or 'b'."
                    );
        };
    }


    private static CastlingRights parseCastlingRights(
            String field
    ) {

        if (field.equals(
                "-"
        )) {

            return new CastlingRights(
                    false,
                    false,
                    false,
                    false
            );
        }


        boolean whiteKingSide =
                false;

        boolean whiteQueenSide =
                false;

        boolean blackKingSide =
                false;

        boolean blackQueenSide =
                false;


        for (int i = 0;
             i < field.length();
             i++) {

            char token =
                    field.charAt(
                            i
                    );


            switch (token) {

                case 'K' -> {

                    if (whiteKingSide) {
                        throw duplicateCastlingRight(
                                token
                        );
                    }

                    whiteKingSide =
                            true;
                }

                case 'Q' -> {

                    if (whiteQueenSide) {
                        throw duplicateCastlingRight(
                                token
                        );
                    }

                    whiteQueenSide =
                            true;
                }

                case 'k' -> {

                    if (blackKingSide) {
                        throw duplicateCastlingRight(
                                token
                        );
                    }

                    blackKingSide =
                            true;
                }

                case 'q' -> {

                    if (blackQueenSide) {
                        throw duplicateCastlingRight(
                                token
                        );
                    }

                    blackQueenSide =
                            true;
                }

                default ->
                        throw new IllegalArgumentException(
                                "Invalid FEN castling field: "
                                        + field
                        );
            }
        }


        return new CastlingRights(
                whiteKingSide,
                whiteQueenSide,
                blackKingSide,
                blackQueenSide
        );
    }


    private static IllegalArgumentException duplicateCastlingRight(
            char token
    ) {

        return new IllegalArgumentException(
                "Duplicate FEN castling right: "
                        + token
        );
    }


    private static Square parseEnPassantTarget(
            String field
    ) {

        if (field.equals(
                "-"
        )) {

            return null;
        }


        Square square;


        try {

            square =
                    Square.fromAlgebraic(
                            field
                    );

        } catch (RuntimeException exception) {

            throw new IllegalArgumentException(
                    "Invalid FEN en-passant square: "
                            + field,
                    exception
            );
        }


        int rank =
                square.rank();


        /*
         * In a valid FEN, an en-passant target can only be on
         * rank 3 or rank 6. Internal ranks are zero-based.
         */
        if (rank != 2
                && rank != 5) {

            throw new IllegalArgumentException(
                    "FEN en-passant target must be on rank 3 or rank 6."
            );
        }


        return square;
    }


    private static int parseNonNegativeInteger(
            String field,
            String name
    ) {

        int value =
                parseInteger(
                        field,
                        name
                );


        if (value < 0) {
            throw new IllegalArgumentException(
                    "FEN "
                            + name
                            + " cannot be negative."
            );
        }


        return value;
    }


    private static int parsePositiveInteger(
            String field,
            String name
    ) {

        int value =
                parseInteger(
                        field,
                        name
                );


        if (value < 1) {
            throw new IllegalArgumentException(
                    "FEN "
                            + name
                            + " must be at least 1."
            );
        }


        return value;
    }


    private static int parseInteger(
            String field,
            String name
    ) {

        try {

            return Integer.parseInt(
                    field
            );

        } catch (NumberFormatException exception) {

            throw new IllegalArgumentException(
                    "FEN "
                            + name
                            + " must be an integer.",
                    exception
            );
        }
    }


    private static String encodeBoard(
            Board board
    ) {

        StringBuilder result =
                new StringBuilder();


        for (int rank = 7;
             rank >= 0;
             rank--) {

            int emptyCount =
                    0;


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

                    emptyCount++;

                    continue;
                }


                if (emptyCount > 0) {

                    result.append(
                            emptyCount
                    );

                    emptyCount =
                            0;
                }


                result.append(
                        fenCharacterForPiece(
                                piece
                        )
                );
            }


            if (emptyCount > 0) {

                result.append(
                        emptyCount
                );
            }


            if (rank > 0) {

                result.append(
                        '/'
                );
            }
        }


        return result.toString();
    }


    private static char fenCharacterForPiece(
            Piece piece
    ) {

        char token =
                switch (piece.type()) {

                    case KING ->
                            'k';

                    case QUEEN ->
                            'q';

                    case ROOK ->
                            'r';

                    case BISHOP ->
                            'b';

                    case KNIGHT ->
                            'n';

                    case PAWN ->
                            'p';
                };


        return piece.color() == Color.WHITE
                ? Character.toUpperCase(
                token
        )
                : token;
    }


    private static String encodeCastlingRights(
            Position position
    ) {

        StringBuilder result =
                new StringBuilder();


        if (position.canWhiteCastleKingSide()) {
            result.append(
                    'K'
            );
        }


        if (position.canWhiteCastleQueenSide()) {
            result.append(
                    'Q'
            );
        }


        if (position.canBlackCastleKingSide()) {
            result.append(
                    'k'
            );
        }


        if (position.canBlackCastleQueenSide()) {
            result.append(
                    'q'
            );
        }


        return result.length() == 0
                ? "-"
                : result.toString();
    }


    private record CastlingRights(
            boolean whiteKingSide,
            boolean whiteQueenSide,
            boolean blackKingSide,
            boolean blackQueenSide
    ) {
    }
}

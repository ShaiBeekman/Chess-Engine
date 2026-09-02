package main.java.chess.endgame;

import main.java.chess.model.Color;
import main.java.chess.model.Move;
import main.java.chess.model.Position;
import main.java.chess.rules.MoveGenerator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;


/**
 * Correctness gate for primitive KQRK successor generation.
 *
 * Compares:
 *
 *     primitive successor multiset
 *
 * against:
 *
 *     existing MoveGenerator + Position.makeMove
 *
 * for randomized structurally legal KQRK positions.
 *
 * In-class children are compared by encoded state.
 * KQK/KRK capture boundaries are compared by material type and surviving
 * non-king square.
 */
public final class FourPiecePrimitiveMoveVerificationMain {

    private static final int DEFAULT_POSITIONS = 25_000;


    public static void main(String[] args) {

        int positions =
                args.length > 0
                        ? Integer.parseInt(args[0])
                        : DEFAULT_POSITIONS;

        verify(
                positions,
                Color.WHITE,
                0x4B51524BL
        );

        verify(
                positions,
                Color.BLACK,
                0x4B51524CL
        );
    }


    private static void verify(
            int positions,
            Color strongColor,
            long seed
    ) {
        boolean strongIsWhite =
                strongColor == Color.WHITE;

        FourPieceTablebase tablebase =
                new FourPieceTablebase(
                        FourPieceMaterial.kqrk(strongColor)
                );

        MoveGenerator objectGenerator =
                new MoveGenerator();

        KqrkPrimitiveMoveGenerator.Buffer primitive =
                new KqrkPrimitiveMoveGenerator.Buffer(64);

        Random random = new Random(seed);

        int checked = 0;
        int mismatches = 0;
        long attempts = 0;

        while (checked < positions) {
            attempts++;

            int state =
                    random.nextInt(
                            KqrkPrimitiveState.STATE_COUNT
                    );

            if (!KqrkPrimitiveRules.isStructurallyLegal(
                    state,
                    strongIsWhite
            )) {
                continue;
            }

            Position position =
                    tablebase.positionForState(state);

            Map<String, Integer> expected =
                    new HashMap<>();

            List<Move> moves =
                    objectGenerator.generateLegalMoves(position);

            for (Move move : moves) {
                Position child =
                        position.makeMove(move);

                String key =
                        classifyObjectChild(
                                child,
                                tablebase,
                                strongColor
                        );

                expected.merge(key, 1, Integer::sum);
            }

            Map<String, Integer> actual =
                    new HashMap<>();

            int count =
                    KqrkPrimitiveMoveGenerator.generateLegalSuccessors(
                            state,
                            strongIsWhite,
                            primitive
                    );

            for (int i = 0; i < count; i++) {
                String key =
                        classifyPrimitiveChild(
                                primitive,
                                i
                        );

                actual.merge(key, 1, Integer::sum);
            }

            if (!expected.equals(actual)) {
                mismatches++;

                if (mismatches <= 10) {
                    System.out.println();
                    System.out.println(
                            "Mismatch state: " + state
                    );
                    System.out.println(
                            "Strong color: " + strongColor
                    );
                    System.out.println(
                            "Expected count: " + moves.size()
                    );
                    System.out.println(
                            "Primitive count: " + count
                    );
                    System.out.println(
                            "Expected only: "
                                    + difference(expected, actual)
                    );
                    System.out.println(
                            "Primitive only: "
                                    + difference(actual, expected)
                    );
                }
            }

            checked++;
        }

        System.out.println();
        System.out.println(
                "Primitive move verification — strong "
                        + strongColor
        );
        System.out.println(
                "Legal positions checked: " + checked
        );
        System.out.println(
                "Random raw attempts: " + attempts
        );
        System.out.println(
                "Mismatches: " + mismatches
        );

        if (mismatches != 0) {
            throw new IllegalStateException(
                    "Primitive KQRK move generation mismatch."
            );
        }

        System.out.println(
                "PRIMITIVE MOVE VERIFICATION PASSED"
        );
    }


    private static String classifyObjectChild(
            Position child,
            FourPieceTablebase tablebase,
            Color strongColor
    ) {
        int encoded =
                tablebase.encodePosition(child);

        if (encoded >= 0) {
            return "S:" + encoded;
        }

        int qSquare = -1;
        int rSquare = -1;
        int nonKings = 0;

        for (int rank = 0; rank < 8; rank++) {
            for (int file = 0; file < 8; file++) {
                main.java.chess.model.Square square =
                        new main.java.chess.model.Square(
                                file,
                                rank
                        );

                main.java.chess.model.Piece piece =
                        child.getBoard().getPiece(square);

                if (piece == null
                        || piece.type()
                        == main.java.chess.model.PieceType.KING) {
                    continue;
                }

                nonKings++;

                if (piece.color() != strongColor) {
                    return "UNSUPPORTED";
                }

                if (piece.type()
                        == main.java.chess.model.PieceType.QUEEN) {
                    qSquare = rank * 8 + file;
                } else if (piece.type()
                        == main.java.chess.model.PieceType.ROOK) {
                    rSquare = rank * 8 + file;
                } else {
                    return "UNSUPPORTED";
                }
            }
        }

        if (nonKings == 1 && qSquare >= 0) {
            return "KQK:" + qSquare;
        }

        if (nonKings == 1 && rSquare >= 0) {
            return "KRK:" + rSquare;
        }

        if (nonKings == 0) {
            return "KK";
        }

        return "UNSUPPORTED";
    }


    private static String classifyPrimitiveChild(
            KqrkPrimitiveMoveGenerator.Buffer buffer,
            int index
    ) {
        int type =
                buffer.boundaryType(index);

        if (type
                == KqrkPrimitiveMoveGenerator.BOUNDARY_NONE) {
            return "S:" + buffer.state(index);
        }

        if (type
                == KqrkPrimitiveMoveGenerator.BOUNDARY_KQK) {
            return "KQK:"
                    + buffer.survivingPieceSquare(index);
        }

        if (type
                == KqrkPrimitiveMoveGenerator.BOUNDARY_KRK) {
            return "KRK:"
                    + buffer.survivingPieceSquare(index);
        }

        if (type
                == KqrkPrimitiveMoveGenerator.BOUNDARY_KK) {
            return "KK";
        }

        return "UNSUPPORTED";
    }


    private static Map<String, Integer> difference(
            Map<String, Integer> first,
            Map<String, Integer> second
    ) {
        Map<String, Integer> result =
                new HashMap<>();

        for (Map.Entry<String, Integer> entry :
                first.entrySet()) {
            int other =
                    second.getOrDefault(
                            entry.getKey(),
                            0
                    );

            int extra =
                    entry.getValue() - other;

            if (extra > 0) {
                result.put(
                        entry.getKey(),
                        extra
                );
            }
        }

        return result;
    }
}

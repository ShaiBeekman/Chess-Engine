package main.java.chess.endgame;

import main.java.chess.model.PieceType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;


/**
 * Canonical identity for a four-piece material family.
 *
 * Four total pieces means:
 *
 *     white king
 *     black king
 *     two non-king pieces
 *
 * There are two distributions:
 *
 *     SAME_SIDE  - both non-kings belong to one side
 *     SPLIT      - one non-king belongs to each side
 *
 * Color/orientation is intentionally NOT part of this key.  This class
 * describes the canonical material family only.  Runtime/build code may
 * still choose a concrete color orientation.
 *
 * With five possible non-king types:
 *
 *     Q, R, B, N, P
 *
 * each distribution has C(5 + 2 - 1, 2) = 15 unordered multisets.
 * Therefore the complete canonical four-piece catalog has 30 families.
 */
public record FourPieceMaterialClass(
        Distribution distribution,
        PieceType firstType,
        PieceType secondType
) {

    public enum Distribution {
        SAME_SIDE,
        SPLIT
    }


    private static final List<PieceType> NON_KING_TYPES =
            List.of(
                    PieceType.QUEEN,
                    PieceType.ROOK,
                    PieceType.BISHOP,
                    PieceType.KNIGHT,
                    PieceType.PAWN
            );


    private static final Comparator<PieceType> TYPE_ORDER =
            Comparator.comparingInt(
                    FourPieceMaterialClass::order
            );


    public FourPieceMaterialClass {

        Objects.requireNonNull(
                distribution,
                "distribution"
        );

        Objects.requireNonNull(
                firstType,
                "firstType"
        );

        Objects.requireNonNull(
                secondType,
                "secondType"
        );


        requireNonKing(
                firstType
        );

        requireNonKing(
                secondType
        );


        /*
         * Canonicalize the unordered pair.
         *
         * Q < R < B < N < P
         */
        if (TYPE_ORDER.compare(
                firstType,
                secondType
        ) > 0) {

            PieceType temporary =
                    firstType;

            firstType =
                    secondType;

            secondType =
                    temporary;
        }
    }


    public static FourPieceMaterialClass sameSide(
            PieceType first,
            PieceType second
    ) {

        return new FourPieceMaterialClass(
                Distribution.SAME_SIDE,
                first,
                second
        );
    }


    public static FourPieceMaterialClass split(
            PieceType first,
            PieceType second
    ) {

        return new FourPieceMaterialClass(
                Distribution.SPLIT,
                first,
                second
        );
    }


    public static List<FourPieceMaterialClass> allCanonical() {

        List<FourPieceMaterialClass> result =
                new ArrayList<>(
                        30
                );


        for (Distribution distribution :
                Distribution.values()) {

            for (int first = 0;
                 first < NON_KING_TYPES.size();
                 first++) {

                for (int second = first;
                     second < NON_KING_TYPES.size();
                     second++) {

                    result.add(
                            new FourPieceMaterialClass(
                                    distribution,
                                    NON_KING_TYPES.get(
                                            first
                                    ),
                                    NON_KING_TYPES.get(
                                            second
                                    )
                            )
                    );
                }
            }
        }


        return List.copyOf(
                result
        );
    }


    public int pawnCount() {

        int count =
                0;


        if (firstType
                == PieceType.PAWN) {

            count++;
        }


        if (secondType
                == PieceType.PAWN) {

            count++;
        }


        return count;
    }


    /**
     * Build dependency tier.
     *
     * Promotion always decreases the number of pawns:
     *
     *     0-pawn classes
     *         first
     *
     *     1-pawn classes
     *         may transition into 0-pawn classes
     *
     *     2-pawn classes
     *         may transition into 1-pawn classes
     *
     * This gives us a clean material-class dependency DAG instead of
     * attempting to solve every four-piece class simultaneously.
     */
    public int buildTier() {

        return pawnCount();
    }


    public String displayName() {

        if (distribution
                == Distribution.SAME_SIDE) {

            return "K"
                    + symbol(
                    firstType
            )
                    + symbol(
                    secondType
            )
                    + "K";
        }


        return "K"
                + symbol(
                firstType
        )
                + " vs K"
                + symbol(
                secondType
        );
    }


    public String assetStem() {

        if (distribution
                == Distribution.SAME_SIDE) {

            return "K"
                    + symbol(
                    firstType
            )
                    + symbol(
                    secondType
            )
                    + "K";
        }


        return "K"
                + symbol(
                firstType
        )
                + "-K"
                + symbol(
                secondType
        );
    }


    public boolean isKqrkFamily() {

        return distribution
                == Distribution.SAME_SIDE
                &&
                firstType
                        == PieceType.QUEEN
                &&
                secondType
                        == PieceType.ROOK;
    }


    private static void requireNonKing(
            PieceType type
    ) {

        if (!NON_KING_TYPES.contains(
                type
        )) {

            throw new IllegalArgumentException(
                    "Four-piece non-king material must be Q, R, B, N, or P: "
                            + type
            );
        }
    }


    private static int order(
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

            case PAWN ->
                    4;

            default ->
                    throw new IllegalArgumentException(
                            "Not a supported non-king material type: "
                                    + type
                    );
        };
    }


    private static String symbol(
            PieceType type
    ) {

        return switch (type) {

            case QUEEN ->
                    "Q";

            case ROOK ->
                    "R";

            case BISHOP ->
                    "B";

            case KNIGHT ->
                    "N";

            case PAWN ->
                    "P";

            default ->
                    throw new IllegalArgumentException(
                            "Not a supported non-king material type: "
                                    + type
                    );
        };
    }
}

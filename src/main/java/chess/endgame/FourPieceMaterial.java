package main.java.chess.endgame;

import main.java.chess.model.Color;
import main.java.chess.model.PieceType;


/**
 * Describes one exact four-piece material class:
 *
 *     king + piece A + piece B versus king
 *
 * The first implementation deliberately starts with KQRK.  Queen and rook
 * are distinct, which keeps the state encoding unambiguous while exercising
 * the important four-piece -> three-piece capture transitions.
 *
 * This descriptor is intentionally separate from the tablebase itself so
 * additional four-piece classes can be added without changing the encoder,
 * codec, or service contracts.
 */
public record FourPieceMaterial(
        PieceType firstType,
        PieceType secondType,
        Color strongColor
) {

    public FourPieceMaterial {

        if (firstType == null
                || secondType == null) {

            throw new IllegalArgumentException(
                    "Four-piece material types cannot be null."
            );
        }


        if (strongColor == null) {

            throw new IllegalArgumentException(
                    "Strong color cannot be null."
            );
        }


        if (firstType == PieceType.KING
                || secondType == PieceType.KING) {

            throw new IllegalArgumentException(
                    "The two extra pieces cannot be kings."
            );
        }


        /*
         * Milestone 1 intentionally supports a single material class.
         * Keeping this restriction here prevents us from accidentally
         * treating an unproved class as exact.
         */
        boolean kqrk =
                (firstType == PieceType.QUEEN
                        && secondType == PieceType.ROOK)
                        ||
                        (firstType == PieceType.ROOK
                                && secondType == PieceType.QUEEN);


        if (!kqrk) {

            throw new IllegalArgumentException(
                    "Four-piece milestone 1 currently supports only KQRK."
            );
        }
    }


    public static FourPieceMaterial kqrk(
            Color strongColor
    ) {

        return new FourPieceMaterial(
                PieceType.QUEEN,
                PieceType.ROOK,
                strongColor
        );
    }


    public String materialName() {

        return "KQRK";
    }


    public Color weakColor() {

        return strongColor.opposite();
    }
}

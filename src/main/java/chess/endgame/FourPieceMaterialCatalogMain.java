package main.java.chess.endgame;

import java.util.List;


/**
 * Milestone 11 smoke test:
 *
 * enumerate the complete canonical four-piece material universe and verify
 * the dependency tiers that will drive the generalized tablebase builders.
 */
public final class FourPieceMaterialCatalogMain {

    public static void main(
            String[] args
    ) {

        List<FourPieceMaterialClass> catalog =
                FourPieceMaterialClass.allCanonical();


        int sameSide =
                0;

        int split =
                0;

        int zeroPawn =
                0;

        int onePawn =
                0;

        int twoPawn =
                0;


        System.out.println(
                "Canonical four-piece material catalog"
        );

        System.out.println(
                "====================================="
        );


        for (FourPieceMaterialClass material :
                catalog) {

            if (material.distribution()
                    == FourPieceMaterialClass.Distribution.SAME_SIDE) {

                sameSide++;

            } else {

                split++;
            }


            switch (material.pawnCount()) {

                case 0 ->
                        zeroPawn++;

                case 1 ->
                        onePawn++;

                case 2 ->
                        twoPawn++;

                default ->
                        throw new IllegalStateException(
                                "Impossible pawn count."
                        );
            }


            System.out.printf(
                    "Tier %d  %-10s  %s%n",
                    material.buildTier(),
                    material.assetStem(),
                    material.displayName()
            );
        }


        System.out.println();

        System.out.println(
                "Total canonical classes: "
                        + catalog.size()
        );

        System.out.println(
                "Same-side classes: "
                        + sameSide
        );

        System.out.println(
                "Split classes: "
                        + split
        );

        System.out.println(
                "0-pawn classes: "
                        + zeroPawn
        );

        System.out.println(
                "1-pawn classes: "
                        + onePawn
        );

        System.out.println(
                "2-pawn classes: "
                        + twoPawn
        );


        if (catalog.size() != 30) {

            throw new IllegalStateException(
                    "Expected exactly 30 canonical four-piece material classes."
            );
        }


        if (sameSide != 15
                || split != 15) {

            throw new IllegalStateException(
                    "Expected 15 same-side and 15 split classes."
            );
        }


        long kqrkCount =
                catalog.stream()
                        .filter(
                                FourPieceMaterialClass::isKqrkFamily
                        )
                        .count();


        if (kqrkCount != 1) {

            throw new IllegalStateException(
                    "KQRK must occur exactly once in the canonical catalog."
            );
        }


        System.out.println();

        System.out.println(
                "FOUR-PIECE MATERIAL CATALOG PASSED"
        );
    }
}

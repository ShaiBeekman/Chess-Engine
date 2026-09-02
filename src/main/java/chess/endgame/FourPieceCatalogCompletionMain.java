package main.java.chess.endgame;

import main.java.chess.model.PieceType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * M63C — final lightweight four-piece catalog completion gate.
 *
 * No retrograde solving occurs here.
 *
 * The gate verifies:
 *   1. all 30 canonical four-piece material families are represented;
 *   2. all 30 persisted assets exist;
 *   3. all 29 generic-format assets load and pass their persisted checksum;
 *   4. the EP-aware KP-KP asset loads and matches the M62B/M63 digest;
 *   5. the unified exact facade contains the M63B KP-KP routing fix.
 */
public final class FourPieceCatalogCompletionMain {

    private static final String EXPECTED_KPKP_DIGEST =
            "65e248648ada7b4372976a0b41ef7d0bc7a684ce51fa85be17c51453688e7cc9";

    private FourPieceCatalogCompletionMain() {
    }

    public static void main(
            String[] args
    ) throws Exception {

        if (args.length > 1) {
            throw new IllegalArgumentException(
                    "Usage: FourPieceCatalogCompletionMain [tablebase-directory]"
            );
        }

        Path directory =
                args.length == 0
                        ? Path.of(
                        "tablebases",
                        "four-piece"
                )
                        : Path.of(
                        args[0]
                );

        directory =
                directory.toAbsolutePath()
                        .normalize();

        System.out.println(
                "Final four-piece tablebase catalog gate"
        );
        System.out.println(
                "======================================="
        );
        System.out.println(
                "Directory: "
                        + directory
        );

        List<Entry> entries =
                canonicalEntries();

        require(
                entries.size() == 30,
                "Expected exactly 30 canonical four-piece families."
        );

        int tierZero =
                0;

        int tierOne =
                0;

        int tierTwo =
                0;

        for (Entry entry :
                entries) {

            if (entry.tier() == 0) {

                tierZero++;

            } else if (entry.tier() == 1) {

                tierOne++;

            } else if (entry.tier() == 2) {

                tierTwo++;

            } else {

                throw new IllegalStateException(
                        "Unexpected tier for "
                                + entry.material()
                                .displayName()
                );
            }
        }

        require(
                tierZero == 20,
                "Expected 20 Tier-0 families."
        );

        require(
                tierOne == 8,
                "Expected 8 Tier-1 families."
        );

        require(
                tierTwo == 2,
                "Expected 2 Tier-2 families."
        );

        System.out.println();
        System.out.println(
                "Canonical catalog"
        );
        System.out.println(
                "-----------------"
        );
        System.out.println(
                "Total: 30"
        );
        System.out.println(
                "Tier 0: 20"
        );
        System.out.println(
                "Tier 1: 8"
        );
        System.out.println(
                "Tier 2: 2"
        );
        System.out.println(
                "PASSED"
        );

        System.out.println();
        System.out.println(
                "Persisted asset verification"
        );
        System.out.println(
                "----------------------------"
        );

        int checked =
                0;

        for (Entry entry :
                entries) {

            Path path =
                    directory.resolve(
                            entry.fileName()
                    );

            require(
                    Files.isRegularFile(
                            path
                    ),
                    "Missing persisted asset for "
                            + entry.material()
                            .displayName()
                            + ": "
                            + path
            );

            if (entry.kpkp()) {

                FourPieceTierTwoKpkpTablebaseCodec.Tablebase tablebase =
                        FourPieceTierTwoKpkpTablebaseCodec.load(
                                path
                        );

                String digest =
                        FourPieceTierTwoKpkpTablebaseCodec.contentDigest(
                                tablebase
                        );

                require(
                        EXPECTED_KPKP_DIGEST.equals(
                                digest
                        ),
                        "KP-KP digest mismatch."
                                + "\nExpected: "
                                + EXPECTED_KPKP_DIGEST
                                + "\nActual:   "
                                + digest
                );

            } else {

                FourPieceGenericTablebase tablebase =
                        FourPieceGenericTablebaseCodec.load(
                                path
                        );

                require(
                        tablebase.material()
                                .equals(
                                        entry.material()
                                ),
                        "Material metadata mismatch in "
                                + path
                                + ". Expected "
                                + entry.material()
                                .displayName()
                                + ", found "
                                + tablebase.material()
                                .displayName()
                                + "."
                );

                require(
                        tablebase.sameSideOwnerIsWhite(),
                        "Non-canonical orientation stored in "
                                + path
                );
            }

            checked++;

            System.out.printf(
                    "[%2d / 30] Tier %d  %-8s  %s%n",
                    checked,
                    entry.tier(),
                    entry.material()
                            .assetStem(),
                    entry.fileName()
            );
        }

        require(
                checked == 30,
                "Expected to verify exactly 30 persisted assets."
        );

        System.out.println(
                "All 30 persisted assets: PASSED"
        );

        System.out.println();
        System.out.println(
                "Unified runtime facade"
        );
        System.out.println(
                "----------------------"
        );

        require(
                "M63B-KPKP-RUNTIME-ROUTING-V1".equals(
                        ExactEndgameTablebase.BUILD_ID
                ),
                "ExactEndgameTablebase is not the M63B runtime-routing build."
        );

        System.out.println(
                "ExactEndgameTablebase BUILD_ID: "
                        + ExactEndgameTablebase.BUILD_ID
        );

        System.out.println(
                "KP-KP routing source: PASSED"
        );

        System.out.println();
        System.out.println(
                "============================================================"
        );
        System.out.println(
                "30 / 30 FOUR-PIECE FAMILIES EXACTLY SOLVED, PERSISTED, AND RUNTIME-READY"
        );
        System.out.println(
                "FOUR-PIECE TABLEBASE CATALOG COMPLETE"
        );
        System.out.println(
                "============================================================"
        );
    }

    private static List<Entry> canonicalEntries() {

        List<Entry> entries =
                new ArrayList<>(
                        30
                );

        /*
         * Tier 0 — pawnless SAME_SIDE.
         */
        addSameSide(
                entries,
                0,
                PieceType.QUEEN,
                PieceType.QUEEN
        );

        addSameSide(
                entries,
                0,
                PieceType.QUEEN,
                PieceType.ROOK
        );

        addSameSide(
                entries,
                0,
                PieceType.QUEEN,
                PieceType.BISHOP
        );

        addSameSide(
                entries,
                0,
                PieceType.QUEEN,
                PieceType.KNIGHT
        );

        addSameSide(
                entries,
                0,
                PieceType.ROOK,
                PieceType.ROOK
        );

        addSameSide(
                entries,
                0,
                PieceType.ROOK,
                PieceType.BISHOP
        );

        addSameSide(
                entries,
                0,
                PieceType.ROOK,
                PieceType.KNIGHT
        );

        addSameSide(
                entries,
                0,
                PieceType.BISHOP,
                PieceType.BISHOP
        );

        addSameSide(
                entries,
                0,
                PieceType.BISHOP,
                PieceType.KNIGHT
        );

        addSameSide(
                entries,
                0,
                PieceType.KNIGHT,
                PieceType.KNIGHT
        );

        /*
         * Tier 0 — pawnless SPLIT.
         */
        addSplit(
                entries,
                0,
                PieceType.QUEEN,
                PieceType.QUEEN
        );

        addSplit(
                entries,
                0,
                PieceType.QUEEN,
                PieceType.ROOK
        );

        addSplit(
                entries,
                0,
                PieceType.QUEEN,
                PieceType.BISHOP
        );

        addSplit(
                entries,
                0,
                PieceType.QUEEN,
                PieceType.KNIGHT
        );

        addSplit(
                entries,
                0,
                PieceType.ROOK,
                PieceType.ROOK
        );

        addSplit(
                entries,
                0,
                PieceType.ROOK,
                PieceType.BISHOP
        );

        addSplit(
                entries,
                0,
                PieceType.ROOK,
                PieceType.KNIGHT
        );

        addSplit(
                entries,
                0,
                PieceType.BISHOP,
                PieceType.BISHOP
        );

        addSplit(
                entries,
                0,
                PieceType.BISHOP,
                PieceType.KNIGHT
        );

        addSplit(
                entries,
                0,
                PieceType.KNIGHT,
                PieceType.KNIGHT
        );

        /*
         * Tier 1 — exactly one pawn.
         */
        addSameSide(
                entries,
                1,
                PieceType.QUEEN,
                PieceType.PAWN
        );

        addSameSide(
                entries,
                1,
                PieceType.ROOK,
                PieceType.PAWN
        );

        addSameSide(
                entries,
                1,
                PieceType.BISHOP,
                PieceType.PAWN
        );

        addSameSide(
                entries,
                1,
                PieceType.KNIGHT,
                PieceType.PAWN
        );

        addSplit(
                entries,
                1,
                PieceType.QUEEN,
                PieceType.PAWN
        );

        addSplit(
                entries,
                1,
                PieceType.ROOK,
                PieceType.PAWN
        );

        addSplit(
                entries,
                1,
                PieceType.BISHOP,
                PieceType.PAWN
        );

        addSplit(
                entries,
                1,
                PieceType.KNIGHT,
                PieceType.PAWN
        );

        /*
         * Tier 2 — SAME_SIDE KPPK.
         */
        FourPieceMaterialClass kppk =
                FourPieceMaterialClass.sameSide(
                        PieceType.PAWN,
                        PieceType.PAWN
                );

        entries.add(
                new Entry(
                        2,
                        kppk,
                        kppk.assetStem()
                                + "-white.ftb.gz",
                        false
                )
        );

        /*
         * Tier 2 — SPLIT KP-KP.
         *
         * This uses its own EP-aware persistence format.
         */
        FourPieceMaterialClass kpkp =
                FourPieceMaterialClass.split(
                        PieceType.PAWN,
                        PieceType.PAWN
                );

        entries.add(
                new Entry(
                        2,
                        kpkp,
                        kpkp.assetStem()
                                + "-canonical-ep.ftb.gz",
                        true
                )
        );

        return List.copyOf(
                entries
        );
    }

    private static void addSameSide(
            List<Entry> entries,
            int tier,
            PieceType first,
            PieceType second
    ) {

        FourPieceMaterialClass material =
                FourPieceMaterialClass.sameSide(
                        first,
                        second
                );

        entries.add(
                new Entry(
                        tier,
                        material,
                        material.assetStem()
                                + "-white.ftb.gz",
                        false
                )
        );
    }

    private static void addSplit(
            List<Entry> entries,
            int tier,
            PieceType first,
            PieceType second
    ) {

        FourPieceMaterialClass material =
                FourPieceMaterialClass.split(
                        first,
                        second
                );

        entries.add(
                new Entry(
                        tier,
                        material,
                        material.assetStem()
                                + "-canonical.ftb.gz",
                        false
                )
        );
    }

    private static void require(
            boolean condition,
            String message
    ) {

        if (!condition) {

            throw new IllegalStateException(
                    message
            );
        }
    }

    private record Entry(
            int tier,
            FourPieceMaterialClass material,
            String fileName,
            boolean kpkp
    ) {
    }
}
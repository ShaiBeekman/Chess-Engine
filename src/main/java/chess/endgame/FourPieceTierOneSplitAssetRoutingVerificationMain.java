package main.java.chess.endgame;

import main.java.chess.model.PieceType;

import java.nio.file.Path;
import java.util.List;

/**
 * Milestone 52:
 *
 * Prepare generic Tier-1 runtime asset routing for the four SPLIT one-pawn
 * families without disturbing the four already-persisted SAME_SIDE families.
 */
public final class FourPieceTierOneSplitAssetRoutingVerificationMain {

    private FourPieceTierOneSplitAssetRoutingVerificationMain() {
    }

    public static void main(
            String[] args
    ) {

        if (args.length > 1) {
            throw new IllegalArgumentException(
                    "Usage: FourPieceTierOneSplitAssetRoutingVerificationMain [tablebase-directory]"
            );
        }

        Path directory =
                args.length == 0
                        ? FourPieceTierOneTablebaseService.DEFAULT_DIRECTORY
                        : Path.of(
                        args[0]
                );

        FourPieceTierOneTablebaseService service =
                new FourPieceTierOneTablebaseService(
                        directory
                );

        System.out.println(
                "Tier-1 split asset-routing preparation gate"
        );
        System.out.println(
                "==========================================="
        );

        List<PieceType> nonPawns =
                List.of(
                        PieceType.QUEEN,
                        PieceType.ROOK,
                        PieceType.BISHOP,
                        PieceType.KNIGHT
                );

        int checked =
                0;

        System.out.println();
        System.out.println(
                "SAME_SIDE naming regression"
        );
        System.out.println(
                "---------------------------"
        );

        for (PieceType type :
                nonPawns) {

            FourPieceMaterialClass material =
                    FourPieceMaterialClass.sameSide(
                            type,
                            PieceType.PAWN
                    );

            String expected =
                    material.assetStem()
                            + "-white.ftb.gz";

            verifyFileName(
                    service,
                    material,
                    expected
            );

            System.out.println(
                    "  "
                            + material.displayName()
                            + " -> "
                            + expected
                            + "  PASSED"
            );

            checked++;
        }

        System.out.println();
        System.out.println(
                "SPLIT canonical naming"
        );
        System.out.println(
                "----------------------"
        );

        for (PieceType type :
                nonPawns) {

            FourPieceMaterialClass material =
                    FourPieceMaterialClass.split(
                            type,
                            PieceType.PAWN
                    );

            String expected =
                    material.assetStem()
                            + "-canonical.ftb.gz";

            verifyFileName(
                    service,
                    material,
                    expected
            );

            System.out.println(
                    "  "
                            + material.displayName()
                            + " -> "
                            + expected
                            + "  PASSED"
            );

            checked++;
        }

        if (checked
                != 8) {
            throw new IllegalStateException(
                    "Expected exactly eight Tier-1 asset-routing checks."
            );
        }

        /*
         * Existing SAME_SIDE assets should still be discoverable under exactly
         * the filenames already produced by M39/M46/M49/M51.  We deliberately
         * do not require the files to exist here so this gate can also run
         * against a clean/custom tablebase directory.
         */
        verifyExactSuffix(
                service,
                FourPieceMaterialClass.sameSide(
                        PieceType.QUEEN,
                        PieceType.PAWN
                ),
                "KQPK-white.ftb.gz"
        );

        verifyExactSuffix(
                service,
                FourPieceMaterialClass.sameSide(
                        PieceType.ROOK,
                        PieceType.PAWN
                ),
                "KRPK-white.ftb.gz"
        );

        verifyExactSuffix(
                service,
                FourPieceMaterialClass.sameSide(
                        PieceType.BISHOP,
                        PieceType.PAWN
                ),
                "KBPK-white.ftb.gz"
        );

        verifyExactSuffix(
                service,
                FourPieceMaterialClass.sameSide(
                        PieceType.KNIGHT,
                        PieceType.PAWN
                ),
                "KNPK-white.ftb.gz"
        );

        verifyExactSuffix(
                service,
                FourPieceMaterialClass.split(
                        PieceType.QUEEN,
                        PieceType.PAWN
                ),
                "KQ-KP-canonical.ftb.gz"
        );

        System.out.println();
        System.out.println(
                "Existing SAME_SIDE filenames unchanged: PASSED"
        );
        System.out.println(
                "First SPLIT filename reserved: KQ-KP-canonical.ftb.gz"
        );

        if (service.loadedCount()
                != 0) {
            throw new IllegalStateException(
                    "Asset-path verification unexpectedly loaded a tablebase."
            );
        }

        System.out.println(
                "No tablebases loaded by routing check: PASSED"
        );

        System.out.println();
        System.out.println(
                "TIER-1 SPLIT ASSET ROUTING PREPARATION PASSED"
        );
        System.out.println(
                "NEXT: SOLVE / VALIDATE KQ-KP IN CANONICAL SPLIT ORIENTATION"
        );
    }

    private static void verifyFileName(
            FourPieceTierOneTablebaseService service,
            FourPieceMaterialClass material,
            String expected
    ) {

        Path actual =
                service.assetPath(
                        material
                );

        Path fileName =
                actual.getFileName();

        if (fileName == null
                || !expected.equals(
                fileName.toString()
        )) {
            throw new IllegalStateException(
                    material.displayName()
                            + " asset path mismatch. Expected "
                            + expected
                            + ", found "
                            + actual
            );
        }
    }

    private static void verifyExactSuffix(
            FourPieceTierOneTablebaseService service,
            FourPieceMaterialClass material,
            String expected
    ) {

        verifyFileName(
                service,
                material,
                expected
        );
    }
}

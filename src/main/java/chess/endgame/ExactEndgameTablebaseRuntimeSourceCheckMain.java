package main.java.chess.endgame;

/**
 * M63B compile/source sanity check for the unified exact facade.
 */
public final class ExactEndgameTablebaseRuntimeSourceCheckMain {

    private ExactEndgameTablebaseRuntimeSourceCheckMain() {
    }

    public static void main(String[] args) {
        System.out.println("ExactEndgameTablebase runtime source check");
        System.out.println("==========================================");
        System.out.println("BUILD_ID: " + ExactEndgameTablebase.BUILD_ID);

        if (!"M63B-KPKP-RUNTIME-ROUTING-V1".equals(
                ExactEndgameTablebase.BUILD_ID)) {
            throw new IllegalStateException(
                    "Unexpected ExactEndgameTablebase BUILD_ID.");
        }

        System.out.println("M63B EXACT FACADE SOURCE / COMPILE CHECK PASSED");
    }
}

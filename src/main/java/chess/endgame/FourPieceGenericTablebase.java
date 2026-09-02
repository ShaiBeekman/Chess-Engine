package main.java.chess.endgame;


/**
 * Immutable in-memory representation of one solved generic
 * four-piece material/orientation tablebase.
 *
 * The primitive state encoding is FourPieceGenericPrimitiveState.
 *
 * This object deliberately owns the supplied arrays without cloning them.
 * The four-piece arrays are large, so unnecessary 100+ MB copies would be
 * wasteful.
 */
public final class FourPieceGenericTablebase {

    private final FourPieceMaterialClass material;

    private final boolean sameSideOwnerIsWhite;

    private final byte[] outcome;

    private final short[] distance;

    private final long legalStates;

    private final long wins;

    private final long losses;

    private final long draws;

    private final int maximumDistance;


    FourPieceGenericTablebase(
            FourPieceMaterialClass material,
            boolean sameSideOwnerIsWhite,
            byte[] outcome,
            short[] distance,
            long legalStates,
            long wins,
            long losses,
            long draws,
            int maximumDistance
    ) {

        if (material == null) {

            throw new IllegalArgumentException(
                    "Material cannot be null."
            );
        }


        if (outcome == null
                || distance == null) {

            throw new IllegalArgumentException(
                    "Tablebase arrays cannot be null."
            );
        }


        if (outcome.length
                != FourPieceGenericPrimitiveState.STATE_COUNT) {

            throw new IllegalArgumentException(
                    "Outcome array has incorrect size: "
                            + outcome.length
            );
        }


        if (distance.length
                != FourPieceGenericPrimitiveState.STATE_COUNT) {

            throw new IllegalArgumentException(
                    "Distance array has incorrect size: "
                            + distance.length
            );
        }


        if (legalStates < 0
                || wins < 0
                || losses < 0
                || draws < 0) {

            throw new IllegalArgumentException(
                    "Tablebase counts cannot be negative."
            );
        }


        if (wins + losses + draws
                != legalStates) {

            throw new IllegalArgumentException(
                    "WIN + LOSS + DRAW does not equal legal-state count."
            );
        }


        this.material =
                material;

        this.sameSideOwnerIsWhite =
                sameSideOwnerIsWhite;

        this.outcome =
                outcome;

        this.distance =
                distance;

        this.legalStates =
                legalStates;

        this.wins =
                wins;

        this.losses =
                losses;

        this.draws =
                draws;

        this.maximumDistance =
                maximumDistance;
    }


    public static FourPieceGenericTablebase fromResult(
            FourPieceGenericPrimitiveRetrogradeBuilder.Result result
    ) {

        if (result == null) {

            throw new IllegalArgumentException(
                    "Retrograde result cannot be null."
            );
        }


        return new FourPieceGenericTablebase(
                result.material(),
                result.sameSideOwnerIsWhite(),
                result.outcome(),
                result.distance(),
                result.legalStates(),
                result.wins(),
                result.losses(),
                result.draws(),
                result.maximumDistance()
        );
    }


    public FourPieceMaterialClass material() {

        return material;
    }


    public boolean sameSideOwnerIsWhite() {

        return sameSideOwnerIsWhite;
    }


    public long legalStates() {

        return legalStates;
    }


    public long wins() {

        return wins;
    }


    public long losses() {

        return losses;
    }


    public long draws() {

        return draws;
    }


    public int maximumDistance() {

        return maximumDistance;
    }


    public byte outcome(
            int state
    ) {

        requireState(
                state
        );

        return outcome[state];
    }


    public short distance(
            int state
    ) {

        requireState(
                state
        );

        return distance[state];
    }


    public Probe probe(
            int state
    ) {

        requireState(
                state
        );


        return new Probe(
                outcome[state],
                distance[state]
        );
    }


    public boolean isValidState(
            int state
    ) {

        requireState(
                state
        );


        return outcome[state]
                != FourPieceTablebase.INVALID;
    }


    /*
     * Package-private direct array access.
     *
     * Persistence/verification code in this package can use the data
     * without forcing enormous defensive copies.
     */
    byte[] outcomeData() {

        return outcome;
    }


    short[] distanceData() {

        return distance;
    }


    private static void requireState(
            int state
    ) {

        if (state < 0
                || state
                >= FourPieceGenericPrimitiveState.STATE_COUNT) {

            throw new IllegalArgumentException(
                    "Primitive state out of range: "
                            + state
            );
        }
    }


    public record Probe(
            byte outcome,
            short distance
    ) {

    }
}
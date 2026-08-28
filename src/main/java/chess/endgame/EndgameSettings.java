package main.java.chess.endgame;

public record EndgameSettings(
        int minimumPieces,
        int maximumPieces
) {

    public EndgameSettings {

        if (minimumPieces < 3) {
            throw new IllegalArgumentException(
                    "Minimum pieces must be at least 3."
            );
        }

        if (maximumPieces < minimumPieces) {
            throw new IllegalArgumentException(
                    "Maximum pieces cannot be smaller than minimum pieces."
            );
        }

        if (maximumPieces > 7) {
            throw new IllegalArgumentException(
                    "The current Endgame Study range is 3–7 total pieces."
            );
        }
    }


    public static EndgameSettings fixed(
            int pieces
    ) {

        return new EndgameSettings(
                pieces,
                pieces
        );
    }


    public static EndgameSettings variable(
            int minimumPieces,
            int maximumPieces
    ) {

        return new EndgameSettings(
                minimumPieces,
                maximumPieces
        );
    }


    public static EndgameSettings variable() {

        return new EndgameSettings(
                3,
                7
        );
    }


    public boolean isFixed() {

        return minimumPieces
                == maximumPieces;
    }


    public String displayName() {

        if (isFixed()) {

            return "Fixed "
                    + minimumPieces;
        }


        return "Variable "
                + minimumPieces
                + "–"
                + maximumPieces;
    }
}

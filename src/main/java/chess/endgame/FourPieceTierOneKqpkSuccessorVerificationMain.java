package main.java.chess.endgame;

import main.java.chess.model.Color;
import main.java.chess.model.Move;
import main.java.chess.model.PieceType;
import main.java.chess.model.Position;
import main.java.chess.model.Square;
import main.java.chess.rules.MoveGenerator;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class FourPieceTierOneKqpkSuccessorVerificationMain {

    private static final int RANDOM_POSITIONS = 10_000;
    private static final MoveGenerator OBJECT_GENERATOR = new MoveGenerator();

    private FourPieceTierOneKqpkSuccessorVerificationMain() {
    }

    public static void main(String[] args) {
        System.out.println("Tier-1 KQPK successor classification verification");
        System.out.println("===============================================");
        System.out.println();

        FourPieceTierOneKqpkSuccessorClassifier classifier =
                new FourPieceTierOneKqpkSuccessorClassifier();

        verifyWhitePushAndPromotionFixtures(classifier);
        verifyBlackPushAndPromotionFixtures(classifier);
        verifyThreePieceCaptureBoundaries(classifier);
        verifyRandomizedAccounting(classifier, Color.WHITE, 0x4B51504B31L);
        verifyRandomizedAccounting(classifier, Color.BLACK, 0x4B51504B32L);

        System.out.println();
        System.out.println("TIER-1 KQPK SUCCESSOR CLASSIFICATION PASSED");
        System.out.println("NEXT: REPLACE THE BRIDGE WITH ALLOCATION-FREE PRIMITIVE GENERATION");
    }

    private static void verifyWhitePushAndPromotionFixtures(
            FourPieceTierOneKqpkSuccessorClassifier classifier
    ) {
        int pushState = state("a1", "h8", "b1", "e2", Color.WHITE);
        List<FourPieceTierOneKqpkSuccessorClassifier.Successor> pushes =
                classifier.generate(pushState, Color.WHITE);

        require(
                requireSuccessor(pushes, "e2", "e3", null).kind()
                        == FourPieceTierOneKqpkSuccessorClassifier.Kind.SAME_CLASS,
                "e2-e3 must remain KQPK."
        );
        require(
                requireSuccessor(pushes, "e2", "e4", null).kind()
                        == FourPieceTierOneKqpkSuccessorClassifier.Kind.SAME_CLASS,
                "e2-e4 must remain KQPK."
        );

        int promotionState = state("a1", "h8", "b1", "a7", Color.WHITE);
        List<FourPieceTierOneKqpkSuccessorClassifier.Successor> promotions =
                classifier.generate(promotionState, Color.WHITE);

        requirePromotion(promotions, PieceType.QUEEN, "KQQK");
        requirePromotion(promotions, PieceType.ROOK, "KQRK");
        requirePromotion(promotions, PieceType.BISHOP, "KQBK");
        requirePromotion(promotions, PieceType.KNIGHT, "KQNK");

        System.out.println("White KQPK fixtures: PASSED");
        System.out.println("  e2-e3/e4 stay in class");
        System.out.println("  a7-a8=Q/R/B/N route to Tier 0");
        System.out.println();
    }

    private static void verifyBlackPushAndPromotionFixtures(
            FourPieceTierOneKqpkSuccessorClassifier classifier
    ) {
        int pushState = state("a1", "h8", "g8", "e7", Color.BLACK);
        List<FourPieceTierOneKqpkSuccessorClassifier.Successor> pushes =
                classifier.generate(pushState, Color.BLACK);

        require(
                requireSuccessor(pushes, "e7", "e6", null).kind()
                        == FourPieceTierOneKqpkSuccessorClassifier.Kind.SAME_CLASS,
                "e7-e6 must remain KQPK."
        );
        require(
                requireSuccessor(pushes, "e7", "e5", null).kind()
                        == FourPieceTierOneKqpkSuccessorClassifier.Kind.SAME_CLASS,
                "e7-e5 must remain KQPK."
        );

        int promotionState = state("a1", "h8", "g8", "h2", Color.BLACK);
        List<FourPieceTierOneKqpkSuccessorClassifier.Successor> promotions =
                classifier.generate(promotionState, Color.BLACK);

        requirePromotion(promotions, PieceType.QUEEN, "KQQK");
        requirePromotion(promotions, PieceType.ROOK, "KQRK");
        requirePromotion(promotions, PieceType.BISHOP, "KQBK");
        requirePromotion(promotions, PieceType.KNIGHT, "KQNK");

        System.out.println("Black KQPK fixtures: PASSED");
        System.out.println("  e7-e6/e5 stay in class");
        System.out.println("  h2-h1=Q/R/B/N route to Tier 0");
        System.out.println();
    }

    private static void verifyThreePieceCaptureBoundaries(
            FourPieceTierOneKqpkSuccessorClassifier classifier
    ) {
        int pawnCaptureState = state("a1", "d3", "h1", "c2", Color.BLACK);
        FourPieceTierOneKqpkSuccessorClassifier.Successor pawnCaptured =
                requireSuccessor(
                        classifier.generate(pawnCaptureState, Color.WHITE),
                        "d3",
                        "c2",
                        null
                );

        require(
                pawnCaptured.kind()
                        == FourPieceTierOneKqpkSuccessorClassifier.Kind.THREE_PIECE_BOUNDARY,
                "Black Kxc2 must leave a three-piece boundary."
        );
        require(
                pawnCaptured.threePieceType() == PieceType.QUEEN,
                "After Kxc2, KQK must remain."
        );

        int queenCaptureState = state("a1", "d3", "c2", "b2", Color.BLACK);
        FourPieceTierOneKqpkSuccessorClassifier.Successor queenCaptured =
                requireSuccessor(
                        classifier.generate(queenCaptureState, Color.WHITE),
                        "d3",
                        "c2",
                        null
                );

        require(
                queenCaptured.kind()
                        == FourPieceTierOneKqpkSuccessorClassifier.Kind.THREE_PIECE_BOUNDARY,
                "Black Kxc2 must leave a three-piece boundary."
        );
        require(
                queenCaptured.threePieceType() == PieceType.PAWN,
                "After queen capture, KPK must remain."
        );

        System.out.println("Three-piece capture boundaries: PASSED");
        System.out.println("  pawn captured -> KQK");
        System.out.println("  queen captured -> KPK");
        System.out.println();
    }

    private static void verifyRandomizedAccounting(
            FourPieceTierOneKqpkSuccessorClassifier classifier,
            Color strongColor,
            long seed
    ) {
        Random random = new Random(seed);
        int checked = 0;
        long attempts = 0;
        long legalMoves = 0;

        Map<FourPieceTierOneKqpkSuccessorClassifier.Kind, Long> kinds =
                new EnumMap<>(FourPieceTierOneKqpkSuccessorClassifier.Kind.class);
        for (FourPieceTierOneKqpkSuccessorClassifier.Kind kind :
                FourPieceTierOneKqpkSuccessorClassifier.Kind.values()) {
            kinds.put(kind, 0L);
        }

        while (checked < RANDOM_POSITIONS) {
            attempts++;

            int pawnRank = 1 + random.nextInt(6);
            int pawnSquare = pawnRank * 8 + random.nextInt(8);
            int whiteKing = random.nextInt(64);
            int blackKing = random.nextInt(64);
            int queen = random.nextInt(64);

            if (!distinct(whiteKing, blackKing, queen, pawnSquare)
                    || kingsAdjacent(whiteKing, blackKing)) {
                continue;
            }

            int state = FourPieceGenericPrimitiveState.encode(
                    whiteKing,
                    blackKing,
                    queen,
                    pawnSquare,
                    random.nextBoolean()
            );

            Position position = classifier.positionForState(state, strongColor);
            List<Move> expected = OBJECT_GENERATOR.generateLegalMoves(position);

            List<FourPieceTierOneKqpkSuccessorClassifier.Successor> actual;
            try {
                actual = classifier.generate(state, strongColor);
            } catch (IllegalArgumentException exception) {
                /*
                 * Raw primitive states are denser than the legal chess state
                 * space. The classifier explicitly rejects positions whose
                 * generated successors reveal a king-capture artifact.
                 */
                continue;
            }

            require(
                    actual.size() == expected.size(),
                    "Classifier lost or duplicated a legal successor."
            );

            for (FourPieceTierOneKqpkSuccessorClassifier.Successor successor : actual) {
                kinds.put(successor.kind(), kinds.get(successor.kind()) + 1);

                switch (successor.kind()) {
                    case SAME_CLASS -> require(
                            successor.childState() >= 0,
                            "Same-class randomized child lacks primitive state."
                    );
                    case THREE_PIECE_BOUNDARY -> require(
                            successor.threePieceType() == PieceType.QUEEN
                                    || successor.threePieceType() == PieceType.PAWN,
                            "Randomized three-piece boundary must be KQK or KPK."
                    );
                    case TIER_ZERO_PROMOTION -> require(
                            successor.tierZeroMaterial() != null
                                    && successor.tierZeroMaterial().buildTier() == 0,
                            "Randomized promotion boundary must be Tier 0."
                    );
                }
            }

            legalMoves += actual.size();
            checked++;
        }

        System.out.println("Randomized accounting — strong " + strongColor + ": PASSED");
        System.out.println("  Positions: " + checked + "  attempts: " + attempts);
        System.out.println("  Legal successors: " + legalMoves);
        System.out.println("  Same-class: " + kinds.get(FourPieceTierOneKqpkSuccessorClassifier.Kind.SAME_CLASS));
        System.out.println("  Three-piece boundaries: " + kinds.get(FourPieceTierOneKqpkSuccessorClassifier.Kind.THREE_PIECE_BOUNDARY));
        System.out.println("  Tier-0 promotions: " + kinds.get(FourPieceTierOneKqpkSuccessorClassifier.Kind.TIER_ZERO_PROMOTION));
        System.out.println();
    }

    private static void requirePromotion(
            List<FourPieceTierOneKqpkSuccessorClassifier.Successor> successors,
            PieceType promotion,
            String expectedAssetStem
    ) {
        FourPieceTierOneKqpkSuccessorClassifier.Successor successor =
                successors.stream()
                        .filter(candidate ->
                                candidate.move().isPromotion()
                                        && candidate.move().promotion() == promotion)
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Missing promotion to " + promotion));

        require(
                successor.kind()
                        == FourPieceTierOneKqpkSuccessorClassifier.Kind.TIER_ZERO_PROMOTION,
                promotion + " promotion must be a Tier-0 boundary."
        );
        require(
                successor.tierZeroMaterial() != null,
                promotion + " promotion is missing Tier-0 material metadata."
        );
        require(
                expectedAssetStem.equals(successor.tierZeroMaterial().assetStem()),
                promotion + " promotion routed to "
                        + successor.tierZeroMaterial().assetStem()
                        + " instead of "
                        + expectedAssetStem
                        + "."
        );
    }

    private static FourPieceTierOneKqpkSuccessorClassifier.Successor requireSuccessor(
            List<FourPieceTierOneKqpkSuccessorClassifier.Successor> successors,
            String from,
            String to,
            PieceType promotion
    ) {
        Square fromSquare = Square.fromAlgebraic(from);
        Square toSquare = Square.fromAlgebraic(to);

        for (FourPieceTierOneKqpkSuccessorClassifier.Successor successor : successors) {
            Move move = successor.move();
            if (!move.from().equals(fromSquare) || !move.to().equals(toSquare)) {
                continue;
            }
            if (promotion == null && !move.isPromotion()) {
                return successor;
            }
            if (promotion != null && move.isPromotion() && move.promotion() == promotion) {
                return successor;
            }
        }

        throw new IllegalStateException(
                "Required successor not found: "
                        + from
                        + " -> "
                        + to
                        + (promotion == null ? "" : "=" + promotion)
        );
    }

    private static int state(
            String whiteKing,
            String blackKing,
            String queen,
            String pawn,
            Color sideToMove
    ) {
        return FourPieceGenericPrimitiveState.encode(
                index(whiteKing),
                index(blackKing),
                index(queen),
                index(pawn),
                sideToMove == Color.BLACK
        );
    }

    private static int index(String algebraic) {
        Square square = Square.fromAlgebraic(algebraic);
        return square.rank() * 8 + square.file();
    }

    private static boolean distinct(int a, int b, int c, int d) {
        return a != b
                && a != c
                && a != d
                && b != c
                && b != d
                && c != d;
    }

    private static boolean kingsAdjacent(int first, int second) {
        int firstFile = first & 7;
        int firstRank = first >>> 3;
        int secondFile = second & 7;
        int secondRank = second >>> 3;

        return Math.abs(firstFile - secondFile) <= 1
                && Math.abs(firstRank - secondRank) <= 1;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}

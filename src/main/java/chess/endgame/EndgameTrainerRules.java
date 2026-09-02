package main.java.chess.endgame;

import java.util.Random;

/**
 * Pure scheduling rules shared by the Endgame Trainer UI and its regression
 * verification main.  Keeping these decisions outside Swing prevents the
 * verification gate from merely re-implementing the UI's behavior.
 */
public final class EndgameTrainerRules {

    public static final int FRESH_STUDIES_BEFORE_REVIEW = 4;

    private EndgameTrainerRules() {
    }

    /**
     * A review is due only when there is a real backlog and four fresh studies
     * have been delivered since the previous review.
     */
    public static boolean reviewDue(
            int reviewBacklog,
            int freshStudiesSinceReview
    ) {
        return reviewBacklog > 0
                && freshStudiesSinceReview >= FRESH_STUDIES_BEFORE_REVIEW;
    }

    /**
     * Updates the session-only pacing count after one study is delivered.
     * Review delivery resets the window; fresh delivery advances it, capped at
     * the review threshold.
     */
    public static int freshCountAfterDelivery(
            int currentFreshCount,
            boolean reviewDelivered
    ) {
        if (reviewDelivered) {
            return 0;
        }

        return Math.min(
                FRESH_STUDIES_BEFORE_REVIEW,
                Math.max(0, currentFreshCount) + 1
        );
    }

    /**
     * Chooses the piece count for Mixed.  With no forced review bucket this is
     * a true random 50/50 choice between three and four pieces.  A due review
     * only forces a bucket when exactly one bucket contains an eligible review.
     */
    public static int chooseMixedPieceCount(
            Random random,
            boolean reviewIsDue,
            boolean hasThreePieceReview,
            boolean hasFourPieceReview
    ) {
        if (random == null) {
            throw new IllegalArgumentException(
                    "Random source cannot be null."
            );
        }

        if (reviewIsDue) {
            if (hasThreePieceReview && !hasFourPieceReview) {
                return 3;
            }

            if (hasFourPieceReview && !hasThreePieceReview) {
                return 4;
            }
        }

        return random.nextBoolean()
                ? 4
                : 3;
    }

    /**
     * Mixed has no physical queue of its own.  Its review count is the sum of
     * the real family queues.
     */
    public static int aggregateReviewCount(
            EndgameStudyProgress progress,
            Iterable<String> curriculumFamilies
    ) {
        if (progress == null || curriculumFamilies == null) {
            return 0;
        }

        int total = 0;

        for (String family : curriculumFamilies) {
            if (family == null
                    || family.isBlank()
                    || "Mixed".equals(family)) {
                continue;
            }

            total += progress.reviewCount(family);
        }

        return total;
    }
}

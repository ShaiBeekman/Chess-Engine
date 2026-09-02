package main.java.chess.endgame;

import main.java.chess.model.PieceType;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Milestone 61 randomized completeness + soundness gate for exact EP-aware
 * SPLIT KP-KP same-class predecessor generation.
 *
 * Parent -> child completeness:
 *   every sampled M60 BOUNDARY_NONE edge must be recovered from its exact
 *   child State, including the EP-history bit.
 *
 * Child -> parent soundness:
 *   every returned predecessor must forward-generate that exact child State.
 *
 * EP-specific deterministic gates additionally prove:
 *   - an EP-enabled child is recovered from its creating double-push parent;
 *   - an ordinary child can recover an EP-enabled parent when the right expired
 *     because the mover chose another legal move.
 */
public final class FourPieceTierTwoKpkpPrimitivePredecessorCompletenessMain {

    private static final FourPieceMaterialClass KPKP =
            FourPieceMaterialClass.split(
                    PieceType.PAWN,
                    PieceType.PAWN
            );

    private static final int DEFAULT_PARENT_SAMPLES =
            100_000;

    private static final int DEFAULT_CHILD_SAMPLES =
            100_000;

    private FourPieceTierTwoKpkpPrimitivePredecessorCompletenessMain() {
    }

    public static void main(
            String[] args
    ) {

        if (args.length > 2) {
            throw new IllegalArgumentException(
                    "Usage: FourPieceTierTwoKpkpPrimitivePredecessorCompletenessMain "
                            + "[parent-samples] [child-samples]"
            );
        }

        int parentSamples =
                args.length >= 1
                        ? Integer.parseInt(args[0])
                        : DEFAULT_PARENT_SAMPLES;

        int childSamples =
                args.length >= 2
                        ? Integer.parseInt(args[1])
                        : DEFAULT_CHILD_SAMPLES;

        if (parentSamples < 1
                || childSamples < 1) {
            throw new IllegalArgumentException(
                    "Sample counts must be positive."
            );
        }

        System.out.println(
                "KP-KP Tier-2 EP-aware predecessor completeness / soundness gate"
        );
        System.out.println(
                "==============================================================="
        );
        System.out.println(
                "Parent samples: "
                        + parentSamples
        );
        System.out.println(
                "Child samples: "
                        + childSamples
        );

        verifyEpCreatingPredecessor();
        verifyExpiringEpPredecessor();

        runRandomGate(
                parentSamples,
                childSamples,
                0x4B504B504D3631L
        );

        System.out.println();
        System.out.println(
                "KP-KP EP-AWARE PREDECESSOR COMPLETENESS / SOUNDNESS GATE PASSED"
        );
        System.out.println(
                "NEXT: KP-KP DEPENDENCY-AWARE EXACT RETROGRADE BUILD / VALIDATION"
        );
    }

    private static void verifyEpCreatingPredecessor() {

        FourPieceTierTwoKpkpPrimitiveState.State parent =
                state(
                        "a1",
                        "h8",
                        "e2",
                        "d4",
                        false,
                        false
                );

        FourPieceTierTwoKpkpPrimitiveMoveGenerator.Buffer successors =
                new FourPieceTierTwoKpkpPrimitiveMoveGenerator.Buffer(32);

        FourPieceTierTwoKpkpPrimitiveMoveGenerator
                .generateLegalSuccessors(
                        parent,
                        KPKP,
                        successors
                );

        FourPieceTierTwoKpkpPrimitiveState.State child =
                findSameClassChild(
                        successors,
                        "e2",
                        "e4"
                );

        require(
                child != null
                        && child.enPassantAvailable(),
                "Expected WHITE e2-e4 to create an EP-enabled child."
        );

        FourPieceTierTwoKpkpPrimitivePredecessorGenerator.Buffer predecessors =
                new FourPieceTierTwoKpkpPrimitivePredecessorGenerator.Buffer(32);

        int count =
                FourPieceTierTwoKpkpPrimitivePredecessorGenerator
                        .generatePredecessors(
                                child,
                                KPKP,
                                predecessors
                        );

        require(
                contains(
                        predecessors,
                        count,
                        parent
                ),
                "EP-enabled child did not recover its creating double-push parent."
        );

        System.out.println(
                "EP-creating double-push predecessor: PASSED"
        );
    }

    private static void verifyExpiringEpPredecessor() {

        FourPieceTierTwoKpkpPrimitiveState.State parent =
                state(
                        "a1",
                        "h8",
                        "e5",
                        "d5",
                        false,
                        true
                );

        FourPieceTierTwoKpkpPrimitiveMoveGenerator.Buffer successors =
                new FourPieceTierTwoKpkpPrimitiveMoveGenerator.Buffer(32);

        FourPieceTierTwoKpkpPrimitiveMoveGenerator
                .generateLegalSuccessors(
                        parent,
                        KPKP,
                        successors
                );

        FourPieceTierTwoKpkpPrimitiveState.State child =
                findSameClassChild(
                        successors,
                        "a1",
                        "a2"
                );

        require(
                child != null
                        && !child.enPassantAvailable(),
                "Expected a1-a2 to expire the EP right."
        );

        FourPieceTierTwoKpkpPrimitivePredecessorGenerator.Buffer predecessors =
                new FourPieceTierTwoKpkpPrimitivePredecessorGenerator.Buffer(32);

        int count =
                FourPieceTierTwoKpkpPrimitivePredecessorGenerator
                        .generatePredecessors(
                                child,
                                KPKP,
                                predecessors
                        );

        require(
                contains(
                        predecessors,
                        count,
                        parent
                ),
                "Ordinary child did not recover EP-enabled parent whose right expired."
        );

        System.out.println(
                "EP-expiring predecessor history: PASSED"
        );
    }

    private static void runRandomGate(
            int parentSamples,
            int childSamples,
            long seed
    ) {

        Random random =
                new Random(seed);

        FourPieceTierTwoKpkpPrimitiveMoveGenerator.Buffer successors =
                new FourPieceTierTwoKpkpPrimitiveMoveGenerator.Buffer(32);

        FourPieceTierTwoKpkpPrimitiveMoveGenerator.Buffer validationForward =
                new FourPieceTierTwoKpkpPrimitiveMoveGenerator.Buffer(32);

        FourPieceTierTwoKpkpPrimitivePredecessorGenerator.Buffer predecessors =
                new FourPieceTierTwoKpkpPrimitivePredecessorGenerator.Buffer(32);

        long parentAttempts =
                0;

        int legalParents =
                0;

        long sameClassEdges =
                0;

        long epSameClassEdges =
                0;

        long boundaryEdges =
                0;

        long recoveredEdges =
                0;

        while (legalParents < parentSamples) {

            parentAttempts++;

            FourPieceTierTwoKpkpPrimitiveState.State parent =
                    randomState(random);

            if (!FourPieceTierTwoKpkpPrimitiveMoveGenerator
                    .isStructurallyLegal(
                            parent,
                            KPKP
                    )) {
                continue;
            }

            int successorCount =
                    FourPieceTierTwoKpkpPrimitiveMoveGenerator
                            .generateLegalSuccessors(
                                    parent,
                                    KPKP,
                                    successors
                            );

            for (int i = 0; i < successorCount; i++) {

                if (successors.boundaryType(i)
                        != FourPieceTierTwoKpkpPrimitiveMoveGenerator
                        .BOUNDARY_NONE) {

                    boundaryEdges++;
                    continue;
                }

                sameClassEdges++;

                FourPieceTierTwoKpkpPrimitiveState.State child =
                        successors.state(i);

                if (child.enPassantAvailable()) {
                    epSameClassEdges++;
                }

                int predecessorCount =
                        FourPieceTierTwoKpkpPrimitivePredecessorGenerator
                                .generatePredecessors(
                                        child,
                                        KPKP,
                                        predecessors,
                                        validationForward
                                );

                if (!contains(
                        predecessors,
                        predecessorCount,
                        parent
                )) {

                    throw new IllegalStateException(
                            "KP-KP predecessor generator missed legal same-class edge."
                                    + "\nparent="
                                    + describe(parent)
                                    + "\nchild="
                                    + describe(child)
                    );
                }

                recoveredEdges++;
            }

            legalParents++;
        }

        int legalChildren =
                0;

        long childAttempts =
                0;

        long epChildren =
                0;

        long predecessorsChecked =
                0;

        long duplicatePredecessors =
                0;

        while (legalChildren < childSamples) {

            childAttempts++;

            FourPieceTierTwoKpkpPrimitiveState.State child =
                    randomState(random);

            if (!FourPieceTierTwoKpkpPrimitiveMoveGenerator
                    .isStructurallyLegal(
                            child,
                            KPKP
                    )) {
                continue;
            }

            if (child.enPassantAvailable()) {
                epChildren++;
            }

            int predecessorCount =
                    FourPieceTierTwoKpkpPrimitivePredecessorGenerator
                            .generatePredecessors(
                                    child,
                                    KPKP,
                                    predecessors,
                                    validationForward
                            );

            Set<FourPieceTierTwoKpkpPrimitiveState.State> unique =
                    new HashSet<>(
                            Math.max(
                                    16,
                                    predecessorCount * 2
                            )
                    );

            for (int i = 0; i < predecessorCount; i++) {

                FourPieceTierTwoKpkpPrimitiveState.State parent =
                        predecessors.state(i);

                if (!unique.add(parent)) {
                    duplicatePredecessors++;
                }

                int forwardCount =
                        FourPieceTierTwoKpkpPrimitiveMoveGenerator
                                .generateLegalSuccessors(
                                        parent,
                                        KPKP,
                                        validationForward
                                );

                if (!containsSameClassChild(
                        validationForward,
                        forwardCount,
                        child
                )) {

                    throw new IllegalStateException(
                            "KP-KP predecessor generator returned unsound parent."
                                    + "\nparent="
                                    + describe(parent)
                                    + "\nchild="
                                    + describe(child)
                    );
                }

                predecessorsChecked++;
            }

            legalChildren++;
        }

        if (duplicatePredecessors != 0) {
            throw new IllegalStateException(
                    "Duplicate KP-KP predecessors observed: "
                            + duplicatePredecessors
            );
        }

        if (sameClassEdges != recoveredEdges) {
            throw new IllegalStateException(
                    "KP-KP completeness accounting mismatch."
            );
        }

        System.out.println();
        System.out.println(
                "KP-KP randomized gate"
        );
        System.out.println(
                "---------------------"
        );
        System.out.println(
                "  legal parents checked: "
                        + legalParents
        );
        System.out.println(
                "  parent attempts: "
                        + parentAttempts
        );
        System.out.println(
                "  same-class edges checked: "
                        + sameClassEdges
        );
        System.out.println(
                "  EP-enabled same-class children: "
                        + epSameClassEdges
        );
        System.out.println(
                "  boundary edges skipped: "
                        + boundaryEdges
        );
        System.out.println(
                "  recovered same-class edges: "
                        + recoveredEdges
        );
        System.out.println(
                "  legal children checked: "
                        + legalChildren
        );
        System.out.println(
                "  child attempts: "
                        + childAttempts
        );
        System.out.println(
                "  sampled EP-enabled children: "
                        + epChildren
        );
        System.out.println(
                "  predecessors soundness-checked: "
                        + predecessorsChecked
        );
        System.out.println(
                "  duplicate predecessors: "
                        + duplicatePredecessors
        );
        System.out.println(
                "  completeness + soundness: PASSED"
        );
    }

    /**
     * Random exact state sampler.
     *
     * EP=true is deliberately sampled aggressively (50% whenever geometry can
     * carry it) so the sparse history domain receives substantial coverage.
     */
    private static FourPieceTierTwoKpkpPrimitiveState.State randomState(
            Random random
    ) {

        while (true) {

            int wk =
                    random.nextInt(64);

            int bk =
                    random.nextInt(64);

            int whitePawn =
                    (1 + random.nextInt(6)) * 8
                            + random.nextInt(8);

            int blackPawn =
                    (1 + random.nextInt(6)) * 8
                            + random.nextInt(8);

            if (!distinct(
                    wk,
                    bk,
                    whitePawn,
                    blackPawn
            )) {
                continue;
            }

            int base =
                    FourPieceGenericPrimitiveState.encode(
                            wk,
                            bk,
                            whitePawn,
                            blackPawn,
                            random.nextBoolean()
                    );

            boolean ep =
                    FourPieceTierTwoKpkpPrimitiveState
                            .canCarryEnPassant(base)
                            && random.nextBoolean();

            return FourPieceTierTwoKpkpPrimitiveState.of(
                    base,
                    ep
            );
        }
    }

    private static FourPieceTierTwoKpkpPrimitiveState.State findSameClassChild(
            FourPieceTierTwoKpkpPrimitiveMoveGenerator.Buffer buffer,
            String from,
            String to
    ) {

        int fromSquare =
                sq(from);

        int toSquare =
                sq(to);

        for (int i = 0; i < buffer.size(); i++) {

            if (buffer.boundaryType(i)
                    == FourPieceTierTwoKpkpPrimitiveMoveGenerator.BOUNDARY_NONE
                    && buffer.fromSquare(i) == fromSquare
                    && buffer.toSquare(i) == toSquare) {

                return buffer.state(i);
            }
        }

        return null;
    }

    private static boolean contains(
            FourPieceTierTwoKpkpPrimitivePredecessorGenerator.Buffer buffer,
            int count,
            FourPieceTierTwoKpkpPrimitiveState.State state
    ) {

        for (int i = 0; i < count; i++) {
            if (buffer.state(i).equals(state)) {
                return true;
            }
        }

        return false;
    }

    private static boolean containsSameClassChild(
            FourPieceTierTwoKpkpPrimitiveMoveGenerator.Buffer buffer,
            int count,
            FourPieceTierTwoKpkpPrimitiveState.State child
    ) {

        for (int i = 0; i < count; i++) {

            if (buffer.boundaryType(i)
                    == FourPieceTierTwoKpkpPrimitiveMoveGenerator.BOUNDARY_NONE
                    && child.equals(buffer.state(i))) {

                return true;
            }
        }

        return false;
    }

    private static FourPieceTierTwoKpkpPrimitiveState.State state(
            String wk,
            String bk,
            String whitePawn,
            String blackPawn,
            boolean blackToMove,
            boolean enPassantAvailable
    ) {

        return FourPieceTierTwoKpkpPrimitiveState.encode(
                sq(wk),
                sq(bk),
                sq(whitePawn),
                sq(blackPawn),
                blackToMove,
                enPassantAvailable
        );
    }

    private static String describe(
            FourPieceTierTwoKpkpPrimitiveState.State state
    ) {

        return "WK="
                + algebraic(
                FourPieceTierTwoKpkpPrimitiveState.whiteKing(state)
        )
                + " BK="
                + algebraic(
                FourPieceTierTwoKpkpPrimitiveState.blackKing(state)
        )
                + " WP="
                + algebraic(
                FourPieceTierTwoKpkpPrimitiveState.whitePawn(state)
        )
                + " BP="
                + algebraic(
                FourPieceTierTwoKpkpPrimitiveState.blackPawn(state)
        )
                + " stm="
                + (FourPieceTierTwoKpkpPrimitiveState.blackToMove(state)
                ? "BLACK"
                : "WHITE")
                + " ep="
                + state.enPassantAvailable()
                + " ["
                + state.baseState()
                + "]";
    }

    private static String algebraic(
            int square
    ) {

        return ""
                + (char) ('a' + (square & 7))
                + ((square >>> 3) + 1);
    }

    private static int sq(
            String algebraic
    ) {

        int file =
                algebraic.charAt(0) - 'a';

        int rank =
                algebraic.charAt(1) - '1';

        return rank * 8 + file;
    }

    private static boolean distinct(
            int a,
            int b,
            int c,
            int d
    ) {

        return a != b
                && a != c
                && a != d
                && b != c
                && b != d
                && c != d;
    }

    private static void require(
            boolean condition,
            String message
    ) {

        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}

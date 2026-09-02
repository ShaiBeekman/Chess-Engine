package main.java.chess.search;

import main.java.chess.evaluation.PositionEvaluator;
import main.java.chess.model.Color;
import main.java.chess.model.Move;
import main.java.chess.model.Position;
import main.java.chess.model.PositionKey;
import main.java.chess.model.GameState;
import main.java.chess.rules.GameStateEvaluator;
import main.java.chess.rules.MoveGenerator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * One persistent Dovetail line walker.
 *
 * A walker never owns a private chess tree. Every step is committed into the
 * shared canonical PositionGraph, so transpositions discovered by different
 * walkers (or by the node-coverage lane) collapse onto the same PositionNode.
 *
 * Move choice is deliberately biased rather than restrictive:
 *
 *  1. prefer edges this node's walkers have used less often,
 *  2. prefer edges with fewer total traversals,
 *  3. prefer less-visited already-known targets,
 *  4. strongly discourage returning to a position already on this walk.
 *
 * Repetition is still allowed when it is the best remaining choice. This is an
 * exploration bias, not an extra chess rule.
 */
public final class DovetailWalker {

    public static final String BUILD_ID =
            "M76-STRENGTH-AWARE-DOVETAIL-WALKERS-V1";

    public static final int MAX_HALF_MOVES = 10_000;

    /*
     * M76 deliberately preserves M69's exploration hierarchy. Evaluation
     * never allows a walker to skip a less-visited edge in favor of a more
     * familiar one. Strength is used only to bias the random choice among
     * candidates that are tied at the best exploration penalty.
     */
    private static final long PATH_REPEAT_PENALTY = 1_000_000_000L;
    private static final long WALKER_VISIT_PENALTY = 1_000_000L;
    private static final long TOTAL_VISIT_PENALTY = 10_000L;
    private static final long TARGET_VISIT_PENALTY = 10L;

    private static final int PROVEN_OUTCOME_SCORE =
            1_000_000;


    /**
     * Heterogeneous walkers give Dovetail both breadth and chess direction.
     *
     * A, E, I...   EXPLORER  — uniform random among equally novel moves
     * B, F, J...   GUIDED    — light evaluation bias
     * C, G, K...   STRONG    — substantial evaluation bias
     * D, H, L...   PRINCIPAL — strongest evaluation bias
     *
     * Every fourth walker is therefore still a pure explorer. The diagonal
     * schedule itself remains unchanged.
     */
    public enum BiasProfile {

        EXPLORER(0),
        GUIDED(1),
        STRONG(3),
        PRINCIPAL(6);

        private final int weightExponent;

        BiasProfile(
                int weightExponent
        ) {
            this.weightExponent =
                    weightExponent;
        }

        int weightExponent() {
            return weightExponent;
        }

        public static BiasProfile forWalkerIndex(
                int walkerIndex
        ) {
            if (walkerIndex < 0) {
                throw new IllegalArgumentException(
                        "Walker index cannot be negative."
                );
            }

            BiasProfile[] profiles =
                    values();

            return profiles[
                    walkerIndex % profiles.length
                    ];
        }
    }

    public enum Termination {
        ACTIVE,
        TERMINAL,
        NO_LEGAL_MOVE,
        DEPTH_LIMIT,
        COMMIT_FAILED
    }

    private final int index;
    private final String label;
    private final Random random;
    private final Set<PositionKey> pathKeys;
    private final PositionEvaluator positionEvaluator;
    private final BiasProfile biasProfile;

    private PositionNode currentNode;
    private int depth;
    private long pathRevisits;
    private Termination termination;

    public DovetailWalker(
            int index,
            PositionNode root,
            long seed
    ) {
        if (index < 0) {
            throw new IllegalArgumentException("Walker index cannot be negative.");
        }
        if (root == null) {
            throw new IllegalArgumentException("Walker root cannot be null.");
        }

        this.index = index;
        this.label = labelFor(index);
        this.random = new Random(seed);
        this.pathKeys = new HashSet<>();

        this.positionEvaluator =
                new PositionEvaluator();

        this.biasProfile =
                BiasProfile.forWalkerIndex(
                        index
                );

        this.currentNode = root;
        this.depth = 0;
        this.pathRevisits = 0L;
        this.termination = Termination.ACTIVE;

        this.pathKeys.add(root.getKey());
    }

    public StepResult step(
            PositionGraph graph,
            MoveGenerator moveGenerator,
            GameStateEvaluator gameStateEvaluator
    ) {
        if (graph == null || moveGenerator == null || gameStateEvaluator == null) {
            throw new IllegalArgumentException("Walker dependencies cannot be null.");
        }

        if (isRetired()) {
            return StepResult.noProgress(currentNode, termination);
        }

        if (depth >= MAX_HALF_MOVES) {
            termination = Termination.DEPTH_LIMIT;
            return StepResult.noProgress(currentNode, termination);
        }

        GameState gameState = gameStateEvaluator.evaluate(currentNode.getPosition());
        if (isTerminal(gameState)) {
            termination = Termination.TERMINAL;
            return StepResult.noProgress(currentNode, termination);
        }

        List<Move> legalMoves = currentNode.getOrCacheLegalMoves(moveGenerator);
        if (legalMoves.isEmpty()) {
            termination = Termination.NO_LEGAL_MOVE;
            return StepResult.noProgress(currentNode, termination);
        }

        Candidate chosen = chooseCandidate(legalMoves);
        PositionNode from = currentNode;

        if (!graph.ensureManualContinuation(
                from.getPosition(),
                chosen.childPosition()
        )) {
            termination = Termination.COMMIT_FAILED;
            return StepResult.noProgress(from, termination);
        }

        /*
         * ensureManualContinuation owns graph-shape bookkeeping (unique edge,
         * reverse parent link, dirty queues). The walker owns only traversal
         * accounting.
         */
        from.incrementWalkerEdgeVisit(chosen.move());

        PositionNode to = graph.getOrCreateNode(chosen.childPosition());
        to.incrementVisitCount();

        boolean pathRevisit = pathKeys.contains(to.getKey());
        if (pathRevisit) {
            pathRevisits++;
        }

        pathKeys.add(to.getKey());
        currentNode = to;
        depth++;

        if (depth >= MAX_HALF_MOVES) {
            termination = Termination.DEPTH_LIMIT;
        } else {
            GameState childState = gameStateEvaluator.evaluate(to.getPosition());
            if (isTerminal(childState)) {
                termination = Termination.TERMINAL;
            }
        }

        return new StepResult(
                true,
                from,
                to,
                chosen.move(),
                pathRevisit,
                termination
        );
    }

    private Candidate chooseCandidate(
            List<Move> legalMoves
    ) {

        long bestExplorationPenalty =
                Long.MAX_VALUE;

        List<Candidate> explorationFrontier =
                new ArrayList<>();


        for (Move move :
                legalMoves) {

            Position childPosition =
                    currentNode
                            .getPosition()
                            .makeMove(
                                    move
                            );

            PositionKey childKey =
                    childPosition
                            .createPositionKey();


            int walkerVisits =
                    currentNode
                            .getWalkerEdgeVisitCount(
                                    move
                            );

            int totalVisits =
                    currentNode
                            .getEdgeVisitCount(
                                    move
                            );

            PositionNode existingTarget =
                    existingTarget(
                            currentNode,
                            move
                    );

            int targetVisits =
                    existingTarget == null
                            ? 0
                            : existingTarget
                            .getVisitCount();

            boolean pathRepeat =
                    pathKeys.contains(
                            childKey
                    );


            /*
             * This is the exact M69 exploration penalty. Keeping it intact is
             * important: M76 adds chess strength without converting Dovetail
             * into a best-first engine and without sacrificing its anti-repeat
             * / low-visit behavior.
             */
            long explorationPenalty =
                    0L;

            explorationPenalty +=
                    (long) walkerVisits
                            * WALKER_VISIT_PENALTY;

            explorationPenalty +=
                    (long) totalVisits
                            * TOTAL_VISIT_PENALTY;

            explorationPenalty +=
                    (long) targetVisits
                            * TARGET_VISIT_PENALTY;

            if (pathRepeat) {

                explorationPenalty +=
                        PATH_REPEAT_PENALTY;
            }


            int sideScore =
                    scoreForSideToMove(
                            childPosition,
                            existingTarget
                    );


            Candidate candidate =
                    new Candidate(
                            move,
                            childPosition,
                            explorationPenalty,
                            sideScore
                    );


            if (explorationPenalty
                    < bestExplorationPenalty) {

                bestExplorationPenalty =
                        explorationPenalty;

                explorationFrontier.clear();

                explorationFrontier.add(
                        candidate
                );

            } else if (explorationPenalty
                    == bestExplorationPenalty) {

                explorationFrontier.add(
                        candidate
                );
            }
        }


        if (explorationFrontier.isEmpty()) {

            throw new IllegalStateException(
                    "A legal-move set produced no Dovetail candidate."
            );
        }


        /*
         * M69 stopped here and chose uniformly from the tied frontier.
         *
         * M76 keeps EXPLORER walkers exactly uniform, while the other three
         * profiles apply increasingly strong rank-weighted random selection.
         * Every candidate keeps positive probability, so the walk remains
         * stochastic rather than becoming deterministic minimax.
         */
        return chooseStrengthBiased(
                explorationFrontier
        );
    }


    private Candidate chooseStrengthBiased(
            List<Candidate> candidates
    ) {

        if (candidates.size() == 1) {

            return candidates.get(
                    0
            );
        }


        List<Candidate> ranked =
                new ArrayList<>(
                        candidates
                );


        ranked.sort(
                (left, right) -> {

                    int scoreOrder =
                            Integer.compare(
                                    right.sideScore(),
                                    left.sideScore()
                            );

                    if (scoreOrder != 0) {

                        return scoreOrder;
                    }


                    /*
                     * Stable deterministic tie ordering keeps a fixed random
                     * seed reproducible while equal-score candidates still
                     * receive equal probability.
                     */
                    return left
                            .move()
                            .toString()
                            .compareTo(
                                    right
                                            .move()
                                            .toString()
                            );
                }
        );


        int exponent =
                biasProfile
                        .weightExponent();


        if (exponent == 0) {

            return ranked.get(
                    random.nextInt(
                            ranked.size()
                    )
            );
        }


        long[] weights =
                new long[
                        ranked.size()
                        ];

        long totalWeight =
                0L;


        int index =
                0;

        while (index
                < ranked.size()) {

            int groupEnd =
                    index + 1;

            int score =
                    ranked
                            .get(index)
                            .sideScore();


            while (groupEnd
                    < ranked.size()
                    &&
                    ranked
                            .get(groupEnd)
                            .sideScore()
                            == score) {

                groupEnd++;
            }


            /*
             * All candidates with the same score get exactly the same weight.
             * rankStrength is larger for better score groups.
             */
            int strictlyBetter =
                    index;

            long rankStrength =
                    ranked.size()
                            - strictlyBetter;

            long weight =
                    integerPower(
                            rankStrength,
                            exponent
                    );


            for (int candidateIndex = index;
                 candidateIndex < groupEnd;
                 candidateIndex++) {

                weights[
                        candidateIndex
                        ] =
                        weight;

                totalWeight +=
                        weight;
            }


            index =
                    groupEnd;
        }


        double ticket =
                random.nextDouble()
                        * totalWeight;

        long cumulative =
                0L;


        for (int candidateIndex = 0;
             candidateIndex < ranked.size();
             candidateIndex++) {

            cumulative +=
                    weights[
                            candidateIndex
                            ];


            if (ticket
                    < cumulative) {

                return ranked.get(
                        candidateIndex
                );
            }
        }


        /*
         * Floating-point rounding can theoretically leave ticket at the
         * extreme upper boundary. The final candidate is a safe deterministic
         * fallback.
         */
        return ranked.get(
                ranked.size() - 1
        );
    }


    private int scoreForSideToMove(
            Position childPosition,
            PositionNode existingTarget
    ) {

        Color sideToMove =
                currentNode
                        .getPosition()
                        .getSideToMove();


        if (existingTarget != null) {

            SearchOutcome outcome =
                    existingTarget
                            .getSearchOutcome();


            SearchOutcome sideWin =
                    sideToMove == Color.WHITE
                            ? SearchOutcome.WHITE_WIN
                            : SearchOutcome.BLACK_WIN;

            SearchOutcome opponentWin =
                    sideToMove == Color.WHITE
                            ? SearchOutcome.BLACK_WIN
                            : SearchOutcome.WHITE_WIN;


            if (outcome
                    == sideWin) {

                int mateDistance =
                        Math.max(
                                0,
                                existingTarget
                                        .getMateDistance()
                        );

                return PROVEN_OUTCOME_SCORE
                        - mateDistance;
            }


            if (outcome
                    == opponentWin) {

                int mateDistance =
                        Math.max(
                                0,
                                existingTarget
                                        .getMateDistance()
                        );

                /*
                 * If every tied exploration candidate loses, delaying mate is
                 * preferable, hence +mateDistance inside the losing region.
                 */
                return -PROVEN_OUTCOME_SCORE
                        + mateDistance;
            }


            if (outcome
                    == SearchOutcome.DRAW) {

                return 0;
            }
        }


        int whitePerspectiveValue =
                existingTarget == null
                        ? positionEvaluator.evaluate(
                        childPosition
                )
                        : existingTarget
                        .getSearchValue();


        /*
         * PositionGraph stores ordinary evaluations from White's perspective.
         * Dovetail walkers need "larger is better for the side choosing now."
         */
        return sideToMove == Color.WHITE
                ? whitePerspectiveValue
                : -whitePerspectiveValue;
    }


    private static long integerPower(
            long base,
            int exponent
    ) {

        if (exponent < 0) {

            throw new IllegalArgumentException(
                    "Exponent cannot be negative."
            );
        }


        long result =
                1L;


        for (int i = 0;
             i < exponent;
             i++) {

            result *=
                    base;
        }


        return result;
    }


    private static PositionNode existingTarget(
            PositionNode node,
            Move move
    ) {

        for (SearchEdge edge :
                node.getOutgoingEdges()) {

            if (edge
                    .getMove()
                    .equals(
                            move
                    )) {

                return edge.getTarget();
            }
        }


        return null;
    }


    private static boolean isTerminal(GameState state) {
        return switch (state) {
            case CHECKMATE,
                 STALEMATE,
                 DRAW_75_MOVE,
                 DRAW_FIVEFOLD_REPETITION -> true;
            case CHECK,
                 ONGOING -> false;
        };
    }

    public int getIndex() {
        return index;
    }

    public String getLabel() {
        return label;
    }

    public PositionNode getCurrentNode() {
        return currentNode;
    }

    public int getDepth() {
        return depth;
    }

    public long getPathRevisits() {
        return pathRevisits;
    }

    public boolean isRetired() {
        return termination != Termination.ACTIVE;
    }

    public BiasProfile getBiasProfile() {
        return biasProfile;
    }


    public Termination getTermination() {
        return termination;
    }

    public static String labelFor(int zeroBasedIndex) {
        if (zeroBasedIndex < 0) {
            throw new IllegalArgumentException("Walker index cannot be negative.");
        }

        int value = zeroBasedIndex + 1;
        StringBuilder result = new StringBuilder();

        while (value > 0) {
            value--;
            result.append((char) ('A' + (value % 26)));
            value /= 26;
        }

        return result.reverse().toString();
    }

    public record StepResult(
            boolean advanced,
            PositionNode from,
            PositionNode to,
            Move move,
            boolean pathRevisit,
            Termination termination
    ) {
        private static StepResult noProgress(
                PositionNode node,
                Termination termination
        ) {
            return new StepResult(
                    false,
                    node,
                    node,
                    null,
                    false,
                    termination
            );
        }
    }

    private record Candidate(
            Move move,
            Position childPosition,
            long explorationPenalty,
            int sideScore
    ) {
    }
}

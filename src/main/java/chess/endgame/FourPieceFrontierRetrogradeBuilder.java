package main.java.chess.endgame;

import main.java.chess.model.Board;
import main.java.chess.model.Color;
import main.java.chess.model.Move;
import main.java.chess.model.Piece;
import main.java.chess.model.PieceType;
import main.java.chess.model.Position;
import main.java.chess.model.PositionKey;
import main.java.chess.model.Square;
import main.java.chess.rules.MoveGenerator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Frontier-driven exact retrograde builder for KQRK.
 *
 * This replaces the repeated full-state sweep architecture.
 *
 * The builder performs one classification pass over the raw 4-piece state
 * space.  During that pass it records:
 *
 *     - terminal checkmates
 *     - stalemates
 *     - exact KQK/KRK boundary children
 *     - the number of unresolved in-class children for each state
 *
 * It then propagates only from newly solved KQRK states.  Predecessors are
 * generated on demand by reversing the previous mover's non-capturing move.
 *
 * This works especially cleanly for KQRK because every in-class KQRK -> KQRK
 * edge is non-capturing.  Captures leave the class and were already handled
 * during the initial boundary scan via the exact 3-piece tablebases.
 *
 * Retrograde semantics:
 *
 *     parent is WIN  if any child is LOSS
 *     parent is LOSS if every legal child is WIN
 *     otherwise unresolved states become DRAW at the fixpoint
 *
 * Exact mate distances:
 *
 *     WIN  = 1 + minimum LOSS-child distance
 *     LOSS = 1 + maximum WIN-child distance
 */
public final class FourPieceFrontierRetrogradeBuilder {

    private static final int PROGRESS_INTERVAL =
            1_000_000;


    private final FourPieceTablebase tablebase;
    private final MoveGenerator moveGenerator;


    public FourPieceFrontierRetrogradeBuilder(
            FourPieceTablebase tablebase
    ) {

        if (tablebase == null) {

            throw new IllegalArgumentException(
                    "Four-piece tablebase cannot be null."
            );
        }


        if (!"KQRK".equals(
                tablebase.materialName()
        )) {

            throw new IllegalArgumentException(
                    "Frontier builder currently supports only KQRK."
            );
        }


        this.tablebase =
                tablebase;

        this.moveGenerator =
                new MoveGenerator();
    }


    public BuildResult build() {

        long started =
                System.currentTimeMillis();


        int stateCount =
                tablebase.stateCount();


        byte[] outcome =
                new byte[
                        stateCount
                        ];


        short[] distance =
                new short[
                        stateCount
                        ];


        /*
         * remaining[state] counts legal children that have not yet been
         * proven WIN.  KQRK has comfortably fewer than 255 legal moves in
         * any state, so one unsigned byte is sufficient.
         */
        byte[] remaining =
                new byte[
                        stateCount
                        ];


        /*
         * Needed only while proving LOSS states: the defender chooses the
         * WIN child with the longest mate distance.
         */
        short[] maximumWinningChildDistance =
                new short[
                        stateCount
                        ];


        Arrays.fill(
                outcome,
                FourPieceTablebase.INVALID
        );

        Arrays.fill(
                distance,
                (short) -1
        );

        Arrays.fill(
                maximumWinningChildDistance,
                (short) -1
        );


        IntMinHeap frontier =
                new IntMinHeap(
                        1_000_000,
                        distance
                );


        int legalStates =
                0;

        int terminalMates =
                0;

        int terminalDraws =
                0;

        int boundaryWins =
                0;

        int boundaryLosses =
                0;


        System.out.println(
                "KQRK frontier retrograde: classifying "
                        + stateCount
                        + " raw states..."
        );


        // =====================================================
        // ONE FULL CLASSIFICATION PASS
        // =====================================================

        for (int state = 0;
             state < stateCount;
             state++) {

            if (state > 0
                    && state % PROGRESS_INTERVAL == 0) {

                System.out.println(
                        "  classified "
                                + state
                                + " / "
                                + stateCount
                                + " raw states"
                );
            }


            if (!tablebase.isLegalState(
                    state
            )) {

                continue;
            }


            legalStates++;


            Position position =
                    tablebase.positionForState(
                            state
                    );


            List<Move> legalMoves =
                    moveGenerator.generateLegalMoves(
                            position
                    );


            if (legalMoves.isEmpty()) {

                if (tablebase.isSideToMoveInCheckForBuild(
                        position
                )) {

                    solve(
                            state,
                            FourPieceTablebase.LOSS,
                            0,
                            outcome,
                            distance,
                            frontier
                    );

                    terminalMates++;

                } else {

                    outcome[state] =
                            FourPieceTablebase.DRAW;

                    distance[state] =
                            -1;

                    terminalDraws++;
                }


                continue;
            }


            outcome[state] =
                    FourPieceTablebase.UNKNOWN;


            int unresolvedInClass =
                    0;

            int knownWinChildren =
                    0;

            int knownDrawChildren =
                    0;

            int bestLossChildDistance =
                    Integer.MAX_VALUE;

            int maxWinChildDistance =
                    -1;


            for (Move move :
                    legalMoves) {

                Position child =
                        position.makeMove(
                                move
                        );


                int childState =
                        tablebase.encodePosition(
                                child
                        );


                if (childState >= 0) {

                    /*
                     * In-class child.  It will be accounted for by frontier
                     * propagation when/if that child becomes solved.
                     */
                    unresolvedInClass++;
                    continue;
                }


                FourPieceTablebase.Probe boundary =
                        tablebase.probeKnownThreePieceForBuild(
                                child
                        );


                switch (boundary.outcome()) {

                    case LOSS -> {

                        /*
                         * Parent can force this exact losing child
                         * immediately, therefore parent is already WIN.
                         */
                        bestLossChildDistance =
                                Math.min(
                                        bestLossChildDistance,
                                        boundary.mateDistance()
                                );
                    }

                    case WIN -> {

                        knownWinChildren++;

                        maxWinChildDistance =
                                Math.max(
                                        maxWinChildDistance,
                                        boundary.mateDistance()
                                );
                    }

                    case DRAW ->

                            knownDrawChildren++;

                    case UNSUPPORTED ->

                            throw new IllegalStateException(
                                    "KQRK produced an unsupported out-of-class child: "
                                            + move
                            );
                }
            }


            if (bestLossChildDistance
                    != Integer.MAX_VALUE) {

                solve(
                        state,
                        FourPieceTablebase.WIN,
                        bestLossChildDistance + 1,
                        outcome,
                        distance,
                        frontier
                );

                boundaryWins++;
                continue;
            }


            /*
             * Only in-class children can still change from unknown.
             * Exact boundary WIN children are already proven and therefore
             * removed from the remaining-child counter now.
             */
            int remainingCount =
                    unresolvedInClass
                            + knownDrawChildren;


            /*
             * Draw children can never become WIN, so a parent with an exact
             * draw child can never later become LOSS.  We encode them into
             * remaining so the counter cannot fall to zero.
             */
            if (remainingCount > 255) {

                throw new IllegalStateException(
                        "KQRK legal-move count exceeded unsigned-byte storage."
                );
            }


            remaining[state] =
                    (byte) remainingCount;


            maximumWinningChildDistance[state] =
                    safeShortOrMinusOne(
                            maxWinChildDistance
                    );


            /*
             * If every legal child was already an exact boundary WIN, then
             * this parent is immediately LOSS.
             */
            if (unresolvedInClass == 0
                    && knownDrawChildren == 0
                    && knownWinChildren == legalMoves.size()) {

                solve(
                        state,
                        FourPieceTablebase.LOSS,
                        maxWinChildDistance + 1,
                        outcome,
                        distance,
                        frontier
                );

                boundaryLosses++;
            }
        }


        System.out.println(
                "Initial classification complete."
        );

        System.out.println(
                "Legal states: "
                        + legalStates
        );

        System.out.println(
                "Terminal mates: "
                        + terminalMates
        );

        System.out.println(
                "Terminal stalemates: "
                        + terminalDraws
        );

        System.out.println(
                "Boundary WIN seeds: "
                        + boundaryWins
        );

        System.out.println(
                "Boundary LOSS seeds: "
                        + boundaryLosses
        );

        System.out.println(
                "Initial frontier: "
                        + frontier.size()
        );


        // =====================================================
        // FRONTIER PROPAGATION
        // =====================================================

        long propagated =
                0;


        while (!frontier.isEmpty()) {

            int childState =
                    frontier.remove();


            byte childOutcome =
                    outcome[
                            childState
                            ];


            int childDistance =
                    distance[
                            childState
                            ];


            Position childPosition =
                    tablebase.positionForState(
                            childState
                    );


            List<Integer> predecessors =
                    generatePredecessors(
                            childPosition,
                            childState
                    );


            for (int parentState :
                    predecessors) {

                if (outcome[parentState]
                        != FourPieceTablebase.UNKNOWN) {

                    continue;
                }


                if (childOutcome
                        == FourPieceTablebase.LOSS) {

                    /*
                     * One losing child proves the parent WIN immediately.
                     * Because solved children are processed in increasing
                     * distance order by the bucketed queue contract below,
                     * the first LOSS child gives the minimum mate distance.
                     */
                    solve(
                            parentState,
                            FourPieceTablebase.WIN,
                            childDistance + 1,
                            outcome,
                            distance,
                            frontier
                    );


                } else if (childOutcome
                        == FourPieceTablebase.WIN) {

                    int left =
                            unsigned(
                                    remaining[
                                            parentState
                                            ]
                            );


                    if (left <= 0) {

                        /*
                         * A zero counter here means this state contained an
                         * exact DRAW boundary child.  Such a state can never
                         * become LOSS.
                         */
                        continue;
                    }


                    left--;


                    remaining[parentState] =
                            (byte) left;


                    int currentMax =
                            maximumWinningChildDistance[
                                    parentState
                                    ];


                    if (childDistance
                            > currentMax) {

                        maximumWinningChildDistance[
                                parentState
                                ] =
                                safeShort(
                                        childDistance
                                );
                    }


                    if (left == 0) {

                        int maxChild =
                                maximumWinningChildDistance[
                                        parentState
                                        ];


                        solve(
                                parentState,
                                FourPieceTablebase.LOSS,
                                maxChild + 1,
                                outcome,
                                distance,
                                frontier
                        );
                    }
                }
            }


            propagated++;


            if (propagated
                    % PROGRESS_INTERVAL == 0) {

                System.out.println(
                        "  propagated "
                                + propagated
                                + " solved states; frontier "
                                + frontier.size()
                );
            }
        }


        // =====================================================
        // FIXPOINT: REMAINING UNKNOWN = DRAW
        // =====================================================

        int wins =
                0;

        int losses =
                0;

        int draws =
                0;


        for (int state = 0;
             state < stateCount;
             state++) {

            if (outcome[state]
                    == FourPieceTablebase.UNKNOWN) {

                outcome[state] =
                        FourPieceTablebase.DRAW;

                distance[state] =
                        -1;
            }


            switch (outcome[state]) {

                case FourPieceTablebase.WIN ->
                        wins++;

                case FourPieceTablebase.LOSS ->
                        losses++;

                case FourPieceTablebase.DRAW ->
                        draws++;

                default -> {
                }
            }
        }


        tablebase.restoreFromPersistence(
                outcome,
                distance
        );


        long elapsed =
                System.currentTimeMillis()
                        - started;


        return new BuildResult(
                legalStates,
                wins,
                losses,
                draws,
                propagated,
                elapsed
        );
    }


    // =========================================================
    // PREDECESSOR GENERATION
    // =========================================================

    /**
     * Generates every legal in-class KQRK predecessor of childPosition.
     *
     * The previous mover is the opposite of the child's side to move.
     * Since captures leave KQRK, every in-class predecessor is obtained by
     * moving one of the previous mover's pieces backwards to an empty square.
     *
     * Every candidate is verified by making the forward move and comparing
     * the resulting encoded state to childState.  That final verification
     * keeps the reverse generator simple and lets the existing Position and
     * MoveGenerator rules remain the source of truth.
     */
    private List<Integer> generatePredecessors(
            Position childPosition,
            int childState
    ) {

        Color previousMover =
                childPosition.getSideToMove()
                        .opposite();


        Board board =
                childPosition.getBoard();


        List<Integer> predecessors =
                new ArrayList<>(
                        32
                );


        for (int rank = 0;
             rank < 8;
             rank++) {

            for (int file = 0;
                 file < 8;
                 file++) {

                Square destination =
                        new Square(
                                file,
                                rank
                        );


                Piece movedPiece =
                        board.getPiece(
                                destination
                        );


                if (movedPiece == null
                        || movedPiece.color()
                        != previousMover) {

                    continue;
                }


                switch (movedPiece.type()) {

                    case KING ->

                            addKingPredecessors(
                                    childPosition,
                                    childState,
                                    destination,
                                    movedPiece,
                                    predecessors
                            );

                    case ROOK ->

                            addSlidingPredecessors(
                                    childPosition,
                                    childState,
                                    destination,
                                    movedPiece,
                                    predecessors,
                                    false
                            );

                    case QUEEN ->

                            addSlidingPredecessors(
                                    childPosition,
                                    childState,
                                    destination,
                                    movedPiece,
                                    predecessors,
                                    true
                            );

                    default -> {
                    }
                }
            }
        }


        return predecessors;
    }


    private void addKingPredecessors(
            Position childPosition,
            int childState,
            Square destination,
            Piece movedPiece,
            List<Integer> predecessors
    ) {

        for (int df = -1;
             df <= 1;
             df++) {

            for (int dr = -1;
                 dr <= 1;
                 dr++) {

                if (df == 0
                        && dr == 0) {

                    continue;
                }


                int file =
                        destination.file()
                                + df;

                int rank =
                        destination.rank()
                                + dr;


                if (!inside(
                        file,
                        rank
                )) {

                    continue;
                }


                tryPredecessor(
                        childPosition,
                        childState,
                        new Square(
                                file,
                                rank
                        ),
                        destination,
                        movedPiece,
                        predecessors
                );
            }
        }
    }


    private void addSlidingPredecessors(
            Position childPosition,
            int childState,
            Square destination,
            Piece movedPiece,
            List<Integer> predecessors,
            boolean diagonals
    ) {

        int[][] directions =
                diagonals
                        ? QUEEN_DIRECTIONS
                        : ROOK_DIRECTIONS;


        for (int[] direction :
                directions) {

            int file =
                    destination.file()
                            + direction[0];

            int rank =
                    destination.rank()
                            + direction[1];


            while (inside(
                    file,
                    rank
            )) {

                Square origin =
                        new Square(
                                file,
                                rank
                        );


                if (childPosition.getBoard()
                        .getPiece(
                                origin
                        ) != null) {

                    break;
                }


                tryPredecessor(
                        childPosition,
                        childState,
                        origin,
                        destination,
                        movedPiece,
                        predecessors
                );


                file +=
                        direction[0];

                rank +=
                        direction[1];
            }
        }
    }


    private void tryPredecessor(
            Position childPosition,
            int childState,
            Square origin,
            Square destination,
            Piece movedPiece,
            List<Integer> predecessors
    ) {

        if (childPosition.getBoard()
                .getPiece(
                        origin
                ) != null) {

            return;
        }


        Board predecessorBoard =
                new Board(
                        childPosition.getBoard()
                );


        predecessorBoard.removePiece(
                destination
        );


        predecessorBoard.setPiece(
                origin,
                movedPiece
        );


        Color predecessorSide =
                childPosition.getSideToMove()
                        .opposite();


        Position temporary =
                new Position(
                        predecessorBoard,
                        predecessorSide,
                        false,
                        false,
                        false,
                        false,
                        null,
                        0,
                        1,
                        Map.of()
                );


        Map<PositionKey, Integer>
                repetitions =
                new HashMap<>();


        repetitions.put(
                temporary.createPositionKey(),
                1
        );


        Position predecessor =
                new Position(
                        predecessorBoard,
                        predecessorSide,
                        false,
                        false,
                        false,
                        false,
                        null,
                        0,
                        1,
                        repetitions
                );


        int predecessorState =
                tablebase.encodePosition(
                        predecessor
                );


        if (predecessorState < 0) {
            return;
        }


        /*
         * Ask the ordinary legal move generator whether the exact forward
         * move exists.  This automatically verifies king safety and all
         * normal legality constraints.
         */
        List<Move> legalMoves =
                moveGenerator.generateLegalMoves(
                        predecessor
                );


        for (Move move :
                legalMoves) {

            Position forward =
                    predecessor.makeMove(
                            move
                    );


            int forwardState =
                    tablebase.encodePosition(
                            forward
                    );


            if (forwardState
                    == childState) {

                predecessors.add(
                        predecessorState
                );

                return;
            }
        }
    }


    // =========================================================
    // SOLVING
    // =========================================================

    private void solve(
            int state,
            byte solvedOutcome,
            int solvedDistance,
            byte[] outcome,
            short[] distance,
            IntMinHeap frontier
    ) {

        if (outcome[state]
                == FourPieceTablebase.WIN
                ||
                outcome[state]
                        == FourPieceTablebase.LOSS) {

            return;
        }


        outcome[state] =
                solvedOutcome;


        distance[state] =
                safeShort(
                        solvedDistance
                );


        frontier.add(
                state
        );
    }


    private int unsigned(
            byte value
    ) {

        return value
                & 0xFF;
    }


    private short safeShort(
            int value
    ) {

        if (value < 0
                || value > Short.MAX_VALUE) {

            throw new IllegalStateException(
                    "Four-piece mate distance exceeds persistence range: "
                            + value
            );
        }


        return (short) value;
    }


    private short safeShortOrMinusOne(
            int value
    ) {

        if (value < 0) {
            return -1;
        }

        return safeShort(
                value
        );
    }


    private boolean inside(
            int file,
            int rank
    ) {

        return file >= 0
                && file < 8
                && rank >= 0
                && rank < 8;
    }


    private static final int[][] ROOK_DIRECTIONS = {
            {1, 0},
            {-1, 0},
            {0, 1},
            {0, -1}
    };


    private static final int[][] QUEEN_DIRECTIONS = {
            {1, 0},
            {-1, 0},
            {0, 1},
            {0, -1},
            {1, 1},
            {1, -1},
            {-1, 1},
            {-1, -1}
    };


    // =========================================================
    // RESULT
    // =========================================================

    public record BuildResult(
            int legalStates,
            int wins,
            int losses,
            int draws,
            long propagatedStates,
            long buildMillis
    ) {
    }


    // =========================================================
    // COMPACT DISTANCE-ORDERED FRONTIER
    // =========================================================

    /**
     * Primitive min-heap ordered by the already-stored mate distance.
     *
     * Distance ordering is not just a performance detail.  It guarantees
     * that when a WIN parent is first reached from a LOSS child, that child
     * has the minimum exact mate distance among all solved LOSS children.
     * Therefore the parent's stored WIN distance is exact without later
     * relaxation.
     */
    private static final class IntMinHeap {

        private int[] heap;

        private int size;

        private final short[] distance;


        private IntMinHeap(
                int initialCapacity,
                short[] distance
        ) {

            this.heap =
                    new int[
                            Math.max(
                                    16,
                                    initialCapacity
                            )
                            ];

            this.distance =
                    distance;
        }


        private void add(
                int state
        ) {

            ensureCapacity();


            int index =
                    size++;


            heap[index] =
                    state;


            siftUp(
                    index
            );
        }


        private int remove() {

            if (size == 0) {

                throw new IllegalStateException(
                        "Cannot remove from an empty retrograde frontier."
                );
            }


            int result =
                    heap[0];


            size--;


            if (size > 0) {

                heap[0] =
                        heap[size];


                siftDown(
                        0
                );
            }


            return result;
        }


        private boolean isEmpty() {

            return size == 0;
        }


        private int size() {

            return size;
        }


        private void siftUp(
                int index
        ) {

            while (index > 0) {

                int parent =
                        (index - 1)
                                >>> 1;


                if (!less(
                        heap[index],
                        heap[parent]
                )) {

                    break;
                }


                swap(
                        index,
                        parent
                );


                index =
                        parent;
            }
        }


        private void siftDown(
                int index
        ) {

            while (true) {

                int left =
                        index * 2
                                + 1;


                if (left >= size) {
                    return;
                }


                int right =
                        left + 1;


                int smallest =
                        left;


                if (right < size
                        && less(
                        heap[right],
                        heap[left]
                )) {

                    smallest =
                            right;
                }


                if (!less(
                        heap[smallest],
                        heap[index]
                )) {

                    return;
                }


                swap(
                        index,
                        smallest
                );


                index =
                        smallest;
            }
        }


        private boolean less(
                int firstState,
                int secondState
        ) {

            int firstDistance =
                    distance[
                            firstState
                            ];


            int secondDistance =
                    distance[
                            secondState
                            ];


            if (firstDistance
                    != secondDistance) {

                return firstDistance
                        < secondDistance;
            }


            return firstState
                    < secondState;
        }


        private void swap(
                int first,
                int second
        ) {

            int temp =
                    heap[first];


            heap[first] =
                    heap[second];


            heap[second] =
                    temp;
        }


        private void ensureCapacity() {

            if (size < heap.length) {
                return;
            }


            heap =
                    Arrays.copyOf(
                            heap,
                            heap.length * 2
                    );
        }
    }
}

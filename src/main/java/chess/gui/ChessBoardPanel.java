package main.java.chess.gui;

import main.java.chess.model.Board;
import main.java.chess.model.Color;
import main.java.chess.model.Move;
import main.java.chess.model.Piece;
import main.java.chess.model.PieceType;
import main.java.chess.model.Position;
import main.java.chess.model.PositionKey;
import main.java.chess.model.Square;
import main.java.chess.rules.AttackDetector;
import main.java.chess.rules.MoveGenerator;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ChessBoardPanel extends JPanel {

    private final List<Runnable> interactionCompletions = new ArrayList<>();

    /** EDT-only: keep initial analysis layout out of an active piece gesture. */
    boolean deferUntilPieceInteractionEnds(Runnable completion) {
        if (dragSourceSquare == null) return false;
        interactionCompletions.add(completion);
        return true;
    }


    private static final int BOARD_SIZE = 8;
    private static final int SQUARE_SIZE = 80;
    private static final int DRAG_THRESHOLD = 6;

    private static final java.awt.Color LIGHT_SQUARE =
            new java.awt.Color(238, 238, 210);

    private static final java.awt.Color DARK_SQUARE =
            new java.awt.Color(118, 150, 86);

    private static final java.awt.Color SELECTED_SQUARE =
            new java.awt.Color(246, 246, 105, 180);

    private static final java.awt.Color LEGAL_MOVE_HIGHLIGHT =
            new java.awt.Color(60, 60, 60, 110);

    private static final java.awt.Color SETUP_OVERLAY =
            new java.awt.Color(42, 107, 173, 45);

    /*
     * Painted by the board itself so the loading dim covers exactly the same
     * scaled square as the chess pieces, with no Swing-layout edge sliver.
     */
    private static final java.awt.Color LOADING_OVERLAY =
            new java.awt.Color(8, 13, 18, 150);

    /*
     * Actual playable position.
     */
    private Position position;

    /*
     * Temporary analysis-navigation position.
     */
    private Position previewPosition;

    private final MoveGenerator moveGenerator;
    private final AttackDetector attackDetector;

    private Square selectedSquare;
    private List<Move> selectedLegalMoves;

    /*
     * Promotion is chosen directly on the board.  When several legal
     * promotion moves share a destination, the move is held here until
     * the player clicks one of the four displayed pieces.
     */
    private List<Move> pendingPromotionMoves;
    private Square pendingPromotionSquare;
    private int hoveredPromotionChoice;

    private Consumer<Position> positionChangeListener;
    private Consumer<Position> previewCommitListener;
    private Runnable setupChangeListener;

    /*
     * Drag state. In ordinary play, dragging is just another way to
     * execute a legal move. Clicking remains fully supported.
     */
    private Square dragSourceSquare;
    private Piece draggedPiece;
    private int pressX;
    private int pressY;
    private int dragX;
    private int dragY;
    private boolean dragging;

    /*
     * Free board-editor state.
     */
    private boolean setupMode;
    private Board setupBoard;
    private Color setupSideToMove;

    /*
     * Setup-editor keyboard history. This is separate from ChessWindow's
     * played-game and analysis navigation histories.
     */
    private final List<SetupSnapshot> setupHistory;
    private int setupHistoryIndex;

    /* M68A: false = White at bottom, true = Black at bottom. */
    private boolean flipped;

    /* Exact-endgame tablebase loading visual state. */
    private boolean loadingDimmed;


    public ChessBoardPanel(
            Position position
    ) {

        if (position == null) {
            throw new IllegalArgumentException(
                    "Position cannot be null."
            );
        }

        this.position = position;
        this.previewPosition = null;
        this.flipped = false;
        this.loadingDimmed = false;

        this.moveGenerator =
                new MoveGenerator();

        this.attackDetector =
                new AttackDetector();

        this.selectedSquare = null;
        this.selectedLegalMoves =
                new ArrayList<>();

        this.pendingPromotionMoves =
                new ArrayList<>();

        this.pendingPromotionSquare =
                null;

        this.hoveredPromotionChoice =
                -1;

        this.dragSourceSquare = null;
        this.draggedPiece = null;
        this.dragging = false;

        this.setupMode = false;
        this.setupBoard = null;
        this.setupSideToMove =
                position.getSideToMove();

        this.setupHistory =
                new ArrayList<>();

        this.setupHistoryIndex =
                -1;

        setPreferredSize(
                new Dimension(
                        BOARD_SIZE * SQUARE_SIZE,
                        BOARD_SIZE * SQUARE_SIZE
                )
        );

        MouseAdapter mouseAdapter =
                new MouseAdapter() {

                    @Override
                    public void mousePressed(
                            MouseEvent event
                    ) {
                        handleMousePressed(
                                event
                        );
                    }

                    @Override
                    public void mouseDragged(
                            MouseEvent event
                    ) {
                        handleMouseDragged(
                                event
                        );
                    }

                    @Override
                    public void mouseReleased(
                            MouseEvent event
                    ) {
                        handleMouseReleased(
                                event
                        );
                    }


                    @Override
                    public void mouseMoved(
                            MouseEvent event
                    ) {
                        handleMouseMoved(
                                event
                        );
                    }
                };

        // This canvas handles chess gestures and shortcuts, not composed text.
        // Avoid starting the Windows input method on the first mouse gesture.
        enableInputMethods(false);

        addMouseListener(
                mouseAdapter
        );

        addMouseMotionListener(
                mouseAdapter
        );
    }


    public void setFlipped(boolean flipped) {
        this.flipped = flipped;
        clearSelection();
        cancelDrag();
        clearPendingPromotion();
        repaint();
    }

    public boolean isFlipped() {
        return flipped;
    }

    public void flipBoard() {
        setFlipped(!flipped);
    }

    private int displayFile(int modelFile) {
        return flipped ? 7 - modelFile : modelFile;
    }

    private int displayRank(int modelRank) {
        return flipped ? modelRank : 7 - modelRank;
    }

    // =========================================================
    // Real position
    // =========================================================

    public Position getPosition() {
        return position;
    }


    public void setPosition(
            Position position
    ) {

        if (position == null) {
            throw new IllegalArgumentException(
                    "Position cannot be null."
            );
        }

        boolean sameRealPosition =
                samePosition(
                        this.position,
                        position
                );

        boolean hadPreview =
                this.previewPosition != null;

        this.position =
                position;

        this.previewPosition =
                null;

        if (!sameRealPosition
                || hadPreview) {

            clearSelection();
        }

        cancelDrag();
        clearPendingPromotion();

        repaint();
    }


    // =========================================================
    // Analysis preview
    // =========================================================

    public void setPreviewPosition(
            Position previewPosition
    ) {

        if (setupMode) {
            return;
        }

        if (previewPosition == null) {
            clearPreview();
            return;
        }

        if (samePosition(
                this.previewPosition,
                previewPosition
        )) {
            return;
        }

        this.previewPosition =
                previewPosition;

        clearSelection();
        cancelDrag();
        repaint();
    }


    public void clearPreview() {

        if (previewPosition == null) {
            return;
        }

        previewPosition =
                null;

        clearSelection();
        cancelDrag();
        repaint();
    }


    public boolean isPreviewing() {
        return previewPosition != null;
    }


    public void setPreviewCommitListener(
            Consumer<Position> listener
    ) {
        this.previewCommitListener =
                listener;
    }


    private Position getDisplayedPosition() {

        if (previewPosition != null) {
            return previewPosition;
        }

        return position;
    }


    // =========================================================
    // Setup / board editor
    // =========================================================

    public boolean isSetupMode() {
        return setupMode;
    }


    public void beginSetupMode() {

        beginSetupMode(
                getDisplayedPosition()
        );
    }


    /**
     * Begin Setup from an explicit editor source without replacing the real
     * board underneath it. This lets Endgame -> Setup start from the standard
     * full board while Cancel can still restore the pre-Setup position.
     */
    public void beginSetupMode(
            Position source
    ) {

        if (source == null) {
            return;
        }

        setupBoard =
                new Board(
                        source.getBoard()
                );

        setupSideToMove =
                source.getSideToMove();

        setupMode =
                true;

        previewPosition =
                null;

        setupHistory.clear();
        setupHistoryIndex =
                -1;
        recordSetupSnapshot();

        clearSelection();
        cancelDrag();

        repaint();
    }


    public void cancelSetupMode() {

        setupMode =
                false;

        setupBoard =
                null;

        setupHistory.clear();
        setupHistoryIndex =
                -1;

        clearSelection();
        cancelDrag();

        repaint();
    }


    public void setSetupSideToMove(
            Color color
    ) {

        if (color == null) {
            return;
        }

        if (setupSideToMove
                == color) {

            return;
        }

        setupSideToMove =
                color;

        if (setupMode) {
            recordSetupSnapshot();
        }

        repaint();
        fireSetupChanged();
    }


    public Color getSetupSideToMove() {
        return setupSideToMove;
    }


    public void clearSetupBoard() {

        if (!setupMode) {
            return;
        }

        setupBoard =
                new Board();

        recordSetupSnapshot();

        clearSelection();
        cancelDrag();

        repaint();
        fireSetupChanged();
    }


    public void placeSetupPiece(
            Piece piece,
            Square square
    ) {

        if (!setupMode
                || setupBoard == null
                || piece == null
                || square == null) {
            return;
        }

        setupBoard.setPiece(
                square,
                piece
        );

        recordSetupSnapshot();

        repaint();
        fireSetupChanged();
    }


    public void removeSetupPiece(
            Square square
    ) {

        if (!setupMode
                || setupBoard == null
                || square == null) {
            return;
        }

        setupBoard.removePiece(
                square
        );

        recordSetupSnapshot();

        repaint();
        fireSetupChanged();
    }


    public boolean undoSetupEdit() {

        return applySetupHistoryIndex(
                setupHistoryIndex - 1
        );
    }


    public boolean redoSetupEdit() {

        return applySetupHistoryIndex(
                setupHistoryIndex + 1
        );
    }


    public boolean resetSetupEditsToStart() {

        return applySetupHistoryIndex(
                0
        );
    }


    public boolean restoreLatestSetupEdit() {

        return applySetupHistoryIndex(
                setupHistory.size() - 1
        );
    }


    private void recordSetupSnapshot() {

        if (!setupMode
                || setupBoard == null) {

            return;
        }

        while (setupHistory.size()
                > setupHistoryIndex + 1) {

            setupHistory.remove(
                    setupHistory.size() - 1
            );
        }

        setupHistory.add(
                new SetupSnapshot(
                        setupBoard,
                        setupSideToMove
                )
        );

        setupHistoryIndex =
                setupHistory.size() - 1;
    }


    private boolean applySetupHistoryIndex(
            int targetIndex
    ) {

        if (!setupMode
                || setupBoard == null
                || targetIndex < 0
                || targetIndex >= setupHistory.size()
                || targetIndex == setupHistoryIndex) {

            return false;
        }

        SetupSnapshot snapshot =
                setupHistory.get(
                        targetIndex
                );

        setupBoard =
                new Board(
                        snapshot.board
                );

        setupSideToMove =
                snapshot.sideToMove;

        setupHistoryIndex =
                targetIndex;

        clearSelection();
        cancelDrag();
        repaint();
        fireSetupChanged();

        return true;
    }


    public Position finishSetupMode() {

        if (!setupMode
                || setupBoard == null) {

            throw new IllegalStateException(
                    "Board is not in setup mode."
            );
        }

        validateSetupBoard();

        Board committedBoard =
                new Board(
                        setupBoard
                );

        /*
         * Manually constructed positions begin with no castling or
         * en-passant assumptions. The position begins a fresh history.
         */
        Position temporary =
                new Position(
                        committedBoard,
                        setupSideToMove,
                        false,
                        false,
                        false,
                        false,
                        null,
                        0,
                        1,
                        Map.of()
                );

        validateSetupPositionLegality(
                temporary
        );

        Map<PositionKey, Integer>
                repetitionCounts =
                new HashMap<>();

        repetitionCounts.put(
                temporary.createPositionKey(),
                1
        );

        Position committed =
                new Position(
                        committedBoard,
                        setupSideToMove,
                        false,
                        false,
                        false,
                        false,
                        null,
                        0,
                        1,
                        repetitionCounts
                );

        setupMode =
                false;

        setupBoard =
                null;

        setupHistory.clear();
        setupHistoryIndex =
                -1;

        clearSelection();
        cancelDrag();

        return committed;
    }


    private void validateSetupBoard() {

        int whiteKings = 0;
        int blackKings = 0;

        for (int rank = 0;
             rank < BOARD_SIZE;
             rank++) {

            for (int file = 0;
                 file < BOARD_SIZE;
                 file++) {

                Piece piece =
                        setupBoard.getPiece(
                                new Square(
                                        file,
                                        rank
                                )
                        );

                if (piece == null
                        || piece.type()
                        != PieceType.KING) {
                    continue;
                }

                if (piece.color()
                        == Color.WHITE) {

                    whiteKings++;

                } else {

                    blackKings++;
                }
            }
        }

        if (whiteKings != 1
                || blackKings != 1) {

            throw new IllegalArgumentException(
                    "A setup position must contain exactly one white king "
                            + "and exactly one black king."
            );
        }

        /*
         * Pawns on the first/eighth rank are not legal chess positions and
         * would make downstream move generation/promotion semantics ambiguous.
         */
        for (int file = 0; file < BOARD_SIZE; file++) {

            Piece firstRank =
                    setupBoard.getPiece(
                            new Square(
                                    file,
                                    0
                            )
                    );

            Piece eighthRank =
                    setupBoard.getPiece(
                            new Square(
                                    file,
                                    7
                            )
                    );

            if ((firstRank != null
                    && firstRank.type() == PieceType.PAWN)
                    || (eighthRank != null
                    && eighthRank.type() == PieceType.PAWN)) {

                throw new IllegalArgumentException(
                        "A pawn cannot be placed on rank 1 or rank 8."
                );
            }
        }
    }


    /**
     * Setup accepts arbitrary legal positions, not arbitrary impossible board
     * diagrams. The side that moved previously may not have left its own king
     * in check. Without this guard, a position such as White-to-move with the
     * black king already attacked can let the search walk into a king-capture
     * state and later fail with "King not found".
     */
    private void validateSetupPositionLegality(
            Position position
    ) {

        Color previousMover =
                position.getSideToMove()
                        .opposite();

        Square previousKing =
                findKing(
                        position.getBoard(),
                        previousMover
                );

        if (previousKing == null) {

            throw new IllegalArgumentException(
                    "A setup position must contain both kings."
            );
        }

        if (attackDetector.isSquareAttacked(
                position.getBoard(),
                previousKing,
                position.getSideToMove()
        )) {

            throw new IllegalArgumentException(
                    "Illegal setup position: "
                            + displayColor(previousMover)
                            + " king is already in check while "
                            + displayColor(position.getSideToMove())
                            + " is to move. Change the side to move or reposition the pieces."
            );
        }
    }


    private Square findKing(
            Board board,
            Color color
    ) {

        for (int rank = 0; rank < BOARD_SIZE; rank++) {
            for (int file = 0; file < BOARD_SIZE; file++) {

                Square square =
                        new Square(
                                file,
                                rank
                        );

                Piece piece =
                        board.getPiece(
                                square
                        );

                if (piece != null
                        && piece.color() == color
                        && piece.type() == PieceType.KING) {

                    return square;
                }
            }
        }

        return null;
    }


    private String displayColor(
            Color color
    ) {

        return color == Color.WHITE
                ? "White"
                : "Black";
    }


    /*
     * Used by PiecePalettePanel to convert a drop point into a square.
     */
    public Square getSquareAtPoint(
            Point point
    ) {

        if (point == null) {
            return null;
        }

        return getSquareFromMouse(
                point.x,
                point.y
        );
    }


    public Board getSetupBoardSnapshot() {

        if (!setupMode
                || setupBoard == null) {

            return null;
        }

        return new Board(
                setupBoard
        );
    }


    public void setSetupChangeListener(
            Runnable listener
    ) {

        setupChangeListener =
                listener;
    }


    private void fireSetupChanged() {

        if (setupChangeListener != null) {
            setupChangeListener.run();
        }
    }


    private static final class SetupSnapshot {

        private final Board board;
        private final Color sideToMove;


        private SetupSnapshot(
                Board board,
                Color sideToMove
        ) {

            this.board =
                    new Board(
                            board
                    );

            this.sideToMove =
                    sideToMove;
        }
    }


    // =========================================================
    // Position-change listener
    // =========================================================

    public void setPositionChangeListener(
            Consumer<Position> listener
    ) {
        this.positionChangeListener =
                listener;
    }


    // =========================================================
    // Mouse interaction
    // =========================================================

    private void handleMouseMoved(
            MouseEvent event
    ) {

        if (!hasPendingPromotion()) {

            if (hoveredPromotionChoice != -1) {

                hoveredPromotionChoice =
                        -1;

                repaint();
            }

            return;
        }


        int newHover =
                getPromotionChoiceIndex(
                        event.getX(),
                        event.getY()
                );


        if (newHover
                != hoveredPromotionChoice) {

            hoveredPromotionChoice =
                    newHover;

            repaint();
        }
    }


    private void handleMousePressed(
            MouseEvent event
    ) {

        if (hasPendingPromotion()) {

            handlePromotionClick(
                    event
            );

            return;
        }

        Square clickedSquare =
                getSquareFromMouse(
                        event.getX(),
                        event.getY()
                );

        if (clickedSquare == null) {
            return;
        }

        Piece clickedPiece =
                getDisplayedBoard()
                        .getPiece(
                                clickedSquare
                        );

        pressX =
                event.getX();

        pressY =
                event.getY();

        dragX =
                pressX;

        dragY =
                pressY;

        dragging =
                false;

        dragSourceSquare =
                null;

        draggedPiece =
                null;

        /*
         * In setup mode every piece is draggable, regardless of side.
         */
        if (setupMode) {

            if (clickedPiece != null) {

                dragSourceSquare =
                        clickedSquare;

                draggedPiece =
                        clickedPiece;
            }

            return;
        }

        Position interactionPosition =
                getDisplayedPosition();

        if (interactionPosition == null) {
            return;
        }

        /*
         * A friendly piece can become a drag source. We also preserve
         * the existing click-to-select interaction.
         */
        if (clickedPiece != null
                && clickedPiece.color()
                == interactionPosition.getSideToMove()) {

            dragSourceSquare =
                    clickedSquare;

            draggedPiece =
                    clickedPiece;
        }

        handleClickSelection(
                clickedSquare,
                clickedPiece,
                interactionPosition
        );
    }


    private void handleMouseDragged(
            MouseEvent event
    ) {

        if (hasPendingPromotion()) {
            return;
        }

        if (dragSourceSquare == null
                || draggedPiece == null) {
            return;
        }

        int dx =
                event.getX() - pressX;

        int dy =
                event.getY() - pressY;

        if (!dragging
                && dx * dx + dy * dy
                >= DRAG_THRESHOLD * DRAG_THRESHOLD) {

            dragging =
                    true;

            if (!setupMode) {

                selectedSquare =
                        dragSourceSquare;

                selectedLegalMoves =
                        getLegalMovesFromSquare(
                                dragSourceSquare
                        );
            }
        }

        if (!dragging) {
            return;
        }

        dragX =
                event.getX();

        dragY =
                event.getY();

        repaint();
    }


    private void handleMouseReleased(
            MouseEvent event
    ) {

        if (hasPendingPromotion()) {
            return;
        }

        if (!dragging
                || dragSourceSquare == null
                || draggedPiece == null) {

            cancelDrag();
            return;
        }

        Square destination =
                getSquareFromMouse(
                        event.getX(),
                        event.getY()
                );

        if (setupMode) {

            /*
             * Dropping off the board removes the piece.
             */
            setupBoard.removePiece(
                    dragSourceSquare
            );

            if (destination != null) {

                setupBoard.setPiece(
                        destination,
                        draggedPiece
                );
            }

            recordSetupSnapshot();

            clearSelection();
            cancelDrag();
            repaint();
            fireSetupChanged();
            return;
        }

        if (destination != null) {

            Move move =
                    findMoveFromTo(
                            dragSourceSquare,
                            destination
                    );

            if (move != null) {

                cancelDrag();

                executeMove(
                        move
                );

                return;
            }
        }

        /*
         * Illegal drop: snap the piece back by doing nothing to the
         * immutable Position.
         */
        clearSelection();
        cancelDrag();
        repaint();
    }


    private void handleClickSelection(
            Square clickedSquare,
            Piece clickedPiece,
            Position interactionPosition
    ) {

        if (selectedSquare == null) {

            if (clickedPiece == null) {
                return;
            }

            if (clickedPiece.color()
                    != interactionPosition.getSideToMove()) {

                return;
            }

            selectSquare(
                    clickedSquare
            );

            return;
        }

        if (selectedSquare.equals(
                clickedSquare
        )) {

            clearSelection();
            repaint();
            return;
        }

        if (clickedPiece != null
                && clickedPiece.color()
                == interactionPosition.getSideToMove()) {

            selectSquare(
                    clickedSquare
            );

            return;
        }

        Move selectedMove =
                findMoveTo(
                        clickedSquare
                );

        if (selectedMove != null) {

            executeMove(
                    selectedMove
            );

            return;
        }

        clearSelection();
        repaint();
    }


    // =========================================================
    // Execute real game move
    // =========================================================

    private void executeMove(
            Move move
    ) {

        Position moveBase =
                getDisplayedPosition();

        if (moveBase == null) {
            return;
        }

        boolean movingFromPreview =
                previewPosition != null;

        if (movingFromPreview
                && previewCommitListener != null) {

            previewCommitListener.accept(
                    moveBase
            );
        }

        position =
                moveBase.makeMove(
                        move
                );

        previewPosition =
                null;

        clearSelection();
        cancelDrag();
        repaint();

        if (positionChangeListener != null) {

            positionChangeListener.accept(
                    position
            );
        }
    }


    // =========================================================
    // Selection / legal moves
    // =========================================================

    private void selectSquare(
            Square square
    ) {

        selectedSquare =
                square;

        selectedLegalMoves =
                getLegalMovesFromSquare(
                        square
                );

        repaint();
    }


    private void clearSelection() {

        selectedSquare =
                null;

        selectedLegalMoves =
                new ArrayList<>();
    }


    private List<Move> getLegalMovesFromSquare(
            Square square
    ) {

        List<Move> result =
                new ArrayList<>();

        Position interactionPosition =
                getDisplayedPosition();

        if (interactionPosition == null
                || setupMode) {
            return result;
        }

        for (Move move :
                moveGenerator.generateLegalMoves(
                        interactionPosition
                )) {

            if (move.from().equals(
                    square
            )) {

                result.add(
                        move
                );
            }
        }

        return result;
    }


    private Move findMoveTo(
            Square destination
    ) {

        List<Move> destinationMoves =
                new ArrayList<>();


        for (Move move :
                selectedLegalMoves) {

            if (move.to().equals(
                    destination
            )) {

                destinationMoves.add(
                        move
                );
            }
        }


        if (destinationMoves.isEmpty()) {
            return null;
        }


        if (destinationMoves.size() == 1
                || !destinationMoves.get(0)
                .isPromotion()) {

            return destinationMoves.get(0);
        }


        /*
         * Do not execute a promotion yet.  Hold all legal promotion
         * variants and let the board overlay collect the player's choice.
         */
        pendingPromotionMoves =
                new ArrayList<>(
                        destinationMoves
                );

        pendingPromotionSquare =
                destination;

        clearSelection();
        cancelDrag();
        repaint();

        return null;
    }


    private Move findMoveFromTo(
            Square source,
            Square destination
    ) {

        selectedLegalMoves =
                getLegalMovesFromSquare(
                        source
                );

        return findMoveTo(
                destination
        );
    }


    // =========================================================
    // In-board promotion picker
    // =========================================================

    private boolean hasPendingPromotion() {

        return pendingPromotionSquare != null
                && pendingPromotionMoves != null
                && !pendingPromotionMoves.isEmpty();
    }


    private void clearPendingPromotion() {

        pendingPromotionSquare =
                null;

        hoveredPromotionChoice =
                -1;

        if (pendingPromotionMoves == null) {

            pendingPromotionMoves =
                    new ArrayList<>();

        } else {

            pendingPromotionMoves.clear();
        }
    }


    private void handlePromotionClick(
            MouseEvent event
    ) {

        if (!hasPendingPromotion()) {
            return;
        }


        int index =
                getPromotionChoiceIndex(
                        event.getX(),
                        event.getY()
                );


        if (index < 0
                || index >= 4) {

            /*
             * Keep the picker open until one of its pieces is chosen.
             */
            return;
        }


        PieceType chosenType =
                switch (index) {
                    case 0 -> PieceType.QUEEN;
                    case 1 -> PieceType.ROOK;
                    case 2 -> PieceType.BISHOP;
                    case 3 -> PieceType.KNIGHT;
                    default -> throw new IllegalStateException();
                };


        Move chosenMove =
                null;


        for (Move move :
                pendingPromotionMoves) {

            if (move.isPromotion()
                    && move.promotion()
                    == chosenType) {

                chosenMove =
                        move;

                break;
            }
        }


        if (chosenMove == null) {
            return;
        }


        clearPendingPromotion();

        executeMove(
                chosenMove
        );
    }


    /*
     * The four choices are displayed as a vertical strip on the promotion
     * file, extending inward from the final rank.  This is the familiar
     * chess-site interaction: Q, R, B, N appear directly on the board.
     */
    private int getPromotionChoiceIndex(
            int mouseX,
            int mouseY
    ) {

        if (!hasPendingPromotion()) {
            return -1;
        }


        int file =
                displayFile(pendingPromotionSquare.file());

        int destinationDisplayRank =
                displayRank(pendingPromotionSquare.rank());


        double squareSize = SQUARE_SIZE * getRenderScale();
        if (mouseX < 0 || mouseY < 0 || squareSize <= 0) return -1;
        int clickedFile = (int) (mouseX / squareSize);
        int clickedDisplayRank = (int) (mouseY / squareSize);


        if (clickedFile != file) {
            return -1;
        }


        boolean promotingToTop =
                destinationDisplayRank == 0;


        int index =
                promotingToTop
                        ? clickedDisplayRank
                        : 7 - clickedDisplayRank;


        return index >= 0 && index < 4
                ? index
                : -1;
    }


    private void drawPromotionPicker(
            Graphics2D g2
    ) {

        if (!hasPendingPromotion()) {
            return;
        }


        Position displayed =
                getDisplayedPosition();

        if (displayed == null) {
            return;
        }


        /*
         * The moving pawn is still on its source square because the move
         * has not been committed yet.  Its color determines which glyphs
         * belong in the picker.
         */
        Color promotionColor =
                displayed.getSideToMove();


        PieceType[] choices = {
                PieceType.QUEEN,
                PieceType.ROOK,
                PieceType.BISHOP,
                PieceType.KNIGHT
        };


        int file =
                displayFile(pendingPromotionSquare.file());

        int destinationDisplayRank =
                displayRank(pendingPromotionSquare.rank());

        boolean promotingToTop =
                destinationDisplayRank == 0;


        Font font =
                new Font(
                        Font.SERIF,
                        Font.PLAIN,
                        58
                );

        g2.setFont(
                font
        );

        FontMetrics metrics =
                g2.getFontMetrics();


        for (int index = 0;
             index < choices.length;
             index++) {

            int displayRank =
                    promotingToTop
                            ? index
                            : 7 - index;

            int x =
                    file * SQUARE_SIZE;

            int y =
                    displayRank * SQUARE_SIZE;


            /*
             * Keep the selector integrated with the board.  The tile under
             * the pointer brightens and receives a stronger outline so the
             * player always knows which promotion will be chosen.
             */
            boolean hovered =
                    index
                            == hoveredPromotionChoice;


            g2.setColor(
                    hovered
                            ? new java.awt.Color(
                            255,
                            255,
                            255,
                            252
                    )
                            : new java.awt.Color(
                            245,
                            245,
                            245,
                            245
                    )
            );

            g2.fillRect(
                    x,
                    y,
                    SQUARE_SIZE,
                    SQUARE_SIZE
            );


            g2.setColor(
                    hovered
                            ? new java.awt.Color(
                            45,
                            45,
                            45,
                            220
                    )
                            : new java.awt.Color(
                            80,
                            80,
                            80,
                            150
                    )
            );

            g2.setStroke(
                    new BasicStroke(
                            hovered
                                    ? 3.0f
                                    : 1.0f
                    )
            );

            g2.drawRect(
                    x + (hovered ? 1 : 0),
                    y + (hovered ? 1 : 0),
                    SQUARE_SIZE - (hovered ? 3 : 1),
                    SQUARE_SIZE - (hovered ? 3 : 1)
            );


            Piece piece =
                    new Piece(
                            choices[index],
                            promotionColor
                    );


            drawPieceCentered(
                    g2,
                    piece,
                    x + SQUARE_SIZE / 2,
                    y + SQUARE_SIZE / 2,
                    metrics
            );
        }
    }


    // =========================================================
    // Drag helpers
    // =========================================================

    private void cancelDrag() {

        dragSourceSquare =
                null;

        draggedPiece =
                null;

        dragging =
                false;
        if (!interactionCompletions.isEmpty()) {
            List<Runnable> ready = new ArrayList<>(interactionCompletions);
            interactionCompletions.clear();
            // The release handler must finish committing the board/history first.
            for (Runnable completion : ready) {
                javax.swing.SwingUtilities.invokeLater(completion);
            }
        }

    }


    private Board getDisplayedBoard() {

        if (setupMode
                && setupBoard != null) {

            return setupBoard;
        }

        Position displayed =
                getDisplayedPosition();

        if (displayed == null) {

            return new Board();
        }

        return displayed.getBoard();
    }


    // =========================================================
    // Position identity
    // =========================================================

    private boolean samePosition(
            Position first,
            Position second
    ) {

        if (first == second) {
            return true;
        }

        if (first == null
                || second == null) {
            return false;
        }

        return first
                .createPositionKey()
                .equals(
                        second.createPositionKey()
                );
    }


    // =========================================================
    // Mouse coordinate conversion
    // =========================================================

    private Square getSquareFromMouse(
            int mouseX,
            int mouseY
    ) {

        double squareSize = SQUARE_SIZE * getRenderScale();
        if (mouseX < 0 || mouseY < 0 || squareSize <= 0) return null;

        int displayFile =
                (int) (mouseX / squareSize);

        int displayRank =
                (int) (mouseY / squareSize);

        if (displayFile < 0
                || displayFile >= BOARD_SIZE
                || displayRank < 0
                || displayRank >= BOARD_SIZE) {

            return null;
        }

        int modelFile =
                flipped ? 7 - displayFile : displayFile;

        int modelRank =
                flipped ? displayRank : 7 - displayRank;

        return new Square(
                modelFile,
                modelRank
        );
    }


    /**
     * Dims the board while an exact tablebase position is being loaded.
     * The scrim is painted inside ChessBoardPanel rather than by a sibling
     * component so its bounds are mathematically identical to the board.
     */
    public void setLoadingDimmed(
            boolean loadingDimmed
    ) {
        if (this.loadingDimmed == loadingDimmed) {
            return;
        }

        this.loadingDimmed =
                loadingDimmed;

        repaint();
    }


    public boolean isLoadingDimmed() {
        return loadingDimmed;
    }


    // =========================================================
    // Painting
    // =========================================================

    /** The host, painting and hit testing share the same square in every mode. */
    static int squareWithin(int availableWidth, int availableHeight) {
        return Math.max(0, Math.min(availableWidth, availableHeight));
    }

    private double getRenderScale() {
        return squareWithin(getWidth(), getHeight()) / (double) (BOARD_SIZE * SQUARE_SIZE);
    }

    @Override
    protected void paintComponent(
            Graphics graphics
    ) {

        super.paintComponent(
                graphics
        );

        Graphics2D g2 =
                (Graphics2D) graphics.create();

        double scale = getRenderScale();
        if (scale <= 0) {
            g2.dispose();
            return;
        }
        g2.scale(scale, scale);

        g2.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
        );

        g2.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON
        );

        drawBoard(
                g2
        );

        if (setupMode) {

            g2.setColor(
                    SETUP_OVERLAY
            );

            g2.fillRect(
                    0,
                    0,
                    BOARD_SIZE * SQUARE_SIZE,
                    BOARD_SIZE * SQUARE_SIZE
            );
        }

        drawSelection(
                g2
        );

        drawLegalMoves(
                g2
        );

        drawPieces(
                g2
        );

        drawPromotionPicker(
                g2
        );

        if (dragging
                && draggedPiece != null) {

            drawDraggedPiece(
                    g2,
                    draggedPiece,
                    (int) Math.round(dragX / scale),
                    (int) Math.round(dragY / scale)
            );
        }

        if (loadingDimmed) {
            g2.setColor(
                    LOADING_OVERLAY
            );

            g2.fillRect(
                    0,
                    0,
                    BOARD_SIZE * SQUARE_SIZE,
                    BOARD_SIZE * SQUARE_SIZE
            );
        }

        g2.dispose();
    }


    private void drawBoard(
            Graphics2D g2
    ) {

        for (int displayRank = 0;
             displayRank < BOARD_SIZE;
             displayRank++) {

            for (int file = 0;
                 file < BOARD_SIZE;
                 file++) {

                boolean light =
                        (displayRank + file)
                                % 2 == 0;

                g2.setColor(
                        light
                                ? LIGHT_SQUARE
                                : DARK_SQUARE
                );

                g2.fillRect(
                        file * SQUARE_SIZE,
                        displayRank * SQUARE_SIZE,
                        SQUARE_SIZE,
                        SQUARE_SIZE
                );
            }
        }
    }


    private void drawSelection(
            Graphics2D g2
    ) {

        if (selectedSquare == null) {
            return;
        }

        int displayRank =
                displayRank(selectedSquare.rank());

        int displayFile =
                displayFile(selectedSquare.file());

        g2.setColor(
                SELECTED_SQUARE
        );

        g2.fillRect(
                displayFile
                        * SQUARE_SIZE,
                displayRank
                        * SQUARE_SIZE,
                SQUARE_SIZE,
                SQUARE_SIZE
        );
    }


    private void drawLegalMoves(
            Graphics2D g2
    ) {

        if (setupMode) {
            return;
        }

        Position displayedPosition =
                getDisplayedPosition();

        if (displayedPosition == null) {
            return;
        }

        Board board =
                displayedPosition.getBoard();

        for (Move move :
                selectedLegalMoves) {

            Square destination =
                    move.to();

            int displayRank =
                    displayRank(destination.rank());

            int displayFile =
                    displayFile(destination.file());

            int x =
                    displayFile
                            * SQUARE_SIZE;

            int y =
                    displayRank
                            * SQUARE_SIZE;

            Piece target =
                    board.getPiece(
                            destination
                    );

            g2.setColor(
                    LEGAL_MOVE_HIGHLIGHT
            );

            if (target == null) {

                int diameter = 20;

                g2.fillOval(
                        x
                                + SQUARE_SIZE / 2
                                - diameter / 2,
                        y
                                + SQUARE_SIZE / 2
                                - diameter / 2,
                        diameter,
                        diameter
                );

            } else {

                int inset = 7;

                g2.setStroke(
                        new BasicStroke(
                                5
                        )
                );

                g2.drawOval(
                        x + inset,
                        y + inset,
                        SQUARE_SIZE - 2 * inset,
                        SQUARE_SIZE - 2 * inset
                );
            }
        }
    }


    private void drawPieces(
            Graphics2D g2
    ) {

        Board board =
                getDisplayedBoard();

        Font font =
                new Font(
                        Font.SERIF,
                        Font.PLAIN,
                        58
                );

        g2.setFont(
                font
        );

        FontMetrics metrics =
                g2.getFontMetrics();

        for (int rank = 0;
             rank < BOARD_SIZE;
             rank++) {

            for (int file = 0;
                 file < BOARD_SIZE;
                 file++) {

                Square square =
                        new Square(
                                file,
                                rank
                        );

                Piece piece =
                        board.getPiece(
                                square
                        );

                if (piece == null) {
                    continue;
                }

                /*
                 * While dragging, leave the origin visually empty.
                 */
                if (dragging
                        && dragSourceSquare != null
                        && square.equals(
                        dragSourceSquare
                )) {
                    continue;
                }

                drawPieceCentered(
                        g2,
                        piece,
                        displayFile(file) * SQUARE_SIZE
                                + SQUARE_SIZE / 2,
                        displayRank(rank) * SQUARE_SIZE
                                + SQUARE_SIZE / 2,
                        metrics
                );
            }
        }
    }


    private void drawDraggedPiece(
            Graphics2D g2,
            Piece piece,
            int centerX,
            int centerY
    ) {

        Font font =
                new Font(
                        Font.SERIF,
                        Font.PLAIN,
                        58
                );

        g2.setFont(
                font
        );

        drawPieceCentered(
                g2,
                piece,
                centerX,
                centerY,
                g2.getFontMetrics()
        );
    }


    private void drawPieceCentered(
            Graphics2D g2,
            Piece piece,
            int centerX,
            int centerY,
            FontMetrics metrics
    ) {

        String symbol =
                getPieceSymbol(
                        piece
                );

        int textWidth =
                metrics.stringWidth(
                        symbol
                );

        int textX =
                centerX
                        - textWidth / 2;

        int textY =
                centerY
                        - metrics.getHeight() / 2
                        + metrics.getAscent();

        g2.setColor(
                java.awt.Color.BLACK
        );

        g2.drawString(
                symbol,
                textX,
                textY
        );
    }


    public static String getPieceSymbol(
            Piece piece
    ) {

        PieceType type =
                piece.type();

        Color color =
                piece.color();

        if (color == Color.WHITE) {

            return switch (type) {
                case KING -> "\u2654";
                case QUEEN -> "\u2655";
                case ROOK -> "\u2656";
                case BISHOP -> "\u2657";
                case KNIGHT -> "\u2658";
                case PAWN -> "\u2659";
            };
        }

        return switch (type) {
            case KING -> "\u265A";
            case QUEEN -> "\u265B";
            case ROOK -> "\u265C";
            case BISHOP -> "\u265D";
            case KNIGHT -> "\u265E";
            case PAWN -> "\u265F";
        };
    }
}

package main.java.chess.gui;

import main.java.chess.model.Board;
import main.java.chess.model.Color;
import main.java.chess.model.Move;
import main.java.chess.model.Piece;
import main.java.chess.model.PieceType;
import main.java.chess.model.Position;
import main.java.chess.model.PositionKey;
import main.java.chess.model.Square;
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
     * Actual playable position.
     */
    private Position position;

    /*
     * Temporary analysis-navigation position.
     */
    private Position previewPosition;

    private final MoveGenerator moveGenerator;

    private Square selectedSquare;
    private List<Move> selectedLegalMoves;

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

        this.moveGenerator =
                new MoveGenerator();

        this.selectedSquare = null;
        this.selectedLegalMoves =
                new ArrayList<>();

        this.dragSourceSquare = null;
        this.draggedPiece = null;
        this.dragging = false;

        this.setupMode = false;
        this.setupBoard = null;
        this.setupSideToMove =
                position.getSideToMove();

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
                };

        addMouseListener(
                mouseAdapter
        );

        addMouseMotionListener(
                mouseAdapter
        );
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

        Position source =
                getDisplayedPosition();

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

        clearSelection();
        cancelDrag();

        repaint();
    }


    public void cancelSetupMode() {

        setupMode =
                false;

        setupBoard =
                null;

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

        setupSideToMove =
                color;

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

        repaint();
        fireSetupChanged();
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

    private void handleMousePressed(
            MouseEvent event
    ) {

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

        for (Move move :
                selectedLegalMoves) {

            if (move.to().equals(
                    destination
            )) {

                /*
                 * If several promotion moves share the destination,
                 * dragging/clicking defaults to queen promotion.
                 */
                if (!move.isPromotion()
                        || move.promotion()
                        == PieceType.QUEEN) {

                    return move;
                }
            }
        }

        for (Move move :
                selectedLegalMoves) {

            if (move.to().equals(
                    destination
            )) {
                return move;
            }
        }

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
    // Drag helpers
    // =========================================================

    private void cancelDrag() {

        dragSourceSquare =
                null;

        draggedPiece =
                null;

        dragging =
                false;
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

        int file =
                mouseX / SQUARE_SIZE;

        int displayRank =
                mouseY / SQUARE_SIZE;

        if (file < 0
                || file >= BOARD_SIZE
                || displayRank < 0
                || displayRank >= BOARD_SIZE) {

            return null;
        }

        int modelRank =
                7 - displayRank;

        return new Square(
                file,
                modelRank
        );
    }


    // =========================================================
    // Painting
    // =========================================================

    @Override
    protected void paintComponent(
            Graphics graphics
    ) {

        super.paintComponent(
                graphics
        );

        Graphics2D g2 =
                (Graphics2D) graphics.create();

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

        if (dragging
                && draggedPiece != null) {

            drawDraggedPiece(
                    g2,
                    draggedPiece,
                    dragX,
                    dragY
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
                7 - selectedSquare.rank();

        g2.setColor(
                SELECTED_SQUARE
        );

        g2.fillRect(
                selectedSquare.file()
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
                    7 - destination.rank();

            int x =
                    destination.file()
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
                        file * SQUARE_SIZE
                                + SQUARE_SIZE / 2,
                        (7 - rank) * SQUARE_SIZE
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

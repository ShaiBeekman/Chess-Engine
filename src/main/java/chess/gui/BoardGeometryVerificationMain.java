package main.java.chess.gui;

import main.java.chess.model.FenCodec;
import main.java.chess.model.Square;
import main.java.chess.endgame.EndgameSettings;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Explicit desktop-session geometry gate. No desktop resolution changes or visible windows. */
public final class BoardGeometryVerificationMain {
    static final String START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private static final String EXACT = "8/8/8/8/8/4K3/6Q1/7k w - - 0 1";

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args.length == 0 ? "target/board-layout/after" : args[0]);
        boolean baseline = args.length > 1 && args[1].equals("--record-baseline");
        Files.createDirectories(output);
        List<String> failures = new ArrayList<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                ChessWindow window = new ChessWindow(FenCodec.parse(START));
                try {
                    call(window, "stopGuiExplorationChain");
                    for (boolean dark : new boolean[]{true, false}) {
                        call(window, "applyTheme", new Class<?>[]{boolean.class}, dark);
                        for (String mode : new String[]{"DOVETAIL", "HYBRID", "STOCKFISH", "SETUP", "ENDGAME"}) {
                            showMode(window, mode);
                            for (int[] screen : new int[][]{{1920,1080}, {1600,900}, {1366,768}, {1280,720}}) {
                                // Simulate a taskbar plus the real native title bar/frame.
                                Rectangle usable = new Rectangle(0, 0, screen[0], screen[1] - 40);
                                window.setBounds(0, 0, screen[0], screen[1]);
                                window.fitWindowToUsableBounds(usable, false);
                                layout(window);
                                String name = mode.toLowerCase() + "-" + screen[0] + "x" + screen[1] + (dark ? "-dark" : "-light");
                                render(window.getContentPane(), output.resolve(name + ".png"));
                                List<String> errors = geometryErrors(window);
                                if (!usable.contains(window.getBounds())) errors.add("Window exceeds usable screen");
                                if (!errors.isEmpty()) failures.add(name + ": " + String.join("; ", errors));
                                ChessBoardPanel board = field(window, "boardPanel", ChessBoardPanel.class);
                                JPanel host = field(window, "boardStack", JPanel.class);
                                System.out.println(name + " board=" + board.getBounds() + " host=" + host.getSize()
                                        + (errors.isEmpty() ? " PASS" : " FAIL " + errors));
                                verifySquares(board);
                                // Loading and flipping must not alter the board allocation.
                                Rectangle bounds = board.getBounds();
                                call(window, "showBoardLoading", new Class<?>[]{long.class, String.class}, 1L, "KQK");
                                layout(window);
                                if (!geometryErrors(window).isEmpty()) failures.add(name + " loading: " + geometryErrors(window));
                                if (!bounds.equals(board.getBounds())) failures.add(name + " loading changed board bounds");
                                if (screen[0] == 1366 && dark) render(window.getContentPane(), output.resolve(name + "-loading.png"));
                                call(window, "hideBoardLoading");
                                board.flipBoard();
                                layout(window);
                                if (!bounds.equals(board.getBounds())) failures.add(name + " flip changed board bounds");
                                board.flipBoard();
                            }
                        }
                    }
                    // Reproduce the exact regression: Home bypassed Setup's border cleanup.
                    showMode(window, "DOVETAIL");
                    call(window, "beginPositionSetup");
                    call(window, "returnToEngineHome");
                    call(window, "stopGuiExplorationChain");
                    layout(window);
                    List<String> home = geometryErrors(window);
                    if (!field(window, "boardStack", JPanel.class).getInsets().equals(new Insets(0,0,0,0))) home.add("Setup coordinate border leaked through Home");
                    if (!home.isEmpty()) failures.add("Setup -> Home: " + home);
                    render(window.getContentPane(), output.resolve("setup-to-home.png"));
                    // Loading FEN also exits Setup through the common analysis handoff.
                    call(window, "beginPositionSetup");
                    call(window, "loadFenPosition", new Class<?>[]{main.java.chess.model.Position.class}, FenCodec.parse(START));
                    call(window, "stopGuiExplorationChain");
                    layout(window);
                    if (field(window, "boardPanel", ChessBoardPanel.class).isSetupMode()) failures.add("FEN retained Setup mode");
                    if (!geometryErrors(window).isEmpty()) failures.add("FEN handoff: " + geometryErrors(window));
                    // Directly constrain the host width: catches the h-file overflow independently of frame size.
                    showMode(window, "DOVETAIL");
                    JPanel column = field(window, "boardArea", JPanel.class);
                    // Constrain width without assuming Home enlarged the user's frame.
                    column.setSize(480, Math.min(700, column.getParent().getHeight()));
                    layout(column);
                    List<String> narrow = geometryErrors(window);
                    if (!narrow.isEmpty()) failures.add("Narrow board host: " + narrow);
                    render(column, output.resolve("narrow-host.png"));
                    if (!baseline) verifyPromotionAtScale();
                    System.out.println("Legacy EndgameSolverPanel is not instantiated or routed by ChessWindow; no shared-board Solver mode exists.");
                } catch (Exception exception) { throw new RuntimeException(exception); }
                finally { window.dispose(); }
            });
            Files.write(output.resolve("failures.txt"), failures);
            if (!failures.isEmpty() && !baseline) throw new AssertionError(String.join("\n", failures));
            System.out.println(baseline ? "BASELINE RECORDED: " + failures.size() + " geometry failures" : "BOARD GEOMETRY VERIFICATION PASSED");
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
        System.exit(0);
    }

    static void showMode(ChessWindow window, String mode) throws Exception {
        call(window, "stopGuiExplorationChain");
        ChessBoardPanel board = field(window, "boardPanel", ChessBoardPanel.class);
        board.cancelSetupMode();
        board.setPosition(FenCodec.parse(START));
        board.setEnabled(true);
        boolean setup = mode.equals("SETUP");
        boolean endgame = mode.equals("ENDGAME");
        field(window, "piecePalettePanel", JPanel.class).setVisible(setup);
        field(window, "setupPaletteHost", JPanel.class).setVisible(setup);
        field(window, "setupPanel", JPanel.class).setVisible(setup);
        field(window, "endgameStudyPanel", JPanel.class).setVisible(endgame);
        field(window, "evaluationBar", JPanel.class).setVisible(!setup && !endgame);
        setField(window, "endgameStudyMode", endgame);
        if (setup) {
            board.beginSetupMode(FenCodec.parse(START));
            call(window, "refreshSetupPanel");
            call(window, "showAuxiliaryAnalysisCard", new Class<?>[]{String.class}, "SETUP");
        } else if (endgame) {
            board.setPosition(FenCodec.parse(EXACT));
            EndgameCurriculumPanel panel = field(window, "endgameStudyPanel", EndgameCurriculumPanel.class);
            panel.setPosition(board.getPosition(), EndgameSettings.fixed(3));
            panel.setFamilyDisplay("KQK");
            call(window, "showAuxiliaryAnalysisCard", new Class<?>[]{String.class}, "ENDGAME");
        } else {
            Field engineMode = ChessWindow.class.getDeclaredField("analysisEngineMode");
            engineMode.setAccessible(true);
            Object value = java.util.Arrays.stream(engineMode.getType().getEnumConstants()).filter(e -> e.toString().equals(mode)).findFirst().orElseThrow();
            engineMode.set(window, value);
            call(window, "showActiveAnalysisEngineCard");
            call(window, "styleEngineSelectorButtons");
        }
        call(window, "updateBoardAreaInsetsForCurrentMode");
        call(window, "updateApplicationHeaderBorderForCurrentMode");
    }

    static List<String> geometryErrors(ChessWindow window) throws Exception {
        List<String> errors = new ArrayList<>();
        ChessBoardPanel board = field(window, "boardPanel", ChessBoardPanel.class);
        JPanel host = field(window, "boardStack", JPanel.class);
        JPanel column = field(window, "boardArea", JPanel.class);
        Insets insets = host.getInsets();
        Rectangle inner = new Rectangle(insets.left, insets.top,
                Math.max(0, host.getWidth() - insets.left - insets.right), Math.max(0, host.getHeight() - insets.top - insets.bottom));
        if (board.getWidth() <= 0 || board.getWidth() != board.getHeight()) errors.add("Board is not a positive square");
        if (!inner.contains(board.getBounds())) errors.add("Board " + board.getBounds() + " exceeds host " + inner);
        Component child = board;
        while (child.getParent() != null && child.getParent() != window) {
            if (!new Rectangle(child.getParent().getSize()).contains(child.getBounds())) errors.add(child.getClass().getSimpleName() + " exceeds ancestor bounds");
            child = child.getParent();
        }
        JComponent overlay = field(window, "boardLoadingOverlay", JComponent.class);
        if (!overlay.getBounds().equals(board.getBounds())) errors.add("Loading overlay differs from board");
        if (overlay.isVisible()) {
            for (Component card : overlay.getComponents()) if (!new Rectangle(overlay.getSize()).contains(card.getBounds())) errors.add("Loading card exceeds board");
        }
        JComponent bar = field(window, "evaluationBar", JComponent.class);
        if (bar.isVisible()) {
            Rectangle square = SwingUtilities.convertRectangle(host, board.getBounds(), column);
            if (bar.getY() != square.y || bar.getHeight() != square.height) errors.add("Evaluation bar " + bar.getBounds() + " differs from square " + square);
        }
        // Painted pixels at every file/rank must be present, including the h-file and bottom edge.
        BufferedImage image = new BufferedImage(board.getWidth(), board.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        board.printAll(graphics);
        graphics.dispose();
        for (int rank = 0; rank < 8; rank++) for (int file = 0; file < 8; file++) {
            int x = (int) ((file + .08) * board.getWidth() / 8);
            int y = (int) ((rank + .08) * board.getHeight() / 8);
            int expected = image.getRGB((int) (((file + rank) % 2 + .08) * board.getWidth() / 8), (int) (.08 * board.getHeight() / 8));
            if (image.getRGB(x,y) != expected) { errors.add("Rendered squares do not follow current board size"); return errors; }
        }
        return errors;
    }

    private static void verifyPromotionAtScale() {
        for (boolean flipped : new boolean[]{false, true}) {
            ChessBoardPanel board = new ChessBoardPanel(FenCodec.parse("7k/P7/8/8/8/8/8/K7 w - - 0 1"));
            board.setSize(335,335);
            board.setFlipped(flipped);
            Point from = center(board,new Square(0,6));
            Point to = center(board,new Square(0,7));
            mouse(board,java.awt.event.MouseEvent.MOUSE_PRESSED,from);
            mouse(board,java.awt.event.MouseEvent.MOUSE_DRAGGED,to);
            mouse(board,java.awt.event.MouseEvent.MOUSE_RELEASED,to);
            // The fourth promotion tile is a knight in either board orientation.
            Point knight = center(board,new Square(0,4));
            mouse(board,java.awt.event.MouseEvent.MOUSE_PRESSED,knight);
            mouse(board,java.awt.event.MouseEvent.MOUSE_RELEASED,knight);
            if (board.getPosition().getBoard().getPiece(new Square(0,7)).type() != main.java.chess.model.PieceType.KNIGHT)
                throw new AssertionError("Scaled promotion choice missed its tile");
        }
    }

    private static void mouse(ChessBoardPanel board,int id,Point point) {
        boolean drag=id==java.awt.event.MouseEvent.MOUSE_DRAGGED;
        board.dispatchEvent(new java.awt.event.MouseEvent(board,id,System.currentTimeMillis(),
                drag?java.awt.event.MouseEvent.BUTTON1_DOWN_MASK:0,point.x,point.y,1,false,
                drag?java.awt.event.MouseEvent.NOBUTTON:java.awt.event.MouseEvent.BUTTON1));
    }

    static void verifySquares(ChessBoardPanel board) {
        for (boolean flipped : new boolean[]{false,true}) {
            board.setFlipped(flipped);
            for (int rank=0; rank<8; rank++) for (int file=0; file<8; file++) {
                Square square=new Square(file,rank);
                if (!square.equals(board.getSquareAtPoint(center(board,square)))) throw new AssertionError("Square mapping: " + square);
            }
        }
        board.setFlipped(false);
    }
    static Point center(ChessBoardPanel board, Square square) {
        int file = board.isFlipped() ? 7-square.file() : square.file();
        int rank = board.isFlipped() ? square.rank() : 7-square.rank();
        return new Point((int)((file+.5)*board.getWidth()/8), (int)((rank+.5)*board.getHeight()/8));
    }
    static void layout(Container parent) { for(int i=0;i<3;i++) layoutTree(parent); }
    private static void layoutTree(Container parent) {
        parent.doLayout();
        for(Component child:parent.getComponents()) if(child.isVisible() && child instanceof Container c) layoutTree(c);
    }
    static void render(Container component, Path output) throws Exception {
        BufferedImage image=new BufferedImage(component.getWidth(),component.getHeight(),BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics=image.createGraphics(); component.printAll(graphics); graphics.dispose();
        ImageIO.write(image,"png",output.toFile());
    }
    static <T> T field(Object target,String name,Class<T> type) throws Exception {
        Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);return type.cast(field.get(target));
    }
    static void setField(Object target,String name,Object value) throws Exception {
        Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);
    }
    static Object call(Object target,String name,Class<?>[] types,Object...args) throws Exception {
        Method method=target.getClass().getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(target,args);
    }
    static Object call(Object target,String name) throws Exception { return call(target,name,new Class<?>[0]); }
}

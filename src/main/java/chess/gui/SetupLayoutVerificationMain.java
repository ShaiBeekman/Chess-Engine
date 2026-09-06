package main.java.chess.gui;

import main.java.chess.model.FenCodec;
import main.java.chess.model.Piece;
import main.java.chess.model.PieceType;
import main.java.chess.model.Square;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Headful, offscreen Swing verification; run explicitly after Maven package. */
public final class SetupLayoutVerificationMain {
    private static final String START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args.length == 0 ? "target/setup-layout" : args[0]);
        Files.createDirectories(output);
        try {
            SwingUtilities.invokeAndWait(() -> {
                ChessWindow window = new ChessWindow(FenCodec.parse(START));
                try {
                    check(ChessWindow.usableWindowBounds(window.getGraphicsConfiguration()).contains(window.getBounds()),
                            "Startup window exceeds usable monitor bounds");
                    call(window, "beginPositionSetup");
                    for (boolean dark : new boolean[]{true, false}) {
                        call(window, "applyTheme", new Class<?>[]{boolean.class}, dark);
                        for (int[] screen : new int[][]{{1920, 1080}, {1600, 900}, {1366, 768}, {1280, 720}}) {
                            // Reserve a 40px Windows taskbar; JFrame contributes its actual native decorations.
                            Rectangle usable = new Rectangle(0, 0, screen[0], screen[1] - 40);
                            window.setBounds(0, 0, screen[0] + 200, screen[1] + 200);
                            window.fitWindowToUsableBounds(usable, false);
                            check(usable.contains(window.getBounds()), "Frame/minimum exceeds " + usable);
                            layout(window);
                            String name = "setup-" + screen[0] + "x" + screen[1] + (dark ? "-dark" : "-light");
                            render(window.getContentPane(), output.resolve(name + ".png"));
                            verifySetup(window);
                            System.out.println(name + " client=" + window.getContentPane().getSize()
                                    + " board=" + field(window, "boardPanel", ChessBoardPanel.class).getSize()
                                    + " palette=" + field(window, "piecePalettePanel", PiecePalettePanel.class).getSize());
                        }
                    }
                    // A taskbar at the top/left and a monitor left of the primary must also fit.
                    Rectangle secondary = new Rectangle(-1560, 30, 1560, 830);
                    window.setBounds(-1700, -50, 1800, 1000);
                    window.fitWindowToUsableBounds(secondary, false);
                    check(secondary.contains(window.getBounds()), "Secondary-monitor clamp");
                    window.fitWindowToUsableBounds(new Rectangle(0, 0, 1366, 728), false);
                    layout(window);
                    verifyEdits(window);
                    call(window, "cancelPositionSetup");
                    layout(window);
                    ChessBoardPanel board = field(window, "boardPanel", ChessBoardPanel.class);
                    check(!board.isSetupMode(), "Cancel must leave Setup");
                    check(BoardGeometryVerificationMain.geometryErrors(window).isEmpty(), "Analysis board geometry after Cancel");
                    check(!field(window, "setupPaletteHost", JPanel.class).isVisible(), "Palette leaked into Analysis");
                    render(window.getContentPane(), output.resolve("analysis-after-setup.png"));
                    call(window, "stopGuiExplorationChain");
                    // Exercise the Endgame containers without triggering tablebase generation/loading.
                    Field mode = ChessWindow.class.getDeclaredField("endgameStudyMode");
                    mode.setAccessible(true);
                    mode.setBoolean(window, true);
                    call(window, "updateBoardAreaInsetsForCurrentMode");
                    field(window, "analysisEngineCards", JPanel.class).setVisible(false);
                    field(window, "evaluationBar", JComponent.class).setVisible(false);
                    field(window, "endgameStudyPanel", JPanel.class).setVisible(true);
                    call(window, "showAuxiliaryAnalysisCard", new Class<?>[]{String.class}, "ENDGAME");
                    layout(window);
                    check(BoardGeometryVerificationMain.geometryErrors(window).isEmpty(), "Endgame board geometry after Setup");
                    check(field(window, "endgameStudyPanel", JPanel.class).isVisible()
                            && field(window, "auxiliaryAnalysisCards", JPanel.class).isVisible()
                            && !field(window, "analysisEngineCards", JPanel.class).isVisible(), "Endgame dashboard hidden");
                    render(window.getContentPane(), output.resolve("endgame-after-setup.png"));
                    System.out.println("SETUP LAYOUT VERIFICATION PASSED");
                } catch (Exception failure) {
                    throw new RuntimeException(failure);
                } finally {
                    window.dispose();
                }
            });
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }

    private static void verifySetup(ChessWindow window) throws Exception {
        ChessBoardPanel board = field(window, "boardPanel", ChessBoardPanel.class);
        SetupPanel dashboard = field(window, "setupPanel", SetupPanel.class);
        PiecePalettePanel palette = field(window, "piecePalettePanel", PiecePalettePanel.class);
        check(board.getWidth() == board.getHeight() && board.getWidth() > 300, "Board must be square and usable");
        List<String> problems = new ArrayList<>();
        inspect(dashboard, problems);
        inspect(palette, problems);
        inspect(field(window, "applicationHeader", JPanel.class), problems);
        check(problems.isEmpty(), String.join("\n", problems));
        Rectangle boardBounds = SwingUtilities.convertRectangle(board.getParent(), board.getBounds(), window.getContentPane());
        Rectangle paletteBounds = SwingUtilities.convertRectangle(palette.getParent(), palette.getBounds(), window.getContentPane());
        check(paletteBounds.y > boardBounds.y + boardBounds.height, "Palette must remain below board");
        check(new Rectangle(window.getContentPane().getSize()).contains(paletteBounds), "Palette outside client area");
        check(buttons(palette).size() == 13, "Both six-piece rows and Clear Board must be present");
        for (String text : new String[]{"White", "Black", "Clear", "Cancel", "ANALYZE POSITION"}) {
            check(button(dashboard, text).isVisible(), "Missing action: " + text);
        }
        for (boolean flipped : new boolean[]{false, true}) {
            board.setFlipped(flipped);
            for (int rank = 0; rank < 8; rank++) for (int file = 0; file < 8; file++) {
                Square expected = new Square(file, rank);
                check(expected.equals(board.getSquareAtPoint(center(board, expected))), "Scaled/flipped square mapping: " + expected);
            }
            check(board.getSquareAtPoint(new Point(-1, 0)) == null, "Negative drop must be outside board");
            check(board.getSquareAtPoint(new Point(board.getWidth(), 0)) == null, "Right-edge drop must be outside board");
        }
        board.setFlipped(false);
    }

    private static void verifyEdits(ChessWindow window) throws Exception {
        ChessBoardPanel board = field(window, "boardPanel", ChessBoardPanel.class);
        SetupPanel dashboard = field(window, "setupPanel", SetupPanel.class);
        PiecePalettePanel palette = field(window, "piecePalettePanel", PiecePalettePanel.class);
        for (boolean flipped : new boolean[]{false, true}) {
            board.setFlipped(flipped);
            Point from = center(board, new Square(4, 1));
            Point to = center(board, new Square(4, 3));
            board.dispatchEvent(new MouseEvent(board, MouseEvent.MOUSE_PRESSED, 1, 0, from.x, from.y, 1, false, MouseEvent.BUTTON1));
            board.dispatchEvent(new MouseEvent(board, MouseEvent.MOUSE_DRAGGED, 2, MouseEvent.BUTTON1_DOWN_MASK, to.x, to.y, 0, false, MouseEvent.NOBUTTON));
            board.dispatchEvent(new MouseEvent(board, MouseEvent.MOUSE_RELEASED, 3, 0, to.x, to.y, 1, false, MouseEvent.BUTTON1));
            check(board.getSetupBoardSnapshot().getPiece(new Square(4, 1)) == null, "Drag source not cleared");
            check(board.getSetupBoardSnapshot().getPiece(new Square(4, 3)).type() == PieceType.PAWN, "Scaled drag landed on wrong square");
            check(board.undoSetupEdit() && board.redoSetupEdit() && board.undoSetupEdit(), "Setup history after scaled drag");
        }
        board.setFlipped(false);
        button(dashboard, "Black").doClick();
        check(board.getSetupSideToMove() == main.java.chess.model.Color.BLACK, "Side to move control");
        check(field(dashboard, "fenArea", JTextField.class).getText().contains(" b "), "FEN side to move");
        button(palette, "Clear Board").doClick();
        check(!button(dashboard, "ANALYZE POSITION").isEnabled(), "Cleared position must disable Analyze");
        board.placeSetupPiece(new Piece(PieceType.KING, main.java.chess.model.Color.WHITE), new Square(4, 0));
        board.placeSetupPiece(new Piece(PieceType.KING, main.java.chess.model.Color.BLACK), new Square(4, 7));
        check(button(dashboard, "ANALYZE POSITION").isEnabled(), "Valid position must enable Analyze");
        // A long validation message must fit alongside the compact actions.
        board.placeSetupPiece(new Piece(PieceType.ROOK, main.java.chess.model.Color.BLACK), new Square(4, 1));
        layout(window);
        check(!button(dashboard, "ANALYZE POSITION").isEnabled(), "Illegal position must disable Analyze");
        verifySetup(window);
        check(board.undoSetupEdit(), "Undo invalid position");
        verifySetup(window);
        // Validate compact spacing after a theme change at an unchanged window size.
        call(window, "applyTheme", new Class<?>[]{boolean.class}, true);
        layout(window);
        verifySetup(window);
    }

    private static Point center(ChessBoardPanel board, Square square) {
        int file = board.isFlipped() ? 7 - square.file() : square.file();
        int rank = board.isFlipped() ? square.rank() : 7 - square.rank();
        return new Point((int) ((file + 0.5) * board.getWidth() / 8), (int) ((rank + 0.5) * board.getHeight() / 8));
    }

    private static void inspect(Container parent, List<String> problems) {
        for (Component child : parent.getComponents()) {
            if (!child.isVisible()) continue;
            String name = child instanceof AbstractButton b ? b.getText() : child instanceof JLabel l ? l.getText() : child.getClass().getSimpleName();
            if (child instanceof JScrollPane) problems.add("Unexpected workspace scroll pane");
            if (child.getWidth() <= 0 || child.getHeight() <= 0 || !new Rectangle(parent.getSize()).contains(child.getBounds()))
                problems.add("Clipped " + name + " " + child.getBounds() + " in " + parent.getSize());
            if (child instanceof JLabel || child instanceof AbstractButton) {
                JComponent control = (JComponent) child;
                Insets insets = control.getInsets();
                Icon icon = child instanceof JLabel label ? label.getIcon() : ((AbstractButton) child).getIcon();
                int textHeight = name == null || name.isEmpty() ? 0 : child.getFontMetrics(child.getFont()).getHeight();
                int neededHeight = Math.max(textHeight, icon == null ? 0 : icon.getIconHeight()) + insets.top + insets.bottom;
                if (child.getHeight() < neededHeight)
                    problems.add("Short control " + name + " " + child.getSize() + " needs " + neededHeight);
            }
            if (child instanceof JTextArea area && !area.getText().isEmpty()) {
                try {
                    var end = area.modelToView2D(area.getDocument().getLength());
                    if (end != null && end.getMaxY() > area.getHeight()) problems.add("Clipped text area: " + area.getText());
                } catch (javax.swing.text.BadLocationException failure) { throw new AssertionError(failure); }
            }
            if (child instanceof Container nested) inspect(nested, problems);
        }
    }

    private static List<AbstractButton> buttons(Container parent) {
        List<AbstractButton> result = new ArrayList<>();
        for (Component child : parent.getComponents()) {
            if (child instanceof AbstractButton b) result.add(b);
            else if (child instanceof Container nested) result.addAll(buttons(nested));
        }
        return result;
    }

    private static AbstractButton button(Container parent, String text) {
        return buttons(parent).stream().filter(b -> text.equals(b.getText())).findFirst().orElseThrow();
    }

    private static void layout(Container parent) {
        for (int i = 0; i < 3; i++) layoutTree(parent);
    }
    private static void layoutTree(Container parent) {
        parent.doLayout();
        for (Component child : parent.getComponents()) if (child.isVisible() && child instanceof Container nested) layoutTree(nested);
    }
    private static void render(Container content, Path path) throws Exception {
        BufferedImage image = new BufferedImage(content.getWidth(), content.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        content.printAll(graphics);
        graphics.dispose();
        ImageIO.write(image, "png", path.toFile());
    }
    private static <T> T field(Object target, String name, Class<T> type) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(target));
    }
    private static void call(Object target, String name) throws Exception { call(target, name, new Class<?>[0]); }
    private static void call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        method.invoke(target, args);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

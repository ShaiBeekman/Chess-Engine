package main.java.chess.gui;

import main.java.chess.analysis.MoveAnalysis;
import main.java.chess.analysis.PositionAnalysis;
import main.java.chess.endgame.ExactEndgameTablebase;
import main.java.chess.model.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.WindowEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import static main.java.chess.gui.BoardGeometryVerificationMain.*;

/** Live Swing action/drag smoke pass; optional --robot drives native desktop input.
 * Use a temporary APPDATA directory to isolate curriculum progress. */
public final class BoardInteractionVerificationMain {
    private static ChessWindow window;
    private static Robot robot;
    private static boolean nativeInput;
    private static Path output;

    public static void main(String[] args) throws Exception {
        output=Path.of(args.length==0?"target/board-layout/live":args[0]);
        Files.createDirectories(output);
        nativeInput=args.length>1 && args[1].equals("--robot");
        if(nativeInput) { robot=new Robot(); robot.setAutoDelay(80); }
        Point pointer=nativeInput?MouseInfo.getPointerInfo().getLocation():null;
        int status=0;
        try {
            edt(() -> {
                window=new ChessWindow(FenCodec.parse(START));
                window.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                window.setSize(1366,800);
                window.setLocation(20,20);
                window.setVisible(true);
                return null;
            });
            await("Dovetail analysis", () -> {
                PositionAnalysis analysis=field(window,"currentAnalysis",PositionAnalysis.class);
                return analysis!=null && !analysis.getMoves().isEmpty();
            });
            checkGeometry("dovetail-initial");
            edt(() -> {
                PositionAnalysis analysis=field(window,"currentAnalysis",PositionAnalysis.class);
                call(field(window,"analysisPanel",AnalysisPanel.class),"selectRootMove",new Class<?>[]{MoveAnalysis.class},analysis.getMoves().get(0));
                return null;
            });
            await("Dovetail continuation", () -> board().isPreviewing());
            checkGeometry("dovetail-continuation");
            click(edt(() -> field(field(window,"analysisPanel",AnalysisPanel.class),"backButton",JButton.class)));
            await("Dovetail back", () -> !board().isPreviewing());
            checkGeometry("dovetail-back");
            selectEngine("Hybrid");
            await("Hybrid analysis", () -> field(window,"currentAnalysis",PositionAnalysis.class)!=null);
            checkGeometry("hybrid");
            selectEngine("Stockfish");
            await("Stockfish candidates", () -> !field(field(window,"stockfishCandidatePanel",StockfishCandidatePanel.class),"currentCandidates",List.class).isEmpty());
            checkGeometry("stockfish");
            click(edt(() -> field(window,"flipBoardButton",JButton.class)));
            await("Flip", () -> board().isFlipped());
            checkGeometry("stockfish-flipped");
            click(edt(() -> field(window,"flipBoardButton",JButton.class)));

            click(edt(() -> field(window,"setupPositionButton",JButton.class)));
            await("Setup", () -> board().isSetupMode());
            click(button(edt(() -> field(window,"piecePalettePanel",JPanel.class)),"Clear Board"));
            paletteDrag("white","king",new Square(4,0));
            paletteDrag("black","king",new Square(4,7));
            paletteDrag("white","queen",new Square(3,3));
            paletteDrag("black","rook",new Square(2,5));
            checkGeometry("setup-white-black-drops");
            dragSquare(new Square(3,3),new Square(5,2));
            Point outside=edt(() -> { Point p0=board().getLocationOnScreen(); return new Point(p0.x+board().getWidth()+12,p0.y+board().getHeight()/2); });
            drag(screenSquare(new Square(2,5)),outside);
            await("Remove piece", () -> board().getSetupBoardSnapshot().getPiece(new Square(2,5))==null);
            paletteDrag("black","rook",new Square(2,5));
            click(edt(() -> field(window,"flipBoardButton",JButton.class)));
            paletteDrag("white","pawn",new Square(4,1));
            checkGeometry("setup-flipped-edit");
            click(edt(() -> field(window,"flipBoardButton",JButton.class)));
            click(button(edt(() -> field(window,"setupPanel",JPanel.class)),"Cancel"));
            await("Cancel", () -> !board().isSetupMode());
            checkGeometry("setup-cancel");

            click(edt(() -> field(window,"setupPositionButton",JButton.class)));
            click(button(edt(() -> field(window,"piecePalettePanel",JPanel.class)),"Clear Board"));
            paletteDrag("white","king",new Square(4,0));
            paletteDrag("black","king",new Square(4,7));
            click(button(edt(() -> field(window,"setupPanel",JPanel.class)),"Black"));
            click(button(edt(() -> field(window,"setupPanel",JPanel.class)),"ANALYZE POSITION"));
            await("Analyze Position", () -> !board().isSetupMode());
            edt(() -> { require(board().getPosition().getSideToMove()==main.java.chess.model.Color.BLACK,"Analyze side to move");return null; });
            checkGeometry("setup-analyze");

            click(edt(() -> field(window,"setupPositionButton",JButton.class)));
            // Setup Reset restores its starting snapshot without leaving Setup.
            click(button(edt(() -> field(window,"piecePalettePanel",JPanel.class)),"Clear Board"));
            click(edt(() -> field(window,"resetPositionButton",JButton.class)));
            await("Setup Reset", () -> board().isSetupMode()
                    && board().getSetupBoardSnapshot().getPiece(new Square(7,0))!=null);
            checkGeometry("setup-reset");
            click(edt(() -> field(window,"engineHomeButton",JButton.class)));
            await("Home from Setup", () -> !board().isSetupMode());
            checkGeometry("setup-home");
            // Normal Analysis Reset retains its modal confirmation.
            click(edt(() -> field(window,"resetPositionButton",JButton.class)));
            await("Reset confirmation", () -> visibleDialog()!=null);
            click(button(edt(BoardInteractionVerificationMain::visibleDialog),"Yes"));
            await("Analysis Reset", () -> visibleDialog()==null);
            checkGeometry("analysis-reset");

            selectEngine("Dovetail");
            edt(() -> { setField(window,"selectedEndgameFamily","KQK"); return null; });
            click(edt(() -> field(window,"endgameButton",JButton.class)));
            await("Exact KQK", () -> field(window,"endgameStudyReady",Boolean.class) && board().isEnabled());
            checkGeometry("endgame-exact");
            String first=edt(() -> FenCodec.toFen(board().getPosition()));
            Move best=edt(() -> field(window,"activeEndgameTablebase",ExactEndgameTablebase.class).bestMoves(board().getPosition()).get(0));
            dragSquare(best.from(),best.to());
            await("Legal exact move", () -> !FenCodec.toFen(board().getPosition()).equals(first) && board().isEnabled());
            checkGeometry("endgame-move");
            int committed = edt(() -> field(window, "gameHistory", List.class).size());
            String latestFen = edt(() -> FenCodec.toFen(board().getPosition()));
            edt(() -> {
                EndgamePlayedLine line = field(field(window, "endgameStudyPanel", EndgameCurriculumPanel.class), "playedLine", EndgamePlayedLine.class);
                require(field(line, "steps", List.class).size() == committed - 1, "Live review missing committed moves");
                return null;
            });
            click(edt(() -> field(field(window, "endgameStudyPanel", EndgameCurriculumPanel.class), "previousMoveButton", JButton.class)));
            await("Previous exact move", () -> field(window, "endgameMoveReviewIndex", Integer.class) == committed - 2 && !board().isEnabled());
            checkGeometry("endgame-review-previous");
            click(edt(() -> field(field(window, "endgameStudyPanel", EndgameCurriculumPanel.class), "nextMoveButton", JButton.class)));
            await("Return to latest exact move", () -> latestFen.equals(FenCodec.toFen(board().getPosition())) && board().isEnabled());
            edt(() -> { require(field(window, "gameHistory", List.class).size() == committed, "Review mutated committed history"); return null; });
            String study=edt(() -> field(window,"currentEndgameStudyId",String.class));
            click(edt(() -> field(field(window,"endgameStudyPanel",EndgameCurriculumPanel.class),"nextButton",JButton.class)));
            await("Next exact position", () -> field(window,"endgameStudyReady",Boolean.class)
                    && !study.equals(field(window,"currentEndgameStudyId",String.class)));
            checkGeometry("endgame-next");
            edt(() -> {
                EndgamePlayedLine line = field(field(window, "endgameStudyPanel", EndgameCurriculumPanel.class), "playedLine", EndgamePlayedLine.class);
                require(field(line, "steps", List.class).isEmpty(), "Next Position retained the previous played line");
                return null;
            });
            click(edt(() -> field(field(window,"endgameStudyPanel",EndgameCurriculumPanel.class),"hintButton",JButton.class)));
            checkGeometry("endgame-hint");
            click(edt(() -> field(field(window,"endgameStudyPanel",EndgameCurriculumPanel.class),"giveUpButton",JButton.class)));
            checkGeometry("endgame-give-up");
            click(edt(() -> field(window,"engineHomeButton",JButton.class)));
            await("Engine Home", () -> !field(window,"endgameStudyMode",Boolean.class));
            checkGeometry("endgame-home");
            System.out.println("Solver is absent from the current GUI routing; no Solver navigation was fabricated.");
            System.out.println("LIVE BOARD INTERACTION VERIFICATION PASSED");
        } catch(Throwable failure) {
            status=1; failure.printStackTrace();
            if(window!=null) try { edt(() -> { render(window.getContentPane(),output.resolve("failure.png"));return null; }); } catch(Exception ignored) {}
        } finally {
            if(window!=null) edt(() -> {
                for(Window owned:window.getOwnedWindows()) owned.dispose();
                window.dispatchEvent(new WindowEvent(window,WindowEvent.WINDOW_CLOSING)); return null;
            });
            if(nativeInput) robot.mouseMove(pointer.x,pointer.y);
        }
        System.exit(status);
    }

    private static ChessBoardPanel board() throws Exception { return field(window,"boardPanel",ChessBoardPanel.class); }
    private static void selectEngine(String name) throws Exception {
        click(edt(() -> field(window,"engineModeDropdownButton",JButton.class)));
        JPopupMenu menu=edt(() -> field(window,"engineModeMenu",JPopupMenu.class));
        click(button(menu,name));
        await(name+" mode", () -> field(window,"analysisEngineMode",Object.class).toString().equals(name.toUpperCase()));
    }
    private static void paletteDrag(String color,String type,Square target) throws Exception {
        AbstractButton tile=edt(() -> buttons(field(window,"piecePalettePanel",JPanel.class)).stream()
                .filter(b -> b.getToolTipText()!=null && b.getToolTipText().toLowerCase().contains(color)
                        && b.getToolTipText().toLowerCase().contains(type)).findFirst().orElseThrow());
        drag(centerOnScreen(tile),screenSquare(target));
        await("Palette "+color+" "+type, () -> {
            Piece piece=board().getSetupBoardSnapshot().getPiece(target);
            return piece!=null && piece.type().toString().equalsIgnoreCase(type) && piece.color().toString().equalsIgnoreCase(color);
        });
        edt(() -> { PiecePalettePanel palette=field(window,"piecePalettePanel",PiecePalettePanel.class);
            require(field(palette,"dragGhost",Window.class)==null,"Drag ghost was not cleaned up"); return null; });
    }
    private static void dragSquare(Square from,Square to) throws Exception { drag(screenSquare(from),screenSquare(to)); }
    private static Point screenSquare(Square square) throws Exception {
        return edt(() -> { Point p0=center(board(),square); SwingUtilities.convertPointToScreen(p0,board());return p0; });
    }
    private static void drag(Point from,Point to) throws Exception {
        if(nativeInput) {
            robot.mouseMove(from.x,from.y);robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            for(int i=1;i<=12;i++) robot.mouseMove(from.x+(to.x-from.x)*i/12,from.y+(to.y-from.y)*i/12);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);robot.waitForIdle();
            return;
        }
        Component source=edt(() -> {
            Point point=new Point(from);SwingUtilities.convertPointFromScreen(point,window.getContentPane());
            return SwingUtilities.getDeepestComponentAt(window.getContentPane(),point.x,point.y);
        });
        mouse(source,java.awt.event.MouseEvent.MOUSE_PRESSED,from);
        for(int i=1;i<=12;i++) mouse(source,java.awt.event.MouseEvent.MOUSE_DRAGGED,
                new Point(from.x+(to.x-from.x)*i/12,from.y+(to.y-from.y)*i/12));
        mouse(source,java.awt.event.MouseEvent.MOUSE_RELEASED,to);
        edt(() -> null);
    }
    private static void mouse(Component source,int id,Point screen) throws Exception {
        edt(() -> {
            Point point=new Point(screen);SwingUtilities.convertPointFromScreen(point,source);
            boolean drag=id==java.awt.event.MouseEvent.MOUSE_DRAGGED;
            source.dispatchEvent(new java.awt.event.MouseEvent(source,id,System.currentTimeMillis(),
                    drag?InputEvent.BUTTON1_DOWN_MASK:0,point.x,point.y,screen.x,screen.y,1,false,
                    drag?java.awt.event.MouseEvent.NOBUTTON:java.awt.event.MouseEvent.BUTTON1));
            return null;
        });
    }
    private static void click(AbstractButton button) throws Exception {
        if(nativeInput) {
            Point point=centerOnScreen(button);robot.mouseMove(point.x,point.y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        } else {
            // Queue, rather than block on, actions that may enter a modal event loop.
            SwingUtilities.invokeLater(() -> button.doClick(0));
        }
        Thread.sleep(100);
    }
    private static Point centerOnScreen(Component component) throws Exception {
        return edt(() -> { Point point=component.getLocationOnScreen();point.translate(component.getWidth()/2,component.getHeight()/2);return point; });
    }
    private static AbstractButton button(Container parent,String text) throws Exception {
        return edt(() -> buttons(parent).stream().filter(b -> text.equals(b.getText())).findFirst().orElseThrow());
    }
    private static List<AbstractButton> buttons(Container parent) {
        List<AbstractButton> result=new ArrayList<>();
        for(Component child:parent.getComponents()) {
            if(child instanceof AbstractButton b) result.add(b);
            if(child instanceof Container c) result.addAll(buttons(c));
        }
        return result;
    }
    private static JDialog visibleDialog() {
        for(Window candidate:Window.getWindows()) if(candidate instanceof JDialog dialog && dialog.isShowing()) return dialog;
        return null;
    }
    private static void checkGeometry(String step) throws Exception {
        if(nativeInput) robot.waitForIdle();
        edt(() -> {
            List<String> errors=geometryErrors(window);
            require(errors.isEmpty(),step+": "+errors);
            Insets insets=field(window,"boardStack",JPanel.class).getInsets();
            if(!board().isSetupMode()) require(insets.equals(new Insets(0,0,0,0)),step+": Setup border leaked");
            System.out.println(step+" PASS board="+board().getSize()+" window="+window.getSize());
            return null;
        });
        if(nativeInput) {
            Rectangle bounds=edt(window::getBounds);
            ImageIO.write(robot.createScreenCapture(bounds),"png",output.resolve(step+".png").toFile());
        } else edt(() -> { render(window.getContentPane(),output.resolve(step+".png"));return null; });
    }
    private static <T> T edt(Callable<T> action) throws Exception {
        FutureTask<T> future=new FutureTask<>(action);SwingUtilities.invokeLater(future);return future.get();
    }
    private static void await(String step,Callable<Boolean> condition) throws Exception {
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(90);
        while(!edt(condition)) {
            if(System.nanoTime()>deadline) throw new AssertionError("Timed out: "+step);
            Thread.sleep(100);
        }
    }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}

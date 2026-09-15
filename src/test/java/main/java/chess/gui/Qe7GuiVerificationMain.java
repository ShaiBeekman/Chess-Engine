package main.java.chess.gui;

import main.java.chess.endgame.*;
import main.java.chess.model.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import static main.java.chess.gui.BoardGeometryVerificationMain.*;

/** Automated Swing listener checks against the supplied production JAR.
 * Native EXE mouse verification is recorded separately. */
public final class Qe7GuiVerificationMain {
    static final String ROOT="8/8/2K5/8/8/q7/1k6/8 b - - 0 1";
    static ChessWindow window;
    static EndgameCurriculumPanel panel;
    static ChessBoardPanel board;
    static Path output;
    static boolean fixed;
    static int attempted;
    static List<Position> history() throws Exception {return field(window,"gameHistory",List.class);}
    static List<Position> solution() throws Exception {return field(window,"endgameSolutionHistory",List.class);}
    static int index() throws Exception {return field(window,"endgameMoveReviewIndex",Integer.class);}
    static boolean flag(String name)throws Exception{return field(window,name,Boolean.class);}
    static String status()throws Exception{return field(panel,"statusValue",JTextArea.class).getText();}
    static String fen(Position p){return FenCodec.toFen(p);}
    static String current()throws Exception{return fen(board.getPosition());}
    static <T>T edt(Callable<T> task)throws Exception{FutureTask<T> f=new FutureTask<>(task);SwingUtilities.invokeAndWait(f);return f.get();}
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void await(String label,Callable<Boolean> condition)throws Exception{
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(60);
        while(System.nanoTime()<until){if(edt(condition))return;Thread.sleep(75);}
        throw new AssertionError("Timeout: "+label+"; "+edt(()->status()));
    }
    static void click(Object owner,String name)throws Exception{
        edt(()->{JButton b=field(owner,name,JButton.class);require(b.isShowing()&&b.isEnabled(),"Disabled: "+name);b.doClick();return null;});
    }
    static void mouse(Component c,int kind,int x,int y){
        c.dispatchEvent(new MouseEvent(c,kind,System.currentTimeMillis(),kind==MouseEvent.MOUSE_PRESSED?InputEvent.BUTTON1_DOWN_MASK:0,x,y,1,false,MouseEvent.BUTTON1));
    }
    static void boardMove(String from,String to,boolean drag)throws Exception{
        edt(()->{
            Square a=new Square(from.charAt(0)-'a',from.charAt(1)-'1'),b=new Square(to.charAt(0)-'a',to.charAt(1)-'1');
            Point p=center(board,a),q=center(board,b);
            mouse(board,MouseEvent.MOUSE_PRESSED,p.x,p.y);
            if(drag)mouse(board,MouseEvent.MOUSE_DRAGGED,q.x,q.y);else{mouse(board,MouseEvent.MOUSE_RELEASED,p.x,p.y);mouse(board,MouseEvent.MOUSE_PRESSED,q.x,q.y);}
            mouse(board,MouseEvent.MOUSE_RELEASED,q.x,q.y);return null;
        });
    }
    static void row(int n)throws Exception{
        edt(()->{
            Object played=field(panel,"playedLine",Object.class);JComponent canvas=field(played,"canvas",JComponent.class);
            Rectangle r=(Rectangle)call(canvas,"rowBounds",new Class<?>[]{int.class},n);
            canvas.scrollRectToVisible(r);
            mouse(canvas,MouseEvent.MOUSE_PRESSED,Math.min(80,canvas.getWidth()/2),r.y+r.height/2);
            mouse(canvas,MouseEvent.MOUSE_RELEASED,Math.min(80,canvas.getWidth()/2),r.y+r.height/2);
            mouse(canvas,MouseEvent.MOUSE_CLICKED,Math.min(80,canvas.getWidth()/2),r.y+r.height/2);
            return null;
        });
    }
    static void trace(String stage)throws Exception{
        Position shown=(Position)call(board,"getDisplayedPosition");
        List<Position> review=solution().isEmpty()?history():solution();
        Object played=field(panel,"playedLine",Object.class);
        System.out.println(stage+" | boardModel="+current()+" | paintPosition="+fen(shown)
                +" | playedLatest="+fen(history().getLast())+" | historySize="+history().size()
                +" | reviewIndex="+index()+" | reviewPosition="+fen(review.get(index()))
                +" | rowSelection="+field(played,"selected",Integer.class)
                +" | turn="+field(panel,"sideValue",JLabel.class).getText()
                +" | enabled="+board.isEnabled()+" | ready="+flag("endgameStudyReady")
                +" | givenUp="+flag("currentEndgameGivenUp")+" | preview="+board.isPreviewing()
                +" | status="+status());
    }
    static void snapshot(String stage)throws Exception{
        edt(()->{trace(stage);layout(window);render(window.getContentPane(),output.resolve(stage+".png"));return null;});
    }
    static void synchronizedView()throws Exception{
        List<Position> review=solution().isEmpty()?history():solution();
        require(current().equals(fen(review.get(index()))),"MODEL / REVIEW DIVERGED");
        require(current().equals(fen((Position)call(board,"getDisplayedPosition"))),"RENDER / MODEL DIVERGED");
        require(field(panel,"sideValue",JLabel.class).getText().startsWith(board.getPosition().getSideToMove()==main.java.chess.model.Color.WHITE?"White":"Black"),"TURN DIVERGED");
        require(field(field(panel,"playedLine",Object.class),"selected",Integer.class)==index(),"ROW DIVERGED");
        // Compare the actual painted board with a fresh rendering of the selected history position.
        ChessBoardPanel expected=new ChessBoardPanel(review.get(index()));expected.setSize(board.getSize());expected.setFlipped(board.isFlipped());
        BufferedImage actualImage=new BufferedImage(board.getWidth(),board.getHeight(),BufferedImage.TYPE_INT_RGB);
        BufferedImage expectedImage=new BufferedImage(board.getWidth(),board.getHeight(),BufferedImage.TYPE_INT_RGB);
        Graphics2D a=actualImage.createGraphics(),e=expectedImage.createGraphics();board.printAll(a);expected.printAll(e);a.dispose();e.dispose();
        require(Arrays.equals(actualImage.getRGB(0,0,board.getWidth(),board.getHeight(),null,0,board.getWidth()),expectedImage.getRGB(0,0,board.getWidth(),board.getHeight(),null,0,board.getWidth())),"PAINTED PIXELS DIFFER FROM REVIEW POSITION");
    }
    static void queenE7()throws Exception{
        Piece p=board.getPosition().getBoard().getPiece(new Square(4,6));
        require(p!=null&&p.type()==PieceType.QUEEN&&p.color()==main.java.chess.model.Color.BLACK,"Queen absent from e7");
        synchronizedView();
    }
    /** Reproduce the surrendered Qe7 attempt without copying anyone's personal progress. */
    private static void initializeFixture(EndgameStudyProgressStore store)throws Exception{
        var sequenceStore=new EndgameStudySequenceStore(store.file().resolveSibling("endgame-study-sequence.properties"));
        if(sequenceStore.exists()||Files.exists(store.file())){
            require(sequenceStore.exists()&&Files.exists(store.file()),"Both sequence and progress are required for an existing test profile");
            return;
        }
        var sequence=new EndgameStudySequence();
        var first=new EndgameStudySequence.Puzzle("KQK",ROOT,EndgameSettings.fixed(3));
        sequence.append("Mixed",first);
        sequence.append("Mixed",new EndgameStudySequence.Puzzle("KB-KB","KB6/8/k7/8/8/8/8/1b6 b - - 0 1",EndgameSettings.fixed(4)));
        sequenceStore.save(sequence);
        var progress=new EndgameStudyProgress();
        progress.recordAttempt(first.id());
        progress.enqueueReview(first.id());
        progress.setSession("Mixed",new EndgameStudyProgress.Session(0,List.of(ROOT),false,true,true));
        store.save(progress);
    }
    public static void main(String[] args)throws Exception{
        require(args.length==2&&(args[0].equals("before")||args[0].equals("after")),"Usage: Qe7GuiVerificationMain before|after OUTPUT_DIRECTORY");
        fixed=args[0].equals("after");output=Path.of(args[1]);Files.createDirectories(output);
        EndgameStudyProgressStore store=new EndgameStudyProgressStore();
        require(store.file().toString().contains("chess-qe7-audit"),"Isolated test profile required");
        initializeFixture(store);
        byte[] order=Files.readAllBytes(store.file().resolveSibling("endgame-study-sequence.properties"));
        int exit=0;
        try{
            edt(()->{
                window=new ChessWindow(FenCodec.parse(START));window.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                window.setExtendedState(JFrame.NORMAL);window.setBounds(25,25,1500,820);window.setAlwaysOnTop(true);window.setVisible(true);
                panel=field(window,"endgameStudyPanel",EndgameCurriculumPanel.class);board=field(window,"boardPanel",ChessBoardPanel.class);
                Consumer<Position> original=field(board,"positionChangeListener",Consumer.class);
                board.setPositionChangeListener(p->{
                    attempted++;
                    System.out.println("MOVE_CALLBACK proposed="+fen(p));
                    try{original.accept(p);System.out.println("MOVE_CALLBACK accepted="+history().stream().anyMatch(h->fen(h).equals(fen(p))));trace("callback-return");}
                    catch(Exception x){throw new RuntimeException(x);}
                });
                return null;
            });
            await("startup",()->field(window,"currentAnalysis",Object.class)!=null);
            click(window,"endgameButton");await("restored solution",()->!solution().isEmpty());
            require(edt(()->current().equals(ROOT)),"Wrong saved root");
            snapshot("01-solution-start");
            row(1);snapshot("02-click-Qe7");
            boolean rowWorked=edt(()->index()==1);
            System.out.println("ROW_CLICK_Qe7_WORKED="+rowWorked);
            if(!rowWorked)click(panel,"nextMoveButton");
            edt(()->{queenE7();return null;});snapshot("03-next-Qe7");
            // This is the user's solution-review position. The disabled canvas must not play Kb5.
            int beforeCallbacks=attempted;
            boardMove("c6","b5",false);
            boolean reviewStable=edt(()->current().equals(fen(solution().get(index()))));
            snapshot("04-board-input-during-review");
            System.out.println("READ_ONLY_REVIEW_STABLE="+reviewStable+"; callbacks="+(attempted-beforeCallbacks));
            if(fixed)require(rowWorked&&reviewStable&&attempted==beforeCallbacks,"Review input regression");
            else require(!rowWorked&&!reviewStable,"Expected baseline bugs were not reproduced");
            click(panel,"nextMoveButton");snapshot("05-next-after-review");
            click(panel,"previousMoveButton");edt(()->{queenE7();return null;});
            click(window,"resetPositionButton");await("reset",()->flag("endgameStudyReady")&&current().equals(ROOT));
            // Trace Qe7 in ordinary active study play, including its exact automatic reply.
            boardMove("a3","e7",false);
            await("accepted Qe7 and reply",()->history().size()==3);
            edt(()->{queenE7();return null;});snapshot("06-normal-Qe7");
            click(panel,"hintButton");await("hint complete",()->status().startsWith("Hint:"));
            Thread.sleep(1500);edt(()->{queenE7();return null;});snapshot("07-after-analysis-hint");
            click(panel,"previousMoveButton");edt(()->{require(index()==1,"Previous index");queenE7();return null;});snapshot("08-previous-Qe7");
            click(panel,"nextMoveButton");edt(()->{require(index()==2,"Next index");queenE7();return null;});snapshot("09-next-reply");
            click(window,"resetPositionButton");await("reset before drag",()->flag("endgameStudyReady")&&current().equals(ROOT));
            edt(()->{board.setFlipped(true);return null;});boardMove("a3","e7",true);
            await("flipped drag Qe7",()->history().size()==3);edt(()->{queenE7();board.setFlipped(false);return null;});snapshot("10-flipped-drag-Qe7");
            click(panel,"giveUpButton");await("revealed continuation",()->!solution().isEmpty());
            click(panel,"nextMoveButton");edt(()->{synchronizedView();return null;});snapshot("11-give-up-review");
            click(window,"resetPositionButton");await("final reset",()->flag("endgameStudyReady")&&current().equals(ROOT));
            click(panel,"nextButton");await("second saved puzzle",()->field(window,"loadedEndgameIndex",Integer.class)==1&&flag("endgameStudyReady"));
            edt(()->{synchronizedView();return null;});snapshot("12-next-saved-puzzle");
            require(Arrays.equals(order,Files.readAllBytes(store.file().resolveSibling("endgame-study-sequence.properties"))),"Saved order rewritten");
            System.out.println("AUTOMATED "+(fixed?"FIXED":"BASELINE")+" QE7 CHECKS PASSED; saved order unchanged");
        }catch(Throwable t){exit=1;t.printStackTrace();if(window!=null)snapshot("failure");}
        finally{if(window!=null)edt(()->{window.dispatchEvent(new WindowEvent(window,WindowEvent.WINDOW_CLOSING));return null;});}
        System.exit(exit);
    }
}

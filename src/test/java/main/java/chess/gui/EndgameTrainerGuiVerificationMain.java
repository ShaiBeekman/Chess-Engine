package main.java.chess.gui;

import main.java.chess.endgame.*;
import main.java.chess.model.*;
import main.java.chess.rules.MoveGenerator;
import main.java.chess.util.SanMoveFormatter;
import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.*;
import static main.java.chess.gui.BoardGeometryVerificationMain.*;

/** Visible Swing integration test. Real buttons and board mouse events use the production listeners.
 * Run first and restart in SEPARATE JVMs, with APPDATA set to an isolated chess-trainer-fix directory. */
public final class EndgameTrainerGuiVerificationMain {
    private static ChessWindow window;
    private static EndgameCurriculumPanel panel;
    private static Path output;
    private static final String[] FAMILIES = {"KQK", "KRK", "KPK", "KQRK"};
    private static final String[] ROOTS = {
            "7k/8/8/8/8/4K3/6Q1/8 w - - 0 1",
            "8/8/8/8/4k3/8/6r1/7K b - - 0 1",
            "8/4P3/3K4/8/8/8/8/7k w - - 0 1",
            "7k/8/8/8/8/4K3/6Q1/5R2 w - - 0 1"};
    public static void main(String[] args) throws Exception {
        output = Path.of(args[1]); Files.createDirectories(output);
        String phase = args[0];
        var progressStore = new EndgameStudyProgressStore();
        require(progressStore.file().toString().contains("chess-trainer-fix"), "Refusing to modify a personal profile");
        var sequenceStore = new EndgameStudySequenceStore(progressStore.file().resolveSibling("endgame-study-sequence.properties"));
        if (phase.equals("first")) {
            require(!sequenceStore.exists(), "Use a fresh test profile for first phase");
            var sequence = new EndgameStudySequence();
            for (int i = 0; i < ROOTS.length; i++) {
                var puzzle = new EndgameStudySequence.Puzzle(FAMILIES[i], ROOTS[i], EndgameSettings.fixed(i == 3 ? 4 : 3));
                sequence.append("Mixed", puzzle); sequence.append(FAMILIES[i], puzzle);
            }
            sequenceStore.save(sequence);
            progressStore.save(new EndgameStudyProgress());
        }
        int exit = 0;
        try {
            edt(() -> {
                window = new ChessWindow(FenCodec.parse(START));
                window.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                window.setExtendedState(JFrame.NORMAL);
                window.setBounds(20, 20, 1500, 820);
                window.setAlwaysOnTop(true);
                window.setVisible(true);
                panel = field(window, "endgameStudyPanel", EndgameCurriculumPanel.class);
                return null;
            });
            await("startup analysis", () -> field(window,"currentAnalysis",Object.class) != null);
            click(window, "endgameButton");
            if (phase.equals("first")) firstPhase(sequenceStore);
            else if (phase.equals("restart")) restartPhase(sequenceStore);
            else if (phase.equals("saved-collection-reset") || phase.equals("saved-collection-restart")) {
                String family = progressStore.load().selectedFamily();
                var snapshot = sequenceStore.load().snapshot();
                var collection = snapshot.get(family);
                require(collection != null && !collection.order().isEmpty(), "No saved collection to verify");
                List<String> roots = new ArrayList<>();
                for (int i = 0; i < collection.order().size(); i++) roots.add(collection.at(i).fen());
                Path collectionFile = progressStore.file().resolveSibling("endgame-study-sequence.properties");
                byte[] original = Files.readAllBytes(collectionFile);
                await("saved collection loaded", () -> (Integer)field(window,"loadedEndgameIndex",Object.class) >= 0);
                if (phase.equals("saved-collection-restart")) {
                    require(Files.readString(output.resolve("saved-collection-snapshot.txt")).equals(snapshot.toString()),
                            "Puzzle definitions/settings/order changed across restart");
                    ready(roots.getLast());
                    require(edt(() -> (Integer)field(window,"loadedEndgameIndex",Object.class) == roots.size()-1),
                            "Current collection index changed across restart");
                }
                confirmReset("resetProgressButton"); ready(roots.getFirst());
                for (int i = 1; i < roots.size(); i++) { click(panel,"nextButton"); ready(roots.get(i)); }
                click(window,"resetPositionButton"); ready(roots.getLast());
                require(edt(() -> history().size() == 1), "Individual reset did not restore its original root");
                require(sequenceStore.load().snapshot().equals(snapshot), "Reset/replay changed original puzzles or order");
                require(Arrays.equals(original, Files.readAllBytes(collectionFile)), "Reset/replay rewrote the collection file");
                Files.writeString(output.resolve("saved-collection-snapshot.txt"), snapshot.toString());
                screenshot(phase);
                System.out.println("Saved collection verified: " + roots.size() + " puzzles, same roots/settings/order; collection file unchanged");
            }
            else if (phase.equals("shuffle-before-close")) {
                await("initial saved puzzle", () -> (Integer)field(window,"loadedEndgameIndex",Object.class) >= 0);
                selectFamily("Mixed");
                await("Mixed collection", () -> "Mixed".equals(field(window,"loadedEndgameSelection",String.class)));
                edt(() -> { field(panel,"orderBox",JComboBox.class).setSelectedItem("Shuffle"); return null; });
                await("explicitly shuffled collection", () -> flag("endgameStudyReady"));
                List<String> shuffled = storedRoots(sequenceStore);
                Files.write(output.resolve("shuffled-restart.txt"), shuffled);
                Files.writeString(output.resolve("shuffled-collections.txt"), sequenceStore.load().snapshot().toString());
                click(panel,"nextButton"); ready(shuffled.get(1));
                screenshot("shuffled-before-close");
            }
            else if (phase.equals("shuffle-restart")) {
                List<String> shuffled = Files.readAllLines(output.resolve("shuffled-restart.txt"));
                ready(shuffled.get(1));
                require(edt(() -> (Integer)field(window,"loadedEndgameIndex",Object.class) == 1), "Shuffled index lost on restart");
                require(storedRoots(sequenceStore).equals(shuffled), "Restart reshuffled the collection");
                require(Files.readString(output.resolve("shuffled-collections.txt")).equals(sequenceStore.load().snapshot().toString()),
                        "Restart changed saved puzzle definitions/settings");
                Path collectionFile = progressStore.file().resolveSibling("endgame-study-sequence.properties");
                byte[] original = Files.readAllBytes(collectionFile);
                confirmReset("resetProgressButton"); ready(shuffled.getFirst());
                for (int i = 1; i < shuffled.size(); i++) { click(panel,"nextButton"); ready(shuffled.get(i)); }
                require(Arrays.equals(original, Files.readAllBytes(collectionFile)), "Reset/replay rewrote the collection");
                hint(); giveUp(); verifySolution();
                screenshot("shuffled-after-restart");
            }
            else if (phase.equals("rapid-help")) {
                await("initial saved puzzle", () -> (Integer)field(window,"loadedEndgameIndex",Object.class) >= 0);
                selectFamily("KQK");
                await("queen study", () -> (flag("endgameStudyReady") || flag("currentEndgameGivenUp")) && "KQK".equals(field(window,"currentEndgameFamily",String.class)));
                click(window,"resetPositionButton"); ready(ROOTS[0]);
                // Queue both real listeners before either worker can publish its result.
                edt(() -> {
                    field(panel,"giveUpButton",JButton.class).doClick();
                    field(panel,"hintButton",JButton.class).doClick();
                    return null;
                });
                await("solution after rapid Give Up / Hint", () -> !solution().isEmpty());
                verifySolution();
                require(edt(() -> !board().isEnabled() && flag("currentEndgameGivenUp")), "Rapid actions reopened the attempt");
                screenshot("rapid-help");
                click(window,"resetPositionButton"); ready(ROOTS[0]);
                playBestMove();
                String currentFen = edt(() -> FenCodec.toFen(board().getPosition()));
                int playedCount = edt(() -> history().size());
                edt(() -> {
                    field(panel,"giveUpButton",JButton.class).doClick();
                    field(panel,"previousMoveButton",JButton.class).doClick();
                    field(panel,"giveUpButton",JButton.class).doClick();
                    return null;
                });
                await("solution after surrender and move review", () -> !solution().isEmpty());
                verifySolution();
                require(edt(() -> history().size() == playedCount && FenCodec.toFen(board().getPosition()).equals(currentFen)),
                        "Solution did not return to its requested position or overwrote played moves");
                require(edt(() -> !board().isEnabled() && flag("currentEndgameGivenUp")), "Review reopened the attempt");
                hint();
                screenshot("give-up-during-review");
            }
            else if (phase.equals("given-up-restart")) {
                await("restored given-up solution", () -> !solution().isEmpty());
                require(edt(() -> !board().isEnabled() && !flag("endgameStudyReady") && flag("currentEndgameGivenUp")), "Given-up attempt became playable on restart");
                verifySolution(); hint(); screenshot("given-up-restored");
            } else if (phase.equals("completed")) {
                await("initial saved puzzle", () -> (Integer)field(window,"loadedEndgameIndex",Object.class) >= 0);
                selectFamily("KQRK"); ready(ROOTS[3]);
                click(window,"resetPositionButton"); ready(ROOTS[3]); playBestMove();
                require(edt(() -> !flag("endgameStudyReady") && flag("currentEndgameStudyClean")), "Unassisted mate was not completed cleanly");
                click(panel,"hintButton"); require(edt(() -> status().contains("no legal moves")), "Terminal hint feedback");
                click(panel,"giveUpButton"); require(edt(() -> status().contains("already finished") && !flag("currentEndgameGivenUp")), "Give Up altered a finished attempt");
                screenshot("completed-attempt");
            } else if (phase.equals("missing")) {
                await("missing asset message", () -> status().contains("could not be loaded"));
                click(panel,"giveUpButton");
                require(edt(() -> status().contains("unavailable")), "Missing proof gave no feedback");
                require(sequenceStore.load().size("Mixed") == ROOTS.length, "Missing assets lost saved puzzles");
                screenshot("missing-assets");
            } else throw new IllegalArgumentException(phase);
            System.out.println("VISIBLE GUI PHASE " + phase + " PASSED");
        } catch (Throwable failure) { failure.printStackTrace(); screenshot("failure-"+phase); exit = 1; }
        finally {
            if (window != null) edt(() -> { window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING)); return null; });
        }
        System.exit(exit);
    }
    private static void firstPhase(EndgameStudySequenceStore store) throws Exception {
        ready(ROOTS[0]); hint(); screenshot("hint-white");
        playBestMove(); hint();
        click(panel,"previousMoveButton"); hint();
        require(edt(() -> status().contains("Black to move")), "Review hint used root's side instead of displayed side");
        screenshot("hint-black-review");
        click(window,"resetPositionButton"); ready(ROOTS[0]);
        require(edt(() -> history().size() == 1 && flag("currentEndgameStudyClean")), "Individual reset did not restore a fresh starting position");
        hint(); giveUp(); verifySolution(); screenshot("give-up-solution");
        click(panel,"nextMoveButton");
        require(edt(() -> !board().isEnabled()), "Solution review reopened a surrendered attempt");
        click(window,"resetPositionButton"); ready(ROOTS[0]);
        for (int i = 1; i < ROOTS.length; i++) {
            click(panel,"nextButton"); ready(ROOTS[i]); hint(); giveUp(); verifySolution(); screenshot("solution-"+FAMILIES[i]);
        }
        require(store.load().size("Mixed") == ROOTS.length, "Saved replay appended duplicate puzzles");
        selectFamily("KQK"); ready(ROOTS[0]); hint();
        confirmReset("resetFamilyButton"); ready(ROOTS[0]); hint();
        click(panel,"nextButton");
        await("new generated KQK appended", () -> flag("endgameStudyReady") && (Integer)field(window,"loadedEndgameIndex",Object.class) == 1);
        String generated = edt(() -> FenCodec.toFen(board().getPosition()));
        require(!generated.equals(ROOTS[0]) && store.load().size("KQK") == 2, "Next did not append a distinct generated puzzle");
        hint();
        confirmReset("resetFamilyButton"); ready(ROOTS[0]);
        click(panel,"nextButton"); ready(generated);
        require(store.load().size("KQK") == 2, "Generated puzzle was regenerated instead of replayed");
        selectFamily("Mixed"); ready(ROOTS[3]);
        // Stale asynchronous results must not overwrite a reset or a newly selected family.
        edt(() -> { field(panel,"hintButton",JButton.class).doClick(); field(window,"resetPositionButton",JButton.class).doClick(); return null; });
        ready(ROOTS[3]); Thread.sleep(250);
        require(edt(() -> !status().startsWith("Hint:")), "Stale hint survived reset");
        var originals = store.load().snapshot();
        edt(() -> { field(panel,"orderBox",JComboBox.class).setSelectedItem("Shuffle"); return null; });
        await("shuffled first position", () -> flag("endgameStudyReady"));
        List<String> shuffled = storedRoots(store);
        confirmReset("resetProgressButton"); ready(shuffled.getFirst());
        for (int i = 1; i < shuffled.size(); i++) { click(panel,"nextButton"); ready(shuffled.get(i)); }
        require(storedRoots(store).equals(shuffled), "Reset reshuffled saved collection");
        edt(() -> { field(panel,"orderBox",JComboBox.class).setSelectedItem("Ordered"); return null; });
        ready(ROOTS[0]);
        for (var entry : originals.entrySet()) require(store.load().snapshot().get(entry.getKey()).originals().equals(entry.getValue().originals()), "Shuffle mutated original roots/settings");
        confirmReset("resetProgressButton"); ready(ROOTS[0]);
        click(panel,"nextButton"); ready(ROOTS[1]); hint(); playBestMove(); hint();
        Files.writeString(output.resolve("restart-fen.txt"), edt(() -> FenCodec.toFen(board().getPosition())));
        Files.writeString(output.resolve("all-collections.txt"), store.load().snapshot().toString());
        screenshot("before-close");
    }
    private static void restartPhase(EndgameStudySequenceStore store) throws Exception {
        ready(Files.readString(output.resolve("restart-fen.txt")));
        require(edt(() -> (Integer)field(window,"loadedEndgameIndex",Object.class) == 1 && history().size() == 3 && !flag("currentEndgameStudyClean")), "Index, played moves, or hint flag lost on restart");
        require(storedRoots(store).equals(List.of(ROOTS)), "Every original root/order must survive a process restart");
        require(Files.readString(output.resolve("all-collections.txt")).equals(store.load().snapshot().toString()), "Generated family sequence/settings lost across restart");
        hint(); giveUp(); verifySolution(); screenshot("after-restart");
        click(panel,"nextButton"); ready(ROOTS[2]); hint();
        click(panel,"nextButton"); ready(ROOTS[3]); giveUp(); verifySolution();
        confirmReset("resetProgressButton"); ready(ROOTS[0]);
        for (int i = 1; i < ROOTS.length; i++) { click(panel,"nextButton"); ready(ROOTS[i]); }
        // Missing loaded proof is reported synchronously and never silently ignores the button.
        edt(() -> { setField(window,"activeEndgameTablebase",null); return null; });
        click(panel,"hintButton"); require(edt(() -> status().contains("unavailable")), "Missing hint feedback");
        click(panel,"giveUpButton"); require(edt(() -> status().contains("unavailable")), "Missing solution feedback");
        click(window,"resetPositionButton"); ready(ROOTS[3]);
        // A worker-level unsupported position must report its failure as well.
        edt(() -> { board().setPosition(FenCodec.parse(START)); return null; });
        click(panel,"hintButton"); await("unsupported hint", () -> status().startsWith("Hint unavailable:"));
        click(panel,"giveUpButton"); await("unsupported solution", () -> status().contains("Solution unavailable:"));
        screenshot("unavailable-feedback");
        click(window,"resetPositionButton"); ready(ROOTS[3]);
        edt(() -> { field(panel,"giveUpButton",JButton.class).doClick(); field(panel,"familyBox",JComboBox.class).setSelectedItem("KQK"); return null; });
        ready(ROOTS[0]); Thread.sleep(250);
        require(edt(() -> solution().isEmpty() && !flag("currentEndgameGivenUp")), "Old solution leaked after family switch");
        hint(); giveUp(); verifySolution();
        require(store.load().size("Mixed") == ROOTS.length, "Unavailable analysis changed collection");
    }
    private static void hint() throws Exception {
        Position before = edt(() -> board().getPosition());
        @SuppressWarnings("unchecked") List<Move> best = edt(() -> ((ExactEndgameTablebase)call(window,"tablebaseForExactPosition",new Class<?>[]{Position.class},before)).bestMoves(before));
        click(panel,"hintButton"); await("visible hint", () -> status().startsWith("Hint:"));
        String text = edt(() -> status());
        require(best.stream().anyMatch(m -> text.contains(new SanMoveFormatter().format(before,m))), "Hint is not an exact legal move: "+text);
        require(text.contains(before.getSideToMove() == main.java.chess.model.Color.WHITE ? "White to move" : "Black to move"), "Wrong hint side");
        require(edt(() -> FenCodec.toFen(board().getPosition()).equals(FenCodec.toFen(before))), "Hint played a move");
        System.out.println(text);
    }
    private static void giveUp() throws Exception {
        click(panel,"giveUpButton"); await("visible solution", () -> !solution().isEmpty());
        require(edt(() -> flag("currentEndgameGivenUp") && !flag("endgameStudyReady") && !board().isEnabled()), "Give Up did not resolve the attempt");
        var p = edt(() -> field(window,"endgameProgress",EndgameStudyProgress.class));
        String id = edt(() -> field(window,"currentEndgameStudyId",String.class));
        require(p.get(id).status() == EndgameStudyProgress.Status.ATTEMPTED && !p.isCompleted(id), "Give Up falsely completed or mastered puzzle");
    }
    private static void verifySolution() throws Exception {
        List<Position> line = edt(() -> List.copyOf(solution()));
        require(line.size() > 1, "Empty solution");
        for (int i = 1; i < line.size(); i++) {
            Position before = line.get(i-1), after = line.get(i);
            ExactEndgameTablebase tb = edt(() -> (ExactEndgameTablebase)call(window,"tablebaseForExactPosition",new Class<?>[]{Position.class},before));
            require(tb.bestMoves(before).stream().anyMatch(m -> FenCodec.toFen(before.makeMove(m)).equals(FenCodec.toFen(after))), "Solution contains nonoptimal or illegal move");
        }
        require(new MoveGenerator().generateLegalMoves(line.getLast()).isEmpty(), "Solution did not reach terminal position");
        System.out.println("Exact solution verified: "+(line.size()-1)+" plies");
    }
    private static void playBestMove() throws Exception {
        Position before = edt(() -> board().getPosition());
        Move best = edt(() -> ((ExactEndgameTablebase)call(window,"tablebaseForExactPosition",new Class<?>[]{Position.class},before)).bestMoves(before).getFirst());
        require(best.promotion() == null, "This board-input fixture unexpectedly requires promotion");
        edt(() -> {
            ChessBoardPanel b = board();
            for (Square square : List.of(best.from(),best.to())) {
                int file = b.isFlipped() ? 7-square.file() : square.file();
                int rank = b.isFlipped() ? square.rank() : 7-square.rank();
                int x=(int)((file+.5)*b.getWidth()/8), y=(int)((rank+.5)*b.getHeight()/8);
                for (int kind : new int[]{MouseEvent.MOUSE_PRESSED,MouseEvent.MOUSE_RELEASED})
                    b.dispatchEvent(new MouseEvent(b,kind,System.currentTimeMillis(),kind==MouseEvent.MOUSE_PRESSED?InputEvent.BUTTON1_DOWN_MASK:0,x,y,1,false,MouseEvent.BUTTON1));
            }
            return null;
        });
        await("player move and exact reply", () -> history().size() >= 3 || history().size() == 2 && !flag("endgameStudyReady"));
    }
    private static void selectFamily(String family) throws Exception { edt(() -> { field(panel,"familyBox",JComboBox.class).setSelectedItem(family); return null; }); }
    private static void confirmReset(String name) throws Exception {
        SwingUtilities.invokeLater(() -> { try { field(panel,name,JButton.class).doClick(); } catch(Exception e) { throw new RuntimeException(e); } });
        for (int i=0;i<2;i++) {
            await("reset confirmation", () -> yesButton() != null);
            edt(() -> { yesButton().doClick(); return null; });
        }
    }
    private static JButton yesButton() {
        for (Window w : Window.getWindows()) if (w instanceof JDialog && w.isShowing()) {
            JButton button=findYes((Container)w); if(button!=null) return button;
        }
        return null;
    }
    private static JButton findYes(Container c) {
        for(Component child:c.getComponents()) {
            if(child instanceof JButton b && b.getText().equals("Yes"))return b;
            if(child instanceof Container nested) {JButton b=findYes(nested);if(b!=null)return b;}
        }
        return null;
    }
    private static void ready(String fen) throws Exception { await("saved puzzle "+fen, () -> (flag("endgameStudyReady") || flag("currentEndgameGivenUp")) && (Integer)field(window,"loadedEndgameIndex",Object.class) >= 0 && FenCodec.toFen(board().getPosition()).equals(fen)); }
    private static List<String> storedRoots(EndgameStudySequenceStore store) throws Exception { var s=store.load();var r=new ArrayList<String>();for(int i=0;i<s.size("Mixed");i++)r.add(s.at("Mixed",i).fen());return r; }
    private static void click(Object owner,String name)throws Exception{edt(()->{JButton b=field(owner,name,JButton.class);require(b.isShowing()&&b.isEnabled(),"Button unavailable: "+name);b.doClick();return null;});}
    private static ChessBoardPanel board()throws Exception{return field(window,"boardPanel",ChessBoardPanel.class);}
    @SuppressWarnings("unchecked") private static List<Position> history()throws Exception{return field(window,"gameHistory",List.class);}
    @SuppressWarnings("unchecked") private static List<Position> solution()throws Exception{return field(window,"endgameSolutionHistory",List.class);}
    private static String status()throws Exception{return field(panel,"statusValue",JTextArea.class).getText();}
    private static boolean flag(String name)throws Exception{return (Boolean)field(window,name,Object.class);}
    private static void screenshot(String name)throws Exception{if(window==null)return;edt(()->{layout(window);render(window.getContentPane(),output.resolve(name+".png"));return null;});}
    private static <T>T edt(Callable<T> task)throws Exception{FutureTask<T> f=new FutureTask<>(task);SwingUtilities.invokeAndWait(f);return f.get();}
    private static void await(String label,Callable<Boolean> condition)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(60);while(System.nanoTime()<end){if(edt(condition))return;Thread.sleep(75);}throw new AssertionError("Timed out: "+label+"; "+edt(()->status()));}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}

package main.java.chess.gui;

import main.java.chess.model.FenCodec;
import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import static main.java.chess.gui.BoardGeometryVerificationMain.*;

/** Realized native-frame transition gate. Run with an isolated APPDATA profile. */
public final class WindowStateVerificationMain {
    private static ChessWindow window;
    private static Path output;

    public static void main(String[] args) throws Exception {
        output = Path.of(args.length == 0 ? "target/endgame-redesign/window-state" : args[0]);
        Files.createDirectories(output);
        int exit = 0;
        try {
            edt(() -> {
                window = new ChessWindow(FenCodec.parse(START));
                window.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                setField(window, "selectedEndgameFamily", "KQK");
                window.setExtendedState(Frame.MAXIMIZED_BOTH);
                window.setVisible(true);
                return null;
            });
            settle();
            for (int[] screen : new int[][]{{1920,1080}, {1600,900}, {1366,768}}) {
                for (boolean maximized : new boolean[]{false, true}) {
                    if (args.length > 1 && args[1].equals("--maximized-only") && !maximized) continue;
                    final Rectangle requested = new Rectangle(0, 0, screen[0], screen[1] - 40);
                    edt(() -> { window.setExtendedState(Frame.NORMAL); return null; });
                    settle();
                    edt(() -> {
                        Rectangle usable = ChessWindow.usableWindowBounds(window.getGraphicsConfiguration());
                        int width = Math.min(usable.width - 40, Math.max(1240, screen[0] - 200));
                        int height = Math.min(usable.height - 30, Math.max(760, screen[1] - 180));
                        window.setBounds(usable.x + 20, usable.y + 15, width, height);
                        call(window, "applyTheme", new Class<?>[]{boolean.class}, screen[0] != 1600);
                        if (maximized) window.setExtendedState(Frame.MAXIMIZED_BOTH);
                        return null;
                    });
                    settle();
                    if (maximized) {
                        require(edt(() -> (window.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH), "Native maximize not established");
                        Rectangle bounds = edt(() -> window.getBounds());
                        Dimension minimum = edt(() -> window.getMinimumSize());
                        Dimension maximum = edt(() -> window.getMaximumSize());
                        edt(() -> { window.fitWindowToUsableBounds(requested, true); return null; });
                        settle();
                        require(edt(() -> minimum.equals(window.getMinimumSize()) && maximum.equals(window.getMaximumSize())), "Maximized fit changed native size hints at " + requested);
                        require(edt(() -> bounds.equals(window.getBounds())), "Maximized fit changed native frame bounds");
                    }
                    String scenario = screen[0] + "x" + screen[1] + (maximized ? "-maximized" : "-windowed");
                    step(scenario, "Home", "returnToEngineHome", false);
                    step(scenario, "Analysis-Setup", "beginPositionSetup", false);
                    step(scenario, "Setup-Cancel", "cancelPositionSetup", false);
                    step(scenario, "Analysis-Setup-again", "beginPositionSetup", false);
                    step(scenario, "Setup-Analyze", "commitPositionSetup", false);
                    step(scenario, "Analysis-Setup-home", "beginPositionSetup", false);
                    step(scenario, "Setup-Home", "returnToEngineHome", false);
                    step(scenario, "Analysis-Endgame", "showEndgameCurriculum", true);
                    step(scenario, "Endgame-Home", "returnToEngineHome", false);
                    step(scenario, "Analysis-Setup-endgame", "beginPositionSetup", false);
                    step(scenario, "Setup-Endgame", "showEndgameCurriculum", true);
                    step(scenario, "Endgame-Setup", "beginPositionSetup", false);
                    step(scenario, "Setup-Endgame-repeat", "showEndgameCurriculum", true);
                    verifySolverHost(scenario);
                    step(scenario, "Endgame-Home-repeat", "returnToEngineHome", false);
                    edt(() -> { render(window.getContentPane(), output.resolve(scenario + ".png")); return null; });
                }
            }
            verifyFullscreen();
            System.out.println("WINDOW STATE TRANSITION VERIFICATION PASSED");
        } catch (Throwable failure) { failure.printStackTrace(); exit = 1; }
        finally { if (window != null) edt(() -> { window.dispose(); return null; }); }
        System.exit(exit);
    }

    private static void step(String scenario, String name, String method, boolean study) throws Exception {
        int state = edt(() -> window.getExtendedState());
        Rectangle bounds = edt(() -> window.getBounds());
        GraphicsDevice device = edt(() -> window.getGraphicsConfiguration().getDevice());
        boolean fullscreen = edt(() -> device.getFullScreenWindow() == window);
        edt(() -> { call(window, method); call(window, "stopGuiExplorationChain"); return null; });
        if (study) awaitStudy();
        settle();
        edt(() -> {
            require(state == window.getExtendedState(), scenario + " " + name + " changed state " + state + " -> " + window.getExtendedState());
            require(bounds.equals(window.getBounds()), scenario + " " + name + " resized/recentered " + bounds + " -> " + window.getBounds());
            require(!fullscreen || device.getFullScreenWindow() == window, name + " left fullscreen");
            layout(window);
            require(geometryErrors(window).isEmpty(), scenario + " " + name + " " + geometryErrors(window));
            return null;
        });
        System.out.println(scenario + " " + name + " state=" + state + " bounds=" + bounds + " PASS");
    }

    private static void verifySolverHost(String scenario) throws Exception {
        int state = edt(() -> window.getExtendedState());
        Rectangle bounds = edt(() -> window.getBounds());
        // Production has no Solver route. Mount its existing component in the SAME
        // auxiliary card host only for geometry/state coverage; do not add a route.
        EndgameSolverPanel solver = edt(() -> {
            EndgameSolverPanel panel = new EndgameSolverPanel();
            panel.applyTheme(true);
            field(window, "auxiliaryAnalysisCards", JPanel.class).add(panel, "VERIFY_SOLVER");
            call(window, "showAuxiliaryAnalysisCard", new Class<?>[]{String.class}, "VERIFY_SOLVER");
            call(window, "refreshWorkspaceLayout");
            call(window, "resizeForCurrentMode");
            return panel;
        });
        settle();
        require(edt(() -> state == window.getExtendedState() && bounds.equals(window.getBounds())), "Curriculum-Solver host changed frame");
        edt(() -> { layout(window); render(window.getContentPane(), output.resolve(scenario + "-solver-host.png")); return null; });
        step(scenario, "Solver-host-Curriculum", "showEndgameCurriculum", true);
        edt(() -> { call(window, "showAuxiliaryAnalysisCard", new Class<?>[]{String.class}, "VERIFY_SOLVER"); return null; });
        step(scenario, "Solver-host-Home", "returnToEngineHome", false);
        edt(() -> { field(window, "auxiliaryAnalysisCards", JPanel.class).remove(solver); return null; });
        System.out.println(scenario + " Curriculum-Solver shared host PASS (legacy component; no production Solver route)");
    }

    private static void verifyFullscreen() throws Exception {
        edt(() -> { window.setExtendedState(Frame.NORMAL); return null; });
        settle();
        GraphicsDevice device = edt(() -> window.getGraphicsConfiguration().getDevice());
        Window original = edt(device::getFullScreenWindow);
        Rectangle previous = edt(() -> window.getBounds());
        try {
            edt(() -> { device.setFullScreenWindow(window); return null; });
            settle();
            require(edt(() -> device.getFullScreenWindow() == window), "AWT fullscreen not established");
            Rectangle bounds = edt(() -> window.getBounds());
            Dimension minimum = edt(() -> window.getMinimumSize());
            Dimension maximum = edt(() -> window.getMaximumSize());
            edt(() -> { window.fitWindowToUsableBounds(new Rectangle(0, 0, 1366, 728), true); return null; });
            settle();
            require(edt(() -> bounds.equals(window.getBounds()) && minimum.equals(window.getMinimumSize()) && maximum.equals(window.getMaximumSize())), "Fullscreen fitter changed native bounds/hints");
            step("fullscreen", "Home-Setup", "beginPositionSetup", false);
            step("fullscreen", "Setup-Home", "returnToEngineHome", false);
            step("fullscreen", "Home-Endgame", "showEndgameCurriculum", true);
            step("fullscreen", "Endgame-Setup", "beginPositionSetup", false);
            step("fullscreen", "Setup-Home-repeat", "returnToEngineHome", false);
        } finally {
            edt(() -> { device.setFullScreenWindow(original); window.setBounds(previous); return null; });
        }
    }

    private static void awaitStudy() throws Exception {
        long deadline = System.nanoTime() + 60_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (edt(() -> field(window, "endgameStudyReady", Boolean.class))) return;
            Thread.sleep(100);
        }
        throw new AssertionError("Exact KQK study did not become ready");
    }
    private static void settle() throws Exception { Thread.sleep(250); edt(() -> null); }
    private static <T> T edt(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        SwingUtilities.invokeAndWait(task);
        return task.get();
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}

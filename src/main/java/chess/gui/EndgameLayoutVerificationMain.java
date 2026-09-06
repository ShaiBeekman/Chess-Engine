package main.java.chess.gui;

import main.java.chess.endgame.EndgameSettings;
import main.java.chess.endgame.EndgameStudyProgress;
import main.java.chess.model.FenCodec;
import main.java.chess.model.Position;
import main.java.chess.model.PieceType;
import main.java.chess.endgame.ExactEndgameTablebase;
import main.java.chess.endgame.ThreePieceTablebaseService;
import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static main.java.chess.gui.BoardGeometryVerificationMain.*;

/** Explicit presentation/contract gate. Does not generate studies or write user progress. */
public final class EndgameLayoutVerificationMain {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args.length == 0 ? "target/endgame-redesign/layout" : args[0]);
        Files.createDirectories(output);
        ExactEndgameTablebase tablebase = ExactEndgameTablebase.of(new ThreePieceTablebaseService()
                .get(PieceType.QUEEN, main.java.chess.model.Color.WHITE));
        List<Position> history = new ArrayList<>();
        history.add(FenCodec.parse("7k/8/8/8/8/4K3/6Q1/8 w - - 0 1"));
        for (int i = 0; i < 6; i++) {
            Position position = history.getLast();
            history.add(position.makeMove(tablebase.bestMoves(position).getFirst()));
        }
        try {
            SwingUtilities.invokeAndWait(() -> {
                ChessWindow window = new ChessWindow(FenCodec.parse(START));
                try {
                    call(window, "stopGuiExplorationChain");
                    EndgameCurriculumPanel curriculum = field(window, "endgameStudyPanel", EndgameCurriculumPanel.class);
                    for (boolean dark : new boolean[]{true, false}) {
                        call(window, "applyTheme", new Class<?>[]{boolean.class}, dark);
                        showMode(window, "ENDGAME");
                        curriculum.setFamilies(List.of("Mixed", "KQK", "KRK", "KPK", "KQP-K"), "KQK");
                        curriculum.setPosition(history.getFirst(), EndgameSettings.fixed(3));
                        curriculum.setProvenMate(tablebase.probe(history.getFirst()).mateDistance());
                        curriculum.setPlayedLine(history, tablebase);
                        curriculum.setDisplayedPosition(history.get(3));
                        field(window, "boardPanel", ChessBoardPanel.class).setPosition(history.get(3));
                        curriculum.setProgress("KQK", EndgameStudyProgress.Status.ATTEMPTED,
                                24, 18, 12, 368452, 1248, 6, EndgameStudyProgress.StudyOrder.ORDERED);
                        curriculum.setMoveReviewState(3, 6);
                        curriculum.setStatus("Analysis is hidden while you solve.");
                        for (int[] screen : new int[][]{{1920,1080}, {1600,900}, {1366,768}, {1280,720}}) {
                            window.setBounds(0, 0, screen[0], screen[1]);
                            window.fitWindowToUsableBounds(new Rectangle(0, 0, screen[0], screen[1] - 40), false);
                            layout(window);
                            String name = "curriculum-" + screen[0] + "x" + screen[1] + (dark ? "-dark" : "-light");
                            field(curriculum, "playedLine", EndgamePlayedLine.class).revealSelection();
                            verifyActions(curriculum, "hintButton", "giveUpButton", "nextButton", "previousMoveButton", "nextMoveButton");
                            require(geometryErrors(window).isEmpty(), name + " board " + geometryErrors(window));
                            verifyText(curriculum);
                            verifyComposition(curriculum, screen[0] >= 1600);
                            render(window.getContentPane(), output.resolve(name + ".png"));
                            System.out.println(name + " " + curriculum.getSize() + " PASS");
                        }
                        // Long asynchronous rejection/hint feedback and large mixed-family counters.
                        curriculum.setProgress("Mixed", EndgameStudyProgress.Status.ATTEMPTED,
                                1234567, 2345678, 3456789, 123456789012L, 9876543210L, 9876543,
                                EndgameStudyProgress.StudyOrder.SHUFFLE);
                        curriculum.setStatus("Hint used — this attempt will not count as mastered. Return to the latest position to continue solving this exact endgame.");
                        layout(window);
                        verifyActions(curriculum, "hintButton", "giveUpButton", "nextButton");
                        render(curriculum, output.resolve("curriculum-long-feedback" + (dark ? "-dark" : "-light") + ".png"));
                        verifyStandalone(output, new EndgameCurriculumPanel(), dark, "narrow-curriculum");
                        verifyStandalone(output, new EndgameSolverPanel(), dark, "solver");
                        EndgameModeChooser chooser = new EndgameModeChooser(window, dark);
                        layout(chooser);
                        render(chooser.getContentPane(), output.resolve("chooser" + (dark ? "-dark" : "-light") + ".png"));
                        chooser.dispose();
                    }
                    // Theme changes at a fixed large size must preserve adapted fonts and bounds.
                    curriculum.setSize(1100, 800);
                    curriculum.applyTheme(true);
                    layout(curriculum);
                    Font comboFont = field(curriculum, "familyBox", JComboBox.class).getFont();
                    Rectangle actionBounds = field(curriculum, "nextButton", JButton.class).getBounds();
                    curriculum.applyTheme(false);
                    layout(curriculum);
                    require(comboFont.equals(field(curriculum, "familyBox", JComboBox.class).getFont()), "Theme reset responsive font");
                    require(actionBounds.equals(field(curriculum, "nextButton", JButton.class).getBounds()), "Theme changed action bounds");
                    verifyPlayedHistory(tablebase, history);
                    verifyCurriculumContract();
                    verifySolverContract();
                } catch (Exception exception) { throw new RuntimeException(exception); }
                finally { window.dispose(); }
            });
            System.out.println("ENDGAME LAYOUT AND UI CONTRACT VERIFICATION PASSED");
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
        System.exit(0);
    }

    private static void verifyComposition(EndgameCurriculumPanel panel, boolean large) {
        JPanel study = namedPanel(panel, "currentStudyCard"), path = namedPanel(panel, "playedLineCard");
        require(study.getHeight() <= study.getPreferredSize().height + 35, "Study regained empty vertical space");
        if (large) require(path.getHeight() >= 250, "Played line is compressed: " + path.getHeight());
        EndgamePlayedLine line = findPlayedLine(path);
        JScrollPane scroll = (JScrollPane) line.getComponent(0);
        require(scroll.getViewport().getHeight() >= 54, "Review cannot show a full move row");
        System.out.println("Composition: study=" + study.getHeight() + " path=" + path.getHeight());
    }

    private static EndgamePlayedLine findPlayedLine(Container root) {
        for (Component child : root.getComponents()) {
            if (child instanceof EndgamePlayedLine line) return line;
            if (child instanceof Container nested) {
                EndgamePlayedLine line = findPlayedLine(nested);
                if (line != null) return line;
            }
        }
        return null;
    }

    private static JPanel namedPanel(Container root, String name) {
        for (Component child : root.getComponents()) {
            if (child instanceof JPanel panel && name.equals(panel.getName())) return panel;
            if (child instanceof Container nested) {
                JPanel match = namedPanel(nested, name);
                if (match != null) return match;
            }
        }
        return null;
    }

    private static void verifyPlayedHistory(ExactEndgameTablebase tablebase, List<Position> history) throws Exception {
        EndgameCurriculumPanel panel = new EndgameCurriculumPanel();
        EndgamePlayedLine path = field(panel, "playedLine", EndgamePlayedLine.class);
        panel.setPlayedLine(history.subList(0, 1), tablebase);
        panel.setMoveReviewState(0, 0);
        require(field(path, "steps", List.class).isEmpty(), "Unplayed solution leaked at START");
        panel.setPlayedLine(history.subList(0, 3), tablebase);
        List<?> steps = field(path, "steps", List.class);
        require(steps.size() == 2, "Unplayed continuation leaked");
        for (int i = 0; i < steps.size(); i++) {
            EndgamePlayedLine.Step step = (EndgamePlayedLine.Step) steps.get(i);
            require(!step.san().equals("Played move"), "Played SAN missing");
            require(step.result().equals("Exact WIN / White"), "Result perspective alternated: " + step.result());
            require(step.distance().equals("DTM " + tablebase.probe(history.get(i)).mateDistance() + " \u2192 "
                    + tablebase.probe(history.get(i + 1)).mateDistance() + " plies"), "Incorrect DTM transition");
        }
        panel.setMoveReviewState(1, 2);
        require(steps.size() == 2, "Review mutated history");
        panel.setMoveReviewState(0, 0);
        require(steps.isEmpty(), "New study retained old moves");
        panel.setPlayedLine(history.subList(0, 2), null);
        EndgamePlayedLine.Step unavailable = (EndgamePlayedLine.Step) steps.getFirst();
        require(unavailable.result().isEmpty() && unavailable.distance().isEmpty(), "Missing proof invented telemetry");
        System.out.println("Played history: SAN, exact perspective/DTM, no future moves, reset and unavailable proof PASS");
    }

    private static void verifyStandalone(Path output, JPanel panel, boolean dark, String name) throws Exception {
        if (panel instanceof EndgameCurriculumPanel p) { p.applyTheme(dark); p.setFamilies(List.of("Mixed", "KQK"), "KQK"); p.setProvenMate(9); }
        if (panel instanceof EndgameSolverPanel p) {
            p.applyTheme(dark);
            p.setFamilies(List.of("Random", "KQK", "KRK"), "KQK");
            p.setPosition(FenCodec.parse("8/8/8/8/8/4K3/6Q1/7k w - - 0 1"), EndgameSettings.fixed(3));
            p.setProvenMate(9);
            p.setPracticeMode(true);
            p.setPracticeStrength(75);
        }
        for (int width : new int[]{1000, 700, 460}) {
            panel.setSize(width, 600);
            layout(panel);
            verifyActions(panel, "hintButton", "giveUpButton", "nextButton");
            render(panel, output.resolve(name + "-" + width + (dark ? "-dark" : "-light") + ".png"));
            System.out.println(name + " width=" + width + (dark ? " dark" : " light") + " PASS");
        }
    }

    private static void verifyActions(JPanel panel, String... names) throws Exception {
        for (String name : names) {
            JButton button = field(panel, name, JButton.class);
            Rectangle bounds = SwingUtilities.convertRectangle(button.getParent(), button.getBounds(), panel);
            require(new Rectangle(panel.getSize()).contains(bounds) && bounds.height >= 30, name + " clipped: " + bounds);
            Component child = button;
            while (child != panel) {
                Container parent = child.getParent();
                if (parent instanceof JViewport) {
                    require(new Rectangle(parent.getSize()).contains(child.getBounds())
                            || !name.equals("previousMoveButton") && !name.equals("nextMoveButton"), name + " clipped by scroll viewport");
                }
                child = parent;
            }
            require(button.getFontMetrics(button.getFont()).stringWidth(button.getText()) <= button.getWidth() - 16,
                    name + " text clipped at width " + button.getWidth());
        }
    }

    private static void verifyText(Container parent) {
        for (Component component : parent.getComponents()) {
            if (!component.isVisible()) continue;
            if (component instanceof JLabel label && !(label instanceof EndgameWorkspace.TelemetryLabel) && label.getIcon() == null && label.getText() != null) {
                int available = label.getWidth() - label.getInsets().left - label.getInsets().right;
                require(label.getFontMetrics(label.getFont()).stringWidth(label.getText()) <= available,
                        "Clipped label: " + label.getText() + " width=" + available);
            }
            if (component instanceof Container child && !(component instanceof JComboBox<?>)) verifyText(child);
        }
    }

    private static void verifyCurriculumContract() throws Exception {
        EndgameCurriculumPanel p = new EndgameCurriculumPanel();
        List<String> events = new ArrayList<>();
        p.setFamilyListener(f -> events.add("family:" + f));
        p.setOrderListener(o -> events.add("order:" + o));
        p.setFamilies(List.of("Mixed", "KQK", "KRK"), "KQK");
        p.setStudyOrder(EndgameStudyProgress.StudyOrder.SHUFFLE);
        require(events.isEmpty(), "Programmatic family/order fired user callbacks");
        field(p, "familyBox", JComboBox.class).setSelectedItem("KRK");
        field(p, "orderBox", JComboBox.class).setSelectedIndex(0);
        require(events.equals(List.of("family:KRK", "order:ORDERED")), "Selection callbacks " + events);
        p.setHintListener(() -> events.add("hint"));
        p.setGiveUpListener(() -> events.add("give-up"));
        p.setNextListener(() -> events.add("next"));
        p.setPreviousMoveListener(() -> events.add("previous-move"));
        p.setNextMoveListener(() -> events.add("next-move"));
        p.setResetFamilyListener(() -> events.add("reset-family"));
        p.setResetProgressListener(() -> events.add("reset-all"));
        p.setLoadingTablebase("KQK");
        require(!field(p, "hintButton", JButton.class).isEnabled(), "Hint enabled while loading");
        p.setProving();
        require(!field(p, "hintButton", JButton.class).isEnabled(), "Hint enabled while proving");
        p.setProvenMate(11);
        require(field(p, "hintButton", JButton.class).isEnabled(), "Hint disabled after proof");
        p.setMoveReviewState(0, 4);
        require(!field(p, "previousMoveButton", JButton.class).isEnabled(), "Review before first move");
        p.setMoveReviewState(4, 4);
        require(!field(p, "nextMoveButton", JButton.class).isEnabled(), "Review beyond latest");
        p.setMoveReviewState(2, 4);
        events.clear();
        for (String name : new String[]{"hintButton", "giveUpButton", "nextButton", "previousMoveButton", "nextMoveButton", "resetFamilyButton", "resetProgressButton"}) {
            field(p, name, JButton.class).doClick();
        }
        require(events.equals(List.of("hint", "give-up", "next", "previous-move", "next-move", "reset-family", "reset-all")), "Action callback mapping " + events);
        p.setRejected("Proof rejected");
        require(!field(p, "hintButton", JButton.class).isEnabled(), "Rejected hint enabled");
        System.out.println("Curriculum callbacks, programmatic updates, proof/hint states, review bounds PASS");
    }

    private static void verifySolverContract() throws Exception {
        EndgameSolverPanel p = new EndgameSolverPanel();
        List<String> events = new ArrayList<>();
        p.setPracticeModeListener(value -> events.add("practice:" + value));
        p.setPracticeStrengthListener(value -> events.add("strength:" + value));
        p.setPracticeMode(true);
        require(p.isPracticeMode() && events.isEmpty(), "Programmatic practice callback");
        p.setPracticeStrength(-5);
        require(p.getPracticeStrength() == 0, "Strength lower clamp");
        p.setPracticeStrength(110);
        require(p.getPracticeStrength() == 100, "Strength upper clamp");
        field(p, "practiceMode", JCheckBox.class).doClick();
        require(events.contains("practice:false"), "Practice user callback");
        p.setPracticeMode(false);
        require(!field(p, "practiceStrength", JSlider.class).isEnabled(), "Practice slider state");
        p.setLoadingTablebase("KQK");
        require(!field(p, "hintButton", JButton.class).isEnabled(), "Solver loading hint");
        p.setProvenMate(9);
        require(field(p, "hintButton", JButton.class).isEnabled(), "Solver proof hint");
        p.setHintListener(() -> events.add("hint"));
        p.setGiveUpListener(() -> events.add("give-up"));
        p.setNextListener(() -> events.add("next"));
        p.setNewListener(() -> events.add("options"));
        p.setResetProgressListener(() -> events.add("reset"));
        events.clear();
        for (String name : new String[]{"hintButton", "giveUpButton", "nextButton", "newButton", "resetProgressButton"}) field(p, name, JButton.class).doClick();
        require(events.equals(List.of("hint", "give-up", "next", "options", "reset")), "Solver action mapping " + events);
        System.out.println("Solver practice, strength, hint and action contracts PASS");
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}

package main.java.chess.gui;

import main.java.chess.endgame.ExactEndgameTablebase;
import main.java.chess.model.Position;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Read-only rendering of already committed curriculum positions. No solution search or preview. */
final class EndgamePlayedLine extends JPanel {
    record Step(String san, String actor, String result, String distance) { }
    private final List<Position> positions = new ArrayList<>();
    private final List<Step> steps = new ArrayList<>();
    private final PathCanvas canvas = new PathCanvas();
    private final JScrollPane scroll = EndgameWorkspace.scroll(canvas);
    private EndgameWorkspace.Palette palette = EndgameWorkspace.Palette.of(true);
    private int selected;

    EndgamePlayedLine() {
        super(new BorderLayout());
        setOpaque(false);
        scroll.setPreferredSize(new Dimension(0, 36));
        add(scroll);
        canvas.getAccessibleContext().setAccessibleName("Committed exact move path");
    }

    void applyTheme(boolean dark) { palette = EndgameWorkspace.Palette.of(dark); repaint(); }

    void setHistory(List<Position> history, ExactEndgameTablebase tablebase) {
        // Preserve snapshots across material changes and avoid probing old positions on navigation.
        int common = 0;
        while (common < positions.size() && common < history.size()
                && positions.get(common) == history.get(common)) common++;
        if (common < positions.size()) {
            positions.subList(common, positions.size()).clear();
            steps.subList(Math.max(0, common - 1), steps.size()).clear();
        }
        if (positions.isEmpty() && !history.isEmpty()) positions.add(history.get(0));
        for (int i = positions.size(); i < history.size(); i++) {
            Position before = history.get(i - 1), after = history.get(i);
            String san = playedSan(before, after);
            String result = "", distance = "";
            if (tablebase != null) {
                ExactEndgameTablebase.Probe a = tablebase.probe(before), b = tablebase.probe(after);
                if (b.outcome() != ExactEndgameTablebase.Outcome.UNSUPPORTED) {
                    // Both sides are reported from the trainee's (initial side-to-move) perspective.
                    ExactEndgameTablebase.Outcome outcome = b.outcome();
                    if (after.getSideToMove() != history.get(0).getSideToMove()) {
                        if (outcome == ExactEndgameTablebase.Outcome.WIN) outcome = ExactEndgameTablebase.Outcome.LOSS;
                        else if (outcome == ExactEndgameTablebase.Outcome.LOSS) outcome = ExactEndgameTablebase.Outcome.WIN;
                    }
                    result = "Exact " + outcome + " / " + side(history.get(0));
                    if (a.hasForcedMate() && b.hasForcedMate() && a.mateDistance() >= 0 && b.mateDistance() >= 0)
                        distance = "DTM " + a.mateDistance() + " \u2192 " + b.mateDistance() + " plies";
                }
            }
            steps.add(new Step(san, side(before), result, distance));
            positions.add(after);
        }
        updateAccessibleText();
        canvas.revalidate();
        canvas.repaint();
    }

    private static String side(Position position) {
        return position.getSideToMove() == main.java.chess.model.Color.WHITE ? "White" : "Black";
    }

    private static String playedSan(Position before, Position after) {
        var generator = new main.java.chess.rules.MoveGenerator();
        var formatter = new main.java.chess.util.SanMoveFormatter();
        for (var move : generator.generateLegalMoves(before)) {
            if (before.makeMove(move).createPositionKey().equals(after.createPositionKey())) return formatter.format(before, move);
        }
        return "Played move";
    }

    void select(int index, int lastIndex) {
        selected = index;
        if (lastIndex == 0) { positions.clear(); steps.clear(); }
        updateAccessibleText();
        canvas.revalidate();
        canvas.repaint();
        SwingUtilities.invokeLater(() -> {
            if (selected == index) revealSelection();
        });
    }

    void revealSelection() {
        if (selected <= steps.size()) canvas.scrollRectToVisible(canvas.rowBounds(selected));
    }

    private void updateAccessibleText() {
        StringBuilder text = new StringBuilder("START");
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            text.append("; ").append(i + 1).append(" ").append(step.actor()).append(" ")
                    .append(step.san()).append(" OPTIMAL ").append(step.result()).append(" ").append(step.distance());
        }
        text.append("; Selected move ").append(selected);
        canvas.getAccessibleContext().setAccessibleDescription(text.toString());
    }

    private final class PathCanvas extends JPanel implements Scrollable {
        PathCanvas() { setOpaque(false); }
        private float scale() {
            JComponent owner = (JComponent) SwingUtilities.getAncestorOfClass(EndgameCurriculumPanel.class, this);
            Float scale = owner == null ? null : (Float) owner.getClientProperty("endgameScale");
            return scale == null ? 1 : scale;
        }
        private int px(int value) { return Math.round(value * scale()); }
        private int rowHeight() { return px(54); }
        Rectangle rowBounds(int index) {
            return index == 0 ? new Rectangle(0, 0, getWidth(), px(24))
                    : new Rectangle(0, px(24) + (index - 1) * rowHeight(), getWidth(), rowHeight());
        }
        @Override public Dimension getPreferredSize() {
            return new Dimension(0, Math.max(px(95), px(24) + steps.size() * rowHeight()));
        }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int rail = px(10), left = px(30);
            g.setColor(palette.border());
            if (!steps.isEmpty()) g.drawLine(rail, px(14), rail, px(24) + (steps.size() - 1) * rowHeight() + px(16));
            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, px(12)));
            g.setColor(selected == 0 ? palette.accent() : palette.secondary());
            g.drawRect(rail - px(3), px(7), px(6), px(6));
            g.drawString("START", left, px(15));
            if (steps.isEmpty()) {
                g.setColor(palette.secondary());
                g.setFont(new Font("Segoe UI", Font.PLAIN, px(13)));
                g.drawString("Your played moves will appear here.", left, px(49));
                g.drawString("Play a move to begin the exact path.", left, px(70));
            }
            for (int i = 0; i < steps.size(); i++) {
                Rectangle bounds = rowBounds(i + 1);
                if (!g.getClipBounds().intersects(bounds)) continue;
                Step step = steps.get(i);
                int y = bounds.y;
                boolean active = selected == i + 1;
                g.setColor(palette.border());
                g.drawLine(rail, y + px(16), left - px(7), y + px(16));
                if (active) {
                    g.setColor(palette.control());
                    g.fillRect(left - px(5), y + 1, getWidth() - left + px(4), rowHeight() - 3);
                    g.setColor(palette.border());
                    g.drawRect(left - px(5), y + 1, getWidth() - left + px(3), rowHeight() - 3);
                }
                g.setColor(active ? palette.accent() : palette.secondary());
                g.fillRect(rail - px(2), y + px(14), px(4), px(4));
                g.setFont(new Font("Segoe UI", Font.BOLD, px(14)));
                g.drawString((i + 1) + "  " + step.san(), left + px(4), y + px(19));
                g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, px(11)));
                g.setColor(palette.secondary());
                g.drawString(step.actor().toUpperCase() + " / OPTIMAL" + (active ? " / SELECTED" : ""), left + px(125), y + px(19));
                g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, px(12)));
                g.drawString(step.result(), left + px(4), y + px(34));
                g.drawString(step.distance(), left + px(4), y + px(49));
            }
            g.dispose();
        }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) { return px(20); }
        @Override public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) { return Math.max(px(20), r.height - rowHeight()); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return getParent() instanceof JViewport v && v.getHeight() >= getPreferredSize().height; }
    }
}

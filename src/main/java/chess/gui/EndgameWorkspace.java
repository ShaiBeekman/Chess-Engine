package main.java.chess.gui;

import javax.swing.*;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicCheckBoxUI;
import javax.swing.plaf.basic.BasicSliderUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicProgressBarUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.font.TextAttribute;
import java.util.Map;

/** Local presentation primitives using Setup's surfaces/controls and Analysis's telemetry. */
final class EndgameWorkspace {
    private EndgameWorkspace() { }

    record Palette(Color background, Color card, Color primary, Color secondary,
                   Color border, Color control, Color accent) {
        static Palette of(boolean dark) {
            return dark
                    ? new Palette(new Color(12,20,26), new Color(14,23,30), new Color(241,245,250),
                        new Color(177,199,219), new Color(32,51,64), new Color(13,23,30), new Color(63,151,255))
                    : new Palette(new Color(242,245,248), Color.WHITE, new Color(31,43,55),
                        new Color(77,99,118), new Color(202,213,224), new Color(248,250,252), new Color(0,117,172));
        }
    }

    /** Counters remain complete, including large Mixed-family totals and cursors. */
    static final class TelemetryLabel extends JLabel {
        TelemetryLabel(String text) { super(text); }
        private java.util.List<String> lines(int width) {
            java.util.List<String> lines = new java.util.ArrayList<>();
            String text = getText() == null ? "" : getText();
            FontMetrics metrics = getFontMetrics(getFont());
            String line = "";
            for (String word : text.split(" +")) {
                String next = line.isEmpty() ? word : line + " " + word;
                if (!line.isEmpty() && metrics.stringWidth(next) > width) {
                    lines.add(line);
                    line = word;
                } else line = next;
            }
            lines.add(line);
            return lines;
        }
        @Override public Dimension getPreferredSize() {
            Dimension natural = super.getPreferredSize();
            int width = getWidth() > 0 ? getWidth() : natural.width;
            return new Dimension(natural.width, Math.max(natural.height,
                    lines(Math.max(1, width)).size() * getFontMetrics(getFont()).getHeight()));
        }
        @Override public Dimension getMinimumSize() { return new Dimension(0, getPreferredSize().height); }
        @Override public void setBounds(int x, int y, int width, int height) {
            boolean changed = width != getWidth();
            super.setBounds(x, y, width, height);
            if (changed) revalidate();
        }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(getFont());
            g.setColor(getForeground());
            FontMetrics metrics = g.getFontMetrics();
            int y = metrics.getAscent();
            for (String line : lines(Math.max(1, getWidth()))) {
                g.drawString(line, 0, y);
                y += metrics.getHeight();
            }
            g.dispose();
        }
    }

    /** Increase legibility on large dashboards without shrinking compact-mode text. */
    static void adapt(JPanel panel) {
        float scale = Math.min(1.35f, Math.max(1f, Math.min(panel.getWidth() / 850f, panel.getHeight() / 660f)));
        Float previous = (Float) panel.getClientProperty("endgameScale");
        if (previous != null && Math.abs(previous - scale) < 0.015f) return;
        panel.putClientProperty("endgameScale", scale);
        adaptChildren(panel, scale);
    }

    private static void adaptChildren(Container parent, float scale) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JComponent component) {
                if (component instanceof JLabel || component instanceof AbstractButton
                        || component instanceof JTextArea || component instanceof JComboBox<?>) {
                    Font original = (Font) component.getClientProperty("endgameBaseFont");
                    if (original == null) {
                        original = component.getFont();
                        component.putClientProperty("endgameBaseFont", original);
                    }
                    if (original != null) component.setFont(original.deriveFont(original.getSize2D() * scale));
                }
                if (component.isPreferredSizeSet()) {
                    Dimension original = (Dimension) component.getClientProperty("endgameBaseSize");
                    if (original == null) {
                        original = component.getPreferredSize();
                        component.putClientProperty("endgameBaseSize", original);
                    }
                    component.setPreferredSize(new Dimension(Math.round(original.width * scale), Math.round(original.height * scale)));
                }
            }
            if (child instanceof Container nested && !(child instanceof JComboBox<?>) && !(child instanceof JScrollBar)) adaptChildren(nested, scale);
        }
    }

    static JPanel transparent(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    static JPanel card(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.putClientProperty("endgameCard", true);
        return panel;
    }

    static JLabel label(String text, int size, boolean bold, boolean muted) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", bold ? Font.BOLD : Font.PLAIN, size));
        label.putClientProperty("secondary", muted);
        return label;
    }

    static JLabel section(String text) {
        JLabel label = label(text, 12, false, true);
        label.setFont(label.getFont().deriveFont(Map.of(TextAttribute.TRACKING, 0.055f)));
        return label;
    }

    static void telemetry(JComponent component, boolean accent) {
        component.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        component.putClientProperty("accent", accent);
        component.putClientProperty("secondary", !accent);
    }

    static JTextArea wrapping(String text) {
        JTextArea area = new JTextArea(text, 2, 1);
        area.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setFocusable(false);
        area.setOpaque(false);
        area.setBorder(null);
        return area;
    }

    static JPanel heading(String title, String subtitle, String mode) {
        JPanel heading = transparent(new BorderLayout(12, 5));
        JLabel name = label(title, 28, true, false);
        name.setFont(name.getFont().deriveFont(Map.of(TextAttribute.TRACKING, 0.055f)));
        JPanel titleRow = transparent(new BorderLayout(10, 0));
        titleRow.add(name, BorderLayout.CENTER);
        JLabel badge = section(mode.endsWith("CURRICULUM") ? "CURRICULUM / EXACT" : "SOLVER / EXACT");
        badge.putClientProperty("accent", true);
        titleRow.add(badge, BorderLayout.EAST);
        heading.add(titleRow, BorderLayout.NORTH);
        heading.add(label(subtitle, 13, false, true), BorderLayout.CENTER);
        return heading;
    }

    static JPanel stack(int gap, Component... components) {
        JPanel panel = transparent(new GridBagLayout());
        GridBagConstraints row = new GridBagConstraints();
        row.gridx = 0;
        row.weightx = 1;
        row.fill = GridBagConstraints.HORIZONTAL;
        row.anchor = GridBagConstraints.NORTHWEST;
        for (int i = 0; i < components.length; i++) {
            row.gridy = i;
            row.insets = new Insets(i == 0 ? 0 : gap, 0, 0, 0);
            panel.add(components[i], row);
        }
        return panel;
    }

    static JPanel top(JComponent content) {
        JPanel panel = transparent(new BorderLayout());
        panel.add(content, BorderLayout.NORTH);
        return panel;
    }

    static JButton button(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.BOLD, 13));
        button.setPreferredSize(new Dimension(button.getPreferredSize().width + 12, 34));
        button.setMinimumSize(new Dimension(0, 32));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setRolloverEnabled(true);
        return button;
    }

    static JPanel actions(JButton first, JButton second, JButton primary) {
        primary.putClientProperty("primaryAction", true);
        JPanel actions = transparent(new GridBagLayout());
        GridBagConstraints cell = new GridBagConstraints();
        cell.fill = GridBagConstraints.BOTH;
        cell.weighty = 1;
        JButton[] buttons = {first, second, primary};
        for (int i = 0; i < buttons.length; i++) {
            cell.gridx = i;
            cell.weightx = i == 2 ? 0.5 : 0.25;
            cell.insets = new Insets(0, i == 0 ? 0 : 8, 0, 0);
            actions.add(buttons[i], cell);
        }
        actions.setPreferredSize(new Dimension(380, 40));
        JPanel card = card(new BorderLayout(0, 8));
        card.add(section("TRAINING ACTIONS"), BorderLayout.NORTH);
        card.add(actions, BorderLayout.CENTER);
        return card;
    }

    /** A two-column instrument on wide hosts; a vertical stack on narrow hosts. */
    static final class Pair extends JPanel {
        private final JComponent first, second;
        Pair(JComponent first, JComponent second) {
            super(null);
            this.first = first;
            this.second = second;
            setOpaque(false);
            add(first);
            add(second);
        }
        private boolean wide() { return getWidth() == 0 || getWidth() >= 620; }
        @Override public Dimension getMinimumSize() { return new Dimension(0, getPreferredSize().height); }
        @Override public Dimension getPreferredSize() {
            int a = first.getPreferredSize().height, b = second.getPreferredSize().height;
            return new Dimension(760, wide() ? Math.max(a, b) : a + b + 8);
        }
        @Override public void doLayout() {
            int width = getWidth(), height = getHeight();
            if (wide()) {
                int left = (width - 8) * 54 / 100;
                first.setBounds(0, 0, left, height);
                second.setBounds(left + 8, 0, width - left - 8, height);
            } else {
                int firstHeight = first.getPreferredSize().height;
                first.setBounds(0, 0, width, firstHeight);
                second.setBounds(0, firstHeight + 8, width, Math.max(0, height - firstHeight - 8));
            }
        }
    }

    /** Three adjacent counters, stacking only when the actual text cannot fit. */
    static final class TelemetryCells extends JPanel {
        private final JPanel[] cells = new JPanel[3];
        TelemetryCells(JLabel complete, JLabel inProgress, JLabel review) {
            super(null);
            setOpaque(false);
            JLabel[] values = {complete, inProgress, review};
            String[] titles = {"COMPLETE", "IN PROGRESS", "REVIEW"};
            for (int i = 0; i < cells.length; i++) {
                values[i].setFont(new Font(Font.MONOSPACED, Font.BOLD, 18));
                values[i].putClientProperty("secondary", false);
                values[i].getAccessibleContext().setAccessibleName(titles[i]);
                cells[i] = stack(2, section(titles[i]), values[i]);
                add(cells[i]);
            }
        }
        @Override public Dimension getMinimumSize() { return new Dimension(0, getPreferredSize().height); }
        private boolean wide() {
            int needed = 0;
            for (JPanel cell : cells) needed = Math.max(needed, cell.getPreferredSize().width);
            return getWidth() == 0 || getWidth() >= needed * 3 + 16;
        }
        @Override public Dimension getPreferredSize() {
            int height = 0;
            for (JPanel cell : cells) height = Math.max(height, cell.getPreferredSize().height);
            return new Dimension(0, wide() ? height : height * 3 + 12);
        }
        @Override public void doLayout() {
            boolean wide = wide();
            int width = wide ? (getWidth() - 16) / 3 : getWidth();
            int height = wide ? getHeight() : (getHeight() - 12) / 3;
            for (int i = 0; i < cells.length; i++) cells[i].setBounds(wide ? i * (width + 8) : 0,
                    wide ? 0 : i * (height + 6), width, height);
        }
    }

    /** Tracks viewport width so long messages wrap and narrow hosts scroll only vertically. */
    static final class Body extends JPanel implements Scrollable {
        Body(JComponent main, JComponent path) { this(main, path, false); }
        Body(JComponent main, JComponent path, boolean expandPath) {
            super(new GridBagLayout());
            setOpaque(false);
            GridBagConstraints row = new GridBagConstraints();
            row.gridx = 0;
            row.gridy = 0;
            row.weightx = 1;
            row.weighty = expandPath ? 0 : 1;
            row.fill = GridBagConstraints.BOTH;
            add(main, row);
            row.gridy = 1;
            row.weighty = expandPath ? 1 : 0;
            row.insets = new Insets(8, 0, 0, 0);
            add(path, row);
        }
        @Override public Dimension getMinimumSize() { return new Dimension(0, 0); }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) { return 20; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) { return Math.max(20, r.height - 30); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() {
            return getParent() instanceof JViewport viewport && viewport.getHeight() >= getPreferredSize().height;
        }
    }

    static JScrollPane scroll(JComponent content) {
        JScrollPane scroll = new JScrollPane(content, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        return scroll;
    }

    static void theme(JComponent root, boolean dark) {
        Palette p = Palette.of(dark);
        root.putClientProperty("endgameScale", null);
        root.setBackground(p.background());
        root.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(p.border()),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        themeChildren(root, p);
        root.revalidate();
        root.repaint();
    }

    private static void themeChildren(Container container, Palette p) {
        for (Component child : container.getComponents()) {
            if (child instanceof JPanel panel && Boolean.TRUE.equals(panel.getClientProperty("endgameCard"))) {
                panel.setBackground(p.card());
                panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(p.border()),
                        BorderFactory.createEmptyBorder(8, 12, 8, 12)));
            }
            if (child instanceof JLabel label) {
                label.setForeground(Boolean.TRUE.equals(label.getClientProperty("accent")) ? p.accent()
                        : Boolean.TRUE.equals(label.getClientProperty("secondary")) ? p.secondary() : p.primary());
            }
            if (child instanceof JTextArea area) area.setForeground(p.secondary());
            if (child instanceof JButton button) styleButton(button, p);
            if (child instanceof JCheckBox check) {
                check.setOpaque(false);
                check.setForeground(p.primary());
                check.setFont(new Font("Segoe UI", Font.PLAIN, 13));
                check.setUI(new BasicCheckBoxUI());
                check.setIcon(new Icon() {
                    public int getIconWidth() { return 18; }
                    public int getIconHeight() { return 18; }
                    public void paintIcon(Component c, Graphics g, int x, int y) {
                        g.setColor(check.isSelected() ? p.accent() : p.control());
                        g.fillRect(x, y, 16, 16);
                        g.setColor(check.hasFocus() ? p.accent() : p.border());
                        g.drawRect(x, y, 16, 16);
                        if (check.isSelected()) {
                            Graphics2D line = (Graphics2D) g.create();
                            line.setColor(Color.WHITE);
                            line.setStroke(new BasicStroke(2));
                            line.drawLine(x+4, y+8, x+7, y+11);
                            line.drawLine(x+7, y+11, x+13, y+4);
                            line.dispose();
                        }
                    }
                });
            }
            if (child instanceof JSlider slider) {
                slider.setOpaque(false);
                slider.setForeground(p.accent());
                slider.setUI(new BasicSliderUI(slider) {
                    @Override protected Dimension getThumbSize() { return new Dimension(12, 20); }
                    @Override public void paintTrack(Graphics graphics) {
                        int y = trackRect.y + trackRect.height / 2 - 2;
                        graphics.setColor(p.border());
                        graphics.fillRect(trackRect.x, y, trackRect.width, 4);
                        if (slider.isEnabled()) {
                            graphics.setColor(p.accent());
                            graphics.fillRect(trackRect.x, y, Math.max(0, thumbRect.x - trackRect.x + 6), 4);
                        }
                    }
                    @Override public void paintThumb(Graphics graphics) {
                        graphics.setColor(slider.isEnabled() ? p.primary() : p.secondary());
                        graphics.fillRect(thumbRect.x, thumbRect.y, thumbRect.width, thumbRect.height);
                        graphics.setColor(slider.hasFocus() ? p.accent() : p.border());
                        graphics.drawRect(thumbRect.x, thumbRect.y, thumbRect.width - 1, thumbRect.height - 1);
                    }
                    @Override public void paintFocus(Graphics graphics) { }
                });
            }
            if (child instanceof JComboBox<?> box) {
                box.setBackground(p.control());
                box.setForeground(p.primary());
                box.setFont(new Font("Segoe UI", Font.PLAIN, 13));
                box.setPreferredSize(new Dimension(120, 32));
                box.setMinimumSize(new Dimension(70, 30));
                box.setBorder(BorderFactory.createLineBorder(p.border()));
                box.setUI(new BasicComboBoxUI() {
                    @Override protected JButton createArrowButton() {
                        JButton arrow = button("v");
                        arrow.setText("");
                        arrow.setIcon(new Icon() {
                            public int getIconWidth() { return 8; }
                            public int getIconHeight() { return 5; }
                            public void paintIcon(Component c, Graphics g, int x, int y) {
                                g.setColor(p.secondary());
                                g.fillPolygon(new int[]{x, x+8, x+4}, new int[]{y, y, y+5}, 3);
                            }
                        });
                        arrow.setPreferredSize(new Dimension(26, 30));
                        styleButton(arrow, p);
                        return arrow;
                    }
                });
            }
            if (child instanceof JProgressBar bar) {
                bar.setUI(new BasicProgressBarUI());
                bar.setBorderPainted(false);
                bar.setForeground(p.accent());
                bar.setBackground(p.border());
                bar.setPreferredSize(new Dimension(100, 5));
            }
            if (child instanceof JScrollBar bar) {
                bar.setPreferredSize(new Dimension(10, 10));
                bar.setUI(new BasicScrollBarUI() {
                    @Override protected void configureScrollBarColors() {
                        thumbColor = p.border();
                        trackColor = p.background();
                    }
                    @Override protected JButton createDecreaseButton(int orientation) { return emptyButton(); }
                    @Override protected JButton createIncreaseButton(int orientation) { return emptyButton(); }
                    private JButton emptyButton() {
                        JButton button = new JButton();
                        button.setPreferredSize(new Dimension(0, 0));
                        return button;
                    }
                });
            }
            if (child instanceof Container nested && !(child instanceof JComboBox<?>) && !(child instanceof JScrollBar)) {
                themeChildren(nested, p);
            }
        }
    }

    private static void styleButton(JButton button, Palette p) {
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        button.setUI(new BasicButtonUI() {
            @Override public void paint(Graphics graphics, JComponent component) {
                AbstractButton control = (AbstractButton) component;
                boolean primary = Boolean.TRUE.equals(control.getClientProperty("primaryAction")) && control.isEnabled();
                Color fill = primary ? new Color(17,96,199) : p.control();
                if (control.isEnabled() && control.getModel().isArmed() && control.getModel().isPressed()) fill = fill.darker();
                else if (control.isEnabled() && control.getModel().isRollover()) fill = fill.brighter();
                graphics.setColor(fill);
                graphics.fillRect(0, 0, control.getWidth(), control.getHeight());
                graphics.setColor(primary || control.hasFocus() ? p.accent() : p.border());
                graphics.drawRect(0, 0, control.getWidth() - 1, control.getHeight() - 1);
                control.setForeground(primary ? Color.WHITE : control.isEnabled() ? p.primary() : p.secondary().darker());
                super.paint(graphics, component);
            }
            @Override protected void paintButtonPressed(Graphics graphics, AbstractButton button) { }
            @Override protected void paintText(Graphics graphics, AbstractButton button, Rectangle bounds, String text) {
                graphics.setColor(button.getForeground());
                graphics.drawString(text, bounds.x, bounds.y + graphics.getFontMetrics().getAscent());
            }
        });
    }
}

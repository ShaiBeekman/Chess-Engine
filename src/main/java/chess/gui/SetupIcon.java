package main.java.chess.gui;

import javax.swing.Icon;
import java.awt.*;

/** Small vector icons shared by the setup dashboard and palette. */
final class SetupIcon implements Icon {
    enum Kind { COPY, TRASH, CLOSE, PLAY, INFO, MATERIAL }
    private final Kind kind;
    private final int size;
    SetupIcon(Kind kind, int size) { this.kind = kind; this.size = size; }
    public int getIconWidth() { return size; }
    public int getIconHeight() { return size; }
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.translate(x, y);
        g.scale(size / 24.0, size / 24.0);
        g.setColor(component.getForeground());
        g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (kind) {
            case COPY -> {
                g.drawRect(8, 6, 12, 16);
                g.drawLine(15, 3, 4, 3); g.drawLine(4, 3, 4, 18);
            }
            case TRASH -> {
                g.drawLine(3, 5, 21, 5); g.drawRect(9, 1, 6, 3);
                g.drawPolyline(new int[]{5, 6, 18, 19}, new int[]{8, 22, 22, 8}, 4);
                g.drawLine(10, 9, 10, 18); g.drawLine(14, 9, 14, 18);
            }
            case CLOSE -> { g.drawLine(3, 3, 21, 21); g.drawLine(21, 3, 3, 21); }
            case PLAY -> g.fillPolygon(new int[]{5, 22, 5}, new int[]{1, 12, 23}, 3);
            case INFO -> {
                Color ink = g.getColor();
                g.setColor(new Color(ink.getRed(), ink.getGreen(), ink.getBlue(), 22));
                g.fillOval(0, 0, 24, 24); g.setColor(ink);
                g.setStroke(new BasicStroke(1.2f)); g.drawOval(6, 6, 12, 12);
                g.fillOval(11, 8, 2, 2); g.drawLine(12, 12, 12, 16);
            }
            case MATERIAL -> {
                g.fillOval(9, 1, 6, 6); g.fillOval(2, 5, 5, 5); g.fillOval(17, 5, 5, 5);
                g.fillOval(8, 8, 8, 8); g.fillOval(1, 11, 7, 8); g.fillOval(16, 11, 7, 8);
                g.fillArc(5, 15, 14, 15, 0, 180); g.fillRect(5, 22, 14, 2);
                g.fillArc(0, 17, 8, 12, 0, 180); g.fillArc(16, 17, 8, 12, 0, 180);
            }
        }
        g.dispose();
    }
}

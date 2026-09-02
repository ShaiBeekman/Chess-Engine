package main.java.chess.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

/**
 * Lightweight board-sized busy card used while exact endgame assets are
 * loaded on a SwingWorker thread.
 *
 * ChessBoardPanel itself paints the dimming scrim so it matches the board
 * coordinates exactly. This component supplies the centered loading card and
 * consumes pointer input while the next exact study is being prepared.
 */
public final class BoardLoadingOverlay extends JPanel {

    private static final Color CARD_BACKGROUND =
            new Color(24, 33, 42, 245);

    private static final Color CARD_BORDER =
            new Color(55, 72, 88);

    private static final Color PRIMARY_TEXT =
            new Color(242, 244, 247);

    private static final Color SECONDARY_TEXT =
            new Color(174, 184, 195);

    private final JLabel titleLabel;
    private final JLabel detailLabel;
    private final JProgressBar progressBar;

    public BoardLoadingOverlay() {

        setOpaque(false);
        setVisible(false);
        setLayout(new GridBagLayout());

        JPanel card =
                new JPanel();

        card.setLayout(
                new BoxLayout(
                        card,
                        BoxLayout.Y_AXIS
                )
        );

        card.setBackground(
                CARD_BACKGROUND
        );

        card.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(
                                CARD_BORDER,
                                1
                        ),
                        new EmptyBorder(
                                18,
                                22,
                                18,
                                22
                        )
                )
        );

        card.setPreferredSize(
                new Dimension(
                        350,
                        138
                )
        );

        card.setMaximumSize(
                new Dimension(
                        350,
                        138
                )
        );

        titleLabel =
                new JLabel(
                        "Loading exact position..."
                );

        titleLabel.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        titleLabel.setForeground(
                PRIMARY_TEXT
        );

        titleLabel.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.BOLD,
                        17
                )
        );

        detailLabel =
                new JLabel(
                        "Preparing the next exact study..."
                );

        detailLabel.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        detailLabel.setForeground(
                SECONDARY_TEXT
        );

        detailLabel.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.PLAIN,
                        12
                )
        );

        progressBar =
                new JProgressBar();

        progressBar.setIndeterminate(
                true
        );

        progressBar.setStringPainted(
                false
        );

        progressBar.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        Dimension progressSize =
                new Dimension(
                        286,
                        16
                );

        progressBar.setPreferredSize(
                progressSize
        );

        progressBar.setMaximumSize(
                progressSize
        );

        card.add(
                Box.createVerticalGlue()
        );

        card.add(
                titleLabel
        );

        card.add(
                Box.createRigidArea(
                        new Dimension(
                                0,
                                8
                        )
                )
        );

        card.add(
                detailLabel
        );

        card.add(
                Box.createRigidArea(
                        new Dimension(
                                0,
                                14
                        )
                )
        );

        card.add(
                progressBar
        );

        card.add(
                Box.createVerticalGlue()
        );

        add(
                card
        );

        /*
         * Prevent clicks, drags, and wheel events from falling through to the
         * board while the overlay is visible.
         */
        addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent event) {
                        event.consume();
                    }

                    @Override
                    public void mouseReleased(MouseEvent event) {
                        event.consume();
                    }

                    @Override
                    public void mouseClicked(MouseEvent event) {
                        event.consume();
                    }
                }
        );

        addMouseMotionListener(
                new MouseMotionAdapter() {
                    @Override
                    public void mouseDragged(MouseEvent event) {
                        event.consume();
                    }

                    @Override
                    public void mouseMoved(MouseEvent event) {
                        event.consume();
                    }
                }
        );

        addMouseWheelListener(
                event -> event.consume()
        );
    }


    public void showLoading(
            String title,
            String detail
    ) {

        titleLabel.setText(
                title == null || title.isBlank()
                        ? "Loading exact position..."
                        : title
        );

        detailLabel.setText(
                detail == null || detail.isBlank()
                        ? "Preparing the next exact study..."
                        : detail
        );

        progressBar.setIndeterminate(
                true
        );

        setVisible(
                true
        );

        revalidate();
        repaint();
    }


    public void hideLoading() {

        setVisible(
                false
        );
    }

}

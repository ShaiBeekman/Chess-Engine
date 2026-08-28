package main.java.chess.gui;

import main.java.chess.endgame.EndgameSettings;
import main.java.chess.model.Color;
import main.java.chess.model.Position;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;

public class EndgameStudyPanel extends JPanel {

    private final JLabel modeValue;
    private final JLabel sideValue;
    private final JLabel instructionValue;
    private final JLabel statusValue;

    private final JButton hintButton;
    private final JButton giveUpButton;
    private final JButton nextButton;
    private final JButton newButton;

    private Runnable hintListener;
    private Runnable giveUpListener;
    private Runnable nextListener;
    private Runnable newListener;

    private java.awt.Color primary;
    private java.awt.Color secondary;
    private java.awt.Color background;
    private java.awt.Color control;
    private java.awt.Color border;


    public EndgameStudyPanel() {

        setLayout(
                new BorderLayout()
        );

        setPreferredSize(
                new Dimension(
                        390,
                        640
                )
        );


        JPanel content =
                new JPanel();

        content.setOpaque(
                false
        );

        content.setLayout(
                new BoxLayout(
                        content,
                        BoxLayout.Y_AXIS
                )
        );

        content.setBorder(
                BorderFactory.createEmptyBorder(
                        22,
                        20,
                        20,
                        20
                )
        );


        JLabel title =
                label(
                        "ENDGAME STUDY",
                        true,
                        13
                );


        JLabel subtitle =
                label(
                        "Solve the position without analysis spoilers.",
                        false,
                        12
                );


        content.add(
                title
        );

        content.add(
                Box.createVerticalStrut(
                        4
                )
        );

        content.add(
                subtitle
        );

        content.add(
                Box.createVerticalStrut(
                        26
                )
        );


        content.add(
                section(
                        "GENERATION"
                )
        );

        content.add(
                Box.createVerticalStrut(
                        7
                )
        );

        modeValue =
                value();

        content.add(
                modeValue
        );

        content.add(
                Box.createVerticalStrut(
                        22
                )
        );


        content.add(
                section(
                        "SIDE TO MOVE"
                )
        );

        content.add(
                Box.createVerticalStrut(
                        7
                )
        );

        sideValue =
                value();

        content.add(
                sideValue
        );

        content.add(
                Box.createVerticalStrut(
                        28
                )
        );


        instructionValue =
                label(
                        "Find the best move",
                        true,
                        18
                );

        content.add(
                instructionValue
        );

        content.add(
                Box.createVerticalStrut(
                        10
                )
        );


        statusValue =
                label(
                        "Analysis is hidden while you solve.",
                        false,
                        12
                );

        content.add(
                statusValue
        );

        content.add(
                Box.createVerticalStrut(
                        26
                )
        );


        hintButton =
                actionButton(
                        "Hint"
                );

        giveUpButton =
                actionButton(
                        "Give Up"
                );

        nextButton =
                actionButton(
                        "Next Endgame"
                );

        newButton =
                actionButton(
                        "New Endgame"
                );


        hintButton.addActionListener(
                event -> run(
                        hintListener
                )
        );

        giveUpButton.addActionListener(
                event -> run(
                        giveUpListener
                )
        );

        nextButton.addActionListener(
                event -> run(
                        nextListener
                )
        );

        newButton.addActionListener(
                event -> run(
                        newListener
                )
        );


        content.add(
                hintButton
        );

        content.add(
                Box.createVerticalStrut(
                        8
                )
        );

        content.add(
                giveUpButton
        );

        content.add(
                Box.createVerticalGlue()
        );

        content.add(
                nextButton
        );

        content.add(
                Box.createVerticalStrut(
                        8
                )
        );

        content.add(
                newButton
        );


        add(
                content,
                BorderLayout.CENTER
        );


        applyTheme(
                true
        );
    }


    public void setPosition(
            Position position,
            EndgameSettings settings
    ) {

        if (position == null
                || settings == null) {
            return;
        }


        modeValue.setText(
                settings.displayName()
        );


        sideValue.setText(
                position.getSideToMove()
                        == Color.WHITE
                        ? "White"
                        : "Black"
        );


        setProving();
    }


    public void setStatus(
            String text
    ) {

        statusValue.setText(
                text
        );
    }


    public void setProving() {

        instructionValue.setText(
                "Preparing study"
        );


        statusValue.setText(
                "Proving the position exactly…"
        );


        hintButton.setEnabled(
                false
        );
    }


    public void setProvenMate(
            int mateInMoves
    ) {

        instructionValue.setText(
                "Find the best move"
        );


        statusValue.setText(
                "Exact mate proven • Mate in "
                        + mateInMoves
        );


        hintButton.setEnabled(
                true
        );
    }


    public void setRejected(
            String reason
    ) {

        instructionValue.setText(
                "Generating another position"
        );


        statusValue.setText(
                reason
        );


        hintButton.setEnabled(
                false
        );
    }


    public void setHintListener(
            Runnable listener
    ) {
        hintListener =
                listener;
    }


    public void setGiveUpListener(
            Runnable listener
    ) {
        giveUpListener =
                listener;
    }


    public void setNextListener(
            Runnable listener
    ) {
        nextListener =
                listener;
    }


    public void setNewListener(
            Runnable listener
    ) {
        newListener =
                listener;
    }


    public void applyTheme(
            boolean dark
    ) {

        background =
                dark
                        ? new java.awt.Color(19, 27, 35)
                        : new java.awt.Color(250, 251, 253);

        primary =
                dark
                        ? new java.awt.Color(242, 244, 247)
                        : new java.awt.Color(31, 35, 41);

        secondary =
                dark
                        ? new java.awt.Color(164, 173, 184)
                        : new java.awt.Color(100, 107, 117);

        control =
                dark
                        ? new java.awt.Color(26, 35, 44)
                        : new java.awt.Color(244, 246, 249);

        border =
                dark
                        ? new java.awt.Color(42, 53, 64)
                        : new java.awt.Color(210, 216, 224);


        setBackground(
                background
        );

        setBorder(
                BorderFactory.createLineBorder(
                        border,
                        1,
                        true
                )
        );


        themeRecursively(
                this
        );


        for (JButton button :
                new JButton[] {
                        hintButton,
                        giveUpButton,
                        nextButton,
                        newButton
                }) {

            button.setForeground(
                    primary
            );

            button.setBackground(
                    control
            );

            button.setBorder(
                    BorderFactory.createLineBorder(
                            border,
                            1,
                            true
                    )
            );

            button.setOpaque(
                    true
            );

            button.setContentAreaFilled(
                    true
            );
        }


        repaint();
    }


    private JLabel section(
            String text
    ) {

        JLabel label =
                label(
                        text,
                        false,
                        10
                );

        label.putClientProperty(
                "secondary",
                Boolean.TRUE
        );

        return label;
    }


    private JLabel value() {

        return label(
                "",
                true,
                12
        );
    }


    private JLabel label(
            String text,
            boolean primaryText,
            int size
    ) {

        JLabel label =
                new JLabel(
                        text
                );

        label.setFont(
                new Font(
                        Font.SANS_SERIF,
                        primaryText
                                ? Font.BOLD
                                : Font.PLAIN,
                        size
                )
        );

        label.putClientProperty(
                primaryText
                        ? "primary"
                        : "secondary",
                Boolean.TRUE
        );

        return label;
    }


    private JButton actionButton(
            String text
    ) {

        JButton button =
                new JButton(
                        text
                );

        button.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.BOLD,
                        12
                )
        );

        button.setFocusPainted(
                false
        );

        button.setCursor(
                Cursor.getPredefinedCursor(
                        Cursor.HAND_CURSOR
                )
        );

        button.setMaximumSize(
                new Dimension(
                        Integer.MAX_VALUE,
                        40
                )
        );

        return button;
    }


    private void run(
            Runnable runnable
    ) {

        if (runnable != null) {
            runnable.run();
        }
    }


    private void themeRecursively(
            java.awt.Container container
    ) {

        for (java.awt.Component component :
                container.getComponents()) {

            if (component instanceof JLabel label) {

                if (Boolean.TRUE.equals(
                        label.getClientProperty(
                                "secondary"
                        )
                )) {

                    label.setForeground(
                            secondary
                    );

                } else {

                    label.setForeground(
                            primary
                    );
                }
            }


            if (component instanceof java.awt.Container child) {

                themeRecursively(
                        child
                );
            }
        }
    }
}

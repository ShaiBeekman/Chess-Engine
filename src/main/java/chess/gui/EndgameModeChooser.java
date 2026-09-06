package main.java.chess.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * M68E3C entry chooser: Curriculum and Solver are deliberately separate
 * interfaces, with a left-aligned visual hierarchy matching the choice cards.
 */
public final class EndgameModeChooser extends JDialog {

    public enum Choice {
        CURRICULUM,
        SOLVER,
        CANCEL
    }

    private Choice choice = Choice.CANCEL;

    public EndgameModeChooser(Window owner, boolean dark) {
        super(owner, "Endgame", ModalityType.APPLICATION_MODAL);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        EndgameWorkspace.Palette palette = EndgameWorkspace.Palette.of(dark);
        Color background = palette.background();
        Color foreground = palette.primary();
        Color secondary = palette.secondary();
        Color card = palette.card();

        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(BorderFactory.createEmptyBorder(22, 22, 30, 22));
        root.setBackground(background);

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 16));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));

        JLabel title = new JLabel("ENDGAME");
        title.setFont(new Font("Segoe UI", Font.BOLD, 28));
        title.setForeground(foreground);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel("Choose how you want to work with solved endings.");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitle.setForeground(secondary);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        header.add(title);
        header.add(Box.createVerticalStrut(3));
        header.add(subtitle);

        root.add(header);
        root.add(Box.createVerticalStrut(18));

        JPanel curriculum = choiceCard(
                "Curriculum",
                "Train solved 3- and 4-piece endings",
                card,
                foreground,
                secondary,
                () -> {
                    choice = Choice.CURRICULUM;
                    dispose();
                }
        );
        curriculum.setAlignmentX(Component.LEFT_ALIGNMENT);
        curriculum.setMaximumSize(new Dimension(Integer.MAX_VALUE, 84));
        root.add(curriculum);

        root.add(Box.createVerticalStrut(12));

        JPanel solver = choiceCard(
                "Solver",
                "Generate and prove endgame positions",
                card,
                foreground,
                secondary,
                () -> {
                    choice = Choice.SOLVER;
                    dispose();
                }
        );
        solver.setAlignmentX(Component.LEFT_ALIGNMENT);
        solver.setMaximumSize(new Dimension(Integer.MAX_VALUE, 84));
        root.add(solver);

        // Intentional breathing room beneath the final card.
        root.add(Box.createVerticalStrut(22));

        setContentPane(root);
        setSize(new Dimension(500, 345));
        setLocationRelativeTo(owner);
    }

    public Choice showChoice() {
        setVisible(true);
        return choice;
    }

    private JPanel choiceCard(
            String title,
            String description,
            Color background,
            Color foreground,
            Color secondary,
            Runnable action
    ) {
        JPanel panel = new JPanel(new BorderLayout(10, 4));
        panel.setBackground(background);
        panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(secondary.darker()),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)));
        panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        titleLabel.setForeground(foreground);

        JLabel descriptionLabel = new JLabel(description);
        descriptionLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        descriptionLabel.setForeground(secondary);

        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(descriptionLabel, BorderLayout.CENTER);

        MouseAdapter listener = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                action.run();
            }
        };

        panel.addMouseListener(listener);
        titleLabel.addMouseListener(listener);
        descriptionLabel.addMouseListener(listener);

        return panel;
    }
}

package main.java.chess.gui;

import main.java.chess.endgame.EndgameSettings;
import main.java.chess.endgame.EndgameStudyProgress;
import main.java.chess.model.Position;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Dedicated Endgame interface.
 */
public final class EndgameCurriculumPanel extends JPanel {

    private final JLabel familyHero =
            new JLabel("RANDOM", SwingConstants.CENTER);

    private final JLabel familyDescription =
            new JLabel(
                    "Exact solved-position training",
                    SwingConstants.CENTER
            );

    private final JLabel sideValue =
            new JLabel("—", SwingConstants.CENTER);

    private final JLabel proofValue =
            new JLabel("Exact tablebase", SwingConstants.CENTER);

    private final JLabel instructionValue =
            new JLabel(
                    "Choose a family to begin",
                    SwingConstants.CENTER
            );

    private final JLabel statusValue =
            new JLabel(
                    "Progress is saved automatically.",
                    SwingConstants.CENTER
            );

    private final JLabel masteryValue =
            new JLabel("0 mastered", SwingConstants.CENTER);

    private final JLabel masteryTitle =
            new JLabel(
                    "ALL FAMILIES MASTERY",
                    SwingConstants.CENTER
            );

    private final JLabel orderPositionValue =
            new JLabel("ORDERED", SwingConstants.CENTER);

    private final JLabel inProgressValue =
            new JLabel(
                    "In progress: 0",
                    SwingConstants.CENTER
            );

    private final JLabel completeValue =
            new JLabel(
                    "Complete: 0",
                    SwingConstants.CENTER
            );

    private final JLabel reviewValue =
            new JLabel(
                    "Review stack: 0",
                    SwingConstants.CENTER
            );

    private final JProgressBar masteryBar =
            new JProgressBar(0, 100);

    private final JComboBox<String> familyBox =
            new JComboBox<>();

    private final JButton hintButton =
            button("Hint");

    private final JButton giveUpButton =
            button("Give Up");

    private final JButton nextButton =
            button("Next Position");

    private final JButton previousMoveButton =
            button("Previous Move");

    private final JButton nextMoveButton =
            button("Next Move");

    private final JLabel moveReviewValue =
            new JLabel(
                    "Starting position",
                    SwingConstants.CENTER
            );

    private final JButton resetProgressButton =
            button("Reset All Endgame Progress");

    private final JButton resetFamilyButton =
            button("Reset This Family");

    private final JComboBox<String> orderBox =
            new JComboBox<>(
                    new String[]{
                            "Ordered",
                            "Shuffle"
                    }
            );

    private Runnable hintListener;
    private Runnable giveUpListener;
    private Runnable nextListener;
    private Runnable previousMoveListener;
    private Runnable nextMoveListener;
    private Runnable resetProgressListener;
    private Runnable resetFamilyListener;

    private Consumer<EndgameStudyProgress.StudyOrder> orderListener;
    private Consumer<String> familyListener;

    private boolean updatingFamily;
    private boolean updatingOrder;

    private java.awt.Color primary;
    private java.awt.Color secondary;
    private java.awt.Color background;
    private java.awt.Color card;
    private java.awt.Color control;
    private java.awt.Color border;
    private java.awt.Color accent;


    public EndgameCurriculumPanel() {

        setLayout(
                new BorderLayout()
        );

        setPreferredSize(
                new Dimension(
                        410,
                        650
                )
        );

        JPanel root =
                new JPanel();

        root.setOpaque(false);
        root.setLayout(
                new BoxLayout(
                        root,
                        BoxLayout.Y_AXIS
                )
        );

        root.setBorder(
                BorderFactory.createEmptyBorder(
                        18,
                        18,
                        6,
                        18
                )
        );


        JLabel title =
                text(
                        "ENDGAME",
                        true,
                        13
                );

        JLabel subtitle =
                text(
                        "Master exact 3- and 4-piece endings",
                        false,
                        12
                );

        makeFullWidthCentered(title);
        makeFullWidthCentered(subtitle);

        root.add(title);
        root.add(
                Box.createVerticalStrut(3)
        );
        root.add(subtitle);
        root.add(
                Box.createVerticalStrut(16)
        );


        // =====================================================
        // Family card
        // =====================================================

        JPanel hero =
                cardPanel();

        hero.setLayout(
                new BoxLayout(
                        hero,
                        BoxLayout.Y_AXIS
                )
        );

        familyHero.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.BOLD,
                        27
                )
        );

        familyDescription.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.PLAIN,
                        11
                )
        );

        makeFullWidthCentered(familyHero);
        makeFullWidthCentered(familyDescription);

        familyBox.setPreferredSize(
                new Dimension(
                        220,
                        32
                )
        );

        familyBox.setMinimumSize(
                new Dimension(
                        180,
                        32
                )
        );

        familyBox.setMaximumSize(
                new Dimension(
                        260,
                        32
                )
        );

        hero.add(
                Box.createVerticalStrut(14)
        );
        hero.add(familyHero);
        hero.add(
                Box.createVerticalStrut(3)
        );
        hero.add(familyDescription);
        hero.add(
                Box.createVerticalStrut(12)
        );
        addCenteredComponent(
                hero,
                familyBox
        );
        hero.add(
                Box.createVerticalStrut(14)
        );

        root.add(hero);
        root.add(
                Box.createVerticalStrut(12)
        );


        // =====================================================
        // Progress card
        // =====================================================

        JPanel progress =
                cardPanel();

        progress.setLayout(
                new BoxLayout(
                        progress,
                        BoxLayout.Y_AXIS
                )
        );

        masteryTitle.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.BOLD,
                        10
                )
        );

        masteryValue.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.BOLD,
                        12
                )
        );

        for (JLabel label : new JLabel[]{
                inProgressValue,
                completeValue,
                reviewValue,
                orderPositionValue
        }) {
            label.setFont(
                    new Font(
                            Font.SANS_SERIF,
                            Font.PLAIN,
                            11
                    )
            );
        }

        for (JLabel label : new JLabel[]{
                masteryTitle,
                masteryValue,
                completeValue,
                inProgressValue,
                reviewValue,
                orderPositionValue
        }) {
            makeFullWidthCentered(label);
        }

        masteryBar.setStringPainted(false);
        masteryBar.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );
        masteryBar.setPreferredSize(
                new Dimension(
                        330,
                        8
                )
        );
        masteryBar.setMaximumSize(
                new Dimension(
                        330,
                        8
                )
        );

        progress.add(
                Box.createVerticalStrut(10)
        );
        progress.add(masteryTitle);
        progress.add(
                Box.createVerticalStrut(8)
        );
        progress.add(masteryBar);
        progress.add(
                Box.createVerticalStrut(7)
        );
        progress.add(masteryValue);
        progress.add(
                Box.createVerticalStrut(3)
        );
        progress.add(completeValue);
        progress.add(inProgressValue);
        progress.add(reviewValue);
        progress.add(
                Box.createVerticalStrut(7)
        );

        orderBox.setPreferredSize(
                new Dimension(
                        180,
                        30
                )
        );
        orderBox.setMinimumSize(
                new Dimension(
                        180,
                        30
                )
        );
        orderBox.setMaximumSize(
                new Dimension(
                        180,
                        30
                )
        );

        addCenteredComponent(
                progress,
                orderBox
        );

        progress.add(
                Box.createVerticalStrut(4)
        );
        progress.add(orderPositionValue);
        progress.add(
                Box.createVerticalStrut(7)
        );

        addCenteredButton(
                progress,
                resetFamilyButton,
                210
        );

        progress.add(
                Box.createVerticalStrut(10)
        );

        root.add(progress);
        root.add(
                Box.createVerticalStrut(12)
        );


        // =====================================================
        // Current-position card
        // =====================================================

        JPanel lesson =
                cardPanel();

        lesson.setLayout(
                new BoxLayout(
                        lesson,
                        BoxLayout.Y_AXIS
                )
        );

        JLabel current =
                text(
                        "CURRENT POSITION",
                        true,
                        10
                );

        current.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.BOLD,
                        10
                )
        );

        sideValue.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.BOLD,
                        14
                )
        );

        proofValue.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.PLAIN,
                        11
                )
        );

        instructionValue.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.BOLD,
                        16
                )
        );

        statusValue.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.PLAIN,
                        11
                )
        );

        for (JLabel label : new JLabel[]{
                current,
                sideValue,
                proofValue,
                instructionValue,
                statusValue
        }) {
            makeFullWidthCentered(label);
        }

        lesson.add(
                Box.createVerticalStrut(10)
        );
        lesson.add(current);
        lesson.add(
                Box.createVerticalStrut(8)
        );
        lesson.add(sideValue);
        lesson.add(proofValue);
        lesson.add(
                Box.createVerticalStrut(12)
        );
        lesson.add(instructionValue);
        lesson.add(
                Box.createVerticalStrut(4)
        );
        lesson.add(statusValue);

        moveReviewValue.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.PLAIN,
                        11
                )
        );
        moveReviewValue.putClientProperty(
                "secondary",
                true
        );
        makeFullWidthCentered(moveReviewValue);

        JPanel moveReviewActions =
                new JPanel(
                        new GridLayout(
                                1,
                                2,
                                8,
                                0
                        )
                );

        moveReviewActions.setOpaque(false);
        moveReviewActions.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );
        moveReviewActions.setPreferredSize(
                new Dimension(
                        310,
                        34
                )
        );
        moveReviewActions.setMaximumSize(
                new Dimension(
                        310,
                        34
                )
        );

        previousMoveButton.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.BOLD,
                        11
                )
        );
        nextMoveButton.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.BOLD,
                        11
                )
        );

        previousMoveButton.setToolTipText(
                "Previous move (Left Arrow)"
        );
        nextMoveButton.setToolTipText(
                "Next move (Right Arrow)"
        );

        previousMoveButton.setEnabled(false);
        nextMoveButton.setEnabled(false);

        moveReviewActions.add(previousMoveButton);
        moveReviewActions.add(nextMoveButton);

        lesson.add(
                Box.createVerticalStrut(9)
        );
        lesson.add(moveReviewValue);
        lesson.add(
                Box.createVerticalStrut(6)
        );
        addCenteredComponent(
                lesson,
                moveReviewActions
        );
        lesson.add(
                Box.createVerticalStrut(10)
        );

        root.add(lesson);
        root.add(
                Box.createVerticalStrut(8)
        );


        // =====================================================
        // Secondary action
        // =====================================================

        addCenteredButton(
                root,
                resetProgressButton,
                250
        );

        root.add(
                Box.createVerticalStrut(8)
        );


        // =====================================================
        // Scrollable curriculum body
        // =====================================================

        JScrollPane scrollPane =
                new JScrollPane(
                        root,
                        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
                );

        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);
        scrollPane.getVerticalScrollBar().setBlockIncrement(90);

        add(
                scrollPane,
                BorderLayout.CENTER
        );


        // =====================================================
        // Fixed Trainer actions
        //
        // Keep Hint / Give Up / Next Position outside the scroll pane so
        // Next Position can never be clipped below the visible panel.
        // =====================================================

        JPanel actionFooter =
                new JPanel();

        actionFooter.setOpaque(false);
        actionFooter.setLayout(
                new BoxLayout(
                        actionFooter,
                        BoxLayout.Y_AXIS
                )
        );

        actionFooter.setBorder(
                BorderFactory.createEmptyBorder(
                        6,
                        18,
                        10,
                        18
                )
        );

        JPanel actions =
                new JPanel(
                        new GridLayout(
                                1,
                                2,
                                8,
                                0
                        )
                );

        actions.setOpaque(false);
        actions.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        Dimension actionSize =
                new Dimension(
                        330,
                        38
                );

        actions.setPreferredSize(actionSize);
        actions.setMinimumSize(actionSize);
        actions.setMaximumSize(actionSize);

        actions.add(hintButton);
        actions.add(giveUpButton);

        actionFooter.add(actions);
        actionFooter.add(
                Box.createVerticalStrut(5)
        );

        addCenteredButton(
                actionFooter,
                nextButton,
                220
        );

        add(
                actionFooter,
                BorderLayout.SOUTH
        );


        // =====================================================
        // Listeners
        // =====================================================

        hintButton.addActionListener(
                event -> run(hintListener)
        );

        giveUpButton.addActionListener(
                event -> run(giveUpListener)
        );

        nextButton.addActionListener(
                event -> run(nextListener)
        );

        previousMoveButton.addActionListener(
                event -> run(previousMoveListener)
        );

        nextMoveButton.addActionListener(
                event -> run(nextMoveListener)
        );

        resetProgressButton.addActionListener(
                event -> run(resetProgressListener)
        );

        resetFamilyButton.addActionListener(
                event -> run(resetFamilyListener)
        );

        orderBox.addActionListener(
                event -> {
                    if (!updatingOrder
                            && orderListener != null) {

                        orderListener.accept(
                                orderBox.getSelectedIndex() == 0
                                        ? EndgameStudyProgress.StudyOrder.ORDERED
                                        : EndgameStudyProgress.StudyOrder.SHUFFLE
                        );
                    }
                }
        );

        familyBox.addActionListener(
                event -> {
                    if (!updatingFamily
                            && familyListener != null
                            && familyBox.getSelectedItem() != null) {

                        familyListener.accept(
                                familyBox.getSelectedItem()
                                        .toString()
                        );
                    }
                }
        );

        applyTheme(true);
    }


    // =========================================================
    // Public configuration
    // =========================================================

    public void setFamilies(
            List<String> families,
            String selected
    ) {
        updatingFamily = true;

        familyBox.removeAllItems();

        for (String family : families) {
            familyBox.addItem(family);
        }

        if (selected != null) {
            familyBox.setSelectedItem(selected);
        }

        updatingFamily = false;

        updateHero(selected);
    }


    public void setFamilyListener(
            Consumer<String> listener
    ) {
        familyListener = listener;
    }


    public void setResetProgressListener(
            Runnable listener
    ) {
        resetProgressListener = listener;
    }


    public void setResetFamilyListener(
            Runnable listener
    ) {
        resetFamilyListener = listener;
    }


    public void setOrderListener(
            Consumer<EndgameStudyProgress.StudyOrder> listener
    ) {
        orderListener = listener;
    }


    public void setStudyOrder(
            EndgameStudyProgress.StudyOrder order
    ) {
        updatingOrder = true;

        orderBox.setSelectedIndex(
                order == EndgameStudyProgress.StudyOrder.SHUFFLE
                        ? 1
                        : 0
        );

        updatingOrder = false;
    }


    public void setHintListener(
            Runnable listener
    ) {
        hintListener = listener;
    }


    public void setGiveUpListener(
            Runnable listener
    ) {
        giveUpListener = listener;
    }


    public void setNextListener(
            Runnable listener
    ) {
        nextListener = listener;
    }


    public void setPreviousMoveListener(
            Runnable listener
    ) {
        previousMoveListener = listener;
    }


    public void setNextMoveListener(
            Runnable listener
    ) {
        nextMoveListener = listener;
    }


    public String getSelectedFamily() {
        Object selected =
                familyBox.getSelectedItem();

        return selected == null
                ? "Random"
                : selected.toString();
    }


    // =========================================================
    // Study state
    // =========================================================

    public void setPosition(
            Position position,
            EndgameSettings settings
    ) {
        if (position == null) {
            return;
        }

        setDisplayedPosition(
                position
        );

        instructionValue.setText(
                "Find the exact best move"
        );

        statusValue.setText(
                "Analysis is hidden while you solve."
        );
    }


    public void setDisplayedPosition(
            Position position
    ) {
        if (position == null) {
            return;
        }

        sideValue.setText(
                (position.getSideToMove()
                        == main.java.chess.model.Color.WHITE
                        ? "White"
                        : "Black")
                        + " to move"
        );
    }


    public void setMoveReviewState(
            int index,
            int lastIndex
    ) {
        int safeLast =
                Math.max(
                        0,
                        lastIndex
                );

        int safeIndex =
                Math.max(
                        0,
                        Math.min(
                                index,
                                safeLast
                        )
                );

        previousMoveButton.setEnabled(
                safeLast > 0
                        && safeIndex > 0
        );

        nextMoveButton.setEnabled(
                safeLast > 0
                        && safeIndex < safeLast
        );

        if (safeLast == 0) {
            moveReviewValue.setText(
                    "Starting position"
            );
            return;
        }

        if (safeIndex == 0) {
            moveReviewValue.setText(
                    "Review • Starting position"
            );
            return;
        }

        if (safeIndex == safeLast) {
            moveReviewValue.setText(
                    String.format(
                            "Latest • Move %,d / %,d",
                            safeIndex,
                            safeLast
                    )
            );
            return;
        }

        moveReviewValue.setText(
                String.format(
                        "Review • Move %,d / %,d",
                        safeIndex,
                        safeLast
                )
        );
    }


    public void setFamilyDisplay(String name) {
        updateHero(name);
    }


    public void setLoadingTablebase(String name) {
        updateHero(name);
        proofValue.setText(
                "Loading exact tablebase…"
        );
        instructionValue.setText(
                "Preparing study"
        );
        hintButton.setEnabled(false);
    }


    public void setProving() {
        proofValue.setText(
                "Proving the position exactly…"
        );
        instructionValue.setText(
                "Preparing study"
        );
        hintButton.setEnabled(false);
    }


    public void setProvenMate(int mate) {
        proofValue.setText(
                mate >= 0
                        ? "Exact WIN  •  DTM " + mate
                        : "Exact WIN"
        );

        instructionValue.setText(
                "Find the exact best move"
        );

        hintButton.setEnabled(true);
    }


    public void setRejected(String reason) {
        proofValue.setText(
                "Generating another position…"
        );

        statusValue.setText(
                reason == null
                        ? "Position rejected by exact proof."
                        : reason
        );

        hintButton.setEnabled(false);
    }


    public void setStatus(String status) {
        statusValue.setText(status);
    }


    // =========================================================
    // Progress display
    // =========================================================

    public void setProgress(
            String family,
            EndgameStudyProgress.Status current,
            int inProgress,
            int completed,
            int mastered,
            long curriculumTotal,
            long cursor,
            int reviewCount,
            EndgameStudyProgress.StudyOrder order
    ) {
        String label =
                family == null
                        || "Mixed".equals(family)
                        ? "ALL FAMILIES"
                        : family;

        masteryTitle.setText(
                label + " MASTERY"
        );

        long denominator =
                Math.max(
                        curriculumTotal,
                        0
                );

        int percentage =
                denominator > 0
                        ? (int) Math.min(
                        100,
                        Math.round(
                                mastered
                                        * 100.0
                                        / denominator
                        )
                )
                        : 0;

        masteryBar.setValue(percentage);

        masteryValue.setText(
                denominator > 0
                        ? String.format(
                        "%,d / %,d mastered",
                        mastered,
                        denominator
                )
                        : String.format(
                        "%,d mastered",
                        mastered
                )
        );

        completeValue.setText(
                denominator > 0
                        ? String.format(
                        "Complete: %,d / %,d",
                        completed,
                        denominator
                )
                        : String.format(
                        "Complete: %,d",
                        completed
                )
        );

        inProgressValue.setText(
                denominator > 0
                        ? String.format(
                        "In progress: %,d / %,d",
                        inProgress,
                        denominator
                )
                        : String.format(
                        "In progress: %,d",
                        inProgress
                )
        );

        reviewValue.setText(
                String.format(
                        "Review stack: %,d",
                        reviewCount
                )
        );

        orderPositionValue.setText(
                (order == EndgameStudyProgress.StudyOrder.ORDERED
                        ? "ORDERED"
                        : "SHUFFLE")
                        +
                        (denominator > 0
                                ? String.format(
                                "  •  Position %,d / %,d",
                                Math.min(
                                        cursor + 1,
                                        denominator
                                ),
                                denominator
                        )
                                : "Mixed".equals(family)
                                ? String.format(
                                "  •  Position %,d",
                                cursor + 1
                        )
                                : "")
        );

        setStudyOrder(order);

        resetFamilyButton.setText(
                family == null
                        || "Mixed".equals(family)
                        ? "Reset Current Family"
                        : "Reset "
                        + family
                        + " Progress"
        );
    }


    // =========================================================
    // Theme
    // =========================================================

    public void applyTheme(boolean dark) {
        background =
                dark
                        ? new java.awt.Color(19, 27, 35)
                        : new java.awt.Color(250, 251, 253);

        card =
                dark
                        ? new java.awt.Color(24, 33, 42)
                        : java.awt.Color.WHITE;

        control =
                dark
                        ? new java.awt.Color(30, 40, 50)
                        : new java.awt.Color(244, 246, 249);

        border =
                dark
                        ? new java.awt.Color(46, 58, 70)
                        : new java.awt.Color(216, 222, 230);

        primary =
                dark
                        ? new java.awt.Color(242, 244, 247)
                        : new java.awt.Color(31, 35, 41);

        secondary =
                dark
                        ? new java.awt.Color(164, 173, 184)
                        : new java.awt.Color(100, 107, 117);

        accent =
                dark
                        ? new java.awt.Color(110, 156, 214)
                        : new java.awt.Color(55, 105, 170);

        setBackground(background);

        theme(this);

        masteryBar.setForeground(accent);
        masteryBar.setBackground(control);

        familyBox.setBackground(control);
        familyBox.setForeground(primary);

        orderBox.setBackground(control);
        orderBox.setForeground(primary);

        for (JButton button : new JButton[]{
                hintButton,
                giveUpButton,
                nextButton,
                previousMoveButton,
                nextMoveButton,
                resetProgressButton,
                resetFamilyButton
        }) {
            button.setBackground(control);
            button.setForeground(primary);
            button.setBorder(
                    BorderFactory.createLineBorder(
                            border,
                            1,
                            true
                    )
            );
        }

        repaint();
    }


    // =========================================================
    // Helpers
    // =========================================================

    private void updateHero(String name) {
        String value =
                name == null
                        || name.isBlank()
                        ? "Random"
                        : name;

        familyHero.setText(
                value.toUpperCase()
        );

        familyDescription.setText(
                "Random".equals(value)
                        ? "Explore across every solved family"
                        : description(value)
        );
    }


    private String description(String family) {
        if (family.equals("KQK")) {
            return "Queen and king technique";
        }

        if (family.equals("KRK")) {
            return "Rook and king technique";
        }

        if (family.equals("KPK")) {
            return "Pawn conversion fundamentals";
        }

        return family.contains("-")
                ? "Split-material exact endgame"
                : "Same-side material exact endgame";
    }


    private JPanel cardPanel() {
        JPanel panel =
                new JPanel();

        panel.putClientProperty(
                "curriculumCard",
                true
        );

        panel.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        panel.setMaximumSize(
                new Dimension(
                        Integer.MAX_VALUE,
                        Integer.MAX_VALUE
                )
        );

        return panel;
    }


    private JLabel text(
            String text,
            boolean bold,
            int size
    ) {
        JLabel label =
                new JLabel(
                        text,
                        SwingConstants.CENTER
                );

        label.setFont(
                new Font(
                        Font.SANS_SERIF,
                        bold
                                ? Font.BOLD
                                : Font.PLAIN,
                        size
                )
        );

        label.putClientProperty(
                bold
                        ? "primary"
                        : "secondary",
                true
        );

        return label;
    }


    private static JButton button(String text) {
        JButton button =
                new JButton(text);

        button.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.BOLD,
                        12
                )
        );

        button.setFocusPainted(false);

        button.setCursor(
                Cursor.getPredefinedCursor(
                        Cursor.HAND_CURSOR
                )
        );

        button.setMaximumSize(
                new Dimension(
                        Integer.MAX_VALUE,
                        38
                )
        );

        return button;
    }


    private void theme(Container container) {
        for (Component component : container.getComponents()) {

            if (component instanceof JPanel panel
                    && Boolean.TRUE.equals(
                    panel.getClientProperty(
                            "curriculumCard"
                    )
            )) {
                panel.setBackground(card);
                panel.setBorder(
                        BorderFactory.createLineBorder(
                                border,
                                1,
                                true
                        )
                );
            }

            if (component instanceof JLabel label) {
                label.setForeground(
                        Boolean.TRUE.equals(
                                label.getClientProperty(
                                        "secondary"
                                )
                        )
                                ? secondary
                                : primary
                );
            }

            if (component instanceof Container child) {
                theme(child);
            }
        }
    }


    private static void addCenteredButton(
            JPanel parent,
            JButton button,
            int width
    ) {
        Dimension size =
                new Dimension(
                        width,
                        38
                );

        button.setPreferredSize(size);
        button.setMinimumSize(size);
        button.setMaximumSize(size);

        addCenteredComponent(
                parent,
                button
        );
    }


    private static void addCenteredComponent(
            JPanel parent,
            JComponent component
    ) {
        Box row =
                Box.createHorizontalBox();

        row.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        int height =
                Math.max(
                        component.getPreferredSize().height,
                        30
                );

        row.setMinimumSize(
                new Dimension(
                        0,
                        height
                )
        );

        row.setPreferredSize(
                new Dimension(
                        component.getPreferredSize().width,
                        height
                )
        );

        row.setMaximumSize(
                new Dimension(
                        Integer.MAX_VALUE,
                        height
                )
        );

        row.add(
                Box.createHorizontalGlue()
        );
        row.add(component);
        row.add(
                Box.createHorizontalGlue()
        );

        parent.add(row);
    }


    private static void makeFullWidthCentered(
            JLabel label
    ) {
        label.setHorizontalAlignment(
                SwingConstants.CENTER
        );

        label.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        Dimension preferred =
                label.getPreferredSize();

        label.setMaximumSize(
                new Dimension(
                        Integer.MAX_VALUE,
                        preferred.height + 2
                )
        );
    }


    private static void run(Runnable runnable) {
        if (runnable != null) {
            runnable.run();
        }
    }
}


package main.java.chess.gui;

import main.java.chess.endgame.EndgameSettings;
import main.java.chess.endgame.ExactEndgameTablebase;
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
            new EndgameWorkspace.TelemetryLabel("Exact tablebase");

    private final JLabel instructionValue =
            new JLabel(
                    "Choose a family to begin",
                    SwingConstants.CENTER
            );

    private final JTextArea statusValue = EndgameWorkspace.wrapping("Progress is saved automatically.");

    private final JLabel masteryValue =
            new EndgameWorkspace.TelemetryLabel("0 mastered");

    private final JLabel masteryTitle =
            new JLabel(
                    "ALL FAMILIES MASTERY",
                    SwingConstants.CENTER
            );

    private final JLabel orderPositionValue =
            new EndgameWorkspace.TelemetryLabel("ORDERED");

    private final JLabel inProgressValue =
            new EndgameWorkspace.TelemetryLabel("0");

    private final JLabel completeValue =
            new EndgameWorkspace.TelemetryLabel("0");

    private final JLabel reviewValue =
            new EndgameWorkspace.TelemetryLabel("0");

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

    private final EndgamePlayedLine playedLine = new EndgamePlayedLine();

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

    public EndgameCurriculumPanel() {
        setLayout(new BorderLayout(0, 10));
        setPreferredSize(new Dimension(800, 650));
        setMinimumSize(new Dimension(0, 0));
        add(EndgameWorkspace.heading("ENDGAME", "Master exact 3- and 4-piece endings",
                "EXACT SOLUTION TRAINING  /  CURRICULUM"), BorderLayout.NORTH);

        familyHero.setFont(new Font("Segoe UI", Font.BOLD, 25));
        familyDescription.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        familyDescription.putClientProperty("secondary", true);
        sideValue.setFont(new Font("Segoe UI", Font.BOLD, 17));
        instructionValue.setFont(new Font("Segoe UI", Font.BOLD, 18));
        masteryValue.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        for (JLabel label : new JLabel[]{proofValue, orderPositionValue, inProgressValue,
                completeValue, reviewValue, moveReviewValue}) EndgameWorkspace.telemetry(label, label == proofValue);
        masteryTitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        masteryTitle.putClientProperty("secondary", true);
        for (JLabel label : new JLabel[]{familyHero, familyDescription, sideValue, proofValue,
                instructionValue, masteryTitle, masteryValue, orderPositionValue,
                inProgressValue, completeValue, reviewValue, moveReviewValue}) {
            label.setHorizontalAlignment(SwingConstants.LEFT);
            label.setMinimumSize(new Dimension(0, label.getPreferredSize().height));
        }
        familyBox.getAccessibleContext().setAccessibleName("Endgame family");
        orderBox.getAccessibleContext().setAccessibleName("Curriculum order");
        statusValue.getAccessibleContext().setAccessibleName("Current study feedback");

        JPanel family = EndgameWorkspace.transparent(new BorderLayout(12, 0));
        family.add(familyHero, BorderLayout.CENTER);
        family.add(familyBox, BorderLayout.EAST);
        JPanel study = EndgameWorkspace.card(new BorderLayout(0, 8));
        study.setName("currentStudyCard");
        study.add(EndgameWorkspace.section("CURRENT STUDY"), BorderLayout.NORTH);
        JLabel defense = EndgameWorkspace.section("TABLEBASE DEFENSE  \u2022  EXACT");
        defense.putClientProperty("accent", true);
        statusValue.setRows(1);
        JPanel exactState = EndgameWorkspace.transparent(new BorderLayout(8, 0));
        exactState.add(sideValue, BorderLayout.WEST);
        exactState.add(proofValue, BorderLayout.CENTER);
        study.add(EndgameWorkspace.top(EndgameWorkspace.stack(4, family, familyDescription,
                exactState, defense, instructionValue, statusValue)), BorderLayout.CENTER);

        JPanel order = EndgameWorkspace.transparent(new BorderLayout(8, 0));
        orderBox.setToolTipText("Study order");
        order.add(orderBox, BorderLayout.CENTER);
        order.add(resetFamilyButton, BorderLayout.EAST);
        masteryBar.setStringPainted(false);
        masteryBar.getAccessibleContext().setAccessibleName("Curriculum mastery percentage");
        JPanel progress = EndgameWorkspace.card(new BorderLayout(0, 8));
        progress.setName("masteryCard");
        progress.add(masteryTitle, BorderLayout.NORTH);
        progress.add(EndgameWorkspace.top(EndgameWorkspace.stack(5, masteryValue, masteryBar,
                new EndgameWorkspace.TelemetryCells(completeValue, inProgressValue, reviewValue), order,
                orderPositionValue)), BorderLayout.CENTER);

        previousMoveButton.setToolTipText("Previous move (Left Arrow)");
        nextMoveButton.setToolTipText("Next move (Right Arrow)");
        previousMoveButton.setEnabled(false);
        nextMoveButton.setEnabled(false);
        JPanel navigation = EndgameWorkspace.transparent(new GridLayout(1, 2, 8, 0));
        navigation.add(previousMoveButton);
        navigation.add(nextMoveButton);
        JPanel path = EndgameWorkspace.card(new BorderLayout(0, 8));
        path.setName("playedLineCard");
        path.add(new EndgameWorkspace.Pair(EndgameWorkspace.stack(6,
                EndgameWorkspace.section("PLAYED LINE / MOVE REVIEW"), moveReviewValue), navigation), BorderLayout.NORTH);
        path.add(playedLine, BorderLayout.CENTER);

        JPanel body = new EndgameWorkspace.Body(new EndgameWorkspace.Pair(study, progress), path, true);
        add(EndgameWorkspace.scroll(body), BorderLayout.CENTER);
        JPanel footer = EndgameWorkspace.actions(hintButton, giveUpButton, nextButton);
        JPanel actionHeading = EndgameWorkspace.transparent(new BorderLayout(8, 0));
        actionHeading.add(EndgameWorkspace.section("TRAINING ACTIONS / AUTO-SAVED"), BorderLayout.CENTER);
        resetProgressButton.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        resetProgressButton.setPreferredSize(new Dimension(194, 24));
        actionHeading.add(resetProgressButton, BorderLayout.EAST);
        footer.remove(((BorderLayout) footer.getLayout()).getLayoutComponent(BorderLayout.NORTH));
        footer.add(actionHeading, BorderLayout.NORTH);
        add(footer, BorderLayout.SOUTH);

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

        playedLine.select(safeIndex, safeLast);

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


    /** Presentation snapshot of committed curriculum history; never asks for a continuation. */
    public void setPlayedLine(List<Position> history, ExactEndgameTablebase tablebase) {
        playedLine.setHistory(history, tablebase);
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

        completeValue.setText(String.format("%,d", completed));
        inProgressValue.setText(String.format("%,d", inProgress));
        reviewValue.setText(String.format("%,d", reviewCount));

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

    @Override public void doLayout() { EndgameWorkspace.adapt(this); super.doLayout(); }

    public void applyTheme(boolean dark) {
        EndgameWorkspace.theme(this, dark);
        playedLine.applyTheme(dark);
    }

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
                ("Random".equals(value) || "Mixed".equals(value))
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


    private static JButton button(String text) {
        return EndgameWorkspace.button(text);
    }

    private static void run(Runnable runnable) {
        if (runnable != null) {
            runnable.run();
        }
    }
}


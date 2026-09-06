package main.java.chess.gui;

import main.java.chess.endgame.EndgameSettings;
import main.java.chess.endgame.EndgameStudyProgress;
import main.java.chess.model.Position;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public class EndgameSolverPanel extends JPanel {
    private final JLabel modeValue=new JLabel();
    private final JLabel sideValue=new JLabel();
    private final JLabel familyValue=new JLabel("Random exact family");
    private final JTextArea statusValue=EndgameWorkspace.wrapping("Analysis is hidden while you solve.");
    private final JLabel proofValue=new JLabel("Exact solution");
    private final JLabel progressValue=new JLabel("Current: Unseen");
    private final JLabel totalsValue=new JLabel("Attempted: 0  •  Mastered: 0");
    private final JComboBox<String> familyBox=new JComboBox<>();
    private final JCheckBox practiceMode=new JCheckBox("Practice defense");
    private final JSlider practiceStrength=new JSlider(0,100,100);
    private final JButton hintButton=button("Hint");
    private final JButton giveUpButton=button("Give Up");
    private final JButton nextButton=button("Next Endgame");
    private final JButton newButton=button("Generation Options");
    private final JButton resetProgressButton=button("Reset Endgame Progress");

    private Runnable hintListener,giveUpListener,nextListener,newListener,resetProgressListener;
    private Consumer<Boolean> practiceModeListener;
    private IntConsumer practiceStrengthListener;
    private Consumer<String> familyListener;
    private boolean updatingFamily;

    public EndgameSolverPanel(){
        setLayout(new BorderLayout(0, 10));
        setPreferredSize(new Dimension(800, 650));
        setMinimumSize(new Dimension(0, 0));
        add(EndgameWorkspace.heading("ENDGAME SOLVER", "Generate and prove exact endgame positions",
                "EXACT SOLUTION TRAINING  /  SOLVER"), BorderLayout.NORTH);
        modeValue.setFont(new Font("Segoe UI", Font.BOLD, 23));
        sideValue.setFont(new Font("Segoe UI", Font.BOLD, 17));
        familyValue.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        familyValue.putClientProperty("secondary", true);
        EndgameWorkspace.telemetry(proofValue, true);
        EndgameWorkspace.telemetry(totalsValue, false);
        progressValue.setFont(new Font("Segoe UI", Font.BOLD, 16));
        familyBox.getAccessibleContext().setAccessibleName("Solver family");
        practiceStrength.getAccessibleContext().setAccessibleName("Practice defense strength");
        practiceStrength.setToolTipText("Practice defense strength: 0–100");
        JPanel study = EndgameWorkspace.card(new BorderLayout(0, 12));
        study.add(EndgameWorkspace.section("CURRENT STUDY"), BorderLayout.NORTH);
        study.add(EndgameWorkspace.stack(10, familyBox, familyValue, modeValue, sideValue,
                proofValue, statusValue), BorderLayout.CENTER);
        JPanel defense = EndgameWorkspace.card(new BorderLayout(0, 12));
        defense.add(EndgameWorkspace.section("DEFENSE & PROGRESS"), BorderLayout.NORTH);
        defense.add(EndgameWorkspace.stack(10, progressValue, totalsValue, practiceMode,
                practiceStrength, newButton), BorderLayout.CENTER);
        JPanel note = EndgameWorkspace.card(new BorderLayout(0, 6));
        note.add(EndgameWorkspace.section("EXACT SOLUTION"), BorderLayout.NORTH);
        note.add(EndgameWorkspace.wrapping("Find the exact best move. Analysis remains hidden while you solve."), BorderLayout.CENTER);
        add(EndgameWorkspace.scroll(new EndgameWorkspace.Body(new EndgameWorkspace.Pair(study, defense), note)), BorderLayout.CENTER);
        JPanel footer = EndgameWorkspace.transparent(new BorderLayout(0, 6));
        footer.add(EndgameWorkspace.actions(hintButton, giveUpButton, nextButton), BorderLayout.CENTER);
        footer.add(resetProgressButton, BorderLayout.SOUTH);
        add(footer, BorderLayout.SOUTH);

        hintButton.addActionListener(e->run(hintListener)); giveUpButton.addActionListener(e->run(giveUpListener));
        nextButton.addActionListener(e->run(nextListener)); newButton.addActionListener(e->run(newListener));
        resetProgressButton.addActionListener(e->run(resetProgressListener));
        practiceMode.addActionListener(e->{if(practiceModeListener!=null)practiceModeListener.accept(practiceMode.isSelected());});
        ChangeListener strengthChange=e->{if(!practiceStrength.getValueIsAdjusting()&&practiceStrengthListener!=null)practiceStrengthListener.accept(practiceStrength.getValue());};
        practiceStrength.addChangeListener(strengthChange);
        familyBox.addActionListener(e->{if(!updatingFamily&&familyListener!=null&&familyBox.getSelectedItem()!=null)familyListener.accept((String)familyBox.getSelectedItem());});
        applyTheme(true);
    }

    public void setFamilies(List<String> families,String selected){
        updatingFamily=true; familyBox.removeAllItems(); for(String f:families)familyBox.addItem(f);
        if(selected!=null)familyBox.setSelectedItem(selected); updatingFamily=false;
    }
    public String getSelectedFamily(){Object o=familyBox.getSelectedItem();return o==null?"Random":o.toString();}
    public void setFamilyListener(Consumer<String> l){familyListener=l;}
    public void setResetProgressListener(Runnable l){resetProgressListener=l;}
    public void setProgress(EndgameStudyProgress.Status current,int attempted,int mastered){
        String s=current==null?"Unseen":switch(current){case UNSEEN->"Unseen";case ATTEMPTED->"Attempted";case MASTERED->"Mastered ✓";};
        progressValue.setText("Current: "+s); totalsValue.setText("Attempted: "+attempted+"  •  Mastered: "+mastered);
    }
    public void setPosition(Position p,EndgameSettings settings){if(p==null||settings==null)return;modeValue.setText(settings.displayName());sideValue.setText((p.getSideToMove()==main.java.chess.model.Color.WHITE?"White":"Black")+" to move");statusValue.setText("Find the exact best move.");}
    public void setLoadingTablebase(String name){
        familyValue.setText(name+" • loading exact tablebase…");
        statusValue.setText("Loading exact solution…");
        proofValue.setText("Exact solution");
        hintButton.setEnabled(false);
    }

    // Preserved from the pre-curriculum Endgame Study panel.
    // ChessWindow uses this while a generated study is being proven exactly.
    public void setProving(){
        statusValue.setText("Proving the position exactly…");
        proofValue.setText("Exact solution • preparing…");
        hintButton.setEnabled(false);
    }

    public void setFamilyDisplay(String name){
        familyValue.setText(name==null?"Exact family":name);
    }

    public void setProvenMate(int mate){
        proofValue.setText(mate>=0?"Exact WIN • DTM "+mate:"Exact WIN");
        statusValue.setText("Exact solution loaded • Analysis hidden.");
        hintButton.setEnabled(true);
    }

    // Preserved API used when a generated root fails exact-study eligibility.
    public void setRejected(String reason){
        statusValue.setText(reason==null?"Generating another exact position…":reason);
        proofValue.setText("Generating another position…");
        hintButton.setEnabled(false);
    }

    public void setStatus(String text){statusValue.setText(text);}
    public void setHintListener(Runnable l){hintListener=l;} public void setGiveUpListener(Runnable l){giveUpListener=l;}
    public void setNextListener(Runnable l){nextListener=l;} public void setNewListener(Runnable l){newListener=l;}
    public void setPracticeMode(boolean enabled){practiceMode.setSelected(enabled);practiceStrength.setEnabled(enabled);}
    public boolean isPracticeMode(){return practiceMode.isSelected();}
    public void setPracticeStrength(int strength){practiceStrength.setValue(Math.max(0,Math.min(100,strength)));}
    public int getPracticeStrength(){return practiceStrength.getValue();}
    public void setPracticeModeListener(Consumer<Boolean> l){practiceModeListener=l;}
    public void setPracticeStrengthListener(IntConsumer l){practiceStrengthListener=l;}

    @Override public void doLayout() { EndgameWorkspace.adapt(this); super.doLayout(); }

    public void applyTheme(boolean dark) { EndgameWorkspace.theme(this, dark); }
    private static JButton button(String text) { return EndgameWorkspace.button(text); }
    private static void run(Runnable r){if(r!=null)r.run();}
}

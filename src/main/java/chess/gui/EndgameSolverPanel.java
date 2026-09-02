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
    private final JLabel statusValue=new JLabel("Analysis is hidden while you solve.");
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
    private java.awt.Color primary,secondary,background,control,border;

    public EndgameSolverPanel(){
        setLayout(new BorderLayout()); setPreferredSize(new Dimension(390,640));
        JPanel content=new JPanel(); content.setOpaque(false); content.setLayout(new BoxLayout(content,BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(18,18,18,18));
        content.add(label("ENDGAME SOLVER",true,13)); content.add(Box.createVerticalStrut(3));
        content.add(label("Generate and prove exact endgame positions",false,12)); content.add(Box.createVerticalStrut(16));
        content.add(section("FAMILY")); content.add(Box.createVerticalStrut(5));
        familyBox.setMaximumSize(new Dimension(Integer.MAX_VALUE,32)); content.add(familyBox); content.add(Box.createVerticalStrut(5));
        familyValue.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,11)); familyValue.putClientProperty("secondary",true); content.add(familyValue);
        content.add(Box.createVerticalStrut(13)); content.add(section("STUDY")); content.add(Box.createVerticalStrut(5));
        modeValue.setFont(new Font(Font.SANS_SERIF,Font.BOLD,12)); content.add(modeValue); content.add(Box.createVerticalStrut(3));
        sideValue.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,12)); content.add(sideValue); content.add(Box.createVerticalStrut(3));
        proofValue.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,12)); content.add(proofValue);
        content.add(Box.createVerticalStrut(13)); content.add(section("PROGRESS")); content.add(Box.createVerticalStrut(5));
        progressValue.setFont(new Font(Font.SANS_SERIF,Font.BOLD,12)); content.add(progressValue); content.add(Box.createVerticalStrut(3));
        totalsValue.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,11)); totalsValue.putClientProperty("secondary",true); content.add(totalsValue);
        content.add(Box.createVerticalStrut(14)); statusValue.setFont(new Font(Font.SANS_SERIF,Font.BOLD,12)); content.add(statusValue);
        content.add(Box.createVerticalStrut(12)); content.add(hintButton); content.add(Box.createVerticalStrut(6)); content.add(giveUpButton);
        content.add(Box.createVerticalStrut(12)); content.add(practiceMode); content.add(Box.createVerticalStrut(3));
        practiceStrength.setMaximumSize(new Dimension(Integer.MAX_VALUE,36)); content.add(practiceStrength);
        content.add(Box.createVerticalGlue()); content.add(nextButton); content.add(Box.createVerticalStrut(6)); content.add(newButton);
        content.add(Box.createVerticalStrut(6)); content.add(resetProgressButton); add(content,BorderLayout.CENTER);

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

    public void applyTheme(boolean dark){
        background=dark?new java.awt.Color(19,27,35):new java.awt.Color(250,251,253); primary=dark?new java.awt.Color(242,244,247):new java.awt.Color(31,35,41);
        secondary=dark?new java.awt.Color(164,173,184):new java.awt.Color(100,107,117); control=dark?new java.awt.Color(26,35,44):new java.awt.Color(244,246,249); border=dark?new java.awt.Color(42,53,64):new java.awt.Color(210,216,224);
        setBackground(background); setBorder(BorderFactory.createLineBorder(border,1,true)); theme(this);
        familyBox.setForeground(primary); familyBox.setBackground(control); practiceMode.setForeground(primary); practiceMode.setOpaque(false);
        practiceStrength.setOpaque(false); for(JButton b:new JButton[]{hintButton,giveUpButton,nextButton,newButton,resetProgressButton}){b.setForeground(primary);b.setBackground(control);b.setBorder(BorderFactory.createLineBorder(border,1,true));b.setOpaque(true);b.setContentAreaFilled(true);}
        repaint();
    }
    private JLabel section(String t){JLabel l=label(t,false,10);l.putClientProperty("secondary",true);return l;}
    private JLabel label(String t,boolean bold,int size){JLabel l=new JLabel(t);l.setFont(new Font(Font.SANS_SERIF,bold?Font.BOLD:Font.PLAIN,size));l.putClientProperty(bold?"primary":"secondary",true);return l;}
    private static JButton button(String t){JButton b=new JButton(t);b.setFont(new Font(Font.SANS_SERIF,Font.BOLD,12));b.setFocusPainted(false);b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));b.setMaximumSize(new Dimension(Integer.MAX_VALUE,38));return b;}
    private static void run(Runnable r){if(r!=null)r.run();}
    private void theme(Container c){for(Component x:c.getComponents()){if(x instanceof JLabel l)l.setForeground(Boolean.TRUE.equals(l.getClientProperty("secondary"))?secondary:primary);if(x instanceof Container child)theme(child);}}
}

package dev.mx.launcher;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import javax.swing.event.*;

final class PlayerDollSettingsDialog extends JDialog {
    private final JTextArea message=new JTextArea();
    private Timer playerEdit;
    private Runnable commitPending=()->{};
    PlayerDollSettingsDialog(Window owner,PlayerDoll doll){
        super(owner,"玩家玩偶",ModalityType.MODELESS);setName("playerDollSettings");PlayerDollOptions initial=doll.options();
        UiTheme.Surface root=new UiTheme.Surface(new BorderLayout(0,16),0);root.setBorder(new EmptyBorder(22,24,20,24));root.add(UiTheme.label("玩家玩偶",22,true),BorderLayout.NORTH);
        JPanel form=UiTheme.transparent(new GridBagLayout());JCheckBox enabled=new UiTheme.Toggle("显示玩家玩偶",initial.enabled),cape=new UiTheme.Toggle("显示官方披风",initial.cape);enabled.setName("dollEnabled");cape.setName("dollCape");
        JComboBox<String> source=new JComboBox<>(new String[]{"自动获取已登录正版账号","输入正版玩家 ID / UUID"});source.setName("dollSource");UiTheme.combo(source);source.setSelectedIndex(initial.manual?1:0);
        JTextField player=new JTextField(initial.player);player.setName("dollPlayerId");player.setFont(UiTheme.font(13,false));player.setEnabled(initial.manual);player.setToolTipText("正版 Java 玩家名，如 Notch；也支持 UUID");
        JSlider size=new JSlider(60,180,initial.scale);size.setName("dollScale");UiTheme.slider(size);JLabel percent=UiTheme.label(initial.scale+"%",12,true);JPanel sizeRow=UiTheme.transparent(new BorderLayout(12,0));sizeRow.add(size);sizeRow.add(percent,BorderLayout.EAST);
        row(form,0,"主页显示",enabled);row(form,1,"玩家来源",source);row(form,2,"玩家 ID",player);row(form,3,"玩偶大小",sizeRow);row(form,4,"披风",cape);
        JPanel body=UiTheme.transparent(new BorderLayout(0,12));body.add(form,BorderLayout.NORTH);JPanel hints=UiTheme.transparent(new BorderLayout(0,8));hints.add(UiTheme.muted("左键互动，按住拖动；滚轮转身，右键打开菜单。",12),BorderLayout.NORTH);
        message.setFont(UiTheme.font(12,false));message.setForeground(UiTheme.p().muted());message.setLineWrap(true);message.setWrapStyleWord(true);message.setOpaque(false);message.setEditable(false);message.setFocusable(false);message.setRows(3);status(doll.notice());hints.add(message);hints.add(UiTheme.muted("玩偶仅在启动器主页显示；未识别皮肤时使用原版 Steve。",11),BorderLayout.SOUTH);body.add(hints,BorderLayout.CENTER);root.add(body);
        JPanel actions=UiTheme.transparent(new FlowLayout(FlowLayout.RIGHT,8,0));UiTheme.Button refresh=new UiTheme.Button("刷新材质","",UiTheme.Tone.SURFACE),close=new UiTheme.Button("关闭","",UiTheme.Tone.SURFACE);
        java.util.function.BooleanSupplier save=()->{
            PlayerDollOptions next=doll.options();next.enabled=enabled.isSelected();next.manual=source.getSelectedIndex()==1;next.scale=size.getValue();next.cape=cape.isSelected();
            boolean valid=PlayerDoll.validPlayer(player.getText());if(valid)next.player=player.getText();
            if(!doll.apply(next))return false;
            status(!valid&&next.manual?"请输入有效的正版 Java 玩家名或 UUID；其他设置已实时保存。":doll.notice());return true;
        };
        playerEdit=new Timer(600,e->{if(source.getSelectedIndex()==1)save.getAsBoolean();});playerEdit.setRepeats(false);
        commitPending=()->{if(playerEdit.isRunning()){playerEdit.stop();save.getAsBoolean();}if(size.getValueIsAdjusting())save.getAsBoolean();};
        enabled.addActionListener(e->save.getAsBoolean());cape.addActionListener(e->save.getAsBoolean());
        source.addActionListener(e->{playerEdit.stop();player.setEnabled(source.getSelectedIndex()==1);save.getAsBoolean();});
        player.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){changed();}public void removeUpdate(DocumentEvent e){changed();}public void changedUpdate(DocumentEvent e){changed();}private void changed(){if(source.getSelectedIndex()==1){status("输入完成后自动获取皮肤与披风…");playerEdit.restart();}}});
        player.addActionListener(e->{playerEdit.stop();save.getAsBoolean();});player.addFocusListener(new FocusAdapter(){@Override public void focusLost(FocusEvent e){if(playerEdit.isRunning()){playerEdit.stop();save.getAsBoolean();}}});
        size.addChangeListener(e->{percent.setText(size.getValue()+"%");doll.previewScale(size.getValue());if(!size.getValueIsAdjusting())save.getAsBoolean();});
        refresh.addActionListener(e->{playerEdit.stop();if(save.getAsBoolean())doll.refresh();});close.addActionListener(e->dispose());actions.add(refresh);actions.add(close);root.add(actions,BorderLayout.SOUTH);
        setContentPane(root);WindowChrome.dialog(this);UiTheme.refresh(root);player.setDisabledTextColor(UiTheme.p().muted());setSize(600,530);setMinimumSize(new Dimension(560,510));setLocationRelativeTo(owner);
    }
    @Override public void dispose(){commitPending.run();if(playerEdit!=null)playerEdit.stop();super.dispose();}
    void status(String text){message.setText(text);}
    private static void row(JPanel form,int row,String label,JComponent component){GridBagConstraints c=new GridBagConstraints();c.gridx=0;c.gridy=row;c.anchor=GridBagConstraints.WEST;c.insets=new Insets(0,0,12,18);form.add(UiTheme.label(label,13,true),c);c.gridx=1;c.weightx=1;c.fill=GridBagConstraints.HORIZONTAL;c.insets=new Insets(0,0,12,0);component.setPreferredSize(new Dimension(340,34));form.add(component,c);}
}

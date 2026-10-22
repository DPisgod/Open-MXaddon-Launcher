package dev.mx.launcher;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

final class PlayerDollSettingsDialog extends JDialog {
    private final JTextArea message=new JTextArea();
    PlayerDollSettingsDialog(Window owner,PlayerDoll doll){
        super(owner,"玩家玩偶",ModalityType.MODELESS);setName("playerDollSettings");PlayerDollOptions initial=doll.options();
        UiTheme.Surface root=new UiTheme.Surface(new BorderLayout(0,16),0);root.setBorder(new EmptyBorder(22,24,20,24));root.add(UiTheme.label("玩家玩偶",22,true),BorderLayout.NORTH);
        JPanel form=UiTheme.transparent(new GridBagLayout());JCheckBox enabled=new UiTheme.Toggle("显示独立桌面玩偶",initial.enabled),cape=new UiTheme.Toggle("显示官方披风",initial.cape),topmost=new UiTheme.Toggle("始终置顶",initial.topmost);enabled.setName("dollEnabled");cape.setName("dollCape");topmost.setName("dollTopmost");
        JComboBox<String> source=new JComboBox<>(new String[]{"自动获取已登录正版账号","输入正版玩家 ID / UUID"});source.setName("dollSource");UiTheme.combo(source);source.setSelectedIndex(initial.manual?1:0);
        JTextField player=new JTextField(initial.player);player.setName("dollPlayerId");player.setFont(UiTheme.font(13,false));player.setEnabled(initial.manual);player.setToolTipText("正版 Java 玩家名，如 Notch；也支持 UUID");source.addActionListener(e->player.setEnabled(source.getSelectedIndex()==1));
        JSlider size=new JSlider(60,180,initial.scale);size.setName("dollScale");UiTheme.slider(size);JLabel percent=UiTheme.label(initial.scale+"%",12,true);size.addChangeListener(e->percent.setText(size.getValue()+"%"));JPanel sizeRow=UiTheme.transparent(new BorderLayout(12,0));sizeRow.add(size);sizeRow.add(percent,BorderLayout.EAST);
        row(form,0,"桌面显示",enabled);row(form,1,"玩家来源",source);row(form,2,"玩家 ID",player);row(form,3,"玩偶大小",sizeRow);row(form,4,"披风",cape);row(form,5,"窗口",topmost);
        JPanel body=UiTheme.transparent(new BorderLayout(0,12));body.add(form,BorderLayout.NORTH);JPanel hints=UiTheme.transparent(new BorderLayout(0,8));hints.add(UiTheme.muted("左键互动，按住拖动；滚轮转身，右键打开菜单。",12),BorderLayout.NORTH);
        message.setFont(UiTheme.font(12,false));message.setForeground(UiTheme.p().muted());message.setLineWrap(true);message.setWrapStyleWord(true);message.setOpaque(false);message.setEditable(false);message.setFocusable(false);message.setRows(3);status(doll.notice());hints.add(message);hints.add(UiTheme.muted("仅显示已启用的官方披风；桌面玩偶与主页玩偶不会同时显示。",11),BorderLayout.SOUTH);body.add(hints,BorderLayout.CENTER);root.add(body);
        JPanel actions=UiTheme.transparent(new FlowLayout(FlowLayout.RIGHT,8,0));UiTheme.Button refresh=new UiTheme.Button("刷新材质","",UiTheme.Tone.SURFACE),close=new UiTheme.Button("关闭","",UiTheme.Tone.SURFACE),apply=new UiTheme.Button("应用","",UiTheme.Tone.PRIMARY);
        java.util.function.BooleanSupplier save=()->{PlayerDollOptions next=doll.options();next.enabled=enabled.isSelected();next.manual=source.getSelectedIndex()==1;next.player=player.getText();next.scale=size.getValue();next.cape=cape.isSelected();next.topmost=topmost.isSelected();if(!doll.apply(next))return false;status("玩偶设置已保存。"+doll.notice());return true;};
        apply.addActionListener(e->save.getAsBoolean());refresh.addActionListener(e->{if(save.getAsBoolean())doll.refresh();});close.addActionListener(e->dispose());actions.add(refresh);actions.add(close);actions.add(apply);root.add(actions,BorderLayout.SOUTH);
        setContentPane(root);WindowChrome.dialog(this);UiTheme.refresh(root);player.setDisabledTextColor(UiTheme.p().muted());setSize(600,580);setMinimumSize(new Dimension(560,540));setLocationRelativeTo(owner);
    }
    void status(String text){message.setText(text);}
    private static void row(JPanel form,int row,String label,JComponent component){GridBagConstraints c=new GridBagConstraints();c.gridx=0;c.gridy=row;c.anchor=GridBagConstraints.WEST;c.insets=new Insets(0,0,12,18);form.add(UiTheme.label(label,13,true),c);c.gridx=1;c.weightx=1;c.fill=GridBagConstraints.HORIZONTAL;c.insets=new Insets(0,0,12,0);component.setPreferredSize(new Dimension(340,34));form.add(component,c);}
}

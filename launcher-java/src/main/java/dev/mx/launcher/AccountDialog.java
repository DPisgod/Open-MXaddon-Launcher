package dev.mx.launcher;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.nio.file.Path;

/** Game account selection, separate from the product authorization account. */
public final class AccountDialog extends JDialog {
    private Session result;
    private boolean premiumRequested,signOutRequested;
    private final JTextField nameField=new JTextField();

    public AccountDialog(Window owner,Path home,Session current){
        super(owner,"添加游戏账号",ModalityType.APPLICATION_MODAL);setDefaultCloseOperation(DISPOSE_ON_CLOSE);setResizable(false);
        UiTheme.Surface root=new UiTheme.Surface(new BorderLayout(0,20),0);root.background=true;root.setBorder(new EmptyBorder(24,26,22,26));
        JPanel heading=UiTheme.transparent(new BorderLayout(0,8));heading.add(UiTheme.label("添加游戏账号",22,true),BorderLayout.NORTH);
        heading.add(UiTheme.muted(current==null?"选择正版登录，或添加中文离线账号。":"当前账号："+current.name+(current.premium?" · 正版":" · 离线"),12),BorderLayout.CENTER);root.add(heading,BorderLayout.NORTH);
        JPanel body=UiTheme.transparent(new GridBagLayout());GridBagConstraints c=new GridBagConstraints();c.gridx=0;c.weightx=1;c.fill=GridBagConstraints.HORIZONTAL;c.anchor=GridBagConstraints.NORTHWEST;
        c.gridy=0;c.insets=new Insets(0,0,7,0);body.add(UiTheme.label("Minecraft 正版账号",13,true),c);
        UiTheme.Button premium=new UiTheme.Button("登入正版账号","user",UiTheme.Tone.PRIMARY);premium.setPreferredSize(new Dimension(100,44));premium.addActionListener(e->{premiumRequested=true;dispose();});
        c.gridy=1;c.insets=new Insets(0,0,7,0);body.add(premium,c);
        c.gridy=2;c.insets=new Insets(0,0,22,0);body.add(UiTheme.muted("通过微软浏览器授权登录，需要拥有 Java 版。",11),c);
        JComponent line=new JComponent(){@Override protected void paintComponent(Graphics g){g.setColor(UiTheme.p().line());g.fillRect(0,0,getWidth(),1);}};line.setPreferredSize(new Dimension(10,1));c.gridy=3;c.insets=new Insets(0,0,20,0);body.add(line,c);
        c.gridy=4;c.insets=new Insets(0,0,7,0);body.add(UiTheme.label("离线账号名称",13,true),c);
        nameField.setName("offlineAccountName");nameField.setText(current!=null&&!current.premium?current.name:"");nameField.setFont(UiTheme.font(14,false));nameField.setBackground(UiTheme.p().surface());nameField.setForeground(UiTheme.p().text());nameField.setCaretColor(UiTheme.p().accent());nameField.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UiTheme.p().line()),new EmptyBorder(9,12,9,12)));nameField.setPreferredSize(new Dimension(100,42));nameField.addActionListener(e->doOfflineLogin());
        c.gridy=5;c.insets=new Insets(0,0,8,0);body.add(nameField,c);
        c.gridy=6;c.insets=new Insets(0,0,12,0);body.add(UiTheme.muted("支持 1–16 个汉字、英文、数字或下划线。",11),c);
        UiTheme.Button offline=new UiTheme.Button("添加并使用离线账号","plus",UiTheme.Tone.SURFACE);offline.setPreferredSize(new Dimension(100,42));offline.addActionListener(e->doOfflineLogin());c.gridy=7;c.weighty=1;c.insets=new Insets(0,0,0,0);body.add(offline,c);root.add(body,BorderLayout.CENTER);
        JPanel bottom=UiTheme.transparent(new FlowLayout(FlowLayout.RIGHT,8,0));
        if(current!=null&&current.premium){UiTheme.Button signOut=new UiTheme.Button("退出正版登录","",UiTheme.Tone.SURFACE);signOut.addActionListener(e->{signOutRequested=true;dispose();});bottom.add(signOut);}
        UiTheme.Button cancel=new UiTheme.Button("取消","",UiTheme.Tone.SURFACE);cancel.addActionListener(e->dispose());bottom.add(cancel);root.add(bottom,BorderLayout.SOUTH);
        setContentPane(root);WindowChrome.dialog(this);setSize(460,510);setMinimumSize(new Dimension(460,510));setLocationRelativeTo(owner);
        addWindowListener(new java.awt.event.WindowAdapter(){@Override public void windowOpened(java.awt.event.WindowEvent e){nameField.requestFocusInWindow();}});
    }
    public Session result(){return result;}
    public boolean premiumRequested(){return premiumRequested;}
    boolean signOutRequested(){return signOutRequested;}
    private void doOfflineLogin(){try{result=Session.offline(Session.offlineName(nameField.getText()));dispose();}catch(IllegalArgumentException e){WindowChrome.prompt(this,"请检查离线名称",e.getMessage(),false);nameField.requestFocusInWindow();}}
}

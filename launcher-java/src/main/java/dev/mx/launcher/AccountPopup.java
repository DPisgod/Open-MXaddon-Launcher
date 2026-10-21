package dev.mx.launcher;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicPopupMenuUI;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/** Upward picker with distinct, keyboard-accessible switch and delete buttons. */
final class AccountPopup extends JPopupMenu {
    private JButton initialFocus;

    AccountPopup(Path home,List<Session> accounts,Session current,Consumer<Session> select,Consumer<Session> remove,Consumer<Session> configureSkin,Runnable add) {
        setUI(new BasicPopupMenuUI());setOpaque(true);
        setLayout(new BorderLayout(0,10));setBackground(UiTheme.p().surface());setForeground(UiTheme.p().text());
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UiTheme.p().line()),new EmptyBorder(14,10,10,10)));
        JLabel title=UiTheme.label("游戏账号",14,true);title.setBorder(new EmptyBorder(0,6,0,0));add(title,BorderLayout.NORTH);
        JPanel rows=new AccountRows();
        for(Session account:accounts) {
            BufferedImage avatar=SkinFetcher.cached(account,home);
            boolean active=current!=null&&AccountStore.key(account).equals(AccountStore.key(current));
            AccountButton choose=new AccountButton(account,avatar,active);
            choose.addActionListener(e->{setVisible(false);select.accept(account);});
            UiTheme.Button skin=new UiTheme.Button("",account.premium?"refresh":"image",UiTheme.Tone.PLAIN);
            String skinAction=(account.premium?"刷新皮肤 ":"设置皮肤 ")+account.name;skin.setToolTipText(skinAction);skin.getAccessibleContext().setAccessibleName(skinAction);skin.setPreferredSize(new Dimension(36,48));skin.addActionListener(e->{setVisible(false);configureSkin.accept(account);});
            UiTheme.Button delete=new UiTheme.Button("","trash",UiTheme.Tone.PLAIN);
            delete.setToolTipText("删除账号 "+account.name);delete.getAccessibleContext().setAccessibleName("删除账号 "+account.name);
            delete.setPreferredSize(new Dimension(36,48));delete.addActionListener(e->{setVisible(false);remove.accept(account);});
            JPanel controls=UiTheme.transparent(new GridLayout(1,2,0,0));controls.add(skin);controls.add(delete);
            JPanel row=UiTheme.transparent(new BorderLayout(4,0));row.setMaximumSize(new Dimension(Integer.MAX_VALUE,62));row.add(choose,BorderLayout.CENTER);row.add(controls,BorderLayout.EAST);rows.add(row);
            if(initialFocus==null||active)initialFocus=choose;
            new SwingWorker<BufferedImage,Void>(){
                @Override protected BufferedImage doInBackground(){return SkinFetcher.fetch(account,home);}
                @Override protected void done(){try{choose.avatar=get();choose.repaint();}catch(Exception ignored){}}
            }.execute();
        }
        if(accounts.isEmpty()){JLabel empty=UiTheme.muted("还没有游戏账号，点击下方添加。",12);empty.setBorder(new EmptyBorder(18,6,18,6));rows.add(empty);}
        JScrollPane scroll=new JScrollPane(rows);scroll.setBorder(null);scroll.setOpaque(true);scroll.setBackground(UiTheme.p().surface());scroll.getViewport().setOpaque(true);scroll.getViewport().setBackground(UiTheme.p().surface());scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);scroll.setPreferredSize(new Dimension(372,Math.max(54,Math.min(6,accounts.size())*62)));UiTheme.scroll(scroll);add(scroll,BorderLayout.CENTER);
        JPanel actions=UiTheme.transparent(new GridLayout(1,1));
        UiTheme.Button addAccount=new UiTheme.Button("添加账号","user",UiTheme.Tone.SURFACE);addAccount.addActionListener(e->{setVisible(false);add.run();});actions.add(addAccount);
        add(actions,BorderLayout.SOUTH);
        if(initialFocus==null)initialFocus=addAccount;
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0),"dismiss");
        getActionMap().put("dismiss",new AbstractAction(){@Override public void actionPerformed(ActionEvent e){setVisible(false);}});
    }

    void showAbove(JComponent anchor){show(anchor,0,-getPreferredSize().height-10);initialFocus.requestFocusInWindow();}
    @Override protected void paintComponent(Graphics graphics){graphics.setColor(UiTheme.p().surface());graphics.fillRect(0,0,getWidth(),getHeight());}

    private static final class AccountRows extends JPanel implements Scrollable {
        AccountRows(){setOpaque(false);setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));}
        @Override public Dimension getPreferredScrollableViewportSize(){return new Dimension(332,248);}
        @Override public int getScrollableUnitIncrement(Rectangle r,int orientation,int direction){return 62;}
        @Override public int getScrollableBlockIncrement(Rectangle r,int orientation,int direction){return Math.max(62,r.height-62);}
        @Override public boolean getScrollableTracksViewportWidth(){return true;}
        @Override public boolean getScrollableTracksViewportHeight(){return false;}
    }

    private static final class AccountButton extends JButton {
        private final Session account;private BufferedImage avatar;private final boolean active;
        AccountButton(Session account,BufferedImage avatar,boolean active){
            this.account=account;this.avatar=avatar;this.active=active;
            setPreferredSize(new Dimension(282,62));setBorderPainted(false);setContentAreaFilled(false);setOpaque(false);setFocusPainted(false);setRolloverEnabled(true);setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            getAccessibleContext().setAccessibleName("切换账号 "+account.name+(account.premium?" 正版":" 离线")+(active?" 当前":""));setToolTipText(account.name);
        }
        @Override protected void paintComponent(Graphics graphics){
            Graphics2D g=(Graphics2D)graphics.create();UiTheme.quality(g);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            if(active||getModel().isRollover()||UiTheme.showFocus(this)){g.setColor(UiTheme.p().background());g.fillRoundRect(0,3,getWidth(),getHeight()-6,12,12);}
            g.drawImage(avatar,10,13,36,36,null);g.setColor(UiTheme.p().text());g.setFont(UiTheme.font(13,true));
            String name=account.name;FontMetrics fm=g.getFontMetrics();int max=getWidth()-(active?100:66);while(name.length()>1&&fm.stringWidth(name)>max)name=name.substring(0,name.offsetByCodePoints(name.length(),-1));if(!name.equals(account.name))name+="…";
            g.drawString(name,58,27);g.setColor(UiTheme.p().muted());g.setFont(UiTheme.font(10,false));g.drawString(account.premium?"Minecraft 正版账号":"Minecraft 离线账号",58,46);
            if(active){g.setColor(UiTheme.p().accent());g.drawString("当前",getWidth()-34,32);}g.dispose();
        }
    }
}

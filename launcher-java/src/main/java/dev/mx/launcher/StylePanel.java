package dev.mx.launcher;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Component styles and theme settings; wallpaper settings have their own entry. */
final class StylePanel extends JPanel {
    private Appearance saved;
    private final Predicate<Appearance> apply;
    private final List<StyleCard> cards=new ArrayList<>();
    private final List<UiTheme.Button> themes=new ArrayList<>();
    private final JLabel message=UiTheme.muted("选择后立即生效并保存。霓虹赛博默认使用深色，也可搭配浅色。",12);
    private final JSlider transparency;
    StylePanel(Appearance appearance,Predicate<Appearance> apply,Runnable back){
        this.saved=appearance.copy();this.apply=apply;setName("stylePanel");setOpaque(false);setLayout(new BorderLayout(0,18));setBorder(new EmptyBorder(24,30,24,30));
        JPanel heading=UiTheme.transparent(new BorderLayout());JPanel titles=UiTheme.transparent(new GridLayout(2,1,0,4));titles.add(UiTheme.label("选择你的界面风格",24,true));titles.add(UiTheme.muted("从按钮到面板，让每一次启动都有自己的个性。",12));heading.add(titles);
        UiTheme.Button home=new UiTheme.Button("返回主页","home",UiTheme.Tone.SURFACE);home.addActionListener(e->back.run());heading.add(home,BorderLayout.EAST);add(heading,BorderLayout.NORTH);
        JPanel body=UiTheme.transparent(new BorderLayout(0,18));
        JPanel styles=UiTheme.transparent(new GridLayout(1,3,16,0));String[] ids={"rounded","minimal","cyber"},names={"柔和圆角","简约直角","霓虹赛博"},descriptions={"舒展圆角 · 轻盈日常","利落边界 · 简洁专注","青紫霓虹 · 金属质感"};
        for(int i=0;i<ids.length;i++){StyleCard card=new StyleCard(ids[i],names[i],descriptions[i]);cards.add(card);styles.add(card);}styles.setPreferredSize(new Dimension(700,250));body.add(styles,BorderLayout.NORTH);
        UiTheme.Surface settings=new UiTheme.Surface(new GridBagLayout(),22);settings.setBorder(new EmptyBorder(16,24,16,24));GridBagConstraints c=new GridBagConstraints();c.gridx=0;c.gridy=0;c.anchor=GridBagConstraints.WEST;c.insets=new Insets(6,0,10,16);settings.add(UiTheme.label("明暗模式",15,true),c);
        JPanel modes=UiTheme.transparent(new FlowLayout(FlowLayout.LEFT,6,0));String[] values={"light","dark","system"},labels={"浅色","深色","跟随系统"},icons={"sun","moon","system"};
        for(int i=0;i<values.length;i++){String mode=values[i];UiTheme.Button button=new UiTheme.Button(labels[i],icons[i],UiTheme.Tone.NAV);button.setName("theme-"+mode);button.addActionListener(e->{Appearance value=saved.copy();value.theme=mode;save(value);});themes.add(button);modes.add(button);}c.gridx=1;c.weightx=1;c.fill=GridBagConstraints.HORIZONTAL;settings.add(modes,c);
        c.gridx=0;c.gridy=1;c.weightx=0;c.fill=GridBagConstraints.NONE;settings.add(UiTheme.label("UI 透明度",15,true),c);
        transparency=new JSlider(0,100,saved.uiTransparency);transparency.setName("uiTransparency");transparency.setToolTipText("仅调整主页顶栏和启动栏背景；0% 不透明，100% 完全透明");UiTheme.slider(transparency);JLabel percent=UiTheme.label(saved.uiTransparency+"%",13,true);percent.setPreferredSize(new Dimension(44,30));JPanel slider=UiTheme.transparent(new BorderLayout(12,0));slider.add(transparency);slider.add(percent,BorderLayout.EAST);c.gridx=1;c.weightx=1;c.fill=GridBagConstraints.HORIZONTAL;settings.add(slider,c);
        transparency.addChangeListener(e->{percent.setText(transparency.getValue()+"%");if(!transparency.getValueIsAdjusting()){Appearance value=saved.copy();value.uiTransparency=transparency.getValue();if(!save(value))transparency.setValue(saved.uiTransparency);}});
        c.gridx=0;c.gridy=2;c.gridwidth=2;settings.add(UiTheme.muted("透明度作用于主页顶栏与启动栏背景，文字和按钮保持清晰。壁纸、模糊和播放请到右上角“壁纸”。",12),c);
        body.add(settings,BorderLayout.CENTER);add(body,BorderLayout.CENTER);add(message,BorderLayout.SOUTH);refreshSelection();
    }
    private boolean save(Appearance value){if(!apply.test(value)){message.setText("保存失败，请检查启动器目录的写入权限。");return false;}saved=value.copy();refreshSelection();message.setText("已保存界面风格，壁纸设置保持不变。");return true;}
    private void refreshSelection(){String[] modes={"light","dark","system"};for(int i=0;i<themes.size();i++)themes.get(i).selected=modes[i].equals(saved.theme);repaint();}
    @Override protected void paintComponent(Graphics g){g.setColor(UiTheme.p().background());g.fillRect(0,0,getWidth(),getHeight());super.paintComponent(g);}
    private final class StyleCard extends JButton {
        private final String id,title,subtitle;
        StyleCard(String id,String title,String subtitle){this.id=id;this.title=title;this.subtitle=subtitle;setName("style-"+id);getAccessibleContext().setAccessibleName(title);setContentAreaFilled(false);setBorderPainted(false);setFocusPainted(false);setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));addActionListener(e->{Appearance value=saved.copy();value.buttonStyle=id;if("cyber".equals(id)&&!"cyber".equals(saved.buttonStyle))value.theme="dark";save(value);});}
        @Override protected void paintComponent(Graphics graphics){
            Graphics2D g=(Graphics2D)graphics.create();UiTheme.quality(g);int w=getWidth(),h=getHeight();boolean neon="cyber".equals(id),minimal="minimal".equals(id),selected=id.equals(saved.buttonStyle);int arc=minimal?6:18;
            g.setColor(neon?new Color(0x0A1022):UiTheme.p().surface());g.fillRoundRect(1,1,w-2,h-2,arc,arc);
            g.setPaint(neon?new GradientPaint(0,0,new Color(0xCC36F5),w,h,new Color(0x28DFFF)):selected?UiTheme.p().accent():UiTheme.p().line());g.setStroke(new BasicStroke(selected?2:1));g.drawRoundRect(2,2,w-5,h-5,arc,arc);
            int x=20,y=20,pw=w-40,ph=Math.max(80,h-106);g.setColor(neon?new Color(0x141F39):UiTheme.p().background());g.fillRoundRect(x,y,pw,ph,arc,arc);
            g.setColor(neon?new Color(0x2D3E60):UiTheme.p().line());for(int i=0;i<3;i++)g.fillRoundRect(x+14,y+14+i*11,pw/2-i*12,4,3,3);
            int bw=Math.min(120,pw-28),by=y+ph-38;g.setPaint(neon?new GradientPaint(x,by,new Color(0x983CE5),x+bw,by,new Color(0x008EBB)):UiTheme.p().accent());g.fillRoundRect(x+14,by,bw,25,minimal?4:neon?8:24, minimal?4:neon?8:24);g.setColor(Color.WHITE);g.setFont(UiTheme.font(11,true));g.drawString("开始旅程",x+27,by+17);
            if(neon){g.setColor(new Color(0x46E8FF));g.drawRoundRect(x+14,by,bw,25,8,8);g.drawImage(ApplicationIcon.brand(),w-100,y+12,58,58,null);}
            g.setFont(UiTheme.font(17,true));g.setColor(neon?new Color(0xEDF5FF):UiTheme.p().text());g.drawString(title,20,h-53);g.setFont(UiTheme.font(11,false));g.setColor(neon?new Color(0xA8B7D6):UiTheme.p().muted());g.drawString(subtitle,20,h-30);
            if(selected){g.setColor(neon?new Color(0x39DEFF):UiTheme.p().accent());g.fillOval(w-33,h-62,17,17);g.setColor(neon?new Color(0x091020):UiTheme.p().accentText());g.setStroke(new BasicStroke(1.8f));g.drawLine(w-29,h-54,w-26,h-51);g.drawLine(w-26,h-51,w-21,h-57);}
            if(UiTheme.showFocus(this)){g.setColor(UiTheme.p().accent());g.drawRoundRect(5,5,w-11,h-11,arc,arc);}g.dispose();
        }
    }
}

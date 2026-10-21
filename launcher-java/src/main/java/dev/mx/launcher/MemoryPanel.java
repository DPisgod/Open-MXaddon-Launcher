package dev.mx.launcher;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Shared global/version controls, rendered with the current launcher palette. */
final class MemoryPanel extends JPanel {
    private final Map<String,JRadioButton> modes=new LinkedHashMap<>();
    private final JSlider slider=new JSlider(1024,32768,4096);
    private final JLabel customValue=UiTheme.label("4096 MB",12,false),automatic=UiTheme.muted("",12),inherited=UiTheme.muted("",12);
    private final JLabel usage=UiTheme.muted("",12),allocation=UiTheme.label("",13,true),hint=UiTheme.muted("",11);
    private final Supplier<Settings> global;
    private MemoryAllocation.Ram ram=MemoryAllocation.ram();
    private int allocated;
    private final Timer timer=new Timer(1500,e->refresh());
    MemoryPanel(boolean version,MemoryAllocation.Choice choice,Supplier<Settings> global){
        this.global=global;setOpaque(false);setLayout(new BorderLayout(0,10));setBorder(new EmptyBorder(6,0,8,0));
        JPanel controls=UiTheme.transparent(new GridBagLayout());ButtonGroup group=new ButtonGroup();
        if(version)addMode(controls,group,"follow","跟随全局设置",inherited);
        addMode(controls,group,"auto","自动配置",automatic);
        JPanel custom=UiTheme.transparent(new BorderLayout(12,0));UiTheme.slider(slider);slider.setName("customMemorySlider");slider.getAccessibleContext().setAccessibleName("自定义游戏内存（MB）");slider.setToolTipText("拖动调整游戏内存，范围 1024–32768 MB");slider.setMinorTickSpacing(128);
        customValue.setName("customMemoryValue");customValue.setPreferredSize(new Dimension(82,28));customValue.setHorizontalAlignment(SwingConstants.RIGHT);custom.add(slider,BorderLayout.CENTER);custom.add(customValue,BorderLayout.EAST);
        addMode(controls,group,"custom","自定义",custom);add(controls,BorderLayout.NORTH);
        JPanel summary=UiTheme.transparent(new BorderLayout(0,5));JPanel labels=UiTheme.transparent(new BorderLayout());labels.add(UiTheme.muted("已使用内存 / 物理内存",11),BorderLayout.WEST);labels.add(UiTheme.muted("游戏分配上限",11),BorderLayout.EAST);summary.add(labels,BorderLayout.NORTH);
        JComponent bar=new JComponent(){@Override protected void paintComponent(Graphics graphics){Graphics2D g=(Graphics2D)graphics.create();UiTheme.quality(g);int w=getWidth(),h=6;g.setColor(UiTheme.p().line());g.fillRoundRect(0,0,w,h,6,6);if(ram.totalMb()>0){int used=(int)(w*(ram.totalMb()-ram.freeMb())/ram.totalMb()),game=(int)(w*Math.min(allocated,ram.freeMb())/ram.totalMb());g.setColor(UiTheme.p().accent());g.fillRect(0,0,used,h);g.setColor(UiTheme.alpha(UiTheme.p().accent(),95));g.fillRect(used,0,game,h);}g.dispose();}};bar.setPreferredSize(new Dimension(200,6));summary.add(bar,BorderLayout.CENTER);
        JPanel values=UiTheme.transparent(new BorderLayout());values.add(usage,BorderLayout.WEST);values.add(allocation,BorderLayout.EAST);summary.add(values,BorderLayout.SOUTH);add(summary,BorderLayout.CENTER);add(hint,BorderLayout.SOUTH);
        slider.addChangeListener(e->refresh());
        select(choice);timer.setRepeats(true);
    }
    private void addMode(JPanel panel,ButtonGroup group,String key,String text,JComponent control){
        JRadioButton radio=new JRadioButton(text);radio.setName("memoryMode-"+key);radio.setFont(UiTheme.font(12,false));radio.setOpaque(false);radio.setForeground(UiTheme.p().text());radio.setFocusPainted(false);
        radio.setIcon(new RadioIcon(false));radio.setSelectedIcon(new RadioIcon(true));radio.addActionListener(e->refresh());int row=modes.size();modes.put(key,radio);group.add(radio);
        GridBagConstraints c=new GridBagConstraints();c.gridy=row;c.gridx=0;c.anchor=GridBagConstraints.WEST;c.insets=new Insets(0,0,5,20);panel.add(radio,c);c.gridx=1;c.weightx=1;c.fill=GridBagConstraints.HORIZONTAL;c.insets=new Insets(0,0,5,0);panel.add(control,c);
    }
    private record RadioIcon(boolean selected) implements Icon{
        public int getIconWidth(){return 20;}public int getIconHeight(){return 20;}
        public void paintIcon(Component c,Graphics graphics,int x,int y){Graphics2D g=(Graphics2D)graphics.create();UiTheme.quality(g);g.setColor(selected?UiTheme.p().accent():UiTheme.p().muted());g.setStroke(new BasicStroke(1.4f));g.drawOval(x+2,y+2,15,15);if(selected)g.fillOval(x+6,y+6,7,7);g.dispose();}
    }
    void select(MemoryAllocation.Choice choice){modes.getOrDefault(choice.mode(),modes.get("auto")).setSelected(true);slider.setValue(MemoryAllocation.clamp(choice.mb()));refresh();}
    MemoryAllocation.Choice choice(){String mode=modes.entrySet().stream().filter(e->e.getValue().isSelected()).map(Map.Entry::getKey).findFirst().orElse("auto");return new MemoryAllocation.Choice(mode,slider.getValue());}
    private void refresh(){
        ram=MemoryAllocation.ram();MemoryAllocation.Choice c=choice();boolean custom="custom".equals(c.mode());slider.setEnabled(custom);customValue.setText(slider.getValue()+" MB");customValue.setForeground(custom?UiTheme.p().text():UiTheme.p().muted());
        automatic.setText("启动时根据可用内存自动计算");inherited.setText("使用全局内存分配方案");
        allocated=MemoryAllocation.resolve(c,global.get(),ram);usage.setText(ram.totalMb()>0?String.format(java.util.Locale.ROOT,"%.1f GB / %.1f GB",(ram.totalMb()-ram.freeMb())/1024d,ram.totalMb()/1024d):"无法读取物理内存");
        allocation.setText(String.format(java.util.Locale.ROOT,"%.1f GB",allocated/1024d));
        hint.setText(custom?(ram.freeMb()>0&&allocated>ram.freeMb()?"分配量超过当前可用内存，可能影响系统响应。":"下次启动生效；运行中的客户端保持原有分配。子进程按需使用内存。"):(ram.freeMb()>0&&ram.freeMb()<2560?"当前可用内存较少，建议先关闭其他客户端或程序。":"启动时根据可用物理内存重新计算，并为系统预留内存。"));repaint();
    }
    @Override public void addNotify(){super.addNotify();timer.start();}
    @Override public void removeNotify(){timer.stop();super.removeNotify();}
}

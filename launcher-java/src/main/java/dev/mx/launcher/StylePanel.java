package dev.mx.launcher;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

/** Name-only color presets; every preset keeps the same rounded components. */
final class StylePanel extends JPanel {
    private static final String[] PRESETS={"light","dark","cyber"};
    private Appearance saved;
    private final Predicate<Appearance> apply;
    private final JLabel message=UiTheme.muted("选择后立即生效并保存。",12);
    private final JComboBox<String> styles=new JComboBox<>(new String[]{"浅色","深色","霓虹赛博"});
    private boolean updating;

    StylePanel(Appearance appearance,Predicate<Appearance> apply,IntConsumer previewTransparency,Runnable back,Runnable wallpapers){
        this.saved=appearance.copy();this.apply=apply;setName("personalizationPanel");setOpaque(false);setLayout(new BorderLayout(0,22));setBorder(new EmptyBorder(24,30,24,30));
        JPanel heading=UiTheme.transparent(new BorderLayout());JPanel titles=UiTheme.transparent(new GridLayout(2,1,0,4));titles.add(UiTheme.label("个性化",24,true));titles.add(UiTheme.muted("选择界面配色，调整 UI 透明度。",12));heading.add(titles);
        UiTheme.Button home=new UiTheme.Button("返回主页","home",UiTheme.Tone.SURFACE);home.setPreferredSize(new Dimension(102,38));home.addActionListener(e->back.run());JPanel returnArea=UiTheme.transparent(new FlowLayout(FlowLayout.RIGHT,0,0));returnArea.add(home);heading.add(returnArea,BorderLayout.EAST);add(heading,BorderLayout.NORTH);

        UiTheme.Surface settings=new UiTheme.Surface(new GridBagLayout(),22);settings.setBorder(new EmptyBorder(22,24,22,24));GridBagConstraints c=new GridBagConstraints();c.gridx=0;c.gridy=0;c.anchor=GridBagConstraints.WEST;c.insets=new Insets(0,0,22,24);settings.add(UiTheme.label("界面风格",15,true),c);
        UiTheme.combo(styles);styles.setFocusable(true);styles.setName("appearanceStyle");styles.getAccessibleContext().setAccessibleName("界面风格");styles.setPreferredSize(new Dimension(280,38));styles.setSelectedIndex(index(saved.theme));JPanel choice=UiTheme.transparent(new FlowLayout(FlowLayout.LEFT,0,0));choice.add(styles);c.gridx=1;c.weightx=1;c.fill=GridBagConstraints.HORIZONTAL;settings.add(choice,c);
        styles.addActionListener(e->{if(updating)return;Appearance value=saved.copy();value.theme=PRESETS[styles.getSelectedIndex()];if(!save(value)){updating=true;styles.setSelectedIndex(index(saved.theme));updating=false;}});

        c.gridx=0;c.gridy=1;c.weightx=0;c.fill=GridBagConstraints.NONE;c.insets=new Insets(0,0,12,24);settings.add(UiTheme.label("UI 透明度",15,true),c);
        JSlider transparency=new JSlider(0,100,saved.uiTransparency);transparency.setName("uiTransparency");transparency.setToolTipText("仅调整主页顶栏和启动栏背景；0% 不透明，100% 完全透明");UiTheme.slider(transparency);JLabel percent=UiTheme.label(saved.uiTransparency+"%",13,true);percent.setPreferredSize(new Dimension(44,30));JPanel slider=UiTheme.transparent(new BorderLayout(12,0));slider.add(transparency);slider.add(percent,BorderLayout.EAST);c.gridx=1;c.weightx=1;c.fill=GridBagConstraints.HORIZONTAL;settings.add(slider,c);
        transparency.addChangeListener(e->{
            percent.setText(transparency.getValue()+"%");if(updating)return;
            // Paint each pointer movement, keeping disk writes and theme refreshes out of the drag.
            previewTransparency.accept(transparency.getValue());
            if(!transparency.getValueIsAdjusting()&&transparency.getValue()!=saved.uiTransparency){
                Appearance value=saved.copy();value.uiTransparency=transparency.getValue();
                if(!save(value)){
                    updating=true;try{transparency.setValue(saved.uiTransparency);}finally{updating=false;}
                    previewTransparency.accept(saved.uiTransparency);
                }
            }
        });
        c.gridx=0;c.gridy=2;c.weightx=0;c.fill=GridBagConstraints.NONE;c.insets=new Insets(4,0,12,24);settings.add(UiTheme.label("壁纸",15,true),c);
        UiTheme.Button wallpaper=new UiTheme.Button("选择壁纸与播放设置","image",UiTheme.Tone.SURFACE);wallpaper.setName("wallpaperMenu");wallpaper.addActionListener(e->wallpapers.run());JPanel wallpaperArea=UiTheme.transparent(new FlowLayout(FlowLayout.LEFT,0,0));wallpaperArea.add(wallpaper);c.gridx=1;c.weightx=1;c.fill=GridBagConstraints.HORIZONTAL;settings.add(wallpaperArea,c);
        c.gridx=0;c.gridy=3;c.gridwidth=2;c.insets=new Insets(4,0,0,0);settings.add(UiTheme.muted("支持内置壁纸和自行导入图片或视频，预览后应用。",12),c);
        JPanel body=UiTheme.transparent(new BorderLayout());body.add(settings,BorderLayout.NORTH);add(body,BorderLayout.CENTER);add(message,BorderLayout.SOUTH);
    }
    private static int index(String preset){for(int i=0;i<PRESETS.length;i++)if(PRESETS[i].equals(preset))return i;return 0;}
    private boolean save(Appearance value){if(!apply.test(value)){message.setText("保存失败，请检查启动器目录的写入权限。");return false;}saved=value.copy();message.setText("个性化设置已保存。");return true;}
    @Override protected void paintComponent(Graphics g){g.setColor(UiTheme.p().background());g.fillRect(0,0,getWidth(),getHeight());super.paintComponent(g);}
}

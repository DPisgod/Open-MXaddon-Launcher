package dev.mx.launcher;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.HashSet;
import java.util.Set;

/** Production navigation, component rendering, persistence and independent wallpaper writes. */
public final class StyleUiTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void click(Container root,String name){((JButton)AccountUiBehaviorTest.findNamed(root,name)).doClick();}
    public static void main(String[] args)throws Exception{
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());Path home=Files.createTempDirectory("oml-style-"),out=Path.of(args[0]);Files.createDirectories(out);LauncherPaths.override(home);
        Appearance initial=new Appearance();initial.mask=27;initial.blur=9;initial.loop=false;initial.save(home);check(Appearance.load(home).buttonStyle.equals("rounded"),"legacy default changed");
        LauncherFrame[] frame={null};
        try{
            SwingUtilities.invokeAndWait(()->{frame[0]=new LauncherFrame(false);frame[0].setVisible(true);frame[0].setSize(1000,740);});
            Set<Integer> signatures=new HashSet<>();
            for(String style:new String[]{"rounded","minimal","cyber"}){
                SwingUtilities.invokeAndWait(()->{
                    AccountBehaviorTest.findButton(frame[0],"风格").doClick();click(frame[0].stylePanel(),"style-"+style);frame[0].validate();
                    check(Appearance.load(home).buttonStyle.equals(style),"style not saved");check(UiTheme.style().equals(style),"global components not changed");
                    if(style.equals("cyber"))check(UiTheme.p().dark(),"cyber preset should start dark");
                    JButton launch=AccountBehaviorTest.findButton(frame[0],"启动游戏");launch.setSize(150,46);BufferedImage image=AppearanceControlsTest.render(launch);int hash=1;for(int y=0;y<46;y++)for(int x=0;x<150;x++)hash=31*hash+image.getRGB(x,y);signatures.add(hash);
                    JSlider opacity=(JSlider)AccountUiBehaviorTest.findNamed(frame[0].stylePanel(),"uiTransparency");opacity.setValue(37);check(Appearance.load(home).uiTransparency==37,"transparency not persisted");
                    Appearance current=Appearance.load(home);check(current.mask==27&&current.blur==9&&!current.loop,"style overwrote wallpaper settings");
                    check(opacity.getHeight()>=14&&opacity.getWidth()>200,"style controls clipped at minimum size");
                    for(String mode:new String[]{"light","dark","system"}){click(frame[0].stylePanel(),"theme-"+mode);check(Appearance.load(home).theme.equals(mode),"theme not saved");}
                    click(frame[0].stylePanel(),"theme-"+(style.equals("cyber")?"dark":"light"));
                });
                LauncherDesignShot.shot(frame[0],out.resolve("style-"+style+"-minimum.png"));
                SwingUtilities.invokeAndWait(()->AccountBehaviorTest.findButton(frame[0],"主页").doClick());LauncherDesignShot.shot(frame[0],out.resolve("home-"+style+"-minimum.png"));
            }
            check(signatures.size()==3,"actual buttons paint identically across styles");
            SwingUtilities.invokeAndWait(()->{
                click(frame[0],"wallpaperMenu");check(frame[0].appearancePanel().isVisible(),"wallpaper entry not routed");
                check(AccountUiBehaviorTest.findNamed(frame[0].appearancePanel(),"uiTransparency")==null,"transparency still in wallpaper menu");
                AccountBehaviorTest.findButton(frame[0].appearancePanel(),"应用壁纸").doClick();Appearance current=Appearance.load(home);check(current.buttonStyle.equals("cyber")&&current.theme.equals("dark")&&current.uiTransparency==37,"wallpaper overwrote style preferences");
                frame[0].openDownloads();frame[0].validate();
            });LauncherDesignShot.shot(frame[0],out.resolve("downloads-cyber-minimum.png"));
            SwingUtilities.invokeAndWait(()->{frame[0].dispose();frame[0]=new LauncherFrame(false);frame[0].setVisible(true);check(UiTheme.style().equals("cyber")&&UiTheme.p().dark(),"restart lost selected style");frame[0].showStyles();frame[0].validate();});
            LauncherDesignShot.shot(frame[0],out.resolve("style-cyber-restored.png"));
            System.out.println("STYLE_THREE_DISTINCT_BUTTONS_THEME_TRANSPARENCY_NAVIGATION_INDEPENDENT_WALLPAPER_WRITES_RESTART_MINIMUM_LAYOUT_OK");
        }finally{SwingUtilities.invokeAndWait(()->{if(frame[0]!=null)frame[0].dispose();});}System.exit(0);
    }
}

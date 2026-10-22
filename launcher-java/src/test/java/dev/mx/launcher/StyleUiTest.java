package dev.mx.launcher;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;

/** Unified name-only presets, legacy migration and independent wallpaper preferences. */
public final class StyleUiTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static JRadioButton selector(LauncherFrame frame,String id){return (JRadioButton)AccountUiBehaviorTest.findNamed(frame.stylePanel(),"appearanceStyle-"+id);}
    private static void checkSimpleMenu(Component component){
        if(component instanceof JLabel label)check(label.getIcon()==null,"preview image remains");
        if(component instanceof JButton button){String text=button.getText();check(!Set.of("柔和圆角","简约直角","跟随系统","浅色","深色","霓虹赛博").contains(text),"old card or separate theme button remains");}
        if(component instanceof Container parent)for(Component child:parent.getComponents())checkSimpleMenu(child);
    }
    public static void main(String[] args)throws Exception{
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());Path home=Files.createTempDirectory("oml-personalization-"),out=Path.of(args[0]);Files.createDirectories(out);LauncherPaths.override(home);
        Path preferences=LauncherPaths.dataFile(home,"appearance.properties");
        for(String oldStyle:new String[]{"rounded","minimal","cyber"})for(String theme:new String[]{"light","dark","system"}){
            Files.writeString(preferences,"buttonStyle="+oldStyle+"\ntheme="+theme+"\nuiTransparency=37\nblur=9\nmask=27\nloop=false\n");
            Appearance migrated=Appearance.load(home);String expected=oldStyle.equals("cyber")?"cyber":theme.equals("system")?(UiTheme.systemDark()?"dark":"light"):theme;
            check(migrated.theme.equals(expected),"legacy style migration failed: "+oldStyle+"/"+theme);migrated.save(home);
            check(!Files.readString(preferences).contains("buttonStyle"),"removed geometry preference still saved");
            check(migrated.uiTransparency==37&&migrated.mask==27&&migrated.blur==9&&!migrated.loop,"migration changed unrelated settings");
        }
        Appearance initial=Appearance.load(home);initial.theme="light";initial.save(home);LauncherFrame[] frame={null};Set<Integer> signatures=new HashSet<>();
        try{
            SwingUtilities.invokeAndWait(()->{frame[0]=new LauncherFrame(false);frame[0].setVisible(true);frame[0].setSize(1000,740);});
            String[] ids={"light","dark","cyber","china-red","sakura","purple"},names={"浅色","深色","霓虹赛博","中国红","樱花粉","基佬紫"};Dimension[] launchSize={null},choicesSize={null};
            for(int i=0;i<ids.length;i++){
                final int index=i;
                SwingUtilities.invokeAndWait(()->{
                    AccountBehaviorTest.findButton(frame[0],"个性化").doClick();JPanel presets=(JPanel)AccountUiBehaviorTest.findNamed(frame[0].stylePanel(),"appearanceStyles");
                    check(presets.getComponentCount()==6,"unexpected preset count");for(int n=0;n<ids.length;n++)check(names[n].equals(selector(frame[0],ids[n]).getText()),"preset names not plain or incorrect");
                    selector(frame[0],ids[index]).doClick(0);check(Arrays.stream(presets.getComponents()).filter(c->((JRadioButton)c).isSelected()).count()==1,"radio selection is not exclusive");frame[0].validate();checkSimpleMenu(frame[0].stylePanel());
                    check(Appearance.load(home).theme.equals(ids[index])&&UiTheme.style().equals(ids[index]),"selection not globally applied/saved");
                    check(UiTheme.p().dark()==(index==1||index==2||index==5),"preset palette incorrect");
                    JButton launch=AccountBehaviorTest.findButton(frame[0],"启动游戏");if(launchSize[0]==null){launchSize[0]=launch.getSize();choicesSize[0]=presets.getSize();}
                    check(launch.getSize().equals(launchSize[0])&&presets.getSize().equals(choicesSize[0]),"preset changed component geometry");
                    BufferedImage image=AppearanceControlsTest.render(launch);int hash=1;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)hash=31*hash+image.getRGB(x,y);signatures.add(hash);
                    JSlider opacity=(JSlider)AccountUiBehaviorTest.findNamed(frame[0].stylePanel(),"uiTransparency");opacity.setValue(43);check(Appearance.load(home).uiTransparency==43,"transparency not saved");
                    Appearance current=Appearance.load(home);check(current.mask==27&&current.blur==9&&!current.loop,"personalization overwrote wallpaper");check(opacity.getHeight()>=14&&opacity.getWidth()>200&&presets.getHeight()>=30,"minimum layout clipped controls");
                });
                Thread.sleep(260);LauncherDesignShot.shot(frame[0],out.resolve("personalization-"+ids[i]+"-minimum.png"));SwingUtilities.invokeAndWait(()->AccountBehaviorTest.findButton(frame[0],"主页").doClick());Thread.sleep(260);LauncherDesignShot.shot(frame[0],out.resolve("home-"+ids[i]+"-minimum.png"));
            }
            check(signatures.size()==6,"color presets do not change actual buttons");
            SwingUtilities.invokeAndWait(()->{
                AccountBehaviorTest.findButton(frame[0],"个性化").doClick();
                check(SwingUtilities.isDescendingFrom(AccountUiBehaviorTest.findNamed(frame[0],"wallpaperMenu"),frame[0].stylePanel()),"wallpaper entry outside personalization");
                ((JButton)AccountUiBehaviorTest.findNamed(frame[0].stylePanel(),"wallpaperMenu")).doClick();check(AccountUiBehaviorTest.findNamed(frame[0].appearancePanel(),"uiTransparency")==null,"opacity in wallpaper menu");
                AccountBehaviorTest.findButton(frame[0].appearancePanel(),"应用壁纸").doClick();check(Appearance.load(home).theme.equals("purple")&&Appearance.load(home).uiTransparency==43,"wallpaper overwrote personalization");
                check(((UiTheme.Button)AccountBehaviorTest.findButton(frame[0],"个性化")).selected,"wallpaper not grouped under personalization");
                AccountBehaviorTest.findButton(frame[0].appearancePanel(),"返回个性化").doClick();check(frame[0].stylePanel().isShowing(),"wallpaper back button leaves personalization");
                frame[0].dispose();frame[0]=new LauncherFrame(false);frame[0].setVisible(true);frame[0].showStyles();frame[0].validate();check(selector(frame[0],"purple").isSelected()&&UiTheme.style().equals("purple"),"restart lost preset");
            });Thread.sleep(260);LauncherDesignShot.shot(frame[0],out.resolve("personalization-purple-restored.png"));
            System.out.println("PERSONALIZATION_FLAT_RADIO_SIX_PALETTES_SHARED_ROUNDED_GEOMETRY_LEGACY_MIGRATION_TRANSPARENCY_WALLPAPER_RESTART_MINIMUM_OK");
        }finally{SwingUtilities.invokeAndWait(()->{if(frame[0]!=null)frame[0].dispose();});}System.exit(0);
    }
}

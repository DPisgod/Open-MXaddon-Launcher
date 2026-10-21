package dev.mx.launcher;

import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.nio.file.*;

/** Production controls and full-window compositing, with disposable data and no authorization. */
public final class AppearanceControlsTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static BufferedImage render(Component component){BufferedImage image=new BufferedImage(component.getWidth(),component.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();component.paint(g);g.dispose();return image;}
    static int distance(int a,int b){return Math.abs((a>>16&255)-(b>>16&255))+Math.abs((a>>8&255)-(b>>8&255))+Math.abs((a&255)-(b&255));}
    static void apply(LauncherFrame frame,Appearance value)throws Exception{Method method=LauncherFrame.class.getDeclaredMethod("applyAppearance",Appearance.class);method.setAccessible(true);check((boolean)method.invoke(frame,value),"apply failed");}
    public static void main(String[] args)throws Exception{
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());Path home=Files.createTempDirectory("mx-transparency-controls-"),out=Path.of(args[0]);Files.createDirectories(out);LauncherPaths.override(home);
        check(Appearance.load(home).uiTransparency==0,"old installations must retain solid panels");
        BufferedImage source=new BufferedImage(1140,810,BufferedImage.TYPE_INT_RGB);Graphics2D g=source.createGraphics();g.setPaint(new GradientPaint(0,0,new Color(0x5488A8),1140,810,new Color(0xA85B65)));g.fillRect(0,0,1140,810);g.dispose();Path png=home.resolve("test-wallpaper.png");ImageIO.write(source,"png",png.toFile());
        WallpaperLibrary.Item item=new WallpaperLibrary(home).importFile(png);Appearance initial=new Appearance();initial.wallpaper=item.id();initial.mask=0;initial.fit="stretch";initial.save(home);
        LauncherFrame[] frames=new LauncherFrame[1];
        try{
            SwingUtilities.invokeAndWait(()->{frames[0]=new LauncherFrame(false);frames[0].setVisible(true);});Thread.sleep(200);
            SwingUtilities.invokeAndWait(()->{try{
                LauncherFrame frame=frames[0];
                for(String theme:new String[]{"light","dark"}){
                    frame.previewTheme(theme);int[] header=new int[3],dock=new int[3];
                    for(int i=0;i<3;i++){
                        Appearance a=Appearance.load(home);a.uiTransparency=i*50;apply(frame,a);
                        BufferedImage image=render(frame.getContentPane());Component panel=AccountUiBehaviorTest.findNamed(frame,"launchDock");Point point=SwingUtilities.convertPoint(panel,8,panel.getHeight()/2,frame.getContentPane());header[i]=image.getRGB(230,8);dock[i]=image.getRGB(point.x,point.y);
                        ImageIO.write(image,"png",out.resolve("home-"+theme+"-transparency-"+(i*50)+".png").toFile());
                        check(frame.getBackground().getAlpha()==255,"UI setting made the native window translucent");
                        check(AccountBehaviorTest.findButton(frame,"启动游戏").isEnabled(),"background opacity disabled launch");
                    }
                    WallpaperView reference=new WallpaperView(frame.wallpaperPlayer(),Appearance.load(home),0);reference.setSize(frame.getContentPane().getSize());BufferedImage bare=render(reference);
                    check(distance(header[2],bare.getRGB(230,8))<3,"header does not expose the continuous wallpaper");
                    Component panel=AccountUiBehaviorTest.findNamed(frame,"launchDock");Point point=SwingUtilities.convertPoint(panel,8,panel.getHeight()/2,frame.getContentPane());check(distance(dock[2],bare.getRGB(point.x,point.y))<3,"dock does not expose the wallpaper");
                    check(distance(header[0],header[2])>50&&distance(dock[0],dock[2])>50,"opacity control has no visual effect");
                    check(distance(header[1],header[0])>20&&distance(header[1],header[2])>20,"50 percent did not blend");
                }
                frame.showAppearance();frame.setSize(1000,740);frame.validate();JSlider opacity=(JSlider)AccountUiBehaviorTest.findNamed(frame,"uiTransparency");opacity.setValue(37);
                check(Appearance.load(home).uiTransparency==100,"preview unexpectedly saved");AccountBehaviorTest.findButton(frame,"应用壁纸").doClick();check(Appearance.load(home).uiTransparency==37,"apply did not persist");
                Component apply=AccountBehaviorTest.findButton(frame,"应用壁纸");ImageIO.write(render(frame.getContentPane()),"png",out.resolve("wallpapers-minimum-size.png").toFile());check(apply.getHeight()==44&&opacity.getHeight()>=14,"minimum layout clipped controls: apply="+apply.getSize()+" opacity="+opacity.getSize());
                for(String theme:new String[]{"light","dark"}){
                    UiTheme.set(theme);MemoryPanel memory=new MemoryPanel(false,new MemoryAllocation.Choice("custom",4096),Settings::new);memory.setSize(610,180);UiTheme.refresh(memory);layout(memory);
                    JSlider memorySlider=(JSlider)AccountUiBehaviorTest.findNamed(memory,"customMemorySlider");JLabel value=(JLabel)AccountUiBehaviorTest.findNamed(memory,"customMemoryValue");
                    check(countSpinners(memory)==0,"old memory spinner remains");check(memorySlider.isEnabled(),"custom slider disabled");memorySlider.setValue(6144);check(memory.choice().mb()==6144&&value.getText().equals("6144 MB"),"drag value not reflected in allocation");
                    memorySlider.setValue(32768);check(memory.choice().mb()==32768,"slider maximum incorrect");memory.select(new MemoryAllocation.Choice("auto",4096));check(!memorySlider.isEnabled(),"automatic allocation has active custom slider");
                    memory.select(new MemoryAllocation.Choice("custom",4784));check(memorySlider.isEnabled()&&memory.choice().mb()==4784,"mode switch lost custom value");
                    ImageIO.write(render(memory),"png",out.resolve("memory-slider-"+theme+".png").toFile());
                    SettingsDialog settings=new SettingsDialog(frame,home,home.resolve(".min"));settings.addNotify();settings.validate();((JRadioButton)AccountUiBehaviorTest.findNamed(settings,"memoryMode-custom")).doClick();((JSlider)AccountUiBehaviorTest.findNamed(settings,"customMemorySlider")).setValue(6144);
                    ImageIO.write(render(settings.getContentPane()),"png",out.resolve("settings-slider-"+theme+".png").toFile());AccountBehaviorTest.findButton(settings,"保存").doClick();check(Settings.load(home).memoryMb==6144&&Settings.load(home).memoryMode.equals("custom"),"settings did not save slider allocation");
                    SettingsDialog reopened=new SettingsDialog(frame,home,home.resolve(".min"));check(((JSlider)AccountUiBehaviorTest.findNamed(reopened,"customMemorySlider")).getValue()==6144,"reopened settings lost slider value");reopened.dispose();

                }
            }catch(Exception e){throw new RuntimeException(e);}});
            SwingUtilities.invokeAndWait(()->{JButton gear=AccountBehaviorTest.findButton(frames[0],"settings");gear.requestFocusInWindow();});Thread.sleep(100);
            SwingUtilities.invokeAndWait(()->{JButton gear=AccountBehaviorTest.findButton(frames[0],"settings");check(gear.hasFocus(),"fixture settings button did not receive focus");KeyboardFocusManager.getCurrentKeyboardFocusManager().redispatchEvent(gear,new KeyEvent(gear,KeyEvent.KEY_PRESSED,System.currentTimeMillis(),0,KeyEvent.VK_F6,KeyEvent.CHAR_UNDEFINED));check(UiTheme.showFocus(gear),"keyboard focus indicator missing");gear.dispatchEvent(new MouseEvent(gear,MouseEvent.MOUSE_PRESSED,System.currentTimeMillis(),0,2,2,1,false,MouseEvent.BUTTON1));check(!UiTheme.showFocus(gear),"mouse click left a blue focus outline");gear.getModel().setArmed(false);gear.dispatchEvent(new MouseEvent(gear,MouseEvent.MOUSE_RELEASED,System.currentTimeMillis(),0,-1,-1,1,false,MouseEvent.BUTTON1));});
            SwingUtilities.invokeAndWait(()->{try{AccountBehaviorTest.findButton(frames[0],"主页").doClick();Appearance value=Appearance.load(home);value.uiTransparency=100;apply(frames[0],value);}catch(Exception e){throw new RuntimeException(e);}});Thread.sleep(100);WindowCaptureTest.verify(frames[0],out,"fully-transparent-panels",true);
            System.out.println("MEMORY_SLIDER_ONLY_DISABLE_THEME_MOUSE_KEYBOARD_FOCUS_TRANSPARENCY_BLEND_PREVIEW_APPLY_MINIMUM_LAYOUT_OK");
        }finally{SwingUtilities.invokeAndWait(()->{if(frames[0]!=null)frames[0].dispose();});}
        System.exit(0);
    }
    static int countSpinners(Container parent){int count=0;for(Component child:parent.getComponents()){if(child instanceof JSpinner)count++;if(child instanceof Container nested)count+=countSpinners(nested);}return count;}
    static void layout(Container parent){parent.doLayout();for(Component child:parent.getComponents())if(child instanceof Container nested)layout(nested);}
}

package dev.mx.launcher;

import javax.swing.*;
import javax.swing.plaf.basic.BasicSliderUI;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Real task-disable transitions, repaint cadence, slider pointer mapping and navigation. */
public final class MotionAndControlsTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static Object field(Object value,String name)throws Exception{return DownloadCenterTest.field(value,name);}
    static void edt(Runnable run)throws Exception{SwingUtilities.invokeAndWait(run);}
    static int sample(JComboBox<?> box){BufferedImage pixels=AppearanceControlsTest.render(box);return pixels.getRGB(20,box.getHeight()-7);}
    static void begin(ContentBrowserPanel browser,CountDownLatch release)throws Exception{
        Class<?> work=Class.forName("dev.mx.launcher.ContentBrowserPanel$Work");
        Object task=Proxy.newProxyInstance(work.getClassLoader(),new Class<?>[]{work},(proxy,method,args)->{if(method.getName().equals("run")){release.await();return null;}return null;});
        Method run=ContentBrowserPanel.class.getDeclaredMethod("runTask",work,Consumer.class,String.class);run.setAccessible(true);run.invoke(browser,task,(Consumer<Object>)value->{},"刷新状态测试…");
    }
    public static void main(String[] args)throws Exception{
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());Path home=Files.createTempDirectory("oml-motion-controls-"),out=Path.of(args[0]);Files.createDirectories(out);LauncherPaths.override(home);
        LauncherFrame[] frame={null};ContentBrowserPanel[] browser={null};CountDownLatch[] pending={null};RepaintManager original=RepaintManager.currentManager(null);AtomicInteger ticks=new AtomicInteger();JProgressBar[] tracked={null};
        try{
            edt(()->{frame[0]=new LauncherFrame(false);frame[0].setVisible(true);frame[0].openDownloads();try{browser[0]=(ContentBrowserPanel)field(frame[0].downloadCenter(),"resources");}catch(Exception e){throw new RuntimeException(e);}AccountBehaviorTest.findButton(frame[0].downloadCenter(),"Mod").doClick();});Thread.sleep(300);
            for(String theme:List.of("light","dark","cyber")){
                int[] before=new int[7];String[] names={"source","category","sort","target","loader","files","game"};pending[0]=new CountDownLatch(1);
                edt(()->{try{frame[0].previewTheme(theme);for(int i=0;i<names.length;i++)before[i]=sample((JComboBox<?>)field(browser[0],names[i]));begin(browser[0],pending[0]);for(int i=0;i<names.length;i++){JComboBox<?> box=(JComboBox<?>)field(browser[0],names[i]);check(!box.isEnabled(),"Task did not disable "+names[i]);check(AppearanceControlsTest.distance(before[i],sample(box))<5,"Disabled background changed: "+theme+"/"+names[i]);}DownloadCenterTest.shot(frame[0],out.resolve("refresh-disabled-"+theme+".png"));}catch(Exception e){throw new RuntimeException(e);}});
                if(theme.equals("cyber")){
                    edt(()->{try{tracked[0]=(JProgressBar)field(browser[0],"progress");RepaintManager.setCurrentManager(new RepaintManager(){@Override public void addDirtyRegion(JComponent c,int x,int y,int w,int h){if(c==tracked[0])ticks.incrementAndGet();super.addDirtyRegion(c,x,y,w,h);}});}catch(Exception e){throw new RuntimeException(e);}});
                    Thread.sleep(150);ticks.set(0);Thread.sleep(1000);int requests=ticks.get();check(requests>=40,"Progress repaint cadence too low: "+requests);System.out.println("VISIBLE_PROGRESS_REPAINTS_PER_SECOND="+requests);
                }
                pending[0].countDown();long until=System.currentTimeMillis()+5000;boolean[] busy={true};while(busy[0]&&System.currentTimeMillis()<until){edt(()->busy[0]=browser[0].busy());Thread.sleep(20);}check(!busy[0],"Task did not finish");
            }
            Thread.sleep(100);int stopped=ticks.get();Thread.sleep(150);check(ticks.get()==stopped,"Hidden progress kept repainting");
            int[] fill={0};edt(()->{tracked[0].setVisible(true);tracked[0].setIndeterminate(false);tracked[0].setValue(0);tracked[0].setValue(800);fill[0]=filled(tracked[0]);});check(fill[0]<10,"Determinate progress jumped instead of easing");Thread.sleep(80);edt(()->{fill[0]=filled(tracked[0]);check(tracked[0].getValue()==800,"Animation changed real progress");});check(fill[0]>tracked[0].getWidth()*.35&&fill[0]<tracked[0].getWidth()*.8,"No intermediate progress position");Thread.sleep(350);edt(()->{check(Math.abs(filled(tracked[0])-tracked[0].getWidth()*.8)<3,"Progress did not settle to real value");tracked[0].setVisible(false);});
            edt(()->{frame[0].showStyles();});Thread.sleep(280);
            edt(()->{JSlider slider=(JSlider)AccountUiBehaviorTest.findNamed(frame[0].stylePanel(),"uiTransparency");pointer(slider,.83);pointer(slider,.19);slider.setInverted(true);AppearanceControlsTest.render(slider);pointer(slider,.71);slider.setInverted(false);slider.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);AppearanceControlsTest.render(slider);pointer(slider,.76);int old=slider.getValue();slider.setEnabled(false);press(slider,slider.getWidth()/2);check(slider.getValue()==old,"Disabled slider responded");release(slider,0);slider.setEnabled(true);});
            edt(()->{frame[0].openDownloads();});Thread.sleep(35);edt(()->{try{check(((UiMotion.Pages)field(frame[0],"pageHost")).animating(),"Page transition did not start");AccountBehaviorTest.findButton(frame[0],"个性化").doClick();AccountBehaviorTest.findButton(frame[0],"下载").doClick();}catch(Exception e){throw new RuntimeException(e);}});Thread.sleep(300);edt(()->{try{check(!((UiMotion.Pages)field(frame[0],"pageHost")).animating(),"Transition snapshot was not released");check(frame[0].getBackground().getAlpha()==255&&frame[0].getOpacity()==1,"Animation made HWND translucent");}catch(Exception e){throw new RuntimeException(e);}});
            SettingsDialog[] dialog={null};edt(()->{dialog[0]=new SettingsDialog(frame[0],home,LauncherPaths.gameRoot());dialog[0].setModalityType(Dialog.ModalityType.MODELESS);dialog[0].setVisible(true);});Thread.sleep(60);edt(()->{try{check(((UiMotion.Reveal)field(dialog[0].getContentPane(),"reveal")).value()<1,"Dialog entrance did not animate");check(dialog[0].getOpacity()==1,"Dialog uses layered window opacity");}catch(Exception e){throw new RuntimeException(e);}});Thread.sleep(220);edt(()->{try{check(((UiMotion.Reveal)field(dialog[0].getContentPane(),"reveal")).value()==1,"Dialog entrance did not finish");}catch(Exception e){throw new RuntimeException(e);}finally{dialog[0].dispose();}});
            System.out.println("REFRESH_DISABLED_THEME_EDITOR_SLIDER_ABSOLUTE_CLICK_DRAG_RTL_INVERTED_60HZ_IDLE_STOP_RAPID_NAVIGATION_OPAQUE_WINDOW_OK");
        }finally{if(pending[0]!=null)pending[0].countDown();edt(()->{RepaintManager.setCurrentManager(original);if(frame[0]!=null)frame[0].dispose();});}Class<?> clock=Class.forName("dev.mx.launcher.UiMotion$FineClock");Field users=clock.getDeclaredField("users");users.setAccessible(true);check(users.getInt(null)==0,"High-resolution timer request leaked");System.exit(0);
    }
    static int filled(JProgressBar bar){BufferedImage pixels=AppearanceControlsTest.render(bar);int count=0;for(int x=2;x<bar.getWidth()-2;x++)if(AppearanceControlsTest.distance(pixels.getRGB(x,bar.getHeight()/2),UiTheme.p().line().getRGB())>50)count++;return count;}
    static void pointer(JSlider slider,double fraction){int x=(int)(slider.getWidth()*fraction),expected=((BasicSliderUI)slider.getUI()).valueForXPosition(x);press(slider,x);check(slider.getValue()==expected,"Track click did not jump to pointer: "+slider.getValue()+" vs "+expected);int next=x-31;slider.dispatchEvent(new MouseEvent(slider,MouseEvent.MOUSE_DRAGGED,System.currentTimeMillis(),InputEvent.BUTTON1_DOWN_MASK,next,slider.getHeight()/2,0,false,MouseEvent.NOBUTTON));check(Math.abs(slider.getValue()-((BasicSliderUI)slider.getUI()).valueForXPosition(next))<=1,"Track click could not continue dragging");release(slider,next);check(!slider.getValueIsAdjusting(),"Slider stayed in adjusting state");}
    static void press(JSlider slider,int x){slider.dispatchEvent(new MouseEvent(slider,MouseEvent.MOUSE_PRESSED,System.currentTimeMillis(),InputEvent.BUTTON1_DOWN_MASK,x,slider.getHeight()/2,1,false,MouseEvent.BUTTON1));}
    static void release(JSlider slider,int x){slider.dispatchEvent(new MouseEvent(slider,MouseEvent.MOUSE_RELEASED,System.currentTimeMillis(),0,x,slider.getHeight()/2,1,false,MouseEvent.BUTTON1));}
}

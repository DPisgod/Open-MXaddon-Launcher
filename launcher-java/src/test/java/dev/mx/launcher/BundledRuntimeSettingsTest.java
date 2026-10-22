package dev.mx.launcher;

import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Must run with dist/runtime/bin/java.exe, and invoke the actual toolbar settings action. */
public final class BundledRuntimeSettingsTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        LauncherRuntime.configureRendering();UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());Path home=Files.createTempDirectory("oml-bundled-settings-");LauncherPaths.override(home);
        LauncherFrame[] frame={null};AtomicReference<Throwable> failure=new AtomicReference<>();CountDownLatch done=new CountDownLatch(1);int[] opened={0};Timer[] poll={null};long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{error.printStackTrace();failure.compareAndSet(null,error);done.countDown();});
        try{
            SwingUtilities.invokeAndWait(()->{
                frame[0]=new LauncherFrame(false);frame[0].setVisible(true);
                poll[0]=new Timer(250,e->{try{
                    if(System.nanoTime()>deadline)throw new AssertionError("Toolbar settings dialog did not appear");
                    for(Window window:frame[0].getOwnedWindows())if(window instanceof SettingsDialog dialog&&dialog.isShowing()){
                        check(MemoryAllocation.ram().totalMb()>0,"Bundled runtime cannot read physical RAM");
                        JRadioButton custom=(JRadioButton)AccountUiBehaviorTest.findNamed(dialog,"memoryMode-custom");custom.doClick();
                        JSlider slider=(JSlider)AccountUiBehaviorTest.findNamed(dialog,"customMemorySlider");check(slider.isEnabled(),"Memory slider unavailable");slider.setValue(6144);
                        AccountBehaviorTest.findButton(dialog,"保存").doClick();check(Settings.load(home).memoryMb==6144&&Settings.load(home).memoryMode.equals("custom"),"Settings action failed to save");
                        if(++opened[0]==3){poll[0].stop();done.countDown();}else SwingUtilities.invokeLater(()->{if(opened[0]==1)frame[0].openDownloads();else frame[0].showStyles();AccountBehaviorTest.findButton(frame[0],"settings").doClick();});
                        break;
                    }
                }catch(Throwable error){failure.compareAndSet(null,error);poll[0].stop();for(Window window:frame[0].getOwnedWindows())window.dispose();done.countDown();}});poll[0].start();
                SwingUtilities.invokeLater(()->AccountBehaviorTest.findButton(frame[0],"settings").doClick());
            });
            check(done.await(15,TimeUnit.SECONDS),"Settings action timed out");if(failure.get()!=null)throw new AssertionError("Packaged Java settings action failed",failure.get());check(opened[0]==3,"Not all toolbar settings actions opened");
            System.out.println("BUNDLED_JAVA_HOME="+System.getProperty("java.home"));System.out.println("BUNDLED_RUNTIME_REAL_GEAR_CLICK_HOME_DOWNLOADS_PERSONALIZATION_RAM_MEMORY_SAVE_OK");
        }finally{SwingUtilities.invokeAndWait(()->{if(poll[0]!=null)poll[0].stop();if(frame[0]!=null){for(Window window:frame[0].getOwnedWindows())window.dispose();frame[0].dispose();}});}System.exit(0);
    }
}

package dev.mx.launcher;

import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Actual switch button, detected runtime selection, automatic reset and launch resolution. */
public final class JavaSelectionTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());Path home=Files.createTempDirectory("oml-java-selection-"),out=Path.of(args[0]);Files.createDirectories(out);LauncherPaths.override(home);
        Path gameRoot=LauncherPaths.gameRoot();var detected=JavaRuntimes.discover(home,gameRoot,"");
        check(!detected.isEmpty(),"Current Java was not detected");
        Set<String> directories=new HashSet<>();for(var runtime:detected){check(runtime.major()>0&&Files.isRegularFile(runtime.executable()),"Invalid detected Java");check(directories.add(runtime.executable().getParent().toRealPath().toString().toLowerCase(Locale.ROOT)),"Duplicate Java bin directory");}
        var current=JavaRuntimes.inspect(Path.of(System.getProperty("java.home"),"bin/java.exe"));check(current.major()==Runtime.version().feature(),"Current runtime version is wrong");
        check(JavaRuntimes.major("1.8.0_471")==8&&JavaRuntimes.major("17.0.12")==17&&JavaRuntimes.major("25-ea")==25,"Java version formats");
        SettingsDialog[] settings={null};javax.swing.Timer[] poll={null};AtomicReference<Throwable> failure=new AtomicReference<>();CountDownLatch done=new CountDownLatch(1);int[] stage={0};String[] chosen={null};long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(25);
        try{
            SwingUtilities.invokeAndWait(()->{
                UiTheme.set("purple");settings[0]=new SettingsDialog(null,home,gameRoot);settings[0].setModal(false);settings[0].setVisible(true);
                poll[0]=new javax.swing.Timer(100,e->{try{
                    if(System.nanoTime()>deadline)throw new AssertionError("Java selection timed out");
                    for(Window window:settings[0].getOwnedWindows())if(window instanceof JavaSelectionDialog dialog&&dialog.isShowing()){
                        JList<?> list=(JList<?>)AccountUiBehaviorTest.findNamed(dialog,"javaRuntimeList");
                        if(list.getModel().getSize()<2)return;
                        if(stage[0]==0){
                            check(list.getSelectedIndex()==0,"Automatic choice is not first/default");
                            ImageIO.write(AppearanceControlsTest.render(settings[0].getContentPane()),"png",out.resolve("settings-switch-java.png").toFile());
                            ImageIO.write(AppearanceControlsTest.render(dialog.getContentPane()),"png",out.resolve("java-runtime-list.png").toFile());
                            list.setSelectedIndex(1);((JButton)AccountUiBehaviorTest.findNamed(dialog,"useJavaRuntime")).doClick(0);
                            chosen[0]=((JTextField)AccountUiBehaviorTest.findNamed(settings[0],"javaPath")).getText();
                            check(!chosen[0].isBlank()&&Files.isRegularFile(Path.of(chosen[0])),"Selected Java was not copied to path");
                            check(Settings.load(home).javaPath.isEmpty(),"Selection persisted before settings Save");
                            AccountBehaviorTest.findButton(settings[0],"保存").doClick(0);check(Settings.load(home).javaPath.equals(chosen[0]),"Manual selection did not persist");
                            check(GameJava.executable(home.resolve("fixture-instance"),8).equals(chosen[0]),"Launch ignored explicit Java");
                            stage[0]=1;settings[0]=new SettingsDialog(null,home,gameRoot);settings[0].setModal(false);settings[0].setVisible(true);SwingUtilities.invokeLater(()->AccountBehaviorTest.findButton(settings[0],"切换 Java").doClick(0));
                        }else{
                            check(list.getSelectedIndex()>0,"Saved manual Java was not preselected");list.setSelectedIndex(0);((JButton)AccountUiBehaviorTest.findNamed(dialog,"useJavaRuntime")).doClick(0);
                            check(((JTextField)AccountUiBehaviorTest.findNamed(settings[0],"javaPath")).getText().isBlank(),"Automatic choice did not clear manual path");
                            AccountBehaviorTest.findButton(settings[0],"保存").doClick(0);check(Settings.load(home).javaPath.isBlank(),"Automatic mode did not persist");poll[0].stop();done.countDown();
                        }
                        break;
                    }
                }catch(Throwable problem){failure.set(problem);poll[0].stop();for(Window window:settings[0].getOwnedWindows())window.dispose();done.countDown();}});poll[0].start();SwingUtilities.invokeLater(()->AccountBehaviorTest.findButton(settings[0],"切换 Java").doClick(0));
            });
            check(done.await(28,TimeUnit.SECONDS),"Java settings interaction timed out");if(failure.get()!=null)throw new AssertionError("Java settings failed",failure.get());
            Path instance=home.resolve("fixture-instance");
            for(int major:List.of(8,17,21,25))if(JavaRuntimes.matching(detected,major).isPresent())check(JavaRuntimes.inspect(Path.of(GameJava.executable(instance,major))).major()==major,"Automatic mode chose wrong Java "+major);
            Path local=instance.resolve("runtime/bin/javaw.exe");Files.createDirectories(local.getParent());Files.write(local,new byte[]{0});check(GameJava.executable(instance,8).equals(local.toAbsolutePath().toString()),"Automatic mode ignored instance runtime");
            System.out.println("JAVA_DISCOVERY_DEDUP_VERSION_ARCH_REAL_SWITCH_BUTTON_MANUAL_SAVE_REOPEN_AUTOMATIC_RESET_MATCHING_INSTANCE_PRIORITY_OK count="+detected.size());
        }finally{SwingUtilities.invokeAndWait(()->{if(poll[0]!=null)poll[0].stop();if(settings[0]!=null){for(Window window:settings[0].getOwnedWindows())window.dispose();settings[0].dispose();}});}System.exit(0);
    }
}

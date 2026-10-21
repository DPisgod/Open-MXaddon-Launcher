package dev.mx.launcher;

import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Uses only disposable instance data; exercises cancel, deletion, recovery, and last-version UI. */
public final class VersionDeletionTest {
    static void check(boolean yes,String message){if(!yes)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());Path home=Files.createTempDirectory("mx-version-delete-");LauncherPaths.override(home);Path root=home.resolve(".minecraft");
        String first="1.21.1-OML",second="1.20.1-test";
        for(String id:new String[]{first,second}){Path dir=root.resolve("versions").resolve(id);Files.createDirectories(dir.resolve("instance/saves/test"));Files.writeString(dir.resolve(id+".json"),"{}");Files.writeString(dir.resolve("instance/saves/test/level.dat"),"keep-world");}
        Files.createDirectories(root.resolve("libraries"));Files.writeString(root.resolve("libraries/shared.jar"),"shared");
        LauncherFrame[] frame=new LauncherFrame[1];SwingUtilities.invokeAndWait(()->{frame[0]=new LauncherFrame(false);frame[0].setVisible(true);});
        choose(frame[0],false);check(Files.isDirectory(root.resolve("versions").resolve(first)),"cancel deleted version");
        choose(frame[0],true);await(()->!Files.exists(root.resolve("versions").resolve(first)));await(()->frame[0].debugVersionText().equals(second));
        check(Files.readString(root.resolve("libraries/shared.jar")).equals("shared"),"shared libraries modified");
        try(var files=Files.walk(root.resolve(".mx-version-trash"))){Path saved=files.filter(p->p.endsWith("level.dat")).findFirst().orElseThrow();check(Files.readString(saved).equals("keep-world"),"save not recoverable");}
        choose(frame[0],true);await(()->!Files.exists(root.resolve("versions").resolve(second)));await(()->frame[0].debugVersionText().equals("未安装游戏版本"));
        SwingUtilities.invokeAndWait(()->{JComboBox<?> box=findCombo(frame[0]);for(int i=0;i<box.getItemCount();i++)check(!box.getItemAt(i).toString().equals("删除当前版本…"),"delete offered for no version");frame[0].dispose();});
        boolean rejected=false;try{VersionManager.remove(root,"../libraries");}catch(java.io.IOException expected){rejected=true;}check(rejected,"outside directory accepted");
        System.out.println("VERSION_DELETE_CANCEL_RECOVER_LAST_AND_SHARED_FILES_OK");System.exit(0);
    }
    static void choose(LauncherFrame frame,boolean confirm)throws Exception{
        AtomicBoolean answered=new AtomicBoolean();Timer[] timer=new Timer[1];
        SwingUtilities.invokeAndWait(()->{timer[0]=new Timer(80,e->{for(Window window:Window.getWindows())if(window instanceof JDialog dialog&&dialog.isVisible()&&"删除游戏版本".equals(dialog.getName())){check(dialog.isUndecorated()&&dialog.getTitle().equals("删除游戏版本"),"confirmation capture name or borderless style missing");JButton button=AccountBehaviorTest.findButton(dialog,confirm?"确定":"取消");if(button!=null){answered.set(true);button.doClick();timer[0].stop();}}});timer[0].start();findCombo(frame).setSelectedItem("删除当前版本…");});
        try{await(answered::get);}finally{SwingUtilities.invokeAndWait(()->timer[0].stop());}
    }
    static JComboBox<?> findCombo(Container parent){for(Component child:parent.getComponents()){if(child instanceof JComboBox<?> box)return box;if(child instanceof Container c){JComboBox<?> box=findCombo(c);if(box!=null)return box;}}return null;}
    static void await(java.util.function.BooleanSupplier condition)throws Exception{long deadline=System.currentTimeMillis()+10000;while(!condition.getAsBoolean()&&System.currentTimeMillis()<deadline)Thread.sleep(50);check(condition.getAsBoolean(),"UI transition timed out");}
}

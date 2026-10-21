package dev.mx.launcher;

import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.List;
import java.util.Properties;
import com.sun.jna.platform.win32.Crypt32Util;

public final class MemoryAndClientsTest {
    static void check(boolean yes,String message){if(!yes)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        if(args.length>0&&args[0].equals("child")){Thread.sleep(60000);return;}
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());Path home=Files.createTempDirectory("mx-memory-clients-");LauncherPaths.override(home);
        Settings settings=Settings.load(home);check(settings.memoryMode.equals("auto"),"fresh memory is not automatic");
        check(MemoryAllocation.automatic(new MemoryAllocation.Ram(32358,17100))==6784,"automatic high-memory budget");
        check(MemoryAllocation.automatic(new MemoryAllocation.Ram(8192,1800))==1024,"low RAM floor");
        check(MemoryAllocation.automatic(new MemoryAllocation.Ram(65536,50000))==8192,"automatic cap");
        check(MemoryAllocation.automatic(new MemoryAllocation.Ram(32768,6000))<MemoryAllocation.automatic(new MemoryAllocation.Ram(32768,17000)),"additional client does not reduce budget");
        settings.memoryMode="custom";settings.memoryMb=6144;settings.save(home);check(Settings.load(home).memoryMb==6144&&Settings.load(home).memoryMode.equals("custom"),"global memory not saved");
        Path root=home.resolve(".minecraft");String version="1.21.1-Test";Path folder=root.resolve("versions").resolve(version);Files.createDirectories(folder);Files.writeString(folder.resolve(version+".json"),"{}");
        MemoryAllocation.save(root,version,new MemoryAllocation.Choice("follow",2048));check(MemoryAllocation.resolve(root,version,settings)==6144,"version does not inherit global");
        MemoryAllocation.save(root,version,new MemoryAllocation.Choice("custom",3072));check(MemoryAllocation.resolve(root,version,settings)==3072,"version custom ignored");
        boolean rejected=false;try{MemoryAllocation.save(root,"../escape",new MemoryAllocation.Choice("auto",4096));}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"version path escapes root");
        // A legacy generated account is removed; legitimate premium/Chinese accounts survive.
        String legacy="{\"accounts\":[{\"name\":\"Player\",\"premium\":false},{\"name\":\"小明\",\"premium\":false},{\"name\":\"Player\",\"premium\":true,\"uuid\":\"123456781234123412341234567890ab\",\"accessToken\":\"synthetic-test-only\"}]}";
        Files.write(home.resolve("accounts.dat"),Crypt32Util.cryptProtectData(legacy.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        AccountStore store=new AccountStore(home);check(store.available(null).size()==2&&store.available(null).stream().anyMatch(s->s.premium&&s.name.equals("Player")),"migration removed legitimate account");
        store.remember(Session.offline("Player"));check(new AccountStore(home).available(null).size()==3,"explicitly added Player removed");
        Path fresh=Files.createTempDirectory("mx-fresh-account-");LauncherPaths.override(fresh);
        Transferable previous=Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
        try{MicrosoftLogin.copyDeviceCode("TEST-1234");check("TEST-1234".equals(Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor)),"code not copied");}
        finally{if(previous!=null)Toolkit.getDefaultToolkit().getSystemClipboard().setContents(previous,null);}
        Process first=child(),second=child(),unrelated=child();LauncherFrame[] frame=new LauncherFrame[1];
        try{
            SwingUtilities.invokeAndWait(()->{
                frame[0]=new LauncherFrame(false);frame[0].setVisible(true);frame[0].showAccounts();
                check(AccountBehaviorTest.findButton(AccountUiBehaviorTest.popup(),"切换账号 Player 离线")==null,"fresh default Player remains");
                check(AccountBehaviorTest.findButton(AccountUiBehaviorTest.popup(),"官网授权")==null,"authorization button remains");MenuSelectionManager.defaultManager().clearSelectedPath();
                frame[0].gameStarted(first.pid());frame[0].gameStarted(second.pid());
                JButton launch=AccountBehaviorTest.findButton(frame[0],"启动游戏"),stop=AccountBehaviorTest.findButton(frame[0],"结束全部游戏");
                check(launch!=null&&launch.isEnabled(),"launch disabled after successful launches");check(stop!=null&&stop.isEnabled()&&stop.getToolTipText().contains("2"),"power count/enabled incorrect");
                check(AccountBehaviorTest.findButton(frame[0],"更换背景")==null,"duplicate background button remains");stop.doClick();
            });
            first.waitFor(8,java.util.concurrent.TimeUnit.SECONDS);second.waitFor(8,java.util.concurrent.TimeUnit.SECONDS);Thread.sleep(250);
            check(!first.isAlive()&&!second.isAlive()&&unrelated.isAlive(),"stop did not isolate owned clients");
            SwingUtilities.invokeAndWait(()->{check(AccountBehaviorTest.findButton(frame[0],"启动游戏").isEnabled(),"launch did not recover after stop");check(!AccountBehaviorTest.findButton(frame[0],"结束全部游戏").isEnabled(),"stop remains enabled without clients");frame[0].showAppearance();JButton back=(JButton)AccountUiBehaviorTest.findNamed(frame[0],"returnHome");check(back.getHeight()==38&&back.getWidth()==102,"return home is stretched");});
            Path out=Path.of(args[0]);Files.createDirectories(out);
            for(String theme:List.of("light","dark"))SwingUtilities.invokeAndWait(()->{try{
                UiTheme.set(theme);SettingsDialog global=new SettingsDialog(frame[0],fresh,fresh.resolve(".minecraft"));global.addNotify();global.validate();shot(global,out.resolve("memory-global-"+theme+".png"));global.dispose();
                VersionSettingsDialog perVersion=new VersionSettingsDialog(frame[0],root,version);perVersion.addNotify();perVersion.validate();AccountBehaviorTest.findButton(perVersion,"游戏设置").doClick();perVersion.validate();shot(perVersion,out.resolve("memory-version-"+theme+".png"));perVersion.dispose();
            }catch(Exception e){throw new RuntimeException(e);}});
        }finally{first.destroyForcibly();second.destroyForcibly();unrelated.destroyForcibly();SwingUtilities.invokeAndWait(()->{if(frame[0]!=null)frame[0].dispose();});}
        System.out.println("MEMORY_GLOBAL_VERSION_AUTO_CUSTOM_MIGRATION_MULTI_STOP_CLIPBOARD_COMPACT_HOME_OK");System.exit(0);
    }
    static Process child()throws Exception{return new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin/java.exe").toString(),"-cp",System.getProperty("java.class.path"),MemoryAndClientsTest.class.getName(),"child").start();}
    static void shot(JDialog dialog,Path path)throws Exception{Container c=dialog.getContentPane();BufferedImage image=new BufferedImage(c.getWidth(),c.getHeight(),BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();c.paint(g);g.dispose();ImageIO.write(image,"png",path.toFile());}
}

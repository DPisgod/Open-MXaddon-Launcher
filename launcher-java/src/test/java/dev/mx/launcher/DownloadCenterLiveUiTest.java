package dev.mx.launcher;

import java.nio.file.*;
import java.util.*;
import javax.swing.*;

/** Clicks the production search, file-selection and installation actions against public Modrinth. */
public final class DownloadCenterLiveUiTest {
    static Object field(Object object,String name)throws Exception{return DownloadCenterTest.field(object,name);}
    static void waitIdle(ContentBrowserPanel panel)throws Exception{
        long limit=System.currentTimeMillis()+60000;
        while(System.currentTimeMillis()<limit){boolean[] busy={false};SwingUtilities.invokeAndWait(()->busy[0]=panel.busy());if(!busy[0])return;Thread.sleep(100);}
        throw new AssertionError("Resource task timed out");
    }
    public static void main(String[] args)throws Exception{
        Path home=Files.createTempDirectory("oml-resource-live-ui-"),out=Path.of(args[0]);Files.createDirectories(out);LauncherPaths.override(home);Path root=LauncherPaths.gameRoot();String id="1.21.1-Fabric-Live";
        Path outer=root.resolve("versions").resolve(id),instance=outer.resolve("instance"),version=instance.resolve("versions").resolve(id);Files.createDirectories(version);
        var metadata=Map.of("id",id,"mxcloudInstance",true,"mxcloudMinecraft","1.21.1","mxcloudLoader","fabric","mainClass","net.fabricmc.loader.impl.launch.knot.KnotClient");
        Files.writeString(outer.resolve(id+".json"),Json.write(metadata));Files.writeString(version.resolve(id+".json"),Json.write(metadata));Files.write(version.resolve(id+".jar"),new byte[0]);
        Path world=instance.resolve("saves/下载测试世界");Files.createDirectories(world);Files.writeString(world.resolve("level.dat"),"fixture world");
        LauncherFrame[] frame={null};ContentBrowserPanel[] browser={null};
        try{
            SwingUtilities.invokeAndWait(()->{frame[0]=new LauncherFrame(false);frame[0].setVisible(true);frame[0].openDownloads();try{browser[0]=(ContentBrowserPanel)field(frame[0].downloadCenter(),"resources");}catch(Exception e){throw new RuntimeException(e);}});
            for(var kind:List.of(ContentCatalog.Kind.MOD,ContentCatalog.Kind.DATA)){
                SwingUtilities.invokeAndWait(()->{try{
                    AccountBehaviorTest.findButton(frame[0].downloadCenter(),kind.title).doClick();
                    ((JTextField)field(browser[0],"query")).setText(kind==ContentCatalog.Kind.MOD?"Sodium":"VeinMiner");AccountBehaviorTest.findButton(browser[0],"搜索").doClick();
                }catch(Exception e){throw new RuntimeException(e);}});waitIdle(browser[0]);
                SwingUtilities.invokeAndWait(()->{try{JList<?> list=(JList<?>)field(browser[0],"results");if(list.getModel().getSize()==0)throw new AssertionError("No search results: "+kind);list.setSelectedIndex(0);}catch(Exception e){throw new RuntimeException(e);}});waitIdle(browser[0]);
                SwingUtilities.invokeAndWait(()->{try{
                    JComboBox<?> files=(JComboBox<?>)field(browser[0],"files");if(files.getItemCount()==0)throw new AssertionError("No compatible files: "+kind);
                    if(kind==ContentCatalog.Kind.DATA){((JComboBox<?>)field(browser[0],"world")).setSelectedItem("下载测试世界");if(!((ContentCatalog.FileVersion)files.getSelectedItem()).filename().endsWith(".zip"))throw new AssertionError("Datapack selected a mod JAR");}
                    AccountBehaviorTest.findButton(browser[0],"下载并安装").doClick();
                }catch(Exception e){throw new RuntimeException(e);}});waitIdle(browser[0]);
                Path destination=kind==ContentCatalog.Kind.MOD?instance.resolve("mods"):world.resolve("datapacks");
                if(!Files.isDirectory(destination))throw new AssertionError("No installed resources: "+kind+" "+((JLabel)field(browser[0],"status")).getText());
                try(var entries=Files.list(destination)){if(entries.count()==0)throw new AssertionError("Empty destination");}
                SwingUtilities.invokeAndWait(()->{try{DownloadCenterTest.shot(frame[0],out.resolve(kind.mr+"-installed.png"));}catch(Exception e){throw new RuntimeException(e);}});
                System.out.println("LIVE_UI_SEARCH_FILES_INSTALL_OK "+kind+" -> "+destination);
            }
        }finally{SwingUtilities.invokeAndWait(()->{if(frame[0]!=null)frame[0].dispose();});}
        System.out.println("DOWNLOAD_CENTER_REAL_MOD_AND_WORLD_DATAPACK_OK");System.exit(0);
    }
}

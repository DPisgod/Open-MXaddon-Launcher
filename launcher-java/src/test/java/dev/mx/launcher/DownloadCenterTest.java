package dev.mx.launcher;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;

public final class DownloadCenterTest {
    static void check(boolean yes,String message){if(!yes)throw new AssertionError(message);}
    static Object field(Object object,String name)throws Exception{var field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}
    public static void main(String[] args)throws Exception{
        Path home=Files.createTempDirectory("oml-download-center-"),out=Path.of(args[0]);Files.createDirectories(out);LauncherPaths.override(home);
        var release=new DownloadCatalog.Game("1.21.1","release","https://fixture/1","","2024-08-08T12:00:00Z");
        var normal=new DownloadCatalog.Game("26w14a","snapshot","https://fixture/2","","2026-04-01T12:00:00Z");
        var special=new DownloadCatalog.Game("25w14craftmine","snapshot","https://fixture/3","","2025-04-01T12:00:00Z");
        var beta=new DownloadCatalog.Game("b1.7.3","old_beta","https://fixture/4","","2011-07-08T12:00:00Z");
        check(!normal.special()&&special.special()&&beta.category().equals("远古版"),"Version classification");
        Path instance=home.resolve("instance");Files.createDirectories(instance.resolve("saves/世界一"));Files.writeString(instance.resolve("saves/世界一/level.dat"),"fixture");
        check(ContentBrowserPanel.resourceDirectory(instance,ContentCatalog.Kind.DATA,"世界一").endsWith("saves/世界一/datapacks"),"Datapack world destination");
        for(String world:List.of("","../outside","not-a-world"))try{ContentBrowserPanel.resourceDirectory(instance,ContentCatalog.Kind.DATA,world);throw new AssertionError("Invalid world accepted");}catch(java.io.IOException expected){}
        LauncherFrame[] frame=new LauncherFrame[1];
        try{
            SwingUtilities.invokeAndWait(()->{frame[0]=new LauncherFrame(false);frame[0].setVisible(true);frame[0].openDownloads();frame[0].downloadCenter().setGames(List.of(release,normal,special,beta));});
            SwingUtilities.invokeAndWait(()->{try{
                var center=frame[0].downloadCenter();var list=(JList<?>)field(center,"versions");var group=(JComboBox<?>)field(center,"group");var query=(JTextField)field(center,"query");
                check(list.getModel().getSize()==1,"Release filter");group.setSelectedItem("愚人节");check(list.getModel().getSize()==1&&list.getModel().getElementAt(0).equals(special),"Special version filter");
                group.setSelectedItem("远古版");query.setText("b1.7");check(list.getModel().getSize()==1,"Legacy version search");query.setText("");group.setSelectedItem("全部版本");check(list.getModel().getSize()==4,"All versions");
                check(AccountBehaviorTest.findButton(center,"配置并安装")!=null,"Configure action missing");
                for(String theme:List.of("light","dark")){frame[0].previewTheme(theme);shot(frame[0],out.resolve("versions-"+theme+".png"));}
                AccountBehaviorTest.findButton(center,"数据包").doClick();var browser=(ContentBrowserPanel)field(center,"resources");check(((JComboBox<?>)field(browser,"world")).isEnabled(),"Data world control disabled");
                AccountBehaviorTest.findButton(center,"Mod").doClick();
                @SuppressWarnings("unchecked") var model=(DefaultListModel<ContentCatalog.Project>)field(browser,"projects");
                model.addElement(new ContentCatalog.Project(ContentCatalog.Source.MODRINTH,"fixture","Sodium · 性能优化","CaffeineMC","提升渲染效率，改善帧率与游戏体验。","https://modrinth.com/mod/sodium",ContentCatalog.Kind.MOD,"",251000000,"2026-10-20T12:00:00Z",List.of("optimization","fabric"),List.of("1.21.1")));
                model.addElement(new ContentCatalog.Project(ContentCatalog.Source.CURSEFORGE,"fixture-cf","冒险与探索","Example Team","来自另一资源平台的示例结果，保留独立的来源标记。","https://www.curseforge.com/",ContentCatalog.Kind.MOD,"",1234567,"2026-10-19T12:00:00Z",List.of("adventure"),List.of("1.21.1")));
                ((JTextArea)field(browser,"description")).setText("Sodium · 性能优化\n\n作者：CaffeineMC\n来源：Modrinth\n\n选择兼容文件后，可以直接安装到实例，也可以仅保存文件。\n\n列表展示简介、下载量、更新时间和类型标签。\n数据包会安装到所选存档的 datapacks 目录。");
                for(String theme:List.of("light","dark")){frame[0].previewTheme(theme);shot(frame[0],out.resolve("resources-"+theme+".png"));}
                frame[0].setSize(1000,740);frame[0].validate();shot(frame[0],out.resolve("resources-minimum.png"));
                var button=AccountBehaviorTest.findButton(center,"仅下载文件");check(button.getWidth()>90&&button.getHeight()>=35,"Resource actions clipped");
            }catch(Exception e){throw new RuntimeException(e);}});
        }finally{SwingUtilities.invokeAndWait(()->{if(frame[0]!=null)frame[0].dispose();});}
        System.out.println("DOWNLOAD_CENTER_GROUP_SEARCH_NAVIGATION_DATA_WORLD_THEMES_MINIMUM_OK");System.exit(0);
    }
    static void shot(LauncherFrame frame,Path file)throws Exception{frame.validate();Container content=frame.getContentPane();BufferedImage image=new BufferedImage(content.getWidth(),content.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();content.paint(g);g.dispose();ImageIO.write(image,"png",file.toFile());}
}

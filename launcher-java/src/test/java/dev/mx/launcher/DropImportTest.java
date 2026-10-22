package dev.mx.launcher;

import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.datatransfer.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.*;

/** Real main-window file transfer, explicit confirmation and a supplied large pack payload. */
public final class DropImportTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void edt(Runnable task)throws Exception{SwingUtilities.invokeAndWait(task);}
    static void zip(Path target,Map<String,byte[]> entries)throws Exception{try(ZipOutputStream output=new ZipOutputStream(Files.newOutputStream(target))){for(var entry:entries.entrySet()){output.putNextEntry(new ZipEntry(entry.getKey()));output.write(entry.getValue());output.closeEntry();}}}
    static byte[] bytes(String text){return text.getBytes(StandardCharsets.UTF_8);}
    static String manifest(){return Json.write(Map.of("game","minecraft","formatVersion",1,"versionId","1.0","name","DropPack","dependencies",Map.of("minecraft","1.21.1","fabric-loader","0.19.3"),"files",List.of()));}
    static void transfer(LauncherFrame frame,Path file){
        Transferable data=new Transferable(){public DataFlavor[] getTransferDataFlavors(){return new DataFlavor[]{DataFlavor.javaFileListFlavor};}public boolean isDataFlavorSupported(DataFlavor flavor){return flavor.equals(DataFlavor.javaFileListFlavor);}public Object getTransferData(DataFlavor flavor)throws UnsupportedFlavorException{if(!isDataFlavorSupported(flavor))throw new UnsupportedFlavorException(flavor);return List.of(file.toFile());}};
        JComponent target=(JComponent)frame.getContentPane();check(target.getTransferHandler().importData(new TransferHandler.TransferSupport(target,data)),"File drop rejected");
    }
    public static void main(String[] args)throws Exception{
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());Path home=Files.createTempDirectory("oml-drop-import-"),out=Path.of(args[0]);Files.createDirectories(out);LauncherPaths.override(home);Path root=LauncherPaths.gameRoot();LauncherFrame[] frame={null};
        try{
            for(String id:List.of("One","Two")){Path dir=root.resolve("versions/"+id);Files.createDirectories(dir.resolve("instance/versions/"+id));Files.writeString(dir.resolve(id+".json"),Json.write(Map.of("id",id,"mxcloudInstance",true,"libraries",List.of())));Files.write(dir.resolve("instance/versions/"+id+"/"+id+".jar"),new byte[]{0});}
            Settings settings=new Settings();settings.lastVersion="One";settings.save(home);
            Path jar=home.resolve("example.jar");zip(jar,Map.of("fabric.mod.json",bytes("{\"id\":\"example\",\"version\":\"1.0\"}")));
            edt(()->{frame[0]=new LauncherFrame(false);frame[0].setVisible(true);transfer(frame[0],jar);});
            Path installed=root.resolve("versions/One/instance/mods/example.jar");long deadline=System.currentTimeMillis()+8000;while(!Files.exists(installed)&&System.currentTimeMillis()<deadline)Thread.sleep(40);
            check(Files.exists(installed)&&Arrays.equals(Files.readAllBytes(jar),Files.readAllBytes(installed)),"Dropped JAR did not reach selected instance");check(!Files.exists(root.resolve("versions/Two/instance/mods/example.jar")),"Dropped JAR changed another instance");
            Thread.sleep(100);edt(()->{for(Window window:frame[0].getOwnedWindows())check(!(window instanceof VersionSettingsDialog&&window.isShowing()),"Dropped JAR opened management window");});
            Path archive=home.resolve("DropPack.zip");zip(archive,Map.of("modrinth.index.json",bytes(manifest()),"overrides/config/example.txt",bytes("settings"),"overrides/saves/World/level.dat",bytes("save")));
            CountDownLatch dismissed=new CountDownLatch(1);AtomicReference<Throwable> failure=new AtomicReference<>();javax.swing.Timer[] poll={null};long[] shownAt={0};long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            edt(()->{poll[0]=new javax.swing.Timer(60,e->{try{
                if(System.nanoTime()>until)throw new AssertionError("Drop confirmation did not appear");
                for(Window window:frame[0].getOwnedWindows())if(window instanceof AutoInstallDialog dialog&&dialog.isShowing()){
                    if(shownAt[0]==0)shownAt[0]=System.nanoTime();if(System.nanoTime()-shownAt[0]<TimeUnit.MILLISECONDS.toNanos(240))continue;
                    check(dialog.getTitle().equals("自动安装"),"Confirmation title");check(((JLabel)AccountUiBehaviorTest.findNamed(dialog,"autoInstallVersion")).getText().equals("版本：DropPack"),"Detected version label");
                    check(AccountBehaviorTest.findButton(dialog,"立即安装")!=null,"Install action missing");
                    ImageIO.write(AppearanceControlsTest.render(dialog.getContentPane()),"png",out.resolve("auto-install-confirmation.png").toFile());AccountBehaviorTest.findButton(dialog,"取消").doClick(0);poll[0].stop();dismissed.countDown();break;
                }
            }catch(Throwable error){failure.set(error);poll[0].stop();for(Window window:frame[0].getOwnedWindows())window.dispose();dismissed.countDown();}});poll[0].start();transfer(frame[0],archive);});
            check(dismissed.await(12,TimeUnit.SECONDS),"Confirmation timed out");if(failure.get()!=null)throw new AssertionError(failure.get());check(!Files.exists(root.resolve("versions/DropPack"))&&!Files.exists(root.resolve(".mx-downloads")),"Cancel started installation");
            var pack=ModpackInstaller.forImport(archive,root);Path payload=home.resolve("confirmed-payload");CountDownLatch completed=new CountDownLatch(1);AutoInstallDialog[] confirm={null};
            edt(()->{confirm[0]=new AutoInstallDialog(frame[0],root,pack,id->{check(id.equals("DropPack"),"Completion returned wrong id");completed.countDown();},worker->{ModpackInstaller.installContent(archive,pack,payload,worker,new ContentCatalog(""));return pack.options().id();});confirm[0].setModal(false);confirm[0].setVisible(true);AccountBehaviorTest.findButton(confirm[0],"立即安装").doClick(0);});
            check(completed.await(8,TimeUnit.SECONDS),"Immediate install did not run");check(Files.readString(payload.resolve("config/example.txt")).equals("settings")&&Files.readString(payload.resolve("saves/World/level.dat")).equals("save"),"Pack contents lost");
            Files.createDirectories(root.resolve("versions/DropPack"));check(ModpackInstaller.forImport(archive,root).options().id().equals("DropPack-2"),"Existing version would be overwritten");
            Path unsafe=home.resolve("unsafe.zip");zip(unsafe,Map.of("modrinth.index.json",bytes(manifest()),"overrides/../../escape.txt",bytes("bad")));boolean rejected=false;try{ModpackInstaller.forImport(unsafe,root);}catch(IOException expected){rejected=true;}check(rejected&&!Files.exists(home.resolve("escape.txt")),"Unsafe overrides accepted");
            if(args.length>1){
                Path supplied=Path.of(args[1]);var actual=ModpackInstaller.forImport(supplied,root);check(actual.options().minecraft().equals("1.21.1")&&actual.options().loader()==DownloadCatalog.Loader.FABRIC&&actual.options().loaderVersion().equals("0.19.3"),"Supplied archive metadata");
                Map<String,Object> contentOnly=new LinkedHashMap<>(actual.manifest());contentOnly.put("files",List.of());var local=new ModpackInstaller.Pack(actual.title(),actual.options(),actual.modrinth(),contentOnly);Path unpacked=home.resolve("supplied-payload");
                VersionInstaller offline=new VersionInstaller(root,p->{},url->{throw new IOException("Payload extraction must not download files");});ModpackInstaller.installContent(supplied,local,unpacked,offline,new ContentCatalog(""));
                long expected;try(ZipFile original=new ZipFile(supplied.toFile())){expected=original.stream().filter(entry->!entry.isDirectory()&&entry.getName().startsWith("overrides/")).count();for(String path:List.of("config/iris.properties","options.txt","servers.dat"))try(InputStream source=original.getInputStream(original.getEntry("overrides/"+path))){check(Arrays.equals(source.readAllBytes(),Files.readAllBytes(unpacked.resolve(path))),"Supplied content changed: "+path);}}
                long written;try(var files=Files.walk(unpacked)){written=files.filter(Files::isRegularFile).count();}check(written==expected&&written>50000,"Large supplied pack was truncated");System.out.println("SUPPLIED_ARCHIVE_ALL_OVERRIDE_FILES_EXTRACTED_OK count="+written);
            }
            System.out.println("REAL_MAIN_FILE_DROP_JAR_CURRENT_INSTANCE_NO_MANAGEMENT_POPUP_PACK_CONFIRM_CANCEL_IMMEDIATE_INSTALL_COLLISION_PATH_SAFETY_OK");
        }finally{
            edt(()->{if(frame[0]!=null){for(Window window:frame[0].getOwnedWindows())window.dispose();frame[0].dispose();}});
            Path temp=Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();check(home.toAbsolutePath().normalize().getParent().equals(temp)&&home.getFileName().toString().startsWith("oml-drop-import-"),"Unsafe fixture cleanup");try(var files=Files.walk(home)){for(Path path:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}
        }System.exit(0);
    }
}

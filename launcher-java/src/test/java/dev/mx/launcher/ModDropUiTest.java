package dev.mx.launcher;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.io.File;
import java.nio.file.*;
import java.util.List;
import java.util.zip.*;

/** Exercises the home-window file transfer handler and the production dialog import worker. */
public final class ModDropUiTest {
    public static void main(String[] args)throws Exception{
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        Path home=Files.createTempDirectory("mx-mod-drop-"),version=home.resolve(".min/versions/"+VersionInstaller.ID),input=home.resolve("拖入示例.jar");
        Files.createDirectories(version.resolve("instance/versions/"+VersionInstaller.ID));
        Files.writeString(version.resolve(VersionInstaller.ID+".json"),"{\"mxcloudInstance\":true}");
        Files.write(version.resolve("instance/versions/"+VersionInstaller.ID+"/"+VersionInstaller.ID+".jar"),new byte[0]);
        try(ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(input))){out.putNextEntry(new ZipEntry("fabric.mod.json"));out.write("{\"id\":\"test_drop\",\"name\":\"拖入示例\",\"version\":\"1\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));out.closeEntry();}
        LauncherPaths.override(home);LauncherFrame[] frame=new LauncherFrame[1];
        Transferable files=new Transferable(){public DataFlavor[] getTransferDataFlavors(){return new DataFlavor[]{DataFlavor.javaFileListFlavor};}public boolean isDataFlavorSupported(DataFlavor flavor){return DataFlavor.javaFileListFlavor.equals(flavor);}public Object getTransferData(DataFlavor flavor){return List.of(input.toFile());}};
        SwingUtilities.invokeAndWait(()->{frame[0]=new LauncherFrame(false);frame[0].setVisible(true);JComponent root=(JComponent)frame[0].getContentPane();if(!root.getTransferHandler().importData(new TransferHandler.TransferSupport(root,files)))throw new AssertionError("home drop rejected");});
        Path imported=version.resolve("instance/mods/拖入示例.jar");long deadline=System.currentTimeMillis()+15000;while(!Files.exists(imported)&&System.currentTimeMillis()<deadline)Thread.sleep(100);
        if(!Files.exists(imported)||Files.exists(home.resolve(".min/mods/拖入示例.jar")))throw new AssertionError("drop destination incorrect");
        if(!java.util.Arrays.equals(Files.readAllBytes(imported),Files.readAllBytes(input)))throw new AssertionError("drop content changed");
        SwingUtilities.invokeAndWait(()->{for(Window window:Window.getWindows())window.dispose();});
        System.out.println("HOME_DROP_IMPORT_SELECTED_INSTANCE_OK");System.exit(0);
    }
}

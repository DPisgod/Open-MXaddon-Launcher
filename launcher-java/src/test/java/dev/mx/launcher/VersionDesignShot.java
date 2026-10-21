package dev.mx.launcher;

import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;

/** Real production dialogs under the Windows look and feel; disposable game root. */
public final class VersionDesignShot {
    public static void main(String[] args)throws Exception{
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        Path home=Files.createTempDirectory("mx-version-ui-"),out=Path.of(args[0]);Files.createDirectories(out);LauncherPaths.override(home);
        Path version=home.resolve(".minecraft/versions/"+VersionInstaller.ID);Files.createDirectories(version.resolve("instance/versions/"+VersionInstaller.ID));
        Files.writeString(version.resolve(VersionInstaller.ID+".json"),"{\"mxcloudInstance\":true}");
        Files.write(version.resolve("instance/versions/"+VersionInstaller.ID+"/"+VersionInstaller.ID+".jar"),new byte[0]);
        ModManager mods=new ModManager(version.resolve("instance"));
        try(var files=Files.list(Path.of(args[1]))){for(Path file:files.filter(p->p.toString().endsWith(".jar")).toList())mods.add(file);}
        mods.toggle(mods.list().get(0).file());
        SwingUtilities.invokeAndWait(()->{
            for(String theme:new String[]{"light","dark"})try{
                UiTheme.set(theme);
                VersionSettingsDialog dialog=new VersionSettingsDialog(null,home.resolve(".minecraft"),VersionInstaller.ID);render(dialog,out.resolve("version-mods-"+theme+".png"));dialog.dispose();
                InstallVersionDialog install=new InstallVersionDialog(null,home.resolve(".minecraft"),id->{});render(install,out.resolve("version-install-"+theme+".png"));install.dispose();
            }catch(Exception e){throw new RuntimeException(e);}
        });
        System.out.println("VERSION_DIALOG_SHOTS_OK");System.exit(0);
    }
    static void render(JDialog dialog,Path path)throws Exception{
        dialog.addNotify();dialog.validate();dialog.dispatchEvent(new java.awt.event.ComponentEvent(dialog,java.awt.event.ComponentEvent.COMPONENT_RESIZED));Container content=dialog.getContentPane();BufferedImage image=new BufferedImage(content.getWidth(),content.getHeight(),BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics=image.createGraphics();if(dialog.getShape()!=null)graphics.clip(dialog.getShape());content.paint(graphics);graphics.dispose();ImageIO.write(image,"png",path.toFile());
    }
}

package dev.mx.launcher;

import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;

public final class DownloadsUiTest {
    public static void main(String[] args)throws Exception{
        Path home=Files.createTempDirectory("mx-download-ui-"),out=Path.of(args[0]);Files.createDirectories(out);LauncherPaths.override(home);Path root=LauncherPaths.gameRoot();
        SwingUtilities.invokeAndWait(()->{
            UiTheme.set("light");
            for(String theme:new String[]{"light","dark"}){
                UiTheme.set(theme);JDialog[] dialogs={new InstallVersionDialog(null,root,id->{},false),new ContentBrowserDialog(null,root,"",id->{},false)};
                for(JDialog dialog:dialogs){try{dialog.addNotify();dialog.validate();Container content=dialog.getContentPane();BufferedImage image=new BufferedImage(content.getWidth(),content.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D graphics=image.createGraphics();content.printAll(graphics);graphics.dispose();ImageIO.write(image,"png",out.resolve((dialog instanceof InstallVersionDialog?"game-installer-":"resources-")+theme+".png").toFile());}catch(Exception failure){throw new RuntimeException(failure);}finally{dialog.dispose();}}
            }
        });
        System.out.println("DOWNLOADS_UI_THEMES_OK");System.exit(0);
    }
}

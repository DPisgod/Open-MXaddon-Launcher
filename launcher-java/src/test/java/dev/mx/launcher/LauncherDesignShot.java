package dev.mx.launcher;

import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;

/** Renders the production views against disposable local fixture data, without auth side effects. */
public final class LauncherDesignShot {
    public static void main(String[] args)throws Exception{
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        Path home=Path.of(args[0]),out=Path.of(args[1]);Files.createDirectories(home);Files.createDirectories(out);LauncherPaths.override(home);SessionStore.save(home,Session.offline("Seven"));
        Appearance initial=new Appearance();initial.theme="light";initial.save(home);
        if(args.length>2){WallpaperLibrary lib=new WallpaperLibrary(home);if(lib.items().isEmpty())for(String name:new String[]{"静态 预览.PNG","motion.mp4","motion.gif","photo.jpg"})lib.importFile(Path.of(args[2]).resolve(name));}
        Path version=home.resolve(".minecraft/versions/1.21.1-Fabric 0.19.3");Files.createDirectories(version);Files.writeString(version.resolve("1.21.1-Fabric 0.19.3.json"),"{\"id\":\"1.21.1-Fabric 0.19.3\",\"libraries\":[{\"name\":\"net.fabricmc:fabric-loader:0.19.3\"}]}");Files.write(version.resolve("1.21.1-Fabric 0.19.3.jar"),new byte[0]);
        LauncherFrame[] box=new LauncherFrame[1];SwingUtilities.invokeAndWait(()->{box[0]=new LauncherFrame(false);box[0].setVisible(true);});Thread.sleep(500);
        for(String theme:new String[]{"light","dark"}){
            SwingUtilities.invokeAndWait(()->box[0].previewTheme(theme));Thread.sleep(180);shot(box[0],out.resolve("home-"+theme+".png"));
        }
        SwingUtilities.invokeAndWait(()->box[0].showAppearance());Thread.sleep(800);
        if(args.length>2)SwingUtilities.invokeAndWait(()->{JButton last=AccountBehaviorTest.findButton(box[0].appearancePanel(),"photo.jpg");if(last!=null)last.doClick();});
        for(String theme:new String[]{"light","dark"}){
            SwingUtilities.invokeAndWait(()->box[0].previewTheme(theme));Thread.sleep(180);shot(box[0],out.resolve("wallpapers-"+theme+".png"));
        }
        SwingUtilities.invokeAndWait(()->box[0].setSize(1000,740));Thread.sleep(200);shot(box[0],out.resolve("wallpapers-minimum-size.png"));
        if(args.length>3&&args[3].equals("keep")){System.out.println("UI_PREVIEW_OPEN");return;}
        SwingUtilities.invokeAndWait(()->box[0].dispose());System.out.println("DESIGN_SHOTS_OK");System.exit(0);
    }
    static void shot(LauncherFrame frame,Path path)throws Exception{
            BufferedImage[] image=new BufferedImage[1];SwingUtilities.invokeAndWait(()->{Component content=frame.getContentPane();BufferedImage rendered=new BufferedImage(content.getWidth(),content.getHeight(),BufferedImage.TYPE_INT_ARGB);Graphics2D g=rendered.createGraphics();if(frame.getShape()!=null)g.clip(frame.getShape());content.paint(g);g.dispose();image[0]=rendered;});ImageIO.write(image[0],"png",path.toFile());
    }
}

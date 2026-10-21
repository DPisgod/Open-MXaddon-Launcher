package dev.mx.launcher;

import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;

/** Production account picker rendered with disposable offline and explicitly fake premium entries. */
public final class AccountDesignShot {
    public static void main(String[] args)throws Exception {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        Path home=Files.createTempDirectory("mx-account-ui-"),out=Path.of(args[0]);Files.createDirectories(out);LauncherPaths.override(home);
        Session chinese=Session.offline("测试玩家_01");SessionStore.save(home,chinese);
        AccountStore store=new AccountStore(home);store.remember(chinese);store.remember(Session.offline("小明"));store.remember(Session.premium("OnlineDemo","123456781234123412341234567890ab","fake-test-token-no-access",null));
        if(args.length>2){
            Path gameHome=Path.of(args[2]);Files.createDirectories(home.resolve("skins"));String[] skins={"alex","ari","steve"};int i=0;
            for(Session account:store.available(null)){BufferedImage head=SkinFetcher.gameDefaultHead(gameHome,skins[i++]);if(head!=null)ImageIO.write(head,"png",home.resolve("skins").resolve(SkinFetcher.cacheKey(account)+".png").toFile());}
        }
        Appearance options=new Appearance();options.theme="dark";options.save(home);
        Path version=home.resolve(".minecraft/versions/1.21.1-Fabric 0.19.3");Files.createDirectories(version);Files.writeString(version.resolve("1.21.1-Fabric 0.19.3.json"),"{\"id\":\"1.21.1-Fabric 0.19.3\",\"libraries\":[{\"name\":\"net.fabricmc:fabric-loader:0.19.3\"}]}");Files.write(version.resolve("1.21.1-Fabric 0.19.3.jar"),new byte[0]);
        LauncherFrame[] frame=new LauncherFrame[1];SwingUtilities.invokeAndWait(()->{frame[0]=new LauncherFrame(false);frame[0].setVisible(true);});Thread.sleep(250);
        for(String theme:new String[]{"light","dark"}){
            SwingUtilities.invokeAndWait(()->{MenuSelectionManager.defaultManager().clearSelectedPath();frame[0].previewTheme(theme);frame[0].showAccounts();});Thread.sleep(200);shot(frame[0],out.resolve("account-switcher-"+theme+".png"));
        }
        SwingUtilities.invokeAndWait(()->{MenuSelectionManager.defaultManager().clearSelectedPath();frame[0].setSize(1000,740);});Thread.sleep(200);SwingUtilities.invokeAndWait(()->frame[0].showAccounts());Thread.sleep(200);shot(frame[0],out.resolve("account-switcher-minimum.png"));
        if(args.length>1&&args[1].equals("keep")){System.out.println("ACCOUNT_PREVIEW_OPEN");return;}
        SwingUtilities.invokeAndWait(()->frame[0].dispose());System.out.println("ACCOUNT_DESIGN_SHOT_OK");System.exit(0);
    }
    static void shot(LauncherFrame frame,Path path)throws Exception{
        SwingUtilities.invokeAndWait(()->{Component content=frame.getLayeredPane();BufferedImage image=new BufferedImage(content.getWidth(),content.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();content.paint(g);g.dispose();try{ImageIO.write(image,"png",path.toFile());}catch(Exception e){throw new RuntimeException(e);}});
    }
}

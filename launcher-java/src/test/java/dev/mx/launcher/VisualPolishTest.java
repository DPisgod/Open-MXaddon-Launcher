package dev.mx.launcher;
import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;

/** Regressions for native-white bars, unshaped window corners, and selection escaping a viewport. */
public final class VisualPolishTest {
 static void check(boolean valid,String message){if(!valid)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());Path home=Files.createTempDirectory("mx-polish-");LauncherPaths.override(home);Path out=Path.of(args[0]);Files.createDirectories(out);
  LauncherFrame[] frame=new LauncherFrame[1];InstallVersionDialog[] dialog=new InstallVersionDialog[1];
  SwingUtilities.invokeAndWait(()->{frame[0]=new LauncherFrame(false);frame[0].setVisible(true);dialog[0]=new InstallVersionDialog(frame[0],home.resolve(".minecraft"),id->{});dialog[0].addNotify();});
  Thread.sleep(200);
  SwingUtilities.invokeAndWait(()->{
   for(Window window:new Window[]{frame[0],dialog[0]})checkRounded((RootPaneContainer)window);
   for(String theme:new String[]{"light","dark"})try{
    UiTheme.set(theme);BufferedImage image=new BufferedImage(440,170,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();g.setColor(UiTheme.p().surface());g.fillRect(0,0,440,170);g.setColor(UiTheme.p().text());g.setFont(UiTheme.font(12,false));
    for(int percent:new int[]{0,33,100}){UiTheme.ProgressBar bar=new UiTheme.ProgressBar();bar.setSize(380,7);bar.setValue(percent*10);BufferedImage pixels=new BufferedImage(380,7,BufferedImage.TYPE_INT_ARGB);Graphics2D paint=pixels.createGraphics();bar.paint(paint);paint.dispose();check(pixels.getRGB(375,3)!=Color.WHITE.getRGB(),"white native progress painter remained");if(percent==33)check((pixels.getRGB(20,3)&255)>((pixels.getRGB(20,3)>>16)&255)+25,"progress is not blue");int y=20+(percent==0?0:percent==33?1:2)*48;g.drawString(percent+"%",28,y);g.drawImage(pixels,28,y+10,null);}
    g.dispose();ImageIO.write(image,"png",out.resolve("progress-"+theme+".png").toFile());
   }catch(Exception e){throw new RuntimeException(e);}
   UiTheme.set("dark");AppearancePanel panel=new AppearancePanel(new WallpaperLibrary(home),new Appearance(),value->true,()->{});JButton tile=AccountBehaviorTest.findButton(panel,WallpaperLibrary.DEFAULT_NAME);check(tile!=null,"wallpaper tile missing");tile.setSize(200,145);BufferedImage canvas=new BufferedImage(200,145,BufferedImage.TYPE_INT_RGB);Graphics2D g=canvas.createGraphics();g.setColor(Color.MAGENTA);g.fillRect(0,0,200,145);g.setClip(0,0,200,30);tile.paint(g);g.dispose();check(canvas.getRGB(1,100)==Color.MAGENTA.getRGB(),"selection border escaped viewport");panel.close();dialog[0].dispose();frame[0].dispose();
  });System.out.println("ROUND_WINDOW_THEME_PROGRESS_VIEWPORT_CLIP_OK");System.exit(0);
 }
 static void checkRounded(RootPaneContainer window){
  Window nativeWindow=(Window)window;check(nativeWindow.getBackground().getAlpha()==255,"layered transparent window breaks smart capture");
  if(Boolean.TRUE.equals(window.getRootPane().getClientProperty("mxcloud.nativeCorners")))check(nativeWindow.getShape()==null,"native DWM corners blocked by a window region");
  else if(nativeWindow.isVisible())check(nativeWindow.getShape()!=null&&!nativeWindow.getShape().contains(.5,.5),"fallback corners not rounded");
 }
}

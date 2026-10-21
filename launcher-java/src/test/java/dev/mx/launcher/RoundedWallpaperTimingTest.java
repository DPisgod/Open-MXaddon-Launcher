package dev.mx.launcher;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;

/** Exercises the complete rounded launcher paint tree at normal and fractional device scales. */
public final class RoundedWallpaperTimingTest {
 public static void main(String[] args)throws Exception{
  UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());Path home=Files.createTempDirectory("mx-rounded-video-");LauncherPaths.override(home);WallpaperLibrary library=new WallpaperLibrary(home);List<String> report=new ArrayList<>();
  for(int fps:new int[]{30,60})for(double scale:new double[]{1,1.25}){
   WallpaperLibrary.Item item=library.importFile(Path.of(args[0]).resolve("motion-"+fps+".mp4"));Appearance appearance=new Appearance();appearance.wallpaper=item.id();appearance.loop=true;appearance.mask=0;appearance.uiTransparency=60;appearance.save(home);
   LauncherFrame[] frame=new LauncherFrame[1];List<Long> presented=new CopyOnWriteArrayList<>();
   BufferedImage canvas=new BufferedImage((int)(1140*scale),(int)Math.ceil(810*scale),BufferedImage.TYPE_INT_RGB);
   SwingUtilities.invokeAndWait(()->{frame[0]=new LauncherFrame(false);frame[0].setVisible(true);frame[0].wallpaperPlayer().onChange(()->{Graphics2D g=canvas.createGraphics();g.scale(scale,scale);frame[0].getContentPane().paint(g);g.dispose();presented.add(System.nanoTime());});});
   try{
    WallpaperBehaviorTest.await(()->presented.size()>fps*3,20000,"rounded video did not start");presented.clear();Thread.sleep(5000);List<Long> sample=List.copyOf(presented);
    double actual=(sample.size()-1)*1e9/(sample.get(sample.size()-1)-sample.get(0));if(actual<fps*.95||actual>fps*1.05)throw new AssertionError("rounded video cadence: "+fps+" scale="+scale+" actual="+actual);
    String line=String.format(java.util.Locale.ROOT,"ROUNDED_%d_SCALE_%.2f_PRESENTED_%.2f_FPS",fps,scale,actual);report.add(line);System.out.println(line);
   }finally{SwingUtilities.invokeAndWait(()->frame[0].dispose());}
  }
  Files.write(Path.of(args[1]).resolve("rounded-video-timing.txt"),report);System.out.println("ROUNDED_VIDEO_CADENCE_OK");System.exit(0);
 }
}

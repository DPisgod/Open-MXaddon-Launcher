package dev.mx.launcher;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Measures actual presented source cadence, rather than inspecting FFmpeg argument strings. */
public final class WallpaperTimingTest {
    public static void main(String[] args)throws Exception {
        Path fixtures=Path.of(args[0]),home=Files.createTempDirectory("mx-wallpaper-timing-");
        LauncherPaths.override(home);WallpaperLibrary library=new WallpaperLibrary(home);
        List<String> report=new ArrayList<>();
        for(int fps:new int[]{30,60}) {
            WallpaperLibrary.Item item=library.importFile(fixtures.resolve("motion-"+fps+".mp4"));
            WallpaperPlayer player=new WallpaperPlayer();Appearance settings=new Appearance();settings.loop=false;
            List<Long> presented=new CopyOnWriteArrayList<>();
            WallpaperView view=new WallpaperView(player,settings,0);view.setSize(1140,720);
            java.awt.image.BufferedImage canvas=new java.awt.image.BufferedImage(1140,720,java.awt.image.BufferedImage.TYPE_INT_RGB);
            player.onChange(()->{java.awt.Graphics2D g=canvas.createGraphics();view.paint(g);g.dispose();presented.add(System.nanoTime());});player.select(item,settings);
            WallpaperBehaviorTest.await(()->presented.size()>fps,12000,"video did not begin: "+player.error());
            presented.clear();Thread.sleep(1600);
            List<Long> sample=List.copyOf(presented);
            double actual=(sample.size()-1)*1_000_000_000.0/(sample.get(sample.size()-1)-sample.get(0));
            player.close();
            WallpaperBehaviorTest.require(actual>fps*.95&&actual<fps*1.05,"source cadence changed: "+fps+" -> "+actual);
            report.add(String.format(java.util.Locale.ROOT,"SOURCE_%d_PRESENTED_%.2f_FPS frames=%d",fps,actual,sample.size()));
        }
        Files.write(home.resolve("timing.txt"),report);report.forEach(System.out::println);
        System.out.println("SOURCE_CADENCE_OK");System.exit(0);
    }
}

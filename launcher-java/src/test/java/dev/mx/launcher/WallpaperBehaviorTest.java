package dev.mx.launcher;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.awt.image.BufferedImage;
import javax.swing.SwingUtilities;

/** Real media decoding and persistence tests; no production account or game is touched. */
public final class WallpaperBehaviorTest {
    static void require(boolean yes,String message){if(!yes)throw new AssertionError(message);}
    static void await(BooleanSupplier condition,int millis,String message)throws Exception{long until=System.currentTimeMillis()+millis;while(!condition.getAsBoolean()&&System.currentTimeMillis()<until)Thread.sleep(40);require(condition.getAsBoolean(),message);}
    public static void main(String[] args)throws Exception{
        Path fixtures=Path.of(args[0]),home=Files.createTempDirectory("mx-wallpaper-test-");
        LauncherPaths.override(home);WallpaperLibrary library=new WallpaperLibrary(home);List<WallpaperLibrary.Item> imported=new ArrayList<>();
        for(String file:new String[]{"静态 预览.PNG","photo.jpg","photo.webp","motion.gif","motion.mp4","motion.webm"}){
            WallpaperLibrary.Item item=library.importFile(fixtures.resolve(file));require(Files.isRegularFile(item.thumbnail()),"thumbnail missing "+file);require(WallpaperLibrary.probe(item.file())!=null,"probe failed "+file);imported.add(item);System.out.println("IMPORT_OK "+file);
        }
        require(new WallpaperLibrary(home).items().size()==6,"library not persisted");
        Path invalid=fixtures.resolve("invalid.png");Files.writeString(invalid,"not an image");boolean rejected=false;try{library.importFile(invalid);}catch(Exception expected){rejected=true;}require(rejected&&library.items().size()==6,"invalid import was not rolled back");
        try(var files=Files.list(home.resolve("wallpapers"))){require(files.count()==13,"failed import left partial files");}
        Appearance options=new Appearance();options.theme="dark";options.wallpaper=imported.get(4).id();options.blur=17;options.mask=32;options.fit="contain";options.muted=true;options.loop=true;options.pauseWithGame=true;options.save(home);
        options.uiTransparency=65;options.save(home);
        Appearance restored=Appearance.load(home);require(restored.theme.equals("dark")&&restored.wallpaper.equals(options.wallpaper)&&restored.blur==17&&restored.mask==32&&restored.fit.equals("contain")&&restored.uiTransparency==65&&restored.copy().uiTransparency==65,"appearance persistence failed");
        for(int i:new int[]{3,4,5}){
            WallpaperPlayer player=new WallpaperPlayer();player.select(imported.get(i),options);await(()->player.frameCount()>8,12000,"animation did not advance "+imported.get(i).extension()+" "+player.error());
            int before=hash(player.frame());Thread.sleep(350);require(before!=hash(player.frame()),"animated wallpaper pixels did not change");
            player.setPaused(true);Thread.sleep(120);int count=player.frameCount();Thread.sleep(400);require(player.frameCount()==count,"paused wallpaper kept decoding");require(!player.decoderAlive(),"paused decoder remained alive");
            player.setPaused(false);await(()->player.frameCount()>count+5,12000,"animation did not resume");player.close();Thread.sleep(150);require(!player.decoderAlive(),"decoder leaked after close");System.out.println("PLAY_PAUSE_RESUME_CLOSE_OK "+imported.get(i).extension());
        }
        for(int i:new int[]{3,4}){
            WallpaperPlayer player=new WallpaperPlayer();Appearance once=options.copy();once.loop=false;player.select(imported.get(i),once);await(()->player.frameCount()>3,12000,"single-play did not decode");Thread.sleep(1800);int count=player.frameCount();Thread.sleep(500);require(player.frameCount()==count&&!player.decoderAlive(),"loop-off did not stop at EOF");player.close();System.out.println("LOOP_OFF_OK "+imported.get(i).extension());
        }
        WallpaperPlayer cancelled=new WallpaperPlayer();cancelled.select(imported.get(4),options);cancelled.close();Thread.sleep(400);require(!cancelled.decoderAlive(),"rapid cancellation leaked process");
        WallpaperPlayer frozen=new WallpaperPlayer();frozen.setPaused(true);frozen.select(imported.get(4),options);require(frozen.frame().getWidth()<=480&&!frozen.decoderAlive(),"paused preview did not load the selected poster");frozen.close();System.out.println("PAUSED_POSTER_OK");
        WallpaperPlayer sound=new WallpaperPlayer();Appearance withSound=options.copy();withSound.muted=false;sound.select(imported.get(4),withSound);await(()->sound.frameCount()>8,12000,"audio-enabled video failed");sound.setPaused(true);sound.close();System.out.println("SILENT_AUDIO_TRACK_OK");
        System.out.println("WALLPAPER_BEHAVIOR_OK home="+home);System.exit(0);
    }
    static int hash(BufferedImage image){int value=1;for(int y=0;y<image.getHeight();y+=13)for(int x=0;x<image.getWidth();x+=13)value=31*value+image.getRGB(x,y);return value;}
}

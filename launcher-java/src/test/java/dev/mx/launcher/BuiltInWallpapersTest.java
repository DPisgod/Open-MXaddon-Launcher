package dev.mx.launcher;

import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Exercises the media shipped in the real runtime, using no user wallpaper files. */
public final class BuiltInWallpapersTest {
    public static void main(String[] args) throws Exception {
        Path home=Files.createTempDirectory("oml-builtins-");LauncherPaths.override(home);
        WallpaperLibrary library=new WallpaperLibrary(home);
        check(library.builtIns().size()==5,"Missing bundled wallpaper");
        check(library.items().isEmpty(),"Bundled wallpapers entered personal library");
        check(Files.isRegularFile(WallpaperLibrary.decoder()),"Decoder is not shipped");
        Set<String> titles=new HashSet<>();
        for(var item:library.builtIns()){
            titles.add(item.title());check(library.find(item.id())!=null,"Stable ID not resolved");
            check(ImageIO.read(item.thumbnail().toFile())!=null,"Thumbnail missing");
            check(WallpaperLibrary.probe(item.file())!=null,"Cannot decode "+item.title());
            if(item.animated())try(WallpaperPlayer player=new WallpaperPlayer()){
                Appearance options=new Appearance();options.wallpaper=item.id();options.save(home);
                check(new WallpaperLibrary(home).find(Appearance.load(home).wallpaper)!=null,"Selection not restored");
                player.select(item,options);
                WallpaperBehaviorTest.await(()->player.frameCount()>12,15000,"Playback failed: "+item.title()+" "+player.error());
                player.setPaused(true);Thread.sleep(150);int frames=player.frameCount();Thread.sleep(180);
                check(player.frameCount()==frames&&!player.decoderAlive(),"Paused decoder remains active");
                player.setPaused(false);
                WallpaperBehaviorTest.await(()->player.frameCount()>frames+4,15000,"Resume failed");
            }
            System.out.println("BUILTIN_MEDIA_OK "+item.title());
        }
        check(titles.equals(Set.of("水灵灵","线条风","刀客","动漫女孩","红色力量")),"Display names differ");
        check(!Files.exists(home.resolve("wallpapers/library.json")),"Bundled library modified user data");
        try(var stream=WallpaperPlayer.class.getResourceAsStream("/wallpapers/Windows10.png")){
            check(stream!=null,"Default missing");var image=ImageIO.read(stream);
            try(WallpaperPlayer player=new WallpaperPlayer()){
                check(player.frame().getRGB(200,200)==image.getRGB(200,200),"Default image differs");
            }
        }
        check(WallpaperPlayer.class.getResource("/wallpapers/default-landscape.png")==null,"Old default still bundled");
        SwingUtilities.invokeAndWait(()->{
            try(AppearancePanel panel=new AppearancePanel(library,new Appearance(),a->true,()->{})){
                for(String name:List.of("Windows10","水灵灵","线条风","刀客","动漫女孩","红色力量"))
                    check(AccountBehaviorTest.findButton(panel,name)!=null,"Missing wallpaper tile "+name);
            }
        });
        System.out.println("SIX_BUILTINS_DEFAULT_NAMES_PERSIST_PLAY_PAUSE_RESUME_OK");System.exit(0);
    }
    static void check(boolean okay,String message){if(!okay)throw new AssertionError(message);}
}

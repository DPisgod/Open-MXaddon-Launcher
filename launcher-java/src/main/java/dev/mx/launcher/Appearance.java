package dev.mx.launcher;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/** Personal appearance settings, stored separately from authorization and game settings. */
final class Appearance {
    String theme="light", wallpaper="", fit="cover";
    int blur=0, mask=12, uiTransparency=0;
    boolean loop=true, muted=true, pauseWithGame=true;

    Appearance copy() {
        Appearance a=new Appearance();
        a.theme=theme;a.wallpaper=wallpaper;a.fit=fit;a.blur=blur;a.mask=mask;a.uiTransparency=uiTransparency;
        a.loop=loop;a.muted=muted;a.pauseWithGame=pauseWithGame;return a;
    }
    static Appearance load(Path home) {
        Appearance a=new Appearance();Properties p=new Properties();
        try(InputStream in=Files.newInputStream(LauncherPaths.dataFile(home,"appearance.properties"))){p.load(in);}
        catch(IOException ignored){return a;}
        // Import the previous two-axis settings into one color preset.
        String previous=p.getProperty("theme","light");
        a.theme="cyber".equals(p.getProperty("buttonStyle"))?"cyber":
                "system".equals(previous)?(UiTheme.systemDark()?"dark":"light"):oneOf(previous,"light","dark","cyber","china-red","sakura","purple");
        a.fit=oneOf(p.getProperty("fit"),"cover","contain","stretch");
        a.wallpaper=p.getProperty("wallpaper","");
        a.blur=number(p.getProperty("blur"),0,0,100);
        a.mask=number(p.getProperty("mask"),12,0,85);
        a.uiTransparency=number(p.getProperty("uiTransparency"),0,0,100);
        a.loop=Boolean.parseBoolean(p.getProperty("loop","true"));
        a.muted=Boolean.parseBoolean(p.getProperty("muted","true"));
        a.pauseWithGame=Boolean.parseBoolean(p.getProperty("pauseWithGame","true"));return a;
    }
    void save(Path home) throws IOException {
        Path file=LauncherPaths.dataFile(home,"appearance.properties");Properties p=new Properties();
        p.setProperty("theme",theme);p.setProperty("wallpaper",wallpaper);p.setProperty("fit",fit);
        p.setProperty("blur",Integer.toString(blur));p.setProperty("mask",Integer.toString(mask));
        p.setProperty("uiTransparency",Integer.toString(uiTransparency));
        p.setProperty("loop",Boolean.toString(loop));p.setProperty("muted",Boolean.toString(muted));
        p.setProperty("pauseWithGame",Boolean.toString(pauseWithGame));
        Path temp=Files.createTempFile(file.getParent(),"appearance-",".tmp");
        try {
            try(OutputStream out=Files.newOutputStream(temp)){p.store(out,"OML appearance");}
            try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temp);}
    }
    private static int number(String s,int fallback,int min,int max){try{return Math.max(min,Math.min(max,Integer.parseInt(s)));}catch(Exception e){return fallback;}}
    private static String oneOf(String s,String... values){for(String v:values)if(v.equals(s))return v;return values[0];}
}

package dev.mx.launcher;

import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.util.Properties;

final class PlayerDollOptions {
    boolean enabled=true,manual,cape=true;
    String player="";
    int scale=100,x=Integer.MIN_VALUE,y=Integer.MIN_VALUE;
    PlayerDollOptions copy(){PlayerDollOptions p=new PlayerDollOptions();p.enabled=enabled;p.manual=manual;p.cape=cape;p.player=player;p.scale=scale;p.x=x;p.y=y;return p;}
    static PlayerDollOptions load(Path home){PlayerDollOptions p=new PlayerDollOptions();Properties values=new Properties();try(InputStream input=Files.newInputStream(file(home))){values.load(input);}catch(IOException ignored){p.enabled=Appearance.load(home).doll;return p;}
        p.enabled=Boolean.parseBoolean(values.getProperty("enabled","false"));p.manual=Boolean.parseBoolean(values.getProperty("manual","false"));p.cape=Boolean.parseBoolean(values.getProperty("cape","true"));p.player=values.getProperty("player","");p.scale=number(values,"scale",100,60,180);p.x=number(values,"x",Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MAX_VALUE);p.y=number(values,"y",Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MAX_VALUE);if(!"launcher".equals(values.getProperty("placement"))){p.enabled=p.enabled||Appearance.load(home).doll;p.x=Integer.MIN_VALUE;p.y=Integer.MIN_VALUE;}return p;
    }
    void save(Path home)throws IOException{Properties p=new Properties();p.setProperty("enabled",Boolean.toString(enabled));p.setProperty("manual",Boolean.toString(manual));p.setProperty("cape",Boolean.toString(cape));p.setProperty("placement","launcher");p.setProperty("player",player);p.setProperty("scale",Integer.toString(scale));p.setProperty("x",Integer.toString(x));p.setProperty("y",Integer.toString(y));Path file=file(home),temp=Files.createTempFile(file.getParent(),"doll-",".tmp");try{try(OutputStream out=Files.newOutputStream(temp)){p.store(out,"Player doll preferences");}Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}}
    private static Path file(Path home){return LauncherPaths.dataDirectory(home).resolve("player-doll.properties");}
    private static int number(Properties p,String key,int fallback,int min,int max){try{return Math.max(min,Math.min(max,Integer.parseInt(p.getProperty(key))));}catch(RuntimeException invalid){return fallback;}}
    static Point position(int x,int y,Dimension size,Dimension area){
        int maxX=Math.max(0,area.width-size.width-12),maxY=Math.max(0,area.height-size.height-12);
        if(x==Integer.MIN_VALUE||y==Integer.MIN_VALUE){x=maxX-12;y=maxY-4;}
        return new Point(Math.max(Math.min(12,maxX),Math.min(x,maxX)),Math.max(Math.min(12,maxY),Math.min(y,maxY)));
    }
}

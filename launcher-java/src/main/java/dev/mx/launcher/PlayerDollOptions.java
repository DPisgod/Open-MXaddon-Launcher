package dev.mx.launcher;

import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.util.Properties;

final class PlayerDollOptions {
    boolean enabled,manual,cape=true,topmost=true;
    String player="";
    int scale=100,x=Integer.MIN_VALUE,y=Integer.MIN_VALUE;
    PlayerDollOptions copy(){PlayerDollOptions p=new PlayerDollOptions();p.enabled=enabled;p.manual=manual;p.cape=cape;p.topmost=topmost;p.player=player;p.scale=scale;p.x=x;p.y=y;return p;}
    static PlayerDollOptions load(Path home){PlayerDollOptions p=new PlayerDollOptions();Properties values=new Properties();try(InputStream input=Files.newInputStream(file(home))){values.load(input);}catch(IOException ignored){return p;}
        p.enabled=Boolean.parseBoolean(values.getProperty("enabled","false"));p.manual=Boolean.parseBoolean(values.getProperty("manual","false"));p.cape=Boolean.parseBoolean(values.getProperty("cape","true"));p.topmost=Boolean.parseBoolean(values.getProperty("topmost","true"));p.player=values.getProperty("player","");p.scale=number(values,"scale",100,60,180);p.x=number(values,"x",Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MAX_VALUE);p.y=number(values,"y",Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MAX_VALUE);return p;
    }
    void save(Path home)throws IOException{Properties p=new Properties();p.setProperty("enabled",Boolean.toString(enabled));p.setProperty("manual",Boolean.toString(manual));p.setProperty("cape",Boolean.toString(cape));p.setProperty("topmost",Boolean.toString(topmost));p.setProperty("player",player);p.setProperty("scale",Integer.toString(scale));p.setProperty("x",Integer.toString(x));p.setProperty("y",Integer.toString(y));Path file=file(home),temp=Files.createTempFile(file.getParent(),"doll-",".tmp");try{try(OutputStream out=Files.newOutputStream(temp)){p.store(out,"Player doll preferences");}Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}}
    private static Path file(Path home){return LauncherPaths.dataDirectory(home).resolve("player-doll.properties");}
    private static int number(Properties p,String key,int fallback,int min,int max){try{return Math.max(min,Math.min(max,Integer.parseInt(p.getProperty(key))));}catch(RuntimeException invalid){return fallback;}}
    static Rectangle workArea(GraphicsConfiguration config){Rectangle b=new Rectangle(config.getBounds());Insets insets=Toolkit.getDefaultToolkit().getScreenInsets(config);b.x+=insets.left;b.y+=insets.top;b.width-=insets.left+insets.right;b.height-=insets.top+insets.bottom;return b;}
    static Point position(int x,int y,Dimension size){
        Rectangle nearest=null;for(GraphicsDevice device:GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()){Rectangle area=workArea(device.getDefaultConfiguration());if(nearest==null)nearest=area;if(new Rectangle(x,y,size.width,size.height).intersects(area)){nearest=area;break;}}
        if(x==Integer.MIN_VALUE||y==Integer.MIN_VALUE||!new Rectangle(x,y,size.width,size.height).intersects(nearest)){x=nearest.x+nearest.width-size.width-32;y=nearest.y+nearest.height-size.height-12;}
        return new Point(Math.max(nearest.x,Math.min(x,nearest.x+nearest.width-size.width)),Math.max(nearest.y,Math.min(y,nearest.y+nearest.height-size.height)));
    }
}

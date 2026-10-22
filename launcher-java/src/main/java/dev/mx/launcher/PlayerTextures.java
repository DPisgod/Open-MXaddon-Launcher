package dev.mx.launcher;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Public Java Edition profile textures; account access tokens are never used here. */
final class PlayerTextures {
    record Profile(String uuid,String name,BufferedImage skin,BufferedImage cape,boolean slim){}
    @FunctionalInterface interface Source {byte[] read(String url,int maximum)throws IOException;}
    private final Path cache;
    private final Source source;
    PlayerTextures(Path home){this(home,PlayerTextures::download);}
    PlayerTextures(Path home,Source source){cache=LauncherPaths.dataDirectory(home).resolve("player-doll-cache");this.source=source;}
    Profile fetch(String id)throws IOException{
        String input=id==null?"":id.strip(),uuid=input.replace("-","");
        if(!uuid.matches("[a-fA-F0-9]{32}")){
            if(!input.matches("[A-Za-z0-9_]{1,16}"))throw new IOException("请输入正版 Java 玩家名或 UUID");
            var lookup=json(source.read("https://api.mojang.com/users/profiles/minecraft/"+input,65536));uuid=Json.str(lookup,"id");
        }
        if(uuid==null||!uuid.matches("[a-fA-F0-9]{32}"))throw new IOException("没有找到这个正版玩家");
        uuid=uuid.toLowerCase(Locale.ROOT);var profile=json(source.read("https://sessionserver.mojang.com/session/minecraft/profile/"+uuid,1024*1024));
        Map<String,Object> textures=null;
        for(Object raw:VersionInstaller.list(profile.get("properties"))){var property=VersionInstaller.map(raw);if("textures".equals(property.get("name")))try{textures=VersionInstaller.map(json(Base64.getDecoder().decode(Json.str(property,"value"))).get("textures"));}catch(IllegalArgumentException malformed){throw new IOException("玩家材质数据无效",malformed);}}
        if(textures==null)throw new IOException("这个玩家没有可读取的皮肤资料");
        var skinData=VersionInstaller.map(textures.get("SKIN"));var capeData=VersionInstaller.map(textures.get("CAPE"));
        BufferedImage skin=skinData.isEmpty()?defaultSkin():texture(Json.str(skinData,"url"),false),cape=capeData.isEmpty()?null:texture(Json.str(capeData,"url"),true);
        boolean slim="slim".equals(VersionInstaller.map(skinData.get("metadata")).get("model"));String name=Objects.toString(profile.get("name"),input);
        Profile result=new Profile(uuid,name,skin,cape,slim);try{save(result);}catch(IOException ignored){}return result;
    }
    Profile cached(String id){
        if(id==null)return null;String uuid=id.replace("-","").toLowerCase(Locale.ROOT);
        try{
            if(!uuid.matches("[a-f0-9]{32}")){
                if(!Files.isDirectory(cache))return null;try(var paths=Files.list(cache)){for(Path file:paths.filter(p->p.toString().endsWith(".properties")).toList()){Properties p=new Properties();try(InputStream in=Files.newInputStream(file)){p.load(in);}if(id.equalsIgnoreCase(p.getProperty("name"))){uuid=p.getProperty("uuid","");break;}}}
            }
            if(!uuid.matches("[a-f0-9]{32}"))return null;Properties p=new Properties();try(InputStream in=Files.newInputStream(cache.resolve(uuid+".properties"))){p.load(in);}
            BufferedImage skin=decode(Files.readAllBytes(cache.resolve(uuid+"-skin.png")),false),cape=null;
            if(Boolean.parseBoolean(p.getProperty("cape")))cape=decode(Files.readAllBytes(cache.resolve(uuid+"-cape.png")),true);
            return new Profile(uuid,p.getProperty("name","Player"),skin,cape,Boolean.parseBoolean(p.getProperty("slim")));
        }catch(IOException|RuntimeException ignored){return null;}
    }
    private BufferedImage texture(String url,boolean cape)throws IOException{return decode(source.read(textureUrl(url),2*1024*1024),cape);}
    static String textureUrl(String url)throws IOException{
        try{URI uri=URI.create(url);if(!Set.of("http","https").contains(uri.getScheme())||!"textures.minecraft.net".equalsIgnoreCase(uri.getHost())||uri.getRawUserInfo()!=null||uri.getPort()!=-1||!uri.getPath().matches("/texture/[a-fA-F0-9]+"))throw new IllegalArgumentException();return "https://textures.minecraft.net"+uri.getPath();}catch(RuntimeException invalid){throw new IOException("官方皮肤材质地址无效");}
    }
    static BufferedImage decode(byte[] bytes,boolean cape)throws IOException{
        if(bytes.length>2*1024*1024)throw new IOException("玩家材质图片过大");
        try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IOException("无法读取玩家 PNG 材质");var reader=readers.next();try{
            reader.setInput(input);int w=reader.getWidth(0),h=reader.getHeight(0);if(!reader.getFormatName().equalsIgnoreCase("png")||w<64||w>1024||w%64!=0||(!cape&&h!=w&&h*2!=w)||(cape&&h*2!=w))throw new IOException("玩家材质尺寸无效");
            BufferedImage raw=reader.read(0),image=new BufferedImage(64,cape||h*2==w?32:64,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);g.drawImage(raw,0,0,image.getWidth(),image.getHeight(),null);g.dispose();
            if(!cape){
                // Old 64x32 skins often fill unused hat pixels with opaque black.
                if(image.getHeight()==32){boolean opaque=true;for(int y=0;y<16;y++)for(int x=32;x<64;x++)if((image.getRGB(x,y)>>>24)<128)opaque=false;if(opaque)for(int y=0;y<16;y++)for(int x=32;x<64;x++)image.setRGB(x,y,image.getRGB(x,y)&0xffffff);}
                for(Rectangle r:java.util.List.of(new Rectangle(0,0,32,16),new Rectangle(0,16,64,16)))for(int y=r.y;y<r.y+r.height;y++)for(int x=r.x;x<r.x+r.width;x++)image.setRGB(x,y,image.getRGB(x,y)|0xff000000);
            }
            return image;
        }finally{reader.dispose();}}
    }
    private void save(Profile profile)throws IOException{
        Files.createDirectories(cache);writePng(cache.resolve(profile.uuid()+"-skin.png"),profile.skin());if(profile.cape()!=null)writePng(cache.resolve(profile.uuid()+"-cape.png"),profile.cape());else Files.deleteIfExists(cache.resolve(profile.uuid()+"-cape.png"));
        Properties p=new Properties();p.setProperty("uuid",profile.uuid());p.setProperty("name",profile.name());p.setProperty("slim",Boolean.toString(profile.slim()));p.setProperty("cape",Boolean.toString(profile.cape()!=null));Path temp=Files.createTempFile(cache,"profile-",".tmp");try{try(OutputStream out=Files.newOutputStream(temp)){p.store(out,"Public player textures");}Files.move(temp,cache.resolve(profile.uuid()+".properties"),StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}
    }
    private static void writePng(Path file,BufferedImage image)throws IOException{Path temp=Files.createTempFile(file.getParent(),"texture-",".tmp");try{ImageIO.write(image,"png",temp.toFile());Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}}
    private static Map<String,Object> json(byte[] bytes)throws IOException{try{return Json.parseObject(new String(bytes,StandardCharsets.UTF_8));}catch(RuntimeException invalid){throw new IOException("无法读取玩家资料",invalid);}}
    private static byte[] download(String url,int max)throws IOException{
        HttpURLConnection connection=(HttpURLConnection)URI.create(url).toURL().openConnection();connection.setConnectTimeout(8000);connection.setReadTimeout(8000);connection.setInstanceFollowRedirects(false);connection.setRequestProperty("User-Agent","Open-MXaddon-Launcher/"+ApplicationVersion.CURRENT);
        try{int code=connection.getResponseCode();if(code!=200)throw new IOException(code==404||code==204?"没有找到这个正版玩家":code==429?"获取过于频繁，请稍后重试":"玩家材质获取失败（"+code+"）");try(InputStream in=connection.getInputStream()){byte[] bytes=in.readNBytes(max+1);if(bytes.length>max)throw new IOException("玩家资料超出大小限制");return bytes;}}finally{connection.disconnect();}
    }
    static BufferedImage defaultSkin(){return DefaultSkin.IMAGE;}
    private static final class DefaultSkin {
        private static final BufferedImage IMAGE=load();
        private static BufferedImage load(){
            try(InputStream input=PlayerTextures.class.getResourceAsStream("/player/steve.png")){
                if(input==null)throw new IOException("Missing bundled original Steve skin");
                return decode(input.readAllBytes(),false);
            }catch(IOException missing){throw new IllegalStateException("无法读取内置原版 Steve 皮肤",missing);}
        }
    }
}

package dev.mx.launcher;

import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

/** Local-only wallpaper library. Imported media remains available if its original is moved. */
final class WallpaperLibrary {
    static final Set<String> FORMATS=Set.of("jpg","jpeg","png","webp","gif","mp4","webm");
    record Item(String id,String name,String extension,Path file,Path thumbnail){
        boolean animated(){return Set.of("gif","mp4","webm").contains(extension);}
        String title(){int dot=name.lastIndexOf('.');return dot>0?name.substring(0,dot):name;}
    }
    private final Path root;
    private final List<Item> items=new ArrayList<>();
    private static final ExecutorService IO=Executors.newCachedThreadPool(r->{Thread t=new Thread(r,"mx-wallpaper-probe");t.setDaemon(true);return t;});
    WallpaperLibrary(Path home){
        root=home.resolve("wallpapers").toAbsolutePath().normalize();
        try {
            Object value=Json.parseObject(Files.readString(root.resolve("library.json"))).get("items");
            if(value instanceof List<?> list)for(Object entry:list)if(entry instanceof Map<?,?> m){
                String id=String.valueOf(m.get("id")),ext=String.valueOf(m.get("extension")),name=String.valueOf(m.get("name"));
                if(!id.matches("[a-f0-9]{32}")||!FORMATS.contains(ext))continue;
                Path file=root.resolve(id+"."+ext),thumb=root.resolve(id+".thumb.png");
                if(Files.isRegularFile(file)&&Files.isRegularFile(thumb))items.add(new Item(id,name,ext,file,thumb));
            }
        }catch(Exception ignored){}
    }
    synchronized List<Item> items(){return List.copyOf(items);}
    synchronized Item find(String id){return items.stream().filter(i->i.id().equals(id)).findFirst().orElse(null);}
    Item importFile(Path source) throws Exception {
        if(!Files.isRegularFile(source))throw new IOException("请选择本地图片或视频文件");
        String name=source.getFileName().toString();int dot=name.lastIndexOf('.');String ext=dot<0?"":name.substring(dot+1).toLowerCase(Locale.ROOT);
        if(!FORMATS.contains(ext))throw new IOException("支持 JPG、PNG、WebP、GIF、MP4 和 WebM");
        if(Files.size(source)>1024L*1024*1024)throw new IOException("单张壁纸请小于 1 GB");
        Files.createDirectories(root);String id=UUID.randomUUID().toString().replace("-","");
        Path file=root.resolve(id+"."+ext),thumbnail=root.resolve(id+".thumb.png");
        try {
            Files.copy(source,file);
            BufferedImage image=probe(file);
            if(image==null)throw new IOException("无法解码这个文件，请选择有效的图片或视频");
            ImageIO.write(scale(image,480,270),"png",thumbnail.toFile());
            Item item=new Item(id,name,ext,file,thumbnail);
            synchronized(this){items.add(item);try{save();}catch(Exception e){items.remove(item);throw e;}}
            return item;
        }catch(Exception e){Files.deleteIfExists(file);Files.deleteIfExists(thumbnail);throw e;}
    }
    private void save() throws IOException {
        Map<String,Object> data=Json.map();List<Object> rows=new ArrayList<>();
        for(Item item:items){Map<String,Object> row=Json.map();row.put("id",item.id());row.put("name",item.name());row.put("extension",item.extension());rows.add(row);}data.put("items",rows);
        Path temp=Files.createTempFile(root,"library-",".tmp");
        try{Files.writeString(temp,Json.write(data));Files.move(temp,root.resolve("library.json"),StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}
    }
    static Path decoder(){
        String override=System.getProperty("mx.ffmpeg");if(override!=null&&!override.isBlank())return Path.of(override);
        try{return Path.of(WallpaperLibrary.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent().resolve("Media/ffmpeg.exe");}
        catch(Exception e){return LauncherPaths.home().resolve("Media/ffmpeg.exe");}
    }
    static List<String> command(Path source){
        return new ArrayList<>(List.of(decoder().toString(),"-nostdin","-hide_banner","-loglevel","error","-threads","2","-filter_threads","1","-protocol_whitelist","file,pipe","-i",source.toAbsolutePath().toString()));
    }
    static BufferedImage probe(Path file) throws Exception {
        String name=file.getFileName().toString().toLowerCase(Locale.ROOT);
        if(name.endsWith(".jpg")||name.endsWith(".jpeg")||name.endsWith(".png")){
            try(ImageInputStream in=ImageIO.createImageInputStream(file.toFile())){
                Iterator<ImageReader> readers=ImageIO.getImageReaders(in);
                if(readers.hasNext()){
                    ImageReader reader=readers.next();
                    try{reader.setInput(in);int w=reader.getWidth(0),h=reader.getHeight(0);if((long)w*h>80_000_000)throw new IOException("图片尺寸过大，请先缩小后导入");
                        ImageReadParam param=reader.getDefaultReadParam();int sample=Math.max(1,Math.max(w/1600,h/1000));param.setSourceSubsampling(sample,sample,0,0);return reader.read(0,param);
                    }finally{reader.dispose();}
                }
            }
        }
        if(!Files.isRegularFile(decoder()))throw new IOException("媒体组件缺失，请使用完整的启动器分发包");
        List<String> args=command(file);args.addAll(List.of("-an","-vf","scale=1280:720:force_original_aspect_ratio=decrease","-frames:v","1","-f","image2pipe","-c:v","png","pipe:1"));
        Process process=new ProcessBuilder(args).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        Future<BufferedImage> future=IO.submit(()->ImageIO.read(process.getInputStream()));
        try{BufferedImage image=future.get(25,TimeUnit.SECONDS);if(!process.waitFor(5,TimeUnit.SECONDS)||process.exitValue()!=0||image==null)throw new IOException("无法解码壁纸文件");return image;}
        catch(TimeoutException e){throw new IOException("壁纸读取超时，请换一个文件",e);}
        finally{future.cancel(true);process.destroyForcibly();try{process.getInputStream().close();}catch(IOException ignored){}}
    }
    static BufferedImage scale(BufferedImage source,int maxWidth,int maxHeight){
        double ratio=Math.min(1,Math.min(maxWidth/(double)source.getWidth(),maxHeight/(double)source.getHeight()));
        int w=Math.max(1,(int)(source.getWidth()*ratio)),h=Math.max(1,(int)(source.getHeight()*ratio));
        BufferedImage out=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);Graphics2D g=out.createGraphics();UiTheme.quality(g);g.setColor(new Color(0x172331));g.fillRect(0,0,w,h);g.drawImage(source,0,0,w,h,null);g.dispose();return out;
    }
}

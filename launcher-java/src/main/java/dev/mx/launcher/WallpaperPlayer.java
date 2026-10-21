package dev.mx.launcher;

import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import javax.swing.SwingUtilities;
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import java.util.regex.*;

/** Bounded-frame decoder. Media decoding never runs on Swing's event dispatch thread. */
final class WallpaperPlayer implements AutoCloseable {
    private static final ExecutorService WORKERS=Executors.newCachedThreadPool(r->{Thread t=new Thread(r,"mx-wallpaper-media");t.setDaemon(true);return t;});
    private static final class Playback {
        volatile boolean cancelled;volatile Process video,audio;volatile SourceDataLine line;
        void cancel(){cancelled=true;if(video!=null)video.destroyForcibly();if(audio!=null)audio.destroyForcibly();if(line!=null){line.stop();line.close();}}
    }
    private volatile BufferedImage frame=defaultImage();
    private volatile String error="";
    private volatile int publishedFrames;
    private final AtomicInteger generation=new AtomicInteger();
    private final AtomicBoolean repaintQueued=new AtomicBoolean();
    private Playback playback;
    private WallpaperLibrary.Item item;
    private Appearance settings=new Appearance();
    private boolean paused,closed;
    private Runnable changed=()->{};
    private Consumer<String> onError=s->{};
    void onChange(Runnable listener){changed=listener;}
    void onError(Consumer<String> listener){onError=listener;}
    BufferedImage frame(){return frame;}
    String error(){return error;}
    int frameCount(){return publishedFrames;}
    boolean isPaused(){return paused;}
    synchronized boolean decoderAlive(){return playback!=null&&playback.video!=null&&playback.video.isAlive();}
    synchronized void select(WallpaperLibrary.Item next,Appearance options){
        boolean changedItem=item==null?next!=null:next==null||!item.id().equals(next.id());
        if(changedItem&&next!=null)try{BufferedImage poster=ImageIO.read(next.thumbnail().toFile());if(poster!=null)frame=poster;}catch(IOException ignored){}
        item=next;settings=options.copy();error="";restart();
        if(changedItem)SwingUtilities.invokeLater(changed);
    }
    synchronized void setPaused(boolean value){if(paused==value)return;paused=value;restart();}
    private synchronized void restart(){
        int ticket=generation.incrementAndGet();if(playback!=null)playback.cancel();playback=null;
        if(closed)return;
        WallpaperLibrary.Item selected=item;Appearance opts=settings.copy();
        if(selected==null){publish(defaultImage(),ticket);return;}
        if(paused)return;
        Playback run=new Playback();playback=run;
        WORKERS.execute(()->{
            try {
                BufferedImage poster=WallpaperLibrary.probe(selected.file());
                if(run.cancelled||generation.get()!=ticket)return;
                publish(poster,ticket);
                if(!selected.animated())return;
                int width=Math.max(2,(poster.getWidth()/2)*2),height=Math.max(2,(poster.getHeight()/2)*2);
                List<String> args=WallpaperLibrary.command(selected.file());
                // Input loop belongs before -i. GIF's own loop metadata must not defeat the user's switch.
                int input=args.indexOf("-i");if(selected.extension().equals("gif")){args.addAll(input,List.of("-ignore_loop","1"));input=args.indexOf("-i");}
                if(opts.loop)args.addAll(input,List.of("-stream_loop","-1"));
                // Retain every source timestamp. Pacing by packet read rate alone can burst buffered frames.
                args.set(args.indexOf("-loglevel")+1,"info");
                args.addAll(List.of("-nostats","-an","-sn","-dn","-vf","scale="+width+":"+height+",showinfo=checksum=0","-fps_mode","passthrough","-f","rawvideo","-pix_fmt","bgr0","pipe:1"));
                Process video=new ProcessBuilder(args).start();run.video=video;
                if(run.cancelled){video.destroyForcibly();return;}
                BlockingQueue<Double> timestamps=new ArrayBlockingQueue<>(16);
                Future<?> timing=WORKERS.submit(()->readTimestamps(video,timestamps,run));
                if(!opts.muted&&!selected.extension().equals("gif"))WORKERS.execute(()->audio(selected,opts,run));
                int bytes=width*height*4;byte[] buffer=new byte[bytes];java.nio.IntBuffer packed=java.nio.ByteBuffer.wrap(buffer).order(java.nio.ByteOrder.LITTLE_ENDIAN).asIntBuffer();double origin=Double.NaN,lastPts=Double.NaN;long start=0;
                try(InputStream in=video.getInputStream()){
                    while(!run.cancelled&&generation.get()==ticket){
                        BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
                        int offset=0;while(offset<bytes){int n=in.read(buffer,offset,bytes-offset);if(n<0)break;offset+=n;}if(offset!=bytes)break;
                        // Packed RGB avoids the slow BGR-to-ARGB conversion on every fractional-DPI Swing repaint.
                        packed.rewind();packed.get(((DataBufferInt)image.getRaster().getDataBuffer()).getData());
                        Double pts=timestamps.poll(5,TimeUnit.SECONDS);
                        if(pts==null)throw new IOException("无法读取视频帧时间，请更换壁纸文件");
                        if(Double.isNaN(origin)||pts<lastPts-.1){origin=pts;start=System.nanoTime();}
                        lastPts=pts;
                        long wait=start+(long)((pts-origin)*1_000_000_000L)-System.nanoTime();
                        long due=System.nanoTime()+wait;
                        while(wait>0&&!run.cancelled&&generation.get()==ticket){TimeUnit.NANOSECONDS.sleep(Math.min(wait,100_000_000L));wait=due-System.nanoTime();}
                        if(!run.cancelled)publish(image,ticket);
                    }
                }finally{timing.cancel(true);}
                if(!run.cancelled&&video.waitFor()!=0)throw new IOException("动态壁纸无法播放，请尝试另一个文件");
            }catch(Exception e){if(!run.cancelled&&generation.get()==ticket){error=e.getMessage()==null?"壁纸播放失败":e.getMessage();SwingUtilities.invokeLater(()->{if(generation.get()==ticket)onError.accept(error);});}}
            finally{run.cancel();}
        });
    }
    private static void readTimestamps(Process video,BlockingQueue<Double> timestamps,Playback run){
        Pattern pattern=Pattern.compile("pts_time:([-+0-9.eE]+)");
        try(BufferedReader reader=video.errorReader()){
            String line;
            while(!run.cancelled&&(line=reader.readLine())!=null){
                if(!line.contains("Parsed_showinfo"))continue;
                Matcher match=pattern.matcher(line);
                if(match.find()){
                    double pts=Double.parseDouble(match.group(1));
                    if(!Double.isFinite(pts))continue;
                    while(!run.cancelled&&!timestamps.offer(pts,100,TimeUnit.MILLISECONDS)){}
                }
            }
        }catch(Exception ignored){/* Main reader reports missing timestamps and handles cancellation. */}
    }
    private void audio(WallpaperLibrary.Item item,Appearance opts,Playback run){
        try{
            List<String> args=WallpaperLibrary.command(item.file());int input=args.indexOf("-i");if(opts.loop)args.addAll(input,List.of("-stream_loop","-1"));args.add(args.indexOf("-i"),"-re");
            args.addAll(List.of("-vn","-f","s16le","-ar","44100","-ac","2","pipe:1"));
            Process process=new ProcessBuilder(args).redirectError(ProcessBuilder.Redirect.DISCARD).start();run.audio=process;if(run.cancelled){process.destroyForcibly();return;}
            AudioFormat format=new AudioFormat(44100,16,2,true,false);SourceDataLine line=AudioSystem.getSourceDataLine(format);run.line=line;
            line.open(format,17640);if(run.cancelled)return;line.start();
            try(InputStream in=process.getInputStream()){byte[] buffer=new byte[8192];int n;while(!run.cancelled&&(n=in.read(buffer))>=0){int offset=0;while(offset<n&&!run.cancelled)offset+=line.write(buffer,offset,n-offset);}}
        }catch(Exception ignored){/* A silent clip or unavailable sound device never blocks wallpaper/video. */}
        finally{if(run.line!=null){run.line.stop();run.line.close();}if(run.audio!=null)run.audio.destroyForcibly();}
    }
    private void publish(BufferedImage value,int ticket){
        if(value==null||generation.get()!=ticket)return;frame=value;publishedFrames++;
        if(repaintQueued.compareAndSet(false,true))SwingUtilities.invokeLater(()->{repaintQueued.set(false);changed.run();});
    }
    @Override public synchronized void close(){closed=true;generation.incrementAndGet();if(playback!=null)playback.cancel();}
    private static final class DefaultImage {static final BufferedImage IMAGE=loadDefaultImage();}
    private static BufferedImage defaultImage(){return DefaultImage.IMAGE;}
    private static BufferedImage loadDefaultImage(){
        try(InputStream in=WallpaperPlayer.class.getResourceAsStream("/wallpapers/default-landscape.png")){if(in!=null){BufferedImage image=ImageIO.read(in);if(image!=null)return image;}}
        catch(IOException ignored){}
        BufferedImage image=new BufferedImage(1280,720,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();
        g.setPaint(new GradientPaint(0,0,new Color(0x779CC0),1280,720,new Color(0x284F69)));g.fillRect(0,0,1280,720);g.dispose();return image;
    }
}

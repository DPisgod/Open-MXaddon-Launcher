package dev.mx.launcher;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.OutputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/** Renders a short offline demonstration from production views, using disposable data. */
public final class MotionPreview {
    public static void main(String[] args)throws Exception{
        LauncherRuntime.configureRendering();UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());Path home=Files.createTempDirectory("oml-motion-preview-"),out=Path.of(args[0]).toAbsolutePath();Files.createDirectories(out.getParent());LauncherPaths.override(home);Appearance appearance=new Appearance();appearance.theme="cyber";appearance.save(home);
        LauncherFrame[] frame={null};SettingsDialog[] dialog={null};ContentBrowserPanel[] browser={null};CountDownLatch pending=new CountDownLatch(1);
        Process encoder=new ProcessBuilder(WallpaperLibrary.decoder().toString(),"-hide_banner","-loglevel","error","-y","-f","rawvideo","-pixel_format","bgr24","-video_size","1140x810","-framerate","60","-i","pipe:0","-an","-c:v","libopenh264","-b:v","4M","-pix_fmt","yuv420p","-movflags","+faststart",out.toString()).redirectError(out.resolveSibling("encoder.log").toFile()).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        OutputStream stream=encoder.getOutputStream();ExecutorService writer=Executors.newSingleThreadExecutor();
        final int count=540;UiMotion.FrameTimer[] recorder={null};int[] index={0};long[] started={0};CountDownLatch finished=new CountDownLatch(1);
        try{
            SwingUtilities.invokeAndWait(()->{
                frame[0]=new LauncherFrame(false);frame[0].setLocation(70,40);frame[0].setVisible(true);started[0]=System.nanoTime();
                recorder[0]=new UiMotion.FrameTimer(16,e->{try{
                    int n=index[0]++;
                    if(n==60)frame[0].showStyles();
                    if(n==120)((JButton)AccountUiBehaviorTest.findNamed(frame[0].stylePanel(),"wallpaperMenu")).doClick();
                    if(n==180){frame[0].openDownloads();frame[0].downloadCenter().setGames(List.of(new DownloadCatalog.Game("1.21.1","release","https://fixture/game","","2024-08-08T12:00:00Z"),new DownloadCatalog.Game("26.4-snapshot-5","snapshot","https://fixture/preview","","2026-10-20T12:00:00Z")));}
                    if(n==240){AccountBehaviorTest.findButton(frame[0].downloadCenter(),"Mod").doClick();browser[0]=(ContentBrowserPanel)MotionAndControlsTest.field(frame[0].downloadCenter(),"resources");MotionAndControlsTest.begin(browser[0],pending);}
                    if(n==350)pending.countDown();
                    if(n==390){dialog[0]=new SettingsDialog(frame[0],home,LauncherPaths.gameRoot());dialog[0].setModalityType(Dialog.ModalityType.MODELESS);dialog[0].setVisible(true);}
                    if(n==475){dialog[0].dispose();dialog[0]=null;AccountBehaviorTest.findButton(frame[0],"主页").doClick();}
                    Container content=frame[0].getContentPane();BufferedImage image=new BufferedImage(1140,810,BufferedImage.TYPE_3BYTE_BGR);Graphics2D g=image.createGraphics();content.paint(g);
                    if(dialog[0]!=null&&dialog[0].isShowing()){g.setColor(new Color(0,0,0,90));g.fillRect(0,0,1140,810);Container panel=dialog[0].getContentPane();Graphics2D modal=(Graphics2D)g.create();modal.translate((1140-panel.getWidth())/2,(810-panel.getHeight())/2);panel.paint(modal);modal.dispose();}
                    g.setColor(new Color(7,14,29,225));g.fillRoundRect(18,760,610,34,12,12);g.setColor(Color.WHITE);g.setFont(UiTheme.font(12,false));String caption=n<60?"动画预览 · 窗口内容淡入":n<120?"个性化 · 页面淡入与轻移":n<180?"壁纸设置 · 子页面过渡":n<240?"下载中心 · 页面过渡":n<350?"刷新状态 · 筛选框保持原配色 / 进度条连续动画":n<390?"刷新完成 · 恢复操作":n<475?"设置窗口 · 内容淡入与上移":"返回主页 · 过渡结束";g.drawString(caption,32,782);g.dispose();byte[] bytes=((DataBufferByte)image.getRaster().getDataBuffer()).getData();writer.submit(()->{try{stream.write(bytes);}catch(Exception failure){throw new RuntimeException(failure);}});
                    if(index[0]>=count){recorder[0].stop();finished.countDown();}
                }catch(Exception failure){failure.printStackTrace();recorder[0].stop();finished.countDown();}});recorder[0].start();
            });
            finished.await();writer.shutdown();if(!writer.awaitTermination(60,TimeUnit.SECONDS))throw new IllegalStateException("Preview encoder timed out");stream.close();if(encoder.waitFor()!=0)throw new IllegalStateException("Preview encoding failed");System.out.println("PRODUCTION_MOTION_PREVIEW_FRAMES="+index[0]+" ELAPSED_MS="+(System.nanoTime()-started[0])/1_000_000);
        }finally{writer.shutdownNow();stream.close();encoder.destroy();pending.countDown();SwingUtilities.invokeAndWait(()->{if(recorder[0]!=null)recorder[0].stop();if(dialog[0]!=null)dialog[0].dispose();if(frame[0]!=null)frame[0].dispose();});}System.exit(0);
    }
}

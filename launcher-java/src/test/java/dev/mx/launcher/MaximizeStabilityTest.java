package dev.mx.launcher;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** Production work-area sizing and GDI rendering, with disposable settings and no online auth. */
public final class MaximizeStabilityTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        LauncherRuntime.configureRendering();
        check("false".equals(System.getProperty("sun.java2d.d3d"))&&"false".equals(System.getProperty("sun.java2d.opengl")),"unsafe launcher rendering options");
        List<Throwable> errors=new CopyOnWriteArrayList<>();Thread.setDefaultUncaughtExceptionHandler((thread,error)->{error.printStackTrace();errors.add(error);});
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());Path home=Files.createTempDirectory("mx-maximize-safe-"),out=Path.of(args[1]);Files.createDirectories(out);LauncherPaths.override(home);
        LauncherRuntime.diagnostics(home);
        Thread.UncaughtExceptionHandler logger=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{errors.add(error);logger.uncaughtException(thread,error);});
        Path source=Path.of(args[0]).resolve("motion-60.mp4");
        if(args.length>2){Path userHome=Path.of(args[2]);WallpaperLibrary.Item selected=new WallpaperLibrary(userHome).find(Appearance.load(userHome).wallpaper);check(selected!=null,"user wallpaper not found");source=selected.file();}
        WallpaperLibrary library=new WallpaperLibrary(home);WallpaperLibrary.Item video=library.importFile(source);int transitions=0;
        for(boolean animated:new boolean[]{false,true})for(String theme:new String[]{"light","dark"}){
            Appearance a=new Appearance();a.theme=theme;a.uiTransparency=65;a.wallpaper=animated?video.id():"";a.save(home);
            LauncherFrame[] frame=new LauncherFrame[1];AtomicInteger pulses=new AtomicInteger();Timer[] heartbeat=new Timer[1];Rectangle[] normal=new Rectangle[1];
            try{
                SwingUtilities.invokeAndWait(()->{frame[0]=new LauncherFrame(false);frame[0].setVisible(true);check(!frame[0].getGraphicsConfiguration().getClass().getName().contains("D3D"),"Direct3D graphics configuration active");normal[0]=frame[0].getBounds();heartbeat[0]=new Timer(20,e->pulses.incrementAndGet());heartbeat[0].start();});Thread.sleep(200);
                int before=frame[0].wallpaperPlayer().frameCount();
                for(int i=0;i<6;i++){
                    SwingUtilities.invokeAndWait(()->WindowChromeTest.button(frame[0],"最大化 / 还原窗口").doClick(0));Thread.sleep(100);
                    SwingUtilities.invokeAndWait(()->{
                        check(frame[0].isDisplayable()&&WindowChrome.isMaximized(frame[0]),"maximized window closed");
                        check((frame[0].getExtendedState()&Frame.MAXIMIZED_BOTH)==0,"native maximize path used");
                        check(frame[0].getBounds().equals(WindowChrome.workArea(frame[0].getGraphicsConfiguration())),"work area mismatch / cropped UI");
                        check(!frame[0].getGlassPane().contains(1,100),"maximized edge resizing still active");
                        HWND hwnd=new HWND(Native.getComponentPointer(frame[0]));check((User32.INSTANCE.GetWindowLong(hwnd,-16)&0x00010000)==0,"native maximize box still enabled");
                    });
                    if(i==0){BufferedImage image=com.sun.jna.platform.win32.GDI32Util.getScreenshot(new HWND(Native.getComponentPointer(frame[0])));ImageIO.write(image,"png",out.resolve((animated?"video-":"static-")+theme+"-maximized.png").toFile());}
                    SwingUtilities.invokeAndWait(()->WindowChromeTest.button(frame[0],"最大化 / 还原窗口").doClick(0));Thread.sleep(100);
                    SwingUtilities.invokeAndWait(()->check(!WindowChrome.isMaximized(frame[0])&&frame[0].getBounds().equals(normal[0]),"original position/size not restored"));transitions+=2;
                }
                SwingUtilities.invokeAndWait(()->{WindowChrome.toggleMaximize(frame[0]);WindowChromeTest.button(frame[0],"最小化窗口").doClick(0);});Thread.sleep(100);
                SwingUtilities.invokeAndWait(()->frame[0].setExtendedState(Frame.NORMAL));Thread.sleep(100);
                SwingUtilities.invokeAndWait(()->{check(WindowChrome.isMaximized(frame[0])&&frame[0].getBounds().equals(WindowChrome.workArea(frame[0].getGraphicsConfiguration())),"minimize/restore lost work area");WindowChrome.toggleMaximize(frame[0]);});
                check(pulses.get()>10,"event dispatch heartbeat stalled");if(animated)check(frame[0].wallpaperPlayer().frameCount()>before+10,"video stopped after maximize");
                WindowCaptureTest.verify(frame[0],out,(animated?"video-":"static-")+theme+"-restored",true);
                System.out.println("WORK_AREA_"+(animated?"VIDEO_":"STATIC_")+theme+"_HEARTBEAT_"+pulses.get()+"_OK");
            }finally{SwingUtilities.invokeAndWait(()->{if(heartbeat[0]!=null)heartbeat[0].stop();if(frame[0]!=null)frame[0].dispose();});}
        }
        check(errors.isEmpty(),"unhandled UI errors: "+errors);String log=Files.readString(home.resolve(".min/logs/launcher.log"));check(!log.contains("uncaught on "),"unhandled errors were logged");check(log.contains("d3d=false")&&log.contains("window=work-area")&&log.contains("window=restored"),"diagnostic log missing rendering/sizing records");System.out.println("GDI_SAFE_MAXIMIZE_RESTORE_"+transitions+"_TRANSITIONS_VIDEO_THEMES_MINIMIZE_CAPTURE_OK");System.exit(0);
    }
}

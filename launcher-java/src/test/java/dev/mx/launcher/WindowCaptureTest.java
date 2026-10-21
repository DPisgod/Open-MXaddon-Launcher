package dev.mx.launcher;

import com.sun.jna.*;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.WinDef.*;
import com.sun.jna.platform.win32.WinGDI.*;
import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;

/** Captures only this test's own windows through the classic Win32 screenshot APIs. */
public final class WindowCaptureTest {
    interface Attributes extends com.sun.jna.win32.StdCallLibrary{
        Attributes USER=Native.load("user32",Attributes.class),DWM=Native.load("dwmapi",Attributes.class);
        boolean GetWindowDisplayAffinity(HWND hwnd,IntByReference affinity);
        int DwmGetWindowAttribute(HWND hwnd,int attribute,int[] value,int size);
    }
    static void check(boolean yes,String message){if(!yes)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());LauncherPaths.override(Files.createTempDirectory("mx-capture-"));Path out=Path.of(args[0]);Files.createDirectories(out);
        LauncherFrame[] frame=new LauncherFrame[1];JDialog[] dialog=new JDialog[1];
        SwingUtilities.invokeAndWait(()->{frame[0]=new LauncherFrame(false);frame[0].setVisible(true);});Thread.sleep(350);
        try{
            for(String theme:new String[]{"light","dark"}){
                SwingUtilities.invokeAndWait(()->frame[0].previewTheme(theme));Thread.sleep(150);verify(frame[0],out,"home-"+theme,true);
            }
            SwingUtilities.invokeAndWait(()->{dialog[0]=new AccountDialog(frame[0],LauncherPaths.home(),null);dialog[0].setModalityType(Dialog.ModalityType.MODELESS);dialog[0].setVisible(true);});Thread.sleep(200);verify(dialog[0],out,"account",false);
            System.out.println("OPAQUE_NAMED_ENUMERABLE_NO_CAPTURE_EXCLUSION_GDI_PRINTWINDOW_DWM_OK");
            if(args.length>1&&args[1].equals("keep")){SwingUtilities.invokeAndWait(()->dialog[0].dispose());System.out.println("CAPTURE_PREVIEW_READY");return;}
        }finally{if(args.length<2||!args[1].equals("keep"))SwingUtilities.invokeAndWait(()->{if(dialog[0]!=null)dialog[0].dispose();frame[0].dispose();});}
        System.exit(0);
    }
    static void verify(Window window,Path out,String name,boolean scenery)throws Exception{
        HWND hwnd=new HWND(Native.getComponentPointer(window));int style=User32.INSTANCE.GetWindowLong(hwnd,-20);
        check((style&0x00080000)==0,"WS_EX_LAYERED makes QQ skip the window");check((style&0x80)==0,"tool-window style excludes normal window selection");
        int normalStyle=User32.INSTANCE.GetWindowLong(hwnd,-16);check((normalStyle&0x00C00000)==0x00C00000,"normal native frame metadata missing");
        RECT client=new RECT(),outer=new RECT();User32.INSTANCE.GetClientRect(hwnd,client);User32.INSTANCE.GetWindowRect(hwnd,outer);check(client.right==outer.right-outer.left&&client.bottom==outer.bottom-outer.top,"native title bar consumes the custom UI");
        char[] title=new char[256];check(User32.INSTANCE.GetWindowText(hwnd,title,title.length)>0,"window has no discoverable name");
        IntByReference affinity=new IntByReference();check(Attributes.USER.GetWindowDisplayAffinity(hwnd,affinity)&&affinity.getValue()==0,"capture exclusion is enabled");
        int[] corner={0};if(Attributes.DWM.DwmGetWindowAttribute(hwnd,33,corner,4)==0){check(corner[0]==2,"DWM round preference missing");check(window.getShape()==null,"window region disables DWM smoothing");}
        BufferedImage gdi=GDI32Util.getScreenshot(hwnd),printed=print(hwnd);
        for(var entry:Map.of("gdi",gdi,"print",printed).entrySet()){
            BufferedImage image=entry.getValue();Set<Integer> colors=new HashSet<>();int colored=0,total=0;
            for(int y=20;y<image.getHeight()-20;y+=7)for(int x=20;x<image.getWidth()-20;x+=7){int rgb=image.getRGB(x,y)&0xffffff;colors.add(rgb);if(rgb!=0&&rgb!=0xffffff)colored++;total++;}
            check(colors.size()>(scenery?128:15)&&colored>total*.7,"native screenshot is blank: "+entry.getKey());ImageIO.write(image,"png",out.resolve(name+"-"+entry.getKey()+".png").toFile());
        }
    }
    static BufferedImage print(HWND hwnd){
        RECT rect=new RECT();User32.INSTANCE.GetWindowRect(hwnd,rect);int w=rect.right-rect.left,h=rect.bottom-rect.top;HDC source=User32.INSTANCE.GetDC(hwnd),memory=GDI32.INSTANCE.CreateCompatibleDC(source);HBITMAP bitmap=GDI32.INSTANCE.CreateCompatibleBitmap(source,w,h);com.sun.jna.platform.win32.WinNT.HANDLE previous=GDI32.INSTANCE.SelectObject(memory,bitmap);
        try{check(User32.INSTANCE.PrintWindow(hwnd,memory,2),"PrintWindow failed");GDI32.INSTANCE.SelectObject(memory,previous);previous=null;
            BITMAPINFO info=new BITMAPINFO();info.bmiHeader.biWidth=w;info.bmiHeader.biHeight=-h;info.bmiHeader.biPlanes=1;info.bmiHeader.biBitCount=32;info.bmiHeader.biCompression=0;
            Memory bytes=new Memory((long)w*h*4);check(GDI32.INSTANCE.GetDIBits(source,bitmap,0,h,bytes,info,0)==h,"bitmap read failed");BufferedImage image=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);image.setRGB(0,0,w,h,bytes.getIntArray(0,w*h),0,w);bytes.close();return image;
        }finally{if(previous!=null)GDI32.INSTANCE.SelectObject(memory,previous);GDI32.INSTANCE.DeleteObject(bitmap);GDI32.INSTANCE.DeleteDC(memory);User32.INSTANCE.ReleaseDC(hwnd,source);}
    }
}

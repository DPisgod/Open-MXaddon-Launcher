package dev.mx.launcher;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;

public final class WindowChromeTest {
    static void check(boolean yes,String message){if(!yes)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        LauncherRuntime.configureRendering();
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());LauncherPaths.override(Files.createTempDirectory("mx-window-chrome-"));LauncherFrame[] frame=new LauncherFrame[1];
        SwingUtilities.invokeAndWait(()->{frame[0]=new LauncherFrame(false);frame[0].setVisible(true);check(frame[0].isUndecorated()&&frame[0].getTitle().equals("Open-MXaddon-Launcher"),"capture name or borderless style missing");
            Container root=frame[0].getContentPane();Component heading=((BorderLayout)root.getLayout()).getLayoutComponent(BorderLayout.NORTH);Rectangle before=frame[0].getBounds();drag(heading,30,20,before.x+30,before.y+20,70,45,before.x+70,before.y+45);check(frame[0].getX()==before.x+40&&frame[0].getY()==before.y+25,"heading drag failed");
            Component glass=frame[0].getGlassPane();before=frame[0].getBounds();drag(glass,before.width-1,100,before.x+before.width-1,before.y+100,before.width+39,100,before.x+before.width+39,before.y+100);check(frame[0].getWidth()==before.width+40,"edge resize failed");
            check(!glass.contains(100,100)&&glass.contains(1,100),"edge overlay blocks content");button(frame[0],"最大化 / 还原窗口").doClick();
        });Thread.sleep(250);
        SwingUtilities.invokeAndWait(()->{check(WindowChrome.isMaximized(frame[0]),"maximize failed");check((frame[0].getExtendedState()&Frame.MAXIMIZED_BOTH)==0,"unsafe native maximize used");check(frame[0].getBounds().equals(WindowChrome.workArea(frame[0].getGraphicsConfiguration())),"maximized window exceeds work area");check(frame[0].getShape()==null,"maximized corners should be square");button(frame[0],"最大化 / 还原窗口").doClick();});Thread.sleep(250);
        SwingUtilities.invokeAndWait(()->{check(frame[0].getExtendedState()==Frame.NORMAL,"restore failed");VisualPolishTest.checkRounded(frame[0]);button(frame[0],"最小化窗口").doClick();});Thread.sleep(150);
        SwingUtilities.invokeAndWait(()->{check((frame[0].getExtendedState()&Frame.ICONIFIED)!=0,"minimize failed");frame[0].setExtendedState(Frame.NORMAL);button(frame[0],"关闭窗口").doClick();check(!frame[0].isDisplayable(),"close failed");
            InstallVersionDialog dialog=new InstallVersionDialog(null,LauncherPaths.gameRoot(),id->{});dialog.addNotify();check(dialog.isUndecorated()&&!dialog.getTitle().isBlank(),"dialog capture name or borderless style missing");button(dialog,"关闭窗口").doClick();check(!dialog.isDisplayable(),"dialog close failed");});
        System.out.println("BORDERLESS_DRAG_RESIZE_MAXIMIZE_RESTORE_MINIMIZE_CLOSE_OK");System.exit(0);
    }
    static JButton button(Container root,String label){JButton result=AccountBehaviorTest.findButton(root,label);check(result!=null,"button missing: "+label);return result;}
    static void drag(Component component,int x,int y,int ax,int ay,int nx,int ny,int nax,int nay){long when=System.currentTimeMillis();component.dispatchEvent(new MouseEvent(component,MouseEvent.MOUSE_PRESSED,when,InputEvent.BUTTON1_DOWN_MASK,x,y,ax,ay,1,false,MouseEvent.BUTTON1));component.dispatchEvent(new MouseEvent(component,MouseEvent.MOUSE_DRAGGED,when+10,InputEvent.BUTTON1_DOWN_MASK,nx,ny,nax,nay,0,false,MouseEvent.NOBUTTON));component.dispatchEvent(new MouseEvent(component,MouseEvent.MOUSE_RELEASED,when+20,0,nx,ny,nax,nay,1,false,MouseEvent.BUTTON1));}
}

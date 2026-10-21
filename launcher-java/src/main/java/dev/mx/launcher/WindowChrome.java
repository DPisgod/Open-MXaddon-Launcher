package dev.mx.launcher;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;

/** Window controls live in the page heading; no native title strip. */
final class WindowChrome {
    private WindowChrome(){}
    static void undecorate(Window window){
        if(window instanceof JFrame frame){frame.setUndecorated(true);if(frame.getTitle().isBlank())frame.setTitle("OML");}
        if(window instanceof JDialog dialog){dialog.setName(dialog.getTitle());dialog.setUndecorated(true);}
        window.setIconImages(ApplicationIcon.images());
        // QQ's smart window selection skips per-pixel layered windows. Keep an ordinary opaque HWND.
        window.setBackground(UiTheme.p().background());
        window.addComponentListener(new ComponentAdapter(){@Override public void componentResized(ComponentEvent e){round(window);}@Override public void componentShown(ComponentEvent e){round(window);}});
        window.addWindowListener(new WindowAdapter(){@Override public void windowOpened(WindowEvent e){round(window);}@Override public void windowClosed(WindowEvent e){if(window instanceof RootPaneContainer root)root.getRootPane().putClientProperty("mxcloud.nativeFrame",null);}});
        window.addHierarchyListener(e->{if((e.getChangeFlags()&HierarchyEvent.DISPLAYABILITY_CHANGED)!=0&&window.isDisplayable())round(window);});
        if(window instanceof Frame frame)frame.addWindowStateListener(e->round(window));
    }
    private static void round(Window window){
        if(window.getWidth()<=0||window.getHeight()<=0||!window.isDisplayable())return;
        boolean maximized=isMaximized(window);
        installNativeFrame(window);
        if(window.getShape()!=null)window.setShape(null);
        if(window instanceof RootPaneContainer root&&Integer.valueOf(maximized?1:2).equals(root.getRootPane().getClientProperty("mxcloud.cornerPreference"))&&Boolean.TRUE.equals(root.getRootPane().getClientProperty("mxcloud.nativeCorners")))return;
        boolean nativeCorners=nativeCorners(window,maximized?1:2);
        if(window instanceof RootPaneContainer root){root.getRootPane().putClientProperty("mxcloud.nativeCorners",nativeCorners);root.getRootPane().putClientProperty("mxcloud.cornerPreference",maximized?1:2);}
        if(!nativeCorners&&!maximized)window.setShape(new RoundRectangle2D.Double(0,0,window.getWidth(),window.getHeight(),24,24));window.repaint();
    }
    /** Keep normal native frame metadata, but let Swing paint the complete client area. */
    private static void installNativeFrame(Window window){
        if(!System.getProperty("os.name","").startsWith("Windows")||!(window instanceof RootPaneContainer root))return;
        if(root.getRootPane().getClientProperty("mxcloud.nativeFrame")!=null)return;
        try{NativeFrame frame=new NativeFrame(window);root.getRootPane().putClientProperty("mxcloud.nativeFrame",frame);frame.install();}
        catch(Throwable unavailable){if(root.getRootPane().getClientProperty("mxcloud.nativeFrame")==null)root.getRootPane().putClientProperty("mxcloud.nativeFrame",Boolean.FALSE);}
    }
    private static final class NativeFrame{
        final com.sun.jna.platform.win32.WinDef.HWND hwnd;
        final com.sun.jna.platform.win32.WinUser.WindowProc callback;
        com.sun.jna.Pointer previous;
        final int style;
        NativeFrame(Window window){
            var user=com.sun.jna.platform.win32.User32.INSTANCE;
            hwnd=new com.sun.jna.platform.win32.WinDef.HWND(com.sun.jna.Native.getComponentPointer(window));
            previous=new com.sun.jna.Pointer(user.GetWindowLongPtr(hwnd,-4).longValue());
            callback=(handle,message,wParam,lParam)->message==0x83?new com.sun.jna.platform.win32.WinDef.LRESULT(0):user.CallWindowProc(previous,handle,message,wParam,lParam);
            // Our own edges and work-area sizing replace native resize/maximize transitions.
            // Keep the normal caption metadata required by QQ, without a native maximize box.
            style=(user.GetWindowLong(hwnd,-16)&~0x00050000)|0x00C00000|0x00080000|(window instanceof Frame?0x00020000:0);
        }
        void install(){
            var user=com.sun.jna.platform.win32.User32.INSTANCE;
            com.sun.jna.Native.setLastError(0);var old=user.SetWindowLongPtr(hwnd,-4,com.sun.jna.CallbackReference.getFunctionPointer(callback));
            if(old==null&&com.sun.jna.Native.getLastError()!=0)throw new IllegalStateException("Cannot install native client frame");if(old!=null)previous=old;
            user.SetWindowLong(hwnd,-16,style);user.SetWindowPos(hwnd,null,0,0,0,0,0x0001|0x0002|0x0004|0x0020|0x0010);
        }
    }
    private interface Dwm extends com.sun.jna.win32.StdCallLibrary{
        Dwm INSTANCE=com.sun.jna.Native.load("dwmapi",Dwm.class);
        int DwmSetWindowAttribute(com.sun.jna.platform.win32.WinDef.HWND window,int attribute,int[] value,int size);
    }
    private static boolean nativeCorners(Window window,int preference){
        if(!System.getProperty("os.name","").startsWith("Windows"))return false;
        try{var handle=new com.sun.jna.platform.win32.WinDef.HWND(com.sun.jna.Native.getComponentPointer(window));return Dwm.INSTANCE.DwmSetWindowAttribute(handle,33,new int[]{preference},4)==0;}catch(Throwable unavailable){return false;}
    }
    static Container content(Window window,Container child){return child;}
    static JPanel controls(Window window,boolean full){
        JPanel controls=UiTheme.transparent(new FlowLayout(FlowLayout.RIGHT,2,0));
        if(full&&window instanceof JFrame frame){
            controls.add(button("最小化窗口","minimize",()->frame.setExtendedState(frame.getExtendedState()|Frame.ICONIFIED)));
            JButton maximize=button("最大化 / 还原窗口","maximize",()->toggleMaximize(frame));controls.add(maximize);
        }
        controls.add(button("关闭窗口","close",()->window.dispatchEvent(new WindowEvent(window,WindowEvent.WINDOW_CLOSING))));return controls;
    }
    private static JButton button(String label,String icon,Runnable action){
        JButton button=new JButton(){@Override protected void paintComponent(Graphics graphics){
            Graphics2D g=(Graphics2D)graphics.create();UiTheme.quality(g);boolean hover=getModel().isRollover(),close="close".equals(icon);
            if(hover||UiTheme.showFocus(this)){g.setColor(close&&hover?new Color(0xDB555E):UiTheme.p().line());g.fillRoundRect(0,0,getWidth(),getHeight(),10,10);}
            g.setColor(close&&hover?Color.WHITE:UiTheme.p().muted());String symbol=icon;
            Window owner=SwingUtilities.getWindowAncestor(this);if("maximize".equals(icon)&&isMaximized(owner))symbol="restore";
            UiTheme.icon(g,symbol,(getWidth()-16)/2,(getHeight()-16)/2,16);g.dispose();
        }};
        button.setName(icon+"Window");button.setToolTipText(label);button.getAccessibleContext().setAccessibleName(label);button.setPreferredSize(new Dimension(32,32));button.setBorderPainted(false);button.setContentAreaFilled(false);button.setFocusPainted(false);button.setRolloverEnabled(true);button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));button.addActionListener(e->action.run());return button;
    }
    static void toggleMaximize(JFrame frame){
        Object saved=frame.getRootPane().getClientProperty("mxcloud.restoreBounds");
        if(saved instanceof Rectangle previous){frame.getRootPane().putClientProperty("mxcloud.restoreBounds",null);frame.setBounds(previous);}
        else{frame.getRootPane().putClientProperty("mxcloud.restoreBounds",new Rectangle(frame.getBounds()));frame.setBounds(workArea(frame.getGraphicsConfiguration()));}
        LauncherRuntime.windowMode(saved==null,frame.getBounds());round(frame);frame.revalidate();frame.repaint();
    }
    static Rectangle workArea(GraphicsConfiguration config){Rectangle bounds=new Rectangle(config.getBounds());Insets insets=Toolkit.getDefaultToolkit().getScreenInsets(config);bounds.x+=insets.left;bounds.y+=insets.top;bounds.width-=insets.left+insets.right;bounds.height-=insets.top+insets.bottom;return bounds;}
    static boolean isMaximized(Window window){return window instanceof RootPaneContainer root&&root.getRootPane().getClientProperty("mxcloud.restoreBounds") instanceof Rectangle||window instanceof Frame frame&&(frame.getExtendedState()&Frame.MAXIMIZED_BOTH)!=0;}
    static void drag(Window window,Component heading){
        MouseAdapter move=new MouseAdapter(){Point press;Rectangle bounds;
            @Override public void mousePressed(MouseEvent e){if(SwingUtilities.isLeftMouseButton(e)){press=e.getLocationOnScreen();bounds=window.getBounds();}}
            @Override public void mouseDragged(MouseEvent e){if(press==null||isMaximized(window))return;Point point=e.getLocationOnScreen();window.setLocation(bounds.x+point.x-press.x,bounds.y+point.y-press.y);}
            @Override public void mouseReleased(MouseEvent e){press=null;}
            @Override public void mouseClicked(MouseEvent e){if(e.getClickCount()==2&&SwingUtilities.isLeftMouseButton(e)&&window instanceof JFrame frame)toggleMaximize(frame);}
        };bindDrag(heading,move);
    }
    private static void bindDrag(Component component,MouseAdapter move){
        if(component instanceof AbstractButton||component instanceof javax.swing.text.JTextComponent||component instanceof JComboBox<?>)return;
        component.addMouseListener(move);component.addMouseMotionListener(move);if(component instanceof Container parent)for(Component child:parent.getComponents())bindDrag(child,move);
    }
    static void dialog(JDialog dialog){
        String title=dialog.getTitle();undecorate(dialog);Container content=dialog.getContentPane();if(dialog.getDefaultCloseOperation()==JDialog.HIDE_ON_CLOSE)dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        JPanel heading=UiTheme.transparent(new BorderLayout(12,0));JPanel controls=controls(dialog,false);JPanel corner=UiTheme.transparent(new BorderLayout());corner.add(controls,BorderLayout.NORTH);heading.add(corner,BorderLayout.EAST);
        if(content.getLayout() instanceof BorderLayout layout){Component old=layout.getLayoutComponent(BorderLayout.NORTH);if(old!=null){content.remove(old);heading.add(old,BorderLayout.CENTER);}else heading.add(UiTheme.label(title,18,true),BorderLayout.CENTER);content.add(heading,BorderLayout.NORTH);}
        else{JPanel wrapper=new JPanel(new BorderLayout());wrapper.setBackground(UiTheme.p().background());heading.setBorder(new EmptyBorder(12,20,0,14));Component label=UiTheme.label(title,18,true);if(content.getLayout() instanceof BoxLayout&&content.getComponentCount()>0&&content.getComponent(0) instanceof JLabel){label=content.getComponent(0);content.remove(label);}heading.add(label,BorderLayout.CENTER);wrapper.add(heading,BorderLayout.NORTH);wrapper.add(content,BorderLayout.CENTER);dialog.setContentPane(wrapper);}
        dialog.setContentPane(content(dialog,dialog.getContentPane()));drag(dialog,heading);resize(dialog,dialog.getRootPane());
        dialog.getRootPane().registerKeyboardAction(e->dialog.dispatchEvent(new WindowEvent(dialog,WindowEvent.WINDOW_CLOSING)),KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0),JComponent.WHEN_IN_FOCUSED_WINDOW);
    }
    static int prompt(Window owner,String title,String message,boolean confirm){
        JDialog dialog=new JDialog(owner,title,Dialog.ModalityType.APPLICATION_MODAL);dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);int[] answer={JOptionPane.CANCEL_OPTION};
        UiTheme.Surface root=new UiTheme.Surface(new BorderLayout(0,18),0);root.setBorder(new EmptyBorder(22,24,22,24));root.add(UiTheme.label(title,19,true),BorderLayout.NORTH);
        JTextArea body=new JTextArea(message);body.setFont(UiTheme.font(12,false));body.setForeground(UiTheme.p().text());body.setOpaque(false);body.setEditable(false);body.setLineWrap(true);body.setWrapStyleWord(true);body.setColumns(34);body.setRows(Math.min(12,Math.max(3,message.length()/30+message.split("\n",-1).length)));body.setCaretPosition(0);JScrollPane scroll=new JScrollPane(body);scroll.setBorder(null);scroll.getViewport().setBackground(UiTheme.p().surface());UiTheme.scroll(scroll);root.add(scroll,BorderLayout.CENTER);
        JPanel actions=UiTheme.transparent(new FlowLayout(FlowLayout.RIGHT,8,0));if(confirm){UiTheme.Button cancel=new UiTheme.Button("取消","",UiTheme.Tone.SURFACE);cancel.addActionListener(e->dialog.dispose());actions.add(cancel);}
        UiTheme.Button accept=new UiTheme.Button("确定","",UiTheme.Tone.PRIMARY);accept.addActionListener(e->{answer[0]=JOptionPane.OK_OPTION;dialog.dispose();});actions.add(accept);root.add(actions,BorderLayout.SOUTH);dialog.setContentPane(root);dialog(dialog);dialog.setResizable(false);dialog.pack();dialog.setLocationRelativeTo(owner);dialog.setVisible(true);return answer[0];
    }
    static void resize(Window window,JRootPane root){
        JComponent edges=new JComponent(){@Override public boolean contains(int x,int y){return resizable(window)&&edge(x,y,getWidth(),getHeight())!=Cursor.DEFAULT_CURSOR;}};edges.setOpaque(false);
        MouseAdapter resize=new MouseAdapter(){int edge;Point press;Rectangle bounds;
            @Override public void mouseMoved(MouseEvent e){edges.setCursor(Cursor.getPredefinedCursor(edge(e.getX(),e.getY(),edges.getWidth(),edges.getHeight())));}
            @Override public void mousePressed(MouseEvent e){if(!SwingUtilities.isLeftMouseButton(e))return;edge=edge(e.getX(),e.getY(),edges.getWidth(),edges.getHeight());press=e.getLocationOnScreen();bounds=window.getBounds();}
            @Override public void mouseDragged(MouseEvent e){if(press==null)return;Point point=e.getLocationOnScreen();int dx=point.x-press.x,dy=point.y-press.y;Rectangle next=new Rectangle(bounds);boolean left=edge==Cursor.W_RESIZE_CURSOR||edge==Cursor.NW_RESIZE_CURSOR||edge==Cursor.SW_RESIZE_CURSOR,right=edge==Cursor.E_RESIZE_CURSOR||edge==Cursor.NE_RESIZE_CURSOR||edge==Cursor.SE_RESIZE_CURSOR,top=edge==Cursor.N_RESIZE_CURSOR||edge==Cursor.NW_RESIZE_CURSOR||edge==Cursor.NE_RESIZE_CURSOR,bottom=edge==Cursor.S_RESIZE_CURSOR||edge==Cursor.SW_RESIZE_CURSOR||edge==Cursor.SE_RESIZE_CURSOR;Dimension min=window.getMinimumSize();
                if(left){next.width=Math.max(min.width,bounds.width-dx);next.x=bounds.x+bounds.width-next.width;}if(right)next.width=Math.max(min.width,bounds.width+dx);if(top){next.height=Math.max(min.height,bounds.height-dy);next.y=bounds.y+bounds.height-next.height;}if(bottom)next.height=Math.max(min.height,bounds.height+dy);window.setBounds(next);}
            @Override public void mouseReleased(MouseEvent e){press=null;}
        };edges.addMouseListener(resize);edges.addMouseMotionListener(resize);root.setGlassPane(edges);edges.setVisible(true);
    }
    private static boolean resizable(Window window){return window instanceof Frame frame?frame.isResizable()&&!isMaximized(window):window instanceof Dialog dialog&&dialog.isResizable();}
    private static int edge(int x,int y,int width,int height){boolean left=x<6,right=x>=width-6,top=y<6,bottom=y>=height-6;if(top)return left?Cursor.NW_RESIZE_CURSOR:right?Cursor.NE_RESIZE_CURSOR:Cursor.N_RESIZE_CURSOR;if(bottom)return left?Cursor.SW_RESIZE_CURSOR:right?Cursor.SE_RESIZE_CURSOR:Cursor.S_RESIZE_CURSOR;return left?Cursor.W_RESIZE_CURSOR:right?Cursor.E_RESIZE_CURSOR:Cursor.DEFAULT_CURSOR;}
}

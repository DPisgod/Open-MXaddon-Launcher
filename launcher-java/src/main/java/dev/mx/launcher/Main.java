package dev.mx.launcher;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Font;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 启动器入口。
 *
 * <p>取得单实例所有权后打开主页，并在后台检查 GitHub Release 更新。
 */
public final class Main {
    private static SingleInstance instance;
    private static LauncherFrame mainWindow;

    private Main() {
    }

    public static void main(String[] args) {
        LauncherRuntime.configureRendering();
        try{
            instance=SingleInstance.acquire(SingleInstance.directory());
            if(instance==null)return;
            Runtime.getRuntime().addShutdownHook(new Thread(()->instance.close(),"mxcloud-instance-release"));
            instance.onActivation(()->{
                if(mainWindow!=null&&mainWindow.isDisplayable())activate(mainWindow);
            });
        }catch(java.io.IOException failure){javax.swing.JOptionPane.showMessageDialog(null,failure.getMessage(),"MXCloud",javax.swing.JOptionPane.WARNING_MESSAGE);return;}
        LauncherRuntime.diagnostics(LauncherPaths.home());
        // 关掉 JNA 的噪声日志
        Logger.getLogger("com.sun.jna").setLevel(Level.OFF);

        // 统一字体（中文显示更整齐；缺失时回落到系统默认）
        installFont();

        // 用系统外观的控件尺寸，颜色由我们自己控制
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        UIManager.put("OptionPane.messageFont", new Font("Microsoft YaHei UI", Font.PLAIN, 12));
        UIManager.put("OptionPane.buttonFont", new Font("Microsoft YaHei UI", Font.PLAIN, 12));

        SwingUtilities.invokeLater(() -> {
            try{LauncherPaths.dataDirectory(LauncherPaths.home());UiTheme.set(Appearance.load(LauncherPaths.home()).theme);LauncherFrame f=new LauncherFrame();mainWindow=f;f.addWindowListener(new java.awt.event.WindowAdapter(){@Override public void windowClosed(java.awt.event.WindowEvent e){instance.close();}});f.setVisible(true);LauncherUpdateDialog.checkOnStartup(f);}
            catch(java.io.UncheckedIOException e){WindowChrome.prompt(null,"无法整理启动器配置",e.getMessage()+"\n请检查 .minecraft 及旧配置文件的占用和写入权限后重试。",false);}
        });
    }

    static void activate(java.awt.Window window){
        if(window instanceof java.awt.Frame frame&&(frame.getExtendedState()&java.awt.Frame.ICONIFIED)!=0)frame.setExtendedState(frame.getExtendedState()&~java.awt.Frame.ICONIFIED);
        java.awt.Window target=window;
        for(java.awt.Window child:window.getOwnedWindows())if(child.isVisible())target=child;
        target.toFront();target.requestFocus();
        if(System.getProperty("os.name","").startsWith("Windows"))try{
            var handle=new com.sun.jna.platform.win32.WinDef.HWND(com.sun.jna.Native.getComponentPointer(target));
            com.sun.jna.platform.win32.User32.INSTANCE.SetForegroundWindow(handle);
        }catch(Throwable ignored){}
    }
    /** 把 Swing 默认字体换成微软雅黑（存在时）。 */
    private static void installFont() {
        String[] candidates = {"Microsoft YaHei UI", "Microsoft YaHei", "SimHei"};
        String chosen = null;
        java.util.Set<String> available = new java.util.HashSet<>();
        for (String name : new String[]{
                "Microsoft YaHei UI", "Microsoft YaHei", "SimHei", "SimSun"}) {
            if (isFontAvailable(name)) {
                available.add(name);
            }
        }
        for (String c : candidates) {
            if (available.contains(c)) {
                chosen = c;
                break;
            }
        }
        if (chosen == null) {
            return;
        }
        final String family = chosen;
        for (Enumeration<Object> e = UIManager.getDefaults().keys(); e.hasMoreElements(); ) {
            Object key = e.nextElement();
            Object val = UIManager.get(key);
            if (val instanceof javax.swing.plaf.FontUIResource) {
                javax.swing.plaf.FontUIResource fr = (javax.swing.plaf.FontUIResource) val;
                UIManager.put(key, new javax.swing.plaf.FontUIResource(
                        family, fr.getStyle(), fr.getSize()));
            }
        }
    }

    private static boolean isFontAvailable(String name) {
        for (String f : java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames()) {
            if (f.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }
}

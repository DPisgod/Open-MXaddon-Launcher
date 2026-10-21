package dev.mx.launcher;

import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.nio.file.*;

/** Exercises the production profile hit area and delete/switch actions with disposable accounts. */
public final class AccountUiBehaviorTest {
    static void check(boolean yes,String message){if(!yes)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        Path home=Files.createTempDirectory("mx-account-ui-test-");LauncherPaths.override(home);Files.createDirectories(home.resolve("skins"));
        Session first=Session.offline("测试玩家"),second=Session.offline("小明");AccountStore store=new AccountStore(home);store.remember(first);store.remember(second);SessionStore.save(home,first);
        for(Session s:new Session[]{first,second})ImageIO.write(SkinFetcher.steve(null),"png",home.resolve("skins").resolve(SkinFetcher.cacheKey(s)+".png").toFile());
        LauncherFrame[] frame=new LauncherFrame[1];SwingUtilities.invokeAndWait(()->{frame[0]=new LauncherFrame(false);frame[0].setVisible(true);});
        SwingUtilities.invokeAndWait(()->{
            Component area=findNamed(frame[0],"accountSwitcher");check(area!=null,"profile hit area missing");
            Component avatar=((Container)area).getComponent(0);avatar.dispatchEvent(new MouseEvent(avatar,MouseEvent.MOUSE_CLICKED,System.currentTimeMillis(),0,5,5,1,false,MouseEvent.BUTTON1));
            JButton pick=AccountBehaviorTest.findButton(popup(),"切换账号 小明 离线");check(pick!=null,"avatar did not open picker");pick.doClick();
        });
        check(SessionStore.load(home).name.equals("小明"),"click switch did not persist Chinese name");
        delete(frame[0],"测试玩家",JOptionPane.CANCEL_OPTION);
        check(new AccountStore(home).available(null).size()==2,"cancel removed account");
        delete(frame[0],"测试玩家",JOptionPane.OK_OPTION);
        check(new AccountStore(home).available(null).size()==1&&SessionStore.load(home).name.equals("小明"),"inactive deletion affected current");
        delete(frame[0],"小明",JOptionPane.OK_OPTION);
        check(new AccountStore(home).available(null).isEmpty()&&SessionStore.load(home)==null,"last account resurrected");
        SwingUtilities.invokeAndWait(()->{frame[0].dispose();frame[0]=new LauncherFrame(false);frame[0].setVisible(true);frame[0].showAccounts();check(AccountBehaviorTest.findButton(popup(),"切换账号 Player 离线")==null,"restart resurrected removed account");frame[0].dispose();});
        System.out.println("PROFILE_CLICK_SWITCH_DELETE_CANCEL_LAST_RESTART_OK");System.exit(0);
    }
    static void delete(LauncherFrame frame,String name,int answer)throws Exception {
        SwingUtilities.invokeAndWait(()->{
            frame.showAccounts();JButton button=AccountBehaviorTest.findButton(popup(),"删除账号 "+name);check(button!=null,"delete missing");
            Timer confirm=new Timer(100,e->{for(Window window:Window.getWindows())if(window instanceof JDialog dialog&&dialog.isVisible()&&"删除账号".equals(dialog.getName())){JButton choice=AccountBehaviorTest.findButton(dialog,answer==JOptionPane.OK_OPTION?"确定":"取消");if(choice!=null)choice.doClick();}});confirm.start();
            try{button.doClick();}finally{confirm.stop();}
        });
    }
    static Container popup(){for(MenuElement element:MenuSelectionManager.defaultManager().getSelectedPath())if(element instanceof JPopupMenu popup)return popup;throw new AssertionError("popup not shown");}
    static Component findNamed(Container parent,String name){for(Component child:parent.getComponents()){if(name.equals(child.getName()))return child;if(child instanceof Container container){Component found=findNamed(container,name);if(found!=null)return found;}}return null;}
    static JOptionPane findPane(Container parent){for(Component child:parent.getComponents()){if(child instanceof JOptionPane pane)return pane;if(child instanceof Container container){JOptionPane found=findPane(container);if(found!=null)return found;}}return null;}
}

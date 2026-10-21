package dev.mx.launcher;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import javax.swing.*;
import java.util.concurrent.atomic.AtomicReference;

/** Multi-account persistence uses real Windows DPAPI in an isolated temporary home. */
public final class AccountBehaviorTest {
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        Path home=Files.createTempDirectory("mx-account-test-");
        AccountStore store=new AccountStore(home);
        Session chinese=Session.offline(Session.offlineName(" 测试玩家_01 "));
        Session second=Session.offline(Session.offlineName("小明"));
        Session premium=Session.premium("OnlineDemo","123456781234123412341234567890ab","fake-test-token-no-access",null);
        store.remember(chinese);store.remember(second);store.remember(premium);store.remember(premium);
        AccountStore loaded=new AccountStore(home);
        check(loaded.available(chinese).size()==3,"duplicates or missing accounts");
        check(loaded.available(chinese).stream().anyMatch(s->s.premium&&s.accessToken.equals(premium.accessToken)),"premium session lost");
        check(!new String(Files.readAllBytes(LauncherPaths.dataFile(home,"accounts.dat")),StandardCharsets.ISO_8859_1).contains(premium.accessToken),"vault contains plaintext credential");
        SessionStore.save(home,chinese);check(SessionStore.load(home).name.equals("测试玩家_01"),"Chinese session did not survive restart");
        check(Session.offlineName("一二三四五六七八九十一二三四五六").length()==16,"16 Chinese characters rejected");
        for(String name:new String[]{"name with space","<html>","一二三四五六七八九十一二三四五六七"}) {
            boolean rejected=false;try{Session.offlineName(name);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"invalid name accepted");
        }
        AtomicReference<Session> selected=new AtomicReference<>();
        SwingUtilities.invokeAndWait(()->{
            AccountPopup popup=new AccountPopup(home,loaded.available(chinese),chinese,selected::set,account->{},account->{},()->{});
            JButton premiumButton=findButton(popup,"切换账号 OnlineDemo 正版");
            check(premiumButton!=null&&findButton(popup,"删除账号 小明")!=null,"account picker missing switch/delete buttons");
            premiumButton.doClick();
        });
        check(selected.get()!=null&&selected.get().premium&&selected.get().uuid.equals(premium.uuid),"quick switch changed premium identity");
        loaded.forget(premium);check(new AccountStore(home).available(chinese).size()==2,"signed-out premium account persisted");
        byte[] bad={1,2,3,4};Files.write(LauncherPaths.dataFile(home,"accounts.dat"),bad);
        boolean failed=false;try{new AccountStore(home).remember(chinese);}catch(java.io.IOException expected){failed=true;}
        check(failed&&java.util.Arrays.equals(bad,Files.readAllBytes(LauncherPaths.dataFile(home,"accounts.dat"))),"unreadable vault overwritten");
        System.out.println("ACCOUNT_CHINESE_DPAPI_SWITCH_SIGNOUT_OK");System.exit(0);
    }
    static JButton findButton(java.awt.Container parent,String name){for(java.awt.Component child:parent.getComponents()){if(child instanceof JButton button&&name.equals(button.getAccessibleContext().getAccessibleName()))return button;if(child instanceof java.awt.Container container){JButton found=findButton(container,name);if(found!=null)return found;}}return null;}
}

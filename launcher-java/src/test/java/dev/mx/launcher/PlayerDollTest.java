package dev.mx.launcher;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class PlayerDollTest {
    static final String UUID="0123456789abcdef0123456789abcdef";
    static void check(boolean yes,String message){if(!yes)throw new AssertionError(message);}
    static Object field(Object object,String name)throws Exception{Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}
    static void edt(Runnable work)throws Exception{SwingUtilities.invokeAndWait(work);}
    static byte[] png(BufferedImage image)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);return out.toByteArray();}
    static BufferedImage cape(){BufferedImage image=new BufferedImage(64,32,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();g.setColor(new Color(0xF52650));g.fillRect(0,0,64,32);g.setColor(new Color(0xFFC840));g.fillRect(4,4,4,10);g.dispose();return image;}
    static byte[] profile(boolean cape,boolean slim){Map<String,Object> textures=new LinkedHashMap<>();textures.put("SKIN",Map.of("url","http://textures.minecraft.net/texture/abcdef","metadata",Map.of("model",slim?"slim":"default")));if(cape)textures.put("CAPE",Map.of("url","https://textures.minecraft.net/texture/123456"));String encoded=Base64.getEncoder().encodeToString(Json.write(Map.of("textures",textures)).getBytes(StandardCharsets.UTF_8));return Json.write(Map.of("id",UUID,"name","FixturePlayer","properties",List.of(Map.of("name","textures","value",encoded)))).getBytes(StandardCharsets.UTF_8);}
    static long hash(BufferedImage image){long hash=1;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)hash=31*hash+image.getRGB(x,y);return hash;}
    static void awaitProfile(PlayerDoll doll)throws Exception{long deadline=System.currentTimeMillis()+6000;boolean[] ready={false};while(!ready[0]&&System.currentTimeMillis()<deadline){edt(()->ready[0]=doll.notice().contains("已获取")||doll.notice().contains("未启用官方披风"));Thread.sleep(20);}check(ready[0],"Texture lookup did not complete: "+doll.notice());}
    public static void main(String[] args)throws Exception{
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());Path home=Files.createTempDirectory("oml-player-doll-"),out=Path.of(args[0]);Files.createDirectories(out);LauncherPaths.override(home);
        try(InputStream original=PlayerTextures.class.getResourceAsStream("/player/steve.png")){check(original!=null,"Original Steve skin missing from distribution");byte[] bytes=original.readAllBytes();check(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)).equals("d876e0c88f4b3de71040966ed94a614f315b888592b520b993399fd2738418d0"),"Original Steve asset changed");check(hash(PlayerTextures.defaultSkin())==hash(PlayerTextures.decode(bytes,false)),"Fallback is not the original Steve skin");}
        check(hash(SkinFetcher.fullSkin(null,home))==hash(PlayerTextures.defaultSkin()),"Homepage doll fallback is not original Steve");
        check(hash(SkinFetcher.fullSkin(Session.offline("未登录正版玩家"),home))==hash(PlayerTextures.defaultSkin()),"Offline homepage doll default is not Steve");
        AtomicReference<byte[]> metadata=new AtomicReference<>(profile(true,true));byte[] skin=png(PlayerTextures.defaultSkin()),cape=png(cape());List<String> requests=Collections.synchronizedList(new ArrayList<>());
        PlayerTextures textures=new PlayerTextures(home,(url,max)->{requests.add(url);if(url.contains("users/profiles/"))return ("{\"id\":\""+UUID+"\"}").getBytes(StandardCharsets.UTF_8);if(url.contains("sessionserver"))return metadata.get();if(url.endsWith("abcdef"))return skin;if(url.endsWith("123456"))return cape;throw new IOException("Unexpected URL "+url);});
        var profile=textures.fetch("FixturePlayer");check(profile.slim()&&profile.cape()!=null&&profile.skin().getWidth()==64,"Profile skin/cape/slim parse");check(requests.stream().allMatch(url->url.startsWith("https://")),"Insecure texture request");check(textures.cached("fixtureplayer").cape()!=null,"Texture cache not restored by player name");
        metadata.set(profile(false,false));check(textures.fetch(UUID).cape()==null&&textures.cached(UUID).cape()==null,"Unequipped cape retained in cache");metadata.set(profile(true,true));
        boolean rejected=false;try{PlayerTextures.textureUrl("https://example.com/texture/abcdef");}catch(IOException expected){rejected=true;}check(rejected,"Untrusted texture URL accepted");
        PlayerDollView renderer=new PlayerDollView();renderer.setSize(260,360);renderer.profile(profile);BufferedImage front=AppearanceControlsTest.render(renderer);check((front.getRGB(0,0)>>>24)==255,"Fixture RGB rendering changed");
        // Export alpha using the production component, keeping the transparent desktop edges.
        BufferedImage transparent=new BufferedImage(260,360,BufferedImage.TYPE_INT_ARGB);Graphics2D g=transparent.createGraphics();renderer.paint(g);g.dispose();check((transparent.getRGB(0,0)>>>24)==0,"Doll background is opaque");ImageIO.write(transparent,"png",out.resolve("player-doll-front.png").toFile());
        renderer.back=true;BufferedImage back=AppearanceControlsTest.render(renderer);ImageIO.write(back,"png",out.resolve("player-doll-cape.png").toFile());renderer.showCape=false;check(hash(back)!=hash(AppearanceControlsTest.render(renderer)),"Cape is not rendered from the back");renderer.showCape=true;
        renderer.interact();Thread.sleep(130);check(hash(back)!=hash(AppearanceControlsTest.render(renderer)),"Interaction did not animate limbs");
        BufferedImage legacy=new BufferedImage(64,32,BufferedImage.TYPE_INT_ARGB);g=legacy.createGraphics();g.drawImage(PlayerTextures.defaultSkin(),0,0,null);g.dispose();renderer.profile(new PlayerTextures.Profile(UUID,"Legacy",legacy,null,false));AppearanceControlsTest.render(renderer);
        Session premium=Session.premium("FixturePlayer",UUID,"DO_NOT_SEND_TEST_TOKEN",null);AtomicReference<Session> current=new AtomicReference<>(premium);PlayerDoll[] doll={null};LauncherFrame[] launcher={null};
        try{
            edt(()->{UiTheme.set("cyber");launcher[0]=new LauncherFrame(false);launcher[0].setVisible(true);doll[0]=new PlayerDoll(launcher[0],home,current::get,()->List.of(premium),true,textures);PlayerDollOptions options=doll[0].options();options.enabled=true;check(doll[0].apply(options),"Enable failed");});awaitProfile(doll[0]);
            check(requests.stream().noneMatch(url->url.contains("TOKEN")),"Account token sent to texture API");
            edt(()->{try{
                JWindow window=(JWindow)field(doll[0],"window");PlayerDollView view=(PlayerDollView)field(doll[0],"view");check(window.isVisible()&&window.getBackground().getAlpha()==0&&window.isAlwaysOnTop(),"Desktop window transparency/visibility/topmost");
                Point before=window.getLocation();long time=System.currentTimeMillis();view.dispatchEvent(new MouseEvent(view,MouseEvent.MOUSE_PRESSED,time,InputEvent.BUTTON1_DOWN_MASK,100,130,1,false,MouseEvent.BUTTON1));view.dispatchEvent(new MouseEvent(view,MouseEvent.MOUSE_RELEASED,time+1,0,100,130,1,false,MouseEvent.BUTTON1));check((int)field(view,"action")>=0,"Left click did not interact");
                view.dispatchEvent(new MouseEvent(view,MouseEvent.MOUSE_PRESSED,time+2,InputEvent.BUTTON1_DOWN_MASK,100,130,1,false,MouseEvent.BUTTON1));view.dispatchEvent(new MouseEvent(view,MouseEvent.MOUSE_DRAGGED,time+3,InputEvent.BUTTON1_DOWN_MASK,70,100,0,false,MouseEvent.NOBUTTON));view.dispatchEvent(new MouseEvent(view,MouseEvent.MOUSE_RELEASED,time+4,0,70,100,1,false,MouseEvent.BUTTON1));check(!window.getLocation().equals(before),"Dragging did not move doll");
                PlayerDollOptions saved=PlayerDollOptions.load(home);check(saved.x==window.getX()&&saved.y==window.getY(),"Drag position not saved");
                view.dispatchEvent(new MouseEvent(view,MouseEvent.MOUSE_RELEASED,time+5,0,100,130,1,true,MouseEvent.BUTTON3));MenuElement[] menus=MenuSelectionManager.defaultManager().getSelectedPath();check(menus.length>0,"Right click menu missing");
                JPopupMenu popup=(JPopupMenu)menus[0];((JMenuItem)popup.getComponent(0)).doClick(0);check(field(doll[0],"settings") instanceof PlayerDollSettingsDialog,"Right click settings entry failed");
            }catch(Exception failure){throw new RuntimeException(failure);}});Thread.sleep(260);
            double[] angle={0};edt(()->{try{PlayerDollView view=(PlayerDollView)field(doll[0],"view");angle[0]=view.angle;view.dispatchEvent(new MouseWheelEvent(view,MouseEvent.MOUSE_WHEEL,System.currentTimeMillis(),0,100,130,0,false,MouseWheelEvent.WHEEL_UNIT_SCROLL,3,4));check(view.angle==angle[0],"Doll wheel jumped without easing");}catch(Exception failure){throw new RuntimeException(failure);}});
            Thread.sleep(45);edt(()->{try{double now=((PlayerDollView)field(doll[0],"view")).angle;check(now>angle[0]&&now<angle[0]+.72,"Doll wheel lacks intermediate rotation");}catch(Exception failure){throw new RuntimeException(failure);}});
            edt(()->{try{
                PlayerDollSettingsDialog settings=(PlayerDollSettingsDialog)field(doll[0],"settings");ImageIO.write(AppearanceControlsTest.render(settings.getContentPane()),"png",out.resolve("player-doll-settings.png").toFile());
                JComboBox<?> source=(JComboBox<?>)AccountUiBehaviorTest.findNamed(settings,"dollSource");source.setSelectedIndex(1);((JTextField)AccountUiBehaviorTest.findNamed(settings,"dollPlayerId")).setText("FixturePlayer");((JSlider)AccountUiBehaviorTest.findNamed(settings,"dollScale")).setValue(120);AccountBehaviorTest.findButton(settings,"应用").doClick(0);
                check(PlayerDollOptions.load(home).manual&&PlayerDollOptions.load(home).scale==120,"Manual source/scale not persisted");check(((JWindow)field(doll[0],"window")).getWidth()==264,"Scale did not resize window");settings.dispose();
                PlayerDollOptions options=doll[0].options();options.enabled=false;doll[0].apply(options);check(!((JWindow)field(doll[0],"window")).isVisible()&&!((javax.swing.Timer)field(doll[0],"ticker")).isRunning(),"Hide left window/timer running");options.enabled=true;doll[0].apply(options);
                current.set(null);PlayerDoll fallback=new PlayerDoll(launcher[0],home,()->null,List::of,false,textures);try{PlayerDollOptions noAccount=new PlayerDollOptions();noAccount.enabled=true;fallback.apply(noAccount);PlayerDollView fallbackView=(PlayerDollView)field(fallback,"view");check(hash(fallbackView.profile().skin())==hash(PlayerTextures.defaultSkin())&&fallbackView.profile().cape()==null,"No-account fallback is not original Steve without cape");}finally{fallback.close();}
                SettingsDialog main=new SettingsDialog(launcher[0],home,LauncherPaths.gameRoot());main.setModal(false);main.setVisible(true);AccountBehaviorTest.findButton(main,"玩家玩偶…").doClick(0);
            }catch(Exception failure){throw new RuntimeException(failure);}});
            edt(()->{try{PlayerDoll controller=(PlayerDoll)field(launcher[0],"playerDoll");check(((JDialog)field(controller,"settings")).isShowing(),"Launcher settings cannot reopen hidden doll settings");SkinDoll embedded=(SkinDoll)field(launcher[0],"doll");PlayerDollOptions desktop=controller.options();desktop.enabled=true;controller.apply(desktop);check(!embedded.isVisible(),"Homepage doll duplicated desktop doll");desktop.enabled=false;controller.apply(desktop);check(embedded.isVisible(),"Homepage doll did not return when desktop doll hidden");}catch(Exception failure){throw new RuntimeException(failure);}});
            Point clamped=PlayerDollOptions.position(Integer.MAX_VALUE,Integer.MIN_VALUE,new Dimension(220,300));check(clamped.x<Integer.MAX_VALUE&&clamped.y>Integer.MIN_VALUE,"Offscreen saved position not recovered");
            System.out.println("PLAYER_TEXTURES_SKIN_CAPE_SLIM_LEGACY_CACHE_NO_STALE_CAPE_TRANSPARENT_RENDER_INTERACT_DRAG_RIGHT_MENU_SETTINGS_MANUAL_SCALE_HIDE_TIMER_REOPEN_PERSIST_OK");
        }finally{edt(()->{if(doll[0]!=null)doll[0].close();if(launcher[0]!=null){for(Window owned:launcher[0].getOwnedWindows())owned.dispose();launcher[0].dispose();}});}
        System.exit(0);
    }
}

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
        Session premium=Session.premium("FixturePlayer",UUID,"DO_NOT_SEND_TEST_TOKEN",null);AtomicReference<Session> current=new AtomicReference<>(premium);PlayerDoll[] doll={null};JFrame[] host={null};LauncherFrame[] launcher={null};JLayeredPane[] stage={null};
        try{
            // Old desktop coordinates must never become launcher-relative coordinates.
            Path legacyHome=Files.createTempDirectory("oml-doll-migration-");Path prefs=LauncherPaths.dataDirectory(legacyHome).resolve("player-doll.properties");Files.writeString(prefs,"enabled=true\nx=1800\ny=900\nscale=120\n");
            PlayerDollOptions migrated=PlayerDollOptions.load(legacyHome);check(migrated.enabled&&migrated.x==Integer.MIN_VALUE&&migrated.y==Integer.MIN_VALUE&&migrated.scale==120,"Desktop preference migration failed");
            edt(()->{UiTheme.set("cyber");host[0]=new JFrame();host[0].setSize(640,500);stage[0]=new JLayeredPane();host[0].setContentPane(stage[0]);doll[0]=new PlayerDoll(host[0],home,current::get,()->List.of(premium),true,textures);doll[0].mount(stage[0]);host[0].setVisible(true);doll[0].start();});awaitProfile(doll[0]);
            check(requests.stream().noneMatch(url->url.contains("TOKEN")),"Account token sent to texture API");
            edt(()->{try{
                PlayerDollView view=(PlayerDollView)field(doll[0],"view");check(view.isShowing()&&view.getParent()==stage[0]&&SwingUtilities.getWindowAncestor(view)==host[0],"Doll is not embedded in its launcher");
                check(Arrays.stream(Window.getWindows()).noneMatch(w->"playerDollWindow".equals(w.getName())),"Independent desktop doll window remains");
                Point before=view.getLocation();long time=System.currentTimeMillis();view.dispatchEvent(new MouseEvent(view,MouseEvent.MOUSE_PRESSED,time,InputEvent.BUTTON1_DOWN_MASK,100,130,1,false,MouseEvent.BUTTON1));view.dispatchEvent(new MouseEvent(view,MouseEvent.MOUSE_RELEASED,time+1,0,100,130,1,false,MouseEvent.BUTTON1));check((int)field(view,"action")>=0,"Left click did not interact");
                view.dispatchEvent(new MouseEvent(view,MouseEvent.MOUSE_PRESSED,time+2,InputEvent.BUTTON1_DOWN_MASK,100,130,1,false,MouseEvent.BUTTON1));view.dispatchEvent(new MouseEvent(view,MouseEvent.MOUSE_DRAGGED,time+3,InputEvent.BUTTON1_DOWN_MASK,70,100,0,false,MouseEvent.NOBUTTON));view.dispatchEvent(new MouseEvent(view,MouseEvent.MOUSE_RELEASED,time+4,0,70,100,1,false,MouseEvent.BUTTON1));check(!view.getLocation().equals(before),"Dragging did not move doll");
                PlayerDollOptions saved=PlayerDollOptions.load(home);check(saved.x==view.getX()&&saved.y==view.getY(),"Launcher-relative position not saved");
                view.dispatchEvent(new MouseEvent(view,MouseEvent.MOUSE_RELEASED,time+5,0,100,130,1,true,MouseEvent.BUTTON3));MenuElement[] menus=MenuSelectionManager.defaultManager().getSelectedPath();check(menus.length>0,"Right click menu missing");
                JPopupMenu popup=(JPopupMenu)menus[0];((JMenuItem)popup.getComponent(0)).doClick(0);check(field(doll[0],"settings") instanceof PlayerDollSettingsDialog,"Right click settings entry failed");
            }catch(Exception failure){throw new RuntimeException(failure);}});Thread.sleep(260);
            double[] angle={0};edt(()->{try{PlayerDollView view=(PlayerDollView)field(doll[0],"view");angle[0]=view.angle;view.dispatchEvent(new MouseWheelEvent(view,MouseEvent.MOUSE_WHEEL,System.currentTimeMillis(),0,100,130,0,false,MouseWheelEvent.WHEEL_UNIT_SCROLL,3,4));check(view.angle==angle[0],"Doll wheel jumped without easing");}catch(Exception failure){throw new RuntimeException(failure);}});
            Thread.sleep(45);edt(()->{try{double now=((PlayerDollView)field(doll[0],"view")).angle;check(now>angle[0]&&now<angle[0]+.72,"Doll wheel lacks intermediate rotation");}catch(Exception failure){throw new RuntimeException(failure);}});
            edt(()->{try{PlayerDollSettingsDialog settings=(PlayerDollSettingsDialog)field(doll[0],"settings");((JComboBox<?>)AccountUiBehaviorTest.findNamed(settings,"dollSource")).setSelectedIndex(1);JTextField input=(JTextField)AccountUiBehaviorTest.findNamed(settings,"dollPlayerId");input.setText("FixturePlayer");check(!doll[0].options().player.equals("FixturePlayer"),"ID lookup did not debounce typing");}catch(Exception failure){throw new RuntimeException(failure);}});
            Thread.sleep(800);edt(()->check(doll[0].options().player.equals("FixturePlayer")&&PlayerDollOptions.load(home).manual,"ID did not save automatically after typing"));awaitProfile(doll[0]);
            edt(()->{try{
                PlayerDollSettingsDialog settings=(PlayerDollSettingsDialog)field(doll[0],"settings");ImageIO.write(AppearanceControlsTest.render(settings.getContentPane()),"png",out.resolve("player-doll-settings.png").toFile());
                check(AccountBehaviorTest.findButton(settings,"应用")==null,"Apply button remains");JComboBox<?> source=(JComboBox<?>)AccountUiBehaviorTest.findNamed(settings,"dollSource");source.setSelectedIndex(1);((JTextField)AccountUiBehaviorTest.findNamed(settings,"dollPlayerId")).setText("FixturePlayer");JSlider scale=(JSlider)AccountUiBehaviorTest.findNamed(settings,"dollScale");int savedScale=PlayerDollOptions.load(home).scale;scale.setValueIsAdjusting(true);scale.setValue(90);check(((PlayerDollView)field(doll[0],"view")).getWidth()==198,"Scale does not preview during dragging");check(PlayerDollOptions.load(home).scale==savedScale,"Slider writes preferences during dragging");scale.setValue(120);scale.setValueIsAdjusting(false);
                PlayerDollView view=(PlayerDollView)field(doll[0],"view");check(PlayerDollOptions.load(home).manual&&PlayerDollOptions.load(home).scale==120,"Manual source/scale not persisted");check(view.getWidth()==264,"Scale did not resize embedded model");JCheckBox capeToggle=(JCheckBox)AccountUiBehaviorTest.findNamed(settings,"dollCape");capeToggle.doClick(0);check(!view.showCape&&!PlayerDollOptions.load(home).cape,"Cape toggle is not live");capeToggle.doClick(0);
                JCheckBox showToggle=(JCheckBox)AccountUiBehaviorTest.findNamed(settings,"dollEnabled");showToggle.doClick(0);check(!view.isVisible(),"Display toggle is not live");showToggle.doClick(0);JTextField edit=(JTextField)AccountUiBehaviorTest.findNamed(settings,"dollPlayerId");edit.setText("invalid player ID!");capeToggle.doClick(0);check(!view.showCape&&doll[0].options().player.equals("FixturePlayer"),"Invalid partial ID blocked another live setting");capeToggle.doClick(0);edit.setText("FixturePlayer2");settings.dispose();check(PlayerDollOptions.load(home).player.equals("FixturePlayer2"),"Closing lost pending ID edit");
                host[0].setSize(350,420);host[0].validate();doll[0].layoutView();check(view.getX()>=0&&view.getY()>=0&&view.getX()+view.getWidth()<=stage[0].getWidth()&&view.getY()+view.getHeight()<=stage[0].getHeight(),"Resized doll escaped launcher bounds");
                doll[0].paused(true);check(!((javax.swing.Timer)field(doll[0],"ticker")).isRunning(),"Minimized launcher kept doll timer running");doll[0].paused(false);
                PlayerDollOptions options=doll[0].options();options.enabled=false;doll[0].apply(options);check(!view.isVisible()&&!((javax.swing.Timer)field(doll[0],"ticker")).isRunning(),"Hidden doll kept animating");options.enabled=true;doll[0].apply(options);
                doll[0].close();PlayerDoll fallback=new PlayerDoll(host[0],home,()->null,List::of,false,textures);try{fallback.mount(stage[0]);PlayerDollOptions defaultOptions=fallback.options();defaultOptions.manual=false;defaultOptions.enabled=true;fallback.apply(defaultOptions);PlayerDollView fallbackView=(PlayerDollView)field(fallback,"view");check(hash(fallbackView.profile().skin())==hash(PlayerTextures.defaultSkin())&&fallbackView.profile().cape()==null,"No-account fallback is not original Steve");}finally{fallback.close();}
                doll[0].close();host[0].dispose();launcher[0]=new LauncherFrame(false);launcher[0].setVisible(true);
            }catch(Exception failure){throw new RuntimeException(failure);}});Thread.sleep(260);
            edt(()->{try{
                PlayerDoll controller=(PlayerDoll)field(launcher[0],"playerDoll");PlayerDollView view=(PlayerDollView)field(controller,"view");check(view.isShowing(),"Homepage doll not shown by default");
                check(SwingUtilities.getAncestorOfClass(JLayeredPane.class,view).getName().equals("playerDollStage"),"Doll not confined to home content area");
                ImageIO.write(AppearanceControlsTest.render(launcher[0].getContentPane()),"png",out.resolve("launcher-home-player-doll.png").toFile());launcher[0].showStyles();
            }catch(Exception failure){throw new RuntimeException(failure);}});Thread.sleep(280);
            edt(()->{try{
                PlayerDoll controller=(PlayerDoll)field(launcher[0],"playerDoll");PlayerDollView view=(PlayerDollView)field(controller,"view");check(!view.isShowing()&&!((javax.swing.Timer)field(controller,"ticker")).isRunning(),"Doll remained visible/active on other pages");
                JCheckBox toggle=(JCheckBox)AccountUiBehaviorTest.findNamed(launcher[0].stylePanel(),"skinDollToggle");toggle.doClick(0);check(!controller.options().enabled,"Personalization switch did not hide unified doll");
                PlayerDollOptions options=controller.options();options.enabled=true;controller.apply(options);check(toggle.isSelected(),"Settings switch not reflected in personalization");AccountBehaviorTest.findButton(launcher[0].stylePanel(),"返回主页").doClick(0);
                SettingsDialog main=new SettingsDialog(launcher[0],home,LauncherPaths.gameRoot());main.setModal(false);main.setVisible(true);AccountBehaviorTest.findButton(main,"玩家玩偶…").doClick(0);check(((JDialog)field(controller,"settings")).isShowing(),"Main settings cannot reopen doll settings");
            }catch(Exception failure){throw new RuntimeException(failure);}});
            Point clamped=PlayerDollOptions.position(Integer.MAX_VALUE,Integer.MIN_VALUE,new Dimension(220,300),new Dimension(640,500));check(clamped.x>=0&&clamped.y>=0&&clamped.x+220<=640&&clamped.y+300<=500,"Out-of-bounds position not recovered");
            System.out.println("EMBEDDED_DOLL_NO_DESKTOP_WINDOW_ORIGINAL_STEVE_TEXTURES_CAPE_INTERACT_DRAG_RIGHT_MENU_MANUAL_SCALE_RESIZE_MIGRATION_PAGE_PAUSE_TOGGLE_REOPEN_OK");
        }finally{edt(()->{if(doll[0]!=null)doll[0].close();if(host[0]!=null)host[0].dispose();if(launcher[0]!=null){for(Window owned:launcher[0].getOwnedWindows())owned.dispose();launcher[0].dispose();}});}
        System.exit(0);
    }
}

package dev.mx.launcher;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

/** Owns one independent transparent desktop window; closed with its launcher. */
final class PlayerDoll implements AutoCloseable {
    private final Window owner;
    private final Path home;
    private final Supplier<Session> current;
    private final Supplier<List<Session>> accounts;
    private final boolean network;
    private final PlayerTextures textures;
    private PlayerDollOptions options;
    private JWindow window;
    private final PlayerDollView view=new PlayerDollView();
    private final javax.swing.Timer ticker=new UiMotion.FrameTimer(16,e->tick());
    private double targetAngle;
    private long lastFrame;
    private SwingWorker<PlayerTextures.Profile,Void> lookup;
    private String sourceId="",notice="左键互动 · 拖动移动 · 右键设置";
    private int request;
    private boolean closed;
    private PlayerDollSettingsDialog settings;
    private Runnable visibilityChanged=()->{};
    PlayerDoll(Window owner,Path home,Supplier<Session> current,Supplier<List<Session>> accounts,boolean network){this(owner,home,current,accounts,network,new PlayerTextures(home));}
    PlayerDoll(Window owner,Path home,Supplier<Session> current,Supplier<List<Session>> accounts,boolean network,PlayerTextures textures){this.owner=owner;this.home=home;this.current=current;this.accounts=accounts;this.network=network;this.textures=textures;options=PlayerDollOptions.load(home);}
    PlayerDollOptions options(){return options.copy();}
    void onVisibilityChanged(Runnable listener){visibilityChanged=listener;}
    String notice(){return notice;}
    void start(){if(options.enabled)show();}
    void openSettings(Window parent){if(settings!=null&&settings.isDisplayable()){settings.toFront();return;}settings=new PlayerDollSettingsDialog(parent==null?owner:parent,this);settings.setVisible(true);}
    private String selectedId(){if(options.manual)return options.player.strip();Session account=current.get();if(account!=null&&account.premium)return account.uuid;for(Session saved:accounts.get())if(saved.premium)return saved.uuid;return "";}
    void accountChanged(){if(window!=null&&window.isVisible()&&!options.manual&&!selectedId().equalsIgnoreCase(sourceId))refresh();}
    boolean apply(PlayerDollOptions next){
        if(next.manual&&!next.player.strip().matches("[A-Za-z0-9_]{1,16}")&&!next.player.strip().replace("-","").matches("[a-fA-F0-9]{32}")){notice="请输入正版 Java 玩家名或 UUID";updateNotice();return false;}
        next.scale=Math.max(60,Math.min(180,next.scale));next.player=next.player.strip();next.x=options.x;next.y=options.y;
        try{next.save(home);}catch(Exception failure){notice="无法保存玩偶设置，请检查目录权限";updateNotice();return false;}
        boolean wasEnabled=options.enabled;options=next.copy();
        if(options.enabled)show();else hide();if(wasEnabled!=options.enabled)visibilityChanged.run();return true;
    }
    private void show(){
        if(closed)return;
        if(window==null){window=new JWindow((Window)null);window.setName("playerDollWindow");window.setType(Window.Type.UTILITY);window.setFocusableWindowState(false);window.setBackground(new Color(0,0,0,0));window.setContentPane(view);window.setIconImages(ApplicationIcon.images());installMouse();}
        window.setAlwaysOnTop(options.topmost);view.showCape=options.cape;window.setSize(220*options.scale/100,300*options.scale/100);window.setLocation(PlayerDollOptions.position(options.x,options.y,window.getSize()));window.setVisible(true);ticker.start();if(!selectedId().equalsIgnoreCase(sourceId)||sourceId.isEmpty()||lookup!=null&&lookup.isCancelled())refresh();
    }
    void refresh(){
        if(closed)return;String id=selectedId();sourceId=id;int serial=++request;if(lookup!=null)lookup.cancel(true);
        if(id.isBlank()){view.profile(new PlayerTextures.Profile("","Steve",PlayerTextures.defaultSkin(),null,false));notice=options.manual?"请输入正版玩家 ID":"暂无已登录正版账号，可在设置中输入正版玩家 ID";view.say("右键设置玩家 ID");updateNotice();return;}
        var cached=textures.cached(id);if(cached!=null)view.profile(cached);else view.profile(new PlayerTextures.Profile("",options.manual?options.player:"Player",PlayerTextures.defaultSkin(),null,false));
        if(!network){notice=cached==null?"离线预览：未连接玩家材质服务":"已读取缓存皮肤与披风";updateNotice();return;}
        notice="正在获取皮肤和披风…";updateNotice();lookup=new SwingWorker<>(){
            @Override protected PlayerTextures.Profile doInBackground()throws Exception{return textures.fetch(id);}
            @Override protected void done(){if(closed||isCancelled()||serial!=request)return;try{var profile=get();view.profile(profile);notice=profile.name()+" · "+(profile.slim()?"纤细手臂":"标准手臂")+" · "+(profile.cape()==null?"未启用官方披风":"皮肤与披风已获取");view.say("我是 "+profile.name());}catch(Exception failure){notice=(cached!=null?"使用缓存；":"获取失败；")+message(failure);view.say(cached!=null?"先穿缓存的皮肤～":"右键重试获取");}updateNotice();}
        };lookup.execute();
    }
    private void updateNotice(){if(settings!=null&&settings.isDisplayable())settings.status(notice);}
    private void tick(){long now=System.nanoTime();double dt=lastFrame==0?16:Math.min(100,(now-lastFrame)/1_000_000.0);lastFrame=now;view.angle+=(targetAngle-view.angle)*(1-Math.exp(-dt/55.0));view.repaint();}
    private void hide(){ticker.stop();lastFrame=0;if(lookup!=null)lookup.cancel(true);if(window!=null)window.setVisible(false);}
    private void installMouse(){
        MouseAdapter mouse=new MouseAdapter(){Point pressed,origin;boolean moved;
            @Override public void mousePressed(MouseEvent e){if(SwingUtilities.isLeftMouseButton(e)){pressed=e.getLocationOnScreen();origin=window.getLocation();moved=false;}}
            @Override public void mouseDragged(MouseEvent e){if(pressed==null)return;Point now=e.getLocationOnScreen();int dx=now.x-pressed.x,dy=now.y-pressed.y;if(Math.abs(dx)+Math.abs(dy)>4)moved=true;if(moved)window.setLocation(PlayerDollOptions.position(origin.x+dx,origin.y+dy,window.getSize()));}
            @Override public void mouseReleased(MouseEvent e){if(SwingUtilities.isRightMouseButton(e)){menu(e);return;}if(!SwingUtilities.isLeftMouseButton(e)||pressed==null)return;if(moved){options.x=window.getX();options.y=window.getY();saveQuietly();}else view.interact();pressed=null;}
            @Override public void mouseWheelMoved(MouseWheelEvent e){targetAngle+=e.getPreciseWheelRotation()*.18;e.consume();}
        };view.addMouseListener(mouse);view.addMouseMotionListener(mouse);view.addMouseWheelListener(mouse);
    }
    private void menu(MouseEvent e){JPopupMenu menu=new JPopupMenu();item(menu,"玩偶设置…",()->openSettings(owner));item(menu,"挥手 / 跳跃 / 转圈",view::interact);item(menu,"转身查看披风",()->targetAngle+=Math.PI);item(menu,"刷新皮肤与披风",this::refresh);menu.addSeparator();item(menu,"隐藏玩偶",()->{PlayerDollOptions next=options.copy();next.enabled=false;apply(next);});menu.show(view,e.getX(),e.getY());}
    private static void item(JPopupMenu menu,String title,Runnable action){JMenuItem item=new JMenuItem(title);item.setFont(UiTheme.font(12,false));item.setBackground(UiTheme.p().surface());item.setForeground(UiTheme.p().text());item.addActionListener(e->action.run());menu.add(item);}
    private void saveQuietly(){try{options.save(home);}catch(Exception failure){notice="位置未保存："+message(failure);updateNotice();}}
    private static String message(Throwable failure){while(failure.getCause()!=null&&failure.getCause()!=failure)failure=failure.getCause();return failure.getMessage()==null?"请稍后重试":failure.getMessage();}
    @Override public void close(){closed=true;request++;hide();if(window!=null)window.dispose();if(settings!=null)settings.dispose();}
}

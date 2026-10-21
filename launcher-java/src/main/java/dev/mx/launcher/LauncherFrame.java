package dev.mx.launcher;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.List;
import java.util.Map;

/** F: panoramic wallpaper, quiet navigation and a single floating launch dock. */
public final class LauncherFrame extends JFrame {
    private final Path home=LauncherPaths.home(),gameRoot=home.resolve(".minecraft");
    private final AccountStore accounts=new AccountStore(home);
    private JPanel profileControl;
    private Appearance appearance=Appearance.load(home);
    private final WallpaperLibrary library=new WallpaperLibrary(home);
    private final WallpaperPlayer wallpaper=new WallpaperPlayer();
    private final WallpaperView backdrop=new WallpaperView(wallpaper,appearance,0);
    private final JPanel homeView=UiTheme.transparent(new BorderLayout());
    private final AnnouncementOverlay announcement=new AnnouncementOverlay(home);
    private final java.util.List<UiTheme.Surface> wallpaperSurfaces=new java.util.ArrayList<>();
    private final CardLayout pages=new CardLayout();
    private final JPanel pageHost=UiTheme.transparent(pages);
    private AppearancePanel appearancePanel;
    private DownloadCenterPanel downloadCenter;
    private boolean downloadsVisible;
    private final boolean network;
    private final UiTheme.Button homeTab=new UiTheme.Button("主页","",UiTheme.Tone.NAV);
    private final UiTheme.Button appearanceTab=new UiTheme.Button("壁纸","",UiTheme.Tone.NAV);
    private final UiTheme.Button downloadsTab=new UiTheme.Button("下载","",UiTheme.Tone.NAV);
    private final UiTheme.Button launchBtn=new UiTheme.Button("启动游戏","play",UiTheme.Tone.PRIMARY);
    private final UiTheme.PowerButton stopBtn=new UiTheme.PowerButton();
    private final GameProcesses games=new GameProcesses();
    private int lastGameCount;
    private final UiTheme.Button settingsBtn=new UiTheme.Button("","settings",UiTheme.Tone.PLAIN);
    private final UiTheme.Button lightBtn=new UiTheme.Button("","sun",UiTheme.Tone.NAV);
    private final UiTheme.Button systemBtn=new UiTheme.Button("","system",UiTheme.Tone.NAV);
    private final UiTheme.Button darkBtn=new UiTheme.Button("","moon",UiTheme.Tone.NAV);
    private final JComboBox<String> versionBox=new JComboBox<>();
    private static final String ADD_VERSION="添加版本…",VERSION_SETTINGS="当前版本设置…",DELETE_VERSION="删除当前版本…",NO_VERSION="未安装游戏版本";
    private boolean updatingVersions;
    private String selectedVersion="";
    private final JLabel nameLabel=UiTheme.label("添加游戏账号",13,true),typeLabel=UiTheme.muted("尚未选择账号",10);
    private final JLabel statusLabel=UiTheme.muted("正在准备…",11),loaderLabel=UiTheme.muted("选择游戏版本",10);
    private final AvatarPanel avatar=new AvatarPanel();
    private final JProgressBar progress=new UiTheme.ProgressBar();
    private Timer poller,systemThemeTimer;
    private Session session;
    private boolean busy,appearanceVisible,disposed,accountCleared=true;

    public LauncherFrame(){this(true);}
    /** UI harness never touches the file system outside its own home; launch stays available without authorization. */
    LauncherFrame(boolean initialize){
        super("");network=initialize;WindowChrome.undecorate(this);UiTheme.set(appearance.theme);setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(1000,740));setSize(1140,810);setLocationRelativeTo(null);
        buildUi();Session saved=SessionStore.load(home);List<Session> available=accounts.available(saved);session=saved!=null?saved:available.isEmpty()?null:available.get(0);accountCleared=session==null;if(session!=null)SessionStore.save(home,session);applySession();avatar.setImage(session==null?null:SkinFetcher.cached(session,home));wallpaper.onChange(backdrop::repaint);wallpaper.onError(error->statusLabel.setText("壁纸读取失败，请在外观中更换"));wallpaper.select(library.find(appearance.wallpaper),appearance);
        addWindowListener(new WindowAdapter(){@Override public void windowOpened(WindowEvent e){refreshTheme();}@Override public void windowIconified(WindowEvent e){syncPlayback();}@Override public void windowDeiconified(WindowEvent e){syncPlayback();}@Override public void windowClosed(WindowEvent e){disposeResources();}});
        systemThemeTimer=new Timer(3000,e->{if("system".equals(appearance.theme)&&UiTheme.set("system"))refreshTheme();});systemThemeTimer.start();
        if(initialize)SwingUtilities.invokeLater(this::bootstrap);else{refreshVersions();statusLabel.setText("界面预览");}
    }
    private void buildUi(){
        WallpaperView root=backdrop;
        UiTheme.Surface header=new UiTheme.Surface(new BorderLayout(20,0),0);header.setBorder(new EmptyBorder(12,24,12,20));header.setPreferredSize(new Dimension(100,70));
        JPanel brand=UiTheme.transparent(new FlowLayout(FlowLayout.LEFT,10,5));
        JComponent mark=new JComponent(){@Override protected void paintComponent(Graphics graphics){Graphics2D g=(Graphics2D)graphics.create();UiTheme.quality(g);g.drawImage(ApplicationIcon.brand(),0,0,30,30,this);g.dispose();}};mark.setName("brandMark");mark.setPreferredSize(new Dimension(30,30));brand.add(mark);
        JLabel logo=UiTheme.label("Open-MXaddon-Launcher",16,true);logo.setFont(new Font("Segoe UI",Font.BOLD,16));brand.add(logo);brand.setPreferredSize(new Dimension(295,44));header.add(brand,BorderLayout.WEST);
        JPanel navigation=UiTheme.transparent(new FlowLayout(FlowLayout.CENTER,0,0));UiTheme.Surface navPill=new UiTheme.Surface(new FlowLayout(FlowLayout.CENTER,2,3),24);navPill.background=true;homeTab.setPreferredSize(new Dimension(82,36));appearanceTab.setPreferredSize(new Dimension(82,36));navPill.add(homeTab);navPill.add(appearanceTab);navigation.add(navPill);header.add(navigation,BorderLayout.CENTER);
        JPanel right=UiTheme.transparent(new FlowLayout(FlowLayout.RIGHT,10,0));UiTheme.Surface themes=new UiTheme.Surface(new FlowLayout(FlowLayout.CENTER,1,3),22);themes.background=true;themes.add(lightBtn);themes.add(systemBtn);themes.add(darkBtn);right.add(themes);right.add(settingsBtn);right.add(WindowChrome.controls(this,true));settingsBtn.setToolTipText("游戏设置");header.add(right,BorderLayout.EAST);root.add(header,BorderLayout.NORTH);WindowChrome.drag(this,header);
        homeTab.addActionListener(e->showHome());appearanceTab.addActionListener(e->showAppearance());settingsBtn.addActionListener(e->openSettings());
        ((Container)homeTab.getParent()).add(downloadsTab);downloadsTab.setPreferredSize(new Dimension(82,36));downloadsTab.addActionListener(e->openDownloads());
        lightBtn.setToolTipText("浅色模式");systemBtn.setToolTipText("跟随系统");darkBtn.setToolTipText("深色模式");lightBtn.addActionListener(e->setTheme("light"));systemBtn.addActionListener(e->setTheme("system"));darkBtn.addActionListener(e->setTheme("dark"));

        JPanel bottom=UiTheme.transparent(new GridBagLayout());bottom.setBorder(new EmptyBorder(0,24,24,24));
        GridBagConstraints b=new GridBagConstraints();b.gridx=0;b.gridy=0;b.anchor=GridBagConstraints.EAST;b.insets=new Insets(0,0,8,74);stopBtn.setEnabled(false);stopBtn.setToolTipText("没有运行中的游戏");stopBtn.addActionListener(e->stopGames());bottom.add(stopBtn,b);
        UiTheme.Surface dock=new UiTheme.Surface(new GridBagLayout(),30);dock.setBorder(new EmptyBorder(14,18,14,18));
        header.setName("mainHeader");dock.setName("launchDock");wallpaperSurfaces.addAll(java.util.List.of(header,dock,navPill,themes));updateTransparency();
        JPanel profile=UiTheme.transparent(new BorderLayout(10,0));profileControl=profile;profile.setName("accountSwitcher");profile.add(avatar,BorderLayout.WEST);JPanel profileText=UiTheme.transparent(new GridLayout(2,1,0,3));profileText.add(nameLabel);profileText.add(typeLabel);profile.add(profileText,BorderLayout.CENTER);profile.setPreferredSize(new Dimension(145,44));bindAccountPicker(profile);profile.setFocusable(true);
        profile.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0),"accounts");profile.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE,0),"accounts");profile.getActionMap().put("accounts",new AbstractAction(){@Override public void actionPerformed(ActionEvent e){showAccounts();}});
        GridBagConstraints d=new GridBagConstraints();d.gridx=0;d.gridy=0;d.anchor=GridBagConstraints.WEST;d.insets=new Insets(0,0,0,18);dock.add(profile,d);
        JPanel version=UiTheme.transparent(new BorderLayout(0,3));UiTheme.combo(versionBox);versionBox.setPreferredSize(new Dimension(200,36));versionBox.setMaximumRowCount(8);version.add(versionBox,BorderLayout.CENTER);version.add(loaderLabel,BorderLayout.SOUTH);
        versionBox.setRenderer((list,value,index,selected,focus)->{String name=value==null?"":value;String display=index<0&&name.matches("[0-9].*")?"Minecraft "+name.split("[- ]",2)[0]:name;JLabel label=UiTheme.label(display,13,false);label.setBorder(new EmptyBorder(8,12,8,10));if(ADD_VERSION.equals(name)||VERSION_SETTINGS.equals(name)||DELETE_VERSION.equals(name))label.setIcon(new Icon(){public int getIconWidth(){return 19;}public int getIconHeight(){return 19;}public void paintIcon(Component c,Graphics g,int x,int y){Graphics2D q=(Graphics2D)g.create();q.setColor(label.getForeground());UiTheme.icon(q,ADD_VERSION.equals(name)?"plus":DELETE_VERSION.equals(name)?"trash":"settings",x,y,17);q.dispose();}});label.setOpaque(true);label.setBackground(selected&&index>=0?UiTheme.p().accent():UiTheme.p().surface());label.setForeground(selected&&index>=0?UiTheme.p().accentText():UiTheme.p().text());return label;});
        d.gridx=1;d.weightx=1;d.fill=GridBagConstraints.HORIZONTAL;dock.add(version,d);
        JPanel state=UiTheme.transparent(new BorderLayout(0,6));state.setPreferredSize(new Dimension(100,38));state.add(statusLabel,BorderLayout.CENTER);progress.setPreferredSize(new Dimension(100,5));progress.setVisible(false);state.add(progress,BorderLayout.SOUTH);d.gridx=2;d.weightx=0;dock.add(state,d);
        launchBtn.setPreferredSize(new Dimension(150,46));launchBtn.addActionListener(e->onLaunch());d.gridx=3;d.insets=new Insets(0,0,0,0);d.fill=GridBagConstraints.NONE;dock.add(launchBtn,d);
        b.gridx=0;b.gridy=1;b.weightx=1;b.fill=GridBagConstraints.HORIZONTAL;b.insets=new Insets(0,0,0,0);bottom.add(dock,b);homeView.add(bottom,BorderLayout.SOUTH);
        homeView.add(announcement,BorderLayout.CENTER);pageHost.add(homeView,"home");root.add(pageHost,BorderLayout.CENTER);setContentPane(WindowChrome.content(this,root));
        versionBox.addActionListener(e->onVersionSelected());bindModDrop(getContentPane());WindowChrome.resize(this,getRootPane());refreshTheme();
    }
    private void bindModDrop(Component component){
        if(component instanceof JComponent target)target.setTransferHandler(new TransferHandler(){
            @Override public boolean canImport(TransferSupport support){return !busy&&!games.running()&&support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);}
            @Override public boolean importData(TransferSupport support){if(!canImport(support))return false;try{Object value=support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);if(value instanceof List<?> files){List<Path> paths=new java.util.ArrayList<>();for(Object file:files)if(file instanceof File f)paths.add(f.toPath());SwingUtilities.invokeLater(()->importDroppedMods(paths));return true;}}catch(Exception e){showError("无法添加 Mod",root(e));}return false;}
        });
        if(component instanceof Container container)for(Component child:container.getComponents())bindModDrop(child);
    }
    void importDroppedMods(List<Path> files){
        if(busy||games.running()||files.isEmpty())return;
        if(selectedVersion.isEmpty()){showError("请先添加游戏版本","在版本列表中选择“添加版本”，安装完成后再拖入 Mod。");return;}
        try{VersionSettingsDialog dialog=new VersionSettingsDialog(this,gameRoot,selectedVersion);dialog.importFiles(files);dialog.setVisible(true);}catch(Exception e){showError("无法添加 Mod",root(e));}
    }
    private void bootstrap(){
        announcement.checkOnStartup();
        refreshVersions();Session saved=SessionStore.load(home);if(saved!=null)session=saved;applySession();refreshAvatarAsync();
        statusLabel.setText(selectedVersion.isEmpty()?"请添加游戏版本":"● 已就绪");
        if(session==null)statusLabel.setText("添加游戏账号后可启动");
        poller=new Timer(700,e->poll());poller.start();
    }
    private void refreshVersions(){
        updatingVersions=true;try{List<String> versions=VersionScanner.scan(gameRoot);versionBox.removeAllItems();String last=selectedVersion.isEmpty()?Settings.load(home).lastVersion:selectedVersion;selectedVersion=versions.contains(last)?last:versions.isEmpty()?"":versions.get(0);if(versions.isEmpty())versionBox.addItem(NO_VERSION);else for(String version:versions)versionBox.addItem(version);versionBox.addItem(ADD_VERSION);if(!selectedVersion.isEmpty()){versionBox.addItem(VERSION_SETTINGS);versionBox.addItem(DELETE_VERSION);}versionBox.setSelectedItem(selectedVersion.isEmpty()?NO_VERSION:selectedVersion);versionBox.setEnabled(!busy);}finally{updatingVersions=false;}updateLoader();
    }
    private void onVersionSelected(){if(updatingVersions)return;Object value=versionBox.getSelectedItem();if(value==null)return;String choice=value.toString();if(ADD_VERSION.equals(choice)||VERSION_SETTINGS.equals(choice)||DELETE_VERSION.equals(choice)){updatingVersions=true;versionBox.setSelectedItem(selectedVersion.isEmpty()?NO_VERSION:selectedVersion);updatingVersions=false;SwingUtilities.invokeLater(()->{if(ADD_VERSION.equals(choice))addVersion();else if(DELETE_VERSION.equals(choice))deleteVersion();else versionSettings();});return;}if(!NO_VERSION.equals(choice))selectedVersion=choice;updateLoader();}
    private void deleteVersion(){
        if(busy||selectedVersion.isEmpty())return;if(games.running()){showError("请先退出游戏","所有客户端退出后可删除版本。");return;}String version=selectedVersion;
        if(WindowChrome.prompt(this,"删除游戏版本","删除版本“"+version+"”？\n\n版本文件夹会移至 .minecraft/.mx-version-trash。独立版本中的 Mod、配置和存档会一起移入，可手动找回。",true)!=JOptionPane.OK_OPTION)return;
        setBusy(true,"正在删除版本…");new SwingWorker<Path,Void>(){
            @Override protected Path doInBackground()throws Exception{return VersionManager.remove(gameRoot,version);}
            @Override protected void done(){try{get();selectedVersion="";refreshVersions();Settings settings=Settings.load(home);settings.lastVersion=selectedVersion;settings.save(home);setBusy(false,"版本已移入回收目录");}catch(Exception e){setBusy(false,"版本未删除");showError("无法删除版本",root(e));}}
        }.execute();
    }
    void addVersion(){if(busy)return;InstallVersionDialog dialog=new InstallVersionDialog(this,gameRoot,id->{selectedVersion=id;refreshVersions();Settings settings=Settings.load(home);settings.lastVersion=id;settings.save(home);statusLabel.setText("● 安装完成");});dialog.setVisible(true);}
    void openDownloads(){
        if(busy||games.running()){showError("请先退出游戏","退出游戏后可下载并安装资源。");return;}
        if(downloadCenter==null){downloadCenter=new DownloadCenterPanel(this,gameRoot,selectedVersion,id->{selectedVersion=id;refreshVersions();Settings settings=Settings.load(home);settings.lastVersion=id;settings.save(home);},network);pageHost.add(downloadCenter,"downloads");}
        downloadCenter.selectInstance(selectedVersion);appearanceVisible=false;downloadsVisible=true;if(appearancePanel!=null)appearancePanel.setActive(false);pages.show(pageHost,"downloads");syncPlayback();refreshTheme();
    }
    private void versionSettings(){if(busy)return;if(games.running()){showError("请先退出游戏","所有客户端退出后可修改此版本的 Mod。");return;}if(selectedVersion.isEmpty()){addVersion();return;}try{new VersionSettingsDialog(this,gameRoot,selectedVersion).setVisible(true);}catch(Exception e){showError("无法打开版本设置",root(e));}}
    private void updateLoader(){
        Object value=versionBox.getSelectedItem();if(value==null)return;if(selectedVersion.isEmpty()){loaderLabel.setText("展开列表添加游戏版本");return;}loaderLabel.setText("本地游戏版本");
        String name=value.toString();try{Map<String,Object> meta=Json.parseObject(Files.readString(gameRoot.resolve("versions").resolve(name).resolve(name+".json")));String chosen=ContentCatalog.installedLoader(meta);loaderLabel.setText(chosen.isBlank()?"Minecraft 原版":chosen);Object libraries=meta.get("libraries");if(libraries instanceof List<?> list)for(Object entry:list)if(entry instanceof Map<?,?> lib){String coordinate=String.valueOf(lib.get("name"));if(coordinate.startsWith("net.fabricmc:fabric-loader:"))loaderLabel.setText("Fabric "+coordinate.substring(coordinate.lastIndexOf(':')+1));}}catch(Exception ignored){}
        versionBox.setToolTipText(name);
    }
    private void applySession(){if(accountCleared){nameLabel.setText("添加游戏账号");nameLabel.setToolTipText("点击添加账号");typeLabel.setText("尚未选择账号");return;}String display=session.name;FontMetrics fm=nameLabel.getFontMetrics(nameLabel.getFont());while(display.length()>1&&fm.stringWidth(display)>96)display=display.substring(0,display.offsetByCodePoints(display.length(),-1));nameLabel.setText(display.equals(session.name)?display:display+"…");nameLabel.setToolTipText(session.name+" · 点击切换账号");typeLabel.setText(session.premium?"Minecraft 正版账号":"Minecraft 离线账号");}
    private void bindAccountPicker(Component component){component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));component.addMouseListener(new MouseAdapter(){@Override public void mouseClicked(MouseEvent e){if(SwingUtilities.isLeftMouseButton(e))showAccounts();}});if(component instanceof Container container)for(Component child:container.getComponents())bindAccountPicker(child);}
    void showAccounts(){if(busy)return;new AccountPopup(home,accounts.available(accountCleared?null:session),accountCleared?null:session,this::switchAccount,this::removeAccount,this::configureSkin,this::onLogin).showAbove(profileControl);}
    private void configureSkin(Session selected){
        if(selected.premium){try{SkinFetcher.invalidate(selected,home);}catch(Exception e){showError("无法刷新皮肤",root(e));return;}if(!accountCleared&&AccountStore.key(selected).equals(AccountStore.key(session)))refreshAvatarAsync();showAccounts();return;}
        FileDialog chooser=new FileDialog(this,"选择此账号的 Minecraft PNG 皮肤",FileDialog.LOAD);chooser.setFile("*.png");chooser.setVisible(true);String file=chooser.getFile(),directory=chooser.getDirectory();chooser.dispose();if(file==null)return;
        new SwingWorker<BufferedImage,Void>(){
            @Override protected BufferedImage doInBackground()throws Exception{return SkinFetcher.importSkin(selected,home,Path.of(directory,file));}
            @Override protected void done(){if(disposed)return;try{BufferedImage head=get();if(!accountCleared&&AccountStore.key(selected).equals(AccountStore.key(session)))avatar.setImage(head);statusLabel.setText("皮肤头像已更新");showAccounts();}catch(Exception e){showError("皮肤未导入",root(e));}}
        }.execute();
    }
    private void removeAccount(Session selected){
        if(WindowChrome.prompt(this,"删除账号","从启动器移除账号“"+selected.name+"”？\n只移除本机保存的登录信息。",true)!=JOptionPane.OK_OPTION)return;
        try{accounts.forget(selected);}catch(Exception e){showError("账号未删除",root(e));return;}
        if(!accountCleared&&AccountStore.key(selected).equals(AccountStore.key(session)))selectRemainingAccount();
        statusLabel.setText(games.running()?"游戏运行中 · 账号已移除":"账号已移除");
    }
    private void selectRemainingAccount(){List<Session> remaining=accounts.available(null);session=remaining.isEmpty()?null:remaining.get(0);accountCleared=session==null;if(accountCleared)SessionStore.clear(home);else SessionStore.save(home,session);applySession();refreshAvatarAsync();}
    private void switchAccount(Session selected){useAccount(selected,true);}
    private void useAccount(Session selected,boolean keepCurrent){
        try{if(keepCurrent&&!accountCleared)accounts.remember(session);accounts.remember(selected);}catch(Exception e){showError("账号未保存",root(e));return;}
        session=selected;accountCleared=false;SessionStore.save(home,session);applySession();refreshAvatarAsync();statusLabel.setText(games.running()?"游戏运行中 · 账号已切换":"账号已切换");
    }
    private void refreshAvatarAsync(){Session selected=session;if(selected==null){avatar.setImage(null);avatar.setLoading(false);return;}avatar.setImage(SkinFetcher.cached(selected,home));avatar.setLoading(true);new SwingWorker<BufferedImage,Void>(){
        @Override protected BufferedImage doInBackground(){return SkinFetcher.fetch(selected,home);}
        @Override protected void done(){if(disposed)return;try{if(session==selected){avatar.setImage(get());avatar.setLoading(false);}}catch(Exception ignored){if(session==selected)avatar.setLoading(false);}}
    }.execute();}
    private void onLaunch(){
        if(busy)return;launchGame();
    }
    private void launchGame(){
        if(busy)return;if(!requireGameAccount())return;
        if(selectedVersion.isEmpty()){addVersion();return;}
        String version=selectedVersion;Path launchDirectory;try{launchDirectory=VersionInstaller.gameDirectory(gameRoot,version);}catch(Exception e){showError("版本未就绪",root(e));return;}
        Settings settings=Settings.load(home);settings.lastVersion=version;settings.save(home);setBusy(true,"正在启动游戏…");Session account=session;int memory=MemoryAllocation.resolve(gameRoot,version,settings);
        new SwingWorker<Integer,Void>(){
            @Override protected Integer doInBackground()throws Exception{return GameLauncher.launch(launchDirectory,version,account,memory);}
            @Override protected void done(){try{gameStarted(get());}catch(Exception e){if(!disposed){setBusy(false,"启动失败");showError("启动失败",root(e));}}}
        }.execute();
    }
    private boolean requireGameAccount(){if(session!=null)return true;statusLabel.setText("请添加游戏账号");onLogin();return session!=null;}
    void gameStarted(long pid){games.add(pid);lastGameCount=games.count();if(!disposed)setBusy(false,lastGameCount>0?"运行中 · "+lastGameCount+" 个客户端":"游戏已退出");}
    private void stopGames(){if(busy||!games.running())return;setBusy(true,"正在结束游戏…");new SwingWorker<Void,Void>(){
        @Override protected Void doInBackground()throws Exception{games.stopAll();return null;}
        @Override protected void done(){if(disposed)return;try{get();lastGameCount=games.count();setBusy(false,"游戏已结束");}catch(Exception e){setBusy(false,"部分游戏未结束");showError("无法结束游戏",root(e));}}
    }.execute();}
    private void onLogin(){
        if(busy)return;AccountDialog dialog=new AccountDialog(this,home,accountCleared?null:session);UiTheme.refresh(dialog);dialog.setVisible(true);
        if(dialog.signOutRequested()){try{if(session!=null)accounts.forget(session);}catch(Exception e){showError("无法退出账号",root(e));return;}selectRemainingAccount();return;}
        Session picked;
        if(dialog.premiumRequested()){MicrosoftLogin.LoginResult result=MicrosoftLogin.login(this,session);if(result==null)return;picked=result.session;}
        else{picked=dialog.result();if(picked==null)return;}
        useAccount(picked,true);
    }
    private void openSettings(){if(busy)return;SettingsDialog dialog=new SettingsDialog(this,home,gameRoot);UiTheme.refresh(dialog);dialog.setVisible(true);if(dialog.changed()){session=SessionStore.load(home);accountCleared=session==null;applySession();refreshVersions();refreshAvatarAsync();}}
    private void poll(){
        if(disposed)return;
        if(busy)return;
        int count=games.count();if(count!=lastGameCount){lastGameCount=count;statusLabel.setText(count>0?"运行中 · "+count+" 个客户端":"● 已就绪");updatePower();syncPlayback();}
    }
    private void updatePower(){int count=games.count();stopBtn.setEnabled(!busy&&count>0);stopBtn.setToolTipText(count>0?"结束全部游戏（"+count+" 个客户端）":"没有运行中的游戏");}
    private void setBusy(boolean value,String text){busy=value;launchBtn.setEnabled(!value);launchBtn.setText("启动游戏");updatePower();settingsBtn.setEnabled(!value);versionBox.setEnabled(!value);statusLabel.setText(text);if(!value)progress.setVisible(false);syncPlayback();}
    private void setTheme(String mode){
        String previous=appearance.theme;appearance.theme=mode;try{appearance.save(home);}catch(Exception e){appearance.theme=previous;showError("外观未保存","无法写入启动器目录，请检查文件权限。 ");return;}UiTheme.set(mode);refreshTheme();
    }
    private void refreshTheme(){
        MenuSelectionManager.defaultManager().clearSelectedPath();
        homeTab.selected=!appearanceVisible&&!downloadsVisible;appearanceTab.selected=appearanceVisible;downloadsTab.selected=downloadsVisible;lightBtn.selected="light".equals(appearance.theme);systemBtn.selected="system".equals(appearance.theme);darkBtn.selected="dark".equals(appearance.theme);UiTheme.refresh(getContentPane());getContentPane().revalidate();getContentPane().repaint();
        try{if(isDisplayable()){com.sun.jna.platform.win32.WinDef.HWND hwnd=new com.sun.jna.platform.win32.WinDef.HWND(com.sun.jna.Native.getComponentPointer(this));int[] dark={UiTheme.p().dark()?1:0};Dwm.INSTANCE.DwmSetWindowAttribute(hwnd,20,dark,4);}}catch(Throwable ignored){}
    }
    private void updateTransparency(){for(UiTheme.Surface surface:wallpaperSurfaces){surface.transparency=appearance.uiTransparency;surface.repaint();}}
    private interface Dwm extends com.sun.jna.Library{Dwm INSTANCE=com.sun.jna.Native.load("dwmapi",Dwm.class);int DwmSetWindowAttribute(com.sun.jna.platform.win32.WinDef.HWND hwnd,int attribute,int[] value,int size);}
    private void showHome(){if(downloadCenter!=null&&downloadCenter.busy()){showError("下载任务进行中","请先完成或取消下载页中的任务。");return;}appearanceVisible=false;downloadsVisible=false;if(appearancePanel!=null)appearancePanel.setActive(false);pages.show(pageHost,"home");syncPlayback();refreshTheme();}
    void showAppearance(){
        if(downloadCenter!=null&&downloadCenter.busy())return;downloadsVisible=false;
        if(appearancePanel!=null){appearancePanel.close();pageHost.remove(appearancePanel);}
        appearancePanel=new AppearancePanel(library,appearance,this::applyAppearance,this::showHome);pageHost.add(appearancePanel,"appearance");appearanceVisible=true;pages.show(pageHost,"appearance");appearancePanel.setActive((getExtendedState()&ICONIFIED)==0);syncPlayback();refreshTheme();
    }
    private boolean applyAppearance(Appearance value){
        value.theme=appearance.theme;try{value.save(home);}catch(Exception e){showError("壁纸未保存","无法写入外观设置，请检查启动器目录权限。");return false;}
        appearance=value;backdrop.options(value);updateTransparency();wallpaper.select(library.find(value.wallpaper),value);syncPlayback();return true;
    }
    private void syncPlayback(){
        boolean minimized=(getExtendedState()&ICONIFIED)!=0;boolean game=games.running();
        wallpaper.setPaused(minimized||appearanceVisible||downloadsVisible||(appearance.pauseWithGame&&(game||busy)));
        if(appearancePanel!=null)appearancePanel.setActive(appearanceVisible&&!minimized&&!(appearance.pauseWithGame&&game));
    }
    private void disposeResources(){if(disposed)return;disposed=true;announcement.close();if(poller!=null)poller.stop();if(systemThemeTimer!=null)systemThemeTimer.stop();wallpaper.close();if(appearancePanel!=null)appearancePanel.close();if(downloadCenter!=null)downloadCenter.close();}
    @Override public void dispose(){disposeResources();super.dispose();}
    private void showError(String title,String body){WindowChrome.prompt(this,title,body,false);}
    private static String root(Throwable e){while(e.getCause()!=null&&e.getCause()!=e)e=e.getCause();return e.getMessage()==null?"操作未完成，请重试。":e.getMessage();}
    Path homeDir(){return home;}String debugStatusText(){return statusLabel.getText();}String debugVersionText(){Object selected=versionBox.getSelectedItem();return selected==null?"":selected.toString();}
    AppearancePanel appearancePanel(){return appearancePanel;}
    DownloadCenterPanel downloadCenter(){return downloadCenter;}
    void previewTheme(String theme){setTheme(theme);}
    WallpaperPlayer wallpaperPlayer(){return wallpaper;}
    private static final class AvatarPanel extends JPanel {
        BufferedImage image;boolean loading;
        AvatarPanel(){setOpaque(false);setPreferredSize(new Dimension(42,42));}
        void setImage(BufferedImage image){this.image=image;repaint();}void setLoading(boolean value){loading=value;repaint();}
        @Override protected void paintComponent(Graphics graphics){Graphics2D g=(Graphics2D)graphics.create();UiTheme.quality(g);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);g.clip(new java.awt.geom.RoundRectangle2D.Double(0,0,42,42,12,12));g.setColor(UiTheme.p().line());g.fillRect(0,0,42,42);if(image!=null)g.drawImage(image,0,0,42,42,null);else{g.setColor(UiTheme.p().muted());UiTheme.icon(g,"user",11,10,21);}g.setClip(null);g.setColor(loading?UiTheme.p().muted():new Color(0x45B982));g.fillOval(33,33,9,9);g.setColor(UiTheme.p().surface());g.drawOval(33,33,9,9);g.dispose();}
    }
}

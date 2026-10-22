package dev.mx.launcher;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/** One content browser for mods, shaders, resource packs and complete instances. */
final class ContentBrowserPanel extends JPanel implements AutoCloseable {
    private final Window owner;
    private final boolean network;
    private boolean closed;
    private Runnable onClose=()->{};
    private final JComboBox<ContentCatalog.Category> category=new JComboBox<>();
    private final JComboBox<String> sort=new JComboBox<>(new String[]{"综合排序","下载最多","最近更新","最新发布"});
    private final JComboBox<String> world=new JComboBox<>();
    private final Map<String,ImageIcon> icons=new HashMap<>();
    private int categoryRequest;
    private final Path gameRoot;
    private final Consumer<String> installed;
    private final JComboBox<ContentCatalog.Source> source=new JComboBox<>(ContentCatalog.Source.values());
    private final JComboBox<ContentCatalog.Kind> kind=new JComboBox<>(ContentCatalog.Kind.values());
    private final JComboBox<String> target=new JComboBox<>(),game=new JComboBox<>(),loader=new JComboBox<>(new String[]{"","fabric","quilt","forge","neoforge"});
    private final JCheckBox tests=new UiTheme.Toggle("包含 Beta / Alpha",true),dependencies=new UiTheme.Toggle("自动下载必需依赖",true);
    private final JTextField query=new JTextField();
    private final DefaultListModel<ContentCatalog.Project> projects=new DefaultListModel<>();
    private final JList<ContentCatalog.Project> results=new JList<>(projects){@Override public boolean getScrollableTracksViewportWidth(){return true;}};
    private final JComboBox<ContentCatalog.FileVersion> files=new JComboBox<>();
    private final JTextArea description=new JTextArea();
    private final JLabel status=UiTheme.muted("输入名称搜索，或直接浏览热门资源。",12);
    private final JProgressBar progress=new UiTheme.ProgressBar();
    private final UiTheme.Button search=new UiTheme.Button("搜索","",UiTheme.Tone.PRIMARY),download=new UiTheme.Button("下载并安装","download",UiTheme.Tone.PRIMARY),cancel=new UiTheme.Button("关闭","",UiTheme.Tone.SURFACE);
    private final UiTheme.Button previous=new UiTheme.Button("上一页","",UiTheme.Tone.SURFACE),next=new UiTheme.Button("下一页","",UiTheme.Tone.SURFACE);
    private final List<JComponent> controls=new ArrayList<>();
    private ContentCatalog.Project project;
    private List<ContentCatalog.FileVersion> versions=List.of();
    private int offset,fileOffset,total,selectionRequest;
    private boolean pageMore;
    private volatile VersionInstaller workerInstaller;
    private boolean busy;
    private boolean installTask;
    private SwingWorker<?,?> activeTask;
    private volatile boolean cancelled;

    ContentBrowserPanel(Window owner,Path gameRoot,String selectedVersion,Consumer<String> installed){this(owner,gameRoot,selectedVersion,installed,true);}
    ContentBrowserPanel(Window owner,Path gameRoot,String selectedVersion,Consumer<String> installed,boolean network){
        super(new BorderLayout());this.owner=owner;this.network=network;this.gameRoot=gameRoot;this.installed=installed;
        UiTheme.Surface root=new UiTheme.Surface(new BorderLayout(0,14),20);root.setBorder(new EmptyBorder(18,18,16,18));
        JPanel top=UiTheme.transparent(new BorderLayout(0,12));
        JPanel filters=UiTheme.transparent(new GridLayout(0,4,10,8));
        for(JComboBox<?> box:List.of(source,kind,target,game,loader,files,category,sort,world))UiTheme.combo(box);
        source.setSelectedItem(ContentCatalog.Source.ALL);category.addItem(null);world.addItem("选择目标存档");
        category.setRenderer((list,value,index,selected,focus)->{JLabel label=UiTheme.label(value==null?"全部类型":value.title()+" · "+value.source(),12,false);label.setOpaque(true);label.setBorder(new EmptyBorder(8,10,8,10));label.setBackground(selected?UiTheme.p().line():UiTheme.p().surface());return label;});
        target.addItem("请选择目标实例");for(String version:VersionScanner.scan(gameRoot))target.addItem(version);if(selectedVersion!=null&&!selectedVersion.isBlank())target.setSelectedItem(selectedVersion);
        game.setEditable(true);game.addItem("");
        filters.add(field("来源",source));filters.add(field("类型标签",category));filters.add(field("Minecraft 版本（可输入）",game));filters.add(field("排序",sort));
        filters.add(field("安装到实例",target));filters.add(field("模组加载器",loader));filters.add(field("数据包目标存档",world));
        UiTheme.Button credential=new UiTheme.Button("CurseForge 密钥","settings",UiTheme.Tone.SURFACE);credential.addActionListener(e->configureKey());filters.add(credential);
        JPanel searchRow=UiTheme.transparent(new BorderLayout(10,0));query.setFont(UiTheme.font(13,false));query.setName("resourceQuery");query.setToolTipText("输入资源名称，留空浏览热门资源");searchRow.add(query,BorderLayout.CENTER);
        JPanel searchActions=UiTheme.transparent(new FlowLayout(FlowLayout.RIGHT,8,0));UiTheme.Button reset=new UiTheme.Button("重置条件","",UiTheme.Tone.SURFACE);searchActions.add(reset);searchActions.add(search);searchRow.add(searchActions,BorderLayout.EAST);
        reset.addActionListener(e->{query.setText("");source.setSelectedItem(ContentCatalog.Source.ALL);game.setSelectedItem("");loader.setSelectedIndex(0);category.setSelectedIndex(0);sort.setSelectedIndex(0);tests.setSelected(true);offset=0;search();});
        JPanel filterHost=UiTheme.transparent(new BorderLayout(0,12));filterHost.add(filters,BorderLayout.CENTER);filterHost.add(searchRow,BorderLayout.SOUTH);top.add(filterHost,BorderLayout.CENTER);root.add(top,BorderLayout.NORTH);
        results.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);results.setFixedCellHeight(100);results.setBackground(UiTheme.p().surface());results.setName("resourceResults");
        results.setCellRenderer((list,value,index,selected,focus)->resourceRow(value,selected));
        JScrollPane listScroll=new JScrollPane(results);listScroll.setBorder(null);listScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);UiTheme.scroll(listScroll);
        JPanel details=UiTheme.transparent(new BorderLayout(0,14));description.setEditable(false);description.setLineWrap(true);description.setWrapStyleWord(true);description.setFont(UiTheme.font(13,false));description.setBackground(UiTheme.p().surface());description.setForeground(UiTheme.p().text());description.setBorder(new EmptyBorder(14,16,14,16));
        JScrollPane body=new JScrollPane(description);body.setBorder(null);UiTheme.scroll(body);details.add(body,BorderLayout.CENTER);
        JPanel versionActions=UiTheme.transparent(new BorderLayout(0,10));files.setPreferredSize(new Dimension(340,38));versionActions.add(files,BorderLayout.NORTH);
        JPanel actions=UiTheme.transparent(new GridLayout(2,2,8,8));UiTheme.Button website=new UiTheme.Button("原项目页面","",UiTheme.Tone.SURFACE),moreFiles=new UiTheme.Button("更多文件","",UiTheme.Tone.SURFACE),save=new UiTheme.Button("仅下载文件","download",UiTheme.Tone.SURFACE);actions.add(moreFiles);actions.add(website);actions.add(save);actions.add(download);versionActions.add(actions,BorderLayout.CENTER);details.add(versionActions,BorderLayout.SOUTH);save.addActionListener(e->saveSelected());
        JSplitPane split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,listScroll,details);split.setOpaque(false);split.setBorder(null);split.setDividerLocation(520);split.setResizeWeight(0.60);listScroll.setMinimumSize(new Dimension(350,120));details.setMinimumSize(new Dimension(290,120));root.add(split,BorderLayout.CENTER);
        JPanel footer=UiTheme.transparent(new BorderLayout(0,10));footer.add(status,BorderLayout.NORTH);progress.setPreferredSize(new Dimension(100,7));progress.setVisible(false);footer.add(progress,BorderLayout.CENTER);
        JPanel navigation=UiTheme.transparent(new FlowLayout(FlowLayout.LEFT,8,0));UiTheme.Button local=new UiTheme.Button("导入整合包","folder",UiTheme.Tone.SURFACE);navigation.add(local);navigation.add(previous);navigation.add(next);navigation.add(cancel);
        JPanel options=UiTheme.transparent(new GridLayout(1,2,12,0));tests.setPreferredSize(new Dimension(155,28));dependencies.setPreferredSize(new Dimension(170,28));options.add(tests);options.add(dependencies);JPanel footerRow=UiTheme.transparent(new BorderLayout(12,0));footerRow.add(navigation,BorderLayout.WEST);footerRow.add(options,BorderLayout.EAST);footer.add(footerRow,BorderLayout.SOUTH);root.add(footer,BorderLayout.SOUTH);
        controls.addAll(List.of(source,kind,target,game,loader,tests,dependencies,credential,query,search,results,files,website,moreFiles,download,previous,next,local));
        controls.addAll(List.of(category,sort,world,reset,save));source.addActionListener(e->loadCategories());kind.addActionListener(e->loadCategories());
        search.addActionListener(e->{offset=0;search();});query.addActionListener(e->search.doClick());
        previous.addActionListener(e->{if(offset>=20){offset-=20;search();}});next.addActionListener(e->{offset+=20;search();});previous.setEnabled(false);next.setEnabled(false);
        results.addListSelectionListener(e->{if(!e.getValueIsAdjusting()&&!busy){project=results.getSelectedValue();fileOffset=0;loadFiles();}});
        moreFiles.addActionListener(e->{if(project!=null&&project.source()==ContentCatalog.Source.CURSEFORGE){fileOffset+=50;loadFiles();}});
        tests.addActionListener(e->filterFiles());target.addActionListener(e->targetChanged());
        files.addActionListener(e->{var file=(ContentCatalog.FileVersion)files.getSelectedItem();files.setToolTipText(file==null?null:file.filename()+" · "+file.release()+" · "+String.join(", ",file.games())+" · "+String.join(", ",file.loaders()));});
        website.addActionListener(e->{if(project!=null)try{Desktop.getDesktop().browse(URI.create(project.website()));}catch(Exception failure){error(failure);}});
        download.addActionListener(e->installSelected());local.addActionListener(e->importPack());
        cancel.addActionListener(e->leave());
        UiTheme.refresh(root);add(root,BorderLayout.CENTER);targetChanged();world.setEnabled(false);loadCategories();
        if(network)new SwingWorker<List<DownloadCatalog.Game>,Void>(){protected List<DownloadCatalog.Game> doInBackground()throws Exception{return new DownloadCatalog().games();}protected void done(){if(closed||!isDisplayable())return;try{String current=gameFilter();for(var item:get())game.addItem(item.id());game.setSelectedItem(current);}catch(Exception ignored){}}}.execute();
    }
    private JPanel field(String label,JComponent value){
        String shortName=switch(label){case "类型标签"->"类型";case "Minecraft 版本（可输入）"->"游戏版本";case "安装到实例"->"目标实例";case "模组加载器"->"加载器";case "数据包目标存档"->"目标存档";default->label;};
        JPanel panel=UiTheme.transparent(new BorderLayout(7,0));JLabel title=UiTheme.muted(shortName,11);title.setPreferredSize(new Dimension(52,36));panel.add(title,BorderLayout.WEST);panel.add(value,BorderLayout.CENTER);panel.setPreferredSize(new Dimension(200,36));return panel;
    }
    private ContentCatalog catalog(){return new ContentCatalog(DownloadCredentials.load());}
    private String gameFilter(){return Objects.toString(game.getEditor().getItem(),"").strip();}
    private String loaderFilter(){return Objects.toString(loader.getSelectedItem(),"");}
    private void targetChanged(){
        world.removeAllItems();world.addItem("选择目标存档");
        if(target.getSelectedIndex()<=0)return;try{String version=target.getSelectedItem().toString();Path instance=VersionInstaller.gameDirectory(gameRoot,version);Map<String,Object> meta=Json.parseObject(Files.readString(instance.resolve("versions").resolve(version).resolve(version+".json")));
            game.setSelectedItem(Objects.toString(meta.get("mxcloudMinecraft"),version.split("-",2)[0]));loader.setSelectedItem(ContentCatalog.installedLoader(meta));
            Path saves=instance.resolve("saves");if(Files.isDirectory(saves))try(var folders=Files.list(saves)){for(Path folder:folders.filter(p->!Files.isSymbolicLink(p)&&Files.isRegularFile(p.resolve("level.dat"))).sorted().toList())world.addItem(folder.getFileName().toString());}
        }catch(Exception ignored){}
    }
    private void search(){
        ContentCatalog.Source selected=(ContentCatalog.Source)source.getSelectedItem();ContentCatalog.Kind category=(ContentCatalog.Kind)kind.getSelectedItem();String text=query.getText(),game=gameFilter(),loader=loaderFilter();
        var tag=(ContentCatalog.Category)this.category.getSelectedItem();String ordering=new String[]{"relevance","downloads","updated","newest"}[sort.getSelectedIndex()];
        runTask(()->catalog().search(selected,category,text,game,loader,offset,tag,ordering),page->{total=page.total();pageMore=page.more();projects.clear();project=null;files.removeAllItems();versions=List.of();description.setText("选择左侧资源查看介绍和兼容文件。\n\n数据包需安装到具体存档；光影需要相应渲染模组。");for(var value:page.projects())projects.addElement(value);previous.setEnabled(offset>0);next.setEnabled(pageMore);status.setText((projects.isEmpty()?"暂无匹配资源":"共 "+total+" 项 · 第 "+(offset/20+1)+" 页")+(page.notice().isEmpty()?"":" · "+page.notice()));status.setToolTipText(page.notice());loadIcons(page.projects());},"正在搜索资源…");
    }
    private void loadFiles(){
        ContentCatalog.Project chosen=project;if(chosen==null)return;int request=++selectionRequest;description.setText(chosen.title()+"\n作者："+chosen.author()+"\n来源："+chosen.source()+" · "+compact(chosen.downloads())+" 次下载\n更新："+chosen.updated()+"\n类型："+String.join(" / ",chosen.tags().stream().map(ContentCatalog::categoryName).toList())+"\n支持版本："+String.join(", ",chosen.games())+"\n\n"+chosen.description());description.setCaretPosition(0);
        String game=gameFilter(),loader=loaderFilter();
        runTask(()->catalog().versions(chosen,game,loader,fileOffset),values->{if(request!=selectionRequest)return;if(fileOffset==0)versions=values;else{var combined=new ArrayList<>(versions);combined.addAll(values);versions=combined;}filterFiles();status.setText("已读取 "+versions.size()+" 个文件版本。下载前请确认目标实例。");},"正在读取可下载版本…");
    }
    private void filterFiles(){files.removeAllItems();for(var version:versions)if(tests.isSelected()||"release".equals(version.release()))files.addItem(version);}
    private void installSelected(){
        var file=(ContentCatalog.FileVersion)files.getSelectedItem();ContentCatalog.Project chosen=project;if(file==null||chosen==null)return;
        String packName=chosen.kind()==ContentCatalog.Kind.PACK?packName():null;if(chosen.kind()==ContentCatalog.Kind.PACK&&packName==null)return;
        String targetId=Objects.toString(target.getSelectedItem(),"");if(chosen.kind()!=ContentCatalog.Kind.PACK&&target.getSelectedIndex()<=0){status.setText("请先选择要安装到的游戏实例。");return;}
        String game=gameFilter(),loader=loaderFilter();boolean withDependencies=dependencies.isSelected(),withTests=tests.isSelected();String selectedWorld=world.getSelectedIndex()>0?Objects.toString(world.getSelectedItem(),""):"";
        if(chosen.kind()==ContentCatalog.Kind.DATA&&selectedWorld.isEmpty()){status.setText("请选择数据包要安装到的存档；也可以仅下载文件。");return;}
        runInstall(installer->{
            ContentCatalog catalog=catalog();
            if(chosen.kind()==ContentCatalog.Kind.PACK){
                Path packCache=gameRoot.resolve(".mx-packs");Path archive=catalog.download(file,packCache,installer);return ModpackInstaller.install(archive,packName,gameRoot,installer,catalog);
            }
            Path instance=VersionInstaller.gameDirectory(gameRoot,targetId);
            var metadata=Json.parseObject(Files.readString(instance.resolve("versions").resolve(targetId).resolve(targetId+".json")));
            String actualGame=Objects.toString(metadata.get("mxcloudMinecraft"),targetId.split("-",2)[0]),actualLoader=ContentCatalog.installedLoader(metadata);
            if(!file.games().contains(actualGame))throw new IOException("资源不兼容目标实例的 Minecraft "+actualGame);
            if(chosen.kind()==ContentCatalog.Kind.MOD&&!file.loaders().contains(actualLoader))throw new IOException("模组不兼容目标实例的加载器："+actualLoader);
            List<ContentCatalog.FileVersion> plan=chosen.kind()==ContentCatalog.Kind.MOD&&withDependencies?catalog.resolve(file,actualGame,actualLoader,withTests):List.of(file);
            Path destination=resourceDirectory(instance,chosen.kind(),selectedWorld);Files.createDirectories(destination);Path stage=Files.createTempDirectory(gameRoot,".resource-download-");
            List<Path> committed=new ArrayList<>();
            try{
                for(var dependency:plan)catalog.download(dependency,stage,installer);
                for(var dependency:plan){Path target=VersionInstaller.safe(destination,dependency.filename());if(Files.exists(target)&&!VersionInstaller.valid(target,new VersionInstaller.Download(dependency.url(),target,dependency.algorithm(),dependency.hash(),dependency.size())))throw new IOException("存在不同内容的同名文件，不会覆盖："+dependency.filename());}
                for(var dependency:plan){installer.checkCancelled();Path target=VersionInstaller.safe(destination,dependency.filename());if(!Files.exists(target)){Files.move(stage.resolve(dependency.filename()),target);committed.add(target);}}
            }catch(Exception failure){for(Path target:committed)Files.deleteIfExists(target);throw failure;}
            finally{try(var children=Files.list(stage)){for(Path path:children.toList())Files.deleteIfExists(path);}Files.deleteIfExists(stage);}
            return targetId;
        });
    }
    private String packName(){String text=JOptionPane.showInputDialog(this,"新整合包实例名称（中文、字母、数字、空格及 . _ + -）", "Pack-"+System.currentTimeMillis());return text==null||text.isBlank()?null:text.strip();}
    private void importPack(){FileDialog choose=chooser("选择 .mrpack 或 CurseForge 整合包 ZIP",FileDialog.LOAD);choose.setVisible(true);if(choose.getFile()==null)return;Path archive=Path.of(choose.getDirectory(),choose.getFile());choose.dispose();String name=packName();if(name==null)return;runInstall(installer->ModpackInstaller.install(archive,name,gameRoot,installer,catalog()));}
    private void runInstall(InstallVersionDialog.Action action){
        installTask=true;
        runTask(()->{workerInstaller=new VersionInstaller(gameRoot,p->SwingUtilities.invokeLater(()->{if(isDisplayable()){status.setText(p.message());progress.setIndeterminate(p.total()==0);if(p.total()>0)progress.setValue(p.completed()*1000/p.total());}}));if(cancelled)workerInstaller.cancel();return action.run(workerInstaller);},id->{installed.accept(id);status.setText("下载与安装完成："+id);},"正在准备下载与安装…");
    }
    private void configureKey(){JPasswordField input=new JPasswordField(32);JPanel panel=UiTheme.transparent(new BorderLayout(0,10));panel.add(UiTheme.muted("填写 CurseForge 官方 API Key，仅加密保存在当前 Windows 用户本机。",12),BorderLayout.NORTH);panel.add(input,BorderLayout.CENTER);if(JOptionPane.showConfirmDialog(this,panel,"配置 CurseForge API Key",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION){char[] chars=input.getPassword();try{DownloadCredentials.save(new String(chars));status.setText(chars.length==0?"已清除本机密钥。":"密钥已加密保存，可选择 CurseForge 搜索。");}catch(Exception failure){error(failure);}finally{Arrays.fill(chars,'\0');}}}
    @FunctionalInterface private interface Work<T>{T run()throws Exception;}
    private <T>void runTask(Work<T> work,Consumer<T> done,String message){
        if(busy)return;busy=true;cancelled=false;controls.forEach(c->c.setEnabled(false));cancel.setText("取消");cancel.setEnabled(true);status.setText(message);progress.setVisible(true);progress.setIndeterminate(true);
        activeTask=new SwingWorker<T,Void>(){protected T doInBackground()throws Exception{return work.run();}protected void done(){busy=false;installTask=false;workerInstaller=null;controls.forEach(c->c.setEnabled(true));cancel.setText("返回版本");cancel.setEnabled(true);previous.setEnabled(offset>0);next.setEnabled(pageMore);world.setEnabled(kind.getSelectedItem()==ContentCatalog.Kind.DATA);loader.setEnabled(kind.getSelectedItem()==ContentCatalog.Kind.MOD);progress.setIndeterminate(false);progress.setVisible(false);if(closed||!isDisplayable())return;try{T result=get();if(!cancelled)done.accept(result);else status.setText("操作已取消。");}catch(Exception failure){if(cancelled)status.setText("操作已取消。");else error(InstallVersionDialog.installationFailure(failure));}}};activeTask.execute();
    }
    private void error(Throwable failure){status.setText("操作未完成："+failure.getMessage());status.setToolTipText(failure.toString());}
    void leave(){if(busy){cancelled=true;status.setText("正在取消，请稍候…");cancel.setEnabled(false);if(workerInstaller!=null)workerInstaller.cancel();if(!installTask&&activeTask!=null)activeTask.cancel(true);}else onClose.run();}
    void onClose(Runnable action){onClose=action;cancel.setText("返回版本");}
    boolean busy(){return busy;}
    void refreshTargets(String selected){if(busy)return;String previousWorld=Objects.toString(world.getSelectedItem(),"");target.removeAllItems();target.addItem("请选择目标实例");for(String id:VersionScanner.scan(gameRoot))target.addItem(id);if(selected!=null&&!selected.isBlank())target.setSelectedItem(selected);targetChanged();for(int i=1;i<world.getItemCount();i++)if(previousWorld.equals(world.getItemAt(i)))world.setSelectedIndex(i);}
    void selectKind(ContentCatalog.Kind value){if(busy)return;kind.setSelectedItem(value);world.setEnabled(value==ContentCatalog.Kind.DATA);loader.setEnabled(value==ContentCatalog.Kind.MOD);offset=0;projects.clear();files.removeAllItems();if(network)search();}
    private FileDialog chooser(String title,int mode){return owner instanceof Dialog dialog?new FileDialog(dialog,title,mode):new FileDialog((Frame)owner,title,mode);}
    private void loadCategories(){
        category.removeAllItems();category.addItem(null);if(!network)return;
        int request=++categoryRequest;var provider=(ContentCatalog.Source)source.getSelectedItem();var type=(ContentCatalog.Kind)kind.getSelectedItem();
        new SwingWorker<List<ContentCatalog.Category>,Void>(){
            protected List<ContentCatalog.Category> doInBackground()throws Exception{return catalog().categories(provider,type);}
            protected void done(){if(closed||request!=categoryRequest)return;try{for(var item:get())category.addItem(item);}catch(Exception e){status.setToolTipText("类型标签加载失败，仍可按名称搜索："+InstallVersionDialog.installationFailure(e).getMessage());}}
        }.execute();
    }
    private Component resourceRow(ContentCatalog.Project value,boolean selected){
        JPanel row=UiTheme.transparent(new BorderLayout(12,0));row.setOpaque(true);row.setBackground(selected?UiTheme.p().line():UiTheme.p().surface());row.setBorder(new EmptyBorder(12,10,12,10));
        JLabel icon=new JLabel(icons.get(value.source()+":"+value.id()));icon.setPreferredSize(new Dimension(52,52));if(icon.getIcon()==null){icon.setText(value.kind().title);icon.setFont(UiTheme.font(11,true));icon.setForeground(UiTheme.p().accent());}row.add(icon,BorderLayout.WEST);
        JPanel text=UiTheme.transparent(new GridLayout(3,1,0,4));JLabel title=UiTheme.label(value.title(),14,true),summary=UiTheme.muted(value.description(),11),meta=UiTheme.muted(value.source()+" · "+compact(value.downloads())+" 下载 · "+(value.updated().length()>=10?value.updated().substring(0,10):"日期未知")+" · "+String.join(" / ",value.tags().stream().limit(3).map(ContentCatalog::categoryName).toList()),10);
        for(JLabel label:List.of(title,summary,meta)){label.putClientProperty("html.disable",true);text.add(label);}row.add(text,BorderLayout.CENTER);return row;
    }
    static String compact(long value){return value>=100000000?String.format(Locale.ROOT,"%.1f 亿",value/100000000.0):value>=10000?String.format(Locale.ROOT,"%.1f 万",value/10000.0):String.valueOf(value);}
    private void loadIcons(List<ContentCatalog.Project> values){
        new SwingWorker<Map<String,ImageIcon>,Void>(){
            protected Map<String,ImageIcon> doInBackground(){Map<String,ImageIcon> result=new HashMap<>();for(var value:values)try{if(closed)break;String key=value.source()+":"+value.id();if(value.icon().isBlank())continue;byte[] bytes=DownloadCatalog.request(value.icon(),Map.of(),2*1024*1024);var image=decodeIcon(bytes);if(image!=null)result.put(key,new ImageIcon(image.getScaledInstance(48,48,Image.SCALE_SMOOTH)));}catch(Exception ignored){}return result;}
            protected void done(){if(closed)return;try{if(icons.size()>160)icons.clear();icons.putAll(get());results.repaint();}catch(Exception ignored){}}
        }.execute();
    }
    private static java.awt.image.BufferedImage decodeIcon(byte[] bytes)throws Exception{
        try(var input=javax.imageio.ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){var readers=javax.imageio.ImageIO.getImageReaders(input);if(readers.hasNext()){var reader=readers.next();try{reader.setInput(input);if((long)reader.getWidth(0)*reader.getHeight(0)>4_000_000)return null;return reader.read(0);}finally{reader.dispose();}}}
        if(bytes.length>=12&&new String(bytes,0,4,java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")&&new String(bytes,8,4,java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP")&&Files.isRegularFile(WallpaperLibrary.decoder())){
            Path file=Files.createTempFile("oml-resource-icon-",".webp");try{Files.write(file,bytes);return WallpaperLibrary.probe(file);}finally{Files.deleteIfExists(file);}
        }return null;
    }
    private void saveSelected(){
        var file=(ContentCatalog.FileVersion)files.getSelectedItem();if(file==null)return;FileDialog choose=chooser("保存资源文件",FileDialog.SAVE);choose.setFile(file.filename());choose.setVisible(true);if(choose.getFile()==null){choose.dispose();return;}Path target=Path.of(choose.getDirectory(),choose.getFile());choose.dispose();
        installTask=true;runTask(()->{if(Files.exists(target))throw new IOException("目标文件已存在，请选择其他名称");workerInstaller=new VersionInstaller(gameRoot,p->{});if(cancelled)workerInstaller.cancel();Path stage=Files.createTempDirectory(target.toAbsolutePath().getParent(),".oml-resource-");try{Path downloaded=catalog().download(file,stage,workerInstaller);workerInstaller.checkCancelled();Files.move(downloaded,target);return target;}finally{try(var children=Files.list(stage)){for(Path child:children.toList())Files.deleteIfExists(child);}Files.deleteIfExists(stage);}},path->status.setText("已保存："+path),"正在下载资源文件…");
    }
    static Path resourceDirectory(Path instance,ContentCatalog.Kind kind,String world)throws IOException{
        if(kind!=ContentCatalog.Kind.DATA)return instance.resolve(kind.folder);
        Path saves=instance.resolve("saves").toAbsolutePath().normalize(),folder=saves.resolve(world).normalize();
        if(world.isBlank()||!saves.equals(folder.getParent())||Files.isSymbolicLink(folder)||!Files.isRegularFile(folder.resolve("level.dat"))||!folder.toRealPath().startsWith(saves.toRealPath()))throw new IOException("请选择此实例中已有的有效存档");
        Path destination=folder.resolve("datapacks");if(!saves.toRealPath().startsWith(instance.toRealPath())||Files.exists(destination)&&!destination.toRealPath().startsWith(folder.toRealPath()))throw new IOException("存档或数据包目录指向了实例之外");return destination;
    }
    @Override public void close(){closed=true;cancelled=true;if(workerInstaller!=null)workerInstaller.cancel();if(!installTask&&activeTask!=null)activeTask.cancel(true);}
}

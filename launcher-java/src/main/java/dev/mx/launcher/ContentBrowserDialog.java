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
final class ContentBrowserDialog extends JDialog {
    private final Path gameRoot;
    private final Consumer<String> installed;
    private final JComboBox<ContentCatalog.Source> source=new JComboBox<>(ContentCatalog.Source.values());
    private final JComboBox<ContentCatalog.Kind> kind=new JComboBox<>(ContentCatalog.Kind.values());
    private final JComboBox<String> target=new JComboBox<>(),game=new JComboBox<>(),loader=new JComboBox<>(new String[]{"","fabric","quilt","forge","neoforge"});
    private final JCheckBox tests=new UiTheme.Toggle("包含 Beta / Alpha",true),dependencies=new UiTheme.Toggle("自动下载必需依赖",true);
    private final JTextField query=new JTextField();
    private final DefaultListModel<ContentCatalog.Project> projects=new DefaultListModel<>();
    private final JList<ContentCatalog.Project> results=new JList<>(projects);
    private final JComboBox<ContentCatalog.FileVersion> files=new JComboBox<>();
    private final JTextArea description=new JTextArea();
    private final JLabel status=UiTheme.muted("输入名称搜索，或直接浏览热门资源。",12);
    private final JProgressBar progress=new UiTheme.ProgressBar();
    private final UiTheme.Button search=new UiTheme.Button("搜索","",UiTheme.Tone.PRIMARY),download=new UiTheme.Button("下载并安装","download",UiTheme.Tone.PRIMARY),cancel=new UiTheme.Button("关闭","",UiTheme.Tone.SURFACE);
    private final List<JComponent> controls=new ArrayList<>();
    private ContentCatalog.Project project;
    private List<ContentCatalog.FileVersion> versions=List.of();
    private int offset,fileOffset,total,selectionRequest;
    private volatile VersionInstaller workerInstaller;
    private boolean busy;
    private volatile boolean cancelled;

    ContentBrowserDialog(Window owner,Path gameRoot,String selectedVersion,Consumer<String> installed){this(owner,gameRoot,selectedVersion,installed,true);}
    ContentBrowserDialog(Window owner,Path gameRoot,String selectedVersion,Consumer<String> installed,boolean network){
        super(owner,"下载资源",ModalityType.APPLICATION_MODAL);this.gameRoot=gameRoot;this.installed=installed;setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        UiTheme.Surface root=new UiTheme.Surface(new BorderLayout(0,16),0);root.setBorder(new EmptyBorder(24,24,22,24));
        JPanel top=UiTheme.transparent(new BorderLayout(0,14));top.add(UiTheme.label("资源下载",23,true),BorderLayout.NORTH);
        JPanel filters=UiTheme.transparent(new GridLayout(0,4,10,8));
        for(JComboBox<?> box:List.of(source,kind,target,game,loader,files))UiTheme.combo(box);
        target.addItem("请选择目标实例");for(String version:VersionScanner.scan(gameRoot))target.addItem(version);if(selectedVersion!=null&&!selectedVersion.isBlank())target.setSelectedItem(selectedVersion);
        game.setEditable(true);game.addItem("");
        filters.add(field("下载平台",source));filters.add(field("资源类型",kind));filters.add(field("安装到实例",target));filters.add(field("Minecraft 版本",game));
        filters.add(field("模组加载器",loader));filters.add(tests);filters.add(dependencies);
        UiTheme.Button credential=new UiTheme.Button("CurseForge 密钥","settings",UiTheme.Tone.SURFACE);credential.addActionListener(e->configureKey());filters.add(credential);
        JPanel searchRow=UiTheme.transparent(new BorderLayout(10,0));query.setFont(UiTheme.font(13,false));searchRow.add(query,BorderLayout.CENTER);searchRow.add(search,BorderLayout.EAST);
        JPanel filterHost=UiTheme.transparent(new BorderLayout(0,12));filterHost.add(filters,BorderLayout.CENTER);filterHost.add(searchRow,BorderLayout.SOUTH);top.add(filterHost,BorderLayout.CENTER);root.add(top,BorderLayout.NORTH);
        results.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);results.setFixedCellHeight(66);results.setBackground(UiTheme.p().surface());
        results.setCellRenderer((list,value,index,selected,focus)->{JPanel row=UiTheme.transparent(new GridLayout(2,1,0,5));row.setOpaque(true);row.setBackground(selected?UiTheme.p().line():UiTheme.p().surface());row.setBorder(new EmptyBorder(10,12,10,12));JLabel title=UiTheme.label(value.title(),13,true);title.putClientProperty("html.disable",true);JLabel author=UiTheme.muted(value.author(),11);author.putClientProperty("html.disable",true);row.add(title);row.add(author);return row;});
        JScrollPane listScroll=new JScrollPane(results);listScroll.setBorder(null);UiTheme.scroll(listScroll);
        JPanel details=UiTheme.transparent(new BorderLayout(0,14));description.setEditable(false);description.setLineWrap(true);description.setWrapStyleWord(true);description.setFont(UiTheme.font(13,false));description.setBackground(UiTheme.p().surface());description.setForeground(UiTheme.p().text());description.setBorder(new EmptyBorder(14,16,14,16));
        JScrollPane body=new JScrollPane(description);body.setBorder(null);UiTheme.scroll(body);details.add(body,BorderLayout.CENTER);
        JPanel versionActions=UiTheme.transparent(new BorderLayout(0,10));files.setPreferredSize(new Dimension(340,38));versionActions.add(files,BorderLayout.NORTH);
        JPanel actions=UiTheme.transparent(new FlowLayout(FlowLayout.RIGHT,8,0));UiTheme.Button website=new UiTheme.Button("原项目页面","",UiTheme.Tone.SURFACE),moreFiles=new UiTheme.Button("更多文件","",UiTheme.Tone.SURFACE);actions.add(moreFiles);actions.add(website);actions.add(download);versionActions.add(actions,BorderLayout.CENTER);details.add(versionActions,BorderLayout.SOUTH);
        JSplitPane split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,listScroll,details);split.setOpaque(false);split.setBorder(null);split.setDividerLocation(310);split.setResizeWeight(0.33);root.add(split,BorderLayout.CENTER);
        JPanel footer=UiTheme.transparent(new BorderLayout(0,10));footer.add(status,BorderLayout.NORTH);progress.setPreferredSize(new Dimension(100,7));progress.setVisible(false);footer.add(progress,BorderLayout.CENTER);
        JPanel navigation=UiTheme.transparent(new FlowLayout(FlowLayout.RIGHT,8,0));UiTheme.Button previous=new UiTheme.Button("上一页","",UiTheme.Tone.SURFACE),next=new UiTheme.Button("下一页","",UiTheme.Tone.SURFACE),local=new UiTheme.Button("导入整合包","folder",UiTheme.Tone.SURFACE);navigation.add(local);navigation.add(previous);navigation.add(next);navigation.add(cancel);footer.add(navigation,BorderLayout.SOUTH);root.add(footer,BorderLayout.SOUTH);
        controls.addAll(List.of(source,kind,target,game,loader,tests,dependencies,credential,query,search,results,files,website,moreFiles,download,previous,next,local));
        search.addActionListener(e->{offset=0;search();});query.addActionListener(e->search.doClick());
        previous.addActionListener(e->{if(offset>=20){offset-=20;search();}});next.addActionListener(e->{if(offset+20<total){offset+=20;search();}});
        results.addListSelectionListener(e->{if(!e.getValueIsAdjusting()&&!busy){project=results.getSelectedValue();fileOffset=0;loadFiles();}});
        moreFiles.addActionListener(e->{if(project!=null&&project.source()==ContentCatalog.Source.CURSEFORGE){fileOffset+=50;loadFiles();}});
        tests.addActionListener(e->filterFiles());target.addActionListener(e->targetChanged());
        website.addActionListener(e->{if(project!=null)try{Desktop.getDesktop().browse(URI.create(project.website()));}catch(Exception failure){error(failure);}});
        download.addActionListener(e->installSelected());local.addActionListener(e->importPack());
        cancel.addActionListener(e->leave());addWindowListener(new WindowAdapter(){@Override public void windowClosing(WindowEvent event){leave();}});
        UiTheme.refresh(root);setContentPane(root);WindowChrome.dialog(this);setSize(1060,750);setMinimumSize(new Dimension(960,700));setLocationRelativeTo(owner);targetChanged();
        if(network)new SwingWorker<List<DownloadCatalog.Game>,Void>(){protected List<DownloadCatalog.Game> doInBackground()throws Exception{return new DownloadCatalog().games();}protected void done(){if(!isDisplayable())return;try{String current=gameFilter();for(var item:get())game.addItem(item.id());game.setSelectedItem(current);}catch(Exception ignored){}}}.execute();
    }
    private JPanel field(String label,JComponent value){JPanel panel=UiTheme.transparent(new BorderLayout(0,5));panel.add(UiTheme.muted(label,11),BorderLayout.NORTH);panel.add(value,BorderLayout.CENTER);return panel;}
    private ContentCatalog catalog(){return new ContentCatalog(DownloadCredentials.load());}
    private String gameFilter(){return Objects.toString(game.getEditor().getItem(),"").strip();}
    private String loaderFilter(){return Objects.toString(loader.getSelectedItem(),"");}
    private void targetChanged(){
        if(target.getSelectedIndex()<=0)return;try{String version=target.getSelectedItem().toString();Path instance=VersionInstaller.gameDirectory(gameRoot,version);Map<String,Object> meta=Json.parseObject(Files.readString(instance.resolve("versions").resolve(version).resolve(version+".json")));
            game.setSelectedItem(Objects.toString(meta.get("mxcloudMinecraft"),version.split("-",2)[0]));loader.setSelectedItem(ContentCatalog.installedLoader(meta));
        }catch(Exception ignored){}
    }
    private void search(){
        ContentCatalog.Source selected=(ContentCatalog.Source)source.getSelectedItem();ContentCatalog.Kind category=(ContentCatalog.Kind)kind.getSelectedItem();String text=query.getText(),game=gameFilter(),loader=loaderFilter();
        runTask(()->catalog().search(selected,category,text,game,loader,offset),page->{total=page.total();projects.clear();project=null;files.removeAllItems();versions=List.of();description.setText("");for(var value:page.projects())projects.addElement(value);status.setText("共 "+total+" 项 · 第 "+(offset/20+1)+" 页");},"正在搜索资源…");
    }
    private void loadFiles(){
        ContentCatalog.Project chosen=project;if(chosen==null)return;int request=++selectionRequest;description.setText(chosen.title()+"\n作者："+chosen.author()+"\n来源："+chosen.source()+"\n\n"+chosen.description());description.setCaretPosition(0);
        String game=gameFilter(),loader=loaderFilter();
        runTask(()->catalog().versions(chosen,game,loader,fileOffset),values->{if(request!=selectionRequest)return;if(fileOffset==0)versions=values;else{var combined=new ArrayList<>(versions);combined.addAll(values);versions=combined;}filterFiles();status.setText("已读取 "+versions.size()+" 个文件版本。下载前请确认目标实例。");},"正在读取可下载版本…");
    }
    private void filterFiles(){files.removeAllItems();for(var version:versions)if(tests.isSelected()||"release".equals(version.release()))files.addItem(version);}
    private void installSelected(){
        var file=(ContentCatalog.FileVersion)files.getSelectedItem();ContentCatalog.Project chosen=project;if(file==null||chosen==null)return;
        String packName=chosen.kind()==ContentCatalog.Kind.PACK?packName():null;if(chosen.kind()==ContentCatalog.Kind.PACK&&packName==null)return;
        String targetId=Objects.toString(target.getSelectedItem(),"");if(chosen.kind()!=ContentCatalog.Kind.PACK&&target.getSelectedIndex()<=0){status.setText("请先选择要安装到的游戏实例。");return;}
        String game=gameFilter(),loader=loaderFilter();boolean withDependencies=dependencies.isSelected(),withTests=tests.isSelected();
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
            Path destination=instance.resolve(chosen.kind().folder);Files.createDirectories(destination);Path stage=Files.createTempDirectory(gameRoot,".resource-download-");
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
    private void importPack(){FileDialog choose=new FileDialog(this,"选择 .mrpack 或 CurseForge 整合包 ZIP",FileDialog.LOAD);choose.setVisible(true);if(choose.getFile()==null)return;Path archive=Path.of(choose.getDirectory(),choose.getFile());choose.dispose();String name=packName();if(name==null)return;runInstall(installer->ModpackInstaller.install(archive,name,gameRoot,installer,catalog()));}
    private void runInstall(InstallVersionDialog.Action action){
        runTask(()->{workerInstaller=new VersionInstaller(gameRoot,p->SwingUtilities.invokeLater(()->{if(isDisplayable()){status.setText(p.message());progress.setIndeterminate(p.total()==0);if(p.total()>0)progress.setValue(p.completed()*1000/p.total());}}));if(cancelled)workerInstaller.cancel();return action.run(workerInstaller);},id->{installed.accept(id);status.setText("下载与安装完成："+id);},"正在准备下载与安装…");
    }
    private void configureKey(){JPasswordField input=new JPasswordField(32);JPanel panel=UiTheme.transparent(new BorderLayout(0,10));panel.add(UiTheme.muted("填写 CurseForge 官方 API Key，仅加密保存在当前 Windows 用户本机。",12),BorderLayout.NORTH);panel.add(input,BorderLayout.CENTER);if(JOptionPane.showConfirmDialog(this,panel,"配置 CurseForge API Key",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION){char[] chars=input.getPassword();try{DownloadCredentials.save(new String(chars));status.setText(chars.length==0?"已清除本机密钥。":"密钥已加密保存，可选择 CurseForge 搜索。");}catch(Exception failure){error(failure);}finally{Arrays.fill(chars,'\0');}}}
    @FunctionalInterface private interface Work<T>{T run()throws Exception;}
    private <T>void runTask(Work<T> work,Consumer<T> done,String message){
        if(busy)return;busy=true;cancelled=false;controls.forEach(c->c.setEnabled(false));cancel.setText("取消");cancel.setEnabled(true);status.setText(message);progress.setVisible(true);progress.setIndeterminate(true);
        new SwingWorker<T,Void>(){protected T doInBackground()throws Exception{return work.run();}protected void done(){busy=false;workerInstaller=null;controls.forEach(c->c.setEnabled(true));cancel.setText("关闭");cancel.setEnabled(true);progress.setIndeterminate(false);progress.setVisible(false);if(!isDisplayable())return;try{T result=get();if(!cancelled)done.accept(result);else status.setText("操作已取消。");}catch(Exception failure){if(cancelled)status.setText("操作已取消。");else error(InstallVersionDialog.installationFailure(failure));}}}.execute();
    }
    private void error(Throwable failure){status.setText("操作未完成："+failure.getMessage());status.setToolTipText(failure.toString());}
    private void leave(){if(busy){cancelled=true;if(workerInstaller!=null)workerInstaller.cancel();status.setText("正在取消，请稍候…");cancel.setEnabled(false);}else dispose();}
}

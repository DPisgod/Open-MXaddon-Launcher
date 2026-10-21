package dev.mx.launcher;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;

final class InstallVersionDialog extends JDialog {
    private volatile VersionInstaller installer;
    private boolean running,loading;
    private volatile boolean cancelRequested;
    private final JLabel status=UiTheme.muted("正在获取官方版本列表…",12);
    private final JProgressBar progress=new UiTheme.ProgressBar();
    private final UiTheme.Button start=new UiTheme.Button("安装游戏","download",UiTheme.Tone.PRIMARY);
    private final UiTheme.Button close=new UiTheme.Button("关闭","",UiTheme.Tone.SURFACE);
    private final JComboBox<String> type=new JComboBox<>(new String[]{"正式版","快照 / 预发布","历史 Beta","历史 Alpha","全部"});
    private final JComboBox<DownloadCatalog.Game> game=new JComboBox<>();
    private final JComboBox<DownloadCatalog.Loader> loader=new JComboBox<>(DownloadCatalog.Loader.values());
    private final JComboBox<DownloadCatalog.LoaderVersion> loaderVersion=new JComboBox<>();
    private final JCheckBox api=new UiTheme.Toggle("自动下载 Fabric API",true),java=new UiTheme.Toggle("自动下载匹配的 Java",true);
    private final JTextField name=new JTextField();
    private List<DownloadCatalog.Game> games=List.of();
    private int loaderRequest;
    InstallVersionDialog(Window owner,Path gameRoot,Consumer<String> installed){this(owner,gameRoot,installed,true);}
    InstallVersionDialog(Window owner,Path gameRoot,Consumer<String> installed,boolean network){
        super(owner,"添加游戏版本",ModalityType.APPLICATION_MODAL);setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        UiTheme.Surface root=new UiTheme.Surface(new BorderLayout(0,20),0);root.setBorder(new EmptyBorder(24,28,24,28));
        JPanel heading=UiTheme.transparent(new GridLayout(0,1,0,8));heading.add(UiTheme.label("安装 Minecraft",23,true));heading.add(UiTheme.muted("官方版本列表 · 独立实例 · 下载校验 · 自动匹配运行环境",12));root.add(heading,BorderLayout.NORTH);
        JPanel form=UiTheme.transparent(new GridBagLayout());
        row(form,0,"版本类型",type);row(form,1,"游戏版本",game);row(form,2,"模组加载器",loader);row(form,3,"加载器版本",loaderVersion);row(form,4,"实例名称（留空自动）",name);row(form,5,"安装选项",java);row(form,6,"Fabric 依赖",api);
        UiTheme.Button manual=new UiTheme.Button("仅下载加载器","download",UiTheme.Tone.SURFACE);
        manual.setToolTipText("Forge / NeoForge 下载官方安装器；Fabric / Quilt 下载所选加载器 JAR");row(form,7,"手动下载",manual);root.add(form,BorderLayout.CENTER);
        JPanel bottom=UiTheme.transparent(new BorderLayout(0,12));bottom.add(status,BorderLayout.NORTH);progress.setPreferredSize(new Dimension(100,7));bottom.add(progress,BorderLayout.CENTER);
        JPanel actions=UiTheme.transparent(new FlowLayout(FlowLayout.RIGHT,8,0));actions.add(close);actions.add(start);bottom.add(actions,BorderLayout.SOUTH);root.add(bottom,BorderLayout.SOUTH);
        for(JComboBox<?> box:List.of(type,game,loader,loaderVersion))UiTheme.combo(box);loaderVersion.setEnabled(false);api.setEnabled(false);start.setEnabled(false);
        type.addActionListener(e->filter());game.addActionListener(e->loadLoaders());loader.addActionListener(e->loadLoaders());
        start.addActionListener(e->{DownloadCatalog.Options options=options();if(options==null)return;run(gameRoot,installed,worker->worker.install(options));});
        manual.addActionListener(e->{DownloadCatalog.Options options=options();if(options==null||options.loader()==DownloadCatalog.Loader.VANILLA)return;
            if(running)return;
            FileDialog choose=new FileDialog(this,"保存加载器文件",FileDialog.SAVE);choose.setFile(options.loader().name().toLowerCase()+"-"+options.minecraft()+"-"+options.loaderVersion()+(options.loader()==DownloadCatalog.Loader.FABRIC||options.loader()==DownloadCatalog.Loader.QUILT?"-loader.jar":"-installer.jar"));choose.setVisible(true);
            if(choose.getFile()==null)return;Path target=Path.of(choose.getDirectory(),choose.getFile());choose.dispose();
            run(gameRoot,id->{},worker->{
                if(Files.exists(target))throw new java.io.IOException("文件已存在，请选择其他文件名");
                if(options.loader()==DownloadCatalog.Loader.FABRIC||options.loader()==DownloadCatalog.Loader.QUILT){String url=options.loader()==DownloadCatalog.Loader.FABRIC?"https://maven.fabricmc.net/net/fabricmc/fabric-loader/":"https://maven.quiltmc.org/repository/release/org/quiltmc/quilt-loader/";String artifact=options.loader()==DownloadCatalog.Loader.FABRIC?"fabric-loader":"quilt-loader";url+=options.loaderVersion()+"/"+artifact+"-"+options.loaderVersion()+".jar";String hash=new String(worker.readUrl(url+".sha1",1024),StandardCharsets.UTF_8).strip().split("\\s+")[0];worker.obtain(new VersionInstaller.Download(url,target,"SHA-1",hash,-1));}
                else{Path temp=Files.createTempDirectory(gameRoot,".loader-download-");try{Path file=worker.downloadLoaderInstaller(options.loader(),options.minecraft(),options.loaderVersion(),temp);Files.move(file,target);}finally{try(var files=Files.list(temp)){for(Path file:files.toList())Files.deleteIfExists(file);}Files.deleteIfExists(temp);}}
                return target.toString();
            });
        });
        Runnable leave=()->{if(running){cancelRequested=true;if(installer!=null)installer.cancel();status.setText("正在取消…");close.setEnabled(false);}else dispose();};
        close.addActionListener(e->leave.run());addWindowListener(new WindowAdapter(){@Override public void windowClosing(WindowEvent e){leave.run();}});
        UiTheme.refresh(root);setContentPane(root);WindowChrome.dialog(this);setSize(720,650);setMinimumSize(new Dimension(680,620));setLocationRelativeTo(owner);
        if(network)fetchGames();
    }
    private static void row(JPanel form,int row,String text,Component component){GridBagConstraints c=new GridBagConstraints();c.gridy=row;c.gridx=0;c.anchor=GridBagConstraints.WEST;c.insets=new Insets(7,0,7,16);form.add(UiTheme.muted(text,12),c);c.gridx=1;c.weightx=1;c.fill=GridBagConstraints.HORIZONTAL;c.insets=new Insets(7,0,7,0);component.setPreferredSize(new Dimension(350,34));form.add(component,c);}
    private void fetchGames(){new SwingWorker<List<DownloadCatalog.Game>,Void>(){
        protected List<DownloadCatalog.Game> doInBackground()throws Exception{return new DownloadCatalog().games();}
        protected void done(){if(!isDisplayable())return;try{games=get();filter();status.setText("请选择游戏版本，可选择自动安装加载器及 Java。");}catch(Exception failure){status.setText("列表读取失败，关闭后重新打开可重试。");status.setToolTipText(installationFailure(failure).getMessage());}}
    }.execute();}
    private void filter(){loading=true;game.removeAllItems();String selected=switch(type.getSelectedIndex()){case 0->"release";case 1->"snapshot";case 2->"old_beta";case 3->"old_alpha";default->"";};for(var item:games)if(selected.isEmpty()||selected.equals(item.type()))game.addItem(item);loading=false;loadLoaders();}
    private void loadLoaders(){
        if(loading||running)return;int request=++loaderRequest;DownloadCatalog.Game selected=(DownloadCatalog.Game)game.getSelectedItem();DownloadCatalog.Loader chosen=(DownloadCatalog.Loader)loader.getSelectedItem();
        loaderVersion.removeAllItems();loaderVersion.setEnabled(false);api.setEnabled(chosen==DownloadCatalog.Loader.FABRIC);start.setEnabled(selected!=null&&chosen==DownloadCatalog.Loader.VANILLA);
        if(selected==null||chosen==DownloadCatalog.Loader.VANILLA)return;status.setText("正在读取兼容加载器版本…");
        new SwingWorker<List<DownloadCatalog.LoaderVersion>,Void>(){
            protected List<DownloadCatalog.LoaderVersion> doInBackground()throws Exception{return new DownloadCatalog().loaders(chosen,selected.id());}
            protected void done(){if(!isDisplayable()||request!=loaderRequest)return;try{for(var value:get())loaderVersion.addItem(value);loaderVersion.setEnabled(loaderVersion.getItemCount()>0);start.setEnabled(loaderVersion.getItemCount()>0);status.setText(loaderVersion.getItemCount()>0?"可选择任意加载器版本，包括测试版。":"此游戏版本没有该加载器，请选择原版或其他加载器。");}catch(Exception failure){status.setText("加载器列表读取失败，可切换加载器重试。");status.setToolTipText(installationFailure(failure).getMessage());}}
        }.execute();
    }
    private DownloadCatalog.Options options(){var selected=(DownloadCatalog.Game)game.getSelectedItem();var chosen=(DownloadCatalog.Loader)loader.getSelectedItem();var version=(DownloadCatalog.LoaderVersion)loaderVersion.getSelectedItem();if(selected==null||chosen!=DownloadCatalog.Loader.VANILLA&&version==null)return null;return new DownloadCatalog.Options(selected.id(),chosen,version==null?"":version.version(),api.isEnabled()&&api.isSelected(),java.isSelected(),name.getText());}
    @FunctionalInterface interface Action {String run(VersionInstaller installer)throws Exception;}
    private void run(Path gameRoot,Consumer<String> complete,Action action){
        running=true;cancelRequested=false;start.setEnabled(false);close.setText("取消安装");for(Component component:List.of(type,game,loader,loaderVersion,name,api,java))component.setEnabled(false);
        new SwingWorker<String,VersionInstaller.Progress>(){
            protected String doInBackground()throws Exception{installer=new VersionInstaller(gameRoot,p->{if(p.notice()||p.total()==0||p.completed()%12==0||p.completed()==p.total())publish(p);});if(cancelRequested)installer.cancel();return action.run(installer);}
            protected void process(List<VersionInstaller.Progress> chunks){var p=chunks.get(chunks.size()-1);showStatus(p.message());progress.setIndeterminate(p.total()==0);if(p.total()>0)progress.setValue(p.completed()*1000/p.total());}
            protected void done(){running=false;installer=null;progress.setIndeterminate(false);close.setText("关闭");close.setEnabled(true);for(Component component:List.of(type,game,loader,name,java))component.setEnabled(true);try{String id=get();complete.accept(id);status.setText("操作完成："+id);progress.setValue(1000);start.setText("安装游戏");}catch(Exception failure){Throwable cause=installationFailure(failure);showStatus(cancelRequested?"已取消，已校验下载缓存保留。":"未完成："+cause.getMessage());status.setToolTipText(cause.toString());}loadLoaders();}
        }.execute();
    }
    static Throwable installationFailure(Throwable failure){while((failure instanceof java.util.concurrent.ExecutionException||failure instanceof java.util.concurrent.CompletionException)&&failure.getCause()!=null)failure=failure.getCause();return failure;}
    private void showStatus(String message){status.setText("<html><div style='width:460px'>"+message.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")+"</div></html>");}
}

package dev.mx.launcher;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

final class InstallVersionDialog extends JDialog {
    private volatile VersionInstaller installer;
    private boolean running;
    private volatile boolean cancelRequested;
    private final JLabel status=UiTheme.muted("将安装到独立游戏目录，保留其他版本。",12);
    private final JProgressBar progress=new UiTheme.ProgressBar();
    private final UiTheme.Button start=new UiTheme.Button("一键安装","download",UiTheme.Tone.PRIMARY);
    private final UiTheme.Button close=new UiTheme.Button("关闭","",UiTheme.Tone.SURFACE);
    InstallVersionDialog(Window owner,Path gameRoot,Consumer<String> installed){
        super(owner,"添加游戏版本",ModalityType.APPLICATION_MODAL);setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        UiTheme.Surface root=new UiTheme.Surface(new BorderLayout(0,20),22);root.setBorder(new EmptyBorder(26,28,24,28));
        JPanel text=UiTheme.transparent(null);text.setLayout(new BoxLayout(text,BoxLayout.Y_AXIS));
        text.add(UiTheme.label("添加游戏版本",23,true));text.add(Box.createVerticalStrut(8));text.add(UiTheme.muted("Minecraft 1.21.1  ·  Fabric 0.19.3  ·  Java 21",13));text.add(Box.createVerticalStrut(22));
        for(String line:new String[]{"自动安装 Minecraft 1.21.1 与 Fabric 加载器","下载游戏本体、完整资源与 Windows 原生库，并校验文件","mods 目录保持为空，可随后自行拖入 Mod","下载约 1 GB；安装与缓存至少需要 2.2 GB 可用空间"}){text.add(UiTheme.muted(line,12));text.add(Box.createVerticalStrut(10));}
        root.add(text,BorderLayout.NORTH);
        JPanel state=UiTheme.transparent(new BorderLayout(0,10));state.add(status,BorderLayout.NORTH);progress.setPreferredSize(new Dimension(100,7));state.add(progress,BorderLayout.SOUTH);root.add(state,BorderLayout.CENTER);
        JPanel actions=UiTheme.transparent(new FlowLayout(FlowLayout.RIGHT,8,0));actions.add(close);actions.add(start);root.add(actions,BorderLayout.SOUTH);
        start.addActionListener(e->{
            running=true;cancelRequested=false;start.setEnabled(false);close.setText("取消安装");
            new SwingWorker<String,VersionInstaller.Progress>(){
                @Override protected String doInBackground()throws Exception{installer=new VersionInstaller(gameRoot,p->{if(p.notice()||p.total()==0||p.completed()%12==0||p.completed()==p.total())publish(p);});if(cancelRequested)installer.cancel();return installer.install();}
                @Override protected void process(List<VersionInstaller.Progress> chunks){VersionInstaller.Progress p=chunks.get(chunks.size()-1);showStatus(p.message());progress.setIndeterminate(p.total()==0);if(p.total()>0)progress.setValue(p.completed()*1000/p.total());}
                @Override protected void done(){running=false;installer=null;progress.setIndeterminate(false);close.setText("关闭");close.setEnabled(true);try{String version=get();status.setText("安装完成，可以返回主页启动游戏。");progress.setValue(1000);installed.accept(version);start.setText("已安装");}catch(Exception failure){Throwable cause=installationFailure(failure);boolean cancelled=cancelRequested;showStatus(cancelled?"安装已取消；已校验文件保留，重试时会复用。":"安装未完成："+cause.getMessage()+"。已校验文件保留，重试时会复用。");status.setToolTipText(cancelled?null:cause.toString());if(!cancelled){System.err.println(java.time.Instant.now()+" [version-install] failed");cause.printStackTrace(System.err);}start.setEnabled(true);start.setText("重试安装");}}
            }.execute();
        });
        Runnable leave=()->{if(running){cancelRequested=true;if(installer!=null)installer.cancel();status.setText("正在取消安装，已下载的校验缓存会保留…");close.setEnabled(false);}else dispose();};
        close.addActionListener(e->leave.run());addWindowListener(new WindowAdapter(){@Override public void windowClosing(WindowEvent e){leave.run();}});
        setContentPane(root);WindowChrome.dialog(this);setSize(620,420);setMinimumSize(new Dimension(580,420));setLocationRelativeTo(owner);
    }
    static Throwable installationFailure(Throwable failure){while((failure instanceof java.util.concurrent.ExecutionException||failure instanceof java.util.concurrent.CompletionException)&&failure.getCause()!=null)failure=failure.getCause();return failure;}
    private void showStatus(String message){status.setText("<html><div style='width:410px'>"+message.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")+"</div></html>");}
}

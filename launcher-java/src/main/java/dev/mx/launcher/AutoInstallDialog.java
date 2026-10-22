package dev.mx.launcher;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/** Explicit confirmation before a dropped pack writes or downloads anything. */
final class AutoInstallDialog extends JDialog {
    @FunctionalInterface interface Install {String run(VersionInstaller installer)throws Exception;}
    private final UiTheme.Button cancel=new UiTheme.Button("取消","",UiTheme.Tone.SURFACE),start=new UiTheme.Button("立即安装","download",UiTheme.Tone.PRIMARY);
    private final JProgressBar progress=new UiTheme.ProgressBar();
    private final JTextArea status=new JTextArea("将自动下载缺少的游戏文件、加载器与模组。",2,28);
    private volatile VersionInstaller installer;
    private volatile boolean cancelRequested;
    private boolean running,installed;
    AutoInstallDialog(Window owner,Path archive,Path gameRoot,ModpackInstaller.Pack pack,Consumer<String> completed){
        this(owner,gameRoot,pack,completed,worker->ModpackInstaller.install(archive,pack.options().id(),gameRoot,worker,new ContentCatalog(DownloadCredentials.load())));
    }
    AutoInstallDialog(Window owner,Path gameRoot,ModpackInstaller.Pack pack,Consumer<String> completed,Install action){
        super(owner,"自动安装",ModalityType.APPLICATION_MODAL);setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);setName("autoInstallDialog");
        UiTheme.Surface root=new UiTheme.Surface(new BorderLayout(0,14),0);root.setBorder(new EmptyBorder(20,24,20,24));root.add(UiTheme.label("自动安装",21,true),BorderLayout.NORTH);
        JPanel body=UiTheme.transparent(new BorderLayout(0,12));JLabel version=UiTheme.label("版本："+pack.options().id(),15,true);version.setName("autoInstallVersion");version.setToolTipText(version.getText());body.add(version,BorderLayout.NORTH);
        status.setFont(UiTheme.font(12,false));status.setForeground(UiTheme.p().muted());status.setOpaque(false);status.setEditable(false);status.setLineWrap(true);status.setWrapStyleWord(true);status.setFocusable(false);body.add(status,BorderLayout.CENTER);progress.setPreferredSize(new Dimension(100,7));progress.setVisible(false);body.add(progress,BorderLayout.SOUTH);root.add(body,BorderLayout.CENTER);
        JPanel actions=UiTheme.transparent(new FlowLayout(FlowLayout.RIGHT,8,0));actions.add(cancel);actions.add(start);root.add(actions,BorderLayout.SOUTH);
        Runnable leave=()->{if(running){cancelRequested=true;installer.cancel();cancel.setEnabled(false);status.setText("正在取消安装并清理未完成的文件…");}else dispose();};
        cancel.addActionListener(e->leave.run());addWindowListener(new WindowAdapter(){@Override public void windowClosing(WindowEvent e){leave.run();}});
        start.addActionListener(e->{if(running)return;running=true;cancelRequested=false;start.setEnabled(false);progress.setVisible(true);progress.setIndeterminate(true);status.setText("正在准备安装…");
            installer=new VersionInstaller(gameRoot,p->{ });
            new SwingWorker<String,VersionInstaller.Progress>(){
                @Override protected String doInBackground()throws Exception{
                    installer=new VersionInstaller(gameRoot,p->{if(p.notice()||p.total()==0||p.completed()%12==0||p.completed()==p.total())publish(p);});if(cancelRequested)installer.cancel();return action.run(installer);
                }
                @Override protected void process(List<VersionInstaller.Progress> updates){var p=updates.get(updates.size()-1);status.setText(p.message());progress.setIndeterminate(p.total()==0);if(p.total()>0)progress.setValue((int)(p.completed()*1000L/p.total()));}
                @Override protected void done(){running=false;progress.setIndeterminate(false);if(!isDisplayable())return;cancel.setEnabled(true);try{String id=get();installed=true;completed.accept(id);dispose();}catch(Exception failure){if(cancelRequested){dispose();return;}Throwable cause=InstallVersionDialog.installationFailure(failure);status.setText("安装失败："+cause.getMessage());status.setToolTipText(cause.toString());start.setEnabled(true);}}
            }.execute();
        });
        setContentPane(root);WindowChrome.dialog(this);setSize(500,260);setResizable(false);setLocationRelativeTo(owner);
    }
    boolean installed(){return installed;}
    @Override public void dispose(){if(running&&installer!=null){cancelRequested=true;installer.cancel();}super.dispose();}
}

package dev.mx.launcher;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/** The first choice always returns to automatic game-runtime selection. */
final class JavaSelectionDialog extends JDialog {
    private record Choice(JavaRuntimes.Installation runtime){
        String path(){return runtime==null?"":runtime.executable().toString();}
        String title(){return runtime==null?"自动选择":runtime.title();}
        String detail(){return runtime==null?"按游戏版本匹配 Java，优先使用该实例下载的运行时":path();}
    }
    private final DefaultListModel<Choice> choices=new DefaultListModel<>();
    private final JList<Choice> list=new JList<>(choices);
    private final JLabel status=UiTheme.muted("正在检测本机 Java…",12);
    private final UiTheme.Button refresh=new UiTheme.Button("重新检测","",UiTheme.Tone.SURFACE);
    private SwingWorker<List<JavaRuntimes.Installation>,Void> scan;
    JavaSelectionDialog(Window owner,Path home,Path gameRoot,String current,Consumer<String> selected,Runnable browse){
        super(owner,"切换 Java",ModalityType.APPLICATION_MODAL);
        UiTheme.Surface root=new UiTheme.Surface(new BorderLayout(0,14),0);root.setBorder(new EmptyBorder(22,24,20,24));
        JPanel header=UiTheme.transparent(new BorderLayout());header.add(UiTheme.label("切换 Java",21,true));header.add(refresh,BorderLayout.EAST);root.add(header,BorderLayout.NORTH);
        choices.addElement(new Choice(null));list.setName("javaRuntimeList");list.getAccessibleContext().setAccessibleName("可用 Java，第一项自动选择");list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);list.setFixedCellHeight(66);list.setBackground(UiTheme.p().surface());list.setSelectedIndex(0);
        list.setCellRenderer((items,item,index,active,focus)->{
            JPanel row=UiTheme.transparent(new GridLayout(2,1,0,4));row.setOpaque(true);row.setBackground(active?UiTheme.p().line():UiTheme.p().surface());row.setBorder(new EmptyBorder(10,12,10,12));row.add(UiTheme.label(item.title(),14,true));JLabel path=UiTheme.muted(item.detail(),11);path.setToolTipText(item.detail());row.add(path);return row;
        });
        list.addMouseMotionListener(new MouseMotionAdapter(){@Override public void mouseMoved(MouseEvent e){int index=list.locationToIndex(e.getPoint());list.setToolTipText(index>=0?choices.get(index).detail():null);}});
        JScrollPane scroll=new JScrollPane(list);scroll.setBorder(BorderFactory.createLineBorder(UiTheme.p().line()));scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);UiTheme.scroll(scroll);root.add(scroll,BorderLayout.CENTER);
        JPanel footer=UiTheme.transparent(new BorderLayout(0,12));footer.add(status,BorderLayout.NORTH);JPanel actions=UiTheme.transparent(new FlowLayout(FlowLayout.RIGHT,8,0));
        UiTheme.Button manual=new UiTheme.Button("手动浏览…","folder",UiTheme.Tone.SURFACE),cancel=new UiTheme.Button("取消","",UiTheme.Tone.SURFACE),choose=new UiTheme.Button("使用所选 Java","",UiTheme.Tone.PRIMARY);choose.setName("useJavaRuntime");manual.addActionListener(e->{dispose();browse.run();});cancel.addActionListener(e->dispose());
        Runnable apply=()->{Choice choice=list.getSelectedValue();if(choice!=null){selected.accept(choice.path());dispose();}};choose.addActionListener(e->apply.run());
        list.addMouseListener(new MouseAdapter(){@Override public void mouseClicked(MouseEvent e){int index=list.locationToIndex(e.getPoint());if(e.getClickCount()==2&&SwingUtilities.isLeftMouseButton(e)&&index>=0&&list.getCellBounds(index,index).contains(e.getPoint()))apply.run();}});
        list.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0),"useJava");list.getActionMap().put("useJava",new AbstractAction(){@Override public void actionPerformed(ActionEvent e){apply.run();}});
        actions.add(manual);actions.add(cancel);actions.add(choose);footer.add(actions,BorderLayout.SOUTH);root.add(footer,BorderLayout.SOUTH);
        refresh.addActionListener(e->load(home,gameRoot,list.getSelectedValue()==null?current:list.getSelectedValue().path()));
        setContentPane(root);WindowChrome.dialog(this);setSize(760,530);setMinimumSize(new Dimension(650,420));setLocationRelativeTo(owner);getRootPane().setDefaultButton(choose);load(home,gameRoot,current);
    }
    private void load(Path home,Path gameRoot,String selected){
        if(scan!=null)scan.cancel(true);refresh.setEnabled(false);status.setText("正在检测本机 Java…");choices.clear();choices.addElement(new Choice(null));list.setSelectedIndex(0);
        scan=new SwingWorker<>(){
            @Override protected List<JavaRuntimes.Installation> doInBackground(){return JavaRuntimes.discover(home,gameRoot,selected);}
            @Override protected void done(){if(isCancelled())return;refresh.setEnabled(true);try{
                var found=get();for(var runtime:found){choices.addElement(new Choice(runtime));if(runtime.executable().toString().equalsIgnoreCase(selected))list.setSelectedIndex(choices.size()-1);}
                status.setText("检测到 "+found.size()+" 个 Java；选择后请在设置中点击保存。默认使用自动选择。");
            }catch(Exception failure){status.setText("检测未完成，仍可使用自动选择或手动浏览。");}}
        };scan.execute();
    }
    @Override public void dispose(){if(scan!=null)scan.cancel(true);super.dispose();}
}

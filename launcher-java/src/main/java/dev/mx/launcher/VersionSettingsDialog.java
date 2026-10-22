package dev.mx.launcher;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;

final class VersionSettingsDialog extends JDialog {
    private final ModManager manager;
    private final JTable table;
    private final Model model=new Model();
    private final JLabel message=UiTheme.muted("拖入 JAR 文件即可添加到此版本。",12);
    VersionSettingsDialog(Window owner,Path gameRoot,String version)throws Exception{
        super(owner,"版本设置 · "+version,ModalityType.APPLICATION_MODAL);manager=new ModManager(VersionInstaller.gameDirectory(gameRoot,version));
        UiTheme.Surface root=new UiTheme.Surface(new BorderLayout(0,18),0);root.setBorder(new EmptyBorder(24,24,22,24));
        JPanel heading=UiTheme.transparent(new BorderLayout());JPanel titles=UiTheme.transparent(new GridLayout(2,1,0,7));titles.add(UiTheme.label("版本与 Mod 管理",22,true));titles.add(UiTheme.muted(version+"  ·  仅管理当前版本",12));heading.add(titles,BorderLayout.CENTER);
        UiTheme.Button open=new UiTheme.Button("Mod 文件夹","folder",UiTheme.Tone.SURFACE);open.setToolTipText("打开 Mod 文件夹");open.setFont(UiTheme.font(12,false));open.addActionListener(e->open(manager.directory()));root.add(heading,BorderLayout.NORTH);
        table=new JTable(model);table.setRowHeight(44);table.setFont(UiTheme.font(12,false));table.setBackground(UiTheme.p().surface());table.setForeground(UiTheme.p().text());table.setSelectionBackground(UiTheme.p().line());table.setSelectionForeground(UiTheme.p().text());table.setGridColor(UiTheme.p().line());table.setShowVerticalLines(false);table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);table.getTableHeader().setFont(UiTheme.font(12,true));table.getTableHeader().setBackground(UiTheme.p().background());table.getTableHeader().setForeground(UiTheme.p().muted());
        table.getTableHeader().setDefaultRenderer(new javax.swing.table.DefaultTableCellRenderer(){@Override public Component getTableCellRendererComponent(JTable t,Object value,boolean selected,boolean focus,int row,int column){JLabel label=(JLabel)super.getTableCellRendererComponent(t,value,false,false,row,column);label.setBackground(UiTheme.p().background());label.setForeground(UiTheme.p().muted());label.setFont(UiTheme.font(12,true));label.setBorder(new EmptyBorder(10,8,10,8));return label;}});
        JScrollPane scroll=new JScrollPane(table);scroll.getViewport().setBackground(UiTheme.p().surface());scroll.setBackground(UiTheme.p().surface());scroll.setBorder(BorderFactory.createLineBorder(UiTheme.p().line()));UiTheme.scroll(scroll);root.add(scroll,BorderLayout.CENTER);
        JPanel bottom=UiTheme.transparent(new BorderLayout(0,12));JPanel actions=UiTheme.transparent(new FlowLayout(FlowLayout.LEFT,8,0));
        UiTheme.Button add=new UiTheme.Button("添加 Mod","plus",UiTheme.Tone.PRIMARY),locate=new UiTheme.Button("文件位置","folder",UiTheme.Tone.SURFACE),toggle=new UiTheme.Button("启用 / 禁用","power",UiTheme.Tone.SURFACE),uninstall=new UiTheme.Button("卸载","trash",UiTheme.Tone.SURFACE);
        actions.add(add);actions.add(locate);actions.add(toggle);actions.add(uninstall);actions.add(open);bottom.add(actions,BorderLayout.NORTH);bottom.add(message,BorderLayout.CENTER);bottom.add(UiTheme.muted("禁用只改为 .jar.disabled；卸载文件移至 mods/.mx-trash。",10),BorderLayout.SOUTH);root.add(bottom,BorderLayout.SOUTH);
        add.addActionListener(e->{FileDialog chooser=new FileDialog(this,"添加 Mod JAR",FileDialog.LOAD);chooser.setMultipleMode(true);chooser.setFile("*.jar");chooser.setVisible(true);List<Path> files=java.util.Arrays.stream(chooser.getFiles()).map(File::toPath).toList();chooser.dispose();importFiles(files);});
        locate.addActionListener(e->{ModManager.Mod selected=selected();if(selected!=null)try{new ProcessBuilder("explorer.exe","/select,",selected.file().toString()).start();}catch(Exception failure){error(failure);}});
        toggle.addActionListener(e->{ModManager.Mod selected=selected();if(selected!=null)try{manager.toggle(selected.file());reload();message.setText(selected.enabled()?"已禁用：仅修改文件后缀":"已启用：已恢复 .jar 后缀");}catch(Exception failure){error(failure);}});
        uninstall.addActionListener(e->{ModManager.Mod selected=selected();if(selected!=null&&WindowChrome.prompt(this,"卸载 Mod","卸载“"+selected.name()+"”？文件会移入此版本的回收目录。",true)==JOptionPane.OK_OPTION)try{manager.uninstall(selected.file());reload();message.setText("已卸载，可在 mods/.mx-trash 中找回文件。");}catch(Exception failure){error(failure);}});
        TransferHandler drop=new TransferHandler(){@Override public boolean canImport(TransferSupport support){return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);}@Override public boolean importData(TransferSupport support){if(!canImport(support))return false;try{Object value=support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);if(value instanceof List<?> list){List<Path> files=new ArrayList<>();for(Object file:list)if(file instanceof File f)files.add(f.toPath());importFiles(files);return true;}}catch(Exception failure){error(failure);}return false;}};
        table.setTransferHandler(drop);root.setTransferHandler(drop);scroll.setTransferHandler(drop);
        JPanel modPage=UiTheme.transparent(new BorderLayout(0,18));root.remove(scroll);root.remove(bottom);modPage.add(scroll,BorderLayout.CENTER);modPage.add(bottom,BorderLayout.SOUTH);
        CardLayout cards=new CardLayout();UiMotion.Pages host=new UiMotion.Pages(cards);host.add(modPage,"mods");
        Path home=gameRoot.toAbsolutePath().getParent();MemoryPanel memory=new MemoryPanel(true,MemoryAllocation.load(gameRoot,version),()->Settings.load(home));
        JPanel gamePage=UiTheme.transparent(new BorderLayout());JPanel memoryArea=UiTheme.transparent(new BorderLayout(0,12));memoryArea.setBorder(new EmptyBorder(12,4,12,4));memoryArea.add(UiTheme.label("游戏内存分配",16,true),BorderLayout.NORTH);memoryArea.add(memory,BorderLayout.CENTER);gamePage.add(memoryArea,BorderLayout.NORTH);
        JPanel saveArea=UiTheme.transparent(new FlowLayout(FlowLayout.RIGHT,0,0));JLabel saved=UiTheme.muted("",12);UiTheme.Button save=new UiTheme.Button("保存设置","",UiTheme.Tone.PRIMARY);saveArea.add(saved);saveArea.add(save);gamePage.add(saveArea,BorderLayout.SOUTH);
        save.addActionListener(e->{try{MemoryAllocation.save(gameRoot,version,memory.choice());saved.setText("已保存，下次启动生效  ");}catch(Exception failure){WindowChrome.prompt(this,"设置未保存",failure.getMessage(),false);}});host.add(gamePage,"game");
        JPanel body=UiTheme.transparent(new BorderLayout(0,12));JPanel tabs=UiTheme.transparent(new FlowLayout(FlowLayout.LEFT,6,0));UiTheme.Button mods=new UiTheme.Button("Mod 管理","",UiTheme.Tone.NAV),game=new UiTheme.Button("游戏设置","",UiTheme.Tone.NAV);mods.selected=true;tabs.add(mods);tabs.add(game);mods.addActionListener(e->{host.transition(()->cards.show(host,"mods"));mods.selected=true;game.selected=false;tabs.repaint();});game.addActionListener(e->{host.transition(()->cards.show(host,"game"));mods.selected=false;game.selected=true;tabs.repaint();});body.add(tabs,BorderLayout.NORTH);body.add(host,BorderLayout.CENTER);root.add(body,BorderLayout.CENTER);
        setContentPane(root);WindowChrome.dialog(this);setSize(860,600);setMinimumSize(new Dimension(780,540));setLocationRelativeTo(owner);reload();
    }
    void importFiles(List<Path> files){if(files.isEmpty())return;message.setText("正在添加 Mod…");new SwingWorker<Integer,Void>(){String failures="";@Override protected Integer doInBackground(){int added=0;for(Path file:files)try{manager.add(file);added++;}catch(Exception e){failures=e.getMessage();}return added;}@Override protected void done(){try{int count=get();reload();message.setText("已添加 "+count+" 个 Mod"+(failures.isBlank()?"":"；"+failures));}catch(Exception e){error(e);}}}.execute();}
    private ModManager.Mod selected(){int row=table.getSelectedRow();if(row<0){message.setText("请先选择一个 Mod。");return null;}return model.rows.get(table.convertRowIndexToModel(row));}
    private void reload()throws Exception{model.rows=manager.list();model.fireTableDataChanged();}
    private void open(Path folder){try{Desktop.getDesktop().open(folder.toFile());}catch(Exception e){error(e);}}
    private void error(Exception e){message.setText(e.getMessage());message.setToolTipText(e.toString());}
    private static final class Model extends AbstractTableModel{List<ModManager.Mod> rows=List.of();String[] names={"Mod","版本","状态","文件名"};@Override public int getRowCount(){return rows.size();}@Override public int getColumnCount(){return 4;}@Override public String getColumnName(int c){return names[c];}@Override public Object getValueAt(int r,int c){ModManager.Mod mod=rows.get(r);return switch(c){case 0->mod.name();case 1->mod.version();case 2->mod.enabled()?"已启用":"已禁用";default->mod.file().getFileName();};}}
}

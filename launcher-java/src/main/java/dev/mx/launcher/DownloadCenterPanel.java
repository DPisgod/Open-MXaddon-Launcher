package dev.mx.launcher;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/** Launcher-native download workspace: horizontal categories, version library, resource inspector. */
final class DownloadCenterPanel extends JPanel implements AutoCloseable {
    private final Window owner;
    private final Path root;
    private final Consumer<String> installed;
    private final boolean network;
    private final CardLayout pages=new CardLayout();
    private final JPanel body=UiTheme.transparent(pages);
    private final JPanel featured=UiTheme.transparent(new GridLayout(1,2,16,0));
    private final DefaultListModel<DownloadCatalog.Game> model=new DefaultListModel<>();
    private final JList<DownloadCatalog.Game> versions=new JList<>(model);
    private final JComboBox<String> group=new JComboBox<>(new String[]{"正式版","预览版","远古版","愚人节","全部版本"});
    private final JTextField query=new JTextField();
    private final JLabel status=UiTheme.muted("正在读取 Mojang 官方版本列表…",12);
    private final UiTheme.Button install=new UiTheme.Button("配置并安装","download",UiTheme.Tone.PRIMARY);
    private final List<UiTheme.Button> navigation=new ArrayList<>();
    private final ContentBrowserPanel resources;
    private List<DownloadCatalog.Game> games=List.of();
    private boolean closed;

    DownloadCenterPanel(Window owner,Path root,String selected,Consumer<String> installed,boolean network){
        super(new BorderLayout(0,18));this.owner=owner;this.root=root;this.installed=installed;this.network=network;
        setOpaque(false);setBorder(new EmptyBorder(22,26,22,26));
        JPanel heading=UiTheme.transparent(new BorderLayout());JPanel titles=UiTheme.transparent(new BorderLayout(0,5));titles.add(UiTheme.label("发现你的下一个世界",24,true),BorderLayout.NORTH);titles.add(UiTheme.muted("安装游戏、搭配加载器，寻找值得一玩的社区内容。",12),BorderLayout.SOUTH);heading.add(titles,BorderLayout.CENTER);
        UiTheme.Button refresh=new UiTheme.Button("刷新版本","",UiTheme.Tone.SURFACE);refresh.addActionListener(e->loadGames());JPanel refreshArea=UiTheme.transparent(new FlowLayout(FlowLayout.RIGHT,0,0));refreshArea.add(refresh);heading.add(refreshArea,BorderLayout.EAST);
        JPanel tabs=UiTheme.transparent(new GridLayout(1,6,8,0));
        String[] names={"游戏版本","Mod","整合包","数据包","资源包","光影包"};
        ContentCatalog.Kind[] kinds={null,ContentCatalog.Kind.MOD,ContentCatalog.Kind.PACK,ContentCatalog.Kind.DATA,ContentCatalog.Kind.RESOURCE,ContentCatalog.Kind.SHADER};
        for(int i=0;i<names.length;i++){int index=i;UiTheme.Button button=new UiTheme.Button(names[i],"",UiTheme.Tone.NAV);button.setPreferredSize(new Dimension(110,38));navigation.add(button);tabs.add(button);button.addActionListener(e->show(index,kinds[index]));}
        JPanel top=UiTheme.transparent(new BorderLayout(0,14));top.add(heading,BorderLayout.NORTH);top.add(tabs,BorderLayout.SOUTH);add(top,BorderLayout.NORTH);
        resources=new ContentBrowserPanel(owner,root,selected,this::complete,network);resources.onClose(()->show(0,null));
        body.add(gameLibrary(),"games");body.add(resources,"resources");add(body,BorderLayout.CENTER);navigation.get(0).selected=true;
        UiTheme.refresh(this);if(network)loadGames();else status.setText("离线预览 · 可注入测试版本列表");
    }
    @Override protected void paintComponent(Graphics graphics){graphics.setColor(UiTheme.p().background());graphics.fillRect(0,0,getWidth(),getHeight());super.paintComponent(graphics);}
    private JPanel gameLibrary(){
        JPanel panel=UiTheme.transparent(new BorderLayout(0,14));
        featured.setPreferredSize(new Dimension(100,96));panel.add(featured,BorderLayout.NORTH);
        UiTheme.Surface library=new UiTheme.Surface(new BorderLayout(0,12),20);library.setBorder(new EmptyBorder(18,18,16,18));
        JPanel filters=UiTheme.transparent(new BorderLayout(12,0));UiTheme.combo(group);group.setPreferredSize(new Dimension(150,36));query.setFont(UiTheme.font(13,false));query.setToolTipText("搜索版本号，例如 1.21.1、b1.7.3、snapshot");query.setName("gameVersionSearch");filters.add(group,BorderLayout.WEST);filters.add(query,BorderLayout.CENTER);filters.add(UiTheme.label("版本库",15,true),BorderLayout.EAST);library.add(filters,BorderLayout.NORTH);
        versions.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);versions.setFixedCellHeight(63);versions.setName("gameVersions");versions.setCellRenderer((list,value,index,selected,focus)->{
            JPanel row=UiTheme.transparent(new BorderLayout(12,0));row.setOpaque(true);row.setBackground(selected?UiTheme.p().line():UiTheme.p().surface());row.setBorder(new EmptyBorder(10,12,10,12));JPanel text=UiTheme.transparent(new GridLayout(2,1,0,4));text.add(UiTheme.label(value.id(),14,true));text.add(UiTheme.muted(value.category()+" · "+value.date(),11));row.add(text,BorderLayout.CENTER);row.add(UiTheme.muted("选择加载器  ›",11),BorderLayout.EAST);return row;
        });
        JScrollPane scroll=new JScrollPane(versions);scroll.setBorder(null);UiTheme.scroll(scroll);library.add(scroll,BorderLayout.CENTER);
        JPanel footer=UiTheme.transparent(new BorderLayout(12,0));footer.add(status,BorderLayout.CENTER);footer.add(install,BorderLayout.EAST);library.add(footer,BorderLayout.SOUTH);panel.add(library,BorderLayout.CENTER);
        install.setEnabled(false);install.addActionListener(e->configure(versions.getSelectedValue()));versions.addListSelectionListener(e->install.setEnabled(versions.getSelectedValue()!=null));
        versions.addMouseListener(new java.awt.event.MouseAdapter(){public void mouseClicked(java.awt.event.MouseEvent e){if(e.getClickCount()==2&&versions.locationToIndex(e.getPoint())>=0)configure(versions.getSelectedValue());}});
        group.addActionListener(e->filter());query.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){filter();}public void removeUpdate(DocumentEvent e){filter();}public void changedUpdate(DocumentEvent e){filter();}});
        return panel;
    }
    private void show(int index,ContentCatalog.Kind kind){
        if(resources.busy())return;for(int i=0;i<navigation.size();i++)navigation.get(i).selected=i==index;
        pages.show(body,index==0?"games":"resources");if(kind!=null)resources.selectKind(kind);repaint();
    }
    private void configure(DownloadCatalog.Game game){if(game==null)return;new InstallVersionDialog(owner,root,this::complete,network,game).setVisible(true);}
    private void complete(String id){installed.accept(id);resources.refreshTargets(id);}
    void selectInstance(String id){resources.refreshTargets(id);}
    private void loadGames(){
        if(!network)return;status.setText("正在获取官方版本列表…");
        new SwingWorker<List<DownloadCatalog.Game>,Void>(){protected List<DownloadCatalog.Game> doInBackground()throws Exception{return new DownloadCatalog().games();}protected void done(){if(closed)return;try{setGames(get());}catch(Exception e){status.setText("版本列表读取失败，请点击刷新重试");status.setToolTipText(InstallVersionDialog.installationFailure(e).getMessage());}}}.execute();
    }
    void setGames(List<DownloadCatalog.Game> values){games=List.copyOf(values);featured.removeAll();feature("最新正式版",values.stream().filter(g->g.type().equals("release")).findFirst().orElse(null));feature("最新预览版",values.stream().filter(g->g.type().equals("snapshot")&&!g.special()).findFirst().orElse(null));filter();featured.revalidate();featured.repaint();}
    private void feature(String title,DownloadCatalog.Game game){
        UiTheme.Surface card=new UiTheme.Surface(new BorderLayout(14,0),20);card.setBorder(new EmptyBorder(14,18,14,16));JPanel text=UiTheme.transparent(new GridLayout(3,1,0,3));text.add(UiTheme.muted(title,11));text.add(UiTheme.label(game==null?"暂未获取":game.id(),20,true));text.add(UiTheme.muted(game==null?"刷新版本列表后重试":game.date(),10));card.add(text,BorderLayout.CENTER);
        UiTheme.Button action=new UiTheme.Button("安装","download",UiTheme.Tone.PRIMARY);action.setEnabled(game!=null);action.addActionListener(e->configure(game));JPanel actionArea=UiTheme.transparent(new GridBagLayout());actionArea.add(action);card.add(actionArea,BorderLayout.EAST);featured.add(card);
    }
    private void filter(){model.clear();String selected=Objects.toString(group.getSelectedItem(),""),text=query.getText().strip().toLowerCase(Locale.ROOT);for(var game:games)if((selected.equals("全部版本")||selected.equals(game.category()))&&game.id().toLowerCase(Locale.ROOT).contains(text))model.addElement(game);status.setText(model.size()+" 个版本 · 历史版本含 Beta / Alpha");install.setEnabled(false);}
    @Override public void close(){closed=true;resources.close();}
    boolean busy(){return resources.busy();}
}

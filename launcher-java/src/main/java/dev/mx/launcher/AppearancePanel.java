package dev.mx.launcher;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Predicate;

/** H: local wallpaper library, preview and explicitly applied playback settings. */
final class AppearancePanel extends JPanel implements AutoCloseable {
    private final WallpaperLibrary library;
    private final Predicate<Appearance> apply;
    private final Runnable back;
    private final WallpaperPlayer previewPlayer=new WallpaperPlayer();
    private final WallpaperView preview;
    private final JPanel grid;
    private final JLabel message=UiTheme.muted("选择壁纸，在右侧预览后应用。",12);
    private final JLabel previewName=UiTheme.muted("内置风景 · 静态壁纸",12);
    private final UiTheme.Button importButton=new UiTheme.Button("导入壁纸","upload",UiTheme.Tone.PRIMARY);
    private final UiTheme.Button applyButton=new UiTheme.Button("应用壁纸","",UiTheme.Tone.PRIMARY);
    private final UiTheme.Button playButton=new UiTheme.Button("暂停预览","pause",UiTheme.Tone.SURFACE);
    private Appearance draft;
    private WallpaperLibrary.Item selected;
    private boolean importing,active,manualPause;
    private final List<Tile> tiles=new ArrayList<>();

    AppearancePanel(WallpaperLibrary library,Appearance saved,Predicate<Appearance> apply,Runnable back){
        this.library=library;this.draft=saved.copy();this.apply=apply;this.back=back;
        setOpaque(false);setLayout(new BorderLayout(0,20));setBorder(new EmptyBorder(26,30,26,30));
        JPanel heading=UiTheme.transparent(new BorderLayout());
        JPanel titles=UiTheme.transparent(new GridLayout(2,1,0,5));titles.add(UiTheme.label("让启动器，成为你的风景",24,true));titles.add(UiTheme.muted("导入你喜欢的图片或视频，打造专属桌面。",12));heading.add(titles,BorderLayout.CENTER);
        UiTheme.Button returnButton=new UiTheme.Button("返回主页","home",UiTheme.Tone.SURFACE);returnButton.setName("returnHome");returnButton.setPreferredSize(new Dimension(102,38));returnButton.addActionListener(e->back.run());JPanel returnArea=UiTheme.transparent(new FlowLayout(FlowLayout.RIGHT,0,0));returnArea.add(returnButton);heading.add(returnArea,BorderLayout.EAST);add(heading,BorderLayout.NORTH);

        JPanel columns=UiTheme.transparent(new GridBagLayout());GridBagConstraints c=new GridBagConstraints();c.gridy=0;c.fill=GridBagConstraints.BOTH;c.weighty=1;c.insets=new Insets(0,0,0,20);c.gridx=0;c.weightx=.58;
        UiTheme.Surface left=new UiTheme.Surface(new BorderLayout(0,13),22);left.setBorder(new EmptyBorder(20,20,18,20));
        JPanel libraryHead=UiTheme.transparent(new BorderLayout(12,0));libraryHead.add(UiTheme.label("我的壁纸",19,true),BorderLayout.WEST);libraryHead.add(importButton,BorderLayout.EAST);left.add(libraryHead,BorderLayout.NORTH);
        grid=UiTheme.transparent(new GridLayout(0,2,12,12));JPanel gridHolder=new LibraryViewport();gridHolder.add(grid,BorderLayout.NORTH);
        JScrollPane scroll=new JScrollPane(gridHolder);scroll.setBorder(null);scroll.setOpaque(false);scroll.getViewport().setOpaque(false);scroll.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);scroll.getVerticalScrollBar().setUnitIncrement(22);scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);UiTheme.scroll(scroll);left.add(scroll,BorderLayout.CENTER);
        JPanel libraryBottom=UiTheme.transparent(new BorderLayout(0,9));libraryBottom.add(UiTheme.muted("JPG · PNG · WebP · GIF · MP4 · WebM",11),BorderLayout.NORTH);
        UiTheme.Button drop=new UiTheme.Button("拖入图片或视频文件","upload",UiTheme.Tone.SURFACE);drop.setPreferredSize(new Dimension(100,48));drop.addActionListener(e->chooseFiles());libraryBottom.add(drop,BorderLayout.CENTER);libraryBottom.add(message,BorderLayout.SOUTH);left.add(libraryBottom,BorderLayout.SOUTH);
        columns.add(left,c);

        UiTheme.Surface right=new UiTheme.Surface(new BorderLayout(0,12),22);right.setBorder(new EmptyBorder(20,20,18,20));
        JPanel rightTitle=UiTheme.transparent(new BorderLayout());rightTitle.add(UiTheme.label("壁纸预览",19,true),BorderLayout.WEST);rightTitle.add(playButton,BorderLayout.EAST);right.add(rightTitle,BorderLayout.NORTH);
        JPanel stack=UiTheme.transparent(new GridBagLayout());GridBagConstraints r=new GridBagConstraints();r.gridx=0;r.gridy=0;r.weightx=1;r.fill=GridBagConstraints.BOTH;r.weighty=1;
        preview=new WallpaperView(previewPlayer,draft,14);preview.previewUi=true;preview.setPreferredSize(new Dimension(340,195));preview.setMinimumSize(new Dimension(280,130));stack.add(preview,r);
        JPanel controls=UiTheme.transparent(new GridBagLayout());controls.setBorder(new EmptyBorder(11,0,0,0));
        GridBagConstraints g=new GridBagConstraints();g.gridx=0;g.gridy=0;g.gridwidth=2;g.weightx=1;g.fill=GridBagConstraints.HORIZONTAL;g.insets=new Insets(0,0,12,0);controls.add(previewName,g);g.gridwidth=1;
        JComboBox<String> fit=new JComboBox<>(new String[]{"填充","适应","拉伸"});UiTheme.combo(fit);fit.setSelectedIndex("contain".equals(draft.fit)?1:"stretch".equals(draft.fit)?2:0);
        addRow(controls,g,1,"显示方式",fit);
        JSlider blur=new JSlider(0,100,draft.blur),mask=new JSlider(0,85,draft.mask);UiTheme.slider(blur);UiTheme.slider(mask);
        addRow(controls,g,2,"模糊度",slider(blur));addRow(controls,g,3,"遮罩强度",slider(mask));
        JSlider transparency=new JSlider(0,100,draft.uiTransparency);transparency.setName("uiTransparency");transparency.setToolTipText("0% 为不透明，100% 为完全透明；仅调整顶栏和启动栏背景");UiTheme.slider(transparency);addRow(controls,g,4,"UI 透明度",slider(transparency));
        JCheckBox loop=check("循环播放",draft.loop),mute=check("静音播放",draft.muted),pause=check("游戏启动后暂停",draft.pauseWithGame);
        g.gridx=0;g.gridwidth=2;g.gridy=5;g.insets=new Insets(3,0,2,0);controls.add(loop,g);g.gridy=6;controls.add(mute,g);g.gridy=7;controls.add(pause,g);
        r.gridy=1;r.weighty=0;stack.add(controls,r);right.add(stack,BorderLayout.CENTER);
        JPanel applyArea=UiTheme.transparent(new BorderLayout(0,8));applyButton.setPreferredSize(new Dimension(100,44));applyArea.add(applyButton,BorderLayout.CENTER);applyArea.add(UiTheme.muted("预览不会覆盖当前壁纸，点击应用后保存。",10),BorderLayout.SOUTH);right.add(applyArea,BorderLayout.SOUTH);
        c.gridx=1;c.weightx=.42;c.insets=new Insets(0,0,0,0);columns.add(right,c);add(columns,BorderLayout.CENTER);

        importButton.addActionListener(e->chooseFiles());applyButton.addActionListener(e->{message.setText(apply.test(draft.copy())?"已应用壁纸，外观设置已保存。":"保存失败，请检查启动器目录的写入权限。");});
        fit.addActionListener(e->{draft.fit=new String[]{"cover","contain","stretch"}[fit.getSelectedIndex()];preview.options(draft);});
        blur.addChangeListener(e->{draft.blur=blur.getValue();preview.options(draft);});mask.addChangeListener(e->{draft.mask=mask.getValue();preview.options(draft);});
        transparency.addChangeListener(e->{draft.uiTransparency=transparency.getValue();preview.options(draft);});
        loop.addActionListener(e->{draft.loop=loop.isSelected();restartPreview();});mute.addActionListener(e->{draft.muted=mute.isSelected();restartPreview();});pause.addActionListener(e->draft.pauseWithGame=pause.isSelected());
        playButton.addActionListener(e->{manualPause=!manualPause;previewPlayer.setPaused(!active||manualPause);playButton.setText(manualPause?"继续预览":"暂停预览");});
        previewPlayer.onChange(preview::repaint);previewPlayer.onError(error->message.setText("预览失败："+error));
        TransferHandler dropHandler=new TransferHandler(){
            @Override public boolean canImport(TransferSupport support){return !importing&&support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);}
            @Override public boolean importData(TransferSupport support){if(!canImport(support))return false;try{Object value=support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);if(value instanceof List<?> list){List<Path> files=new ArrayList<>();for(Object file:list)if(file instanceof File f)files.add(f.toPath());importPaths(files);return true;}}catch(Exception e){message.setText("无法导入拖放的文件");}return false;}
        };
        setTransferHandler(dropHandler);left.setTransferHandler(dropHandler);grid.setTransferHandler(dropHandler);drop.setTransferHandler(dropHandler);
        selected=library.find(draft.wallpaper);rebuildTiles();restartPreview();
    }
    @Override protected void paintComponent(Graphics graphics){graphics.setColor(UiTheme.p().background());graphics.fillRect(0,0,getWidth(),getHeight());super.paintComponent(graphics);}
    private JCheckBox check(String text,boolean selected){return new UiTheme.Toggle(text,selected);}
    private JPanel slider(JSlider slider){
        JPanel row=UiTheme.transparent(new BorderLayout(8,0));JLabel value=UiTheme.muted(slider.getValue()+"%",11);value.setPreferredSize(new Dimension(36,25));slider.addChangeListener(e->value.setText(slider.getValue()+"%"));row.add(slider,BorderLayout.CENTER);row.add(value,BorderLayout.EAST);return row;
    }
    private void addRow(JPanel panel,GridBagConstraints g,int row,String name,JComponent control){g.gridy=row;g.gridx=0;g.weightx=0;g.gridwidth=1;g.insets=new Insets(3,0,7,10);panel.add(UiTheme.muted(name,12),g);g.gridx=1;g.weightx=1;g.insets=new Insets(3,0,7,0);panel.add(control,g);}
    private void chooseFiles(){
        if(importing)return;Window owner=SwingUtilities.getWindowAncestor(this);
        FileDialog chooser=owner instanceof Frame?new FileDialog((Frame)owner,"导入壁纸 · 图片或视频",FileDialog.LOAD):new FileDialog((Dialog)owner,"导入壁纸 · 图片或视频",FileDialog.LOAD);
        chooser.setMultipleMode(true);chooser.setFilenameFilter((directory,name)->{int dot=name.lastIndexOf('.');return dot>=0&&WallpaperLibrary.FORMATS.contains(name.substring(dot+1).toLowerCase(java.util.Locale.ROOT));});
        chooser.setVisible(true);File[] files=chooser.getFiles();chooser.dispose();
        if(files.length>0)importPaths(java.util.Arrays.stream(files).map(File::toPath).toList());
    }
    void importPaths(List<Path> paths){
        if(importing||paths.isEmpty())return;importing=true;importButton.setEnabled(false);applyButton.setEnabled(false);message.setText("正在读取并导入壁纸…");
        new SwingWorker<List<WallpaperLibrary.Item>,Void>(){
            String failure="";
            @Override protected List<WallpaperLibrary.Item> doInBackground(){List<WallpaperLibrary.Item> added=new ArrayList<>();for(Path path:paths)try{added.add(library.importFile(path));}catch(Exception e){failure=e.getMessage();}return added;}
            @Override protected void done(){importing=false;importButton.setEnabled(true);applyButton.setEnabled(true);try{List<WallpaperLibrary.Item> added=get();rebuildTiles();if(!added.isEmpty())select(added.get(added.size()-1));message.setText(failure.isBlank()?"已导入 "+added.size()+" 张壁纸，点击应用即可使用。":"部分文件未导入："+failure);}catch(Exception e){message.setText("导入失败，请检查文件后重试。");}}
        }.execute();
    }
    private void rebuildTiles(){
        grid.removeAll();tiles.clear();Tile builtIn=new Tile(null);tiles.add(builtIn);grid.add(builtIn);
        for(WallpaperLibrary.Item item:library.items()){Tile tile=new Tile(item);tiles.add(tile);grid.add(tile);}for(Tile tile:tiles)tile.setTransferHandler(getTransferHandler());grid.revalidate();grid.repaint();
    }
    private void select(WallpaperLibrary.Item item){selected=item;draft.wallpaper=item==null?"":item.id();manualPause=false;restartPreview();grid.repaint();}
    private void restartPreview(){
        preview.options(draft);previewPlayer.select(selected,draft);previewPlayer.setPaused(!active||manualPause);
        previewName.setText(selected==null?"内置风景 · 静态壁纸":selected.extension().toUpperCase()+" · "+(selected.animated()?"动态壁纸":"静态壁纸"));playButton.setEnabled(selected!=null&&selected.animated());playButton.setText(manualPause?"继续预览":"暂停预览");
    }
    void setActive(boolean value){active=value;previewPlayer.setPaused(!value||manualPause);}
    Appearance draft(){return draft.copy();}
    WallpaperPlayer previewPlayer(){return previewPlayer;}
    @Override public void close(){previewPlayer.close();}

    private static final class LibraryViewport extends JPanel implements Scrollable {
        LibraryViewport(){super(new BorderLayout());setOpaque(false);}
        @Override public Dimension getPreferredScrollableViewportSize(){return new Dimension(400,400);}
        @Override public int getScrollableUnitIncrement(Rectangle visible,int orientation,int direction){return 22;}
        @Override public int getScrollableBlockIncrement(Rectangle visible,int orientation,int direction){return Math.max(22,visible.height-40);}
        @Override public boolean getScrollableTracksViewportWidth(){return true;}
        @Override public boolean getScrollableTracksViewportHeight(){return false;}
    }

    private final class Tile extends JButton {
        final WallpaperLibrary.Item item;BufferedImage image;
        Tile(WallpaperLibrary.Item item){
            this.item=item;setPreferredSize(new Dimension(200,145));setMinimumSize(new Dimension(100,110));setBorderPainted(false);setContentAreaFilled(false);setFocusPainted(false);setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));getAccessibleContext().setAccessibleName(item==null?"内置风景":item.name());
            try{image=item==null?new WallpaperPlayer().frame():ImageIO.read(item.thumbnail().toFile());}catch(Exception ignored){}
            addActionListener(e->select(item));
        }
        @Override protected void paintComponent(Graphics graphics){
            Graphics2D g=(Graphics2D)graphics.create();Shape viewportClip=g.getClip();UiTheme.quality(g);int w=getWidth(),h=getHeight();g.clip(new java.awt.geom.RoundRectangle2D.Double(2,2,w-4,h-4,16,16));g.setColor(UiTheme.p().background());g.fillRect(0,0,w,h);
            if(image!=null){double scale=Math.max(w/(double)image.getWidth(),h/(double)image.getHeight());int iw=(int)(image.getWidth()*scale),ih=(int)(image.getHeight()*scale);g.drawImage(image,(w-iw)/2,(h-ih)/2,iw,ih,null);}
            g.setPaint(new GradientPaint(0,h/2f,new Color(0,0,0,0),0,h,new Color(0,0,0,190)));g.fillRect(0,0,w,h);g.setColor(Color.WHITE);g.setFont(UiTheme.font(12,true));String title=item==null?"内置风景":item.title();FontMetrics fm=g.getFontMetrics();while(title.length()>1&&fm.stringWidth(title)>w-24)title=title.substring(0,title.length()-1);g.drawString(title,12,h-15);
            String badge=item==null?"默认":item.extension().toUpperCase();g.setFont(UiTheme.font(10,false));int bw=g.getFontMetrics().stringWidth(badge)+14;g.setColor(new Color(17,30,46,175));g.fillRoundRect(w-bw-9,9,bw,22,7,7);g.setColor(Color.WHITE);g.drawString(badge,w-bw-2,24);
            if(item!=null&&item.animated()){g.setColor(new Color(0,0,0,100));g.fillOval(w/2-17,h/2-17,34,34);g.setColor(Color.WHITE);UiTheme.icon(g,"play",w/2-8,h/2-9,18);}
            g.setClip(viewportClip);boolean chosen=item==null?draft.wallpaper.isEmpty():item.id().equals(draft.wallpaper);if(chosen||UiTheme.showFocus(this)){g.setStroke(new BasicStroke(2));g.setColor(UiTheme.p().accent());g.drawRoundRect(1,1,w-3,h-3,16,16);}g.dispose();
        }
    }
}

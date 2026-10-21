package dev.mx.launcher;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.nio.file.Path;

/** A bounded floating card over the home wallpaper; it never occupies the launch dock or navigation. */
final class AnnouncementOverlay extends JLayeredPane implements AutoCloseable {
    private final AnnouncementFeed.State state;
    private final AnnouncementFeed feed;
    private UiTheme.Surface card;
    private AnnouncementFeed.Message current;
    private SwingWorker<AnnouncementFeed.Message,Void> loading;
    private boolean closed;

    AnnouncementOverlay(Path home) { this(home, new AnnouncementFeed()); }
    AnnouncementOverlay(Path home, AnnouncementFeed feed) {
        this.state = new AnnouncementFeed.State(home); this.feed = feed;
        setName("announcementOverlay"); setOpaque(false);
    }

    void checkOnStartup() {
        if (closed || loading != null) return;
        loading = new SwingWorker<>() {
            @Override protected AnnouncementFeed.Message doInBackground() throws IOException { return feed.fetch(); }
            @Override protected void done() {
                if (closed || isCancelled()) return;
                try { showMessage(get()); }
                catch (Exception failure) { System.err.println("[announcement] " + InstallVersionDialog.installationFailure(failure).getMessage()); }
            }
        };
        loading.execute();
    }

    void showMessage(AnnouncementFeed.Message message) {
        if (closed) return;
        removeAll(); card = null; current = message;
        if (!state.shouldShow(message)) { revalidate(); repaint(); return; }
        card = new UiTheme.Surface(new BorderLayout(0, 14), 22);
        card.setName("announcementCard"); card.setBorder(new EmptyBorder(18, 20, 16, 20));
        JPanel heading = UiTheme.transparent(new BorderLayout(10, 0)); heading.setName("announcementDragHandle");
        JLabel title = UiTheme.label("更新公告", 16, true);
        heading.add(title, BorderLayout.CENTER);
        UiTheme.Button close = new UiTheme.Button("", "close", UiTheme.Tone.PLAIN);
        close.setName("dismissAnnouncement"); close.setPreferredSize(new Dimension(28, 28));
        close.setToolTipText("关闭此公告"); close.getAccessibleContext().setAccessibleName("关闭此公告");
        close.addActionListener(e -> dismiss()); heading.add(close, BorderLayout.EAST);
        card.add(heading, BorderLayout.NORTH);
        JTextArea body = new JTextArea(message.text()); body.setName("announcementText");
        body.setFont(UiTheme.font(13, false)); body.setForeground(UiTheme.p().text()); body.setOpaque(false);
        body.setEditable(false); body.setLineWrap(true); body.setWrapStyleWord(true); body.setCaretPosition(0);
        body.setBorder(new EmptyBorder(0, 0, 0, 8)); body.setColumns(25);
        body.setRows(Math.min(10, Math.max(3, message.text().split("\n", -1).length + message.text().length() / 24)));
        JScrollPane scroll = new JScrollPane(body); scroll.setBorder(null); scroll.setOpaque(false); scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER); UiTheme.scroll(scroll);
        card.add(scroll, BorderLayout.CENTER);
        card.add(UiTheme.muted("拖动标题栏移动", 11), BorderLayout.SOUTH);
        bindDrag(heading); bindDrag(title);
        add(card, JLayeredPane.PALETTE_LAYER); doLayout(); revalidate(); repaint();
    }

    private void dismiss() {
        try { state.dismiss(current); removeAll(); card = null; revalidate(); repaint(); }
        catch (IOException failure) {
            WindowChrome.prompt(SwingUtilities.getWindowAncestor(this), "无法保存公告关闭状态", "请检查启动器 .minecraft 目录的写入权限后重试。", false);
        }
    }

    private void bindDrag(JComponent handle) {
        handle.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)); handle.setToolTipText("拖动以移动公告");
        MouseAdapter drag = new MouseAdapter() {
            Point start, origin;
            @Override public void mousePressed(MouseEvent event) {
                if (card != null && SwingUtilities.isLeftMouseButton(event)) {
                    start = SwingUtilities.convertPoint(handle, event.getPoint(), AnnouncementOverlay.this); origin = card.getLocation();
                }
            }
            @Override public void mouseDragged(MouseEvent event) {
                if (start == null || card == null) return;
                Point now = SwingUtilities.convertPoint(handle, event.getPoint(), AnnouncementOverlay.this);
                card.setLocation(clamp(origin.x + now.x - start.x, getWidth() - card.getWidth()), clamp(origin.y + now.y - start.y, getHeight() - card.getHeight()));
                repaint();
            }
            @Override public void mouseReleased(MouseEvent event) {
                if (start == null || card == null) return;
                start = null;
                try { state.move(card.getX(), card.getY()); }
                catch (IOException failure) { System.err.println("[announcement] 无法保存公告位置：" + failure.getMessage()); }
            }
        };
        handle.addMouseListener(drag); handle.addMouseMotionListener(drag);
    }

    @Override public void doLayout() {
        if (card == null || getWidth() <= 0 || getHeight() <= 0) return;
        int width = Math.min(400, Math.max(1, getWidth() - 32));
        int height = Math.min(card.getPreferredSize().height, Math.max(1, getHeight() - 32));
        int x = state.x < 0 ? getWidth() - width - 24 : state.x, y = state.y < 0 ? 24 : state.y;
        card.setBounds(clamp(x, getWidth() - width), clamp(y, getHeight() - height), width, height);
    }

    private static int clamp(int value, int maximum) { return Math.max(0, Math.min(value, maximum)); }

    @Override public void close() { closed = true; if (loading != null) loading.cancel(true); }
}

package dev.mx.launcher;

import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class AnnouncementUiTest {
    public static void main(String[] args) throws Exception {
        Path home = Files.createTempDirectory("mx-announcement-ui-"); LauncherPaths.override(home);
        Appearance appearance = new Appearance(); appearance.theme = "light"; appearance.save(home);
        AnnouncementFeed.Message first = AnnouncementFeed.message("欢迎使用 MXCloud 启动器！\n\n启动器现已支持 GitHub Release 更新检查。发现新版本时，你可以选择立即更新，也可以取消并继续使用当前版本。\n\n祝你游戏愉快。");
        SwingUtilities.invokeAndWait(() -> {
            LauncherFrame frame = new LauncherFrame(false); frame.setVisible(true); frame.validate();
            try {
                AnnouncementOverlay overlay = (AnnouncementOverlay) find(frame, "announcementOverlay");
                overlay.showMessage(first); frame.validate();
                Component card = find(overlay, "announcementCard"); check(card != null && card.isShowing(), "Announcement must float on the main view");
                if (args.length > 0) {
                    Path directory = Path.of(args[0]);
                    for (String theme : new String[]{"light", "dark"}) {
                        frame.previewTheme(theme); frame.validate();
                        BufferedImage image = new BufferedImage(frame.getContentPane().getWidth(), frame.getContentPane().getHeight(), BufferedImage.TYPE_INT_RGB);
                        Graphics2D graphics = image.createGraphics(); frame.getContentPane().printAll(graphics); graphics.dispose();
                        try { Files.createDirectories(directory); ImageIO.write(image, "png", directory.resolve("announcement-" + theme + ".png").toFile()); }
                        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
                    }
                }
                Component handle = find(overlay, "announcementDragHandle"); Point before = card.getLocation();
                mouse(handle, MouseEvent.MOUSE_PRESSED, 15, 12); mouse(handle, MouseEvent.MOUSE_DRAGGED, -85, 92); mouse(handle, MouseEvent.MOUSE_RELEASED, 15, 12);
                Point moved = card.getLocation(); check(!moved.equals(before), "Drag must move the announcement");
                AnnouncementOverlay restored = new AnnouncementOverlay(home); restored.setSize(overlay.getSize()); restored.showMessage(first);
                check(find(restored, "announcementCard").getLocation().equals(moved), "Moved position must persist"); restored.close();
                frame.setSize(1000, 740); frame.validate();
                check(card.getX() >= 0 && card.getY() >= 0 && card.getX() + card.getWidth() <= overlay.getWidth() && card.getY() + card.getHeight() <= overlay.getHeight(), "Resize must keep the close button reachable");
                frame.showAppearance(); check(!card.isShowing(), "Announcement must stay on home, not the wallpaper settings page");
                ((AbstractButton) find(overlay, "dismissAnnouncement")).doClick();
                check(find(overlay, "announcementCard") == null, "Close must hide the card");
                AnnouncementOverlay restart = new AnnouncementOverlay(home); restart.setSize(800, 500); restart.showMessage(first);
                check(find(restart, "announcementCard") == null, "Closed message must remain hidden after restart");
                restart.showMessage(AnnouncementFeed.message(first.text() + "\n新公告内容"));
                check(find(restart, "announcementCard") != null, "Edited announcement must reappear");
                restart.showMessage(AnnouncementFeed.message("长文本测试\n".repeat(500))); restart.validate();
                check(find(restart, "announcementCard").getHeight() <= 500, "Long announcement must remain bounded"); restart.close();
                AtomicInteger requests = new AtomicInteger();
                AnnouncementOverlay closed = new AnnouncementOverlay(home, new AnnouncementFeed(uri -> { requests.incrementAndGet(); throw new AssertionError("Closed view fetched network"); }));
                closed.close(); closed.checkOnStartup(); check(requests.get() == 0, "Disposed view must not start requests");
            } finally { frame.dispose(); }
        });
        System.out.println("ANNOUNCEMENT_DRAG_CLOSE_NEW_CONTENT_AND_THEME_OK"); System.exit(0);
    }
    private static void mouse(Component component, int id, int x, int y) {
        component.dispatchEvent(new MouseEvent(component, id, System.currentTimeMillis(), id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.BUTTON1_DOWN_MASK : 0, x, y, 1, false, id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.NOBUTTON : MouseEvent.BUTTON1));
    }
    private static Component find(Container parent, String name) {
        for (Component child : parent.getComponents()) {
            if (name.equals(child.getName())) return child;
            if (child instanceof Container container) { Component found = find(container, name); if (found != null) return found; }
        }
        return null;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}

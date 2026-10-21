package dev.mx.launcher;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 设置面板截图工具（自动验证设置界面）。
 *
 * <pre>
 *   java -cp mxaddon-launcher.jar dev.mx.launcher.SettingsShot [输出路径]
 * </pre>
 */
public final class SettingsShot {

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "false");
        final String out = args.length > 0 ? args[0] : "build/settings-shot.png";

        final Path home = Path.of(System.getProperty("user.dir"));
        final AtomicReference<SettingsDialog> ref = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            SettingsDialog d = new SettingsDialog(null, home, home.resolve(".minecraft"));
            ref.set(d);
            d.setLocationRelativeTo(null);
            // 模态对话框 setVisible 会阻塞 EDT，这里只做 pack/布局后手动显示
            d.setModal(false);
            d.setVisible(true);
        });

        Thread.sleep(900);

        final BufferedImage[] img = new BufferedImage[1];
        SwingUtilities.invokeAndWait(() -> {
            Component root = ref.get().getContentPane();
            int w = Math.max(1, root.getWidth());
            int h = Math.max(1, root.getHeight());
            BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = bi.createGraphics();
            root.paint(g);
            g.dispose();
            img[0] = bi;
        });

        File f = new File(out);
        if (f.getParentFile() != null) {
            f.getParentFile().mkdirs();
        }
        ImageIO.write(img[0], "png", f);
        System.out.println("设置面板截图: " + f.getAbsolutePath()
                + " (" + img[0].getWidth() + "x" + img[0].getHeight() + ")");

        SwingUtilities.invokeAndWait(() -> ref.get().dispose());
        Runtime.getRuntime().halt(0);
    }
}

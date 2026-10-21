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
 * 账号对话框截图（自动验证界面）。
 *
 * <pre>
 *   java -cp mxaddon-launcher.jar dev.mx.launcher.AccountShot [输出png] [offline|premium]
 * </pre>
 */
public final class AccountShot {

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "false");
        String out = args.length > 0 ? args[0] : "build/account-shot.png";
        String mode = args.length > 1 ? args[1] : "offline";

        Path home = Path.of(System.getProperty("user.home"), ".mxcloud-shot");
        java.nio.file.Files.createDirectories(home);

        Session cur = "premium".equals(mode)
                ? Session.premium("MC_haha_one", "9ff2b871ca614b549035fe0c10b65c23", "x", null)
                : Session.offline("Player");

        final AtomicReference<AccountDialog> ref = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            AccountDialog d = new AccountDialog(null, home, cur);
            d.setModal(false);
            d.setLocationRelativeTo(null);
            d.setVisible(true);
            ref.set(d);
        });
        Thread.sleep(900);

        final BufferedImage[] img = new BufferedImage[1];
        SwingUtilities.invokeAndWait(() -> {
            Component root = ref.get().getContentPane();
            BufferedImage bi = new BufferedImage(
                    Math.max(1, root.getWidth()), Math.max(1, root.getHeight()),
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D g = bi.createGraphics();
            root.paint(g);
            g.dispose();
            img[0] = bi;
        });

        File f = new File(out);
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        ImageIO.write(img[0], "png", f);
        System.out.println("账号对话框截图: " + f.getAbsolutePath()
                + " (" + img[0].getWidth() + "x" + img[0].getHeight() + ")");

        SwingUtilities.invokeAndWait(() -> ref.get().dispose());
        Runtime.getRuntime().halt(0);
    }
}

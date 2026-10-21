package dev.mx.launcher;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class LauncherUpdateDialogTest {
    public static void main(String[] args) throws Exception {
        AtomicInteger requests = new AtomicInteger(), installations = new AtomicInteger();
        byte[] data = "MZ-fixture".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        GitHubUpdater updater = new GitHubUpdater(uri -> {
            requests.incrementAndGet();
            return new HttpURLConnection(uri.toURL()) {
                public void disconnect() {}
                public boolean usingProxy() { return false; }
                public void connect() {}
                public int getResponseCode() { return 200; }
                public long getContentLengthLong() { return data.length; }
                public InputStream getInputStream() { return new ByteArrayInputStream(data); }
            };
        });
        GitHubUpdater.Release release = new GitHubUpdater.Release("1.2.0", URI.create("https://github.com/DPisgod/Open-MXaddon-Launcher/releases/download/v1.2.0/MXCloud-Setup-1.2.0.exe"), data.length, "");
        CountDownLatch installed = new CountDownLatch(1);
        SwingUtilities.invokeAndWait(() -> {
            UiTheme.set("light");
            LauncherUpdateDialog dialog = new LauncherUpdateDialog(null, release, updater, file -> { throw new AssertionError("Cancelled update ran installer"); });
            if (args.length > 0) {
                BufferedImage preview = new BufferedImage(dialog.getWidth(), dialog.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = preview.createGraphics(); dialog.getRootPane().printAll(graphics); graphics.dispose();
                try { javax.imageio.ImageIO.write(preview, "png", new File(args[0])); } catch (IOException e) { throw new UncheckedIOException(e); }
            }
            button(dialog, "取消").doClick();
            if (dialog.isDisplayable() || requests.get() != 0) throw new AssertionError("Cancel must close without downloads");
            LauncherUpdateDialog update = new LauncherUpdateDialog(null, release, updater, file -> {
                if (!java.util.Arrays.equals(data, Files.readAllBytes(file))) throw new AssertionError("Wrong installer");
                installations.incrementAndGet(); Files.delete(file); Files.delete(file.getParent()); installed.countDown();
            });
            button(update, "立即更新").doClick();
        });
        if (!installed.await(10, TimeUnit.SECONDS)) throw new AssertionError("Update did not launch installer");
        SwingUtilities.invokeAndWait(() -> { for (Window window : Window.getWindows()) window.dispose(); });
        if (requests.get() != 1 || installations.get() != 1) throw new AssertionError("Update must download and launch once");
        System.out.println("LAUNCHER_UPDATE_DIALOG_OK");
    }
    private static AbstractButton button(Container parent, String text) {
        for (Component child : parent.getComponents()) {
            if (child instanceof AbstractButton button && text.equals(button.getText())) return button;
            if (child instanceof Container container) { AbstractButton found = button(container, text); if (found != null) return found; }
        }
        return null;
    }
}

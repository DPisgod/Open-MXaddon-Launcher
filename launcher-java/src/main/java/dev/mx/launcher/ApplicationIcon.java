package dev.mx.launcher;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Shared OML monogram for Open-MXaddon-Launcher and the operating system. */
final class ApplicationIcon {
    private static final List<Image> IMAGES = load();

    private ApplicationIcon() {}

    static List<Image> images() { return IMAGES; }
    static Image brand() { return IMAGES.isEmpty() ? null : IMAGES.getLast(); }

    private static List<Image> load() {
        List<Image> images = new ArrayList<>();
        for (int size : new int[]{16, 24, 32, 48, 64, 128, 256}) {
            String path = "/icons/launcher-" + size + ".png";
            try (var stream = ApplicationIcon.class.getResourceAsStream(path)) {
                if (stream == null) throw new IOException("Missing application icon: " + path);
                var image = ImageIO.read(stream);
                if (image == null) throw new IOException("Invalid application icon: " + path);
                images.add(image);
            } catch (IOException error) {
                // An icon failure must not prevent the launcher from opening.
                System.err.println(error.getMessage());
            }
        }
        return List.copyOf(images);
    }
}

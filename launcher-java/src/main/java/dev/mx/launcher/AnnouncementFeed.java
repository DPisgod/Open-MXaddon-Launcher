package dev.mx.launcher;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Plain text on the default development branch, independent of launcher releases. */
final class AnnouncementFeed {
    static final URI SOURCE = URI.create("https://raw.githubusercontent.com/DPisgod/Open-MXaddon-Launcher/main/ANNOUNCEMENT.txt");
    static final int MAX_BYTES = 32 * 1024;
    record Message(String text, String id) {}
    @FunctionalInterface interface Connections { HttpURLConnection open(URI source) throws IOException; }
    private final Connections connections;

    AnnouncementFeed() { this(source -> (HttpURLConnection) source.toURL().openConnection()); }
    AnnouncementFeed(Connections connections) { this.connections = connections; }

    Message fetch() throws IOException {
        HttpURLConnection connection = connections.open(SOURCE);
        try {
            connection.setConnectTimeout(7000); connection.setReadTimeout(7000);
            connection.setUseCaches(false); connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "OML-Launcher/" + ApplicationVersion.CURRENT);
            connection.setRequestProperty("Accept", "text/plain");
            connection.setRequestProperty("Cache-Control", "no-cache");
            int code = connection.getResponseCode();
            if (code == 404 || code == 204) return null;
            if (code != 200) throw new IOException("公告读取失败（HTTP " + code + "）");
            try (InputStream input = connection.getInputStream()) {
                byte[] bytes = input.readNBytes(MAX_BYTES + 1);
                if (bytes.length > MAX_BYTES) throw new IOException("公告文本超过 32 KB");
                return message(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString());
            }
        } finally { connection.disconnect(); }
    }

    static Message message(String text) {
        if (text == null) return null;
        String normalized = text.replaceFirst("^\uFEFF", "").replace("\r\n", "\n").replace('\r', '\n').strip();
        if (normalized.isEmpty()) return null;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
            return new Message(normalized, HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    /** Local acknowledgement and position only; never written back to the repository. */
    static final class State {
        private final Path file;
        private String dismissed = "";
        int x = -1, y = -1;

        State(Path home) {
            file = home.resolve(".minecraft").resolve("announcement.properties");
            Properties values = new Properties();
            try (InputStream input = Files.newInputStream(file)) { values.load(input); }
            catch (IOException | IllegalArgumentException ignored) { return; }
            dismissed = values.getProperty("dismissed", "");
            try { x = Integer.parseInt(values.getProperty("x", "-1")); y = Integer.parseInt(values.getProperty("y", "-1")); }
            catch (NumberFormatException ignored) { x = y = -1; }
        }

        boolean shouldShow(Message message) { return message != null && !message.id().equals(dismissed); }

        void dismiss(Message message) throws IOException {
            String previous = dismissed; dismissed = message.id();
            try { save(); } catch (IOException failure) { dismissed = previous; throw failure; }
        }

        void move(int x, int y) throws IOException { this.x = x; this.y = y; save(); }

        private void save() throws IOException {
            Files.createDirectories(file.getParent());
            Properties values = new Properties();
            values.setProperty("dismissed", dismissed); values.setProperty("x", Integer.toString(x)); values.setProperty("y", Integer.toString(y));
            Path temporary = Files.createTempFile(file.getParent(), "announcement-", ".tmp");
            try {
                try (OutputStream output = Files.newOutputStream(temporary)) { values.store(output, "OML announcement preferences"); }
                try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(temporary); }
        }
    }
}

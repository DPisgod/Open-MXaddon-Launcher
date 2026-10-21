package dev.mx.launcher;

import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

public final class AnnouncementFeedTest {
    public static void main(String[] args) throws Exception {
        Path home = Files.createTempDirectory("mx-announcement-state-");
        AnnouncementFeed.Message first = AnnouncementFeed.message("第一条公告\n欢迎使用");
        AnnouncementFeed.State state = new AnnouncementFeed.State(home);
        check(state.shouldShow(first), "New message must appear");
        state.move(123, 87); state.dismiss(first);
        AnnouncementFeed.State restart = new AnnouncementFeed.State(home);
        check(!restart.shouldShow(first), "Dismissal must survive restart");
        check(restart.x == 123 && restart.y == 87, "Position must survive restart");
        check(!restart.shouldShow(AnnouncementFeed.message("\uFEFF第一条公告\r\n欢迎使用\r\n")), "BOM and line endings must not redisplay the same message");
        AnnouncementFeed.Message changed = AnnouncementFeed.message("第一条公告\n欢迎使用新版本");
        check(restart.shouldShow(changed), "Edited content must appear");
        restart.dismiss(changed);
        check(!new AnnouncementFeed.State(home).shouldShow(changed), "Updated dismissal must also persist");
        check(!restart.shouldShow(AnnouncementFeed.message(" \n\r\n")), "Empty announcement must be hidden");
        Files.writeString(home.resolve(".min/announcement.properties"), "x=broken\ny=42\ndismissed=unused\n");
        check(new AnnouncementFeed.State(home).x == -1, "Corrupt position must use default");

        AtomicReference<byte[]> response = new AtomicReference<>("在线公告\n第二行".getBytes(StandardCharsets.UTF_8));
        int[] status = {200};
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            check("text/plain".equals(exchange.getRequestHeaders().getFirst("Accept")), "Fetch plain text");
            check("no-cache".equals(exchange.getRequestHeaders().getFirst("Cache-Control")), "Fetch fresh content");
            byte[] bytes = response.get(); exchange.sendResponseHeaders(status[0], bytes.length);
            try (OutputStream output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        AnnouncementFeed feed = new AnnouncementFeed(uri -> (HttpURLConnection) new URL("http://127.0.0.1:" + server.getAddress().getPort()).openConnection());
        try {
            check(feed.fetch().text().equals("在线公告\n第二行"), "Fetch UTF-8 text");
            response.set(new byte[0]); check(feed.fetch() == null, "Empty file");
            status[0] = 404; check(feed.fetch() == null, "Deleted file");
            status[0] = 503; expectFailure(feed, "Server failure");
            status[0] = 200; response.set(new byte[]{(byte) 0xC3, 0x28}); expectFailure(feed, "Invalid UTF-8");
            byte[] oversized = new byte[AnnouncementFeed.MAX_BYTES + 1]; Arrays.fill(oversized, (byte) 'a'); response.set(oversized);
            expectFailure(feed, "Oversized announcement");
        } finally { server.stop(0); }
        AnnouncementFeed offline = new AnnouncementFeed(uri -> { throw new IOException("Offline"); });
        expectFailure(offline, "Offline connection");
        System.out.println("ANNOUNCEMENT_FEED_AND_DISMISSAL_OK");
    }
    private static void expectFailure(AnnouncementFeed feed, String message) throws Exception {
        try { feed.fetch(); throw new AssertionError(message); } catch (IOException expected) {}
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}

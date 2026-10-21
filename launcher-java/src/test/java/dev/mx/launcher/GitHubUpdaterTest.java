package dev.mx.launcher;

import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.*;

/** Local HTTP fixtures exercise redirects, interrupted bodies and checksum failures without installing anything. */
public final class GitHubUpdaterTest {
    private static final byte[] INSTALLER = "MZ-test-installer-content".getBytes(StandardCharsets.UTF_8);
    private static String hash;
    private static String fixture;
    private static int status = 200;
    private static byte[] payload = INSTALLER;
    private static String redirect = "https://release-assets.githubusercontent.com/installer";
    private static final AtomicInteger downloads = new AtomicInteger();

    public static void main(String[] args) throws Exception {
        hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(INSTALLER));
        fixture = release("v1.2.0", "MXCloud-Setup-1.2.0.exe", hash);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/latest")) {
                byte[] data = fixture.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, data.length);
                try (OutputStream out = exchange.getResponseBody()) { out.write(data); }
            } else if (path.endsWith(".exe")) {
                downloads.incrementAndGet(); exchange.getResponseHeaders().set("Location", redirect);
                exchange.sendResponseHeaders(302, -1); exchange.close();
            } else {
                exchange.sendResponseHeaders(200, payload.length);
                try (OutputStream out = exchange.getResponseBody()) { out.write(payload); }
            }
        });
        server.start();
        String local = "http://127.0.0.1:" + server.getAddress().getPort();
        GitHubUpdater updater = new GitHubUpdater(uri -> (HttpURLConnection) URI.create(local + uri.getRawPath()).toURL().openConnection());
        try {
            check(GitHubUpdater.compareVersions("1.10.0", "1.9.9") > 0, "Numeric version comparison");
            check(GitHubUpdater.compareVersions("v1.0.0", "1.0.0") == 0, "v tag");
            check(updater.latest("1.2.0") == null, "Same version");
            check(updater.latest("2.0.0") == null, "No downgrade");
            GitHubUpdater.Release latest = updater.latest("1.0.0");
            check(latest != null && latest.version().equals("1.2.0"), "New version");
            check(downloads.get() == 0, "Checking never downloads an installer");
            fixture = fixture.replace("\"prerelease\":false", "\"prerelease\":true");
            check(updater.latest("1.0.0") == null, "Ignore prereleases");
            fixture = release("v1.2.0", "MXCloud-Setup-1.2.0.exe", hash).replace("\"draft\":false", "\"draft\":true");
            check(updater.latest("1.0.0") == null, "Ignore drafts");
            fixture = release("v1.2.0-beta", "MXCloud-Setup-1.2.0-beta.exe", hash);
            check(updater.latest("1.0.0") == null, "Ignore non-release tags");
            fixture = release("v1.2.0", "MXCloud-Launcher-1.2.0-portable.zip", hash);
            check(updater.latest("1.0.0") == null, "Require setup, not portable ZIP");
            fixture = release("v1.2.0", "MXCloud-Setup-1.2.0.exe", hash).replace("https://github.com/", "https://example.org/");
            check(updater.latest("1.0.0") == null, "Require this repository's asset");
            status = 404; check(updater.latest("1.0.0") == null, "No release");
            status = 403; expectFailure(() -> updater.latest("1.0.0"), "API failure"); status = 200;
            fixture = "{}"; check(updater.latest("1.0.0") == null, "No release metadata");
            Path installed = updater.download(latest, () -> false, bytes -> {});
            check(Arrays.equals(Files.readAllBytes(installed), INSTALLER), "Download through GitHub redirect");
            Files.delete(installed); Files.delete(installed.getParent());
            Set<Path> before = tempDirectories();
            var badHash = new GitHubUpdater.Release(latest.version(), latest.installer(), latest.size(), "0".repeat(64));
            expectFailure(() -> updater.download(badHash, () -> false, bytes -> {}), "Checksum mismatch");
            payload = "MZ-truncated".getBytes(StandardCharsets.UTF_8);
            expectFailure(() -> updater.download(latest, () -> false, bytes -> {}), "Truncated download");
            payload = new byte[INSTALLER.length];
            expectFailure(() -> updater.download(new GitHubUpdater.Release(latest.version(), latest.installer(), latest.size(), ""), () -> false, bytes -> {}), "HTML or invalid executable");
            payload = INSTALLER;
            AtomicBoolean cancelled = new AtomicBoolean();
            expectFailure(() -> updater.download(latest, cancelled::get, bytes -> cancelled.set(true)), "Cancel during download");
            int count = downloads.get();
            expectFailure(() -> updater.download(latest, () -> true, bytes -> {}), "Cancel before download");
            check(count == downloads.get(), "Cancel does not access installation package");
            redirect = "http://release-assets.githubusercontent.com/insecure";
            expectFailure(() -> updater.download(latest, () -> false, bytes -> {}), "HTTPS downgrade blocked");
            redirect = "https://example.org/installer";
            expectFailure(() -> updater.download(latest, () -> false, bytes -> {}), "Third-party redirect blocked");
            check(before.equals(tempDirectories()), "Failed/cancelled downloads leave no temporary installers");
            System.out.println("GITHUB_UPDATER_OK");
        } finally { server.stop(0); }
    }

    private static Set<Path> tempDirectories() throws IOException {
        try (var entries = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return new HashSet<>(entries.filter(path -> path.getFileName().toString().startsWith("MXCloud-update-")).toList());
        }
    }
    private static String release(String tag, String filename, String digest) {
        return "{\"tag_name\":\"" + tag + "\",\"draft\":false,\"prerelease\":false,\"assets\":[{\"name\":\"" + filename
                + "\",\"state\":\"uploaded\",\"size\":" + INSTALLER.length + ",\"digest\":\"sha256:" + digest
                + "\",\"browser_download_url\":\"https://github.com/" + GitHubUpdater.REPOSITORY + "/releases/download/" + tag + "/" + filename + "\"}]}";
    }
    @FunctionalInterface private interface Attempt { void run() throws Exception; }
    private static void expectFailure(Attempt attempt, String message) throws Exception {
        try { attempt.run(); throw new AssertionError(message); } catch (IOException expected) {}
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}

package dev.mx.launcher;

import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.function.*;

/** Public GitHub Releases only; no credentials or separate update service. */
final class GitHubUpdater {
    static final String REPOSITORY = "DPisgod/Open-MXaddon-Launcher";
    static final URI LATEST = URI.create("https://api.github.com/repos/" + REPOSITORY + "/releases/latest");
    record Release(String version, URI installer, long size, String sha256) {}
    @FunctionalInterface interface Connections { HttpURLConnection open(URI uri) throws IOException; }
    private final Connections connections;

    GitHubUpdater() { this(uri -> (HttpURLConnection) uri.toURL().openConnection()); }
    GitHubUpdater(Connections connections) { this.connections = connections; }

    Release latest(String current) throws IOException {
        HttpURLConnection connection = open(LATEST, true);
        try {
            int code = connection.getResponseCode();
            if (code == 404) return null;
            if (code != 200) throw new IOException("GitHub 检查更新失败（HTTP " + code + "）");
            try (InputStream input = connection.getInputStream()) {
                byte[] data = input.readNBytes(1024 * 1024 + 1);
                if (data.length > 1024 * 1024) throw new IOException("GitHub 版本信息过大");
                return parseRelease(new String(data, StandardCharsets.UTF_8), current);
            }
        } finally { connection.disconnect(); }
    }

    static Release parseRelease(String json, String current) throws IOException {
        try {
            Map<String,Object> release = Json.parseObject(json);
            if (Boolean.TRUE.equals(release.get("draft")) || Boolean.TRUE.equals(release.get("prerelease"))) return null;
            String tag = Json.str(release, "tag_name");
            String version = normalizeVersion(tag);
            if (version == null || compareVersions(version, current) <= 0) return null;
            String filename = "MXCloud-Setup-" + version + ".exe";
            String expectedUrl = "https://github.com/" + REPOSITORY + "/releases/download/" + tag + "/" + filename;
            List<Object> assets = Json.arr(release, "assets");
            if (assets == null) return null;
            for (Object value : assets) {
                if (!(value instanceof Map<?,?> asset) || !filename.equals(asset.get("name"))) continue;
                if (!"uploaded".equals(asset.get("state")) || !expectedUrl.equals(asset.get("browser_download_url"))) continue;
                if (!(asset.get("size") instanceof Number size) || size.longValue() <= 0) continue;
                String digest = Objects.toString(asset.get("digest"), "");
                if (!digest.isEmpty() && !digest.matches("sha256:[0-9a-fA-F]{64}")) throw new IOException("安装包校验信息无效");
                return new Release(version, URI.create(expectedUrl), size.longValue(), digest.isEmpty() ? "" : digest.substring(7));
            }
            return null; // A release without an uploaded installer is not installable yet.
        } catch (IllegalArgumentException failure) { throw new IOException("GitHub 版本信息无效", failure); }
    }

    static String normalizeVersion(String value) {
        if (value == null) return null;
        String version = value.replaceFirst("^[vV]", "");
        return version.matches("\\d+\\.\\d+\\.\\d+") ? version : null;
    }

    static int compareVersions(String first, String second) {
        String a = normalizeVersion(first), b = normalizeVersion(second);
        if (a == null || b == null) throw new IllegalArgumentException("版本号必须为三段数字");
        String[] left = a.split("\\."), right = b.split("\\.");
        for (int index = 0; index < 3; index++) {
            int difference = new BigInteger(left[index]).compareTo(new BigInteger(right[index]));
            if (difference != 0) return difference;
        }
        return 0;
    }

    Path download(Release release, BooleanSupplier cancelled, LongConsumer progress) throws IOException {
        checkCancelled(cancelled);
        Path directory = Files.createTempDirectory("MXCloud-update-");
        Path partial = directory.resolve("installer.part");
        Path installer = directory.resolve("MXCloud-Setup-" + release.version() + ".exe");
        boolean complete = false;
        try {
            HttpURLConnection connection = open(release.installer(), false);
            try {
                if (connection.getResponseCode() != 200) throw new IOException("GitHub 安装包下载失败（HTTP " + connection.getResponseCode() + "）");
                long length = connection.getContentLengthLong();
                if (length >= 0 && length != release.size()) throw new IOException("安装包大小与 GitHub 发布信息不一致");
                MessageDigest digest;
                try { digest = MessageDigest.getInstance("SHA-256"); }
                catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
                long downloaded = 0;
                byte[] header = new byte[2];
                try (InputStream input = connection.getInputStream(); OutputStream output = Files.newOutputStream(partial)) {
                    byte[] buffer = new byte[64 * 1024];
                    int count;
                    while (true) {
                        checkCancelled(cancelled);
                        count = input.read(buffer);
                        if (count == -1) break;
                        for (int i = 0; i < count && downloaded + i < 2; i++) header[(int) downloaded + i] = buffer[i];
                        downloaded += count;
                        if (downloaded > release.size()) throw new IOException("安装包超过预期大小");
                        output.write(buffer, 0, count); digest.update(buffer, 0, count); progress.accept(downloaded);
                    }
                }
                checkCancelled(cancelled);
                if (downloaded != release.size()) throw new IOException("安装包下载不完整，请重试");
                if (header[0] != 'M' || header[1] != 'Z') throw new IOException("下载内容不是 Windows 安装包");
                if (!release.sha256().isEmpty() && !HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(release.sha256()))
                    throw new IOException("安装包 SHA-256 校验失败，请重试");
                Files.move(partial, installer);
                complete = true;
                return installer;
            } finally { connection.disconnect(); }
        } finally {
            if (!complete) { Files.deleteIfExists(partial); Files.deleteIfExists(installer); Files.deleteIfExists(directory); }
        }
    }

    private HttpURLConnection open(URI uri, boolean metadata) throws IOException {
        for (int redirects = 0; redirects < 6; redirects++) {
            String host = Objects.toString(uri.getHost(), "").toLowerCase(Locale.ROOT);
            boolean allowed = host.equals("api.github.com") || host.equals("github.com") || host.endsWith(".githubusercontent.com");
            if (!"https".equals(uri.getScheme()) || !allowed || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443))
                throw new IOException("更新地址不是受支持的 GitHub HTTPS 地址");
            HttpURLConnection connection = connections.open(uri);
            try {
                connection.setConnectTimeout(metadata ? 7000 : 15000);
                connection.setReadTimeout(metadata ? 7000 : 20000);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("User-Agent", "MXCloud-Launcher/" + ApplicationVersion.CURRENT);
                connection.setRequestProperty("Accept", metadata ? "application/vnd.github+json" : "application/octet-stream");
                if (metadata) connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
                int code = connection.getResponseCode();
                if (code != 301 && code != 302 && code != 303 && code != 307 && code != 308) return connection;
                String location = connection.getHeaderField("Location");
                if (location == null) throw new IOException("GitHub 下载重定向缺少地址");
                uri = uri.resolve(location);
            } catch (IOException | RuntimeException failure) { connection.disconnect(); throw failure; }
            connection.disconnect();
        }
        throw new IOException("GitHub 下载重定向次数过多");
    }

    private static void checkCancelled(BooleanSupplier cancelled) throws InterruptedIOException {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("更新已取消");
    }

    static void launchInstaller(Path installer, Path home) throws IOException {
        if (!System.getProperty("os.name", "").startsWith("Windows")) throw new IOException("自动安装仅支持 Windows");
        // NSIS requires /D last and unquoted, including directories with spaces. No command shell is involved.
        var result = com.sun.jna.platform.win32.Shell32.INSTANCE.ShellExecute(null, "open", installer.toAbsolutePath().toString(),
                "/D=" + home.toAbsolutePath().normalize(), installer.getParent().toString(), 1);
        if (result == null || result.longValue() <= 32) throw new IOException("无法启动安装包，请重试");
    }
}

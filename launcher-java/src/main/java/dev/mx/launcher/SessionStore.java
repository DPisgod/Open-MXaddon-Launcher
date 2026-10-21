package dev.mx.launcher;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Properties;

/**
 * 会话持久化（{@code <home>\.minecraft\session.properties}）。
 *
 * <p>正版令牌存本地磁盘，因此文件权限尽量收紧（Windows 上为当前用户目录，
 * 已是用户私有；POSIX 上设为 600）。
 */
public final class SessionStore {

    private static final String FILE = "session.properties";

    private SessionStore() {
    }

    public static Session load(Path home) {
        Path f = LauncherPaths.dataFile(home,FILE);
        if (!Files.isRegularFile(f)) {
            return null;
        }
        Properties p = new Properties();
        try (var in = Files.newInputStream(f)) {
            p.load(in);
        } catch (IOException e) {
            return null;
        }
        String name = p.getProperty("name", "");
        boolean premium = Boolean.parseBoolean(p.getProperty("premium", "false"));
        String uuid = p.getProperty("uuid", "");
        String token = p.getProperty("accessToken", "");
        String xuid = p.getProperty("xuid", "");
        if(name.isBlank())return null;
        if(!premium&&name.equals("Player")&&!Boolean.parseBoolean(p.getProperty("explicitAccount","false"))){clear(home);return null;}

        if (premium && !uuid.isBlank() && !token.isBlank()) {
            return Session.premium(name, uuid, token, xuid.isBlank() ? null : xuid);
        }
        return Session.offline(name);
    }

    public static void save(Path home, Session s) {
        Properties p = new Properties();
        p.setProperty("explicitAccount","true");
        p.setProperty("name", s.name == null ? "Player" : s.name);
        p.setProperty("premium", String.valueOf(s.premium));
        p.setProperty("uuid", s.uuid == null ? "" : s.uuid);
        p.setProperty("accessToken", s.accessToken == null ? "" : s.accessToken);
        p.setProperty("xuid", s.xuid == null ? "" : s.xuid);

        Path f = LauncherPaths.dataFile(home,FILE);
        try (OutputStream out = Files.newOutputStream(f)) {
            p.store(out, "MXCloud session");
        } catch (IOException ignored) {
            return;
        }
        try {
            Files.setPosixFilePermissions(f,
                    java.util.Set.of(PosixFilePermission.OWNER_READ,
                                     PosixFilePermission.OWNER_WRITE));
        } catch (Exception ignored) {
            // Windows 不支持 POSIX 权限，忽略
        }
    }

    /** 退出正版登录（清掉令牌）。 */
    public static void clear(Path home) {
        try {
            Files.deleteIfExists(LauncherPaths.dataFile(home,FILE));
        } catch (IOException ignored) {
        }
    }
}

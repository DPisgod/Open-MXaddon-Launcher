package dev.mx.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 启动器设置（保存在 {@code <home>\.minecraft\launcher.properties}）。
 *
 * <p>当前可调项：
 * <ul>
 *   <li>{@code memoryMb} —— 分配给游戏的内存（-Xmx，单位 MB）</li>
 *   <li>{@code jvmArgs} —— 追加的 JVM 参数（原样拼接）</li>
 *   <li>{@code javaPath} —— 指定 java.exe；空表示自动查找</li>
 *   <li>{@code offlineName} —— 离线玩家名</li>
 *   <li>{@code lastVersion} —— 上次选择的版本</li>
 * </ul>
 */
public final class Settings {

    public int memoryMb = 4096;
    public String memoryMode = "auto";
    public String jvmArgs = "";
    public String javaPath = "";
    public String offlineName = "";
    public String lastVersion = "";

    private static final String FILE = "launcher.properties";

    public static Settings load(Path home) {
        Settings s = new Settings();
        Path f = LauncherPaths.dataFile(home,FILE);
        if (!Files.isRegularFile(f)) {
            return s;
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(f)) {
            p.load(in);
        } catch (IOException ignored) {
            return s;
        }
        s.memoryMb = parseInt(p.getProperty("memoryMb"), s.memoryMb);
        s.memoryMode = p.getProperty("memoryMode",p.containsKey("memoryMb")?"custom":"auto");
        if(!"custom".equals(s.memoryMode))s.memoryMode="auto";
        s.jvmArgs = p.getProperty("jvmArgs", s.jvmArgs);
        s.javaPath = p.getProperty("javaPath", s.javaPath);
        s.offlineName = p.getProperty("offlineName", s.offlineName);
        s.lastVersion = p.getProperty("lastVersion", s.lastVersion);
        return s;
    }

    public void save(Path home) {
        Properties p = new Properties();
        p.setProperty("memoryMb", String.valueOf(memoryMb));
        p.setProperty("memoryMode", memoryMode);
        p.setProperty("jvmArgs", jvmArgs == null ? "" : jvmArgs);
        p.setProperty("javaPath", javaPath == null ? "" : javaPath);
        p.setProperty("offlineName", offlineName == null ? "" : offlineName);
        p.setProperty("lastVersion", lastVersion == null ? "" : lastVersion);
        try (OutputStream out = Files.newOutputStream(LauncherPaths.dataFile(home,FILE))) {
            p.store(out, "OML launcher settings");
        } catch (IOException ignored) {
        }
    }

    /** 把设置拼成 JVM 参数串（-Xmx 等）。供后续扩展。 */
    public String toJvmArgLine() {
        StringBuilder sb = new StringBuilder();
        sb.append("-Xmx").append(Math.max(512, memoryMb)).append("M");
        sb.append(" -Xms").append(Math.min(Math.max(512, memoryMb), 2048)).append("M");
        if (jvmArgs != null && !jvmArgs.isBlank()) {
            sb.append(' ').append(jvmArgs.trim());
        }
        return sb.toString();
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }
}

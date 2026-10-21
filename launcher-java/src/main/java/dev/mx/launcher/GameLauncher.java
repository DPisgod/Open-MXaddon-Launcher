package dev.mx.launcher;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 直接用 java 命令启动 Minecraft（原版 / Fabric）。
 *
 * <p>职责只有一件事：按版本 JSON 拼出完整的 JVM 命令行并拉起游戏进程。
 * 不下载任何内容、不解密、不注入：启动器只做「读取本地版本 → 启动游戏」。
 */
final class GameLauncher {

    private GameLauncher() {}

    /**
     * 启动游戏。
     *
     * @param gameDir  游戏目录（独立实例时为 {@code .../<版本>/instance}）
     * @param version  版本名（目录名，与 {@code versions/<版本>/<版本>.json} 对应）
     * @param account  已选择的游戏账号（正版或离线）
     * @param memoryMb 堆上限（MB）
     * @return 游戏进程 PID
     */
    static int launch(Path gameDir, String version, Session account, int memoryMb) throws Exception {
        Path root = gameDir.toAbsolutePath().normalize();
        if (version == null || version.isBlank() || version.contains("/") || version.contains("\\")) {
            throw new IOException("请选择有效的游戏版本");
        }
        Path versionDir = root.resolve("versions").resolve(version).normalize();
        if (!versionDir.getParent().equals(root.resolve("versions").normalize())) {
            throw new IOException("无效的版本目录");
        }
        Path versionJson = versionDir.resolve(version + ".json");
        Path gameJar = versionDir.resolve(version + ".jar");
        if (!Files.isRegularFile(versionJson)) throw new IOException("版本缺少 " + version + ".json，请重新安装该版本");
        if (!Files.isRegularFile(gameJar)) throw new IOException("版本缺少 " + version + ".jar，请重新安装该版本");

        Map<String, Object> meta = Json.parseObject(Files.readString(versionJson, StandardCharsets.UTF_8));
        String mainClass = VersionInstaller.str(meta, "mainClass");
        if (mainClass.isBlank()) throw new IOException("版本缺少启动主类");

        Path libraries = root.resolve("libraries");
        List<Path> classpath = new ArrayList<>();
        classpath.add(gameJar);
        boolean glfw = false;
        for (Object entry : VersionInstaller.list(meta.get("libraries"))) {
            Map<String, Object> library = VersionInstaller.map(entry);
            if (!VersionInstaller.windowsLibrary(library)) continue;
            Map<String, Object> artifact = VersionInstaller.map(VersionInstaller.map(library.get("downloads")).get("artifact"));
            String path = VersionInstaller.str(artifact, "path");
            if (path.isBlank()) continue;
            Path file = VersionInstaller.safe(libraries, path);
            if (!Files.isRegularFile(file)) throw new IOException("缺少游戏依赖：" + path + "，请重新安装该版本");
            classpath.add(file);
            String coordinate = VersionInstaller.str(library, "name");
            if (coordinate.contains("natives-windows") || coordinate.contains("lwjgl") || coordinate.contains("glfw")) glfw = true;
        }
        if (!glfw) throw new IOException("版本依赖不完整（缺少 Windows 原生库），请重新安装该版本");

        Path natives = ensureNatives(root, versionDir, version, classpath);

        Path assets = root.resolve("assets");
        String assetsIndex = VersionInstaller.str(VersionInstaller.map(meta.get("assetIndex")), "id");
        if (assetsIndex.isBlank()) assetsIndex = "legacy";

        List<String> command = new ArrayList<>();
        command.add(javaBinary());
        command.add("-Xms1024M");
        command.add("-Xmx" + Math.max(1024, memoryMb) + "M");
        command.add("-Dfile.encoding=UTF-8");
        command.add("-Dstdout.encoding=UTF-8");
        command.add("-Dstderr.encoding=UTF-8");
        command.add("-Djdk.lang.Process.allowAmbiguousCommands=true");
        command.add("-Dlog4j2.formatMsgNoLookups=true");
        command.add("-Djava.library.path=" + natives);
        command.add("-Djna.tmpdir=" + natives);
        command.add("-Dorg.lwjgl.system.SharedLibraryExtractPath=" + natives);
        command.add("-Dio.netty.native.workdir=" + natives);
        // G1：低延迟，接近原启动链的参数
        command.add("-XX:+UnlockExperimentalVMOptions");
        command.add("-XX:+UseG1GC");
        command.add("-XX:G1NewSizePercent=20");
        command.add("-XX:G1ReservePercent=20");
        command.add("-XX:G1HeapRegionSize=32M");
        command.add("-XX:MaxGCPauseMillis=50");
        command.add("-XX:+PerfDisableSharedMem");
        command.add("-cp");
        command.add(String.join(java.io.File.pathSeparator, classpath.stream().map(Path::toString).toList()));
        command.add(mainClass);
        command.add("--username");
        command.add(account.name);
        command.add("--version");
        command.add(version);
        command.add("--gameDir");
        command.add(root.toString());
        command.add("--assetsDir");
        command.add(assets.toString());
        command.add("--assetIndex");
        command.add(assetsIndex);
        command.add("--width");
        command.add("854");
        command.add("--height");
        command.add("480");
        command.add("--versionType");
        command.add("release");
        if (account.premium && account.accessToken != null && !account.accessToken.isBlank()) {
            command.add("--uuid");
            command.add(account.uuid == null || account.uuid.isBlank() ? offlineUuid(account.name) : account.uuid.replace("-", ""));
            command.add("--accessToken");
            command.add(account.accessToken);
            command.add("--userType");
            command.add("msa");
        } else {
            command.add("--uuid");
            command.add(offlineUuid(account.name));
            command.add("--accessToken");
            command.add("0");
            command.add("--userType");
            command.add("legacy");
        }

        Path logs = root.resolve("logs");
        Files.createDirectories(logs);
        Path log = logs.resolve("launcher-game.log");
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(root.toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
        Process process = builder.start();
        try (OutputStream stdin = process.getOutputStream()) {
            stdin.close();
        } catch (IOException ignored) {
        }
        return (int) process.pid();
    }

    /**
     * 优先使用启动器自带的精简 JRE；其次用「运行启动器自身的那个 JVM」
     * （保证游戏与启动器 Java 版本一致）；最后才回退 JAVA_HOME / 系统 javaw。
     */
    static String javaBinary() {
        Path bundled = LauncherPaths.home().resolve("runtime").resolve("bin").resolve("javaw.exe");
        if (Files.isRegularFile(bundled)) return bundled.toAbsolutePath().toString();
        String javaHome = System.getProperty("java.home");
        if (javaHome != null && !javaHome.isBlank()) {
            Path candidate = Path.of(javaHome).resolve("bin").resolve("javaw.exe");
            if (Files.isRegularFile(candidate)) return candidate.toAbsolutePath().toString();
        }
        String home = System.getenv("JAVA_HOME");
        if (home != null && !home.isBlank()) {
            Path candidate = Path.of(home).resolve("bin").resolve("javaw.exe");
            if (Files.isRegularFile(candidate)) return candidate.toAbsolutePath().toString();
        }
        return "javaw";
    }

    /**
     * 原生库目录：已是现成目录就直接用，否则从带 {@code natives-windows} 的依赖包里解出 DLL。
     */
    private static Path ensureNatives(Path root, Path versionDir, String version, List<Path> classpath) throws IOException {
        Path installed = versionDir.resolve(version + "-natives");
        if (Files.isRegularFile(installed.resolve("glfw.dll"))) return installed;
        Path shared = root.resolve("natives");
        if (Files.isRegularFile(shared.resolve("glfw.dll"))) return shared;
        Files.createDirectories(installed);
        for (Path jar : classpath) {
            if (!jar.getFileName().toString().contains("natives-windows")) continue;
            try (ZipFile archive = new ZipFile(jar.toFile())) {
                for (ZipEntry entry : Collections.list(archive.entries())) {
                    String name = entry.getName();
                    if (entry.isDirectory() || !name.toLowerCase(Locale.ROOT).endsWith(".dll")) continue;
                    Path target = installed.resolve(Path.of(name).getFileName().toString());
                    try (var input = archive.getInputStream(entry)) {
                        Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
        if (!Files.isRegularFile(installed.resolve("glfw.dll"))) {
            throw new IOException("缺少 Windows 原生库，请重新安装该版本");
        }
        return installed;
    }

    /** 离线账号 UUID：与官方离线模式一致（MD5("OfflinePlayer:<名字>") 加版本位）。 */
    static String offlineUuid(String name) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5")
                    .digest(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
            digest[6] = (byte) ((digest[6] & 0x0f) | 0x30);
            digest[8] = (byte) ((digest[8] & 0x3f) | 0x80);
            StringBuilder text = new StringBuilder(32);
            for (byte value : digest) text.append(String.format("%02x", value));
            return text.toString();
        } catch (Exception impossible) {
            return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8))
                    .toString().replace("-", "");
        }
    }
}

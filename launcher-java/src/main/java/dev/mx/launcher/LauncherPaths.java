package dev.mx.launcher;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.List;

/**
 * 启动器目录解析。
 *
 * <p>「home」= 启动器的运行目录。账号与设置保存在其 .min 子目录，
 * 皮肤和壁纸仍在原位置，服务端覆盖文件仍从运行目录读取。
 * 旧版运行目录中的五个状态文件在首次读取前自动迁移：
 * <ul>
 *   <li>{@code .min/session.properties} —— 当前游戏账号</li>
 *   <li>{@code .min/accounts.dat} —— 加密游戏账号列表</li>
 *   <li>{@code .min/device-session.dat} —— 加密官网授权缓存</li>
 *   <li>{@code .min/appearance.properties} —— 外观设置</li>
 *   <li>{@code .min/launcher.properties} —— 启动器设置</li>
 *   <li>{@code server.properties} —— 服务端地址覆盖（可选）</li>
 *   <li>{@code skins/} —— 皮肤缓存</li>
 *   <li>{@code .min/} —— 游戏目录</li>
 * </ul>
 *
 * <p>解析顺序：
 * <ol>
 *   <li>系统属性 {@code -Dmxcloud.home=...}（测试/调试用）</li>
 *   <li>jar 所在目录（正常运行时）</li>
 *   <li>当前工作目录（兜底）</li>
 * </ol>
 */
public final class LauncherPaths {

    private static volatile Path cached = null;
    static final List<String> STATE_FILES=List.of("accounts.dat","appearance.properties","device-session.dat","launcher.properties","session.properties");

    private LauncherPaths() {
    }

    public static Path home() {
        Path h = cached;
        if (h != null) {
            return h;
        }
        synchronized (LauncherPaths.class) {
            if (cached == null) {
                cached = resolve();
            }
            return cached;
        }
    }

    private static Path resolve() {
        String override = System.getProperty("mxcloud.home");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        try {
            File f = new File(LauncherPaths.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            File parent = f.isFile() ? f.getParentFile() : f;
            if (parent != null) {
                return parent.toPath();
            }
        } catch (Exception ignored) {
        }
        return Path.of(System.getProperty("user.dir", "."));
    }

    /** 游戏根目录（{@code <home>\.min}）。 */
    public static Path gameRoot() {
        return home().resolve(".min");
    }

    /** Moves bytes without decrypting credentials. Existing destination files are never overwritten. */
    public static synchronized Path dataDirectory(Path home) {
        Path base=home.toAbsolutePath().normalize(),data=base.resolve(".min");
        try{
            Files.createDirectories(data);Path conflicts=null;
            for(String name:STATE_FILES){
                Path old=base.resolve(name),target=data.resolve(name);
                if(!Files.exists(old,LinkOption.NOFOLLOW_LINKS))continue;
                if(!Files.isRegularFile(old,LinkOption.NOFOLLOW_LINKS))throw new IOException("旧配置不是普通文件："+name);
                if(Files.exists(target,LinkOption.NOFOLLOW_LINKS)){
                    if(!Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS))throw new IOException("配置目标不是普通文件："+name);
                    if(conflicts==null)conflicts=Files.createTempDirectory(data,".launcher-migration-");
                    target=conflicts.resolve(name);
                }
                if(!target.toAbsolutePath().normalize().startsWith(data))throw new IOException("配置迁移目标超出 .min");
                Files.move(old,target);
            }
            return data;
        }catch(IOException e){throw new UncheckedIOException("无法迁移或读取 .min 中的启动器配置："+e.getMessage(),e);}
    }
    public static Path dataFile(Path home,String name){
        if(!STATE_FILES.contains(name))throw new IllegalArgumentException("未知的启动器配置文件");
        return dataDirectory(home).resolve(name);
    }

    /** 供测试注入固定目录。 */
    static void override(Path p) {
        cached = p;
    }
}

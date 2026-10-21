package dev.mx.launcher;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.List;

/**
 * 启动器目录解析。
 *
 * <p>「home」= 启动器的运行目录。账号与设置保存在其 .minecraft 子目录，
 * 皮肤和壁纸仍在原位置，服务端覆盖文件仍从运行目录读取。
 * 旧版运行目录中的五个状态文件在首次读取前自动迁移：
 * <ul>
 *   <li>{@code .minecraft/session.properties} —— 当前游戏账号</li>
 *   <li>{@code .minecraft/accounts.dat} —— 加密游戏账号列表</li>
 *   <li>{@code .minecraft/device-session.dat} —— 加密官网授权缓存</li>
 *   <li>{@code .minecraft/appearance.properties} —— 外观设置</li>
 *   <li>{@code .minecraft/launcher.properties} —— 启动器设置</li>
 *   <li>{@code server.properties} —— 服务端地址覆盖（可选）</li>
 *   <li>{@code skins/} —— 皮肤缓存</li>
 *   <li>{@code .minecraft/} —— 游戏目录</li>
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

    /** 游戏根目录（{@code <home>\.minecraft}）。 */
    public static Path gameRoot() {
        return dataDirectory(home());
    }

    /** Moves bytes without decrypting credentials. Existing destination files are never overwritten. */
    public static synchronized Path dataDirectory(Path home) {
        Path base=home.toAbsolutePath().normalize(),data=base.resolve(".minecraft"),legacy=base.resolve(".min");
        try{
            if(Files.exists(legacy,LinkOption.NOFOLLOW_LINKS)){
                if(Files.isSymbolicLink(legacy)||!Files.isDirectory(legacy,LinkOption.NOFOLLOW_LINKS))throw new IOException("旧 .min 不是普通目录");
                if(!Files.exists(data,LinkOption.NOFOLLOW_LINKS))Files.move(legacy,data);
                else{
                    if(Files.isSymbolicLink(data)||!Files.isDirectory(data,LinkOption.NOFOLLOW_LINKS))throw new IOException(".minecraft 不是普通目录");
                    mergeLegacy(legacy,data,legacy,data,new Path[1]);
                    Files.delete(legacy);
                }
            }
            Files.createDirectories(data);Path conflicts=null;
            if(Files.isSymbolicLink(data))throw new IOException(".minecraft 不能是目录链接");
            for(String name:STATE_FILES){
                Path old=base.resolve(name),target=data.resolve(name);
                if(!Files.exists(old,LinkOption.NOFOLLOW_LINKS))continue;
                if(!Files.isRegularFile(old,LinkOption.NOFOLLOW_LINKS))throw new IOException("旧配置不是普通文件："+name);
                if(Files.exists(target,LinkOption.NOFOLLOW_LINKS)){
                    if(!Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS))throw new IOException("配置目标不是普通文件："+name);
                    if(conflicts==null)conflicts=Files.createTempDirectory(data,".launcher-migration-");
                    target=conflicts.resolve(name);
                }
                if(!target.toAbsolutePath().normalize().startsWith(data))throw new IOException("配置迁移目标超出 .minecraft");
                Files.move(old,target);
            }
            return data;
        }catch(IOException e){throw new UncheckedIOException("无法迁移或读取 .minecraft 中的启动器配置："+e.getMessage(),e);}
    }
    private static void mergeLegacy(Path source,Path target,Path legacy,Path data,Path[] recovery)throws IOException{
        try(var entries=Files.list(source)){
            for(Path entry:entries.toList()){
                if(Files.isSymbolicLink(entry))throw new IOException("旧数据包含目录链接，请先手动整理："+entry);
                Path destination=target.resolve(entry.getFileName());
                if(!Files.exists(destination,LinkOption.NOFOLLOW_LINKS))Files.move(entry,destination);
                else if(Files.isDirectory(entry,LinkOption.NOFOLLOW_LINKS)&&Files.isDirectory(destination,LinkOption.NOFOLLOW_LINKS)&&!Files.isSymbolicLink(destination)){
                    // Keep an existing version/instance intact; same-name instances go to recovery as a unit.
                    if(source.equals(legacy.resolve("versions")))backup(entry,legacy,data,recovery);
                    else{mergeLegacy(entry,destination,legacy,data,recovery);Files.delete(entry);}
                }else backup(entry,legacy,data,recovery);
            }
        }
    }
    private static void backup(Path entry,Path legacy,Path data,Path[] recovery)throws IOException{
        if(recovery[0]==null)recovery[0]=Files.createTempDirectory(data,".min-migration-backup-");
        Path target=recovery[0].resolve(legacy.relativize(entry));Files.createDirectories(target.getParent());Files.move(entry,target);
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

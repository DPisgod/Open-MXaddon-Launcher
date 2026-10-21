package dev.mx.launcher;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.*;

/** Game runtimes are independent of the Java 21 launcher runtime. */
final class GameJava {
    static void ensure(Path instance,int major,VersionInstaller installer)throws IOException{
        DownloadCatalog catalog=new DownloadCatalog();
        var releases=VersionInstaller.list(catalog.json("https://api.adoptium.net/v3/assets/latest/"+major+"/hotspot?architecture=x64&image_type=jre&os=windows&vendor=eclipse"));
        if(releases.isEmpty())throw new IOException("没有适用于 Java "+major+" 的 Windows x64 运行时，请在设置中指定 Java 路径");
        Map<String,Object> pack=VersionInstaller.map(VersionInstaller.map(VersionInstaller.map(releases.get(0)).get("binary")).get("package"));
        Path zip=instance.resolve(".java-runtime.zip");
        try{
            installer.obtain(new VersionInstaller.Download(Json.str(pack,"link"),zip,"SHA-256",Json.str(pack,"checksum"),Json.num(pack,"size",-1)));
            Path target=instance.resolve("runtime");Files.createDirectories(target);long total=0;int entries=0;
            try(ZipFile archive=new ZipFile(zip.toFile())){
                for(ZipEntry entry:Collections.list(archive.entries())){
                    installer.checkCancelled();if(++entries>30000)throw new IOException("Java 压缩包文件过多");
                    String name=entry.getName();int slash=name.indexOf('/');if(slash<0||entry.isDirectory())continue;
                    Path file=VersionInstaller.safe(target,name.substring(slash+1));Files.createDirectories(file.getParent());
                    try(InputStream input=archive.getInputStream(entry);OutputStream output=Files.newOutputStream(file)){
                        byte[] bytes=new byte[65536];int n;while((n=input.read(bytes))>=0){installer.checkCancelled();total+=n;if(total>1_000_000_000L)throw new IOException("Java 运行时过大");output.write(bytes,0,n);}
                    }
                }
            }
            if(!Files.isRegularFile(target.resolve("bin/javaw.exe")))throw new IOException("Java 运行时不完整");
        }finally{Files.deleteIfExists(zip);}
    }
    static String executable(Path instance,int major)throws IOException{
        String configured=Settings.load(LauncherPaths.home()).javaPath;
        if(configured!=null&&!configured.isBlank()){
            if(!Files.isRegularFile(Path.of(configured)))throw new IOException("设置中的 Java 路径不存在");return configured;
        }
        Path local=instance.resolve("runtime/bin/javaw.exe");if(Files.isRegularFile(local))return local.toAbsolutePath().toString();
        if(major>Runtime.version().feature())throw new IOException("此游戏需要 Java "+major+"，请启用安装时自动下载 Java，或在设置中指定路径");
        if(major<=8&&Runtime.version().feature()>8)throw new IOException("此历史版本需要 Java 8，请重新安装并启用自动下载 Java，或在设置中指定 Java 8");
        return GameLauncher.javaBinary();
    }
    static String console(String executable){return executable.endsWith("javaw.exe")?executable.substring(0,executable.length()-9)+"java.exe":executable;}
}

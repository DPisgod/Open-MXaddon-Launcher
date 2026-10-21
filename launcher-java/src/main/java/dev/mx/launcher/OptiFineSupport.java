package dev.mx.launcher;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;

/** Uses a user-selected official OptiFine installer inside a staged vanilla instance. */
final class OptiFineSupport {
    static void validate(Path file)throws IOException{
        if(!Files.isRegularFile(file)||Files.size(file)>128L*1024*1024)throw new IOException("请选择有效的 OptiFine 安装 JAR");
        try(JarFile jar=new JarFile(file.toFile())){if(jar.getEntry("optifine/Installer.class")==null)throw new IOException("文件不是 OptiFine 官方安装器");}
    }
    static void install(Path file,DownloadCatalog.Options options,Path instance,VersionInstaller installer)throws Exception{
        validate(file);if(options.loader()!=DownloadCatalog.Loader.VANILLA)throw new IOException("本地 OptiFine 独立安装不能同时选择其他加载器");
        Path target=instance.resolve("versions").resolve(options.id()),vanilla=instance.resolve("versions").resolve(options.minecraft());Files.createDirectories(vanilla);
        var base=Json.parseObject(Files.readString(target.resolve(options.id()+".json")));
        if(!target.equals(vanilla)){Files.copy(target.resolve(options.id()+".jar"),vanilla.resolve(options.minecraft()+".jar"));var metadata=new LinkedHashMap<>(base);metadata.put("id",options.minecraft());Files.writeString(vanilla.resolve(options.minecraft()+".json"),Json.write(metadata));}
        Files.writeString(instance.resolve("launcher_profiles.json"),"{\"profiles\":{}}");
        Path helper=Path.of(OptiFineSupport.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath();
        Path java=Path.of(System.getProperty("java.home"),"bin/java.exe");
        Process child=new ProcessBuilder(java.toString(),"-cp",helper+File.pathSeparator+file.toAbsolutePath(),"dev.mx.launcher.OptiFineBootstrap",instance.toString(),options.minecraft()).directory(instance.toFile()).redirectErrorStream(true).redirectOutput(instance.resolve("optifine-install.log").toFile()).start();
        try{long deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(5);while(!child.waitFor(200,TimeUnit.MILLISECONDS)){installer.checkCancelled();if(System.nanoTime()>deadline)throw new IOException("OptiFine 安装超时");}if(child.exitValue()!=0)throw new IOException("OptiFine 安装失败，请确认 JAR 支持 Minecraft "+options.minecraft());}
        finally{if(child.isAlive()){child.descendants().forEach(ProcessHandle::destroyForcibly);child.destroyForcibly();child.waitFor(10,TimeUnit.SECONDS);}}
        LoaderInstaller.mergeProfile(options,instance,target,base,installer);
        base.put("mxcloudLoader","optifine");Files.writeString(target.resolve(options.id()+".json"),Json.write(base));
    }
}

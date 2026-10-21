package dev.mx.launcher;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

final class LoaderInstaller {
    static void install(DownloadCatalog.Options options,Path instance,Path versionDir,Map<String,Object> base,VersionInstaller installer)throws Exception{
        Path executable=installer.downloadLoaderInstaller(options.loader(),options.minecraft(),options.loaderVersion(),instance);
        Path vanilla=instance.resolve("versions").resolve(options.minecraft());Files.createDirectories(vanilla);
        Files.copy(versionDir.resolve(options.id()+".jar"),vanilla.resolve(options.minecraft()+".jar"));
        Map<String,Object> original=new LinkedHashMap<>(base);original.put("id",options.minecraft());
        Files.writeString(vanilla.resolve(options.minecraft()+".json"),Json.write(original));
        Files.writeString(instance.resolve("launcher_profiles.json"),"{\"profiles\":{}}");
        int major=(int)Json.num(VersionInstaller.map(base.get("javaVersion")),"majorVersion",8);
        String java=GameJava.console(GameJava.executable(instance,major));
        Process process=new ProcessBuilder(java,"-jar",executable.toString(),"--installClient",instance.toString()).directory(instance.toFile()).redirectErrorStream(true).redirectOutput(instance.resolve("loader-install.log").toFile()).start();
        try{
            long deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(15);
            while(!process.waitFor(250,TimeUnit.MILLISECONDS)){installer.checkCancelled();if(System.nanoTime()>deadline)throw new IOException("加载器安装超时，请查看 loader-install.log");}
            if(process.exitValue()!=0){
                Path logs=instance.getParent().getParent().resolve("logs");Files.createDirectories(logs);Path saved=logs.resolve("loader-"+UUID.randomUUID()+".log");Files.copy(instance.resolve("loader-install.log"),saved);
                throw new IOException("官方加载器安装失败（"+process.exitValue()+"），日志："+saved);
            }
        }finally{if(process.isAlive()){for(ProcessHandle child:process.descendants().toList())child.destroyForcibly();process.destroyForcibly();process.waitFor(10,TimeUnit.SECONDS);}}
        mergeProfile(options,instance,versionDir,base,installer);Files.deleteIfExists(executable);
    }
    static void mergeProfile(DownloadCatalog.Options options,Path instance,Path versionDir,Map<String,Object> base,VersionInstaller installer)throws Exception{
        Map<String,Object> profile=null;Path profileFolder=null;
        try(var folders=Files.list(instance.resolve("versions"))){
            for(Path folder:folders.toList()){
                String id=folder.getFileName().toString();if(id.equals(options.minecraft()))continue;
                Path file=folder.resolve(id+".json");if(!Files.isRegularFile(file))continue;
                Map<String,Object> candidate=Json.parseObject(Files.readString(file));
                if(options.minecraft().equals(candidate.get("inheritsFrom"))){profile=candidate;profileFolder=folder;break;}
            }
        }
        if(profile==null)throw new IOException("加载器未生成客户端版本配置");
        VersionInstaller.mergeArguments(base,profile);
        LinkedHashMap<String,Object> libraries=new LinkedHashMap<>();
        for(Object raw:VersionInstaller.list(base.get("libraries")))libraries.put(key(raw),raw);
        for(Object raw:VersionInstaller.list(profile.get("libraries")))libraries.put(key(raw),raw);
        base.put("libraries",new ArrayList<>(libraries.values()));base.put("mainClass",profile.get("mainClass"));
        for(String property:List.of("logging","javaVersion"))if(profile.containsKey(property))base.put(property,profile.get(property));
        base.put("mxcloudLoaderProfile",profile.get("id"));
        String jarId=VersionInstaller.str(profile,"id");Path patchedJar=profileFolder.resolve(jarId+".jar");
        if(Files.isRegularFile(patchedJar))Files.copy(patchedJar,versionDir.resolve(options.id()+".jar"),StandardCopyOption.REPLACE_EXISTING);
        installer.ensureLibraries(instance,base);
        Files.writeString(versionDir.resolve(options.id()+".json"),Json.write(base));
    }
    private static String key(Object value){String coordinate=VersionInstaller.str(VersionInstaller.map(value),"name");String[] parts=coordinate.split(":");return parts.length>=3?parts[0]+":"+parts[1]+(parts.length>3?":"+parts[3]:""):coordinate;}
}

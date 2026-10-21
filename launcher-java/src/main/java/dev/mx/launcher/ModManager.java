package dev.mx.launcher;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Changes only a selected instance's local files. Uninstalls move to a local recovery folder. */
final class ModManager {
    record Mod(Path file,String name,String version,boolean enabled){}
    private final Path mods;
    ModManager(Path gameDirectory)throws IOException{mods=gameDirectory.toAbsolutePath().normalize().resolve("mods");Files.createDirectories(mods);}
    Path directory(){return mods;}
    List<Mod> list()throws IOException{
        List<Mod> rows=new ArrayList<>();try(var paths=Files.list(mods)){
            for(Path file:paths.filter(Files::isRegularFile).toList()){
                String filename=file.getFileName().toString().toLowerCase(Locale.ROOT);if(!filename.endsWith(".jar")&&!filename.endsWith(".jar.disabled"))continue;
                String name=file.getFileName().toString(),version="";
                try(ZipFile zip=new ZipFile(file.toFile())){ZipEntry entry=zip.getEntry("fabric.mod.json");if(entry!=null&&entry.getSize()<1024*1024)try(InputStream input=zip.getInputStream(entry)){Map<String,Object> meta=Json.parseObject(new String(input.readNBytes(1024*1024),StandardCharsets.UTF_8));name=String.valueOf(meta.getOrDefault("name",name));version=String.valueOf(meta.getOrDefault("version",""));}}catch(Exception ignored){}
                rows.add(new Mod(file,name,version,filename.endsWith(".jar")));
            }
        }rows.sort(Comparator.comparing(Mod::name,String.CASE_INSENSITIVE_ORDER));return rows;
    }
    Path toggle(Path file)throws IOException{
        Path source=owned(file);String name=source.getFileName().toString();String lower=name.toLowerCase(Locale.ROOT);
        String target=lower.endsWith(".jar.disabled")?name.substring(0,name.length()-9):lower.endsWith(".jar")?name+".disabled":null;
        if(target==null)throw new IOException("只支持 .jar 或 .jar.disabled 文件");Path to=mods.resolve(target);
        Files.move(source,to);return to;
    }
    Path uninstall(Path file)throws IOException{
        Path source=owned(file),trash=mods.resolve(".mx-trash").resolve(UUID.randomUUID().toString());Files.createDirectories(trash);Path target=trash.resolve(source.getFileName());Files.move(source,target);return target;
    }
    Path add(Path file)throws IOException{
        if(!Files.isRegularFile(file)||!file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))throw new IOException("请拖入 Mod 的 JAR 文件");
        if(Files.size(file)>512L*1024*1024)throw new IOException("单个 Mod 请小于 512 MB");
        try(ZipFile zip=new ZipFile(file.toFile())){if(!zip.entries().hasMoreElements())throw new IOException("JAR 内容为空");}
        Path target=mods.resolve(file.getFileName().toString());if(Files.exists(target))throw new IOException("已存在同名 Mod："+target.getFileName());
        Path temp=Files.createTempFile(mods,".adding-",".tmp");try{Files.copy(file,temp,StandardCopyOption.REPLACE_EXISTING);Files.move(temp,target);}finally{Files.deleteIfExists(temp);}return target;
    }
    private Path owned(Path file)throws IOException{Path path=file.toAbsolutePath().normalize();if(!mods.equals(path.getParent())||Files.isSymbolicLink(path)||!Files.isRegularFile(path))throw new IOException("Mod 文件不属于当前版本");return path;}
}

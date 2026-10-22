package dev.mx.launcher;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Installs mrpack/CurseForge manifests into a new staged instance; incomplete packs never enter the version list. */
final class ModpackInstaller {
    private static final int MAX_CONTENT_ENTRIES=200000;
    private static final long MAX_CONTENT_BYTES=4_000_000_000L;
    record Pack(String title,DownloadCatalog.Options options,boolean modrinth,Map<String,Object> manifest){}
    static Pack forImport(Path archive,Path gameRoot)throws IOException{
        Pack pack=inspect(archive,"");String base=pack.title().isBlank()?archive.getFileName().toString().replaceFirst("(?i)\\.(zip|mrpack)$",""):pack.title();
        base=base.replaceAll("[^\\p{L}\\p{N}_.+ \\-]","_").strip().replaceAll("^[. ]+|[. ]+$","");if(base.isBlank())base="Modpack";if(base.length()>90)base=base.substring(0,90);
        if(base.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?"))base="Pack-"+base;
        String name=base;for(int suffix=2;Files.exists(gameRoot.resolve("versions").resolve(name));suffix++)name=base+"-"+suffix;
        var options=pack.options();return new Pack(pack.title(),new DownloadCatalog.Options(options.minecraft(),options.loader(),options.loaderVersion(),options.fabricApi(),options.autoJava(),name),pack.modrinth(),pack.manifest());
    }
    static Pack inspect(Path archive,String name)throws IOException{
        try(ZipFile zip=new ZipFile(archive.toFile())){
            boolean modrinth=zip.getEntry("modrinth.index.json")!=null;
            ZipEntry entry=zip.getEntry(modrinth?"modrinth.index.json":"manifest.json");if(entry==null)throw new IOException("不是 Modrinth .mrpack 或 CurseForge 整合包");
            Map<String,Object> manifest;try(InputStream input=zip.getInputStream(entry)){byte[] bytes=input.readNBytes(8*1024*1024+1);if(bytes.length>8*1024*1024)throw new IOException("整合包清单过大");manifest=Json.parseObject(new String(bytes,StandardCharsets.UTF_8));}
            String game;DownloadCatalog.Loader loader=DownloadCatalog.Loader.VANILLA;String loaderVersion="";
            if(modrinth){
                if(Json.num(manifest,"formatVersion",0)!=1||!"minecraft".equals(manifest.get("game")))throw new IOException("不支持的整合包格式");
                var dependencies=VersionInstaller.map(manifest.get("dependencies"));game=VersionInstaller.str(dependencies,"minecraft");
                int loaders=0;
                for(var pair:Map.of("fabric-loader",DownloadCatalog.Loader.FABRIC,"quilt-loader",DownloadCatalog.Loader.QUILT,"forge",DownloadCatalog.Loader.FORGE,"neoforge",DownloadCatalog.Loader.NEOFORGE).entrySet())if(dependencies.containsKey(pair.getKey())){loader=pair.getValue();loaderVersion=VersionInstaller.str(dependencies,pair.getKey());loaders++;}
                if(loaders>1)throw new IOException("整合包包含多个不兼容加载器");
            }else{
                if(!"minecraftModpack".equals(manifest.get("manifestType"))||Json.num(manifest,"manifestVersion",0)!=1)throw new IOException("不支持的 CurseForge 整合包格式");
                var minecraft=VersionInstaller.map(manifest.get("minecraft"));game=VersionInstaller.str(minecraft,"version");
                List<?> loaders=VersionInstaller.list(minecraft.get("modLoaders"));
                for(Object raw:loaders){var item=VersionInstaller.map(raw);if(loaders.size()>1&&!Boolean.TRUE.equals(item.get("primary")))continue;
                    String id=VersionInstaller.str(item,"id");int dash=id.indexOf('-');if(dash<0)throw new IOException("整合包加载器无效");
                    loader=switch(id.substring(0,dash)){case "fabric"->DownloadCatalog.Loader.FABRIC;case "quilt"->DownloadCatalog.Loader.QUILT;case "forge"->DownloadCatalog.Loader.FORGE;case "neoforge"->DownloadCatalog.Loader.NEOFORGE;default->throw new IOException("不支持的整合包加载器："+id);};loaderVersion=id.substring(dash+1);
                }
            }
            if(game.isBlank())throw new IOException("整合包缺少游戏版本");
            Pack pack=new Pack(VersionInstaller.str(manifest,"name"),new DownloadCatalog.Options(game,loader,loaderVersion,false,true,name),modrinth,manifest);
            overrides(zip,pack);return pack;
        }
    }
    static String install(Path archive,String name,Path gameRoot,VersionInstaller installer,ContentCatalog catalog)throws Exception{
        Pack pack=inspect(archive,name);
        return installer.install(pack.options(),(instance,downloads)->installContent(archive,pack,instance,downloads,catalog));
    }
    static void installContent(Path archive,Pack pack,Path instance,VersionInstaller installer,ContentCatalog catalog)throws Exception{
        int resourceNumber=0;int resourceCount=VersionInstaller.list(pack.manifest().get("files")).size();
        for(Object raw:VersionInstaller.list(pack.manifest().get("files"))){
            installer.report(new VersionInstaller.Progress("下载整合包资源 "+resourceNumber+" / "+resourceCount,resourceNumber++,resourceCount));
            installer.checkCancelled();var file=VersionInstaller.map(raw);
            if(pack.modrinth()){
                if("unsupported".equals(VersionInstaller.map(file.get("env")).get("client")))continue;
                String path=VersionInstaller.str(file,"path");Path target=contentPath(instance,path);
                var hashes=VersionInstaller.map(file.get("hashes"));String algorithm=hashes.containsKey("sha512")?"SHA-512":"SHA-1";
                String hash=VersionInstaller.str(hashes,algorithm.equals("SHA-512")?"sha512":"sha1");IOException failure=null;boolean downloaded=false;
                for(Object url:VersionInstaller.list(file.get("downloads"))){
                    try{installer.obtain(new VersionInstaller.Download(url.toString(),target,algorithm,hash,Json.num(file,"fileSize",-1)));downloaded=true;break;}
                    catch(IOException error){installer.checkCancelled();failure=error;}
                }
                if(!downloaded)throw new IOException("整合包文件下载失败："+path,failure);
            }else{
                if(Boolean.FALSE.equals(file.get("required")))continue;
                ContentCatalog.FileVersion version=catalog.file(ContentCatalog.Source.CURSEFORGE,VersionInstaller.str(file,"projectID"),VersionInstaller.str(file,"fileID"));
                catalog.download(version,instance.resolve("mods"),installer);
            }
        }
        try(ZipFile zip=new ZipFile(archive.toFile())){
            List<OverrideFile> content=overrides(zip,pack);long total=0;int count=0;
            installer.report(new VersionInstaller.Progress("正在写入整合包文件…",0,content.size()));
            for(OverrideFile file:content){
                    ZipEntry entry=file.entry();installer.checkCancelled();
                    Path target=contentPath(instance,file.path());
                    if(entry.isDirectory()){Files.createDirectories(target);count++;continue;}
                    Files.createDirectories(target.getParent());
                    try(InputStream input=zip.getInputStream(entry);OutputStream output=Files.newOutputStream(target)){
                        byte[] bytes=new byte[65536];int n;while((n=input.read(bytes))>=0){installer.checkCancelled();total+=n;if(total>MAX_CONTENT_BYTES)throw new IOException("整合包解压大小超出限制");output.write(bytes,0,n);}
                    }
                    count++;if(count==1||count%128==0||count==content.size())installer.report(new VersionInstaller.Progress("写入整合包文件 "+count+" / "+content.size(),count,content.size()));
            }
        }
    }
    private record OverrideFile(ZipEntry entry,String path){}
    private static List<OverrideFile> overrides(ZipFile zip,Pack pack)throws IOException{
        String common=pack.modrinth()?"overrides":Objects.toString(pack.manifest().get("overrides"),"overrides");
        if(common.contains("..")||common.startsWith("/")||common.contains(":"))throw new IOException("无效的整合包覆盖目录");
        List<OverrideFile> content=new ArrayList<>();long bytes=0;
        for(String prefix:pack.modrinth()?List.of(common+"/","client-overrides/"):List.of(common+"/")){
            for(ZipEntry entry:Collections.list(zip.entries())){
                if(!entry.getName().startsWith(prefix)||entry.getName().equals(prefix))continue;
                if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("整合包识别已取消");
                String relative=entry.getName().substring(prefix.length());contentPath(Path.of("pack-validation"),relative);
                if(content.size()>=MAX_CONTENT_ENTRIES)throw new IOException("整合包覆盖文件过多");
                long size=Math.max(0,entry.getSize());if(size>MAX_CONTENT_BYTES-bytes)throw new IOException("整合包解压大小超出限制");bytes+=size;content.add(new OverrideFile(entry,relative));
            }
        }return content;
    }
    static Path contentPath(Path instance,String path)throws IOException{
        Path target=VersionInstaller.safe(instance,path);String first=instance.toAbsolutePath().normalize().relativize(target).getName(0).toString().toLowerCase(Locale.ROOT);
        if(Set.of("versions","libraries","runtime","launcher_profiles.json","launcher.properties","accounts.dat","session.properties").contains(first)||first.startsWith("."))throw new IOException("整合包不能覆盖启动器或运行时："+path);
        return target;
    }
}

package dev.mx.launcher;

import java.nio.file.*;
import java.util.*;

/** Manually invoked against a locally obtained official JAR and a disposable copy of vanilla. */
public final class OptiFineLocalLiveTest {
    public static void main(String[] args)throws Exception{
        Path original=Path.of(args[0]),libraries=Path.of(args[1]),local=Path.of(args[2]);String game=original.getFileName().toString();
        Path home=Files.createTempDirectory("oml-optifine-local-"),instance=home.resolve("instance");LauncherPaths.override(home);
        var options=new DownloadCatalog.Options(game,DownloadCatalog.Loader.VANILLA,"",false,false,game+"-Local-OptiFine");
        Path target=instance.resolve("versions").resolve(options.id());Files.createDirectories(target);
        Files.copy(original.resolve(game+".jar"),target.resolve(options.id()+".jar"));
        var metadata=Json.parseObject(Files.readString(original.resolve(game+".json")));metadata.put("id",options.id());Files.writeString(target.resolve(options.id()+".json"),Json.write(metadata));
        for(Object raw:VersionInstaller.list(metadata.get("libraries"))){var lib=VersionInstaller.map(raw);String path=VersionInstaller.str(VersionInstaller.map(VersionInstaller.map(lib.get("downloads")).get("artifact")),"path");if(path.isBlank())continue;Path source=libraries.resolve(path);if(Files.isRegularFile(source)){Path copy=instance.resolve("libraries").resolve(path);Files.createDirectories(copy.getParent());Files.copy(source,copy);}}
        OptiFineSupport.install(local,options,instance,new VersionInstaller(home,p->{}));
        var result=Json.parseObject(Files.readString(target.resolve(options.id()+".json")));
        if(!"optifine".equals(result.get("mxcloudLoader"))||!Objects.toString(result.get("mainClass"),"").contains("launchwrapper"))throw new AssertionError("OptiFine profile not imported");
        if(!Json.write(result).contains("optifine.OptiFineTweaker"))throw new AssertionError("Missing OptiFine launch arguments");
        if(VersionInstaller.list(result.get("libraries")).stream().noneMatch(v->VersionInstaller.str(VersionInstaller.map(v),"name").startsWith("optifine:")))throw new AssertionError("Missing OptiFine library");
        System.out.println("OFFICIAL_LOCAL_OPTIFINE_PATCH_PROFILE_ARGUMENTS_LIBRARIES_OK "+home);
    }
}

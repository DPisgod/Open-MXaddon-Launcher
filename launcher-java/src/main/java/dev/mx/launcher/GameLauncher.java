package dev.mx.launcher;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

/** Publisher launch arguments, including historical positional arguments and loader JVM arguments. */
final class GameLauncher {
    private GameLauncher() {}
    static int launch(Path gameDir,String version,Session account,int memoryMb)throws Exception{
        Path root=gameDir.toAbsolutePath().normalize();List<String> command=command(root,version,account,memoryMb);
        Path logs=root.resolve("logs");Files.createDirectories(logs);
        Process process=new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(logs.resolve("launcher-game.log").toFile())).start();
        process.getOutputStream().close();return (int)process.pid();
    }
    static List<String> command(Path root,String version,Session account,int memoryMb)throws Exception{
        Path versions=root.resolve("versions").toAbsolutePath().normalize(),dir=VersionInstaller.safe(versions,version);
        if(!versions.equals(dir.getParent()))throw new IOException("无效的版本目录");
        Map<String,Object> meta=Json.parseObject(Files.readString(dir.resolve(version+".json")));
        Path gameJar=dir.resolve(version+".jar");if(!Files.isRegularFile(gameJar))throw new IOException("版本缺少客户端 JAR，请重新安装");
        String mainClass=Json.str(meta,"mainClass");if(mainClass==null||mainClass.isBlank())throw new IOException("版本缺少启动主类");
        Path libraries=root.resolve("libraries"),natives=dir.resolve(version+"-natives");Files.createDirectories(natives);
        List<Path> classpath=new ArrayList<>();
        for(Object raw:VersionInstaller.list(meta.get("libraries"))){
            Map<String,Object> library=VersionInstaller.map(raw);if(!VersionInstaller.windowsLibrary(library))continue;
            var downloads=VersionInstaller.map(library.get("downloads"));var artifact=VersionInstaller.map(downloads.get("artifact"));
            String path=VersionInstaller.str(artifact,"path");
            if(path.isBlank()&&!library.containsKey("natives"))path=VersionInstaller.mavenPath(VersionInstaller.str(library,"name"));
            if(!path.isBlank()){
                Path file=VersionInstaller.safe(libraries,path);if(!Files.isRegularFile(file))throw new IOException("缺少游戏依赖："+path);classpath.add(file);
                if(path.contains("natives-windows"))extract(file,natives);
            }
            String classifier=VersionInstaller.str(VersionInstaller.map(library.get("natives")),"windows").replace("${arch}","64");
            var nativeArtifact=VersionInstaller.map(VersionInstaller.map(downloads.get("classifiers")).get(classifier));
            if(!nativeArtifact.isEmpty())extract(VersionInstaller.safe(libraries,VersionInstaller.str(nativeArtifact,"path")),natives);
        }
        classpath.add(gameJar);
        int major=(int)Json.num(VersionInstaller.map(meta.get("javaVersion")),"majorVersion",8);
        // LWJGL 2 loads OpenAL through ANSI Win32 paths; keep its DLL path ASCII when the installation path is Chinese.
        if(major<=8&&!StandardCharsetsHolder.ascii(natives.toString()))natives=legacyNativeCache(natives);
        String assetsId=Objects.toString(VersionInstaller.map(meta.get("assetIndex")).get("id"),"legacy");
        Path assets=root.resolve("assets"),legacyAssets=assets.resolve("virtual").resolve(assetsId);
        String uuid=account.premium&&!Objects.toString(account.uuid,"").isBlank()?account.uuid.replace("-",""):offlineUuid(account.name);
        String token=account.premium?Objects.toString(account.accessToken,"0"):"0";
        Map<String,String> values=new HashMap<>();
        values.put("auth_player_name",account.name);values.put("auth_uuid",uuid);values.put("auth_access_token",token);
        values.put("auth_session",account.premium?"token:"+token+":"+uuid:"0");values.put("user_type",account.premium?"msa":"legacy");
        values.put("version_name",version);values.put("version_type",Objects.toString(meta.get("type"),"release"));
        values.put("game_directory",root.toString());values.put("assets_root",assets.toString());values.put("assets_index_name",assetsId);
        values.put("game_assets",Files.isDirectory(legacyAssets)?legacyAssets.toString():root.resolve("resources").toString());
        values.put("user_properties","{}");values.put("auth_xuid","");values.put("clientid","");
        values.put("natives_directory",natives.toString());values.put("library_directory",libraries.toString());
        values.put("classpath_separator",File.pathSeparator);values.put("classpath",String.join(File.pathSeparator,classpath.stream().map(Path::toString).toList()));
        values.put("launcher_name","OML");values.put("launcher_version",ApplicationVersion.CURRENT);
        values.put("resolution_width","854");values.put("resolution_height","480");
        List<String> command=new ArrayList<>();command.add(GameJava.executable(root,major));
        command.add("-Xms"+Math.min(1024,memoryMb)+"M");command.add("-Xmx"+Math.max(1024,memoryMb)+"M");
        command.add("-Dfile.encoding=UTF-8");command.add("-Dlog4j2.formatMsgNoLookups=true");
        command.add("-Dstdout.encoding=UTF-8");command.add("-Dstderr.encoding=UTF-8");
        command.add("-Djava.library.path="+natives);command.add("-Dorg.lwjgl.system.SharedLibraryExtractPath="+natives);
        command.add("-Dorg.lwjgl.librarypath="+natives);
        Map<String,Object> arguments=VersionInstaller.map(meta.get("arguments"));
        List<String> jvm=arguments(arguments.get("jvm"),values);command.addAll(jvm);
        if(!jvm.contains("-cp")&&!jvm.contains("-classpath")&&!jvm.contains("--class-path")){command.add("-cp");command.add(values.get("classpath"));}
        var logging=VersionInstaller.map(VersionInstaller.map(meta.get("logging")).get("client"));var loggingFile=VersionInstaller.map(logging.get("file"));
        if(!loggingFile.isEmpty()){
            Path file=VersionInstaller.safe(assets.resolve("log_configs"),VersionInstaller.str(loggingFile,"id"));
            if(Files.isRegularFile(file))command.add(VersionInstaller.str(logging,"argument").replace("${path}",file.toUri().toASCIIString()));
        }
        String extra=Settings.load(LauncherPaths.home()).jvmArgs;if(extra!=null&&!extra.isBlank())command.addAll(split(extra));
        command.add(mainClass);
        if(arguments.containsKey("game"))command.addAll(arguments(arguments.get("game"),values));
        else for(String argument:split(VersionInstaller.str(meta,"minecraftArguments")))command.add(expand(argument,values));
        return command;
    }
    static List<String> arguments(Object raw,Map<String,String> values)throws IOException{
        List<String> result=new ArrayList<>();
        for(Object item:VersionInstaller.list(raw)){
            if(item instanceof String text)result.add(expand(text,values));
            else{Map<String,Object> object=VersionInstaller.map(item);if(!VersionInstaller.windowsLibrary(object))continue;
                Object value=object.get("value");if(value instanceof String text)result.add(expand(text,values));else for(Object text:VersionInstaller.list(value))result.add(expand(text.toString(),values));}
        }return result;
    }
    static String expand(String text,Map<String,String> values)throws IOException{
        Matcher matcher=Pattern.compile("\\$\\{([^}]+)}").matcher(text);StringBuffer result=new StringBuffer();
        while(matcher.find()){String replacement=values.get(matcher.group(1));if(replacement==null)throw new IOException("不支持的启动参数："+matcher.group());matcher.appendReplacement(result,Matcher.quoteReplacement(replacement));}
        matcher.appendTail(result);return result.toString();
    }
    static List<String> split(String text){
        List<String> parts=new ArrayList<>();Matcher matcher=Pattern.compile("\"([^\"]*)\"|'([^']*)'|(\\S+)").matcher(text);
        while(matcher.find())parts.add(matcher.group(1)!=null?matcher.group(1):matcher.group(2)!=null?matcher.group(2):matcher.group(3));return parts;
    }
    private static void extract(Path jar,Path natives)throws IOException{
        try(ZipFile zip=new ZipFile(jar.toFile())){for(ZipEntry entry:Collections.list(zip.entries()))if(!entry.isDirectory()&&entry.getName().endsWith(".dll")){
            Path file=natives.resolve(Path.of(entry.getName()).getFileName().toString());if(Files.isRegularFile(file))continue;
            try(InputStream input=zip.getInputStream(entry)){Files.copy(input,file);}
        }}
    }
    static String javaBinary(){
        for(String home:List.of(LauncherPaths.home().resolve("runtime").toString(),System.getProperty("java.home",""),Objects.toString(System.getenv("JAVA_HOME"),""))){
            Path java=Path.of(home,"bin/javaw.exe");if(Files.isRegularFile(java))return java.toAbsolutePath().toString();
        }return "javaw";
    }
    static String offlineUuid(String name){return UUID.nameUUIDFromBytes(("OfflinePlayer:"+name).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString().replace("-","");}
    private static Path legacyNativeCache(Path source)throws Exception{
        java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");List<Path> files;
        try(var entries=Files.list(source)){files=entries.filter(Files::isRegularFile).sorted().toList();}
        for(Path file:files){digest.update(file.getFileName().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));digest.update(Files.readAllBytes(file));}
        Path cache=Path.of(System.getProperty("java.io.tmpdir"),"OML","Natives",HexFormat.of().formatHex(digest.digest()).substring(0,20));Files.createDirectories(cache);
        for(Path file:files){Path target=cache.resolve(file.getFileName());if(!Files.isRegularFile(target)||Files.mismatch(file,target)!=-1)Files.copy(file,target,StandardCopyOption.REPLACE_EXISTING);}
        return cache;
    }
    private static final class StandardCharsetsHolder {static boolean ascii(String text){return text.chars().allMatch(c->c<128);}}
}

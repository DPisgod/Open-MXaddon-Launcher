package dev.mx.launcher;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.*;

public final class DownloadsBehaviorTest {
    private static final Map<String,byte[]> responses=new ConcurrentHashMap<>();
    static byte[] text(String text){return text.getBytes(StandardCharsets.UTF_8);}
    static String hash(byte[] bytes,String algorithm)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(bytes));}
    static Map<String,Object> artifact(String url,byte[] bytes,String path)throws Exception{responses.put(url,bytes);return Map.of("url",url,"sha1",hash(bytes,"SHA-1"),"size",bytes.length,"path",path);}
    static VersionInstaller installer(Path root){return new VersionInstaller(root,p->{},url->{byte[] bytes=responses.get(url);if(bytes==null)throw new IOException("Unexpected fixture URL: "+url);return new HttpURLConnection(URI.create(url).toURL()){public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}public int getResponseCode(){return 200;}public InputStream getInputStream(){return new ByteArrayInputStream(bytes);}};});}
    static byte[] zip(String name,byte[] bytes)throws Exception{ByteArrayOutputStream output=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(output)){zip.putNextEntry(new ZipEntry(name));zip.write(bytes);zip.closeEntry();}return output.toByteArray();}
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        Path home=Files.createTempDirectory("mx-download-tests-");LauncherPaths.override(home);
        Settings settings=Settings.load(home);settings.javaPath=Path.of(System.getProperty("java.home"),"bin/javaw.exe").toString();settings.save(home);
        List<Object> releases=new ArrayList<>();
        for(String game:List.of("b1.7.3","test-snapshot")){
            byte[] client=zip("fixture",text(game)),nativeJar=zip("lwjgl64.dll",text("MZ-fixture")),asset=text("asset");
            var core=artifact("https://fixture.test/core",zip("library",text("core")),"fixture/core/1/core.jar");
            var nativeArtifact=artifact("https://fixture.test/natives",nativeJar,"fixture/native/1/native.jar");
            String assetHash=hash(asset,"SHA-1");responses.put("https://resources.download.minecraft.net/"+assetHash.substring(0,2)+"/"+assetHash,asset);
            byte[] index=text(Json.write(Map.of("objects",Map.of("sound/test.ogg",Map.of("hash",assetHash,"size",asset.length)),"virtual",true,"map_to_resources",true)));
            var indexArtifact=new LinkedHashMap<>(artifact("https://fixture.test/assets",index,""));indexArtifact.put("id","fixture");
            Map<String,Object> meta=new LinkedHashMap<>();meta.put("id",game);meta.put("type",game.startsWith("b")?"old_beta":"snapshot");meta.put("mainClass","net.minecraft.client.Main");meta.put("javaVersion",Map.of("majorVersion",8));meta.put("downloads",Map.of("client",artifact("https://fixture.test/client-"+game,client,"")));
            meta.put("assetIndex",indexArtifact);meta.put("libraries",List.of(Map.of("name","fixture:core:1","downloads",Map.of("artifact",core)),Map.of("name","fixture:platform:1","downloads",Map.of("classifiers",Map.of("natives-windows",nativeArtifact)),"natives",Map.of("windows","natives-windows"))));
            if(game.startsWith("b"))meta.put("minecraftArguments","${auth_player_name} ${auth_session} --gameDir ${game_directory}");
            else meta.put("arguments",Map.of("game",List.of("--username","${auth_player_name}","--gameDir","${game_directory}"),"jvm",List.of("-cp","${classpath}")));
            byte[] json=text(Json.write(meta));String url="https://fixture.test/"+game+".json";responses.put(url,json);releases.add(Map.of("id",game,"url",url,"sha1",hash(json,"SHA-1"),"type",meta.get("type")));
        }
        responses.put(DownloadCatalog.MANIFEST,text(Json.write(Map.of("versions",releases))));
        Path root=LauncherPaths.gameRoot();
        for(String game:List.of("b1.7.3","test-snapshot")){
            String id=installer(root).install(new DownloadCatalog.Options(game,DownloadCatalog.Loader.VANILLA,"",false,false,""));
            Path instance=VersionInstaller.gameDirectory(root,id);check(Files.isRegularFile(instance.resolve("assets/virtual/fixture/sound/test.ogg")),"Virtual legacy assets");
            check(Files.isRegularFile(instance.resolve("resources/sound/test.ogg")),"Legacy resource map");
            List<String> command=GameLauncher.command(instance,id,Session.offline("测试玩家"),2048);
            check(command.contains("测试玩家")&&command.contains(instance.toString()),"Account and isolated game directory");
            check(command.stream().noneMatch(value->value.contains("${")),"Unexpanded launch variables");
            check(Files.isRegularFile(instance.resolve("versions/"+id+"/"+id+"-natives/lwjgl64.dll")),"Legacy native classifiers");
        }
        try{installer(root).install(new DownloadCatalog.Options("b1.7.3",DownloadCatalog.Loader.VANILLA,"",false,false,""));throw new AssertionError("Duplicate overwrite");}catch(IOException expected){}
        try{installer(root).install(new DownloadCatalog.Options("b1.7.3",DownloadCatalog.Loader.VANILLA,"",false,false,"../outside"));throw new AssertionError("Path traversal");}catch(IOException expected){}
        Map<String,Object> base=new LinkedHashMap<>(Map.of("arguments",Map.of("game",List.of("--username","${auth_player_name}"),"jvm",List.of("-cp","${classpath}"))));
        VersionInstaller.mergeArguments(base,Map.of("arguments",Map.of("game",List.of("--launchTarget","forgeclient"),"jvm",List.of("--add-opens=java.base/java.util=ALL-UNNAMED"))));
        check(VersionInstaller.list(VersionInstaller.map(base.get("arguments")).get("game")).contains("forgeclient"),"Loader game arguments");
        var legacy=new LinkedHashMap<String,Object>(Map.of("minecraftArguments","one two"));VersionInstaller.mergeArguments(legacy,Map.of("minecraftArguments","three four"));check(!legacy.containsKey("arguments"),"Legacy loader must not lose positional arguments");
        for(String algorithm:List.of("SHA-1","SHA-256","SHA-512","MD5")){
            byte[] content=text(algorithm);responses.put("https://fixture.test/"+algorithm,content);Path destination=root.resolve("verified-"+algorithm);
            installer(root).obtain(new VersionInstaller.Download("https://fixture.test/"+algorithm,destination,algorithm,hash(content,algorithm),content.length));check(Arrays.equals(content,Files.readAllBytes(destination)),"Hash algorithm "+algorithm);
        }
        for(String path:List.of("../escape","versions/hijack.json","runtime/bin/java.exe",".minecraft/file")){try{ModpackInstaller.contentPath(root,path);throw new AssertionError("Unsafe pack path: "+path);}catch(IOException expected){}}
        Path archive=home.resolve("fixture.mrpack");
        byte[] mod=text("mod-content");String modUrl="https://fixture.test/mod.jar";responses.put(modUrl,mod);
        var manifest=Map.of("formatVersion",1,"game","minecraft","name","Fixture","versionId","1","dependencies",Map.of("minecraft","b1.7.3"),"files",List.of(Map.of("path","mods/fixture.jar","hashes",Map.of("sha512",hash(mod,"SHA-512")),"fileSize",mod.length,"downloads",List.of(modUrl))));
        try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(archive))){for(var entry:Map.of("modrinth.index.json",text(Json.write(manifest)),"overrides/config/example.txt",text("config"),"client-overrides/options.txt",text("client")).entrySet()){zip.putNextEntry(new ZipEntry(entry.getKey()));zip.write(entry.getValue());zip.closeEntry();}}
        var pack=ModpackInstaller.inspect(archive,"Fixture-Pack");Path instance=home.resolve("pack-content");Files.createDirectories(instance);ModpackInstaller.installContent(archive,pack,instance,installer(root),new ContentCatalog(""));
        check(Arrays.equals(Files.readAllBytes(instance.resolve("mods/fixture.jar")),mod)&&Files.readString(instance.resolve("config/example.txt")).equals("config")&&Files.readString(instance.resolve("options.txt")).equals("client"),"Pack manifest and client overrides");
        try(var files=Files.list(root)){check(files.noneMatch(path->path.getFileName().toString().startsWith(".mx-install-")),"Staging cleanup");}
        System.out.println("DOWNLOADS_VANILLA_BETA_SNAPSHOT_NATIVE_ARGS_HASH_PACK_OK");
    }
}

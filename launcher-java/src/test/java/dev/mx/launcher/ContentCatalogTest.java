package dev.mx.launcher;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ContentCatalogTest {
    public static void main(String[] args)throws Exception{
        check(DownloadCatalog.neoGame("21.1.264").equals("1.21.1")&&DownloadCatalog.neoGame("26.3.0.95").equals("26.3")&&DownloadCatalog.neoGame("26.1.2.9").equals("26.1.2"),"NeoForge old/new Minecraft version numbering");
        List<String> requested=new ArrayList<>();
        Map<String,Object> modrinth=Map.of("id","mr-file","name","Example beta","version_type","beta","game_versions",List.of("1.21.1"),"loaders",List.of("fabric"),"files",List.of(Map.of("filename","example.jar","url","https://cdn.modrinth.com/example.jar","size",10,"primary",true,"hashes",Map.of("sha512","a".repeat(128)))),"dependencies",List.of(Map.of("dependency_type","required","project_id","api-project","version_id","api-file")));
        var curse=Map.of("id",123,"displayName","Example alpha","fileName","example.jar","downloadUrl","https://edge.forgecdn.net/example.jar","fileLength",10,"releaseType",3,"gameVersions",List.of("1.21.1","Fabric"),"hashes",List.of(Map.of("algo",1,"value","b".repeat(40))),"dependencies",List.of(Map.of("modId",456,"relationType",3)));
        var parsedMr=ContentCatalog.modrinth(modrinth);check(parsedMr.release().equals("beta")&&parsedMr.algorithm().equals("SHA-512")&&parsedMr.dependencies().size()==1,"Modrinth beta/hash/dependency");
        var parsedCf=ContentCatalog.curse(curse);check(parsedCf.release().equals("alpha")&&parsedCf.loaders().contains("fabric")&&parsedCf.dependencies().size()==1,"CurseForge alpha/loader/dependency");
        ContentCatalog catalog=new ContentCatalog("fixture-key",(url,headers,limit)->{
            requested.add(url);
            if(url.startsWith("https://api.modrinth.com/v2/search")){check(headers.isEmpty(),"Never send CurseForge key to Modrinth");return bytes(Map.of("hits",List.of(Map.of("project_id","example","title","Example","author","Author","description","Summary","slug","example")),"total_hits",1));}
            if(url.startsWith("https://api.curseforge.com/v1/mods/search")){check("fixture-key".equals(headers.get("x-api-key")),"CurseForge auth header");return bytes(Map.of("data",List.of(Map.of("id",1,"name","CF Example","summary","Summary","authors",List.of(Map.of("name","Author")),"links",Map.of("websiteUrl","https://www.curseforge.com/minecraft/mc-mods/example"))),"pagination",Map.of("totalCount",1)));}
            if(url.contains("/version/api-file"))return bytes(Map.of("id","api-file","name","API","version_type","release","game_versions",List.of("1.21.1"),"loaders",List.of("fabric"),"files",List.of(Map.of("filename","api.jar","url","https://cdn.modrinth.com/api.jar","size",10,"hashes",Map.of("sha1","b".repeat(40))))));
            if(url.contains("/project/example/version"))return bytes(List.of(modrinth));
            if(url.contains("/mods/1/files"))return bytes(Map.of("data",List.of(curse)));
            throw new IOException("Unexpected URL "+url);
        });
        for(ContentCatalog.Kind kind:ContentCatalog.Kind.values())for(ContentCatalog.Source source:ContentCatalog.Source.values()){
            var page=catalog.search(source,kind,"test + unicode 中文","1.21.1","fabric",20);check(page.projects().size()==(source==ContentCatalog.Source.ALL?2:1)&&page.projects().get(0).kind()==kind,"Search adapter "+source+" "+kind);
        }
        var project=new ContentCatalog.Project(ContentCatalog.Source.MODRINTH,"example","Example","Author","","",ContentCatalog.Kind.MOD);
        check(catalog.versions(project,"1.21.1","fabric",0).get(0).release().equals("beta"),"Project beta files");
        var cfProject=new ContentCatalog.Project(ContentCatalog.Source.CURSEFORGE,"1","Example","Author","","",ContentCatalog.Kind.MOD);
        check(catalog.versions(cfProject,"1.21.1","fabric",50).size()==1,"CurseForge file pagination");
        check(catalog.resolve(parsedMr,"1.21.1","fabric",true).size()==2,"Automatic required dependencies");
        try{catalog.resolve(parsedMr,"1.20.1","fabric",true);throw new AssertionError("Incompatible dependency");}catch(IOException expected){}
        try{new ContentCatalog("",(url,headers,limit)->{throw new AssertionError("Connected without key");}).search(ContentCatalog.Source.CURSEFORGE,ContentCatalog.Kind.MOD,"","","",0);throw new AssertionError("Missing API key");}catch(IOException expected){}
        var fallback=new ContentCatalog("",(url,headers,limit)->{check(url.contains("api.modrinth.com"),"Missing key still queried CurseForge");return bytes(Map.of("hits",List.of(),"total_hits",0));}).search(ContentCatalog.Source.ALL,ContentCatalog.Kind.MOD,"","","",0);
        check(fallback.notice().contains("CurseForge")&&!fallback.more(),"Merged source availability notice or pagination");
        var data=new ContentCatalog("",(url,headers,limit)->{
            check(url.contains("loaders=%5B%22datapack%22%5D"),"Datapack loader filter missing");
            var pack=new LinkedHashMap<String,Object>(modrinth);pack.put("loaders",List.of("datapack"));pack.put("files",List.of(Map.of("filename","data.zip","url","https://cdn.modrinth.com/data.zip","size",10,"hashes",Map.of("sha1","b".repeat(40)))));
            return bytes(List.of(modrinth,pack));
        }).versions(new ContentCatalog.Project(ContentCatalog.Source.MODRINTH,"data","Data","Author","","",ContentCatalog.Kind.DATA),"1.21.1","",0);
        check(data.size()==1&&data.get(0).filename().equals("data.zip"),"Dual mod/datapack project chose a mod JAR");
        var blocked=new LinkedHashMap<String,Object>(curse);blocked.remove("downloadUrl");check(ContentCatalog.curse(blocked).url()==null,"Respect restricted third-party distribution");
        check(requested.stream().anyMatch(url->url.contains("classId=6552"))&&requested.stream().anyMatch(url->url.contains("index=50")),"Shader category and pagination");
        System.out.println("CONTENT_MODRINTH_CURSEFORGE_FOUR_TYPES_BETA_ALPHA_DEPENDENCIES_AUTH_OK");
    }
    private static byte[] bytes(Object value){return Json.write(value).getBytes(StandardCharsets.UTF_8);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}

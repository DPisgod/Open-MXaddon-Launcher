package dev.mx.launcher;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Modrinth and CurseForge adapters. A missing official download URL is never reconstructed. */
final class ContentCatalog {
    enum Source { MODRINTH("Modrinth"), CURSEFORGE("CurseForge");final String title;Source(String title){this.title=title;}public String toString(){return title;} }
    enum Kind { MOD("模组","mod",6,"mods"), SHADER("光影包","shader",6552,"shaderpacks"), RESOURCE("材质 / 资源包","resourcepack",12,"resourcepacks"), PACK("整合包","modpack",4471,"modpacks");
        final String title,mr,folder;final int cf;Kind(String title,String mr,int cf,String folder){this.title=title;this.mr=mr;this.cf=cf;this.folder=folder;}public String toString(){return title;} }
    record Project(Source source,String id,String title,String author,String description,String website,Kind kind){public String toString(){return title+" · "+author;}}
    record FileVersion(Source source,String id,String title,String release,String filename,String url,String algorithm,String hash,long size,List<String> games,List<String> loaders,List<Dependency> dependencies){
        public String toString(){return title+"  ·  "+release+"  ·  "+filename;}
    }
    record Dependency(String project,String version){}
    record Page(List<Project> projects,int total){}
    private final String key;
    private final DownloadCatalog.Transport transport;
    ContentCatalog(String key){this(key,DownloadCatalog::request);}
    ContentCatalog(String key,DownloadCatalog.Transport transport){this.key=Objects.toString(key,"").strip();this.transport=transport;}
    Object request(String url)throws IOException{
        boolean curse=URI.create(url).getHost().equals("api.curseforge.com");
        if(curse&&key.isEmpty())throw new IOException("请先配置 CurseForge API Key；Modrinth 无需密钥");
        return Json.parse(new String(transport.read(url,curse?Map.of("x-api-key",key):Map.of(),16*1024*1024),StandardCharsets.UTF_8));
    }
    Page search(Source source,Kind kind,String query,String game,String loader,int offset)throws IOException{
        if(source==Source.MODRINTH){
            List<List<String>> facets=new ArrayList<>();facets.add(List.of("project_type:"+kind.mr));
            if(!game.isBlank())facets.add(List.of("versions:"+game));if(kind==Kind.MOD&&!loader.isBlank())facets.add(List.of("categories:"+loader));
            var result=VersionInstaller.map(request("https://api.modrinth.com/v2/search?limit=20&offset="+offset+"&query="+DownloadCatalog.encode(query)+"&facets="+DownloadCatalog.encode(Json.write(facets))));
            List<Project> projects=new ArrayList<>();
            for(Object raw:VersionInstaller.list(result.get("hits"))){var hit=VersionInstaller.map(raw);projects.add(new Project(source,Json.str(hit,"project_id"),Json.str(hit,"title"),Json.str(hit,"author"),Json.str(hit,"description"),"https://modrinth.com/"+kind.mr+"/"+Json.str(hit,"slug"),kind));}
            return new Page(projects,(int)Json.num(result,"total_hits",0));
        }
        var result=VersionInstaller.map(request("https://api.curseforge.com/v1/mods/search?gameId=432&classId="+kind.cf+"&pageSize=20&index="+offset+"&searchFilter="+DownloadCatalog.encode(query)+filters(game,kind==Kind.MOD?loader:"")));
        List<Project> projects=new ArrayList<>();
        for(Object raw:VersionInstaller.list(result.get("data"))){var hit=VersionInstaller.map(raw);List<String> authors=new ArrayList<>();for(Object item:VersionInstaller.list(hit.get("authors")))authors.add(VersionInstaller.str(VersionInstaller.map(item),"name"));
            projects.add(new Project(source,Json.str(hit,"id"),Json.str(hit,"name"),String.join(", ",authors),Json.str(hit,"summary"),Json.str(VersionInstaller.map(hit.get("links")),"websiteUrl"),kind));}
        return new Page(projects,(int)Json.num(VersionInstaller.map(result.get("pagination")),"totalCount",0));
    }
    List<FileVersion> versions(Project project,String game,String loader,int offset)throws IOException{
        if(project.source()==Source.MODRINTH){
            String url="https://api.modrinth.com/v2/project/"+DownloadCatalog.encode(project.id())+"/version?";
            if(!game.isBlank())url+="game_versions="+DownloadCatalog.encode(Json.write(List.of(game)))+"&";
            if(!loader.isBlank()&&project.kind()==Kind.MOD)url+="loaders="+DownloadCatalog.encode(Json.write(List.of(loader)));
            List<FileVersion> result=new ArrayList<>();for(Object raw:VersionInstaller.list(request(url)))result.add(modrinth(VersionInstaller.map(raw)));return result;
        }
        var response=VersionInstaller.map(request("https://api.curseforge.com/v1/mods/"+DownloadCatalog.encode(project.id())+"/files?pageSize=50&index="+offset+filters(game,project.kind()==Kind.MOD?loader:"")));
        List<FileVersion> result=new ArrayList<>();for(Object raw:VersionInstaller.list(response.get("data")))result.add(curse(VersionInstaller.map(raw)));return result;
    }
    FileVersion file(Source source,String project,String id)throws IOException{
        return source==Source.MODRINTH?modrinth(VersionInstaller.map(request("https://api.modrinth.com/v2/version/"+DownloadCatalog.encode(id)))):curse(VersionInstaller.map(VersionInstaller.map(request("https://api.curseforge.com/v1/mods/"+project+"/files/"+id)).get("data")));
    }
    static FileVersion modrinth(Map<String,Object> value)throws IOException{
        List<?> files=VersionInstaller.list(value.get("files"));if(files.isEmpty())throw new IOException("此版本没有可下载文件");
        Map<String,Object> file=VersionInstaller.map(files.get(0));for(Object raw:files)if(Boolean.TRUE.equals(VersionInstaller.map(raw).get("primary"))){file=VersionInstaller.map(raw);break;}
        var hashes=VersionInstaller.map(file.get("hashes"));String algorithm=hashes.containsKey("sha512")?"SHA-512":"SHA-1",hash=VersionInstaller.str(hashes,algorithm.equals("SHA-512")?"sha512":"sha1");
        List<Dependency> dependencies=new ArrayList<>();for(Object raw:VersionInstaller.list(value.get("dependencies"))){var dependency=VersionInstaller.map(raw);if("required".equals(dependency.get("dependency_type")))dependencies.add(new Dependency(Json.str(dependency,"project_id"),Json.str(dependency,"version_id")));}
        return new FileVersion(Source.MODRINTH,Json.str(value,"id"),Json.str(value,"name"),Json.str(value,"version_type"),Json.str(file,"filename"),Json.str(file,"url"),algorithm,hash,Json.num(file,"size",-1),strings(value.get("game_versions")),strings(value.get("loaders")),dependencies);
    }
    static FileVersion curse(Map<String,Object> value)throws IOException{
        String hash="",algorithm="";for(Object raw:VersionInstaller.list(value.get("hashes"))){var item=VersionInstaller.map(raw);long type=Json.num(item,"algo",0);if(type==1||hash.isEmpty()){algorithm=type==1?"SHA-1":"MD5";hash=VersionInstaller.str(item,"value");}}
        List<Dependency> dependencies=new ArrayList<>();for(Object raw:VersionInstaller.list(value.get("dependencies"))){var dependency=VersionInstaller.map(raw);if(Json.num(dependency,"relationType",0)==3)dependencies.add(new Dependency(Json.str(dependency,"modId"),null));}
        List<String> tags=strings(value.get("gameVersions")),loaders=new ArrayList<>();for(String loader:List.of("Fabric","Forge","Quilt","NeoForge"))if(tags.contains(loader))loaders.add(loader.toLowerCase(Locale.ROOT));
        return new FileVersion(Source.CURSEFORGE,Json.str(value,"id"),Json.str(value,"displayName"),switch((int)Json.num(value,"releaseType",1)){case 2->"beta";case 3->"alpha";default->"release";},Json.str(value,"fileName"),Json.str(value,"downloadUrl"),algorithm,hash,Json.num(value,"fileLength",-1),tags,loaders,dependencies);
    }
    Path download(FileVersion file,Path directory,VersionInstaller installer)throws IOException{
        if(file.url()==null||file.url().isBlank())throw new IOException("作者未向第三方启动器开放此文件下载，请到原项目页面下载");
        String name=file.filename();if(name==null||name.contains("/")||name.contains("\\")||name.contains(":"))throw new IOException("无效的资源文件名");
        Path target=VersionInstaller.safe(directory,name);if(Files.exists(target)){
            var task=new VersionInstaller.Download(file.url(),target,file.algorithm(),file.hash(),file.size());
            if(VersionInstaller.valid(target,task))return target;throw new IOException("同名文件已存在，不会覆盖："+name);
        }
        installer.obtain(new VersionInstaller.Download(file.url(),target,file.algorithm(),file.hash(),file.size()));return target;
    }
    List<FileVersion> resolve(FileVersion file,String game,String loader,boolean includeTests)throws IOException{
        LinkedHashMap<String,FileVersion> result=new LinkedHashMap<>();resolve(file,game,loader,includeTests,result,new HashSet<>());return new ArrayList<>(result.values());
    }
    private void resolve(FileVersion file,String game,String loader,boolean tests,LinkedHashMap<String,FileVersion> result,Set<String> visiting)throws IOException{
        if(result.size()>200)throw new IOException("资源依赖过多");String key=file.source()+":"+file.id();if(result.containsKey(key)||!visiting.add(key))return;
        for(Dependency dependency:file.dependencies()){
            FileVersion child;
            if(dependency.version()!=null)child=file(file.source(),dependency.project(),dependency.version());
            else{
                Project project=new Project(file.source(),dependency.project(),"","","","",Kind.MOD);
                child=versions(project,game,loader,0).stream().filter(v->tests||"release".equals(v.release())).findFirst().orElseThrow(()->new IOException("找不到兼容依赖："+dependency.project()));
            }
            if(!game.isBlank()&&!child.games().contains(game))throw new IOException("依赖不支持当前游戏版本："+child.title());
            if(!loader.isBlank()&&!child.loaders().contains(loader))throw new IOException("依赖加载器不匹配："+child.title());
            resolve(child,game,loader,tests,result,visiting);
        }
        result.put(key,file);visiting.remove(key);
    }
    void installFabricApi(String game,Path instance,VersionInstaller installer)throws IOException{
        Project api=new Project(Source.MODRINTH,"fabric-api","Fabric API","FabricMC","","https://modrinth.com/mod/fabric-api",Kind.MOD);
        List<FileVersion> versions=versions(api,game,"fabric",0);if(versions.isEmpty())throw new IOException("尚无兼容 "+game+" 的 Fabric API，可取消自动 Fabric API 后安装");
        download(versions.get(0),instance.resolve("mods"),installer);
    }
    static String filters(String game,String loader){return (game.isBlank()?"":"&gameVersion="+DownloadCatalog.encode(game))+(game.isBlank()||loader.isBlank()?"":"&modLoaderType="+switch(loader){case "forge"->1;case "fabric"->4;case "quilt"->5;case "neoforge"->6;default->0;});}
    static String installedLoader(Map<String,Object> metadata){
        String selected=VersionInstaller.str(metadata,"mxcloudLoader");if(!selected.isBlank())return "vanilla".equals(selected)?"":selected;
        for(Object value:VersionInstaller.list(metadata.get("libraries"))){String name=VersionInstaller.str(VersionInstaller.map(value),"name");if(name.startsWith("org.quiltmc:quilt-loader:"))return "quilt";if(name.startsWith("net.fabricmc:fabric-loader:"))return "fabric";if(name.startsWith("net.neoforged:"))return "neoforge";if(name.startsWith("net.minecraftforge:forge:"))return "forge";}
        return "";
    }
    static List<String> strings(Object value){return VersionInstaller.list(value).stream().map(Object::toString).toList();}
}

package dev.mx.launcher;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/** Publisher metadata shared by the install wizard, content browser and pack importer. */
final class DownloadCatalog {
    static final String MANIFEST="https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    enum Loader { VANILLA("原版"), FABRIC("Fabric"), QUILT("Quilt"), FORGE("Forge"), NEOFORGE("NeoForge");
        final String label; Loader(String label){this.label=label;} public String toString(){return label;} }
    record Game(String id,String type,String url,String sha1,String released){
        Game(String id,String type,String url,String sha1){this(id,type,url,sha1,"");}
        boolean special(){return Set.of("15w14a","1.RV-Pre1","3D Shareware v1.34","20w14infinite","22w13oneblockatatime","23w13a_or_b","24w14potato","25w14craftmine").contains(id)||id.matches(".*(?:craftmine|potato|infinite|oneblockatatime|_or_b).*");}
        String category(){return special()?"愚人节":switch(type){case "release"->"正式版";case "snapshot"->"预览版";default->"远古版";};}
        String date(){try{return java.time.OffsetDateTime.parse(released).atZoneSameInstant(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"));}catch(Exception e){return released;}}
        public String toString(){return id+"  ·  "+category();}
    }
    record LoaderVersion(Loader loader,String version,boolean stable){public String toString(){return version+(stable?"":"  · 测试版");}}
    record Options(String minecraft,Loader loader,String loaderVersion,boolean fabricApi,boolean autoJava,String name){
        String id(){return name==null||name.isBlank()?minecraft+(loader==Loader.VANILLA?"":"-"+loader.label+"-"+loaderVersion):name.strip();}
    }
    @FunctionalInterface interface Transport {byte[] read(String url,Map<String,String> headers,int limit)throws IOException;}
    private final Transport transport;
    DownloadCatalog(){this(DownloadCatalog::request);}
    DownloadCatalog(Transport transport){this.transport=transport;}
    Object json(String url)throws IOException{return Json.parse(new String(transport.read(url,Map.of(),16*1024*1024),StandardCharsets.UTF_8));}
    Map<String,Object> object(String url)throws IOException{return VersionInstaller.map(json(url));}
    List<Game> games()throws IOException{
        List<Game> result=new ArrayList<>();
        for(Object item:VersionInstaller.list(object(MANIFEST).get("versions"))){var value=VersionInstaller.map(item);result.add(new Game(Json.str(value,"id"),Json.str(value,"type"),Json.str(value,"url"),Json.str(value,"sha1"),Objects.toString(value.get("releaseTime"),"")));}
        return result;
    }
    List<LoaderVersion> loaders(Loader loader,String game)throws IOException{
        if(loader==Loader.VANILLA)return List.of();
        List<LoaderVersion> result=new ArrayList<>();
        if(loader==Loader.FABRIC||loader==Loader.QUILT){
            String base=loader==Loader.FABRIC?"https://meta.fabricmc.net/v2":"https://meta.quiltmc.org/v3";
            for(Object item:VersionInstaller.list(json(base+"/versions/loader/"+encode(game)))){
                var metadata=VersionInstaller.map(VersionInstaller.map(item).get("loader"));
                String version=Json.str(metadata,"version");if(version!=null)result.add(new LoaderVersion(loader,version,!Boolean.FALSE.equals(metadata.get("stable"))&&!version.contains("beta")&&!version.contains("alpha")));
            }
        }else{
            String xml=new String(transport.read(loader==Loader.FORGE?"https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml":"https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml",Map.of(),8*1024*1024),StandardCharsets.UTF_8);
            Matcher matcher=Pattern.compile("<version>([^<]+)</version>").matcher(xml);
            while(matcher.find()){
                String version=matcher.group(1);boolean matches=loader==Loader.FORGE?version.startsWith(game+"-"):neoGame(version).equals(game);
                if(matches)result.add(new LoaderVersion(loader,loader==Loader.FORGE?version.substring(game.length()+1):version,!version.contains("beta")&&!version.contains("alpha")));
            }
            Collections.reverse(result);
        }
        result.sort((a,b)->compareLoaderVersions(b.version(),a.version()));return result;
    }
    static int compareLoaderVersions(String a,String b){
        String[] left=a.split("[.\\-+]"),right=b.split("[.\\-+]");
        for(int index=0;index<Math.max(left.length,right.length);index++){
            String x=index<left.length?left[index]:"",y=index<right.length?right[index]:"";
            if(x.equals(y))continue;
            if(x.isEmpty())return 1;if(y.isEmpty())return -1;
            int difference=x.matches("\\d+")&&y.matches("\\d+")?new java.math.BigInteger(x).compareTo(new java.math.BigInteger(y)):x.compareToIgnoreCase(y);
            if(difference!=0)return difference;
        }return 0;
    }
    static String neoGame(String version){String[] p=version.split("\\.");if(p.length<2)return "";if(p.length>=4)return p[0]+"."+p[1]+("0".equals(p[2])?"":"."+p[2]);return "1."+p[0]+("0".equals(p[1])?"":"."+p[1]);}
    static String installerUrl(Loader loader,String game,String version)throws IOException{
        if(!version.matches("[A-Za-z0-9_.+\\-]+")||!game.matches("[A-Za-z0-9_.+\\-]+"))throw new IOException("无效的加载器版本");
        return switch(loader){
            case FORGE->"https://maven.minecraftforge.net/net/minecraftforge/forge/"+game+"-"+version+"/forge-"+game+"-"+version+"-installer.jar";
            case NEOFORGE->"https://maven.neoforged.net/releases/net/neoforged/neoforge/"+version+"/neoforge-"+version+"-installer.jar";
            default->throw new IOException("此加载器通过版本配置安装，请选择自动安装");
        };
    }
    static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    static byte[] request(String url,Map<String,String> headers,int limit)throws IOException{
        URI uri=URI.create(url);
        for(int redirects=0;redirects<8;redirects++){
            if(!"https".equals(uri.getScheme())||uri.getUserInfo()!=null)throw new IOException("下载地址必须为 HTTPS");
            HttpURLConnection connection=(HttpURLConnection)uri.toURL().openConnection();
            connection.setConnectTimeout(15000);connection.setReadTimeout(25000);connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent","DPisgod/Open-MXaddon-Launcher/"+ApplicationVersion.CURRENT+" (github.com/DPisgod/Open-MXaddon-Launcher)");
            connection.setRequestProperty("Accept","application/json");
            if("api.curseforge.com".equals(uri.getHost()))headers.forEach(connection::setRequestProperty);
            try{
                int code=connection.getResponseCode();
                if(Set.of(301,302,303,307,308).contains(code)){String target=connection.getHeaderField("Location");if(target==null)throw new IOException("下载跳转地址缺失");uri=uri.resolve(target);continue;}
                if(code<200||code>=300)throw new IOException("请求失败："+uri.getHost()+"（HTTP "+code+"）");
                try(InputStream input=connection.getInputStream()){byte[] data=input.readNBytes(limit+1);if(data.length>limit)throw new IOException("下载清单超过大小限制");return data;}
            }finally{connection.disconnect();}
        }
        throw new IOException("下载跳转过多");
    }
}

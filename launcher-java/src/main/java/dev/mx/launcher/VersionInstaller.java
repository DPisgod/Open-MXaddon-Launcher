package dev.mx.launcher;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import java.util.zip.*;

/** Installs a pinned Windows x64 instance from publisher metadata, committing only after verification. */
final class VersionInstaller {
    static final String MINECRAFT="1.21.1",LOADER="0.19.3",ID="1.21.1-MXCloud";
    record Progress(String message,int completed,int total,boolean notice){
        Progress(String message,int completed,int total){this(message,completed,total,false);}
    }
    record Download(String url,Path target,String algorithm,String hash,long size){}
    @FunctionalInterface interface ConnectionFactory {URLConnection open(String url)throws IOException;}
    @FunctionalInterface private interface Request<T> {T read(URLConnection connection)throws IOException;}
    private static final int ATTEMPTS=3;
    private final Path root,cache;
    private final Consumer<Progress> progress;
    private final ConnectionFactory connections;
    private volatile int total;
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private final AtomicInteger finished=new AtomicInteger();
    private final Set<Future<?>> pending=ConcurrentHashMap.newKeySet();
    VersionInstaller(Path gameRoot,Consumer<Progress> progress){this(gameRoot,progress,VersionInstaller::open);}
    VersionInstaller(Path gameRoot,Consumer<Progress> progress,ConnectionFactory connections){root=gameRoot.toAbsolutePath().normalize();cache=root.resolve(".mx-downloads");this.progress=progress;this.connections=connections;}
    void cancel(){cancelled.set(true);for(Future<?> task:pending)task.cancel(true);}
    private void check()throws InterruptedIOException{if(cancelled.get()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("安装已取消");}

    String install()throws Exception{
        if(!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows")||!System.getProperty("os.arch").matches("amd64|x86_64"))throw new IOException("目前支持 Windows x64");
        Files.createDirectories(root);Files.createDirectories(cache);
        Path versions=root.resolve("versions");Files.createDirectories(versions);Path destination=versions.resolve(ID);
        if(Files.exists(destination))throw new IOException("此版本已存在，请在版本设置中管理；不会覆盖已有游戏数据。");
        if(Files.getFileStore(root).getUsableSpace()<2_200_000_000L)throw new IOException("安装需要至少 2.2 GB 可用空间（含下载缓存）");
        Path stage=Files.createTempDirectory(root,".mx-install-");
        try{
            Path instance=stage.resolve("instance");Files.createDirectories(instance);
            progress.accept(new Progress("读取兼容版本与下载清单…",0,0));
            Map<String,Object> manifest=json("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
            Map<String,Object> release=null;for(Object o:list(manifest.get("versions"))){Map<String,Object> v=map(o);if(MINECRAFT.equals(v.get("id"))){release=v;break;}}
            if(release==null)throw new IOException("官方清单中没有兼容版本 "+MINECRAFT);
            Path source=stage.resolve("minecraft.json");obtain(new Download(str(release,"url"),source,"SHA-1",str(release,"sha1"),-1));
            Map<String,Object> meta=Json.parseObject(Files.readString(source));
            Map<String,Object> fabric=json("https://meta.fabricmc.net/v2/versions/loader/"+MINECRAFT+"/"+LOADER+"/profile/json");
            if(!MINECRAFT.equals(fabric.get("inheritsFrom")))throw new IOException("Fabric 版本不匹配");
            List<Download> downloads=new ArrayList<>();List<Object> libraries=new ArrayList<>();
            Map<String,Object> client=map(map(meta.get("downloads")).get("client"));
            Path versionDir=instance.resolve("versions").resolve(ID);Files.createDirectories(versionDir);
            downloads.add(download(client,versionDir.resolve(ID+".jar")));
            LinkedHashMap<String,Map<String,Object>> selected=new LinkedHashMap<>();
            for(Object o:list(meta.get("libraries"))){Map<String,Object> lib=map(o);if(windowsLibrary(lib))selected.put(str(lib,"name"),lib);}
            for(Object o:list(fabric.get("libraries")))selected.put(str(map(o),"name"),map(o));
            List<Path> nativeJars=new ArrayList<>();
            for(Map<String,Object> raw:selected.values()){
                Map<String,Object> lib=new LinkedHashMap<>(raw);lib.remove("rules");
                String coordinate=str(lib,"name");Map<String,Object> artifact=map(map(lib.get("downloads")).get("artifact"));
                if(artifact.isEmpty()){
                    String path=mavenPath(coordinate),base=String.valueOf(lib.getOrDefault("url","https://libraries.minecraft.net/"));if(!base.endsWith("/"))base+="/";
                    String url=base+path,hash=new String(readUrl(url+".sha1",1024),StandardCharsets.UTF_8).trim().split("\\s+")[0];
                    artifact=new LinkedHashMap<>();artifact.put("path",path);artifact.put("url",url);artifact.put("sha1",hash);
                    lib.put("downloads",Map.of("artifact",artifact));
                }
                Path file=safe(instance.resolve("libraries"),str(artifact,"path"));downloads.add(download(artifact,file));libraries.add(lib);
                if(coordinate.contains(":natives-windows"))nativeJars.add(file);
            }
            Map<String,Object> index=map(meta.get("assetIndex"));String assetId=str(index,"id");
            if(!assetId.matches("[A-Za-z0-9_.-]+"))throw new IOException("资源索引名称无效");
            Path indexFile=instance.resolve("assets/indexes").resolve(assetId+".json");obtain(download(index,indexFile));
            Map<String,Object> objects=map(Json.parseObject(Files.readString(indexFile)).get("objects"));Set<String> assets=new HashSet<>();
            for(Object o:objects.values()){
                Map<String,Object> asset=map(o);String hash=str(asset,"hash");if(!hash.matches("[a-f0-9]{40}"))throw new IOException("资源散列无效");
                if(assets.add(hash))downloads.add(new Download("https://resources.download.minecraft.net/"+hash.substring(0,2)+"/"+hash,instance.resolve("assets/objects").resolve(hash.substring(0,2)).resolve(hash),"SHA-1",hash,number(asset.get("size"))));
            }
            // 只安装原版 + Fabric 加载器本身；mods 目录留给玩家自行拖入。
            Files.createDirectories(instance.resolve("mods"));
            finished.set(0);total=downloads.size();progress.accept(new Progress("下载游戏、依赖与资源（约 1 GB）…",0,total));
            ExecutorService workers=Executors.newFixedThreadPool(12,r->{Thread t=new Thread(r,"mx-version-download");t.setDaemon(true);return t;});
            CompletionService<Void> completion=new ExecutorCompletionService<>(workers);
            Throwable downloadFailure=null;
            try{
                for(Download task:downloads)pending.add(completion.submit(()->{check();obtain(task);int done=finished.incrementAndGet();progress.accept(new Progress("下载并校验文件 "+done+" / "+downloads.size(),done,downloads.size()));return null;}));
                for(int i=0;i<downloads.size();i++){check();completion.take().get();}
            }catch(Exception|Error failure){downloadFailure=failure;throw failure;}
            finally{for(Future<?> f:pending)f.cancel(true);pending.clear();workers.shutdownNow();if(!workers.awaitTermination(40,TimeUnit.SECONDS)){IOException cleanup=new IOException("下载线程尚未退出，请稍后重试");if(downloadFailure!=null)downloadFailure.addSuppressed(cleanup);else throw cleanup;}}
            check();progress.accept(new Progress("解压原生库并检查安装完整性…",0,0));
            Path natives=versionDir.resolve(ID+"-natives");Files.createDirectories(natives);
            for(Path jar:nativeJars)extractNatives(jar,natives);
            if(!Files.isRegularFile(natives.resolve("glfw.dll")))throw new IOException("缺少 Windows GLFW 原生库");
            meta.put("id",ID);meta.put("libraries",libraries);meta.put("mainClass",fabric.get("mainClass"));meta.remove("inheritsFrom");
            Files.writeString(versionDir.resolve(ID+".json"),Json.write(meta));
            Map<String,Object> outer=new LinkedHashMap<>(meta);outer.put("mxcloudInstance",true);outer.put("mxcloudInstaller",1);
            Files.writeString(stage.resolve(ID+".json"),Json.write(outer));
            Files.writeString(stage.resolve("installation.json"),Json.write(Map.of("minecraft",MINECRAFT,"fabric",LOADER,"files",downloads.size(),"assetObjects",assets.size(),"createdAt",System.currentTimeMillis())));
            Files.delete(source);check();
            try{Files.move(stage,destination,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(stage,destination);}
            progress.accept(new Progress("安装完成",1,1));return ID;
        }finally{if(Files.exists(stage))removeStage(stage,root);}
    }

    static Path gameDirectory(Path gameRoot,String version)throws IOException{
        Path dir=safe(gameRoot.resolve("versions"),version);
        Map<String,Object> meta=Json.parseObject(Files.readString(dir.resolve(version+".json")));
        if(Boolean.TRUE.equals(meta.get("mxcloudInstance"))){Path instance=dir.resolve("instance");if(!Files.isRegularFile(instance.resolve("versions").resolve(version).resolve(version+".jar")))throw new IOException("版本安装不完整，请重新安装");return instance;}
        return gameRoot;
    }
    static boolean windowsLibrary(Map<String,Object> lib){
        String name=str(lib,"name");if(name.contains("natives-windows-arm64")||name.contains("natives-windows-x86"))return false;
        Object raw=lib.get("rules");if(!(raw instanceof List<?> rules))return true;boolean allowed=false;
        for(Object o:rules){Map<String,Object> rule=map(o),os=map(rule.get("os"));boolean matches=true;
            if(os.containsKey("name"))matches="windows".equals(os.get("name"));
            if(os.containsKey("arch"))matches&=System.getProperty("os.arch").matches(str(os,"arch"));
            if(os.containsKey("version"))matches&=System.getProperty("os.version").matches(str(os,"version"));
            if(rule.containsKey("features"))for(Object value:map(rule.get("features")).values())matches&=!Boolean.TRUE.equals(value);
            if(matches)allowed="allow".equals(rule.get("action"));
        }return allowed;
    }
    static Path safe(Path base,String relative)throws IOException{Path normalized=base.toAbsolutePath().normalize(),target=normalized.resolve(relative).normalize();if(relative.isBlank()||!target.startsWith(normalized)||target.equals(normalized)||relative.contains(":"))throw new IOException("无效的安装路径");return target;}
    private static String mavenPath(String coordinate)throws IOException{String[] p=coordinate.split(":");if(p.length<3||p.length>4)throw new IOException("无效依赖坐标");return p[0].replace('.','/')+"/"+p[1]+"/"+p[2]+"/"+p[1]+"-"+p[2]+(p.length==4?"-"+p[3]:"")+".jar";}
    private Download download(Map<String,Object> object,Path target){return new Download(str(object,"url"),target,"SHA-1",str(object,"sha1"),number(object.get("size")));}
    void obtain(Download task)throws IOException{
        check();if(!task.hash.matches("[a-fA-F0-9]{40}"))throw new IOException("下载清单缺少有效校验值");
        Path cached=cache.resolve(task.hash.toLowerCase(Locale.ROOT));
        synchronized(task.hash.intern()){
            if(!valid(cached,task)){
                retry(task.url,task.target.getFileName().toString(),connection->{
                    Path temp=Files.createTempFile(cache,"download-",".part");
                    try{
                        try(InputStream input=connection.getInputStream();OutputStream output=Files.newOutputStream(temp)){
                            byte[] bytes=new byte[65536];int n;long count=0;
                            while((n=input.read(bytes))>=0){check();count+=n;if(count>1_000_000_000L||(task.size>=0&&count>task.size))throw new IOException("下载大小超出清单");output.write(bytes,0,n);}
                        }
                        if(!valid(temp,task))throw new IOException("文件校验失败："+task.target.getFileName());
                        check();Files.move(temp,cached,StandardCopyOption.REPLACE_EXISTING);return null;
                    }finally{Files.deleteIfExists(temp);}
                });
            }
        }
        check();Files.createDirectories(task.target.getParent());Files.copy(cached,task.target,StandardCopyOption.REPLACE_EXISTING);
    }
    static boolean valid(Path file,Download task)throws IOException{
        if(!Files.isRegularFile(file)||(task.size>=0&&Files.size(file)!=task.size))return false;
        MessageDigest digest;try{digest=MessageDigest.getInstance(task.algorithm);}catch(java.security.NoSuchAlgorithmException e){throw new IOException("不支持的校验算法",e);}try(InputStream in=Files.newInputStream(file)){byte[] bytes=new byte[65536];int n;while((n=in.read(bytes))>=0)digest.update(bytes,0,n);}
        return HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(task.hash);
    }
    private Map<String,Object> json(String url)throws IOException{return Json.parseObject(new String(readUrl(url,16*1024*1024),StandardCharsets.UTF_8));}
    byte[] readUrl(String url,int limit)throws IOException{return retry(url,Path.of(URI.create(url).getPath()).getFileName().toString(),connection->{try(InputStream in=connection.getInputStream()){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))>=0){check();if(out.size()+n>limit)throw new IOException("下载清单过大");out.write(buffer,0,n);}return out.toByteArray();}});}
    private <T>T retry(String url,String name,Request<T> request)throws IOException{
        URI uri=URI.create(url);if(!"https".equals(uri.getScheme()))throw new IOException("下载地址必须为 HTTPS");
        String label=uri.getHost()+" / "+name;
        for(int attempt=1;attempt<=ATTEMPTS;attempt++){
            check();URLConnection connection=null;
            try{
                connection=connections.open(url);
                if(connection instanceof HttpURLConnection http){int code=http.getResponseCode();if(code<200||code>=300)throw new IOException("HTTP "+code);}
                T result=request.read(connection);check();return result;
            }catch(IOException failure){
                // SocketTimeoutException is an InterruptedIOException, but is not cancellation.
                check();
                if(failure instanceof InterruptedIOException&&!(failure instanceof SocketTimeoutException))throw failure;
                String reason=failure instanceof SocketTimeoutException?"网络连接或读取超时":failure instanceof UnknownHostException?"域名解析失败":Objects.toString(failure.getMessage(),failure.getClass().getSimpleName());
                System.err.println(java.time.Instant.now()+" [version-install] attempt "+attempt+"/"+ATTEMPTS+" "+uri.getHost()+uri.getRawPath()+" : "+failure);
                if(attempt==ATTEMPTS)throw new IOException("下载失败："+label+"（"+reason+"，已尝试 "+ATTEMPTS+" 次）",failure);
                progress.accept(new Progress("正在重试 "+label+"（第 "+(attempt+1)+" / "+ATTEMPTS+" 次）",finished.get(),total,true));
            }finally{if(connection instanceof HttpURLConnection http)http.disconnect();}
            try{Thread.sleep(attempt*1000L);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();check();}
        }
        throw new AssertionError("unreachable retry state");
    }
    private static URLConnection open(String url)throws IOException{URI uri=URI.create(url);if(!"https".equals(uri.getScheme()))throw new IOException("下载地址必须为 HTTPS");URLConnection c=uri.toURL().openConnection();c.setConnectTimeout(15000);c.setReadTimeout(25000);c.setRequestProperty("User-Agent","MXCloud-Launcher/1.0 (compatible-version-installer)");return c;}
    private static void verifyMod(Path file,String id)throws IOException{try(ZipFile archive=new ZipFile(file.toFile())){ZipEntry entry=archive.getEntry("fabric.mod.json");if(entry==null)throw new IOException("依赖不是 Fabric Mod");try(InputStream in=archive.getInputStream(entry)){if(!id.equals(Json.parseObject(new String(in.readAllBytes(),StandardCharsets.UTF_8)).get("id")))throw new IOException("Mod 标识不匹配");}}}
    private void extractNatives(Path jar,Path destination)throws IOException{try(ZipFile zip=new ZipFile(jar.toFile())){for(ZipEntry entry:Collections.list(zip.entries())){check();String name=entry.getName();if(entry.isDirectory()||!name.toLowerCase(Locale.ROOT).endsWith(".dll"))continue;Path target=destination.resolve(Path.of(name).getFileName().toString());try(InputStream in=zip.getInputStream(entry)){Files.copy(in,target,StandardCopyOption.REPLACE_EXISTING);}}}}
    private static void removeStage(Path stage,Path root)throws IOException{Path checked=stage.toAbsolutePath().normalize();if(!checked.getParent().equals(root)||!checked.getFileName().toString().startsWith(".mx-install-"))throw new IOException("拒绝清理非安装临时目录");try(var files=Files.walk(checked)){for(Path file:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(file);}}
    @SuppressWarnings("unchecked") static Map<String,Object> map(Object value){return value instanceof Map<?,?> m?(Map<String,Object>)m:Map.of();}
    static List<?> list(Object value){return value instanceof List<?> l?l:List.of();}
    static String str(Map<String,Object> map,String key){Object value=map.get(key);return value==null?"":value.toString();}
    private static long number(Object value){return value instanceof Number n?n.longValue():-1;}
}

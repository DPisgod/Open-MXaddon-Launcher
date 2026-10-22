package dev.mx.launcher;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;

/** Bounded local discovery; never searches whole drives or downloads a runtime. */
final class JavaRuntimes {
    record Installation(Path executable,String version,int major,String architecture){
        String title(){return "Java "+major+" ("+version+(architecture.isBlank()?"":", "+architecture)+")";}
    }
    static List<Installation> discover(Path home,Path gameRoot,String configured){
        Map<String,Path> candidates=new LinkedHashMap<>();
        add(candidates,configured);add(candidates,System.getProperty("java.home"));add(candidates,System.getenv("JAVA_HOME"));add(candidates,System.getenv("JRE_HOME"));add(candidates,home.resolve("runtime").toString());
        for(String part:Objects.toString(System.getenv("PATH"),"").split(File.pathSeparator))add(candidates,part);
        for(String variable:List.of("ProgramFiles","ProgramFiles(x86)","ProgramW6432")){
            String base=System.getenv(variable);if(base==null)continue;
            for(String vendor:List.of("Java","Microsoft","Eclipse Adoptium","AdoptOpenJDK","Amazon Corretto","BellSoft","Semeru","Zulu"))children(candidates,Path.of(base,vendor));
        }
        String profile=System.getProperty("user.home","");if(!profile.isBlank()){
            children(candidates,Path.of(profile,".jdks"));children(candidates,Path.of(profile,".jabba","jdk"));
            children(candidates,Path.of(profile,"AppData","Local","Programs","Eclipse Adoptium"));
        }
        Path versions=gameRoot.resolve("versions");
        try(var entries=Files.list(versions)){for(Path version:entries.filter(Files::isDirectory).limit(256).toList()){add(candidates,version.resolve("instance/runtime").toString());add(candidates,version.resolve("runtime").toString());}}catch(IOException|SecurityException ignored){}
        List<Installation> result=new ArrayList<>();
        for(Path candidate:candidates.values()){
            if(Thread.currentThread().isInterrupted())break;
            try{result.add(inspect(candidate));}catch(IOException|RuntimeException ignored){}
        }
        result.sort(Comparator.comparingInt(Installation::major).reversed().thenComparing(Installation::version,JavaRuntimes::newestFirst).thenComparing(i->i.executable().toString(),String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }
    private static void children(Map<String,Path> paths,Path parent){
        add(paths,parent.toString());try(var children=Files.list(parent)){children.filter(Files::isDirectory).limit(128).forEach(child->{add(paths,child.toString());add(paths,child.resolve("current").toString());});}catch(IOException|SecurityException ignored){}
    }
    private static void add(Map<String,Path> paths,String value){
        if(value==null||value.isBlank())return;
        try{
            Path input=Path.of(value.replace("\"","")).toAbsolutePath().normalize();
            Path bin=Files.isDirectory(input)?(Files.isRegularFile(input.resolve("javaw.exe"))||Files.isRegularFile(input.resolve("java.exe"))?input:input.resolve("bin")):input.getParent();
            if(bin==null)return;String filename=input.getFileName()==null?"":input.getFileName().toString();
            Path executable=Files.isRegularFile(input)&&(filename.equalsIgnoreCase("java.exe")||filename.equalsIgnoreCase("javaw.exe"))?input:bin.resolve("javaw.exe");if(!Files.isRegularFile(executable))executable=bin.resolve("java.exe");
            if(!Files.isRegularFile(executable))return;executable=executable.toRealPath();paths.putIfAbsent(executable.getParent().toString().toLowerCase(Locale.ROOT),executable);
        }catch(IOException|RuntimeException ignored){}
    }
    static Installation inspect(Path executable)throws IOException{
        Path binary=executable.toAbsolutePath().normalize();String file=binary.getFileName().toString();
        if(!Files.isRegularFile(binary)||(!file.equalsIgnoreCase("java.exe")&&!file.equalsIgnoreCase("javaw.exe")))throw new IOException("请选择有效的 java.exe 或 javaw.exe");
        Properties metadata=new Properties();Path root=binary.getParent().getParent();
        try(InputStream input=Files.newInputStream(root.resolve("release"))){metadata.load(input);}catch(IOException ignored){}
        String version=unquote(metadata.getProperty("JAVA_VERSION","")),arch=unquote(metadata.getProperty("OS_ARCH",""));
        if(major(version)==0){
            Path console=binary.getParent().resolve("java.exe");if(!Files.isRegularFile(console))throw new IOException("无法读取 Java 版本");
            Process process=new ProcessBuilder(console.toString(),"-XshowSettings:properties","-version").redirectErrorStream(true).start();
            try{
                if(!process.waitFor(3,TimeUnit.SECONDS))throw new IOException("Java 版本检测超时");
                String output=new String(process.getInputStream().readNBytes(32768),Charset.defaultCharset());
                Matcher match=Pattern.compile("(?:java|openjdk) version \\\"([^\\\"]+)\\\"").matcher(output);
                if(match.find())version=match.group(1);
                Matcher architecture=Pattern.compile("(?m)^\\s*os\\.arch\\s*=\\s*(\\S+)").matcher(output);if(architecture.find())arch=architecture.group(1);
            }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException("Java 检测已取消",interrupted);}finally{process.destroyForcibly();}
        }
        int feature=major(version);if(feature==0)throw new IOException("无法识别 Java 版本");
        arch=switch(arch.toLowerCase(Locale.ROOT)){case "amd64","x86_64","x64"->"x64";case "x86","i386","i686"->"x86";case "aarch64","arm64"->"ARM64";default->arch;};
        return new Installation(binary,version,feature,arch);
    }
    private static String unquote(String value){return value.replace("\"","").trim();}
    private static int newestFirst(String first,String second){
        String[] a=first.split("[^0-9]+"),b=second.split("[^0-9]+");for(int i=0;i<Math.max(a.length,b.length);i++){long x=i<a.length&&!a[i].isBlank()?Long.parseLong(a[i]):0,y=i<b.length&&!b[i].isBlank()?Long.parseLong(b[i]):0;int order=Long.compare(y,x);if(order!=0)return order;}return first.compareTo(second);
    }
    static int major(String version){try{String value=version.startsWith("1.")?version.substring(2):version;Matcher match=Pattern.compile("^([0-9]+)").matcher(value);return match.find()?Integer.parseInt(match.group(1)):0;}catch(NumberFormatException invalid){return 0;}}
    static Optional<Installation> matching(List<Installation> runtimes,int major){return runtimes.stream().filter(i->i.major()==major&&!i.architecture().equals("x86")&&!i.architecture().equals("ARM64")).findFirst();}
}

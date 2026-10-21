package dev.mx.launcher;

import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.io.*;
import java.util.Properties;

/** Physical RAM is sampled again for every launch, including launches of additional clients. */
final class MemoryAllocation {
    record Ram(long totalMb,long freeMb) {}
    record Choice(String mode,int mb) {}
    static Ram ram(){
        if(ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os)
            return new Ram(os.getTotalMemorySize()/1048576,os.getFreeMemorySize()/1048576);
        return new Ram(0,0);
    }
    static int clamp(int mb){return Math.max(1024,Math.min(32768,mb));}
    static int automatic(Ram ram){
        if(ram.totalMb<=0)return 4096;
        long free=Math.max(0,Math.min(ram.totalMb,ram.freeMb));
        long reserve=Math.max(1536,ram.totalMb/12);
        long target=Math.min(8192,Math.min(ram.totalMb/4,free*2/5));
        return clamp((int)(Math.max(0,Math.min(target,free-reserve))/128*128));
    }
    static int resolve(Choice choice,Settings global,Ram ram){
        String mode=choice.mode;int mb=choice.mb;
        if("follow".equals(mode)){mode=global.memoryMode;mb=global.memoryMb;}
        return "auto".equals(mode)?automatic(ram):clamp(mb);
    }
    static int resolve(Path root,String version,Settings global){return resolve(load(root,version),global,ram());}
    static Path file(Path root,String version){
        Path versions=root.toAbsolutePath().normalize().resolve("versions"),dir=versions.resolve(version).normalize();
        if(version.isBlank()||!dir.getParent().equals(versions))throw new IllegalArgumentException("无效的游戏版本");
        return dir.resolve("OML-settings.properties");
    }
    static Choice load(Path root,String version){
        Path settings=file(root,version),legacy=settings.resolveSibling("mxcloud-settings.properties");
        if(!Files.exists(settings)&&Files.isRegularFile(legacy))try{Files.move(legacy,settings);}catch(IOException ignored){}
        Properties p=new Properties();try(InputStream in=Files.newInputStream(Files.exists(settings)?settings:legacy)){p.load(in);}catch(IOException ignored){}
        String mode=p.getProperty("memoryMode","follow");if(!java.util.Set.of("follow","auto","custom").contains(mode))mode="follow";
        int mb=4096;try{mb=Integer.parseInt(p.getProperty("memoryMb","4096"));}catch(NumberFormatException ignored){}
        return new Choice(mode,clamp(mb));
    }
    static void save(Path root,String version,Choice choice)throws IOException{
        Path file=file(root,version);Files.createDirectories(file.getParent());Properties p=new Properties();
        p.setProperty("memoryMode",choice.mode);p.setProperty("memoryMb",String.valueOf(clamp(choice.mb)));
        Path temp=Files.createTempFile(file.getParent(),".memory-",".tmp");
        try{try(OutputStream out=Files.newOutputStream(temp)){p.store(out,"OML version settings");}
            Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
        }finally{Files.deleteIfExists(temp);}
    }
}

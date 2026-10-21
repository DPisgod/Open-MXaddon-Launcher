package dev.mx.launcher;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import com.sun.jna.platform.win32.Crypt32Util;

/** Per-Windows-user API key; only encrypted bytes are stored beside launcher data. */
final class DownloadCredentials {
    static String load(){
        String environment=System.getenv("CURSEFORGE_API_KEY");if(environment!=null&&!environment.isBlank())return environment.strip();
        Path file=LauncherPaths.dataDirectory(LauncherPaths.home()).resolve("curseforge-key.dat");
        try{return new String(Crypt32Util.cryptUnprotectData(Files.readAllBytes(file)),StandardCharsets.UTF_8).strip();}catch(Exception failure){return "";}
    }
    static void save(String key)throws IOException{
        Path directory=LauncherPaths.dataDirectory(LauncherPaths.home()),file=directory.resolve("curseforge-key.dat");
        if(key.isBlank()){Files.deleteIfExists(file);return;}
        byte[] protectedBytes=Crypt32Util.cryptProtectData(key.strip().getBytes(StandardCharsets.UTF_8));
        Path temporary=Files.createTempFile(directory,".curseforge-",".tmp");
        try{Files.write(temporary,protectedBytes);Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temporary);}
    }
}

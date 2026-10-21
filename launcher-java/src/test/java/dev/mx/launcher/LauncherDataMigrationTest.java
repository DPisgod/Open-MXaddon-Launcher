package dev.mx.launcher;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import com.sun.jna.platform.win32.Crypt32Util;

/** Covers state relocation using real DPAPI ciphertext and independent existing game files. */
public final class LauncherDataMigrationTest {
    static void check(boolean yes,String message){if(!yes)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        Path home=Files.createTempDirectory("mx-data-migration-");Path data=home.resolve(".minecraft");Files.createDirectories(data.resolve("versions/keep/world"));Files.writeString(data.resolve("versions/keep/world/level.dat"),"world-marker");
        Files.createDirectories(home.resolve("wallpapers"));Files.writeString(home.resolve("wallpapers/keep.png"),"wallpaper-marker");Files.createDirectories(home.resolve("skins"));Files.writeString(home.resolve("skins/keep.png"),"skin-marker");
        Map<String,byte[]> original=new HashMap<>();
        String account="{\"explicitAccounts\":true,\"accounts\":[{\"name\":\"迁移玩家\",\"premium\":false}]}";
        original.put("accounts.dat",Crypt32Util.cryptProtectData(account.getBytes(StandardCharsets.UTF_8)));
        original.put("device-session.dat",Crypt32Util.cryptProtectData("{\"device_token\":\"synthetic-only\",\"hwid\":\"synthetic-hwid\"}".getBytes(StandardCharsets.UTF_8)));
        Properties session=new Properties();session.setProperty("name","迁移玩家");session.setProperty("premium","false");session.setProperty("explicitAccount","true");try(var out=new java.io.ByteArrayOutputStream()){session.store(out,"legacy fixture");original.put("session.properties",out.toByteArray());}
        original.put("appearance.properties","theme=dark\nmask=37\nwallpaper=saved-id\n".getBytes(StandardCharsets.ISO_8859_1));
        original.put("launcher.properties","memoryMb=6144\nmemoryMode=custom\nlastVersion=keep\n".getBytes(StandardCharsets.ISO_8859_1));
        for(var entry:original.entrySet())Files.write(home.resolve(entry.getKey()),entry.getValue());
        ExecutorService workers=Executors.newFixedThreadPool(4);try{List<Callable<Path>> jobs=new ArrayList<>();for(int i=0;i<12;i++)jobs.add(()->LauncherPaths.dataDirectory(home));for(Future<Path> result:workers.invokeAll(jobs))check(result.get().equals(data),"wrong directory");}finally{workers.shutdownNow();}
        for(var entry:original.entrySet()){check(!Files.exists(home.resolve(entry.getKey())),"legacy file remains");check(Arrays.equals(Files.readAllBytes(data.resolve(entry.getKey())),entry.getValue()),"migration changed file bytes");}
        check(new AccountStore(home).available(null).get(0).name.equals("迁移玩家"),"account not restored");check(SessionStore.load(home).name.equals("迁移玩家"),"current session not restored");
        check(Settings.load(home).memoryMb==6144&&Settings.load(home).lastVersion.equals("keep"),"memory/version preferences lost");check(Appearance.load(home).theme.equals("dark")&&Appearance.load(home).mask==37,"appearance lost");
        byte[] decrypted=Crypt32Util.cryptUnprotectData(Files.readAllBytes(LauncherPaths.dataFile(home,"device-session.dat")));try{check(Json.str(Json.parseObject(new String(decrypted,StandardCharsets.UTF_8)),"device_token").equals("synthetic-only"),"DPAPI cannot read relocated authorization");}finally{Arrays.fill(decrypted,(byte)0);}
        Settings updated=Settings.load(home);updated.memoryMb=3072;updated.save(home);Appearance appearance=Appearance.load(home);appearance.theme="light";appearance.save(home);SessionStore.save(home,Session.offline("新玩家"));new AccountStore(home).remember(Session.offline("新玩家"));
        check(Settings.load(home).memoryMb==3072&&Appearance.load(home).theme.equals("light")&&SessionStore.load(home).name.equals("新玩家"),"writes do not use relocated state");for(String name:LauncherPaths.STATE_FILES)check(!Files.exists(home.resolve(name)),"writer recreated legacy root file");
        SessionStore.clear(home);check(!Files.exists(data.resolve("session.properties")),"signout failed to clear new path");
        byte[] current=Files.readAllBytes(data.resolve("launcher.properties"));Files.writeString(home.resolve("launcher.properties"),"memoryMb=1024\n");LauncherPaths.dataDirectory(home);
        check(Arrays.equals(current,Files.readAllBytes(data.resolve("launcher.properties"))),"existing destination overwritten");check(!Files.exists(home.resolve("launcher.properties")),"conflict left in root");
        try(var paths=Files.list(data)){Path backup=paths.filter(p->p.getFileName().toString().startsWith(".launcher-migration-")).findFirst().orElseThrow();check(Files.readString(backup.resolve("launcher.properties")).equals("memoryMb=1024\n"),"conflicting old file not preserved");}
        check(Files.readString(data.resolve("versions/keep/world/level.dat")).equals("world-marker")&&Files.readString(home.resolve("wallpapers/keep.png")).equals("wallpaper-marker")&&Files.readString(home.resolve("skins/keep.png")).equals("skin-marker"),"unrelated files modified");
        Path fresh=Files.createTempDirectory("mx-data-fresh-");SessionStore.save(fresh,Session.offline("新安装"));check(Files.exists(fresh.resolve(".minecraft/session.properties"))&&!Files.exists(fresh.resolve("session.properties")),"fresh install writes to root");
        Path blocked=Files.createTempDirectory("mx-data-blocked-");Files.writeString(blocked.resolve("accounts.dat"),"untouched");Files.writeString(blocked.resolve(".minecraft"),"blocking-file");boolean failed=false;try{LauncherPaths.dataDirectory(blocked);}catch(java.io.UncheckedIOException expected){failed=true;}check(failed&&Files.readString(blocked.resolve("accounts.dat")).equals("untouched"),"failed migration lost old state");
        System.out.println("STATE_FIVE_FILES_MIGRATE_DPAPI_READ_WRITE_CONCURRENT_CONFLICT_PRESERVE_OK");System.exit(0);
    }
}

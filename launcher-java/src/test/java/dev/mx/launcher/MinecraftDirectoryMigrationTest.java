package dev.mx.launcher;

import java.nio.file.*;

public final class MinecraftDirectoryMigrationTest {
    public static void main(String[] args)throws Exception{
        Path home=Files.createTempDirectory("mx-minecraft-migration-");Path old=home.resolve(".min");Files.createDirectories(old.resolve("versions/old/instance/saves/world"));Files.writeString(old.resolve("versions/old/instance/saves/world/level.dat"),"world");Files.writeString(old.resolve("announcement.properties"),"dismissed=keep");
        LauncherPaths.override(home);Path root=LauncherPaths.gameRoot();check(root.equals(home.resolve(".minecraft")),"New root");check(!Files.exists(old)&&Files.readString(root.resolve("versions/old/instance/saves/world/level.dat")).equals("world"),"Whole legacy instance retained");check(Files.readString(root.resolve("announcement.properties")).contains("keep"),"Dismissed announcement retained");
        Path merged=Files.createTempDirectory("mx-migration-merge-");Path legacy=merged.resolve(".min"),modern=merged.resolve(".minecraft");Files.createDirectories(legacy.resolve("versions/old"));Files.createDirectories(legacy.resolve("versions/conflict"));Files.createDirectories(modern.resolve("versions/conflict"));
        Files.writeString(legacy.resolve("versions/old/old.json"),"old");Files.writeString(legacy.resolve("versions/conflict/world.dat"),"legacy-world");Files.writeString(modern.resolve("versions/conflict/world.dat"),"modern-world");Files.writeString(legacy.resolve("session.properties"),"old-account");Files.writeString(modern.resolve("session.properties"),"current-account");
        LauncherPaths.dataDirectory(merged);check(!Files.exists(legacy)&&Files.readString(modern.resolve("versions/old/old.json")).equals("old"),"Nonconflicting old instance must remain visible");check(Files.readString(modern.resolve("versions/conflict/world.dat")).equals("modern-world"),"Existing instance never overwritten");
        try(var paths=Files.list(modern)){Path backup=paths.filter(path->path.getFileName().toString().startsWith(".min-migration-backup-")).findFirst().orElseThrow();check(Files.readString(backup.resolve("versions/conflict/world.dat")).equals("legacy-world")&&Files.readString(backup.resolve("session.properties")).equals("old-account"),"Conflicting worlds and accounts retained in recovery");}
        check(Files.readString(modern.resolve("session.properties")).equals("current-account"),"Current account retained");
        System.out.println("MINECRAFT_DIRECTORY_MIGRATE_MERGE_CONFLICT_WORLD_ACCOUNT_OK");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}

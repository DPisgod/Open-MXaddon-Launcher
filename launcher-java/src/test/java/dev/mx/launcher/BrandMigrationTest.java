package dev.mx.launcher;

import java.nio.file.*;

/** Renaming a settings file must preserve existing per-version memory choices. */
public final class BrandMigrationTest {
    public static void main(String[] args) throws Exception {
        Path root=Files.createTempDirectory("oml-brand-test-");
        Path version=root.resolve("versions/legacy");Files.createDirectories(version);
        Path legacy=version.resolve("mxcloud-settings.properties");
        Files.writeString(legacy,"memoryMode=custom\nmemoryMb=6144\n");
        if(!MemoryAllocation.load(root,"legacy").equals(new MemoryAllocation.Choice("custom",6144)))
            throw new AssertionError("Legacy settings lost");
        if(Files.exists(legacy)||!Files.isRegularFile(version.resolve("OML-settings.properties")))
            throw new AssertionError("Settings filename was not migrated");
        // A current OML setting wins when both generations are present.
        Files.writeString(legacy,"memoryMode=custom\nmemoryMb=2048\n");
        if(MemoryAllocation.load(root,"legacy").mb()!=6144)throw new AssertionError("Current settings overwritten");
        try(var entries=Files.walk(root)){
            for(Path path:entries.sorted(java.util.Comparator.reverseOrder()).toList())Files.delete(path);
        }
        System.out.println("OML_BRAND_MIGRATION_OK");
    }
}

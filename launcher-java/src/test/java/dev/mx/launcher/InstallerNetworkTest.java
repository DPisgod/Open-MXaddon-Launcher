package dev.mx.launcher;

import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Real publisher downloads into a new isolated root, without production accounts. */
public final class InstallerNetworkTest {
    public static void main(String[] args)throws Exception{
        Path root=Path.of(args[0]);AtomicInteger printed=new AtomicInteger();
        VersionInstaller installer=new VersionInstaller(root,p->{if(p.total()==0||p.completed()==p.total()||p.completed()/200>printed.get()){printed.set(p.completed()/200);System.out.println(p.message());}});
        String id=installer.install();Path game=VersionInstaller.gameDirectory(root,id);
        if(!Files.isRegularFile(game.resolve("versions").resolve(id).resolve(id+".jar")))throw new AssertionError("missing client");
        var mods=new ModManager(game).list();
        java.util.Set<String> ids=new java.util.HashSet<>();
        for(var mod:mods)try(var zip=new java.util.zip.ZipFile(mod.file().toFile());var input=zip.getInputStream(zip.getEntry("fabric.mod.json"))){ids.add(Json.str(Json.parseObject(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)),"id"));}
        if(mods.size()!=2||!ids.equals(java.util.Set.of("fabric-api","fabric-language-kotlin")))throw new AssertionError("expected only Fabric API and Kotlin");
        System.out.println("CLEAN_INSTALL_OK "+game);System.exit(0);
    }
}

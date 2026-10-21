package dev.mx.launcher;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

public final class ModManagerTest {
    static void check(boolean yes,String message){if(!yes)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("mx-mod-management-");Path input=root.resolve("中文示例.jar");
        try(ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(input))){out.putNextEntry(new ZipEntry("fabric.mod.json"));out.write("{\"id\":\"test\",\"name\":\"中文示例\",\"version\":\"1.0\"}".getBytes(StandardCharsets.UTF_8));out.closeEntry();}
        ModManager first=new ModManager(root.resolve("one")),second=new ModManager(root.resolve("two"));Path file=first.add(input);byte[] original=Files.readAllBytes(file);
        check(first.list().size()==1&&second.list().isEmpty(),"instances mixed");
        Path disabled=first.toggle(file);check(disabled.toString().endsWith(".jar.disabled")&&!first.list().get(0).enabled()&&Arrays.equals(original,Files.readAllBytes(disabled)),"disable changed contents");
        file=first.toggle(disabled);check(file.getFileName().toString().equals("中文示例.jar")&&first.list().get(0).enabled(),"enable failed");
        boolean rejected=false;try{first.add(input);}catch(java.io.IOException e){rejected=true;}check(rejected,"duplicate silently overwritten");
        rejected=false;try{first.toggle(input);}catch(java.io.IOException e){rejected=true;}check(rejected,"external file changed");
        Path saved=first.uninstall(file);check(first.list().isEmpty()&&Arrays.equals(original,Files.readAllBytes(saved)),"uninstall not recoverable");
        Path broken=root.resolve("broken.jar");Files.writeString(broken,"broken");rejected=false;try{first.add(broken);}catch(java.io.IOException e){rejected=true;}check(rejected&&first.list().isEmpty(),"invalid import remained");
        rejected=false;try{VersionInstaller.safe(root,"../escape");}catch(java.io.IOException e){rejected=true;}check(rejected,"installer traversal");
        check(!VersionInstaller.windowsLibrary(Map.of("name","native:test:1:natives-windows-arm64")),"ARM64 included");
        System.out.println("MOD_ADD_TOGGLE_RESTORE_UNINSTALL_ISOLATION_OK");System.exit(0);
    }
}

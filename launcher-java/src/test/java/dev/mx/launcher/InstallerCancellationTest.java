package dev.mx.launcher;

import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;

public final class InstallerCancellationTest {
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("mx-install-cancel-");AtomicReference<VersionInstaller> ref=new AtomicReference<>();
        VersionInstaller installer=new VersionInstaller(root,p->{if(p.total()>100)ref.get().cancel();});ref.set(installer);
        boolean failed=false;try{installer.install();}catch(Exception expected){failed=true;}
        if(!failed||Files.exists(root.resolve("versions").resolve(VersionInstaller.ID)))throw new AssertionError("incomplete version published");
        try(var children=Files.list(root)){if(children.anyMatch(p->p.getFileName().toString().startsWith(".mx-install-")))throw new AssertionError("cancelled staging leaked");}
        if(!Files.isDirectory(root.resolve(".mx-downloads")))throw new AssertionError("download cache removed");
        System.out.println("INSTALL_CANCEL_NO_PARTIAL_VERSION_OK");System.exit(0);
    }
}

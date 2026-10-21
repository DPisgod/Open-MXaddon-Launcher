package dev.mx.launcher;

import java.io.File;

/** Runs the local installer's public API in a separate JVM, never on the UI thread. */
public final class OptiFineBootstrap {
    public static void main(String[] args)throws Exception{
        if(args.length!=2)throw new IllegalArgumentException("Expected an isolated Minecraft directory and version");
        Class<?> installer=Class.forName("optifine.Installer");
        String version=(String)installer.getMethod("getOptiFineVersion").invoke(null);
        String minecraft=(String)installer.getMethod("getMinecraftVersionFromOfVersion",String.class).invoke(null,version);
        if(!args[1].equals(minecraft))throw new IllegalArgumentException("OptiFine targets Minecraft "+minecraft+", selected "+args[1]);
        installer.getMethod("doInstall",File.class).invoke(null,new File(args[0]));
    }
}

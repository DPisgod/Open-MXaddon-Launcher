package dev.mx.launcher;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;

/** Configure the launcher before AWT initializes; game JVM options are independent. */
final class LauncherRuntime {
    private static PrintStream diagnostic;
    private LauncherRuntime(){}
    static void configureRendering(){
        if(System.getProperty("os.name","").startsWith("Windows")){
            System.setProperty("sun.java2d.d3d","false");
            System.setProperty("sun.java2d.opengl","false");
        }
    }
    static void diagnostics(Path home){
        try{
            Path directory=LauncherPaths.dataDirectory(home).resolve("logs");Files.createDirectories(directory);
            Path log=directory.resolve("launcher.log");
            if(Files.exists(log)&&Files.size(log)>1024*1024)Files.move(log,directory.resolve("launcher.previous.log"),StandardCopyOption.REPLACE_EXISTING);
            PrintStream errors=new PrintStream(Files.newOutputStream(log,StandardOpenOption.CREATE,StandardOpenOption.APPEND),true,StandardCharsets.UTF_8);
            System.setErr(errors);
            diagnostic=errors;
            errors.println(Instant.now()+" launcher start; java="+System.getProperty("java.version")+"; d3d="+System.getProperty("sun.java2d.d3d")+"; opengl="+System.getProperty("sun.java2d.opengl")+"; maximize=work-area-resize");
            Thread.setDefaultUncaughtExceptionHandler((thread,error)->{errors.println(Instant.now()+" uncaught on "+thread.getName());error.printStackTrace(errors);});
        }catch(IOException|UncheckedIOException|SecurityException unavailable){System.err.println("Launcher diagnostic log unavailable: "+unavailable.getClass().getSimpleName());}
    }
    static void windowMode(boolean expanded,java.awt.Rectangle bounds){PrintStream log=diagnostic;if(log!=null)log.println(Instant.now()+" window="+(expanded?"work-area":"restored")+" bounds="+bounds);}
}

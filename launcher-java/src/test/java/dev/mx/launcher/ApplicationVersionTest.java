package dev.mx.launcher;

public final class ApplicationVersionTest {
    public static void main(String[] args){
        if(args.length!=1||!args[0].equals(ApplicationVersion.CURRENT))throw new AssertionError("Launcher and setup versions differ");
        System.out.println("SHARED_APPLICATION_VERSION_OK "+ApplicationVersion.CURRENT);
    }
}

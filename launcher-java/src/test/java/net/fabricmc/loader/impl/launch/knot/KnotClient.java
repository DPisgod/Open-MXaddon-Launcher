package net.fabricmc.loader.impl.launch.knot;

import java.nio.file.*;
import java.util.zip.ZipFile;

/** Test-only game stand-in. Never packaged into the launcher or bootstrap JAR. */
public final class KnotClient {
    public static void main(String[] args)throws Exception{
        if(!"synthetic-bootstrap-ticket".equals(System.getProperty("mx.ticket"))||!"synthetic-hwid".equals(System.getProperty("mx.hwid")))throw new AssertionError("authorization transport differs");
        Path body=Path.of(System.getProperty("mx.payload.path"));
        if(!body.toString().contains("MXCloud")||!System.getProperty("fabric.addMods","").contains(body.toString()))throw new AssertionError("missing mod path");
        try(ZipFile jar=new ZipFile(body.toFile())){if(jar.getEntry("fabric.mod.json")==null)throw new AssertionError("mod not readable");}
        if(Boolean.getBoolean("mx.test.fail"))throw new IllegalStateException("synthetic game startup failure");
        System.out.println("SYNTHETIC_KNOT_LOADED_OK");
    }
}

package dev.mx.launcher;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import com.sun.jna.platform.win32.Crypt32Util;

/** Exercises the packaged application credential and real DPAPI overrides in an isolated home. */
public final class DownloadCredentialsTest {
    public static void main(String[] args)throws Exception{
        Path home=Files.createTempDirectory("oml-download-credentials-");
        LauncherPaths.override(home);
        Path saved=LauncherPaths.gameRoot().resolve("curseforge-key.dat");
        try{
            if(args.length>0&&args[0].equals("--environment")){
                DownloadCredentials.save("fixture-local-key");
                check(DownloadCredentials.load().equals("fixture-environment-key"),"Environment must override local and bundled credentials");
                System.out.println("CURSEFORGE_ENVIRONMENT_PRIORITY_OK");return;
            }
            check(System.getenv("CURSEFORGE_API_KEY")==null||System.getenv("CURSEFORGE_API_KEY").isBlank(),"Run without an environment override");
            String bundled;
            try(InputStream input=DownloadCredentials.class.getResourceAsStream("/OML-curseforge-api.key")){
                bundled=input==null?"":new String(input.readAllBytes(),StandardCharsets.UTF_8).strip();
            }
            check(DownloadCredentials.load().equals(bundled),"Fresh installation must use the bundled credential");
            DownloadCredentials.save("  fixture-local-key  ");
            check(DownloadCredentials.load().equals("fixture-local-key"),"Local credential must override the bundled credential");
            check(!new String(Files.readAllBytes(saved),StandardCharsets.ISO_8859_1).contains("fixture-local-key"),"Local credential stored as plaintext");
            DownloadCredentials.save("  ");
            check(!Files.exists(saved)&&DownloadCredentials.load().equals(bundled),"Clearing a local credential must restore the default");
            Files.write(saved,Crypt32Util.cryptProtectData("  ".getBytes(StandardCharsets.UTF_8)));
            check(DownloadCredentials.load().equals(bundled),"Blank decrypted credential must fall back to default");
            Files.write(saved,new byte[]{1,2,3});
            check(DownloadCredentials.load().equals(bundled),"Unreadable local credential must fall back to default");
            Files.delete(saved);
            System.out.println("CURSEFORGE_BUNDLED_DPAPI_OVERRIDE_CLEAR_FALLBACK_OK");
            if(args.length>0&&args[0].equals("--live")){
                check(!bundled.isEmpty(),"Live check requires a bundled application credential");
                ContentCatalog catalog=new ContentCatalog(DownloadCredentials.load());
                for(var kind:new ContentCatalog.Kind[]{ContentCatalog.Kind.MOD,ContentCatalog.Kind.SHADER,ContentCatalog.Kind.RESOURCE,ContentCatalog.Kind.PACK}){
                    var page=catalog.search(ContentCatalog.Source.CURSEFORGE,kind,"","","",0);
                    check(!page.projects().isEmpty(),"Empty CurseForge search: "+kind);
                    check(!catalog.versions(page.projects().get(0),"","",0).isEmpty(),"Empty CurseForge files: "+kind);
                    check(!catalog.categories(ContentCatalog.Source.CURSEFORGE,kind).isEmpty(),"Empty CurseForge categories: "+kind);
                    System.out.println("CURSEFORGE_LIVE_SEARCH_FILES_CATEGORIES_OK "+kind);
                }
            }
        }finally{
            Files.deleteIfExists(saved);Files.deleteIfExists(home.resolve(".minecraft"));Files.deleteIfExists(home);LauncherPaths.override(null);
        }
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}

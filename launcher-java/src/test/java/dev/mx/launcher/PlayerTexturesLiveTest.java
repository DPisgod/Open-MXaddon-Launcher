package dev.mx.launcher;

import java.nio.file.Files;

/** Optional official-service smoke test. No account credentials are used. */
public final class PlayerTexturesLiveTest {
    public static void main(String[] args)throws Exception{
        var home=Files.createTempDirectory("oml-public-textures-");var textures=new PlayerTextures(home);
        var profile=textures.fetch(args.length==0?"Dinnerbone":args[0]);
        if(profile.skin()==null||profile.skin().getWidth()!=64||profile.uuid().length()!=32||textures.cached(profile.uuid())==null)throw new AssertionError("Official profile/texture/cache smoke test failed");
        System.out.println("OFFICIAL_PUBLIC_PROFILE_TEXTURE_CACHE_OK name="+profile.name()+" cape="+(profile.cape()!=null)+" slim="+profile.slim());
    }
}

package dev.mx.launcher;

import java.nio.file.*;

/** Public-profile lookup only; no Minecraft login or production credentials involved. */
public final class SkinLookupLiveTest {
    public static void main(String[] args)throws Exception{
        Path home=Files.createTempDirectory("mx-public-skin-test-");
        Session offline=Session.offline("Notch");SkinFetcher.fetch(offline,home);
        if(!Files.isRegularFile(home.resolve("skins").resolve(SkinFetcher.cacheKey(offline)+".png")))throw new AssertionError("Offline name skin lookup failed");
        Session premium=Session.premium("Notch","069a79f444e94726a5befca90e38aaf5","fake-no-access",null);SkinFetcher.fetch(premium,home);
        if(!Files.isRegularFile(home.resolve("skins").resolve(SkinFetcher.cacheKey(premium)+".png")))throw new AssertionError("UUID skin lookup failed");
        System.out.println("PUBLIC_NAME_AND_UUID_SKIN_LOOKUP_OK");System.exit(0);
    }
}

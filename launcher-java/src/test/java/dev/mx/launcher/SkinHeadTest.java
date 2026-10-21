package dev.mx.launcher;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;

/** Head pixels must come from each account's face/hat, never the body placeholder. */
public final class SkinHeadTest {
    static void check(boolean yes,String message){if(!yes)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        Path home=Files.createTempDirectory("mx-skin-test-");
        Session first=Session.offline("测试玩家"),second=Session.offline("另一账号");
        BufferedImage raw=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);
        for(int y=8;y<16;y++)for(int x=8;x<16;x++)raw.setRGB(x,y,0xffc08050);
        raw.setRGB(40,8,0xff2299ff);
        Path file=home.resolve("皮肤 PNG.png");ImageIO.write(raw,"png",file.toFile());
        BufferedImage head=SkinFetcher.importSkin(first,home,file);
        check(head.getWidth()==128&&head.getHeight()==128,"head size");
        check(head.getRGB(0,0)==0xff2299ff,"hat layer was lost");
        check(head.getRGB(64,64)==0xffc08050,"face was not cropped");
        check(SkinFetcher.fetch(first,home).getRGB(0,0)==0xff2299ff,"custom account head not used");
        raw.setRGB(40,8,0xff00ff55);ImageIO.write(raw,"png",file.toFile());SkinFetcher.importSkin(second,home,file);
        check(SkinFetcher.cached(second,home).getRGB(0,0)==0xff00ff55&&SkinFetcher.cached(first,home).getRGB(0,0)==0xff2299ff,"account skin caches mixed");
        Path invalid=home.resolve("invalid.png");ImageIO.write(new BufferedImage(32,32,BufferedImage.TYPE_INT_RGB),"png",invalid.toFile());
        boolean rejected=false;try{SkinFetcher.importSkin(first,home,invalid);}catch(java.io.IOException expected){rejected=true;}
        check(rejected&&SkinFetcher.cached(first,home).getRGB(0,0)==0xff2299ff,"invalid import overwrote skin");
        Session premium=Session.premium("Premium","123456781234123412341234567890ab","no-access",null);
        rejected=false;try{SkinFetcher.importSkin(premium,home,file);}catch(java.io.IOException expected){rejected=true;}
        check(rejected,"local override allowed for official account");
        if(args.length>0){BufferedImage official=SkinFetcher.gameDefaultHead(Path.of(args[0]),"alex");check(official!=null&&official.getWidth()==128,"local game head unavailable");}
        System.out.println("PER_ACCOUNT_SKIN_FACE_HAT_CACHE_ROLLBACK_OK");System.exit(0);
    }
}

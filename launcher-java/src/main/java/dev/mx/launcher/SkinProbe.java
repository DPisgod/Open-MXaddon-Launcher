package dev.mx.launcher;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 皮肤头像获取的无界面验证。
 *
 * <pre>
 *   java -cp mxaddon-launcher.jar dev.mx.launcher.SkinProbe
 * </pre>
 */
public final class SkinProbe {

    /** 一个公开的正版 UUID（Notch），用于验证在线皮肤接口。 */
    private static final String NOTCH = "069a79f444e94726a5befca90e38aaf5";

    public static void main(String[] args) throws Exception {
        Path tmp = Files.createTempDirectory("mxcloud-skin");

        System.out.println("=== 皮肤头像探测 ===");

        // 1) 离线 -> Steve
        Session off = Session.offline("Player");
        BufferedImage steve = SkinFetcher.fetch(off, tmp);
        check("离线头像非空", steve != null);
        check("离线头像尺寸", steve.getWidth() == 128 && steve.getHeight() == 128);
        System.out.println("  离线头像: " + steve.getWidth() + "x" + steve.getHeight());

        // 2) 正版 -> 在线拉取
        Session prem = Session.premium("Notch", NOTCH, "dummy", null);
        BufferedImage online = SkinFetcher.fetch(prem, tmp);
        check("正版头像非空", online != null);
        System.out.println("  正版头像: " + online.getWidth() + "x" + online.getHeight());

        // 3) 缓存是否生成
        Path cache = tmp.resolve("skins").resolve(NOTCH + ".png");
        check("缓存文件已生成", Files.isRegularFile(cache));
        System.out.println("  缓存路径: " + cache.getFileName());

        // 4) 断网场景（用不存在的 UUID）-> 回退 Steve
        Session bad = Session.premium("Nobody", "00000000000000000000000000000000", "x", null);
        BufferedImage fallback = SkinFetcher.fetch(bad, tmp);
        check("异常 UUID 回退成功", fallback != null);
        System.out.println("  异常回退: " + fallback.getWidth() + "x" + fallback.getHeight());

        // 5) 再次读取（命中缓存）
        BufferedImage cached = SkinFetcher.fetch(prem, tmp);
        check("缓存命中非空", cached != null);

        // 清理
        try (var s = Files.walk(tmp)) {
            s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (Exception ignored) {}
            });
        }

        System.out.println("=== 皮肤探测通过 ===");
        Runtime.getRuntime().halt(0);
    }

    private static void check(String name, boolean cond) {
        if (!cond) {
            throw new AssertionError("断言失败: " + name);
        }
        System.out.println("  ✓ " + name);
    }
}

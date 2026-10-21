package dev.mx.launcher;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipFile;

/**
 * 皮肤头像获取。
 *
 * <p><b>主方案：Mojang 官方 API + 自行裁剪</b>
 *
 * <pre>
 *   1. GET sessionserver.mojang.com/session/minecraft/profile/{uuid}
 *      → 返回 base64 编码的 textures JSON
 *   2. 解出 textures.SKIN.url（textures.minecraft.net/...）
 *   3. 下载 64x64 皮肤图
 *   4. 裁出头部区域（8,8)-(16,16) + 帽子层（40,8)-(48,16) 合成头像
 * </pre>
 *
 * 正版优先使用官方皮肤；离线按名字查询，中文账号可导入本地皮肤。
 * 网络不可用时使用账号缓存及本地游戏的默认皮肤头部。
 */
public final class SkinFetcher {

    /** 离线默认头像（Steve）资源名，放在 jar 内。 */
    private static final String STEVE_RES = "/dev/mx/launcher/assets/steve.png";

    private static final int SIZE = 128;
    private static final int TIMEOUT_MS = 8000;
    private static final Map<String,Long> LAST_LOOKUP = new ConcurrentHashMap<>();
    private static final Map<String,BufferedImage> DEFAULT_HEADS = new ConcurrentHashMap<>();

    private SkinFetcher() {
    }

    // ============================================================
    //  对外接口
    // ============================================================

    /**
     * 获取头像。
     *
     * @param s    当前会话
     * @param home 启动器根目录（用于缓存）
     * @return 头像图片；任何异常都回退到 Steve，不会抛错
     */
    public static BufferedImage fetch(Session s, Path home) {
        if(s==null)return steve(home);
        Path cacheDir=home.resolve("skins"),cache=cacheDir.resolve(cacheKey(s)+".png");
        BufferedImage cached=readImage(cache);if(cached!=null)return cached;
        String lookup=cacheKey(s);long now=System.currentTimeMillis();Long last=LAST_LOOKUP.get(lookup);
        if(last!=null&&now-last<300_000)return defaultHead(s,home);
        LAST_LOOKUP.put(lookup,now);
        String uuid=s.premium?s.uuid:null;
        if(!s.premium&&s.name.matches("[A-Za-z0-9_]{3,16}")){
            byte[] profile=httpGet("https://api.mojang.com/users/profiles/minecraft/"+s.name);
            if(profile!=null)try{uuid=Json.str(Json.parseObject(new String(profile,StandardCharsets.UTF_8)),"id");}catch(Exception ignored){}
        }
        BufferedImage img=null;
        if(uuid!=null&&uuid.matches("[a-fA-F0-9]{32}")){
            img=fromMojang(uuid);
            if(img==null)img=fromThirdParty(uuid);
        }
        if (img != null) {
            synchronized(SkinFetcher.class){BufferedImage latest=readImage(cache);if(latest!=null)return latest;writeCache(cacheDir, cache, img);}
            return img;
        }

        // 4) 最终兜底
        return defaultHead(s,home);
    }

    static String cacheKey(Session s){return s.premium?s.uuid.replace("-","").toLowerCase():"offline-"+UUID.nameUUIDFromBytes(("OfflinePlayer:"+s.name).getBytes(StandardCharsets.UTF_8)).toString().replace("-","");}
    static BufferedImage cached(Session s,Path home){BufferedImage image=readImage(home.resolve("skins").resolve(cacheKey(s)+".png"));return image!=null?image:defaultHead(s,home);}
    static BufferedImage gameDefaultHead(Path home,String name){return fromGameDefault(home,name);}
    static void invalidate(Session s,Path home)throws IOException{Files.deleteIfExists(home.resolve("skins").resolve(cacheKey(s)+".png"));LAST_LOOKUP.remove(cacheKey(s));}

    /** Local PNG skin -> face + transparent hat layer. No account token or skin is uploaded. */
    static BufferedImage importSkin(Session s,Path home,Path source)throws IOException{
        if(s.premium)throw new IOException("正版头像使用该账号的官方皮肤");
        if(Files.size(source)>8*1024*1024)throw new IOException("请选择小于 8 MB 的 PNG 皮肤");
        BufferedImage skin;
        try(var input=ImageIO.createImageInputStream(source.toFile())){
            var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IOException("无法读取皮肤 PNG");
            var reader=readers.next();try{reader.setInput(input);int w=reader.getWidth(0),h=reader.getHeight(0);
                if(!reader.getFormatName().equalsIgnoreCase("png")||w<64||w>2048||w%64!=0||(h!=w&&h*2!=w))throw new IOException("请选择 64×64、64×32 或等比例放大的 Minecraft PNG 皮肤");
                skin=reader.read(0);
            }finally{reader.dispose();}
        }
        BufferedImage head=cropHead(skin);Path dir=home.resolve("skins");Files.createDirectories(dir);Path temp=Files.createTempFile(dir,"head-",".tmp");
        try{synchronized(SkinFetcher.class){ImageIO.write(head,"png",temp.toFile());Files.move(temp,dir.resolve(cacheKey(s)+".png"),StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(temp);}
        return head;
    }

    private static BufferedImage defaultHead(Session s,Path home){
        String[] defaults={"steve","alex","ari","efe","kai","makena","noor","sunny","zuri"};
        String name=defaults[Math.floorMod(UUID.nameUUIDFromBytes(("OfflinePlayer:"+s.name).getBytes(StandardCharsets.UTF_8)).hashCode(),defaults.length)];
        BufferedImage head=fromGameDefault(home,name);return head!=null?head:steve(home);
    }

    private static BufferedImage fromGameDefault(Path home,String name){
        if(home==null)return null;String key=home.toAbsolutePath()+":"+name;BufferedImage cached=DEFAULT_HEADS.get(key);if(cached!=null)return cached;
        Path versions=home.resolve(".min/versions");if(!Files.isDirectory(versions))return null;
        try(var dirs=Files.list(versions)){
            for(Path version:dirs.filter(Files::isDirectory).toList()){
                String id=version.getFileName().toString();Path actual=VersionInstaller.gameDirectory(home.resolve(".min"),id);
                Path jar=actual.resolve("versions").resolve(id).resolve(id+".jar");if(!Files.isRegularFile(jar)||Files.size(jar)<1024*1024)continue;
                try(ZipFile archive=new ZipFile(jar.toFile())){var entry=archive.getEntry("assets/minecraft/textures/entity/player/wide/"+name+".png");if(entry==null)continue;
                    try(InputStream in=archive.getInputStream(entry)){BufferedImage raw=ImageIO.read(in);if(raw!=null){BufferedImage head=cropHead(raw);DEFAULT_HEADS.put(key,head);return head;}}
                }catch(IOException ignored){}
            }
        }catch(IOException ignored){}
        return null;
    }

    /** 离线默认头像（内嵌 Steve；资源缺失时程序化绘制一个 Steve 风格头像）。 */
    public static BufferedImage steve(Path home) {
        if (home != null) {
            BufferedImage custom = readImage(home.resolve("skins").resolve("steve.png"));
            if (custom != null) {
                return custom;
            }
        }
        try (InputStream in = SkinFetcher.class.getResourceAsStream(STEVE_RES)) {
            if (in != null) {
                BufferedImage img = ImageIO.read(in);
                if (img != null) {
                    return img;
                }
            }
        } catch (IOException ignored) {
        }
        BufferedImage gameDefault=fromGameDefault(home,"steve");
        return gameDefault!=null?gameDefault:drawSteve();
    }

    // ============================================================
    //  主方案：Mojang 官方
    // ============================================================

    /**
     * 从 Mojang 官方接口获取皮肤并裁成头像。
     *
     * @return 128x128 头像；失败返回 null
     */
    private static BufferedImage fromMojang(String uuid) {
        try {
            // 1) 取 profile（含 textures 属性）
            String profileUrl =
                    "https://sessionserver.mojang.com/session/minecraft/profile/" + uuid;
            byte[] profileBytes = httpGet(profileUrl);
            if (profileBytes == null || profileBytes.length == 0) {
                return null;
            }
            Map<String, Object> profile = Json.parseObject(
                    new String(profileBytes, StandardCharsets.UTF_8));

            String skinUrl = extractSkinUrl(profile);
            if (skinUrl == null) {
                return null;
            }

            // 2) 下载皮肤本体（64x64 PNG）
            //    注意：Mojang 返回的地址是 http://，很多环境会拦截；
            //    统一提升为 https。
            if (skinUrl.startsWith("http://")) {
                skinUrl = "https://" + skinUrl.substring("http://".length());
            }
            byte[] skinBytes = httpGet(skinUrl);
            if (skinBytes == null || skinBytes.length == 0) {
                return null;
            }
            BufferedImage skin = ImageIO.read(new ByteArrayInputStream(skinBytes));
            if (skin == null) {
                return null;
            }

            // 3) 裁剪头部 + 帽子层 → 头像
            return cropHead(skin);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 从 profile JSON 里解出皮肤 URL。 */
    @SuppressWarnings("unchecked")
    private static String extractSkinUrl(Map<String, Object> profile) {
        Object propsObj = profile.get("properties");
        if (!(propsObj instanceof List)) {
            return null;
        }
        for (Object o : (List<Object>) propsObj) {
            if (!(o instanceof Map)) continue;
            Map<String, Object> p = (Map<String, Object>) o;
            if (!"textures".equals(Json.str(p, "value")) && !"textures".equals(Json.str(p, "name"))) {
                continue;
            }
            String b64 = Json.str(p, "value");
            if (b64 == null || b64.isBlank()) continue;
            try {
                String json = new String(java.util.Base64.getDecoder().decode(b64),
                        StandardCharsets.UTF_8);
                Map<String, Object> tex = Json.parseObject(json);
                Map<String, Object> textures = Json.obj(tex, "textures");
                if (textures == null) continue;
                Map<String, Object> skin = Json.obj(textures, "SKIN");
                if (skin == null) continue;
                return Json.str(skin, "url");
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /**
     * 把 64x64（或 64x32 旧版）皮肤裁成 128x128 头像。
     *
     * <p>Minecraft 皮肤的头部区域：
     * <ul>
     *   <li>脸：(8,8) 起 8x8 像素</li>
     *   <li>帽子/头饰层：(40,8) 起 8x8 像素（半透明叠加）</li>
     * </ul>
     */
    private static BufferedImage cropHead(BufferedImage skin) {
        int scale = skin.getWidth() / 64;     // 皮肤可能是 64x64 或放大版
        if (scale < 1) scale = 1;

        BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        // 脸部：(8,8) 8x8
        BufferedImage face = sub(skin, 8 * scale, 8 * scale, 8 * scale, 8 * scale);
        // 帽子层：(40,8) 8x8（旧版 64x32 皮肤没有这一层）
        BufferedImage hat = null;
        if (skin.getWidth() >= 64 * scale && skin.getHeight() >= 16 * scale) {
            hat = sub(skin, 40 * scale, 8 * scale, 8 * scale, 8 * scale);
        }

        int faceSize = SIZE * 8 / 8;   // 铺满
        g.drawImage(face, 0, 0, faceSize, faceSize, null);
        if (hat != null && hasAnyOpaque(hat)) {
            g.drawImage(hat, 0, 0, faceSize, faceSize, null);
        }
        g.dispose();
        return out;
    }

    private static BufferedImage sub(BufferedImage src, int x, int y, int w, int h) {
        return src.getSubimage(
                Math.min(x, src.getWidth() - 1),
                Math.min(y, src.getHeight() - 1),
                Math.min(w, src.getWidth() - Math.max(0, Math.min(x, src.getWidth() - 1))),
                Math.min(h, src.getHeight() - Math.max(0, Math.min(y, src.getHeight() - 1))));
    }

    /** 判断图层是否有不透明像素（避免全透明的帽子层盖住脸）。 */
    private static boolean hasAnyOpaque(BufferedImage img) {
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if (((img.getRGB(x, y) >>> 24) & 0xFF) > 8) {
                    return true;
                }
            }
        }
        return false;
    }

    // ============================================================
    //  兜底：第三方皮肤站
    // ============================================================

    private static BufferedImage fromThirdParty(String uuid) {
        // minotar 较稳；crafatar / mc-heads 近年常出 422/403
        String[] urls = {
            "https://minotar.net/helm/" + uuid + "/" + SIZE + ".png",
            "https://mc-heads.net/avatar/" + uuid + "/" + SIZE,
            "https://crafatar.com/avatars/" + uuid + "?size=" + SIZE + "&overlay",
        };
        for (String u : urls) {
            byte[] data = httpGet(u);
            if (data == null || data.length == 0) {
                continue;
            }
            try {
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
                if (img != null) {
                    return img;
                }
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    // ============================================================
    //  HTTP / 文件
    // ============================================================

    private static byte[] httpGet(String url) {
        HttpURLConnection conn = null;
        try {
            URL u = URI.create(url).toURL();
            conn = (HttpURLConnection) u.openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", "MXCloud-Launcher/1.0");
            conn.setInstanceFollowRedirects(true);
            if (conn.getResponseCode() != 200) {
                return null;
            }
            try (InputStream in = conn.getInputStream()) {
                return in.readAllBytes();
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static BufferedImage readImage(Path p) {
        if (p == null || !Files.isRegularFile(p)) {
            return null;
        }
        try {
            BufferedImage img = ImageIO.read(p.toFile());
            // 旧缓存可能是 12 字节的错误响应，尺寸非法就丢弃
            if (img == null || img.getWidth() < 8 || img.getHeight() < 8) {
                Files.deleteIfExists(p);
                return null;
            }
            return img;
        } catch (IOException e) {
            return null;
        }
    }

    private static void writeCache(Path dir, Path target, BufferedImage img) {
        try {
            Files.createDirectories(dir);
            Path tmp = dir.resolve(target.getFileName() + ".tmp");
            ImageIO.write(img, "png", tmp.toFile());
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
        }
    }

    // ============================================================
    //  程序化绘制 Steve（无资源时的最终兜底）
    // ============================================================

    /**
     * 程序化绘制一个 Steve 风格头像（无资源时的最终兜底）。
     *
     * <p>按 Minecraft 皮肤的真实比例绘制（在 8x8 网格上放大）：
     * 头部占上方，肩部在下缘被圆形裁剪，整体居中。
     */
    private static BufferedImage drawSteve() {
        BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(0x2B, 0x30, 0x3A));
        g.fillRect(0, 0, SIZE, SIZE);

        Color skin     = new Color(0xC6, 0x8A, 0x5E);
        Color skinDark = new Color(0xA8, 0x6E, 0x48);
        Color hair     = new Color(0x3B, 0x28, 0x1C);
        Color shirt    = new Color(0x3E, 0x8C, 0x8C);
        Color eye      = new Color(0x3B, 0x5A, 0x8C);
        Color eyebrow  = new Color(0x4A, 0x33, 0x22);
        Color mouth    = new Color(0x8A, 0x5A, 0x3A);

        int headW = 56, headH = 56;
        int hx = (SIZE - headW) / 2;
        int hy = 20;

        int bodyW = 76, bodyH = 34;
        int bx = (SIZE - bodyW) / 2;
        int by = hy + headH - 2;

        g.setColor(shirt);
        g.fillRect(bx, by, bodyW, bodyH);
        g.setColor(shirt.darker());
        g.fillRect(bx, by, bodyW, 5);

        g.setColor(skin);
        g.fillRect(hx, hy, headW, headH);
        g.setColor(skinDark);
        g.fillRect(hx, hy + headH - 6, headW, 6);

        g.setColor(hair);
        g.fillRect(hx, hy, headW, 13);
        g.fillRect(hx, hy, 6, 30);
        g.fillRect(hx + headW - 6, hy, 6, 30);

        g.setColor(eyebrow);
        g.fillRect(hx + 11, hy + 22, 12, 3);
        g.fillRect(hx + headW - 23, hy + 22, 12, 3);

        g.setColor(Color.WHITE);
        g.fillRect(hx + 11, hy + 26, 12, 9);
        g.fillRect(hx + headW - 23, hy + 26, 12, 9);
        g.setColor(eye);
        g.fillRect(hx + 15, hy + 27, 7, 7);
        g.fillRect(hx + headW - 19, hy + 27, 7, 7);

        g.setColor(skinDark);
        g.fillRect(hx + headW / 2 - 3, hy + 36, 6, 8);

        g.setColor(mouth);
        g.fillRect(hx + 18, hy + 47, 20, 3);

        g.dispose();
        BufferedImage head=new BufferedImage(SIZE,SIZE,BufferedImage.TYPE_INT_ARGB);Graphics2D face=head.createGraphics();face.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);face.drawImage(img,0,0,SIZE,SIZE,hx,hy,hx+headW,hy+headH,null);face.dispose();return head;
    }
}

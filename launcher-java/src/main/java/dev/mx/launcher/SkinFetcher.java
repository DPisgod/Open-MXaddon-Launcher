package dev.mx.launcher;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
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
        Path full=Files.createTempFile(dir,"skin-",".tmp");
        try{synchronized(SkinFetcher.class){
            ImageIO.write(head,"png",temp.toFile());Files.move(temp,dir.resolve(cacheKey(s)+".png"),StandardCopyOption.REPLACE_EXISTING);
            // 玩偶用整张皮肤，与头像分开缓存
            ImageIO.write(skin,"png",full.toFile());Files.move(full,dir.resolve(cacheKey(s)+"-skin.png"),StandardCopyOption.REPLACE_EXISTING);
        }}finally{Files.deleteIfExists(temp);Files.deleteIfExists(full);}
        return head;
    }

    private static BufferedImage defaultHead(Session s,Path home){
        String[] defaults={"steve","alex","ari","efe","kai","makena","noor","sunny","zuri"};
        String name=defaults[Math.floorMod(UUID.nameUUIDFromBytes(("OfflinePlayer:"+s.name).getBytes(StandardCharsets.UTF_8)).hashCode(),defaults.length)];
        BufferedImage head=fromGameDefault(home,name);return head!=null?head:steve(home);
    }

    private static BufferedImage fromGameDefault(Path home,String name){
        BufferedImage raw=gameTexture(home,name);return raw==null?null:cropHead(raw);
    }

    /** 从已安装客户端 jar 取整张默认皮肤（64×64），供皮肤玩偶使用。 */
    private static BufferedImage gameTexture(Path home,String name){
        if(home==null)return null;String key="raw:"+home.toAbsolutePath()+":"+name;BufferedImage cached=DEFAULT_HEADS.get(key);if(cached!=null)return cached;
        Path versions=home.resolve(".minecraft/versions");if(!Files.isDirectory(versions))return null;
        try(var dirs=Files.list(versions)){
            for(Path version:dirs.filter(Files::isDirectory).toList()){
                String id=version.getFileName().toString();Path actual=VersionInstaller.gameDirectory(home.resolve(".minecraft"),id);
                Path jar=actual.resolve("versions").resolve(id).resolve(id+".jar");if(!Files.isRegularFile(jar)||Files.size(jar)<1024*1024)continue;
                try(ZipFile archive=new ZipFile(jar.toFile())){
                    for(String path:new String[]{"assets/minecraft/textures/entity/player/wide/"+name+".png",
                                                 "assets/minecraft/textures/entity/"+name+".png"}){
                        var entry=archive.getEntry(path);if(entry==null)continue;
                        try(InputStream in=archive.getInputStream(entry)){BufferedImage raw=ImageIO.read(in);if(raw!=null){DEFAULT_HEADS.put(key,raw);return raw;}}
                    }
                }catch(IOException ignored){}
            }
        }catch(IOException ignored){}
        return null;
    }


    /** 离线默认头像（优先保留自定义头像，兜底使用原版 Steve 皮肤裁剪）。 */
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
            conn.setRequestProperty("User-Agent", "OML-Launcher/1.0");
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
    // ============================================================
    //  皮肤玩偶：整张皮肤 + 披风
    // ============================================================

    private static final Map<String,Object[]> TEXTURES = new ConcurrentHashMap<>();

    record Textures(String skin, String cape, boolean slim) {}

    /**
     * 玩偶用的整张皮肤。
     *
     * <p>正版：官方皮肤（textures.minecraft.net，落到 skins/&lt;key&gt;-skin.png）；
     * 离线：玩家导入的本地皮肤，或客户端 jar 里的官方默认 Steve / Alex。
     */
    static BufferedImage fullSkin(Session s, Path home) {
        if (s == null) return PlayerTextures.defaultSkin();
        Path file = home.resolve("skins").resolve(cacheKey(s) + "-skin.png");
        BufferedImage cached = readImage(file);
        if (cached != null) return cached;
        if (s.premium && s.uuid != null && s.uuid.replace("-", "").matches("[a-fA-F0-9]{32}")) {
            Textures textures = textures(s.uuid);
            if (textures != null && textures.skin() != null) {
                BufferedImage image = download(textures.skin());
                if (image != null) {
                    writeCache(file.getParent(), file, image);
                    return image;
                }
            }
        }
        return PlayerTextures.defaultSkin();
    }

    /** 玩偶披风：只有正版账号带官方披风；取不到返回 null。 */
    static BufferedImage cape(Session s, Path home) {
        if (s == null || !s.premium || s.uuid == null || s.uuid.isBlank()) return null;
        Path file = home.resolve("skins").resolve(cacheKey(s) + "-cape.png");
        BufferedImage cached = readImage(file);
        if (cached != null) return cached;
        Textures textures = textures(s.uuid);
        if (textures == null || textures.cape() == null) return null;
        BufferedImage image = download(textures.cape());
        if (image != null) {
            writeCache(file.getParent(), file, image);
            return image;
        }
        return null;
    }

    /** slim（Alex 细臂）判定：经典手臂背面区域 (52,20)-(56,32) 全空即视为 3 像素手臂。 */
    static boolean slim(BufferedImage skin) {
        if (skin == null || skin.getHeight() < 64) return false;
        int scale = Math.max(1, skin.getWidth() / 64);
        for (int y = 20; y < 32; y++) {
            for (int x = 52; x < 56; x++) {
                int px = Math.min(skin.getWidth() - 1, x * scale);
                int py = Math.min(skin.getHeight() - 1, y * scale);
                if (((skin.getRGB(px, py) >>> 24) & 0xFF) > 8) return false;
            }
        }
        return true;
    }

    private static String defaultName(Session s) {
        String[] defaults = {"steve", "alex", "ari", "efe", "kai", "makena", "noor", "sunny", "zuri"};
        String name = s == null ? "Steve" : s.name;
        return defaults[Math.floorMod(UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)).hashCode(), defaults.length)];
    }

    /** 解析官方 textures 属性：皮肤地址、披风地址、是否 slim。结果缓存 10 分钟。 */
    @SuppressWarnings("unchecked")
    private static Textures textures(String uuid) {
        String key = uuid.replace("-", "").toLowerCase();
        Object[] cached = TEXTURES.get(key);
        if (cached != null && System.currentTimeMillis() - (Long) cached[0] < 600_000) return (Textures) cached[1];
        Textures result = null;
        byte[] bytes = httpGet("https://sessionserver.mojang.com/session/minecraft/profile/" + key);
        if (bytes != null && bytes.length > 0) {
            try {
                Map<String, Object> profile = Json.parseObject(new String(bytes, StandardCharsets.UTF_8));
                if (profile.get("properties") instanceof List<?> properties) {
                    for (Object item : properties) {
                        if (!(item instanceof Map<?, ?> raw)) continue;
                        Map<String, Object> property = (Map<String, Object>) raw;
                        if (!"textures".equals(Json.str(property, "name"))) continue;
                        String base64 = Json.str(property, "value");
                        if (base64 == null || base64.isBlank()) continue;
                        Map<String, Object> decoded = Json.parseObject(new String(java.util.Base64.getDecoder().decode(base64), StandardCharsets.UTF_8));
                        Map<String, Object> list = Json.obj(decoded, "textures");
                        if (list == null) continue;
                        String skinUrl = null, capeUrl = null;
                        Map<String, Object> skin = Json.obj(list, "SKIN");
                        if (skin != null) skinUrl = Json.str(skin, "url");
                        Map<String, Object> cape = Json.obj(list, "CAPE");
                        if (cape != null) capeUrl = Json.str(cape, "url");
                        boolean slimModel = "slim".equalsIgnoreCase(Json.str(Json.obj(skin == null ? Map.of() : skin, "metadata"), "model"));
                        result = new Textures(https(skinUrl), https(capeUrl), slimModel);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        TEXTURES.put(key, new Object[]{System.currentTimeMillis(), result});
        return result;
    }

    private static String https(String url) {
        if (url == null || url.isBlank()) return null;
        return url.startsWith("http://") ? "https://" + url.substring("http://".length()) : url;
    }

    private static BufferedImage download(String url) {
        byte[] bytes = httpGet(url);
        if (bytes == null || bytes.length == 0) return null;
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            return image == null || image.getWidth() < 32 ? null : image;
        } catch (IOException failure) {
            return null;
        }
    }

    /** Original bundled Minecraft skin; no procedural replacement. */
    private static BufferedImage drawSteve(){return cropHead(PlayerTextures.defaultSkin());}
}

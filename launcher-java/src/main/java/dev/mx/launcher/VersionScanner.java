package dev.mx.launcher;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 游戏版本扫描。
 *
 * <p><b>只认启动器目录下的 {@code .min\versions\*} 子目录名</b>，
 * 不扫描 {\@code %APPDATA%\.minecraft} 或其它任何位置。
 *
 * <p>目录结构假定为：
 *
 * <pre>
 *   &lt;启动器目录&gt;\.min\
 *       ├── versions\
 *       │     ├── 1.20.1\        ← 版本名取自这里的目录名
 *       │     ├── 1.20.4\
 *       │     └── 1.21\
 *       ├── assets\
 *       └── ...
 * </pre>
 */
public final class VersionScanner {

    private VersionScanner() {
    }

    /**
     * 扫描版本列表，按版本号从新到旧排序。
     *
     * @param gameRoot 游戏根目录（即 {@code .min}）
     * @return 版本目录名列表；目录不存在时返回空列表
     */
    public static List<String> scan(Path gameRoot) {
        List<String> out = new ArrayList<>();
        if (gameRoot == null) {
            return out;
        }
        Path versions = gameRoot.resolve("versions");
        if (!Files.isDirectory(versions)) {
            return out;
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(versions)) {
            for (Path p : ds) {
                if (Files.isDirectory(p)) {
                    String n = p.getFileName().toString();
                    // 跳过明显的非版本目录
                    if (n.startsWith(".") || n.equalsIgnoreCase("cache")) {
                        continue;
                    }
                    out.add(n);
                }
            }
        } catch (IOException ignored) {
            return out;
        }
        out.sort(VersionScanner::compareDesc);
        return out;
    }

    /** 版本号从新到旧（1.21 > 1.20.4 > 1.20.1 > 1.9）。无法解析的排最后。 */
    static int compareDesc(String a, String b) {
        int[] pa = parse(a);
        int[] pb = parse(b);
        if (pa == null && pb == null) return a.compareToIgnoreCase(b);
        if (pa == null) return 1;
        if (pb == null) return -1;
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int x = i < pa.length ? pa[i] : 0;
            int y = i < pb.length ? pb[i] : 0;
            if (x != y) return Integer.compare(y, x); // 降序
        }
        return 0;
    }

    /** 解析 "1.20.4" / "1.21" / "1.20.1-forge" 之类的版本号为数字段。 */
    private static int[] parse(String v) {
        if (v == null || v.isEmpty()) return null;
        // 取开头的数字与点号段
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (Character.isDigit(c) || (c == '.' && sb.length() > 0)) {
                sb.append(c);
            } else {
                break;
            }
        }
        String num = sb.toString();
        if (num.isEmpty() || num.equals(".")) return null;
        String[] parts = num.split("\\.");
        try {
            int[] r = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                r[i] = parts[i].isEmpty() ? 0 : Integer.parseInt(parts[i]);
            }
            return r;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

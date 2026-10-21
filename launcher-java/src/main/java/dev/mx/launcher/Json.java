package dev.mx.launcher;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简 JSON 解析/生成（零依赖）。
 *
 * <p>只覆盖登录流程需要的子集：对象、数组、字符串、数字、布尔、null。
 * 不作为通用 JSON 库使用。
 */
final class Json {

    private Json() {
    }

    // ============================================================
    //  解析
    // ============================================================

    public static Object parse(String s) {
        Parser p = new Parser(s);
        p.skipWs();
        Object v = p.value();
        p.skipWs();
        return v;
    }

    /** 解析为对象；非对象时抛异常。 */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String s) {
        Object v = parse(s);
        if (v instanceof Map) {
            return (Map<String, Object>) v;
        }
        throw new IllegalArgumentException("不是 JSON 对象");
    }

    // ============================================================
    //  取值辅助
    // ============================================================

    @SuppressWarnings("unchecked")
    static String str(Map<String, Object> o, String k) {
        Object v = o.get(k);
        return v == null ? null : String.valueOf(v);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> obj(Map<String, Object> o, String k) {
        Object v = o.get(k);
        return (v instanceof Map) ? (Map<String, Object>) v : null;
    }

    @SuppressWarnings("unchecked")
    static List<Object> arr(Map<String, Object> o, String k) {
        Object v = o.get(k);
        return (v instanceof List) ? (List<Object>) v : null;
    }

    static long num(Map<String, Object> o, String k, long def) {
        Object v = o.get(k);
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return def;
        }
    }

    static boolean has(Map<String, Object> o, String k) {
        return o != null && o.containsKey(k) && o.get(k) != null;
    }

    // ============================================================
    //  生成（仅对象/字符串/数字需要）
    // ============================================================

    /** 生成一个简单 JSON 对象，值支持 String / Number / Boolean / List&lt;String&gt; / 嵌套 Map。 */
    public static String write(Object o) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, o);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(StringBuilder sb, Object o) {
        if (o == null) {
            sb.append("null");
        } else if (o instanceof String) {
            writeString(sb, (String) o);
        } else if (o instanceof Number || o instanceof Boolean) {
            sb.append(o);
        } else if (o instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : ((Map<String, Object>) o).entrySet()) {
                if (!first) sb.append(',');
                first = false;
                writeString(sb, e.getKey());
                sb.append(':');
                writeValue(sb, e.getValue());
            }
            sb.append('}');
        } else if (o instanceof List) {
            sb.append('[');
            boolean first = true;
            for (Object v : (List<Object>) o) {
                if (!first) sb.append(',');
                first = false;
                writeValue(sb, v);
            }
            sb.append(']');
        } else {
            writeString(sb, String.valueOf(o));
        }
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    /** 便捷：构造有序 Map。 */
    public static Map<String, Object> map() {
        return new LinkedHashMap<>();
    }

    // ============================================================
    //  递归下降解析器
    // ============================================================

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
        }

        void skipWs() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        Object value() {
            skipWs();
            if (i >= s.length()) throw err("意外结束");
            char c = s.charAt(i);
            switch (c) {
                case '{': return object();
                case '[': return array();
                case '"': return string();
                case 't': expect("true");  return Boolean.TRUE;
                case 'f': expect("false"); return Boolean.FALSE;
                case 'n': expect("null");  return null;
                default:  return number();
            }
        }

        Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++; // {
            skipWs();
            if (peek() == '}') { i++; return m; }
            while (true) {
                skipWs();
                String k = string();
                skipWs();
                if (peek() != ':') throw err("缺少 ':'");
                i++;
                m.put(k, value());
                skipWs();
                char c = peek();
                if (c == ',') { i++; continue; }
                if (c == '}') { i++; return m; }
                throw err("对象格式错误");
            }
        }

        List<Object> array() {
            List<Object> l = new ArrayList<>();
            i++; // [
            skipWs();
            if (peek() == ']') { i++; return l; }
            while (true) {
                l.add(value());
                skipWs();
                char c = peek();
                if (c == ',') { i++; continue; }
                if (c == ']') { i++; return l; }
                throw err("数组格式错误");
            }
        }

        String string() {
            if (peek() != '"') throw err("期望字符串");
            i++;
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c != '\\') { sb.append(c); continue; }
                if (i >= s.length()) break;
                char e = s.charAt(i++);
                switch (e) {
                    case '"':  sb.append('"');  break;
                    case '\\': sb.append('\\'); break;
                    case '/':  sb.append('/');  break;
                    case 'b':  sb.append('\b'); break;
                    case 'f':  sb.append('\f'); break;
                    case 'n':  sb.append('\n'); break;
                    case 'r':  sb.append('\r'); break;
                    case 't':  sb.append('\t'); break;
                    case 'u':
                        if (i + 4 <= s.length()) {
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                        }
                        break;
                    default: sb.append(e);
                }
            }
            throw err("字符串未闭合");
        }

        Object number() {
            int start = i;
            while (i < s.length()) {
                char c = s.charAt(i);
                if (Character.isDigit(c) || c == '-' || c == '+' || c == '.'
                        || c == 'e' || c == 'E') {
                    i++;
                } else {
                    break;
                }
            }
            String n = s.substring(start, i);
            if (n.isEmpty()) throw err("非法数字");
            if (n.contains(".") || n.contains("e") || n.contains("E")) {
                return Double.parseDouble(n);
            }
            try {
                return Long.parseLong(n);
            } catch (NumberFormatException e) {
                return Double.parseDouble(n);
            }
        }

        char peek() {
            if (i >= s.length()) throw err("意外结束");
            return s.charAt(i);
        }

        void expect(String lit) {
            if (!s.startsWith(lit, i)) throw err("期望 " + lit);
            i += lit.length();
        }

        IllegalArgumentException err(String msg) {
            return new IllegalArgumentException("JSON 解析失败(位置 " + i + "): " + msg);
        }
    }
}

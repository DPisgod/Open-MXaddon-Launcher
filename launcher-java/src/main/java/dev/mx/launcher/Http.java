package dev.mx.launcher;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 极简 HTTP 客户端（基于 {@link HttpURLConnection}，无第三方依赖）。
 *
 * <p>启动器只需要「发一个 JSON POST，拿回 JSON」这一件事，
 * 不值得引入 OkHttp / HttpClient 之类的依赖。
 *
 * <p>所有方法都不抛异常：失败返回 {@code null}，由调用方决定怎么提示。
 * 这样界面上不会因为网络抖动就弹一堆堆栈。
 */
public final class Http {

    private Http() {
    }

    /** 响应体 + 状态码。 */
    public static final class Resp {
        public final int code;
        public final String body;

        public Resp(int code, String body) {
            this.code = code;
            this.body = body;
        }

        public boolean ok() {
            return code >= 200 && code < 300;
        }
    }

    /** 发一个 JSON POST。失败（含超时、DNS 失败、连不上）返回 null。 */
    public static Resp postJson(String url, String jsonBody, int timeoutMs) {
        return request("POST", url, jsonBody, timeoutMs);
    }

    /** 发一个 GET。 */
    public static Resp get(String url, int timeoutMs) {
        return request("GET", url, null, timeoutMs);
    }

    /** 通用请求。 */
    public static Resp request(String method, String url,
                               String jsonBody, int timeoutMs) {
        HttpURLConnection conn = null;
        try {
            URL u = new URL(url);
            conn = (HttpURLConnection) u.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", userAgent());
            conn.setRequestProperty("Accept", "application/json");

            if (jsonBody != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type",
                        "application/json; charset=utf-8");
                byte[] data = jsonBody.getBytes(StandardCharsets.UTF_8);
                conn.setFixedLengthStreamingMode(data.length);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(data);
                }
            }

            int code = conn.getResponseCode();
            InputStream in = (code >= 200 && code < 300)
                    ? conn.getInputStream() : conn.getErrorStream();
            String body = "";
            if (in != null) {
                body = readAll(in);
            }
            return new Resp(code, body);
        } catch (Throwable t) {
            return null;
        } finally {
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** 发一个表单 POST（application/x-www-form-urlencoded）。 */
    public static Resp postForm(String url, Map<String, String> form, int timeoutMs) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : form.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(urlEncode(e.getKey())).append('=').append(urlEncode(e.getValue()));
        }
        HttpURLConnection conn = null;
        try {
            URL u = new URL(url);
            conn = (HttpURLConnection) u.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type",
                    "application/x-www-form-urlencoded; charset=utf-8");
            conn.setRequestProperty("User-Agent", userAgent());

            byte[] data = sb.toString().getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(data.length);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(data);
            }

            int code = conn.getResponseCode();
            InputStream in = (code >= 200 && code < 300)
                    ? conn.getInputStream() : conn.getErrorStream();
            String body = in == null ? "" : readAll(in);
            return new Resp(code, body);
        } catch (Throwable t) {
            return null;
        } finally {
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    // ---------------------------------------------------------------- 工具

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int r;
        while ((r = in.read(chunk)) != -1) {
            buf.write(chunk, 0, r);
        }
        return buf.toString("UTF-8");
    }

    private static String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s == null ? "" : s, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private static String userAgent() {
        return "OML-Launcher/" + ApplicationVersion.CURRENT;
    }
}

package dev.mx.launcher;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 微软账号登录（OAuth 2.0 设备码流程）。
 *
 * <p>完整链路：
 *
 * <pre>
 *   1. POST /devicecode            取 user_code + device_code
 *   2. 用户到 microsoft.com/link 输入验证码
 *   3. POST /token (轮询)          换微软访问令牌
 *   4. POST user.auth.xboxlive.com 换 Xbox Live 令牌
 *   5. POST xsts.auth.xboxlive.com 换 XSTS 令牌（含 uhs / xuid）
 *   6. POST api.minecraftservices.com/authentication/login_with_xbox 换 MC 令牌
 *   7. GET  api.minecraftservices.com/minecraft/profile              取 UUID + 玩家名
 * </pre>
 *
 * <p>使用公开的启动器 client_id（Prism Launcher 注册，供第三方启动器接入），
 * 属微软支持的第三方启动器接入方式，无需自建 Azure 应用。
 *
 * <p><b>零依赖</b>：JSON 用工程内的 {@link Json} 处理，不引入第三方库。
 */
public final class MicrosoftLogin {

    /** 公开启动器 client_id（Prism Launcher 注册，供第三方启动器接入）。 */
    private static final String CLIENT_ID = "c36a9fb6-4f2a-41ff-90bd-ae7cc92031eb";

    private static final String SCOPE = "XboxLive.signin offline_access";
    private static final String LINK_URI = "https://www.microsoft.com/link";

    /** 登录结果。 */
    public static final class LoginResult {
        public final Session session;

        LoginResult(Session session) {
            this.session = session;
        }
    }

    private MicrosoftLogin() {
    }

    static void copyDeviceCode(String code){
        if(code==null||!code.matches("[A-Za-z0-9-]{4,20}"))throw new IllegalArgumentException("验证码尚未就绪");
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(code),null);
    }

    /** 弹出登录窗口走完整流程；用户取消时返回 null。 */
    public static LoginResult login(LauncherFrame parent, Session current) {
        DeviceCodeDialog dlg = new DeviceCodeDialog(parent);
        dlg.setVisible(true);
        return dlg.result;
    }

    // ============================================================
    //  登录对话框
    // ============================================================

    static final class DeviceCodeDialog extends JDialog {
        private final JLabel codeLabel = new JLabel("……");
        private final JLabel hintLabel = new JLabel("正在向微软申请验证码…");
        private final JButton openBtn = new UiTheme.Button("打开登录页面","",UiTheme.Tone.PRIMARY);
        private final JButton copyBtn = new UiTheme.Button("复制验证码","",UiTheme.Tone.SURFACE);
        private final JButton offlineBtn = new UiTheme.Button("取消登录","",UiTheme.Tone.SURFACE);
        private final JLabel statusLabel = new JLabel(" ");

        LoginResult result;
        private volatile boolean cancelled;

        DeviceCodeDialog(LauncherFrame parent) {this(parent,true);}
        DeviceCodeDialog(LauncherFrame parent,boolean initialize) {
            super(parent, "登入正版账号", true);
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            buildUi();
            WindowChrome.dialog(this);
            setSize(460, 410);
            setLocationRelativeTo(parent);
            setResizable(false);

            addWindowListener(new java.awt.event.WindowAdapter() {
                @Override public void windowClosing(java.awt.event.WindowEvent e) {
                    cancelled = true;
                }
            });

            if(initialize)start();
        }

        private void buildUi() {
            UiTheme.Surface root = new UiTheme.Surface(new java.awt.BorderLayout(0,20),0);root.background=true;
            root.setBorder(BorderFactory.createEmptyBorder(22, 24, 18, 24));

            root.add(UiTheme.label("使用微软账号登录",22,true),java.awt.BorderLayout.NORTH);
            JPanel body=UiTheme.transparent(null);body.setLayout(new BoxLayout(body,BoxLayout.Y_AXIS));

            hintLabel.setForeground(UiTheme.p().muted());
            hintLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 11));
            hintLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            body.add(hintLabel);
            body.add(Box.createVerticalStrut(16));

            codeLabel.setForeground(UiTheme.p().accent());
            codeLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 30));
            codeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            body.add(codeLabel);
            body.add(Box.createVerticalStrut(20));

            copyBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
            copyBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));copyBtn.setPreferredSize(new Dimension(100,40));
            copyBtn.setEnabled(false);
            copyBtn.addActionListener(e -> {
                try{copyDeviceCode(codeLabel.getText());statusLabel.setText("验证码已复制到剪贴板");}catch(Exception failure){statusLabel.setText("无法复制，请手动复制上方验证码");}
            });
            body.add(copyBtn);
            body.add(Box.createVerticalStrut(8));

            openBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
            openBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));openBtn.setPreferredSize(new Dimension(100,44));openBtn.setEnabled(false);
            openBtn.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 13));
            openBtn.addActionListener(e -> openBrowser());
            body.add(openBtn);
            body.add(Box.createVerticalStrut(14));

            statusLabel.setForeground(UiTheme.p().muted());
            statusLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 11));
            statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            body.add(statusLabel);root.add(body,java.awt.BorderLayout.CENTER);

            offlineBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
            offlineBtn.setPreferredSize(new Dimension(100,38));
            offlineBtn.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 11));
            offlineBtn.addActionListener(e -> {
                cancelled = true;
                dispose();
            });
            root.add(offlineBtn,java.awt.BorderLayout.SOUTH);

            setContentPane(root);
        }

        private static void style(JButton b, Color bg, Color fg) {
            b.setBackground(bg);
            b.setForeground(fg);
            b.setFocusPainted(false);
            b.setBorderPainted(false);
            b.setOpaque(true);
            b.setCursor(new Cursor(Cursor.HAND_CURSOR));
            final Color base = bg;
            b.addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) {
                    if (b.isEnabled()) b.setBackground(base.brighter());
                }
                @Override public void mouseExited(MouseEvent e) { b.setBackground(base); }
            });
        }

        private void openBrowser() {
            try{copyDeviceCode(codeLabel.getText());hintLabel.setText("验证码已复制，在浏览器中粘贴即可");}
            catch(Exception e){hintLabel.setText("未能自动复制，请手动复制上方验证码");}
            try {
                Desktop.getDesktop().browse(URI.create(LINK_URI));
            } catch (Exception e) {
                statusLabel.setText("请手动访问 microsoft.com/link");
            }
        }

        private void start() {
            new SwingWorker<LoginResult, String>() {
                @Override protected LoginResult doInBackground() throws Exception {
                    Map<String, Object> dc = requestDeviceCode();
                    String deviceCode = Json.str(dc, "device_code");
                    String userCode = Json.str(dc, "user_code");
                    int interval = (int) Json.num(dc, "interval", 5);
                    int expiresIn = (int) Json.num(dc, "expires_in", 900);

                    SwingUtilities.invokeLater(() -> {
                        if(cancelled||!isDisplayable())return;
                        codeLabel.setText(userCode);
                        hintLabel.setText("在浏览器打开 microsoft.com/link 输入上方验证码");
                        copyBtn.setEnabled(true);
                        openBtn.setEnabled(true);
                        // 自动打开浏览器，省一步操作
                        openBrowser();
                    });

                    String msToken = pollForToken(deviceCode, interval, expiresIn, this::publish);
                    if (msToken == null) {
                        throw new IllegalStateException("登录超时，请重试");
                    }

                    publish("正在验证 Xbox 账号…");
                    String xbl = xboxLiveAuth(msToken);

                    publish("正在获取 XSTS 授权…");
                    Map<String, Object> xsts = xstsAuth(xbl);
                    if (xsts == null) {
                        return null;
                    }

                    publish("正在登录 Minecraft…");
                    Map<String, Object> mc = minecraftLogin(
                            Json.str(xsts, "uhs"), Json.str(xsts, "token"));

                    publish("正在读取玩家档案…");
                    Map<String, Object> profile = minecraftProfile(Json.str(mc, "access_token"));

                    Session s = Session.premium(
                            Json.str(profile, "name"),
                            Json.str(profile, "id"),
                            Json.str(mc, "access_token"),
                            Json.str(xsts, "xuid"));
                    return new LoginResult(s);
                }

                @Override protected void process(List<String> chunks) {
                    if (!chunks.isEmpty()) {
                        statusLabel.setForeground(new Color(0x84, 0x8F, 0xA0));
                        statusLabel.setText(chunks.get(chunks.size() - 1));
                    }
                }

                @Override protected void done() {
                    try {
                        LoginResult r = get();
                        result = r;
                        dispose();
                    } catch (Exception ex) {
                        if (cancelled) {
                            dispose();
                            return;
                        }
                        Throwable c = ex;
                        while (c.getCause() != null && c.getCause() != c) c = c.getCause();
                        statusLabel.setForeground(new Color(0xE8, 0x6B, 0x6B));
                        statusLabel.setText(c.getMessage() == null ? "登录失败" : c.getMessage());
                        codeLabel.setText("失败");
                        hintLabel.setText("可关闭本窗口重试，或改用离线模式");
                    }
                }
            }.execute();
        }
    }

    // ============================================================
    //  协议实现
    // ============================================================

    private static Map<String, Object> requestDeviceCode() throws IOException {
        String body = "client_id=" + enc(CLIENT_ID) + "&scope=" + enc(SCOPE);
        return postForm("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode", body);
    }

    private static String pollForToken(String deviceCode, int interval, int expiresIn,
                                       Consumer<String> progress)
            throws IOException, InterruptedException {
        long deadline = System.currentTimeMillis() + expiresIn * 1000L;
        int wait = Math.max(1, interval);
        int tick = 0;

        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(wait * 1000L);
            tick += wait;
            progress.accept("等待你在浏览器中完成授权…（已等待 " + tick + "s）");

            String body = "client_id=" + enc(CLIENT_ID)
                    + "&grant_type=" + enc("urn:ietf:params:oauth:grant-type:device_code")
                    + "&device_code=" + enc(deviceCode);

            Map<String, Object> r = postFormAllowError(
                    "https://login.microsoftonline.com/consumers/oauth2/v2.0/token", body);

            if (Json.has(r, "access_token")) {
                return Json.str(r, "access_token");
            }
            String err = Json.str(r, "error");
            if (err == null) err = "";
            switch (err) {
                case "authorization_pending":
                    break;
                case "slow_down":
                    wait += 1;
                    break;
                case "expired_token":
                    throw new IllegalStateException("验证码已过期，请重新登录");
                case "authorization_declined":
                    throw new IllegalStateException("授权被拒绝");
                default:
                    throw new IllegalStateException("登录失败: " + errText(r, err));
            }
        }
        return null;
    }

    private static String errText(Map<String, Object> r, String fallback) {
        String d = Json.str(r, "error_description");
        if (d != null && !d.isBlank()) {
            // 只取首句，避免把整段 trace 展示给用户
            int dot = d.indexOf('.');
            return dot > 0 ? d.substring(0, dot) : d;
        }
        return fallback;
    }

    private static String xboxLiveAuth(String msAccessToken) throws IOException {
        Map<String, Object> props = Json.map();
        props.put("AuthMethod", "RPS");
        props.put("SiteName", "user.auth.xboxlive.com");
        props.put("RpsTicket", "d=" + msAccessToken);

        Map<String, Object> body = Json.map();
        body.put("Properties", props);
        body.put("RelyingParty", "http://auth.xboxlive.com");
        body.put("TokenType", "JWT");

        Map<String, Object> r = postJson("https://user.auth.xboxlive.com/user/authenticate", body);
        return Json.str(r, "Token");
    }

    /** @return 含 token / uhs / xuid 的 Map；XErr 时抛异常 */
    private static Map<String, Object> xstsAuth(String xblToken) throws IOException {
        Map<String, Object> props = Json.map();
        props.put("SandboxId", "RETAIL");
        props.put("UserTokens", List.of(xblToken));

        Map<String, Object> body = Json.map();
        body.put("Properties", props);
        body.put("RelyingParty", "rp://api.minecraftservices.com/");
        body.put("TokenType", "JWT");

        Map<String, Object> r = postJsonAllowError(
                "https://xsts.auth.xboxlive.com/xsts/authorize", body);

        if (Json.has(r, "XErr")) {
            long err = Json.num(r, "XErr", 0);
            String msg;
            if (err == 2148916233L) {
                msg = "该微软账号还没有 Xbox 档案，请先到 xbox.com 创建后再登录";
            } else if (err == 2148916235L) {
                msg = "该账号所在地区不支持 Xbox Live";
            } else if (err == 2148916238L) {
                msg = "未成年账号需要家长同意后才能登录";
            } else {
                msg = "XSTS 授权失败（" + err + "）";
            }
            throw new IllegalStateException(msg);
        }

        Map<String, Object> dc = Json.obj(r, "DisplayClaims");
        List<Object> xui = dc == null ? null : Json.arr(dc, "xui");
        if (xui == null || xui.isEmpty()) {
            throw new IllegalStateException("XSTS 响应缺少 DisplayClaims");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) xui.get(0);

        Map<String, Object> out = Json.map();
        out.put("token", Json.str(r, "Token"));
        out.put("uhs", Json.str(first, "uhs"));
        out.put("xuid", Json.str(first, "xid"));
        return out;
    }

    private static Map<String, Object> minecraftLogin(String uhs, String xstsToken)
            throws IOException {
        Map<String, Object> body = Json.map();
        body.put("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken);
        return postJson("https://api.minecraftservices.com/authentication/login_with_xbox", body);
    }

    private static Map<String, Object> minecraftProfile(String mcAccessToken) throws IOException {
        Map<String, Object> r =
                getJson("https://api.minecraftservices.com/minecraft/profile", mcAccessToken);
        if (!Json.has(r, "name") || !Json.has(r, "id")) {
            throw new IllegalStateException("该账号没有 Minecraft Java 版游戏所有权");
        }
        return r;
    }

    // ============================================================
    //  HTTP 工具
    // ============================================================

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static Map<String, Object> postForm(String url, String body) throws IOException {
        return postFormAllowError(url, body);
    }

    private static Map<String, Object> postFormAllowError(String url, String body)
            throws IOException {
        HttpURLConnection c = open(url);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        c.setRequestProperty("Accept", "application/json");
        try (OutputStream os = c.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        return readJson(c);
    }

    private static Map<String, Object> postJson(String url, Map<String, Object> body)
            throws IOException {
        return postJsonAllowError(url, body);
    }

    private static Map<String, Object> postJsonAllowError(String url, Map<String, Object> body)
            throws IOException {
        HttpURLConnection c = open(url);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Accept", "application/json");
        try (OutputStream os = c.getOutputStream()) {
            os.write(Json.write(body).getBytes(StandardCharsets.UTF_8));
        }
        return readJson(c);
    }

    private static Map<String, Object> getJson(String url, String bearer) throws IOException {
        HttpURLConnection c = open(url);
        c.setRequestMethod("GET");
        c.setRequestProperty("Accept", "application/json");
        if (bearer != null) {
            c.setRequestProperty("Authorization", "Bearer " + bearer);
        }
        return readJson(c);
    }

    private static HttpURLConnection open(String url) throws IOException {
        URL u = URI.create(url).toURL();
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", "OML-Launcher/1.0");
        c.setInstanceFollowRedirects(true);
        return c;
    }

    private static Map<String, Object> readJson(HttpURLConnection c) throws IOException {
        int code = c.getResponseCode();
        InputStream in = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
        String text = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        try {
            return Json.parseObject(text);
        } catch (Exception e) {
            throw new IOException("服务端返回异常 (HTTP " + code + "): "
                    + (text.length() > 200 ? text.substring(0, 200) : text));
        }
    }
}

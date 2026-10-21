package dev.mx.launcher;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;

/**
 * 设置面板（模态对话框）。
 *
 * <p>可调项：
 * <ul>
 *   <li>内存分配（-Xmx）</li>
 *   <li>附加 JVM 参数</li>
 *   <li>Java 路径（留空 = 自动查找）</li>
 *   <li>离线玩家名</li>
 *   <li>清空皮肤缓存</li>
 * </ul>
 */
public final class SettingsDialog extends JDialog {





    private final Path home;
    private final Path gameRoot;

    private final MemoryPanel memoryPanel;
    private final JTextField jvmArgsField;
    private final JTextField javaPathField;
    private final JTextField offlineNameField;
    private final JLabel cacheLabel = new JLabel(" ");

    private boolean changed;

    public SettingsDialog(java.awt.Window owner, Path home, Path gameRoot) {
        super(owner, "设置", ModalityType.APPLICATION_MODAL);
        this.home = home;
        this.gameRoot = gameRoot;

        Settings st = Settings.load(home);

        memoryPanel = new MemoryPanel(false,new MemoryAllocation.Choice(st.memoryMode,st.memoryMb),()->Settings.load(home));
        jvmArgsField = new JTextField(st.jvmArgs == null ? "" : st.jvmArgs);
        javaPathField = new JTextField(st.javaPath == null ? "" : st.javaPath);
        offlineNameField = new JTextField(st.offlineName == null ? "" : st.offlineName);

        buildUi();
        WindowChrome.dialog(this);
        setSize(660, 620);
        setLocationRelativeTo(owner);
        setResizable(false);
    }

    private static int clampMemory(int v) {
        if (v < 1024) return 1024;
        if (v > 32768) return 32768;
        return v;
    }

    /** 是否有改动（用于外部刷新）。 */
    public boolean changed() {
        return changed;
    }

    private void buildUi() {
        JPanel root = new UiTheme.Surface(new BorderLayout(),0);
        root.setBackground(UiTheme.p().background());
        root.setBorder(BorderFactory.createEmptyBorder(18, 22, 16, 22));

        JLabel title = new JLabel("设置 · MXCloud " + ApplicationVersion.CURRENT);
        title.setForeground(UiTheme.p().text());
        title.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 16));
        root.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setBackground(UiTheme.p().background());
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));

        // --- 内存 ---
        form.add(label("游戏内存分配"));
        memoryPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        memoryPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE,200));
        form.add(memoryPanel);
        form.add(Box.createVerticalStrut(12));

        // --- JVM 参数 ---
        form.add(label("附加 JVM 参数"));
        jvmArgsField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        jvmArgsField.setBackground(UiTheme.p().surface());
        jvmArgsField.setForeground(UiTheme.p().text());
        jvmArgsField.setCaretColor(UiTheme.p().text());
        jvmArgsField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x2A, 0x2F, 0x3A)),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        jvmArgsField.setFont(new Font("Consolas", Font.PLAIN, 12));
        jvmArgsField.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(jvmArgsField);
        JLabel jvmTip = tip("例如：-XX:+UseG1GC -Dfile.encoding=UTF-8");
        form.add(jvmTip);
        form.add(Box.createVerticalStrut(12));

        // --- Java 路径 ---
        form.add(label("Java 路径（留空则自动查找）"));
        JPanel javaRow = new JPanel(new BorderLayout(6, 0));
        javaRow.setOpaque(false);
        javaPathField.setBackground(UiTheme.p().surface());
        javaPathField.setForeground(UiTheme.p().text());
        javaPathField.setCaretColor(UiTheme.p().text());
        javaPathField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x2A, 0x2F, 0x3A)),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        javaPathField.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 12));
        javaRow.add(javaPathField, BorderLayout.CENTER);

        JButton browse = new UiTheme.Button("浏览…","",UiTheme.Tone.SURFACE);
        browse.setBackground(UiTheme.p().surface());
        browse.setForeground(UiTheme.p().text());
        browse.setFocusPainted(false);
        browse.setBorderPainted(false);
        browse.setOpaque(true);
        browse.setCursor(new Cursor(Cursor.HAND_CURSOR));
        browse.setPreferredSize(new Dimension(72, 28));
        browse.addActionListener(e -> pickJava());
        javaRow.add(browse, BorderLayout.EAST);
        javaRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        javaRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(javaRow);
        form.add(Box.createVerticalStrut(12));

        // --- 离线玩家名 ---
        form.add(label("离线玩家名"));
        offlineNameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        offlineNameField.setBackground(UiTheme.p().surface());
        offlineNameField.setForeground(UiTheme.p().text());
        offlineNameField.setCaretColor(UiTheme.p().text());
        offlineNameField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x2A, 0x2F, 0x3A)),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        offlineNameField.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 12));
        offlineNameField.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(offlineNameField);
        form.add(Box.createVerticalStrut(12));

        // --- 缓存 ---
        JPanel cacheRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
        cacheRow.setOpaque(false);
        JButton clearCache = new UiTheme.Button("清空皮肤缓存","",UiTheme.Tone.SURFACE);
        clearCache.setBackground(UiTheme.p().surface());
        clearCache.setForeground(UiTheme.p().text());
        clearCache.setFocusPainted(false);
        clearCache.setBorderPainted(false);
        clearCache.setOpaque(true);
        clearCache.setCursor(new Cursor(Cursor.HAND_CURSOR));
        clearCache.setPreferredSize(new Dimension(120, 28));
        clearCache.addActionListener(e -> clearSkinCache());
        cacheRow.add(clearCache);
        cacheLabel.setForeground(UiTheme.p().muted());
        cacheLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 11));
        cacheRow.add(cacheLabel);
        cacheRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(cacheRow);

        root.add(form, BorderLayout.CENTER);

        // --- 底部按钮 ---
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        bottom.setOpaque(false);

        JButton reset = new UiTheme.Button("恢复默认","",UiTheme.Tone.SURFACE);
        styleGhost(reset);
        reset.addActionListener(e -> {
            memoryPanel.select(new MemoryAllocation.Choice("auto",4096));
            jvmArgsField.setText("");
            javaPathField.setText("");
            offlineNameField.setText("");
        });
        bottom.add(reset);

        JButton cancel = new UiTheme.Button("取消","",UiTheme.Tone.SURFACE);
        styleGhost(cancel);
        cancel.addActionListener(e -> dispose());
        bottom.add(cancel);

        JButton save = new UiTheme.Button("保存","",UiTheme.Tone.PRIMARY);
        stylePrimary(save);
        save.addActionListener(e -> onSave());
        bottom.add(save);

        root.add(bottom, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private JLabel label(String s) {
        JLabel l = new JLabel(s);
        l.setForeground(UiTheme.p().muted());
        l.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 11));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private JLabel tip(String s) {
        JLabel l = new JLabel(s);
        l.setForeground(new Color(0x5A, 0x63, 0x70));
        l.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 10));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(BorderFactory.createEmptyBorder(4, 2, 0, 0));
        return l;
    }

    private static void styleGhost(JButton b) {
        b.setBackground(UiTheme.p().surface());
        b.setForeground(UiTheme.p().text());
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setOpaque(true);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(88, 30));
        final Color base = UiTheme.p().surface();
        b.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { b.setBackground(base.brighter()); }
            @Override public void mouseExited(MouseEvent e) { b.setBackground(base); }
        });
    }

    private static void stylePrimary(JButton b) {
        b.setBackground(UiTheme.p().accent());
        b.setForeground(new Color(0x0B, 0x14, 0x10));
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setOpaque(true);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(88, 30));
        b.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 12));
        final Color base = UiTheme.p().accent();
        b.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { b.setBackground(base.brighter()); }
            @Override public void mouseExited(MouseEvent e) { b.setBackground(base); }
        });
    }

    private void pickJava() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("选择 java.exe");
        fc.setFileFilter(new FileNameExtensionFilter("java.exe", "exe"));
        if (!javaPathField.getText().isBlank()) {
            try {
                fc.setCurrentDirectory(new java.io.File(javaPathField.getText()).getParentFile());
            } catch (Exception ignored) {
            }
        }
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            javaPathField.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    private void clearSkinCache() {
        java.nio.file.Path dir = home.resolve("skins");
        int n = 0;
        try (var ds = java.nio.file.Files.newDirectoryStream(dir, "*.png")) {
            for (java.nio.file.Path p : ds) {
                java.nio.file.Files.deleteIfExists(p);
                n++;
            }
        } catch (Exception ignored) {
        }
        cacheLabel.setText("已清空 " + n + " 个缓存文件");
    }

    private void onSave() {
        Settings st = new Settings();
        st.memoryMb = memoryPanel.choice().mb();
        st.memoryMode = memoryPanel.choice().mode();
        st.jvmArgs = jvmArgsField.getText().trim();
        st.javaPath = javaPathField.getText().trim();
        st.offlineName = offlineNameField.getText().trim();
        if(!st.offlineName.isEmpty())try{st.offlineName=Session.offlineName(st.offlineName);}catch(IllegalArgumentException e){WindowChrome.prompt(this,"离线名称无效",e.getMessage(),false);return;}
        Settings old = Settings.load(home);
        st.lastVersion = old.lastVersion;
        st.save(home);

        // 离线名变了且当前是离线会话 -> 立即生效
        Session cur = SessionStore.load(home);
        if (cur != null && !cur.premium && !st.offlineName.isEmpty()) {
            Session s = Session.offline(Session.offlineName(st.offlineName));
            SessionStore.save(home, s);
        }

        changed = true;
        dispose();
    }
}

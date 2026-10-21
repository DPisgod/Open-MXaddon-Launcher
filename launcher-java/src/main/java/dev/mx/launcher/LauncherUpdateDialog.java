package dev.mx.launcher;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Checks once per application startup; all network and file I/O stays off the EDT. */
final class LauncherUpdateDialog extends JDialog {
    @FunctionalInterface interface InstallerLauncher { void launch(Path installer) throws Exception; }
    private final JLabel status = UiTheme.muted("选择立即更新后，将从 GitHub 下载并打开安装向导。", 12);
    private final JProgressBar progress = new UiTheme.ProgressBar();
    private final UiTheme.Button update = new UiTheme.Button("立即更新", "download", UiTheme.Tone.PRIMARY);
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private SwingWorker<Path,Long> download;

    static void checkOnStartup(LauncherFrame owner) {
        GitHubUpdater updater = new GitHubUpdater();
        new SwingWorker<GitHubUpdater.Release,Void>() {
            @Override protected GitHubUpdater.Release doInBackground() throws Exception { return updater.latest(ApplicationVersion.CURRENT); }
            @Override protected void done() {
                if (!owner.isDisplayable()) return;
                try {
                    GitHubUpdater.Release release = get();
                    if (release != null) new LauncherUpdateDialog(owner, release, updater, installer -> {
                        GitHubUpdater.launchInstaller(installer, owner.homeDir());
                        owner.dispose();
                        System.exit(0);
                    }).setVisible(true);
                } catch (Exception failure) {
                    System.err.println("[launcher-update] " + InstallVersionDialog.installationFailure(failure).getMessage());
                }
            }
        }.execute();
    }

    LauncherUpdateDialog(Window owner, GitHubUpdater.Release release, GitHubUpdater updater, InstallerLauncher launcher) {
        super(owner, "发现启动器新版本", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        UiTheme.Surface root = new UiTheme.Surface(new BorderLayout(0, 20), 0);
        root.setBorder(new EmptyBorder(24, 28, 24, 28));
        JPanel text = UiTheme.transparent(new GridLayout(0, 1, 0, 9));
        text.add(UiTheme.label("发现启动器新版本", 22, true));
        text.add(UiTheme.label("当前版本 " + ApplicationVersion.CURRENT + "  →  最新版本 " + release.version(), 14, false));
        text.add(UiTheme.muted("安装向导将使用当前目录，保留游戏、账号和配置。", 12));
        text.add(UiTheme.muted("安装向导打开后，启动器会退出。取消则继续使用当前版本。", 12));
        root.add(text, BorderLayout.NORTH);
        JPanel state = UiTheme.transparent(new BorderLayout(0, 12));
        state.add(status, BorderLayout.NORTH); progress.setMaximum(1000); progress.setVisible(false); state.add(progress, BorderLayout.SOUTH);
        root.add(state, BorderLayout.CENTER);
        JPanel actions = UiTheme.transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        UiTheme.Button cancel = new UiTheme.Button("取消", "", UiTheme.Tone.SURFACE);
        cancel.addActionListener(e -> dispose()); actions.add(cancel); actions.add(update); root.add(actions, BorderLayout.SOUTH);
        update.addActionListener(e -> {
            update.setEnabled(false); cancelled.set(false); progress.setVisible(true); progress.setValue(0); status.setText("正在连接 GitHub 下载安装包…");
            download = new SwingWorker<>() {
                @Override protected Path doInBackground() throws Exception {
                    long[] last = {0};
                    return updater.download(release, cancelled::get, bytes -> {
                        long now = System.nanoTime();
                        if (bytes == release.size() || now - last[0] > 100_000_000L) { last[0] = now; publish(bytes); }
                    });
                }
                @Override protected void process(List<Long> chunks) {
                    if (cancelled.get()) return;
                    long bytes = chunks.get(chunks.size() - 1);
                    progress.setValue((int) (bytes * 1000 / release.size()));
                    status.setText(String.format("正在下载更新：%.1f / %.1f MB", bytes / 1048576.0, release.size() / 1048576.0));
                }
                @Override protected void done() {
                    try {
                        Path installer = get();
                        if (cancelled.get() || !isDisplayable()) { Files.deleteIfExists(installer); Files.deleteIfExists(installer.getParent()); return; }
                        status.setText("下载和校验完成，正在打开安装向导…");
                        launcher.launch(installer);
                        dispose();
                    } catch (Exception failure) {
                        if (cancelled.get()) return;
                        status.setText("更新未完成，可重试或取消。");
                        status.setToolTipText(InstallVersionDialog.installationFailure(failure).getMessage());
                        update.setEnabled(true); update.setText("重试更新");
                        WindowChrome.prompt(LauncherUpdateDialog.this, "更新未完成", InstallVersionDialog.installationFailure(failure).getMessage(), false);
                    }
                }
            };
            download.execute();
        });
        addWindowListener(new WindowAdapter() { @Override public void windowClosing(WindowEvent e) { dispose(); } });
        setContentPane(root); WindowChrome.dialog(this); setResizable(false); pack(); setLocationRelativeTo(owner);
    }

    @Override public void dispose() {
        cancelled.set(true);
        // Let the worker finish its cleanup; cancelling SwingWorker would discard a completed download's path.
        super.dispose();
    }
}

package dev.mx.launcher;

import java.nio.file.*;

/** Opt-in release smoke test: run with the bundled runtime and expected latest version; never executes setup. */
public final class GitHubReleaseLiveTest {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Supply the expected latest GitHub Release version");
        GitHubUpdater updater = new GitHubUpdater();
        GitHubUpdater.Release current = updater.latest(ApplicationVersion.CURRENT);
        boolean newer = GitHubUpdater.compareVersions(args[0], ApplicationVersion.CURRENT) > 0;
        if (newer != (current != null)) throw new AssertionError("Incorrect startup update decision");
        GitHubUpdater.Release release = updater.latest("0.0.0");
        if (release == null || !args[0].equals(release.version()) || release.sha256().isEmpty())
            throw new AssertionError("Latest GitHub release metadata is incorrect");
        Path installer = updater.download(release, () -> false, bytes -> {});
        try {
            if (Files.size(installer) != release.size()) throw new AssertionError("Wrong published installer size");
            System.out.println("LIVE_GITHUB_RELEASE_DOWNLOAD_OK " + release.version() + " " + Files.size(installer));
        } finally { Files.deleteIfExists(installer); Files.deleteIfExists(installer.getParent()); }
    }
}

package dev.mx.launcher;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Opt-in real publisher integration test. Downloads to the supplied disposable directory, never uses saved accounts. */
public final class DownloadsLiveTest {
    public static void main(String[] args)throws Exception{
        Path home=Path.of(args[0]).toAbsolutePath();Files.createDirectories(home);LauncherPaths.override(home);
        DownloadCatalog catalog=new DownloadCatalog();var games=catalog.games();
        if(args.length<4||!args[3].equals("install-only")){
        for(String type:List.of("release","snapshot","old_beta","old_alpha"))if(games.stream().noneMatch(game->game.type().equals(type)))throw new AssertionError("Missing official version type "+type);
        System.out.println("OFFICIAL_GAME_CATALOG_OK versions="+games.size());
        for(var loader:DownloadCatalog.Loader.values())if(loader!=DownloadCatalog.Loader.VANILLA){var versions=catalog.loaders(loader,"1.21.1");if(versions.isEmpty())throw new AssertionError("Missing loader "+loader);System.out.println("OFFICIAL_LOADER_CATALOG_OK "+loader+" "+versions.get(0).version());}
        ContentCatalog content=new ContentCatalog("");
        Path root=LauncherPaths.gameRoot();VersionInstaller downloads=new VersionInstaller(root,p->{});
        for(var kind:ContentCatalog.Kind.values()){
            var page=content.search(ContentCatalog.Source.MODRINTH,kind,"","1.21.1",kind==ContentCatalog.Kind.MOD?"fabric":"",0);
            if(page.projects().isEmpty())throw new AssertionError("Empty content catalog "+kind);
            var project=page.projects().get(0);var versions=content.versions(project,"1.21.1",kind==ContentCatalog.Kind.MOD?"fabric":"",0);
            if(versions.isEmpty())throw new AssertionError("Empty content versions "+kind);
            var file=versions.get(0);if(file.size()>100_000_000L){System.out.println("CONTENT_METADATA_OK "+kind+" "+project.title());continue;}
            Path downloaded=content.download(file,home.resolve("content").resolve(kind.folder),downloads);
            if(kind==ContentCatalog.Kind.PACK)ModpackInstaller.inspect(downloaded,"Live-Pack");
            System.out.println("LIVE_CONTENT_DOWNLOAD_OK "+kind+" "+downloaded.getFileName()+" "+Files.size(downloaded));
        }
        if(args.length>1&&args[1].equals("catalog"))return;
        }
        Path root=LauncherPaths.gameRoot();
        AtomicLong last=new AtomicLong();VersionInstaller installer=new VersionInstaller(root,p->{long now=System.nanoTime();if(now-last.get()>3_000_000_000L||p.total()==0||p.completed()==p.total()){last.set(now);System.out.println(p.message());}});
        String minecraft=args.length>1?args[1]:"b1.7.3";DownloadCatalog.Loader loader=args.length>2?DownloadCatalog.Loader.valueOf(args[2]):DownloadCatalog.Loader.VANILLA;
        String loaderVersion=loader==DownloadCatalog.Loader.VANILLA?"":catalog.loaders(loader,minecraft).get(0).version();
        var options=new DownloadCatalog.Options(minecraft,loader,loaderVersion,loader==DownloadCatalog.Loader.FABRIC,true,"");
        String id=Files.isDirectory(root.resolve("versions").resolve(options.id()))?options.id():installer.install(options);
        Path instance=VersionInstaller.gameDirectory(root,id);var command=GameLauncher.command(instance,id,Session.offline("IntegrationTest"),2048);
        if(command.stream().anyMatch(value->value.contains("${")))throw new AssertionError("Unexpanded metadata arguments");
        System.out.println("LIVE_INSTALL_AND_COMMAND_OK "+id+" java="+command.get(0)+" instance="+instance);
    }
}

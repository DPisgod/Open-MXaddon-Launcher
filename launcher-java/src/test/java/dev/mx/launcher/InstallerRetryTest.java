package dev.mx.launcher;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.*;

/** Deterministic network fault injection; no publisher connection or user data needed. */
public final class InstallerRetryTest {
    private static final byte[] CONTENT="verified dependency contents".getBytes(StandardCharsets.UTF_8);
    private static final String URL="https://downloads.example.test/dependency.jar";
    private static final AtomicInteger passed=new AtomicInteger();
    @FunctionalInterface private interface Input {InputStream open()throws IOException;}
    private static final class Transport implements VersionInstaller.ConnectionFactory {
        final ArrayDeque<Input> responses=new ArrayDeque<>();
        int opened,disconnected;
        Transport(Input... responses){this.responses.addAll(List.of(responses));}
        @Override public URLConnection open(String url)throws IOException{
            opened++;Input next=responses.poll();if(next==null)throw new AssertionError("unexpected network request");
            return new HttpURLConnection(URI.create(url).toURL()){
                @Override public int getResponseCode(){return 200;}
                @Override public InputStream getInputStream()throws IOException{return next.open();}
                @Override public void connect(){}
                @Override public void disconnect(){disconnected++;}
                @Override public boolean usingProxy(){return false;}
            };
        }
    }
    private static Input good(){return ()->new ByteArrayInputStream(CONTENT);}
    private static Input connectTimeout(){return ()->{throw new SocketTimeoutException("Connect timed out");};}
    private static Input readTimeout(){return ()->new InputStream(){
        int calls;
        @Override public int read()throws IOException{if(calls++==0)return CONTENT[0];throw new SocketTimeoutException("Read timed out");}
    };}
    private static VersionInstaller.Download task(Path root,String target)throws Exception{
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(CONTENT));
        return new VersionInstaller.Download(URL,root.resolve(target),"SHA-1",hash,CONTENT.length);
    }
    private static VersionInstaller installer(Path root,Transport transport)throws IOException{
        Files.createDirectories(root.resolve(".mx-downloads"));return new VersionInstaller(root,p->{},transport);
    }
    private static void require(boolean result,String message){if(!result)throw new AssertionError(message);}
    private static void done(String name){passed.incrementAndGet();System.out.println("PASS "+name);}
    private static void noPartial(Path root)throws IOException{try(var files=Files.list(root.resolve(".mx-downloads"))){require(files.noneMatch(p->p.toString().endsWith(".part")),"partial download leaked");}}
    public static void main(String[] args)throws Exception{
        Path base=Files.createTempDirectory(Path.of(args[0]),"faults-");
        {
            Path root=base.resolve("recover");Transport transport=new Transport(connectTimeout(),readTimeout(),good());
            VersionInstaller installer=installer(root,transport);var first=task(root,"mods/first.jar");installer.obtain(first);
            require(Arrays.equals(Files.readAllBytes(first.target()),CONTENT),"recovered file differs");
            require(transport.opened==3&&transport.disconnected==3,"timeouts must retry and close connections");
            installer.obtain(task(root,"mods/second.jar"));require(transport.opened==3,"verified cache redownloaded");noPartial(root);
            done("connect/read timeouts recover on third attempt; verified cache reused");
        }
        {
            Path root=base.resolve("exhausted");Transport transport=new Transport(connectTimeout(),connectTimeout(),connectTimeout());
            VersionInstaller installer=installer(root,transport);IOException failed=null;
            try{installer.obtain(task(root,"mods/dependency.jar"));}catch(IOException e){failed=e;}
            require(failed!=null&&failed.getMessage().contains("downloads.example.test / dependency.jar")&&failed.getMessage().contains("3 次"),"failed file/site/retry count missing");
            require(failed.getCause() instanceof SocketTimeoutException&&transport.opened==3,"timeout lost or retries unbounded");
            require(InstallVersionDialog.installationFailure(new ExecutionException(new ExecutionException(failed)))==failed,"UI discards download context");
            require(!Files.exists(task(root,"mods/dependency.jar").target()),"failed file published");noPartial(root);
            done("three failed attempts preserve domain, filename and timeout cause in UI");
        }
        {
            Path root=base.resolve("metadata");Transport transport=new Transport(connectTimeout(),good());VersionInstaller installer=installer(root,transport);
            require(Arrays.equals(installer.readUrl("https://downloads.example.test/manifest.json",1024),CONTENT),"metadata retry failed");
            require(transport.opened==2&&transport.disconnected==2,"metadata retry/cleanup missing");done("metadata requests also recover from timeout");
        }
        {
            Path root=base.resolve("checksum");Transport transport=new Transport(()->new ByteArrayInputStream(new byte[CONTENT.length]),good());VersionInstaller installer=installer(root,transport);
            installer.obtain(task(root,"mods/dependency.jar"));require(transport.opened==2,"corrupt content was accepted");noPartial(root);done("hash mismatch retries and only verified content is published");
        }
        {
            Path root=base.resolve("cancel");Files.createDirectories(root.resolve(".mx-downloads"));AtomicReference<VersionInstaller> ref=new AtomicReference<>();
            Transport transport=new Transport(good(),connectTimeout(),good());
            VersionInstaller installer=new VersionInstaller(root,p->{if(p.notice())ref.get().cancel();},transport);ref.set(installer);
            installer.obtain(task(root,"mods/retained.jar"));
            var other=new VersionInstaller.Download(URL,root.resolve("mods/not-published.jar"),"SHA-1","0000000000000000000000000000000000000000",CONTENT.length);
            try{installer.obtain(other);throw new AssertionError("cancel ignored");}catch(InterruptedIOException expected){require(!(expected instanceof SocketTimeoutException),"cancel confused with timeout");}
            require(transport.opened==2&&Files.isRegularFile(root.resolve("mods/retained.jar")),"cancel retried or removed verified files");noPartial(root);done("cancel stops retries and retains earlier verified downloads");
        }
        {
            Path root=base.resolve("interrupt");Transport transport=new Transport(()->{throw new InterruptedIOException("interrupted");},good());VersionInstaller installer=installer(root,transport);
            try{installer.obtain(task(root,"mods/dependency.jar"));throw new AssertionError("interrupt ignored");}catch(InterruptedIOException expected){}
            require(transport.opened==1,"genuine interruption retried");noPartial(root);done("genuine interrupted I/O is not retried");
        }
        {
            Path root=base.resolve("https");Transport transport=new Transport(good());VersionInstaller installer=installer(root,transport);
            try{installer.readUrl("http://downloads.example.test/manifest.json",1024);throw new AssertionError("insecure source accepted");}catch(IOException expected){}
            require(transport.opened==0,"insecure source connected");done("HTTPS enforcement retained");
        }
        System.out.println("INSTALLER_RETRY_TESTS_OK cases="+passed.get()+" evidence="+base);
    }
}

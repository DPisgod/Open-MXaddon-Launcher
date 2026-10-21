package dev.mx.launcher;

import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.HexFormat;

/** Per-Windows-user ownership, independent of installation directory. */
final class SingleInstance implements AutoCloseable {
    private final FileChannel channel;
    private final FileLock lock;
    private final ServerSocket server;
    private final String token;
    private volatile Runnable activation;
    private volatile boolean pending;

    private SingleInstance(FileChannel channel, FileLock lock, Path endpoint) throws IOException {
        this.channel=channel;this.lock=lock;
        server=new ServerSocket();server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),0));
        byte[] random=new byte[32];new SecureRandom().nextBytes(random);token=HexFormat.of().formatHex(random);
        Files.writeString(endpoint,server.getLocalPort()+"\n"+token+"\n"+ProcessHandle.current().pid(),StandardCharsets.US_ASCII);
        Thread.ofVirtual().name("mxcloud-instance").start(()->{
            while(!server.isClosed())try(Socket socket=server.accept()){
                socket.setSoTimeout(1500);
                byte[] message=socket.getInputStream().readNBytes(65);
                if((token+"\n").equals(new String(message,StandardCharsets.US_ASCII))){
                    pending=true;dispatch();socket.getOutputStream().write(1);
                }
            }catch(IOException ignored){}
        });
    }
    static Path directory(){
        String local=System.getenv("LOCALAPPDATA");
        Path base=Path.of(local==null?System.getProperty("user.home"):local);
        // Share the existing lock with older installations to prevent concurrent writes.
        Path legacy=base.resolve("MXCloud").resolve("Instance");
        return Files.isDirectory(legacy)?legacy:base.resolve("OML").resolve("Instance");
    }
    /** null means the existing process acknowledged activation. Never start a second UI on IPC failure. */
    static SingleInstance acquire(Path directory) throws IOException {
        Files.createDirectories(directory);Path endpoint=directory.resolve("endpoint");
        FileChannel channel=FileChannel.open(directory.resolve("owner.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
        try{
            for(int attempt=0;attempt<50;attempt++){
                FileLock lock=null;try{lock=channel.tryLock();}catch(OverlappingFileLockException ignored){}
                if(lock!=null){
                    try{return new SingleInstance(channel,lock,endpoint);}catch(IOException failure){lock.release();throw failure;}
                }
                try{
                    String[] values=Files.readString(endpoint,StandardCharsets.US_ASCII).split("\n");
                    if(values.length!=3||!values[1].matches("[0-9a-f]{64}"))throw new IOException("Invalid endpoint");
                    // The newly launched process grants the owner foreground activation rights.
                    // Only restore ICONIFIED; never use the native maximize transition.
                    if(System.getProperty("os.name","").startsWith("Windows"))try{Foreground.INSTANCE.AllowSetForegroundWindow(Integer.parseInt(values[2]));}catch(Throwable ignored){}
                    try(Socket socket=new Socket()){
                        socket.connect(new InetSocketAddress("127.0.0.1",Integer.parseInt(values[0])),500);socket.setSoTimeout(1000);
                        socket.getOutputStream().write((values[1]+"\n").getBytes(StandardCharsets.US_ASCII));
                        if(socket.getInputStream().read()==1){channel.close();return null;}
                    }
                }catch(IOException|IllegalArgumentException ignored){}
                try{Thread.sleep(100);}catch(InterruptedException stopped){Thread.currentThread().interrupt();throw new IOException(stopped);}
            }
            throw new IOException("已检测到运行中的 OML，但暂时无法唤起窗口。请稍后重试。");
        }catch(IOException failure){channel.close();throw failure;}
    }
    private interface Foreground extends com.sun.jna.win32.StdCallLibrary{
        Foreground INSTANCE=com.sun.jna.Native.load("user32",Foreground.class);
        boolean AllowSetForegroundWindow(int processId);
    }
    synchronized void onActivation(Runnable action){activation=action;dispatch();}
    private synchronized void dispatch(){if(pending&&activation!=null){pending=false;javax.swing.SwingUtilities.invokeLater(activation);}}
    @Override public void close(){try{server.close();}catch(IOException ignored){}try{lock.release();}catch(IOException ignored){}try{channel.close();}catch(IOException ignored){}}
}

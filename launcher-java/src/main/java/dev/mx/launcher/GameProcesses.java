package dev.mx.launcher;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/** Only process handles returned by this launcher's successful launches are owned. */
final class GameProcesses {
    private final Map<Long,ProcessHandle> clients=new LinkedHashMap<>();
    synchronized void add(long pid){if(pid==ProcessHandle.current().pid())throw new IllegalArgumentException("不能将启动器自身作为游戏");ProcessHandle.of(pid).filter(ProcessHandle::isAlive).ifPresent(handle->clients.put(pid,handle));}
    synchronized int count(){clients.values().removeIf(handle->!handle.isAlive());return clients.size();}
    boolean running(){return count()>0;}
    void stopAll()throws Exception{
        List<ProcessHandle> owned;synchronized(this){owned=List.copyOf(clients.values());}
        List<ProcessHandle> targets=new ArrayList<>();for(ProcessHandle root:owned){if(!root.isAlive())continue;try(var descendants=root.descendants()){targets.addAll(descendants.toList());}targets.add(root);}
        List<CompletableFuture<ProcessHandle>> exits=new ArrayList<>();
        for(ProcessHandle handle:targets){if(handle.pid()==ProcessHandle.current().pid()||!handle.isAlive())continue;exits.add(handle.onExit());if(!handle.destroyForcibly()&&handle.isAlive())throw new IOException("无法结束部分游戏进程，请检查权限");}
        CompletableFuture.allOf(exits.toArray(CompletableFuture[]::new)).get(5,TimeUnit.SECONDS);count();
    }
}

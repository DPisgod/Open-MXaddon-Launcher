package dev.mx.launcher;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

/** Removes a version from selection, preserving its complete directory for recovery. */
final class VersionManager {
    static Path remove(Path gameRoot,String version)throws IOException{
        Path root=gameRoot.toRealPath(),versions=root.resolve("versions").toRealPath(),source=versions.resolve(version).normalize();
        if(version.isBlank()||!versions.equals(source.getParent())||Files.isSymbolicLink(source)||!Files.isDirectory(source,LinkOption.NOFOLLOW_LINKS)||!versions.equals(source.toRealPath().getParent()))throw new IOException("只能删除当前游戏目录中的版本文件夹");
        if(!versions.getParent().equals(root))throw new IOException("版本目录位置无效");
        Path trash=root.resolve(".mx-version-trash");Files.createDirectories(trash);if(!trash.toRealPath().equals(trash))throw new IOException("版本回收目录位置无效");
        Path recovery=Files.createDirectory(trash.resolve(UUID.randomUUID().toString())),target=recovery.resolve(source.getFileName());
        try{return Files.move(source,target);}catch(IOException e){Files.deleteIfExists(recovery);throw e;}
    }
}

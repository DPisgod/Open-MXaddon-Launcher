package dev.mx.launcher;

import java.io.InputStream;
import java.util.Properties;

/** Shared release version, also consumed by the native shell and installer. */
final class ApplicationVersion {
    static final String CURRENT=load();
    private static String load(){
        try(InputStream input=ApplicationVersion.class.getResourceAsStream("/mxcloud-version.properties")){
            if(input==null)throw new IllegalStateException("缺少启动器版本信息");
            Properties values=new Properties();values.load(input);
            String version=values.getProperty("version","").trim();
            if(!version.matches("\\d+\\.\\d+\\.\\d+"))throw new IllegalStateException("启动器版本信息无效");
            return version;
        }catch(java.io.IOException failure){throw new ExceptionInInitializerError(failure);}
    }
}

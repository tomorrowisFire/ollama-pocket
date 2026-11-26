package com.ollamapocket.app;

import java.io.File;
import java.io.IOException;

/** Cheap resident-weight identity and bounded startup hints, not a cryptographic file check. */
final class EngineLoadPolicy {
    private EngineLoadPolicy(){}
    static String weightKey(File file)throws IOException{
        if(file==null||!file.isFile()||file.length()<=0)throw new IOException("Missing weights");
        return file.getCanonicalPath()+"|"+file.length()+"|"+file.lastModified();
    }
    static long gpuStartupMs(long weightBytes){
        long mib=Math.max(0,weightBytes)/(1024L*1024L);
        return 35000L+Math.min(145000L,mib*1000L/32L);
    }
    static int hostThreads(int cores){return Math.max(1,Math.min(4,cores/2));}
}

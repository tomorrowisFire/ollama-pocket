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

    /** Process-local automatic GPU retry budget: monotonic clock, bounded history, exact weights key.
     * A failed startup is not a permanent capability verdict or a performance measurement. */
    static final class GpuRetryBudget {
        static final int MAX_ENTRIES=16;
        static final long RESET_MS=900_000L, MAX_DELAY_MS=300_000L;
        private static final class Failure {
            final long at;final int count;
            Failure(long at,int count){this.at=at;this.count=count;}
            long delay(){return Math.min(MAX_DELAY_MS,60_000L<<(count-1));}
        }
        private final java.util.LinkedHashMap<String,Failure> failures=new java.util.LinkedHashMap<>(MAX_ENTRIES,0.75f,true);
        synchronized long remainingMs(String key,long now,boolean automatic){
            if(!automatic||key==null||now<0)return 0;
            Failure f=failures.get(key);
            if(f==null)return 0;
            if(now<f.at||now-f.at>=RESET_MS){failures.remove(key);return 0;}
            return Math.max(0L,f.delay()-(now-f.at));
        }
        synchronized void failed(String key,long now,boolean eligible){
            if(!eligible||key==null||key.isEmpty()||now<0)return;
            Failure previous=failures.get(key);
            int count=previous!=null&&now>=previous.at&&now-previous.at<RESET_MS?Math.min(4,previous.count+1):1;
            failures.put(key,new Failure(now,count));
            while(failures.size()>MAX_ENTRIES)failures.remove(failures.keySet().iterator().next());
        }
        synchronized void succeeded(String key){if(key!=null)failures.remove(key);}
    }
}

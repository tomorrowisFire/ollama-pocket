package com.ollamapocket.app;

import java.io.IOException;
import java.util.function.BooleanSupplier;

/** One loaded embedded voice engine per process, including canceled work still unwinding. */
final class VoiceNativeWork {
    private static Lease active;
    private VoiceNativeWork() {}

    static Lease acquire(BooleanSupplier cancelled) throws IOException {
        return acquire(cancelled,30000L);
    }

    static synchronized Lease acquire(BooleanSupplier cancelled,long timeoutMillis) throws IOException {
        if(cancelled==null||timeoutMillis<0||timeoutMillis>30000L)throw new IllegalArgumentException("voice work bound");
        long until=System.nanoTime()+timeoutMillis*1000000L;
        while(active!=null){
            if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new IOException("voice work canceled");
            long remaining=until-System.nanoTime();
            if(remaining<=0)throw new IOException("voice engine busy");
            try{VoiceNativeWork.class.wait(Math.min(50L,Math.max(1L,remaining/1000000L)));}
            catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("voice work canceled",e);}
        }
        if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new IOException("voice work canceled");
        active=new Lease();return active;
    }

    static final class Lease implements AutoCloseable {
        private boolean closed;
        private Lease() {}
        @Override public void close(){
            synchronized(VoiceNativeWork.class){
                if(closed)return;
                closed=true;
                if(active==this){active=null;VoiceNativeWork.class.notifyAll();}
            }
        }
    }
}

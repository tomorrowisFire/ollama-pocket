package com.ollamapocket.app;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** Bounded, cancellable readiness gate for a user-approved local model transfer. */
final class LocalServerReadiness {
    static final long WAIT_MS=30_000, POLL_MS=200;
    static final int PROBE_MS=1000;
    interface Probe {boolean ready(int timeout)throws Exception;}
    interface Sleeper {void sleep(long millis)throws InterruptedException;}
    private LocalServerReadiness(){}
    private static void check(BooleanSupplier cancelled)throws InterruptedIOException{
        if(Thread.currentThread().isInterrupted()||cancelled.getAsBoolean())throw new InterruptedIOException("CHAT_CANCELLED");
    }
    private static long remaining(long start,LongSupplier clock)throws IOException{
        long elapsed=clock.getAsLong()-start;
        if(elapsed<0||elapsed>=WAIT_MS)throw new IOException("LOCAL_SERVER_NOT_READY");
        return WAIT_MS-elapsed;
    }
    static void await(BooleanSupplier alive,Probe probe,BooleanSupplier cancelled,LongSupplier clock,Sleeper sleep)throws IOException{
        long start=clock.getAsLong();
        for(;;){
            check(cancelled);
            remaining(start,clock);
            if(alive.getAsBoolean()){
                check(cancelled);
                long left=remaining(start,clock);
                boolean ready=false;
                try{ready=probe.ready((int)Math.min(PROBE_MS,left));}
                catch(InterruptedException e){Thread.currentThread().interrupt();check(cancelled);}
                catch(Exception unavailable){/* Bounded retry for a daemon still starting. */}
                check(cancelled);
                remaining(start,clock);
                if(ready&&alive.getAsBoolean()){check(cancelled);return;}
            }
            try{sleep.sleep(Math.min(POLL_MS,remaining(start,clock)));}
            catch(InterruptedException e){Thread.currentThread().interrupt();check(cancelled);}
        }
    }
}

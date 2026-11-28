package com.ollamapocket.app;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Latest-query ownership with bounded workers and pending jobs. */
final class ModelSearchWorkers implements AutoCloseable {
    private final ThreadPoolExecutor pool=new ThreadPoolExecutor(2,2,0L,TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(2));
    private Future<?> ollama,hf;
    private boolean closed;
    synchronized void replace(Runnable nextOllama,Runnable nextHf){
        if(closed)return;
        cancel();
        if(nextOllama!=null)ollama=pool.submit(nextOllama);
        if(nextHf!=null)hf=pool.submit(nextHf);
    }
    synchronized void cancel(){
        if(ollama!=null)ollama.cancel(true);
        if(hf!=null)hf.cancel(true);
        ollama=null;hf=null;
        pool.purge();
    }
    @Override public synchronized void close(){
        if(closed)return;
        closed=true;cancel();pool.shutdownNow();
    }
    int queued(){return pool.getQueue().size();}
    boolean awaitTermination(long millis)throws InterruptedException{
        return pool.awaitTermination(millis,TimeUnit.MILLISECONDS);
    }
}

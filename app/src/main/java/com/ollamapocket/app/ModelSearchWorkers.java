package com.ollamapocket.app;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Latest-query ownership with bounded workers and pending jobs. */
final class ModelSearchWorkers implements AutoCloseable {
    // Isolate providers: a slow/cancellation-resistant request must not occupy
    // the other provider's worker. Each lane retains only its latest queued job.
    private static ThreadPoolExecutor lane(){
        return new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(1));
    }
    private final ThreadPoolExecutor ollamaLane=lane(),hfLane=lane();
    private Future<?> ollama,hf;
    private boolean closed;
    synchronized void replace(Runnable nextOllama,Runnable nextHf){
        if(closed)return;
        cancel();
        if(nextOllama!=null)ollama=ollamaLane.submit(nextOllama);
        if(nextHf!=null)hf=hfLane.submit(nextHf);
    }
    synchronized void cancel(){
        if(ollama!=null)ollama.cancel(true);
        if(hf!=null)hf.cancel(true);
        ollama=null;hf=null;
        ollamaLane.purge();hfLane.purge();
    }
    @Override public synchronized void close(){
        if(closed)return;
        closed=true;cancel();ollamaLane.shutdownNow();hfLane.shutdownNow();
    }
    int queued(){return ollamaLane.getQueue().size()+hfLane.getQueue().size();}
    boolean awaitTermination(long millis)throws InterruptedException{
        long budget=TimeUnit.MILLISECONDS.toNanos(Math.max(0L,millis)),start=System.nanoTime();
        boolean first=ollamaLane.awaitTermination(budget,TimeUnit.NANOSECONDS);
        long remaining=Math.max(0L,budget-Math.max(0L,System.nanoTime()-start));
        boolean second=hfLane.awaitTermination(remaining,TimeUnit.NANOSECONDS);
        return first&&second;
    }
}

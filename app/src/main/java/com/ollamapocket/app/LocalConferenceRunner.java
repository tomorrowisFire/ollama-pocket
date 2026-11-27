package com.ollamapocket.app;

import android.content.Context;
import android.os.SystemClock;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.json.JSONArray;
import org.json.JSONObject;

/** Foreground-only sequential local CPU requests. No workflow checkpoint or tool access. */
final class LocalConferenceRunner {
    enum Stop { USER, DEADLINE, THERMAL, MEMORY, SERVER, COOP, INTERRUPTED }
    private final Context context;
    private final AtomicBoolean used=new AtomicBoolean();
    private final AtomicReference<Stop> reason=new AtomicReference<>();
    private volatile HttpURLConnection connection;
    private volatile long deadline,elapsedMillis;
    LocalConferenceRunner(Context context){this.context=context.getApplicationContext();}
    Stop reason(){return reason.get();}
    long elapsedMillis(){return elapsedMillis;}
    void cancel(){
        if(reason.compareAndSet(null,Stop.USER)){
            Thread closer=new Thread(this::disconnect,"onhand-conference-close");
            closer.setDaemon(true);closer.start();
        }
    }
    private void disconnect(){
        HttpURLConnection active=connection;
        if(active!=null)active.disconnect();
    }
    private boolean stopped(){
        if(reason.get()!=null)return true;
        Stop stop=null;
        if(Thread.currentThread().isInterrupted())stop=Stop.INTERRUPTED;
        else if(deadline>0&&SystemClock.elapsedRealtime()>=deadline)stop=Stop.DEADLINE;
        else{
            InferenceSafetyPolicy.Reason risk=InferenceSafety.check(context);
            if(risk==InferenceSafetyPolicy.Reason.THERMAL)stop=Stop.THERMAL;
            else if(risk==InferenceSafetyPolicy.Reason.LOW_MEMORY)stop=Stop.MEMORY;
            else if(!OllamaForegroundService.isRunning())stop=Stop.SERVER;
            else if(!"off".equals(CoopSession.get(context).role()))stop=Stop.COOP;
        }
        if(stop!=null)reason.compareAndSet(null,stop);
        return reason.get()!=null;
    }
    private void check()throws IOException{
        if(stopped())throw new IOException("Conference stopped");
    }
    ConferencePolicy.Result run(ConferencePolicy.Request request,Consumer<ConferencePolicy.Result> progress){
        if(!used.compareAndSet(false,true))throw new IllegalStateException("Conference already used");
        long started=SystemClock.elapsedRealtime();
        deadline=started+ConferencePolicy.MAX_MILLIS;
        ScheduledExecutorService watchdog=Executors.newSingleThreadScheduledExecutor(r->{
            Thread t=new Thread(r,"onhand-conference-safety");t.setDaemon(true);return t;
        });
        try{
            watchdog.scheduleAtFixedRate(()->{if(stopped())disconnect();},0,250,TimeUnit.MILLISECONDS);
            if(!stopped()){GpuRunner.stop();DistributedRunner.stop();}
            return ConferencePolicy.run(request,call->generate(request.model,call),this::stopped,progress);
        }finally{
            elapsedMillis=Math.max(0,SystemClock.elapsedRealtime()-started);
            watchdog.shutdownNow();disconnect();connection=null;
            if(InferenceSafety.blocked(context))try{
                OllamaHttp.post("/api/generate",new JSONObject().put("model",request.model).put("keep_alive",0).put("stream",false),"POST");
            }catch(Exception ignored){}
        }
    }
    private ConferencePolicy.Reply generate(String model,ConferencePolicy.Call call)throws Exception{
        check();
        JSONObject body=OllamaHttp.chatBody(model,new JSONArray().put(new JSONObject().put("role","user").put("content",call.prompt)));
        if(call.maxTokens<256||call.maxTokens>ConferencePolicy.MAX_TOKENS||
            !DocumentBatchPolicy.fits(call.prompt,call.maxTokens))throw new IOException("Conference context limit");
        body.getJSONObject("options").put("num_predict",call.maxTokens);
        HttpURLConnection c=OllamaHttp.newChatConnection();connection=c;
        try{
            long remaining=Math.max(1,deadline-SystemClock.elapsedRealtime());
            c.setConnectTimeout((int)Math.min(3000L,remaining));c.setReadTimeout((int)Math.min(60_000L,remaining));
            StructuredTextStream response=new StructuredTextStream(ConferencePolicy.MAX_REPLY,call.maxTokens);
            check();
            OllamaHttp.streamChat(c,body,item->{
                check();response.accept(item);
            },()->reason.get()!=null);
            check();
            return new ConferencePolicy.Reply(response.text(),response.truncated());
        }finally{connection=null;c.disconnect();}
    }
}

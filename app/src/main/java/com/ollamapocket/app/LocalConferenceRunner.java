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
    private volatile long deadline;
    LocalConferenceRunner(Context context){this.context=context.getApplicationContext();}
    Stop reason(){return reason.get();}
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
        deadline=SystemClock.elapsedRealtime()+ConferencePolicy.MAX_MILLIS;
        ScheduledExecutorService watchdog=Executors.newSingleThreadScheduledExecutor(r->{
            Thread t=new Thread(r,"onhand-conference-safety");t.setDaemon(true);return t;
        });
        try{
            watchdog.scheduleAtFixedRate(()->{if(stopped())disconnect();},0,250,TimeUnit.MILLISECONDS);
            if(!stopped()){GpuRunner.stop();DistributedRunner.stop();}
            return ConferencePolicy.run(request,call->generate(request.model,call),this::stopped,progress);
        }finally{
            watchdog.shutdownNow();disconnect();connection=null;
            if(InferenceSafety.blocked(context))try{
                OllamaHttp.post("/api/generate",new JSONObject().put("model",request.model).put("keep_alive",0).put("stream",false),"POST");
            }catch(Exception ignored){}
        }
    }
    private ConferencePolicy.Reply generate(String model,ConferencePolicy.Call call)throws Exception{
        check();
        JSONObject body=OllamaHttp.chatBody(model,new JSONArray().put(new JSONObject().put("role","user").put("content",call.prompt)));
        body.getJSONObject("options").put("num_predict",ConferencePolicy.MAX_TOKENS);
        HttpURLConnection c=OllamaHttp.newChatConnection();connection=c;
        try{
            long remaining=Math.max(1,deadline-SystemClock.elapsedRealtime());
            c.setConnectTimeout((int)Math.min(3000L,remaining));c.setReadTimeout((int)Math.min(60_000L,remaining));
            StringBuilder output=new StringBuilder();
            boolean[] terminal={false},truncated={false};
            check();
            OllamaHttp.streamChat(c,body,item->{
                check();
                JSONObject message=item.optJSONObject("message");
                if(message!=null){
                    Object value=message.opt("content");
                    if(value!=null&&value!=JSONObject.NULL){
                        if(!(value instanceof String))throw new IOException("Invalid conference output");
                        String part=(String)value;
                        if((long)output.length()+part.length()>ConferencePolicy.MAX_REPLY)throw new IOException("Conference output limit");
                        output.append(part);
                    }
                }
                if(Boolean.TRUE.equals(item.opt("done"))){
                    terminal[0]=true;
                    String why=item.optString("done_reason","");
                    truncated[0]=!why.isEmpty()&&!"stop".equals(why);
                    Object raw=item.opt("eval_count");
                    if(raw!=null&&raw!=JSONObject.NULL){
                        if(!(raw instanceof Number))throw new IOException("Invalid token count");
                        double n=((Number)raw).doubleValue();
                        if(!Double.isFinite(n)||n<0||n!=Math.floor(n)||n>ConferencePolicy.MAX_TOKENS)
                            throw new IOException("Conference token limit");
                    }
                }
            });
            check();
            if(!terminal[0])throw new IOException("Incomplete conference stream");
            return new ConferencePolicy.Reply(output.toString(),truncated[0]);
        }finally{connection=null;c.disconnect();}
    }
}

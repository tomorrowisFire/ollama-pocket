package com.ollamapocket.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** One private local checkpoint; no external tools, cloud, chat history or background job. */
final class LocalWorkflow {
    private final Context context;
    private final SharedPreferences store;
    private volatile HttpURLConnection connection;
    private volatile boolean paused,cancelled;
    LocalWorkflow(Context c){context=c.getApplicationContext();store=context.getSharedPreferences("onhand_workflow_v1",Context.MODE_PRIVATE);}
    static final class Checkpoint {
        String plan,model,id,status;
        long started,reportedTokens;
        int attempts,totalAttempts,unknownAttempts,format=2;
        boolean legacyStats;
        final List<String> results=new ArrayList<>();
        List<WorkflowPolicy.Step> steps(){return WorkflowPolicy.parse(plan,format);}
        boolean complete(){return results.size()==steps().size();}
        boolean resumable(){return !complete()&&!"CANCELLED".equals(status)&&WorkflowPolicy.canAttempt(attempts,System.currentTimeMillis(),started);}
    }
    Checkpoint load()throws Exception{
        String raw=store.getString("checkpoint",null);
        if(raw==null)return null;
        if(raw.length()>180000)throw new IOException("Checkpoint limit");
        JSONObject j=new JSONObject(raw);int schema=j.getInt("schema");
        if(schema!=1&&schema!=2)throw new IOException("Checkpoint schema");
        if(schema==2&&!LocalChatLibrary.digest(raw).equals(store.getString("checkpoint_digest","")))throw new IOException("Checkpoint checksum mismatch");
        Checkpoint c=new Checkpoint();c.plan=j.getString("plan");c.model=j.getString("model");c.id=j.getString("id");
        c.started=j.getLong("started");c.attempts=j.getInt("attempts");c.status=j.getString("status");
        c.format=schema==1?1:j.getInt("format");c.legacyStats=schema==1||j.optBoolean("legacyStats",false);
        c.totalAttempts=j.optInt("totalAttempts",0);c.unknownAttempts=j.optInt("unknownAttempts",0);c.reportedTokens=j.optLong("reportedTokens",0);
        if(c.model.isEmpty()||c.model.length()>200||c.id.isEmpty()||c.id.length()>64||c.started<=0||c.attempts<0||c.attempts>WorkflowPolicy.MAX_ATTEMPTS||
           c.totalAttempts<0||c.totalAttempts>30||c.unknownAttempts<0||c.unknownAttempts>c.totalAttempts||c.reportedTokens<0||
           c.reportedTokens>(long)(c.totalAttempts-c.unknownAttempts)*WorkflowPolicy.MAX_TOKENS||
           !java.util.Arrays.asList("QUEUED","RUNNING","VERIFYING","RETRY_WAIT","PAUSED","BLOCKED","COMPLETED","CANCELLED").contains(c.status))throw new IOException("Invalid checkpoint");
        List<WorkflowPolicy.Step> steps=c.steps();JSONArray a=j.getJSONArray("results");
        if(a.length()>steps.size())throw new IOException("Invalid result count");
        for(int i=0;i<a.length();i++){String v=a.getString(i);if(!WorkflowPolicy.verified(steps.get(i),v))throw new IOException("Invalid checkpoint evidence");c.results.add(v);}
        if("COMPLETED".equals(c.status)!=c.complete())throw new IOException("Invalid completion");
        if(schema==2&&!c.legacyStats&&(c.totalAttempts<c.results.size()+c.attempts||c.totalAttempts>c.results.size()*WorkflowPolicy.MAX_ATTEMPTS+c.attempts))throw new IOException("Invalid attempt count");
        return c;
    }
    private void save(Checkpoint c)throws Exception{
        JSONObject j=new JSONObject().put("schema",2).put("format",c.format).put("plan",c.plan).put("model",c.model).put("id",c.id)
            .put("started",c.started).put("attempts",c.attempts).put("status",c.status).put("results",new JSONArray(c.results))
            .put("totalAttempts",c.totalAttempts).put("unknownAttempts",c.unknownAttempts).put("reportedTokens",c.reportedTokens).put("legacyStats",c.legacyStats);
        String raw=j.toString();
        if(!store.edit().putString("checkpoint",raw).putString("checkpoint_digest",LocalChatLibrary.digest(raw)).commit())throw new IOException("Checkpoint write failed");
    }
    Checkpoint create(String plan,String model)throws Exception{
        WorkflowPolicy.parse(plan);
        if(model==null||model.isEmpty()||model.length()>200)throw new IOException("Model required");
        Checkpoint c=new Checkpoint();c.plan=plan;c.model=model;c.id=java.util.UUID.randomUUID().toString();
        c.started=System.currentTimeMillis();c.status="QUEUED";save(c);return c;
    }
    void clear()throws Exception{if(!store.edit().remove("checkpoint").remove("checkpoint_digest").commit())throw new IOException("Checkpoint removal failed");}
    void prepare(){paused=false;cancelled=false;}
    void pause(){paused=true;disconnect();}
    void cancel(){cancelled=true;disconnect();}
    void cancelSaved(Checkpoint c)throws Exception{if(c!=null&&!c.complete()){c.status="CANCELLED";save(c);}}
    private void disconnect(){HttpURLConnection c=connection;if(c!=null)c.disconnect();}
    Checkpoint run(Checkpoint c,Consumer<Checkpoint> progress,BooleanSupplier stop)throws Exception{
        long initialWall=System.currentTimeMillis(),initialMono=SystemClock.elapsedRealtime();
        BooleanSupplier expired=()->System.currentTimeMillis()<c.started||
            System.currentTimeMillis()-c.started>=WorkflowPolicy.MAX_MILLIS||
            SystemClock.elapsedRealtime()-initialMono>=Math.max(0,WorkflowPolicy.MAX_MILLIS-(initialWall-c.started));
        AtomicReference<String> abort=new AtomicReference<>();
        Runnable watch=()->{
            String state=cancelled?"CANCELLED":paused||stop.getAsBoolean()?"PAUSED":
                expired.getAsBoolean()||InferenceSafety.blocked(context)||!OllamaForegroundService.isRunning()||
                !"off".equals(CoopSession.get(context).role())?"BLOCKED":null;
            if(state!=null&&abort.compareAndSet(null,state))disconnect();
        };
        List<WorkflowPolicy.Step> steps=c.steps();
        ScheduledExecutorService watchdog=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"onhand-workflow-safety");t.setDaemon(true);return t;});
        try{
            if(!c.resumable())return c;
            watchdog.scheduleAtFixedRate(watch,0,1,TimeUnit.SECONDS);
            GpuRunner.stop();DistributedRunner.stop();
            while(!c.complete()){
                watch.run();
                String stopped=cancelled?"CANCELLED":abort.get();
                if(stopped!=null||Thread.currentThread().isInterrupted()){c.status=stopped==null?"PAUSED":stopped;save(c);break;}
                if(!WorkflowPolicy.canAttempt(c.attempts,System.currentTimeMillis(),c.started)||c.totalAttempts>=30){c.status="BLOCKED";save(c);break;}
                int index=c.results.size();WorkflowPolicy.Step step=steps.get(index);
                String previous=index==0?"":c.results.get(index-1);
                boolean retry=c.attempts>0;
                c.attempts++;c.totalAttempts++;c.unknownAttempts++;c.status="RUNNING";save(c);progress.accept(c);
                StringBuilder output=new StringBuilder();long[] tokens={-1};boolean[] truncated={false};
                HttpURLConnection request=null;
                try{
                    watch.run();if(abort.get()!=null||cancelled||Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException();
                    JSONArray messages=new JSONArray().put(new JSONObject().put("role","user").put("content",WorkflowPolicy.prompt(step,previous,retry)));
                    JSONObject body=OllamaHttp.chatBody(c.model,messages);
                    body.getJSONObject("options").put("num_predict",WorkflowPolicy.MAX_TOKENS);
                    request=OllamaHttp.newChatConnection();request.setReadTimeout(60_000);connection=request;
                    watch.run();if(abort.get()!=null||cancelled)throw new java.io.InterruptedIOException();
                    OllamaHttp.streamChat(request,body,item->{
                        if(abort.get()!=null||cancelled||Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException();
                        JSONObject message=item.optJSONObject("message");
                        if(message!=null){String part=message.optString("content","");
                            if((long)output.length()+part.length()>WorkflowPolicy.MAX_OUTPUT)throw new IOException("Output limit");output.append(part);}
                        if(item.optBoolean("done",false)){
                            Object raw=item.opt("eval_count");
                            if(raw instanceof Number){
                                double d=((Number)raw).doubleValue();
                                if(!Double.isFinite(d)||d<0||d!=Math.floor(d)||d>WorkflowPolicy.MAX_TOKENS)throw new IOException("Invalid output token count");
                                tokens[0]=(long)d;
                            }
                            truncated[0]="length".equals(item.optString("done_reason"));
                        }
                    });
                }catch(Exception e){
                    watch.run();String reason=cancelled?"CANCELLED":abort.get();
                    c.status=reason!=null?reason:Thread.currentThread().isInterrupted()?"PAUSED":"BLOCKED";
                    save(c);break; // never blindly retry a transport failure
                }finally{connection=null;if(request!=null)request.disconnect();}
                if(tokens[0]>=0){c.unknownAttempts--;c.reportedTokens+=tokens[0];}
                watch.run();String reason=cancelled?"CANCELLED":abort.get();
                if(reason!=null||Thread.currentThread().isInterrupted()){c.status=reason==null?"PAUSED":reason;save(c);break;}
                c.status="VERIFYING";save(c);
                if(!truncated[0]&&WorkflowPolicy.verified(step,output.toString())){
                    c.results.add(output.toString());c.attempts=0;c.status=c.complete()?"COMPLETED":"QUEUED";save(c);progress.accept(c);
                }else{
                    c.status=c.attempts>=WorkflowPolicy.MAX_ATTEMPTS?"BLOCKED":"RETRY_WAIT";save(c);progress.accept(c);
                    if("BLOCKED".equals(c.status))break;
                }
            }
            return c;
        }finally{
            watchdog.shutdownNow();disconnect();connection=null;
            if(InferenceSafety.blocked(context))try{OllamaHttp.post("/api/generate",new JSONObject().put("model",c.model).put("keep_alive",0).put("stream",false),"POST");}catch(Exception ignored){}
        }
    }
}

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
        long started,updated,reportedTokens;
        int attempts,totalAttempts,unknownAttempts,format=2;
        boolean legacyStats;
        WorkflowRunJournal.Reason reason=WorkflowRunJournal.Reason.UNKNOWN;
        WorkflowRunJournal journal=new WorkflowRunJournal();
        final List<String> results=new ArrayList<>();
        List<WorkflowPolicy.Step> steps(){return WorkflowPolicy.parse(plan,format);}
        boolean complete(){return results.size()==steps().size();}
        WorkflowPolicy.Budget budget(long now){return WorkflowPolicy.budget(status,steps().size(),results.size(),attempts,totalAttempts,started,updated,now);}
        boolean resumable(){return WorkflowRunJournal.resumableReason(reason)&&budget(System.currentTimeMillis()).allowed();}
    }
    private Checkpoint readCurrent()throws Exception{
        String raw=store.getString("checkpoint",null);
        return raw==null?null:decode(raw,store.getString("checkpoint_digest",""));
    }
    Checkpoint load()throws Exception{
        Checkpoint c=readCurrent();
        if(c==null)return null;
        if(WorkflowRunJournal.interruptedState(c.status)){
            c.status="PAUSED";c.reason=WorkflowRunJournal.Reason.RECOVERED;save(c);
        }
        return c;
    }
    String reviewSnapshot(Checkpoint c)throws Exception{return encode(c);}
    Checkpoint reviewedResume(String reviewed,String draft,String model)throws Exception{
        Checkpoint fresh=readCurrent(); // do not write recovery state during confirmation
        if(fresh==null||WorkflowRunJournal.interruptedState(fresh.status)||
            !WorkflowPolicy.reviewedResume(reviewed,encode(fresh),fresh.plan,draft,fresh.model,model)||!fresh.resumable())
            return null;
        return fresh;
    }
    private static Checkpoint decode(String raw,String digest)throws Exception{
        if(raw.length()>WorkflowLibrary.MAX_SNAPSHOT)throw new IOException("Checkpoint limit");
        JSONObject j=new JSONObject(raw);int schema=j.getInt("schema");
        if(schema!=1&&schema!=2&&schema!=3)throw new IOException("Checkpoint schema");
        if(schema>=2&&!LocalChatLibrary.digest(raw).equals(digest))throw new IOException("Checkpoint checksum mismatch");
        Checkpoint c=new Checkpoint();c.plan=j.getString("plan");c.model=j.getString("model");c.id=j.getString("id");
        c.started=j.getLong("started");c.updated=j.optLong("updated",0);c.attempts=j.getInt("attempts");c.status=j.getString("status");
        c.format=schema==1?1:j.getInt("format");c.legacyStats=schema==1||j.optBoolean("legacyStats",false);
        c.totalAttempts=j.optInt("totalAttempts",0);c.unknownAttempts=j.optInt("unknownAttempts",0);c.reportedTokens=j.optLong("reportedTokens",0);
        if(c.model.isEmpty()||c.model.length()>200||c.id.isEmpty()||c.id.length()>64||c.started<=0||c.updated<0||(c.updated>0&&c.updated<c.started)||c.attempts<0||c.attempts>WorkflowPolicy.MAX_ATTEMPTS||
           c.totalAttempts<0||c.totalAttempts>WorkflowPolicy.MAX_TOTAL_ATTEMPTS||c.unknownAttempts<0||c.unknownAttempts>c.totalAttempts||c.reportedTokens<0||
           c.reportedTokens>(long)(c.totalAttempts-c.unknownAttempts)*WorkflowPolicy.MAX_TOKENS||
           !java.util.Arrays.asList("QUEUED","RUNNING","VERIFYING","RETRY_WAIT","PAUSED","BLOCKED","COMPLETED","CANCELLED").contains(c.status))throw new IOException("Invalid checkpoint");
        List<WorkflowPolicy.Step> steps=c.steps();JSONArray a=j.getJSONArray("results");
        if(a.length()>steps.size())throw new IOException("Invalid result count");
        for(int i=0;i<a.length();i++){String v=a.getString(i);if(!WorkflowPolicy.verified(steps.get(i),v))throw new IOException("Invalid checkpoint evidence");c.results.add(v);}
        if("COMPLETED".equals(c.status)!=c.complete())throw new IOException("Invalid completion");
        if(schema>=2&&!c.legacyStats&&(c.totalAttempts<c.results.size()+c.attempts||c.totalAttempts>c.results.size()*WorkflowPolicy.MAX_ATTEMPTS+c.attempts))throw new IOException("Invalid attempt count");
        if(schema==3){
            c.reason=WorkflowRunJournal.Reason.valueOf(j.getString("reason"));
            c.journal=WorkflowRunJournal.read(j.getJSONArray("journal"),steps.size(),c.started);
            if(!c.journal.matchesLast(c.status,c.reason,c.results.size(),c.attempts,c.totalAttempts,c.updated))
                throw new IOException("Invalid journal state");
        }
        return c;
    }
    private static String encode(Checkpoint c)throws Exception{
        String raw=new JSONObject().put("schema",3).put("reason",c.reason.name()).put("journal",c.journal.json()).put("format",c.format).put("plan",c.plan).put("model",c.model).put("id",c.id)
            .put("started",c.started).put("updated",c.updated).put("attempts",c.attempts).put("status",c.status).put("results",new JSONArray(c.results))
            .put("totalAttempts",c.totalAttempts).put("unknownAttempts",c.unknownAttempts).put("reportedTokens",c.reportedTokens).put("legacyStats",c.legacyStats).toString();
        if(raw.length()>WorkflowLibrary.MAX_SNAPSHOT)throw new IOException("Checkpoint limit");
        return raw;
    }
    private void save(Checkpoint c)throws Exception{
        c.updated=Math.max(c.started,Math.max(c.updated,System.currentTimeMillis()));
        c.journal.record(c.updated,c.status,c.reason,c.results.size(),c.attempts,c.totalAttempts,c.steps().size(),c.started);
        String raw=encode(c);
        if(!store.edit().putString("checkpoint",raw).putString("checkpoint_digest",LocalChatLibrary.digest(raw)).commit())throw new IOException("Checkpoint write failed");
    }
    List<WorkflowLibrary.Template> templates()throws Exception{return WorkflowLibrary.templates(store.getString("templates",null));}
    void saveTemplate(String name,String plan)throws Exception{
        String raw=WorkflowLibrary.saveTemplate(store.getString("templates",null),java.util.UUID.randomUUID().toString(),name,plan,System.currentTimeMillis());
        if(!store.edit().putString("templates",raw).commit())throw new IOException("Template write failed");
    }
    void deleteTemplate(String id)throws Exception{
        if(!store.edit().putString("templates",WorkflowLibrary.deleteTemplate(store.getString("templates",null),id)).commit())throw new IOException("Template write failed");
    }
    static final class ArchivedRun {
        final Checkpoint checkpoint;final long archived;
        ArchivedRun(Checkpoint c,long archived){checkpoint=c;this.archived=archived;}
    }
    List<ArchivedRun> history()throws Exception{
        List<ArchivedRun> result=new ArrayList<>();
        for(WorkflowLibrary.Archive a:WorkflowLibrary.history(store.getString("history",null)))
            result.add(new ArchivedRun(decode(a.snapshot,a.digest),a.archived));
        return result;
    }
    void deleteHistory(String id)throws Exception{
        if(!store.edit().putString("history",WorkflowLibrary.deleteHistory(store.getString("history",null),id)).commit())throw new IOException("History write failed");
    }
    void clearHistory()throws Exception{if(!store.edit().remove("history").commit())throw new IOException("History removal failed");}
    Checkpoint create(String plan,String model)throws Exception{
        if(!DocumentBatchPolicy.fits(WorkflowPolicy.firstPrompt(plan),WorkflowPolicy.MAX_TOKENS))
            throw new IOException("Initial workflow context limit");
        if(model==null||model.isEmpty()||model.length()>200)throw new IOException("Model required");
        Checkpoint c=new Checkpoint();c.plan=plan;c.model=model;c.id=java.util.UUID.randomUUID().toString();
        c.started=System.currentTimeMillis();c.updated=c.started;c.status="QUEUED";c.reason=WorkflowRunJournal.Reason.NONE;
        c.journal.record(c.updated,c.status,c.reason,0,0,0,c.steps().size(),c.started);
        Checkpoint prior=load();String raw=encode(c);
        SharedPreferences.Editor transaction=store.edit().putString("checkpoint",raw).putString("checkpoint_digest",LocalChatLibrary.digest(raw));
        if(prior!=null)transaction.putString("history",WorkflowLibrary.archive(store.getString("history",null),encode(prior),c.started));
        if(!transaction.commit())throw new IOException("Checkpoint/archive write failed");return c;
    }
    void clear()throws Exception{if(!store.edit().remove("checkpoint").remove("checkpoint_digest").commit())throw new IOException("Checkpoint removal failed");}
    void prepare(){paused=false;cancelled=false;}
    void pause(){paused=true;disconnect();}
    void cancel(){cancelled=true;disconnect();}
    void cancelSaved(Checkpoint c)throws Exception{if(c!=null&&!c.complete()&&!"CANCELLED".equals(c.status)){c.status="CANCELLED";c.reason=WorkflowRunJournal.Reason.USER_CANCEL;save(c);}}
    private void disconnect(){HttpURLConnection c=connection;if(c!=null)c.disconnect();}
    private void stopped(Checkpoint c,WorkflowRunJournal.Reason reason)throws Exception{c.status=WorkflowRunJournal.stopState(reason).name();c.reason=reason;save(c);}
    Checkpoint run(Checkpoint c,Consumer<Checkpoint> progress,BooleanSupplier stop)throws Exception{
        long initialWall=System.currentTimeMillis(),initialMono=SystemClock.elapsedRealtime();
        BooleanSupplier expired=()->System.currentTimeMillis()<c.started||
            System.currentTimeMillis()-c.started>=WorkflowPolicy.MAX_MILLIS||
            SystemClock.elapsedRealtime()-initialMono>=Math.max(0,WorkflowPolicy.MAX_MILLIS-(initialWall-c.started));
        AtomicReference<WorkflowRunJournal.Reason> abort=new AtomicReference<>();
        Runnable watch=()->{
            WorkflowRunJournal.Reason reason=WorkflowRunJournal.stopReason(cancelled,paused||stop.getAsBoolean(),expired.getAsBoolean(),
                InferenceSafety.check(context),OllamaForegroundService.isRunning(),"off".equals(CoopSession.get(context).role()));
            if(reason!=null&&abort.compareAndSet(null,reason))disconnect();
        };
        java.util.function.Supplier<WorkflowRunJournal.Reason> stopReason=()->cancelled?WorkflowRunJournal.Reason.USER_CANCEL:
            abort.get()!=null?abort.get():Thread.currentThread().isInterrupted()?WorkflowRunJournal.Reason.INTERRUPTED:null;
        List<WorkflowPolicy.Step> steps=c.steps();
        ScheduledExecutorService watchdog=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"onhand-workflow-safety");t.setDaemon(true);return t;});
        try{
            if(!c.resumable())return c;
            watch.run();
            if(stopReason.get()!=null){stopped(c,stopReason.get());return c;}
            watchdog.scheduleAtFixedRate(watch,0,1,TimeUnit.SECONDS);
            GpuRunner.stop();DistributedRunner.stop();
            while(!c.complete()){
                watch.run();
                WorkflowRunJournal.Reason halted=stopReason.get();
                if(halted!=null){stopped(c,halted);break;}
                WorkflowPolicy.Budget remaining=c.budget(System.currentTimeMillis());
                if(!remaining.allowed()){
                    stopped(c,expired.getAsBoolean()||remaining.state==WorkflowPolicy.BudgetState.CLOCK||
                        remaining.state==WorkflowPolicy.BudgetState.EXPIRED?WorkflowRunJournal.Reason.TIME_LIMIT:WorkflowRunJournal.Reason.ATTEMPT_LIMIT);break;
                }
                int index=c.results.size();WorkflowPolicy.Step step=steps.get(index);
                String previous=index==0?"":c.results.get(index-1);
                boolean retry=c.attempts>0;
                String stepPrompt=WorkflowPolicy.prompt(step,previous,retry);
                // The shared CPU request uses a 2048-token context. Don't silently discard prior evidence.
                if(!DocumentBatchPolicy.fits(stepPrompt,WorkflowPolicy.MAX_TOKENS)){
                    stopped(c,WorkflowRunJournal.Reason.CONTEXT_LIMIT);break;
                }
                c.attempts++;c.totalAttempts++;c.unknownAttempts++;c.status="RUNNING";c.reason=WorkflowRunJournal.Reason.NONE;save(c);progress.accept(c);
                StructuredTextStream response=new StructuredTextStream(WorkflowPolicy.MAX_OUTPUT,WorkflowPolicy.MAX_TOKENS);
                String output;long tokens;boolean truncated;
                HttpURLConnection request=null;
                try{
                    watch.run();if(abort.get()!=null||cancelled||Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException();
                    JSONArray messages=new JSONArray().put(new JSONObject().put("role","user").put("content",stepPrompt));
                    JSONObject body=OllamaHttp.chatBody(c.model,messages);
                    body.getJSONObject("options").put("num_predict",WorkflowPolicy.MAX_TOKENS);
                    request=OllamaHttp.newChatConnection();request.setReadTimeout(60_000);connection=request;
                    watch.run();if(abort.get()!=null||cancelled)throw new java.io.InterruptedIOException();
                    OllamaHttp.streamChat(request,body,item->{
                        if(abort.get()!=null||cancelled||Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException();
                        response.accept(item);
                    },()->abort.get()!=null||cancelled||paused);
                    output=response.text();tokens=response.reportedTokens();truncated=response.truncated();
                }catch(Exception e){
                    watch.run();WorkflowRunJournal.Reason reason=stopReason.get();
                    try{stopped(c,reason!=null?reason:WorkflowRunJournal.failure(e));}
                    finally{InferenceSafetyPolicy.restoreInterrupt(e);}
                    break; // preserve paused checkpoint; never blindly retry transport
                }finally{connection=null;if(request!=null)request.disconnect();}
                if(tokens>=0){c.unknownAttempts--;c.reportedTokens+=tokens;}
                watch.run();WorkflowRunJournal.Reason reason=stopReason.get();
                if(reason!=null){stopped(c,reason);break;}
                c.status="VERIFYING";c.reason=WorkflowRunJournal.Reason.NONE;save(c);progress.accept(c);
                WorkflowPolicy.Decision decision=WorkflowPolicy.decide(step,output,truncated,c.attempts);
                // Recheck safety/stop at the verification-to-checkpoint boundary.
                watch.run();reason=stopReason.get();
                if(reason!=null){stopped(c,reason);break;}
                if(decision==WorkflowPolicy.Decision.ADVANCE){
                    c.results.add(output);c.attempts=0;c.status=c.complete()?"COMPLETED":"QUEUED";c.reason=WorkflowRunJournal.Reason.VERIFIED;save(c);progress.accept(c);
                }else{
                    c.status=decision==WorkflowPolicy.Decision.BLOCK?"BLOCKED":"RETRY_WAIT";c.reason=truncated?WorkflowRunJournal.Reason.TRUNCATED:WorkflowRunJournal.Reason.VERIFY_FAILED;save(c);progress.accept(c);
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

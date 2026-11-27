package com.ollamapocket.app;

import android.content.Context;
import android.os.SystemClock;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import org.json.JSONArray;
import org.json.JSONObject;

/** Explicit, foreground-only, local CPU requests. Never writes chat or sends to peers/cloud. */
final class DocumentBatchRunner {
    enum Stop { NONE, USER, DEADLINE, THERMAL, MEMORY, SERVER, COOP }
    private final Context context;
    private final String model;
    private final DocumentRequestControl<Stop> control=new DocumentRequestControl<>();
    private volatile long deadline;
    // In-memory diagnostic counters: dispatched chat requests, including failed/cancelled streams.
    private volatile int requests;
    private volatile long elapsedMillis;
    int requests(){return requests;}
    long elapsedMillis(){return elapsedMillis;}
    DocumentBatchRunner(Context context,String model){this.context=context.getApplicationContext();this.model=model;}
    Stop reason(){Stop value=control.reason();return value==null?Stop.NONE:value;}
    // UI cancellation records intent only; the existing worker watchdog closes transport.
    void cancel(){stop(Stop.USER);}
    private void stop(Stop value){control.stop(value);}
    private static void disconnect(HttpURLConnection c){
        if(c!=null)try{c.disconnect();}catch(RuntimeException ignored){}
    }
    private void watchStop(){if(stopped())disconnect(control.takeStopped());}
    private boolean stopped(){
        if(reason()!=Stop.NONE)return true;
        if(Thread.currentThread().isInterrupted()){stop(Stop.USER);return true;}
        if(deadline>0&&SystemClock.elapsedRealtime()>=deadline){stop(Stop.DEADLINE);return true;}
        InferenceSafetyPolicy.Reason risk=InferenceSafety.check(context);
        if(risk==InferenceSafetyPolicy.Reason.THERMAL){stop(Stop.THERMAL);return true;}
        if(risk==InferenceSafetyPolicy.Reason.LOW_MEMORY){stop(Stop.MEMORY);return true;}
        if(!OllamaForegroundService.isRunning()){stop(Stop.SERVER);return true;}
        if(!"off".equals(CoopSession.get(context).role())){stop(Stop.COOP);return true;}
        return false;
    }
    DocumentBatchPolicy.Result run(DocumentBatchPolicy.Plan plan,IntConsumer progress){
        if(!control.begin())throw new IllegalStateException("Batch already used");
        long started=SystemClock.elapsedRealtime();
        deadline=started+DocumentBatchPolicy.MAX_MILLIS;
        ScheduledExecutorService watch=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"document-batch-safety");t.setDaemon(true);return t;});
        try{
            if(!stopped()){GpuRunner.stop();DistributedRunner.stop();}
            watch.scheduleAtFixedRate(this::watchStop,0,1,TimeUnit.SECONDS);
            return DocumentBatchPolicy.analyze(plan,this::generate,this::stopped,progress);
        }finally{
            HttpURLConnection remaining=control.finish();
            watch.shutdownNow();disconnect(remaining);
            elapsedMillis=Math.max(0,SystemClock.elapsedRealtime()-started);
            if(reason()==Stop.THERMAL||reason()==Stop.MEMORY)try{
                OllamaHttp.post("/api/generate",new JSONObject().put("model",model).put("keep_alive",0).put("stream",false),"POST");
            }catch(Exception ignored){}
        }
    }
    /** One reviewed knowledge request. Same CPU-only transport, resource guards and terminal checks. */
    KnowledgeAnswerPolicy.Answer runKnowledge(KnowledgeAnswerPolicy.Plan plan)throws Exception{
        if(plan.excerpts.isEmpty()||!DocumentBatchPolicy.fits(plan.prompt,KnowledgeAnswerPolicy.TOKENS))
            throw new IllegalArgumentException("No fitting evidence");
        if(!control.begin())throw new IllegalStateException("Batch already used");
        long started=SystemClock.elapsedRealtime();deadline=started+KnowledgeAnswerPolicy.MAX_MILLIS;
        ScheduledExecutorService watch=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"knowledge-answer-safety");t.setDaemon(true);return t;});
        try{
            if(stopped())throw new IOException("Knowledge stopped");
            GpuRunner.stop();DistributedRunner.stop();
            watch.scheduleAtFixedRate(this::watchStop,0,1,TimeUnit.SECONDS);
            String answer=generate(plan.prompt,KnowledgeAnswerPolicy.MAX_ANSWER,KnowledgeAnswerPolicy.TOKENS);
            if(stopped()||requests!=1)throw new IOException("Knowledge incomplete");
            return KnowledgeAnswerPolicy.answer(plan,answer);
        }finally{
            HttpURLConnection remaining=control.finish();
            watch.shutdownNow();disconnect(remaining);
            elapsedMillis=Math.max(0,SystemClock.elapsedRealtime()-started);
            if(reason()==Stop.THERMAL||reason()==Stop.MEMORY)try{
                OllamaHttp.post("/api/generate",new JSONObject().put("model",model).put("keep_alive",0).put("stream",false),"POST");
            }catch(Exception ignored){}
        }
    }
    private int planRequestCeiling(){return DocumentBatchPolicy.MAX_UNITS*2;}
    private String generate(String prompt,int maxChars,int maxTokens)throws Exception{
        if(stopped())throw new IOException("Batch stopped");
        JSONArray messages=new JSONArray().put(new JSONObject().put("role","user").put("content",prompt));
        JSONObject body=OllamaHttp.chatBody(model,messages);
        body.getJSONObject("options").put("num_predict",maxTokens);
        // Keep the existing context and CPU memory policy. The input estimator is not an exact tokenizer.
        if(!DocumentBatchPolicy.fits(prompt,maxTokens))throw new IOException("Estimated context limit");
        long remaining=deadline-SystemClock.elapsedRealtime();
        if(remaining<=0){stop(Stop.DEADLINE);throw new IOException("Batch deadline");}
        HttpURLConnection c=OllamaHttp.newChatConnection();
        try{
            if(!control.attach(c))throw new IOException("Batch stopped");
            c.setConnectTimeout((int)Math.max(1,Math.min(3000L,remaining)));
            c.setReadTimeout((int)Math.max(1,Math.min(90000L,remaining)));
            StructuredTextStream response=new StructuredTextStream(maxChars,maxTokens);
            if(stopped())throw new IOException("Batch stopped");
            if(requests>=planRequestCeiling())throw new IOException("Batch request limit");
            requests++;
            OllamaHttp.streamChat(c,body,item->{
                if(stopped())throw new IOException("Batch stopped");
                response.accept(item);
            },()->reason()!=Stop.NONE);
            if(stopped()||response.truncated())throw new IOException("Batch incomplete");
            return DocumentBatchPolicy.checked(response.text(),maxChars);
        }finally{control.release(c);disconnect(c);}
    }
}

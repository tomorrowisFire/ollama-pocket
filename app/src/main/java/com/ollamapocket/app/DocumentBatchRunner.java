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
    private volatile Stop reason=Stop.NONE;
    private volatile HttpURLConnection connection;
    private volatile long deadline;
    // In-memory diagnostic counters: dispatched chat requests, including failed/cancelled streams.
    private volatile int requests;
    private volatile long elapsedMillis;
    int requests(){return requests;}
    long elapsedMillis(){return elapsedMillis;}
    DocumentBatchRunner(Context context,String model){this.context=context.getApplicationContext();this.model=model;}
    Stop reason(){return reason;}
    void cancel(){stop(Stop.USER);}
    private void stop(Stop value){
        synchronized(this){if(reason==Stop.NONE)reason=value;}
        HttpURLConnection c=connection;if(c!=null)c.disconnect();
    }
    private boolean stopped(){
        if(reason!=Stop.NONE)return true;
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
        long started=SystemClock.elapsedRealtime();
        deadline=started+DocumentBatchPolicy.MAX_MILLIS;
        ScheduledExecutorService watch=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"document-batch-safety");t.setDaemon(true);return t;});
        try{
            if(!stopped()){GpuRunner.stop();DistributedRunner.stop();}
            watch.scheduleAtFixedRate(this::stopped,0,1,TimeUnit.SECONDS);
            return DocumentBatchPolicy.analyze(plan,this::generate,this::stopped,progress);
        }finally{
            watch.shutdownNow();
            HttpURLConnection c=connection;if(c!=null)c.disconnect();connection=null;
            elapsedMillis=Math.max(0,SystemClock.elapsedRealtime()-started);
            if(reason==Stop.THERMAL||reason==Stop.MEMORY)try{
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
        HttpURLConnection c=OllamaHttp.newChatConnection();connection=c;
        c.setConnectTimeout((int)Math.max(1,Math.min(3000L,remaining)));
        c.setReadTimeout((int)Math.max(1,Math.min(90000L,remaining)));
        StringBuilder text=new StringBuilder();boolean[] truncated={false};
        try{
            if(stopped())throw new IOException("Batch stopped");
            if(requests>=planRequestCeiling())throw new IOException("Batch request limit");
            requests++;
            OllamaHttp.streamChat(c,body,item->{
                if(stopped())throw new IOException("Batch stopped");
                JSONObject message=item.optJSONObject("message");
                if(message!=null){
                    String chunk=message.optString("content","");
                    if((long)text.length()+chunk.length()>maxChars)throw new IOException("Batch output limit");
                    text.append(chunk);
                }
                if(Boolean.TRUE.equals(item.opt("done")))truncated[0]="length".equals(item.optString("done_reason"));
            });
            if(stopped()||truncated[0])throw new IOException("Batch incomplete");
            return DocumentBatchPolicy.checked(text.toString(),maxChars);
        }finally{connection=null;c.disconnect();}
    }
}

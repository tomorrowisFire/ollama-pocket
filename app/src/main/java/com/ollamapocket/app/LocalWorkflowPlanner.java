package com.ollamapocket.app;

import android.content.Context;
import android.os.SystemClock;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONObject;

/** Single explicit foreground local CPU planning request. Never executes the proposal. */
final class LocalWorkflowPlanner {
    private final Context context;
    private final AtomicBoolean cancelled=new AtomicBoolean(),used=new AtomicBoolean();
    private volatile HttpURLConnection connection;
    private volatile long deadline;
    LocalWorkflowPlanner(Context c){context=c.getApplicationContext();}
    void cancel(){
        cancelled.set(true);
        HttpURLConnection active=connection;
        if(active!=null)active.disconnect();
    }
    private boolean unsafe(){
        return cancelled.get()||Thread.currentThread().isInterrupted()||
            (deadline>0&&SystemClock.elapsedRealtime()>=deadline)||
            InferenceSafety.blocked(context)||!OllamaForegroundService.isRunning()||
            !"off".equals(CoopSession.get(context).role());
    }
    private void check()throws IOException{
        if(unsafe()){cancel();throw new IOException("Plan request stopped");}
    }
    String generate(String rawGoal,String model,String language)throws Exception{
        if(!used.compareAndSet(false,true))throw new IllegalStateException("Planner already used");
        String goal=WorkflowGoalPlanner.goal(rawGoal);
        String prompt=WorkflowGoalPlanner.prompt(goal,language);
        if(model==null||model.isEmpty()||model.length()>200)throw new IllegalArgumentException("Model required");
        if(!DocumentBatchPolicy.fits(prompt,WorkflowGoalPlanner.MAX_TOKENS))
            throw new IllegalArgumentException("Estimated context limit");
        deadline=SystemClock.elapsedRealtime()+WorkflowGoalPlanner.MAX_MILLIS;
        ScheduledExecutorService watchdog=Executors.newSingleThreadScheduledExecutor(r->{
            Thread t=new Thread(r,"onhand-planner-safety");t.setDaemon(true);return t;
        });
        try{
            check();
            watchdog.scheduleAtFixedRate(()->{if(unsafe())cancel();},0,250,TimeUnit.MILLISECONDS);
            GpuRunner.stop();DistributedRunner.stop();check();
            JSONObject body=OllamaHttp.chatBody(model,new JSONArray().put(new JSONObject().put("role","user").put("content",prompt)));
            body.getJSONObject("options").put("num_predict",WorkflowGoalPlanner.MAX_TOKENS);
            HttpURLConnection c=OllamaHttp.newChatConnection();connection=c;
            c.setConnectTimeout(3000);c.setReadTimeout(30_000);
            StringBuilder output=new StringBuilder();
            boolean[] terminal={false},truncated={false};
            check();
            OllamaHttp.streamChat(c,body,item->{
                check();
                JSONObject msg=item.optJSONObject("message");
                if(msg!=null){
                    Object value=msg.opt("content");
                    if(value!=null&&value!=JSONObject.NULL){
                        if(!(value instanceof String))throw new IOException("Invalid plan output");
                        String part=(String)value;
                        if((long)output.length()+part.length()>WorkflowPolicy.MAX_OUTPUT)throw new IOException("Plan output limit");
                        output.append(part);
                    }
                }
                if(Boolean.TRUE.equals(item.opt("done"))){
                    terminal[0]=true;
                    String reason=item.optString("done_reason","");
                    truncated[0]=!reason.isEmpty()&&!"stop".equals(reason);
                    Object count=item.opt("eval_count");
                    if(count!=null&&count!=JSONObject.NULL){
                        if(!(count instanceof Number))throw new IOException("Invalid token count");
                        double n=((Number)count).doubleValue();
                        if(!Double.isFinite(n)||n<0||n!=Math.floor(n)||n>WorkflowGoalPlanner.MAX_TOKENS)
                            throw new IOException("Plan token limit");
                    }
                }
            });
            check();
            if(!terminal[0])throw new IOException("Incomplete plan output");
            String plan=WorkflowGoalPlanner.compile(goal,output.toString(),truncated[0]);
            check();return plan;
        }finally{
            watchdog.shutdownNow();
            HttpURLConnection active=connection;connection=null;
            if(active!=null)active.disconnect();
            if(InferenceSafety.blocked(context))try{
                OllamaHttp.post("/api/generate",new JSONObject().put("model",model).put("keep_alive",0).put("stream",false),"POST");
            }catch(Exception ignored){}
        }
    }
}

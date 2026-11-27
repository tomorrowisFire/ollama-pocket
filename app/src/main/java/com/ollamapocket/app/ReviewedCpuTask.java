package com.ollamapocket.app;

import android.content.Context;
import java.io.*;
import java.net.HttpURLConnection;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.json.JSONObject;

/** One explicit foreground task; existing loopback CPU inference, never changes chat/model selection. */
final class ReviewedCpuTask {
    private final Context context;
    private final BooleanSupplier stopped;
    ReviewedCpuTask(Context context,BooleanSupplier stopped){this.context=context.getApplicationContext();this.stopped=stopped;}
    private boolean unsafe(){
        try{return stopped.getAsBoolean()||!OllamaForegroundService.isRunning()||
                !"off".equals(CoopSession.get(context).role())||InferenceSafety.blocked(context);}
        catch(RuntimeException unavailable){return true;}
    }
    private void check()throws IOException{
        if(unsafe()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Local task unavailable");
    }
    String installed(String model)throws Exception{
        check();
        String digest=ReviewedLocalPolicy.manifestDigest(new File(context.getFilesDir().getCanonicalFile(),"models"),model,OllamaHttp.get("/api/tags"));
        check();return digest;
    }
    ReviewedLocalPolicy.Result generate(String model,String expected,String instruction,String prompt,int tokens,int milli)throws Exception{
        check();
        JSONObject body=ReviewedLocalPolicy.request(model,instruction,prompt,tokens,milli);
        if(!expected.equals(installed(model)))throw new IOException("Local model identity changed");
        GpuRunner.stop();DistributedRunner.stop();check();
        AtomicBoolean abort=new AtomicBoolean();
        long start=System.nanoTime();
        HttpURLConnection request=OllamaHttp.newChatConnection();
        request.setReadTimeout(60000);
        BooleanSupplier cancelled=()->abort.get()||unsafe()||System.nanoTime()-start>=180_000_000_000L;
        ScheduledExecutorService watchdog=null;
        try{
            StringBuilder answer=new StringBuilder();boolean[] done={false},truncated={false};
            watchdog=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"reviewed-local-watch");t.setDaemon(true);return t;});
            watchdog.scheduleAtFixedRate(()->{if(cancelled.getAsBoolean()){abort.set(true);request.disconnect();}},0,1,TimeUnit.SECONDS);
            check();
            OllamaHttp.streamChat(request,body,item->{
                if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Local task cancelled");
                JSONObject message=item.optJSONObject("message");
                if(message!=null){
                    Object value=message.opt("content");
                    if(value!=null&&!(value instanceof String))throw new IOException("Invalid local content");
                    if(value instanceof String){
                        String part=(String)value;
                        if((long)answer.length()+part.length()>ReviewedLocalPolicy.MAX_OUTPUT)throw new IOException("Local output limit");
                        answer.append(part);
                    }
                    if(message.has("tool_calls")&&!message.isNull("tool_calls"))throw new IOException("Tool responses unsupported");
                }
                if(Boolean.TRUE.equals(item.opt("done"))){
                    Object count=item.opt("eval_count");
                    if(!(count instanceof Number)||((Number)count).doubleValue()<0||
                            ((Number)count).doubleValue()>tokens||((Number)count).doubleValue()!=Math.floor(((Number)count).doubleValue()))
                        throw new IOException("Local token evidence missing");
                    if(!"stop".equals(item.opt("done_reason"))&&!"length".equals(item.opt("done_reason")))
                        throw new IOException("Local completion evidence missing");
                    truncated[0]=StructuredInferenceTerminal.truncated(item.opt("done_reason"));done[0]=true;
                }
            },cancelled);
            if(!done[0]||cancelled.getAsBoolean()||!expected.equals(installed(model)))throw new IOException("Local result not verified");
            check();return new ReviewedLocalPolicy.Result(answer.toString(),truncated[0]);
        }finally{
            abort.set(true);
            try{if(watchdog!=null)watchdog.shutdownNow();}
            finally{request.disconnect();}
        }
    }
}

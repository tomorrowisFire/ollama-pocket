package com.ollamapocket.app;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.*;
import org.json.JSONObject;

/** Host-only composition fixture. No Android UI, microphone, device or real provider is used. */
public final class F11IntegratedPolicyTest {
    private static final String MODEL="qwen2.5:0.5b", DIGEST="a".repeat(64);
    private static int checks;
    private interface Action {void run()throws Exception;}
    private static void ok(boolean condition,String label){
        checks++;if(!condition)throw new AssertionError(label);
    }
    private static void reject(Action action)throws Exception{
        checks++;try{action.run();}
        catch(IOException|IllegalArgumentException expected){return;}
        throw new AssertionError("Unsafe fixture accepted");
    }
    private static String local(String text,boolean terminal,AtomicBoolean cancelled)throws Exception{
        String wire=new JSONObject().put("message",new JSONObject().put("content",text))
                .put("done",false).toString()+"\n";
        if(terminal)wire+=new JSONObject().put("done",true).put("done_reason","stop").toString()+"\n";
        StringBuilder result=new StringBuilder();
        try(BufferedReader reader=LocalInferenceStream.strictUtf8(new ByteArrayInputStream(wire.getBytes(StandardCharsets.UTF_8)))){
            LocalInferenceStream.ndjson(reader,item->{
                JSONObject message=item.optJSONObject("message");
                if(message!=null)result.append(message.getString("content"));
            },true,cancelled::get);
        }
        return result.toString();
    }
    private static void exportAndSpeak(String text,int chunkLimit)throws Exception{
        ArtifactFileExport export=ArtifactFileExport.prepare(ArtifactFileExport.Format.REPORT_TEXT,text);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();export.writeTo(bytes);
        ok(Arrays.equals(bytes.toByteArray(),text.getBytes(StandardCharsets.UTF_8)),"Exact report export");
        VoiceInteractionPolicy.Playback playback=new VoiceInteractionPolicy.Playback(text,"f11-",chunkLimit);
        StringBuilder spoken=new StringBuilder();
        boolean first=true;
        while(!playback.finished()){
            VoiceInteractionPolicy.Playback.Chunk chunk=playback.next();
            if(first){
                first=false;ok(playback.pause()&&playback.resume(),"Pause/resume");
                VoiceInteractionPolicy.Playback.Chunk replacement=playback.next();
                ok(replacement.text.equals(chunk.text)&&!playback.acknowledge(chunk.id),"Stale utterance rejected");
                chunk=replacement;
            }
            ok(chunk.text.length()<=chunkLimit,"Backend chunk limit");
            spoken.append(chunk.text);
            ok(playback.acknowledge(chunk.id),"Exact playback acknowledgement");
        }
        ok(spoken.toString().equals(text)&&playback.percent()==100,"Exact multilingual playback reconstruction");
    }
    private static String event(JSONObject value){return "data: "+value.toString()+"\n\n";}
    private static void cloud(String text)throws Exception{
        String delta=event(new JSONObject().put("type","response.output_text.delta").put("delta",text));
        String usage=event(new JSONObject().put("type","response.completed")
                .put("response",new JSONObject().put("usage",new JSONObject().put("input_tokens",9).put("output_tokens",2))));
        String unreliable=event(new JSONObject().put("type","response.created")
                .put("usage",new JSONObject().put("input_tokens",10)));
        CloudApi.Reply reply=CloudApi.parseStream(CloudApi.Provider.OPENAI,
                new ByteArrayInputStream((unreliable+delta+usage).getBytes(StandardCharsets.UTF_8)),null);
        ok(reply.text.equals(text)&&reply.inputTokens==-1&&reply.outputTokens==2,"Valid text, uncertain cumulative usage");
        CloudUsageLedger ledger=new CloudUsageLedger();
        ledger.record("OPENAI",reply.inputTokens,reply.outputTokens,2,4);
        CloudRequestAdvice.Estimate estimate=CloudRequestAdvice.estimate(false,
                List.of(new CloudHistory.Message("user","next")),2048,2,4,10,ledger.get("OPENAI"));
        ok(!estimate.allows()&&estimate.state==CloudRequestAdvice.State.UNKNOWN_USAGE,"Unknown usage blocks budgeted next request");
        ok(ledger.get("OPENAI").requests==1&&ledger.get("OPENAI").unknown==1,"Separate exact request accounting");
        reject(()->CloudApi.parseStream(CloudApi.Provider.OPENAI,
                new ByteArrayInputStream(delta.getBytes(StandardCharsets.UTF_8)),null));
    }
    private static void workflow(Path directory,String text)throws Exception{
        ModelVariantCatalog.Variant unknown=new ModelVariantCatalog.Variant("Ollama",MODEL,"",0);
        ModelVariantCatalog.Variant known=new ModelVariantCatalog.Variant("Ollama",MODEL,"",123);
        var batch=ModelVariantCatalog.collect(List.of(CompletableFuture.completedFuture(List.of(unknown)),
                CompletableFuture.completedFuture(List.of(known))));
        ModelSearchCache cache=new ModelSearchCache(2,60000);
        String key=ModelSearchCache.key("Ollama",MODEL);
        ok(batch.complete()&&batch.values.size()==1&&batch.values.get(0)==known,"Exact selected model and known size");
        cache.putIfCurrent(cache.writeEpoch(),key,batch.values,1000);
        String model=cache.get(key,1001).get(0).model;
        ok(model.equals(MODEL),"Selected model identity");
        BranchWorkflowRun.Definition def=BranchWorkflowRun.parse(BranchWorkflowRun.chainedExample(model,DIGEST));
        AtomicLong clock=new AtomicLong(1000);AtomicBoolean paused=new AtomicBoolean();
        BranchWorkflowRun run=BranchWorkflowRun.create(directory.toFile(),def,clock.get());
        var first=run.execute(run.read(),task->new ReviewedLocalPolicy.Result(local("true",true,new AtomicBoolean()),false),
                paused::get,p->{if(p.snapshot.value.cursor.visits.size()==1)paused.set(true);},
                clock::get,clock::addAndGet);
        ok(first.snapshot.value.cursor.status==WorkflowBranchPlan.Status.READY&&def.remainingRequests(first.snapshot.value)==3,"Durable pause and remaining branch budget");
        BranchWorkflowRun reopened=BranchWorkflowRun.open(directory.toFile(),run.id);
        var result=reopened.execute(reopened.read(),task->{
            ok("true".equals(task.previous),"Exact reviewed prior output");
            return new ReviewedLocalPolicy.Result(local(text,true,new AtomicBoolean()),false);
        },()->false,null,clock::get,clock::addAndGet);
        var saved=reopened.read().value;
        ok(saved.cursor.status==WorkflowBranchPlan.Status.COMPLETED&&def.remainingRequests(saved)==0,"Verified terminal workflow");
        ok(text.equals(saved.previousOutput())&&text.equals(result.lastAnswer),"Retained exact text");
        ok(saved.last().evidence.equals(ReviewedLocalPolicy.sha("stop\n"+text)),"Exact durable evidence");
        exportAndSpeak(saved.previousOutput(),12);
        BranchWorkflowRun cancelled=BranchWorkflowRun.create(directory.toFile(),def,clock.get());
        AtomicBoolean stop=new AtomicBoolean();
        var aborted=cancelled.execute(cancelled.read(),task->{
            stop.set(true);
            return new ReviewedLocalPolicy.Result(local("true",true,stop),false);
        },stop::get,null,clock::get,clock::addAndGet);
        ok(aborted.snapshot.value.last().outcome==WorkflowAttemptStore.Outcome.CANCELLED,"Interrupted local stream never advances");
        ok(aborted.snapshot.value.previousOutput()==null&&aborted.snapshot.value.last().evidence==null,"No false accepted evidence");
    }
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("onhand-f11-integrated-").toRealPath();
        try{
            String text="verified. 한국어 中文 日本語 👩‍💻 🇰🇷 e\u0301\r\nNext.";
            workflow(root,text);cloud(text);
            String[] fragments={"한","中","日","English","😀","👩‍💻","🇰🇷","e\u0301","\r\n"," ","!"};
            Random random=new Random(1128);
            for(int i=0;i<64;i++){
                StringBuilder value=new StringBuilder("verified ");
                for(int j=0;j<32;j++)value.append(fragments[random.nextInt(fragments.length)]);
                String exact=value.toString();
                ok(local(exact,true,new AtomicBoolean()).equals(exact),"Exact local wire round trip");
                exportAndSpeak(exact,2+random.nextInt(30));
            }
            for(String bad:new String[]{"bad\u0000text","bad\uD800"}){
                reject(()->new ReviewedLocalPolicy.Result(bad,false));
                reject(()->ArtifactFileExport.prepare(ArtifactFileExport.Format.REPORT_TEXT,bad));
                reject(()->new VoiceInteractionPolicy.Playback(bad,"bad-",12));
            }
            reject(()->local("verified",false,new AtomicBoolean()));
            System.out.println("PASS: "+checks+" F11 host-only cross-module assertions");
        }finally{
            try(var paths=Files.walk(root)){
                for(Path p:paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new))Files.delete(p);
            }
        }
    }
}

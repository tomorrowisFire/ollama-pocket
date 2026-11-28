package com.ollamapocket.app;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.json.JSONObject;
import org.json.JSONArray;

public final class ReviewedExecutionIntegrationTest {
    private static int checks;
    private static final String MODEL="qwen2.5:0.5b",SHA="a".repeat(64);
    private interface Action{void run()throws Exception;}
    private static void ok(boolean pass){checks++;if(!pass)throw new AssertionError("check "+checks);}
    private static void reject(Action a)throws Exception{
        checks++;try{a.run();}catch(Exception expected){return;}throw new AssertionError("Invalid execution accepted");
    }
    private static void manifest(Path root)throws Exception{
        Path dir=Files.createDirectories(root.resolve("manifests/registry.ollama.ai/library/qwen2.5"));
        Path file=dir.resolve("0.5b");
        byte[] body="{\"schemaVersion\":2,\"layers\":[]}".getBytes(StandardCharsets.UTF_8);
        Files.write(file,body);
        String digest=ReviewedLocalPolicy.sha(body);
        JSONObject row=new JSONObject().put("name",MODEL).put("model",MODEL).put("digest",digest);
        JSONObject catalog=new JSONObject().put("models",new JSONArray().put(row));
        ok(ReviewedLocalPolicy.manifestDigest(root.toFile(),MODEL,catalog).equals(digest));
        reject(()->ReviewedLocalPolicy.manifestDigest(root.toFile(),"../qwen:0.5b",catalog));
        reject(()->ReviewedLocalPolicy.manifestDigest(root.toFile(),MODEL,new JSONObject().put("models",new JSONArray().put(row).put(row))));
        reject(()->ReviewedLocalPolicy.manifestDigest(root.toFile(),MODEL,new JSONObject().put("models",new JSONArray().put(new JSONObject().put("name",MODEL).put("digest",SHA)))));
        Files.writeString(file,"changed");
        reject(()->ReviewedLocalPolicy.manifestDigest(root.toFile(),MODEL,catalog));
        Files.delete(file);
        Path outside=Files.createTempFile("local-manifest-",".json");
        try{
            Files.write(outside,body);Files.createSymbolicLink(file,outside);
            reject(()->ReviewedLocalPolicy.manifestDigest(root.toFile(),MODEL,catalog));
        }finally{Files.deleteIfExists(file);Files.delete(outside);}
        AgentProfile profile=new AgentProfile("Review",MODEL,digest,"system instruction",750,128);
        JSONObject request=ReviewedLocalPolicy.request(profile.model,profile.instruction,"한국어",profile.maxTokens,profile.temperatureMilli);
        ok(profile.matchesInstalled(MODEL,digest)&&!profile.matchesInstalled(MODEL,SHA));
        ok(request.getJSONArray("messages").length()==2&&request.getJSONArray("messages").getJSONObject(0).getString("role").equals("system"));
        ok(request.getJSONObject("options").getInt("num_gpu")==0&&request.getJSONObject("options").getInt("num_predict")==128&&request.getJSONObject("options").getDouble("temperature")==.75);
        ok(!request.has("tools")&&!request.has("endpoint")&&!request.has("history")&&request.getInt("keep_alive")==0);
        reject(()->ReviewedLocalPolicy.request("remote",null,"test",1,0));
        reject(()->ReviewedLocalPolicy.request(MODEL,null,"test",513,0));
        reject(()->ReviewedLocalPolicy.request(MODEL,null,"test",1,2001));
        reject(()->ReviewedLocalPolicy.request(MODEL,null," ",1,0));
        String exact="한국어 日本語 中文 👩‍💻\r\n";
        var exactResult=new ReviewedLocalPolicy.Result(exact,false);
        ok(exactResult.text.equals(exact)&&exactResult.evidence().equals(ReviewedLocalPolicy.sha("stop\n"+exact)));
        ok(!exactResult.evidence().equals(new ReviewedLocalPolicy.Result(exact,true).evidence()));
        ok(new ReviewedLocalPolicy.Result("",true).text.isEmpty());
        ok(new ReviewedLocalPolicy.Result(" \t\n",false).text.equals(" \t\n"));
        for(String bad:new String[]{null,"a\ud800","\udc00b","a\u0000b","x\u007f","x\u0085","a".repeat(ReviewedLocalPolicy.MAX_OUTPUT+1)})
            reject(()->new ReviewedLocalPolicy.Result(bad,false));
    }
    private static void branches(Path root)throws Exception{
        String raw=BranchWorkflowRun.example(MODEL,SHA);
        BranchWorkflowRun.Definition plan=BranchWorkflowRun.parse(raw);
        ok(plan.graph.nodes().size()==3&&plan.tasks.get("check").condition);
        ok(plan.graph.maxPathNodes()==2);
        ok(plan.maxRequests()==2*WorkflowAttemptStore.MAX_PER_NODE);
        ok(plan.maxOutputTokens()==plan.maxRequests()*BranchWorkflowRun.MAX_TASK_OUTPUT_TOKENS);
        ok(plan.maxRequests()<=WorkflowAttemptStore.MAX_TOTAL);
        reject(()->BranchWorkflowRun.parse(raw.replace("\"condition\"","\"shell\"")));
        reject(()->BranchWorkflowRun.parse(raw.replace("\"false\": \"no\"","\"false\": \"check\"")));
        reject(()->BranchWorkflowRun.parse(raw.replace("\"modelSha256\"","\"apiKey\"")));
        reject(()->BranchWorkflowRun.parse(raw.replace("\"kind\": \"condition\"","\"kind\": \"condition\", \"url\":\"https://example.invalid\"")));
        AtomicLong now=new AtomicLong(1000);AtomicInteger calls=new AtomicInteger();
        BranchWorkflowRun run=BranchWorkflowRun.create(root.toFile(),plan,now.get());
        ok(BranchWorkflowRun.list(root.toFile()).contains(run.id));
        BranchWorkflowRun reopen=BranchWorkflowRun.open(root.toFile(),run.id);
        ok(reopen.definition.raw.equals(raw)&&reopen.started==1000);
        ok(plan.remainingRequests(reopen.ledger.read().value)==6);
        var result=reopen.execute(reopen.ledger.read(),task->{
            var current=reopen.ledger.read().value;
            ok(current.pending());
            int count=calls.incrementAndGet();
            ok(plan.remainingRequests(current)==(count==1?5:count==2?4:2));
            ok(plan.remainingOutputTokens(current)==plan.remainingRequests(current)*BranchWorkflowRun.MAX_TASK_OUTPUT_TOKENS);
            return new ReviewedLocalPolicy.Result(count==1?"not a boolean":count==2?"true":"verified",false);
        },()->false,null,now::get,ms->now.addAndGet(ms));
        ok(result.snapshot.value.cursor.status==WorkflowBranchPlan.Status.COMPLETED&&calls.get()==3);
        ok(plan.remainingRequests(result.snapshot.value)==0&&plan.remainingOutputTokens(result.snapshot.value)==0);
        ok(result.snapshot.value.attempts.size()==3&&result.snapshot.value.attempts.get(0).outcome==WorkflowAttemptStore.Outcome.RETRY_SAFE);
        ok(result.snapshot.value.attempts.get(1).reserved>=3000&&result.snapshot.value.cursor.visits.size()==2);
        ok(result.snapshot.value.cursor.visits.get(1).node.equals("yes"));
        // A pause during retry delay must not create a second reservation.
        BranchWorkflowRun paused=BranchWorkflowRun.create(root.toFile(),plan,now.get());
        AtomicBoolean pause=new AtomicBoolean();AtomicInteger pausedCalls=new AtomicInteger();
        AtomicLong waited=new AtomicLong();
        var pauseResult=paused.execute(paused.ledger.read(),task->{
            pausedCalls.incrementAndGet();return new ReviewedLocalPolicy.Result("not a boolean",false);
        },pause::get,null,now::get,ms->{waited.addAndGet(ms);now.addAndGet(ms);pause.set(true);});
        ok(waited.get()==100L&&pausedCalls.get()==1);
        ok(pauseResult.snapshot.value.attempts.size()==1&&!pauseResult.snapshot.value.pending());
        ok(plan.remainingRequests(pauseResult.snapshot.value)==5);
        ok(pauseResult.snapshot.value.cursor.status==WorkflowBranchPlan.Status.READY&&
                pauseResult.snapshot.value.last().outcome==WorkflowAttemptStore.Outcome.RETRY_SAFE);
        pause.set(false);
        var resumed=paused.execute(paused.ledger.read(),task->{
            pausedCalls.incrementAndGet();return new ReviewedLocalPolicy.Result(task.condition?"true":"verified",false);
        },pause::get,null,now::get,ms->now.addAndGet(ms));
        ok(resumed.snapshot.value.cursor.status==WorkflowBranchPlan.Status.COMPLETED&&pausedCalls.get()==3);
        BranchWorkflowRun interruptedRun=BranchWorkflowRun.create(root.toFile(),plan,now.get());
        try{
            var interruptedResult=interruptedRun.execute(interruptedRun.ledger.read(),
                    task->new ReviewedLocalPolicy.Result("not a boolean",false),()->false,null,now::get,
                    ms->{throw new InterruptedException("retry cancelled");});
            ok(Thread.currentThread().isInterrupted()&&interruptedResult.snapshot.value.attempts.size()==1);
            ok(!interruptedResult.snapshot.value.pending());
        }finally{Thread.interrupted();}
        BranchWorkflowRun another=BranchWorkflowRun.open(root.toFile(),run.id);
        another.execute(another.ledger.read(),task->{throw new AssertionError("Completed run replay");},()->false,null,now::get,ms->now.addAndGet(ms));
        BranchWorkflowRun.delete(root.toFile(),run.id,result.snapshot.revision);
        ok(!BranchWorkflowRun.list(root.toFile()).contains(run.id));
        BranchWorkflowRun blocked=BranchWorkflowRun.create(root.toFile(),plan,now.get());
        var falseResult=blocked.execute(blocked.ledger.read(),task->new ReviewedLocalPolicy.Result(task.condition?"false":"blocked",false),
                ()->false,null,now::get,ms->now.addAndGet(ms));
        ok(falseResult.snapshot.value.cursor.status==WorkflowBranchPlan.Status.BLOCKED&&falseResult.snapshot.value.cursor.visits.get(1).node.equals("no"));
        BranchWorkflowRun uncertain=BranchWorkflowRun.create(root.toFile(),plan,now.get());
        var failure=uncertain.execute(uncertain.ledger.read(),task->{throw new IOException("Transport uncertain");},()->false,null,now::get,ms->now.addAndGet(ms));
        ok(failure.snapshot.value.last().outcome==WorkflowAttemptStore.Outcome.UNCERTAIN);
        BranchWorkflowRun invalidResult=BranchWorkflowRun.create(root.toFile(),plan,now.get());
        var invalidRun=invalidResult.execute(invalidResult.ledger.read(),
                task->new ReviewedLocalPolicy.Result("true\u0000",false),()->false,null,now::get,ms->now.addAndGet(ms));
        ok(invalidRun.snapshot.value.attempts.size()==1&&!invalidRun.snapshot.value.pending());
        ok(invalidRun.snapshot.value.last().outcome==WorkflowAttemptStore.Outcome.UNCERTAIN&&
                invalidRun.snapshot.value.last().evidence==null&&invalidRun.lastAnswer.isEmpty());
        BranchWorkflowRun expired=BranchWorkflowRun.create(root.toFile(),plan,now.get());
        var afterDeadline=expired.execute(expired.ledger.read(),task->{
            now.set(expired.started+WorkflowAttemptStore.WINDOW_MS);
            return new ReviewedLocalPolicy.Result("true",false);
        },()->false,null,now::get,ms->now.addAndGet(ms));
        ok(afterDeadline.snapshot.value.last().outcome==WorkflowAttemptStore.Outcome.CANCELLED);
        ok(afterDeadline.lastAnswer.isEmpty()&&afterDeadline.snapshot.value.last().evidence==null);
        BranchWorkflowRun beforeDispatch=BranchWorkflowRun.create(root.toFile(),plan,now.get());
        var beforeDeadline=beforeDispatch.execute(beforeDispatch.ledger.read(),task->{throw new AssertionError("Expired dispatch");},
                ()->false,event->{if(event.snapshot.value.pending())now.set(beforeDispatch.started+WorkflowAttemptStore.WINDOW_MS);},
                now::get,ms->now.addAndGet(ms));
        ok(beforeDeadline.snapshot.value.last().outcome==WorkflowAttemptStore.Outcome.CANCELLED);
        BranchWorkflowRun rollback=BranchWorkflowRun.create(root.toFile(),plan,now.get());
        var rolled=rollback.execute(rollback.ledger.read(),task->{
            now.decrementAndGet();return new ReviewedLocalPolicy.Result("true",false);
        },()->false,null,now::get,ms->now.addAndGet(ms));
        ok(rolled.snapshot.value.last().outcome==WorkflowAttemptStore.Outcome.CANCELLED&&rolled.lastAnswer.isEmpty());
        now.incrementAndGet();
        BranchWorkflowRun unresolved=BranchWorkflowRun.create(root.toFile(),plan,now.get());
        var pending=unresolved.ledger.reserve(unresolved.ledger.read(),now.get());
        BranchWorkflowRun recovered=BranchWorkflowRun.open(root.toFile(),unresolved.id);
        var recoveredResult=recovered.execute(recovered.ledger.read(),task->{throw new AssertionError("Pending reservation replay");},
                ()->false,null,now::get,ms->now.addAndGet(ms));
        ok(recoveredResult.snapshot.value.pending()&&recoveredResult.snapshot.value.last().key.equals(pending.value.last().key));
        ok(plan.remainingRequests(recoveredResult.snapshot.value)==5);
        reject(()->BranchWorkflowRun.delete(root.toFile(),unresolved.id,pending.revision));
        var abandoned=recovered.abandon(recoveredResult.snapshot,now.get());
        ok(abandoned.snapshot.value.last().outcome==WorkflowAttemptStore.Outcome.UNCERTAIN&&
                abandoned.snapshot.value.cursor.status==WorkflowBranchPlan.Status.BLOCKED);
        reject(()->recovered.abandon(pending,now.get()));
        reject(()->BranchWorkflowRun.open(root.toFile(),"../outside"));
        Path file=root.resolve("branch-runs-v1").resolve(unresolved.id).resolve("definition-v1.bin");
        byte[] damaged=Files.readAllBytes(file);damaged[damaged.length-1]^=1;Files.write(file,damaged);
        reject(()->BranchWorkflowRun.open(root.toFile(),unresolved.id));
    }
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("reviewed-local-").toRealPath();
        try{
            manifest(Files.createDirectory(root.resolve("models")));
            branches(Files.createDirectory(root.resolve("runs")));
            System.out.println("PASS: "+checks+" reviewed local profile and durable conditional execution checks");
        }finally{try(var paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new))Files.delete(p);}}
    }
}

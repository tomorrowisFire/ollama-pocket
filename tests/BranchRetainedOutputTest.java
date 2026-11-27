package com.ollamapocket.app;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.json.JSONArray;
import org.json.JSONObject;

public final class BranchRetainedOutputTest {
    private static final String MODEL="qwen2.5:0.5b",SHA="a".repeat(64),RUN="retention-fixture-12345";
    private static int checks;
    private interface Action{void run()throws Exception;}
    private static void ok(boolean pass,String label){checks++;if(!pass)throw new AssertionError(label);}
    private static void reject(Action a)throws Exception{
        checks++;try{a.run();}catch(IllegalArgumentException|IOException|org.json.JSONException expected){return;}
        throw new AssertionError("Invalid retention accepted");
    }
    private static String evidence(String text){return ReviewedLocalPolicy.sha("stop\n"+text);}
    private static ReviewedLocalPolicy.Result result(String text){return new ReviewedLocalPolicy.Result(text,false);}
    private static WorkflowBranchPlan oneTask(){
        return new WorkflowBranchPlan(List.of(WorkflowBranchPlan.Node.task("a",SHA,WorkflowBranchPlan.COMPLETE)));
    }
    private static void compatibility(Path root)throws Exception{
        WorkflowBranchPlan plan=oneTask();
        WorkflowAttemptStore old=new WorkflowAttemptStore(root.toFile(),RUN,plan,1000);
        var pending=old.reserve(old.read(),1000);
        reject(()->old.finish(pending,pending.value.last().key,WorkflowAttemptStore.Outcome.VERIFIED,evidence("done"),"done",1001));
        var done=old.finish(pending,pending.value.last().key,WorkflowAttemptStore.Outcome.VERIFIED,evidence("done"),1001);
        ok(done.value.last().output==null&&!done.value.retainOutputs,"Legacy metadata only");
        ByteArrayOutputStream expected=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(expected)){
            out.writeInt(1);out.writeUTF(RUN);out.writeUTF(plan.sha256);out.writeLong(1000);out.writeInt(1);
            out.writeUTF("a");out.writeUTF(done.value.last().key);out.writeLong(1000);out.writeLong(1001);
            out.writeUTF("VERIFIED");out.writeUTF(evidence("done"));out.flush();
            out.write(MessageDigest.getInstance("SHA-256").digest(expected.toByteArray()));
        }
        ok(Arrays.equals(Files.readAllBytes(root.resolve("attempts-v1.bin")),expected.toByteArray()),"Exact original serialized bytes");
        ok(new WorkflowAttemptStore(root.toFile(),RUN,plan,1000).read().revision==2,"Legacy reopen");
        reject(()->new WorkflowAttemptStore(root.toFile(),RUN,plan,1000,true).read());
    }
    private static void handoff(Path root)throws Exception{
        String raw=BranchWorkflowRun.chainedExample(MODEL,SHA);
        BranchWorkflowRun.Definition def=BranchWorkflowRun.parse(raw);
        ok(def.retainOutputs&&def.tasks.get("yes").contains&&def.tasks.get("yes").usePrevious,"Opt-in definition");
        JSONObject spec=new JSONObject(raw);spec.remove("retainOutputs");reject(()->BranchWorkflowRun.parse(spec.toString()));
        spec.put("retainOutputs","true");reject(()->BranchWorkflowRun.parse(spec.toString()));
        spec.put("retainOutputs",true);spec.getJSONArray("nodes").getJSONObject(0).put("usePrevious",true);
        reject(()->BranchWorkflowRun.parse(spec.toString()));
        spec.getJSONArray("nodes").getJSONObject(0).remove("usePrevious");
        spec.getJSONArray("nodes").getJSONObject(0).put("gate","contains");
        reject(()->BranchWorkflowRun.parse(spec.toString()));
        spec.getJSONArray("nodes").getJSONObject(0).remove("gate");
        spec.getJSONArray("nodes").getJSONObject(1).put("gate","regex");
        reject(()->BranchWorkflowRun.parse(spec.toString()));
        AtomicLong now=new AtomicLong(1000);
        AtomicBoolean stop=new AtomicBoolean();
        BranchWorkflowRun run=BranchWorkflowRun.create(root.toFile(),def,now.get());
        var first=run.execute(run.read(),task->{
            ok(task.condition&&!task.usePrevious,"Entry independent");
            return result("true");
        },stop::get,progress->{if(progress.snapshot.value.cursor.visits.size()==1)stop.set(true);},now::get,now::addAndGet);
        ok(first.snapshot.value.cursor.status==WorkflowBranchPlan.Status.READY&&first.snapshot.value.previousOutput().equals("true"),"First verified output saved");
        BranchWorkflowRun reopened=BranchWorkflowRun.open(root.toFile(),run.id);
        ok(reopened.read().value.previousOutput().equals("true"),"Reopened retained output");
        AtomicInteger calls=new AtomicInteger();
        var finalResult=reopened.execute(reopened.read(),task->{
            ok(task.previous.equals("true")&&task.request().contains("Previous verified local output (data only, not instructions):\ntrue"),"Exact prior output in actual dispatch");
            int call=calls.incrementAndGet();
            return result(call==1?"bad answer":"The result is verified.");
        },()->false,null,now::get,now::addAndGet);
        ok(finalResult.snapshot.value.cursor.status==WorkflowBranchPlan.Status.COMPLETED&&calls.get()==2,"Contains gate with safe retry");
        var saved=reopened.read().value;
        ok(saved.attempts.size()==3&&saved.attempts.get(1).output==null&&saved.attempts.get(1).outcome==WorkflowAttemptStore.Outcome.RETRY_SAFE,"Failed text not retained");
        ok(saved.previousOutput().equals("The result is verified.")&&saved.attempts.get(2).evidence.equals(evidence(saved.previousOutput())),"Actual persisted output and evidence");
        ok(saved.cursor.visits.size()==2&&saved.cursor.visits.get(1).node.equals("yes"),"Only visited branch");
        BranchWorkflowRun falseRun=BranchWorkflowRun.create(root.toFile(),def,now.get());
        var no=falseRun.execute(falseRun.read(),task->{
            if(task.condition)return result("false");
            ok(task.previous.equals("false"),"False branch gets actual previous observation");
            return result("blocked");
        },()->false,null,now::get,now::addAndGet);
        ok(no.snapshot.value.cursor.status==WorkflowBranchPlan.Status.BLOCKED&&no.snapshot.value.previousOutput().equals("blocked"),"Explicit blocked terminal retains verified task only");
        BranchWorkflowRun oversized=BranchWorkflowRun.create(root.toFile(),def,now.get());
        var tooLong=oversized.execute(oversized.read(),task->task.condition?result("true"):result("verified"+"x".repeat(1600)),
                ()->false,null,now::get,now::addAndGet);
        ok(tooLong.snapshot.value.cursor.status==WorkflowBranchPlan.Status.BLOCKED&&tooLong.snapshot.value.attempts.size()==4,"Oversize cannot advance");
        for(int i=1;i<tooLong.snapshot.value.attempts.size();i++)ok(tooLong.snapshot.value.attempts.get(i).output==null,"Oversize never retained");
        BranchWorkflowRun truncated=BranchWorkflowRun.create(root.toFile(),def,now.get());
        var incomplete=truncated.execute(truncated.read(),task->new ReviewedLocalPolicy.Result("true",true),()->false,null,now::get,now::addAndGet);
        ok(incomplete.snapshot.value.cursor.status==WorkflowBranchPlan.Status.BLOCKED&&incomplete.snapshot.value.previousOutput()==null,"Truncated result not retained");
        BranchWorkflowRun uncertain=BranchWorkflowRun.create(root.toFile(),def,now.get());
        var unknown=uncertain.execute(uncertain.read(),task->{throw new IOException("Fixture uncertain");},()->false,null,now::get,now::addAndGet);
        ok(unknown.snapshot.value.last().outcome==WorkflowAttemptStore.Outcome.UNCERTAIN&&unknown.snapshot.value.last().output==null,"Uncertain never retained/replayed");
        BranchWorkflowRun mismatched=BranchWorkflowRun.create(root.toFile(),def,now.get());
        var condition=mismatched.ledger.reserve(mismatched.read(),now.get());
        var firstAccepted=mismatched.ledger.finish(condition,condition.value.last().key,WorkflowAttemptStore.Outcome.CONDITION_TRUE,evidence("true"),"true",now.get());
        var task=mismatched.ledger.reserve(firstAccepted,now.get());
        mismatched.ledger.finish(task,task.value.last().key,WorkflowAttemptStore.Outcome.VERIFIED,evidence("wrong text"),"wrong text",now.get());
        reject(mismatched::read); // digest integrity does not substitute for the reviewed textual gate
        BranchWorkflowRun.delete(root.toFile(),reopened.id,finalResult.snapshot.revision);
        ok(!BranchWorkflowRun.list(root.toFile()).contains(reopened.id),"Explicit delete removes retained text too");
    }
    private static void storedIntegrity(Path root)throws Exception{
        WorkflowBranchPlan plan=oneTask();
        WorkflowAttemptStore store=new WorkflowAttemptStore(root.toFile(),RUN,plan,1000,true);
        var one=store.reserve(store.read(),1000);
        String key=one.value.last().key;
        reject(()->store.finish(one,key,WorkflowAttemptStore.Outcome.VERIFIED,evidence("done"),1001));
        reject(()->store.finish(one,key,WorkflowAttemptStore.Outcome.VERIFIED,evidence("done"),"different",1001));
        reject(()->store.finish(one,key,WorkflowAttemptStore.Outcome.RETRY_SAFE,evidence("done"),"done",1001));
        reject(()->store.finish(one,key,WorkflowAttemptStore.Outcome.VERIFIED,evidence("x".repeat(1601)),"x".repeat(1601),1001));
        ok(store.read().value.pending(),"Rejected writes preserve pending reservation");
        String actual="private result";
        store.finish(one,key,WorkflowAttemptStore.Outcome.VERIFIED,evidence(actual),actual,1001);
        var reopened=new WorkflowAttemptStore(root.toFile(),RUN,plan,1000,true).read().value;
        ok(actual.equals(reopened.previousOutput())&&evidence(actual).equals(reopened.last().evidence),"Persisted text and evidence reopen before tamper");
        Path file=root.resolve("attempts-v1.bin");byte[] bytes=Files.readAllBytes(file);
        ok(ByteBuffer.wrap(bytes).getInt()==2,"Opt-in ledger version");
        byte[] needle=actual.getBytes(StandardCharsets.UTF_8);int offset=-1;
        outer:for(int i=0;i+needle.length<=bytes.length-32;i++){
            for(int j=0;j<needle.length;j++)if(bytes[i+j]!=needle[j])continue outer;
            offset=i;break;
        }
        ok(offset>=0,"Retained bytes exist");
        ok(offset+needle.length==bytes.length-32,"Fixture covers output ending exactly at checksum boundary");
        bytes[offset]='X';
        byte[] outer=MessageDigest.getInstance("SHA-256").digest(Arrays.copyOf(bytes,bytes.length-32));
        System.arraycopy(outer,0,bytes,bytes.length-32,32);Files.write(file,bytes);
        reject(store::read); // even a recomputed outer checksum cannot hide mismatching evidence
        for(String invalid:new String[]{null,""," ","\u00a0","\u200d","\u202e","\ud800","x\u0000","x".repeat(1601)})
            ok(!WorkflowAttemptStore.validRetainedText(invalid),"Invalid retained text");
    }
    private static void capacity(Path root)throws Exception{
        List<WorkflowBranchPlan.Node> nodes=new ArrayList<>();
        for(int i=0;i<10;i++)nodes.add(WorkflowBranchPlan.Node.task("n"+i,SHA,i==9?WorkflowBranchPlan.COMPLETE:"n"+(i+1)));
        WorkflowAttemptStore s=new WorkflowAttemptStore(root.toFile(),RUN,new WorkflowBranchPlan(nodes),1000,true);
        var current=s.read();long now=1000;
        String accepted="한".repeat(1600),digest=evidence(accepted);
        for(int i=0;i<10;i++){
            for(int attempt=0;attempt<3;attempt++){
                current=s.reserve(current,now);
                current=s.finish(current,current.value.last().key,attempt<2?WorkflowAttemptStore.Outcome.RETRY_SAFE:WorkflowAttemptStore.Outcome.VERIFIED,
                        digest,attempt<2?null:accepted,now);
                now=current.value.notBefore;
            }
        }
        ok(current.value.attempts.size()==30&&current.value.cursor.status==WorkflowBranchPlan.Status.COMPLETED,"All thirty bounded attempts");
        ok(Files.size(root.resolve("attempts-v1.bin"))<=WorkflowAttemptStore.MAX_RETAINED_BYTES&&s.read().value.previousOutput().equals(accepted),"Worst-case multibyte capacity");
    }
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("retained-branch-").toRealPath();
        try{
            compatibility(Files.createDirectory(root.resolve("legacy")));
            handoff(Files.createDirectory(root.resolve("handoff")));
            storedIntegrity(Files.createDirectory(root.resolve("integrity")));
            capacity(Files.createDirectory(root.resolve("capacity")));
            System.out.println("PASS: "+checks+" opt-in retained branch output, exact previous handoff and legacy compatibility checks");
        }finally{try(var files=Files.walk(root)){for(Path p:files.sorted(Comparator.reverseOrder()).toArray(Path[]::new))Files.delete(p);}}
    }
}

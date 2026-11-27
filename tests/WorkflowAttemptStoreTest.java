package com.ollamapocket.app;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static com.ollamapocket.app.WorkflowAttemptStore.Outcome;

public final class WorkflowAttemptStoreTest {
    private static final String RUN="run-fixture-12345678",DEF="a".repeat(64),EVIDENCE="b".repeat(64);
    private static final long START=1000;
    private static int checks;
    private interface Action{void run()throws Exception;}
    private static void ok(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
    private static void reject(Action a)throws Exception{
        checks++;try{a.run();}catch(IOException|IllegalArgumentException expected){return;}throw new AssertionError("Invalid workflow operation");
    }
    private static WorkflowBranchPlan plan(){
        return new WorkflowBranchPlan(Arrays.asList(WorkflowBranchPlan.Node.condition("check",DEF,"yes","no"),
                WorkflowBranchPlan.Node.task("yes",DEF,WorkflowBranchPlan.COMPLETE),
                WorkflowBranchPlan.Node.task("no",DEF,WorkflowBranchPlan.COMPLETE)));
    }
    private static WorkflowAttemptStore open(Path root)throws Exception{return new WorkflowAttemptStore(root.toFile(),RUN,plan(),START);}
    private static void clean(Path root)throws Exception{
        try(var paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new))Files.delete(p);}
    }
    private static void pathTests(Path root)throws Exception{
        WorkflowAttemptStore s=open(root),other=open(root);
        var initial=s.read();
        ok(initial.revision==0&&initial.value.cursor.nextNode.equals("check"));
        var foreign=new WorkflowAttemptStore(root.toFile(),"other-run-123456789",plan(),START).read();
        reject(()->s.reserve(foreign,START));
        reject(()->s.reserve(initial,START-1));
        reject(()->s.reserve(initial,START+WorkflowAttemptStore.WINDOW_MS));
        var one=s.reserve(initial,START);
        ok(one.revision==1&&one.value.pending()&&one.value.last().node.equals("check"));
        reject(()->other.reserve(initial,START));
        var reopened=open(root).read();
        ok(reopened.revision==one.revision&&reopened.value.pending()&&reopened.value.last().key.equals(one.value.last().key));
        reject(()->other.reserve(reopened,START+1)); // process recovery must not replay unknown work
        reject(()->other.finish(reopened,"wrong",Outcome.CONDITION_FALSE,EVIDENCE,START+1));
        reject(()->other.finish(reopened,reopened.value.last().key,Outcome.VERIFIED,EVIDENCE,START+1));
        reject(()->other.finish(reopened,reopened.value.last().key,Outcome.CONDITION_FALSE,null,START+1));
        var two=other.finish(reopened,reopened.value.last().key,Outcome.CONDITION_FALSE,EVIDENCE,START+1);
        ok(two.revision==2&&two.value.cursor.nextNode.equals("no")&&!two.value.pending());
        reject(()->s.finish(one,one.value.last().key,Outcome.CONDITION_TRUE,EVIDENCE,START+2));
        var three=s.reserve(two,START+2);
        ok(three.value.last().node.equals("no")&&three.value.attempts.size()==2);
        var four=s.finish(three,three.value.last().key,Outcome.VERIFIED,EVIDENCE,START+3);
        ok(four.value.cursor.status==WorkflowBranchPlan.Status.COMPLETED&&four.value.cursor.visits.size()==2);
        reject(()->s.reserve(four,START+4));
        ok(open(root).read().revision==4);
        reject(()->new WorkflowAttemptStore(root.toFile(),"other-run-123456789",plan(),START).read());
        reject(()->new WorkflowAttemptStore(root.toFile(),RUN,plan(),START+1).read());
    }
    private static void retryTests(Path root)throws Exception{
        WorkflowAttemptStore s=open(root);
        var first=s.reserve(s.read(),START);
        var safe=s.finish(first,first.value.last().key,Outcome.RETRY_SAFE,EVIDENCE,START+10);
        ok(safe.value.currentAttempts==1&&safe.value.notBefore==START+2010);
        reject(()->s.reserve(safe,START+2009));
        var second=s.reserve(safe,START+2010);
        ok(!second.value.last().key.equals(first.value.last().key));
        var safe2=s.finish(second,second.value.last().key,Outcome.RETRY_SAFE,EVIDENCE,START+2011);
        ok(safe2.value.notBefore==START+6011&&safe2.value.currentAttempts==2);
        reject(()->s.reserve(safe2,START+6010));
        var third=s.reserve(safe2,START+6011);
        reject(()->s.finish(third,third.value.last().key,Outcome.RETRY_SAFE,EVIDENCE,START+6012));
        var stopped=s.finish(third,third.value.last().key,Outcome.BLOCKED,null,START+6012);
        ok(stopped.value.cursor.status==WorkflowBranchPlan.Status.BLOCKED);
        reject(()->s.reserve(stopped,START+6013));
    }
    private static void concurrentTests(Path root)throws Exception{
        WorkflowAttemptStore s=open(root),other=open(root);
        var initial=s.read();
        ExecutorService pool=Executors.newFixedThreadPool(8);
        try{
            CountDownLatch begin=new CountDownLatch(1);
            List<Future<Boolean>> claims=new ArrayList<>();
            for(int i=0;i<16;i++){
                WorkflowAttemptStore owner=i%2==0?s:other;
                claims.add(pool.submit(()->{begin.await();try{owner.reserve(initial,START);return true;}catch(ProjectTransactions.TransactionFailure e){return false;}}));
            }
            begin.countDown();int admitted=0;for(Future<Boolean> f:claims)if(f.get(5,TimeUnit.SECONDS))admitted++;
            ok(admitted==1);
        }finally{pool.shutdownNow();}
        var pending=open(root).read();
        ok(pending.value.pending());
        var uncertain=s.finish(pending,pending.value.last().key,Outcome.UNCERTAIN,null,START+1);
        ok(uncertain.value.cursor.status==WorkflowBranchPlan.Status.BLOCKED);
        reject(()->s.reserve(uncertain,START+2));
        Path ledger=root.resolve("attempts-v1.bin");
        byte[] corrupted=Files.readAllBytes(ledger);corrupted[4]^=1;Files.write(ledger,corrupted);
        reject(()->open(root).read());reject(()->s.reserve(uncertain,START+3));
        ok(Arrays.equals(Files.readAllBytes(ledger),corrupted)); // never replace corrupt state with empty
    }
    private static void linkedTests(Path root)throws Exception{
        Path outside=Files.createTempFile("attempt-outside-",".bin");
        try{
            Files.createSymbolicLink(root.resolve("attempts-v1.bin"),outside);
            reject(()->open(root).read());
            Files.delete(root.resolve("attempts-v1.bin"));
            Files.deleteIfExists(root.resolve("attempts-v1.lock"));
            Files.createSymbolicLink(root.resolve("attempts-v1.lock"),outside);
            reject(()->open(root).read());
            ok(Files.size(outside)==0);
        }finally{Files.deleteIfExists(outside);}
    }
    private static void writeUncertainty(Path root)throws Exception{
        final class FailingBackend implements ProjectTransactions.Backend {
            byte[] saved;
            boolean fail=true;
            @Override public byte[] read(){return saved==null?null:saved.clone();}
            @Override public void write(byte[] data)throws IOException{
                saved=data.clone();
                if(fail)throw new IOException("Fixture write outcome uncertain");
            }
        }
        FailingBackend backend=new FailingBackend();
        WorkflowAttemptStore s=new WorkflowAttemptStore(root.toFile(),RUN,plan(),START,backend);
        var initial=s.read();
        try{s.reserve(initial,START);throw new AssertionError("Uncertain write was accepted");}
        catch(ProjectTransactions.TransactionFailure e){ok(e.reason==ProjectTransactions.Failure.WRITE_UNCERTAIN);}
        var recovered=s.read();ok(recovered.value.pending()&&recovered.revision==1);
        reject(()->s.reserve(initial,START));reject(()->s.reserve(recovered,START+1));
        backend.fail=false;
        var blocked=s.finish(recovered,recovered.value.last().key,Outcome.UNCERTAIN,null,START+2);
        ok(blocked.value.cursor.status==WorkflowBranchPlan.Status.BLOCKED);
    }
    private static void fullBudget(Path root)throws Exception{
        List<WorkflowBranchPlan.Node> nodes=new ArrayList<>();
        for(int i=0;i<10;i++)nodes.add(WorkflowBranchPlan.Node.task("n"+i,DEF,i==9?WorkflowBranchPlan.COMPLETE:"n"+(i+1)));
        WorkflowAttemptStore s=new WorkflowAttemptStore(root.toFile(),RUN,new WorkflowBranchPlan(nodes),START);
        var current=s.read();long time=START;
        for(int i=0;i<10;i++){
            for(int attempt=0;attempt<3;attempt++){
                current=s.reserve(current,time);
                ok(current.value.currentAttempts==attempt+1);
                current=s.finish(current,current.value.last().key,attempt<2?Outcome.RETRY_SAFE:Outcome.VERIFIED,EVIDENCE,time);
                time=current.value.notBefore;
            }
        }
        ok(current.revision==60&&current.value.attempts.size()==30&&current.value.cursor.status==WorkflowBranchPlan.Status.COMPLETED);
        final var done=current;final long after=time;
        reject(()->s.reserve(done,after));
    }
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("attempt-ledger-").toRealPath();
        try{
            pathTests(Files.createDirectory(root.resolve("path")));
            retryTests(Files.createDirectory(root.resolve("retry")));
            concurrentTests(Files.createDirectory(root.resolve("concurrent")));
            linkedTests(Files.createDirectory(root.resolve("links")));
            writeUncertainty(Files.createDirectory(root.resolve("write")));
            fullBudget(Files.createDirectory(root.resolve("budget")));
        }finally{clean(root);}
        System.out.println("PASS: "+checks+" durable workflow attempt, finite retry and stale reservation checks");
    }
}

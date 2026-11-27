package com.ollamapocket.app;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import org.json.JSONArray;
import org.json.JSONObject;

/** Foreground text-only branch plans and their private immutable run definition. */
final class BranchWorkflowRun {
    private static final Object LOCK=new Object();
    private static final String RUN_ID="[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    static final int MAX_SAVED_RUNS=16;
    static final class Task {
        final String prompt,expect;
        final boolean condition;
        Task(String prompt,String expect,boolean condition){this.prompt=prompt;this.expect=expect;this.condition=condition;}
        String request(){
            return prompt+"\n\n"+(condition?
                    "Return exactly one word: true or false. Do not perform actions or call tools.":
                    "Return exactly the required text: "+expect+". Do not perform actions or call tools.");
        }
        WorkflowAttemptStore.Outcome outcome(ReviewedLocalPolicy.Result result,boolean retry){
            if(!result.truncated){
                String value=result.text.trim();
                if(condition&&"true".equals(value))return WorkflowAttemptStore.Outcome.CONDITION_TRUE;
                if(condition&&"false".equals(value))return WorkflowAttemptStore.Outcome.CONDITION_FALSE;
                if(!condition&&expect.equals(value))return WorkflowAttemptStore.Outcome.VERIFIED;
            }
            return retry?WorkflowAttemptStore.Outcome.RETRY_SAFE:WorkflowAttemptStore.Outcome.BLOCKED;
        }
    }
    static final class Definition {
        final String raw,model,digest;
        final WorkflowBranchPlan graph;
        final Map<String,Task> tasks;
        private Definition(String raw,String model,String digest,WorkflowBranchPlan graph,Map<String,Task> tasks){
            this.raw=raw;this.model=model;this.digest=digest;this.graph=graph;
            this.tasks=Collections.unmodifiableMap(new LinkedHashMap<>(tasks));
        }
    }
    static Definition parse(String raw)throws Exception{
        ToolJsonPolicy.requireObject(raw);
        JSONObject spec=new JSONObject(raw);
        if(spec.length()!=3)throw new IllegalArgumentException("Branch definition fields");
        String model=string(spec,"model"),digest=string(spec,"modelSha256");
        if(!ReviewedLocalPolicy.modelTag(model)||!digest.matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("Branch model identity");
        JSONArray nodes=spec.optJSONArray("nodes");
        if(nodes==null||nodes.length()<1||nodes.length()>WorkflowBranchPlan.MAX_NODES)
            throw new IllegalArgumentException("Branch node count");
        List<WorkflowBranchPlan.Node> graph=new ArrayList<>();
        Map<String,Task> tasks=new LinkedHashMap<>();
        for(int i=0;i<nodes.length();i++){
            JSONObject node=nodes.getJSONObject(i);
            String id=string(node,"id"),kind=string(node,"kind");
            String prompt=ReviewedLocalPolicy.text(string(node,"prompt"),2000);
            if(node.length()!=5)throw new IllegalArgumentException("Branch node fields");
            boolean condition="condition".equals(kind);
            if(!condition&&!"task".equals(kind))throw new IllegalArgumentException("Branch node kind");
            String expect=condition?"":ReviewedLocalPolicy.text(string(node,"expect"),200).trim();
            String next=string(node,condition?"true":"next"),other=condition?string(node,"false"):"";
            // Bind each definition to the selected model manifest and exact reviewed source.
            String identity=ReviewedLocalPolicy.sha(raw+"\n"+i);
            graph.add(condition?WorkflowBranchPlan.Node.condition(id,identity,next,other):
                    WorkflowBranchPlan.Node.task(id,identity,next));
            if(tasks.put(id,new Task(prompt,expect,condition))!=null)throw new IllegalArgumentException("Duplicate branch node");
        }
        return new Definition(raw,model,digest,new WorkflowBranchPlan(graph),tasks);
    }
    private static String string(JSONObject o,String key)throws Exception{
        Object value=o.get(key);
        if(!(value instanceof String))throw new IllegalArgumentException("Branch string field");
        return (String)value;
    }
    static String example(String model,String digest)throws Exception{
        if(!ReviewedLocalPolicy.modelTag(model)||digest==null||!digest.matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("Branch model");
        return new JSONObject().put("model",model).put("modelSha256",digest).put("nodes",new JSONArray()
                .put(new JSONObject().put("id","check").put("kind","condition").put("prompt","Is 2 + 2 equal to 4?").put("true","yes").put("false","no"))
                .put(new JSONObject().put("id","yes").put("kind","task").put("prompt","Return the word verified.").put("expect","verified").put("next",WorkflowBranchPlan.COMPLETE))
                .put(new JSONObject().put("id","no").put("kind","task").put("prompt","Return the word blocked.").put("expect","blocked").put("next",WorkflowBranchPlan.BLOCK))).toString(2);
    }

    final Definition definition;
    final String id;
    final long started;
    final WorkflowAttemptStore ledger;
    private BranchWorkflowRun(File directory,String id,long started,Definition definition)throws IOException{
        this.id=id;this.started=started;this.definition=definition;
        ledger=new WorkflowAttemptStore(directory,id,definition.graph,started);
    }
    private static File root(File privateDir,boolean create)throws IOException{
        if(privateDir==null||!privateDir.equals(privateDir.getCanonicalFile())||!privateDir.isDirectory())
            throw new IOException("Invalid private branch root");
        File root=new File(privateDir,"branch-runs-v1");
        if(!root.equals(root.getCanonicalFile())||Files.isSymbolicLink(root.toPath()))throw new IOException("Linked branch root");
        if(create&&!root.exists())Files.createDirectory(root.toPath());
        if(root.exists()&&!root.isDirectory())throw new IOException("Invalid branch root");
        return root;
    }
    private static File[] entries(File root)throws IOException{
        if(!root.exists())return new File[0];
        File[] entries=root.listFiles();
        if(entries==null||entries.length>MAX_SAVED_RUNS)throw new IOException("Branch run storage limit");
        return entries;
    }
    static List<String> list(File privateDir)throws IOException{
        synchronized(LOCK){
            List<String> ids=new ArrayList<>();
            for(File f:entries(root(privateDir,false))){
                if(f.getName().matches(RUN_ID)&&f.equals(f.getCanonicalFile())&&
                        Files.isDirectory(f.toPath(),LinkOption.NOFOLLOW_LINKS))ids.add(f.getName());
            }
            Collections.sort(ids);return ids;
        }
    }
    static BranchWorkflowRun create(File privateDir,Definition reviewed,long now)throws Exception{
        synchronized(LOCK){
            if(reviewed==null||now<=0)throw new IllegalArgumentException("Branch run review");
            File root=root(privateDir,true);
            if(entries(root).length>=MAX_SAVED_RUNS)throw new IOException("Branch run storage full");
            String id=UUID.randomUUID().toString();
            File directory=new File(root,id);Files.createDirectory(directory.toPath());
            Path stage=directory.toPath().resolve(".definition.part");
            boolean published=false;
            try{
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                try(DataOutputStream out=new DataOutputStream(bytes)){
                    out.writeUTF(id);out.writeLong(now);out.writeUTF(reviewed.raw);
                    out.writeUTF(ReviewedLocalPolicy.sha(reviewed.raw));out.writeUTF(reviewed.graph.sha256);
                }
                try(FileChannel out=FileChannel.open(stage,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){
                    ByteBuffer b=ByteBuffer.wrap(bytes.toByteArray());
                    while(b.hasRemaining())if(out.write(b)<=0)throw new IOException("Branch definition write");
                    out.force(true);
                }
                Files.move(stage,directory.toPath().resolve("definition-v1.bin"),StandardCopyOption.ATOMIC_MOVE);
                published=true;
                return open(privateDir,id);
            }finally{
                Files.deleteIfExists(stage);
                if(!published)Files.deleteIfExists(directory.toPath());
            }
        }
    }
    static BranchWorkflowRun open(File privateDir,String id)throws Exception{
        synchronized(LOCK){
            if(id==null||!id.matches(RUN_ID))throw new IllegalArgumentException("Branch run ID");
            File directory=new File(root(privateDir,false),id);
            if(!directory.equals(directory.getCanonicalFile())||!Files.isDirectory(directory.toPath(),LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Missing branch run");
            byte[] raw=ReviewedLocalPolicy.readFile(new File(directory,"definition-v1.bin"),65536);
            try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(raw))){
                if(!id.equals(in.readUTF()))throw new IOException("Branch run identity");
                long started=in.readLong();String spec=in.readUTF(),digest=in.readUTF(),graph=in.readUTF();
                Definition definition=parse(spec);
                if(started<=0||!ReviewedLocalPolicy.sha(spec).equals(digest)||!definition.graph.sha256.equals(graph)||in.read()!=-1)
                    throw new IOException("Branch definition changed");
                return new BranchWorkflowRun(directory,id,started,definition);
            }
        }
    }
    /** Explicit user deletion only. Never remove a run that has an unresolved reservation. */
    static void delete(File privateDir,String id,long expectedRevision)throws Exception{
        synchronized(LOCK){
            BranchWorkflowRun run=open(privateDir,id);
            ProjectTransactions.Snapshot<WorkflowAttemptStore.Ledger> saved=run.ledger.read();
            if(saved.revision!=expectedRevision||saved.value.pending())throw new IOException("Branch run changed or unresolved");
            File directory=new File(root(privateDir,false),id);
            File[] files=directory.listFiles();if(files==null||files.length>3)throw new IOException("Unexpected branch files");
            for(File f:files)if(!Arrays.asList("definition-v1.bin","attempts-v1.bin","attempts-v1.lock").contains(f.getName())||
                    !Files.isRegularFile(f.toPath(),LinkOption.NOFOLLOW_LINKS))throw new IOException("Unexpected branch file");
            // Application integration has one foreground worker; no concurrent deletion/dispatch.
            for(File f:files)Files.delete(f.toPath());
            Files.delete(directory.toPath());
        }
    }

    Progress abandon(ProjectTransactions.Snapshot<WorkflowAttemptStore.Ledger> reviewed,long now)throws Exception{
        if(reviewed==null||!reviewed.value.pending())throw new IOException("No unresolved branch attempt");
        WorkflowAttemptStore.Attempt pending=reviewed.value.last();
        return new Progress(ledger.finish(reviewed,pending.key,WorkflowAttemptStore.Outcome.UNCERTAIN,null,
                Math.max(now,pending.reserved)),"");
    }
    interface Generate {ReviewedLocalPolicy.Result run(Task task)throws Exception;}
    interface Sleep {void waitMillis(long ms)throws InterruptedException;}
    static final class Progress {
        final ProjectTransactions.Snapshot<WorkflowAttemptStore.Ledger> snapshot;
        final String lastAnswer;
        Progress(ProjectTransactions.Snapshot<WorkflowAttemptStore.Ledger> snapshot,String lastAnswer){
            this.snapshot=snapshot;this.lastAnswer=lastAnswer;
        }
    }
    Progress execute(ProjectTransactions.Snapshot<WorkflowAttemptStore.Ledger> reviewed,Generate generate,
                     BooleanSupplier stop,Consumer<Progress> observer)throws Exception{
        final long wall=System.currentTimeMillis(),mono=System.nanoTime();
        return execute(reviewed,generate,()->stop.getAsBoolean()||System.nanoTime()-mono>=
                Math.max(0L,WorkflowAttemptStore.WINDOW_MS-Math.max(0L,wall-started))*1_000_000L,
                observer,System::currentTimeMillis,Thread::sleep);
    }
    Progress execute(ProjectTransactions.Snapshot<WorkflowAttemptStore.Ledger> reviewed,Generate generate,
                     BooleanSupplier stop,Consumer<Progress> observer,LongSupplier clock,Sleep sleep)throws Exception{
        if(reviewed==null||!id.equals(reviewed.value.runId)||!definition.graph.sha256.equals(reviewed.value.cursor.planSha256))
            throw new IllegalArgumentException("Branch execution review");
        ProjectTransactions.Snapshot<WorkflowAttemptStore.Ledger> current=reviewed;
        String last="";
        while(!current.value.pending()&&current.value.cursor.status==WorkflowBranchPlan.Status.READY){
            if(stop.getAsBoolean()||Thread.currentThread().isInterrupted())break;
            long now=clock.getAsLong();
            if(now<started||now-started>=WorkflowAttemptStore.WINDOW_MS)break;
            if(now<current.value.notBefore){
                long wait=current.value.notBefore-now;
                if(wait>4000L)break;
                sleep.waitMillis(wait);
                if(stop.getAsBoolean()||Thread.currentThread().isInterrupted())break;
                now=clock.getAsLong();
                if(now<current.value.notBefore||now<started||now-started>=WorkflowAttemptStore.WINDOW_MS)break;
            }
            current=ledger.reserve(current,now);
            WorkflowAttemptStore.Attempt attempt=current.value.last();
            Task task=definition.tasks.get(attempt.node);
            ReviewedLocalPolicy.Result result=null;
            WorkflowAttemptStore.Outcome outcome;String evidence=null;
            try{
                if(observer!=null)observer.accept(new Progress(current,last));
                if(stop.getAsBoolean()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Branch cancelled");
                result=Objects.requireNonNull(generate.run(task));
                if(stop.getAsBoolean()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Branch cancelled");
                evidence=result.evidence();
                boolean retry=current.value.currentAttempts<WorkflowAttemptStore.MAX_PER_NODE&&
                        current.value.attempts.size()<WorkflowAttemptStore.MAX_TOTAL;
                outcome=task.outcome(result,retry);
                if(outcome==WorkflowAttemptStore.Outcome.RETRY_SAFE&&clock.getAsLong()-started>=WorkflowAttemptStore.WINDOW_MS-4000L)
                    outcome=WorkflowAttemptStore.Outcome.BLOCKED;
                last=result.text;
            }catch(Exception failure){
                outcome=stop.getAsBoolean()||Thread.currentThread().isInterrupted()||failure instanceof InterruptedException||
                        failure instanceof InterruptedIOException?WorkflowAttemptStore.Outcome.CANCELLED:WorkflowAttemptStore.Outcome.UNCERTAIN;
            }
            // Persist the actual terminal/uncertain outcome. If this fails, the durable pending reservation prevents replay.
            boolean interrupted=Thread.interrupted();
            try{current=ledger.finish(current,attempt.key,outcome,evidence,Math.max(attempt.reserved,clock.getAsLong()));}
            finally{if(interrupted)Thread.currentThread().interrupt();}
            if(observer!=null)observer.accept(new Progress(current,last));
        }
        return new Progress(current,last);
    }
}

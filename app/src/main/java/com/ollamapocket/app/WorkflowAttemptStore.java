package com.ollamapocket.app;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;

/** Private attempt reservations for an exact branch graph. Never dispatches or retries work itself. */
final class WorkflowAttemptStore {
    static final int MAX_TOTAL=30,MAX_PER_NODE=3,MAX_BYTES=16384;
    static final long WINDOW_MS=30L*60*1000;
    private static final Object LOCK=new Object();
    enum Outcome { PENDING, VERIFIED, CONDITION_TRUE, CONDITION_FALSE, RETRY_SAFE, BLOCKED, UNCERTAIN, CANCELLED }

    static final class Attempt {
        final String node,key,evidence;
        final long reserved,finished;
        final Outcome outcome;
        private Attempt(String node,String key,long reserved,long finished,Outcome outcome,String evidence){
            this.node=node;this.key=key;this.reserved=reserved;this.finished=finished;this.outcome=outcome;this.evidence=evidence;
        }
    }
    static final class Ledger {
        final List<Attempt> attempts;
        final WorkflowBranchPlan.Snapshot cursor;
        final long revision,notBefore,started;
        final String runId;
        final int currentAttempts;
        private Ledger(List<Attempt> attempts,WorkflowBranchPlan.Snapshot cursor,long revision,long notBefore,int currentAttempts,String runId,long started){
            this.attempts=Collections.unmodifiableList(new ArrayList<>(attempts));this.cursor=cursor;
            this.revision=revision;this.notBefore=notBefore;this.currentAttempts=currentAttempts;
            this.runId=runId;this.started=started;
        }
        Attempt last(){return attempts.isEmpty()?null:attempts.get(attempts.size()-1);}
        boolean pending(){return last()!=null&&last().outcome==Outcome.PENDING;}
    }
    private final Path root,file,lockFile;
    private final String runId;
    private final WorkflowBranchPlan plan;
    private final long started;
    private final ProjectTransactions<Ledger> tx;

    /** Dedicated app-private directory for this run; caller must preserve its identity across recovery. */
    WorkflowAttemptStore(File directory,String runId,WorkflowBranchPlan plan,long started)throws IOException{
        this(directory,runId,plan,started,null);
    }
    /** Fixture-only backend injection for write-uncertainty tests; production uses private disk. */
    WorkflowAttemptStore(File directory,String runId,WorkflowBranchPlan plan,long started,ProjectTransactions.Backend fixture)throws IOException{
        if(directory==null||!directory.isDirectory()||!directory.getAbsoluteFile().equals(directory.getCanonicalFile())||
                runId==null||!runId.matches("[A-Za-z0-9_-]{16,64}")||plan==null||started<=0)
            throw new IllegalArgumentException("Workflow attempt store identity");
        this.root=directory.toPath().toAbsolutePath();this.file=root.resolve("attempts-v1.bin");
        this.lockFile=root.resolve("attempts-v1.lock");this.runId=runId;this.plan=plan;this.started=started;
        tx=new ProjectTransactions<>(fixture==null?new Disk():fixture,new Codec(),LOCK,derive(Collections.emptyList()),MAX_BYTES);
    }
    private interface Operation<T>{T run()throws Exception;}
    private <T>T locked(Operation<T> operation)throws Exception{
        synchronized(LOCK){
            checkRoot();
            try(FileChannel channel=FileChannel.open(lockFile,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){
                if(!Files.isRegularFile(lockFile,LinkOption.NOFOLLOW_LINKS))throw new IOException("Invalid workflow lock");
                FileLock acquired;
                try{acquired=channel.tryLock();}catch(OverlappingFileLockException e){throw new IOException("Workflow attempt store busy",e);}
                if(acquired==null)throw new IOException("Workflow attempt store busy");
                try(FileLock held=acquired){return operation.run();}
            }
        }
    }
    private void checkRoot()throws IOException{
        if(!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS)||!root.toFile().getCanonicalFile().equals(root.toFile()))
            throw new IOException("Invalid workflow private root");
    }
    ProjectTransactions.Snapshot<Ledger> read()throws Exception{return locked(tx::read);}
    private void requireIdentity(ProjectTransactions.Snapshot<Ledger> reviewed)throws IOException{
        if(reviewed==null||!runId.equals(reviewed.value.runId)||started!=reviewed.value.started||
                !plan.sha256.equals(reviewed.value.cursor.planSha256))
            throw new IOException("Workflow reviewed run identity");
    }
    /** Return a persisted reservation only after exact reviewed state and post-write readback succeed. */
    ProjectTransactions.Snapshot<Ledger> reserve(ProjectTransactions.Snapshot<Ledger> reviewed,long now)throws Exception{
        requireIdentity(reviewed);
        return locked(()->tx.commit(reviewed,current->{
            if(current.pending()||current.cursor.status!=WorkflowBranchPlan.Status.READY||
                    current.attempts.size()>=MAX_TOTAL||current.currentAttempts>=MAX_PER_NODE||
                    now<current.notBefore||now<started||now-started>=WINDOW_MS)
                throw new IllegalArgumentException("Workflow reservation unavailable");
            String node=current.cursor.nextNode;
            List<Attempt> next=new ArrayList<>(current.attempts);
            next.add(new Attempt(node,key(node,next.size(),now),now,0,Outcome.PENDING,null));
            return derive(next);
        }));
    }
    /** Only actual verified evidence can settle an attempt. RETRY_SAFE must never be inferred from an uncertain call. */
    ProjectTransactions.Snapshot<Ledger> finish(ProjectTransactions.Snapshot<Ledger> reviewed,String key,
                                                 Outcome outcome,String evidence,long now)throws Exception{
        requireIdentity(reviewed);
        return locked(()->tx.commit(reviewed,current->{
            Attempt last=current.last();
            if(last==null||!current.pending()||!last.key.equals(key)||outcome==null||outcome==Outcome.PENDING||
                    now<last.reserved)throw new IllegalArgumentException("Workflow settlement mismatch");
            List<Attempt> next=new ArrayList<>(current.attempts);
            next.set(next.size()-1,new Attempt(last.node,last.key,last.reserved,now,outcome,evidence));
            return derive(next);
        }));
    }
    private static boolean digest(String value){return value!=null&&value.matches("[a-f0-9]{64}");}
    private static boolean needsEvidence(Outcome o){
        return o==Outcome.VERIFIED||o==Outcome.CONDITION_TRUE||o==Outcome.CONDITION_FALSE||o==Outcome.RETRY_SAFE;
    }
    private static long delay(int count){return count==1?2000L:4000L;}
    private static WorkflowBranchPlan.Outcome graphOutcome(Outcome o){
        switch(o){
            case VERIFIED:return WorkflowBranchPlan.Outcome.VERIFIED;
            case CONDITION_TRUE:return WorkflowBranchPlan.Outcome.CONDITION_TRUE;
            case CONDITION_FALSE:return WorkflowBranchPlan.Outcome.CONDITION_FALSE;
            case BLOCKED:return WorkflowBranchPlan.Outcome.UNVERIFIED;
            case UNCERTAIN:return WorkflowBranchPlan.Outcome.UNCERTAIN;
            case CANCELLED:return WorkflowBranchPlan.Outcome.CANCELLED;
            default:throw new IllegalArgumentException("Not a settled graph outcome");
        }
    }
    private Ledger derive(List<Attempt> entries){
        if(entries.size()>MAX_TOTAL)throw new IllegalArgumentException("Workflow attempt count");
        WorkflowBranchPlan.Snapshot cursor=plan.start();
        long notBefore=started,revision=0;
        int perNode=0;
        for(int i=0;i<entries.size();i++){
            Attempt a=entries.get(i);
            if(a==null||a.outcome==null||cursor.status!=WorkflowBranchPlan.Status.READY||
                    !Objects.equals(cursor.nextNode,a.node)||a.reserved<notBefore||a.reserved<started||
                    a.reserved-started>=WINDOW_MS||!key(a.node,i,a.reserved).equals(a.key)||++perNode>MAX_PER_NODE)
                throw new IllegalArgumentException("Workflow attempt path");
            revision++;
            if(a.outcome==Outcome.PENDING){
                if(i!=entries.size()-1||a.finished!=0||a.evidence!=null)
                    throw new IllegalArgumentException("Unresolved reservation");
                notBefore=a.reserved;continue;
            }
            revision++;
            if(a.finished<a.reserved||needsEvidence(a.outcome)&&!digest(a.evidence)||
                    a.evidence!=null&&!digest(a.evidence))throw new IllegalArgumentException("Workflow settlement evidence");
            notBefore=a.finished;
            if(a.outcome==Outcome.RETRY_SAFE){
                if(perNode>=MAX_PER_NODE||i+1>=MAX_TOTAL||a.finished>Long.MAX_VALUE-delay(perNode))
                    throw new IllegalArgumentException("Retry budget exhausted");
                notBefore+=delay(perNode);
            }else{
                cursor=plan.advance(cursor,new WorkflowBranchPlan.Visit(a.node,graphOutcome(a.outcome),a.evidence));
                perNode=0;
            }
        }
        return new Ledger(entries,cursor,revision,notBefore,perNode,runId,started);
    }
    private String key(String node,int index,long reserved){
        return hex(hash(("onhand-attempt-v1\n"+runId+"\n"+plan.sha256+"\n"+node+"\n"+index+"\n"+reserved)
                .getBytes(StandardCharsets.US_ASCII)));
    }
    private static byte[] hash(byte[] bytes){
        try{return MessageDigest.getInstance("SHA-256").digest(bytes);}
        catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
    private static String hex(byte[] bytes){
        StringBuilder b=new StringBuilder(64);
        for(byte v:bytes)b.append(Character.forDigit((v&255)>>>4,16)).append(Character.forDigit(v&15,16));
        return b.toString();
    }
    private final class Codec implements ProjectTransactions.Codec<Ledger>{
        @Override public long revision(Ledger value){return value.revision;}
        @Override public byte[] encode(Ledger value)throws IOException{
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(DataOutputStream out=new DataOutputStream(bytes)){
                out.writeInt(1);out.writeUTF(runId);out.writeUTF(plan.sha256);out.writeLong(started);
                out.writeInt(value.attempts.size());
                for(Attempt a:value.attempts){
                    out.writeUTF(a.node);out.writeUTF(a.key);out.writeLong(a.reserved);out.writeLong(a.finished);
                    out.writeUTF(a.outcome.name());out.writeUTF(a.evidence==null?"":a.evidence);
                }
                out.flush();
                byte[] body=bytes.toByteArray();out.write(hash(body));
            }
            byte[] encoded=bytes.toByteArray();
            if(encoded.length>MAX_BYTES)throw new IOException("Workflow ledger size");
            return encoded;
        }
        @Override public Ledger decode(byte[] bytes)throws IOException{
            if(bytes==null||bytes.length<=32||bytes.length>MAX_BYTES)throw new IOException("Workflow ledger size");
            byte[] body=Arrays.copyOf(bytes,bytes.length-32);
            if(!MessageDigest.isEqual(hash(body),Arrays.copyOfRange(bytes,body.length,bytes.length)))
                throw new IOException("Workflow ledger checksum");
            try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(body))){
                if(in.readInt()!=1||!runId.equals(in.readUTF())||!plan.sha256.equals(in.readUTF())||in.readLong()!=started)
                    throw new IOException("Workflow ledger identity");
                int count=in.readInt();
                if(count<1||count>MAX_TOTAL)throw new IOException("Workflow ledger count");
                List<Attempt> attempts=new ArrayList<>();
                for(int i=0;i<count;i++){
                    String node=in.readUTF(),key=in.readUTF();long reserved=in.readLong(),finished=in.readLong();
                    Outcome outcome=Outcome.valueOf(in.readUTF());String evidence=in.readUTF();
                    attempts.add(new Attempt(node,key,reserved,finished,outcome,evidence.isEmpty()?null:evidence));
                }
                if(in.read()!=-1)throw new IOException("Workflow ledger trailing bytes");
                return derive(attempts);
            }catch(IllegalArgumentException invalid){throw new IOException("Invalid workflow ledger",invalid);}
        }
    }
    private final class Disk implements ProjectTransactions.Backend {
        @Override public byte[] read()throws IOException{
            checkRoot();
            BasicFileAttributes attrs;
            try{attrs=Files.readAttributes(file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);}
            catch(NoSuchFileException absent){return null;}
            if(!attrs.isRegularFile()||attrs.size()<1||attrs.size()>MAX_BYTES)throw new IOException("Invalid workflow ledger file");
            try(InputStream in=Files.newInputStream(file,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
                byte[] bytes=new byte[(int)attrs.size()];int offset=0;
                while(offset<bytes.length){int n=in.read(bytes,offset,bytes.length-offset);if(n<=0)throw new EOFException("Workflow ledger truncated");offset+=n;}
                if(in.read()!=-1)throw new IOException("Workflow ledger grew");
                return bytes;
            }
        }
        @Override public void write(byte[] bytes)throws IOException{
            checkRoot();
            if(bytes==null||bytes.length<1||bytes.length>MAX_BYTES)throw new IOException("Workflow ledger size");
            Path staging=Files.createTempFile(root,".attempts-v1-",".part");
            try{
                try(FileChannel out=FileChannel.open(staging,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){
                    ByteBuffer b=ByteBuffer.wrap(bytes);
                    while(b.hasRemaining())if(out.write(b)<=0)throw new IOException("Workflow ledger write");
                    out.force(true);
                }
                Files.move(staging,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            }finally{Files.deleteIfExists(staging);}
        }
    }
}
